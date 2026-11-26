package com.divinis.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.divinis.boss.Telegraph;
import com.divinis.net.TelegraphPayload;
import com.divinis.Divinis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.divinis.platform.Dist;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.client.ClientPlayerNetworkEvent;
import com.divinis.platform.event.client.RenderLevelStageEvent;

/**
 * Draws boss attack warnings (boss.Telegraphs) on the ground: a faint red area with a bright rim that fills up from
 * its origin while the attack charges, pulses just before it lands, and flashes when it hits. A sweep, once full,
 * burns bright orange-red and turns round its origin. The area is drawn in half-block cells laid on top of the
 * blocks under them, so it follows uneven ground.
 */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class TelegraphRenderer {
    private TelegraphRenderer() {}

    private static final float CELL = 0.5F;
    private static final float LIFT = 0.03F;

    private static final RenderType TYPE = RenderType.create("divinis_telegraph", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 8192, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private record Cell(float x, float z, float y, double depth, boolean edge) {}

    /** a cell of a turning area, kept relative to the area's origin (its ground is looked up where it is now) */
    private record Spot(float dx, float dz, double depth, boolean edge) {}

    private static final class Shown {
        final int id;
        final Telegraph shape;
        final int warmup, linger, active;
        final float spin;
        final long start;
        List<Cell> cells;
        List<Spot> spots;
        final Map<Long, Float> ground = new HashMap<>();

        Shown(int id, Telegraph shape, int warmup, int linger, int active, float spin, long start) {
            this.id = id;
            this.shape = shape;
            this.warmup = warmup;
            this.linger = linger;
            this.active = active;
            this.spin = spin;
            this.start = start;
        }
    }

    private static final List<Shown> SHOWN = new ArrayList<>();

    public static void receive(TelegraphPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (p.warmup() < 0) {
            SHOWN.removeIf(s -> s.id == p.id());
            return;
        }
        SHOWN.add(new Shown(p.id(), p.telegraph(), p.warmup(), p.linger(), p.active(), p.spin(), level.getGameTime()));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SHOWN.clear();
    }

    // ------------------------------------------------------------------ cells

    /** Top of the ground at a block column near height y (the shape's height if nothing is found). */
    private static float groundAt(ClientLevel level, int bx, int bz, double y, Map<Long, Float> cache) {
        long key = BlockPos.asLong(bx, 0, bz);
        Float known = cache.get(key);
        if (known != null) return known;
        float found = (float) y;
        int top = Mth.floor(y) + 3;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int by = top; by >= top - 9; by--) {
            pos.set(bx, by, bz);
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            pos.set(bx, by + 1, bz);
            VoxelShape above = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!above.isEmpty() && above.max(Direction.Axis.Y) >= 1.0) continue; // buried, keep looking down
            found = by + (float) shape.max(Direction.Axis.Y);
            break;
        }
        cache.put(key, found);
        return found;
    }

    private static List<Cell> cells(ClientLevel level, Telegraph t) {
        List<Cell> out = new ArrayList<>();
        Map<Long, Float> ground = new HashMap<>();
        double r = t.reach();
        double x0 = Math.floor((t.x() - r) / CELL) * CELL, z0 = Math.floor((t.z() - r) / CELL) * CELL;
        int n = (int) Math.ceil(2 * r / CELL) + 2;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float cx = (float) (x0 + i * CELL), cz = (float) (z0 + j * CELL);
                float mx = cx + CELL / 2, mz = cz + CELL / 2;
                if (!t.contains(mx, mz, 0)) continue;
                boolean edge = !t.contains(mx + CELL, mz, 0) || !t.contains(mx - CELL, mz, 0)
                        || !t.contains(mx, mz + CELL, 0) || !t.contains(mx, mz - CELL, 0);
                float y = groundAt(level, Mth.floor(mx), Mth.floor(mz), t.y(), ground);
                out.add(new Cell(cx, cz, y, Mth.clamp(t.depth(mx, mz), 0, 1), edge));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SHOWN.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            SHOWN.clear();
            return;
        }
        float now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        SHOWN.removeIf(s -> now - s.start > s.warmup + s.active + s.linger + 1);
        if (SHOWN.isEmpty()) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(TYPE);

        for (Shown s : SHOWN) {
            if (s.active > 0) {
                drawTurning(level, vc, m, s, now);
                continue;
            }
            if (s.cells == null) s.cells = cells(level, s.shape);
            float age = now - s.start;
            if (age < 0) continue;
            float progress = Math.min(1.0F, age / s.warmup);
            boolean impact = age >= s.warmup;
            float flash = impact ? 1.0F - (age - s.warmup) / Math.max(1, s.linger) : 0;
            // the last quarter pulses faster and faster
            float pulse = progress > 0.75F ? 0.8F + 0.2F * Mth.sin(age * (0.8F + progress)) : 1.0F;
            for (Cell c : s.cells) {
                int r, g, b;
                float alpha;
                if (impact) {
                    r = 255; g = 150; b = 90;
                    alpha = 0.75F * flash;
                } else {
                    r = 230; g = 30; b = 24;
                    boolean filled = c.depth() <= progress;
                    alpha = c.edge() ? 0.75F : filled ? 0.45F : 0.18F;
                    if (filled && !c.edge()) g = 50;
                    alpha *= pulse;
                }
                if (alpha <= 0.01F) continue;
                int a = (int) (alpha * 255);
                float y = c.y() + LIFT;
                vc.addVertex(m, c.x(), y, c.z()).setColor(r, g, b, a);
                vc.addVertex(m, c.x(), y, c.z() + CELL).setColor(r, g, b, a);
                vc.addVertex(m, c.x() + CELL, y, c.z() + CELL).setColor(r, g, b, a);
                vc.addVertex(m, c.x() + CELL, y, c.z()).setColor(r, g, b, a);
            }
        }
        buffers.endBatch(TYPE);
        pose.popPose();
    }

    /** A sweep: the area turned round its origin as far as it has burned; filling, then burning, then a flash. */
    private static void drawTurning(ClientLevel level, VertexConsumer vc, Matrix4f m, Shown s, float now) {
        Telegraph t = s.shape;
        if (s.spots == null) {
            s.spots = new ArrayList<>();
            double r = t.reach();
            int n = (int) Math.ceil(2 * r / CELL) + 2;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    float mx = (float) (t.x() - r) + (i + 0.5F) * CELL, mz = (float) (t.z() - r) + (j + 0.5F) * CELL;
                    if (!t.contains(mx, mz, 0)) continue;
                    boolean edge = !t.contains(mx + CELL, mz, 0) || !t.contains(mx - CELL, mz, 0)
                            || !t.contains(mx, mz + CELL, 0) || !t.contains(mx, mz - CELL, 0);
                    s.spots.add(new Spot((float) (mx - t.x()), (float) (mz - t.z()), Mth.clamp(t.depth(mx, mz), 0, 1), edge));
                }
            }
        }
        float age = now - s.start;
        if (age < 0) return;
        float burnt = Mth.clamp(age - s.warmup, 0, s.active);
        double turn = Math.toRadians(s.spin * burnt);
        float cos = (float) Math.cos(turn), sin = (float) Math.sin(turn);
        boolean burning = age >= s.warmup && age < s.warmup + s.active;
        boolean flash = age >= s.warmup + s.active;
        float progress = Math.min(1.0F, age / s.warmup);
        float pulse = 0.8F + 0.2F * Mth.sin(age * 0.9F);
        float half = CELL / 2;
        for (Spot c : s.spots) {
            float x = (float) t.x() + c.dx() * cos - c.dz() * sin, z = (float) t.z() + c.dx() * sin + c.dz() * cos;
            int r, g, b;
            float alpha;
            if (flash) {
                r = 255; g = 150; b = 90;
                alpha = 0.6F * (1 - (age - s.warmup - s.active) / Math.max(1, s.linger));
            } else if (burning) {
                r = 255; g = c.edge() ? 120 : 70; b = 20;
                alpha = (c.edge() ? 0.8F : 0.55F) * pulse;
            } else {
                r = 230; g = 30; b = 24;
                boolean filled = c.depth() <= progress;
                alpha = c.edge() ? 0.75F : filled ? 0.45F : 0.18F;
            }
            if (alpha <= 0.01F) continue;
            int a = (int) (alpha * 255);
            float y = groundAt(level, Mth.floor(x), Mth.floor(z), t.y(), s.ground) + LIFT;
            // the cell's corners, turned with it
            float ax = -half * cos + half * sin, az = -half * sin - half * cos;
            float bx = half * cos + half * sin, bz = half * sin - half * cos;
            vc.addVertex(m, x + ax, y, z + az).setColor(r, g, b, a);
            vc.addVertex(m, x - bx, y, z - bz).setColor(r, g, b, a);
            vc.addVertex(m, x - ax, y, z - az).setColor(r, g, b, a);
            vc.addVertex(m, x + bx, y, z + bz).setColor(r, g, b, a);
        }
    }
}
