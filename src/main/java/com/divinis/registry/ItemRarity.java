package com.divinis.registry;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.world.item.Item;

/**
 * Divinis' rarity of an item, for any item in the game: it is written in bold, in its colour, at the end of the
 * item's tooltip ("RARE"), and the item's name takes the colour (client.RarityTooltip). The colours are the fishing
 * pools' (fishing.FishingPool.Tier). An item has one only when given one here (assign); for now only the fishing rods
 * are (fishing.FishingRods).
 */
public enum ItemRarity {
    COMMON("COMMON", 0xD8D8D8),
    UNCOMMON("UNCOMMON", 0x55FF55),
    RARE("RARE", 0x55A0FF),
    EPIC("EPIC", 0xB462FF),
    LEGENDARY("LEGENDARY", 0xFFC23A),
    UNIQUE("UNIQUE", 0xFF4848);

    public final String label;
    public final int color;

    ItemRarity(String label, int color) {
        this.label = label;
        this.color = color;
    }

    private static final List<Map.Entry<Supplier<? extends Item>, ItemRarity>> ASSIGNED = new ArrayList<>();
    private static Map<Item, ItemRarity> byItem;

    /** Gives an item (registered or still to be) its rarity. */
    public static void assign(Supplier<? extends Item> item, ItemRarity rarity) {
        ASSIGNED.add(Map.entry(item, rarity));
        byItem = null;
    }

    /** The item's rarity, or null when it has none. */
    public static ItemRarity of(Item item) {
        if (byItem == null) {
            Map<Item, ItemRarity> map = new IdentityHashMap<>();
            for (var e : ASSIGNED) map.put(e.getKey().get(), e.getValue());
            byItem = map;
        }
        return byItem.get(item);
    }
}
