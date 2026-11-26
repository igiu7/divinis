package com.divinis.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Tools of the rare metals (ModIngots): a pickaxe, axe, shovel and hoe of each, made like the steel ones (three
 * ingots / two / one / two and sticks). Blacksteel is as good as steel (the diamond tier); each metal after it in
 * this list is a little better than the one before - more uses (+110), faster (+0.35), a little more damage (+0.25)
 * and easier to enchant (+1). They mine all that diamond mines; repaired with their ingot.
 */
public final class ModFantasyTools {
    private ModFantasyTools() {}

    /** Metal name -> its ingot, worst to best. */
    private static final Map<String, Supplier<Item>> METALS = new LinkedHashMap<>();

    static {
        METALS.put("blacksteel", ModIngots.BLACKSTEEL_INGOT::get);
        METALS.put("moonsilver", ModIngots.MOONSILVER_INGOT::get);
        METALS.put("crimsonite", ModIngots.CRIMSONITE_INGOT::get);
        METALS.put("mithril", ModIngots.MITHRIL_INGOT::get);
        METALS.put("etherite", ModIngots.ETHERITE_INGOT::get);
        METALS.put("adamantite", ModIngots.ADAMANTITE_INGOT::get);
        METALS.put("dragonsteel", ModIngots.DRAGONSTEEL_INGOT::get);
        METALS.put("divinite", ModIngots.DIVINITE_INGOT::get);
        METALS.put("valorium", ModIngots.VALORIUM_INGOT::get);
        METALS.put("voidsteel", ModIngots.VOIDSTEEL_INGOT::get);
    }

    /** Every tool, by name (e.g. "mithril_pickaxe"). */
    public static final Map<String, DeferredItem<? extends Item>> TOOLS = new LinkedHashMap<>();

    /** Each metal's tier (its weapons use it too). */
    public static final Map<String, Tier> TIERS = new LinkedHashMap<>();

    static {
        int rank = 0;
        for (Map.Entry<String, Supplier<Item>> e : METALS.entrySet()) {
            String metal = e.getKey();
            Supplier<Item> ingot = e.getValue();
            Tier tier = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1561 + rank * 110, 8.0F + rank * 0.35F, 3.0F + rank * 0.25F,
                    10 + rank, () -> Ingredient.of(ingot.get()));
            TIERS.put(metal, tier);
            Rarity rarity = rank < 3 ? Rarity.UNCOMMON : rank < 6 ? Rarity.RARE : Rarity.EPIC;
            TOOLS.put(metal + "_pickaxe", ModBlocks.ITEMS.register(metal + "_pickaxe", () -> new PickaxeItem(tier,
                    new Item.Properties().rarity(rarity).attributes(PickaxeItem.createAttributes(tier, 1.0F, -2.8F)))));
            TOOLS.put(metal + "_axe", ModBlocks.ITEMS.register(metal + "_axe", () -> new AxeItem(tier,
                    new Item.Properties().rarity(rarity).attributes(AxeItem.createAttributes(tier, 5.0F, -3.0F)))));
            TOOLS.put(metal + "_shovel", ModBlocks.ITEMS.register(metal + "_shovel", () -> new ShovelItem(tier,
                    new Item.Properties().rarity(rarity).attributes(ShovelItem.createAttributes(tier, 1.5F, -3.0F)))));
            TOOLS.put(metal + "_hoe", ModBlocks.ITEMS.register(metal + "_hoe", () -> new HoeItem(tier,
                    new Item.Properties().rarity(rarity).attributes(HoeItem.createAttributes(tier, -3.0F, 0.0F)))));
            rank++;
        }
    }

    /** Loads the class, so the tools are in the register before it is registered (after ModIngots). */
    public static void init() {}
}
