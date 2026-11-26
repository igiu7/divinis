package com.divinis.city;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The plan of the starting city, as the steps that build it (each small enough for a part of a server tick). All
 * coordinates are relative to the centre of the square at street level; north is -z.
 *
 * <pre>
 *  - the walls: a square, their outside at 96 blocks from the centre, a gatehouse in the north and south sides,
 *    square towers along them, round towers at the corners, a lane inside them (87 - 92);
 *  - the square (|x|, |z| up to 20) with its fountain, stalls, benches, lamps and trees, a road round it (21 - 25);
 *  - twelve shops and halls round the square (26 - 42), their doors on the square, then a ring road (43 - 47);
 *  - from the square, avenues to both gates (x from -3 to 3) and to the east and west walls (z from -3 to 3);
 *  - between the ring road and the lane, four districts of houses on streets, with the cathedral, the park, the
 *    mage's tower, the barracks, an inn, the stables and the caravan yard among them.
 * </pre>
 * Everything random comes from the city's seed, so the plan is the same after a restart and the building goes on.
 */
final class CityPlan {
    static final int WALL = Fortifications.WALL_OUT;
    static final int EXTENT = 126;       // the land shaped round the city
    static final int FLAT = 104;         // flat up to here, then blending into the land to EXTENT
    static final int LANE = 87;
    static final int PLAZA = 20, PLAZA_ROAD = 25, SHOPS = 42, RING = 47;
    static final int BAND_IN = 48, BAND_OUT = 86;

    /** One step of the building: what it is (for progress messages) and what it does. */
    record Step(String what, Runnable run) {}

    /** The blocks of a district between its streets (district coordinates, see band()). */
    private static final int[][] BLOCKS_X = {{-86, -52}, {-48, -26}, {-22, -4}, {4, 22}, {26, 44}};
    private static final int[][] CROSS = {{-51, -49}, {-25, -23}, {23, 25}, {45, 47}};
    private static final int OUTER_N = -86, OUTER_S = -69, INNER_N = -65, INNER_S = -48, RING_N = -68, RING_S = -66;

    private CityPlan() {}

