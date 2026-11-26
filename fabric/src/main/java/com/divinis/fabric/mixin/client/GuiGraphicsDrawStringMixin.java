package com.divinis.fabric.mixin.client;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Adds drawString with float coordinates to GuiGraphics, as NeoForge has it. Diegesis' Fabric build hooks these
 * methods (which vanilla lacks: optional hooks, but in a development run Mixin fails on them); applied before
 * Diegesis' mixins (priority 500), they are there for them.
 */
@Mixin(value = GuiGraphics.class, priority = 500)
public abstract class GuiGraphicsDrawStringMixin {
    @Shadow @Final private MultiBufferSource.BufferSource bufferSource;

    @Shadow
    public abstract PoseStack pose();

    @Shadow
    protected abstract void flushIfUnmanaged();

    public int drawString(Font font, @Nullable String text, float x, float y, int color, boolean dropShadow) {
        if (text == null) return 0;
        int width = font.drawInBatch(text, x, y, color, dropShadow, pose().last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0,
                15728880, font.isBidirectional());
        flushIfUnmanaged();
        return width;
    }

    public int drawString(Font font, net.minecraft.util.FormattedCharSequence text, float x, float y, int color, boolean dropShadow) {
        int width = font.drawInBatch(text, x, y, color, dropShadow, pose().last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
        flushIfUnmanaged();
        return width;
    }
}
