package com.divinis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.divinis.client.DashClient;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/** The Dash roll turns the whole player (after their facing, before the model; the name tag stays upright). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity> {
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void divinis$dashRoll(T entity, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale,
                                   CallbackInfo ci) {
        try {
            DashClient.roll(entity, pose, partialTick);
        } catch (Throwable ignored) {
            // an animation must never crash rendering
        }
    }
}
