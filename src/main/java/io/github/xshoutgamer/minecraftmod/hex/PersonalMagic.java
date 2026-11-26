package io.github.xshoutgamer.minecraftmod.hex;

import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class PersonalMagic {
    public static final int QUICK = 3, BOOK = 4, PERSONAL = 5, GRIMOIRE = 6, GRIMOIRE_RELEASE = 7;
    public static final int BOOK_FX = 30, DISSOLVE_FX = 31, POWER_FX = 32, MEMORY_FX = 33, GUARDIAN_FX = 34,
            ACTIVATE_FX = 35, BOOK_CLOSED = 36;
    public enum Power {
        FLIGHT("Chaos Flight"), SHIELD("Personal Chaos Shield"), STEP("Reality Step"),
        LIFT("Personal Telekinesis"), PROJECTION("Chaos Projection"),
        DREAM("Dreamwalker"), PORTAL("Abyssal Portal"), GUARDIANS("Forbidden Guardians"),
        ECLIPSE("Eternal Eclipse"), MIRROR("Mirror Reality"), VOID("Void Singularity"),
        LIBRARY("Astral Library"), PRISON("Dimensional Prison"), MEMORY("Memory of Reality"),
        SCRIPTURE("The Forbidden Scripture");

        public final String title;
        Power(String title) { this.title = title; }
    }

    private static final String FLIGHT = "minecraftmod.personalMagicFlight";
    private static final Map<MinecraftServer, Map<UUID, State>> STATES = new HashMap<>();
    private record Field(Vec3 at, long start, long end, float radius) {}
    private static final class State {
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final Map<Power, Field> fields = new java.util.EnumMap<>(Power.class);
        boolean book, flight;
        long next, bookStart;
        Vec3 dreamAnchor;
        long focusAt;
        Vec3 focus;
        BlockPos block;
        int dissolveArea;
        net.minecraft.world.level.block.state.BlockState originalBlock;
        Power charging;
        long chargeStart;
        Entity entity;

        State(ServerPlayer player) { dimension = player.level().dimension(); }
    }

    private PersonalMagic() {}

    static void input(ServerPlayer player, HexNetwork.Input packet) {
        if (!player.isAlive() || player.isSpectator() || !HexConfig.GAMEPLAY.get() || HexDomains.silenced(player)) return;
        var states = STATES.computeIfAbsent(player.getServer(), server -> new HashMap<>());
        var state = states.computeIfAbsent(player.getUUID(), key -> new State(player));
        long now = player.level().getGameTime();
        if (state.dimension != player.level().dimension()) return;
        if (packet.action() == GRIMOIRE_RELEASE && state.book && state.charging != null) {
            release(player, state, now);
            return;
        }
        if (now < state.next) return;
        state.next = now + 8;
        if (packet.action() == BOOK) {
            state.book = !state.book;
            if (!state.book) {
                endDream(player, state);
                state.charging = null;
                state.fields.keySet().removeIf(power -> power.ordinal() >= Power.DREAM.ordinal());
                scene(player, BOOK_CLOSED, 0, hand(player), player.position(), now, 14, 1);
            } else {
                state.bookStart = now;
                scene(player, BOOK_FX, 0, hand(player), player.position(), now, 18, 1);
            }
            chime(player, player.position(), state.book ? .67F : .48F);
            return;
        }
        if (packet.action() == QUICK) {
            if (state.focus == null) startDissolve(player, state, now);
            return;
        }
        int index = packet.spell();
        if (index < 0 || index >= Power.values().length) return;
        var power = Power.values()[index];
        boolean dark = index >= Power.DREAM.ordinal();
        if (dark != (packet.action() == GRIMOIRE) || dark && !state.book) return;
        if (dark) {
            if (state.charging != null) return;
            state.charging = power;
            state.chargeStart = now;
            scene(player, ACTIVATE_FX, power.ordinal(), hand(player), player.position().add(0, 1, 0), now, 12, 1);
            HexDomains.sound(player, player.position(), MinecraftMod.DARKHOLD_SOUND.get(), .62F, .74F);
        } else cast(player, state, power, now, 0);
    }

    private static void startDissolve(ServerPlayer player, State state, long now) {
        var eye = player.getEyePosition();
        var blockHit = HexAbilities.trace(player, HexConfig.DISINTEGRATION_RANGE.get());
        var end = blockHit == null ? eye.add(player.getLookAngle().scale(HexConfig.DISINTEGRATION_RANGE.get())) : blockHit.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, new AABB(eye, end).inflate(1),
                entity -> entity instanceof Mob && allowed(player, entity));
        state.entity = hit == null ? null : hit.getEntity();
        state.block = null;
        if (state.entity == null) {
            if (blockHit == null || blockHit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return;
            if (!HexTerrain.canRewrite(player)) {
                message(player, "Block disintegration requires terrain-edit permission.");
                return;
            }
            state.block = blockHit.getBlockPos();
            state.originalBlock = player.level().getBlockState(state.block);
        }
        state.focus = state.entity == null ? Vec3.atCenterOf(state.block) : state.entity.getBoundingBox().getCenter();
        state.dissolveArea = state.block != null && player.isShiftKeyDown() ? HexConfig.DISINTEGRATION_AREA.get() : 0;
        int channel = 18 + 7 * state.dissolveArea;
        state.focusAt = now + channel;
        state.next = now + channel + 7;
        scene(player, DISSOLVE_FX, 0, hand(player), state.focus, now, channel + 4, 1.25F + state.dissolveArea * .6F);
        chime(player, state.focus, .75F);
    }

    private static void release(ServerPlayer player, State state, long now) {
        var power = state.charging;
        state.charging = null;
        float charge = Math.clamp((now - state.chargeStart) / 40F, 0, 1);
        cast(player, state, power, now, charge);
    }

    private static void cast(ServerPlayer player, State state, Power power, long now, float charge) {
        boolean enhanced = state.fields.containsKey(Power.SCRIPTURE);
        double scale = (enhanced ? 1.5 : 1) * (1 + charge * .4F);
        Vec3 at = HexAbilities.aim(player, HexConfig.DISINTEGRATION_RANGE.get() * scale);
        float radius = (float) (9 * scale);
        switch (power) {
            case FLIGHT -> {
                state.flight = !state.flight;
                if (state.flight && !player.getAbilities().mayfly) {
                    player.getPersistentData().putBoolean(FLIGHT, true);
                    player.getAbilities().mayfly = true;
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                } else if (!state.flight) restoreFlight(player);
            }
            case STEP, PORTAL -> {
                var safe = HexAbilities.safeArrival(player, power == Power.STEP ? HexAbilities.aim(player, 12 * scale) : at);
                if (safe == null) {
                    message(player, "No safe destination in reach.");
                    return;
                }
                var from = player.position();
                player.connection.teleport(safe.x, safe.y, safe.z, player.getYRot(), player.getXRot());
                scene(player, POWER_FX, power.ordinal(), from, safe, now, 65, power == Power.PORTAL ? 6 : 3);
                chime(player, safe, .52F);
                return;
            }
            case PROJECTION -> HexAbilities.detonate(player, at, 5 * scale, .65F, .7, false);
            case DREAM -> {
                if (state.dreamAnchor != null) {
                    endDream(player, state);
                    return;
                }
                at = player.position();
                state.dreamAnchor = at;
            }
            case SHIELD, GUARDIANS, ECLIPSE, LIBRARY, SCRIPTURE -> {
                at = player.position();
                radius = switch (power) {
                    case ECLIPSE -> 90;
                    case LIBRARY, SCRIPTURE -> 24;
                    default -> radius;
                };
            }
            case MEMORY -> {
                var journal = HexTerrain.get(player.serverLevel());
                var records = journal.restoration(player.getUUID(), BlockPos.containing(at), 22 * scale);
                if (records.isEmpty()) {
                    message(player, "Memory of Reality found no saved blocks here.");
                    return;
                }
                int count = 0;
                for (var pos : records) {
                    if (count >= 48) break;
                    if (player.isShiftKeyDown()) {
                        if (journal.restore(player, pos)) count++;
                    } else {
                        scene(player, MEMORY_FX, 0, hand(player), Vec3.atCenterOf(pos), now, 110, .5F);
                        count++;
                    }
                }
                message(player, player.isShiftKeyDown() ? "Restored " + count + " recorded blocks." : "Showing " + count + " recorded positions. Crouch and cast to restore.");
            }
            default -> {}
        }
        if (power == Power.DREAM && !player.getAbilities().mayfly) {
            player.getPersistentData().putBoolean(FLIGHT, true);
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
        int duration = switch (power) {
            case ECLIPSE, GUARDIANS, SCRIPTURE -> 320;
            case DREAM, SHIELD, LIBRARY -> 220;
            case MIRROR, VOID, PRISON -> 160;
            case LIFT -> 90;
            default -> 36;
        };
        duration += Math.round(duration * charge * .2F);
        state.fields.put(power, new Field(at, now, now + duration, radius));
        if (!dynamic(power)) scene(player, POWER_FX, power.ordinal(), hand(player), at, now, duration, radius);
        state.next = now + 16;
        if (power.ordinal() >= Power.DREAM.ordinal()) {
            HexDomains.sound(player, at, MinecraftMod.DARKHOLD_SOUND.get(), 1.05F, .59F + charge * .28F);
        } else chime(player, at, .9F);
    }

    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var states = STATES.get(server);
        if (states == null) return;
        var entries = states.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            var state = entry.getValue();
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || player.isSpectator() || player.level().dimension() != state.dimension
                    || !HexConfig.GAMEPLAY.get()) {
                if (player != null) restoreFlight(player);
                entries.remove();
                continue;
            }
            long now = player.level().getGameTime();
            if (state.book && now % 4 == 0) scene(player, BOOK_FX, state.fields.containsKey(Power.SCRIPTURE) ? 1 : 0,
                    hand(player), player.position(), state.bookStart, (int) (now - state.bookStart + 12), 1);
            if (state.charging != null) {
                if (now - state.chargeStart >= 100) release(player, state, now);
                else if (now % 4 == 0) scene(player, ACTIVATE_FX, state.charging.ordinal(), hand(player),
                        player.position().add(0,1,0), now, 10, Math.clamp((now - state.chargeStart) / 24F, .8F, 2));
            }
            if (state.focus != null && now >= state.focusAt) {
                finishDissolve(player, state, now);
                state.entity = null;
                state.block = null;
                state.focus = null;
            }
            var dream = state.fields.get(Power.DREAM);
            if (dream != null && (now >= dream.end || player.distanceToSqr(state.dreamAnchor) > 64 * 64)) endDream(player, state);
            state.fields.values().removeIf(field -> now >= field.end);
            for (var field : state.fields.entrySet()) update(player, field.getKey(), field.getValue(), now);
            if (!state.book && !state.flight && state.focus == null && state.fields.isEmpty() && now >= state.next) entries.remove();
        }
    }

    private static void finishDissolve(ServerPlayer player, State state, long now) {
        var eye = player.getEyePosition();
        var end = state.entity == null ? state.focus : state.entity.getBoundingBox().getCenter();
        if (eye.distanceTo(end) > HexConfig.DISINTEGRATION_RANGE.get() + 1) return;
        if (!player.serverLevel().hasChunkAt(BlockPos.containing(end))) return;
        var hit = player.level().clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (state.entity != null && state.entity.level() == player.level() && allowed(player, state.entity)
                && hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            state.entity.hurt(player.level().damageSources().indirectMagic(player, player), HexConfig.DISINTEGRATION_DAMAGE.get().floatValue());
        } else if (state.block != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && hit.getBlockPos().equals(state.block) && player.level().getBlockState(state.block).equals(state.originalBlock)) {
            var journal = HexTerrain.get(player.serverLevel());
            if (!journal.disintegrate(player, state.block)) {
                message(player, "That block could not be disintegrated.");
                return;
            }
            if (state.dissolveArea > 0) {
                int remaining = Math.min(18, HexConfig.EDITS.get()) - 1;
                for (var pos : HexTerrain.volume(state.block, state.dissolveArea, HexConfig.VOLUME_SHAPE.get())) {
                    if (remaining <= 0) break;
                    if (!pos.equals(state.block) && journal.disintegrate(player, pos)) remaining--;
                }
            }
        } else return;
        scene(player, DISSOLVE_FX, 1, hand(player), end, now, 26, 2 + state.dissolveArea);
    }

    private static void endDream(ServerPlayer player, State state) {
        if (state.dreamAnchor == null) return;
        var anchor = state.dreamAnchor;
        state.dreamAnchor = null;
        state.fields.remove(Power.DREAM);
        if (player.level().dimension() == state.dimension && player.serverLevel().hasChunkAt(BlockPos.containing(anchor))
                && player.level().noCollision(player, player.getBoundingBox().move(anchor.subtract(player.position())))) {
            player.connection.teleport(anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot());
        }
        if (!state.flight) restoreFlight(player);
    }

    private static boolean dynamic(Power power) {
        return power == Power.SHIELD || power == Power.GUARDIANS || power == Power.LIFT;
    }

    private static void update(ServerPlayer player, Power power, Field field, long now) {
        var level = player.serverLevel();
        var center = field.at;
        var radius = field.radius;
        if (power == Power.SHIELD) {
            if (now % 4 == 0) scene(player, POWER_FX, power.ordinal(), hand(player), player.position().add(0,1,0), now, 10, 3);
            HexAbilities.barrier(player, player.position().add(0, 1, 0), 3, now);
        } else if (power == Power.GUARDIANS) {
            for (int i = 0; i < 3; i++) {
                double angle = now * .065 + i * Math.PI * 2 / 3;
                var guardian = player.position().add(Math.cos(angle) * 3, 1.8, Math.sin(angle) * 3);
                if (now % 4 == 0) scene(player, GUARDIAN_FX, i, hand(player), guardian, now, 10, 1.2F);
                if (now % 21 == i * 7) {
                    var target = level.getEntitiesOfClass(Mob.class, new AABB(guardian, guardian).inflate(12),
                            mob -> mob instanceof Enemy && allowed(player, mob) && player.hasLineOfSight(mob))
                            .stream().min(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(guardian))).orElse(null);
                    if (target != null) {
                        target.hurt(level.damageSources().indirectMagic(player, player), 5);
                        scene(player, DISSOLVE_FX, 2, guardian, target.getEyePosition(), now, 12, .8F);
                    }
                }
            }
        } else if (power == Power.LIFT || power == Power.VOID || power == Power.PRISON) {
            if (power == Power.LIFT) {
                center = HexAbilities.aim(player, 16);
                if (now % 4 == 0) scene(player, POWER_FX, power.ordinal(), hand(player), center, now, 10, 3);
            }
            var anchor = center;
            var entities = level.getEntities(player, new AABB(center, center).inflate(radius),
                    entity -> allowed(player, entity) && entity.distanceToSqr(anchor) < radius * radius
                            && (entity instanceof Mob || entity instanceof ItemEntity)
                            && (power != Power.PRISON || entity instanceof Enemy));
            for (int i = 0; i < Math.min(48, entities.size()); i++) {
                var entity = entities.get(i);
                var goal = center.add(0, power == Power.LIFT ? 3 : 1, 0);
                var move = goal.subtract(entity.position()).scale(power == Power.PRISON ? .19 : .12);
                if (move.lengthSqr() > .81) move = move.normalize().scale(.9);
                entity.setDeltaMovement(move);
                entity.hasImpulse = true;
            }
        }
    }

    private static boolean allowed(ServerPlayer player, Entity entity) {
        return entity.isAlive() && !(entity instanceof net.minecraft.world.entity.player.Player)
                && !player.isAlliedTo(entity)
                && !(entity instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isOwnedBy(player))
                && player.serverLevel().mayInteract(player, entity.blockPosition());
    }

    public static void damage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var states = STATES.get(player.getServer());
        var state = states == null ? null : states.get(player.getUUID());
        if (state != null && state.dimension == player.level().dimension() && (state.fields.containsKey(Power.SHIELD) || state.fields.containsKey(Power.LIBRARY)
                || state.fields.containsKey(Power.DREAM))) event.setNewDamage(event.getNewDamage() * .45F);
    }

    public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restoreFlight(player);
    }

    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        restoreFlight(player);
        var states = STATES.get(player.getServer());
        if (states != null) {
            var state = states.remove(player.getUUID());
            if (state != null) endDream(player, state);
        }
    }

    public static void stop(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        STATES.remove(event.getServer());
    }

    private static void restoreFlight(ServerPlayer player) {
        if (!player.getPersistentData().getBoolean(FLIGHT)) return;
        player.getPersistentData().remove(FLIGHT);
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    private static Vec3 hand(ServerPlayer player) { return player.getEyePosition().add(player.getLookAngle().scale(.7)).add(0,-.3,0); }
    private static void message(ServerPlayer player, String text) { player.displayClientMessage(Component.literal(text), true); }
    private static void chime(ServerPlayer player, Vec3 at, float pitch) {
        HexDomains.sound(player, at, MinecraftMod.CHAOS_SOUND.get(), .9F, pitch);
    }
    private static void scene(ServerPlayer player, int kind, int variant, Vec3 origin, Vec3 target, long now, int duration, float radius) {
        HexDomains.scene(player, kind, variant, origin, target, now, duration, radius);
    }
}
