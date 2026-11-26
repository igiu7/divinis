package com.divinis.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.divinis.fabric.DropsCapture;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** While a creature collects its death drops (DropsCapture), what it drops goes into the collection. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void divinis$capture(ItemStack stack, float offset, CallbackInfoReturnable<ItemEntity> cir) {
        if (!(this instanceof DropsCapture capture) || capture.divinis$captured() == null) return;
        Entity self = (Entity) (Object) this;
        if (stack.isEmpty() || self.level().isClientSide) {
            cir.setReturnValue(null);
            return;
        }
        ItemEntity item = new ItemEntity(self.level(), self.getX(), self.getY() + offset, self.getZ(), stack);
        item.setDefaultPickUpDelay();
        capture.divinis$captured().add(item);
        cir.setReturnValue(item);
    }
}
