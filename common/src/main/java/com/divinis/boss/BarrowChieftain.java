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
import net.minecraft.core.particles.ParticleOptions;
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
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Barrow Chieftain (/summon divinis:barrow_chieftain on the floor of his crypt - his home): an undead war chief of
 * the bronze age, three blocks tall, with a long spear and a round bronze shield. He sleeps on a stone bier; a player
 * coming near or a blow wakes him: soul fire kindles in his eyes, he rises, puts on his helmet and strikes his spear
 * on his shield. Every attack is telegraphed (boss.Telegraphs: whoever stands in the red when it fills is hit) - with
 * good movement he can be beaten without taking a hit.
 *
 * Phase 1, on foot:
 * - Thrust: a long line before him - whoever is on it is hurled back;
 * - Shield wall: he raises his shield for four seconds, turning slowly after his foes; blows from the front are
 *   stopped (and answered with a bash of the shield on the half circle before him) - from behind and the sides they
 *   land, and harder;
 * - Spear throw: a circle under his target; the spear flies there, then back into his hand.
 * At half health he drives his spear into the ground and raises his arms: two horse skeletons claw their way out of
 * the floor with his stone war chariot, and he springs up into it (he cannot be hurt meanwhile).
 * Phase 2, in the chariot:
 * - Chariot charge: a lane is marked through the room towards a foe, and he drives down it - three times (four when
 *   enraged); driving into a wall or a pillar stuns him for four seconds;
 * - The circle: he drives once round the room; everything outside the middle is marked and bursts into soul fire when
 *   he is round - run into the middle;
 * - Guardians: three barrow guards rise from the floor (the circles); while any of them stands, he takes half damage;
 * - Rain of spears: three waves of circles over the room, spears falling into them.
 * Below 40% health he is enraged: faster, shorter rests, one charge more.
 * Dying, the chariot breaks apart and the horses fall to bones; he climbs down, kneels and crumbles into dust.
 * Timings match the animations in client/BarrowChieftainModelData (tools/models/build_barrow_chieftain.py).
 * Effects: soul fire (ModParticles.GHOST_FLAME) and grave dust (BONE_DUST), the world grey round him as he calls up his
 * chariot, a pale fog in the circle's fire, teal at his screams.
 */
