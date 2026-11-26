package com.divinis.skill;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.divinis.net.SkillPayloads;
import com.divinis.Divinis;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.PlayerEvent;
import com.divinis.platform.net.PacketDistributor;

/**
 * Dash, the first skill of Dexterity (its second orb, at ORB_POINTS points): a dive into a forward roll, up again
 * running, about five blocks on. The client moves the player and plays the animation (client.DashClient, made in
 * tools/models/build_dash.py) when the key is pressed; the server checks the skill and the cooldown and shows the
 * dash to everyone around.
 */
@EventBusSubscriber(modid = Divinis.MODID)
public final class Dash {
    public static final int ORB_INDEX = 1;
    public static final int ORB_POINTS = (ORB_INDEX + 1) * SkillTree.POINTS_PER_ORB;
    public static final int COOLDOWN = 80; // ticks
    private static final int LAG = 8;      // ticks the server forgives (packets do not arrive evenly)

    private static final Map<UUID, Long> LAST = new HashMap<>();

    private Dash() {}

    static SkillOrb orb() {
        return new SkillOrb(ORB_POINTS, "Dash", true, List.of(),
                List.of("Dash: dive into a forward roll, about 5 blocks", "Key: Dash (set it in Controls)", "Cooldown: " + COOLDOWN / 20 + " s"),
                "dash");
    }

    public static boolean unlocked(CharacterData data) {
        return data.points(SkillTree.DEX) >= ORB_POINTS;
    }

    public static void use(ServerPlayer player) {
        if (!unlocked(CharacterService.data(player))) {
            player.displayClientMessage(Component.literal("You have not unlocked Dash (" + ORB_POINTS + " points in Dexterity).")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isFallFlying()
                || player.isSwimming() || player.isSleeping()) return;
        long now = player.level().getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < COOLDOWN - LAG) return;
        LAST.put(player.getUUID(), now);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SkillPayloads.Dash(player.getId()));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.35F, 1.7F);
    }

    /** Tells the client its points and book (it shows the skills it may use; Perks). */
    public static void sync(ServerPlayer player) {
        CharacterData data = CharacterService.data(player);
        List<Integer> points = new java.util.ArrayList<>();
        for (int p : data.points) points.add(p);
        PacketDistributor.sendToPlayer(player, new SkillPayloads.Points(points, data.book));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
            com.divinis.net.DivinisNet.sendCharacter(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }
}
