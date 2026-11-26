package com.divinis.client;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What the anvil may forge (ForgeKnownPayload): the technologies the player knows; null = the world has none, all may. */
public final class ClientForge {
    private static Set<String> known;
    private static Map<String, String> techNames = Map.of();

    private ClientForge() {}

    public static void setKnown(List<String> techs, boolean all, Map<String, String> names) {
        known = all ? null : new HashSet<>(techs);
        techNames = Map.copyOf(names);
    }

    public static boolean knows(String tech) {
        return known == null || known.contains(tech);
    }

    public static String techName(String tech) {
        return techNames.getOrDefault(tech, tech);
    }
}
