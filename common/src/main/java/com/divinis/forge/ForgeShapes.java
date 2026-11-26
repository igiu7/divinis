package com.divinis.forge;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.divinis.Divinis;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * What the anvil forges: the weapon parts of the rare metals ("<metal>_<part>", registry.ModWeapons), each cut to its
 * shape - the pixels of its 16x16 icon (resources divinis/forge_shapes.json, written by tools/build_weapon_parts.py).
 * The hot ingot hammered flat is the shape and a margin round it (BLANK_MARGIN pixels); the player cuts the margin away.
 * Grids are [row][column], row 0 at the top of the icon.
 */
public final class ForgeShapes {
    private ForgeShapes() {}

    public static final int SIZE = 16;
    /** How far the flattened blank reaches past the shape, pixels (each step one pixel up, down, left and right). */
    public static final int BLANK_MARGIN = 2;
    public static final List<String> METALS = List.of("blacksteel", "moonsilver", "crimsonite", "mithril", "etherite",
            "adamantite", "dragonsteel", "divinite", "valorium", "voidsteel");

    /** A part: its weapon, the ingots it takes and its pixels. */
    public record Shape(String part, String weapon, int ingots, boolean[][] pixels, boolean[][] blank) {}

    private static final Map<String, Shape> SHAPES = load();

    private static Map<String, Shape> load() {
        Map<String, Shape> out = new LinkedHashMap<>();
        try (InputStream in = ForgeShapes.class.getResourceAsStream("/divinis/forge_shapes.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                JsonArray rows = o.getAsJsonArray("rows");
                boolean[][] px = new boolean[SIZE][SIZE];
                for (int y = 0; y < SIZE; y++) {
                    String row = rows.get(y).getAsString();
                    for (int x = 0; x < SIZE; x++) px[y][x] = row.charAt(x) == '#';
                }
                out.put(e.getKey(), new Shape(e.getKey(), o.get("weapon").getAsString(), o.get("ingots").getAsInt(), px, grow(px)));
            }
        } catch (Exception ex) {
            Divinis.LOGGER.error("Could not read the forge shapes", ex);
        }
        return out;
    }

    private static boolean[][] grow(boolean[][] px) {
        boolean[][] out = px;
        for (int step = 0; step < BLANK_MARGIN; step++) {
            boolean[][] next = new boolean[SIZE][SIZE];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    next[y][x] = out[y][x] || (y > 0 && out[y - 1][x]) || (y < SIZE - 1 && out[y + 1][x])
                            || (x > 0 && out[y][x - 1]) || (x < SIZE - 1 && out[y][x + 1]);
                }
            }
            out = next;
        }
        return out;
    }

    /** The parts in the order the book lists them (sword blade first). */
    public static Map<String, Shape> all() { return SHAPES; }

    public static Shape shape(String part) { return SHAPES.get(part); }

    public static Item ingot(String metal) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, metal + "_ingot")).orElse(Items.AIR);
    }

    public static Item part(String metal, String part) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, metal + "_" + part)).orElse(Items.AIR);
    }

    /** The technology that teaches a metal's parts. */
    public static String tech(String metal) { return metal + "_weapons"; }

    /** The pixels kept, as 4 longs (row by row), and back. */
    public static long[] pack(boolean[][] kept) {
        long[] out = new long[4];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (kept[y][x]) {
                    int i = y * SIZE + x;
                    out[i >> 6] |= 1L << (i & 63);
                }
            }
        }
        return out;
    }

    public static boolean[][] unpack(long[] bits) {
        boolean[][] out = new boolean[SIZE][SIZE];
        if (bits.length < 4) return out;
        for (int i = 0; i < SIZE * SIZE; i++) out[i / SIZE][i % SIZE] = (bits[i >> 6] >>> (i & 63) & 1L) != 0;
        return out;
    }
}
