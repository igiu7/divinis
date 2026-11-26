package com.divinis.neoforge;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.divinis.Divinis;
import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.client.ClientPlayerNetworkEvent;
import com.divinis.platform.event.client.ClientTickEvent;
import com.divinis.platform.event.client.EntityRenderersEvent;
import com.divinis.platform.event.client.FMLClientSetupEvent;
import com.divinis.platform.event.client.InputEvent;
import com.divinis.platform.event.client.ModelEvent;
import com.divinis.platform.event.client.MovementInputUpdateEvent;
import com.divinis.platform.event.client.RegisterArmorRenderersEvent;
import com.divinis.platform.event.client.RegisterGuiLayersEvent;
import com.divinis.platform.event.client.RegisterKeyMappingsEvent;
import com.divinis.platform.event.client.RegisterParticleProvidersEvent;
import com.divinis.platform.event.client.RegisterShadersEvent;
import com.divinis.platform.event.client.RenderGuiLayerEvent;
import com.divinis.platform.event.client.RenderHandEvent;
import com.divinis.platform.event.client.RenderLevelStageEvent;
import com.divinis.platform.event.client.ScreenEvent;
import com.divinis.platform.event.client.ViewportEvent;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.NeoForge;

/** The client's events on NeoForge, turned into Divinis'. */
final class DivinisNeoForgeClient {
    private DivinisNeoForgeClient() {}

    private static <E> E post(E event) {
        return DivinisBus.post(event);
    }

    private static boolean heard(Class<?> type) {
        return DivinisBus.hasListeners(type);
    }

    static void init(IEventBus modBus) {
        com.divinis.client.ClientSubscribers.init();
        registration(modBus);
        game(NeoForge.EVENT_BUS);
    }

