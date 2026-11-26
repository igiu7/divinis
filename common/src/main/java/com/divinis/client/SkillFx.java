package com.divinis.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.divinis.net.SkillFxPayload;
import com.divinis.Divinis;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.divinis.platform.Dist;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.client.ClientPlayerNetworkEvent;
import com.divinis.platform.event.client.ClientTickEvent;
import com.divinis.platform.event.client.RenderLevelStageEvent;

/**
 * What the book skills show only to their user (skill.SkillEffects, SkillFxPayload):
 * - Echo of Stone: the ores' outlines through the stone, each in its ore's colour, fading in and out;
 * - Track: the animals glowing (MinecraftGlowMixin asks glowing);
 * - Treasure Sense: a golden arrow (tools/models/build_treasure_arrow.py) floating before the player and turning to
 *   the chest, its distance in the action bar.
 */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class SkillFx {
    public static final ResourceLocation ARROW = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "block/skill/treasure_arrow");
    public static final ResourceLocation CAMP = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "block/skill/camp");

    /** Lines drawn over everything (no depth test), so the ores show through the stone. */
    private static final RenderType XRAY_LINES = RenderType.create("divinis_xray_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES, 4096, false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.5)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    private record Ore(BlockPos pos, int color) {}

    private static final List<Ore> ORES = new ArrayList<>();
    private static long oresFrom, oresUntil;
    private static final Map<Integer, Long> GLOWING = new HashMap<>();
    private static BlockPos target;
    private static long targetUntil;

    private SkillFx() {}

    private static long now() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    public static void receive(SkillFxPayload payload) {
        long now = now();
        switch (payload.kind()) {
            case SkillFxPayload.ORES -> {
                ORES.clear();
                for (int i = 0; i < payload.blocks().size(); i++) {
                    ORES.add(new Ore(payload.blocks().get(i), i < payload.ints().size() ? payload.ints().get(i) : 0xFFFFFF));
                }
                oresFrom = now;
                oresUntil = now + payload.ticks();
            }
            case SkillFxPayload.GLOW -> {
                for (int id : payload.ints()) GLOWING.put(id, now + payload.ticks());
            }
            case SkillFxPayload.POINTER -> {
                target = payload.blocks().isEmpty() ? null : payload.blocks().get(0);
                targetUntil = now + payload.ticks();
            }
            default -> {
            }
        }
    }

    /** MinecraftGlowMixin: Track's animals glow for their tracker. */
    public static boolean glowing(Entity entity) {
        if (GLOWING.isEmpty()) return false;
        Long until = GLOWING.get(entity.getId());
        return until != null && until > now();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ORES.clear();
        GLOWING.clear();
        target = null;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = now();
        GLOWING.values().removeIf(until -> until <= now);
        if (target != null) {
            if (now >= targetUntil) {
                target = null;
            } else if (now % 10 == 0) {
                int metres = (int) Math.sqrt(mc.player.blockPosition().distSqr(target));
                mc.player.displayClientMessage(Component.literal("Treasure: " + metres + " m").withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || (ORES.isEmpty() && target == null)) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = mc.level.getGameTime() + partial;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        if (!ORES.isEmpty()) {
            if (now > oresUntil) {
                ORES.clear();
            } else {
                float alpha = Math.min(1, (now - oresFrom) / 6) * Math.min(1, (oresUntil - now) / 30);
                VertexConsumer lines = buffers.getBuffer(XRAY_LINES);
                pose.pushPose();
                pose.translate(-cam.x, -cam.y, -cam.z);
                for (Ore ore : ORES) {
                    int c = ore.color() == 0 ? 0xFFFFFF : ore.color();
                    float r = (c >> 16 & 255) / 255F, g = (c >> 8 & 255) / 255F, b = (c & 255) / 255F;
                    // brighter than the stone round it: the ore's colour lifted towards white
                    LevelRenderer.renderLineBox(pose, lines, new AABB(ore.pos()).inflate(0.004),
                            0.35F + 0.65F * r, 0.35F + 0.65F * g, 0.35F + 0.65F * b, alpha * 0.9F);
                }
                pose.popPose();
                buffers.endBatch(XRAY_LINES);
            }
        }
        if (target != null) pointer(mc, pose, buffers, cam, now, partial);
    }

    /** The golden arrow, 1.6 blocks before the eyes and a little low, pointing at the chest, bobbing gently. */
    private static void pointer(Minecraft mc, PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, float now, float partial) {
        Vec3 eye = mc.player.getEyePosition(partial);
        float yaw = mc.player.getViewYRot(partial) * Mth.DEG_TO_RAD;
        Vec3 ahead = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 at = eye.add(ahead.scale(1.6)).add(0, -0.45 + Mth.sin(now * 0.12F) * 0.04, 0);
        Vec3 to = Vec3.atCenterOf(target).subtract(at);
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        float turn = (float) Math.atan2(-to.x, -to.z);
        float tilt = (float) Math.atan2(to.y, Math.max(flat, 1.0E-4));
        float grow = Math.min(1, (now - (targetUntil - 20 * 20)) / 6) * Math.min(1, (targetUntil - now) / 10);
        pose.pushPose();
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        pose.mulPose(Axis.YP.rotation(turn));
        pose.mulPose(Axis.XP.rotation(tilt));
        float s = 0.6F * Math.max(0, grow);
        pose.scale(s, s, s);
        pose.translate(-0.5, -0.5, -0.5);
        var model = com.divinis.platform.client.ClientServices.extraModel(ARROW);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), null, model,
                1, 1, 1, 0xF000F0, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        buffers.endBatch(Sheets.cutoutBlockSheet());
    }
}
