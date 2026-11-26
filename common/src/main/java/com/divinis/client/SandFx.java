package com.divinis.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.divinis.Divinis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Sand tornadoes (boss.SandTornado, and the Sphinx turning into one as he dies): a funnel of whirling sand - rings
 * from a narrow foot to a wide mouth, bending and swaying as it whirls, its wall the streaked sand_tornado.png turning
 * round it. Three walls one inside another, the inner darker and faster; the foot and the mouth fade out, and so does
 * the whole of it (alpha) as it rises or blows away.
 */
public final class SandFx {
    private SandFx() {}

    public static final RenderType WALL = RenderType.entityTranslucent(
            ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/effect/sand_tornado.png"));
    private static final int RINGS = 16, SIDES = 18;

    /**
     * A tornado standing on the pose's origin: height high, foot and mouth its radii there, turned by time (ticks),
     * swaying by sway (blocks at the mouth), alpha 0..1, light as given (the sand is lit as the place it stands in).
     */
    public static void tornado(PoseStack pose, MultiBufferSource buffers, float height, float foot, float mouth, float time, float sway,
            float alpha, int seed, int light) {
        if (alpha <= 0.01F) return;
        VertexConsumer vc = buffers.getBuffer(WALL);
        Matrix4f m = pose.last().pose();
        for (int wall = 0; wall < 3; wall++) {
            float scale = 1.0F - wall * 0.2F;
            float shade = 1.0F - wall * 0.16F;
            float turn = time * (0.05F + wall * 0.025F) + seed * 0.37F;
            float a0 = alpha * (wall == 0 ? 0.75F : 0.9F);
            float[][] ring = new float[RINGS + 1][3];
            for (int i = 0; i <= RINGS; i++) {
                float h = i / (float) RINGS;
                float r = (foot + (mouth - foot) * (float) Math.pow(h, 1.5)) * scale * (1 + 0.06F * Mth.sin(time * 0.3F + i * 0.9F + seed));
                float bend = sway * h * h;
                ring[i][0] = bend * Mth.sin(time * 0.045F + h * 2.5F + seed);
                ring[i][1] = bend * Mth.cos(time * 0.037F + h * 2.1F + seed * 1.3F);
                ring[i][2] = r;
            }
            for (int i = 0; i < RINGS; i++) {
                float h0 = i / (float) RINGS, h1 = (i + 1) / (float) RINGS;
                float e0 = edge(h0), e1 = edge(h1);
                for (int j = 0; j < SIDES; j++) {
                    float t0 = j / (float) SIDES, t1 = (j + 1) / (float) SIDES;
                    float c0 = Mth.cos(t0 * Mth.TWO_PI), s0 = Mth.sin(t0 * Mth.TWO_PI), c1 = Mth.cos(t1 * Mth.TWO_PI), s1 = Mth.sin(t1 * Mth.TWO_PI);
                    // the sand winds round faster at the foot, as a tornado's does
                    float u0 = t0 * 3 - turn * (1.6F - h0 * 0.8F), u1 = t1 * 3 - turn * (1.6F - h0 * 0.8F);
                    float u2 = t0 * 3 - turn * (1.6F - h1 * 0.8F), u3 = t1 * 3 - turn * (1.6F - h1 * 0.8F);
                    float v0 = h0 * height / 2.5F, v1 = h1 * height / 2.5F;
                    vertex(vc, pose, m, ring[i][0] + c0 * ring[i][2], h0 * height, ring[i][1] + s0 * ring[i][2], u0, v0, c0, s0, shade, a0 * e0, light);
                    vertex(vc, pose, m, ring[i][0] + c1 * ring[i][2], h0 * height, ring[i][1] + s1 * ring[i][2], u1, v0, c1, s1, shade, a0 * e0, light);
                    vertex(vc, pose, m, ring[i + 1][0] + c1 * ring[i + 1][2], h1 * height, ring[i + 1][1] + s1 * ring[i + 1][2], u3, v1, c1, s1, shade, a0 * e1, light);
                    vertex(vc, pose, m, ring[i + 1][0] + c0 * ring[i + 1][2], h1 * height, ring[i + 1][1] + s0 * ring[i + 1][2], u2, v1, c0, s0, shade, a0 * e1, light);
                }
            }
        }
    }

    /** The funnel fades in at the foot and out at the mouth. */
    private static float edge(float h) {
        return Mth.clamp(h / 0.12F, 0.35F, 1) * Mth.clamp((1 - h) / 0.25F, 0, 1);
    }

    private static void vertex(VertexConsumer vc, PoseStack pose, Matrix4f m, float x, float y, float z, float u, float v, float nx, float nz,
            float shade, float a, int light) {
        vc.addVertex(m, x, y, z).setColor(shade, shade * 0.96F, shade * 0.9F, Mth.clamp(a, 0, 1)).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(Math.max(light, LightTexture.pack(10, 10))).setNormal(pose.last(), nx, 0, nz);
    }
}
