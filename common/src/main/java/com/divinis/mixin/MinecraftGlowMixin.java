package com.divinis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.divinis.client.SkillFx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** The Hunter's Track: the tracked animals glow, for the tracker only (client.SkillFx). */
@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
    private void divinis$tracked(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && SkillFx.glowing(entity)) cir.setReturnValue(true);
    }
}
