package com.divinis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.divinis.boss.SandTornado;
import com.divinis.Divinis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** A sand tornado (SandFx): it rises out of the ground, whirls, and blows away at the end of its life. */
public class SandTornadoRenderer extends EntityRenderer<SandTornado> {
    public SandTornadoRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(SandTornado tornado) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/effect/sand_tornado.png");
    }

    @Override
    public boolean shouldRender(SandTornado tornado, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        float s = tornado.size();
        AABB box = tornado.getBoundingBox().inflate(3 * s, 0, 3 * s).expandTowards(0, SandTornado.HEIGHT * s, 0);
        return frustum.isVisible(box);
    }

    @Override
    public void render(SandTornado tornado, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float k = tornado.strength(partialTick);
        float s = tornado.size();
        float ticks = tornado.tickCount + partialTick;
        // rising: it grows up out of the ground; blowing away: it widens and thins
        boolean dying = ticks > tornado.life() - SandTornado.FADE;
        float height = SandTornado.HEIGHT * s * (dying ? 1 + (1 - k) * 0.3F : 0.3F + 0.7F * k);
        float widen = dying ? 1 + (1 - k) * 0.8F : 1;
        SandFx.tornado(pose, buffers, height, 0.45F * s * widen, 2.6F * s * widen, ticks, 0.9F * s, k, tornado.getId(), light);
        super.render(tornado, yaw, partialTick, pose, buffers, light);
    }
}
