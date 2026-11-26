package com.divinis.boss;

import java.util.List;

import javax.annotation.Nullable;

import com.divinis.registry.ModEntities;
import com.divinis.util.Msg;

import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenEffect.Anchor;
import com.divinis.screenfx.ScreenFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.CriticalHitEvent;

/**
 * The Wildfire (/summon divinis:wildfire): a floating great helm wrapped in fire, four shields circling it. Asleep it
 * is a heap of helm and shields on the ground; when a player comes close or strikes it, the heap trembles, rises, the
 * shields swing up into their orbit and the fire lights. It floats about its target and fights with:
 * - Fireball: hurls a ball of fire at the spot its target stands on (marked on the ground); it bursts there, hard,
 *   and sets everyone caught on fire. Enraged, it throws one at up to three players.
 * - Burning wheel: its shields fly far out and four beams of fire, 20 blocks long, are marked on the ground; after a
 *   moment they burn and wheel round it for 6 seconds (one way or the other) with the shields - keep walking round
 *   the Wildfire ahead of them.
 * - Inferno: it sinks to the middle and charges a blast over the whole room (a huge red field that fills for 12 s,
 *   very hard when it goes off). Its shields are what feed it: critical hits break them one after the other (more
 *   hits each for bigger parties). With all four broken the blast is called off and it falls, stunned and taking
 *   more damage, until its shields come back from below and spiral into their orbit again.
 * Below 40% health it is enraged: it moves faster and rests less between attacks.
 * The timings match the animations in client/WildfireModelData (tools/models/build_wildfire.py).
 */
