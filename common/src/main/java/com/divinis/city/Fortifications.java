package com.divinis.city;

import com.divinis.city.Bk.Roof;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The city's walls, towers and gates. A side of the wall is drawn on a canvas facing north: x runs along the wall,
 * the wall's body is at z from -WALL_OUT to -WALL_IN (outside is towards -z). The walls carry a walkway behind a
 * crenellated parapet on corbels, with arrow slits, banners and buttresses; square towers stand along them, round ones
 * at the corners, and a gatehouse with a portcullis on the north and south sides.
 */
final class Fortifications {
    static final int WALL_IN = 93, WALL_OUT = 96;
    static final int TOP = 10;           // the walkway's floor (players walk at TOP + 1)

    private Fortifications() {}

    /** The wall from x0 to x1, leaving out the gate's width if the side has one. */
    static void wall(Canvas n, int x0, int x1, boolean gate, RandomSource r) {
        BlockState stairsOut = Bk.stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, true);
        for (int t = x0; t <= x1; t++) {
            if (gate && Math.abs(t) <= 8) continue;
            for (int y = -4; y <= TOP; y++) for (int z = WALL_IN; z <= WALL_OUT; z++) n.set(t, y, -z, Bk.stone(r));
            n.clear(t, TOP + 1, -WALL_OUT, t, TOP + 3, -WALL_IN - 1);
            n.set(t, TOP + 1, -WALL_IN, Blocks.STONE_BRICK_WALL);
            // corbels carrying the parapet, the parapet and its merlons with caps
            n.set(t, TOP, -WALL_OUT - 1, stairsOut);
            n.set(t, TOP + 1, -WALL_OUT - 1, Bk.stone(r));
            if (Math.floorMod(t, 4) < 2) {
                n.set(t, TOP + 2, -WALL_OUT - 1, Bk.stone(r));
                n.set(t, TOP + 3, -WALL_OUT - 1, Bk.slab(Blocks.STONE_BRICK_SLAB, false));
            } else {
                n.set(t, TOP + 2, -WALL_OUT - 1, Blocks.AIR);
            }
            // a sloped plinth at the foot
            for (int y = -2; y <= 1; y++) n.set(t, y, -WALL_OUT - 1, Bk.cobble(r));
            n.set(t, 2, -WALL_OUT - 1, Bk.stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
            // arrow slits, banners, buttresses, lanterns on the walkway
            if (Math.floorMod(t, 8) == 4) {
                n.set(t, 6, -WALL_OUT, Blocks.AIR);
                n.set(t, 7, -WALL_OUT, Blocks.AIR);
            }
            if (Math.floorMod(t, 24) == 12) n.set(t, 8, -WALL_OUT - 1, Bk.banner(Blocks.PURPLE_WALL_BANNER, Direction.NORTH));
            if (Math.floorMod(t, 12) == 6) {
                for (int y = -1; y <= 7; y++) n.set(t, y, -WALL_IN + 1, Bk.stone(r));
                n.set(t, 8, -WALL_IN + 1, Bk.stairs(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
            }
            if (Math.floorMod(t, 8) == 0) n.set(t, TOP + 2, -WALL_IN, Bk.lantern(false));
        }
    }

    /** Stairs up to the walkway along the inside of the wall, rising towards +x from x. */
    static void wallStairs(Canvas n, int x, RandomSource r) {
        for (int k = 0; k <= TOP; k++) {
            for (int z = -WALL_IN + 1; z <= -WALL_IN + 2; z++) {
                for (int y = -1; y < k; y++) n.set(x + k, y, z, Bk.stone(r));
                n.set(x + k, k, z, k < TOP ? Bk.stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, false) : Bk.st(Blocks.STONE_BRICKS));
                n.clear(x + k, k + 1, z, x + k, k + 3, z);
            }
        }
        for (int t = x + TOP - 2; t <= x + TOP + 2; t++) n.set(t, TOP + 1, -WALL_IN, Blocks.AIR);
    }

    // ------------------------------------------------------------------ towers

    /** A square tower on the wall, centred at x on the wall's line; the walkway runs through it. */
    static void squareTower(Canvas n, int x, RandomSource r) {
        Canvas c = n.at(x, 0, -(WALL_IN + WALL_OUT) / 2 - 1);
        int h = 17;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                for (int y = -4; y <= h; y++) {
                    BlockState s;
                    if (edge || y == h || y < 0) s = Bk.stone(r);
                    else if (y == TOP) s = Bk.st(Blocks.SPRUCE_PLANKS);
                    else s = Bk.st(Blocks.AIR);
                    c.set(dx, y, dz, s);
                }
                if (edge) {
                    if ((dx + dz) % 2 == 0) {
                        c.set(dx, h + 1, dz, Bk.stone(r));
                        c.set(dx, h + 2, dz, Bk.slab(Blocks.STONE_BRICK_SLAB, false));
                    }
                }
            }
        }
        // the walkway through it, a door to the street, a ladder to the top
        c.clear(-4, TOP + 1, -1, -4, TOP + 2, 1);
        c.clear(4, TOP + 1, -1, 4, TOP + 2, 1);
        Bk.door(c, 0, 0, 4, Blocks.SPRUCE_DOOR, Direction.SOUTH, false);
        for (int y = 0; y <= h; y++) c.set(2, y, 3, Bk.ladder(Direction.NORTH));
        // slits and a banner on the outside, light inside
        for (int y : new int[] {4, 14}) {
            c.clear(0, y, -4, 0, y + 1, -4);
            c.clear(-4, y, -2, -4, y + 1, -2);
            c.clear(4, y, -2, 4, y + 1, -2);
        }
        c.set(0, 9, -5, Bk.banner(Blocks.PURPLE_WALL_BANNER, Direction.NORTH));
        c.set(0, TOP - 1, 0, Bk.lantern(true));
        c.set(0, h - 1, 0, Bk.lantern(true));
        // corner posts and a hipped roof over the top
        for (int dx : new int[] {-4, 4}) for (int dz : new int[] {-4, 4}) {
            for (int y = h + 1; y <= h + 3; y++) c.set(dx, y, dz, Bk.log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
        }
        hipRoof(c, -5, 5, -5, 5, h + 4, Roof.SLATE);
        c.set(0, h + 2, 0, Bk.lantern(true));
    }

