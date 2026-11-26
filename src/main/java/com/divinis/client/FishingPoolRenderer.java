package com.divinis.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.divinis.fishing.FishingPool;
import com.divinis.Divinis;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A fishing pool (fishing.FishingPool): a see-through disc of its tier's colour lying on the water, turning slowly,
 * two ripples running out of its middle; it grows in and fades out. Its name (in its colour, the vanilla name tag)
 * floats over it. Textures: tools/build_fishing_pool.py.
 * Drawn unlit (UNLIT: texture times colour, no lightmap), so it looks the same by day and by night, and night vision
 * or a fullbright mod (both change the lightmap) do not wash it out.
 */
public class FishingPoolRenderer extends EntityRenderer<FishingPool> {
    private static final ResourceLocation POOL = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/fishing_pool.png");
    private static final ResourceLocation RIPPLE = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/fishing_pool_ripple.png");
    private static final java.util.function.Function<ResourceLocation, RenderType> UNLIT = net.minecraft.Util.memoize(
            tex -> RenderType.create("divinis_unlit_" + tex.getPath(), DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                    256, false, true, RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setCullState(RenderStateShard.NO_CULL)
                            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                            .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                            .createCompositeState(false)));
    private static final float GROW = 20, FADE = 60; // ticks

    public FishingPoolRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FishingPool pool, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = pool.tickCount + partialTick;
        float left = pool.end() - (pool.level().getGameTime() + partialTick);
        float shown = Mth.clamp(age / GROW, 0, 1) * Mth.clamp(left / FADE, 0, 1);
        if (shown > 0.01F) {
            int c = pool.color();
            float r = (c >> 16 & 255) / 255F, g = (c >> 8 & 255) / 255F, b = (c & 255) / 255F;
            float size = FishingPool.RADIUS * (0.6F + 0.4F * easeOut(Mth.clamp(age / GROW, 0, 1)));
            float breathe = 0.92F + 0.08F * Mth.sin(age * 0.08F);

            pose.pushPose();
            pose.translate(0, 0.02, 0);
            pose.mulPose(Axis.YP.rotationDegrees(age * (pool.isWhirl() ? -6F : 0.6F))); // a whirlpool spins fast
            disc(pose, buffers.getBuffer(UNLIT.apply(POOL)), size, r, g, b, shown * breathe);
            pose.popPose();

            for (int i = 0; i < 2; i++) {
                float t = ((age / 50F) + i * 0.5F) % 1;
                if (pool.isWhirl()) t = 1 - t; // the ripples of a whirlpool run in
                pose.pushPose();
                pose.translate(0, 0.03 + i * 0.005, 0);
                disc(pose, buffers.getBuffer(UNLIT.apply(RIPPLE)), size * (0.15F + 0.85F * t),
                        r, g, b, shown * 0.9F * (1 - t) * Math.min(1, t * 4));
                pose.popPose();
            }
        }
        super.render(pool, yaw, partialTick, pose, buffers, light);
    }

    private static float easeOut(float t) {
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    /** A flat square, the texture's circle in it, facing up. */
    private static void disc(PoseStack pose, VertexConsumer out, float half, float r, float g, float b, float a) {
        PoseStack.Pose p = pose.last();
        vertex(out, p, -half, -half, 0, 0, r, g, b, a);
        vertex(out, p, -half, half, 0, 1, r, g, b, a);
        vertex(out, p, half, half, 1, 1, r, g, b, a);
        vertex(out, p, half, -half, 1, 0, r, g, b, a);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose p, float x, float z, float u, float v,
                               float r, float g, float b, float a) {
        out.addVertex(p, x, 0, z).setUv(u, v).setColor(r, g, b, a);
    }

    @Override
    public ResourceLocation getTextureLocation(FishingPool pool) {
        return POOL;
    }
}