public class Wildfire extends Monster implements BossTargeting.Marked {
    public static final byte SLEEPING = 0, WAKING = 1, AWAKE = 2, STUNNED = 3;
    public static final int MAX_SHIELDS = 4;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Wildfire.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(Wildfire.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> SHIELDS = SynchedEntityData.defineId(Wildfire.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> CHANNEL = SynchedEntityData.defineId(Wildfire.class, EntityDataSerializers.BOOLEAN);

    private enum Attack {
        NONE(0), FIREBALL(28), SWEEP(150);

        final int length;

        Attack(int length) {
            this.length = length;
        }
    }

    private static final byte EVENT_ATTACK = 125; // + attack ordinal (vanilla to 65, Minotaur 110+, Golem 120+)
    private static final byte EVENT_REGEN = 124, EVENT_SHIELD_BREAK = 123, EVENT_INFERNO = 122, EVENT_SWEEP_BACK = 121;
    private static final int WAKE_TICKS = 70, WAKE_IGNITE = 56;
    private static final double WAKE_RANGE = 14, AGGRO_RANGE = 48;
    private static final double HOVER = 1.6, CHANNEL_HOVER = 0.6, KEEP_AWAY = 7;

    private static final int FIREBALL_RELEASE = 14;
    private static final float SWEEP_DAMAGE = 10, SWEEP_RADIUS = 20, SWEEP_HALF_ANGLE = 12;
    /** the burning wheel: marked for SWEEP_PREP ticks, then turning SWEEP_SPIN degrees a tick for SWEEP_BURN ticks */
    public static final int SWEEP_PREP = 30, SWEEP_BURN = 120;
    public static final float SWEEP_SPIN = 1.6F;
    private static final int SWEEP_HIT_EVERY = 10;
    private static final float INFERNO_DAMAGE = 45, INFERNO_RADIUS = 32;
    private static final int INFERNO_TICKS = 240, INFERNO_EVERY = 900, INFERNO_FIRST = 400;
    private static final int STUN_TICKS = 40, REGEN_TICKS = 50; // 2 s down, then 2.5 s to rise again: 4.5 s in all

    // client: animations
    public final AnimationState sleepAnimation = new AnimationState();
    public final AnimationState wakeAnimation = new AnimationState();
    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState fireballAnimation = new AnimationState();
    public final AnimationState sweepAnimation = new AnimationState();
    public final AnimationState infernoAnimation = new AnimationState();
    public final AnimationState stunAnimation = new AnimationState();
    public final AnimationState regenAnimation = new AnimationState();
    /** Ticks since it began dying (both sides; its own count, so vanilla neither tips nor reddens it). */
    public int dying;
    public final AnimationState deathAnimation = new AnimationState();
    private int clientAttackTicks;
    private int clientWakeStart = -1;
    private int clientSweepStart = -1, clientSweepDir = 1;

    // server: the fight
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private Attack attack = Attack.NONE;
    private final BossTargeting targeting = new BossTargeting();
    private int attackTick;
    private int cooldown = 30;
    private int wakeTicks;
    private float lockedYaw;
    private int infernoTimer = INFERNO_FIRST;
    private int channelTicks;
    private int shieldHits, hitsPerShield = 2;
    private Vec3 channelAt = Vec3.ZERO;
    private int stunTicks, regenTicks;

    public Wildfire(EntityType<? extends Wildfire> type, Level level) {
        super(type, level);
        this.xpReward = 300;
        setPersistenceRequired();
        bossBar.setVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 350.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FLYING_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 20.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, SLEEPING);
        builder.define(TARGET_ID, -1);
        builder.define(SHIELDS, (byte) MAX_SHIELDS);
        builder.define(CHANNEL, false);
    }

    public byte state() {
        return entityData.get(STATE);
    }

    private void setState(byte s) {
        entityData.set(STATE, s);
    }

    public int shields() {
        return entityData.get(SHIELDS);
    }

    public boolean channeling() {
        return entityData.get(CHANNEL);
    }

    /** Client: is the fire lit? Not while it is a heap, only once it has risen. */
    public boolean burning() {
        return state() == AWAKE || state() == STUNNED || state() == WAKING && clientWakeStart >= 0 && tickCount - clientWakeStart >= WAKE_IGNITE;
    }

    /**
     * Client: how far (degrees) the burning wheel has turned now, or NaN when it is not burning - for the model's
     * shields and the fire along the beams.
     */
    public float sweepTurn(float partialTick) {
        if (clientSweepStart < 0 || clientAttackTicks <= 0) return Float.NaN;
        float t = tickCount + partialTick - clientSweepStart - SWEEP_PREP;
        return clientSweepDir * SWEEP_SPIN * Mth.clamp(t, 0, SWEEP_BURN);
    }

    /** Client: are the beams burning (not only marked)? */
    public boolean sweepBurning(float partialTick) {
        float t = tickCount + partialTick - clientSweepStart - SWEEP_PREP;
        return clientSweepStart >= 0 && clientAttackTicks > 0 && t >= 0 && t < SWEEP_BURN;
    }

    /** Client: an attack animation is playing. */
    public boolean isAttacking() {
        return clientAttackTicks > 0;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions full = super.getDefaultDimensions(pose);
        if (entityData == null) return full;
        return state() == SLEEPING || state() == STUNNED ? EntityDimensions.scalable(full.width(), 1.4F) : full;
    }

    // ------------------------------------------------------------------ client

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STATE.equals(key)) {
            refreshDimensions();
            if (level().isClientSide && state() == WAKING) {
                wakeAnimation.start(tickCount);
                clientWakeStart = tickCount;
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id > EVENT_ATTACK && id < EVENT_ATTACK + Attack.values().length || id == EVENT_SWEEP_BACK) {
            Attack a = id == EVENT_SWEEP_BACK ? Attack.SWEEP : Attack.values()[id - EVENT_ATTACK];
            fireballAnimation.stop();
            sweepAnimation.stop();
            (a == Attack.FIREBALL ? fireballAnimation : sweepAnimation).start(tickCount);
            clientAttackTicks = a.length;
            if (a == Attack.SWEEP) {
                clientSweepStart = tickCount;
                clientSweepDir = id == EVENT_SWEEP_BACK ? -1 : 1;
            }
        } else if (id == EVENT_REGEN) {
            regenAnimation.start(tickCount);
            clientAttackTicks = REGEN_TICKS;
        } else if (id == EVENT_SHIELD_BREAK) {
            com.divinis.client.FireFx.burst(position().add(0, 2, 0), 2.5F);
        } else if (id == EVENT_INFERNO) {
            com.divinis.client.FireFx.wave(position().add(0, -1, 0), INFERNO_RADIUS);
            com.divinis.client.FireFx.burst(position().add(0, 2, 0), 6);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && isDeadOrDying()) deathAnimation.startIfStopped(tickCount);
        if (!level().isClientSide) return;
        byte s = state();
        if (s == SLEEPING) sleepAnimation.startIfStopped(tickCount);
        else sleepAnimation.stop();
        if (s != WAKING) wakeAnimation.stop();
        if (s == STUNNED) stunAnimation.startIfStopped(tickCount);
        else stunAnimation.stop();
        if (channeling()) infernoAnimation.startIfStopped(tickCount);
        else infernoAnimation.stop();
        // the orbit of the idle animation would add to the attacks' own wheeling: off while they play
        if (s == AWAKE && clientAttackTicks <= 0 && !channeling()) idleAnimation.startIfStopped(tickCount);
        else idleAnimation.stop();
        if (clientAttackTicks > 0) clientAttackTicks--;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(4.0); // the shields fly far out
    }

