package com.divinis.client;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.divinis.boss.Bullets;
import com.divinis.net.BulletPayload;
import com.divinis.Divinis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import com.divinis.platform.Dist;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.client.ClientPlayerNetworkEvent;
import com.divinis.platform.event.client.RenderLevelStageEvent;

/**
 * Draws boss bullets (boss.Bullets) on the paths the server gave them:
 * - STAR: a white-hot core with a four-pointed flare, turning and twinkling, a streak of light behind it;
 * - PETAL: a cherry petal tumbling, a pink streak behind it;
 * - SPARK: a small star;
 * - EMBER: a spark of the sun, a long golden streak behind it;
 * - VOIDSTAR: a black star - a black core in a violet corona, a violet streak, space bending a little round it;
 * - PRISM: a white star with a streak of colour - the more colourful the higher its index (the world's layers).
 * Bullets of one ring are joined by a thread of light. One that hits is gone at once (a flash); the rest break up in
 * sparks when their life is over (black stars only vanish). When time stops for them they hang still.
 */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class BulletFx {
    private BulletFx() {}

    private static final RenderType STAR = RenderType.eyes(tex("creator_star"));
    private static final RenderType TRAIL = RenderType.eyes(tex("creator_trail"));
    private static final RenderType PETAL = RenderType.entityTranslucentEmissive(tex("creator_petal"));
    private static final RenderType VOIDSTAR = RenderType.entityTranslucentEmissive(tex("creator_voidstar"));
    /** times (game time, from - to) when bullets hang still */
    private static final java.util.List<long[]> PAUSES = new java.util.ArrayList<>();
    private static final int TRAIL_STEPS = 6;

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/effect/" + name + ".png");
    }

    private record Bullet(Bullets.Spec spec, long start) {}

    private static final Map<Integer, Bullet> BULLETS = new HashMap<>();

    public static void receive(BulletPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (p.pause() != 0) PAUSES.add(new long[] {p.start(), p.start() + Math.abs(p.pause()), p.pause() < 0 ? 1 : 0});
        for (Bullets.Spec s : p.spawn()) BULLETS.put(s.id(), new Bullet(s, p.start()));
        for (int id : p.gone()) {
            Bullet b = BULLETS.remove(id);
            if (b == null) continue;
            Vec3 at = b.spec().at(age(b, level.getGameTime()));
            for (int i = 0; i < 6; i++) {
                level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, (Math.random() - 0.5) * 0.2, (Math.random() - 0.5) * 0.2,
                        (Math.random() - 0.5) * 0.2);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BULLETS.clear();
        PAUSES.clear();
    }

    private static float age(Bullet b, float now) {
        return Bullets.age(b.start(), now, PAUSES);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BULLETS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            BULLETS.clear();
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long tick = level.getGameTime();
        // those whose life is over break up
        PAUSES.removeIf(p -> tick - p[1] > 1200);
        BULLETS.values().removeIf(b -> {
            if (age(b, tick) <= b.spec().delay() + b.spec().life()) return false;
            if (b.spec().kind() == Bullets.VOIDSTAR) return true;
            Vec3 at = b.spec().at(b.spec().delay() + b.spec().life());
            for (int i = 0; i < 4; i++) {
                level.addParticle(b.spec().kind() == Bullets.PETAL ? ParticleTypes.CHERRY_LEAVES : ParticleTypes.FIREWORK, at.x, at.y, at.z,
                        (Math.random() - 0.5) * 0.08, Math.random() * 0.05, (Math.random() - 0.5) * 0.08);
            }
            return true;
        });
        if (BULLETS.isEmpty()) return;
        Camera cam = event.getCamera();
        Vec3 c = cam.getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-c.x, -c.y, -c.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Map<Integer, Map<Integer, Vec3>> rings = new HashMap<>();
        for (Bullet b : BULLETS.values()) {
            Bullets.Spec s = b.spec();
            float age = age(b, tick + partial);
            Vec3 at = s.at(age);
            float in = Mth.clamp(age / 4, 0, 1); // it swells in as it appears
            float flight = age - s.delay();
            if (s.link() != 0) rings.computeIfAbsent(s.link(), k -> new HashMap<>()).put(s.index(), at);
            // the streak behind it
            if (flight > 0.5F) {
                Vec3 prev = at;
                for (int i = 1; i <= TRAIL_STEPS; i++) {
                    Vec3 p = s.at(Math.max(s.delay(), age - i * (s.kind() == Bullets.EMBER ? 2.2F : 0.8F)));
                    float k = (1 - i / (float) (TRAIL_STEPS + 1)) * in;
                    if (s.kind() == Bullets.PETAL) BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), c, prev, p, s.size() * 0.5F * k, k, 0.45F * k, 0.62F * k);
                    else if (s.kind() == Bullets.VOIDSTAR) BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), c, prev, p, s.size() * 0.6F * k, 0.5F * k, 0.15F * k, k);
                    else if (s.kind() == Bullets.PRISM) {
                        int rgb = Mth.hsvToRgb((s.id() * 0.13F + age * 0.01F) % 1, Math.min(1, s.index() / 10F), 1);
                        BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), c, prev, p, s.size() * 0.5F * k, (rgb >> 16 & 255) / 255F * k,
                                (rgb >> 8 & 255) / 255F * k, (rgb & 255) / 255F * k);
                    } else if (s.kind() == Bullets.EMBER) BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), c, prev, p, s.size() * 0.4F * k, k, 0.7F * k, 0.2F * k);
                    else BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), c, prev, p, s.size() * 0.55F * k, k, 0.86F * k, 0.5F * k);
                    prev = p;
                }
            }
            float spin = age * (s.kind() == Bullets.PETAL ? 0.35F : 0.12F) + s.id();
            if (s.kind() == Bullets.PETAL) {
                billboard(pose, buffers.getBuffer(PETAL), at, s.size() * in, spin, 1, 1, 1, 1);
            } else if (s.kind() == Bullets.VOIDSTAR) {
                billboard(pose, buffers.getBuffer(VOIDSTAR), at, 2.2F * s.size() * in, -spin, 1, 1, 1, 1);
                ScreenFxClient.lens(at, 0.35F * s.size(), 0.5F);
            } else if (s.kind() == Bullets.EMBER) {
                float twinkle = 0.8F + 0.2F * Mth.sin(age * 2.3F + s.id());
                billboard(pose, buffers.getBuffer(STAR), at, 1.1F * s.size() * in, spin, twinkle, 0.75F * twinkle, 0.3F * twinkle, 1);
            } else {
                float twinkle = 0.75F + 0.25F * Mth.sin(age * 1.7F + s.id() * 2.3F);
                float size = (s.kind() == Bullets.SPARK ? 0.6F : 1.6F) * s.size() * in;
                billboard(pose, buffers.getBuffer(STAR), at, size, spin, twinkle, twinkle * 0.95F, twinkle * 0.8F, 1);
            }
        }
        // threads of light joining a ring's bullets
        for (Map<Integer, Vec3> ring : rings.values()) {
            for (Map.Entry<Integer, Vec3> e : ring.entrySet()) {
                Vec3 next = ring.get(e.getKey() + 1);
                if (next != null) BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), c, e.getValue(), next, 0.12F, 0.9F, 0.8F, 0.5F);
            }
        }
        buffers.endBatch(TRAIL);
        buffers.endBatch(STAR);
        buffers.endBatch(PETAL);
        buffers.endBatch(VOIDSTAR);
        pose.popPose();
    }

    /** A camera-facing quad at a point, size across, rolled by `roll` radians. */
    static void billboard(PoseStack pose, VertexConsumer vc, Vec3 at, float size, float roll, float r, float g, float b, float a) {
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(roll));
        Matrix4f m = pose.last().pose();
        float h = size / 2;
        float[][] q = {{-h, -h, 0, 1}, {h, -h, 1, 1}, {h, h, 1, 0}, {-h, h, 0, 0}};
        int cr = (int) (Mth.clamp(r, 0, 1) * 255), cg = (int) (Mth.clamp(g, 0, 1) * 255), cb = (int) (Mth.clamp(b, 0, 1) * 255);
        int ca = (int) (Mth.clamp(a, 0, 1) * 255);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] v = q[pass == 0 ? i : 3 - i];
                vc.addVertex(m, v[0], v[1], 0).setColor(cr, cg, cb, ca).setUv(v[2], v[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 0, 1);
            }
        }
        pose.popPose();
    }
}
