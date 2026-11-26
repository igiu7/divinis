package com.divinis.city;

import java.util.Arrays;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Where the city goes: near the world spawn, on the flattest dry land found. Asked from the world generator only
 * (no chunk is generated), so it is quick enough at the first start of the server.
 */
final class Site {
    private static final int STEP = 160;     // between two places looked at
    private static final int RINGS = 10;     // how far out (rings of STEP)
    private static final int SAMPLES = 9;    // per side of the city, for one place

    private Site() {}

    private record Look(int water, double rough, int floor) {}

    /** The centre of the city's square at street level. */
    static BlockPos choose(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int ring = 0; ring <= RINGS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int x = spawn.getX() + dx * STEP, z = spawn.getZ() + dz * STEP;
                    if (!level.getWorldBorder().isWithinBounds(x - CityPlan.EXTENT, z - CityPlan.EXTENT)
                            || !level.getWorldBorder().isWithinBounds(x + CityPlan.EXTENT, z + CityPlan.EXTENT)) continue;
                    Look look = look(level, x, z);
                    double score = look.water() * 40.0 + look.rough() * 3.0 + ring * 6.0;
                    if (score < bestScore) {
                        bestScore = score;
                        best = new BlockPos(x, look.floor(), z);
                    }
                }
            }
            // good enough: dry and fairly flat
            if (best != null && bestScore < 20 + ring * 6.0) break;
        }
        return best != null ? best : new BlockPos(spawn.getX(), Math.max(level.getSeaLevel() + 2, spawn.getY()), spawn.getZ());
    }

    private static Look look(ServerLevel level, int cx, int cz) {
        var gen = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        int n = SAMPLES * SAMPLES, water = 0;
        int[] heights = new int[n];
        int i = 0;
        for (int sx = 0; sx < SAMPLES; sx++) {
            for (int sz = 0; sz < SAMPLES; sz++) {
                int x = cx - CityPlan.WALL + sx * (2 * CityPlan.WALL) / (SAMPLES - 1);
                int z = cz - CityPlan.WALL + sz * (2 * CityPlan.WALL) / (SAMPLES - 1);
                int h = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                heights[i++] = h;
                var biome = level.getUncachedNoiseBiome(x >> 2, level.getSeaLevel() >> 2, z >> 2);
                if (h < level.getSeaLevel() || biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER)) water++;
            }
        }
        double mean = Arrays.stream(heights).average().orElse(64);
        double var = Arrays.stream(heights).mapToDouble(h -> (h - mean) * (h - mean)).average().orElse(0);
        int[] sorted = heights.clone();
        Arrays.sort(sorted);
        int floor = Math.max(level.getSeaLevel() + 2, sorted[n / 2]);
        return new Look(water, Math.sqrt(var), floor);
    }
}
