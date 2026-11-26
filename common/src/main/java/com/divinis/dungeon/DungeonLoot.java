package com.divinis.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What the reward chests of the dungeons hold (each player rolls his own):
 * - boss:  the guardian's chest (one emerald for now);
 * - vault: a locked vault of the Labyrinth - bronze age riches: bronze, copper and tin, now and then a bronze tool
 *          or bucket, Labyrinth crystals;
 * - cache: a small chest in some rooms - a few ingots, food, arrows.
 */
public final class DungeonLoot {
    private DungeonLoot() {}

    public static final String BOSS = "boss", VAULT = "vault", CACHE = "cache";

    private static Optional<Item> tagged(String tag) {
        TagKey<Item> key = TagKey.create(Registries.ITEM, ResourceLocation.parse(tag));
        return BuiltInRegistries.ITEM.getTag(key).flatMap(set -> set.stream().findFirst()).map(h -> h.value());
    }

    private static void add(List<ItemStack> out, Optional<Item> item, int count) {
        item.ifPresent(i -> {
            if (count > 0) out.add(new ItemStack(i, count));
        });
    }

    private static int between(RandomSource r, int min, int max) {
        return min + r.nextInt(max - min + 1);
    }

    public static List<ItemStack> roll(String kind, RandomSource r) {
        List<ItemStack> out = new ArrayList<>();
        Optional<Item> bronze = tagged("c:ingots/bronze");
        switch (kind) {
            case VAULT -> {
                add(out, bronze.or(() -> Optional.of(Items.COPPER_INGOT)), between(r, 4, 9));
                add(out, Optional.of(Items.COPPER_INGOT), between(r, 3, 8));
                add(out, tagged("c:ingots/tin"), between(r, 2, 5));
                com.divinis.Divinis.hooks().vaultTools(r, out); // tools of the world's own age
                add(out, Optional.of(DungeonRegistry.LABYRINTH_CRYSTAL.get()), between(r, 1, 3));
                add(out, Optional.of(Items.EMERALD), between(r, 1, 2));
            }
            case CACHE -> {
                if (r.nextBoolean()) add(out, bronze.or(() -> Optional.of(Items.COPPER_INGOT)), between(r, 1, 3));
                else add(out, Optional.of(Items.COPPER_INGOT), between(r, 2, 5));
                add(out, Optional.of(r.nextBoolean() ? Items.BREAD : Items.COOKED_MUTTON), between(r, 2, 4));
                if (r.nextFloat() < 0.4F) add(out, Optional.of(Items.ARROW), between(r, 4, 10));
                if (r.nextFloat() < 0.3F) add(out, Optional.of(DungeonRegistry.LABYRINTH_CRYSTAL.get()), 1);
            }
            default -> out.add(new ItemStack(Items.EMERALD));
        }
        return out;
    }
}
