package com.divinis.boss;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Serpent of Infinity's body: a chain of segments, each kept a fixed distance behind the one before it (the first
 * behind the head), so the body follows wherever the head goes. The server and every client run the same chain from
 * the head's position (for hits and for drawing); a jump of the head (a teleport) lays the chain out straight
 * behind it again, along the way it now faces, so the body comes out of the wall (or the ground) after the head.
 * Positions are the middles of the segments.
 */
public final class SerpentBody {
    public static final int SEGMENTS = 22;
    public static final double HEAD_GAP = 2.6, GAP = 2.05;
    private static final double JUMP = 7.0;

    public final Vec3[] at = new Vec3[SEGMENTS];
    public final Vec3[] before = new Vec3[SEGMENTS];
    private Vec3 lastHead;

    public SerpentBody(Vec3 head) {
        layOut(head, new Vec3(0, 0, 1));
    }

    /** All segments in a straight line behind the head, along `back`. */
    public void layOut(Vec3 head, Vec3 back) {
        Vec3 d = back.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : back.normalize();
        for (int i = 0; i < SEGMENTS; i++) {
            at[i] = head.add(d.scale(HEAD_GAP + i * GAP));
            before[i] = at[i];
        }
        lastHead = head;
    }

    /** Coiled up on the ground round `center` (asleep): a spiral, the head at its inner end. */
    public static Vec3 coilHead(Vec3 center) {
        return center.add(1.5, 1.2, 0);
    }

    public void coil(Vec3 center) {
        double angle = 0, radius = 3.4;
        for (int i = 0; i < SEGMENTS; i++) {
            angle += GAP / radius;
            radius += 0.22;
            at[i] = center.add(Math.cos(angle) * radius, 1.0 + Math.max(0, 0.6 - i * 0.05), Math.sin(angle) * radius);
            before[i] = at[i];
        }
        lastHead = coilHead(center);
    }

    /** One step: the head is at `head` now, looking along `look`. */
    public void follow(Vec3 head, Vec3 look) {
        for (int i = 0; i < SEGMENTS; i++) before[i] = at[i];
        if (lastHead != null && head.distanceTo(lastHead) > JUMP) {
            layOut(head, look.scale(-1));
            return;
        }
        Vec3 lead = head;
        for (int i = 0; i < SEGMENTS; i++) {
            double gap = i == 0 ? HEAD_GAP : GAP;
            Vec3 d = at[i].subtract(lead);
            double len = d.length();
            if (len > 1.0E-4) at[i] = lead.add(d.scale(gap / len));
            lead = at[i];
        }
        lastHead = head;
    }

    /** Where segment i is between the last step and this one. */
    public Vec3 lerp(int i, float partial) {
        return before[i].lerp(at[i], partial);
    }

    /** The way the head looks, from its yaw and pitch (Minecraft's). */
    public static Vec3 look(float yaw, float pitch) {
        float y = yaw * Mth.DEG_TO_RAD, p = pitch * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(y) * Mth.cos(p), -Mth.sin(p), Mth.cos(y) * Mth.cos(p));
    }
}
