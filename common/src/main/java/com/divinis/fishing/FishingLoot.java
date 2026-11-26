package com.divinis.fishing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import com.divinis.registry.ModIngots;
import com.divinis.skill.XpRules;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What the rod brings up (FishingGame; the vanilla fishing loot is never used). From the most to the least likely:
 * fish, leather (3 - 5), an ingot - the older its era the more often (bronze, iron, steel, then the ten rare metals in
 * their order), and the rarest a skill book (only "test" for now). Luck (rods, pools, Luck of the Sea) makes the rare
 * ones likelier, the rarer the more, and fish a little less likely. Some rods change the odds (Mods). Nothing is
 * enchanted.
 */
public final class FishingLoot {
    private FishingLoot() {}

    /** A catch and the XP it gives. */
    public record Catch(ItemStack stack, double xp) {}

    /** How a rod changes the catch: the odds of fish, ingots and books (times), leather a catch, fur instead, cooked fish. */
    public record Mods(double fish, double ingots, double books, int leatherMin, int leatherMax, double fur, boolean cooked) {
        public static final Mods NONE = new Mods(1, 1, 1, 3, 5, 0, false);

        static Mods of(FishingRods.Gift gift) {
            return switch (gift) {
                case SMITH -> new Mods(0.7, 2, 1, 3, 5, 0, false);
                case TANNER -> new Mods(1, 1, 1, 5, 8, 0.2, false);
                case COOK -> new Mods(1, 1, 1, 3, 5, 0, true);
                case SCHOLAR -> new Mods(1, 1, 3, 3, 5, 0, false);
                default -> NONE;
            };
        }
    }

    private enum Kind { FISH, LEATHER, INGOT, BOOK }

    /** perLuck: how much likelier each point of luck makes it (fish: none, they get less likely instead). */
    private record Entry(Kind kind, double weight, double perLuck, Function<RandomSource, ItemStack> stack, double xp, boolean rare) {
        double weight(int luck, Mods mods) {
            return switch (kind) {
                case FISH -> weight / (1 + 0.04 * luck) * mods.fish();
                case LEATHER -> weight * (1 + perLuck * luck);
                case INGOT -> weight * (1 + perLuck * luck) * mods.ingots();
                case BOOK -> weight * (1 + perLuck * luck) * mods.books();
            };
        }
    }

    private static final double FISH = 70, LEATHER = 20, BOOK = 0.05;
    private static final double INGOT_FIRST = 3.5, INGOT_STEP = 0.62; // each era's ingots this much rarer than the last's
    private static final int RARE_FROM = 3;                           // the ingots from blacksteel on are the rare metals

    private static List<Entry> entries;

    private static List<Entry> entries() {
        if (entries != null) return entries;
        List<Entry> out = new ArrayList<>();
        out.add(new Entry(Kind.FISH, FISH * 0.6, 0, r -> new ItemStack(Items.COD), XpRules.FISH, false));
        out.add(new Entry(Kind.FISH, FISH * 0.25, 0, r -> new ItemStack(Items.SALMON), XpRules.FISH, false));
        out.add(new Entry(Kind.FISH, FISH * 0.1, 0, r -> new ItemStack(Items.TROPICAL_FISH), XpRules.FISH, false));
        out.add(new Entry(Kind.FISH, FISH * 0.05, 0, r -> new ItemStack(Items.PUFFERFISH), XpRules.FISH, false));
        out.add(new Entry(Kind.LEATHER, LEATHER, 0.03, r -> new ItemStack(Items.LEATHER), XpRules.FISH_JUNK, false));
        List<Supplier<Item>> ingots = new ArrayList<>();
        ingots.add(() -> item("alltheores:bronze_ingot", Items.COPPER_INGOT));
        ingots.add(() -> Items.IRON_INGOT);
        ingots.add(() -> item("alltheores:steel_ingot", Items.IRON_INGOT));
        for (var ingot : List.of(ModIngots.BLACKSTEEL_INGOT, ModIngots.MOONSILVER_INGOT, ModIngots.CRIMSONITE_INGOT,
                ModIngots.MITHRIL_INGOT, ModIngots.ETHERITE_INGOT, ModIngots.ADAMANTITE_INGOT, ModIngots.DRAGONSTEEL_INGOT,
                ModIngots.DIVINITE_INGOT, ModIngots.VALORIUM_INGOT, ModIngots.VOIDSTEEL_INGOT)) {
            ingots.add(ingot::get);
        }
        for (int era = 0; era < ingots.size(); era++) {
            Supplier<Item> item = ingots.get(era);
            out.add(new Entry(Kind.INGOT, INGOT_FIRST * Math.pow(INGOT_STEP, era), 0.08 * (1 + era * 0.25),
                    r -> new ItemStack(item.get()), XpRules.FISH_TREASURE, era >= RARE_FROM));
        }
        out.add(new Entry(Kind.BOOK, BOOK, 0.5, r -> com.divinis.skill.SkillBook.byId("test").map(b -> b.stack()).orElse(ItemStack.EMPTY),
                XpRules.FISH_TREASURE * 4, false));
        return entries = out;
    }

    private static Item item(String id, Item fallback) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(fallback);
    }

    public static Catch roll(RandomSource random, int luck) {
        return roll(random, luck, Mods.NONE);
    }

    public static Catch roll(RandomSource random, int luck, Mods mods) {
        return pick(random, luck, mods, e -> true);
    }

    /** Anything but fish (a treasure's things). */
    static Catch rollNoFish(RandomSource random, int luck, Mods mods) {
        return pick(random, luck, mods, e -> e.kind != Kind.FISH);
    }

    /** An ingot, by the usual odds among them (the golden mean's perfect hit). */
    static Catch rollIngot(RandomSource random, int luck) {
        return pick(random, luck, Mods.NONE, e -> e.kind == Kind.INGOT);
    }

    /** A rare metal (blacksteel and on), by the usual odds among them (the Ancient Fish). */
    static Catch rollRareIngot(RandomSource random, int luck) {
        return pick(random, luck, Mods.NONE, e -> e.rare);
    }

    private static Catch pick(RandomSource random, int luck, Mods mods, java.util.function.Predicate<Entry> allowed) {
        luck = Mth.clamp(luck, 0, 100);
        double total = 0;
        for (Entry e : entries()) if (allowed.test(e)) total += e.weight(luck, mods);
        double r = random.nextDouble() * total;
        for (Entry e : entries()) {
            if (!allowed.test(e)) continue;
            r -= e.weight(luck, mods);
            if (r < 0) {
                ItemStack stack = make(e, random, mods);
                if (!stack.isEmpty()) return new Catch(stack, e.xp);
                break;
            }
        }
        return new Catch(new ItemStack(Items.COD), XpRules.FISH);
    }

    private static ItemStack make(Entry e, RandomSource random, Mods mods) {
        if (e.kind == Kind.LEATHER) {
            if (random.nextDouble() < mods.fur()) {
                return new ItemStack(item("divinis:fur", Items.LEATHER), 1 + random.nextInt(2));
            }
            return new ItemStack(Items.LEATHER, Mth.nextInt(random, mods.leatherMin(), mods.leatherMax()));
        }
        ItemStack stack = e.stack.apply(random);
        if (mods.cooked()) {
            if (stack.is(Items.COD)) return new ItemStack(Items.COOKED_COD);
            if (stack.is(Items.SALMON)) return new ItemStack(Items.COOKED_SALMON);
        }
        return stack;
    }
}
