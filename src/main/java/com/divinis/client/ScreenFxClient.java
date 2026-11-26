package com.divinis.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.divinis.net.ScreenFxPayload;
import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenEffect.Anchor;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import com.divinis.Divinis;

import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Draws the screen effects (screenfx.ScreenEffect) the server puts on: each runs for its ticks, fading in and out,
 * at its strength; several of the same kind take the strongest.
 *
 * To work with any shader pack (Iris/Oculus) everything is done after the world is finished, on what is on the
 * screen, or through the camera:
 * - the image effects (invert, blur, aberration, distort, desaturate, glitch, noise, pixelate) copy the finished frame
 *   and draw it back through one shader of our own (shaders/core/screen_fx), under the HUD - one pass for each
 *   place they are on: the whole screen, a circle round a thing (its middle projected onto the screen), only on a
 *   thing or everywhere but on it (its shape drawn white into a mask of our own, after the world - so no shader
 *   pack touches it - with the camera of that frame); there a flash lights just that part and OUTLINE draws a
 *   glowing outline round the shape (seen through walls, like the glowing effect);
 * - the overlays (flash, eyes, vignette, darkness, text) are drawn over it, still under the HUD;
 * - shake, tilt and fov turn the camera; darkness and fog close the fog in (shader packs that draw their own fog
 *   may ignore that, the darkness overlay stays); the sky effect draws a giant eye and stars round the camera
 *   with an entity render type, which shader packs handle.
 * Time runs on the real clock, so effects go on while the game is paused.
 */
public final class ScreenFxClient {
    private ScreenFxClient() {}

    private static final ResourceLocation EYE = tex("eye");
    private static final ResourceLocation VIGNETTE = tex("vignette");
    private static final ResourceLocation MOUTH = tex("mouth");
    private static final ResourceLocation SANDSTORM = tex("sandstorm");
    private static final ResourceLocation WEB = tex("web");
    private static final RenderType SKY_EYE = RenderType.entityTranslucentEmissive(EYE);
    private static final RenderType STAR = RenderType.eyes(tex("star"));

    private record Active(ScreenEffect effect, float start, int ticks, float strength, int color, String text, long seed,
            Anchor anchor, int entity, float radius) {}

    /** One place image effects are on (a pass of the shader). */
    private record Place(Anchor anchor, int entity, float radius) {
        static Place of(Active a) {
            return new Place(a.anchor(), a.anchor() == Anchor.SCREEN ? -1 : a.entity(), a.anchor() == Anchor.AREA ? a.radius() : 0);
        }
    }

    private static final List<ScreenEffect> IMAGE = List.of(ScreenEffect.INVERT, ScreenEffect.BLUR, ScreenEffect.ABERRATION,
            ScreenEffect.DISTORT, ScreenEffect.DESATURATE, ScreenEffect.GLITCH, ScreenEffect.NOISE, ScreenEffect.PIXELATE,
            ScreenEffect.OUTLINE, ScreenEffect.FLASH, ScreenEffect.AURA, ScreenEffect.HEAT, ScreenEffect.TIMESTOP, ScreenEffect.SHATTER,
            ScreenEffect.GLOW, ScreenEffect.REWIND);

    /** Space bending round a black hole this frame: where (world), the hole's radius, how much. */
    private record Lens(Vec3 at, float radius, float strength) {}

    private static final List<Lens> LENSES = new ArrayList<>(), LENSES_NOW = new ArrayList<>();

    /** A black hole asks for space to bend round it this frame (client.BlackHoleFx). */
    public static void lens(Vec3 at, float radius, float strength) {
        if (LENSES.size() < 8) LENSES.add(new Lens(at, radius, strength));
    }

    /** A shock wave this frame: where (world), how far it has run, how wide its crest (blocks), how strong. */
    private record Shock(Vec3 at, float radius, float width, float strength) {}

    /** A radial blur this frame: towards where (world), how strong. */
    private record Radial(Vec3 at, float strength) {}

    private static final List<Shock> SHOCKS = new ArrayList<>(), SHOCKS_NOW = new ArrayList<>();
    private static final List<Radial> RADIALS = new ArrayList<>(), RADIALS_NOW = new ArrayList<>();
    private static float kaleido, kaleidoNow, kaleidoColor, kaleidoColorNow, edgeAberration, edgeAberrationNow;
    @Nullable private static TextureTarget depthCopy;

    /** A shock wave rolling out over the picture this frame (client.CreatorFx). */
    public static void shock(Vec3 at, float radius, float width, float strength) {
        if (SHOCKS.size() < 8) SHOCKS.add(new Shock(at, radius, width, strength));
    }

    /**
     * The true form's pass this frame (client.CreatorFx): the picture overexposed (exposure), the world's colour drained
     * (bleach) and flat white within whiteRadius of `at`, its colour back within restoreRadius of restoreAt (< 0: nowhere);
     * revealY (not NaN): the world black above that height, being made layer by layer.
     */
    public record CreatorView(float exposure, float bleach, Vec3 at, float whiteRadius, Vec3 restoreAt, float restoreRadius, double revealY) {}

    @Nullable private static CreatorView creatorView, creatorViewNow;
    @Nullable private static Matrix4f invViewProj;

    public static void creatorView(CreatorView view) {
        creatorView = view;
    }

    /** The colours split towards the edges of the screen this frame (with a kaleidoscope sky). */
    public static void edgeAberration(float strength) {
        edgeAberration = Math.max(edgeAberration, strength);
    }

    /** The picture blurred towards a point this frame. */
    public static void radial(Vec3 at, float strength) {
        if (RADIALS.size() < 2) RADIALS.add(new Radial(at, strength));
    }

    /** The sky turned into a kaleidoscope of the world below it this frame (0..1), grey, (colour 1) in colour or (2) white. */
    public static void kaleido(float strength, float colour) {
        kaleido = Math.max(kaleido, strength);
        kaleidoColor = Math.max(kaleidoColor, colour);
    }

