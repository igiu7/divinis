package com.divinis.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.divinis.boss.Aurelion;
import com.divinis.net.BeamPayload;
import com.divinis.Divinis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.animation.KeyframeAnimations;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Draws Aurelion (models and animations from Blender: AurelionModelData, AurelionHandModelData,
 * tools/models/build_aurelion*.py): its body three times the model's size with its glowing irises, its two dead hands
 * where the server has them (gripping as tight as it says), and its effects:
 * - the tear in the sky while it comes down (a void with stars inside, light pouring round the edges, shafts of
 *   light down to the floor and one great shaft round it), closing after it has arrived;
 * - the black hole while that attack lasts (client.BlackHoleFx);
 * and in its second phase: the blood-soaked body (AurelionP2ModelData), the eight shards of its symbol flying round it
 * (bursting out of where the symbol was when it shatters), the sky torn wide open over the room, and the star of its
 * collapse - swelling, a corona of rays, falling in on itself into a black hole, then the blast of light.
 * Nothing at all while it waits.
 */
public class AurelionRenderer extends EntityRenderer<Aurelion> {
    public static final ModelLayerLocation BODY = layer("aurelion");
    public static final ModelLayerLocation HAND = layer("aurelion_hand");
    public static final ModelLayerLocation BODY_P2 = layer("aurelion_p2");
    public static final ModelLayerLocation SHARD = layer("aurelion_shard");
    private static final ResourceLocation TEXTURE_P2 = tex("entity/aurelion_p2");
    private static final RenderType GLOW_P2 = RenderType.eyes(tex("entity/aurelion_p2_glow"));
    private static final ResourceLocation SHARD_TEXTURE = tex("entity/aurelion_shard");
    private static final RenderType SHARD_GLOW = RenderType.eyes(tex("entity/aurelion_shard_glow"));
    private static final RenderType CRACK_WIDE = RenderType.entityTranslucent(tex("effect/sky_crack"));
    private static final float SCALE = 3.0F, HAND_SCALE = 2.5F;

    private static final ResourceLocation TEXTURE = tex("entity/aurelion");
    private static final RenderType GLOW = RenderType.eyes(tex("entity/aurelion_glow"));
    private static final ResourceLocation HAND_TEXTURE = tex("entity/aurelion_hand");
    private static final RenderType CRACK = RenderType.entityTranslucent(tex("effect/sky_crack"));
    private static final RenderType CRACK_GLOW = RenderType.eyes(tex("effect/sky_crack_glow"));

    private final Body body;
    private final BodyP2 bodyP2;
    private final Hand hand;
    private final Shard shard;

