package com.divinis.skill;

import java.util.ArrayList;
import java.util.List;

import com.divinis.Divinis;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * The skill books' passives that work through events (SkillBooks; the others are asked for where they act: fishing,
 * healing, climate, hunger, research, crops, dungeon chests, tool wear in ItemStackDurabilityMixin):
 * Strong Arm and Sharp Axe (mining and chopping speed - on both sides, so the client breaks as fast as the server),
 * Rich Seams, Forest's Gift and Bountiful Harvest (drops), Predator and Labyrinth Sense (damage), Silent Step
 * (visibility), Long Stride (falls), and the Skinning buff (an animal's drops).
 */
@EventBusSubscriber(modid = Divinis.MODID)
public final class PerkEvents {
    private PerkEvents() {}

    public static final double BOUNTIFUL_CHANCE = 0.15, SEAM_CHANCE = 0.15;

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        BlockState state = event.getState();
        ItemStack tool = player.getMainHandItem();
        if (tool.is(ItemTags.PICKAXES) && state.is(BlockTags.MINEABLE_WITH_PICKAXE) && Perks.has(player, SkillBooks.STRONG_ARM)) {
            event.setNewSpeed(event.getNewSpeed() * 1.10F);
        } else if (tool.is(ItemTags.AXES) && state.is(BlockTags.LOGS) && Perks.has(player, SkillBooks.SHARP_AXE)) {
            event.setNewSpeed(event.getNewSpeed() * 1.15F);
        }
    }

    /** ItemStackDurabilityMixin: wear a tool takes, less for the Strong Arm's pickaxes and the Sharp Axe's axes. */
    public static int wear(ItemStack stack, Entity user, int amount) {
        if (amount <= 0 || !(user instanceof ServerPlayer player)) return amount;
        boolean lighter = (stack.is(ItemTags.PICKAXES) && Perks.has(player, SkillBooks.STRONG_ARM))
                || (stack.is(ItemTags.AXES) && Perks.has(player, SkillBooks.SHARP_AXE));
        if (!lighter) return amount;
        int left = amount;
        for (int i = 0; i < amount; i++) if (player.getRandom().nextFloat() < 0.2F) left--;
        return left;
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        ServerLevel level = event.getLevel();
        BlockState state = event.getState();
        Entity breaker = event.getBreaker();
        if (state.is(Tags.Blocks.ORES)) {
            if (breaker instanceof ServerPlayer p && Perks.has(p, SkillBooks.RICH_SEAMS) && level.random.nextDouble() < SEAM_CHANCE) {
                // not an ore that drops itself (silk touch: placed and broken again it would double forever)
                duplicate(event, d -> !(d.getItem() instanceof BlockItem bi && bi.getBlock() == state.getBlock()));
            }
        } else if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            if (breaker instanceof ServerPlayer p && Perks.has(p, SkillBooks.BOUNTIFUL) && level.random.nextDouble() < BOUNTIFUL_CHANCE) {
                duplicate(event, d -> true);
            }
        } else if (state.is(BlockTags.LEAVES)) {
            Player near = breaker instanceof Player p ? p : level.getNearestPlayer(event.getPos().getX(), event.getPos().getY(),
                    event.getPos().getZ(), 24, false);
            if (near != null && Perks.has(near, SkillBooks.FOREST_GIFT)) {
                boolean sapling = event.getDrops().stream().anyMatch(d -> d.getItem().is(ItemTags.SAPLINGS));
                if (sapling && level.random.nextFloat() < 0.35F) {
                    duplicate(event, d -> d.is(ItemTags.SAPLINGS));
                }
                if (level.random.nextFloat() < 0.4F) drop(event, new ItemStack(Items.STICK));
                if (level.random.nextFloat() < 0.04F) drop(event, new ItemStack(Items.APPLE));
            }
        }
    }

    private static void duplicate(BlockDropsEvent event, java.util.function.Predicate<ItemStack> which) {
        List<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity d : event.getDrops()) {
            if (!which.test(d.getItem())) continue;
            extra.add(new ItemEntity(event.getLevel(), d.getX(), d.getY(), d.getZ(), d.getItem().copy()));
        }
        event.getDrops().addAll(extra);
    }

    private static void drop(BlockDropsEvent event, ItemStack stack) {
        var p = event.getPos();
        event.getDrops().add(new ItemEntity(event.getLevel(), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, stack));
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer p && Perks.animal(event.getEntity()) && Perks.has(p, SkillBooks.PREDATOR)) {
            event.setAmount(event.getAmount() * 1.2F);
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
        }
        if (event.getEntity() instanceof ServerPlayer p && source instanceof Mob
                && com.divinis.dungeon.DungeonService.isDungeon(p.level()) && Perks.has(p, SkillBooks.LABYRINTH_SENSE)) {
            event.setAmount(event.getAmount() * 0.85F);
        }
    }

    @SubscribeEvent
    public static void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getEntity() instanceof Player p && Perks.animal(event.getLookingEntity()) && Perks.has(p, SkillBooks.SILENT_STEP)) {
            event.modifyVisibility(0.5);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && Perks.has(p, SkillBooks.LONG_STRIDE)) {
            event.setDamageMultiplier(event.getDamageMultiplier() * 0.7F);
        }
    }

    /** Skinning: the next animal killed (within its 60 s) drops twice. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !Perks.animal(event.getEntity())) return;
        CharacterData data = CharacterService.data(p);
        if (!data.buff(SkillBooks.SKINNING)) return;
        data.buffs.remove(SkillBooks.SKINNING);
        p.setData(SkillAttachments.CHARACTER, data);
        List<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity d : event.getDrops()) extra.add(new ItemEntity(d.level(), d.getX(), d.getY(), d.getZ(), d.getItem().copy()));
        event.getDrops().addAll(extra);
    }
}
