package com.divinis.platform.entity;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * The parts of the multipart creatures in each level, by id: the level's searches (getEntities, getEntity) look
 * here too (mixin.LevelPartsMixin), as vanilla does for the ender dragon's parts.
 */
public final class PartRegistry {
    private static final Map<Level, Int2ObjectOpenHashMap<PartEntity<?>>> PARTS = new WeakHashMap<>();

    private PartRegistry() {}

    /** The creature entered a level (add) or left it (remove): its parts with it. */
    public static synchronized void update(Entity parent, boolean add) {
        if (!(parent instanceof MultipartEntity m)) return;
        Int2ObjectOpenHashMap<PartEntity<?>> map = PARTS.computeIfAbsent(parent.level(), l -> new Int2ObjectOpenHashMap<>());
        for (PartEntity<?> p : m.getDivinisParts()) {
            if (p == null) continue;
            if (add) map.put(p.getId(), p);
            else map.remove(p.getId());
        }
    }

    @Nullable
    public static synchronized Entity get(Level level, int id) {
        Int2ObjectOpenHashMap<PartEntity<?>> map = PARTS.get(level);
        return map == null ? null : map.get(id);
    }

    public static synchronized Collection<PartEntity<?>> all(Level level) {
        Int2ObjectOpenHashMap<PartEntity<?>> map = PARTS.get(level);
        return map == null || map.isEmpty() ? Collections.emptyList() : java.util.List.copyOf(map.values());
    }
}
