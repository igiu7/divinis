package com.divinis.fishing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.divinis.registry.ItemRarity;
import com.divinis.registry.ModBlocks;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The fishing rods and what each fishes with (FishingGame): fishing speed (percent off the wait), luck (FishingLoot),
 * and the special rods' gifts (Gift: what each does is played in FishingGame, FishingPool, FishingLoot and the
 * clients' wheel and foam). The vanilla rod ("Stick with String") has none of either. Each rod has a rarity
 * (registry.ItemRarity) its speed, luck and gift go with. None of these rods can be crafted or found yet.
 * Textures: tools/build_fishing_rods.py, on the vanilla rod.
 */
public final class FishingRods {
    private FishingRods() {}

    /** What a rod does besides its speed and luck; the text is its tooltip line. */
    public enum Gift {
        NONE(""),
        MORE_POOLS("Pools come up twice as often after a catch"),
        POOLS_LAST("A pool ages half as fast while your bobber is in it"),
        DOUBLE_FISH("A fish comes up two at a time, one time in four"),
        STORM("Fishing speed +90% in the rain, +130% in a thunderstorm"),
        PATIENT("The minigame's zone is half again as wide, its needle a quarter slower"),
        SEEKER("Your bobber drifts into the nearest pool (10 blocks)"),
        CURSED("The minigame's zone is 40% narrower, its needle 40% faster"),
        DAWN("Fishing speed +80% at dawn and at dusk"),
        LANTERN("Luck +4 at night, -1 by day"),
        ICE_HOLE("In cold lands: fishing speed +60%, luck +5, the water round the bobber never freezes"),
        OASIS("In hot lands: pools come up twice as often, a tier better"),
        RHYTHM("Each catch in a row: +1 luck (up to +5); a miss starts again"),
        TWIN_MOONS("The minigame has two zones, each narrower"),
        QUICK_HAND("The minigame's needle turns twice as fast"),
        SECOND_CHANCE("A miss in the minigame gets one more try"),
        GOLDEN_MEAN("Hit the very middle of the zone: an ingot for sure"),
        CHARMER("Pools that come up are a tier better"),
        DEVOURER("Pools give half again as much, but each catch in one takes 20 s of its time"),
        COLLECTOR("A pool's bonus stays on the rod for 60 s after you leave it"),
        WHIRLPOOL("Half the pools that come up are whirlpools: a bobber in one gets a bite at once"),
        SMITH("Ingots twice as often, fish less often"),
        TANNER("Leather 5 - 8 at a time, sometimes fur"),
        COOK("Fish come up cooked"),
        SCHOLAR("Skill books three times as often - but the rod wears out fast"),
        TREASURE("Now and then a treasure: a few things at once"),
        HOUND("The foam of a fish coming lasts twice as long, in the colour of the nearest pool"),
        HARPOON("Casts almost twice as far"),
        ANCHOR("Your bobber stays where it lands"),
        WANDERER("From a boat: fishing speed +50%, luck +2"),
        BLOODY("A miss in the minigame costs half a heart"),
        GAMBLER("Each cast rolls its speed (-50% to +150%) and luck (-2 to +8)"),
        GLUTTON("Each bite makes you hungrier"),
        PATIENCE("The longer you wait for a bite, the more luck: +1 every 12 s, up to +10"),
        LEVIATHAN("Now and then an Ancient Fish bites: three zones to hit in order"),
        STARLIGHT("On a clear night pools come up starlit: twice their bonus"),
        ROYAL("Your party within 16 blocks fishes with +1 luck");

        public final String text;

        Gift(String text) {
            this.text = text;
        }
    }

    /** speed: percent, luck, its gift, the minigame's zone and needle speed (times). */
    public record Stats(int speed, int luck, Gift gift, float zone, float needle) {
        static final Stats PLAIN = new Stats(0, 0, Gift.NONE, 1, 1);

        Stats(int speed, int luck, Gift gift) {
            this(speed, luck, gift, 1, 1);
        }
    }

    public static final int SEEK_RANGE = 10, ROYAL_RANGE = 16;

    /** A rod of these (vanilla behaviour: cast, reel in, the line drawn from it). */
    public static final class Rod extends FishingRodItem {
        public final Stats stats;

