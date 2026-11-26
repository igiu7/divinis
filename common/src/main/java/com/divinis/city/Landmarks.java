package com.divinis.city;

import com.divinis.city.Bk.Roof;
import com.divinis.city.Bk.Wood;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The city's great buildings, each drawn facing north on its canvas like the houses: the cathedral, the mage's tower,
 * the stables, the caravan yard and the park. Those with an NPC return where it stands (canvas coordinates).
 */
final class Landmarks {
    private Landmarks() {}

    // ------------------------------------------------------------------ cathedral

    /** The cathedral: a bell tower in front with a tall spire, the nave behind it with buttresses and coloured glass. */
    static BlockPos cathedral(Canvas c, RandomSource r) {
        int nx0 = 3, nx1 = 17, nz0 = 8, nz1 = 36, wallTop = 13;
        Block[] glass = {Blocks.PURPLE_STAINED_GLASS_PANE, Blocks.BLUE_STAINED_GLASS_PANE, Blocks.MAGENTA_STAINED_GLASS_PANE, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE};
        // the nave
        for (int x = nx0; x <= nx1; x++) {
            for (int z = nz0; z <= nz1; z++) {
                boolean edge = x == nx0 || x == nx1 || z == nz1;
                c.set(x, -1, z, edge ? Bk.stone(r) : Bk.st((x + z) % 2 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS));
                for (int y = 0; y <= wallTop; y++) c.set(x, y, z, edge ? Bk.stone(r) : Bk.st(Blocks.AIR));
                if (!edge) c.set(x, wallTop, z, Blocks.DARK_OAK_PLANKS);
            }
        }
        Buildings.roofAlongZ(c, nx0, nx1, nz0, nz1, wallTop + 1, Roof.SLATE, Blocks.STONE_BRICKS);
        // tall windows between the buttresses, buttresses outside
        for (int z = nz0 + 2; z <= nz1 - 2; z += 4) {
            Block g = glass[(z / 4) % glass.length];
            for (int y = 3; y <= 10; y++) {
                c.set(nx0, y, z, g);
                c.set(nx1, y, z, g);
            }
            c.set(nx0, 11, z, Bk.stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
            c.set(nx1, 11, z, Bk.stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
            int zb = z + 2;
            if (zb >= nz1) continue;
            for (int y = -1; y <= 9; y++) {
                c.set(nx0 - 1, y, zb, Bk.stone(r));
                c.set(nx1 + 1, y, zb, Bk.stone(r));
            }
            c.set(nx0 - 1, 10, zb, Bk.stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, false));
            c.set(nx1 + 1, 10, zb, Bk.stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, false));
        }
        // a round window in the back wall
        for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) {
            if (dx * dx + dy * dy <= 5) c.set(10 + dx, 8 + dy, nz1, dx == 0 && dy == 0 ? Blocks.YELLOW_STAINED_GLASS_PANE : Blocks.PURPLE_STAINED_GLASS_PANE);
        }
        // pews, the aisle, the altar, chandeliers
        for (int z = nz0 + 4; z <= nz1 - 8; z += 2) {
            for (int x = nx0 + 2; x <= 8; x++) c.set(x, 0, z, Bk.stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
            for (int x = 12; x <= nx1 - 2; x++) c.set(x, 0, z, Bk.stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
        }
        for (int z = nz0; z <= nz1 - 5; z++) for (int x = 9; x <= 11; x++) c.set(x, 0, z, Blocks.PURPLE_CARPET);
        c.fill(7, 0, nz1 - 4, 13, 0, nz1 - 1, Blocks.POLISHED_ANDESITE);
        c.fill(9, 1, nz1 - 3, 11, 1, nz1 - 3, Blocks.CHISELED_STONE_BRICKS);
        c.set(9, 2, nz1 - 3, Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 3)
                .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
        c.set(11, 2, nz1 - 3, Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 3)
                .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
        c.set(10, 1, nz1 - 5, Blocks.LECTERN);
        c.set(8, 1, nz1 - 1, Bk.banner(Blocks.PURPLE_WALL_BANNER, Direction.NORTH));
        c.set(12, 1, nz1 - 1, Bk.banner(Blocks.PURPLE_WALL_BANNER, Direction.NORTH));
        for (int z = nz0 + 5; z <= nz1 - 5; z += 7) {
            c.set(10, wallTop - 1, z, Blocks.CHAIN);
            c.set(10, wallTop - 2, z, Bk.lantern(true));
        }
        // the bell tower in front
        int tx0 = 6, tx1 = 14, tz0 = 0, tz1 = 8, th = 24;
        for (int x = tx0; x <= tx1; x++) {
            for (int z = tz0; z <= tz1; z++) {
                boolean edge = x == tx0 || x == tx1 || z == tz0 || z == tz1;
                c.set(x, -1, z, Bk.stone(r));
                for (int y = 0; y <= th; y++) c.set(x, y, z, edge || y == th ? Bk.stone(r) : Bk.st(Blocks.AIR));
            }
        }
        for (int x = tx0; x <= tx1; x += tx1 - tx0) for (int z = tz0; z <= tz1; z += tz1 - tz0) {
            for (int y = -1; y <= th + 1; y++) c.set(x, y, z, Blocks.CHISELED_STONE_BRICKS);
        }
        // the great door: an arch three wide and four high, open into the nave
        c.clear(9, 0, tz0, 11, 3, tz0);
        c.set(9, 4, tz0, Bk.stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
        c.set(11, 4, tz0, Bk.stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
        c.set(10, 4, tz0, Blocks.AIR);
        c.clear(9, 0, tz1, 11, 4, tz1);
        // the rose window over the door, louvred openings for the bells, the bells
        for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) {
            if (dx * dx + dy * dy <= 5) c.set(10 + dx, 9 + dy, tz0, dx == 0 && dy == 0 ? Blocks.YELLOW_STAINED_GLASS_PANE : Blocks.PURPLE_STAINED_GLASS_PANE);
        }
        c.fill(tx0 + 1, 17, tz0 + 1, tx1 - 1, 17, tz1 - 1, Blocks.SPRUCE_PLANKS);
        for (int x = 8; x <= 12; x += 4) {
            c.clear(x, 19, tz0, x, 21, tz0);
            c.clear(x, 19, tz1, x, 21, tz1);
        }
        c.clear(tx0, 19, 3, tx0, 21, 5);
        c.clear(tx1, 19, 3, tx1, 21, 5);
        c.set(10, 18, 4, Blocks.BELL);
        c.set(10, 23, 4, Bk.lantern(true));
        c.set(10, 12, 4, Bk.lantern(true));
        // the spire: a steep slate pyramid, two rows for every step in
        for (int k = 0; k <= 5; k++) {
            int a = tx0 - 1 + k, b = tx1 + 1 - k, p = tz0 - 1 + k, q = tz1 + 1 - k;
            for (int dy = 0; dy < 2; dy++) {
                int y = th + 1 + k * 2 + dy;
                boolean stair = dy == 0;
                for (int x = a; x <= b; x++) {
                    c.set(x, y, p, stair ? Bk.stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.SOUTH, false) : Bk.st(Blocks.DEEPSLATE_TILES));
                    c.set(x, y, q, stair ? Bk.stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.NORTH, false) : Bk.st(Blocks.DEEPSLATE_TILES));
                }
                for (int z = p + 1; z < q; z++) {
                    c.set(a, y, z, stair ? Bk.stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.EAST, false) : Bk.st(Blocks.DEEPSLATE_TILES));
                    c.set(b, y, z, stair ? Bk.stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.WEST, false) : Bk.st(Blocks.DEEPSLATE_TILES));
                }
            }
        }
        int spireTop = th + 1 + 12;
        c.set(10, spireTop, 4, Blocks.DEEPSLATE_TILES);
        c.set(10, spireTop + 1, 4, Blocks.CHISELED_STONE_BRICKS);
        c.set(10, spireTop + 2, 4, Blocks.LIGHTNING_ROD);
        // flowers and lamps before the door
        for (int x : new int[] {7, 13}) Bk.lampPost(c, x, -2);
        return new BlockPos(10, 0, nz1 - 6);
    }

    // ------------------------------------------------------------------ the mage's tower

    /** A round tower of dark stone with violet glass and an amethyst crown; centre at (cx, cz). */
    static BlockPos mageTower(Canvas c, int cx, int cz, RandomSource r) {
        int h = 24;
        double outer = 5.4 * 5.4, inner = 3.6 * 3.6;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d2 = dx * dx + dz * dz;
                if (d2 > outer) continue;
                boolean shell = d2 > inner;
                c.set(cx + dx, -1, cz + dz, shell ? Bk.st(Blocks.POLISHED_BLACKSTONE_BRICKS) : Bk.st(Blocks.PURPUR_BLOCK));
                for (int y = 0; y <= h; y++) {
                    BlockState s;
                    if (shell) s = Bk.st(y % 6 == 5 ? Blocks.CHISELED_POLISHED_BLACKSTONE : r.nextFloat() < 0.2F ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.DEEPSLATE_BRICKS);
                    else s = Bk.st(y == 8 || y == 16 ? Blocks.DARK_OAK_PLANKS : Blocks.AIR);
                    c.set(cx + dx, y, cz + dz, s);
                }
            }
        }
        // windows of violet glass all the way up
        for (int y = 2; y <= h - 2; y += 4) {
            c.set(cx + 5, y, cz, Blocks.PURPLE_STAINED_GLASS_PANE);
            c.set(cx - 5, y, cz, Blocks.PURPLE_STAINED_GLASS_PANE);
            c.set(cx, y + 1, cz + 5, Blocks.PURPLE_STAINED_GLASS_PANE);
            c.set(cx, y + 1, cz - 5, Blocks.PURPLE_STAINED_GLASS_PANE);
        }
        // the door, a ladder, the study inside
        c.clear(cx, 0, cz - 5, cx, 1, cz - 4);
        Bk.door(c, cx, 0, cz - 5, Blocks.DARK_OAK_DOOR, Direction.NORTH, false);
        for (int y = 0; y < h; y++) c.set(cx + 3, y, cz, Bk.ladder(Direction.WEST));
        c.set(cx - 3, 0, cz, Blocks.ENCHANTING_TABLE);
        c.set(cx - 3, 0, cz + 1, Blocks.BOOKSHELF);
        c.set(cx - 3, 0, cz - 1, Blocks.BOOKSHELF);
        c.set(cx - 2, 0, cz + 2, Blocks.BREWING_STAND);
        c.set(cx, 0, cz + 3, Blocks.LECTERN);
        c.set(cx, 3, cz, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        c.set(cx, 11, cz, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        for (int dx = -3; dx <= 3; dx += 6) for (int y = 9; y <= 10; y++) c.set(cx + dx, y, cz - 1, Blocks.BOOKSHELF);
        c.set(cx - 2, 9, cz + 2, Blocks.AMETHYST_CLUSTER);
        // the crown: a ring of amethyst, a cone of slate, a glowing tip
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            double d2 = dx * dx + dz * dz;
            if (d2 <= 6.2 * 6.2 && d2 > 4.8 * 4.8) c.set(cx + dx, h, cz + dz, Blocks.POLISHED_BLACKSTONE_BRICKS);
            if (d2 <= 6.2 * 6.2 && d2 > 5.4 * 5.4 && (dx + dz) % 2 == 0) c.set(cx + dx, h + 1, cz + dz, Blocks.AMETHYST_BLOCK);
        }
        for (int k = 0; k <= 7; k++) {
            double rr = 5.5 - k * 0.75;
            int y = h + 1 + k;
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
                double d2 = dx * dx + dz * dz;
                if (d2 > rr * rr || d2 < (rr - 1.2) * (rr - 1.2)) continue;
                c.set(cx + dx, y, cz + dz, Blocks.DEEPSLATE_TILES);
            }
        }
        c.set(cx, h + 9, cz, Blocks.AMETHYST_BLOCK);
        c.set(cx, h + 10, cz, Blocks.END_ROD);
        return new BlockPos(cx, 0, cz + 1);
    }

    // ------------------------------------------------------------------ stables

    /** Open stables: stalls under a roof along the back, hay, troughs, a fenced paddock in front. */
    static BlockPos stables(Canvas c, int w, RandomSource r) {
        Wood wood = Wood.SPRUCE;
        int z0 = 8, z1 = 16;
        for (int x = 0; x < w; x++) for (int z = z0; z <= z1; z++) c.set(x, -1, z, (x + z) % 3 == 0 ? Blocks.COARSE_DIRT : Blocks.PACKED_MUD);
        // posts, the back wall, the roof
        for (int x = 0; x < w; x += 3) {
            for (int y = 0; y <= 3; y++) {
                c.set(x, y, z0, Bk.log(wood.log, Direction.Axis.Y));
                c.set(x, y, z1, Bk.log(wood.log, Direction.Axis.Y));
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y <= 3; y++) if (x % 3 != 0) c.set(x, y, z1, wood.planks);
            c.set(x, 4, z0, Bk.log(wood.stripped, Direction.Axis.X));
            c.set(x, 4, z1, Bk.log(wood.stripped, Direction.Axis.X));
        }
        for (int z = z0; z <= z1; z++) {
            for (int y = 0; y <= 3; y++) {
                c.set(0, y, z, z == z0 ? Bk.log(wood.log, Direction.Axis.Y) : Bk.st(wood.planks));
                c.set(w - 1, y, z, z == z0 ? Bk.log(wood.log, Direction.Axis.Y) : Bk.st(wood.planks));
            }
        }
        Buildings.roofAlongX(c, 0, w - 1, z0, z1, 5, Roof.SPRUCE_WOOD, wood.planks);
        // stalls: fences between them, a gate in front, hay and a trough in each
        for (int x = 3; x < w - 1; x += 3) for (int z = z0 + 1; z < z1; z++) c.set(x, 0, z, wood.fence);
        for (int x = 1; x < w - 1; x++) {
            if (x % 3 == 0) continue;
            c.set(x, 0, z0, x % 3 == 1 ? Bk.st(wood.fence) : Bk.st(Blocks.SPRUCE_FENCE_GATE));
        }
        for (int x = 1; x < w - 1; x += 3) {
            c.set(x, 0, z1 - 1, Blocks.HAY_BLOCK);
            if (x + 1 < w - 1) c.set(x + 1, 0, z1 - 1, Blocks.WATER_CAULDRON.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
            c.set(x, 3, (z0 + z1) / 2, Bk.lantern(true));
        }
        // the paddock in front
        for (int x = 0; x < w; x++) {
            c.set(x, 0, 0, x == w / 2 ? Bk.st(Blocks.SPRUCE_FENCE_GATE) : Bk.st(wood.fence));
            for (int z = 0; z < z0; z++) c.set(x, -1, z, r.nextFloat() < 0.3F ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK);
        }
        for (int z = 1; z < z0; z++) {
            c.set(0, 0, z, wood.fence);
            c.set(w - 1, 0, z, wood.fence);
        }
        c.set(2, 0, 2, Blocks.HAY_BLOCK);
        c.set(3, 0, 2, Blocks.HAY_BLOCK);
        c.set(2, 1, 2, Blocks.HAY_BLOCK);
        c.set(w - 3, 0, 3, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
        Bk.lampPost(c, w / 2 - 2, -1);
        c.sign(w / 2 + 2, 1, -1, Direction.NORTH, Blocks.SPRUCE_WALL_SIGN.defaultBlockState(), "", "Stables", "");
        c.set(w / 2 + 2, 0, -1, Blocks.SPRUCE_PLANKS);
        c.set(w / 2 + 2, 1, 0, Blocks.SPRUCE_PLANKS);
        return new BlockPos(w / 2, 0, 3);
    }

    // ------------------------------------------------------------------ the caravan yard

    /** A fenced yard where caravans stop: carts loaded with goods, a tent, crates and barrels. */
    static BlockPos caravanYard(Canvas c, int w, int d, RandomSource r) {
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
            c.set(x, -1, z, r.nextFloat() < 0.25F ? Blocks.COARSE_DIRT : r.nextFloat() < 0.2F ? Blocks.GRAVEL : Blocks.DIRT_PATH);
            boolean edge = x == 0 || x == w - 1 || z == d - 1;
            if (edge) c.set(x, 0, z, Blocks.OAK_FENCE);
        }
        for (int x = 0; x < w; x++) if (Math.abs(x - w / 2) > 2) c.set(x, 0, 0, Blocks.OAK_FENCE);
        // carts: a bed of slabs on wheels (trapdoors), loaded
        for (int[] cart : new int[][] {{3, 4}, {w - 7, 4}, {3, d - 6}}) {
            int x = cart[0], z = cart[1];
            for (int dx = 0; dx < 4; dx++) for (int dz = 0; dz < 2; dz++) c.set(x + dx, 1, z + dz, Bk.slab(Blocks.SPRUCE_SLAB, false));
            for (int dx : new int[] {0, 3}) {
                c.set(x + dx, 0, z - 1, Bk.shutter(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH));
                c.set(x + dx, 0, z + 2, Bk.shutter(Blocks.DARK_OAK_TRAPDOOR, Direction.SOUTH));
            }
            c.set(x + 1, 2, z, Blocks.BARREL);
            c.set(x + 2, 2, z + 1, Blocks.HAY_BLOCK);
            c.set(x + 3, 2, z, Blocks.CHEST);
            c.set(x - 1, 1, z, Blocks.OAK_FENCE);
        }
        // a tent of striped cloth
        int tx = w / 2 - 2, tz = d - 7;
        for (int x = tx; x <= tx + 4; x++) for (int z = tz; z <= tz + 4; z++) {
            boolean corner = (x == tx || x == tx + 4) && (z == tz || z == tz + 4);
            if (corner) for (int y = 0; y <= 2; y++) c.set(x, y, z, Blocks.OAK_FENCE);
            c.set(x, 3, z, (x - tx) % 2 == 0 ? Blocks.PURPLE_WOOL : Blocks.WHITE_WOOL);
        }
        for (int x = tx + 1; x <= tx + 3; x++) c.set(x, 4, tz + 2, (x - tx) % 2 == 0 ? Blocks.PURPLE_WOOL : Blocks.WHITE_WOOL);
        c.set(tx + 2, 2, tz + 2, Bk.lantern(true));
        c.set(tx + 1, 0, tz + 3, Blocks.BARREL);
        c.set(tx + 3, 0, tz + 3, Blocks.BARREL);
        c.set(tx + 2, 0, tz + 3, Blocks.CARTOGRAPHY_TABLE);
        for (int i = 0; i < 6; i++) {
            int x = 1 + r.nextInt(w - 2), z = 1 + r.nextInt(d - 2);
            if (c.isAir(x, 0, z) && c.isAir(x, 1, z)) c.set(x, 0, z, r.nextBoolean() ? Blocks.BARREL : Blocks.HAY_BLOCK);
        }
        Bk.lampPost(c, w / 2 - 3, 1);
        Bk.lampPost(c, w / 2 + 3, 1);
        return new BlockPos(tx + 2, 0, tz + 1);
    }

    // ------------------------------------------------------------------ the park

    /** A park: paths crossing at a gazebo, a pond, trees, flowers, benches and lamps. */
    static void park(Canvas c, int w, int d, RandomSource r) {
        Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.LILY_OF_THE_VALLEY};
        int cx = w / 2, cz = d / 2;
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
            boolean path = Math.abs(x - cx) <= 1 || Math.abs(z - cz) <= 1;
            c.set(x, -1, z, path ? (r.nextFloat() < 0.3F ? Blocks.GRAVEL : Blocks.DIRT_PATH) : Blocks.GRASS_BLOCK);
            if (!path) {
                float f = r.nextFloat();
                if (f < 0.10F) c.set(x, 0, z, flowers[r.nextInt(flowers.length)]);
                else if (f < 0.30F) c.set(x, 0, z, Blocks.SHORT_GRASS);
            }
        }
        // a low hedge round it, with gaps for the paths
        for (int x = 0; x < w; x++) for (int z : new int[] {0, d - 1}) if (Math.abs(x - cx) > 1) c.set(x, 0, z, Bk.leaves(Blocks.OAK_LEAVES));
        for (int z = 0; z < d; z++) for (int x : new int[] {0, w - 1}) if (Math.abs(z - cz) > 1) c.set(x, 0, z, Bk.leaves(Blocks.OAK_LEAVES));
        // the pond in one quarter
        int px = cx + (w / 4) + 1, pz = cz + (d / 4) + 1;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            double d2 = dx * dx + dz * dz;
            if (d2 > 17) continue;
            c.set(px + dx, 0, pz + dz, Blocks.AIR);
            if (d2 > 11) {
                c.set(px + dx, -1, pz + dz, Blocks.MOSSY_COBBLESTONE);
            } else {
                c.set(px + dx, -2, pz + dz, Blocks.CLAY);
                c.set(px + dx, -1, pz + dz, Blocks.WATER);
                if (r.nextFloat() < 0.15F) c.set(px + dx, 0, pz + dz, Blocks.LILY_PAD);
            }
        }
        // trees in the other quarters
        for (int[] t : new int[][] {{cx - w / 4, cz - d / 4}, {cx + w / 4, cz - d / 4}, {cx - w / 4, cz + d / 4}, {cx - w / 4 - 3, cz + d / 4 + 4}}) {
            c.set(t[0], -1, t[1], Blocks.GRASS_BLOCK);
            Buildings.tree(c, t[0], t[1], r);
        }
        // the gazebo at the crossing
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            c.set(cx + dx, -1, cz + dz, Blocks.POLISHED_ANDESITE);
            c.set(cx + dx, 0, cz + dz, Blocks.AIR);
            if (Math.abs(dx) == 3 && Math.abs(dz) == 3) for (int y = 0; y <= 3; y++) c.set(cx + dx, y, cz + dz, Blocks.STRIPPED_DARK_OAK_LOG);
        }
        Fortifications.hipRoof(c, cx - 4, cx + 4, cz - 4, cz + 4, 4, Roof.SLATE);
        c.set(cx, 4, cz, Blocks.CHAIN);
        c.set(cx, 3, cz, Bk.lantern(true));
        c.set(cx - 2, 0, cz + 2, Bk.stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
        c.set(cx + 2, 0, cz - 2, Bk.stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
        // benches and lamps along the paths
        for (int k : new int[] {-1, 1}) {
            Bk.lampPost(c, cx + 2, cz + k * (d / 2 - 2));
            Bk.lampPost(c, cx + k * (w / 2 - 2), cz - 2);
            c.set(cx - 2, 0, cz + k * (d / 2 - 4), Bk.stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
            c.set(cx + k * (w / 2 - 4), 0, cz + 2, Bk.stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        }
    }

    /** A small kitchen garden: farmland with ripe crops, a water hole, a fence round it. */
    static void garden(Canvas c, int x0, int z0, int w, int d, RandomSource r) {
        Block[] crops = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};
        Block crop = crops[r.nextInt(crops.length)];
        for (int x = x0; x < x0 + w; x++) for (int z = z0; z < z0 + d; z++) {
            boolean edge = x == x0 || z == z0 || x == x0 + w - 1 || z == z0 + d - 1;
            if (edge) {
                c.set(x, -1, z, Blocks.GRASS_BLOCK);
                c.set(x, 0, z, Blocks.SPRUCE_FENCE);
            } else if (x == x0 + w / 2 && z == z0 + d / 2) {
                c.set(x, -1, z, Blocks.WATER);
                c.set(x, 0, z, Blocks.AIR);
            } else {
                c.set(x, -1, z, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
                c.set(x, 0, z, ((CropBlock) crop).getStateForAge(((CropBlock) crop).getMaxAge()));
            }
        }
    }
}
