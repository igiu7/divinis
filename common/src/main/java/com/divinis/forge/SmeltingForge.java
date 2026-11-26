package com.divinis.forge;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.divinis.registry.ModBlocks;
import com.divinis.registry.ModIngots;
import com.divinis.Divinis;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import com.divinis.platform.bus.IEventBus;
import com.divinis.platform.registry.DeferredBlock;
import com.divinis.platform.registry.DeferredItem;
import com.divinis.platform.registry.DeferredRegister;

import java.util.function.Supplier;

/**
 * The Smelting Forge: a furnace that heats rare-metal ingots for the anvil, built on Diegesis (MIT, by Pocky). It is a
 * multiblock: a 3x3x3 of Forge Bricks with the Smelting Forge block at the front of the bottom layer (the firebox), the
 * block above it (the charging mouth) and the middle of the cube (the hearth) left empty (structure). The controller
 * checks it every second; built right, the forge forms (FORMED on the controller and the bricks: the blocks are hidden
 * and client.forge.SmeltingForgeRenderer draws the whole furnace, tools/models/build_smelting_forge.py), broken, it
 * comes apart. Only a formed forge opens: the camera flies to its front, six ingots go on the hearth, coal into the
 * firebox (both kept in the block). Coal does not light by itself: the fire is fanned with the bellows beside the
 * forge, worked up and down with the mouse (the client sends each stroke, PUMP) - five strokes bring a cold fire to a
 * smoulder, three more to a full fire (FIRE 0, 1, 2: three models, the forge giving more light). Left alone, a fire
 * sinks a step every three minutes (FADE_TICKS: full to a smoulder, a smoulder to out); a stroke of the bellows starts
 * the three minutes again. Both a smoulder and a full fire burn coal; when it runs out the fire goes out. Coal in the firebox lights it - the block turns to its burning model, the coal glowing, the forge giving
 * light - and burns one piece at a time: coal and charcoal 80 seconds, a block of coal (or of charcoal) nine times as
 * long. Only in a full fire does every ingot on the hearth heat, and after HEAT_TICKS it is a hot ingot
 * (ModIngots.HOT_INGOTS).
 * The work runs every 10 ticks, in the block entity while nobody is at the forge and in the station's logic while
 * someone is (client: client.forge.SmeltingForgeBehavior, scene assets/divinis/diegesis/scenes/smelting_forge.json).
 */
