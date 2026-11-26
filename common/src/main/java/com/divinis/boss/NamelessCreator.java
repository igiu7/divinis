package com.divinis.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.divinis.net.CreatorFxPayload;
import com.divinis.net.FireFxPayload;
import com.divinis.registry.ModParticles;
import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.divinis.platform.net.PacketDistributor;

/**
 * The Nameless Creator (/summon divinis:nameless_creator in the middle of its arena - its home). Each phase peels one
 * layer off the god; for now its first phase is fought, and it changes into the second (whose fight comes later).
 * Its name is on purpose in no language file: the boss bar shows the raw key - a name that does not exist in the
 * game's files.
 *
 * At first there is nothing. When a player comes near, the sun and the clouds slow to a stop (client.CreatorSky) and it
 * is let down out of the sky on strings, folded flat like a stage prop, and opens like a pop-up book (8 s).
 * Phase 1, Majesty - it floats over its home, turned to its target, and:
 * - Starburst: its rose window brightens and a fan of thin lines is drawn from it to the target; stars fly along them,
 *   faster and faster - three volleys, each turned a little; those that miss break up after three seconds;
 * - Dimensional cuts: a thin white plane across the arena, then it opens and cuts all along - three or four, one after
 *   another; then five at once in a pentagram round the target: safe only inside its pentagon, or out beyond it;
 * - Ring of stars: 24 stars in a ring round the target, a gap in it marked by golden light; the ring closes in over two
 *   seconds, turning (the gap moves), and bursts in the middle;
 * - Camellia bursts: flowers fall from its crown near the target, a circle of petals on the ground under each; they
 *   grow, and burst one after another into spirals of petals;
 * - Collapsing star: it raises its hands, panes of light are struck into the floor round the arena and a star grows
 *   over it, pulling everyone in and throwing sparks out in a spiral; it falls in on itself to a point, silence - and
 *   goes off over the whole arena: only those behind a pane live through it unhurt;
 * - The cartographer: the map that is its face marks places in red; 25 ticks later columns of light fall on them -
 *   three waves, each another pattern.
 * At half health it stops: the collage cracks down the middle, the map tears, the sky turns into a grey kaleidoscope,
 * and over two seconds one half takes on gold, the other violet and black - the second phase, Two Hands:
 * - The crush (at once, and only once): it reaches out, and on everyone's screen two hands crush the hunger bar - no
 *   natural healing for the rest of the fight (the bar never gets back to 18);
 * - Judgement: time stops (bullets and particles hang still, players are slowed, the world drained and cold); 15-25
 *   cuts are drawn round the target, ticking; time runs on and they all strike at once - stand in the gaps;
 * - Reality-shattering punches: it draws back; gold and violet fists in turn gather high over the target (a circle
 *   marks it) and fall, the screen shattering like glass, each throwing a wave over the ground - jump it;
 * - Solar sparks: its gold half shines, a warm glow over everything; sparks arc over the arena like fireworks, thicker
 *   and thicker, each breaking into three where it lands;
 * - Converging supernova: its violet half shines; black stars circle the arena, stop, and rush in on where the target
 *   stood; a blast (the colours inverted), and a pool of shadow that eats at whoever stands in it;
 * - Orion's sword: a sword of stars drawn star by star, swung flat round the whole arena low over the ground (jump it),
 *   then raised and brought down on a line through the target (step aside);
 * - Echo of the servants: the arena split into squares or stripes of light and shadow, swapping every two seconds; on
 *   the light Aurelion's columns fall, on the shadow Nocthariel's maws bite - only the borders between are safe.
 * At a quarter of its health both halves burn like paper from the edges in, the ash falls, the kaleidoscope turns to
 * colour; a second of silence, and the skeleton is left - the third phase, the Skeleton (its hands over its face part
 * more and more as its health goes):
 * - Light slashes: it vanishes and appears here and there, each time cutting through the target, 8-12 times, faster and
 *   faster (the warning from 10 ticks down to 5); between them, for a frame, the screen shows the game itself - the
 *   menu's panorama, the building-terrain screen, the old dirt background;
 * - Chronos' hand: its clock's hand becomes a beam across the arena, turning round it faster and faster (jump it), the
 *   sun and moon whirling across the sky; stars shed off it hang still - then everything goes back the way it came
 *   (sepia, the colours split the other way);
 * - Cages: outlines of boxes round the target load in edge by edge, pulse for 1.5 s and snap shut - whoever is inside
 *   one is hurt badly;
 * - Unloaded chunks: the chunk borders show; some of the floor's chunks flicker with the loading texture, then fall
 *   into the void for three seconds (whoever stands there is eaten at), then load again.
 * At a tenth of its health its hands tremble and fall; only the missing texture is left; the sound cuts off, the screen
 * goes black but for its face for a second, and its wireframe fills with white light from the face down - the true
 * form; the sky goes white, the colours split at the screen's edges for good, the world's colour drains away (flat white
 * round it):
 * - Do not look at the Creator (all the time): the longer you look at its face the more your screen is overexposed, then
 *   your HUD turns to the missing texture part by part, then you are hurt and blinded; look away and it goes. Its face
 *   takes more harm (a blow struck looking at it, a shot that hits it);
 * - Erasure: white cuts through the arena; the floor under them goes flat white and is gone a second later, and the
 *   arena's edge with it - smaller every time (beyond it you are eaten at). The blocks come back when it dies;
 * - Chequerboard: the floor a chequer of the missing texture, magenta hurts, black is safe, moving on a square each second;
 * - Gigabeam: light gathers before its face for three seconds, then a beam across the whole arena turns for five, the
 *   menu's splash texts drifting along it;
 * - The act of creation (at the end, and it cannot die before it is over): black, silence; its face unfolds like a
 *   flower; one block before you; the world made again layer by layer from the bottom up, a wave of shots with each.
 * Dying, its face's cube cracks and breaks into pixels, the white goes out from the edges in, and the world gets its
 * colour and its blocks back from where it fell.
 * Its effects: client.CreatorFx (in the world and on the screen), client.BulletFx (its stars and petals),
 * client.BloomFx (the light of its true form); animations from tools/models/build_creator_p*.py.
 */
