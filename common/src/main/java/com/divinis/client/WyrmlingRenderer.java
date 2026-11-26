package com.divinis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.divinis.boss.VoidWyrmling;
import com.divinis.Divinis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The Void Wyrmling (model and animations from Blender: WyrmlingModelData, tools/models/build_wyrmling.py), with its glow. */
public class WyrmlingRenderer extends MobRenderer<VoidWyrmling, WyrmlingRenderer.Model> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "void_wyrmling"), "main");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/void_wyrmling.png");
    private static final RenderType GLOW =
            RenderType.eyes(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/void_wyrmling_glow.png"));

    public WyrmlingRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(LAYER)), 0.3F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(VoidWyrmling wyrmling) {
        return TEXTURE;
    }

    @Override
    protected void scale(VoidWyrmling wyrmling, PoseStack pose, float partialTick) {
        pose.scale(1.2F, 1.2F, 1.2F);
    }

    public static class Model extends HierarchicalModel<VoidWyrmling> {
        private final ModelPart root;
        private final ModelPart head;

        public Model(ModelPart root) {
            this.root = root;
            this.head = root.getChild("head");
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(VoidWyrmling w, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            head.xRot = headPitch * Mth.DEG_TO_RAD; // diving and climbing
            animate(w.flyAnimation, WyrmlingModelData.FLY, ageInTicks);
            animate(w.swoopAnimation, WyrmlingModelData.SWOOP, ageInTicks);
        }
    }
}
