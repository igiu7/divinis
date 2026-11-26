package com.divinis.boss;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.divinis.registry.ModParticles;
import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenEffect.Anchor;
import com.divinis.screenfx.ScreenFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Evil Brother of the Guild Master (/summon divinis:evil_brother): the Guild Master's dwarf twin at the levers of
 * a steam mech six blocks tall - the boss after the Golem (more health and harder blows). Its attacks are telegraphed
 * (boss.Telegraphs: whoever stands in the red when it fills is hit), timed to the animations in client/EvilBrotherModelData
 * (tools/models/build_evil_brother.py).
 *
 * Phase 1, the mech under armour:
 * - Rivet spiral: the steam hammer comes down and drives rivets through the ground in a spiral running out from it,
 *   one bursting up after another;
 * - Harpoon: the cannon fires a harpoon on a chain along a line at its target; whoever it catches is reeled in to
 *   its feet - into the hammer's blow on the ground before it;
 * - Gear saws: it flings three spinning saw-toothed gears, which roll out along the ground and come back to it.
 * At half health it kneels, the dwarf throws the levers, the boiler shudders and its steam engine starts: the chimneys
 * blow out, it rears up roaring, and from then on it breathes steam everywhere and the air round it shimmers with heat.
 * Phase 2, all steam:
 * - Scalding geysers: both fists into the ground, and geysers of steam burst up out of squares round it - a random
 *   half of them first, shown in red; only when they have burst are the next ones shown (another random half, maybe
 *   where the first burst too), with less time to get out;
 * - Pressure release: it opens its valves and turns round on the spot, a jet of steam sweeping the whole circle;
 * - Boiler rush: three dashes on jets of steam at its target, each leaving a trail of scalding steam behind;
 * - Whiteout: it fills the room with steam - nothing can be seen but its glow - and charges out of the white three
 *   times, the warning showing only late.
 * Dying, its engine overheats: it kneels, the boiler glows and shakes ever harder, steam bursts out everywhere, and
 * after five seconds it blows up (a blast for the eyes only: no blocks break, nobody is hurt).
 */
