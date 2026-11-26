package com.divinis.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.divinis.registry.ItemRarity;
import com.divinis.Divinis;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import com.divinis.platform.Dist;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.client.ClientPlayerNetworkEvent;
import com.divinis.platform.event.client.RegisterGuiLayersEvent;
import com.divinis.platform.event.client.VanillaGuiLayers;

/**
 * What was just caught (fishing.FishingGame), bottom left: "+1" and the item's icon on a dark strip edged in its
 * rarity's colour. A new one slides in from the left with a little bounce, the older ones glide up to make room;
 * the same item caught again soon after counts up on its strip (the icon pops); after a few seconds each slides back
 * out and fades.
 */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class CatchPopups {
    private static final float ENTER = 0.32F, HOLD = 3.0F, EXIT = 0.35F, BUMP = 0.25F, MERGE = 2.0F; // seconds
    private static final int ROW = 20, HEIGHT = 18, MAX = 5;

    private static final class Popup {
        final ItemStack stack;
        int count;
        final long born;
        long seen, bumped;
        float y = Float.NaN;

        Popup(ItemStack stack, long now) {
            this.stack = stack.copyWithCount(1);
            this.count = stack.getCount();
            this.born = this.seen = now;
            this.bumped = 0;
        }
    }

    private static final List<Popup> POPUPS = new ArrayList<>();
    private static long lastFrame = System.nanoTime();

    private CatchPopups() {}

    private static float seconds(long from, long to) {
        return (to - from) / 1.0e9F;
    }

    public static void receive(List<ItemStack> stacks) {
        long now = System.nanoTime();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            Popup same = null;
            for (Popup p : POPUPS) {
                if (seconds(p.seen, now) < MERGE && ItemStack.isSameItemSameComponents(p.stack, stack)) same = p;
            }
            if (same != null) {
                same.count += stack.getCount();
                same.seen = same.bumped = now;
            } else {
                POPUPS.add(new Popup(stack, now));
            }
        }
        while (POPUPS.size() > MAX) POPUPS.remove(0);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        POPUPS.clear();
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "catch_popups"),
                CatchPopups::render);
    }

    private static float backOut(float t) {
        float c = 1.7F, u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    private static int argb(float a, int rgb) {
        return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int accent(ItemStack stack) {
        ItemRarity rarity = ItemRarity.of(stack.getItem());
        if (rarity != null) return rarity.color;
        Integer vanilla = stack.getRarity().color().getColor();
        return vanilla == null ? 0xFFFFFF : vanilla;
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1F, seconds(lastFrame, now));
        lastFrame = now;
        POPUPS.removeIf(p -> seconds(p.seen, now) > HOLD + EXIT);
        Minecraft mc = Minecraft.getInstance();
        if (POPUPS.isEmpty() || mc.options.hideGui) return;
        Font font = mc.font;
        int bottom = g.guiHeight() - 26;
        RenderSystem.enableBlend();
        for (int i = POPUPS.size() - 1, slot = 0; i >= 0; i--, slot++) {
            Popup p = POPUPS.get(i);
            float target = bottom - slot * ROW;
            p.y = Float.isNaN(p.y) ? target : p.y + (target - p.y) * (1 - (float) Math.exp(-dt * 14)); // glide up
            float age = seconds(p.born, now), left = seconds(p.seen, now) - HOLD;
            float in = Mth.clamp(age / ENTER, 0, 1), out = Mth.clamp(left / EXIT, 0, 1);
            float alpha = Math.min(1, age / 0.12F) * (1 - out);
            if (alpha <= 0.02F) continue;
            float slide = -70 * (1 - backOut(in)) - 50 * out * out;
            float pop = 1 + 0.35F * (1 - Mth.clamp(seconds(p.bumped, now) / BUMP, 0, 1)) * (p.bumped == 0 ? 0 : 1);
            pop *= 0.6F + 0.4F * Math.min(1, backOut(in)); // the icon pops in with the strip
            pop *= 1 - 0.5F * out;

            String text = "+" + p.count;
            int textW = font.width(text);
            int w = 2 + 5 + textW + 4 + 16 + 5;
            int x = Math.round(6 + slide), y = Math.round(p.y);
            g.fill(x, y, x + w, y + HEIGHT, argb(alpha * 0.55F, 0x0A0D12));
            g.fill(x, y, x + w, y + 1, argb(alpha * 0.25F, 0xFFFFFF));
            g.fill(x, y, x + 2, y + HEIGHT, argb(alpha, accent(p.stack)));
            g.drawString(font, text, x + 7, y + 5, argb(alpha, 0xFFFFFF), true);

            g.pose().pushPose();
            g.pose().translate(x + 7 + textW + 4 + 8, y + HEIGHT / 2.0F, 0);
            g.pose().scale(pop, pop, 1);
            g.renderItem(p.stack, -8, -8);
            g.pose().popPose();
        }
        RenderSystem.disableBlend();
    }
}
