package com.divinis.client.gui;

import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** A special button: an icon and two lines on a violet flame that flares up under the mouse, a shine sweeping across it. */
final class FlameButton extends Button {
    static final int H = 34;

    private final String label;
    private final Supplier<String> sub;
    private final Supplier<Icon> iconOf;
    private final Runnable action;
    private Consumer<TooltipList> tooltip = list -> {};
    private final Anim hover = new Anim();
    private final float seed;

    FlameButton(Panel panel, String label, Supplier<String> sub, Supplier<Icon> icon, float seed, Runnable action) {
        super(panel, Component.literal(label), Icon.empty());
        this.label = label;
        this.sub = sub;
        this.iconOf = icon;
        this.seed = seed;
        this.action = action;
    }

    FlameButton tooltip(Consumer<TooltipList> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Override
    public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        GuiHelper.setupDrawing();
        Color4I violet = ModuleScreen.VIOLET, light = ModuleScreen.VIOLET_LIGHT;
        float k = hover.toward(isMouseOver(), 9F), p = Anim.pulse(2200, seed);
        for (int i = 1; i <= 3; i++) ModuleScreen.roundOutline(g, x - i, y - i, w + i * 2, h + i * 2, violet.withAlpha((int) ((10 + 14 * p + 30 * k) * (4 - i) / 3F)));
        ModuleScreen.round(g, x, y, w, h, Color4I.rgba(6, 3, 12, 220));
        Flame.draw(g, x + 1, y + 1, w - 2, h - 2, 0.55F + 0.12F * p + 0.33F * k, seed);
        float sweep = ((System.currentTimeMillis() + (long) (seed * 1000)) % 3400L) / 1400F;
        if (sweep < 1F) {
            int sx = x + (int) ((w + 20) * sweep) - 10;
            for (int i = 0; i < 8; i++) {
                int px = sx + i;
                if (px > x + 1 && px < x + w - 1) Color4I.WHITE.withAlpha((int) (26 * Mth.sin(i / 8F * Mth.PI))).draw(g, px, y + 1, 1, h - 2);
            }
        }
        ModuleScreen.roundOutline(g, x, y, w, h, light.withAlpha((int) (130 + 60 * p + 65 * k)));
        Color4I.WHITE.withAlpha((int) (30 + 40 * k)).draw(g, x + 3, y + 1, w - 6, 1);

        int ix = x + 7, iy = y + (h - 20) / 2;
        ModuleScreen.round(g, ix, iy, 20, 20, Color4I.rgba(0, 0, 0, 150));
        ModuleScreen.roundOutline(g, ix, iy, 20, 20, light.withAlpha(200));
        g.pose().pushPose();
        g.pose().translate(0, -1.5F * k, 0);
        iconOf.get().draw(g, ix + 2, iy + 2, 16, 16);
        g.pose().popPose();
        int tx = ix + 26;
        theme.drawString(g, Component.literal(Ui.fit(theme, label, w - (tx - x) - 4)).withStyle(ChatFormatting.BOLD), tx, y + 8, ModuleScreen.TEXT, Theme.SHADOW);
        theme.drawString(g, Ui.fit(theme, sub.get(), w - (tx - x) - 4), tx, y + 19, light.lerp(ModuleScreen.TEXT, k), Theme.SHADOW);
    }

    @Override
    public void onClicked(MouseButton button) {
        playClickSound();
        action.run();
    }

    @Override
    public void addMouseOverText(TooltipList list) {
        tooltip.accept(list);
    }
}
