package com.divinis.client;

import com.divinis.boss.Hydra;
import com.divinis.Divinis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

/**
 * The Hydra (HydraRenderer draws it twice its pixels). Geometry and all animations come from Blender (HydraModelData,
 * tools/models/build_hydra.py); here each of its ten neck slots is shown as the boss says - a whole neck with its
 * head, only the stump of a cut one, or nothing - and it wades when it moves between attacks.
 */
public class HydraModel extends HierarchicalModel<Hydra> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "hydra"), "main");

    private final ModelPart root;
    private final ModelPart[] base = new ModelPart[Hydra.SLOTS], upper = new ModelPart[Hydra.SLOTS], stump = new ModelPart[Hydra.SLOTS];

    public HydraModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("body");
        for (int i = 0; i < Hydra.SLOTS; i++) {
            base[i] = body.getChild("nb" + i);
            ModelPart first = base[i].getChild("n" + i + "_0");
            upper[i] = first.getChild("n" + i + "_1");
            stump[i] = first.getChild("st" + i);
        }
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Hydra boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        for (int i = 0; i < Hydra.SLOTS; i++) {
            boolean alive = boss.alive(i), cut = boss.stump(i) && !alive;
            base[i].visible = alive || cut;
            upper[i].visible = alive;
            stump[i].visible = cut;
        }
        if (boss.isDeadOrDying()) {
            animate(boss.deathAnimation, HydraModelData.DEATH, ageInTicks);
            return;
        }
        if (!boss.isAttacking()) animateWalk(HydraModelData.WALK, limbSwing, limbSwingAmount, 1.5F, 2.0F);
        animate(boss.sleepAnimation, HydraModelData.SLEEP, ageInTicks);
        animate(boss.wakeAnimation, HydraModelData.WAKE, ageInTicks);
        animate(boss.idleAnimation, HydraModelData.IDLE, ageInTicks);
        animate(boss.fireAnimation, HydraModelData.FIRE, ageInTicks);
        animate(boss.acidAnimation, HydraModelData.ACID, ageInTicks);
        animate(boss.iceAnimation, HydraModelData.ICE, ageInTicks);
        animate(boss.tailAnimation, HydraModelData.TAIL, ageInTicks);
        animate(boss.diveAnimation, HydraModelData.DIVE, ageInTicks);
        animate(boss.emergeAnimation, HydraModelData.EMERGE, ageInTicks);
        animate(boss.breathsAnimation, HydraModelData.BREATHS, ageInTicks);
        animate(boss.mawAnimation, HydraModelData.MAW, ageInTicks);
        animate(boss.roarAnimation, HydraModelData.ROAR, ageInTicks);
        animate(boss.flinchAnimation, HydraModelData.FLINCH, ageInTicks);
        animate(boss.regrowAnimation[0], HydraModelData.REGROW_0, ageInTicks);
        animate(boss.regrowAnimation[1], HydraModelData.REGROW_1, ageInTicks);
        animate(boss.regrowAnimation[2], HydraModelData.REGROW_2, ageInTicks);
        animate(boss.transformAnimation, HydraModelData.TRANSFORM, ageInTicks);
    }
}
