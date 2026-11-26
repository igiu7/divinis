package com.divinis.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.divinis.client.ClientCharacter;
import com.divinis.net.CharacterPayload;
import com.divinis.skill.CharacterService;
import com.divinis.skill.SkillBook;
import com.divinis.skill.SkillOrb;
import com.divinis.skill.SkillTree;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.ModalPanel;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.PanelScrollBar;
import dev.ftb.mods.ftblibrary.ui.ScrollBar;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The character's skill trees, laid out like the technology tree: a dark space to drag or scroll around, in it four
 * bars going right - STR, DEX, INT and the skill book's - each a row of points with an orb every
 * SkillTree.POINTS_PER_ORB of them (a passive, or a skill). "+" puts a point in (shift: five); clicking an orb further
 * on puts in the points to reach it. The fourth bar takes a skill book: its slot shows only the skill books in the
 * inventory. On the right, one above the other, the modifiers of the passives unlocked and the skills unlocked, each
 * scrolling; under them the reset of all points (once every 24 hours). Every action runs a /divinis skills command.
 */
public final class SkillTreeScreen extends BaseScreen {
    private static final int HEADER = 38;
    private static final int FOOTER = 16;
    private static final int DETAIL = 170;

    // the bars (content coordinates of the space)
    private static final int PAD = 14;
    private static final int CARD_W = 96;
    private static final int CARD_H = 50;
    private static final int ROW_H = 68;
    private static final int GAP = 22;
    private static final int STEP = 13;
    private static final int ORB = 18;

    private static double lastScrollX = -1, lastScrollY = -1;

    private SpacePanel space;
    private ListPanel modifiers, skills;

    private static CharacterPayload state() {
        return ClientCharacter.get();
    }

    private static int points(SkillTree t) {
        int[] p = state().treePoints();
        return t.ordinal() < p.length ? p[t.ordinal()] : 0;
    }

    private static String book() {
        return state().skillBook();
    }

    private static Color4I color(SkillTree t) {
        return Color4I.rgb(t.color);
    }

    /** Seconds until a reset is allowed, counted down locally since the snapshot arrived. */
    private static int resetLeft() {
        long passed = (System.currentTimeMillis() - ClientCharacter.receivedAt()) / 1000L;
        return (int) Math.max(0, state().resetSeconds() - passed);
    }

    private static int spent() {
        int sum = 0;
        for (int p : state().treePoints()) sum += p;
        return sum;
    }

