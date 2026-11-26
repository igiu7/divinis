package com.divinis.client.gui;

import java.util.List;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.PanelScrollBar;
import dev.ftb.mods.ftblibrary.ui.ScrollBar;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The frame of every Divinis module: dark, slightly see-through glass with violet accents. A header with the module's
 * icon, title and subtitle (and room on the right for the module's own figures), a rail on the left to switch between
 * the modules in {@link Modules}, the module's body, and a footer with a hint. The frame fades in when it opens;
 * switching modules on the rail only fades the body in, while the rail's marker slides to the new module.
 * <p>
 * A module adds its widgets in {@link #addBody()} and draws behind them in {@link #drawBody}, inside
 * {@link #bodyX()}, {@link #bodyY()}, {@link #bodyW()}, {@link #bodyH()}.
 */
public abstract class ModuleScreen extends BaseScreen {
    public static final int HEADER = 40;
    public static final int FOOTER = 16;
    public static final int RAIL = 30;
    public static final int GAP = 6;

    public static final Color4I GLASS = Color4I.rgba(8, 7, 12, 212);
    public static final Color4I GLASS_HEADER = Color4I.rgba(20, 14, 32, 140);
    public static final Color4I INSET = Color4I.rgba(0, 0, 0, 105);
    public static final Color4I LINE = Color4I.rgba(255, 255, 255, 22);
    public static final Color4I VIOLET = Color4I.rgb(0x9b5cff);
    public static final Color4I VIOLET_LIGHT = Color4I.rgb(0xc8a6ff);
    public static final Color4I VIOLET_DEEP = Color4I.rgb(0x4b2787);
    public static final Color4I TEXT = Color4I.rgb(0xf1eefa);
    public static final Color4I MUTED = Color4I.rgb(0x9b94ae);

    private static final int TAB = 20, TAB_STEP = 26;

    private static boolean switching; // the next module screen is opened from the rail
    private static int lastTab = -1;  // where the rail's marker slides from

    protected int preferredWidth = 640;
    protected int preferredHeight = 340;
    protected int minWidth = 420;
    protected int minHeight = 250;

    private long openedAt;
    private boolean switched;
    private int tabFrom;

    // ---- what a module provides ----
    /** Its id in {@link Modules}, to mark it on the rail. */
    protected abstract String moduleId();
    protected abstract String title();
    protected abstract String subtitle();
    protected abstract Icon icon();
    protected abstract void addBody();

    /** Drawn behind the body's widgets; x, y, w, h are the whole window's. */
    protected void drawBody(GuiGraphics g, Theme theme, int x, int y, int w, int h) {}

    /** Figures on the right of the header, ending at right (absolute). */
    protected void drawHeaderExtras(GuiGraphics g, Theme theme, int x, int y, int right) {}

    protected String hint() {
        return "";
    }

    // ---- the body's area, relative to the window ----
    protected final int bodyX() { return RAIL + GAP; }
    protected final int bodyY() { return HEADER + GAP; }
    protected final int bodyW() { return width - RAIL - GAP * 2; }
    protected final int bodyH() { return height - HEADER - FOOTER - GAP * 2; }

    private int tabIndex() {
        List<Modules.Module> all = Modules.all();
        for (int i = 0; i < all.size(); i++) if (all.get(i).id().equals(moduleId())) return i;
        return -1;
    }

    private static int tabY(int index) {
        return HEADER + 8 + index * TAB_STEP;
    }

    // ---- sizing and widgets ----
    @Override
    public boolean onInit() {
        int sw = getWindow().getGuiScaledWidth();
        int sh = getWindow().getGuiScaledHeight();
        setSize(Math.max(minWidth, Math.min(preferredWidth, sw - 12)), Math.max(minHeight, Math.min(preferredHeight, sh - 12)));
        if (openedAt == 0) { // not again on a resize
            openedAt = System.currentTimeMillis();
            switched = switching;
            switching = false;
            tabFrom = switched && lastTab >= 0 ? lastTab : tabIndex();
        }
        return true;
    }

    @Override
    public final void addWidgets() {
        FrameButton close = new FrameButton(this, false, "Close", () -> closeGui(false));
        close.setPos(width - 24, 12);
        add(close);
        if (getPrevScreen() != null) {
            FrameButton back = new FrameButton(this, true, "Back", () -> closeGui(true));
            back.setPos(width - 44, 12);
            add(back);
        }
        List<Modules.Module> all = Modules.all();
        for (int i = 0; i < all.size(); i++) add(new RailTab(this, i, all.get(i)));
        addBody();
    }

    @Override
    public void alignWidgets() {
    }

    // ---- drawing ----
    @Override
    public final void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        float e = switched ? 1F : Anim.easeOut(Anim.progress(openedAt, 320));

        // a violet haze rising from the bottom of the screen
        int sw = getWindow().getGuiScaledWidth(), sh = getWindow().getGuiScaledHeight();
        g.fillGradient(0, sh / 3, sw, sh, 0x00000000, VIOLET_DEEP.withAlpha((int) (70 * e)).rgba());
        g.flush();
        GuiHelper.setupDrawing();

        // glow, glass, border
        float glow = (0.55F + 0.45F * Anim.pulse(3800, 0F)) * e;
        for (int i = 1; i <= 4; i++) roundOutline(g, x - i, y - i, w + i * 2, h + i * 2, VIOLET.withAlpha((int) (glow * (22 - i * 5))));
        round(g, x, y, w, h, GLASS.withAlpha((int) (GLASS.alphai() * e)));
        GLASS_HEADER.withAlpha((int) (GLASS_HEADER.alphai() * e)).draw(g, x + 1, y + 1, w - 2, HEADER - 1);
        roundOutline(g, x, y, w, h, VIOLET.withAlpha((int) (105 * e)));
        VIOLET_LIGHT.withAlpha((int) (50 * e)).draw(g, x + 3, y + 1, w - 6, 1);
        glowLine(g, x + 1, y + HEADER - 1, w - 2, e);

        // rail and footer
        LINE.draw(g, x + RAIL, y + HEADER, 1, h - HEADER - FOOTER);
        LINE.draw(g, x + 1, y + h - FOOTER, w - 2, 1);
        int from = tabFrom, to = tabIndex();
        if (to >= 0) {
            float k = from == to ? 1F : Anim.easeOut(Anim.progress(openedAt, 260));
            int my = y + (int) Mth.lerp(k, tabY(from < 0 ? to : from), tabY(to));
            VIOLET.withAlpha(60).draw(g, x + 1, my - 1, 3, TAB + 2);
            VIOLET.draw(g, x + 1, my + 2, 2, TAB - 4);
        }

        // header: the icon's tile, title, subtitle
        float p = Anim.pulse(2600, 0F);
        roundOutline(g, x + 11, y + 7, 26, 26, VIOLET.withAlpha((int) ((30 + 40 * p) * e)));
        round(g, x + 12, y + 8, 24, 24, VIOLET.withAlpha((int) (42 * e)));
        roundOutline(g, x + 12, y + 8, 24, 24, VIOLET.withAlpha((int) (210 * e)));
        icon().draw(g, x + 16, y + 12, 16, 16);
        Ui.text(g, theme, Component.literal(title()).withStyle(ChatFormatting.BOLD), x + 45, y + 10, TEXT, true);
        Ui.text(g, theme, subtitle(), x + 45, y + 23, VIOLET_LIGHT.withAlpha(210), false);
        drawHeaderExtras(g, theme, x, y, x + w - (getPrevScreen() != null ? 52 : 32));

        // footer: the hint, the mod's name
        int fy = y + h - FOOTER + 4;
        Ui.text(g, theme, Ui.fit(theme, hint(), w - 90), x + 9, fy, MUTED.withAlpha(190), false);
        String name = "D I V I N I S";
        Ui.text(g, theme, name, x + w - 9 - theme.getStringWidth(name), fy, VIOLET.withAlpha(150), false);

        drawBody(g, theme, x, y, w, h);
    }

    /** The fade-in: the window (or only the body, after a switch) starts dark and clears. */
    @Override
    public void drawForeground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        super.drawForeground(g, theme, x, y, w, h);
        float t = Anim.easeOut(Anim.progress(openedAt, switched ? 220 : 380));
        if (t >= 1F) return;
        GuiHelper.setupDrawing();
        Color4I veil = Color4I.rgba(8, 7, 12, (int) ((1F - t) * 235));
        if (switched) veil.draw(g, x + bodyX(), y + bodyY(), bodyW(), bodyH());
        else veil.draw(g, x + 1, y + HEADER, w - 2, h - HEADER - 1);
    }

    // ---- drawing helpers for modules ----
    /** A rectangle with its corners cut by two pixels. */
    public static void round(GuiGraphics g, int x, int y, int w, int h, Color4I c) {
        c.draw(g, x + 2, y, w - 4, 1);
        c.draw(g, x + 1, y + 1, w - 2, 1);
        c.draw(g, x, y + 2, w, h - 4);
        c.draw(g, x + 1, y + h - 2, w - 2, 1);
        c.draw(g, x + 2, y + h - 1, w - 4, 1);
    }

    public static void roundOutline(GuiGraphics g, int x, int y, int w, int h, Color4I c) {
        c.draw(g, x + 2, y, w - 4, 1);
        c.draw(g, x + 2, y + h - 1, w - 4, 1);
        c.draw(g, x, y + 2, 1, h - 4);
        c.draw(g, x + w - 1, y + 2, 1, h - 4);
        c.draw(g, x + 1, y + 1, 1, 1);
        c.draw(g, x + w - 2, y + 1, 1, 1);
        c.draw(g, x + 1, y + h - 2, 1, 1);
        c.draw(g, x + w - 2, y + h - 2, 1, 1);
    }

    /** A darker well in the glass, for a part of the body. */
    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        round(g, x, y, w, h, INSET);
        roundOutline(g, x, y, w, h, LINE);
    }

    /** A small title with a fading line after it, at the top of a part of the body. */
    public static void section(GuiGraphics g, Theme theme, String title, int x, int y, int w) {
        Ui.text(g, theme, title, x, y, VIOLET_LIGHT.withAlpha(220), false);
        int lx = x + theme.getStringWidth(title) + 5, lw = x + w - lx;
        for (int i = 0; i < lw; i += 2) VIOLET.withAlpha((int) (90 * (1F - i / (float) lw))).draw(g, lx + i, y + 4, 2, 1);
    }

    /** A line brightest in its middle and fading to both ends, a highlight sweeping along it; reveal grows it from the middle. */
    public static void glowLine(GuiGraphics g, int x, int y, int w, float reveal) {
        int half = (int) (w / 2F * reveal), cx = x + w / 2;
        float sweep = (System.currentTimeMillis() % 4600L) / 4600F * 1.8F - 0.4F;
        for (int i = -half; i < half; i += 2) {
            float t = (i + w / 2F) / w;
            float fade = 1F - Math.abs(t * 2F - 1F);
            float hi = (float) Math.exp(-(t - sweep) * (t - sweep) / 0.003F);
            int a = (int) Mth.clamp(30 + 190 * fade * (2F - fade) + 70 * hi, 0, 255);
            VIOLET.lerp(Color4I.WHITE, hi * 0.65F).withAlpha(a).draw(g, cx + i, y, 2, 1);
        }
    }

    /** A thin violet scrollbar, without a track. */
    public static PanelScrollBar scrollBar(Panel parent, Panel panel) {
        return new PanelScrollBar(parent, ScrollBar.Plane.VERTICAL, panel) {
            private final Anim hover = new Anim();

            @Override
            public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            }

            @Override
            public void drawScrollBar(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
                float k = hover.toward(isMouseOver(), 12F);
                round(g, x, y, w, h, VIOLET.withAlpha((int) (110 + 120 * k)));
            }
        };
    }

    // ---- the frame's buttons ----
    /** Close (an x) or back (an arrow) in the header's corner. */
    private static final class FrameButton extends Button {
        private final boolean back;
        private final Runnable action;
        private final Anim hover = new Anim();

        FrameButton(Panel panel, boolean back, String tip, Runnable action) {
            super(panel, Component.literal(tip), Icon.empty());
            this.back = back;
            this.action = action;
            setSize(16, 16);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            float k = hover.toward(isMouseOver(), 14F);
            Color4I tint = back ? VIOLET : Color4I.rgb(0xe0564f);
            if (k > 0.01F) {
                round(g, x, y, w, h, tint.withAlpha((int) (70 * k)));
                roundOutline(g, x, y, w, h, tint.withAlpha((int) (200 * k)));
            }
            Color4I mark = MUTED.lerp(TEXT, k);
            int cx = x + w / 2, cy = y + h / 2;
            if (back) {
                for (int i = 0; i < 4; i++) {
                    mark.draw(g, cx - 2 + i, cy - i, 2, 1);
                    if (i > 0) mark.draw(g, cx - 2 + i, cy + i, 2, 1);
                }
            } else {
                for (int i = 0; i < 7; i++) {
                    mark.draw(g, cx - 3 + i, cy - 3 + i, 1, 1);
                    mark.draw(g, cx + 3 - i, cy - 3 + i, 1, 1);
                }
            }
        }

        @Override
        public void onClicked(MouseButton button) {
            playClickSound();
            action.run();
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(title);
        }
    }

    /** A module on the rail. */
    private final class RailTab extends Button {
        private final int index;
        private final Modules.Module module;
        private final Anim hover = new Anim();

        RailTab(Panel panel, int index, Modules.Module module) {
            super(panel, Component.literal(module.name()), module.icon());
            this.index = index;
            this.module = module;
            setPosAndSize((RAIL - TAB) / 2 + 1, tabY(index), TAB, TAB);
        }

        private boolean current() {
            return index == tabIndex();
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            float k = hover.toward(isMouseOver() || current(), 12F);
            if (current()) {
                round(g, x, y, w, h, VIOLET.withAlpha(48));
                roundOutline(g, x, y, w, h, VIOLET.withAlpha(170));
            } else if (k > 0.01F) {
                round(g, x, y, w, h, Color4I.WHITE.withAlpha((int) (16 * k)));
                roundOutline(g, x, y, w, h, VIOLET.withAlpha((int) (110 * k)));
            }
            int s = 14 + (int) (2 * k * (current() ? 0 : 1));
            icon.draw(g, x + (w - s) / 2, y + (h - s) / 2, s, s);
        }

        @Override
        public void onClicked(MouseButton button) {
            if (current()) return;
            playClickSound();
            lastTab = tabIndex();
            switching = true;
            ModuleScreen next = module.open().get();
            next.setPreviousScreen(getPrevScreen());
            next.openGui();
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.literal(module.name()).withStyle(ChatFormatting.BOLD));
            if (!current()) list.add(Component.literal("Click to open").withStyle(ChatFormatting.GRAY));
        }
    }
}
