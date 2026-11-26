package com.divinis.platform.event.client;

import com.divinis.platform.bus.Event;
import java.util.function.Supplier;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public abstract class EntityRenderersEvent extends Event {
    /** The time to give entity and block entity types their renderers. */
    public abstract static class RegisterRenderers extends EntityRenderersEvent {
        public abstract <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider);

        public abstract <T extends BlockEntity> void registerBlockEntityRenderer(BlockEntityType<? extends T> type,
                                                                                BlockEntityRendererProvider<T> provider);
    }

    /** The time to add model layers (what models bake from). */
    public abstract static class RegisterLayerDefinitions extends EntityRenderersEvent {
        public abstract void registerLayerDefinition(ModelLayerLocation layer, Supplier<LayerDefinition> definition);
    }

    /** A player renderer was made: layers can be added to it (once per kind of player model). */
    public static class AddPlayerLayers extends EntityRenderersEvent {
        private final PlayerRenderer renderer;
        private final EntityModelSet models;

        public AddPlayerLayers(PlayerRenderer renderer, EntityModelSet models) {
            this.renderer = renderer;
            this.models = models;
        }

        public PlayerRenderer getRenderer() { return renderer; }
        public EntityModelSet getEntityModels() { return models; }
    }
}
