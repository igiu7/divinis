package com.divinis.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.divinis.registry.ItemRarity;
import com.divinis.registry.ModBlocks;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * A skill book: put into the fourth skill tree (CharacterService.insertBook) it gives that tree its orbs - four, POINTS
 * in all: a passive, a skill, a passive, a skill (what they do: Perks, SkillEffects). Each book is an item,
 * "skill_book_<id>" (SkillBook.Book), in ModBlocks' item register, of its rarity (registry.ItemRarity). The books:
 * SkillBooks; "test" keeps its orbs empty.
 */
public record SkillBook(String id, String name, int maxPoints, List<SkillOrb> orbs, ItemRarity rarity) {
    /** Points a book's tree takes: 2 passives and 2 skills, an orb every SkillTree.POINTS_PER_ORB. */
    public static final int POINTS = 4 * SkillTree.POINTS_PER_ORB;

    private static final Map<String, SkillBook> BOOKS = new LinkedHashMap<>();
    private static final Map<String, DeferredItem<Book>> ITEMS = new LinkedHashMap<>();

    static {
        add(new SkillBook("test", "Test", POINTS, SkillTree.emptyOrbs(POINTS), ItemRarity.RARE));
        SkillBooks.all().forEach(SkillBook::add);
    }

    private static void add(SkillBook book) {
        BOOKS.put(book.id(), book);
        DeferredItem<Book> item = ModBlocks.ITEMS.register("skill_book_" + book.id(),
                () -> new Book(book.id(), new Item.Properties().stacksTo(1)));
        ITEMS.put(book.id(), item);
        ItemRarity.assign(item, book.rarity());
    }

    public static Optional<SkillBook> byId(String id) {
        return Optional.ofNullable(id == null ? null : BOOKS.get(id));
    }

    /** The book an item is, if it is one. */
    public static Optional<SkillBook> of(ItemStack stack) {
        return stack.getItem() instanceof Book b ? byId(b.book) : Optional.empty();
    }

    public static java.util.Collection<SkillBook> all() {
        return BOOKS.values();
    }

    public ItemStack stack() {
        return new ItemStack(ITEMS.get(id).get());
    }

    /** Loads the class, so the books are in the item register before it is registered. */
    public static void init() {}

    /** The item of a skill book: its orbs in the tooltip. */
    public static final class Book extends Item {
        final String book;

        Book(String book, Properties properties) {
            super(properties);
            this.book = book;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.literal("Skill book - put it into the fourth tree of your skills").withStyle(ChatFormatting.GRAY));
            SkillBook.byId(book).ifPresent(b -> {
                for (SkillOrb orb : b.orbs()) {
                    if (orb.id().isEmpty()) continue;
                    lines.add(Component.literal((orb.skill() ? "Skill" : "Passive") + " at " + orb.points() + ": ").withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.literal(orb.name()).withStyle(orb.skill() ? ChatFormatting.AQUA : ChatFormatting.GREEN)));
                }
            });
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }
    }
}
