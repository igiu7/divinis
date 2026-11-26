package com.divinis.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.divinis.registry.ModEntities;
import com.divinis.registry.ModParticles;
import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenEffect.Anchor;
import com.divinis.screenfx.ScreenFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ignaroth (/summon divinis:ignaroth in the middle of his crater - his home): a great red dragon. He sleeps curled on
 * his hoard (DragonHoard); four ballistae (Ballista) stand round the crater, ready - use one to load and shoot a bolt
 * where you look. A player coming near or a blow wakes him: one eye opens, then the other, he rises and roars. Every
 * attack is telegraphed (boss.Telegraphs: whoever stands in the red when it fills is hit) - with good movement he can
 * be beaten without taking a hit.
 *
 * Phase 1, on the ground:
 * - Breath: a cone of fire before him, then the fire sweeps round as he turns his head;
 * - Tail: a swing of the tail over the half circle behind him;
 * - Wings: he rears and beats his wings - a cone before him that hurls whoever is in it far back.
 * At half health he springs into the air; the crater cracks and lava rises round its edge (the outer ring of the room
 * burns for the rest of the fight - keep inside). He cannot be hurt meanwhile.
 * Phase 2, in the air (circling high over the crater):
 * - Bombing run: he flies low across the crater along a marked lane, breathing fire - the lane burns behind him for a
 *   while;
 * - Meteors: three waves of circles over the crater, burning rocks falling into them;
 * - Dive: a circle under a player; he dives onto it and beats back up;
 * - Ballistae: bolts hit him; three hits from different players (from as many as there are, up to three) within
 *   fifteen seconds bring him down for eight seconds - on the ground he takes half as much again.
 * Below 40% health he is enraged: faster, shorter rests.
 * Dying, he falls out of the sky into his crater, writhes, breathes one last fire into the sky and lies still; the
 * lava sinks back.
 * Timings match the animations in client/IgnarothModelData (tools/models/build_ignaroth.py).
 * Effects: dragon fire (ModParticles.DRAGON_FIRE), embers (EMBER) and ash (ASH); the lava (client.IgnarothRenderer);
 * heat over the crater, the screen shaking at his roars and his falls.
 */
