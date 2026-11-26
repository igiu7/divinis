package com.divinis.forge;

import java.util.HashMap;
import java.util.Map;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.divinis.registry.ModIngots;
import com.divinis.registry.ModWeapons;
import com.divinis.Divinis;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * The cauldron as a Diegesis station (MIT, by Pocky): using one flies the camera over it, the inventory below. A stack
 * dragged onto the cauldron (or shift-clicked in the inventory) is used on it, one at a time, as vanilla would:
 *  - any fluid container (iron and bronze buckets, other mods' tanks - NeoForge's fluid handlers on the item and on the
 *    cauldron): a full one pours into the cauldron, an empty one is filled from it - a bucket only from a full cauldron,
 *    as in vanilla, and into an empty one;
 *  - bottles: a glass bottle takes one level of water, a water bottle gives one (and makes an empty cauldron a water
 *    cauldron); a powder snow bucket fills an empty cauldron, an empty bucket takes a full one's powder snow;
 *  - hot metal (ModIngots.HOT_INGOTS, ModWeapons.HOT_PARTS) dipped in water is quenched: it comes out cold (the ingot,
 *    the part), with steam and a hiss, straight into the inventory (on the ground if it is full).
 * The logic plays effects for everyone around (POUR, FILL, QUENCH); client.forge.CauldronBehavior animates them.
 */
