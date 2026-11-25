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
    private double fallSpeed;
    private double speedCap;
    private int phaseTicks, chargeTicks, persistenceTicks, craterDepth;
    private int craterCursor, crackCursor;
    private boolean terrain;
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
        builder.define(RADIUS, 40);
        builder.define(CHARGE_DURATION, 60);
        builder.define(CRACK_LENGTH, 26);
        builder.define(HEIGHT, 24F);
        builder.define(PREVIOUS_Y, 0F);
        builder.define(VISUAL_Y, 0F);
    }

    void begin(ServerPlayer owner, BlockPos target) {
        ownerId = owner.getUUID();
        entityData.set(TARGET, target);
        entityData.set(RADIUS, CelestiaConfig.RADIUS.get());
        entityData.set(CRACK_LENGTH, CelestiaConfig.CRACK_LENGTH.get());
        entityData.set(HEIGHT, CelestiaConfig.HEIGHT.get().floatValue());
        speedCap = CelestiaConfig.SPEED.get();
        chargeTicks = CelestiaConfig.CHARGE.get();
        entityData.set(CHARGE_DURATION, chargeTicks);
        persistenceTicks = CelestiaConfig.PERSIST.get() == -1 ? -1 : CelestiaConfig.PERSIST.get() * 20;
        craterDepth = CelestiaConfig.DEPTH.get();
        terrain = CelestiaConfig.TERRAIN.get();
        skyY = Math.min(target.getY() + CelestiaConfig.ALTITUDE.get(), level().getMaxBuildHeight() + 64);
        setPos(target.getX() + 0.5, skyY, target.getZ() + 0.5);
        setOldPosAndRot();
        resetVisualY();
    }

    public Phase phase() { return PHASES[entityData.get(PHASE)]; }
    public int age() { return entityData.get(AGE); }
    public int surfaceAge() { return entityData.get(SURFACE_AGE); }
    public float modelHeight() { return entityData.get(HEIGHT); }
    public int chargeTicks() { return entityData.get(CHARGE_DURATION); }
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
        return distance < 448 * 448;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        int age = ++phaseTicks;
        if (phase() != Phase.EMBEDDED) entityData.set(AGE, age);
        entityData.set(PREVIOUS_Y, entityData.get(VISUAL_Y));
        if (phase() == Phase.IMPACTING || phase() == Phase.EMBEDDED || phase() == Phase.SOLIDIFYING) {
            entityData.set(SURFACE_AGE, Math.min(720, surfaceAge() + 1));
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
                if (age < chargeTicks - 6) moveToY(skyY + Math.sin(age * 0.11) * 0.45);
                if (age >= chargeTicks) {
                    advance(Phase.DESCENDING);
                    sound(MinecraftMod.DESCENT.get(), 3.4F, false);
                }
            }
            case DESCENDING -> {
                fallSpeed = Math.min(speedCap, fallSpeed + 1.65 + age * 0.22);
                double ground = target().getY() + 0.85;
                moveToY(Math.max(ground, getY() - fallSpeed));
                if (getY() <= ground) impact(level);
            }
            case IMPACTING -> {
                double embed = terrain ? craterDepth + modelHeight() * 0.42 : 0.6;
                double buriedY = terrain
                        ? Math.max(level.getMinBuildHeight() + 1, target().getY() + 0.85 - embed)
                        : target().getY() + 1;
                double progress = Math.min(1, age / 24.0);
                moveToY(Mth.lerp(1 - (1 - progress) * (1 - progress), target().getY() + 0.85, buriedY));
                if (age == 8 || age == 21 || age == 39) sound(MinecraftMod.SHOCKWAVE.get(), 2F - age / 35F, true);
                if (age == 4 && CelestiaConfig.LIGHT.get()) light = ImpactTerrain.placeLight(level, owner(level), target(), craterDepth);
                if (age == 12) sound(MinecraftMod.FRACTURE.get(), 2.9F, true);
                if (age == 15) sound(MinecraftMod.DEBRIS.get(), 2.4F, true);
                if (age == 22) sound(MinecraftMod.RUMBLE.get(), 3.9F, true);
                if (age >= IMPACT_TICKS) {
                    advance(Phase.EMBEDDED);
                    if (light == null && CelestiaConfig.LIGHT.get()) light = ImpactTerrain.placeLight(level, owner(level), target(), craterDepth);
                }
            }
            case EMBEDDED -> {
                if (age % 82 == 1) sound(MinecraftMod.AMBIENT.get(), 0.8F, true);
                if (waitingToSolidify && age % 300 == 0 && owner(level) != null) {
                    advance(Phase.SOLIDIFYING);
                } else if (!waitingToSolidify && persistenceTicks >= 0 && age >= persistenceTicks) {
                    advance(Phase.SOLIDIFYING);
                    sound(MinecraftMod.DISSOLVE.get(), 1.7F, true);
                } else if (age >= 72000) phaseTicks = 1;
            }
            case SOLIDIFYING -> {
                if (age >= SOLIDIFY_TICKS) {
                    if (NailStructure.place(level, owner(level), this, terrain, light)) discard();
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
        sound(MinecraftMod.IMPACT.get(), 6.5F, true);
        workTerrain(level);
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
        if (crater == null) crater = FracturePattern.crater(getUUID().getMostSignificantBits(), radius(), craterDepth);
        for (int blocks = 0; blocks < 125 && craterCursor < crater.size();) {
            var column = crater.get(craterCursor++);
            var floor = ImpactTerrain.surface(level, target(), column.x(), column.z());
            if (column.depth() < 0) ImpactTerrain.rim(level, owner, floor, -column.depth());
            else ImpactTerrain.cut(level, owner, floor, column.depth());
            blocks += 2 + Math.abs(column.depth());
        }
        if (cuts == null) cuts = FracturePattern.cuts(cracks(), radius());
        for (int budget = 0; budget < 6 && crackCursor < cuts.size(); budget++) {
            var cut = cuts.get(crackCursor);
            if (cut.delay() > surfaceAge()) break;
            crackCursor++;
            var floor = ImpactTerrain.surface(level, target(), cut.x(), cut.z());
            ImpactTerrain.cut(level, owner, floor, cut.depth());
            if (floor != null && crackCursor % 4 == 0) ImpactTerrain.residue(level, owner, floor.below(cut.depth() - 1));
        }
        if (surfaceAge() == 44) ImpactTerrain.scatterRubble(level, owner, target(), radius(), getUUID().getMostSignificantBits());
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

    private void sound(SoundEvent sound, float volume, boolean ground) {
        float gain = CelestiaConfig.VOLUME.get().floatValue();
        if (gain > 0) level().playSound(null, getX(), ground ? target().getY() + 1 : getY(), getZ(),
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
        tag.putDouble("FallSpeed", fallSpeed);
        tag.putDouble("SpeedCap", speedCap);
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
        fallSpeed = tag.getDouble("FallSpeed");
        speedCap = tag.getDouble("SpeedCap");
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
