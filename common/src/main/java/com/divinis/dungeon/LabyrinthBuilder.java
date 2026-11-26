package com.divinis.dungeon;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds a generated Labyrinth into the dungeon world: floor, the 10 high walls of cut andesite bricks with taller
 * pillars at the corners (no roof, only an invisible barrier lid on the walls so the sky shows), the Minotaur's hall, locked doors, the rooms and the corridor
 * decoration (torches, vines, cobwebs in dead ends, lantern posts, moss and bones). Create's motors that turn the
 * decorative cogwheels sit under the floor, out of sight.
 */
final class LabyrinthBuilder {
    private LabyrinthBuilder() {}

    private static final int BULK = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    static BlockState wall() {
        return Palette.of("create:cut_andesite_bricks", Blocks.STONE_BRICKS);
    }

    static void build(ServerLevel level, Labyrinth lab, RandomSource r) {
        shell(level, lab, r);
        ArenaLayout.build(level, lab.origin, r);
        for (Labyrinth.Door door : lab.doors) {
            bars(level, door.blocks, door.lock, DungeonRegistry.LABYRINTH_LOCK.get().defaultBlockState().setValue(LabyrinthLockBlock.KEY, door.key));
        }
        corridors(level, lab, r);
        for (Labyrinth.Room room : lab.rooms) room(level, lab, room, r);
        for (LabyrinthPuzzles.Puzzle p : lab.puzzles) p.build(level, r);
    }

    // ------------------------------------------------------------------ shell

    private static void shell(ServerLevel level, Labyrinth lab, RandomSource r) {
        BlockState brick = wall();
        BlockState small = Palette.of("create:small_andesite_bricks", Blocks.STONE_BRICKS);
        BlockState cut = Palette.of("create:cut_andesite", Blocks.ANDESITE);
        BlockState post = Palette.with(Palette.of("create:andesite_pillar", Blocks.POLISHED_ANDESITE), "axis", Direction.Axis.Y);
        BlockState floor = Palette.of("create:polished_cut_andesite", Blocks.POLISHED_ANDESITE);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos o = lab.origin;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int g = Labyrinth.G, n = Labyrinth.N;
        for (int x = 0; x <= n * g; x++) {
            for (int z = 0; z <= n * g; z++) {
                int wx = o.getX() + Labyrinth.X0 + x, wz = o.getZ() + Labyrinth.Z0 + z;
                boolean lineX = x % g == 0, lineZ = z % g == 0;
                int cx = x / g, cz = z / g;
                Labyrinth.Room room = !lineX && !lineZ && Labyrinth.inside(cx, cz) ? lab.roomAt[cx][cz] : null;
                BlockState f = room == null ? (r.nextFloat() < 0.25F ? cut : floor) : roomFloor(room, r, floor, cut);
                level.setBlock(p.set(wx, o.getY(), wz), f, BULK);
                int height = 0; // 0 = open
                if (lineX && lineZ) {
                    height = Labyrinth.WALL_H + 1;
                    Labyrinth.Room a = roomOf(lab, cx - 1, cz - 1);
                    if (a != null && a == roomOf(lab, cx, cz - 1) && a == roomOf(lab, cx - 1, cz) && a == roomOf(lab, cx, cz)) height = 0;
                } else if (lineX) {
                    if (cx == 0 || cx == n || !lab.open(cx - 1, cz, Direction.EAST)) height = Labyrinth.WALL_H;
                } else if (lineZ) {
                    if (cz == 0 || cz == n || !lab.open(cx, cz - 1, Direction.SOUTH)) height = Labyrinth.WALL_H;
                }
                for (int y = 1; y <= Labyrinth.WALL_H + 1; y++) {
                    BlockState s = y > height ? air : height > Labyrinth.WALL_H ? post
                            : r.nextFloat() < 0.12F ? small : r.nextFloat() < 0.08F ? cut : brick;
                    // an invisible lid right on top of the walls: the sky shows, but nothing climbs over a wall
                    if (y == Labyrinth.WALL_H + 1 && s == air) s = Blocks.BARRIER.defaultBlockState();
                    level.setBlock(p.set(wx, o.getY() + y, wz), s, BULK);
                }
            }
        }
    }

    @Nullable
    private static Labyrinth.Room roomOf(Labyrinth lab, int x, int z) {
        return Labyrinth.inside(x, z) ? lab.roomAt[x][z] : null;
    }

    private static BlockState roomFloor(Labyrinth.Room room, RandomSource r, BlockState floor, BlockState cut) {
        return switch (room.kind) {
            case VAULT -> Blocks.WAXED_CUT_COPPER.defaultBlockState();
            case ARENA -> r.nextFloat() < 0.3F ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : cut;
            default -> floor;
        };
    }

