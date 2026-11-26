package io.github.xshoutgamer.minecraftmod.hex;

import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class HexAbilities {
    private enum Rune { PROTECTION, GRAVITY, SILENCE, RESTORATION, TELEPORTATION }
    private record PlacedRune(Rune kind, Vec3 at, long created) {}
    private record Field(HexSpell spell, Vec3 at, long end, int variant, float radius) {}
    private static final String FLIGHT_MARKER = "minecraftmod.hexGrantedFlight";

    private final long[] readyAt = new long[HexSpell.values().length];
    private final List<Entity> held = new ArrayList<>();
    private final List<PlacedRune> runes = new ArrayList<>();
    private final List<Field> fields = new ArrayList<>();
    private HexSpell charging;
    private long chargeStart;
    private double liftDistance;
    private int runeMode, gravityMode, barrierMode, anchorSelection;
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> ORB_DAMAGE =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    MinecraftMod.id("chaos_orb"));
    private static final String ANCHORS = "minecraftmod.hexAnchors";
    private Vec3 barrierAnchor;
    private Vec3 orb, velocity;
    private long orbEnd;
    private float orbCharge;
    private Vec3 echoPosition;
    private boolean echoStationary;
    private long echoEnd;
    private Deque<BlockPos> terrainWork;
    private boolean restoring, reportWork;
    private Vec3 corruptionCenter;
    private HexConfig.Volume workShape;
    private BlockPos workCenter;
    private long workStart;
    private int scannedBlocks;
    private record Sapped(Mob mob, long expiry, float damage) {}
    private final java.util.List<Sapped> sapped = new java.util.ArrayList<>();
    private int corruptionRadius;
    private long corruptionStart;
    private final java.util.Set<java.util.UUID> corrupted = new java.util.HashSet<>();
    private int changedBlocks;
    boolean ascended;

    static Vec3 aim(ServerPlayer player, double range) {
        var hit = trace(player, range);
        return hit == null ? player.getEyePosition().add(player.getLookAngle().scale(range)) : hit.getLocation();
    }

    static BlockHitResult trace(ServerPlayer player, double range) {
        var start = player.getEyePosition();
        var end = start.add(player.getLookAngle().scale(range));
        var level = player.serverLevel();
        var context = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        var hit = BlockGetter.traverseBlocks(start, end, context, (clip, pos) -> {
            if (!level.hasChunkAt(pos)) return BlockHitResult.miss(Vec3.atCenterOf(pos), player.getDirection(), pos.immutable());
            var state = level.getBlockState(pos);
            return level.clipWithInteractionOverride(start, end, pos, clip.getBlockShape(state, level, pos), state);
        }, clip -> null);
        return hit;
    }

    void press(HexDomains.Domain domain, ServerPlayer player, HexSpell spell, long now) {
        if (charging != null || now < readyAt[spell.ordinal()]) return;
        if (player.position().subtract(domain.center).horizontalDistance() > domain.reach(now)) {
            player.displayClientMessage(Component.literal("Return to your Scarlet Hex to cast."), true);
            return;
        }
        if (HexDomains.silenced(player) && spell != HexSpell.RUNES && domain.gameplay()) {
            player.displayClientMessage(Component.literal("The Silence Rune is suppressing your spells."), true);
            return;
        }
        var eye = hand(player);
        var target = aim(player, HexConfig.TELEKINESIS_RANGE.get() * domain.scale());
        if (HexConfig.DEBUG.get()) player.displayClientMessage(Component.literal(
                spell.title + " → " + BlockPos.containing(target).toShortString()), true);
        HexDomains.sound(player, eye, MinecraftMod.HEX_CAST.get(), .75F, .74F + spell.ordinal() * .048F);
        if (spell.sustained) {
            if (spell == HexSpell.BARRIER && player.isShiftKeyDown()) {
                barrierMode = (barrierMode + 1) % 4;
                player.displayClientMessage(Component.literal("Barrier mode: " + switch (barrierMode) {
                    case 1 -> "forward shield";
                    case 2 -> "personal sphere";
                    case 3 -> "stationary shield";
                    default -> "protective dome";
                }), true);
            }
            barrierAnchor = target;
            charging = spell;
            chargeStart = now;
            if (spell == HexSpell.TELEKINESIS && domain.gameplay()) acquire(player, target);
            HexDomains.scene(player, HexNetwork.HAND_FX, spell.ordinal(), eye, target, now, 10, 1);
            return;
        }
        readyAt[spell.ordinal()] = now + cooldown(spell);
        switch (spell) {
            case ASCENSION -> {
                ascended = !ascended;
                if (ascended && domain.gameplay() && HexConfig.FLIGHT.get() && !player.getAbilities().mayfly) {
                    player.getPersistentData().putBoolean(FLIGHT_MARKER, true);
                    player.getAbilities().mayfly = true;
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                } else if (!ascended) resetFlight(player);
                scene(player, spell, ascended ? 1 : 0, eye, player.position(), now, 35, 3);
            }
            case FRACTURE -> {
                var anchors = anchors(player);
                if (player.isShiftKeyDown()) {
                    anchorSelection = (anchorSelection + 1) % (anchors.size() + 1);
                    feedback(player, anchorSelection == 0 ? "Rift destination: crosshair"
                            : "Rift destination: rune " + anchors.get(anchorSelection - 1).getString("Id"));
                    return;
                }
                if (anchorSelection > anchors.size()) anchorSelection = 0;
                var destination = anchorSelection == 0 ? target
                        : Vec3.atBottomCenterOf(BlockPos.of(anchors.get(anchorSelection - 1).getLong("Pos")));
                var safe = safeArrival(player, destination);
                if (domain.gameplay() && safe == null) {
                    feedback(player, "Reality Fracture could not find a safe destination.");
                    return;
                }
                if (domain.gameplay()) player.connection.teleport(safe.x, safe.y, safe.z, player.getYRot(), player.getXRot());
                scene(player, spell, 0, eye, safe == null ? destination : safe, now, 68, 4);
            }
            case RUNES -> {
                if (player.isShiftKeyDown()) {
                    runeMode = (runeMode + 1) % Rune.values().length;
                    player.displayClientMessage(Component.literal("Selected " + Rune.values()[runeMode].name().toLowerCase(java.util.Locale.ROOT) + " rune"), true);
                } else {
                    if (runes.size() == 5) runes.removeFirst();
                    long id = now;
                    if (Rune.values()[runeMode] == Rune.RESTORATION && domain.gameplay() && !HexTerrain.canRestore(player)) {
                        feedback(player, "Restoration Runes require the world owner or a server operator.");
                        return;
                    }
                    if (Rune.values()[runeMode] == Rune.RESTORATION && domain.gameplay()
                            && HexTerrain.get(player.serverLevel()).restoration(player.getUUID(), BlockPos.containing(target),
                                    HexConfig.RUNE_RADIUS.get()).isEmpty()) {
                        feedback(player, "This area has no recorded changes to restore.");
                        return;
                    }
                    if (Rune.values()[runeMode] == Rune.TELEPORTATION && domain.gameplay()) {
                        var safe = safeArrival(player, target);
                        if (safe == null) {
                            feedback(player, "Teleportation Rune needs a safe landing spot.");
                            return;
                        }
                        saveAnchor(player, BlockPos.containing(safe));
                    }
                    runes.add(new PlacedRune(Rune.values()[runeMode], target.add(0, .18, 0), id));
                    scene(player, HexNetwork.RUNE_FX, runeMode, eye, target.add(0, .18, 0), id, 24, HexConfig.RUNE_RADIUS.get());
                }
            }
            case TEMPORAL, GRAVITY -> {
                if (spell == HexSpell.GRAVITY && player.isShiftKeyDown()) gravityMode = (gravityMode + 1) % 4;
                int ticks = spell == HexSpell.TEMPORAL ? HexConfig.TEMPORAL_TICKS.get() : 110;
                float radius = (float) ((spell == HexSpell.TEMPORAL ? HexConfig.TEMPORAL_RADIUS.get() : HexConfig.GRAVITY_RADIUS.get()) * domain.scale());
                fields.removeIf(field -> field.spell == spell);
                fields.add(new Field(spell, target, now + ticks, gravityMode, radius));
                scene(player, spell, gravityMode, eye, target, now, ticks, radius);
            }
            case ECHO -> {
                echoPosition = player.position();
                echoStationary = player.isShiftKeyDown();
                echoEnd = now + HexConfig.ECHO_LIFE.get();
            }
            case GENESIS -> {
                if (domain.gameplay()) {
                    if (!HexTerrain.canRestore(player)) {
                        feedback(player, "Genesis requires the world owner or a server operator.");
                        return;
                    }
                    terrainWork = HexTerrain.get(player.serverLevel()).restoration(player.getUUID(), BlockPos.containing(target), HexConfig.GENESIS_RADIUS.get() * domain.scale());
                    if (terrainWork.isEmpty()) {
                        terrainWork = null;
                        feedback(player, "Genesis found no recorded changes near your target.");
                        return;
                    }
                    restoring = true;
                    reportWork = true;
                    changedBlocks = 0;
                    if (HexConfig.DEBUG.get()) feedback(player, "Genesis queued " + terrainWork.size() + " blocks.");
                }
                scene(player, spell, 0, eye, target, now, 100, (float) (22 * domain.scale()));
            }
            case SCARLET_WORLD -> {
                if (!domain.expand(now)) {
                    feedback(player, "The Scarlet Hex has reached its maximum radius.");
                    return;
                }
                domain.sync(player);
                HexDomains.sound(player, domain.center, MinecraftMod.HEX_EXPAND.get(), 1.35F, .57F);
            }
            default -> throw new IllegalStateException("Unexpected instant spell " + spell);
        }
    }

    void release(HexDomains.Domain domain, ServerPlayer player, long now) {
        if (charging == null) return;
        var spell = charging;
        charging = null;
        readyAt[spell.ordinal()] = now + cooldown(spell);
        float strength = Math.clamp((now - chargeStart) / (float) HexConfig.ORB_CHARGE.get(), .12F, 1);
        var eye = hand(player);
        var target = aim(player, HexConfig.TELEKINESIS_RANGE.get() * domain.scale());
        switch (spell) {
            case TELEKINESIS -> {
                for (var entity : held) {
                    if (entity.isAlive() && entity.level() == player.level()) {
                        entity.setDeltaMovement(new Vec3(0, -.08, 0));
                        if (entity instanceof LivingEntity mob) {
                            mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 45, 0, true, false));
                        }
                    }
                }
                held.clear();
            }
            case REWRITE -> {
                int radius = rewriteRadius(now - chargeStart);
                if (domain.gameplay() && HexTerrain.canRewrite(player)) {
                    var center = BlockPos.containing(target.add(0, -.03, 0));
                    restoring = HexConfig.REWRITE.get() == HexConfig.Rewrite.NATURAL;
                    workShape = HexConfig.VOLUME_SHAPE.get();
                    workCenter = center;
                    workStart = now;
                    scannedBlocks = 0;
                    terrainWork = restoring ? HexTerrain.get(player.serverLevel()).restoration(player.getUUID(), center, radius)
                            : HexTerrain.volume(center, radius, workShape);
                    if (terrainWork.isEmpty()) {
                        terrainWork = null;
                        feedback(player, "Natural Restoration found no recorded changes in this patch.");
                        return;
                    }
                    reportWork = true;
                    changedBlocks = 0;
                } else if (domain.gameplay()) {
                    feedback(player, HexTerrain.rewriteRestriction(player));
                }
                scene(player, spell, 0, eye, target, now, Math.max(68, radius * 5), radius);
                if (domain.gameplay()) {
                    corruptionCenter = target;
                    corruptionRadius = radius;
                    corruptionStart = now;
                    corrupted.clear();
                }
            }
            case ORB -> {
                orb = eye;
                orbEnd = now + 32;
                velocity = player.getLookAngle().scale(1.4 + strength * 1.1);
                orbCharge = strength;
            }
            case BARRIER -> scene(player, HexNetwork.BARRIER_FX, 1, eye, player.position(), now, 14, 3.5F);
            default -> throw new IllegalStateException("Unexpected sustained spell " + spell);
        }
        HexDomains.sound(player, eye, MinecraftMod.HEX_CAST.get(), .58F, 1.1F);
    }

    void tick(HexDomains.Domain domain, ServerPlayer player, long now) {
        var level = player.serverLevel();
        var eye = hand(player);
        if (charging != null && player.position().subtract(domain.center).horizontalDistance() > domain.reach(now) + 3) {
            if (charging == HexSpell.TELEKINESIS) release(domain, player, now);
            charging = null;
        }
        if (charging != null && now - chargeStart >= 220) release(domain, player, now);
        if (charging != null) {
            if (charging == HexSpell.TELEKINESIS) {
                var anchor = aim(player, liftDistance > 0 ? liftDistance : 12);
                int index = 0;
                for (var entity : held) {
                    if (entity.level() != level || !eligible(player, entity)) continue;
                    var goal = anchor.add((index % 3 - 1) * 1.2, 1 + index / 3, 0);
                    var motion = goal.subtract(entity.position()).scale(.29);
                    steer(entity, motion.lengthSqr() > 4 ? motion.normalize().scale(2) : motion);
                    if (now % 3 == 0) scene(player, HexSpell.TELEKINESIS, index, eye, entity.getBoundingBox().getCenter(), now, 7, 1.35F);
                    index++;
                }
                if (index == 0 && now % 3 == 0) scene(player, HexSpell.TELEKINESIS, 0, eye, anchor, now, 7, .8F);
            } else if (charging == HexSpell.BARRIER) {
                var center = switch (barrierMode) {
                    case 1 -> eye.add(player.getLookAngle().scale(2.6));
                    case 3 -> barrierAnchor.add(0, 1, 0);
                    default -> player.position().add(0, 1, 0);
                };
                float radius = (float) ((barrierMode == 2 ? 2 : barrierMode == 1 ? 2.75 : 3.8) * domain.scale());
                if (domain.gameplay()) barrier(player, center, radius, now);
                if (now % 3 == 0) scene(player, HexNetwork.BARRIER_FX, barrierMode + 3, eye, center, now, 7, radius);
            } else if (now % 3 == 0) {
                int chargeTime = charging == HexSpell.REWRITE ? HexConfig.REWRITE_CHARGE.get() : HexConfig.ORB_CHARGE.get();
                float growth = Math.clamp((now - chargeStart) / (float) chargeTime, .15F, 1F);
                HexDomains.scene(player, HexNetwork.HAND_FX, charging.ordinal(), eye, eye.add(player.getLookAngle()), now, 7, growth);
            }
        }
        if (orb != null) tickOrb(domain, player, now);
        if (ascended && now % 4 == 0) {
            scene(player, HexSpell.ASCENSION, 1, eye, player.position(), now, 9, 3);
            if (domain.gameplay()) player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 16, 0, true, false));
        }
        fields.removeIf(field -> now >= field.end);
        for (var field : fields) {
            if (domain.gameplay()) applyField(player, field.at, field.radius, field.spell == HexSpell.TEMPORAL ? -1 : field.variant);
        }
        tickEcho(domain, player, now);
        runes.removeIf(rune -> now - rune.created >= HexConfig.RUNE_LIFE.get() * 20L);
        Vec3 previousRune = null;
        for (var rune : runes) {
            var radius = HexConfig.RUNE_RADIUS.get();
            if (now % 12 == 0) scene(player, HexNetwork.RUNE_FX, rune.kind.ordinal(),
                    previousRune == null ? rune.at : previousRune, rune.at, rune.created, (int) (now - rune.created) + 24, radius);
            previousRune = rune.at;
            if (!domain.gameplay() || player.distanceToSqr(rune.at) >= 70 * 70) continue;
            if (rune.kind == Rune.GRAVITY) applyField(player, rune.at, radius, 0);
            if (rune.kind == Rune.PROTECTION) barrier(player, rune.at, radius, now);
            if (rune.kind == Rune.RESTORATION && now % 20 == 0 && terrainWork == null) {
                terrainWork = HexTerrain.get(level).restoration(player.getUUID(), BlockPos.containing(rune.at), radius);
                restoring = true;
                reportWork = false;
                changedBlocks = 0;
            }
        }
        if (domain.gameplay() && now % 20 == 0) {
            sapped.removeIf(mark -> !mark.mob.isAlive() || mark.mob.level() != level || now >= mark.expiry);
            for (var mark : sapped) {
                if (eligible(player, mark.mob)) mark.mob.hurt(player.damageSources().indirectMagic(player, player), mark.damage);
            }
        }
        if (corruptionCenter != null && domain.gameplay()) {
            float front = Math.min(corruptionRadius, (now - corruptionStart) / 5F + 1);
            applyCorruption(player, corruptionCenter, front, now);
            if (front >= corruptionRadius) corruptionCenter = null;
        }
        if (terrainWork != null && domain.gameplay()) {
            var journal = HexTerrain.get(level);
            int budget = HexConfig.EDITS.get();
            int edits = 0;
            int scanLimit = restoring ? budget : budget * 12;
            Vec3 changedAt = null;
            for (int i = 0; i < scanLimit && edits < budget && !terrainWork.isEmpty(); i++) {
                var pos = terrainWork.getFirst();
                if (!restoring && HexTerrain.distance(workCenter, pos, workShape) > (now - workStart) / 5F + 1) break;
                terrainWork.removeFirst();
                scannedBlocks++;
                int change = restoring ? (journal.restore(player, pos) ? 1 : 0)
                        : workShape == HexConfig.Volume.TERRAIN
                        ? journal.rewrite(player, pos) : journal.rewriteBlock(player, pos);
                if (change > 0) {
                    edits += change;
                    changedAt = Vec3.atCenterOf(pos);
                }
            }
            changedBlocks += edits;
            if (!restoring && changedAt != null && now % 4 == 0) {
                scene(player, PersonalMagic.DISSOLVE_FX, 3, changedAt.add(0,-.8,0), changedAt, now, 14, 1.4F);
            }
            if (terrainWork.isEmpty() || !restoring && journal.full()) {
                if (reportWork) feedback(player, (restoring ? "Genesis restored " : "Reality Rewrite changed ") + changedBlocks + " blocks."
                        + (HexConfig.DEBUG.get() && !restoring ? " Scanned " + scannedBlocks + (journal.full() ? " · journal full." : ".") : ""));
                terrainWork = null;
                reportWork = false;
                changedBlocks = 0;
            }
        }
    }

    private void acquire(ServerPlayer player, Vec3 target) {
        var level = player.serverLevel();
        var eye = player.getEyePosition();
        var hit = ProjectileUtil.getEntityHitResult(level, player, eye, target,
                new AABB(eye, target).inflate(1), entity -> eligible(player, entity) && !(entity instanceof Projectile));
        if (hit == null) return;
        var selected = hit.getEntity();
        held.add(selected);
        liftDistance = Math.clamp(player.distanceTo(selected), 4, HexConfig.TELEKINESIS_RANGE.get());
        for (var extra : level.getEntities(selected, selected.getBoundingBox().inflate(2.8),
                entity -> eligible(player, entity) && !(entity instanceof Projectile))) {
            if (held.size() >= HexConfig.TELEKINESIS_LIMIT.get()) break;
            held.add(extra);
        }
    }

    private void tickOrb(HexDomains.Domain domain, ServerPlayer player, long now) {
        var level = player.serverLevel();
        var next = orb.add(velocity);
        if (now >= orbEnd || !level.hasChunkAt(BlockPos.containing(orb)) || !level.hasChunkAt(BlockPos.containing(next))) {
            orb = null;
            return;
        }
        var block = level.clip(new ClipContext(orb, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        var end = block.getType() == HitResult.Type.MISS ? next : block.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(level, player, orb, end, new AABB(orb, end).inflate(.8),
                entity -> entity instanceof LivingEntity living && orbEligible(player, living));
        if (hit != null) end = hit.getLocation();
        boolean struck = hit != null || block.getType() != HitResult.Type.MISS;
        float blast = domain.phase == HexNetwork.ENHANCED ? HexConfig.ORB_ENHANCED_RADIUS.get()
                : net.minecraft.util.Mth.lerp(orbCharge, HexConfig.ORB_RADIUS.get(),
                        Math.max(HexConfig.ORB_RADIUS.get(), HexConfig.ORB_MAX_RADIUS.get()));
        if (struck && domain.gameplay() && !HexConfig.DEMO_ORBS.get()) detonate(player, end, blast, orbCharge, domain.scale());
        scene(player, HexNetwork.ORB_FX, struck ? 1 : 0, orb, end, now, struck ? 45 : 5, struck ? blast * 1.16F : 1.25F);
        orb = struck ? null : next;
    }

    static int detonate(ServerPlayer player, Vec3 at, double radius, float charge, double scale) {
        return detonate(player, at, radius, charge, scale, HexConfig.ORB_TERRAIN.get());
    }

    static int detonate(ServerPlayer player, Vec3 at, double radius, float charge, double scale, boolean terrain) {
        var level = player.serverLevel();
        var source = level.damageSources().source(ORB_DAMAGE, player, player);
        int affected = 0;
        for (var victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                living -> orbEligible(player, living))) {
            var away = victim.getBoundingBox().getCenter().subtract(at);
            double strength = 1 - away.length() / radius;
            if (strength <= 0 || victim.isInvulnerableTo(source)) continue;
            if (HexConfig.ORB_DAMAGE.get() > 0) {
                victim.hurt(source, (float) (HexConfig.ORB_DAMAGE.get() * (.45 + charge * .55) * scale * strength));
            }
            if (HexConfig.ORB_PUSH.get() > 0) {
                victim.knockback(HexConfig.ORB_PUSH.get() * strength, -away.x, -away.z);
                if (victim instanceof ServerPlayer) victim.hurtMarked = true;
            }
            affected++;
        }
        if (terrain && player.hasPermissions(2)) HexTerrain.get(level).orbScorch(player, at, radius);
        if (HexConfig.DEBUG.get()) feedback(player, "Chaos Orb contacted " + affected + " eligible entities.");
        HexDomains.sound(player, at, MinecraftMod.HEX_EXPAND.get(), 1.5F, .83F);
        return affected;
    }

    private static boolean orbEligible(ServerPlayer player, LivingEntity living) {
        if (!living.isAlive() || living.isSpectator() || !player.serverLevel().mayInteract(player, living.blockPosition())) return false;
        if (living == player) return HexConfig.ORB_SELF.get() && !player.isCreative();
        if (living instanceof ServerPlayer other) {
            return HexConfig.ORB_PLAYERS.get() && !other.isCreative() && player.canHarmPlayer(other);
        }
        return !player.isAlliedTo(living) && !(living instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isOwnedBy(player));
    }

    private void tickEcho(HexDomains.Domain domain, ServerPlayer player, long now) {
        if (echoPosition == null) return;
        if (now >= echoEnd) {
            echoPosition = null;
            return;
        }
        var level = player.serverLevel();
        var desired = player.position().add(player.getLookAngle().cross(new Vec3(0, 1, 0)).scale(2.5));
        if (!echoStationary) echoPosition = echoPosition.distanceToSqr(desired) > 32 * 32 ? desired : echoPosition.lerp(desired, .19);
        if (!level.hasChunkAt(BlockPos.containing(echoPosition))) return;
        if (now % 3 == 0) scene(player, HexSpell.ECHO, echoStationary ? 1 : 0, hand(player), echoPosition, now, 8, 3);
        if (domain.gameplay() && HexConfig.ECHO_GUARD.get() && now % 3 == 0) {
            barrier(player, echoPosition.add(0, 1, 0), 2.2F, now);
        }
        if (domain.gameplay() && HexConfig.ECHO_ATTACK.get() && now % 15 == 0) {
            double range = HexConfig.ECHO_RANGE.get();
            var eye = echoPosition.add(0, 1.36, 0);
            var hostile = level.getEntitiesOfClass(Mob.class, new AABB(eye, eye).inflate(range),
                    mob -> mob instanceof Enemy && eligible(player, mob) && mob.distanceToSqr(eye) < range * range
                            && level.clip(new ClipContext(eye, mob.getEyePosition(), ClipContext.Block.COLLIDER,
                                    ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS)
                    .stream().min(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(eye))).orElse(null);
            if (hostile != null) {
                hostile.hurt(level.damageSources().indirectMagic(player, player), (float) (4 * domain.scale()));
                if (now % 45 == 15) {
                    hostile.knockback(.64, eye.x - hostile.getX(), eye.z - hostile.getZ());
                } else {
                    hostile.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 24, 1, true, false));
                }
                scene(player, HexNetwork.ORB_FX, 0, eye, hostile.getEyePosition(), now, 5, .54F);
            }
        }
    }

    boolean runeApplies(ServerPlayer owner, ServerPlayer target, boolean silence) {
        long now = owner.level().getGameTime();
        if (owner.level() != target.level()) return false;
        if (!silence && owner != target && !owner.isAlliedTo(target)) return false;
        if (silence && (owner == target || owner.isAlliedTo(target))) return false;
        return runes.stream().anyMatch(rune -> rune.kind == (silence ? Rune.SILENCE : Rune.PROTECTION)
                && now - rune.created < HexConfig.RUNE_LIFE.get() * 20L
                && target.distanceToSqr(rune.at) < Math.pow(HexConfig.RUNE_RADIUS.get(), 2));
    }

    private static List<net.minecraft.nbt.CompoundTag> anchors(ServerPlayer player) {
        var dimension = player.level().dimension().location().toString();
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getList(ANCHORS, net.minecraft.nbt.Tag.TAG_COMPOUND)
                .stream().map(tag -> (net.minecraft.nbt.CompoundTag) tag)
                .filter(tag -> tag.getString("Dimension").equals(dimension)).toList();
    }

    private void saveAnchor(ServerPlayer player, BlockPos pos) {
        var data = player.getPersistentData();
        var persistent = data.getCompound(Player.PERSISTED_NBT_TAG);
        var anchors = persistent.getList(ANCHORS, net.minecraft.nbt.Tag.TAG_COMPOUND);
        var dimension = player.level().dimension().location().toString();
        for (var value : anchors) {
            var anchor = (net.minecraft.nbt.CompoundTag) value;
            if (anchor.getLong("Pos") == pos.asLong() && anchor.getString("Dimension").equals(dimension)) {
                feedback(player, "Existing teleportation rune " + anchor.getString("Id") + ".");
                return;
            }
        }
        var anchor = new net.minecraft.nbt.CompoundTag();
        var id = java.util.UUID.randomUUID().toString().substring(0, 8);
        anchor.putString("Id", id);
        anchor.putLong("Pos", pos.asLong());
        anchor.putString("Dimension", dimension);
        if (anchors.size() >= 12) anchors.removeFirst();
        anchors.add(anchor);
        persistent.put(ANCHORS, anchors);
        data.put(Player.PERSISTED_NBT_TAG, persistent);
        anchorSelection = anchors(player).size();
        feedback(player, "Saved teleportation rune " + id + ".");
    }

    private static int rewriteRadius(long ticks) {
        double t = Math.clamp(ticks / (double) HexConfig.REWRITE_CHARGE.get(), 0, 1);
        return (int) Math.round(6 + (HexConfig.rewriteRadius() - 6) * Math.pow(t, 1.22));
    }

    private void applyCorruption(ServerPlayer player, Vec3 at, float radius, long now) {
        for (var mob : player.level().getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(radius),
                entity -> eligible(player, entity) && !player.isAlliedTo(entity)
                        && !(entity instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isOwnedBy(player))
                        && (!HexConfig.CORRUPTION_HOSTILE.get() || entity instanceof Enemy)
                        && HexTerrain.distance(BlockPos.containing(at), entity.blockPosition(), HexConfig.VOLUME_SHAPE.get()) < radius)) {
            if (!corrupted.add(mob.getUUID())) continue;
            float damage = HexConfig.CORRUPTION_DAMAGE.get().floatValue();
            if (HexConfig.CORRUPTION_FALLOFF.get()) damage *= Math.max(0, 1 - playerDistance(at, mob) / corruptionRadius);
            if (damage > 0) mob.hurt(player.level().damageSources().indirectMagic(player, player), damage);
            int duration = HexConfig.CORRUPTION_TICKS.get();
            if (duration > 0) {
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 1));
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0));
                float periodic = HexConfig.CORRUPTION_PERIODIC.get().floatValue();
                if (periodic > 0 && sapped.size() < 128 && sapped.stream().noneMatch(mark -> mark.mob == mob)) {
                    sapped.add(new Sapped(mob, now + duration, periodic));
                }
            }
            var glow = mob.getBoundingBox().getCenter();
            scene(player, PersonalMagic.DISSOLVE_FX, 4, glow, at.add(0,1,0), now, 22, 1);
        }
    }

    private static float playerDistance(Vec3 point, Entity entity) {
        return (float) entity.position().distanceTo(point);
    }

    private static void feedback(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    static Vec3 safeArrival(ServerPlayer player, Vec3 target) {
        var level = player.serverLevel();
        var base = BlockPos.containing(target.add(0, .05, 0));
        for (int dy = 0; dy <= 4; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    var pos = base.offset(dx, dy, dz);
                    if (level.isOutsideBuildHeight(pos.above()) || !level.hasChunkAt(pos)
                            || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)
                            || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                            || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()
                            || level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                            || level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE)
                            || level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) continue;
                    var feet = Vec3.atBottomCenterOf(pos);
                    if (level.noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())))) return feet;
                }
            }
        }
        return null;
    }

    static void barrier(ServerPlayer player, Vec3 center, float radius, long now) {
        for (var projectile : player.level().getEntitiesOfClass(Projectile.class, new AABB(center, center).inflate(radius + 1),
                entity -> entity.getOwner() != player && eligible(player, entity))) {
            var normal = projectile.position().subtract(center);
            var motion = projectile.getDeltaMovement();
            if (normal.lengthSqr() <= radius * radius && motion.dot(normal) < -.03) {
                normal = normal.normalize();
                steer(projectile, motion.subtract(normal.scale(2 * motion.dot(normal))).scale(.72));
                scene(player, HexNetwork.BARRIER_FX, 2, center, projectile.position(), now, 14, 1.8F);
            }
        }
    }

    private static void applyField(ServerPlayer player, Vec3 center, float radius, int variant) {
        var candidates = player.level().getEntities(player, new AABB(center, center).inflate(radius),
                entity -> eligible(player, entity) && entity.distanceToSqr(center) < radius * radius);
        candidates.sort(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(center)));
        for (var entity : candidates.subList(0, Math.min(candidates.size(), HexConfig.FIELD_LIMIT.get()))) {
            if (variant < 0) {
                if (entity instanceof Mob mob && !(mob instanceof Enemy)) continue;
                HexDomains.slow(player, entity);
                continue;
            }
            var motion = entity.getDeltaMovement();
            double gravity = HexConfig.GRAVITY.get();
            var toward = center.subtract(entity.position());
            var orbit = new Vec3(-toward.z, 0, toward.x).normalize().scale(.31);
            double height = center.y + 3.5 + Math.sin(entity.getId() * 1.71) * 1.2;
            double lift = Math.clamp((height - entity.getY()) * .16 + gravity, -.24, .62);
            steer(entity, switch (variant) {
                case 0 -> new Vec3(motion.x * .56, lift, motion.z * .56);
                case 1 -> new Vec3(motion.x * .32, lift * .64, motion.z * .32);
                case 2 -> orbit.scale(1.35).add(0, lift + .12, 0);
                default -> orbit.add(toward.x * .031, lift, toward.z * .031);
            });
        }
    }

    private static boolean eligible(ServerPlayer player, Entity entity) {
        return entity.isAlive() && !entity.isRemoved() && !(entity instanceof Player)
                && (entity instanceof Mob || entity instanceof ItemEntity || entity instanceof FallingBlockEntity || entity instanceof Projectile)
                && player.serverLevel().mayInteract(player, entity.blockPosition());
    }

    private static void steer(Entity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity);
        entity.hasImpulse = true;
    }

    private static Vec3 hand(ServerPlayer player) {
        return player.getEyePosition().add(player.getLookAngle().scale(.65)).add(0, -.3, 0);
    }

    private static int cooldown(HexSpell spell) { return spell.cooldown * HexConfig.COOLDOWN_PERCENT.get() / 100; }

    private static void scene(ServerPlayer player, HexSpell spell, int variant, Vec3 origin, Vec3 target, long now, int life, float radius) {
        scene(player, 6 + spell.ordinal(), variant, origin, target, now, life, radius);
    }

    private static void scene(ServerPlayer player, int kind, int variant, Vec3 origin, Vec3 target, long now, int life, float radius) {
        HexDomains.scene(player, kind, variant, origin, target, now, life, radius);
    }

    void clear(ServerPlayer player) {
        held.clear();
        fields.clear();
        runes.clear();
        charging = null;
        orb = null;
        echoPosition = null;
        terrainWork = null;
        corruptionCenter = null;
        corrupted.clear();
        sapped.clear();
        ascended = false;
        resetFlight(player);
    }

    static void resetFlight(ServerPlayer player) {
        if (!player.getPersistentData().getBoolean(FLIGHT_MARKER)) return;
        player.getPersistentData().remove(FLIGHT_MARKER);
        if (player.isCreative() || player.isSpectator()) return;
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }
}
