package io.github.xshoutgamer.minecraftmod.nail;

import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CelestialNail extends Entity {
    public enum Phase { SUMMONING, LEVITATING, CHARGING, DESCENDING, IMPACTING, EMBEDDED, SOLIDIFYING }

    public static final TagKey<Block> IMPACT_IMMUNE = TagKey.create(Registries.BLOCK, MinecraftMod.id("impact_immune"));
    public static final int MATERIALIZE_TICKS = 28;
    public static final int IMPACT_TICKS = 54;
    public static final int SOLIDIFY_TICKS = 48;
    private static final Phase[] PHASES = Phase.values();
    private static final EntityDataAccessor<Integer> PHASE = intData();
    private static final EntityDataAccessor<Integer> AGE = intData();
    private static final EntityDataAccessor<Integer> SURFACE_AGE = intData();
    private static final EntityDataAccessor<Integer> CHARGE_DURATION = intData();
    private static final EntityDataAccessor<Integer> RADIUS = intData();
    private static final EntityDataAccessor<Integer> WIDTH = intData();
    private static final EntityDataAccessor<Integer> CRACK_LENGTH = intData();
    private static final EntityDataAccessor<Float> HEIGHT =
            SynchedEntityData.defineId(CelestialNail.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PREVIOUS_Y =
            SynchedEntityData.defineId(CelestialNail.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> VISUAL_Y =
            SynchedEntityData.defineId(CelestialNail.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<BlockPos> TARGET =
            SynchedEntityData.defineId(CelestialNail.class, EntityDataSerializers.BLOCK_POS);

    private UUID ownerId;
    private double skyY;
    private double releaseY;
    private int descentTicks;
    private int phaseTicks, chargeTicks, persistenceTicks, craterDepth;
    private int craterCursor, crackCursor;
    private boolean terrain, visualOnly;
    private boolean waitingToSolidify;
    private BlockPos light;
    private List<FracturePattern.Column> crater;
    private List<FracturePattern.Segment> cracks;
    private List<FracturePattern.FractureCut> cuts;

    public CelestialNail(EntityType<? extends CelestialNail> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        noCulling = true;
    }

    private static EntityDataAccessor<Integer> intData() {
        return SynchedEntityData.defineId(CelestialNail.class, EntityDataSerializers.INT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHASE, 0);
        builder.define(AGE, 0);
        builder.define(SURFACE_AGE, 0);
        builder.define(TARGET, BlockPos.ZERO);
        builder.define(RADIUS, 15);
        builder.define(WIDTH, 8);
        builder.define(CHARGE_DURATION, 120);
        builder.define(CRACK_LENGTH, 145);
        builder.define(HEIGHT, 60F);
        builder.define(PREVIOUS_Y, 0F);
        builder.define(VISUAL_Y, 0F);
    }

    void begin(ServerPlayer owner, BlockPos target) {
        ownerId = owner.getUUID();
        entityData.set(TARGET, target);
        entityData.set(RADIUS, CelestiaConfig.RADIUS.get());
        entityData.set(CRACK_LENGTH, CelestiaConfig.CRACK_LENGTH.get());
        entityData.set(HEIGHT, CelestiaConfig.LENGTH.get().floatValue());
        entityData.set(WIDTH, CelestiaConfig.WIDTH.get());
        descentTicks = CelestiaConfig.DESCENT.get();
        chargeTicks = CelestiaConfig.CHARGE.get();
        entityData.set(CHARGE_DURATION, chargeTicks);
        persistenceTicks = CelestiaConfig.PERSIST.get() == -1 ? -1 : CelestiaConfig.PERSIST.get() * 20;
        craterDepth = CelestiaConfig.DEPTH.get();
        visualOnly = CelestiaConfig.VISUAL_ONLY.get();
        terrain = !visualOnly && CelestiaConfig.TERRAIN.get();
        skyY = Math.min(target.getY() + CelestiaConfig.ALTITUDE.get(), level().getMaxBuildHeight() + 192);
        releaseY = skyY;
        setPos(target.getX() + centerOffset(), skyY, target.getZ() + centerOffset());
        setOldPosAndRot();
        resetVisualY();
    }

    public Phase phase() { return PHASES[entityData.get(PHASE)]; }
    public int age() { return entityData.get(AGE); }
    public int surfaceAge() { return entityData.get(SURFACE_AGE); }
    public float modelHeight() { return entityData.get(HEIGHT); }
    public int chargeTicks() { return entityData.get(CHARGE_DURATION); }
    public int modelWidth() { return entityData.get(WIDTH); }
    public double centerOffset() { return modelWidth() % 2 == 0 ? 0 : 0.5; }
    public int baseY() {
        return terrain ? Math.max(level().getMinBuildHeight(), target().getY() + 1 - (int) modelHeight() / 2) : target().getY() + 1;
    }
    public int radius() { return entityData.get(RADIUS); }
    public BlockPos target() { return entityData.get(TARGET); }

    public float visualY(float partialTick) {
        return Mth.lerp(partialTick, entityData.get(PREVIOUS_Y), entityData.get(VISUAL_Y));
    }

    public List<FracturePattern.Segment> cracks() {
        if (cracks == null) cracks = FracturePattern.cracks(getUUID().getMostSignificantBits(), entityData.get(CRACK_LENGTH));
        return cracks;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (CRACK_LENGTH.equals(accessor)) cracks = null;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 768 * 768;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        int age = ++phaseTicks;
        if (phase() != Phase.EMBEDDED) entityData.set(AGE, age);
        entityData.set(PREVIOUS_Y, entityData.get(VISUAL_Y));
        if (phase() == Phase.IMPACTING || phase() == Phase.EMBEDDED || phase() == Phase.SOLIDIFYING) {
            entityData.set(SURFACE_AGE, Math.min(1200, surfaceAge() + 1));
            workTerrain(level);
        }
        switch (phase()) {
            case SUMMONING -> {
                if (age == 1) sound(MinecraftMod.SUMMON.get(), 1.8F, false);
                if (age >= MATERIALIZE_TICKS) {
                    advance(Phase.LEVITATING);
                    sound(MinecraftMod.GATHER.get(), 1.9F, false);
                }
            }
            case LEVITATING -> {
                moveToY(skyY + Math.sin(age * 0.16) * 0.65);
                if (age >= 12) {
                    advance(Phase.CHARGING);
                    sound(MinecraftMod.CHARGE.get(), 2.5F, false);
                }
            }
            case CHARGING -> {
                if (age == 8 || age == chargeTicks - 40) cinematicSound(MinecraftMod.DIVINE_ARC.get(), 1.22F, false);
                if (age == 31 || age == chargeTicks - 24) cinematicSound(MinecraftMod.GOLDEN_FLOW.get(), .46F, false);
                if (age < chargeTicks - 6) moveToY(skyY + Math.sin(age * 0.11) * 0.45);
                if (age >= chargeTicks) {
                    releaseY = getY();
                    advance(Phase.DESCENDING);
                    sound(MinecraftMod.DESCENT.get(), 3.2F, false);
                }
            }
            case DESCENDING -> {
                double ground = target().getY() + 1;
                moveToY(NailProfile.descent(releaseY, ground, age / (double) descentTicks));
                if (age >= descentTicks) impact(level);
            }
            case IMPACTING -> {
                double progress = Math.min(1, age / 16.0);
                moveToY(Mth.lerp(1 - (1 - progress) * (1 - progress), target().getY() + 1, baseY()));
                if (age == 5) cinematicSound(MinecraftMod.GOLDEN_FLOW.get(), 1.1F, true);
                if (age == 12 || age == 29) cinematicSound(MinecraftMod.CRIMSON_ARC.get(), age == 12 ? .46F : .64F, true);
                if (age == 8 || age == 21 || age == 39) sound(MinecraftMod.SHOCKWAVE.get(), 2F - age / 35F, true);
                if (age == 4 && !visualOnly && CelestiaConfig.LIGHT.get()) light = ImpactTerrain.placeLight(level, owner(level), target(), craterDepth);
                if (age == 12) sound(MinecraftMod.FRACTURE.get(), 2.9F, true);
                if (age == 15) sound(MinecraftMod.DEBRIS.get(), 2.4F, true);
                if (age == 22) sound(MinecraftMod.RUMBLE.get(), 3.9F, true);
                if (age >= IMPACT_TICKS) {
                    advance(Phase.EMBEDDED);
                    if (light == null && !visualOnly && CelestiaConfig.LIGHT.get()) light = ImpactTerrain.placeLight(level, owner(level), target(), craterDepth);
                }
            }
            case EMBEDDED -> {
                if (age == 20 || age == 144 || age == 300) cinematicSound(MinecraftMod.RESIDUAL_ARC.get(), .72F, true);
                if (age == 110 || age == 265) sound(MinecraftMod.RUMBLE.get(), age == 110 ? 1.6F : 0.8F, true);
                if (age % 82 == 1) sound(MinecraftMod.AMBIENT.get(), 0.8F, true);
                if (waitingToSolidify && age % 300 == 0 && owner(level) != null) {
                    advance(Phase.SOLIDIFYING);
                } else if (!waitingToSolidify && persistenceTicks >= 0 && age >= persistenceTicks
                        && (!terrain || (crater != null && craterCursor >= crater.size() && crackCursor >= cuts.size()))) {
                    advance(Phase.SOLIDIFYING);
                    sound(MinecraftMod.DISSOLVE.get(), 1.7F, true);
                } else if (age >= 72000) phaseTicks = 1;
            }
            case SOLIDIFYING -> {
                if (age >= SOLIDIFY_TICKS) {
                    if (visualOnly || NailStructure.place(level, owner(level), this, terrain, light)) discard();
                    else {
                        waitingToSolidify = true;
                        advance(Phase.EMBEDDED);
                    }
                }
            }
        }
    }

    private void impact(ServerLevel level) {
        advance(Phase.IMPACTING);
        NailImpact.send(level, this);
        sound(MinecraftMod.IMPACT.get(), 4.8F, true);
        sound(MinecraftMod.RELEASE.get(), 2.2F, true);
        cinematicSound(MinecraftMod.CRIMSON_ARC.get(), 1.3F, true);
        workTerrain(level);
        if (visualOnly) return;
        ServerPlayer owner = owner(level);
        double damage = CelestiaConfig.DAMAGE.get();
        if (damage <= 0) return;
        Vec3 center = Vec3.atCenterOf(target());
        double range = radius() * 1.15;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(target()).inflate(range),
                victim -> victim.isAlive() && !victim.isSpectator() && victim != owner)) {
            if (owner != null && victim instanceof ServerPlayer player && !owner.canHarmPlayer(player)) continue;
            double strength = 1 - victim.position().distanceTo(center) / range;
            if (strength <= 0) continue;
            if (victim.hurt(owner == null ? damageSources().magic() : damageSources().indirectMagic(this, owner),
                    (float) (damage * strength))) {
                victim.knockback(0.9 * strength, center.x - victim.getX(), center.z - victim.getZ());
            }
        }
    }

    private void workTerrain(ServerLevel level) {
        if (!terrain) return;
        ServerPlayer owner = owner(level);
        if (owner == null) return;
        if (crater == null) crater = FracturePattern.crater(getUUID().getMostSignificantBits(), radius(), craterDepth,
                target().getY() + 1 - baseY(), modelWidth());
        int budget = CelestiaConfig.TERRAIN_BUDGET.get();
        if (cuts == null) cuts = FracturePattern.cuts(cracks(), radius());
        // Share the budget between the bowl and the outward-moving front.
        int crackBudget = craterCursor < crater.size() ? budget / 2 : budget;
        int spent = 0;
        while (crackCursor < cuts.size()) {
            var cut = cuts.get(crackCursor);
            int cost = cut.depth() + 2;
            if (cut.delay() > surfaceAge() || spent + cost > crackBudget) break;
            crackCursor++;
            spent += cost;
            var floor = ImpactTerrain.surface(level, target(), cut.x(), cut.z());
            int removed = ImpactTerrain.cut(level, owner, floor, cut.depth());
            if (floor != null && removed >= cut.depth() && crackCursor % 9 == 0) {
                ImpactTerrain.residue(level, owner, floor.below(removed - 1));
            }
        }
        while (craterCursor < crater.size()) {
            var column = crater.get(craterCursor);
            int cost = column.depth() + 2;
            if (spent + cost > budget) break;
            craterCursor++;
            spent += cost;
            var floor = ImpactTerrain.surface(level, target(), column.x(), column.z());
            int removed = ImpactTerrain.cut(level, owner, floor, column.depth());
            if (floor != null && removed >= column.depth() && column.depth() >= craterDepth - 4 && craterCursor % 13 == 0) {
                ImpactTerrain.residue(level, owner, floor.below(removed - 1));
            }
        }
    }

    private ServerPlayer owner(ServerLevel level) {
        if (ownerId == null) return null;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        return owner != null && owner.level() == level ? owner : null;
    }

    private void moveToY(double y) {
        setPos(getX(), y, getZ());
        entityData.set(VISUAL_Y, (float) y);
    }

    private void resetVisualY() {
        entityData.set(PREVIOUS_Y, (float) getY());
        entityData.set(VISUAL_Y, (float) getY());
    }

    private void advance(Phase next) {
        phaseTicks = 0;
        entityData.set(PHASE, next.ordinal());
        entityData.set(AGE, 0);
    }

    private void cinematicSound(SoundEvent cue, float volume, boolean ground) {
        float gain = CelestiaConfig.CINEMATIC_SOUNDS.get().floatValue() * CelestiaConfig.VOLUME.get().floatValue();
        if (gain <= 0) return;
        double angle = (getUUID().getLeastSignificantBits() & 255) * Math.PI / 128 + phaseTicks * .07;
        double reach = cue == MinecraftMod.DIVINE_ARC.get() ? 75 : cue == MinecraftMod.GOLDEN_FLOW.get() ? 32 : 12;
        double y = ground ? target().getY() + 1 : getY() + modelHeight();
        level().playSound(null, getX() + Math.cos(angle) * reach, y, getZ() + Math.sin(angle) * reach,
                cue, SoundSource.PLAYERS, Math.min(1.8F, gain * volume), 1F);
    }

    private void sound(SoundEvent sound, float volume, boolean ground) {
        float gain = CelestiaConfig.VOLUME.get().floatValue();
        if (gain > 0 && volume > 0) level().playSound(null, getX(), ground ? target().getY() + 1 : getY(), getZ(),
                sound, SoundSource.PLAYERS, volume * gain, 1F);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && light != null && level() instanceof ServerLevel level && level.hasChunkAt(light)
                && level.getBlockState(light).is(Blocks.LIGHT)) level.removeBlock(light, false);
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putInt("Phase", phase().ordinal());
        tag.putInt("Age", phaseTicks);
        tag.putInt("SurfaceAge", surfaceAge());
        tag.putLong("Target", target().asLong());
        tag.putInt("Radius", radius());
        tag.putInt("Cracks", entityData.get(CRACK_LENGTH));
        tag.putFloat("Height", modelHeight());
        tag.putDouble("Sky", skyY);
        tag.putDouble("ReleaseY", releaseY);
        tag.putInt("Descent", descentTicks);
        tag.putInt("Width", modelWidth());
        tag.putBoolean("VisualOnly", visualOnly);
        tag.putInt("Charge", chargeTicks);
        tag.putInt("Persistence", persistenceTicks);
        tag.putInt("Depth", craterDepth);
        tag.putBoolean("Terrain", terrain);
        tag.putBoolean("WaitingToSolidify", waitingToSolidify);
        tag.putInt("CraterCursor", craterCursor);
        tag.putInt("CrackCursor", crackCursor);
        if (light != null) tag.putLong("Light", light.asLong());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        entityData.set(PHASE, Math.clamp(tag.getInt("Phase"), 0, PHASES.length - 1));
        phaseTicks = tag.getInt("Age");
        entityData.set(AGE, phase() == Phase.EMBEDDED ? 0 : phaseTicks);
        entityData.set(SURFACE_AGE, tag.getInt("SurfaceAge"));
        entityData.set(TARGET, BlockPos.of(tag.getLong("Target")));
        entityData.set(RADIUS, tag.getInt("Radius"));
        entityData.set(CRACK_LENGTH, tag.getInt("Cracks"));
        entityData.set(HEIGHT, tag.getFloat("Height"));
        skyY = tag.getDouble("Sky");
        releaseY = tag.contains("ReleaseY") ? tag.getDouble("ReleaseY") : getY();
        descentTicks = tag.contains("Descent") ? Math.max(1, tag.getInt("Descent")) : 7;
        entityData.set(WIDTH, tag.contains("Width") ? tag.getInt("Width") : 8);
        visualOnly = tag.getBoolean("VisualOnly");
        chargeTicks = tag.getInt("Charge");
        entityData.set(CHARGE_DURATION, chargeTicks);
        persistenceTicks = tag.getInt("Persistence");
        craterDepth = tag.getInt("Depth");
        terrain = tag.getBoolean("Terrain");
        waitingToSolidify = tag.getBoolean("WaitingToSolidify");
        craterCursor = tag.getInt("CraterCursor");
        crackCursor = tag.getInt("CrackCursor");
        if (tag.contains("Light")) light = BlockPos.of(tag.getLong("Light"));
        resetVisualY();
    }
}
