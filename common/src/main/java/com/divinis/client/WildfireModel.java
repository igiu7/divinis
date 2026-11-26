package com.divinis.client;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.divinis.boss.Wildfire;
import com.divinis.Divinis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Wildfire: the helm and its four shields. Geometry and animations come from Blender (WildfireModelData,
 * tools/models/build_wildfire.py); here broken shields are hidden and the helm turns to its target. Also tells the
 * renderer where the helm and the shields are, for the fire drawn around them.
 */
public class WildfireModel extends HierarchicalModel<Wildfire> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "wildfire"), "main");

    private final ModelPart root, floating, head, ring;
    private final ModelPart[] arms = new ModelPart[Wildfire.MAX_SHIELDS];
    private final ModelPart[] shields = new ModelPart[Wildfire.MAX_SHIELDS];

    public WildfireModel(ModelPart root) {
        this.root = root;
        this.floating = root.getChild("float");
        this.head = floating.getChild("head");
        this.ring = floating.getChild("ring");
        for (int i = 0; i < arms.length; i++) {
            arms[i] = ring.getChild("arm" + i);
            shields[i] = arms[i].getChild("shield" + i);
        }
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Wildfire boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        if (boss.isDeadOrDying()) {
            animate(boss.deathAnimation, BossDeaths.WILDFIRE, ageInTicks);
            return;
        }
        int left = boss.shields();
        for (int i = 0; i < shields.length; i++) shields[i].visible = i < left || boss.state() == Wildfire.SLEEPING;
        if (boss.state() == Wildfire.AWAKE && !boss.isAttacking() && !boss.channeling()) {
            head.yRot += Mth.clamp(netHeadYaw, -45, 45) * Mth.DEG_TO_RAD;
            head.xRot += Mth.clamp(headPitch, -30, 30) * Mth.DEG_TO_RAD;
        }
        animate(boss.sleepAnimation, WildfireModelData.SLEEP, ageInTicks);
        animate(boss.wakeAnimation, WildfireModelData.WAKE, ageInTicks);
        animate(boss.idleAnimation, WildfireModelData.IDLE, ageInTicks);
        animate(boss.fireballAnimation, WildfireModelData.FIREBALL, ageInTicks);
        animate(boss.sweepAnimation, WildfireModelData.SWEEP, ageInTicks);
        float turn = boss.sweepTurn(ageInTicks - boss.tickCount);
        if (!Float.isNaN(turn)) ring.yRot += turn * Mth.DEG_TO_RAD; // the shields go round with the burning beams
        animate(boss.infernoAnimation, WildfireModelData.INFERNO, ageInTicks);
        animate(boss.stunAnimation, WildfireModelData.STUN, ageInTicks);
        animate(boss.regenAnimation, WildfireModelData.REGEN, ageInTicks);
    }

    private static Vec3 at(PoseStack pose, ModelPart... chain) {
        pose.pushPose();
        for (ModelPart p : chain) p.translateAndRotate(pose);
        Vector3f v = pose.last().pose().transformPosition(new Vector3f());
        pose.popPose();
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** Where the helm's middle is, after setupAnim, with `pose` set up as the renderer draws the model. */
    public Vec3 headAt(PoseStack pose) {
        return at(pose, root, floating, head);
    }

    public Vec3 shieldAt(PoseStack pose, int i) {
        return at(pose, root, floating, ring, arms[i], shields[i]);
    }

    public boolean shieldShown(int i) {
        return shields[i].visible;
    }
}
