package com.divinis.dungeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The Labyrinth: a maze of 3 wide corridors between 10 high walls, open to the sky, on a 24 x 24 grid (4 blocks per
 * cell, 97 blocks across) south of the Minotaur's hall (ArenaLayout). Rooms are set into the maze:
 * - START (3 x 3 cells): where the party arrives, on the south edge;
 * - PUZZLE (4 x 4): one per lock; its puzzle (LabyrinthPuzzles) gives the key to that lock;
 * - VAULT (2 x 2): one or two, with a single door locked with the bronze / silver key, reward chests inside;
 * - ARENA (3 x 3, up to eight): monsters; whoever walks in is shut in with them until they are all dead (then a
 *   supply chest).
 * The maze is dug like a classic labyrinth (a randomised depth-first walk from the start, which makes long winding
 * corridors with few branches; rooms are single stops on the walk, entered by their doors), then a few extra
 * passages and the rooms' other doors are opened, so there is more than one way. The cell (GATE_X, 0) on the north edge leads to the gate of the Minotaur's hall, opened with
 * the Minotaur Key. Positions are relative to the run's origin (the middle of the hall's floor).
 */
final class Labyrinth {
    static final int N = 24, G = 4, WALL_H = 10;
    /** the maze's north-west corner (a wall post), relative to the origin; its north edge touches the hall */
    static final int X0 = -50, Z0 = ArenaLayout.HALF;
    static final int GATE_X = 12;

    enum Kind { START, PUZZLE, VAULT, ARENA }

    enum Phase { IDLE, FIGHT, PAUSED, CLEARED }

    /** A room: a rectangle of cells with doors (cell inside the room + the side the door is on). */
    static final class Room {
        final Kind kind;
        final int cx, cz, w, h;
        final List<int[]> doors = new ArrayList<>(); // {cell x, cell z, direction ordinal}
        KeyType vaultKey;
        // arena
        Phase phase = Phase.IDLE;
        final List<UUID> mobs = new ArrayList<>();
        int emptyChecks;

        Room(Kind kind, int cx, int cz, int w, int h) {
            this.kind = kind;
            this.cx = cx;
            this.cz = cz;
            this.w = w;
            this.h = h;
        }

        boolean contains(int x, int z) {
            return x >= cx && x < cx + w && z >= cz && z < cz + h;
        }

        /** Inner size in blocks. */
        int sizeX() {
            return w * G - 1;
        }

        int sizeZ() {
            return h * G - 1;
        }
    }

    /** A locked door: the blocks that fill its opening, the keyhole among them. */
    static final class Door {
        final KeyType key;
        final List<BlockPos> blocks;
        final BlockPos lock;
        boolean open;

        Door(KeyType key, List<BlockPos> blocks, BlockPos lock) {
            this.key = key;
            this.blocks = blocks;
            this.lock = lock;
        }
    }

    final BlockPos origin;
    final int runId;
    final Room[][] roomAt = new Room[N][N];
    final List<Room> rooms = new ArrayList<>();
    final boolean[][] openE = new boolean[N][N]; // passage between (x, z) and (x + 1, z)
    final boolean[][] openS = new boolean[N][N]; // passage between (x, z) and (x, z + 1)
    final List<Door> doors = new ArrayList<>();
    final List<LabyrinthPuzzles.Puzzle> puzzles = new ArrayList<>();
    Room start;

    private Labyrinth(BlockPos origin, int runId) {
        this.origin = origin;
        this.runId = runId;
    }

    // ------------------------------------------------------------------ geometry

    /** A block of a cell: dx, dz 0..2 inside it (more reach into the next cells of a room), y from the floor. */
    BlockPos cellBlock(int cx, int cz, int dx, int y, int dz) {
        return origin.offset(X0 + cx * G + 1 + dx, y, Z0 + cz * G + 1 + dz);
    }

    /** A block inside a room: lx, lz from its north-west inner corner, y from the floor. */
    BlockPos interior(Room r, int lx, int y, int lz) {
        return cellBlock(r.cx, r.cz, lx, y, lz);
    }

    Vec3 center(Room r, double y) {
        BlockPos p = interior(r, 0, 0, 0);
        return new Vec3(p.getX() + r.sizeX() / 2.0, p.getY() + y, p.getZ() + r.sizeZ() / 2.0);
    }

    Vec3 spawn() {
        return center(start, 1);
    }

    static BoundingBox boundsAt(BlockPos origin) {
        return new BoundingBox(origin.getX() + X0, origin.getY() - 1, origin.getZ() - ArenaLayout.HALF,
                origin.getX() + X0 + N * G, origin.getY() + ArenaLayout.HEIGHT, origin.getZ() + Z0 + N * G);
    }

