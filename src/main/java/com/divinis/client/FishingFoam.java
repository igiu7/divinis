package com.divinis.client;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.divinis.net.FishingPayloads;
import com.divinis.Divinis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The foam a fish leaves on the water as it swims to a bobber (FishingHookMixin sends where it is each tick): a wake
 * lying on the water - a strip of foam texture through the places it passed, narrow and thick behind the fish,
 * spreading and thinning as it gets older - and a churned patch where the fish is. When the fish bites, the patch
 * spreads out and fades; the wake fades after it. Lit like the world. The hound rod's foam lasts longer, wider, in
 * the colour of the nearest pool. Textures: tools/build_fishing_foam.py.
 */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class FishingFoam {
    private static final ResourceLocation WAKE = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/fishing_foam.png");
    private static final ResourceLocation HEAD = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/fishing_foam_head.png");
    private static final float LIFE = 40;      // ticks a bit of wake lasts
    private static final float HEAD_GONE = 16; // ticks the patch takes to spread out and fade when the fish has bitten
    private static final float LIFT = 0.012F;  // over the water, so the water does not hide it

    private record Point(double x, double y, double z, float born, float dist) {}

    private static final class Trail {
        final ArrayDeque<Point> points = new ArrayDeque<>();
        float last;
        int color = 0xFFFFFF; // the hound rod's: the nearest pool's
        boolean strong;       // the hound rod's: lasts twice as long, a third wider

        float life() {
            return strong ? LIFE * 2 : LIFE;
        }
    }

    private static final Map<Integer, Trail> TRAILS = new HashMap<>();

    private FishingFoam() {}

    public static void receive(FishingPayloads.Foam payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockPos water = BlockPos.containing(payload.x(), payload.y() - 1, payload.z());
        FluidState fluid = level.getFluidState(water);
        if (!fluid.is(FluidTags.WATER)) return;
        double y = water.getY() + fluid.getHeight(level, water) + LIFT;
        Trail trail = TRAILS.computeIfAbsent(payload.hook(), k -> new Trail());
        trail.color = payload.color() == 0 ? 0xFFFFFF : payload.color();
        trail.strong = payload.strong();
        float now = level.getGameTime();
        Point prev = trail.points.peekLast();
        float dist = prev == null ? 0 : prev.dist + (float) Math.hypot(payload.x() - prev.x, payload.z() - prev.z);
        trail.points.add(new Point(payload.x(), y, payload.z(), now, dist));
        trail.last = now;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TRAILS.clear();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || TRAILS.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            TRAILS.clear();
            return;
        }
        float now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        TRAILS.values().removeIf(t -> {
            t.points.removeIf(p -> now - p.born > t.life());
            return t.points.isEmpty() && now - t.last > HEAD_GONE;
        });
        if (TRAILS.isEmpty()) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (Trail t : TRAILS.values()) {
            wake(pose, buffers.getBuffer(RenderType.entityTranslucent(WAKE)), level, t, now, cam);
        }
        buffers.endBatch(RenderType.entityTranslucent(WAKE));
        for (Trail t : TRAILS.values()) {
            head(pose, buffers.getBuffer(RenderType.entityTranslucent(HEAD)), level, t, now, cam);
        }
        buffers.endBatch(RenderType.entityTranslucent(HEAD));
    }

    /** The strip through the trail's points, oldest to newest: wider and fainter the older (camera-relative). */
    private static void wake(PoseStack pose, VertexConsumer out, ClientLevel level, Trail t, float now, Vec3 cam) {
        if (t.points.size() < 2) return;
        Point[] p = t.points.toArray(new Point[0]);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(p[p.length - 1].x, p[p.length - 1].y + 0.5, p[p.length - 1].z));
        PoseStack.Pose last = pose.last();
        float[][] side = new float[p.length][];
        for (int i = 0; i < p.length; i++) {
            Point a = p[Math.max(0, i - 1)], b = p[Math.min(p.length - 1, i + 1)];
            float dx = (float) (b.x - a.x), dz = (float) (b.z - a.z);
            float len = Mth.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-4F) {
                dx = 0;
                dz = 1;
                len = 1;
            }
            float age = Mth.clamp((now - p[i].born) / t.life(), 0, 1);
            float half = (0.13F + 0.22F * age) * (t.strong ? 1.35F : 1); // the wake spreads as it ages
            float grow = Mth.clamp((p.length - 1 - i + 1) / 3F, 0, 1); // the newest bit opens from the fish
            half *= 0.4F + 0.6F * grow;
            float alpha = (float) Math.pow(1 - age, 1.4) * 0.9F;
            side[i] = new float[] {-dz / len * half, dx / len * half, alpha};
        }
        for (int i = 0; i < p.length - 1; i++) {
            Point a = p[i], b = p[i + 1];
            float va = a.dist * 1.6F, vb = b.dist * 1.6F; // fixed to the water: the foam does not slide
            double ax = a.x - cam.x, ay = a.y - cam.y, az = a.z - cam.z;
            double bx = b.x - cam.x, by = b.y - cam.y, bz = b.z - cam.z;
            vertex(out, last, ax + side[i][0], ay, az + side[i][1], 0, va, side[i][2], light, t.color);
            vertex(out, last, ax - side[i][0], ay, az - side[i][1], 1, va, side[i][2], light, t.color);
            vertex(out, last, bx - side[i + 1][0], by, bz - side[i + 1][1], 1, vb, side[i + 1][2], light, t.color);
            vertex(out, last, bx + side[i + 1][0], by, bz + side[i + 1][1], 0, vb, side[i + 1][2], light, t.color);
        }
    }

    /** The churned patch over the fish: two layers turning against each other; spreading and fading once it bit. */
    private static void head(PoseStack pose, VertexConsumer out, ClientLevel level, Trail t, float now, Vec3 cam) {
        Point newest = t.points.peekLast();
        if (newest == null) return;
        float gone = Mth.clamp((now - t.last - 1.5F) / HEAD_GONE, 0, 1);
        if (gone >= 1) return;
        double x = newest.x, z = newest.z;
        Point[] p = t.points.toArray(new Point[0]);
        if (gone == 0 && p.length > 1) { // glide from the place before to the newest
            Point before = p[p.length - 2];
            float f = Mth.clamp(now - t.last, 0, 1);
            x = Mth.lerp(f, before.x, newest.x);
            z = Mth.lerp(f, before.z, newest.z);
        }
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, newest.y + 0.5, z));
        float size = 0.34F + 0.5F * gone;
        float alpha = (1 - gone) * (1 - gone);
        for (int layer = 0; layer < 2; layer++) {
            pose.pushPose();
            pose.translate(x - cam.x, newest.y + 0.002 * (layer + 1) - cam.y, z - cam.z);
            pose.mulPose(Axis.YP.rotationDegrees((layer == 0 ? 1 : -1.6F) * now * 9 + layer * 70));
            float s = size * (layer == 0 ? 1 : 0.8F);
            PoseStack.Pose last = pose.last();
            float a = alpha * (layer == 0 ? 0.95F : 0.7F);
            vertex(out, last, -s, 0, -s, 0, 0, a, light, t.color);
            vertex(out, last, -s, 0, s, 0, 1, a, light, t.color);
            vertex(out, last, s, 0, s, 1, 1, a, light, t.color);
            vertex(out, last, s, 0, -s, 1, 0, a, light, t.color);
            pose.popPose();
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, double x, double y, double z, float u, float v,
                               float alpha, int light, int color) {
        out.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }
}
