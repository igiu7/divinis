package com.divinis.skill;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.divinis.fishing.FishingPool;
import com.divinis.mixin.FishingHookAccessor;
import com.divinis.net.SkillFxPayload;
import com.divinis.Divinis;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The skills of the skill books (SkillBooks): use(player, id) checks the orb is unlocked and the cooldown, then does
 * it; each returns null when done or why it could not be (then no cooldown). Cooldowns and buffs are kept on the
 * character (CharacterData, epoch millis: they last through a relog and a restart). For now they are used with
 * "/divinis skills use <skill>" (the skill slots come later).
 * Vein Breaker and Timber! break their blocks a few a tick (QUEUES), with the player's own tool, as if the player broke
 * them (claims, durability, drops).
 */
@EventBusSubscriber(modid = Divinis.MODID)
public final class SkillEffects {
    private SkillEffects() {}

    private record Def(String id, int cooldown, Function<ServerPlayer, String> action) {}

    private static final Map<String, Def> SKILLS = new LinkedHashMap<>();

    private static void def(String id, int cooldownSeconds, Function<ServerPlayer, String> action) {
        SKILLS.put(id, new Def(id, cooldownSeconds, action));
    }

    static {
        def(SkillBooks.CHUM, 300, SkillEffects::chum);
        def(SkillBooks.CALL_POOL, 600, SkillEffects::callPool);
        def(SkillBooks.ORE_ECHO, 60, SkillEffects::oreEcho);
        def(SkillBooks.VEIN, 120, p -> buff(p, SkillBooks.VEIN, 30, "Break an ore within 30 s"));
        def(SkillBooks.TIMBER, 60, p -> buff(p, SkillBooks.TIMBER, 30, "Cut a tree within 30 s"));
        def(SkillBooks.GROWTH, 180, SkillEffects::growth);
        def(SkillBooks.REAP, 30, SkillEffects::reap);
        def(SkillBooks.ABUNDANCE, 180, SkillEffects::abundance);
        def(SkillBooks.FIELD_POULTICE, 180, SkillEffects::fieldPoultice);
        def(SkillBooks.BREW, 300, p -> {
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 20, 0));
            return null;
        });
        def(SkillBooks.CAMP, 300, SkillEffects::camp);
        def(SkillBooks.SHORTCUT, 120, p -> {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 20, 1));
            p.addEffect(new MobEffectInstance(MobEffects.JUMP, 20 * 20, 1));
            return null;
        });
        def(SkillBooks.TRACK, 60, SkillEffects::track);
        def(SkillBooks.SKINNING, 180, p -> buff(p, SkillBooks.SKINNING, 60, "Kill an animal within 60 s"));
        def(SkillBooks.TREASURE_SENSE, 60, SkillEffects::treasureSense);
        def(SkillBooks.ESCAPE, 0, SkillEffects::escape);
        def(SkillBooks.LECTURE, 1800, SkillEffects::lecture);
        def(SkillBooks.STUDY, 1800, p -> buff(p, SkillBooks.STUDY, 600, "+50% XP for 10 minutes"));
    }

    public static java.util.Set<String> ids() {
        return SKILLS.keySet();
    }

    /** The orb's name (the skill as players know it). */
    private static String name(String id) {
        for (SkillBook b : SkillBook.all()) {
            for (SkillOrb o : b.orbs()) if (o.id().equals(id)) return o.name();
        }
        return id;
    }

    /** Uses the skill if the player may; tells them how it went. */
    public static void use(ServerPlayer player, String id) {
        Def def = SKILLS.get(id);
        if (def == null) {
            fail(player, "No such skill: " + id);
            return;
        }
        if (!Perks.has(player, id)) {
            fail(player, "You have not unlocked " + name(id) + " (put its skill book in and the points into it).");
            return;
        }
        CharacterData data = CharacterService.data(player);
        long now = System.currentTimeMillis();
        Long ready = data.cooldowns.get(id);
        if (ready != null && ready > now) {
            fail(player, name(id) + " is ready again in " + com.divinis.util.Names.duration((int) ((ready - now + 999) / 1000)) + ".");
            return;
        }
        String error = def.action().apply(player);
        if (error != null) {
            fail(player, error);
            return;
        }
        data = CharacterService.data(player);
        if (def.cooldown() > 0) data.cooldowns.put(id, now + def.cooldown() * 1000L);
        player.setData(SkillAttachments.CHARACTER, data);
        player.displayClientMessage(Component.literal(name(id) + "!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.8F, 1.3F);
    }

    private static void fail(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);
    }

    private static String buff(ServerPlayer player, String id, int seconds, String message) {
        CharacterData data = CharacterService.data(player);
        data.buffs.put(id, System.currentTimeMillis() + seconds * 1000L);
        player.setData(SkillAttachments.CHARACTER, data);
        player.sendSystemMessage(Component.literal(name(id) + ": " + message + ".").withStyle(ChatFormatting.AQUA));
        return null;
    }

    private static void endBuff(ServerPlayer player, String id) {
        CharacterData data = CharacterService.data(player);
        data.buffs.remove(id);
        player.setData(SkillAttachments.CHARACTER, data);
    }

    // ------------------------------------------------------------------ fisher

    private static String chum(ServerPlayer player) {
        buff(player, SkillBooks.CHUM, 60, "fish bite twice as fast for 60 s");
        FishingHook hook = player.fishing;
        if (hook != null) { // the wait already running gets halved too
            FishingHookAccessor bobber = (FishingHookAccessor) hook;
            if (bobber.divinis$getNibble() <= 0 && bobber.divinis$getTimeUntilLured() > 1) {
                bobber.divinis$setTimeUntilLured(bobber.divinis$getTimeUntilLured() / 2);
            }
        }
        return null;
    }

    private static String callPool(ServerPlayer player) {
        FishingHook hook = player.fishing;
        if (hook == null || !hook.isInWater()) return "Cast your line into the water first.";
        return FishingPool.summon((ServerLevel) player.level(), hook, FishingPool.Tier.RARE) ? null
                : "There is not enough open water round your bobber.";
    }

    // ------------------------------------------------------------------ miner

    private static String oreEcho(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        List<BlockPos> ores = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-8, -8, -8), at.offset(8, 8, 8))) {
            BlockState s = level.getBlockState(p);
            if (!s.is(Tags.Blocks.ORES)) continue;
            ores.add(p.immutable());
            colors.add(s.getMapColor(level, p).col);
            if (ores.size() >= 400) break;
        }
        if (ores.isEmpty()) return "There is no ore within 8 blocks.";
        PacketDistributor.sendToPlayer(player, new SkillFxPayload(SkillFxPayload.ORES, ores, colors, 15 * 20));
        return null;
    }

    // ------------------------------------------------------------------ vein breaker, timber: queued breaking

    private record Queue(ArrayDeque<BlockPos> left, Block block, boolean axe) {}

    private static final Map<UUID, Queue> QUEUES = new HashMap<>();
    private static boolean breaking; // the queue's own breaks start no queue

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (breaking || event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) return;
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        CharacterData data = CharacterService.data(player);
        if (state.is(Tags.Blocks.ORES) && data.buff(SkillBooks.VEIN) && player.hasCorrectToolForDrops(state)) {
            ArrayDeque<BlockPos> vein = connected(player.serverLevel(), pos, state.getBlock(), 64, false);
            if (!vein.isEmpty()) {
                endBuff(player, SkillBooks.VEIN);
                QUEUES.put(player.getUUID(), new Queue(vein, state.getBlock(), false));
            }
        } else if (state.is(BlockTags.LOGS) && data.buff(SkillBooks.TIMBER) && player.getMainHandItem().is(ItemTags.AXES)) {
            ArrayDeque<BlockPos> tree = connected(player.serverLevel(), pos, null, 200, true);
            if (tree.size() >= 2 && natural(player.serverLevel(), tree)) {
                endBuff(player, SkillBooks.TIMBER);
                QUEUES.put(player.getUUID(), new Queue(tree, null, true));
            }
        }
    }

    /** The blocks touching the first (corners too) like it - the same block, or (null) any log above the cut - nearest first. */
    private static ArrayDeque<BlockPos> connected(ServerLevel level, BlockPos start, Block block, int max, boolean up) {
        ArrayDeque<BlockPos> out = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        seen.add(start);
        open.add(start);
        while (!open.isEmpty() && out.size() < max) {
            BlockPos p = open.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos q = p.offset(dx, dy, dz);
                if (!seen.add(q) || (up && q.getY() < start.getY())) continue;
                BlockState s = level.getBlockState(q);
                if (block != null ? s.is(block) : s.is(BlockTags.LOGS)) {
                    out.add(q);
                    open.add(q);
                    if (out.size() >= max) break;
                }
            }
        }
        return out;
    }

    /** A tree grew: leaves that are not a player's touch its logs. */
    private static boolean natural(ServerLevel level, ArrayDeque<BlockPos> logs) {
        for (BlockPos p : logs) {
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                BlockState s = level.getBlockState(q);
                if (s.is(BlockTags.LEAVES) && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT)) return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Queue queue = QUEUES.get(player.getUUID());
        if (queue == null) return;
        ServerLevel level = player.serverLevel();
        for (int n = 0; n < 4 && !queue.left().isEmpty(); n++) {
            BlockPos p = queue.left().poll();
            BlockState s = level.getBlockState(p);
            boolean same = queue.axe() ? s.is(BlockTags.LOGS) : s.is(queue.block());
            boolean tool = queue.axe() ? player.getMainHandItem().is(ItemTags.AXES) : player.hasCorrectToolForDrops(s);
            if (!same) continue;
            if (!tool || player.distanceToSqr(Vec3.atCenterOf(p)) > 48 * 48) {
                queue.left().clear(); // the tool broke or was put away, or they went off
                break;
            }
            breaking = true;
            try {
                player.gameMode.destroyBlock(p);
            } finally {
                breaking = false;
            }
        }
        if (queue.left().isEmpty()) QUEUES.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        QUEUES.remove(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------------ lumberjack, farmer

    private static String growth(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        int grown = 0;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-6, -3, -6), at.offset(6, 3, 6))) {
            BlockPos pos = p.immutable();
            for (int i = 0; i < 4; i++) {
                BlockState s = level.getBlockState(pos);
                if (!(s.getBlock() instanceof SaplingBlock sapling)) break;
                sapling.advanceTree(level, pos, s, level.random);
                if (!(level.getBlockState(pos).getBlock() instanceof SaplingBlock)) {
                    grown++;
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 12, 1, 1, 1, 0);
                    break;
                }
            }
        }
        return grown > 0 ? null : "There is no sapling within 6 blocks that has room to grow.";
    }

    /** Whether the player may break here (claims and the like: a break event nobody cancels). */
    private static boolean mayBreak(ServerPlayer player, BlockPos pos, BlockState state) {
        return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(player.level(), pos, state, player)).isCanceled();
    }

    private static String reap(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        int reaped = 0;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 1, 2))) {
            BlockPos pos = p.immutable();
            BlockState s = level.getBlockState(pos);
            if (!(s.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(s) || !mayBreak(player, pos, s)) continue;
            List<ItemStack> drops = Block.getDrops(s, level, pos, null, player, player.getMainHandItem());
            ItemStack seed = crop.getCloneItemStack(level, pos, s);
            for (ItemStack d : drops) { // one seed goes back into the ground
                if (!seed.isEmpty() && ItemStack.isSameItem(d, seed)) {
                    d.shrink(1);
                    break;
                }
            }
            boolean twice = Perks.has(player, SkillBooks.BOUNTIFUL) && level.random.nextDouble() < PerkEvents.BOUNTIFUL_CHANCE;
            for (ItemStack d : drops) {
                if (d.isEmpty()) continue;
                Block.popResource(level, pos, d.copy());
                if (twice) Block.popResource(level, pos, d.copy());
            }
            level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_ALL);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
            reaped++;
        }
        if (reaped == 0) return "There are no ripe crops round you.";
        level.playSound(null, at, SoundEvents.CROP_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
        return null;
    }

    private static String abundance(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        int grown = 0;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-4, -2, -4), at.offset(4, 2, 4))) {
            BlockPos pos = p.immutable();
            BlockState s = level.getBlockState(pos);
            Block b = s.getBlock();
            if (b instanceof CropBlock crop) {
                if (crop.isMaxAge(s)) continue;
                level.setBlock(pos, crop.getStateForAge(Math.min(crop.getMaxAge(), crop.getAge(s) + 2)), Block.UPDATE_CLIENTS);
            } else if ((b instanceof StemBlock || b instanceof SweetBerryBushBlock || b instanceof CocoaBlock
                    || com.divinis.Divinis.hooks().growable(s)) && b instanceof BonemealableBlock bm) {
                boolean any = false;
                for (int i = 0; i < 2; i++) {
                    BlockState now = level.getBlockState(pos);
                    if (now.getBlock() == b && bm.isValidBonemealTarget(level, pos, now)) {
                        bm.performBonemeal(level, level.random, pos, now);
                        any = true;
                    }
                }
                if (!any) continue;
            } else {
                continue;
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0);
            grown++;
        }
        return grown > 0 ? null : "There are no growing crops within 4 blocks.";
    }

    // ------------------------------------------------------------------ herbalist, wanderer

    private static String fieldPoultice(ServerPlayer player) {
        int healed = 0;
        for (ServerPlayer p : player.serverLevel().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(4))) {
            if (!p.isAlive() || p.getHealth() >= p.getMaxHealth()) continue;
            p.heal(6.0F);
            p.serverLevel().sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.9, p.getZ(), 3, 0.35, 0.15, 0.35, 0);
            if (p != player) p.sendSystemMessage(Component.literal(player.getGameProfile().getName() + " dressed your wounds.").withStyle(ChatFormatting.GREEN));
            healed++;
        }
        if (healed == 0) return "Nobody here is hurt.";
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return null;
    }

    private static final Map<UUID, SkillCamp> CAMPS = new HashMap<>();

    private static String camp(ServerPlayer player) {
        if (!player.onGround() || player.isInWater()) return "Stand on firm ground to set up a camp.";
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        if (!level.getBlockState(at).canBeReplaced() || !level.getBlockState(at.above()).canBeReplaced()) {
            return "There is no room for a camp here.";
        }
        SkillCamp old = CAMPS.remove(player.getUUID());
        if (old != null && !old.isRemoved()) old.discard();
        SkillCamp camp = new SkillCamp(com.divinis.registry.ModEntities.SKILL_CAMP.get(), level);
        camp.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        camp.light(player.getUUID());
        level.addFreshEntity(camp);
        CAMPS.put(player.getUUID(), camp);
        level.playSound(null, at, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.7F, 1.2F);
        return null;
    }

    // ------------------------------------------------------------------ hunter

    private static String track(ServerPlayer player) {
        List<Integer> ids = new ArrayList<>();
        for (Entity e : player.serverLevel().getEntities(player, player.getBoundingBox().inflate(24), e -> e.isAlive() && Perks.animal(e))) {
            ids.add(e.getId());
            if (ids.size() >= 200) break;
        }
        if (ids.isEmpty()) return "There are no animals within 24 blocks.";
        PacketDistributor.sendToPlayer(player, new SkillFxPayload(SkillFxPayload.GLOW, List.of(), ids, 20 * 20));
        return null;
    }

    // ------------------------------------------------------------------ dungeon scout

    private static String treasureSense(ServerPlayer player) {
        if (!com.divinis.dungeon.DungeonService.isDungeon(player.level())) return "Treasure Sense works in the dungeons.";
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int cx = at.getX() >> 4, cz = at.getZ() >> 4;
        for (int x = cx - 6; x <= cx + 6; x++) {
            for (int z = cz - 6; z <= cz + 6; z++) {
                if (!level.hasChunk(x, z)) continue;
                LevelChunk chunk = level.getChunk(x, z);
                for (var be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof com.divinis.dungeon.DungeonChestBlockEntity chest) || chest.opened(player.getUUID())) continue;
                    double d = be.getBlockPos().distSqr(at);
                    if (d < bestDist) {
                        bestDist = d;
                        best = be.getBlockPos();
                    }
                }
            }
        }
        if (best == null) return "There is no reward chest you have not opened nearby.";
        PacketDistributor.sendToPlayer(player, new SkillFxPayload(SkillFxPayload.POINTER, List.of(best), List.of(), 20 * 20));
        return null;
    }

    private static String escape(ServerPlayer player) {
        Optional<com.divinis.dungeon.DungeonService.Start> start = com.divinis.dungeon.DungeonService.startOf(player);
        if (start.isEmpty()) return "You are not in the Labyrinth.";
        String key = "escape_run_" + start.get().runId();
        CharacterData data = CharacterService.data(player);
        if (data.buff(key)) return "You have used Escape in this run already.";
        data.buffs.put(key, System.currentTimeMillis() + 12L * 60 * 60 * 1000);
        player.setData(SkillAttachments.CHARACTER, data);
        Vec3 to = start.get().at();
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.2);
        player.teleportTo(level, to.x, to.y, to.z, 180, 0);
        player.fallDistance = 0;
        level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.1F);
        return null;
    }

    // ------------------------------------------------------------------ scholar

    private static String lecture(ServerPlayer player) {
        int taught = 0;
        for (ServerPlayer p : player.serverLevel().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(8))) {
            boolean same = p == player || com.divinis.Divinis.hooks().sameParty(player, p);
            if (!same) continue;
            CharacterData data = CharacterService.data(p);
            data.buffs.put(SkillBooks.LECTURE, System.currentTimeMillis() + 5L * 60 * 1000);
            p.setData(SkillAttachments.CHARACTER, data);
            p.sendSystemMessage(Component.literal(player.getGameProfile().getName() + "'s Lecture: +25% XP for 5 minutes.").withStyle(ChatFormatting.AQUA));
            taught++;
        }
        return taught > 0 ? null : "Nobody heard the lecture.";
    }
}
