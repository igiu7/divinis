package com.divinis.dungeon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.divinis.util.Msg;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A reward chest of a dungeon run. Only the run's party may open it, and everyone of them has his own 27 slots,
 * filled with his loot (DungeonLoot, by the chest's kind) the first time he opens it - nobody can take somebody
 * else's reward.
 */
public class DungeonChestBlockEntity extends ChestBlockEntity {
    private static final int SIZE = 27;

    private String kind = DungeonLoot.BOSS;
    private int runId = -1;
    private final Map<UUID, NonNullList<ItemStack>> loot = new HashMap<>();

    public DungeonChestBlockEntity(BlockPos pos, BlockState state) {
        super(DungeonRegistry.DUNGEON_CHEST_ENTITY.get(), pos, state);
    }

    public void setup(int runId, String kind) {
        this.runId = runId;
        this.kind = kind;
        setChanged();
    }

    /** Whether the player has opened this chest (and so rolled its loot). */
    public boolean opened(java.util.UUID player) {
        return loot.containsKey(player);
    }

    /** The Dungeon Scout's Keen Eye: about 15% more - each stack 15% bigger (by chance), or a one-off one doubled 15% of the time. */
    private static void keenEye(List<ItemStack> rolled, net.minecraft.util.RandomSource random) {
        List<ItemStack> extra = new java.util.ArrayList<>();
        for (ItemStack s : rolled) {
            if (s.getMaxStackSize() > 1) {
                float more = s.getCount() * 0.15F;
                int add = (int) more + (random.nextFloat() < more - (int) more ? 1 : 0);
                s.setCount(Math.min(s.getMaxStackSize(), s.getCount() + add));
            } else if (random.nextFloat() < 0.15F) {
                extra.add(s.copy());
            }
        }
        rolled.addAll(extra);
    }

    void open(ServerPlayer player) {
        if (!DungeonService.isMember(runId, player)) {
            player.displayClientMessage(Msg.text("This reward belongs to the party of this dungeon.", ChatFormatting.RED), true);
            return;
        }
        NonNullList<ItemStack> items = loot.computeIfAbsent(player.getUUID(), id -> {
            NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
            List<ItemStack> rolled = DungeonLoot.roll(kind, player.getRandom());
            if (com.divinis.skill.Perks.has(player, com.divinis.skill.SkillBooks.KEEN_EYE)) keenEye(rolled, player.getRandom());
            int first = Math.max(0, SIZE / 2 - rolled.size() / 2);
            for (int i = 0; i < rolled.size() && first + i < SIZE; i++) list.set(first + i, rolled.get(i));
            setChanged();
            return list;
        });
        SimpleContainer view = new SimpleContainer(SIZE) {
            @Override
            public void setChanged() {
                super.setChanged();
                for (int i = 0; i < SIZE; i++) items.set(i, getItem(i));
                DungeonChestBlockEntity.this.setChanged();
            }

            @Override
            public boolean stillValid(Player p) {
                return !isRemoved() && p.distanceToSqr(getBlockPos().getCenter()) < 64;
            }
        };
        for (int i = 0; i < SIZE; i++) view.getItems().set(i, items.get(i));
        level.playSound(null, getBlockPos(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.6F, 0.9F);
        if (DungeonLoot.BOSS.equals(kind)) DungeonService.bossChestOpened(runId, player);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, view),
                Component.literal("Your Reward")));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("kind", kind);
        tag.putInt("run", runId);
        ListTag list = new ListTag();
        loot.forEach((id, items) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            ContainerHelper.saveAllItems(t, items, registries);
            list.add(t);
        });
        tag.put("rewards", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        kind = tag.getString("kind").isEmpty() ? DungeonLoot.BOSS : tag.getString("kind");
        runId = tag.contains("run") ? tag.getInt("run") : -1;
        loot.clear();
        for (Tag raw : tag.getList("rewards", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(t, items, registries);
            loot.put(t.getUUID("id"), items);
        }
    }
}
