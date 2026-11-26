package com.divinis.client.gui;

import java.util.List;

import com.divinis.client.ClientQuests;
import com.divinis.net.GuildMasterOpenPayload;
import com.divinis.quest.Quest;
import com.divinis.quest.Quests;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Guild Master's quests (right-click on npc.GuildMaster): on the left the Guild Master himself, then all his
 * quests, the one picked in detail with Accept, Turn in and Give up.
 */
public final class GuildMasterScreen extends QuestScreen {
    private static final int CARD_W = 132;
    private static String selectedId = "";

    private final GuildMasterOpenPayload data;

    public GuildMasterScreen(GuildMasterOpenPayload data) {
        this.data = data;
    }

    @Override
    protected String moduleId() {
        return "guild";
    }

    @Override
    protected String title() {
        return "Guild Master";
    }

    @Override
    protected String subtitle() {
        return "What brings you to the guild, traveller?";
    }

    @Override
    protected Icon icon() {
        return ItemIcon.getItemIcon(Items.GOLDEN_HELMET);
    }

    @Override
    protected String hint() {
        return "Take up to " + Quests.MAX_ACTIVE + " quests at once. Turn them in here when they are done.";
    }

    @Override
    protected List<Quest> quests() {
        return Quests.ALL;
    }

    @Override
    protected String selectedId() {
        return selectedId;
    }

    @Override
    protected void setSelectedId(String id) {
        selectedId = id;
    }

    @Override
    protected int cardW() {
        return CARD_W;
    }

    @Override
    protected String listTitle() {
        return "GUILD QUESTS";
    }

    @Override
    protected void drawHeaderExtras(GuiGraphics g, Theme theme, int x, int y, int right) {
        String active = "Active " + ClientQuests.activeCount() + " / " + Quests.MAX_ACTIVE;
        int w = theme.getStringWidth(active) + 16, px = right - w;
        boolean full = ClientQuests.activeCount() >= Quests.MAX_ACTIVE;
        round(g, px, y + 12, w, 16, (full ? Ui.ORANGE : VIOLET).withAlpha(45));
        roundOutline(g, px, y + 12, w, 16, (full ? Ui.ORANGE : VIOLET_LIGHT).withAlpha(170));
        theme.drawString(g, active, px + w / 2, y + 16, TEXT, Theme.CENTERED | Theme.SHADOW);
    }

    @Override
    protected void addButtons(Quest q, int right, int y) {
        boolean active = ClientQuests.active(q);
        if (!active) {
            boolean room = ClientQuests.activeCount() < Quests.MAX_ACTIVE;
            Pill accept = new Pill(this, () -> room ? "Accept quest" : "Too many quests", VIOLET, () -> room,
                    () -> command("divinis quests accept " + q.id()))
                    .tooltip(list -> {
                        if (!room) list.add(Component.literal("You can take at most " + Quests.MAX_ACTIVE + " quests at once").withStyle(ChatFormatting.RED));
                    });
            accept.setPosAndSize(right - 110, y, 110, 16);
            add(accept);
            return;
        }
        Pill turnIn = new Pill(this, () -> ready(q) ? "Turn in" : "Not done yet", QuestScreen.GREEN, () -> ready(q),
                () -> command("divinis quests turnin " + q.id()))
                .tooltip(list -> list.add(Component.literal("Reward: " + q.emeralds() + " emeralds, " + q.xp() + " XP").withStyle(ChatFormatting.GOLD)));
        turnIn.setPosAndSize(right - 100, y, 100, 16);
        add(turnIn);
        Pill giveUp = new Pill(this, "Give up", Ui.RED, () -> giveUp(q));
        giveUp.setPosAndSize(right - 176, y, 70, 16);
        add(giveUp);
    }

    // ------------------------------------------------------------------ the card

    private int portraitH() {
        return Mth.clamp(bodyH() - 40, 60, 170);
    }

    @Override
    protected void drawCard(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
        inset(g, x, y, w, h);
        int px = x + 6, py = y + 6, pw = w - 12, ph = portraitH();
        g.fillGradient(px + 1, py + 1, px + pw - 1, py + ph - 1, 0x10000000, VIOLET_DEEP.withAlpha(150).rgba());
        g.flush();
        GuiHelper.setupDrawing();
        float p = Anim.pulse(3000, 0F);
        VIOLET.withAlpha((int) (40 + 30 * p)).draw(g, px + pw / 2 - 26, py + ph - 8, 52, 3);
        roundOutline(g, px, py, pw, ph, VIOLET.withAlpha(120));
        var level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(data.entityId()) instanceof LivingEntity master) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + 1, py + 1, px + pw - 1, py + ph - 1, (int) (ph * 0.36F), 0.0625F,
                    getMouseX(), getMouseY(), master);
        } else {
            ItemIcon.getItemIcon(new ItemStack(Items.GOLDEN_HELMET)).draw(g, px + pw / 2 - 16, py + ph / 2 - 16, 32, 32);
        }
        GuiHelper.setupDrawing();
        VIOLET.draw(g, px + 1, py + ph - 2, pw - 2, 1);

        int ty = py + ph + 6;
        theme.drawString(g, Component.literal("GUILD MASTER").withStyle(ChatFormatting.BOLD), x + w / 2, ty, TEXT, Theme.CENTERED | Theme.SHADOW);
        int done = ClientQuests.totalDone();
        theme.drawString(g, done + (done == 1 ? " quest done" : " quests done"), x + w / 2, ty + 11, VIOLET_LIGHT, Theme.CENTERED);
    }
}
