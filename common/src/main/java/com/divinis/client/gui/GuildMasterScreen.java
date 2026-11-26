package com.divinis.client.gui;

import java.util.List;

import com.divinis.net.GuildMasterOpenPayload;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Panel;
import net.minecraft.world.item.Items;

/** The Guild Master's menu (right-click on npc.GuildMaster): his shop, his quests and the Labyrinth ranking. */
public final class GuildMasterScreen extends DivinisFrame {
    private final GuildMasterOpenPayload data;

    public GuildMasterScreen(GuildMasterOpenPayload data) {
        this.data = data;
        preferredWidth = 420;
        preferredHeight = 240;
    }

    @Override
    protected String title() {
        return "Guild Master";
    }

    @Override
    protected String subtitle() {
        return "What brings you to the guild, traveller?";
    }

    @Override
    protected Color4I accent() {
        return Ui.GOLD;
    }

    @Override
    protected Icon headerIcon() {
        return ItemIcon.getItemIcon(Items.GOLDEN_HELMET);
    }

    @Override
    protected String footer() {
        return "Right-click the Guild Master to talk to him again";
    }

    @Override
    protected void buildContent(Panel p, int cw) {
        int h = Math.max(140, Math.min(170, getHeight() - HEADER_H - FOOTER_H - 20));
        p.add(new ChoiceCards(p, cw, h, List.of(
                new ChoiceCards.Card("Shop", "Tools, supplies and rare goods of the guild.", "Open",
                        ItemIcon.getItemIcon(Items.EMERALD), Ui.GREEN, true, () -> new GuildShopScreen().openGui()),
                new ChoiceCards.Card("Quests", "Work for the guild and earn its rewards.", "Open",
                        ItemIcon.getItemIcon(Items.WRITABLE_BOOK), Ui.BLUE, true, () -> new GuildQuestsScreen(data.entityId()).openGui()),
                new ChoiceCards.Card("Ranking", "The fastest parties through the Labyrinth.", "Open",
                        ItemIcon.getItemIcon(Items.CLOCK), Ui.GOLD, true, () -> new GuildRankingScreen(data.ranking()).openGui()))));
    }
}
