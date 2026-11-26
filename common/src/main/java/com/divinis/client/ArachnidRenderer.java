package com.divinis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.divinis.boss.Arachnid;
import com.divinis.Divinis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws the half-size Arachnid model twice as big (2.3 times in phase 2, growing as it changes): dark brown in phase 1,
 * near black with blood-red markings in phase 2 (arachnid_p2.png), the markings and the eyes then glowing
 * (arachnid_p2_glow.png). While it hangs, the silk thread runs from its spinnerets up to where it is fastened.
 */
public class ArachnidRenderer extends MobRenderer<Arachnid, ArachnidModel> {
    private static final ResourceLocation TEXTURE = tex("arachnid"), TEXTURE_P2 = tex("arachnid_p2"), SILK = tex("arachnid_silk");
    private static final RenderType GLOW = RenderType.eyes(tex("arachnid_p2_glow"));
    private static final float SCALE = 2.0F, SCALE_P2 = 2.3F;
    /** model pixels from its feet up to its spinnerets when it hangs head down */
    private static final float THREAD_FROM = 36;

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/" + name + ".png");
    }

    public ArachnidRenderer(EntityRendererProvider.Context context) {
        super(context, new ArachnidModel(context.bakeLayer(ArachnidModel.LAYER)), 2.6F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }

            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, Arachnid boss, float limbSwing, float limbSwingAmount,
                    float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                if (boss.phase() == 2) super.render(pose, buffers, light, boss, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Arachnid boss) {
        return boss.phase() == 2 ? TEXTURE_P2 : TEXTURE;
    }

    private static float scaleOf(Arachnid boss, float partialTick) {
        return Mth.lerp(boss.grown(partialTick), SCALE, SCALE_P2);
    }

    @Override
    protected void scale(Arachnid boss, PoseStack pose, float partialTick) {
        float s = scaleOf(boss, partialTick);
        pose.scale(s, s, s);
    }

    @Override
    public void render(Arachnid boss, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        shadowRadius = 1.3F * scaleOf(boss, partialTick);
        super.render(boss, yaw, partialTick, pose, buffers, light);
        float top = boss.threadTop();
        if (Float.isNaN(top) || boss.isDeadOrDying()) return;
        double y = Mth.lerp(partialTick, boss.yo, boss.getY());
        float from = THREAD_FROM / 16F * scaleOf(boss, partialTick);
        float to = (float) (top - y);
        if (to <= from) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SILK));
        PoseStack.Pose p = pose.last();
        float w = 0.05F;
        // two crossed strips, so the thread is seen from every side
        strip(vc, p, -w, 0, w, 0, from, to, light);
        strip(vc, p, 0, -w, 0, w, from, to, light);
    }

    private static void strip(VertexConsumer vc, PoseStack.Pose p, float x0, float z0, float x1, float z1, float y0, float y1, int light) {
        float v = (y1 - y0) * 2;
        vertex(vc, p, x0, y0, z0, 0, 0, light);
        vertex(vc, p, x1, y0, z1, 1, 0, light);
        vertex(vc, p, x1, y1, z1, 1, v, light);
        vertex(vc, p, x0, y1, z0, 0, v, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int light) {
        vc.addVertex(p, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(p, 0, 0, 1);
    }
}
