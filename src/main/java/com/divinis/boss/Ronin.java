package com.divinis.boss;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.divinis.net.SlashPayload;
import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenEffect.Anchor;
import com.divinis.screenfx.ScreenFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Ronin (/summon divinis:ronin): a masterless samurai in red lacquered armour under a straw rice hat, fighting with
 * a katana in the manner of a flicker strike - blinking from one foe to the next and cutting as he appears. His cuts tear
 * space: a crescent of red light along them that bends the picture round it as a black hole does (client.SlashFx).
 *
 * Phase 1:
 * - Rift slash: he winds back and cuts across in a dash; the tear he leaves hangs in the air for a moment and burns;
 * - Flicker strike: three blinks, each next to a foe, each a cut as he appears;
 * - Drawing cut: he crouches, his hand on the hilt, the air drawn in to him - then cuts round himself, and a breath
 *   later a second ring further out: step out of the first, but not too far.
 * At half health he sinks to one knee, the world loses its colour, and with a flash of red his armour and hat turn
 * black and his eyes catch fire - red, glowing, leaving streaks as he moves (client.RoninRenderer).
 * Phase 2:
 * - Shadow behind you: he blinks behind a player five times, each time (after a red warning) striking three times in
 *   quick succession - only a shield raised towards him, turning with him, stops it; if none of the fifteen cuts lands,
 *   he is left reeling, stunned for two seconds;
 * - Rift cross: two cuts crossing on a foe, blinking from the end of one to the next; both tears hang and burn;
 * - Blood moon flurry: eight blinks between every foe, a cut at each, a red streak between them;
 * - Thousand cuts: his dark aura flares up round him as he leaps into a darkened sky, cuts tear across the field one
 *   after another through his foes, and he comes down blade first on one of them - his aura bursting out of him in a
 *   wave of black flame over the ground (the red ring: it burns whoever it reaches).
 * In phase 2 a dark aura burns round him like black, see-through flame (client.RoninRenderer).
 * Dying he kneels, turns the blade on himself and commits seppuku, cherry petals falling round him.
 * Timings match the animations in client/RoninModelData (tools/models/build_ronin.py).
 */
