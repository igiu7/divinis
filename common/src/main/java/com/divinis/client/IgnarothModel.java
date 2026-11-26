package com.divinis.client;

import com.divinis.boss.Ignaroth;
import com.divinis.Divinis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Ignaroth (IgnarothRenderer draws him 2.2 times his pixels). Geometry and all animations come from Blender
 * (IgnarothModelData, tools/models/build_ignaroth.py); here his head follows his target and he walks when he moves on
 * the ground between attacks.
 */
public class IgnarothModel extends HierarchicalModel<Ignaroth> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "ignaroth"), "main");

    private final ModelPart root, head;

    public IgnarothModel(ModelPart root) {
        this.root = root;
        ModelPart n = root.getChild("body");
        for (int i = 0; i < 4; i++) n = n.getChild("neck" + i);
        this.head = n.getChild("head");
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Ignaroth boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        if (boss.isDeadOrDying()) {
            animate(boss.deathAnimation, IgnarothModelData.DEATH, ageInTicks);
            return;
        }
        if (!boss.isAttacking()) {
            head.yRot += Mth.clamp(netHeadYaw, -35, 35) * Mth.DEG_TO_RAD;
            head.xRot += Mth.clamp(headPitch, -25, 25) * Mth.DEG_TO_RAD;
            if (!boss.flying()) animateWalk(IgnarothModelData.WALK, limbSwing, limbSwingAmount, 1.5F, 2.5F);
        }
        animate(boss.sleepAnimation, IgnarothModelData.SLEEP, ageInTicks);
        animate(boss.wakeAnimation, IgnarothModelData.WAKE, ageInTicks);
        animate(boss.idleAnimation, IgnarothModelData.IDLE, ageInTicks);
        animate(boss.flyAnimation, IgnarothModelData.FLY, ageInTicks);
        animate(boss.breathAnimation, IgnarothModelData.BREATH, ageInTicks);
        animate(boss.tailAnimation, IgnarothModelData.TAIL, ageInTicks);
        animate(boss.wingsAnimation, IgnarothModelData.WINGS, ageInTicks);
        animate(boss.bombAnimation, IgnarothModelData.BOMB, ageInTicks);
        animate(boss.meteorsAnimation, IgnarothModelData.METEORS, ageInTicks);
        animate(boss.diveAnimation, IgnarothModelData.DIVE, ageInTicks);
        animate(boss.fallAnimation, IgnarothModelData.FALL, ageInTicks);
        animate(boss.stunnedAnimation, IgnarothModelData.STUNNED, ageInTicks);
        animate(boss.recoverAnimation, IgnarothModelData.RECOVER, ageInTicks);
        animate(boss.roarAnimation, IgnarothModelData.ROAR, ageInTicks);
        animate(boss.transformAnimation, IgnarothModelData.TRANSFORM, ageInTicks);
    }
}
