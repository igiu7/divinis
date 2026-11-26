package com.divinis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.divinis.skill.SkillCamp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Wanderer's camp (skill.SkillCamp): its block model (tools/models/build_skill_camp.py) twice its size, centred
 * on the entity - about two blocks across; it grows out of the ground when set up and sinks back when it goes out.
 */
public class SkillCampRenderer extends EntityRenderer<SkillCamp> {
    public SkillCampRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SkillCamp camp, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = camp.tickCount + partialTick;
        float left = camp.end() - (camp.level().getGameTime() + partialTick);
        float k = Mth.clamp(age / 10, 0, 1) * Mth.clamp(left / 20, 0, 1);
        if (k <= 0.01F) return;
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.scale(2 * k, 2 * k, 2 * k);
        pose.translate(-0.5, 0, -0.5);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), null,
                com.divinis.platform.client.ClientServices.extraModel(SkillFx.CAMP), 1, 1, 1, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(camp, yaw, partialTick, pose, buffers, light);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(SkillCamp camp) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
