package com.divinis.client;

import com.divinis.dungeon.DungeonPortal;
import com.divinis.Divinis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

/** The dungeon portal; ring, base and swirl animation come from Blender (DungeonPortalModelData, tools/models). */
public class DungeonPortalModel extends HierarchicalModel<DungeonPortal> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "dungeon_portal"), "main");

    private final ModelPart root;

    public DungeonPortalModel(ModelPart root) {
        this.root = root;
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(DungeonPortal portal, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        animate(portal.swirlAnimation, DungeonPortalModelData.SWIRL, ageInTicks);
    }
}
