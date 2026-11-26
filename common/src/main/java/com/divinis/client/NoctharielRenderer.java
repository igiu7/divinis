package com.divinis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.divinis.boss.Nocthariel;
import com.divinis.Divinis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws Nocthariel (models and animations from Blender: NoctharielModelData, NoctharielP2ModelData,
 * tools/models/build_nocthariel.py): pure black, three times the model's size, the red glow down its throats - and
 * cracks of golden light all over it while it is full of the sun it ate. Then its effects:
 * - the sun it devours: a radiant sun with a corona of rays, dragged down into its mouths;
 * - the black sun (client.BlackHoleFx, crimson) where the server has it;
 * - its two shadow hands (Aurelion's dead hand, in black) while it drags them over the floor.
 * Nothing while it waits under the floor.
 */
public class NoctharielRenderer extends EntityRenderer<Nocthariel> {
    public static final ModelLayerLocation BODY = layer("nocthariel");
    public static final ModelLayerLocation BODY_P2 = layer("nocthariel_p2");
    private static final float SCALE = 3.0F, HAND_SCALE = 3.0F;

    private static final ResourceLocation TEXTURE = tex("nocthariel"), TEXTURE_P2 = tex("nocthariel_p2");
    private static final RenderType GLOW = RenderType.eyes(tex("nocthariel_glow")), GLOW_P2 = RenderType.eyes(tex("nocthariel_p2_glow"));
    private static final RenderType LIGHT = RenderType.eyes(tex("nocthariel_light")), LIGHT_P2 = RenderType.eyes(tex("nocthariel_p2_light"));
    private static final ResourceLocation HAND_TEXTURE = tex("nocthariel_hand");

    private final Body body, bodyP2;
    private final AurelionRenderer.Hand hand;

    public NoctharielRenderer(EntityRendererProvider.Context context) {
        super(context);
        body = new Body(context.bakeLayer(BODY), false);
        bodyP2 = new Body(context.bakeLayer(BODY_P2), true);
        hand = new AurelionRenderer.Hand(context.bakeLayer(AurelionRenderer.HAND));
        shadowRadius = 0;
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, name), "main");
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(Nocthariel boss) {
        return TEXTURE;
    }

    @Override
    public void render(Nocthariel boss, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (boss.state() == Nocthariel.DORMANT) return;
        float age = boss.tickCount + partialTick;
        Vec3 origin = boss.getPosition(partialTick);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(origin);
        int overlay = LivingEntityRenderer.getOverlayCoords(boss, 0);
        float yaw = Mth.rotLerp(partialTick, boss.yRotO, boss.getYRot());
        float since = age - boss.attackSeenAt;
        boolean p2 = boss.phase() == 2;

        // full of light: from swallowing the sun until it has breathed it all out
        float lit = 0;
        if (boss.attack() == Nocthariel.DEVOUR && since > Nocthariel.SWALLOW) {
            lit = Mth.clamp((since - Nocthariel.SWALLOW) / 6, 0, 1) * (0.75F + 0.25F * Mth.sin(age * 0.5F));
        }

        pose.pushPose();
        pose.translate(0, Nocthariel.CHEST, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.scale(-SCALE, -SCALE, SCALE);
        Body model = p2 ? bodyP2 : body;
        model.setupAnim(boss, 0, 0, age, 0, 0);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(p2 ? TEXTURE_P2 : TEXTURE)), light, overlay, -1);
        model.renderToBuffer(pose, buffers.getBuffer(p2 ? GLOW_P2 : GLOW), LightTexture.FULL_BRIGHT, overlay, -1);
        if (lit > 0) {
            int c = (int) (lit * 255);
            model.renderToBuffer(pose, buffers.getBuffer(p2 ? LIGHT_P2 : LIGHT), LightTexture.FULL_BRIGHT, overlay, 0xFF000000 | c << 16 | c << 8 | c);
        }
        pose.popPose();

        if (boss.attack() == Nocthariel.DEVOUR) dawnSun(boss, pose, buffers, cam, origin, since, age);
        if (boss.attack() == Nocthariel.BLACK_SUN || boss.lastAttackSeen == Nocthariel.BLACK_SUN && age - boss.attackEndedAt < 10) {
            float k = boss.attack() == Nocthariel.BLACK_SUN ? Mth.clamp(since / 20, 0, 1) * Mth.clamp((215 - since) / 10, 0, 1) : 0;
            Vec3 sun = boss.sunBefore.lerp(boss.sunNow, partialTick);
            if (k > 0 && !sun.equals(Vec3.ZERO)) BlackHoleFx.draw(pose, buffers, cam, sun.subtract(origin), sun, 2.8F, k, age, BlackHoleFx.CRIMSON);
        }
        if (boss.attack() == Nocthariel.ARMS || boss.lastAttackSeen == Nocthariel.ARMS && age - boss.attackEndedAt < 30) {
            for (int i = 0; i < 2; i++) {
                if (boss.handNow[i].equals(Vec3.ZERO)) continue;
                Vec3 at = boss.handBefore[i].lerp(boss.handNow[i], partialTick).subtract(origin);
                Vec3 heading = boss.handNow[i].subtract(boss.handBefore[i]);
                float handYaw = heading.horizontalDistanceSqr() > 0.01 ? (float) (Mth.atan2(heading.z, heading.x) * Mth.RAD_TO_DEG) - 90.0F : yaw;
                pose.pushPose();
                pose.translate(at.x, at.y, at.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0F - handYaw));
                pose.scale(i == 0 ? HAND_SCALE : -HAND_SCALE, -HAND_SCALE, HAND_SCALE);
                hand.pose(0.55F, age + i * 17);
                hand.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(HAND_TEXTURE)), light, OverlayTexture.NO_OVERLAY, -1);
                pose.popPose();
                BeamFx.glow(pose, buffers, cam, at, 3.5F, 0.35F, 0.02F, 0.04F);
            }
        }
        super.render(boss, entityYaw, partialTick, pose, buffers, light);
    }

    /** The sun it devours: radiant, with a corona of rays, dragged into its mouths. */
    private static void dawnSun(Nocthariel boss, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 origin, float since, float age) {
        float size = Nocthariel.dawnSunSize(since);
        if (size <= 0.05F) return;
        Vec3 at = boss.dawnSunAt(since).subtract(origin);
        float flick = 0.9F + 0.1F * Mth.sin(age * 0.9F);
        BeamFx.glow(pose, buffers, cam, at, size * 5.5F, 0.6F, 0.45F, 0.15F);
        BeamFx.glow(pose, buffers, cam, at, size * 2.4F * flick, 1.0F, 0.85F, 0.45F);
        BeamFx.glow(pose, buffers, cam, at, size * 1.3F, 1.0F, 1.0F, 0.85F);
        VertexConsumer vc = buffers.getBuffer(BeamFx.BEAM);
        for (int i = 0; i < 16; i++) {
            float a = i * Mth.TWO_PI / 16 + age * 0.015F * (i % 2 == 0 ? 1 : -1);
            float tilt = Mth.sin(i * 2.3F) * 0.6F;
            float len = size * (1.4F + 0.5F * Mth.sin(age * 0.25F + i * 1.3F));
            Vec3 dir = new Vec3(Mth.cos(a), tilt, Mth.sin(a)).normalize();
            BeamFx.ribbon(pose, vc, cam, at.add(dir.scale(size * 0.3)), at.add(dir.scale(len)), size * 0.3F, 0.95F, 0.7F, 0.3F);
        }
        if (since > Nocthariel.SUN_FORMED) {
            // light streaming from the sun into its mouths
            Vec3 head = boss.headAt().subtract(origin);
            BeamFx.ribbon(pose, vc, cam, at, head, size * 0.8F, 0.8F, 0.55F, 0.2F);
        }
    }

    /** Its body (either phase): every animation of it. */
    public static class Body extends HierarchicalModel<Nocthariel> {
        private final ModelPart root;
        private final boolean p2;

        public Body(ModelPart root, boolean p2) {
            this.root = root;
            this.p2 = p2;
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(Nocthariel boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            if (boss.isDeadOrDying()) {
                animate(boss.deathAnimation, BossDeaths.NOCTHARIEL, ageInTicks);
                return;
            }
            if (p2) {
                animate(boss.idleAnimation, NoctharielP2ModelData.IDLE, ageInTicks);
                animate(boss.devourAnimation, NoctharielP2ModelData.DEVOUR, ageInTicks);
                animate(boss.castAnimation, NoctharielP2ModelData.CAST, ageInTicks);
                animate(boss.screamAnimation, NoctharielP2ModelData.SCREAM, ageInTicks);
                animate(boss.slamAnimation, NoctharielP2ModelData.SLAM, ageInTicks);
                animate(boss.chantAnimation, NoctharielP2ModelData.CHANT, ageInTicks);
                animate(boss.emergeAnimation, NoctharielP2ModelData.EMERGE, ageInTicks);
            } else {
                animate(boss.idleAnimation, NoctharielModelData.IDLE, ageInTicks);
                animate(boss.devourAnimation, NoctharielModelData.DEVOUR, ageInTicks);
                animate(boss.castAnimation, NoctharielModelData.CAST, ageInTicks);
                animate(boss.screamAnimation, NoctharielModelData.SCREAM, ageInTicks);
                animate(boss.slamAnimation, NoctharielModelData.SLAM, ageInTicks);
                animate(boss.chantAnimation, NoctharielModelData.CHANT, ageInTicks);
                animate(boss.writheAnimation, NoctharielModelData.WRITHE, ageInTicks);
            }
        }
    }
}
