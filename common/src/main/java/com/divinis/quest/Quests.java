package com.divinis.quest;

import java.util.List;
import java.util.Optional;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;

/** The Guild Master's quests, in the order he lists them. */
public final class Quests {
    /** Quests a player can have taken at once. */
    public static final int MAX_ACTIVE = 3;

    public static final List<Quest> ALL = List.of(
            Quest.deliver("iron_for_the_forge", "Iron for the Forge",
                    "The guild's forge burns through iron faster than the miners can bring it up. Fill its crates.",
                    () -> Items.RAW_IRON, "raw iron", 32, s -> s.is(Items.RAW_IRON), 8, 150),
            Quest.slay("clear_the_old_mine", "Clear the Old Mine",
                    "Something keeps dragging the miners into the dark. Make the tunnels safe again.",
                    () -> Items.ZOMBIE_HEAD, "zombies", 10, () -> EntityType.ZOMBIE, 12, 200),
            Quest.deliver("timber_supply", "Timber Supply",
                    "The guild hall needs new beams before the winter. Any wood will do.",
                    () -> Items.OAK_LOG, "logs", 64, s -> s.is(ItemTags.LOGS), 5, 100),
            Quest.slay("web_weavers", "Web Weavers",
                    "Spiders have nested along the trade road. The caravans will not move until they are gone.",
                    () -> Items.COBWEB, "spiders", 12, () -> EntityType.SPIDER, 10, 180),
            Quest.deliver("bone_collector", "Bone Collector",
                    "The guild's alchemist grinds bones into meal for the fields. Bring him a sackful.",
                    () -> Items.BONE, "bones", 24, s -> s.is(Items.BONE), 6, 120),
            Quest.slay("silence_the_archers", "Silence the Archers",
                    "Skeleton archers pick off travellers at night. Put them back in their graves.",
                    () -> Items.BOW, "skeletons", 10, () -> EntityType.SKELETON, 12, 200),
            Quest.deliver("catch_of_the_day", "Catch of the Day",
                    "The guild's cook promised a feast and has nothing to cook. Fish, and plenty of it.",
                    () -> Items.COD, "raw cod", 16, s -> s.is(Items.COD), 6, 120));

    private Quests() {}

    public static Optional<Quest> byId(String id) {
        for (Quest q : ALL) if (q.id().equals(id)) return Optional.of(q);
        return Optional.empty();
    }
}
