package com.divinis.fishing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.divinis.mixin.FishingHookAccessor;
import com.divinis.net.FishingPayloads;
import com.divinis.skill.Skill;
import com.divinis.skill.SkillService;
import com.divinis.skill.XpRules;
import com.divinis.Divinis;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.EntityJoinLevelEvent;
import com.divinis.platform.event.PlayerEvent;
import com.divinis.platform.event.PlayerTickEvent;
import com.divinis.platform.net.PacketDistributor;

/**
 * Fishing, Divinis' way:
 * - the wait for a bite: BASE_WAIT ticks, shortened by fishing speed (percent: the rod's, FishingRods, Lure
 *   SPEED_PER_LURE a level, and the pool the bobber is in, FishingPool) - a plain rod is slow (FishingHookMixin sets
 *   the wait);
 * - a bite is a minigame: the angler sees a wheel (client.FishingHud), a needle turns round it and they press space
 *   when it is in the narrow zone (or one of two; or, for an Ancient Fish, three in their order). In it: the catch
 *   (FishingLoot, with luck: the rod's, Luck of the Sea a point a level and the pool's) straight into the inventory
 *   (dropped in front of the angler when full; client.CatchPopups shows it), Hunter XP, the rod worn, and
 *   now and then a new pool comes up (FishingPool.maybeSpawn). Out of it, or no press within a few turns: the fish is
 *   gone and the bobber waits for the next bite. Reeling in on a bite brings nothing (FishingHookMixin). The bite is
 *   held while the wheel turns; the client judges the press (it sees the needle), the server keeps the round and its
 *   time limit;
 * - the special rods' gifts (FishingRods.Gift) are played here, but for the foam's (FishingHookMixin) and the
 *   speeds and lucks that depend on the time, the land and so on (FishingRods.speed, luck).
 */
@EventBusSubscriber(modid = Divinis.MODID)
public final class FishingGame {
    private static final int TURNS = 3, ANCIENT_TURNS = 5;
    private static final int GRACE = 40; // ticks the server waits past the last turn (lag)
    /** Ticks a plain rod waits for a fish to come (then 1 - 4 s more while it swims to the bobber). */
    public static final int BASE_WAIT_MIN = 450, BASE_WAIT_MAX = 900;
    public static final int SPEED_PER_LURE = 25;
    private static final double ANCIENT_CHANCE = 0.06, TREASURE_CHANCE = 0.08;
    private static final int RHYTHM_MAX = 5, DEVOURED = 400, COLLECTOR_KEEP = 60 * 20;
    private static final String KEPT = "divinis_pool"; // the collector's rod: the pool bonus it keeps (custom data)

    private record Round(int hook, long deadline, boolean retried, boolean ancient) {}

    private static final Map<UUID, Round> ROUNDS = new HashMap<>();
    private static final Map<UUID, Integer> STREAKS = new HashMap<>();
    /** The gambler's rod: what each cast rolled (speed, luck). */
    private static final Map<FishingHook, int[]> GAMBLES = new WeakHashMap<>();

    private FishingGame() {}

