package com.divinis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.divinis.boss.EvilBrother;
import com.divinis.Divinis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Draws the Evil Brother: his steam mech 1.5 times its model's pixels (about six blocks tall) - the armoured mech in
 * phase 1, the lit engine in phase 2 - its glow (the headlamp; the boiler, firebox and red-hot rims in phase 2) at full
 * light, growing white-hot as it dies; and himself, the Guild Master's dwarf in his own colours (evil_brother_dwarf.png),
 * sitting in the cockpit with his hands on the levers, looking at whom the mech hunts. Hidden in its whiteout, only the
 * glow shows through the steam.
 */
public class EvilBrotherRenderer extends MobRenderer<EvilBrother, EvilBrotherModel> {
    private static final ResourceLocation TEXTURE = tex("evil_brother");
    private static final ResourceLocation TEXTURE_P2 = tex("evil_brother_p2");
    private static final ResourceLocation GLOW = tex("evil_brother_glow");
    private static final ResourceLocation GLOW_P2 = tex("evil_brother_p2_glow");
    private static final ResourceLocation DWARF = tex("evil_brother_dwarf");
    private static final float SCALE = 1.5F;

    private final EvilBrotherModel phase1, phase2;
    private final GuildMasterModel dwarf;

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/" + name + ".png");
    }

    public EvilBrotherRenderer(EntityRendererProvider.Context context) {
        super(context, new EvilBrotherModel(context.bakeLayer(EvilBrotherModel.LAYER), EvilBrotherModel.P1), 2.5F);
        this.phase1 = model;
        this.phase2 = new EvilBrotherModel(context.bakeLayer(EvilBrotherModel.LAYER_P2), EvilBrotherModel.P2);
        this.dwarf = new GuildMasterModel(context.bakeLayer(GuildMasterModel.LAYER));
        addLayer(new Glow(this));
        addLayer(new Pilot(this));
    }

    @Override
    public void render(EvilBrother boss, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        model = boss.phase() == 2 ? phase2 : phase1;
        super.render(boss, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(EvilBrother boss) {
        return boss.phase() == 2 ? TEXTURE_P2 : TEXTURE;
    }

    /** Hidden in its whiteout: the mech itself is not drawn (the glow and the pilot's goggles are). */
    @Override
    protected RenderType getRenderType(EvilBrother boss, boolean bodyVisible, boolean translucent, boolean glowing) {
        return boss.hidden() ? null : super.getRenderType(boss, bodyVisible, translucent, glowing);
    }

    @Override
    protected void scale(EvilBrother boss, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    /** No tipping over when it dies: it kneels and bursts (its death animation). */
    @Override
    protected float getFlipDegrees(EvilBrother boss) {
        return 0;
    }

    /** The glow at full light; dying, it grows brighter and redder. */
    private static final class Glow extends RenderLayer<EvilBrother, EvilBrotherModel> {
        Glow(RenderLayerParent<EvilBrother, EvilBrotherModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, EvilBrother boss, float limbSwing, float limbSwingAmount,
                float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ResourceLocation tex = boss.phase() == 2 ? GLOW_P2 : GLOW;
            float flicker = 0.85F + 0.15F * Mth.sin(ageInTicks * 0.7F);
            int color = FastColor.ARGB32.colorFromFloat(1.0F, 1.0F, flicker, flicker * 0.9F);
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(tex)), light, OverlayTexture.NO_OVERLAY, color);
        }
    }

    /** The dwarf in the cockpit: sitting, his hands on the levers, his head turned to the mech's target. */
    private final class Pilot extends RenderLayer<EvilBrother, EvilBrotherModel> {
        Pilot(RenderLayerParent<EvilBrother, EvilBrotherModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, EvilBrother boss, float limbSwing, float limbSwingAmount,
                float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (boss.hidden()) return;
            EvilBrotherModel mech = getParentModel();
            pose.pushPose();
            mech.root().translateAndRotate(pose);
            mech.body.translateAndRotate(pose);
            mech.hull.translateAndRotate(pose);
            mech.seat.translateAndRotate(pose);
            // the dwarf at his own size (the mech is drawn SCALE times bigger), his hips (y 18 of his model) on the seat
            pose.scale(1 / SCALE, 1 / SCALE, 1 / SCALE);
            pose.translate(0, -18 / 16.0F, 0);
            ModelPart root = dwarf.root();
            root.getAllParts().forEach(ModelPart::resetPose);
            ModelPart body = root.getChild("body");
            ModelPart head = body.getChild("head");
            ModelPart armRight = body.getChild("arm_right"), armLeft = body.getChild("arm_left");
            root.getChild("leg_right").xRot = -80 * Mth.DEG_TO_RAD;   // sitting
            root.getChild("leg_left").xRot = -80 * Mth.DEG_TO_RAD;
            // on the levers: pulled back as the mech's levers are
            armRight.xRot = (-55 + mech.leverRight.xRot * Mth.RAD_TO_DEG * 0.6F) * Mth.DEG_TO_RAD;
            armLeft.xRot = (-55 + mech.leverLeft.xRot * Mth.RAD_TO_DEG * 0.6F) * Mth.DEG_TO_RAD;
            if (boss.isDeadOrDying()) {   // he shields his face as the boiler goes
                armRight.xRot = -150 * Mth.DEG_TO_RAD;
                armLeft.xRot = -150 * Mth.DEG_TO_RAD;
                head.xRot = 20 * Mth.DEG_TO_RAD;
            } else {
                head.yRot = Mth.clamp(netHeadYaw, -50, 50) * Mth.DEG_TO_RAD;
                head.xRot = Mth.clamp(headPitch, -30, 30) * Mth.DEG_TO_RAD;
                body.yRot = Mth.sin(ageInTicks * 0.05F) * 4 * Mth.DEG_TO_RAD;
            }
            pose.mulPose(Axis.YP.rotationDegrees(0));
            dwarf.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(DWARF)), light,
                    OverlayTexture.pack(0.0F, boss.hurtTime > 0), -1);
            pose.popPose();
        }
    }
}
