package com.divinis.fabric.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.client.MovementInputUpdateEvent;

import net.minecraft.client.player.LocalPlayer;

/** The movement keys were read (they can still be changed). */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/Input;tick(ZF)V", shift = At.Shift.AFTER))
    private void divinis$input(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (DivinisBus.hasListeners(MovementInputUpdateEvent.class)) DivinisBus.post(new MovementInputUpdateEvent(self, self.input));
    }
}
