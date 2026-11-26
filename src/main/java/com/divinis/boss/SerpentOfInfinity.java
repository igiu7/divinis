package com.divinis.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.divinis.net.FireFxPayload;

import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenEffect.Anchor;
import com.divinis.screenfx.ScreenFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Serpent of Infinity (/summon divinis:serpent_of_infinity): a cosmic worm some 45 blocks long, its head and
 * body segments (SerpentPart) all to be hit; it passes through blocks. Asleep it lies coiled where it was summoned
 * (its home - the middle of its room); a player close by or a blow wakes it: it rises out of the coil with a roar.
 * Touching its head or body hurts. It fights with:
 * - Rift dash: it sinks into the room's edge and bursts out somewhere else along a lane through its target (marked
 *   for a moment), straight across the room and into the far side - four times in a row, fast (six when enraged);
 * - Eruption: a circle marks the ground under a player; it rises from below and bursts up through it with its jaws
 *   open - under up to three players, one after the other;
 * - Coil: it wraps its body round a player; a grid is marked inside the ring and then burns with purple fire -
 *   only the small squares between the lines are safe;
 * - Void grid (now and then): high above, it sets the whole room ablaze with a grid of purple flames, joined along
 *   their lines, burning for a while - stand in the squares;
 * - Gravity rift (its own idea): it tears open a rift that drags everyone in for three seconds before it collapses -
 *   run against the pull.
 * Below 40% health it is enraged: faster, more dashes, shorter rests.
 *
 * At half health it changes (the second phase): it rises over the room and coils into a ring, whiteness runs down
 * its body from the head to the tail, and it bursts, throwing everyone back. From then on it is bone-white (the
 * purple stays), hits harder, rests less, and has four more attacks besides the first ones:
 * - Wyrmlings: it calls up small flying worms that circle low over the players and dive at them - one hit kills
 *   one, but their bite is heavy;
 * - Void walls: walls of purple fire sweep across the room one after another, each from another side, each with a
 *   single gap a few blocks from its target - get to the gap (they are too high to jump); the next one rises only
 *   when the last is nearly across, so two never cut a player off;
 * - Star dash: it cuts a pentagram round its target in one go (the first line through them), five fast dashes from
 *   point to point, each marked beforehand, and the lines it cut burn for a while;
 * - Starfall: it vanishes into the sky, stars rain down on marked spots (most of them on and around the players),
 *   then it falls onto one player from high above, and shock waves of purple fire roll out from where it lands -
 *   jump over each as it reaches you.
 */
