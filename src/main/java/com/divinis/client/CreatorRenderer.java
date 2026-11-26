package com.divinis.client;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.divinis.boss.NamelessCreator;
import com.divinis.Divinis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Nameless Creator in its phase (models and animations from Blender: CreatorP1..P4ModelData,
 * tools/models/build_creator_p*.py), with its glow (creator_p*_glow.png at full light). Phases 1-3 three times their
 * pixels, the True Form 1.5 times, floating. Nothing while it waits; its puppet strings only while it is let down.
 * Phase 1: the light through its rose window flares while it charges a starburst or holds the collapsing star; its
 * face is the live map of its arena (client.CreatorMap). Changing into phase 2 the paper burns away in a widening
 * spatter of holes with glowing edges, and the second phase's body shows through; changing into phase 3 the halves burn
 * away from the edges in, their rims glowing orange, then white.
 * Phase 2: the light of its window and wing flares while its sparks fly or its black stars circle.
 * Phase 3: its hands over its face part further the less health it has left. Changing into the true form, only its
 * outlines show at first (creator_p4_wire.png), then they fill with white light from the face down. Its face flickers
 * (speak) while the chequer moves, and its light grows the longer you look at it.
 * The True Form's face turns to whoever looks at it, whatever its body does, and its white body gives off real light
 * (client.BloomFx): the body alone (not the face, not the outlines of its servants behind it) is drawn a second time
 * as the light's shape.
 */
public class CreatorRenderer extends MobRenderer<NamelessCreator, CreatorRenderer.Model> {
    public static final ModelLayerLocation[] LAYERS = {layer("creator_p1"), layer("creator_p2"), layer("creator_p3"), layer("creator_p4")};
    private static final ResourceLocation[] TEXTURES = {tex("creator_p1"), tex("creator_p2"), tex("creator_p3"), tex("creator_p4")};
    private static final ResourceLocation WIRE = tex("creator_p4_wire");
    private static final RenderType[] GLOW = {RenderType.eyes(tex("creator_p1_glow")), RenderType.eyes(tex("creator_p2_glow")),
            RenderType.eyes(tex("creator_p3_glow")), RenderType.eyes(tex("creator_p4_glow"))};
    private static final float[] SCALE = {3.0F, 3.0F, 3.0F, 1.5F};
    private static final float[] LIFT = {0.3F, 0.3F, 0.3F, 1.0F};
    /** The True Form's light: a faintly warm white. */
    private static final int LIGHT = 0xFFF8EE;
    private static final float LIGHT_STRENGTH = 1.6F;
    /** Where the True Form's face is (model y of its middle; tools/models/creator_common.P4_FACE_Y). */
    private static final float P4_FACE_Y = -22;

