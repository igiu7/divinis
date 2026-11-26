package com.divinis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.divinis.boss.Azramond;
import com.divinis.boss.LichBones;
import com.divinis.boss.LichGhost;
import com.divinis.boss.LichSpike;
import com.divinis.boss.LichThrone;
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
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.Entity;

/**
 * Draws Azramond 1.6 times his model's pixels; his eyes, the crown's stones and the staff's crystal glow green
 * (azramond_glow.png) once he is awake, in his second phase his crown burns too (azramond_p2_glow.png). Props draws
 * his throne, the heaps of bones, the ghosts and the spikes of bone (one Blender model, AzramondPropsModelData from
 * tools/models/build_azramond_props.py, one part each).
 */
public class AzramondRenderer extends MobRenderer<Azramond, AzramondModel> {
    private static final ResourceLocation TEXTURE = tex("azramond");
    private static final RenderType GLOW = RenderType.eyes(tex("azramond_glow")), GLOW_P2 = RenderType.eyes(tex("azramond_p2_glow"));
    static final float SCALE = 1.6F;

    static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/" + name + ".png");
    }

    public AzramondRenderer(EntityRendererProvider.Context context) {
        super(context, new AzramondModel(context.bakeLayer(AzramondModel.LAYER)), 0.8F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }

            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, Azramond boss, float limbSwing, float limbSwingAmount,
                    float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                if (!boss.glowing()) return;
                var vc = buffers.getBuffer(boss.phase() == 2 ? GLOW_P2 : GLOW);
                getParentModel().renderToBuffer(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Azramond boss) {
        return TEXTURE;
    }

    @Override
    protected void scale(Azramond boss, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    protected float getFlipDegrees(Azramond boss) {
        return 0;
    }

    // ------------------------------------------------------------------ the props

    public static final ModelLayerLocation PROPS =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "azramond_props"), "main");
    private static final ResourceLocation PROPS_TEXTURE = tex("azramond_props");
    private static final RenderType PROPS_GLOW = RenderType.eyes(tex("azramond_props_glow"));

    private static final class PropsModel extends HierarchicalModel<Entity> {
        private final ModelPart root, throne, bones, ghost, spike;

        PropsModel(ModelPart root) {
            this.root = root;
            throne = root.getChild("throne");
            bones = root.getChild("bones");
            ghost = root.getChild("ghost");
            spike = root.getChild("spike");
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(Entity e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            throne.visible = e instanceof LichThrone;
            bones.visible = e instanceof LichBones;
            ghost.visible = e instanceof LichGhost;
            spike.visible = e instanceof LichSpike;
            if (e instanceof LichBones b) animate(b.rattleAnimation, AzramondPropsModelData.RATTLE, ageInTicks);
            if (e instanceof LichGhost g) animate(g.floatAnimation, AzramondPropsModelData.FLOAT, ageInTicks);
        }
    }

    /** Draws one of his props: the throne and the ghosts as big as he is, a ghost see-through, a spike rising. */
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
            if (entity instanceof LichSpike s) pose.translate(0, (s.height(partialTick) - 1) * 2.4, 0);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));
            pose.scale(-scale, -scale, scale);
            pose.translate(0, -1.501F, 0);
            float age = entity.tickCount + partialTick;
            model.setupAnim(entity, 0, 0, age, 0, 0);
            boolean ghost = entity instanceof LichGhost;
            RenderType type = ghost ? RenderType.entityTranslucent(PROPS_TEXTURE) : RenderType.entityCutoutNoCull(PROPS_TEXTURE);
            int color = ghost ? FastColor.ARGB32.color(170, 200, 255, 210) : -1;
            model.renderToBuffer(pose, buffers.getBuffer(type), ghost ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, color);
            model.renderToBuffer(pose, buffers.getBuffer(PROPS_GLOW), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffers, light);
        }
    }
}
