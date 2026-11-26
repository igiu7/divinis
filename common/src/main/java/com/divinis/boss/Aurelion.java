package com.divinis.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenEffect.Anchor;
import com.divinis.screenfx.ScreenFx;
import com.divinis.net.BeamPayload;
import com.divinis.net.FireFxPayload;
import com.divinis.platform.net.PacketDistributor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
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
 * Aurelion, Who Was Before Light (/summon divinis:aurelion) - the final boss: a cluster of many eyes on angelic
 * wings, a great symbol of gold behind it and two dead hands floating at its sides. Summon it on the floor in the
 * middle of its room (its home).
 *
 * At first there is nothing. When a player comes near, the sky tears open high above the room, shafts of light pour
 * out of the tear and it comes down out of it, the light flaring as it arrives - then its name, and the fight.
 * Phase 1 (all of its health for now) - it hovers over the floor, turning to its target, and:
 * - Black hole: it opens a black hole over the room that lifts every player into the air; floating (and steering
 *   as they can), they must dodge beams of light shot through the room from every side, from the floor and from
 *   above - each one first a thin red laser marking where it will be; then the hole closes and lets them down;
 * - Judgement: the world turns black and white but for it and its beams, a haze blurs round it; a beam from its
 *   eyes follows each player along the ground, red at first, then burning light, about as fast as a walk and
 *   faster as it goes - keep running;
 * - The dead hands: its hands fly over the players one by one and slam down on marked circles (the world flashes
 *   inverted at each blow, but for it), then come at one player from both sides and clap.
 *
 * At half health the veil is torn (phase 2): every eye rolls back, the wings fold round it, the golden symbol
 * shudders and cracks - then bursts into eight shards in a flash, and it screams in rage while its health comes back
 * in full. From then on it is blood-soaked, its eyes bulging and bleeding, the sky over the room torn wide open, the
 * shards flying round it; and besides the first attacks:
 * - Shards of the sun: a shard marks a player with a laser and fires through them, again and again; then all eight
 *   rise into a ring over the room and burn a spiral down into the floor, from the edge in;
 * - Eclipse: darkness closes in, only it (outlined) is seen; giant eyes open in the dark all round the room, each
 *   flaring red as it marks a player with a laser, then fires;
 * - Judgement of two hands: a hand comes for a player (a circle marks where it closes) and carries them up; anyone -
 *   the one held too - must hit that hand ten times (blade, bow, anything) before it crushes them;
 * - Inverted light: the world turns inside out but for it, and the marks are the only safe places - everything
 *   outside them burns, twice;
 * - Star collapse: it plants panes of light in the floor, a sun swells over it while the world blurs, shakes and
 *   crackles, the sun falls in on itself into a black hole that pulls - and goes off: only those behind a pane of
 *   light live through it unhurt.
 */
