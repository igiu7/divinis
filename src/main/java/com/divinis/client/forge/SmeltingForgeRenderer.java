package com.divinis.client.forge;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.divinis.forge.SmeltingForgeBlock;
import com.divinis.forge.SmeltingForgeBlockEntity;
import com.divinis.Divinis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a formed Smelting Forge: the whole furnace (tools/models/build_smelting_forge.py), its model cut into one model
 * per block cell (divinis/smelting_forge_parts.json), each drawn at its cell, the whole turned to the forge's facing;
 * the models of its fire (out, smoulder, full). The blocks it is built of are hidden meanwhile.
 */
public final class SmeltingForgeRenderer implements BlockEntityRenderer<SmeltingForgeBlockEntity> {
    /** A cell's models: where it lies (blocks from the controller, the forge facing north), cold and burning. */
    private record Part(int x, int y, int z, ModelResourceLocation[] fires) {}

    private static final List<Part> PARTS = load();

    private static List<Part> load() {
        List<Part> out = new ArrayList<>();
        try (InputStream in = SmeltingForgeRenderer.class.getResourceAsStream("/divinis/smelting_forge_parts.json")) {
            JsonObject parts = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("parts");
            for (var e : parts.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                var cell = o.getAsJsonArray("cell");
                out.add(new Part(cell.get(0).getAsInt(), cell.get(1).getAsInt(), cell.get(2).getAsInt(), new ModelResourceLocation[] {
                        o.get("cold").getAsBoolean() ? model(e.getKey()) : null,
                        o.get("smoulder").getAsBoolean() ? model(e.getKey() + "_smoulder") : null,
                        o.get("lit").getAsBoolean() ? model(e.getKey() + "_lit") : null}));
            }
        } catch (Exception ex) {
            Divinis.LOGGER.error("Could not read the smelting forge's parts", ex);
        }
        return out;
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "block/smelting_forge/" + name));
    }

    /** Every model to load (ModelEvent.RegisterAdditional). */
    public static List<ModelResourceLocation> models() {
        List<ModelResourceLocation> out = new ArrayList<>();
        for (Part p : PARTS) {
            for (ModelResourceLocation m : p.fires()) if (m != null) out.add(m);
        }
        return out;
    }

    @Override
    public void render(SmeltingForgeBlockEntity forge, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = forge.getBlockState();
        if (!state.hasProperty(SmeltingForgeBlock.FORMED) || !state.getValue(SmeltingForgeBlock.FORMED) || forge.getLevel() == null) return;
        int fire = state.getValue(SmeltingForgeBlock.FIRE);
        Direction facing = state.getValue(SmeltingForgeBlock.FACING);
        // lit as the air before its mouth is lit (the controller itself is inside the furnace)
        int shade = LevelRenderer.getLightColor(forge.getLevel(), forge.getBlockPos().relative(facing));
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        var models = Minecraft.getInstance().getModelManager();
        var buffer = buffers.getBuffer(RenderType.cutout());
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facingAngle(facing)));
        pose.translate(-0.5, 0, -0.5);
        for (Part p : PARTS) {
            ModelResourceLocation id = p.fires()[fire];
            if (id == null) continue;
            BakedModel model = models.getModel(id);
            pose.pushPose();
            pose.translate(p.x(), p.y(), p.z());
            dispatcher.getModelRenderer().renderModel(pose.last(), buffer, null, model, 1.0F, 1.0F, 1.0F, shade,
                    OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
            pose.popPose();
        }
        pose.popPose();
    }

    /** The blockstate's turn for each facing (the model faces north), clockwise seen from above. */
    private static float facingAngle(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    @Override
    public AABB getRenderBoundingBox(SmeltingForgeBlockEntity forge) {
        return forge.renderBox();
    }

    @Override
    public boolean shouldRenderOffScreen(SmeltingForgeBlockEntity forge) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
