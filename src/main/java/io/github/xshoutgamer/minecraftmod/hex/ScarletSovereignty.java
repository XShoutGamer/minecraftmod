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
import net.minecraft.world.entity.monster.Enemy;
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
    public static final int PRESS = 10, RELEASE = 11, CANCEL = 12, COMMAND = 13;
    public static final int PRESENCE = 44, RELEASE_WAVE = 45, FORMATION = 46, CAPTURE = 47;
    public enum Phase {
        INACTIVE, AWAKENING, LEVITATING, ABSORBING, CONVERGING, CROWN_MANIFESTATION, FULLY_CHARGED,
        RELEASING, REALITY_SHATTERING, SOVEREIGN, COLLAPSING;

        public boolean charging() { return ordinal() >= AWAKENING.ordinal() && ordinal() <= FULLY_CHARGED.ordinal(); }
        public boolean reigning() { return ordinal() >= RELEASING.ordinal() && ordinal() <= SOVEREIGN.ordinal(); }
    }

    private static final Map<ServerPlayer, ScarletSovereignty> ACTIVE = new HashMap<>();
    private static final String FLIGHT = "minecraftmod.sovereigntyFlight";
    private static final String RECOVERY = "minecraftmod.sovereigntyReady";
    private static final net.minecraft.resources.ResourceLocation SPEED = MinecraftMod.id("sovereignty_speed");
    private final ServerLevel level;
    private final long chargeStart;
    private final double initialHeight;
    private final int chargeDuration;
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
        chargeDuration = HexConfig.SOVEREIGN_CHARGE.get();
        initialHeight = player.getY();
    }

    static void input(ServerPlayer player, int action) {
        var current = ACTIVE.get(player);
        long now = player.level().getGameTime();
        if (!available(player)) return;
        switch (action) {
            case PRESS -> {
                if (current != null) {
                    if (current.phase.reigning()) current.end(player, now);
                    return;
                }
                if (now < player.getPersistentData().getLong(RECOVERY)) {
                    tell(player, "Scarlet Sovereignty is recovering.");
                    return;
                }
                current = new ScarletSovereignty(player, now);
                ACTIVE.put(player, current);
                current.sync(player, now);
                cue(player, MinecraftMod.HEX_AWAKEN.get(), 1, .68F);
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
        if (progress < 1F / 7) return Phase.AWAKENING;
        if (progress < 2.5F / 7) return Phase.LEVITATING;
        if (progress < 4F / 7) return Phase.ABSORBING;
        if (progress < 5.5F / 7) return Phase.CONVERGING;
        if (progress < 6.5F / 7) return Phase.CROWN_MANIFESTATION;
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
                double rise = Math.clamp((charge(now) - 1F / 7) * 5, 0, 1);
                double velocity = Math.clamp((initialHeight + rise * 3.4 - player.getY()) * .19, -.16, .22);
                // A short zero-rise effect distinguishes magical hovering from flight cheats on dedicated servers.
                if (!player.hasEffect(MobEffects.LEVITATION)) {
                    player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 4, -1, true, false, false));
                }
                player.setDeltaMovement(player.getDeltaMovement().multiply(.65, 0, .65).add(0, velocity + .04, 0));
                player.hurtMarked = true;
                player.resetFallDistance();
            }
            if (before != phase) {
                var sound = phase == Phase.CROWN_MANIFESTATION || phase == Phase.FULLY_CHARGED
                        ? MinecraftMod.SOVEREIGN_CROWN.get() : MinecraftMod.SOVEREIGN_CURRENT.get();
                cue(player, sound, .68F, .72F + .072F * phase.ordinal());
            }
            if (phase == Phase.FULLY_CHARGED && now % 45 == 0) cue(player, MinecraftMod.SOVEREIGN_CURRENT.get(), .36F, .83F);
        } else if (phase.reigning()) {
            long age = now - released;
            if (age >= HexConfig.SOVEREIGN_LIFE.get() * 20L) {
                end(player, now);
                return;
            }
            if (age >= 48) phase = Phase.SOVEREIGN;
            else if (age >= 10) phase = Phase.REALITY_SHATTERING;
            if (age % 5 == 0) {
                float front = radius * Math.clamp((age + 1) / 42F, .10F, 1);
                catchMobs(player, center(player), front, now);
            }
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
        if (phase == Phase.COLLAPSING && now - collapse >= HexConfig.SOVEREIGN_END.get() / 2) removeConstructs();
        if (before != phase || now % 4 == 0) sync(player, now);
    }

    private void release(ServerPlayer player, long now) {
        phase = Phase.RELEASING;
        released = now;
        radius = HexConfig.SOVEREIGN_MIN.get() + charge(now) *
                Math.max(0, HexConfig.SOVEREIGN_MAX.get() - HexConfig.SOVEREIGN_MIN.get());
        releaseOrigin = player.position();
        grantFlight(player);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED, .28, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        HexDomains.scene(player, RELEASE_WAVE, 0, releaseOrigin, releaseOrigin, now, 58, radius);
        cue(player, MinecraftMod.SOVEREIGN_RELEASE.get(), 2.4F, .94F);
        cue(player, MinecraftMod.SHOCKWAVE.get(), 1.8F, 1.34F);
        sync(player, now);
    }

    private Vec3 center(ServerPlayer player) {
        return phase == Phase.SOVEREIGN ? player.position() : releaseOrigin;
    }

    private static boolean eligible(ServerPlayer player, Entity entity) {
        return entity instanceof Enemy && entity instanceof Mob && entity.isAlive()
                && !entity.isAlliedTo(player) && !(entity instanceof TamableAnimal);
    }

    private void catchMobs(ServerPlayer player, Vec3 at, double range, long now) {
        for (var mob : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(range),
                mob -> eligible(player, mob) && mob.distanceToSqr(at) <= range * range)) {
            if (affected.size() >= 512 || suspended.size() >= 48) break;
            capture(player, mob, now);
        }
    }

    private void capture(ServerPlayer player, Mob mob, long now) {
        if (suspended.size() >= 48 || !affected.add(mob.getUUID())) return;
        float damage = HexConfig.SOVEREIGN_DAMAGE.get().floatValue();
        if (HexConfig.SOVEREIGN_DISSOLVE.get() && player.isCreative() && player.hasPermissions(2)) {
            mob.discard();
        } else if (damage > 0) {
            mob.hurt(player.damageSources().indirectMagic(player, player), damage);
        }
        var raised = mob.position().add(0, 2.1, 0);
        if (!level.noCollision(mob, mob.getBoundingBox().move(0, 2.1, 0))) raised = mob.position();
        if (mob.isAlive() && HexConfig.SOVEREIGN_SUSPEND.get()) suspended.put(mob, raised);
        HexDomains.scene(player, CAPTURE, 0, mob.position(), raised, now, 24, 2.4F);
    }

    private void holdMobs(ServerPlayer player, long now) {
        suspended.entrySet().removeIf(entry -> {
            var mob = entry.getKey();
            if (!eligible(player, mob) || mob.level() != level || mob.distanceToSqr(player) > radius * radius * 1.44) return true;
            mob.setTarget(null);
            mob.getNavigation().stop();
            Vec3 velocity = entry.getValue().add(0, Math.sin(now * .11 + mob.getId()) * .21, 0)
                    .subtract(mob.position()).scale(.22).add(0, .052, 0);
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
        if (mob != null && mob.getEntity() instanceof Mob hostile) {
            capture(player, hostile, now);
            return;
        }
        var pos = ground ? hit.getBlockPos() : BlockPos.containing(target);
        if (ground && repair == null && HexTerrain.canRestore(player)) {
            var work = HexTerrain.get(level).reconstruction(player.getUUID(), pos, HexConfig.GENESIS_RADIUS.get());
            if (!work.isEmpty()) {
                repair = work;
                tell(player, "Reality Command is restoring " + work.size() + " journaled blocks.");
                return;
            }
        }
        if (ground && HexConfig.SOVEREIGN_CONSTRUCTS.get()) {
            int life = Math.clamp((int) (released + HexConfig.SOVEREIGN_LIFE.get() * 20L - now), 1, 600);
            if (constructs.size() < 420 && DarkholdFields.platform(player, pos.relative(hit.getDirection()), 3, life,
                    constructs::add) > 0) {
                HexDomains.scene(player, FORMATION, 1, target, target, now, 42, 6);
                return;
            }
            tell(player, "Aim at a clear surface where terrain magic is allowed.");
        }
        HexDomains.scene(player, FORMATION, 2, target, target, now, 105, ground ? 8 : 28);
    }

    private void tickRepair(ServerPlayer player, long now) {
        if (repair == null) return;
        var journal = HexTerrain.get(level);
        int budget = journal.claimReconstructionBudget(now);
        for (int i = 0; i < budget && !repair.isEmpty(); i++) {
            var pos = repair.removeFirst();
            if (journal.restore(player, pos) && now % 3 == 0 && i % Math.max(8, budget / 5) == 0) {
                var at = Vec3.atCenterOf(pos);
                HexDomains.scene(player, HexNetwork.RECONSTRUCTION_FX, 0, at, at, now, 32, 5);
            }
        }
        if (repair.isEmpty()) repair = null;
    }

    private void end(ServerPlayer player, long now) {
        phase = Phase.COLLAPSING;
        collapse = now;
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
        projectiles.clear();
        suspended.clear();
        repair = null;
    }

    private static void grantFlight(ServerPlayer player) {
        if (!HexConfig.SOVEREIGN_FLIGHT.get() || player.getAbilities().mayfly) return;
        player.getPersistentData().putBoolean(FLIGHT, true);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    private static void recoverFlight(ServerPlayer player) {
        if (!player.getPersistentData().getBoolean(FLIGHT)) return;
        player.getPersistentData().remove(FLIGHT);
        if (player.isCreative() || player.isSpectator()) return;
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
