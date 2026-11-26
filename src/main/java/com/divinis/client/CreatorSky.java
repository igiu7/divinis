package com.divinis.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * The sky slowing to a stop round the Nameless Creator's first phase (mixin.LevelRendererSkyMixin): the sun, the moon,
 * the stars and the clouds run slower and slower until they stand still (some ten seconds); when it is gone they run
 * fast until they have caught up with the world's real time. Round Chronos' hand (its third phase) they race round
 * instead, the sun and the moon whirling across the sky, and run back to the right time after. Only what is drawn:
 * the day itself goes on.
 */
public final class CreatorSky {
    private CreatorSky() {}

    private static final float STOP_TICKS = 200, CATCH_UP = 3, SPIN = 300, SPIN_BACK = 900;
    /** how fast the sky runs now (1: as the world, 0: still) */
    private static float rate = 1;
    /** how many ticks the sky is behind the world */
    private static float lag;
    private static int cloudTicks;

    /** Each client tick: whether a Creator is stopping the sky, or spinning it. */
    public static void tick(boolean stopping, boolean spinning) {
        if (spinning) {
            rate = 1;
            lag -= SPIN;
        } else if (lag < 0) {
            lag = Math.min(0, lag + SPIN_BACK);
        } else if (stopping) {
            rate = Math.max(0, rate - 1 / STOP_TICKS);
            lag += 1 - rate;
        } else {
            rate = 1;
            lag = Math.max(0, lag - CATCH_UP);
        }
    }

    /** The sun's place in the sky (as ClientLevel.getTimeOfDay, but behind by the lag). */
    public static float timeOfDay(ClientLevel level, float partialTick) {
        if (lag == 0) return level.getTimeOfDay(partialTick);
        return level.dimensionType().timeOfDay(level.getDayTime() - (long) lag);
    }

    /** The clouds' clock: called with the real ticks and partial tick; gives the partial tick (the whole ticks: cloudTicks()). */
    public static float cloudPartial(int ticks, float partialTick) {
        if (lag == 0 && rate >= 1) {
            cloudTicks = ticks;
            return partialTick;
        }
        float t = ticks + partialTick * rate - lag;
        cloudTicks = Mth.floor(t);
        return t - cloudTicks;
    }

    public static int cloudTicks() {
        return cloudTicks;
    }
}