        Rod(Stats stats, Properties properties) {
            super(properties);
            this.stats = stats;
        }
    }

    private static final List<DeferredItem<Rod>> ALL = new ArrayList<>();

    // by rarity: speed and luck (and durability) grow with it; the rarer, the stronger the gift
    public static final DeferredItem<Rod> HAZEL_BRONZE = rod("hazel_bronze_fishing_rod", 96, ItemRarity.COMMON, new Stats(10, 0, Gift.NONE));
    public static final DeferredItem<Rod> ASH_IRON = rod("ash_iron_fishing_rod", 128, ItemRarity.COMMON, new Stats(15, 1, Gift.NONE));

    public static final DeferredItem<Rod> STEEL = rod("steel_fishing_rod", 180, ItemRarity.UNCOMMON, new Stats(25, 1, Gift.NONE));
    public static final DeferredItem<Rod> OLD_FISHERMAN = rod("old_fisherman_rod", 180, ItemRarity.UNCOMMON, new Stats(20, 1, Gift.MORE_POOLS));
    public static final DeferredItem<Rod> PATIENT_MONK = rod("patient_monk_rod", 180, ItemRarity.UNCOMMON, new Stats(10, 2, Gift.PATIENT, 1.5F, 0.75F));
    public static final DeferredItem<Rod> QUICK_HAND = rod("quick_hand_rod", 180, ItemRarity.UNCOMMON, new Stats(5, 3, Gift.QUICK_HAND, 1, 2));
    public static final DeferredItem<Rod> TANNER = rod("tanner_rod", 180, ItemRarity.UNCOMMON, new Stats(15, 1, Gift.TANNER));
    public static final DeferredItem<Rod> COOK = rod("cook_rod", 180, ItemRarity.UNCOMMON, new Stats(15, 1, Gift.COOK));
    public static final DeferredItem<Rod> HOUND = rod("hound_rod", 180, ItemRarity.UNCOMMON, new Stats(20, 1, Gift.HOUND));
    public static final DeferredItem<Rod> HARPOONER = rod("harpooner_rod", 180, ItemRarity.UNCOMMON, new Stats(20, 1, Gift.HARPOON));
    public static final DeferredItem<Rod> ANCHOR = rod("anchor_rod", 180, ItemRarity.UNCOMMON, new Stats(20, 1, Gift.ANCHOR));
    public static final DeferredItem<Rod> WANDERER = rod("wanderer_rod", 180, ItemRarity.UNCOMMON, new Stats(15, 1, Gift.WANDERER));
    public static final DeferredItem<Rod> DAWN = rod("dawn_rod", 180, ItemRarity.UNCOMMON, new Stats(15, 1, Gift.DAWN));

