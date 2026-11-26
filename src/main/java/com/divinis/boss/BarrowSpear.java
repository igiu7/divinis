package com.divinis.boss;

import com.divinis.registry.ModParticles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Barrow Chieftain's spear in flight (boss.BarrowChieftain) - only seen, the warnings on the ground do the hurting:
 * - thrown: it flies in an arc onto its circle, sticks there a moment and flies back into his hand;
 * - falling (the rain of spears): it drops point first out of the sky onto its circle, stands in the ground a while and
 *   sinks into it.
 * It is turned along its flight (its yaw and pitch, sent to the clients); client.BarrowSpearRenderer draws the spear
 * of his model, a trail of soul fire behind it.
 */
public class BarrowSpear extends Entity {
    private static final int STAND = 24;

    private Vec3 from = Vec3.ZERO, to = Vec3.ZERO;
    private int flight = 1, back = 0, age;
    private boolean falling;
    private int ownerId = -1;

    public BarrowSpear(EntityType<? extends BarrowSpear> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Thrown from a point onto another in flight ticks; back into the thrower's hand in back ticks. */
    public void thrown(Vec3 from, Vec3 to, int flight, int back, Entity owner) {
        this.from = from;
        this.to = to;
        this.flight = Math.max(1, flight);
        this.back = back;
        this.ownerId = owner.getId();
        moveTo(from.x, from.y, from.z, 0, 0);
        aim(to.subtract(from).add(0, 2, 0));
    }

    /** Falling out of the sky onto a point in fall ticks. */
    public void falling(Vec3 at, int fall) {
        this.falling = true;
        this.from = at.add(0, 16, 0);
        this.to = at.add(0, 1.2, 0);
        this.flight = Math.max(1, fall);
        moveTo(from.x, from.y, from.z, random.nextFloat() * 360, 90);
        setXRot(90);
    }

    /** Turns it to point along a direction. */
    private void aim(Vec3 dir) {
        if (dir.lengthSqr() < 1e-6) return;
        double h = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        setYRot((float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F);
        setXRot((float) -(Mth.atan2(dir.y, h) * Mth.RAD_TO_DEG));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount > 1 && (getX() != xo || getY() != yo || getZ() != zo)) {
                level().addParticle(ModParticles.GHOST_FLAME.get(), xo, yo, zo, 0, 0, 0);
            }
            return;
        }
        age++;
        if (age <= flight) {
            float k = age / (float) flight;
            Vec3 p = falling ? from.lerp(to, k * k) : from.lerp(to, k).add(0, Math.sin(k * Math.PI) * 3.0, 0);
            Vec3 was = position();
            setPos(p.x, p.y, p.z);
            if (!falling) aim(p.subtract(was));
            return;
        }
        if (falling) {
            if (age > flight + STAND) setPos(getX(), getY() - 0.12, getZ()); // sinking into the ground
            if (age > flight + STAND + 14) discard();
            return;
        }
        // back to the thrower's hand
        Entity owner = level().getEntity(ownerId);
        int t = age - flight;
        if (owner == null || t > back + 2) {
            discard();
            return;
        }
        if (t > 2) {
            Vec3 hand = owner.position().add(0, owner.getBbHeight() * 0.85, 0);
            float k = Math.min(1, (t - 2) / (float) Math.max(1, back - 2));
            Vec3 p = to.lerp(hand, k);
            Vec3 was = position();
            setPos(p.x, p.y, p.z);
            aim(was.subtract(p)); // butt first, the point trailing towards the ground it left
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
