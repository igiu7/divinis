package com.divinis.city;

import java.util.function.BiConsumer;

import com.divinis.city.Bk.Roof;
import com.divinis.city.Bk.Wood;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The city's buildings, each drawn facing north on its canvas: x from 0 to w - 1, z from 0 (the front, on the street)
 * to d - 1, storeys of 4 blocks (a beam course every fourth row). Houses are half-timbered over a stone ground floor,
 * often with the upper storeys jutting out over the street, under steep gabled roofs with a chimney. Shops have an
 * arched entrance, a sign, a counter and the place where their NPC will stand.
 */
final class Buildings {
    private Buildings() {}

    /** How a building looks. */
    record Style(Wood wood, Roof roof, Block plaster, boolean stoneBase, boolean jetty, boolean frontGable) {
        static Style random(RandomSource r) {
            Wood wood = Wood.values()[r.nextInt(Wood.values().length)];
            Roof roof = r.nextFloat() < 0.4F ? Roof.SLATE : Roof.values()[r.nextInt(Roof.values().length)];
            Block plaster = Bk.PLASTER[r.nextInt(Bk.PLASTER.length)];
            return new Style(wood, roof, plaster, r.nextFloat() < 0.8F, r.nextFloat() < 0.45F, r.nextFloat() < 0.6F);
        }

        Style noJetty() {
            return new Style(wood, roof, plaster, true, false, frontGable);
        }
    }

    /** A shop: its role (for its NPC), the name on its sign, how it is furnished. */
    record Shop(String role, String name, BiConsumer<Canvas, int[]> wares) {}

    // ------------------------------------------------------------------ houses

    /** A house; open: it has a door and is furnished, otherwise it is shut (nobody gets in). */
    static void house(Canvas c, int w, int d, int floors, Style s, boolean open, RandomSource r) {
        shell(c, w, d, floors, s, r, null);
        int xc = w / 2;
        if (open) {
            Bk.door(c, xc, 0, 0, s.wood().door, Direction.NORTH, r.nextBoolean());
            furnish(c, w, d, floors, s, r);
        } else {
            // a boarded door: planks where the door would be
            c.set(xc, 0, 0, s.wood().planks);
            c.set(xc, 1, 0, s.wood().planks);
        }
        porch(c, xc, s, r);
        // a little life on the street: a barrel, a pot of flowers, crates
        if (r.nextFloat() < 0.5F) c.set(xc + 2 < w ? xc + 2 : xc - 2, 0, -1, r.nextBoolean() ? Blocks.BARREL : Blocks.HAY_BLOCK);
        if (r.nextFloat() < 0.4F && xc - 2 >= 0) c.set(xc - 2, 0, -1, potted(r));
    }

    /** Over the door: a little canopy of upside-down stairs with a lantern hanging beside it (or the jetty does). */
    private static void porch(Canvas c, int xc, Style s, RandomSource r) {
        if (!s.jetty()) {
            for (int x = xc - 1; x <= xc + 1; x++) c.set(x, 2, -1, Bk.stairs(s.wood().stairs, Direction.SOUTH, true));
        }
        c.set(xc + 1, 1, -1, Bk.lantern(true));
    }

    static BlockState potted(RandomSource r) {
        Block[] pots = {Blocks.POTTED_RED_TULIP, Blocks.POTTED_POPPY, Blocks.POTTED_AZURE_BLUET, Blocks.POTTED_ALLIUM,
                Blocks.POTTED_FERN, Blocks.POTTED_OXEYE_DAISY, Blocks.POTTED_BLUE_ORCHID};
        return pots[r.nextInt(pots.length)].defaultBlockState();
    }

