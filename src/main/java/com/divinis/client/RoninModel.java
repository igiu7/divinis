package com.divinis.client;

import com.divinis.boss.Ronin;
import com.divinis.Divinis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

/**
 * The Ronin (RoninModelData, from tools/models/build_ronin.py): he walks when he moves without attacking; everything
 * else comes from his animation states. Its parts down to the head are kept for the eyes' streaks (RoninRenderer).
 */
public class RoninModel extends HierarchicalModel<Ronin> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "ronin"), "main");

    private final ModelPart root;
    final ModelPart body, torso, head;

    public RoninModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.torso = body.getChild("torso");
        this.head = torso.getChild("head");
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Ronin boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        if (boss.isDeadOrDying()) {
            animate(boss.deathAnimation, RoninModelData.DEATH, ageInTicks);
            return;
        }
        if (!boss.isAttacking()) {
            animateWalk(RoninModelData.WALK, limbSwing, limbSwingAmount, 2.0F, 2.5F);
            head.yRot += Math.max(-0.6F, Math.min(0.6F, netHeadYaw * 0.0174533F));
            head.xRot += Math.max(-0.4F, Math.min(0.4F, headPitch * 0.0174533F));
        }
        animate(boss.idleAnimation, RoninModelData.IDLE, ageInTicks);
        animate(boss.slashAnimation, RoninModelData.SLASH, ageInTicks);
        animate(boss.flickerAnimation, RoninModelData.FLICKER, ageInTicks);
        animate(boss.iaiAnimation, RoninModelData.IAI, ageInTicks);
        animate(boss.backstepAnimation, RoninModelData.BACKSTEP, ageInTicks);
        animate(boss.crossAnimation, RoninModelData.CROSS, ageInTicks);
        animate(boss.leapAnimation, RoninModelData.LEAP, ageInTicks);
        animate(boss.plungeAnimation, RoninModelData.PLUNGE, ageInTicks);
        animate(boss.transformAnimation, RoninModelData.TRANSFORM, ageInTicks);
        animate(boss.stunAnimation, RoninModelData.STUN, ageInTicks);
    }
}
