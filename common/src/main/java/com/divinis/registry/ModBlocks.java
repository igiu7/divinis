package com.divinis.registry;

import com.divinis.Divinis;

import com.divinis.platform.bus.IEventBus;
import com.divinis.platform.registry.DeferredRegister;

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
