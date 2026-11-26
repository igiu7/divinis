package com.divinis.fishing;

import java.util.List;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A fishing pool: a see-through ring of colour on the water that may come up after a catch (FishingGame). A bobber in
 * it fishes with its bonus - more luck (rarer catches, FishingLoot) or more fishing speed (shorter waits) - the
 * more the rarer the pool; a starlit one (the starlight rod's) twice as much. Its name, in its colour, floats over it.
 * A whirlpool (the whirlpool rod's) has no tier: a bobber that comes into it gets a bite at once, and it is gone.
 * It lasts LIFE ticks (a whirlpool WHIRL_LIFE) and is never saved. Drawn by client.FishingPoolRenderer.
 */
public class FishingPool extends Entity {
    public static final float RADIUS = 2.0F;
    public static final int LIFE = 2 * 60 * 20, WHIRL_LIFE = 60 * 20;
    public static final int WHIRL_COLOR = 0x8FF0FF, LUCK = 0, SPEED = 1, WHIRL = 2;
    public static final double CHANCE = 0.3; // a pool after a catch (the old fisherman's rod: twice)

    /** The tiers, from the most common; luck and fishing speed (percent) each gives, its colour (the item rarity's), how often it comes. */
    public enum Tier {
        UNCOMMON("Uncommon", com.divinis.registry.ItemRarity.UNCOMMON.color, 1, 20, 50),
        RARE("Rare", com.divinis.registry.ItemRarity.RARE.color, 2, 40, 27),
        EPIC("Epic", com.divinis.registry.ItemRarity.EPIC.color, 4, 75, 14),
        LEGENDARY("Legendary", com.divinis.registry.ItemRarity.LEGENDARY.color, 7, 120, 6.5),
        UNIQUE("Unique", com.divinis.registry.ItemRarity.UNIQUE.color, 12, 200, 2.5);

        public final String title;
        public final int color, luck, speed;
        final double weight;

        Tier(String title, int color, int luck, int speed, double weight) {
            this.title = title;
            this.color = color;
            this.luck = luck;
            this.speed = speed;
            this.weight = weight;
        }

        static Tier roll(RandomSource random) {
            double total = 0;
            for (Tier t : values()) total += t.weight;
            double r = random.nextDouble() * total;
            for (Tier t : values()) {
                if ((r -= t.weight) < 0) return t;
            }
            return UNCOMMON;
        }
    }

    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(FishingPool.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(FishingPool.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> STARLIT = SynchedEntityData.defineId(FishingPool.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> END = SynchedEntityData.defineId(FishingPool.class, EntityDataSerializers.LONG);

    public FishingPool(EntityType<? extends FishingPool> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TIER, 0);
        builder.define(KIND, LUCK);
        builder.define(STARLIT, false);
        builder.define(END, 0L);
    }

    public Tier tier() {
        Tier[] all = Tier.values();
        return all[Mth.clamp(entityData.get(TIER), 0, all.length - 1)];
    }

    /** LUCK, SPEED or WHIRL. */
    public int kind() {
        return entityData.get(KIND);
    }

    public boolean givesLuck() {
        return kind() == LUCK;
    }

    public boolean isWhirl() {
        return kind() == WHIRL;
    }

    public boolean starlit() {
        return entityData.get(STARLIT);
    }

    public int color() {
        return isWhirl() ? WHIRL_COLOR : tier().color;
    }

    /** Its luck and fishing speed (a starlit pool's twice). */
    public int luck() {
        return kind() == LUCK ? tier().luck * (starlit() ? 2 : 1) : 0;
    }

    public int speed() {
        return kind() == SPEED ? tier().speed * (starlit() ? 2 : 1) : 0;
    }

    /** The game time it goes. */
    public long end() {
        return entityData.get(END);
    }

    public String bonus() {
        return isWhirl() ? "Instant Bite" : givesLuck() ? "+" + luck() + " Luck" : "+" + speed() + "% Fishing Speed";
    }

    private void setUp(Tier tier, int kind, boolean starlit) {
        entityData.set(TIER, tier.ordinal());
        entityData.set(KIND, kind);
        entityData.set(STARLIT, starlit && kind != WHIRL);
        entityData.set(END, level().getGameTime() + (kind == WHIRL ? WHIRL_LIFE : LIFE));
        String name = kind == WHIRL ? "Whirlpool: " + bonus() : (starlit() ? "Starlit " : "") + tier.title + " Pool: " + bonus();
        setCustomName(Component.literal(name).withColor(color()));
        setCustomNameVisible(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // sparks of its colour rising off it
            if (random.nextInt(starlit() ? 1 : 3) == 0) {
                int c = starlit() && random.nextBoolean() ? 0xFFFFFF : color();
                double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * RADIUS;
                level().addParticle(new DustParticleOptions(new Vector3f((c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F), 0.8F),
                        getX() + Math.cos(a) * r, getY() + 0.05, getZ() + Math.sin(a) * r, 0, 0.03, 0);
            }
            return;
        }
        if (level().getGameTime() >= end()) discard();
    }

    // ------------------------------------------------------------------ bonuses

    /** Whether the bobber is in this pool. */
    boolean holds(FishingHook hook) {
        double dx = hook.getX() - getX(), dz = hook.getZ() - getZ();
        return dx * dx + dz * dz <= (RADIUS + 0.3) * (RADIUS + 0.3) && Math.abs(hook.getY() - getY()) < 1.5;
    }

    private static List<FishingPool> around(FishingHook hook, double range) {
        return hook.level().getEntitiesOfClass(FishingPool.class, hook.getBoundingBox().inflate(range));
    }

    /** The best luck of the pools the bobber is in. */
    public static int luckAt(FishingHook hook) {
        int best = 0;
        for (FishingPool p : holding(hook)) best = Math.max(best, p.luck());
        return best;
    }

    /** The best fishing speed (percent) of the pools the bobber is in. */
    public static int speedAt(FishingHook hook) {
        int best = 0;
        for (FishingPool p : holding(hook)) best = Math.max(best, p.speed());
        return best;
    }

    /** The colour of the nearest pool within range, or 0 (the hound's rod tints the foam with it). */
    public static int nearestColor(FishingHook hook, double range) {
        FishingPool nearest = null;
        double best = range * range;
        for (FishingPool p : around(hook, range)) {
            double d = p.distanceToSqr(hook);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        return nearest == null ? 0 : nearest.color();
    }

    /** The pools the bobber is in. */
    static List<FishingPool> holding(FishingHook hook) {
        return around(hook, RADIUS + 1).stream().filter(p -> p.holds(hook)).toList();
    }

    /** It goes this many ticks later. */
    void extend(int ticks) {
        entityData.set(END, end() + ticks);
    }

    /** Pushes a bobber on the water towards the middle of the nearest pool within range (the pool seeker's rod). */
    static void drift(FishingHook hook, double range) {
        FishingPool nearest = null;
        double best = range * range;
        for (FishingPool p : around(hook, range)) {
            double d = p.distanceToSqr(hook);
            if (d < best && !p.isWhirl()) {
                best = d;
                nearest = p;
            }
        }
        if (nearest == null) return;
        double dx = nearest.getX() - hook.getX(), dz = nearest.getZ() - hook.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < RADIUS * 0.4) return;
        Vec3 v = hook.getDeltaMovement();
        // the bobber slows by a tenth a tick on the water: 0.006 a tick drifts it at about 1 block a second
        hook.setDeltaMovement(v.x + dx / d * 0.006, v.y, v.z + dz / d * 0.006);
        hook.hurtMarked = true;
    }

    /**
     * How the rod changes the pools coming up: how often (times), how many tiers better, how often a whirlpool comes
     * instead, starlit.
     */
    record Spawn(double more, int better, double whirl, boolean starlit) {
        static final Spawn PLAIN = new Spawn(1, 0, 0, false);
    }

    /** After a catch, now and then: a pool comes up on open water 4 - 12 blocks from the bobber. */
    static void maybeSpawn(ServerLevel level, FishingHook hook, Spawn spawn) {
        RandomSource random = hook.getRandom();
        if (random.nextDouble() >= CHANCE * spawn.more() || around(hook, 24).size() >= 2) return;
        for (int attempt = 0; attempt < 24; attempt++) {
            double a = random.nextDouble() * Math.PI * 2, d = 4 + random.nextDouble() * 8;
            Vec3 at = surface(level, BlockPos.containing(hook.getX() + Math.cos(a) * d, hook.getY(), hook.getZ() + Math.sin(a) * d));
            if (at == null) continue;
            FishingPool pool = new FishingPool(com.divinis.registry.ModEntities.FISHING_POOL.get(), level);
            pool.moveTo(at.x, at.y, at.z);
            Tier tier = Tier.values()[Math.min(Tier.values().length - 1, Tier.roll(random).ordinal() + spawn.better())];
            int kind = random.nextDouble() < spawn.whirl() ? WHIRL : random.nextBoolean() ? LUCK : SPEED;
            pool.setUp(tier, kind, spawn.starlit());
            level.addFreshEntity(pool);
            return;
        }
    }

    /** The Fisher's Call of the Pool: a pool of this tier comes up right round the bobber, if the water is wide enough. */
    public static boolean summon(ServerLevel level, FishingHook hook, Tier tier) {
        Vec3 at = surface(level, hook.blockPosition());
        if (at == null) return false;
        FishingPool pool = new FishingPool(com.divinis.registry.ModEntities.FISHING_POOL.get(), level);
        pool.moveTo(at.x, at.y, at.z);
        pool.setUp(tier, hook.getRandom().nextBoolean() ? LUCK : SPEED, false);
        level.addFreshEntity(pool);
        return true;
    }

    /** The top of the water at this column (near the given height), if the water there is wide enough for a pool. */
    private static Vec3 surface(ServerLevel level, BlockPos near) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos p = near.above(dy);
            if (!level.getFluidState(p).is(FluidTags.WATER) || !level.getBlockState(p.above()).isAir()) continue;
            for (int[] o : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, -1}}) {
                BlockPos q = p.offset(o[0], 0, o[1]);
                if (!level.getFluidState(q).is(FluidTags.WATER) || !level.getBlockState(q.above()).isAir()) return null;
            }
            return new Vec3(p.getX() + 0.5, p.getY() + level.getFluidState(p).getHeight(level, p), p.getZ() + 0.5);
        }
        return null;
    }

    // ------------------------------------------------------------------ an entity that is only a picture

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