public class EvilBrother extends Monster implements BossTargeting.Marked {
    public static final byte FIGHT = 0, TRANSFORM = 1;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(EvilBrother.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(EvilBrother.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> HIDDEN = SynchedEntityData.defineId(EvilBrother.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(EvilBrother.class, EntityDataSerializers.INT);

    public enum Attack {
        NONE(0), POUND(44), HARPOON(52), GEARS(40), GEYSERS(70), SWEEP(112), RUSH(90), WHITEOUT(132);

        final int length;

        Attack(int length) {
            this.length = length;
        }
    }

    private static final byte EVENT_ATTACK = (byte) 130; // + attack ordinal (the Minotaur uses 110+, the Golem 120+)
    public static final int TRANSFORM_TICKS = 120, DEATH_TICKS = 100;
    private static final double AGGRO_RANGE = 48;
    /** The model is drawn 1.5 times its pixels: a pixel is this many blocks. */
    public static final double PX = 1.5 / 16;

    private static final float RIVET_DAMAGE = 16, HARPOON_DAMAGE = 6, SMASH_DAMAGE = 24, GEAR_DAMAGE = 14;
    private static final float GEYSER_DAMAGE = 18, SWEEP_DAMAGE = 8, RUSH_DAMAGE = 22, TRAIL_DAMAGE = 4, CHARGE_DAMAGE = 24;
    private static final int RIVETS = 18, GEAR_OUT = 16;
    private static final float GEAR_RANGE = 18;

    // client: animations
    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState poundAnimation = new AnimationState();
    public final AnimationState harpoonAnimation = new AnimationState();
    public final AnimationState gearsAnimation = new AnimationState();
    public final AnimationState transformAnimation = new AnimationState();
    public final AnimationState geysersAnimation = new AnimationState();
    public final AnimationState sweepAnimation = new AnimationState();
    public final AnimationState rushAnimation = new AnimationState();
    public final AnimationState deathAnimation = new AnimationState();
    private int clientAttackTicks;
    /** Ticks since it began dying (both sides; its own count, so vanilla neither tips nor reddens it). */
    public int dying;

    // server: the fight
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private final BossTargeting targeting = new BossTargeting();
    private Attack attack = Attack.NONE, last = Attack.NONE;
    private int attackTick, cooldown = 40, transformTick;
    private float lockedYaw;
    private Vec3 aim = Vec3.ZERO;
    private final List<Player> hooked = new ArrayList<>();
    @Nullable private Display.ItemDisplay harpoon;

    /** A gear saw rolling out and back: its display, the way out, who it has cut on this pass. */
    private static final class Gear {
        final Display.ItemDisplay shown;
        final Vec3 from, to;
        final Set<LivingEntity> cut = new HashSet<>();
        boolean back;

        Gear(Display.ItemDisplay shown, Vec3 from, Vec3 to) {
            this.shown = shown;
            this.from = from;
            this.to = to;
        }
    }

    private final List<Gear> gears = new ArrayList<>();

    public EvilBrother(EntityType<? extends EvilBrother> type, Level level) {
        super(type, level);
        this.xpReward = 400;
        setPersistenceRequired();
        bossBar.setVisible(true);
    }

    /** After the Golem (400 health, 16 armour): more of both, and harder blows. */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 560.0)
                .add(Attributes.ARMOR, 18.0)
                .add(Attributes.ARMOR_TOUGHNESS, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 22.0)
                .add(Attributes.STEP_HEIGHT, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, FIGHT);
        builder.define(PHASE, (byte) 1);
        builder.define(HIDDEN, false);
        builder.define(TARGET_ID, -1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public byte state() { return entityData.get(STATE); }
    public int phase() { return entityData.get(PHASE); }
    /** In its whiteout: hidden in the steam, only its glow shows. */
    public boolean hidden() { return entityData.get(HIDDEN); }

    /** Client: an attack animation is playing (no walking legs then). */
    public boolean isAttacking() {
        return clientAttackTicks > 0 || state() == TRANSFORM;
    }

    // ------------------------------------------------------------------ client

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STATE.equals(key) && level().isClientSide) {
            if (state() == TRANSFORM) transformAnimation.start(tickCount);
            else transformAnimation.stop();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        int index = (id & 0xFF) - (EVENT_ATTACK & 0xFF);
        if (index > 0 && index < Attack.values().length) {
            Attack a = Attack.values()[index];
            for (AnimationState s : new AnimationState[] {poundAnimation, harpoonAnimation, gearsAnimation, geysersAnimation, sweepAnimation, rushAnimation}) s.stop();
            AnimationState play = switch (a) {
                case POUND -> poundAnimation;
                case HARPOON -> harpoonAnimation;
                case GEARS -> gearsAnimation;
                case GEYSERS -> geysersAnimation;
                case SWEEP -> sweepAnimation;
                default -> rushAnimation;
            };
            play.start(tickCount);
            clientAttackTicks = a == Attack.RUSH || a == Attack.WHITEOUT ? 26 : a.length;
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) return;
        if (isDeadOrDying()) {
            deathAnimation.startIfStopped(tickCount);
            idleAnimation.stop();
            return;
        }
        idleAnimation.startIfStopped(tickCount);
        if (clientAttackTicks > 0) clientAttackTicks--;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(4.0, 2.0, 4.0); // the arms, the chimneys
    }

    // ------------------------------------------------------------------ where its parts are (model pixels -> world)

    /** A point of the model (pixels; -x is its right, -z its front, the ground at y 24) in the world, the mech turned to yaw. */
    private Vec3 at(double mx, double my, double mz, float yaw) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        double right = -mx * PX, forward = -mz * PX, up = (24 - my) * PX;
        // its right hand side, facing (fx, fz), is (-fz, fx)
        return new Vec3(getX() + fx * forward - fz * right, getY() + up, getZ() + fz * forward + fx * right);
    }

    private Vec3 at(double mx, double my, double mz) {
        return at(mx, my, mz, getYRot());
    }

    // ------------------------------------------------------------------ server

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (tickCount % 10 == 0) updateBossBar(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        tickGears(level);
        breathe(level);
        if (state() == TRANSFORM) {
            transformTick(level);
            return;
        }
        fight(level);
    }

    /** Smoke from the chimneys in phase 1; in phase 2 steam everywhere and the air round it shimmering with heat. */
    private void breathe(ServerLevel level) {
        if (phase() == 1) {
            if (tickCount % 4 == 0) {
                for (double side : new double[] {-6.5, 6.5}) {
                    Vec3 c = at(side, -46.5, 14);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 0, 0, 0.12, 0, 1.0);
                }
            }
            return;
        }
        for (double side : new double[] {-6.5, 6.5}) {
            Vec3 c = at(side, -46.5, 14);
            level.sendParticles(ModParticles.STEAM.get(), c.x, c.y, c.z, 0, (random.nextDouble() - 0.5) * 0.05, 0.3, (random.nextDouble() - 0.5) * 0.05, 1.0);
        }
        if (tickCount % 3 == 0) {
            for (int side : new int[] {-12, 12}) {   // the shoulder vents
                Vec3 v = at(side, -29.5, 3);
                level.sendParticles(ModParticles.STEAM.get(), v.x, v.y, v.z, 0, side * 0.006, 0.18, 0, 1.0);
            }
            Vec3 knee = at(random.nextBoolean() ? -8 : 8, 7, -4);   // leaking joints
            level.sendParticles(ModParticles.STEAM.get(), knee.x, knee.y, knee.z, 0, 0, 0.05, 0, 1.0);
        }
        if (tickCount % 40 == 0) {
            ScreenFx.playOnNear(this, 48, Anchor.AREA, 6.5F, ScreenEffect.HEAT, 50, 0.8F, -1);
        }
    }

