package com.divinis.fabric;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.divinis.Divinis;
import com.divinis.client.ClientSubscribers;
import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.ItemTooltipEvent;
import com.divinis.platform.event.client.ClientPlayerNetworkEvent;
import com.divinis.platform.event.client.ClientTickEvent;
import com.divinis.platform.event.client.EntityRenderersEvent;
import com.divinis.platform.event.client.FMLClientSetupEvent;
import com.divinis.platform.event.client.ModelEvent;
import com.divinis.platform.event.client.RegisterArmorRenderersEvent;
import com.divinis.platform.event.client.RegisterGuiLayersEvent;
import com.divinis.platform.event.client.RegisterKeyMappingsEvent;
import com.divinis.platform.event.client.RegisterParticleProvidersEvent;
import com.divinis.platform.event.client.RegisterShadersEvent;
import com.divinis.platform.event.client.RenderLevelStageEvent;
import com.divinis.platform.event.client.ScreenEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** The client on Fabric: Divinis' client listeners, its registrations, and Fabric's client events turned into Divinis'. */
public final class DivinisFabricClient implements ClientModInitializer {
    private static <E> E post(E event) {
        return DivinisBus.post(event);
    }

    private static boolean heard(Class<?> type) {
        return DivinisBus.hasListeners(type);
    }

    @Override
    public void onInitializeClient() {
        ClientSubscribers.init();
        registration();
        events();
        post(new FMLClientSetupEvent() {
            @Override
            public void enqueueWork(Runnable work) {
                work.run();
            }
        });
    }

    private static void registration() {
        post(new RegisterKeyMappingsEvent() {
            @Override
            public void register(KeyMapping key) {
                KeyBindingHelper.registerKeyBinding(key);
            }
        });
        CoreShaderRegistrationCallback.EVENT.register(context -> post(new RegisterShadersEvent() {
            @Override
            public void register(ResourceLocation id, VertexFormat format, Consumer<ShaderInstance> loaded) {
                try {
                    context.register(id, format, loaded::accept);
                } catch (java.io.IOException e) {
                    Divinis.LOGGER.error("Could not load the shader {}", id, e);
                }
            }
        }));
        post(new RegisterParticleProvidersEvent() {
            @Override
            public <T extends ParticleOptions> void registerSpriteSet(ParticleType<T> type, ParticleEngine.SpriteParticleRegistration<T> registration) {
                ParticleFactoryRegistry.getInstance().register(type, registration::create);
            }
        });
        post(new EntityRenderersEvent.RegisterLayerDefinitions() {
            @Override
            public void registerLayerDefinition(ModelLayerLocation layer, Supplier<LayerDefinition> definition) {
                EntityModelLayerRegistry.registerModelLayer(layer, definition::get);
            }
        });
        post(new EntityRenderersEvent.RegisterRenderers() {
            @Override
            public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                EntityRendererRegistry.register(type, provider);
            }

            @Override
            public <T extends BlockEntity> void registerBlockEntityRenderer(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {
                BlockEntityRendererRegistry.register(type, provider);
            }
        });
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerRenderer player) post(new EntityRenderersEvent.AddPlayerLayers(player, context.getModelSet()));
        });
        RegisterGuiLayersEvent.LAYERS.clear();
        post(new RegisterGuiLayersEvent());
        ModelLoadingPlugin.register(context -> post(new ModelEvent.RegisterAdditional() {
            @Override
            public void register(ResourceLocation model) {
                context.addModels(model);
            }
        }));
        post(new RegisterArmorRenderersEvent() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public void register(ArmorModel model, Function<ItemStack, ResourceLocation> texture,
                                 Supplier<BlockEntityWithoutLevelRenderer> itemRenderer, Item... items) {
                ArmorRenderer.register((pose, buffers, stack, entity, slot, light, context) -> {
                    HumanoidModel worn = model.model(entity, stack, slot, context);
                    context.copyPropertiesTo(worn);
                    ArmorRenderer.renderPart(pose, buffers, light, stack, worn, texture.apply(stack));
                }, items);
                for (Item item : items) {
                    BuiltinItemRendererRegistry.INSTANCE.register(item, (stack, mode, pose, buffers, light, overlay) ->
                            itemRenderer.get().renderByItem(stack, mode, pose, buffers, light, overlay));
                }
            }
        });
    }

    private static void stage(RenderLevelStageEvent.Stage stage, WorldRenderContext c) {
        if (!heard(RenderLevelStageEvent.class)) return;
        Minecraft mc = Minecraft.getInstance();
        int tick = mc.level == null ? 0 : (int) mc.level.getGameTime();
        post(new RenderLevelStageEvent(stage, c.worldRenderer(), new PoseStack(), c.positionMatrix(), c.projectionMatrix(), tick,
                c.tickCounter(), c.camera(), c.frustum()));
    }

    private static void events() {
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (heard(ClientTickEvent.Pre.class)) post(new ClientTickEvent.Pre());
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (heard(ClientTickEvent.Post.class)) post(new ClientTickEvent.Post());
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> post(new ClientPlayerNetworkEvent.LoggingIn(mc.player)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> post(new ClientPlayerNetworkEvent.LoggingOut(mc.player)));
        WorldRenderEvents.AFTER_ENTITIES.register(c -> stage(RenderLevelStageEvent.Stage.AFTER_ENTITIES, c));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(c -> {
            stage(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS, c);
            stage(RenderLevelStageEvent.Stage.AFTER_PARTICLES, c);
        });
        WorldRenderEvents.LAST.register(c -> stage(RenderLevelStageEvent.Stage.AFTER_WEATHER, c));
        WorldRenderEvents.END.register(c -> stage(RenderLevelStageEvent.Stage.AFTER_LEVEL, c));
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
                if (heard(ScreenEvent.Render.Post.class)) post(new ScreenEvent.Render.Post(s, g, mx, my, delta));
            });
            ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, button) ->
                    !post(new ScreenEvent.MouseButtonPressed.Pre(s, mx, my, button)).isCanceled());
        });
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) ->
                post(new ItemTooltipEvent(stack, Minecraft.getInstance().player, lines, type, context)));
    }
}
