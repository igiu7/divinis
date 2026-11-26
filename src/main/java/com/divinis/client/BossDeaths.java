package com.divinis.client;

import java.util.List;
import java.util.Map;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import org.joml.Vector3f;

/**
 * The bosses' death animations (their effects are played by the bosses themselves, boss.BossDeath), timed to their
 * tickDeath:
 *  MINOTAUR (4 s)   rears up roaring at the sky, drops its axe, falls to its knees, then onto its face
 *  GOLEM (5 s)      staggers as its crystals crack, then comes apart into the heap of stones it slept as (2.4-3.2 s)
 *  WILDFIRE (4.5 s) its ring spins wild, the helm shakes, the shields fall away one by one, it sinks, then flares up
 *  SERPENT (6 s)    the head roars, jaws and mandibles wide, thrashing, and droops at the end
 *  AURELION (7 s)   every eye rolls back, the wings fold round it and tremble, it rises and shrinks into a point
 *  NOCTHARIEL (7 s) every mouth screams, the arms claw at the sky, the wings beat and wrap round it, it turns faster
 *                   and faster and is swallowed into nothing
 */
public final class BossDeaths {
    private BossDeaths() {}

    private static final AnimationChannel.Interpolation SMOOTH = AnimationChannel.Interpolations.CATMULLROM;
    private static final AnimationChannel.Interpolation LINEAR = AnimationChannel.Interpolations.LINEAR;

    /** Rotation keys: {time, x, y, z} in degrees. */
    private static AnimationChannel rot(float[]... keys) {
        Keyframe[] out = new Keyframe[keys.length];
        for (int i = 0; i < keys.length; i++) {
            float[] k = keys[i];
            out[i] = new Keyframe(k[0], KeyframeAnimations.degreeVec(k[1], k[2], k[3]), SMOOTH);
        }
        return new AnimationChannel(AnimationChannel.Targets.ROTATION, out);
    }

    /** Position keys: {time, x, y (up), z} in pixels. */
    private static AnimationChannel pos(float[]... keys) {
        Keyframe[] out = new Keyframe[keys.length];
        for (int i = 0; i < keys.length; i++) {
            float[] k = keys[i];
            out[i] = new Keyframe(k[0], KeyframeAnimations.posVec(k[1], k[2], k[3]), SMOOTH);
        }
        return new AnimationChannel(AnimationChannel.Targets.POSITION, out);
    }

    /** Scale keys: {time, scale}. */
    private static AnimationChannel scale(float[]... keys) {
        Keyframe[] out = new Keyframe[keys.length];
        for (int i = 0; i < keys.length; i++) {
            float[] k = keys[i];
            out[i] = new Keyframe(k[0], KeyframeAnimations.scaleVec(k[1], k[1], k[1]), SMOOTH);
        }
        return new AnimationChannel(AnimationChannel.Targets.SCALE, out);
    }

    private static float[] k(float t, float x, float y, float z) {
        return new float[] {t, x, y, z};
    }

    private static float[] s(float t, float v) {
        return new float[] {t, v};
    }

    // ------------------------------------------------------------------ the Minotaur

