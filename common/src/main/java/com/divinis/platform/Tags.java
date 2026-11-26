package com.divinis.platform;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** The shared "c" tags both loaders fill (the ones Divinis reads). */
public final class Tags {
    private Tags() {}

    public static final class Blocks {
        public static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"));

        private Blocks() {}
    }

    public static final class Items {
        public static final TagKey<Item> ORES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ores"));
        public static final TagKey<Item> INGOTS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots"));

        private Items() {}
    }
}
