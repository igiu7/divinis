package com.divinis.client.gui;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Panel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The Guild Master's quests: the Labyrinth (he opens a portal to it for 30 seconds) and, for now, example quests
 * that cannot be taken yet.
 */
public final class GuildQuestsScreen extends DivinisFrame {
    private record Quest(Item icon, String name, String task, String reward) {}

    private static final Quest[] EXAMPLES = {
            new Quest(Items.IRON_ORE, "Iron for the Forge", "Bring 32 raw iron to the guild", "8 emeralds"),
            new Quest(Items.ZOMBIE_HEAD, "Clear the Old Mine", "Slay 10 zombies in the dark", "12 emeralds"),
            new Quest(Items.OAK_LOG, "Timber Supply", "Bring 64 logs of any wood", "5 emeralds"),
            new Quest(Items.COMPASS, "The Lost Caravan", "Find the caravan that never arrived", "20 emeralds"),
    };

    private final int guildMasterId;

    public GuildQuestsScreen(int guildMasterId) {
        this.guildMasterId = guildMasterId;
        preferredWidth = 380;
        preferredHeight = 260;
    }

    @Override
    protected String title() {
        return "Guild Quests";
    }

    @Override
    protected String subtitle() {
        return "Work for the Guild Master";
    }

    @Override
    protected Color4I accent() {
        return Ui.BLUE;
    }

    @Override
    protected Icon headerIcon() {
        return ItemIcon.getItemIcon(Items.WRITABLE_BOOK);
    }

    @Override
    protected String footer() {
        return "Esc - back to the Guild Master";
    }

    @Override
    protected void buildContent(Panel p, int cw) {
        p.add(new Widgets.SectionHeader(p, cw, "Dungeons"));
        p.add(new Tile(p, cw, "The Minotaur's Labyrinth", "Slay the guardian of the Labyrinth (up to "
                + com.divinis.dungeon.DungeonService.MAX_PARTY + " players, 30 min)", ItemIcon.getItemIcon(Items.ENDER_EYE))
                .accent(Ui.RED)
                .chip("Open portal", Ui.RED, () -> {
                    com.divinis.platform.net.PacketDistributor.sendToServer(new com.divinis.net.DungeonOpenPayload(guildMasterId));
                    closeGui(false);
                })
                .tip("The Guild Master opens a portal next to him for 30 seconds.",
                        "Whoever walks in joins the party; the guardian grows stronger for every player.",
                        "Reward: loot for everyone and a place in the ranking."));
        p.add(new Widgets.InfoBox(p, cw, "Example quests: none of them can be taken yet.", Ui.ORANGE, Icons.INFO));
        p.add(new Widgets.SectionHeader(p, cw, "Available quests"));
        for (Quest q : EXAMPLES) {
            p.add(new Tile(p, cw, q.name(), q.task(), ItemIcon.getItemIcon(q.icon()))
                    .accent(Ui.BLUE)
                    .badge(q.reward(), Ui.GOLD)
                    .tip(q.name(), q.task(), "Reward: " + q.reward(), "Coming soon"));
        }
    }
}