    public AurelionRenderer(EntityRendererProvider.Context context) {
        super(context);
        body = new Body(context.bakeLayer(BODY));
        bodyP2 = new BodyP2(context.bakeLayer(BODY_P2));
        hand = new Hand(context.bakeLayer(HAND));
        shard = new Shard(context.bakeLayer(SHARD));
        shadowRadius = 0;
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, name), "main");
    }

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/" + path + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(Aurelion boss) {
        return TEXTURE;
    }

    @Override
    public void render(Aurelion boss, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (boss.state() == Aurelion.DORMANT) return;
        float age = boss.tickCount + partialTick;
        Vec3 origin = boss.getPosition(partialTick);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(origin);
        int overlay = LivingEntityRenderer.getOverlayCoords(boss, 0);
        float yaw = Mth.rotLerp(partialTick, boss.yRotO, boss.getYRot());

        // the tear in the sky
        float crack = 0;
        if (boss.state() == Aurelion.DESCEND) crack = Mth.clamp((age - boss.descendSeenAt - 8) / 12, 0, 1);
        else crack = Mth.clamp(1 - (age - boss.awakeSeenAt) / 60, 0, 1);
        if (crack > 0 && boss.hasHome()) drawSky(boss, pose, buffers, cam, origin, age, crack);

        // the body
        pose.pushPose();
        pose.translate(0, boss.getBbHeight() / 2, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.scale(-SCALE, -SCALE, SCALE);
        if (boss.phase() == 2) {
            bodyP2.setupAnim(boss, 0, 0, age, 0, 0);
            bodyP2.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE_P2)), light, overlay, -1);
            bodyP2.renderToBuffer(pose, buffers.getBuffer(GLOW_P2), LightTexture.FULL_BRIGHT, overlay, -1);
        } else {
            body.setupAnim(boss, 0, 0, age, 0, 0);
            body.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay, -1);
            body.renderToBuffer(pose, buffers.getBuffer(GLOW), LightTexture.FULL_BRIGHT, overlay, -1);
        }
        pose.popPose();

        if (boss.phase() == 2) {
            if (boss.hasHome()) tornSky(boss, pose, buffers, cam, origin, age);
            shards(boss, pose, buffers, cam, origin, partialTick, age, yaw, light);
        }
        if (boss.attack() == Aurelion.COLLAPSE) star(boss, pose, buffers, cam, origin, age);

        // the hands
        for (int i = 0; i < 2; i++) {
            if (boss.handNow[i].equals(Vec3.ZERO)) continue;
            Vec3 at = boss.handBefore[i].lerp(boss.handNow[i], partialTick).subtract(origin);
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
            pose.scale(i == 0 ? HAND_SCALE : -HAND_SCALE, -HAND_SCALE, HAND_SCALE); // one of them mirrored
            hand.pose(boss.grip(i), age + i * 31);
            hand.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(HAND_TEXTURE)), light, OverlayTexture.NO_OVERLAY, -1);
            pose.popPose();
        }

        // the black hole
        float hole = 0;
        if (boss.attack() == Aurelion.HOLE) {
            boss.holeShown = true;
            hole = Mth.clamp((age - boss.attackSeenAt) / 30, 0, 1);
        } else if (boss.holeShown) {
            hole = Mth.clamp(1 - (age - boss.attackEndedAt) / 10, 0, 1);
            if (hole <= 0) boss.holeShown = false;
        }
        if (hole > 0 && boss.hasHome()) {
            BlackHoleFx.draw(pose, buffers, cam, boss.holeAt().subtract(origin), boss.holeAt(), 3.1F, hole, age, BlackHoleFx.GOLD);
        }

        super.render(boss, entityYaw, partialTick, pose, buffers, light);
    }

    /** The tear high above the room, the shafts of light out of it, the light it comes down in. */
    private static void drawSky(Aurelion boss, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 origin, float age, float k) {
        Vec3 home = boss.home();
        Vec3 tear = home.add(0, Aurelion.CRACK_HEIGHT + 3, 0).subtract(origin);
        RandomSource rnd = RandomSource.create(boss.getUUID().getLeastSignificantBits());
        float turn = rnd.nextFloat() * 360;
        float size = 38 * (0.3F + 0.7F * k);
        pose.pushPose();
        pose.translate(tear.x, tear.y, tear.z);
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        flat(pose, buffers.getBuffer(CRACK_GLOW), size * 1.25F, k, k, k, 1);
        pose.translate(0, -0.05, 0);
        flat(pose, buffers.getBuffer(CRACK), size, 1, 1, 1, k);
        pose.popPose();
        BeamFx.glow(pose, buffers, cam, tear, 30 * k, 0.8F * k, 0.7F * k, 0.45F * k);

        float since = age - boss.descendSeenAt;
        float shafts = boss.state() == Aurelion.DESCEND ? Mth.clamp((since - 20) / 15, 0, 1) * k : k;
        if (shafts > 0) {
            for (int i = 0; i < 8; i++) {
                float along = (rnd.nextFloat() - 0.5F) * size * 0.7F;
                Vec3 from = tear.add(new Vec3(along, 0, (rnd.nextFloat() - 0.5F) * 4).yRot(-turn * Mth.DEG_TO_RAD));
                double a = rnd.nextDouble() * Math.PI * 2, r = 4 + rnd.nextDouble() * 18;
                Vec3 to = home.add(Math.cos(a) * r, 0, Math.sin(a) * r).subtract(origin);
                float flick = 0.7F + 0.3F * Mth.sin(age * 0.2F + i * 1.7F);
                BeamFx.draw(pose, buffers, cam, BeamPayload.SHAFT, from, to, 2.0F + rnd.nextFloat() * 2.5F, shafts * flick, age);
            }
            if (boss.state() == Aurelion.DESCEND && since > 35) {
                // the great light it comes down in
                Vec3 to = home.subtract(origin);
                float glow = Mth.clamp((since - 35) / 20, 0, 1) * Mth.clamp((Aurelion.DESCEND_TICKS + 5 - since) / 25, 0, 1);
                BeamFx.draw(pose, buffers, cam, BeamPayload.SHAFT, tear, to, 9.0F, glow, age);
                BeamFx.draw(pose, buffers, cam, BeamPayload.LIGHT, tear, to, 2.5F, glow * 0.8F, age);
            }
        }
    }

    /** The sky over the room torn wide open, all through the second phase (opening as it shatters). */
    private static void tornSky(Aurelion boss, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 origin, float age) {
        float k = Mth.clamp((age - boss.phaseSeenAt) / 30, 0, 1);
        if (boss.phaseSeenAt < -100) k = 1; // already torn when we came
        Vec3 tear = boss.home().add(0, Aurelion.CRACK_HEIGHT + 8, 0).subtract(origin);
        pose.pushPose();
        pose.translate(tear.x, tear.y, tear.z);
        pose.mulPose(Axis.YP.rotationDegrees(boss.getUUID().getMostSignificantBits() % 360));
        flat(pose, buffers.getBuffer(CRACK_GLOW), 130 * k, 0.9F * k, 0.75F * k, 0.75F * k, 1);
        pose.translate(0, -0.1, 0);
        flat(pose, buffers.getBuffer(CRACK_WIDE), 110 * k, 1, 1, 1, k);
        pose.popPose();
    }

    /** The eight shards of its symbol, flying round it (or in their ring over the room); out of the symbol as it breaks. */
    private void shards(Aurelion boss, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 origin, float partial, float age, float yaw,
            int light) {
        float time = boss.level().getGameTime() + partial;
        float ring = Mth.lerp(partial, boss.shardRingBefore, boss.shardRing);
        float burst = boss.phaseSeenAt < -100 ? 1 : Mth.clamp((age - boss.phaseSeenAt) / 20, 0, 1);
        Vec3 mid = boss.center();
        for (int i = 0; i < Aurelion.SHARD_COUNT; i++) {
            double a = i * Math.PI * 2 / Aurelion.SHARD_COUNT;
            Vec3 from = mid.add(new Vec3(Math.cos(a) * 4.5, Math.sin(a) * 4.5, 2.5).yRot(-yaw * Mth.DEG_TO_RAD));
            Vec3 at = from.lerp(boss.shardAt(i, time, ring), burst).subtract(origin);
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(Axis.YP.rotationDegrees(time * 4 + i * 45));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.05F + i) * 40));
            pose.scale(-2.5F, -2.5F, 2.5F);
            shard.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(SHARD_TEXTURE)), light, OverlayTexture.NO_OVERLAY, -1);
            shard.renderToBuffer(pose, buffers.getBuffer(SHARD_GLOW), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
            pose.popPose();
            BeamFx.glow(pose, buffers, cam, at, 2.2F, 0.5F, 0.38F, 0.12F);
        }
    }

    /** The star of its collapse: it swells with a corona of rays, falls in on itself into a black hole, then goes off. */
    private static void star(Aurelion boss, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 origin, float age) {
        float since = age - boss.attackSeenAt;
        Vec3 at = boss.starAt().subtract(origin);
        float grown = Mth.clamp((since - 10) / (Aurelion.STAR_GROWN - 10), 0, 1);
        float fall = Mth.clamp((since - Aurelion.STAR_GROWN) / (float) (Aurelion.STAR_FALLS - Aurelion.STAR_GROWN), 0, 1);
        float size = (1 + 6 * grown) * (1 - fall);
        if (size > 0.05F) {
            float flick = 0.9F + 0.1F * Mth.sin(age * 0.9F);
            BeamFx.glow(pose, buffers, cam, at, size * 4.5F, 0.55F, 0.42F, 0.15F);
            BeamFx.glow(pose, buffers, cam, at, size * 2.2F * flick, 1.0F, 0.85F, 0.5F);
            BeamFx.glow(pose, buffers, cam, at, size * 1.2F, 1.0F, 1.0F, 0.9F);
            VertexConsumer vc = buffers.getBuffer(BeamFx.BEAM);
            for (int i = 0; i < 14; i++) {
                float a = i * Mth.TWO_PI / 14 + age * 0.02F * (i % 2 == 0 ? 1 : -1);
                float tilt = Mth.sin(i * 2.1F) * 0.6F;
                float len = size * (1.3F + 0.5F * Mth.sin(age * 0.3F + i * 1.7F));
                Vec3 dir = new Vec3(Mth.cos(a), tilt, Mth.sin(a)).normalize();
                BeamFx.ribbon(pose, vc, cam, at.add(dir.scale(size * 0.3)), at.add(dir.scale(len)), size * 0.35F, 0.9F, 0.7F, 0.3F);
            }
        }
        if (fall > 0 && since < Aurelion.STAR_FALLS) {
            BlackHoleFx.draw(pose, buffers, cam, at, boss.starAt(), 2.6F, fall, age, BlackHoleFx.GOLD);
        }
        float blast = (since - Aurelion.STAR_FALLS) / 22;
        if (blast > 0 && blast < 1) {
            BeamFx.glow(pose, buffers, cam, at, 10 + 70 * blast, 1 - blast, (1 - blast) * 0.9F, (1 - blast) * 0.7F);
        }
    }

    /** A flat square in the pose's xz plane, both sides. */
    private static void flat(PoseStack pose, VertexConsumer vc, float size, float r, float g, float b, float a) {
        Matrix4f m = pose.last().pose();
        float h = size / 2;
        float[][] c = {{-h, -h, 0, 0}, {h, -h, 1, 0}, {h, h, 1, 1}, {-h, h, 0, 1}};
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] q = c[pass == 0 ? i : 3 - i];
                vc.addVertex(m, q[0], 0, q[1]).setColor(r, g, b, a).setUv(q[2], q[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 1, 0);
            }
        }
    }

    /** The body: idle always, coming down, summoning the black hole, judgement. */
    public static class Body extends HierarchicalModel<Aurelion> {
        private final ModelPart root;

        public Body(ModelPart root) {
            this.root = root;
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(Aurelion boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            if (boss.isDeadOrDying()) {
                animate(boss.deathAnimation, BossDeaths.AURELION, ageInTicks);
                return;
            }
            animate(boss.idleAnimation, AurelionModelData.IDLE, ageInTicks);
            animate(boss.descendAnimation, AurelionModelData.DESCEND, ageInTicks);
            animate(boss.summonAnimation, AurelionModelData.SUMMON, ageInTicks);
            animate(boss.gazeAnimation, AurelionModelData.GAZE, ageInTicks);
            animate(boss.sealAnimation, AurelionModelData.SEAL, ageInTicks);
        }
    }

    /** The second phase's body: trembling, bleeding, screaming, casting, wrapping its wings, reaching, holding. */
    public static class BodyP2 extends HierarchicalModel<Aurelion> {
        private final ModelPart root;

        public BodyP2(ModelPart root) {
            this.root = root;
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(Aurelion boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            if (boss.isDeadOrDying()) {
                animate(boss.deathAnimation, BossDeaths.AURELION, ageInTicks);
                return;
            }
            animate(boss.idleAnimation, AurelionP2ModelData.IDLE, ageInTicks);
            animate(boss.bleedAnimation, AurelionP2ModelData.BLEED, ageInTicks);
            animate(boss.screamAnimation, AurelionP2ModelData.SCREAM, ageInTicks);
            animate(boss.castAnimation, AurelionP2ModelData.CAST, ageInTicks);
            animate(boss.wrapAnimation, AurelionP2ModelData.WRAP, ageInTicks);
            animate(boss.reachAnimation, AurelionP2ModelData.REACH, ageInTicks);
            animate(boss.holdAnimation, AurelionP2ModelData.HOLD, ageInTicks);
            animate(boss.gazeAnimation, AurelionP2ModelData.REACH, ageInTicks);
            animate(boss.summonAnimation, AurelionP2ModelData.CAST, ageInTicks);
        }
    }

    /** A shard of the symbol (no animations: it is spun as it flies). */
    public static class Shard extends HierarchicalModel<Aurelion> {
        private final ModelPart root;

        public Shard(ModelPart root) {
            this.root = root;
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(Aurelion boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {}
    }

    /** A hand: the grip played to its point of the close animation, and the fingers twitching. */
    public static class Hand extends HierarchicalModel<Aurelion> {
        private static final Vector3f CACHE = new Vector3f();
        private final ModelPart root;

        public Hand(ModelPart root) {
            this.root = root;
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(Aurelion boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {}

        void pose(float grip, float age) {
            root.getAllParts().forEach(ModelPart::resetPose);
            KeyframeAnimations.animate(this, AurelionHandModelData.CLOSE, (long) (Mth.clamp(grip, 0, 1) * 999), 1.0F, CACHE);
            KeyframeAnimations.animate(this, AurelionHandModelData.TWITCH, (long) (age * 50), 1.0F, CACHE);
        }
    }
}
