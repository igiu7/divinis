package com.divinis.city;

import java.util.List;

import com.divinis.Config;
import com.divinis.Divinis;
import com.divinis.platform.DataKey;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.event.EntityJoinLevelEvent;
import com.divinis.platform.event.FinalizeSpawnEvent;
import com.divinis.platform.event.PlayerEvent;
import com.divinis.platform.event.RegisterCommandsEvent;
import com.divinis.platform.event.ServerStartedEvent;
import com.divinis.platform.event.ServerStoppedEvent;
import com.divinis.platform.event.ServerTickEvent;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The starting city: built the first time the server starts (CityPlan, a few steps every tick, going on after a
 * restart), then the place where players join for the first time and respawn, without hostile mobs inside its walls.
 * /divinis admin city status | tp | rebuild | here  - admins (permission level 2).
 */
@EventBusSubscriber
public final class CityService {
    private static final long BUDGET_NANOS = 30_000_000L; // per tick
    private static final String CULL = "divinis_city_cull";
    private static final long NEW_WORLD_TICKS = 1200; // a world younger than this (game time) is a new one

    /** Whether the player has been brought to the city (on their first join, or once it was finished). */
    public static DataKey<Boolean> ARRIVED;

    private static List<CityPlan.Step> steps;
    private static int lastPercent = -1;

    private CityService() {}