public class Aurelion extends Monster implements BossTargeting.Marked, com.divinis.platform.entity.MultipartEntity {
    public static final byte DORMANT = 0, DESCEND = 1, AWAKE = 2, TRANSFORM = 3;
    public static final byte NONE = 0, HOLE = 1, GAZE = 2, HANDS = 3, SHARDS = 4, ECLIPSE = 5, GRAB = 6, INVERTED = 7, COLLAPSE = 8;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Aurelion.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(Aurelion.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> HOME = SynchedEntityData.defineId(Aurelion.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Byte> ATTACK = SynchedEntityData.defineId(Aurelion.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Vector3f> HAND_L = SynchedEntityData.defineId(Aurelion.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> HAND_R = SynchedEntityData.defineId(Aurelion.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> GRIP_L = SynchedEntityData.defineId(Aurelion.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> GRIP_R = SynchedEntityData.defineId(Aurelion.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(Aurelion.class, EntityDataSerializers.BYTE);

    private static final byte EVENT_SUMMON = 90, EVENT_GAZE = 91, EVENT_SEAL = 92, EVENT_SCREAM = 93, EVENT_CAST = 94, EVENT_WRAP = 95,
            EVENT_REACH = 96, EVENT_HOLD = 97, EVENT_HOLD_END = 98;
    public static final double HOVER = 1.5, CRACK_HEIGHT = 40, HOLE_ABOVE = 11;
    public static final int DESCEND_TICKS = 160;
    private static final double WAKE_RANGE = 22, AGGRO = 64, ROOM_R = 26;

    // black hole
    private static final ResourceLocation FLOAT_ID = ResourceLocation.fromNamespaceAndPath("divinis", "aurelion_float");
    private static final double LIFT_RANGE = 48;
    private static final int HOLE_FORM = 30, WAVE_START = 60, WAVE_EVERY = 32, WAVES = 7, LASER_TICKS = 22, LIGHT_TICKS = 8;
    private static final float BEAM_WIDTH = 1.6F, BEAM_DAMAGE = 16;
    private static final int HOLE_END = WAVE_START + WAVES * WAVE_EVERY + 20;
    // judgement
    private static final int GAZE_WARN = 30, GAZE_END = 150;
    private static final float GAZE_DAMAGE = 9, GAZE_RADIUS = 1.7F;
    // hands
    private static final int SLAMS = 6, SLAM_EVERY = 24, SLAM_WARN = 22;
    private static final float SLAM_DAMAGE = 20, SLAM_RADIUS = 3.2F, CLAP_DAMAGE = 28, CLAP_RADIUS = 5;
    // the second phase
    public static final int TRANSFORM_TICKS = 150, SHATTER_AT = 60, SHARD_COUNT = 8, SHARD_RING_AT = 115;
    private static final int SHARD_VOLLEYS = 6, SPIRAL_SHOTS = 24, SPIRAL_START = 140;
    private static final int ECLIPSE_TICKS = 190, EYE_LIFE = 46, EYE_FIRES = 26;
    private static final int GRAB_HITS = 10, GRAB_HOLD = 120;
    private static final float GRAB_RADIUS = 2.4F, CRUSH_DAMAGE = 40;
    private static final int INVERT_ROUND = 65, INVERT_WARN = 50;
    private static final float INVERT_DAMAGE = 30, SAFE_RADIUS = 3.0F;
    public static final int STAR_GROWN = 140, STAR_FALLS = 170, COLLAPSE_END = 195;
    private static final float STAR_DAMAGE = 45, SHIELD_WIDTH = 3.4F, SHIELD_HEIGHT = 4.5F;

    // client
    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState descendAnimation = new AnimationState();
    public final AnimationState summonAnimation = new AnimationState();
    public final AnimationState gazeAnimation = new AnimationState();
    public int descendSeenAt = -1000, awakeSeenAt = -1000, attackSeenAt = -1000, attackEndedAt = -1000;
    public boolean holeShown;
    public final AnimationState sealAnimation = new AnimationState();
    public final AnimationState screamAnimation = new AnimationState();
    public final AnimationState castAnimation = new AnimationState();
    public final AnimationState wrapAnimation = new AnimationState();
    public final AnimationState reachAnimation = new AnimationState();
    public final AnimationState holdAnimation = new AnimationState();
    public final AnimationState bleedAnimation = new AnimationState();
    /** Ticks since it began dying (both sides; its own count, so vanilla neither tips nor reddens it). */
    public int dying;
    public final AnimationState deathAnimation = new AnimationState();
    public int transformSeenAt = -1000, phaseSeenAt = -1000;
    /** 0..1: the shards in their ring over the room instead of round it (both sides, from the attack) */
    public float shardRing, shardRingBefore;
    public final Vec3[] handBefore = {Vec3.ZERO, Vec3.ZERO}, handNow = {Vec3.ZERO, Vec3.ZERO};

    // server
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
    private final BossTargeting targeting = new BossTargeting();
    private int descendTicks, actTick, cooldown = 50;
    private byte lastAttack = NONE;
    private double driftAngle;
    private final Set<UUID> lifted = new HashSet<>();
    private final Map<UUID, Double> floatPull = new HashMap<>();
    private final List<Shot> shots = new ArrayList<>();
    private final Map<UUID, Gaze> gazes = new HashMap<>();
    private final Vec3[] hand = new Vec3[2], handGoal = new Vec3[2];
    private final double[] handSpeed = {0.9, 0.9};
    private final float[] grip = new float[2], gripGoal = new float[2];
    private final boolean[] handBusy = new boolean[2];
    private Vec3 clapAt = Vec3.ZERO, clapSide = Vec3.ZERO;
    private final AurelionHand[] handParts = new AurelionHand[2];
    private int transformTicks, tries, grabHand, grabHits, grabbedAt;
    @Nullable private UUID grabbed;
    private Vec3 grabAt = Vec3.ZERO;
    private final List<Vec3> safeSpots = new ArrayList<>();
    private record Shield(Vec3 at, Vec3 normal) {}
    private final List<Shield> shields = new ArrayList<>();

    /** A beam of the black hole attack: a laser first, light from tick `fires`. */
    private record Shot(int id, Vec3 from, Vec3 to, int fires, Set<UUID> hit) {}

    /** Where a player's beam of judgement is on the ground. */
    private static final class Gaze {
        final int id = Beams.nextId();
        Vec3 at;
        int lastHit = -100;

        Gaze(Vec3 at) {
            this.at = at;
        }
    }

    public Aurelion(EntityType<? extends Aurelion> type, Level level) {
        super(type, level);
        for (int i = 0; i < 2; i++) handParts[i] = new AurelionHand(this, i);
        setId(ENTITY_COUNTER.getAndAdd(handParts.length + 1) + 1); // the hands take the ids right after it
        noPhysics = true;
        setNoGravity(true);
        setPersistenceRequired();
        this.xpReward = 1000;
        bossBar.setVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 800.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 20.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, DORMANT);
        builder.define(TARGET_ID, -1);
        builder.define(HOME, BlockPos.ZERO);
        builder.define(ATTACK, NONE);
        builder.define(HAND_L, new Vector3f());
        builder.define(HAND_R, new Vector3f());
        builder.define(GRIP_L, 0.0F);
        builder.define(GRIP_R, 0.0F);
        builder.define(PHASE, (byte) 1);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < handParts.length; i++) if (handParts[i] != null) handParts[i].setId(id + i + 1);
    }

    @Override
    public com.divinis.platform.entity.PartEntity<?>[] getDivinisParts() {
        return handParts;
    }

    /** Entering or leaving a level: its parts with it (PartRegistry). */
    @Override
    public void setLevelCallback(net.minecraft.world.level.entity.EntityInLevelCallback callback) {
        super.setLevelCallback(callback);
        com.divinis.platform.entity.PartRegistry.update(this, callback != net.minecraft.world.level.entity.EntityInLevelCallback.NULL);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    /** Where shard i is: round it, or (ring 1) in the ring over the room. time: the level's game time. */
    public Vec3 shardAt(int i, float time, float ring) {
        double a = time * 0.03 + i * Math.PI * 2 / SHARD_COUNT;
        Vec3 round = center().add(Math.cos(a) * 9, 2.5 + Math.sin(time * 0.07 + i) * 0.8, Math.sin(a) * 9);
        if (ring <= 0) return round;
        double b = i * Math.PI * 2 / SHARD_COUNT + time * 0.01;
        Vec3 high = home().add(Math.cos(b) * 14, HOVER + 17, Math.sin(b) * 14);
        return round.lerp(high, ring);
    }

    /** Where the star of its collapse hangs. */
    public Vec3 starAt() {
        return home().add(0, HOVER + getBbHeight() / 2 + 9, 0);
    }

    public byte state() {
        return entityData.get(STATE);
    }

    private void setState(byte s) {
        entityData.set(STATE, s);
    }

    public byte attack() {
        return entityData.get(ATTACK);
    }

    public Vec3 home() {
        return Vec3.atBottomCenterOf(entityData.get(HOME));
    }

    public boolean hasHome() {
        return !entityData.get(HOME).equals(BlockPos.ZERO);
    }

    public Vec3 center() {
        return position().add(0, getBbHeight() / 2, 0);
    }

    public Vec3 holeAt() {
        return home().add(0, HOVER + getBbHeight() / 2 + HOLE_ABOVE, 0);
    }

    public float grip(int i) {
        return entityData.get(i == 0 ? GRIP_L : GRIP_R);
    }

    // ------------------------------------------------------------------ every tick, both sides

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && isDeadOrDying()) deathAnimation.startIfStopped(tickCount);
        if (level().isClientSide) {
            for (int i = 0; i < 2; i++) {
                Vector3f v = entityData.get(i == 0 ? HAND_L : HAND_R);
                handBefore[i] = handNow[i].equals(Vec3.ZERO) ? new Vec3(v) : handNow[i];
                handNow[i] = new Vec3(v);
            }
            if (state() != DORMANT) idleAnimation.startIfStopped(tickCount);
            if (state() == DESCEND) descendAnimation.startIfStopped(tickCount);
            else descendAnimation.stop();
            if (phase() == 2) bleedAnimation.startIfStopped(tickCount);
            if (attack() != COLLAPSE) holdAnimation.stop();
        }
        // the shards' ring (both sides alike, from the attack's time)
        shardRingBefore = shardRing;
        int since = level().isClientSide ? tickCount - attackSeenAt : actTick;
        float ringGoal = attack() == SHARDS && since >= SHARD_RING_AT ? 1 : 0;
        shardRing += Mth.clamp(ringGoal - shardRing, -0.05F, 0.05F);
        // the hands can be hit where they are
        for (int i = 0; i < 2; i++) {
            Vec3 at = level().isClientSide ? handNow[i] : hand[i];
            if (at == null || at.equals(Vec3.ZERO)) continue;
            AurelionHand h = handParts[i];
            h.xo = h.getX();
            h.yo = h.getY();
            h.zo = h.getZ();
            h.setPos(at.x, at.y - 1.0, at.z);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EVENT_SUMMON -> summonAnimation.start(tickCount);
            case EVENT_GAZE -> gazeAnimation.start(tickCount);
            case EVENT_SEAL -> sealAnimation.start(tickCount);
            case EVENT_SCREAM -> screamAnimation.start(tickCount);
            case EVENT_CAST -> castAnimation.start(tickCount);
            case EVENT_WRAP -> wrapAnimation.start(tickCount);
            case EVENT_REACH -> reachAnimation.start(tickCount);
            case EVENT_HOLD -> holdAnimation.start(tickCount);
            case EVENT_HOLD_END -> holdAnimation.stop();
            default -> super.handleEntityEvent(id);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!level().isClientSide) return;
        if (STATE.equals(key)) {
            if (state() == DESCEND) descendSeenAt = tickCount;
            if (state() == AWAKE) awakeSeenAt = tickCount;
            if (state() == TRANSFORM) transformSeenAt = tickCount;
        }
        if (PHASE.equals(key) && phase() == 2) phaseSeenAt = tickCount;
        if (ATTACK.equals(key)) {
            if (attack() != NONE) attackSeenAt = tickCount;
            else attackEndedAt = tickCount;
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(64); // the wings, the hands, the tear in the sky, the black hole
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    public boolean isPickable() {
        return state() == AWAKE && super.isPickable();
    }

    // ------------------------------------------------------------------ server

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (!hasHome()) {
            entityData.set(HOME, blockPosition());
            setPos(home().add(0, HOVER, 0));
        }
        setDeltaMovement(Vec3.ZERO);
        if (tickCount % 10 == 0) updateBossBar(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        switch (state()) {
            case DORMANT -> {
                setPos(home().add(0, CRACK_HEIGHT, 0));
                if (tickCount % 10 == 0 && nearestPlayer(WAKE_RANGE) != null) startDescent(level);
            }
            case DESCEND -> descend(level);
            case TRANSFORM -> transformTick(level);
            default -> {
                fight(level);
                // the torn sky: stars and its eye over the room, all through the second phase
                if (phase() == 2 && tickCount % 200 == 0) screen(ScreenEffect.SKY, 230, 1.0F, -1);
            }
        }
        holdGrabbed(level);
        moveHands();
    }

    private void startDescent(ServerLevel level) {
        setState(DESCEND);
        descendTicks = 0;
        playSound(SoundEvents.AMBIENT_CAVE.value(), 5.0F, 0.5F);
        for (Player p : fighters(LIFT_RANGE + 16)) {
            if (p instanceof ServerPlayer sp) {
                ScreenFx.play(sp, ScreenEffect.SHAKE, DESCEND_TICKS, 0.35F);
                ScreenFx.play(sp, ScreenEffect.VIGNETTE, DESCEND_TICKS, 0.55F, 0xE8C060, "");
            }
        }
    }

    /** The sky tears open, light pours out, it comes down; then its name. */
    private void descend(ServerLevel level) {
        int t = descendTicks++;
        double k = Mth.clamp((t - 40) / 90.0, 0, 1);
        k = k * k * (3 - 2 * k);
        setPos(home().add(0, Mth.lerp(k, CRACK_HEIGHT, HOVER), 0));
        setYRot(getYRot() + 0.5F);
        if (t == 10) {
            playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 6.0F, 0.5F);
            playSound(SoundEvents.GLASS_BREAK, 6.0F, 0.4F);
            screen(ScreenEffect.FLASH, 14, 0.7F, 0xFFF4D0);
        }
        if (t == 20 || t == 60 || t == 100) playSound(SoundEvents.BEACON_ACTIVATE, 6.0F, 0.5F);
        if (t == 135) {
            playSound(SoundEvents.BELL_RESONATE, 6.0F, 0.6F);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 5.0F, 1.4F);
            screen(ScreenEffect.FLASH, 26, 1.0F, 0xFFF8E0);
            for (Player p : fighters(LIFT_RANGE + 16)) {
                if (p instanceof ServerPlayer sp) {
                    ScreenFx.playOn(sp, this, Anchor.EXCEPT, 0, ScreenEffect.INVERT, 8, 1.0F, -1);
                    ScreenFx.play(sp, ScreenEffect.TEXT, 90, 1.0F, 0xF2C46A, "Aurelion, Who Was Before Light");
                    ScreenFx.playOn(sp, this, Anchor.ENTITY, 0, ScreenEffect.OUTLINE, 60, 1.0F, 0xFFE8A0);
                }
            }
        }
        if (t >= DESCEND_TICKS) {
            setState(AWAKE);
            cooldown = 40;
        }
    }

    private void screen(ScreenEffect effect, int ticks, float strength, int color) {
        for (Player p : fighters(LIFT_RANGE + 16)) {
            if (p instanceof ServerPlayer sp) ScreenFx.play(sp, effect, ticks, strength, color, "");
        }
    }

    private void screenOn(Anchor anchor, ScreenEffect effect, int ticks, float strength, int color) {
        for (Player p : fighters(LIFT_RANGE + 16)) {
            if (p instanceof ServerPlayer sp) ScreenFx.playOn(sp, this, anchor, 0, effect, ticks, strength, color);
        }
    }

    private void updateBossBar(ServerLevel level) {
        for (ServerPlayer p : List.copyOf(bossBar.getPlayers())) {
            if (state() != AWAKE || !p.isAlive() || p.level() != level || p.distanceToSqr(this) > 90 * 90) bossBar.removePlayer(p);
        }
        if (state() != AWAKE) return;
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(this) < 72 * 72) bossBar.addPlayer(p);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            bossBar.removeAllPlayers();
            Telegraphs.cancel(this);
            letDown();
            release(false);
        }
        super.remove(reason);
    }

    /** Dying (7 s): its eyes roll back, its wings fold round it, rays of light tear out of it ever more, it shrinks into a point and bursts in a supernova. */
    public static final int DEATH_TICKS = 140;

    @Override
    protected void tickDeath() {
        dying++;
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 c = getBoundingBox().getCenter();
        int t = dying;
        if (t == 1) {
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, 5.0F, 0.4F);
            BossDeath.on(this, com.divinis.screenfx.ScreenEffect.Anchor.EXCEPT, 0, com.divinis.screenfx.ScreenEffect.DESATURATE, DEATH_TICKS, 1.0F, -1);
            BossDeath.on(this, com.divinis.screenfx.ScreenEffect.Anchor.ENTITY, 0, com.divinis.screenfx.ScreenEffect.AURA, DEATH_TICKS, 1.0F, 0xFFE8A0);
        }
        if (t % 6 == 0 && t < DEATH_TICKS - 20) {   // rays of light tearing out of it, more and more
            int rays = 1 + t / 30;
            for (int i = 0; i < rays; i++) {
                Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian()).normalize();
                Beams.send(level, Beams.nextId(), com.divinis.net.BeamPayload.LIGHT, c, c.add(d.scale(40)), 0.6F + t / 140.0F, 12);
            }
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 0.6F + t * 0.008F);
            BossDeath.shake(this, 6, 0.2F + t * 0.004F);
        }
        if (t % 4 == 0) level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, c.x, c.y, c.z, 6, 2.5, 2.5, 2.5, 0.05);
        if (t == 60) BossDeath.screen(this, com.divinis.screenfx.ScreenEffect.BLUR, DEATH_TICKS - 60, 0.6F, -1);
        if (t == 90) BossDeath.screen(this, com.divinis.screenfx.ScreenEffect.ABERRATION, DEATH_TICKS - 90, 0.8F, -1);
        if (t >= 100 && t < DEATH_TICKS) BossDeath.implode(level, net.minecraft.core.particles.ParticleTypes.END_ROD, c, 8, 8, 0.4);
        if (t == DEATH_TICKS - 8) {
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, 5.0F, 0.5F);
            BossDeath.screen(this, com.divinis.screenfx.ScreenEffect.FLASH, 40, 1.0F, 0xFFFFFF);
        }
        if (t == DEATH_TICKS) {   // a supernova
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, 5.0F, 0.6F);
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.5F);
            BossDeath.shake(this, 40, 1.0F);
            BossDeath.sphere(level, net.minecraft.core.particles.ParticleTypes.END_ROD, c, 200, 1.2);
            BossDeath.sphere(level, net.minecraft.core.particles.ParticleTypes.FIREWORK, c, 120, 0.9);
            BossDeath.ring(level, net.minecraft.core.particles.ParticleTypes.END_ROD, c, 2, 80, 1.0);
        }
        if (t >= DEATH_TICKS) remove(RemovalReason.KILLED);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            Telegraphs.cancel(this);
            letDown();
            release(false);
        }
        super.die(source);
    }

    @Nullable
    private Player nearestPlayer(double range) {
        Player best = null;
        double bestD = range * range;
        for (Player p : level().players()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            double d = p.distanceToSqr(home());
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private List<Player> fighters(double range) {
        List<Player> out = new ArrayList<>();
        for (Player p : level().players()) {
            if (!p.isCreative() && !p.isSpectator() && p.isAlive() && p.distanceToSqr(home()) < range * range) out.add(p);
        }
        return out;
    }

    private float damageScale() {
        var attr = getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null || attr.getBaseValue() <= 0 ? 1.0F : (float) (attr.getValue() / attr.getBaseValue());
    }

    private Vec3 onGround(Vec3 at) {
        BlockPos.MutableBlockPos p = BlockPos.containing(at.x, at.y + 2, at.z).mutable();
        for (int i = 0; i < 24; i++) {
            BlockState s = level().getBlockState(p);
            if (!s.getCollisionShape(level(), p).isEmpty()) return new Vec3(at.x, p.getY() + s.getCollisionShape(level(), p).max(Direction.Axis.Y), at.z);
            p.move(0, -1, 0);
        }
        return at;
    }

    private void face(Vec3 at) {
        Vec3 d = at.subtract(center());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float now = Mth.approachDegrees(getYRot(), yaw, 4.0F);
        setYRot(now);
        setYHeadRot(now);
        setYBodyRot(now);
    }

    // ------------------------------------------------------------------ the fight

    private void fight(ServerLevel level) {
        if (attack() == NONE || getTarget() == null || !getTarget().isAlive()) setTarget(targeting.pick(this, home(), AGGRO, getTarget()));
        LivingEntity target = getTarget();
        actTick++;
        switch (attack()) {
            case HOLE -> blackHole(level);
            case GAZE -> judgement(level);
            case HANDS -> deadHands(level, target);
            case SHARDS -> sunShards(level);
            case ECLIPSE -> eclipse(level);
            case GRAB -> twoHands(level, target);
            case INVERTED -> invertedLight(level);
            case COLLAPSE -> starCollapse(level);
            default -> {
                driftAngle += 0.012;
                Vec3 goal = home().add(Math.cos(driftAngle) * 4, HOVER + Math.sin(tickCount * 0.05) * 0.6, Math.sin(driftAngle) * 4);
                setPos(position().add(goal.subtract(position()).scale(0.05)));
                if (target != null) face(target.position());
                if (target == null || --cooldown > 0) break;
                byte next;
                do {
                    float r = random.nextFloat();
                    if (phase() == 2 && random.nextFloat() < 0.72F) {
                        next = r < 0.2F ? SHARDS : r < 0.4F ? ECLIPSE : r < 0.6F ? GRAB : r < 0.8F ? INVERTED : COLLAPSE;
                    } else {
                        next = r < 0.34F ? HOLE : r < 0.67F ? GAZE : HANDS;
                    }
                } while (next == lastAttack);
                begin(next);
            }
        }
        tickShots(level);
    }

    private void begin(byte a) {
        entityData.set(ATTACK, a);
        lastAttack = a;
        actTick = 0;
    }

    private void done() {
        entityData.set(ATTACK, NONE);
        actTick = 0;
        cooldown = phase() == 2 ? 30 + random.nextInt(20) : 50 + random.nextInt(30);
    }

    // ---- black hole: everyone floats, beams of light from every side

    private void blackHole(ServerLevel level) {
        int t = actTick - 1;
        Vec3 c = home().add(0, HOVER, 0);
        setPos(position().add(c.subtract(position()).scale(0.08)));
        if (t == 0) {
            level.broadcastEntityEvent(this, EVENT_SUMMON);
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 5.0F, 0.5F);
            for (Player p : fighters(LIFT_RANGE + 16)) {
                if (p instanceof ServerPlayer sp) {
                    ScreenFx.playOn(sp, this, Anchor.AREA, 18, ScreenEffect.DISTORT, HOLE_END, 0.8F, -1);
                    ScreenFx.play(sp, ScreenEffect.ABERRATION, HOLE_END, 0.3F);
                    ScreenFx.play(sp, ScreenEffect.SHAKE, 40, 0.4F);
                }
            }
        }
        if (t == HOLE_FORM) {
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 5.0F, 0.4F);
            for (Player p : fighters(LIFT_RANGE)) {
                lifted.add(p.getUUID());
                setFloat(p, -1.3);
            }
        }
        if (t > HOLE_FORM && t < HOLE_END) {
            // keep the lifted floating in a band of height: a little gravity either way (none at all would let them
            // drift on and on), gentle so it feels like floating
            double low = home().y + 5, high = home().y + 10;
            for (Player p : level.players()) {
                if (!lifted.contains(p.getUUID())) continue;
                if (!p.isAlive() || p.isSpectator()) {
                    clearFloat(p);
                    continue;
                }
                setFloat(p, p.getY() < low ? -1.15 : p.getY() > high ? -0.85 : p.getDeltaMovement().y > 0 ? -0.95 : -1.05);
                p.resetFallDistance();
            }
            if (t % 20 == 0) playSound(SoundEvents.PORTAL_AMBIENT, 4.0F, 0.3F);
        }
        if (t >= WAVE_START && t < WAVE_START + WAVES * WAVE_EVERY && (t - WAVE_START) % WAVE_EVERY == 0) {
            int wave = (t - WAVE_START) / WAVE_EVERY;
            List<Player> targets = new ArrayList<>();
            for (Player p : level.players()) if (lifted.contains(p.getUUID()) && p.isAlive()) targets.add(p);
            int count = 3 + wave / 2 + targets.size();
            for (int i = 0; i < count; i++) shoot(level, targets.isEmpty() ? null : targets.get(random.nextInt(targets.size())));
            playSound(SoundEvents.BEACON_POWER_SELECT, 4.0F, 1.6F);
        }
        if (t == HOLE_END) {
            letDown();
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 5.0F, 0.7F);
            screen(ScreenEffect.FLASH, 10, 0.8F, 0xFFFFFF);
            screenOn(Anchor.EXCEPT, ScreenEffect.INVERT, 6, 1.0F, -1);
            done();
        }
    }

    /** One beam through the room: near a floating player (or anywhere), along, up or slanting. */
    private void shoot(ServerLevel level, @Nullable Player near) {
        Vec3 at = near != null ? near.position().add(0, 0.9, 0) : holeAt().add(random.nextGaussian() * 10, -6, random.nextGaussian() * 10);
        at = at.add(random.nextGaussian() * 0.8, random.nextGaussian() * 0.6, random.nextGaussian() * 0.8);
        Vec3 from, to;
        float r = random.nextFloat();
        if (r < 0.3F) {
            // from the floor up to the sky (or down from above)
            from = new Vec3(at.x, home().y, at.z);
            to = new Vec3(at.x, home().y + 32, at.z);
        } else {
            double yaw = random.nextDouble() * Math.PI * 2;
            double up = r < 0.65F ? 0 : (random.nextBoolean() ? 1 : -1) * (0.35 + random.nextDouble() * 0.5);
            Vec3 d = new Vec3(Math.cos(yaw), up, Math.sin(yaw)).normalize();
            from = at.subtract(d.scale(40));
            to = at.add(d.scale(40));
        }
        addShot(level, from, to, LASER_TICKS);
    }

    /** A beam: a red laser for `warn` ticks, then light. */
    private void addShot(ServerLevel level, Vec3 from, Vec3 to, int warn) {
        int id = Beams.nextId();
        Beams.send(level, id, BeamPayload.LASER, from, to, 0.12F, warn);
        shots.add(new Shot(id, from, to, tickCount + warn, new HashSet<>()));
    }

    private void tickShots(ServerLevel level) {
        if (shots.isEmpty()) return;
        shots.removeIf(s -> tickCount > s.fires() + LIGHT_TICKS);
        for (Shot s : shots) {
            if (tickCount == s.fires()) {
                Beams.send(level, s.id(), BeamPayload.LIGHT, s.from(), s.to(), BEAM_WIDTH, LIGHT_TICKS);
                Vec3 mid = s.from().add(s.to()).scale(0.5);
                level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.BEACON_DEACTIVATE, getSoundSource(), 2.0F, 1.8F);
            }
            if (tickCount < s.fires() || tickCount > s.fires() + 5) continue;
            for (Player p : fighters(LIFT_RANGE + 16)) {
                if (s.hit().contains(p.getUUID()) || !Beams.hits(p, s.from(), s.to(), BEAM_WIDTH / 2)) continue;
                s.hit().add(p.getUUID());
                if (p.hurt(damageSources().mobAttack(this), BEAM_DAMAGE * damageScale())) p.igniteForSeconds(2);
            }
        }
    }

    private void setFloat(Player p, double amount) {
        AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
        if (g == null) return;
        Double now = floatPull.get(p.getUUID());
        if (now != null && now == amount) return;
        floatPull.put(p.getUUID(), amount);
        g.addOrUpdateTransientModifier(new AttributeModifier(FLOAT_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private void clearFloat(Player p) {
        AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
        if (g != null) g.removeModifier(FLOAT_ID);
        floatPull.remove(p.getUUID());
        lifted.remove(p.getUUID());
    }

    /** Lets everyone it lifted down gently. */
    private void letDown() {
        if (lifted.isEmpty()) return;
        for (Player p : level().players()) {
            if (!lifted.contains(p.getUUID())) continue;
            AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
            if (g != null) g.removeModifier(FLOAT_ID);
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
            p.resetFallDistance();
        }
        lifted.clear();
        floatPull.clear();
    }

    // ---- judgement: a beam from its eyes follows each player

    private void judgement(ServerLevel level) {
        int t = actTick - 1;
        Vec3 eye = center().add(Vec3.directionFromRotation(0, getYRot()).scale(1.5));
        if (t == 0) {
            level.broadcastEntityEvent(this, EVENT_GAZE);
            playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 4.0F, 0.6F);
            gazes.clear();
            for (Player p : fighters(LIFT_RANGE)) {
                double a = random.nextDouble() * Math.PI * 2;
                gazes.put(p.getUUID(), new Gaze(onGround(p.position().add(Math.cos(a) * 7, 0, Math.sin(a) * 7))));
                if (p instanceof ServerPlayer sp) {
                    ScreenFx.playOn(sp, this, Anchor.EXCEPT, 0, ScreenEffect.DESATURATE, GAZE_END + 10, 1.0F, -1);
                    ScreenFx.playOn(sp, this, Anchor.ENTITY, 0, ScreenEffect.AURA, GAZE_END + 10, 1.0F, -1);
                }
            }
        }
        LivingEntity target = getTarget();
        if (target != null) face(target.position());
        for (Player p : level.players()) {
            Gaze g = gazes.get(p.getUUID());
            if (g == null) continue;
            if (!p.isAlive() || p.isSpectator()) {
                gazes.remove(p.getUUID());
                break;
            }
            double speed = t < GAZE_WARN ? 0.14 : 0.18 + 0.08 * Math.min(1, (t - GAZE_WARN) / 100.0);
            Vec3 to = p.position().subtract(g.at).multiply(1, 0, 1);
            if (to.length() > 1.0E-3) g.at = onGround(g.at.add(to.normalize().scale(Math.min(speed, to.length()))));
            boolean burning = t >= GAZE_WARN;
            Beams.send(level, g.id, burning ? BeamPayload.LIGHT : BeamPayload.LASER, eye, g.at.add(0, 0.1, 0), burning ? 1.3F : 0.12F, 3);
            if (burning && tickCount - g.lastHit >= 10 && p.position().subtract(g.at).horizontalDistance() < GAZE_RADIUS
                    && Math.abs(p.getY() - g.at.y) < 3) {
                g.lastHit = tickCount;
                if (p.hurt(damageSources().mobAttack(this), GAZE_DAMAGE * damageScale())) p.igniteForSeconds(3);
            }
            if (burning && t % 3 == 0) level.sendParticles(ParticleTypes.END_ROD, g.at.x, g.at.y + 0.2, g.at.z, 3, 0.3, 0.1, 0.3, 0.05);
        }
        if (t == GAZE_WARN) playSound(SoundEvents.BEACON_ACTIVATE, 5.0F, 1.5F);
        if (t >= GAZE_END) {
            gazes.clear();
            done();
        }
    }

    // ---- the dead hands: slams on marked circles, then a clap

    private Vec3 handRest(int i) {
        double side = i == 0 ? 1 : -1;
        Vec3 off = new Vec3(side * 5.5, -1.2 + Math.sin(tickCount * 0.08 + i * 2) * 0.5, 1.5)
                .yRot(-getYRot() * Mth.DEG_TO_RAD);
        return center().add(off);
    }

    private void deadHands(ServerLevel level, @Nullable LivingEntity target) {
        int t = actTick - 1;
        if (target != null) face(target.position());
        if (t < SLAMS * SLAM_EVERY) {
            int k = t / SLAM_EVERY, local = t % SLAM_EVERY, h = k % 2;
            if (local == 0) {
                List<Player> ps = fighters(LIFT_RANGE);
                LivingEntity who = k == 0 && target != null ? target : ps.isEmpty() ? target : ps.get(random.nextInt(ps.size()));
                if (who == null) {
                    handBusy[0] = handBusy[1] = false;
                    done();
                    return;
                }
                Vec3 at = onGround(who.position());
                clapAt = at;
                handBusy[h] = true;
                handGoal[h] = at.add(0, 7, 0);
                handSpeed[h] = 1.4;
                gripGoal[h] = 0;
                playSound(SoundEvents.PHANTOM_FLAP, 3.0F, 0.5F);
                Telegraphs.show(level, Telegraph.circle(at, SLAM_RADIUS), SLAM_WARN, 4, this, e -> {
                    if (e.hurt(damageSources().mobAttack(this), SLAM_DAMAGE * damageScale())) {
                        e.setDeltaMovement(e.getDeltaMovement().add(0, 0.7, 0));
                        e.hurtMarked = true;
                    }
                });
            }
            if (local == SLAM_WARN - 4) {
                handGoal[h] = clapAt.add(0, 0.8, 0);
                handSpeed[h] = 2.6;
                gripGoal[h] = 0.25F;
            }
            if (local == SLAM_WARN) {
                playSound(SoundEvents.ANVIL_LAND, 4.0F, 0.5F);
                level.playSound(null, clapAt.x, clapAt.y, clapAt.z, SoundEvents.GENERIC_EXPLODE.value(), getSoundSource(), 3.0F, 0.7F);
                level.sendParticles(ParticleTypes.EXPLOSION, clapAt.x, clapAt.y + 0.5, clapAt.z, 3, 1.2, 0.2, 1.2, 0);
                screenOn(Anchor.EXCEPT, ScreenEffect.INVERT, 5, 1.0F, -1);
                for (Player p : fighters(LIFT_RANGE + 16)) {
                    if (p instanceof ServerPlayer sp && p.distanceToSqr(clapAt) < 24 * 24) ScreenFx.play(sp, ScreenEffect.SHAKE, 10, 0.6F);
                }
            }
            if (local == SLAM_WARN + 1) {
                handBusy[h] = false;
                handSpeed[h] = 0.9;
            }
            return;
        }
        int c = t - SLAMS * SLAM_EVERY;
        if (c == 0) {
            Player who = target instanceof Player tp ? tp : null;
            if (who == null) {
                done();
                return;
            }
            clapAt = onGround(who.position());
            double yaw = random.nextDouble() * Math.PI * 2;
            clapSide = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            for (int i = 0; i < 2; i++) {
                handBusy[i] = true;
                handGoal[i] = clapAt.add(clapSide.scale(i == 0 ? 8 : -8)).add(0, 1.5, 0);
                handSpeed[i] = 1.6;
                gripGoal[i] = 0;
            }
            playSound(SoundEvents.ELDER_GUARDIAN_AMBIENT, 4.0F, 0.5F);
            Telegraphs.show(level, Telegraph.circle(clapAt, CLAP_RADIUS), 30, 6, this, e -> {
                if (e.hurt(damageSources().mobAttack(this), CLAP_DAMAGE * damageScale())) {
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.9, 0));
                    e.hurtMarked = true;
                }
            });
        }
        if (c == 24) {
            for (int i = 0; i < 2; i++) {
                handGoal[i] = clapAt.add(clapSide.scale(i == 0 ? 1.0 : -1.0)).add(0, 1.5, 0);
                handSpeed[i] = 2.8;
                gripGoal[i] = 0.15F;
            }
        }
        if (c == 30) {
            playSound(SoundEvents.ANVIL_LAND, 5.0F, 0.4F);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.6F);
            screen(ScreenEffect.FLASH, 8, 0.8F, 0xFFFFFF);
            screenOn(Anchor.EXCEPT, ScreenEffect.INVERT, 6, 1.0F, -1);
            for (Player p : fighters(LIFT_RANGE + 16)) if (p instanceof ServerPlayer sp) ScreenFx.play(sp, ScreenEffect.SHAKE, 14, 0.8F);
        }
        if (c == 42) {
            handBusy[0] = handBusy[1] = false;
            handSpeed[0] = handSpeed[1] = 0.9;
            done();
        }
    }

    // ------------------------------------------------------------------ the veil torn: the second phase

    private void startTransform(ServerLevel level) {
        Telegraphs.cancel(this);
        letDown();
        release(false);
        shots.clear();
        gazes.clear();
        handBusy[0] = handBusy[1] = false;
        entityData.set(ATTACK, NONE);
        setState(TRANSFORM);
        transformTicks = 0;
        level.broadcastEntityEvent(this, EVENT_SEAL);
        playSound(SoundEvents.BEACON_DEACTIVATE, 5.0F, 0.3F);
        playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 5.0F, 0.4F);
        screenOn(Anchor.EXCEPT, ScreenEffect.DESATURATE, TRANSFORM_TICKS, 1.0F, -1);
        screen(ScreenEffect.NOISE, TRANSFORM_TICKS, 0.3F, -1);
        screen(ScreenEffect.GLITCH, SHATTER_AT, 0.4F, -1);
        screen(ScreenEffect.SHAKE, SHATTER_AT, 0.3F, -1);
    }

    /** Its eyes roll back, the symbol cracks - and shatters; it screams, healing, and the sky tears wide. */
    private void transformTick(ServerLevel level) {
        int t = transformTicks++;
        Vec3 c = home().add(0, HOVER + 1.5, 0);
        setPos(position().add(c.subtract(position()).scale(0.06)));
        if (t == 20 || t == 38 || t == 52) {
            playSound(SoundEvents.GLASS_BREAK, 4.0F, 0.5F + t * 0.005F);
            playSound(SoundEvents.ANVIL_LAND, 3.0F, 0.4F);
            ScreenFx.shake(level, center(), 64, 8, 0.4F + t * 0.006F);
        }
        if (t == SHATTER_AT) {
            entityData.set(PHASE, (byte) 2);
            bossBar.setColor(BossEvent.BossBarColor.RED);
            level.broadcastEntityEvent(this, EVENT_SCREAM);
            playSound(SoundEvents.GLASS_BREAK, 6.0F, 0.3F);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 5.0F, 0.5F);
            playSound(SoundEvents.WARDEN_ROAR, 6.0F, 0.6F);
            screen(ScreenEffect.FLASH, 26, 1.0F, 0xFFFFFF);
            screenOn(Anchor.EXCEPT, ScreenEffect.INVERT, 10, 1.0F, -1);
            int scream = TRANSFORM_TICKS - SHATTER_AT;
            screen(ScreenEffect.SHAKE, scream, 0.9F, -1);
            screen(ScreenEffect.ABERRATION, scream, 0.8F, -1);
            screen(ScreenEffect.VIGNETTE, scream, 0.6F, 0x8A0000);
            screen(ScreenEffect.SKY, scream + 200, 1.0F, -1);
            screenOn(Anchor.AREA, ScreenEffect.DISTORT, scream, 1.2F, -1);
            screenOn(Anchor.ENTITY, ScreenEffect.AURA, scream, 1.3F, -1);
            screenOn(Anchor.ENTITY, ScreenEffect.OUTLINE, scream, 1.2F, 0xC01010);
        }
        if (t > SHATTER_AT) {
            // screaming in rage, its health coming back
            float k = (t - SHATTER_AT) / (float) (TRANSFORM_TICKS - SHATTER_AT - 10);
            setHealth(Math.max(getHealth(), getMaxHealth() * Math.min(1.0F, 0.5F + 0.5F * k)));
            if ((t - SHATTER_AT) % 30 == 10) {
                playSound(SoundEvents.WARDEN_ROAR, 6.0F, 0.5F + random.nextFloat() * 0.2F);
                playSound(SoundEvents.GHAST_HURT, 5.0F, 0.3F);
            }
            if ((t - SHATTER_AT) % 30 == 25) playSound(SoundEvents.ENDER_DRAGON_GROWL, 5.0F, 0.6F);
            if (t % 5 == 0) level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + 3, getZ(), 6, 2, 2, 2, 0.2);
        }
        if (t >= TRANSFORM_TICKS) {
            setHealth(getMaxHealth());
            setState(AWAKE);
            cooldown = 30;
        }
    }

    // ---- shards of the sun

    private void sunShards(ServerLevel level) {
        int t = actTick - 1;
        float time = level.getGameTime();
        if (t == 0) {
            level.broadcastEntityEvent(this, EVENT_CAST);
            playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 5.0F, 0.5F);
        }
        if (t >= 10 && t < 10 + SHARD_VOLLEYS * 18 && (t - 10) % 18 == 0) {
            List<Player> ps = fighters(LIFT_RANGE);
            if (!ps.isEmpty()) {
                Player p = ps.get(random.nextInt(ps.size()));
                Vec3 aim = p.position().add(0, 1.0, 0);
                int best = 0;
                for (int i = 1; i < SHARD_COUNT; i++) {
                    if (shardAt(i, time, shardRing).distanceToSqr(aim) < shardAt(best, time, shardRing).distanceToSqr(aim)) best = i;
                }
                Vec3 from = shardAt(best, time, shardRing);
                Vec3 to = aim.add(aim.subtract(from).normalize().scale(30));
                addShot(level, from, to, 20);
                playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 4.0F, 0.6F);
            }
        }
        if (t == SHARD_RING_AT) {
            level.broadcastEntityEvent(this, EVENT_CAST);
            playSound(SoundEvents.BEACON_POWER_SELECT, 5.0F, 0.5F);
        }
        if (t >= SPIRAL_START && t < SPIRAL_START + SPIRAL_SHOTS * 3 && (t - SPIRAL_START) % 3 == 0) {
            int k = (t - SPIRAL_START) / 3;
            double r = 22 - k * (20.0 / SPIRAL_SHOTS), a = k * 0.9;
            Vec3 at = onGround(home().add(Math.cos(a) * r, 0, Math.sin(a) * r));
            Vec3 from = shardAt(k % SHARD_COUNT, time, shardRing);
            addShot(level, from, at.add(at.subtract(from).normalize().scale(2)), 16);
            Telegraphs.show(level, Telegraph.circle(at, 1.6F), 16, 4, this, null);
        }
        if (t >= SPIRAL_START + SPIRAL_SHOTS * 3 + 30) done();
    }

    // ---- eclipse

    private void eclipse(ServerLevel level) {
        int t = actTick - 1;
        if (t == 0) {
            level.broadcastEntityEvent(this, EVENT_WRAP);
            playSound(SoundEvents.WARDEN_HEARTBEAT, 6.0F, 0.5F);
            playSound(SoundEvents.AMBIENT_CAVE.value(), 6.0F, 0.4F);
            screen(ScreenEffect.DARKNESS, ECLIPSE_TICKS, 1.0F, -1);
            screenOn(Anchor.ENTITY, ScreenEffect.OUTLINE, ECLIPSE_TICKS, 0.8F, 0xE8B040);
        }
        if (t % 20 == 10) playSound(SoundEvents.WARDEN_HEARTBEAT, 4.0F, 0.6F);
        if (t >= 20 && t < ECLIPSE_TICKS - 40 && t % 12 == 0) {
            List<Player> ps = fighters(LIFT_RANGE);
            if (!ps.isEmpty()) {
                Player p = ps.get(random.nextInt(ps.size()));
                double a = random.nextDouble() * Math.PI * 2, r = 16 + random.nextDouble() * 8;
                Vec3 eye = home().add(Math.cos(a) * r, HOVER + 3 + random.nextDouble() * 9, Math.sin(a) * r);
                PacketDistributor.sendToPlayersNear(level, null, eye.x, eye.y, eye.z, 96,
                        new FireFxPayload(FireFxPayload.WORLD_EYE, eye.x, eye.y, eye.z, 5.0F, 0, 0, EYE_LIFE));
                Vec3 aim = p.position().add(0, 1.0, 0);
                addShot(level, eye, aim.add(aim.subtract(eye).normalize().scale(30)), EYE_FIRES);
                level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.ENDER_EYE_DEATH, getSoundSource(), 3.0F, 0.5F);
            }
        }
        if (t >= ECLIPSE_TICKS) done();
    }

    // ---- judgement of two hands

    private void twoHands(ServerLevel level, @Nullable LivingEntity target) {
        int t = actTick - 1;
        if (grabbed != null) return; // holding someone: holdGrabbed does it
        if (t == 0) {
            if (!(target instanceof Player)) {
                done();
                return;
            }
            grabHand = tries % 2;
            level.broadcastEntityEvent(this, EVENT_REACH);
            playSound(SoundEvents.ELDER_GUARDIAN_AMBIENT, 4.0F, 0.4F);
            handBusy[grabHand] = true;
            handSpeed[grabHand] = 1.6;
            gripGoal[grabHand] = 0;
        }
        if (target == null || t > 40) { // lost its target
            handBusy[grabHand] = false;
            done();
            return;
        }
        if (t < 18) handGoal[grabHand] = target.position().add(0, 4, 0);
        if (t == 18) {
            grabAt = onGround(target.position());
            Telegraphs.show(level, Telegraph.circle(grabAt, GRAB_RADIUS), 12, 4, this, null);
            handGoal[grabHand] = grabAt.add(0, 4, 0);
        }
        if (t == 26) {
            handGoal[grabHand] = grabAt.add(0, 1.4, 0);
            handSpeed[grabHand] = 3.0;
            gripGoal[grabHand] = 0.2F;
        }
        if (t == 30) {
            Player caught = null;
            for (Player p : fighters(LIFT_RANGE)) {
                if (p.position().subtract(grabAt).horizontalDistance() < GRAB_RADIUS + 0.3 && Math.abs(p.getY() - grabAt.y) < 2.5) {
                    caught = p;
                    break;
                }
            }
            if (caught != null) {
                grab(level, caught);
            } else {
                // missed: the hand slams the floor
                playSound(SoundEvents.ANVIL_LAND, 4.0F, 0.5F);
                ScreenFx.shake(level, grabAt, 20, 8, 0.6F);
                handBusy[grabHand] = false;
                handSpeed[grabHand] = 0.9;
                if (++tries < 2) actTick = 0;
                else done();
            }
        }
    }

    private void grab(ServerLevel level, Player p) {
        grabbed = p.getUUID();
        grabHits = 0;
        grabbedAt = tickCount;
        gripGoal[grabHand] = 1.0F;
        playSound(SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 4.0F, 0.4F);
        playSound(SoundEvents.WARDEN_ROAR, 4.0F, 1.2F);
        for (Player other : fighters(LIFT_RANGE + 16)) {
            if (!(other instanceof ServerPlayer sp)) continue;
            if (other == p) {
                ScreenFx.play(sp, ScreenEffect.VIGNETTE, GRAB_HOLD, 0.75F, 0x900000, "");
                ScreenFx.play(sp, ScreenEffect.SHAKE, GRAB_HOLD, 0.25F);
                ScreenFx.play(sp, ScreenEffect.DISTORT, GRAB_HOLD, 0.35F);
            } else {
                ScreenFx.playOn(sp, p, Anchor.EXCEPT, 0, ScreenEffect.DESATURATE, GRAB_HOLD, 1.0F, -1);
                ScreenFx.playOn(sp, p, Anchor.ENTITY, 0, ScreenEffect.OUTLINE, GRAB_HOLD, 1.0F, 0xFFD060);
            }
        }
    }

    /** Holds whoever its hand has caught up high; free at ten blows to the hand, crushed when time runs out. */
    private void holdGrabbed(ServerLevel level) {
        if (grabbed == null) return;
        Player p = level.getPlayerByUUID(grabbed);
        if (p == null || !p.isAlive() || attack() != GRAB) {
            release(false);
            return;
        }
        handGoal[grabHand] = home().add(0, HOVER + 11, 0).add(new Vec3(grabHand == 0 ? 4 : -4, 0, 0).yRot(-getYRot() * Mth.DEG_TO_RAD));
        handSpeed[grabHand] = 0.6;
        Vec3 at = hand[grabHand].add(0, -2.2, 0);
        if (p instanceof ServerPlayer sp) {
            sp.connection.teleport(at.x, at.y, at.z, 0, 0, java.util.EnumSet.of(net.minecraft.world.entity.RelativeMovement.X_ROT,
                    net.minecraft.world.entity.RelativeMovement.Y_ROT));
        }
        p.resetFallDistance();
        int held = tickCount - grabbedAt;
        if (held % 20 == 10) {
            p.hurt(damageSources().mobAttack(this), 2 * damageScale());
            playSound(SoundEvents.SKELETON_HURT, 2.0F, 0.4F);
        }
        if (grabHits >= GRAB_HITS) {
            playSound(SoundEvents.ITEM_BREAK, 4.0F, 0.5F);
            screen(ScreenEffect.FLASH, 8, 0.5F, 0xFFE8A0);
            release(true);
            handGoal[0] = handRest(0);
            actTick = 0;
            done();
        } else if (held >= GRAB_HOLD) {
            p.hurt(damageSources().mobAttack(this), CRUSH_DAMAGE * damageScale());
            playSound(SoundEvents.ANVIL_LAND, 4.0F, 0.3F);
            playSound(SoundEvents.PLAYER_BIG_FALL, 4.0F, 0.5F);
            if (p instanceof ServerPlayer sp) {
                ScreenFx.play(sp, ScreenEffect.FLASH, 14, 0.9F, 0x8A0000, "");
                ScreenFx.play(sp, ScreenEffect.SHAKE, 20, 1.2F);
            }
            release(true);
            done();
        }
    }

    /** Lets go of whoever it holds (gently, if `fall`), and clears what was on everyone's screens for it. */
    private void release(boolean fall) {
        if (grabbed == null) return;
        Player p = level().getPlayerByUUID(grabbed);
        grabbed = null;
        handBusy[grabHand] = false;
        handSpeed[grabHand] = 0.9;
        gripGoal[grabHand] = 0;
        if (p != null) {
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0));
            p.resetFallDistance();
        }
        for (Player other : fighters(LIFT_RANGE + 16)) {
            if (other instanceof ServerPlayer sp) {
                ScreenFx.clear(sp);
                if (phase() == 2) ScreenFx.play(sp, ScreenEffect.SKY, 230, 1.0F);
            }
        }
    }

    // ---- inverted light

    private void invertedLight(ServerLevel level) {
        int t = actTick - 1;
        if (t == 0) {
            level.broadcastEntityEvent(this, EVENT_REACH);
            playSound(SoundEvents.BEACON_DEACTIVATE, 5.0F, 0.4F);
            screenOn(Anchor.EXCEPT, ScreenEffect.INVERT, INVERT_ROUND * 2 + 5, 1.0F, -1);
        }
        int round = t / INVERT_ROUND, local = t % INVERT_ROUND;
        if (round < 2 && local == 0) {
            safeSpots.clear();
            for (Player p : fighters(LIFT_RANGE)) {
                double a = random.nextDouble() * Math.PI * 2, r = 5 + random.nextDouble() * 3;
                safeSpots.add(spotInRoom(p.position().add(Math.cos(a) * r, 0, Math.sin(a) * r)));
            }
            for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 18;
                safeSpots.add(spotInRoom(home().add(Math.cos(a) * r, 0, Math.sin(a) * r)));
            }
            for (Vec3 spot : safeSpots) Telegraphs.show(level, Telegraph.circle(spot, SAFE_RADIUS), INVERT_WARN, 4, this, null);
            playSound(SoundEvents.BELL_BLOCK, 5.0F, 0.5F);
        }
        if (round < 2 && local == INVERT_WARN) {
            // everything but the marks burns
            for (Player p : fighters(LIFT_RANGE)) {
                boolean safe = false;
                for (Vec3 spot : safeSpots) if (p.position().subtract(spot).horizontalDistance() <= SAFE_RADIUS + 0.2) safe = true;
                if (!safe && p.hurt(damageSources().mobAttack(this), INVERT_DAMAGE * damageScale())) p.igniteForSeconds(4);
            }
            for (int i = 0; i < 14; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * 24;
                Vec3 at = onGround(home().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                Beams.send(level, Beams.nextId(), BeamPayload.SHAFT, at.add(0, 30, 0), at, 3.0F + random.nextFloat() * 2, 10);
            }
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 5.0F, 0.6F);
            screen(ScreenEffect.FLASH, 8, 0.6F, 0xFFFFFF);
            ScreenFx.shake(level, home(), 64, 12, 0.7F);
        }
        if (t >= INVERT_ROUND * 2 + 5) done();
    }

    private Vec3 spotInRoom(Vec3 at) {
        Vec3 d = at.subtract(home()).multiply(1, 0, 1);
        if (d.length() > ROOM_R - 4) at = home().add(d.normalize().scale(ROOM_R - 4));
        return onGround(at);
    }

    // ---- star collapse

    private void starCollapse(ServerLevel level) {
        int t = actTick - 1;
        Vec3 c = home().add(0, HOVER, 0);
        setPos(position().add(c.subtract(position()).scale(0.08)));
        Vec3 star = starAt();
        if (t == 0) {
            level.broadcastEntityEvent(this, EVENT_HOLD);
            playSound(SoundEvents.BEACON_ACTIVATE, 6.0F, 0.4F);
            shields.clear();
            int n = Mth.clamp(fighters(LIFT_RANGE).size() + 2, 3, 6);
            double base = random.nextDouble() * Math.PI * 2;
            for (int i = 0; i < n; i++) {
                double a = base + i * Math.PI * 2 / n + random.nextDouble() * 0.4;
                Vec3 at = onGround(home().add(Math.cos(a) * 14, 0, Math.sin(a) * 14));
                Vec3 normal = home().subtract(at).multiply(1, 0, 1).normalize();
                shields.add(new Shield(at, normal));
            }
        }
        if (t >= 4 && t < 4 + shields.size() * 4 && (t - 4) % 4 == 0) {
            // each pane of light struck into the floor
            Shield sh = shields.get((t - 4) / 4);
            float yaw = (float) (Mth.atan2(sh.normal().z, sh.normal().x) * Mth.RAD_TO_DEG) - 90.0F;
            Beams.send(level, Beams.nextId(), BeamPayload.LIGHT, center(), sh.at().add(0, 2, 0), 1.0F, 6);
            PacketDistributor.sendToPlayersNear(level, null, sh.at().x, sh.at().y, sh.at().z, 96, new FireFxPayload(FireFxPayload.LIGHT_SHIELD,
                    sh.at().x, sh.at().y, sh.at().z, yaw, SHIELD_WIDTH, 0, COLLAPSE_END - t));
            level.playSound(null, sh.at().x, sh.at().y, sh.at().z, SoundEvents.ANVIL_PLACE, getSoundSource(), 3.0F, 0.6F);
            ScreenFx.shake(level, sh.at(), 16, 6, 0.4F);
        }
        if (t == 30) {
            screen(ScreenEffect.NOISE, STAR_FALLS - 30, 0.35F, -1);
            screen(ScreenEffect.BLUR, 60, 0.2F, -1);
            screen(ScreenEffect.ABERRATION, STAR_FALLS - 30, 0.3F, -1);
        }
        if (t == 90) {
            screen(ScreenEffect.BLUR, 50, 0.5F, -1);
            screen(ScreenEffect.SHAKE, STAR_FALLS - 90, 0.35F, -1);
        }
        if (t == STAR_GROWN) {
            screen(ScreenEffect.BLUR, STAR_FALLS - STAR_GROWN + 5, 0.9F, -1);
            screen(ScreenEffect.SHAKE, STAR_FALLS - STAR_GROWN, 0.8F, -1);
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 6.0F, 0.4F);
        }
        if (t % 20 == 0 && t < STAR_FALLS) playSound(SoundEvents.BEACON_AMBIENT, 6.0F, 0.5F + t / 300.0F);
        if (t > STAR_GROWN && t < STAR_FALLS && t % 3 == 0) {
            // the black hole pulls
            for (Player p : fighters(LIFT_RANGE)) {
                Vec3 to = star.subtract(p.position()).multiply(1, 0, 1);
                double d = to.length();
                if (d < 1) continue;
                p.setDeltaMovement(to.scale(0.16 / d));
                p.hurtMarked = true;
            }
        }
        if (t == STAR_FALLS) {
            level.broadcastEntityEvent(this, EVENT_HOLD_END);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 6.0F, 0.4F);
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 6.0F, 0.5F);
            screen(ScreenEffect.FLASH, 30, 1.0F, 0xFFF6E0);
            screenOn(Anchor.EXCEPT, ScreenEffect.INVERT, 8, 1.0F, -1);
            ScreenFx.shake(level, home(), 80, 30, 1.2F);
            for (Player p : fighters(LIFT_RANGE + 12)) {
                if (behindShield(star, p)) continue;
                if (p.hurt(damageSources().mobAttack(this), STAR_DAMAGE * damageScale())) {
                    p.igniteForSeconds(6);
                    Vec3 push = p.position().subtract(star).multiply(1, 0, 1).normalize().scale(1.4);
                    p.setDeltaMovement(p.getDeltaMovement().add(push.x, 0.7, push.z));
                    p.hurtMarked = true;
                }
            }
        }
        if (t >= COLLAPSE_END) {
            shields.clear();
            done();
        }
    }

    /** Whether a pane of light stands between the star and a player. */
    private boolean behindShield(Vec3 star, Player p) {
        Vec3 eye = p.getEyePosition();
        Vec3 d = eye.subtract(star);
        for (Shield sh : shields) {
            double denom = d.dot(sh.normal());
            if (Math.abs(denom) < 1.0E-6) continue;
            double k = sh.at().subtract(star).dot(sh.normal()) / denom;
            if (k <= 0 || k >= 1) continue;
            Vec3 hit = star.add(d.scale(k));
            Vec3 along = new Vec3(-sh.normal().z, 0, sh.normal().x);
            if (Math.abs(hit.subtract(sh.at()).dot(along)) <= SHIELD_WIDTH / 2 + 0.3 && hit.y >= sh.at().y - 0.2
                    && hit.y <= sh.at().y + SHIELD_HEIGHT + 0.3) return true;
        }
        return false;
    }

    /** The hands: at rest beside it, or where an attack sends them; synced to the clients. */
    private void moveHands() {
        for (int i = 0; i < 2; i++) {
            if (hand[i] == null) hand[i] = handRest(i);
            Vec3 goal = handBusy[i] && handGoal[i] != null ? handGoal[i] : handRest(i);
            if (!handBusy[i] && attack() == HOLE) goal = center().add(new Vec3(i == 0 ? 7 : -7, 3, 0).yRot(-getYRot() * Mth.DEG_TO_RAD));
            Vec3 d = goal.subtract(hand[i]);
            double len = d.length();
            double speed = handBusy[i] ? handSpeed[i] : Math.max(0.15, Math.min(1.2, len * 0.15));
            hand[i] = len <= speed ? goal : hand[i].add(d.scale(speed / len));
            grip[i] += Mth.clamp(gripGoal[i] - grip[i], -0.12F, 0.12F);
            if (!handBusy[i]) gripGoal[i] = attack() == HOLE ? 0.0F : 0.35F + 0.1F * Mth.sin(tickCount * 0.05F + i);
            entityData.set(i == 0 ? HAND_L : HAND_R, new Vector3f((float) hand[i].x, (float) hand[i].y, (float) hand[i].z));
            entityData.set(i == 0 ? GRIP_L : GRIP_R, grip[i]);
        }
    }

    // ------------------------------------------------------------------ damage, sounds, saving

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (!level().isClientSide) entityData.set(TARGET_ID, target == null ? -1 : target.getId());
    }

    @Override
    public int markedTargetId() {
        return state() != AWAKE || !isAlive() ? -1 : entityData.get(TARGET_ID);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (state() != AWAKE) return false;
        boolean hurt = super.hurt(source, amount);
        if (hurt && level() instanceof ServerLevel level) {
            targeting.onHurt(source, level.getGameTime());
            if (phase() == 1 && isAlive() && getHealth() <= getMaxHealth() * 0.5F) startTransform(level);
        }
        return hurt;
    }

    /** A hand hit: while it holds someone, a blow towards freeing them; otherwise a little of it reaches Aurelion. */
    boolean hurtHand(AurelionHand part, DamageSource source, float amount) {
        if (state() != AWAKE) return false;
        if (grabbed != null && part.index == grabHand && source.getEntity() instanceof Player) {
            grabHits++;
            playSound(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 2.0F, 0.6F);
            if (level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT, part.getX(), part.getY() + 1, part.getZ(), 8, 0.5, 0.5, 0.5, 0.2);
                Component progress = Component.literal("The hand: " + Math.min(grabHits, GRAB_HITS) + " / " + GRAB_HITS)
                        .withStyle(net.minecraft.ChatFormatting.GOLD);
                for (Player p : fighters(LIFT_RANGE + 16)) p.displayClientMessage(progress, true);
            }
            return true;
        }
        return hurt(source, amount * 0.4F);
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
    public boolean isInWall() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return state() == AWAKE ? SoundEvents.BEACON_AMBIENT : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_CLUSTER_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Awake", state() != DORMANT);
        tag.putByte("Phase", (byte) phase());
        BlockPos h = entityData.get(HOME);
        tag.putIntArray("Home", new int[] {h.getX(), h.getY(), h.getZ()});
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setState(tag.getBoolean("Awake") ? AWAKE : DORMANT);
        if (tag.getByte("Phase") == 2) {
            entityData.set(PHASE, (byte) 2);
            bossBar.setColor(BossEvent.BossBarColor.RED);
        }
        int[] h = tag.getIntArray("Home");
        if (h.length == 3) entityData.set(HOME, new BlockPos(h[0], h[1], h[2]));
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }
}
