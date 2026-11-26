package com.divinis.client;

import java.util.HashSet;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.divinis.boss.BossTargeting;
import com.divinis.Divinis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** A red arrow bobbing and turning over the head of whoever a boss is going for (boss.BossTargeting). */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class TargetMarker {
    private TargetMarker() {}

    private static final RenderType ARROW = RenderType.entityTranslucentEmissive(
            ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/effect/target_marker.png"));
    private static final double RANGE = 96;
    private static final float SIZE = 0.6F;

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Camera cam = event.getCamera();
        Vec3 c = cam.getPosition();
        Set<Integer> marked = new HashSet<>();
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof BossTargeting.Marked boss && e.distanceToSqr(c) < RANGE * RANGE) {
                int id = boss.markedTargetId();
                if (id >= 0) marked.add(id);
            }
        }
        if (marked.isEmpty()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float time = level.getGameTime() + partial;
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-c.x, -c.y, -c.z);
        for (int id : marked) {
            Entity target = level.getEntity(id);
            if (target == null) continue;
            Vec3 at = target.getPosition(partial).add(0, target.getBbHeight() + 0.55 + Mth.sin(time * 0.15F) * 0.12, 0);
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(new Quaternionf().rotationY((float) Math.toRadians(-cam.getYRot())));
            VertexConsumer vc = buffers.getBuffer(ARROW);
            Matrix4f m = pose.last().pose();
            float h = SIZE / 2;
            float[][] corners = {{-h, -h, 0, 1}, {h, -h, 1, 1}, {h, h, 1, 0}, {-h, h, 0, 0}};
            for (float[] q : corners) {
                vc.addVertex(m, q[0], q[1], 0).setColor(255, 255, 255, 255).setUv(q[2], q[3])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 1, 0);
            }
            pose.popPose();
        }
        buffers.endBatch(ARROW);
        pose.popPose();
    }
}
