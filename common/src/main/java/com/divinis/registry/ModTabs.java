package com.divinis.registry;

import com.divinis.Divinis;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import com.divinis.platform.bus.IEventBus;
import com.divinis.platform.registry.DeferredRegister;

/** One creative tab with every item of the mod. */
public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Divinis.MODID);

    static {
        TABS.register("divinis", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .title(Component.translatable("itemGroup.divinis"))
                .icon(() -> ModBlocks.ITEMS.getEntries().stream().filter(e -> e.getId().getPath().equals("blacksteel_longsword"))
                        .findFirst().map(e -> new ItemStack(e.get())).orElse(ItemStack.EMPTY))
                .displayItems((params, output) -> ModBlocks.ITEMS.getEntries().forEach(e -> output.accept(e.get())))
                .build());
    }

    private ModTabs() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
