package com.divinis.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.divinis.Divinis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A black hole, for every boss that opens one (Aurelion's, the Serpent of Infinity's rift): a disc of swirling light
 * round a black core, the light bent into a bright ring at its edge, sparks falling in - and space bending round it
 * (ScreenFxClient.lens: the picture round its edge is pulled in, as light round a real one).
 */
public final class BlackHoleFx {
    private BlackHoleFx() {}

    private static final RenderType HOLE = RenderType.entityTranslucent(tex("black_hole"));
    private static final RenderType RING = RenderType.eyes(tex("photon_ring"));
    private static final RenderType DISC = RenderType.eyes(tex("accretion"));

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/effect/" + name + ".png");
    }

    /**
     * Draws one at `at` (relative to the pose; `world` is the same place in the world), `radius` the black core's
     * radius, k how far open (0..1), in GOLD, VIOLET or CRIMSON.
     */
    public static final int GOLD = 0, VIOLET = 1, CRIMSON = 2;

    public static void draw(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 at, Vec3 world, float radius, float k, float age,
            int tint) {
        if (k <= 0.01F) return;
        float r = radius * k;
        float[][] disc = {{1.0F, 0.9F, 0.75F}, {0.75F, 0.4F, 1.0F}, {1.0F, 0.18F, 0.16F}};
        float[][] halo = {{0.45F, 0.3F, 0.08F}, {0.35F, 0.12F, 0.5F}, {0.5F, 0.03F, 0.05F}};
        float dr = disc[tint][0], dg = disc[tint][1], db = disc[tint][2];
        BeamFx.glow(pose, buffers, cam, at, r * 7, halo[tint][0] * k, halo[tint][1] * k, halo[tint][2] * k);
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Axis.XP.rotationDegrees(14));
        pose.mulPose(Axis.YP.rotationDegrees(age * 2.4F));
        flat(pose, buffers.getBuffer(DISC), r * 5.5F, dr * k, dg * k, db * k);
        pose.popPose();
        billboard(pose, buffers.getBuffer(HOLE), at, r * 2.0F, 1, 1, 1, 1);
        billboard(pose, buffers.getBuffer(RING), at, r * 2.26F, dr * k, dg * k, db * k, 1);
        for (int i = 0; i < 28; i++) {
            float f = (age * 0.012F + i / 28.0F) % 1.0F;
            float a = i * 2.39996F + f * 9.0F;
            float d = r * 4.2F * (1 - f);
            Vec3 p = at.add(Mth.cos(a) * d, Mth.sin(i * 1.3F) * (1 - f) * r * 0.8F, Mth.sin(a) * d);
            BeamFx.glow(pose, buffers, cam, p, 0.9F * (1 - f * 0.5F), f * dr, f * dg * 0.8F, f * db * 0.5F);
        }
        ScreenFxClient.lens(world, r, 0.9F * k);
    }

    private static void flat(PoseStack pose, VertexConsumer vc, float size, float r, float g, float b) {
        Matrix4f m = pose.last().pose();
        float h = size / 2;
        float[][] c = {{-h, -h, 0, 0}, {h, -h, 1, 0}, {h, h, 1, 1}, {-h, h, 0, 1}};
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] q = c[pass == 0 ? i : 3 - i];
                vc.addVertex(m, q[0], 0, q[1]).setColor(r, g, b, 1).setUv(q[2], q[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 1, 0);
            }
        }
    }

    private static void billboard(PoseStack pose, VertexConsumer vc, Vec3 at, float size, float r, float g, float b, float a) {
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        Matrix4f m = pose.last().pose();
        float h = size / 2;
        float[][] c = {{-h, -h, 0, 1}, {h, -h, 1, 1}, {h, h, 1, 0}, {-h, h, 0, 0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] q = c[pass == 0 ? i : 3 - i];
                vc.addVertex(m, q[0], q[1], 0).setColor(r, g, b, a).setUv(q[2], q[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 0, 1);
            }
        }
        pose.popPose();
    }
}
