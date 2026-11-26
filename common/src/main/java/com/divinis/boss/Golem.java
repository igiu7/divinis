package com.divinis.boss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenEffect.Anchor;
import com.divinis.screenfx.ScreenFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Golem, an 8 block tall boss of deepslate (/summon divinis:golem). Asleep it is only a heap of stones half sunk
 * in the ground; when a player comes close or strikes it, the heap rumbles, the stones lift and float apart, slam
 * together into the golem, its eyes, core and runes light up and it drives its fists into the ground. It fights with
 * three telegraphed attacks (boss.Telegraphs: a red area shows where it lands, whoever stands there when it is full
 * gets hit):
 * - Whirlwind: it swings its arms out and turns its torso round twice - two hits on a ring around it; right at its
 *   feet, inside the ring, is safe;
 * - Boulder: it tears a boulder from the ground and hurls it at up to three players; the boulder flies in an arc and
 *   lands where they stood when it was thrown;
 * - Crystal spikes: it drives both fists into the ground and spikes of its dark blue crystals burst up out of the
 *   ground along a line towards its target, one stretch after the other, and sink back a moment later. They are only
 *   seen (item displays of BossItems.GOLEM_CRYSTAL_SPIKE), no blocks are placed.
 * Shields do not stop any of its blows (ShieldPierce).
 * Below 40% health it is enraged: it moves faster and rests less between attacks.
 * The attack timings match the animations in client/GolemModelData (tools/models/build_golem.py).
 */
public class Golem extends Monster implements BossTargeting.Marked {
    public static final byte SLEEPING = 0, WAKING = 1, AWAKE = 2;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Golem.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(Golem.class, EntityDataSerializers.INT);

    private enum Attack {
        NONE(0, 0), SPIN(52, 25), THROW(50, 30), SPIKES(40, 18);

        final int length, impact;

        Attack(int length, int impact) {
            this.length = length;
            this.impact = impact;
        }
    }

    private static final byte EVENT_ATTACK = 120; // + attack ordinal (vanilla uses up to 65, the Minotaur 110+)
    // waking, in ticks (as the "wake" animation): rumbling 8-24, lifting 24-40, together at 65, fists down at 75
    private static final int WAKE_TICKS = 80;
    private static final int WAKE_SNAP = 65, WAKE_ROAR = 70, WAKE_SLAM = 75;
    private static final double WAKE_RANGE = 12;
    private static final double AGGRO_RANGE = 48;

    private static final float SPIN_DAMAGE = 18, SPIN_INNER = 2.6F, SPIN_OUTER = 9.5F;
    private static final int SPIN_SECOND = 40;
    private static final float BOULDER_DAMAGE = 22, BOULDER_RADIUS = 3.0F;
    private static final int BOULDER_FLIGHT = 20, MAX_BOULDERS = 3;
    private static final float SPIKE_DAMAGE = 16;
    private static final int SPIKE_SEGMENTS = 6, SPIKE_STEP = 4, SPIKE_STAY = 30;

    private static final AttributeModifier ENRAGED = new AttributeModifier(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.divinis.Divinis.MODID, "golem_enraged"),
            0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

    // client: animations
    public final AnimationState sleepAnimation = new AnimationState();
    public final AnimationState wakeAnimation = new AnimationState();
    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState spinAnimation = new AnimationState();
    public final AnimationState throwAnimation = new AnimationState();
    public final AnimationState spikesAnimation = new AnimationState();
    /** Ticks since it began dying (both sides; its own count, so vanilla neither tips nor reddens it). */
    public int dying;
    public final AnimationState deathAnimation = new AnimationState();
    private int clientAttackTicks;
    private int clientWakeStart = -1;

    // server: the fight
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.BLUE,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private Attack attack = Attack.NONE;
    private final BossTargeting targeting = new BossTargeting();
    private int attackTick;
    private int cooldown = 20;
    private int wakeTicks;
    private float lockedYaw;
    private final List<Vec3> throwTargets = new ArrayList<>();

    private record Boulder(Display.BlockDisplay rock, Vec3 from, Vec3 to, int start) {}