    private void updateBossBar(ServerLevel level) {
        for (ServerPlayer p : List.copyOf(bossBar.getPlayers())) {
            if (!p.isAlive() || p.level() != level || p.distanceToSqr(this) > 64 * 64) bossBar.removePlayer(p);
        }
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 48 * 48) bossBar.addPlayer(p);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel) {
            bossBar.removeAllPlayers();
            Telegraphs.cancel(this);
            clearProps();
        }
        super.remove(reason);
    }

    private void clearProps() {
        for (Gear g : gears) g.shown.discard();
        gears.clear();
        if (harpoon != null) harpoon.discard();
        harpoon = null;
        hooked.clear();
    }

    private boolean validTarget(@Nullable LivingEntity t) {
        return t != null && t.isAlive() && t.level() == level() && t.distanceToSqr(this) < AGGRO_RANGE * AGGRO_RANGE
                && !(t instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private List<Player> fighters(ServerLevel level, double range) {
        List<Player> out = new ArrayList<>();
        for (Player p : level.players()) {
            if (!p.isCreative() && !p.isSpectator() && p.isAlive() && p.distanceToSqr(this) < range * range) out.add(p);
        }
        return out;
    }

    // ------------------------------------------------------------------ the fight

    private void fight(ServerLevel level) {
        if (attack == Attack.NONE || !validTarget(getTarget())) setTarget(targeting.pick(this, position(), AGGRO_RANGE, getTarget()));
        LivingEntity target = getTarget();
        if (attack != Attack.NONE) {
            attackTick(level, target);
            return;
        }
        if (target == null) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(target, 20, 20);
        double dist = Math.sqrt(distanceToSqr(target.getX(), getY(), target.getZ()));
        if (cooldown > 0) cooldown--;
        if (cooldown <= 0 && dist < 30) {
            Attack a = choose(level, dist);
            if (a != Attack.NONE) {
                start(level, a, target);
                return;
            }
        }
        if (dist > 7) {
            if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
        }
    }

    private Attack choose(ServerLevel level, double dist) {
        List<Attack> options = new ArrayList<>();
        if (phase() == 1) {
            if (dist <= 9) options.add(Attack.POUND);
            if (dist >= 5 && dist <= 24) options.add(Attack.HARPOON);
            if (dist <= 20) options.add(Attack.GEARS);
        } else {
            options.add(Attack.GEYSERS);
            if (dist <= 16) options.add(Attack.SWEEP);
            if (dist >= 6) options.add(Attack.RUSH);
            if (last != Attack.WHITEOUT && random.nextFloat() < 0.4F) options.add(Attack.WHITEOUT);
        }
        if (options.size() > 1) options.remove(last);
        return options.isEmpty() ? Attack.NONE : options.get(random.nextInt(options.size()));
    }

    /** /bossattack: starts an attack by name now, if it can (awake, in the right phase, nothing under way). */
    public boolean forceAttack(String name) {
        if (!(level() instanceof ServerLevel level) || state() != FIGHT || attack != Attack.NONE || !isAlive()) return false;
        Attack a;
        try {
            a = Attack.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (a == Attack.NONE) return false;
        LivingEntity target = getTarget() != null ? getTarget() : targeting.pick(this, position(), AGGRO_RANGE, null);
        if (target == null) return false;
        setTarget(target);
        start(level, a, target);
        return true;
    }

    private Vec3 forward() {
        return new Vec3(-Mth.sin(lockedYaw * Mth.DEG_TO_RAD), 0, Mth.cos(lockedYaw * Mth.DEG_TO_RAD));
    }

    private void lockYaw() {
        setYRot(lockedYaw);
        yRotO = lockedYaw;
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
    }

    private void faceTo(Vec3 p) {
        lockedYaw = (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        lockYaw();
    }

    private void start(ServerLevel level, Attack a, LivingEntity target) {
        attack = a;
        last = a;
        attackTick = 0;
        getNavigation().stop();
        faceTo(target.position());
        aim = target.position();
        level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + a.ordinal()));
        switch (a) {
            case POUND -> startPound(level);
            case HARPOON -> startHarpoon(level, target);
            case GEARS -> startGears(level);
            case GEYSERS -> startGeysers(level);
            case SWEEP -> startSweep(level);
            case RUSH -> rush(level, target);
            case WHITEOUT -> startWhiteout(level);
            default -> {}
        }
    }

    private void end() {
        attack = Attack.NONE;
        cooldown = phase() == 1 ? 30 : 22;
        if (hidden()) entityData.set(HIDDEN, false);
    }

    private void attackTick(ServerLevel level, @Nullable LivingEntity target) {
        attackTick++;
        if (attack != Attack.SWEEP) lockYaw();
        if (attack != Attack.RUSH && attack != Attack.WHITEOUT) setDeltaMovement(0, getDeltaMovement().y, 0);
        switch (attack) {
            case POUND -> poundTick(level);
            case HARPOON -> harpoonTick(level);
            case GEARS -> gearsTick(level);
            case GEYSERS -> geysersTick(level);
            case SWEEP -> sweepTick(level);
            case RUSH -> rushTick(level, target);
            case WHITEOUT -> whiteoutTick(level);
            default -> {}
        }
        if (attackTick >= attack.length) end();
    }

    // ---- phase 1: the rivet spiral

    private Vec3 spiral(int i) {
        float angle = lockedYaw * Mth.DEG_TO_RAD + i * 0.7F;
        double r = 3.0 + i * 0.72;
        return new Vec3(getX() - Mth.sin(angle) * r, getY(), getZ() + Mth.cos(angle) * r);
    }

    private void startPound(ServerLevel level) {
        for (int i = 0; i < RIVETS; i++) {
            Telegraphs.show(level, Telegraph.circle(spiral(i), 1.5F), 18 + i * 2, 4, this, e -> hit(e, RIVET_DAMAGE, 0.3, 0.7));
        }
        playSound(SoundEvents.PISTON_EXTEND, 3.0F, 0.5F);
    }

    private void poundTick(ServerLevel level) {
        if (attackTick == 18) {   // the hammer hits the ground
            Vec3 h = at(-20, 24, -16);
            playSound(SoundEvents.ANVIL_LAND, 3.0F, 0.5F);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.8F);
            ScreenFx.shake(level, position(), 28, 12, 0.7F);
            level.sendParticles(ParticleTypes.EXPLOSION, h.x, h.y + 0.3, h.z, 1, 0, 0, 0, 0);
            ground(level, h, 30, 1.2);
        }
        int i = (attackTick - 18) / 2;
        if (attackTick >= 18 && (attackTick - 18) % 2 == 0 && i < RIVETS) {   // a rivet bursts up
            Vec3 p = spiral(i);
            ground(level, p, 12, 0.5);
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y + 0.5, p.z, 10, 0.3, 0.5, 0.3, 0.4);
            level.sendParticles(ParticleTypes.SMOKE, p.x, p.y + 0.3, p.z, 4, 0.2, 0.2, 0.2, 0.02);
            level.playSound(null, p.x, p.y, p.z, SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.5F, 0.6F + i * 0.04F);
        }
    }

    // ---- phase 1: the harpoon

    private Vec3 muzzle() {
        return at(20.5, 4.5, -24);
    }

    private void startHarpoon(ServerLevel level, LivingEntity target) {
        hooked.clear();
        Vec3 dir = forward();
        double reach = Mth.clamp(Math.sqrt(distanceToSqr(target.getX(), getY(), target.getZ())) + 2, 6, 24);
        Vec3 from = position().add(dir.scale(1.5));
        Telegraphs.show(level, Telegraph.rect(from, lockedYaw, (float) reach, 1.0F), 12, 4, this, e -> {
            hit(e, HARPOON_DAMAGE, 0, 0);
            if (e instanceof Player p) hooked.add(p);
        });
        // the hammer's blow on the ground before it, where the caught are reeled to
        Telegraphs.show(level, Telegraph.sector(position(), lockedYaw, 7.0F, 45), 40, 5, this, e -> hit(e, SMASH_DAMAGE, 1.2, 0.6));
        playSound(SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 3.0F, 0.5F);
        aim = from.add(dir.scale(reach));
    }

    private void harpoonTick(ServerLevel level) {
        Vec3 reelTo = position().add(forward().scale(4.5));
        if (attackTick == 12) {   // fired
            playSound(SoundEvents.CROSSBOW_SHOOT, 3.0F, 0.5F);
            playSound(SoundEvents.PISTON_CONTRACT, 2.0F, 0.7F);
            Vec3 m = muzzle();
            level.sendParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, 8, 0.2, 0.2, 0.2, 0.05);
            harpoon = spawnProp(level, new ItemStack(BossItems.EVIL_BROTHER_HARPOON.get()), m, 1.6F, lockedYaw, 0);
        }
        if (harpoon != null && attackTick > 12 && attackTick <= 33) {
            Vec3 m = muzzle();
            Vec3 tip;
            if (attackTick <= 17) {
                tip = m.lerp(aim.add(0, 1, 0), (attackTick - 12) / 5.0);   // flying out
            } else if (!hooked.isEmpty()) {
                tip = hooked.get(0).position().add(0, 1, 0);               // in its catch
            } else {
                tip = aim.add(0, 1, 0).lerp(m, (attackTick - 17) / 16.0);  // drawn back empty
            }
            harpoon.setPos(tip);
            // the chain between the cannon and the harpoon
            if (attackTick % 2 == 0) {
                ItemParticleOption chain = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.CHAIN));
                int links = (int) Math.max(2, m.distanceTo(tip) * 1.5);
                for (int i = 0; i <= links; i++) {
                    Vec3 p = m.lerp(tip, i / (double) links);
                    level.sendParticles(chain, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
            }
        }
        if (attackTick > 17 && attackTick <= 32) {   // reeling in
            if (attackTick % 4 == 0) playSound(SoundEvents.CHAIN_STEP, 2.0F, 0.6F);
            for (Player p : hooked) {
                if (!p.isAlive()) continue;
                Vec3 pull = reelTo.subtract(p.position());
                if (pull.horizontalDistance() > 1.0) {
                    Vec3 v = pull.normalize().scale(0.75);
                    p.setDeltaMovement(v.x, Math.max(0.05, v.y), v.z);
                    p.hurtMarked = true;
                }
            }
        }
        if (attackTick == 33 && harpoon != null) {
            harpoon.discard();
            harpoon = null;
        }
        if (attackTick == 40) {   // the hammer
            Vec3 h = at(-20, 24, -18);
            playSound(SoundEvents.ANVIL_LAND, 3.0F, 0.4F);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.7F);
            ScreenFx.shake(level, position(), 24, 12, 0.7F);
            level.sendParticles(ParticleTypes.EXPLOSION, h.x, h.y + 0.3, h.z, 2, 0.6, 0.2, 0.6, 0);
            ground(level, h, 30, 1.5);
            hooked.clear();
        }
    }

    // ---- phase 1: the gear saws

    private void startGears(ServerLevel level) {
        for (int i = -1; i <= 1; i++) {
            float yaw = lockedYaw + i * 35;
            Telegraphs.show(level, Telegraph.rect(position(), yaw, GEAR_RANGE, 1.2F), 14, GEAR_OUT * 2, this, null);
        }
        playSound(SoundEvents.GRINDSTONE_USE, 3.0F, 0.5F);
    }

    private void gearsTick(ServerLevel level) {
        if (attackTick != 14) return;
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.5F);
        playSound(SoundEvents.GRINDSTONE_USE, 3.0F, 0.8F);
        for (int i = -1; i <= 1; i++) {
            float yaw = lockedYaw + i * 35;
            Vec3 dir = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
            Vec3 from = position().add(dir.scale(2)).add(0, 1.1, 0);
            Vec3 to = position().add(dir.scale(GEAR_RANGE)).add(0, 1.1, 0);
            gears.add(new Gear(spawnProp(level, new ItemStack(BossItems.EVIL_BROTHER_GEAR.get()), from, 2.4F, yaw + 90, 0), from, to));
        }
    }

    /** The gears roll out GEAR_OUT ticks and back as long, spinning, cutting whoever they pass (once each way). */
    private void tickGears(ServerLevel level) {
        for (Iterator<Gear> it = gears.iterator(); it.hasNext(); ) {
            Gear g = it.next();
            int age = g.shown.tickCount;
            if (age > GEAR_OUT * 2 || !isAlive()) {
                g.shown.discard();
                it.remove();
                continue;
            }
            boolean back = age > GEAR_OUT;
            if (back != g.back) {
                g.back = true;
                g.cut.clear();
                level.playSound(null, g.to.x, g.to.y, g.to.z, SoundEvents.GRINDSTONE_USE, SoundSource.HOSTILE, 1.5F, 1.2F);
            }
            double t = back ? 1 - (age - GEAR_OUT) / (double) GEAR_OUT : age / (double) GEAR_OUT;
            Vec3 p = g.from.lerp(g.to, t);
            g.shown.setPos(p);
            spin(g.shown, age * 0.9F);
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y - 1.0, p.z, 2, 0.3, 0.05, 0.3, 0.1);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.4, 1.5, 1.4),
                    e -> e != this && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator())))) {
                if (g.cut.add(e)) hit(e, GEAR_DAMAGE, 0.5, 0.3);
            }
        }
    }

    // ---- the transformation

    private void startTransform(ServerLevel level) {
        Telegraphs.cancel(this);
        clearProps();
        attack = Attack.NONE;
        transformTick = 0;
        getNavigation().stop();
        entityData.set(HIDDEN, false);
        entityData.set(STATE, TRANSFORM);
        playSound(SoundEvents.IRON_DOOR_CLOSE, 3.0F, 0.5F);
        playSound(SoundEvents.PISTON_CONTRACT, 3.0F, 0.4F);
    }

    private void transformTick(ServerLevel level) {
        transformTick++;
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (transformTick == 12) playSound(SoundEvents.LEVER_CLICK, 3.0F, 0.6F);       // the dwarf throws the levers
        if (transformTick == 16) playSound(SoundEvents.LEVER_CLICK, 3.0F, 0.5F);
        if (transformTick >= 28 && transformTick < 62) {                                  // the boiler shudders
            if (transformTick % 4 == 0) {
                playSound(SoundEvents.IRON_GOLEM_DAMAGE, 2.0F, 0.4F + transformTick * 0.006F);
                ScreenFx.shake(level, position(), 32, 4, 0.2F + transformTick * 0.006F);
                Vec3 b = at(random.nextIntBetweenInclusive(-8, 8), -20, 12);
                level.sendParticles(ModParticles.STEAM.get(), b.x, b.y, b.z, 0, (random.nextDouble() - 0.5) * 0.3, 0.1, (random.nextDouble() - 0.5) * 0.3, 1.0);
            }
            if (transformTick % 10 == 0) playSound(SoundEvents.FIRECHARGE_USE, 2.0F, 0.5F);
        }
        if (transformTick == 64) {   // the chimneys blow
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 1.2F);
            playSound(SoundEvents.BLAZE_SHOOT, 3.0F, 0.4F);
            ScreenFx.shake(level, position(), 40, 20, 0.9F);
            ScreenFx.playNear(level, position(), 40, ScreenEffect.FLASH, 8, 0.45F, 0xFFFFFF);
            for (double side : new double[] {-6.5, 6.5}) {
                Vec3 c = at(side, -46.5, 14);
                for (int i = 0; i < 30; i++) {
                    level.sendParticles(ModParticles.STEAM.get(), c.x, c.y, c.z, 0, (random.nextDouble() - 0.5) * 0.5, 0.5 + random.nextDouble() * 0.6,
                            (random.nextDouble() - 0.5) * 0.5, 1.0);
                }
            }
            steamRing(level, position(), 4, 40);
        }
        if (transformTick >= 64 && transformTick % 6 == 0) steamRing(level, position(), 2.5, 10);
        if (transformTick == 82) {   // it rears up roaring
            playSound(SoundEvents.RAVAGER_ROAR, 4.0F, 0.5F);
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 0.6F);
            ScreenFx.playOnNear(this, 48, Anchor.AREA, 9, ScreenEffect.HEAT, 80, 1.0F, -1);
            ScreenFx.playNear(level, position(), 40, ScreenEffect.ABERRATION, 20, 0.5F, -1);
        }
        if (transformTick >= TRANSFORM_TICKS) {
            entityData.set(PHASE, (byte) 2);
            entityData.set(STATE, FIGHT);
            cooldown = 20;
            last = Attack.NONE;
        }
    }

    // ---- phase 2: scalding geysers

    private static final int GEYSER_CELL = 4, GEYSER_FIRST = 24, GEYSER_SECOND_WARN = 16;
    private final List<Vec3> geyserCells = new ArrayList<>(), geysersA = new ArrayList<>(), geysersB = new ArrayList<>();

    /** The squares the geysers may burst from (4 blocks across, up to 19 blocks out, under it too), a random half of them first. */
    private void startGeysers(ServerLevel level) {
        geyserCells.clear();
        geysersA.clear();
        geysersB.clear();
        for (int i = -5; i <= 5; i++) {
            for (int j = -5; j <= 5; j++) {
                double cx = Math.floor(getX() / GEYSER_CELL) * GEYSER_CELL + i * GEYSER_CELL + GEYSER_CELL / 2.0;
                double cz = Math.floor(getZ() / GEYSER_CELL) * GEYSER_CELL + j * GEYSER_CELL + GEYSER_CELL / 2.0;
                double d = Math.hypot(cx - getX(), cz - getZ());
                // under the mech too: its own geysers never hurt it (Telegraphs spares their owner)
                if (d <= 19) geyserCells.add(new Vec3(cx, getY(), cz));
            }
        }
        warnGeysers(level, geysersA, GEYSER_FIRST);
        playSound(SoundEvents.PISTON_EXTEND, 3.0F, 0.4F);
    }

    /** Picks a random half of the squares (no two runs alike), shows them in red and has them burst after warn ticks. */
    private void warnGeysers(ServerLevel level, List<Vec3> into, int warn) {
        into.clear();
        for (Vec3 c : geyserCells) if (random.nextBoolean()) into.add(c);
        if (into.isEmpty() && !geyserCells.isEmpty()) into.add(geyserCells.get(random.nextInt(geyserCells.size())));
        for (Vec3 c : into) {
            Telegraphs.show(level, Telegraph.rect(c.add(0, 0, -GEYSER_CELL / 2.0), 0, GEYSER_CELL, GEYSER_CELL / 2.0F), warn, 6, this,
                    e -> hit(e, GEYSER_DAMAGE, 0.2, 1.1));
        }
    }

    private void geysersTick(ServerLevel level) {
        if (attackTick == 16) {
            playSound(SoundEvents.ANVIL_LAND, 3.0F, 0.5F);
            ScreenFx.shake(level, position(), 28, 10, 0.6F);
            steamRing(level, position(), 3, 30);
        }
        int second = GEYSER_FIRST + GEYSER_SECOND_WARN;
        // the second set is chosen, and shown, only as the first bursts
        if (attackTick == GEYSER_FIRST) warnGeysers(level, geysersB, GEYSER_SECOND_WARN);
        for (int wave : new int[] {GEYSER_FIRST, second}) {
            List<Vec3> set = wave == GEYSER_FIRST ? geysersA : geysersB;
            if (attackTick >= wave && attackTick < wave + 10) {
                for (Vec3 c : set) geyser(level, c, attackTick == wave);
            }
            if (attackTick == wave) {
                playSound(SoundEvents.FIRE_EXTINGUISH, 3.0F, 0.5F);
                playSound(SoundEvents.BLAZE_SHOOT, 2.0F, 0.6F);
                ScreenFx.shake(level, position(), 28, 6, 0.35F);
            }
        }
    }

    private void geyser(ServerLevel level, Vec3 c, boolean first) {
        for (int i = 0; i < (first ? 6 : 2); i++) {
            level.sendParticles(ModParticles.STEAM.get(), c.x + (random.nextDouble() - 0.5) * 2.4, c.y + 0.1, c.z + (random.nextDouble() - 0.5) * 2.4,
                    0, 0, 0.6 + random.nextDouble() * 0.5, 0, 1.0);
        }
        if (first) level.sendParticles(ParticleTypes.SPLASH, c.x, c.y + 0.2, c.z, 12, 1.0, 0.1, 1.0, 0.3);
    }

    // ---- phase 2: the pressure release

    private static final int SWEEP_WARMUP = 25, SWEEP_ACTIVE = 80;
    private static final float SWEEP_SPIN = 360.0F / SWEEP_ACTIVE;

    private void startSweep(ServerLevel level) {
        Telegraphs.sweep(level, Telegraph.sector(position(), lockedYaw, 18, 20), SWEEP_WARMUP, SWEEP_ACTIVE, SWEEP_SPIN, 10, this,
                e -> hit(e, SWEEP_DAMAGE, 0.9, 0.2));
        playSound(SoundEvents.IRON_DOOR_OPEN, 3.0F, 0.5F);
    }

    private void sweepTick(ServerLevel level) {
        int t = attackTick - SWEEP_WARMUP;
        if (t < 0 || t >= SWEEP_ACTIVE) {
            lockYaw();
            return;
        }
        // the mech turns with its jet: the warning, the steam and the model agree
        float yaw = lockedYaw + SWEEP_SPIN * (t + 1);
        setYRot(yaw);
        yRotO = yaw - SWEEP_SPIN;
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        if (t % 5 == 0) playSound(SoundEvents.FIRE_EXTINGUISH, 2.5F, 0.4F + random.nextFloat() * 0.2F);
        Vec3 dir = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
        Vec3 from = at(0, -9, -11, yaw);
        for (int i = 0; i < 6; i++) {
            double spread = (random.nextDouble() - 0.5) * 0.5;
            Vec3 v = dir.yRot((float) spread).scale(0.9 + random.nextDouble() * 0.6);
            level.sendParticles(ModParticles.STEAM.get(), from.x, from.y - 1.5, from.z, 0, v.x, 0.02, v.z, 1.0);
        }
    }

    // ---- phase 2: the boiler rush

    private static final int RUSH_EVERY = 30, RUSH_WARN = 14, RUSH_MOVE = 8;
    private Vec3 rushFrom = Vec3.ZERO, rushTo = Vec3.ZERO;

    private void rush(ServerLevel level, @Nullable LivingEntity target) {
        Vec3 goal = target != null ? target.position() : position().add(forward().scale(12));
        faceTo(goal);
        double len = Mth.clamp(Math.sqrt(distanceToSqr(goal.x, getY(), goal.z)) + 4, 8, 22);
        rushFrom = position();
        rushTo = position().add(forward().scale(len));
        Telegraphs.show(level, Telegraph.rect(rushFrom, lockedYaw, (float) len, 2.0F), RUSH_WARN, 4, this, e -> hit(e, RUSH_DAMAGE, 1.3, 0.5));
        level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + Attack.RUSH.ordinal()));
        playSound(SoundEvents.PISTON_EXTEND, 3.0F, 0.6F);
    }

    private void rushTick(ServerLevel level, @Nullable LivingEntity target) {
        int t = attackTick % RUSH_EVERY;
        if (attackTick > 0 && t == 0 && attackTick < Attack.RUSH.length) {
            rush(level, validTarget(target) ? target : null);
            return;
        }
        if (t >= RUSH_WARN && t < RUSH_WARN + RUSH_MOVE) {
            if (t == RUSH_WARN) {
                playSound(SoundEvents.BLAZE_SHOOT, 3.0F, 0.5F);
                playSound(SoundEvents.FIRE_EXTINGUISH, 3.0F, 0.6F);
                // the trail it leaves: scalding steam for three seconds
                double len = rushFrom.distanceTo(rushTo);
                Telegraphs.sweep(level, Telegraph.rect(rushFrom, lockedYaw, (float) len, 1.6F), RUSH_MOVE, 60, 0, 10, this,
                        e -> hit(e, TRAIL_DAMAGE, 0, 0.1));
            }
            Vec3 step = rushTo.subtract(rushFrom).scale(1.0 / RUSH_MOVE);
            move(MoverType.SELF, new Vec3(step.x, 0, step.z));
            for (int side : new int[] {-8, 8}) {   // steam jets out of its feet
                Vec3 f = at(side, 23, 6);
                level.sendParticles(ModParticles.STEAM.get(), f.x, f.y, f.z, 0, -step.x * 0.15, 0.05, -step.z * 0.15, 1.0);
            }
        } else {
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        if (t > RUSH_WARN && t < RUSH_WARN + 60 && t % 4 == 0) {   // the trail steams
            for (int i = 0; i < 3; i++) {
                Vec3 p = rushFrom.lerp(rushTo, random.nextDouble());
                level.sendParticles(ModParticles.STEAM.get(), p.x, p.y + 0.1, p.z, 0, 0, 0.12, 0, 1.0);
            }
        }
    }

    // ---- phase 2: the whiteout

    private void startWhiteout(ServerLevel level) {
        playSound(SoundEvents.FIRE_EXTINGUISH, 4.0F, 0.3F);
        playSound(SoundEvents.IRON_DOOR_OPEN, 3.0F, 0.4F);
        for (Player p : fighters(level, 48)) {
            if (p instanceof ServerPlayer sp) {
                ScreenFx.play(sp, ScreenEffect.FOG, Attack.WHITEOUT.length + 10, 1.0F, 0xE4E8EC, "");
                ScreenFx.play(sp, ScreenEffect.DESATURATE, Attack.WHITEOUT.length + 10, 0.35F, -1, "");
            }
        }
        steamRing(level, position(), 5, 60);
    }

    private void whiteoutTick(ServerLevel level) {
        if (attackTick < 120 && attackTick % 2 == 0) steamRing(level, position(), 3 + random.nextDouble() * 8, 6);
        if (attackTick == 15) entityData.set(HIDDEN, true);
        for (int charge : new int[] {30, 60, 90}) {
            int t = attackTick - charge;
            if (t == 0) {
                List<Player> all = fighters(level, 40);
                if (all.isEmpty()) continue;
                Player victim = all.get(random.nextInt(all.size()));
                float angle = random.nextFloat() * Mth.TWO_PI;
                Vec3 out = victim.position().add(Math.cos(angle) * 11, 0, Math.sin(angle) * 11);
                BlockPos ground = BlockPos.containing(out.x, victim.getY() + 2, out.z);
                for (int down = 0; down < 6 && level.getBlockState(ground.below()).isAir(); down++) ground = ground.below();
                teleportTo(out.x, ground.getY(), out.z);
                faceTo(victim.position());
                rushFrom = position();
                rushTo = position().add(forward().scale(22));
                Telegraphs.show(level, Telegraph.rect(rushFrom, lockedYaw, 22, 2.0F), 12, 4, this, e -> hit(e, CHARGE_DAMAGE, 1.4, 0.6));
                level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + Attack.WHITEOUT.ordinal()));
                playSound(SoundEvents.PISTON_EXTEND, 3.0F, 0.4F);
                steamRing(level, position(), 2, 20);
            }
            if (t >= 12 && t < 22) {
                Vec3 step = rushTo.subtract(rushFrom).scale(0.1);
                move(MoverType.SELF, new Vec3(step.x, 0, step.z));
                if (t == 12) playSound(SoundEvents.BLAZE_SHOOT, 3.0F, 0.5F);
            }
        }
        if (attackTick == 120) {
            entityData.set(HIDDEN, false);
            steamRing(level, position(), 4, 50);
            playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.6F);
        }
    }

    // ------------------------------------------------------------------ death: the engine overheats and bursts

    @Override
    protected void tickDeath() {
        dying++;
        int deathTime = dying;
        if (!(level() instanceof ServerLevel level)) return;
        setDeltaMovement(0, getDeltaMovement().y, 0);
        float heat = deathTime / (float) DEATH_TICKS;
        if (deathTime == 1) {
            Telegraphs.cancel(this);
            clearProps();
            bossBar.removeAllPlayers();
            playSound(SoundEvents.IRON_GOLEM_DEATH, 4.0F, 0.5F);
        }
        // steam bursting out ever harder, the boiler groaning, the ground shaking
        int puffs = 1 + (int) (heat * 8);
        for (int i = 0; i < puffs; i++) {
            Vec3 b = at(random.nextIntBetweenInclusive(-12, 12), -12 - random.nextInt(18), random.nextIntBetweenInclusive(-4, 16));
            level.sendParticles(ModParticles.STEAM.get(), b.x, b.y, b.z, 0, (random.nextDouble() - 0.5) * 0.6, 0.2 + random.nextDouble() * 0.4,
                    (random.nextDouble() - 0.5) * 0.6, 1.0);
        }
        if (deathTime % 10 == 0) {
            playSound(SoundEvents.FIRE_EXTINGUISH, 3.0F, 0.4F + heat * 0.8F);
            ScreenFx.shake(level, position(), 36, 10, 0.2F + heat * 0.7F);
        }
        if (deathTime % 6 == 0) {
            Vec3 b = at(0, -20, 12);
            level.sendParticles(ParticleTypes.LAVA, b.x, b.y, b.z, (int) (heat * 6), 0.8, 0.8, 0.8, 0);
            level.sendParticles(ParticleTypes.FLAME, b.x, b.y, b.z, (int) (heat * 10), 1.0, 1.0, 1.0, 0.02);
        }
        if (deathTime == 40) playSound(SoundEvents.BELL_BLOCK, 3.0F, 0.5F);   // the alarm
        if (deathTime == 60) playSound(SoundEvents.BELL_BLOCK, 3.0F, 0.6F);
        if (deathTime == 75) playSound(SoundEvents.BELL_BLOCK, 3.0F, 0.7F);
        if (deathTime == 30) ScreenFx.playOnNear(this, 48, Anchor.AREA, 10, ScreenEffect.HEAT, DEATH_TICKS, 1.0F, -1);
        if (deathTime == DEATH_TICKS - 6) ScreenFx.playNear(level, position(), 48, ScreenEffect.FLASH, 14, 0.9F, 0xFFF2D0);
        if (deathTime >= DEATH_TICKS) {
            Vec3 b = at(0, -18, 10);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, b.x, b.y, b.z, 2, 1.0, 1.0, 1.0, 0);
            level.sendParticles(ParticleTypes.EXPLOSION, b.x, b.y, b.z, 20, 2.5, 2.5, 2.5, 0);
            level.sendParticles(ParticleTypes.LAVA, b.x, b.y, b.z, 40, 2.0, 2.0, 2.0, 0);
            for (int i = 0; i < 80; i++) {
                level.sendParticles(ModParticles.STEAM.get(), b.x, b.y, b.z, 0, (random.nextDouble() - 0.5) * 1.6, random.nextDouble() * 1.2,
                        (random.nextDouble() - 0.5) * 1.6, 1.0);
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 5.0F, 0.6F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.7F);
            ScreenFx.shake(level, position(), 56, 30, 1.0F);
            ScreenFx.playNear(level, position(), 48, ScreenEffect.ABERRATION, 25, 0.8F, -1);
            remove(RemovalReason.KILLED);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Damage multiplier from the ATTACK_DAMAGE attribute (e.g. dungeon.DungeonScaling for bigger parties). */
    private float damageScale() {
        var attr = getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null || attr.getBaseValue() <= 0 ? 1.0F : (float) (attr.getValue() / attr.getBaseValue());
    }

    /** A hit from one of the attacks: damage, then a shove away from the mech and up. */
    private void hit(LivingEntity e, float damage, double push, double up) {
        if (!e.hurt(damageSources().mobAttack(this), damage * damageScale())) return;
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0) return;
        e.setDeltaMovement(e.getDeltaMovement().add(dx / len * push * resist, up * resist, dz / len * push * resist));
        e.hurtMarked = true;
    }

    /** Dust of the ground thrown up round a point. */
    private void ground(ServerLevel level, Vec3 at, int count, double spread) {
        BlockState under = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (!under.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), at.x, at.y + 0.2, at.z, count, spread, 0.2, spread, 0.15);
    }

    /** A ring of steam rolling out over the ground. */
    private void steamRing(ServerLevel level, Vec3 c, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double x = c.x + Math.cos(a) * radius, z = c.z + Math.sin(a) * radius;
            level.sendParticles(ModParticles.STEAM.get(), x, c.y + 0.3, z, 0, Math.cos(a) * 0.25, 0.04, Math.sin(a) * 0.25, 1.0);
        }
    }

    /** A prop shown by an item display (the harpoon, a gear), moved smoothly by the client. */
    private Display.ItemDisplay spawnProp(ServerLevel level, ItemStack stack, Vec3 at, float size, float yaw, float roll) {
        Display.ItemDisplay prop = EntityType.ITEM_DISPLAY.create(level);
        CompoundTag tag = new CompoundTag();
        tag.put("item", stack.save(level.registryAccess()));
        tag.putString("item_display", "none");
        tag.put("transformation", transform(size, roll));
        tag.putInt("teleport_duration", 1);
        tag.putFloat("view_range", 2.0F);
        prop.load(tag);
        prop.moveTo(at.x, at.y, at.z, yaw, 0);
        level.addFreshEntity(prop);
        return prop;
    }

    private static CompoundTag transform(float size, float roll) {
        CompoundTag tr = new CompoundTag();
        tr.put("translation", floats(0, 0, 0));
        tr.put("scale", floats(size, size, size));
        tr.put("left_rotation", floats(0, 0, Mth.sin(roll / 2), Mth.cos(roll / 2)));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        return tr;
    }

    /** Turns a gear about its axle (its own z), the client easing between the steps. */
    private static void spin(Display.ItemDisplay prop, float angle) {
        CompoundTag tag = new CompoundTag();
        tag.put("transformation", transform(2.4F, angle));
        tag.putInt("start_interpolation", 0);
        tag.putInt("interpolation_duration", 1);
        prop.load(prop.saveWithoutId(new CompoundTag()).merge(tag));
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(FloatTag.valueOf(v));
        return list;
    }

    // ------------------------------------------------------------------ the rest

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (!level().isClientSide) entityData.set(TARGET_ID, target == null ? -1 : target.getId());
    }

    @Override
    public int markedTargetId() {
        return !isAlive() ? -1 : entityData.get(TARGET_ID);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (state() == TRANSFORM) return false;   // the engine starting: nothing gets through
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) {
            targeting.onHurt(source, level().getGameTime());
            if (phase() == 1 && getHealth() <= getMaxHealth() * 0.5F && isAlive()) startTransform((ServerLevel) level());
        }
        return hurt;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return phase() == 2 ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.PISTON_CONTRACT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_DAMAGE;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;   // tickDeath plays its own
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.5F, 0.5F);
        playSound(SoundEvents.PISTON_EXTEND, 0.4F, 1.4F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Phase", (byte) phase());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Phase")) entityData.set(PHASE, (byte) Math.max(1, tag.getByte("Phase")));
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    @Override
    public void setCustomName(@Nullable net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }
}
