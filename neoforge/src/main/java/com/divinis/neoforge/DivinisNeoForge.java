package com.divinis.neoforge;

import com.divinis.Divinis;
import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.BabyEntitySpawnEvent;
import com.divinis.platform.event.BlockDropsEvent;
import com.divinis.platform.event.BlockEvent;
import com.divinis.platform.event.CriticalHitEvent;
import com.divinis.platform.event.EntityJoinLevelEvent;
import com.divinis.platform.event.EntityTeleportEvent;
import com.divinis.platform.event.ExplosionEvent;
import com.divinis.platform.event.FinalizeSpawnEvent;
import com.divinis.platform.event.ItemTooltipEvent;
import com.divinis.platform.event.LivingDeathEvent;
import com.divinis.platform.event.LivingDropsEvent;
import com.divinis.platform.event.LivingEvent;
import com.divinis.platform.event.LivingFallEvent;
import com.divinis.platform.event.LivingHealEvent;
import com.divinis.platform.event.LivingIncomingDamageEvent;
import com.divinis.platform.event.LivingShieldBlockEvent;
import com.divinis.platform.event.PlayerEvent;
import com.divinis.platform.event.PlayerInteractEvent;
import com.divinis.platform.event.PlayerTickEvent;
import com.divinis.platform.event.RegisterCommandsEvent;
import com.divinis.platform.event.ServerStartedEvent;
import com.divinis.platform.event.ServerStoppedEvent;
import com.divinis.platform.event.ServerStoppingEvent;
import com.divinis.platform.event.ServerTickEvent;
import com.divinis.platform.event.TagsUpdatedEvent;
import com.divinis.platform.neoforge.ServicesImpl;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Divinis on NeoForge: starts the common mod and turns NeoForge's events into Divinis' (each one only while
 * something listens to it), writing back what the listeners changed.
 */
@Mod(Divinis.MODID)
public final class DivinisNeoForge {
    public DivinisNeoForge(IEventBus modBus) {
        ServicesImpl.MOD_BUS = modBus;
        Divinis.init();
        ServicesImpl.ATTACHMENTS.register(modBus);
        if (FMLEnvironment.dist.isClient()) DivinisNeoForgeClient.init(modBus);
        gameEvents(NeoForge.EVENT_BUS);
    }

    private static <E> E post(E event) {
        return DivinisBus.post(event);
    }

    private static boolean heard(Class<?> type) {
        return DivinisBus.hasListeners(type);
    }

