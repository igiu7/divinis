package com.divinis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.divinis.boss.SerpentBody;
import com.divinis.boss.SerpentOfInfinity;
import com.divinis.Divinis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Serpent of Infinity: its head where the entity is, then every body segment and the tail where the body
 * chain (boss.SerpentBody) has them, each turned to face the one before it, all twice the models' size, with their
 * purple glow; purple embers trail from the head while it is awake. In the second phase it is drawn with the white
 * textures, part by part as the whiteness runs down it in the change, each part flashing as it turns.
 */
public class SerpentRenderer extends EntityRenderer<SerpentOfInfinity> {
    private static final float SCALE = 2.0F;

    private static final ResourceLocation HEAD_TEX = tex("serpent_head");
    private static final RenderType HEAD_GLOW = RenderType.eyes(tex("serpent_head_glow"));
    private static final ResourceLocation BODY_TEX = tex("serpent_body");
    private static final RenderType BODY_GLOW = RenderType.eyes(tex("serpent_body_glow"));
    private static final ResourceLocation TAIL_TEX = tex("serpent_tail");
    private static final RenderType TAIL_GLOW = RenderType.eyes(tex("serpent_tail_glow"));
    private static final ResourceLocation HEAD_WHITE = tex("serpent_head_white");
    private static final ResourceLocation BODY_WHITE = tex("serpent_body_white");
    private static final ResourceLocation TAIL_WHITE = tex("serpent_tail_white");

    private final SerpentModels.Head head;
    private final SerpentModels.Segment body;
    private final SerpentModels.Segment tail;

    public SerpentRenderer(EntityRendererProvider.Context context) {
        super(context);
        head = new SerpentModels.Head(context.bakeLayer(SerpentModels.HEAD));
        body = new SerpentModels.Segment(context.bakeLayer(SerpentModels.BODY), SerpentBodyModelData.PULSE);
        tail = new SerpentModels.Segment(context.bakeLayer(SerpentModels.TAIL), SerpentTailModelData.WAG);
        shadowRadius = 0;
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(SerpentOfInfinity boss) {
        return HEAD_TEX;
    }

    @Override
    public void render(SerpentOfInfinity boss, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = boss.tickCount + partialTick;
        int overlay = LivingEntityRenderer.getOverlayCoords(boss, 0);
        Vec3 origin = boss.getPosition(partialTick);
        Vec3 headAt = origin.add(0, SerpentOfInfinity.HEAD_HALF, 0);

        // the head
        float yaw = Mth.rotLerp(partialTick, boss.yRotO, boss.getYRot());
        float pitch = Mth.lerp(partialTick, boss.xRotO, boss.getXRot());
        pose.pushPose();
        pose.translate(0, SerpentOfInfinity.HEAD_HALF, 0);
        head.setupAnim(boss, 0, 0, age, 0, 0);
        draw(head, boss.white(-1) ? HEAD_WHITE : HEAD_TEX, HEAD_GLOW, yaw, pitch, pose, buffers, light, overlay);
        pose.popPose();
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();

        // the body, segment by segment, the tail last
        Vec3 lead = headAt;
        for (int i = 0; i < SerpentBody.SEGMENTS; i++) {
            Vec3 at = boss.body.lerp(i, partialTick);
            if (boss.burst(i)) {   // dying: burst already
                lead = at;
                continue;
            }
            Vec3 d = lead.subtract(at);
            float segYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
            float segPitch = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
            boolean last = i == SerpentBody.SEGMENTS - 1;
            SerpentModels.Segment model = last ? tail : body;
            model.setupAnim(boss, 0, 0, age - i * 2.5F, 0, 0);
            pose.pushPose();
            pose.translate(at.x - origin.x, at.y - origin.y, at.z - origin.z);
            boolean white = boss.white(i);
            ResourceLocation texture = last ? (white ? TAIL_WHITE : TAIL_TEX) : (white ? BODY_WHITE : BODY_TEX);
            draw(model, texture, last ? TAIL_GLOW : BODY_GLOW, segYaw, segPitch, pose, buffers, light, overlay);
            pose.popPose();
            float flash = boss.whitenFlash(i, partialTick);
            if (flash > 0) FireFx.voidGlow(pose, buffers, cam, at.subtract(origin), 6.0F * flash, flash * 1.5F);
            lead = at;
        }

        float headFlash = boss.whitenFlash(-1, partialTick);
        if (headFlash > 0) FireFx.voidGlow(pose, buffers, cam, headAt.subtract(origin), 8.0F * headFlash, headFlash * 1.5F);
        if (boss.state() != SerpentOfInfinity.SLEEPING) {
            pose.pushPose();
            pose.translate(0, SerpentOfInfinity.HEAD_HALF, 0);
            FireFx.voidGlow(pose, buffers, cam, Vec3.ZERO, 5.0F, 0.6F);
            for (int i = 0; i < 4; i++) {
                Vec3 at = boss.body.lerp(i * 6, partialTick).subtract(headAt);
                FireFx.voidGlow(pose, buffers, cam, at, 3.2F, 0.35F);
            }
            pose.popPose();
        }
        super.render(boss, entityYaw, partialTick, pose, buffers, light);
    }

    /** One model at the pose's origin, turned to yaw and pitch, with its texture and its glow. */
    private static void draw(HierarchicalModel<SerpentOfInfinity> model, ResourceLocation texture, RenderType glow, float yaw, float pitch,
            PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.mulPose(Axis.XP.rotationDegrees(-pitch));
        pose.scale(-SCALE, -SCALE, SCALE);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, overlay, -1);
        model.renderToBuffer(pose, buffers.getBuffer(glow), 0xF000F0, overlay, -1);
        pose.popPose();
    }
}
