package com.divinis.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.divinis.boss.NamelessCreator;
import com.divinis.net.CreatorFxPayload;
import com.divinis.Divinis;

import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import com.divinis.platform.Dist;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.client.ClientPlayerNetworkEvent;
import com.divinis.platform.event.client.ClientTickEvent;
import com.divinis.platform.event.client.RegisterGuiLayersEvent;
import com.divinis.platform.event.client.RenderGuiLayerEvent;
import com.divinis.platform.event.client.RenderLevelStageEvent;
import com.divinis.platform.event.client.VanillaGuiLayers;

/**
 * The Nameless Creator's effects in the world (net.CreatorFxPayload), all drawn added as light:
 * - TRAJ: a thin white-gold line a star will fly along, fading;
 * - CUT: a dimensional cut - a faint white plane with a bright line through it, then it opens from the line into a
 *   plane of white and violet, its edges eaten away, and the picture stays torn along it for a second;
 * - GAP: a pillar of golden light in the gap of a ring of stars, going round with it;
 * - FLOWER: a camellia falling from its crown, growing where it lands over a circle of petals that brightens;
 * - STAR: the collapsing star - a ball of plasma in a corona of rays, space bending round it, light drawn in to it from
 *   all over the arena; it falls in on itself to a point - the screen black but for that white point - and goes off;
 * - MARK: a mark on the ground (and on the map that is its face), then a column of light falling onto it;
 * - SHOCK: a shock wave over the picture (client.ScreenFxClient), a radial blur at first;
 * - CRACK: the crack down the middle of the god, shining;
 * - TIMESTOP: time stands still (the particles hang still: mixin.ParticleFreezeMixin; the bullets: client.BulletFx);
 * - FIST: a gold fist (Aurelion's dead hand, clenched) in balls of light, or a violet one (Nocthariel's) dripping ooze,
 *   gathering high over a marked circle and falling on it;
 * - GWAVE: a low wall of light rolling out over the ground - jump it;
 * - VOIDZONE: a pool of black ooze, blobs swelling and shrinking in it;
 * - SWORD, SWORD_V: Orion's sword - stars appearing one by one up its blade and joined by lines, pulses of light
 *   running from hilt to tip; swung flat round the arena, a great sweep of light behind it; then raised and brought
 *   down along a line marked on the ground;
 * - ZONES: the arena split into squares of light (gold, motes of light rising) and shadow (violet, ooze), swapping;
 * - CRUSH: two hands, one gold and one violet, come in on the HUD, take the hunger bar between them and crush it to
 *   pieces.
 * - LSLASH, CHRONOS, CAGE, CHUNK, CHUNKGRID: the third phase's cuts of light, Chronos' beam, cages, the void;
 * - ERASE, ERASE_RING: cuts of pure white, the floor under them flat white until it is gone; the arena's edge erased;
 * - CHECKER: the floor a chequer of the missing texture, moving on a square every second;
 * - GIGA: the great beam - light gathering before its face, then a huge turning tube of light, a splash where it hits,
 *   the menu's splash texts drifting out along it;
 * - CREATION: the world made again - black, the face unfolding like a flower of chequered squares, one block before
 *   you, then the world coming back layer by layer from the bottom up (ScreenFxClient's true form pass).
 * Round its true form: how long you look at its face (GAZE, from the server) overexposes the picture, burns out its
 * bright parts, turns the HUD to the missing texture part by part and rings in your ears; the world's colour drains,
 * flat white round it, and comes back in a wave from where it falls.
 * Round its first phase the sky slows to a stop (client.CreatorSky); from its change on, the sky is a grey kaleidoscope,
 * in colour from its third phase, white in its true form.
 */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class CreatorFx {
    private CreatorFx() {}

    private static final RenderType TRAIL = RenderType.eyes(tex("creator_trail"));
    private static final RenderType PLANE = RenderType.eyes(tex("creator_plane"));
    private static final RenderType DECAL = RenderType.eyes(tex("creator_decal"));
    private static final RenderType MARK = RenderType.eyes(tex("creator_mark"));
    private static final RenderType COLUMN = RenderType.eyes(tex("creator_column"));
    private static final RenderType PLASMA = RenderType.eyes(tex("creator_plasma"));
    private static final RenderType CORONA = RenderType.eyes(tex("creator_corona"));
    private static final RenderType STAR = RenderType.eyes(tex("creator_star"));
    private static final RenderType FLOWER = RenderType.entityTranslucentEmissive(tex("creator_flower"));
    private static final RenderType BLOB = RenderType.entityTranslucentEmissive(tex("creator_blob"));
    private static final RenderType ZONE = RenderType.entityTranslucentEmissive(tex("creator_zone"));
    private static final RenderType GOLD_HAND = RenderType.entityCutoutNoCull(
            ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/aurelion_hand.png"));
    private static final RenderType VIOLET_HAND = RenderType.entityCutoutNoCull(
            ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/nocthariel_hand.png"));
    private static final ResourceLocation CRUSH_HANDS = tex("creator_crush");
    private static final ResourceLocation DIRT = ResourceLocation.withDefaultNamespace("textures/block/dirt.png");
    private static final ResourceLocation PANORAMA = ResourceLocation.withDefaultNamespace("textures/gui/title/background/panorama_0.png");
    private static final RenderType LOADING = RenderType.entityCutoutNoCull(DIRT);
    private static final RenderType VOID = RenderType.entitySolid(DIRT);
    private static final RenderType VOID_WHITE = RenderType.entitySolid(tex("creator_zone"));
    private static final RenderType[] TYPES = {VOID, VOID_WHITE, LOADING, ZONE, TRAIL, PLANE, DECAL, MARK, COLUMN, PLASMA, CORONA, STAR, FLOWER, BLOB,
            GOLD_HAND, VIOLET_HAND, RenderType.lines()};
    @javax.annotation.Nullable private static AurelionRenderer.Hand hand;

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/effect/" + name + ".png");
    }

    private record Fx(byte kind, Vec3 a, Vec3 b, float f0, float f1, float f2, float f3, int delay, int life, long start) {}

    private static final List<Fx> FX = new ArrayList<>();
    private static float kaleido, kaleidoGoal, kaleidoColor, edge;
    /** how long this player has looked at the true form's face (0..1), and when the server last said */
    private static float gaze;
    private static long gazeAt;

    public static float gaze() {
        return gaze;
    }

    /** Whether the sky is its kaleidoscope now (the clouds are left out then: mixin.LevelRendererSkyMixin). */
    public static boolean skyIsKaleidoscope() {
        return kaleido > 0.3F;
    }

    public static void receive(CreatorFxPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (p.kind() == CreatorFxPayload.GAZE) {
            gaze = p.f0();
            gazeAt = level.getGameTime();
            return;
        }
        FX.add(new Fx(p.kind(), p.a(), p.b(), p.f0(), p.f1(), p.f2(), p.f3(), p.delay(), p.life(), level.getGameTime()));
    }

    /** The places the map has marked that are still to be struck (client.CreatorMap). */
    static List<Vec3> marked(long now) {
        List<Vec3> out = new ArrayList<>();
        for (Fx f : FX) if (f.kind() == CreatorFxPayload.MARK && now - f.start() < f.delay()) out.add(f.a());
        return out;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        FX.clear();
        kaleido = kaleidoGoal = kaleidoColor = 0;
    }

    /** Whether time stands still now (the judgement). */
    public static boolean timeStopped() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || FX.isEmpty()) return false;
        long now = level.getGameTime();
        for (Fx f : FX) if (f.kind() == CreatorFxPayload.TIMESTOP && now - f.start() < f.life()) return true;
        return false;
    }

    // ------------------------------------------------------------------ each tick: the sky, the light drawn in

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) return;
        boolean stopping = false, spinning = false;
        kaleidoGoal = 0;
        float colour = 0;
        edge = 0;
        for (NamelessCreator c : level.getEntitiesOfClass(NamelessCreator.class, mc.player.getBoundingBox().inflate(96))) {
            if (c.state() == NamelessCreator.WAITING) continue;
            if (c.phase() == 1 && c.state() != NamelessCreator.TRANSFORM) stopping = true;
            if (c.phase() >= 2 || c.state() == NamelessCreator.TRANSFORM && c.stateAge(0) >= NamelessCreator.CRACK_AT) kaleidoGoal = 1;
            if (c.phase() >= 4) {
                colour = 2;
                edge = 1;
            } else if (c.phase() == 3 && c.state() == NamelessCreator.TRANSFORM) {
                float k = Mth.clamp((c.stateAge(0) - NamelessCreator.UNVEIL_DARK) / 20, 0, 1);
                colour = Math.max(colour, 1 + k);
                edge = Math.max(edge, k);
            } else if (c.phase() >= 3) {
                colour = Math.max(colour, 1);
            }
            else if (c.phase() == 2 && c.state() == NamelessCreator.TRANSFORM) colour = Math.max(colour, Mth.clamp((c.stateAge(0) - 40) / 60, 0, 1));
        }
        kaleidoColor = colour;
        long tickNow = level.getGameTime();
        for (Fx f : FX) {
            if (f.kind() == CreatorFxPayload.CHRONOS && tickNow - f.start() >= f.delay() && tickNow - f.start() < f.life()) spinning = true;
        }
        CreatorSky.tick(stopping, spinning);
        kaleido = Mth.approach(kaleido, kaleidoGoal, kaleidoGoal > kaleido ? 0.04F : 0.02F);
        long now = level.getGameTime();
        if (now - gazeAt > 10) gaze = Math.max(0, gaze - 0.05F);
        if (gaze > 0.2F && now % 6 == 0) { // a ringing in the ears, louder the longer you look
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_FLUTE.value(), 2.0F, gaze * 0.5F));
        }
        FX.removeIf(f -> now - f.start() > f.life() + 1);
        RandomSource rnd = level.random;
        for (Fx f : FX) {
            float age = now - f.start();
            if (f.kind() == CreatorFxPayload.STAR && age >= 20 && age < NamelessCreator.STAR_GROW_TO) {
                // light drawn in to the star from all over the arena
                for (int i = 0; i < 5; i++) {
                    double ang = rnd.nextDouble() * Mth.TWO_PI, r = 6 + rnd.nextDouble() * NamelessCreator.ARENA;
                    Vec3 p = f.a().add(Math.cos(ang) * r, -NamelessCreator.STAR_HEIGHT + rnd.nextDouble() * 14, Math.sin(ang) * r);
                    Vec3 v = f.a().subtract(p).scale(0.045);
                    level.addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, v.x, v.y, v.z);
                }
            }
            if (f.kind() == CreatorFxPayload.GIGA && age < f.delay()) { // light drawn in to the point before its face
                for (int i = 0; i < 4; i++) {
                    Vec3 p = f.a().add(rnd.nextGaussian() * 4, rnd.nextGaussian() * 3, rnd.nextGaussian() * 4);
                    Vec3 v = f.a().subtract(p).scale(0.08);
                    level.addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, v.x, v.y, v.z);
                }
            }
            if (f.kind() == CreatorFxPayload.VOIDZONE && rnd.nextInt(3) == 0) {
                double a = rnd.nextDouble() * Mth.TWO_PI, r = Math.sqrt(rnd.nextDouble()) * f.f0() / 2;
                level.addParticle(ParticleTypes.REVERSE_PORTAL, f.a().x + Math.cos(a) * r, f.a().y + 0.1, f.a().z + Math.sin(a) * r, 0, 0.03, 0);
            }
            if (f.kind() == CreatorFxPayload.FIST && age < f.delay() && rnd.nextInt(2) == 0) {
                Vec3 at = fistAt(f, age);
                level.addParticle(f.f0() < 0.5F ? ParticleTypes.END_ROD : ParticleTypes.DRIPPING_OBSIDIAN_TEAR, at.x + rnd.nextGaussian() * 0.6,
                        at.y + 0.5, at.z + rnd.nextGaussian() * 0.6, 0, -0.02, 0);
            }
            if (f.kind() == CreatorFxPayload.FLOWER && now - f.start() == f.delay()) {
                level.addParticle(ParticleTypes.CHERRY_LEAVES, f.b().x, f.b().y + 0.5, f.b().z, 0, 0.05, 0);
            }
            if (f.kind() == CreatorFxPayload.CUT && now - f.start() == f.delay()) {
                Vec3 d = f.b().subtract(f.a());
                for (int i = 0; i < 24; i++) {
                    Vec3 p = f.a().add(d.scale(rnd.nextDouble())).add(0, rnd.nextDouble() * f.f0(), 0);
                    level.addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, -0.03, 0);
                }
            }
        }
    }

    // ------------------------------------------------------------------ in the world

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (kaleido > 0.001F) ScreenFxClient.kaleido(kaleido, kaleidoColor);
        if (edge > 0.001F) ScreenFxClient.edgeAberration(edge);
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = level.getGameTime() + partial;
        creatorView(level, now);
        if (FX.isEmpty()) return;
        Camera cam = event.getCamera();
        Vec3 c = cam.getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-c.x, -c.y, -c.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Fx f : FX) {
            float age = now - f.start();
            if (age < 0 || age > f.life()) continue;
            switch (f.kind()) {
                case CreatorFxPayload.TRAJ -> traj(pose, buffers, c, f, age);
                case CreatorFxPayload.CUT -> cut(pose, buffers, c, f, age);
                case CreatorFxPayload.GAP -> gap(pose, buffers, c, f, age);
                case CreatorFxPayload.FLOWER -> flower(pose, buffers, f, age);
                case CreatorFxPayload.STAR -> star(pose, buffers, c, f, age);
                case CreatorFxPayload.MARK -> mark(pose, buffers, c, f, age, now);
                case CreatorFxPayload.SHOCK -> shock(f, age);
                case CreatorFxPayload.CRACK -> crack(pose, buffers, c, f, age);
                case CreatorFxPayload.FIST -> fist(pose, buffers, c, f, age);
                case CreatorFxPayload.GWAVE -> groundWave(pose, buffers, f, age);
                case CreatorFxPayload.VOIDZONE -> voidZone(pose, buffers, f, age);
                case CreatorFxPayload.SWORD -> sword(pose, buffers, c, f, age, false);
                case CreatorFxPayload.SWORD_V -> sword(pose, buffers, c, f, age, true);
                case CreatorFxPayload.ZONES -> zones(pose, buffers, c, f, age);
                case CreatorFxPayload.LSLASH -> lightSlash(pose, buffers, c, f, age);
                case CreatorFxPayload.CHRONOS -> chronos(pose, buffers, c, f, age, now);
                case CreatorFxPayload.CAGE -> cage(pose, buffers, f, age);
                case CreatorFxPayload.CHUNK -> chunk(pose, buffers, f, age);
                case CreatorFxPayload.CHUNKGRID -> chunkGrid(pose, buffers, f, age);
                case CreatorFxPayload.ERASE -> erase(pose, buffers, c, f, age);
                case CreatorFxPayload.ERASE_RING -> eraseRing(pose, buffers, f, age);
                case CreatorFxPayload.CHECKER -> checker(pose, buffers, f, age);
                case CreatorFxPayload.GIGA -> giga(pose, buffers, c, f, age, now);
                default -> {}
            }
        }
        for (RenderType t : TYPES) buffers.endBatch(t);
        pose.popPose();
    }

    private static void traj(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Fx f, float age) {
        float k = Math.min(1, age / 3) * Mth.clamp((f.life() - age) / 4, 0, 1) * (0.85F + 0.15F * Mth.sin(age * 2.1F));
        BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, f.a(), f.b(), 0.32F, 0.35F * k, 0.28F * k, 0.12F * k);
        BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, f.a(), f.b(), 0.09F, k, 0.92F * k, 0.68F * k);
    }

    private static void cut(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Fx f, float age) {
        Vec3 a = f.a(), b = f.b();
        float h = f.f0();
        if (age < f.delay()) { // a faint plane and a bright line through it, and a line on the ground
            float k = Math.min(1, age / 3) * (0.8F + 0.2F * Mth.sin(age * 3.3F));
            plane(pose, buffers.getBuffer(PLANE), a, b, a.y, h, 0.12F * k, 0.11F * k, 0.14F * k);
            Vec3 up = new Vec3(0, 1.6, 0);
            BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, a.add(up), b.add(up), 0.12F, k, k, k);
            strip(pose, buffers.getBuffer(TRAIL), a, b, 0.2F, 0.8F * k, 0.75F * k, 0.9F * k);
            return;
        }
        float t = age - f.delay();
        float open = Mth.clamp(t / 3, 0, 1);
        float fade = Mth.clamp(1 - (t - 3) / 14, 0, 1);
        if (fade > 0) {
            plane(pose, buffers.getBuffer(PLANE), a, b, a.y + h * (1 - open) / 2, h * open, fade, 0.92F * fade, fade);
            Vec3 mid = new Vec3(0, h / 2, 0);
            BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, a.add(mid), b.add(mid), 0.35F * fade, fade, 0.85F * fade, fade);
        }
        if (t < 20) { // space stays torn along it for a second
            float k = 1 - t / 20;
            for (int i = 1; i <= 3; i++) ScreenFxClient.lens(a.lerp(b, i / 4.0).add(0, h / 2, 0), 0.35F, 0.7F * k);
        }
    }

    private static void gap(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Fx f, float age) {
        float t = Math.max(0, age - f.delay());
        float r = Math.max(0, f.f0() + f.f3() * t);
        float ang = f.f1() + f.f2() * t;
        Vec3 at = f.a().add(Mth.cos(ang) * r, 0, Mth.sin(ang) * r);
        float k = Math.min(1, age / 4) * Mth.clamp((f.life() - age) / 3, 0, 1);
        BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, at, at.add(0, 7, 0), 1.4F, k, 0.72F * k, 0.2F * k);
        BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, at, at.add(0, 7, 0), 0.4F, k, 0.95F * k, 0.7F * k);
        BeamFx.glow(pose, buffers, cam, at.add(0, 0.2, 0), 2.6F, 0.8F * k, 0.55F * k, 0.15F * k);
    }

    private static void flower(PoseStack pose, MultiBufferSource buffers, Fx f, float age) {
        float bright = 0.25F + 0.75F * (float) Math.pow(Math.min(1, age / f.life()), 2);
        if (age > f.life() - 6) bright *= 0.8F + 0.2F * Mth.sin(age * 2.5F);
        flat(pose, buffers.getBuffer(DECAL), f.b().add(0, 0.05, 0), 4.6F, age * 0.02F, bright, 0.7F * bright, 0.85F * bright);
        if (age < f.delay()) {
            float k = age / f.delay();
            Vec3 at = f.a().lerp(f.b(), k * k).add(0, Mth.sin(k * Mth.PI) * 2.5F, 0);
            BulletFx.billboard(pose, buffers.getBuffer(FLOWER), at, 1.0F, age * 0.3F, 1, 1, 1, 1);
        } else {
            float g = (age - f.delay()) / Math.max(1, f.life() - f.delay());
            float size = 1.0F + g * 1.4F + (g > 0.8F ? Mth.sin(age * 3) * 0.1F : 0);
            BulletFx.billboard(pose, buffers.getBuffer(FLOWER), f.b().add(0, size * 0.45F, 0), size, Mth.sin(age * 0.1F) * 0.2F, 1, 1, 1, 1);
        }
    }

    /** The collapsing star's radius at an age (ticks). */
    static float starRadius(float full, float age) {
        if (age < NamelessCreator.STAR_GROW_FROM) return 0;
        if (age < NamelessCreator.STAR_GROW_TO) {
            float k = (age - NamelessCreator.STAR_GROW_FROM) / (NamelessCreator.STAR_GROW_TO - NamelessCreator.STAR_GROW_FROM);
            return full * k * k * (3 - 2 * k);
        }
        if (age < NamelessCreator.STAR_COLLAPSED) {
            float k = (age - NamelessCreator.STAR_GROW_TO) / (NamelessCreator.STAR_COLLAPSED - NamelessCreator.STAR_GROW_TO);
            return Math.max(0.04F, full * (1 - k * k));
        }
        return age < NamelessCreator.STAR_SILENCE_TO ? 0.04F : 0;
    }

    private static void star(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Fx f, float age) {
        float r = starRadius(f.f0(), age);
        if (r <= 0) return;
        Vec3 at = f.a();
        if (r > 0.05F) {
            float grown = r / f.f0();
            BulletFx.billboard(pose, buffers.getBuffer(CORONA), at, r * 6.5F, age * 0.01F, 0.9F, 0.85F, 0.8F, 1);
            BulletFx.billboard(pose, buffers.getBuffer(PLASMA), at, r * 2, age * 0.02F, 1, 1, 1, 1);
            BulletFx.billboard(pose, buffers.getBuffer(PLASMA), at, r * 1.92F, -age * 0.031F + 1, 0.6F, 0.55F, 0.5F, 1);
            BeamFx.glow(pose, buffers, cam, at, r * 3.5F, 0.6F, 0.45F, 0.2F);
            ScreenFxClient.lens(at, r * 0.75F, 0.4F + grown * 0.9F);
        } else {
            BulletFx.billboard(pose, buffers.getBuffer(STAR), at, 0.8F, age * 0.2F, 1, 1, 1, 1);
        }
    }

    private static void mark(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Fx f, float age, float now) {
        Vec3 a = f.a();
        if (age < f.delay()) {
            float k = age / f.delay();
            float pulse = 0.6F + 0.4F * Mth.sin(age * (0.4F + k * 1.4F));
            float b = (0.35F + 0.65F * k) * pulse;
            flat(pose, buffers.getBuffer(MARK), a.add(0, 0.05, 0), f.f0() * 1.7F, 0, b, 0.3F * b, 0.2F * b);
            return;
        }
        float t = age - f.delay();
        float fall = Mth.clamp(t / 3, 0, 1);
        float fade = Mth.clamp(1 - (t - 3) / Math.max(1, f.life() - f.delay() - 3), 0, 1);
        double top = a.y + 40, bottom = top - 40 * fall;
        float scroll = now * 0.06F;
        cylinder(pose, buffers.getBuffer(COLUMN), a, f.f0() / 2, bottom, top, scroll, fade, 0.95F * fade, 0.8F * fade);
        cylinder(pose, buffers.getBuffer(COLUMN), a, f.f0() * 0.85F, bottom, top, -scroll * 0.7F, 0.3F * fade, 0.27F * fade, 0.18F * fade);
        flat(pose, buffers.getBuffer(MARK), a.add(0, 0.05, 0), f.f0() * 1.7F, 0, fade, 0.85F * fade, 0.5F * fade);
        if (fall >= 1) BeamFx.glow(pose, buffers, cam, a.add(0, 0.3, 0), f.f0() * 3, fade, 0.9F * fade, 0.6F * fade);
    }

    private static void shock(Fx f, float age) {
        float k = Mth.clamp(age / f.life(), 0, 1);
        float run = 1 - (1 - k) * (1 - k);
        ScreenFxClient.shock(f.a(), f.f0() * run, 1.2F + f.f0() * 0.06F, (1 - k) * 0.9F);
        if (f.f1() > 0 && k < 0.5F) ScreenFxClient.radial(f.a(), f.f1() * (1 - k * 2));
    }

    private static void crack(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Fx f, float age) {
        float k = (float) Math.sqrt(Mth.clamp(1 - age / f.life(), 0, 1)) * (0.8F + 0.2F * Mth.sin(age * 4.1F));
        BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, f.a(), f.b(), 1.2F * k, 0.6F * k, 0.5F * k, 0.4F * k);
        BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, f.a(), f.b(), 0.35F * k, k, 0.97F * k, 0.9F * k);
        BeamFx.glow(pose, buffers, cam, f.a().lerp(f.b(), 0.62), 4 * k, 0.8F * k, 0.75F * k, 0.6F * k);
    }

    // ------------------------------------------------------------------ the second phase

    /** Where a fist is: high over its circle, rising a little as it gathers, falling the last few ticks. */
    private static Vec3 fistAt(Fx f, float age) {
        float high = 9 + Math.min(age, f.delay()) * 0.04F;
        if (age > f.delay() - 4) high *= Math.max(0, (f.delay() - age) / 4);
        return f.b().add(0, Math.max(0.6, high), 0);
    }

    private static void fist(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Fx f, float age) {
        boolean gold = f.f0() < 0.5F;
        float r = gold ? 1 : 0.62F, g = gold ? 0.8F : 0.25F, b = gold ? 0.3F : 1;
        float k = Math.min(1, age / f.delay());
        float fade = Mth.clamp((f.life() - age) / 6, 0, 1);
        float pulse = 0.7F + 0.3F * Mth.sin(age * (0.4F + k));
        flat(pose, buffers.getBuffer(MARK), f.b().add(0, 0.05, 0), 5.2F, age * 0.05F, r * k * pulse * fade, g * k * pulse * fade, b * k * pulse * fade);
        Vec3 at = fistAt(f, age);
        if (hand == null) hand = new AurelionRenderer.Hand(Minecraft.getInstance().getEntityModels().bakeLayer(AurelionRenderer.HAND));
        pose.pushPose();
        pose.translate(at.x, at.y + 1.6, at.z);
        pose.mulPose(Axis.YP.rotationDegrees(age * 2));
        pose.mulPose(Axis.XP.rotationDegrees(90)); // the knuckles down
        pose.scale(3 * fade, -3 * fade, 3 * fade);
        hand.pose(Math.min(1, k * 1.6F), age);
        int tint = gold ? 0xFFFFD070 : 0xFFB070FF;
        hand.renderToBuffer(pose, buffers.getBuffer(gold ? GOLD_HAND : VIOLET_HAND), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint);
        pose.popPose();
        // round it, balls of light - or of ooze - swelling, drifting and merging
        for (int i = 0; i < 7; i++) {
            float a = age * (0.08F + i * 0.01F) + i * 0.9F;
            float d = 1.4F + 0.5F * Mth.sin(age * 0.13F + i * 1.7F);
            Vec3 p = at.add(Mth.cos(a) * d, 1.4 + Mth.sin(age * 0.11F + i) * 0.9, Mth.sin(a) * d);
            float s = (1.0F + 0.5F * Mth.sin(age * 0.21F + i * 2.1F)) * fade;
            if (gold) BeamFx.glow(pose, buffers, cam, p, s * 1.6F, 0.9F * fade, 0.7F * fade, 0.25F * fade);
            else BulletFx.billboard(pose, buffers.getBuffer(BLOB), p, s * 1.2F, 0, 0.16F, 0.04F, 0.24F, 0.85F * fade);
        }
        if (gold) BeamFx.glow(pose, buffers, cam, at.add(0, 1.4, 0), 6 * fade, 0.6F * fade, 0.45F * fade, 0.15F * fade);
    }

    private static void groundWave(PoseStack pose, MultiBufferSource buffers, Fx f, float age) {
        float r = NamelessCreator.waveRadius(f.f0(), f.life(), age);
        if (r < 0.2F) return;
        boolean gold = f.f1() < 0.5F;
        float fade = Mth.clamp((f.life() - age) / 5, 0, 1);
        float cr = (gold ? 1 : 0.65F) * fade, cg = (gold ? 0.78F : 0.3F) * fade, cb = (gold ? 0.3F : 1) * fade;
        int n = Math.max(24, (int) (r * 3));
        float y = (float) f.a().y;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float x0 = (float) f.a().x + Mth.cos(a0) * r, z0 = (float) f.a().z + Mth.sin(a0) * r;
            float x1 = (float) f.a().x + Mth.cos(a1) * r, z1 = (float) f.a().z + Mth.sin(a1) * r;
            float[][] q = {{x0, y, z0, 0, 1}, {x1, y, z1, 1, 1}, {x1, y + 0.7F, z1, 1, 0}, {x0, y + 0.7F, z0, 0, 0}};
            quad(pose, buffers.getBuffer(COLUMN), q, cr, cg, cb);
        }
    }

    private static void voidZone(PoseStack pose, MultiBufferSource buffers, Fx f, float age) {
        float k = Math.min(1, age / 6) * Mth.clamp((f.life() - age) / 10, 0, 1);
        float rad = f.f0() / 2;
        for (int i = 0; i < 14; i++) {
            float a = i * 2.4F + age * 0.01F, d = rad * (float) Math.sqrt((i + 0.5F) / 14);
            Vec3 p = f.a().add(Mth.cos(a) * d, 0.04 + i * 0.003, Mth.sin(a) * d);
            float s = (1.6F + 0.6F * Mth.sin(age * 0.15F + i * 1.3F)) * k;
            flatTinted(pose, buffers.getBuffer(BLOB), p, s * 1.4F, 0.05F, 0.01F, 0.08F, 0.9F * k);
        }
        flatTinted(pose, buffers.getBuffer(BLOB), f.a().add(0, 0.03, 0), f.f0() * 1.3F * k, 0.25F, 0.05F, 0.4F, 0.5F * k);
    }

    /** The sword's direction now (from its hilt to its tip). */
    private static Vec3 swordDir(Fx f, float age, boolean down) {
        float yaw, pitch;
        if (down) {
            yaw = f.f1();
            pitch = age < f.delay() ? Mth.HALF_PI : Mth.HALF_PI * Math.max(0, 1 - (age - f.delay()) / NamelessCreator.SWORD_V_FALL);
        } else {
            yaw = NamelessCreator.swordYaw(f.f1(), f.f2(), age);
            pitch = NamelessCreator.swordPitch(age);
        }
        float cp = Mth.cos(pitch);
        return new Vec3(Mth.cos(yaw) * cp, Mth.sin(pitch), Mth.sin(yaw) * cp);
    }

    private static void sword(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Fx f, float age, boolean down) {
        int n = 14;
        float build = down ? 0 : NamelessCreator.SWORD_BUILD * 0.8F;
        float fade = Mth.clamp((f.life() - age) / 6, 0, 1);
        Vec3 hilt = f.a();
        Vec3 dir = swordDir(f, age, down);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(Mth.cos(f.f1() + Mth.HALF_PI), 0, Mth.sin(f.f1() + Mth.HALF_PI));
        side = side.normalize();
        Vec3[] stars = new Vec3[n];
        int shown = 0;
        for (int i = 0; i < n; i++) {
            if (age < i * build / n) break;
            float along = f.f0() * i / (n - 1);
            float off = (FireFx.hash(i * 7 + 3) - 0.5F) * 0.9F;
            stars[i] = hilt.add(dir.scale(along)).add(side.scale(off));
            shown = i + 1;
        }
        if (down && age < f.delay()) { // the line it will come down on
            float k = Math.min(1, age / 4) * (0.7F + 0.3F * Mth.sin(age * 1.3F));
            Vec3 foot = hilt.subtract(0, NamelessCreator.SWORD_HILT, 0);
            Vec3 flatDir = new Vec3(Mth.cos(f.f1()), 0, Mth.sin(f.f1()));
            strip(pose, buffers.getBuffer(TRAIL), foot, foot.add(flatDir.scale(f.f0())), 2.4F, k, 0.75F * k, 0.9F * k);
        }
        for (int i = 0; i < shown; i++) {
            float tw = 0.8F + 0.2F * Mth.sin(age * 0.9F + i);
            BulletFx.billboard(pose, buffers.getBuffer(STAR), stars[i], (i == 0 ? 2.2F : 1.4F) * fade, age * 0.05F + i, tw * fade, tw * fade,
                    tw * 0.9F * fade, 1);
            if (i > 0) BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, stars[i - 1], stars[i], 0.16F * fade, 0.8F * fade, 0.85F * fade, fade);
        }
        if (shown > 3) { // the crossguard
            Vec3 g = hilt.add(dir.scale(f.f0() * 0.08));
            Vec3 l = g.add(side.scale(2.2)), r = g.subtract(side.scale(2.2));
            BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, l, r, 0.16F * fade, 0.8F * fade, 0.85F * fade, fade);
            BulletFx.billboard(pose, buffers.getBuffer(STAR), l, 1.2F * fade, 0, fade, fade, fade, 1);
            BulletFx.billboard(pose, buffers.getBuffer(STAR), r, 1.2F * fade, 0, fade, fade, fade, 1);
        }
        if (!down && age < NamelessCreator.SWORD_BUILD) ScreenFxClient.radial(hilt.add(0, 6, 0), 0.35F * age / NamelessCreator.SWORD_BUILD);
        if (shown == n) { // pulses of light running from the hilt to the tip
            float p = (age * 0.08F) % 1;
            BeamFx.glow(pose, buffers, cam, hilt.add(dir.scale(f.f0() * p)), 3.5F * fade, 0.8F * fade, 0.85F * fade, fade);
        }
        // swung: a great sweep of light behind the blade
        float swingFrom = down ? f.delay() : NamelessCreator.SWORD_BUILD + NamelessCreator.SWORD_TIP;
        float swingLen = down ? NamelessCreator.SWORD_V_FALL : NamelessCreator.SWORD_SWING;
        if (age > swingFrom && age < swingFrom + swingLen + 8) {
            float tail = Mth.clamp((swingFrom + swingLen + 8 - age) / 8, 0, 1);
            Vec3 d1 = dir;
            for (int j = 1; j <= 8; j++) {
                float back = age - j * (down ? 0.6F : 1.2F);
                if (back < swingFrom - 1) break;
                Vec3 d2 = swordDir(f, back, down);
                float k = (1 - j / 9F) * fade * tail;
                plane3(pose, buffers.getBuffer(PLANE), hilt, hilt.add(d1.scale(f.f0())), hilt.add(d2.scale(f.f0())), 0.7F * k, 0.72F * k, k);
                d1 = d2;
            }
        }
    }

    private static void zones(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Fx f, float age) {
        float k = Math.min(1, age / 10) * Mth.clamp((f.life() - age) / 10, 0, 1);
        int swaps = (int) (age / Math.max(1, f.delay()));
        float cell = f.f0(), rot = f.f1();
        boolean chequer = f.f2() > 0.5F;
        Vec3 u = new Vec3(Mth.cos(rot), 0, Mth.sin(rot)), v = new Vec3(-u.z, 0, u.x);
        int n = (int) Math.ceil(f.f3() / cell) + 1;
        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                Vec3 mid = f.a().add(u.scale((i + 0.5) * cell)).add(v.scale((j + 0.5) * cell));
                if (mid.distanceTo(f.a()) > f.f3() + cell * 0.3) continue;
                boolean light = Math.floorMod(i + (chequer ? j : 0) + swaps, 2) == 0;
                Vec3 at = mid.add(0, 0.04, 0);
                if (light) flatSquare(pose, buffers.getBuffer(ZONE), at, u, v, cell, 1, 0.78F, 0.3F, 0.45F * k);
                else flatSquare(pose, buffers.getBuffer(ZONE), at, u, v, cell, 0.1F, 0.02F, 0.18F, 0.6F * k);
                int seed = i * 31 + j * 17;
                for (int m = 0; m < 2; m++) {
                    Vec3 p = mid.add(u.scale((FireFx.hash(seed + m) - 0.5) * cell * 0.7)).add(v.scale((FireFx.hash(seed + m + 9) - 0.5) * cell * 0.7));
                    if (light) { // motes of light drifting up
                        float h = (age * 0.03F + FireFx.hash(seed + m + 5) * 3) % 3;
                        BeamFx.glow(pose, buffers, cam, p.add(0, 0.3 + h, 0), 0.7F * k, 0.9F * k, 0.7F * k, 0.25F * k);
                    } else { // blobs of ooze
                        float s = (1.4F + 0.5F * Mth.sin(age * 0.12F + seed + m)) * k;
                        flatTinted(pose, buffers.getBuffer(BLOB), p.add(0, 0.06, 0), s, 0.04F, 0.0F, 0.07F, 0.85F * k);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the third phase

    /** A cut of light: a thin flickering line, then a very bright narrow blade of light with a streak, fading. */
    private static void lightSlash(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Fx f, float age) {
        if (age < f.delay()) {
            float k = (0.5F + 0.5F * Mth.sin(age * 4.1F)) * Math.min(1, age / 2);
            BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, f.a(), f.b(), 0.07F, k, 0.5F * k, 0.5F * k);
            return;
        }
        float t = age - f.delay();
        float k = Mth.clamp(1 - t / (f.life() - f.delay()), 0, 1);
        BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, f.a(), f.b(), 2.2F * k, 0.5F * k, 0.5F * k, 0.6F * k);
        BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, f.a(), f.b(), 0.5F * (0.4F + k), k, k, k);
        BeamFx.glow(pose, buffers, cam, f.a().lerp(f.b(), 0.5), 5 * k, 0.6F * k, 0.6F * k, 0.7F * k);
    }

    /** Chronos' hand: a great beam turning round its source, faster and faster - a tube of scrolling light, a flare at its root. */
    private static void chronos(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Fx f, float age, float now) {
        float grow = Mth.clamp((age - f.delay() + 6) / 6, 0, 1);
        float fade = Mth.clamp((f.life() - age) / 6, 0, 1);
        float k = grow * fade;
        if (k <= 0) return;
        float ang = NamelessCreator.beamAngle(f.f1(), f.f2(), f.f3(), age - f.delay());
        Vec3 dir = new Vec3(Mth.cos(ang), 0, Mth.sin(ang));
        Vec3 from = f.a(), to = from.add(dir.scale(f.f0() * grow));
        tube(pose, buffers.getBuffer(COLUMN), from, to, 0.45F, now * 0.09F, k, 0.92F * k, 0.75F * k);
        tube(pose, buffers.getBuffer(COLUMN), from, to, 0.8F, -now * 0.05F, 0.3F * k, 0.25F * k, 0.18F * k);
        BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, from, to, 0.25F, k, k, 0.95F * k);
        BulletFx.billboard(pose, buffers.getBuffer(CORONA), from, 5 * k, now * 0.03F, k, 0.9F * k, 0.7F * k, 1);
        BulletFx.billboard(pose, buffers.getBuffer(STAR), from, 3 * k, -now * 0.05F, k, k, k, 1);
    }

    /** A cage: the white outline of a box, its edges loading in one by one, flickering; shut, its faces flash white. */
    private static void cage(PoseStack pose, MultiBufferSource buffers, Fx f, float age) {
        Vec3 a = f.a(), b = f.b();
        float[][] c = {{(float) a.x, (float) a.y, (float) a.z}, {(float) b.x, (float) a.y, (float) a.z}, {(float) b.x, (float) a.y, (float) b.z},
                {(float) a.x, (float) a.y, (float) b.z}, {(float) a.x, (float) b.y, (float) a.z}, {(float) b.x, (float) b.y, (float) a.z},
                {(float) b.x, (float) b.y, (float) b.z}, {(float) a.x, (float) b.y, (float) b.z}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        boolean shut = age >= f.delay();
        int shown = shut ? 12 : Math.min(12, (int) (age / 1.2F) + 1);
        float fade = Mth.clamp((f.life() - age) / 6, 0, 1);
        float pulse = shut ? 1 : 0.55F + 0.45F * Mth.sin(age * 0.9F);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < shown; i++) {
            if (!shut && FireFx.hash((int) age * 13 + i) < 0.12F) continue; // flickering
            float[] p = c[edges[i][0]], q = c[edges[i][1]];
            float nx = q[0] - p[0], ny = q[1] - p[1], nz = q[2] - p[2];
            float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            int al = (int) (255 * pulse * fade);
            lines.addVertex(m, p[0], p[1], p[2]).setColor(255, 255, 255, al).setNormal(pose.last(), nx / len, ny / len, nz / len);
            lines.addVertex(m, q[0], q[1], q[2]).setColor(255, 255, 255, al).setNormal(pose.last(), nx / len, ny / len, nz / len);
        }
        if (shut) { // its faces flash white as it snaps shut
            float k = Mth.clamp(1 - (age - f.delay()) / 8, 0, 1) * 0.8F;
            if (k > 0) {
                int[][] faces = {{0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 0, 4, 7}};
                for (int[] fc : faces) {
                    float[][] q = new float[4][];
                    float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
                    for (int i = 0; i < 4; i++) q[i] = new float[] {c[fc[i]][0], c[fc[i]][1], c[fc[i]][2], uv[i][0], uv[i][1]};
                    quadAlpha(pose, buffers.getBuffer(ZONE), q, 1, 1, 1, k);
                }
            }
        }
    }

    /** A chunk of the floor: flickering with the loading texture, then a black void, then flickering back. */
    private static void chunk(PoseStack pose, MultiBufferSource buffers, Fx f, float age) {
        float x0 = (float) f.a().x, z0 = (float) f.a().z, y = (float) f.a().y + 0.02F;
        boolean gone = age >= f.delay() && age < f.life() - 6;
        if (gone) {
            float[][] q = {{x0, y, z0, 0, 0}, {x0 + 16, y, z0, 1, 0}, {x0 + 16, y, z0 + 16, 1, 1}, {x0, y, z0 + 16, 0, 1}};
            quadAlpha(pose, buffers.getBuffer(VOID), q, 0, 0, 0, 1);
            return;
        }
        // flickering: block by block, now the loading texture, now the world
        VertexConsumer vc = buffers.getBuffer(LOADING);
        int tick = (int) age;
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                if (FireFx.hash(bx * 31 + bz * 17 + tick * 7) < 0.55F) continue;
                float[][] q = {{x0 + bx, y, z0 + bz, 0, 0}, {x0 + bx + 1, y, z0 + bz, 1, 0}, {x0 + bx + 1, y, z0 + bz + 1, 1, 1},
                        {x0 + bx, y, z0 + bz + 1, 0, 1}};
                quadAlpha(pose, vc, q, 0.45F, 0.45F, 0.45F, 1);
            }
        }
    }

    /** The chunk borders over the arena, as F3+G shows them: red corners, yellow lines every two blocks on the edges. */
    private static void chunkGrid(PoseStack pose, MultiBufferSource buffers, Fx f, float age) {
        float k = Math.min(1, age / 6) * Mth.clamp((f.life() - age) / 6, 0, 1);
        if (k <= 0) return;
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Matrix4f m = pose.last().pose();
        int minX = Mth.floor((f.a().x - f.f0()) / 16), maxX = Mth.floor((f.a().x + f.f0()) / 16);
        int minZ = Mth.floor((f.a().z - f.f0()) / 16), maxZ = Mth.floor((f.a().z + f.f0()) / 16);
        float y0 = (float) f.a().y - 2, y1 = (float) f.a().y + 14;
        int al = (int) (255 * k);
        for (int cx = minX; cx <= maxX + 1; cx++) {
            for (int cz = minZ; cz <= maxZ + 1; cz++) {
                float x = cx * 16, z = cz * 16;
                line(lines, pose, m, x, y0 - 6, z, x, y1 + 8, z, 255, 40, 40, al); // the corner
                if (cz <= maxZ) for (int d = 2; d < 16; d += 2) line(lines, pose, m, x, y0, z + d, x, y1, z + d, 255, 230, 40, al / 2);
                if (cx <= maxX) for (int d = 2; d < 16; d += 2) line(lines, pose, m, x + d, y0, z, x + d, y1, z, 255, 230, 40, al / 2);
                for (float yy = y0; yy <= y1; yy += 2) {
                    if (cz <= maxZ) line(lines, pose, m, x, yy, z, x, yy, z + 16, 255, 230, 40, al / 2);
                    if (cx <= maxX) line(lines, pose, m, x, yy, z, x + 16, yy, z, 255, 230, 40, al / 2);
                }
            }
        }
    }

    private static void line(VertexConsumer vc, PoseStack pose, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int r, int g,
            int b, int a) {
        float nx = x1 - x0, ny = y1 - y0, nz = z1 - z0;
        float len = Math.max(1.0E-4F, Mth.sqrt(nx * nx + ny * ny + nz * nz));
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a).setNormal(pose.last(), nx / len, ny / len, nz / len);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a).setNormal(pose.last(), nx / len, ny / len, nz / len);
    }

    /** An open tube round the line from-to, the texture tiled round it and scrolled along it. */
    private static void tube(PoseStack pose, VertexConsumer vc, Vec3 from, Vec3 to, float radius, float scroll, float r, float g, float b) {
        Vec3 axis = to.subtract(from);
        double len = axis.length();
        if (len < 1.0E-3) return;
        Vec3 d = axis.scale(1 / len);
        Vec3 u = d.cross(Math.abs(d.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize(), v = d.cross(u);
        int n = 12;
        float v0 = scroll, v1 = scroll + (float) len / 8;
        for (int i = 0; i < n; i++) {
            double a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            Vec3 o0 = u.scale(Math.cos(a0) * radius).add(v.scale(Math.sin(a0) * radius)), o1 = u.scale(Math.cos(a1) * radius).add(v.scale(Math.sin(a1) * radius));
            Vec3 p0 = from.add(o0), p1 = from.add(o1), p2 = to.add(o1), p3 = to.add(o0);
            float u0 = i * 2F / n, u1 = (i + 1) * 2F / n;
            float[][] q = {{(float) p0.x, (float) p0.y, (float) p0.z, u0, v0}, {(float) p1.x, (float) p1.y, (float) p1.z, u1, v0},
                    {(float) p2.x, (float) p2.y, (float) p2.z, u1, v1}, {(float) p3.x, (float) p3.y, (float) p3.z, u0, v1}};
            quad(pose, vc, q, r, g, b);
        }
    }

    /** A frame of something from the game itself, over the whole screen: the menu's panorama, the "building terrain"
     * screen, or the old dirt background. */
    private static void gameFlash(GuiGraphics g, int which) {
        int w = g.guiWidth(), h = g.guiHeight();
        if (which == 0) {
            g.blit(PANORAMA, 0, 0, w, h, 0, 0, 1024, 1024, 1024, 1024);
            return;
        }
        g.setColor(0.25F, 0.25F, 0.25F, 1);
        for (int x = 0; x < w; x += 32) for (int y = 0; y < h; y += 32) g.blit(DIRT, x, y, 0, 0, 32, 32, 32, 32);
        g.setColor(1, 1, 1, 1);
        if (which == 1) {
            Component text = Component.translatable("menu.generatingTerrain");
            Minecraft mc = Minecraft.getInstance();
            g.drawString(mc.font, text, (w - mc.font.width(text)) / 2, h / 2 - 20, 0xFFFFFF);
        }
    }

    // ------------------------------------------------------------------ the true form

    /** A cut of pure white: a thin line, then the floor under it flat white until it is gone. */
    private static void erase(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Fx f, float age) {
        Vec3 a = f.a().add(0, 0.08, 0), b = f.b().add(0, 0.08, 0);
        if (age < f.delay()) {
            float k = 0.6F + 0.4F * Mth.sin(age * 3.7F);
            BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, a, b, 0.09F, k, k, k);
            return;
        }
        strip(pose, buffers.getBuffer(VOID_WHITE), f.a().add(0, -0.04, 0), f.b().add(0, -0.04, 0), f.f0(), 1, 1, 1);
        float k = Mth.clamp(1 - (age - f.delay()) / 6, 0, 1);
        if (k > 0) BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, a.add(0, 1, 0), b.add(0, 1, 0), 2.5F * k, k, k, k);
    }

    /** The floor between two radii white, before it is gone (and before that, a thin white ring where it will end). */
    private static void eraseRing(PoseStack pose, MultiBufferSource buffers, Fx f, float age) {
        int n = 64;
        float y = (float) f.a().y + 0.03F;
        boolean white = age >= f.delay();
        float r0 = white ? f.f0() : f.f0() - 0.15F, r1 = white ? f.f1() : f.f0() + 0.15F;
        float k = white ? 1 : 0.6F + 0.4F * Mth.sin(age * 3.7F);
        VertexConsumer vc = buffers.getBuffer(white ? VOID_WHITE : TRAIL);
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float x = (float) f.a().x, z = (float) f.a().z;
            float[][] q = {{x + Mth.cos(a0) * r0, y, z + Mth.sin(a0) * r0, 0, 0}, {x + Mth.cos(a1) * r0, y, z + Mth.sin(a1) * r0, 1, 0},
                    {x + Mth.cos(a1) * r1, y, z + Mth.sin(a1) * r1, 1, 1}, {x + Mth.cos(a0) * r1, y, z + Mth.sin(a0) * r1, 0, 1}};
            quad(pose, vc, q, k, k, k);
        }
    }

    /** The floor a chequer of the missing texture, moving on a square every period; it flickers just before. */
    private static void checker(PoseStack pose, MultiBufferSource buffers, Fx f, float age) {
        float in = Math.min(1, age / 6), out = Mth.clamp((f.life() - age) / 6, 0, 1);
        if (in * out <= 0) return;
        int shift = (int) (age / Math.max(1, f.delay()));
        boolean flicker = f.delay() - age % f.delay() < 5 && ((int) age) % 2 == 0;
        float cell = f.f0();
        int n = (int) Math.ceil(f.f1() / cell);
        VertexConsumer vc = buffers.getBuffer(VOID_WHITE);
        float y = (float) f.a().y + 0.03F;
        for (int i = -n; i < n; i++) {
            for (int j = -n; j < n; j++) {
                float x0 = (float) f.a().x + i * cell, z0 = (float) f.a().z + j * cell;
                if (Math.hypot(x0 + cell / 2 - f.a().x, z0 + cell / 2 - f.a().z) > f.f1()) continue;
                boolean magenta = Math.floorMod(i + j + shift, 2) == 0;
                float b = (flicker ? 0.55F : 1) * in * out;
                float[][] q = {{x0, y, z0, 0, 0}, {x0 + cell, y, z0, 1, 0}, {x0 + cell, y, z0 + cell, 1, 1}, {x0, y, z0 + cell, 0, 1}};
                if (magenta) quad(pose, vc, q, 0.97F * b, 0, 0.97F * b);
                else quad(pose, vc, q, 0.02F, 0.02F, 0.02F);
            }
        }
    }

    private static List<String> splashes;

    /** The menu's splash texts (from the game's own texts/splashes.txt). */
    private static List<String> splashes() {
        if (splashes == null) {
            splashes = new ArrayList<>();
            try (var in = Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.withDefaultNamespace("texts/splashes.txt"))
                    .orElseThrow().open()) {
                for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                    if (!line.isBlank() && line.length() < 40) splashes.add(line.trim());
                }
            } catch (Exception e) {
                splashes.add("Missingno!");
            }
        }
        return splashes;
    }

    /** The great beam: gathering in a point before its face, then a huge turning beam to the edge of the arena. */
    private static void giga(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Fx f, float age, float now) {
        Vec3 src = f.a();
        if (age < f.delay()) { // gathering
            float k = age / f.delay();
            BulletFx.billboard(pose, buffers.getBuffer(CORONA), src, 1 + k * 7, now * 0.05F, k, k, k, 1);
            BulletFx.billboard(pose, buffers.getBuffer(STAR), src, 1 + k * 4, -now * 0.1F, 1, 1, 1, 1);
            ScreenFxClient.radial(src, 0.45F * k);
            return;
        }
        float t = age - f.delay();
        float narrow = Mth.clamp((f.life() - age) / 10, 0, 1);
        float k = Math.min(1, t / 3) * narrow;
        float yaw = f.f1() + f.f2() * Mth.clamp(t / NamelessCreator.GIGA_BEAM, 0, 1);
        Vec3 end = new Vec3(src.x + Mth.cos(yaw) * f.f0(), f.b().y, src.z + Mth.sin(yaw) * f.f0());
        tube(pose, buffers.getBuffer(COLUMN), src, end, 1.2F * k, now * 0.15F, k, k, k);
        tube(pose, buffers.getBuffer(COLUMN), src, end, 2.4F * k, -now * 0.08F, 0.35F * k, 0.35F * k, 0.4F * k);
        BeamFx.ribbon(pose, buffers.getBuffer(TRAIL), cam, src, end, 0.9F * k, k, k, k);
        BulletFx.billboard(pose, buffers.getBuffer(CORONA), src, 9 * k, now * 0.04F, k, k, k, 1);
        BulletFx.billboard(pose, buffers.getBuffer(PLASMA), end.add(0, 0.5, 0), 6 * k, now * 0.1F, k, k, k, 1);
        BeamFx.glow(pose, buffers, cam, end.add(0, 0.5, 0), 12 * k, k, k, k);
        // the menu's splash texts drifting out along it from its root
        Minecraft mc = Minecraft.getInstance();
        List<String> lines = splashes();
        Vec3 dir = end.subtract(src).normalize();
        for (int i = 0; i < 5; i++) {
            float along = (t * 0.12F + i * 2.6F) % 13;
            Vec3 at = src.add(dir.scale(along + 1)).add(0, 1.6 + Mth.sin(now * 0.2F + i) * 0.4, 0);
            String text = lines.get(Math.floorMod((int) (f.start() / 7) + i * 37, lines.size()));
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
            pose.mulPose(Axis.ZP.rotationDegrees(-20));
            float s = 0.045F * (1 + 0.08F * Mth.sin(now * 0.6F + i));
            pose.scale(-s, -s, s);
            int alpha = (int) (255 * k * Mth.clamp(1 - along / 13, 0, 1));
            if (alpha > 8) {
                mc.font.drawInBatch(text, -mc.font.width(text) / 2F, 0, alpha << 24 | 0xFFFF55, true, pose.last().pose(), buffers,
                        net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            }
            pose.popPose();
        }
    }

    /** The world being made again: the height made so far (NaN once it is all made). */
    static double creationHeight(Fx f, float age) {
        int layers = NamelessCreator.CREATION_LAYERS;
        float from = NamelessCreator.CREATION_START, every = NamelessCreator.CREATION_EVERY;
        if (age >= from + layers * every + 10) return Double.NaN;
        if (age < from) return f.f0() - 1000;
        int k = (int) ((age - from) / every);
        float within = Mth.clamp((age - from - k * every) / 10, 0, 1);
        return f.f0() + (f.f1() - f.f0()) * (Math.min(k, layers - 1) + within) / layers;
    }

    /** The true form's pass: how long it was looked at, the world drained of colour and white round it, its colour coming
     * back from where it fell, the world being made again. */
    private static void creatorView(ClientLevel level, float now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        NamelessCreator god = null;
        for (NamelessCreator c : level.getEntitiesOfClass(NamelessCreator.class, mc.player.getBoundingBox().inflate(96))) {
            if (c.phase() >= 4) god = c;
        }
        float exposure = Mth.clamp((gaze - 0.2F) / 0.6F, 0, 1);
        double reveal = Double.NaN;
        for (Fx f : FX) if (f.kind() == CreatorFxPayload.CREATION) reveal = creationHeight(f, now - f.start());
        if (god == null && exposure <= 0 && Double.isNaN(reveal)) return;
        Vec3 at = god != null ? god.position().add(0, 1, 0) : mc.player.position();
        float bleach = 0, white = 0, restore = -1;
        if (god != null) {
            float hf = god.getHealth() / god.getMaxHealth();
            bleach = 0.3F + 0.55F * Mth.clamp((0.1F - hf) / 0.08F, 0, 1);
            white = 5;
            if (god.isDeadOrDying()) restore = god.dying * 0.5F;
        }
        ScreenFxClient.creatorView(new ScreenFxClient.CreatorView(exposure, bleach, at, white, at, restore, reveal));
    }

    /** The missing texture over a part of the HUD: magenta and black squares. */
    private static void missing(GuiGraphics g, int x, int y, int w, int h) {
        int s = 4;
        for (int i = 0; i * s < w; i++) {
            for (int j = 0; j * s < h; j++) {
                g.fill(x + i * s, y + j * s, Math.min(x + w, x + i * s + s), Math.min(y + h, y + j * s + s), (i + j) % 2 == 0 ? 0xFFF800F8 : 0xFF000000);
            }
        }
    }

    private static final ResourceLocation[] HUD = {VanillaGuiLayers.HOTBAR, VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.EXPERIENCE_BAR, VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.CROSSHAIR,
            VanillaGuiLayers.SELECTED_ITEM_NAME, VanillaGuiLayers.EXPERIENCE_LEVEL};

    /** Where each of those parts of the HUD is: x and y from the middle of the bottom (the crosshair: of the screen), w, h. */
    private static final int[][] HUD_AT = {{-91, -22, 182, 22}, {-91, -39, 81, 9}, {10, -39, 81, 9}, {-91, -49, 81, 9}, {-91, -29, 182, 5},
            {10, -49, 81, 9}, {-7, 0, 15, 15}, {-50, -59, 100, 9}, {-10, -35, 20, 8}};

    /** Looked at too long, the HUD turns to the missing texture, part after part. */
    private static boolean hudMissing(RenderGuiLayerEvent.Pre event) {
        int n = (int) Mth.clamp((gaze - 0.6F) / 0.3F * HUD.length, 0, HUD.length);
        for (int i = 0; i < n; i++) {
            if (!event.getName().equals(HUD[i])) continue;
            GuiGraphics g = event.getGuiGraphics();
            int[] r = HUD_AT[i];
            int x = g.guiWidth() / 2 + r[0], y = i == 6 ? g.guiHeight() / 2 - 7 : g.guiHeight() + r[1];
            missing(g, x, y, r[2], r[3]);
            return true;
        }
        return false;
    }

    /** The act of creation, on the screen: the face unfolding like a flower of chequered squares; one block before you. */
    private static void renderCreation(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        float now = mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false);
        for (Fx f : FX) {
            if (f.kind() != CreatorFxPayload.CREATION) continue;
            float age = now - f.start();
            int w = g.guiWidth(), h = g.guiHeight();
            if (age < NamelessCreator.CREATION_START + 20) { // the flower
                float open = Mth.clamp(age / 30, 0, 1);
                float fade = Mth.clamp((NamelessCreator.CREATION_START + 20 - age) / 15, 0, 1);
                Vector4f p = ScreenFxClient.onScreen(f.b());
                int cx = p == null ? w / 2 : (int) ((p.x * 0.5F + 0.5F) * w), cy = p == null ? h / 3 : (int) ((0.5F - p.y * 0.5F) * h);
                int s = Math.max(2, (int) (10 * open));
                int a = (int) (255 * fade) << 24;
                for (int petal = 0; petal < 8; petal++) {
                    float ang = petal * Mth.TWO_PI / 8 + open * 0.6F;
                    for (int j = 0; j < 4; j++) {
                        float d = (j + 1) * s * 1.6F * open;
                        int x = cx + (int) (Mth.cos(ang) * d) - s / 2, y = cy + (int) (Mth.sin(ang) * d) - s / 2;
                        g.fill(x, y, x + s, y + s, ((petal + j) % 2 == 0 ? 0xF800F8 : 0x000000) | a);
                    }
                }
                g.fill(cx - s, cy - s, cx, cy, 0xF800F8 | a);
                g.fill(cx, cy, cx + s, cy + s, 0xF800F8 | a);
                g.fill(cx, cy - s, cx + s, cy, a);
                g.fill(cx - s, cy, cx, cy + s, a);
            }
            if (age > 10 && age < 140) { // one block before you, in the dark
                float k = Mth.clamp((age - 10) / 10, 0, 1) * Mth.clamp((140 - age) / 30, 0, 1);
                g.pose().pushPose();
                g.pose().translate(w / 2F, h / 2F + 30, 0);
                float sc = 3 + k;
                g.pose().scale(sc, sc, 1);
                g.renderItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GRASS_BLOCK), -8, -8);
                g.pose().popPose();
            }
            return;
        }
    }

    // ------------------------------------------------------------------ the hunger bar crushed (on the HUD)

    /** The vanilla bar is hidden while ours is crushed. */
    @SubscribeEvent
    public static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (gaze > 0.6F && hudMissing(event)) {
            event.setCanceled(true);
            return;
        }
        if (!event.getName().equals(VanillaGuiLayers.FOOD_LEVEL)) return;
        Fx crush = crushing();
        if (crush != null && crushAge(crush) < 40) event.setCanceled(true);
    }

    @javax.annotation.Nullable
    private static Fx crushing() {
        for (Fx f : FX) if (f.kind() == CreatorFxPayload.CRUSH) return f;
        return null;
    }

    private static float crushAge(Fx f) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() - f.start() + mc.getTimer().getGameTimeDeltaPartialTick(false);
    }

    @EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {}

        @SubscribeEvent
        public static void onLayers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "creator_crush"), CreatorFx::renderCrush);
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "creator_creation"), CreatorFx::renderCreation);
        }
    }

    /**
     * Two hands (gold from the left, violet from the right) come in over the HUD (0-18), take the hunger bar between
     * them (18-24) and crush it together (24-32); it breaks into pieces that fall away, and the hands go (32-60).
     */
    private static void renderCrush(GuiGraphics g, DeltaTracker delta) {
        Fx f = crushing();
        Minecraft mc = Minecraft.getInstance();
        if (f == null || mc.player == null || mc.options.hideGui) return;
        float age = crushAge(f);
        int w = g.guiWidth(), h = g.guiHeight();
        int right = w / 2 + 91, y = h - 39;
        int mid = right - 40;
        float squeeze = Mth.clamp((age - 24) / 8, 0, 1);
        float half = 40 * (1 - 0.8F * squeeze);
        int food = mc.player.getFoodData().getFoodLevel();
        if (age < 32) { // the bar, squeezed between the hands, trembling
            float shake = age > 18 ? (age - 18) * 0.15F : 0;
            for (int i = 0; i < 10; i++) {
                float x = mid + half * ((i - 4.5F) / 5F) - 4.5F + Mth.sin(age * 3 + i) * shake;
                float yy = y + Mth.cos(age * 2.7F + i * 1.3F) * shake * (1 + squeeze * 2);
                int n = 19 - i * 2;
                ResourceLocation sprite = ResourceLocation.withDefaultNamespace(food > n ? "hud/food_full" : food == n ? "hud/food_half" : "hud/food_empty");
                g.pose().pushPose();
                g.pose().translate(x + 4.5F, yy + 4.5F, 0);
                g.pose().scale(1 - squeeze * 0.6F, 1 + squeeze * 0.4F, 1);
                g.blitSprite(sprite, -5, -5, 9, 9);
                g.pose().popPose();
            }
        } else if (age < 60) { // the pieces falling away
            RandomSource rnd = RandomSource.create(f.start());
            float t = age - 32;
            for (int i = 0; i < 40; i++) {
                float vx = (rnd.nextFloat() - 0.5F) * 5, vy = -rnd.nextFloat() * 4 - 1;
                float x = mid + vx * t, yy = y + 4 + vy * t + 0.35F * t * t;
                int c = rnd.nextBoolean() ? 0x9E5B2E : rnd.nextBoolean() ? 0xC8452A : 0x5A3418;
                int a = (int) (255 * Mth.clamp(1 - t / 28, 0, 1)) << 24;
                int s = 1 + rnd.nextInt(2);
                g.fill((int) x, (int) yy, (int) x + s, (int) yy + s, c | a);
            }
        }
        // the hands: coming in, closing, crushing, going
        float in = Mth.clamp(age / 18, 0, 1), go = Mth.clamp((age - 44) / 16, 0, 1);
        float reach = (1 - in) * (1 - in) * 140 + go * go * 140;
        boolean fist = age >= 18 && age < 44;
        int size = 48;
        for (int side = 0; side < 2; side++) {
            float edge = side == 0 ? mid - half - size + 6 - reach : mid + half + size - 6 + reach;
            g.pose().pushPose();
            g.pose().translate(edge, y - size / 2F + 4, 0);
            if (side == 1) g.pose().scale(-1, 1, 1);
            if (side == 0) g.setColor(1, 0.82F, 0.4F, 1);
            else g.setColor(0.62F, 0.35F, 1, 1);
            g.blit(CRUSH_HANDS, 0, 0, size, size, fist ? 32 : 0, 0, 32, 32, 64, 32);
            g.setColor(1, 1, 1, 1);
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ on the screen

    /** The silence after the star collapses: all black, but for its white point (from ScreenFxClient's layer). */
    static void renderScreen(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (FX.isEmpty() || mc.level == null || mc.player == null) return;
        float now = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        for (Fx f : FX) {
            float age = now - f.start();
            if (f.kind() == CreatorFxPayload.GAMEFLASH && age >= 0 && age < f.life()) {
                gameFlash(g, (int) f.f0());
                return;
            }
            if (f.kind() == CreatorFxPayload.CHRONOS && age >= f.delay() && age < f.life()) { // the daylight flickering
                float k = 0.5F + 0.5F * Mth.sin(age * 0.9F);
                g.fill(0, 0, g.guiWidth(), g.guiHeight(), (int) (k * 0.35F * 255) << 24);
            }
        }
        for (Fx f : FX) {
            if (f.kind() != CreatorFxPayload.STAR) continue;
            float age = now - f.start();
            if (age < NamelessCreator.STAR_COLLAPSED || age >= NamelessCreator.STAR_SILENCE_TO || mc.player.distanceToSqr(f.a()) > 80 * 80) continue;
            int w = g.guiWidth(), h = g.guiHeight();
            g.fill(0, 0, w, h, 0xFF000000);
            Vector4f p = ScreenFxClient.onScreen(f.a());
            if (p == null || Math.abs(p.x) > 1 || Math.abs(p.y) > 1) continue;
            int x = (int) ((p.x * 0.5F + 0.5F) * w), y = (int) ((0.5F - p.y * 0.5F) * h);
            float pulse = 0.8F + 0.2F * Mth.sin(age * 1.7F);
            int halo = (int) (pulse * 0x40) << 24 | 0xFFFFFF;
            g.fill(x - 4, y - 4, x + 5, y + 5, halo);
            g.fill(x - 2, y - 2, x + 3, y + 3, 0xC0FFFFFF);
            g.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
            return;
        }
    }

    // ------------------------------------------------------------------ shapes

    /** A vertical plane over the line a-b, from y up h; both sides. */
    private static void plane(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, double y, float h, float r, float g, float bl) {
        float len = (float) a.distanceTo(b);
        float[][] q = {{(float) a.x, (float) y, (float) a.z, 0, 1}, {(float) b.x, (float) y, (float) b.z, len / 4, 1},
                {(float) b.x, (float) (y + h), (float) b.z, len / 4, 0}, {(float) a.x, (float) (y + h), (float) a.z, 0, 0}};
        quad(pose, vc, q, r, g, bl);
    }

    /** A flat strip on the ground along a-b, w wide. */
    private static void strip(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float w, float r, float g, float bl) {
        Vec3 d = b.subtract(a).multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-d.z, 0, d.x).scale(w / 2);
        float y = (float) a.y + 0.06F;
        float len = (float) a.distanceTo(b);
        float[][] q = {{(float) (a.x - side.x), y, (float) (a.z - side.z), 0, 0}, {(float) (a.x + side.x), y, (float) (a.z + side.z), 1, 0},
                {(float) (b.x + side.x), y, (float) (b.z + side.z), 1, len}, {(float) (b.x - side.x), y, (float) (b.z - side.z), 0, len}};
        quad(pose, vc, q, r, g, bl);
    }

    /** A sheet of light between the hilt and two blade positions (a fan); both sides. */
    private static void plane3(PoseStack pose, VertexConsumer vc, Vec3 hilt, Vec3 tipA, Vec3 tipB, float r, float g, float b) {
        float[][] q = {{(float) hilt.x, (float) hilt.y, (float) hilt.z, 0, 0.5F}, {(float) hilt.x, (float) hilt.y, (float) hilt.z, 0, 0.5F},
                {(float) tipB.x, (float) tipB.y, (float) tipB.z, 4, 0}, {(float) tipA.x, (float) tipA.y, (float) tipA.z, 4, 1}};
        quad(pose, vc, q, r, g, b);
    }

    /** A soft blob lying flat at a point, of a colour and see-through. */
    private static void flatTinted(PoseStack pose, VertexConsumer vc, Vec3 at, float size, float r, float g, float b, float a) {
        float h = size / 2;
        float x = (float) at.x, y = (float) at.y, z = (float) at.z;
        float[][] q = {{x - h, y, z - h, 0, 0}, {x + h, y, z - h, 1, 0}, {x + h, y, z + h, 1, 1}, {x - h, y, z + h, 0, 1}};
        quadAlpha(pose, vc, q, r, g, b, a);
    }

    /** A square lying flat, along the axes u and v, size across, of a colour and see-through. */
    private static void flatSquare(PoseStack pose, VertexConsumer vc, Vec3 at, Vec3 u, Vec3 v, float size, float r, float g, float b, float a) {
        Vec3 du = u.scale(size / 2), dv = v.scale(size / 2);
        Vec3[] c = {at.subtract(du).subtract(dv), at.add(du).subtract(dv), at.add(du).add(dv), at.subtract(du).add(dv)};
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        float[][] q = new float[4][];
        for (int i = 0; i < 4; i++) q[i] = new float[] {(float) c[i].x, (float) c[i].y, (float) c[i].z, uv[i][0], uv[i][1]};
        quadAlpha(pose, vc, q, r, g, b, a);
    }

    private static void quadAlpha(PoseStack pose, VertexConsumer vc, float[][] q, float r, float g, float b, float a) {
        Matrix4f m = pose.last().pose();
        int cr = (int) (Mth.clamp(r, 0, 1) * 255), cg = (int) (Mth.clamp(g, 0, 1) * 255), cb = (int) (Mth.clamp(b, 0, 1) * 255);
        int ca = (int) (Mth.clamp(a, 0, 1) * 255);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] v = q[pass == 0 ? i : 3 - i];
                vc.addVertex(m, v[0], v[1], v[2]).setColor(cr, cg, cb, ca).setUv(v[3], v[4]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 1, 0);
            }
        }
    }

    /** A square lying flat at a point, size across, turned by rot radians. */
    private static void flat(PoseStack pose, VertexConsumer vc, Vec3 at, float size, float rot, float r, float g, float b) {
        float h = size / 2, cs = Mth.cos(rot) * h, sn = Mth.sin(rot) * h;
        float x = (float) at.x, y = (float) at.y, z = (float) at.z;
        float[][] q = {{x - cs + sn, y, z - sn - cs, 0, 0}, {x + cs + sn, y, z + sn - cs, 1, 0}, {x + cs - sn, y, z + sn + cs, 1, 1},
                {x - cs - sn, y, z - sn + cs, 0, 1}};
        quad(pose, vc, q, r, g, b);
    }

    /** An open cylinder round a vertical axis, the texture tiled round it and scrolled up it. */
    private static void cylinder(PoseStack pose, VertexConsumer vc, Vec3 at, float radius, double y0, double y1, float scroll, float r, float g,
            float b) {
        int n = 14;
        float v0 = scroll, v1 = scroll + (float) (y1 - y0) / 8;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float x0 = (float) at.x + Mth.cos(a0) * radius, z0 = (float) at.z + Mth.sin(a0) * radius;
            float x1 = (float) at.x + Mth.cos(a1) * radius, z1 = (float) at.z + Mth.sin(a1) * radius;
            float u0 = i * 2F / n, u1 = (i + 1) * 2F / n;
            float[][] q = {{x0, (float) y0, z0, u0, v1}, {x1, (float) y0, z1, u1, v1}, {x1, (float) y1, z1, u1, v0}, {x0, (float) y1, z0, u0, v0}};
            quad(pose, vc, q, r, g, b);
        }
    }

    /** A quad of corners {x, y, z, u, v}, both sides, full light. */
    private static void quad(PoseStack pose, VertexConsumer vc, float[][] q, float r, float g, float b) {
        Matrix4f m = pose.last().pose();
        int cr = (int) (Mth.clamp(r, 0, 1) * 255), cg = (int) (Mth.clamp(g, 0, 1) * 255), cb = (int) (Mth.clamp(b, 0, 1) * 255);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] v = q[pass == 0 ? i : 3 - i];
                vc.addVertex(m, v[0], v[1], v[2]).setColor(cr, cg, cb, 255).setUv(v[3], v[4]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 1, 0);
            }
        }
    }
}