public final class CauldronStation {
    private CauldronStation() {}

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "cauldron");
    public static final String USE = "use";
    public static final ResourceLocation POUR = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "cauldron_pour");
    public static final ResourceLocation FILL = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "cauldron_fill");
    public static final ResourceLocation QUENCH = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "cauldron_quench");

    public static void register() {
        Diegesis.station(ID).blocks(Blocks.CAULDRON, Blocks.WATER_CAULDRON, Blocks.LAVA_CAULDRON, Blocks.POWDER_SNOW_CAULDRON)
                .logic(Logic::new).register();
    }

    private static Map<Item, Item> cooled;

    /** What hot metal becomes once quenched (a hot ingot its ingot, a hot part its part), or null. */
    public static Item cooled(ItemStack stack) {
        if (cooled == null) {
            Map<Item, Item> map = new HashMap<>();
            ModIngots.HOT_INGOTS.forEach((metal, hot) -> map.put(hot.get(), ForgeShapes.ingot(metal)));
            ModWeapons.HOT_PARTS.forEach((id, hot) -> {
                var part = ModWeapons.PARTS.get(id.replace("_hot_", "_"));
                if (part != null) map.put(hot.get(), part.get());
            });
            cooled = map;
        }
        return stack.isEmpty() ? null : cooled.get(stack.getItem());
    }

    /** Whether the stack is something the cauldron does anything with (the inventory below shows only these). */
    public static boolean usable(ItemStack stack) {
        return cooled(stack) != null || stack.is(Items.GLASS_BOTTLE) || isWaterBottle(stack) || stack.is(Items.POWDER_SNOW_BUCKET)
                || stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET);
    }

    static boolean isWaterBottle(ItemStack stack) {
        PotionContents potion = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        return stack.is(Items.POTION) && potion != null && potion.is(Potions.WATER);
    }

    /** Water in the cauldron, levels 0..3. */
    public static int waterLevel(BlockState state) {
        return state.is(Blocks.WATER_CAULDRON) ? state.getValue(LayeredCauldronBlock.LEVEL) : 0;
    }

    private static final class Logic implements StationLogic {
        Logic(StationContext station) {}

        @Override
        public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
            if (!USE.equals(action)) return;
            int from = data.contains("from") ? data.getInt("from") : -1;
            if (from >= player.getInventory().items.size()) return;
            ItemStack held = from >= 0 ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
            if (held.isEmpty()) return;
            boolean quenching = cooled(held) != null;
            ItemStack result = use(station, player, held.copyWithCount(1));
            if (result == null) return;
            held.shrink(1);
            if (quenching) {
                // the quenched piece goes straight into the inventory, or on the ground when it is full
                if (from < 0 && held.isEmpty()) player.inventoryMenu.setCarried(ItemStack.EMPTY);
                player.getInventory().placeItemBackInInventory(result);
            } else if (held.isEmpty()) {
                // a container is replaced by what it became: in its place if it was alone, else into the inventory
                if (from >= 0) player.getInventory().setItem(from, result);
                else player.inventoryMenu.setCarried(result);
            } else if (!result.isEmpty()) {
                player.getInventory().placeItemBackInInventory(result);
            }
            player.inventoryMenu.broadcastChanges();
        }

        /** Uses one item on the cauldron: what it becomes, or null if nothing happens. */
        private static ItemStack use(StationContext station, Player player, ItemStack one) {
            Level level = station.level();
            BlockPos pos = station.pos();
            BlockState state = level.getBlockState(pos);
            int water = waterLevel(state);
            Item cold = cooled(one);
            if (cold != null) {
                if (water == 0) return deny(player, "The hot metal needs water to be quenched.");
                CompoundTag fx = new CompoundTag();
                fx.putString("hot", BuiltInRegistries.ITEM.getKey(one.getItem()).toString());
                fx.putString("cold", BuiltInRegistries.ITEM.getKey(cold).toString());
                station.effect(QUENCH, "cauldron", fx);
                level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.9F, 1.1F);
                return new ItemStack(cold);
            }
            if (one.is(Items.GLASS_BOTTLE)) {
                if (water == 0) return deny(player, "There is no water in the cauldron.");
                lower(level, pos, state);
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                effect(station, FILL, Fluids.WATER);
                return PotionContents.createItemStack(Items.POTION, Potions.WATER);
            }
            if (isWaterBottle(one)) {
                if (state.is(Blocks.CAULDRON)) {
                    level.setBlockAndUpdate(pos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 1));
                } else if (state.is(Blocks.WATER_CAULDRON) && water < 3) {
                    level.setBlockAndUpdate(pos, state.setValue(LayeredCauldronBlock.LEVEL, water + 1));
                } else {
                    return deny(player, state.is(Blocks.WATER_CAULDRON) ? "The cauldron is full." : "Something else is in the cauldron.");
                }
                level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                effect(station, POUR, Fluids.WATER);
                return new ItemStack(Items.GLASS_BOTTLE);
            }
            if (one.is(Items.POWDER_SNOW_BUCKET)) {
                if (!state.is(Blocks.CAULDRON)) return deny(player, "The cauldron must be empty.");
                level.setBlockAndUpdate(pos, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_POWDER_SNOW, SoundSource.BLOCKS, 1.0F, 1.0F);
                return new ItemStack(Items.BUCKET);
            }
            if (one.is(Items.BUCKET) && state.is(Blocks.POWDER_SNOW_CAULDRON)) {
                if (state.getValue(LayeredCauldronBlock.LEVEL) < 3) return deny(player, "A bucket takes only a full cauldron.");
                level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
                level.playSound(null, pos, SoundEvents.BUCKET_FILL_POWDER_SNOW, SoundSource.BLOCKS, 1.0F, 1.0F);
                return new ItemStack(Items.POWDER_SNOW_BUCKET);
            }
            // a full bucket (water, lava) pours in, an empty one is filled from a full cauldron
            if (one.is(Items.WATER_BUCKET) || one.is(Items.LAVA_BUCKET)) {
                if (!state.is(Blocks.CAULDRON)) return deny(player, "The cauldron must be empty to pour a whole container in.");
                boolean pourWater = one.is(Items.WATER_BUCKET);
                level.setBlockAndUpdate(pos, pourWater ? Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3)
                        : Blocks.LAVA_CAULDRON.defaultBlockState());
                level.playSound(null, pos, pourWater ? SoundEvents.BUCKET_EMPTY : SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
                effect(station, POUR, pourWater ? net.minecraft.world.level.material.Fluids.WATER : net.minecraft.world.level.material.Fluids.LAVA);
                return new ItemStack(Items.BUCKET);
            }
            if (!one.is(Items.BUCKET)) return null;
            if (state.is(Blocks.CAULDRON)) return deny(player, "The cauldron is empty.");
            boolean lava = state.is(Blocks.LAVA_CAULDRON);
            if (!lava && !(state.is(Blocks.WATER_CAULDRON) && state.getValue(LayeredCauldronBlock.LEVEL) == 3)) {
                return deny(player, "There is not enough in the cauldron to fill it (a bucket takes a full cauldron).");
            }
            level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
            level.playSound(null, pos, lava ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            effect(station, FILL, lava ? net.minecraft.world.level.material.Fluids.LAVA : net.minecraft.world.level.material.Fluids.WATER);
            return new ItemStack(lava ? Items.LAVA_BUCKET : Items.WATER_BUCKET);
        }

        private static void lower(Level level, BlockPos pos, BlockState state) {
            int water = state.getValue(LayeredCauldronBlock.LEVEL);
            level.setBlockAndUpdate(pos, water <= 1 ? Blocks.CAULDRON.defaultBlockState() : state.setValue(LayeredCauldronBlock.LEVEL, water - 1));
        }

        private static void effect(StationContext station, ResourceLocation id, Fluid fluid) {
            CompoundTag fx = new CompoundTag();
            fx.putString("fluid", BuiltInRegistries.FLUID.getKey(fluid).toString());
            station.effect(id, "cauldron", fx);
        }

        private static ItemStack deny(Player player, String why) {
            player.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.GOLD), true);
            return null;
        }
    }
}
