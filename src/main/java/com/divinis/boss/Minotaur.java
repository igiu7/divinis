package com.divinis.boss;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

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
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Minotaur, a 4 block tall boss (/summon divinis:minotaur). It sleeps sitting on the ground until a player comes
 * close or hurts it, then stands up roaring and fights with three telegraphed attacks (boss.Telegraphs: a red area on
 * the ground shows where it will land, whoever still stands there when it is full gets hit):
 * - Cleave: a two-handed axe blow on a wide cone in front of it;
 * - Earthquake: it slams the ground, hitting all around it, and a shockwave rolls outwards a moment later;
 * - Charge: it paws the ground, then runs along a marked lane, trampling everyone on it. Running into a wall
 *   stuns it for a while.
 * A raised shield stops only half of any of its blows (ShieldPierce). It drops its axe once in a hundred kills.
 * Below 40% health it is enraged: it moves faster and rests less between attacks.
 * The attack timings match the animations in client/MinotaurModelData (tools/models/build_minotaur.py).
 */
public class Minotaur extends Monster implements BossTargeting.Marked {
    public static final byte SLEEPING = 0, WAKING = 1, AWAKE = 2;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Minotaur.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(Minotaur.class, EntityDataSerializers.INT);

    private enum Attack {
        NONE(0, 0), CLEAVE(40, 24), STOMP(45, 30), CHARGE(60, 30);

        final int length, impact;

        Attack(int length, int impact) {
            this.length = length;
            this.impact = impact;
        }
    }

    private static final byte EVENT_ATTACK = 110; // + attack ordinal (vanilla uses up to 65)
    private static final int WAKE_TICKS = 60;
    private static final int WAKE_ROAR_AT = 30;
    private static final double WAKE_RANGE = 10;
    private static final double AGGRO_RANGE = 40;

    private static final float CLEAVE_DAMAGE = 22, CLEAVE_RADIUS = 7, CLEAVE_HALF_ANGLE = 50;
    private static final float STOMP_DAMAGE = 16, STOMP_RADIUS = 6.5F;
    private static final float WAVE_DAMAGE = 10, WAVE_INNER = 6.5F, WAVE_OUTER = 11, WAVE_DELAY = 10;
    private static final float CHARGE_DAMAGE = 24, CHARGE_HALF_WIDTH = 1.8F;
    private static final int CHARGE_RUN_TICKS = 15;
    /** chance that it drops its axe (BossItems.MINOTAUR_AXE) */
    private static final float AXE_DROP_CHANCE = 0.01F;

    private static final AttributeModifier ENRAGED = new AttributeModifier(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.divinis.Divinis.MODID, "minotaur_enraged"),
            0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

    // client: animations
    public final AnimationState sleepAnimation = new AnimationState();
    public final AnimationState wakeAnimation = new AnimationState();
    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState cleaveAnimation = new AnimationState();
    public final AnimationState stompAnimation = new AnimationState();
    public final AnimationState chargeAnimation = new AnimationState();
    /** Ticks since it began dying (both sides; its own count, so vanilla neither tips nor reddens it). */
    public int dying;
    public final AnimationState deathAnimation = new AnimationState();
    private int clientAttackTicks;

    // server: the fight
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private Attack attack = Attack.NONE;
    private final BossTargeting targeting = new BossTargeting();
    private int attackTick;
    private int cooldown = 20;
    private int stunTicks;
    private int wakeTicks;
    private float lockedYaw;
    private Vec3 chargeDir = Vec3.ZERO;
    private float chargeSpeed;
    private boolean charging;
    private final Set<UUID> trampled = new HashSet<>();

    public Minotaur(EntityType<? extends Minotaur> type, Level level) {
        super(type, level);
        this.xpReward = 250;
        setPersistenceRequired();
        bossBar.setVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 20.0)
                .add(Attributes.STEP_HEIGHT, 1.5);
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

    /** Client: an attack animation is playing (no walking legs then). */
    public boolean isAttacking() {
        return clientAttackTicks > 0;
    }

