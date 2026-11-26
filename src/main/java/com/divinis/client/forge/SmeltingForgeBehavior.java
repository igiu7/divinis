package com.divinis.client.forge;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import com.divinis.forge.AnvilForge;
import com.divinis.forge.SmeltingForge;
import com.divinis.forge.SmeltingForgeBlock;
import com.divinis.Divinis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * The Smelting Forge on this client (forge.SmeltingForge). The bellows beside it is drawn here as it is worked: pressing
 * on it and moving the mouse down squeezes it, moving back up opens it - each squeeze and opening is a stroke, sent to
 * the server (SmeltingForge.PUMP), which fans the fire: five from out to a smoulder, three more to a full fire. A
 * caption says how the fire stands, how many strokes it still wants and how long before it sinks. The ingots on the hearth redden as they heat
 * (their heat, from the station's data); the inventory below shows what the forge takes - ingots and coal. The shelf,
 * the firebox and the bellows show only on a formed forge.
 */
public final class SmeltingForgeBehavior implements StationBehavior {
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "block/smelting_forge_atlas");
    /** Tiles of the forge's atlas (tools/models/build_smelting_forge.py): x, y, w, h in its 64 px. */
    private static final int[] LEATHER = {0, 32, 16, 16}, WOOD = {16, 32, 16, 16}, IRON = {16, 48, 16, 16};
    /** How far (scene pixels) the mouse moves down to squeeze the bellows shut. */
    private static final float SQUEEZE = 5.0F;

    /** How squeezed the bellows is, 0 open .. 1 shut, and where the stroke began. */
    private float squeeze;
    private boolean working, shut;
    private float startY;
    private String caption = "";

    public static void register() {
        DiegesisClient.registerBehavior(SmeltingForge.ID, view -> new SmeltingForgeBehavior());
    }

    @Override
    public void onSceneReady(StationView view) {
        view.node("bellows").ifPresent(n -> n.setPainter(new Bellows()));
        tint(view);
    }

    @Override
    public void onOpen(StationView view) {
        view.setInventoryFilter(stack -> AnvilForge.metalOf(stack) != null || SmeltingForge.fuelTicks(stack) > 0);
        caption = "";
        caption(view);
    }

    @Override
    public void onClose(StationView view) {
        working = false;
        shut = false;
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        tint(view);
        caption(view);
    }

    /** The shelf, the firebox and the bellows are seen only on a formed forge; the bellows opens again when let go. */
    @Override
    public void frame(StationView view, float dt) {
        var state = view.level().getBlockState(view.pos());
        boolean formed = state.hasProperty(SmeltingForgeBlock.FORMED) && state.getValue(SmeltingForgeBlock.FORMED);
        for (String id : new String[] {"ingot_0", "ingot_1", "ingot_2", "ingot_3", "ingot_4", "ingot_5", "fuel", "bellows"}) {
            view.node(id).ifPresent(n -> {
                if (n.isVisible() != formed) n.setVisible(formed);
            });
        }
        if (!working) squeeze = Math.max(0, squeeze - dt * 3.5F);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (!(event instanceof SceneEvent.Stroke stroke) || !stroke.node().equals("bellows") || stroke.button() != 0) return;
        switch (stroke.phase()) {
            case START -> {
                working = true;
                startY = stroke.point().y() + squeeze * SQUEEZE;
            }
            case MOVE -> {
                squeeze = Mth.clamp((startY - stroke.point().y()) / SQUEEZE, 0, 1);
                if (squeeze >= 0.8F && !shut) {
                    shut = true;
                    puff(view);
                } else if (squeeze <= 0.2F && shut) {
                    shut = false;
                    stroke(view);
                }
            }
            case END -> {
                working = false;
                // let go squeezed: it opens by itself, and that ends the stroke
                if (shut) {
                    shut = false;
                    stroke(view);
                }
            }
            default -> { }
        }
    }

    /** The air blown out of the nozzle into the tuyere, and the fire answering it. */
    private void puff(StationView view) {
        view.spawnParticles(DiegesisParticles.SMOKE, new Vector3f(-16.5F, 5.0F, 8), new Vector3f(10, 1, 0), 0.6F, 5, 0xFFB0B0B0);
        view.playSound(SoundEvents.WOOL_PLACE, "bellows", 0.7F, 0.6F);
        if (view.state().data().getInt("fire") > SmeltingForge.OUT) {
            view.spawnParticles(DiegesisParticles.SPARK, new Vector3f(8, 6, 7), new Vector3f(0, 8, 0), 3F, 10, 0xFFFFA040);
        }
    }

    /** A whole stroke (squeezed and opened): the server counts it. */
    private void stroke(StationView view) {
        view.sendAction(SmeltingForge.PUMP, new CompoundTag());
    }

    /** How the fire stands, and what it wants, for the player at the forge. */
    private void caption(StationView view) {
        if (!view.isFocused()) return;
        CompoundTag data = view.state().data();
        int fire = data.getInt("fire"), pumps = data.getInt("pumps");
        boolean fuel = data.getInt("burn") > 0 || SmeltingForge.fuelTicks(view.state().getItem(SlotRef.of(SmeltingForge.FUEL))) > 0;
        int left = Math.max(0, SmeltingForge.FADE_TICKS - data.getInt("since")) / 20;
        String fades = " - it sinks in " + left / 60 + ":" + String.format("%02d", left % 60) + " unless the bellows is worked";
        String text;
        if (fire == SmeltingForge.FULL) {
            text = "The fire burns: the ingots on the hearth are heating" + fades;
        } else if (!fuel) {
            text = "Lay coal in the firebox, then work the bellows (drag it down and up)";
        } else if (fire == SmeltingForge.SMOULDER) {
            text = "Embers: work the bellows to fan the fire (" + pumps + "/" + SmeltingForge.STROKES[1] + ")" + fades;
        } else {
            text = "The fire is out: work the bellows to light it (" + pumps + "/" + SmeltingForge.STROKES[0] + ")";
        }
        if (!text.equals(caption)) {
            caption = text;
            view.showCaption(Component.literal(text), 0);
        }
    }

    private static void tint(StationView view) {
        int[] heat = view.state().data().getIntArray("heat");
        for (int i = 0; i < SmeltingForge.SHELF; i++) {
            float t = i < heat.length ? Mth.clamp(heat[i] / (float) SmeltingForge.HEAT_TICKS, 0, 1) : 0;
            // from the metal's own colour to a dull red glow
            int g = (int) Mth.lerp(t, 255, 150), b = (int) Mth.lerp(t, 255, 100);
            int argb = 0xFFFF0000 | g << 8 | b;
            view.node("ingot_" + i).ifPresent(n -> n.setTint(argb));
        }
    }

    /**
     * The bellows, in its node's pixels (standing on the ground, its long side along x, the nozzle at +x in the forge's
     * tuyere): a bottom board on four feet, the leather between it and the top board, which comes down as it is
     * squeezed, the handle on the top board's far end.
     */
    private final class Bellows implements NodePainter {
        @Override
        public void paint(PoseStack pose, MultiBufferSource buffers, int light) {
            AnvilBehavior.Boxes b = new AnvilBehavior.Boxes(pose, buffers);
            TextureAtlasSprite atlas = AnvilBehavior.Boxes.sprite(ATLAS);
            int[][] wood = {WOOD, WOOD, WOOD, WOOD, WOOD, WOOD};
            int[][] leather = {LEATHER, LEATHER, LEATHER, LEATHER, LEATHER, LEATHER};
            int[][] iron = {IRON, IRON, IRON, IRON, IRON, IRON};
            float top = Mth.lerp(squeeze, 7.0F, 3.0F);
            for (float[] f : new float[][] {{-5, -5.5F}, {-5, 4.5F}, {4, -5.5F}, {4, 4.5F}}) {
                b.box(f[0], 0, f[1], f[0] + 1, 1, f[1] + 1, atlas, 64, wood, false, light);
            }
            b.box(-5.5F, 1, -6, 5.5F, 2, 6, atlas, 64, wood, true, light);                 // the bottom board
            b.box(-5, 2, -5.5F, 4.5F, top, 5.5F, atlas, 64, leather, false, light);        // the leather
            b.box(-5.5F, top, -6, 5.5F, top + 1, 6, atlas, 64, wood, true, light);         // the top board
            b.box(-9.5F, top + 0.2F, -1, -5.5F, top + 0.8F, 1, atlas, 64, wood, true, light); // the handle
            b.box(-10.5F, top - 0.5F, -1.5F, -9.5F, top + 1.5F, 1.5F, atlas, 64, wood, false, light);
            b.box(4.5F, 3.9F, -1.2F, 7.6F, 5.7F, 1.2F, atlas, 64, iron, true, light);       // the nozzle, into the tuyere
        }
    }
}
