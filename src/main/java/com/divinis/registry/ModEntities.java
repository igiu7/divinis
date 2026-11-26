package com.divinis.registry;

import com.divinis.Divinis;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Divinis' creatures: the bosses and what they summon, the Guild Master, the dungeon portal, the fishing pool, the camp. */
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Divinis.MODID);

    /** The Guild Master dwarf NPC (npc.GuildMaster). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.npc.GuildMaster>> GUILD_MASTER = ENTITY_TYPES.register("guild_master",
            () -> EntityType.Builder.<com.divinis.npc.GuildMaster>of(com.divinis.npc.GuildMaster::new, MobCategory.MISC)
                    .sized(0.8F, 1.45F)
                    .eyeHeight(1.15F)
                    .clientTrackingRange(10)
                    .build("guild_master"));

    /** The Minotaur boss (boss.Minotaur), about 4 blocks tall. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.Minotaur>> MINOTAUR = ENTITY_TYPES.register("minotaur",
            () -> EntityType.Builder.<com.divinis.boss.Minotaur>of(com.divinis.boss.Minotaur::new, MobCategory.MONSTER)
                    .sized(2.4F, 3.9F)
                    .eyeHeight(3.3F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .build("minotaur"));

    /** The Golem boss (boss.Golem), about 8 blocks tall. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.Golem>> GOLEM = ENTITY_TYPES.register("golem",
            () -> EntityType.Builder.<com.divinis.boss.Golem>of(com.divinis.boss.Golem::new, MobCategory.MONSTER)
                    .sized(4.5F, 7.8F)
                    .eyeHeight(6.6F)
                    .fireImmune()
                    .clientTrackingRange(14)
                    .build("golem"));

    /** The Wildfire boss (boss.Wildfire): a floating helm in fire, four shields round it. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.Wildfire>> WILDFIRE = ENTITY_TYPES.register("wildfire",
            () -> EntityType.Builder.<com.divinis.boss.Wildfire>of(com.divinis.boss.Wildfire::new, MobCategory.MONSTER)
                    .sized(2.4F, 3.2F)
                    .eyeHeight(2.2F)
                    .fireImmune()
                    .clientTrackingRange(14)
                    .build("wildfire"));

    /** The Serpent of Infinity boss (boss.SerpentOfInfinity): the head; its body segments are its parts. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.SerpentOfInfinity>> SERPENT_OF_INFINITY = ENTITY_TYPES.register("serpent_of_infinity",
            () -> EntityType.Builder.<com.divinis.boss.SerpentOfInfinity>of(com.divinis.boss.SerpentOfInfinity::new, MobCategory.MONSTER)
                    .sized(2.6F, 2.6F)
                    .eyeHeight(1.3F)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("serpent_of_infinity"));

    /** The Serpent of Infinity's flying minion in its second phase (boss.VoidWyrmling). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.VoidWyrmling>> VOID_WYRMLING = ENTITY_TYPES.register("void_wyrmling",
            () -> EntityType.Builder.<com.divinis.boss.VoidWyrmling>of(com.divinis.boss.VoidWyrmling::new, MobCategory.MONSTER)
                    .sized(0.9F, 0.6F)
                    .fireImmune()
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("void_wyrmling"));

    /** Aurelion, Who Was Before Light - the final boss (boss.Aurelion). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.Aurelion>> AURELION = ENTITY_TYPES.register("aurelion",
            () -> EntityType.Builder.<com.divinis.boss.Aurelion>of(com.divinis.boss.Aurelion::new, MobCategory.MONSTER)
                    .sized(5.0F, 5.0F)
                    .eyeHeight(2.5F)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("aurelion"));

    /** The Nameless Creator (boss.NamelessCreator) - its name deliberately in no language file. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.NamelessCreator>> NAMELESS_CREATOR = ENTITY_TYPES.register("nameless_creator",
            () -> EntityType.Builder.<com.divinis.boss.NamelessCreator>of(com.divinis.boss.NamelessCreator::new, MobCategory.MONSTER)
                    .sized(3.5F, 8.5F)
                    .eyeHeight(7.0F)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("nameless_creator"));

    /** Nocthariel, Who Devoured the Dawn (boss.Nocthariel). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.Nocthariel>> NOCTHARIEL = ENTITY_TYPES.register("nocthariel",
            () -> EntityType.Builder.<com.divinis.boss.Nocthariel>of(com.divinis.boss.Nocthariel::new, MobCategory.MONSTER)
                    .sized(3.5F, 7.0F)
                    .eyeHeight(6.5F)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("nocthariel"));

    /** The Evil Brother of the Guild Master (boss.EvilBrother): the dwarf in his steam mech, about six blocks tall. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.EvilBrother>> EVIL_BROTHER = ENTITY_TYPES.register("evil_brother",
            () -> EntityType.Builder.<com.divinis.boss.EvilBrother>of(com.divinis.boss.EvilBrother::new, MobCategory.MONSTER)
                    .sized(3.6F, 6.0F)
                    .eyeHeight(4.6F)
                    .fireImmune()
                    .clientTrackingRange(14)
                    .build("evil_brother"));

    /** The Ronin (boss.Ronin): a samurai with a katana, about two and a half blocks tall. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.Ronin>> RONIN = ENTITY_TYPES.register("ronin",
            () -> EntityType.Builder.<com.divinis.boss.Ronin>of(com.divinis.boss.Ronin::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.5F)
                    .eyeHeight(2.1F)
                    .clientTrackingRange(12)
                    .build("ronin"));

    /** The Sphinx (boss.Sphinx): a winged lion with a pharaoh's head, about four blocks tall. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.Sphinx>> SPHINX = ENTITY_TYPES.register("sphinx",
            () -> EntityType.Builder.<com.divinis.boss.Sphinx>of(com.divinis.boss.Sphinx::new, MobCategory.MONSTER)
                    .sized(3.0F, 3.8F)
                    .eyeHeight(3.3F)
                    .fireImmune()
                    .clientTrackingRange(14)
                    .build("sphinx"));

    /** The Arachnid (boss.Arachnid): a great spider, five and a half blocks across the legs. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.Arachnid>> ARACHNID = ENTITY_TYPES.register("arachnid",
            () -> EntityType.Builder.<com.divinis.boss.Arachnid>of(com.divinis.boss.Arachnid::new, MobCategory.MONSTER)
                    .sized(3.4F, 2.2F)
                    .eyeHeight(1.6F)
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .build("arachnid"));

    /** The Barrow Chieftain (boss.BarrowChieftain): an undead war chief, three blocks tall, later in his chariot. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.BarrowChieftain>> BARROW_CHIEFTAIN = ENTITY_TYPES.register("barrow_chieftain",
            () -> EntityType.Builder.<com.divinis.boss.BarrowChieftain>of(com.divinis.boss.BarrowChieftain::new, MobCategory.MONSTER)
                    .sized(1.2F, 3.0F)
                    .eyeHeight(2.6F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .build("barrow_chieftain"));

    /** The Hydra (boss.Hydra): a great swamp beast with heads on long necks (boss.HydraHead). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.Hydra>> HYDRA = ENTITY_TYPES.register("hydra",
            () -> EntityType.Builder.<com.divinis.boss.Hydra>of(com.divinis.boss.Hydra::new, MobCategory.MONSTER)
                    .sized(3.4F, 2.4F)
                    .eyeHeight(2.0F)
                    .clientTrackingRange(14)
                    .updateInterval(1)
                    .build("hydra"));

    /** Azramond, the Lich King (boss.Azramond): a floating skeleton king, some three blocks tall. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.Azramond>> AZRAMOND = ENTITY_TYPES.register("azramond",
            () -> EntityType.Builder.<com.divinis.boss.Azramond>of(com.divinis.boss.Azramond::new, MobCategory.MONSTER)
                    .sized(1.2F, 3.3F)
                    .eyeHeight(3.0F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .build("azramond"));

    /** Azramond's throne of bones (boss.LichThrone). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.LichThrone>> LICH_THRONE = ENTITY_TYPES.register("lich_throne",
            () -> EntityType.Builder.<com.divinis.boss.LichThrone>of(com.divinis.boss.LichThrone::new, MobCategory.MISC)
                    .sized(1.8F, 3.0F)
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(40)
                    .build("lich_throne"));

    /** A heap of bones Azramond raises (boss.LichBones). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.LichBones>> LICH_BONES = ENTITY_TYPES.register("lich_bones",
            () -> EntityType.Builder.<com.divinis.boss.LichBones>of(com.divinis.boss.LichBones::new, MobCategory.MISC)
                    .sized(1.0F, 0.7F)
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(10)
                    .build("lich_bones"));

    /** A ghost of Azramond's land of the dead (boss.LichGhost). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.LichGhost>> LICH_GHOST = ENTITY_TYPES.register("lich_ghost",
            () -> EntityType.Builder.<com.divinis.boss.LichGhost>of(com.divinis.boss.LichGhost::new, MobCategory.MISC)
                    .sized(0.8F, 1.8F)
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("lich_ghost"));

    /** A spike of bone of Azramond's dance of bones (boss.LichSpike). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.LichSpike>> LICH_SPIKE = ENTITY_TYPES.register("lich_spike",
            () -> EntityType.Builder.<com.divinis.boss.LichSpike>of(com.divinis.boss.LichSpike::new, MobCategory.MISC)
                    .sized(0.6F, 2.6F)
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("lich_spike"));

    /** A shade of a fallen one (boss.LichShade): a dark copy of a player. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.LichShade>> LICH_SHADE = ENTITY_TYPES.register("lich_shade",
            () -> EntityType.Builder.<com.divinis.boss.LichShade>of(com.divinis.boss.LichShade::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("lich_shade"));

    /** Ignaroth (boss.Ignaroth): a great red dragon, some twelve blocks long. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.Ignaroth>> IGNAROTH = ENTITY_TYPES.register("ignaroth",
            () -> EntityType.Builder.<com.divinis.boss.Ignaroth>of(com.divinis.boss.Ignaroth::new, MobCategory.MONSTER)
                    .sized(5.0F, 4.0F)
                    .eyeHeight(3.4F)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("ignaroth"));

    /** Ignaroth's hoard (boss.DragonHoard). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.DragonHoard>> DRAGON_HOARD = ENTITY_TYPES.register("dragon_hoard",
            () -> EntityType.Builder.<com.divinis.boss.DragonHoard>of(com.divinis.boss.DragonHoard::new, MobCategory.MISC)
                    .sized(4.0F, 1.4F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(12)
                    .updateInterval(40)
                    .build("dragon_hoard"));

    /** A ballista on the rim of Ignaroth's crater (boss.Ballista). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.Ballista>> BALLISTA = ENTITY_TYPES.register("ballista",
            () -> EntityType.Builder.<com.divinis.boss.Ballista>of(com.divinis.boss.Ballista::new, MobCategory.MISC)
                    .sized(1.6F, 1.6F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(12)
                    .updateInterval(5)
                    .build("ballista"));

    /** A ballista bolt (boss.BallistaBolt). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.BallistaBolt>> BALLISTA_BOLT = ENTITY_TYPES.register("ballista_bolt",
            () -> EntityType.Builder.<com.divinis.boss.BallistaBolt>of(com.divinis.boss.BallistaBolt::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .build("ballista_bolt"));

    /** The Barrow Chieftain's spear in flight (boss.BarrowSpear). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.BarrowSpear>> BARROW_SPEAR = ENTITY_TYPES.register("barrow_spear",
            () -> EntityType.Builder.<com.divinis.boss.BarrowSpear>of(com.divinis.boss.BarrowSpear::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("barrow_spear"));

    /** An egg sac the Arachnid flings (boss.ArachnidEgg). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.ArachnidEgg>> ARACHNID_EGG = ENTITY_TYPES.register("arachnid_egg",
            () -> EntityType.Builder.<com.divinis.boss.ArachnidEgg>of(com.divinis.boss.ArachnidEgg::new, MobCategory.MISC)
                    .sized(0.9F, 0.9F)
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("arachnid_egg"));

    /** The silk a player is wrapped in by the Arachnid (boss.ArachnidCocoon). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.ArachnidCocoon>> ARACHNID_COCOON = ENTITY_TYPES.register("arachnid_cocoon",
            () -> EntityType.Builder.<com.divinis.boss.ArachnidCocoon>of(com.divinis.boss.ArachnidCocoon::new, MobCategory.MISC)
                    .sized(1.1F, 2.2F)
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(10)
                    .build("arachnid_cocoon"));

    /** The skin the Arachnid sheds (boss.ArachnidMolt). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.ArachnidMolt>> ARACHNID_MOLT = ENTITY_TYPES.register("arachnid_molt",
            () -> EntityType.Builder.<com.divinis.boss.ArachnidMolt>of(com.divinis.boss.ArachnidMolt::new, MobCategory.MISC)
                    .sized(3.0F, 1.0F)
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("arachnid_molt"));

    /** A tornado of sand the Sphinx raises (boss.SandTornado). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.SandTornado>> SAND_TORNADO = ENTITY_TYPES.register("sand_tornado",
            () -> EntityType.Builder.<com.divinis.boss.SandTornado>of(com.divinis.boss.SandTornado::new, MobCategory.MISC)
                    .sized(1.5F, 6.0F)
                    .fireImmune()
                    .noSave()
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .build("sand_tornado"));

    /** The Wildfire's fireball (boss.WildfireFireball). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.boss.WildfireFireball>> WILDFIRE_FIREBALL = ENTITY_TYPES.register("wildfire_fireball",
            () -> EntityType.Builder.<com.divinis.boss.WildfireFireball>of(com.divinis.boss.WildfireFireball::new, MobCategory.MISC)
                    .sized(0.9F, 0.9F)
                    .fireImmune()
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("wildfire_fireball"));

    /** The round portal into and out of the dungeons (dungeon.DungeonPortal). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.dungeon.DungeonPortal>> DUNGEON_PORTAL = ENTITY_TYPES.register("dungeon_portal",
            () -> EntityType.Builder.<com.divinis.dungeon.DungeonPortal>of(com.divinis.dungeon.DungeonPortal::new, MobCategory.MISC)
                    .sized(3.0F, 3.0F)
                    .fireImmune()
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("dungeon_portal"));

    /** A fishing pool on the water (fishing.FishingPool). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.fishing.FishingPool>> FISHING_POOL = ENTITY_TYPES.register("fishing_pool",
            () -> EntityType.Builder.<com.divinis.fishing.FishingPool>of(com.divinis.fishing.FishingPool::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .fireImmune()
                    .noSave()
                    .clientTrackingRange(6)
                    .updateInterval(40)
                    .build("fishing_pool"));

    /** The Wanderer's camp (skill.SkillCamp). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.divinis.skill.SkillCamp>> SKILL_CAMP = ENTITY_TYPES.register("skill_camp",
            () -> EntityType.Builder.<com.divinis.skill.SkillCamp>of(com.divinis.skill.SkillCamp::new, MobCategory.MISC)
                    .sized(2.0F, 1.0F)
                    .fireImmune()
                    .noSave()
                    .clientTrackingRange(8)
                    .updateInterval(40)
                    .build("skill_camp"));

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(GUILD_MASTER.get(), com.divinis.npc.GuildMaster.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(MINOTAUR.get(), com.divinis.boss.Minotaur.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(GOLEM.get(), com.divinis.boss.Golem.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(WILDFIRE.get(), com.divinis.boss.Wildfire.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(SERPENT_OF_INFINITY.get(), com.divinis.boss.SerpentOfInfinity.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(VOID_WYRMLING.get(), com.divinis.boss.VoidWyrmling.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(AURELION.get(), com.divinis.boss.Aurelion.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(NOCTHARIEL.get(), com.divinis.boss.Nocthariel.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(NAMELESS_CREATOR.get(), com.divinis.boss.NamelessCreator.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(EVIL_BROTHER.get(), com.divinis.boss.EvilBrother.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(RONIN.get(), com.divinis.boss.Ronin.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(SPHINX.get(), com.divinis.boss.Sphinx.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(ARACHNID.get(), com.divinis.boss.Arachnid.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(BARROW_CHIEFTAIN.get(), com.divinis.boss.BarrowChieftain.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(HYDRA.get(), com.divinis.boss.Hydra.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(AZRAMOND.get(), com.divinis.boss.Azramond.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(IGNAROTH.get(), com.divinis.boss.Ignaroth.createAttributes().build()));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(LICH_SHADE.get(), com.divinis.boss.LichShade.createAttributes().build()));
    }
}
