package com.divinis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.divinis.dungeon.DungeonPortal;
import com.divinis.Divinis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Draws the portal glowing (full light, see-through swirl); it grows when it opens and shrinks before it closes. */
public class DungeonPortalRenderer extends EntityRenderer<DungeonPortal> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/dungeon_portal.png");

    private final DungeonPortalModel model;

    public DungeonPortalRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new DungeonPortalModel(context.bakeLayer(DungeonPortalModel.LAYER));
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(DungeonPortal portal) {
        return TEXTURE;
    }

    @Override
    public void render(DungeonPortal portal, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = portal.tickCount + partialTick;
        float size = Math.min(1.0F, age / DungeonPortal.GROW_TICKS);
        int left = portal.remaining();
        if (left >= 0) size = Math.min(size, Math.max(0, (left - partialTick) / DungeonPortal.GROW_TICKS));
        if (size > 0.01F) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, portal.yRotO, portal.getYRot())));
            pose.translate(0, 1.5, 0); // grow from the middle of the ring
            pose.scale(size, size, size);
            pose.translate(0, -1.5, 0);
            pose.scale(-1.0F, -1.0F, 1.0F);
            pose.translate(0, -1.501, 0);
            model.setupAnim(portal, 0, 0, age, 0, 0);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        super.render(portal, yaw, partialTick, pose, buffers, light);
    }
}
