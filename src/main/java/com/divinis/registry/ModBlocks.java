package com.divinis.registry;

import com.divinis.Divinis;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Divinis' block and item registers: every part of the mod adds its blocks and items here (they all go into its tab). */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Divinis.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Divinis.MODID);

    private ModBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
