package com.divinis.client;

import com.divinis.boss.Minotaur;
import com.divinis.Divinis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Minotaur boss (half size; MinotaurRenderer draws it twice as big). Geometry and all animations come from
 * Blender (MinotaurModelData, tools/models/build_minotaur.py); here the head follows its target while it is awake
 * and the legs walk when it moves without attacking.
 */
public class MinotaurModel extends HierarchicalModel<Minotaur> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "minotaur"), "main");

    private final ModelPart root;
    private final ModelPart head;

    public MinotaurModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("body").getChild("chest").getChild("head");
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Minotaur boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        if (boss.isDeadOrDying()) {
            animate(boss.deathAnimation, BossDeaths.MINOTAUR, ageInTicks);
            return;
        }
        boolean awake = boss.state() == Minotaur.AWAKE;
        if (awake && !boss.isAttacking()) {
            head.yRot = Mth.clamp(netHeadYaw, -40, 40) * Mth.DEG_TO_RAD;
            head.xRot = Mth.clamp(headPitch, -25, 25) * Mth.DEG_TO_RAD;
            animateWalk(MinotaurModelData.WALK, limbSwing, limbSwingAmount, 2.0F, 2.5F);
        }
        animate(boss.sleepAnimation, MinotaurModelData.SLEEP, ageInTicks);
        animate(boss.wakeAnimation, MinotaurModelData.WAKE, ageInTicks);
        animate(boss.idleAnimation, MinotaurModelData.IDLE, ageInTicks);
        animate(boss.cleaveAnimation, MinotaurModelData.CLEAVE, ageInTicks);
        animate(boss.stompAnimation, MinotaurModelData.STOMP, ageInTicks);
        animate(boss.chargeAnimation, MinotaurModelData.CHARGE, ageInTicks);
    }
}
