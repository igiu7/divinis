package com.divinis.boss;

import javax.annotation.Nullable;

import com.divinis.registry.ModParticles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A tornado of sand raised by the Sphinx (boss.Sphinx), drawn by client.SandTornadoRenderer: a funnel of whirling sand
 * size * HEIGHT blocks high; whoever stands in its foot is hurt (every half second). It either hunts a player at a walk (chase), or circles a point, its circle widening
 * (orbit). It rises out of the ground over its first ticks and blows away over its last. Never saved.
 */
public class SandTornado extends Entity {
    public static final float HEIGHT = 7.0F;
    static final double CHASE_SPEED = 0.09;
    static final float DAMAGE = 6;
    public static final int RISE = 12, FADE = 20;
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(SandTornado.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SandTornado.class, EntityDataSerializers.INT);

    @Nullable private Sphinx owner;
    @Nullable private Player prey;
    private float damageScale = 1;
    // orbit: round centre, at radius growing by grow a tick, turning spin degrees a tick
    @Nullable private Vec3 centre;
    private double radius, grow;
    private float angle, spin;

    public SandTornado(EntityType<? extends SandTornado> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SIZE, 1.0F);
        builder.define(LIFE, 100);
    }

    public float size() { return entityData.get(SIZE); }
    public int life() { return entityData.get(LIFE); }

    /** Hunts a player for life ticks. */
    void chase(Sphinx owner, @Nullable Player prey, float size, int life, float damageScale) {
        this.owner = owner;
        this.prey = prey;
        this.damageScale = damageScale;
        entityData.set(SIZE, size);
        entityData.set(LIFE, life);
    }

    /** Circles centre from angle (degrees), radius growing by grow a tick, turning spin degrees a tick. */
    void orbit(Sphinx owner, Vec3 centre, float angle, double radius, double grow, float spin, float size, int life, float damageScale) {
        this.owner = owner;
        this.centre = centre;
        this.angle = angle;
        this.radius = radius;
        this.grow = grow;
        this.spin = spin;
        this.damageScale = damageScale;
        entityData.set(SIZE, size);
        entityData.set(LIFE, life);
        setPos(centre.add(Mth.cos(angle * Mth.DEG_TO_RAD) * radius, 0, Mth.sin(angle * Mth.DEG_TO_RAD) * radius));
    }

    /** How grown it is (0..1): rising, whole, blowing away. */
    public float strength(float partialTick) {
        float t = tickCount + partialTick;
        return Mth.clamp(Math.min(t / RISE, (life() - t) / FADE), 0, 1);
    }

    @Override
    public void tick() {
        super.tick();
        float size = size();
        if (level().isClientSide) {
            // sand thrown off it, whirling round
            float k = strength(0);
            for (int i = 0; i < 3; i++) {
                if (random.nextFloat() > k) continue;
                float h = random.nextFloat();
                double r = (0.5 + h * 2.1) * size;
                float a = random.nextFloat() * Mth.TWO_PI;
                double x = getX() + Mth.cos(a) * r, z = getZ() + Mth.sin(a) * r;
                level().addParticle(ModParticles.SAND.get(), x, getY() + h * HEIGHT * size, z,
                        -Mth.sin(a) * 0.35, 0.05, Mth.cos(a) * 0.35);
            }
            if (random.nextFloat() < k) {
                level().addParticle(ModParticles.SAND.get(), getX() + (random.nextDouble() - 0.5) * 2 * size, getY() + 0.1,
                        getZ() + (random.nextDouble() - 0.5) * 2 * size, (random.nextDouble() - 0.5) * 0.3, 0.02, (random.nextDouble() - 0.5) * 0.3);
            }
            return;
        }
        if (owner == null || !owner.isAlive() || tickCount > life()) {
            discard();
            return;
        }
        // how it moves
        if (centre != null) {
            angle += spin;
            radius += grow;
            setPos(centre.add(Mth.cos(angle * Mth.DEG_TO_RAD) * radius, 0, Mth.sin(angle * Mth.DEG_TO_RAD) * radius));
        } else {
            if (prey == null || !prey.isAlive() || prey.distanceToSqr(this) > 48 * 48 || tickCount % 40 == 0) prey = owner.nearestFighter(position());
            if (prey != null) {
                Vec3 to = new Vec3(prey.getX() - getX(), 0, prey.getZ() - getZ());
                if (to.lengthSqr() > 0.04) setPos(position().add(to.normalize().scale(CHASE_SPEED)));
            }
        }
        if (tickCount % 20 == 0) level().playSound(null, getX(), getY(), getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 0.7F, 0.6F);
        if (strength(0) < 0.5F) return;
        // whoever stands in its foot is hurt
        if (tickCount % 10 != 0) return;
        double foot = 1.5 * size, top = HEIGHT * size;
        AABB box = new AABB(getX() - foot, getY() - 1, getZ() - foot, getX() + foot, getY() + top, getZ() + foot);
        for (Player p : level().getEntitiesOfClass(Player.class, box, p -> p.isAlive() && !p.isCreative() && !p.isSpectator())) {
            if (new Vec3(getX() - p.getX(), 0, getZ() - p.getZ()).length() > foot) continue;
            if (p.hurt(damageSources().mobAttack(owner), DAMAGE * damageScale)) {
                ((ServerLevel) level()).sendParticles(ModParticles.SAND.get(), p.getX(), p.getY() + 1, p.getZ(), 10, 0.4, 0.5, 0.4, 0.05);
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
