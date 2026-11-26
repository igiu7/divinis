package com.divinis.client.gui;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** An animated violet flame rising from the bottom of a rectangle, with embers drifting up out of it. */
final class Flame {
    private static final Color4I OUTER = Color4I.rgb(0x5a1fb0);
    private static final Color4I MID = Color4I.rgb(0x9b5cff);
    private static final Color4I CORE = Color4I.rgb(0xe9dcff);

    private Flame() {}

    /** The flame filling up to `height` (0..1) of the rectangle; `seed` keeps two flames from moving alike. */
    static void draw(GuiGraphics g, int x, int y, int w, int h, float height, float seed) {
        float t = System.currentTimeMillis() / 1000F + seed * 17F;
        int bottom = y + h;
        for (int i = 0; i < w; i += 2) {
            float u = (i + 1F) / w;
            float edge = Mth.sin(u * Mth.PI); // lower at the sides
            float n = 0.55F + 0.22F * Mth.sin(t * 3.1F + i * 0.45F + seed) + 0.15F * Mth.sin(t * 5.7F - i * 0.9F)
                    + 0.08F * Mth.sin(t * 9.3F + i * 1.7F);
            int fh = (int) (h * height * n * (0.35F + 0.65F * edge));
            if (fh <= 0) continue;
            int cw = Math.min(2, w - i);
            OUTER.withAlpha(110).draw(g, x + i, bottom - fh, cw, fh);
            int mh = (int) (fh * 0.62F);
            if (mh > 0) MID.withAlpha(150).draw(g, x + i, bottom - mh, cw, mh);
            int ch = (int) (fh * 0.28F);
            if (ch > 0) CORE.withAlpha(150).draw(g, x + i, bottom - ch, cw, ch);
        }
        // embers
        for (int k = 0; k < Math.max(4, w / 10); k++) {
            float speed = 0.35F + hash(k, 1) * 0.5F;
            float phase = (t * speed + hash(k, 2)) % 1F;
            int ex = x + (int) (hash(k, 3) * (w - 2) + Mth.sin(t * 2F + k) * 3F);
            int ey = bottom - 2 - (int) (phase * h * (0.6F + 0.4F * height));
            if (ex < x || ex > x + w - 2 || ey < y) continue;
            Color4I c = hash(k, 4) < 0.4F ? CORE : MID;
            c.withAlpha((int) (230 * (1F - phase))).draw(g, ex, ey, hash(k, 5) < 0.3F ? 2 : 1, hash(k, 5) < 0.3F ? 2 : 1);
        }
    }

    private static float hash(int i, int k) {
        int n = i * 374761393 + k * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 0xFFFF) / 65535F;
    }
}
