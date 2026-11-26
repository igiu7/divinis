package com.divinis.boss;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.divinis.registry.ModParticles;
import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenEffect.Anchor;
import com.divinis.screenfx.ScreenFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * The Hydra (/summon divinis:hydra in its bog - its home): a great swamp beast sunk to the waist, heads on long necks.
 * It sleeps sunk under the water, only its back showing; a player coming near or a blow wakes it: it rises, its heads
 * coming up one after another. Every attack is telegraphed (boss.Telegraphs: whoever stands in the red when it fills is
 * hit) - with good movement it can be beaten without taking a hit.
 *
 * Its heads (HydraHead) are hit on their own: each has its health (the blow goes to the Hydra too, in full; a blow on
 * the body only in part). A head brought down is cut off: the neck's stump bleeds, and ten seconds later the head grows
 * back as TWO - unless the stump is burned first: used on with a torch, a soul torch, flint and steel or a fire charge,
 * or struck by fire (a burning arrow). Each of the first three heads grows back so once; heads that grew back stay
 * stumps when cut. The heads it has decide the attacks it can make.
 *
 * Phase 1:
 * - Fire: every fire head breathes a cone of fire before it (it burns);
 * - Acid: every acid head spits three gobs onto the players; they leave pools of acid that burn for five seconds;
 * - Ice: every ice head breathes a line of frost (it slows and freezes).
 * - Tail: with no heads left it slams its tail: a wave rolls across the whole bog - jump it.
 * At half health every head roars, it sinks under and comes up whole again (its first heads back, the cuts healed)
 * with a fourth, the great head (it cannot be hurt meanwhile).
 * Phase 2, besides the first attacks:
 * - Dive: it sinks under and bursts up under a player (the circle), every head striking down;
 * - Wave: a tail slam, a wave across the bog - jump it (two when enraged);
 * - Three breaths: fire, acid and ice at once in three directions, turning - get out of their reach or keep in the gaps;
 * - The maw: the great head draws everyone in for three seconds (walk, better run, against it) and bites the half
 *   circle before it.
 * Below 40% health it is enraged: faster, shorter rests.
 * Dying, its heads fall to the water one after another and the body sinks.
 * Timings match the animations in client/HydraModelData (tools/models/build_hydra.py).
 * Effects: fire, acid (ModParticles.ACID, VENOM), frost (FROST), its blood (HYDRA_BLOOD), the bog thrown up
 * (BOG_SPLASH); a bog-green fog while it changes, a warping of the world round the maw.
 */