    // ------------------------------------------------------------------ client

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STATE.equals(key) && level().isClientSide && state() == WAKING) wakeAnimation.start(tickCount);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id > EVENT_ATTACK && id < EVENT_ATTACK + Attack.values().length) {
            Attack a = Attack.values()[id - EVENT_ATTACK];
            cleaveAnimation.stop();
            stompAnimation.stop();
            chargeAnimation.stop();
            AnimationState s = switch (a) {
                case CLEAVE -> cleaveAnimation;
                case STOMP -> stompAnimation;
                default -> chargeAnimation;
            };
            s.start(tickCount);
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
        return getBoundingBox().inflate(3.0); // the axe reaches far out
    }

    // ------------------------------------------------------------------ server: state

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (tickCount % 10 == 0) updateBossBar(level);
        bossBar.setProgress(getHealth() / getMaxHealth());

        switch (state()) {
            case SLEEPING -> {
                getNavigation().stop();
                if (tickCount % 10 == 0 && nearestPlayer(WAKE_RANGE) != null) wakeUp();
                if (tickCount % 80 == 0) playSound(SoundEvents.POLAR_BEAR_AMBIENT_BABY, 1.0F, 0.4F); // snoring
            }
            case WAKING -> {
                getNavigation().stop();
                if (++wakeTicks == WAKE_ROAR_AT) playSound(SoundEvents.RAVAGER_ROAR, 4.0F, 0.6F);
                if (wakeTicks >= WAKE_TICKS) {
                    setState(AWAKE);
                    cooldown = 10;
                }
            }
            default -> fight(level);
        }
    }

    private void wakeUp() {
        if (state() != SLEEPING) return;
        setState(WAKING);
        wakeTicks = 0;
        playSound(SoundEvents.RAVAGER_STUNNED, 2.0F, 0.6F);
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
        if (!level().isClientSide) {
            bossBar.removeAllPlayers();
            Telegraphs.cancel(this);
        }
        super.remove(reason);
    }

    /** Dying (4 s): it roars at the sky, drops its axe, falls to its knees and onto its face; its soul leaves it. */
    public static final int DEATH_TICKS = 80;

    @Override
    protected void tickDeath() {
        dying++;
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 c = getBoundingBox().getCenter();
        int t = dying;
        if (t == 1) {
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.RAVAGER_ROAR, 4.0F, 0.5F);
            BossDeath.on(this, com.divinis.screenfx.ScreenEffect.Anchor.EXCEPT, 0, com.divinis.screenfx.ScreenEffect.DESATURATE, DEATH_TICKS, 0.7F, -1);
        }
        if (t > 4 && t < 28 && t % 3 == 0) {   // the roar's breath
            Vec3 h = c.add(0, getBbHeight() * 0.45, 0);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, h.x, h.y, h.z, 3, 0.3, 0.2, 0.3, 0.06);
        }
        if (t == 10) BossDeath.shake(this, 20, 0.35F);
        if (t == 40) {   // the axe falls
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.ANVIL_LAND, 2.0F, 0.6F);
            BossDeath.dust(level, position(), 25, 1.2);
        }
        if (t == 46) {   // to its knees
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.RAVAGER_STUNNED, 3.0F, 0.6F);
            BossDeath.shake(this, 12, 0.45F);
            BossDeath.dust(level, position(), 40, 1.5);
        }
        if (t == 60) {   // onto its face
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.RAVAGER_DEATH, 4.0F, 0.5F);
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), 1.5F, 0.5F);
            BossDeath.shake(this, 22, 0.8F);
            BossDeath.dust(level, position(), 80, 2.5);
            BossDeath.ring(level, net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, position(), 1.5, 24, 0.15);
            BossDeath.screen(this, com.divinis.screenfx.ScreenEffect.ABERRATION, 14, 0.5F, -1);
        }
        if (t > 60 && t % 2 == 0) {   // its soul leaves it
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, c.x, c.y, c.z, 2, 1.0, 0.5, 1.0, 0.03);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL, c.x, c.y + 0.5, c.z, 1, 0.8, 0.4, 0.8, 0.02);
        }
        if (t == DEATH_TICKS) {
            BossDeath.sphere(level, net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, c, 40, 0.25);
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.SOUL_SAND_BREAK, 3.0F, 0.5F);
            BossDeath.sound(this, net.minecraft.sounds.SoundEvents.WITHER_SHOOT, 1.5F, 0.4F);
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
            playSound(SoundEvents.RAVAGER_ROAR, 4.0F, 0.5F);
        }

        // a new target only between attacks (one under way keeps its aim)
        if (attack == Attack.NONE || !validTarget(getTarget())) setTarget(targeting.pick(this, position(), AGGRO_RANGE, getTarget()));
        LivingEntity target = getTarget();

        if (attack != Attack.NONE) {
            attackTick(level, target);
            return;
        }
        if (stunTicks > 0) {
            stunTicks--;
            getNavigation().stop();
            if (stunTicks % 5 == 0) level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 4.2, getZ(), 4, 0.6, 0.1, 0.6, 0.0);
            return;
        }
        if (target == null) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(target, 30, 30);
        double dist = Math.sqrt(distanceToSqr(target.getX(), getY(), target.getZ()));
        if (cooldown > 0) cooldown--;

        if (cooldown <= 0) {
            int close = 0;
            for (Player p : level.players()) {
                if (!p.isSpectator() && !p.isCreative() && p.distanceToSqr(this) < 7 * 7) close++;
            }
            if (dist <= 6.0) {
                start(level, close >= 2 || random.nextFloat() < 0.35F ? Attack.STOMP : Attack.CLEAVE, target);
                return;
            }
            if (dist <= 18 && hasLineOfSight(target) && random.nextFloat() < 0.6F) {
                start(level, Attack.CHARGE, target);
                return;
            }
        }
        if (dist > 3.5) {
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

    private void start(ServerLevel level, Attack a, LivingEntity target) {
        attack = a;
        attackTick = 0;
        getNavigation().stop();
        lockedYaw = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        lockYaw();
        level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + a.ordinal()));
        Vec3 at = position();

        switch (a) {
            case CLEAVE -> {
                Telegraphs.show(level, Telegraph.sector(at, lockedYaw, CLEAVE_RADIUS, CLEAVE_HALF_ANGLE), a.impact, 6, this,
                        e -> hit(e, CLEAVE_DAMAGE, 1.4, 0.35));
                playSound(SoundEvents.RAVAGER_ATTACK, 2.0F, 0.6F);
            }
            case STOMP -> {
                Telegraphs.show(level, Telegraph.circle(at, STOMP_RADIUS), a.impact, 6, this,
                        e -> hit(e, STOMP_DAMAGE, 0.8, 0.9));
                Telegraphs.show(level, Telegraph.ring(at, WAVE_INNER, WAVE_OUTER), a.impact + (int) WAVE_DELAY, 6, this,
                        e -> hit(e, WAVE_DAMAGE, 1.0, 0.5));
                playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.8F);
            }
            case CHARGE -> {
                double dist = Math.sqrt(distanceToSqr(target.getX(), getY(), target.getZ()));
                float length = (float) Mth.clamp(dist + 5, 8, 22);
                chargeDir = new Vec3(-Mth.sin(lockedYaw * Mth.DEG_TO_RAD), 0, Mth.cos(lockedYaw * Mth.DEG_TO_RAD));
                chargeSpeed = length / CHARGE_RUN_TICKS;
                trampled.clear();
                // the lane only warns; the hits come from running into people (see attackTick)
                Telegraphs.show(level, Telegraph.rect(at, lockedYaw, length + 1.2F, CHARGE_HALF_WIDTH), a.impact,
                        CHARGE_RUN_TICKS, this, null);
                playSound(SoundEvents.RAVAGER_STUNNED, 2.5F, 0.5F);
            }
            default -> {}
        }
    }

    private void attackTick(ServerLevel level, @Nullable LivingEntity target) {
        attackTick++;
        lockYaw();
        if (!charging) setDeltaMovement(0, getDeltaMovement().y, 0);

        switch (attack) {
            case CLEAVE -> {
                if (attackTick == attack.impact) {
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 1.3F);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.6F);
                    ScreenFx.shake(level, position(), 16, 7, 0.35F);
                    float step = CLEAVE_HALF_ANGLE * 2 / 6;
                    for (float ang = -CLEAVE_HALF_ANGLE; ang <= CLEAVE_HALF_ANGLE + 0.1F; ang += step) {
                        dust(level, lockedYaw + ang, CLEAVE_RADIUS * 0.4, CLEAVE_RADIUS * 0.9, 3);
                    }
                }
            }
            case STOMP -> {
                if (attackTick == attack.impact) {
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.6F);
                    level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 3, 1.5, 0.2, 1.5, 0.0);
                    for (int ang = 0; ang < 360; ang += 24) dust(level, ang, 1.5, STOMP_RADIUS, 3);
                    ScreenFx.shake(level, position(), 24, 12, 0.75F);
                }
                if (attackTick == attack.impact + (int) WAVE_DELAY) {
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.9F);
                    for (int ang = 0; ang < 360; ang += 12) dust(level, ang, WAVE_INNER, WAVE_OUTER, 2);
                    ScreenFx.shake(level, position(), 22, 8, 0.45F);
                }
            }
            case CHARGE -> {
                if (attackTick == attack.impact) {
                    charging = true;
                    playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.9F);
                }
                if (charging) chargeStep(level);
                if (attackTick >= attack.impact + CHARGE_RUN_TICKS) charging = false;
            }
            default -> {}
        }

        if (attackTick >= attack.length) {
            attack = Attack.NONE;
            charging = false;
            cooldown = (enraged() ? 10 : 25) + random.nextInt(15);
        }
    }

    private void chargeStep(ServerLevel level) {
        setDeltaMovement(chargeDir.x * chargeSpeed, getDeltaMovement().y, chargeDir.z * chargeSpeed);
        if (attackTick % 2 == 0) playSound(SoundEvents.RAVAGER_STEP, 2.0F, 0.7F);
        BlockState under = level.getBlockState(BlockPos.containing(getX(), getY() - 0.5, getZ()));
        if (!under.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), getX(), getY() + 0.1, getZ(), 6, 1.0, 0.1, 1.0, 0.1);
        }
        AABB reach = getBoundingBox().inflate(0.6, 0.2, 0.6);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, reach, e -> e != this && e.isAlive()
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            if (trampled.add(e.getUUID())) hit(e, CHARGE_DAMAGE, 2.2, 0.6);
        }
        // ran into a wall: it stops, stunned
        if (horizontalCollision && attackTick > attack.impact + 2) {
            charging = false;
            stunTicks = 50;
            attackTick = attack.length; // end the attack now
            setDeltaMovement(Vec3.ZERO);
            playSound(SoundEvents.ANVIL_LAND, 2.0F, 0.5F);
            playSound(SoundEvents.RAVAGER_STUNNED, 2.0F, 0.7F);
            ScreenFx.shake(level, position(), 24, 12, 0.7F);
            level.sendParticles(ParticleTypes.EXPLOSION, getX() + chargeDir.x * 1.5, getY() + 2, getZ() + chargeDir.z * 1.5, 2, 0.5, 0.5, 0.5, 0.0);
        }
    }

    /** Damage multiplier from the ATTACK_DAMAGE attribute (e.g. dungeon.DungeonScaling for bigger parties). */
    private float damageScale() {
        var attr = getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null || attr.getBaseValue() <= 0 ? 1.0F : (float) (attr.getValue() / attr.getBaseValue());
    }

    /** A hit from one of the attacks: damage, then a shove away from the Minotaur and up. */
    private void hit(LivingEntity e, float damage, double push, double up) {
        if (!e.hurt(damageSources().mobAttack(this), damage * damageScale())) return;
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0) return;
        e.setDeltaMovement(e.getDeltaMovement().add(dx / len * push * resist, up * resist, dz / len * push * resist));
        e.hurtMarked = true;
    }

    /** Block dust thrown up along a line from the Minotaur, at yaw, from distance a to b. */
    private void dust(ServerLevel level, float yaw, double a, double b, int count) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        for (double d = a; d <= b; d += 1.0) {
            double x = getX() + fx * d, z = getZ() + fz * d;
            BlockPos pos = BlockPos.containing(x, getY() - 0.5, z);
            BlockState state = level.getBlockState(pos);
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
        if (state() == WAKING) return false; // standing up and roaring
        if (state() == SLEEPING) {
            if (!level().isClientSide) wakeUp();
        }
        if (stunTicks > 0) amount *= 1.5F; // stunned after hitting a wall: a chance to hurt it
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) targeting.onHurt(source, level().getGameTime());
        return hurt;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (random.nextFloat() < AXE_DROP_CHANCE) spawnAtLocation(new net.minecraft.world.item.ItemStack(BossItems.MINOTAUR_AXE.get()));
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
        return 0.6F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return state() == AWAKE ? SoundEvents.RAVAGER_AMBIENT : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 1.0F, 0.6F);
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
