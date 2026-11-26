package com.divinis.platform.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import com.divinis.platform.bus.IEventBus;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Registers a mod's entries of one registry (on Architectury's DeferredRegister, so on every loader). Entries are
 * added with register(name, supplier) and the register itself with register(bus) during the mod's start-up.
 */
public class DeferredRegister<T> {
    private static final List<DeferredRegister<?>> QUEUED = new ArrayList<>();

    private final ResourceKey<? extends Registry<T>> registry;
    private final dev.architectury.registry.registries.DeferredRegister<T> inner;
    private final List<DeferredHolder<T, ? extends T>> entries = new ArrayList<>();

    protected DeferredRegister(ResourceKey<? extends Registry<T>> registry, String modid) {
        this.registry = registry;
        @SuppressWarnings("unchecked")
        ResourceKey<Registry<T>> key = (ResourceKey<Registry<T>>) registry;
        this.inner = dev.architectury.registry.registries.DeferredRegister.create(modid, key);
    }

    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> registry, String modid) {
        return new DeferredRegister<>(registry, modid);
    }

    public static Items createItems(String modid) {
        return new Items(modid);
    }

    public static Blocks createBlocks(String modid) {
        return new Blocks(modid);
    }

    protected <I extends T> RegistrySupplier<I> add(String name, Supplier<? extends I> supplier) {
        return inner.register(name, supplier::get);
    }

    public <I extends T> DeferredHolder<T, I> register(String name, Supplier<? extends I> supplier) {
        DeferredHolder<T, I> h = new DeferredHolder<>(add(name, supplier));
        entries.add(h);
        return h;
    }

    public Collection<DeferredHolder<T, ? extends T>> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /** Queues the register (the bus is the mod's, kept for the loaders that need it); see registerQueued. */
    public void register(IEventBus bus) {
        QUEUED.add(this);
    }

    /**
     * Registers the queued registers, blocks first, then items, then the rest (a block entity type needs its blocks):
     * NeoForge orders the registries itself, Fabric fills them in the order they are registered.
     */
    public static void registerQueued() {
        List<DeferredRegister<?>> order = new ArrayList<>(QUEUED);
        order.sort(java.util.Comparator.comparingInt(DeferredRegister::rank));
        for (DeferredRegister<?> r : order) r.inner.register();
        QUEUED.clear();
    }

    public static class Items extends DeferredRegister<Item> {
        Items(String modid) {
            super(Registries.ITEM, modid);
        }

        @Override
        public <I extends Item> DeferredItem<I> register(String name, Supplier<? extends I> supplier) {
            DeferredItem<I> h = new DeferredItem<>(add(name, supplier));
            ((List<DeferredHolder<Item, ? extends Item>>) (List<?>) super.entriesList()).add(h);
            return h;
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> factory) {
            return register(name, () -> factory.apply(new Item.Properties()));
        }

        public DeferredItem<Item> registerSimpleItem(String name) {
            return register(name, () -> new Item(new Item.Properties()));
        }
    }

    public static class Blocks extends DeferredRegister<Block> {
        Blocks(String modid) {
            super(Registries.BLOCK, modid);
        }

        @Override
        public <B extends Block> DeferredBlock<B> register(String name, Supplier<? extends B> supplier) {
            DeferredBlock<B> h = new DeferredBlock<>(add(name, supplier));
            ((List<DeferredHolder<Block, ? extends Block>>) (List<?>) super.entriesList()).add(h);
            return h;
        }

        public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> factory,
                                                               BlockBehaviour.Properties props) {
            return register(name, () -> factory.apply(props));
        }
    }

    /** What blocks and items may take while they are made comes first. */
    private int rank() {
        if (registry.equals(Registries.ARMOR_MATERIAL) || registry.equals(Registries.PARTICLE_TYPE) || registry.equals(Registries.SOUND_EVENT)
                || registry.equals(Registries.MOB_EFFECT)) return 0;
        if (registry.equals(Registries.BLOCK)) return 1;
        if (registry.equals(Registries.ITEM)) return 2;
        return 3;
    }

    List<DeferredHolder<T, ? extends T>> entriesList() {
        return entries;
    }
}