    /**
     * Walls, floors, windows, shutters, roof and chimney. shop: a stone ground floor with an arched entrance and big
     * windows instead of a door (null for a house).
     */
    static void shell(Canvas c, int w, int d, int floors, Style s, RandomSource r, Shop shop) {
        int top = 4 * floors - 1;              // the top plate
        boolean jetty = s.jetty() && floors > 1 && shop == null;
        Wood wood = s.wood();
        int xc = w / 2;

        // foundation and ground floor
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                c.set(x, -1, z, edge ? Bk.cobble(r) : Bk.st(wood.planks));
            }
        }
        // walls, row by row
        for (int y = 0; y <= top; y++) {
            int zf = jetty && y >= 3 ? -1 : 0;
            boolean beam = y % 4 == 3;
            int storey = y / 4;
            for (int x = 0; x < w; x++) {
                for (int z = zf; z < d; z++) {
                    boolean xEdge = x == 0 || x == w - 1, zEdge = z == zf || z == d - 1;
                    if (!xEdge && !zEdge) continue;
                    BlockState b;
                    if (xEdge && zEdge) b = Bk.log(wood.log, Direction.Axis.Y);
                    else if (beam) b = Bk.log(wood.stripped, zEdge ? Direction.Axis.X : Direction.Axis.Z);
                    else if (storey == 0 && (s.stoneBase() || shop != null)) b = shop != null ? Bk.stone(r) : Bk.cobble(r);
                    else if (zEdge ? (x % 3 == 0) : (z % 3 == 0)) b = Bk.log(wood.log, Direction.Axis.Y);
                    else b = Bk.st(s.plaster());
                    c.set(x, y, z, b);
                }
            }
            // inside: air, floors on the beam rows
            for (int x = 1; x < w - 1; x++) {
                for (int z = zf + 1; z < d - 1; z++) c.set(x, y, z, beam ? Bk.st(wood.planks) : Bk.st(Blocks.AIR));
            }
        }
        // the jetty rests on a row of upside-down stairs
        if (jetty) {
            for (int x = 0; x < w; x++) c.set(x, 2, -1, Bk.stairs(wood.stairs, Direction.SOUTH, true));
        }

        // windows: front, sides, back
        for (int f = 0; f < floors; f++) {
            int y = 4 * f + 1;
            int zf = jetty && f > 0 ? -1 : 0;
            if (f == 0 && shop != null) {
                for (int x : new int[] {2, 3, w - 4, w - 3}) if (x > 0 && x < w - 1 && Math.abs(x - xc) > 2) pane(c, x, y, zf);
            } else if (f == 0) {
                for (int x : new int[] {xc - 3, xc + 3}) if (x > 0 && x < w - 1) pane(c, x, y, zf);
            } else {
                for (int x : w >= 9 ? new int[] {xc - 3, xc, xc + 3} : new int[] {xc - 2, xc + 2}) {
                    if (x <= 0 || x >= w - 1) continue;
                    pane(c, x, y, zf);
                    shutters(c, wood, x, y, zf - 1, Direction.NORTH, true);
                }
            }
            int zm = (zf + d - 1) / 2;
            for (int z : d >= 11 ? new int[] {zm - 3, zm + 2} : new int[] {zm}) {
                if (z <= zf || z >= d - 1) continue;
                pane(c, 0, y, z);
                pane(c, w - 1, y, z);
                if (f > 0) {
                    shutters(c, wood, -1, y, z, Direction.WEST, false);
                    shutters(c, wood, w, y, z, Direction.EAST, false);
                }
            }
            if (f > 0) pane(c, xc, y, d - 1);
        }

        // the shop's entrance: an arch three wide, with a sign and banners
        if (shop != null) {
            c.clear(xc - 1, 0, 0, xc + 1, 1, 0);
            c.set(xc, 2, 0, Blocks.AIR);
            c.set(xc - 1, 2, 0, Bk.stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
            c.set(xc + 1, 2, 0, Bk.stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
            c.set(xc - 2, 0, 0, Blocks.CHISELED_STONE_BRICKS);
            c.set(xc + 2, 0, 0, Blocks.CHISELED_STONE_BRICKS);
            c.sign(xc, 3, -1, Direction.NORTH, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState(), "", shop.name(), "");
            c.set(xc - 2, 2, -1, Bk.banner(Blocks.PURPLE_WALL_BANNER, Direction.NORTH));
            c.set(xc + 2, 2, -1, Bk.banner(Blocks.PURPLE_WALL_BANNER, Direction.NORTH));
            c.set(xc - 3, 1, -1, Bk.lantern(false));
            c.set(xc - 3, 0, -1, Blocks.STONE_BRICK_WALL);
            c.set(xc + 3, 1, -1, Bk.lantern(false));
            c.set(xc + 3, 0, -1, Blocks.STONE_BRICK_WALL);
        }

        // the ladder up through the floors, against the back wall
        if (floors > 1) {
            for (int y = 0; y < top; y++) c.set(w - 2, y, d - 2, Bk.ladder(Direction.NORTH));
        }

        // roof and chimney
        int zTop = jetty ? -1 : 0;
        int yb = top + 1;
        Block fill = s.plaster();
        int peak;
        if (s.frontGable()) {
            peak = roofAlongZ(c, 0, w - 1, zTop, d - 1, yb, s.roof(), fill);
            if (w >= 7) pane(c, xc, yb + 1, zTop);
        } else {
            peak = roofAlongX(c, 0, w - 1, zTop, d - 1, yb, s.roof(), fill);
            if (d - zTop >= 7) {
                pane(c, 0, yb + 1, (zTop + d - 1) / 2);
                pane(c, w - 1, yb + 1, (zTop + d - 1) / 2);
            }
        }
        if (r.nextFloat() < 0.85F) {
            Block brick = r.nextBoolean() ? Blocks.BRICKS : Blocks.COBBLESTONE;
            int chimneyTop = Math.min(peak, yb + 3) + 1;
            for (int y = 0; y <= chimneyTop; y++) c.set(1, y, d - 1, brick);
            c.set(1, chimneyTop + 1, d - 1, Bk.campfire());
        }
    }

    private static void pane(Canvas c, int x, int y, int z) {
        c.set(x, y, z, Blocks.GLASS_PANE);
        c.set(x, y + 1, z, Blocks.GLASS_PANE);
    }

    /** Open shutters on both sides of a window two high; out: the way the wall faces. */
    private static void shutters(Canvas c, Wood wood, int x, int y, int z, Direction out, boolean alongX) {
        for (int dy = 0; dy < 2; dy++) {
            if (alongX) {
                c.set(x - 1, y + dy, z, Bk.shutter(wood.trapdoor, out));
                c.set(x + 1, y + dy, z, Bk.shutter(wood.trapdoor, out));
            } else {
                c.set(x, y + dy, z - 1, Bk.shutter(wood.trapdoor, out));
                c.set(x, y + dy, z + 1, Bk.shutter(wood.trapdoor, out));
            }
        }
    }

    /** A gabled roof with its ridge along z (the gables at z0 and z1), one block of eaves all round; returns its top y. */
    static int roofAlongZ(Canvas c, int x0, int x1, int z0, int z1, int yb, Roof roof, Block gable) {
        for (int k = 0; ; k++) {
            int xl = x0 - 1 + k, xr = x1 + 1 - k, y = yb + k;
            if (xl > xr) return y - 1;
            if (xl == xr) {
                for (int z = z0 - 1; z <= z1 + 1; z++) c.set(xl, y, z, roof.full);
                return y;
            }
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                c.set(xl, y, z, Bk.stairs(roof.stairs, Direction.EAST, false));
                c.set(xr, y, z, Bk.stairs(roof.stairs, Direction.WEST, false));
            }
            for (int x = xl + 1; x < xr; x++) {
                c.set(x, y, z0, gable);
                c.set(x, y, z1, gable);
                for (int z = z0 + 1; z < z1; z++) c.set(x, y, z, Blocks.AIR);
            }
        }
    }

    /** The same with its ridge along x (the gables at x0 and x1). */
    static int roofAlongX(Canvas c, int x0, int x1, int z0, int z1, int yb, Roof roof, Block gable) {
        for (int k = 0; ; k++) {
            int zn = z0 - 1 + k, zs = z1 + 1 - k, y = yb + k;
            if (zn > zs) return y - 1;
            if (zn == zs) {
                for (int x = x0 - 1; x <= x1 + 1; x++) c.set(x, y, zn, roof.full);
                return y;
            }
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                c.set(x, y, zn, Bk.stairs(roof.stairs, Direction.SOUTH, false));
                c.set(x, y, zs, Bk.stairs(roof.stairs, Direction.NORTH, false));
            }
            for (int z = zn + 1; z < zs; z++) {
                c.set(x0, y, z, gable);
                c.set(x1, y, z, gable);
                for (int x = x0 + 1; x < x1; x++) c.set(x, y, z, Blocks.AIR);
            }
        }
    }

    // ------------------------------------------------------------------ inside a house

    private static void furnish(Canvas c, int w, int d, int floors, Style s, RandomSource r) {
        Wood wood = s.wood();
        // ground floor: table and chairs, hearth, barrels
        c.set(2, 0, 2, wood.fence);
        c.set(2, 1, 2, Blocks.OAK_PRESSURE_PLATE);
        c.set(1, 0, 2, Bk.stairs(wood.stairs, Direction.WEST, false));
        c.set(3, 0, 2, Bk.stairs(wood.stairs, Direction.EAST, false));
        c.set(1, 0, d - 2, Blocks.BARREL);
        c.set(2, 0, d - 2, (r.nextBoolean() ? Blocks.FURNACE : Blocks.SMOKER).defaultBlockState().setValue(FurnaceBlock.FACING, Direction.NORTH));
        if (w > 7) c.set(3, 0, d - 2, Blocks.CRAFTING_TABLE);
        c.set(w - 2, 0, 1, Blocks.BOOKSHELF);
        c.set(w / 2, 0, d / 2, carpet(r));
        c.set(w / 2, 2, d / 2, Bk.lantern(true));
        // upstairs: a bed, a chest, a shelf
        for (int f = 1; f < floors; f++) {
            int y = 4 * f;
            int zf = s.jetty() ? 0 : 1;
            Bk.bed(c, 1, y, zf + 1, r.nextBoolean() ? Blocks.RED_BED : Blocks.BROWN_BED, Direction.NORTH);
            c.set(w - 2, y, zf, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
            c.set(1, y, d - 2, Blocks.BOOKSHELF);
            c.set(2, y, d - 2, Blocks.BARREL);
            c.set(2, y + 1, d - 2, potted(r));
            c.set(w / 2, y, d / 2, carpet(r));
            c.set(w / 2, y + 2, d / 2, Bk.lantern(true));
        }
    }

    private static BlockState carpet(RandomSource r) {
        Block[] carpets = {Blocks.RED_CARPET, Blocks.BROWN_CARPET, Blocks.GREEN_CARPET, Blocks.PURPLE_CARPET, Blocks.BLUE_CARPET};
        return carpets[r.nextInt(carpets.length)].defaultBlockState();
    }

    // ------------------------------------------------------------------ shops

    /**
     * A shop (or hall) on the square: a stone ground floor with the arched entrance, its wares, a counter across it and
     * behind it the place of its NPC; storerooms above. Returns where the NPC stands (canvas coordinates).
     */
    static BlockPos shop(Canvas c, int w, int d, int floors, Style s, Shop shop, RandomSource r) {
        Style style = s.noJetty();
        shell(c, w, d, floors, style, r, shop);
        int xc = w / 2, cz = d - 6;
        // the counter, with a way through at its end
        for (int x = 2; x <= w - 4; x++) c.set(x, 0, cz, Bk.log(style.wood().stripped, Direction.Axis.X));
        for (int x = 2; x <= w - 4; x += 3) c.set(x, 1, cz, potted(r));
        c.set(w - 3, 0, cz, Bk.trapdoor(style.wood().trapdoor, Direction.NORTH, true));
        // shelves along the back wall
        for (int x = 1; x <= w - 3; x++) c.set(x, 0, d - 2, x % 2 == 0 ? Blocks.BARREL : Blocks.BOOKSHELF);
        for (int x = 1; x <= w - 3; x++) c.set(x, 1, d - 2, x % 3 == 0 ? Bk.st(Blocks.BARREL) : Bk.st(Blocks.AIR));
        // light
        c.set(xc, 2, cz - 2, Bk.lantern(true));
        c.set(xc, 2, cz + 2, Bk.lantern(true));
        c.set(3, 2, 3, Bk.lantern(true));
        c.set(w - 4, 2, 3, Bk.lantern(true));
        // a red carpet from the door to the counter
        for (int z = 1; z < cz; z++) c.set(xc, 0, z, Blocks.RED_CARPET);
        shop.wares().accept(c, new int[] {w, d, cz});
        // upstairs: storerooms and the keeper's bed
        for (int f = 1; f < floors; f++) {
            int y = 4 * f;
            for (int x = 1; x < w - 2; x += 2) c.set(x, y, d - 2, r.nextBoolean() ? Blocks.BARREL : Blocks.CHEST);
            Bk.bed(c, 1, y, 2, Blocks.PURPLE_BED, Direction.NORTH);
            c.set(xc, y + 2, d / 2, Bk.lantern(true));
            c.set(xc, y, d / 2, carpet(r));
        }
        return new BlockPos(xc, 0, cz + 2);
    }

    // ------------------------------------------------------------------ trees

    /** A tree drawn by hand (its leaves never decay): trunk at (x, z), from y = 0. */
    static void tree(Canvas c, int x, int z, RandomSource r) {
        boolean birch = r.nextFloat() < 0.25F;
        Block log = birch ? Blocks.BIRCH_LOG : Blocks.OAK_LOG, leaves = birch ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES;
        int h = 4 + r.nextInt(3);
        for (int y = 0; y < h; y++) c.set(x, y, z, Bk.log(log, Direction.Axis.Y));
        int cy = h - 1;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double dist = dx * dx + dy * dy * 1.6 + dz * dz;
                    if (dist > 9.5 || (dist > 7 && r.nextFloat() < 0.4F)) continue;
                    if (dx == 0 && dz == 0 && dy < 1) continue;
                    if (c.isAir(x + dx, cy + dy, z + dz)) c.set(x + dx, cy + dy, z + dz, Bk.leaves(leaves));
                }
            }
        }
    }
}