public class Hydra extends Monster implements BossTargeting.Marked {
    public static final byte SLEEPING = 0, WAKING = 1, AWAKE = 2, TRANSFORM = 3;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Hydra.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(Hydra.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(Hydra.class, EntityDataSerializers.INT);
    /** the heads: bits 0-9 alive, 10-19 a stump shown, 20-29 the stump burned */
    private static final EntityDataAccessor<Integer> HEADS = SynchedEntityData.defineId(Hydra.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> UNDER = SynchedEntityData.defineId(Hydra.class, EntityDataSerializers.BOOLEAN);

    // ------------------------------------------------------------------ the heads (as in tools/models/build_hydra.py)
    public static final int FIRE = 0, ACID = 1, ICE = 2, GREAT = 9, SLOTS = 10;
    /** each slot's element (0 fire, 1 acid, 2 ice, 9 great) */
    public static final int[] ELEMENT = {FIRE, ACID, ICE, FIRE, FIRE, ACID, ACID, ICE, ICE, GREAT};
    /** the two heads each of the first three grows back as */
    private static final int[][] CHILDREN = {{3, 4}, {5, 6}, {7, 8}};
    /** where the heads are and the stumps (model pixels: its left, up from the ground, forward) at rest */
    private static final float[][] HEAD = {{-22.6F, 34.1F, 17.8F}, {0, 35.2F, 26.3F}, {21.0F, 36.2F, 16.8F}, {-25.7F, 34.1F, 10.5F},
            {-17.2F, 34.1F, 23.8F}, {-7.9F, 35.2F, 25.5F}, {7.9F, 35.2F, 25.5F}, {16.0F, 36.2F, 22.3F}, {23.8F, 36.2F, 10.1F},
            {0, 40.1F, 19.8F}};
    private static final float[][] STUMP = {{-12.3F, 18.5F, 11.4F}, {0, 18.5F, 17.0F}, {12.3F, 18.5F, 11.4F}, {-13.8F, 18.5F, 7.6F},
            {-9.4F, 18.5F, 14.4F}, {-4.7F, 18.5F, 16.7F}, {4.7F, 18.5F, 16.7F}, {9.4F, 18.5F, 14.4F}, {13.8F, 18.5F, 7.6F},
            {0, 21.9F, 7.6F}};
    /** blocks a model pixel is (the renderer draws it twice its size) */
    public static final float PX = 2.0F / 16;

    public enum Attack {
        NONE(0, 0), FIRE(40, 16), ACID(32, 10), ICE(36, 18), TAIL(40, 18), DIVE(70, 46), BREATHS(60, 20), MAW(80, 64), ROAR(30, 0);

        public final int length, impact;

        Attack(int length, int impact) {
            this.length = length;
            this.impact = impact;
        }
    }

    private static final byte EVENT_ATTACK = (byte) 200;             // + attack ordinal (the Barrow Chieftain 185+)
    private static final byte EVENT_FLINCH = (byte) 215, EVENT_REGROW = (byte) 216, EVENT_EMERGE = (byte) 219;   // regrow + 0..2

    public static final int WAKE_TICKS = 100, TRANSFORM_TICKS = 120, DEATH_TICKS = 100;
    private static final int TRANSFORM_UNDER = 50, TRANSFORM_WHOLE = 70, TRANSFORM_UP = 80;
    private static final double WAKE_RANGE = 14, AGGRO_RANGE = 44;
    private static final float BASE_HEALTH = 360, HEAD_HEALTH = 40;
    private static final int REGROW = 200;

    private static final float FIRE_DAMAGE = 14, FIRE_RADIUS = 10, FIRE_HALF = 22;
    private static final float ACID_DAMAGE = 5, ACID_RADIUS = 2.2F;
    private static final int ACID_FLIGHT = 10, ACID_POOL = 100;
    private static final float ICE_DAMAGE = 10, ICE_LENGTH = 16, ICE_HALF_WIDTH = 1.1F;
    private static final float WAVE_DAMAGE = 14, WAVE_SPEED = 0.55F, WAVE_WIDTH = 34, WAVE_REACH = 30;
    private static final float DIVE_DAMAGE = 20, DIVE_RADIUS = 4.2F;
    private static final int DIVE_DOWN = 20, DIVE_LOCK = 14, DIVE_EMERGE = 34;
    private static final float BREATH_DAMAGE = 8, BREATH_RADIUS = 13, BREATH_HALF = 12, BREATH_SPIN = 2.0F;
    private static final int BREATH_ACTIVE = 40;
    private static final float MAW_DAMAGE = 22, MAW_RADIUS = 6, MAW_HALF = 45, MAW_PULL = 0.17F, MAW_RANGE = 20;
    private static final int MAW_PULL_FROM = 8;

    private static final AttributeModifier ENRAGED = new AttributeModifier(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.divinis.Divinis.MODID, "hydra_enraged"),
            0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

    // client: animations
    public final AnimationState sleepAnimation = new AnimationState();
    public final AnimationState wakeAnimation = new AnimationState();
    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState fireAnimation = new AnimationState();
    public final AnimationState acidAnimation = new AnimationState();
    public final AnimationState iceAnimation = new AnimationState();
    public final AnimationState tailAnimation = new AnimationState();
    public final AnimationState diveAnimation = new AnimationState();
    public final AnimationState emergeAnimation = new AnimationState();
    public final AnimationState breathsAnimation = new AnimationState();
    public final AnimationState mawAnimation = new AnimationState();
    public final AnimationState roarAnimation = new AnimationState();
    public final AnimationState flinchAnimation = new AnimationState();
    public final AnimationState[] regrowAnimation = {new AnimationState(), new AnimationState(), new AnimationState()};
    public final AnimationState transformAnimation = new AnimationState();
    public final AnimationState deathAnimation = new AnimationState();
    public int dying;
    private int clientAttackTicks;

    // the heads' hit boxes
    private final HydraHead[] heads = new HydraHead[SLOTS];

    // server: the fight
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.GREEN,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private final BossTargeting targeting = new BossTargeting();
    private Attack attack = Attack.NONE, last = Attack.NONE;
    private int attackTick, cooldown = 20, stateTick;
    private boolean pendingTransform, unclamped;
    private float lockedYaw, spin;
    private float bodyShare = 0.6F;
    @Nullable private Vec3 home;
    private final float[] headHealth = new float[SLOTS];
    private final int[] regrowAt = new int[SLOTS];
    private final boolean[] spent = new boolean[SLOTS];   // a first head that has grown back double (its stump stays)
    private Vec3 aim = Vec3.ZERO;
    private final List<Vec3> spitAt = new ArrayList<>();

    private record Wave(Vec3 from, Vec3 dir, int start, Set<UUID> hit) {}

    private final List<Wave> waves = new ArrayList<>();

    public Hydra(EntityType<? extends Hydra> type, Level level) {
        super(type, level);
        for (int i = 0; i < SLOTS; i++) heads[i] = new HydraHead(this, i, i == GREAT ? 2.4F : 1.7F);
        setId(ENTITY_COUNTER.getAndAdd(SLOTS + 1) + 1); // the heads take the ids right after it
        this.xpReward = 300;
        setPersistenceRequired();
        bossBar.setVisible(true);
        java.util.Arrays.fill(regrowAt, -1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 44.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 16.0)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, SLEEPING);
        builder.define(PHASE, (byte) 1);
        builder.define(TARGET_ID, -1);
        builder.define(HEADS, 0b111);
        builder.define(UNDER, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < SLOTS; i++) if (heads[i] != null) heads[i].setId(id + i + 1);
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return heads;
    }

    public byte state() {
        return entityData.get(STATE);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    /** Is the head in a slot there? */
    public boolean alive(int slot) {
        return (entityData.get(HEADS) >> slot & 1) != 0;
    }

    /** Is a slot's stump shown (its head cut off)? */
    public boolean stump(int slot) {
        return (entityData.get(HEADS) >> (10 + slot) & 1) != 0;
    }

    /** Has a slot's stump been burned (it will not grow back)? */
    public boolean burned(int slot) {
        return (entityData.get(HEADS) >> (20 + slot) & 1) != 0;
    }

    private void setBits(int slot, boolean alive, boolean stump, boolean burned) {
        int v = entityData.get(HEADS);
        v = alive ? v | 1 << slot : v & ~(1 << slot);
        v = stump ? v | 1 << (10 + slot) : v & ~(1 << (10 + slot));
        v = burned ? v | 1 << (20 + slot) : v & ~(1 << (20 + slot));
        entityData.set(HEADS, v);
    }

    /** Is it under the water (diving, changing)? */
    public boolean under() {
        return entityData.get(UNDER);
    }

    public boolean isAttacking() {
        return clientAttackTicks > 0 || state() != AWAKE;
    }

    // ------------------------------------------------------------------ the heads' places

    /** A point of the model (pixels: left, up, forward) in the world, as it stands turned now. */
    public Vec3 modelPoint(float[] p) {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        return position().add(left.scale(p[0] * PX)).add(fwd.scale(p[2] * PX)).add(0, p[1] * PX, 0);
    }

    public Vec3 headAt(int slot) {
        return modelPoint(HEAD[slot]);
    }

    /** Can a slot's hit box be struck now (its head there, or its stump bleeding)? */
    boolean struckAt(int slot) {
        return state() == AWAKE && !under() && (alive(slot) || stump(slot) && !burned(slot) && regrowAt[slot] >= 0
                || level().isClientSide && stump(slot) && !burned(slot));
    }

    private void placeHeads() {
        for (int i = 0; i < SLOTS; i++) {
            HydraHead h = heads[i];
            Vec3 at = alive(i) || !stump(i) ? headAt(i) : modelPoint(STUMP[i]);
            h.xo = h.getX();
            h.yo = h.getY();
            h.zo = h.getZ();
            h.setPos(at.x, at.y - h.getBbHeight() / 2, at.z);
        }
    }

    // ------------------------------------------------------------------ client

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!level().isClientSide || !STATE.equals(key)) return;
        if (state() == WAKING) wakeAnimation.start(tickCount);
        else wakeAnimation.stop();
        if (state() == TRANSFORM) {
            stopAttacks();
            transformAnimation.start(tickCount);
        } else {
            transformAnimation.stop();
        }
    }

