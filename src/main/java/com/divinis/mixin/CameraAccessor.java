package com.divinis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

/** Moves the first-person camera along the Dash roll (client.DashClient). */
@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setPosition")
    void divinis$setPosition(Vec3 position);
}
