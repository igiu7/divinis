package com.divinis.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.divinis.registry.FantasyArmor;
import com.divinis.Divinis;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The capes of the fantasy armour sets (the same cape in every metal): worn with the chestplate, a real cape on the player's back that swings
 * as a player's own cape does (the same reckoning as vanilla's CapeLayer - it trails when running, flaps with each
 * step, lifts when falling, and when crouching), a little further back so it clears the armour. Each set its own
 * length and texture (textures/models/armor/blacksteel_<set>_cape.png, 32x32, from build_blacksteel_armor.py).
 */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ArmorCapeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** set -> cape length in pixels */
    private static final Map<String, Integer> CAPES = Map.of("vanguard", 20, "paladin", 20, "dragonknight", 18, "demonlord", 21, "ranger", 11);

    private static ModelLayerLocation layer(String set) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "blacksteel_" + set + "_cape"), "main");
    }

    @SubscribeEvent
    public static void onLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        CAPES.forEach((set, length) -> event.registerLayerDefinition(layer(set), () -> {
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("cape", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, -1.0F, 10, length, 1),
                    PartPose.ZERO);
            return LayerDefinition.create(mesh, 32, 32);
        }));
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        Map<String, ModelPart> capes = new HashMap<>();
        CAPES.keySet().forEach(set -> capes.put(set, event.getEntityModels().bakeLayer(layer(set)).getChild("cape")));
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new ArmorCapeLayer(renderer, capes));
        }
    }

    private final Map<String, ModelPart> capes;

    public ArmorCapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, Map<String, ModelPart> capes) {
        super(parent);
        this.capes = capes;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof FantasyArmor.Piece piece)) return;
        ModelPart cape = capes.get(piece.set);
        if (cape == null) return;
        pose.pushPose();
        pose.translate(0.0F, player.isCrouching() ? 0.12F : 0.0F, player.isCrouching() ? 0.21F : 0.19F); // behind the armour
        double dx = Mth.lerp(partialTicks, player.xCloakO, player.xCloak) - Mth.lerp(partialTicks, player.xo, player.getX());
        double dy = Mth.lerp(partialTicks, player.yCloakO, player.yCloak) - Mth.lerp(partialTicks, player.yo, player.getY());
        double dz = Mth.lerp(partialTicks, player.zCloakO, player.zCloak) - Mth.lerp(partialTicks, player.zo, player.getZ());
        float bodyYaw = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot);
        double sin = Mth.sin(bodyYaw * Mth.DEG_TO_RAD), cos = -Mth.cos(bodyYaw * Mth.DEG_TO_RAD);
        float lift = Mth.clamp((float) dy * 10.0F, -6.0F, 32.0F);
        float back = Mth.clamp((float) (dx * sin + dz * cos) * 100.0F, 0.0F, 150.0F);
        float sideways = Mth.clamp((float) (dx * cos - dz * sin) * 100.0F, -20.0F, 20.0F);
        float bob = Mth.lerp(partialTicks, player.oBob, player.bob);
        lift += Mth.sin(Mth.lerp(partialTicks, player.walkDistO, player.walkDist) * 6.0F) * 32.0F * bob;
        if (player.isCrouching()) lift += 25.0F;
        pose.mulPose(Axis.XP.rotationDegrees(6.0F + back / 2.0F + lift));
        pose.mulPose(Axis.ZP.rotationDegrees(sideways / 2.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - sideways / 2.0F));
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/models/armor/blacksteel_" + piece.set + "_cape.png");
        cape.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
        if (chest.hasFoil()) cape.render(pose, buffers.getBuffer(RenderType.armorEntityGlint()), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
