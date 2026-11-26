package com.divinis.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.divinis.mixin.CameraAccessor;
import com.divinis.net.SkillPayloads;
import com.divinis.skill.Dash;
import com.divinis.skill.SkillTree;
import com.divinis.Divinis;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;
import com.divinis.platform.Dist;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.client.ClientPlayerNetworkEvent;
import com.divinis.platform.event.client.ClientTickEvent;
import com.divinis.platform.event.client.RenderHandEvent;
import com.divinis.platform.event.client.ViewportEvent;
import com.divinis.platform.event.PlayerTickEvent;
import com.divinis.platform.net.PacketDistributor;

/**
 * The Dash skill on the client (skill.Dash). The key starts it at once when the client knows the skill is unlocked
 * (the server sends the points), else when the server answers; the server shows it to everyone around.
 * - the roll: DashAnimData, baked from tools/models/dash.blend - the whole body turns (LivingEntityRendererMixin),
 *   the limbs move (HumanoidModelMixin), blended in and out of the vanilla pose;
 * - first person: the camera follows the rig's eye (down to the ground, a full turn forward); the hand goes
 *   down and forward as the hands go to the ground, then drops out of sight below the screen until the player is up (only
 *   ever down or forward, so the open end of the arm never shows);
 * - the dasher's client moves them along the facing they had when they pressed the key (SPEED, blocks a tick).
 */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class DashClient {
    private static final float[] SPEED = {0.42F, 0.62F, 0.68F, 0.64F, 0.58F, 0.52F, 0.46F, 0.4F, 0.34F, 0.26F};
    private static final float BLEND = 1.5F;      // ticks from the vanilla pose into the roll, and back
    private static final float MODEL_SCALE = 0.9375F; // the player renderer draws the model this big

    /** Dashing players: entity id -> their tickCount when the dash began. */
    private static final Map<Integer, Integer> STARTS = new HashMap<>();
    private static List<Integer> points;
    private static int ownStart = -1000;  // the own player's tickCount when they began (for the movement)
    private static long clock, lastDash = -1000; // client ticks (the cooldown; tickCount starts again on respawn)
    private static float dirX, dirZ;

    private DashClient() {}

    // ------------------------------------------------------------------ starting

    public static void receivePoints(List<Integer> list, String book) {
        points = list;
        com.divinis.skill.Perks.CLIENT_POINTS = list.stream().mapToInt(Integer::intValue).toArray();
        com.divinis.skill.Perks.CLIENT_BOOK = book;
    }

    public static void receiveDash(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(id) instanceof Player p)) return;
        if (p == mc.player) {
            if (clock - lastDash < 10) return; // already started when the key was pressed
            startOwn(mc.player);
        } else {
            STARTS.put(id, p.tickCount);
        }
    }

    private static boolean unlocked() {
        int dex = SkillTree.DEX.ordinal();
        return points != null && points.size() > dex && points.get(dex) >= Dash.ORB_POINTS;
    }

    private static void press(LocalPlayer player) {
        if (STARTS.containsKey(player.getId())) return;
        long wait = Dash.COOLDOWN - (clock - lastDash);
        if (wait > 0) {
            player.displayClientMessage(Component.literal(String.format("Dash ready in %.1f s", wait / 20.0F))
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (points != null && !unlocked()) {
            player.displayClientMessage(Component.literal("You have not unlocked Dash (" + Dash.ORB_POINTS
                    + " points in Dexterity).").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!player.onGround() || player.isPassenger() || player.isFallFlying() || player.isSwimming()
                || player.isSpectator() || player.getAbilities().flying) return;
        if (points != null) startOwn(player);
        PacketDistributor.sendToServer(new SkillPayloads.Use());
    }

    private static void startOwn(LocalPlayer player) {
        ownStart = player.tickCount;
        lastDash = clock;
        STARTS.put(player.getId(), player.tickCount);
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        dirX = -Mth.sin(yaw);
        dirZ = Mth.cos(yaw);
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        clock++;
        if (mc.player == null || mc.level == null) {
            STARTS.clear();
            return;
        }
        while (ClientModEvents.DASH.consumeClick()) press(mc.player);
        while (ClientModEvents.SKILLS.consumeClick()) {
            if (mc.screen == null) new com.divinis.client.gui.SkillTreeScreen().openGui();
        }
        STARTS.entrySet().removeIf(e -> {
            Entity entity = mc.level.getEntity(e.getKey());
            if (entity == null) return true;
            int k = entity.tickCount - e.getValue();
            if (k == 3 || k == 9) step(entity); // the hands on the ground, then the feet
            return k > DashAnimData.LENGTH + 1;
        });
    }

    private static void step(Entity entity) {
        BlockPos below = entity.getOnPos();
        SoundType sound = entity.level().getBlockState(below).getSoundType();
        entity.level().playLocalSound(entity.getX(), entity.getY(), entity.getZ(), sound.getStepSound(), SoundSource.PLAYERS,
                sound.getVolume() * 0.7F, sound.getPitch() * 0.85F, false);
    }

    /** The dasher's own movement, before the player moves this tick. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        Integer start = STARTS.get(player.getId());
        if (start == null || start != ownStart) return;
        int k = player.tickCount - start - 1;
        if (k < 0 || k >= SPEED.length) return;
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(dirX * SPEED[k], v.y, dirZ * SPEED[k]);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        STARTS.clear();
        points = null;
        com.divinis.skill.Perks.CLIENT_POINTS = new int[0];
        com.divinis.skill.Perks.CLIENT_BOOK = "";
        lastDash = clock - Dash.COOLDOWN;
    }

    // ------------------------------------------------------------------ the animation

    /** Ticks into the entity's dash, or -1 when it is not dashing. */
    private static float time(Entity entity, float partialTick) {
        Integer start = STARTS.get(entity.getId());
        if (start == null) return -1;
        float t = entity.tickCount + partialTick - start;
        return t >= 0 && t <= DashAnimData.LENGTH ? t : -1;
    }

    private static float[] sample(float[][] frames, float t) {
        float i = t / DashAnimData.STEP;
        int a = Mth.clamp(Mth.floor(i), 0, frames.length - 1), b = Math.min(a + 1, frames.length - 1);
        float f = Mth.clamp(i - a, 0, 1);
        float[] out = new float[frames[a].length];
        for (int c = 0; c < out.length; c++) out[c] = Mth.lerp(f, frames[a][c], frames[b][c]);
        return out;
    }

    /** The limbs, at the end of HumanoidModel.setupAnim (age = tickCount + partial tick). */
    public static void pose(HumanoidModel<?> model, LivingEntity entity, float age) {
        if (!(entity instanceof Player) || STARTS.isEmpty()) return;
        Integer start = STARTS.get(entity.getId());
        if (start == null) return;
        float t = age - start;
        if (t < 0 || t > DashAnimData.LENGTH) return;
        float w = Math.min(1, Math.min(t, DashAnimData.LENGTH - t) / BLEND);
        part(model.head, DashAnimData.HEAD, t, w);
        part(model.body, DashAnimData.BODY, t, w);
        part(model.rightArm, DashAnimData.RIGHT_ARM, t, w);
        part(model.leftArm, DashAnimData.LEFT_ARM, t, w);
        part(model.rightLeg, DashAnimData.RIGHT_LEG, t, w);
        part(model.leftLeg, DashAnimData.LEFT_LEG, t, w);
        model.hat.copyFrom(model.head);
    }

    private static void part(ModelPart part, float[][] frames, float t, float w) {
        float[] r = sample(frames, t);
        part.xRot = Mth.lerp(w, part.xRot, r[0]);
        part.yRot = Mth.lerp(w, part.yRot, r[1]);
        part.zRot = Mth.lerp(w, part.zRot, r[2]);
    }

    /**
     * The whole body turning round the rig's pivot, after the renderer turned the pose to the body's facing (the
     * model is drawn upside down, x mirrored, 1.501 blocks down and MODEL_SCALE big from here).
     */
    public static void roll(LivingEntity entity, PoseStack pose, float partialTick) {
        if (!(entity instanceof Player) || STARTS.isEmpty()) return;
        float t = time(entity, partialTick);
        if (t < 0) return;
        float[] w = sample(DashAnimData.WHOLE, t);
        float s = MODEL_SCALE / 16;
        float px = -DashAnimData.PIVOT[0] * s, py = (1.501F * 16 - DashAnimData.PIVOT[1]) * s, pz = DashAnimData.PIVOT[2] * s;
        pose.translate(px - w[3] * s, py - w[4] * s, pz + w[5] * s);
        pose.mulPose(new Quaternionf().rotationZYX(w[2], -w[1], -w[0]));
        pose.translate(-px, -py, -pz);
    }

    /** First person: the camera goes where the rig's eye goes, and turns with it. */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Camera camera = event.getCamera();
        if (STARTS.isEmpty() || camera.isDetached() || camera.getEntity() != Minecraft.getInstance().player) return;
        float t = time(camera.getEntity(), (float) event.getPartialTick());
        if (t < 0) return;
        float[] eye = sample(DashAnimData.EYE, t);
        float yaw = camera.getYRot() * Mth.DEG_TO_RAD;
        Vec3 move = new Vec3(-Mth.sin(yaw) * eye[0], eye[1], Mth.cos(yaw) * eye[0]);
        ((CameraAccessor) camera).divinis$setPosition(camera.getPosition().add(move));
        event.setPitch(event.getPitch() + eye[2]);
        event.setRoll(event.getRoll() + eye[3]);
    }

    /** First person: the hand goes down and a little forward (ticks 0-2), drops below the screen (2-3.5), comes back (8-10.5). */
    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (STARTS.isEmpty() || mc.player == null) return;
        float t = time(mc.player, event.getPartialTick());
        if (t < 0) return;
        float reach = Mth.sin(Mth.clamp(t / 2.5F, 0, 1) * Mth.PI);
        float drop = ease(Mth.clamp((t - 2) / 1.5F, 0, 1)) * (1 - ease(Mth.clamp((t - 8) / 2.5F, 0, 1)));
        event.getPoseStack().translate(0, -0.25F * reach - 1.1F * drop, -0.08F * reach);
    }

    private static float ease(float x) {
        return x * x * (3 - 2 * x);
    }
}