public class NamelessCreator extends Monster implements BossTargeting.Marked {
    public static final byte WAITING = 0, SPAWNING = 1, FIGHT = 2, TRANSFORM = 3;
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(NamelessCreator.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(NamelessCreator.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> ATTACK = SynchedEntityData.defineId(NamelessCreator.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> STATE_TIME = SynchedEntityData.defineId(NamelessCreator.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(NamelessCreator.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> HOME = SynchedEntityData.defineId(NamelessCreator.class, EntityDataSerializers.VECTOR3);

    /** The animations (each an entity event), as the models have them. */
    public enum Anim {
        SPAWN, STARBURST, SLASH, PENTAGRAM, RING, BLOOM, COLLAPSE, CHART, CRACK,
        CRUSH, JUDGEMENT, PUNCH_L, PUNCH_R, SPARKS, SUPERNOVA, SWORD, ECHO, BURN, EMERGE,
        LSLASH, CHRONOS, CAGE, CHUNKS, UNVEIL, REVEAL,
        ERASE, CHECKER, GIGA, CREATION, DEATH
    }

    public enum Attack {
        NONE(0), STARBURST(50), SLASHES(0), RING(64), BLOOM(0), COLLAPSE(COLLAPSE_END), CHART(100),
        CRUSH(60), JUDGEMENT(45), PUNCHES(0), SPARKS(140), SUPERNOVA(120), SWORD(118), ECHO(170),
        LSLASH(0), CHRONOS(140), CAGES(50), CHUNKS(110),
        ERASE(50), CHECKER(170), GIGA(GIGA_CHARGE + GIGA_BEAM + 10), CREATION(CREATION_TICKS);

        /** The phase whose attack it is. */
        public int phase() {
            return ordinal() >= ERASE.ordinal() ? 4 : ordinal() >= LSLASH.ordinal() ? 3 : ordinal() >= CRUSH.ordinal() ? 2 : 1;
        }

        public final int length;

        Attack(int length) {
            this.length = length;
        }
    }

    private static final byte EVENT_ANIM = (byte) 180;
    public static final double ARENA = 22, WAKE_RANGE = 26, AGGRO = 48;
    public static final int SPAWN_TICKS = 160, TRANSFORM_TICKS = 140, CRACK_AT = 20, DISSOLVE_FROM = 100;
    // the collapsing star
    public static final int STAR_GROW_FROM = 10, STAR_GROW_TO = 70, STAR_COLLAPSED = 80, STAR_SILENCE_TO = 90, COLLAPSE_END = 120;
    public static final float STAR_RADIUS = 4.5F, STAR_HEIGHT = 11;
    private static final float SHIELD_WIDTH = 3.4F, SHIELD_HEIGHT = 4.5F;
    // Orion's sword: drawn star by star, lowered flat, swung round; then raised and brought down
    public static final int SWORD_BUILD = 40, SWORD_TIP = 6, SWORD_SWING = 20, SWORD_V_WARN = 18, SWORD_V_FALL = 5;
    public static final float SWORD_LENGTH = 26, SWORD_LOW = 0.15F, SWORD_HIGH = 0.85F, SWORD_HILT = 0.5F;

    /** The flat swing's heading (radians, x = cos, z = sin) at an age of the sword. */
    public static float swordYaw(float start, float sweep, float age) {
        float k = Mth.clamp((age - SWORD_BUILD - SWORD_TIP) / SWORD_SWING, 0, 1);
        return start + sweep * k * k * (3 - 2 * k);
    }

    /** How far the sword points up from flat (radians) at an age of it: straight up while it is drawn, then lowered. */
    public static float swordPitch(float age) {
        if (age < SWORD_BUILD) return Mth.HALF_PI;
        return Mth.HALF_PI * Math.max(0, 1 - (age - SWORD_BUILD) / SWORD_TIP);
    }

    /** How far a wave over the ground has rolled at an age (out to max over its life). */
    public static float waveRadius(float max, int life, float age) {
        return max * Mth.clamp(age / life, 0, 1);
    }
    // the attacks' numbers
    private static final float STAR_DAMAGE = 9, CUT_DAMAGE = 13, RING_DAMAGE = 8, RING_BURST = 16, PETAL_DAMAGE = 7, BLOOM_BURST = 8,
            COLLAPSE_DAMAGE = 28, COLUMN_DAMAGE = 13;
    private static final int CUT_WARN = 12, CUT_EVERY = 16, PENTA_WARN = 24, RING_DELAY = 12, RING_CLOSE = 40, FLOWER_FALL = 14,
            FLOWER_GROW = 20, MARK_WARN = 25;
    private static final float CUT_HALF = 1.0F, CUT_HEIGHT = 4.5F, PENTA_R = 8, RING_R = 12, COLUMN_R = 1.7F;
    // the second phase
    private static final int JUDGE_STOP = 30, PUNCH_EVERY = 16, PUNCH_CHARGE = 20, WAVE_LIFE = 24, NOVA_CIRCLE = 40, NOVA_RUSH = 20,
            NOVA_ZONE = 60, ECHO_SWAP = 40, ECHO_LIFE = 160, BURN_TICKS = 100;
    private static final float WAVE_REACH = 16, FIST_DAMAGE = 14, WAVE_DAMAGE = 8, SPARK_DAMAGE = 7, NOVA_R = 18, NOVA_SPIN = 0.05F,
            NOVA_BLAST = 3.5F, NOVA_DAMAGE = 16, NOVA_STAR_DAMAGE = 8, SWORD_DAMAGE = 18, ECHO_CELL = 7, ECHO_DAMAGE = 8;
    // the third phase
    public static final int UNVEIL_DARK = 86;
    private static final int BEAM_FROM = 10, BEAM_TO = 90, CAGE_SHUT = 30, CHUNK_WARN = 30, CHUNK_GONE = 60, CHUNK_LIFE = 110;
    private static final float SLASH_DAMAGE = 12, BEAM_Y = 0.6F, BEAM_TOP = 1.0F, BEAM_TURN = 0.04F, BEAM_FASTER = 0.0015F, BEAM_DAMAGE = 10,
            BEAM_STAR_DAMAGE = 6, CAGE_DAMAGE = 22, CHUNK_DAMAGE = 8;
    // the true form
    /** how high its face is over its feet (the renderer's lift and scale, creator_common.P4_FACE_Y) */
    public static final float FACE_HEIGHT = 1.0F + 1.5F * (24 + 22) / 16F;
    public static final int GIGA_CHARGE = 60, GIGA_BEAM = 100, CREATION_START = 40, CREATION_EVERY = 30, CREATION_LAYERS = 14,
            CREATION_TICKS = CREATION_START + CREATION_LAYERS * CREATION_EVERY + 40, DEATH_TICKS = 160;
    private static final int ERASE_WARN = 10, ERASE_WHITE = 20, CHECKER_EVERY = 20, CHECKER_LIFE = 160;
    private static final float GAZE_DAMAGE = 4, EDGE_DAMAGE = 6, ERASE_DAMAGE = 14, ERASE_WIDTH = 1.6F, ARENA_MIN = 9, CHECKER_CELL = 3,
            CHECKER_DAMAGE = 5, GIGA_DAMAGE = 6, CREATION_DAMAGE = 6;

    // client
    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState[] anims = new AnimationState[Anim.values().length];

    // server
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_20);
    private final BossTargeting targeting = new BossTargeting();
    @Nullable private Vec3 home;
    private Attack attack = Attack.NONE, last = Attack.NONE;
    private int attackTick, cooldown = 40, stateTick, sinceCollapse = 3, cuts;
    private boolean pendingTransform, unclamped;
    private final List<Vec3> spots = new ArrayList<>();
    private final List<List<Vec3>> waves = new ArrayList<>();
    private final List<Vec3[]> lines = new ArrayList<>();
    private record Shield(Vec3 at, Vec3 normal) {}
    private final List<Shield> shields = new ArrayList<>();
    private Vec3 centre = Vec3.ZERO;
    private final List<Vec3> splits = new ArrayList<>(), strikes = new ArrayList<>();
    private final Set<Integer> swept = new HashSet<>();
    /** its hunger crushed (the second phase's first act): no natural healing near it from then on */
    private boolean crushed;
    /** how far it has drawn back from its home (the punches), and how far it wants to */
    private Vec3 drift = Vec3.ZERO, driftGoal = Vec3.ZERO;
    private int nextTick;
    // the true form: how long each player has looked at its face; the arena it has left; what it has erased
    private final Map<UUID, Float> gazes = new HashMap<>();
    private float arenaR = (float) ARENA;
    private final List<BlockPos> toErase = new ArrayList<>();
    private final List<Long> erased = new ArrayList<>();
    private final List<BlockState> erasedStates = new ArrayList<>();
    private boolean creationDone, pendingCreation;
    /** ticks since it began to die (both sides) */
    public int dying;

    public NamelessCreator(EntityType<? extends NamelessCreator> type, Level level) {
        super(type, level);
        for (int i = 0; i < anims.length; i++) anims[i] = new AnimationState();
        setNoGravity(true);
        setPersistenceRequired();
        xpReward = 1000;
        bossBar.setVisible(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1600.0)
                .add(Attributes.ARMOR, 16.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.FOLLOW_RANGE, AGGRO)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, WAITING);
        builder.define(PHASE, (byte) 1);
        builder.define(ATTACK, (byte) 0);
        builder.define(STATE_TIME, 0);
        builder.define(TARGET_ID, -1);
        builder.define(HOME, new Vector3f());
    }

    @Override
    protected void registerGoals() {}

    public byte state() {
        return entityData.get(STATE);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    public Attack attack() {
        int i = entityData.get(ATTACK);
        return i >= 0 && i < Attack.values().length ? Attack.values()[i] : Attack.NONE;
    }

    /** Ticks since its state (waiting, coming, fighting, changing) began, with the partial tick. */
    public float stateAge(float partialTick) {
        return (int) level().getGameTime() - entityData.get(STATE_TIME) + partialTick;
    }

    public Vec3 homeSynced() {
        Vector3f v = entityData.get(HOME);
        return new Vec3(v.x, v.y, v.z);
    }

    private void setState(byte s) {
        entityData.set(STATE, s);
        entityData.set(STATE_TIME, (int) level().getGameTime());
        stateTick = 0;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        boolean trueForm = entityData != null && phase() == 4; // (asked for while it is being made, too)
        return trueForm ? EntityDimensions.scalable(1.5F, 4.5F) : EntityDimensions.scalable(3.5F, 8.5F);
    }

    @Override
    public boolean isPickable() {
        return state() != WAITING && super.isPickable();
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
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    // ------------------------------------------------------------------ client

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PHASE.equals(key)) refreshDimensions();
        if (!level().isClientSide) return;
        if (PHASE.equals(key)) {
            for (AnimationState a : anims) a.stop();
            idleAnimation.start(tickCount);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        int i = (id & 0xFF) - (EVENT_ANIM & 0xFF);
        if (i >= 0 && i < anims.length) {
            for (int j = 0; j < anims.length; j++) if (j != i) anims[j].stop();
            anims[i].start(tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) {
            if (state() == FIGHT) idleAnimation.startIfStopped(tickCount);
            else idleAnimation.stop();
        }
    }

    private void anim(Anim a) {
        level().broadcastEntityEvent(this, (byte) (EVENT_ANIM + a.ordinal()));
    }

    // ------------------------------------------------------------------ server

    private Vec3 home() {
        if (home == null) {
            home = position();
            entityData.set(HOME, new Vector3f((float) home.x, (float) home.y, (float) home.z));
        }
        return home;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        drift = drift.lerp(driftGoal, 0.06);
        Vec3 h = home().add(drift);
        setPos(h.x, h.y, h.z);
        if (crushed && phase() == 2) for (Player p : fighters()) capHunger(p);
        if (tickCount % 10 == 0) updateBossBar(level);
        bossBar.setProgress(getHealth() / getMaxHealth());
        stateTick++;
        switch (state()) {
            case WAITING -> {
                if (tickCount % 10 == 0 && nearest(WAKE_RANGE) != null) {
                    setState(SPAWNING);
                    anim(Anim.SPAWN);
                }
            }
            case SPAWNING -> spawning(level);
            case TRANSFORM -> transforming(level);
            default -> fight(level);
        }
    }

    private void updateBossBar(ServerLevel level) {
        boolean shown = state() != WAITING;
        for (ServerPlayer p : List.copyOf(bossBar.getPlayers())) {
            if (!shown || !p.isAlive() || p.level() != level || p.distanceToSqr(this) > 80 * 80) bossBar.removePlayer(p);
        }
        if (!shown) return;
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(this) < 64 * 64) bossBar.addPlayer(p);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level) {
            bossBar.removeAllPlayers();
            Bullets.clear(this);
            if (reason != RemovalReason.UNLOADED_TO_CHUNK && reason != RemovalReason.UNLOADED_WITH_PLAYER) restore(level, position(), Double.MAX_VALUE);
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ coming

    /** Let down out of the sky on its strings and opening like a pop-up book (the spawn animation, 8 s). */
    private void spawning(ServerLevel level) {
        int t = stateTick;
        if (t == 1) {
            sound(SoundEvents.BEACON_ACTIVATE, 4.0F, 0.5F);
            ScreenFx.playNear(level, home(), 64, ScreenEffect.VIGNETTE, 150, 0.5F, 0xF0E6C8);
        }
        if (t < 52 && t % 8 == 0) sound(SoundEvents.CHAIN_STEP, 3.0F, 0.6F + t / 100.0F); // the strings paying out
        if (t == 52) {
            sound(SoundEvents.ANVIL_LAND, 1.5F, 0.4F);
            ScreenFx.shake(level, home(), 40, 10, 0.4F);
        }
        if (t >= 60 && t <= 90 && t % 3 == 0) sound(SoundEvents.BOOK_PAGE_TURN, 3.0F, 0.6F + level.random.nextFloat() * 0.4F); // banners
        if (t == 78) sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 4.0F, 1.2F); // the rose window flips round
        if (t == 86) {
            sound(SoundEvents.CHERRY_LEAVES_PLACE, 3.0F, 1.2F);
            level.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY() + 9, getZ(), 30, 1.5, 0.6, 1.5, 0.02);
        }
        if (t == 92) sound(SoundEvents.CHAIN_BREAK, 3.0F, 0.6F); // the clock and the compass drop
        if (t == 100) { // the strings jerk taut
            sound(SoundEvents.CROSSBOW_LOADING_END.value(), 4.0F, 0.5F);
            ScreenFx.shake(level, home(), 40, 6, 0.5F);
        }
        if (t == 112) sound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 4.0F, 0.7F); // the map unfolds
        if (t == 130) {
            sound(SoundEvents.BEACON_POWER_SELECT, 5.0F, 0.6F);
            sound(SoundEvents.BELL_BLOCK, 4.0F, 0.5F);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(home()) < 64 * 64) {
                    ScreenFx.play(p, ScreenEffect.TEXT, 60, 0.6F, 0xF4ECD4, getDisplayName().getString());
                }
            }
        }
        if (t >= SPAWN_TICKS) {
            setState(FIGHT);
            cooldown = 30;
        }
    }

    // ------------------------------------------------------------------ the fight

    @Nullable
    private Player nearest(double range) {
        Player best = null;
        double bestD = range * range;
        for (Player p : level().players()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            double d = p.distanceToSqr(home());
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private List<Player> fighters() {
        List<Player> out = new ArrayList<>();
        for (Player p : level().players()) {
            if (!p.isCreative() && !p.isSpectator() && p.isAlive() && p.distanceToSqr(home()) < AGGRO * AGGRO) out.add(p);
        }
        return out;
    }

    private void fight(ServerLevel level) {
        if (phase() == 4) {
            gazeTick(level);
            edgeTick();
            if (attack == Attack.NONE && pendingCreation && !creationDone) {
                LivingEntity t = getTarget() != null ? getTarget() : nearest(AGGRO);
                if (t != null) {
                    start(level, Attack.CREATION, t);
                    return;
                }
            }
        }
        if (attack == Attack.NONE && pendingTransform) {
            startTransform(level);
            return;
        }
        if (attack == Attack.NONE) setTarget(targeting.pick(this, home(), AGGRO, getTarget()));
        LivingEntity target = getTarget();
        if (target != null) face(target.position(), 4);
        if (attack != Attack.NONE) {
            attackTick++;
            attackTick(level, target);
            if (attackTick >= attack.length && attack.length > 0) end();
            return;
        }
        if (target == null || cooldown-- > 0) return;
        start(level, phase() == 2 && !crushed ? Attack.CRUSH : choose(), target);
    }

    private Attack choose() {
        if (phase() == 4) {
            List<Attack> options = new ArrayList<>(List.of(Attack.ERASE, Attack.ERASE, Attack.CHECKER, Attack.GIGA));
            options.removeIf(a -> a == last);
            return options.get(random.nextInt(options.size()));
        }
        if (phase() == 3) {
            List<Attack> options = new ArrayList<>(List.of(Attack.LSLASH, Attack.CHRONOS, Attack.CAGES, Attack.CAGES, Attack.CHUNKS));
            options.removeIf(a -> a == last);
            return options.get(random.nextInt(options.size()));
        }
        if (phase() == 2) {
            List<Attack> options = new ArrayList<>(List.of(Attack.JUDGEMENT, Attack.PUNCHES, Attack.SPARKS, Attack.SUPERNOVA, Attack.SWORD,
                    Attack.ECHO));
            options.removeIf(a -> a == last);
            return options.get(random.nextInt(options.size()));
        }
        List<Attack> options = new ArrayList<>(List.of(Attack.STARBURST, Attack.STARBURST, Attack.SLASHES, Attack.SLASHES, Attack.RING,
                Attack.BLOOM, Attack.CHART));
        if (sinceCollapse >= 4) options.add(Attack.COLLAPSE);
        if (sinceCollapse >= 7) return Attack.COLLAPSE;
        options.removeIf(a -> a == last);
        return options.get(random.nextInt(options.size()));
    }

    /** /bossattack: starts an attack by name now, if it can. */
    public boolean forceAttack(String name) {
        if (!(level() instanceof ServerLevel level) || state() != FIGHT || attack != Attack.NONE) return false;
        if (name.equals("crack") || name.equals("burn") || name.equals("unveil")) {
            if (!name.equals(phase() == 1 ? "crack" : phase() == 2 ? "burn" : "unveil")) return false;
            startTransform(level);
            return true;
        }
        Attack a = switch (name) {
            case "starburst" -> Attack.STARBURST;
            case "slashes" -> Attack.SLASHES;
            case "ring" -> Attack.RING;
            case "bloom" -> Attack.BLOOM;
            case "collapse" -> Attack.COLLAPSE;
            case "chart" -> Attack.CHART;
            case "crush" -> Attack.CRUSH;
            case "judgement" -> Attack.JUDGEMENT;
            case "punches" -> Attack.PUNCHES;
            case "sparks" -> Attack.SPARKS;
            case "supernova" -> Attack.SUPERNOVA;
            case "sword" -> Attack.SWORD;
            case "echo" -> Attack.ECHO;
            case "lslash" -> Attack.LSLASH;
            case "chronos" -> Attack.CHRONOS;
            case "cages" -> Attack.CAGES;
            case "chunks" -> Attack.CHUNKS;
            case "erase" -> Attack.ERASE;
            case "checker" -> Attack.CHECKER;
            case "gigabeam" -> Attack.GIGA;
            case "creation" -> Attack.CREATION;
            default -> Attack.NONE;
        };
        if (a != Attack.NONE && a.phase() != phase()) return false;
        LivingEntity target = getTarget() != null ? getTarget() : nearest(AGGRO);
        if (a == Attack.NONE || target == null) return false;
        start(level, a, target);
        return true;
    }

    private void start(ServerLevel level, Attack a, LivingEntity target) {
        attack = a;
        last = a;
        attackTick = 0;
        sinceCollapse = a == Attack.COLLAPSE ? 0 : sinceCollapse + 1;
        entityData.set(ATTACK, (byte) a.ordinal());
        spots.clear();
        waves.clear();
        lines.clear();
        splits.clear();
        strikes.clear();
        swept.clear();
        centre = target.position();
        switch (a) {
            case STARBURST -> {
                anim(Anim.STARBURST);
                sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 4.0F, 1.8F);
                sound(SoundEvents.BEACON_ACTIVATE, 3.0F, 1.9F);
            }
            case SLASHES -> cuts = 3 + random.nextInt(2);
            case RING -> ring(level, target);
            case BLOOM -> {
                anim(Anim.BLOOM);
                int n = 5 + random.nextInt(4);
                for (int i = 0; i < n; i++) {
                    double ang = random.nextDouble() * Mth.TWO_PI, r = 1.5 + random.nextDouble() * 4.5;
                    spots.add(floorAt(target.position().add(Math.cos(ang) * r, 0, Math.sin(ang) * r)));
                }
                sound(SoundEvents.CHERRY_LEAVES_BREAK, 3.0F, 0.8F);
            }
            case COLLAPSE -> {
                anim(Anim.COLLAPSE);
                centre = home().add(0, STAR_HEIGHT, 0);
                fx(CreatorFxPayload.STAR, centre, centre, STAR_RADIUS, 0, 0, 0, 0, COLLAPSE_END);
                plantShields(level);
            }
            case CHART -> {
                anim(Anim.CHART);
                chartWaves(target);
            }
            case LSLASH -> {
                int n = 8 + random.nextInt(5), at = 0;
                for (int i = 0; i < n; i++) {
                    int warn = Math.round(10 - 5F * i / (n - 1));
                    spots.add(new Vec3(at, warn, 0));
                    at += warn + 4;
                }
            }
            case PUNCHES -> { // it draws back into the distance, away from the target
                centre = new Vec3(4 + random.nextInt(3), 0, 0);
                Vec3 away = home().subtract(target.position()).multiply(1, 0, 1);
                driftGoal = (away.lengthSqr() < 1 ? forward(getYRot()).reverse() : away.normalize()).scale(8).add(0, 3, 0);
                sound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.5F);
            }
            default -> {}
        }
    }

