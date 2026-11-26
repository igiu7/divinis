package com.divinis.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.divinis.Divinis;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class ClientModEvents {
    public static final KeyMapping SKILLS =
            new KeyMapping("key.divinis.skills", InputConstants.UNKNOWN.getValue(), "key.categories.divinis"); // unbound by default
    public static final KeyMapping DASH =
            new KeyMapping("key.divinis.dash_skill", InputConstants.UNKNOWN.getValue(), "key.categories.divinis"); // unbound (a new name, so the old Left Alt is not kept): the skill slots come later

    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(SKILLS);
        event.register(DASH);
    }

    @SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        com.divinis.client.forge.AnvilBehavior.register();
        com.divinis.client.forge.SmeltingForgeBehavior.register();
        com.divinis.client.forge.CauldronBehavior.register();
        event.enqueueWork(() -> com.divinis.fishing.FishingRods.all().forEach(rod -> net.minecraft.client.renderer.item.ItemProperties.register(
                rod.get(), net.minecraft.resources.ResourceLocation.withDefaultNamespace("cast"), FishingRodCast::cast)));
    }

    @SubscribeEvent
    public static void onParticles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.divinis.registry.ModParticles.STEAM.get(), SteamParticle.Provider::new);
        event.registerSpriteSet(com.divinis.registry.ModParticles.SAND.get(), SandParticle.Provider::new);
        event.registerSpriteSet(com.divinis.registry.ModParticles.SILK.get(), ArachnidParticles.SilkProvider::new);
        event.registerSpriteSet(com.divinis.registry.ModParticles.ACID.get(), ArachnidParticles.AcidProvider::new);
        event.registerSpriteSet(com.divinis.registry.ModParticles.ACID_SPLASH.get(), ArachnidParticles.SplashProvider::new);
        event.registerSpriteSet(com.divinis.registry.ModParticles.VENOM.get(), ArachnidParticles.VenomProvider::new);
        event.registerSpriteSet(com.divinis.registry.ModParticles.GHOST_FLAME.get(), BarrowParticles.GhostFlameProvider::new);
        event.registerSpriteSet(com.divinis.registry.ModParticles.BONE_DUST.get(), BarrowParticles.BoneDustProvider::new);
        event.registerSpriteSet(com.divinis.registry.ModParticles.FROST.get(), HydraParticles.FrostProvider::new);
        event.registerSpriteSet(com.divinis.registry.ModParticles.HYDRA_BLOOD.get(), HydraParticles.BloodProvider::new);
        event.registerSpriteSet(com.divinis.registry.ModParticles.BOG_SPLASH.get(), HydraParticles.SplashProvider::new);
        event.registerSpriteSet(com.divinis.registry.ModParticles.NECRO_FLAME.get(), AzramondParticles.FlameProvider::new);
        event.registerSpriteSet(com.divinis.registry.ModParticles.CURSE.get(), AzramondParticles.CurseProvider::new);
        event.registerSpriteSet(com.divinis.registry.ModParticles.HOLY_LIGHT.get(), AzramondParticles.LightProvider::new);
        event.registerSpriteSet(com.divinis.registry.ModParticles.EMBER.get(), IgnarothParticles.EmberProvider::new);
        event.registerSpriteSet(com.divinis.registry.ModParticles.DRAGON_FIRE.get(), IgnarothParticles.FireProvider::new);
        event.registerSpriteSet(com.divinis.registry.ModParticles.ASH.get(), IgnarothParticles.AshProvider::new);
    }

    @SubscribeEvent
    public static void onAdditionalModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        com.divinis.client.forge.SmeltingForgeRenderer.models().forEach(event::register);
        event.register(SkillFx.CAMP);
        event.register(SkillFx.ARROW);
    }

    @SubscribeEvent
    public static void onBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.divinis.forge.SmeltingForge.BLOCK_ENTITY.get(),
                ctx -> new com.divinis.client.forge.SmeltingForgeRenderer());
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GuildMasterModel.LAYER, GuildMasterModelData::createBodyLayer);
        event.registerLayerDefinition(MinotaurModel.LAYER, MinotaurModelData::createBodyLayer);
        event.registerLayerDefinition(GolemModel.LAYER, GolemModelData::createBodyLayer);
        event.registerLayerDefinition(ArachnidModel.LAYER, ArachnidModelData::createBodyLayer);
        event.registerLayerDefinition(BarrowChieftainModel.LAYER, BarrowChieftainModelData::createBodyLayer);
        event.registerLayerDefinition(HydraModel.LAYER, HydraModelData::createBodyLayer);
        event.registerLayerDefinition(AzramondModel.LAYER, AzramondModelData::createBodyLayer);
        event.registerLayerDefinition(AzramondRenderer.PROPS, AzramondPropsModelData::createBodyLayer);
        event.registerLayerDefinition(IgnarothModel.LAYER, IgnarothModelData::createBodyLayer);
        event.registerLayerDefinition(IgnarothRenderer.PROPS, IgnarothPropsModelData::createBodyLayer);
        event.registerLayerDefinition(ArachnidPropsRenderers.PROPS, ArachnidPropsModelData::createBodyLayer);
        event.registerLayerDefinition(EvilBrotherModel.LAYER, EvilBrotherModelData::createBodyLayer);
        event.registerLayerDefinition(RoninModel.LAYER, RoninModelData::createBodyLayer);
        event.registerLayerDefinition(SphinxModel.LAYER, SphinxModelData::createBodyLayer);
        event.registerLayerDefinition(EvilBrotherModel.LAYER_P2, EvilBrotherP2ModelData::createBodyLayer);
        event.registerLayerDefinition(WildfireModel.LAYER, WildfireModelData::createBodyLayer);
        event.registerLayerDefinition(SerpentModels.HEAD, SerpentHeadModelData::createBodyLayer);
        event.registerLayerDefinition(SerpentModels.BODY, SerpentBodyModelData::createBodyLayer);
        event.registerLayerDefinition(SerpentModels.TAIL, SerpentTailModelData::createBodyLayer);
        event.registerLayerDefinition(WyrmlingRenderer.LAYER, WyrmlingModelData::createBodyLayer);
        event.registerLayerDefinition(AurelionRenderer.BODY, AurelionModelData::createBodyLayer);
        event.registerLayerDefinition(AurelionRenderer.HAND, AurelionHandModelData::createBodyLayer);
        event.registerLayerDefinition(AurelionRenderer.BODY_P2, AurelionP2ModelData::createBodyLayer);
        event.registerLayerDefinition(AurelionRenderer.SHARD, AurelionShardModelData::createBodyLayer);
        event.registerLayerDefinition(NoctharielRenderer.BODY, NoctharielModelData::createBodyLayer);
        event.registerLayerDefinition(NoctharielRenderer.BODY_P2, NoctharielP2ModelData::createBodyLayer);
        event.registerLayerDefinition(DungeonPortalModel.LAYER, DungeonPortalModelData::createBodyLayer);
        event.registerLayerDefinition(CreatorRenderer.LAYERS[0], CreatorP1ModelData::createBodyLayer);
        event.registerLayerDefinition(CreatorRenderer.LAYERS[1], CreatorP2ModelData::createBodyLayer);
        event.registerLayerDefinition(CreatorRenderer.LAYERS[2], CreatorP3ModelData::createBodyLayer);
        event.registerLayerDefinition(CreatorRenderer.LAYERS[3], CreatorP4ModelData::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.divinis.registry.ModEntities.GUILD_MASTER.get(), GuildMasterRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.MINOTAUR.get(), MinotaurRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.GOLEM.get(), GolemRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.ARACHNID.get(), ArachnidRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.BARROW_CHIEFTAIN.get(), BarrowChieftainRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.HYDRA.get(), HydraRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.AZRAMOND.get(), AzramondRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.LICH_THRONE.get(), c -> new AzramondRenderer.Props<>(c, 1.6F, 1.2F));
        event.registerEntityRenderer(com.divinis.registry.ModEntities.LICH_BONES.get(), c -> new AzramondRenderer.Props<>(c, 1.2F, 0.5F));
        event.registerEntityRenderer(com.divinis.registry.ModEntities.LICH_GHOST.get(), c -> new AzramondRenderer.Props<>(c, 1.2F, 0F));
        event.registerEntityRenderer(com.divinis.registry.ModEntities.LICH_SPIKE.get(), c -> new AzramondRenderer.Props<>(c, 1.4F, 0.3F));
        event.registerEntityRenderer(com.divinis.registry.ModEntities.LICH_SHADE.get(), LichShadeRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.IGNAROTH.get(), IgnarothRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.DRAGON_HOARD.get(), c -> new IgnarothRenderer.Props<>(c, 2.2F, 2.5F));
        event.registerEntityRenderer(com.divinis.registry.ModEntities.BALLISTA.get(), c -> new IgnarothRenderer.Props<>(c, 1.2F, 0.8F));
        event.registerEntityRenderer(com.divinis.registry.ModEntities.BALLISTA_BOLT.get(), c -> new IgnarothRenderer.Props<>(c, 1.2F, 0F));
        event.registerEntityRenderer(com.divinis.registry.ModEntities.BARROW_SPEAR.get(), BarrowChieftainRenderer.Spear::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.ARACHNID_EGG.get(), ArachnidPropsRenderers.EggRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.ARACHNID_COCOON.get(), ArachnidPropsRenderers.CocoonRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.ARACHNID_MOLT.get(), ArachnidPropsRenderers.MoltRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.EVIL_BROTHER.get(), EvilBrotherRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.RONIN.get(), RoninRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.SPHINX.get(), SphinxRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.SAND_TORNADO.get(), SandTornadoRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.WILDFIRE.get(), WildfireRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.WILDFIRE_FIREBALL.get(), WildfireFireballRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.SERPENT_OF_INFINITY.get(), SerpentRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.VOID_WYRMLING.get(), WyrmlingRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.AURELION.get(), AurelionRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.NOCTHARIEL.get(), NoctharielRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.NAMELESS_CREATOR.get(), CreatorRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.DUNGEON_PORTAL.get(), DungeonPortalRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.FISHING_POOL.get(), FishingPoolRenderer::new);
        event.registerEntityRenderer(com.divinis.registry.ModEntities.SKILL_CAMP.get(), SkillCampRenderer::new);
        event.registerBlockEntityRenderer(com.divinis.dungeon.DungeonRegistry.DUNGEON_CHEST_ENTITY.get(),
                net.minecraft.client.renderer.blockentity.ChestRenderer::new);
    }
}
