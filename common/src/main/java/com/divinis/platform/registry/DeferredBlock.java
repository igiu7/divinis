package com.divinis.platform.registry;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

public class DeferredBlock<T extends Block> extends DeferredHolder<Block, T> implements ItemLike {
    DeferredBlock(RegistrySupplier<T> entry) {
        super(entry);
    }

    @Override
    public Item asItem() {
        return get().asItem();
    }
}
