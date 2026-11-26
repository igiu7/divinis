package com.divinis.boss;

import java.util.Optional;
import java.util.UUID;

import com.divinis.registry.ModParticles;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A ballista bolt (boss.Ballista): it flies straight and fast; striking Ignaroth it counts as a hit by who shot it
 * (Ignaroth.ballistaHit); striking a block it sticks there a while. It hurts nothing else.
 */
public class BallistaBolt extends Entity {
    private static final double SPEED = 2.6;
    private static final int LIFE = 50, STUCK = 40;

    private Vec3 dir = Vec3.ZERO;
    private Optional<UUID> shooter = Optional.empty();
    private int stuck = -1;

    public BallistaBolt(EntityType<? extends BallistaBolt> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    void shoot(Vec3 from, Vec3 dir, Player by) {
        this.dir = dir.normalize();
        this.shooter = Optional.of(by.getUUID());
        moveTo(from.x, from.y, from.z, 0, 0);
        aim();
    }

    private void aim() {
        double h = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        setYRot((float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90);
        setXRot((float) -(Mth.atan2(dir.y, h) * Mth.RAD_TO_DEG));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (getX() != xo || getZ() != zo) level().addParticle(ParticleTypes.CRIT, xo, yo, zo, 0, 0, 0);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (stuck >= 0) {
            if (++stuck > STUCK) discard();
            return;
        }
        if (tickCount > LIFE) {
            discard();
            return;
        }
        Vec3 from = position(), to = from.add(dir.scale(SPEED));
        AABB sweep = new AABB(from, to).inflate(1.0);
        for (Ignaroth dragon : level.getEntitiesOfClass(Ignaroth.class, sweep, Entity::isAlive)) {
            if (dragon.getBoundingBox().inflate(0.8).clip(from, to).isPresent() || dragon.getBoundingBox().inflate(0.8).contains(to)) {
                Player p = shooter.map(level::getPlayerByUUID).orElse(null);
                if (p != null) dragon.ballistaHit(p, this);
                level.sendParticles(ModParticles.EMBER.get(), to.x, to.y, to.z, 10, 0.3, 0.3, 0.3, 0.1);
                discard();
                return;
            }
        }
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 at = hit.getLocation().subtract(dir.scale(0.6));
            setPos(at.x, at.y, at.z);
            stuck = 0;
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 1.5F, 0.6F);
            return;
        }
        setPos(to.x, to.y, to.z);
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
