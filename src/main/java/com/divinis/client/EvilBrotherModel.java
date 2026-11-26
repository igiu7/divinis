package com.divinis.client;

import com.divinis.boss.EvilBrother;
import com.divinis.Divinis;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

/**
 * The Evil Brother's steam mech, one phase of it (EvilBrotherModelData for phase 1, EvilBrotherP2ModelData for phase
 * 2: the same parts and animations, from tools/models/build_evil_brother.py). It walks while it moves without
 * attacking; everything else comes from the boss's animation states.
 */
public class EvilBrotherModel extends HierarchicalModel<EvilBrother> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "evil_brother"), "main");
    public static final ModelLayerLocation LAYER_P2 =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "evil_brother_p2"), "main");

    /** One phase's animations. */
    public record Anims(AnimationDefinition idle, AnimationDefinition walk, AnimationDefinition pound, AnimationDefinition harpoon,
                        AnimationDefinition gears, AnimationDefinition transform, AnimationDefinition geysers,
                        AnimationDefinition sweep, AnimationDefinition rush, AnimationDefinition death) {}

    public static final Anims P1 = new Anims(EvilBrotherModelData.IDLE, EvilBrotherModelData.WALK, EvilBrotherModelData.POUND,
            EvilBrotherModelData.HARPOON, EvilBrotherModelData.GEARS, EvilBrotherModelData.TRANSFORM, EvilBrotherModelData.GEYSERS,
            EvilBrotherModelData.SWEEP, EvilBrotherModelData.RUSH, EvilBrotherModelData.DEATH);
    public static final Anims P2 = new Anims(EvilBrotherP2ModelData.IDLE, EvilBrotherP2ModelData.WALK, EvilBrotherP2ModelData.POUND,
            EvilBrotherP2ModelData.HARPOON, EvilBrotherP2ModelData.GEARS, EvilBrotherP2ModelData.TRANSFORM, EvilBrotherP2ModelData.GEYSERS,
            EvilBrotherP2ModelData.SWEEP, EvilBrotherP2ModelData.RUSH, EvilBrotherP2ModelData.DEATH);

    private final ModelPart root;
    private final Anims anims;
    /** The parts down to the cockpit's seat, where the dwarf sits. */
    final ModelPart body, hull, seat, leverRight, leverLeft;

    public EvilBrotherModel(ModelPart root, Anims anims) {
        this.root = root;
        this.anims = anims;
        this.body = root.getChild("body");
        this.hull = body.getChild("hull");
        this.seat = hull.getChild("seat");
        this.leverRight = hull.getChild("lever_r");
        this.leverLeft = hull.getChild("lever_l");
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(EvilBrother boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        if (boss.isDeadOrDying()) {
            animate(boss.deathAnimation, anims.death(), ageInTicks);
            return;
        }
        if (!boss.isAttacking()) animateWalk(anims.walk(), limbSwing, limbSwingAmount, 1.5F, 2.0F);
        animate(boss.idleAnimation, anims.idle(), ageInTicks);
        animate(boss.poundAnimation, anims.pound(), ageInTicks);
        animate(boss.harpoonAnimation, anims.harpoon(), ageInTicks);
        animate(boss.gearsAnimation, anims.gears(), ageInTicks);
        animate(boss.transformAnimation, anims.transform(), ageInTicks);
        animate(boss.geysersAnimation, anims.geysers(), ageInTicks);
        animate(boss.sweepAnimation, anims.sweep(), ageInTicks);
        animate(boss.rushAnimation, anims.rush(), ageInTicks);
    }
}
