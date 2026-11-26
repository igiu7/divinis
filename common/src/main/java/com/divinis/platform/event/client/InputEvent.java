package com.divinis.platform.event.client;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;

public abstract class InputEvent extends Event {
    /** A key was pressed, held or let go (GLFW key, scan code, action and modifiers). */
    public static class Key extends InputEvent {
        private final int key, scanCode, action, modifiers;

        public Key(int key, int scanCode, int action, int modifiers) {
            this.key = key;
            this.scanCode = scanCode;
            this.action = action;
            this.modifiers = modifiers;
        }

        public int getKey() { return key; }
        public int getScanCode() { return scanCode; }
        public int getAction() { return action; }
        public int getModifiers() { return modifiers; }
    }
}
