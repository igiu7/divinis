package com.divinis.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.systems.RenderSystem;
import com.divinis.net.FishingPayloads;
import com.divinis.Divinis;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import com.divinis.platform.Dist;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.client.ClientTickEvent;
import com.divinis.platform.event.client.InputEvent;
import com.divinis.platform.event.client.MovementInputUpdateEvent;
import com.divinis.platform.event.client.RegisterGuiLayersEvent;
import com.divinis.platform.event.client.VanillaGuiLayers;
import com.divinis.platform.net.PacketDistributor;

/**
 * The fishing minigame's wheel (fishing.FishingGame): a needle turns round a dial; the jump key, while it is up
 * (jumping is off), stops it - in a golden zone the fish is caught (some rods give two zones; an Ancient Fish has
 * three, hit in their order, the next one lit). The dial is a 64x64 pixel-art texture
 * (tools/build_fishing_wheel.py) drawn two GUI pixels a texel; the zone and the needle are drawn in the same grid
 * over it. The needle runs on the real clock, so the press is judged where the player saw it.
 */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class FishingHud {
    private static final ResourceLocation WHEEL = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/gui/fishing_wheel.png");
    private static final int N = 64, CELL = 2, SIZE = N * CELL;
    private static final float C = N / 2.0F;
    private static final float TRACK_IN = 21.0F, TRACK_OUT = 27.5F; // as in tools/build_fishing_wheel.py
    private static final float TOLERANCE = 1.5F;                    // degrees forgiven at the zone's edges
    private static final float PERFECT = 2.0F;                      // degrees from a zone's middle that are a perfect hit
    private static final float POP = 0.18F, RESULT = 0.9F;          // seconds: the wheel opening, the result shown

    /** The cells of the track and the rims round it: x, y, radius, angle. */
    private static final List<float[]> RING = new ArrayList<>();

    static {
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                float dx = x + 0.5F - C, dy = y + 0.5F - C;
                float r = Mth.sqrt(dx * dx + dy * dy);
                if (r > TRACK_IN - 1 && r <= 28.0F) RING.add(new float[] {x, y, r, angle(dx, dy)});
            }
        }
    }

    private static FishingPayloads.Start round;
    private static long startedAt, endedAt;
    private static float stoppedAt; // the needle's angle when the round ended
    private static int result = -1; // -1 running, else FishingPayloads.CAUGHT / ESCAPED / CANCELLED
    private static int next;        // a sequence: the zone to hit now
    private static boolean holdJump; // no jumping from the wheel's start until the jump key is let go after it

    private FishingHud() {}

    private static float angle(float dx, float dy) {
        return ((float) Math.toDegrees(Math.atan2(dx, -dy)) % 360 + 360) % 360;
    }

    private static float seconds(long since) {
        return (System.nanoTime() - since) / 1.0e9F;
    }

    private static float needle() {
        if (result >= 0) return stoppedAt;
        return round.start() + round.speed() * 20 * seconds(startedAt);
    }

    /** The zone the angle is in (in a sequence only the next one counts), or -1. */
    private static int zoneAt(float a) {
        for (int i = 0; i < round.zones().size(); i++) {
            if (round.sequence() && i != next) continue;
            if (Math.abs(Mth.wrapDegrees(a - round.zones().get(i))) <= round.half() + TOLERANCE) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ the round

    public static void start(FishingPayloads.Start payload) {
        Minecraft mc = Minecraft.getInstance();
        while (mc.options.keyJump.consumeClick()) {} // a press from before does not count
        round = payload;
        startedAt = System.nanoTime();
        result = -1;
        next = 0;
        holdJump = true;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6F, 0.6F));
    }

    public static void end(FishingPayloads.End payload) {
        if (round == null || payload.hook() != round.hook()) return;
        if (result >= 0 && payload.result() == FishingPayloads.CANCELLED) return; // already over here
        if (payload.result() != result) finish(payload.result());
    }

    private static void finish(int how) {
        if (result < 0) stoppedAt = needle();
        result = how;
        endedAt = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        if (how == FishingPayloads.CAUGHT) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.8F, 0.35F));
        } else if (how == FishingPayloads.ESCAPED) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.8F));
        }
    }

    private static boolean running() {
        return round != null && result < 0;
    }

    private static void press() {
        if (!running()) return;
        float a = needle();
        int zone = zoneAt(a);
        if (zone >= 0 && round.sequence() && next < round.zones().size() - 1) { // one of the Ancient Fish's zones: on to the next
            next++;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.2F + next * 0.3F, 0.7F));
            return;
        }
        boolean perfect = zone >= 0 && Math.abs(Mth.wrapDegrees(a - round.zones().get(zone))) <= PERFECT;
        PacketDistributor.sendToServer(new FishingPayloads.Press(round.hook(), zone >= 0, perfect));
        finish(zone >= 0 ? FishingPayloads.CAUGHT : FishingPayloads.ESCAPED);
        stoppedAt = a;
        if (perfect) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 1.0F));
    }

    /** The press, the moment the key goes down (not at the next tick: the needle moves 10 degrees a tick). */
    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS && running() && mc.screen == null
                && mc.options.keyJump.matches(event.getKey(), event.getScanCode())) {
            press();
            // the same press is also counted as a click (onTick): in an Ancient Fish's sequence it would be pressed
            // twice, the second time out of the next zone - a miss
            while (mc.options.keyJump.consumeClick()) {}
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (round == null) return;
        if (mc.player == null) {
            round = null;
            return;
        }
        if (running()) {
            while (mc.options.keyJump.consumeClick()) press(); // a jump bound to a mouse button
            if (running() && needle() - round.start() >= round.turns() * 360) {
                PacketDistributor.sendToServer(new FishingPayloads.Press(round.hook(), false, false));
                finish(FishingPayloads.ESCAPED);
            }
        } else if (seconds(endedAt) > RESULT) {
            round = null;
        }
    }

    /**
     * No jumping during the minigame: the jump key is the wheel's - while it turns, while the result shows, and
     * after it until the key is let go (the press that stops the needle is still held down when the round ends).
     */
    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        if (!holdJump) return;
        if (round == null && !Minecraft.getInstance().options.keyJump.isDown()) {
            holdJump = false;
            return;
        }
        event.getInput().jumping = false;
    }

    // ------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "fishing"),
                FishingHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (round == null || mc.options.hideGui) return;
        float open = seconds(startedAt), shown = result < 0 ? 0 : seconds(endedAt);
        float alpha = result < 0 ? Math.min(1, open / POP) : Mth.clamp(1 - (shown - RESULT * 0.55F) / (RESULT * 0.45F), 0, 1);
        if (alpha <= 0.01F) return;
        float pop = open < POP ? backOut(open / POP) : 1;

        int cx = g.guiWidth() / 2;
        int cy = Math.min(g.guiHeight() / 2 + 30, g.guiHeight() - 40 - SIZE / 2);
        float shake = result == FishingPayloads.ESCAPED && shown < 0.35F ? Mth.sin(shown * 70) * 3 * (1 - shown / 0.35F) : 0;
        float rise = result >= 0 ? -6 * Mth.clamp((shown - RESULT * 0.55F) / (RESULT * 0.45F), 0, 1) : 0;

        g.pose().pushPose();
        g.pose().translate(cx + shake, cy + rise, 0);
        g.pose().scale(pop, pop, 1);
        int x0 = -SIZE / 2, y0 = -SIZE / 2;

        RenderSystem.enableBlend();
        g.setColor(1, 1, 1, alpha);
        g.blit(WHEEL, x0, y0, SIZE, SIZE, 0, 0, N, N, N, N);
        g.setColor(1, 1, 1, 1);

        zone(g, x0, y0, alpha, open, shown);
        if (result == FishingPayloads.CAUGHT) burst(g, x0, y0, alpha, shown);
        needleCells(g, x0, y0, alpha);
        labels(g, mc.font, alpha, open);
        RenderSystem.disableBlend();
        g.pose().popPose();
    }

    private static float backOut(float t) {
        float c = 1.9F;
        float u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    private static int argb(float a, int rgb) {
        return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
        int gg = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
        int bb = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | gg << 8 | bb;
    }

    private static void cell(GuiGraphics g, int x0, int y0, int x, int y, int color) {
        g.fill(x0 + x * CELL, y0 + y * CELL, x0 + x * CELL + CELL, y0 + y * CELL + CELL, color);
    }

    /**
     * The golden zones on the track, their edges bright, pulsing; green when caught, dim when the fish got away. In a
     * sequence the ones already hit are green, the ones still to come dim.
     */
    private static void zone(GuiGraphics g, int x0, int y0, float alpha, float open, float shown) {
        for (int i = 0; i < round.zones().size(); i++) zone(g, x0, y0, alpha, open, shown, i);
    }

    private static void zone(GuiGraphics g, int x0, int y0, float alpha, float open, float shown, int index) {
        float pulse = 0.5F + 0.5F * Mth.sin(open * 7);
        boolean hot = running() && zoneAt(needle()) == index;
        boolean done = round.sequence() && index < next, later = round.sequence() && index > next;
        for (float[] c : RING) {
            float d = Math.abs(Mth.wrapDegrees(c[3] - round.zones().get(index)));
            if (d > round.half()) continue;
            float r = c[2];
            float step = (float) Math.toDegrees(1 / r); // degrees a cell spans here
            int color;
            if (r > TRACK_OUT) {
                color = 0xFFC85A;
            } else if (r <= TRACK_IN) {
                color = 0x96641E;
            } else {
                color = mix(0xD6962A, 0xFFD660, (r - TRACK_IN) / (TRACK_OUT - TRACK_IN));
                if (d > round.half() - step) color = 0xFFF6D6;
                color = mix(color, 0xFFFFFF, (hot ? 0.35F : 0.12F) * pulse);
            }
            if (result == FishingPayloads.CAUGHT || done) color = mix(color, 0x7CFF8C, done && result < 0 ? 0.7F : Math.max(0.35F, 1 - shown * 2));
            else if (result == FishingPayloads.ESCAPED) color = mix(color, 0x3A2A20, 0.55F);
            else if (later) color = mix(color, 0x2A2418, 0.5F);
            cell(g, x0, y0, (int) c[0], (int) c[1], argb(alpha, color));
        }
    }

    /** Caught: a ring of light runs out from the zone over the rim. */
    private static void burst(GuiGraphics g, int x0, int y0, float alpha, float shown) {
        float t = Mth.clamp(shown / 0.45F, 0, 1);
        if (t >= 1) return;
        float radius = 22 + t * 12;
        float a = alpha * (1 - t) * 0.8F;
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                float dx = x + 0.5F - C, dy = y + 0.5F - C;
                if (Math.abs(Mth.sqrt(dx * dx + dy * dy) - radius) < 0.6F) cell(g, x0, y0, x, y, argb(a, 0xB8FFC4));
            }
        }
    }

    /** The needle: two cells wide, a red tip, a shadow below and right, a faint trail behind it while it turns. */
    private static void needleCells(GuiGraphics g, int x0, int y0, float alpha) {
        float a = needle();
        if (running()) {
            line(g, x0, y0, a - round.speed() * 0.35F, alpha * 0.18F, false);
            line(g, x0, y0, a - round.speed() * 0.18F, alpha * 0.35F, false);
        }
        line(g, x0, y0, a, alpha, true);
    }

    private static void line(GuiGraphics g, int x0, int y0, float degrees, float alpha, boolean solid) {
        float s = Mth.sin(degrees * Mth.DEG_TO_RAD), c = Mth.cos(degrees * Mth.DEG_TO_RAD);
        Set<Long> cells = new LinkedHashSet<>();
        Set<Long> tip = new LinkedHashSet<>();
        for (float t = 7; t <= 29.5F; t += 0.25F) {
            float[] offsets = t < 26 ? new float[] {-0.45F, 0.45F} : new float[] {0};
            for (float o : offsets) {
                long key = key(Mth.floor(C + s * t + c * o), Mth.floor(C - c * t + s * o));
                cells.add(key);
                if (t >= 25) tip.add(key);
            }
        }
        boolean caught = result == FishingPayloads.CAUGHT, missed = result == FishingPayloads.ESCAPED;
        if (solid) {
            for (long k : cells) {
                int x = (int) (k >> 32) + 1, y = (int) k + 1;
                if (!cells.contains(key(x, y))) cell(g, x0, y0, x, y, argb(alpha * 0.5F, 0x000000));
            }
        }
        for (long k : cells) {
            int color = tip.contains(k) ? 0xFF6048 : 0xF6F4EC;
            if (caught) color = tip.contains(k) ? 0x5CFF7A : 0xEAFFEE;
            else if (missed) color = tip.contains(k) ? 0xC02818 : 0xC8B8B0;
            cell(g, x0, y0, (int) (k >> 32), (int) k, argb(alpha, color));
        }
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    /** "Fish on the line!" above, the key below (or the result). */
    private static void labels(GuiGraphics g, Font font, float alpha, float open) {
        if (alpha < 0.1F) return;
        int top = -SIZE / 2 - 13, bottom = SIZE / 2 + 5;
        Component title;
        int color;
        if (result == FishingPayloads.CAUGHT) {
            title = Component.literal("Caught!");
            color = 0x7CFF8C;
        } else if (result == FishingPayloads.ESCAPED) {
            title = Component.literal("The fish got away");
            color = 0xFF6A5A;
        } else if (result == FishingPayloads.CANCELLED) {
            title = Component.literal("Reeled in");
            color = 0xB0B8C4;
        } else {
            String text = round.title().isEmpty() ? "Fish on the line!" : round.title();
            if (round.sequence()) text += "  " + next + "/" + round.zones().size();
            title = Component.literal(text);
            color = round.sequence() ? 0x8FF0FF : 0xFFD660;
        }
        g.drawCenteredString(font, title, 0, top, argb(alpha, color));
        if (result >= 0) return;
        Component key = Minecraft.getInstance().options.keyJump.getTranslatedKeyMessage();
        int w = Math.max(font.width(key) + 12, 34), h = 13;
        float glow = 0.5F + 0.5F * Mth.sin(open * 7);
        int x = -w / 2, y = bottom;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, argb(alpha, 0x0D1016));
        g.fill(x, y, x + w, y + h, argb(alpha, mix(0x263042, 0x34445E, glow)));
        g.fill(x, y, x + w, y + 1, argb(alpha, 0x56647C));
        g.fill(x, y + h - 2, x + w, y + h, argb(alpha, 0x161B26));
        g.drawCenteredString(font, key, 0, y + 2, argb(alpha, 0xFFFFFF));
    }
}