public class BarrowChieftain extends Monster implements BossTargeting.Marked {
    public static final byte SLEEPING = 0, WAKING = 1, AWAKE = 2, TRANSFORM = 3;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(BarrowChieftain.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(BarrowChieftain.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(BarrowChieftain.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SPEAR_OUT = SynchedEntityData.defineId(BarrowChieftain.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> STUNNED = SynchedEntityData.defineId(BarrowChieftain.class, EntityDataSerializers.BOOLEAN);

    public enum Attack {
        NONE(0, 0), THRUST(32, 18), GUARD(92, 0), THROW(40, 26), CHARGE(0, 24), CIRCLE(76, 66), SUMMON(34, 30), RAIN(76, 30),
        ROAR(30, 0), BASH(18, 10);

        public final int length, impact;

        Attack(int length, int impact) {
            this.length = length;
            this.impact = impact;
        }
    }

    private static final byte EVENT_ATTACK = (byte) 185; // + attack ordinal (the Arachnid 170+)

    // waking (as the "wake" animation): eyes 10, sits 20-40, stands 40-60, the helmet 60-72, clangs 76 and 84
    public static final int WAKE_TICKS = 100, WAKE_EYES = 10;
    private static final int WAKE_STAND = 60, WAKE_CLANG = 76, WAKE_CLANG2 = 84;
    // the change (as "transform"): the spear planted 20, arms up 32, the horses 40-90, he springs up 90-104
    public static final int TRANSFORM_TICKS = 120;
    private static final int TRANSFORM_PLANT = 20, TRANSFORM_CALL = 32, TRANSFORM_RISE = 40, TRANSFORM_UP = 90, TRANSFORM_ROAR = 112;
    public static final int DEATH_TICKS = 100;

    private static final double WAKE_RANGE = 12, AGGRO_RANGE = 44;
    private static final float THRUST_DAMAGE = 16, THRUST_LENGTH = 9, THRUST_HALF_WIDTH = 1.1F;
    private static final int GUARD_UP = 8, GUARD_DOWN = 84, COUNTER_REST = 16;
    private static final float BASH_DAMAGE = 12, BASH_RADIUS = 4.8F, FRONT = 75;
    private static final float THROW_DAMAGE = 15, THROW_RADIUS = 2.6F;
    private static final int THROW_RELEASE = 16, THROW_BACK = 32;
    private static final float CHARGE_DAMAGE = 20, CHARGE_HALF_WIDTH = 1.9F, CHARGE_MAX = 26, CHARGE_SPEED = 1.0F;
    private static final int STUN_TICKS = 80;
    private static final float CIRCLE_DAMAGE = 18;
    private static final int CIRCLE_LAP = 60;
    private static final float GUARD_RISE_DAMAGE = 8;
    private static final int GUARDIANS = 3;
    private static final float SPEAR_DAMAGE = 14, SPEAR_RADIUS = 2.0F, RAIN_AREA = 14;
    private static final int[] RAIN_WAVES = {30, 46, 62};
    private static final int SPEAR_FALL = 8;
    private static final int TEAL = 0x5AE6DC;

    private static final AttributeModifier ENRAGED = new AttributeModifier(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.divinis.Divinis.MODID, "barrow_chieftain_enraged"),
            0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    /** the barrow guards he calls carry this tag (they go when he does) */
    public static final String GUARD_TAG = "divinis_barrow_guard";

    // client: animations
    public final AnimationState sleepAnimation = new AnimationState();
    public final AnimationState wakeAnimation = new AnimationState();
    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState rideAnimation = new AnimationState();
    public final AnimationState thrustAnimation = new AnimationState();
    public final AnimationState guardAnimation = new AnimationState();
    public final AnimationState bashAnimation = new AnimationState();
    public final AnimationState throwAnimation = new AnimationState();
    public final AnimationState transformAnimation = new AnimationState();
    public final AnimationState chargeAnimation = new AnimationState();
    public final AnimationState summonAnimation = new AnimationState();
    public final AnimationState rainAnimation = new AnimationState();
    public final AnimationState stunAnimation = new AnimationState();
    public final AnimationState roarAnimation = new AnimationState();
    public final AnimationState deathAnimation = new AnimationState();
    public int dying;
    /** Client: how far the wheels have turned (radians). */
    public float wheelTurn, wheelTurnO;
    private int clientAttackTicks, clientWakeStart = -1;

    // server: the fight
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.GREEN,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private final BossTargeting targeting = new BossTargeting();
    private Attack attack = Attack.NONE, last = Attack.NONE;
    private int attackTick, cooldown = 20, stateTick, counterRest, stun;
    private boolean pendingTransform, unclamped, guarding;
    private float lockedYaw;
    @Nullable private Vec3 home;
    // the throw, the charge, the circle
    private Vec3 aim = Vec3.ZERO, laneFrom = Vec3.ZERO, laneDir = Vec3.ZERO, circleCenter = Vec3.ZERO;
    private double laneLength, circleRadius, circleStart;
    private boolean laneBlocked;
    private int runsLeft, runTick;
    @Nullable private BarrowSpear thrown;
    private final List<Vec3> spots = new ArrayList<>();
    private final List<List<Vec3>> waves = new ArrayList<>();
    private final List<Entity> guards = new ArrayList<>();

    public BarrowChieftain(EntityType<? extends BarrowChieftain> type, Level level) {
        super(type, level);
        this.xpReward = 250;
        setPersistenceRequired();
        bossBar.setVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 340.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 44.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 16.0)
                .add(Attributes.STEP_HEIGHT, 1.2);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, SLEEPING);
        builder.define(PHASE, (byte) 1);
        builder.define(TARGET_ID, -1);
        builder.define(SPEAR_OUT, false);
        builder.define(STUNNED, false);
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

    /** Client: has he thrown his spear (it is not in his hand)? */
    public boolean spearOut() {
        return entityData.get(SPEAR_OUT);
    }

    public boolean stunned() {
        return entityData.get(STUNNED);
    }

    /** Client: an attack (or waking, changing, the stun) is playing - no walking, no looking round. */
    public boolean isAttacking() {
        return clientAttackTicks > 0 || state() != AWAKE || stunned();
    }

    /** Client: does soul fire burn in his eyes? */
    public boolean glowing() {
        return state() != SLEEPING && !(state() == WAKING && (clientWakeStart < 0 || tickCount - clientWakeStart < WAKE_EYES));
    }

    /** Client: is the chariot there (from the change on)? */
    public boolean chariotShown() {
        return phase() == 2 || state() == TRANSFORM;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions full = super.getDefaultDimensions(pose);
        if (entityData == null) return full;
        if (state() == SLEEPING) return EntityDimensions.scalable(1.6F, 1.6F);
        return phase() == 2 ? EntityDimensions.scalable(3.0F, 3.6F) : full;
    }

    // ------------------------------------------------------------------ client

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PHASE.equals(key) || STATE.equals(key)) refreshDimensions();
        if (!level().isClientSide) return;
        if (STATE.equals(key)) {
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
        if (STUNNED.equals(key)) {
            if (stunned()) {
                stopAttacks();
                stunAnimation.start(tickCount);
            } else {
                stunAnimation.stop();
            }
        }
    }

    private void stopAttacks() {
        for (AnimationState s : new AnimationState[] {thrustAnimation, guardAnimation, bashAnimation, throwAnimation, chargeAnimation,
                summonAnimation, rainAnimation, roarAnimation}) s.stop();
    }

    @Override
    public void handleEntityEvent(byte id) {
        int index = (id & 0xFF) - (EVENT_ATTACK & 0xFF);
        if (index > 0 && index < Attack.values().length) {
            Attack a = Attack.values()[index];
            if (a == Attack.BASH) { // over the guard
                bashAnimation.start(tickCount);
                return;
            }
            stopAttacks();
            (switch (a) {
                case THRUST -> thrustAnimation;
                case GUARD -> guardAnimation;
                case THROW -> throwAnimation;
                case CHARGE -> chargeAnimation;
                case SUMMON -> summonAnimation;
                case RAIN -> rainAnimation;
                default -> roarAnimation;
            }).start(tickCount);
            clientAttackTicks = a == Attack.CHARGE ? 24 : a.length;
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) return;
        wheelTurnO = wheelTurn;
        double moved = Math.sqrt((getX() - xo) * (getX() - xo) + (getZ() - zo) * (getZ() - zo));
        wheelTurn += (float) (moved / (7.2 * 1.5 / 16)); // the wheel's radius in blocks
        if (isDeadOrDying()) {
            deathAnimation.startIfStopped(tickCount);
            return;
        }
        byte s = state();
        if (s == SLEEPING) sleepAnimation.startIfStopped(tickCount);
        else sleepAnimation.stop();
        if (s == AWAKE && phase() == 1) idleAnimation.startIfStopped(tickCount);
        else idleAnimation.stop();
        if (s == AWAKE && phase() == 2) rideAnimation.startIfStopped(tickCount);
        else rideAnimation.stop();
        if (clientAttackTicks > 0) clientAttackTicks--;
        // soul fire from his eyes; from the hooves and the wheels as the chariot runs
        if (glowing() && tickCount % 4 == 0) {
            Vec3 eye = position().add(0, (phase() == 2 ? 3.4 : 2.65), 0).add(facing().scale(phase() == 2 ? 0.25 : 0.25));
            level().addParticle(ModParticles.GHOST_FLAME.get(), eye.x, eye.y, eye.z, 0, 0.01, 0);
        }
        if (phase() == 2 && moved > 0.15 && s == AWAKE) {
            Vec3 f = facing(), side = new Vec3(-f.z, 0, f.x);
            for (int sx : new int[] {-1, 1}) {
                Vec3 wheel = position().add(side.scale(sx * 1.0));
                level().addParticle(ModParticles.GHOST_FLAME.get(), wheel.x, getY() + 0.1, wheel.z, 0, 0.02, 0);
                Vec3 hoof = position().add(f.scale(3.4)).add(side.scale(sx * 0.75));
                if (random.nextInt(2) == 0) level().addParticle(ModParticles.BONE_DUST.get(), hoof.x, getY() + 0.1, hoof.z, 0, 0.03, 0);
            }
        }
    }

    private Vec3 facing() {
        float yaw = getYRot();
        return new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(5.0, 2.0, 5.0); // the spear, the chariot and the horses
    }

    // ------------------------------------------------------------------ server: states

    private Vec3 home() {
        if (home == null) home = position();
        return home;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (tickCount % 10 == 0) updateBossBar(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        guards.removeIf(e -> !e.isAlive());
        home();
        if (!guards.isEmpty() && tickCount % 5 == 0) links(level);
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

    private void wakeUp() {
        if (state() != SLEEPING) return;
        entityData.set(STATE, WAKING);
        stateTick = 0;
        playSound(SoundEvents.SKELETON_AMBIENT, 3.0F, 0.4F);
    }

    private void waking(ServerLevel level) {
        getNavigation().stop();
        stateTick++;
        int t = stateTick;
        if (t == WAKE_EYES) {
            playSound(SoundEvents.SOUL_ESCAPE.value(), 3.0F, 0.6F);
            playSound(SoundEvents.FIRECHARGE_USE, 2.0F, 0.5F);
            Vec3 head = position().add(0, 1.0, 0);
            level.sendParticles(ModParticles.GHOST_FLAME.get(), head.x, head.y, head.z, 12, 0.4, 0.2, 0.4, 0.02);
        }
        if (t > 20 && t < WAKE_STAND && t % 5 == 0) {
            playSound(SoundEvents.SKELETON_STEP, 1.5F, 0.5F);
            level.sendParticles(ModParticles.BONE_DUST.get(), getX(), getY() + 1.0, getZ(), 6, 0.6, 0.4, 1.2, 0.02);
        }
        if (t == WAKE_STAND) {
            playSound(SoundEvents.GRINDSTONE_USE, 2.0F, 0.5F); // the bier sinking
            dust(level, position(), 30, 1.5);
            refreshDimensions();
        }
        if (t == WAKE_CLANG || t == WAKE_CLANG2) {
            playSound(SoundEvents.ANVIL_LAND, 2.5F, t == WAKE_CLANG ? 0.7F : 0.6F);
            playSound(SoundEvents.SHIELD_BLOCK, 3.0F, 0.5F);
            Vec3 s = position().add(0, 1.6, 0).add(facing().scale(0.6));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, s.x, s.y, s.z, 14, 0.3, 0.3, 0.3, 0.3);
            ScreenFx.shake(level, position(), 24, 6, t == WAKE_CLANG ? 0.3F : 0.45F);
        }
        if (t == WAKE_CLANG2 + 4) scream(level, 0.5F);
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
            if (thrown != null) thrown.discard();
            for (Skeleton s : level.getEntitiesOfClass(Skeleton.class, getBoundingBox().inflate(64), s -> s.getTags().contains(GUARD_TAG))) {
                s.discard();
            }
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ server: the change

    private void startTransform(ServerLevel level) {
        pendingTransform = false;
        attack = Attack.NONE;
        guarding = false;
        Telegraphs.cancel(this);
        getNavigation().stop();
        entityData.set(STATE, TRANSFORM);
        stateTick = 0;
        playSound(SoundEvents.SKELETON_HURT, 3.0F, 0.4F);
        BossDeath.on(this, Anchor.EXCEPT, 0, ScreenEffect.DESATURATE, TRANSFORM_TICKS - 10, 1.0F, -1);
    }

    private void transforming(ServerLevel level) {
        stateTick++;
        int t = stateTick;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (t == TRANSFORM_PLANT) { // the spear into the ground
            playSound(SoundEvents.TRIDENT_HIT_GROUND, 3.0F, 0.5F);
            dust(level, position().add(facing().scale(0.8)), 20, 0.6);
            ScreenFx.shake(level, position(), 24, 8, 0.4F);
        }
        if (t == TRANSFORM_CALL) {
            playSound(SoundEvents.WITHER_SKELETON_AMBIENT, 3.0F, 0.5F);
            ScreenFx.playNear(level, position(), 40, ScreenEffect.FOG, TRANSFORM_TICKS - t, 0.6F, 0x3A8A86);
        }
        if (t >= TRANSFORM_RISE && t < TRANSFORM_UP && t % 3 == 0) { // the horses and the chariot clawing out of the floor
            Vec3 f = facing();
            for (int i = 0; i < 3; i++) {
                Vec3 p = position().add(f.scale(random.nextDouble() * 5 - 0.5)).add((random.nextDouble() - 0.5) * 3, 0.1, (random.nextDouble() - 0.5) * 3);
                level.sendParticles(ModParticles.BONE_DUST.get(), p.x, p.y, p.z, 4, 0.3, 0.2, 0.3, 0.05);
                level.sendParticles(ModParticles.GHOST_FLAME.get(), p.x, p.y, p.z, 1, 0.2, 0.1, 0.2, 0.02);
                dust(level, p, 3, 0.4);
            }
            if (t % 9 == 0) {
                playSound(SoundEvents.SKELETON_HORSE_AMBIENT, 3.0F, 0.5F + random.nextFloat() * 0.2F);
                playSound(SoundEvents.ROOTED_DIRT_BREAK, 2.5F, 0.5F);
                ScreenFx.shake(level, position(), 24, 5, 0.25F);
            }
        }
        if (t == TRANSFORM_UP) {
            entityData.set(PHASE, (byte) 2);
            playSound(SoundEvents.SKELETON_HORSE_DEATH, 3.0F, 0.6F);
            BossDeath.on(this, Anchor.EXCEPT, 0, ScreenEffect.FLASH, 6, 0.3F, TEAL);
            BossDeath.ring(level, ModParticles.GHOST_FLAME.get(), position().add(0, 0.2, 0), 3.0, 40, 0.04);
        }
        if (t == TRANSFORM_ROAR) scream(level, 0.8F);
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
            if (attack == Attack.NONE && stun == 0) start(level, Attack.ROAR, getTarget());
        }
        if (counterRest > 0) counterRest--;
        if (stun > 0) {
            stunTick(level);
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
        if (target == null) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(target, 30, 30);
        double dist = Math.sqrt(distanceToSqr(target.getX(), getY(), target.getZ()));
        if (cooldown > 0) cooldown--;
        if (cooldown <= 0 && dist < 28) {
            start(level, choose(dist), target);
            return;
        }
        double keep = phase() == 1 ? 4.5 : 9;
        if (dist > keep) {
            if (tickCount % 8 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
        }
    }

    private Attack choose(double dist) {
        List<Attack> options = new ArrayList<>();
        if (phase() == 1) {
            options.add(Attack.THROW);
            if (dist <= 8) {
                options.add(Attack.THRUST);
                options.add(Attack.THRUST);
                options.add(Attack.GUARD);
            }
        } else {
            options.add(Attack.CHARGE);
            options.add(Attack.CIRCLE);
            options.add(Attack.RAIN);
            if (guards.isEmpty()) options.add(Attack.SUMMON);
        }
        if (options.size() > 1) options.removeIf(a -> a == last);
        return options.get(random.nextInt(options.size()));
    }

    /** /bossattack: starts an attack by name now, if he can. */
    public boolean forceAttack(String name) {
        if (!(level() instanceof ServerLevel level) || state() != AWAKE || attack != Attack.NONE || stun > 0) return false;
        if (name.equals("chariot")) {
            if (phase() != 1) return false;
            startTransform(level);
            return true;
        }
        Attack a = switch (name) {
            case "thrust" -> Attack.THRUST;
            case "guard" -> Attack.GUARD;
            case "throw" -> Attack.THROW;
            case "charge" -> Attack.CHARGE;
            case "circle" -> Attack.CIRCLE;
            case "guardians" -> Attack.SUMMON;
            case "spears" -> Attack.RAIN;
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
        lockedYaw = target != null ? yawTo(position(), target.position()) : getYRot();
        lockYaw();
        level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + a.ordinal()));
        Vec3 at = position();
        Vec3 fwd = forward(lockedYaw);
        switch (a) {
            case THRUST -> {
                Telegraphs.show(level, Telegraph.rect(at.add(fwd.scale(0.5)), lockedYaw, THRUST_LENGTH, THRUST_HALF_WIDTH), a.impact, 4, this,
                        e -> hit(e, THRUST_DAMAGE, 2.2, 0.5));
                playSound(SoundEvents.SKELETON_AMBIENT, 2.5F, 0.5F);
            }
            case GUARD -> {
                guarding = false;
                playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 3.0F, 0.6F);
            }
            case THROW -> {
                aim = target != null ? target.position() : at.add(fwd.scale(8));
                Telegraphs.show(level, Telegraph.circle(aim, THROW_RADIUS), a.impact, 5, this, e -> hit(e, THROW_DAMAGE, 0.8, 0.4));
                playSound(SoundEvents.SKELETON_AMBIENT, 2.5F, 0.6F);
            }
            case CHARGE -> {
                runsLeft = enraged() ? 4 : 3;
                beginRun(level, target);
                setNoGravity(false);
            }
            case CIRCLE -> {
                circleCenter = home().distanceTo(at) < 20 ? home() : at;
                circleRadius = Mth.clamp(at.distanceTo(new Vec3(circleCenter.x, at.y, circleCenter.z)), 8, 13);
                circleStart = Math.atan2(at.z - circleCenter.z, at.x - circleCenter.x);
                Telegraphs.show(level, Telegraph.ring(circleCenter, (float) circleRadius - 3.5F, (float) circleRadius + 10), a.impact, 8, this,
                        e -> {
                            hit(e, CIRCLE_DAMAGE, 0.6, 0.6);
                            e.igniteForSeconds(2);
                        });
                playSound(SoundEvents.SKELETON_HORSE_AMBIENT, 3.0F, 0.4F);
            }
            case SUMMON -> {
                spots.clear();
                for (int i = 0; i < GUARDIANS; i++) {
                    Vec3 p = at.add(forward(lockedYaw + (i - 1) * 70).scale(4.5));
                    spots.add(new Vec3(p.x, at.y, p.z));
                    Telegraphs.show(level, Telegraph.circle(spots.get(i), 1.5F), a.impact, 4, this, e -> hit(e, GUARD_RISE_DAMAGE, 0.8, 0.6));
                }
                playSound(SoundEvents.WITHER_SKELETON_AMBIENT, 3.0F, 0.5F);
            }
            case RAIN -> {
                waves.clear();
                Vec3 c = home().distanceTo(at) < 24 ? home() : at;
                List<Player> players = fighters();
                for (int w = 0; w < RAIN_WAVES.length; w++) {
                    List<Vec3> wave = new ArrayList<>();
                    for (Player p : players) wave.add(p.position());
                    for (int i = 0; i < 8; i++) {
                        double ang = random.nextDouble() * Mth.TWO_PI, r = Math.sqrt(random.nextDouble()) * RAIN_AREA;
                        wave.add(new Vec3(c.x + Math.cos(ang) * r, c.y, c.z + Math.sin(ang) * r));
                    }
                    for (Vec3 p : wave) {
                        Telegraphs.show(level, Telegraph.circle(p, SPEAR_RADIUS), RAIN_WAVES[w], 4, this, e -> hit(e, SPEAR_DAMAGE, 0.3, 0.2));
                    }
                    waves.add(wave);
                }
                playSound(SoundEvents.WITHER_SKELETON_AMBIENT, 3.0F, 0.7F);
            }
            case ROAR -> scream(level, 0.7F);
            default -> {}
        }
    }

    private void attackTick(ServerLevel level, @Nullable LivingEntity target) {
        attackTick++;
        int t = attackTick;
        if (attack != Attack.GUARD && attack != Attack.CHARGE && attack != Attack.CIRCLE) lockYaw();
        if (attack != Attack.CHARGE && attack != Attack.CIRCLE) setDeltaMovement(0, getDeltaMovement().y, 0);
        switch (attack) {
            case THRUST -> {
                if (t == attack.impact) {
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.5F);
                    playSound(SoundEvents.TRIDENT_THROW.value(), 2.0F, 0.6F);
                    Vec3 f = forward(lockedYaw);
                    for (double d = 1; d <= THRUST_LENGTH; d += 1) {
                        Vec3 p = position().add(f.scale(d)).add(0, 1.4, 0);
                        level.sendParticles(ModParticles.GHOST_FLAME.get(), p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.01);
                    }
                    ScreenFx.shake(level, position(), 16, 4, 0.25F);
                }
            }
            case GUARD -> {
                guarding = t >= GUARD_UP && t < GUARD_DOWN;
                LivingEntity near = target != null ? target : nearestPlayer(16);
                if (guarding && near != null) { // turning slowly after his foe: slower than one can walk round him
                    float want = yawTo(position(), near.position());
                    lockedYaw += Mth.clamp(Mth.wrapDegrees(want - lockedYaw), -3.0F, 3.0F);
                }
                lockYaw();
                if (guarding && t % 6 == 0) {
                    Vec3 s = position().add(0, 1.6, 0).add(forward(lockedYaw).scale(0.9));
                    level.sendParticles(ModParticles.GHOST_FLAME.get(), s.x, s.y, s.z, 2, 0.4, 0.5, 0.1, 0.01);
                }
                if (t == GUARD_DOWN) guarding = false;
            }
            case BASH -> {
                lockYaw();
                if (t == attack.impact) {
                    playSound(SoundEvents.SHIELD_BLOCK, 3.0F, 0.5F);
                    playSound(SoundEvents.ANVIL_LAND, 1.5F, 1.2F);
                    Vec3 f = position().add(forward(lockedYaw).scale(2.0));
                    level.sendParticles(ParticleTypes.CLOUD, f.x, getY() + 0.5, f.z, 14, 1.2, 0.2, 1.2, 0.05);
                    ScreenFx.shake(level, position(), 14, 5, 0.35F);
                }
                if (t >= attack.length) { // back to the guard
                    attack = Attack.GUARD;
                    attackTick = bashResume;
                    level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + Attack.GUARD.ordinal()));
                    return;
                }
            }
            case THROW -> {
                if (t == THROW_RELEASE) {
                    Vec3 hand = position().add(0, 2.6, 0).add(forward(lockedYaw).scale(0.6));
                    BarrowSpear spear = ModEntities.BARROW_SPEAR.get().create(level);
                    if (spear != null) {
                        spear.thrown(hand, aim, attack.impact - THROW_RELEASE, THROW_BACK - attack.impact, this);
                        level.addFreshEntity(spear);
                        thrown = spear;
                    }
                    entityData.set(SPEAR_OUT, true);
                    playSound(SoundEvents.TRIDENT_THROW.value(), 3.0F, 0.6F);
                }
                if (t == attack.impact) {
                    level.playSound(null, aim.x, aim.y, aim.z, SoundEvents.TRIDENT_HIT_GROUND, net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.6F);
                    dust(level, aim, 20, 1.0);
                    BossDeath.ring(level, ModParticles.GHOST_FLAME.get(), aim.add(0, 0.2, 0), THROW_RADIUS, 20, 0.03);
                    ScreenFx.shake(level, aim, 16, 5, 0.35F);
                }
                if (t == THROW_BACK) {
                    if (thrown != null) thrown.discard();
                    thrown = null;
                    entityData.set(SPEAR_OUT, false);
                    playSound(SoundEvents.TRIDENT_RETURN, 2.5F, 0.7F);
                }
            }
            case CHARGE -> {
                chargeTick(level, target);
                return;
            }
            case CIRCLE -> circleTick(level, t);
            case SUMMON -> {
                if (t == 20) { // the butt of the spear struck on the car
                    playSound(SoundEvents.ANVIL_LAND, 2.0F, 0.5F);
                    ScreenFx.shake(level, position(), 20, 6, 0.3F);
                }
                if (t > 20 && t < attack.impact) {
                    for (Vec3 s : spots) {
                        level.sendParticles(ModParticles.BONE_DUST.get(), s.x, s.y + 0.1, s.z, 3, 0.4, 0.1, 0.4, 0.04);
                        dust(level, s, 2, 0.4);
                    }
                }
                if (t == attack.impact) {
                    for (Vec3 s : spots) raiseGuard(level, s);
                    playSound(SoundEvents.SKELETON_AMBIENT, 3.0F, 0.4F);
                }
            }
            case RAIN -> {
                for (int w = 0; w < RAIN_WAVES.length; w++) {
                    if (t == RAIN_WAVES[w] - SPEAR_FALL) {
                        for (Vec3 p : waves.get(w)) {
                            BarrowSpear spear = ModEntities.BARROW_SPEAR.get().create(level);
                            if (spear == null) continue;
                            spear.falling(p, SPEAR_FALL);
                            level.addFreshEntity(spear);
                        }
                    }
                    if (t == RAIN_WAVES[w]) {
                        Vec3 c = home();
                        level.playSound(null, c.x, c.y, c.z, SoundEvents.TRIDENT_HIT_GROUND, net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.5F);
                        for (Vec3 p : waves.get(w)) dust(level, p, 6, 0.5);
                        ScreenFx.shake(level, c, 30, 5, 0.3F);
                    }
                }
                if (t == 20) playSound(SoundEvents.WITHER_SKELETON_AMBIENT, 3.0F, 0.4F);
            }
            default -> {}
        }
        if (t >= attack.length) endAttack();
    }

