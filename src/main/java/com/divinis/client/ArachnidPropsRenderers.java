package com.divinis.client;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.divinis.boss.ArachnidCocoon;
import com.divinis.boss.ArachnidEgg;
import com.divinis.boss.ArachnidMolt;
import com.divinis.Divinis;

import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * What the Arachnid leaves about (boss.Arachnid): its egg sacs and the cocoons it wraps players in (one Blender model
 * with both, ArachnidPropsModelData from tools/models/build_arachnid_props.py, showing one of them), and the skin it
 * sheds - its own model, lying on its back with the legs curled (the end of its death animation), pale and see-through,
 * fading at the end.
 */
public final class ArachnidPropsRenderers {
    private ArachnidPropsRenderers() {}

    public static final ModelLayerLocation PROPS =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "arachnid_props"), "main");
    private static final ResourceLocation SILK = tex("arachnid_props"), WEB = tex("arachnid_web"), MOLT = tex("arachnid_molt");

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/" + name + ".png");
    }

    /** Turns the pose for a model drawn the way living things are (model space: y down, the ground at 24). */
    private static void modelSpace(PoseStack pose, float yaw, float scale) {
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.scale(scale, scale, scale);
        pose.translate(0, -1.501F, 0);
    }

    // ------------------------------------------------------------------ the egg sac and the cocoon

    private static final class PropsModel<T extends Entity> extends HierarchicalModel<T> {
        private final ModelPart root, egg, cocoon;

        PropsModel(ModelPart root) {
            this.root = root;
            this.egg = root.getChild("egg");
            this.cocoon = root.getChild("cocoon");
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            egg.visible = entity instanceof ArachnidEgg;
            cocoon.visible = entity instanceof ArachnidCocoon;
            if (entity instanceof ArachnidEgg e) {
                animate(e.pulseAnimation, ArachnidPropsModelData.PULSE, ageInTicks);
                animate(e.hatchAnimation, ArachnidPropsModelData.HATCH, ageInTicks);
            } else if (entity instanceof ArachnidCocoon c) {
                animate(c.struggleAnimation, ArachnidPropsModelData.STRUGGLE, ageInTicks);
            }
        }
    }

    public static final class EggRenderer extends EntityRenderer<ArachnidEgg> {
        private final PropsModel<ArachnidEgg> model;

        public EggRenderer(EntityRendererProvider.Context context) {
            super(context);
            model = new PropsModel<>(context.bakeLayer(PROPS));
            shadowRadius = 0.45F;
        }

        @Override
        public ResourceLocation getTextureLocation(ArachnidEgg egg) {
            return SILK;
        }

        @Override
        public void render(ArachnidEgg egg, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            pose.pushPose();
            float spin = egg.landed() ? egg.getYRot() : egg.getYRot() + (egg.tickCount + partialTick) * 25;
            modelSpace(pose, spin, 1.0F);
            model.setupAnim(egg, 0, 0, egg.tickCount + partialTick, 0, 0);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(SILK)), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            super.render(egg, yaw, partialTick, pose, buffers, light);
        }
    }

    public static final class CocoonRenderer extends EntityRenderer<ArachnidCocoon> {
        private final PropsModel<ArachnidCocoon> model;

        public CocoonRenderer(EntityRendererProvider.Context context) {
            super(context);
            model = new PropsModel<>(context.bakeLayer(PROPS));
            shadowRadius = 0.5F;
        }

        @Override
        public ResourceLocation getTextureLocation(ArachnidCocoon cocoon) {
            return cocoon.strong() ? SILK : WEB;
        }

        @Override
        public void render(ArachnidCocoon cocoon, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            pose.pushPose();
            modelSpace(pose, cocoon.getYRot(), 1.0F);
            model.setupAnim(cocoon, 0, 0, cocoon.tickCount + partialTick, 0, 0);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(cocoon))), light,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
            super.render(cocoon, yaw, partialTick, pose, buffers, light);
        }
    }

    // ------------------------------------------------------------------ the shed skin

    private static final class MoltModel extends HierarchicalModel<ArachnidMolt> {
        private static final Vector3f CACHE = new Vector3f();
        private final ModelPart root;

        MoltModel(ModelPart root) {
            this.root = root;
            root.getChild("body").getChild("cocoon").visible = false;
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(ArachnidMolt molt, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            KeyframeAnimations.animate(this, ArachnidModelData.DEATH, 4000L, 1.0F, CACHE); // on its back, the legs curled
        }
    }

    public static final class MoltRenderer extends EntityRenderer<ArachnidMolt> {
        private final MoltModel model;

        public MoltRenderer(EntityRendererProvider.Context context) {
            super(context);
            model = new MoltModel(context.bakeLayer(ArachnidModel.LAYER));
            shadowRadius = 1.6F;
        }

        @Override
        public ResourceLocation getTextureLocation(ArachnidMolt molt) {
            return MOLT;
        }

        @Override
        public void render(ArachnidMolt molt, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            float fade = molt.fade(partialTick);
            if (fade > 0.01F) {
                pose.pushPose();
                modelSpace(pose, molt.getYRot(), 2.0F);
                model.setupAnim(molt, 0, 0, molt.tickCount + partialTick, 0, 0);
                int alpha = Mth.clamp((int) (fade * 255), 0, 255);
                model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(MOLT)), light, OverlayTexture.NO_OVERLAY,
                        FastColor.ARGB32.color(alpha, 255, 255, 255));
                pose.popPose();
            }
            super.render(molt, yaw, partialTick, pose, buffers, light);
        }
    }
}
