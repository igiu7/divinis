package com.divinis.platform;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Data a mod keeps on entities (players): saved with them, made with the default when missing, and with
 * copyOnDeath carried over to a respawned player. NeoForge keeps it as an attachment, Fabric as well (its API).
 */
public final class DataKey<T> {
    public final ResourceLocation id;
    public final Supplier<T> defaults;
    public final Codec<T> codec;
    public final boolean copyOnDeath;
    /** The loader's own handle (set by Services.registerData). */
    public Object handle;

    private DataKey(ResourceLocation id, Supplier<T> defaults, Codec<T> codec, boolean copyOnDeath) {
        this.id = id;
        this.defaults = defaults;
        this.codec = codec;
        this.copyOnDeath = copyOnDeath;
    }

    public static <T> DataKey<T> create(ResourceLocation id, Supplier<T> defaults, Codec<T> codec, boolean copyOnDeath) {
        DataKey<T> key = new DataKey<>(id, defaults, codec, copyOnDeath);
        Services.registerData(key);
        return key;
    }

    public T get(Entity entity) {
        return Services.getData(entity, this);
    }

    public void set(Entity entity, T value) {
        Services.setData(entity, this, value);
    }
}