    private int bashResume;

    private void endAttack() {
        attack = Attack.NONE;
        guarding = false;
        cooldown = enraged() ? 14 + random.nextInt(12) : (phase() == 2 ? 24 : 30) + random.nextInt(15);
    }

    // ------------------------------------------------------------------ the charge

    /** A new run: the lane towards a foe (to the first wall or pillar), marked piece by piece as he will reach it. */
    private void beginRun(ServerLevel level, @Nullable LivingEntity target) {
        runTick = 0;
        Vec3 at = position();
        LivingEntity t = target != null && validTarget(target) ? target : nearestPlayer(AGGRO_RANGE);
        lockedYaw = t != null ? yawTo(at, t.position()) : getYRot();
        lockYaw();
        laneDir = forward(lockedYaw);
        laneFrom = at;
        Vec3 eye = at.add(0, 1.0, 0);
        HitResult hit = level.clip(new ClipContext(eye, eye.add(laneDir.scale(CHARGE_MAX + 3)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        laneBlocked = hit.getType() == HitResult.Type.BLOCK;
        laneLength = laneBlocked ? Math.max(2, hit.getLocation().distanceTo(eye) - 2.2) : CHARGE_MAX;
        int windup = Attack.CHARGE.impact;
        for (double d = 0; d < laneLength; d += 3) {
            double len = Math.min(3, laneLength - d);
            Telegraphs.show(level, Telegraph.rect(at.add(laneDir.scale(d)), lockedYaw, (float) len + 1.5F, CHARGE_HALF_WIDTH),
                    windup + (int) Math.ceil(d / CHARGE_SPEED), 3, this, e -> hit(e, CHARGE_DAMAGE, 2.4, 0.7));
        }
        level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + Attack.CHARGE.ordinal()));
        playSound(SoundEvents.SKELETON_HORSE_AMBIENT, 3.0F, 0.6F);
    }

    private void chargeTick(ServerLevel level, @Nullable LivingEntity target) {
        runTick++;
        int windup = Attack.CHARGE.impact;
        lockYaw();
        if (runTick < windup) {
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (runTick == windup / 2) {
                playSound(SoundEvents.SKELETON_HORSE_DEATH, 2.5F, 1.0F);
                dust(level, position().add(laneDir.scale(3)), 10, 1.0);
            }
            return;
        }
        double d = (runTick - windup) * CHARGE_SPEED;
        if (d <= laneLength) {
            Vec3 p = laneFrom.add(laneDir.scale(d));
            setPos(p.x, getY(), p.z);
            setDeltaMovement(laneDir.scale(CHARGE_SPEED).add(0, getDeltaMovement().y, 0));
            if (runTick % 3 == 0) {
                playSound(SoundEvents.HORSE_GALLOP, 1.5F, 0.6F);
                ScreenFx.shake(level, position(), 10, 3, 0.2F);
                dust(level, position(), 4, 1.0);
            }
            return;
        }
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (laneBlocked) { // into the wall: stunned
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.5F, 0.7F);
            playSound(SoundEvents.SKELETON_HORSE_HURT, 3.0F, 0.5F);
            playSound(SoundEvents.STONE_BREAK, 3.0F, 0.5F);
            Vec3 front = position().add(laneDir.scale(3.2));
            dust(level, front, 50, 1.5);
            level.sendParticles(ModParticles.BONE_DUST.get(), front.x, front.y + 1, front.z, 30, 1.2, 0.8, 1.2, 0.08);
            ScreenFx.shake(level, position(), 28, 14, 0.8F);
            ScreenFx.playNear(level, position(), 8, ScreenEffect.FLASH, 5, 0.3F, 0xFFFFFF);
            stun = STUN_TICKS;
            entityData.set(STUNNED, true);
            attack = Attack.NONE;
            return;
        }
        runsLeft--;
        if (runsLeft > 0) {
            setTarget(targeting.pick(this, position(), AGGRO_RANGE, getTarget()));
            beginRun(level, getTarget());
        } else {
            endAttack();
        }
    }

