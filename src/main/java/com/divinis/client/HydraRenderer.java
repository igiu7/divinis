package com.divinis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.divinis.boss.Hydra;
import com.divinis.Divinis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the Hydra twice its model's pixels; its eyes, the fire in the fire heads' throats, the acid heads' glands and
 * the ice heads' crystals glow (hydra_glow.png) once it is awake.
 */
public class HydraRenderer extends MobRenderer<Hydra, HydraModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/hydra.png");
    private static final RenderType GLOW = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/hydra_glow.png"));
    private static final float SCALE = 2.0F;

    public HydraRenderer(EntityRendererProvider.Context context) {
        super(context, new HydraModel(context.bakeLayer(HydraModel.LAYER)), 2.4F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }

            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, Hydra boss, float limbSwing, float limbSwingAmount,
                    float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                if (boss.state() != Hydra.SLEEPING) super.render(pose, buffers, light, boss, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Hydra boss) {
        return TEXTURE;
    }

    @Override
    protected void scale(Hydra boss, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    protected float getFlipDegrees(Hydra boss) {
        return 0;
    }
}
