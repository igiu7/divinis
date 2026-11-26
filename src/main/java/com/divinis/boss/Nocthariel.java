package com.divinis.boss;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.divinis.net.BeamPayload;
import com.divinis.net.DrawPayloads;
import com.divinis.net.FireFxPayload;
import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenEffect.Anchor;
import com.divinis.screenfx.ScreenFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Nocthariel, Who Devoured the Dawn (/summon divinis:nocthariel on the floor in the middle of its room): pure black,
 * no face - a ring of mouths round its head - long clawed arms and black wings; a hole in the world.
 *
 * It waits under the floor. When a player comes near, darkness falls, a mouth opens in the floor and it rises out of
 * it, screaming with every mouth; then its name. Phase 1:
 * - Devour the dawn: a sun rises over the room; it drags the sun down and swallows it - the world goes dark but for
 *   it, cracks of light shining through its body - then it breathes the light back out of its mouths: six beams
 *   turning round it, swept along the floor (marked) - keep moving round it; at last the light it held bursts out;
 * - Trial of the shape: one player is given a shape to draw (on the left) and a moment to draw it; drawn near enough,
 *   it is wounded; not, and everyone pays. It cannot be hurt, and hurts no one, while the trial lasts. It is its
 *   first attack, and comes again at least every fourth;
 * - Hungering maws: mouths open in the floor under the players (and elsewhere) and bite - three waves.
 * At half health it writhes, clutching its head - and grows: a second pair of arms, three pairs of wings, mouths on
 * its chest. Phase 2 adds:
 * - The black sun: a black sun hangs over it, and over the whole room whoever looks at it (or the sun) is pulled in
 *   and burned - look away and it leaves you be; at the end it bursts on everyone still looking;
 * - Chorus of mouths: mouths scream all over everyone's screen, and with every scream a dark shock wave rolls over
 *   the floor - jump it;
 * - Arms of the abyss: two great shadow hands drag across the floor along marked lanes, one after another, then clap
 *   together on a player;
 * - The feast: it devours the light - a darkness that eats at everyone, but for a few shafts of dawn light that move
 *   about the room three times (marked; three seconds to get to one each time); stand in one - and at the end it
 *   swallows everything outside them.
 */
