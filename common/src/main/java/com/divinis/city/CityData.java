package com.divinis.city;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The starting city, kept with the overworld: where it stands (the centre of its square, at street level), how far
 * its building has got (the city is built step by step and goes on after a restart), and the places it keeps for
 * the NPCs of its buildings.
 */
public final class CityData extends SavedData {
    private static final String NAME = "divinis_city";

    public enum State { NONE, BUILDING, BUILT }

    /** A place for an NPC: what the building is (its role), where the NPC stands, which way it looks (horizontal index). */
    public record Spot(String role, BlockPos pos, int facing) {}

    State state = State.NONE;
    @Nullable BlockPos origin;
    long seed;
    int step;
    final List<Spot> spots = new ArrayList<>();

    public static CityData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CityData::new, CityData::load, null), NAME);
    }

    public State state() {
        return state;
    }

    @Nullable
    public BlockPos origin() {
        return origin;
    }

    public List<Spot> spots() {
        return List.copyOf(spots);
    }

    /** The NPC place of a building role ("blacksmith"), if the city has one. */
    @Nullable
    public Spot spot(String role) {
        for (Spot s : spots) if (s.role().equals(role)) return s;
        return null;
    }

    private static CityData load(CompoundTag tag, HolderLookup.Provider registries) {
        CityData d = new CityData();
        try {
            d.state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            d.state = State.NONE;
        }
        if (tag.contains("origin")) d.origin = BlockPos.of(tag.getLong("origin"));
        d.seed = tag.getLong("seed");
        d.step = tag.getInt("step");
        for (Tag t : tag.getList("spots", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) t;
            d.spots.add(new Spot(s.getString("role"), BlockPos.of(s.getLong("pos")), s.getInt("facing")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("state", state.name());
        if (origin != null) tag.putLong("origin", origin.asLong());
        tag.putLong("seed", seed);
        tag.putInt("step", step);
        ListTag list = new ListTag();
        for (Spot s : spots) {
            CompoundTag t = new CompoundTag();
            t.putString("role", s.role());
            t.putLong("pos", s.pos().asLong());
            t.putInt("facing", s.facing());
            list.add(t);
        }
        tag.put("spots", list);
        return tag;
    }
}
