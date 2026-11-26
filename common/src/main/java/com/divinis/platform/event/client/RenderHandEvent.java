package com.divinis.platform.event.client;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** A hand (with its item) is about to be drawn in first person (cancelled: it is not). */
public class RenderHandEvent extends Event implements ICancellableEvent {
    private final InteractionHand hand;
    private final PoseStack poseStack;
    private final MultiBufferSource buffers;
    private final int light;
    private final float partialTick;
    private final ItemStack stack;

    public RenderHandEvent(InteractionHand hand, PoseStack poseStack, MultiBufferSource buffers, int light,
                           float partialTick, ItemStack stack) {
        this.hand = hand;
        this.poseStack = poseStack;
        this.buffers = buffers;
        this.light = light;
        this.partialTick = partialTick;
        this.stack = stack;
    }

    public InteractionHand getHand() { return hand; }
    public PoseStack getPoseStack() { return poseStack; }
    public MultiBufferSource getMultiBufferSource() { return buffers; }
    public int getPackedLight() { return light; }
    public float getPartialTick() { return partialTick; }
    public ItemStack getItemStack() { return stack; }
}
