package com.divinis.client;

import org.joml.Vector3f;

import com.divinis.boss.SerpentOfInfinity;
import com.divinis.Divinis;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

/**
 * The Serpent of Infinity's three models (geometry and animations from Blender: SerpentHead/Body/TailModelData,
 * tools/models/build_serpent.py), drawn by SerpentRenderer: the head (idle, bite, roar) and the body segments and
 * tail, each playing its loop at its own offset in time so a wave runs down the body.
 */
public final class SerpentModels {
    private SerpentModels() {}

    public static final ModelLayerLocation HEAD = layer("serpent_head");
    public static final ModelLayerLocation BODY = layer("serpent_body");
    public static final ModelLayerLocation TAIL = layer("serpent_tail");

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, name), "main");
    }

    public static class Head extends HierarchicalModel<SerpentOfInfinity> {
        private final ModelPart root;

        public Head(ModelPart root) {
            this.root = root;
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(SerpentOfInfinity boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            if (boss.isDeadOrDying()) {
                animate(boss.deathAnimation, BossDeaths.SERPENT, ageInTicks);
                return;
            }
            animate(boss.idleAnimation, SerpentHeadModelData.IDLE, ageInTicks);
            animate(boss.biteAnimation, SerpentHeadModelData.BITE, ageInTicks);
            animate(boss.roarAnimation, SerpentHeadModelData.ROAR, ageInTicks);
        }
    }

    /** A body segment or the tail: plays one looping animation at a given time. */
    public static class Segment extends HierarchicalModel<SerpentOfInfinity> {
        private static final Vector3f CACHE = new Vector3f();
        private final ModelPart root;
        private final AnimationDefinition loop;

        public Segment(ModelPart root, AnimationDefinition loop) {
            this.root = root;
            this.loop = loop;
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(SerpentOfInfinity boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            KeyframeAnimations.animate(this, loop, (long) (ageInTicks * 50.0F), 1.0F, CACHE);
        }
    }
}
