package com.divinis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.divinis.boss.WildfireFireball;
import com.divinis.Divinis;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** The Wildfire's fireball (FireFx): a white-hot core in churning flame and a halo, a burning tail behind it. */
public class WildfireFireballRenderer extends EntityRenderer<WildfireFireball> {
    public WildfireFireballRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(WildfireFireball ball) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/effect/wildfire_glow.png");
    }

    @Override
    public void render(WildfireFireball ball, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Camera cam = entityRenderDispatcher.camera;
        float ticks = ball.tickCount + partialTick, time = FireFx.seconds(ticks);
        float pulse = 0.9F + 0.1F * Mth.sin(ticks * 1.3F);
        Vec3 mid = new Vec3(0, 0.4, 0);
        // the tail: fading glows back along its way
        Vec3 back = new Vec3(ball.xo - ball.getX(), ball.yo - ball.getY(), ball.zo - ball.getZ());
        if (back.lengthSqr() > 1.0E-4) {
            back = back.normalize();
            for (int i = 1; i <= 7; i++) {
                float k = 1 - i / 8.0F;
                Vec3 at = mid.add(back.scale(i * 0.38));
                FireFx.glow(pose, buffers, cam, at, 1.5F * k, 0.9F * k, 0.45F * k, 0.12F * k);
                if (i % 2 == 1) FireFx.flame(pose, buffers, cam, at.add(0, -0.4 * k, 0), 0.8F * k, 1.0F * k, time + i, i, 1.0F, 0.6F, 0.3F, k);
            }
        }
        FireFx.glow(pose, buffers, cam, mid, 2.6F * pulse, 0.7F, 0.35F, 0.1F);
        for (int i = 0; i < 3; i++) {
            float a = i * Mth.TWO_PI / 3 + time * 4;
            FireFx.flame(pose, buffers, cam, mid.add(Mth.cos(a) * 0.15, -0.55, Mth.sin(a) * 0.15), 1.1F, 1.3F * pulse, time + i * 0.3F, i,
                    1.0F, 0.7F, 0.4F, 1.0F);
        }
        FireFx.glow(pose, buffers, cam, mid, 0.9F * pulse, 1.0F, 0.95F, 0.8F);
        super.render(ball, yaw, partialTick, pose, buffers, light);
    }
}
