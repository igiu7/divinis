package com.divinis.fabric.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.divinis.fabric.DropsCapture;
import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.LivingDropsEvent;
import com.divinis.platform.event.LivingEvent;
import com.divinis.platform.event.LivingFallEvent;
import com.divinis.platform.event.LivingHealEvent;
import com.divinis.platform.event.LivingIncomingDamageEvent;
import com.divinis.platform.event.LivingShieldBlockEvent;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

/** A creature's damage, healing, falls, shield, visibility and death drops, as NeoForge's events have them. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements DropsCapture {
    @Shadow protected int lastHurtByPlayerTime;

    @Shadow
    public abstract boolean isDamageSourceBlocked(DamageSource source);

    @Shadow
    protected abstract void hurtCurrentlyUsedShield(float amount);

    @Unique private boolean divinis$unblock;
    @Unique private List<ItemEntity> divinis$drops;

    @Override
    public List<ItemEntity> divinis$captured() {
        return divinis$drops;
    }

    @WrapMethod(method = "hurt")
    private boolean divinis$hurt(DamageSource source, float amount, Operation<Boolean> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide || self.isInvulnerableTo(source) || self.isDeadOrDying()) return original.call(source, amount);
        LivingIncomingDamageEvent e = DivinisBus.post(new LivingIncomingDamageEvent(self, source, amount));
        if (e.isCanceled()) return false;
        float a = e.getAmount();
        boolean unblock = false;
        if (a > 0 && DivinisBus.hasListeners(LivingShieldBlockEvent.class) && isDamageSourceBlocked(source)) {
            LivingShieldBlockEvent s = DivinisBus.post(new LivingShieldBlockEvent(self, source, a, true));
            if (!s.getBlocked()) {
                unblock = true;
            } else if (s.getBlockedDamage() < a) { // part of the hit goes through the shield
                hurtCurrentlyUsedShield(s.getBlockedDamage());
                a -= s.getBlockedDamage();
                unblock = true;
            }
        }
        boolean was = divinis$unblock;
        divinis$unblock = unblock;
        try {
            return original.call(source, a);
        } finally {
            divinis$unblock = was;
        }
    }

    @Redirect(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isDamageSourceBlocked(Lnet/minecraft/world/damagesource/DamageSource;)Z"))
    private boolean divinis$blocked(LivingEntity self, DamageSource source) {
        return !divinis$unblock && self.isDamageSourceBlocked(source);
    }

    @WrapMethod(method = "heal")
    private void divinis$heal(float amount, Operation<Void> original) {
        LivingHealEvent e = DivinisBus.post(new LivingHealEvent((LivingEntity) (Object) this, amount));
        if (!e.isCanceled()) original.call(e.getAmount());
    }

    @WrapMethod(method = "causeFallDamage")
    private boolean divinis$fall(float distance, float multiplier, DamageSource source, Operation<Boolean> original) {
        LivingFallEvent e = DivinisBus.post(new LivingFallEvent((LivingEntity) (Object) this, distance, multiplier));
        if (e.isCanceled()) return false;
        return original.call(e.getDistance(), e.getDamageMultiplier(), source);
    }

    @Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
    private void divinis$visibility(Entity looking, CallbackInfoReturnable<Double> cir) {
        if (!DivinisBus.hasListeners(LivingEvent.LivingVisibilityEvent.class)) return;
        LivingEvent.LivingVisibilityEvent e = DivinisBus.post(new LivingEvent.LivingVisibilityEvent((LivingEntity) (Object) this, looking, 1.0));
        if (e.getVisibilityModifier() != 1.0) cir.setReturnValue(cir.getReturnValue() * e.getVisibilityModifier());
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void divinis$captureDrops(ServerLevel level, DamageSource source, CallbackInfo ci) {
        divinis$drops = new ArrayList<>();
    }

    @Inject(method = "dropAllDeathLoot", at = @At("TAIL"))
    private void divinis$drops(ServerLevel level, DamageSource source, CallbackInfo ci) {
        List<ItemEntity> drops = divinis$drops;
        divinis$drops = null;
        if (drops == null) return;
        LivingDropsEvent e = DivinisBus.post(new LivingDropsEvent((LivingEntity) (Object) this, source, drops, lastHurtByPlayerTime > 0));
        if (!e.isCanceled()) for (ItemEntity item : e.getDrops()) level.addFreshEntity(item);
    }
}
