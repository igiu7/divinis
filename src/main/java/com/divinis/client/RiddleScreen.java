package com.divinis.client;

import java.util.ArrayList;
import java.util.List;

import com.divinis.net.RiddlePayloads;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Sphinx's riddle: a sheet of papyrus under a band of the nemes' gold and lapis, the riddle on it and four
 * answers to choose from; the sand running out below. One answer, then it waits for the Sphinx's word (the server
 * closes it: answered by someone, or failed). It cannot be put away: answer, or the time runs out.
 */
public class RiddleScreen extends Screen {
    private static final int PAPYRUS = 0xFFE6D2A2, PAPYRUS_DARK = 0xFFC9AE74, EDGE = 0xFF6E4E1E, GOLD = 0xFFE0B040, LAPIS = 0xFF23408A,
            INK = 0xFF3A2410, RED = 0xFF9A2A18;

    private final int boss;
    private final String question;
    private final List<String> options;
    private final long until;
    private final int ticks;
    private final List<Button> buttons = new ArrayList<>();
    private int chosen = -1;
    private int panelW, panelH, left, top;
    private List<FormattedCharSequence> lines = List.of();

    public RiddleScreen(int boss, String question, List<String> options, int ticks) {
        super(Component.literal("The Riddle of the Sphinx"));
        this.boss = boss;
        this.question = question;
        this.options = options;
        this.ticks = ticks;
        this.until = Util.getMillis() + ticks * 50L;
    }

    @Override
    protected void init() {
        buttons.clear();
        panelW = Math.min(380, width - 32);
        lines = font.split(Component.literal(question), panelW - 36);
        panelH = 64 + lines.size() * 11 + options.size() * 24 + 26;
        left = (width - panelW) / 2;
        top = Math.max(8, (height - panelH) / 2);
        int y = top + 52 + lines.size() * 11;
        for (int i = 0; i < options.size(); i++) {
            int index = i;
            Button b = Button.builder(Component.literal((char) ('A' + i) + ")  " + options.get(i)), btn -> choose(index))
                    .bounds(left + 24, y + i * 24, panelW - 48, 20).build();
            b.active = chosen < 0;
            buttons.add(addRenderableWidget(b));
        }
    }

    private void choose(int index) {
        if (chosen >= 0) return;
        chosen = index;
        for (Button b : buttons) b.active = false;
        PacketDistributor.sendToServer(new RiddlePayloads.Answer(boss, index));
    }

    @Override
    public void tick() {
        if (Util.getMillis() > until + 4000) super.onClose();   // the server never answered
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xB0281A08, 0xD0140C04);
        // the papyrus, its fibres, the band of gold and lapis above and below
        g.fill(left - 3, top - 3, left + panelW + 3, top + panelH + 3, EDGE);
        g.fill(left, top, left + panelW, top + panelH, PAPYRUS);
        for (int x = left + 3; x < left + panelW; x += 7) g.fill(x, top + 12, x + 1, top + panelH - 12, PAPYRUS_DARK & 0x60FFFFFF);
        for (int y = top + 15; y < top + panelH - 12; y += 9) g.fill(left + 2, y, left + panelW - 2, y + 1, PAPYRUS_DARK & 0x40FFFFFF);
        band(g, top);
        band(g, top + panelH - 10);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, Component.literal("The Riddle of the Sphinx").withStyle(s -> s.withBold(true)), width / 2, top + 18, GOLD);
        String sub = chosen < 0 ? "Answer rightly - or all of you are his" : "Your answer is spoken. The Sphinx weighs it...";
        int subW = font.width(sub);
        g.drawString(font, sub, width / 2 - subW / 2, top + 31, chosen < 0 ? RED : INK, false);
        int y = top + 46;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, width / 2 - font.width(line) / 2, y, INK, false);
            y += 11;
        }
        if (chosen >= 0) {
            Button b = buttons.get(chosen);
            g.renderOutline(b.getX() - 2, b.getY() - 2, b.getWidth() + 4, b.getHeight() + 4, GOLD);
        }
        // the sand running out
        float left_ = Mth.clamp((until - Util.getMillis()) / (ticks * 50.0F), 0, 1);
        int barY = top + panelH - 20, barW = panelW - 48;
        g.fill(left + 24, barY, left + 24 + barW, barY + 4, 0xFF5A4020);
        g.fill(left + 24, barY, left + 24 + (int) (barW * left_), barY + 4, left_ > 0.25F ? 0xFFD8A848 : 0xFFE05030);
        int secs = (int) Math.ceil(left_ * ticks / 20.0F);
        g.drawString(font, secs + "s", left + 24 + barW + 4 - font.width(secs + "s") - 2, barY - 10, INK, false);
    }

    private void band(GuiGraphics g, int y) {
        for (int x = left; x < left + panelW; x += 6) {
            g.fill(x, y, Math.min(x + 3, left + panelW), y + 10, GOLD);
            g.fill(Math.min(x + 3, left + panelW), y, Math.min(x + 6, left + panelW), y + 10, LAPIS);
        }
        g.fill(left, y + 4, left + panelW, y + 6, EDGE);
    }

    /** The server says it is over. */
    public void close() {
        super.onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