    private static void gameEvents(IEventBus bus) {
        bus.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Pre e) -> {
            if (heard(ServerTickEvent.Pre.class)) post(new ServerTickEvent.Pre(e.getServer()));
        });
        bus.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> {
            if (heard(ServerTickEvent.Post.class)) post(new ServerTickEvent.Post(e.getServer()));
        });
        bus.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent e) -> post(new ServerStartedEvent(e.getServer())));
        bus.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> post(new ServerStoppingEvent(e.getServer())));
        bus.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> post(new ServerStoppedEvent(e.getServer())));
        bus.addListener((net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre e) -> {
            if (heard(PlayerTickEvent.Pre.class)) post(new PlayerTickEvent.Pre(e.getEntity()));
        });
        bus.addListener((net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) -> {
            if (heard(PlayerTickEvent.Post.class)) post(new PlayerTickEvent.Post(e.getEntity()));
        });
        bus.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) ->
                post(new PlayerEvent.PlayerLoggedInEvent(e.getEntity())));
        bus.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) ->
                post(new PlayerEvent.PlayerLoggedOutEvent(e.getEntity())));
        bus.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed e) -> {
            if (!heard(PlayerEvent.BreakSpeed.class)) return;
            PlayerEvent.BreakSpeed d = post(new PlayerEvent.BreakSpeed(e.getEntity(), e.getState(), e.getNewSpeed(),
                    e.getPosition().orElse(null)));
            e.setNewSpeed(d.getNewSpeed());
        });
        bus.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent e) ->
                post(new PlayerEvent.ItemCraftedEvent(e.getEntity(), e.getCrafting(), e.getInventory())));
        bus.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemSmeltedEvent e) ->
                post(new PlayerEvent.ItemSmeltedEvent(e.getEntity(), e.getSmelting())));
        bus.addListener((net.neoforged.neoforge.event.level.BlockEvent.BreakEvent e) -> {
            BlockEvent.BreakEvent d = post(new BlockEvent.BreakEvent(e.getLevel(), e.getPos(), e.getState(), e.getPlayer()));
            if (d.isCanceled()) e.setCanceled(true);
        });
        bus.addListener((net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent e) -> {
            BlockEvent.EntityPlaceEvent d = post(new BlockEvent.EntityPlaceEvent(e.getLevel(), e.getPos(), e.getPlacedBlock(), e.getEntity()));
            if (d.isCanceled()) e.setCanceled(true);
        });
        bus.addListener((net.neoforged.neoforge.event.level.BlockDropsEvent e) -> {
            BlockDropsEvent d = post(new BlockDropsEvent(e.getLevel(), e.getPos(), e.getState(), e.getBlockEntity(), e.getDrops(),
                    e.getBreaker(), e.getTool()));
            if (d.isCanceled()) e.setCanceled(true);
        });
        bus.addListener((net.neoforged.neoforge.event.entity.living.LivingDeathEvent e) -> {
            LivingDeathEvent d = post(new LivingDeathEvent(e.getEntity(), e.getSource()));
            if (d.isCanceled()) e.setCanceled(true);
        });
        bus.addListener((net.neoforged.neoforge.event.entity.living.LivingDropsEvent e) -> {
            LivingDropsEvent d = post(new LivingDropsEvent(e.getEntity(), e.getSource(), e.getDrops(), e.isRecentlyHit()));
            if (d.isCanceled()) e.setCanceled(true);
        });
        bus.addListener((net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e) -> {
            LivingIncomingDamageEvent d = post(new LivingIncomingDamageEvent(e.getEntity(), e.getSource(), e.getAmount()));
            e.setAmount(d.getAmount());
            if (d.isCanceled()) e.setCanceled(true);
        });
        bus.addListener((net.neoforged.neoforge.event.entity.living.LivingHealEvent e) -> {
            LivingHealEvent d = post(new LivingHealEvent(e.getEntity(), e.getAmount()));
            e.setAmount(d.getAmount());
            if (d.isCanceled()) e.setCanceled(true);
        });
        bus.addListener((net.neoforged.neoforge.event.entity.living.LivingFallEvent e) -> {
            LivingFallEvent d = post(new LivingFallEvent(e.getEntity(), e.getDistance(), e.getDamageMultiplier()));
            e.setDistance(d.getDistance());
            e.setDamageMultiplier(d.getDamageMultiplier());
            if (d.isCanceled()) e.setCanceled(true);
        });
        bus.addListener((net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent e) -> {
            LivingShieldBlockEvent d = post(new LivingShieldBlockEvent(e.getEntity(), e.getDamageSource(), e.getBlockedDamage(), e.getBlocked()));
            e.setBlocked(d.getBlocked());
            e.setBlockedDamage(d.getBlockedDamage());
        });
        bus.addListener((net.neoforged.neoforge.event.entity.living.LivingEvent.LivingVisibilityEvent e) -> {
            if (!heard(LivingEvent.LivingVisibilityEvent.class)) return;
            LivingEvent.LivingVisibilityEvent d = post(new LivingEvent.LivingVisibilityEvent(e.getEntity(), e.getLookingEntity(), 1.0));
            e.modifyVisibility(d.getVisibilityModifier());
        });
        bus.addListener((net.neoforged.neoforge.event.entity.player.CriticalHitEvent e) ->
                post(new CriticalHitEvent(e.getEntity(), e.getTarget(), e.isCriticalHit())));
        bus.addListener((net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent e) ->
                post(new FinalizeSpawnEvent(e.getEntity(), e.getLevel(), e.getSpawnType())));
        bus.addListener((net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent e) -> {
            BabyEntitySpawnEvent d = post(new BabyEntitySpawnEvent(e.getParentA(), e.getParentB(), e.getChild(), e.getCausedByPlayer()));
            e.setChild(d.getChild());
            if (d.isCanceled()) e.setCanceled(true);
        });
        bus.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) -> {
            if (!heard(EntityJoinLevelEvent.class)) return;
            EntityJoinLevelEvent d = post(new EntityJoinLevelEvent(e.getEntity(), e.getLevel()));
            if (d.isCanceled()) e.setCanceled(true);
        });
        bus.addListener((net.neoforged.neoforge.event.entity.EntityTeleportEvent.EnderPearl e) -> {
            EntityTeleportEvent.EnderPearl d = post(new EntityTeleportEvent.EnderPearl(e.getPlayer()));
            if (d.isCanceled()) e.setCanceled(true);
        });
        bus.addListener((net.neoforged.neoforge.event.entity.EntityTeleportEvent.ChorusFruit e) -> {
            EntityTeleportEvent.ChorusFruit d = post(new EntityTeleportEvent.ChorusFruit(e.getEntityLiving()));
            if (d.isCanceled()) e.setCanceled(true);
        });
        bus.addListener((net.neoforged.neoforge.event.level.ExplosionEvent.Detonate e) ->
                post(new ExplosionEvent.Detonate(e.getLevel(), e.getExplosion(), e.getAffectedEntities())));
        bus.addListener((net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) -> {
            PlayerInteractEvent.RightClickBlock d = post(new PlayerInteractEvent.RightClickBlock(e.getEntity(), e.getHand(), e.getPos(), e.getHitVec()));
            if (d.isCanceled()) {
                e.setCanceled(true);
                e.setCancellationResult(d.getCancellationResult());
            }
        });
        bus.addListener((net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem e) -> {
            PlayerInteractEvent.RightClickItem d = post(new PlayerInteractEvent.RightClickItem(e.getEntity(), e.getHand()));
            if (d.isCanceled()) {
                e.setCanceled(true);
                e.setCancellationResult(d.getCancellationResult());
            }
        });
        bus.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e) ->
                post(new RegisterCommandsEvent(e.getDispatcher(), e.getBuildContext(), e.getCommandSelection())));
        bus.addListener((net.neoforged.neoforge.event.TagsUpdatedEvent e) ->
                post(new TagsUpdatedEvent(e.getRegistryAccess(), e.getUpdateCause() == net.neoforged.neoforge.event.TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED)));
        bus.addListener((net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e) ->
                post(new ItemTooltipEvent(e.getItemStack(), e.getEntity(), e.getToolTip(), e.getFlags(), e.getContext())));
    }
}
