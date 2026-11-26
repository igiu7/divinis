package com.divinis.boss;

import javax.annotation.Nullable;

import com.divinis.registry.ModEntities;
import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A ballista on the rim of Ignaroth's crater (boss.Ignaroth puts four there). Use it to man it: you stand behind it and
 * it turns and tilts with your view - aim with the mouse; hold jump to draw the string (the jump bar, as on a horse) and
 * let go to shoot a bolt (BallistaBolt) where you look. Then it is reloaded for RELOAD ticks (the bolt on its rail
 * shows when it is ready). Sneak to step away from it. It cannot be broken or moved.
 */
public class Ballista extends Entity implements PlayerRideableJumping {
    private static final EntityDataAccessor<Integer> RELOAD_LEFT = SynchedEntityData.defineId(Ballista.class, EntityDataSerializers.INT);
    public static final int RELOAD = 80;
    /** at least this much of the jump bar must be drawn to shoot */
    private static final int MIN_DRAW = 20;

    public Ballista(EntityType<? extends Ballista> type, Level level) {
        super(type, level);
    }

    /** Client: is a bolt on the rail? */
    public boolean loaded() {
        return entityData.get(RELOAD_LEFT) <= 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RELOAD_LEFT, 0);
    }

    @Override
    public void tick() {
        super.tick();
        Entity gunner = getFirstPassenger();
        if (gunner != null) { // it turns and tilts with its gunner's view
            yRotO = getYRot();
            xRotO = getXRot();
            setYRot(gunner.getYRot());
            setXRot(Mth.clamp(gunner.getXRot(), -60, 40));
        }
        if (!level().isClientSide && entityData.get(RELOAD_LEFT) > 0) {
            entityData.set(RELOAD_LEFT, entityData.get(RELOAD_LEFT) - 1);
            if (entityData.get(RELOAD_LEFT) == 0) {
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.BLOCKS, 1.5F, 0.6F);
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || isVehicle()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (player.startRiding(this) && player instanceof ServerPlayer sp) {
            ScreenFx.play(sp, ScreenEffect.TEXT, 70, 0.7F, 0xFFD080, "Aim with the mouse - hold JUMP, let go to shoot - SNEAK to leave");
        }
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------ manning it

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    /** The gunner stands behind it, on the ground. */
    @Override
    protected void positionRider(Entity rider, MoveFunction move) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        move.accept(rider, getX() + Mth.sin(yaw) * 1.3, getY(), getZ() - Mth.cos(yaw) * 1.3);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(getX() + Mth.sin(yaw) * 1.6, getY(), getZ() - Mth.cos(yaw) * 1.6);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return !isVehicle() && passenger instanceof Player;
    }

    // ------------------------------------------------------------------ the jump bar draws the string

    @Override
    public boolean canJump() {
        return loaded();
    }

    @Override
    public void onPlayerJump(int power) {}

    @Override
    public void handleStartJump(int power) {
        if (level().isClientSide || !loaded() || !(getFirstPassenger() instanceof Player gunner)) return;
        if (power < MIN_DRAW) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.CROSSBOW_LOADING_START.value(), SoundSource.PLAYERS, 1.0F, 1.4F);
            return;
        }
        fire(gunner);
    }

    @Override
    public void handleStopJump() {}

    private void fire(Player gunner) {
        Vec3 look = gunner.getLookAngle();
        Vec3 from = position().add(0, 1.6, 0).add(look.scale(1.6));
        BallistaBolt bolt = ModEntities.BALLISTA_BOLT.get().create(level());
        if (bolt != null) {
            bolt.shoot(from, look, gunner);
            level().addFreshEntity(bolt);
        }
        entityData.set(RELOAD_LEFT, RELOAD);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 3.0F, 0.5F);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 2.0F, 0.6F);
        ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD, from.x, from.y, from.z, 6, 0.2, 0.2, 0.2, 0.03);
    }

    // ------------------------------------------------------------------ the rest

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
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
