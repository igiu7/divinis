package com.divinis.client;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.divinis.boss.Ronin;
import com.divinis.Divinis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Ronin 1.25 times his model's pixels: red lacquer and a straw hat in phase 1, black lacquer and a black hat
 * in phase 2 (ronin_p2.png) with his eyes and the edge of his blade burning red at full light (ronin_p2_glow.png).
 * In phase 2 his eyes leave streaks of red light behind them as he moves: each frame the eyes' places are found through
 * the model's own parts (so every turn of his head, every cut and every blink shows), kept for EYE_TRAIL_MS, and drawn
 * as two ribbons thinning and fading towards their tails, a small glow on each eye.
 * In phase 2 a dark aura burns round him, like chakra: a wreath of see-through black flame tongues (ronin_aura.png,
 * crimson at their edges) circling him, wavering, taller at the heart - flared up twice as high when he gathers it (his
 * thousand cuts), and bursting out of him in a widening ring of black flame when he lands. It dies away as he dies.
 */
public class RoninRenderer extends MobRenderer<Ronin, RoninModel> {
    private static final ResourceLocation TEXTURE = tex("ronin"), TEXTURE_P2 = tex("ronin_p2");
    private static final RenderType GLOW = RenderType.eyes(tex("ronin_p2_glow"));
    private static final RenderType AURA = RenderType.entityTranslucentEmissive(
            ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/effect/ronin_aura.png"));
    private static final float SCALE = 1.25F;
    /** Where the eyes are on the head (model pixels from the neck: the face's front, under the hat). */
    private static final float EYE_X = 1.0F, EYE_Y = -3.5F, EYE_Z = -3.7F;
    private static final long EYE_TRAIL_MS = 320;

    private record Eyes(Vec3 left, Vec3 right, long ms) {}

    private static final Map<Integer, ArrayDeque<Eyes>> TRAILS = new HashMap<>();

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/" + name + ".png");
    }

    public RoninRenderer(EntityRendererProvider.Context context) {
        super(context, new RoninModel(context.bakeLayer(RoninModel.LAYER)), 0.7F);
        addLayer(new Glow(this));
    }

    @Override
    public ResourceLocation getTextureLocation(Ronin boss) {
        return boss.phase() == 2 ? TEXTURE_P2 : TEXTURE;
    }

