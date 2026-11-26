package com.divinis.client;

import com.divinis.npc.GuildMaster;
import com.divinis.Divinis;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class GuildMasterRenderer extends MobRenderer<GuildMaster, GuildMasterModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/guild_master.png");

    public GuildMasterRenderer(EntityRendererProvider.Context context) {
        super(context, new GuildMasterModel(context.bakeLayer(GuildMasterModel.LAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(GuildMaster npc) {
        return TEXTURE;
    }
}
