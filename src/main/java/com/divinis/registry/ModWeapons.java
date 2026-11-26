package com.divinis.registry;

import java.util.LinkedHashMap;
import java.util.Map;

import com.divinis.Divinis;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The rare-metal weapons (3D models from Blender: tools/models/build_blacksteel_weapons.py, the same in every metal
 * with its own texture) in every metal: "<metal>_<weapon>". Their damage comes from the technology config's
 * weaponBalance ('<material>_<type>': each metal a little stronger than the one before, and each weapon's type), as
 * Epic Knights' weapons do; here each gets its attack speed and reach
 * (a long weapon reaches further, a dagger less - tech.TechEvents caps the extra reach). A lance hits half as hard again
 * from the saddle. Each weapon is made of its metal part ("<metal>_sword_blade", "<metal>_spear_head", ...: PARTS),
 * forged from ingots in the Armory Table (tools/build_weapon_parts.py).
 */
@EventBusSubscriber(modid = Divinis.MODID)
public final class ModWeapons {
    private ModWeapons() {}

    /** weapon -> damage (before the weaponBalance), attack speed (as vanilla's: sword -2.4), extra reach */
    private record Kind(float damage, float speed, double reach, boolean axe) {}

    private static final Map<String, Kind> KINDS = new LinkedHashMap<>();

    static {
        KINDS.put("sword", new Kind(6.0F, -2.4F, 0.0, false));
        KINDS.put("longsword", new Kind(7.0F, -2.8F, 0.5, false));
        KINDS.put("shortsword", new Kind(5.0F, -2.0F, 0.0, false));
        KINDS.put("dagger", new Kind(4.0F, -1.6F, -0.5, false));
        KINDS.put("sabre", new Kind(6.0F, -2.3F, 0.0, false));
        KINDS.put("spear", new Kind(5.5F, -2.6F, 1.5, false));
        KINDS.put("lance", new Kind(7.0F, -3.2F, 1.5, false));
        KINDS.put("halberd", new Kind(9.0F, -3.3F, 1.5, false));
        KINDS.put("two_handed_axe", new Kind(9.0F, -3.3F, 0.5, true));
    }

    public static final Map<String, DeferredItem<? extends Item>> WEAPONS = new LinkedHashMap<>();

    /** weapon -> its metal part */
    private static final Map<String, String> PART_OF = Map.of("sword", "sword_blade", "longsword", "longsword_blade",
            "shortsword", "shortsword_blade", "dagger", "dagger_blade", "sabre", "sabre_blade", "spear", "spear_head",
            "lance", "lance_tip", "halberd", "halberd_head", "two_handed_axe", "two_handed_axe_head");

    /** Every weapon part, by name (e.g. "mithril_dagger_blade"). */
    public static final Map<String, DeferredItem<Item>> PARTS = new LinkedHashMap<>();

    /** The hot parts, one of each part in each metal ("mithril_hot_dagger_blade"): one texture a part (tools/build_hot_parts.py). */
    public static final Map<String, DeferredItem<Item>> HOT_PARTS = new LinkedHashMap<>();

    private static final ResourceLocation REACH = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "weapon_reach");

    static {
        int rank = 0;
        for (Map.Entry<String, Tier> m : ModFantasyTools.TIERS.entrySet()) {
            register(m.getKey(), m.getValue(), rank < 3 ? Rarity.UNCOMMON : rank < 6 ? Rarity.RARE : Rarity.EPIC);
            rank++;
        }
    }

    private static void register(String metal, Tier tier, Rarity rarity) {
        KINDS.forEach((name, k) -> {
            String part = metal + "_" + PART_OF.get(name);
            PARTS.put(part, ModBlocks.ITEMS.register(part, () -> new Item(new Item.Properties().rarity(rarity))));
            String hot = metal + "_hot_" + PART_OF.get(name);
            HOT_PARTS.put(hot, ModBlocks.ITEMS.register(hot, () -> new Item(new Item.Properties().rarity(rarity))));
            String id = metal + "_" + name;
            WEAPONS.put(id, ModBlocks.ITEMS.register(id, () -> {
                ItemAttributeModifiers attrs = k.axe()
                        ? AxeItem.createAttributes(tier, k.damage() - tier.getAttackDamageBonus(), k.speed())
                        : SwordItem.createAttributes(tier, (int) (k.damage() - tier.getAttackDamageBonus()), k.speed());
                if (k.reach() != 0) {
                    attrs = attrs.withModifierAdded(Attributes.ENTITY_INTERACTION_RANGE,
                            new AttributeModifier(REACH, k.reach(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                }
                Item.Properties props = new Item.Properties().rarity(rarity).attributes(attrs);
                return k.axe() ? new AxeItem(tier, props) : new SwordItem(tier, props);
            }));
        });
    }

    /** A lance from the saddle: half as hard again. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || event.getSource().getDirectEntity() != attacker) return;
        if (!attacker.isPassenger()) return;
        Item held = attacker.getMainHandItem().getItem();
        for (Map.Entry<String, DeferredItem<? extends Item>> e : WEAPONS.entrySet()) {
            if (e.getKey().endsWith("_lance") && held == e.getValue().get()) {
                event.setAmount(event.getAmount() * 1.5F);
                return;
            }
        }
    }

    /** Loads the class, so the weapons are in the register before it is registered (after ModFantasyTools). */
    public static void init() {}
}
