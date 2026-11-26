package com.divinis.dungeon;

import com.divinis.registry.ModBlocks;
import com.divinis.Divinis;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The dungeons' blocks and items. The chest and the crystal join ModBlocks' registers. */
public final class DungeonRegistry {
    private DungeonRegistry() {}

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Divinis.MODID);

    /** The reward chest after a boss: everyone of the party finds his own loot in it. No item, cannot be broken. */
    public static final DeferredBlock<DungeonChestBlock> DUNGEON_CHEST = ModBlocks.BLOCKS.register("dungeon_chest",
            () -> new DungeonChestBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DungeonChestBlockEntity>> DUNGEON_CHEST_ENTITY =
            BLOCK_ENTITIES.register("dungeon_chest",
                    () -> BlockEntityType.Builder.of(DungeonChestBlockEntity::new, DUNGEON_CHEST.get()).build(null));

    /** Dark blue crystals growing out of deepslate (decoration; the Golem's crystals). */
    public static final DeferredBlock<DeepslateCrystalBlock> DEEPSLATE_CRYSTAL = ModBlocks.BLOCKS.register("deepslate_crystal",
            () -> new DeepslateCrystalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST_CLUSTER)
                    .lightLevel(s -> 5)
                    .noOcclusion()));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> DEEPSLATE_CRYSTAL_ITEM = ModBlocks.ITEMS.register("deepslate_crystal",
            () -> new net.minecraft.world.item.BlockItem(DEEPSLATE_CRYSTAL.get(), new Item.Properties()));

    /** Dropped by the monsters of the dungeons. */
    public static final DeferredItem<Item> LABYRINTH_CRYSTAL = ModBlocks.ITEMS.register("labyrinth_crystal",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    /** The keyhole plate of a locked Labyrinth door (bronze, silver or gold). No item, cannot be broken. */
    public static final DeferredBlock<LabyrinthLockBlock> LABYRINTH_LOCK = ModBlocks.BLOCKS.register("labyrinth_lock",
            () -> new LabyrinthLockBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(s -> 6)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)));

    public static final DeferredItem<LabyrinthKeyItem> BRONZE_KEY = key("labyrinth_key_bronze", KeyType.BRONZE, Rarity.UNCOMMON);
    public static final DeferredItem<LabyrinthKeyItem> SILVER_KEY = key("labyrinth_key_silver", KeyType.SILVER, Rarity.UNCOMMON);
    public static final DeferredItem<LabyrinthKeyItem> GOLD_KEY = key("labyrinth_key_gold", KeyType.GOLD, Rarity.RARE);

    private static DeferredItem<LabyrinthKeyItem> key(String id, KeyType type, Rarity rarity) {
        return ModBlocks.ITEMS.register(id, () -> new LabyrinthKeyItem(type, new Item.Properties().stacksTo(1).rarity(rarity)));
    }

    public static Item keyItem(KeyType type) {
        return switch (type) {
            case BRONZE -> BRONZE_KEY.get();
            case SILVER -> SILVER_KEY.get();
            case GOLD -> GOLD_KEY.get();
        };
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
