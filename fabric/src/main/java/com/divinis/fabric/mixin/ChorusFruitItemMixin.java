package com.divinis.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.EntityTeleportEvent;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ChorusFruitItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A chorus fruit is about to teleport who ate it (cancelled: it is only eaten). */
@Mixin(ChorusFruitItem.class)
public abstract class ChorusFruitItemMixin {
    @Unique private static final ThreadLocal<Boolean> DIVINIS$STAY = ThreadLocal.withInitial(() -> false);

    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void divinis$chorus(ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        DIVINIS$STAY.set(!level.isClientSide && DivinisBus.post(new EntityTeleportEvent.ChorusFruit(entity)).isCanceled());
    }

    @Redirect(method = "finishUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;randomTeleport(DDDZ)Z"))
    private boolean divinis$teleport(LivingEntity entity, double x, double y, double z, boolean particles) {
        return !DIVINIS$STAY.get() && entity.randomTeleport(x, y, z, particles);
    }
}
