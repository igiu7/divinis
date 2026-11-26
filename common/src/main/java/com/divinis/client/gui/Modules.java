package com.divinis.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;

/** The modules shown on the rail of every {@link ModuleScreen}, in order. */
public final class Modules {
    /** A module: its id (what its screen returns from moduleId), name and icon on the rail, and how to open it. */
    public record Module(String id, String name, Icon icon, Supplier<? extends ModuleScreen> open) {}

    private static final List<Module> ALL = new ArrayList<>();

    static {
        register(new Module("skills", "Skills", Icons.BOOK, SkillTreeScreen::new));
    }

    private Modules() {}

    public static void register(Module module) {
        ALL.removeIf(m -> m.id().equals(module.id()));
        ALL.add(module);
    }

    public static List<Module> all() {
        return Collections.unmodifiableList(ALL);
    }
}
