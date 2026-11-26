package com.divinis.city;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Block states the city is drawn with, in the canvas' own directions (the canvas turns them). */
final class Bk {
    private Bk() {}

    static BlockState st(Block b) {
        return b.defaultBlockState();
    }

    /** Stairs whose high side is towards `facing`; top: upside down. */
    static BlockState stairs(Block b, Direction facing, boolean top) {
        return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    static BlockState slab(Block b, boolean top) {
        return b.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    static BlockState log(Block b, Direction.Axis axis) {
        return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    /** An open trapdoor lying flat against the block on the side opposite to `facing` (a shutter facing out). */
    static BlockState shutter(Block b, Direction facing) {
        return b.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.OPEN, true);
    }

    static BlockState trapdoor(Block b, Direction facing, boolean top) {
        return b.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    static BlockState lantern(boolean hanging) {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging);
    }

    static BlockState ladder(Direction facing) {
        return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
    }

    static BlockState leaves(Block b) {
        return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    static BlockState banner(Block wallBanner, Direction facing) {
        return wallBanner.defaultBlockState().setValue(WallBannerBlock.FACING, facing);
    }

    static BlockState campfire() {
        return Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false);
    }

    /** A door, both halves, facing `facing` (the side it opens away from). */
    static void door(Canvas c, int x, int y, int z, Block door, Direction facing, boolean right) {
        BlockState s = door.defaultBlockState().setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HINGE, right ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
        c.set(x, y, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        c.set(x, y + 1, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** A bed with its head towards `facing` from (x, z). */
    static void bed(Canvas c, int x, int y, int z, Block bed, Direction facing) {
        BlockState s = bed.defaultBlockState().setValue(BedBlock.FACING, facing);
        c.set(x, y, z, s.setValue(BedBlock.PART, BedPart.FOOT));
        c.set(x + facing.getStepX(), y, z + facing.getStepZ(), s.setValue(BedBlock.PART, BedPart.HEAD));
    }

    /** A lantern on a post: two stone brick walls and the lantern. */
    static void lampPost(Canvas c, int x, int z) {
        c.set(x, 0, z, Blocks.STONE_BRICK_WALL);
        c.set(x, 1, z, Blocks.STONE_BRICK_WALL);
        c.set(x, 2, z, Blocks.SPRUCE_FENCE);
        c.set(x, 3, z, lantern(false));
    }

    /** Stone bricks, a little worn. */
    static BlockState stone(RandomSource r) {
        float f = r.nextFloat();
        return st(f < 0.14F ? Blocks.MOSSY_STONE_BRICKS : f < 0.24F ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
    }

    static BlockState cobble(RandomSource r) {
        float f = r.nextFloat();
        return st(f < 0.2F ? Blocks.MOSSY_COBBLESTONE : f < 0.3F ? Blocks.ANDESITE : Blocks.COBBLESTONE);
    }

    /** The woods of the houses: planks, log, stripped log, stairs, slab, door, trapdoor, fence. */
    enum Wood {
        OAK(Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG, Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.OAK_DOOR, Blocks.OAK_TRAPDOOR, Blocks.OAK_FENCE),
        SPRUCE(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_DOOR, Blocks.SPRUCE_TRAPDOOR, Blocks.SPRUCE_FENCE),
        DARK_OAK(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_TRAPDOOR, Blocks.DARK_OAK_FENCE);

        final Block planks, log, stripped, stairs, slab, door, trapdoor, fence;

        Wood(Block planks, Block log, Block stripped, Block stairs, Block slab, Block door, Block trapdoor, Block fence) {
            this.planks = planks;
            this.log = log;
            this.stripped = stripped;
            this.stairs = stairs;
            this.slab = slab;
            this.door = door;
            this.trapdoor = trapdoor;
            this.fence = fence;
        }
    }

    /** The roofs: full block, stairs, slab. */
    enum Roof {
        SLATE(Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB),
        CLAY(Blocks.BRICKS, Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB),
        DARK_WOOD(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB),
        SPRUCE_WOOD(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB),
        MUD(Blocks.MUD_BRICKS, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB);

        final Block full, stairs, slab;

        Roof(Block full, Block stairs, Block slab) {
            this.full = full;
            this.stairs = stairs;
            this.slab = slab;
        }
    }

    /** The plaster between the beams. */
    static final Block[] PLASTER = {Blocks.CALCITE, Blocks.WHITE_TERRACOTTA, Blocks.SMOOTH_SANDSTONE, Blocks.MUSHROOM_STEM, Blocks.CALCITE};
}
