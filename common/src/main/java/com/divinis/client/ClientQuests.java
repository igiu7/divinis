package com.divinis.client;

import java.util.Map;

import com.divinis.net.QuestPayload;
import com.divinis.quest.Quest;

import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** The player's quests as the server last sent them (QuestPayload). */
public final class ClientQuests {
    private static QuestPayload quests = new QuestPayload(Map.of(), Map.of());

    private ClientQuests() {}

    public static boolean active(Quest quest) {
        return quests.active().containsKey(quest.id());
    }

    public static int activeCount() {
        return quests.active().size();
    }

    public static int done(Quest quest) {
        return quests.done().getOrDefault(quest.id(), 0);
    }

    public static int totalDone() {
        int sum = 0;
        for (int n : quests.done().values()) sum += n;
        return sum;
    }

    /** Mobs slain, or the items carried right now (as the server counts them on turn-in). */
    public static int progress(Quest quest) {
        if (quest.kind() == Quest.Kind.SLAY) return quests.active().getOrDefault(quest.id(), 0);
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (quest.item().test(s)) n += s.getCount();
        }
        return n;
    }

    public static void receive(QuestPayload payload) {
        quests = payload;
        if (Minecraft.getInstance().screen instanceof ScreenWrapper wrapper
                && wrapper.getGui() instanceof com.divinis.client.gui.QuestScreen screen) {
            screen.onStateChanged();
        }
    }
}
