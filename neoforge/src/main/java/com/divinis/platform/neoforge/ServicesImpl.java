package com.divinis.platform.neoforge;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.divinis.Divinis;
import com.divinis.platform.DataKey;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Services on NeoForge: attachments, the entity's persistent data, the block-break event. */
public final class ServicesImpl {
    public static IEventBus MOD_BUS;
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Divinis.MODID);
    private static final Map<DataKey<?>, Supplier<AttachmentType<?>>> TYPES = new HashMap<>();

    private ServicesImpl() {}

    public static <T> void registerData(DataKey<T> key) {
        Supplier<AttachmentType<?>> type = ATTACHMENTS.register(key.id.getPath(), () -> {
            AttachmentType.Builder<T> b = AttachmentType.builder(key.defaults).serialize(key.codec);
            if (key.copyOnDeath) b.copyOnDeath();
            return b.build();
        });
        TYPES.put(key, type);
    }

    @SuppressWarnings("unchecked")
    private static <T> AttachmentType<T> type(DataKey<T> key) {
        return (AttachmentType<T>) TYPES.get(key).get();
    }

    public static <T> T getData(Entity entity, DataKey<T> key) {
        return entity.getData(type(key));
    }

    public static <T> void setData(Entity entity, DataKey<T> key, T value) {
        entity.setData(type(key), value);
    }

    public static CompoundTag persistentData(Entity entity) {
        return entity.getPersistentData();
    }

    public static boolean canBreak(ServerPlayer player, BlockPos pos, BlockState state) {
        return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(player.level(), pos, state, player)).isCanceled();
    }
}