    /** Fills an opening with bars (joined up in a second pass), the keyhole plate at lockPos if any. */
    static void bars(ServerLevel level, List<BlockPos> blocks, @Nullable BlockPos lockPos, @Nullable BlockState lock) {
        for (int pass = 0; pass < 2; pass++) {
            for (BlockPos p : blocks) {
                BlockState s = p.equals(lockPos) ? lock : Block.updateFromNeighbourShapes(Palette.bars(), level, p);
                level.setBlock(p, s, Block.UPDATE_ALL);
            }
        }
    }

    static void chest(ServerLevel level, Labyrinth lab, BlockPos p, Direction facing, String kind) {
        level.setBlock(p, DungeonRegistry.DUNGEON_CHEST.get().defaultBlockState().setValue(ChestBlock.FACING, facing), Block.UPDATE_ALL);
        if (level.getBlockEntity(p) instanceof DungeonChestBlockEntity chest) chest.setup(lab.runId, kind);
    }

    static void sign(ServerLevel level, BlockPos pos, Direction facing, String... lines) {
        level.setBlock(pos, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            SignText text = new SignText();
            for (int i = 0; i < lines.length && i < 4; i++) text = text.setMessage(i, Component.literal(lines[i]));
            sign.setText(text.setHasGlowingText(true), true);
            sign.setWaxed(true);
        }
    }

    private static void put(ServerLevel level, BlockPos p, BlockState s) {
        if (level.getBlockState(p).isAir()) level.setBlock(p, s, Block.UPDATE_ALL);
    }

    private static void lanternPost(ServerLevel level, BlockPos p) {
        put(level, p, Blocks.ANDESITE_WALL.defaultBlockState());
        put(level, p.above(), Blocks.ANDESITE_WALL.defaultBlockState());
        put(level, p.above(2), Blocks.LANTERN.defaultBlockState());
    }

    // ------------------------------------------------------------------ corridors

