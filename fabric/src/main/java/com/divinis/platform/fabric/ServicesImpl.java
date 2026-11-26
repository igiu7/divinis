package com.divinis.platform.fabric;

import com.divinis.Divinis;
import com.divinis.platform.DataKey;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/** Services on Fabric: data attachments (Fabric API), the block-break event. */
public final class ServicesImpl {
    private static final AttachmentType<CompoundTag> PERSISTENT = AttachmentRegistry.<CompoundTag>builder()
            .persistent(CompoundTag.CODEC).initializer(CompoundTag::new)
            .buildAndRegister(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "persistent"));

    private ServicesImpl() {}

    public static <T> void registerData(DataKey<T> key) {
        AttachmentRegistry.Builder<T> b = AttachmentRegistry.<T>builder().persistent(key.codec).initializer(key.defaults);
        if (key.copyOnDeath) b.copyOnDeath();
        key.handle = b.buildAndRegister(key.id);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getData(Entity entity, DataKey<T> key) {
        return entity.getAttachedOrCreate((AttachmentType<T>) key.handle);
    }

    @SuppressWarnings("unchecked")
    public static <T> void setData(Entity entity, DataKey<T> key, T value) {
        entity.setAttached((AttachmentType<T>) key.handle, value);
    }

    public static CompoundTag persistentData(Entity entity) {
        return entity.getAttachedOrCreate(PERSISTENT);
    }

    public static boolean canBreak(ServerPlayer player, BlockPos pos, BlockState state) {
        return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(player.level(), player, pos, state, player.level().getBlockEntity(pos));
    }
}
