package com.divinis.dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.divinis.boss.Minotaur;
import com.divinis.registry.ModEntities;
import com.divinis.util.Msg;
import com.divinis.Divinis;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dungeon runs. The Guild Master (or an admin) opens a portal for 30 seconds; up to 5 players who walk in form the
 * party. Every run gets its own slot of the dungeon world (dimension divinis:dungeons, empty void), far from the
 * others, where its dungeon is built: the Labyrinth (Labyrinth, LabyrinthBuilder) - a maze of rooms with monsters,
 * Create puzzles that give keys, locked vaults full of bronze age loot and, behind the Minotaur Key's gate, the
 * Minotaur's hall (ArenaLayout).
 * - The boss scales with the party (DungeonScaling); there are 30 minutes to kill it.
 * - Killing it puts a reward chest (everyone gets his own loot) in the hall and enters the party's time in the
 *   ranking (DungeonData); the exit portal opens at the side of the hall once everyone still inside has opened the
 *   chest at least once.
 * - Dying inside does not kill: the player is dragged out with full health and keeps his things.
 * - Players are in adventure mode inside (back to their own mode when they leave): nothing can be built, broken or
 *   re-set; no water or lava poured; ender pearls and chorus fruit do not work.
 * - Monsters there drop Labyrinth Monster Crystals.
 * When everyone has left (or the time is up, or 5 minutes after the clear) the slot is cleaned and freed.
 */
@EventBusSubscriber(modid = Divinis.MODID)
public final class DungeonService {
    private DungeonService() {}

