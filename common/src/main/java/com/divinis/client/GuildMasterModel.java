package com.divinis.client;

import com.divinis.npc.GuildMaster;
import com.divinis.Divinis;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Guild Master dwarf. Geometry and the idle / greet animations come from Blender (GuildMasterModelData,
 * tools/models); here the head also follows where he looks.
 */
public class GuildMasterModel extends HierarchicalModel<GuildMaster> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "guild_master"), "main");

    private final ModelPart root;
    private final ModelPart head;

    public GuildMasterModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("body").getChild("head");
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(GuildMaster npc, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        animate(npc.idleAnimation, GuildMasterModelData.IDLE, ageInTicks);
        animate(npc.greetAnimation, GuildMasterModelData.GREET, ageInTicks);
    }
}
