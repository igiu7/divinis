package com.divinis.client;

import com.divinis.boss.BarrowChieftain;
import com.divinis.Divinis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Barrow Chieftain (BarrowChieftainRenderer draws it 1.5 times its pixels). Geometry and all animations come from
 * Blender (BarrowChieftainModelData, tools/models/build_barrow_chieftain.py); here the bier is shown only while he
 * sleeps and wakes, the chariot from his change on, the spear in his hand while it is not flying; in the chariot he
 * stands RIDE pixels higher, its wheels turn as it runs, the head follows his target and the legs (or the horses')
 * move as he does.
 */
public class BarrowChieftainModel extends HierarchicalModel<BarrowChieftain> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "barrow_chieftain"), "main");
    /** how much higher he stands in the chariot (pixels; as RIDE in tools/models/build_barrow_chieftain.py) */
    private static final float RIDE = -9.0F;

    private final ModelPart root, body, head, spear, bier, chariot, wheelL, wheelR;

    public BarrowChieftainModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        ModelPart torso = body.getChild("torso");
        this.head = torso.getChild("head");
        this.spear = torso.getChild("arm_r").getChild("forearm_r").getChild("spear");
        this.bier = root.getChild("bier");
        this.chariot = root.getChild("chariot");
        ModelPart car = chariot.getChild("car");
        this.wheelL = car.getChild("wheel_l");
        this.wheelR = car.getChild("wheel_r");
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(BarrowChieftain boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        byte state = boss.state();
        chariot.visible = boss.chariotShown();
        bier.visible = state == BarrowChieftain.SLEEPING || state == BarrowChieftain.WAKING;
        spear.visible = !boss.spearOut();
        float partial = ageInTicks - boss.tickCount;
        float turn = Mth.lerp(partial, boss.wheelTurnO, boss.wheelTurn);
        if (boss.isDeadOrDying()) {
            animate(boss.deathAnimation, BarrowChieftainModelData.DEATH, ageInTicks);
            return;
        }
        if (!boss.isAttacking()) {
            if (boss.phase() == 1) {
                head.yRot += Mth.clamp(netHeadYaw, -40, 40) * Mth.DEG_TO_RAD;
                head.xRot += Mth.clamp(headPitch, -25, 25) * Mth.DEG_TO_RAD;
                animateWalk(BarrowChieftainModelData.WALK, limbSwing, limbSwingAmount, 2.0F, 2.5F);
            }
        }
        if (boss.phase() == 2 && state == BarrowChieftain.AWAKE && !boss.stunned()) {
            animateWalk(BarrowChieftainModelData.GALLOP, limbSwing, limbSwingAmount, 1.0F, 3.0F);
        }
        animate(boss.sleepAnimation, BarrowChieftainModelData.SLEEP, ageInTicks);
        animate(boss.wakeAnimation, BarrowChieftainModelData.WAKE, ageInTicks);
        animate(boss.idleAnimation, BarrowChieftainModelData.IDLE, ageInTicks);
        animate(boss.rideAnimation, BarrowChieftainModelData.RIDE, ageInTicks);
        animate(boss.thrustAnimation, BarrowChieftainModelData.THRUST, ageInTicks);
        animate(boss.guardAnimation, BarrowChieftainModelData.GUARD, ageInTicks);
        animate(boss.bashAnimation, BarrowChieftainModelData.BASH, ageInTicks);
        animate(boss.throwAnimation, BarrowChieftainModelData.THROW, ageInTicks);
        animate(boss.transformAnimation, BarrowChieftainModelData.TRANSFORM, ageInTicks);
        animate(boss.chargeAnimation, BarrowChieftainModelData.CHARGE, ageInTicks);
        animate(boss.summonAnimation, BarrowChieftainModelData.SUMMON, ageInTicks);
        animate(boss.rainAnimation, BarrowChieftainModelData.RAIN, ageInTicks);
        animate(boss.stunAnimation, BarrowChieftainModelData.STUN, ageInTicks);
        animate(boss.roarAnimation, BarrowChieftainModelData.ROAR, ageInTicks);
        if (boss.phase() == 2 && state != BarrowChieftain.TRANSFORM) body.y += RIDE;
        wheelL.xRot -= turn;
        wheelR.xRot -= turn;
    }
}
