package com.divinis.client.gui;

import java.util.function.BooleanSupplier;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A button in the style of {@link ModuleScreen}: a tinted glass pill that lights up under the mouse. */
final class Pill extends Button {
    private final Supplier<String> label;
    private final Color4I color;
    private final BooleanSupplier enabled;
    private final Runnable action;
    private Consumer<TooltipList> tooltip = list -> {};
    private final Anim hover = new Anim();

    Pill(Panel panel, Supplier<String> label, Color4I color, BooleanSupplier enabled, Runnable action) {
        super(panel, Component.empty(), Icon.empty());
        this.label = label;
        this.color = color;
        this.enabled = enabled;
        this.action = action;
    }

    Pill(Panel panel, String label, Color4I color, Runnable action) {
        this(panel, () -> label, color, () -> true, action);
    }

    Pill tooltip(Consumer<TooltipList> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Override
    public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        GuiHelper.setupDrawing();
        boolean on = enabled.getAsBoolean();
        float k = hover.toward(on && isMouseOver(), 14F);
        Color4I c = on ? color : ModuleScreen.MUTED;
        ModuleScreen.round(g, x, y, w, h, c.withAlpha(on ? (int) (40 + 90 * k) : 18));
        ModuleScreen.roundOutline(g, x, y, w, h, c.withAlpha(on ? (int) (150 + 105 * k) : 70));
        if (on && k > 0.01F) Color4I.WHITE.withAlpha((int) (40 * k)).draw(g, x + 2, y + 1, w - 4, 1);
        theme.drawString(g, Ui.fit(theme, label.get(), w - 6), x + w / 2, y + (h - 8) / 2,
                on ? ModuleScreen.TEXT : ModuleScreen.MUTED, Theme.CENTERED | (on ? Theme.SHADOW : 0));
    }

    @Override
    public void onClicked(MouseButton button) {
        if (!enabled.getAsBoolean()) return;
        playClickSound();
        action.run();
    }

    @Override
    public void addMouseOverText(TooltipList list) {
        tooltip.accept(list);
    }
}