    /** Where a point of the world is on the screen in the last frame: -1..1 each way, or null when behind the camera. */
    @Nullable
    public static Vector4f onScreen(Vec3 world) {
        if (lastView == null || lastProj == null) return null;
        return project(world.subtract(lastCam));
    }

    private static final List<Active> ACTIVE = new ArrayList<>();
    @Nullable private static ShaderInstance shader;
    @Nullable private static TextureTarget copy;
    @Nullable private static TextureTarget mask;
    // the camera of the last world frame (for things' places and shapes on the screen)
    @Nullable private static Matrix4f lastView, lastProj;
    private static Vec3 lastCam = Vec3.ZERO;
    private static float lastPartial;

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/screenfx/" + name + ".png");
    }

    private static float now() {
        return Util.getMillis() / 50.0F; // ticks, on the real clock
    }

    public static void receive(ScreenFxPayload p) {
        if (p.effect() == ScreenFxPayload.CLEAR) {
            ACTIVE.clear();
            return;
        }
        ScreenEffect effect = ScreenEffect.byIndex(p.effect());
        if (effect == null || p.ticks() <= 0) return;
        int color = p.color() >= 0 ? p.color() : effect.defaultColor;
        Anchor anchor = p.anchor() >= 0 && p.anchor() < Anchor.values().length ? Anchor.values()[p.anchor()] : Anchor.SCREEN;
        ACTIVE.add(new Active(effect, now(), p.ticks(), p.strength(), color, p.text(), RandomSource.create().nextLong(),
                anchor, p.entity(), p.radius()));
    }

    /** How strong an effect is now: its strength, faded in and out (0 if it is not on). */
    private static float envelope(Active a, float t) {
        float age = t - a.start();
        if (age < 0 || age > a.ticks()) return 0;
        float in, out;
        if (a.effect() == ScreenEffect.FLASH) {
            in = 1;
            out = a.ticks() * 0.85F;
        } else {
            in = Math.max(1, Math.min(10, a.ticks() / 4.0F));
            out = Math.max(1, Math.min(20, a.ticks() / 3.0F));
        }
        return a.strength() * Math.min(1, age / in) * Math.min(1, (a.ticks() - age) / out);
    }

    private static float level(ScreenEffect effect, float t) {
        float best = 0;
        for (Active a : ACTIVE) if (a.effect() == effect) best = Math.max(best, envelope(a, t));
        return best;
    }

    private static float level(ScreenEffect effect, float t, Place place) {
        float best = 0;
        for (Active a : ACTIVE) if (a.effect() == effect && Place.of(a).equals(place)) best = Math.max(best, envelope(a, t));
        return best;
    }

    @Nullable
    private static Active strongest(ScreenEffect effect, float t, Place place) {
        Active best = null;
        float bestLevel = 0;
        for (Active a : ACTIVE) {
            float l = envelope(a, t);
            if (a.effect() == effect && l > bestLevel && Place.of(a).equals(place)) {
                bestLevel = l;
                best = a;
            }
        }
        return best;
    }

    /** The strongest running instance of an effect, or null. */
    @Nullable
    private static Active strongest(ScreenEffect effect, float t) {
        Active best = null;
        float bestLevel = 0;
        for (Active a : ACTIVE) {
            float l = envelope(a, t);
            if (a.effect() == effect && l > bestLevel) {
                bestLevel = l;
                best = a;
            }
        }
        return best;
    }

    private static boolean any() {
        if (ACTIVE.isEmpty()) return false;
        float t = now();
        ACTIVE.removeIf(a -> t - a.start() > a.ticks());
        return !ACTIVE.isEmpty();
    }

    // ------------------------------------------------------------------ registration (mod bus)

    @EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {}

        @SubscribeEvent
        public static void onShaders(RegisterShadersEvent event) throws IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "screen_fx"), DefaultVertexFormat.POSITION_TEX), s -> shader = s);
        }

        @SubscribeEvent
        public static void onLayers(RegisterGuiLayersEvent event) {
            event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "screen_fx"), ScreenFxClient::renderScreen);
        }
    }

    // ------------------------------------------------------------------ camera and fog (game bus)

    @EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
    public static final class Camera3d {
        private Camera3d() {}

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ACTIVE.clear();
        }

        @SubscribeEvent
        public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
            if (!any()) return;
            float t = now();
            float shake = level(ScreenEffect.SHAKE, t);
            if (shake > 0) {
                float a = shake * 2.5F;
                event.setYaw(event.getYaw() + wobble(t, 1.0F) * a);
                event.setPitch(event.getPitch() + wobble(t + 37, 1.3F) * a);
                event.setRoll(event.getRoll() + wobble(t + 91, 1.1F) * a * 0.7F);
            }
            float tilt = level(ScreenEffect.TILT, t);
            if (tilt > 0) event.setRoll(event.getRoll() + tilt * (18.0F + Mth.sin(t * 0.06F) * 4.0F));
        }

        /** A shaky -1..1 from a few sines. */
        private static float wobble(float t, float speed) {
            return (Mth.sin(t * 2.3F * speed) * 0.5F + Mth.sin(t * 3.7F * speed + 1.3F) * 0.3F + Mth.sin(t * 6.1F * speed + 4.1F) * 0.2F);
        }

        @SubscribeEvent
        public static void onFov(ViewportEvent.ComputeFov event) {
            if (!any()) return;
            float fov = level(ScreenEffect.FOV, now());
            if (fov > 0) event.setFOV(Math.min(170, event.getFOV() * (1 + 0.35 * fov)));
        }

        @SubscribeEvent
        public static void onFog(ViewportEvent.RenderFog event) {
            if (!any()) return;
            float t = now();
            float dark = Math.min(1, level(ScreenEffect.DARKNESS, t));
            float fog = Math.min(1, level(ScreenEffect.FOG, t));
            float sky = Math.min(1, level(ScreenEffect.SKY, t));
            if (dark <= 0 && fog <= 0 && sky <= 0) return;
            float far = event.getFarPlaneDistance();
            float near = event.getNearPlaneDistance();
            if (fog > 0) {
                far = Mth.lerp(fog, far, 28);
                near = Mth.lerp(fog, near, 2);
            }
            if (dark > 0) {
                far = Mth.lerp(dark, far, 7);
                near = Mth.lerp(dark, near, 0);
            }
            event.setFarPlaneDistance(far);
            event.setNearPlaneDistance(near);
            event.setCanceled(true); // the changed distances only apply when cancelled
        }

        @SubscribeEvent
        public static void onFogColor(ViewportEvent.ComputeFogColor event) {
            if (!any()) return;
            float t = now();
            float r = event.getRed(), g = event.getGreen(), b = event.getBlue();
            Active fogA = strongest(ScreenEffect.FOG, t);
            if (fogA != null) {
                float f = Math.min(1, envelope(fogA, t));
                int c = fogA.color();
                r = Mth.lerp(f, r, (c >> 16 & 255) / 255.0F);
                g = Mth.lerp(f, g, (c >> 8 & 255) / 255.0F);
                b = Mth.lerp(f, b, (c & 255) / 255.0F);
            }
            float sky = Math.min(1, level(ScreenEffect.SKY, t));
            if (sky > 0) {
                r = Mth.lerp(sky, r, 0.03F);
                g = Mth.lerp(sky, g, 0.01F);
                b = Mth.lerp(sky, b, 0.06F);
            }
            float dark = Math.min(1, level(ScreenEffect.DARKNESS, t));
            if (dark > 0) {
                r *= 1 - dark;
                g *= 1 - dark;
                b *= 1 - dark;
            }
            event.setRed(r);
            event.setGreen(g);
            event.setBlue(b);
        }

        @SubscribeEvent
        public static void onLevelDone(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
            lastView = new Matrix4f(event.getModelViewMatrix());
            lastProj = new Matrix4f(event.getProjectionMatrix());
            lastCam = event.getCamera().getPosition();
            lastPartial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            // what this frame drew: the black holes that bend space, the flames for things' shapes
            LENSES_NOW.clear();
            LENSES_NOW.addAll(LENSES);
            LENSES.clear();
            SHOCKS_NOW.clear();
            SHOCKS_NOW.addAll(SHOCKS);
            SHOCKS.clear();
            RADIALS_NOW.clear();
            RADIALS_NOW.addAll(RADIALS);
            RADIALS.clear();
            kaleidoNow = kaleido;
            kaleidoColorNow = kaleidoColor;
            edgeAberrationNow = edgeAberration;
            kaleido = kaleidoColor = edgeAberration = 0;
            creatorViewNow = creatorView;
            creatorView = null;
            if (kaleidoNow > 0 || creatorViewNow != null) copyDepth(); // the depth is cleared before the hand and the HUD
            invViewProj = new Matrix4f(lastProj).mul(lastView).invert();
            FireFx.frameDone();
        }

        /** The sky: a giant eye opening and stars, all round the camera (so as far away as the sky). */
        @SubscribeEvent
        public static void onLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !any()) return;
            float t = now();
            Active sky = strongest(ScreenEffect.SKY, t);
            if (sky == null) return;
            float s = Math.min(1, envelope(sky, t));
            Minecraft mc = Minecraft.getInstance();
            Camera cam = event.getCamera();
            float dist = Mth.clamp(mc.options.getEffectiveRenderDistance() * 16 * 0.45F, 20, 80);
            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            RandomSource rnd = RandomSource.create(sky.seed());
            float eyeYaw = rnd.nextFloat() * Mth.TWO_PI;
            // the eye, high up, facing the camera
            Vec3 dir = new Vec3(Mth.sin(eyeYaw) * 0.75, 0.66, Mth.cos(eyeYaw) * 0.75).normalize();
            float age = t - sky.start();
            int frame = age < 6 ? 0 : age < 12 ? 1 : (sky.ticks() - age < 10 ? 1 : ((int) (age / 8) % 6 == 0 ? 3 : 2));
            pose.pushPose();
            pose.translate(dir.x * dist, dir.y * dist, dir.z * dist);
            pose.mulPose(cam.rotation());
            float size = dist * 0.9F;
            quad(pose, buffers.getBuffer(SKY_EYE), size, size * 0.5F, frame * 0.25F, frame * 0.25F + 0.25F, 1, 1, 1, s);
            pose.popPose();
            // stars across the upper sky, twinkling
            for (int i = 0; i < 140; i++) {
                float a = rnd.nextFloat() * Mth.TWO_PI, up = 0.1F + rnd.nextFloat() * 0.9F;
                float side = Mth.sqrt(1 - up * up);
                Vec3 at = new Vec3(Mth.cos(a) * side, up, Mth.sin(a) * side).scale(dist * 1.05);
                float twinkle = 0.55F + 0.45F * Mth.sin(t * (0.1F + rnd.nextFloat() * 0.2F) + i);
                pose.pushPose();
                pose.translate(at.x, at.y, at.z);
                pose.mulPose(cam.rotation());
                float sz = dist * (0.012F + rnd.nextFloat() * 0.012F);
                quad(pose, buffers.getBuffer(STAR), sz, sz, 0, 1, s * twinkle, s * twinkle, s * twinkle, 1);
                pose.popPose();
            }
            buffers.endBatch(SKY_EYE);
            buffers.endBatch(STAR);
        }

        private static void quad(PoseStack pose, VertexConsumer vc, float w, float h, float u0, float u1, float r, float g, float b, float a) {
            Matrix4f m = pose.last().pose();
            float x = w / 2, y = h / 2;
            float[][] c = {{-x, -y, u0, 1}, {x, -y, u1, 1}, {x, y, u1, 0}, {-x, y, u0, 0}};
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < 4; i++) {
                    float[] q = c[pass == 0 ? i : 3 - i];
                    vc.addVertex(m, q[0], q[1], 0).setColor(r, g, b, a).setUv(q[2], q[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 0, 1);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the screen (a HUD layer under all others)

    private static void renderScreen(GuiGraphics g, DeltaTracker delta) {
        g.flush();
        // in this order: each pass tells the hand from the world by how the picture differs from the world's own
        if (creatorViewNow != null) creatorPass(g, creatorViewNow, false); // the world drained, being made
        if (kaleidoNow > 0 && depthCopy != null && shader != null && depthValid) {
            ShaderInstance sh = shader;
            zero(sh);
            sh.safeGetUniform("Kaleido").set(Math.min(1, kaleidoNow));
            sh.safeGetUniform("KaleidoColor").set(Math.min(2, kaleidoColorNow));
            sh.safeGetUniform("EdgeAberration").set(edgeAberrationNow);
            RenderSystem.setShaderTexture(2, depthCopy.getDepthTextureId());
            RenderSystem.setShaderTexture(3, depthCopy.getColorTextureId());
            draw(g, sh, Minecraft.getInstance().getMainRenderTarget(), -1);
            kaleidoNow = 0;
        }
        if (creatorViewNow != null) creatorPass(g, creatorViewNow, true); // looked at too long: overexposed
        creatorViewNow = null;
        BloomFx.render(); // the light of glowing bodies
        for (Shock s : SHOCKS_NOW) shockPass(g, s);
        SHOCKS_NOW.clear();
        for (Radial r : RADIALS_NOW) radialPass(g, r);
        RADIALS_NOW.clear();
        CreatorFx.renderScreen(g);
        if (!LENSES_NOW.isEmpty()) {
            g.flush();
            for (Lens lens : LENSES_NOW) lensPass(g, lens);
            LENSES_NOW.clear();
        }
        if (!any()) return;
        float t = now();
        g.flush();
        List<Place> places = new ArrayList<>();
        for (Active a : ACTIVE) {
            if (!IMAGE.contains(a.effect()) || envelope(a, t) <= 0.002F) continue;
            if (a.anchor() == Anchor.SCREEN && (a.effect() == ScreenEffect.FLASH || a.effect() == ScreenEffect.OUTLINE
                    || a.effect() == ScreenEffect.AURA || a.effect() == ScreenEffect.HEAT)) continue;
            Place place = Place.of(a);
            if (!places.contains(place)) places.add(place);
        }
        places.sort((x, y) -> Integer.compare(x.anchor().ordinal(), y.anchor().ordinal())); // the whole screen first
        for (Place place : places) imagePass(g, t, place);
        int w = g.guiWidth(), h = g.guiHeight();

        Active storm = strongest(ScreenEffect.SANDSTORM, t);
        if (storm != null) sandstorm(g, w, h, storm, t);
        Active web = strongest(ScreenEffect.WEB, t);
        if (web != null) web(g, w, h, web, t);
        Active vig = strongest(ScreenEffect.VIGNETTE, t);
        if (vig != null) vignette(g, w, h, vig.color(), Math.min(1, envelope(vig, t)));
        float dark = Math.min(1, level(ScreenEffect.DARKNESS, t));
        if (dark > 0) {
            vignette(g, w, h, 0x000000, dark);
            g.fill(0, 0, w, h, argb(dark * 0.45F, 0x000000));
        }
        Active eyes = strongest(ScreenEffect.EYES, t);
        if (eyes != null) eyes(g, w, h, eyes, t);
        Active mouths = strongest(ScreenEffect.MOUTHS, t);
        if (mouths != null) mouths(g, w, h, mouths, t);
        Active flash = strongest(ScreenEffect.FLASH, t, new Place(Anchor.SCREEN, -1, 0));
        if (flash != null) {
            float f = Math.min(1, envelope(flash, t));
            g.fill(0, 0, w, h, argb(f, flash.color()));
            vignette(g, w, h, flash.color(), f); // the light floods in from the edges too
        }
        Active text = strongest(ScreenEffect.TEXT, t);
        if (text != null && !text.text().isEmpty()) text(g, w, h, text, t);
        g.flush();
    }

    private static int argb(float alpha, int rgb) {
        return (int) (Mth.clamp(alpha, 0, 1) * 255) << 24 | rgb & 0xFFFFFF;
    }

    private static void vignette(GuiGraphics g, int w, int h, int rgb, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor((rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F, (rgb & 255) / 255.0F, Mth.clamp(alpha, 0, 1));
        g.blit(VIGNETTE, 0, 0, 0, 0, w, h, w, h);
        g.setColor(1, 1, 1, 1);
    }

    /**
     * A sandstorm: the air thick with dust of its colour, and three layers of sand (sandstorm.png) blowing across the
     * screen, the nearer ones bigger, faster and fainter, gusting up and down; the edges darker.
     */
    private static void sandstorm(GuiGraphics g, int w, int h, Active a, float t) {
        float s = Math.min(1, envelope(a, t));
        if (s < 0.01F) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.fill(0, 0, w, h, argb(s * 0.3F, a.color()));
        for (int layer = 0; layer < 3; layer++) {
            int tw = (int) (h * (1.6F + layer * 1.1F)), th = tw / 2;
            float speed = (6 + layer * 9) * (h / 240.0F);
            int ox = Math.floorMod((int) (t * speed), tw);
            int oy = Math.floorMod((int) (Mth.sin(t * 0.04F + layer * 2) * th * 0.3F + layer * th / 3), th);
            g.setColor(1, 1, 1, s * (0.75F - layer * 0.2F));
            for (int x = -ox; x < w; x += tw) {
                for (int y = oy - th; y < h; y += th) g.blit(SANDSTORM, x, y, tw, th, 0, 0, 256, 128, 256, 128);
            }
        }
        g.setColor(1, 1, 1, 1);
        vignette(g, w, h, 0x5A3A14, s * 0.8F);
    }

    /**
     * Web stuck over the screen (web.png, stretched over it): it comes in from the edges as it starts, trembles as the
     * one caught in it struggles, a pale haze of silk over everything, and falls away at the end.
     */
    private static void web(GuiGraphics g, int w, int h, Active a, float t) {
        float s = Math.min(1, envelope(a, t));
        if (s < 0.01F) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.fill(0, 0, w, h, argb(s * 0.12F * Math.min(1, a.strength()), a.color()));
        vignette(g, w, h, a.color(), s * 0.35F);
        float grow = 1.25F - 0.25F * s; // closing in from beyond the edges
        int ww = (int) (w * grow), hh = (int) (h * grow);
        int jx = (int) (Mth.sin(t * 1.7F) * 2 * s), jy = (int) (Mth.cos(t * 2.3F) * 2 * s);
        g.setColor((a.color() >> 16 & 255) / 255F, (a.color() >> 8 & 255) / 255F, (a.color() & 255) / 255F, s);
        g.blit(WEB, (w - ww) / 2 + jx, (h - hh) / 2 + jy, ww, hh, 0, 0, 256, 256, 256, 256);
        g.setColor(1, 1, 1, 1);
    }

    /** Eyes open one after another all over the screen, blink now and then, and shut at the end. */
    private static void eyes(GuiGraphics g, int w, int h, Active a, float t) {
        float alpha = Math.min(1, envelope(a, t));
        RandomSource rnd = RandomSource.create(a.seed());
        int count = 5 + (int) (Math.min(3, a.strength()) * 5);
        float age = t - a.start();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int i = 0; i < count; i++) {
            float scale = 1.5F + rnd.nextFloat() * 2.0F;
            int ew = (int) (32 * scale), eh = (int) (16 * scale);
            int x = rnd.nextInt(Math.max(1, w - ew)), y = rnd.nextInt(Math.max(1, h - eh));
            float local = age - i * 3;
            float left = a.ticks() - age;
            if (local < 0) continue;
            int frame;
            if (local < 3) frame = 0;
            else if (local < 6) frame = 1;
            else if (left < 4) frame = 0;
            else if (left < 8) frame = 1;
            else {
                int blink = (int) ((local + i * 13) % 50);
                frame = blink < 3 ? 1 : (int) (local / 6) % 5 == 0 ? 3 : 2;
            }
            g.setColor(1, 1, 1, alpha);
            g.blit(EYE, x, y, ew, eh, frame * 32, 0, 32, 16, 128, 16);
        }
        g.setColor(1, 1, 1, 1);
    }

    /** Mouths open one after another all over the screen and scream - wide, shut a little, wide - then close. */
    private static void mouths(GuiGraphics g, int w, int h, Active a, float t) {
        float alpha = Math.min(1, envelope(a, t));
        RandomSource rnd = RandomSource.create(a.seed() ^ 0x5DEECE66DL);
        int count = 6 + (int) (Math.min(3, a.strength()) * 5);
        float age = t - a.start(), left = a.ticks() - age;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int i = 0; i < count; i++) {
            float scale = 1.6F + rnd.nextFloat() * 2.4F;
            int mw = (int) (32 * scale), mh = (int) (16 * scale);
            int x = rnd.nextInt(Math.max(1, w - mw)), y = rnd.nextInt(Math.max(1, h - mh));
            float local = age - i * 2;
            if (local < 0) continue;
            int frame;
            if (local < 2 || left < 3) frame = 0;
            else if (local < 4 || left < 6) frame = 1;
            else frame = ((int) ((local + i * 5) / 4)) % 3 == 0 ? 2 : 3;
            float shake = frame == 3 ? 1.5F : 0;
            g.setColor(1, 1, 1, alpha);
            g.blit(MOUTH, x + (int) (Mth.sin(t * 3.1F + i) * shake), y + (int) (Mth.cos(t * 2.7F + i) * shake), mw, mh, frame * 32, 0, 32, 16, 128, 16);
        }
        g.setColor(1, 1, 1, 1);
    }

    private static void text(GuiGraphics g, int w, int h, Active a, float t) {
        float alpha = Math.min(1, envelope(a, t));
        if (alpha < 0.02F) return;
        Font font = Minecraft.getInstance().font;
        Component line = Component.literal(a.text());
        float scale = 3.0F;
        float jitter = Math.min(3, a.strength()) * 1.2F;
        float jx = (Mth.sin(t * 2.7F) + Mth.sin(t * 5.3F)) * jitter * 0.5F, jy = (Mth.sin(t * 3.1F + 1) + Mth.sin(t * 6.7F)) * jitter * 0.5F;
        g.pose().pushPose();
        g.pose().translate(w / 2.0F + jx, h * 0.38F + jy, 0);
        g.pose().scale(scale, scale, 1);
        int color = Math.max(4, (int) (alpha * 255)) << 24 | a.color() & 0xFFFFFF;
        g.drawString(font, line, -font.width(line) / 2, -font.lineHeight / 2, color, true);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ the image pass

    /** Space bends round a black hole: the picture round its edge is pulled in towards it. */
    private static void lensPass(GuiGraphics g, Lens lens) {
        if (lastView == null || lastProj == null || shader == null) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        Vec3 rel = lens.at().subtract(lastCam);
        Vector4f c = project(rel);
        if (c == null) return;
        org.joml.Vector3f up = mc.gameRenderer.getMainCamera().getUpVector();
        Vector4f e = project(rel.add(up.x() * lens.radius(), up.y() * lens.radius(), up.z() * lens.radius()));
        if (e == null) return;
        float rPx = (float) Math.hypot((e.x - c.x) * main.width / 2, (e.y - c.y) * main.height / 2);
        if (rPx < 2) return;
        // close to the camera it would swallow the whole view: it fades away as the camera comes near (gone inside
        // it), and however close it is it never bends more than a part of the screen - seen from afar it is unchanged
        double dist = rel.length();
        float near = (float) Mth.clamp((dist - lens.radius() * 1.1) / (lens.radius() * 1.5 + 1.5), 0, 1);
        float strength = lens.strength() * near * near * (3 - 2 * near);
        float maxPx = main.height * 0.22F;
        if (rPx > maxPx) {
            strength *= maxPx / rPx;
            rPx = maxPx;
        }
        if (strength < 0.01F) return;
        ShaderInstance sh = shader;
        zero(sh);
        sh.safeGetUniform("Lens").set(c.x * 0.5F + 0.5F, c.y * 0.5F + 0.5F, rPx, strength);
        draw(g, sh, main, -1);
    }

    /** Whether the copied depth holds the world (a mod or a shader pack may leave it empty: then nothing would be sky). */
    private static boolean depthValid = true;
    private static int depthChecks;

    private static void copyDepth() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (depthCopy == null || depthCopy.width != main.width || depthCopy.height != main.height) {
            if (depthCopy != null) depthCopy.destroyBuffers();
            depthCopy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        }
        // the copy must store its depth as the screen does (with a stencil when a mod has turned one on), or the copy fails
        if (main.isStencilEnabled() && !depthCopy.isStencilEnabled()) depthCopy.enableStencil();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthCopy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        // and the world's picture as it is now, before the hand and the held item are drawn over it
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        if (depthChecks++ % 20 == 0) depthValid = checkDepth(depthCopy);
        main.bindWrite(true);
    }

    /**
     * Whether some of the bottom of the copied depth holds anything: if all of it is as far as the sky, the depth did
     * not come through, and the effects that need it are left off rather than covering the whole screen.
     */
    private static boolean checkDepth(TextureTarget copy) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, copy.frameBufferId);
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.FloatBuffer px = stack.mallocFloat(1);
            for (int i = 0; i < 5; i++) {
                GL11.glReadPixels(copy.width * (i + 1) / 6, copy.height / 8, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, px);
                if (px.get(0) < 0.99999F) return true;
            }
        }
        return false;
    }

    /** The true form's pass: the world drained and being made (exposure false), or the picture overexposed (true). */
    private static void creatorPass(GuiGraphics g, CreatorView v, boolean exposure) {
        if (depthCopy == null || shader == null || invViewProj == null) return;
        if (exposure && v.exposure() <= 0.001F) return;
        ShaderInstance sh = shader;
        zero(sh);
        sh.safeGetUniform("CreatorPass").set(1.0F);
        sh.safeGetUniform("Exposure").set(exposure ? Math.min(1, v.exposure()) : 0.0F);
        sh.safeGetUniform("Bleach").set(!exposure && depthValid ? Math.min(1, v.bleach()) : 0.0F);
        Vec3 b = v.at().subtract(lastCam), r = v.restoreAt().subtract(lastCam);
        sh.safeGetUniform("BleachAt").set((float) b.x, (float) b.y, (float) b.z, v.whiteRadius());
        sh.safeGetUniform("RestoreAt").set((float) r.x, (float) r.y, (float) r.z, v.restoreRadius());
        boolean reveal = !exposure && depthValid && !Double.isNaN(v.revealY());
        sh.safeGetUniform("Reveal").set(reveal ? 1.0F : 0.0F, reveal ? (float) (v.revealY() - lastCam.y) : 0.0F);
        sh.safeGetUniform("InvViewProj").set(invViewProj);
        RenderSystem.setShaderTexture(2, depthCopy.getDepthTextureId());
        RenderSystem.setShaderTexture(3, depthCopy.getColorTextureId());
        draw(g, sh, Minecraft.getInstance().getMainRenderTarget(), -1);
    }

    /** A shock wave: the picture pushed out and in along a ring round a point. */
    private static void shockPass(GuiGraphics g, Shock s) {
        if (shader == null) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        Vector4f c = onScreen(s.at());
        if (c == null) return;
        float pxPerBlock = pixelsPerBlock(s.at(), main);
        if (pxPerBlock <= 0) return;
        ShaderInstance sh = shader;
        zero(sh);
        sh.safeGetUniform("Shock").set(c.x * 0.5F + 0.5F, c.y * 0.5F + 0.5F, s.radius() * pxPerBlock, s.strength());
        sh.safeGetUniform("ShockWidth").set(Math.max(6, s.width() * pxPerBlock));
        draw(g, sh, main, -1);
    }

    private static void radialPass(GuiGraphics g, Radial r) {
        if (shader == null) return;
        Vector4f c = onScreen(r.at());
        float cx = c == null ? 0.5F : Mth.clamp(c.x * 0.5F + 0.5F, -0.5F, 1.5F), cy = c == null ? 0.5F : Mth.clamp(c.y * 0.5F + 0.5F, -0.5F, 1.5F);
        ShaderInstance sh = shader;
        zero(sh);
        sh.safeGetUniform("Radial").set(cx, cy, r.strength());
        draw(g, sh, Minecraft.getInstance().getMainRenderTarget(), -1);
    }

    /** How many pixels a block is across on the screen at a point (0 if behind the camera). */
    private static float pixelsPerBlock(Vec3 at, RenderTarget main) {
        Vector4f c = onScreen(at);
        if (c == null) return 0;
        org.joml.Vector3f up = Minecraft.getInstance().gameRenderer.getMainCamera().getUpVector();
        Vector4f e = onScreen(at.add(up.x(), up.y(), up.z()));
        if (e == null) return 0;
        return (float) Math.hypot((e.x - c.x) * main.width / 2, (e.y - c.y) * main.height / 2);
    }

    /** Every effect uniform off (a pass sets what it needs). */
    private static void zero(ShaderInstance sh) {
        for (String u : new String[] {"Invert", "Blur", "Aberration", "Distort", "Desaturate", "Glitch", "Noise", "Pixelate", "MaskMode",
                "MaskInvert", "AuraBlur", "Heat"}) {
            sh.safeGetUniform(u).set(0.0F);
        }
        sh.safeGetUniform("KeepColor").set(0.0F, 0.0F, 0.0F, 0.0F);
        sh.safeGetUniform("OutlineColor").set(0.0F, 0.0F, 0.0F, 0.0F);
        sh.safeGetUniform("Flash").set(0.0F, 0.0F, 0.0F, 0.0F);
        sh.safeGetUniform("Lens").set(0.0F, 0.0F, 1.0F, 0.0F);
        sh.safeGetUniform("Shock").set(0.0F, 0.0F, 0.0F, 0.0F);
        sh.safeGetUniform("Radial").set(0.5F, 0.5F, 0.0F);
        sh.safeGetUniform("Kaleido").set(0.0F);
        sh.safeGetUniform("KaleidoColor").set(0.0F);
        sh.safeGetUniform("Timestop").set(0.0F);
        sh.safeGetUniform("Shatter").set(0.0F, 0.0F);
        sh.safeGetUniform("Glow").set(0.0F, 0.0F, 0.0F, 0.0F);
        sh.safeGetUniform("Rewind").set(0.0F);
        sh.safeGetUniform("EdgeAberration").set(0.0F);
        sh.safeGetUniform("CreatorPass").set(0.0F);
        sh.safeGetUniform("Time").set(now() / 20.0F % 1000.0F);
    }

    /** Copies the frame and draws it back through the shader (Sampler1: the mask, or the copy again when -1). */
    private static void draw(GuiGraphics g, ShaderInstance sh, RenderTarget main, int maskTexture) {
        Minecraft mc = Minecraft.getInstance();
        if (copy == null || copy.width != main.width || copy.height != main.height) {
            if (copy != null) copy.destroyBuffers();
            copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);
        sh.safeGetUniform("OutSize").set((float) main.width, (float) main.height);
        RenderSystem.setShader(() -> sh);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        RenderSystem.setShaderTexture(1, maskTexture >= 0 ? maskTexture : copy.getColorTextureId());
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        Matrix4f m = g.pose().last().pose();
        float w = g.guiWidth(), h = g.guiHeight();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(m, 0, h, 0).setUv(0, 0);
        b.addVertex(m, w, h, 0).setUv(1, 0);
        b.addVertex(m, w, 0, 0).setUv(1, 1);
        b.addVertex(m, 0, 0, 0).setUv(0, 1);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (mc.level == null) return;
    }

    private static void imagePass(GuiGraphics g, float t, Place place) {
        float invert = Math.min(1, level(ScreenEffect.INVERT, t, place));
        float blur = level(ScreenEffect.BLUR, t, place);
        float aberration = level(ScreenEffect.ABERRATION, t, place);
        float distort = level(ScreenEffect.DISTORT, t, place);
        float desaturate = Math.min(1, level(ScreenEffect.DESATURATE, t, place));
        float glitch = level(ScreenEffect.GLITCH, t, place);
        float noise = level(ScreenEffect.NOISE, t, place);
        float pixelate = level(ScreenEffect.PIXELATE, t, place);
        Active outline = strongest(ScreenEffect.OUTLINE, t, place);
        Active flash = strongest(ScreenEffect.FLASH, t, place);
        ShaderInstance sh = shader;
        if (sh == null) return;

        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        // where on the screen
        float maskMode = 0, maskInvert = 0;
        float cx = 0, cy = 0, radiusPx = 0;
        if (place.anchor() != Anchor.SCREEN) {
            Entity on = mc.level == null ? null : mc.level.getEntity(place.entity());
            if (on == null || lastView == null || lastProj == null) return;
            if (place.anchor() == Anchor.AREA) {
                Vec3 mid = on.getPosition(lastPartial).add(0, on.getBbHeight() / 2, 0).subtract(lastCam);
                Vector4f c = project(mid);
                if (c == null) return; // behind the camera
                org.joml.Vector3f up = mc.gameRenderer.getMainCamera().getUpVector();
                Vector4f e = project(mid.add(up.x() * place.radius(), up.y() * place.radius(), up.z() * place.radius()));
                if (e == null) return;
                cx = c.x * 0.5F + 0.5F;
                cy = c.y * 0.5F + 0.5F;
                radiusPx = (float) Math.hypot((e.x - c.x) * main.width / 2, (e.y - c.y) * main.height / 2);
                maskMode = 1;
            } else {
                drawMask(mc, main, on);
                maskMode = 2;
                maskInvert = place.anchor() == Anchor.EXCEPT ? 1 : 0;
            }
        }
        if (copy == null || copy.width != main.width || copy.height != main.height) {
            if (copy != null) copy.destroyBuffers();
            copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        }
        // copy what is on the screen now (the finished world, after any shader pack)
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);

        zero(sh);
        sh.safeGetUniform("OutSize").set((float) main.width, (float) main.height);
        sh.safeGetUniform("Time").set(t / 20.0F % 1000.0F);
        sh.safeGetUniform("Invert").set(invert);
        sh.safeGetUniform("Blur").set(blur);
        sh.safeGetUniform("Aberration").set(aberration);
        sh.safeGetUniform("Distort").set(distort);
        sh.safeGetUniform("Desaturate").set(desaturate);
        Active des = strongest(ScreenEffect.DESATURATE, t, place);
        int keep = des != null ? des.color() : -1;
        if (keep >= 0) sh.safeGetUniform("KeepColor").set((keep >> 16 & 255) / 255.0F, (keep >> 8 & 255) / 255.0F, (keep & 255) / 255.0F, 1.0F);
        else sh.safeGetUniform("KeepColor").set(0.0F, 0.0F, 0.0F, 0.0F);
        sh.safeGetUniform("Glitch").set(glitch);
        sh.safeGetUniform("Noise").set(noise);
        sh.safeGetUniform("Pixelate").set(pixelate);
        sh.safeGetUniform("MaskMode").set(maskMode);
        sh.safeGetUniform("MaskInvert").set(maskInvert);
        sh.safeGetUniform("Circle").set(cx, cy, radiusPx, radiusPx * 0.25F);
        float pxScale = Math.max(1, main.height / 720.0F);
        if (outline != null && maskMode == 2) {
            int c = outline.color();
            float fade = outline.strength() <= 0 ? 0 : Math.min(1, envelope(outline, t) / outline.strength());
            sh.safeGetUniform("OutlineColor").set((c >> 16 & 255) / 255.0F, (c >> 8 & 255) / 255.0F, (c & 255) / 255.0F, fade);
            sh.safeGetUniform("OutlineWidth").set((1.5F + 2.5F * Math.min(3, outline.strength())) * pxScale);
        } else {
            sh.safeGetUniform("OutlineColor").set(0.0F, 0.0F, 0.0F, 0.0F);
            sh.safeGetUniform("OutlineWidth").set(1.0F);
        }
        float heat = level(ScreenEffect.HEAT, t, place);
        if (heat > 0 && maskMode == 2) {
            sh.safeGetUniform("Heat").set(Math.min(2, heat));
            sh.safeGetUniform("HeatWidth").set((14 + 10 * Math.min(2, heat)) * pxScale);
        }
        float aura = level(ScreenEffect.AURA, t, place);
        if (aura > 0 && maskMode == 2) {
            sh.safeGetUniform("AuraBlur").set(Math.min(1.5F, aura));
            sh.safeGetUniform("AuraWidth").set((10 + 12 * Math.min(2, aura)) * pxScale);
        } else {
            sh.safeGetUniform("AuraBlur").set(0.0F);
            sh.safeGetUniform("AuraWidth").set(1.0F);
        }
        sh.safeGetUniform("Timestop").set(Math.min(1, level(ScreenEffect.TIMESTOP, t, place)));
        sh.safeGetUniform("Rewind").set(Math.min(1, level(ScreenEffect.REWIND, t, place)));
        Active shatter = strongest(ScreenEffect.SHATTER, t, place);
        if (shatter != null) sh.safeGetUniform("Shatter").set(Math.min(1.5F, envelope(shatter, t)), (float) (shatter.seed() & 1023));
        Active glow = strongest(ScreenEffect.GLOW, t, place);
        if (glow != null) {
            int c = glow.color();
            sh.safeGetUniform("Glow").set((c >> 16 & 255) / 255.0F, (c >> 8 & 255) / 255.0F, (c & 255) / 255.0F, Math.min(1.5F, envelope(glow, t)));
        }
        if (flash != null) {
            int c = flash.color();
            sh.safeGetUniform("Flash").set((c >> 16 & 255) / 255.0F, (c >> 8 & 255) / 255.0F, (c & 255) / 255.0F, Math.min(1, envelope(flash, t)));
        } else {
            sh.safeGetUniform("Flash").set(0.0F, 0.0F, 0.0F, 0.0F);
        }

        RenderSystem.setShader(() -> sh);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        RenderSystem.setShaderTexture(1, maskMode == 2 && mask != null ? mask.getColorTextureId() : copy.getColorTextureId());
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        Matrix4f m = g.pose().last().pose();
        float w = g.guiWidth(), h = g.guiHeight();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(m, 0, h, 0).setUv(0, 0);
        b.addVertex(m, w, h, 0).setUv(1, 0);
        b.addVertex(m, w, 0, 0).setUv(1, 1);
        b.addVertex(m, 0, 0, 0).setUv(0, 1);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** A point (camera-relative) on the screen, -1..1 each way, or null when it is behind the camera. */
    @Nullable
    private static Vector4f project(Vec3 rel) {
        Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F);
        lastView.transform(v);
        lastProj.transform(v);
        if (v.w <= 0.05F) return null;
        return new Vector4f(v.x / v.w, v.y / v.w, v.z / v.w, 1);
    }

    /** The thing's shape, white on black, into the mask - drawn again with the last frame's camera. */
    private static void drawMask(Minecraft mc, RenderTarget main, Entity on) {
        if (mask == null || mask.width != main.width || mask.height != main.height) {
            if (mask != null) mask.destroyBuffers();
            mask = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            mask.setFilterMode(GL11.GL_LINEAR);
        }
        mask.setClearColor(0, 0, 0, 0);
        mask.clear(Minecraft.ON_OSX);
        mask.bindWrite(true);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(lastProj, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.set(lastView);
        RenderSystem.applyModelViewMatrix();

        BufferBuilder shape = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        VertexConsumer into = new Positions(shape);
        VertexConsumer nothing = new Positions(null);
        MultiBufferSource buffers = type -> solidModel(type) ? into : nothing;
        Vec3 at = on.getPosition(lastPartial).subtract(lastCam);
        try {
            mc.getEntityRenderDispatcher().render(on, at.x, at.y, at.z, Mth.lerp(lastPartial, on.yRotO, on.getYRot()), lastPartial,
                    new PoseStack(), buffers, LightTexture.FULL_BRIGHT);
        } catch (RuntimeException ignored) {
            // a renderer that does not like being drawn twice: no shape this frame
        }
        BeamFx.maskInto(shape, lastCam); // its beams are part of it
        FireFx.maskInto(shape); // and the flames
        MeshData mesh = shape.build();
        if (mesh != null) {
            RenderSystem.setShader(GameRenderer::getPositionShader);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            BufferUploader.drawWithShader(mesh);
            RenderSystem.enableCull();
        }

        view.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        main.bindWrite(true);
    }

    /** Only a thing's own model counts for its shape (not its glow, flames, shadow, name or enchantment shine). */
    private static boolean solidModel(RenderType type) {
        if (type.mode() != VertexFormat.Mode.QUADS) return false;
        String name = type.toString();
        int colon = name.indexOf(':');
        if (name.startsWith("RenderType[") && colon > 0) name = name.substring(11, colon);
        return (name.contains("entity") || name.contains("armor")) && !name.contains("emissive") && !name.contains("shadow")
                && !name.contains("eyes") && !name.contains("glint") && !name.contains("swirl") && !name.contains("outline");
    }

    /** Takes only where the corners are (into a position-only buffer, or nowhere). */
    private static final class Positions implements VertexConsumer {
        @Nullable private final BufferBuilder into;

        Positions(@Nullable BufferBuilder into) {
            this.into = into;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (into != null) into.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
