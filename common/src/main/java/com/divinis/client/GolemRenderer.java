package com.divinis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.divinis.boss.Golem;
import com.divinis.Divinis;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Draws the quarter-size Golem model four times as big (about 8 blocks); its eyes, core and runes glow once it is awake. */
public class GolemRenderer extends MobRenderer<Golem, GolemModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/golem.png");
    private static final RenderType GLOW =
            RenderType.eyes(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/golem_glow.png"));
    private static final float SCALE = 4.0F;

    public GolemRenderer(EntityRendererProvider.Context context) {
        super(context, new GolemModel(context.bakeLayer(GolemModel.LAYER)), 3.0F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }

            @Override
            public void render(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
                    int light, Golem boss, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                    float netHeadYaw, float headPitch) {
                if (boss.glowing()) super.render(pose, buffers, light, boss, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Golem boss) {
        return TEXTURE;
    }

    @Override
    protected void scale(Golem boss, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }
}
