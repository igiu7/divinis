package com.divinis.registry;

import com.divinis.Divinis;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import com.divinis.platform.bus.IEventBus;
import com.divinis.platform.registry.DeferredHolder;
import com.divinis.platform.registry.DeferredRegister;

/**
 * The mod's particles. STEAM: a puff of water steam (client.SteamParticle, textures particle/steam_0..7 from
 * tools/build_steam_fx.py) - it swells, drifts up and thins away; the speed it is sent with is its push. The Evil
 * Brother's steam engine breathes it (boss.EvilBrother).
 * SAND: a puff of blown sand (client.SandParticle, textures particle/sand_0..7 from tools/build_sand_fx.py) - it flies
 * with the speed it is sent with, slows, spreads into a haze and settles; the Sphinx's storms are made of it.
 * SILK, ACID, ACID_SPLASH, VENOM: the Arachnid's torn web, drops of acid, their splashes and poison mist
 * (client.ArachnidParticles, textures from tools/build_arachnid_fx.py; boss.Arachnid).
 * GHOST_FLAME, BONE_DUST: the Barrow Chieftain's soul fire and grave dust (client.BarrowParticles, textures from
 * tools/build_barrow_fx.py; boss.BarrowChieftain).
 * FROST, HYDRA_BLOOD, BOG_SPLASH: the Hydra's frost, blood and thrown-up bog (client.HydraParticles, textures from
 * tools/build_hydra_fx.py; boss.Hydra).
 * NECRO_FLAME, CURSE, HOLY_LIGHT: Azramond's green soul fire, the runes of his curse and the golden light the dead shun
 * (client.AzramondParticles, textures from tools/build_azramond_fx.py; boss.Azramond).
 * EMBER, DRAGON_FIRE, ASH: Ignaroth's embers, fire and ash (client.IgnarothParticles, textures from
 * tools/build_ignaroth_fx.py; boss.Ignaroth).
 */
public final class ModParticles {
    private ModParticles() {}

    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Divinis.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STEAM = PARTICLES.register("steam", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SAND = PARTICLES.register("sand", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SILK = PARTICLES.register("silk", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ACID = PARTICLES.register("acid", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ACID_SPLASH = PARTICLES.register("acid_splash", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VENOM = PARTICLES.register("venom", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GHOST_FLAME = PARTICLES.register("ghost_flame", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BONE_DUST = PARTICLES.register("bone_dust", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FROST = PARTICLES.register("frost", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HYDRA_BLOOD = PARTICLES.register("hydra_blood", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BOG_SPLASH = PARTICLES.register("bog_splash", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> NECRO_FLAME = PARTICLES.register("necro_flame", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CURSE = PARTICLES.register("curse", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HOLY_LIGHT = PARTICLES.register("holy_light", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMBER = PARTICLES.register("ember", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DRAGON_FIRE = PARTICLES.register("dragon_fire", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ASH = PARTICLES.register("ash", () -> new SimpleParticleType(false));

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
