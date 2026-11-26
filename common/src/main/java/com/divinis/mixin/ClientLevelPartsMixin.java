package com.divinis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.divinis.platform.entity.PartRegistry;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/** A part is found by its id on the client too. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelPartsMixin {
    @Inject(method = "getEntity(I)Lnet/minecraft/world/entity/Entity;", at = @At("RETURN"), cancellable = true)
    private void divinis$part(int id, CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() == null) {
            Entity part = PartRegistry.get((ClientLevel) (Object) this, id);
            if (part != null) cir.setReturnValue(part);
        }
    }
}
