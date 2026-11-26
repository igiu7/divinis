package com.divinis.city;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What each shop on the square is furnished with, in front of its counter (the customers' side, z from 1 to cz - 1)
 * and behind it (where its NPC stands). dims: {w, d, cz}.
 */
final class Wares {
    private Wares() {}

    static Buildings.Shop shop(String role) {
        return switch (role) {
            case "guild_master" -> new Buildings.Shop(role, "Guild Hall", Wares::guild);
            case "mayor" -> new Buildings.Shop(role, "Town Hall", Wares::townHall);
            case "banker" -> new Buildings.Shop(role, "Bank", Wares::bank);
            case "librarian" -> new Buildings.Shop(role, "Library", Wares::library);
            case "innkeeper" -> new Buildings.Shop(role, "The Gilded Tankard", Wares::tavern);
            case "merchant" -> new Buildings.Shop(role, "General Store", Wares::store);
            case "baker" -> new Buildings.Shop(role, "Bakery", Wares::bakery);
            case "tailor" -> new Buildings.Shop(role, "Tailor", Wares::tailor);
            case "blacksmith" -> new Buildings.Shop(role, "Blacksmith", Wares::smithy);
            case "armorer" -> new Buildings.Shop(role, "Armourer", Wares::armorer);
            case "alchemist" -> new Buildings.Shop(role, "Alchemist", Wares::alchemist);
            case "healer" -> new Buildings.Shop(role, "Healer", Wares::healer);
            case "gate_innkeeper" -> new Buildings.Shop(role, "The Last Gate Inn", Wares::tavern);
            case "captain" -> new Buildings.Shop(role, "Barracks", Wares::barracks);
            default -> new Buildings.Shop(role, role, (c, d) -> {});
        };
    }

    private static BlockState cauldron() {
        return Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
    }

    private static BlockState facing(Block b, Direction d) {
        return b.defaultBlockState().setValue(FurnaceBlock.FACING, d);
    }

    private static BlockState candles() {
        return Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true);
    }

    /** Along both side walls in front of the counter, the blocks in turn. */
    private static void sides(Canvas c, int[] d, Block... blocks) {
        int w = d[0], cz = d[2], i = 0;
        for (int z = 1; z < cz - 1; z++) {
            c.set(1, 0, z, blocks[i % blocks.length]);
            c.set(w - 2, 0, z, blocks[(i + 1) % blocks.length]);
            i++;
        }
    }

    /** Banners on the inside of both side walls. */
    private static void banners(Canvas c, int[] d, Block banner) {
        int w = d[0], cz = d[2];
        for (int z = 2; z < cz - 1; z += 3) {
            c.set(1, 2, z, Bk.banner(banner, Direction.EAST));
            c.set(w - 2, 2, z, Bk.banner(banner, Direction.WEST));
        }
    }

    /** A table (fence and plate) with a chair on each side, at x, z. */
    private static void table(Canvas c, int x, int z) {
        c.set(x, 0, z, Blocks.DARK_OAK_FENCE);
        c.set(x, 1, z, Blocks.OAK_PRESSURE_PLATE);
        c.set(x - 1, 0, z, Bk.stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
        c.set(x + 1, 0, z, Bk.stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
    }

    private static void guild(Canvas c, int[] d) {
        int w = d[0], cz = d[2];
        sides(c, d, Blocks.BOOKSHELF, Blocks.CHEST, Blocks.BOOKSHELF, Blocks.BARREL);
        banners(c, d, Blocks.PURPLE_WALL_BANNER);
        c.set(3, 0, cz - 2, Blocks.CARTOGRAPHY_TABLE);
        c.set(w - 4, 0, cz - 2, Blocks.LECTERN);
        table(c, 4, 3);
        table(c, w - 5, 3);
        behind(c, d, Blocks.LECTERN);
    }

    private static void townHall(Canvas c, int[] d) {
        int w = d[0], cz = d[2];
        sides(c, d, Blocks.BOOKSHELF, Blocks.SPRUCE_PLANKS);
        banners(c, d, Blocks.RED_WALL_BANNER);
        c.set(w / 2 - 2, 0, cz - 2, Blocks.BELL);
        c.set(w / 2 + 2, 0, cz - 2, Blocks.LECTERN);
        for (int z = 2; z < cz - 2; z += 2) {
            c.set(3, 0, z, Bk.stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
            c.set(w - 4, 0, z, Bk.stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
        }
        behind(c, d, Blocks.LECTERN);
    }

    private static void bank(Canvas c, int[] d) {
        int w = d[0], cz = d[2];
        for (int x = 2; x <= w - 4; x++) c.set(x, 1, cz, Blocks.IRON_BARS);
        sides(c, d, Blocks.CHISELED_STONE_BRICKS, Blocks.SPRUCE_PLANKS);
        for (int x = 1; x <= w - 3; x += 2) c.set(x, 0, d[1] - 3, Blocks.CHEST);
        c.set(2, 0, 2, Bk.stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
        c.set(w - 3, 0, 2, Bk.stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
        behind(c, d, Blocks.BARREL);
    }

    private static void library(Canvas c, int[] d) {
        int w = d[0], cz = d[2];
        for (int z = 1; z < cz - 1; z++) for (int y = 0; y <= 2; y++) {
            c.set(1, y, z, Blocks.BOOKSHELF);
            c.set(w - 2, y, z, Blocks.BOOKSHELF);
        }
        for (int z = 2; z < cz - 2; z += 3) {
            c.set(4, 0, z, Blocks.LECTERN);
            c.set(w - 5, 0, z, Blocks.CHISELED_BOOKSHELF);
            c.set(w - 5, 1, z, candles());
        }
        behind(c, d, Blocks.BOOKSHELF);
    }

    private static void tavern(Canvas c, int[] d) {
        int w = d[0], cz = d[2];
        for (int z = 1; z < cz - 1; z++) {
            BlockState keg = Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.EAST);
            c.set(1, 0, z, keg);
            if (z % 2 == 1) c.set(1, 1, z, keg);
        }
        for (int z = 2; z < cz - 2; z += 3) {
            table(c, 4, z);
            if (w > 12) table(c, w - 5, z);
        }
        c.set(w / 2 - 2, 1, cz, Blocks.BREWING_STAND);
        c.set(w / 2 + 2, 1, cz, cauldron());
        c.set(w - 2, 0, cz - 2, Bk.campfire());
        behind(c, d, Blocks.BARREL);
    }

    private static void store(Canvas c, int[] d) {
        sides(c, d, Blocks.BARREL, Blocks.HAY_BLOCK, Blocks.CHEST, Blocks.COMPOSTER, Blocks.BROWN_WOOL);
        c.set(3, 0, 3, Blocks.PUMPKIN);
        c.set(4, 0, 3, Blocks.MELON);
        c.set(d[0] - 4, 0, 3, Blocks.BARREL);
        c.set(d[0] - 4, 1, 3, Blocks.BARREL);
        behind(c, d, Blocks.CHEST);
    }

    private static void bakery(Canvas c, int[] d) {
        int w = d[0], cz = d[2];
        sides(c, d, Blocks.HAY_BLOCK, Blocks.BARREL);
        c.set(w / 2 - 2, 1, cz, Blocks.CAKE);
        for (int x = 2; x <= w - 4; x += 2) c.set(x, 0, d[1] - 3, facing(Blocks.SMOKER, Direction.NORTH));
        c.set(3, 0, 3, Blocks.HAY_BLOCK);
        behind(c, d, Blocks.FURNACE);
    }

    private static void tailor(Canvas c, int[] d) {
        sides(c, d, Blocks.LOOM, Blocks.WHITE_WOOL, Blocks.PURPLE_WOOL, Blocks.LOOM, Blocks.LIGHT_BLUE_WOOL, Blocks.RED_WOOL);
        banners(c, d, Blocks.MAGENTA_WALL_BANNER);
        c.set(4, 0, 3, Blocks.LOOM);
        c.set(d[0] - 5, 0, 3, Blocks.CARTOGRAPHY_TABLE);
        behind(c, d, Blocks.WHITE_WOOL);
    }

    private static void smithy(Canvas c, int[] d) {
        int w = d[0], cz = d[2];
        sides(c, d, Blocks.ANVIL, Blocks.BARREL, Blocks.GRINDSTONE, Blocks.SMITHING_TABLE);
        c.set(3, 0, cz - 2, facing(Blocks.BLAST_FURNACE, Direction.SOUTH));
        c.set(4, 0, cz - 2, Blocks.LAVA_CAULDRON);
        c.set(w - 4, 0, cz - 2, Blocks.ANVIL);
        for (int x = 3; x <= w - 4; x += 4) {
            c.set(x, 2, 2, Blocks.CHAIN);
        }
        behind(c, d, Blocks.BLAST_FURNACE);
    }

    private static void armorer(Canvas c, int[] d) {
        sides(c, d, Blocks.SMITHING_TABLE, Blocks.IRON_BARS, Blocks.BARREL, Blocks.GRINDSTONE);
        banners(c, d, Blocks.GRAY_WALL_BANNER);
        c.set(3, 0, 3, Blocks.ANVIL);
        c.set(d[0] - 4, 0, 3, Blocks.ANVIL);
        behind(c, d, Blocks.SMITHING_TABLE);
    }

    private static void alchemist(Canvas c, int[] d) {
        int w = d[0], cz = d[2];
        sides(c, d, Blocks.BOOKSHELF, Blocks.FLOWERING_AZALEA, Blocks.BOOKSHELF, Blocks.POTTED_CRIMSON_FUNGUS);
        c.set(3, 0, 3, Blocks.BREWING_STAND);
        c.set(w - 4, 0, 3, cauldron());
        c.set(w / 2 - 2, 1, cz, Blocks.BREWING_STAND);
        c.set(w / 2 + 2, 1, cz, Blocks.AMETHYST_CLUSTER);
        c.set(3, 0, cz - 2, cauldron());
        behind(c, d, Blocks.BREWING_STAND);
    }

    private static void healer(Canvas c, int[] d) {
        int w = d[0], cz = d[2];
        for (int z = 1; z + 1 < cz - 1; z += 3) {
            Bk.bed(c, 1, 0, z + 1, Blocks.WHITE_BED, Direction.NORTH);
            Bk.bed(c, w - 2, 0, z + 1, Blocks.WHITE_BED, Direction.NORTH);
        }
        c.set(w / 2 - 2, 1, cz, Blocks.BREWING_STAND);
        c.set(w / 2 + 2, 1, cz, candles());
        c.set(4, 0, cz - 2, cauldron());
        behind(c, d, Blocks.BREWING_STAND);
    }

    private static void barracks(Canvas c, int[] d) {
        int w = d[0], cz = d[2];
        sides(c, d, Blocks.TARGET, Blocks.HAY_BLOCK, Blocks.GRINDSTONE, Blocks.BARREL);
        banners(c, d, Blocks.PURPLE_WALL_BANNER);
        c.set(4, 0, 3, Blocks.ANVIL);
        c.set(w - 5, 0, 3, Blocks.SMITHING_TABLE);
        c.set(w / 2, 1, cz, Blocks.LECTERN);
        behind(c, d, Blocks.BARREL);
    }

    /** Two of the shop's work blocks behind the counter, beside where its NPC stands. */
    private static void behind(Canvas c, int[] d, Block b) {
        int w = d[0], cz = d[2];
        c.set(w / 2 - 2, 0, cz + 2, b);
        c.set(w / 2 + 2, 0, cz + 2, b);
    }
}