public class Ronin extends Monster implements BossTargeting.Marked {
    public static final byte FIGHT = 0, TRANSFORM = 1;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Ronin.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(Ronin.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(Ronin.class, EntityDataSerializers.INT);
    /** His aura flared up (the thousand cuts), and when its wave of black flame burst out of him (game time, -1 none). */
    private static final EntityDataAccessor<Boolean> AURA_FLARE = SynchedEntityData.defineId(Ronin.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> AURA_WAVE = SynchedEntityData.defineId(Ronin.class, EntityDataSerializers.INT);
    /** Stunned after a shadow-behind-you that never landed. */
    private static final EntityDataAccessor<Boolean> STUNNED = SynchedEntityData.defineId(Ronin.class, EntityDataSerializers.BOOLEAN);
    public static final int STUN_TICKS = 40;
    public static final int WAVE_TICKS = 14;
    public static final float WAVE_RADIUS = 11;

    public enum Attack {
        NONE(0), RIFT(32), FLICKER(56), IAI(46), BACKSTEP(156), CROSS(46), FLURRY(82), CUTS(108);

        final int length;

        Attack(int length) {
            this.length = length;
        }
    }

    private static final byte EVENT_ATTACK = (byte) 140, EVENT_FLICKER = (byte) 160, EVENT_BACKSTEP = (byte) 161, EVENT_PLUNGE = (byte) 162;
    public static final int TRANSFORM_TICKS = 80, DEATH_TICKS = 120;
    private static final double AGGRO_RANGE = 40;
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.6F, 0.02F, 0.04F), 1.6F);
    private static final DustParticleOptions CRIMSON = new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.12F), 1.2F);

    // client: animations
    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState slashAnimation = new AnimationState();
    public final AnimationState flickerAnimation = new AnimationState();
    public final AnimationState iaiAnimation = new AnimationState();
    public final AnimationState backstepAnimation = new AnimationState();
    public final AnimationState crossAnimation = new AnimationState();
    public final AnimationState leapAnimation = new AnimationState();
    public final AnimationState plungeAnimation = new AnimationState();
    public final AnimationState transformAnimation = new AnimationState();
    public final AnimationState deathAnimation = new AnimationState();
    public final AnimationState stunAnimation = new AnimationState();
    private int clientAttackTicks;
    /** Ticks since he began dying (both sides; his own count, so vanilla neither tips nor reddens him). */
    public int dying;

    // server
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private final BossTargeting targeting = new BossTargeting();
    private Attack attack = Attack.NONE, last = Attack.NONE;
    private int attackTick, cooldown = 30, transformTick;
    private float lockedYaw;
    private Vec3 aim = Vec3.ZERO, dashFrom = Vec3.ZERO, dashTo = Vec3.ZERO;
    @Nullable private Player victim;
    private int chain;
    /** Cuts of the shadow-behind-you that landed; ticks left stunned. */
    private int landedCuts, stun;

    public Ronin(EntityType<? extends Ronin> type, Level level) {
        super(type, level);
        this.xpReward = 350;
        setPersistenceRequired();
        bossBar.setVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 480.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
                .add(Attributes.ATTACK_DAMAGE, 18.0)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, FIGHT);
        builder.define(PHASE, (byte) 1);
        builder.define(TARGET_ID, -1);
        builder.define(AURA_FLARE, false);
        builder.define(AURA_WAVE, -1);
        builder.define(STUNNED, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public byte state() { return entityData.get(STATE); }
    public int phase() { return entityData.get(PHASE); }
    public boolean auraFlared() { return entityData.get(AURA_FLARE); }
    public int auraWave() { return entityData.get(AURA_WAVE); }

    public boolean isAttacking() {
        return clientAttackTicks > 0 || state() == TRANSFORM || entityData.get(STUNNED);
    }

    // ------------------------------------------------------------------ client

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STUNNED.equals(key) && level().isClientSide) {
            if (entityData.get(STUNNED)) stunAnimation.start(tickCount);
            else stunAnimation.stop();
        }
        if (STATE.equals(key) && level().isClientSide) {
            if (state() == TRANSFORM) transformAnimation.start(tickCount);
            else transformAnimation.stop();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        AnimationState[] all = {slashAnimation, flickerAnimation, iaiAnimation, backstepAnimation, crossAnimation, leapAnimation, plungeAnimation};
        if (id == EVENT_FLICKER || id == EVENT_BACKSTEP || id == EVENT_PLUNGE) {
            for (AnimationState s : all) s.stop();
            (id == EVENT_FLICKER ? flickerAnimation : id == EVENT_BACKSTEP ? backstepAnimation : plungeAnimation).start(tickCount);
            clientAttackTicks = Math.max(clientAttackTicks, 12);
            return;
        }
        int index = (id & 0xFF) - (EVENT_ATTACK & 0xFF);
        if (index > 0 && index < Attack.values().length) {
            Attack a = Attack.values()[index];
            for (AnimationState s : all) s.stop();
            AnimationState play = switch (a) {
                case RIFT -> slashAnimation;
                case IAI -> iaiAnimation;
                case CROSS -> crossAnimation;
                case CUTS -> leapAnimation;
                case BACKSTEP -> backstepAnimation;
                default -> flickerAnimation;
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
            return;
        }
        idleAnimation.startIfStopped(tickCount);
        if (clientAttackTicks > 0) clientAttackTicks--;
    }

    // ------------------------------------------------------------------ server

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (tickCount % 10 == 0) updateBossBar(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (phase() == 2 && tickCount % 4 == 0) {   // black smoke curling off his armour
            Vec3 c = getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 1, 0.4, 0.6, 0.4, 0.01);
        }
        if (state() == TRANSFORM) {
            transformTick(level);
            return;
        }
        fight(level);
    }

    private void updateBossBar(ServerLevel level) {
        for (ServerPlayer p : List.copyOf(bossBar.getPlayers())) {
            if (!p.isAlive() || p.level() != level || p.distanceToSqr(this) > 64 * 64) bossBar.removePlayer(p);
        }
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 40 * 40) bossBar.addPlayer(p);
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

    private void fight(ServerLevel level) {
        if (stun > 0) {
            stun--;
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (stun % 4 == 0) {   // reeling: stars round his head
                Vec3 h = position().add(0, getBbHeight() * 0.92, 0);
                double a = stun * 0.8;
                level.sendParticles(ParticleTypes.CRIT, h.x + Math.cos(a) * 0.6, h.y, h.z + Math.sin(a) * 0.6, 2, 0.05, 0.05, 0.05, 0.0);
            }
            if (stun == 0) entityData.set(STUNNED, false);
            return;
        }
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
        getLookControl().setLookAt(target, 30, 30);
        double dist = Math.sqrt(distanceToSqr(target));
        if (cooldown > 0) cooldown--;
        if (cooldown <= 0 && dist < 28) {
            start(level, choose(dist), target);
            return;
        }
        if (dist > 4) {
            if (tickCount % 8 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
        }
    }

    private Attack choose(double dist) {
        List<Attack> options = new ArrayList<>(phase() == 1
                ? List.of(Attack.RIFT, Attack.FLICKER, Attack.IAI)
                : List.of(Attack.BACKSTEP, Attack.CROSS, Attack.FLURRY, Attack.CUTS));
        if (phase() == 1 && dist > 12) options.remove(Attack.IAI);
        if (options.size() > 1) options.remove(last);
        return options.get(random.nextInt(options.size()));
    }

    /** /bossattack: starts an attack by name now, if he can. */
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

    private void start(ServerLevel level, Attack a, LivingEntity target) {
        attack = a;
        last = a;
        attackTick = 0;
        chain = 0;
        landedCuts = 0;
        getNavigation().stop();
        face(yawTo(target.position()));
        aim = target.position();
        victim = target instanceof Player p ? p : null;
        level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + a.ordinal()));
        switch (a) {
            case RIFT -> startRift(level);
            case IAI -> startIai(level);
            case CROSS -> startCross(level);
            case CUTS -> startCuts(level);
            default -> {}
        }
    }

    private void end() {
        attack = Attack.NONE;
        entityData.set(AURA_FLARE, false);
        cooldown = phase() == 1 ? 26 : 18;
        setNoGravity(false);
    }

    private void attackTick(ServerLevel level, @Nullable LivingEntity target) {
        attackTick++;
        face(lockedYaw);
        setDeltaMovement(0, attack == Attack.CUTS ? 0 : getDeltaMovement().y, 0);
        switch (attack) {
            case RIFT -> riftTick(level);
            case FLICKER -> flickerTick(level, 3, 16, 10, 14, 1.0F);
            case IAI -> iaiTick(level);
            case BACKSTEP -> backstepTick(level);
            case CROSS -> crossTick(level);
            case FLURRY -> flickerTick(level, 6, 12, 8, 12, 0.5F);
            case CUTS -> cutsTick(level);
            default -> {}
        }
        if (attackTick >= attack.length) {
            boolean missed = attack == Attack.BACKSTEP && landedCuts == 0;
            end();
            if (missed) {   // fifteen cuts and not one landed: he is left reeling
                stun = STUN_TICKS;
                entityData.set(STUNNED, true);
                playSound(SoundEvents.ANVIL_LAND, 1.5F, 1.6F);
                playSound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 2.0F, 0.6F);
                BossDeath.burst(level, ParticleTypes.CRIT, position().add(0, getBbHeight(), 0), 20, 0.4, 0.3);
            }
        }
    }

    // ------------------------------------------------------------------ the slashes and blinks

    /** A red slash through space for everyone near (client.SlashFx). */
    private void slash(ServerLevel level, Vec3 from, Vec3 to, float bend, float width, int life, float warp) {
        PacketDistributor.sendToPlayersNear(level, null, from.x, from.y, from.z, 96, new SlashPayload(from, to, bend, width, life, warp));
    }

    /** A blink: an after-image of smoke and red dust where he was, a red streak to where he is, and he is there. */
    private void blink(ServerLevel level, Vec3 to) {
        Vec3 from = position();
        Vec3 c = getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 10, 0.3, 0.6, 0.3, 0.02);
        level.sendParticles(CRIMSON, c.x, c.y, c.z, 14, 0.4, 0.7, 0.4, 0.0);
        slash(level, from.add(0, 1.4, 0), to.add(0, 1.4, 0), 0, 0.35F, 8, 0.35F);
        teleportTo(to.x, to.y, to.z);
        playSound(SoundEvents.ENDERMAN_TELEPORT, 1.6F, 1.7F);
        level.sendParticles(CRIMSON, to.x, to.y + 1.2, to.z, 10, 0.3, 0.6, 0.3, 0.0);
    }

    /** A free spot near a player, dist blocks away at an angle round him, on his ground. */
    private Vec3 near(Player p, double dist, float angle) {
        Vec3 at = p.position().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        BlockPos b = BlockPos.containing(at.x, p.getY() + 1, at.z);
        if (!level().getBlockState(b).isAir() || !level().getBlockState(b.above()).isAir()) return p.position();
        return new Vec3(at.x, p.getY(), at.z);
    }

    /** A lingering tear in space along a cut: it burns whoever stands in it. */
    private void tear(ServerLevel level, Vec3 start, float yaw, float length, int ticks) {
        Telegraphs.sweep(level, Telegraph.rect(start, yaw, length, 1.0F), 1, ticks, 0, 10, this, e -> hit(e, 5, 0, 0));
    }

    // ---- phase 1: the rift slash

    private void startRift(ServerLevel level) {
        dashFrom = position();
        Vec3 dir = dirOf(lockedYaw);
        double reach = Mth.clamp(Math.sqrt(distanceToSqr(aim)) + 3, 6, 14);
        dashTo = position().add(dir.scale(reach));
        Telegraphs.show(level, Telegraph.rect(dashFrom, lockedYaw, (float) reach + 2, 1.6F), 15, 4, this, e -> hit(e, 18, 0.8, 0.3));
        playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 2.0F, 0.6F);
    }

    private void riftTick(ServerLevel level) {
        if (attackTick == 15) {
            Vec3 a = dashFrom.add(0, 1.3, 0), b = dashTo.add(0, 1.3, 0);
            teleportTo(dashTo.x, dashTo.y, dashTo.z);
            slash(level, a, b, 2.5F, 1.3F, 30, 1.0F);
            tear(level, dashFrom, lockedYaw, (float) dashFrom.distanceTo(dashTo), 30);
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.5F);
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 1.5F);
            ScreenFx.shake(level, position(), 24, 6, 0.4F);
        }
    }

    // ---- phase 1 and 2: the flicker strikes

    /** count blinks, one every step ticks, each beside a foe with a cut warned warn ticks before it lands. */
    private void flickerTick(ServerLevel level, int count, int step, int warn, float damage, float streak) {
        int t = (attackTick - 1) % step;
        if (chain > 0 && chain <= count && t == warn - 3) level.broadcastEntityEvent(this, EVENT_FLICKER);   // the swing
        if (chain > 0 && chain <= count && t == warn) {   // the cut lands now
            Vec3 side = dirOf(lockedYaw + 90).scale(1.6);
            Vec3 mid = position().add(dirOf(lockedYaw).scale(1.8)).add(0, 1.2, 0);
            slash(level, mid.add(side).add(0, 0.8, 0), mid.subtract(side).add(0, -0.5, 0), 0.8F, 0.6F, 10, 0.4F * streak);
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 1.3F);
        }
        if (t != 0 || chain >= count) return;
        List<Player> all = fighters(level);
        if (all.isEmpty()) return;
        Player p = attack == Attack.FLURRY ? all.get(chain % all.size()) : all.get(random.nextInt(all.size()));
        chain++;
        Vec3 to = near(p, 2.2, random.nextFloat() * Mth.TWO_PI);
        blink(level, to);
        face(yawTo(p.position()));
        Telegraphs.show(level, Telegraph.sector(position(), lockedYaw, 3.6F, 70), warn, 3, this, e -> hit(e, damage, 0.6, 0.2));
    }

    // ---- phase 1: the drawing cut

    private void startIai(ServerLevel level) {
        Telegraphs.show(level, Telegraph.circle(position(), 7.5F), 26, 5, this, e -> hit(e, 20, 1.0, 0.4));
        Telegraphs.show(level, Telegraph.ring(position(), 7.5F, 12.0F), 36, 5, this, e -> hit(e, 16, 0.8, 0.3));
        playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 2.0F, 0.5F);
    }

    private void iaiTick(ServerLevel level) {
        Vec3 c = position().add(0, 1.2, 0);
        if (attackTick > 6 && attackTick < 26) {   // the air drawn in to him
            BossDeath.implode(level, CRIMSON, c, 6, 4, 0.35);
            if (attackTick % 6 == 0) playSound(SoundEvents.BEACON_AMBIENT, 2.0F, 1.6F);
        }
        if (attackTick == 26 || attackTick == 36) {
            double r = attackTick == 26 ? 6.5 : 10.5;
            for (int i = 0; i < 6; i++) {
                double a0 = i * Math.PI / 3 + (attackTick == 36 ? Math.PI / 6 : 0), a1 = a0 + Math.PI / 3;
                Vec3 from = c.add(Math.cos(a0) * r, 0, Math.sin(a0) * r), to = c.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
                slash(level, from, to, (float) (r * 0.15), 1.0F, 22, 0.7F);
            }
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, attackTick == 26 ? 0.5F : 0.7F);
            playSound(SoundEvents.TRIDENT_RIPTIDE_3.value(), 2.0F, 1.4F);
            ScreenFx.shake(level, position(), 24, 6, 0.4F);
        }
    }

    // ---- phase 2: the shadow behind you

    /** A blink every BACK_STEP ticks, five of them; the cuts BACK_CUTS ticks after each (as the backstep animation). */
    private static final int BACK_STEP = 30, BACK_COUNT = 5;
    private static final int[] BACK_CUTS = {9, 15, 21};

    private void backstepTick(ServerLevel level) {
        int t = (attackTick - 1) % BACK_STEP;
        int n = (attackTick - 1) / BACK_STEP;
        if (n >= BACK_COUNT) return;
        if (t == 0) {
            List<Player> all = fighters(level);
            if (all.isEmpty()) return;
            victim = victim != null && validTarget(victim) && random.nextFloat() < 0.7F ? victim : all.get(random.nextInt(all.size()));
            Vec3 look = victim.getLookAngle();
            Vec3 back = new Vec3(look.x, 0, look.z);
            back = back.lengthSqr() < 1e-4 ? dirOf(victim.getYRot()) : back.normalize();
            Vec3 to = victim.position().subtract(back.scale(1.8));
            BlockPos b = BlockPos.containing(to.x, victim.getY() + 1, to.z);
            if (!level.getBlockState(b).isAir()) to = victim.position();
            blink(level, new Vec3(to.x, victim.getY(), to.z));
            face(yawTo(victim.position()));
            level.broadcastEntityEvent(this, EVENT_BACKSTEP);
            playSound(SoundEvents.ENDERMAN_STARE, 1.0F, 1.8F);
            Telegraphs.show(level, Telegraph.sector(position(), lockedYaw, 3.2F, 60), BACK_CUTS[0], 2, this, null);
        }
        if ((t == BACK_CUTS[0] || t == BACK_CUTS[1] || t == BACK_CUTS[2]) && victim != null && victim.isAlive()
                && victim.distanceToSqr(this) < 3.6 * 3.6) {
            // three cuts in quick succession: a shield raised towards him stops each (as vanilla blocks), every one counts
            victim.invulnerableTime = 0;
            boolean landed = victim.hurt(damageSources().mobAttack(this), 6 * damageScale());
            if (landed) landedCuts++;
            Vec3 m = victim.position().add(0, 1.1, 0);
            Vec3 side = dirOf(lockedYaw + 90).scale(1.1);
            float tilt = t == BACK_CUTS[1] ? -0.6F : 0.6F;
            slash(level, m.add(side).add(0, tilt, 0), m.subtract(side).add(0, -tilt, 0), 0.4F, 0.5F, 8, 0.25F);
            playSound(landed ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.SHIELD_BLOCK, 1.6F, 1.2F + t * 0.03F);
            if (landed) level.sendParticles(BLOOD, m.x, m.y, m.z, 8, 0.3, 0.3, 0.3, 0.0);
        }
    }

    // ---- phase 2: the rift cross

    private void startCross(ServerLevel level) {
        for (int i = 0; i < 2; i++) {
            float yaw = lockedYaw + (i == 0 ? 45 : -45);
            Vec3 start = aim.subtract(dirOf(yaw).scale(9));
            Telegraphs.show(level, Telegraph.rect(start, yaw, 18, 1.4F), i == 0 ? 14 : 22, 4, this, e -> hit(e, 18, 0.7, 0.3));
        }
        playSound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 2.0F, 0.6F);
    }

    private void crossTick(ServerLevel level) {
        if (attackTick != 14 && attackTick != 22) return;
        float yaw = lockedYaw + (attackTick == 14 ? 45 : -45);
        Vec3 start = aim.subtract(dirOf(yaw).scale(9)), end = aim.add(dirOf(yaw).scale(9));
        blink(level, end);
        slash(level, start.add(0, 1.3, 0), end.add(0, 1.3, 0), 1.8F, 1.3F, 40, 1.0F);
        tear(level, start, yaw, 18, 40);
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.6F);
        ScreenFx.shake(level, position(), 24, 6, 0.45F);
    }

    // ---- phase 2: the thousand cuts

    private Vec3 plungeAt = Vec3.ZERO;

    private void startCuts(ServerLevel level) {
        for (Player p : fighters(level)) {
            if (p instanceof ServerPlayer sp) {
                ScreenFx.play(sp, ScreenEffect.DARKNESS, Attack.CUTS.length, 0.55F, -1, "");
                ScreenFx.play(sp, ScreenEffect.VIGNETTE, Attack.CUTS.length, 0.8F, 0x900010, "");
            }
        }
        playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.8F);
        // his aura flares up round him
        entityData.set(AURA_FLARE, true);
        playSound(SoundEvents.SOUL_ESCAPE.value(), 3.0F, 0.5F);
        playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 0.7F);
    }

    private void cutsTick(ServerLevel level) {
        if (attackTick <= 10) {   // up into the dark
            setNoGravity(true);
            setPos(getX(), getY() + 0.7, getZ());
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 3, 0.3, 0.2, 0.3, 0.02);
        }
        if (attackTick >= 18 && attackTick <= 68 && (attackTick - 18) % 10 == 0) {   // a cut across the field
            List<Player> all = fighters(level);
            if (!all.isEmpty()) {
                Player p = all.get(random.nextInt(all.size()));
                float yaw = random.nextFloat() * 360;
                Vec3 start = p.position().subtract(dirOf(yaw).scale(15));
                Telegraphs.show(level, Telegraph.rect(start, yaw, 30, 1.2F), 16, 4, this, e -> hit(e, 16, 0.6, 0.3));
                Vec3 s = start, e = p.position().add(dirOf(yaw).scale(15));
                pending.add(new Cut(attackTick + 16, s, e));
            }
        }
        for (var it = pending.iterator(); it.hasNext(); ) {
            Cut c = it.next();
            if (c.at() != attackTick) continue;
            it.remove();
            slash(level, c.from().add(0, 1.0, 0), c.to().add(0, 1.0, 0), 1.2F, 1.2F, 26, 1.0F);
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.7F + random.nextFloat() * 0.3F);
        }
        if (attackTick == 76) {   // he picks where he comes down
            List<Player> all = fighters(level);
            plungeAt = all.isEmpty() ? position() : all.get(random.nextInt(all.size())).position();
            Telegraphs.show(level, Telegraph.circle(plungeAt, 4.0F), 14, 6, this, e -> hit(e, 24, 1.2, 0.8));
        }
        if (attackTick == 82) {
            teleportTo(plungeAt.x, plungeAt.y + 5, plungeAt.z);
            level.broadcastEntityEvent(this, EVENT_PLUNGE);
            playSound(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 1.4F);
        }
        if (attackTick == 90) {
            teleportTo(plungeAt.x, plungeAt.y, plungeAt.z);
            setNoGravity(false);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 1.2F);
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.5F);
            ScreenFx.shake(level, position(), 30, 12, 0.8F);
            BossDeath.ring(level, CRIMSON, position(), 1, 40, 0.5);
            BossDeath.dust(level, position(), 50, 2.0);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + Math.PI / 4;
                Vec3 o = position().add(0, 0.6, 0);
                slash(level, o, o.add(Math.cos(a) * 6, 0, Math.sin(a) * 6), 0.8F, 1.0F, 20, 0.8F);
            }
            // the aura bursts out of him in a wave of black flame over the ground
            entityData.set(AURA_FLARE, false);
            entityData.set(AURA_WAVE, (int) level.getGameTime());
            Telegraphs.show(level, Telegraph.ring(position(), 2.0F, WAVE_RADIUS), WAVE_TICKS / 2, 5, this, e -> hit(e, 14, 1.0, 0.5));
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 2.5F, 0.6F);
            BossDeath.ring(level, ParticleTypes.LARGE_SMOKE, position(), 1.5, 36, 0.6);
        }
    }

    private record Cut(int at, Vec3 from, Vec3 to) {}

    private final List<Cut> pending = new ArrayList<>();

    // ------------------------------------------------------------------ the change

    private void startTransform(ServerLevel level) {
        Telegraphs.cancel(this);
        attack = Attack.NONE;
        pending.clear();
        setNoGravity(false);
        transformTick = 0;
        getNavigation().stop();
        entityData.set(STATE, TRANSFORM);
        BossDeath.on(this, Anchor.EXCEPT, 0, ScreenEffect.DESATURATE, TRANSFORM_TICKS, 1.0F, -1);
        playSound(SoundEvents.BELL_RESONATE, 3.0F, 0.5F);
    }

    private void transformTick(ServerLevel level) {
        transformTick++;
        setDeltaMovement(0, getDeltaMovement().y, 0);
        Vec3 c = getBoundingBox().getCenter();
        if (transformTick < 40 && transformTick % 4 == 0) {   // darkness gathering round him
            BossDeath.implode(level, ParticleTypes.SQUID_INK, c, 4, 6, 0.25);
            playSound(SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.6F);
        }
        if (transformTick == 40) {   // with a flash of red: the black armour, the burning eyes
            entityData.set(PHASE, (byte) 2);
            BossDeath.screen(this, ScreenEffect.FLASH, 10, 0.7F, 0xFF1020);
            BossDeath.screen(this, ScreenEffect.INVERT, 4, 1.0F, -1);
            BossDeath.screen(this, ScreenEffect.VIGNETTE, 60, 0.9F, 0x900010);
            BossDeath.shake(this, 20, 0.7F);
            BossDeath.sphere(level, ParticleTypes.LARGE_SMOKE, c, 40, 0.4);
            BossDeath.sphere(level, CRIMSON, c, 40, 0.5);
            playSound(SoundEvents.ENDERMAN_STARE, 3.0F, 0.5F);
            playSound(SoundEvents.WITHER_SHOOT, 2.0F, 0.4F);
        }
        if (transformTick == 66) {   // he rises with a flick of the blade
            for (int i = 0; i < 8; i++) {
                double a0 = i * Math.PI / 4, a1 = a0 + Math.PI / 4;
                slash(level, c.add(Math.cos(a0) * 4, -0.2, Math.sin(a0) * 4), c.add(Math.cos(a1) * 4, -0.2, Math.sin(a1) * 4), 0.6F, 0.8F, 20, 0.8F);
            }
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.5F);
        }
        if (transformTick >= TRANSFORM_TICKS) {
            entityData.set(STATE, FIGHT);
            cooldown = 15;
            last = Attack.NONE;
        }
    }

    // ------------------------------------------------------------------ seppuku

    @Override
    protected void tickDeath() {
        dying++;
        if (!(level() instanceof ServerLevel level)) return;
        int t = dying;
        Vec3 c = getBoundingBox().getCenter();
        if (t == 1) {
            Telegraphs.cancel(this);
            pending.clear();
            setNoGravity(false);
            bossBar.removeAllPlayers();
            BossDeath.on(this, Anchor.EXCEPT, 0, ScreenEffect.DESATURATE, DEATH_TICKS, 1.0F, -1);
            BossDeath.screen(this, ScreenEffect.VIGNETTE, DEATH_TICKS, 0.8F, 0x200000);
            BossDeath.sound(this, SoundEvents.BELL_RESONATE, 3.0F, 0.6F);
        }
        if (t % 3 == 0) {   // cherry petals falling round him
            level.sendParticles(ParticleTypes.CHERRY_LEAVES, c.x, c.y + 2.5, c.z, 3, 3.0, 0.5, 3.0, 0.0);
        }
        if (t == 16) BossDeath.sound(this, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.5F, 0.6F);   // he kneels
        if (t == 32) BossDeath.sound(this, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.2F, 0.8F);   // turns the blade on himself
        Vec3 belly = c.add(0, -0.4, 0);
        if (t == 50) {   // the blade goes in
            BossDeath.sound(this, SoundEvents.PLAYER_ATTACK_CRIT, 2.0F, 0.5F);
            BossDeath.burst(level, BLOOD, belly, 30, 0.3, 0.0);
            BossDeath.screen(this, ScreenEffect.FLASH, 6, 0.3F, 0x900000);
            BossDeath.shake(this, 6, 0.2F);
        }
        if (t > 50 && t < 70 && t % 2 == 0) level.sendParticles(BLOOD, belly.x, belly.y, belly.z, 3, 0.3, 0.1, 0.3, 0.0);
        if (t == 68) {   // drawn across
            BossDeath.sound(this, SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.4F);
            BossDeath.burst(level, BLOOD, belly, 40, 0.5, 0.0);
        }
        if (t == 100) {   // he falls forward
            BossDeath.sound(this, SoundEvents.ARMOR_STAND_FALL, 2.0F, 0.6F);
            BossDeath.dust(level, position(), 30, 1.0);
            BossDeath.sound(this, SoundEvents.BELL_RESONATE, 3.0F, 0.4F);
        }
        if (t >= DEATH_TICKS) {
            BossDeath.sphere(level, ParticleTypes.CHERRY_LEAVES, c, 60, 0.3);
            BossDeath.screen(this, ScreenEffect.FLASH, 10, 0.4F, 0xFFE0E8);
            remove(RemovalReason.KILLED);
        }
    }

    // ------------------------------------------------------------------ the rest

    private float damageScale() {
        var attr = getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null || attr.getBaseValue() <= 0 ? 1.0F : (float) (attr.getValue() / attr.getBaseValue());
    }

    private void hit(LivingEntity e, float damage, double push, double up) {
        if (!e.hurt(damageSources().mobAttack(this), damage * damageScale())) return;
        Vec3 c = e.position().add(0, 1, 0);
        if (level() instanceof ServerLevel level) level.sendParticles(BLOOD, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.0);
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0) return;
        e.setDeltaMovement(e.getDeltaMovement().add(dx / len * push * resist, up * resist, dz / len * push * resist));
        e.hurtMarked = true;
    }

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
        if (state() == TRANSFORM) return false;
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
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ARMOR_EQUIP_CHAIN.value();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
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
