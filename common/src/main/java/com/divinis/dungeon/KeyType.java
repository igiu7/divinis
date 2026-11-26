package com.divinis.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.util.StringRepresentable;

/** The keys of the Labyrinth and the locks they open. */
public enum KeyType implements StringRepresentable {
    BRONZE("bronze", "Bronze Key", "the bronze vault", ChatFormatting.GOLD),
    SILVER("silver", "Silver Key", "the silver vault", ChatFormatting.GRAY),
    GOLD("gold", "Minotaur Key", "the Minotaur's hall", ChatFormatting.YELLOW);

    final String id;
    final String title;
    final String opens;
    final ChatFormatting color;

    KeyType(String id, String title, String opens, ChatFormatting color) {
        this.id = id;
        this.title = title;
        this.opens = opens;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
