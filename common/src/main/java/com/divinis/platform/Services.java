package com.divinis.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What each loader does its own way. Every method here is implemented by com.divinis.platform.fabric.ServicesImpl
 * and com.divinis.platform.neoforge.ServicesImpl (Architectury puts the right one in place of the body).
 */
public final class Services {
    private Services() {}

    /** Makes the data key known to the loader (during the mod's start-up). */
    @ExpectPlatform
    public static <T> void registerData(DataKey<T> key) {
        throw new AssertionError();
    }

    /** The entity's value of the key (created when it has none). */
    @ExpectPlatform
    public static <T> T getData(Entity entity, DataKey<T> key) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static <T> void setData(Entity entity, DataKey<T> key, T value) {
        throw new AssertionError();
    }

    /** Free NBT kept with the entity across saves (not copied when a player respawns). */
    @ExpectPlatform
    public static CompoundTag persistentData(Entity entity) {
        throw new AssertionError();
    }

    /** Whether the player may break the block there (claims, protections: the loader's block-break event). */
    @ExpectPlatform
    public static boolean canBreak(ServerPlayer player, BlockPos pos, BlockState state) {
        throw new AssertionError();
    }

    /** The physical side the game runs on. */
    public static Dist dist() {
        return dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT ? Dist.CLIENT : Dist.DEDICATED_SERVER;
    }

    public static boolean isModLoaded(String modid) {
        return dev.architectury.platform.Platform.isModLoaded(modid);
    }

    public static java.nio.file.Path configDir() {
        return dev.architectury.platform.Platform.getConfigFolder();
    }
}
