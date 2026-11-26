package com.divinis.boss;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.divinis.net.RiddlePayloads;
import com.divinis.registry.ModEntities;
import com.divinis.registry.ModParticles;
import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenEffect.Anchor;
import com.divinis.screenfx.ScreenFx;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Sphinx (/summon divinis:sphinx): a winged lion with a pharaoh's face, the guardian of the desert, fighting with
 * the sand itself - its tornadoes, its storms - and the light of the sun.
 *
 * Every quarter of his health lost (at 75, 50 and 25%) he stops, sits up as the statues sit and asks a riddle: everyone
 * near is shown it (client.RiddleScreen) with four answers and RIDDLE_TICKS to choose. The first right answer, from
 * anyone, and he bows his head and fights on; a wrong one silences only who gave it - but if everyone answers wrongly,
 * or the sand runs out, the desert takes them: every player within DOOM_RANGE dies. He cannot be hurt while he asks.
 *
 * Phase 1:
 * - Call of the desert: he rears up roaring and slams down; tornadoes of sand rise where his foes stand (the red
 *   circles) and hunt them at a walk, hurting whoever they catch (SandTornado);
 * - Lion's leap: he crouches a long moment and springs at a foe, landing in a burst of sand, the sand thrown out in a
 *   ring after it;
 * - Eye of Ra: he lifts his head, his eyes kindle, and a beam of sunlight sweeps from his right to his left.
 * After the riddle at half his health his body cracks to sandstone burning turquoise inside, and he rises in a storm.
 * Phase 2:
 * - Haboob: he beats his wings again and again - a sandstorm blinds everyone, the wind pushes them, and the sand
 *   erupts in spikes under their feet (the circles);
 * - Desert gale: three great beats of his wings, each blowing a cone of sand that throws whoever it catches far back;
 * - Sand vortex: he sits up roaring; six tornadoes circle out from him in a widening spiral while the wind draws
 *   everyone in towards him;
 * - Wrath of Ra: he rises into the sky on beating wings, and pillars of sunlight burn down where his foes stand, wave
 *   after wave; then he dives on one of them.
 * Dying he rears up screaming and spins into a tornado of sand that blows away into nothing (client.SphinxRenderer).
 * Timings match the animations in client/SphinxModelData (tools/models/build_sphinx.py).
 */