    // ------------------------------------------------------------------ the bobber out

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FishingHook hook = player.fishing;
        Round round = ROUNDS.get(player.getUUID());
        if (round != null && (hook == null || hook.isRemoved() || hook.getId() != round.hook())) {
            ROUNDS.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, new FishingPayloads.End(round.hook(), FishingPayloads.CANCELLED));
            round = null;
        }
        if (hook == null) return;
        FishingHookAccessor bite = (FishingHookAccessor) hook;
        gifts(player, hook, round != null || bite.divinis$getNibble() > 0);
        if (round == null) {
            if (bite.divinis$getNibble() > 0) start(player, hook, false);
            return;
        }
        if (player.level().getGameTime() > round.deadline()) {
            miss(player, hook, round);
            return;
        }
        bite.divinis$setNibble(Math.max(bite.divinis$getNibble(), 5)); // the fish stays on while the wheel turns
    }

    /** What works while the bobber is out: whirlpools, and the rods' gifts that do. */
    private static void gifts(ServerPlayer player, FishingHook hook, boolean biting) {
        long now = player.level().getGameTime();
        List<FishingPool> pools = FishingPool.holding(hook);
        FishingHookAccessor bobber = (FishingHookAccessor) hook;
        for (FishingPool pool : pools) {
            if (pool.isWhirl() && !biting && bobber.divinis$getTimeUntilLured() > 20) {
                bobber.divinis$setTimeUntilLured(20); // a bite at once
                pool.discard();
                player.level().playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE,
                        SoundSource.NEUTRAL, 0.8F, 1.2F);
            }
        }
        switch (FishingRods.gift(hook)) {
            case POOLS_LAST -> {
                if (now % 40 == 0) pools.forEach(p -> p.extend(20));
            }
            case SEEKER -> {
                if (!biting && hook.isInWater()) FishingPool.drift(hook, FishingRods.SEEK_RANGE);
            }
            case ANCHOR -> {
                if (hook.isInWater()) {
                    Vec3 v = hook.getDeltaMovement();
                    hook.setDeltaMovement(0, v.y, 0);
                }
            }
            case ICE_HOLE -> {
                if (now % 20 == 0) thaw(player.level(), hook.blockPosition());
            }
            case COLLECTOR -> {
                if (now % 20 == 0) keep(FishingRods.held(player), pools, now);
            }
            default -> {
            }
        }
    }

    /** The ice hole rod: ice round the bobber turns back to water. */
    private static void thaw(net.minecraft.world.level.Level level, BlockPos at) {
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 0, 1))) {
            if (level.getBlockState(p).is(Blocks.ICE)) level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
        }
    }

    /** The collector's rod: the bonus of the pool the bobber is in stays on the rod for COLLECTOR_KEEP ticks. */
    private static void keep(ItemStack rod, List<FishingPool> pools, long now) {
        int luck = 0, speed = 0;
        for (FishingPool p : pools) {
            luck = Math.max(luck, p.luck());
            speed = Math.max(speed, p.speed());
        }
        if (luck == 0 && speed == 0) return;
        CompoundTag tag = new CompoundTag();
        tag.putInt("luck", luck);
        tag.putInt("speed", speed);
        tag.putLong("until", now + COLLECTOR_KEEP);
        CustomData.update(DataComponents.CUSTOM_DATA, rod, data -> data.put(KEPT, tag));
    }

    /** The collector rod's kept bonus (luck, speed, until), if any is still on. */
    public static int[] kept(ItemStack rod, long now) {
        CompoundTag data = rod.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(KEPT)) return null;
        CompoundTag tag = data.getCompound(KEPT);
        long left = tag.getLong("until") - now;
        return left > 0 ? new int[] {tag.getInt("luck"), tag.getInt("speed"), (int) left} : null;
    }

    /** A new bobber: the harpooner's flies farther, the gambler's rolls. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof FishingHook hook)
                || !(hook.getPlayerOwner() instanceof ServerPlayer player)) return;
        switch (FishingRods.gift(hook)) {
            case HARPOON -> hook.setDeltaMovement(hook.getDeltaMovement().scale(1.8));
            case GAMBLER -> {
                RandomSource random = player.getRandom();
                int[] roll = {Mth.nextInt(random, -50, 150), Mth.nextInt(random, -2, 8)};
                GAMBLES.put(hook, roll);
                player.displayClientMessage(Component.literal("The dice: " + (roll[0] >= 0 ? "+" : "") + roll[0]
                        + "% Fishing Speed, " + (roll[1] >= 0 ? "+" : "") + roll[1] + " Luck").withStyle(ChatFormatting.GOLD), true);
            }
            default -> {
            }
        }
    }

    /** FishingRods: what the gambler's cast rolled (speed, luck). */
    static int[] gamble(FishingHook hook) {
        return GAMBLES.getOrDefault(hook, new int[] {0, 0});
    }

    /** FishingRods: the rhythm rod's catches in a row. */
    static int streak(net.minecraft.world.entity.player.Player player) {
        return STREAKS.getOrDefault(player.getUUID(), 0);
    }

    // ------------------------------------------------------------------ the minigame

    private static void start(ServerPlayer player, FishingHook hook, boolean retried) {
        RandomSource random = player.getRandom();
        FishingRods.Stats rod = FishingRods.stats(hook);
        if (rod.gift() == FishingRods.Gift.GLUTTON && !retried) player.causeFoodExhaustion(3.0F);
        boolean ancient = rod.gift() == FishingRods.Gift.LEVIATHAN && !retried && random.nextDouble() < ANCIENT_CHANCE;
        float speed = Mth.nextFloat(random, 7.5F, 11.5F) * rod.needle(); // degrees a tick: a turn in 1.6 - 2.4 s
        float half = Mth.nextFloat(random, 9.0F, 14.0F) * rod.zone();    // the zone, 18 - 28 degrees wide
        if (com.divinis.skill.Perks.has(player, com.divinis.skill.SkillBooks.CALM_HANDS)) half *= 1.2F;
        float start = random.nextFloat() * 360;
        List<Float> zones = new ArrayList<>();
        int turns = TURNS;
        String title = retried ? "Second chance!" : "";
        if (ancient) {
            half = Mth.nextFloat(random, 8.0F, 11.0F);
            float z = start + Mth.nextFloat(random, 90, 150);
            for (int i = 0; i < 3; i++) {
                zones.add(z % 360);
                z += Mth.nextFloat(random, 95, 140);
            }
            turns = ANCIENT_TURNS;
            title = "An Ancient Fish!";
        } else {
            float z = start + Mth.nextFloat(random, 120, 300); // never right under the needle
            zones.add(z % 360);
            if (rod.gift() == FishingRods.Gift.TWIN_MOONS) zones.add((z + 180 + Mth.nextFloat(random, -30, 30)) % 360);
        }
        long deadline = player.level().getGameTime() + Mth.ceil(turns * 360 / speed) + GRACE;
        ROUNDS.put(player.getUUID(), new Round(hook.getId(), deadline, retried, ancient));
        PacketDistributor.sendToPlayer(player, new FishingPayloads.Start(hook.getId(), start, speed, zones, half, turns, ancient, title));
    }

    public static void press(ServerPlayer player, int hookId, boolean hit, boolean perfect) {
        Round round = ROUNDS.get(player.getUUID());
        FishingHook hook = player.fishing;
        if (round == null || round.hook() != hookId || hook == null || hook.getId() != hookId) return;
        if (hit) reelIn(player, hook, round, perfect);
        else miss(player, hook, round);
    }

    /** A miss (or no press in time): the bloody hook bites, the rhythm is lost, a second chance - or the fish is gone. */
    private static void miss(ServerPlayer player, FishingHook hook, Round round) {
        FishingRods.Gift gift = FishingRods.gift(hook);
        STREAKS.remove(player.getUUID());
        if (gift == FishingRods.Gift.BLOODY) player.hurt(player.damageSources().magic(), 1.0F);
        if (gift == FishingRods.Gift.SECOND_CHANCE && !round.retried()) {
            PacketDistributor.sendToPlayer(player, new FishingPayloads.End(hook.getId(), FishingPayloads.ESCAPED));
            start(player, hook, true);
            return;
        }
        escape(player, hook);
    }

    /** The catch flies out of the water to the angler (as the vanilla one does), the bobber comes back. */
    private static void reelIn(ServerPlayer player, FishingHook hook, Round round, boolean perfect) {
        ROUNDS.remove(player.getUUID());
        InteractionHand hand = player.getMainHandItem().getItem() instanceof FishingRodItem ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
        ItemStack rod = player.getItemInHand(hand);
        FishingRods.Gift gift = FishingRods.stats(rod).gift();
        RandomSource random = player.getRandom();
        int luck = luck(hook);
        FishingLoot.Mods mods = FishingLoot.Mods.of(gift);

        List<FishingLoot.Catch> caught = new ArrayList<>();
        if (round.ancient()) {
            caught.add(new FishingLoot.Catch(new ItemStack(FishingRods.ANCIENT_FISH.get()), XpRules.FISH_TREASURE * 6));
            caught.add(FishingLoot.rollRareIngot(random, luck));
        } else if (gift == FishingRods.Gift.GOLDEN_MEAN && perfect) {
            caught.add(FishingLoot.rollIngot(random, luck));
        } else {
            caught.add(FishingLoot.roll(random, luck, mods));
        }
        ItemStack first = caught.get(0).stack();
        if (gift == FishingRods.Gift.DOUBLE_FISH && first.is(ItemTags.FISHES) && random.nextInt(4) == 0) first.setCount(2);
        if (gift == FishingRods.Gift.TREASURE && random.nextDouble() < TREASURE_CHANCE) {
            for (int i = Mth.nextInt(random, 2, 4); i > 0; i--) caught.add(FishingLoot.rollNoFish(random, luck, mods));
            player.displayClientMessage(Component.literal("A treasure!").withStyle(ChatFormatting.GOLD), true);
        }

        List<ItemStack> stacks = new ArrayList<>();
        double xp = 0;
        for (FishingLoot.Catch c : caught) {
            stacks.add(c.stack().copy());
            xp += c.xp();
            if (c.stack().is(ItemTags.FISHES)) player.awardStat(Stats.FISH_CAUGHT, 1);
        }
        CriteriaTriggers.FISHING_ROD_HOOKED.trigger(player, rod, hook, stacks);
        for (FishingLoot.Catch c : caught) give(player, c.stack());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                0.25F, ((random.nextFloat() - random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
        PacketDistributor.sendToPlayer(player, new FishingPayloads.Caught(stacks));
        SkillService.addXp(player, Skill.HUNTER, xp);
        if (gift == FishingRods.Gift.RHYTHM) STREAKS.merge(player.getUUID(), 1, (a, b) -> Math.min(RHYTHM_MAX, a + b));
        if (gift == FishingRods.Gift.DEVOURER) FishingPool.holding(hook).forEach(p -> p.extend(-DEVOURED));
        FishingPool.maybeSpawn((ServerLevel) player.level(), hook, spawn(gift, hook));

        hook.discard();
        if (rod.getItem() instanceof FishingRodItem) rod.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.NEUTRAL, 1.0F, 0.4F / (random.nextFloat() * 0.4F + 0.8F));
        player.swing(hand, true);
        PacketDistributor.sendToPlayer(player, new FishingPayloads.End(hook.getId(), FishingPayloads.CAUGHT));
    }

    /** Straight into the inventory; what does not fit is dropped in front of the player. */
    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        if (!stack.isEmpty()) player.drop(stack, false);
    }

    /** How the rod changes the pool that may come up after this catch. */
    private static FishingPool.Spawn spawn(FishingRods.Gift gift, FishingHook hook) {
        boolean hot = FishingRods.hot(hook.level(), hook.blockPosition());
        return switch (gift) {
            case MORE_POOLS -> new FishingPool.Spawn(2, 0, 0, false);
            case OASIS -> hot ? new FishingPool.Spawn(2, 1, 0, false) : FishingPool.Spawn.PLAIN;
            case CHARMER -> new FishingPool.Spawn(1, 1, 0, false);
            case WHIRLPOOL -> new FishingPool.Spawn(1, 0, 0.5, false);
            case STARLIGHT -> new FishingPool.Spawn(1, 0, 0, FishingRods.night(hook.level()) && !hook.level().isRaining()
                    && hook.level().canSeeSky(hook.blockPosition().above()));
            default -> FishingPool.Spawn.PLAIN;
        };
    }

    /** The fish gets away; the bobber waits for the next bite (as when a vanilla bite runs out). */
    private static void escape(ServerPlayer player, FishingHook hook) {
        ROUNDS.remove(player.getUUID());
        letGo(hook);
        PacketDistributor.sendToPlayer(player, new FishingPayloads.End(hook.getId(), FishingPayloads.ESCAPED));
    }

    private static void letGo(FishingHook hook) {
        FishingHookAccessor bite = (FishingHookAccessor) hook;
        bite.divinis$setNibble(0);
        bite.divinis$setTimeUntilLured(0);
        bite.divinis$setTimeUntilHooked(0);
        hook.getEntityData().set(FishingHookAccessor.divinis$dataBiting(), false);
    }

    // ------------------------------------------------------------------ luck and speed

    /** The pool's bonus (luck, speed) for this bobber: the devourer's half again more, the collector's kept one. */
    private static int[] poolBonus(FishingHook hook) {
        int luck = FishingPool.luckAt(hook), speed = FishingPool.speedAt(hook);
        FishingRods.Gift gift = FishingRods.gift(hook);
        if (gift == FishingRods.Gift.DEVOURER) {
            luck = Math.round(luck * 1.5F);
            speed = Math.round(speed * 1.5F);
        } else if (gift == FishingRods.Gift.COLLECTOR && hook.getPlayerOwner() != null) {
            int[] kept = kept(FishingRods.held(hook.getPlayerOwner()), hook.level().getGameTime());
            if (kept != null) {
                luck = Math.max(luck, kept[0]);
                speed = Math.max(speed, kept[1]);
            }
        }
        return new int[] {luck, speed};
    }

    /** The rod's, Luck of the Sea, the pool's, and the Fisher's book's (Steady Line). */
    private static int luck(FishingHook hook) {
        int book = com.divinis.skill.Perks.has(hook.getPlayerOwner(), com.divinis.skill.SkillBooks.STEADY_LINE) ? 1 : 0;
        return FishingRods.luck(hook) + ((FishingHookAccessor) hook).divinis$getLuck() + poolBonus(hook)[0] + book;
    }

    /** The Fisher's book: Steady Line +15%, Chum +100% while it runs. */
    private static int bookSpeed(FishingHook hook) {
        net.minecraft.world.entity.player.Player owner = hook.getPlayerOwner();
        return (com.divinis.skill.Perks.has(owner, com.divinis.skill.SkillBooks.STEADY_LINE) ? 15 : 0)
                + (com.divinis.skill.Perks.buff(owner, com.divinis.skill.SkillBooks.CHUM) ? 100 : 0);
    }

    /** FishingHookMixin: a new wait for a fish to come, from the rod's and the pool's fishing speed. */
    public static int waitTicks(FishingHook hook) {
        int speed = FishingRods.speed(hook) + ((FishingHookAccessor) hook).divinis$getLureSpeed() / 100 * SPEED_PER_LURE
                + poolBonus(hook)[1] + bookSpeed(hook);
        speed = Math.max(-90, speed);
        return Math.max(20, Math.round(Mth.nextInt(hook.getRandom(), BASE_WAIT_MIN, BASE_WAIT_MAX) / (1 + speed / 100F)));
    }

    /** FishingHookMixin: the rod is reeled in. A catch comes only from the minigame, so the bite is dropped. */
    public static void onRetrieve(FishingHook hook) {
        if (hook.level().isClientSide()) return;
        ((FishingHookAccessor) hook).divinis$setNibble(0);
        if (hook.getPlayerOwner() instanceof ServerPlayer player) {
            Round round = ROUNDS.remove(player.getUUID());
            if (round != null) PacketDistributor.sendToPlayer(player, new FishingPayloads.End(round.hook(), FishingPayloads.CANCELLED));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ROUNDS.remove(event.getEntity().getUUID());
        STREAKS.remove(event.getEntity().getUUID());
    }
}