    private final Model[] models;
    // what the next super.render draws
    private int drawing;
    private float glow = 1, extraScale = 1;
    private boolean dissolving, wire;

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, name), "main");
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/" + name + ".png");
    }

    public CreatorRenderer(EntityRendererProvider.Context context) {
        super(context, null, 0);
        AnimationDefinition[] p1 = new AnimationDefinition[NamelessCreator.Anim.values().length];
        p1[NamelessCreator.Anim.SPAWN.ordinal()] = CreatorP1ModelData.SPAWN;
        p1[NamelessCreator.Anim.STARBURST.ordinal()] = CreatorP1ModelData.STARBURST;
        p1[NamelessCreator.Anim.SLASH.ordinal()] = CreatorP1ModelData.SLASH;
        p1[NamelessCreator.Anim.PENTAGRAM.ordinal()] = CreatorP1ModelData.PENTAGRAM;
        p1[NamelessCreator.Anim.RING.ordinal()] = CreatorP1ModelData.RING;
        p1[NamelessCreator.Anim.BLOOM.ordinal()] = CreatorP1ModelData.BLOOM;
        p1[NamelessCreator.Anim.COLLAPSE.ordinal()] = CreatorP1ModelData.COLLAPSE;
        p1[NamelessCreator.Anim.CHART.ordinal()] = CreatorP1ModelData.CHART;
        p1[NamelessCreator.Anim.CRACK.ordinal()] = CreatorP1ModelData.CRACK;
        AnimationDefinition[] p2 = new AnimationDefinition[NamelessCreator.Anim.values().length];
        p2[NamelessCreator.Anim.CRUSH.ordinal()] = CreatorP2ModelData.CRUSH;
        p2[NamelessCreator.Anim.JUDGEMENT.ordinal()] = CreatorP2ModelData.JUDGEMENT;
        p2[NamelessCreator.Anim.PUNCH_L.ordinal()] = CreatorP2ModelData.PUNCH_L;
        p2[NamelessCreator.Anim.PUNCH_R.ordinal()] = CreatorP2ModelData.PUNCH_R;
        p2[NamelessCreator.Anim.SPARKS.ordinal()] = CreatorP2ModelData.SPARKS;
        p2[NamelessCreator.Anim.SUPERNOVA.ordinal()] = CreatorP2ModelData.SUPERNOVA;
        p2[NamelessCreator.Anim.SWORD.ordinal()] = CreatorP2ModelData.SWORD;
        p2[NamelessCreator.Anim.ECHO.ordinal()] = CreatorP2ModelData.ECHO;
        p2[NamelessCreator.Anim.BURN.ordinal()] = CreatorP2ModelData.BURN;
        AnimationDefinition[] p3 = new AnimationDefinition[NamelessCreator.Anim.values().length];
        p3[NamelessCreator.Anim.EMERGE.ordinal()] = CreatorP3ModelData.EMERGE;
        p3[NamelessCreator.Anim.UNVEIL.ordinal()] = CreatorP3ModelData.UNVEIL;
        p3[NamelessCreator.Anim.LSLASH.ordinal()] = CreatorP3ModelData.LSLASH;
        p3[NamelessCreator.Anim.CHRONOS.ordinal()] = CreatorP3ModelData.CHRONOS;
        p3[NamelessCreator.Anim.CAGE.ordinal()] = CreatorP3ModelData.CAGE;
        p3[NamelessCreator.Anim.CHUNKS.ordinal()] = CreatorP3ModelData.CHUNKS;
        AnimationDefinition[] p4 = new AnimationDefinition[NamelessCreator.Anim.values().length];
        p4[NamelessCreator.Anim.REVEAL.ordinal()] = CreatorP4ModelData.REVEAL;
        p4[NamelessCreator.Anim.ERASE.ordinal()] = CreatorP4ModelData.ERASE;
        p4[NamelessCreator.Anim.CHECKER.ordinal()] = CreatorP4ModelData.CHECKER;
        p4[NamelessCreator.Anim.GIGA.ordinal()] = CreatorP4ModelData.GIGA;
        p4[NamelessCreator.Anim.CREATION.ordinal()] = CreatorP4ModelData.CREATION;
        p4[NamelessCreator.Anim.DEATH.ordinal()] = CreatorP4ModelData.DEATH;
        AnimationDefinition[] none = new AnimationDefinition[NamelessCreator.Anim.values().length];
        models = new Model[] {
                new Model(context.bakeLayer(LAYERS[0]), 1, CreatorP1ModelData.IDLE, p1),
                new Model(context.bakeLayer(LAYERS[1]), 2, CreatorP2ModelData.IDLE, p2),
                new Model(context.bakeLayer(LAYERS[2]), 3, CreatorP3ModelData.IDLE, p3),
                new Model(context.bakeLayer(LAYERS[3]), 4, CreatorP4ModelData.IDLE, p4)};
        model = models[0];
        addLayer(new RenderLayer<>(this) { // the glow
            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, NamelessCreator e, float limbSwing, float limbSwingAmount,
                    float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                if (glow <= 0.01F) return;
                VertexConsumer vc = buffers.getBuffer(GLOW[drawing]);
                for (float left = glow; left > 0.01F; left -= 1) {
                    int c = (int) (Math.min(1, left) * 255);
                    getParentModel().renderToBuffer(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFF000000 | c << 16 | c << 8 | c);
                }
            }
        });
        addLayer(new RenderLayer<>(this) { // the live map of its face
            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, NamelessCreator e, float limbSwing, float limbSwingAmount,
                    float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                if (drawing != 0 || dissolving) return;
                ResourceLocation map = CreatorMap.update(e);
                VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(map));
                getParentModel().mapFace(pose, vc, 1);
                getParentModel().mapFace(pose, vc, -1);
            }
        });
    }

    private static int index(NamelessCreator e) {
        return Mth.clamp(e.phase(), 1, 4) - 1;
    }

    @Override
    public ResourceLocation getTextureLocation(NamelessCreator e) {
        return wire ? WIRE : dissolving ? DISSOLVE[drawing].location : TEXTURES[drawing];
    }

    @Nullable
    @Override
    protected RenderType getRenderType(NamelessCreator e, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityCutoutNoCull(getTextureLocation(e));
    }

    @Override
    protected void scale(NamelessCreator e, PoseStack pose, float partialTick) {
        float s = SCALE[drawing] * extraScale;
        pose.scale(s, s, s);
    }

    @Override
    protected float getFlipDegrees(NamelessCreator e) {
        return 0;
    }

    @Override
    public void render(NamelessCreator e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (e.state() == NamelessCreator.WAITING) return;
        int i = index(e);
        float age = e.stateAge(partialTick);
        pose.pushPose();
        pose.translate(0, LIFT[i], 0);
        if (e.state() == NamelessCreator.TRANSFORM && i == 0 && age >= NamelessCreator.DISSOLVE_FROM) {
            // the paper burning away, the second phase's body showing through
            float k = Mth.clamp((age - NamelessCreator.DISSOLVE_FROM) / (NamelessCreator.TRANSFORM_TICKS - NamelessCreator.DISSOLVE_FROM), 0, 1);
            draw(e, yaw, partialTick, pose, buffers, light, 1, k, 0.985F, false);
            DISSOLVE[0].to(k, 0xFF60E0FF);
            draw(e, yaw, partialTick, pose, buffers, light, 0, 0, 1, true);
        } else if (e.state() == NamelessCreator.TRANSFORM && i == 3) {
            // the true form: its outlines first, then white light filling them from the face down
            float fill = Mth.clamp((age - 101) / 80, 0, 1);
            wire = true;
            draw(e, yaw, partialTick, pose, buffers, LightTexture.FULL_BRIGHT, 3, 0, 1.004F, false);
            wire = false;
            models[3].fill = fill;
            draw(e, yaw, partialTick, pose, buffers, LightTexture.FULL_BRIGHT, 3, fill, 1, false);
            models[3].fill = 1;
        } else if (e.state() == NamelessCreator.TRANSFORM && i == 1) {
            // both halves burning like paper from the edges in, the rims orange, then white
            float k = Mth.clamp(age / 100F, 0, 1);
            int rim = k < 0.6F ? 0xFF2080FF : 0xFFD0F0FF;
            DISSOLVE[1].to(k * 0.97F, rim);
            draw(e, yaw, partialTick, pose, buffers, light, 1, 1 - k, 1, true);
        } else {
            float flare = 1;
            if (i == 0 && (e.attack() == NamelessCreator.Attack.STARBURST || e.attack() == NamelessCreator.Attack.COLLAPSE)) {
                flare = 2.2F + 0.4F * Mth.sin((e.tickCount + partialTick) * 0.6F);
            }
            if (i == 1 && (e.attack() == NamelessCreator.Attack.SPARKS || e.attack() == NamelessCreator.Attack.SUPERNOVA
                    || e.attack() == NamelessCreator.Attack.SWORD)) {
                flare = 2.0F + 0.5F * Mth.sin((e.tickCount + partialTick) * 0.5F);
            }
            draw(e, yaw, partialTick, pose, buffers, i == 3 ? LightTexture.FULL_BRIGHT : light, i, flare, 1, false);
            if (i == 3) { // the white body once more, as the shape of its light
                model.bodyOnly = true;
                float strength = LIGHT_STRENGTH * (1 + 1.5F * CreatorFx.gaze());
                super.render(e, yaw, partialTick, pose, BloomFx.capture(getRenderType(e, true, false, false), LIGHT, strength), light);
                model.bodyOnly = false;
            }
        }
        pose.popPose();
    }

    private void draw(NamelessCreator e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int phase, float glow,
            float scale, boolean dissolving) {
        drawing = phase;
        model = models[phase];
        this.glow = glow;
        this.extraScale = scale;
        this.dissolving = dissolving;
        super.render(e, yaw, partialTick, pose, buffers, light);
        this.dissolving = false;
        this.extraScale = 1;
        this.glow = 1;
    }

    /** One phase's model: its idle and attacks; the puppet strings only while it comes; the True Form's face on the camera. */
    public static final class Model extends HierarchicalModel<NamelessCreator> {
        private final ModelPart root;
        private final AnimationDefinition idle;
        private final AnimationDefinition[] anims;
        private final List<ModelPart> strings = new ArrayList<>(), shineless = new ArrayList<>();
        private final List<ModelPart> filling = new ArrayList<>();
        private final List<Float> fillAt = new ArrayList<>();
        private final int phase;
        private static final Vector3f CACHE = new Vector3f();
        /** The true form filled with light this far (0..1): the parts filled in order from the face down. */
        float fill = 1;
        @Nullable private final ModelPart head;
        @Nullable private final ModelPart[][] faceChain;
        /** Drawing only what gives light (the True Form's body). */
        boolean bodyOnly;

        Model(ModelPart root, int phase, AnimationDefinition idle, AnimationDefinition[] anims) {
            this.root = root;
            this.idle = idle;
            this.anims = anims;
            this.phase = phase;
            if (phase == 4) {
                String[][] order = {{"body", "0.1"}, {"chest", "0.15"}, {"leg_l", "0.3"}, {"leg_r", "0.3"}, {"arm_l", "0.35"}, {"arm_r", "0.35"},
                        {"shin_l", "0.5"}, {"shin_r", "0.5"}, {"forearm_l", "0.5"}, {"forearm_r", "0.5"}, {"hand_l", "0.65"}, {"hand_r", "0.65"},
                        {"streak_l", "0.75"}, {"streak_r", "0.75"}, {"remnant_wing_l", "0.95"}, {"remnant_wing_r", "0.95"},
                        {"remnant_horn_l0", "0.95"}, {"remnant_horn_r0", "0.95"}};
                for (String[] o : order) {
                    getAnyDescendantWithName(o[0]).ifPresent(p -> {
                        filling.add(p);
                        fillAt.add(Float.parseFloat(o[1]));
                    });
                }
            }
            for (String s : new String[] {"string_hand_l", "string_hand_r", "string_head_l", "string_head_r"}) {
                getAnyDescendantWithName(s).ifPresent(strings::add);
            }
            this.head = phase == 4 ? root.getChild("head") : null;
            if (head != null) shineless.add(head);
            for (String s : new String[] {"remnant_wing_l", "remnant_wing_r", "remnant_horn_l0", "remnant_horn_r0"}) {
                getAnyDescendantWithName(s).ifPresent(shineless::add);
            }
            if (phase == 1) {
                ModelPart body = root.getChild("body");
                faceChain = new ModelPart[2][];
                for (int side = 0; side < 2; side++) {
                    String t = side == 0 ? "l" : "r";
                    ModelPart half = body.getChild("half_" + t), head = half.getChild("head_" + t);
                    faceChain[side] = new ModelPart[] {root, body, half, head, head.getChild("face_" + t)};
                }
            } else {
                faceChain = null;
            }
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(NamelessCreator e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            if (e.state() == NamelessCreator.FIGHT && !e.isDeadOrDying()) animate(e.idleAnimation, idle, ageInTicks);
            if (phase == 4 && e.attack() == NamelessCreator.Attack.CHECKER) animate(e.idleAnimation, CreatorP4ModelData.SPEAK, ageInTicks);
            for (int i = 0; i < anims.length; i++) if (anims[i] != null) animate(e.anims[i], anims[i], ageInTicks);
            for (ModelPart p : strings) p.visible = e.state() == NamelessCreator.SPAWNING;
            for (ModelPart p : shineless) p.visible = !bodyOnly;
            for (ModelPart p : filling) if (!shineless.contains(p)) p.visible = true;
            for (int i = 0; i < filling.size(); i++) if (fill < fillAt.get(i)) filling.get(i).visible = false;
            if (phase == 3 && e.state() == NamelessCreator.FIGHT) { // its hands parting as its health goes
                float k = Mth.clamp((0.25F - e.getHealth() / e.getMaxHealth()) / 0.15F, 0, 1);
                if (k > 0) KeyframeAnimations.animate(this, CreatorP3ModelData.SPREAD, 0, k, CACHE);
            }
            if (head != null) lookAtCamera(e, ageInTicks - e.tickCount);
        }

        /** Turns the face to the camera (the True Form's face is always on whoever looks). */
        private void lookAtCamera(NamelessCreator e, float partialTick) {
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 at = e.getPosition(partialTick).add(0, LIFT[3] + SCALE[3] * (24 - P4_FACE_Y) / 16.0, 0);
            double dx = cam.x - at.x, dy = cam.y - at.y, dz = cam.z - at.z;
            float yawTo = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            float body = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
            head.yRot = Mth.wrapDegrees(yawTo - body) * Mth.DEG_TO_RAD;
            head.xRot = (float) -Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        }

        /**
         * The map over one half of its face (side 1: its left, the map's right half; -1: its right, the map's left half):
         * on the front of that half's map, where the model has it now.
         */
        void mapFace(PoseStack pose, VertexConsumer vc, int side) {
            if (faceChain == null) return;
            pose.pushPose();
            for (ModelPart p : faceChain[side > 0 ? 0 : 1]) p.translateAndRotate(pose);
            float x0 = side > 0 ? 0 : -5 / 16F, x1 = side > 0 ? 5 / 16F : 0;
            float u0 = side > 0 ? 0.5F : 0, u1 = side > 0 ? 1 : 0.5F;
            float y0 = -4.5F / 16, y1 = 4.5F / 16, z = -1.02F / 16;
            Matrix4f m = pose.last().pose();
            float[][] q = {{x0, y0, u0, 0}, {x1, y0, u1, 0}, {x1, y1, u1, 1}, {x0, y1, u0, 1}};
            for (float[] v : q) {
                vc.addVertex(m, v[0], v[1], z).setColor(255, 255, 255, 255).setUv(v[2], v[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 0, -1);
            }
            pose.popPose();
        }
    }

    private static final Dissolve[] DISSOLVE = {new Dissolve(0), new Dissolve(1)};

    /** A phase's texture burning away: pixels gone in a spatter that widens, a glowing rim round each hole. */
    private static final class Dissolve {
        final ResourceLocation location;
        private final int phase;
        private DynamicTexture texture;
        private NativeImage base;
        private float[] noise;
        private float shown = -1;
        private int shownRim;

        Dissolve(int phase) {
            this.phase = phase;
            this.location = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "creator_p" + (phase + 1) + "_dissolve");
        }

        /** Burnt this far (0..1), the rims of this colour (ABGR). */
        void to(float k, int rim) {
            Minecraft mc = Minecraft.getInstance();
            if (texture == null) {
                try (InputStream in = mc.getResourceManager().getResource(TEXTURES[phase]).orElseThrow().open()) {
                    base = NativeImage.read(in);
                } catch (Exception ex) {
                    return;
                }
                texture = new DynamicTexture(base.getWidth(), base.getHeight(), false);
                mc.getTextureManager().register(location, texture);
                RandomSource rnd = RandomSource.create(7 + phase);
                noise = new float[base.getWidth() * base.getHeight()];
                int w = base.getWidth(), h = base.getHeight();
                for (int i = 0; i < noise.length; i++) {
                    // phase 1 burns in a spatter; phase 2 from the edges of each piece in
                    float n = rnd.nextFloat();
                    if (phase == 1) {
                        int x = i % w, y = i / w;
                        float edge = Math.min(Math.min(x % 8, 7 - x % 8), Math.min(y % 8, 7 - y % 8)) / 3.5F;
                        n = n * 0.35F + Math.min(1, edge) * 0.65F;
                    }
                    noise[i] = n;
                }
            }
            if (Math.abs(k - shown) < 0.02F && rim == shownRim) return;
            shown = k;
            shownRim = rim;
            NativeImage img = texture.getPixels();
            if (img == null) return;
            int w = base.getWidth();
            for (int y = 0; y < base.getHeight(); y++) {
                for (int x = 0; x < w; x++) {
                    float n = noise[y * w + x];
                    int c = base.getPixelRGBA(x, y);
                    if (n < k) c = 0;
                    else if (n < k + 0.06F && (c >>> 24) != 0) c = rim; // the burning rim
                    img.setPixelRGBA(x, y, c);
                }
            }
            texture.upload();
        }
    }
}
