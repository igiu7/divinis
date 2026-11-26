package com.divinis.forge;

import java.util.List;
import java.util.Optional;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.pockyl.diegesis.api.station.StationType;
import com.divinis.net.ForgeKnownPayload;
import com.divinis.skill.Skill;
import com.divinis.skill.SkillService;
import com.divinis.skill.XpRules;
import com.divinis.Divinis;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.divinis.platform.net.PacketDistributor;

/**
 * The anvil is a smithy, built on Diegesis (MIT, by Pocky - github.com/Pocky-l/diegesis): using one flies the camera to
 * it and its vanilla menu is gone. Beside it hangs a book of recipes - the weapon parts, the same in every metal, each
 * with its outline; the player lays hot ingots of a rare metal (heated in a SmeltingForge) on the anvil (its one slot, "ingots") and the metal they are
 * makes the part's. A recipe chosen, the ingots are a hot bar on the anvil, hammered flat (three strokes) and cut to the
 * part's shape pixel by pixel (client: client.forge.AnvilBehavior, scene assets/divinis/diegesis/scenes/anvil.json). "Done" sends the
 * cut here: it must be exactly the part's shape, the city must know the metal's weapons and the anvil must hold the
 * ingots - then they are taken from it and the part given, still hot (ModWeapons.HOT_PARTS: quenched in the cauldron's
 * water it is the part, CauldronStation). Ingots left on the anvil go back to the player when they leave.
 */
public final class AnvilForge {
    private AnvilForge() {}

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "anvil");
    public static final String FORGE = "forge";
    public static final String SLOT = "ingots";
    /** The player cut into the part: the ingots it took are ruined. */
    public static final String RUIN = "ruin";

    /** The metal of a hot rare-metal ingot ("divinis:mithril_hot_ingot" -> "mithril"), or null: only these go on the anvil. */
    public static String hotMetalOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (var e : com.divinis.registry.ModIngots.HOT_INGOTS.entrySet()) if (stack.is(e.getValue().get())) return e.getKey();
        return null;
    }

    /** The metal of a (cold) rare-metal ingot ("divinis:mithril_ingot" -> "mithril"), or null. */
    public static String metalOf(ItemStack stack) {
        for (String metal : ForgeShapes.METALS) if (!stack.isEmpty() && stack.is(ForgeShapes.ingot(metal))) return metal;
        return null;
    }

    public static StationType STATION;

    /** Registers the station (mod setup, both sides). */
    public static void register() {
        STATION = Diegesis.station(ID)
                .tag(BlockTags.ANVIL)
                .orientation(StationType.Orientation.BLOCK)
                .exclusive()
                .schema(StationSchema.builder().slots(SLOT, 1, SlotRules.DEFAULT.accepts(stack -> hotMetalOf(stack) != null)).build())
                .logic(Logic::new)
                .register();
    }

    private static final class Logic implements StationLogic {
        Logic(StationContext station) {}

        @Override
        public void onOpen(StationContext station, Player player) {
            if (!(player instanceof ServerPlayer sp)) return;
            List<String> known = Divinis.hooks().knownTechs(sp);
            PacketDistributor.sendToPlayer(sp, new ForgeKnownPayload(known == null ? List.of() : known, known == null,
                    Divinis.hooks().techNames()));
        }

        @Override
        public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
            if (!(player instanceof ServerPlayer sp)) return;
            if (RUIN.equals(action)) {
                ruin(station, sp, ForgeShapes.shape(data.getString("part")));
                return;
            }
            if (!FORGE.equals(action)) return;
            SlotRef slot = SlotRef.of(SLOT);
            ItemStack laid = station.state().getItem(slot);
            String metal = hotMetalOf(laid);
            ForgeShapes.Shape shape = ForgeShapes.shape(data.getString("part"));
            if (shape == null || metal == null) {
                deny(sp, "Lay hot ingots of a rare metal on the anvil (heat them in a Smelting Forge).");
                return;
            }
            Item result = ForgeShapes.part(metal, shape.part());
            if (result == net.minecraft.world.item.Items.AIR) return;
            // the cut must be the shape, pixel for pixel
            boolean[][] kept = ForgeShapes.unpack(data.getLongArray("kept"));
            for (int y = 0; y < ForgeShapes.SIZE; y++) {
                for (int x = 0; x < ForgeShapes.SIZE; x++) {
                    if (kept[y][x] != shape.pixels()[y][x]) {
                        deny(sp, "The cut does not match the part's shape.");
                        return;
                    }
                }
            }
            Optional<String> blocked = com.divinis.Divinis.hooks().craftBlocked(sp, new ItemStack(result));
            if (blocked.isPresent()) {
                deny(sp, blocked.get());
                return;
            }
            if (laid.getCount() < shape.ingots()) {
                deny(sp, "The anvil needs " + shape.ingots() + " ingots for this part.");
                return;
            }
            Item ingot = laid.getItem();
            ItemStack rest = laid.copy();
            rest.shrink(shape.ingots());
            station.state().setItem(slot, rest);
            Inventory inv = sp.getInventory();
            // the part comes off the anvil hot: quenched in a cauldron of water it is the part (CauldronStation)
            var hot = com.divinis.registry.ModWeapons.HOT_PARTS.get(metal + "_hot_" + shape.part());
            inv.placeItemBackInInventory(new ItemStack(hot != null ? hot.get() : result));
            sp.containerMenu.broadcastChanges();
            sp.level().playSound(null, station.pos(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.3F);
            SkillService.addXp(sp, Skill.BLACKSMITH, Math.max(5, XpRules.material(new ItemStack(ingot)) * shape.ingots()));
        }

        /** Cutting into the part ruins the metal: the ingots it takes are gone. */
        private static void ruin(StationContext station, ServerPlayer sp, ForgeShapes.Shape shape) {
            SlotRef slot = SlotRef.of(SLOT);
            ItemStack laid = station.state().getItem(slot);
            if (shape == null || hotMetalOf(laid) == null) return;
            ItemStack rest = laid.copy();
            rest.shrink(Math.min(shape.ingots(), laid.getCount()));
            station.state().setItem(slot, rest);
            sp.level().playSound(null, station.pos(), SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 0.5F, 1.2F);
            deny(sp, "You cut into the part: the metal is ruined.");
        }

        private static void deny(ServerPlayer player, String why) {
            player.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.RED), true);
        }
    }
}