    private static void corridors(ServerLevel level, Labyrinth lab, RandomSource r) {
        for (int x = 0; x < Labyrinth.N; x++) {
            for (int z = 0; z < Labyrinth.N; z++) {
                if (lab.roomAt[x][z] != null) continue;
                List<Direction> walls = new ArrayList<>();
                for (Direction d : Direction.Plane.HORIZONTAL) if (!lab.open(x, z, d) && !(x == Labyrinth.GATE_X && z == 0 && d == Direction.NORTH)) walls.add(d);
                if (walls.isEmpty()) continue;
                Direction w = walls.get(r.nextInt(walls.size()));
                // the block in the middle of the cell's side against wall w
                int mx = 1 + w.getStepX(), mz = 1 + w.getStepZ();
                float f = r.nextFloat();
                if (f < 0.12F) {
                    put(level, lab.cellBlock(x, z, mx, 3, mz), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, w.getOpposite()));
                } else if (f < 0.26F) {
                    int top = 3 + r.nextInt(6);
                    for (int y = top; y <= Labyrinth.WALL_H; y++) {
                        put(level, lab.cellBlock(x, z, mx, y, mz), Palette.with(Blocks.VINE.defaultBlockState(), w.getSerializedName(), true));
                    }
                } else if (f < 0.31F) {
                    int[][] corners = {{0, 0}, {2, 0}, {0, 2}, {2, 2}};
                    int[] k = corners[r.nextInt(4)];
                    lanternPost(level, lab.cellBlock(x, z, k[0], 1, k[1]));
                } else if (f < 0.38F) {
                    for (int i = 0; i < 3; i++) put(level, lab.cellBlock(x, z, r.nextInt(3), 1, r.nextInt(3)), Blocks.MOSS_CARPET.defaultBlockState());
                }
                if (walls.size() == 3) { // a dead end: cobwebs and old bones
                    put(level, lab.cellBlock(x, z, mx == 1 ? 0 : mx, 1, mz == 1 ? 0 : mz), Blocks.COBWEB.defaultBlockState());
                    if (r.nextBoolean()) put(level, lab.cellBlock(x, z, 1, 1, 1), Palette.with(Blocks.SKELETON_SKULL.defaultBlockState(), "rotation", r.nextInt(16)));
                    if (r.nextBoolean()) put(level, lab.cellBlock(x, z, mx == 1 ? 2 : mx, 1, mz == 1 ? 2 : mz), Blocks.BONE_BLOCK.defaultBlockState());
                }
            }
        }
    }

    // ------------------------------------------------------------------ rooms

    /** Spots along the inner walls of a room that are not in front of a door: lx, lz and the way into the room. */
    private record Spot(int lx, int lz, Direction into) {}

    private static List<Spot> spots(Labyrinth.Room room) {
        int sx = room.sizeX(), sz = room.sizeZ();
        List<Spot> out = new ArrayList<>();
        for (int i = 0; i < Math.max(sx, sz); i++) {
            if (i < sx) {
                if (!nearDoor(room, Direction.NORTH, i)) out.add(new Spot(i, 0, Direction.SOUTH));
                if (!nearDoor(room, Direction.SOUTH, i)) out.add(new Spot(i, sz - 1, Direction.NORTH));
            }
            if (i > 0 && i < sz - 1) {
                if (!nearDoor(room, Direction.WEST, i)) out.add(new Spot(0, i, Direction.EAST));
                if (!nearDoor(room, Direction.EAST, i)) out.add(new Spot(sx - 1, i, Direction.WEST));
            }
        }
        return out;
    }

    /** Is position i along side d of the room within a block of a door? */
    private static boolean nearDoor(Labyrinth.Room room, Direction d, int i) {
        for (int[] door : room.doors) {
            if (door[2] != d.ordinal()) continue;
            int cell = d.getAxis() == Direction.Axis.Z ? door[0] - room.cx : door[1] - room.cz;
            int from = cell * Labyrinth.G;
            if (i >= from - 1 && i <= from + 3) return true;
        }
        return false;
    }

    private static Spot take(List<Spot> spots, RandomSource r) {
        return spots.remove(r.nextInt(spots.size()));
    }

    private static void room(ServerLevel level, Labyrinth lab, Labyrinth.Room room, RandomSource r) {
        List<Spot> spots = spots(room);
        int sx = room.sizeX(), sz = room.sizeZ();
        switch (room.kind) {
            case START -> {
                if (spots.size() >= 2) {
                    Spot a = take(spots, r), b = take(spots, r);
                    sign(level, lab.interior(room, a.lx, 2, a.lz), a.into, "~ The Labyrinth ~", "Solve the puzzle", "rooms to win", "their keys.");
                    sign(level, lab.interior(room, b.lx, 2, b.lz), b.into, "Bronze, Silver:", "the vaults.", "Minotaur Key:", "his hall, north.");
                }
                for (int i = 0; i < 2 && !spots.isEmpty(); i++) {
                    Spot s = take(spots, r);
                    put(level, lab.interior(room, s.lx, 1, s.lz), Blocks.CAMPFIRE.defaultBlockState());
                }
                lanternPost(level, lab.interior(room, 1, 1, 1));
                lanternPost(level, lab.interior(room, sx - 2, 1, 1));
            }
            case VAULT -> {
                for (int[] k : new int[][] {{0, 0}, {sx - 1, 0}, {0, sz - 1}, {sx - 1, sz - 1}}) {
                    put(level, lab.interior(room, k[0], 1, k[1]), Blocks.GILDED_BLACKSTONE.defaultBlockState());
                    put(level, lab.interior(room, k[0], 2, k[1]), Palette.with(Palette.with(Blocks.CANDLE.defaultBlockState(), "candles", 3), "lit", true));
                }
                for (int i = 0; i < 2 && !spots.isEmpty(); i++) {
                    Spot s = take(spots, r);
                    chest(level, lab, lab.interior(room, s.lx, 1, s.lz), s.into, DungeonLoot.VAULT);
                }
            }
            case ARENA -> arena(level, lab, room, spots, r);
            case PUZZLE -> {
                lanternPost(level, lab.interior(room, 0, 1, 0));
                lanternPost(level, lab.interior(room, sx - 1, 1, 0));
                lanternPost(level, lab.interior(room, 0, 1, sz - 1));
                lanternPost(level, lab.interior(room, sx - 1, 1, sz - 1));
            }
        }
    }

    /** A fighting pit: four pillars with fire on top, bones, a Create flywheel turning in the floor. */
    private static void arena(ServerLevel level, Labyrinth lab, Labyrinth.Room room, List<Spot> spots, RandomSource r) {
        int sx = room.sizeX();
        BlockState pillar = wall();
        for (int[] k : new int[][] {{2, 2}, {sx - 3, 2}, {2, sx - 3}, {sx - 3, sx - 3}}) {
            for (int y = 1; y <= 4; y++) level.setBlock(lab.interior(room, k[0], y, k[1]), pillar, Block.UPDATE_ALL);
            level.setBlock(lab.interior(room, k[0], 5, k[1]), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (int i = 0; i < 4 && !spots.isEmpty(); i++) {
            Spot s = take(spots, r);
            put(level, lab.interior(room, s.lx, 1, s.lz), r.nextBoolean() ? Blocks.BONE_BLOCK.defaultBlockState()
                    : Palette.with(Blocks.SKELETON_SKULL.defaultBlockState(), "rotation", r.nextInt(16)));
        }
        // a cogwheel turning in the middle of the floor; its motor is hidden under it
        if (Palette.exists("create:creative_motor")) {
            BlockPos mid = lab.interior(room, sx / 2, 0, sx / 2);
            level.setBlock(mid.below(), Palette.motor(Direction.UP), Block.UPDATE_ALL);
            level.setBlock(mid, Palette.with(Palette.of("create:andesite_encased_shaft", Blocks.POLISHED_ANDESITE), "axis", Direction.Axis.Y), Block.UPDATE_ALL);
            level.setBlock(mid.above(), Palette.largeCogwheel(Direction.Axis.Y), Block.UPDATE_ALL);
        }
    }
}
