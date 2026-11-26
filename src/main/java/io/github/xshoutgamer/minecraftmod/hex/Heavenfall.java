package io.github.xshoutgamer.minecraftmod.hex;

import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class Heavenfall {
    public static final int PRESS = 15, RELEASE = 16, CANCEL = 17;
    public static final int AIM = 52, APERTURE = 53, COLUMN = 54, IMPACT = 55, RESIDUE = 56, GROUND = 57;
    private static final String COOLDOWN = "minecraftmod.heavenfallReady";
    private static final HeavenfallConfig CONFIG = HexConfig.HEAVENFALL;
    public enum Phase {
        TARGETING, CHARGING, HEAVEN_OPENING, ENERGY_CONVERGENCE, DESCENT, SUSTAINED, COLLAPSING, AFTERMATH, COMPLETE
    }

    private record Charge(ServerLevel level, long start) {}
    private record Target(Vec3 point, BlockPos block) {}
    private static final Map<ServerPlayer, Charge> CHARGES = new HashMap<>();
    private static final Map<ServerLevel, ArrayList<Heavenfall>> STRIKES = new HashMap<>();
    private final UUID caster;
    private final UUID event = UUID.randomUUID();
    private final Target target;
    private final HeavenfallConfig.Scale scale;
    private final Vec3 sky;
    private Vec3 columnContact;
    private final Vec3 hand;
    private final int aftermath;
    private final long start;
    private Phase phase = Phase.HEAVEN_OPENING;
    private HeavenfallTerrain crater, corruption;
    private final boolean editsTerrain;
    private boolean journalWarning;
    private int edits;

    private Heavenfall(ServerPlayer player, Target target, HeavenfallConfig.Scale scale, long now) {
        caster = player.getUUID();
        this.target = target;
        columnContact = target.point;
        this.scale = scale;
        start = now;
        hand = player.getEyePosition().add(player.getLookAngle().scale(.65)).subtract(0, .35, 0);
        double altitude = Math.min(player.level().getMaxBuildHeight() - 2, target.point.y + CONFIG.altitude.get());
        sky = new Vec3(target.point.x, Math.max(target.point.y + 2, altitude), target.point.z);
        aftermath = CONFIG.aftermath.get() * 20;
        editsTerrain = CONFIG.terrain.get() && HexTerrain.canRewrite(player);
    }

    public static boolean enabled() { return CONFIG.enabled.get(); }

    static void input(ServerPlayer player, int action) {
        long now = player.level().getGameTime();
        if (action == CANCEL || !CONFIG.enabled.get() || !ScarletSovereignty.empowered(player)) {
            CHARGES.remove(player);
            return;
        }
        if (action == PRESS) {
            if (CHARGES.containsKey(player)) return;
            if (now < player.getPersistentData().getLong(COOLDOWN)) {
                tell(player, "Heavenfall is recovering.");
                return;
            }
            var charge = new Charge(player.serverLevel(), now);
            CHARGES.put(player, charge);
            preview(player, charge, now);
            return;
        }
        var charge = CHARGES.remove(player);
        if (charge == null || !valid(player, charge, now)) return;
        var target = target(player);
        if (target == null) {
            tell(player, "Aim Heavenfall at a loaded surface, water, or mob.");
            return;
        }
        var strikes = STRIKES.computeIfAbsent(player.serverLevel(), ignored -> new ArrayList<>());
        long owned = strikes.stream().filter(strike -> strike.caster.equals(player.getUUID())
                && strike.phase != Phase.AFTERMATH).count();
        if (owned >= CONFIG.ownerLimit.get() || strikes.size() >= CONFIG.dimensionLimit.get()) {
            tell(player, "Too many Heavenfall strikes are active in this dimension.");
            return;
        }
        // A tap always uses the standard profile. Holding beyond four ticks scales the cast.
        float strength = Math.clamp((now - charge.start - 4) / (float) (CONFIG.charge.get() - 4), 0, 1);
        var strike = new Heavenfall(player, target, CONFIG.scale(strength), now);
        strikes.add(strike);
        int cooldown = player.isCreative() ? CONFIG.creativeCooldown.get() : CONFIG.survivalCooldown.get();
        player.getPersistentData().putLong(COOLDOWN, now + cooldown);
        strike.broadcast(player.serverLevel(), AIM, Phase.HEAVEN_OPENING, strike.hand, 22, strike.scale.crater(), now);
        strike.cue(player, MinecraftMod.HEAVENFALL_OPEN.get(), 1.4F, 1);
    }

    private static Target target(ServerPlayer player) {
        var level = player.serverLevel();
        var eye = player.getEyePosition();
        double range = CONFIG.reach.get();
        var hit = HexAbilities.trace(player, range, ClipContext.Fluid.ANY);
        var end = hit == null ? eye.add(player.getLookAngle().scale(range)) : hit.getLocation();
        var entity = ProjectileUtil.getEntityHitResult(level, player, eye, end, new AABB(eye, end).inflate(.8),
                candidate -> candidate instanceof Mob mob && eligible(player, mob));
        if (entity != null && level.hasChunkAt(BlockPos.containing(entity.getLocation()))
                && level.getWorldBorder().isWithinBounds(entity.getEntity().blockPosition())) {
            return new Target(entity.getLocation(), entity.getEntity().blockPosition());
        }
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !level.hasChunkAt(hit.getBlockPos())
                || !level.getWorldBorder().isWithinBounds(hit.getBlockPos())) return null;
        return new Target(hit.getLocation(), hit.getBlockPos());
    }

    private static boolean eligible(ServerPlayer player, Mob mob) {
        return PersonalMagic.allowed(player, mob) && !(mob instanceof TamableAnimal pet && pet.isTame());
    }

    private static void preview(ServerPlayer player, Charge charge, long now) {
        var target = target(player);
        if (target == null) return;
        var at = player.getEyePosition().subtract(0, .3, 0);
        float progress = Math.clamp((now - charge.start) / (float) CONFIG.charge.get(), 0, 1);
        Phase phase = now == charge.start ? Phase.TARGETING : Phase.CHARGING;
        HexDomains.scene(player, AIM, phase.ordinal(), at, target.point, now, 6, 3 + 5 * progress);
    }

    static void cancel(ServerPlayer player) { CHARGES.remove(player); }

    public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            STRIKES.remove(level);
            CHARGES.values().removeIf(charge -> charge.level == level);
        }
    }

    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        for (var player : level.players()) {
            var charge = CHARGES.get(player);
            if (charge == null) continue;
            if (!valid(player, charge, now)) cancel(player);
            else if (now % 4 == 0) preview(player, charge, now);
        }
        var strikes = STRIKES.get(level);
        if (strikes == null) return;
        long workers = strikes.stream().filter(strike -> strike.editsTerrain && (strike.phase.ordinal() < Phase.SUSTAINED.ordinal() || strike.terrainPending())).count();
        int budget = CONFIG.terrainBudget.get() / Math.max(1, (int) workers);
        strikes.removeIf(strike -> !strike.tickStrike(level, now, budget));
        if (strikes.isEmpty()) STRIKES.remove(level);
    }

    private static boolean valid(ServerPlayer player, Charge charge, long now) {
        return player.isAlive() && player.serverLevel() == charge.level && ScarletSovereignty.empowered(player)
                && CONFIG.enabled.get() && now - charge.start <= CONFIG.charge.get() + 1200L;
    }

    private boolean tickStrike(ServerLevel level, long now, int budget) {
        int age = (int) (now - start);
        int impact = 66;
        int end = impact + scale.ticks();
        int residue = end + 42;
        var player = level.getPlayerByUUID(caster) instanceof ServerPlayer owner ? owner : null;
        var next = age < 24 ? Phase.HEAVEN_OPENING : age < 58 ? Phase.ENERGY_CONVERGENCE : age < impact ? Phase.DESCENT
                : age < end ? Phase.SUSTAINED : age < residue ? Phase.COLLAPSING
                : age < residue + aftermath || terrainPending() && age < residue + 400 ? Phase.AFTERMATH : Phase.COMPLETE;
        if (next == Phase.COMPLETE) return false;
        boolean changed = next != phase || age <= 1;
        phase = next;
        if (phase == Phase.DESCENT && changed && player != null) cue(player, MinecraftMod.HEAVENFALL_DESCENT.get(), 2.2F, 1);
        if (phase == Phase.SUSTAINED) {
            if (changed) {
                broadcast(level, IMPACT, phase, sky, 44, scale.crater() * 2F, now);
                if (player != null) {
                    cue(player, MinecraftMod.SOVEREIGN_RELEASE.get(), 2.7F, .79F);
                    cue(player, MinecraftMod.IMPACT.get(), 2.5F, .65F);
                    if (editsTerrain) {
                        crater = new HeavenfallTerrain(target.block, scale, event, true, sky);
                        corruption = new HeavenfallTerrain(target.block, scale, event, false);
                    }
                }
            }
            if (player != null && player.isAlive() && (age - impact) % CONFIG.pulseInterval.get() == 0) {
                pulse(player, age == impact, now);
                cue(player, MinecraftMod.HEAVENFALL_DESCENT.get(), .85F, .58F);
            }
        }
        if (phase == Phase.COLLAPSING && changed && player != null) cue(player, MinecraftMod.HEX_COLLAPSE.get(), 1.1F, .65F);
        if (crater != null && player != null && player.isAlive() && CONFIG.terrain.get() && HexTerrain.canRewrite(player)) {
            editTerrain(player, now, budget, Math.clamp((age - impact + 1) / (float) scale.ticks(), 0, 1));
        }
        boolean contactChanged = false;
        if ((phase == Phase.SUSTAINED || phase == Phase.COLLAPSING) && crater != null && crater.centerExcavated()) {
            var contact = craterContact(level, target.block, target.point);
            contactChanged = !contact.equals(columnContact);
            columnContact = contact;
        }
        if (changed || age % 20 == 0) sync(level, impact, end, residue);
        else if (contactChanged) syncColumn(level, impact, end);
        if (phase == Phase.AFTERMATH && player != null && edits > 0 && !terrainPending()) {
            tell(player, "Heavenfall recorded " + edits + " terrain changes. Use Genesis or /hexterrain events to restore them.");
            edits = 0;
        }
        return true;
    }

    static Vec3 craterContact(ServerLevel level, BlockPos column, Vec3 impact) {
        if (!level.hasChunkAt(column)) return impact;
        // Sample just inside the hit block so a strike on a face or edge follows the excavated column.
        double x = Math.clamp(impact.x, column.getX() + .001, column.getX() + .999);
        double z = Math.clamp(impact.z, column.getZ() + .001, column.getZ() + .999);
        var pos = new BlockPos.MutableBlockPos();
        double localX = x - column.getX();
        double localZ = z - column.getZ();
        int startY = Math.min(level.getMaxBuildHeight() - 1, BlockPos.containing(impact).getY());
        for (int y = startY; y >= level.getMinBuildHeight(); y--) {
            pos.set(column.getX(), y, column.getZ());
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            double top = Double.NEGATIVE_INFINITY;
            for (var box : shape.toAabbs()) {
                if (localX >= box.minX && localX <= box.maxX && localZ >= box.minZ && localZ <= box.maxZ
                        && y + box.minY <= impact.y + .001) top = Math.max(top, y + box.maxY);
            }
            if (top != Double.NEGATIVE_INFINITY) return new Vec3(impact.x, Math.min(impact.y, top), impact.z);
        }
        return new Vec3(impact.x, level.getMinBuildHeight(), impact.z);
    }

    private boolean terrainPending() {
        return crater != null && (!crater.finished() || !corruption.finished());
    }

    private void editTerrain(ServerPlayer player, long now, int budget, float progress) {
        var journal = HexTerrain.get(player.serverLevel());
        if (journal.full()) {
            if (!journalWarning) tell(player, "Heavenfall terrain edits stopped because the undo journal is full.");
            journalWarning = true;
            crater = corruption = null;
            return;
        }
        if (crater.finished() && corruption.finished()) return;
        int cut = crater.tick(player, budget, Math.min(1, progress * 1.8F));
        int spread = crater.finished() && budget > cut ? corruption.tick(player, budget - cut, progress) : 0;
        edits += cut + spread;
        if (now % 6 == 0 && (cut > 0 || spread > 0)) {
            float radius = spread > 0 ? corruption.editedRadius() : crater.editedRadius();
            broadcast(player.serverLevel(), GROUND, Phase.SUSTAINED, sky, 9, Math.max(2, radius), now);
        }
    }

    private void pulse(ServerPlayer player, boolean first, long now) {
        var at = target.point;
        double range = first ? Math.max(scale.crater(), scale.corruption()) : scale.beam();
        var area = new AABB(at, sky).inflate(range, 3, range);
        var mobs = player.level().getEntitiesOfClass(Mob.class, area, mob -> eligible(player, mob));
        int affected = 0;
        for (var mob : mobs) {
            double distance = mob.position().subtract(at).horizontalDistance();
            if (distance > range || distance > scale.beam() && Math.abs(mob.getY() - at.y) > range * .65) continue;
            if (distance <= scale.beam()) mob.hurt(player.damageSources().indirectMagic(player, player), CONFIG.damage.get().floatValue());
            if (first) {
                double force = CONFIG.knockback.get() * Math.max(0, 1 - distance / range);
                mob.knockback(force, at.x - mob.getX(), at.z - mob.getZ());
                int time = CONFIG.corruptionTicks.get();
                if (time > 0) {
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, time, 1));
                    mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, time));
                }
            }
            if (affected < 10) HexDomains.scene(player, ScarletSovereignty.CAPTURE, 2, at,
                    mob.getBoundingBox().getCenter(), now, 26, 2.3F);
            if (++affected >= 96) break;
        }
    }

    private void sync(ServerLevel level, int impact, int end, int residue) {
        switch (phase) {
            case HEAVEN_OPENING -> broadcast(level, APERTURE, phase, sky, 24, scale.aperture(), start);
            case ENERGY_CONVERGENCE -> broadcast(level, APERTURE, phase, sky, 34, scale.aperture(), start + 24);
            case DESCENT -> {
                broadcast(level, APERTURE, phase, sky, 8, scale.aperture(), start + 58);
            }
            case SUSTAINED -> {
                broadcast(level, APERTURE, phase, sky, scale.ticks(), scale.aperture(), start + impact);
            }
            case COLLAPSING -> {
                broadcast(level, APERTURE, phase, sky, 42, scale.aperture(), start + end);
            }
            case AFTERMATH -> {
                if (aftermath > 0) broadcast(level, RESIDUE, phase, sky, aftermath, scale.crater(), start + residue);
            }
            default -> { }
        }
        syncColumn(level, impact, end);
    }

    private void syncColumn(ServerLevel level, int impact, int end) {
        switch (phase) {
            case DESCENT -> broadcast(level, COLUMN, phase, sky, 8, scale.beam(), start + 58);
            case SUSTAINED -> broadcast(level, COLUMN, phase, sky, scale.ticks(), scale.beam(), start + impact);
            case COLLAPSING -> broadcast(level, COLUMN, phase, sky, 42, scale.beam(), start + end);
            default -> { }
        }
    }

    private void broadcast(ServerLevel level, int kind, Phase phase, Vec3 origin, int duration, float radius, long from) {
        var scene = new HexNetwork.Scene(level.dimension().location(), caster, origin, kind == COLUMN ? columnContact : target.point, from,
                duration, kind, phase.ordinal(), radius);
        double range = (double) scale.range() * scale.range();
        for (var viewer : level.players()) {
            if (viewer.getUUID().equals(caster) || viewer.distanceToSqr(target.point) < range
                    || viewer.distanceToSqr(origin) < range) scene.send(viewer);
        }
    }

    private void cue(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        HexDomains.sound(player, target.point, sound, volume, pitch);
    }

    private static void tell(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }
}
