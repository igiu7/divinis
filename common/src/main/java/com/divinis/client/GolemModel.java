package com.divinis.client;

import com.divinis.boss.Golem;
import com.divinis.Divinis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Golem boss (quarter size; GolemRenderer draws it four times as big). Geometry and all animations come from
 * Blender (GolemModelData, tools/models/build_golem.py); here the head follows its target while it is awake and the
 * legs walk when it moves without attacking.
 */
public class GolemModel extends HierarchicalModel<Golem> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "golem"), "main");

    private final ModelPart root;
    private final ModelPart head;

    public GolemModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("body").getChild("torso").getChild("head");
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Golem boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        if (boss.isDeadOrDying()) {
            animate(boss.deathAnimation, BossDeaths.GOLEM, ageInTicks);
            return;
        }
        if (boss.state() == Golem.AWAKE && !boss.isAttacking()) {
            head.yRot += Mth.clamp(netHeadYaw, -35, 35) * Mth.DEG_TO_RAD;
            head.xRot += Mth.clamp(headPitch, -20, 20) * Mth.DEG_TO_RAD;
            animateWalk(GolemModelData.WALK, limbSwing, limbSwingAmount, 1.5F, 2.5F);
        }
        animate(boss.sleepAnimation, GolemModelData.SLEEP, ageInTicks);
        animate(boss.wakeAnimation, GolemModelData.WAKE, ageInTicks);
        animate(boss.idleAnimation, GolemModelData.IDLE, ageInTicks);
        animate(boss.spinAnimation, GolemModelData.SPIN, ageInTicks);
        animate(boss.throwAnimation, GolemModelData.THROW, ageInTicks);
        animate(boss.spikesAnimation, GolemModelData.SPIKES, ageInTicks);
    }
}
