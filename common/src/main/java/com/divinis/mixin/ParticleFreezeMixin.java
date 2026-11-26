package com.divinis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.divinis.client.CreatorFx;

import net.minecraft.client.particle.ParticleEngine;

/** Time stops round the Nameless Creator's judgement (client.CreatorFx): every particle hangs where it is. */
@Mixin(ParticleEngine.class)
public abstract class ParticleFreezeMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void divinis$frozen(CallbackInfo ci) {
        if (CreatorFx.timeStopped()) ci.cancel();
    }
}
