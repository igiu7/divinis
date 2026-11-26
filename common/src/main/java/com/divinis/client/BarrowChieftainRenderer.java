package com.divinis.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.divinis.boss.BarrowChieftain;
import com.divinis.boss.BarrowSpear;
import com.divinis.Divinis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws the Barrow Chieftain 1.5 times his model's pixels (three blocks to his crest); the soul fire in his eyes and the
 * horses', and the runes on the chariot, glow (barrow_chieftain_glow.png) once he is awake. Spear draws his spear in
 * flight (boss.BarrowSpear): the spear of his model alone, turned along its flight.
 */
public class BarrowChieftainRenderer extends MobRenderer<BarrowChieftain, BarrowChieftainModel> {
    private static final ResourceLocation TEXTURE = tex("barrow_chieftain");
    private static final RenderType GLOW = RenderType.eyes(tex("barrow_chieftain_glow"));
    static final float SCALE = 1.5F;

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/" + name + ".png");
    }

    public BarrowChieftainRenderer(EntityRendererProvider.Context context) {
        super(context, new BarrowChieftainModel(context.bakeLayer(BarrowChieftainModel.LAYER)), 0.8F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }

            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, BarrowChieftain boss, float limbSwing,
                    float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                if (boss.glowing()) super.render(pose, buffers, light, boss, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(BarrowChieftain boss) {
        return TEXTURE;
    }

    @Override
    protected void scale(BarrowChieftain boss, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public void render(BarrowChieftain boss, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        shadowRadius = boss.chariotShown() ? 2.2F : 0.8F;
        super.render(boss, yaw, partialTick, pose, buffers, light);
    }

    @Override
    protected float getFlipDegrees(BarrowChieftain boss) {
        return 0; // his death is his own (no tipping over)
    }

    public static final class Spear extends EntityRenderer<BarrowSpear> {
        private final ModelPart spear;

        public Spear(EntityRendererProvider.Context context) {
            super(context);
            ModelPart root = context.bakeLayer(BarrowChieftainModel.LAYER);
            spear = root.getChild("body").getChild("torso").getChild("arm_r").getChild("forearm_r").getChild("spear");
            shadowRadius = 0;
        }

        @Override
        public ResourceLocation getTextureLocation(BarrowSpear entity) {
            return TEXTURE;
        }

        @Override
        public void render(BarrowSpear entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            float y = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot()) * Mth.DEG_TO_RAD;
            float x = Mth.lerp(partialTick, entity.xRotO, entity.getXRot()) * Mth.DEG_TO_RAD;
            Vector3f dir = new Vector3f(-Mth.sin(y) * Mth.cos(x), -Mth.sin(x), Mth.cos(y) * Mth.cos(x));
            pose.pushPose();
            pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir)); // its point along its flight
            pose.scale(-SCALE, -SCALE, SCALE);
            spear.resetPose();
            spear.x = 0;
            spear.y = 8; // its middle on the entity
            spear.z = 0;
            spear.xRot = spear.yRot = spear.zRot = 0;
            spear.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), Math.max(light, LightTexture.pack(10, 10)),
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffers, light);
        }
    }
}
