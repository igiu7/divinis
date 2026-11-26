package com.divinis.boss;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.divinis.registry.ModEntities;
import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenFx;
import com.divinis.Divinis;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Silk a player is wrapped in by the Arachnid (boss.Arachnid): they cannot move or jump until it is cut open - by
 * their own blows (they can strike it from inside) or a friend's - or its time runs out.
 * - thin (the web of the first phase): a loose net (the player is seen through it), one blow, three seconds;
 * - thick (the cocoon of the second phase): dense silk, STRONG_HEALTH of blows, eight seconds, and it hurts the one
 *   inside every second.
 * The one inside sees web stuck over their screen (ScreenEffect.WEB; darker in the thick one) until they are free.
 * Who is held is kept here (HELD); a player who is held by nothing loses the slowing at once (also after a restart).
 */
@EventBusSubscriber(modid = Divinis.MODID)
public class ArachnidCocoon extends Entity {
    private static final EntityDataAccessor<Boolean> STRONG = SynchedEntityData.defineId(ArachnidCocoon.class, EntityDataSerializers.BOOLEAN);
    private static final ResourceLocation WEBBED = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "arachnid_webbed");
    private static final AttributeModifier NO_MOVE = new AttributeModifier(WEBBED, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final float WEAK_HEALTH = 1, STRONG_HEALTH = 8;
    private static final int WEAK_TICKS = 60, STRONG_TICKS = 160;
    private static final float SQUEEZE = 2.0F;

    /** players held now (server) */
    private static final Set<UUID> HELD = new HashSet<>();

    public final AnimationState struggleAnimation = new AnimationState();
    @Nullable private UUID held;
    private int bossId = -1;
    private float health;
    private int life;

    public ArachnidCocoon(EntityType<? extends ArachnidCocoon> type, Level level) {
        super(type, level);
    }

    /** Wraps a player (unless something holds them already). */
    public static void trap(ServerLevel level, ServerPlayer player, boolean strong, Arachnid boss) {
        if (HELD.contains(player.getUUID()) || !player.isAlive() || player.isCreative() || player.isSpectator()) return;
        ArachnidCocoon c = ModEntities.ARACHNID_COCOON.get().create(level);
        if (c == null) return;
        c.held = player.getUUID();
        c.bossId = boss.getId();
        c.entityData.set(STRONG, strong);
        c.health = strong ? STRONG_HEALTH * Math.max(1, boss.damageScale()) : WEAK_HEALTH;
        c.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        level.addFreshEntity(c);
        HELD.add(player.getUUID());
        hold(player, true);
        ScreenFx.play(player, ScreenEffect.WEB, strong ? STRONG_TICKS : WEAK_TICKS, strong ? 1.0F : 0.6F, 0xF0EEE8, "");
        if (strong) ScreenFx.play(player, ScreenEffect.DARKNESS, STRONG_TICKS, 0.35F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), strong ? SoundEvents.WOOL_PLACE : SoundEvents.COBWEB_PLACE,
                SoundSource.HOSTILE, 2.0F, 0.6F);
        Arachnid.silk(level, player.position().add(0, 1, 0), 20, 0.5);
    }

    /** Frees everyone the boss with this id holds (it died or went). */
    public static void freeAll(ServerLevel level, int bossId) {
        for (Entity e : level.getAllEntities()) {
            if (e instanceof ArachnidCocoon c && c.bossId == bossId) c.free();
        }
    }

    private static void hold(Player player, boolean on) {
        for (var attr : java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
            AttributeInstance a = player.getAttribute(attr);
            if (a == null) continue;
            if (on) a.addOrUpdateTransientModifier(NO_MOVE);
            else a.removeModifier(WEBBED);
        }
    }

    public boolean strong() {
        return entityData.get(STRONG);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STRONG, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            struggleAnimation.startIfStopped(tickCount);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        life++;
        Player p = held == null ? null : level.getPlayerByUUID(held);
        if (p == null || !p.isAlive() || p.level() != level || health <= 0 || life >= (strong() ? STRONG_TICKS : WEAK_TICKS)) {
            free();
            return;
        }
        if (p.distanceToSqr(position()) > 0.09 && p instanceof ServerPlayer sp) {
            sp.connection.teleport(getX(), getY(), getZ(), sp.getYRot(), sp.getXRot());
        }
        if (life % 10 == 0) Arachnid.silk(level, position().add(0, 1.2, 0), 2, 0.4);
        if (strong() && life % 20 == 0) {
            Entity boss = level.getEntity(bossId);
            DamageSource src = boss instanceof Arachnid a ? damageSources().mobAttack(a) : damageSources().magic();
            float scale = boss instanceof Arachnid a ? a.damageScale() : 1;
            p.hurt(src, SQUEEZE * scale);
        }
    }

    /** Cut open (or its time is over): the one inside can move again. */
    private void free() {
        if (held != null) {
            HELD.remove(held);
            Player p = level().getPlayerByUUID(held);
            if (p != null) hold(p, false);
            if (p instanceof ServerPlayer sp) ScreenFx.clear(sp); // the web off their screen
            held = null;
        }
        if (level() instanceof ServerLevel level && !isRemoved()) {
            Arachnid.silk(level, position().add(0, 1, 0), 30, 0.5);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_BREAK, SoundSource.HOSTILE, 1.5F, 0.8F);
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || !(source.getEntity() instanceof Player)) return false;
        health -= Math.max(1, amount / 4);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_HIT, SoundSource.HOSTILE, 1.2F, 0.9F);
        ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 3, 0.3, 0.5, 0.3, 0.01);
        if (health <= 0) free();
        return true;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (held != null && !level().isClientSide) {
            HELD.remove(held);
            Player p = level().getPlayerByUUID(held);
            if (p != null) hold(p, false);
            held = null;
        }
        super.remove(reason);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    /** A player held by nothing (the cocoon gone some other way, a restart) is let go at once. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % 10 == 0 && !HELD.contains(p.getUUID())) {
            AttributeInstance a = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (a != null && a.hasModifier(WEBBED)) hold(p, false);
        }
    }
}
