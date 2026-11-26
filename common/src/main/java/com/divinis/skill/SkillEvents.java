package com.divinis.skill;

import com.divinis.Config;
import com.divinis.Divinis;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.divinis.platform.bus.EventPriority;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.TagsUpdatedEvent;
import com.divinis.platform.event.BabyEntitySpawnEvent;
import com.divinis.platform.event.FinalizeSpawnEvent;
import com.divinis.platform.event.LivingDeathEvent;
import com.divinis.platform.event.PlayerEvent;
import com.divinis.platform.event.PlayerInteractEvent;
import com.divinis.platform.event.BlockEvent;
import com.divinis.platform.event.ServerStoppedEvent;
import com.divinis.platform.event.ServerTickEvent;

/** Hands out skill XP. Runs at the lowest priority so cancelled actions (protected land etc.) give nothing. */
@EventBusSubscriber(modid = Divinis.MODID)
public class SkillEvents {
    private static final String SPAWNER_TAG = "divinis_spawner";

    // ------------------------------------------------------------------ blacksmith

    /** Gear made on a crafting table (the Armory Table gives its XP in ArmoryService). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        double xp = XpRules.crafted(event.getCrafting(), event.getInventory());
        if (xp > 0) SkillService.addXp(player, Skill.BLACKSMITH, xp);
    }

    /**
     * Metal taken out of any furnace (bloomery, steel furnace, furnace, blast furnace...); cooked food and the like
     * give nothing. The bronze furnace gives its XP in BronzeFurnaceMenu.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack out = event.getSmelting();
        double xp = XpRules.smelted(out) * out.getCount();
        if (xp > 0) SkillService.addXp(player, Skill.BLACKSMITH, xp);
    }

    // ------------------------------------------------------------------ miner + farmer

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        BlockState state = event.getState();

        if (PlacedBlockTracker.consume(level, event.getPos())) return; // placed by a player: no XP

        double farm = XpRules.farmer(state, level, event.getPos());
        if (farm > 0) {
            SkillService.addXp(player, Skill.FARMER, farm);
            return;
        }
        double mine = XpRules.miner(state, level, event.getPos(), player.hasCorrectToolForDrops(state));
        if (mine > 0) SkillService.addXp(player, Skill.MINER, mine);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer) || !(event.getLevel() instanceof ServerLevel level)) return;
        BlockState state = event.getPlacedBlock();
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)
                || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS) || state.is(Blocks.BAMBOO)) {
            PlacedBlockTracker.placed(level, event.getPos());
        }
    }

    /** Picking sweet berries and medicinal herbs by hand. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        // the event fires for both hands; bushes are picked by the main hand, whatever it holds - except when the
        // item is used instead: bone meal on a bush that is not ripe, or anything held while crouching
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (player.isSecondaryUseActive() && !(held.isEmpty() && player.getOffhandItem().isEmpty())) return;
        if (held.is(net.minecraft.world.item.Items.BONE_MEAL)
                && state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock b
                && b.isValidBonemealTarget(event.getLevel(), event.getPos(), state)) return;
        double xp = XpRules.picked(state);
        if (xp > 0) SkillService.addXp(player, Skill.FARMER, xp);
    }

    /** player -> {hour (epoch hours), breeding XP given in it} */
    private static final java.util.Map<java.util.UUID, double[]> BREEDING_HOUR = new java.util.HashMap<>();

    /** Breeding animals, at most XpRules.BREEDING_PER_HOUR an hour. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBaby(BabyEntitySpawnEvent event) {
        if (event.isCanceled() || !(event.getCausedByPlayer() instanceof ServerPlayer player)) return;
        double hour = System.currentTimeMillis() / 3_600_000L;
        double[] got = BREEDING_HOUR.computeIfAbsent(player.getUUID(), k -> new double[] {hour, 0});
        if (got[0] != hour) {
            got[0] = hour;
            got[1] = 0;
        }
        double xp = Math.min(XpRules.BREEDING, XpRules.BREEDING_PER_HOUR - got[1]);
        if (xp <= 0) return;
        got[1] += xp;
        SkillService.addXp(player, Skill.FARMER, xp);
    }

    // ------------------------------------------------------------------ hunter

    /** Mobs from spawners give much less hunter XP. */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.SPAWNER || type == MobSpawnType.TRIAL_SPAWNER) {
            com.divinis.platform.Services.persistentData(event.getEntity()).putBoolean(SPAWNER_TAG, true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        LivingEntity victim = event.getEntity();
        if (victim == killer || victim instanceof ServerPlayer) return;

        double xp = XpRules.hunter(victim);
        if (xp <= 0) return;
        if (com.divinis.platform.Services.persistentData(victim).getBoolean(SPAWNER_TAG)) xp *= Config.SPAWNER_MOB_MULTIPLIER.get();
        SkillService.addXp(killer, Skill.HUNTER, xp);
    }

    // ------------------------------------------------------------------ housekeeping

    @SubscribeEvent
    public static void onTags(TagsUpdatedEvent event) {
        XpRules.clearCache();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 1200 != 0) return;
        PlacedBlockTracker.purge(event.getServer().overworld().getGameTime());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PlacedBlockTracker.clear();
        BREEDING_HOUR.clear();
    }
}
