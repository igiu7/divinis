package com.divinis.forge;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.StationHost;
import com.pockyl.diegesis.api.state.StationState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Smelting Forge's block entity: keeps the station's state (the shelf, the fuel, the fire), checks every second
 * whether the forge around it is built, and works while nobody is at it.
 */
public class SmeltingForgeBlockEntity extends BlockEntity implements StationHost {
    private final StationState station = new StationState(SmeltingForge.SCHEMA);

    public SmeltingForgeBlockEntity(BlockPos pos, BlockState state) {
        super(SmeltingForge.BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public StationState stationState() {
        return station;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SmeltingForgeBlockEntity forge) {
        if (level.getGameTime() % 20 == 0) {
            boolean built = SmeltingForge.built(level, pos, state.getValue(SmeltingForgeBlock.FACING));
            if (built != state.getValue(SmeltingForgeBlock.FORMED)) {
                if (!built && level instanceof ServerLevel server) {
                    Diegesis.activeStation(server, pos).ifPresent(station -> station.users().forEach(p -> {
                        if (p instanceof net.minecraft.server.level.ServerPlayer sp) Diegesis.close(sp);
                    }));
                }
                SmeltingForge.setFormed(level, pos, state, built);
                return;
            }
        }
        if (level.getGameTime() % SmeltingForge.STEP != 0) return;
        // while someone works at it, the station's logic does the work
        if (level instanceof ServerLevel server && Diegesis.activeStation(server, pos).isPresent()) return;
        if (SmeltingForge.work(forge.station, level, pos)) {
            forge.setChanged();
            level.sendBlockUpdated(pos, state, level.getBlockState(pos), Block.UPDATE_CLIENTS);
        }
    }

    /** Everything on the shelf and in the firebox, to drop when the forge is broken. */
    public java.util.List<ItemStack> contents() {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (ItemStack s : station.getItems(SmeltingForge.INGOTS)) if (!s.isEmpty()) out.add(s.copy());
        for (ItemStack s : station.getItems(SmeltingForge.FUEL)) if (!s.isEmpty()) out.add(s.copy());
        return out;
    }

    /** The formed furnace reaches past the block: three blocks to the back and sides, five up (its chimney). */
    public net.minecraft.world.phys.AABB renderBox() {
        BlockPos p = getBlockPos();
        return new net.minecraft.world.phys.AABB(p.getX() - 3, p.getY(), p.getZ() - 3, p.getX() + 4, p.getY() + 6, p.getZ() + 4);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("station", station.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        station.load(tag.getCompound("station"), registries);
    }

    // clients see the shelf and the fire while nobody works at the forge (an idle_visible scene)
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
