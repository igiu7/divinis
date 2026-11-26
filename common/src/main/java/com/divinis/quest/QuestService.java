package com.divinis.quest;

import java.util.HashMap;
import java.util.Map;

import com.divinis.Divinis;
import com.divinis.npc.GuildMaster;
import com.divinis.platform.DataKey;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.event.LivingDeathEvent;
import com.divinis.platform.event.PlayerEvent;
import com.divinis.platform.event.RegisterCommandsEvent;
import com.divinis.platform.net.PacketDistributor;
import com.divinis.net.QuestPayload;
import com.divinis.util.Errors;
import com.divinis.util.Msg;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Guild Master's quests on the server: taking one and turning it in (only next to a Guild Master), giving one up,
 * counting the mobs slain, the rewards (emeralds and character XP), and sending the player's quests to them.
 * /divinis quests accept|abandon|turnin <quest> - the quest screens (client.gui.GuildMasterScreen, QuestLogScreen) run these.
 */
@EventBusSubscriber
public final class QuestService {
    private static final double GUILD_MASTER_RANGE = 8.0;

    public static DataKey<QuestData> QUESTS;

    private QuestService() {}

    /** During the mod's start-up, with the other player data. */
    public static void register() {
        QUESTS = DataKey.create(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "quests"), QuestData::new, QuestData.CODEC, true);
    }

    public static QuestData data(ServerPlayer player) {
        return QUESTS.get(player);
    }

    private static void save(ServerPlayer player, QuestData data) {
        QUESTS.set(player, data);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        QuestData d = data(player);
        PacketDistributor.sendToPlayer(player, new QuestPayload(new HashMap<>(d.active), new HashMap<>(d.done)));
    }

    /** How far the player is with the quest: mobs slain, or the items they carry. */
    public static int progress(ServerPlayer player, Quest quest) {
        if (quest.kind() == Quest.Kind.SLAY) return data(player).active.getOrDefault(quest.id(), 0);
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (quest.item().test(s)) n += s.getCount();
        }
        return n;
    }

    private static void needGuildMaster(ServerPlayer player) throws CommandSyntaxException {
        if (player.level().getEntitiesOfClass(GuildMaster.class, player.getBoundingBox().inflate(GUILD_MASTER_RANGE)).isEmpty()) {
            throw Errors.of("Only the Guild Master can do that - go and talk to him.");
        }
    }

    private static Quest quest(String id) throws CommandSyntaxException {
        return Quests.byId(id).orElseThrow(() -> Errors.of("There is no quest " + id + "."));
    }

    // ------------------------------------------------------------------ actions

    public static void accept(ServerPlayer player, String id) throws CommandSyntaxException {
        Quest quest = quest(id);
        needGuildMaster(player);
        QuestData data = data(player);
        if (data.active.containsKey(id)) throw Errors.of("You are already on " + quest.name() + ".");
        if (data.active.size() >= Quests.MAX_ACTIVE) throw Errors.of("You can take at most " + Quests.MAX_ACTIVE + " quests at once.");
        data.active.put(id, 0);
        save(player, data);
        player.sendSystemMessage(Msg.text("Quest taken: " + quest.name() + " - " + quest.task() + ".", ChatFormatting.GOLD));
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static void abandon(ServerPlayer player, String id) throws CommandSyntaxException {
        Quest quest = quest(id);
        QuestData data = data(player);
        if (data.active.remove(id) == null) throw Errors.of("You are not on " + quest.name() + ".");
        save(player, data);
        player.sendSystemMessage(Msg.text("Quest given up: " + quest.name() + ".", ChatFormatting.GRAY));
    }

    public static void turnIn(ServerPlayer player, String id) throws CommandSyntaxException {
        Quest quest = quest(id);
        needGuildMaster(player);
        QuestData data = data(player);
        if (!data.active.containsKey(id)) throw Errors.of("You are not on " + quest.name() + ".");
        if (progress(player, quest) < quest.count()) throw Errors.of(quest.name() + " is not done yet: " + quest.task() + ".");
        if (quest.kind() == Quest.Kind.DELIVER) {
            int left = quest.count();
            for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (!quest.item().test(s)) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        data.active.remove(id);
        data.done.merge(id, 1, Integer::sum);
        save(player, data);
        ItemStack reward = new ItemStack(Items.EMERALD, quest.emeralds());
        if (!player.getInventory().add(reward)) player.drop(reward, false);
        com.divinis.skill.CharacterService.addXp(player, quest.xp(), "quest");
        player.sendSystemMessage(Msg.text("Quest complete: " + quest.name() + "! ", ChatFormatting.GREEN)
                .append(Component.literal("+" + quest.emeralds() + " emeralds, +" + quest.xp() + " XP").withStyle(ChatFormatting.GOLD)));
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        QuestData data = data(player);
        boolean changed = false;
        for (Map.Entry<String, Integer> e : data.active.entrySet()) {
            Quest quest = Quests.byId(e.getKey()).orElse(null);
            if (quest == null || quest.kind() != Quest.Kind.SLAY || event.getEntity().getType() != quest.mob().get()) continue;
            if (e.getValue() >= quest.count()) continue;
            int now = e.getValue() + 1;
            e.setValue(now);
            changed = true;
            player.displayClientMessage(Component.literal(quest.name() + "  ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(now + " / " + quest.count() + " " + quest.target()).withStyle(now >= quest.count() ? ChatFormatting.GREEN : ChatFormatting.GRAY)), true);
            if (now >= quest.count()) {
                player.sendSystemMessage(Msg.text(quest.name() + " is done - turn it in at the Guild Master.", ChatFormatting.GREEN));
                player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
            }
        }
        if (changed) save(player, data);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("divinis").then(Commands.literal("quests")
                .then(Commands.literal("accept").then(questArg().executes(c -> run(c, QuestService::accept))))
                .then(Commands.literal("abandon").then(questArg().executes(c -> run(c, QuestService::abandon))))
                .then(Commands.literal("turnin").then(questArg().executes(c -> run(c, QuestService::turnIn))))));
    }

    @FunctionalInterface
    private interface Action { void run(ServerPlayer player, String id) throws CommandSyntaxException; }

    private static int run(CommandContext<CommandSourceStack> ctx, Action action) throws CommandSyntaxException {
        action.run(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "quest"));
        return 1;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> questArg() {
        return Commands.argument("quest", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Quests.ALL.stream().map(Quest::id), b));
    }
}
