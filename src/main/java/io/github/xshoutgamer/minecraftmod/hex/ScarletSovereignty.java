package io.github.xshoutgamer.minecraftmod.hex;

import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class ScarletSovereignty {
    public static final int PRESS = 10, RELEASE = 11, CANCEL = 12, COMMAND = 13, DISMISS = 14;
    public static final int PRESENCE = 44, RELEASE_WAVE = 45, FORMATION = 46, CAPTURE = 47;
    public static final int TRANSFORMATION_AUDIO = 48, TERRAIN_FX = 49, GATE_FX = 50, DOMINION_FX = 51;
    private static final int CINEMATIC_TICKS = 200;

    public enum Phase {
        INACTIVE, AWAKENING, LEVITATING, ABSORBING, CONVERGING, CROWN_MANIFESTATION, FULLY_CHARGED,
        RELEASING, REALITY_SHATTERING, SOVEREIGN, COLLAPSING;

        public boolean charging() { return ordinal() >= AWAKENING.ordinal() && ordinal() <= FULLY_CHARGED.ordinal(); }
        public boolean reigning() { return ordinal() >= RELEASING.ordinal() && ordinal() <= SOVEREIGN.ordinal(); }
    }

    private static final Map<ServerPlayer, ScarletSovereignty> ACTIVE = new HashMap<>();
    private static final String FLIGHT = "minecraftmod.sovereigntyFlight";
    private static final String FLIGHT_SPEED = "minecraftmod.sovereigntyFlightSpeed";
    private static final String RECOVERY = "minecraftmod.sovereigntyReady";
    private static final net.minecraft.resources.ResourceLocation SPEED = MinecraftMod.id("sovereignty_speed");
    private final ServerLevel level;
    private final long chargeStart;
    private final double initialHeight;
    private final int chargeDuration;
    private final UUID terrainEvent = UUID.randomUUID();
    private final boolean restoreTerrain;
    private final boolean unlimited;
    private final int activeTicks;
    private SovereignTerrainWave terrainWave;
    private Deque<BlockPos> endingRepair;
    private long gatesCooldown;
    private boolean gatesArmed;
    private Vec3 gateA, gateB;
    private boolean terrainFinalized;
    private final Set<Projectile> projectiles = new java.util.LinkedHashSet<>();
    private final Set<BlockPos> constructs = new HashSet<>();
    private final Map<Mob, Vec3> suspended = new LinkedHashMap<>();
    private final Set<UUID> affected = new HashSet<>();
    private Phase phase = Phase.AWAKENING;
    private long released, collapse, nextCommand;
    private float radius;
    private Vec3 releaseOrigin;
    private Deque<BlockPos> repair;

    private ScarletSovereignty(ServerPlayer player, long now) {
        level = player.serverLevel();
        chargeStart = now;
        chargeDuration = HexConfig.SOVEREIGN_CHARGE_MODE.get() == HexConfig.SovereigntyCharge.CINEMATIC
                ? CINEMATIC_TICKS : HexConfig.SOVEREIGN_CHARGE.get();
        restoreTerrain = HexConfig.SOVEREIGN_TERRAIN_ENDING.get() == HexConfig.SovereigntyTerrainEnding.RESTORE;
        var duration = HexConfig.SOVEREIGN_DURATION_MODE.get();
        unlimited = duration == HexConfig.SovereigntyDuration.UNLIMITED_CREATIVE && player.isCreative();
        activeTicks = duration == HexConfig.SovereigntyDuration.STANDARD ? 1200 : HexConfig.SOVEREIGN_LIFE.get() * 20;
        initialHeight = player.getY();
    }

    static void input(ServerPlayer player, int action) {
        var current = ACTIVE.get(player);
        long now = player.level().getGameTime();
        if (!available(player)) return;
        switch (action) {
            case PRESS -> {
                if (current != null) return;
                if (now < player.getPersistentData().getLong(RECOVERY)) {
                    tell(player, "Scarlet Sovereignty is recovering.");
                    return;
                }
                current = new ScarletSovereignty(player, now);
                ACTIVE.put(player, current);
                current.sync(player, now);
                var at = player.position();
                HexDomains.scene(player, TRANSFORMATION_AUDIO, current.chargeDuration == CINEMATIC_TICKS ? 0 : 1,
                        at, at, now, current.chargeDuration, 28);
            }
            case RELEASE -> {
                if (current != null && current.phase.charging()) current.release(player, now);
            }
            case CANCEL -> {
                if (current != null && current.phase.charging()) {
                    player.getPersistentData().putLong(RECOVERY, now + 10);
                    clear(player);
                }
            }
            case DISMISS -> {
                if (current != null && current.phase.reigning()) current.end(player, now);
            }
            case COMMAND -> {
                if (current != null && current.phase.reigning()) current.command(player, now);
            }
            default -> { }
        }
    }

    private static boolean available(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isSleeping()
                && HexConfig.SOVEREIGN_ENABLED.get() && HexConfig.GAMEPLAY.get();
    }

    static Phase chargePhase(float progress) {
        if (progress < 4F / 24) return Phase.AWAKENING;
        if (progress < 7F / 24) return Phase.LEVITATING;
        if (progress < 15F / 24) return Phase.ABSORBING;
        if (progress < 20F / 24) return Phase.CONVERGING;
        if (progress < 1) return Phase.CROWN_MANIFESTATION;
        return Phase.FULLY_CHARGED;
    }

    private float charge(long now) {
        return Math.clamp((now - chargeStart) / (float) chargeDuration, 0, 1);
    }

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var current = ACTIVE.get(player);
        if (current == null) return;
        if (current.level != player.serverLevel() || !available(player)) {
            clear(player);
            return;
        }
        current.update(player, current.level.getGameTime());
    }

    private void update(ServerPlayer player, long now) {
        var before = phase;
        if (phase.charging()) {
            phase = chargePhase(charge(now));
            if (phase != Phase.AWAKENING && !player.isPassenger() && !player.isInWater() && !player.isFallFlying()) {
                double rise = Math.clamp((charge(now) - 4F / 24) * 5, 0, 1);
                double velocity = Math.clamp((initialHeight + rise * 4.8 - player.getY()) * .19, -.16, .22);
                // A short zero-rise effect distinguishes magical hovering from flight cheats on dedicated servers.
                if (!player.hasEffect(MobEffects.LEVITATION)) {
                    player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 4, -1, true, false, false));
                }
                player.setDeltaMovement(player.getDeltaMovement().multiply(.65, 0, .65).add(0, velocity + .04, 0));
                player.hurtMarked = true;
                player.resetFallDistance();
            }
        } else if (phase.reigning()) {
            long age = now - released;
            if ((!unlimited || !player.isCreative()) && age >= activeTicks) {
                end(player, now);
                return;
            }
            if (age >= 48) phase = Phase.SOVEREIGN;
            else if (age >= 10) phase = Phase.REALITY_SHATTERING;
            if (age <= 50 && age % 5 == 0) {
                float front = radius * Math.clamp((age + 1) / 42F, .10F, 1);
                catchMobs(player, releaseOrigin, front, now);
            }
            if (age % HexConfig.SOVEREIGN_AURA_INTERVAL.get() == 0) autonomous(player, now);
            if (terrainWave != null && !terrainWave.tick(player, terrainEvent,
                    restoreTerrain && !unlimited ? released + activeTicks : 0, now)) terrainWave = null;
            tickGates(player, now);
            if (HexConfig.SOVEREIGN_PROJECTILES.get()) holdProjectiles(player, now);
            holdMobs(player, now);
            if (age % 20 == 0 && player.getHealth() < player.getMaxHealth()) player.heal(1.5F);
            grantFlight(player);
            tickRepair(player, now);
        } else if (now - collapse >= HexConfig.SOVEREIGN_END.get()) {
            HexDomains.scene(player, FORMATION, 0, player.position(), player.position(), now, 22, 15);
            cue(player, MinecraftMod.HEX_COLLAPSE.get(), .7F, 1.35F);
            clear(player);
            return;
        }
        if (phase == Phase.COLLAPSING) {
            if (now - collapse >= HexConfig.SOVEREIGN_END.get() / 2) removeConstructs();
            if (endingRepair != null && !endingRepair.isEmpty()) restoreBatch(player, endingRepair, now);
        }
        if (before != phase || now % 4 == 0) sync(player, now);
    }

    private void release(ServerPlayer player, long now) {
        phase = Phase.RELEASING;
        released = now;
        radius = HexConfig.SOVEREIGN_MIN.get() + charge(now) *
                Math.max(0, HexConfig.SOVEREIGN_MAX.get() - HexConfig.SOVEREIGN_MIN.get());
        releaseOrigin = player.position();
        if (HexConfig.SOVEREIGN_TERRAIN.get() && HexTerrain.canRewrite(player)) {
            var feet = player.position();
            var hit = level.clip(new net.minecraft.world.level.ClipContext(feet, feet.subtract(0, 64, 0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            var surface = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : player.blockPosition();
            int terrainRadius = Math.min((int) radius, HexConfig.SOVEREIGN_TERRAIN_RADIUS.get());
            terrainWave = new SovereignTerrainWave(surface, terrainRadius, now);
            var ground = Vec3.atCenterOf(surface).add(0, .55, 0);
            HexDomains.scene(player, TERRAIN_FX, 1, ground, ground, now, 58, terrainRadius);
        }
        grantFlight(player);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED, .28, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        HexDomains.scene(player, RELEASE_WAVE, 0, releaseOrigin, releaseOrigin, now, 58, radius);
        cue(player, MinecraftMod.SOVEREIGN_RELEASE.get(), 2.4F, .94F);
        cue(player, MinecraftMod.SHOCKWAVE.get(), 1.8F, 1.34F);
        sync(player, now);
    }

    private static boolean eligible(ServerPlayer player, Entity entity) {
        return entity instanceof Mob && PersonalMagic.allowed(player, entity)
                && !(entity instanceof TamableAnimal animal && animal.isTame());
    }

    private void catchMobs(ServerPlayer player, Vec3 at, double range, long now) {
        for (var mob : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(range),
                mob -> eligible(player, mob) && mob.distanceToSqr(at) <= range * range)) {
            if (affected.size() >= 512 || suspended.size() >= 48) break;
            capture(player, mob, now, HexConfig.SOVEREIGN_DAMAGE.get().floatValue());
        }
    }

    private void autonomous(ServerPlayer player, long now) {
        var at = player.position();
        double range = Math.min(radius, HexConfig.SOVEREIGN_AURA_RANGE.get());
        var targets = level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(range), mob ->
                eligible(player, mob) && mob.distanceToSqr(at) <= range * range
                        && !affected.contains(mob.getUUID()) && player.hasLineOfSight(mob));
        targets.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
        for (int i = 0; i < Math.min(3, targets.size()) && affected.size() < 512 && suspended.size() < 48; i++) {
            capture(player, targets.get(i), now, HexConfig.SOVEREIGN_AURA_DAMAGE.get().floatValue());
        }
    }

    private void capture(ServerPlayer player, Mob mob, long now, float damage) {
        if (affected.size() >= 512 || suspended.size() >= 48 || !affected.add(mob.getUUID())) return;
        if (HexConfig.SOVEREIGN_DISSOLVE.get() && player.isCreative() && player.hasPermissions(2)) {
            mob.discard();
        } else if (damage > 0) {
            mob.hurt(player.damageSources().indirectMagic(player, player), damage);
        }
        var raised = mob.position().add(0, 2.1, 0);
        if (!level.noCollision(mob, mob.getBoundingBox().move(0, 2.1, 0))) raised = mob.position();
        if (mob.isAlive() && HexConfig.SOVEREIGN_SUSPEND.get()) suspended.put(mob, raised);
        HexDomains.scene(player, CAPTURE, mob.isRemoved() ? 2 : 0, mob.position(), raised, now, 28, 2.4F);
        HexDomains.scene(player, DOMINION_FX, 0, player.getEyePosition().subtract(0, .7, 0),
                raised, now, 22, 2.5F);
    }

    private void holdMobs(ServerPlayer player, long now) {
        suspended.entrySet().removeIf(entry -> {
            var mob = entry.getKey();
            if (!eligible(player, mob) || mob.level() != level || mob.distanceToSqr(player) > radius * radius * 1.44) return true;
            mob.setTarget(null);
            mob.getNavigation().stop();
            Vec3 goal = entry.getValue().add(0, Math.sin(now * .11 + mob.getId()) * .21, 0);
            if (phase == Phase.SOVEREIGN && mob.distanceToSqr(player) < 576) {
                double angle = now * .025 + mob.getId() * 2.399963;
                double ring = 7 + Math.floorMod(mob.getId(), 5) * 2.6;
                var orbit = player.position().add(Math.cos(angle) * ring, 3 + ring * .36, Math.sin(angle) * ring);
                if (level.hasChunkAt(BlockPos.containing(orbit)) && level.noCollision(mob,
                        mob.getBoundingBox().move(orbit.subtract(mob.position())))) goal = orbit;
            }
            var delta = goal.subtract(mob.position());
            Vec3 velocity = delta.lengthSqr() > 7 ? delta.normalize().scale(.55) : delta.scale(.22);
            velocity = velocity.add(0, .052, 0);
            mob.setDeltaMovement(velocity);
            mob.hasImpulse = true;
            if (now % 16 == 0) HexDomains.scene(player, CAPTURE, 0, mob.position(), mob.getBoundingBox().getCenter(), now, 20, 1.8F);
            return false;
        });
    }

    private void holdProjectiles(ServerPlayer player, long now) {
        var at = player.position();
        double shield = Math.min(26, radius);
        if (now % 2 == 0 && projectiles.size() < 40) {
            for (var shot : level.getEntitiesOfClass(Projectile.class, new AABB(at, at).inflate(shield),
                    shot -> shot.getOwner() != null && eligible(player, shot.getOwner()) && shot.distanceToSqr(at) <= shield * shield)) {
                if (projectiles.size() >= 40) break;
                if (shot.getDeltaMovement().lengthSqr() > .01) projectiles.add(shot);
            }
        }
        int index = 0;
        for (Iterator<Projectile> iter = projectiles.iterator(); iter.hasNext();) {
            var shot = iter.next();
            if (shot.isRemoved() || shot.level() != level) {
                iter.remove();
                continue;
            }
            double angle = now * .045 + index * 2.399963;
            double ring = 4.6 + (index++ % 4) * 1.15;
            Vec3 next = at.add(Math.cos(angle) * ring, 2.7 + ring * .4, Math.sin(angle) * ring);
            Vec3 delta = next.subtract(shot.position());
            if (level.hasChunkAt(BlockPos.containing(next)) && level.noCollision(shot, shot.getBoundingBox().move(delta))) {
                shot.setPos(next);
                shot.setNoGravity(true);
                shot.setDeltaMovement(Vec3.ZERO);
                shot.hurtMarked = true;
            } else {
                shot.discard();
                iter.remove();
            }
            if (now % 12 == 0) HexDomains.scene(player, CAPTURE, 1, next, next, now, 14, 1.1F);
        }
    }

    private void command(ServerPlayer player, long now) {
        if (now < nextCommand) return;
        nextCommand = now + 20;
        var start = player.getEyePosition();
        var hit = HexAbilities.trace(player, radius);
        boolean ground = hit != null && hit.getType() == HitResult.Type.BLOCK;
        Vec3 target = ground ? hit.getLocation() : start.add(player.getLookAngle().scale(Math.min(radius, 48)));
        var mob = ProjectileUtil.getEntityHitResult(level, player, start, target, new AABB(start, target).inflate(1.5),
                entity -> eligible(player, entity));
        if (mob != null && mob.getEntity() instanceof Mob targetMob) {
            capture(player, targetMob, now, HexConfig.SOVEREIGN_DAMAGE.get().floatValue());
            cue(player, MinecraftMod.CHAOS_SOUND.get(), .8F, 1.15F);
            return;
        }
        var pos = ground ? hit.getBlockPos() : BlockPos.containing(target);
        if (ground && player.getMainHandItem().is(net.minecraft.world.item.Items.AMETHYST_SHARD)) {
            openGates(player, target);
            return;
        }
        if (ground && repair == null && HexTerrain.canRestore(player)) {
            var work = HexTerrain.get(level).reconstruction(player.getUUID(), pos, HexConfig.GENESIS_RADIUS.get());
            if (!work.isEmpty()) {
                repair = work;
                cue(player, MinecraftMod.SOVEREIGN_CROWN.get(), .76F, .92F);
                tell(player, "Reality Command is restoring " + work.size() + " journaled blocks.");
                return;
            }
        }
        boolean sky = !ground && player.getXRot() < -24;
        if (!sky && HexConfig.SOVEREIGN_CONSTRUCTS.get() && constructs.size() < 420) {
            int life = unlimited ? 2400 : Math.clamp((int) (released + activeTicks - now), 1, activeTicks);
            var anchor = ground ? pos.relative(hit.getDirection())
                    : BlockPos.containing(start.add(player.getLookAngle().scale(12)).subtract(0, 1.5, 0));
            int placed = DarkholdFields.platform(player, anchor, 3, life, constructs::add);
            if (ground && placed > 0) {
                var direction = net.minecraft.core.Direction.fromYRot(player.getYRot());
                for (int i = 3; i <= 12; i += 3) {
                    DarkholdFields.platform(player, anchor.relative(direction, i).above(i / 6), 2, life, constructs::add);
                }
            }
            if (placed > 0) {
                var core = Vec3.atCenterOf(anchor);
                HexDomains.scene(player, FORMATION, 1, player.position(), core, now, 62, 9);
                cue(player, MinecraftMod.SOVEREIGN_CROWN.get(), .84F, 1.28F);
                return;
            }
        }
        HexDomains.scene(player, FORMATION, 2, player.position(), target, now, 160, sky ? 38 : 9);
        cue(player, MinecraftMod.SOVEREIGN_CURRENT.get(), .83F, .75F);
    }

    private void openGates(ServerPlayer player, Vec3 target) {
        var a = HexAbilities.safeArrival(player, player.position());
        var b = HexAbilities.safeArrival(player, target.add(0, .12, 0));
        if (a == null || b == null || a.distanceToSqr(b) < 25 || a.distanceToSqr(b) > radius * radius) {
            tell(player, "Aim at a clear, loaded landing spot within Sovereignty range.");
            return;
        }
        gateA = a;
        gateB = b;
        gatesCooldown = level.getGameTime() + 12;
        gatesArmed = false;
        tell(player, "Linked two Scarlet Fractures. Step away and re-enter to travel.");
        cue(player, MinecraftMod.DARKHOLD_SOUND.get(), 1, 1.36F);
    }

    private void tickGates(ServerPlayer player, long now) {
        if (gateA == null) return;
        if (now % 8 == 0) {
            HexDomains.scene(player, GATE_FX, 0, gateA, gateA, now, 12, 2.5F);
            HexDomains.scene(player, GATE_FX, 1, gateB, gateB, now, 12, 2.5F);
        }
        var destination = player.distanceToSqr(gateA) < 1.2 ? gateB : player.distanceToSqr(gateB) < 1.2 ? gateA : null;
        if (destination == null) {
            gatesArmed = true;
            return;
        }
        if (!gatesArmed || now < gatesCooldown) return;
        gatesArmed = false;
        var safe = HexAbilities.safeArrival(player, destination);
        gatesCooldown = now + 36;
        if (safe != null && safe.distanceToSqr(destination) <= 9) {
            player.connection.teleport(safe.x, safe.y, safe.z, player.getYRot(), player.getXRot());
            cue(player, MinecraftMod.DARKHOLD_SOUND.get(), .9F, 1.25F);
        }
    }

    private void tickRepair(ServerPlayer player, long now) {
        if (repair == null) return;
        restoreBatch(player, repair, now);
        if (repair.isEmpty()) repair = null;
    }

    private void restoreBatch(ServerPlayer player, Deque<BlockPos> work, long now) {
        var journal = HexTerrain.get(level);
        int budget = journal.claimReconstructionBudget(now, 1.5);
        for (int i = 0; i < budget && !work.isEmpty(); i++) {
            var pos = work.removeFirst();
            if (journal.restore(player, pos) && now % 3 == 0 && i % Math.max(8, budget / 5) == 0) {
                var at = Vec3.atCenterOf(pos);
                HexDomains.scene(player, HexNetwork.RECONSTRUCTION_FX, 0, at, at, now, 32, 5);
            }
        }
    }

    private void finalizeTerrain(ServerPlayer player, long now) {
        terrainWave = null;
        if (terrainFinalized) return;
        terrainFinalized = true;
        if (restoreTerrain && releaseOrigin != null) {
            endingRepair = HexTerrain.get(level).expireEvent(player.getUUID(), terrainEvent, Math.max(1, now));
        }
    }

    private void end(ServerPlayer player, long now) {
        phase = Phase.COLLAPSING;
        collapse = now;
        finalizeTerrain(player, now);
        gateA = gateB = null;
        player.getPersistentData().putLong(RECOVERY, now + HexConfig.SOVEREIGN_END.get() + HexConfig.SOVEREIGN_RECOVERY.get() * 20L);
        removePower(player);
        cue(player, MinecraftMod.HEX_COLLAPSE.get(), 1.1F, .8F);
        sync(player, now);
    }

    private void sync(ServerPlayer player, long now) {
        float progress = phase.charging() ? charge(now)
                : phase == Phase.COLLAPSING ? 1 - Math.clamp((now - collapse) / (float) HexConfig.SOVEREIGN_END.get(), 0, 1) : 1;
        float sky = HexConfig.SOVEREIGN_SKY.get();
        Vec3 target = player.position();
        HexDomains.scene(player, PRESENCE, phase.ordinal(), new Vec3(target.x, target.y + progress, target.z), target, now, 12, sky);
    }

    private void removePower(ServerPlayer player) {
        recoverFlight(player);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED);
        long now = level.getGameTime();
        if (player.serverLevel() == level) {
            for (var shot : projectiles) {
                if (!shot.isRemoved()) {
                    var at = shot.position();
                    HexDomains.scene(player, CAPTURE, 1, at, at, now, 18, 1.2F);
                }
            }
        }
        for (var shot : projectiles) if (!shot.isRemoved()) shot.discard();
        projectiles.clear();
        suspended.clear();
        repair = null;
    }

    private void removeConstructs() {
        var block = io.github.xshoutgamer.minecraftmod.CelestialBlocks.ARCANE_PLATFORM.get();
        for (var pos : constructs) {
            if (level.hasChunkAt(pos) && level.getBlockState(pos).is(block)) {
                level.removeBlock(pos, false);
            }
        }
        constructs.clear();
    }

    private static void grantFlight(ServerPlayer player) {
        if (!HexConfig.SOVEREIGN_FLIGHT.get()) return;
        if (!player.getPersistentData().contains(FLIGHT_SPEED)) {
            float speed = player.getAbilities().getFlyingSpeed();
            player.getPersistentData().putFloat(FLIGHT_SPEED, speed);
            player.getAbilities().setFlyingSpeed(speed * 1.33F);
            player.onUpdateAbilities();
        }
        if (player.getAbilities().mayfly) return;
        player.getPersistentData().putBoolean(FLIGHT, true);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    private static void recoverFlight(ServerPlayer player) {
        boolean speedChanged = player.getPersistentData().contains(FLIGHT_SPEED);
        if (speedChanged) {
            player.getAbilities().setFlyingSpeed(player.getPersistentData().getFloat(FLIGHT_SPEED));
            player.getPersistentData().remove(FLIGHT_SPEED);
        }
        if (!player.getPersistentData().getBoolean(FLIGHT)) {
            if (speedChanged) player.onUpdateAbilities();
            return;
        }
        player.getPersistentData().remove(FLIGHT);
        if (player.isCreative() || player.isSpectator()) {
            if (speedChanged) player.onUpdateAbilities();
            return;
        }
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        if (player.isAlive() && !player.onGround() && !player.hasEffect(MobEffects.SLOW_FALLING)) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, true, false, false));
        }
    }

    static boolean empowered(ServerPlayer player) {
        var state = ACTIVE.get(player);
        return state != null && state.phase.reigning() && player.serverLevel() == state.level;
    }

    static double reach(ServerPlayer player) { return empowered(player) ? HexConfig.SOVEREIGN_REACH.get() : 1; }

    static int recovery(ServerPlayer player, int ticks) {
        return empowered(player) ? (int) Math.round(ticks * HexConfig.SOVEREIGN_SPELL_RECOVERY.get()) : ticks;
    }

    public static void impact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {
        var shot = event.getProjectile();
        if (!(shot.level() instanceof ServerLevel) || !HexConfig.SOVEREIGN_PROJECTILES.get()) return;
        if (ACTIVE.values().stream().anyMatch(state -> state.projectiles.contains(shot))) {
            event.setCanceled(true);
            shot.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() instanceof ServerPlayer player && empowered(player)
                && eligible(player, shot.getOwner())) {
            var state = ACTIVE.get(player);
            if (state.projectiles.size() < 40) {
                state.projectiles.add(shot);
                shot.setDeltaMovement(Vec3.ZERO);
            } else {
                shot.discard();
            }
            event.setCanceled(true);
        }
    }

    public static void target(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        if (event.getEntity().level() instanceof ServerLevel && event.getNewAboutToBeSetTarget() != null
                && suspended(event.getEntity())) event.setCanceled(true);
    }

    private static boolean suspended(Entity entity) {
        return entity instanceof Mob mob && ACTIVE.values().stream().anyMatch(state -> state.suspended.containsKey(mob));
    }

    private static boolean ward(Entity entity) {
        return entity instanceof ServerPlayer player && empowered(player) && HexConfig.SOVEREIGN_WARD.get();
    }

    public static void damage(LivingDamageEvent.Pre event) {
        if (ward(event.getEntity()) || suspended(event.getSource().getEntity())) event.setNewDamage(0);
    }

    public static void knockback(LivingKnockBackEvent event) {
        if (ward(event.getEntity())) event.setCanceled(true);
    }

    static void clear(ServerPlayer player) {
        var current = ACTIVE.remove(player);
        if (current == null) return;
        if (current.phase.reigning()) {
            player.getPersistentData().putLong(RECOVERY, current.level.getGameTime() + HexConfig.SOVEREIGN_RECOVERY.get() * 20L);
        }
        current.finalizeTerrain(player, current.level.getGameTime());
        current.removePower(player);
        current.removeConstructs();
        if (current.phase.charging()) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(1, 0, 1));
            player.hurtMarked = true;
            player.resetFallDistance();
        }
        var pos = player.position();
        HexDomains.scene(player, PRESENCE, Phase.INACTIVE.ordinal(), pos, pos, player.level().getGameTime(), 1, 0);
    }

    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) recoverFlight(player);
    }

    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    private static void cue(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        HexDomains.sound(player, player.position(), sound, volume, pitch);
    }

    private static void tell(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }
}
