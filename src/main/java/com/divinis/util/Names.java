package com.divinis.util;

public final class Names {
    private Names() {}

    /** A short duration: "1h 5m", "12m", "40s", "instant". */
    public static String duration(int seconds) {
        if (seconds <= 0) return "instant";
        int h = seconds / 3600, m = (seconds % 3600) / 60;
        if (h > 0) return h + "h" + (m > 0 ? " " + m + "m" : "");
        if (m > 0) return m + "m";
        return seconds + "s";
    }
}
