package com.divinis.client;

import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;
import com.divinis.boss.NamelessCreator;
import com.divinis.Divinis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

/**
 * The map that is the Nameless Creator's face: its arena seen from above, live, as a map item draws it (the colours of
 * the ground, shaded by its height, on parchment), with the players on it, the god in its middle, and the places it has
 * marked blinking red (the cartographer's columns, client.CreatorFx). When it is struck, for a moment a pixel of
 * magenta shows through. North is up.
 */
public final class CreatorMap {
    private CreatorMap() {}

    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "creator_map");
    private static final int SIZE = 64;
    /** blocks from its middle to its edge */
    private static final double REACH = NamelessCreator.ARENA + 4;
    private static final int PARCHMENT = rgb(222, 206, 160), EDGE = rgb(150, 118, 70);

    private static DynamicTexture texture;
    private static final int[] ground = new int[SIZE * SIZE];
    private static long groundAt = Long.MIN_VALUE, drawnAt = Long.MIN_VALUE;
    private static Vec3 groundHome = Vec3.ZERO;

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | b << 16 | g << 8 | r; // NativeImage's ABGR
    }

    /** Brings the map up to date for this boss (once a tick at most), and gives its texture. */
    public static ResourceLocation update(NamelessCreator boss) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (texture == null) {
            texture = new DynamicTexture(SIZE, SIZE, true);
            mc.getTextureManager().register(TEXTURE, texture);
        }
        if (level == null) return TEXTURE;
        long now = level.getGameTime();
        if (now == drawnAt) return TEXTURE;
        drawnAt = now;
        Vec3 home = boss.homeSynced();
        if (now - groundAt > 100 || home.distanceToSqr(groundHome) > 1) {
            groundAt = now;
            groundHome = home;
            readGround(level, home);
        }
        NativeImage img = texture.getPixels();
        if (img == null) return TEXTURE;
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) img.setPixelRGBA(x, y, ground[y * SIZE + x]);
        // the places marked, blinking
        if ((now / 4) % 2 == 0) {
            for (Vec3 m : CreatorFx.marked(now)) dot(img, home, m, rgb(230, 30, 30), 1);
        }
        // the god in the middle, the players
        dot(img, home, boss.position(), rgb(240, 200, 60), 1);
        for (Player p : level.players()) dot(img, home, p.position(), rgb(255, 255, 255), 0);
        // struck: a pixel of magenta shows through
        if (boss.hurtTime > 7) {
            int x = 4 + level.random.nextInt(SIZE - 8), y = 4 + level.random.nextInt(SIZE - 8);
            img.setPixelRGBA(x, y, rgb(248, 0, 248));
        }
        texture.upload();
        return TEXTURE;
    }

    /** A dot (radius 0: one pixel, 1: a plus) at a place of the world. */
    private static void dot(NativeImage img, Vec3 home, Vec3 at, int color, int radius) {
        int x = (int) Math.floor((at.x - home.x) / REACH * SIZE / 2 + SIZE / 2.0);
        int y = (int) Math.floor((at.z - home.z) / REACH * SIZE / 2 + SIZE / 2.0);
        for (int[] d : radius == 0 ? new int[][] {{0, 0}} : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int px = x + d[0], py = y + d[1];
            if (px >= 1 && py >= 1 && px < SIZE - 1 && py < SIZE - 1) img.setPixelRGBA(px, py, color);
        }
    }

    /** The ground's colours as a map item has them, shaded by height (lighter where it rises to the north). */
    private static void readGround(ClientLevel level, Vec3 home) {
        int[] heights = new int[SIZE * SIZE];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int bx = Mth.floor(home.x + (x + 0.5 - SIZE / 2.0) / (SIZE / 2.0) * REACH);
                int bz = Mth.floor(home.z + (y + 0.5 - SIZE / 2.0) / (SIZE / 2.0) * REACH);
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
                heights[y * SIZE + x] = top;
                pos.set(bx, top, bz);
                BlockState state = level.getBlockState(pos);
                MapColor mc = state.getMapColor(level, pos);
                int north = y > 0 ? heights[(y - 1) * SIZE + x] : top;
                MapColor.Brightness light = top > north ? MapColor.Brightness.HIGH : top < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                int c = mc == MapColor.NONE ? PARCHMENT : mc.calculateRGBColor(light);
                ground[y * SIZE + x] = mix(c, PARCHMENT, 0.18F);
            }
        }
        for (int i = 0; i < SIZE; i++) { // the map's frame
            for (int[] p : List.of(new int[] {i, 0}, new int[] {i, SIZE - 1}, new int[] {0, i}, new int[] {SIZE - 1, i})) ground[p[1] * SIZE + p[0]] = EDGE;
        }
    }

    private static int mix(int abgr, int with, float k) {
        int r = (int) Mth.lerp(k, abgr & 255, with & 255), g = (int) Mth.lerp(k, abgr >> 8 & 255, with >> 8 & 255),
                b = (int) Mth.lerp(k, abgr >> 16 & 255, with >> 16 & 255);
        return 0xFF000000 | b << 16 | g << 8 | r;
    }
}
