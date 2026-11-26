package com.divinis.dungeon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What the dungeons keep across restarts: the ranking of the fastest clears and the slots of the dungeon world
 * that were in use (a restart ends every run; those slots are cleaned when the server starts again).
 */
public final class DungeonData extends SavedData {
    private static final String NAME = "divinis_dungeons";
    private static final int KEEP = 50;

    /** One clear: the party's names, how long it took (ticks), how many went in, when (epoch millis). */
    public record Clear(String names, int ticks, int players, long when) {}

    private final List<Clear> ranking = new ArrayList<>();
    private final Set<Integer> usedSlots = new HashSet<>();

    public static DungeonData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DungeonData::new, DungeonData::load, null), NAME);
    }

    public List<Clear> top(int n) {
        return ranking.subList(0, Math.min(n, ranking.size()));
    }

    /** Adds a clear; returns its place in the ranking (1 = fastest), 0 if it is not kept. */
    public int addClear(Clear clear) {
        ranking.add(clear);
        ranking.sort(Comparator.comparingInt(Clear::ticks));
        int place = ranking.indexOf(clear) + 1;
        while (ranking.size() > KEEP) ranking.remove(ranking.size() - 1);
        setDirty();
        return place <= KEEP ? place : 0;
    }

    public Set<Integer> usedSlots() {
        return Set.copyOf(usedSlots);
    }

    public void markSlot(int slot, boolean used) {
        if (used ? usedSlots.add(slot) : usedSlots.remove(slot)) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Clear c : ranking) {
            CompoundTag t = new CompoundTag();
            t.putString("names", c.names());
            t.putInt("ticks", c.ticks());
            t.putInt("players", c.players());
            t.putLong("when", c.when());
            list.add(t);
        }
        tag.put("ranking", list);
        tag.put("slots", new IntArrayTag(usedSlots.stream().mapToInt(Integer::intValue).toArray()));
        return tag;
    }

    public static DungeonData load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonData data = new DungeonData();
        for (Tag raw : tag.getList("ranking", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            data.ranking.add(new Clear(t.getString("names"), t.getInt("ticks"), t.getInt("players"), t.getLong("when")));
        }
        for (int s : tag.getIntArray("slots")) data.usedSlots.add(s);
        return data;
    }
}
