package com.divinis.boss;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.divinis.registry.ModParticles;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A shade of a fallen one (boss.Azramond): a dark copy of a player (client.LichShadeRenderer draws them in their own skin,
 * black and see-through) that walks their path again Azramond.SHADE_DELAY ticks behind them and strikes where they
 * struck (the half circle before it, marked). Touching it hurts. It can be cut down, and fades after its time.
 */
public class LichShade extends Monster {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(LichShade.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final float TOUCH_DAMAGE = 6, STRIKE_DAMAGE = 8, STRIKE_RADIUS = 2.6F;
    private static final int STRIKE_WARN = 8;

    private int bossId = -1, life;
    private final Map<UUID, Integer> touched = new HashMap<>();

    public LichShade(EntityType<? extends LichShade> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    void follow(Azramond boss, Player player) {
        bossId = boss.getId();
        entityData.set(OWNER, Optional.of(player.getUUID()));
        setCustomName(player.getName().copy().append(" (shade)"));
    }

    /** Client: whose shade it is (its skin). */
    public Optional<UUID> owner() {
        return entityData.get(OWNER);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
    }

    @Override
    protected void registerGoals() {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 4 == 0) level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 0, 0.01, 0);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        Azramond boss = level.getEntity(bossId) instanceof Azramond a ? a : null;
        life++;
        UUID who = owner().orElse(null);
        if (boss == null || !boss.isAlive() || who == null || life > Azramond.SHADE_LIFE) {
            vanish(level);
            return;
        }
        Azramond.Step step = boss.stepOf(who, Azramond.SHADE_DELAY);
        if (step != null) { // where they were, what they did
            setPos(step.pos().x, step.pos().y, step.pos().z);
            setYRot(step.yaw());
            setYHeadRot(step.yaw());
            setYBodyRot(step.yaw());
            setXRot(step.pitch());
            if (step.swing()) strike(level, boss);
        }
        for (Player p : boss.fighters()) { // its touch
            if (!getBoundingBox().intersects(p.getBoundingBox())) continue;
            int last = touched.getOrDefault(p.getUUID(), -100);
            if (life - last < 20) continue;
            touched.put(p.getUUID(), life);
            if (p.hurt(damageSources().mobAttack(boss), TOUCH_DAMAGE * boss.damageScale())) {
                level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
            }
        }
    }

    /** It strikes where its player struck: the half circle before it. */
    private void strike(ServerLevel level, Azramond boss) {
        swing(InteractionHand.MAIN_HAND, true);
        Telegraphs.show(level, Telegraph.sector(position(), getYRot(), STRIKE_RADIUS, 60), STRIKE_WARN, 3, boss, e -> {
            if (e != this && e instanceof Player) boss.hit(e, STRIKE_DAMAGE, 0.6, 0.2);
        });
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F, 0.6F);
    }

    private void vanish(ServerLevel level) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.02);
        level.sendParticles(ModParticles.NECRO_FLAME.get(), getX(), getY() + 1, getZ(), 6, 0.3, 0.5, 0.3, 0.02);
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(source.getEntity() instanceof Player) && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    protected void tickDeath() {
        if (level() instanceof ServerLevel level) vanish(level);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VEX_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }
}
