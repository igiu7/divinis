package com.divinis.screenfx;

import java.util.Locale;

import javax.annotation.Nullable;

/**
 * The screen effects a boss (or /screenfx) can put on a player's screen for a while (client.ScreenFxClient draws
 * them). Strength is 0..1 (more makes most of them stronger still). All of them are drawn on top of the finished
 * frame, after any shader pack, or through the camera, so they work with or without shaders.
 *
 * Overlays: FLASH (a blinding flash, color), EYES (eyes opening all over the screen), MOUTHS (mouths opening all over
 * the screen, screaming), VIGNETTE (a coloured edge, color), TEXT (words across the screen, color), SANDSTORM (sand
 * blowing across the screen in drifts and streaks, the air thick with dust, color), WEB (threads of web stuck over
 * the screen from its edges, trembling, color).
 * Camera and world: SHAKE, TILT (the view rolls over), FOV (the view widens), DARKNESS (darkness closes in), FOG (a
 * coloured fog, color), SKY (a giant eye and stars in the sky).
 * Image (a pass over the whole picture): INVERT, BLUR, ABERRATION (the colours split), DISTORT (the picture waves),
 * DESATURATE (black and white; color = a colour to keep), GLITCH, NOISE, PIXELATE; and, only with an anchor on a
 * thing, OUTLINE (a glowing outline round it, color), AURA (a blur round its shape, like a haze about it) and
 * HEAT (the air shimmers over and round it); TIMESTOP (time stands still: the colour drained, a cold tint, a grain that
 * does not move, dark edges), SHATTER (the picture breaks like glass, the pieces shifted and turned, their edges bright),
 * GLOW (a soft glow of its colour over everything bright), REWIND (time running backwards: sepia, a running grain, the
 * colours split the other way). A thing's shape takes in the beams (client.BeamFx) and boss flames
 * (client.FireFx) too: EXCEPT leaves them as they are with the thing, HEAT makes the air over the flames shimmer.
 *
 * Where (Anchor): on the whole screen; in a circle round a thing (AREA, radius in blocks, following it); only on
 * a thing's shape (ENTITY); or everywhere but on it (EXCEPT). The image effects and FLASH (then a light on just
 * that part) take an anchor; the others are always on the whole screen.
 */
public enum ScreenEffect {
    FLASH(0xFFFFFF), EYES(-1), VIGNETTE(0x6A1FA8), TEXT(0xFFFFFF),
    SHAKE(-1), TILT(-1), FOV(-1), DARKNESS(0x000000), FOG(0x5A2A80), SKY(-1),
    INVERT(-1), BLUR(-1), ABERRATION(-1), DISTORT(-1), DESATURATE(-1), GLITCH(-1), NOISE(-1), PIXELATE(-1),
    OUTLINE(0xB060FF), AURA(-1), HEAT(-1), MOUTHS(-1), SANDSTORM(0xC89A58), WEB(0xF0EEE8),
    TIMESTOP(-1), SHATTER(-1), GLOW(0xFFC060), REWIND(-1);

    public enum Anchor { SCREEN, AREA, ENTITY, EXCEPT }

    /** the colour it uses when none is given (-1: none) */
    public final int defaultColor;

    ScreenEffect(int defaultColor) {
        this.defaultColor = defaultColor;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static ScreenEffect byId(String id) {
        for (ScreenEffect e : values()) if (e.id().equals(id)) return e;
        return null;
    }

    @Nullable
    public static ScreenEffect byIndex(int i) {
        return i >= 0 && i < values().length ? values()[i] : null;
    }
}
