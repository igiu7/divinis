package com.divinis.client;

import com.divinis.boss.Arachnid;
import com.divinis.Divinis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Arachnid (half size; ArachnidRenderer draws it twice as big). Geometry and all animations come from Blender
 * (ArachnidModelData, tools/models/build_arachnid.py); here its cocoon is shown only while it sleeps in it, the head
 * follows its target and the legs walk when it moves between attacks.
 */
public class ArachnidModel extends HierarchicalModel<Arachnid> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "arachnid"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart cocoon;

    public ArachnidModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("body");
        this.head = body.getChild("thorax").getChild("head");
        this.cocoon = body.getChild("cocoon");
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Arachnid boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        cocoon.visible = !boss.isDeadOrDying() && boss.cocooned();
        if (boss.isDeadOrDying()) {
            animate(boss.deathAnimation, ArachnidModelData.DEATH, ageInTicks);
            return;
        }
        if (!boss.isAttacking()) {
            head.yRot += Mth.clamp(netHeadYaw, -30, 30) * Mth.DEG_TO_RAD;
            head.xRot += Mth.clamp(headPitch, -20, 20) * Mth.DEG_TO_RAD;
            animateWalk(ArachnidModelData.WALK, limbSwing, limbSwingAmount, 2.5F, 2.5F);
        }
        animate(boss.sleepAnimation, ArachnidModelData.SLEEP, ageInTicks);
        animate(boss.wakeAnimation, ArachnidModelData.WAKE, ageInTicks);
        animate(boss.idleAnimation, ArachnidModelData.IDLE, ageInTicks);
        animate(boss.pounceAnimation, ArachnidModelData.POUNCE, ageInTicks);
        animate(boss.spitAnimation, ArachnidModelData.SPIT, ageInTicks);
        animate(boss.stompAnimation, ArachnidModelData.STOMP, ageInTicks);
        animate(boss.climbAnimation, ArachnidModelData.CLIMB, ageInTicks);
        animate(boss.hangAnimation, ArachnidModelData.HANG, ageInTicks);
        animate(boss.fallAnimation, ArachnidModelData.FALL, ageInTicks);
        animate(boss.layAnimation, ArachnidModelData.LAY, ageInTicks);
        animate(boss.shootAnimation, ArachnidModelData.SHOOT, ageInTicks);
        animate(boss.rainAnimation, ArachnidModelData.RAIN, ageInTicks);
        animate(boss.roarAnimation, ArachnidModelData.ROAR, ageInTicks);
        animate(boss.transformAnimation, ArachnidModelData.TRANSFORM, ageInTicks);
    }
}
