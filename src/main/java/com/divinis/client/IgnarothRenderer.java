package com.divinis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.divinis.boss.Ballista;
import com.divinis.boss.BallistaBolt;
import com.divinis.boss.DragonHoard;
import com.divinis.boss.Ignaroth;
import com.divinis.Divinis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws Ignaroth 2.2 times his model's pixels; his eyes, the fire in his throat and between his belly plates and the
 * veins of his wings glow (ignaroth_glow.png). Round his crater, once it has cracked, the lava he raised: a ring of
 * molten rock (ignaroth_lava.png, slowly flowing) from Ignaroth.lava() out LAVA_WIDTH blocks, at full light.
 * Props draws his hoard, the ballistae (their bolt on the rail when loaded, turned and tilted with their gunner) and
 * the bolts in flight (one Blender model, IgnarothPropsModelData from tools/models/build_ignaroth_props.py).
 */
public class IgnarothRenderer extends MobRenderer<Ignaroth, IgnarothModel> {
    private static final ResourceLocation TEXTURE = tex("ignaroth"), LAVA = tex("ignaroth_lava");
    private static final RenderType GLOW = RenderType.eyes(tex("ignaroth_glow"));
    static final float SCALE = 2.2F;
    private static final int SEGMENTS = 64;

    static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/" + name + ".png");
    }

    public IgnarothRenderer(EntityRendererProvider.Context context) {
        super(context, new IgnarothModel(context.bakeLayer(IgnarothModel.LAYER)), 3.0F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Ignaroth boss) {
        return TEXTURE;
    }

    @Override
    protected void scale(Ignaroth boss, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    protected float getFlipDegrees(Ignaroth boss) {
        return 0;
    }

    @Override
    public void render(Ignaroth boss, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        shadowRadius = boss.flying() ? 0 : 3.0F;
        super.render(boss, yaw, partialTick, pose, buffers, light);
        float r = boss.lava();
        if (r > 0) lava(boss, r, partialTick, pose, buffers);
    }

    /** The ring of lava round his crater, drawn from where he is now. */
    private void lava(Ignaroth boss, float inner, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Vec3 home = boss.homeSynced();
        double ex = Mth.lerp(partialTick, boss.xo, boss.getX()), ey = Mth.lerp(partialTick, boss.yo, boss.getY()), ez = Mth.lerp(partialTick, boss.zo, boss.getZ());
        float ox = (float) (home.x - ex), oy = (float) (home.y - ey) + 0.06F, oz = (float) (home.z - ez);
        float outer = inner + Ignaroth.LAVA_WIDTH;
        float flow = (boss.tickCount + partialTick) * 0.004F;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(LAVA));
        PoseStack.Pose p = pose.last();
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i * Mth.TWO_PI / SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
            float[][] pts = {
                    {(float) Math.cos(a0) * inner, (float) Math.sin(a0) * inner}, {(float) Math.cos(a0) * outer, (float) Math.sin(a0) * outer},
                    {(float) Math.cos(a1) * outer, (float) Math.sin(a1) * outer}, {(float) Math.cos(a1) * inner, (float) Math.sin(a1) * inner}};
            for (float[] q : pts) {
                float x = ox + q[0], z = oz + q[1];
                vc.addVertex(p, x, oy, z).setColor(255, 255, 255, 255).setUv((float) (home.x + q[0]) / 4 + flow, (float) (home.z + q[1]) / 4 + flow * 0.6F)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
            }
        }
    }

    // ------------------------------------------------------------------ the props

    public static final ModelLayerLocation PROPS =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "ignaroth_props"), "main");
    private static final ResourceLocation PROPS_TEXTURE = tex("ignaroth_props");

    private static final class PropsModel extends HierarchicalModel<Entity> {
        private final ModelPart root, hoard, ballista, ballistaTop, ballistaBolt, bolt;

        PropsModel(ModelPart root) {
            this.root = root;
            hoard = root.getChild("hoard");
            ballista = root.getChild("ballista");
            ballistaTop = ballista.getChild("ballista_top");
            ballistaBolt = ballistaTop.getChild("ballista_bolt");
            bolt = root.getChild("bolt");
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(Entity e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            hoard.visible = e instanceof DragonHoard;
            ballista.visible = e instanceof Ballista;
            bolt.visible = e instanceof BallistaBolt;
            ballistaBolt.visible = e instanceof Ballista b && b.loaded();
            if (e instanceof Ballista) ballistaTop.xRot = e.getXRot() * Mth.DEG_TO_RAD; // tilted with its gunner's view
        }
    }

    public static final class Props<T extends Entity> extends EntityRenderer<T> {
        private final PropsModel model;
        private final float scale;

        public Props(EntityRendererProvider.Context context, float scale, float shadow) {
            super(context);
            this.model = new PropsModel(context.bakeLayer(PROPS));
            this.scale = scale;
            this.shadowRadius = shadow;
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return PROPS_TEXTURE;
        }

        @Override
        public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            pose.pushPose();
            float y = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            if (entity instanceof BallistaBolt) {
                pose.translate(0, 0.1, 0);
                pose.mulPose(Axis.YP.rotationDegrees(180.0F - y));
                pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
                pose.translate(0, -0.1, 0);
            } else {
                pose.mulPose(Axis.YP.rotationDegrees(180.0F - y));
            }
            pose.scale(-scale, -scale, scale);
            pose.translate(0, -1.501F, 0);
            model.setupAnim(entity, 0, 0, entity.tickCount + partialTick, 0, 0);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(PROPS_TEXTURE)), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffers, light);
        }
    }
}
