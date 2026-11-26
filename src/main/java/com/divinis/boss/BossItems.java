package com.divinis.boss;

import com.divinis.registry.ModBlocks;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;

/** Items dropped by bosses. Registered into ModBlocks' item register. */
public final class BossItems {
    private BossItems() {}

    /**
     * The Minotaur's great double axe (1% drop): a bronze axe in its numbers, but like its owner it gets half of
     * every blow through a raised shield (ShieldPierce), and right-click makes its holder charge (MinotaurAxeItem).
     */
    public static final DeferredItem<MinotaurAxeItem> MINOTAUR_AXE = ModBlocks.ITEMS.register("minotaur_axe",
            () -> new MinotaurAxeItem(MinotaurAxeItem.TIER, new Item.Properties().rarity(Rarity.EPIC)
                    .attributes(AxeItem.createAttributes(MinotaurAxeItem.TIER, 6.5F, -3.15F))));

    /** Items that are only shown by bosses' effects (item displays), kept out of the creative tab. */
    private static final net.neoforged.neoforge.registries.DeferredRegister.Items VISUALS =
            net.neoforged.neoforge.registries.DeferredRegister.createItems(com.divinis.Divinis.MODID);

    /** The Golem's crystal spikes, bursting out of the ground (Golem's crystal spikes attack). */
    public static final DeferredItem<Item> GOLEM_CRYSTAL_SPIKE = VISUALS.register("golem_crystal_spike", () -> new Item(new Item.Properties()));

    /** The Evil Brother's gear saws and harpoon (his gear saws and harpoon attacks; models from tools/build_steam_fx.py). */
    public static final DeferredItem<Item> EVIL_BROTHER_GEAR = VISUALS.register("evil_brother_gear", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> EVIL_BROTHER_HARPOON = VISUALS.register("evil_brother_harpoon", () -> new Item(new Item.Properties()));

    /** Registers the display-only items; the others join ModBlocks' item register when this class loads. */
    public static void init(net.neoforged.bus.api.IEventBus modBus) {
        VISUALS.register(modBus);
    }
}
