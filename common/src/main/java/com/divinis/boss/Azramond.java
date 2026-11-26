package com.divinis.boss;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.divinis.registry.ModEntities;
import com.divinis.registry.ModParticles;
import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenEffect.Anchor;
import com.divinis.screenfx.ScreenFx;
import com.divinis.Divinis;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.LivingHealEvent;

/**
 * Azramond, the Lich King (/summon divinis:azramond where his throne is to stand - his home): a skeleton king in a
 * golden crown and tattered robes, floating a block over the floor, a bone staff in his hand. He sleeps slumped on his
 * throne of bones (LichThrone), the crown askew; a player coming near or a blow wakes him: green fire kindles in his
 * eyes, he sets his crown straight, rises from the throne and lifts his staff. Every attack is telegraphed
 * (boss.Telegraphs: whoever stands in the red when it fills is hit) - with good movement he can be beaten without
 * taking a hit.
 *
 * Phase 1:
 * - Soul fire: three balls of green fire onto his foes (the circles);
 * - The dead rise: heaps of bones on the floor (LichBones, the circles) - three seconds later each rises as a skeleton,
 *   unless broken first (two blows); whoever stands on one as it rises is hurt;
 * - Frost: a ring of ice bursts out around him - get out of its reach.
 * At half health he smashes his throne, the room darkens, wings of shadow tear out of his back and his crown catches
 * green fire (he cannot be hurt meanwhile).
 * Phase 2, besides the first attacks:
 * - The land of the dead: the world loses its colour for eight seconds; ghosts (LichGhost) drift from the room's edge
 *   towards the players - one touch hurts; circles of golden light (marked with light) are safe, ghosts fade in them;
 * - The shades of the fallen: a shade of every player (LichShade) walks their path again two seconds behind them and
 *   strikes where they struck - do not walk back over your own steps;
 * - The curse: a player is marked: for eight seconds healing hurts them instead;
 * - The dance of bones: spikes of bone (LichSpike) burst from the floor along a spiral out from him (two when enraged).
 * Below 40% health he is enraged: faster, shorter rests, five balls of soul fire.
 * Dying, he sinks; his crown falls and rolls away over the floor; he crumbles to dust in his robe.
 * Timings match the animations in client/AzramondModelData (tools/models/build_azramond.py).
 * Effects: green soul fire (ModParticles.NECRO_FLAME), purple runes of his curse (CURSE), golden light (HOLY_LIGHT),
 * grave dust (BONE_DUST); the world grey in the land of the dead, darkness as his throne breaks.
 */
@EventBusSubscriber(modid = Divinis.MODID)
public class Azramond extends Monster implements BossTargeting.Marked {
    public static final byte SLEEPING = 0, WAKING = 1, AWAKE = 2, TRANSFORM = 3;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Azramond.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(Azramond.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(Azramond.class, EntityDataSerializers.INT);

    public enum Attack {
        NONE(0, 0), SOULFIRE(32, 28), RAISE(40, 20), FROST(36, 24), LANDS(40, 20), SHADES(40, 20), CURSE(28, 16), DANCE(50, 18), ROAR(30, 0);

        public final int length, impact;

        Attack(int length, int impact) {
            this.length = length;
            this.impact = impact;
        }
    }

    private static final byte EVENT_ATTACK = (byte) 225; // + attack ordinal (the Hydra 200-219)

    public static final int WAKE_TICKS = 100, WAKE_EYES = 10, TRANSFORM_TICKS = 120, DEATH_TICKS = 100;
    private static final int WAKE_STAND = 64, TRANSFORM_SMASH = 30, TRANSFORM_WINGS = 70, TRANSFORM_CROWN = 84;
    private static final double WAKE_RANGE = 12, AGGRO_RANGE = 44, HOVER = 1.0;

    private static final float FIRE_DAMAGE = 12, FIRE_RADIUS = 2.4F;
    private static final int FIRE_CAST = 16;
    private static final int BONES = 4, MAX_RAISED = 6;
    private static final float FROST_DAMAGE = 12, FROST_RADIUS = 7.0F;
    public static final int LANDS_TICKS = 160;
    private static final float LIGHT_RADIUS = 2.6F;
    public static final int SHADE_DELAY = 40, SHADE_LIFE = 200;
    public static final int CURSE_TICKS = 160;
    private static final float SPIKE_DAMAGE = 12, SPIKE_RADIUS = 1.3F;
    private static final int SPIKES = 32;

    private static final AttributeModifier ENRAGED = new AttributeModifier(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "azramond_enraged"),
            0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    /** skeletons raised from his heaps of bones carry this tag (they go when he does) */
    public static final String RAISED_TAG = "divinis_azramond_raised";

    /** players under his curse, and until when (game time) */
    private static final Map<UUID, Long> CURSED = new HashMap<>();

    // client: animations
    public final AnimationState sleepAnimation = new AnimationState();
    public final AnimationState wakeAnimation = new AnimationState();
    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState fireAnimation = new AnimationState();
    public final AnimationState raiseAnimation = new AnimationState();
    public final AnimationState frostAnimation = new AnimationState();
    public final AnimationState landsAnimation = new AnimationState();
    public final AnimationState shadesAnimation = new AnimationState();
    public final AnimationState curseAnimation = new AnimationState();
    public final AnimationState danceAnimation = new AnimationState();
    public final AnimationState roarAnimation = new AnimationState();
    public final AnimationState transformAnimation = new AnimationState();
    public final AnimationState deathAnimation = new AnimationState();
    public int dying;
    private int clientAttackTicks, clientWakeStart = -1;

    // server: the fight
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.GREEN,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private final BossTargeting targeting = new BossTargeting();
    private Attack attack = Attack.NONE, last = Attack.NONE;
    private int attackTick, cooldown = 20, stateTick, lastLands = -1000, lastShades = -1000;
    private boolean pendingTransform, unclamped;
    private float lockedYaw;
    @Nullable private Vec3 home;
    private float homeYaw;
    @Nullable private LichThrone throne;
    private final List<Vec3> spots = new ArrayList<>();
    private final List<Entity> summoned = new ArrayList<>();
    /** the circles of light in the land of the dead, and until when they shine */
    private final List<Vec3> lights = new ArrayList<>();
    private int lightsUntil = -1;

    /** what the players did, tick by tick (the shades walk it again) */
    record Step(int tick, Vec3 pos, float yaw, float pitch, boolean swing) {}

    private final Map<UUID, ArrayDeque<Step>> steps = new HashMap<>();
    private final Map<UUID, Boolean> wasSwinging = new HashMap<>();

    public Azramond(EntityType<? extends Azramond> type, Level level) {
        super(type, level);
        this.xpReward = 320;
        setPersistenceRequired();
        bossBar.setVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 420.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 44.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, SLEEPING);
        builder.define(PHASE, (byte) 1);
        builder.define(TARGET_ID, -1);
    }

