package com.divinis.boss;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A warning area on the ground (a boss attack about to land). Flat shapes on the x/z plane at height y; yaw is a
 * Minecraft yaw in degrees (the direction the shape opens to).
 * - CIRCLE: a = radius
 * - RING:   a = inner radius, b = outer radius
 * - SECTOR: a = radius, b = half of the opening angle in degrees
 * - RECT:   starts at (x, z) and runs a blocks along yaw, b = half of its width
 * - LATTICE: a grid of lines within a circle: a = radius, b = the distance between lines, yaw = half the width of a
 *   line; the squares between the lines are safe
 * The same geometry decides who gets hit on the server (Telegraphs) and what is drawn on the client.
 */
public record Telegraph(Shape shape, double x, double y, double z, float yaw, float a, float b) {
    public enum Shape { CIRCLE, RING, SECTOR, RECT, LATTICE }

    public static Telegraph circle(Vec3 center, float radius) {
        return new Telegraph(Shape.CIRCLE, center.x, center.y, center.z, 0, radius, 0);
    }

    public static Telegraph ring(Vec3 center, float inner, float outer) {
        return new Telegraph(Shape.RING, center.x, center.y, center.z, 0, inner, outer);
    }

    public static Telegraph sector(Vec3 center, float yaw, float radius, float halfAngle) {
        return new Telegraph(Shape.SECTOR, center.x, center.y, center.z, yaw, radius, halfAngle);
    }

    public static Telegraph rect(Vec3 start, float yaw, float length, float halfWidth) {
        return new Telegraph(Shape.RECT, start.x, start.y, start.z, yaw, length, halfWidth);
    }

    public static Telegraph lattice(Vec3 center, float radius, float spacing, float lineHalfWidth) {
        return new Telegraph(Shape.LATTICE, center.x, center.y, center.z, lineHalfWidth, radius, spacing);
    }

    /** The same shape turned by degrees round its origin (a lattice does not turn). */
    public Telegraph turned(float degrees) {
        return degrees == 0 || shape == Shape.LATTICE ? this : new Telegraph(shape, x, y, z, yaw + degrees, a, b);
    }

    private double fx() {
        return -Mth.sin(yaw * Mth.DEG_TO_RAD);
    }

    private double fz() {
        return Mth.cos(yaw * Mth.DEG_TO_RAD);
    }

    /** Is the point inside the shape, the shape grown by margin (e.g. half an entity's width)? */
    public boolean contains(double px, double pz, double margin) {
        double dx = px - x, dz = pz - z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        return switch (shape) {
            case CIRCLE -> dist <= a + margin;
            case RING -> dist >= a - margin && dist <= b + margin;
            case SECTOR -> {
                if (dist > a + margin) yield false;
                if (dist <= 1.0 + margin) yield true; // right at the tip
                double cos = (dx * fx() + dz * fz()) / dist;
                double angle = Math.toDegrees(Math.acos(Mth.clamp(cos, -1.0, 1.0)));
                yield angle <= b + Math.toDegrees(Math.asin(Math.min(1.0, margin / dist)));
            }
            case RECT -> {
                double along = dx * fx() + dz * fz();
                double side = dx * fz() - dz * fx();
                yield along >= -margin && along <= a + margin && Math.abs(side) <= b + margin;
            }
            case LATTICE -> {
                if (dist > a + margin) yield false;
                double offX = Math.abs(dx / b - Math.rint(dx / b)) * b, offZ = Math.abs(dz / b - Math.rint(dz / b)) * b;
                yield offX <= yaw + margin || offZ <= yaw + margin;
            }
        };
    }

    /** 0 at the shape's origin .. 1 at its far edge: how the warning fills up while the attack charges. */
    public double depth(double px, double pz) {
        double dx = px - x, dz = pz - z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        return switch (shape) {
            case CIRCLE, SECTOR, LATTICE -> dist / a;
            case RING -> (dist - a) / Math.max(0.01, b - a);
            case RECT -> (dx * fx() + dz * fz()) / a;
        };
    }

    /** Radius of a circle around (x, z) that holds the whole shape. */
    public double reach() {
        return switch (shape) {
            case CIRCLE, SECTOR, LATTICE -> a;
            case RING -> b;
            case RECT -> Math.sqrt(a * a + b * b);
        };
    }

    public static final StreamCodec<ByteBuf, Telegraph> CODEC = StreamCodec.of(
            (buf, t) -> {
                buf.writeByte(t.shape.ordinal());
                buf.writeDouble(t.x);
                buf.writeDouble(t.y);
                buf.writeDouble(t.z);
                buf.writeFloat(t.yaw);
                buf.writeFloat(t.a);
                buf.writeFloat(t.b);
            },
            buf -> new Telegraph(Shape.values()[buf.readByte()], buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat()));
}
