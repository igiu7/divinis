package com.divinis.boss;

import java.util.ArrayList;
import java.util.List;

/**
 * The shapes of Nocthariel's trial (a player must draw one) and how close a drawing comes to one. A shape is a few
 * strokes, each a line through points (x, y in -1..1, y down). How close: both are cut into evenly spaced points
 * along their lines, each squeezed into the same square (so size, place and how wide or tall it was drawn do not
 * matter, the shape does), then the mean distance from every point of one to the nearest point of the other, both
 * ways (0 the same; a fair hand drawing comes to about 0.05, another shape to 0.09 and more).
 */
public final class DrawShapes {
    private DrawShapes() {}

    public static final float PASS = 0.07F;
    private static final int SAMPLES = 80;

    public record Shape(String name, float[][] strokes) {}

    public static final List<Shape> SHAPES = new ArrayList<>();

    static {
        SHAPES.add(new Shape("star", new float[][] {star()}));
        SHAPES.add(new Shape("triangle", new float[][] {{0, -1, 0.9F, 0.75F, -0.9F, 0.75F, 0, -1}}));
        SHAPES.add(new Shape("spiral", new float[][] {spiral()}));
        SHAPES.add(new Shape("eye", new float[][] {eyeLids(), circle(0, 0, 0.32F, 20)}));
        SHAPES.add(new Shape("lightning", new float[][] {{0.3F, -1, -0.35F, 0.05F, 0.25F, 0.0F, -0.3F, 1}}));
        SHAPES.add(new Shape("crescent", new float[][] {crescent()}));
        SHAPES.add(new Shape("hourglass", new float[][] {{-0.7F, -1, 0.7F, -1, -0.7F, 1, 0.7F, 1, -0.7F, -1}}));
        SHAPES.add(new Shape("trident", new float[][] {{0, 1, 0, -1}, {-0.7F, -0.9F, -0.7F, -0.2F, 0.7F, -0.2F, 0.7F, -0.9F}}));
    }

    private static float[] star() {
        float[] p = new float[22];
        for (int i = 0; i < 11; i++) {
            double a = -Math.PI / 2 + i * 4 * Math.PI / 5;
            p[i * 2] = (float) Math.cos(a);
            p[i * 2 + 1] = (float) Math.sin(a);
        }
        return p;
    }

    private static float[] spiral() {
        float[] p = new float[84];
        for (int i = 0; i < 42; i++) {
            double t = i * 0.3;
            p[i * 2] = (float) (Math.cos(t) * t / 12);
            p[i * 2 + 1] = (float) (Math.sin(t) * t / 12);
        }
        return p;
    }

    private static float[] circle(float cx, float cy, float r, int n) {
        float[] p = new float[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            double a = i * Math.PI * 2 / n;
            p[i * 2] = cx + (float) Math.cos(a) * r;
            p[i * 2 + 1] = cy + (float) Math.sin(a) * r;
        }
        return p;
    }

    private static float[] eyeLids() {
        float[] p = new float[(2 * 12 + 1) * 2];
        int n = 0;
        for (int i = 0; i <= 12; i++) {
            float x = -1 + i / 6.0F;
            p[n++] = x;
            p[n++] = -0.55F * (1 - x * x);
        }
        for (int i = 11; i >= 0; i--) {
            float x = -1 + i / 6.0F;
            p[n++] = x;
            p[n++] = 0.55F * (1 - x * x);
        }
        return p;
    }

    private static float[] crescent() {
        List<Float> p = new ArrayList<>();
        for (int i = 0; i <= 16; i++) {
            double a = Math.PI / 2 + i * Math.PI / 16;
            p.add((float) Math.cos(a));
            p.add((float) Math.sin(a));
        }
        for (int i = 16; i >= 0; i--) {
            double a = Math.PI / 2 + i * Math.PI / 16;
            p.add((float) Math.cos(a) * 0.45F - 0.15F);
            p.add((float) Math.sin(a) * 0.8F);
        }
        float[] out = new float[p.size()];
        for (int i = 0; i < out.length; i++) out[i] = p.get(i);
        return out;
    }

    /** How far a drawing is from a shape (lower is closer); a drawing with (almost) nothing in it is far. */
    public static float distance(float[][] shape, List<float[]> drawing) {
        float[][] a = normalise(sample(List.of(shape)));
        float[][] b = normalise(sample(drawing));
        if (a.length == 0 || b.length < 8) return 10;
        return (mean(a, b) + mean(b, a)) / 2;
    }

    /** Evenly spaced points along all the strokes. */
    private static float[][] sample(List<float[]> strokes) {
        double total = 0;
        for (float[] s : strokes) for (int i = 2; i + 1 < s.length; i += 2) total += Math.hypot(s[i] - s[i - 2], s[i + 1] - s[i - 1]);
        if (total < 1.0E-6) return new float[0][];
        List<float[]> out = new ArrayList<>();
        for (float[] s : strokes) {
            for (int i = 2; i + 1 < s.length; i += 2) {
                double len = Math.hypot(s[i] - s[i - 2], s[i + 1] - s[i - 1]);
                int n = (int) Math.max(1, Math.round(SAMPLES * len / total));
                for (int j = 0; j < n; j++) {
                    float t = j / (float) n;
                    out.add(new float[] {s[i - 2] + (s[i] - s[i - 2]) * t, s[i - 1] + (s[i + 1] - s[i - 1]) * t});
                }
            }
        }
        return out.toArray(new float[0][]);
    }

    /** Squeezed into the same square: middle at 0, each way 1 across (a very thin one is not stretched out). */
    private static float[][] normalise(float[][] p) {
        if (p.length == 0) return p;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] q : p) {
            minX = Math.min(minX, q[0]);
            maxX = Math.max(maxX, q[0]);
            minY = Math.min(minY, q[1]);
            maxY = Math.max(maxY, q[1]);
        }
        float m = Math.max(maxX - minX, maxY - minY);
        if (m < 1.0E-5) return new float[0][];
        float sx = Math.max(maxX - minX, m * 0.25F), sy = Math.max(maxY - minY, m * 0.25F);
        float cx = (maxX + minX) / 2, cy = (maxY + minY) / 2;
        float[][] out = new float[p.length][];
        for (int i = 0; i < p.length; i++) out[i] = new float[] {(p[i][0] - cx) / sx, (p[i][1] - cy) / sy};
        return out;
    }

    private static float mean(float[][] from, float[][] to) {
        double sum = 0;
        for (float[] p : from) {
            double best = Double.MAX_VALUE;
            for (float[] q : to) best = Math.min(best, (p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]));
            sum += Math.sqrt(best);
        }
        return (float) (sum / from.length);
    }
}
