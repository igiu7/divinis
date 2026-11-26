package com.divinis.client.gui;

import java.util.List;

import com.divinis.client.ClientQuests;
import com.divinis.quest.Quest;
import com.divinis.quest.Quests;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.PanelScrollBar;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

/**
 * A module screen of quests: an optional card on the left, a list of quests, and the quest picked in detail - its story,
 * its objective with the progress, its reward and the buttons the screen gives it. The Guild Master's screen lists all
 * his quests, the quest log only those taken.
 */
public abstract class QuestScreen extends ModuleScreen {
    static final int LIST_W = 196;
    static final int ROW = 30;
    static final Color4I GREEN = Color4I.rgb(0x58c47a);
    static final Color4I GOLD = Color4I.rgb(0xf2c14e);

    private long selectedAt;

    /** The quests the list shows. */
    protected abstract List<Quest> quests();

    /** The id of the quest picked (kept by the screen across reopenings). */
    protected abstract String selectedId();

    protected abstract void setSelectedId(String id);

    /** Width of the card on the left (0: none). */
    protected int cardW() {
        return 0;
    }

    /** The detail's buttons for the quest, right-aligned at the bottom of the detail (relative coordinates). */
    protected abstract void addButtons(Quest quest, int right, int y);

    protected String listTitle() {
        return "QUESTS";
    }

    protected String emptyText() {
        return "No quests.";
    }

    QuestScreen() {
        preferredWidth = 670;
        preferredHeight = 350;
        minWidth = 470;
        minHeight = 270;
    }

    /** Called when the server sent the player's quests. */
    public void onStateChanged() {
        refreshWidgets();
    }