    private void stopAttacks() {
        for (AnimationState s : new AnimationState[] {fireAnimation, acidAnimation, iceAnimation, tailAnimation, diveAnimation,
                emergeAnimation, breathsAnimation, mawAnimation, roarAnimation}) s.stop();
    }

    @Override
    public void handleEntityEvent(byte id) {
        int u = id & 0xFF;
        if (u == (EVENT_FLINCH & 0xFF)) {
            flinchAnimation.start(tickCount);
            return;
        }
        if (u >= (EVENT_REGROW & 0xFF) && u < (EVENT_REGROW & 0xFF) + 3) {
            regrowAnimation[u - (EVENT_REGROW & 0xFF)].start(tickCount);
            return;
        }
        if (u == (EVENT_EMERGE & 0xFF)) {
            diveAnimation.stop();
            emergeAnimation.start(tickCount);
            return;
        }
        int index = u - (EVENT_ATTACK & 0xFF);
        if (index > 0 && index < Attack.values().length) {
            Attack a = Attack.values()[index];
            stopAttacks();
            (switch (a) {
                case FIRE -> fireAnimation;
                case ACID -> acidAnimation;
                case ICE -> iceAnimation;
                case TAIL -> tailAnimation;
                case DIVE -> diveAnimation;
                case BREATHS -> breathsAnimation;
                case MAW -> mawAnimation;
                default -> roarAnimation;
            }).start(tickCount);
            clientAttackTicks = a.length;
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        placeHeads();
        if (!level().isClientSide) return;
        if (isDeadOrDying()) {
            deathAnimation.startIfStopped(tickCount);
            return;
        }
        byte s = state();
        if (s == SLEEPING) sleepAnimation.startIfStopped(tickCount);
        else sleepAnimation.stop();
        if (s == AWAKE) idleAnimation.startIfStopped(tickCount);
        else idleAnimation.stop();
        if (clientAttackTicks > 0) clientAttackTicks--;
        // a burning stump smoulders; a bleeding one bleeds, flames dancing round it (burn it!)
        if (s == AWAKE && tickCount % 3 == 0) {
            for (int i = 0; i < SLOTS; i++) {
                if (!stump(i)) continue;
                Vec3 p = modelPoint(STUMP[i]);
                if (burned(i)) {
                    level().addParticle(ParticleTypes.SMOKE, p.x, p.y + 0.2, p.z, 0, 0.03, 0);
                } else {
                    level().addParticle(ModParticles.HYDRA_BLOOD.get(), p.x, p.y + 0.1, p.z, (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
                    double a = tickCount * 0.3 + i;
                    level().addParticle(ParticleTypes.SMALL_FLAME, p.x + Math.cos(a) * 0.6, p.y + 0.3, p.z + Math.sin(a) * 0.6, 0, 0.01, 0);
                }
            }
        }
        // the heads' breath: fire, acid and frost curling from their jaws
        if (s == AWAKE && tickCount % 8 == 0) {
            for (int i = 0; i < SLOTS; i++) {
                if (!alive(i)) continue;
                Vec3 p = headAt(i).add(0, -0.3, 0);
                ParticleOptions o = switch (ELEMENT[i]) {
                    case FIRE -> ParticleTypes.SMALL_FLAME;
                    case ACID -> ModParticles.VENOM.get();
                    case ICE -> ModParticles.FROST.get();
                    default -> ParticleTypes.SMOKE;
                };
                level().addParticle(o, p.x, p.y, p.z, 0, 0.01, 0);
            }
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(5.0, 4.0, 5.0); // the necks
    }

    // ------------------------------------------------------------------ server: states

    private Vec3 home() {
        if (home == null) home = position();
        return home;
    }

    private float scaleHealth() {
        return getMaxHealth() / BASE_HEALTH;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (tickCount % 10 == 0) updateBossBar(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        home();
        tickWaves(level);
        regrow(level);
        switch (state()) {
            case SLEEPING -> {
                getNavigation().stop();
                if (tickCount % 10 == 0 && nearestPlayer(WAKE_RANGE) != null) wakeUp();
            }
            case WAKING -> waking(level);
            case TRANSFORM -> transforming(level);
            default -> fight(level);
        }
    }

    private void wakeUp() {
        if (state() != SLEEPING) return;
        entityData.set(STATE, WAKING);
        stateTick = 0;
        for (int i = 0; i < 3; i++) headHealth[i] = HEAD_HEALTH * scaleHealth();
        playSound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 3.0F, 0.5F);
    }

    private void waking(ServerLevel level) {
        getNavigation().stop();
        stateTick++;
        int t = stateTick;
        if (t < 30 && t % 4 == 0) splash(level, position(), 12, 2.0);
        if (t == 30 || t == 46 || t == 62) { // a head coming up
            int slot = (t - 30) / 16;
            Vec3 p = headAt(slot);
            splash(level, new Vec3(p.x, getY() + 0.5, p.z), 20, 0.8);
            playSound(SoundEvents.PLAYER_SPLASH, 2.5F, 0.6F);
            playSound(slot == 0 ? SoundEvents.BLAZE_AMBIENT : slot == 1 ? SoundEvents.SLIME_SQUISH : SoundEvents.GLASS_BREAK, 2.5F, 0.5F);
        }
        if (t == 80) scream(level, 0.6F);
        if (t >= WAKE_TICKS) {
            entityData.set(STATE, AWAKE);
            cooldown = 20;
        }
    }

    private void updateBossBar(ServerLevel level) {
        for (ServerPlayer p : List.copyOf(bossBar.getPlayers())) {
            if (state() == SLEEPING || !p.isAlive() || p.level() != level || p.distanceToSqr(this) > 64 * 64) bossBar.removePlayer(p);
        }
        if (state() == SLEEPING) return;
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
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ cutting, burning, growing back

    /** HydraHead: a blow on a head (or a stump). */
    boolean hurtHead(int slot, DamageSource source, float amount) {
        if (!struckAt(slot) || level().isClientSide) return false;
        boolean fire = source.is(DamageTypeTags.IS_FIRE) || source.getDirectEntity() != null && source.getDirectEntity().isOnFire();
        if (!alive(slot)) { // a bleeding stump: fire burns it shut
            if (fire) burn(slot);
            bodyShare = 0.3F;
            return hurt(source, amount);
        }
        bodyShare = slot == GREAT ? 1.25F : 1.0F;
        boolean hurt = hurt(source, amount);
        if (hurt && slot != GREAT) {
            headHealth[slot] -= amount;
            if (headHealth[slot] <= 0) cut(slot);
        }
        return hurt;
    }

    /** HydraHead: used on with something that burns. */
    InteractionResult burnWith(int slot, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean burns = stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH) || stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE);
        if (!burns || alive(slot) || !stump(slot) || burned(slot) || regrowAt[slot] < 0) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (stack.is(Items.FLINT_AND_STEEL)) stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        else if (stack.is(Items.FIRE_CHARGE) && !player.getAbilities().instabuild) stack.shrink(1);
        burn(slot);
        player.swing(hand, true);
        return InteractionResult.CONSUME;
    }

    private void cut(int slot) {
        ServerLevel level = (ServerLevel) level();
        headHealth[slot] = 0;
        boolean grows = slot < 3 && !spent[slot];
        regrowAt[slot] = grows ? tickCount + REGROW : -1;
        setBits(slot, false, true, !grows);   // heads that grew back do not grow again: their stumps close at once
        Vec3 p = headAt(slot);
        level.sendParticles(ModParticles.HYDRA_BLOOD.get(), p.x, p.y, p.z, 40, 0.5, 0.5, 0.5, 0.15);
        level.sendParticles(ModParticles.BOG_SPLASH.get(), p.x, getY() + 0.5, p.z, 20, 0.8, 0.2, 0.8, 0.1);
        playSound(SoundEvents.PLAYER_ATTACK_CRIT, 3.0F, 0.5F);
        playSound(SoundEvents.RAVAGER_HURT, 3.0F, 0.6F);
        playSound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 2.0F, 0.7F);
        level.broadcastEntityEvent(this, EVENT_FLINCH);
        ScreenFx.shake(level, position(), 24, 6, 0.35F);
        if (grows) say(level, "Burn the stump!", 0xFFB040);
    }

    private void burn(int slot) {
        ServerLevel level = (ServerLevel) level();
        regrowAt[slot] = -1;
        setBits(slot, false, true, true);
        Vec3 p = modelPoint(STUMP[slot]);
        level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 30, 0.4, 0.3, 0.4, 0.06);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 15, 0.3, 0.3, 0.3, 0.03);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.6F);
        playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 1.4F);
    }

    /** Words across the screen of everyone near. */
    private void say(ServerLevel level, String text, int color) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < 40 * 40) ScreenFx.play(p, ScreenEffect.TEXT, 45, 0.8F, color, text);
        }
    }

    /** Stumps not burned in time: a first head grows back as two. */
    private void regrow(ServerLevel level) {
        for (int i = 0; i < 3; i++) {
            if (regrowAt[i] < 0 || tickCount < regrowAt[i]) continue;
            regrowAt[i] = -1;
            spent[i] = true;
            setBits(i, false, true, true);
            if (state() != AWAKE || under()) continue;
            for (int c : CHILDREN[i]) {
                setBits(c, true, false, false);
                headHealth[c] = HEAD_HEALTH * 0.85F * scaleHealth();
            }
            level.broadcastEntityEvent(this, (byte) (EVENT_REGROW + i));
            Vec3 p = modelPoint(STUMP[i]);
            level.sendParticles(ModParticles.HYDRA_BLOOD.get(), p.x, p.y, p.z, 30, 0.5, 0.6, 0.5, 0.12);
            playSound(SoundEvents.SLIME_BLOCK_BREAK, 3.0F, 0.5F);
            playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 1.1F);
            say(level, "It grew back double!", 0xC0FF60);
        }
    }

    /** The heads as they are at the start (and after the change): the first three whole, no other, no cuts. */
    private void wholeHeads(boolean great) {
        int v = 0b111 | (great ? 1 << GREAT : 0);
        entityData.set(HEADS, v);
        java.util.Arrays.fill(regrowAt, -1);
        java.util.Arrays.fill(spent, false);
        for (int i = 0; i < SLOTS; i++) headHealth[i] = (i < 3 ? HEAD_HEALTH : 0) * scaleHealth();
    }

    private boolean has(int element) {
        for (int i = 0; i < SLOTS; i++) if (alive(i) && ELEMENT[i] == element) return true;
        return false;
    }

    private List<Integer> slotsOf(int element) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) if (alive(i) && ELEMENT[i] == element) out.add(i);
        return out;
    }

    // ------------------------------------------------------------------ server: the change

    private void startTransform(ServerLevel level) {
        pendingTransform = false;
        attack = Attack.NONE;
        waves.clear();
        Telegraphs.cancel(this);
        getNavigation().stop();
        entityData.set(STATE, TRANSFORM);
        stateTick = 0;
        BossDeath.on(this, Anchor.EXCEPT, 0, ScreenEffect.DESATURATE, TRANSFORM_TICKS - 10, 1.0F, -1);
        ScreenFx.playNear(level, position(), 40, ScreenEffect.FOG, TRANSFORM_TICKS, 0.6F, 0x3E5A26);
    }

    private void transforming(ServerLevel level) {
        stateTick++;
        int t = stateTick;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (t == 6) scream(level, 0.6F);
        if (t >= 30 && t <= TRANSFORM_UNDER && t % 3 == 0) {
            splash(level, position(), 16, 2.2);
            playSound(SoundEvents.PLAYER_SPLASH, 2.5F, 0.5F);
        }
        if (t == TRANSFORM_UNDER) entityData.set(UNDER, true);
        if (t > TRANSFORM_UNDER && t < TRANSFORM_UP && t % 4 == 0) {
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 0.3, getZ(), 12, 2.0, 0.1, 2.0, 0.05);
            ScreenFx.shake(level, position(), 24, 4, 0.2F + (t - TRANSFORM_UNDER) * 0.01F);
        }
        if (t == TRANSFORM_WHOLE) {
            wholeHeads(true);
            entityData.set(PHASE, (byte) 2);
        }
        if (t == TRANSFORM_UP) {
            entityData.set(UNDER, false);
            splash(level, position(), 80, 3.0);
            playSound(SoundEvents.GENERIC_SPLASH, 4.0F, 0.4F);
            ScreenFx.shake(level, position(), 32, 14, 0.7F);
        }
        if (t == 108) scream(level, 0.9F);
        if (t >= TRANSFORM_TICKS) {
            entityData.set(STATE, AWAKE);
            cooldown = 16;
        }
    }

    // ------------------------------------------------------------------ server: the fight

    private boolean enraged() {
        return phase() == 2 && getHealth() < getMaxHealth() * 0.4F;
    }

    @Nullable
    private Player nearestPlayer(double range) {
        Player best = null;
        double bestD = range * range;
        for (Player p : level().players()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            double d = p.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private boolean validTarget(@Nullable LivingEntity t) {
        return t != null && t.isAlive() && t.level() == level() && t.distanceToSqr(this) < AGGRO_RANGE * AGGRO_RANGE
                && !(t instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private List<Player> fighters() {
        List<Player> out = new ArrayList<>();
        for (Player p : level().players()) if (validTarget(p)) out.add(p);
        return out;
    }

    private void fight(ServerLevel level) {
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (enraged() && speed != null && !speed.hasModifier(ENRAGED.id())) {
            speed.addTransientModifier(ENRAGED);
            if (attack == Attack.NONE) start(level, Attack.ROAR, getTarget());
        }
        if (attack == Attack.NONE && pendingTransform) {
            startTransform(level);
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
        getLookControl().setLookAt(target, 20, 20);
        double dist = Math.sqrt(distanceToSqr(target.getX(), getY(), target.getZ()));
        if (cooldown > 0) cooldown--;
        if (cooldown <= 0 && dist < 30) {
            start(level, choose(dist), target);
            return;
        }
        if (dist > 9) {
            if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
        }
    }

    private Attack choose(double dist) {
        List<Attack> options = new ArrayList<>();
        if (has(FIRE) && dist < 12) options.add(Attack.FIRE);
        if (has(ACID)) options.add(Attack.ACID);
        if (has(ICE)) options.add(Attack.ICE);
        if (phase() == 2) {
            options.add(Attack.DIVE);
            options.add(Attack.TAIL);
            if (has(FIRE) || has(ACID) || has(ICE)) options.add(Attack.BREATHS);
            options.add(Attack.MAW);
        }
        if (options.isEmpty()) options.add(Attack.TAIL); // no heads left: the tail
        if (options.size() > 1) options.removeIf(a -> a == last);
        return options.get(random.nextInt(options.size()));
    }

    /** /bossattack: starts an attack by name now, if it can. */
    public boolean forceAttack(String name) {
        if (!(level() instanceof ServerLevel level) || state() != AWAKE || attack != Attack.NONE) return false;
        if (name.equals("sink")) {
            if (phase() != 1) return false;
            startTransform(level);
            return true;
        }
        if (name.startsWith("cut")) {
            int slot = Mth.clamp(name.length() > 3 ? name.charAt(3) - '0' : 0, 0, 8);
            if (!alive(slot)) return false;
            cut(slot);
            return true;
        }
        Attack a = switch (name) {
            case "fire" -> Attack.FIRE;
            case "acid" -> Attack.ACID;
            case "ice" -> Attack.ICE;
            case "tail" -> Attack.TAIL;
            case "dive" -> Attack.DIVE;
            case "breaths" -> Attack.BREATHS;
            case "maw" -> Attack.MAW;
            default -> Attack.NONE;
        };
        LivingEntity target = getTarget() != null ? getTarget() : nearestPlayer(AGGRO_RANGE);
        if (a == Attack.NONE || target == null) return false;
        start(level, a, target);
        return true;
    }

    private void lockYaw() {
        setYRot(lockedYaw);
        yRotO = lockedYaw;
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
    }

    private static Vec3 forward(float yaw) {
        return new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** The ground under a head, and the way it faces (out from the body). */
    private Vec3 groundUnder(int slot) {
        Vec3 h = headAt(slot);
        return new Vec3(h.x, getY(), h.z);
    }

    private float headYaw(int slot) {
        return yawTo(position(), groundUnder(slot));
    }

    private void start(ServerLevel level, Attack a, @Nullable LivingEntity target) {
        attack = a;
        last = a;
        attackTick = 0;
        getNavigation().stop();
        lockedYaw = target != null ? yawTo(position(), target.position()) : getYRot();
        lockYaw();
        level.broadcastEntityEvent(this, (byte) (EVENT_ATTACK + a.ordinal()));
        Vec3 at = position();
        switch (a) {
            case FIRE -> {
                for (int s : slotsOf(FIRE)) {
                    Telegraphs.show(level, Telegraph.sector(groundUnder(s), headYaw(s), FIRE_RADIUS, FIRE_HALF), a.impact, 6, this, e -> {
                        hit(e, FIRE_DAMAGE, 0.6, 0.3);
                        e.igniteForSeconds(3);
                    });
                }
                playSound(SoundEvents.BLAZE_AMBIENT, 3.0F, 0.5F);
            }
            case ACID -> {
                spitAt.clear();
                List<Player> players = fighters();
                int n = slotsOf(ACID).size() * 3;
                for (int i = 0; i < n; i++) {
                    Vec3 p = players.isEmpty() ? at.add(forward(lockedYaw).scale(6 + i)) : players.get(i % players.size()).position();
                    if (i >= players.size()) p = p.add((random.nextDouble() - 0.5) * 6, 0, (random.nextDouble() - 0.5) * 6);
                    spitAt.add(p);
                    int warm = (i % 3 == 0 ? 10 : i % 3 == 1 ? 16 : 22) + ACID_FLIGHT;
                    Telegraphs.sweep(level, Telegraph.circle(p, ACID_RADIUS), warm, ACID_POOL, 0, 15, this, e -> {
                        hit(e, ACID_DAMAGE, 0, 0);
                        e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), this);
                    });
                }
                playSound(SoundEvents.SLIME_SQUISH, 3.0F, 0.5F);
            }
            case ICE -> {
                for (int s : slotsOf(ICE)) {
                    Vec3 g = groundUnder(s);
                    float yaw = target != null ? yawTo(g, target.position()) : headYaw(s);
                    Telegraphs.show(level, Telegraph.rect(g, yaw, ICE_LENGTH, ICE_HALF_WIDTH), a.impact, 6, this, e -> {
                        hit(e, ICE_DAMAGE, 0.3, 0.1);
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), this);
                        e.setTicksFrozen(Math.max(e.getTicksFrozen(), 180));
                    });
                }
                playSound(SoundEvents.GLASS_BREAK, 3.0F, 0.5F);
            }
            case TAIL -> playSound(SoundEvents.RAVAGER_ATTACK, 3.0F, 0.5F);
            case DIVE -> {
                entityData.set(UNDER, false);
                playSound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 3.0F, 0.5F);
            }
            case BREATHS -> {
                spin = random.nextBoolean() ? BREATH_SPIN : -BREATH_SPIN;
                int[] els = {FIRE, ACID, ICE};
                for (int k = 0; k < 3; k++) {
                    int el = els[k];
                    if (!has(el)) continue;
                    Telegraphs.sweep(level, Telegraph.sector(at, lockedYaw + k * 120, BREATH_RADIUS, BREATH_HALF), a.impact, BREATH_ACTIVE, spin, 8, this,
                            e -> {
                                hit(e, BREATH_DAMAGE, 0.3, 0.1);
                                if (el == FIRE) e.igniteForSeconds(3);
                                else if (el == ACID) e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), this);
                                else e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
                            });
                }
                playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.7F);
            }
            case MAW -> {
                Telegraphs.show(level, Telegraph.sector(at, lockedYaw, MAW_RADIUS, MAW_HALF), a.impact, 6, this, e -> hit(e, MAW_DAMAGE, 1.6, 0.6));
                playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.4F);
                BossDeath.on(this, Anchor.AREA, (float) MAW_RANGE, ScreenEffect.DISTORT, a.impact - MAW_PULL_FROM, 0.5F, -1);
            }
            case ROAR -> scream(level, 0.8F);
            default -> {}
        }
    }

    private void attackTick(ServerLevel level, @Nullable LivingEntity target) {
        attackTick++;
        int t = attackTick;
        if (attack != Attack.BREATHS && attack != Attack.DIVE) lockYaw();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        switch (attack) {
            case FIRE -> {
                if (t >= attack.impact && t < attack.impact + 12) {
                    for (int s : slotsOf(FIRE)) breathe(level, s, headYaw(s), FIRE, FIRE_RADIUS);
                }
                if (t == attack.impact) playSound(SoundEvents.BLAZE_SHOOT, 3.0F, 0.5F);
            }
            case ACID -> {
                for (int i = 0; i < spitAt.size(); i++) {
                    int when = i % 3 == 0 ? 10 : i % 3 == 1 ? 16 : 22;
                    List<Integer> mouths = slotsOf(ACID);
                    if (t == when && !mouths.isEmpty()) {
                        Vec3 from = headAt(mouths.get(i / 3 % mouths.size()));
                        Vec3 to = spitAt.get(i);
                        Vec3 v = to.subtract(from);
                        for (int k = 0; k < 5; k++) { // a gob of acid arcing over
                            level.sendParticles(ModParticles.ACID.get(), from.x, from.y, from.z, 0, v.x / ACID_FLIGHT * (0.9 + k * 0.05),
                                    0.35 + k * 0.05, v.z / ACID_FLIGHT * (0.9 + k * 0.05), 1.0);
                        }
                        playSound(SoundEvents.LLAMA_SPIT, 2.0F, 0.6F);
                    }
                    if (t == when + ACID_FLIGHT) {
                        Vec3 p = spitAt.get(i);
                        level.sendParticles(ModParticles.ACID_SPLASH.get(), p.x, p.y + 0.1, p.z, 6, 1.0, 0, 1.0, 0);
                        level.sendParticles(ModParticles.VENOM.get(), p.x, p.y + 0.2, p.z, 8, 1.0, 0.1, 1.0, 0.01);
                        level.playSound(null, p.x, p.y, p.z, SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 1.2F);
                    }
                }
            }
            case ICE -> {
                if (t >= attack.impact && t < attack.impact + 8) {
                    for (int s : slotsOf(ICE)) {
                        Vec3 g = groundUnder(s);
                        float yaw = target != null ? yawTo(g, target.position()) : headYaw(s);
                        breathe(level, s, yaw, ICE, ICE_LENGTH);
                    }
                }
                if (t == attack.impact) {
                    playSound(SoundEvents.POWDER_SNOW_BREAK, 3.0F, 0.5F);
                    playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.7F);
                }
            }
            case TAIL -> {
                if (t == attack.impact) slam(level, target);
                if (t == attack.impact + 20 && enraged()) slam(level, target);
            }
            case DIVE -> diveTick(level, target, t);
            case BREATHS -> {
                if (t > attack.impact && t <= attack.impact + BREATH_ACTIVE) {
                    lockedYaw += spin;
                    int[] els = {FIRE, ACID, ICE};
                    for (int k = 0; k < 3; k++) {
                        if (!has(els[k])) continue;
                        breathe(level, slotsOf(els[k]).get(0), lockedYaw + k * 120, els[k], BREATH_RADIUS);
                    }
                    if (t % 10 == 0) playSound(SoundEvents.BLAZE_SHOOT, 2.5F, 0.5F);
                }
                lockYaw();
            }
            case MAW -> {
                if (t >= MAW_PULL_FROM && t < attack.impact - 4) { // drawn in: walk, or better run, against it
                    Vec3 mouth = headAt(GREAT);
                    Vec3 floorMouth = new Vec3(mouth.x, getY(), mouth.z);
                    if (t % 3 == 0) {
                        for (Player p : fighters()) {
                            Vec3 to = floorMouth.subtract(p.position()).multiply(1, 0, 1);
                            double d = to.length();
                            if (d < 0.8 || d > MAW_RANGE) continue;
                            p.setDeltaMovement(to.scale(MAW_PULL / d).add(0, p.getDeltaMovement().y, 0));
                            p.hurtMarked = true;
                        }
                    }
                    for (int i = 0; i < 4; i++) { // the air rushing in
                        double ang = random.nextDouble() * Mth.TWO_PI, r = 4 + random.nextDouble() * 10;
                        Vec3 from = floorMouth.add(Math.cos(ang) * r, 0.5 + random.nextDouble() * 2, Math.sin(ang) * r);
                        Vec3 v = mouth.subtract(from).scale(0.08);
                        level.sendParticles(ModParticles.BOG_SPLASH.get(), from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
                    }
                    if (t % 12 == 0) playSound(SoundEvents.ELYTRA_FLYING, 2.0F, 0.5F);
                }
                if (t == attack.impact) {
                    playSound(SoundEvents.EVOKER_FANGS_ATTACK, 3.0F, 0.5F);
                    playSound(SoundEvents.RAVAGER_ATTACK, 3.0F, 0.4F);
                    ScreenFx.shake(level, position(), 24, 10, 0.6F);
                    Vec3 m = position().add(forward(lockedYaw).scale(3));
                    splash(level, m, 30, 2.0);
                }
            }
            default -> {}
        }
        if (t >= attack.length) {
            entityData.set(UNDER, false);
            attack = Attack.NONE;
            cooldown = enraged() ? 14 + random.nextInt(12) : (phase() == 2 ? 24 : 32) + random.nextInt(14);
        }
    }

    /** Its breath out of a head along a direction, for as far as it reaches: fire, acid or frost. */
    private void breathe(ServerLevel level, int slot, float yaw, int element, float reach) {
        Vec3 from = headAt(slot).add(0, -0.4, 0);
        Vec3 dir = forward(yaw);
        Vec3 ground = new Vec3(from.x, getY() + 0.4, from.z).add(dir.scale(reach));
        Vec3 v = ground.subtract(from).scale(1.0 / 14);
        ParticleOptions a = element == FIRE ? ParticleTypes.FLAME : element == ACID ? ModParticles.ACID.get() : ModParticles.FROST.get();
        ParticleOptions b = element == FIRE ? ParticleTypes.LARGE_SMOKE : element == ACID ? ModParticles.VENOM.get() : ParticleTypes.SNOWFLAKE;
        for (int i = 0; i < 5; i++) {
            double sx = (random.nextDouble() - 0.5) * 0.12, sz = (random.nextDouble() - 0.5) * 0.12;
            level.sendParticles(a, from.x, from.y, from.z, 0, v.x + sx, v.y, v.z + sz, 1.0);
        }
        Vec3 mid = from.lerp(ground, 0.5 + random.nextDouble() * 0.5);
        level.sendParticles(b, mid.x, mid.y, mid.z, 2, 0.4, 0.3, 0.4, 0.01);
    }

    // ------------------------------------------------------------------ the wave

    /** The tail slams down: a wave rolls out across the whole bog towards the target. */
    private void slam(ServerLevel level, @Nullable LivingEntity target) {
        Vec3 dir = target != null ? target.position().subtract(position()).multiply(1, 0, 1).normalize() : forward(lockedYaw);
        if (dir.lengthSqr() < 1e-4) dir = forward(lockedYaw);
        waves.add(new Wave(position(), dir, tickCount, new HashSet<>()));
        playSound(SoundEvents.GENERIC_SPLASH, 4.0F, 0.5F);
        playSound(SoundEvents.RAVAGER_STEP, 3.0F, 0.5F);
        ScreenFx.shake(level, position(), 30, 10, 0.5F);
        splash(level, position().subtract(dir.scale(4)), 40, 2.0);
    }

    /** The waves rolling on: whoever is on the ground where one passes is hit (jump it). */
    private void tickWaves(ServerLevel level) {
        if (waves.isEmpty()) return;
        waves.removeIf(w -> (tickCount - w.start()) * WAVE_SPEED > WAVE_REACH);
        for (Wave w : waves) {
            double front = 2.5 + (tickCount - w.start()) * WAVE_SPEED;
            Vec3 side = new Vec3(-w.dir().z, 0, w.dir().x);
            Vec3 mid = w.from().add(w.dir().scale(front));
            for (double s = -WAVE_WIDTH / 2; s <= WAVE_WIDTH / 2; s += 1.5) { // the wave front: a wall of thrown-up bog
                Vec3 p = mid.add(side.scale(s));
                level.sendParticles(ModParticles.BOG_SPLASH.get(), p.x, p.y + 0.3, p.z, 1, 0.3, 0.15, 0.3, 0.02);
                if (random.nextInt(3) == 0) level.sendParticles(ParticleTypes.SPLASH, p.x, p.y + 0.5, p.z, 2, 0.3, 0.1, 0.3, 0.1);
            }
            if ((tickCount - w.start()) % 6 == 0) {
                level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.PLAYER_SPLASH, net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.6F);
            }
            for (Player p : fighters()) {
                if (w.hit().contains(p.getUUID())) continue;
                Vec3 rel = p.position().subtract(w.from());
                double along = rel.x * w.dir().x + rel.z * w.dir().z, across = rel.x * side.x + rel.z * side.z;
                if (Math.abs(along - front) > 0.9 || Math.abs(across) > WAVE_WIDTH / 2) continue;
                if (!p.onGround() || p.getY() > w.from().y + 0.6) continue; // jumped it
                w.hit().add(p.getUUID());
                if (p.hurt(damageSources().mobAttack(this), WAVE_DAMAGE * damageScale())) {
                    p.setDeltaMovement(p.getDeltaMovement().add(w.dir().x * 0.9, 0.5, w.dir().z * 0.9));
                    p.hurtMarked = true;
                }
            }
        }
    }

    // ------------------------------------------------------------------ the dive

    private void diveTick(ServerLevel level, @Nullable LivingEntity target, int t) {
        if (t < DIVE_DOWN && t % 3 == 0) splash(level, position(), 14, 2.0);
        if (t == DIVE_LOCK) { // where it will come up: under its target
            aim = target != null ? target.position() : position();
            Telegraphs.show(level, Telegraph.circle(aim, DIVE_RADIUS), Attack.DIVE.impact - DIVE_LOCK, 6, this, e -> hit(e, DIVE_DAMAGE, 1.2, 1.0));
        }
        if (t == DIVE_DOWN) {
            entityData.set(UNDER, true);
            playSound(SoundEvents.GENERIC_SPLASH, 3.0F, 0.5F);
        }
        if (t > DIVE_DOWN && t < Attack.DIVE.impact) { // moving under the bog: a trail of bubbles
            Vec3 p = position().lerp(aim, Math.min(1, (t - DIVE_DOWN) / (double) (DIVE_EMERGE - DIVE_DOWN)));
            setPos(p.x, aim.y, p.z);
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, p.x, p.y + 0.2, p.z, 6, 1.2, 0.1, 1.2, 0.05);
            if (t % 5 == 0) ScreenFx.shake(level, p, 16, 4, 0.2F);
            lockedYaw = target != null ? yawTo(position(), target.position()) : lockedYaw;
            lockYaw();
        }
        if (t == DIVE_EMERGE) level.broadcastEntityEvent(this, EVENT_EMERGE);
        if (t == Attack.DIVE.impact) {
            entityData.set(UNDER, false);
            splash(level, position(), 90, 3.2);
            playSound(SoundEvents.GENERIC_SPLASH, 4.0F, 0.4F);
            playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.7F);
            ScreenFx.shake(level, position(), 30, 14, 0.8F);
            ScreenFx.playNear(level, position(), DIVE_RADIUS + 3, ScreenEffect.FLASH, 5, 0.25F, 0xC8E0B0);
        }
    }

    // ------------------------------------------------------------------ hits and effects

    float damageScale() {
        var attr = getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null || attr.getBaseValue() <= 0 ? 1.0F : (float) (attr.getValue() / attr.getBaseValue());
    }

    private void hit(LivingEntity e, float damage, double push, double up) {
        if (!e.hurt(damageSources().mobAttack(this), damage * damageScale())) return;
        if (push <= 0 && up <= 0) return;
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resist <= 0) return;
        e.setDeltaMovement(e.getDeltaMovement().add(dx / len * push * resist, up * resist, dz / len * push * resist));
        e.hurtMarked = true;
    }

    /** The bog thrown up round a point. */
    private void splash(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ModParticles.BOG_SPLASH.get(), at.x, at.y + 0.3, at.z, count, spread, 0.3, spread, 0.12);
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, count / 2, spread, 0.2, spread, 0.2);
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (!ground.isAir() && !ground.is(Blocks.WATER)) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.2, at.z, count / 3, spread, 0.2, spread, 0.15);
        }
    }

    /** Every head roars: the screen shakes, bog-green at the edges. */
    private void scream(ServerLevel level, float strength) {
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.8F);
        playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.6F);
        ScreenFx.shake(level, position(), 32, 14, strength);
        ScreenFx.playNear(level, position(), 32, ScreenEffect.VIGNETTE, 30, strength, 0x2E4A18);
        ScreenFx.playNear(level, position(), 20, ScreenEffect.FOV, 16, strength * 0.6F, -1);
    }

    // ------------------------------------------------------------------ dying

    @Override
    protected void tickDeath() {
        dying++;
        if (!(level() instanceof ServerLevel level)) return;
        int t = dying;
        if (t == 1) {
            BossDeath.sound(this, SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.5F);
            BossDeath.on(this, Anchor.ENTITY, 0, ScreenEffect.OUTLINE, 60, 1.0F, 0x80C040);
            Telegraphs.cancel(this);
            waves.clear();
        }
        int[] order = {0, 3, 5, 1, 7, 4, 6, 2, 8, 9};
        for (int n = 0; n < order.length; n++) { // the heads falling into the water
            if (t == 20 + n * 4 + 9 && (alive(order[n]) || order[n] == GREAT && phase() == 2)) {
                Vec3 p = headAt(order[n]);
                splash(level, new Vec3(p.x, getY(), p.z), 20, 0.8);
                BossDeath.sound(this, SoundEvents.PLAYER_SPLASH, 2.0F, 0.6F + n * 0.04F);
            }
        }
        if (t > 50 && t % 4 == 0) { // the body sinking
            splash(level, position(), 10, 2.0);
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 0.3, getZ(), 10, 2.0, 0.1, 2.0, 0.05);
        }
        if (t == DEATH_TICKS) {
            BossDeath.sound(this, SoundEvents.GENERIC_SPLASH, 4.0F, 0.4F);
            BossDeath.screen(this, ScreenEffect.FLASH, 6, 0.25F, 0x9CC060);
            splash(level, position(), 100, 3.0);
            level.sendParticles(ModParticles.HYDRA_BLOOD.get(), getX(), getY() + 0.5, getZ(), 40, 2.0, 0.4, 2.0, 0.05);
        }
        if (t >= DEATH_TICKS) remove(RemovalReason.KILLED);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) Telegraphs.cancel(this);
        super.die(source);
    }

    // ------------------------------------------------------------------ damage, sounds, saving

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (!level().isClientSide) entityData.set(TARGET_ID, target == null ? -1 : target.getId());
    }

    @Override
    public int markedTargetId() {
        return state() == SLEEPING || !isAlive() ? -1 : entityData.get(TARGET_ID);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            unclamped = true;
            try {
                return super.hurt(source, amount);
            } finally {
                unclamped = false;
            }
        }
        float share = bodyShare;
        bodyShare = 0.6F; // a blow on the body itself (the heads set their own share just before)
        if (state() == WAKING || state() == TRANSFORM || under()) return false;
        if (state() == SLEEPING) {
            if (!level().isClientSide) wakeUp();
            return false;
        }
        boolean hurt = super.hurt(source, amount * share);
        if (hurt && !level().isClientSide) targeting.onHurt(source, level().getGameTime());
        return hurt;
    }

    /** In its first phase no blow takes it below half its health: it changes once the attack under way is over. */
    @Override
    public void setHealth(float health) {
        if (!unclamped && tickCount > 0 && !level().isClientSide && phase() == 1 && health < getMaxHealth() * 0.5F) {
            health = getMaxHealth() * 0.5F;
            pendingTransform = true;
        }
        super.setHealth(health);
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
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(MobEffects.POISON) && !effect.is(MobEffects.MOVEMENT_SLOWDOWN) && super.canBeAffected(effect);
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return state() == AWAKE ? SoundEvents.RAVAGER_AMBIENT : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDER_DRAGON_GROWL;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.PLAYER_SPLASH, 0.8F, 0.5F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Awake", state() != SLEEPING);
        tag.putByte("Phase", (byte) phase());
        Vec3 h = home();
        tag.putDouble("HomeX", h.x);
        tag.putDouble("HomeY", h.y);
        tag.putDouble("HomeZ", h.z);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeY")) home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        int phase = Math.max(1, tag.getByte("Phase"));
        entityData.set(PHASE, (byte) phase);
        boolean awake = tag.getBoolean("Awake");
        entityData.set(STATE, awake ? AWAKE : SLEEPING);
        wholeHeads(phase == 2); // a fight cut off starts its heads whole again
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    @Override
    public void setCustomName(@Nullable net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }
}
