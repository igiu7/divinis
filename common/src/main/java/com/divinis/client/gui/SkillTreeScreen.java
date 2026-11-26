package com.divinis.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.PanelScrollBar;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The character's skill trees. On the left the character's card: the player, their level and points, the skill book and
 * the reset. On the right the four trees - STR, DEX, INT and the skill book's - as tiles to pick one, and under them the
 * tree picked: its unlocked modifiers as chips, its points as a bar of segments (one per orb, every
 * SkillTree.POINTS_PER_ORB points), +1 / +5 to put points in, and its orbs (a passive or a skill) as milestones along a
 * line. At the bottom what all trees have unlocked: the passives' modifiers and the skills. Every action runs a
 * /divinis skills command.
 */
public final class SkillTreeScreen extends ModuleScreen {
    private static final int CARD_W = 132;
    private static final int TILES_H = 62;
    private static final int NODE = 16;
    private static final int NODES_Y = 60; // in the detail

    private static SkillTree selected = SkillTree.STR;
    private long selectedAt;

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
        var player = Minecraft.getInstance().player;
        if (player != null) player.connection.sendCommand(command);
    }

    private static Icon treeIcon(SkillTree t) {
        return switch (t) {
            case STR -> ItemIcon.getItemIcon(new ItemStack(Items.IRON_SWORD));
            case DEX -> ItemIcon.getItemIcon(new ItemStack(Items.FEATHER));
            case INT -> ItemIcon.getItemIcon(new ItemStack(Items.AMETHYST_SHARD));
            case BOOK -> ItemIcon.getItemIcon(SkillBook.byId(book()).map(SkillBook::stack).orElse(new ItemStack(Items.BOOK)));
        };
    }

    private static String treeName(SkillTree t) {
        return t == SkillTree.BOOK ? SkillBook.byId(book()).map(SkillBook::name).orElse("Skill Book") : t.title;
    }

    private static String upper(String s) {
        return s.toUpperCase(Locale.ROOT);
    }

    /** Called when the server sent a fresh state. */
    public void onStateChanged() {
        refreshWidgets();
    }

    public SkillTreeScreen() {
        preferredWidth = 670;
        preferredHeight = 350;
        minWidth = 470;
        minHeight = 290;
    }

    @Override
    protected String moduleId() {
        return "skills";
    }

    @Override
    protected String title() {
        return "Skills";
    }

    @Override
    protected String subtitle() {
        return "Level " + state().level();
    }

    @Override
    protected Icon icon() {
        return Icons.BOOK;
    }

    @Override
    protected String hint() {
        return "Pick a tree above, then put points in with +1 / +5.";
    }

    // ------------------------------------------------------------------ layout (relative to the window)

    private int rightX() { return bodyX() + CARD_W + GAP; }
    private int rightW() { return bodyW() - CARD_W - GAP; }
    private int detailY() { return bodyY() + TILES_H + GAP; }
    private int detailH() { return bodyH() - TILES_H - GAP; }
    private int listsY() { return detailY() + 94; }
    private int listsH() { return detailY() + detailH() - 6 - listsY(); }
    private int listW() { return (rightW() - 16 - GAP) / 2; }
    private int portraitH() { return Mth.clamp(bodyH() - 118, 60, 120); }

    private int nodeX(int index, int count) {
        int x0 = rightX() + 14, x1 = rightX() + rightW() - 14 - NODE;
        return count <= 1 ? x0 : x0 + (x1 - x0) * index / (count - 1);
    }

    @Override
    protected void addBody() {
        // the tree tiles
        SkillTree[] trees = SkillTree.values();
        int tw = (rightW() - 8) / trees.length;
        for (int i = 0; i < trees.length; i++) {
            TreeTile tile = new TreeTile(this, trees[i]);
            tile.setPosAndSize(rightX() + 4 + i * tw, bodyY() + 4, tw, TILES_H - 8);
            add(tile);
        }

        // +1 / +5
        int py = detailY() + 6, px = rightX() + rightW() - 8;
        Pill five = new Pill(this, () -> "+" + SkillTree.POINTS_PER_ORB, color(selected), () -> canAdd(SkillTree.POINTS_PER_ORB),
                () -> command("divinis skills assign " + selected.id() + " " + SkillTree.POINTS_PER_ORB))
                .tooltip(list -> addTip(list, SkillTree.POINTS_PER_ORB));
        five.setPosAndSize(px - 26, py, 26, 14);
        add(five);
        Pill one = new Pill(this, () -> "+1", color(selected), () -> canAdd(1), () -> command("divinis skills assign " + selected.id() + " 1"))
                .tooltip(list -> addTip(list, 1));
        one.setPosAndSize(px - 56, py, 26, 14);
        add(one);

        // the orbs of the tree picked
        List<SkillOrb> orbs = selected.orbs(book());
        for (int i = 0; i < orbs.size(); i++) {
            Node node = new Node(this, selected, orbs.get(i));
            node.setPosAndSize(nodeX(i, orbs.size()), detailY() + NODES_Y, NODE, NODE);
            add(node);
        }

        // what all trees have unlocked
        if (listsH() >= 30) {
            int lx = rightX() + 8, lw = listW();
            for (int k = 0; k < 2; k++) {
                int x = lx + k * (lw + GAP);
                ListPanel list = new ListPanel(this, k == 0 ? modifierLines(selected) : skillLines(selected));
                list.setPosAndSize(x, listsY() + 15, lw - 6, listsH() - 17);
                add(list);
                PanelScrollBar bar = scrollBar(this, list);
                bar.setPosAndSize(x + lw - 5, listsY() + 15, 3, listsH() - 17);
                add(bar);
            }
        }

        // the card's buttons
        int cx = bodyX() + 6, cw = CARD_W - 12, cy = bodyY() + bodyH() - 24;
        Pill reset = new Pill(this, this::resetLabel, Ui.RED, () -> spent() > 0 && resetLeft() == 0, this::askReset)
                .tooltip(list -> {
                    list.add(Component.literal("Takes back every point you have put in").withStyle(ChatFormatting.GRAY));
                    list.add(Component.literal("Once every 24 hours").withStyle(ChatFormatting.GRAY));
                });
        reset.setPosAndSize(cx, cy, cw, 16);
        add(reset);
        BookButton bookButton = new BookButton(this);
        bookButton.setPosAndSize(cx, cy - BookButton.H - 5, cw, BookButton.H);
        add(bookButton);
    }

    private static boolean canAdd(int amount) {
        int max = selected.maxPoints(book());
        return state().freePoints() >= amount && max > 0 && points(selected) + amount <= max;
    }

    private static void addTip(TooltipList list, int amount) {
        list.add(Component.literal("Put " + amount + (amount == 1 ? " point" : " points") + " into " + treeName(selected)).withStyle(ChatFormatting.BOLD));
        if (selected.maxPoints(book()) == 0) list.add(Component.literal("Put a skill book in first").withStyle(ChatFormatting.RED));
        else if (state().freePoints() < amount) list.add(Component.literal("Not enough points - earn them with levels").withStyle(ChatFormatting.RED));
        else if (points(selected) + amount > selected.maxPoints(book())) list.add(Component.literal("The tree has no room for that").withStyle(ChatFormatting.RED));
    }

    private String resetLabel() {
        if (spent() == 0) return "No points to reset";
        int left = resetLeft();
        return left > 0 ? "Reset in " + com.divinis.util.Names.duration(left) : "Reset all points";
    }

    private void askReset() {
        GlassModal.confirm(this, "Reset all skill points?",
                "All " + spent() + " points come back to spend again (the skill book stays). You can do this once every "
                        + (CharacterService.RESET_COOLDOWN / 3_600_000L) + " hours.",
                Ui.RED, () -> command("divinis skills reset"));
    }

    private void select(SkillTree tree) {
        if (tree == selected) return;
        selected = tree;
        selectedAt = System.currentTimeMillis();
        refreshWidgets();
    }

    // ------------------------------------------------------------------ what is unlocked

    /** A line of the lists at the bottom: text, colour, indent. */
    private record Line(String text, Color4I color, int indent) {}

    private static List<SkillOrb> unlocked(SkillTree t, boolean skill) {
        List<SkillOrb> out = new ArrayList<>();
        for (SkillOrb orb : t.orbs(book())) if (orb.skill() == skill && orb.points() <= points(t)) out.add(orb);
        return out;
    }

    private static String shortName(SkillTree t) {
        return t == SkillTree.BOOK ? SkillBook.byId(book()).map(SkillBook::name).orElse("Book") : t.abbr;
    }

    /** The modifiers of the passives the tree has unlocked. */
    private static List<Line> modifierLines(SkillTree t) {
        List<Line> out = new ArrayList<>();
        for (SkillOrb orb : unlocked(t, false)) {
            out.add(new Line(orb.name(), color(t), 0));
            if (orb.modifiers().isEmpty()) out.add(new Line("no modifiers yet", MUTED, 8));
            for (String m : orb.modifiers()) out.add(new Line(m, TEXT, 8));
        }
        if (out.isEmpty()) out.add(new Line("No passive unlocked in " + shortName(t) + " yet.", MUTED, 0));
        return out;
    }

    /** The skills the tree has unlocked. */
    private static List<Line> skillLines(SkillTree t) {
        List<Line> out = new ArrayList<>();
        for (SkillOrb orb : unlocked(t, true)) {
            out.add(new Line(orb.name(), color(t), 0));
            if (orb.skills().isEmpty()) out.add(new Line("no skill yet", MUTED, 8));
            for (String s : orb.skills()) out.add(new Line(s, TEXT, 8));
        }
        if (out.isEmpty()) out.add(new Line("No skill unlocked in " + shortName(t) + " yet.", MUTED, 0));
        return out;
    }

    /** The short modifiers ("+15% Fishing Speed") of the tree's unlocked passives, for its chips. */
    private static List<String> chips(SkillTree t) {
        List<String> out = new ArrayList<>();
        for (SkillOrb orb : unlocked(t, false)) {
            for (String m : orb.modifiers()) if (m.length() <= 24 && (m.startsWith("+") || m.startsWith("-"))) out.add(m);
        }
        return out;
    }

    // ------------------------------------------------------------------ drawing

    /** The XP to the next level and the points to spend, on the right of the header. */
    @Override
    protected void drawHeaderExtras(GuiGraphics g, Theme theme, int x, int y, int right) {
        CharacterPayload s = state();
        String free = s.freePoints() + (s.freePoints() == 1 ? " point" : " points") + " to spend";
        int fw = theme.getStringWidth(free) + 16, fx = right - fw;
        if (s.freePoints() > 0) {
            float pulse = Anim.pulse(1400, 0F);
            round(g, fx, y + 12, fw, 16, VIOLET.withAlpha((int) (45 + 45 * pulse)));
            roundOutline(g, fx, y + 12, fw, 16, VIOLET_LIGHT.withAlpha((int) (140 + 110 * pulse)));
        } else {
            round(g, fx, y + 12, fw, 16, Color4I.WHITE.withAlpha(10));
            roundOutline(g, fx, y + 12, fw, 16, LINE);
        }
        theme.drawString(g, free, fx + fw / 2, y + 16, s.freePoints() > 0 ? TEXT : MUTED, Theme.CENTERED | Theme.SHADOW);

        int bw = 120, bx = fx - 14 - bw;
        if (bx < x + 110) return;
        String xp = s.levelNeed() <= 0 ? "max level" : s.levelXp() + " / " + s.levelNeed() + " XP";
        Ui.textRight(g, theme, xp, bx + bw, y + 11, MUTED, false);
        round(g, bx, y + 22, bw, 5, Color4I.WHITE.withAlpha(16));
        float k = s.levelNeed() <= 0 ? 1F : Mth.clamp(s.levelXp() / (float) s.levelNeed(), 0F, 1F);
        int fill = (int) (bw * k);
        for (int i = 0; i < fill; i += 2) VIOLET_DEEP.lerp(VIOLET_LIGHT, i / (float) bw).draw(g, bx + i, y + 23, Math.min(2, fill - i), 3);
    }

    @Override
    protected void drawBody(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        drawCard(g, theme, x + bodyX(), y + bodyY(), CARD_W, bodyH());
        inset(g, x + rightX(), y + bodyY(), rightW(), TILES_H);
        drawDetail(g, theme, x + rightX(), y + detailY(), rightW(), detailH());
    }

    /** The character: the player under a violet glow, name, level, points spent, the book. */
    private void drawCard(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        CharacterPayload s = state();
        inset(g, x, y, w, h);
        int px = x + 6, py = y + 6, pw = w - 12, ph = portraitH();
        g.fillGradient(px + 1, py + 1, px + pw - 1, py + ph - 1, 0x10000000, VIOLET_DEEP.withAlpha(150).rgba());
        g.flush();
        GuiHelper.setupDrawing();
        float p = Anim.pulse(3000, 0F);
        VIOLET.withAlpha((int) (40 + 30 * p)).draw(g, px + pw / 2 - 26, py + ph - 8, 52, 3);
        roundOutline(g, px, py, pw, ph, VIOLET.withAlpha(120));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + 1, py + 1, px + pw - 1, py + ph - 1, (int) (ph * 0.36F), 0.0625F,
                    getMouseX(), getMouseY(), player);
        }
        GuiHelper.setupDrawing();
        VIOLET.draw(g, px + 1, py + ph - 2, pw - 2, 1);

        int ty = py + ph + 6;
        String name = player == null ? "" : upper(player.getGameProfile().getName());
        theme.drawString(g, Component.literal(Ui.fit(theme, name, w - 10)).withStyle(ChatFormatting.BOLD), x + w / 2, ty, TEXT, Theme.CENTERED | Theme.SHADOW);
        theme.drawString(g, "LEVEL " + s.level(), x + w / 2, ty + 11, VIOLET_LIGHT, Theme.CENTERED);
        theme.drawString(g, spent() + " points spent", x + w / 2, ty + 24, MUTED, Theme.CENTERED);
    }

    /** The tree picked: name, chips, the bar of segments, the line the orbs sit on; the lists' titles. */
    private void drawDetail(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        inset(g, x, y, w, h);
        SkillTree t = selected;
        Color4I c = color(t);
        int have = points(t), max = t.maxPoints(book());

        Component name = Component.literal(upper(treeName(t))).withStyle(ChatFormatting.BOLD);
        Ui.text(g, theme, name, x + 8, y + 9, c, true);
        Ui.text(g, theme, max == 0 ? "put a skill book in to open this tree" : "a passive or a skill every " + SkillTree.POINTS_PER_ORB + " points",
                x + 14 + theme.getStringWidth(name), y + 9, MUTED, false);

        // the chips
        int cx = x + 8, cy = y + 23;
        List<String> chips = chips(t);
        if (chips.isEmpty() && max > 0) Ui.text(g, theme, "No modifiers unlocked in this tree yet", cx, cy + 2, MUTED.withAlpha(170), false);
        for (String chip : chips) {
            int cw = theme.getStringWidth(chip) + 8;
            if (cx + cw > x + w - 8) break;
            round(g, cx, cy, cw, 12, c.withAlpha(30));
            roundOutline(g, cx, cy, cw, 12, c.withAlpha(110));
            Ui.text(g, theme, chip, cx + 4, cy + 2, TEXT, false);
            cx += cw + 4;
        }

        // the bar: one segment per orb, filled as far as the points go
        int by = y + 41, segs = Math.max(1, (max == 0 ? SkillTree.MAX_POINTS : max) / SkillTree.POINTS_PER_ORB);
        String count = max == 0 ? "-" : have + " / " + max;
        int countW = theme.getStringWidth(count);
        int bx = x + 8, sw = (w - 16 - countW - 8) / segs;
        for (int i = 0; i < segs; i++) {
            int segX = bx + i * sw;
            float f = max == 0 ? 0F : Mth.clamp((have - i * SkillTree.POINTS_PER_ORB) / (float) SkillTree.POINTS_PER_ORB, 0F, 1F);
            round(g, segX, by, sw - 3, 6, Color4I.WHITE.withAlpha(14));
            if (f > 0) {
                round(g, segX, by, Math.max(4, (int) ((sw - 3) * f)), 6, c.withAlpha(f >= 1F ? 230 : 150));
                if (f >= 1F) Color4I.WHITE.withAlpha(70).draw(g, segX + 2, by + 1, sw - 7, 1);
            }
        }
        Ui.text(g, theme, count, x + w - 8 - countW, by - 1, max == 0 ? MUTED : TEXT, false);

        // the line the orbs sit on, lit as far as they are unlocked
        List<SkillOrb> orbs = t.orbs(book());
        if (orbs.size() > 1) {
            int ly = y + NODES_Y + NODE / 2, dx = x - rightX();
            int x0 = dx + nodeX(0, orbs.size()) + NODE / 2, x1 = dx + nodeX(orbs.size() - 1, orbs.size()) + NODE / 2;
            LINE.draw(g, x0, ly, x1 - x0, 1);
            int lit = -1;
            for (int i = 0; i < orbs.size(); i++) if (orbs.get(i).points() <= have) lit = i;
            if (lit > 0) {
                int xe = dx + nodeX(lit, orbs.size()) + NODE / 2;
                c.withAlpha(60).draw(g, x0, ly - 1, xe - x0, 3);
                c.draw(g, x0, ly, xe - x0, 1);
            }
        }

        if (listsH() >= 30) {
            int lx = x + 8, lw = listW(), ly = y + (listsY() - detailY());
            LINE.draw(g, x + 8, ly - 5, w - 16, 1);
            section(g, theme, "MODIFIERS", lx + 2, ly + 3, lw - 4);
            section(g, theme, "SKILLS", lx + lw + GAP + 2, ly + 3, lw - 4);
        }
    }

    /** The detail fades in again when another tree is picked. */
    @Override
    public void drawForeground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        super.drawForeground(g, theme, x, y, w, h);
        float t = Anim.easeOut(Anim.progress(selectedAt, 220));
        if (t >= 1F) return;
        GuiHelper.setupDrawing();
        Color4I.rgba(8, 7, 12, (int) ((1F - t) * 200)).draw(g, x + rightX() + 1, y + detailY() + 1, rightW() - 2, detailH() - 2);
    }

    // ------------------------------------------------------------------ widgets

    /** One tree at the top: its name in its colour, its icon in a framed square, its points. */
    private final class TreeTile extends Button {
        final SkillTree tree;
        private final Anim hover = new Anim();
        private final Anim pick = new Anim();

        TreeTile(Panel panel, SkillTree tree) {
            super(panel, Component.literal(tree.title), Icon.empty());
            this.tree = tree;
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            Color4I c = color(tree);
            float k = hover.toward(isMouseOver(), 14F), s = pick.toward(selected == tree, 10F);
            int max = tree.maxPoints(book());
            if (s > 0.01F || k > 0.01F) {
                round(g, x + 2, y, w - 4, h, c.withAlpha((int) (12 * k + 20 * s)));
                c.withAlpha((int) (230 * s)).draw(g, x + w / 2 - (int) (18 * s), y + h - 2, (int) (36 * s), 2);
            }
            String name = tree == SkillTree.BOOK ? "BOOK" : upper(tree.title);
            theme.drawString(g, Component.literal(Ui.fit(theme, name, w - 6)).withStyle(ChatFormatting.BOLD), x + w / 2, y + 4, c, Theme.CENTERED | Theme.SHADOW);
            int bx = x + w / 2 - 11, by = y + 15;
            int glow = (int) (40 * s + 30 * k * Anim.pulse(1600, tree.ordinal()));
            if (glow > 0) roundOutline(g, bx - 2, by - 2, 26, 26, c.withAlpha(glow));
            round(g, bx, by, 22, 22, Color4I.rgba(0, 0, 0, 150));
            roundOutline(g, bx, by, 22, 22, c.withAlpha(max == 0 ? 90 : (int) (160 + 95 * Math.max(k, s))));
            treeIcon(tree).draw(g, bx + 3, by + 3, 16, 16);
            theme.drawString(g, max == 0 ? "empty" : points(tree) + "/" + max, x + w / 2, by + 25, s > 0.5F && max > 0 ? TEXT : MUTED, Theme.CENTERED);
        }

        @Override
        public void onClicked(MouseButton button) {
            if (selected == tree) return;
            playClickSound();
            select(tree);
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.literal(treeName(tree)).withStyle(ChatFormatting.BOLD).withColor(tree.color));
            int max = tree.maxPoints(book());
            if (max == 0) list.add(Component.literal("Put a skill book in to open this tree").withStyle(ChatFormatting.GRAY));
            else list.add(Component.literal(points(tree) + " of " + max + " points").withStyle(ChatFormatting.GRAY));
            if (selected != tree) list.add(Component.literal("Click to look at it").withStyle(ChatFormatting.YELLOW));
        }
    }

    /** One orb of the tree picked: a framed square, a diamond for a skill, a ring for a passive. */
    private final class Node extends Button {
        final SkillTree tree;
        final SkillOrb orb;
        private final Anim hover = new Anim();

        Node(Panel panel, SkillTree tree, SkillOrb orb) {
            super(panel, Component.literal(orb.name()), Icon.empty());
            this.tree = tree;
            this.orb = orb;
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
            boolean on = unlocked();
            float k = hover.toward(isMouseOver(), 14F);
            if (on) roundOutline(g, x - 2, y - 2, w + 4, h + 4, c.withAlpha((int) (50 + 50 * Anim.pulse(2400, orb.points()))));
            if (k > 0.01F) roundOutline(g, x - 1, y - 1, w + 2, h + 2, TEXT.withAlpha((int) (120 * k)));
            round(g, x, y, w, h, on ? c.withAlpha(200) : Color4I.rgba(0, 0, 0, 170));
            roundOutline(g, x, y, w, h, on ? c : (reachable() > 0 ? c.withAlpha(170) : Color4I.rgb(0x4a4560)));
            if (on) Color4I.WHITE.withAlpha(80).draw(g, x + 2, y + 1, w - 4, 1);
            int cx = x + w / 2, cy = y + h / 2;
            Color4I mark = on ? TEXT : (reachable() > 0 ? c : MUTED.withAlpha(150));
            if (orb.skill()) {
                for (int i = 0; i < 4; i++) mark.draw(g, cx - i, cy - 3 + i, i * 2 + 1, 1);
                for (int i = 0; i < 3; i++) mark.draw(g, cx - 2 + i, cy + 1 + i, 5 - i * 2, 1);
            } else {
                mark.draw(g, cx - 1, cy - 3, 3, 1);
                mark.draw(g, cx - 1, cy + 2, 3, 1);
                mark.draw(g, cx - 3, cy - 1, 1, 3);
                mark.draw(g, cx + 2, cy - 1, 1, 3);
            }
            theme.drawString(g, String.valueOf(orb.points()), cx + 1, y + h + 3, on ? c : MUTED, Theme.CENTERED);
        }

        /** Points go in only with +1 / +5; an orb only shows what it gives. */
        @Override
        public void onClicked(MouseButton button) {
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.literal(orb.name()).withStyle(ChatFormatting.BOLD).withColor(tree.color));
            list.add(Component.literal((orb.skill() ? "Skill" : "Passive") + " of " + treeName(tree) + " - at " + orb.points() + " points")
                    .withStyle(ChatFormatting.GRAY));
            List<String> what = orb.skill() ? orb.skills() : orb.modifiers();
            if (what.isEmpty()) list.add(Component.literal(orb.skill() ? "No skill yet" : "No modifiers yet").withStyle(ChatFormatting.DARK_GRAY));
            for (String line : what) list.add(Component.literal(line));
            int need = orb.points() - points(tree);
            if (need <= 0) list.add(Component.literal("Unlocked").withStyle(ChatFormatting.GREEN));
            else list.add(Component.literal("Needs " + need + " more points").withStyle(ChatFormatting.RED));
        }
    }

    // ------------------------------------------------------------------ the skill book

    /** The card's skill book button: the book on a violet flame that flares up under the mouse. */
    private final class BookButton extends Button {
        static final int H = 34;
        private final Anim hover = new Anim();

        BookButton(Panel panel) {
            super(panel, Component.literal("Skill book"), Icon.empty());
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            float k = hover.toward(isMouseOver(), 9F), p = Anim.pulse(2200, 0F);
            for (int i = 1; i <= 3; i++) roundOutline(g, x - i, y - i, w + i * 2, h + i * 2, VIOLET.withAlpha((int) ((10 + 14 * p + 30 * k) * (4 - i) / 3F)));
            round(g, x, y, w, h, Color4I.rgba(6, 3, 12, 220));
            Flame.draw(g, x + 1, y + 1, w - 2, h - 2, 0.55F + 0.12F * p + 0.33F * k, 0F);
            // a shine sweeping across now and then
            float sweep = (System.currentTimeMillis() % 3400L) / 1400F;
            if (sweep < 1F) {
                int sx = x + (int) ((w + 20) * sweep) - 10;
                for (int i = 0; i < 8; i++) {
                    int px = sx + i;
                    if (px > x + 1 && px < x + w - 1) Color4I.WHITE.withAlpha((int) (26 * Mth.sin(i / 8F * Mth.PI))).draw(g, px, y + 1, 1, h - 2);
                }
            }
            roundOutline(g, x, y, w, h, VIOLET_LIGHT.withAlpha((int) (130 + 60 * p + 65 * k)));
            Color4I.WHITE.withAlpha((int) (30 + 40 * k)).draw(g, x + 3, y + 1, w - 6, 1);

            // the book, lifting a little under the mouse
            int ix = x + 7, iy = y + (h - 20) / 2;
            round(g, ix, iy, 20, 20, Color4I.rgba(0, 0, 0, 150));
            roundOutline(g, ix, iy, 20, 20, VIOLET_LIGHT.withAlpha(200));
            g.pose().pushPose();
            g.pose().translate(0, -1.5F * k, 0);
            treeIcon(SkillTree.BOOK).draw(g, ix + 2, iy + 2, 16, 16);
            g.pose().popPose();
            int tx = ix + 26;
            String name = SkillBook.byId(book()).map(SkillBook::name).orElse("Put one in");
            theme.drawString(g, Component.literal("SKILL BOOK").withStyle(ChatFormatting.BOLD), tx, y + 8, TEXT, Theme.SHADOW);
            theme.drawString(g, Ui.fit(theme, name, w - (tx - x) - 4), tx, y + 19, VIOLET_LIGHT.lerp(TEXT, k), Theme.SHADOW);
        }

        @Override
        public void onClicked(MouseButton button) {
            playClickSound();
            new BookPicker(SkillTreeScreen.this).open();
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.literal(SkillBook.byId(book()).map(b -> "Skill book: " + b.name()).orElse("No skill book in the tree"))
                    .withStyle(ChatFormatting.BOLD));
            list.add(Component.literal("The skill book gives the fourth tree its orbs").withStyle(ChatFormatting.GRAY));
            list.add(Component.literal("Click to choose from the books you carry").withStyle(ChatFormatting.YELLOW));
        }
    }

    /** The skill books in the player's inventory (nothing else), one row each, to put one in; and taking the book out. */
    private static final class BookPicker extends GlassModal {
        private static final int W = 252, ROW = 28, VISIBLE = 5, FLAME_H = 30;
        private final List<Integer> books = new ArrayList<>(); // inventory slots

        BookPicker(BaseScreen gui) {
            super(gui, "Skill books", VIOLET, W, 0);
            var player = Minecraft.getInstance().player;
            if (player != null) {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    if (SkillBook.of(player.getInventory().getItem(i)).isPresent()) books.add(i);
                }
            }
            setSize(W, listY() + listH() + 8 + (SkillBook.byId(book()).isPresent() ? 22 : 0) + FLAME_H);
        }

        private static int listY() {
            return TITLE_H + 24;
        }

        private int listH() {
            return Math.min(VISIBLE, Math.max(1, books.size())) * ROW;
        }

        @Override
        public void addWidgets() {
            if (!books.isEmpty()) {
                Panel list = new Panel(this) {
                    @Override
                    public void addWidgets() {
                        var player = Minecraft.getInstance().player;
                        for (int i = 0; i < books.size(); i++) {
                            int slot = books.get(i);
                            ItemStack stack = player == null ? ItemStack.EMPTY : player.getInventory().getItem(slot).copy();
                            BookRow row = new BookRow(this, stack, slot);
                            row.setPosAndSize(0, i * ROW, width, ROW - 3);
                            add(row);
                        }
                    }

                    @Override
                    public void alignWidgets() {
                    }

                    @Override
                    public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
                    }
                };
                list.setOnlyRenderWidgetsInside(true);
                list.setOnlyInteractWithWidgetsInside(true);
                list.setScrollStep(ROW);
                list.setPosAndSize(10, listY(), W - 26, listH());
                add(list);
                if (books.size() > VISIBLE) {
                    PanelScrollBar bar = scrollBar(this, list);
                    bar.setPosAndSize(W - 13, listY(), 3, listH());
                    add(bar);
                }
            }
            int y = height - FLAME_H + 7;
            if (SkillBook.byId(book()).isPresent()) {
                int refund = points(SkillTree.BOOK);
                Pill out = new Pill(this, "Take the book out" + (refund > 0 ? " (" + refund + " points back)" : ""), Ui.RED, () -> {
                    close();
                    command("divinis skills book remove");
                });
                out.setPosAndSize(10, y - 22, W - 20, 16);
                add(out);
            }
            Pill cancel = new Pill(this, "Close", VIOLET_LIGHT, this::close);
            cancel.setPosAndSize((W - 90) / 2, y, 90, 16);
            add(cancel);
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            super.drawBackground(g, theme, x, y, w, h);
            Flame.draw(g, x + 2, y + h - FLAME_H - 6, w - 4, FLAME_H + 4, 0.5F + 0.1F * Anim.pulse(2600, 1F), 3F);
            int ty = y + TITLE_H + 8;
            SkillBook.byId(book()).ifPresentOrElse(b -> {
                Ui.text(g, theme, "In the tree:", x + 10, ty, MUTED, false);
                int cx = x + 14 + theme.getStringWidth("In the tree:"), cw = theme.getStringWidth(b.name()) + 10;
                round(g, cx, ty - 2, cw, 12, VIOLET.withAlpha(50));
                roundOutline(g, cx, ty - 2, cw, 12, VIOLET_LIGHT.withAlpha(170));
                Ui.text(g, theme, b.name(), cx + 5, ty, TEXT, false);
            }, () -> Ui.text(g, theme, "No skill book in the tree yet", x + 10, ty, MUTED, false));
            if (books.isEmpty()) {
                inset(g, x + 10, y + listY(), w - 20, ROW - 3);
                theme.drawString(g, "No skill books in your inventory", x + w / 2, y + listY() + 9, MUTED, Theme.CENTERED);
            }
        }

        /** One skill book of the inventory: its icon, name, and what it holds. */
        private final class BookRow extends Button {
            final ItemStack stack;
            final int slot;
            private final Anim hover = new Anim();

            BookRow(Panel panel, ItemStack stack, int slot) {
                super(panel, stack.getHoverName(), ItemIcon.getItemIcon(stack));
                this.stack = stack;
                this.slot = slot;
            }

            private boolean inTree() {
                return SkillBook.of(stack).map(b -> b.id().equals(book())).orElse(false);
            }

            @Override
            public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
                GuiHelper.setupDrawing();
                boolean in = inTree();
                float k = hover.toward(isMouseOver() && !in, 12F);
                round(g, x, y, w, h, in ? VIOLET.withAlpha(45) : Color4I.WHITE.withAlpha((int) (8 + 14 * k)));
                roundOutline(g, x, y, w, h, in ? VIOLET_LIGHT.withAlpha(190) : VIOLET.withAlpha((int) (45 + 170 * k)));
                if (k > 0.01F) VIOLET.withAlpha((int) (220 * k)).draw(g, x + 1, y + 3, 2, h - 6);
                int ix = x + 4 + (int) (3 * k), iy = y + (h - 20) / 2;
                round(g, ix, iy, 20, 20, Color4I.rgba(0, 0, 0, 150));
                roundOutline(g, ix, iy, 20, 20, VIOLET.withAlpha((int) (120 + 100 * k)));
                icon.draw(g, ix + 2, iy + 2, 16, 16);
                int tx = ix + 26;
                Ui.text(g, theme, stack.getHoverName(), tx, y + 4, TEXT, true);
                SkillBook.of(stack).ifPresent(b -> {
                    long skills = b.orbs().stream().filter(SkillOrb::skill).count();
                    String what = (b.orbs().size() - skills) + " passives, " + skills + " skills, " + b.maxPoints() + " points";
                    Ui.text(g, theme, Ui.fit(theme, what, w - (tx - x) - (in ? 70 : 6)), tx, y + 14, MUTED, false);
                });
                if (in) {
                    String tag = "IN THE TREE";
                    int tw = theme.getStringWidth(tag) + 8;
                    round(g, x + w - tw - 5, y + (h - 12) / 2, tw, 12, VIOLET.withAlpha(110));
                    Ui.text(g, theme, tag, x + w - tw - 1, y + (h - 12) / 2 + 2, TEXT, false);
                }
            }

            @Override
            public void onClicked(MouseButton button) {
                if (inTree()) return;
                playClickSound();
                close();
                command("divinis skills book insert " + slot);
            }

            @Override
            public void addMouseOverText(TooltipList list) {
                list.add(stack.getHoverName());
                SkillBook.of(stack).ifPresent(b -> {
                    for (SkillOrb orb : b.orbs()) {
                        list.add(Component.literal((orb.skill() ? "Skill" : "Passive") + " at " + orb.points() + ": ").withStyle(ChatFormatting.DARK_GRAY)
                                .append(Component.literal(orb.name()).withStyle(orb.skill() ? ChatFormatting.AQUA : ChatFormatting.GREEN)));
                    }
                });
                int refund = points(SkillTree.BOOK);
                list.add(Component.literal(inTree() ? "Already in the tree"
                        : SkillBook.byId(book()).isPresent()
                                ? "Click: replaces the book in the tree" + (refund > 0 ? " (its " + refund + " points come back)" : "")
                                : "Click: put it into the fourth tree").withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    // ------------------------------------------------------------------ the lists at the bottom

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
                if (l.indent() == 0 && l.color() != MUTED) y += 1;
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
}