    private final List<Boulder> boulders = new ArrayList<>();

    /** a crystal spike that is shown: up from start, sinking at until, gone at until + SPIKE_MOVE */
    private record Spike(Display.ItemDisplay shown, Vec3 at, int until) {}

    private final List<Spike> spikes = new ArrayList<>();

    public Golem(EntityType<? extends Golem> type, Level level) {
        super(type, level);
        this.xpReward = 300;
        setPersistenceRequired();
        bossBar.setVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.ARMOR, 16.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 20.0)
                .add(Attributes.STEP_HEIGHT, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, SLEEPING);
        builder.define(TARGET_ID, -1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public byte state() {
        return entityData.get(STATE);
    }

    private void setState(byte state) {
        entityData.set(STATE, state);
    }

    /** Client: do its eyes, core and runes glow? Not while it is a heap of stones, only once it has come together. */
    public boolean glowing() {
        return state() == AWAKE || state() == WAKING && clientWakeStart >= 0 && tickCount - clientWakeStart >= WAKE_SNAP;
    }

    /** Client: an attack animation is playing (no walking legs, no looking around then). */
    public boolean isAttacking() {
        return clientAttackTicks > 0;
    }

    /** Asleep it is a low heap of stones. */
    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions full = super.getDefaultDimensions(pose);
        if (entityData == null) return full; // asked while still being built
        return state() == SLEEPING ? EntityDimensions.scalable(full.width(), 2.2F) : full;
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
        if (id > EVENT_ATTACK && id < EVENT_ATTACK + Attack.values().length) {
            Attack a = Attack.values()[id - EVENT_ATTACK];
            spinAnimation.stop();
            throwAnimation.stop();
            spikesAnimation.stop();
            (switch (a) {
                case SPIN -> spinAnimation;
                case THROW -> throwAnimation;
                default -> spikesAnimation;
            }).start(tickCount);
            clientAttackTicks = a.length;
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
        if (s == AWAKE) idleAnimation.startIfStopped(tickCount);
        else idleAnimation.stop();
        if (s != WAKING) wakeAnimation.stop();
        if (clientAttackTicks > 0) clientAttackTicks--;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(4.0); // the arms reach far out
    }

    // ------------------------------------------------------------------ server: state

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (tickCount % 10 == 0) updateBossBar(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        tickBoulders(level);
        tickSpikes(level, false);

        switch (state()) {
            case SLEEPING -> {
                getNavigation().stop();
                if (tickCount % 10 == 0 && nearestPlayer(WAKE_RANGE) != null) wakeUp();
            }
            case WAKING -> {
                getNavigation().stop();
                wakeTicks++;
                BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState());
                if (wakeTicks >= 8 && wakeTicks <= 24 && wakeTicks % 4 == 0) { // the heap rumbles
                    playSound(SoundEvents.GRAVEL_BREAK, 2.5F, 0.4F + random.nextFloat() * 0.2F);
                    level.sendParticles(dust, getX(), getY() + 0.3, getZ(), 12, 2.0, 0.2, 2.0, 0.05);
                    ScreenFx.shake(level, position(), 24, 5, 0.25F);
                }
                if (wakeTicks == 24) playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 0.5F); // the stones lift
                if (wakeTicks > 24 && wakeTicks < WAKE_SNAP && wakeTicks % 3 == 0) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 4, getZ(), 6, 2.0, 2.0, 2.0, 0.02);
                }
                if (wakeTicks == WAKE_SNAP) { // together
                    playSound(SoundEvents.ANVIL_LAND, 2.5F, 0.4F);
                    playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 3.0F, 0.6F);
                    ScreenFx.shake(level, position(), 28, 10, 0.6F);
                    level.sendParticles(dust, getX(), getY() + 4, getZ(), 60, 2.0, 2.5, 2.0, 0.2);
                }
                if (wakeTicks == WAKE_ROAR) playSound(SoundEvents.WARDEN_ROAR, 4.0F, 0.6F);
                if (wakeTicks == WAKE_SLAM) { // its fists hit the ground
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.5F, 0.6F);
                    ScreenFx.shake(level, position(), 32, 16, 0.9F);
                    level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 2, 1.5, 0.2, 1.5, 0.0);
                    for (int ang = 0; ang < 360; ang += 30) dust(level, ang, 2, 6, 2);
                }
                if (wakeTicks >= WAKE_TICKS) {
                    setState(AWAKE);
                    cooldown = 20;
                }
            }
            default -> fight(level);
        }
    }

    private void wakeUp() {
        if (state() != SLEEPING) return;
        setState(WAKING);
        wakeTicks = 0;
        playSound(SoundEvents.STONE_BREAK, 3.0F, 0.4F);
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
            for (Boulder b : boulders) b.rock().discard();
            boulders.clear();
            tickSpikes(level, true);
        }
        super.remove(reason);
    }

    /** Dying (5 s): its crystals crack and leak light, it staggers and comes apart into its heap of stones, which crumbles to dust. */
    public static final int DEATH_TICKS = 100;

    @Override
    protected void tickDeath() {
        dying++;
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 c = getBoundingBox().getCenter();
        int t = dying;
        if (t == 1) {
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, 3.0F, 0.5F);
            BossDeath.on(this, com.divinis.screenfx.ScreenEffect.Anchor.ENTITY, 0, com.divinis.screenfx.ScreenEffect.OUTLINE, 64, 1.0F, 0x60A0FF);
        }
        if (t < 48 && t % 5 == 0) {   // its crystals crack, light leaking out
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK, 2.0F, 0.5F + t * 0.01F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, c.x, c.y + 1, c.z, 6, 1.6, 2.0, 1.6, 0.05);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1, c.z, 8, 1.6, 2.0, 1.6, 0.2);
            BossDeath.shake(this, 6, 0.15F + t * 0.006F);
        }
        if (t == 48) BossDeath.sound(this, net.minecraft.sounds.SoundEvents.WARDEN_ROAR, 3.0F, 0.4F);
        if (t >= 48 && t <= 64 && t % 3 == 0) {   // it comes apart
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.DEEPSLATE_BREAK, 3.0F, 0.5F);
            BossDeath.dust(level, c.add(0, 1, 0), 40, 2.0);
        }
        if (t == 64) {   // the heap lands
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), 2.5F, 0.5F);
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.ANVIL_LAND, 2.5F, 0.4F);
            BossDeath.shake(this, 26, 0.9F);
            BossDeath.screen(this, com.divinis.screenfx.ScreenEffect.FLASH, 8, 0.35F, 0x80B0FF);
            BossDeath.dust(level, position(), 120, 3.0);
            BossDeath.ring(level, net.minecraft.core.particles.ParticleTypes.CLOUD, position(), 2, 40, 0.3);
        }
        if (t > 64 && t % 4 == 0) {   // the heap crumbling to dust
            BossDeath.dust(level, position(), 20, 2.0);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, c.x, getY() + 0.8, c.z, 2, 1.5, 0.3, 1.5, 0.02);
        }
        if (t == DEATH_TICKS) {
            BossDeath.sphere(level, net.minecraft.core.particles.ParticleTypes.END_ROD, position().add(0, 1, 0), 50, 0.4);
            BossDeath.dust(level, position(), 150, 3.0);
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 0.5F);
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.DEEPSLATE_BREAK, 3.0F, 0.4F);
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

    // ------------------------------------------------------------------ server: the fight

    private boolean enraged() {
        return getHealth() < getMaxHealth() * 0.4F;
    }

    private void fight(ServerLevel level) {
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (enraged() && speed != null && !speed.hasModifier(ENRAGED.id())) {
            speed.addTransientModifier(ENRAGED);
            playSound(SoundEvents.WARDEN_ROAR, 4.0F, 0.5F);
        }
        // a new target only between attacks (one under way keeps its aim)
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
        getLookControl().setLookAt(target, 20, 20);
        double dist = Math.sqrt(distanceToSqr(target.getX(), getY(), target.getZ()));
        if (cooldown > 0) cooldown--;
        if (cooldown <= 0) {
            int close = 0;
            for (Player p : level.players()) {
                if (!p.isSpectator() && !p.isCreative() && p.distanceToSqr(this) < 9 * 9) close++;
            }
            if (dist <= 8) {
                start(level, close >= 2 || random.nextFloat() < 0.55F ? Attack.SPIN : Attack.SPIKES, target);
                return;
            }
            if (dist <= 26) {
                start(level, random.nextFloat() < 0.6F ? Attack.THROW : Attack.SPIKES, target);
                return;
            }
        }
        if (dist > 5) {
            if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
        }
    }

    private void lockYaw() {
        setYRot(lockedYaw);
        yRotO = lockedYaw;
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
    }

    private Vec3 forward() {
        return new Vec3(-Mth.sin(lockedYaw * Mth.DEG_TO_RAD), 0, Mth.cos(lockedYaw * Mth.DEG_TO_RAD));
    }

    private void start(ServerLevel level, Attack a, LivingEntity target) {
        attack = a;
        attackTick = 0;
        getNavigation().stop();
        lockedYaw = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        lockYaw();
        level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + a.ordinal()));
        Vec3 at = position();
        switch (a) {
            case SPIN -> {
                for (int warmup : new int[] {a.impact, SPIN_SECOND}) {
                    Telegraphs.show(level, Telegraph.ring(at, SPIN_INNER, SPIN_OUTER), warmup, 5, this,
                            e -> hit(e, SPIN_DAMAGE, 1.8, 0.5));
                }
                playSound(SoundEvents.IRON_GOLEM_ATTACK, 3.0F, 0.4F);
            }
            case THROW -> {
                throwTargets.clear();
                throwTargets.add(target.position());
                for (Player p : level.players()) {
                    if (throwTargets.size() >= MAX_BOULDERS) break;
                    if (p == target || p.isCreative() || p.isSpectator() || p.distanceToSqr(this) > 30 * 30) continue;
                    throwTargets.add(p.position());
                }
                for (Vec3 t : throwTargets) {
                    Telegraphs.show(level, Telegraph.circle(t, BOULDER_RADIUS), a.impact + BOULDER_FLIGHT, 5, this,
                            e -> hit(e, BOULDER_DAMAGE, 0.8, 0.6));
                }
                playSound(SoundEvents.GRAVEL_BREAK, 3.0F, 0.5F);
            }
            case SPIKES -> {
                Vec3 dir = forward();
                for (int i = 0; i < SPIKE_SEGMENTS; i++) {
                    Vec3 from = at.add(dir.scale(2.5 + i * 3.0));
                    Telegraphs.show(level, Telegraph.rect(from, lockedYaw, 3.0F, 1.6F), a.impact + i * SPIKE_STEP, 4, this,
                            e -> hit(e, SPIKE_DAMAGE, 0.3, 1.0));
                }
                playSound(SoundEvents.IRON_GOLEM_ATTACK, 3.0F, 0.3F);
            }
            default -> {}
        }
    }

    private void attackTick(ServerLevel level, @Nullable LivingEntity target) {
        attackTick++;
        lockYaw();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        switch (attack) {
            case SPIN -> {
                if (attackTick == attack.impact || attackTick == SPIN_SECOND) {
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.4F);
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.5F, 1.2F);
                    ScreenFx.shake(level, position(), 22, 8, 0.45F);
                    for (int ang = 0; ang < 360; ang += 20) dust(level, ang, SPIN_INNER, SPIN_OUTER, 2);
                }
            }
            case THROW -> {
                if (attackTick == attack.impact) {
                    Vec3 hand = position().add(0, 8.5, 0).add(forward().scale(1.5));
                    for (Vec3 t : throwTargets) boulders.add(new Boulder(spawnBoulder(level, hand), hand, t, tickCount));
                    playSound(SoundEvents.WITHER_SHOOT, 2.0F, 0.4F);
                }
            }
            case SPIKES -> {
                if (attackTick == attack.impact) {
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.6F);
                    level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 3, 1.5, 0.2, 1.5, 0.0);
                    ScreenFx.shake(level, position(), 30, 14, 0.8F);
                }
                int k = attackTick - attack.impact;
                if (k >= 0 && k % SPIKE_STEP == 0 && k / SPIKE_STEP < SPIKE_SEGMENTS) {
                    raiseSpikes(level, position().add(forward().scale(4.0 + k / SPIKE_STEP * 3.0)));
                }
            }
            default -> {}
        }
        if (attackTick >= attack.length) {
            attack = Attack.NONE;
            cooldown = (enraged() ? 12 : 30) + random.nextInt(15);
        }
    }

    // ------------------------------------------------------------------ boulders

    /** A flying boulder: a block display (two blocks across) moved along an arc, smoothed by the client. */
    private Display.BlockDisplay spawnBoulder(ServerLevel level, Vec3 at) {
        Display.BlockDisplay rock = EntityType.BLOCK_DISPLAY.create(level);
        CompoundTag tag = new CompoundTag();
        tag.put("block_state", NbtUtils.writeBlockState(Blocks.COBBLED_DEEPSLATE.defaultBlockState()));
        CompoundTag tr = new CompoundTag();
        tr.put("translation", floats(-1, -1, -1));
        tr.put("scale", floats(2, 2, 2));
        tr.put("left_rotation", floats(0, 0, 0, 1));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        tag.put("transformation", tr);
        tag.putInt("teleport_duration", 2);
        rock.load(tag);
        rock.setPos(at);
        level.addFreshEntity(rock);
        return rock;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(FloatTag.valueOf(v));
        return list;
    }

    private void tickBoulders(ServerLevel level) {
        for (Iterator<Boulder> it = boulders.iterator(); it.hasNext(); ) {
            Boulder b = it.next();
            float t = Math.min(1.0F, (tickCount - b.start()) / (float) BOULDER_FLIGHT);
            Vec3 p = b.from().lerp(b.to(), t).add(0, Math.sin(t * Math.PI) * 8.0, 0);
            b.rock().setPos(p);
            if (t >= 1.0F) {
                b.rock().discard();
                it.remove();
                level.playSound(null, b.to().x, b.to().y, b.to().z, SoundEvents.GENERIC_EXPLODE.value(),
                        net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.7F);
                level.sendParticles(ParticleTypes.EXPLOSION, b.to().x, b.to().y + 0.5, b.to().z, 2, 0.6, 0.2, 0.6, 0.0);
                ScreenFx.shake(level, b.to(), 16, 8, 0.5F);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLED_DEEPSLATE.defaultBlockState()),
                        b.to().x, b.to().y + 0.5, b.to().z, 40, 1.2, 0.4, 1.2, 0.2);
            }
        }
    }

    // ------------------------------------------------------------------ spikes

    /** how big a crystal spike is shown (blocks) and how long it takes to come up or sink (ticks) */
    private static final float SPIKE_SIZE = 2.6F;
    private static final int SPIKE_MOVE = 3;

    /** A crystal spike, three blocks tall, sunk under the ground: it is moved up (smoothly, on the client) next. */
    private Display.ItemDisplay spawnSpike(ServerLevel level, Vec3 at) {
        Display.ItemDisplay spike = EntityType.ITEM_DISPLAY.create(level);
        CompoundTag tag = new CompoundTag();
        tag.put("item", new net.minecraft.world.item.ItemStack(BossItems.GOLEM_CRYSTAL_SPIKE.get()).save(level.registryAccess()));
        tag.putString("item_display", "none");
        float turn = random.nextFloat() * Mth.TWO_PI, lean = (random.nextFloat() - 0.5F) * 0.35F;
        CompoundTag tr = new CompoundTag();
        tr.put("translation", floats(0, SPIKE_SIZE / 2, 0)); // the model's bottom on the spike's position
        tr.put("scale", floats(SPIKE_SIZE, SPIKE_SIZE, SPIKE_SIZE));
        tr.put("left_rotation", floats(0, Mth.sin(turn / 2), 0, Mth.cos(turn / 2)));
        tr.put("right_rotation", floats(Mth.sin(lean / 2), 0, 0, Mth.cos(lean / 2)));
        tag.put("transformation", tr);
        tag.putInt("teleport_duration", SPIKE_MOVE);
        CompoundTag light = new CompoundTag();
        light.putInt("block", 12);
        light.putInt("sky", 15);
        tag.put("brightness", light);
        spike.load(tag);
        spike.setPos(at.x, at.y - SPIKE_SIZE - 0.2, at.z);
        level.addFreshEntity(spike);
        return spike;
    }

    /** Crystal spikes burst up out of the ground around a point. */
    private void raiseSpikes(ServerLevel level, Vec3 center) {
        for (int i = 0; i < 3; i++) {
            Vec3 at = new Vec3(center.x + (random.nextDouble() - 0.5) * 2.4, getY(), center.z + (random.nextDouble() - 0.5) * 2.4);
            spikes.add(new Spike(spawnSpike(level, at), at, tickCount + SPIKE_STAY));
        }
        level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_CLUSTER_PLACE,
                net.minecraft.sounds.SoundSource.HOSTILE, 2.5F, 0.5F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.POINTED_DRIPSTONE_LAND,
                net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.6F);
        BlockState ground = level.getBlockState(BlockPos.containing(center.x, getY() - 0.5, center.z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), center.x, center.y + 0.3, center.z, 25, 1.0, 0.3, 1.0, 0.15);
        }
    }

    /** Moves the spikes: up just after they appear, down when their time is over, then gone. */
    private void tickSpikes(ServerLevel level, boolean all) {
        for (Iterator<Spike> it = spikes.iterator(); it.hasNext(); ) {
            Spike s = it.next();
            if (all || tickCount >= s.until() + SPIKE_MOVE + 1) {
                s.shown().discard();
                it.remove();
                continue;
            }
            int born = s.until() - SPIKE_STAY;
            if (tickCount == born + 1) s.shown().setPos(s.at()); // up
            if (tickCount == s.until()) { // down again
                s.shown().setPos(s.at().x, s.at().y - SPIKE_SIZE - 0.2, s.at().z);
                level.playSound(null, s.at().x, s.at().y, s.at().z, SoundEvents.AMETHYST_CLUSTER_BREAK,
                        net.minecraft.sounds.SoundSource.HOSTILE, 0.6F, 0.6F);
            }
        }
    }

    // ------------------------------------------------------------------ hits

    /** Damage multiplier from the ATTACK_DAMAGE attribute (e.g. dungeon.DungeonScaling for bigger parties). */
    private float damageScale() {
        var attr = getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null || attr.getBaseValue() <= 0 ? 1.0F : (float) (attr.getValue() / attr.getBaseValue());
    }

    /** A hit from one of the attacks: damage, then a shove away from the golem and up. */
    private void hit(LivingEntity e, float damage, double push, double up) {
        if (!e.hurt(damageSources().mobAttack(this), damage * damageScale())) return;
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0) return;
        e.setDeltaMovement(e.getDeltaMovement().add(dx / len * push * resist, up * resist, dz / len * push * resist));
        e.hurtMarked = true;
    }

    /** Stone dust thrown up along a line from the golem, at yaw, from distance a to b. */
    private void dust(ServerLevel level, float yaw, double a, double b, int count) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        for (double d = a; d <= b; d += 1.5) {
            double x = getX() + fx * d, z = getZ() + fz * d;
            BlockState state = level.getBlockState(BlockPos.containing(x, getY() - 0.5, z));
            if (state.isAir()) continue;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, getY() + 0.2, z, count, 0.3, 0.1, 0.3, 0.15);
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
        if (state() == WAKING) return false; // still coming together
        if (state() == SLEEPING && !level().isClientSide) wakeUp();
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) targeting.onHurt(source, level().getGameTime());
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

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return state() == AWAKE ? SoundEvents.IRON_GOLEM_HURT : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_DAMAGE;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.5F, 0.4F);
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
