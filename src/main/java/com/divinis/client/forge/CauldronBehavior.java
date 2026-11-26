package com.divinis.client.forge;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.divinis.forge.CauldronStation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * The cauldron on this client (forge.CauldronStation): the inventory below shows what the cauldron takes, a stack let go
 * over it (or shift-clicked) is used on it by the server, and what the server did is animated for everyone around - a
 * stream poured in and a splash, a dip and droplets as a container is filled, and a quench: the hot piece is lowered
 * into the water, held there in a cloud of steam with bubbles and a hiss, and lifted out cold.
 */
public final class CauldronBehavior implements StationBehavior {
    /** Seconds a pour runs; a quench: lowered, held under, lifted out. */
    private static final float POUR_TIME = 0.55F;
    private static final float DOWN = 0.45F, UNDER = 0.9F, UP = 0.55F;

    private float pouring = -1;
    private int pourColor;
    private float quench = -1;
    private ItemStack hot = ItemStack.EMPTY, cold = ItemStack.EMPTY;
    private float sinceSteam;
    /** The surface last seen, for the painter. */
    private float level = 15;

    public static void register() {
        DiegesisClient.registerBehavior(CauldronStation.ID, view -> new CauldronBehavior());
        for (ResourceLocation id : new ResourceLocation[] {CauldronStation.POUR, CauldronStation.FILL, CauldronStation.QUENCH}) {
            DiegesisClient.registerEffect(id, (view, node, data) -> { });
        }
    }

    @Override
    public void onSceneReady(StationView view) {
        view.node("quench").ifPresent(n -> n.setPainter(new Dipped()));
    }

    @Override
    public void onOpen(StationView view) {
        view.setInventoryFilter(CauldronStation::usable);
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (!"cauldron".equals(node) || stack.isEmpty()) return false;
        view.sendAction(CauldronStation.USE, new CompoundTag());
        return true;
    }

    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (!CauldronStation.usable(stack)) return false;
        CompoundTag data = new CompoundTag();
        data.putInt("from", slot);
        view.sendAction(CauldronStation.USE, data);
        return true;
    }

    /** The surface of what the cauldron holds, scene pixels up (vanilla's heights: 9, 12, 15 for its levels). */
    private static float surface(StationView view) {
        BlockState s = view.level().getBlockState(view.pos());
        if (s.is(Blocks.LAVA_CAULDRON)) return 15;
        if (s.hasProperty(LayeredCauldronBlock.LEVEL)) return 6 + 3 * s.getValue(LayeredCauldronBlock.LEVEL);
        return 4;
    }

    private static int colour(String fluidId) {
        Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(fluidId));
        int tint = IClientFluidTypeExtensions.of(fluid).getTintColor();
        return fluid.isSame(net.minecraft.world.level.material.Fluids.WATER) ? 0xFF3F76E4 : tint == -1 ? 0xFFFF7A1A : tint | 0xFF000000;
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        float top = surface(view);
        if (effect.equals(CauldronStation.POUR)) {
            pouring = 0;
            pourColor = colour(data.getString("fluid"));
        } else if (effect.equals(CauldronStation.FILL)) {
            // a container dipped in: droplets thrown up off the surface
            int c = colour(data.getString("fluid"));
            view.spawnParticles(DiegesisParticles.SPLASH, new Vector3f(8, top, 8), new Vector3f(0, 4, 0), 3.0F, 8, c);
            view.spawnParticles(DiegesisParticles.DROPLET, new Vector3f(8, top + 1, 8), new Vector3f(0, 10, 0), 2.5F, 10, c);
        } else if (effect.equals(CauldronStation.QUENCH)) {
            hot = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.getString("hot"))));
            cold = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.getString("cold"))));
            quench = 0;
            sinceSteam = 1;
        }
    }

    @Override
    public void frame(StationView view, float dt) {
        float top = surface(view);
        level = top;
        if (pouring >= 0) {
            pouring += dt;
            if (pouring < POUR_TIME) {
                // a stream from over the rim on the camera's side down onto the middle
                view.pourStream("pour", new Vector3f(8, 22, 3), new Vector3f(8, top, 8), pourColor, 1.0F, null);
            } else {
                view.spawnParticles(DiegesisParticles.SPLASH, new Vector3f(8, top, 8), new Vector3f(0, 5, 0), 3.0F, 12, pourColor);
                pouring = -1;
            }
        }
        if (quench >= 0) {
            float before = quench;
            quench += dt;
            sinceSteam += dt;
            // touching the water: a burst of steam and the hiss
            if (before < DOWN && quench >= DOWN) {
                view.spawnParticles(DiegesisParticles.STEAM, new Vector3f(8, top + 0.5F, 8), new Vector3f(0, 10, 0), 4.0F, 30, 0xFFFFFFFF);
                view.spawnParticles(DiegesisParticles.SPLASH, new Vector3f(8, top, 8), new Vector3f(0, 4, 0), 2.5F, 10, 0xFF3F76E4);
                view.playSound(SoundEvents.FIRE_EXTINGUISH, "cauldron", 0.6F, 1.3F);
            }
            if (quench >= DOWN && quench < DOWN + UNDER && sinceSteam > 0.08F) {
                sinceSteam = 0;
                view.spawnParticles(DiegesisParticles.STEAM, new Vector3f(8, top + 0.5F, 8), new Vector3f(0, 7, 0), 3.0F, 4, 0xFFFFFFFF);
                view.spawnParticles(DiegesisParticles.BUBBLE, new Vector3f(8, top - 1.5F, 8), new Vector3f(0, 4, 0), 2.0F, 3, 0xFFFFFFFF);
            }
            if (quench >= DOWN + UNDER + UP) quench = -1;
        }
    }

    /** The piece being quenched, held upright over the middle of the cauldron: down into the water, and up again cold. */
    private final class Dipped implements NodePainter {
        @Override
        public void paint(PoseStack pose, MultiBufferSource buffers, int light) {
            if (quench < 0) return;
            float top = level;
            float above = top + 6, under = top - 3.5F;
            float y;
            boolean coldNow;
            if (quench < DOWN) {
                float t = quench / DOWN;
                y = Mth.lerp(t * t, above, under);
                coldNow = false;
            } else if (quench < DOWN + UNDER) {
                y = under + 0.3F * Mth.sin((quench - DOWN) * 18);
                coldNow = quench > DOWN + UNDER * 0.6F;
            } else {
                float t = (quench - DOWN - UNDER) / UP;
                y = Mth.lerp(1 - (1 - t) * (1 - t), under, above + 2);
                coldNow = true;
            }
            ItemStack shown = coldNow ? cold : hot;
            if (shown.isEmpty()) return;
            pose.pushPose();
            pose.translate(8, y, 8);
            pose.mulPose(Axis.YP.rotationDegrees(25));
            pose.scale(9, 9, 9);
            Minecraft.getInstance().getItemRenderer().renderStatic(shown, ItemDisplayContext.FIXED, coldNow ? light : LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, pose, buffers, Minecraft.getInstance().level, 0);
            pose.popPose();
        }
    }
}
