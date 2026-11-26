package com.divinis.fabric.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.client.InputEvent;

import net.minecraft.client.KeyboardHandler;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("TAIL"))
    private void divinis$key(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (DivinisBus.hasListeners(InputEvent.Key.class)) DivinisBus.post(new InputEvent.Key(key, scanCode, action, modifiers));
    }
}