    private void stunTick(ServerLevel level) {
        stun--;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (stun % 4 == 0) {
            Vec3 h = position().add(0, getBbHeight() * 0.95, 0);
            double a = stun * 0.8;
            level.sendParticles(ParticleTypes.CRIT, h.x + Math.cos(a) * 0.7, h.y, h.z + Math.sin(a) * 0.7, 2, 0.05, 0.05, 0.05, 0.0);
        }
        if (stun == 0) {
            entityData.set(STUNNED, false);
            endAttack();
        }
    }

    // ------------------------------------------------------------------ the circle

    private void circleTick(ServerLevel level, int t) {
        if (t <= CIRCLE_LAP) {
            double ang = circleStart + t / (double) CIRCLE_LAP * Mth.TWO_PI;
            double x = circleCenter.x + Math.cos(ang) * circleRadius, z = circleCenter.z + Math.sin(ang) * circleRadius;
            Vec3 next = new Vec3(circleCenter.x + Math.cos(ang + 0.1) * circleRadius, getY(), circleCenter.z + Math.sin(ang + 0.1) * circleRadius);
            setPos(x, getY(), z);
            lockedYaw = yawTo(position(), next);
            lockYaw();
            setDeltaMovement(next.subtract(position()).normalize().scale(1.0).add(0, getDeltaMovement().y, 0));
            // a trail of soul fire where he has been
            level.sendParticles(ModParticles.GHOST_FLAME.get(), x, getY() + 0.1, z, 4, 0.7, 0.05, 0.7, 0.01);
            if (t % 3 == 0) {
                playSound(SoundEvents.HORSE_GALLOP, 1.5F, 0.6F);
                dust(level, position(), 3, 0.8);
            }
        } else {
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        if (t == attack.impact) { // the ring bursts into soul fire
            for (int i = 0; i < 90; i++) {
                double ang = random.nextDouble() * Mth.TWO_PI, r = circleRadius - 3.5 + random.nextDouble() * 13.5;
                double x = circleCenter.x + Math.cos(ang) * r, z = circleCenter.z + Math.sin(ang) * r;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, circleCenter.y + 0.2, z, 3, 0.2, 0.6, 0.2, 0.05);
                if (i % 3 == 0) level.sendParticles(ModParticles.GHOST_FLAME.get(), x, circleCenter.y + 0.2, z, 2, 0.3, 0.4, 0.3, 0.03);
            }
            level.playSound(null, circleCenter.x, circleCenter.y, circleCenter.z, SoundEvents.BLAZE_SHOOT, net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.5F);
            level.playSound(null, circleCenter.x, circleCenter.y, circleCenter.z, SoundEvents.SOUL_ESCAPE.value(), net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.6F);
            ScreenFx.shake(level, circleCenter, 30, 10, 0.5F);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(circleCenter) < (circleRadius + 14) * (circleRadius + 14)) {
                    ScreenFx.play(p, ScreenEffect.FOG, 30, 0.5F, 0x3A8A86, "");
                }
            }
        }
    }

    // ------------------------------------------------------------------ guards

    private void raiseGuard(ServerLevel level, Vec3 at) {
        Skeleton s = EntityType.SKELETON.create(level);
        if (s == null) return;
        s.moveTo(at.x, at.y, at.z, lockedYaw, 0);
        s.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
        s.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        s.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
        s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        s.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        for (EquipmentSlot slot : EquipmentSlot.values()) s.setDropChance(slot, 0);
        s.setCustomName(net.minecraft.network.chat.Component.literal("Barrow Guard"));
        s.addTag(GUARD_TAG);
        level.addFreshEntity(s);
        guards.add(s);
        dust(level, at, 25, 0.6);
        level.sendParticles(ModParticles.BONE_DUST.get(), at.x, at.y + 0.5, at.z, 20, 0.4, 0.6, 0.4, 0.05);
        level.sendParticles(ModParticles.GHOST_FLAME.get(), at.x, at.y + 0.3, at.z, 8, 0.4, 0.3, 0.4, 0.03);
    }

    /** Soul fire running from each guard to him: they shield him. */
    private void links(ServerLevel level) {
        Vec3 me = position().add(0, getBbHeight() * 0.6, 0);
        for (Entity g : guards) {
            Vec3 from = g.position().add(0, 1.2, 0);
            Vec3 step = me.subtract(from).scale(1 / 8.0);
            int k = (tickCount / 5) % 8;
            Vec3 p = from.add(step.scale(k));
            level.sendParticles(ModParticles.GHOST_FLAME.get(), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    // ------------------------------------------------------------------ hits and effects

    float damageScale() {
        var attr = getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null || attr.getBaseValue() <= 0 ? 1.0F : (float) (attr.getValue() / attr.getBaseValue());
    }

    private void hit(LivingEntity e, float damage, double push, double up) {
        if (e instanceof Skeleton s && s.getTags().contains(GUARD_TAG)) return; // his own guards
        if (!e.hurt(damageSources().mobAttack(this), damage * damageScale())) return;
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0) return;
        e.setDeltaMovement(e.getDeltaMovement().add(dx / len * push * resist, up * resist, dz / len * push * resist));
        e.hurtMarked = true;
    }

    private void dust(ServerLevel level, Vec3 at, int count, double spread) {
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (ground.isAir()) ground = Blocks.STONE.defaultBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.2, at.z, count, spread, 0.2, spread, 0.15);
    }

    /** A scream: the screen shakes, teal at the edges, the view widening, for everyone near. */
    private void scream(ServerLevel level, float strength) {
        playSound(SoundEvents.WITHER_SKELETON_DEATH, 3.0F, 0.5F);
        playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.8F);
        ScreenFx.shake(level, position(), 32, 14, strength);
        ScreenFx.playNear(level, position(), 32, ScreenEffect.VIGNETTE, 30, strength, 0x2A8A84);
        ScreenFx.playNear(level, position(), 20, ScreenEffect.FOV, 16, strength * 0.6F, -1);
        Vec3 h = position().add(0, getBbHeight() * 0.85, 0);
        level.sendParticles(ModParticles.GHOST_FLAME.get(), h.x, h.y, h.z, 20, 0.6, 0.4, 0.6, 0.05);
    }

    private static void burst(ServerLevel level, ParticleOptions p, Vec3 at, int count, double spread) {
        level.sendParticles(p, at.x, at.y, at.z, count, spread, spread * 0.6, spread, 0.04);
    }

    // ------------------------------------------------------------------ dying

    @Override
    protected void tickDeath() {
        dying++;
        if (!(level() instanceof ServerLevel level)) return;
        int t = dying;
        Vec3 c = getBoundingBox().getCenter();
        if (t == 1) {
            BossDeath.sound(this, SoundEvents.WITHER_SKELETON_DEATH, 3.0F, 0.4F);
            BossDeath.on(this, Anchor.ENTITY, 0, ScreenEffect.OUTLINE, 60, 1.0F, TEAL);
            Telegraphs.cancel(this);
            if (thrown != null) thrown.discard();
        }
        if (t == 16 || t == 24) { // the chariot breaks apart, the horses fall to bones
            BossDeath.sound(this, SoundEvents.STONE_BREAK, 3.0F, 0.5F);
            BossDeath.sound(this, SoundEvents.SKELETON_HORSE_DEATH, 3.0F, 0.5F);
            BossDeath.sound(this, SoundEvents.BONE_BLOCK_BREAK, 3.0F, 0.6F);
            BossDeath.shake(this, 10, 0.5F);
            dust(level, position(), 40, 2.0);
            burst(level, ModParticles.BONE_DUST.get(), position().add(facing().scale(3)).add(0, 1, 0), 40, 1.5);
        }
        if (t == 48) BossDeath.sound(this, SoundEvents.SKELETON_STEP, 2.0F, 0.5F); // kneeling
        if (t > 50 && t % 3 == 0) { // crumbling to dust
            burst(level, ModParticles.BONE_DUST.get(), c, 6, 0.6);
            level.sendParticles(ModParticles.GHOST_FLAME.get(), c.x, c.y + 0.5, c.z, 1, 0.3, 0.4, 0.3, 0.02);
            if (t % 9 == 0) BossDeath.sound(this, SoundEvents.SAND_BREAK, 1.5F, 0.6F);
        }
        if (t == DEATH_TICKS) {
            BossDeath.sound(this, SoundEvents.SOUL_ESCAPE.value(), 3.0F, 0.5F);
            BossDeath.screen(this, ScreenEffect.FLASH, 6, 0.3F, TEAL);
            burst(level, ModParticles.BONE_DUST.get(), c, 80, 1.2);
            burst(level, ModParticles.GHOST_FLAME.get(), c, 30, 0.8);
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

    /** Is the blow coming from before him (within FRONT degrees of where he faces)? */
    private boolean fromFront(DamageSource source) {
        Vec3 from = source.getSourcePosition();
        if (from == null && source.getEntity() != null) from = source.getEntity().position();
        if (from == null) return false;
        Vec3 to = from.subtract(position());
        to = new Vec3(to.x, 0, to.z);
        if (to.lengthSqr() < 1e-4) return false;
        double cos = to.normalize().dot(forward(getYRot()));
        return cos > Math.cos(Math.toRadians(FRONT));
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
        if (!level().isClientSide) {
            if (guarding && fromFront(source)) { // the shield
                ServerLevel level = (ServerLevel) level();
                playSound(SoundEvents.SHIELD_BLOCK, 2.5F, 0.6F);
                Vec3 s = position().add(0, 1.6, 0).add(forward(getYRot()).scale(0.9));
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, s.x, s.y, s.z, 10, 0.3, 0.3, 0.3, 0.2);
                if (counterRest == 0 && attack == Attack.GUARD && source.getEntity() instanceof Player p && p.distanceTo(this) < 7) {
                    counterRest = COUNTER_REST;
                    bashResume = attackTick;
                    attack = Attack.BASH;
                    attackTick = 0;
                    lockedYaw = getYRot();
                    Telegraphs.show(level, Telegraph.sector(position(), lockedYaw, BASH_RADIUS, FRONT), Attack.BASH.impact, 4, this,
                            e -> hit(e, BASH_DAMAGE, 1.8, 0.5));
                    level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + Attack.BASH.ordinal()));
                }
                return false;
            }
            if (guarding) amount *= 1.25F;             // caught from behind or the side
            if (stun > 0) amount *= 1.25F;              // stunned
            if (!guards.isEmpty()) amount *= 0.5F;      // his guards shield him
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) targeting.onHurt(source, level().getGameTime());
        return hurt;
    }

    /** In his first phase no blow takes him below half his health: he changes once the attack under way is over. */
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
    public float getVoicePitch() {
        return 0.5F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return state() == AWAKE ? SoundEvents.SKELETON_AMBIENT : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (phase() == 2) playSound(SoundEvents.HORSE_GALLOP, 1.0F, 0.6F);
        else playSound(SoundEvents.SKELETON_STEP, 1.2F, 0.6F);
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
        entityData.set(STATE, tag.getBoolean("Awake") ? AWAKE : SLEEPING);
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    @Override
    public void setCustomName(@Nullable net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }
}
