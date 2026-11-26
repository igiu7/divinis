package com.divinis.fabric.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.client.RenderHandEvent;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** A first-person hand is about to be drawn (cancelled: it is not). */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void divinis$hand(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack,
                              float equip, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (DivinisBus.hasListeners(RenderHandEvent.class)
                && DivinisBus.post(new RenderHandEvent(hand, pose, buffers, light, partialTick, stack)).isCanceled()) {
            ci.cancel();
        }
    }
}
