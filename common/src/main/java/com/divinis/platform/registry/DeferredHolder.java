package com.divinis.platform.registry;

import java.util.function.Supplier;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** An entry of a DeferredRegister: get() it once the registries are filled. */
public class DeferredHolder<R, T extends R> implements Supplier<T> {
    protected final RegistrySupplier<T> entry;

    DeferredHolder(RegistrySupplier<T> entry) {
        this.entry = entry;
    }

    @Override
    public T get() {
        return entry.get();
    }

    /** The same as get() (as vanilla's Holder calls it). */
    public T value() {
        return entry.get();
    }

    public ResourceLocation getId() {
        return entry.getId();
    }

    @SuppressWarnings("unchecked")
    public ResourceKey<R> getKey() {
        return (ResourceKey<R>) entry.getKey();
    }

    /** The registry's holder of the entry (for vanilla code that takes a Holder). */
    @SuppressWarnings("unchecked")
    public Holder<R> holder() {
        return (Holder<R>) (Object) entry;
    }

    public boolean isBound() {
        return entry.isPresent();
    }
}
