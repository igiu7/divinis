package com.divinis.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Minotaur's hall at the north end of the Labyrinth: a round arena open to the sky, 53 blocks across, ringed by
 * a 12 high wall with battlements. Broken pillars stand in two rings, four near the middle and eight further out (a
 * charging Minotaur that runs into one is stunned), four braziers burn between them, and against the north wall a
 * dais with a throne. The gate from
 * the Labyrinth is in the south wall. The boss sleeps before the dais; the reward chest appears before the dais when
 * he is dead, the way out at the east side. Positions are relative to the middle of the floor.
 */
public final class ArenaLayout {
    private ArenaLayout() {}

    static final int HALF = 28;
    static final int HEIGHT = 13;
    private static final double FLOOR_R = 26, WALL_R = 28.3;
    /** the north edge of the dais, against the wall */
    private static final int BACK = -25;

    static final Vec3 BOSS = new Vec3(0.5, 1, -13.5);
    static final float BOSS_YAW = 0; // looking south, at the gate
    static final BlockPos CHEST = new BlockPos(0, 1, -19);
    /** the way out: on the east side, between two of the outer pillars, facing the middle */
    static final Vec3 EXIT = new Vec3(22.5, 1, 0.5);
    static final float EXIT_YAW = 90;

    static void build(ServerLevel level, BlockPos o, RandomSource r) {
        BlockState floor = Palette.of("create:polished_cut_andesite", Blocks.POLISHED_ANDESITE);
        BlockState ring = Palette.of("create:small_andesite_bricks", Blocks.STONE_BRICKS);
        BlockState center = Palette.of("create:cut_andesite", Blocks.ANDESITE);
        BlockState wall = LabyrinthBuilder.wall();
        BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d >= WALL_R + 0.5) continue;
                boolean gate = Math.abs(x) <= 1 && z >= FLOOR_R - 3;
                BlockState f = d < 3 ? center : (d >= 6 && d < 7) || (d >= 13 && d < 14) || (d >= 21 && d < 22) ? ring
                        : r.nextFloat() < 0.08F ? cracked : floor;
                level.setBlock(p.set(o.getX() + x, o.getY(), o.getZ() + z), f, flags);
                boolean isWall = d >= FLOOR_R && !gate;
                // battlements: every other stretch along the top
                boolean merlon = isWall && ((int) Math.floor(Math.atan2(z, x) * 18 / Math.PI) & 1) == 0;
                for (int y = 1; y <= HEIGHT; y++) {
                    BlockState s = isWall && (y < HEIGHT || merlon) ? (r.nextFloat() < 0.1F ? cracked : wall) : air;
                    level.setBlock(p.set(o.getX() + x, o.getY() + y, o.getZ() + z), s, flags);
                }
            }
        }

        // broken pillars: four near the middle, eight further out
        BlockState pillar = Palette.with(Palette.of("create:andesite_pillar", Blocks.POLISHED_ANDESITE), "axis", Direction.Axis.Y);
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(k < 4 ? 45 + k * 90 : 22.5 + (k - 4) * 45);
            double radius = k < 4 ? 9.5 : 17.5;
            int px = Mth.floor(Math.sin(a) * radius), pz = Mth.floor(-Math.cos(a) * radius);
            int height = 4 + r.nextInt(6);
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    int h = height - (dx + dz == 2 ? r.nextInt(3) : 0); // a broken top
                    for (int y = 1; y <= h; y++) level.setBlock(p.set(o.getX() + px + dx, o.getY() + y, o.getZ() + pz + dz), y == 1 ? wall : pillar, flags);
                }
            }
            if (r.nextFloat() < 0.4F) { // a fallen drum of it on the floor
                Direction.Axis axis = r.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
                BlockState drum = Palette.with(Palette.of("create:andesite_pillar", Blocks.POLISHED_ANDESITE), "axis", axis);
                for (int i = 0; i < 2; i++) {
                    int fx = px + 3 + (axis == Direction.Axis.X ? i : 0), fz = pz + (axis == Direction.Axis.Z ? i : 0);
                    if (Math.sqrt(fx * fx + fz * fz) < FLOOR_R - 1) level.setBlock(p.set(o.getX() + fx, o.getY() + 1, o.getZ() + fz), drum, flags);
                }
            }
        }

        // four braziers between the pillars
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(45 + k * 90);
            int bx = Mth.floor(Math.sin(a) * 22.5), bz = Mth.floor(-Math.cos(a) * 22.5);
            level.setBlock(p.set(o.getX() + bx, o.getY() + 1, o.getZ() + bz), wall, flags);
            level.setBlock(p.set(o.getX() + bx, o.getY() + 2, o.getZ() + bz), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
        }

        // the dais and the throne against the north wall
        for (int x = -4; x <= 4; x++) {
            for (int z = BACK; z <= BACK + 3; z++) level.setBlock(p.set(o.getX() + x, o.getY() + 1, o.getZ() + z), floor, flags);
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = BACK; z <= BACK + 1; z++) level.setBlock(p.set(o.getX() + x, o.getY() + 2, o.getZ() + z), floor, flags);
        }
        BlockState gilded = Blocks.GILDED_BLACKSTONE.defaultBlockState();
        for (int y = 3; y <= 5; y++) level.setBlock(p.set(o.getX(), o.getY() + y, o.getZ() + BACK), gilded, flags);
        level.setBlock(p.set(o.getX() - 1, o.getY() + 3, o.getZ() + BACK), gilded, flags);
        level.setBlock(p.set(o.getX() + 1, o.getY() + 3, o.getZ() + BACK), gilded, flags);
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 2; y <= 4; y++) level.setBlock(p.set(o.getX() + side * 4, o.getY() + y, o.getZ() + BACK), pillar, flags);
            level.setBlock(p.set(o.getX() + side * 4, o.getY() + 5, o.getZ() + BACK), Blocks.SOUL_CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
        }

        // old bones of those who came before (not where the boss, his chest or the way out are)
        for (int i = 0; i < 30; i++) {
            int x = r.nextInt(45) - 22, z = r.nextInt(41) - 20;
            BlockPos at = new BlockPos(o.getX() + x, o.getY() + 1, o.getZ() + z);
            if (!level.getBlockState(at).isAir() || Math.sqrt(x * x + z * z) > FLOOR_R - 2
                    || Math.abs(x) <= 2 && z >= -20 && z <= 2 || x >= 19 && Math.abs(z) <= 3) continue;
            level.setBlock(at, r.nextInt(3) == 0 ? Blocks.BONE_BLOCK.defaultBlockState()
                    : r.nextBoolean() ? Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, r.nextInt(16))
                    : Blocks.COBWEB.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
