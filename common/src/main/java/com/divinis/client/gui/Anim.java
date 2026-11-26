package com.divinis.client.gui;

import net.minecraft.util.Mth;

/** Time-based easing for the Divinis screens: smooth hovers, fades, pulses. */
final class Anim {
    private float value;
    private long last;

    /** Moves towards 1 (on) or 0 (off), faster with a higher speed; returns the current value. Call once per frame. */
    float toward(boolean on, float speed) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0F : Math.min(0.1F, (now - last) / 1e9F);
        last = now;
        float target = on ? 1F : 0F;
        value += (target - value) * (1F - (float) Math.exp(-speed * dt));
        if (Math.abs(target - value) < 0.002F) value = target;
        return value;
    }

    static float easeOut(float t) {
        float u = 1F - Mth.clamp(t, 0F, 1F);
        return 1F - u * u * u;
    }

    /** 0 to 1 over ms milliseconds since start. */
    static float progress(long start, int ms) {
        return Mth.clamp((System.currentTimeMillis() - start) / (float) ms, 0F, 1F);
    }

    /** A 0..1 wave with the given period. */
    static float pulse(long period, float phase) {
        return 0.5F + 0.5F * Mth.sin((System.currentTimeMillis() % period) / (float) period * Mth.TWO_PI + phase);
    }
}
