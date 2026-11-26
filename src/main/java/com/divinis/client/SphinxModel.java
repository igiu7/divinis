package com.divinis.client;

import com.divinis.boss.Sphinx;
import com.divinis.Divinis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

/**
 * The Sphinx (SphinxModelData, from tools/models/build_sphinx.py): he walks when he moves without attacking, sits up
 * while he asks his riddle; everything else comes from his animation states. Its parts down to the head are kept for
 * the glow of his eyes (SphinxRenderer).
 */
public class SphinxModel extends HierarchicalModel<Sphinx> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "sphinx"), "main");

    private final ModelPart root;
    final ModelPart body, neck, head;

    public SphinxModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck = body.getChild("neck");
        this.head = neck.getChild("head");
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Sphinx boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        if (boss.isDeadOrDying()) {
            animate(boss.deathAnimation, SphinxModelData.DEATH, ageInTicks);
            return;
        }
        if (!boss.isAttacking()) {
            animateWalk(SphinxModelData.WALK, limbSwing, limbSwingAmount, 1.6F, 2.5F);
            head.yRot += Math.max(-0.7F, Math.min(0.7F, netHeadYaw * 0.0174533F));
            head.xRot += Math.max(-0.4F, Math.min(0.4F, headPitch * 0.0174533F));
        }
        animate(boss.idleAnimation, SphinxModelData.IDLE, ageInTicks);
        animate(boss.riddleAnimation, SphinxModelData.RIDDLE, ageInTicks);
        animate(boss.summonAnimation, SphinxModelData.SUMMON, ageInTicks);
        animate(boss.pounceAnimation, SphinxModelData.POUNCE, ageInTicks);
        animate(boss.gazeAnimation, SphinxModelData.GAZE, ageInTicks);
        animate(boss.beatAnimation, SphinxModelData.BEAT, ageInTicks);
        animate(boss.galeAnimation, SphinxModelData.GALE, ageInTicks);
        animate(boss.roarAnimation, SphinxModelData.ROAR, ageInTicks);
        animate(boss.judgementAnimation, SphinxModelData.JUDGEMENT, ageInTicks);
        animate(boss.transformAnimation, SphinxModelData.TRANSFORM, ageInTicks);
    }
}
