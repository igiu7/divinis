package com.divinis.boss;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Arachnid (/summon divinis:arachnid where its room's floor is - its home): a great hunting spider, five and a
 * half blocks across the legs. It sleeps wrapped in a cocoon hanging from the ceiling over its home (a thread up to the
 * ceiling, or high into the air when there is none); a player coming near or a blow wakes it: the cocoon shudders, it
 * tears out, is let down on its thread and screams. Every attack is telegraphed (boss.Telegraphs: whoever stands in the
 * red when it fills is hit) - with good movement it can be beaten without taking a hit.
 *
 * Phase 1:
 * - Pounce: it crouches a long moment, then springs at its target and lands on the marked circle (poison);
 * - Web spit: three lines of web fan out in front of it; whoever is caught is wrapped in a thin web (ArachnidCocoon)
 *   for three seconds - a blow frees them, theirs or a friend's;
 * - Stomp: the front legs come down on the half circle before it, a moment later the hind legs on the half circle
 *   behind it - its sides are safe.
 * At half health it climbs up its thread, thrashes, splits and sheds its skin (ArachnidMolt falls to the floor) and comes
 * out bigger, near black with blood-red markings and burning eyes; it lets itself down and screams (it cannot be hurt
 * meanwhile).
 * Phase 2:
 * - Death from above: it springs up onto its thread and hangs over its target, following them; then the place under it
 *   is marked and it drops onto it;
 * - Brood: it flings four egg sacs (ArachnidEgg) - onto the players and round the room (the circles); six seconds after
 *   they land they hatch into cave spiders, unless they are broken first;
 * - Cocoon: a line of silk shot at one player - whoever it catches is wrapped in a thick cocoon that hurts them every
 *   second until it is cut open (several blows) or eight seconds have passed;
 * - Acid rain: it spits acid into the air; drops fall on a grid of circles over the room, then on a second grid in the
 *   gaps of the first - stand between the circles, then move.
 * Below 40% health it is enraged: faster, shorter rests, and it pounces again.
 * Dying, it rolls onto its back and its legs curl up one after another.
 * Its effects: torn silk drifting off it and its thread (ModParticles.SILK), poison mist from its fangs (VENOM),
 * drops of acid falling onto the marked circles and splashing (ACID, ACID_SPLASH); on the screen a web for whoever is
 * wrapped (ScreenEffect.WEB), a green haze in the acid rain, the world grey round it while it changes, red at its
 * screams.
 * Timings match the animations in client/ArachnidModelData (tools/models/build_arachnid.py).
 */
public class Arachnid extends Monster implements BossTargeting.Marked {
    public static final byte SLEEPING = 0, WAKING = 1, AWAKE = 2, TRANSFORM = 3;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Arachnid.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(Arachnid.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(Arachnid.class, EntityDataSerializers.INT);
    /** the y the silk thread runs up to while it hangs (NaN: no thread) */
    private static final EntityDataAccessor<Float> THREAD = SynchedEntityData.defineId(Arachnid.class, EntityDataSerializers.FLOAT);

    public enum Attack {
        NONE(0, 0), POUNCE(40, 28), WEB(32, 18), STOMP(44, 18), DROP(80, 64), BROOD(36, 20), COCOON(32, 18), RAIN(66, 18), ROAR(30, 0);

        public final int length, impact;

        Attack(int length, int impact) {
            this.length = length;
            this.impact = impact;
        }
    }

    private static final byte EVENT_ATTACK = (byte) 170; // + attack ordinal (Ronin and Sphinx 140+, Ronin's own 160-162)

    // waking (as the "wake" animation): shudders to 24, tears out at 24, let down 32-56, screams 62
    public static final int WAKE_TICKS = 80, WAKE_BURST = 24;
    private static final int WAKE_DOWN = 32, WAKE_LAND = 56, WAKE_SCREAM = 62;
    // the change (as the "transform" animation): up 0-10, thrashing, the skin splits at 40, bigger from 44, down 70-86
    public static final int TRANSFORM_TICKS = 100, TRANSFORM_SHED = 40, TRANSFORM_GROW = 44;
    private static final int TRANSFORM_UP = 10, TRANSFORM_DOWN = 70, TRANSFORM_LAND = 86;
    public static final int DEATH_TICKS = 80;
    // death from above (as "climb", "hang", "fall"): up 0-12, follows 12-42, marked at 42, falls from 58, lands at 64
    public static final int CLIMB_TICKS = 12, FOLLOW_END = 42, FALL_START = 58;

    private static final double WAKE_RANGE = 13, AGGRO_RANGE = 44;
    private static final double HANG_MAX = 8, HANG_MIN = 2.5, MODEL_TOP = 4.6; // blocks the hanging spider takes above its feet

    private static final float POUNCE_DAMAGE = 16, POUNCE_RADIUS = 3.2F;
    private static final float WEB_DAMAGE = 6, WEB_LENGTH = 18, WEB_HALF_WIDTH = 0.9F, WEB_SPREAD = 24;
    private static final float STOMP_DAMAGE = 15, STOMP_FRONT = 7.5F, STOMP_BACK = 6.5F;
    private static final int STOMP_SECOND = 30;
    private static final float DROP_DAMAGE = 22, DROP_RADIUS = 3.8F;
    private static final float EGG_DAMAGE = 6, EGG_RADIUS = 1.6F;
    private static final int EGGS = 4, EGG_FLIGHT = 16;
    private static final float COCOON_DAMAGE = 4, COCOON_LENGTH = 24, COCOON_HALF_WIDTH = 1.1F;
    private static final float ACID_DAMAGE = 8, ACID_RADIUS = 1.25F, ACID_SPACING = 4, ACID_AREA = 15;
    private static final int ACID_FIRST = 40, ACID_SECOND = 62;
    /** acid drops fall ACID_DROP_HEIGHT blocks in ACID_FALL ticks (client.ArachnidParticles.Acid: 0.036 a tick more each tick) */
    private static final int ACID_FALL = 12;
    private static final double ACID_DROP_HEIGHT = 8, ACID_DROP_SPEED = 0.45;

    private static final AttributeModifier ENRAGED = new AttributeModifier(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.divinis.Divinis.MODID, "arachnid_enraged"),
            0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    /** cave spiders hatched from its eggs carry this tag (they go when it does) */
    public static final String BROOD_TAG = "divinis_arachnid_brood";

    // client: animations
    public final AnimationState sleepAnimation = new AnimationState();
    public final AnimationState wakeAnimation = new AnimationState();
    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState pounceAnimation = new AnimationState();
    public final AnimationState spitAnimation = new AnimationState();
    public final AnimationState stompAnimation = new AnimationState();
    public final AnimationState climbAnimation = new AnimationState();
    public final AnimationState hangAnimation = new AnimationState();
    public final AnimationState fallAnimation = new AnimationState();
    public final AnimationState layAnimation = new AnimationState();
    public final AnimationState shootAnimation = new AnimationState();
    public final AnimationState rainAnimation = new AnimationState();
    public final AnimationState roarAnimation = new AnimationState();
    public final AnimationState transformAnimation = new AnimationState();
    public final AnimationState deathAnimation = new AnimationState();
    /** Ticks since it began dying (its own count, so vanilla neither tips nor reddens it). */
    public int dying;
    private int clientAttackTicks;
    private int clientWakeStart = -1, clientTransformStart = -1, clientDropStart = -1;

    // server: the fight
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private final BossTargeting targeting = new BossTargeting();
    private Attack attack = Attack.NONE, last = Attack.NONE;
    private int attackTick, cooldown = 20, stateTick, lastRain = -1000;
    private boolean pendingTransform, unclamped;
    private float lockedYaw;
    @Nullable private Vec3 home;
    private double hang = HANG_MAX;
    private Vec3 moveFrom = Vec3.ZERO, moveTo = Vec3.ZERO;
    private final List<Vec3> spots = new ArrayList<>();
    private final List<Vec3> acidA = new ArrayList<>(), acidB = new ArrayList<>();
    private final List<Entity> spawned = new ArrayList<>();

    public Arachnid(EntityType<? extends Arachnid> type, Level level) {
        super(type, level);
        this.xpReward = 250;
        setPersistenceRequired();
        bossBar.setVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 44.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, SLEEPING);
        builder.define(PHASE, (byte) 1);
        builder.define(TARGET_ID, -1);
        builder.define(THREAD, Float.NaN);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public byte state() {
        return entityData.get(STATE);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    /** The y its thread runs up to, NaN when it is not on one. */
    public float threadTop() {
        return entityData.get(THREAD);
    }

    /** Client: an attack (or waking, or changing) is playing - no walking legs, no looking round. */
    public boolean isAttacking() {
        return clientAttackTicks > 0 || state() != AWAKE;
    }

    /** Client: is it still wrapped in its cocoon (asleep, or waking until it tears out)? */
    public boolean cocooned() {
        return state() == SLEEPING || state() == WAKING && (clientWakeStart < 0 || tickCount - clientWakeStart < WAKE_BURST);
    }

    /** Client: how far it has grown in the change (0 before, 1 after). */
    public float grown(float partialTick) {
        if (phase() == 1) return 0;
        if (state() != TRANSFORM || clientTransformStart < 0) return 1;
        return Mth.clamp((tickCount + partialTick - clientTransformStart - TRANSFORM_GROW) / 16F, 0, 1);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions full = super.getDefaultDimensions(pose);
        if (entityData == null) return full;
        return phase() == 2 ? full.scale(1.15F) : full;
    }

    // ------------------------------------------------------------------ client

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PHASE.equals(key)) refreshDimensions();
        if (STATE.equals(key) && level().isClientSide) {
            if (state() == WAKING) {
                wakeAnimation.start(tickCount);
                clientWakeStart = tickCount;
            } else {
                wakeAnimation.stop();
            }
            if (state() == TRANSFORM) {
                transformAnimation.start(tickCount);
                clientTransformStart = tickCount;
                stopAttacks();
            } else {
                transformAnimation.stop();
            }
        }
    }

    private AnimationState[] attackAnimations() {
        return new AnimationState[] {pounceAnimation, spitAnimation, stompAnimation, climbAnimation, hangAnimation, fallAnimation,
                layAnimation, shootAnimation, rainAnimation, roarAnimation};
    }

    private void stopAttacks() {
        for (AnimationState s : attackAnimations()) s.stop();
        clientDropStart = -1;
    }

    @Override
    public void handleEntityEvent(byte id) {
        int index = (id & 0xFF) - (EVENT_ATTACK & 0xFF);
        if (index > 0 && index < Attack.values().length) {
            Attack a = Attack.values()[index];
            stopAttacks();
            switch (a) {
                case POUNCE -> pounceAnimation.start(tickCount);
                case WEB -> spitAnimation.start(tickCount);
                case STOMP -> stompAnimation.start(tickCount);
                case DROP -> {
                    climbAnimation.start(tickCount);
                    clientDropStart = tickCount;
                }
                case BROOD -> layAnimation.start(tickCount);
                case COCOON -> shootAnimation.start(tickCount);
                case RAIN -> rainAnimation.start(tickCount);
                default -> roarAnimation.start(tickCount);
            }
            clientAttackTicks = a.length;
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
        if (clientDropStart >= 0) { // death from above: up onto the thread, hanging, the fall
            int t = tickCount - clientDropStart;
            if (t == CLIMB_TICKS) {
                climbAnimation.stop();
                hangAnimation.start(tickCount);
            }
            if (t == FALL_START) {
                hangAnimation.stop();
                fallAnimation.start(tickCount);
                clientDropStart = -1;
            }
        }
        if (clientAttackTicks > 0) clientAttackTicks--;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        AABB box = getBoundingBox().inflate(3.5, 1.0, 3.5).expandTowards(0, MODEL_TOP, 0); // the legs, the hanging body
        float top = threadTop();
        return Float.isNaN(top) ? box : box.expandTowards(0, top - box.maxY, 0);
    }

    // ------------------------------------------------------------------ server: where it lives

    /** The floor of its room under it: where it was summoned. */
    private Vec3 home() {
        if (home == null) home = position();
        return home;
    }

    /** How high it hangs over the floor at (x, z) - lower under a low ceiling - and the y its thread runs up to. */
    private double[] hangAt(Vec3 floor) {
        ServerLevel level = (ServerLevel) level();
        BlockPos.MutableBlockPos p = BlockPos.containing(floor.x, floor.y + 1, floor.z).mutable();
        double ceiling = Double.NaN;
        for (int i = 1; i < 28; i++) {
            p.setY(Mth.floor(floor.y) + i);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                ceiling = p.getY();
                break;
            }
        }
        double h = Double.isNaN(ceiling) ? HANG_MAX : Mth.clamp(ceiling - floor.y - MODEL_TOP, HANG_MIN, HANG_MAX);
        return new double[] {h, Double.isNaN(ceiling) ? floor.y + h + MODEL_TOP + 16 : ceiling};
    }

    /** Puts it on its thread at the given floor point, hang blocks up. */
    private void hangOver(Vec3 floor, double height, double threadTop) {
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        setPos(floor.x, floor.y + height, floor.z);
        entityData.set(THREAD, (float) threadTop);
    }

    private void land() {
        setNoGravity(false);
        entityData.set(THREAD, Float.NaN);
    }

    /** The floor under a point (the first solid block down, at most 30 below; its home's floor else). */
    private double floorUnder(Vec3 at) {
        ServerLevel level = (ServerLevel) level();
        BlockPos.MutableBlockPos p = BlockPos.containing(at).mutable();
        for (int i = 0; i < 30; i++) {
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return p.getY() + 1;
            p.move(0, -1, 0);
        }
        return home().y;
    }

    // ------------------------------------------------------------------ server: states

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (tickCount % 10 == 0) updateBossBar(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        spawned.removeIf(e -> !e.isAlive());
        ambient(level);

        switch (state()) {
            case SLEEPING -> sleep(level);
            case WAKING -> waking(level);
            case TRANSFORM -> transforming(level);
            default -> fight(level);
        }
    }

    private void sleep(ServerLevel level) {
        getNavigation().stop();
        if (Float.isNaN(threadTop())) { // just summoned: up onto its thread over its home
            double[] h = hangAt(home());
            hang = h[0];
            hangOver(home(), hang, h[1]);
        }
        setDeltaMovement(Vec3.ZERO);
        if (tickCount % 10 == 0 && nearestPlayer(WAKE_RANGE) != null) wakeUp();
    }

    private void wakeUp() {
        if (state() != SLEEPING) return;
        entityData.set(STATE, WAKING);
        stateTick = 0;
        playSound(SoundEvents.SPIDER_AMBIENT, 3.0F, 0.4F);
    }

    private void waking(ServerLevel level) {
        getNavigation().stop();
        stateTick++;
        int t = stateTick;
        Vec3 h = home();
        setDeltaMovement(Vec3.ZERO);
        if (t < WAKE_BURST && t % 4 == 0) { // the cocoon shudders
            playSound(SoundEvents.WOOL_HIT, 2.5F, 0.5F + random.nextFloat() * 0.3F);
            silk(level, position().add(0, 2, 0), 6, 1.2);
        }
        if (t == WAKE_BURST) { // it tears out
            playSound(SoundEvents.WOOL_BREAK, 3.0F, 0.5F);
            playSound(SoundEvents.SPIDER_HURT, 3.0F, 0.4F);
            silk(level, position().add(0, 2, 0), 60, 1.8);
            ScreenFx.shake(level, position(), 24, 6, 0.3F);
        }
        if (t >= WAKE_DOWN && t <= WAKE_LAND) { // let down on its thread
            float k = (t - WAKE_DOWN) / (float) (WAKE_LAND - WAKE_DOWN);
            setPos(h.x, h.y + hang * (1 - k * k), h.z);
        }
        if (t == WAKE_LAND) {
            land();
            setPos(h.x, h.y, h.z);
            playSound(SoundEvents.SPIDER_STEP, 3.0F, 0.4F);
            dust(level, position(), 30, 2.0);
            ScreenFx.shake(level, position(), 24, 8, 0.4F);
        }
        if (t == WAKE_SCREAM) {
            playSound(SoundEvents.SPIDER_DEATH, 4.0F, 0.4F);
            playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 1.4F);
            scream(level, 0x5A1010, 0.55F);
        }
        if (t >= WAKE_TICKS) {
            entityData.set(STATE, AWAKE);
            cooldown = 20;
        }
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
            for (Entity e : spawned) e.discard();
            spawned.clear();
            ArachnidCocoon.freeAll(level, getId());
            for (CaveSpider s : level.getEntitiesOfClass(CaveSpider.class, getBoundingBox().inflate(64), s -> s.getTags().contains(BROOD_TAG))) {
                s.discard();
            }
        }
        super.remove(reason);
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
        BossDeath.on(this, Anchor.EXCEPT, 0, ScreenEffect.DESATURATE, TRANSFORM_TICKS - 10, 1.0F, -1);
        double[] h = hangAt(home());
        hang = h[0];
        moveTo = home();
        entityData.set(THREAD, (float) h[1]);
        setNoGravity(true);
        playSound(SoundEvents.SPIDER_HURT, 4.0F, 0.3F);
    }

    private void transforming(ServerLevel level) {
        stateTick++;
        int t = stateTick;
        setDeltaMovement(Vec3.ZERO);
        Vec3 h = moveTo;
        if (t <= TRANSFORM_UP) { // up onto its thread over its home
            float k = t / (float) TRANSFORM_UP;
            Vec3 at = moveFrom.lerp(h, k);
            setPos(at.x, h.y + hang * Math.sin(k * Math.PI / 2), at.z);
        }
        if (t > TRANSFORM_UP && t < TRANSFORM_SHED && t % 4 == 0) { // thrashing
            playSound(SoundEvents.SPIDER_HURT, 2.5F, 0.4F + random.nextFloat() * 0.3F);
            ScreenFx.shake(level, h, 28, 5, 0.25F + t * 0.006F);
            level.sendParticles(ModParticles.VENOM.get(), getX(), getY() + 2, getZ(), 6, 0.8, 1.2, 0.8, 0.02);
            silk(level, position().add(0, 3, 0), 4, 0.8);
        }
        if (t == TRANSFORM_SHED) { // the old skin splits and falls
            playSound(SoundEvents.TURTLE_EGG_CRACK, 4.0F, 0.4F);
            playSound(SoundEvents.SLIME_BLOCK_BREAK, 3.0F, 0.5F);
            ArachnidMolt molt = ModEntities.ARACHNID_MOLT.get().create(level);
            if (molt != null) {
                molt.moveTo(getX(), getY() + 0.5, getZ(), getYRot(), 0);
                level.addFreshEntity(molt);
                spawned.add(molt);
            }
            BossDeath.on(this, Anchor.EXCEPT, 0, ScreenEffect.FLASH, 6, 0.3F, 0xC02020);
            BossDeath.on(this, Anchor.AREA, 9, ScreenEffect.ABERRATION, 30, 0.8F, -1);
            level.sendParticles(ModParticles.VENOM.get(), getX(), getY() + 2, getZ(), 40, 1.2, 1.4, 1.2, 0.04);
            silk(level, position().add(0, 2.5, 0), 50, 1.4);
        }
        if (t == TRANSFORM_GROW) {
            entityData.set(PHASE, (byte) 2);
            bossBar.setColor(BossEvent.BossBarColor.RED);
            playSound(SoundEvents.WARDEN_HEARTBEAT, 4.0F, 0.6F);
        }
        if (t >= TRANSFORM_DOWN && t <= TRANSFORM_LAND) {
            float k = (t - TRANSFORM_DOWN) / (float) (TRANSFORM_LAND - TRANSFORM_DOWN);
            setPos(h.x, h.y + hang * (1 - k * k), h.z);
        }
        if (t == TRANSFORM_LAND) {
            land();
            setPos(h.x, h.y, h.z);
            dust(level, position(), 40, 2.4);
            ScreenFx.shake(level, position(), 28, 10, 0.6F);
            playSound(SoundEvents.SPIDER_DEATH, 4.0F, 0.35F);
            playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 1.2F);
            scream(level, 0x8A0A0A, 0.8F);
            BossDeath.ring(level, ModParticles.VENOM.get(), position().add(0, 0.3, 0), 2.5, 40, 0.15);
        }
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

    private List<Player> fighters() {
        List<Player> out = new ArrayList<>();
        for (Player p : level().players()) if (validTarget(p)) out.add(p);
        return out;
    }

    private void fight(ServerLevel level) {
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
        if (target == null) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(target, 30, 30);
        double dist = Math.sqrt(distanceToSqr(target.getX(), getY(), target.getZ()));
        if (cooldown > 0) cooldown--;
        if (cooldown <= 0 && dist < 26) {
            start(level, choose(dist), target);
            return;
        }
        if (dist > 6) {
            if (tickCount % 8 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
        }
    }

    private Attack choose(double dist) {
        List<Attack> options = new ArrayList<>();
        if (phase() == 1) {
            options.add(Attack.POUNCE);
            options.add(Attack.WEB);
            if (dist <= 8) {
                options.add(Attack.STOMP);
                options.add(Attack.STOMP);
            }
        } else {
            options.add(Attack.DROP);
            options.add(Attack.COCOON);
            if (spawned.stream().filter(e -> e instanceof ArachnidEgg).count() < 2) options.add(Attack.BROOD);
            if (tickCount - lastRain > 240) options.add(Attack.RAIN);
            if (enraged()) {
                options.add(Attack.POUNCE);
                if (dist <= 8) options.add(Attack.STOMP);
            }
        }
        if (options.size() > 1) options.removeIf(a -> a == last);
        return options.get(random.nextInt(options.size()));
    }

    /** /bossattack: starts an attack by name now, if it can. */
    public boolean forceAttack(String name) {
        if (!(level() instanceof ServerLevel level) || state() != AWAKE || attack != Attack.NONE) return false;
        Attack a = switch (name) {
            case "pounce" -> Attack.POUNCE;
            case "web" -> Attack.WEB;
            case "stomp" -> Attack.STOMP;
            case "drop" -> Attack.DROP;
            case "brood" -> Attack.BROOD;
            case "cocoon" -> Attack.COCOON;
            case "rain" -> Attack.RAIN;
            case "molt" -> null;
            default -> Attack.NONE;
        };
        if (a == null) {
            if (phase() != 1) return false;
            startTransform(level);
            return true;
        }
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

    private Vec3 forward(float yaw) {
        return new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
    }

    private void start(ServerLevel level, Attack a, @Nullable LivingEntity target) {
        attack = a;
        last = a;
        attackTick = 0;
        getNavigation().stop();
        if (target != null) lockedYaw = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        else lockedYaw = getYRot();
        lockYaw();
        level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + a.ordinal()));
        Vec3 at = position();
        Vec3 fwd = forward(lockedYaw);
        switch (a) {
            case POUNCE -> {
                Vec3 to = target != null ? target.position() : at.add(fwd.scale(6));
                moveFrom = at;
                moveTo = to;
                Telegraphs.show(level, Telegraph.circle(to, POUNCE_RADIUS), a.impact, 5, this, e -> {
                    hit(e, POUNCE_DAMAGE, 1.0, 0.5);
                    e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0), this);
                });
                playSound(SoundEvents.SPIDER_AMBIENT, 3.0F, 0.5F);
            }
            case WEB -> {
                for (float off : new float[] {-WEB_SPREAD, 0, WEB_SPREAD}) {
                    float yaw = lockedYaw + off;
                    Telegraphs.show(level, Telegraph.rect(at.add(forward(yaw).scale(1.5)), yaw, WEB_LENGTH, WEB_HALF_WIDTH), a.impact, 4, this,
                            e -> {
                                hit(e, WEB_DAMAGE, 0.2, 0.1);
                                if (e instanceof ServerPlayer p) ArachnidCocoon.trap(level, p, false, this);
                            });
                }
                playSound(SoundEvents.SPIDER_AMBIENT, 3.0F, 0.7F);
            }
            case STOMP -> {
                Telegraphs.show(level, Telegraph.sector(at, lockedYaw, STOMP_FRONT, 60), a.impact, 5, this,
                        e -> hit(e, STOMP_DAMAGE, 1.4, 0.4));
                Telegraphs.show(level, Telegraph.sector(at, lockedYaw + 180, STOMP_BACK, 65), STOMP_SECOND, 5, this,
                        e -> hit(e, STOMP_DAMAGE, 1.4, 0.4));
                playSound(SoundEvents.SPIDER_AMBIENT, 3.0F, 0.4F);
            }
            case DROP -> {
                moveFrom = at;
                double[] h = hangAt(at);
                hang = h[0];
                entityData.set(THREAD, (float) h[1]);
                setNoGravity(true);
                playSound(SoundEvents.SPIDER_AMBIENT, 3.0F, 0.6F);
            }
            case BROOD -> {
                spots.clear();
                for (Player p : fighters()) {
                    if (spots.size() >= EGGS) break;
                    spots.add(p.position());
                }
                Vec3 c = home();
                for (int i = 0; spots.size() < EGGS && i < 20; i++) {
                    double ang = random.nextDouble() * Mth.TWO_PI, r = 5 + random.nextDouble() * 6;
                    Vec3 p = new Vec3(c.x + Math.cos(ang) * r, c.y, c.z + Math.sin(ang) * r);
                    p = new Vec3(p.x, floorUnder(p.add(0, 3, 0)), p.z);
                    if (p.distanceTo(at) > 3.5) spots.add(p);
                }
                for (Vec3 s : spots) {
                    Telegraphs.show(level, Telegraph.circle(s, EGG_RADIUS), a.impact + EGG_FLIGHT, 4, this, e -> hit(e, EGG_DAMAGE, 0.6, 0.3));
                }
                playSound(SoundEvents.SLIME_SQUISH, 3.0F, 0.5F);
            }
            case COCOON -> {
                Telegraphs.show(level, Telegraph.rect(at.add(fwd.scale(1.5)), lockedYaw, COCOON_LENGTH, COCOON_HALF_WIDTH), a.impact, 4, this,
                        e -> {
                            hit(e, COCOON_DAMAGE, 0.1, 0.0);
                            if (e instanceof ServerPlayer p) ArachnidCocoon.trap(level, p, true, this);
                        });
                playSound(SoundEvents.SPIDER_AMBIENT, 3.0F, 0.45F);
            }
            case RAIN -> {
                lastRain = tickCount;
                acidA.clear();
                acidB.clear();
                Vec3 c = home().distanceTo(at) < 24 ? home() : at;
                for (int wave = 0; wave < 2; wave++) {
                    double off = wave * ACID_SPACING / 2;
                    int n = (int) (ACID_AREA / ACID_SPACING) + 1;
                    for (int i = -n; i <= n; i++) {
                        for (int j = -n; j <= n; j++) {
                            double x = i * ACID_SPACING + off, z = j * ACID_SPACING + off;
                            if (x * x + z * z > ACID_AREA * ACID_AREA) continue;
                            Vec3 p = new Vec3(c.x + x, c.y, c.z + z);
                            (wave == 0 ? acidA : acidB).add(p);
                            Telegraphs.show(level, Telegraph.circle(p, ACID_RADIUS), wave == 0 ? ACID_FIRST : ACID_SECOND, 4, this, e -> {
                                hit(e, ACID_DAMAGE, 0.0, 0.0);
                                e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), this);
                            });
                        }
                    }
                }
                playSound(SoundEvents.SPIDER_AMBIENT, 3.0F, 0.35F);
            }
            case ROAR -> {
                playSound(SoundEvents.SPIDER_DEATH, 4.0F, 0.5F);
                playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 1.3F);
                scream(level, 0xB00000, 0.7F);
                level.sendParticles(ModParticles.VENOM.get(), getX(), getY() + 1.5, getZ(), 30, 1.0, 0.6, 1.0, 0.05);
            }
            default -> {}
        }
    }

    private void attackTick(ServerLevel level, @Nullable LivingEntity target) {
        attackTick++;
        int t = attackTick;
        if (attack != Attack.DROP) lockYaw();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        switch (attack) {
            case POUNCE -> {
                if (t >= 20 && t <= attack.impact) { // the spring, in an arc
                    float k = (t - 20) / (float) (attack.impact - 20);
                    Vec3 p = moveFrom.lerp(moveTo, k);
                    setNoGravity(true);
                    setPos(p.x, p.y + Math.sin(k * Math.PI) * 2.5, p.z);
                }
                if (t < 20 && t % 3 == 0) venom(level, 2);
                if (t == 20) playSound(SoundEvents.SPIDER_AMBIENT, 3.0F, 0.9F);
                if (t > 20 && t < attack.impact) silk(level, position().add(0, 1, 0), 2, 0.5);
                if (t == attack.impact) {
                    setNoGravity(false);
                    playSound(SoundEvents.SPIDER_STEP, 3.0F, 0.5F);
                    dust(level, position(), 25, 1.8);
                    ScreenFx.shake(level, position(), 20, 6, 0.4F);
                    BossDeath.ring(level, ModParticles.VENOM.get(), position().add(0, 0.3, 0), POUNCE_RADIUS, 24, 0.05);
                    level.sendParticles(ModParticles.ACID_SPLASH.get(), getX(), getY() + 0.1, getZ(), 8, 1.4, 0.0, 1.4, 0);
                }
            }
            case WEB -> {
                if (t < attack.impact && t % 4 == 0) venom(level, 1);
                if (t >= attack.impact && t < attack.impact + 4) {
                    for (float off : new float[] {-WEB_SPREAD, 0, WEB_SPREAD}) {
                        Vec3 dir = forward(lockedYaw + off);
                        double d = 1.5 + (t - attack.impact + 1) * WEB_LENGTH / 4;
                        Vec3 p = position().add(dir.scale(d)).add(0, 0.6, 0);
                        level.sendParticles(ModParticles.SILK.get(), p.x, p.y, p.z, 8, 0.5, 0.25, 0.5, 0.02);
                        spray(level, mouth(), dir, ModParticles.SILK.get(), 3, 0.9);
                    }
                }
                if (t == attack.impact) playSound(SoundEvents.LLAMA_SPIT, 3.0F, 0.5F);
            }
            case STOMP -> {
                if (t == attack.impact || t == STOMP_SECOND) {
                    playSound(SoundEvents.RAVAGER_STEP, 3.0F, 0.6F);
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.4F);
                    ScreenFx.shake(level, position(), 22, 8, 0.45F);
                    float yaw = t == attack.impact ? lockedYaw : lockedYaw + 180;
                    for (int a = -60; a <= 60; a += 20) dustLine(level, yaw + a, 2, t == attack.impact ? STOMP_FRONT : STOMP_BACK);
                    Vec3 foot = position().add(forward(yaw).scale(2.5));
                    level.sendParticles(ParticleTypes.CLOUD, foot.x, foot.y + 0.2, foot.z, 10, 1.2, 0.05, 1.2, 0.04);
                }
            }
            case DROP -> dropTick(level, target, t);
            case BROOD -> {
                if (t == attack.impact) {
                    Vec3 from = position().add(0, 2.6, 0).add(forward(lockedYaw).scale(-2.0));
                    for (Vec3 s : spots) {
                        ArachnidEgg egg = ModEntities.ARACHNID_EGG.get().create(level);
                        if (egg == null) continue;
                        egg.launch(from, s, EGG_FLIGHT, this);
                        level.addFreshEntity(egg);
                        spawned.add(egg);
                    }
                    playSound(SoundEvents.SLIME_JUMP, 3.0F, 0.5F);
                    silk(level, from, 20, 0.6);
                    level.sendParticles(ModParticles.VENOM.get(), from.x, from.y, from.z, 10, 0.5, 0.4, 0.5, 0.03);
                }
                if (t < attack.impact && t % 4 == 0) {
                    Vec3 back = position().add(0, 2.2, 0).add(forward(lockedYaw).scale(-2.0));
                    level.sendParticles(ModParticles.VENOM.get(), back.x, back.y, back.z, 2, 0.3, 0.3, 0.3, 0.01);
                }
            }
            case COCOON -> {
                if (t >= attack.impact && t < attack.impact + 4) {
                    Vec3 dir = forward(lockedYaw);
                    for (int i = 0; i < 4; i++) {
                        double d = 1.5 + ((t - attack.impact) * 4 + i) * COCOON_LENGTH / 16;
                        Vec3 p = position().add(dir.scale(d)).add(0, 0.8, 0);
                        level.sendParticles(ModParticles.SILK.get(), p.x, p.y, p.z, 5, 0.25, 0.25, 0.25, 0.01);
                    }
                    spray(level, position().add(0, 1.2, 0), dir, ModParticles.SILK.get(), 6, 1.2);
                }
                if (t == attack.impact) playSound(SoundEvents.CROSSBOW_SHOOT, 3.0F, 0.5F);
            }
            case RAIN -> {
                if (t < attack.impact && t % 3 == 0) venom(level, 2);
                if (t == attack.impact) {
                    playSound(SoundEvents.LLAMA_SPIT, 3.0F, 0.3F);
                    Vec3 m = mouth();
                    for (int i = 0; i < 40; i++) {
                        level.sendParticles(ModParticles.ACID.get(), m.x, m.y, m.z, 0, (random.nextDouble() - 0.5) * 0.5,
                                0.9 + random.nextDouble() * 0.6, (random.nextDouble() - 0.5) * 0.5, 1.0);
                    }
                    Vec3 c = home().distanceTo(position()) < 24 ? home() : position();
                    for (ServerPlayer p : level.players()) {
                        if (p.distanceToSqr(c) < (ACID_AREA + 8) * (ACID_AREA + 8)) {
                            ScreenFx.play(p, ScreenEffect.FOG, ACID_SECOND - attack.impact + 20, 0.55F, 0x557A1E, "");
                        }
                    }
                }
                // the drops, falling so that they land on the circles as the warnings fill
                if (t == ACID_FIRST - ACID_FALL) rainDrops(level, acidA);
                if (t == ACID_SECOND - ACID_FALL) rainDrops(level, acidB);
                if (t == ACID_FIRST || t == ACID_SECOND) {
                    Vec3 c = home().distanceTo(position()) < 24 ? home() : position();
                    level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.HOSTILE, 2.5F, 0.8F);
                    level.playSound(null, c.x, c.y, c.z, SoundEvents.SLIME_SQUISH, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.6F);
                    for (Vec3 p : t == ACID_FIRST ? acidA : acidB) {
                        level.sendParticles(ModParticles.VENOM.get(), p.x, p.y + 0.2, p.z, 1, 0.4, 0.05, 0.4, 0.01);
                    }
                }
            }
            default -> {}
        }
        if (t >= attack.length) {
            if (attack == Attack.DROP) land();
            attack = Attack.NONE;
            cooldown = enraged() ? 14 + random.nextInt(12) : (phase() == 2 ? 22 : 30) + random.nextInt(15);
        }
    }

    /** Death from above: up onto its thread, hanging over its target and following them, marked, the drop. */
    private void dropTick(ServerLevel level, @Nullable LivingEntity target, int t) {
        setDeltaMovement(Vec3.ZERO);
        double floor = moveFrom.y;
        if (t <= CLIMB_TICKS) {
            float k = t / (float) CLIMB_TICKS;
            setPos(getX(), floor + hang * Math.sin(k * Math.PI / 2), getZ());
            if (t == 2) playSound(SoundEvents.SPIDER_STEP, 3.0F, 0.6F);
            return;
        }
        if (t <= FOLLOW_END) { // following its target over their head (no faster than they can run)
            if (target != null && target.isAlive()) {
                Vec3 to = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
                double len = to.length();
                if (len > 0.05) {
                    Vec3 step = to.scale(Math.min(0.32, len) / len);
                    setPos(getX() + step.x, floor + hang, getZ() + step.z);
                }
                lockedYaw = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
            }
            lockYaw();
            entityData.set(THREAD, (float) hangAt(new Vec3(getX(), floor, getZ()))[1]);
            if (t % 3 == 0) venomDrip(level);
            if (t == FOLLOW_END) { // marked: it will land here
                moveTo = new Vec3(getX(), floorUnder(position()), getZ());
                Telegraphs.show(level, Telegraph.circle(moveTo, DROP_RADIUS), attack.impact - FOLLOW_END, 5, this,
                        e -> hit(e, DROP_DAMAGE, 1.6, 0.6));
                playSound(SoundEvents.SPIDER_AMBIENT, 3.0F, 0.3F);
            }
            return;
        }
        lockYaw();
        if (t >= FALL_START && t <= attack.impact) {
            float k = (t - FALL_START) / (float) (attack.impact - FALL_START);
            setPos(moveTo.x, moveTo.y + hang * (1 - k * k), moveTo.z);
            if (t == FALL_START) entityData.set(THREAD, Float.NaN);
        }
        if (t == attack.impact) {
            setNoGravity(false);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.7F);
            playSound(SoundEvents.SPIDER_HURT, 3.0F, 0.4F);
            dust(level, position(), 50, 2.6);
            ScreenFx.shake(level, position(), 28, 12, 0.7F);
            BossDeath.ring(level, ParticleTypes.CLOUD, position().add(0, 0.2, 0), DROP_RADIUS, 36, 0.12);
            BossDeath.ring(level, ModParticles.VENOM.get(), position().add(0, 0.4, 0), DROP_RADIUS * 0.6, 20, 0.05);
            silk(level, position().add(0, 1.5, 0), 30, 1.2);
            ScreenFx.playNear(level, position(), DROP_RADIUS + 4, ScreenEffect.ABERRATION, 12, 0.6F, -1);
        }
    }

    // ------------------------------------------------------------------ hits and effects

    /** Damage multiplier from the ATTACK_DAMAGE attribute (e.g. dungeon.DungeonScaling for bigger parties). */
    float damageScale() {
        var attr = getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null || attr.getBaseValue() <= 0 ? 1.0F : (float) (attr.getValue() / attr.getBaseValue());
    }

    private void hit(LivingEntity e, float damage, double push, double up) {
        if (!e.hurt(damageSources().mobAttack(this), damage * damageScale())) return;
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0 || push <= 0 && up <= 0) return;
        e.setDeltaMovement(e.getDeltaMovement().add(dx / len * push * resist, up * resist, dz / len * push * resist));
        e.hurtMarked = true;
    }

    static void silk(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ModParticles.SILK.get(), at.x, at.y, at.z, count, spread, spread, spread, 0.03);
    }

    /** blocks to the front of its head, by its size */
    private double size() {
        return phase() == 2 ? 1.15 : 1.0;
    }

    /** Where its fangs are (as it stands). */
    private Vec3 mouth() {
        return position().add(0, 0.9 * size(), 0).add(forward(getYRot()).scale(2.1 * size()));
    }

    /** Poison mist curling from its fangs. */
    private void venom(ServerLevel level, int count) {
        Vec3 m = mouth();
        level.sendParticles(ModParticles.VENOM.get(), m.x, m.y, m.z, count, 0.25, 0.15, 0.25, 0.01);
    }

    /** A drop of poison falling from its fangs (it hangs head down: they are under it). */
    private void venomDrip(ServerLevel level) {
        level.sendParticles(ModParticles.ACID.get(), getX() + (random.nextDouble() - 0.5) * 0.6, getY() - 0.2,
                getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0, -0.1, 0, 1.0);
        silk(level, position().add(0, 3.6 * size(), 0), 1, 0.2);
    }

    /** Particles shot out along a direction (each with that speed, a little spread). */
    private void spray(ServerLevel level, Vec3 from, Vec3 dir, net.minecraft.core.particles.ParticleOptions p, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double sx = dir.x * speed + (random.nextDouble() - 0.5) * 0.15, sz = dir.z * speed + (random.nextDouble() - 0.5) * 0.15;
            level.sendParticles(p, from.x, from.y, from.z, 0, sx, 0.05 + random.nextDouble() * 0.08, sz, 1.0);
        }
    }

    /** Drops of acid high over each circle, falling so that they land as its warning fills (ACID_FALL ticks). */
    private void rainDrops(ServerLevel level, List<Vec3> circles) {
        for (Vec3 c : circles) {
            for (int i = 0; i < 3; i++) {
                double x = c.x + (random.nextDouble() - 0.5) * ACID_RADIUS * 1.4, z = c.z + (random.nextDouble() - 0.5) * ACID_RADIUS * 1.4;
                level.sendParticles(ModParticles.ACID.get(), x, c.y + ACID_DROP_HEIGHT, z, 0, 0, -ACID_DROP_SPEED, 0, 1.0);
            }
        }
    }

    /** A scream: the screen shakes and reddens at the edges for everyone near. */
    private void scream(ServerLevel level, int color, float strength) {
        ScreenFx.shake(level, position(), 32, 14, strength);
        ScreenFx.playNear(level, position(), 32, ScreenEffect.VIGNETTE, 30, strength, color);
        ScreenFx.playNear(level, position(), 20, ScreenEffect.FOV, 16, strength * 0.6F, -1);
    }

    /** Every tick: silk drifting off its thread, venom dripping from its fangs in its second phase. */
    private void ambient(ServerLevel level) {
        if (!Float.isNaN(threadTop()) && tickCount % 5 == 0) {
            double y = getY() + 4.4 * size() + random.nextDouble() * Math.max(0, threadTop() - getY() - 4.4);
            level.sendParticles(ModParticles.SILK.get(), getX(), y, getZ(), 1, 0.1, 0.1, 0.1, 0.0);
        }
        if (phase() == 2 && state() == AWAKE && attack != Attack.DROP && tickCount % 6 == 0) {
            venom(level, 1);
            if (random.nextInt(3) == 0) {
                Vec3 m = mouth();
                level.sendParticles(ModParticles.ACID.get(), m.x, m.y - 0.2, m.z, 0, 0, -0.05, 0, 1.0);
            }
        }
    }

    private void dust(ServerLevel level, Vec3 at, int count, double spread) {
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (ground.isAir()) ground = Blocks.STONE.defaultBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.2, at.z, count, spread, 0.2, spread, 0.15);
    }

    private void dustLine(ServerLevel level, float yaw, double a, double b) {
        Vec3 dir = forward(yaw);
        for (double d = a; d <= b; d += 1.5) dust(level, position().add(dir.scale(d)), 3, 0.3);
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
            BossDeath.sound(this, SoundEvents.SPIDER_DEATH, 4.0F, 0.4F);
            BossDeath.on(this, Anchor.ENTITY, 0, ScreenEffect.OUTLINE, 48, 1.0F, 0xC03030);
            entityData.set(THREAD, Float.NaN);
            Telegraphs.cancel(this);
            ArachnidCocoon.freeAll(level, getId());
        }
        if (t == 18) { // over onto its back
            BossDeath.sound(this, SoundEvents.RAVAGER_STEP, 3.0F, 0.5F);
            BossDeath.shake(this, 10, 0.5F);
            dust(level, position(), 40, 2.0);
        }
        if (t < 18 && t % 3 == 0) level.sendParticles(ModParticles.VENOM.get(), c.x, c.y, c.z, 4, 1.0, 0.6, 1.0, 0.03);
        if (t > 24 && t < 70 && t % 6 == 0) { // the legs curling up
            BossDeath.sound(this, SoundEvents.SPIDER_STEP, 1.5F, 0.6F + t * 0.005F);
            level.sendParticles(ModParticles.VENOM.get(), c.x, c.y, c.z, 3, 1.2, 0.4, 1.2, 0.01);
            silk(level, c, 2, 1.2);
        }
        if (t == DEATH_TICKS) {
            BossDeath.sound(this, SoundEvents.SPIDER_HURT, 3.0F, 0.3F);
            BossDeath.screen(this, ScreenEffect.FLASH, 6, 0.25F, 0x802020);
            silk(level, c, 60, 1.6);
            level.sendParticles(ModParticles.VENOM.get(), c.x, c.y, c.z, 50, 1.6, 0.6, 1.6, 0.03);
            level.sendParticles(ModParticles.ACID_SPLASH.get(), c.x, getY() + 0.1, c.z, 16, 1.6, 0.0, 1.6, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 30, 1.4, 0.6, 1.4, 0.02);
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
        if (state() == SLEEPING && !level().isClientSide) {
            wakeUp();
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) targeting.onHurt(source, level().getGameTime());
        return hurt;
    }

    /**
     * In its first phase no blow takes it below half its health: it stops there and changes once the attack under way
     * is over (not on loading, nor for /kill).
     */
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
    public boolean onClimbable() {
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
        return !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
    }

    @Override
    public float getVoicePitch() {
        return 0.45F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return state() == AWAKE ? SoundEvents.SPIDER_AMBIENT : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SPIDER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SPIDER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.SPIDER_STEP, 1.2F, 0.5F);
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
        if (tag.contains("HomeY")) home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        entityData.set(PHASE, (byte) Math.max(1, tag.getByte("Phase")));
        boolean awake = tag.getBoolean("Awake");
        entityData.set(STATE, awake ? AWAKE : SLEEPING);
        if (awake) { // back on the floor (a fight cut off mid-air ends there)
            setNoGravity(false);
            entityData.set(THREAD, Float.NaN);
        }
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    @Override
    public void setCustomName(@Nullable net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }
}
