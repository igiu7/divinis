package com.divinis.boss;

import javax.annotation.Nullable;

import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenEffect.Anchor;
import com.divinis.screenfx.ScreenFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Wildfire's fireball: flies straight at the spot its target stood on (marked on the ground) and bursts on the
 * first thing it touches - hard damage around it and everyone caught set on fire. Drawn by client.WildfireFireballRenderer.
 * Never saved: a fireball does not outlive a restart.
 */
public class WildfireFireball extends Entity {
    static final double SPEED = 0.9;
    static final float DAMAGE = 18, RADIUS = 2.6F;
    static final int FIRE_SECONDS = 6, MAX_LIFE = 120;
    private static final byte EVENT_BURST = 3;

    @Nullable private Wildfire owner;
    private Vec3 velocity = Vec3.ZERO;
    private float damageScale = 1;

    public WildfireFireball(EntityType<? extends WildfireFireball> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    void launch(Wildfire owner, Vec3 to, float damageScale) {
        this.owner = owner;
        this.damageScale = damageScale;
        this.velocity = to.subtract(position()).normalize().scale(SPEED);
        setDeltaMovement(velocity);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (tickCount > MAX_LIFE || owner == null) {
            burst((ServerLevel) level(), position());
            return;
        }
        Vec3 from = position(), to = from.add(velocity);
        // a creature in the way
        AABB swept = getBoundingBox().expandTowards(velocity).inflate(0.4);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, swept, e -> e != owner && e.isAlive()
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            burst((ServerLevel) level(), e.position().add(0, e.getBbHeight() / 2, 0));
            return;
        }
        // a wall or the ground
        BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            burst((ServerLevel) level(), hit.getLocation());
            return;
        }
        setPos(to);
        setDeltaMovement(velocity);
        if (tickCount % 2 == 0) ((ServerLevel) level()).sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 1, 0.1, 0.1, 0.1, 0.0);
    }

    /** Bursts at a place: damage and fire around it, the explosion shown on the clients (FireFx). */
    private void burst(ServerLevel level, Vec3 at) {
        setPos(at);
        DamageSource source = owner != null ? level.damageSources().mobAttack(owner) : level.damageSources().onFire();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(RADIUS), e -> e != owner && e.isAlive()
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            if (e.distanceToSqr(at) > RADIUS * RADIUS * 1.2) continue;
            if (e.hurt(source, DAMAGE * damageScale)) {
                e.igniteForSeconds(FIRE_SECONDS);
                Vec3 push = e.position().subtract(at).multiply(1, 0, 1).normalize().scale(0.6);
                e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.35, push.z));
                e.hurtMarked = true;
            }
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6F, 1.1F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.4F, 0.6F);
        level.broadcastEntityEvent(this, EVENT_BURST);
        ScreenFx.shake(level, at, 14, 8, 0.45F);
        discard();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_BURST) com.divinis.client.FireFx.burst(position(), RADIUS * 1.6F);
        else super.handleEntityEvent(id);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