    static List<Step> steps(ServerLevel level, BlockPos origin, long seed, Consumer<CityData.Spot> spot) {
        List<Step> steps = new ArrayList<>();
        RandomSource plan = RandomSource.create(seed);
        Canvas root = new Canvas(level, origin, Rotation.NONE);

        // the land, a chunk at a time
        int cx0 = (origin.getX() - EXTENT) >> 4, cx1 = (origin.getX() + EXTENT) >> 4;
        int cz0 = (origin.getZ() - EXTENT) >> 4, cz1 = (origin.getZ() + EXTENT) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                int fx = cx, fz = cz;
                long s = plan.nextLong();
                steps.add(new Step("Shaping the land", () -> terrain(level, origin, fx, fz, RandomSource.create(s))));
            }
        }

        // the square and the shops round it
        long ps = plan.nextLong();
        steps.add(new Step("Laying the square", () -> plaza(root, RandomSource.create(ps))));
        shops(steps, root, plan, spot);

        // the four districts
        Rotation[] bands = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};
        for (int b = 0; b < 4; b++) district(steps, root.at(0, 0, 0, bands[b]), b, plan, spot);

        // the walls (all of them before the towers, which stand over their ends), towers and gates
        for (int side = 0; side < 4; side++) {
            Canvas n = root.at(0, 0, 0, bands[side]);
            boolean gate = side == 0 || side == 2;
            for (int t = -89; t <= 89; t += 24) {
                int a = t, b = Math.min(89, t + 23);
                long s = plan.nextLong();
                steps.add(new Step("Raising the walls", () -> Fortifications.wall(n, a, b, gate, RandomSource.create(s))));
            }
        }
        for (int side = 0; side < 4; side++) {
            Canvas n = root.at(0, 0, 0, bands[side]);
            boolean gate = side == 0 || side == 2;
            for (int t : gate ? new int[] {-48, 48} : new int[] {-48, 0, 48}) {
                long s = plan.nextLong();
                steps.add(new Step("Building the towers", () -> Fortifications.squareTower(n, t, RandomSource.create(s))));
            }
            long s1 = plan.nextLong();
            steps.add(new Step("Building the towers", () -> Fortifications.roundTower(n.at(WALL - 1, 0, -(WALL - 1), Rotation.COUNTERCLOCKWISE_90), RandomSource.create(s1))));
            long s2 = plan.nextLong();
            if (gate) steps.add(new Step("Building the gates", () -> Fortifications.gatehouse(n, RandomSource.create(s2))));
            long s3 = plan.nextLong();
            steps.add(new Step("Building the walls' stairs", () -> {
                RandomSource r = RandomSource.create(s3);
                Fortifications.wallStairs(n, gate ? 12 : 8, r);
                laneLamps(n);
            }));
        }
        // the roads out of the gates
        steps.add(new Step("Lighting the roads", () -> {
            roadLamps(root.at(0, 0, 0, Rotation.NONE));
            roadLamps(root.at(0, 0, 0, Rotation.CLOCKWISE_180));
        }));
        return steps;
    }

    // ------------------------------------------------------------------ the land

    private static float smooth(float t) {
        t = Mth.clamp(t, 0F, 1F);
        return t * t * (3 - 2 * t);
    }

    private static boolean soft(BlockState s) {
        return s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced();
    }

    /** One chunk of the land: flat inside, blending into the land round it, the ground of the streets laid. */
    private static void terrain(ServerLevel level, BlockPos origin, int cx, int cz, RandomSource r) {
        level.getChunk(cx, cz);
        int flags = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;
        BlockState air = Blocks.AIR.defaultBlockState();
        int y0 = origin.getY();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int ix = 0; ix < 16; ix++) {
            for (int iz = 0; iz < 16; iz++) {
                int wx = (cx << 4) + ix, wz = (cz << 4) + iz;
                int x = wx - origin.getX(), z = wz - origin.getZ();
                int c = Math.max(Math.abs(x), Math.abs(z));
                if (c > EXTENT) continue;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR, wx, wz);
                int target = y0;
                if (c > FLAT) target = Math.round(y0 + (ground - y0) * smooth((c - FLAT) / (float) (EXTENT - FLAT)));
                // clear everything above the ground
                for (int y = target; y < Math.max(top, target + 1); y++) {
                    p.set(wx, y, wz);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, air, flags);
                }
                // fill below it: the surface, earth, then stone into any hole or water underneath
                p.set(wx, target - 1, wz);
                boolean outside = c > WALL + 1;
                level.setBlock(p, outside ? road(x, z, c, r) : ground(x, z, r), flags);
                for (int y = target - 2; y >= target - 3; y--) level.setBlock(p.set(wx, y, wz), Blocks.DIRT.defaultBlockState(), flags);
                for (int y = target - 4; y > target - 30; y--) {
                    p.set(wx, y, wz);
                    if (!soft(level.getBlockState(p))) break;
                    level.setBlock(p, (target - y < 9 ? Blocks.DIRT : Blocks.STONE).defaultBlockState(), flags);
                }
                // grass and flowers in the yards
                if (!outside && plot(x, z) && r.nextFloat() < 0.12F) {
                    level.setBlock(p.set(wx, target, wz), (r.nextFloat() < 0.3F ? flower(r) : Blocks.SHORT_GRASS).defaultBlockState(), flags);
                }
            }
        }
    }

    private static Block flower(RandomSource r) {
        Block[] f = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET};
        return f[r.nextInt(f.length)];
    }

    /** Outside the walls: grass, and the roads leaving the gates. */
    private static BlockState road(int x, int z, int c, RandomSource r) {
        if (Math.abs(x) <= 3 && Math.abs(z) > WALL) {
            float f = r.nextFloat();
            return Bk.st(f < 0.5F ? Blocks.DIRT_PATH : f < 0.75F ? Blocks.GRAVEL : Blocks.COBBLESTONE);
        }
        return Bk.st(Blocks.GRASS_BLOCK);
    }

    /** The ground of the city at x, z (the block under the street level). */
    static BlockState ground(int x, int z, RandomSource r) {
        int ax = Math.abs(x), az = Math.abs(z), c = Math.max(ax, az);
        float f = r.nextFloat();
        if (c > LANE + 5) return Bk.st(Blocks.GRASS_BLOCK);
        if (c >= LANE) return Bk.st(f < 0.45F ? Blocks.COBBLESTONE : f < 0.7F ? Blocks.GRAVEL : f < 0.85F ? Blocks.ANDESITE : Blocks.COARSE_DIRT);
        if (c <= PLAZA) return plazaStone(x, z, r);
        if (ax <= 3 || az <= 3) {
            // the avenues: a stone border, cobbles and setts in the middle
            int across = ax <= 3 && az > PLAZA ? ax : az;
            if (across == 3) return Bk.st(Blocks.STONE_BRICKS);
            return Bk.st(f < 0.4F ? Blocks.POLISHED_ANDESITE : f < 0.75F ? Blocks.COBBLESTONE : f < 0.9F ? Blocks.ANDESITE : Blocks.MOSSY_COBBLESTONE);
        }
        if (c <= PLAZA_ROAD) return Bk.st(f < 0.5F ? Blocks.COBBLESTONE : f < 0.8F ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS);
        if (c <= RING) return Bk.st(f < 0.55F ? Blocks.COBBLESTONE : f < 0.8F ? Blocks.ANDESITE : Blocks.MOSSY_COBBLESTONE);
        if (street(x, z)) return Bk.st(f < 0.55F ? Blocks.DIRT_PATH : f < 0.75F ? Blocks.COARSE_DIRT : f < 0.9F ? Blocks.GRAVEL : Blocks.COBBLESTONE);
        return Bk.st(Blocks.GRASS_BLOCK);
    }

    /** The square: bands of stone bricks round the fountain, polished and plain andesite between them. */
    private static BlockState plazaStone(int x, int z, RandomSource r) {
        int c = Math.max(Math.abs(x), Math.abs(z));
        if (c % 5 == 0) return Bk.st(Blocks.STONE_BRICKS);
        if (Math.abs(x) <= 1 || Math.abs(z) <= 1) return Bk.st(Blocks.POLISHED_ANDESITE);
        float f = r.nextFloat();
        if (f < 0.06F) return Bk.st(Blocks.MOSSY_STONE_BRICKS);
        return Bk.st(((x + z) & 1) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.ANDESITE);
    }

    /** District coordinates of x, z: {district, x, z}, or null outside the districts. */
    static int[] band(int x, int z) {
        if (z >= -BAND_OUT && z <= -BAND_IN && x >= -BAND_OUT && x <= RING) return new int[] {0, x, z};
        if (x >= BAND_IN && x <= BAND_OUT && z >= -BAND_OUT && z <= RING) return new int[] {1, z, -x};
        if (z >= BAND_IN && z <= BAND_OUT && x >= -RING && x <= BAND_OUT) return new int[] {2, -x, -z};
        if (x >= -BAND_OUT && x <= -BAND_IN && z >= -RING && z <= BAND_OUT) return new int[] {3, -z, x};
        return null;
    }

    private static boolean street(int x, int z) {
        int[] b = band(x, z);
        if (b == null) return false;
        int lx = b[1], lz = b[2];
        if (Math.abs(lx) <= 3 || (lz >= RING_N && lz <= RING_S)) return true;
        for (int[] s : CROSS) if (lx >= s[0] && lx <= s[1]) return true;
        return false;
    }

    private static boolean plot(int x, int z) {
        return band(x, z) != null && !street(x, z);
    }

    // ------------------------------------------------------------------ the square

    private static void plaza(Canvas c, RandomSource r) {
        // the fountain: a round basin, an obelisk with a lantern in the middle
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > 26) continue;
                if (d2 > 16) {
                    c.set(dx, -1, dz, Blocks.STONE_BRICKS);
                    c.set(dx, 0, dz, Bk.slab(Blocks.STONE_BRICK_SLAB, false));
                } else {
                    c.set(dx, -2, dz, Blocks.STONE_BRICKS);
                    c.set(dx, -1, dz, Blocks.WATER);
                    if (d2 > 2 && r.nextFloat() < 0.12F) c.set(dx, 0, dz, Blocks.LILY_PAD);
                }
            }
        }
        c.set(0, -1, 0, Blocks.CHISELED_STONE_BRICKS);
        c.set(0, 0, 0, Blocks.POLISHED_ANDESITE);
        c.set(0, 1, 0, Blocks.POLISHED_ANDESITE);
        c.set(0, 2, 0, Blocks.CHISELED_STONE_BRICKS);
        c.set(0, 3, 0, Blocks.STONE_BRICK_WALL);
        c.set(0, 4, 0, Bk.lantern(false));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            c.set(d.getStepX(), 1, d.getStepZ(), Bk.stairs(Blocks.STONE_BRICK_STAIRS, d.getOpposite(), true));
        }
        // benches facing the fountain, lamps, trees in planters
        for (int k : new int[] {-1, 1}) {
            for (int a = 4; a <= 6; a++) {
                c.set(a * k, 0, -8, Bk.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
                c.set(a * k, 0, 8, Bk.stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
                c.set(-8, 0, a * k, Bk.stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
                c.set(8, 0, a * k, Bk.stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
            }
            for (int m : new int[] {-1, 1}) {
                Bk.lampPost(c, 10 * k, 10 * m);
                Bk.lampPost(c, 16 * k, 5 * m);
                int tx = 17 * k, tz = 17 * m;
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) c.set(tx + dx, -1, tz + dz, Blocks.GRASS_BLOCK);
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) c.set(tx + dx, 0, tz + dz, Bk.slab(Blocks.STONE_BRICK_SLAB, false));
                }
                Buildings.tree(c, tx, tz, r);
            }
        }
        // market stalls along the sides
        Block[][] cloth = {{Blocks.RED_WOOL, Blocks.WHITE_WOOL}, {Blocks.PURPLE_WOOL, Blocks.YELLOW_WOOL}, {Blocks.BLUE_WOOL, Blocks.WHITE_WOOL},
                {Blocks.GREEN_WOOL, Blocks.WHITE_WOOL}, {Blocks.ORANGE_WOOL, Blocks.BROWN_WOOL}};
        Block[] goods = {Blocks.MELON, Blocks.PUMPKIN, Blocks.HAY_BLOCK, Blocks.BARREL, Blocks.BEEHIVE, Blocks.COMPOSTER};
        int i = 0;
        for (int k : new int[] {-1, 1}) {
            for (int m : new int[] {-1, 1}) {
                stall(c.at(15 * k, 0, 11 * m), cloth[i++ % cloth.length], goods, r);
                stall(c.at(11 * m, 0, 15 * k), cloth[i++ % cloth.length], goods, r);
            }
        }
        // a notice board before the Guild Hall
        c.set(-16, 0, -19, Blocks.SPRUCE_FENCE);
        c.set(-16, 1, -19, Blocks.SPRUCE_FENCE);
        c.set(-12, 0, -19, Blocks.SPRUCE_FENCE);
        c.set(-12, 1, -19, Blocks.SPRUCE_FENCE);
        for (int x = -16; x <= -12; x++) c.set(x, 2, -19, Blocks.SPRUCE_PLANKS);
        for (int x = -15; x <= -13; x++) c.set(x, 3, -19, Bk.slab(Blocks.SPRUCE_SLAB, false));
        c.sign(-14, 2, -18, Direction.SOUTH, Blocks.SPRUCE_WALL_SIGN.defaultBlockState(), "Notices", "Work for the", "guild - ask the", "Guild Master");
    }

    /** A market stall: four posts, a striped awning, goods on the counter. */
    private static void stall(Canvas c, Block[] cloth, Block[] goods, RandomSource r) {
        for (int dx = -1; dx <= 1; dx += 2) for (int dz = -1; dz <= 1; dz += 2) {
            for (int y = 0; y <= 2; y++) c.set(dx, y, dz, Blocks.SPRUCE_FENCE);
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) c.set(dx, 3, dz, cloth[(dx + 1) % 2]);
        c.set(0, 0, 0, Blocks.BARREL);
        c.set(0, 1, 0, Buildings.potted(r));
        c.set(r.nextBoolean() ? -1 : 1, 0, 0, goods[r.nextInt(goods.length)]);
        c.set(0, 2, 0, Bk.lantern(true));
    }

    // ------------------------------------------------------------------ the shops round the square

    private static void shops(List<Step> steps, Canvas root, RandomSource plan, Consumer<CityData.Spot> spot) {
        // north row: front at z = -26 facing south; south row: front at z = 26 facing north
        String[] north = {"librarian", "guild_master", "mayor", "banker"};
        String[] south = {"innkeeper", "merchant", "baker", "tailor"};
        int[][] xs = {{-42, -25}, {-23, -6}, {6, 23}, {25, 42}};
        for (int i = 0; i < 4; i++) {
            int x0 = xs[i][0], x1 = xs[i][1];
            shop(steps, root.at(x1, 0, -PLAZA_ROAD - 1, Rotation.CLOCKWISE_180), x1 - x0 + 1, north[i], plan, spot);
            shop(steps, root.at(x0, 0, PLAZA_ROAD + 1, Rotation.NONE), x1 - x0 + 1, south[i], plan, spot);
        }
        // east row: front at x = 26 facing west; west row: front at x = -26 facing east
        shop(steps, root.at(PLAZA_ROAD + 1, 0, -5, Rotation.COUNTERCLOCKWISE_90), 20, "blacksmith", plan, spot);
        shop(steps, root.at(PLAZA_ROAD + 1, 0, 24, Rotation.COUNTERCLOCKWISE_90), 20, "armorer", plan, spot);
        shop(steps, root.at(-PLAZA_ROAD - 1, 0, -24, Rotation.CLOCKWISE_90), 20, "alchemist", plan, spot);
        shop(steps, root.at(-PLAZA_ROAD - 1, 0, 5, Rotation.CLOCKWISE_90), 20, "healer", plan, spot);
    }

    private static void shop(List<Step> steps, Canvas c, int w, String role, RandomSource plan, Consumer<CityData.Spot> spot) {
        long s = plan.nextLong();
        int floors = role.equals("guild_master") || role.equals("mayor") || role.equals("innkeeper") ? 3 : 2 + plan.nextInt(2);
        steps.add(new Step("Building the shops", () -> {
            RandomSource r = RandomSource.create(s);
            Buildings.Style style = role.equals("guild_master")
                    ? new Buildings.Style(Bk.Wood.DARK_OAK, Bk.Roof.SLATE, Blocks.CALCITE, true, false, true)
                    : Buildings.Style.random(r);
            BlockPos at = Buildings.shop(c, w, SHOPS - PLAZA_ROAD, floors, style, Wares.shop(role), r);
            spot.accept(new CityData.Spot(role, c.pos(at.getX(), at.getY(), at.getZ()), c.dir(Direction.NORTH).get2DDataValue()));
        }));
    }

    // ------------------------------------------------------------------ the districts

    /**
     * A district (b: 0 north, 1 east, 2 south, 3 west), drawn as the northern one: its blocks between the streets,
     * houses on both sides of every row facing the streets, the great buildings in their places, and lamps.
     */
    private static void district(List<Step> steps, Canvas d, int b, RandomSource plan, Consumer<CityData.Spot> spot) {
        for (int i = 0; i < BLOCKS_X.length; i++) {
            int a = BLOCKS_X[i][0], e = BLOCKS_X[i][1];
            // the great buildings take whole blocks
            if (i == 1 && b == 1) {
                long s = plan.nextLong();
                steps.add(new Step("Building the cathedral", () -> {
                    Canvas c = d.at(e, 0, INNER_S, Rotation.CLOCKWISE_180);
                    BlockPos at = Landmarks.cathedral(c, RandomSource.create(s));
                    spot.accept(new CityData.Spot("priest", c.pos(at.getX(), at.getY(), at.getZ()), c.dir(Direction.NORTH).get2DDataValue()));
                }));
                continue;
            }
            if (i == 1 && b == 3) {
                long s = plan.nextLong();
                steps.add(new Step("Planting the park", () -> Landmarks.park(d.at(a, 0, OUTER_N), e - a + 1, INNER_S - OUTER_N + 1, RandomSource.create(s))));
                continue;
            }
            row(steps, d, b, i, a, e, OUTER_N, OUTER_S, true, plan, spot);
            row(steps, d, b, i, a, e, INNER_N, INNER_S, false, plan, spot);
        }
        // lamps at the crossings of the streets
        steps.add(new Step("Lighting the streets", () -> {
            for (int[] s : CROSS) {
                for (int lz : new int[] {RING_N - 1, RING_S + 1}) {
                    if (d.isAir(s[0] - 1, 0, lz)) Bk.lampPost(d, s[0] - 1, lz);
                }
            }
            for (int lz = INNER_S; lz >= OUTER_N; lz -= 10) {
                if (lz >= RING_N && lz <= RING_S) continue;
                Bk.lampPost(d, -3, lz);
                Bk.lampPost(d, 3, lz - 5);
            }
        }));
    }

    /** A row of a block: houses facing north along its north side, facing south along its south side. */
    private static void row(List<Step> steps, Canvas d, int b, int i, int a, int e, int zn, int zs, boolean outer,
                            RandomSource plan, Consumer<CityData.Spot> spot) {
        // the great buildings of a row
        String special = null;
        if (outer && b == 0 && i == 2) special = "gate_innkeeper";
        if (outer && b == 0 && i == 3) special = "captain";
        if (outer && b == 2 && i == 3) special = "stables";
        if (outer && b == 2 && i == 2) special = "caravan";
        if (!outer && b == 3 && i == 3) special = "mage";
        if (special != null) {
            String role = special;
            long s = plan.nextLong();
            int w = e - a + 1;
            steps.add(new Step("Building the " + role.replace('_', ' '), () -> {
                RandomSource r = RandomSource.create(s);
                // facing south, towards the city's middle
                Canvas c = d.at(e, 0, zs, Rotation.CLOCKWISE_180);
                BlockPos at;
                String who = role;
                switch (role) {
                    case "stables" -> { at = Landmarks.stables(c, w, r); who = "stablemaster"; }
                    case "caravan" -> { at = Landmarks.caravanYard(c, w, zs - zn + 1, r); who = "caravan_master"; }
                    case "mage" -> at = Landmarks.mageTower(c, w / 2, (zs - zn) / 2, r);
                    default -> at = Buildings.shop(c, w, zs - zn - 1, 2, Buildings.Style.random(r), Wares.shop(role), r);
                }
                spot.accept(new CityData.Spot(who, c.pos(at.getX(), at.getY(), at.getZ()), c.dir(Direction.NORTH).get2DDataValue()));
            }));
            return;
        }
        for (boolean north : new boolean[] {true, false}) {
            int x = a;
            while (x <= e) {
                int w = 7 + plan.nextInt(5);
                if (x + w - 1 > e) w = e - x + 1;
                if (w < 7) {
                    long s = plan.nextLong();
                    int gx = x, gw = w;
                    if (gw >= 4) {
                        steps.add(new Step("Planting gardens", () -> Landmarks.garden(north ? d.at(gx, 0, zn) : d.at(gx + gw - 1, 0, zs, Rotation.CLOCKWISE_180),
                                0, 1, gw, 5, RandomSource.create(s))));
                    }
                    break;
                }
                int depth = 7 + plan.nextInt(2);
                int floors = plan.nextFloat() < 0.15F ? 1 : plan.nextFloat() < 0.3F ? 3 : 2;
                boolean open = plan.nextFloat() < 0.35F;
                long s = plan.nextLong();
                int hx = x, hw = w;
                steps.add(new Step("Building the houses", () -> {
                    RandomSource r = RandomSource.create(s);
                    Canvas c = north ? d.at(hx, 0, zn) : d.at(hx + hw - 1, 0, zs, Rotation.CLOCKWISE_180);
                    Buildings.house(c, hw, depth, floors, Buildings.Style.random(r), open, r);
                }));
                x += w + plan.nextInt(3);
            }
        }
    }

    // ------------------------------------------------------------------ lamps

    /** Lamps along the lane inside a side of the wall (drawn as the north side). */
    private static void laneLamps(Canvas n) {
        for (int t = -80; t <= 80; t += 16) {
            if (Math.abs(t) <= 12 || Math.abs(Math.abs(t) - 48) <= 6) continue;
            Bk.lampPost(n, t, -LANE - 1);
        }
    }

    /** Lamps along the road out of the north gate (drawn as the north side), on the land as it is there. */
    private static void roadLamps(Canvas n) {
        for (int t = WALL + 8; t <= EXTENT - 2; t += 8) {
            for (int x : new int[] {-4, 4}) {
                BlockPos p = n.pos(x, 0, -t);
                int y = n.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
                Canvas at = new Canvas(n.level, new BlockPos(p.getX(), y, p.getZ()), Rotation.NONE);
                Bk.lampPost(at, 0, 0);
            }
        }
    }
}
