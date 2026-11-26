package com.divinis.fabric;

import com.divinis.Divinis;
import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.BlockEvent;
import com.divinis.platform.event.EntityJoinLevelEvent;
import com.divinis.platform.event.ExplosionEvent;
import com.divinis.platform.event.LivingDeathEvent;
import com.divinis.platform.event.PlayerEvent;
import com.divinis.platform.event.PlayerInteractEvent;
import com.divinis.platform.event.PlayerTickEvent;
import com.divinis.platform.event.RegisterCommandsEvent;
import com.divinis.platform.event.ServerStartedEvent;
import com.divinis.platform.event.ServerStoppedEvent;
import com.divinis.platform.event.ServerStoppingEvent;
import com.divinis.platform.event.ServerTickEvent;
import com.divinis.platform.event.TagsUpdatedEvent;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.TickEvent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;

/**
 * Divinis on Fabric: starts the common mod and turns Fabric's (and Architectury's) events into Divinis'. What
 * Fabric has no event for comes from the mixins in com.divinis.fabric.mixin.
 */
public final class DivinisFabric implements ModInitializer {
    private static <E> E post(E event) {
        return DivinisBus.post(event);
    }

    private static boolean heard(Class<?> type) {
        return DivinisBus.hasListeners(type);
    }

    @Override
    public void onInitialize() {
        Divinis.init();

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (heard(ServerTickEvent.Pre.class)) post(new ServerTickEvent.Pre(server));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (heard(ServerTickEvent.Post.class)) post(new ServerTickEvent.Post(server));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> post(new ServerStartedEvent(server)));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> post(new ServerStoppingEvent(server)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> post(new ServerStoppedEvent(server)));
        TickEvent.PLAYER_PRE.register(player -> {
            if (heard(PlayerTickEvent.Pre.class)) post(new PlayerTickEvent.Pre(player));
        });
        TickEvent.PLAYER_POST.register(player -> {
            if (heard(PlayerTickEvent.Post.class)) post(new PlayerTickEvent.Post(player));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> post(new PlayerEvent.PlayerLoggedInEvent(handler.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> post(new PlayerEvent.PlayerLoggedOutEvent(handler.player)));
        dev.architectury.event.events.common.PlayerEvent.CRAFT_ITEM.register((player, crafted, inventory) ->
                post(new PlayerEvent.ItemCraftedEvent(player, crafted, inventory)));
        dev.architectury.event.events.common.PlayerEvent.SMELT_ITEM.register((player, smelted) ->
                post(new PlayerEvent.ItemSmeltedEvent(player, smelted)));
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled());
        dev.architectury.event.events.common.BlockEvent.PLACE.register((level, pos, state, entity) ->
                post(new BlockEvent.EntityPlaceEvent(level, pos, state, entity)).isCanceled() ? EventResult.interruptFalse() : EventResult.pass());
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !post(new LivingDeathEvent(entity, source)).isCanceled());
        EntityEvent.ADD.register((entity, level) -> heard(EntityJoinLevelEvent.class)
                && post(new EntityJoinLevelEvent(entity, level)).isCanceled() ? EventResult.interruptFalse() : EventResult.pass());
        dev.architectury.event.events.common.ExplosionEvent.DETONATE.register((level, explosion, entities) ->
                post(new ExplosionEvent.Detonate(level, explosion, entities)));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            PlayerInteractEvent.RightClickBlock d = post(new PlayerInteractEvent.RightClickBlock(player, hand, hit.getBlockPos(), hit));
            if (!d.isCanceled()) return InteractionResult.PASS;
            return d.getCancellationResult() == InteractionResult.PASS ? InteractionResult.FAIL : d.getCancellationResult();
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            PlayerInteractEvent.RightClickItem d = post(new PlayerInteractEvent.RightClickItem(player, hand));
            return d.isCanceled() ? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand));
        });
        CommandRegistrationEvent.EVENT.register((dispatcher, context, selection) -> post(new RegisterCommandsEvent(dispatcher, context, selection)));
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> post(new TagsUpdatedEvent(registries, client)));
    }
}
