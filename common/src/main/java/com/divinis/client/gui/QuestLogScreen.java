package com.divinis.client.gui;

import java.util.List;

import com.divinis.client.ClientQuests;
import com.divinis.quest.Quest;
import com.divinis.quest.Quests;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Items;

/** The quest log module: the quests the player has taken, how far each is, and giving one up. */
public final class QuestLogScreen extends QuestScreen {
    private static String selectedId = "";

    @Override
    protected String moduleId() {
        return "quests";
    }

    @Override
    protected String title() {
        return "Quest Log";
    }

    @Override
    protected String subtitle() {
        int n = ClientQuests.activeCount();
        return n == 0 ? "No quests taken" : n + (n == 1 ? " quest" : " quests") + " in progress";
    }

    @Override
    protected Icon icon() {
        return ItemIcon.getItemIcon(Items.WRITABLE_BOOK);
    }

    @Override
    protected String hint() {
        return "Quests are taken and turned in at the Guild Master.";
    }

    @Override
    protected List<Quest> quests() {
        return activeQuests();
    }

    @Override
    protected String selectedId() {
        return selectedId;
    }

    @Override
    protected void setSelectedId(String id) {
        selectedId = id;
    }

    @Override
    protected String listTitle() {
        return "ACTIVE QUESTS";
    }

    @Override
    protected String emptyText() {
        return "You have no quests. The Guild Master has work for you - right-click him to see it.";
    }

    @Override
    protected void drawHeaderExtras(GuiGraphics g, Theme theme, int x, int y, int right) {
        String done = ClientQuests.totalDone() + " done";
        int w = theme.getStringWidth(done) + 16, px = right - w;
        round(g, px, y + 12, w, 16, VIOLET.withAlpha(35));
        roundOutline(g, px, y + 12, w, 16, VIOLET_LIGHT.withAlpha(150));
        theme.drawString(g, done, px + w / 2, y + 16, TEXT, Theme.CENTERED | Theme.SHADOW);
        String active = "Active " + ClientQuests.activeCount() + " / " + Quests.MAX_ACTIVE;
        Ui.textRight(g, theme, active, px - 10, y + 16, MUTED, false);
    }

    @Override
    protected void addButtons(Quest q, int right, int y) {
        Pill info = new Pill(this, () -> ready(q) ? "Turn in at the Guild Master" : "Not done yet", QuestScreen.GREEN, () -> false, () -> {});
        info.setPosAndSize(right - 150, y, 150, 16);
        add(info);
        Pill giveUp = new Pill(this, "Give up", Ui.RED, () -> giveUp(q));
        giveUp.setPosAndSize(right - 226, y, 70, 16);
        add(giveUp);
    }
}
