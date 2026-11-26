package com.divinis.boss;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.divinis.util.Msg;
import com.divinis.Divinis;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Minotaur's Axe. Right-click: the Minotaur's charge. The holder plants his feet for a moment while a red lane
 * (boss.Telegraphs) shows the way he will go, then runs it, hitting hard everything he runs into, once each. Not on
 * horseback (or riding anything); one minute until it can be used again.
 */
public class MinotaurAxeItem extends AxeItem {
    static final int COOLDOWN = 60 * 20;
    static final int WINDUP = 10, RUN = 8;
    static final float LENGTH = 10, HALF_WIDTH = 1.2F, DAMAGE = 20;

    /** Bronze's stats (190 uses, speed 5): mines what iron mines, mended with bronze ingots. */
    public static final Tier TIER = new net.neoforged.neoforge.common.SimpleTier(net.minecraft.tags.BlockTags.INCORRECT_FOR_IRON_TOOL,
            190, 5.0F, 1.5F, 10, () -> net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.parse("c:ingots/bronze"))));

    public MinotaurAxeItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isPassenger()) {
            if (!level.isClientSide) player.displayClientMessage(Msg.text("You cannot charge while riding.", ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) return InteractionResultHolder.consume(stack);
        ServerPlayer sp = (ServerPlayer) player;
        player.getCooldowns().addCooldown(this, COOLDOWN);
        Charge.start(sp);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click: charge (once a minute)").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Gets half of every blow through shields").withStyle(ChatFormatting.GRAY));
    }

    /** The charges going on (server). */
    @EventBusSubscriber(modid = Divinis.MODID)
    public static final class Charge {
        private Charge() {}

        private static final class Run {
            final ServerLevel level;
            final Vec3 dir;
            final float yaw;
            final Set<UUID> hit = new HashSet<>();
            int age;

            Run(ServerLevel level, Vec3 dir, float yaw) {
                this.level = level;
                this.dir = dir;
                this.yaw = yaw;
            }
        }

        private static final Map<UUID, Run> RUNS = new HashMap<>();

        static void start(ServerPlayer player) {
            float yaw = player.getYRot();
            Vec3 dir = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
            ServerLevel level = player.serverLevel();
            RUNS.put(player.getUUID(), new Run(level, dir, yaw));
            // feet planted while the lane shows
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WINDUP, 6, false, false, false));
            Telegraphs.show(level, Telegraph.rect(player.position(), yaw, LENGTH, HALF_WIDTH), WINDUP, RUN, player, null);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8F, 1.3F);
        }

        @SubscribeEvent
        public static void onTick(ServerTickEvent.Post event) {
            if (RUNS.isEmpty()) return;
            MinecraftServer server = event.getServer();
            for (Iterator<Map.Entry<UUID, Run>> it = RUNS.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Run> e = it.next();
                ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
                Run run = e.getValue();
                if (player == null || !player.isAlive() || player.level() != run.level || player.isPassenger()) {
                    it.remove();
                    continue;
                }
                run.age++;
                if (run.age <= WINDUP) continue;
                if (run.age > WINDUP + RUN || run.age > WINDUP + 1 && player.horizontalCollision) {
                    player.setDeltaMovement(player.getDeltaMovement().multiply(0.2, 1, 0.2));
                    player.hurtMarked = true;
                    it.remove();
                    continue;
                }
                step(player, run);
            }
        }

        private static void step(ServerPlayer player, Run run) {
            double speed = LENGTH / RUN;
            player.setDeltaMovement(run.dir.x * speed, Math.min(player.getDeltaMovement().y, 0.1), run.dir.z * speed);
            player.hurtMarked = true; // sends the push to the player's client
            ServerLevel level = run.level;
            if (run.age == WINDUP + 1) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1.2F, 0.8F);
            }
            BlockState under = level.getBlockState(BlockPos.containing(player.getX(), player.getY() - 0.5, player.getZ()));
            if (!under.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), player.getX(), player.getY() + 0.1,
                        player.getZ(), 5, 0.4, 0.1, 0.4, 0.1);
            }
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.8, 0.3, 0.8),
                    t -> t != player && t.isAlive() && !(t instanceof Player p && (p.isCreative() || p.isSpectator())))) {
                if (!run.hit.add(target.getUUID())) continue;
                if (!target.hurt(level.damageSources().playerAttack(player), DAMAGE)) continue;
                double resist = 1.0 - target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
                target.setDeltaMovement(target.getDeltaMovement().add(run.dir.x * 1.6 * resist, 0.5 * resist, run.dir.z * 1.6 * resist));
                target.hurtMarked = true;
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.7F);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(), 12, 0.4, 0.4, 0.4, 0.2);
            }
        }

        @SubscribeEvent
        public static void onStopped(ServerStoppedEvent event) {
            RUNS.clear();
        }
    }
}