    public static final DeferredItem<Rod> SIREN = rod("siren_rod", 220, ItemRarity.RARE, new Stats(30, 2, Gift.POOLS_LAST));
    public static final DeferredItem<Rod> STORMCALLER = rod("stormcaller_rod", 220, ItemRarity.RARE, new Stats(20, 1, Gift.STORM));
    public static final DeferredItem<Rod> POOL_SEEKER = rod("pool_seeker_rod", 220, ItemRarity.RARE, new Stats(30, 2, Gift.SEEKER));
    public static final DeferredItem<Rod> DROWNED_LANTERN = rod("drowned_lantern_rod", 220, ItemRarity.RARE, new Stats(15, 0, Gift.LANTERN));
    public static final DeferredItem<Rod> ICE_HOLE = rod("ice_hole_rod", 220, ItemRarity.RARE, new Stats(0, 0, Gift.ICE_HOLE));
    public static final DeferredItem<Rod> OASIS = rod("oasis_rod", 220, ItemRarity.RARE, new Stats(25, 1, Gift.OASIS));
    public static final DeferredItem<Rod> RHYTHM = rod("rhythm_rod", 220, ItemRarity.RARE, new Stats(20, 0, Gift.RHYTHM));
    public static final DeferredItem<Rod> TWIN_MOONS = rod("twin_moons_rod", 220, ItemRarity.RARE, new Stats(15, 2, Gift.TWIN_MOONS, 0.7F, 1));
    public static final DeferredItem<Rod> SECOND_CHANCE = rod("second_chance_rod", 220, ItemRarity.RARE, new Stats(20, 2, Gift.SECOND_CHANCE));
    public static final DeferredItem<Rod> GOLDEN_MEAN = rod("golden_mean_rod", 220, ItemRarity.RARE, new Stats(20, 1, Gift.GOLDEN_MEAN));
    public static final DeferredItem<Rod> DEVOURER = rod("devourer_rod", 220, ItemRarity.RARE, new Stats(15, 0, Gift.DEVOURER));
    public static final DeferredItem<Rod> POOL_COLLECTOR = rod("pool_collector_rod", 220, ItemRarity.RARE, new Stats(20, 1, Gift.COLLECTOR));
    public static final DeferredItem<Rod> WHIRLPOOL = rod("whirlpool_rod", 220, ItemRarity.RARE, new Stats(25, 1, Gift.WHIRLPOOL));
    public static final DeferredItem<Rod> RIVER_SMITH = rod("river_smith_rod", 220, ItemRarity.RARE, new Stats(20, 1, Gift.SMITH));
    public static final DeferredItem<Rod> TREASURER = rod("treasurer_rod", 220, ItemRarity.RARE, new Stats(20, 2, Gift.TREASURE));
    public static final DeferredItem<Rod> GAMBLER = rod("gambler_rod", 220, ItemRarity.RARE, new Stats(0, 0, Gift.GAMBLER));
    public static final DeferredItem<Rod> GLUTTON = rod("glutton_rod", 220, ItemRarity.RARE, new Stats(90, 0, Gift.GLUTTON));
    public static final DeferredItem<Rod> GILDED_SHORE = rod("gilded_shore_rod", 220, ItemRarity.RARE, new Stats(0, 5, Gift.NONE));

    public static final DeferredItem<Rod> STAFF_OF_THE_DEEP = rod("staff_of_the_deep", 280, ItemRarity.EPIC, new Stats(45, 3, Gift.DOUBLE_FISH));
    public static final DeferredItem<Rod> WATER_CHARMER = rod("water_charmer_rod", 280, ItemRarity.EPIC, new Stats(30, 2, Gift.CHARMER));
    public static final DeferredItem<Rod> SCHOLAR = rod("scholar_rod", 60, ItemRarity.EPIC, new Stats(0, 3, Gift.SCHOLAR));
    public static final DeferredItem<Rod> BLOODY_HOOK = rod("bloody_hook_rod", 280, ItemRarity.EPIC, new Stats(0, 7, Gift.BLOODY));
    public static final DeferredItem<Rod> CURSED_CHAIN = rod("cursed_chain_rod", 280, ItemRarity.EPIC, new Stats(0, 8, Gift.CURSED, 0.6F, 1.4F));
    public static final DeferredItem<Rod> ETERNAL_PATIENCE = rod("eternal_patience_rod", 280, ItemRarity.EPIC, new Stats(0, 0, Gift.PATIENCE));

    public static final DeferredItem<Rod> STARLIGHT = rod("starlight_rod", 350, ItemRarity.LEGENDARY, new Stats(40, 3, Gift.STARLIGHT));
    public static final DeferredItem<Rod> ROYAL_ANGLER = rod("royal_angler_rod", 350, ItemRarity.LEGENDARY, new Stats(40, 3, Gift.ROYAL));

    public static final DeferredItem<Rod> LEVIATHAN = rod("leviathan_rod", 500, ItemRarity.UNIQUE, new Stats(60, 5, Gift.LEVIATHAN));

