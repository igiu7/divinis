package com.divinis.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.divinis.registry.FantasyArmor;
import com.divinis.Divinis;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * The fantasy armour sets' models (the same in every metal, painted with the metal's texture) (Blacksteel<Set>ArmorModelData, from Blender): one model per set holding all four
 * pieces, each under the player's part it moves with; for each slot only that piece's parts are shown. In the
 * inventory (and in hand, on the ground, in frames) a piece is drawn as its own 3D model too (models/item/<piece>.json
 * is builtin/entity; PieceRenderer draws it, from a copy of the model kept in its resting pose).
 */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BlacksteelArmorModels {
    private BlacksteelArmorModels() {}

    private static final Map<String, Supplier<LayerDefinition>> LAYERS = Map.of(
            "vanguard", BlacksteelVanguardArmorModelData::createBodyLayer,
            "swordsman", BlacksteelSwordsmanArmorModelData::createBodyLayer,
            "paladin", BlacksteelPaladinArmorModelData::createBodyLayer,
            "spellblade", BlacksteelSpellbladeArmorModelData::createBodyLayer,
            "dragonknight", BlacksteelDragonknightArmorModelData::createBodyLayer,
            "demonlord", BlacksteelDemonlordArmorModelData::createBodyLayer,
            "ranger", BlacksteelRangerArmorModelData::createBodyLayer);

    private static ModelLayerLocation layer(String set) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "blacksteel_" + set + "_armor"), "main");
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        LAYERS.forEach((set, def) -> event.registerLayerDefinition(layer(set), def));
    }

    /** The model of a set, with its pieces' parts found once. */
    private static final class SetModel {
        final HumanoidModel<LivingEntity> model;
        final ModelPart helmet, chest, chestL, chestR, legsB, legsL, legsR, bootsL, bootsR;

        SetModel(ModelPart root) {
            model = new HumanoidModel<>(root);
            helmet = root.getChild("head").getChild("helmet");
            chest = root.getChild("body").getChild("chest");
            legsB = root.getChild("body").getChild("legs_b");
            chestL = root.getChild("left_arm").getChild("chest_l");
            chestR = root.getChild("right_arm").getChild("chest_r");
            legsL = root.getChild("left_leg").getChild("legs_l");
            legsR = root.getChild("right_leg").getChild("legs_r");
            bootsL = root.getChild("left_leg").getChild("boots_l");
            bootsR = root.getChild("right_leg").getChild("boots_r");
        }

        HumanoidModel<LivingEntity> forSlot(EquipmentSlot slot) {
            helmet.visible = slot == EquipmentSlot.HEAD;
            chest.visible = chestL.visible = chestR.visible = slot == EquipmentSlot.CHEST;
            legsB.visible = legsL.visible = legsR.visible = slot == EquipmentSlot.LEGS;
            bootsL.visible = bootsR.visible = slot == EquipmentSlot.FEET;
            return model;
        }
    }

    private static final Map<String, SetModel> WORN = new HashMap<>(), SHOWN = new HashMap<>();
    @Nullable private static PieceRenderer pieceRenderer;

    private static SetModel model(Map<String, SetModel> cache, String set) {
        return cache.computeIfAbsent(set, s -> new SetModel(Minecraft.getInstance().getEntityModels().bakeLayer(layer(s))));
    }

    private static IClientItemExtensions extensions(String set) {
        return new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                return model(WORN, set).forSlot(slot);
            }

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (pieceRenderer == null) pieceRenderer = new PieceRenderer();
                return pieceRenderer;
            }
        };
    }

    /** A piece on its own: its part of the set's model, centred and sized to fill the item's space, facing the viewer. */
    private static final class PieceRenderer extends BlockEntityWithoutLevelRenderer {
        PieceRenderer() {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            if (!(stack.getItem() instanceof FantasyArmor.Piece piece)) return;
            EquipmentSlot slot = piece.getEquipmentSlot();
            HumanoidModel<LivingEntity> model = model(SHOWN, piece.set).forSlot(slot);
            // the middle of the piece (model pixels, y down) and how big to draw it
            float cy, k;
            switch (slot) {
                case HEAD -> { cy = -4.5F; k = 1.05F; }
                case CHEST -> { cy = 6.5F; k = 0.62F; }
                case LEGS -> { cy = 14.5F; k = 0.8F; }
                default -> { cy = 21.7F; k = 1.25F; }
            }
            pose.pushPose();
            pose.translate(0.5F, 0.5F, 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.scale(k, -k, k);
            pose.translate(0, -cy / 16.0F, 0);
            ResourceLocation texture = piece.texture();
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, overlay, -1);
            if (stack.hasFoil()) model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityGlint()), light, overlay, -1);
            pose.popPose();
        }
    }

    @SubscribeEvent
    public static void onExtensions(RegisterClientExtensionsEvent event) {
        Map<String, List<Item>> bySet = new HashMap<>();
        for (var piece : FantasyArmor.PIECES) {
            FantasyArmor.Piece item = (FantasyArmor.Piece) piece.get();
            bySet.computeIfAbsent(item.set, k -> new java.util.ArrayList<>()).add(item);
        }
        bySet.forEach((set, items) -> event.registerItem(extensions(set), items.toArray(new Item[0])));
    }
}