    public static final AnimationDefinition MINOTAUR = AnimationDefinition.Builder.withLength(4.0F)
            .addAnimation("chest", rot(k(0, 0, 0, 0), k(0.5F, -30, 0, 0), k(1.4F, -34, 0, 0), k(1.8F, 25, 0, 0), k(2.8F, 15, 0, 0), k(4, 15, 0, 0)))
            .addAnimation("head", rot(k(0, 0, 0, 0), k(0.5F, -40, 0, 0), k(1.0F, -44, 6, 0), k(1.4F, -40, -6, 0), k(1.8F, 25, 0, 0), k(3.0F, 30, 0, 12), k(4, 30, 0, 12)))
            .addAnimation("jaw", rot(k(0, 0, 0, 0), k(0.5F, 35, 0, 0), k(1.4F, 35, 0, 0), k(1.8F, 10, 0, 0), k(4, 20, 0, 0)))
            .addAnimation("arm_right", rot(k(0, 0, 0, 0), k(0.5F, -150, 0, 25), k(1.4F, -150, 0, 25), k(1.8F, -20, 0, 10), k(3.0F, -165, 0, 10), k(4, -165, 0, 10)))
            .addAnimation("arm_left", rot(k(0, 0, 0, 0), k(0.5F, -150, 0, -25), k(1.4F, -150, 0, -25), k(1.8F, -20, 0, -10), k(3.0F, -165, 0, -10), k(4, -165, 0, -10)))
            .addAnimation("axe", rot(k(0, 0, 0, 0), k(1.8F, 0, 0, 0), k(2.1F, 90, 0, 20), k(4, 90, 0, 20)))
            .addAnimation("axe", pos(k(0, 0, 0, 0), k(1.8F, 0, 0, 0), k(2.1F, 0, -10, 4), k(4, 0, -10, 4)))
            .addAnimation("body", rot(k(0, 0, 0, 0), k(2.3F, 0, 0, 0), k(3.0F, 85, 0, 0), k(4, 85, 0, 0)))
            .addAnimation("body", pos(k(0, 0, 0, 0), k(1.8F, 0, 0, 0), k(2.3F, 0, -9, 0), k(3.0F, 0, -15, -4), k(4, 0, -15, -4)))
            .addAnimation("leg_right", rot(k(0, 0, 0, 0), k(1.8F, 0, 0, 0), k(2.3F, -75, 0, 0), k(3.0F, -20, 0, 0), k(4, -20, 0, 0)))
            .addAnimation("leg_left", rot(k(0, 0, 0, 0), k(1.8F, 0, 0, 0), k(2.3F, -75, 0, 0), k(3.0F, -20, 0, 0), k(4, -20, 0, 0)))
            .addAnimation("tail", rot(k(0, 0, 0, 0), k(0.6F, -30, 25, 0), k(1.2F, -30, -25, 0), k(2.0F, 10, 0, 0), k(3.0F, 30, 10, 0), k(4, 30, 10, 0)))
            .build();

    // ------------------------------------------------------------------ the Golem: a stagger, then back into its heap

    public static final AnimationDefinition GOLEM = golem();

    private static AnimationDefinition golem() {
        float collapse = 2.4F, heap = 3.2F, length = 5.0F;
        AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(length);
        b.addAnimation("torso", rot(k(0, 0, 0, 0), k(0.5F, 8, 0, -6), k(1.0F, -6, 0, 8), k(1.5F, 10, 0, -10), k(2.0F, -8, 0, 6), k(collapse, 0, 0, 0)));
        b.addAnimation("head", rot(k(0, 0, 0, 0), k(0.4F, -25, 10, 0), k(1.2F, -30, -10, 0), k(2.0F, 20, 0, 0), k(collapse, 0, 0, 0)));
        // every part goes from its place to where it lay in the heap (the sleep animation's pose)
        for (Map.Entry<String, List<AnimationChannel>> e : GolemModelData.SLEEP.boneAnimations().entrySet()) {
            for (AnimationChannel c : e.getValue()) {
                Vector3f heapAt = c.keyframes()[0].target();
                Vector3f zero = new Vector3f();
                b.addAnimation(e.getKey(), new AnimationChannel(c.target(),
                        new Keyframe(0, zero, LINEAR), new Keyframe(collapse, zero, SMOOTH),
                        new Keyframe(heap, new Vector3f(heapAt), SMOOTH), new Keyframe(length, new Vector3f(heapAt), LINEAR)));
            }
        }
        return b.build();
    }

    // ------------------------------------------------------------------ the Wildfire

    public static final AnimationDefinition WILDFIRE = wildfire();