public class Sphinx extends Monster implements BossTargeting.Marked {
    public static final byte FIGHT = 0, RIDDLE = 1, TRANSFORM = 2;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Sphinx.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(Sphinx.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(Sphinx.class, EntityDataSerializers.INT);
    /** His eyes kindled (the eye of Ra). */
    private static final EntityDataAccessor<Boolean> GAZING = SynchedEntityData.defineId(Sphinx.class, EntityDataSerializers.BOOLEAN);

    public enum Attack {
        NONE(0), TORNADOES(40), POUNCE(52), GAZE(64), HABOOB(120), GALE(48), VORTEX(100), WRATH(100);

        final int length;

        Attack(int length) {
            this.length = length;
        }
    }

    private static final byte EVENT_ATTACK = (byte) 140;
    public static final int TRANSFORM_TICKS = 80, DEATH_TICKS = 120, RIDDLE_TICKS = 600;
    /** He turns into his tornado from here on in his death (client.SphinxRenderer). */
    public static final int DEATH_SPIN = 48;
    private static final double AGGRO_RANGE = 48, DOOM_RANGE = 64;
    private static final int SAND_COLOR = 0xC89A58, SUN_COLOR = 0xFFD070, TURQUOISE = 0x40F0D8;
    private static final DustParticleOptions GOLD_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.8F, 0.3F), 1.4F);
    private static final DustParticleOptions TURQUOISE_DUST = new DustParticleOptions(new Vector3f(0.25F, 0.95F, 0.85F), 1.3F);
    private static final BlockParticleOption SAND_BLOCK = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());

    // client: animations
    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState summonAnimation = new AnimationState();
    public final AnimationState pounceAnimation = new AnimationState();
    public final AnimationState gazeAnimation = new AnimationState();
    public final AnimationState beatAnimation = new AnimationState();
    public final AnimationState galeAnimation = new AnimationState();
    public final AnimationState roarAnimation = new AnimationState();
    public final AnimationState judgementAnimation = new AnimationState();
    public final AnimationState riddleAnimation = new AnimationState();
    public final AnimationState transformAnimation = new AnimationState();
    public final AnimationState deathAnimation = new AnimationState();
    private int clientAttackTicks;
    /** Ticks since he began dying (both sides; his own count, so vanilla neither tips nor reddens him). */
    public int dying;

    // server
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
    private final BossTargeting targeting = new BossTargeting();
    private Attack attack = Attack.NONE, last = Attack.NONE;
    private int attackTick, cooldown = 40, transformTick;
    private float lockedYaw;
    @Nullable private Player victim;
    private final List<SandTornado> tornadoes = new ArrayList<>();
    private final List<Vec3> spots = new ArrayList<>();
    private Vec3 from = Vec3.ZERO, land = Vec3.ZERO, wind = Vec3.ZERO;
    private double hoverY;
    private int beamId;
    private float gazeYaw;

    private record Strike(int at, Vec3 where) {}

    private final List<Strike> strikes = new ArrayList<>();

    // the riddles
    /** How many he has asked (one at each quarter of his health lost); which ones, so none comes twice. */
    private int riddles;
    private final List<Integer> askedRiddles = new ArrayList<>();
    @Nullable private Riddles.Asked riddle;
    private final Set<UUID> askedPlayers = new HashSet<>(), wrong = new HashSet<>();
    private int riddleTick, solvedTick, doomTick;

    public Sphinx(EntityType<? extends Sphinx> type, Level level) {
        super(type, level);
        this.xpReward = 400;
        setPersistenceRequired();
        bossBar.setVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 600.0)
                .add(Attributes.ARMOR, 16.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 18.0)
                .add(Attributes.STEP_HEIGHT, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, FIGHT);
        builder.define(PHASE, (byte) 1);
        builder.define(TARGET_ID, -1);
        builder.define(GAZING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public byte state() { return entityData.get(STATE); }
    public int phase() { return entityData.get(PHASE); }
    public boolean gazing() { return entityData.get(GAZING); }

    public boolean isAttacking() {
        return clientAttackTicks > 0 || state() != FIGHT;
    }

    // ------------------------------------------------------------------ client

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STATE.equals(key) && level().isClientSide) {
            if (state() == RIDDLE) riddleAnimation.startIfStopped(tickCount);
            else riddleAnimation.stop();
            if (state() == TRANSFORM) transformAnimation.start(tickCount);
            else transformAnimation.stop();
            if (state() != FIGHT) {
                for (AnimationState s : attackAnimations()) s.stop();
                clientAttackTicks = 0;
            }
        }
    }

    private AnimationState[] attackAnimations() {
        return new AnimationState[] {summonAnimation, pounceAnimation, gazeAnimation, beatAnimation, galeAnimation, roarAnimation, judgementAnimation};
    }

    @Override
    public void handleEntityEvent(byte id) {
        int index = (id & 0xFF) - (EVENT_ATTACK & 0xFF);
        if (index > 0 && index < Attack.values().length) {
            Attack a = Attack.values()[index];
            for (AnimationState s : attackAnimations()) s.stop();
            AnimationState play = switch (a) {
                case TORNADOES -> summonAnimation;
                case POUNCE -> pounceAnimation;
                case GAZE -> gazeAnimation;
                case HABOOB -> beatAnimation;
                case GALE -> galeAnimation;
                case VORTEX -> roarAnimation;
                default -> judgementAnimation;
            };
            play.start(tickCount);
            clientAttackTicks = a.length;
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
            riddleAnimation.stop();
            return;
        }
        idleAnimation.startIfStopped(tickCount);
        if (clientAttackTicks > 0) clientAttackTicks--;
        // phase 2: sand whirling round him, turquoise sparks from his cracks
        if (phase() == 2 && random.nextInt(2) == 0) {
            float a = random.nextFloat() * Mth.TWO_PI;
            double r = 2.2 + random.nextDouble();
            level().addParticle(ModParticles.SAND.get(), getX() + Mth.cos(a) * r, getY() + random.nextDouble() * 3, getZ() + Mth.sin(a) * r,
                    -Mth.sin(a) * 0.2, 0.02, Mth.cos(a) * 0.2);
        }
    }

    // ------------------------------------------------------------------ server

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (tickCount % 10 == 0) updateBossBar(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        tornadoes.removeIf(t -> !t.isAlive());
        if (phase() == 2 && tickCount % 6 == 0) {
            Vec3 c = getBoundingBox().getCenter();
            level.sendParticles(TURQUOISE_DUST, c.x, c.y, c.z, 1, 1.0, 1.0, 1.4, 0.0);
        }
        switch (state()) {
            case RIDDLE -> riddleTick(level);
            case TRANSFORM -> transformTick(level);
            default -> fight(level);
        }
    }

    private void updateBossBar(ServerLevel level) {
        for (ServerPlayer p : List.copyOf(bossBar.getPlayers())) {
            if (!p.isAlive() || p.level() != level || p.distanceToSqr(this) > 72 * 72) bossBar.removePlayer(p);
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
            clearTornadoes();
            closeRiddle();
        }
        super.remove(reason);
    }

    private boolean validTarget(@Nullable LivingEntity t) {
        return t != null && t.isAlive() && t.level() == level() && t.distanceToSqr(this) < AGGRO_RANGE * AGGRO_RANGE
                && !(t instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private List<Player> fighters(ServerLevel level) {
        List<Player> out = new ArrayList<>();
        for (Player p : level.players()) {
            if (validTarget(p)) out.add(p);
        }
        return out;
    }

    /** The nearest player he is fighting (for his tornadoes to hunt). */
    @Nullable
    Player nearestFighter(Vec3 at) {
        if (!(level() instanceof ServerLevel level)) return null;
        Player best = null;
        for (Player p : fighters(level)) {
            if (best == null || p.distanceToSqr(at) < best.distanceToSqr(at)) best = p;
        }
        return best;
    }

    private void fight(ServerLevel level) {
        if (attack == Attack.NONE || !validTarget(getTarget())) setTarget(targeting.pick(this, position(), AGGRO_RANGE, getTarget()));
        LivingEntity target = getTarget();
        if (attack != Attack.NONE) {
            attackTick(level);
            return;
        }
        if (target == null) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(target, 30, 30);
        double dist = Math.sqrt(distanceToSqr(target));
        if (cooldown > 0) cooldown--;
        if (cooldown <= 0 && dist < 32) {
            start(level, choose(dist), target);
            return;
        }
        if (dist > 7) {
            if (tickCount % 8 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
        }
    }

    private Attack choose(double dist) {
        List<Attack> options = new ArrayList<>(phase() == 1
                ? List.of(Attack.TORNADOES, Attack.POUNCE, Attack.GAZE)
                : List.of(Attack.HABOOB, Attack.GALE, Attack.VORTEX, Attack.WRATH));
        if (dist > 20) options.remove(Attack.GALE);
        if (options.size() > 1) options.remove(last);
        return options.get(random.nextInt(options.size()));
    }

    /** /bossattack: starts an attack by name now, if he can ("riddle" asks a riddle). */
    public boolean forceAttack(String name) {
        if (!(level() instanceof ServerLevel level) || state() != FIGHT || attack != Attack.NONE || !isAlive()) return false;
        if (name.equalsIgnoreCase("riddle")) {
            startRiddle(level);
            return true;
        }
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

    private Vec3 dirOf(float yaw) {
        return new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
    }

    private float yawTo(Vec3 p) {
        return (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private void face(float yaw) {
        lockedYaw = yaw;
        setYRot(yaw);
        yRotO = yaw;
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    /** His eyes, looking along yaw (the model drawn 1.5 times: about 3.3 blocks up, 2 ahead). */
    private Vec3 eyes(float yaw) {
        return position().add(dirOf(yaw).scale(2.0)).add(0, 3.3, 0);
    }

    private void start(ServerLevel level, Attack a, LivingEntity target) {
        attack = a;
        last = a;
        attackTick = 0;
        strikes.clear();
        spots.clear();
        getNavigation().stop();
        face(yawTo(target.position()));
        victim = target instanceof Player p ? p : null;
        level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + a.ordinal()));
        switch (a) {
            case TORNADOES -> startTornadoes(level);
            case POUNCE -> startPounce(level, target);
            case GAZE -> startGaze(level);
            case HABOOB -> startHaboob(level);
            case VORTEX -> playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.6F);
            case WRATH -> startWrath(level);
            default -> {}
        }
    }

    private void end() {
        attack = Attack.NONE;
        entityData.set(GAZING, false);
        cooldown = phase() == 1 ? 30 : 22;
        setNoGravity(false);
    }

    private void attackTick(ServerLevel level) {
        attackTick++;
        face(lockedYaw);
        if (attack != Attack.POUNCE && attack != Attack.WRATH) setDeltaMovement(0, getDeltaMovement().y, 0);
        switch (attack) {
            case TORNADOES -> tornadoesTick(level);
            case POUNCE -> pounceTick(level);
            case GAZE -> gazeTick(level);
            case HABOOB -> haboobTick(level);
            case GALE -> galeTick(level);
            case VORTEX -> vortexTick(level);
            case WRATH -> wrathTick(level);
            default -> {}
        }
        for (var it = strikes.iterator(); it.hasNext(); ) {
            Strike s = it.next();
            if (s.at() != attackTick) continue;
            it.remove();
            if (attack == Attack.WRATH) sunPillar(level, s.where());
            else eruption(level, s.where());
        }
        if (attackTick >= attack.length) end();
    }

    // ------------------------------------------------------------------ the sand and the sun

    private float damageScale() {
        var attr = getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null || attr.getBaseValue() <= 0 ? 1.0F : (float) (attr.getValue() / attr.getBaseValue());
    }

    private void hit(LivingEntity e, float damage, double push, double up) {
        if (!e.hurt(damageSources().mobAttack(this), damage * damageScale())) return;
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0) return;
        e.setDeltaMovement(e.getDeltaMovement().add(dx / len * push * resist, up * resist, dz / len * push * resist));
        e.hurtMarked = true;
    }

    /** Sand thrown up in a burst. */
    private void sandBurst(ServerLevel level, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(ModParticles.SAND.get(), at.x, at.y + 0.3, at.z, count, spread, 0.3, spread, speed);
        level.sendParticles(SAND_BLOCK, at.x, at.y + 0.5, at.z, count / 2, spread, 0.5, spread, 0.0);
    }

    /** A ring of sand rushing out over the ground. */
    private void sandRing(ServerLevel level, Vec3 at, double radius, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            double cx = Math.cos(a), cz = Math.sin(a);
            level.sendParticles(ModParticles.SAND.get(), at.x + cx * radius, at.y + 0.2, at.z + cz * radius, 0, cx, 0.08, cz, speed);
        }
    }

    /** The sand erupts here in a spike (warned before): it throws up whoever stands on it. */
    private void eruption(ServerLevel level, Vec3 at) {
        sandBurst(level, at, 24, 0.6, 0.12);
        for (int i = 0; i < 10; i++) level.sendParticles(ModParticles.SAND.get(), at.x, at.y + i * 0.4, at.z, 2, 0.2, 0.1, 0.2, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SAND_BREAK, getSoundSource(), 2.0F, 0.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, getSoundSource(), 0.8F, 0.5F);
    }

    /** A pillar of sunlight burns down here (warned before). */
    private void sunPillar(ServerLevel level, Vec3 at) {
        Beams.send(level, Beams.nextId(), com.divinis.net.BeamPayload.LIGHT, at.add(0, 26, 0), at.add(0, 0.1, 0), 1.8F, 8);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.2, at.z, 20, 1.2, 0.1, 1.2, 0.05);
        level.sendParticles(GOLD_DUST, at.x, at.y + 1, at.z, 20, 1.0, 1.0, 1.0, 0.0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, getSoundSource(), 2.0F, 0.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, getSoundSource(), 1.5F, 0.5F);
    }

    private void tornado(ServerLevel level, Vec3 at, java.util.function.Consumer<SandTornado> setup) {
        SandTornado t = ModEntities.SAND_TORNADO.get().create(level);
        if (t == null) return;
        t.setPos(at);
        setup.accept(t);
        level.addFreshEntity(t);
        tornadoes.add(t);
    }

    private void clearTornadoes() {
        for (SandTornado t : tornadoes) t.discard();
        tornadoes.clear();
    }

    // ---- phase 1: the call of the desert

    private void startTornadoes(ServerLevel level) {
        List<Player> all = fighters(level);
        for (int i = 0; i < Math.min(3, all.size()); i++) spots.add(all.get(i).position());
        while (spots.size() < 3) {
            double a = random.nextDouble() * Math.PI * 2, r = 6 + random.nextDouble() * 8;
            spots.add(position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
        }
        for (Vec3 s : spots) Telegraphs.show(level, Telegraph.circle(s, 2.5F), 20, 4, this, e -> hit(e, 8, 0.2, 0.6));
        playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.8F);
    }

    private void tornadoesTick(ServerLevel level) {
        if (attackTick == 20) {
            ScreenFx.shake(level, position(), 32, 10, 0.6F);
            sandRing(level, position(), 2, 40, 0.5);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.5F);
            for (Vec3 s : spots) {
                sandBurst(level, s, 30, 1.0, 0.1);
                tornado(level, s, t -> t.chase(this, null, 0.9F, 160, damageScale()));
            }
            playSound(SoundEvents.ELYTRA_FLYING, 3.0F, 0.5F);
        }
    }

    // ---- phase 1: the lion's leap

    /** He springs at POUNCE_JUMP and lands at POUNCE_LAND (as the pounce animation): two seconds to get away. */
    private static final int POUNCE_JUMP = 28, POUNCE_LAND = 40;

    private void startPounce(ServerLevel level, LivingEntity target) {
        from = position();
        land = target.position();
        Vec3 d = land.subtract(from);
        if (d.horizontalDistance() > 22) land = from.add(d.normalize().scale(22));
        Telegraphs.show(level, Telegraph.circle(land, 4.0F), POUNCE_LAND, 5, this, e -> hit(e, 16, 1.0, 0.5));
        Telegraphs.show(level, Telegraph.ring(land, 4.0F, 9.0F), POUNCE_LAND + 6, 4, this, e -> hit(e, 9, 0.8, 0.3));
        playSound(SoundEvents.RAVAGER_STUNNED, 2.0F, 0.6F);
    }

    private void pounceTick(ServerLevel level) {
        if (attackTick >= POUNCE_JUMP && attackTick < POUNCE_LAND) {
            setNoGravity(true);
            float p = (attackTick - POUNCE_JUMP + 1) / (float) (POUNCE_LAND - POUNCE_JUMP);
            Vec3 at = from.lerp(land, p).add(0, Mth.sin(p * Mth.PI) * 4.5, 0);
            setPos(at.x, at.y, at.z);
            setDeltaMovement(Vec3.ZERO);
            if (attackTick == POUNCE_JUMP) {
                playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 1.2F);
                sandBurst(level, from, 20, 1.0, 0.1);
            }
        }
        if (attackTick == POUNCE_LAND) {
            setPos(land.x, land.y, land.z);
            setNoGravity(false);
            ScreenFx.shake(level, position(), 28, 10, 0.7F);
            sandBurst(level, position(), 50, 2.0, 0.15);
            sandRing(level, position(), 1.5, 36, 0.4);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.7F);
        }
        if (attackTick == POUNCE_LAND + 6) {
            sandRing(level, position(), 4.0, 48, 0.6);
            playSound(SoundEvents.SAND_BREAK, 3.0F, 0.5F);
        }
    }

    // ---- phase 1: the eye of Ra

    private static final int GAZE_START = 20, GAZE_TICKS = 36;
    private static final float GAZE_ARC = 120, GAZE_LENGTH = 26;

    private void startGaze(ServerLevel level) {
        entityData.set(GAZING, true);
        beamId = Beams.nextId();
        gazeYaw = lockedYaw + GAZE_ARC / 2;   // from his right
        float spin = -GAZE_ARC / GAZE_TICKS;
        Telegraphs.sweep(level, Telegraph.rect(position(), gazeYaw, GAZE_LENGTH, 1.4F), GAZE_START, GAZE_TICKS, spin, 6, this, e -> {
            if (e.hurt(damageSources().mobAttack(this), 9 * damageScale())) e.igniteForSeconds(3);
        });
        playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 0.6F);
    }

    private void gazeTick(ServerLevel level) {
        int t = attackTick - GAZE_START;
        if (t < 0) {
            Vec3 e = eyes(lockedYaw);
            level.sendParticles(GOLD_DUST, e.x, e.y, e.z, 2, 0.3, 0.2, 0.3, 0.0);
            return;
        }
        if (t <= GAZE_TICKS) {
            float yaw = gazeYaw - GAZE_ARC / GAZE_TICKS * t;
            Vec3 to = position().add(dirOf(yaw).scale(GAZE_LENGTH)).add(0, 0.1, 0);
            Beams.send(level, beamId, com.divinis.net.BeamPayload.LIGHT, eyes(yaw), to, 1.1F, 3);
            level.sendParticles(ParticleTypes.FLAME, to.x, to.y, to.z, 3, 0.4, 0.1, 0.4, 0.02);
            for (int i = 4; i < GAZE_LENGTH; i += 5) {
                Vec3 g = position().add(dirOf(yaw).scale(i));
                level.sendParticles(ModParticles.SAND.get(), g.x, g.y + 0.2, g.z, 1, 0.3, 0.05, 0.3, 0.02);
            }
            if (t % 8 == 0) playSound(SoundEvents.BEACON_AMBIENT, 3.0F, 1.8F);
        }
        if (t == GAZE_TICKS + 4) entityData.set(GAZING, false);
    }

    // ---- phase 2: the haboob

    private void startHaboob(ServerLevel level) {
        double a = random.nextDouble() * Math.PI * 2;
        wind = new Vec3(Math.cos(a), 0, Math.sin(a));
        for (Player p : fighters(level)) {
            if (!(p instanceof ServerPlayer sp)) continue;
            ScreenFx.play(sp, ScreenEffect.SANDSTORM, 150, 1.0F, SAND_COLOR, "");
            ScreenFx.play(sp, ScreenEffect.FOG, 150, 0.85F, 0xB08850, "");
        }
        playSound(SoundEvents.ENDER_DRAGON_FLAP, 4.0F, 0.5F);
        playSound(SoundEvents.ELYTRA_FLYING, 4.0F, 0.4F);
    }

    private void haboobTick(ServerLevel level) {
        if (attackTick % 10 == 2) playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.6F);
        if (attackTick >= 10 && attackTick <= 110) {
            for (Player p : fighters(level)) {
                p.setDeltaMovement(p.getDeltaMovement().add(wind.scale(0.022)));
                p.hurtMarked = true;
                if (attackTick % 2 == 0) {
                    Vec3 up = p.position().subtract(wind.scale(6));
                    for (int i = 0; i < 4; i++) {
                        level.sendParticles(ModParticles.SAND.get(), up.x + (random.nextDouble() - 0.5) * 8, up.y + random.nextDouble() * 3,
                                up.z + (random.nextDouble() - 0.5) * 8, 0, wind.x, 0.0, wind.z, 0.6);
                    }
                }
            }
        }
        if (attackTick >= 20 && attackTick <= 100 && (attackTick - 20) % 14 == 0) {
            for (Player p : fighters(level)) {
                double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 1.5;
                Vec3 at = p.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                Telegraphs.show(level, Telegraph.circle(at, 2.4F), 16, 4, this, e -> hit(e, 12, 0.1, 0.9));
                strikes.add(new Strike(attackTick + 16, at));
            }
        }
    }

    // ---- phase 2: the desert gale

    private static final int[] GALE_BEATS = {14, 26, 38};
    private static final float[] GALE_TURN = {-40, 40, 0};

    private void galeTick(ServerLevel level) {
        for (int i = 0; i < GALE_BEATS.length; i++) {
            float yaw = lockedYaw + GALE_TURN[i];
            if (attackTick == GALE_BEATS[i] - 10) {
                Telegraphs.show(level, Telegraph.sector(position(), yaw, 15, 32), 10, 4, this, e -> hit(e, 11, 1.8, 0.45));
            }
            if (attackTick == GALE_BEATS[i]) {
                playSound(SoundEvents.ENDER_DRAGON_FLAP, 4.0F, 0.5F);
                playSound(SoundEvents.ELYTRA_FLYING, 3.0F, 0.8F);
                Vec3 o = position().add(0, 1.0, 0);
                for (int j = 0; j < 70; j++) {
                    float a = yaw + (random.nextFloat() - 0.5F) * 64;
                    Vec3 d = dirOf(a);
                    double r = 1.5 + random.nextDouble() * 2;
                    level.sendParticles(ModParticles.SAND.get(), o.x + d.x * r, o.y + random.nextDouble() * 1.5, o.z + d.z * r, 0,
                            d.x, 0.02, d.z, 0.9 + random.nextDouble() * 0.6);
                }
            }
        }
    }

    // ---- phase 2: the sand vortex

    private void vortexTick(ServerLevel level) {
        if (attackTick == 12) {
            Vec3 c = position();
            for (int i = 0; i < 6; i++) {
                float a = i * 60;
                tornado(level, c, t -> t.orbit(this, c, a, 3.0, 0.18, 3.2F, 0.8F, 88, damageScale()));
            }
            sandRing(level, c, 2, 48, 0.6);
            ScreenFx.shake(level, c, 32, 14, 0.5F);
        }
        if (attackTick > 12 && attackTick < 92) {
            for (Player p : fighters(level)) {
                Vec3 in = position().subtract(p.position()).multiply(1, 0, 1);
                double d = in.length();
                if (d < 2.5 || d > 30) continue;
                p.setDeltaMovement(p.getDeltaMovement().add(in.scale(0.03 / d)));
                p.hurtMarked = true;
            }
            if (attackTick % 20 == 0) playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.5F);
        }
        if (attackTick == 50) Telegraphs.show(level, Telegraph.circle(position(), 4.5F), 20, 5, this, e -> hit(e, 14, 1.4, 0.6));
        if (attackTick == 70) {
            sandBurst(level, position(), 40, 2.0, 0.2);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.6F);
        }
    }

    // ---- phase 2: the wrath of Ra

    private void startWrath(ServerLevel level) {
        hoverY = getY();
        from = position();
        for (Player p : fighters(level)) {
            if (!(p instanceof ServerPlayer sp)) continue;
            ScreenFx.play(sp, ScreenEffect.VIGNETTE, Attack.WRATH.length, 0.8F, SUN_COLOR, "");
            ScreenFx.play(sp, ScreenEffect.FLASH, 8, 0.3F, SUN_COLOR, "");
        }
        playSound(SoundEvents.BEACON_POWER_SELECT, 4.0F, 0.5F);
    }

    private void wrathTick(ServerLevel level) {
        if (attackTick <= 20) {   // up into the sky
            setNoGravity(true);
            setDeltaMovement(Vec3.ZERO);
            setPos(getX(), hoverY + attackTick * 0.25, getZ());
            if (attackTick % 4 == 0) playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.7F);
            sandBurst(level, from, 4, 2.0, 0.05);
        } else if (attackTick < 88) {
            setDeltaMovement(Vec3.ZERO);
            setPos(getX(), hoverY + 5, getZ());
            if (attackTick % 9 == 0) playSound(SoundEvents.ENDER_DRAGON_FLAP, 2.5F, 0.8F);
        }
        if (attackTick == 24 || attackTick == 40 || attackTick == 56 || attackTick == 72) {
            List<Vec3> at = new ArrayList<>();
            for (Player p : fighters(level)) at.add(p.position());
            for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = 4 + random.nextDouble() * 12;
                at.add(new Vec3(from.x + Math.cos(a) * r, from.y, from.z + Math.sin(a) * r));
            }
            for (Vec3 p : at) {
                Telegraphs.show(level, Telegraph.circle(p, 2.8F), 14, 5, this, e -> {
                    if (e.hurt(damageSources().mobAttack(this), 13 * damageScale())) e.igniteForSeconds(4);
                });
                strikes.add(new Strike(attackTick + 14, p));
            }
            playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 1.4F);
        }
        if (attackTick == 80) {   // he picks where he comes down
            List<Player> all = fighters(level);
            land = all.isEmpty() ? from : all.get(random.nextInt(all.size())).position();
            Telegraphs.show(level, Telegraph.circle(land, 5.0F), 12, 6, this, e -> hit(e, 18, 1.3, 0.6));
            Telegraphs.show(level, Telegraph.ring(land, 5.0F, 10.0F), 18, 4, this, e -> hit(e, 9, 0.9, 0.3));
            face(yawTo(land));
        }
        if (attackTick >= 88 && attackTick < 92) {
            float p = (attackTick - 87) / 4.0F;
            Vec3 high = new Vec3(getX(), hoverY + 5, getZ());
            Vec3 now = high.lerp(land, p);
            setPos(now.x, now.y, now.z);
        }
        if (attackTick == 92) {
            setPos(land.x, land.y, land.z);
            setNoGravity(false);
            ScreenFx.shake(level, position(), 32, 14, 0.9F);
            sandBurst(level, position(), 60, 2.5, 0.2);
            sandRing(level, position(), 2, 48, 0.7);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.6F);
        }
        if (attackTick == 98) sandRing(level, position(), 5.5, 60, 0.6);
    }

    // ------------------------------------------------------------------ the riddles

    private void startRiddle(ServerLevel level) {
        Telegraphs.cancel(this);
        clearTornadoes();
        strikes.clear();
        attack = Attack.NONE;
        setNoGravity(false);
        entityData.set(GAZING, false);
        getNavigation().stop();
        riddles++;
        riddleTick = 0;
        solvedTick = 0;
        doomTick = 0;
        wrong.clear();
        askedPlayers.clear();
        // a riddle he has not asked yet
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < Riddles.ALL.size(); i++) if (!askedRiddles.contains(i)) left.add(i);
        if (left.isEmpty()) {
            askedRiddles.clear();
            for (int i = 0; i < Riddles.ALL.size(); i++) left.add(i);
        }
        int index = left.get(random.nextInt(left.size()));
        askedRiddles.add(index);
        riddle = Riddles.ask(index, random);
        entityData.set(STATE, RIDDLE);
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive() || p.distanceToSqr(this) > DOOM_RANGE * DOOM_RANGE) continue;
            askedPlayers.add(p.getUUID());
            PacketDistributor.sendToPlayer(p, new RiddlePayloads.Open(getId(), riddle.question(), riddle.options(), RIDDLE_TICKS));
            ScreenFx.play(p, ScreenEffect.VIGNETTE, RIDDLE_TICKS, 0.7F, SAND_COLOR, "");
        }
        playSound(SoundEvents.BELL_RESONATE, 4.0F, 0.5F);
        playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 4.0F, 0.5F);
        BossDeath.sphere(level, GOLD_DUST, getBoundingBox().getCenter(), 40, 0.25);
    }

    private void riddleTick(ServerLevel level) {
        riddleTick++;
        setDeltaMovement(0, getDeltaMovement().y, 0);
        Player near = nearestFighter(position());
        if (near != null) face(yawTo(near.position()));
        if (doomTick > 0) {
            doom(level);
            return;
        }
        if (solvedTick > 0) {
            if (++solvedTick > 40) resume(level);
            return;
        }
        if (riddleTick % 30 == 0) playSound(SoundEvents.WARDEN_HEARTBEAT, 3.0F, 0.7F);
        if (riddleTick % 3 == 0) {   // the sand running out round him
            Vec3 c = position().add(0, 4.5, 0);
            level.sendParticles(ModParticles.SAND.get(), c.x, c.y, c.z, 2, 1.5, 0.2, 1.5, 0.0);
        }
        // whoever left or died has no say any more
        askedPlayers.removeIf(id -> {
            Player p = level.getPlayerByUUID(id);
            return p == null || !p.isAlive() || p.distanceToSqr(this) > DOOM_RANGE * DOOM_RANGE * 1.5;
        });
        if (askedPlayers.isEmpty()) {
            resume(level);
            return;
        }
        if (wrong.containsAll(askedPlayers) || riddleTick > RIDDLE_TICKS + 20) fail(level);
    }

    /** A player has chosen an answer (net.RiddlePayloads.Answer). */
    public void answered(ServerPlayer player, int choice) {
        if (!(level() instanceof ServerLevel level) || state() != RIDDLE || riddle == null || doomTick > 0 || solvedTick > 0) return;
        if (!askedPlayers.contains(player.getUUID()) || wrong.contains(player.getUUID())) return;
        if (choice == riddle.right()) {
            solved(level, player);
            return;
        }
        wrong.add(player.getUUID());
        PacketDistributor.sendToPlayer(player, new RiddlePayloads.Close(getId()));
        ScreenFx.play(player, ScreenEffect.TEXT, 50, 0.6F, 0xE05030, "Wrong - your voice is taken");
        player.playNotifySound(SoundEvents.WITHER_SPAWN, getSoundSource(), 0.6F, 1.6F);
        for (UUID id : askedPlayers) {
            if (id.equals(player.getUUID()) || !(level.getPlayerByUUID(id) instanceof ServerPlayer other)) continue;
            other.sendSystemMessage(Component.literal(player.getName().getString() + " answered wrongly...").withStyle(s -> s.withColor(0xE08040)));
        }
    }

    private void solved(ServerLevel level, ServerPlayer by) {
        solvedTick = 1;
        closeRiddle();
        String answer = riddle == null ? "" : riddle.options().get(riddle.right());
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) > DOOM_RANGE * DOOM_RANGE) continue;
            ScreenFx.play(p, ScreenEffect.FLASH, 12, 0.5F, SUN_COLOR, "");
            ScreenFx.play(p, ScreenEffect.TEXT, 70, 0.4F, 0xFFE0A0, by.getName().getString() + ": \"" + answer + "\" - the Sphinx bows");
        }
        playSound(SoundEvents.PLAYER_LEVELUP, 3.0F, 0.6F);
        playSound(SoundEvents.BELL_RESONATE, 3.0F, 1.0F);
        BossDeath.sphere(level, GOLD_DUST, getBoundingBox().getCenter(), 50, 0.35);
    }

    private void fail(ServerLevel level) {
        doomTick = 1;
        closeRiddle();
    }

    /** No one answered rightly: the desert takes every player near. */
    private void doom(ServerLevel level) {
        int t = doomTick++;
        Vec3 c = getBoundingBox().getCenter();
        if (t == 1) {
            String answer = riddle == null ? "" : riddle.options().get(riddle.right());
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(this) > DOOM_RANGE * DOOM_RANGE) continue;
                ScreenFx.play(p, ScreenEffect.SANDSTORM, 70, 1.6F, SAND_COLOR, "");
                ScreenFx.play(p, ScreenEffect.FOG, 70, 1.0F, 0x9A7040, "");
                ScreenFx.play(p, ScreenEffect.TEXT, 60, 1.2F, 0xE04020, "WRONG. It was \"" + answer + "\"");
                ScreenFx.play(p, ScreenEffect.SHAKE, 40, 0.8F);
            }
            playSound(SoundEvents.RAVAGER_ROAR, 5.0F, 0.4F);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 5.0F, 0.6F);
        }
        if (t < 30) {
            BossDeath.implode(level, ModParticles.SAND.get(), c, 8, 10, 0.5);
            if (t % 5 == 0) {
                for (ServerPlayer p : level.players()) {
                    if (p.distanceToSqr(this) < DOOM_RANGE * DOOM_RANGE) sandBurst(level, p.position(), 16, 0.8, 0.1);
                }
            }
        }
        if (t == 30) {
            for (ServerPlayer p : List.copyOf(level.players())) {
                if (p.isCreative() || p.isSpectator() || p.distanceToSqr(this) > DOOM_RANGE * DOOM_RANGE) continue;
                sandBurst(level, p.position(), 60, 1.0, 0.3);
                p.hurt(damageSources().genericKill(), Float.MAX_VALUE);
            }
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 5.0F, 0.4F);
            BossDeath.sphere(level, ModParticles.SAND.get(), c, 120, 0.8);
        }
        if (t >= 60) resume(level);
    }

    private void closeRiddle() {
        if (!(level() instanceof ServerLevel level)) return;
        for (UUID id : askedPlayers) {
            if (level.getPlayerByUUID(id) instanceof ServerPlayer p) PacketDistributor.sendToPlayer(p, new RiddlePayloads.Close(getId()));
        }
    }

    /** After a riddle: on with the fight - or, after the one at half his health, the change. */
    private void resume(ServerLevel level) {
        riddle = null;
        askedPlayers.clear();
        wrong.clear();
        doomTick = 0;
        solvedTick = 0;
        if (phase() == 1 && riddles >= 2) {
            startTransform(level);
            return;
        }
        entityData.set(STATE, FIGHT);
        cooldown = 30;
        last = Attack.NONE;
    }

    // ------------------------------------------------------------------ the change

    private void startTransform(ServerLevel level) {
        transformTick = 0;
        getNavigation().stop();
        entityData.set(STATE, TRANSFORM);
        playSound(SoundEvents.BELL_RESONATE, 3.0F, 0.4F);
    }

    private void transformTick(ServerLevel level) {
        transformTick++;
        setDeltaMovement(0, getDeltaMovement().y, 0);
        Vec3 c = getBoundingBox().getCenter();
        if (transformTick < 40 && transformTick % 3 == 0) {   // the sand drawn in to him, his body cracking
            BossDeath.implode(level, ModParticles.SAND.get(), c, 7, 8, 0.4);
            if (transformTick % 9 == 0) playSound(SoundEvents.DEEPSLATE_BREAK, 3.0F, 0.5F);
        }
        if (transformTick == 40) {   // he throws himself up in a storm, burning turquoise inside
            entityData.set(PHASE, (byte) 2);
            BossDeath.screen(this, ScreenEffect.FLASH, 10, 0.6F, TURQUOISE);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(this) > AGGRO_RANGE * AGGRO_RANGE) continue;
                ScreenFx.play(p, ScreenEffect.SANDSTORM, 60, 1.0F, SAND_COLOR, "");
            }
            BossDeath.shake(this, 20, 0.8F);
            BossDeath.sphere(level, ModParticles.SAND.get(), c, 120, 0.7);
            BossDeath.sphere(level, TURQUOISE_DUST, c, 40, 0.5);
            sandRing(level, position(), 2, 60, 0.8);
            playSound(SoundEvents.RAVAGER_ROAR, 4.0F, 0.4F);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.8F);
        }
        if (transformTick >= TRANSFORM_TICKS) {
            entityData.set(STATE, FIGHT);
            cooldown = 15;
            last = Attack.NONE;
        }
    }

    // ------------------------------------------------------------------ his end: a tornado of sand, blown away

    @Override
    protected void tickDeath() {
        dying++;
        if (!(level() instanceof ServerLevel level)) return;
        int t = dying;
        Vec3 c = getBoundingBox().getCenter();
        if (t == 1) {
            Telegraphs.cancel(this);
            clearTornadoes();
            closeRiddle();
            strikes.clear();
            setNoGravity(false);
            bossBar.removeAllPlayers();
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(this) > BossDeath.REACH * BossDeath.REACH) continue;
                ScreenFx.play(p, ScreenEffect.SANDSTORM, DEATH_TICKS, 0.7F, SAND_COLOR, "");
            }
            BossDeath.on(this, Anchor.EXCEPT, 0, ScreenEffect.DESATURATE, DEATH_TICKS, 0.6F, -1);
            BossDeath.sound(this, SoundEvents.RAVAGER_HURT, 4.0F, 0.5F);
        }
        if (t == 24) {   // he rears up screaming
            BossDeath.sound(this, SoundEvents.RAVAGER_ROAR, 5.0F, 0.4F);
            BossDeath.sound(this, SoundEvents.ENDER_DRAGON_GROWL, 4.0F, 0.6F);
            BossDeath.shake(this, 16, 0.5F);
        }
        if (t >= DEATH_SPIN && t < 100) {   // and turns into a tornado of sand: it whirls up round him, tighter and faster
            float k = (t - DEATH_SPIN) / 52.0F;
            for (int i = 0; i < 6; i++) {
                double h = random.nextDouble() * 9;
                double r = 0.8 + h * 0.35 + k * 1.5;
                double a = random.nextDouble() * Math.PI * 2;
                level.sendParticles(ModParticles.SAND.get(), getX() + Math.cos(a) * r, getY() + h, getZ() + Math.sin(a) * r, 0,
                        -Math.sin(a), 0.15, Math.cos(a), 0.5 + k);
            }
            if (t % 10 == 0) BossDeath.sound(this, SoundEvents.ELYTRA_FLYING, 3.0F, 0.4F + k * 0.4F);
            if (phase() == 2 && t % 4 == 0) level.sendParticles(TURQUOISE_DUST, c.x, c.y, c.z, 4, 0.8, 1.5, 0.8, 0.0);
        }
        if (t == 100) {   // and blows away
            BossDeath.sphere(level, ModParticles.SAND.get(), c.add(0, 2, 0), 160, 0.9);
            sandRing(level, position(), 2, 80, 1.0);
            BossDeath.screen(this, ScreenEffect.FLASH, 14, 0.5F, 0xFFE8B0);
            BossDeath.shake(this, 20, 0.6F);
            BossDeath.sound(this, SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.5F);
            BossDeath.sound(this, SoundEvents.ELYTRA_FLYING, 4.0F, 1.2F);
        }
        if (t >= DEATH_TICKS) remove(RemovalReason.KILLED);
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
        if (state() != FIGHT) return false;
        // he stops at each quarter of his health to ask his riddle: no blow takes him past it
        float floor = riddles < 3 ? getMaxHealth() * (0.75F - 0.25F * riddles) : 0;
        if (floor > 0 && getHealth() - amount < floor) amount = Math.max(0.01F, getHealth() - floor);
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) {
            targeting.onHurt(source, level().getGameTime());
            if (floor > 0 && getHealth() <= floor + 0.05F && isAlive()) startRiddle((ServerLevel) level());
        }
        return hurt;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SAND_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Phase", (byte) phase());
        tag.putInt("Riddles", riddles);
        tag.put("AskedRiddles", new IntArrayTag(askedRiddles.stream().mapToInt(Integer::intValue).toArray()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Phase")) entityData.set(PHASE, (byte) Math.max(1, tag.getByte("Phase")));
        riddles = tag.getInt("Riddles");
        askedRiddles.clear();
        for (int i : tag.getIntArray("AskedRiddles")) askedRiddles.add(i);
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    @Override
    public void setCustomName(@Nullable net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }
}
