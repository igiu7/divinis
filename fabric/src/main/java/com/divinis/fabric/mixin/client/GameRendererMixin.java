package com.divinis.fabric.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.client.ViewportEvent;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

/** The camera's angles (with a roll) and the field of view, as NeoForge's ViewportEvents change them. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void divinis$fov(Camera camera, float partialTick, boolean useSetting, CallbackInfoReturnable<Double> cir) {
        if (!DivinisBus.hasListeners(ViewportEvent.ComputeFov.class)) return;
        cir.setReturnValue(DivinisBus.post(new ViewportEvent.ComputeFov(camera, partialTick, cir.getReturnValue(), useSetting)).getFOV());
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            shift = At.Shift.AFTER))
    private void divinis$angles(DeltaTracker delta, CallbackInfo ci) {
        if (!DivinisBus.hasListeners(ViewportEvent.ComputeCameraAngles.class)) return;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float pt = delta.getGameTimeDeltaPartialTick(true);
        ViewportEvent.ComputeCameraAngles e = DivinisBus.post(new ViewportEvent.ComputeCameraAngles(camera, pt, camera.getYRot(), camera.getXRot(), 0));
        if (e.getYaw() != camera.getYRot() || e.getPitch() != camera.getXRot()) ((CameraInvoker) camera).divinis$setRotation(e.getYaw(), e.getPitch());
        if (e.getRoll() != 0) {
            float d = (float) (Math.PI / 180.0);
            camera.rotation().rotationYXZ((float) Math.PI - e.getYaw() * d, -e.getPitch() * d, -e.getRoll() * d);
        }
    }
}
