package com.divinis.client;

import com.divinis.net.CharacterPayload;

import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import net.minecraft.client.Minecraft;

/** The player's character as the server last sent it (CharacterPayload). */
public final class ClientCharacter {
    private static CharacterPayload character = new CharacterPayload(1, 0, 0, 0, new int[0], "", 0);
    private static long receivedAt;

    private ClientCharacter() {}

    public static CharacterPayload get() {
        return character;
    }

    /** When the snapshot arrived (System.currentTimeMillis). */
    public static long receivedAt() {
        return receivedAt;
    }

    public static void receive(CharacterPayload payload) {
        character = payload;
        receivedAt = System.currentTimeMillis();
        if (Minecraft.getInstance().screen instanceof ScreenWrapper wrapper
                && wrapper.getGui() instanceof com.divinis.client.gui.SkillTreeScreen skills) {
            skills.onStateChanged();
        }
    }
}
