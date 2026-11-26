package com.divinis.dungeon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The puzzle rooms of the Labyrinth. The server keeps each puzzle's state itself (levers it reads, clicks it counts),
 * the blocks only show it.
 * - WATER: a water board behind glass, as in the dungeons of Hypixel Skyblock. Water pours from the top onto three
 *   closed shelves and can only get through where a coloured block is: four levers, each on a block of its colour,
 *   slide the blocks of that colour out of the back wall (closing the hole) or back into it (opening the hole). The
 *   water runs down into five cups: fill the green-marked ones, keep the red ones dry; a light comes on over every
 *   cup that is right. The blue lever in front of the others lets all the water out to start it again. The board is
 *   made so that exactly one setting of the levers does it (checked with a model of Minecraft's water).
 * - VALVES: four Create valve handles and four dials. A turn of a valve moves its dial one step, and the dial to its
 *   right one step back. Set every dial to its golden mark.
 * - CASINGS: a 4 x 4 wall of casings. Touching one flips it and its four neighbours between andesite and brass.
 *   Make the whole wall brass.
 * Every puzzle is built in its room against a wall without a door ("front"); local coordinates: a runs along that
 * wall (0..14), b away from it into the room (0..14), y up from the floor.
 */
final class LabyrinthPuzzles {
    private LabyrinthPuzzles() {}

    enum Kind { WATER, VALVES, CASINGS }

    static Puzzle create(Kind kind, Labyrinth lab, Labyrinth.Room room, Direction front, KeyType key, RandomSource r) {
        return switch (kind) {
            case WATER -> new WaterBoard(lab, room, front, key);
            case VALVES -> new Valves(lab, room, front, key);
            case CASINGS -> new Casings(lab, room, front, key);
        };
    }

    abstract static class Puzzle {
        final Labyrinth lab;
        final Labyrinth.Room room;
        final Direction front;
        final KeyType key;
        final int size;
        boolean solved;
        protected final Set<BlockPos> parts = new HashSet<>();
        protected long lastClick;

        Puzzle(Labyrinth lab, Labyrinth.Room room, Direction front, KeyType key) {
            this.lab = lab;
            this.room = room;
            this.front = front;
            this.key = key;
            this.size = room.sizeX();
        }

        abstract String title();

        abstract void build(ServerLevel level, RandomSource r);

        /** Called every few ticks; true when the puzzle has just been solved. */
        boolean tick(ServerLevel level) {
            return false;
        }

        boolean owns(BlockPos pos) {
            return parts.contains(pos);
        }

        /** A player used one of the puzzle's blocks; true when the puzzle has just been solved. */
        boolean click(ServerLevel level, ServerPlayer player, BlockPos pos) {
            return false;
        }

        /** Too soon after the last click (a held button repeats; a turning valve takes a moment). */
        protected boolean debounce(ServerLevel level, int ticks) {
            long now = level.getGameTime();
            if (now - lastClick < ticks) return true;
            lastClick = now;
            return false;
        }

        // ---- local frame
        BlockPos at(int a, int y, int b) {
            int m = size - 1, lx, lz;
            switch (front) {
                case NORTH -> { lx = a; lz = b; }
                case SOUTH -> { lx = m - a; lz = m - b; }
                case WEST -> { lx = b; lz = m - a; }
                default -> { lx = m - b; lz = a; }
            }
            return lab.interior(room, lx, y, lz);
        }

        /** Away from the front wall, into the room. */
        Direction into() {
            return front.getOpposite();
        }

        static void set(ServerLevel level, BlockPos pos, BlockState state) {
            if (level.getBlockState(pos) != state) level.setBlock(pos, state, Block.UPDATE_ALL);
        }

        void sign(ServerLevel level, BlockPos pos, String... lines) {
            LabyrinthBuilder.sign(level, pos, into(), lines);
        }
    }

    // ------------------------------------------------------------------ water board

    static final class WaterBoard extends Puzzle {
        static final int COLS = 13, ROWS = 10, SOURCE = 6, LEVERS = 4;
        static final int[] CUPS = {1, 3, 6, 9, 11};
        static final int[] SHELF_ROWS = {7, 5, 3};
        private static final Block[] WOOL = {Blocks.ORANGE_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.MAGENTA_WOOL, Blocks.YELLOW_WOOL};
        private static final int[] LEVER_A = {3, 6, 9, 12};
        /** the reset lever, in front of the others */
        private static final int RESET_A = 7, RESET_B = 6;
        private boolean resetLever;

        private boolean[][] solid;                 // the board with every lever off
        private final List<List<int[]>> gates = new ArrayList<>(); // per lever: the cells it flips
        private boolean[] target;                  // per cup: must hold water
        private int solution;                      // the lever setting that does it (bit per lever)
        private int levers;
        private int goodChecks;
        private int heldSolution;

        WaterBoard(Labyrinth lab, Labyrinth.Room room, Direction front, KeyType key) {
            super(lab, room, front, key);
        }

        @Override
        String title() {
            return "water board";
        }

        // board cell (column c, row r) -> world: a = c + 1, y = r + 1, in the slot at b = 1
        private BlockPos cell(int c, int r) {
            return at(c + 1, r + 1, 1);
        }

        /** The back wall block behind a board cell, where a gate block hides when it is pulled in. */
        private BlockPos wallBehind(int c, int r) {
            return at(c + 1, r + 1, 0);
        }

        /** Slides a block from one place to the next with the pistons' animation, leaving `behind` where it was. */
        private static void slide(ServerLevel level, BlockPos from, BlockPos to, BlockState moved, BlockState behind) {
            Direction dir = Direction.getNearest(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
            level.setBlock(from, behind, Block.UPDATE_ALL);
            BlockState moving = Blocks.MOVING_PISTON.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.piston.MovingPistonBlock.FACING, dir);
            level.setBlock(to, moving, 68);
            level.setBlockEntity(net.minecraft.world.level.block.piston.MovingPistonBlock.newMovingBlockEntity(to, moving, moved, dir, true, false));
        }

        // ---- a model of Minecraft water in the one block thick board (as FlowingFluid spreads it)
        private static boolean[] simulate(boolean[][] solid) {
            int[][] amount = new int[COLS][ROWS];
            for (int[] col : amount) java.util.Arrays.fill(col, -1);
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            amount[SOURCE][ROWS - 1] = 9; // the source
            queue.add(new int[] {SOURCE, ROWS - 1});
            while (!queue.isEmpty()) {
                int[] q = queue.poll();
                int c = q[0], r = q[1], a = amount[c][r];
                boolean source = a == 9;
                if (r > 0 && free(solid, c, r - 1)) {
                    if (8 > amount[c][r - 1]) {
                        amount[c][r - 1] = 8; // falling water
                        queue.add(new int[] {c, r - 1});
                    }
                    if (!source) continue;
                } else if (r > 0 && amount[c][r - 1] >= 0 && !source) {
                    continue; // resting on water: it does not spread
                }
                int spread = a >= 8 ? 7 : a - 1;
                if (spread <= 0) continue;
                int best = 1000;
                List<Integer> ways = new ArrayList<>();
                for (int d = -1; d <= 1; d += 2) {
                    if (!free(solid, c + d, r)) continue;
                    int dist = 1000;
                    for (int k = 1; k <= 5; k++) {
                        int x = c + d * k;
                        if (!free(solid, x, r)) break;
                        if (r > 0 && free(solid, x, r - 1)) {
                            dist = k - 1;
                            break;
                        }
                    }
                    if (dist < best) {
                        best = dist;
                        ways.clear();
                    }
                    if (dist == best) ways.add(c + d);
                }
                for (int n : ways) {
                    if (spread > amount[n][r]) {
                        amount[n][r] = spread;
                        queue.add(new int[] {n, r});
                    }
                }
            }
            boolean[] wet = new boolean[CUPS.length];
            for (int i = 0; i < CUPS.length; i++) wet[i] = amount[CUPS[i]][0] >= 0;
            return wet;
        }

        private static boolean free(boolean[][] solid, int c, int r) {
            return c >= 0 && c < COLS && r >= 0 && r < ROWS && !solid[c][r];
        }

        private boolean[][] withLevers(int setting) {
            boolean[][] s = new boolean[COLS][];
            for (int c = 0; c < COLS; c++) s[c] = solid[c].clone();
            for (int l = 0; l < LEVERS; l++) {
                if ((setting >> l & 1) == 0) continue;
                for (int[] g : gates.get(l)) s[g[0]][g[1]] = !s[g[0]][g[1]];
            }
            return s;
        }

        /** A random board with exactly one lever setting (not all off) giving some wet and some dry cups. */
        private void design(RandomSource r) {
            for (int attempt = 0; attempt < 500; attempt++) {
                solid = new boolean[COLS][ROWS];
                for (int c = 0; c < COLS; c++) {
                    boolean cup = false;
                    for (int cc : CUPS) cup |= cc == c;
                    solid[c][0] = !cup;
                }
                solid[SOURCE - 1][ROWS - 1] = solid[SOURCE + 1][ROWS - 1] = true;
                for (int row : SHELF_ROWS) for (int c = 0; c < COLS; c++) solid[c][row] = true;
                gates.clear();
                Set<Long> used = new HashSet<>();
                for (int l = 0; l < LEVERS; l++) {
                    List<int[]> g = new ArrayList<>();
                    for (int i = 0, n = 1 + r.nextInt(2); i < n; i++) {
                        int c, row;
                        do {
                            c = r.nextInt(COLS);
                            row = SHELF_ROWS[r.nextInt(SHELF_ROWS.length)];
                        } while (!used.add((long) c << 8 | row));
                        g.add(new int[] {c, row});
                        solid[c][row] = r.nextBoolean(); // with its lever off: out (closed) or in the wall (open)
                    }
                    gates.add(g);
                }
                java.util.Map<String, List<Integer>> outcomes = new java.util.HashMap<>();
                java.util.Map<String, boolean[]> wets = new java.util.HashMap<>();
                for (int s = 0; s < 1 << LEVERS; s++) {
                    boolean[] wet = simulate(withLevers(s));
                    String k = java.util.Arrays.toString(wet);
                    outcomes.computeIfAbsent(k, x -> new ArrayList<>()).add(s);
                    wets.put(k, wet);
                }
                List<String> good = new ArrayList<>();
                for (var e : outcomes.entrySet()) {
                    int s = e.getValue().get(0);
                    int wetCount = 0;
                    for (boolean w : wets.get(e.getKey())) if (w) wetCount++;
                    if (e.getValue().size() == 1 && Integer.bitCount(s) >= 2 && wetCount > 0 && wetCount < CUPS.length) good.add(e.getKey());
                }
                if (good.isEmpty()) continue;
                java.util.Collections.sort(good);
                String pick = good.get(r.nextInt(good.size()));
                target = wets.get(pick);
                solution = outcomes.get(pick).get(0);
                return;
            }
            // practically never: a board where any one lever wins
            target = simulate(withLevers(1));
            solution = 1;
        }

        @Override
        void build(ServerLevel level, RandomSource r) {
            design(r);
            BlockState back = Palette.of("create:cut_andesite_bricks", Blocks.STONE_BRICKS);
            BlockState shelf = Palette.of("create:polished_cut_andesite", Blocks.POLISHED_ANDESITE);
            BlockState glass = Palette.of("create:framed_glass", Blocks.GLASS);
            // back wall, side caps and the glass front, around a one block slot
            for (int a = 0; a <= COLS + 1; a++) {
                for (int y = 1; y <= ROWS + 1; y++) {
                    set(level, at(a, y, 0), back);
                    boolean edge = a == 0 || a == COLS + 1;
                    set(level, at(a, y, 1), edge ? back : Blocks.AIR.defaultBlockState());
                    set(level, at(a, y, 2), edge ? back : glass);
                }
            }
            for (int c = 0; c < COLS; c++) for (int row = 0; row < ROWS; row++) if (solid[c][row]) set(level, cell(c, row), shelf);
            for (int l = 0; l < LEVERS; l++) {
                for (int[] g : gates.get(l)) {
                    // out in the slot, or pulled back into the wall behind it
                    boolean out = solid[g[0]][g[1]];
                    set(level, cell(g[0], g[1]), out ? WOOL[l].defaultBlockState() : Blocks.AIR.defaultBlockState());
                    set(level, wallBehind(g[0], g[1]), out ? back : WOOL[l].defaultBlockState());
                }
                // a block of the lever's colour with the lever on top
                set(level, at(LEVER_A[l] - 1, 1, 4), WOOL[l].defaultBlockState());
                BlockPos lever = at(LEVER_A[l] - 1, 2, 4);
                set(level, lever, Palette.with(Palette.with(Blocks.LEVER.defaultBlockState(), "face", "floor"), "facing", into()));
                parts.add(lever);
            }
            // the cups' marks on the floor in front of the glass: green must fill, red must stay dry
            for (int i = 0; i < CUPS.length; i++) {
                set(level, at(CUPS[i] + 1, 0, 3), (target[i] ? Blocks.LIME_CONCRETE : Blocks.RED_CONCRETE).defaultBlockState());
            }
            set(level, cell(SOURCE, ROWS - 1), Blocks.WATER.defaultBlockState());
            sign(level, at(1, 2, 3), "Fill the green", "cups, keep the", "red ones dry.", "Levers move blocks");
            set(level, at(RESET_A, 1, RESET_B), Blocks.BLUE_CONCRETE.defaultBlockState());
            BlockPos reset = at(RESET_A, 2, RESET_B);
            set(level, reset, Palette.with(Palette.with(Blocks.LEVER.defaultBlockState(), "face", "floor"), "facing", into()));
            parts.add(reset);
            sign(level, at(RESET_A, 1, RESET_B + 1), "Blue lever:", "let the water", "out and start", "it again");
        }

        /** Lets all the water out of the board; the source at the top starts it flowing again. */
        private void drain(ServerLevel level) {
            for (int c = 0; c < COLS; c++) {
                for (int row = 0; row < ROWS; row++) {
                    if (c == SOURCE && row == ROWS - 1) continue;
                    BlockPos p = cell(c, row);
                    if (level.getFluidState(p).is(FluidTags.WATER) && level.getBlockState(p).is(Blocks.WATER)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
            set(level, cell(SOURCE, ROWS - 1), Blocks.WATER.defaultBlockState());
            level.playSound(null, cell(SOURCE, 0), SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 0.8F);
        }

        private boolean wet(ServerLevel level, int cup) {
            return level.getFluidState(cell(CUPS[cup], 0)).is(FluidTags.WATER);
        }

        @Override
        boolean tick(ServerLevel level) {
            int now = 0;
            for (int l = 0; l < LEVERS; l++) {
                BlockState lever = level.getBlockState(at(LEVER_A[l] - 1, 2, 4));
                if (lever.hasProperty(BlockStateProperties.POWERED) && lever.getValue(BlockStateProperties.POWERED)) now |= 1 << l;
            }
            BlockState resetState = level.getBlockState(at(RESET_A, 2, RESET_B));
            boolean reset = resetState.hasProperty(BlockStateProperties.POWERED) && resetState.getValue(BlockStateProperties.POWERED);
            if (reset != resetLever) {
                resetLever = reset;
                drain(level);
                goodChecks = 0;
                return false;
            }
            if (now != levers) {
                int changed = now ^ levers;
                levers = now;
                goodChecks = 0;
                heldSolution = 0;
                BlockState back = Palette.of("create:cut_andesite_bricks", Blocks.STONE_BRICKS);
                for (int l = 0; l < LEVERS; l++) {
                    if ((changed >> l & 1) == 0) continue;
                    boolean on = (now >> l & 1) == 1;
                    for (int[] g : gates.get(l)) {
                        boolean out = solid[g[0]][g[1]] != on;
                        BlockPos slot = cell(g[0], g[1]), wall = wallBehind(g[0], g[1]);
                        if (out) slide(level, wall, slot, WOOL[l].defaultBlockState(), back);
                        else slide(level, slot, wall, WOOL[l].defaultBlockState(), Blocks.AIR.defaultBlockState());
                        level.playSound(null, slot, out ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT,
                                SoundSource.BLOCKS, 0.5F, 1.1F);
                    }
                }
                return false;
            }
            // the water decides (a light over every cup that is right); the right levers held for a moment win too,
            // should the water ever run differently from the model
            boolean all = true;
            for (int i = 0; i < CUPS.length; i++) {
                boolean right = wet(level, i) == target[i];
                all &= right;
                set(level, at(CUPS[i] + 1, 1, 3), right ? Blocks.VERDANT_FROGLIGHT.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            goodChecks = all ? goodChecks + 1 : 0;
            heldSolution = levers == solution ? heldSolution + 1 : 0;
            return goodChecks >= 3 || heldSolution >= 12;
        }
    }

    // ------------------------------------------------------------------ valves

    static final class Valves extends Puzzle {
        private static final int[] VALVE_A = {2, 5, 8, 11};
        private final int[] dial = new int[4];
        private final int[] target = new int[4];

        Valves(Labyrinth lab, Labyrinth.Room room, Direction front, KeyType key) {
            super(lab, room, front, key);
        }

        @Override
        String title() {
            return "valve wall";
        }

        @Override
        void build(ServerLevel level, RandomSource r) {
            do {
                for (int i = 0; i < 4; i++) target[i] = r.nextInt(4);
            } while (target[0] == 0 && target[1] == 0 && target[2] == 0 && target[3] == 0);
            BlockState casing = Palette.andesiteCasing();
            for (int a = 1; a <= 12; a++) for (int y = 1; y <= 6; y++) set(level, at(a, y, 0), casing);
            BlockState valve = Palette.exists("create:copper_valve_handle")
                    ? Palette.with(Palette.of("create:copper_valve_handle", Blocks.LEVER), "facing", into())
                    : Palette.with(Palette.with(Blocks.LEVER.defaultBlockState(), "face", "wall"), "facing", into());
            for (int i = 0; i < 4; i++) {
                BlockPos p = at(VALVE_A[i], 2, 1);
                set(level, p, valve);
                parts.add(p);
                set(level, at(VALVE_A[i], 4, 0), Blocks.GILDED_BLACKSTONE.defaultBlockState());
            }
            sign(level, at(3, 3, 1), "Set every dial", "to its gold mark.", "A valve also turns", "the next dial back");
            draw(level);
        }

        /** The four marks of dial i, clockwise from the top. */
        private BlockPos mark(int i, int k) {
            int a = VALVE_A[i];
            return switch (k) {
                case 0 -> at(a, 5, 0);
                case 1 -> at(a + 1, 4, 0);
                case 2 -> at(a, 3, 0);
                default -> at(a - 1, 4, 0);
            };
        }

        private void draw(ServerLevel level) {
            for (int i = 0; i < 4; i++) {
                for (int k = 0; k < 4; k++) {
                    boolean here = dial[i] == k, goal = target[i] == k;
                    BlockState s = here && goal ? Blocks.VERDANT_FROGLIGHT.defaultBlockState()
                            : here ? Blocks.SEA_LANTERN.defaultBlockState()
                            : goal ? Blocks.GOLD_BLOCK.defaultBlockState()
                            : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                    set(level, mark(i, k), s);
                }
            }
        }

        @Override
        boolean click(ServerLevel level, ServerPlayer player, BlockPos pos) {
            if (debounce(level, 6)) return false;
            for (int i = 0; i < 4; i++) {
                if (!at(VALVE_A[i], 2, 1).equals(pos)) continue;
                int step = player.isShiftKeyDown() ? -1 : 1;
                dial[i] = Math.floorMod(dial[i] + step, 4);
                if (i < 3) dial[i + 1] = Math.floorMod(dial[i + 1] - step, 4);
                level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 0.8F + 0.1F * dial[i]);
                draw(level);
                for (int k = 0; k < 4; k++) if (dial[k] != target[k]) return false;
                return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ casings

    static final class Casings extends Puzzle {
        private static final int GRID = 4, A0 = 5, Y0 = 2;
        private final boolean[][] brass = new boolean[GRID][GRID];

        Casings(Labyrinth lab, Labyrinth.Room room, Direction front, KeyType key) {
            super(lab, room, front, key);
        }

        @Override
        String title() {
            return "casing wall";
        }

        private void press(int col, int row) {
            int[][] around = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : around) {
                int c = col + d[0], w = row + d[1];
                if (c >= 0 && c < GRID && w >= 0 && w < GRID) brass[c][w] = !brass[c][w];
            }
        }

        private boolean done() {
            for (boolean[] col : brass) for (boolean b : col) if (!b) return false;
            return true;
        }

        @Override
        void build(ServerLevel level, RandomSource r) {
            for (boolean[] col : brass) java.util.Arrays.fill(col, true);
            // scramble from the solution, so there always is one
            do {
                for (int i = 0; i < 5 + r.nextInt(4); i++) press(r.nextInt(GRID), r.nextInt(GRID));
            } while (done());
            BlockState frame = Palette.copperCasing();
            for (int a = 1; a <= 13; a++) for (int y = 1; y <= 7; y++) set(level, at(a, y, 0), frame);
            for (int c = 0; c < GRID; c++) for (int w = 0; w < GRID; w++) parts.add(at(A0 + c, Y0 + w, 0));
            sign(level, at(3, 3, 1), "Make the whole", "wall brass.", "A touch flips a", "plate and its sides");
            draw(level);
        }

        private void draw(ServerLevel level) {
            for (int c = 0; c < GRID; c++) {
                for (int w = 0; w < GRID; w++) {
                    set(level, at(A0 + c, Y0 + w, 0), brass[c][w] ? Palette.brassCasing() : Palette.andesiteCasing());
                }
            }
        }

        @Override
        boolean click(ServerLevel level, ServerPlayer player, BlockPos pos) {
            if (debounce(level, 4)) return false;
            for (int c = 0; c < GRID; c++) {
                for (int w = 0; w < GRID; w++) {
                    if (!at(A0 + c, Y0 + w, 0).equals(pos)) continue;
                    press(c, w);
                    level.playSound(null, pos, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
                    draw(level);
                    return done();
                }
            }
            return false;
        }
    }
}