public class Ignaroth extends Monster implements BossTargeting.Marked {
    public static final byte SLEEPING = 0, WAKING = 1, AWAKE = 2, TRANSFORM = 3;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Ignaroth.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(Ignaroth.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(Ignaroth.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(Ignaroth.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DOWNED = SynchedEntityData.defineId(Ignaroth.class, EntityDataSerializers.BOOLEAN);
    /** the lava: its inner edge's radius round home (0: none), and home */
    private static final EntityDataAccessor<Float> LAVA = SynchedEntityData.defineId(Ignaroth.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> HOME = SynchedEntityData.defineId(Ignaroth.class, EntityDataSerializers.VECTOR3);

    public enum Attack {
        NONE(0, 0), BREATH(60, 16), TAIL(32, 18), WINGS(36, 20), BOMB(80, 20), METEORS(70, 30), DIVE(60, 46), ROAR(30, 0);

        public final int length, impact;

        Attack(int length, int impact) {
            this.length = length;
            this.impact = impact;
        }
    }

    private static final byte EVENT_ATTACK = (byte) 235, EVENT_FALL = (byte) 245, EVENT_RECOVER = (byte) 246;

    public static final int WAKE_TICKS = 100, TRANSFORM_TICKS = 120, DEATH_TICKS = 120, DOWN_TICKS = 160;
    private static final int TRANSFORM_LEAP = 20, TRANSFORM_CRACK = 40, TRANSFORM_LAVA_FROM = 50, TRANSFORM_LAVA_TO = 100;
    private static final double WAKE_RANGE = 14, AGGRO_RANGE = 48;
    public static final float LAVA_START = 26, LAVA_EDGE = 16, LAVA_WIDTH = 9;
    private static final double SKY = 10, CIRCLE = 11, BALLISTA_RANGE = 13;

    private static final float BREATH_DAMAGE = 16, BREATH_RADIUS = 11, BREATH_HALF = 30, SWEEP_HALF = 14, SWEEP_SPIN = 2.5F;
    private static final int SWEEP_FROM = 24, SWEEP_TICKS = 28;
    private static final float TAIL_DAMAGE = 18, TAIL_RADIUS = 9.5F;
    private static final float GUST_DAMAGE = 6, GUST_RADIUS = 12, GUST_HALF = 50;
    private static final float BOMB_DAMAGE = 14, BOMB_HALF = 1.8F, BOMB_LENGTH = 32;
    private static final int BOMB_APPROACH = 20, BOMB_RUN = 40, BOMB_BURN = 60;
    private static final float METEOR_DAMAGE = 16, METEOR_RADIUS = 2.6F;
    private static final int[] METEOR_WAVES = {30, 45, 60};
    private static final int METEOR_FALL = 10;
    private static final float DIVE_DAMAGE = 22, DIVE_RADIUS = 4.0F;
    private static final int DIVE_LOCK = 10, DIVE_START = 30;
    private static final float BALLISTA_DAMAGE = 12;
    private static final int KNOCK_WINDOW = 300, KNOCK_REST = 400;

    private static final AttributeModifier ENRAGED = new AttributeModifier(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.divinis.Divinis.MODID, "ignaroth_enraged"),
            0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

    // client: animations
    public final AnimationState sleepAnimation = new AnimationState();
    public final AnimationState wakeAnimation = new AnimationState();
    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState flyAnimation = new AnimationState();
    public final AnimationState breathAnimation = new AnimationState();
    public final AnimationState tailAnimation = new AnimationState();
    public final AnimationState wingsAnimation = new AnimationState();
    public final AnimationState bombAnimation = new AnimationState();
    public final AnimationState meteorsAnimation = new AnimationState();
    public final AnimationState diveAnimation = new AnimationState();
    public final AnimationState fallAnimation = new AnimationState();
    public final AnimationState stunnedAnimation = new AnimationState();
    public final AnimationState recoverAnimation = new AnimationState();
    public final AnimationState roarAnimation = new AnimationState();
    public final AnimationState transformAnimation = new AnimationState();
    public final AnimationState deathAnimation = new AnimationState();
    public int dying;
    private int clientAttackTicks;

    // server: the fight
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private final BossTargeting targeting = new BossTargeting();
    private Attack attack = Attack.NONE, last = Attack.NONE;
    private int attackTick, cooldown = 20, stateTick, downTick, lastDown = -1000;
    private boolean pendingTransform, unclamped;
    private float lockedYaw;
    private double circleAngle;
    @Nullable private Vec3 home;
    private Vec3 aim = Vec3.ZERO, laneFrom = Vec3.ZERO, laneDir = Vec3.ZERO, moveFrom = Vec3.ZERO;
    private final List<List<Vec3>> waves = new ArrayList<>();
    private final Map<UUID, Integer> boltHits = new HashMap<>();
    @Nullable private DragonHoard hoard;
    private final List<Ballista> ballistae = new ArrayList<>();

    public Ignaroth(EntityType<? extends Ignaroth> type, Level level) {
        super(type, level);
        this.xpReward = 400;
        setPersistenceRequired();
        bossBar.setVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 18.0)
                .add(Attributes.STEP_HEIGHT, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, SLEEPING);
        builder.define(PHASE, (byte) 1);
        builder.define(TARGET_ID, -1);
        builder.define(FLYING, false);
        builder.define(DOWNED, false);
        builder.define(LAVA, 0F);
        builder.define(HOME, new Vector3f());
    }

    @Override
    protected void registerGoals() {}

    public byte state() {
        return entityData.get(STATE);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    public boolean flying() {
        return entityData.get(FLYING);
    }

    public boolean downed() {
        return entityData.get(DOWNED);
    }

    /** Client: the lava's inner edge round home (0: none). */
    public float lava() {
        return entityData.get(LAVA);
    }

    public Vec3 homeSynced() {
        Vector3f v = entityData.get(HOME);
        return new Vec3(v.x, v.y, v.z);
    }

    public boolean isAttacking() {
        return clientAttackTicks > 0 || state() != AWAKE || downed();
    }

    // ------------------------------------------------------------------ client

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!level().isClientSide) return;
        if (STATE.equals(key)) {
            if (state() == WAKING) wakeAnimation.start(tickCount);
            else wakeAnimation.stop();
            if (state() == TRANSFORM) {
                stopAttacks();
                transformAnimation.start(tickCount);
            } else {
                transformAnimation.stop();
            }
        }
    }

    private void stopAttacks() {
        for (AnimationState s : new AnimationState[] {breathAnimation, tailAnimation, wingsAnimation, bombAnimation, meteorsAnimation,
                diveAnimation, roarAnimation, fallAnimation, recoverAnimation}) s.stop();
    }

    @Override
    public void handleEntityEvent(byte id) {
        int u = id & 0xFF;
        if (u == (EVENT_FALL & 0xFF)) {
            stopAttacks();
            fallAnimation.start(tickCount);
            return;
        }
        if (u == (EVENT_RECOVER & 0xFF)) {
            stopAttacks();
            recoverAnimation.start(tickCount);
            clientAttackTicks = 28;
            return;
        }
        int index = u - (EVENT_ATTACK & 0xFF);
        if (index > 0 && index < Attack.values().length) {
            Attack a = Attack.values()[index];
            stopAttacks();
            (switch (a) {
                case BREATH -> breathAnimation;
                case TAIL -> tailAnimation;
                case WINGS -> wingsAnimation;
                case BOMB -> bombAnimation;
                case METEORS -> meteorsAnimation;
                case DIVE -> diveAnimation;
                default -> roarAnimation;
            }).start(tickCount);
            clientAttackTicks = a == Attack.BOMB ? 0 : a.length;
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) return;
        if (isDeadOrDying()) {
            deathAnimation.startIfStopped(tickCount);
            return;
        }
        byte s = state();
        if (s == SLEEPING) sleepAnimation.startIfStopped(tickCount);
        else sleepAnimation.stop();
        boolean air = flying() && !downed();
        if (s == AWAKE && !air && !downed()) idleAnimation.startIfStopped(tickCount);
        else idleAnimation.stop();
        if (s == AWAKE && air) flyAnimation.startIfStopped(tickCount);
        else flyAnimation.stop();
        if (s == AWAKE && downed() && !fallAnimation.isStarted()) stunnedAnimation.startIfStopped(tickCount);
        if (!downed()) stunnedAnimation.stop();
        if (downed() && fallAnimation.isStarted() && fallAnimation.getAccumulatedTime() > 1000) {
            fallAnimation.stop();
            stunnedAnimation.startIfStopped(tickCount);
        }
        if (clientAttackTicks > 0) clientAttackTicks--;
        // smoke curling from his nostrils, embers from the fire in his belly
        if (s != SLEEPING && tickCount % 6 == 0) {
            float yaw = yBodyRot * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            Vec3 snout = position().add(fwd.scale(air ? 6.0 : 6.5)).add(0, air ? 2.4 : 4.6, 0);
            level().addParticle(ParticleTypes.SMOKE, snout.x, snout.y, snout.z, 0, 0.03, 0);
            level().addParticle(ModParticles.EMBER.get(), getX() + (random.nextDouble() - 0.5) * 2, getY() + 1.0, getZ() + (random.nextDouble() - 0.5) * 2,
                    0, 0.02, 0);
        }
        if (s == SLEEPING && tickCount % 30 == 0) {
            float yaw = yBodyRot * Mth.DEG_TO_RAD;
            Vec3 snout = position().add(-Mth.sin(yaw) * 2.5, 2.0, Mth.cos(yaw) * 2.5);
            level().addParticle(ParticleTypes.SMOKE, snout.x, snout.y, snout.z, 0, 0.02, 0);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(LAVA_START + LAVA_WIDTH, 12, LAVA_START + LAVA_WIDTH); // the wings, the lava round the crater
    }

    // ------------------------------------------------------------------ server: states

    private Vec3 home() {
        if (home == null) {
            home = position();
            entityData.set(HOME, new Vector3f((float) home.x, (float) home.y, (float) home.z));
        }
        return home;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (tickCount % 10 == 0) updateBossBar(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        home();
        props(level);
        lavaTick(level);
        switch (state()) {
            case SLEEPING -> {
                getNavigation().stop();
                if (tickCount % 10 == 0 && nearestPlayer(WAKE_RANGE) != null) wakeUp();
            }
            case WAKING -> waking(level);
            case TRANSFORM -> transforming(level);
            default -> fight(level);
        }
    }

    /** His hoard under him and the four ballistae round the crater (put there again after a restart). */
    private void props(ServerLevel level) {
        Vec3 h = home();
        if (hoard == null || !hoard.isAlive()) {
            hoard = ModEntities.DRAGON_HOARD.get().create(level);
            if (hoard != null) {
                hoard.moveTo(h.x, h.y, h.z, getYRot(), 0);
                level.addFreshEntity(hoard);
            }
        }
        ballistae.removeIf(b -> !b.isAlive());
        if (ballistae.isEmpty()) {
            for (int i = 0; i < 4; i++) {
                double ang = i * Mth.HALF_PI + Math.PI / 4;
                Vec3 p = new Vec3(h.x + Math.cos(ang) * BALLISTA_RANGE, h.y, h.z + Math.sin(ang) * BALLISTA_RANGE);
                Ballista b = ModEntities.BALLISTA.get().create(level);
                if (b == null) continue;
                b.moveTo(p.x, floorAt(p).y, p.z, (float) (Mth.atan2(h.z - p.z, h.x - p.x) * Mth.RAD_TO_DEG) - 90, 0);
                level.addFreshEntity(b);
                ballistae.add(b);
            }
        }
    }

    private Vec3 floorAt(Vec3 at) {
        BlockPos.MutableBlockPos p = BlockPos.containing(at.x, at.y + 4, at.z).mutable();
        for (int i = 0; i < 40; i++) {
            if (!level().getBlockState(p).getCollisionShape(level(), p).isEmpty()) return new Vec3(at.x, p.getY() + 1, at.z);
            p.move(0, -1, 0);
        }
        return new Vec3(at.x, home().y, at.z);
    }

    private void wakeUp() {
        if (state() != SLEEPING) return;
        entityData.set(STATE, WAKING);
        stateTick = 0;
        playSound(SoundEvents.ENDER_DRAGON_AMBIENT, 2.0F, 0.6F);
    }

    private void waking(ServerLevel level) {
        stateTick++;
        int t = stateTick;
        if (t == 10 || t == 30) playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 2.0F, 0.4F); // an eyelid
        if (t > 32 && t < 60 && t % 6 == 0) {
            playSound(SoundEvents.RAVAGER_STEP, 2.5F, 0.5F);
            level.sendParticles(ModParticles.EMBER.get(), getX(), getY() + 1, getZ(), 10, 2.0, 0.5, 2.0, 0.05);
        }
        if (t == 72) roar(level, 0.9F);
        if (t >= WAKE_TICKS) {
            entityData.set(STATE, AWAKE);
            cooldown = 20;
        }
    }

    private void updateBossBar(ServerLevel level) {
        for (ServerPlayer p : List.copyOf(bossBar.getPlayers())) {
            if (state() == SLEEPING || !p.isAlive() || p.level() != level || p.distanceToSqr(this) > 72 * 72) bossBar.removePlayer(p);
        }
        if (state() == SLEEPING) return;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 56 * 56) bossBar.addPlayer(p);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel) {
            bossBar.removeAllPlayers();
            Telegraphs.cancel(this);
            if (reason != RemovalReason.UNLOADED_TO_CHUNK) {
                if (hoard != null) hoard.discard();
                for (Ballista b : ballistae) b.discard();
            }
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ the lava round the crater

    private void lavaTick(ServerLevel level) {
        float r = lava();
        if (r <= 0) return;
        Vec3 h = home();
        if (tickCount % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double ang = random.nextDouble() * Mth.TWO_PI, d = r + random.nextDouble() * LAVA_WIDTH;
                double x = h.x + Math.cos(ang) * d, z = h.z + Math.sin(ang) * d;
                level.sendParticles(i == 0 ? ParticleTypes.LAVA : ModParticles.EMBER.get(), x, h.y + 0.2, z, 1, 0.1, 0.1, 0.1, 0.02);
            }
        }
        if (tickCount % 10 != 0) return;
        for (Player p : level.players()) { // whoever is in it burns
            if (p.isCreative() || p.isSpectator()) continue;
            double d = Math.hypot(p.getX() - h.x, p.getZ() - h.z);
            if (d < r || d > r + LAVA_WIDTH || p.getY() > h.y + 1.5 || p.getY() < h.y - 3) continue;
            p.igniteForSeconds(4);
            p.hurt(damageSources().lava(), 4);
        }
    }

    // ------------------------------------------------------------------ server: the change

    private void startTransform(ServerLevel level) {
        pendingTransform = false;
        attack = Attack.NONE;
        Telegraphs.cancel(this);
        getNavigation().stop();
        entityData.set(STATE, TRANSFORM);
        stateTick = 0;
        moveFrom = position();
    }

    private void transforming(ServerLevel level) {
        stateTick++;
        int t = stateTick;
        getNavigation().stop();
        if (t >= TRANSFORM_LEAP) { // up into the air over the crater
            setNoGravity(true);
            entityData.set(FLYING, true);
            float k = Math.min(1, (t - TRANSFORM_LEAP) / 60F);
            Vec3 h = home();
            Vec3 to = new Vec3(h.x + Math.cos(circleAngle) * CIRCLE, h.y + SKY, h.z + Math.sin(circleAngle) * CIRCLE);
            Vec3 p = moveFrom.lerp(to, k * k * (3 - 2 * k));
            setPos(p.x, p.y, p.z);
            setDeltaMovement(Vec3.ZERO);
        }
        if (t == TRANSFORM_LEAP) {
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 4.0F, 0.6F);
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 60, 3.0, 0.3, 3.0, 0.15);
            ScreenFx.shake(level, position(), 32, 10, 0.5F);
        }
        if (t > TRANSFORM_LEAP && t % 12 == 0) playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.7F);
        if (t == TRANSFORM_CRACK) { // the crater cracks
            Vec3 h = home();
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.4F);
            ScreenFx.shake(level, h, 40, 30, 1.0F);
            for (int i = 0; i < 120; i++) {
                double ang = random.nextDouble() * Mth.TWO_PI, d = LAVA_EDGE + random.nextDouble() * (LAVA_START - LAVA_EDGE + LAVA_WIDTH);
                double x = h.x + Math.cos(ang) * d, z = h.z + Math.sin(ang) * d;
                BlockState ground = level.getBlockState(BlockPos.containing(x, h.y - 0.5, z));
                if (!ground.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, h.y + 0.2, z, 4, 0.3, 0.3, 0.3, 0.2);
                level.sendParticles(ParticleTypes.LAVA, x, h.y + 0.2, z, 1, 0.2, 0.2, 0.2, 0.0);
            }
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(h) < 56 * 56) {
                    ScreenFx.play(p, ScreenEffect.FLASH, 8, 0.4F, 0xFF7020, "");
                    ScreenFx.play(p, ScreenEffect.TEXT, 50, 0.8F, 0xFF9040, "The lava rises - stay inside!");
                }
            }
        }
        if (t >= TRANSFORM_LAVA_FROM && t <= TRANSFORM_LAVA_TO) {
            float k = (t - TRANSFORM_LAVA_FROM) / (float) (TRANSFORM_LAVA_TO - TRANSFORM_LAVA_FROM);
            entityData.set(LAVA, Mth.lerp(k, LAVA_START, LAVA_EDGE));
            if (t % 10 == 0) playSound(SoundEvents.LAVA_AMBIENT, 4.0F, 0.6F);
        }
        if (t == 80) entityData.set(PHASE, (byte) 2);
        if (t == 92) roar(level, 1.0F);
        if (t >= TRANSFORM_TICKS) {
            entityData.set(STATE, AWAKE);
            cooldown = 30;
        }
    }

    // ------------------------------------------------------------------ server: the fight

    private boolean enraged() {
        return phase() == 2 && getHealth() < getMaxHealth() * 0.4F;
    }

    @Nullable
    private Player nearestPlayer(double range) {
        Player best = null;
        double bestD = range * range;
        for (Player p : level().players()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            double d = p.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private boolean validTarget(@Nullable LivingEntity t) {
        return t != null && t.isAlive() && t.level() == level() && t.distanceToSqr(this) < AGGRO_RANGE * AGGRO_RANGE
                && !(t instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    List<Player> fighters() {
        List<Player> out = new ArrayList<>();
        for (Player p : level().players()) if (validTarget(p)) out.add(p);
        return out;
    }

    private void fight(ServerLevel level) {
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (enraged() && speed != null && !speed.hasModifier(ENRAGED.id())) {
            speed.addTransientModifier(ENRAGED);
            if (attack == Attack.NONE && !downed()) start(level, Attack.ROAR, getTarget());
        }
        if (downed()) {
            downTick(level);
            return;
        }
        if (attack == Attack.NONE && pendingTransform) {
            startTransform(level);
            return;
        }
        if (attack == Attack.NONE || !validTarget(getTarget())) setTarget(targeting.pick(this, position(), AGGRO_RANGE, getTarget()));
        LivingEntity target = getTarget();
        if (attack != Attack.NONE) {
            attackTick(level, target);
            return;
        }
        if (phase() == 2) {
            circle(level, target);
            if (target != null && cooldown-- <= 0) start(level, choose(0), target);
            return;
        }
        setNoGravity(false);
        if (target == null) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(target, 20, 20);
        double dist = Math.sqrt(distanceToSqr(target.getX(), getY(), target.getZ()));
        if (cooldown > 0) cooldown--;
        if (cooldown <= 0 && dist < 16) {
            start(level, choose(dist), target);
            return;
        }
        if (dist > 7) {
            if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
        }
    }

    /** In the air: circling high over the crater. */
    private void circle(ServerLevel level, @Nullable LivingEntity target) {
        setNoGravity(true);
        entityData.set(FLYING, true);
        Vec3 h = home();
        circleAngle += enraged() ? 0.03 : 0.022;
        Vec3 want = new Vec3(h.x + Math.cos(circleAngle) * CIRCLE, h.y + SKY + Math.sin(tickCount * 0.05) * 0.6, h.z + Math.sin(circleAngle) * CIRCLE);
        Vec3 p = position().lerp(want, 0.08);
        float yaw = yawTo(position(), p.add(p.subtract(position()).scale(5)));
        setPos(p.x, p.y, p.z);
        setDeltaMovement(Vec3.ZERO);
        if (position().distanceToSqr(want) > 0.5) {
            lockedYaw = Mth.approachDegrees(getYRot(), yaw, 6);
            lockYaw();
        }
        if (tickCount % 30 == 0) playSound(SoundEvents.ENDER_DRAGON_FLAP, 2.5F, 0.8F);
    }

    private Attack choose(double dist) {
        List<Attack> options = new ArrayList<>();
        if (phase() == 1) {
            LivingEntity t = getTarget();
            boolean behind = t != null && Math.abs(Mth.wrapDegrees(yawTo(position(), t.position()) - getYRot())) > 110;
            if (behind) return Attack.TAIL;
            options.add(Attack.BREATH);
            options.add(Attack.BREATH);
            options.add(Attack.WINGS);
            options.add(Attack.TAIL);
        } else {
            options.add(Attack.BOMB);
            options.add(Attack.METEORS);
            options.add(Attack.DIVE);
            options.add(Attack.DIVE);
        }
        if (options.size() > 1) options.removeIf(a -> a == last);
        return options.get(random.nextInt(options.size()));
    }

    /** /bossattack: starts an attack by name now, if he can. */
    public boolean forceAttack(String name) {
        if (!(level() instanceof ServerLevel level) || state() != AWAKE || attack != Attack.NONE || downed()) return false;
        if (name.equals("takeoff")) {
            if (phase() != 1) return false;
            startTransform(level);
            return true;
        }
        if (name.equals("knockdown")) {
            if (phase() != 2) return false;
            knockDown(level);
            return true;
        }
        Attack a = switch (name) {
            case "breath" -> Attack.BREATH;
            case "tail" -> Attack.TAIL;
            case "wings" -> Attack.WINGS;
            case "bomb" -> Attack.BOMB;
            case "meteors" -> Attack.METEORS;
            case "dive" -> Attack.DIVE;
            default -> Attack.NONE;
        };
        if (a == Attack.NONE) return false;
        if (phase() == 1 && (a == Attack.BOMB || a == Attack.METEORS || a == Attack.DIVE)) return false;
        if (phase() == 2 && (a == Attack.BREATH || a == Attack.TAIL || a == Attack.WINGS)) return false;
        LivingEntity target = getTarget() != null ? getTarget() : nearestPlayer(AGGRO_RANGE);
        if (target == null) return false;
        start(level, a, target);
        return true;
    }

    private void lockYaw() {
        setYRot(lockedYaw);
        yRotO = lockedYaw;
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
    }

    private static Vec3 forward(float yaw) {
        return new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private void start(ServerLevel level, Attack a, @Nullable LivingEntity target) {
        attack = a;
        last = a;
        attackTick = 0;
        getNavigation().stop();
        if (phase() == 1 || a == Attack.ROAR) {
            lockedYaw = target != null ? yawTo(position(), target.position()) : getYRot();
            lockYaw();
        }
        level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + a.ordinal()));
        Vec3 at = position();
        Vec3 fwd = forward(lockedYaw);
        Vec3 snout = at.add(fwd.scale(4.5));
        switch (a) {
            case BREATH -> {
                Telegraphs.show(level, Telegraph.sector(snout, lockedYaw, BREATH_RADIUS, BREATH_HALF), a.impact, 6, this, this::burn);
                float spin = random.nextBoolean() ? SWEEP_SPIN : -SWEEP_SPIN;
                Telegraphs.sweep(level, Telegraph.sector(snout, lockedYaw, BREATH_RADIUS, SWEEP_HALF), SWEEP_FROM, SWEEP_TICKS, spin, 8, this, this::burn);
                breathSpin = spin;
                playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.8F);
            }
            case TAIL -> {
                Telegraphs.show(level, Telegraph.sector(at, lockedYaw + 180, TAIL_RADIUS, 90), a.impact, 5, this, e -> hit(e, TAIL_DAMAGE, 1.6, 0.6));
                playSound(SoundEvents.RAVAGER_ATTACK, 3.0F, 0.5F);
            }
            case WINGS -> {
                Telegraphs.show(level, Telegraph.sector(at, lockedYaw, GUST_RADIUS, GUST_HALF), a.impact, 6, this, e -> hit(e, GUST_DAMAGE, 3.2, 0.9));
                playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.6F);
            }
            case BOMB -> {
                Vec3 h = home();
                Vec3 to = target != null ? target.position() : h;
                laneDir = new Vec3(to.x - h.x, 0, to.z - h.z);
                if (laneDir.lengthSqr() < 1) laneDir = forward(random.nextFloat() * 360);
                laneDir = laneDir.normalize();
                laneFrom = new Vec3(h.x, h.y, h.z).subtract(laneDir.scale(BOMB_LENGTH / 2));
                moveFrom = position();
                float yaw = yawTo(Vec3.ZERO, laneDir);
                for (double d = 0; d < BOMB_LENGTH; d += 3) {
                    int warm = BOMB_APPROACH + (int) (d / BOMB_LENGTH * BOMB_RUN);
                    Telegraphs.sweep(level, Telegraph.rect(floorAt(laneFrom.add(laneDir.scale(d))), yaw, 3.2F, BOMB_HALF), warm, BOMB_BURN, 0, 10, this,
                            e -> {
                                hit(e, BOMB_DAMAGE, 0.3, 0.3);
                                e.igniteForSeconds(3);
                            });
                }
                playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.9F);
            }
            case METEORS -> {
                waves.clear();
                Vec3 h = home();
                List<Player> players = fighters();
                for (int w = 0; w < METEOR_WAVES.length; w++) {
                    List<Vec3> wave = new ArrayList<>();
                    for (Player p : players) wave.add(p.position());
                    for (int i = 0; i < 7; i++) {
                        double ang = random.nextDouble() * Mth.TWO_PI, r = Math.sqrt(random.nextDouble()) * (LAVA_EDGE - 1);
                        wave.add(floorAt(new Vec3(h.x + Math.cos(ang) * r, h.y, h.z + Math.sin(ang) * r)));
                    }
                    for (Vec3 p : wave) {
                        Telegraphs.show(level, Telegraph.circle(p, METEOR_RADIUS), METEOR_WAVES[w], 5, this, e -> {
                            hit(e, METEOR_DAMAGE, 0.7, 0.5);
                            e.igniteForSeconds(3);
                        });
                    }
                    waves.add(wave);
                }
                playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.5F);
            }
            case DIVE -> {
                moveFrom = position();
                playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.5F);
            }
            case ROAR -> roar(level, 0.9F);
            default -> {}
        }
    }

    private float breathSpin;

    private void attackTick(ServerLevel level, @Nullable LivingEntity target) {
        attackTick++;
        int t = attackTick;
        if (phase() == 1) {
            setDeltaMovement(0, getDeltaMovement().y, 0);
            lockYaw();
        }
        switch (attack) {
            case BREATH -> {
                if (t > SWEEP_FROM && t <= SWEEP_FROM + SWEEP_TICKS) { // the head turns with the fire
                    lockedYaw += breathSpin * 0.6F;
                    lockYaw();
                }
                if (t >= attack.impact && t < SWEEP_FROM + SWEEP_TICKS) {
                    float yaw = t < SWEEP_FROM ? lockedYaw : lockedYaw + breathSpin * 0.4F * (t - SWEEP_FROM);
                    breathe(level, position().add(forward(getYRot()).scale(5.5)).add(0, 3.2, 0), forward(yaw).add(0, -0.25, 0), BREATH_RADIUS);
                }
                if (t == attack.impact) playSound(SoundEvents.ENDER_DRAGON_SHOOT, 4.0F, 0.6F);
                if (t > attack.impact && t % 8 == 0) playSound(SoundEvents.BLAZE_SHOOT, 3.0F, 0.5F);
            }
            case TAIL -> {
                if (t == attack.impact) {
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.4F);
                    ScreenFx.shake(level, position(), 20, 6, 0.4F);
                    Vec3 b = position().add(forward(lockedYaw + 180).scale(5));
                    level.sendParticles(ParticleTypes.CLOUD, b.x, b.y + 0.3, b.z, 20, 3.0, 0.2, 3.0, 0.05);
                }
            }
            case WINGS -> {
                if (t == attack.impact) {
                    playSound(SoundEvents.ENDER_DRAGON_FLAP, 4.0F, 0.4F);
                    ScreenFx.shake(level, position(), 24, 8, 0.5F);
                    Vec3 f = forward(lockedYaw);
                    for (int i = 0; i < 30; i++) {
                        Vec3 p = position().add(f.scale(2 + random.nextDouble() * 4)).add((random.nextDouble() - 0.5) * 6, 0.5, (random.nextDouble() - 0.5) * 6);
                        level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 0, f.x * 1.2, 0.05, f.z * 1.2, 1.0);
                        level.sendParticles(ModParticles.ASH.get(), p.x, p.y + 1, p.z, 0, f.x * 0.8, 0.1, f.z * 0.8, 1.0);
                    }
                }
            }
            case BOMB -> bombTick(level, t);
            case METEORS -> {
                if (t == 20) roarSound();
                for (int w = 0; w < METEOR_WAVES.length; w++) {
                    int fall = METEOR_WAVES[w] - METEOR_FALL;
                    if (t >= fall && t < METEOR_WAVES[w]) { // burning rocks streaking down onto their circles
                        float k = (t - fall + 1) / (float) METEOR_FALL;
                        for (Vec3 p : waves.get(w)) {
                            Vec3 sky = p.add(6, 22, 3);
                            Vec3 q = sky.lerp(p, k);
                            level.sendParticles(ModParticles.DRAGON_FIRE.get(), q.x, q.y, q.z, 2, 0.2, 0.2, 0.2, 0.01);
                            level.sendParticles(ParticleTypes.LARGE_SMOKE, q.x, q.y + 0.5, q.z, 1, 0.1, 0.1, 0.1, 0.01);
                        }
                    }
                    if (t == METEOR_WAVES[w]) {
                        for (Vec3 p : waves.get(w)) {
                            level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.5, p.z, 1, 0, 0, 0, 0);
                            level.sendParticles(ModParticles.EMBER.get(), p.x, p.y + 0.3, p.z, 16, 1.0, 0.3, 1.0, 0.12);
                            level.sendParticles(ModParticles.DRAGON_FIRE.get(), p.x, p.y + 0.4, p.z, 8, 0.8, 0.3, 0.8, 0.04);
                        }
                        Vec3 h = home();
                        level.playSound(null, h.x, h.y, h.z, SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 0.6F);
                        ScreenFx.shake(level, h, 40, 8, 0.45F);
                    }
                }
                circle(level, target);
            }
            case DIVE -> diveTick(level, target, t);
            default -> {
                if (phase() == 2) circle(level, target);
            }
        }
        if (t >= attack.length) {
            attack = Attack.NONE;
            cooldown = enraged() ? 16 + random.nextInt(12) : (phase() == 2 ? 30 : 32) + random.nextInt(14);
        }
    }

    /** The bombing run: to the lane's start, low along it breathing fire, back up. */
    private void bombTick(ServerLevel level, int t) {
        Vec3 h = home();
        double low = h.y + 6;
        if (t <= BOMB_APPROACH) {
            float k = t / (float) BOMB_APPROACH;
            Vec3 p = moveFrom.lerp(new Vec3(laneFrom.x, low, laneFrom.z), k * k * (3 - 2 * k));
            setPos(p.x, p.y, p.z);
            lockedYaw = yawTo(Vec3.ZERO, laneDir);
            lockYaw();
            if (t == BOMB_APPROACH) level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + Attack.BOMB.ordinal()));
        } else if (t <= BOMB_APPROACH + BOMB_RUN) {
            float k = (t - BOMB_APPROACH) / (float) BOMB_RUN;
            Vec3 p = laneFrom.add(laneDir.scale(BOMB_LENGTH * k));
            setPos(p.x, low, p.z);
            lockYaw();
            Vec3 mouth = position().add(laneDir.scale(4)).add(0, 1.5, 0);
            breathe(level, mouth, laneDir.scale(0.3).add(0, -1, 0), 7);
            if (t % 6 == 0) {
                playSound(SoundEvents.BLAZE_SHOOT, 3.0F, 0.5F);
                ScreenFx.shake(level, position(), 16, 4, 0.25F);
            }
        } else {
            Vec3 h2 = home();
            Vec3 to = new Vec3(h2.x + Math.cos(circleAngle) * CIRCLE, h2.y + SKY, h2.z + Math.sin(circleAngle) * CIRCLE);
            Vec3 p = position().lerp(to, 0.12);
            setPos(p.x, p.y, p.z);
        }
        setDeltaMovement(Vec3.ZERO);
    }

    /** The dive: a circle under a player, up a little, down onto it, and back up. */
    private void diveTick(ServerLevel level, @Nullable LivingEntity target, int t) {
        setDeltaMovement(Vec3.ZERO);
        if (t == DIVE_LOCK) {
            aim = target != null ? floorAt(target.position()) : floorAt(home());
            Telegraphs.show(level, Telegraph.circle(aim, DIVE_RADIUS), Attack.DIVE.impact - DIVE_LOCK, 6, this, e -> hit(e, DIVE_DAMAGE, 1.8, 0.8));
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.7F);
        }
        if (t < DIVE_START) { // rising for it
            Vec3 p = position().add(0, 0.12, 0);
            setPos(p.x, p.y, p.z);
            if (target != null) {
                lockedYaw = yawTo(position(), target.position());
                lockYaw();
            }
            return;
        }
        if (t == DIVE_START) {
            moveFrom = position();
            level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + Attack.DIVE.ordinal()));
            lockedYaw = yawTo(position(), aim);
            lockYaw();
        }
        if (t <= Attack.DIVE.impact) {
            float k = (t - DIVE_START) / (float) (Attack.DIVE.impact - DIVE_START);
            Vec3 p = moveFrom.lerp(aim, k * k);
            setPos(p.x, p.y, p.z);
            level.sendParticles(ModParticles.EMBER.get(), p.x, p.y + 1, p.z, 3, 1.0, 0.5, 1.0, 0.05);
            if (t == Attack.DIVE.impact) {
                playSound(SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.6F);
                ScreenFx.shake(level, aim, 32, 14, 0.8F);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, aim.x, aim.y + 0.5, aim.z, 1, 0, 0, 0, 0);
                level.sendParticles(ModParticles.EMBER.get(), aim.x, aim.y + 0.3, aim.z, 40, 2.0, 0.3, 2.0, 0.15);
                BossDeath.ring(level, ParticleTypes.CLOUD, aim.add(0, 0.2, 0), DIVE_RADIUS, 40, 0.15);
            }
            return;
        }
        Vec3 h = home(); // back up
        Vec3 to = new Vec3(h.x + Math.cos(circleAngle) * CIRCLE, h.y + SKY, h.z + Math.sin(circleAngle) * CIRCLE);
        Vec3 p = position().lerp(to, 0.1);
        setPos(p.x, p.y, p.z);
    }

    // ------------------------------------------------------------------ the ballistae

    /** Ballista: a bolt has struck him. */
    void ballistaHit(Player shooter, Entity bolt) {
        if (!(level() instanceof ServerLevel level) || state() != AWAKE) return;
        hurt(damageSources().thrown(bolt, shooter), BALLISTA_DAMAGE);
        level.sendParticles(ParticleTypes.CRIT, bolt.getX(), bolt.getY(), bolt.getZ(), 20, 0.4, 0.4, 0.4, 0.3);
        playSound(SoundEvents.ENDER_DRAGON_HURT, 4.0F, 0.6F);
        if (phase() != 2 || downed() || !flying() || attack == Attack.DIVE || tickCount - lastDown < KNOCK_REST) return;
        boltHits.put(shooter.getUUID(), tickCount);
        boltHits.values().removeIf(at -> tickCount - at > KNOCK_WINDOW);
        int need = Math.max(1, Math.min(3, fighters().size()));
        int have = boltHits.size();
        for (Player p : fighters()) {
            if (p instanceof ServerPlayer sp) {
                ScreenFx.play(sp, ScreenEffect.TEXT, 30, 0.8F, 0xFFD080, "Ballista hit! " + Math.min(have, need) + "/" + need);
            }
        }
        if (have >= need) knockDown(level);
    }

    /** Brought down: he falls to the ground under him, lies there stunned, and springs up again. */
    private void knockDown(ServerLevel level) {
        Telegraphs.cancel(this);
        attack = Attack.NONE;
        boltHits.clear();
        entityData.set(DOWNED, true);
        downTick = 0;
        moveFrom = position();
        aim = floorAt(position());
        level.broadcastEntityEvent(this, EVENT_FALL);
        playSound(SoundEvents.ENDER_DRAGON_HURT, 4.0F, 0.4F);
    }

    private void downTick(ServerLevel level) {
        downTick++;
        int t = downTick;
        setDeltaMovement(Vec3.ZERO);
        if (t <= 12) { // falling
            float k = t / 12F;
            Vec3 p = moveFrom.lerp(aim, k * k);
            setPos(p.x, p.y, p.z);
        }
        if (t == 12) {
            entityData.set(FLYING, false);
            setNoGravity(false);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.5F);
            playSound(SoundEvents.ENDER_DRAGON_HURT, 4.0F, 0.5F);
            ScreenFx.shake(level, position(), 36, 18, 0.9F);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ModParticles.ASH.get(), getX(), getY() + 1, getZ(), 60, 3.0, 1.0, 3.0, 0.08);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(this) < 48 * 48) ScreenFx.play(p, ScreenEffect.TEXT, 50, 0.8F, 0xFFE070, "He is down - strike!");
            }
        }
        if (t > 12 && t < DOWN_TICKS - 28 && t % 8 == 0) {
            Vec3 h = position().add(0, 2.5, 0);
            double a = t * 0.5;
            level.sendParticles(ParticleTypes.CRIT, h.x + Math.cos(a) * 1.5, h.y, h.z + Math.sin(a) * 1.5, 3, 0.1, 0.1, 0.1, 0);
        }
        if (t == DOWN_TICKS - 28) {
            level.broadcastEntityEvent(this, EVENT_RECOVER);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.7F);
        }
        if (t >= DOWN_TICKS - 12) { // up again
            setNoGravity(true);
            entityData.set(FLYING, true);
            Vec3 p = position().add(0, 0.5, 0);
            setPos(p.x, p.y, p.z);
        }
        if (t >= DOWN_TICKS) {
            entityData.set(DOWNED, false);
            lastDown = tickCount;
            cooldown = 30;
        }
    }

    // ------------------------------------------------------------------ hits and effects

    float damageScale() {
        var attr = getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null || attr.getBaseValue() <= 0 ? 1.0F : (float) (attr.getValue() / attr.getBaseValue());
    }

    private void burn(LivingEntity e) {
        hit(e, BREATH_DAMAGE, 0.4, 0.2);
        e.igniteForSeconds(4);
    }

    private void hit(LivingEntity e, float damage, double push, double up) {
        if (!e.hurt(damageSources().mobAttack(this), damage * damageScale())) return;
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0) return;
        e.setDeltaMovement(e.getDeltaMovement().add(dx / len * push * resist, up * resist, dz / len * push * resist));
        e.hurtMarked = true;
    }

    /** Dragon fire out of his mouth along a direction (as far as it reaches). */
    private void breathe(ServerLevel level, Vec3 mouth, Vec3 dir, float reach) {
        Vec3 d = dir.normalize();
        for (int i = 0; i < 6; i++) {
            double s = 0.6 + random.nextDouble() * 0.6;
            level.sendParticles(ModParticles.DRAGON_FIRE.get(), mouth.x, mouth.y, mouth.z, 0,
                    d.x * s + (random.nextDouble() - 0.5) * 0.25, d.y * s + (random.nextDouble() - 0.5) * 0.15,
                    d.z * s + (random.nextDouble() - 0.5) * 0.25, 1.0);
        }
        Vec3 far = mouth.add(d.scale(reach * (0.5 + random.nextDouble() * 0.5)));
        level.sendParticles(ModParticles.EMBER.get(), far.x, far.y, far.z, 2, 0.6, 0.3, 0.6, 0.05);
    }

    private void roarSound() {
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 4.0F, 0.6F);
    }

    /** His roar: the whole crater shakes, the edges of the screen glowing with heat, the air shimmering. */
    private void roar(ServerLevel level, float strength) {
        roarSound();
        playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.5F);
        ScreenFx.shake(level, position(), 48, 20, strength);
        ScreenFx.playNear(level, position(), 48, ScreenEffect.VIGNETTE, 30, strength, 0x8A2A08);
        ScreenFx.playNear(level, position(), 24, ScreenEffect.FOV, 16, strength * 0.6F, -1);
        BossDeath.on(this, Anchor.AREA, 8, ScreenEffect.HEAT, 40, 0.8F, -1);
        level.sendParticles(ModParticles.EMBER.get(), getX(), getY() + 3, getZ(), 40, 2.5, 1.5, 2.5, 0.12);
    }

    // ------------------------------------------------------------------ dying

    @Override
    protected void tickDeath() {
        dying++;
        if (!(level() instanceof ServerLevel level)) return;
        int t = dying;
        if (t == 1) {
            BossDeath.sound(this, SoundEvents.ENDER_DRAGON_DEATH, 3.0F, 0.7F);
            BossDeath.on(this, Anchor.ENTITY, 0, ScreenEffect.OUTLINE, 80, 1.0F, 0xFF8030);
            Telegraphs.cancel(this);
            moveFrom = position();
            aim = floorAt(position());
        }
        if (t <= 20) { // out of the sky into the crater
            float k = t / 20F;
            Vec3 p = moveFrom.lerp(aim, k * k);
            setPos(p.x, p.y, p.z);
            setDeltaMovement(Vec3.ZERO);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2, getZ(), 4, 1.5, 0.8, 1.5, 0.02);
        }
        if (t == 20) {
            setNoGravity(false);
            BossDeath.sound(this, SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.5F);
            BossDeath.shake(this, 30, 1.0F);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ModParticles.ASH.get(), getX(), getY() + 1, getZ(), 80, 3.0, 1.0, 3.0, 0.1);
        }
        if (t >= 70 && t <= 90) { // one last breath of fire into the sky
            float yaw = getYRot();
            Vec3 mouth = position().add(forward(yaw).scale(4.5)).add(0, 3.5, 0);
            breathe(level, mouth, new Vec3(0, 1, 0).add(forward(yaw).scale(0.2)), 12);
            if (t % 5 == 0) BossDeath.sound(this, SoundEvents.BLAZE_SHOOT, 3.0F, 0.4F);
        }
        if (t > 20) entityData.set(LAVA, Math.max(0, lava() > 0 ? lava() + 0.4F : 0)); // the lava sinking back
        if (lava() > LAVA_START + LAVA_WIDTH) entityData.set(LAVA, 0F);
        if (t > 90 && t % 4 == 0) level.sendParticles(ModParticles.EMBER.get(), getX(), getY() + 1, getZ(), 6, 2.5, 0.6, 2.5, 0.03);
        if (t == DEATH_TICKS) {
            BossDeath.sound(this, SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.4F);
            BossDeath.screen(this, ScreenEffect.FLASH, 8, 0.3F, 0xFF9040);
            level.sendParticles(ModParticles.EMBER.get(), getX(), getY() + 2, getZ(), 80, 3.0, 1.5, 3.0, 0.1);
            level.sendParticles(ModParticles.ASH.get(), getX(), getY() + 2, getZ(), 80, 3.0, 1.5, 3.0, 0.05);
        }
        if (t >= DEATH_TICKS) remove(RemovalReason.KILLED);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) Telegraphs.cancel(this);
        super.die(source);
    }

    // ------------------------------------------------------------------ damage, sounds, saving

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (!level().isClientSide) entityData.set(TARGET_ID, target == null ? -1 : target.getId());
    }

    @Override
    public int markedTargetId() {
        return state() == SLEEPING || !isAlive() ? -1 : entityData.get(TARGET_ID);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            unclamped = true;
            try {
                return super.hurt(source, amount);
            } finally {
                unclamped = false;
            }
        }
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION) && source.getEntity() == this) return false;
        if (state() == WAKING || state() == TRANSFORM) return false;
        if (state() == SLEEPING) {
            if (!level().isClientSide) wakeUp();
            return false;
        }
        if (downed()) amount *= 1.5F;
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) targeting.onHurt(source, level().getGameTime());
        return hurt;
    }

    @Override
    public void setHealth(float health) {
        if (!unclamped && tickCount > 0 && !level().isClientSide && phase() == 1 && health < getMaxHealth() * 0.5F) {
            health = getMaxHealth() * 0.5F;
            pendingTransform = true;
        }
        super.setHealth(health);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return state() == AWAKE ? SoundEvents.ENDER_DRAGON_AMBIENT : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDER_DRAGON_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 1.5F, 0.5F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Awake", state() != SLEEPING);
        tag.putByte("Phase", (byte) phase());
        Vec3 h = home();
        tag.putDouble("HomeX", h.x);
        tag.putDouble("HomeY", h.y);
        tag.putDouble("HomeZ", h.z);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeY")) {
            home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
            entityData.set(HOME, new Vector3f((float) home.x, (float) home.y, (float) home.z));
        }
        int phase = Math.max(1, tag.getByte("Phase"));
        entityData.set(PHASE, (byte) phase);
        entityData.set(STATE, tag.getBoolean("Awake") ? AWAKE : SLEEPING);
        if (phase == 2) {
            entityData.set(LAVA, LAVA_EDGE);
            entityData.set(FLYING, true);
            setNoGravity(true);
        }
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    @Override
    public void setCustomName(@Nullable net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }
}