    /** The Leviathan rod's Ancient Fish: a trophy (it comes with a rare ingot). */
    public static final DeferredItem<Item> ANCIENT_FISH = ModBlocks.ITEMS.register("ancient_fish",
            () -> new Item(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
                    lines.add(Component.literal("A trophy from the deep").withStyle(net.minecraft.ChatFormatting.GRAY));
                }

                @Override
                public boolean isFoil(ItemStack stack) {
                    return true;
                }
            });

    private static DeferredItem<Rod> rod(String name, int durability, ItemRarity rarity, Stats stats) {
        DeferredItem<Rod> item = ModBlocks.ITEMS.register(name, () -> new Rod(stats, new Item.Properties().durability(durability)));
        ItemRarity.assign(item, rarity);
        ALL.add(item);
        return item;
    }

    static {
        ItemRarity.assign(() -> net.minecraft.world.item.Items.FISHING_ROD, ItemRarity.COMMON); // the Stick with String
    }

    public static List<DeferredItem<Rod>> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** Loads the class, so the rods are in the item register before it is registered. */
    public static void init() {}

    // ------------------------------------------------------------------ what a rod fishes with

    public static Stats stats(ItemStack stack) {
        return stack.getItem() instanceof Rod rod ? rod.stats : Stats.PLAIN;
    }

    /** The rod the player fishes with: the one in the main hand, else the off hand's. */
    public static ItemStack held(Player player) {
        return player.getMainHandItem().getItem() instanceof FishingRodItem ? player.getMainHandItem() : player.getOffhandItem();
    }

    public static Stats stats(FishingHook hook) {
        Player owner = hook.getPlayerOwner();
        return owner == null ? Stats.PLAIN : stats(held(owner));
    }

    public static Gift gift(FishingHook hook) {
        return stats(hook).gift();
    }

    private static long dayTime(Level level) {
        return level.getDayTime() % 24000;
    }

    public static boolean night(Level level) {
        long t = dayTime(level);
        return t >= 13000 && t < 23000;
    }

    static boolean dawnOrDusk(Level level) {
        long t = dayTime(level);
        return t >= 22500 || t < 1000 || (t >= 11500 && t < 13500);
    }

    static boolean cold(Level level, BlockPos pos) {
        return level.getBiome(pos).value().coldEnoughToSnow(pos);
    }

    static boolean hot(Level level, BlockPos pos) {
        return level.getBiome(pos).value().getBaseTemperature() >= 0.95F;
    }

    /** The rod's fishing speed for this bobber now (some depend on the weather, the time, the land, a boat...). */
    public static int speed(FishingHook hook) {
        Stats stats = stats(hook);
        Level level = hook.level();
        BlockPos pos = hook.blockPosition();
        return switch (stats.gift()) {
            case STORM -> level.isRainingAt(pos.above()) ? (level.isThundering() ? 130 : 90) : stats.speed();
            case DAWN -> dawnOrDusk(level) ? 80 : stats.speed();
            case ICE_HOLE -> cold(level, pos) ? 60 : stats.speed();
            case WANDERER -> hook.getPlayerOwner() != null && hook.getPlayerOwner().getVehicle() instanceof Boat ? stats.speed() + 50 : stats.speed();
            case GAMBLER -> FishingGame.gamble(hook)[0];
            default -> stats.speed();
        };
    }

    /** The rod's luck for this bobber now (with the royal rod's of a party member nearby). */
    public static int luck(FishingHook hook) {
        Stats stats = stats(hook);
        Level level = hook.level();
        Player owner = hook.getPlayerOwner();
        int luck = switch (stats.gift()) {
            case LANTERN -> night(level) ? 4 : -1;
            case ICE_HOLE -> cold(level, hook.blockPosition()) ? 5 : stats.luck();
            case RHYTHM -> stats.luck() + (owner == null ? 0 : FishingGame.streak(owner));
            case WANDERER -> owner != null && owner.getVehicle() instanceof Boat ? stats.luck() + 2 : stats.luck();
            case GAMBLER -> FishingGame.gamble(hook)[1];
            case PATIENCE -> Math.min(10, hook.tickCount / 240);
            default -> stats.luck();
        };
        return luck + (owner != null && royalNearby(owner) ? 1 : 0);
    }

    /** Whether someone of the player's party (or they) fishes with the royal rod within ROYAL_RANGE blocks. */
    private static boolean royalNearby(Player player) {
        Optional<UUID> team = partyOf(player);
        for (Player other : player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(ROYAL_RANGE))) {
            if (other.fishing == null || stats(held(other)).gift() != Gift.ROYAL) continue;
            if (other == player || (team.isPresent() && team.equals(partyOf(other)))) return true;
        }
        return false;
    }

    private static Optional<UUID> partyOf(Player player) {
        try {
            return FTBTeamsAPI.api().getManager().getTeamForPlayerID(player.getUUID())
                    .filter(Team::isPartyTeam).map(Team::getId);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
