package com.divinis.fabric.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.Camera;

@Mixin(Camera.class)
public interface CameraInvoker {
    @Invoker("setRotation")
    void divinis$setRotation(float yaw, float pitch);
}