    /** A round tower at a corner, drawn for the south-east corner (the walls leave it towards -x and -z). */
    static void roundTower(Canvas c, RandomSource r) {
        int h = 19;
        double outer = 7.4 * 7.4, inner = 5.4 * 5.4, merlon = 6.4 * 6.4;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                double d2 = dx * dx + dz * dz;
                if (d2 > outer) continue;
                boolean shell = d2 > inner;
                for (int y = -4; y <= h; y++) {
                    BlockState s;
                    if (shell || y == h || y < 0) s = Bk.stone(r);
                    else if (y == TOP) s = Bk.st(Blocks.SPRUCE_PLANKS);
                    else s = Bk.st(Blocks.AIR);
                    c.set(dx, y, dz, s);
                }
                if (d2 > merlon) {
                    int a = (int) Math.floor((Math.atan2(dz, dx) + Math.PI) / (Math.PI / 12));
                    if (a % 2 == 0) {
                        c.set(dx, h + 1, dz, Bk.stone(r));
                        c.set(dx, h + 2, dz, Bk.slab(Blocks.STONE_BRICK_SLAB, false));
                    }
                }
                // the walkways come in from the west and the north
                if (shell && ((Math.abs(dz) <= 1 && dx < 0) || (Math.abs(dx) <= 1 && dz < 0))) {
                    c.clear(dx, TOP + 1, dz, dx, TOP + 2, dz);
                }
                // arrow slits on the outside
                if (shell && (dx == 7 || dz == 7) && Math.abs(dx - dz) > 2 && Math.floorMod(dx + dz, 3) == 0) {
                    c.clear(dx, 5, dz, dx, 6, dz);
                }
            }
        }
        for (int y = TOP + 1; y <= h; y++) c.set(0, y, 5, Bk.ladder(Direction.NORTH));
        c.set(0, TOP - 1, 0, Bk.lantern(true));
        c.set(0, h - 1, 0, Bk.lantern(true));
        // a cone of slate on top, set back behind the merlons
        for (int k = 0; k <= 6; k++) {
            double rr = 6 - k;
            int y = h + 2 + k;
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    double d2 = dx * dx + dz * dz;
                    if (d2 > rr * rr + 0.6 || d2 <= (rr - 1) * (rr - 1) + 0.6 && rr > 0) continue;
                    if (rr == 0) {
                        if (dx == 0 && dz == 0) c.set(0, y, 0, Blocks.DEEPSLATE_TILES);
                        continue;
                    }
                    Direction up = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                    c.set(dx, y, dz, Bk.stairs(Blocks.DEEPSLATE_TILE_STAIRS, up, false));
                }
            }
        }
        c.set(0, h + 9, 0, Blocks.LIGHTNING_ROD);
    }

    /** A hipped roof over x0..x1, z0..z1 rising from y. */
    static void hipRoof(Canvas c, int x0, int x1, int z0, int z1, int y, Roof roof) {
        for (int k = 0; ; k++) {
            int a = x0 + k, b = x1 - k, p = z0 + k, q = z1 - k, yy = y + k;
            if (a > b || p > q) return;
            if (a == b || p == q) {
                c.fill(a, yy, p, b, yy, q, roof.full);
                c.set((a + b) / 2, yy + 1, (p + q) / 2, Blocks.LIGHTNING_ROD);
                return;
            }
            for (int x = a; x <= b; x++) {
                c.set(x, yy, p, Bk.stairs(roof.stairs, Direction.SOUTH, false));
                c.set(x, yy, q, Bk.stairs(roof.stairs, Direction.NORTH, false));
            }
            for (int z = p + 1; z < q; z++) {
                c.set(a, yy, z, Bk.stairs(roof.stairs, Direction.EAST, false));
                c.set(b, yy, z, Bk.stairs(roof.stairs, Direction.WEST, false));
            }
            if (a + 1 <= b - 1 && p + 1 <= q - 1) c.clear(a + 1, yy, p + 1, b - 1, yy, q - 1);
        }
    }

    // ------------------------------------------------------------------ the gate

    /** A gatehouse at x = 0 of a side: an arched passage with a raised portcullis between two towers. */
    static void gatehouse(Canvas n, RandomSource r) {
        Canvas g = n.at(0, 0, -(WALL_IN + WALL_OUT) / 2 - 1);
        int top = 15;
        for (int x = -8; x <= 8; x++) {
            for (int z = -5; z <= 5; z++) {
                for (int y = -4; y <= top; y++) g.set(x, y, z, Bk.stone(r));
                boolean edge = Math.abs(x) == 8 || Math.abs(z) == 5;
                if (edge && (x + z) % 2 == 0) {
                    g.set(x, top + 1, z, Bk.stone(r));
                    g.set(x, top + 2, z, Bk.slab(Blocks.STONE_BRICK_SLAB, false));
                }
            }
        }
        // the passage and its arch
        g.clear(-2, 0, -5, 2, 5, 5);
        for (int z = -5; z <= 5; z++) {
            g.set(-2, 6, z, Bk.stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
            g.set(2, 6, z, Bk.stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
            g.clear(-1, 6, z, 1, 6, z);
            g.set(-1, 7, z, Bk.stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
            g.set(1, 7, z, Bk.stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
            g.set(0, 7, z, Blocks.AIR);
            g.set(0, -1, z, Blocks.STONE_BRICKS);
            g.set(-1, -1, z, Blocks.POLISHED_ANDESITE);
            g.set(1, -1, z, Blocks.POLISHED_ANDESITE);
        }
        // the portcullis, raised into the arch
        for (int x = -2; x <= 2; x++) g.set(x, 5, -3, Blocks.IRON_BARS);
        for (int x = -1; x <= 1; x++) g.set(x, 6, -3, Blocks.IRON_BARS);
        g.set(0, 7, -3, Blocks.IRON_BARS);
        g.set(0, 7, 0, Bk.lantern(true));
        g.set(0, 7, 3, Bk.lantern(true));
        // the guard room over the passage, at the walkway's height, with doors to both walkways
        g.clear(-7, TOP + 1, -4, 7, top - 1, 4);
        g.fill(-7, TOP, -4, 7, TOP, 4, Blocks.SPRUCE_PLANKS);
        g.clear(-8, TOP + 1, -1, -8, TOP + 2, 1);
        g.clear(8, TOP + 1, -1, 8, TOP + 2, 1);
        for (int x : new int[] {-4, 4}) {
            g.clear(x, TOP + 2, -5, x, TOP + 3, -5);
            g.set(x, TOP + 2, 5, Blocks.GLASS_PANE);
            g.set(x, TOP + 3, 5, Blocks.GLASS_PANE);
        }
        for (int y = TOP + 1; y <= top; y++) g.set(6, y, 4, Bk.ladder(Direction.NORTH));
        g.set(0, top - 1, 0, Bk.lantern(true));
        g.set(-4, TOP + 1, 3, Blocks.BARREL);
        g.set(-5, TOP + 1, 3, Blocks.BARREL);
        g.set(-3, TOP + 1, 0, Blocks.CRAFTING_TABLE);
        // banners above the arch, lamp posts on both sides of it, inside and out
        for (int x : new int[] {-3, 0, 3}) g.set(x, 10, -6, Bk.banner(Blocks.PURPLE_WALL_BANNER, Direction.NORTH));
        for (int x : new int[] {-3, 3}) g.set(x, 9, 6, Bk.banner(Blocks.PURPLE_WALL_BANNER, Direction.SOUTH));
        for (int z : new int[] {-6, 6}) for (int x : new int[] {-3, 3}) {
            g.set(x, 0, z, Blocks.STONE_BRICK_WALL);
            g.set(x, 1, z, Blocks.STONE_BRICK_WALL);
            g.set(x, 2, z, Bk.lantern(false));
        }
        // the two towers in front, higher than the gatehouse, with roofs
        for (int side : new int[] {-1, 1}) {
            int xa = side < 0 ? -9 : 4, xb = side < 0 ? -4 : 9;
            for (int x = xa; x <= xb; x++) {
                for (int z = -8; z <= -2; z++) {
                    for (int y = -4; y <= 19; y++) g.set(x, y, z, Bk.stone(r));
                    boolean edge = x == xa || x == xb || z == -8 || z == -2;
                    if (edge && (x + z) % 2 == 0) g.set(x, 20, z, Bk.stone(r));
                }
            }
            for (int y : new int[] {5, 12}) g.clear((xa + xb) / 2, y, -8, (xa + xb) / 2, y + 1, -8);
            for (int x = xa; x <= xb; x += xb - xa) for (int z = -8; z <= -2; z += 6) {
                for (int y = 20; y <= 21; y++) g.set(x, y, z, Bk.log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
            }
            hipRoof(g, xa - 1, xb + 1, -9, -1, 22, Roof.SLATE);
            g.set((xa + xb) / 2, 7, -9, Bk.banner(Blocks.PURPLE_WALL_BANNER, Direction.NORTH));
        }
    }
}
