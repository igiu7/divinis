package com.divinis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.divinis.client.CreatorFx;
import com.divinis.client.CreatorSky;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;

/**
 * The sky slowing to a stop round the Nameless Creator (client.CreatorSky): the sun and the clouds drawn behind time;
 * and no clouds while its kaleidoscope is the sky (client.CreatorFx), so the kaleidoscope covers all of it.
 * Not required: a shader pack that draws its own sky only loses the effect.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSkyMixin {
    @Shadow
    private int ticks;

    @Redirect(method = "renderSky", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F"))
    private float divinis$skyTime(ClientLevel level, float partialTick) {
        return CreatorSky.timeOfDay(level, partialTick);
    }

    @Inject(method = "renderClouds", require = 0, at = @At("HEAD"), cancellable = true)
    private void divinis$noClouds(CallbackInfo ci) {
        if (CreatorFx.skyIsKaleidoscope()) ci.cancel();
    }

    @ModifyVariable(method = "renderClouds", require = 0, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float divinis$cloudPartial(float partialTick) {
        return CreatorSky.cloudPartial(ticks, partialTick);
    }

    @Redirect(method = "renderClouds", require = 0, at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;ticks:I"))
    private int divinis$cloudTicks(LevelRenderer self) {
        return CreatorSky.cloudTicks();
    }
}
