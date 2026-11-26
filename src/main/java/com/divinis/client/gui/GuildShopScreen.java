package com.divinis.client.gui;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Panel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** The Guild Master's shop. For now only example offers that cannot be bought yet. */
public final class GuildShopScreen extends DivinisFrame {
    private record Offer(Item item, String name, String description, int price) {}

    private static final Offer[] EXAMPLES = {
            new Offer(Items.IRON_PICKAXE, "Miner's Pickaxe", "A sturdy pick from the guild forge", 12),
            new Offer(Items.TORCH, "Torches x16", "Light for the deep tunnels", 2),
            new Offer(Items.BREAD, "Travel Rations x8", "Bread baked for long journeys", 3),
            new Offer(Items.MAP, "Map of the Region", "Drawn by the guild's scouts", 6),
            new Offer(Items.DIAMOND, "Rough Diamond", "Rare stone from the far mines", 40),
    };

    public GuildShopScreen() {
        preferredWidth = 380;
        preferredHeight = 260;
    }

    @Override
    protected String title() {
        return "Guild Shop";
    }

    @Override
    protected String subtitle() {
        return "Goods of the Guild Master";
    }

    @Override
    protected Color4I accent() {
        return Ui.GREEN;
    }

    @Override
    protected Icon headerIcon() {
        return ItemIcon.getItemIcon(Items.EMERALD);
    }

    @Override
    protected String footer() {
        return "Esc - back to the Guild Master";
    }

    @Override
    protected void buildContent(Panel p, int cw) {
        p.add(new Widgets.InfoBox(p, cw, "Example offers: the shop does not sell anything yet.", Ui.ORANGE, Icons.INFO));
        p.add(new Widgets.SectionHeader(p, cw, "Offers"));
        for (Offer o : EXAMPLES) {
            p.add(new Tile(p, cw, o.name(), o.description(), ItemIcon.getItemIcon(o.item()))
                    .accent(Ui.GREEN)
                    .badge(o.price() + " emeralds", Ui.GOLD)
                    .tip(o.name(), "Price: " + o.price() + " emeralds", "Coming soon"));
        }
    }
}