    static void command(String command) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.connection.sendCommand(command);
    }

    Quest selected() {
        List<Quest> list = quests();
        for (Quest q : list) if (q.id().equals(selectedId())) return q;
        return list.isEmpty() ? null : list.get(0);
    }

    static boolean ready(Quest q) {
        return ClientQuests.active(q) && ClientQuests.progress(q) >= q.count();
    }

    // ------------------------------------------------------------------ layout (relative)

    int listX() { return bodyX() + (cardW() > 0 ? cardW() + GAP : 0); }
    int detailX() { return listX() + LIST_W + GAP; }
    int detailW() { return bodyX() + bodyW() - detailX(); }

    @Override
    protected void addBody() {
        addCard();
        List<Quest> list = quests();
        int ly = bodyY() + 18, lh = bodyH() - 22;
        if (!list.isEmpty()) {
            QuestList panel = new QuestList(this, list);
            panel.setPosAndSize(listX() + 4, ly, LIST_W - 12, lh);
            add(panel);
            PanelScrollBar bar = scrollBar(this, panel);
            bar.setPosAndSize(listX() + LIST_W - 6, ly, 3, lh);
            add(bar);
        }
        Quest q = selected();
        if (q != null) addButtons(q, detailX() + detailW() - 8, bodyY() + bodyH() - 24);
    }

    /** The card's widgets, if the screen has a card. */
    protected void addCard() {}

    /** The card, drawn behind its widgets (absolute). */
    protected void drawCard(GuiGraphics g, Theme theme, int x, int y, int w, int h) {}

    @Override
    protected void drawBody(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        if (cardW() > 0) drawCard(g, theme, x + bodyX(), y + bodyY(), cardW(), bodyH());
        int lx = x + listX(), by = y + bodyY();
        inset(g, lx, by, LIST_W, bodyH());
        section(g, theme, listTitle(), lx + 7, by + 5, LIST_W - 14);
        if (quests().isEmpty()) {
            List<FormattedText> lines = theme.listFormattedStringToWidth(Component.literal(emptyText()), LIST_W - 20);
            for (int i = 0; i < lines.size(); i++) Ui.text(g, theme, lines.get(i), lx + 9, by + 22 + i * 10, MUTED, false);
        }
        drawDetail(g, theme, x + detailX(), by, detailW(), bodyH());
    }

    private void drawDetail(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        inset(g, x, y, w, h);
        Quest q = selected();
        if (q == null) {
            theme.drawString(g, "Pick a quest on the left", x + w / 2, y + h / 2 - 4, MUTED, Theme.CENTERED);
            return;
        }
        boolean active = ClientQuests.active(q), ready = ready(q);
        int progress = Math.min(q.count(), ClientQuests.progress(q));

        // icon, name, state
        float p = Anim.pulse(2600, 0F);
        roundOutline(g, x + 7, y + 7, 30, 30, VIOLET.withAlpha((int) (30 + 40 * p)));
        round(g, x + 8, y + 8, 28, 28, Color4I.rgba(0, 0, 0, 150));
        roundOutline(g, x + 8, y + 8, 28, 28, VIOLET.withAlpha(200));
        ItemIcon.getItemIcon(q.icon().get()).draw(g, x + 14, y + 14, 16, 16);
        Ui.text(g, theme, Component.literal(Ui.fit(theme, q.name(), w - 120)).withStyle(ChatFormatting.BOLD), x + 44, y + 11, TEXT, true);
        String state = ready ? "READY" : active ? "IN PROGRESS" : "AVAILABLE";
        Color4I sc = ready ? GREEN : active ? VIOLET_LIGHT : MUTED;
        int sw = theme.getStringWidth(state) + 10;
        round(g, x + w - sw - 8, y + 10, sw, 12, sc.withAlpha(ready ? (int) (50 + 50 * p) : 40));
        roundOutline(g, x + w - sw - 8, y + 10, sw, 12, sc.withAlpha(180));
        Ui.text(g, theme, state, x + w - sw - 3, y + 12, TEXT, false);
        int done = ClientQuests.done(q);
        Ui.text(g, theme, done == 0 ? "Never done yet" : "Done " + done + (done == 1 ? " time" : " times"), x + 44, y + 24, MUTED, false);

        // the story
        int ty = y + 46;
        for (FormattedText line : theme.listFormattedStringToWidth(Component.literal(q.story()), w - 20)) {
            Ui.text(g, theme, line, x + 10, ty, Color4I.rgb(0xc9c2da), false);
            ty += 10;
        }

        // the objective
        ty += 8;
        section(g, theme, "OBJECTIVE", x + 10, ty, w - 20);
        ty += 14;
        Ui.text(g, theme, q.task(), x + 10, ty, TEXT, false);
        String count = progress + " / " + q.count();
        Ui.textRight(g, theme, count, x + w - 10, ty, ready ? GREEN : active ? TEXT : MUTED, false);
        ty += 12;
        int bw = w - 20;
        round(g, x + 10, ty, bw, 6, Color4I.WHITE.withAlpha(16));
        int fill = (int) (bw * Mth.clamp(progress / (float) q.count(), 0F, 1F));
        if (fill > 0) {
            Color4I a = ready ? GREEN.addBrightness(-0.2F) : VIOLET_DEEP, b = ready ? GREEN : VIOLET_LIGHT;
            for (int i = 0; i < fill; i += 2) a.lerp(b, i / (float) bw).draw(g, x + 10 + i, ty + 1, Math.min(2, fill - i), 4);
        }
        if (q.kind() == Quest.Kind.DELIVER) {
            ty += 10;
            Ui.text(g, theme, active ? "Counted from your inventory; taken when you turn it in." : "Take the quest to start it.", x + 10, ty, MUTED, false);
        }

        // the reward
        ty += 16;
        section(g, theme, "REWARD", x + 10, ty, w - 20);
        ty += 13;
        int cx = x + 10;
        cx = rewardChip(g, theme, cx, ty, ItemIcon.getItemIcon(Items.EMERALD), q.emeralds() + " emeralds", GREEN);
        rewardChip(g, theme, cx, ty, ItemIcon.getItemIcon(Items.EXPERIENCE_BOTTLE), q.xp() + " XP", VIOLET_LIGHT);
    }

    /** A chip with an item icon; returns where the next one goes. */
    private static int rewardChip(GuiGraphics g, Theme theme, int x, int y, Icon icon, String text, Color4I c) {
        int w = theme.getStringWidth(text) + 22;
        round(g, x, y, w, 14, c.withAlpha(30));
        roundOutline(g, x, y, w, 14, c.withAlpha(130));
        icon.draw(g, x + 3, y + 3, 9, 9);
        Ui.text(g, theme, text, x + 15, y + 3, TEXT, false);
        return x + w + 5;
    }

    /** The detail fades in again when another quest is picked. */
    @Override
    public void drawForeground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        super.drawForeground(g, theme, x, y, w, h);
        float t = Anim.easeOut(Anim.progress(selectedAt, 220));
        if (t >= 1F) return;
        GuiHelper.setupDrawing();
        Color4I.rgba(8, 7, 12, (int) ((1F - t) * 200)).draw(g, x + detailX() + 1, y + bodyY() + 1, detailW() - 2, bodyH() - 2);
    }

    void giveUp(Quest q) {
        GlassModal.confirm(this, "Give up " + q.name() + "?", q.kind() == Quest.Kind.SLAY
                        ? "The mobs you have slain for it are not kept. You can take it again at the Guild Master."
                        : "You can take it again at the Guild Master.",
                Ui.RED, () -> command("divinis quests abandon " + q.id()));
    }

    // ------------------------------------------------------------------ the list

    private final class QuestList extends Panel {
        private final List<Quest> list;

        QuestList(Panel parent, List<Quest> list) {
            super(parent);
            this.list = list;
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
            setScrollStep(ROW);
        }

        @Override
        public void addWidgets() {
            for (int i = 0; i < list.size(); i++) {
                QuestRow row = new QuestRow(this, list.get(i));
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
    }

    /** A quest in the list: icon, name, and how far it is (or what it asks). */
    private final class QuestRow extends Button {
        private final Quest quest;
        private final Anim hover = new Anim();

        QuestRow(Panel panel, Quest quest) {
            super(panel, Component.literal(quest.name()), ItemIcon.getItemIcon(quest.icon().get()));
            this.quest = quest;
        }

        private boolean picked() {
            Quest s = selected();
            return s != null && s.id().equals(quest.id());
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            boolean picked = picked(), active = ClientQuests.active(quest), ready = ready(quest);
            float k = hover.toward(isMouseOver() || picked, 12F);
            round(g, x, y, w, h, picked ? VIOLET.withAlpha(45) : Color4I.WHITE.withAlpha((int) (6 + 12 * k)));
            roundOutline(g, x, y, w, h, picked ? VIOLET_LIGHT.withAlpha(170) : VIOLET.withAlpha((int) (35 + 140 * k)));
            if (picked) VIOLET.draw(g, x + 1, y + 3, 2, h - 6);
            int ix = x + 4 + (int) (2 * k), iy = y + (h - 20) / 2;
            round(g, ix, iy, 20, 20, Color4I.rgba(0, 0, 0, 150));
            roundOutline(g, ix, iy, 20, 20, (ready ? GREEN : VIOLET).withAlpha((int) (110 + 100 * k)));
            icon.draw(g, ix + 2, iy + 2, 16, 16);
            int tx = ix + 25, tw = w - (tx - x) - 5;
            Ui.text(g, theme, Ui.fit(theme, quest.name(), tw), tx, y + 4, TEXT, true);
            if (ready) {
                float p = Anim.pulse(1500, 0F);
                Ui.text(g, theme, "Ready to turn in", tx, y + 15, GREEN.lerp(TEXT, 0.3F * p), false);
            } else if (active) {
                int progress = Math.min(quest.count(), ClientQuests.progress(quest));
                String count = progress + "/" + quest.count();
                int bw = tw - theme.getStringWidth(count) - 5;
                round(g, tx, y + 17, bw, 4, Color4I.WHITE.withAlpha(16));
                int fill = (int) (bw * (progress / (float) quest.count()));
                if (fill > 0) round(g, tx, y + 17, Math.max(4, fill), 4, VIOLET_LIGHT.withAlpha(220));
                Ui.textRight(g, theme, count, tx + tw, y + 15, MUTED, false);
            } else {
                Ui.text(g, theme, Ui.fit(theme, quest.task(), tw), tx, y + 15, MUTED, false);
            }
        }

        @Override
        public void onClicked(MouseButton button) {
            if (picked()) return;
            playClickSound();
            setSelectedId(quest.id());
            selectedAt = System.currentTimeMillis();
            QuestScreen.this.refreshWidgets();
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.literal(quest.name()).withStyle(ChatFormatting.BOLD));
            list.add(Component.literal(quest.task()).withStyle(ChatFormatting.GRAY));
            list.add(Component.literal("Reward: " + quest.emeralds() + " emeralds, " + quest.xp() + " XP").withStyle(ChatFormatting.GOLD));
        }
    }

    /** The quests taken, in the Guild Master's order. */
    static List<Quest> activeQuests() {
        return Quests.ALL.stream().filter(ClientQuests::active).toList();
    }
}