    @Override
    protected void registerGoals() {}

    public byte state() {
        return entityData.get(STATE);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    public boolean isAttacking() {
        return clientAttackTicks > 0 || state() != AWAKE;
    }

    /** Client: does green fire burn in his eyes? */
    public boolean glowing() {
        return state() != SLEEPING && !(state() == WAKING && (clientWakeStart < 0 || tickCount - clientWakeStart < WAKE_EYES));
    }

    /** Client: are his wings there? */
    public boolean winged() {
        return phase() == 2 || state() == TRANSFORM;
    }

    /** Is a point in one of the circles of light (the land of the dead)? */
    boolean inLight(Vec3 at) {
        if (tickCount > lightsUntil) return false;
        for (Vec3 l : lights) if (l.distanceToSqr(at.x, l.y, at.z) < LIGHT_RADIUS * LIGHT_RADIUS) return true;
        return false;
    }

    /** Where a player was delay ticks ago, and what they did (the shades), if it is known. */
    @Nullable
    Step stepOf(UUID player, int delay) {
        ArrayDeque<Step> list = steps.get(player);
        if (list == null) return null;
        int want = tickCount - delay;
        for (Step s : list) if (s.tick() == want) return s;
        return null;
    }

    // ------------------------------------------------------------------ client

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!level().isClientSide || !STATE.equals(key)) return;
        if (state() == WAKING) {
            wakeAnimation.start(tickCount);
            clientWakeStart = tickCount;
        } else {
            wakeAnimation.stop();
        }
        if (state() == TRANSFORM) {
            stopAttacks();
            transformAnimation.start(tickCount);
        } else {
            transformAnimation.stop();
        }
    }

    private void stopAttacks() {
        for (AnimationState s : new AnimationState[] {fireAnimation, raiseAnimation, frostAnimation, landsAnimation, shadesAnimation,
                curseAnimation, danceAnimation, roarAnimation}) s.stop();
    }

    @Override
    public void handleEntityEvent(byte id) {
        int index = (id & 0xFF) - (EVENT_ATTACK & 0xFF);
        if (index > 0 && index < Attack.values().length) {
            Attack a = Attack.values()[index];
            stopAttacks();
            (switch (a) {
                case SOULFIRE -> fireAnimation;
                case RAISE -> raiseAnimation;
                case FROST -> frostAnimation;
                case LANDS -> landsAnimation;
                case SHADES -> shadesAnimation;
                case CURSE -> curseAnimation;
                case DANCE -> danceAnimation;
                default -> roarAnimation;
            }).start(tickCount);
            clientAttackTicks = a == Attack.CURSE ? 28 : Math.min(a.length, 40);
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
        if (s == AWAKE) idleAnimation.startIfStopped(tickCount);
        else idleAnimation.stop();
        if (clientAttackTicks > 0) clientAttackTicks--;
        if (!glowing()) return;
        // green fire from his eyes and the staff's crystal; the crown burning in his second phase
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        if (tickCount % 5 == 0) {
            Vec3 eye = position().add(0, 3.0, 0).add(fwd.scale(0.35));
            level().addParticle(ModParticles.NECRO_FLAME.get(), eye.x, eye.y, eye.z, 0, 0.01, 0);
            Vec3 gem = position().add(0, 3.6, 0).add(right.scale(0.85)).add(fwd.scale(0.3));
            level().addParticle(ModParticles.NECRO_FLAME.get(), gem.x, gem.y, gem.z, 0, 0.02, 0);
        }
        if (phase() == 2 && tickCount % 2 == 0) {
            Vec3 crown = position().add(0, 3.55, 0);
            level().addParticle(ModParticles.NECRO_FLAME.get(), crown.x + (random.nextDouble() - 0.5) * 0.6, crown.y,
                    crown.z + (random.nextDouble() - 0.5) * 0.6, 0, 0.04, 0);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(4.0, 1.5, 4.0); // the staff, the wings
    }

    // ------------------------------------------------------------------ server: states

    private Vec3 home() {
        if (home == null) {
            home = position();
            homeYaw = getYRot();
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
        summoned.removeIf(e -> !e.isAlive());
        record(level);
        lightsTick(level);
        if (phase() == 1 && state() != TRANSFORM && (throne == null || !throne.isAlive())) placeThrone(level);
        switch (state()) {
            case SLEEPING -> {
                setNoGravity(false);
                setYRot(homeYaw);
                setYBodyRot(homeYaw);
                setYHeadRot(homeYaw);
                if (tickCount % 10 == 0 && nearestPlayer(WAKE_RANGE) != null) wakeUp();
            }
            case WAKING -> waking(level);
            case TRANSFORM -> transforming(level);
            default -> fight(level);
        }
    }

    /** His throne, where he was summoned (it stands until he breaks it). */
    private void placeThrone(ServerLevel level) {
        throne = ModEntities.LICH_THRONE.get().create(level);
        if (throne == null) return;
        Vec3 h = home();
        throne.moveTo(h.x, h.y, h.z, homeYaw, 0);
        level.addFreshEntity(throne);
    }

    private void wakeUp() {
        if (state() != SLEEPING) return;
        entityData.set(STATE, WAKING);
        stateTick = 0;
        playSound(SoundEvents.SKELETON_AMBIENT, 3.0F, 0.3F);
    }

    private void waking(ServerLevel level) {
        stateTick++;
        int t = stateTick;
        if (t == WAKE_EYES) {
            playSound(SoundEvents.SOUL_ESCAPE.value(), 3.0F, 0.5F);
            Vec3 h = position().add(0, 2.2, 0);
            level.sendParticles(ModParticles.NECRO_FLAME.get(), h.x, h.y, h.z, 16, 0.3, 0.3, 0.3, 0.02);
        }
        if (t == 36) playSound(SoundEvents.ARMOR_EQUIP_GOLD.value(), 2.5F, 0.6F); // the crown set straight
        if (t >= WAKE_STAND) { // up from the throne, floating
            setNoGravity(true);
            hover(0.08);
        }
        if (t == WAKE_STAND) playSound(SoundEvents.BONE_BLOCK_STEP, 2.0F, 0.5F);
        if (t == 84) { // the staff lifted: a burst of green fire
            playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 3.0F, 0.6F);
            Vec3 g = position().add(0, 4.2, 0);
            level.sendParticles(ModParticles.NECRO_FLAME.get(), g.x, g.y, g.z, 40, 0.4, 0.4, 0.4, 0.06);
            scream(level, 0.5F);
        }
        if (t >= WAKE_TICKS) {
            entityData.set(STATE, AWAKE);
            cooldown = 20;
        }
    }

    /** Floats towards HOVER blocks over the floor, bobbing. */
    private void hover(double rate) {
        double floor = floorUnder();
        double want = floor + HOVER + Math.sin(tickCount * 0.08) * 0.15;
        Vec3 v = getDeltaMovement();
        setDeltaMovement(v.x, (want - getY()) * rate * 4, v.z);
    }

    private double floorUnder() {
        BlockPos.MutableBlockPos p = BlockPos.containing(getX(), getY() + 0.5, getZ()).mutable();
        for (int i = 0; i < 12; i++) {
            if (!level().getBlockState(p).getCollisionShape(level(), p).isEmpty()) return p.getY() + 1;
            p.move(0, -1, 0);
        }
        return home().y;
    }

    private void updateBossBar(ServerLevel level) {
        for (ServerPlayer p : List.copyOf(bossBar.getPlayers())) {
            if (state() == SLEEPING || !p.isAlive() || p.level() != level || p.distanceToSqr(this) > 64 * 64) bossBar.removePlayer(p);
        }
        if (state() == SLEEPING) return;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 48 * 48) bossBar.addPlayer(p);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level) {
            bossBar.removeAllPlayers();
            Telegraphs.cancel(this);
            if (throne != null && reason != RemovalReason.UNLOADED_TO_CHUNK) throne.discard();
            for (Entity e : summoned) e.discard();
            for (Skeleton s : level.getEntitiesOfClass(Skeleton.class, getBoundingBox().inflate(64), s -> s.getTags().contains(RAISED_TAG))) {
                s.discard();
            }
        }
        super.remove(reason);
    }

    /** What every player near does, tick by tick, for the shades to walk again. */
    private void record(ServerLevel level) {
        if (state() != AWAKE || phase() != 2) {
            steps.clear();
            return;
        }
        for (Player p : fighters()) {
            boolean swing = p.swinging && !wasSwinging.getOrDefault(p.getUUID(), false);
            wasSwinging.put(p.getUUID(), p.swinging);
            ArrayDeque<Step> list = steps.computeIfAbsent(p.getUUID(), u -> new ArrayDeque<>());
            list.addLast(new Step(tickCount, p.position(), p.getYRot(), p.getXRot(), swing));
            while (list.size() > SHADE_DELAY + SHADE_LIFE + 20) list.removeFirst();
        }
    }

    // ------------------------------------------------------------------ server: the change

    private void startTransform(ServerLevel level) {
        pendingTransform = false;
        attack = Attack.NONE;
        Telegraphs.cancel(this);
        entityData.set(STATE, TRANSFORM);
        stateTick = 0;
        playSound(SoundEvents.WITHER_SKELETON_HURT, 3.0F, 0.4F);
    }

    private void transforming(ServerLevel level) {
        stateTick++;
        int t = stateTick;
        setDeltaMovement(0, (floorUnder() + HOVER + (t < TRANSFORM_SMASH ? 2.5 : t < 100 ? 1.0 : 0) - getY()) * 0.15, 0);
        if (t == TRANSFORM_SMASH) { // the throne breaks, the room darkens
            playSound(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 3.0F, 0.5F);
            playSound(SoundEvents.BONE_BLOCK_BREAK, 3.0F, 0.4F);
            ScreenFx.shake(level, position(), 32, 16, 0.8F);
            if (throne != null) {
                Vec3 p = throne.position().add(0, 1.5, 0);
                level.sendParticles(ModParticles.BONE_DUST.get(), p.x, p.y, p.z, 120, 1.2, 1.4, 1.2, 0.12);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), p.x, p.y, p.z, 80, 1.0, 1.2, 1.0, 0.2);
                throne.discard();
                throne = null;
            }
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(this) < 48 * 48) {
                    ScreenFx.play(p, ScreenEffect.DARKNESS, TRANSFORM_TICKS - t, 0.7F);
                    ScreenFx.play(p, ScreenEffect.FOG, TRANSFORM_TICKS - t + 20, 0.6F, 0x1E3A22, "");
                }
            }
        }
        if (t > TRANSFORM_SMASH && t < TRANSFORM_WINGS && t % 4 == 0) {
            playSound(SoundEvents.SKELETON_HURT, 2.0F, 0.4F + random.nextFloat() * 0.3F);
            Vec3 c = position().add(0, 2, 0);
            level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 6, 0.6, 0.8, 0.6, 0.02);
        }
        if (t == TRANSFORM_WINGS) { // the wings tear out
            entityData.set(PHASE, (byte) 2);
            playSound(SoundEvents.PHANTOM_FLAP, 4.0F, 0.4F);
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.6F);
            Vec3 c = position().add(0, 2.4, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 60, 1.6, 0.8, 1.6, 0.05);
            ScreenFx.shake(level, position(), 28, 10, 0.5F);
        }
        if (t == TRANSFORM_CROWN) { // the crown catches fire
            playSound(SoundEvents.FIRECHARGE_USE, 3.0F, 0.5F);
            playSound(SoundEvents.SOUL_ESCAPE.value(), 3.0F, 0.6F);
            Vec3 c = position().add(0, 3.6, 0);
            level.sendParticles(ModParticles.NECRO_FLAME.get(), c.x, c.y, c.z, 50, 0.4, 0.3, 0.4, 0.06);
            BossDeath.on(this, Anchor.EXCEPT, 0, ScreenEffect.FLASH, 6, 0.35F, 0x40FF70);
        }
        if (t == 92) scream(level, 0.9F);
        if (t >= TRANSFORM_TICKS) {
            entityData.set(STATE, AWAKE);
            cooldown = 16;
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
        setNoGravity(true);
        hover(0.1);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (enraged() && speed != null && !speed.hasModifier(ENRAGED.id())) {
            speed.addTransientModifier(ENRAGED);
            if (attack == Attack.NONE) start(level, Attack.ROAR, getTarget());
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
        if (target == null) return;
        getLookControl().setLookAt(target, 30, 30);
        float yaw = yawTo(position(), target.position());
        setYRot(Mth.approachDegrees(getYRot(), yaw, 8));
        setYBodyRot(getYRot());
        setYHeadRot(getYRot());
        double dist = Math.sqrt(distanceToSqr(target.getX(), getY(), target.getZ()));
        if (cooldown > 0) cooldown--;
        if (cooldown <= 0 && dist < 28) {
            start(level, choose(dist), target);
            return;
        }
        // floating to keep at a casting distance from his target
        double sp = getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.6;
        Vec3 to = target.position().subtract(position()).multiply(1, 0, 1);
        Vec3 v = getDeltaMovement();
        Vec3 move = dist > 11 ? to.normalize().scale(sp) : dist < 6 ? to.normalize().scale(-sp * 0.7) : Vec3.ZERO;
        setDeltaMovement(move.x, v.y, move.z);
    }

    private Attack choose(double dist) {
        List<Attack> options = new ArrayList<>();
        options.add(Attack.SOULFIRE);
        if (summoned.stream().filter(e -> e instanceof LichBones).count() == 0 && raisedCount() < MAX_RAISED) options.add(Attack.RAISE);
        if (dist < 9) options.add(Attack.FROST);
        if (phase() == 2) {
            if (tickCount - lastLands > 400) options.add(Attack.LANDS);
            if (tickCount - lastShades > 400) options.add(Attack.SHADES);
            options.add(Attack.CURSE);
            options.add(Attack.DANCE);
            options.add(Attack.DANCE);
        }
        if (options.size() > 1) options.removeIf(a -> a == last);
        return options.get(random.nextInt(options.size()));
    }

    private int raisedCount() {
        return level().getEntitiesOfClass(Skeleton.class, getBoundingBox().inflate(48), s -> s.getTags().contains(RAISED_TAG)).size();
    }

    /** /bossattack: starts an attack by name now, if he can. */
    public boolean forceAttack(String name) {
        if (!(level() instanceof ServerLevel level) || state() != AWAKE || attack != Attack.NONE) return false;
        if (name.equals("wings")) {
            if (phase() != 1) return false;
            startTransform(level);
            return true;
        }
        Attack a = switch (name) {
            case "soulfire" -> Attack.SOULFIRE;
            case "raise" -> Attack.RAISE;
            case "frost" -> Attack.FROST;
            case "lands" -> Attack.LANDS;
            case "shades" -> Attack.SHADES;
            case "curse" -> Attack.CURSE;
            case "dance" -> Attack.DANCE;
            default -> Attack.NONE;
        };
        LivingEntity target = getTarget() != null ? getTarget() : nearestPlayer(AGGRO_RANGE);
        if (a == Attack.NONE || target == null) return false;
        start(level, a, target);
        return true;
    }

    private void lockYaw() {
        setYRot(lockedYaw);
        yRotO = lockedYaw;
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private Vec3 floorAt(Vec3 p) {
        return new Vec3(p.x, floorUnder(), p.z);
    }

    private void start(ServerLevel level, Attack a, @Nullable LivingEntity target) {
        attack = a;
        last = a;
        attackTick = 0;
        lockedYaw = target != null ? yawTo(position(), target.position()) : getYRot();
        lockYaw();
        level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + a.ordinal()));
        Vec3 at = floorAt(position());
        List<Player> players = fighters();
        switch (a) {
            case SOULFIRE -> {
                spots.clear();
                int n = enraged() ? 5 : 3;
                for (int i = 0; i < n; i++) {
                    Vec3 p = players.isEmpty() ? at.add((random.nextDouble() - 0.5) * 12, 0, (random.nextDouble() - 0.5) * 12)
                            : players.get(i % players.size()).position();
                    if (i >= players.size()) p = p.add((random.nextDouble() - 0.5) * 7, 0, (random.nextDouble() - 0.5) * 7);
                    spots.add(p);
                    Telegraphs.show(level, Telegraph.circle(p, FIRE_RADIUS), a.impact, 5, this, e -> {
                        hit(e, FIRE_DAMAGE, 0.5, 0.4);
                        e.igniteForSeconds(3);
                    });
                }
                playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 3.0F, 0.6F);
            }
            case RAISE -> {
                spots.clear();
                Vec3 c = home().distanceTo(at) < 20 ? home() : at;
                for (int i = 0; i < BONES; i++) {
                    double ang = random.nextDouble() * Mth.TWO_PI, r = 4 + random.nextDouble() * 8;
                    spots.add(new Vec3(c.x + Math.cos(ang) * r, c.y, c.z + Math.sin(ang) * r));
                }
                playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 3.0F, 0.5F);
            }
            case FROST -> {
                Telegraphs.show(level, Telegraph.circle(at, FROST_RADIUS), a.impact, 6, this, e -> {
                    hit(e, FROST_DAMAGE, 1.0, 0.4);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), this);
                    e.setTicksFrozen(Math.max(e.getTicksFrozen(), 200));
                });
                playSound(SoundEvents.POWDER_SNOW_STEP, 3.0F, 0.5F);
            }
            case LANDS -> {
                lastLands = tickCount;
                playSound(SoundEvents.WITHER_AMBIENT, 3.0F, 0.5F);
            }
            case SHADES -> {
                lastShades = tickCount;
                playSound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, 3.0F, 0.6F);
            }
            case CURSE -> playSound(SoundEvents.EVOKER_PREPARE_WOLOLO, 3.0F, 0.5F);
            case DANCE -> {
                int arms = enraged() ? 2 : 1;
                float start = random.nextFloat() * 360;
                spots.clear();
                for (int arm = 0; arm < arms; arm++) {
                    for (int i = 0; i < SPIKES; i++) {
                        double ang = Math.toRadians(start + arm * 180 + i * 22);
                        double r = 2.0 + i * 0.45;
                        Vec3 p = at.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
                        spots.add(p);
                        Telegraphs.show(level, Telegraph.circle(p, SPIKE_RADIUS), a.impact + i * 2, 3, this, e -> hit(e, SPIKE_DAMAGE, 0.3, 0.8));
                    }
                }
                playSound(SoundEvents.SKELETON_AMBIENT, 3.0F, 0.4F);
            }
            case ROAR -> scream(level, 0.8F);
            default -> {}
        }
    }

    private void attackTick(ServerLevel level, @Nullable LivingEntity target) {
        attackTick++;
        int t = attackTick;
        lockYaw();
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0, v.y, 0);
        switch (attack) {
            case SOULFIRE -> {
                if (t >= FIRE_CAST && t <= attack.impact) { // the balls of soul fire arcing onto their circles
                    Vec3 from = position().add(0, 3.6, 0).add(forward(lockedYaw).scale(0.8));
                    float k = (t - FIRE_CAST) / (float) (attack.impact - FIRE_CAST);
                    for (Vec3 s : spots) {
                        Vec3 p = from.lerp(s, k).add(0, Math.sin(k * Math.PI) * 3.0, 0);
                        level.sendParticles(ModParticles.NECRO_FLAME.get(), p.x, p.y, p.z, 6, 0.15, 0.15, 0.15, 0.01);
                        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.01);
                    }
                }
                if (t == FIRE_CAST) playSound(SoundEvents.BLAZE_SHOOT, 3.0F, 0.5F);
                if (t == attack.impact) {
                    for (Vec3 s : spots) {
                        level.sendParticles(ModParticles.NECRO_FLAME.get(), s.x, s.y + 0.2, s.z, 30, 1.0, 0.3, 1.0, 0.05);
                        level.sendParticles(ParticleTypes.SOUL, s.x, s.y + 0.2, s.z, 8, 0.8, 0.2, 0.8, 0.02);
                        level.playSound(null, s.x, s.y, s.z, SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.6F);
                    }
                    ScreenFx.shake(level, position(), 24, 5, 0.3F);
                }
            }
            case RAISE -> {
                if (t == attack.impact) {
                    for (Vec3 s : spots) {
                        LichBones b = ModEntities.LICH_BONES.get().create(level);
                        if (b == null) continue;
                        b.moveTo(s.x, s.y, s.z, random.nextFloat() * 360, 0);
                        b.owner(this);
                        level.addFreshEntity(b);
                        summoned.add(b);
                        Telegraphs.show(level, Telegraph.circle(s, 1.6F), LichBones.RISE, 4, this, e -> {
                            if (!(e instanceof Skeleton)) hit(e, 6, 0.6, 0.5);
                        });
                        level.sendParticles(ModParticles.BONE_DUST.get(), s.x, s.y + 0.3, s.z, 16, 0.5, 0.2, 0.5, 0.05);
                        level.sendParticles(ModParticles.NECRO_FLAME.get(), s.x, s.y + 0.2, s.z, 6, 0.4, 0.1, 0.4, 0.02);
                    }
                    playSound(SoundEvents.BONE_BLOCK_PLACE, 3.0F, 0.5F);
                }
            }
            case FROST -> {
                if (t < attack.impact && t % 3 == 0) {
                    Vec3 c = position().add(0, 2.5, 0);
                    level.sendParticles(ModParticles.FROST.get(), c.x, c.y, c.z, 6, 1.2, 1.0, 1.2, 0.02);
                }
                if (t == attack.impact) { // the ring of ice bursting out
                    Vec3 c = floorAt(position());
                    for (int i = 0; i < 60; i++) {
                        double ang = i / 60.0 * Mth.TWO_PI;
                        for (double r = 1.5; r <= FROST_RADIUS; r += 1.8) {
                            level.sendParticles(ModParticles.FROST.get(), c.x + Math.cos(ang) * r, c.y + 0.3, c.z + Math.sin(ang) * r, 1,
                                    0.1, 0.2, 0.1, 0.02);
                        }
                    }
                    level.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 0.5, c.z, 80, FROST_RADIUS * 0.5, 0.4, FROST_RADIUS * 0.5, 0.05);
                    playSound(SoundEvents.GLASS_BREAK, 3.0F, 0.5F);
                    playSound(SoundEvents.POWDER_SNOW_BREAK, 3.0F, 0.6F);
                    ScreenFx.shake(level, position(), 20, 8, 0.4F);
                }
            }
            case LANDS -> {
                if (t == attack.impact) lands(level);
            }
            case SHADES -> {
                if (t == attack.impact) {
                    for (Player p : fighters()) {
                        LichShade s = ModEntities.LICH_SHADE.get().create(level);
                        if (s == null) continue;
                        Step from = stepOf(p.getUUID(), SHADE_DELAY);
                        Vec3 at = from != null ? from.pos() : p.position().subtract(p.getLookAngle().multiply(1, 0, 1).scale(3));
                        s.moveTo(at.x, at.y, at.z, p.getYRot(), 0);
                        s.follow(this, p);
                        level.addFreshEntity(s);
                        summoned.add(s);
                        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 30, 0.4, 0.8, 0.4, 0.02);
                    }
                    playSound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 3.0F, 0.5F);
                }
            }
            case CURSE -> {
                if (t == attack.impact && target instanceof ServerPlayer p) curse(level, p);
            }
            case DANCE -> {
                if (t == attack.impact) {
                    playSound(SoundEvents.BONE_BLOCK_BREAK, 3.0F, 0.5F);
                    ScreenFx.shake(level, position(), 20, 6, 0.35F);
                }
                for (int i = 0; i < spots.size(); i++) { // each spike as its warning fills
                    int when = attack.impact + (i % SPIKES) * 2;
                    if (t == when) {
                        Vec3 p = spots.get(i);
                        LichSpike spike = ModEntities.LICH_SPIKE.get().create(level);
                        if (spike != null) {
                            spike.moveTo(p.x, p.y, p.z, random.nextFloat() * 360, 0);
                            level.addFreshEntity(spike);
                        }
                        level.sendParticles(ModParticles.BONE_DUST.get(), p.x, p.y + 0.3, p.z, 8, 0.4, 0.3, 0.4, 0.06);
                        if (i % 3 == 0) level.playSound(null, p.x, p.y, p.z, SoundEvents.BONE_BLOCK_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.7F);
                    }
                }
            }
            default -> {}
        }
        if (t >= attack.length) {
            attack = Attack.NONE;
            cooldown = enraged() ? 14 + random.nextInt(12) : (phase() == 2 ? 24 : 32) + random.nextInt(14);
        }
    }

    private static Vec3 forward(float yaw) {
        return new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
    }

    // ------------------------------------------------------------------ the land of the dead

    private void lands(ServerLevel level) {
        Vec3 c = home().distanceTo(position()) < 24 ? home() : floorAt(position());
        lights.clear();
        int n = Math.max(3, fighters().size() + 1);
        for (int i = 0; i < n; i++) {
            double ang = (i + random.nextDouble() * 0.5) / n * Mth.TWO_PI, r = 5 + random.nextDouble() * 5;
            lights.add(new Vec3(c.x + Math.cos(ang) * r, c.y, c.z + Math.sin(ang) * r));
        }
        lightsUntil = tickCount + LANDS_TICKS;
        int ghosts = 6 + fighters().size() * 2;
        for (int i = 0; i < ghosts; i++) {
            double ang = i / (double) ghosts * Mth.TWO_PI;
            Vec3 p = c.add(Math.cos(ang) * 17, 0.3, Math.sin(ang) * 17);
            LichGhost g = ModEntities.LICH_GHOST.get().create(level);
            if (g == null) continue;
            g.moveTo(p.x, p.y, p.z, 0, 0);
            g.owner(this, LANDS_TICKS);
            level.addFreshEntity(g);
            summoned.add(g);
        }
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 48 * 48) {
                ScreenFx.play(p, ScreenEffect.DESATURATE, LANDS_TICKS, 1.0F, 0xE8C060, "");
                ScreenFx.play(p, ScreenEffect.VIGNETTE, LANDS_TICKS, 0.6F, 0x0E1A10, "");
            }
        }
        playSound(SoundEvents.WITHER_SPAWN, 2.0F, 0.6F);
    }

    /** The circles of light: shining columns and motes while the land of the dead lasts. */
    private void lightsTick(ServerLevel level) {
        if (tickCount > lightsUntil || lights.isEmpty()) return;
        for (Vec3 l : lights) {
            double a = tickCount * 0.25;
            for (int i = 0; i < 4; i++) {
                double ang = a + i * Mth.HALF_PI;
                level.sendParticles(ModParticles.HOLY_LIGHT.get(), l.x + Math.cos(ang) * LIGHT_RADIUS, l.y + 0.2, l.z + Math.sin(ang) * LIGHT_RADIUS,
                        1, 0, 0.05, 0, 0.01);
            }
            if (tickCount % 3 == 0) level.sendParticles(ModParticles.HOLY_LIGHT.get(), l.x, l.y + 0.5, l.z, 2, 0.6, 0.8, 0.6, 0.02);
            if (tickCount % 6 == 0) level.sendParticles(ParticleTypes.END_ROD, l.x, l.y + 0.2, l.z, 1, 0.5, 0.1, 0.5, 0.03);
        }
    }

    // ------------------------------------------------------------------ the curse

    private void curse(ServerLevel level, ServerPlayer p) {
        CURSED.put(p.getUUID(), level.getGameTime() + CURSE_TICKS);
        ScreenFx.play(p, ScreenEffect.VIGNETTE, CURSE_TICKS, 0.8F, 0x7A28B8, "");
        ScreenFx.play(p, ScreenEffect.TEXT, 50, 0.8F, 0xC080FF, "Cursed - do not heal!");
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EVOKER_CAST_SPELL, net.minecraft.sounds.SoundSource.HOSTILE, 2.5F, 0.5F);
        Vec3 from = position().add(0, 2.6, 0);
        Vec3 to = p.position().add(0, 1.2, 0);
        for (int i = 0; i <= 16; i++) {
            Vec3 q = from.lerp(to, i / 16.0);
            level.sendParticles(ModParticles.CURSE.get(), q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    /** Under the curse, healing hurts instead (and its runes circle the cursed one's head). */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide) return;
        Long until = CURSED.get(e.getUUID());
        if (until == null) return;
        if (e.level().getGameTime() > until) {
            CURSED.remove(e.getUUID());
            return;
        }
        event.setCanceled(true);
        float amount = event.getAmount();
        e.hurt(e.damageSources().magic(), amount);
        if (e.level() instanceof ServerLevel level) {
            level.sendParticles(ModParticles.CURSE.get(), e.getX(), e.getY() + 1.2, e.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
        }
    }

    @SubscribeEvent
    public static void onTick(com.divinis.platform.event.PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.level().isClientSide || CURSED.isEmpty() || p.tickCount % 4 != 0) return;
        Long until = CURSED.get(p.getUUID());
        if (until == null) return;
        if (p.level().getGameTime() > until) {
            CURSED.remove(p.getUUID());
            return;
        }
        double a = p.tickCount * 0.3;
        ((ServerLevel) p.level()).sendParticles(ModParticles.CURSE.get(), p.getX() + Math.cos(a) * 0.6, p.getY() + 2.2,
                p.getZ() + Math.sin(a) * 0.6, 1, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------------ hits and effects

    float damageScale() {
        var attr = getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null || attr.getBaseValue() <= 0 ? 1.0F : (float) (attr.getValue() / attr.getBaseValue());
    }

    void hit(LivingEntity e, float damage, double push, double up) {
        if (e instanceof Skeleton s && s.getTags().contains(RAISED_TAG) || e instanceof LichShade) return;
        if (!e.hurt(damageSources().mobAttack(this), damage * damageScale())) return;
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0) return;
        e.setDeltaMovement(e.getDeltaMovement().add(dx / len * push * resist, up * resist, dz / len * push * resist));
        e.hurtMarked = true;
    }

    /** His scream: the screen shakes, green at the edges, the view widening, for everyone near. */
    private void scream(ServerLevel level, float strength) {
        playSound(SoundEvents.WITHER_SKELETON_DEATH, 3.0F, 0.4F);
        playSound(SoundEvents.WITHER_AMBIENT, 2.5F, 0.6F);
        ScreenFx.shake(level, position(), 32, 14, strength);
        ScreenFx.playNear(level, position(), 32, ScreenEffect.VIGNETTE, 30, strength, 0x1E6A2C);
        ScreenFx.playNear(level, position(), 20, ScreenEffect.FOV, 16, strength * 0.6F, -1);
        Vec3 h = position().add(0, 3.0, 0);
        level.sendParticles(ModParticles.NECRO_FLAME.get(), h.x, h.y, h.z, 24, 0.6, 0.5, 0.6, 0.05);
    }

    // ------------------------------------------------------------------ dying

    @Override
    protected void tickDeath() {
        dying++;
        setNoGravity(false);
        if (!(level() instanceof ServerLevel level)) return;
        int t = dying;
        Vec3 c = getBoundingBox().getCenter();
        if (t == 1) {
            BossDeath.sound(this, SoundEvents.WITHER_SKELETON_DEATH, 3.0F, 0.3F);
            BossDeath.on(this, Anchor.ENTITY, 0, ScreenEffect.OUTLINE, 70, 1.0F, 0x50FF80);
            Telegraphs.cancel(this);
            lightsUntil = -1;
            for (Entity e : summoned) e.discard();
        }
        if (t == 32) BossDeath.sound(this, SoundEvents.ARMOR_EQUIP_GOLD.value(), 2.5F, 0.4F);          // the crown falls
        if (t > 36 && t < 64 && t % 6 == 0) BossDeath.sound(this, SoundEvents.CHAIN_STEP, 1.5F, 1.4F); // and rolls away
        if (t > 50 && t % 3 == 0) { // crumbling to dust
            level.sendParticles(ModParticles.BONE_DUST.get(), c.x, c.y, c.z, 8, 0.5, 0.6, 0.5, 0.04);
            level.sendParticles(ModParticles.NECRO_FLAME.get(), c.x, c.y, c.z, 1, 0.4, 0.5, 0.4, 0.02);
            if (t % 9 == 0) BossDeath.sound(this, SoundEvents.SAND_BREAK, 1.5F, 0.6F);
        }
        if (t == DEATH_TICKS) {
            BossDeath.sound(this, SoundEvents.SOUL_ESCAPE.value(), 3.0F, 0.4F);
            BossDeath.screen(this, ScreenEffect.FLASH, 6, 0.3F, 0x60FF80);
            level.sendParticles(ModParticles.BONE_DUST.get(), c.x, c.y, c.z, 90, 0.8, 0.6, 0.8, 0.06);
            level.sendParticles(ModParticles.NECRO_FLAME.get(), c.x, c.y, c.z, 40, 0.6, 0.6, 0.6, 0.05);
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
        if (state() == WAKING || state() == TRANSFORM) return false;
        if (state() == SLEEPING) {
            if (!level().isClientSide) wakeUp();
            return false;
        }
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
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(MobEffects.POISON) && !effect.is(MobEffects.MOVEMENT_SLOWDOWN) && !effect.is(MobEffects.WITHER)
                && super.canBeAffected(effect);
    }

    @Override
    public float getVoicePitch() {
        return 0.45F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return state() == AWAKE ? SoundEvents.WITHER_SKELETON_AMBIENT : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_SKELETON_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {}

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Awake", state() != SLEEPING);
        tag.putByte("Phase", (byte) phase());
        Vec3 h = home();
        tag.putDouble("HomeX", h.x);
        tag.putDouble("HomeY", h.y);
        tag.putDouble("HomeZ", h.z);
        tag.putFloat("HomeYaw", homeYaw);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeY")) {
            home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
            homeYaw = tag.getFloat("HomeYaw");
        }
        entityData.set(PHASE, (byte) Math.max(1, tag.getByte("Phase")));
        entityData.set(STATE, tag.getBoolean("Awake") ? AWAKE : SLEEPING);
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    @Override
    public void setCustomName(@Nullable net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }
}