public class Nocthariel extends Monster implements BossTargeting.Marked {
    public static final byte DORMANT = 0, RISE = 1, AWAKE = 2, TRANSFORM = 3;
    public static final byte NONE = 0, DEVOUR = 1, DRAW = 2, MAWS = 3, BLACK_SUN = 4, CHORUS = 5, ARMS = 6, FEAST = 7;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Nocthariel.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(Nocthariel.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> HOME = SynchedEntityData.defineId(Nocthariel.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Byte> ATTACK = SynchedEntityData.defineId(Nocthariel.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(Nocthariel.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Vector3f> SUN = SynchedEntityData.defineId(Nocthariel.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> HAND_L = SynchedEntityData.defineId(Nocthariel.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> HAND_R = SynchedEntityData.defineId(Nocthariel.class, EntityDataSerializers.VECTOR3);

    private static final byte EVENT_DEVOUR = 100, EVENT_CAST = 101, EVENT_SCREAM = 102, EVENT_SLAM = 103, EVENT_CHANT = 104,
            EVENT_CHANT_END = 105, EVENT_WRITHE = 106, EVENT_EMERGE = 107;
    public static final double HOVER = 1.5, CHEST = 4.5, HEAD = 7.6;
    public static final int RISE_TICKS = 140, TRANSFORM_TICKS = 150, GROW_AT = 70;
    private static final double WAKE_RANGE = 22, AGGRO = 64, ROOM_R = 26, RANGE = 64;
    private static final int RED = 0xC01020;

    // devour the dawn
    public static final int SUN_FORMED = 40, SWALLOW = 88, BREATH_START = 100, BREATH_END = 230, DEVOUR_END = 245;
    private static final float BREATH_SPIN = 1.3F, BREATH_DAMAGE = 14, BREATH_LENGTH = 27;
    // the trial
    private static final int DRAW_TICKS = 300;
    private static final float TRIAL_WOUND = 70, TRIAL_PUNISH = 16;
    // maws
    private static final int MAW_WARN = 28;
    private static final float MAW_DAMAGE = 18, MAW_RADIUS = 2.6F;
    // black sun
    private static final int BLACK_SUN_TICKS = 220;
    private static final float SUN_TOUCH = 20, SUN_BURST = 28;
    // chorus
    public static final double SHOCK_SPEED = 0.6, SHOCK_START = 3, SHOCK_END = 30;
    private static final float SHOCK_DAMAGE = 16;
    // arms
    private static final int SWEEPS = 5, SWEEP_EVERY = 34, SWEEP_WARN = 22;
    private static final float SWEEP_DAMAGE = 22, CLAP_DAMAGE = 28;
    // feast
    private static final int FEAST_ROUND = 85, FEAST_GRACE = 60, FEAST_TICKS = 10 + 3 * FEAST_ROUND;
    private static final float REFUGE_RADIUS = 3.0F, DARK_BITE = 4, SWALLOW_DAMAGE = 25;

    // client
    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState devourAnimation = new AnimationState();
    public final AnimationState castAnimation = new AnimationState();
    public final AnimationState screamAnimation = new AnimationState();
    public final AnimationState slamAnimation = new AnimationState();
    public final AnimationState chantAnimation = new AnimationState();
    public final AnimationState writheAnimation = new AnimationState();
    public final AnimationState emergeAnimation = new AnimationState();
    /** Ticks since it began dying (both sides; its own count, so vanilla neither tips nor reddens it). */
    public int dying;
    public final AnimationState deathAnimation = new AnimationState();
    public int riseSeenAt = -1000, attackSeenAt = -1000, attackEndedAt = -1000, phaseSeenAt = -1000;
    public byte lastAttackSeen;
    public final Vec3[] handBefore = {Vec3.ZERO, Vec3.ZERO}, handNow = {Vec3.ZERO, Vec3.ZERO};
    public Vec3 sunBefore = Vec3.ZERO, sunNow = Vec3.ZERO;

    // server
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private final BossTargeting targeting = new BossTargeting();
    private int riseTicks, transformTicks, actTick, cooldown = 50;
    private byte lastAttack = NONE;
    private int sinceTrial = 99;
    private double drift;
    private boolean trial, trialStrike;
    @Nullable private UUID drawer;
    private int drawShape, drawUntil;
    private Vec3 sun = Vec3.ZERO;
    private final Vec3[] hand = new Vec3[2], handGoal = new Vec3[2];
    private final double[] handSpeed = {1.0, 1.0};
    private Vec3 laneFrom = Vec3.ZERO, laneDir = Vec3.ZERO, clapAt = Vec3.ZERO, clapSide = Vec3.ZERO;
    private final Set<UUID> swept = new HashSet<>();
    private final List<Vec3> refuges = new ArrayList<>();
    private record Shock(Vec3 center, int start, Set<UUID> hit) {}
    private final List<Shock> shocks = new ArrayList<>();
    private final int[] breathIds = new int[6];

    public Nocthariel(EntityType<? extends Nocthariel> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setPersistenceRequired();
        this.xpReward = 1000;
        bossBar.setVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 900.0)
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
        builder.define(PHASE, (byte) 1);
        builder.define(SUN, new Vector3f());
        builder.define(HAND_L, new Vector3f());
        builder.define(HAND_R, new Vector3f());
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

    public int phase() {
        return entityData.get(PHASE);
    }

    public Vec3 home() {
        return Vec3.atBottomCenterOf(entityData.get(HOME));
    }

    public boolean hasHome() {
        return !entityData.get(HOME).equals(BlockPos.ZERO);
    }

    public Vec3 headAt() {
        return position().add(0, HEAD, 0);
    }

    /** Where the sun it devours is, `since` ticks into that attack (the same on both sides). */
    public Vec3 dawnSunAt(float since) {
        Vec3 high = home().add(0, 17, 0), mouth = headAt();
        if (since < SUN_FORMED) return high;
        float k = Mth.clamp((since - SUN_FORMED) / (SWALLOW - SUN_FORMED), 0, 1);
        k = k * k * (3 - 2 * k);
        return high.lerp(mouth, k);
    }

    /** How big that sun is (0 when swallowed). */
    public static float dawnSunSize(float since) {
        if (since < SUN_FORMED) return 5 * Mth.clamp(since / SUN_FORMED, 0, 1);
        if (since < SWALLOW) return 5 - 2 * (since - SUN_FORMED) / (SWALLOW - SUN_FORMED);
        return Math.max(0, 3 - (since - SWALLOW) / 3);
    }

    /** How far out a dark shock wave is, age ticks after it started. */
    public static double shockAt(float age) {
        return SHOCK_START + SHOCK_SPEED * age;
    }

    // ------------------------------------------------------------------ every tick, both sides

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && isDeadOrDying()) deathAnimation.startIfStopped(tickCount);
        if (!level().isClientSide) return;
        if (state() != DORMANT) idleAnimation.startIfStopped(tickCount);
        for (int i = 0; i < 2; i++) {
            Vector3f v = entityData.get(i == 0 ? HAND_L : HAND_R);
            handBefore[i] = handNow[i].equals(Vec3.ZERO) ? new Vec3(v) : handNow[i];
            handNow[i] = new Vec3(v);
        }
        Vector3f s = entityData.get(SUN);
        sunBefore = sunNow.equals(Vec3.ZERO) ? new Vec3(s) : sunNow;
        sunNow = new Vec3(s);
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EVENT_DEVOUR -> devourAnimation.start(tickCount);
            case EVENT_CAST -> castAnimation.start(tickCount);
            case EVENT_SCREAM -> screamAnimation.start(tickCount);
            case EVENT_SLAM -> slamAnimation.start(tickCount);
            case EVENT_CHANT -> chantAnimation.start(tickCount);
            case EVENT_CHANT_END -> chantAnimation.stop();
            case EVENT_WRITHE -> writheAnimation.start(tickCount);
            case EVENT_EMERGE -> {
                writheAnimation.stop();
                emergeAnimation.start(tickCount);
            }
            default -> super.handleEntityEvent(id);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!level().isClientSide) return;
        if (STATE.equals(key) && state() == RISE) riseSeenAt = tickCount;
        if (PHASE.equals(key) && phase() == 2) phaseSeenAt = tickCount;
        if (ATTACK.equals(key)) {
            if (attack() != NONE) {
                attackSeenAt = tickCount;
                lastAttackSeen = attack();
            } else {
                attackEndedAt = tickCount;
            }
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(48);
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
            setPos(home().add(0, -10, 0));
        }
        setDeltaMovement(Vec3.ZERO);
        if (tickCount % 10 == 0) updateBossBar(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        switch (state()) {
            case DORMANT -> {
                setPos(home().add(0, -10, 0));
                if (tickCount % 10 == 0 && nearestPlayer(WAKE_RANGE) != null) startRise(level);
            }
            case RISE -> rise(level);
            case TRANSFORM -> transformTick(level);
            default -> {
                fight(level);
                // a haze about it, so the black of it can be made out
                if (tickCount % 200 == 0) ScreenFx.playOnNear(this, RANGE, Anchor.ENTITY, 0, ScreenEffect.AURA, 210, 0.6F, -1);
            }
        }
        tickShocks(level);
        moveHands();
    }

    private void startRise(ServerLevel level) {
        setState(RISE);
        riseTicks = 0;
        playSound(SoundEvents.AMBIENT_CAVE.value(), 6.0F, 0.4F);
        screen(ScreenEffect.DARKNESS, RISE_TICKS, 0.6F, -1);
        screen(ScreenEffect.DESATURATE, RISE_TICKS, 0.7F, -1);
        screen(ScreenEffect.SHAKE, RISE_TICKS, 0.3F, -1);
        Vec3 h = home();
        PacketDistributor.sendToPlayersNear(level, null, h.x, h.y, h.z, 96, new FireFxPayload(FireFxPayload.MAW, h.x, h.y, h.z, 10, 0, 110, RISE_TICKS));
    }

    /** Darkness falls, a mouth opens in the floor, it rises out of it screaming; then its name. */
    private void rise(ServerLevel level) {
        int t = riseTicks++;
        double k = Mth.clamp((t - 20) / 90.0, 0, 1);
        k = k * k * (3 - 2 * k);
        setPos(home().add(0, Mth.lerp(k, -10, HOVER), 0));
        if (t % 20 == 0 && t < 110) playSound(SoundEvents.WARDEN_HEARTBEAT, 6.0F, 0.5F);
        if (t == 108) {
            level.broadcastEntityEvent(this, EVENT_SCREAM);
            playSound(SoundEvents.WARDEN_ROAR, 6.0F, 0.5F);
            playSound(SoundEvents.GHAST_SCREAM, 6.0F, 0.3F);
        }
        if (t == 112) {
            screen(ScreenEffect.FLASH, 20, 1.0F, 0x000000);
            screenOn(Anchor.EXCEPT, ScreenEffect.INVERT, 8, 1.0F, -1);
            screen(ScreenEffect.MOUTHS, 45, 1.0F, -1);
            screen(ScreenEffect.TEXT, 90, 1.0F, RED, "Nocthariel, Who Devoured the Dawn");
            screenOn(Anchor.ENTITY, ScreenEffect.OUTLINE, 70, 1.0F, RED);
            ScreenFx.shake(level, home(), RANGE, 30, 1.0F);
        }
        if (t >= RISE_TICKS) {
            setState(AWAKE);
            cooldown = 40;
            ScreenFx.playOnNear(this, RANGE, Anchor.ENTITY, 0, ScreenEffect.AURA, 210, 0.6F, -1);
        }
    }

    private void screen(ScreenEffect effect, int ticks, float strength, int color) {
        screen(effect, ticks, strength, color, "");
    }

    private void screen(ScreenEffect effect, int ticks, float strength, int color, String text) {
        for (Player p : fighters(RANGE)) if (p instanceof ServerPlayer sp) ScreenFx.play(sp, effect, ticks, strength, color, text);
    }

    private void screenOn(Anchor anchor, ScreenEffect effect, int ticks, float strength, int color) {
        for (Player p : fighters(RANGE)) if (p instanceof ServerPlayer sp) ScreenFx.playOn(sp, this, anchor, anchor == Anchor.AREA ? 14 : 0, effect, ticks, strength, color);
    }

    private void updateBossBar(ServerLevel level) {
        for (ServerPlayer p : List.copyOf(bossBar.getPlayers())) {
            if (state() == DORMANT || state() == RISE || !p.isAlive() || p.level() != level || p.distanceToSqr(this) > 90 * 90) bossBar.removePlayer(p);
        }
        if (state() == DORMANT || state() == RISE) return;
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
        }
        super.remove(reason);
    }

    /** Dying (7 s): every mouth screams, it is swallowed into its own darkness, turning ever faster, and the dawn bursts back. */
    public static final int DEATH_TICKS = 140;

    @Override
    protected void tickDeath() {
        dying++;
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 c = getBoundingBox().getCenter();
        int t = dying;
        if (t == 1) {
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.WARDEN_ROAR, 5.0F, 0.4F);
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.GHAST_SCREAM, 4.0F, 0.4F);
            BossDeath.screen(this, com.divinis.screenfx.ScreenEffect.MOUTHS, 70, 1.5F, -1);
            BossDeath.screen(this, com.divinis.screenfx.ScreenEffect.DARKNESS, DEATH_TICKS - 10, 0.6F, -1);
        }
        if (t < 70 && t % 7 == 0) {   // every mouth screams
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.GHAST_SCREAM, 3.0F, 0.3F + random.nextFloat() * 0.3F);
            BossDeath.shake(this, 6, 0.3F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK, c.x, c.y, c.z, 12, 2.0, 2.0, 2.0, 0.08);
        }
        if (t >= 60 && t < DEATH_TICKS) {   // swallowed into its own dark
            BossDeath.implode(level, net.minecraft.core.particles.ParticleTypes.SQUID_INK, c, 10, 12, 0.5);
            BossDeath.implode(level, net.minecraft.core.particles.ParticleTypes.SCULK_SOUL, c, 7, 3, 0.3);
            if (t % 10 == 0) BossDeath.sound(this, net.minecraft.sounds.SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 0.5F + (t - 60) * 0.01F);
        }
        if (t == 90) BossDeath.screen(this, com.divinis.screenfx.ScreenEffect.DISTORT, DEATH_TICKS - 90, 0.7F, -1);
        if (t == DEATH_TICKS - 6) BossDeath.screen(this, com.divinis.screenfx.ScreenEffect.FLASH, 40, 1.0F, 0xFFF0D0);
        if (t == DEATH_TICKS) {   // the dawn comes back
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, 5.0F, 0.6F);
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.BELL_RESONATE, 4.0F, 0.8F);
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.5F);
            BossDeath.shake(this, 30, 1.0F);
            BossDeath.sphere(level, net.minecraft.core.particles.ParticleTypes.END_ROD, c, 160, 1.0);
            BossDeath.sphere(level, net.minecraft.core.particles.ParticleTypes.FLAME, c, 60, 0.6);
            BossDeath.ring(level, net.minecraft.core.particles.ParticleTypes.END_ROD, c, 2, 72, 0.9);
        }
        if (t >= DEATH_TICKS) remove(RemovalReason.KILLED);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            Telegraphs.cancel(this);
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

    private Vec3 inRoom(Vec3 at) {
        Vec3 d = at.subtract(home()).multiply(1, 0, 1);
        if (d.length() > ROOM_R - 4) at = home().add(d.normalize().scale(ROOM_R - 4));
        return onGround(at);
    }

    private void face(Vec3 at) {
        Vec3 d = at.subtract(position());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float now = Mth.approachDegrees(getYRot(), yaw, 4.0F);
        setYRot(now);
        setYHeadRot(now);
        setYBodyRot(now);
    }

    private void fx(ServerLevel level, byte kind, Vec3 at, float a, float b, int delay, int duration) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 96, new FireFxPayload(kind, at.x, at.y, at.z, a, b, delay, duration));
    }

    // ------------------------------------------------------------------ the fight

    private void fight(ServerLevel level) {
        if (attack() == NONE || getTarget() == null || !getTarget().isAlive()) setTarget(targeting.pick(this, home(), AGGRO, getTarget()));
        LivingEntity target = getTarget();
        actTick++;
        switch (attack()) {
            case DEVOUR -> devour(level);
            case DRAW -> trial(level);
            case MAWS -> maws(level);
            case BLACK_SUN -> blackSun(level, target);
            case CHORUS -> chorus(level);
            case ARMS -> arms(level, target);
            case FEAST -> feast(level);
            default -> {
                drift += 0.012;
                Vec3 goal = home().add(Math.cos(drift) * 5, HOVER + Math.sin(tickCount * 0.04) * 0.5, Math.sin(drift) * 5);
                setPos(position().add(goal.subtract(position()).scale(0.05)));
                if (target != null) face(target.position());
                if (target == null || --cooldown > 0) break;
                byte next;
                if (sinceTrial >= 4) {
                    next = DRAW; // the trial first, then at least every fourth attack
                } else {
                    do {
                        float r = random.nextFloat();
                        if (phase() == 2 && random.nextFloat() < 0.65F) next = r < 0.25F ? BLACK_SUN : r < 0.5F ? CHORUS : r < 0.75F ? ARMS : FEAST;
                        else next = r < 0.38F ? DEVOUR : r < 0.66F ? DRAW : MAWS;
                    } while (next == lastAttack);
                }
                begin(next);
            }
        }
    }

    private void begin(byte a) {
        entityData.set(ATTACK, a);
        lastAttack = a;
        actTick = 0;
        sinceTrial = a == DRAW ? 0 : sinceTrial + 1;
    }

    /** /bossattack: starts an attack now (by name: devour, draw, maws, black_sun, chorus, arms, feast); false if it can't. */
    public boolean forceAttack(String name) {
        String[] names = {"devour", "draw", "maws", "black_sun", "chorus", "arms", "feast"};
        for (int i = 0; i < names.length; i++) {
            if (!names[i].equals(name)) continue;
            if (state() != AWAKE) return false;
            Telegraphs.cancel(this);
            if (trial) {
                trial = false;
                drawer = null;
            }
            if (getTarget() == null) setTarget(targeting.pick(this, home(), AGGRO, null));
            begin((byte) (i + 1));
            return true;
        }
        return false;
    }

    private void done() {
        entityData.set(ATTACK, NONE);
        actTick = 0;
        cooldown = phase() == 2 ? 30 + random.nextInt(20) : 50 + random.nextInt(30);
    }

    // ---- devour the dawn

    private void devour(ServerLevel level) {
        int t = actTick - 1;
        LivingEntity target = getTarget();
        if (target != null && t < BREATH_START) face(target.position());
        if (t == 0) {
            level.broadcastEntityEvent(this, EVENT_CAST);
            playSound(SoundEvents.BEACON_ACTIVATE, 6.0F, 0.6F);
            screen(ScreenEffect.FLASH, 12, 0.4F, 0xFFE0A0);
            screen(ScreenEffect.VIGNETTE, SWALLOW, 0.4F, 0xFFC860);
        }
        if (t == SWALLOW - 38) {
            level.broadcastEntityEvent(this, EVENT_DEVOUR);
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 5.0F, 0.6F);
        }
        if (t == SWALLOW) {
            playSound(SoundEvents.GENERIC_EAT, 6.0F, 0.3F);
            playSound(SoundEvents.WARDEN_ROAR, 6.0F, 0.6F);
            screenOn(Anchor.EXCEPT, ScreenEffect.INVERT, 6, 1.0F, -1);
            screen(ScreenEffect.DARKNESS, BREATH_END - SWALLOW, 0.85F, -1);
            screenOn(Anchor.ENTITY, ScreenEffect.OUTLINE, BREATH_END - SWALLOW, 1.0F, 0xFFC860);
            ScreenFx.shake(level, home(), RANGE, 20, 0.8F);
        }
        if (t == BREATH_START - 30) {
            // the beams' sweep marked on the floor, then turning
            float base = random.nextFloat() * 360;
            Vec3 feet = onGround(position());
            for (int k = 0; k < 6; k++) {
                breathIds[k] = Beams.nextId();
                Telegraphs.sweep(level, Telegraph.rect(feet, base + k * 60, BREATH_LENGTH, 1.3F), 30, BREATH_END - BREATH_START, BREATH_SPIN, 10, this, e -> {
                    if (e.hurt(damageSources().mobAttack(this), BREATH_DAMAGE * damageScale())) e.igniteForSeconds(3);
                });
            }
            breathBase = base;
        }
        if (t >= BREATH_START && t < BREATH_END) {
            Vec3 head = headAt(), feet = onGround(position());
            for (int k = 0; k < 6; k++) {
                float yaw = (breathBase + k * 60 + BREATH_SPIN * (t - BREATH_START)) * Mth.DEG_TO_RAD;
                Vec3 end = feet.add(-Mth.sin(yaw) * BREATH_LENGTH, 0.2, Mth.cos(yaw) * BREATH_LENGTH);
                Beams.send(level, breathIds[k], BeamPayload.LIGHT, head, end, 1.6F, 3);
            }
            if (t % 20 == 0) playSound(SoundEvents.BEACON_AMBIENT, 6.0F, 1.4F);
        }
        if (t == BREATH_END) {
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 5.0F, 0.6F);
            screen(ScreenEffect.FLASH, 18, 0.8F, 0xFFF0C0);
            ScreenFx.shake(level, home(), RANGE, 16, 0.7F);
        }
        if (t >= DEVOUR_END) done();
    }

    private float breathBase;

    // ---- the trial of the shape

    private void trial(ServerLevel level) {
        int t = actTick - 1;
        if (t == 0) {
            // anyone there may be given the shape (players in creative too, for trying it)
            List<Player> ps = new ArrayList<>();
            for (Player p : level.players()) if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(home()) < RANGE * RANGE) ps.add(p);
            if (ps.isEmpty()) {
                done();
                return;
            }
            Player p = ps.get(random.nextInt(ps.size()));
            drawer = p.getUUID();
            drawShape = random.nextInt(DrawShapes.SHAPES.size());
            drawUntil = tickCount + DRAW_TICKS + 40; // a little more for the drawing to arrive
            trial = true;
            level.broadcastEntityEvent(this, EVENT_CHANT);
            playSound(SoundEvents.WITCH_AMBIENT, 6.0F, 0.4F);
            if (p instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, new DrawPayloads.Open(getId(), drawShape, DRAW_TICKS));
            for (Player other : ps) {
                if (!(other instanceof ServerPlayer sp)) continue;
                ScreenFx.play(sp, ScreenEffect.VIGNETTE, DRAW_TICKS, 0.5F, RED, "");
                if (other != p) ScreenFx.play(sp, ScreenEffect.TEXT, 80, 0.6F, 0xE0C0C0, p.getName().getString() + " must trace the shape...");
            }
        }
        if (t % 30 == 15) playSound(SoundEvents.WARDEN_HEARTBEAT, 4.0F, 0.6F);
        if (drawer != null && (tickCount > drawUntil || level.getPlayerByUUID(drawer) == null || !level.getPlayerByUUID(drawer).isAlive())) {
            judge(level, false);
        }
    }

    /** The drawing has come back from its player. */
    public void drawingSubmitted(ServerPlayer player, List<float[]> strokes) {
        if (!trial || drawer == null || !drawer.equals(player.getUUID()) || !(level() instanceof ServerLevel level)) return;
        float d = DrawShapes.distance(DrawShapes.SHAPES.get(drawShape).strokes(), strokes);
        judge(level, d <= DrawShapes.PASS);
    }

    private void judge(ServerLevel level, boolean drawn) {
        trial = false;
        drawer = null;
        level.broadcastEntityEvent(this, EVENT_CHANT_END);
        level.broadcastEntityEvent(this, EVENT_SCREAM);
        if (drawn) {
            playSound(SoundEvents.WITHER_HURT, 6.0F, 0.4F);
            playSound(SoundEvents.GHAST_HURT, 6.0F, 0.4F);
            screen(ScreenEffect.FLASH, 12, 0.8F, 0xFFF4E0);
            screenOn(Anchor.EXCEPT, ScreenEffect.INVERT, 6, 1.0F, -1);
            screenOn(Anchor.ENTITY, ScreenEffect.GLITCH, 30, 1.2F, -1);
            screen(ScreenEffect.TEXT, 50, 0.6F, 0xFFE0A0, "The shape holds - it is wounded!");
            trialStrike = true;
            hurt(damageSources().magic(), TRIAL_WOUND);
            trialStrike = false;
        } else {
            playSound(SoundEvents.WARDEN_ROAR, 6.0F, 0.8F);
            playSound(SoundEvents.WITCH_CELEBRATE, 6.0F, 0.5F);
            screen(ScreenEffect.MOUTHS, 40, 1.2F, -1);
            screen(ScreenEffect.FLASH, 14, 0.8F, RED);
            screen(ScreenEffect.TEXT, 50, 0.6F, RED, "The shape is wrong - everyone pays");
            ScreenFx.shake(level, home(), RANGE, 20, 1.0F);
            for (Player p : fighters(RANGE)) {
                p.hurt(damageSources().mobAttack(this), TRIAL_PUNISH * damageScale());
                p.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
            }
        }
        done();
    }

    // ---- hungering maws

    private void maws(ServerLevel level) {
        int t = actTick - 1;
        if (t % 40 == 0 && t < 120) {
            level.broadcastEntityEvent(this, EVENT_SLAM);
            playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 5.0F, 0.5F);
            List<Vec3> at = new ArrayList<>();
            for (Player p : fighters(RANGE)) at.add(onGround(p.position()));
            for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * 20;
                at.add(inRoom(home().add(Math.cos(a) * r, 0, Math.sin(a) * r)));
            }
            for (Vec3 p : at) {
                Telegraphs.show(level, Telegraph.circle(p, MAW_RADIUS), MAW_WARN, 4, this, e -> {
                    if (e.hurt(damageSources().mobAttack(this), MAW_DAMAGE * damageScale())) {
                        e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
                        e.setDeltaMovement(e.getDeltaMovement().add(0, 0.5, 0));
                        e.hurtMarked = true;
                    }
                });
                fx(level, FireFxPayload.MAW, p, MAW_RADIUS * 2, 0, MAW_WARN, MAW_WARN + 16);
            }
        }
        if (t % 40 == MAW_WARN && t < 120) {
            playSound(SoundEvents.EVOKER_FANGS_ATTACK, 5.0F, 0.5F);
            for (Player p : fighters(RANGE)) if (p instanceof ServerPlayer sp) ScreenFx.play(sp, ScreenEffect.SHAKE, 8, 0.5F);
        }
        if (t >= 130) done();
    }

    // ---- the black sun

    private void blackSun(ServerLevel level, @Nullable LivingEntity target) {
        int t = actTick - 1;
        sun = headAt().add(0, 6, 0); // it hangs over it
        if (t == 0) {
            level.broadcastEntityEvent(this, EVENT_CAST);
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 6.0F, 0.4F);
            screenOn(Anchor.EXCEPT, ScreenEffect.DESATURATE, BLACK_SUN_TICKS, 0.9F, -1);
            screen(ScreenEffect.TEXT, 60, 0.6F, RED, "Do not look at it");
        }
        if (t >= 20 && t < 200) {
            // the whole room: whoever looks at it is pulled in and burned; whoever looks away is left alone
            for (Player p : fighters(RANGE)) {
                if (!looksAtMe(p)) continue;
                Vec3 to = sun.subtract(p.position()).multiply(1, 0, 1);
                double d = to.length();
                if (t % 3 == 0 && d > 1) {
                    p.setDeltaMovement(to.scale(0.14 / d));
                    p.hurtMarked = true;
                }
                if (t % 10 == 0) {
                    p.hurt(damageSources().mobAttack(this), SUN_TOUCH * 0.3F * damageScale());
                    if (p instanceof ServerPlayer sp) {
                        ScreenFx.play(sp, ScreenEffect.ABERRATION, 12, 1.0F);
                        ScreenFx.play(sp, ScreenEffect.VIGNETTE, 12, 0.7F, RED, "");
                    }
                }
            }
            if (t % 20 == 0) playSound(SoundEvents.PORTAL_AMBIENT, 5.0F, 0.3F);
        }
        if (t == 200) {
            // it bursts: everyone still looking at it takes it full
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 6.0F, 0.4F);
            screen(ScreenEffect.FLASH, 14, 0.8F, RED);
            ScreenFx.shake(level, sun, RANGE, 18, 1.0F);
            for (Player p : fighters(RANGE)) {
                if (looksAtMe(p) && p.hurt(damageSources().mobAttack(this), SUN_BURST * damageScale())) {
                    p.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
                }
            }
        }
        entityData.set(SUN, new Vector3f((float) sun.x, (float) sun.y, (float) sun.z));
        if (t >= BLACK_SUN_TICKS) done();
    }

    /** Whether a player is looking at it (or its black sun over it), with nothing in the way. */
    private boolean looksAtMe(Player p) {
        Vec3 look = p.getViewVector(1.0F);
        Vec3 eye = p.getEyePosition();
        for (Vec3 at : new Vec3[] {position().add(0, CHEST, 0), sun}) {
            Vec3 to = at.subtract(eye).normalize();
            if (look.dot(to) > 0.77 && p.hasLineOfSight(this)) return true;
        }
        return false;
    }

    // ---- the chorus of mouths

    private void chorus(ServerLevel level) {
        int t = actTick - 1;
        if (t == 0) {
            screen(ScreenEffect.MOUTHS, 170, 0.9F, -1);
            playSound(SoundEvents.GHAST_SCREAM, 6.0F, 0.4F);
        }
        if (t >= 10 && t <= 140 && (t - 10) % 32 == 0) {
            level.broadcastEntityEvent(this, EVENT_SCREAM);
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 6.0F, 0.5F);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 6.0F, 0.5F);
            screen(ScreenEffect.SHAKE, 8, 0.6F, -1);
            screen(ScreenEffect.ABERRATION, 12, 1.0F, -1);
            screenOn(Anchor.AREA, ScreenEffect.DISTORT, 12, 1.2F, -1);
            Vec3 at = onGround(position());
            shocks.add(new Shock(at, tickCount, new HashSet<>()));
            fx(level, FireFxPayload.DARK_SHOCK, at, 0, 0, 0, (int) ((SHOCK_END - SHOCK_START) / SHOCK_SPEED));
        }
        if (t >= 170) done();
    }

    private void tickShocks(ServerLevel level) {
        if (shocks.isEmpty()) return;
        shocks.removeIf(s -> shockAt(tickCount - s.start()) > SHOCK_END);
        for (Shock s : shocks) {
            double r = shockAt(tickCount - s.start());
            for (Player p : fighters(RANGE)) {
                if (s.hit().contains(p.getUUID())) continue;
                double d = p.position().subtract(s.center()).horizontalDistance();
                if (Math.abs(d - r) > 0.8 || !p.onGround() || p.getY() > s.center().y + 0.6) continue;
                s.hit().add(p.getUUID());
                if (p.hurt(damageSources().mobAttack(this), SHOCK_DAMAGE * damageScale())) {
                    Vec3 push = p.position().subtract(s.center()).multiply(1, 0, 1).normalize();
                    p.setDeltaMovement(p.getDeltaMovement().add(push.x * 0.8, 0.5, push.z * 0.8));
                    p.hurtMarked = true;
                }
            }
        }
    }

    // ---- arms of the abyss

    private void arms(ServerLevel level, @Nullable LivingEntity target) {
        int t = actTick - 1;
        if (t == 0) {
            level.broadcastEntityEvent(this, EVENT_CAST);
            playSound(SoundEvents.WARDEN_EMERGE, 6.0F, 0.6F);
            for (int i = 0; i < 2; i++) hand[i] = onGround(home()).add(i == 0 ? 6 : -6, -3, 0);
        }
        if (target == null) {
            done();
            return;
        }
        if (t < SWEEPS * SWEEP_EVERY) {
            int s = t / SWEEP_EVERY, local = t % SWEEP_EVERY, h = s % 2;
            if (local == 0) {
                double yaw = random.nextDouble() * Math.PI * 2;
                laneDir = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
                laneFrom = onGround(target.position()).subtract(laneDir.scale(30));
                float telegraphYaw = (float) (Mth.atan2(-laneDir.x, laneDir.z) * Mth.RAD_TO_DEG);
                Telegraphs.show(level, Telegraph.rect(laneFrom, telegraphYaw, 60, 2.2F), SWEEP_WARN, SWEEP_EVERY - SWEEP_WARN, this, null);
                handGoal[h] = laneFrom.add(0, 0.9, 0);
                handSpeed[h] = 3.5;
                swept.clear();
                playSound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 4.0F, 0.5F);
            }
            if (local >= SWEEP_WARN) {
                handGoal[h] = laneFrom.add(laneDir.scale(60)).add(0, 0.9, 0);
                handSpeed[h] = 2.6;
                for (Player p : fighters(RANGE)) {
                    if (swept.contains(p.getUUID()) || hand[h] == null) continue;
                    if (p.position().subtract(hand[h]).horizontalDistance() < 2.4 && Math.abs(p.getY() - hand[h].y) < 2.5) {
                        swept.add(p.getUUID());
                        if (p.hurt(damageSources().mobAttack(this), SWEEP_DAMAGE * damageScale())) {
                            p.setDeltaMovement(p.getDeltaMovement().add(laneDir.x * 1.2, 0.6, laneDir.z * 1.2));
                            p.hurtMarked = true;
                        }
                    }
                }
                if (local % 4 == 0) level.sendParticles(ParticleTypes.SQUID_INK, hand[h].x, hand[h].y, hand[h].z, 6, 1, 0.3, 1, 0.02);
            }
            return;
        }
        int c = t - SWEEPS * SWEEP_EVERY;
        if (c == 0) {
            clapAt = onGround(target.position());
            double yaw = random.nextDouble() * Math.PI * 2;
            clapSide = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            for (int i = 0; i < 2; i++) {
                handGoal[i] = clapAt.add(clapSide.scale(i == 0 ? 9 : -9)).add(0, 1.5, 0);
                handSpeed[i] = 2.0;
            }
            Telegraphs.show(level, Telegraph.circle(clapAt, 5), 30, 6, this, e -> {
                if (e.hurt(damageSources().mobAttack(this), CLAP_DAMAGE * damageScale())) {
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.9, 0));
                    e.hurtMarked = true;
                }
            });
        }
        if (c == 24) {
            for (int i = 0; i < 2; i++) {
                handGoal[i] = clapAt.add(clapSide.scale(i == 0 ? 1.0 : -1.0)).add(0, 1.5, 0);
                handSpeed[i] = 3.0;
            }
        }
        if (c == 30) {
            playSound(SoundEvents.ANVIL_LAND, 6.0F, 0.3F);
            screen(ScreenEffect.FLASH, 8, 0.7F, 0x000000);
            screenOn(Anchor.EXCEPT, ScreenEffect.INVERT, 6, 1.0F, -1);
            ScreenFx.shake(level, clapAt, RANGE, 14, 1.0F);
        }
        if (c == 44) done();
    }

    /** The shadow hands: only while it uses them; out of sight under the floor otherwise. */
    private void moveHands() {
        for (int i = 0; i < 2; i++) {
            if (hand[i] == null) hand[i] = home().add(0, -6, 0);
            Vec3 goal = attack() == ARMS && handGoal[i] != null ? handGoal[i] : home().add(i == 0 ? 6 : -6, -6, 0);
            double speed = attack() == ARMS ? handSpeed[i] : 1.5;
            Vec3 d = goal.subtract(hand[i]);
            double len = d.length();
            hand[i] = len <= speed ? goal : hand[i].add(d.scale(speed / len));
            entityData.set(i == 0 ? HAND_L : HAND_R, new Vector3f((float) hand[i].x, (float) hand[i].y, (float) hand[i].z));
        }
    }

    // ---- the feast

    private void feast(ServerLevel level) {
        int t = actTick - 1;
        if (t == 0) {
            level.broadcastEntityEvent(this, EVENT_CHANT);
            playSound(SoundEvents.WARDEN_HEARTBEAT, 6.0F, 0.4F);
            // dark, but not so dark the shafts of light cannot be seen across the room
            screen(ScreenEffect.DARKNESS, FEAST_TICKS, 0.55F, -1);
            screenOn(Anchor.ENTITY, ScreenEffect.OUTLINE, FEAST_TICKS, 0.9F, RED);
            screen(ScreenEffect.NOISE, FEAST_TICKS, 0.2F, -1);
        }
        int round = (t - 10) / FEAST_ROUND, local = (t - 10) % FEAST_ROUND;
        if (t >= 10 && round < 3 && local == 0) {
            refuges.clear();
            List<Player> ps = fighters(RANGE);
            for (int i = 0; i < ps.size() + 1; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = 4 + random.nextDouble() * 16;
                refuges.add(inRoom(home().add(Math.cos(a) * r, 0, Math.sin(a) * r)));
            }
            for (Vec3 r : refuges) {
                Beams.send(level, Beams.nextId(), BeamPayload.SHAFT, r.add(0, 30, 0), r, REFUGE_RADIUS * 2, FEAST_ROUND);
                Telegraphs.show(level, Telegraph.circle(r, REFUGE_RADIUS), FEAST_GRACE, 0, this, null);
            }
            playSound(SoundEvents.BELL_RESONATE, 5.0F, 0.5F);
        }
        // the darkness eats at whoever is out of the light - once they have had time to get to it
        if (t >= 10 && round < 3 && local >= FEAST_GRACE && local % 20 == 0) {
            for (Player p : fighters(RANGE)) {
                if (inRefuge(p)) continue;
                p.hurt(damageSources().magic(), DARK_BITE * damageScale());
                if (p instanceof ServerPlayer sp) ScreenFx.play(sp, ScreenEffect.VIGNETTE, 14, 0.8F, RED, "");
            }
            playSound(SoundEvents.WARDEN_HEARTBEAT, 5.0F, 0.6F);
        }
        int swallow = 10 + 3 * FEAST_ROUND - 15;
        if (t > swallow - 15 && t < swallow && t % 3 == 0) {
            for (Player p : fighters(RANGE)) {
                if (inRefuge(p)) continue;
                Vec3 to = position().subtract(p.position()).multiply(1, 0, 1);
                double d = to.length();
                if (d > 1) {
                    p.setDeltaMovement(to.scale(0.15 / d));
                    p.hurtMarked = true;
                }
            }
        }
        if (t == swallow) {
            level.broadcastEntityEvent(this, EVENT_CHANT_END);
            level.broadcastEntityEvent(this, EVENT_DEVOUR);
            playSound(SoundEvents.GENERIC_EAT, 6.0F, 0.2F);
            playSound(SoundEvents.WARDEN_ROAR, 6.0F, 0.4F);
            screen(ScreenEffect.FLASH, 16, 0.9F, RED);
            screen(ScreenEffect.MOUTHS, 30, 1.3F, -1);
            ScreenFx.shake(level, home(), RANGE, 20, 1.0F);
            for (Player p : fighters(RANGE)) {
                if (!inRefuge(p)) p.hurt(damageSources().mobAttack(this), SWALLOW_DAMAGE * damageScale());
            }
        }
        if (t >= FEAST_TICKS) {
            refuges.clear();
            done();
        }
    }

    private boolean inRefuge(Player p) {
        for (Vec3 r : refuges) if (p.position().subtract(r).horizontalDistance() <= REFUGE_RADIUS + 0.2) return true;
        return false;
    }

    // ------------------------------------------------------------------ it writhes and grows: the second phase

    private void startTransform(ServerLevel level) {
        Telegraphs.cancel(this);
        if (trial) {
            trial = false;
            drawer = null;
        }
        entityData.set(ATTACK, NONE);
        setState(TRANSFORM);
        transformTicks = 0;
        level.broadcastEntityEvent(this, EVENT_WRITHE);
        playSound(SoundEvents.WARDEN_DEATH, 6.0F, 0.5F);
        screenOn(Anchor.EXCEPT, ScreenEffect.DESATURATE, TRANSFORM_TICKS, 1.0F, -1);
        screen(ScreenEffect.GLITCH, GROW_AT, 0.5F, -1);
        screen(ScreenEffect.NOISE, TRANSFORM_TICKS, 0.3F, -1);
        screen(ScreenEffect.SHAKE, TRANSFORM_TICKS, 0.35F, -1);
    }

    private void transformTick(ServerLevel level) {
        int t = transformTicks++;
        if (t % 12 == 0 && t < GROW_AT) {
            playSound(SoundEvents.SKELETON_HURT, 5.0F, 0.3F + random.nextFloat() * 0.2F);
            playSound(SoundEvents.WARDEN_HEARTBEAT, 6.0F, 0.6F + t / 100.0F);
        }
        if (t == GROW_AT) {
            entityData.set(PHASE, (byte) 2);
            level.broadcastEntityEvent(this, EVENT_EMERGE);
            playSound(SoundEvents.WARDEN_ROAR, 6.0F, 0.4F);
            playSound(SoundEvents.GHAST_SCREAM, 6.0F, 0.3F);
            screen(ScreenEffect.FLASH, 22, 1.0F, RED);
            screenOn(Anchor.EXCEPT, ScreenEffect.INVERT, 8, 1.0F, -1);
            int rest = TRANSFORM_TICKS - GROW_AT;
            screen(ScreenEffect.MOUTHS, 60, 1.2F, -1);
            screen(ScreenEffect.ABERRATION, rest, 0.8F, -1);
            screen(ScreenEffect.VIGNETTE, rest, 0.6F, 0x500000);
            screenOn(Anchor.ENTITY, ScreenEffect.AURA, rest, 1.4F, -1);
            screenOn(Anchor.ENTITY, ScreenEffect.OUTLINE, rest, 1.2F, RED);
            ScreenFx.shake(level, home(), RANGE, 30, 1.2F);
        }
        if (t == GROW_AT + 40) {
            level.broadcastEntityEvent(this, EVENT_SCREAM);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 6.0F, 0.4F);
        }
        if (t >= TRANSFORM_TICKS) {
            setState(AWAKE);
            cooldown = 30;
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
        if (state() != AWAKE || trial && !trialStrike) return false; // untouchable while the trial lasts
        boolean hurt = super.hurt(source, amount);
        if (hurt && level() instanceof ServerLevel level) {
            targeting.onHurt(source, level.getGameTime());
            if (phase() == 1 && isAlive() && getHealth() <= getMaxHealth() * 0.5F) startTransform(level);
        }
        return hurt;
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
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public float getVoicePitch() {
        return 0.4F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return state() == AWAKE ? SoundEvents.WARDEN_AMBIENT : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
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
        if (tag.getByte("Phase") == 2) entityData.set(PHASE, (byte) 2);
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
