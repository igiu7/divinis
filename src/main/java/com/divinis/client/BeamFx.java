package com.divinis.client;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.divinis.net.BeamPayload;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Beams (net.BeamPayload): ribbons of light from one point to another, always turned to the camera, drawn additive.
 * - LASER: a thin flickering red line, a warning;
 * - LIGHT: a beam of white-gold light - a bright core in a wide soft glow, a burst of light where it starts and ends;
 * - SHAFT: a wide, soft, slowly wavering shaft of light (from the sky).
 * A beam grows in over a few ticks and fades out at the end; when the server moves one, it glides over.
 */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class BeamFx {
    private BeamFx() {}

    public static final RenderType BEAM = RenderType.eyes(tex("beam"));
    public static final RenderType GLOW = RenderType.eyes(tex("light_glow"));

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/effect/" + name + ".png");
    }

    private static final class Beam {
        byte kind;
        Vec3 from, to, oldFrom, oldTo;
        float width;
        long born, moved, until;
    }

    private static final Map<Integer, Beam> BEAMS = new HashMap<>();

    public static void receive(BeamPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        Vec3 from = new Vec3(p.fx(), p.fy(), p.fz()), to = new Vec3(p.tx(), p.ty(), p.tz());
        Beam b = BEAMS.get(p.id());
        if (b == null) {
            b = new Beam();
            b.from = b.oldFrom = from;
            b.to = b.oldTo = to;
            b.born = now;
            BEAMS.put(p.id(), b);
        } else {
            b.oldFrom = b.from;
            b.oldTo = b.to;
            b.from = from;
            b.to = to;
            if (b.kind != p.kind()) b.born = now;
        }
        b.kind = p.kind();
        b.width = p.width();
        b.moved = now;
        b.until = now + p.life();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BEAMS.clear();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BEAMS.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            BEAMS.clear();
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = level.getGameTime() + partial;
        BEAMS.values().removeIf(b -> now > b.until + 5);
        if (BEAMS.isEmpty()) return;
        Camera cam = event.getCamera();
        Vec3 c = cam.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-c.x, -c.y, -c.z);
        for (Beam b : BEAMS.values()) {
            float glide = Mth.clamp(now - b.moved, 0, 1);
            Vec3 from = b.oldFrom.lerp(b.from, glide), to = b.oldTo.lerp(b.to, glide);
            float grow = Mth.clamp((now - b.born) / 3, 0, 1);
            float fade = Mth.clamp((b.until + 5 - now) / 5, 0, 1);
            float k = grow * fade;
            if (k <= 0.01F) continue;
            draw(pose, buffers, c, b.kind, from, to, b.width, k, now);
        }
        buffers.endBatch(BEAM);
        buffers.endBatch(GLOW);
        pose.popPose();
    }

    /** Draws one beam now (also used by the bosses' own renderers); pose in world space, cam the camera. */
    public static void draw(PoseStack pose, MultiBufferSource buffers, Vec3 cam, byte kind, Vec3 from, Vec3 to, float width, float k, float time) {
        switch (kind) {
            case BeamPayload.LASER -> {
                float flicker = 0.75F + 0.25F * Mth.sin(time * 2.7F + (float) from.x);
                ribbon(pose, buffers.getBuffer(BEAM), cam, from, to, width * 3, 1.0F * k * flicker, 0.08F * k, 0.06F * k);
                ribbon(pose, buffers.getBuffer(BEAM), cam, from, to, width, 1.0F * k, 0.55F * k, 0.5F * k);
                glow(pose, buffers, cam, from, width * 10, 1.0F * k, 0.15F * k, 0.1F * k);
            }
            case BeamPayload.LIGHT -> {
                float pulse = 0.9F + 0.1F * Mth.sin(time * 1.9F);
                float w = width * (0.6F + 0.4F * k);
                ribbon(pose, buffers.getBuffer(BEAM), cam, from, to, w * 2.6F, 0.55F * k, 0.42F * k, 0.16F * k);
                ribbon(pose, buffers.getBuffer(BEAM), cam, from, to, w * pulse, 1.0F * k, 0.96F * k, 0.82F * k);
                glow(pose, buffers, cam, from, w * 4.0F, 1.0F * k, 0.85F * k, 0.5F * k);
                glow(pose, buffers, cam, to, w * 3.0F, 1.0F * k, 0.85F * k, 0.5F * k);
            }
            default -> {
                float waver = 0.8F + 0.2F * Mth.sin(time * 0.35F + (float) to.z);
                ribbon(pose, buffers.getBuffer(BEAM), cam, from, to, width, 0.5F * k * waver, 0.42F * k * waver, 0.22F * k * waver);
                ribbon(pose, buffers.getBuffer(BEAM), cam, from, to, width * 0.35F, 0.7F * k, 0.66F * k, 0.5F * k);
                glow(pose, buffers, cam, to, width * 2.5F, 0.6F * k, 0.5F * k, 0.25F * k);
            }
        }
    }

    /**
     * The beams as plain shapes (positions only, camera-relative, a little wider than their glow) into a buffer - the
     * screen effects' mask of a thing (client.ScreenFxClient) takes them in, so they stay as they are with it.
     */
    public static void maskInto(com.mojang.blaze3d.vertex.BufferBuilder into, Vec3 cam) {
        for (Beam b : BEAMS.values()) {
            float w = b.kind == BeamPayload.LASER ? Math.max(0.35F, b.width * 3) : b.width * 2.6F;
            Vec3 from = b.from.subtract(cam), to = b.to.subtract(cam);
            Vec3 axis = to.subtract(from);
            if (axis.lengthSqr() < 1.0E-6) continue;
            Vec3 side = axis.cross(from.add(to).scale(-0.5));
            if (side.lengthSqr() < 1.0E-8) side = axis.cross(new Vec3(0, 1, 0.01));
            side = side.normalize().scale(w / 2);
            Vec3[] q = {from.subtract(side), from.add(side), to.add(side), to.subtract(side)};
            for (Vec3 v : q) into.addVertex((float) v.x, (float) v.y, (float) v.z);
        }
    }

    /** A flat ribbon along from-to, width wide, turned about its own line to face the camera; both sides. */
    public static void ribbon(PoseStack pose, VertexConsumer vc, Vec3 cam, Vec3 from, Vec3 to, float width, float r, float g, float b) {
        Vec3 axis = to.subtract(from);
        double len = axis.length();
        if (len < 1.0E-3) return;
        Vec3 view = cam.subtract(from.add(to).scale(0.5));
        Vec3 side = axis.cross(view);
        if (side.lengthSqr() < 1.0E-8) side = axis.cross(new Vec3(0, 1, 0.01));
        side = side.normalize().scale(width / 2);
        Matrix4f m = pose.last().pose();
        float v1 = (float) (len / Math.max(0.5, width * 2));
        Vec3[] q = {from.subtract(side), from.add(side), to.add(side), to.subtract(side)};
        float[][] uv = {{0, 0}, {1, 0}, {1, v1}, {0, v1}};
        int cr = (int) (Mth.clamp(r, 0, 1) * 255), cg = (int) (Mth.clamp(g, 0, 1) * 255), cb = (int) (Mth.clamp(b, 0, 1) * 255);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                int j = pass == 0 ? i : 3 - i;
                vc.addVertex(m, (float) q[j].x, (float) q[j].y, (float) q[j].z).setColor(cr, cg, cb, 255).setUv(uv[j][0], uv[j][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 1, 0);
            }
        }
    }

    /** A round glow at a point, size across, facing the camera. */
    public static void glow(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 at, float size, float r, float g, float b) {
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        VertexConsumer vc = buffers.getBuffer(GLOW);
        Matrix4f m = pose.last().pose();
        float h = size / 2;
        float[][] c = {{-h, -h, 0, 1}, {h, -h, 1, 1}, {h, h, 1, 0}, {-h, h, 0, 0}};
        int cr = (int) (Mth.clamp(r, 0, 1) * 255), cg = (int) (Mth.clamp(g, 0, 1) * 255), cb = (int) (Mth.clamp(b, 0, 1) * 255);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] q = c[pass == 0 ? i : 3 - i];
                vc.addVertex(m, q[0], q[1], 0).setColor(cr, cg, cb, 255).setUv(q[2], q[3])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 1, 0);
            }
        }
        pose.popPose();
    }
}
