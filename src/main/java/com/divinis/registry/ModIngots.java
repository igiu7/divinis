package com.divinis.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Ingots of the rare metals (3D models from Blender: tools/models/build_ingots.py). Just materials for now; they join
 * ModBlocks' item register (and so the Divinis tab).
 */
public final class ModIngots {
    private ModIngots() {}

    public static final DeferredItem<Item> BLACKSTEEL_INGOT = ingot("blacksteel_ingot", Rarity.UNCOMMON);
    public static final DeferredItem<Item> MOONSILVER_INGOT = ingot("moonsilver_ingot", Rarity.UNCOMMON);
    public static final DeferredItem<Item> CRIMSONITE_INGOT = ingot("crimsonite_ingot", Rarity.UNCOMMON);
    public static final DeferredItem<Item> MITHRIL_INGOT = ingot("mithril_ingot", Rarity.RARE);
    public static final DeferredItem<Item> ETHERITE_INGOT = ingot("etherite_ingot", Rarity.RARE);
    public static final DeferredItem<Item> ADAMANTITE_INGOT = ingot("adamantite_ingot", Rarity.RARE);
    public static final DeferredItem<Item> DRAGONSTEEL_INGOT = ingot("dragonsteel_ingot", Rarity.EPIC);
    public static final DeferredItem<Item> DIVINITE_INGOT = ingot("divinite_ingot", Rarity.EPIC);
    public static final DeferredItem<Item> VALORIUM_INGOT = ingot("valorium_ingot", Rarity.EPIC);
    public static final DeferredItem<Item> VOIDSTEEL_INGOT = ingot("voidsteel_ingot", Rarity.EPIC);

    /** The hot ingots, one of each metal ("<metal>_hot_ingot"): all with one model (tools/build_hot_ingot.py). */
    public static final java.util.Map<String, DeferredItem<Item>> HOT_INGOTS = new java.util.LinkedHashMap<>();

    static {
        for (String metal : java.util.List.of("blacksteel", "moonsilver", "crimsonite", "mithril", "etherite", "adamantite",
                "dragonsteel", "divinite", "valorium", "voidsteel")) {
            HOT_INGOTS.put(metal, ingot(metal + "_hot_ingot", Rarity.UNCOMMON));
        }
    }

    private static DeferredItem<Item> ingot(String name, Rarity rarity) {
        return ModBlocks.ITEMS.register(name, () -> new Item(new Item.Properties().rarity(rarity)));
    }

    /** Loads the class, so the ingots are in the register before it is registered. */
    public static void init() {}
}
