package com.divinis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;

/** The Dash roll's limbs (third person, visible to everyone). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void divinis$dashPose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, CallbackInfo ci) {
        try {
            com.divinis.client.DashClient.pose((HumanoidModel<?>) (Object) this, entity, ageInTicks);
        } catch (Throwable ignored) {
            // an animation must never crash rendering
        }
    }
}
