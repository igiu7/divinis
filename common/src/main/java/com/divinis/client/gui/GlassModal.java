package com.divinis.client.gui;

import java.util.List;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.ModalPanel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** A dialog over a {@link ModuleScreen}, in its glass and violet: the screen behind dims, the dialog fades in. */
abstract class GlassModal extends ModalPanel {
    static final int TITLE_H = 24;

    private final String title;
    private final Color4I accent;
    private final long openedAt = System.currentTimeMillis();

    GlassModal(BaseScreen gui, String title, Color4I accent, int w, int h) {
        super(gui);
        this.title = title;
        this.accent = accent;
        setSize(w, h);
    }

    /** Centres it on its screen and shows it. */
    void open() {
        BaseScreen gui = getGui();
        gui.pushModalPanel(this);
        // after the push: it clamps the position as if it were the screen's, which pushes the dialog right and down
        setPos((gui.width - width) / 2, (gui.height - height) / 2);
    }

    void close() {
        getGui().closeModalPanel(this);
    }

    @Override
    public void alignWidgets() {
    }

    @Override
    public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        GuiHelper.setupDrawing();
        float e = Anim.easeOut(Anim.progress(openedAt, 200));
        g.pose().pushPose();
        g.pose().translate(0, 0, 900);
        Color4I.BLACK.withAlpha((int) (120 * e)).draw(g, 0, 0, getWindow().getGuiScaledWidth(), getWindow().getGuiScaledHeight());
        g.pose().translate(0, (1F - e) * 6F, 0);
        super.draw(g, theme, x, y, w, h);
        g.pose().popPose();
    }

    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        for (int i = 1; i <= 3; i++) ModuleScreen.roundOutline(g, x - i, y - i, w + i * 2, h + i * 2, accent.withAlpha(26 - i * 7));
        ModuleScreen.round(g, x, y, w, h, Color4I.rgba(10, 8, 16, 242));
        ModuleScreen.roundOutline(g, x, y, w, h, accent.withAlpha(150));
        Ui.text(g, theme, Component.literal(title).withStyle(ChatFormatting.BOLD), x + 10, y + 9, ModuleScreen.TEXT, true);
        ModuleScreen.glowLine(g, x + 1, y + TITLE_H - 1, w - 2, 1F);
    }

    /** "Are you sure?": the message, then Yes (in the accent) and Cancel. */
    static void confirm(BaseScreen gui, String title, String message, Color4I accent, Runnable onYes) {
        Theme theme = gui.getTheme();
        int w = 240;
        List<FormattedText> lines = theme.listFormattedStringToWidth(Component.literal(message), w - 20);
        new GlassModal(gui, title, accent, w, TITLE_H + 10 + lines.size() * 10 + 34) {
            @Override
            public void addWidgets() {
                Pill yes = new Pill(this, "Yes", accent, () -> {
                    close();
                    onYes.run();
                });
                yes.setPosAndSize(w / 2 - 94, height - 26, 90, 16);
                add(yes);
                Pill cancel = new Pill(this, "Cancel", ModuleScreen.MUTED, this::close);
                cancel.setPosAndSize(w / 2 + 4, height - 26, 90, 16);
                add(cancel);
            }

            @Override
            public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
                super.drawBackground(g, theme, x, y, w, h);
                for (int i = 0; i < lines.size(); i++) Ui.text(g, theme, lines.get(i), x + 10, y + TITLE_H + 8 + i * 10, ModuleScreen.MUTED, false);
            }
        }.open();
    }
}
