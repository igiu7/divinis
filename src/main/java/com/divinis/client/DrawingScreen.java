package com.divinis.client;

import java.util.ArrayList;
import java.util.List;

import com.divinis.boss.DrawShapes;
import com.divinis.net.DrawPayloads;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Nocthariel's trial of the shape: the shape on the left, a canvas on the right; draw it with the mouse (as many
 * strokes as you like), Clear to start again, Done to give it in - or the time runs out and what is there goes in.
 * Closing the screen gives it in as it is.
 */
public class DrawingScreen extends Screen {
    private static final int BG = 0xE0100608, PANEL = 0xFF1A0E12, PANEL_EDGE = 0xFF6A1018, INK = 0xFFE8D8C0, SHAPE = 0xFFD02030;

    private final int boss;
    private final DrawShapes.Shape shape;
    private final long until;
    private final int ticks;
    private final List<List<float[]>> strokes = new ArrayList<>();
    private boolean sent;
    private int size, leftX, rightX, top;

    public DrawingScreen(int boss, int shape, int ticks) {
        super(Component.literal("Draw the shape"));
        this.boss = boss;
        this.shape = DrawShapes.SHAPES.get(Mth.clamp(shape, 0, DrawShapes.SHAPES.size() - 1));
        this.ticks = ticks;
        this.until = Util.getMillis() + ticks * 50L;
    }

    @Override
    protected void init() {
        size = Math.min(width / 2 - 40, height - 90);
        leftX = width / 2 - size - 12;
        rightX = width / 2 + 12;
        top = 40;
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> strokes.clear())
                .bounds(rightX, top + size + 8, size / 2 - 4, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> submit())
                .bounds(rightX + size / 2 + 4, top + size + 8, size / 2 - 4, 20).build());
    }

    @Override
    public void tick() {
        if (Util.getMillis() >= until) submit();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, BG);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, Component.literal("Draw the shape - or everyone pays").withStyle(s -> s.withColor(0xE04040)),
                width / 2, 14, 0xFFFFFF);
        panel(g, leftX);
        panel(g, rightX);
        g.drawCenteredString(font, "The shape", leftX + size / 2, top - 11, 0xC0A0A0);
        g.drawCenteredString(font, "Your drawing", rightX + size / 2, top - 11, 0xC0A0A0);
        for (float[] s : shape.strokes()) {
            for (int i = 2; i + 1 < s.length; i += 2) {
                line(g, leftX + (s[i - 2] * 0.42F + 0.5F) * size, top + (s[i - 1] * 0.42F + 0.5F) * size,
                        leftX + (s[i] * 0.42F + 0.5F) * size, top + (s[i + 1] * 0.42F + 0.5F) * size, 2, SHAPE);
            }
        }
        for (List<float[]> s : strokes) {
            for (int i = 1; i < s.size(); i++) {
                line(g, rightX + s.get(i - 1)[0] * size, top + s.get(i - 1)[1] * size, rightX + s.get(i)[0] * size,
                        top + s.get(i)[1] * size, 2, INK);
            }
        }
        // the time left
        float left = Mth.clamp((until - Util.getMillis()) / (ticks * 50.0F), 0, 1);
        int barW = size * 2 + 24;
        g.fill(leftX, top + size + 34, leftX + barW, top + size + 38, 0xFF2A1014);
        g.fill(leftX, top + size + 34, leftX + (int) (barW * left), top + size + 38, left > 0.3F ? 0xFFC02030 : 0xFFFF5040);
    }

    private void panel(GuiGraphics g, int x) {
        g.fill(x - 2, top - 2, x + size + 2, top + size + 2, PANEL_EDGE);
        g.fill(x, top, x + size, top + size, PANEL);
    }

    /** A thick line of little squares. */
    private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int w, int color) {
        float len = Math.max(1, (float) Math.hypot(x1 - x0, y1 - y0));
        for (float t = 0; t <= len; t += 0.75F) {
            int x = Math.round(x0 + (x1 - x0) * t / len), y = Math.round(y0 + (y1 - y0) * t / len);
            g.fill(x - w / 2, y - w / 2, x - w / 2 + w, y - w / 2 + w, color);
        }
    }

    private boolean onCanvas(double x, double y) {
        return x >= rightX && x < rightX + size && y >= top && y < top + size;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && onCanvas(x, y)) {
            List<float[]> s = new ArrayList<>();
            s.add(new float[] {(float) (x - rightX) / size, (float) (y - top) / size});
            strokes.add(s);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && !strokes.isEmpty() && onCanvas(x, y)) {
            List<float[]> s = strokes.get(strokes.size() - 1);
            float[] last = s.get(s.size() - 1);
            float nx = (float) (x - rightX) / size, ny = (float) (y - top) / size;
            if (Math.hypot(nx - last[0], ny - last[1]) > 0.006) s.add(new float[] {nx, ny});
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    private void submit() {
        if (sent) return;
        sent = true;
        List<float[]> out = new ArrayList<>();
        for (List<float[]> s : strokes) {
            if (s.size() < 2) continue;
            float[] f = new float[s.size() * 2];
            for (int i = 0; i < s.size(); i++) {
                f[i * 2] = s.get(i)[0];
                f[i * 2 + 1] = s.get(i)[1];
            }
            out.add(f);
        }
        PacketDistributor.sendToServer(new DrawPayloads.Submit(boss, out));
        onClose();
    }

    @Override
    public void onClose() {
        if (!sent) {
            submit();
            return;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