    private void end() {
        attack = Attack.NONE;
        driftGoal = Vec3.ZERO;
        entityData.set(ATTACK, (byte) 0);
        shields.clear();
        cooldown = 26 + random.nextInt(16);
    }

    private void attackTick(ServerLevel level, @Nullable LivingEntity target) {
        int t = attackTick;
        switch (attack) {
            // (the attacks count their own ticks from 0)
            case STARBURST -> starburst(level, target, t - 1);
            case SLASHES -> slashes(level, target, t - 1);
            case RING -> ringTick(level, t - 1);
            case BLOOM -> bloom(level, t - 1);
            case COLLAPSE -> collapse(level, t - 1);
            case CHART -> chart(level, t - 1);
            case CRUSH -> crushTick(level, t - 1);
            case JUDGEMENT -> judgement(level, target, t - 1);
            case PUNCHES -> punches(level, target, t);
            case SPARKS -> sparks(level, t - 1);
            case SUPERNOVA -> supernova(level, target, t - 1);
            case SWORD -> swordTick(level, target, t - 1);
            case ECHO -> echo(level, t - 1);
            case LSLASH -> lightSlashes(level, target, t - 1);
            case CHRONOS -> chronos(level, target, t - 1);
            case CAGES -> cages(level, target, t - 1);
            case CHUNKS -> chunks(level, t - 1);
            case ERASE -> eraseTick(level, target, t - 1);
            case CHECKER -> checkerTick(level, t - 1);
            case GIGA -> gigaTick(level, target, t - 1);
            case CREATION -> creationTick(level, t - 1);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ 1. starburst

    private void starburst(ServerLevel level, @Nullable LivingEntity target, int t) {
        if (t % 10 == 0 && t <= 20) { // a fan of lines from the window to the target
            Vec3 from = haloPos();
            Vec3 aim = target != null ? target.getEyePosition().subtract(0, 0.4, 0) : from.add(forward(getYRot()).scale(10)).subtract(0, 6, 0);
            Vec3 dir = aim.subtract(from).normalize();
            int n = 7 + random.nextInt(3);
            float turn = (t / 10 - 1) * 6 + (random.nextBoolean() ? 2 : -2);
            List<Vec3> fan = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                float a = (-35 + 70F * i / (n - 1) + turn) * Mth.DEG_TO_RAD;
                Vec3 d = dir.yRot(a);
                fan.add(d);
                fx(CreatorFxPayload.TRAJ, from, from.add(d.scale(34)), 0, 0, 0, 0, 15, 18);
            }
            lines.add(new Vec3[] {from});
            spots.addAll(fan);
            spots.add(null); // the end of a volley
            sound(SoundEvents.AMETHYST_CLUSTER_STEP, 3.0F, 1.9F);
        }
        if (t >= 15 && t <= 35 && (t - 15) % 10 == 0) { // and the stars fly along them
            int volley = (t - 15) / 10;
            Vec3 from = lines.get(volley)[0];
            List<Bullets.Spec> stars = new ArrayList<>();
            int seen = 0;
            for (Vec3 d : spots) {
                if (d == null) {
                    seen++;
                    continue;
                }
                if (seen == volley) stars.add(Bullets.line(Bullets.STAR, from, d, 0.35F, 0.035F, 0, 60, 0.9F));
            }
            Bullets.fire(level, this, stars, STAR_DAMAGE * damageScale());
            level.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 24, 1.2, 1.2, 0.4, 0.12);
            level.sendParticles(ParticleTypes.FIREWORK, from.x, from.y, from.z, 10, 1.4, 1.4, 0.4, 0.06);
            sound(SoundEvents.BELL_BLOCK, 3.0F, 1.6F);
            sound(SoundEvents.AMETHYST_CLUSTER_BREAK, 3.0F, 0.8F);
            ScreenFx.playNear(level, home(), 48, ScreenEffect.ABERRATION, 3, 0.9F, -1);
        }
    }

    private Vec3 haloPos() {
        return position().add(0, 7.8, 0).add(forward(getYRot()).scale(-1.1));
    }

    // ------------------------------------------------------------------ 2. dimensional cuts