    static boolean inside(int x, int z) {
        return x >= 0 && x < N && z >= 0 && z < N;
    }

    boolean open(int x, int z, Direction d) {
        return switch (d) {
            case EAST -> x < N - 1 && openE[x][z];
            case WEST -> x > 0 && openE[x - 1][z];
            case SOUTH -> z < N - 1 && openS[x][z];
            case NORTH -> z > 0 && openS[x][z - 1];
            default -> false;
        };
    }

    void setOpen(int x, int z, Direction d, boolean value) {
        switch (d) {
            case EAST -> openE[x][z] = value;
            case WEST -> openE[x - 1][z] = value;
            case SOUTH -> openS[x][z] = value;
            case NORTH -> openS[x][z - 1] = value;
            default -> {}
        }
    }

    int degree(int x, int z) {
        int n = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) if (open(x, z, d)) n++;
        return n;
    }

    /** The wall blocks between cell (x, z) and its neighbour on side d: 3 wide, the full height of the wall. */
    List<BlockPos> opening(int x, int z, Direction d) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            for (int y = 1; y <= WALL_H; y++) {
                out.add(switch (d) {
                    case NORTH -> origin.offset(X0 + x * G + i, y, Z0 + z * G);
                    case SOUTH -> origin.offset(X0 + x * G + i, y, Z0 + (z + 1) * G);
                    case WEST -> origin.offset(X0 + x * G, y, Z0 + z * G + i);
                    default -> origin.offset(X0 + (x + 1) * G, y, Z0 + z * G + i);
                });
            }
        }
        return out;
    }

    /** The room a position is strictly inside (not on its walls), or null. */
    @Nullable
    Room roomAt(BlockPos pos) {
        int rx = pos.getX() - origin.getX() - X0, rz = pos.getZ() - origin.getZ() - Z0;
        if (rx <= 0 || rz <= 0) return null;
        int x = rx / G, z = rz / G;
        if (!inside(x, z)) return null;
        Room r = roomAt[x][z];
        if (r == null) return null;
        int lx = rx - r.cx * G, lz = rz - r.cz * G;
        return lx >= 1 && lx <= r.sizeX() && lz >= 1 && lz <= r.sizeZ() ? r : null;
    }

    // ------------------------------------------------------------------ generation

    private boolean free(int cx, int cz, int w, int h, Set<Long> reserved) {
        if (cx < 0 || cz < 0 || cx + w > N || cz + h > N) return false;
        for (int x = cx - 1; x <= cx + w; x++) {
            for (int z = cz - 1; z <= cz + h; z++) {
                if (inside(x, z) && roomAt[x][z] != null) return false;
            }
        }
        for (int x = cx; x < cx + w; x++) for (int z = cz; z < cz + h; z++) if (reserved.contains(key(x, z))) return false;
        return true;
    }

    private static long key(int x, int z) {
        return (long) x << 32 | (z & 0xffffffffL);
    }

    private Room place(Kind kind, int cx, int cz, int w, int h) {
        Room r = new Room(kind, cx, cz, w, h);
        for (int x = cx; x < cx + w; x++) for (int z = cz; z < cz + h; z++) roomAt[x][z] = r;
        for (int x = cx; x < cx + w; x++) {
            for (int z = cz; z < cz + h; z++) {
                if (x < cx + w - 1) openE[x][z] = true;
                if (z < cz + h - 1) openS[x][z] = true;
            }
        }
        rooms.add(r);
        return r;
    }

    @Nullable
    private Room tryPlace(Kind kind, int size, Set<Long> reserved, RandomSource r) {
        for (int tries = 0; tries < 300; tries++) {
            int cx = r.nextInt(N - size + 1), cz = r.nextInt(N - size + 1);
            if (free(cx, cz, size, size, reserved)) return place(kind, cx, cz, size, size);
        }
        return null;
    }

    /** The sides of a room a door can be on (a corridor cell outside, not another room). */
    private List<Direction> doorSides(Room room) {
        List<Direction> out = new ArrayList<>();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int[] c = doorCell(room, d, 0);
            int ox = c[0] + d.getStepX(), oz = c[1] + d.getStepZ();
            if (inside(ox, oz) && roomAt[ox][oz] == null) out.add(d);
        }
        return out;
    }

    /** The cell of the room on side d where a door goes: the middle of that side (shifted by nudge). */
    private static int[] doorCell(Room room, Direction d, int nudge) {
        int mx = room.cx + (room.w - 1) / 2 + (room.w % 2 == 0 ? nudge : 0);
        int mz = room.cz + (room.h - 1) / 2 + (room.h % 2 == 0 ? nudge : 0);
        return switch (d) {
            case NORTH -> new int[] {mx, room.cz};
            case SOUTH -> new int[] {mx, room.cz + room.h - 1};
            case WEST -> new int[] {room.cx, mz};
            default -> new int[] {room.cx + room.w - 1, mz};
        };
    }

    /** Marks a cell seen - all of a room's cells, if it is one. */
    private void markSeen(boolean[] seen, int x, int z) {
        Room room = roomAt[x][z];
        if (room == null) {
            seen[x * N + z] = true;
            return;
        }
        for (int i = room.cx; i < room.cx + room.w; i++) for (int j = room.cz; j < room.cz + room.h; j++) seen[i * N + j] = true;
    }

    static Labyrinth generate(BlockPos origin, int runId, RandomSource r) {
        Labyrinth lab = new Labyrinth(origin, runId);
        Set<Long> reserved = new HashSet<>();
        reserved.add(key(GATE_X, 0));
        reserved.add(key(GATE_X, 1));
        lab.start = lab.place(Kind.START, GATE_X - 1, N - 3, 3, 3);

        List<Room> puzzleRooms = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Room p = lab.tryPlace(Kind.PUZZLE, 4, reserved, r);
            if (p != null) puzzleRooms.add(p);
        }
        int vaultCount = Math.min(2, Math.max(0, puzzleRooms.size() - 1));
        List<Room> vaults = new ArrayList<>();
        for (int i = 0; i < vaultCount; i++) {
            Room v = lab.tryPlace(Kind.VAULT, 2, reserved, r);
            if (v != null) vaults.add(v);
        }
        for (int i = 0; i < 8; i++) lab.tryPlace(Kind.ARENA, 3, reserved, r);

        // doors
        for (Room room : lab.rooms) {
            List<Direction> sides = lab.doorSides(room);
            Collections.shuffle(sides, new java.util.Random(r.nextLong()));
            int want = switch (room.kind) {
                case START -> sides.size();
                case VAULT -> 1;
                case PUZZLE -> Math.min(2, sides.size() - 1);
                case ARENA -> 2 + r.nextInt(2);
            };
            for (int i = 0; i < Math.min(want, sides.size()); i++) {
                Direction d = sides.get(i);
                int[] c = doorCell(room, d, r.nextInt(2));
                room.doors.add(new int[] {c[0], c[1], d.ordinal()});
            }
            if (room.doors.isEmpty() && !sides.isEmpty()) {
                int[] c = doorCell(room, sides.get(0), 0);
                room.doors.add(new int[] {c[0], c[1], sides.get(0).ordinal()});
            }
        }

        // the maze: a randomised depth-first walk over the cells (a room counts as one stop, reached by its doors)
        java.util.Map<Long, int[]> doorAt = new java.util.HashMap<>(); // corridor cell + side -> the room door there
        for (Room room : lab.rooms) {
            for (int[] d : room.doors) {
                Direction dir = Direction.values()[d[2]];
                doorAt.put(key(d[0] + dir.getStepX(), d[1] + dir.getStepZ()) * 4 + dir.getOpposite().get2DDataValue(), d);
            }
        }
        boolean[] seen = new boolean[N * N];
        java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();
        stack.push(new int[] {lab.start.cx, lab.start.cz});
        lab.markSeen(seen, lab.start.cx, lab.start.cz);
        while (!stack.isEmpty()) {
            int[] at = stack.peek();
            List<int[]> ways = new ArrayList<>(); // {from x, from z, dir, to x, to z}
            Room here = lab.roomAt[at[0]][at[1]];
            if (here != null) {
                for (int[] d : here.doors) {
                    Direction dir = Direction.values()[d[2]];
                    int tx = d[0] + dir.getStepX(), tz = d[1] + dir.getStepZ();
                    if (!seen[tx * N + tz]) ways.add(new int[] {d[0], d[1], d[2], tx, tz});
                }
            } else {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    int tx = at[0] + dir.getStepX(), tz = at[1] + dir.getStepZ();
                    if (!inside(tx, tz) || seen[tx * N + tz]) continue;
                    Room there = lab.roomAt[tx][tz];
                    if (there != null && !doorAt.containsKey(key(at[0], at[1]) * 4 + dir.get2DDataValue())) continue;
                    ways.add(new int[] {at[0], at[1], dir.ordinal(), tx, tz});
                }
            }
            if (ways.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] w = ways.get(r.nextInt(ways.size()));
            lab.setOpen(w[0], w[1], Direction.values()[w[2]], true);
            lab.markSeen(seen, w[3], w[4]);
            Room next = lab.roomAt[w[3]][w[4]];
            stack.push(next != null ? new int[] {next.cx, next.cz} : new int[] {w[3], w[4]});
        }
        // more than one way: every room's doors (but a vault's one), and now and then a wall knocked through
        for (Room room : lab.rooms) {
            if (room.kind == Kind.VAULT) continue;
            for (int[] d : room.doors) lab.setOpen(d[0], d[1], Direction.values()[d[2]], true);
        }
        for (int x = 0; x < N; x++) {
            for (int z = 0; z < N; z++) {
                for (Direction d : new Direction[] {Direction.EAST, Direction.SOUTH}) {
                    int nx = x + d.getStepX(), nz = z + d.getStepZ();
                    if (!inside(nx, nz) || lab.roomAt[x][z] != null || lab.roomAt[nx][nz] != null || lab.open(x, z, d)) continue;
                    if (r.nextFloat() < 0.05F) lab.setOpen(x, z, d, true);
                }
            }
        }

        // keys: the Minotaur Key always, one per vault; a puzzle for each
        List<KeyType> keys = new ArrayList<>();
        for (int i = 0; i < vaults.size(); i++) {
            Room v = vaults.get(i);
            v.vaultKey = i == 0 ? KeyType.BRONZE : KeyType.SILVER;
            keys.add(v.vaultKey);
            int[] door = v.doors.get(0);
            List<BlockPos> blocks = lab.opening(door[0], door[1], Direction.values()[door[2]]);
            lab.doors.add(new Door(v.vaultKey, blocks, blocks.get(WALL_H + 1))); // middle column, second from the floor
        }
        Collections.shuffle(keys, new java.util.Random(r.nextLong()));
        keys.add(0, KeyType.GOLD);
        List<BlockPos> gate = lab.opening(GATE_X, 0, Direction.NORTH);
        lab.doors.add(new Door(KeyType.GOLD, gate, gate.get(WALL_H + 1)));

        List<LabyrinthPuzzles.Kind> kinds = new ArrayList<>(List.of(LabyrinthPuzzles.Kind.values()));
        Collections.shuffle(kinds, new java.util.Random(r.nextLong()));
        for (int i = 0; i < puzzleRooms.size() && i < keys.size(); i++) {
            Room p = puzzleRooms.get(i);
            List<Direction> walls = new ArrayList<>();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                boolean door = false;
                for (int[] dd : p.doors) if (dd[2] == d.ordinal()) door = true;
                if (!door) walls.add(d);
            }
            Direction front = walls.get(r.nextInt(walls.size()));
            lab.puzzles.add(LabyrinthPuzzles.create(kinds.get(i % kinds.size()), lab, p, front, keys.get(i), r));
        }
        return lab;
    }

    // ------------------------------------------------------------------ the run

    @SuppressWarnings("unchecked")
    private static final EntityType<? extends Mob>[] MOBS = new EntityType[] {
            EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SKELETON, EntityType.SPIDER,
            EntityType.HUSK, EntityType.STRAY};

    /** Every few ticks while the party is inside: arenas shut and open, puzzle machines are read. */
    void tick(ServerLevel level, List<ServerPlayer> players, int partySize) {
        for (Room room : rooms) if (room.kind == Kind.ARENA) arenaTick(level, room, players, partySize);
        for (LabyrinthPuzzles.Puzzle puzzle : puzzles) {
            if (!puzzle.solved && puzzle.tick(level)) solved(level, puzzle);
        }
    }

    private List<List<BlockPos>> doorBlocks(Room room) {
        List<List<BlockPos>> out = new ArrayList<>();
        for (int[] d : room.doors) {
            Direction dir = Direction.values()[d[2]];
            if (open(d[0], d[1], dir)) out.add(opening(d[0], d[1], dir));
        }
        return out;
    }

    private void arenaTick(ServerLevel level, Room room, List<ServerPlayer> players, int partySize) {
        if (room.phase == Phase.CLEARED) return;
        boolean someoneIn = false, inDoorway = false;
        for (ServerPlayer p : players) {
            if (roomAt(p.blockPosition()) == room) someoneIn = true;
            for (List<BlockPos> door : doorBlocks(room)) {
                for (BlockPos b : door) if (b.getX() == p.getBlockX() && b.getZ() == p.getBlockZ()) inDoorway = true;
            }
        }
        if (room.phase == Phase.FIGHT) {
            room.mobs.removeIf(id -> !(level.getEntity(id) instanceof Entity e) || !e.isAlive());
            if (room.mobs.isEmpty()) {
                room.phase = Phase.CLEARED;
                seal(level, room, false);
                Vec3 c = center(room, 1);
                LabyrinthBuilder.chest(level, this, BlockPos.containing(c).offset(0, 0, 2), Direction.SOUTH, DungeonLoot.CACHE);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.8F);
                DungeonService.tell(runId, Component.literal("The arena is cleared - its gates open.")
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
                return;
            }
            // everybody left (or fell): open up again, the monsters wait for the next ones
            room.emptyChecks = someoneIn ? 0 : room.emptyChecks + 1;
            if (room.emptyChecks >= 8) {
                room.phase = Phase.PAUSED;
                seal(level, room, false);
            }
            return;
        }
        if (!someoneIn || inDoorway) return;
        seal(level, room, true);
        room.emptyChecks = 0;
        if (room.phase == Phase.IDLE) spawnMobs(level, room, partySize);
        room.phase = Phase.FIGHT;
    }

    private void seal(ServerLevel level, Room room, boolean shut) {
        for (List<BlockPos> door : doorBlocks(room)) {
            if (shut) LabyrinthBuilder.bars(level, door, null, null);
            else for (BlockPos p : door) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        Vec3 c = center(room, 1);
        level.playSound(null, c.x, c.y, c.z, shut ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.IRON_DOOR_OPEN,
                SoundSource.BLOCKS, 2.0F, 0.6F);
    }

    private void spawnMobs(ServerLevel level, Room room, int partySize) {
        RandomSource r = level.getRandom();
        int count = 3 + r.nextInt(3) + (partySize - 1);
        for (int i = 0; i < count; i++) {
            BlockPos at = null;
            for (int tries = 0; tries < 20 && at == null; tries++) {
                BlockPos p = interior(room, 1 + r.nextInt(room.sizeX() - 2), 1, 1 + r.nextInt(room.sizeZ() - 2));
                if (level.isEmptyBlock(p) && level.isEmptyBlock(p.above()) && !level.getBlockState(p.below()).isAir()) at = p;
            }
            if (at == null) continue;
            Mob mob = MOBS[r.nextInt(MOBS.length)].create(level);
            if (mob == null) continue;
            mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.nextFloat() * 360, 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null);
            // the Labyrinth is open to the sky: a helmet keeps the undead from burning
            if (!(mob instanceof Spider) && !(mob instanceof Husk)) {
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
                mob.setDropChance(EquipmentSlot.HEAD, 0);
            }
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
            DungeonScaling.apply(mob, partySize);
            room.mobs.add(mob.getUUID());
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 8, 0.3, 0.5, 0.3, 0.01);
        }
    }

    /** A player used a block: a puzzle part? Returns true if a puzzle took it. */
    boolean click(ServerLevel level, ServerPlayer player, BlockPos pos) {
        for (LabyrinthPuzzles.Puzzle puzzle : puzzles) {
            if (puzzle.solved || !puzzle.owns(pos)) continue;
            if (puzzle.click(level, player, pos)) solved(level, puzzle);
            return true;
        }
        return false;
    }

    @Nullable
    Door doorWithLock(BlockPos pos) {
        for (Door d : doors) if (!d.open && d.lock.equals(pos)) return d;
        return null;
    }

    void openDoor(ServerLevel level, Door door) {
        door.open = true;
        for (BlockPos p : door.blocks) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        Vec3 c = door.lock.getCenter();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.5F, 0.6F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.5F, 0.8F);
        level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 30, 0.8, 1.0, 0.8, 0.02);
    }

    private void solved(ServerLevel level, LabyrinthPuzzles.Puzzle puzzle) {
        puzzle.solved = true;
        Vec3 at = center(puzzle.room, 1.2);
        ItemEntity key = new ItemEntity(level, at.x, at.y, at.z, keyStack(puzzle.key, runId), 0, 0.25, 0);
        key.setGlowingTag(true);
        level.addFreshEntity(key);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y + 0.5, at.z, 40, 0.6, 0.6, 0.6, 0.3);
        DungeonService.tell(runId, Component.literal("The " + puzzle.title() + " gives way - a " + puzzle.key.title
                + " appears!").withStyle(puzzle.key.color));
    }

    static ItemStack keyStack(KeyType type, int runId) {
        ItemStack stack = new ItemStack(DungeonRegistry.keyItem(type));
        CompoundTag tag = new CompoundTag();
        tag.putInt(DungeonService.RUN_TAG, runId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** Removes everything a run's dungeon (maze and hall) was made of. */
    static void clear(ServerLevel level, BlockPos origin) {
        BoundingBox b = boundsAt(origin);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    p.set(x, y, z);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }
}
