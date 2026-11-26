package com.divinis.boss;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Void Wyrmling: a small flying worm the Serpent of Infinity calls up in its second phase. It circles low over its
 * player (some three blocks above, like a phantom but lower), then marks the ground under them, hangs for a moment
 * and dives through them - a heavy bite. One hit of anything kills it. It goes when its serpent is gone.
 */
public class VoidWyrmling extends Monster {
    private static final byte EVENT_SWOOP = 60;
    private static final float BITE = 12;
    private static final int AIM_TICKS = 14, DIVE_TICKS = 22, RISE_TICKS = 16;
    private static final double ORBIT_R = 5, ORBIT_H = 3.2, SPEED = 0.55, DIVE_SPEED = 1.05;

    private enum Mode { ORBIT, AIM, DIVE, RISE }

    public final AnimationState flyAnimation = new AnimationState();
    public final AnimationState swoopAnimation = new AnimationState();

    private int ownerId = -1;
    private float damageScale = 1;
    @Nullable private Player prey;
    private Mode mode = Mode.ORBIT;
    private int modeTick, orbitTime = 60;
    private double angle;
    private Vec3 heading = new Vec3(0, 0, 1), lock = Vec3.ZERO;
    private boolean bitten;

    public VoidWyrmling(EntityType<? extends VoidWyrmling> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        this.xpReward = 0;
        angle = random.nextDouble() * Math.PI * 2;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ATTACK_DAMAGE, BITE);
    }

    /** Called up by a serpent at a place, after one player. */
    public static void spawn(ServerLevel level, SerpentOfInfinity owner, Vec3 at, Player prey, float damageScale) {
        VoidWyrmling w = com.divinis.registry.ModEntities.VOID_WYRMLING.get().create(level);
        if (w == null) return;
        w.ownerId = owner.getId();
        w.prey = prey;
        w.damageScale = damageScale;
        w.orbitTime = 40 + w.random.nextInt(50);
        w.moveTo(at.x, at.y, at.z, w.random.nextFloat() * 360, 0);
        level.addFreshEntity(w);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) flyAnimation.startIfStopped(tickCount);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_SWOOP) swoopAnimation.start(tickCount);
        else super.handleEntityEvent(id);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        setDeltaMovement(Vec3.ZERO);
        if (tickCount % 20 == 0) {
            Entity owner = level.getEntity(ownerId);
            if (!(owner instanceof SerpentOfInfinity s) || !s.isAlive()) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.3, getZ(), 20, 0.3, 0.3, 0.3, 0.05);
                discard();
                return;
            }
        }
        if (prey == null || !prey.isAlive() || prey.isSpectator() || prey.isCreative() || prey.level() != level
                || prey.distanceToSqr(this) > 64 * 64) {
            prey = pickPrey();
            if (prey == null) {
                fly(position().add(0, 0.2, 0), 0.2);
                return;
            }
        }
        modeTick++;
        switch (mode) {
            case ORBIT -> {
                angle += 0.085;
                Vec3 goal = prey.position().add(Math.cos(angle) * ORBIT_R, ORBIT_H + Math.sin(tickCount * 0.12) * 0.5, Math.sin(angle) * ORBIT_R);
                fly(goal, SPEED);
                if (modeTick >= orbitTime) {
                    setMode(Mode.AIM);
                    lock = prey.position().add(0, 0.9, 0);
                    Telegraphs.show(level, Telegraph.circle(prey.position(), 1.6F), AIM_TICKS + 4, 0, this, null);
                    level.broadcastEntityEvent(this, EVENT_SWOOP);
                    playSound(SoundEvents.PHANTOM_SWOOP, 1.5F, 1.4F);
                }
            }
            case AIM -> {
                look(lock.subtract(position()));
                if (modeTick >= AIM_TICKS) {
                    setMode(Mode.DIVE);
                    heading = lock.subtract(position()).normalize();
                    bitten = false;
                }
            }
            case DIVE -> {
                setPos(position().add(heading.scale(DIVE_SPEED)));
                look(heading);
                if (!bitten) {
                    for (Player p : level.players()) {
                        if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
                        if (getBoundingBox().inflate(0.35).intersects(p.getBoundingBox())) {
                            bitten = true;
                            p.hurt(damageSources().mobAttack(this), BITE * damageScale);
                            playSound(SoundEvents.PHANTOM_BITE, 1.5F, 0.8F);
                            break;
                        }
                    }
                }
                if (modeTick >= DIVE_TICKS || bitten && modeTick > 3) setMode(Mode.RISE);
            }
            case RISE -> {
                fly(position().add(heading.x * 3, 3, heading.z * 3), SPEED);
                if (modeTick >= RISE_TICKS) {
                    setMode(Mode.ORBIT);
                    orbitTime = 50 + random.nextInt(50);
                }
            }
        }
    }

    private void setMode(Mode m) {
        mode = m;
        modeTick = 0;
    }

    @Nullable
    private Player pickPrey() {
        List<Player> near = new ArrayList<>();
        for (Player p : level().players()) {
            if (p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceToSqr(this) < 48 * 48) near.add(p);
        }
        return near.isEmpty() ? null : near.get(random.nextInt(near.size()));
    }

    /** Moves towards a point at up to `speed` blocks a tick, turning smoothly. */
    private void fly(Vec3 goal, double speed) {
        Vec3 to = goal.subtract(position());
        double d = to.length();
        if (d < 1.0E-3) return;
        heading = heading.add(to.scale(1 / d).subtract(heading).scale(0.25)).normalize();
        setPos(position().add(heading.scale(Math.min(d, speed))));
        look(heading);
    }

    private void look(Vec3 v) {
        if (v.lengthSqr() < 1.0E-6) return;
        float yaw = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-Mth.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        setXRot(pitch);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, Math.max(amount, getMaxHealth())); // any hit kills it
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
        return 1.6F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }
}
