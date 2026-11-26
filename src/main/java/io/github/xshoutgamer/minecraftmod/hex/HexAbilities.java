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
    private int runeMode, gravityMode, barrierMode;
    private Vec3 barrierAnchor;
    private Vec3 orb, velocity;
    private long orbEnd;
    private Deque<BlockPos> terrainWork;
    private boolean restoring;
    boolean ascended;

    static Vec3 aim(ServerPlayer player, double range) {
        var start = player.getEyePosition();
        var end = start.add(player.getLookAngle().scale(range));
        var level = player.serverLevel();
        var context = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        var hit = BlockGetter.traverseBlocks(start, end, context, (clip, pos) -> {
            if (!level.hasChunkAt(pos)) return BlockHitResult.miss(Vec3.atCenterOf(pos), player.getDirection(), pos.immutable());
            var state = level.getBlockState(pos);
            return level.clipWithInteractionOverride(start, end, pos, clip.getBlockShape(state, level, pos), state);
        }, clip -> null);
        return hit == null ? end : hit.getLocation();
    }

    void press(HexDomains.Domain domain, ServerPlayer player, HexSpell spell, long now) {
        if (charging != null || now < readyAt[spell.ordinal()]) return;
        if (player.position().subtract(domain.center).horizontalDistance() > domain.radius) {
            player.displayClientMessage(Component.literal("Return to your Scarlet Hex to cast."), true);
            return;
        }
        if (runes.stream().anyMatch(rune -> rune.kind == Rune.SILENCE && player.position().distanceTo(rune.at) < HexConfig.RUNE_RADIUS.get())
                && spell != HexSpell.RUNES && domain.gameplay()) {
            player.displayClientMessage(Component.literal("The Silence Rune is suppressing your spells."), true);
            return;
        }
        var eye = hand(player);
        var target = aim(player, HexConfig.TELEKINESIS_RANGE.get() * domain.scale());
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
                var anchor = runes.stream().filter(rune -> rune.kind == Rune.TELEPORTATION).findFirst();
                var destination = anchor.map(PlacedRune::at).orElse(target);
                var safe = safeArrival(player, destination);
                if (domain.gameplay() && safe != null) player.connection.teleport(safe.x, safe.y, safe.z, player.getYRot(), player.getXRot());
                scene(player, spell, 0, eye, safe == null ? destination : safe, now, 68, 4);
            }
            case RUNES -> {
                if (player.isShiftKeyDown()) {
                    runeMode = (runeMode + 1) % Rune.values().length;
                    player.displayClientMessage(Component.literal("Selected " + Rune.values()[runeMode].name().toLowerCase(java.util.Locale.ROOT) + " rune"), true);
                } else {
                    if (runes.size() == 5) runes.removeFirst();
                    long id = now;
                    runes.add(new PlacedRune(Rune.values()[runeMode], target.add(0, .18, 0), id));
                    scene(player, HexNetwork.RUNE_FX, runeMode, eye, target.add(0, .18, 0), id, 24, HexConfig.RUNE_RADIUS.get());
                }
            }
            case TEMPORAL, GRAVITY -> {
                if (spell == HexSpell.GRAVITY && player.isShiftKeyDown()) gravityMode = (gravityMode + 1) % 4;
                int ticks = spell == HexSpell.TEMPORAL ? HexConfig.TEMPORAL_TICKS.get() : 110;
                float radius = (float) ((spell == HexSpell.TEMPORAL ? 16 : 14) * domain.scale());
                fields.removeIf(field -> field.spell == spell);
                fields.add(new Field(spell, target, now + ticks, gravityMode, radius));
                scene(player, spell, gravityMode, eye, target, now, ticks, radius);
            }
            case ECHO -> {
                fields.removeIf(field -> field.spell == spell);
                fields.add(new Field(spell, player.position(), now + 220, player.isShiftKeyDown() ? 1 : 0, 3));
            }
            case GENESIS -> {
                if (domain.gameplay()) {
                    terrainWork = HexTerrain.get(player.serverLevel()).restoration(player.getUUID(), BlockPos.containing(target), 28 * domain.scale());
                    restoring = true;
                }
                scene(player, spell, 0, eye, target, now, 100, (float) (22 * domain.scale()));
            }
            case SCARLET_WORLD -> {
                domain.state(HexNetwork.ENHANCED, now, 360);
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
        float strength = Math.clamp((now - chargeStart) / 45F, .12F, 1);
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
                int radius = (int) ((4 + Math.round(strength * 9)) * domain.scale());
                if (domain.gameplay() && HexConfig.EDIT.get() && player.hasPermissions(2)) {
                    terrainWork = HexTerrain.patch(BlockPos.containing(target.add(0, -.03, 0)), radius);
                    restoring = false;
                }
                scene(player, spell, 0, eye, target, now, 68, radius);
            }
            case ORB -> {
                orb = eye;
                orbEnd = now + 32;
                velocity = player.getLookAngle().scale(1.4 + strength * 1.1);
                scene(player, spell, 2, eye, target, now, 10, 1 + strength);
            }
            case BARRIER -> scene(player, HexNetwork.BARRIER_FX, 1, eye, player.position(), now, 14, 3.5F);
            default -> throw new IllegalStateException("Unexpected sustained spell " + spell);
        }
        HexDomains.sound(player, eye, MinecraftMod.HEX_CAST.get(), .58F, 1.1F);
    }

    void tick(HexDomains.Domain domain, ServerPlayer player, long now) {
        var level = player.serverLevel();
        var eye = hand(player);
        if (charging != null && player.position().subtract(domain.center).horizontalDistance() > domain.radius + 3) {
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
                float growth = Math.clamp((now - chargeStart) / 45F, .15F, 1F);
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
            if (field.spell == HexSpell.ECHO) {
                if (now % 4 == 0) {
                    var at = field.variant == 1 ? field.at : player.position().add(player.getLookAngle().cross(new Vec3(0, 1, 0)).scale(2.5));
                    scene(player, field.spell, field.variant, eye, at, now, 9, field.radius);
                }
            } else if (domain.gameplay()) {
                applyField(player, field.at, field.radius, field.spell == HexSpell.TEMPORAL ? -1 : field.variant);
            }
        }
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
            }
        }
        if (terrainWork != null && domain.gameplay()) {
            var journal = HexTerrain.get(level);
            for (int i = 0; i < HexConfig.EDITS.get() && !terrainWork.isEmpty(); i++) {
                var pos = terrainWork.removeFirst();
                if (restoring) journal.restore(player, pos);
                else journal.rewrite(player, pos);
            }
            if (terrainWork.isEmpty()) terrainWork = null;
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
                entity -> entity instanceof LivingEntity && eligible(player, entity));
        if (hit != null) end = hit.getLocation();
        boolean struck = hit != null || block.getType() != HitResult.Type.MISS;
        if (struck && domain.gameplay() && !HexConfig.DEMO_ORBS.get() && hit != null) {
            hit.getEntity().hurt(level.damageSources().playerAttack(player), (float) ((4 + velocity.lengthSqr()) * domain.scale()));
        }
        scene(player, HexNetwork.ORB_FX, struck ? 1 : 0, orb, end, now, struck ? 16 : 5, struck ? 3 : 1.25F);
        orb = struck ? null : next;
    }

    private static Vec3 safeArrival(ServerPlayer player, Vec3 target) {
        var level = player.serverLevel();
        var base = BlockPos.containing(target.add(0, .05, 0));
        for (int dy = 0; dy <= 4; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    var pos = base.offset(dx, dy, dz);
                    if (level.isOutsideBuildHeight(pos.above()) || !level.hasChunkAt(pos)
                            || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)
                            || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                            || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) continue;
                    var feet = Vec3.atBottomCenterOf(pos);
                    if (level.noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())))) return feet;
                }
            }
        }
        return null;
    }

    private static void barrier(ServerPlayer player, Vec3 center, float radius, long now) {
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
        for (var entity : player.level().getEntities(player, new AABB(center, center).inflate(radius),
                entity -> eligible(player, entity) && entity.distanceToSqr(center) < radius * radius)) {
            var motion = entity.getDeltaMovement();
            double gravity = HexConfig.GRAVITY.get();
            if (variant < 0) {
                if (entity instanceof Mob mob && !(mob instanceof Enemy)) continue;
                steer(entity, motion.scale(.22));
                if (entity instanceof LivingEntity mob) mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 5, true, false));
                continue;
            }
            var toward = center.subtract(entity.position());
            var orbit = new Vec3(-toward.z, 0, toward.x).normalize().scale(.31);
            steer(entity, switch (variant) {
                case 0 -> motion.add(0, gravity * .6, 0);
                case 1 -> motion.scale(.36).add(0, .045, 0);
                case 2 -> motion.scale(.68).add(0, gravity, 0);
                default -> orbit.add(toward.scale(.026)).add(0, gravity, 0);
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
        terrainWork = null;
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
