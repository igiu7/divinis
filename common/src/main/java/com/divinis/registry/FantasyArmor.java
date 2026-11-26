package com.divinis.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.divinis.Divinis;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import com.divinis.platform.bus.IEventBus;
import com.divinis.platform.registry.DeferredItem;
import com.divinis.platform.registry.DeferredRegister;

/**
 * The seven fantasy armour sets - medieval fantasy armour as an isekai hero would wear it - in every rare metal
 * (ModIngots), "<metal>_<set>_<piece>". Each set has one 3D model, over the player and in the hand
 * (client.BlacksteelArmorModels, from tools/models/build_blacksteel_armor.py), painted in each metal's colours
 * (textures/models/armor/<metal>_<set>_layer_1.png, the same as _layer_2); some have a real cape (client.ArmorCapeLayer).
 *
 * Blacksteel is about diamond; the sets differ a little - the Radiant Paladin protects the most, the Guild Ranger and
 * the Arcane Spellblade the least but they are the easiest to enchant. Each metal after blacksteel (the same order as
 * the tools) is a little better than the one before: tougher (+0.25), steadier (+1% knockback resistance), easier to
 * enchant (+1), longer lasting; from mithril on the chest piece and from dragonsteel the leggings give one point more.
 * Repaired with the metal's ingot.
 */
public final class FantasyArmor {
    private FantasyArmor() {}

    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Divinis.MODID);

    private static final Map<String, Supplier<Item>> METALS = new LinkedHashMap<>();

    static {
        METALS.put("blacksteel", ModIngots.BLACKSTEEL_INGOT::get);
        METALS.put("moonsilver", ModIngots.MOONSILVER_INGOT::get);
        METALS.put("crimsonite", ModIngots.CRIMSONITE_INGOT::get);
        METALS.put("mithril", ModIngots.MITHRIL_INGOT::get);
        METALS.put("etherite", ModIngots.ETHERITE_INGOT::get);
        METALS.put("adamantite", ModIngots.ADAMANTITE_INGOT::get);
        METALS.put("dragonsteel", ModIngots.DRAGONSTEEL_INGOT::get);
        METALS.put("divinite", ModIngots.DIVINITE_INGOT::get);
        METALS.put("valorium", ModIngots.VALORIUM_INGOT::get);
        METALS.put("voidsteel", ModIngots.VOIDSTEEL_INGOT::get);
    }

    /** A set's base numbers (in blacksteel). */
    private record Base(int helmet, int chest, int legs, int boots, int enchant, Holder<SoundEvent> sound, float toughness, float knockback) {}

    private static final Map<String, Base> SETS = new LinkedHashMap<>();

    static {
        SETS.put("vanguard", new Base(3, 8, 6, 3, 14, SoundEvents.ARMOR_EQUIP_IRON, 2.0F, 0.05F));
        SETS.put("swordsman", new Base(3, 7, 5, 3, 16, SoundEvents.ARMOR_EQUIP_LEATHER, 1.5F, 0.0F));
        SETS.put("paladin", new Base(4, 9, 7, 3, 12, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0F, 0.12F));
        SETS.put("spellblade", new Base(3, 6, 5, 2, 22, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.0F));
        SETS.put("dragonknight", new Base(3, 8, 7, 3, 12, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.5F, 0.1F));
        SETS.put("demonlord", new Base(4, 8, 6, 3, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.5F, 0.08F));
        SETS.put("ranger", new Base(2, 6, 5, 2, 18, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.0F));
    }

    /** Every piece. */
    public static final List<DeferredItem<ArmorItem>> PIECES = new ArrayList<>();

    /** A piece: drawn with its set's model and its metal's texture, whatever the slot. */
    public static class Piece extends ArmorItem {
        public final String metal, set;

        public Piece(String metal, String set, Holder<ArmorMaterial> material, Type type, int rank) {
            super(material, type, new Item.Properties().rarity(rank < 3 ? Rarity.UNCOMMON : rank < 6 ? Rarity.RARE : Rarity.EPIC)
                    .durability(type.getDurability(33 + rank * 3)));
            this.metal = metal;
            this.set = set;
        }

        public ResourceLocation texture() {
            return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/models/armor/" + metal + "_" + set + "_layer_1.png");
        }
    }

    static {
        int rank = 0;
        for (Map.Entry<String, Supplier<Item>> m : METALS.entrySet()) {
            String metal = m.getKey();
            Supplier<Item> ingot = m.getValue();
            int r = rank;
            for (Map.Entry<String, Base> e : SETS.entrySet()) {
                String set = e.getKey();
                Base b = e.getValue();
                Holder<ArmorMaterial> material = MATERIALS.<ArmorMaterial>register(metal + "_" + set, () -> {
                    EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
                    defense.put(ArmorItem.Type.HELMET, b.helmet());
                    defense.put(ArmorItem.Type.CHESTPLATE, b.chest() + (r >= 3 ? 1 : 0));
                    defense.put(ArmorItem.Type.LEGGINGS, b.legs() + (r >= 6 ? 1 : 0));
                    defense.put(ArmorItem.Type.BOOTS, b.boots());
                    defense.put(ArmorItem.Type.BODY, b.chest());
                    return new ArmorMaterial(defense, b.enchant() + r, b.sound(), () -> Ingredient.of(ingot.get()),
                            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, metal + "_" + set))),
                            b.toughness() + r * 0.25F, b.knockback() + r * 0.01F);
                }).holder();
                for (ArmorItem.Type type : new ArmorItem.Type[] {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
                        ArmorItem.Type.BOOTS}) {
                    PIECES.add(ModBlocks.ITEMS.register(metal + "_" + set + "_" + type.getName(), () -> new Piece(metal, set, material, type, r)));
                }
            }
            rank++;
        }
    }

    public static void register(IEventBus modBus) {
        MATERIALS.register(modBus);
    }
}