    /** During the mod's start-up, with the other player data. */
    public static void register() {
        ARRIVED = DataKey.create(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "city_arrived"), () -> false, Codec.BOOL, true);
    }

    /** Where players appear: on the square, south of the fountain, facing north. */
    public static BlockPos spawn(CityData data) {
        return data.origin().offset(0, 0, 10);
    }

    public static boolean built(MinecraftServer server) {
        return CityData.get(server).state() == CityData.State.BUILT;
    }

    // ------------------------------------------------------------------ building

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        CityData data = CityData.get(server);
        if (data.state == CityData.State.BUILDING) {
            start(server, null);
        } else if (data.state == CityData.State.NONE && Config.CITY_BUILD.get()) {
            // only in a new world: in one already played, levelling the land round the spawn could flatten players' builds
            if (server.overworld().getGameTime() < NEW_WORLD_TICKS) {
                start(server, null);
            } else {
                Divinis.LOGGER.info("The world has been played already, so the starting city is not built by itself; "
                        + "an admin can build it with /divinis admin city here");
            }
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        steps = null;
    }

    /** Starts (or goes on with) building; at: a new place for it (null: where it was, or a place chosen now). */
    private static void start(MinecraftServer server, BlockPos at) {
        ServerLevel level = server.overworld();
        CityData data = CityData.get(server);
        if (at != null || data.origin == null) {
            data.origin = at != null ? at : Site.choose(level);
            data.seed = level.getSeed() ^ data.origin.asLong();
            data.step = 0;
            data.spots.clear();
        }
        data.state = CityData.State.BUILDING;
        data.setDirty();
        steps = CityPlan.steps(level, data.origin, data.seed, spot -> {
            data.spots.removeIf(s -> s.role().equals(spot.role()));
            data.spots.add(spot);
            data.setDirty();
        });
        lastPercent = -1;
        Divinis.LOGGER.info("Building the starting city at {} ({} of {} steps done)", data.origin.toShortString(), data.step, steps.size());
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (steps == null) return;
        MinecraftServer server = event.getServer();
        CityData data = CityData.get(server);
        long until = System.nanoTime() + BUDGET_NANOS;
        while (data.step < steps.size() && System.nanoTime() < until) {
            CityPlan.Step step = steps.get(data.step);
            try {
                step.run().run();
            } catch (Exception e) {
                Divinis.LOGGER.error("The city: step {} ({}) failed", data.step, step.what(), e);
            }
            data.step++;
            data.setDirty();
        }
        int percent = data.step * 100 / steps.size();
        if (percent / 10 != lastPercent / 10 && data.step < steps.size()) {
            lastPercent = percent;
            String what = steps.get(Math.min(data.step, steps.size() - 1)).what();
            Divinis.LOGGER.info("The starting city: {}% ({})", percent, what);
            server.getPlayerList().broadcastSystemMessage(Component.literal("The city is being built: " + percent + "% - " + what + "...")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        if (data.step >= steps.size()) finish(server, data);
    }

    private static void finish(MinecraftServer server, CityData data) {
        steps = null;
        data.state = CityData.State.BUILT;
        data.setDirty();
        ServerLevel level = server.overworld();
        level.setDefaultSpawnPos(spawn(data), 180F);
        CityData.Spot guild = data.spot("guild_master");
        if (guild != null && level.getEntitiesOfClass(com.divinis.npc.GuildMaster.class, new AABB(guild.pos()).inflate(6)).isEmpty()) {
            var master = com.divinis.registry.ModEntities.GUILD_MASTER.get().create(level);
            if (master != null) {
                float yaw = Direction.from2DDataValue(guild.facing()).toYRot();
                master.moveTo(guild.pos().getX() + 0.5, guild.pos().getY(), guild.pos().getZ() + 0.5, yaw, 0F);
                master.setYHeadRot(yaw);
                master.setPersistenceRequired();
                level.addFreshEntity(master);
            }
        }
        server.saveEverything(true, true, true); // a crash now would lose minutes of building
        Divinis.LOGGER.info("The starting city is built");
        server.getPlayerList().broadcastSystemMessage(Component.literal("The city is built.").withStyle(ChatFormatting.GOLD), false);
        if (Config.CITY_RESPAWN.get()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!ARRIVED.get(p)) arrive(p, data);
            }
        }
    }

    // ------------------------------------------------------------------ players

    private static void toSquare(ServerPlayer player, CityData data) {
        BlockPos p = spawn(data);
        player.teleportTo(player.server.overworld(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 180F, 0F);
    }

    private static void arrive(ServerPlayer player, CityData data) {
        ARRIVED.set(player, true);
        toSquare(player, data);
        player.sendSystemMessage(Component.literal("Welcome to the city. The Guild Master in the Guild Hall, north of the square, has work for you.")
                .withStyle(ChatFormatting.GOLD));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Config.CITY_RESPAWN.get()) return;
        CityData data = CityData.get(player.server);
        if (data.state() == CityData.State.BUILT && !ARRIVED.get(player)) arrive(player, data);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered() || !Config.CITY_RESPAWN.get()) return;
        CityData data = CityData.get(player.server);
        if (data.state() == CityData.State.BUILT) toSquare(player, data);
    }

    // ------------------------------------------------------------------ no hostile mobs inside

    private static boolean inside(Level level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD || level.getServer() == null) return false;
        CityData data = CityData.get(level.getServer());
        BlockPos o = data.origin();
        if (o == null || data.state() == CityData.State.NONE) return false;
        return Math.abs(pos.getX() - o.getX()) <= CityPlan.WALL + 8 && Math.abs(pos.getZ() - o.getZ()) <= CityPlan.WALL + 8;
    }

    @SubscribeEvent
    public static void onSpawn(FinalizeSpawnEvent event) {
        if (!Config.CITY_SAFE.get() || event.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION && type != MobSpawnType.PATROL
                && type != MobSpawnType.REINFORCEMENT) return;
        if (inside(event.getEntity().level(), event.getEntity().blockPosition())) event.getEntity().addTag(CULL);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity().getTags().contains(CULL)) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ commands

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("divinis").then(Commands.literal("admin").then(Commands.literal("city")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("status").executes(CityService::status))
                .then(Commands.literal("tp").executes(c -> {
                    CityData data = CityData.get(c.getSource().getServer());
                    if (data.origin() == null) {
                        c.getSource().sendFailure(Component.literal("There is no city yet."));
                        return 0;
                    }
                    toSquare(c.getSource().getPlayerOrException(), data);
                    return 1;
                }))
                .then(Commands.literal("rebuild").executes(c -> {
                    MinecraftServer server = c.getSource().getServer();
                    CityData data = CityData.get(server);
                    data.step = 0;
                    start(server, null);
                    c.getSource().sendSuccess(() -> Component.literal("Rebuilding the city where it stands."), true);
                    return 1;
                }))
                .then(Commands.literal("here").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    start(c.getSource().getServer(), p.blockPosition());
                    c.getSource().sendSuccess(() -> Component.literal("Building the city here, round " + p.blockPosition().toShortString() + "."), true);
                    return 1;
                })))));
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        CityData data = CityData.get(c.getSource().getServer());
        String where = data.origin() == null ? "nowhere yet" : data.origin().toShortString();
        String how = switch (data.state()) {
            case NONE -> "not built";
            case BUILT -> "built";
            case BUILDING -> "being built, step " + data.step + (steps != null ? " of " + steps.size() : "");
        };
        c.getSource().sendSuccess(() -> Component.literal("The city: " + how + ", at " + where + ", " + data.spots().size() + " places for NPCs."), false);
        return 1;
    }
}
