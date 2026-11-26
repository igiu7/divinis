package com.divinis.npc;

import com.divinis.net.GuildMasterOpenPayload;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Guild Master: a dwarf who stands where he was summoned (/summon divinis:guild_master) and cannot be hurt
 * (only /kill removes him). Right-click opens his menu (client/gui/GuildMasterScreen) and he waves a greeting.
 */
public class GuildMaster extends PathfinderMob {
    private static final byte EVENT_GREET = 100;
    private static final int GREET_TICKS = 40;

    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState greetAnimation = new AnimationState();
    private int greetTicks;

    public GuildMaster(EntityType<? extends GuildMaster> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.translatable("entity.divinis.guild_master"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            var ranking = com.divinis.dungeon.DungeonData.get(sp.getServer()).top(10).stream()
                    .map(c -> new GuildMasterOpenPayload.RankRow(c.names(), c.ticks(), c.players())).toList();
            PacketDistributor.sendToPlayer(sp, new GuildMasterOpenPayload(getId(), ranking));
            level().broadcastEntityEvent(this, EVENT_GREET);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_GREET) {
            greetAnimation.start(tickCount);
            greetTicks = GREET_TICKS;
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            idleAnimation.startIfStopped(tickCount);
            if (greetTicks > 0 && --greetTicks == 0) greetAnimation.stop();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {}

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