    // ------------------------------------------------------------------ server

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (tickCount % 10 == 0) updateBossBar(level);
        bossBar.setProgress(getHealth() / getMaxHealth());

        switch (state()) {
            case SLEEPING -> {
                setNoGravity(false);
                if (tickCount % 10 == 0 && nearestPlayer(WAKE_RANGE) != null) wakeUp();
            }
            case WAKING -> {
                wakeTicks++;
                setNoGravity(true);
                if (wakeTicks <= 20 && wakeTicks % 4 == 0) playSound(SoundEvents.CHAIN_FALL, 2.0F, 0.6F);
                if (wakeTicks == 20) playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 0.7F);
                if (wakeTicks == WAKE_IGNITE) {
                    playSound(SoundEvents.BLAZE_SHOOT, 3.0F, 0.4F);
                    playSound(SoundEvents.FIRECHARGE_USE, 3.0F, 0.5F);
                }
                if (wakeTicks >= WAKE_TICKS) {
                    setState(AWAKE);
                    cooldown = 20;
                }
            }
            case STUNNED -> stunned(level);
            default -> fight(level);
        }
        // the air shimmers over its fire
        if (state() != SLEEPING && tickCount % 100 == 0) ScreenFx.playOnNear(this, 64, Anchor.ENTITY, 0, ScreenEffect.HEAT, 110, 0.8F, -1);
    }

    private void wakeUp() {
        if (state() != SLEEPING) return;
        setState(WAKING);
        wakeTicks = 0;
        playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 3.0F, 0.5F);
    }

    private void updateBossBar(ServerLevel level) {
        for (ServerPlayer p : List.copyOf(bossBar.getPlayers())) {
            if (state() == SLEEPING || !p.isAlive() || p.level() != level || p.distanceToSqr(this) > 64 * 64) bossBar.removePlayer(p);
        }
        if (state() == SLEEPING) return;
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(this) < 48 * 48) bossBar.addPlayer(p);
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

    /** Dying (4.5 s): its ring spins wild, the shields fall away, the flames gutter, it draws its fire in and bursts in a nova. */
    public static final int DEATH_TICKS = 90;

    @Override
    protected void tickDeath() {
        dying++;
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 c = getBoundingBox().getCenter();
        int t = dying;
        if (t == 1) {
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.BLAZE_DEATH, 4.0F, 0.5F);
            BossDeath.on(this, com.divinis.screenfx.ScreenEffect.Anchor.AREA, 9, com.divinis.screenfx.ScreenEffect.HEAT, DEATH_TICKS, 1.0F, -1);
        }
        if (t < 30 && t % 2 == 0) {   // its ring spins wild
            BossDeath.ring(level, net.minecraft.core.particles.ParticleTypes.FLAME, c, 2.5, 8, 0.12);
        }
        if (t >= 30 && t <= 54 && (t - 30) % 3 == 0) {   // the shields fall away
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.ANVIL_LAND, 1.5F, 0.8F + (t - 30) * 0.02F);
            BossDeath.dust(level, position(), 15, 1.5);
        }
        if (t > 30 && t < 70 && t % 3 == 0) {   // the flames gutter
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 4, 0.8, 0.6, 0.8, 0.03);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, c.x, c.y, c.z, 2, 0.6, 0.4, 0.6, 0.01);
        }
        if (t >= 70 && t < DEATH_TICKS) {   // it draws its fire in
            BossDeath.implode(level, net.minecraft.core.particles.ParticleTypes.FLAME, c, 5, 10, 0.25);
            if (t % 5 == 0) BossDeath.sound(this, net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, 2.0F, 0.5F + (t - 70) * 0.04F);
        }
        if (t == 72) BossDeath.shake(this, 18, 0.5F);
        if (t == DEATH_TICKS - 4) BossDeath.screen(this, com.divinis.screenfx.ScreenEffect.FLASH, 12, 0.8F, 0xFFA040);
        if (t == DEATH_TICKS) {   // a nova of fire
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.6F);
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, 3.0F, 0.5F);
            BossDeath.shake(this, 30, 1.0F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            BossDeath.sphere(level, net.minecraft.core.particles.ParticleTypes.FLAME, c, 120, 0.7);
            BossDeath.sphere(level, net.minecraft.core.particles.ParticleTypes.LAVA, c, 30, 0.5);
            BossDeath.ring(level, net.minecraft.core.particles.ParticleTypes.FLAME, position(), 1, 60, 0.8);
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

    private boolean enraged() {
        return getHealth() < getMaxHealth() * 0.4F;
    }

    private float damageScale() {
        var attr = getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null || attr.getBaseValue() <= 0 ? 1.0F : (float) (attr.getValue() / attr.getBaseValue());
    }

    /** The height of the ground under a point (within 12 blocks down), or its own y if there is none. */
    private double groundBelow(Vec3 at) {
        BlockPos.MutableBlockPos p = BlockPos.containing(at).mutable();
        for (int i = 0; i < 12; i++) {
            BlockState s = level().getBlockState(p);
            if (!s.getCollisionShape(level(), p).isEmpty()) return p.getY() + s.getCollisionShape(level(), p).max(net.minecraft.core.Direction.Axis.Y);
            p.move(0, -1, 0);
        }
        return at.y - HOVER;
    }

    /** Floats towards a point, gently. */
    private void floatTo(Vec3 goal, double speed) {
        Vec3 d = goal.subtract(position());
        double len = d.length();
        Vec3 v = len < 0.05 ? Vec3.ZERO : d.scale(Math.min(speed, len * 0.25) / len);
        setDeltaMovement(getDeltaMovement().scale(0.6).add(v.scale(0.4)));
    }

    private void face(Vec3 at) {
        float yaw = (float) (Mth.atan2(at.z - getZ(), at.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    private void fight(ServerLevel level) {
        setNoGravity(true);
        // a new target only between attacks (one under way keeps its aim)
        if (attack == Attack.NONE && !channeling() || !validTarget(getTarget()))
            setTarget(targeting.pick(this, position(), AGGRO_RANGE, getTarget()));
        LivingEntity target = getTarget();
        if (channeling()) {
            channelTick(level);
            return;
        }
        if (attack != Attack.NONE) {
            attackTick(level, target);
            return;
        }
        if (target == null) {
            setDeltaMovement(getDeltaMovement().scale(0.8));
            return;
        }
        // keep near the target, a little above the ground, a few blocks away
        Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : away.normalize();
        Vec3 goal = target.position().add(away.scale(KEEP_AWAY));
        goal = new Vec3(goal.x, groundBelow(goal.add(0, 3, 0)) + HOVER, goal.z);
        floatTo(goal, enraged() ? 0.32 : 0.24);
        face(target.position());

        if (--infernoTimer <= 0) {
            startInferno(level);
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        double dist = distanceTo(target);
        start(level, dist < 6.5 || random.nextFloat() < 0.35F ? Attack.SWEEP : Attack.FIREBALL, target);
    }

    private void start(ServerLevel level, Attack a, LivingEntity target) {
        attack = a;
        attackTick = 0;
        lockedYaw = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        if (a == Attack.SWEEP) {
            boolean back = random.nextBoolean();
            level.broadcastEntityEvent(this, back ? EVENT_SWEEP_BACK : (byte) (EVENT_ATTACK + a.ordinal()));
            float spin = back ? -SWEEP_SPIN : SWEEP_SPIN;
            Vec3 at = new Vec3(getX(), groundBelow(position()), getZ());
            for (int k = 0; k < MAX_SHIELDS; k++) {
                // a beam out from each shield (they sit on the diagonals of the model)
                Telegraphs.sweep(level, Telegraph.sector(at, lockedYaw + 45 + 90 * k, SWEEP_RADIUS, SWEEP_HALF_ANGLE),
                        SWEEP_PREP, SWEEP_BURN, spin, SWEEP_HIT_EVERY, this, e -> {
                            if (e.hurt(damageSources().mobAttack(this), SWEEP_DAMAGE * damageScale())) e.igniteForSeconds(3);
                        });
            }
            playSound(SoundEvents.ARMOR_EQUIP_GOLD.value(), 3.0F, 0.5F);
        } else {
            level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + a.ordinal()));
            playSound(SoundEvents.BLAZE_AMBIENT, 2.5F, 0.5F);
        }
    }

    private void attackTick(ServerLevel level, @Nullable LivingEntity target) {
        attackTick++;
        setDeltaMovement(getDeltaMovement().scale(0.5));
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
        switch (attack) {
            case FIREBALL -> {
                if (attackTick == FIREBALL_RELEASE) {
                    List<Vec3> spots = new java.util.ArrayList<>();
                    if (target != null) spots.add(target.position());
                    if (enraged()) {
                        for (Player p : level.players()) {
                            if (spots.size() >= 3) break;
                            if (p != target && !p.isCreative() && !p.isSpectator() && p.distanceToSqr(this) < 30 * 30) spots.add(p.position());
                        }
                    }
                    for (Vec3 spot : spots) throwFireball(level, spot);
                }
            }
            case SWEEP -> {
                if (attackTick == SWEEP_PREP) {
                    playSound(SoundEvents.BLAZE_SHOOT, 3.0F, 0.5F);
                    playSound(SoundEvents.FIRECHARGE_USE, 3.0F, 0.6F);
                }
                if (attackTick > SWEEP_PREP && attackTick < SWEEP_PREP + SWEEP_BURN && attackTick % 8 == 0) {
                    playSound(SoundEvents.FIRE_AMBIENT, 2.5F, 0.6F + random.nextFloat() * 0.3F);
                }
            }
            default -> {}
        }
        if (attackTick >= attack.length) {
            attack = Attack.NONE;
            cooldown = (enraged() ? 15 : 35) + random.nextInt(20);
        }
    }

    private void throwFireball(ServerLevel level, Vec3 spot) {
        WildfireFireball ball = ModEntities.WILDFIRE_FIREBALL.get().create(level);
        if (ball == null) return;
        Vec3 from = position().add(0, 2.0, 0);
        ball.setPos(from);
        ball.launch(this, spot.add(0, 0.3, 0), damageScale());
        level.addFreshEntity(ball);
        int flight = (int) Math.ceil(from.distanceTo(spot) / WildfireFireball.SPEED);
        Telegraphs.show(level, Telegraph.circle(spot, WildfireFireball.RADIUS), Math.max(4, flight), 4, this, null);
        playSound(SoundEvents.BLAZE_SHOOT, 2.5F, 0.5F);
    }

    // ------------------------------------------------------------------ inferno

    private void startInferno(ServerLevel level) {
        attack = Attack.NONE;
        entityData.set(CHANNEL, true);
        channelTicks = 0;
        shieldHits = 0;
        int near = 0;
        for (Player p : level.players()) if (!p.isSpectator() && !p.isCreative() && p.distanceToSqr(this) < 40 * 40) near++;
        hitsPerShield = Mth.clamp(1 + near, 2, 5);
        channelAt = new Vec3(getX(), groundBelow(position()) + CHANNEL_HOVER, getZ());
        Telegraphs.show(level, Telegraph.circle(new Vec3(getX(), groundBelow(position()), getZ()), INFERNO_RADIUS), INFERNO_TICKS, 10, this, e -> {
            if (e.hurt(damageSources().mobAttack(this), INFERNO_DAMAGE * damageScale())) {
                e.igniteForSeconds(10);
                Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.2);
                e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.8, push.z));
                e.hurtMarked = true;
            }
        });
        playSound(SoundEvents.WITHER_SPAWN, 3.0F, 1.4F);
        // the whole room heats up while it draws the fire in
        ScreenFx.playOnNear(this, 64, Anchor.ENTITY, 0, ScreenEffect.HEAT, INFERNO_TICKS, 1.8F, -1);
        ScreenFx.playNear(level, position(), 64, ScreenEffect.VIGNETTE, INFERNO_TICKS, 0.55F, 0xFF5A14);
        ScreenFx.playNear(level, position(), 64, ScreenEffect.DISTORT, INFERNO_TICKS, 0.25F, -1);
        tellNear(level, gold("The Wildfire draws in all the fire around it - break its shields with critical hits!"));
    }

    private static net.minecraft.network.chat.Component gold(String s) {
        return Msg.text(s, ChatFormatting.GOLD);
    }

    private void tellNear(ServerLevel level, net.minecraft.network.chat.Component message) {
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(this) < 48 * 48) p.sendSystemMessage(message);
    }

    private void channelTick(ServerLevel level) {
        channelTicks++;
        floatTo(channelAt, 0.3);
        if (getTarget() != null) face(getTarget().position());
        if (channelTicks % 20 == 0) playSound(SoundEvents.FIRE_AMBIENT, 3.0F, 0.6F);
        if (channelTicks % 4 == 0) {
            level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.5, getZ(), 6, 1.2, 1.0, 1.2, 0.08);
        }
        if (channelTicks >= INFERNO_TICKS) { // it goes off (the field's hit does the damage)
            entityData.set(CHANNEL, false);
            level.broadcastEntityEvent(this, EVENT_INFERNO);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.5F);
            playSound(SoundEvents.BLAZE_DEATH, 3.0F, 0.5F);
            ScreenFx.playNear(level, position(), 64, ScreenEffect.FLASH, 14, 0.7F, 0xFF8A30);
            ScreenFx.shake(level, position(), 64, 24, 0.9F);
            infernoTimer = INFERNO_EVERY;
            cooldown = 40;
        }
    }

    /** A critical hit while it charges the Inferno: a step closer to breaking a shield. */
    void criticalHit(ServerPlayer player) {
        if (!channeling() || shields() <= 0) return;
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 2, getZ(), 15, 0.8, 0.8, 0.8, 0.3);
        playSound(SoundEvents.SHIELD_BLOCK, 2.0F, 0.6F + 0.15F * shieldHits);
        if (++shieldHits < hitsPerShield) return;
        shieldHits = 0;
        int left = shields() - 1;
        entityData.set(SHIELDS, (byte) left);
        level.broadcastEntityEvent(this, EVENT_SHIELD_BREAK);
        playSound(SoundEvents.SHIELD_BREAK, 3.0F, 0.5F);
        playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.5F);
        if (left > 0) {
            tellNear(level, gold("A shield shatters! " + left + " left."));
            return;
        }
        // all broken: the Inferno is called off and it falls
        entityData.set(CHANNEL, false);
        Telegraphs.cancel(this);
        setState(STUNNED);
        stunTicks = STUN_TICKS;
        infernoTimer = INFERNO_EVERY;
        playSound(SoundEvents.BLAZE_DEATH, 3.0F, 1.2F);
        tellNear(level, gold("The last shield breaks - the Wildfire falls! Strike now!"));
    }

    private void stunned(ServerLevel level) {
        if (regenTicks > 0) { // the shields come back and it rises again
            setNoGravity(true);
            setDeltaMovement(0, 0.05, 0);
            if (--regenTicks <= 0) {
                setState(AWAKE);
                cooldown = 30;
            }
            return;
        }
        setNoGravity(false);
        if (tickCount % 10 == 0) level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1, getZ(), 6, 0.6, 0.4, 0.6, 0.02);
        if (--stunTicks <= 0) {
            entityData.set(SHIELDS, (byte) MAX_SHIELDS);
            regenTicks = REGEN_TICKS;
            level.broadcastEntityEvent(this, EVENT_REGEN);
            playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.6F);
        }
    }

    /** Critical hits on the Wildfire break its shields while it charges the Inferno. */
    @EventBusSubscriber(modid = com.divinis.Divinis.MODID)
    public static final class Crits {
        private Crits() {}

        @SubscribeEvent
        public static void onCrit(CriticalHitEvent event) {
            if (event.isCriticalHit() && event.getTarget() instanceof Wildfire w && event.getEntity() instanceof ServerPlayer p) {
                w.criticalHit(p);
            }
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
        return state() == SLEEPING || !isAlive() ? -1 : entityData.get(TARGET_ID);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (state() == WAKING) return false;
        if (state() == SLEEPING && !level().isClientSide) wakeUp();
        if (state() == STUNNED && regenTicks <= 0) amount *= 1.5F; // fallen and open
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) targeting.onHurt(source, level().getGameTime());
        return hurt;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isOnFire() {
        return false; // its fire is its own (drawn by its renderer)
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return state() == AWAKE ? SoundEvents.BLAZE_BURN : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BLAZE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLAZE_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Awake", state() != SLEEPING);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setState(tag.getBoolean("Awake") ? AWAKE : SLEEPING);
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    @Override
    public void setCustomName(@Nullable net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }
}