    @Override
    protected void scale(Ronin boss, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public void render(Ronin boss, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(boss, yaw, partialTick, pose, buffers, light);
        if (boss.phase() == 2) aura(boss, partialTick, pose, buffers);
        if (boss.phase() != 2) {
            TRAILS.remove(boss.getId());
            return;
        }
        ArrayDeque<Eyes> trail = TRAILS.get(boss.getId());
        if (trail == null || trail.size() < 2) return;
        long now = System.currentTimeMillis();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 at = boss.getPosition(partialTick);
        pose.pushPose();
        pose.translate(-at.x, -at.y, -at.z);   // world space (relative to the camera)
        var vc = buffers.getBuffer(BeamFx.BEAM);
        Eyes newer = null;
        for (Iterator<Eyes> it = trail.descendingIterator(); it.hasNext(); ) {
            Eyes older = it.next();
            if (newer != null) {
                float age = Mth.clamp((now - older.ms()) / (float) EYE_TRAIL_MS, 0, 1);
                float k = (1 - age) * (1 - age);
                float w = 0.09F * (1 - age * 0.7F);
                BeamFx.ribbon(pose, vc, cam, newer.left(), older.left(), w, 1.0F * k, 0.06F * k, 0.06F * k);
                BeamFx.ribbon(pose, vc, cam, newer.right(), older.right(), w, 1.0F * k, 0.06F * k, 0.06F * k);
            }
            newer = older;
        }
        Eyes last = trail.peekLast();
        if (last != null && !boss.isDeadOrDying()) {
            BeamFx.glow(pose, buffers, cam, last.left(), 0.32F, 1.0F, 0.12F, 0.1F);
            BeamFx.glow(pose, buffers, cam, last.right(), 0.32F, 1.0F, 0.12F, 0.1F);
        }
        pose.popPose();
    }

    /** The dark aura round him, and the wave of black flame it bursts in. */
    private static void aura(Ronin boss, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float ticks = boss.tickCount + partialTick;
        float time = ticks / 20.0F;
        float fade = boss.isDeadOrDying() ? Mth.clamp(1 - boss.dying / 40.0F, 0, 1) : 1;
        if (fade <= 0) return;
        boolean flared = boss.auraFlared();
        float tall = flared ? 2.0F : 1.0F, wide = flared ? 1.5F : 1.0F;
        // the outer wreath, circling him
        int n = 11;
        for (int i = 0; i < n; i++) {
            float a = i * Mth.TWO_PI / n + time * 0.7F;
            float r = (0.48F + 0.06F * Mth.sin(time * 3 + i)) * wide;
            float h = (2.5F + 0.45F * Mth.sin(time * 4.3F + i * 1.7F)) * tall;
            FireFx.flame(AURA, pose, buffers, camera, new Vec3(Mth.cos(a) * r, -0.1, Mth.sin(a) * r), 0.95F * wide, h, time + i * 0.37F,
                    i, fade);
        }
        // the heart of it, taller
        for (int i = 0; i < 4; i++) {
            float a = i * Mth.HALF_PI - time * 0.5F;
            float h = (3.1F + 0.5F * Mth.sin(time * 5 + i * 2.1F)) * tall;
            FireFx.flame(AURA, pose, buffers, camera, new Vec3(Mth.cos(a) * 0.15F, 0, Mth.sin(a) * 0.15F), 1.2F * wide, h, time * 1.3F + i, i + 20, fade);
        }
        // the wave of black flame
        int wave = boss.auraWave();
        if (wave >= 0 && boss.level() != null) {
            float age = (boss.level().getGameTime() + partialTick - wave) / Ronin.WAVE_TICKS;
            if (age >= 0 && age <= 1) {
                float r = 1.5F + age * (Ronin.WAVE_RADIUS - 1.5F);
                int count = 36;
                for (int i = 0; i < count; i++) {
                    float a = i * Mth.TWO_PI / count;
                    FireFx.flame(AURA, pose, buffers, camera, new Vec3(Mth.cos(a) * r, 0, Mth.sin(a) * r), 1.6F, 2.6F * (1 - age * 0.6F),
                            time + i * 0.21F, i, 1 - age * age);
                }
            }
        }
    }

    /** Phase 2: the eyes and the blade's edge at full light; and where the eyes are now, for their streaks. */
    private static final class Glow extends RenderLayer<Ronin, RoninModel> {
        Glow(RenderLayerParent<Ronin, RoninModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, Ronin boss, float limbSwing, float limbSwingAmount,
                float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (boss.phase() != 2) return;
            RoninModel model = getParentModel();
            model.renderToBuffer(pose, buffers.getBuffer(GLOW), light, OverlayTexture.NO_OVERLAY, -1);
            // the eyes' places in the world, through the parts down to the head
            pose.pushPose();
            model.root().translateAndRotate(pose);
            model.body.translateAndRotate(pose);
            model.torso.translateAndRotate(pose);
            model.head.translateAndRotate(pose);
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vector3f l = pose.last().pose().transformPosition(new Vector3f(EYE_X / 16, EYE_Y / 16, EYE_Z / 16));
            Vector3f r = pose.last().pose().transformPosition(new Vector3f(-EYE_X / 16, EYE_Y / 16, EYE_Z / 16));
            pose.popPose();
            long now = System.currentTimeMillis();
            ArrayDeque<Eyes> trail = TRAILS.computeIfAbsent(boss.getId(), id -> new ArrayDeque<>());
            trail.addLast(new Eyes(cam.add(l.x, l.y, l.z), cam.add(r.x, r.y, r.z), now));
            while (!trail.isEmpty() && now - trail.peekFirst().ms() > EYE_TRAIL_MS) trail.removeFirst();
            if (TRAILS.size() > 16) TRAILS.keySet().removeIf(id -> id != boss.getId());
        }
    }
}
