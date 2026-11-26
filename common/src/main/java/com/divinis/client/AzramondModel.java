package com.divinis.client;

import com.divinis.boss.Azramond;
import com.divinis.Divinis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Azramond (AzramondRenderer draws him 1.6 times his pixels). Geometry and all animations come from Blender
 * (AzramondModelData, tools/models/build_azramond.py); here his wings are shown only from his change on, his head
 * follows his target and he leans into it as he floats along.
 */
public class AzramondModel extends HierarchicalModel<Azramond> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "azramond"), "main");

    private final ModelPart root, head, wingL, wingR;

    public AzramondModel(ModelPart root) {
        this.root = root;
        ModelPart torso = root.getChild("body").getChild("torso");
        this.head = torso.getChild("head");
        this.wingL = torso.getChild("wing_l");
        this.wingR = torso.getChild("wing_r");
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Azramond boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        wingL.visible = wingR.visible = boss.winged();
        if (boss.isDeadOrDying()) {
            animate(boss.deathAnimation, AzramondModelData.DEATH, ageInTicks);
            return;
        }
        if (!boss.isAttacking()) {
            head.yRot += Mth.clamp(netHeadYaw, -40, 40) * Mth.DEG_TO_RAD;
            head.xRot += Mth.clamp(headPitch, -25, 25) * Mth.DEG_TO_RAD;
            animateWalk(AzramondModelData.DRIFT, limbSwing, limbSwingAmount, 1.0F, 2.0F);
        }
        animate(boss.sleepAnimation, AzramondModelData.SLEEP, ageInTicks);
        animate(boss.wakeAnimation, AzramondModelData.WAKE, ageInTicks);
        animate(boss.idleAnimation, AzramondModelData.IDLE, ageInTicks);
        animate(boss.fireAnimation, AzramondModelData.FIRE, ageInTicks);
        animate(boss.raiseAnimation, AzramondModelData.RAISE, ageInTicks);
        animate(boss.frostAnimation, AzramondModelData.FROST, ageInTicks);
        animate(boss.landsAnimation, AzramondModelData.LANDS, ageInTicks);
        animate(boss.shadesAnimation, AzramondModelData.SHADES, ageInTicks);
        animate(boss.curseAnimation, AzramondModelData.CURSE, ageInTicks);
        animate(boss.danceAnimation, AzramondModelData.DANCE, ageInTicks);
        animate(boss.roarAnimation, AzramondModelData.ROAR, ageInTicks);
        animate(boss.transformAnimation, AzramondModelData.TRANSFORM, ageInTicks);
    }
}