    private void slashes(ServerLevel level, @Nullable LivingEntity target, int t) {
        Vec3 aim = target != null ? target.position() : centre;
        for (int i = 0; i < cuts; i++) {
            if (t == i * CUT_EVERY) { // a thin white plane across the arena
                double ang = random.nextDouble() * Math.PI;
                Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Vec3 side = new Vec3(-dir.z, 0, dir.x).scale((random.nextDouble() - 0.5) * 3);
                Vec3 mid = floorAt(aim.add(side));
                Vec3 a = mid.subtract(dir.scale(ARENA)), b = mid.add(dir.scale(ARENA));
                lines.add(new Vec3[] {a, b});
                fx(CreatorFxPayload.CUT, a, b, CUT_HEIGHT, 0, 0, 0, CUT_WARN, CUT_WARN + 24);
                anim(Anim.SLASH);
                sound(SoundEvents.TRIDENT_RIPTIDE_1.value(), 3.0F, 1.8F);
            }
            if (t == i * CUT_EVERY + CUT_WARN) cut(level, lines.get(i), false);
        }
        int penta = cuts * CUT_EVERY + 4;
        if (t == penta) { // five at once: a pentagram round the target
            anim(Anim.PENTAGRAM);
            centre = floorAt(aim);
            double rot = random.nextDouble() * Mth.TWO_PI;
            Vec3[] pts = new Vec3[5];
            for (int j = 0; j < 5; j++) {
                double a = rot + j * Mth.TWO_PI / 5;
                pts[j] = centre.add(Math.cos(a) * PENTA_R, 0, Math.sin(a) * PENTA_R);
            }
            for (int j = 0; j < 5; j++) {
                Vec3[] seg = {pts[j], pts[(j + 2) % 5]};
                lines.add(seg);
                fx(CreatorFxPayload.CUT, seg[0], seg[1], CUT_HEIGHT, 0, 0, 0, PENTA_WARN, PENTA_WARN + 24);
            }
            sound(SoundEvents.TRIDENT_RIPTIDE_3.value(), 4.0F, 1.5F);
            sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 4.0F, 0.7F);
        }
        if (t == penta + PENTA_WARN) {
            for (int j = cuts; j < lines.size(); j++) cut(level, lines.get(j), true);
            impactFrame(level);
            fx(CreatorFxPayload.SHOCK, centre.add(0, 1, 0), centre, 16, 0.6F, 0, 0, 0, 18);
            sound(SoundEvents.BELL_RESONATE, 5.0F, 0.6F);
            sound(SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.6F);
        }
        if (t >= penta + PENTA_WARN + 16) end();
    }

    /** A cut opens: everyone on it (and up to CUT_HEIGHT over it) is hurt; shards of glass fall from it. */
    private void cut(ServerLevel level, Vec3[] seg, boolean quiet) {
        Vec3 a = seg[0], b = seg[1];
        for (Player p : fighters()) {
            if (p.getY() < a.y - 1 || p.getY() > a.y + CUT_HEIGHT) continue;
            if (distToSegment(p.position(), a, b) <= CUT_HALF + p.getBbWidth() / 2) hit(p, CUT_DAMAGE, 0.5, 0.3);
        }
        BlockParticleOption glass = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState());
        double len = a.distanceTo(b);
        for (double d = 0; d < len; d += 1.5) {
            Vec3 p = a.lerp(b, d / len);
            level.sendParticles(glass, p.x, p.y + 1.5 + random.nextDouble() * 2, p.z, 3, 0.2, 0.6, 0.2, 0.1);
        }
        if (!quiet) {
            sound(SoundEvents.PLAYER_ATTACK_SWEEP, 4.0F, 0.5F);
            sound(SoundEvents.GLASS_BREAK, 3.0F, 0.6F);
        }
    }

    private static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        double dx = b.x - a.x, dz = b.z - a.z;
        double len2 = dx * dx + dz * dz;
        double k = len2 < 1.0E-6 ? 0 : Mth.clamp(((p.x - a.x) * dx + (p.z - a.z) * dz) / len2, 0, 1);
        double x = a.x + dx * k - p.x, z = a.z + dz * k - p.z;
        return Math.sqrt(x * x + z * z);
    }

    // ------------------------------------------------------------------ 3. ring of stars

    private void ring(ServerLevel level, LivingEntity target) {
        anim(Anim.RING);
        centre = floorAt(target.position());
        int gap = random.nextInt(24);
        float spin = (random.nextBoolean() ? 1 : -1) * Mth.HALF_PI / RING_CLOSE;
        int link = Bullets.nextLink();
        List<Bullets.Spec> stars = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            if (i == gap || i == (gap + 1) % 24) continue;
            stars.add(Bullets.polar(Bullets.STAR, centre.add(0, 1.0, 0), RING_R, i * Mth.TWO_PI / 24, spin, -RING_R / RING_CLOSE, 0, RING_DELAY,
                    RING_CLOSE, 0.9F, link, i));
        }
        Bullets.fire(level, this, stars, RING_DAMAGE * damageScale());
        fx(CreatorFxPayload.GAP, centre, centre, RING_R, (gap + 1) * Mth.TWO_PI / 24, spin, -RING_R / RING_CLOSE, RING_DELAY, RING_DELAY + RING_CLOSE);
        sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 4.0F, 1.4F);
    }

    private void ringTick(ServerLevel level, int t) {
        if (t >= RING_DELAY && t < RING_DELAY + RING_CLOSE && t % 4 == 0) { // a tone rising as it closes
            sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 4.0F, 0.5F + 1.5F * (t - RING_DELAY) / RING_CLOSE);
        }
        if (t == RING_DELAY + RING_CLOSE) { // it bursts in the middle
            for (Player p : fighters()) {
                if (p.position().distanceTo(centre) < 3.0) hit(p, RING_BURST, 1.2, 0.6);
            }
            fx(CreatorFxPayload.SHOCK, centre.add(0, 1, 0), centre, 14, 0.8F, 0, 0, 0, 16);
            level.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y + 1, centre.z, 60, 0.3, 0.3, 0.3, 0.35);
            sound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 5.0F, 0.7F);
            sound(SoundEvents.BELL_BLOCK, 4.0F, 1.2F);
        }
    }

    // ------------------------------------------------------------------ 4. camellia bursts

    private void bloom(ServerLevel level, int t) {
        Vec3 crown = position().add(0, 9.5, 0);
        for (int i = 0; i < spots.size(); i++) {
            Vec3 s = spots.get(i);
            int fall = i * 4;
            if (t == fall) fx(CreatorFxPayload.FLOWER, crown, s, 0, 0, 0, 0, FLOWER_FALL, FLOWER_FALL + FLOWER_GROW);
            if (t == fall + FLOWER_FALL) level.playSound(null, s.x, s.y, s.z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, getSoundSource(), 3.0F, 0.8F);
            if (t == fall + FLOWER_FALL + FLOWER_GROW) { // it bursts into a spiral of petals
                List<Bullets.Spec> petals = new ArrayList<>();
                float spin = (i % 2 == 0 ? 1 : -1) * 0.045F;
                for (int j = 0; j < 16; j++) {
                    petals.add(Bullets.polar(Bullets.PETAL, s.add(0, 1.0, 0), 0.3F, j * Mth.TWO_PI / 16 + i * 0.3F, spin, 0.3F, 0, 0, 45, 0.7F, 0, 0));
                }
                Bullets.fire(level, this, petals, PETAL_DAMAGE * damageScale());
                for (Player p : fighters()) if (p.position().distanceTo(s) < 1.8) hit(p, BLOOM_BURST, 0.6, 0.4);
                fx(CreatorFxPayload.SHOCK, s.add(0, 0.5, 0), s, 6, 0, 0, 0, 0, 12);
                level.sendParticles(ParticleTypes.CHERRY_LEAVES, s.x, s.y + 0.8, s.z, 30, 0.8, 0.4, 0.8, 0.05);
                level.playSound(null, s.x, s.y, s.z, SoundEvents.NOTE_BLOCK_BELL.value(), getSoundSource(), 3.0F, 1.0F + i * 0.1F);
                level.playSound(null, s.x, s.y, s.z, SoundEvents.AMETHYST_BLOCK_CHIME, getSoundSource(), 3.0F, 1.2F);
            }
        }
        if (t >= (spots.size() - 1) * 4 + FLOWER_FALL + FLOWER_GROW + 30) end();
    }

    // ------------------------------------------------------------------ 5. collapsing star

    private void plantShields(ServerLevel level) {
        shields.clear();
        int n = Mth.clamp(fighters().size() + 2, 3, 6);
        double base = random.nextDouble() * Mth.TWO_PI;
        for (int i = 0; i < n; i++) {
            double a = base + i * Mth.TWO_PI / n + random.nextDouble() * 0.4;
            Vec3 at = floorAt(home().add(Math.cos(a) * 12, 0, Math.sin(a) * 12));
            Vec3 normal = home().subtract(at).multiply(1, 0, 1).normalize();
            shields.add(new Shield(at, normal));
        }
    }

    private void collapse(ServerLevel level, int t) {
        Vec3 star = centre;
        if (t >= 4 && t < 4 + shields.size() * 4 && (t - 4) % 4 == 0) { // each pane of light struck into the floor
            Shield sh = shields.get((t - 4) / 4);
            float yaw = (float) (Mth.atan2(sh.normal().z, sh.normal().x) * Mth.RAD_TO_DEG) - 90.0F;
            PacketDistributor.sendToPlayersNear(level, null, sh.at().x, sh.at().y, sh.at().z, 96, new FireFxPayload(FireFxPayload.LIGHT_SHIELD,
                    sh.at().x, sh.at().y, sh.at().z, yaw, SHIELD_WIDTH, 0, COLLAPSE_END - t));
            level.playSound(null, sh.at().x, sh.at().y, sh.at().z, SoundEvents.ANVIL_PLACE, getSoundSource(), 3.0F, 0.8F);
        }
        if (t >= STAR_GROW_FROM && t < STAR_COLLAPSED && t % 20 == 0) sound(SoundEvents.BEACON_AMBIENT, 6.0F, 0.4F + t / 160.0F);
        if (t == 50) sound(SoundEvents.WARDEN_SONIC_CHARGE, 5.0F, 0.5F);
        if (t >= 20 && t < STAR_GROW_TO) {
            float pull = 0.02F + 0.1F * (t - 20) / (STAR_GROW_TO - 20F);
            for (Player p : fighters()) { // it pulls
                Vec3 to = star.subtract(p.position()).multiply(1, 0, 1);
                double d = to.length();
                if (d < 1) continue;
                p.setDeltaMovement(p.getDeltaMovement().add(to.scale(pull / d)));
                p.hurtMarked = true;
            }
            if (t % 3 == 0) { // and throws sparks out in a spiral
                List<Bullets.Spec> sparks = new ArrayList<>();
                for (int arm = 0; arm < 3; arm++) {
                    float a = t * 0.3F + arm * Mth.TWO_PI / 3;
                    sparks.add(Bullets.line(Bullets.SPARK, star, new Vec3(Math.cos(a), -0.45, Math.sin(a)), 0.32F, 0, 0, 50, 0.6F));
                }
                Bullets.fire(level, this, sparks, 5 * damageScale());
            }
        }
        if (t == STAR_COLLAPSED) { // silence
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(home()) < 80 * 80) p.connection.send(new ClientboundStopSoundPacket(null, null));
            }
        }
        if (t == STAR_SILENCE_TO) { // and it goes off
            impactFrame(level);
            fx(CreatorFxPayload.SHOCK, star, star, 44, 1.2F, 0, 0, 0, 30);
            ScreenFx.shake(level, home(), 80, 30, 1.3F);
            ScreenFx.playNear(level, home(), 80, ScreenEffect.ABERRATION, 24, 1.2F, -1);
            ScreenFx.playNear(level, home(), 80, ScreenEffect.FLASH, 12, 0.8F, 0xFFF6E0);
            sound(SoundEvents.GENERIC_EXPLODE.value(), 6.0F, 0.4F);
            sound(SoundEvents.WARDEN_SONIC_BOOM, 6.0F, 0.5F);
            sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 6.0F, 0.6F);
            for (Player p : fighters()) {
                if (p.distanceToSqr(home()) > (ARENA + 8) * (ARENA + 8) || behindShield(star, p)) continue;
                if (hit(p, COLLAPSE_DAMAGE, 1.4, 0.7)) p.igniteForSeconds(5);
            }
        }
    }

    /** Whether a pane of light stands between the star and a player. */
    private boolean behindShield(Vec3 star, Player p) {
        Vec3 eye = p.getEyePosition();
        Vec3 d = eye.subtract(star);
        for (Shield sh : shields) {
            double denom = d.dot(sh.normal());
            if (Math.abs(denom) < 1.0E-6) continue;
            double k = sh.at().subtract(star).dot(sh.normal()) / denom;
            if (k <= 0 || k >= 1) continue;
            Vec3 hitAt = star.add(d.scale(k));
            Vec3 along = new Vec3(-sh.normal().z, 0, sh.normal().x);
            if (Math.abs(hitAt.subtract(sh.at()).dot(along)) <= SHIELD_WIDTH / 2 + 0.3 && hitAt.y >= sh.at().y - 0.2
                    && hitAt.y <= sh.at().y + SHIELD_HEIGHT + 0.3) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 6. the cartographer

    private void chartWaves(LivingEntity target) {
        Vec3 h = home();
        // 1: on every player, and here and there
        List<Vec3> w1 = new ArrayList<>();
        for (Player p : fighters()) w1.add(p.position());
        for (int i = 0; i < 6; i++) w1.add(randomIn(h, ARENA - 2));
        // 2: two crossing lines of marks through the target, gaps between them
        List<Vec3> w2 = new ArrayList<>();
        double rot = random.nextDouble() * Math.PI;
        for (int axis = 0; axis < 2; axis++) {
            Vec3 d = new Vec3(Math.cos(rot + axis * Mth.HALF_PI), 0, Math.sin(rot + axis * Mth.HALF_PI));
            for (int k = -5; k <= 5; k++) {
                Vec3 p = target.position().add(d.scale(k * 3.8));
                if (p.distanceTo(h) < ARENA) w2.add(p);
            }
        }
        // 3: a ring round each player, and on them
        List<Vec3> w3 = new ArrayList<>();
        for (Player p : fighters()) {
            w3.add(p.position());
            for (int k = 0; k < 8; k++) {
                double a = k * Mth.TWO_PI / 8;
                w3.add(p.position().add(Math.cos(a) * 4.2, 0, Math.sin(a) * 4.2));
            }
        }
        for (List<Vec3> w : List.of(w1, w2, w3)) {
            List<Vec3> grounded = new ArrayList<>();
            for (Vec3 p : w) grounded.add(floorAt(p));
            waves.add(grounded);
        }
    }

    private Vec3 randomIn(Vec3 h, double r) {
        double a = random.nextDouble() * Mth.TWO_PI, d = Math.sqrt(random.nextDouble()) * r;
        return h.add(Math.cos(a) * d, 0, Math.sin(a) * d);
    }

    private void chart(ServerLevel level, int t) {
        for (int w = 0; w < waves.size(); w++) {
            int at = w * 30;
            if (t == at) { // the map marks them
                for (Vec3 p : waves.get(w)) fx(CreatorFxPayload.MARK, p, p, COLUMN_R * 2, 0, 0, 0, MARK_WARN, MARK_WARN + 14);
                sound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 4.0F, 0.9F + w * 0.1F);
                sound(SoundEvents.BOOK_PAGE_TURN, 4.0F, 1.4F);
            }
            if (t == at + MARK_WARN) { // and the columns fall
                for (Vec3 p : waves.get(w)) {
                    for (Player pl : fighters()) {
                        if (pl.getY() > p.y - 1 && pl.getY() < p.y + 30
                                && Math.hypot(pl.getX() - p.x, pl.getZ() - p.z) < COLUMN_R + pl.getBbWidth() / 2) hit(pl, COLUMN_DAMAGE, 0.3, 0.5);
                    }
                    fx(CreatorFxPayload.SHOCK, p.add(0, 0.5, 0), p, 5, 0, 0, 0, 0, 10);
                    level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 0.3, p.z, 8, 0.5, 0.1, 0.5, 0.15);
                }
                sound(SoundEvents.LIGHTNING_BOLT_IMPACT, 5.0F, 1.3F);
                sound(SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 1.3F);
            }
        }
    }

    // ------------------------------------------------------------------ phase 2: Two Hands

    /** 13. It reaches out to the players; two hands crush their hunger bars - no natural healing for the rest of the phase. */
    private void crushTick(ServerLevel level, int t) {
        if (t == 0) {
            anim(Anim.CRUSH);
            fx(CreatorFxPayload.CRUSH, home(), home(), 0, 0, 0, 0, 0, 60);
            sound(SoundEvents.WARDEN_HEARTBEAT, 4.0F, 0.6F);
        }
        if (t == 28) {
            crushed = true;
            for (Player p : fighters()) capHunger(p);
            ScreenFx.shake(level, home(), 64, 8, 0.6F);
            ScreenFx.playNear(level, home(), 64, ScreenEffect.ABERRATION, 10, 1.0F, -1);
            sound(SoundEvents.BONE_BLOCK_BREAK, 5.0F, 0.5F);
            sound(SoundEvents.GLASS_BREAK, 4.0F, 0.4F);
            sound(SoundEvents.SKELETON_HURT, 4.0F, 0.3F);
        }
        if (t == 34) silence(level); // and then nothing
    }

    /** Natural healing needs a hunger bar of 18 or more: it never gets there again near it. */
    private static void capHunger(Player p) {
        if (p.getFoodData().getFoodLevel() > 17) p.getFoodData().setFoodLevel(17);
    }

    /** 7. Judgement: time stops, cuts are drawn round the target one by one, time runs on and they all strike at once. */
    private void judgement(ServerLevel level, @Nullable LivingEntity target, int t) {
        if (t == 0) {
            anim(Anim.JUDGEMENT);
            Bullets.pause(level, this, JUDGE_STOP);
            fx(CreatorFxPayload.TIMESTOP, home(), home(), 0, 0, 0, 0, 0, JUDGE_STOP);
            silence(level);
            sound(SoundEvents.BEACON_AMBIENT, 6.0F, 0.5F); // all goes quiet but one note
            ScreenFx.playNear(level, home(), 80, ScreenEffect.TIMESTOP, JUDGE_STOP + 2, 1.0F, -1);
            ScreenFx.playNear(level, home(), 80, ScreenEffect.VIGNETTE, JUDGE_STOP + 2, 0.9F, 0x182436);
            for (Player p : fighters()) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, JUDGE_STOP, 3, false, false));
            Vec3 aim = target != null ? target.position() : centre;
            int n = 15 + random.nextInt(11);
            for (int i = 0; i < n; i++) {
                double ang = random.nextDouble() * Math.PI;
                Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                double off = random.nextDouble() * Mth.TWO_PI, r = Math.sqrt(random.nextDouble()) * 7;
                Vec3 mid = floorAt(aim.add(Math.cos(off) * r, 0, Math.sin(off) * r));
                double len = 4 + random.nextDouble() * 3;
                lines.add(new Vec3[] {mid.subtract(dir.scale(len)), mid.add(dir.scale(len))});
                spots.add(new Vec3(i * (JUDGE_STOP - 2.0) / n, 0, 0)); // when it is drawn
            }
        }
        for (int i = 0; i < lines.size(); i++) { // drawn in the air one by one, ticking
            if (t == (int) spots.get(i).x) {
                Vec3[] seg = lines.get(i);
                fx(CreatorFxPayload.CUT, seg[0], seg[1], 3.5F, 0, 0, 0, JUDGE_STOP - t, JUDGE_STOP - t + 24);
                sound(SoundEvents.NOTE_BLOCK_HAT.value(), 3.0F, 1.6F + random.nextFloat() * 0.3F);
            }
        }
        if (t == JUDGE_STOP) { // time runs on: all at once
            for (Vec3[] seg : lines) cut(level, seg, true);
            impactFrame(level);
            ScreenFx.shake(level, home(), 64, 10, 1.0F);
            sound(SoundEvents.PLAYER_ATTACK_STRONG, 6.0F, 0.5F);
            sound(SoundEvents.GENERIC_EXPLODE.value(), 5.0F, 0.8F);
            sound(SoundEvents.GLASS_BREAK, 5.0F, 0.6F);
        }
    }

    /** 8. Reality-shattering punches: it draws back, and gold and violet fists fall in turn on the target, each throwing a wave. */
    private void punches(ServerLevel level, @Nullable LivingEntity target, int t) {
        int n = (int) centre.x;
        for (int i = 0; i < n; i++) {
            int charge = 8 + i * PUNCH_EVERY;
            boolean gold = i % 2 == 0;
            if (t == charge) {
                Vec3 at = floorAt(target != null ? target.position() : home());
                spots.add(at);
                fx(CreatorFxPayload.FIST, at, at, gold ? 0 : 1, 0, 0, 0, PUNCH_CHARGE, PUNCH_CHARGE + 10);
                anim(gold ? Anim.PUNCH_L : Anim.PUNCH_R);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_CHARGE, getSoundSource(), 3.0F, 1.6F);
            }
            if (t == charge + PUNCH_CHARGE && i < spots.size()) {
                Vec3 at = spots.get(i);
                for (Player p : fighters()) if (p.position().distanceTo(at) < 2.6) hit(p, FIST_DAMAGE, 1.0, 0.6);
                waves.add(List.of(at, new Vec3(t, gold ? 0 : 1, 0)));
                fx(CreatorFxPayload.GWAVE, at, at, WAVE_REACH, gold ? 0 : 1, 0, 0, 0, WAVE_LIFE);
                for (ServerPlayer p : level.players()) {
                    if (p.distanceToSqr(at) < 48 * 48) ScreenFx.play(p, ScreenEffect.SHATTER, 7, 1.0F);
                }
                ScreenFx.shake(level, at, 48, 12, 1.0F);
                BossDeath.dust(level, at, 40, 2.0);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, getSoundSource(), 5.0F, 0.5F);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), getSoundSource(), 5.0F, 0.5F);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, getSoundSource(), 3.0F, 0.5F);
            }
        }
        rollWaves(t);
        if (t >= 8 + (n - 1) * PUNCH_EVERY + PUNCH_CHARGE + WAVE_LIFE + 4) end();
    }

    /** The waves rolling out over the ground from the fists: whoever is on the ground as one passes is hit - jump it. */
    private void rollWaves(int t) {
        for (List<Vec3> w : waves) {
            Vec3 at = w.get(0);
            float age = t - (float) w.get(1).x;
            if (age < 0 || age > WAVE_LIFE) continue;
            float r = waveRadius(WAVE_REACH, WAVE_LIFE, age);
            for (Player p : fighters()) {
                if (swept.contains(p.getUUID().hashCode() * 31 + (int) w.get(1).x)) continue;
                double d = Math.hypot(p.getX() - at.x, p.getZ() - at.z);
                if (Math.abs(d - r) < 0.8 && p.getY() - at.y < 0.6 && hit(p, WAVE_DAMAGE, 0.6, 0.5)) {
                    swept.add(p.getUUID().hashCode() * 31 + (int) w.get(1).x);
                }
            }
        }
    }

    /** 9. Solar sparks: its gold half shines, sparks arc over the arena like fireworks, thicker and thicker; each one
     * that lands breaks into three. */
    private void sparks(ServerLevel level, int t) {
        if (t == 0) {
            anim(Anim.SPARKS);
            ScreenFx.playNear(level, home(), 64, ScreenEffect.GLOW, 92, 0.7F, 0xFFC060);
            sound(SoundEvents.BEACON_ACTIVATE, 4.0F, 1.5F);
        }
        if (t >= 6 && t <= 86 && t % 4 == 2) {
            int count = 2 + (t - 6) / 20;
            Vec3 left = forward(getYRot() - 90);
            Vec3 from = position().add(left.scale(2.5)).add(0, 8, 0);
            List<Bullets.Spec> big = new ArrayList<>(), small = new ArrayList<>();
            List<Player> players = fighters();
            for (int i = 0; i < count; i++) {
                Vec3 land = !players.isEmpty() && random.nextBoolean()
                        ? players.get(random.nextInt(players.size())).position().add(random.nextGaussian() * 2.5, 0, random.nextGaussian() * 2.5)
                        : randomIn(home(), ARENA - 1);
                land = floorAt(land);
                float g = 0.045F, vy = 0.5F + random.nextFloat() * 0.3F;
                int fall = Bullets.ticksToFall(from, new Vec3(0, vy, 0), g, land.y + 0.3);
                if (fall <= 0) continue;
                Vec3 v = new Vec3((land.x - from.x) / fall, vy, (land.z - from.z) / fall);
                big.add(Bullets.arc(Bullets.EMBER, from, v, g, 0, fall, 0.8F));
                Vec3 at = land.add(0, 0.3, 0);
                splits.add(new Vec3(t + fall, 0, 0));
                splits.add(at);
                for (int j = 0; j < 3; j++) {
                    double a = random.nextDouble() * Mth.TWO_PI;
                    Vec3 sv = new Vec3(Math.cos(a) * 0.17, 0.26, Math.sin(a) * 0.17);
                    int life = Math.max(4, Bullets.ticksToFall(at, sv, 0.06F, land.y + 0.2));
                    small.add(Bullets.arc(Bullets.EMBER, at, sv, 0.06F, fall, life, 0.5F));
                }
            }
            Bullets.fire(level, this, big, SPARK_DAMAGE * damageScale());
            Bullets.fire(level, this, small, SPARK_DAMAGE * 0.55F * damageScale());
            sound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 3.0F, 0.8F + random.nextFloat() * 0.4F);
            if (t % 8 == 2) sound(SoundEvents.NOTE_BLOCK_BELL.value(), 3.0F, 1.6F + random.nextFloat() * 0.4F);
        }
        for (int i = 0; i + 1 < splits.size(); i += 2) { // the sparks breaking up as they land: embers, crackling
            if ((int) splits.get(i).x != t) continue;
            Vec3 at = splits.get(i + 1);
            level.sendParticles(ModParticles.EMBER.get(), at.x, at.y, at.z, 6, 0.3, 0.2, 0.3, 0.06);
            if (random.nextInt(3) == 0) level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, getSoundSource(), 2.0F, 1.2F);
        }
    }

    /** 10. Converging supernova: black stars circle the arena, stop, and rush in on where the target stood; a blast,
     * and a pool of shadow left there. */
    private void supernova(ServerLevel level, @Nullable LivingEntity target, int t) {
        Vec3 ring = home().add(0, 1.5, 0);
        if (t == 0) {
            anim(Anim.SUPERNOVA);
            int n = 8 + random.nextInt(5);
            List<Bullets.Spec> stars = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                float a0 = i * Mth.TWO_PI / n;
                spots.add(new Vec3(a0, 0, 0));
                stars.add(Bullets.polar(Bullets.VOIDSTAR, ring, NOVA_R, a0, NOVA_SPIN, 0, 0, 0, NOVA_CIRCLE, 1.0F, 0, 0));
            }
            Bullets.fire(level, this, stars, NOVA_STAR_DAMAGE * damageScale());
        }
        if (t < NOVA_CIRCLE && t % 10 == 0) sound(SoundEvents.BEACON_AMBIENT, 5.0F, 0.45F);
        if (t == NOVA_CIRCLE) { // they stop, and rush in on where the target stands now
            centre = floorAt(target != null ? target.position() : home());
            List<Bullets.Spec> rush = new ArrayList<>();
            Vec3 to = centre.add(0, 1, 0);
            for (Vec3 s : spots) {
                float a = (float) s.x + NOVA_SPIN * NOVA_CIRCLE;
                Vec3 from = ring.add(Math.cos(a) * NOVA_R, 0, Math.sin(a) * NOVA_R);
                rush.add(Bullets.line(Bullets.VOIDSTAR, from, to.subtract(from), (float) from.distanceTo(to) / NOVA_RUSH, 0, 0, NOVA_RUSH, 1.0F));
            }
            Bullets.fire(level, this, rush, NOVA_STAR_DAMAGE * damageScale());
            Telegraphs.show(level, Telegraph.circle(centre, NOVA_BLAST), NOVA_RUSH, 2, this, null);
            sound(SoundEvents.PORTAL_TRIGGER, 4.0F, 2.0F);
            sound(SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 4.0F, 0.6F);
        }
        if (t == NOVA_CIRCLE + NOVA_RUSH) { // the blast, and the shadow left behind
            for (Player p : fighters()) if (p.position().distanceTo(centre) < NOVA_BLAST) hit(p, NOVA_DAMAGE, 1.2, 0.6);
            ScreenFx.playNear(level, home(), 80, ScreenEffect.INVERT, 3, 1.0F, -1);
            fx(CreatorFxPayload.SHOCK, centre.add(0, 1, 0), centre, 14, 0.5F, 0, 0, 0, 16);
            fx(CreatorFxPayload.VOIDZONE, centre, centre, NOVA_BLAST * 2, 0, 0, 0, 0, NOVA_ZONE);
            level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GENERIC_EXPLODE.value(), getSoundSource(), 5.0F, 0.6F);
            level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.WARDEN_SONIC_BOOM, getSoundSource(), 4.0F, 1.2F);
        }
        if (t > NOVA_CIRCLE + NOVA_RUSH && t % 10 == 0) {
            for (Player p : fighters()) {
                if (p.position().distanceTo(centre) < NOVA_BLAST) {
                    hit(p, 3, 0, 0);
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
                }
            }
        }
    }

    /** 11. Orion's sword: drawn star by star, swung flat round the whole arena (jump it), then brought down on a line. */
    private void swordTick(ServerLevel level, @Nullable LivingEntity target, int t) {
        Vec3 hilt = position().add(0, SWORD_HILT, 0);
        if (t == 0) {
            anim(Anim.SWORD);
            Vec3 aim = target != null ? target.position() : home().add(1, 0, 0);
            float toward = (float) Mth.atan2(aim.z - hilt.z, aim.x - hilt.x);
            float sweep = (random.nextBoolean() ? 1 : -1) * Mth.PI * 1.5F;
            centre = new Vec3(toward - sweep / 2, sweep, 0);
            fx(CreatorFxPayload.SWORD, hilt, hilt, SWORD_LENGTH, (float) centre.x, sweep, 0, SWORD_BUILD, SWORD_BUILD + SWORD_TIP + SWORD_SWING + 10);
            swept.clear();
        }
        if (t < SWORD_BUILD && t % 5 == 0) { // a choir rising as it is drawn
            sound(SoundEvents.BEACON_POWER_SELECT, 4.0F, 0.5F + t / 60F);
            sound(SoundEvents.AMETHYST_BLOCK_CHIME, 3.0F, 0.8F + t / 50F);
        }
        int from = SWORD_BUILD + SWORD_TIP;
        if (t == from) {
            sound(SoundEvents.PLAYER_ATTACK_SWEEP, 6.0F, 0.3F);
            sound(SoundEvents.WARDEN_SONIC_BOOM, 4.0F, 0.6F);
            ScreenFx.shake(level, home(), 64, SWORD_SWING, 0.6F);
            fx(CreatorFxPayload.SHOCK, hilt, hilt, SWORD_LENGTH, 0, 0, 0, 0, SWORD_SWING);
        }
        if (t > from && t <= from + SWORD_SWING) { // the flat swing: whoever the blade passes, not over it, is hit
            float y0 = swordYaw((float) centre.x, (float) centre.y, t - 1), y1 = swordYaw((float) centre.x, (float) centre.y, t);
            for (Player p : fighters()) {
                if (swept.contains(p.getUUID().hashCode())) continue;
                double d = Math.hypot(p.getX() - hilt.x, p.getZ() - hilt.z);
                double feet = p.getY() - getY();
                if (d > SWORD_LENGTH + 0.5 || feet >= SWORD_HIGH || feet + p.getBbHeight() <= SWORD_LOW) continue;
                float a = (float) Mth.atan2(p.getZ() - hilt.z, p.getX() - hilt.x);
                if (between(a, y0, y1) && hit(p, SWORD_DAMAGE, 1.2, 0.4)) swept.add(p.getUUID().hashCode());
            }
        }
        if (t == 80) { // raised over it, to come down along a line through the target
            Vec3 aim = target != null ? target.position() : home().add(1, 0, 0);
            centre = new Vec3(Mth.atan2(aim.z - hilt.z, aim.x - hilt.x), 0, 0);
            fx(CreatorFxPayload.SWORD_V, hilt, hilt, SWORD_LENGTH, (float) centre.x, 0, 0, SWORD_V_WARN, SWORD_V_WARN + SWORD_V_FALL + 12);
            sound(SoundEvents.BEACON_POWER_SELECT, 4.0F, 1.4F);
        }
        if (t == 80 + SWORD_V_WARN + SWORD_V_FALL) {
            Vec3 dir = new Vec3(Math.cos(centre.x), 0, Math.sin(centre.x));
            Vec3 end = hilt.add(dir.scale(SWORD_LENGTH));
            for (Player p : fighters()) if (distToSegment(p.position(), hilt, end) < 1.3 + p.getBbWidth() / 2) hit(p, SWORD_DAMAGE, 0.8, 0.5);
            fx(CreatorFxPayload.SHOCK, hilt.lerp(end, 0.5), hilt, 12, 0.4F, 0, 0, 0, 14);
            ScreenFx.shake(level, home(), 64, 12, 1.0F);
            for (double d = 2; d < SWORD_LENGTH; d += 2.5) BossDeath.dust(level, floorAt(hilt.add(dir.scale(d))), 8, 0.6);
            sound(SoundEvents.ANVIL_LAND, 4.0F, 0.5F);
            sound(SoundEvents.GENERIC_EXPLODE.value(), 5.0F, 0.6F);
            sound(SoundEvents.PLAYER_ATTACK_STRONG, 5.0F, 0.4F);
        }
    }

    /** Whether the heading a lies on the short arc swept from y0 to y1 (radians). */
    private static boolean between(float a, float y0, float y1) {
        float sweep = Mth.wrapDegrees((y1 - y0) * Mth.RAD_TO_DEG), off = Mth.wrapDegrees((a - y0) * Mth.RAD_TO_DEG);
        return sweep >= 0 ? off >= -0.5F && off <= sweep + 0.5F : off <= 0.5F && off >= sweep - 0.5F;
    }

    /** 12. Echo of the servants: the arena split into light and shadow, swapping every two seconds; out on the light
     * Aurelion's columns strike, out on the shadow Nocthariel's maws bite - only the borders are safe. */
    private void echo(ServerLevel level, int t) {
        if (t == 0) {
            anim(Anim.ECHO);
            centre = new Vec3(random.nextDouble() * Math.PI, random.nextBoolean() ? 1 : 0, 0);
            fx(CreatorFxPayload.ZONES, home(), home(), ECHO_CELL, (float) centre.x, (float) centre.y, (float) ARENA, ECHO_SWAP, ECHO_LIFE);
        }
        if (t >= ECHO_LIFE) return;
        if (t % ECHO_SWAP == 0) { // the zones change places: a flash of each one's new colour, both servants' themes
            for (Player p : fighters()) {
                if (p instanceof ServerPlayer sp) ScreenFx.play(sp, ScreenEffect.FLASH, 5, 0.35F, zoneLight(p.position(), t) ? 0xFFD070 : 0x6020A0, "");
            }
            sound(SoundEvents.AMETHYST_BLOCK_CHIME, 4.0F, 1.2F);
            sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 3.0F, 1.5F);
            sound(SoundEvents.WARDEN_HEARTBEAT, 4.0F, 0.6F);
            sound(SoundEvents.AMBIENT_CAVE.value(), 3.0F, 0.6F);
        }
        if (t % 20 == 10 && t < ECHO_LIFE - 14) { // whoever is not on a border is struck
            for (Player p : fighters()) {
                Vec3 at = floorAt(p.position());
                if (zoneBorder(p.position()) < 1.0) continue;
                if (zoneLight(p.position(), t)) {
                    fx(CreatorFxPayload.MARK, at, at, 2.4F, 0, 0, 0, 10, 22);
                } else {
                    PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 64, new FireFxPayload(FireFxPayload.MAW, at.x, at.y, at.z, 2.6F, 0, 12, 26));
                }
                strikes.add(new Vec3(t + (zoneLight(p.position(), t) ? 10 : 12), 0, 0));
                strikes.add(at);
            }
        }
        for (int i = 0; i + 1 < strikes.size(); i += 2) {
            if ((int) strikes.get(i).x != t) continue;
            Vec3 at = strikes.get(i + 1);
            for (Player p : fighters()) if (Math.hypot(p.getX() - at.x, p.getZ() - at.z) < 1.6) hit(p, ECHO_DAMAGE, 0.3, 0.4);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_FANGS_ATTACK, getSoundSource(), 3.0F, 0.7F);
        }
    }

    /** Which zone a place is in at a tick of the echo (true: light). */
    private boolean zoneLight(Vec3 p, int t) {
        Vec3 u = new Vec3(Math.cos(centre.x), 0, Math.sin(centre.x)), v = new Vec3(-u.z, 0, u.x);
        Vec3 d = p.subtract(home());
        int i = Mth.floor(d.dot(u) / ECHO_CELL), j = Mth.floor(d.dot(v) / ECHO_CELL);
        return Math.floorMod(i + (centre.y > 0.5 ? j : 0) + t / ECHO_SWAP, 2) == 0;
    }

    /** How far a place is from the nearest border between the zones. */
    private double zoneBorder(Vec3 p) {
        Vec3 u = new Vec3(Math.cos(centre.x), 0, Math.sin(centre.x)), v = new Vec3(-u.z, 0, u.x);
        Vec3 d = p.subtract(home());
        double fu = Mth.frac(d.dot(u) / ECHO_CELL), fv = Mth.frac(d.dot(v) / ECHO_CELL);
        double best = Math.min(fu, 1 - fu) * ECHO_CELL;
        if (centre.y > 0.5) best = Math.min(best, Math.min(fv, 1 - fv) * ECHO_CELL);
        return best;
    }

    // ------------------------------------------------------------------ phase 3: the Skeleton

    /** 14. Light slashes: it vanishes and appears here and there, each time cutting through the target - faster and faster. */
    private void lightSlashes(ServerLevel level, @Nullable LivingEntity target, int t) {
        for (int i = 0; i < spots.size(); i++) {
            int at = (int) spots.get(i).x, warn = (int) spots.get(i).y;
            if (t == at) { // gone, and here: a glitch, and a frame of the game itself between the cuts
                Vec3 aim = target != null ? target.position() : centre;
                double ang = random.nextDouble() * Mth.TWO_PI;
                Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Vec3 from = floorAt(aim.subtract(dir.scale(6)));
                drift = driftGoal = from.subtract(home());
                setPos(from.x, from.y, from.z);
                face(aim, 360);
                Vec3 a = from.add(0, 1.2, 0), b = aim.add(dir.scale(7)).add(0, 1.2, 0);
                lines.add(new Vec3[] {a, b});
                fx(CreatorFxPayload.LSLASH, a, b, 0, 0, 0, 0, warn, warn + 8);
                if (i > 0) {
                    fx(CreatorFxPayload.GAMEFLASH, home(), home(), i % 3, 0, 0, 0, 0, 1);
                    sound(SoundEvents.UI_BUTTON_CLICK.value(), 3.0F, 1.0F);
                }
                ScreenFx.playNear(level, home(), 64, ScreenEffect.GLITCH, 3, 0.8F, -1);
                anim(Anim.LSLASH);
                sound(SoundEvents.ENDERMAN_TELEPORT, 3.0F, 1.6F);
            }
            if (t == at + warn && i < lines.size()) { // the cut
                Vec3[] seg = lines.get(i);
                for (Player p : fighters()) {
                    if (p.getY() > seg[0].y + 1 || p.getY() + p.getBbHeight() < seg[0].y - 1) continue;
                    if (distToSegment(p.position(), seg[0], seg[1]) <= 1.0 + p.getBbWidth() / 2) hit(p, SLASH_DAMAGE, 0.6, 0.3);
                }
                ScreenFx.playNear(level, home(), 64, ScreenEffect.INVERT, 2, 1.0F, -1);
                sound(SoundEvents.PLAYER_ATTACK_SWEEP, 4.0F, 1.8F);
                sound(SoundEvents.TRIDENT_THROW.value(), 3.0F, 1.5F);
            }
        }
        Vec3 last = spots.get(spots.size() - 1);
        if (t >= last.x + last.y + 12) end();
    }

    /** The heading (radians) of Chronos' hand `age` ticks after it started turning: faster and faster. */
    public static float beamAngle(float start, float turn, float faster, float age) {
        float t = Math.max(0, age);
        return start + turn * t + faster * t * t / 2;
    }

    /** 15. Chronos' hand: the clock's hand becomes a beam across the arena, turning faster and faster (jump it); stars
     * shed from it hang, then everything goes back the way it came. */
    private void chronos(ServerLevel level, @Nullable LivingEntity target, int t) {
        Vec3 src = position().add(0, BEAM_Y, 0);
        if (t == 0) {
            anim(Anim.CHRONOS);
            Vec3 aim = target != null ? target.position() : home().add(1, 0, 0);
            centre = new Vec3(Mth.atan2(aim.z - src.z, aim.x - src.x) + Math.PI, 0, 0);
            fx(CreatorFxPayload.CHRONOS, src, src, (float) ARENA + 3, (float) centre.x, BEAM_TURN, BEAM_FASTER, BEAM_FROM, BEAM_TO);
            sound(SoundEvents.CHAIN_BREAK, 4.0F, 0.6F);
            nextTick = BEAM_FROM;
        }
        if (t >= BEAM_FROM && t < BEAM_TO) {
            float a0 = beamAngle((float) centre.x, BEAM_TURN, BEAM_FASTER, t - 1 - BEAM_FROM), a1 = beamAngle((float) centre.x, BEAM_TURN, BEAM_FASTER, t - BEAM_FROM);
            if (t % 20 == 0) swept.clear();
            for (Player p : fighters()) {
                if (swept.contains(p.getUUID().hashCode())) continue;
                double feet = p.getY() - src.y + BEAM_Y;
                if (feet >= BEAM_TOP || Math.hypot(p.getX() - src.x, p.getZ() - src.z) > ARENA + 3) continue;
                float a = (float) Mth.atan2(p.getZ() - src.z, p.getX() - src.x);
                if (between(a, a0, a1) && hit(p, BEAM_DAMAGE, 0.8, 0.4)) swept.add(p.getUUID().hashCode());
            }
            if (t >= nextTick) { // the clock ticking, faster and faster
                sound(SoundEvents.NOTE_BLOCK_HAT.value(), 4.0F, 1.2F);
                nextTick = t + Math.max(2, 10 - (t - BEAM_FROM) / 9);
            }
            if (t % 4 == 0 && t < BEAM_TO - 10) { // stars shed off it, drifting the way it turns
                List<Bullets.Spec> stars = new ArrayList<>();
                double r = 3 + random.nextDouble() * (ARENA - 3);
                Vec3 at = src.add(Math.cos(a1) * r, 0.6, Math.sin(a1) * r);
                stars.add(Bullets.line(Bullets.STAR, at, new Vec3(-Math.sin(a1), 0, Math.cos(a1)), 0.12F, 0, 0, 110, 0.8F));
                Bullets.fire(level, this, stars, BEAM_STAR_DAMAGE * damageScale());
            }
        }
        if (t == BEAM_TO) { // still - and then everything goes back
            Bullets.pause(level, this, 6);
            ScreenFx.playNear(level, home(), 80, ScreenEffect.REWIND, 52, 1.0F, -1);
            sound(SoundEvents.BEACON_DEACTIVATE, 5.0F, 2.0F);
            sound(SoundEvents.ENDERMAN_TELEPORT, 4.0F, 0.5F);
        }
        if (t == BEAM_TO + 6) Bullets.rewind(level, this, level.getGameTime(), 40);
        if (t > BEAM_TO + 6 && t < BEAM_TO + 46 && t % 3 == 0) sound(SoundEvents.NOTE_BLOCK_HAT.value(), 3.0F, 0.7F); // ticking backwards
    }

    /** 16. Cages: outlines of boxes round the target load in edge by edge, pulse, and snap shut - whoever is inside is hurt. */
    private void cages(ServerLevel level, @Nullable LivingEntity target, int t) {
        if (t == 0) {
            anim(Anim.CAGE);
            Vec3 aim = target != null ? target.position() : centre;
            int n = 3 + random.nextInt(3);
            for (int i = 0; i < n; i++) {
                double w = 2.5 + random.nextDouble() * 3.5, h = 2.5 + random.nextDouble() * 2, d = 2.5 + random.nextDouble() * 3.5;
                double slack = i == 0 ? Math.max(0, Math.min(w, d) / 2 - 0.8) : 4;
                Vec3 mid = floorAt(aim.add((random.nextDouble() - 0.5) * 2 * slack, 0, (random.nextDouble() - 0.5) * 2 * slack));
                Vec3 lo = mid.add(-w / 2, -0.05, -d / 2), hi = mid.add(w / 2, h, d / 2);
                lines.add(new Vec3[] {lo, hi});
                fx(CreatorFxPayload.CAGE, lo, hi, 0, 0, 0, 0, CAGE_SHUT, CAGE_SHUT + 10);
            }
        }
        if (t < CAGE_SHUT && t % 6 == 0) sound(SoundEvents.NOTE_BLOCK_BIT.value(), 3.0F, 1.6F + t / 60F); // digital beeps
        if (t == CAGE_SHUT) {
            for (Player p : fighters()) {
                Vec3 c = p.position().add(0, p.getBbHeight() / 2, 0);
                for (Vec3[] box : lines) {
                    if (new AABB(box[0], box[1]).contains(c)) {
                        hit(p, CAGE_DAMAGE, 0, 0.3);
                        break;
                    }
                }
            }
            ScreenFx.playNear(level, home(), 64, ScreenEffect.GLITCH, 6, 1.0F, -1);
            ScreenFx.playNear(level, home(), 64, ScreenEffect.PIXELATE, 6, 0.5F, -1);
            sound(SoundEvents.IRON_TRAPDOOR_CLOSE, 5.0F, 0.6F);
            sound(SoundEvents.PISTON_CONTRACT, 4.0F, 0.8F);
            sound(SoundEvents.ANVIL_LAND, 3.0F, 1.8F);
        }
    }

    /** 17. Unloaded chunks: the chunk borders show; some of the floor's chunks flicker, then fall into the void for three
     * seconds - whoever stands in one is eaten at - then load again. */
    private void chunks(ServerLevel level, int t) {
        Vec3 h = home();
        if (t == 0) {
            anim(Anim.CHUNKS);
            List<Vec3> all = new ArrayList<>();
            for (int cx = Mth.floor((h.x - ARENA) / 16); cx <= Mth.floor((h.x + ARENA) / 16); cx++) {
                for (int cz = Mth.floor((h.z - ARENA) / 16); cz <= Mth.floor((h.z + ARENA) / 16); cz++) {
                    double nx = Mth.clamp(h.x, cx * 16, cx * 16 + 16), nz = Mth.clamp(h.z, cz * 16, cz * 16 + 16);
                    if (Math.hypot(nx - h.x, nz - h.z) <= ARENA) all.add(new Vec3(cx * 16, h.y, cz * 16));
                }
            }
            for (Vec3 c : all) if (random.nextFloat() < 0.45F) spots.add(c);
            if (spots.isEmpty()) spots.add(all.get(random.nextInt(all.size())));
            if (spots.size() == all.size() && spots.size() > 1) spots.remove(random.nextInt(spots.size()));
            fx(CreatorFxPayload.CHUNKGRID, h, h, (float) ARENA, 0, 0, 0, 0, CHUNK_LIFE);
            for (Vec3 c : spots) fx(CreatorFxPayload.CHUNK, c, c, 0, 0, 0, 0, CHUNK_WARN, CHUNK_WARN + CHUNK_GONE + 6);
        }
        if (t < CHUNK_WARN && t % 10 == 0) sound(SoundEvents.BEACON_AMBIENT, 5.0F, 0.5F); // a low hum
        if (t == CHUNK_WARN) { // gone: the sound cut off
            silence(level);
            sound(SoundEvents.NOTE_BLOCK_BIT.value(), 5.0F, 0.5F);
            ScreenFx.playNear(level, h, 64, ScreenEffect.PIXELATE, 8, 0.6F, -1);
            ScreenFx.playNear(level, h, 64, ScreenEffect.GLITCH, 8, 1.0F, -1);
        }
        if (t > CHUNK_WARN && t <= CHUNK_WARN + CHUNK_GONE && t % 10 == 0) {
            for (Player p : fighters()) {
                if (p.getY() > h.y + 3) continue;
                for (Vec3 c : spots) {
                    if (p.getX() >= c.x && p.getX() < c.x + 16 && p.getZ() >= c.z && p.getZ() < c.z + 16) {
                        hit(p, CHUNK_DAMAGE, 0, 0);
                        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
                        break;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the change into phase 4

    /** The hands tremble and slowly fall; only the missing texture is left; black but for the face, a clear high note -
     * and the wireframe fills with white light from the face down. */
    private void unveiling(ServerLevel level) {
        int t = stateTick;
        if (t == 1) anim(Anim.UNVEIL);
        if (t < 40 && t % 4 == 0) sound(SoundEvents.CHAIN_HIT, 3.0F, 0.6F + t / 60F);
        if (t == UNVEIL_DARK) {
            silence(level);
            ScreenFx.playOnNear(this, 80, ScreenEffect.Anchor.EXCEPT, 0, ScreenEffect.FLASH, 20, 1.0F, 0x000000);
        }
        if (t == 100) entityData.set(PHASE, (byte) 4);
        if (t == 101) {
            anim(Anim.REVEAL);
            sound(SoundEvents.BEACON_POWER_SELECT, 6.0F, 2.0F);
            sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 5.0F, 2.0F);
        }
        if (t >= 101 + 80) setState(FIGHT);
    }

    // ------------------------------------------------------------------ phase 4: the True Form

    /** 18. Do not look at the Creator: the longer a player looks at its face, the worse (on their screen, and at last hurt
     * and blinded); looking away lets it go. */
    private void gazeTick(ServerLevel level) {
        Vec3 face = position().add(0, FACE_HEIGHT, 0);
        for (Player p : fighters()) {
            if (!(p instanceof ServerPlayer sp)) continue;
            float g = Mth.clamp(gazes.getOrDefault(p.getUUID(), 0F) + (lookingAtFace(p) ? 0.012F : -0.008F), 0, 1);
            gazes.put(p.getUUID(), g);
            if (tickCount % 4 == 0) PacketDistributor.sendToPlayer(sp, new CreatorFxPayload(CreatorFxPayload.GAZE, face, face, g, 0, 0, 0, 0, 0));
            if (g >= 0.9F && tickCount % 20 == 0) {
                p.hurt(damageSources().magic(), GAZE_DAMAGE * damageScale());
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
            }
        }
    }

    private boolean lookingAtFace(Player p) {
        Vec3 to = position().add(0, FACE_HEIGHT, 0).subtract(p.getEyePosition());
        double d = to.length();
        return d < 64 && d > 0.1 && p.getViewVector(1).dot(to.scale(1 / d)) > 0.966 && p.hasLineOfSight(this);
    }

    /** Beyond the arena's edge (erased) is nothing: whoever stands out there is eaten at and pushed back. */
    private void edgeTick() {
        if (tickCount % 10 != 0 || arenaR >= ARENA) return;
        Vec3 h = home();
        for (Player p : fighters()) {
            double d = Math.hypot(p.getX() - h.x, p.getZ() - h.z);
            if (d <= arenaR + 0.5 || Math.abs(p.getY() - h.y) > 8) continue;
            p.hurt(damageSources().magic(), EDGE_DAMAGE * damageScale());
            p.setDeltaMovement(p.getDeltaMovement().add((h.x - p.getX()) / d * 0.6, 0.2, (h.z - p.getZ()) / d * 0.6));
            p.hurtMarked = true;
        }
    }

    /** 19. Erasure: white cuts through the arena; the floor under them goes white and is gone a second later - and the
     * arena's edge with it, smaller every time. */
    private void eraseTick(ServerLevel level, @Nullable LivingEntity target, int t) {
        Vec3 h = home();
        if (t == 0) {
            anim(Anim.ERASE);
            double ang = random.nextDouble() * Math.PI;
            Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            Vec3 mid = floorAt(target != null ? target.position() : h);
            lines.add(new Vec3[] {mid.subtract(dir.scale(ARENA * 2)), mid.add(dir.scale(ARENA * 2))});
            fx(CreatorFxPayload.ERASE, lines.get(0)[0], lines.get(0)[1], ERASE_WIDTH, 0, 0, 0, ERASE_WARN, ERASE_WARN + ERASE_WHITE);
            float smaller = Math.max(ARENA_MIN, arenaR - 3);
            centre = new Vec3(smaller, 0, 0);
            if (smaller < arenaR) fx(CreatorFxPayload.ERASE_RING, h, h, smaller, arenaR + 1, 0, 0, ERASE_WARN, ERASE_WARN + ERASE_WHITE);
            sound(SoundEvents.BRUSH_SAND, 2.0F, 1.8F);
        }
        if (t == ERASE_WARN) { // white: whoever is on it is cut, the world around it drained
            Vec3[] seg = lines.get(0);
            for (Player p : fighters()) if (distToSegment(p.position(), seg[0], seg[1]) <= ERASE_WIDTH / 2 + 0.4) hit(p, ERASE_DAMAGE, 0.4, 0.3);
            ScreenFx.playNear(level, h, 64, ScreenEffect.DESATURATE, 20, 0.7F, -1);
            Vec3 d = seg[1].subtract(seg[0]).normalize(), side = new Vec3(-d.z, 0, d.x);
            double len = seg[0].distanceTo(seg[1]);
            for (double s = 0; s < len; s += 0.5) {
                Vec3 p = seg[0].add(d.scale(s));
                if (Math.hypot(p.x - h.x, p.z - h.z) > arenaR + 1) continue;
                for (double o = -ERASE_WIDTH / 2; o <= ERASE_WIDTH / 2; o += 0.5) {
                    Vec3 q = floorAt(p.add(side.scale(o)));
                    toErase.add(BlockPos.containing(q.x, q.y - 1, q.z));
                }
            }
            float smaller = (float) centre.x;
            for (int x = Mth.floor(h.x - arenaR - 2); x <= h.x + arenaR + 2; x++) {
                for (int z = Mth.floor(h.z - arenaR - 2); z <= h.z + arenaR + 2; z++) {
                    double r = Math.hypot(x + 0.5 - h.x, z + 0.5 - h.z);
                    if (r < smaller || r > arenaR + 1) continue;
                    int top = Mth.floor(floorAt(new Vec3(x + 0.5, h.y, z + 0.5)).y) - 1;
                    for (int y = top; y > top - 3; y--) toErase.add(new BlockPos(x, y, z));
                }
            }
            sound(SoundEvents.BRUSH_GENERIC, 2.0F, 1.6F);
        }
        if (t == ERASE_WARN + ERASE_WHITE) { // and gone, in a scatter of white pixels
            DustParticleOptions white = new DustParticleOptions(new Vector3f(1, 1, 1), 1.0F);
            int n = 0;
            for (BlockPos p : toErase) {
                BlockState state = level.getBlockState(p);
                if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, p) < 0) continue;
                erased.add(p.asLong());
                erasedStates.add(state);
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                if (n++ % 3 == 0) level.sendParticles(white, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
            }
            toErase.clear();
            arenaR = (float) centre.x;
            sound(SoundEvents.SAND_BREAK, 1.5F, 1.8F);
        }
    }

    /** 20. Chequerboard: the floor a chequer of the missing texture; magenta hurts, black is safe; it moves on a square
     * every second, in time with its face's flicker. */
    private void checkerTick(ServerLevel level, int t) {
        Vec3 h = home();
        if (t == 0) {
            anim(Anim.CHECKER);
            fx(CreatorFxPayload.CHECKER, h, h, CHECKER_CELL, arenaR, 0, 0, CHECKER_EVERY, CHECKER_LIFE);
        }
        if (t >= CHECKER_LIFE) return;
        if (t % 10 == 0) sound(SoundEvents.NOTE_BLOCK_HAT.value(), 4.0F, t % 20 == 0 ? 2.0F : 1.5F); // a metronome
        if (t > 0 && t % CHECKER_EVERY == 0) ScreenFx.playNear(level, h, 64, ScreenEffect.GLITCH, 3, 0.7F, -1);
        if (t > 8 && t % 5 == 0) {
            int shift = t / CHECKER_EVERY;
            for (Player p : fighters()) {
                if (p.getY() - h.y > 1.2) continue;
                int i = Mth.floor((p.getX() - h.x) / CHECKER_CELL), j = Mth.floor((p.getZ() - h.z) / CHECKER_CELL);
                if (Math.floorMod(i + j + shift, 2) == 0) hit(p, CHECKER_DAMAGE, 0, 0.2);
            }
        }
    }

    /** 21. Gigabeam: light gathers before its face for three seconds, then a beam across the whole arena turns slowly
     * for five, and narrows away. */
    private void gigaTick(ServerLevel level, @Nullable LivingEntity target, int t) {
        Vec3 h = home();
        if (t == 0) {
            anim(Anim.GIGA);
            Vec3 face = position().add(0, FACE_HEIGHT, 0);
            Vec3 aim = target != null ? target.position() : h.add(1, 0, 0);
            Vec3 to = aim.subtract(face).multiply(1, 0, 1).normalize();
            Vec3 src = face.add(to.scale(1.2));
            float sweep = (random.nextBoolean() ? 1 : -1) * 1.4F;
            float yaw0 = (float) Mth.atan2(aim.z - src.z, aim.x - src.x) - sweep / 2;
            lines.add(new Vec3[] {src});
            centre = new Vec3(yaw0, sweep, 0);
            fx(CreatorFxPayload.GIGA, src, h, (float) ARENA + 6, yaw0, sweep, 0, GIGA_CHARGE, GIGA_CHARGE + GIGA_BEAM + 10);
        }
        if (t < GIGA_CHARGE && t % 6 == 0) sound(SoundEvents.BEACON_POWER_SELECT, 4.0F, 0.5F + t / 40F); // a rising note
        if (t == GIGA_CHARGE) {
            ScreenFx.playNear(level, h, 80, ScreenEffect.SHAKE, GIGA_BEAM, 0.7F, -1);
            ScreenFx.playNear(level, h, 80, ScreenEffect.ABERRATION, GIGA_BEAM, 0.8F, -1);
            sound(SoundEvents.WARDEN_SONIC_BOOM, 5.0F, 0.5F);
        }
        if (t >= GIGA_CHARGE && t < GIGA_CHARGE + GIGA_BEAM) {
            if (t % 10 == 0) { // its roar, on and on
                sound(SoundEvents.BEACON_AMBIENT, 6.0F, 0.5F);
                sound(SoundEvents.FIRE_AMBIENT, 6.0F, 0.5F);
            }
            if (t % 20 == 0) sound(SoundEvents.WARDEN_SONIC_CHARGE, 4.0F, 0.4F);
            if (t % 4 == 0) {
                Vec3 src = lines.get(0)[0];
                float yaw = (float) (centre.x + centre.y * (t - GIGA_CHARGE) / GIGA_BEAM);
                Vec3 end = new Vec3(src.x + Math.cos(yaw) * (ARENA + 6), h.y, src.z + Math.sin(yaw) * (ARENA + 6));
                for (Player p : fighters()) if (Beams.hits(p, src, end, 2.0)) hit(p, GIGA_DAMAGE, 0.3, 0.2);
            }
        }
    }

    /** 22. The act of creation (it cannot die before it is over): black and silence; its face unfolds like a flower of
     * chequered squares; one block before you; then the world is made again layer by layer from the bottom up, a wave of
     * shots at each layer, a music building with it. */
    private void creationTick(ServerLevel level, int t) {
        Vec3 h = home();
        if (t == 0) {
            anim(Anim.CREATION);
            silence(level);
            Bullets.clear(this);
            Vec3 face = position().add(0, FACE_HEIGHT, 0);
            fx(CreatorFxPayload.CREATION, h, face, (float) h.y - 6, (float) h.y + 22, 0, 0, CREATION_START, CREATION_TICKS);
        }
        int k = (t - CREATION_START) / CREATION_EVERY;
        if (t >= CREATION_START && (t - CREATION_START) % CREATION_EVERY == 0 && k < CREATION_LAYERS) {
            int n = 16 + 2 * k;
            List<Bullets.Spec> wave = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                wave.add(Bullets.polar(Bullets.PRISM, h.add(0, 1.2, 0), 1, i * Mth.TWO_PI / n + k * 0.2F, (k % 2 == 0 ? 0.01F : -0.01F), 0.3F, 0, 0,
                        80, 0.8F, 0, k));
            }
            Bullets.fire(level, this, wave, CREATION_DAMAGE * damageScale());
            // the music building with the world: more voices with every layer
            float[] scale = {0.5F, 0.6F, 0.67F, 0.75F, 0.9F, 1.0F, 1.2F, 1.35F, 1.5F, 1.8F, 2.0F};
            sound(SoundEvents.NOTE_BLOCK_HARP.value(), 4.0F, scale[k % scale.length]);
            if (k >= 3) sound(SoundEvents.NOTE_BLOCK_BELL.value(), 3.5F, scale[(k + 2) % scale.length]);
            if (k >= 6) sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 3.5F, scale[(k + 4) % scale.length]);
            if (k >= 9) sound(SoundEvents.NOTE_BLOCK_FLUTE.value(), 3.5F, scale[(k + 6) % scale.length]);
            if (k >= 11) sound(SoundEvents.NOTE_BLOCK_PLING.value(), 3.5F, scale[(k + 3) % scale.length]);
        }
        if (t == CREATION_TICKS - 1) {
            creationDone = true;
            pendingCreation = false;
            ScreenFx.playNear(level, h, 80, ScreenEffect.FLASH, 10, 0.6F, 0xFFFFFF);
            sound(SoundEvents.BEACON_ACTIVATE, 5.0F, 1.2F);
        }
    }

    /** Puts every erased block back, those nearest `from` first, as far as `reach` (all: Double.MAX_VALUE). */
    private void restore(ServerLevel level, Vec3 from, double reach) {
        for (int i = erased.size() - 1; i >= 0; i--) {
            BlockPos p = BlockPos.of(erased.get(i));
            if (p.distToCenterSqr(from) > reach * reach) continue;
            if (level.getBlockState(p).isAir()) level.setBlock(p, erasedStates.get(i), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            erased.remove(i);
            erasedStates.remove(i);
        }
    }

    // ------------------------------------------------------------------ dying

    /** The face's cube cracks and breaks into pixels, the white goes out from the edges in, and the world gets its
     * colour and its blocks back, block by block, from where it fell (the true form's death animation, 8 s). */
    @Override
    protected void tickDeath() {
        dying++;
        if (!(level() instanceof ServerLevel level)) return;
        if (dying == 1) {
            silence(level);
            Bullets.clear(this);
            anim(Anim.DEATH);
            bossBar.removeAllPlayers();
        }
        if (dying == 20) sound(SoundEvents.GLASS_BREAK, 3.0F, 1.6F);
        if (dying == 60) sound(SoundEvents.BEACON_DEACTIVATE, 4.0F, 0.6F);
        restore(level, position(), dying * 0.5);
        if (dying >= DEATH_TICKS) {
            restore(level, position(), Double.MAX_VALUE);
            dropExperience(getLastHurtByMob());
            remove(RemovalReason.KILLED);
        }
    }

    // ------------------------------------------------------------------ the change into phase 3

    /** The halves burn like paper from the edges in; the ash falls; a second of silence - and the skeleton is left. */
    private void burning(ServerLevel level) {
        int t = stateTick;
        if (t == 1) anim(Anim.BURN);
        if (t < BURN_TICKS) {
            for (int i = 0; i < 4; i++) {
                double a = random.nextDouble() * Mth.TWO_PI, r = 1 + random.nextDouble() * 3;
                level.sendParticles(i % 2 == 0 ? ModParticles.ASH.get() : ModParticles.EMBER.get(), getX() + Math.cos(a) * r,
                        getY() + random.nextDouble() * 9, getZ() + Math.sin(a) * r, 1, 0.1, 0.1, 0.1, 0.02);
            }
            if (t % 6 == 0) {
                sound(SoundEvents.FIRE_AMBIENT, 5.0F, 0.6F + random.nextFloat() * 0.4F);
                sound(SoundEvents.BLAZE_BURN, 3.0F, 0.6F);
            }
        }
        if (t == 40) ScreenFx.playNear(level, home(), 80, ScreenEffect.GLITCH, 60, 0.6F, -1);
        if (t == BURN_TICKS) silence(level); // the fire cut off: a second of silence
        if (t == BURN_TICKS + 20) entityData.set(PHASE, (byte) 3);
        if (t == BURN_TICKS + 22) {
            anim(Anim.EMERGE);
            sound(SoundEvents.BEACON_DEACTIVATE, 5.0F, 0.5F);
        }
        if (t >= BURN_TICKS + 22 + 80) setState(FIGHT);
    }

    private void silence(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(home()) < 80 * 80) p.connection.send(new ClientboundStopSoundPacket(null, null));
        }
    }

    // ------------------------------------------------------------------ the change into phase 2

    private void startTransform(ServerLevel level) {
        pendingTransform = false;
        attack = Attack.NONE;
        entityData.set(ATTACK, (byte) 0);
        driftGoal = Vec3.ZERO;
        Bullets.clear(this);
        Telegraphs.cancel(this);
        setState(TRANSFORM);
        if (phase() == 1) {
            anim(Anim.CRACK);
            sound(SoundEvents.CHAIN_STEP, 4.0F, 0.5F);
        }
    }

    private void transforming(ServerLevel level) {
        if (phase() >= 3) {
            unveiling(level);
            return;
        }
        if (phase() == 2) {
            burning(level);
            return;
        }
        int t = stateTick;
        if (t < CRACK_AT && t % 3 == 0) sound(SoundEvents.CHAIN_HIT, 2.0F, 0.6F + t / 40F); // shuddering
        if (t == CRACK_AT) { // the collage cracks down the middle, the map tears
            impactFrame(level);
            ScreenFx.playNear(level, home(), 80, ScreenEffect.ABERRATION, 20, 1.2F, -1);
            ScreenFx.shake(level, home(), 64, 16, 0.8F);
            fx(CreatorFxPayload.CRACK, position(), position().add(0, 9.5, 0), 0, 0, 0, 0, 0, DISSOLVE_FROM - CRACK_AT);
            sound(SoundEvents.BOOK_PAGE_TURN, 6.0F, 0.4F);
            sound(SoundEvents.WOOL_BREAK, 6.0F, 0.5F);
            sound(SoundEvents.GLASS_BREAK, 4.0F, 0.4F);
        }
        if (t == CRACK_AT + 4) sound(SoundEvents.BOOK_PAGE_TURN, 6.0F, 0.5F);
        if (t == DISSOLVE_FROM) { // gold on one side, violet and black on the other: a choir of two notes
            sound(SoundEvents.BEACON_POWER_SELECT, 5.0F, 0.6F);
            sound(SoundEvents.BEACON_POWER_SELECT, 5.0F, 0.9F);
            sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 5.0F, 0.5F);
        }
        if (t == DISSOLVE_FROM + 20) {
            sound(SoundEvents.BEACON_AMBIENT, 5.0F, 0.6F);
            sound(SoundEvents.BEACON_AMBIENT, 5.0F, 0.9F);
        }
        if (t >= TRANSFORM_TICKS) {
            entityData.set(PHASE, (byte) 2);
            setState(FIGHT);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void impactFrame(ServerLevel level) {
        ScreenFx.playNear(level, home(), 80, ScreenEffect.INVERT, 2, 1.0F, -1);
        ScreenFx.playNear(level, home(), 80, ScreenEffect.DESATURATE, 2, 1.0F, -1);
    }

    private void fx(byte kind, Vec3 a, Vec3 b, float f0, float f1, float f2, float f3, int delay, int life) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 h = home();
        PacketDistributor.sendToPlayersNear(level, null, h.x, h.y, h.z, 96, new CreatorFxPayload(kind, a, b, f0, f1, f2, f3, delay, life));
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY() + 4, getZ(), sound, getSoundSource(), volume, pitch);
    }

    private void face(Vec3 at, float step) {
        float want = (float) (Mth.atan2(at.z - getZ(), at.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(getYRot(), want, step);
        setYRot(yaw);
        yRotO = yaw;
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    private static Vec3 forward(float yaw) {
        return new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
    }

    private Vec3 floorAt(Vec3 at) {
        BlockPos.MutableBlockPos p = BlockPos.containing(at.x, home().y + 6, at.z).mutable();
        for (int i = 0; i < 20; i++) {
            if (!level().getBlockState(p).getCollisionShape(level(), p).isEmpty()) return new Vec3(at.x, p.getY() + 1, at.z);
            p.move(0, -1, 0);
        }
        return new Vec3(at.x, home().y, at.z);
    }

    float damageScale() {
        var attr = getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null || attr.getBaseValue() <= 0 ? 1.0F : (float) (attr.getValue() / attr.getBaseValue());
    }

    private boolean hit(LivingEntity e, float damage, double push, double up) {
        if (!e.hurt(damageSources().mobAttack(this), damage * damageScale())) return false;
        Vec3 d = e.position().subtract(position()).multiply(1, 0, 1);
        double len = Math.max(0.01, d.length());
        double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resist > 0) {
            e.setDeltaMovement(e.getDeltaMovement().add(d.x / len * push * resist, up * resist, d.z / len * push * resist));
            e.hurtMarked = true;
        }
        return true;
    }

    // ------------------------------------------------------------------ damage, saving

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (!level().isClientSide) entityData.set(TARGET_ID, target == null ? -1 : target.getId());
    }

    @Override
    public int markedTargetId() {
        return state() == FIGHT && isAlive() ? entityData.get(TARGET_ID) : -1;
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
        if (state() != FIGHT || attack == Attack.CREATION) return false; // coming, changing, making the world again
        if (phase() == 4) { // its face takes more harm
            var direct = source.getDirectEntity();
            Vec3 face = position().add(0, FACE_HEIGHT, 0);
            boolean atFace = direct != null && direct != source.getEntity() ? direct.position().distanceTo(face) < 1.6
                    : source.getEntity() instanceof Player p && lookingAtFace(p);
            if (atFace) amount *= 1.75F;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) targeting.onHurt(source, level().getGameTime());
        return hurt;
    }

    @Override
    public void setHealth(float health) {
        if (!unclamped && tickCount > 0 && !level().isClientSide && (phase() <= 3 || !creationDone)) {
            float floor = getMaxHealth() * (phase() == 1 ? 0.5F : phase() == 2 ? 0.25F : phase() == 3 ? 0.1F : 0.02F);
            if (health < floor) {
                health = floor;
                if (phase() <= 3) pendingTransform = true;
                else pendingCreation = true;
            }
        }
        super.setHealth(health);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BOOK_PAGE_TURN;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Phase", (byte) phase());
        tag.putBoolean("Awake", state() != WAITING);
        tag.putBoolean("Crushed", crushed);
        tag.putBoolean("Created", creationDone);
        tag.putFloat("ArenaRadius", arenaR);
        ListTag list = new ListTag();
        for (int i = 0; i < erased.size(); i++) {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", erased.get(i));
            c.put("State", NbtUtils.writeBlockState(erasedStates.get(i)));
            list.add(c);
        }
        tag.put("Erased", list);
        Vec3 h = home();
        tag.putDouble("HomeX", h.x);
        tag.putDouble("HomeY", h.y);
        tag.putDouble("HomeZ", h.z);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeY")) {
            home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
            entityData.set(HOME, new Vector3f((float) home.x, (float) home.y, (float) home.z));
        }
        if (tag.contains("Phase")) entityData.set(PHASE, (byte) Mth.clamp(tag.getByte("Phase"), 1, 4));
        crushed = tag.getBoolean("Crushed");
        creationDone = tag.getBoolean("Created");
        if (tag.contains("ArenaRadius")) arenaR = tag.getFloat("ArenaRadius");
        erased.clear();
        erasedStates.clear();
        for (Tag t : tag.getList("Erased", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            erased.add(c.getLong("Pos"));
            erasedStates.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), c.getCompound("State")));
        }
        if (tag.getBoolean("Awake") || phase() > 1) {
            entityData.set(STATE, FIGHT);
            entityData.set(STATE_TIME, (int) level().getGameTime());
        }
        refreshDimensions();
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }
}
