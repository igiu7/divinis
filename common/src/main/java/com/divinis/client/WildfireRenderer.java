package com.divinis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.divinis.boss.Wildfire;
import com.divinis.Divinis;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Wildfire (model twice as big) and its fire (FireFx): under the helm, where a body would be, a column of
 * flame tongues of different sizes licking up into it around a white-hot core; a glow round the helm; sparks
 * swirling up. While it charges the Inferno the fire roars up and the shields burn too; stunned it smoulders; asleep
 * it is cold. Its eyes, runes and gems glow (glow texture).
 */
public class WildfireRenderer extends MobRenderer<Wildfire, WildfireModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/wildfire.png");
    private static final RenderType EYES = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/wildfire_glow.png"));
    static final float SCALE = 2.0F;

    public WildfireRenderer(EntityRendererProvider.Context context) {
        super(context, new WildfireModel(context.bakeLayer(WildfireModel.LAYER)), 1.2F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }

            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, Wildfire boss, float limbSwing, float limbSwingAmount,
                    float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                if (boss.burning()) super.render(pose, buffers, light, boss, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Wildfire boss) {
        return TEXTURE;
    }

    @Override
    protected void scale(Wildfire boss, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public void render(Wildfire boss, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(boss, yaw, partialTick, pose, buffers, light);
        if (!boss.burning() || boss.isInvisible()) return;

        // the model's space as the living entity renderer set it up (setupAnim has just run)
        PoseStack model = new PoseStack();
        model.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, boss.yBodyRotO, boss.yBodyRot)));
        model.scale(-1.0F, -1.0F, 1.0F);
        model.scale(SCALE, SCALE, SCALE);
        model.translate(0.0F, -1.501F, 0.0F);
        Vec3 head = getModel().headAt(model);

        Camera cam = entityRenderDispatcher.camera;
        float ticks = boss.tickCount + partialTick, time = FireFx.seconds(ticks);
        boolean stunned = boss.state() == Wildfire.STUNNED, channel = boss.channeling();
        float power = stunned ? 0.35F : channel ? 1.6F : boss.state() == Wildfire.WAKING ? 0.8F : 1.0F;
        float flicker = 0.9F + 0.1F * Mth.sin(ticks * 0.7F) * Mth.sin(ticks * 0.31F + 1.0F);

        // the column under the helm: outer tongues first, then the hot core
        double drop = stunned ? 0.6 : 1.7 + (channel ? 0.6 : 0.0);
        Vec3 base = head.add(0, -drop, 0);
        float height = (float) drop + 0.5F * power;
        for (int i = 0; i < 6; i++) {
            float a = i * 1.05F + time * 1.3F;
            Vec3 at = base.add(Mth.cos(a) * 0.22F, -0.05F * i, Mth.sin(a) * 0.22F);
            float w = (1.4F - i * 0.08F) * power, h = height * (0.85F + 0.25F * FireFx.hash(i + 9)) * flicker;
            FireFx.flame(pose, buffers, cam, at, w, h, time + i * 0.37F, i, 1.0F, 0.55F + 0.05F * i, 0.3F, 0.75F);
        }
        FireFx.flame(pose, buffers, cam, base.add(0, 0.1, 0), 0.65F * power, height * 0.8F * flicker, time * 1.3F, 7, 1.0F, 0.95F, 0.8F, 1.1F);
        FireFx.glow(pose, buffers, cam, head, 3.4F * power * flicker, 0.55F, 0.32F, 0.12F);
        FireFx.glow(pose, buffers, cam, base.add(0, height * 0.35F, 0), 1.6F * power, 0.9F, 0.6F, 0.3F);
        FireFx.embers(pose, buffers, cam, base, 0.9F * power, 3.2F * power, channel ? 40 : 22, time, power);

        // the burning wheel: fire along the four beams, turning with them
        if (boss.sweepBurning(partialTick)) {
            float bodyYaw = Mth.rotLerp(partialTick, boss.yBodyRotO, boss.yBodyRot);
            float turn = boss.sweepTurn(partialTick);
            for (int k = 0; k < Wildfire.MAX_SHIELDS; k++) {
                float beam = (bodyYaw + 45 + 90 * k + turn) * Mth.DEG_TO_RAD;
                float fx = -Mth.sin(beam), fz = Mth.cos(beam);
                for (int d = 3; d <= 19; d += 2) {
                    Vec3 at = new Vec3(fx * d, -1.5, fz * d);
                    FireFx.flame(pose, buffers, cam, at, 1.3F, 1.6F + 0.4F * FireFx.hash(d * 7 + k), time + d * 0.13F, d + k, 1, 1, 1, 1);
                }
            }
        }

        // burning shields while it charges the Inferno or wheels them round
        if (channel || boss.isAttacking() && !stunned) {
            for (int i = 0; i < Wildfire.MAX_SHIELDS; i++) {
                if (!getModel().shieldShown(i)) continue;
                Vec3 s = getModel().shieldAt(model, i);
                FireFx.flame(pose, buffers, cam, s.add(0, -0.9, 0), 1.1F, 1.8F * flicker, time + i, i + 3, 1.0F, 0.6F, 0.3F, 0.85F);
                FireFx.glow(pose, buffers, cam, s, 1.8F, 0.6F, 0.3F, 0.1F);
            }
        }
    }
}
