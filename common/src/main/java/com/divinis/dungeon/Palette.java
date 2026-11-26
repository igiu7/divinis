package com.divinis.dungeon;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Blocks for building dungeons by id, so other mods' blocks (Create) can be used without depending on them: a
 * missing block falls back to a vanilla one. Properties are set by name and ignored when the block has none.
 */
final class Palette {
    private Palette() {}

    static BlockState of(String id, Block fallback) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElse(fallback).defaultBlockState();
    }

    static boolean exists(String id) {
        return BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(id));
    }

    static BlockState with(BlockState state, String property, Object value) {
        Property<?> p = state.getBlock().getStateDefinition().getProperty(property);
        if (p == null) return state;
        String v = value instanceof Direction d ? d.getSerializedName()
                : value instanceof Direction.Axis a ? a.getSerializedName() : String.valueOf(value).toLowerCase();
        return set(state, p, v);
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> p, String value) {
        return p.getValue(value).map(v -> state.setValue(p, v)).orElse(state);
    }

    // Create's blocks (with vanilla stand-ins)
    static BlockState shaft(Direction.Axis axis) {
        return with(of("create:shaft", Blocks.CHAIN), "axis", axis);
    }

    static BlockState cogwheel(Direction.Axis axis) {
        return with(of("create:cogwheel", Blocks.DROPPER), "axis", axis);
    }

    static BlockState largeCogwheel(Direction.Axis axis) {
        return with(of("create:large_cogwheel", Blocks.DROPPER), "axis", axis);
    }

    static BlockState gearshift(Direction.Axis axis) {
        return with(of("create:gearshift", Blocks.OBSERVER), "axis", axis);
    }

    static BlockState motor(Direction facing) {
        return with(of("create:creative_motor", Blocks.PISTON), "facing", facing);
    }

    static BlockState andesiteCasing() {
        return of("create:andesite_casing", Blocks.POLISHED_ANDESITE);
    }

    static BlockState brassCasing() {
        return of("create:brass_casing", Blocks.GOLD_BLOCK);
    }

    static BlockState copperCasing() {
        return of("create:copper_casing", Blocks.CUT_COPPER);
    }

    static BlockState bars() {
        return of("create:andesite_bars", Blocks.IRON_BARS);
    }
}
