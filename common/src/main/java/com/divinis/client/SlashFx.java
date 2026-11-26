package com.divinis.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.divinis.net.SlashPayload;
import com.divinis.Divinis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import com.divinis.platform.Dist;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.client.RenderLevelStageEvent;

/**
 * The Ronin's slashes (net.SlashPayload): a crescent of blood-red light along the cut - a thin white-hot core inside a
 * wide red edge, sharp at both ends - drawn in the instant it is made and fading; while it lasts the picture round it
 * is bent as round a black hole (ScreenFxClient.lens, at points along the cut), so the cut seems torn through space.
 */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class SlashFx {
    private SlashFx() {}

    private record Slash(Vec3 from, Vec3 to, Vec3 ctrl, float width, int life, float warp, long born) {}

    private static final List<Slash> SLASHES = new ArrayList<>();
    private static final int STEPS = 14;

    public static void receive(SlashPayload p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 mid = p.from().add(p.to()).scale(0.5);
        Vec3 along = p.to().subtract(p.from());
        Vec3 side = along.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-6) side = new Vec3(1, 0, 0);
        Vec3 ctrl = mid.add(side.normalize().scale(p.bend())).add(0, p.bend() * 0.15, 0);
        SLASHES.add(new Slash(p.from(), p.to(), ctrl, p.width(), Math.max(1, p.life()), p.warp(), level.getGameTime()));
    }

    private static Vec3 at(Slash s, float t) {
        float u = 1 - t;
        return s.from().scale(u * u).add(s.ctrl().scale(2 * u * t)).add(s.to().scale(t * t));
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SLASHES.isEmpty()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) {
            SLASHES.clear();
            return;
        }
        float now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        SLASHES.removeIf(s -> now - s.born() > s.life());
        if (SLASHES.isEmpty()) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (Slash s : SLASHES) {
            float age = (now - s.born()) / s.life();
            // drawn out in the first few ticks, then fading
            float drawn = Mth.clamp((now - s.born()) / 2.5F, 0, 1);
            float k = drawn * (1 - age) * (1 - age);
            if (k <= 0.01F) continue;
            Vec3 prev = at(s, 0);
            for (int i = 1; i <= STEPS; i++) {
                float t = i / (float) STEPS;
                if (t > drawn + 0.001F) break;
                Vec3 p = at(s, t);
                float taper = Mth.sin(Mth.PI * (t - 0.5F / STEPS));
                float w = s.width() * Math.max(0.12F, taper);
                BeamFx.ribbon(pose, buffers.getBuffer(BeamFx.BEAM), cam, prev, p, w * 2.4F, 0.75F * k, 0.02F * k, 0.05F * k);
                BeamFx.ribbon(pose, buffers.getBuffer(BeamFx.BEAM), cam, prev, p, w, 1.0F * k, 0.25F * k, 0.25F * k);
                BeamFx.ribbon(pose, buffers.getBuffer(BeamFx.BEAM), cam, prev, p, w * 0.3F, 1.0F * k, 0.9F * k, 0.85F * k);
                prev = p;
            }
            if (s.warp() > 0) {
                // space torn along the cut
                for (float t : new float[] {0.25F, 0.5F, 0.75F}) {
                    ScreenFxClient.lens(at(s, t), 1.4F + s.width() * 1.2F, s.warp() * k);
                }
            }
        }
        buffers.endBatch(BeamFx.BEAM);
        pose.popPose();
    }
}