public final class SmeltingForge {
    private SmeltingForge() {}

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "smelting_forge");
    public static final String INGOTS = "ingots";
    public static final String FUEL = "fuel";
    public static final int SHELF = 6;
    /** Ticks an ingot takes to heat through; a piece of coal burns COAL_TICKS, a block of it nine times as long. */
    public static final int HEAT_TICKS = 300;
    public static final int COAL_TICKS = 1600;
    public static final int STEP = 10;
    /** The fire: out, a smoulder, a full fire; and the bellows strokes that bring it from one to the next. */
    public static final int OUT = 0, SMOULDER = 1, FULL = 2;
    public static final int[] STROKES = {5, 3};
    /** Ticks a fire keeps without the bellows before it sinks a step: a full fire to a smoulder, a smoulder to out. */
    public static final int FADE_TICKS = 3 * 60 * 20;
    public static final String PUMP = "pump";
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty FIRE =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("fire", 0, 2);
    private static final TagKey<Item> CHARCOAL_BLOCKS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "storage_blocks/charcoal"));

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(INGOTS, SHELF, SlotRules.DEFAULT.maxStack(1).accepts(stack -> AnvilForge.metalOf(stack) != null).kept())
            .slots(FUEL, 1, SlotRules.DEFAULT.accepts(stack -> fuelTicks(stack) > 0).kept())
            .build();

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Divinis.MODID);

    public static final DeferredBlock<SmeltingForgeBlock> BLOCK = ModBlocks.BLOCKS.register("smelting_forge",
            () -> new SmeltingForgeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(4.0F, 6.0F)
                    .requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS).noOcclusion()
                    .lightLevel(s -> s.getValue(FIRE) == FULL ? 13 : s.getValue(FIRE) == SMOULDER ? 6 : 0)));
    public static final DeferredItem<BlockItem> ITEM = ModBlocks.ITEMS.register("smelting_forge",
            () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    public static final DeferredBlock<ForgeBricksBlock> BRICKS = ModBlocks.BLOCKS.register("forge_bricks",
            () -> new ForgeBricksBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(2.5F, 6.0F)
                    .requiresCorrectToolForDrops().sound(SoundType.MUD_BRICKS)));
    public static final DeferredItem<BlockItem> BRICKS_ITEM = ModBlocks.ITEMS.register("forge_bricks",
            () -> new BlockItem(BRICKS.get(), new Item.Properties()));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<SmeltingForgeBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register("smelting_forge",
            () -> BlockEntityType.Builder.of(SmeltingForgeBlockEntity::new, BLOCK.get()).build(null));

    /** Call before ModBlocks.register (the block and its item join its registers). */
    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }

    /** Registers the station (mod setup, both sides). */
    public static void registerStation() {
        Diegesis.station(ID).block(BLOCK).schema(SCHEMA).logic(Logic::new)
                .openWhen((player, pos, state) -> state.hasProperty(SmeltingForgeBlock.FORMED) && state.getValue(SmeltingForgeBlock.FORMED))
                .register();
    }

    /**
     * The blocks of the forge whose firebox (the controller) is at pos, its front facing that way: the 3x3x3 behind and
     * above it, but for the controller itself, the charging mouth over it and the hearth in the middle. dx runs across,
     * dy up, dz back from the front.
     */
    public static java.util.List<BlockPos> structure(BlockPos pos, net.minecraft.core.Direction facing) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        net.minecraft.core.Direction across = facing.getClockWise(), back = facing.getOpposite();
        for (int dy = 0; dy < 3; dy++) {
            for (int dz = 0; dz < 3; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;        // the controller
                    if (dx == 0 && dy == 1 && dz <= 1) continue;        // the charging mouth and the hearth
                    out.add(pos.relative(across, dx).above(dy).relative(back, dz));
                }
            }
        }
        return out;
    }

    /** The empty places the forge needs: the charging mouth and the hearth. */
    public static java.util.List<BlockPos> openings(BlockPos pos, net.minecraft.core.Direction facing) {
        BlockPos mouth = pos.above();
        return java.util.List.of(mouth, mouth.relative(facing.getOpposite()));
    }

    /** Whether the forge at pos is built right. */
    static boolean built(Level level, BlockPos pos, net.minecraft.core.Direction facing) {
        for (BlockPos p : structure(pos, facing)) if (!level.getBlockState(p).is(BRICKS.get())) return false;
        for (BlockPos p : openings(pos, facing)) if (!level.getBlockState(p).isAir()) return false;
        return true;
    }

    /** Forms the forge (the bricks hidden, the furnace drawn) or takes it apart. */
    static void setFormed(Level level, BlockPos pos, BlockState controller, boolean formed) {
        net.minecraft.core.Direction facing = controller.getValue(SmeltingForgeBlock.FACING);
        level.setBlock(pos, controller.setValue(SmeltingForgeBlock.FORMED, formed)
                .setValue(FIRE, formed ? controller.getValue(FIRE) : OUT), 3);
        for (BlockPos p : structure(pos, facing)) {
            BlockState b = level.getBlockState(p);
            if (b.is(BRICKS.get()) && b.getValue(ForgeBricksBlock.FORMED) != formed) {
                level.setBlock(p, b.setValue(ForgeBricksBlock.FORMED, formed), 3);
            }
        }
        level.playSound(null, pos, formed ? SoundEvents.FIRECHARGE_USE : SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS,
                formed ? 0.6F : 1.0F, formed ? 0.6F : 0.8F);
    }

    /** How long a fuel burns, ticks: coal and charcoal one piece's time, their blocks nine times it; 0 for anything else. */
    public static int fuelTicks(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(Items.COAL_BLOCK) || stack.is(CHARCOAL_BLOCKS)) return COAL_TICKS * 9;
        if (stack.is(ItemTags.COALS)) return COAL_TICKS;
        return 0;
    }

    /** Takes a piece of fuel from the firebox into the fire: its burn time, or 0 when there is none. */
    private static int feed(StationState state) {
        ItemStack fuel = state.getItem(SlotRef.of(FUEL));
        int ticks = fuelTicks(fuel);
        if (ticks > 0) {
            ItemStack rest = fuel.copy();
            rest.shrink(1);
            state.setItem(SlotRef.of(FUEL), rest);
        }
        return ticks;
    }

    /**
     * Every STEP ticks: a fire (smoulder or full) burns its fuel, taking the next piece when one is spent and going out
     * when there is none; a full fire heats the hearth. True if anything changed (to save and send).
     */
    static boolean work(StationState state, Level level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        if (!here.hasProperty(SmeltingForgeBlock.FORMED) || !here.getValue(SmeltingForgeBlock.FORMED)) return false;
        CompoundTag data = state.data();
        int fire = data.getInt("fire");
        int burn = data.getInt("burn");
        int[] heat = data.getIntArray("heat");
        if (heat.length != SHELF) heat = new int[SHELF];
        boolean changed = false;
        if (fire > OUT) {
            // left alone, the fire sinks a step every FADE_TICKS
            int since = data.getInt("since") + STEP;
            if (since >= FADE_TICKS) {
                fire--;
                since = 0;
                data.putInt("pumps", 0);
                changed = true;   // saved even when it has just gone out, or it would sink (and hiss) again every step
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, fire == OUT ? 0.7F : 1.0F);
            }
            data.putInt("since", since);
        }
        if (fire > OUT) {
            if (burn <= 0) {
                burn = feed(state);
                if (burn <= 0) {
                    fire = OUT;
                    data.putInt("pumps", 0);
                    level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 0.8F);
                }
            }
            burn = Math.max(0, burn - STEP);
            changed = true;
        }
        for (int i = 0; i < SHELF; i++) {
            SlotRef slot = SlotRef.of(INGOTS, i);
            ItemStack stack = state.getItem(slot);
            String metal = AnvilForge.metalOf(stack);
            if (metal == null) {
                if (heat[i] != 0) {
                    heat[i] = 0;
                    changed = true;
                }
                continue;
            }
            if (fire != FULL) continue;
            heat[i] += STEP;
            changed = true;
            if (heat[i] >= HEAT_TICKS) {
                state.setItem(slot, new ItemStack(ModIngots.HOT_INGOTS.get(metal).get(), stack.getCount()));
                heat[i] = 0;
                level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.25F, 1.6F);
            }
        }
        if (changed) {
            data.putInt("fire", fire);
            data.putInt("burn", burn);
            data.putIntArray("heat", heat);
            state.setData(data);
        }
        showFire(level, pos, fire);
        return changed;
    }

    private static void showFire(Level level, BlockPos pos, int fire) {
        BlockState block = level.getBlockState(pos);
        if (block.hasProperty(FIRE) && block.getValue(FIRE) != fire) level.setBlock(pos, block.setValue(FIRE, fire), 3);
    }

    /**
     * A stroke of the bellows: with fuel in the firebox (or a piece still burning), it counts towards the next fire -
     * five strokes from out to a smoulder, three from a smoulder to a full fire. At most one stroke every 4 ticks.
     */
    static void pump(StationContext station, Player player) {
        StationState state = station.state();
        Level level = station.level();
        CompoundTag data = state.data();
        long now = level.getGameTime();
        if (now - data.getLong("lastPump") < 4) return;
        data.putLong("lastPump", now);
        int fire = data.getInt("fire");
        int burn = data.getInt("burn");
        level.playSound(null, station.pos(), SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.6F, 0.6F);
        // every stroke keeps the fire up for another FADE_TICKS
        data.putInt("since", 0);
        if (fire == FULL) {
            state.setData(data);
            return;
        }
        if (burn <= 0 && fuelTicks(state.getItem(SlotRef.of(FUEL))) <= 0) {
            state.setData(data);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Lay coal in the firebox first.")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
            return;
        }
        int pumps = data.getInt("pumps") + 1;
        if (pumps >= STROKES[fire]) {
            pumps = 0;
            fire++;
            if (burn <= 0) burn = feed(state);
            level.playSound(null, station.pos(), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5F, fire == FULL ? 1.0F : 0.7F);
        }
        data.putInt("pumps", pumps);
        data.putInt("fire", fire);
        data.putInt("burn", burn);
        state.setData(data);
        showFire(level, station.pos(), fire);
    }

    private static final class Logic implements StationLogic {
        Logic(StationContext station) {}

        @Override
        public void tick(StationContext station) {
            if (station.level().getGameTime() % STEP == 0) work(station.state(), station.level(), station.pos());
        }

        @Override
        public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
            if (PUMP.equals(action)) pump(station, player);
        }
    }
}