    private static AnimationDefinition wildfire() {
        AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(4.5F);
        b.addAnimation("ring", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                new Keyframe(0, KeyframeAnimations.degreeVec(0, 0, 0), LINEAR), new Keyframe(1.5F, KeyframeAnimations.degreeVec(0, 720, 0), LINEAR),
                new Keyframe(2.5F, KeyframeAnimations.degreeVec(0, 900, 0), SMOOTH), new Keyframe(4.5F, KeyframeAnimations.degreeVec(0, 900, 0), SMOOTH)));
        b.addAnimation("head", rot(k(0, 0, 0, 0), k(0.3F, 10, 10, 0), k(0.6F, -10, -10, 0), k(0.9F, 10, 0, 10), k(1.2F, -10, 0, -10), k(1.5F, 0, 0, 0),
                k(3.0F, 15, 0, 0), k(3.6F, -35, 0, 0), k(4.5F, -35, 0, 0)));
        for (int n = 0; n < 4; n++) {
            float t0 = 1.5F + n * 0.15F;
            float side = n % 2 == 0 ? 1 : -1;
            b.addAnimation("arm" + n, rot(k(0, 0, 0, 0), k(t0, 0, 0, 0), k(t0 + 0.6F, 30, 0, 70 * side), k(4.5F, 30, 0, 70 * side)));
            b.addAnimation("shield" + n, pos(k(0, 0, 0, 0), k(t0 + 0.1F, 0, 0, 0), k(t0 + 0.8F, 0, -26, 0), k(4.5F, 0, -26, 0)));
            b.addAnimation("shield" + n, rot(k(0, 0, 0, 0), k(t0 + 0.1F, 0, 0, 0), k(t0 + 0.8F, 90, 0, 30 * side), k(4.5F, 90, 0, 30 * side)));
        }
        b.addAnimation("float", pos(k(0, 0, 0, 0), k(1.5F, 0, 0, 0), k(2.5F, 0, -10, 0), k(3.5F, 0, -8, 0), k(4.2F, 0, 6, 0), k(4.5F, 0, 7, 0)));
        b.addAnimation("wing_left", rot(k(0, 0, 0, 0), k(1.5F, 0, 0, 0), k(2.5F, 0, 0, -40), k(4.5F, 0, 0, -40)));
        b.addAnimation("wing_right", rot(k(0, 0, 0, 0), k(1.5F, 0, 0, 0), k(2.5F, 0, 0, 40), k(4.5F, 0, 0, 40)));
        return b.build();
    }

    // ------------------------------------------------------------------ the Serpent of Infinity's head

    public static final AnimationDefinition SERPENT = AnimationDefinition.Builder.withLength(6.0F)
            .addAnimation("head", rot(k(0, 0, 0, 0), k(0.4F, -30, 0, 0), k(1.0F, -35, 12, 6), k(1.6F, -28, -12, -6), k(2.2F, -36, 10, 4),
                    k(2.8F, -26, -10, -4), k(3.4F, -34, 8, 6), k(4.0F, -28, -8, -6), k(4.6F, -32, 6, 0), k(5.0F, -30, 0, 0), k(5.6F, 30, 0, 0), k(6, 30, 0, 0)))
            .addAnimation("jaw", rot(k(0, 0, 0, 0), k(0.4F, 45, 0, 0), k(5.0F, 45, 0, 0), k(5.6F, 15, 0, 0), k(6, 15, 0, 0)))
            .addAnimation("mandible_left", rot(k(0, 0, 0, 0), k(0.4F, 0, -35, 0), k(5.0F, 0, -35, 0), k(5.6F, 0, -10, 0), k(6, 0, -10, 0)))
            .addAnimation("mandible_right", rot(k(0, 0, 0, 0), k(0.4F, 0, 35, 0), k(5.0F, 0, 35, 0), k(5.6F, 0, 10, 0), k(6, 0, 10, 0)))
            .addAnimation("frill", rot(k(0, 0, 0, 0), k(0.4F, -35, 0, 0), k(5.0F, -35, 0, 0), k(5.6F, 10, 0, 0), k(6, 10, 0, 0)))
            .build();

    // ------------------------------------------------------------------ Aurelion

    public static final AnimationDefinition AURELION = aurelion();

    private static AnimationDefinition aurelion() {
        AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(7.0F);
        String[] eyes = {"eye_main", "eye_ring0", "eye_ring1", "eye_ring2", "eye_ring3", "eye_ring4", "eye_ring5", "eye_ring6",
                "eye_small0", "eye_small1", "eye_small2", "eye_small3", "eye_small4", "eye_small5"};
        for (int i = 0; i < eyes.length; i++) {
            float j = (i % 3 - 1) * 6;
            b.addAnimation(eyes[i], rot(k(0, 0, 0, 0), k(0.8F, -82, 0, 0), k(2.0F, -82, j, 0), k(3.0F, -78, -j, 0), k(4.0F, -84, j, 0), k(7, -82, 0, 0)));
        }
        for (String sideName : new String[] {"left", "right"}) {
            float s = sideName.equals("left") ? 1 : -1;
            for (int i = 0; i < 3; i++) {
                b.addAnimation("wing_" + sideName + i, rot(k(0, 0, 0, 0), k(1.5F, 0, s * 42, s * 10), k(2.5F, 0, s * 46, s * 14), k(3.0F, 0, s * 40, s * 8),
                        k(3.5F, 0, s * 47, s * 14), k(4.0F, 0, s * 41, s * 8), k(4.5F, 0, s * 48, s * 15), k(7, 0, s * 50, s * 16)));
            }
        }
        b.addAnimation("body", pos(k(0, 0, 0, 0), k(7, 0, 8, 0)));
        b.addAnimation("body", scale(s(0, 1), s(4.5F, 1.1F), s(6.5F, 0.12F), s(7, 0.0F)));
        return b.build();
    }

    // ------------------------------------------------------------------ Nocthariel

    public static final AnimationDefinition NOCTHARIEL = nocthariel();

    private static AnimationDefinition nocthariel() {
        AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(7.0F);
        String[] jaws = {"mouth0_jaw", "mouth1_jaw", "mouth2_jaw", "mouth3_jaw", "mouth4_jaw", "mouth5_jaw", "crownmouth0_jaw", "crownmouth1_jaw",
                "crownmouth2_jaw", "chestmouth_left_jaw", "chestmouth_right_jaw"};
        for (int i = 0; i < jaws.length; i++) {
            float off = i * 0.07F;
            b.addAnimation(jaws[i], rot(k(0, 0, 0, 0), k(0.3F + off, 55, 0, 0), k(1.2F + off, 40, 0, 0), k(2.0F + off, 60, 0, 0), k(2.8F + off, 42, 0, 0),
                    k(3.6F + off, 62, 0, 0), k(7, 62, 0, 0)));
        }
        b.addAnimation("head", rot(k(0, 0, 0, 0), k(0.5F, -35, 0, 0), k(1.2F, -38, 8, 4), k(2.0F, -32, -8, -4), k(3.0F, -40, 6, 0), k(4.5F, -30, 0, 0), k(7, -30, 0, 0)));
        for (String arm : new String[] {"arm_left", "arm2_left", "arm_right", "arm2_right"}) {
            float s = arm.contains("left") ? 1 : -1;
            b.addAnimation(arm, rot(k(0, 0, 0, 0), k(1.0F, -160, 0, -20 * s), k(3.5F, -150, 0, -30 * s), k(4.8F, -60, 0, 60 * s), k(7, -60, 0, 60 * s)));
        }
        for (String sideName : new String[] {"left", "right"}) {
            float s = sideName.equals("left") ? 1 : -1;
            for (int i = 0; i < 3; i++) {
                b.addAnimation("wing_" + sideName + i, rot(k(0, 0, 0, 0), k(0.25F, 0, 0, -30 * s), k(0.5F, 0, 0, 20 * s), k(0.75F, 0, 0, -30 * s),
                        k(1.0F, 0, 0, 20 * s), k(1.5F, 0, 0, -35 * s), k(2.0F, 0, 0, 25 * s), k(2.5F, 0, 0, -35 * s), k(3.0F, 0, 0, 20 * s),
                        k(4.2F, 0, s * 50, s * 12), k(7, 0, s * 55, s * 15)));
            }
        }
        b.addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                new Keyframe(0, KeyframeAnimations.degreeVec(0, 0, 0), LINEAR), new Keyframe(4.0F, KeyframeAnimations.degreeVec(0, 120, 0), SMOOTH),
                new Keyframe(6.6F, KeyframeAnimations.degreeVec(0, 900, 0), SMOOTH), new Keyframe(7.0F, KeyframeAnimations.degreeVec(0, 1080, 0), LINEAR)));
        b.addAnimation("body", scale(s(0, 1), s(4.8F, 0.92F), s(6.6F, 0.03F), s(7, 0.0F)));
        return b.build();
    }
}