    private static void registration(IEventBus bus) {
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) -> post(new FMLClientSetupEvent() {
            @Override
            public void enqueueWork(Runnable work) {
                e.enqueueWork(work);
            }
        }));
        bus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e) -> post(new RegisterKeyMappingsEvent() {
            @Override
            public void register(KeyMapping key) {
                e.register(key);
            }
        }));
        bus.addListener((net.neoforged.neoforge.client.event.RegisterShadersEvent e) -> post(new RegisterShadersEvent() {
            @Override
            public void register(ResourceLocation id, VertexFormat format, Consumer<ShaderInstance> loaded) {
                try {
                    e.registerShader(new ShaderInstance(e.getResourceProvider(), id, format), loaded);
                } catch (IOException ex) {
                    Divinis.LOGGER.error("Could not load the shader {}", id, ex);
                }
            }
        }));
        bus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent e) -> post(new RegisterParticleProvidersEvent() {
            @Override
            public <T extends ParticleOptions> void registerSpriteSet(ParticleType<T> type, ParticleEngine.SpriteParticleRegistration<T> registration) {
                e.registerSpriteSet(type, registration);
            }
        }));
        bus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) -> post(new EntityRenderersEvent.RegisterRenderers() {
            @Override
            public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                e.registerEntityRenderer(type, provider);
            }

            @Override
            public <T extends BlockEntity> void registerBlockEntityRenderer(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {
                e.registerBlockEntityRenderer(type, provider);
            }
        }));
        bus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions e) ->
                post(new EntityRenderersEvent.RegisterLayerDefinitions() {
                    @Override
                    public void registerLayerDefinition(ModelLayerLocation layer, Supplier<LayerDefinition> definition) {
                        e.registerLayerDefinition(layer, definition);
                    }
                }));
        bus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers e) -> {
            for (var skin : e.getSkins()) {
                if (e.getSkin(skin) instanceof PlayerRenderer renderer) post(new EntityRenderersEvent.AddPlayerLayers(renderer, e.getEntityModels()));
            }
        });
        bus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent e) -> {
            RegisterGuiLayersEvent.LAYERS.clear();
            post(new RegisterGuiLayersEvent());
            for (RegisterGuiLayersEvent.Entry l : RegisterGuiLayersEvent.LAYERS) {
                switch (l.where()) {
                    case ABOVE -> e.registerAbove(l.other(), l.id(), l.layer());
                    case ABOVE_ALL -> e.registerAboveAll(l.id(), l.layer());
                    case BELOW_ALL -> e.registerBelowAll(l.id(), l.layer());
                }
            }
        });
        bus.addListener((net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional e) -> post(new ModelEvent.RegisterAdditional() {
            @Override
            public void register(ResourceLocation model) {
                e.register(ModelResourceLocation.standalone(model));
            }
        }));
        bus.addListener((net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent e) -> post(new RegisterArmorRenderersEvent() {
            @Override
            public void register(ArmorModel model, Function<ItemStack, ResourceLocation> texture,
                                 Supplier<BlockEntityWithoutLevelRenderer> itemRenderer, Item... items) {
                e.registerItem(new IClientItemExtensions() {
                    @Override
                    public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                        return model.model(entity, stack, slot, original);
                    }

                    @Override
                    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                        return itemRenderer.get();
                    }
                }, items);
            }
        }));
    }

    private static RenderLevelStageEvent.Stage stage(net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage s) {
        if (s == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY) return RenderLevelStageEvent.Stage.AFTER_SKY;
        if (s == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) return RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        if (s == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
        if (s == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES) return RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        if (s == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_WEATHER) return RenderLevelStageEvent.Stage.AFTER_WEATHER;
        if (s == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL) return RenderLevelStageEvent.Stage.AFTER_LEVEL;
        return null;
    }

    private static void game(IEventBus bus) {
        bus.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Pre e) -> {
            if (heard(ClientTickEvent.Pre.class)) post(new ClientTickEvent.Pre());
        });
        bus.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e) -> {
            if (heard(ClientTickEvent.Post.class)) post(new ClientTickEvent.Post());
        });
        bus.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn e) ->
                post(new ClientPlayerNetworkEvent.LoggingIn(e.getPlayer())));
        bus.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) ->
                post(new ClientPlayerNetworkEvent.LoggingOut(e.getPlayer())));
        bus.addListener((net.neoforged.neoforge.client.event.RenderLevelStageEvent e) -> {
            RenderLevelStageEvent.Stage s = stage(e.getStage());
            if (s == null || !heard(RenderLevelStageEvent.class)) return;
            post(new RenderLevelStageEvent(s, e.getLevelRenderer(), e.getPoseStack(), e.getModelViewMatrix(), e.getProjectionMatrix(),
                    e.getRenderTick(), e.getPartialTick(), e.getCamera(), e.getFrustum()));
        });
        bus.addListener((net.neoforged.neoforge.client.event.RenderHandEvent e) -> {
            if (!heard(RenderHandEvent.class)) return;
            RenderHandEvent d = post(new RenderHandEvent(e.getHand(), e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight(),
                    e.getPartialTick(), e.getItemStack()));
            if (d.isCanceled()) e.setCanceled(true);
        });
        bus.addListener((net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles e) -> {
            if (!heard(ViewportEvent.ComputeCameraAngles.class)) return;
            ViewportEvent.ComputeCameraAngles d = post(new ViewportEvent.ComputeCameraAngles(e.getCamera(), e.getPartialTick(),
                    e.getYaw(), e.getPitch(), e.getRoll()));
            e.setYaw(d.getYaw());
            e.setPitch(d.getPitch());
            e.setRoll(d.getRoll());
        });
        bus.addListener((net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov e) -> {
            if (!heard(ViewportEvent.ComputeFov.class)) return;
            e.setFOV(post(new ViewportEvent.ComputeFov(e.getCamera(), e.getPartialTick(), e.getFOV(), e.usedConfiguredFov())).getFOV());
        });
        bus.addListener((net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor e) -> {
            if (!heard(ViewportEvent.ComputeFogColor.class)) return;
            ViewportEvent.ComputeFogColor d = post(new ViewportEvent.ComputeFogColor(e.getCamera(), (float) e.getPartialTick(),
                    e.getRed(), e.getGreen(), e.getBlue()));
            e.setRed(d.getRed());
            e.setGreen(d.getGreen());
            e.setBlue(d.getBlue());
        });
        bus.addListener((net.neoforged.neoforge.client.event.ViewportEvent.RenderFog e) -> {
            if (!heard(ViewportEvent.RenderFog.class)) return;
            ViewportEvent.RenderFog d = post(new ViewportEvent.RenderFog(e.getMode(), e.getCamera(), (float) e.getPartialTick(),
                    e.getNearPlaneDistance(), e.getFarPlaneDistance(), e.getFogShape()));
            if (d.isCanceled()) {
                e.setNearPlaneDistance(d.getNearPlaneDistance());
                e.setFarPlaneDistance(d.getFarPlaneDistance());
                e.setFogShape(d.getFogShape());
                e.setCanceled(true);
            }
        });
        bus.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Render.Post e) -> {
            if (heard(ScreenEvent.Render.Post.class)) {
                post(new ScreenEvent.Render.Post(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY(), e.getPartialTick()));
            }
        });
        bus.addListener((net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre e) -> {
            ScreenEvent.MouseButtonPressed.Pre d = post(new ScreenEvent.MouseButtonPressed.Pre(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getButton()));
            if (d.isCanceled()) e.setCanceled(true);
        });
        bus.addListener((net.neoforged.neoforge.client.event.InputEvent.Key e) ->
                post(new InputEvent.Key(e.getKey(), e.getScanCode(), e.getAction(), e.getModifiers())));
        bus.addListener((net.neoforged.neoforge.client.event.MovementInputUpdateEvent e) ->
                post(new MovementInputUpdateEvent(e.getEntity(), e.getInput())));
        bus.addListener((net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre e) -> {
            if (!heard(RenderGuiLayerEvent.Pre.class)) return;
            RenderGuiLayerEvent.Pre d = post(new RenderGuiLayerEvent.Pre(e.getName(), e.getGuiGraphics(), e.getPartialTick()));
            if (d.isCanceled()) e.setCanceled(true);
        });
    }
}