    /** Runs a command as the player (the server answers with a fresh character). */
    private static void command(String command) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) player.connection.sendCommand(command);
    }

    /** Called when the server sent a fresh state. */
    public void onStateChanged() {
        refreshWidgets();
    }

    // ------------------------------------------------------------------ layout

    @Override
    public boolean onInit() {
        int sw = getWindow().getGuiScaledWidth();
        int sh = getWindow().getGuiScaledHeight();
        setSize(Math.max(420, Math.min(640, sw - 12)), Math.max(250, Math.min(340, sh - 12)));
        return true;
    }

    private int spaceW() { return width - DETAIL - 18; }
    private int bodyH() { return height - HEADER - FOOTER - 10; }
    private int detailX() { return width - DETAIL - 6; }
    private int listH() { return (bodyH() - 26 - 6) / 2; }

    @Override
    public void addWidgets() {
        Buttons.IconButton close = new Buttons.IconButton(this, Icons.CANCEL, "Close", () -> closeGui(false));
        close.setPos(width - 24, 11);
        add(close);
        Buttons.IconButton back = new Buttons.IconButton(this, Icons.LEFT, "Back to the main menu", () -> closeGui(true));
        back.setPos(width - 44, 11);
        add(back);

        space = new SpacePanel(this);
        space.setPosAndSize(6, HEADER + 6, spaceW(), bodyH());
        add(space);

        int lx = detailX(), ly = HEADER + 6, lh = listH();
        modifiers = new ListPanel(this, modifierLines());
        modifiers.setPosAndSize(lx, ly + 13, DETAIL - 6, lh - 13);
        add(modifiers);
        PanelScrollBar bar1 = new PanelScrollBar(this, ScrollBar.Plane.VERTICAL, modifiers);
        bar1.setPosAndSize(lx + DETAIL - 5, ly + 13, 4, lh - 13);
        add(bar1);
        skills = new ListPanel(this, skillLines());
        skills.setPosAndSize(lx, ly + lh + 19, DETAIL - 6, lh - 13);
        add(skills);
        PanelScrollBar bar2 = new PanelScrollBar(this, ScrollBar.Plane.VERTICAL, skills);
        bar2.setPosAndSize(lx + DETAIL - 5, ly + lh + 19, 4, lh - 13);
        add(bar2);

        ResetButton reset = new ResetButton(this);
        reset.setPosAndSize(lx, HEADER + 6 + bodyH() - 22, DETAIL, 20);
        add(reset);
    }

    @Override
    public void alignWidgets() {
    }

    @Override
    public void onClosed() {
        super.onClosed();
        if (space != null) {
            lastScrollX = space.getScrollX();
            lastScrollY = space.getScrollY();
        }
    }

    // ------------------------------------------------------------------ what is unlocked

    /** A line of the lists on the right: text, colour, indent. */
    private record Line(String text, Color4I color, int indent) {}

    private static List<SkillOrb> unlocked(SkillTree t, boolean skill) {
        List<SkillOrb> out = new ArrayList<>();
        for (SkillOrb orb : t.orbs(book())) if (orb.skill() == skill && orb.points() <= points(t)) out.add(orb);
        return out;
    }

    private static String treeName(SkillTree t) {
        return t == SkillTree.BOOK ? SkillBook.byId(book()).map(SkillBook::name).orElse("Book") : t.abbr;
    }

    private static List<Line> modifierLines() {
        List<Line> out = new ArrayList<>();
        for (SkillTree t : SkillTree.values()) {
            for (SkillOrb orb : unlocked(t, false)) {
                out.add(new Line(treeName(t) + "  " + orb.name(), color(t), 0));
                if (orb.modifiers().isEmpty()) out.add(new Line("no modifiers yet", Ui.MUTED, 8));
                for (String m : orb.modifiers()) out.add(new Line(m, Ui.TEXT, 8));
            }
        }
        if (out.isEmpty()) out.add(new Line("Unlock a passive orb to see its modifiers here.", Ui.MUTED, 0));
        return out;
    }

    private static List<Line> skillLines() {
        List<Line> out = new ArrayList<>();
        for (SkillTree t : SkillTree.values()) {
            for (SkillOrb orb : unlocked(t, true)) {
                out.add(new Line(treeName(t) + "  " + orb.name(), color(t), 0));
                if (orb.skills().isEmpty()) out.add(new Line("no skill yet", Ui.MUTED, 8));
                for (String s : orb.skills()) out.add(new Line(s, Ui.TEXT, 8));
            }
        }
        if (out.isEmpty()) out.add(new Line("Unlock a skill orb to see its skills here.", Ui.MUTED, 0));
        return out;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        CharacterPayload s = state();
        Ui.box(g, x, y, w, h, Ui.WINDOW, Ui.BORDER);
        Ui.HEADER.draw(g, x + 1, y + 1, w - 2, HEADER - 1);
        Ui.GOLD.draw(g, x + 1, y + HEADER - 1, w - 2, 1);

        Ui.box(g, x + 9, y + 6, 26, 26, Ui.GOLD.withAlpha(50), Ui.GOLD);
        Icons.BOOK.draw(g, x + 14, y + 11, 16, 16);
        Ui.text(g, theme, Component.literal("Skills").withStyle(ChatFormatting.BOLD), x + 44, y + 7, Ui.TEXT, true);
        Ui.text(g, theme, "Level " + s.level(), x + 44 + theme.getStringWidth("Skills") + 12, y + 7, Ui.GOLD, true);
        // the XP bar
        int bw = 150;
        Ui.TILE_DISABLED.draw(g, x + 44, y + 21, bw, 5);
        float k = s.levelNeed() <= 0 ? 1F : Mth.clamp(s.levelXp() / (float) s.levelNeed(), 0F, 1F);
        Ui.GOLD.draw(g, x + 44, y + 21, (int) (bw * k), 5);
        Ui.text(g, theme, s.levelNeed() <= 0 ? "max level" : s.levelXp() + " / " + s.levelNeed() + " XP", x + 44 + bw + 6, y + 20, Ui.MUTED, false);

        // the points to spend
        String free = s.freePoints() + (s.freePoints() == 1 ? " point" : " points") + " to spend";
        int fw = theme.getStringWidth(free) + 16, fx = x + w - 52 - fw;
        Color4I fc = s.freePoints() > 0 ? Ui.GOLD : Ui.GRAY;
        if (s.freePoints() > 0) {
            float pulse = 0.6F + 0.4F * Mth.sin((System.currentTimeMillis() % 1400L) / 1400F * Mth.TWO_PI);
            Ui.box(g, fx, y + 10, fw, 16, fc.withAlpha((int) (40 + 40 * pulse)), fc.withAlpha((int) (150 + 100 * pulse)));
        } else {
            Ui.box(g, fx, y + 10, fw, 16, fc.withAlpha(30), fc.withAlpha(120));
        }
        theme.drawString(g, free, fx + fw / 2, y + 14, s.freePoints() > 0 ? Ui.TEXT : Ui.MUTED, Theme.CENTERED | Theme.SHADOW);

        // the frames of the two lists
        int lx = x + detailX(), ly = y + HEADER + 6, lh = listH();
        listFrame(g, theme, "MODIFIERS", lx, ly, lh);
        listFrame(g, theme, "SKILLS", lx, ly + lh + 6, lh);

        // footer
        int fy = y + h - FOOTER + 3;
        Ui.text(g, theme, "+ adds a point, shift+click adds five.",
                x + 8, fy, Ui.MUTED.withAlpha(170), false);
    }

    private static void listFrame(GuiGraphics g, Theme theme, String title, int x, int y, int h) {
        Ui.box(g, x, y, DETAIL, h, Ui.CONTENT, Ui.BORDER);
        Ui.text(g, theme, title, x + 6, y + 3, Ui.MUTED, false);
        Ui.BORDER.draw(g, x + 10 + theme.getStringWidth(title), y + 7, DETAIL - 16 - theme.getStringWidth(title), 1);
    }

    // ------------------------------------------------------------------ the space and its bars

    private static int rowY(SkillTree t) { return PAD + t.ordinal() * ROW_H; }
    private static int trackX() { return PAD + CARD_W + GAP; }
    private static int pointX(int i) { return trackX() + i * STEP; }   // the i-th point (i = 1..max)
    private static int trackY(SkillTree t) { return rowY(t) + CARD_H / 2; }

    /** A filled disc of radius r centred on cx, cy. */
    private static void disc(GuiGraphics g, int cx, int cy, int r, Color4I c) {
        for (int dy = -r; dy < r; dy++) {
            float yy = dy + 0.5F;
            int half = (int) Math.round(Math.sqrt(Math.max(0, r * r - yy * yy)));
            if (half > 0) c.draw(g, cx - half, cy + dy, half * 2, 1);
        }
    }

    private final class SpacePanel extends Panel {
        private boolean dragging;

        SpacePanel(Panel parent) {
            super(parent);
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
            setScrollStep(30);
        }

        @Override
        public void addWidgets() {
            int maxX = 0;
            for (SkillTree t : SkillTree.values()) {
                add(new PlusButton(this, t));
                if (t == SkillTree.BOOK) add(new BookSlot(this));
                int max = Math.max(t.maxPoints(book()), SkillTree.MAX_POINTS);
                for (SkillOrb orb : t.orbs(book())) add(new Orb(this, t, orb));
                maxX = Math.max(maxX, pointX(max) + ORB);
            }
            add(new Spacer(this, 0, 0));
            add(new Spacer(this, maxX + PAD, rowY(SkillTree.BOOK) + CARD_H + PAD));
        }

        @Override
        public void alignWidgets() {
            if (lastScrollX >= 0) {
                setScrollX(Mth.clamp(lastScrollX, 0, Math.max(0, getContentWidth() - width)));
                setScrollY(Mth.clamp(lastScrollY, 0, Math.max(0, getContentHeight() - height)));
            }
        }

        @Override
        public void setScrollX(double scroll) {
            super.setScrollX(scroll);
            lastScrollX = scroll;
        }

        @Override
        public void setScrollY(double scroll) {
            super.setScrollY(scroll);
            lastScrollY = scroll;
        }

        @Override
        public boolean scrollPanel(double xDelta, double yDelta) {
            if (!isMouseOver()) return false;
            if (getContentHeight() <= height && xDelta == 0) return movePanelScroll(-getScrollStep() * yDelta, 0);
            return super.scrollPanel(xDelta, yDelta);
        }

        @Override
        public boolean mousePressed(MouseButton button) {
            if (super.mousePressed(button)) return true;
            if (isMouseOver()) {
                dragging = true;
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(int button, double dragX, double dragY) {
            if (dragging) {
                movePanelScroll(-dragX, -dragY);
                return true;
            }
            return super.mouseDragged(button, dragX, dragY);
        }

        @Override
        public void mouseReleased(MouseButton button) {
            dragging = false;
            super.mouseReleased(button);
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            Ui.box(g, x - 1, y - 1, w + 2, h + 2, Color4I.rgb(0x0c0e16), Ui.BORDER);
            // stars, drifting a little slower than the bars as the space moves
            double sx = getScrollX() * 0.4, sy = getScrollY() * 0.4;
            long now = System.currentTimeMillis();
            for (int i = 0; i < 140; i++) {
                int hx = (int) (hash(i, 1) * 1600), hy = (int) (hash(i, 2) * 600);
                int px = Math.floorMod((int) (hx - sx), Math.max(1, w)), py = Math.floorMod((int) (hy - sy), Math.max(1, h));
                float tw = 0.55F + 0.45F * Mth.sin(now / 900F + i * 1.7F);
                int a = (int) ((40 + hash(i, 3) * 110) * tw);
                Color4I c = hash(i, 4) < 0.2F ? Color4I.rgb(0xf2d58a) : hash(i, 4) < 0.4F ? Color4I.rgb(0x9ab8ff) : Color4I.rgb(0xdfe6f5);
                c.withAlpha(a).draw(g, x + px, y + py, 1, 1);
                if (hash(i, 5) < 0.06F) c.withAlpha(a / 3).draw(g, x + px - 1, y + py, 3, 1);
            }
        }

        private static float hash(int i, int k) {
            int n = i * 374761393 + k * 668265263;
            n = (n ^ (n >>> 13)) * 1274126177;
            return ((n ^ (n >>> 16)) & 0xFFFF) / 65535F;
        }

        /** The cards on the left and the bars' tracks with their points, under the orbs. x / y = content origin. */
        @Override
        public void drawOffsetBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            for (SkillTree t : SkillTree.values()) {
                Color4I c = color(t);
                int have = points(t), max = t.maxPoints(book());
                boolean noBook = t == SkillTree.BOOK && max == 0;
                int ry = y + rowY(t), cx = x + PAD;
                // the card
                Ui.box(g, cx, ry, CARD_W, CARD_H, c.withAlpha(noBook ? 18 : 34), c.withAlpha(noBook ? 90 : 200));
                c.withAlpha(noBook ? 90 : 230).draw(g, cx + 1, ry + 1, 2, CARD_H - 2);
                Ui.text(g, theme, Component.literal(t.abbr).withStyle(ChatFormatting.BOLD), cx + 8, ry + 6, noBook ? Ui.MUTED : c, true);
                String sub = t == SkillTree.BOOK ? SkillBook.byId(book()).map(SkillBook::name).orElse("no book") : t.title;
                Ui.text(g, theme, Ui.fit(theme, sub, CARD_W - (t == SkillTree.BOOK ? 34 : 12)), cx + 8, ry + 18, Ui.MUTED, false);
                Ui.text(g, theme, noBook ? "-" : have + " / " + max, cx + 8, ry + 34, noBook ? Ui.MUTED : Ui.TEXT, false);

                // the track
                int ty = y + trackY(t);
                int x0 = x + trackX(), x1 = x + pointX(noBook ? SkillTree.MAX_POINTS : max); // a book's tree is shorter
                Color4I.rgb(0x2a3042).withAlpha(noBook ? 90 : 220).draw(g, x0, ty - 1, x1 - x0, 3);
                if (noBook) {
                    theme.drawString(g, "Put a skill book in to open this tree", (x0 + x1) / 2, ty - 14, Ui.MUTED, Theme.CENTERED);
                    continue;
                }
                if (have > 0) {
                    int filled = x + pointX(have) - x0;
                    c.withAlpha(70).draw(g, x0, ty - 3, filled, 7);
                    c.draw(g, x0, ty - 1, filled, 3);
                }
                // the points between the orbs
                for (int i = 1; i <= max; i++) {
                    if (i % SkillTree.POINTS_PER_ORB == 0) continue;
                    int px = x + pointX(i);
                    boolean on = i <= have;
                    Color4I pc = on ? c : Color4I.rgb(0x454d63);
                    pc.draw(g, px - 2, ty - 2, 5, 5);
                    if (on) Color4I.WHITE.withAlpha(120).draw(g, px - 1, ty - 1, 2, 2);
                    else Color4I.rgb(0x161a24).draw(g, px - 1, ty - 1, 3, 3);
                }
            }
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            super.draw(g, theme, x, y, w, h);
            int cw = getContentWidth(), ch = getContentHeight();
            if (cw > w) {
                int bw = Math.max(16, w * w / cw);
                int bx = (int) ((w - bw) * (getScrollX() / (cw - w)));
                Ui.MUTED.withAlpha(110).draw(g, x + bx, y + h - 3, bw, 2);
            }
            if (ch > h) {
                int bh = Math.max(16, h * h / ch);
                int by = (int) ((h - bh) * (getScrollY() / (ch - h)));
                Ui.MUTED.withAlpha(110).draw(g, x + w - 3, y + by, 2, bh);
            }
        }
    }

    /** Invisible widget that only stretches the scrollable area. */
    private static final class Spacer extends Widget {
        Spacer(Panel panel, int x, int y) {
            super(panel);
            setPosAndSize(x, y, 1, 1);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        }
    }

    /** Puts a point (shift: five) into a tree. */
    private final class PlusButton extends Button {
        final SkillTree tree;

        PlusButton(Panel panel, SkillTree tree) {
            super(panel, Component.literal("+"), Icon.empty());
            this.tree = tree;
            setPosAndSize(PAD + CARD_W - 20, rowY(tree) + CARD_H - 20, 16, 16);
        }

        private boolean enabled() {
            int max = tree.maxPoints(book());
            return state().freePoints() > 0 && max > 0 && points(tree) < max;
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            boolean on = enabled();
            Color4I c = on ? color(tree) : Ui.GRAY;
            Ui.box(g, x, y, w, h, c.withAlpha(on ? (isMouseOver() ? 230 : 150) : 50), c.withAlpha(on ? 255 : 110));
            theme.drawString(g, "+", x + w / 2 + 1, y + 4, on ? Ui.TEXT : Ui.MUTED, Theme.CENTERED | Theme.SHADOW);
        }

        @Override
        public void onClicked(MouseButton button) {
            if (!enabled()) return;
            playClickSound();
            int amount = Screen.hasShiftDown() ? SkillTree.POINTS_PER_ORB : 1;
            command("divinis skills assign " + tree.id() + " " + amount);
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.literal("Add a point to " + tree.title).withStyle(ChatFormatting.BOLD));
            list.add(Component.literal("Shift+click: five points").withStyle(ChatFormatting.GRAY));
            if (state().freePoints() <= 0) list.add(Component.literal("No points to spend - earn them with levels").withStyle(ChatFormatting.RED));
        }
    }

    /** One orb on a bar. */
    private final class Orb extends Button {
        final SkillTree tree;
        final SkillOrb orb;

        Orb(Panel panel, SkillTree tree, SkillOrb orb) {
            super(panel, Component.literal(orb.name()), Icon.empty());
            this.tree = tree;
            this.orb = orb;
            setPosAndSize(pointX(orb.points()) - ORB / 2, trackY(tree) - ORB / 2, ORB, ORB);
        }

        private boolean unlocked() {
            return points(tree) >= orb.points();
        }

        /** Points it still needs, if they can be put in now (0 when it cannot be reached yet). */
        private int reachable() {
            int need = orb.points() - points(tree);
            return need > 0 && need <= state().freePoints() ? need : 0;
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            Color4I c = color(tree);
            int cx = x + w / 2, cy = y + h / 2, r = w / 2;
            boolean on = unlocked();
            if (on) {
                float pulse = 0.7F + 0.3F * Mth.sin((System.currentTimeMillis() % 2400L) / 2400F * Mth.TWO_PI + orb.points());
                disc(g, cx, cy, r + 3, c.withAlpha((int) (45 * pulse)));
                disc(g, cx, cy, r + 1, c.withAlpha((int) (90 * pulse)));
            }
            if (isMouseOver()) disc(g, cx, cy, r + 2, Ui.TEXT.withAlpha(on ? 90 : 60));
            disc(g, cx, cy, r, on ? c : (reachable() > 0 ? c.withAlpha(150) : Color4I.rgb(0x454d63)));
            disc(g, cx, cy, r - 2, on ? c.addBrightness(-0.15F) : Color4I.rgb(0x141823));
            if (on) disc(g, cx - 2, cy - 3, 2, Color4I.WHITE.withAlpha(150)); // its shine
            // a skill: a diamond; a passive: a ring
            Color4I mark = on ? Ui.TEXT : (reachable() > 0 ? c : Ui.GRAY);
            if (orb.skill()) {
                for (int i = 0; i < 4; i++) mark.draw(g, cx - i, cy - 3 + i, i * 2 + 1, 1);
                for (int i = 0; i < 3; i++) mark.draw(g, cx - 2 + i, cy + 1 + i, 5 - i * 2, 1);
            } else {
                mark.draw(g, cx - 1, cy - 3, 3, 1);
                mark.draw(g, cx - 1, cy + 2, 3, 1);
                mark.draw(g, cx - 3, cy - 1, 1, 3);
                mark.draw(g, cx + 2, cy - 1, 1, 3);
            }
            theme.drawString(g, String.valueOf(orb.points()), cx, y + h + 3, on ? c : Ui.MUTED, Theme.CENTERED);
        }

        /** Points go in only with the tree's + button; an orb only shows what it gives. */
        @Override
        public void onClicked(MouseButton button) {
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.literal(orb.name()).withStyle(ChatFormatting.BOLD).withColor(tree.color));
            list.add(Component.literal((orb.skill() ? "Skill" : "Passive") + " of " + tree.title + " - at " + orb.points() + " points")
                    .withStyle(ChatFormatting.GRAY));
            List<String> what = orb.skill() ? orb.skills() : orb.modifiers();
            if (what.isEmpty()) list.add(Component.literal(orb.skill() ? "No skill yet" : "No modifiers yet").withStyle(ChatFormatting.DARK_GRAY));
            for (String line : what) list.add(Component.literal(line));
            int need = orb.points() - points(tree);
            if (need <= 0) list.add(Component.literal("Unlocked").withStyle(ChatFormatting.GREEN));
            else list.add(Component.literal("Needs " + need + " more points").withStyle(ChatFormatting.RED));
        }
    }

    /** The fourth bar's book: shows it, opens the choice of the skill books in the inventory. */
    private final class BookSlot extends Button {
        BookSlot(Panel panel) {
            super(panel, Component.literal("Skill book"), Icon.empty());
            setPosAndSize(PAD + CARD_W - 24, rowY(SkillTree.BOOK) + 5, 20, 20);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            Color4I c = color(SkillTree.BOOK);
            Ui.box(g, x, y, w, h, isMouseOver() ? Ui.TILE_HOVER : Color4I.rgb(0x10131a), c.withAlpha(isMouseOver() ? 255 : 160));
            SkillBook.byId(book()).ifPresentOrElse(b -> ItemIcon.getItemIcon(b.stack()).draw(g, x + 2, y + 2, 16, 16),
                    () -> Icons.ADD.draw(g, x + 4, y + 4, 12, 12));
        }

        @Override
        public void onClicked(MouseButton button) {
            playClickSound();
            BookPicker picker = new BookPicker(SkillTreeScreen.this);
            picker.setPos((SkillTreeScreen.this.width - picker.width) / 2, (SkillTreeScreen.this.height - picker.height) / 2);
            pushModalPanel(picker);
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.literal(SkillBook.byId(book()).map(b -> "Skill book: " + b.name()).orElse("Put a skill book in"))
                    .withStyle(ChatFormatting.BOLD));
            list.add(Component.literal("Click to choose from the skill books you carry").withStyle(ChatFormatting.GRAY));
        }
    }

    // ------------------------------------------------------------------ the choice of a skill book

    /** The skill books in the player's inventory (nothing else), to put one in; and taking the book out. */
    private static final class BookPicker extends ModalPanel {
        private static final int W = 214, SLOT = 22, COLS = 8;
        private final List<int[]> books = new ArrayList<>(); // inventory slot

        BookPicker(BaseScreen gui) {
            super(gui);
            var player = Minecraft.getInstance().player;
            if (player != null) {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    if (SkillBook.of(player.getInventory().getItem(i)).isPresent()) books.add(new int[] {i});
                }
            }
            int rows = Math.max(1, (books.size() + COLS - 1) / COLS);
            setSize(W, 34 + rows * SLOT + 8 + (SkillBook.byId(book()).isPresent() ? 24 : 0) + 24);
        }

        private void close() {
            getGui().closeModalPanel(this);
        }

        @Override
        public void addWidgets() {
            var player = Minecraft.getInstance().player;
            int x0 = (W - Math.min(COLS, Math.max(1, books.size())) * SLOT) / 2;
            for (int i = 0; i < books.size(); i++) {
                int slot = books.get(i)[0];
                ItemStack stack = player == null ? ItemStack.EMPTY : player.getInventory().getItem(slot).copy();
                add(new Pick(this, stack, slot, x0 + (i % COLS) * SLOT, 30 + (i / COLS) * SLOT));
            }
            int y = height - 24;
            if (SkillBook.byId(book()).isPresent()) {
                int refund = points(SkillTree.BOOK);
                Buttons.ChipButton out = new Buttons.ChipButton(this, W - 24, "Take the book out" + (refund > 0 ? " (" + refund + " points back)" : ""),
                        Ui.RED, () -> {
                            close();
                            command("divinis skills book remove");
                        });
                out.setPos(12, y - 24);
                add(out);
            }
            Buttons.ChipButton cancel = new Buttons.ChipButton(this, 90, "Close", Ui.GRAY, this::close);
            cancel.setPos((W - 90) / 2, y);
            add(cancel);
        }

        @Override
        public void alignWidgets() {
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            g.pose().pushPose();
            g.pose().translate(0, 0, 900);
            Color4I.BLACK.withAlpha(130).draw(g, 0, 0, getWindow().getGuiScaledWidth(), getWindow().getGuiScaledHeight());
            super.draw(g, theme, x, y, w, h);
            g.pose().popPose();
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            Color4I.BLACK.withAlpha(60).draw(g, x + 3, y + 3, w, h);
            Ui.box(g, x, y, w, h, Ui.HEADER, Ui.BORDER);
            Ui.GOLD.draw(g, x + 1, y + 1, w - 2, 2);
            Ui.text(g, theme, Component.literal("Skill books you carry").withStyle(ChatFormatting.BOLD), x + 12, y + 10, Ui.TEXT, true);
            if (books.isEmpty()) {
                theme.drawString(g, "No skill books in your inventory", x + w / 2, y + 36, Ui.MUTED, Theme.CENTERED);
            }
        }

        /** One skill book of the inventory. */
        private final class Pick extends Button {
            final ItemStack stack;
            final int slot;

            Pick(Panel panel, ItemStack stack, int slot, int x, int y) {
                super(panel, stack.getHoverName(), ItemIcon.getItemIcon(stack));
                this.stack = stack;
                this.slot = slot;
                setPosAndSize(x, y, 20, 20);
            }

            @Override
            public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
                GuiHelper.setupDrawing();
                Ui.box(g, x, y, w, h, isMouseOver() ? Ui.TILE_HOVER : Ui.TILE, isMouseOver() ? Ui.GOLD : Ui.TILE_BORDER);
                icon.draw(g, x + 2, y + 2, 16, 16);
            }

            @Override
            public void onClicked(MouseButton button) {
                playClickSound();
                close();
                command("divinis skills book insert " + slot);
            }

            @Override
            public void addMouseOverText(TooltipList list) {
                list.add(stack.getHoverName());
                int refund = points(SkillTree.BOOK);
                list.add(Component.literal(SkillBook.byId(book()).isPresent()
                        ? "Replaces the book in the tree" + (refund > 0 ? " (its " + refund + " points come back)" : "")
                        : "Click to put it into the fourth tree").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    // ------------------------------------------------------------------ the lists on the right

    /** A scrolling list of lines, wrapped to its width. */
    private static final class ListPanel extends Panel {
        private final List<Line> lines;

        ListPanel(Panel parent, List<Line> lines) {
            super(parent);
            this.lines = lines;
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
            setScrollStep(20);
        }

        @Override
        public void addWidgets() {
            Theme theme = getGui().getTheme();
            int y = 2;
            for (Line l : lines) {
                for (FormattedText part : theme.listFormattedStringToWidth(Component.literal(l.text()), width - 10 - l.indent())) {
                    add(new Text(this, part, l.color(), 4 + l.indent(), y, width - 8));
                    y += 10;
                }
                if (l.indent() == 0 && l.color() != Ui.MUTED) y += 1;
            }
            add(new Spacer(this, 0, y + 2));
        }

        @Override
        public void alignWidgets() {
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        }
    }

    private static final class Text extends Widget {
        private final FormattedText text;
        private final Color4I color;

        Text(Panel panel, FormattedText text, Color4I color, int x, int y, int w) {
            super(panel);
            this.text = text;
            this.color = color;
            setPosAndSize(x, y, w, 10);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            Ui.text(g, theme, text, x, y, color, false);
        }
    }

    // ------------------------------------------------------------------ reset

    private final class ResetButton extends Button {
        ResetButton(Panel panel) {
            super(panel, Component.literal("Reset points"), Icon.empty());
        }

        /** Label, enabled. */
        private Object[] look() {
            if (spent() == 0) return new Object[] {"No points to reset", false};
            int left = resetLeft();
            if (left > 0) return new Object[] {"Reset in " + com.divinis.util.Names.duration(left), false};
            return new Object[] {"Reset all points", true};
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            Object[] look = look();
            boolean on = (boolean) look[1];
            Color4I c = on ? Ui.RED : Ui.GRAY;
            Ui.box(g, x, y, w, h, c.withAlpha(on ? (isMouseOver() ? 230 : 160) : 60), c.withAlpha(on ? 255 : 130));
            theme.drawString(g, (String) look[0], x + w / 2, y + (h - 9) / 2 + 1, on ? Ui.TEXT : Ui.MUTED, Theme.CENTERED | Theme.SHADOW);
        }

        @Override
        public void onClicked(MouseButton button) {
            if (!(boolean) look()[1]) return;
            playClickSound();
            Dialogs.confirm(SkillTreeScreen.this, "Reset all skill points?",
                    "All " + spent() + " points come back to spend again (the skill book stays). You can do this once every "
                            + (CharacterService.RESET_COOLDOWN / 3_600_000L) + " hours.",
                    Ui.RED, () -> command("divinis skills reset"));
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.literal("Takes back every point you have put in").withStyle(ChatFormatting.GRAY));
            list.add(Component.literal("Once every 24 hours").withStyle(ChatFormatting.GRAY));
        }
    }
}
