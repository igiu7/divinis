package com.divinis.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.EntityTeleportEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;

/** An ender pearl is about to teleport its player (cancelled: it breaks without). */
@Mixin(ThrownEnderpearl.class)
public abstract class ThrownEnderpearlMixin {
    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void divinis$pearl(HitResult hit, CallbackInfo ci) {
        ThrownEnderpearl self = (ThrownEnderpearl) (Object) this;
        if (self.level().isClientSide || !(self.getOwner() instanceof ServerPlayer player)) return;
        if (DivinisBus.post(new EntityTeleportEvent.EnderPearl(player)).isCanceled()) {
            self.discard();
            ci.cancel();
        }
    }
}
