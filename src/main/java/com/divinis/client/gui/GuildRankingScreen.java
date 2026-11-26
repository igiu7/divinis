package com.divinis.client.gui;

import java.util.List;

import com.divinis.net.GuildMasterOpenPayload.RankRow;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Panel;
import net.minecraft.world.item.Items;

/** The fastest clears of the Labyrinth, as the Guild Master keeps them. */
public final class GuildRankingScreen extends DivinisFrame {
    private final List<RankRow> rows;

    public GuildRankingScreen(List<RankRow> rows) {
        this.rows = rows;
        preferredWidth = 380;
        preferredHeight = 260;
    }

    @Override
    protected String title() {
        return "Labyrinth Ranking";
    }

    @Override
    protected String subtitle() {
        return "The fastest parties to slay the guardian";
    }

    @Override
    protected Color4I accent() {
        return Ui.GOLD;
    }

    @Override
    protected Icon headerIcon() {
        return ItemIcon.getItemIcon(Items.CLOCK);
    }

    @Override
    protected String footer() {
        return "Esc - back to the Guild Master";
    }

    private static String time(int ticks) {
        int s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    @Override
    protected void buildContent(Panel p, int cw) {
        if (rows.isEmpty()) {
            p.add(new Widgets.InfoBox(p, cw, "Nobody has cleared the Labyrinth yet. Be the first!", Ui.BLUE,
                    ItemIcon.getItemIcon(Items.COMPASS)));
            return;
        }
        p.add(new Widgets.SectionHeader(p, cw, "Top " + rows.size()));
        for (int i = 0; i < rows.size(); i++) {
            RankRow r = rows.get(i);
            Icon icon = ItemIcon.getItemIcon(i == 0 ? Items.GOLD_INGOT : i == 1 ? Items.IRON_INGOT : i == 2 ? Items.COPPER_INGOT : Items.PAPER);
            Color4I color = i == 0 ? Ui.GOLD : i < 3 ? Ui.BLUE : Ui.GRAY;
            p.add(new Tile(p, cw, "#" + (i + 1) + "  " + r.names(), r.players() + (r.players() == 1 ? " player" : " players"), icon)
                    .accent(color)
                    .badge(time(r.ticks()), color)
                    .tip(r.names(), "Time: " + time(r.ticks())));
        }
    }
}