    public static final ResourceKey<Level> WORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "dungeons"));

    public static final int MAX_PARTY = 5;
    static final int PORTAL_TICKS = 30 * 20;
    static final int TIME_LIMIT = 30 * 60 * 20;
    static final int AFTER_CLEAR = 5 * 60 * 20;
    static final int SLOT_SPACING = 2048;
    static final int FLOOR_Y = 64;
    static final float CRYSTAL_CHANCE = 0.3F;
    private static final String RETURN_TAG = "divinis_dungeon_return";
    /** custom data of Labyrinth keys: the run they were found in */
    static final String RUN_TAG = "divinis_run";
    /** player -> server tick until which portals ignore him (just went through one); kept in memory only, as the
     *  server's tick count starts again from 0 after a restart */
    private static final Map<UUID, Long> PORTAL_COOLDOWN = new HashMap<>();

    /** One run of a dungeon. */
    static final class Run {
        final int id, slot;
        final BlockPos origin;
        @Nullable final UUID guildMaster;
        final Map<UUID, String> members = new LinkedHashMap<>(); // everyone who went in, with his name
        final Set<UUID> present = new HashSet<>();               // who is inside now
        final ServerBossEvent bar = new ServerBossEvent(Component.literal("Labyrinth"), BossEvent.BossBarColor.BLUE,
                BossEvent.BossBarOverlay.PROGRESS);
        Labyrinth lab;
        @Nullable DungeonPortal entrance, exit;
        boolean portalOpen = true;
        long start = -1;
        @Nullable UUID boss;
        boolean cleared;
        long clearedAt;
        /** who has opened the boss's reward chest */
        final Set<UUID> looted = new HashSet<>();

        Run(int id, int slot, BlockPos origin, @Nullable UUID guildMaster) {
            this.id = id;
            this.slot = slot;
            this.origin = origin;
            this.guildMaster = guildMaster;
        }

        BoundingBox bounds() {
            return Labyrinth.boundsAt(origin);
        }

        Vec3 at(Vec3 local) {
            return new Vec3(origin.getX() + local.x, origin.getY() + local.y, origin.getZ() + local.z);
        }
    }

    private static final Map<Integer, Run> RUNS = new HashMap<>();
    private static int nextId;

    public static boolean isDungeon(Level level) {
        return level.dimension() == WORLD;
    }

    private static long now(MinecraftServer server) {
        return server.getTickCount();
    }

    @Nullable
    private static ServerLevel world(MinecraftServer server) {
        return server.getLevel(WORLD);
    }

    /** The start of the Labyrinth run the player is in (the Dungeon Scout's Escape). */
    public record Start(int runId, Vec3 at) {}

    public static java.util.Optional<Start> startOf(ServerPlayer player) {
        Run run = runOf(player);
        if (run == null || run.lab == null || !isDungeon(player.level())) return java.util.Optional.empty();
        return java.util.Optional.of(new Start(run.id, run.lab.spawn()));
    }

    @Nullable
    private static Run runOf(ServerPlayer player) {
        for (Run r : RUNS.values()) if (r.present.contains(player.getUUID())) return r;
        return null;
    }

    private static String time(long ticks) {
        long s = Math.max(0, ticks) / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------------ opening

    /** The Guild Master opens a portal next to him for the player who asked. */
    public static void openFromGuildMaster(ServerPlayer player, Entity guildMaster) {
        if (!guildMaster.isAlive() || guildMaster.level() != player.level() || guildMaster.distanceToSqr(player) > 10 * 10) return;
        for (Run r : RUNS.values()) {
            if (guildMaster.getUUID().equals(r.guildMaster) && r.portalOpen) {
                player.displayClientMessage(Msg.text("A portal is already open - hurry in!", ChatFormatting.GOLD), true);
                return;
            }
        }
        // beside the Guild Master, on the side away from his right hand, facing the player
        Vec3 toPlayer = player.position().subtract(guildMaster.position()).multiply(1, 0, 1);
        if (toPlayer.lengthSqr() < 0.01) toPlayer = new Vec3(0, 0, 1);
        toPlayer = toPlayer.normalize();
        Vec3 side = new Vec3(-toPlayer.z, 0, toPlayer.x);
        Vec3 at = guildMaster.position().add(side.scale(3.5)).add(toPlayer.scale(0.5));
        float yaw = (float) (Mth.atan2(toPlayer.z, toPlayer.x) * Mth.RAD_TO_DEG) - 90.0F;
        open(player, (ServerLevel) player.level(), at, yaw, guildMaster.getUUID());
    }

    /** Opens a portal at a place (Guild Master or admin command). Returns false if the dungeon world is missing. */
    public static boolean open(ServerPlayer opener, ServerLevel level, Vec3 at, float yaw, @Nullable UUID guildMaster) {
        MinecraftServer server = level.getServer();
        ServerLevel world = world(server);
        if (world == null) {
            opener.sendSystemMessage(Msg.text("The dungeon world is missing (datapack divinis:dungeons).", ChatFormatting.RED));
            return false;
        }
        int slot = 0;
        Set<Integer> used = new HashSet<>();
        for (Run r : RUNS.values()) used.add(r.slot);
        while (used.contains(slot)) slot++;
        Run run = new Run(++nextId, slot, new BlockPos(slot * SLOT_SPACING + SLOT_SPACING / 2, FLOOR_Y, 0), guildMaster);
        DungeonData.get(server).markSlot(slot, true);
        run.lab = Labyrinth.generate(run.origin, run.id, world.getRandom());
        LabyrinthBuilder.build(world, run.lab, world.getRandom());
        RUNS.put(run.id, run);

        DungeonPortal portal = ModEntities.DUNGEON_PORTAL.get().create(level);
        if (portal == null) return false;
        portal.moveTo(at.x, at.y, at.z, yaw, 0);
        portal.setRemaining(PORTAL_TICKS);
        portal.instanceId = run.id;
        level.addFreshEntity(portal);
        run.entrance = portal;
        level.playSound(null, at.x, at.y, at.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.NEUTRAL, 1.0F, 1.2F);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at) < 40 * 40) {
                p.sendSystemMessage(Msg.text("A portal to the Labyrinth has opened! It closes in 30 seconds (up to "
                        + MAX_PARTY + " players).", ChatFormatting.LIGHT_PURPLE));
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ going in and out

    /** A player stands in a portal. */
    static void touched(ServerPlayer player, DungeonPortal portal) {
        long now = now(player.getServer());
        if (PORTAL_COOLDOWN.getOrDefault(player.getUUID(), 0L) > now) return;
        PORTAL_COOLDOWN.put(player.getUUID(), now + 40);
        Run run = RUNS.get(portal.instanceId);
        if (run == null) return;
        if (portal.exit) {
            if (run.present.contains(player.getUUID())) leave(player, run, "You leave the labyrinth.");
            return;
        }
        if (isDungeon(player.level()) || runOf(player) != null) return;
        if (run.members.size() >= MAX_PARTY && !run.members.containsKey(player.getUUID())) {
            player.displayClientMessage(Msg.text("The party is full (" + MAX_PARTY + " players).", ChatFormatting.RED), true);
            return;
        }
        enter(player, run);
    }

    private static void enter(ServerPlayer player, Run run) {
        MinecraftServer server = player.getServer();
        ServerLevel world = world(server);
        if (world == null) return;
        CompoundTag back = new CompoundTag();
        back.putString("dim", player.level().dimension().location().toString());
        back.putDouble("x", player.getX());
        back.putDouble("y", player.getY());
        back.putDouble("z", player.getZ());
        back.putFloat("yaw", player.getYRot());
        back.putInt("mode", player.gameMode.getGameModeForPlayer().getId());
        player.getPersistentData().put(RETURN_TAG, back);

        boolean first = run.start < 0;
        run.members.put(player.getUUID(), player.getGameProfile().getName());
        run.present.add(player.getUUID());
        if (first) {
            run.start = now(server);
            spawnBoss(world, run);
        }
        if (run.boss != null && world.getEntity(run.boss) instanceof LivingEntity boss) DungeonScaling.apply(boss, run.members.size());

        int n = run.members.size() - 1;
        Vec3 spawn = run.lab.spawn().add((n % 3 - 1) * 1.5, 0, n / 3 * 1.5);
        player.teleportTo(world, spawn.x, spawn.y, spawn.z, 180, 0); // facing north, towards the Minotaur's hall
        // adventure mode inside: nothing can be broken, placed, wrenched or re-set (Create's value boxes)
        if (player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL) player.setGameMode(GameType.ADVENTURE);
        player.fallDistance = 0;
        world.playSound(null, spawn.x, spawn.y, spawn.z, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4F, 1.4F);
        Msg.info(player, "You entered the Labyrinth. Find the keys, reach the Minotaur and slay him - you have 30 minutes.");
    }

    private static void spawnBoss(ServerLevel world, Run run) {
        Minotaur boss = ModEntities.MINOTAUR.get().create(world);
        if (boss == null) return;
        Vec3 at = run.at(ArenaLayout.BOSS);
        boss.moveTo(at.x, at.y, at.z, ArenaLayout.BOSS_YAW, 0);
        boss.setYHeadRot(ArenaLayout.BOSS_YAW);
        boss.setYBodyRot(ArenaLayout.BOSS_YAW);
        world.addFreshEntity(boss);
        run.boss = boss.getUUID();
    }

    private static void leave(ServerPlayer player, Run run, String message) {
        run.present.remove(player.getUUID());
        run.bar.removePlayer(player);
        sendHome(player);
        Msg.info(player, message);
        if (run.present.isEmpty() && (!run.portalOpen || run.cleared)) end(player.getServer(), run, null);
    }

    /** Takes the player back where he entered from (the world spawn if that is unknown). */
    static void sendHome(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        // the Labyrinth's keys stay in the Labyrinth
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof LabyrinthKeyItem) inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
        }
        CompoundTag back = player.getPersistentData().getCompound(RETURN_TAG);
        player.getPersistentData().remove(RETURN_TAG);
        if (back.contains("mode") && player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
            player.setGameMode(GameType.byId(back.getInt("mode")));
        }
        ServerLevel level = null;
        if (back.contains("dim")) {
            ResourceLocation dim = ResourceLocation.tryParse(back.getString("dim"));
            if (dim != null) level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        }
        if (level == null || level.dimension() == WORLD) {
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0);
        } else {
            player.teleportTo(level, back.getDouble("x"), back.getDouble("y"), back.getDouble("z"), back.getFloat("yaw"), 0);
        }
        player.fallDistance = 0;
    }

    static void portalClosed(DungeonPortal portal) {
        Run run = RUNS.get(portal.instanceId);
        if (run == null || portal.exit) return;
        run.portalOpen = false;
        run.entrance = null;
        if (run.present.isEmpty()) end(portal.getServer(), run, null);
    }

    /** Ends a run: everyone still inside goes home, the slot is cleaned and freed. */
    private static void end(MinecraftServer server, Run run, @Nullable String message) {
        RUNS.remove(run.id);
        for (UUID id : Set.copyOf(run.present)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || !isDungeon(p.level())) continue;
            sendHome(p);
            if (message != null) Msg.info(p, message);
        }
        run.present.clear();
        run.bar.removeAllPlayers();
        if (run.entrance != null) run.entrance.discard();
        if (run.exit != null) run.exit.discard();
        ServerLevel world = world(server);
        if (world != null) {
            BoundingBox b = run.bounds();
            AABB box = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
            for (Entity e : world.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) e.discard();
            Labyrinth.clear(world, run.origin);
        }
        DungeonData.get(server).markSlot(run.slot, false);
    }

    /** Is the player one of the party of this run (for its reward chests)? */
    static boolean isMember(int runId, ServerPlayer player) {
        Run run = RUNS.get(runId);
        return run != null && run.members.containsKey(player.getUUID());
    }

    /** Tells everyone inside a run. */
    static void tell(int runId, Component message) {
        Run run = RUNS.get(runId);
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (run == null || server == null) return;
        for (UUID id : run.present) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.sendSystemMessage(message);
        }
    }

    /** Ends every run (admin command). */
    public static int endAll(MinecraftServer server) {
        List<Run> all = new ArrayList<>(RUNS.values());
        for (Run r : all) end(server, r, "An administrator closed the labyrinth.");
        return all.size();
    }

    /** Leaves the run the player is in (command). */
    public static boolean leaveCommand(ServerPlayer player) {
        Run run = runOf(player);
        if (run == null) return false;
        leave(player, run, "You gave up and left the labyrinth.");
        return true;
    }

    // ------------------------------------------------------------------ the boss dies

    private static void cleared(MinecraftServer server, Run run) {
        ServerLevel world = world(server);
        if (world == null || run.cleared) return;
        run.cleared = true;
        run.clearedAt = now(server);
        long took = run.clearedAt - run.start;
        String names = String.join(", ", run.members.values());
        int place = DungeonData.get(server).addClear(
                new DungeonData.Clear(names, (int) took, run.members.size(), System.currentTimeMillis()));

        BlockPos chestPos = run.origin.offset(ArenaLayout.CHEST);
        world.setBlock(chestPos, DungeonRegistry.DUNGEON_CHEST.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.SOUTH), 3);
        if (world.getBlockEntity(chestPos) instanceof DungeonChestBlockEntity chest) chest.setup(run.id, DungeonLoot.BOSS);

        Vec3 mid = run.at(Vec3.ZERO);
        world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        String rank = place > 0 ? " - #" + place + " in the ranking" : "";
        for (UUID id : run.present) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            p.sendSystemMessage(Msg.text("The guardian has fallen! Time: " + time(took) + rank + ".", ChatFormatting.GOLD));
            p.sendSystemMessage(Msg.text("Everyone, open the chest for your reward - then the way home opens.", ChatFormatting.YELLOW));
        }
    }

    /** Someone opened the boss's reward chest. */
    static void bossChestOpened(int runId, ServerPlayer player) {
        Run run = RUNS.get(runId);
        if (run == null || !run.looted.add(player.getUUID())) return;
        openExit(player.getServer(), run);
    }

    /** The exit portal opens at the side of the hall once everyone inside has opened the boss's chest. */
    private static void openExit(MinecraftServer server, Run run) {
        if (!run.cleared || run.exit != null || run.present.isEmpty() || !run.looted.containsAll(run.present)) return;
        ServerLevel world = world(server);
        DungeonPortal exit = world == null ? null : ModEntities.DUNGEON_PORTAL.get().create(world);
        if (exit == null) return;
        Vec3 at = run.at(ArenaLayout.EXIT);
        exit.moveTo(at.x, at.y, at.z, ArenaLayout.EXIT_YAW, 0);
        exit.setRemaining(-1);
        exit.instanceId = run.id;
        exit.exit = true;
        world.addFreshEntity(exit);
        run.exit = exit;
        world.playSound(null, at.x, at.y, at.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.NEUTRAL, 1.0F, 1.2F);
        tell(run.id, Component.literal("The way home opens at the east side of the hall.").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (RUNS.isEmpty()) return;
        if (server.getTickCount() % 5 == 0) {
            ServerLevel world = world(server);
            for (Run run : RUNS.values()) {
                if (world == null || run.lab == null || run.present.isEmpty()) continue;
                List<ServerPlayer> inside = new ArrayList<>();
                for (UUID id : run.present) {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (p != null && isDungeon(p.level())) inside.add(p);
                }
                run.lab.tick(world, inside, run.members.size());
            }
        }
        if (server.getTickCount() % 20 != 0) return;
        long now = now(server);
        for (Run run : new ArrayList<>(RUNS.values())) {
            // who is still inside (logged out or gone some other way: out of the run)
            run.present.removeIf(id -> {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                return p == null || !isDungeon(p.level());
            });
            if (run.present.isEmpty() && run.start >= 0 && (!run.portalOpen || run.cleared)) {
                end(server, run, null);
                continue;
            }
            if (run.entrance != null && run.entrance.remaining() > 0) {
                run.entrance.setCustomName(Component.literal("Labyrinth (" + (run.entrance.remaining() / 20 + 1) + "s)")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                run.entrance.setCustomNameVisible(true);
            }
            if (run.start < 0) continue;

            long left;
            if (run.cleared) openExit(server, run);
            if (run.cleared) {
                left = AFTER_CLEAR - (now - run.clearedAt);
                run.bar.setName(Component.literal("Cleared! The labyrinth closes in " + time(left)));
                run.bar.setColor(BossEvent.BossBarColor.GREEN);
                run.bar.setProgress(Mth.clamp(left / (float) AFTER_CLEAR, 0, 1));
                if (left <= 0) {
                    end(server, run, "The labyrinth closes behind you.");
                    continue;
                }
            } else {
                left = TIME_LIMIT - (now - run.start);
                run.bar.setName(Component.literal("Labyrinth - " + time(left) + " left"));
                run.bar.setProgress(Mth.clamp(left / (float) TIME_LIMIT, 0, 1));
                if (left <= 0) {
                    end(server, run, "Time is up! The labyrinth throws you out.");
                    continue;
                }
            }
            for (ServerPlayer p : List.copyOf(run.bar.getPlayers())) {
                if (!run.present.contains(p.getUUID())) run.bar.removePlayer(p);
            }
            for (UUID id : run.present) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) run.bar.addPlayer(p);
            }
        }
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        for (Run run : RUNS.values()) {
            if (event.getEntity().getUUID().equals(run.boss)) {
                cleared(event.getEntity().getServer(), run);
                return;
            }
        }
    }

    /** Dying in a dungeon only throws the player out, with full health and all his things. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isDungeon(player.level())) return;
        event.setCanceled(true);
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.clearFire();
        Run run = runOf(player);
        if (run != null) leave(player, run, "You fell in the labyrinth and were dragged out.");
        else sendHome(player);
    }

    /** Logging in inside a dungeon: back into the run if it still goes on, home otherwise. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getPersistentData().remove("divinis_portal_cooldown"); // left over from older versions
        if (!isDungeon(player.level())) return;
        for (Run run : RUNS.values()) {
            if (run.members.containsKey(player.getUUID()) && run.bounds().isInside(player.blockPosition())) {
                run.present.add(player.getUUID());
                return;
            }
        }
        sendHome(player);
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (e instanceof Player || e.level().isClientSide || !isDungeon(e.level())) return;
        int count = e instanceof Minotaur ? 3 + e.getRandom().nextInt(3)
                : e.getRandom().nextFloat() < CRYSTAL_CHANCE ? 1 : 0;
        if (count <= 0) return;
        event.getDrops().add(new ItemEntity(e.level(), e.getX(), e.getY() + 0.5, e.getZ(),
                new ItemStack(DungeonRegistry.LABYRINTH_CRYSTAL.get(), count)));
    }

    /** Monsters left over in the dungeon world from an earlier run (e.g. before a restart) are not let back in. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !isDungeon(event.getLevel()) || !(event.getEntity() instanceof Mob mob)) return;
        for (Run run : RUNS.values()) if (run.bounds().isInside(mob.blockPosition())) return;
        event.setCanceled(true);
    }

    /** Keys in locks, hands on puzzle machines. */
    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isDungeon(player.level())
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = event.getPos();
        for (Run run : RUNS.values()) {
            if (run.lab == null || !run.bounds().isInside(pos)) continue;
            Labyrinth.Door door = run.lab.doorWithLock(pos);
            if (door != null) {
                event.setCanceled(true);
                ItemStack stack = player.getMainHandItem();
                if (!(stack.getItem() instanceof LabyrinthKeyItem key) || key.type != door.key) {
                    player.displayClientMessage(Msg.text("Locked. It takes the " + door.key.title + ".", ChatFormatting.RED), true);
                    return;
                }
                var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (data == null || data.copyTag().getInt(RUN_TAG) != run.id) {
                    player.displayClientMessage(Msg.text("This key belongs to another labyrinth.", ChatFormatting.RED), true);
                    return;
                }
                stack.shrink(1);
                run.lab.openDoor(level, door);
                tell(run.id, Component.literal(player.getGameProfile().getName() + " opened " + door.key.opens + ".")
                        .withStyle(door.key.color));
                return;
            }
            run.lab.click(level, player, pos);
            return;
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && isDungeon(level) && !event.getPlayer().isCreative()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && isDungeon(level)
                && !(event.getEntity() instanceof Player p && p.isCreative())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (isDungeon(event.getLevel())) event.getAffectedBlocks().clear();
    }

    @SubscribeEvent
    public static void onPearl(EntityTeleportEvent.EnderPearl event) {
        if (isDungeon(event.getEntity().level())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onChorus(EntityTeleportEvent.ChorusFruit event) {
        if (isDungeon(event.getEntity().level())) event.setCanceled(true);
    }

    /** No pouring water or lava (any full bucket, the bronze ones too); empty buckets still work. */
    @SubscribeEvent
    public static void onBucket(PlayerInteractEvent.RightClickItem event) {
        if (!isDungeon(event.getLevel()) || event.getEntity().isCreative()) return;
        if (event.getItemStack().getItem() instanceof net.minecraft.world.item.BucketItem bucket
                && bucket.content != net.minecraft.world.level.material.Fluids.EMPTY) {
            event.setCanceled(true);
            event.getEntity().displayClientMessage(Msg.text("The Labyrinth does not let you pour anything out.",
                    ChatFormatting.RED), true);
        }
    }

    /** A restart ends all runs: clean the slots that were in use. */
    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel world = world(server);
        DungeonData data = DungeonData.get(server);
        for (int slot : data.usedSlots()) {
            if (world != null) Labyrinth.clear(world, new BlockPos(slot * SLOT_SPACING + SLOT_SPACING / 2, FLOOR_Y, 0));
            data.markSlot(slot, false);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (Run r : RUNS.values()) r.bar.removeAllPlayers();
        RUNS.clear();
        PORTAL_COOLDOWN.clear();
    }
}
