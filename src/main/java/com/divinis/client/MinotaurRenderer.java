package com.divinis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.divinis.boss.Minotaur;
import com.divinis.Divinis;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Draws the half-size Minotaur model twice as big: about 4 blocks tall. */
public class MinotaurRenderer extends MobRenderer<Minotaur, MinotaurModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/minotaur.png");
    private static final float SCALE = 2.0F;

    public MinotaurRenderer(EntityRendererProvider.Context context) {
        super(context, new MinotaurModel(context.bakeLayer(MinotaurModel.LAYER)), 1.8F);
    }

    @Override
    public ResourceLocation getTextureLocation(Minotaur boss) {
        return TEXTURE;
    }

    @Override
    protected void scale(Minotaur boss, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }
}
