package com.divinis.fabric.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.client.ViewportEvent;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;

/** The fog's colour and distances, as NeoForge's ViewportEvents change them. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

    @Inject(method = "setupColor", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clearColor(FFFF)V"))
    private static void divinis$color(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorld, CallbackInfo ci) {
        if (!DivinisBus.hasListeners(ViewportEvent.ComputeFogColor.class)) return;
        ViewportEvent.ComputeFogColor e = DivinisBus.post(new ViewportEvent.ComputeFogColor(camera, partialTick, fogRed, fogGreen, fogBlue));
        fogRed = e.getRed();
        fogGreen = e.getGreen();
        fogBlue = e.getBlue();
    }

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void divinis$fog(Camera camera, FogRenderer.FogMode mode, float renderDistance, boolean thick, float partialTick, CallbackInfo ci) {
        if (!DivinisBus.hasListeners(ViewportEvent.RenderFog.class)) return;
        ViewportEvent.RenderFog e = DivinisBus.post(new ViewportEvent.RenderFog(mode, camera, partialTick, RenderSystem.getShaderFogStart(),
                RenderSystem.getShaderFogEnd(), RenderSystem.getShaderFogShape()));
        if (e.isCanceled()) {
            RenderSystem.setShaderFogStart(e.getNearPlaneDistance());
            RenderSystem.setShaderFogEnd(e.getFarPlaneDistance());
            RenderSystem.setShaderFogShape(e.getFogShape());
        }
    }
}