public class SerpentOfInfinity extends Monster implements BossTargeting.Marked {
    public static final byte SLEEPING = 0, WAKING = 1, AWAKE = 2, TRANSFORM = 3;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(SerpentOfInfinity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(SerpentOfInfinity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> HOME = SynchedEntityData.defineId(SerpentOfInfinity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(SerpentOfInfinity.class, EntityDataSerializers.BYTE);

    public static final float HEAD_HALF = 1.3F;
    private static final byte EVENT_BITE = 118, EVENT_ROAR = 117;
    private static final double WAKE_RANGE = 18, AGGRO = 56, ROOM_R = 26;
    private static final int WAKE_TICKS = 60;
    private static final float HEAD_DAMAGE = 14, BODY_DAMAGE = 8;

    private static final int DASH_PREP = 16;
    private static final double DASH_SPEED = 2.6;
    private static final int ERUPT_WARN = 30, ERUPT_STEP = 56;
    private static final float ERUPT_DAMAGE = 26, ERUPT_RADIUS = 3.5F;
    private static final int COIL_TICKS = 60;
    private static final float COIL_DAMAGE = 24, COIL_RADIUS = 7.5F, COIL_SPACING = 3.0F, COIL_LINE = 0.8F;
    private static final int GRID_WARN = 50, GRID_BURN = 30, GRID_EVERY = 900, GRID_FIRST = 500;
    private static final float GRID_DAMAGE = 12, GRID_SPACING = 4.0F, GRID_LINE = 1.0F;
    private static final int RIFT_TICKS = 60;
    private static final float RIFT_DAMAGE = 30, RIFT_RADIUS = 5.0F, RIFT_PULL = 18;

    // the second phase
    private static final int TRANSFORM_TICKS = 95, WHITEN_START = 30;
    private static final int MAX_WYRMLINGS = 6;
    public static final int WALL_WARN = 25;
    public static final float WALL_HALF_LENGTH = 32, WALL_GAP = 3.0F;
    private static final double WALL_GAP_SPREAD = 5;
    private static final double WALL_SPEED = 0.65, WALL_START = ROOM_R + 3;
    private static final int WALLS = 3;
    private static final int WALL_TRAVEL = (int) Math.ceil(2 * WALL_START / WALL_SPEED);
    private static final float WALL_DAMAGE = 16;
    private static final int STAR_WARN = 35;
    private static final double STAR_SPEED = 3.5, STAR_R = 14;
    private static final int[] STAR_ORDER = {0, 2, 4, 1, 3, 0};
    private static final float TRAIL_DAMAGE = 7;
    private static final float METEOR_DAMAGE = 13, METEOR_RADIUS = 2.2F, CRASH_DAMAGE = 30, CRASH_RADIUS = 6, SHOCK_DAMAGE = 16;
    private static final int METEOR_FALL = 22;
    public static final double SHOCK_SPEED = 0.55, SHOCK_START = 2, SHOCK_END = 30;
    private static final int SHOCKS = 3, SHOCK_EVERY = 18;

    private enum Act { ROAM, DASH, ERUPT, COIL, GRID, RIFT, WYRMS, WALLS, STAR, STARFALL }

    /** A wall of fire travelling along d across the room, its gap `gap` blocks to the side (along `side`). */
    private record Wall(Vec3 d, Vec3 side, float gap, int start, java.util.Set<UUID> hit) {}

    /** A shock wave rolling out from `center`, started at tick `start`. */
    private record Shock(Vec3 center, int start, java.util.Set<UUID> hit) {}

    /** How far out a shock wave is, age ticks after it started. */
    public static double shockAt(float age) {
        return SHOCK_START + SHOCK_SPEED * age;
    }

    /** How far along its way a wall is, age ticks after it rose (it stands at the edge first). */
    public static double wallAt(float age) {
        return -WALL_START + WALL_SPEED * Math.max(0, age - WALL_WARN);
    }

    private final SerpentPart[] parts = new SerpentPart[SerpentBody.SEGMENTS];
    public final SerpentBody body;

    // client
    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState biteAnimation = new AnimationState();
    public final AnimationState roarAnimation = new AnimationState();
    /** Ticks since it began dying (both sides; its own count, so vanilla neither tips nor reddens it). */
    public int dying;
    public final AnimationState deathAnimation = new AnimationState();

    /** Dying, when a segment bursts (the tail first, one every 4 ticks, the head last at DEATH_TICKS). */
    public static int burstTick(int segment) {
        return 14 + (SerpentBody.SEGMENTS - 1 - segment) * 4;
    }

    /** Client: has this segment burst already (it is not drawn any more)? */
    public boolean burst(int segment) {
        return isDeadOrDying() && dying >= burstTick(segment);
    }

    // server
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private Vec3 dir = new Vec3(0, 0, 1);
    private Act act = Act.ROAM;
    private int actTick, actStep, steps, cooldown = 40, wakeTicks;
    private int gridTimer = GRID_FIRST;
    private double roamAngle;
    private Vec3 laneDir = Vec3.ZERO, spot = Vec3.ZERO;
    private final Map<UUID, Integer> touched = new HashMap<>();
    private final BossTargeting targeting = new BossTargeting();
    private int coilSafeUntil;
    private Vec3 coilCenter = Vec3.ZERO;
    private int transformTicks;
    private final List<Wall> walls = new ArrayList<>();
    private final List<Shock> shocks = new ArrayList<>();
    private int wallsLaunched;
    private final Vec3[] star = new Vec3[5];
    private int starLane, spawned, toSpawn;

    // client: when the change to the second phase began (its tick count); where the server last put it
    private int transformSeenAt = -1000;
    @Nullable private Vec3 netTarget;
    private Vec3 netStep = Vec3.ZERO;
    private boolean netFresh;
    private int netMissed;

    public SerpentOfInfinity(EntityType<? extends SerpentOfInfinity> type, Level level) {
        super(type, level);
        for (int i = 0; i < parts.length; i++) parts[i] = new SerpentPart(this, i, i == parts.length - 1 ? 1.8F : 2.2F);
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1); // the parts take the ids right after it
        body = new SerpentBody(position());
        noPhysics = true;
        setNoGravity(true);
        setPersistenceRequired();
        this.xpReward = 400;
        bossBar.setVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 56.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 20.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, SLEEPING);
        builder.define(TARGET_ID, -1);
        builder.define(HOME, BlockPos.ZERO);
        builder.define(PHASE, (byte) 1);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < parts.length; i++) if (parts[i] != null) parts[i].setId(id + i + 1);
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    public byte state() {
        return entityData.get(STATE);
    }

    private void setState(byte s) {
        entityData.set(STATE, s);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    /** Whether a part (-1 the head, 0.. the segments) is white already - the whiteness runs down it in the change. */
    public boolean white(int segment) {
        if (phase() < 2) return false;
        if (state() != TRANSFORM) return true;
        return tickCount - transformSeenAt >= WHITEN_START + (segment + 1) * 2;
    }

    /** A flash on a part as it turns white (1 .. 0). */
    public float whitenFlash(int segment, float partial) {
        if (state() != TRANSFORM) return 0;
        float since = tickCount + partial - transformSeenAt - (WHITEN_START + (segment + 1) * 2);
        return since < 0 || since > 6 ? 0 : 1 - since / 6;
    }

    public Vec3 home() {
        return Vec3.atBottomCenterOf(entityData.get(HOME));
    }

    /** The middle of the head (the entity's position is the bottom of its head's box). */
    public Vec3 headCenter() {
        return position().add(0, HEAD_HALF, 0);
    }

    // ------------------------------------------------------------------ every tick, both sides

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && isDeadOrDying()) deathAnimation.startIfStopped(tickCount);
        if (level().isClientSide) followNet();
        if (state() == SLEEPING && !entityData.get(HOME).equals(BlockPos.ZERO)) body.coil(home());
        else body.follow(headCenter(), SerpentBody.look(getYRot(), getXRot()));
        for (int i = 0; i < parts.length; i++) {
            SerpentPart p = parts[i];
            Vec3 at = body.at[i];
            p.xo = p.getX();
            p.yo = p.getY();
            p.zo = p.getZ();
            p.setPos(at.x, at.y - p.getBbHeight() / 2, at.z);
        }
        if (level().isClientSide) {
            if (state() == AWAKE) idleAnimation.startIfStopped(tickCount);
            else idleAnimation.stop();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_BITE) biteAnimation.start(tickCount);
        else if (id == EVENT_ROAR) roarAnimation.start(tickCount);
        else super.handleEntityEvent(id);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STATE.equals(key) && level().isClientSide && state() == WAKING) roarAnimation.start(tickCount);
        if (STATE.equals(key) && level().isClientSide && state() == TRANSFORM) transformSeenAt = tickCount;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(48); // the whole body
    }

    // ------------------------------------------------------------------ server

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (entityData.get(HOME).equals(BlockPos.ZERO)) entityData.set(HOME, blockPosition());
        if (tickCount % 10 == 0) updateBossBar(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        setDeltaMovement(Vec3.ZERO);

        switch (state()) {
            case SLEEPING -> {
                Vec3 h = SerpentBody.coilHead(home());
                dir = h.subtract(body.at[0]).multiply(1, 0, 1).normalize();
                moveHead(h); // lies at the inner end of its coil, looking out along it
                if (tickCount % 10 == 0 && nearestPlayer(WAKE_RANGE) != null) wakeUp(level);
            }
            case WAKING -> {
                wakeTicks++;
                if (wakeTicks == 15) {
                    level.broadcastEntityEvent(this, EVENT_ROAR);
                    playSound(SoundEvents.ENDER_DRAGON_GROWL, 4.0F, 0.6F);
                }
                steer(headCenter().add(new Vec3(0, 1, 0.35).normalize()), 0.22, 0.2);
                if (wakeTicks >= WAKE_TICKS) {
                    setState(AWAKE);
                    cooldown = 30;
                }
            }
            case TRANSFORM -> transformTick(level);
            default -> {
                // the air shimmers over its flames and round it
                if (tickCount % 100 == 0) ScreenFx.playOnNear(this, 72, Anchor.ENTITY, 0, ScreenEffect.HEAT, 110, phase() == 2 ? 1.0F : 0.6F, -1);
                fight(level);
                contact(level);
            }
        }
    }

    // ------------------------------------------------------------------ the change to the second phase

    private void startTransform(ServerLevel level) {
        Telegraphs.cancel(this);
        walls.clear();
        shocks.clear();
        act = Act.ROAM;
        actTick = 0;
        transformTicks = 0;
        entityData.set(PHASE, (byte) 2);
        setState(TRANSFORM);
        bossBar.setColor(BossEvent.BossBarColor.WHITE);
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 5.0F, 0.4F);
        // the world drains of colour but for it, a haze about it, the picture tearing as it rises
        ScreenFx.playOnNear(this, 80, Anchor.EXCEPT, 0, ScreenEffect.DESATURATE, TRANSFORM_TICKS + 5, 1.0F, -1);
        ScreenFx.playOnNear(this, 80, Anchor.ENTITY, 0, ScreenEffect.AURA, TRANSFORM_TICKS, 1.2F, -1);
        ScreenFx.playNear(level, home(), 80, ScreenEffect.ABERRATION, TRANSFORM_TICKS, 0.45F, -1);
        ScreenFx.shake(level, home(), 80, TRANSFORM_TICKS, 0.3F);
    }

    /** It rises over the room, coils into a ring, turns white from head to tail and bursts. */
    private void transformTick(ServerLevel level) {
        int t = transformTicks++;
        Vec3 c = home().add(0, 12, 0);
        if (t < WHITEN_START) {
            steer(c.add(Math.cos(t * 0.2) * 6, 0, Math.sin(t * 0.2) * 6), 1.0, 0.25);
        } else {
            double a = t * 0.32;
            steer(c.add(Math.cos(a) * 7, Math.sin(t * 0.15) * 1.5, Math.sin(a) * 7), 2.0, 0.45);
        }
        if (t == WHITEN_START) {
            level.broadcastEntityEvent(this, EVENT_ROAR);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 5.0F, 0.4F);
            // as the whiteness runs down it: a white outline, the picture glitching, the screen ringing with it
            ScreenFx.playOnNear(this, 80, Anchor.ENTITY, 0, ScreenEffect.OUTLINE, TRANSFORM_TICKS - WHITEN_START, 1.3F, 0xFFFFFF);
            ScreenFx.playNear(level, home(), 80, ScreenEffect.GLITCH, (SerpentBody.SEGMENTS + 1) * 2 + 6, 0.55F, -1);
            ScreenFx.playNear(level, home(), 80, ScreenEffect.NOISE, (SerpentBody.SEGMENTS + 1) * 2 + 6, 0.5F, -1);
        }
        int whitenEnd = WHITEN_START + (SerpentBody.SEGMENTS + 1) * 2;
        if (t >= WHITEN_START && t <= whitenEnd && t % 4 == 0) {
            playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 0.5F + (t - WHITEN_START) / 60.0F);
        }
        if (t == whitenEnd + 6) {
            fx(level, FireFxPayload.VOID_BURST, headCenter(), 10.0F, 0, 0, 0);
            Vec3 floor = home();
            for (int i = 0; i < 3; i++) fx(level, FireFxPayload.VOID_RING, floor, 8 + i * 8, 0, i * 3, 10);
            for (Player p : fighters(ROOM_R + 10)) {
                Vec3 push = p.position().subtract(home()).multiply(1, 0, 1);
                push = push.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : push.normalize();
                p.setDeltaMovement(push.x * 1.6, 0.6, push.z * 1.6);
                p.hurtMarked = true;
            }
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 5.0F, 0.5F);
            playSound(SoundEvents.WITHER_SPAWN, 4.0F, 0.7F);
            // it bursts: a white flash, the world turned inside out but for it, the ground heaving
            ScreenFx.playNear(level, home(), 80, ScreenEffect.FLASH, 22, 1.0F, 0xFFFFFF);
            ScreenFx.playOnNear(this, 80, Anchor.EXCEPT, 0, ScreenEffect.INVERT, 10, 1.0F, -1);
            ScreenFx.playOnNear(this, 80, Anchor.AREA, 14, ScreenEffect.DISTORT, 30, 1.5F, -1);
            ScreenFx.shake(level, home(), 80, 24, 1.0F);
        }
        if (t >= TRANSFORM_TICKS) {
            setState(AWAKE);
            act = Act.ROAM;
            cooldown = 20;
        }
    }

    private void wakeUp(ServerLevel level) {
        if (state() != SLEEPING) return;
        setState(WAKING);
        wakeTicks = 0;
        dir = new Vec3(0, 1, 0.35).normalize();
        playSound(SoundEvents.ENDERMAN_STARE, 3.0F, 0.5F);
    }

    /** Turns the head towards a point (by `turn`, 0..1 a tick) and moves it on at `speed` blocks a tick. */
    private void steer(Vec3 goal, double speed, double turn) {
        Vec3 want = goal.subtract(headCenter());
        if (want.lengthSqr() > 1.0E-4) dir = dir.add(want.normalize().subtract(dir).scale(turn)).normalize();
        moveHead(headCenter().add(dir.scale(speed)));
    }

    private void moveHead(Vec3 center) {
        setPos(center.x, center.y - HEAD_HALF, center.z);
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        setXRot(pitch);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    /** Puts the head somewhere else at once, looking along `look` (the body comes out after it). */
    private void teleportHead(Vec3 center, Vec3 look) {
        dir = look.normalize();
        moveHead(center);
        body.layOut(center, dir.scale(-1));
    }

    private void fx(ServerLevel level, byte kind, Vec3 at, float a, float b, int delay, int duration) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 96, new FireFxPayload(kind, at.x, at.y, at.z, a, b, delay, duration));
    }

    private void updateBossBar(ServerLevel level) {
        for (ServerPlayer p : List.copyOf(bossBar.getPlayers())) {
            if (state() == SLEEPING || !p.isAlive() || p.level() != level || p.distanceToSqr(this) > 80 * 80) bossBar.removePlayer(p);
        }
        if (state() == SLEEPING) return;
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(this) < 64 * 64) bossBar.addPlayer(p);
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

    /** Dying (6 s): it roars, and its body bursts in the void segment by segment, from the tail to the head. */
    public static final int DEATH_TICKS = 120;

    @Override
    protected void tickDeath() {
        dying++;
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 c = getBoundingBox().getCenter();
        int t = dying;
        if (t == 1) {
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 5.0F, 0.5F);
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.ENDER_DRAGON_DEATH, 4.0F, 0.6F);
            BossDeath.screen(this, com.divinis.screenfx.ScreenEffect.INVERT, 6, 1.0F, -1);
            BossDeath.screen(this, com.divinis.screenfx.ScreenEffect.GLITCH, DEATH_TICKS, 0.35F, -1);
        }
        // its body bursts segment by segment, from the tail to the head
        for (int i = 0; i < SerpentBody.SEGMENTS; i++) {
            if (t != burstTick(i)) continue;
            Vec3 p = body.lerp(i, 1.0F);
            BossDeath.sphere(level, net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH, p, 18, 0.25);
            BossDeath.sphere(level, net.minecraft.core.particles.ParticleTypes.END_ROD, p, 8, 0.3);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 20, 1.0, 1.0, 1.0, 0.1);
            level.playSound(null, p.x, p.y, p.z, net.minecraft.sounds.SoundEvents.CHORUS_FRUIT_TELEPORT, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F,
                    0.5F + (SerpentBody.SEGMENTS - i) * 0.05F);
            if (i % 4 == 0) BossDeath.shake(this, 6, 0.35F);
        }
        if (t == DEATH_TICKS - 6) BossDeath.screen(this, com.divinis.screenfx.ScreenEffect.FLASH, 14, 0.9F, 0xC080FF);
        if (t == DEATH_TICKS) {   // the head, last
            BossDeath.screen(this, com.divinis.screenfx.ScreenEffect.INVERT, 10, 1.0F, -1);
            BossDeath.screen(this, com.divinis.screenfx.ScreenEffect.DISTORT, 20, 0.8F, -1);
            BossDeath.shake(this, 30, 1.0F);
            BossDeath.sphere(level, net.minecraft.core.particles.ParticleTypes.END_ROD, c, 100, 0.8);
            BossDeath.sphere(level, net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH, c, 60, 0.5);
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN, 4.0F, 0.7F);
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.5F);
        }
        if (t >= DEATH_TICKS) remove(RemovalReason.KILLED);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) Telegraphs.cancel(this);
        super.die(source);
    }

    @Nullable
    private Player nearestPlayer(double range) {
        Player best = null;
        double bestD = range * range;
        for (Player p : level().players()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            double d = p.distanceToSqr(headCenter());
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

    private boolean enraged() {
        return getHealth() < getMaxHealth() * 0.4F;
    }

    private float damageScale() {
        var attr = getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null || attr.getBaseValue() <= 0 ? 1.0F : (float) (attr.getValue() / attr.getBaseValue());
    }

    private double groundBelow(Vec3 at) {
        BlockPos.MutableBlockPos p = BlockPos.containing(at.x, at.y + 2, at.z).mutable();
        for (int i = 0; i < 24; i++) {
            BlockState s = level().getBlockState(p);
            if (!s.getCollisionShape(level(), p).isEmpty()) return p.getY() + s.getCollisionShape(level(), p).max(Direction.Axis.Y);
            p.move(0, -1, 0);
        }
        return at.y;
    }

    private Vec3 onGround(Vec3 at) {
        return new Vec3(at.x, groundBelow(at), at.z);
    }

    /** Its head and body hurt whoever they touch (each player no more than once a second). */
    private void contact(ServerLevel level) {
        for (Player p : level.players()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive() || p.distanceToSqr(this) > 60 * 60) continue;
            // inside its coil only the burning grid hurts, not the body wrapped round it
            if (tickCount < coilSafeUntil && p.position().subtract(coilCenter).horizontalDistance() < COIL_RADIUS + 1.5) continue;
            Integer last = touched.get(p.getUUID());
            if (last != null && tickCount - last < 20) continue;
            float damage = 0;
            if (getBoundingBox().inflate(0.3).intersects(p.getBoundingBox())) damage = HEAD_DAMAGE;
            else {
                for (SerpentPart part : parts) {
                    if (part.getBoundingBox().intersects(p.getBoundingBox())) {
                        damage = BODY_DAMAGE;
                        break;
                    }
                }
            }
            if (damage <= 0) continue;
            touched.put(p.getUUID(), tickCount);
            if (phase() == 2) damage *= 1.25F;
            if (p.hurt(damageSources().mobAttack(this), damage * damageScale())) {
                Vec3 push = p.position().subtract(headCenter()).multiply(1, 0, 1).normalize().scale(0.8);
                p.setDeltaMovement(p.getDeltaMovement().add(push.x, 0.4, push.z));
                p.hurtMarked = true;
            }
        }
    }

    // ------------------------------------------------------------------ the fight

    private void fight(ServerLevel level) {
        // a new target only between attacks (one under way keeps its aim)
        if (act == Act.ROAM || getTarget() == null || !getTarget().isAlive() || getTarget().distanceToSqr(this) > AGGRO * AGGRO
                || getTarget() instanceof Player tp && (tp.isCreative() || tp.isSpectator())) {
            setTarget(targeting.pick(this, home(), AGGRO, getTarget()));
        }
        LivingEntity target = getTarget();
        tickWalls(level);
        tickShocks(level);
        actTick++;
        switch (act) {
            case WYRMS -> wyrms(level);
            case WALLS -> wallsAct(level);
            case STAR -> starDash(level, target);
            case STARFALL -> starfall(level, target);
            case DASH -> dash(level, target);
            case ERUPT -> erupt(level, target);
            case COIL -> coil(level);
            case GRID -> grid(level);
            case RIFT -> rift(level);
            default -> roam(level, target);
        }
    }

    private void begin(Act a) {
        act = a;
        actTick = 0;
        actStep = 0;
    }

    private void done() {
        act = Act.ROAM;
        actTick = 0;
        cooldown = phase() == 2 ? 18 + random.nextInt(15) : (enraged() ? 25 : 45) + random.nextInt(20);
    }

    private void roam(ServerLevel level, @Nullable LivingEntity target) {
        Vec3 center = target != null ? target.position() : home();
        roamAngle += enraged() ? 0.05 : 0.035;
        Vec3 goal = center.add(Math.cos(roamAngle) * 12, 4 + Math.sin(tickCount * 0.05) * 3, Math.sin(roamAngle) * 12);
        steer(goal, (enraged() ? 1.05 : 0.85) * (phase() == 2 ? 1.15 : 1), 0.1);
        if (target == null) return;
        if (--gridTimer <= 0) {
            gridTimer = GRID_EVERY;
            begin(Act.GRID);
            level.broadcastEntityEvent(this, EVENT_ROAR);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 4.0F, 0.5F);
            return;
        }
        if (--cooldown > 0) return;
        float r = random.nextFloat();
        if (phase() == 2 && r < 0.55F) {
            float q = random.nextFloat();
            if (q < 0.25F && wyrmlings() < 3) {
                begin(Act.WYRMS);
                toSpawn = Math.min(MAX_WYRMLINGS - wyrmlings(), 2 + fighters(ROOM_R + 10).size());
                spawned = 0;
            } else if (q < 0.5F) {
                begin(Act.WALLS);
                wallsLaunched = 0;
            } else if (q < 0.75F) {
                begin(Act.STAR);
            } else {
                begin(Act.STARFALL);
            }
            level.broadcastEntityEvent(this, EVENT_ROAR);
            return;
        }
        if (r < 0.32F) {
            begin(Act.DASH);
            steps = enraged() ? 6 : 4;
        } else if (r < 0.58F) {
            begin(Act.ERUPT);
            steps = Math.min(3, Math.max(1, fighters(ROOM_R + 10).size()));
        } else if (r < 0.8F) {
            begin(Act.COIL);
            spot = target.position();
        } else {
            begin(Act.RIFT);
            double a = random.nextDouble() * Math.PI * 2;
            spot = onGround(target.position().add(Math.cos(a) * 4, 0, Math.sin(a) * 4));
        }
    }

    // ---- rift dash: out of one side of the room, across, into the other

    private void dash(ServerLevel level, @Nullable LivingEntity target) {
        int t = actTick - 1;
        double length = ROOM_R * 2 + 6;
        int run = (int) Math.ceil(length / DASH_SPEED);
        if (t == 0) {
            if (target == null) {
                done();
                return;
            }
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 from = home().add(Math.cos(a) * (ROOM_R + 3), 0, Math.sin(a) * (ROOM_R + 3)); // inside the wall
            from = new Vec3(from.x, target.getY() + 1.2, from.z);
            laneDir = target.position().subtract(from).multiply(1, 0, 1).normalize();
            teleportHead(from, laneDir);
            float yaw = (float) (Mth.atan2(laneDir.z, laneDir.x) * Mth.RAD_TO_DEG) - 90.0F;
            Vec3 floor = new Vec3(from.x, onGround(target.position()).y, from.z);
            Telegraphs.show(level, Telegraph.rect(floor, yaw, (float) length, 2.0F), DASH_PREP, run, this, null);
            fx(level, FireFxPayload.VOID_BURST, from, 4.0F, 0, 0, 0);
            playSound(SoundEvents.ENDERMAN_TELEPORT, 3.0F, 0.5F);
        } else if (t < DASH_PREP) {
            moveHead(headCenter()); // waits in the rift
        } else if (t == DASH_PREP) {
            level.broadcastEntityEvent(this, EVENT_BITE);
            ScreenFx.shake(level, headCenter(), 30, 8, 0.4F);
            ScreenFx.playOnNear(this, 40, Anchor.AREA, 8, ScreenEffect.ABERRATION, 14, 1.2F, -1);
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 4.0F, 0.5F);
        }
        if (t >= DASH_PREP) {
            dir = laneDir;
            moveHead(headCenter().add(laneDir.scale(DASH_SPEED)));
            if (t % 3 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 10, 1, 1, 1, 0.05);
        }
        if (t >= DASH_PREP + run) {
            actTick = 0;
            if (++actStep >= steps) done();
        }
    }

    // ---- eruption: up from under a player, jaws first

    private void erupt(ServerLevel level, @Nullable LivingEntity target) {
        int t = actTick - 1;
        if (t == 0) {
            List<Player> ps = fighters(ROOM_R + 10);
            LivingEntity who = ps.isEmpty() ? target : ps.get(actStep % ps.size());
            if (who == null) {
                done();
                return;
            }
            spot = onGround(who.position());
            Telegraphs.show(level, Telegraph.circle(spot, ERUPT_RADIUS), ERUPT_WARN, 6, this, e -> {
                if (e.hurt(damageSources().mobAttack(this), ERUPT_DAMAGE * damageScale())) {
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 1.3, 0));
                    e.hurtMarked = true;
                }
            });
            teleportHead(spot.add(0, -9, 0), new Vec3(0, 1, 0));
            playSound(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 0.4F);
        } else if (t < ERUPT_WARN - 7) {
            moveHead(headCenter());
        } else if (t < ERUPT_WARN + 8) {
            dir = new Vec3(0, 1, 0);
            moveHead(headCenter().add(0, 1.45, 0));
            if (t == ERUPT_WARN - 3) level.broadcastEntityEvent(this, EVENT_BITE);
            if (t == ERUPT_WARN) {
                fx(level, FireFxPayload.VOID_BURST, spot, 5.0F, 0, 0, 0);
                ScreenFx.shake(level, spot, 20, 12, 0.7F);
                playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.6F);
                playSound(SoundEvents.EVOKER_FANGS_ATTACK, 3.0F, 0.5F);
            }
        } else {
            // arcs over and dives back into the ground
            steer(spot.add(dir.x * 4 + 4, -12, dir.z * 4), 1.1, 0.18);
        }
        if (t >= ERUPT_STEP) {
            actTick = 0;
            if (++actStep >= steps) done();
        }
    }

    // ---- coil: round a player, then the grid inside burns

    private void coil(ServerLevel level) {
        int t = actTick - 1;
        Vec3 center = spot;
        double angle = t * (Math.PI * 3) / COIL_TICKS; // one and a half turns
        if (t == 0) {
            Vec3 g = onGround(center);
            Telegraphs.show(level, Telegraph.lattice(g, COIL_RADIUS, COIL_SPACING, COIL_LINE), COIL_TICKS, 6, this, e -> {
                if (e.hurt(damageSources().mobAttack(this), COIL_DAMAGE * damageScale())) e.igniteForSeconds(2);
            });
            fx(level, FireFxPayload.VOID_LATTICE, g, COIL_RADIUS, COIL_SPACING, COIL_TICKS, 12);
            coilCenter = center;
            coilSafeUntil = tickCount + COIL_TICKS + 40; // and a moment after, while the body slides off
            teleportHead(center.add(COIL_RADIUS + 1, 1.2, 0), new Vec3(0, 0, 1));
            fx(level, FireFxPayload.VOID_BURST, headCenter(), 3.0F, 0, 0, 0);
            playSound(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 0.6F);
        }
        Vec3 on = center.add(Math.cos(angle) * (COIL_RADIUS + 1), 1.2, Math.sin(angle) * (COIL_RADIUS + 1));
        dir = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
        moveHead(on);
        if (t == COIL_TICKS) {
            level.broadcastEntityEvent(this, EVENT_BITE);
            playSound(SoundEvents.FIRECHARGE_USE, 3.0F, 0.5F);
        }
        if (t >= COIL_TICKS + 10) done();
    }

    // ---- void grid: the whole room burns but for the squares

    private void grid(ServerLevel level) {
        int t = actTick - 1;
        Vec3 g = onGround(home());
        if (t == 0) {
            Telegraphs.sweep(level, Telegraph.lattice(g, (float) ROOM_R + 4, GRID_SPACING, GRID_LINE), GRID_WARN, GRID_BURN, 0, 10, this, e -> {
                if (e.hurt(damageSources().mobAttack(this), GRID_DAMAGE * damageScale())) e.igniteForSeconds(3);
            });
            fx(level, FireFxPayload.VOID_LATTICE, g, (float) ROOM_R + 4, GRID_SPACING, GRID_WARN, GRID_BURN);
        }
        if (t == GRID_WARN) playSound(SoundEvents.BLAZE_SHOOT, 4.0F, 0.4F);
        double a = t * 0.04;
        steer(home().add(Math.cos(a) * 14, 14, Math.sin(a) * 14), 0.9, 0.12);
        if (t >= GRID_WARN + GRID_BURN + 10) done();
    }

    // ---- gravity rift: everyone dragged in, then it collapses

    private void rift(ServerLevel level) {
        int t = actTick - 1;
        if (t == 0) {
            Telegraphs.show(level, Telegraph.circle(spot, RIFT_RADIUS), RIFT_TICKS, 8, this, e -> {
                if (e.hurt(damageSources().mobAttack(this), RIFT_DAMAGE * damageScale())) {
                    Vec3 push = e.position().subtract(spot).multiply(1, 0, 1).normalize().scale(1.4);
                    e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.6, push.z));
                    e.hurtMarked = true;
                }
            });
            fx(level, FireFxPayload.RIFT, spot, 6.0F, 0, 0, RIFT_TICKS);
            level.broadcastEntityEvent(this, EVENT_ROAR);
            playSound(SoundEvents.PORTAL_TRIGGER, 3.0F, 0.6F);
        }
        if (t < RIFT_TICKS) {
            // a player's motion is their own client's: every few ticks it is set to a drag towards the rift, which
            // they can walk (or better, sprint) away from
            if (t % 3 == 0) {
                for (Player p : fighters(ROOM_R + 20)) {
                    Vec3 to = spot.subtract(p.position()).multiply(1, 0, 1);
                    double d = to.length();
                    if (d < 0.5 || d > RIFT_PULL) continue;
                    p.setDeltaMovement(to.scale(0.2 / d));
                    p.hurtMarked = true;
                }
            }
            if (t % 10 == 0) playSound(SoundEvents.PORTAL_AMBIENT, 3.0F, 0.5F);
        }
        if (t == RIFT_TICKS) {
            fx(level, FireFxPayload.VOID_BURST, spot.add(0, 1, 0), 8.0F, 0, 0, 0);
            ScreenFx.shake(level, spot, 30, 14, 0.8F);
            ScreenFx.playNear(level, spot, 30, ScreenEffect.FLASH, 8, 0.6F, 0xC090FF);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.5F);
        }
        double a = t * 0.06;
        steer(spot.add(Math.cos(a) * 11, 3, Math.sin(a) * 11), 0.9, 0.15);
        if (t >= RIFT_TICKS + 10) done();
    }

    // ---- wyrmlings: small flying worms come out of its body

    private int wyrmlings() {
        return level().getEntitiesOfClass(VoidWyrmling.class, new AABB(BlockPos.containing(home())).inflate(80)).size();
    }

    private void wyrms(ServerLevel level) {
        int t = actTick - 1;
        if (t == 0) playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 4.0F, 0.6F);
        double a = t * 0.05;
        steer(home().add(Math.cos(a) * 10, 9, Math.sin(a) * 10), 0.9, 0.15);
        if (t >= 10 && t % 5 == 0 && spawned < toSpawn) {
            List<Player> ps = fighters(ROOM_R + 10);
            if (!ps.isEmpty()) {
                Vec3 at = body.at[(spawned * 4 + 2) % SerpentBody.SEGMENTS];
                VoidWyrmling.spawn(level, this, at, ps.get(spawned % ps.size()), damageScale());
                fx(level, FireFxPayload.VOID_BURST, at, 2.5F, 0, 0, 0);
                playSound(SoundEvents.PHANTOM_FLAP, 3.0F, 1.4F);
            }
            spawned++;
        }
        if (t >= 10 + toSpawn * 5 + 10) done();
    }

    // ---- void walls: walls of fire with a gap sweep across the room

    private void wallsAct(ServerLevel level) {
        int t = actTick - 1;
        // the next wall only when the last is three quarters across: by the time it arrives the last is gone
        Wall last = walls.isEmpty() ? null : walls.get(walls.size() - 1);
        boolean ready = last == null || tickCount - last.start() >= WALL_WARN + WALL_TRAVEL * 3 / 4;
        if (wallsLaunched < WALLS && ready) {
            float yaw = random.nextFloat() * 360.0F;
            if (last != null) yaw = (float) (Mth.atan2(-last.d().x, last.d().z) * Mth.RAD_TO_DEG) + 90.0F + random.nextFloat() * 40 - 20;
            launchWall(level, yaw, getTarget());
            wallsLaunched++;
        }
        double a = t * 0.04;
        steer(home().add(Math.cos(a) * 14, 16, Math.sin(a) * 14), 0.9, 0.12);
        if (wallsLaunched >= WALLS && walls.isEmpty()) done(); // no other attack while walls are crossing
    }

    private void launchWall(ServerLevel level, float yaw, @Nullable LivingEntity target) {
        float y = yaw * Mth.DEG_TO_RAD;
        Vec3 d = new Vec3(-Mth.sin(y), 0, Mth.cos(y));
        Vec3 side = new Vec3(-d.z, 0, d.x);
        // the gap a few blocks from where its target stands now, so it can be reached
        double near = target != null ? target.position().subtract(home()).dot(side) : 0;
        float gap = (float) Mth.clamp(near + (random.nextDouble() * 2 - 1) * WALL_GAP_SPREAD, -(ROOM_R - 4), ROOM_R - 4);
        walls.add(new Wall(d, side, gap, tickCount, new java.util.HashSet<>()));
        fx(level, FireFxPayload.VOID_WALL, home(), yaw, gap, 0, WALL_TRAVEL);
        playSound(SoundEvents.BLAZE_SHOOT, 4.0F, 0.4F);
    }

    /** The walls on their way: whoever they pass outside the gap burns (once a wall). */
    private void tickWalls(ServerLevel level) {
        if (walls.isEmpty()) return;
        Vec3 h = home();
        walls.removeIf(w -> tickCount - w.start() > WALL_WARN + WALL_TRAVEL);
        for (Wall w : walls) {
            int age = tickCount - w.start();
            if (age < WALL_WARN) continue;
            double at = wallAt(age);
            for (Player p : fighters(ROOM_R + 10)) {
                if (w.hit().contains(p.getUUID())) continue;
                Vec3 rel = p.position().subtract(h);
                double along = rel.dot(w.d()), side = rel.dot(w.side());
                if (Math.abs(along - at) > 0.9 || Math.abs(side - w.gap()) <= WALL_GAP || Math.abs(side) > WALL_HALF_LENGTH) continue;
                if (p.getY() > h.y + 2.2) continue; // over it (only from up high)
                w.hit().add(p.getUUID());
                if (p.hurt(damageSources().mobAttack(this), WALL_DAMAGE * damageScale())) p.igniteForSeconds(3);
            }
        }
    }

    // ---- star dash: a pentagram cut through the room, the lines burning after it

    private int laneTicks() {
        double chord = 2 * STAR_R * Math.sin(Math.toRadians(72));
        return (int) Math.ceil(chord / STAR_SPEED);
    }

    private void starDash(ServerLevel level, @Nullable LivingEntity target) {
        int t = actTick - 1;
        int lane = laneTicks();
        if (t == 0) {
            if (target == null) {
                done();
                return;
            }
            // the star round the target, its first line right through them
            double base = random.nextDouble() * Math.PI * 2;
            double mid = base + Math.PI * 2 / 5; // the first line (point 0 to point 2) is nearest the middle here
            Vec3 toLine = new Vec3(Math.cos(mid), 0, Math.sin(mid)).scale(STAR_R * Math.cos(Math.toRadians(72)));
            Vec3 center = new Vec3(target.getX(), home().y, target.getZ()).subtract(toLine);
            for (int k = 0; k < 5; k++) {
                double a = base + k * Math.PI * 2 / 5;
                star[k] = center.add(Math.cos(a) * STAR_R, 1.4, Math.sin(a) * STAR_R);
            }
            teleportHead(star[0], star[2].subtract(star[0]));
            fx(level, FireFxPayload.VOID_BURST, star[0], 4.0F, 0, 0, 0);
            playSound(SoundEvents.ENDERMAN_TELEPORT, 3.0F, 0.5F);
            for (int k = 0; k < 5; k++) {
                Vec3 from = star[STAR_ORDER[k]], to = star[STAR_ORDER[k + 1]];
                Vec3 d = to.subtract(from);
                float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
                Vec3 floor = new Vec3(from.x, home().y, from.z);
                int passes = STAR_WARN + (k + 1) * lane;
                Telegraph strip = Telegraph.rect(floor, yaw, (float) d.horizontalDistance(), 2.0F);
                Telegraphs.show(level, strip, passes, 0, this, null);
                Telegraphs.sweep(level, strip, passes, 60, 0, 10, this, e -> {
                    if (e.hurt(damageSources().mobAttack(this), TRAIL_DAMAGE * damageScale())) e.igniteForSeconds(2);
                });
                fx(level, FireFxPayload.VOID_LINE, floor, yaw, (float) d.horizontalDistance(), STAR_WARN + k * lane, lane + 60);
            }
        }
        if (t < STAR_WARN) {
            moveHead(headCenter());
            if (t == 8) level.broadcastEntityEvent(this, EVENT_ROAR);
            return;
        }
        int k = (t - STAR_WARN) / lane;
        if (k >= 5) {
            done();
            return;
        }
        if (k != starLane || t == STAR_WARN) {
            starLane = k;
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 4.0F, 0.6F);
        }
        Vec3 from = star[STAR_ORDER[k]], to = star[STAR_ORDER[k + 1]];
        double frac = ((t - STAR_WARN) % lane + 1) / (double) lane;
        dir = to.subtract(from).normalize();
        moveHead(from.lerp(to, frac));
    }

    // ---- starfall: stars rain down, then it falls from the sky itself

    private void starfall(ServerLevel level, @Nullable LivingEntity target) {
        int t = actTick - 1;
        if (t == 0) playSound(SoundEvents.WITHER_AMBIENT, 4.0F, 0.5F);
        if (t < 20) {
            steer(home().add(0, 45, 0), 2.2, 0.3);
        } else if (t < 120) {
            double a = t * 0.08;
            steer(home().add(Math.cos(a) * 8, 45, Math.sin(a) * 8), 1.2, 0.2);
            if (t % 4 == 0) meteor(level);
        } else if (t == 120) {
            List<Player> ps = fighters(ROOM_R + 10);
            Vec3 at = target != null ? target.position() : ps.isEmpty() ? home() : ps.get(0).position();
            spot = onGround(at);
            Telegraphs.show(level, Telegraph.circle(spot, CRASH_RADIUS), 40, 6, this, e -> {
                if (e.hurt(damageSources().mobAttack(this), CRASH_DAMAGE * damageScale())) {
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 1.2, 0));
                    e.hurtMarked = true;
                }
            });
            teleportHead(spot.add(0, 41.3, 0), new Vec3(0, -1, 0));
        } else if (t < 140) {
            moveHead(headCenter());
            if (t == 125) {
                level.broadcastEntityEvent(this, EVENT_ROAR);
                playSound(SoundEvents.ENDER_DRAGON_GROWL, 5.0F, 0.5F);
            }
        } else if (t <= 175) {
            dir = new Vec3(0, -1, 0);
            moveHead(headCenter().add(0, -2, 0));
            if (t == 160) {
                fx(level, FireFxPayload.VOID_BURST, spot.add(0, 1, 0), 10.0F, 0, 0, 0);
                ScreenFx.shake(level, spot, 40, 18, 1.0F);
                ScreenFx.playNear(level, spot, 40, ScreenEffect.FLASH, 10, 0.5F, 0xD8B0FF);
                playSound(SoundEvents.GENERIC_EXPLODE.value(), 5.0F, 0.5F);
                for (int k = 0; k < SHOCKS; k++) {
                    shocks.add(new Shock(spot, tickCount + k * SHOCK_EVERY, new java.util.HashSet<>()));
                    fx(level, FireFxPayload.VOID_SHOCK, spot, 0, 0, k * SHOCK_EVERY, (int) ((SHOCK_END - SHOCK_START) / SHOCK_SPEED));
                }
            }
        } else {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 out = home().add(Math.cos(a) * (ROOM_R - 4), -6, Math.sin(a) * (ROOM_R - 4));
            teleportHead(out, new Vec3(0, 1, 0));
            done();
        }
    }

    /** The shock waves rolling out: whoever is on the ground where one is now gets hit (once a wave). */
    private void tickShocks(ServerLevel level) {
        if (shocks.isEmpty()) return;
        shocks.removeIf(s -> shockAt(tickCount - s.start()) > SHOCK_END);
        for (Shock s : shocks) {
            int age = tickCount - s.start();
            if (age < 0) continue;
            double r = shockAt(age);
            if (age % 4 == 0) playSound(SoundEvents.FIRECHARGE_USE, 1.5F, 0.4F);
            for (Player p : fighters(ROOM_R + 10)) {
                if (s.hit().contains(p.getUUID())) continue;
                double d = p.position().subtract(s.center()).horizontalDistance();
                if (Math.abs(d - r) > 0.8) continue;
                if (!p.onGround() || p.getY() > s.center().y + 0.6) continue; // jumped it
                s.hit().add(p.getUUID());
                if (p.hurt(damageSources().mobAttack(this), SHOCK_DAMAGE * damageScale())) {
                    Vec3 push = p.position().subtract(s.center()).multiply(1, 0, 1).normalize();
                    p.setDeltaMovement(p.getDeltaMovement().add(push.x, 0.5, push.z));
                    p.hurtMarked = true;
                }
            }
        }
    }

    private void meteor(ServerLevel level) {
        List<Player> ps = fighters(ROOM_R + 10);
        Vec3 at;
        if (!ps.isEmpty() && random.nextFloat() < 0.6F) {
            Player p = ps.get(random.nextInt(ps.size()));
            at = p.position().add((random.nextDouble() * 2 - 1) * 2.5, 0, (random.nextDouble() * 2 - 1) * 2.5);
        } else {
            double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * (ROOM_R - 2);
            at = home().add(Math.cos(a) * r, 0, Math.sin(a) * r);
        }
        at = onGround(at);
        Telegraphs.show(level, Telegraph.circle(at, METEOR_RADIUS), METEOR_FALL, 4, this, e -> {
            if (e.hurt(damageSources().mobAttack(this), METEOR_DAMAGE * damageScale())) e.igniteForSeconds(2);
        });
        fx(level, FireFxPayload.METEOR, at, 0, 0, METEOR_FALL, 0);
    }

    // ------------------------------------------------------------------ damage, sounds, saving

    boolean hurtPart(SerpentPart part, DamageSource source, float amount) {
        return hurt(source, amount * 0.5F); // the armoured body takes half
    }

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
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (state() == WAKING || state() == TRANSFORM) return false;
        if (state() == SLEEPING && level() instanceof ServerLevel level) wakeUp(level);
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
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /**
     * Client: its position comes every tick (update interval 1) and is taken as it is, no glide (the usual three-step
     * glide lags it far behind at dash speed). Packets do not arrive evenly - two in one tick, then none - so a tick
     * without one carries it on by the last step (for up to two ticks): no stutter at speed. A jump (a teleport)
     * shows at once, so the body comes out after the head.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        Vec3 to = new Vec3(x, y, z);
        netStep = netTarget != null && to.distanceToSqr(netTarget) < 49 ? to.subtract(netTarget) : Vec3.ZERO;
        if (netTarget == null || to.distanceToSqr(position()) > 49) {
            setPos(x, y, z);
            xo = xOld = x;
            yo = yOld = y;
            zo = zOld = z;
            yRotO = yRot;
            xRotO = xRot;
        }
        netTarget = to;
        netFresh = true;
        setYRot(yRot);
        setXRot(xRot);
    }

    private void followNet() {
        if (netTarget == null) return;
        if (netFresh) netMissed = 0;
        else if (++netMissed <= 2) netTarget = netTarget.add(netStep);
        netFresh = false;
        setPos(netTarget);
    }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean isInWall() {
        return false; // it lives in the walls
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
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
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Awake", state() != SLEEPING);
        tag.putByte("Phase", (byte) phase());
        BlockPos h = entityData.get(HOME);
        tag.putIntArray("Home", new int[] {h.getX(), h.getY(), h.getZ()});
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setState(tag.getBoolean("Awake") ? AWAKE : SLEEPING);
        if (tag.getByte("Phase") == 2) {
            entityData.set(PHASE, (byte) 2);
            bossBar.setColor(BossEvent.BossBarColor.WHITE);
        }
        int[] h = tag.getIntArray("Home");
        if (h.length == 3) entityData.set(HOME, new BlockPos(h[0], h[1], h[2]));
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    @Override
    public void setCustomName(@Nullable net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }
}
