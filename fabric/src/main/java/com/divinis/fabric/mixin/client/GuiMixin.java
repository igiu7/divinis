package com.divinis.fabric.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.divinis.fabric.FabricHud;
import com.divinis.platform.event.client.RegisterGuiLayersEvent.Where;
import com.divinis.platform.event.client.VanillaGuiLayers;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/** Vanilla's HUD layers as NeoForge names them: each can be cancelled, Divinis' layers go in between. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void divinis$below(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        FabricHud.draw(Where.BELOW_ALL, null, g, delta);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void divinis$above(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 2000);
        FabricHud.draw(Where.ABOVE_ALL, null, g, delta);
        g.pose().popPose();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void divinis$crosshair(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (!FabricHud.pre(VanillaGuiLayers.CROSSHAIR, g)) ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At("TAIL"))
    private void divinis$aboveCrosshair(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        FabricHud.draw(Where.ABOVE, VanillaGuiLayers.CROSSHAIR, g, delta);
    }

    @Inject(method = "renderChat", at = @At("HEAD"), cancellable = true)
    private void divinis$chat(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (!FabricHud.pre(VanillaGuiLayers.CHAT, g)) ci.cancel();
    }

    @Inject(method = "renderChat", at = @At("TAIL"))
    private void divinis$aboveChat(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        FabricHud.draw(Where.ABOVE, VanillaGuiLayers.CHAT, g, delta);
    }

    @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
    private void divinis$hotbar(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (!FabricHud.pre(VanillaGuiLayers.HOTBAR, g)) ci.cancel();
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void divinis$xpBar(GuiGraphics g, int x, CallbackInfo ci) {
        if (!FabricHud.pre(VanillaGuiLayers.EXPERIENCE_BAR, g)) ci.cancel();
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
    private void divinis$xpLevel(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (!FabricHud.pre(VanillaGuiLayers.EXPERIENCE_LEVEL, g)) ci.cancel();
    }

    @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void divinis$itemName(GuiGraphics g, CallbackInfo ci) {
        if (!FabricHud.pre(VanillaGuiLayers.SELECTED_ITEM_NAME, g)) ci.cancel();
    }

    @Inject(method = "renderHearts", at = @At("HEAD"), cancellable = true)
    private void divinis$hearts(GuiGraphics g, Player player, int x, int y, int rowHeight, int regen, float maxHealth, int health,
                                int displayHealth, int absorption, boolean blinking, CallbackInfo ci) {
        if (!FabricHud.pre(VanillaGuiLayers.PLAYER_HEALTH, g)) ci.cancel();
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private static void divinis$armor(GuiGraphics g, Player player, int y, int rows, int rowHeight, int x, CallbackInfo ci) {
        if (!FabricHud.pre(VanillaGuiLayers.ARMOR_LEVEL, g)) ci.cancel();
    }

    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void divinis$food(GuiGraphics g, Player player, int y, int x, CallbackInfo ci) {
        if (!FabricHud.pre(VanillaGuiLayers.FOOD_LEVEL, g)) ci.cancel();
    }
}
