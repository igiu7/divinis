package com.divinis.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import com.divinis.platform.Dist;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.client.ClientPlayerNetworkEvent;
import com.divinis.platform.event.client.RenderLevelStageEvent;

/**
 * Pixel-art fire, not Minecraft's particles: camera-facing quads with a looping flame flipbook in flat colours (drawn
 * with alpha, full light) and a stepped pixel glow added as light (textures from tools/models/build_fire_textures.py).
 * Used by the Wildfire, its fireballs and their explosions, and in purple ("void" fire) by the Serpent of Infinity:
 * - flame / glow / ember: one quad each, for renderers;
 * - burst: an explosion at a place - a flash, a ring of flame tongues thrown outwards, sparks flying up;
 * - wave: a ring of fire racing outwards over a whole room (the Wildfire's Inferno);
 * - lattice: a grid of purple flames joined along its lines (the Serpent's flame grid and coil);
 * - rift: a swirl of purple fire drawn into a dark core, collapsing at the end (the Serpent's gravity rift);
 * - line, wall, meteor, ring: the Serpent's second phase - burning trails, walls of fire with a gap travelling
 *   across the room, falling stars and shock rings.
 */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class FireFx {
    private FireFx() {}

    private static final ResourceLocation FLAME_TEX = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/effect/wildfire_flame.png");
    private static final ResourceLocation GLOW_TEX = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/effect/wildfire_glow.png");
    public static final RenderType FLAME = RenderType.entityTranslucentEmissive(FLAME_TEX);
    public static final RenderType GLOW = RenderType.eyes(GLOW_TEX);
    public static final RenderType VOID_FLAME = RenderType.entityTranslucentEmissive(
            ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/effect/serpent_flame.png"));
    public static final RenderType VOID_GLOW = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/effect/serpent_glow.png"));
    private static final int FRAMES = 8;
    // the flames drawn this frame (camera-relative), and those of the finished frame
    private static java.util.List<float[]> SHAPES = new java.util.ArrayList<>(), SHAPES_DONE = new java.util.ArrayList<>();

    /** The world is drawn: this frame's flames are the ones to take for things' shapes. */
    public static void frameDone() {
        java.util.List<float[]> t = SHAPES_DONE;
        SHAPES_DONE = SHAPES;
        SHAPES = t;
        SHAPES.clear();
    }

    /** The finished frame's flames as plain shapes (positions only, camera-relative) into a buffer. */
    public static void maskInto(com.mojang.blaze3d.vertex.BufferBuilder into) {
        for (float[] f : SHAPES_DONE) {
            float rx = Mth.cos(f[5]) * f[3] / 2, rz = Mth.sin(f[5]) * f[3] / 2;
            into.addVertex(f[0] - rx, f[1], f[2] - rz);
            into.addVertex(f[0] + rx, f[1], f[2] + rz);
            into.addVertex(f[0] + rx, f[1] + f[4], f[2] + rz);
            into.addVertex(f[0] - rx, f[1] + f[4], f[2] - rz);
        }
    }
    private static final float FPS = 10;


    // ------------------------------------------------------------------ single quads

    /** Animation time in seconds for a flame (ticks + partial tick). */
    public static float seconds(float ticks) {
        return ticks / 20.0F;
    }

    /**
     * A flame tongue standing on `base` (relative to the pose), w wide and h high, turned to the camera around the
     * vertical. seed picks the frame offset and the mirroring; the pixel colours are kept (r, g, b are for the
     * glows), brightness only fades it out (below 1).
     */
    public static void flame(PoseStack pose, MultiBufferSource buffers, Camera cam, Vec3 base, float w, float h, float time,
            int seed, float r, float g, float b, float brightness) {
        flame(FLAME, pose, buffers, cam, base, w, h, time, seed, brightness);
    }

    /** A purple flame tongue (the Serpent of Infinity's), as flame(). */
    public static void voidFlame(PoseStack pose, MultiBufferSource buffers, Camera cam, Vec3 base, float w, float h, float time,
            int seed, float brightness) {
        flame(VOID_FLAME, pose, buffers, cam, base, w, h, time, seed, brightness);
    }

    /** A flame tongue of any flame texture (8 frames side by side, as wildfire_flame.png), as flame(). */
    public static void flame(RenderType type, PoseStack pose, MultiBufferSource buffers, Camera cam, Vec3 base, float w, float h,
            float time, int seed, float brightness) {
        if (brightness > 0.15F && SHAPES.size() < 8000) {
            // where it is (camera-relative: the pose is only moved, the view turned by the camera) for things' shapes
            org.joml.Vector3f rel = pose.last().pose().transformPosition((float) base.x, (float) base.y, (float) base.z, new org.joml.Vector3f());
            SHAPES.add(new float[] {rel.x, rel.y, rel.z, w, h, cam.getYRot() * Mth.DEG_TO_RAD});
        }
        int frame = Math.floorMod((int) (time * FPS) + seed * 3, FRAMES);
        float u0 = frame / (float) FRAMES, u1 = (frame + 1) / (float) FRAMES;
        if ((seed & 1) == 1) {
            float t = u0;
            u0 = u1;
            u1 = t;
        }
        pose.pushPose();
        pose.translate(base.x, base.y, base.z);
        pose.mulPose(new Quaternionf().rotationY((float) Math.toRadians(-cam.getYRot())));
        quad(pose, buffers.getBuffer(type), -w / 2, 0, w / 2, h, u0, u1, 1, 0, 1, 1, 1, Mth.clamp(brightness, 0, 1), false);
        pose.popPose();
    }

    /** A soft round glow at `at` (relative to the pose), size across, facing the camera. */
    public static void glow(PoseStack pose, MultiBufferSource buffers, Camera cam, Vec3 at, float size, float r, float g, float b) {
        glow(GLOW, pose, buffers, cam, at, size, r, g, b);
    }

    /** A purple glow, as glow(). */
    public static void voidGlow(PoseStack pose, MultiBufferSource buffers, Camera cam, Vec3 at, float size, float strength) {
        glow(VOID_GLOW, pose, buffers, cam, at, size, strength, strength, strength);
    }

    private static void glow(RenderType type, PoseStack pose, MultiBufferSource buffers, Camera cam, Vec3 at, float size, float r, float g, float b) {
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(cam.rotation());
        quad(pose, buffers.getBuffer(type), -size / 2, -size / 2, size / 2, size / 2, 0, 1, 1, 0, r, g, b, 1, true);
        pose.popPose();
    }

    /** Sparks rising and swirling up from `base` in a column of the given radius and height, looping in time. */
    public static void embers(PoseStack pose, MultiBufferSource buffers, Camera cam, Vec3 base, float radius, float height, int count,
            float time, float strength) {
        for (int i = 0; i < count; i++) {
            float seed = hash(i);
            float phase = (time * (0.35F + seed * 0.3F) + i / (float) count) % 1.0F;
            float angle = seed * Mth.TWO_PI + phase * 4.0F;
            float rad = radius * (0.3F + 0.7F * phase) * (0.6F + 0.4F * hash(i + 77));
            Vec3 at = base.add(Mth.cos(angle) * rad, phase * height, Mth.sin(angle) * rad);
            float fade = (1 - phase) * strength;
            glow(pose, buffers, cam, at, 0.18F * (1 - phase * 0.5F), fade, fade * (0.75F - phase * 0.45F), fade * 0.25F);
        }
    }

    /** A quad from (x0, y0) to (x1, y1) in the pose's XY plane, both sides, full light. */
    private static void quad(PoseStack pose, VertexConsumer vc, float x0, float y0, float x1, float y1, float u0, float u1,
            float v0, float v1, float r, float g, float b, float a, boolean twoSided) {
        Matrix4f m = pose.last().pose();
        int cr = (int) Mth.clamp(r * 255, 0, 255), cg = (int) Mth.clamp(g * 255, 0, 255), cb = (int) Mth.clamp(b * 255, 0, 255);
        int ca = (int) Mth.clamp(a * 255, 0, 255);
        float[][] corners = {{x0, y0, u0, v0}, {x1, y0, u1, v0}, {x1, y1, u1, v1}, {x0, y1, u0, v1}};
        // the flame render type draws both sides by itself; the glow's (additive) culls, so it gets a back face too
        int passes = twoSided ? 2 : 1;
        for (int pass = 0; pass < passes; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] c = corners[pass == 0 ? i : 3 - i];
                vc.addVertex(m, c[0], c[1], 0).setColor(cr, cg, cb, ca).setUv(c[2], c[3])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 1, 0);
            }
        }
    }

    public static float hash(int i) {
        int h = i * 0x27d4eb2d;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0xffff) / 65535.0F;
    }

    // ------------------------------------------------------------------ bursts and waves

    private static final int BURST = 0, WAVE = 1, VOID_BURST = 2, LATTICE = 3, RIFT = 4, LINE = 5, WALL = 6, METEOR = 7, RING = 8, SHOCK = 9, EYE = 10, SHIELD = 11, MAW = 12, DARK_SHOCK = 13;
    private static final RenderType MAW_TOP = RenderType.entityTranslucent(
            ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/effect/maw.png"));
    private static final RenderType MAW_JAW = RenderType.entityCutoutNoCull(
            ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/effect/maw_jaw.png"));
    private static final RenderType WORLD_EYE = RenderType.entityTranslucentEmissive(
            ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/screenfx/eye.png"));
    private static final RenderType LIGHT_SHIELD = RenderType.eyes(
            ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/effect/light_shield.png"));

    /** an effect: what, where, its size (and line spacing), when it starts showing and how long it lasts (ticks) */
    private record Effect(int kind, Vec3 at, float size, float spacing, long start, int life) {}

    private static final List<Effect> EFFECTS = new ArrayList<>();

    /** An explosion of fire at a place, size blocks across (a fireball's hit, a shield shattering). */
    public static void burst(Vec3 at, float size) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) EFFECTS.add(new Effect(BURST, at, size, 0, level.getGameTime(), 16));
    }

    /** A ring of fire racing out from a place to the given radius (the Inferno going off). */
    public static void wave(Vec3 at, float radius) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) EFFECTS.add(new Effect(WAVE, at, radius, 0, level.getGameTime(), 30));
    }

    /** A boss's purple fire effect, sent by the server (net.FireFxPayload). */
    public static void receive(com.divinis.net.FireFxPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        long now = level.getGameTime();
        switch (p.kind()) {
            case com.divinis.net.FireFxPayload.VOID_BURST -> EFFECTS.add(new Effect(VOID_BURST, at, p.a(), 0, now, 16));
            case com.divinis.net.FireFxPayload.VOID_LATTICE -> EFFECTS.add(new Effect(LATTICE, at, p.a(), p.b(), now + p.delay(), p.duration()));
            case com.divinis.net.FireFxPayload.RIFT -> EFFECTS.add(new Effect(RIFT, at, p.a(), 0, now + p.delay(), p.duration()));
            case com.divinis.net.FireFxPayload.VOID_LINE -> EFFECTS.add(new Effect(LINE, at, p.a(), p.b(), now + p.delay(), p.duration()));
            case com.divinis.net.FireFxPayload.VOID_WALL -> EFFECTS.add(new Effect(WALL, at, p.a(), p.b(), now,
                    com.divinis.boss.SerpentOfInfinity.WALL_WARN + p.duration()));
            case com.divinis.net.FireFxPayload.METEOR -> EFFECTS.add(new Effect(METEOR, at, 0, p.delay(), now, p.delay() + 12));
            case com.divinis.net.FireFxPayload.VOID_RING -> EFFECTS.add(new Effect(RING, at, p.a(), 0, now + p.delay(), 10));
            case com.divinis.net.FireFxPayload.VOID_SHOCK -> EFFECTS.add(new Effect(SHOCK, at, 0, 0, now + p.delay(), p.duration()));
            case com.divinis.net.FireFxPayload.WORLD_EYE -> EFFECTS.add(new Effect(EYE, at, p.a(), 0, now + p.delay(), p.duration()));
            case com.divinis.net.FireFxPayload.LIGHT_SHIELD -> EFFECTS.add(new Effect(SHIELD, at, p.a(), p.b(), now + p.delay(), p.duration()));
            case com.divinis.net.FireFxPayload.MAW -> EFFECTS.add(new Effect(MAW, at, p.a(), p.delay(), now, p.duration()));
            case com.divinis.net.FireFxPayload.DARK_SHOCK -> EFFECTS.add(new Effect(DARK_SHOCK, at, 0, 0, now + p.delay(), p.duration()));
            default -> {}
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        EFFECTS.clear();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || EFFECTS.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            EFFECTS.clear();
            return;
        }
        float now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        EFFECTS.removeIf(e -> now - e.start() > e.life());
        if (EFFECTS.isEmpty()) return;
        Camera cam = event.getCamera();
        Vec3 c = cam.getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-c.x, -c.y, -c.z);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (Effect e : EFFECTS) {
            if (now < e.start()) continue; // not yet
            float k = (now - e.start()) / e.life(); // 0 .. 1
            float fade = 1 - k;
            float time = seconds(now);
            if (e.kind() == LATTICE) {
                lattice(pose, buffers, cam, e, now - e.start(), time);
            } else if (e.kind() == LINE) {
                line(pose, buffers, cam, e, now - e.start(), time);
            } else if (e.kind() == WALL) {
                wall(pose, buffers, cam, e, now - e.start(), time);
            } else if (e.kind() == MAW) {
                maw(pose, buffers, e, now - e.start());
            } else if (e.kind() == DARK_SHOCK) {
                float age = now - e.start();
                float r = (float) com.divinis.boss.Nocthariel.shockAt(age);
                float fadeOut = Math.min(1, (e.life() - age) / 6);
                int n = Math.max(12, (int) (r * Mth.TWO_PI / 0.8F));
                for (int i = 0; i < n; i++) {
                    float a = i * Mth.TWO_PI / n;
                    Vec3 at = e.at().add(Mth.cos(a) * r, 0.3, Mth.sin(a) * r);
                    BeamFx.glow(pose, buffers, cam.getPosition(), at, 1.4F, 0.8F * fadeOut, 0.04F * fadeOut, 0.06F * fadeOut);
                    if (i % 2 == 0) BeamFx.glow(pose, buffers, cam.getPosition(), at.add(0, 0.5, 0), 0.6F, fadeOut, 0.6F * fadeOut, 0.55F * fadeOut);
                }
            } else if (e.kind() == EYE) {
                worldEye(pose, buffers, cam, e, now - e.start());
            } else if (e.kind() == SHIELD) {
                lightShield(pose, buffers, e, now - e.start());
            } else if (e.kind() == SHOCK) {
                // a low wave of flames rolling outwards, bright along its crest: jump it as it reaches you
                float age = now - e.start();
                float r = (float) com.divinis.boss.SerpentOfInfinity.shockAt(age);
                float fadeOut = Math.min(1, (e.life() - age) / 6);
                int n = Math.max(12, (int) (r * Mth.TWO_PI / 0.7F));
                for (int i = 0; i < n; i++) {
                    float a = i * Mth.TWO_PI / n;
                    Vec3 at = e.at().add(Mth.cos(a) * r, 0, Mth.sin(a) * r);
                    voidFlame(pose, buffers, cam, at, 1.0F, 1.1F * fadeOut, time, i, fadeOut);
                    if (i % 3 == 0) voidGlow(pose, buffers, cam, at.add(0, 0.4, 0), 1.6F, 0.8F * fadeOut);
                }
            } else if (e.kind() == METEOR) {
                meteor(pose, buffers, cam, e, now - e.start(), time);
            } else if (e.kind() == RING) {
                float r = e.size(), h = 2.2F * fade;
                int n = (int) (r * Mth.TWO_PI / 0.9F);
                for (int i = 0; i < n; i++) {
                    float a = i * Mth.TWO_PI / n;
                    voidFlame(pose, buffers, cam, e.at().add(Mth.cos(a) * r, 0, Mth.sin(a) * r), 1.2F, h, time, i, 1);
                }
            } else if (e.kind() == RIFT) {
                rift(pose, buffers, cam, e, now - e.start(), time);
            } else if (e.kind() == VOID_BURST) {
                float s = e.size();
                voidGlow(pose, buffers, cam, e.at(), s * (1.2F + k * 1.6F), fade * 1.3F);
                for (int i = 0; i < 10; i++) {
                    float a = hash(i + (int) e.start()) * Mth.TWO_PI;
                    float r = s * 0.5F * Mth.sqrt(k) * 1.4F;
                    Vec3 at = e.at().add(Mth.cos(a) * r, -s * 0.25F + k * s * 0.4F, Mth.sin(a) * r);
                    voidFlame(pose, buffers, cam, at, s * 0.45F, s * 0.8F * fade, time, i, fade);
                }
            } else if (e.kind() == WAVE) {
                float r = e.size() * Mth.sqrt(k);
                int n = Math.max(12, (int) (r * Mth.TWO_PI / 1.6F));
                for (int i = 0; i < n; i++) {
                    float a = i * Mth.TWO_PI / n;
                    Vec3 at = e.at().add(Mth.cos(a) * r, 0, Mth.sin(a) * r);
                    flame(pose, buffers, cam, at, 1.8F, 3.2F * fade + 0.6F, time, i, 1.0F, 0.85F, 0.7F, fade * 1.2F);
                }
                glow(pose, buffers, cam, e.at().add(0, 1.5, 0), e.size() * 0.6F * fade, fade, fade * 0.6F, fade * 0.2F);
            } else {
                float s = e.size();
                glow(pose, buffers, cam, e.at(), s * (1.2F + k * 1.6F), fade * 1.4F, fade * 1.0F, fade * 0.5F);
                glow(pose, buffers, cam, e.at(), s * 0.6F * fade, fade, fade, fade * 0.8F);
                for (int i = 0; i < 10; i++) {
                    float a = hash(i + (int) e.start()) * Mth.TWO_PI;
                    float r = s * 0.5F * Mth.sqrt(k) * 1.4F;
                    Vec3 at = e.at().add(Mth.cos(a) * r, -s * 0.25F + k * s * 0.4F, Mth.sin(a) * r);
                    flame(pose, buffers, cam, at, s * 0.45F, s * 0.8F * fade, time, i, 1.0F, 0.8F, 0.6F, fade * 1.3F);
                }
                embers(pose, buffers, cam, e.at(), s * 0.6F, s * 1.6F * (0.3F + k), 14, time + e.start(), fade);
            }
        }
        buffers.endBatch(FLAME);
        buffers.endBatch(GLOW);
        buffers.endBatch(VOID_FLAME);
        buffers.endBatch(VOID_GLOW);
        buffers.endBatch(WORLD_EYE);
        buffers.endBatch(MAW_TOP);
        buffers.endBatch(MAW_JAW);
        buffers.endBatch(LIGHT_SHIELD);
        buffers.endBatch(BeamFx.GLOW);
        pose.popPose();
    }

    /** Purple flames along every line of a grid within a circle, side by side so they join; they rise and sink. */
    private static void lattice(PoseStack pose, MultiBufferSource buffers, Camera cam, Effect e, float age, float time) {
        float grow = Math.min(1, age / 4), sink = Math.min(1, (e.life() - age) / 6);
        float h = 1.7F * Math.min(grow, sink);
        if (h <= 0.05F) return;
        float r = e.size(), sp = e.spacing();
        int lines = (int) Math.floor(r / sp);
        int seed = 0;
        for (int axis = 0; axis < 2; axis++) {
            for (int i = -lines; i <= lines; i++) {
                float off = i * sp;
                float half = Mth.sqrt(Math.max(0, r * r - off * off));
                for (float t = -half; t <= half; t += 1.0F) {
                    seed++;
                    Vec3 at = axis == 0 ? e.at().add(off, 0, t) : e.at().add(t, 0, off);
                    voidFlame(pose, buffers, cam, at, 1.25F, h * (0.8F + 0.4F * hash(seed)), time + hash(seed), seed, 1);
                }
            }
        }
    }

    /** A mouth in the floor: it opens (wider until it bites), then jaws of teeth snap up out of it and sink. */
    private static void maw(PoseStack pose, MultiBufferSource buffers, Effect e, float age) {
        float bite = e.spacing(), size = e.size();
        float open = Mth.clamp(age / Math.max(1, bite), 0, 1);
        float fade = Mth.clamp((e.life() - age) / 8, 0, 1);
        Vec3 b = e.at().add(0, 0.03, 0);
        Matrix4f m = pose.last().pose();
        float h = size / 2 * (0.4F + 0.6F * open);
        VertexConsumer top = buffers.getBuffer(MAW_TOP);
        float[][] c = {{-h, -h, 0, 0}, {h, -h, 1, 0}, {h, h, 1, 1}, {-h, h, 0, 1}};
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] q = c[pass == 0 ? i : 3 - i];
                top.addVertex(m, (float) b.x + q[0], (float) b.y, (float) b.z + q[1]).setColor(1, 1, 1, fade).setUv(q[2], q[3])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 1, 0);
            }
        }
        if (age < bite) return;
        // the bite: jaws of teeth snapping up round the rim, leaning in
        float k = Mth.clamp((age - bite) / 3, 0, 1) * Mth.clamp((e.life() - age) / 10, 0, 1);
        float jh = size * 0.55F * k;
        VertexConsumer jaw = buffers.getBuffer(MAW_JAW);
        for (int i = 0; i < 6; i++) {
            float a0 = i * Mth.TWO_PI / 6, a1 = (i + 1) * Mth.TWO_PI / 6, r = size * 0.45F;
            float lean = 0.35F * k;
            float x0 = (float) b.x + Mth.cos(a0) * r, z0 = (float) b.z + Mth.sin(a0) * r;
            float x1 = (float) b.x + Mth.cos(a1) * r, z1 = (float) b.z + Mth.sin(a1) * r;
            float tx0 = (float) b.x + Mth.cos(a0) * r * (1 - lean), tz0 = (float) b.z + Mth.sin(a0) * r * (1 - lean);
            float tx1 = (float) b.x + Mth.cos(a1) * r * (1 - lean), tz1 = (float) b.z + Mth.sin(a1) * r * (1 - lean);
            float[][] q = {{x0, (float) b.y, z0, 0, 1}, {x1, (float) b.y, z1, 1, 1}, {tx1, (float) b.y + jh, tz1, 1, 0}, {tx0, (float) b.y + jh, tz0, 0, 0}};
            for (int pass = 0; pass < 2; pass++) {
                for (int j = 0; j < 4; j++) {
                    float[] v = q[pass == 0 ? j : 3 - j];
                    jaw.addVertex(m, v[0], v[1], v[2]).setColor(255, 255, 255, 255).setUv(v[3], v[4]).setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 1, 0);
                }
            }
        }
    }

    /** A giant eye in the air: it opens, stares, its pupil flares red before it fires, and it shuts. */
    private static void worldEye(PoseStack pose, MultiBufferSource buffers, Camera cam, Effect e, float age) {
        float life = e.life();
        int frame = age < 4 ? 0 : age < 8 ? 1 : life - age < 4 ? 0 : life - age < 8 ? 1 : (age > 16 && age < 28 ? 3 : 2);
        float w = e.size(), h = w / 2;
        pose.pushPose();
        pose.translate(e.at().x, e.at().y, e.at().z);
        pose.mulPose(cam.rotation());
        VertexConsumer vc = buffers.getBuffer(WORLD_EYE);
        Matrix4f m = pose.last().pose();
        float u0 = frame * 0.25F, u1 = u0 + 0.25F;
        float[][] c = {{-w / 2, -h / 2, u0, 1}, {w / 2, -h / 2, u1, 1}, {w / 2, h / 2, u1, 0}, {-w / 2, h / 2, u0, 0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] q = c[pass == 0 ? i : 3 - i];
                vc.addVertex(m, q[0], q[1], 0).setColor(255, 255, 255, 255).setUv(q[2], q[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 0, 1);
            }
        }
        pose.popPose();
        if (age > 14 && age < 30) {
            float f = 1 - Math.abs(age - 22) / 8;
            BeamFx.glow(pose, buffers, cam.getPosition(), e.at(), w * 0.7F, f, 0.05F * f, 0.04F * f);
        }
    }

    /** A pane of light standing in the floor: it rises out of it, glows, and sinks at the end. */
    private static void lightShield(PoseStack pose, MultiBufferSource buffers, Effect e, float age) {
        float rise = Mth.clamp(age / 6, 0, 1) * Mth.clamp((e.life() - age) / 8, 0, 1);
        if (rise <= 0) return;
        float yaw = e.size() * Mth.DEG_TO_RAD, w = e.spacing() / 2, h = 4.5F * rise;
        float sx = Mth.cos(yaw) * w, sz = Mth.sin(yaw) * w; // along the pane (it faces along yaw's normal)
        Vec3 b = e.at();
        VertexConsumer vc = buffers.getBuffer(LIGHT_SHIELD);
        Matrix4f m = pose.last().pose();
        float pulse = 0.8F + 0.2F * Mth.sin((age + (float) b.x) * 0.3F);
        int col = (int) (255 * pulse * rise);
        float[][] c = {{-1, 0, 0, 1}, {1, 0, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] q = c[pass == 0 ? i : 3 - i];
                vc.addVertex(m, (float) (b.x + sx * q[0]), (float) (b.y + h * q[1]), (float) (b.z + sz * q[0])).setColor(col, col, col, 255)
                        .setUv(q[2], q[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 1, 0);
            }
        }
    }

    /** A straight line of purple flames (a burning trail), rising and sinking. */
    private static void line(PoseStack pose, MultiBufferSource buffers, Camera cam, Effect e, float age, float time) {
        float h = 1.5F * Math.min(Math.min(1, age / 4), Math.min(1, (e.life() - age) / 8));
        if (h <= 0.05F) return;
        float yaw = e.size() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        int seed = 0;
        for (float t = 0; t <= e.spacing(); t += 0.9F) {
            seed++;
            voidFlame(pose, buffers, cam, e.at().add(fx * t, 0, fz * t), 1.3F, h * (0.8F + 0.4F * hash(seed)), time + hash(seed), seed, 1);
        }
    }

    /** A wall of purple flames with a gap, standing at the room's edge, then travelling across it. */
    private static void wall(PoseStack pose, MultiBufferSource buffers, Camera cam, Effect e, float age, float time) {
        float yaw = e.size() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw); // the way it travels
        double sx = -fz, sz = fx; // along the wall
        double along = com.divinis.boss.SerpentOfInfinity.wallAt(age);
        float h = 2.6F * Math.min(1, age / 5) * Math.min(1, (e.life() - age) / 5);
        if (h <= 0.05F) return;
        float gap = e.spacing(), half = com.divinis.boss.SerpentOfInfinity.WALL_HALF_LENGTH;
        int seed = 0;
        for (float s = -half; s <= half; s += 0.8F) {
            seed++;
            float d = Math.abs(s - gap);
            if (d < com.divinis.boss.SerpentOfInfinity.WALL_GAP) continue;
            Vec3 at = e.at().add(fx * along + sx * s, 0, fz * along + sz * s);
            boolean edge = d < com.divinis.boss.SerpentOfInfinity.WALL_GAP + 0.8F;
            voidFlame(pose, buffers, cam, at, 1.2F, h * (edge ? 1.4F : 0.85F + 0.3F * hash(seed)), time + hash(seed), seed, 1);
            if (edge) voidGlow(pose, buffers, cam, at.add(0, 1.2, 0), 2.4F, 0.9F);
        }
    }

    /** A star falling from high above onto the place, a burst when it lands. */
    private static void meteor(PoseStack pose, MultiBufferSource buffers, Camera cam, Effect e, float age, float time) {
        float fall = e.spacing();
        if (age < fall) {
            float k = age / fall;
            Vec3 at = e.at().add((1 - k) * 6, (1 - k) * 28 + 0.5, (1 - k) * 2);
            voidGlow(pose, buffers, cam, at, 2.2F, 1.0F);
            for (int i = 1; i <= 5; i++) {
                float kk = Math.max(0, k - i * 0.03F);
                voidGlow(pose, buffers, cam, e.at().add((1 - kk) * 6, (1 - kk) * 28 + 0.5, (1 - kk) * 2), 1.6F - i * 0.2F, 0.6F - i * 0.1F);
            }
        } else {
            float k = (age - fall) / 12, fade = 1 - k;
            voidGlow(pose, buffers, cam, e.at().add(0, 0.6, 0), 3.0F + k * 4, fade * 1.2F);
            for (int i = 0; i < 6; i++) {
                float a = i * Mth.TWO_PI / 6 + hash((int) e.start()) * 3;
                float r = 2.0F * Mth.sqrt(k);
                voidFlame(pose, buffers, cam, e.at().add(Mth.cos(a) * r, 0, Mth.sin(a) * r), 1.1F, 1.8F * fade, time, i, fade);
            }
        }
    }

    /** A rift: a black hole opening over the floor, three arms of purple fire spiralling into it; it caves in at the end. */
    private static void rift(PoseStack pose, MultiBufferSource buffers, Camera cam, Effect e, float age, float time) {
        float k = age / e.life(), r = e.size();
        Vec3 c = e.at().add(0, 1.2, 0);
        float open = Mth.clamp(age / 12, 0, 1) * Mth.clamp((e.life() - age) / 5, 0, 1);
        Vec3 core = e.at().add(0, 2.6, 0);
        BlackHoleFx.draw(pose, buffers, cam.getPosition(), core, core, 1.6F + 0.8F * k, open, time * 20, BlackHoleFx.VIOLET);
        for (int arm = 0; arm < 3; arm++) {
            for (int j = 0; j < 14; j++) {
                float d = (j / 14.0F + time * 0.6F) % 1.0F; // flowing inwards
                float dist = r * (1 - d);
                float a = arm * Mth.TWO_PI / 3 + d * 5.0F + time * 2.0F;
                Vec3 at = c.add(Mth.cos(a) * dist, -1.0 + d * 0.6, Mth.sin(a) * dist);
                voidFlame(pose, buffers, cam, at, 1.0F, 1.4F * (1 - d * 0.5F), time + j * 0.2F, arm * 31 + j, 1);
            }
        }
        if (k > 0.92F) voidGlow(pose, buffers, cam, core, r * 2.5F * (k - 0.92F) * 12.5F, 1.0F);
    }
}
