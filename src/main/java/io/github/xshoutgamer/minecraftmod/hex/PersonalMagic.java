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
    public static final int QUICK = 3, BOOK = 4, PERSONAL = 5, GRIMOIRE = 6, GRIMOIRE_RELEASE = 7, SECONDARY = 8, LIBRARY_SELECT = 9;
    public static final int BOOK_FX = 30, DISSOLVE_FX = 31, POWER_FX = 32, MEMORY_FX = 33, GUARDIAN_FX = 34,
            ACTIVATE_FX = 35, BOOK_CLOSED = 36, SHADOW_FX = 37, SCRIPTURE_FX = 38, ALTAR_FX = 39;
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

    private static final net.minecraft.resources.ResourceLocation FIELD_SPEED = MinecraftMod.id("darkhold_field_speed");
    private static final String FLIGHT = "minecraftmod.personalMagicFlight";
    private static final Map<MinecraftServer, Map<UUID, State>> STATES = new HashMap<>();
    public enum Attunement {
        REACH("Far Reach"), DURATION("Lasting Ink"), RECOVERY("Recovery"), RENEWAL("Restoration"), NONE("Reset");
        public final String title;
        Attunement(String title) { this.title = title; }
    }

    private record Field(Vec3 at, long start, long end, float radius, float charge) {
        boolean contains(Vec3 pos) { return at.distanceToSqr(pos) < radius * radius; }
    }
    private static final class State {
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final Map<Power, Field> fields = new java.util.EnumMap<>(Power.class);
        boolean book, flight;
        long next, bookStart, scriptureReady, mirrorReady;
        Vec3 mirrorGate;
        Attunement attunement = Attunement.NONE;
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
        if (packet.action() == SECONDARY) {
            secondary(player, state, packet.spell(), now);
            return;
        }
        if (packet.action() == LIBRARY_SELECT) {
            var library = state.fields.get(Power.LIBRARY);
            if (library != null && library.contains(player.position()) && packet.spell() >= 0
                    && packet.spell() < Attunement.values().length) {
                state.attunement = Attunement.values()[packet.spell()];
                message(player, "Astral Library · " + state.attunement.title);
            }
            return;
        }
        if (packet.action() == BOOK) {
            state.book = !state.book;
            if (!state.book) {
                endDream(player, state);
                state.charging = null;
                state.fields.keySet().removeIf(power -> power.ordinal() >= Power.DREAM.ordinal());
                state.attunement = Attunement.NONE;
                syncFlight(player, state);
                syncSpeed(player, state);
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
            if (power == Power.SCRIPTURE && now < state.scriptureReady) {
                message(player, "Forbidden Scripture is recovering for " + ((state.scriptureReady - now + 19) / 20) + "s.");
                return;
            }
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
        int chargeTicks = power == Power.VOID ? HexConfig.VOID_CHARGE.get() : 40;
        float charge = Math.clamp((now - state.chargeStart) / (float) chargeTicks, 0, 1);
        cast(player, state, power, now, charge);
    }

    private static void cast(ServerPlayer player, State state, Power power, long now, float charge) {
        boolean enhanced = state.fields.containsKey(Power.SCRIPTURE);
        var eclipse = state.fields.get(Power.ECLIPSE);
        boolean boosted = eclipse != null && eclipse.contains(player.position()) && power.ordinal() < Power.DREAM.ordinal();
        double scale = (enhanced ? 1.5 : 1) * (1 + charge * .4F) * (boosted ? 1.35 : 1)
                * (state.attunement == Attunement.REACH ? 1.32 : 1);
        Vec3 at = HexAbilities.aim(player, HexConfig.DISINTEGRATION_RANGE.get() * scale);
        float radius = (float) (9 * scale);
        switch (power) {
            case FLIGHT -> {
                state.flight = !state.flight;
                syncFlight(player, state);
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
            case MIRROR -> {
                at = player.position();
                state.mirrorGate = null;
                radius = (float) (23 * scale);
                message(player, "Mirror Reality · U places gates. Crouch + U creates a platform.");
            }
            case VOID -> {
                radius = (float) (HexConfig.VOID_RADIUS.get() * (.48 + .52 * charge) * (enhanced ? 1.25 : 1)
                        * (state.attunement == Attunement.REACH ? 1.32 : 1));
                at = at.add(0, 1.4, 0);
            }
            case SHIELD, GUARDIANS, ECLIPSE, LIBRARY, SCRIPTURE -> {
                at = player.position();
                radius = switch (power) {
                    case ECLIPSE -> HexConfig.ECLIPSE_RADIUS.get();
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
        int duration = switch (power) {
            case ECLIPSE -> HexConfig.ECLIPSE_TICKS.get();
            case VOID -> HexConfig.VOID_TICKS.get();
            case SCRIPTURE -> HexConfig.SCRIPTURE_TICKS.get();
            case GUARDIANS, MIRROR -> 320;
            case LIBRARY -> 720;
            case DREAM, SHIELD -> 220;
            case PRISON -> 160;
            case LIFT -> 90;
            default -> 36;
        };
        duration = Math.round(duration * (1 + charge * .2F) * (state.attunement == Attunement.DURATION ? 1.30F : 1));
        state.fields.put(power, new Field(at, now, now + duration, radius, charge));
        syncFlight(player, state);
        if (power == Power.SCRIPTURE) {
            state.scriptureReady = now + HexConfig.SCRIPTURE_COOLDOWN.get();
            message(player, "Forbidden Scripture · U builds a platform while Creation is active.");
        }
        if (power == Power.LIBRARY) {
            var floor = player.blockPosition().below();
            DarkholdFields.platform(player, floor, 4, duration);
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                DarkholdFields.platform(player, floor.relative(direction, 5).above(), 1, duration);
            }
            message(player, "Astral Library · U opens the altar, crouch + U restores nearby recorded blocks.");
        }
        if (power == Power.ECLIPSE) {
            message(player, "Eternal Eclipse · U opens Shadow Passage.");
            HexDomains.sound(player, at, MinecraftMod.ECLIPSE_SOUND.get(), 1.15F, 1);
        }
        if (!dynamic(power)) scene(player, POWER_FX, power.ordinal(), hand(player), at, now, duration, radius);
        state.next = now + (state.attunement == Attunement.RECOVERY ? 10 : 16);
        if (HexConfig.DEBUG.get()) message(player, power.title + " · " + Math.round(charge * 100)
                + "% charge · radius " + Math.round(radius) + " · " + duration + " ticks");
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
                if (player != null) {
                    restoreFlight(player);
                    syncSpeed(player, null);
                }
                entries.remove();
                continue;
            }
            long now = player.level().getGameTime();
            if (state.book && now % 4 == 0) scene(player, BOOK_FX, state.fields.containsKey(Power.SCRIPTURE) ? 1 : 0,
                    hand(player), player.position(), state.bookStart, (int) (now - state.bookStart + 12), 1);
            if (state.charging != null) {
                if (now - state.chargeStart >= Math.max(100, HexConfig.VOID_CHARGE.get() + 10)) release(player, state, now);
                else if (now % 4 == 0) scene(player, ACTIVATE_FX, state.charging.ordinal(), hand(player),
                        state.charging == Power.VOID ? HexAbilities.aim(player, HexConfig.DISINTEGRATION_RANGE.get()) : player.position().add(0,1,0),
                        now, 10, state.charging == Power.VOID
                                ? HexConfig.VOID_RADIUS.get() * (.48F + .52F * Math.clamp((now - state.chargeStart)
                                        / (float) HexConfig.VOID_CHARGE.get(), 0, 1))
                                : Math.clamp((now - state.chargeStart) / 24F, .8F, 2));
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
            if (!state.fields.containsKey(Power.MIRROR)) state.mirrorGate = null;
            if (!state.fields.containsKey(Power.LIBRARY)) state.attunement = Attunement.NONE;
            syncFlight(player, state);
            syncSpeed(player, state);
            for (var field : state.fields.entrySet()) update(player, state, field.getKey(), field.getValue(), now);
            if (!state.book && !state.flight && state.focus == null && state.fields.isEmpty() && now >= state.next && now >= state.scriptureReady) entries.remove();
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
        syncFlight(player, state);
    }

    private static void secondary(ServerPlayer player, State state, int selected, long now) {
        var library = state.fields.get(Power.LIBRARY);
        if (library != null && library.contains(player.position()) && selected == Power.LIBRARY.ordinal()) {
            if (player.isShiftKeyDown()) {
                var journal = HexTerrain.get(player.serverLevel());
                int restored = 0, scanned = 0;
                int budget = state.attunement == Attunement.RENEWAL ? 64 : 24;
                for (var pos : journal.restoration(player.getUUID(), player.blockPosition(), 25)) {
                    if (++scanned > budget * 4 || restored >= budget) break;
                    if (journal.restore(player, pos)) restored++;
                }
                message(player, "Astral Library restored " + restored + " recorded blocks.");
            } else {
                new HexNetwork.Scene(player.level().dimension().location(), player.getUUID(), player.position(), player.position(),
                        now, 5, ALTAR_FX, state.attunement.ordinal(), 0).send(player);
            }
            return;
        }
        var mirror = state.fields.get(Power.MIRROR);
        var scripture = state.fields.get(Power.SCRIPTURE);
        if (scripture != null && selected == Power.SCRIPTURE.ordinal()) {
            createPlatform(player, scripture, now);
        } else if (mirror != null && mirror.contains(player.position()) && selected == Power.MIRROR.ordinal()) {
            if (player.isShiftKeyDown()) {
                createPlatform(player, mirror, now);
                return;
            }
            var target = HexAbilities.safeArrival(player, HexAbilities.aim(player, mirror.radius));
            if (target == null || !mirror.contains(target) || target.distanceToSqr(player.position()) < 4) {
                message(player, "Aim at a safe surface inside the mirror field.");
                return;
            }
            state.mirrorGate = target;
            scene(player, POWER_FX, Power.PORTAL.ordinal(), mirror.at, target, now, (int) (mirror.end - now), 3);
            message(player, "Mirror gates linked. Walk into a gate to travel.");
        } else {
            var eclipse = state.fields.get(Power.ECLIPSE);
            if (eclipse == null || !eclipse.contains(player.position()) || selected != Power.ECLIPSE.ordinal()) {
                message(player, "Select an active Eclipse, Mirror Reality, Astral Library, or Forbidden Scripture spell to use its action.");
                return;
            }
            var target = HexAbilities.safeArrival(player, HexAbilities.aim(player, 34));
            if (target == null || !eclipse.contains(target)) {
                message(player, "Shadow Passage needs a safe destination inside the eclipse.");
                return;
            }
            passage(player, target, now);
            state.next = now + 25;
        }
    }

    private static void createPlatform(ServerPlayer player, Field field, long now) {
        var target = BlockPos.containing(HexAbilities.aim(player, 8)).above();
        int count = DarkholdFields.platform(player, target, 2, (int) Math.min(160, field.end - now));
        message(player, count > 0 ? "Temporary platform created." : "Aim at clear space where you can place blocks.");
    }

    private static void passage(ServerPlayer player, Vec3 target, long now) {
        var start = player.position();
        player.connection.teleport(target.x, target.y, target.z, player.getYRot(), player.getXRot());
        scene(player, SHADOW_FX, 1, start, target, now, 30, 2.1F);
    }

    private static void syncFlight(ServerPlayer player, State state) {
        boolean needed = state.flight || state.fields.containsKey(Power.DREAM) || state.fields.containsKey(Power.SCRIPTURE);
        if (needed && !player.getAbilities().mayfly && !player.isSpectator()) {
            player.getPersistentData().putBoolean(FLIGHT, true);
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        } else if (!needed) restoreFlight(player);
    }

    private static void syncSpeed(ServerPlayer player, State state) {
        var speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        var eclipse = state == null ? null : state.fields.get(Power.ECLIPSE);
        double boost = state != null && state.fields.containsKey(Power.SCRIPTURE) ? .40
                : eclipse != null && eclipse.contains(player.position()) ? .20 : 0;
        var current = speed.getModifier(FIELD_SPEED);
        if (current != null && current.amount() == boost) return;
        if (current != null) speed.removeModifier(FIELD_SPEED);
        if (boost > 0) speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                FIELD_SPEED, boost, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static boolean dynamic(Power power) {
        return power == Power.SHIELD || power == Power.GUARDIANS || power == Power.LIFT;
    }

    private static void update(ServerPlayer player, State state, Power power, Field field, long now) {
        var level = player.serverLevel();
        var center = field.at;
        var radius = field.radius;
        if (power == Power.VOID) {
            long remaining = field.end - now;
            if (remaining == 16) HexDomains.sound(player, center, MinecraftMod.VOID_SOUND.get(), 1, 1);
            if (remaining > 16) {
                int affected = DarkholdFields.singularity(player, center, radius, field.charge, now - field.start);
                if (HexConfig.DEBUG.get() && affected > 0 && (now - field.start) % 40 == 0) {
                    message(player, "Void Singularity damaged " + affected + " mobs.");
                }
            }
        } else if (power == Power.ECLIPSE) {
            if (now % 40 == 0) DarkholdFields.pulse(player, center, radius, HexConfig.ECLIPSE_DAMAGE.get().floatValue(), false, now);
            if (field.contains(player.position())) {
                if (now % 60 == 0) player.heal(1);
                HexAbilities.barrier(player, player.position().add(0,1,0), 3, now);
            }
        } else if (power == Power.MIRROR) {
            if (field.contains(player.position())) DarkholdFields.mirror(player, player.position().add(0,1,0), 5, now);
            if (state.mirrorGate != null && now >= state.mirrorReady) {
                Vec3 destination = player.distanceToSqr(center) < 1.22 ? state.mirrorGate
                        : player.distanceToSqr(state.mirrorGate) < 1.22 ? center : null;
                if (destination != null) {
                    var safe = HexAbilities.safeArrival(player, destination);
                    if (safe != null) passage(player, safe, now);
                    state.mirrorReady = now + 30;
                }
            }
        } else if (power == Power.LIBRARY) {
            if (field.contains(player.position())) {
                if (now % 40 == 0) player.heal(1.5F);
                HexAbilities.barrier(player, player.position().add(0,1,0), 4, now);
            }
        } else if (power == Power.SCRIPTURE) {
            if (now % 24 == 0) DarkholdFields.pulse(player, player.position(), HexConfig.SCRIPTURE_RADIUS.get(), 4, true, now);
            HexAbilities.barrier(player, player.position().add(0,1,0), 4.5F, now);
            if (now % 4 == 0) {
                for (int i = 0; i < 4; i++) {
                    double angle = now * .064 + i * Math.PI / 2;
                    var fig = player.position().add(Math.cos(angle) * 4.1, 1.2 + .41 * i, Math.sin(angle) * 4.1);
                    scene(player, SCRIPTURE_FX, i, player.position(), fig, now, 11, 2.3F);
                }
            }
        } else if (power == Power.SHIELD) {
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
        } else if (power == Power.LIFT || power == Power.PRISON) {
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

    static boolean allowed(ServerPlayer player, Entity entity) {
        return entity.isAlive() && !(entity instanceof net.minecraft.world.entity.player.Player)
                && !player.isAlliedTo(entity)
                && !(entity instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isOwnedBy(player))
                && player.serverLevel().mayInteract(player, entity.blockPosition());
    }

    public static void damage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var states = STATES.get(player.getServer());
        var state = states == null ? null : states.get(player.getUUID());
        if (state == null || state.dimension != player.level().dimension()) return;
        var library = state.fields.get(Power.LIBRARY);
        var eclipse = state.fields.get(Power.ECLIPSE);
        float multiplier = 1;
        if (state.fields.containsKey(Power.SHIELD) || state.fields.containsKey(Power.DREAM)
                || state.fields.containsKey(Power.SCRIPTURE) || library != null && library.contains(player.position())) multiplier *= .45F;
        if (eclipse != null && eclipse.contains(player.position())) multiplier *= 1 - HexConfig.ECLIPSE_RESISTANCE.get().floatValue();
        if (multiplier < 1) event.setNewDamage(event.getNewDamage() * multiplier);
    }

    public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restoreFlight(player);
    }

    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        restoreFlight(player);
        syncSpeed(player, null);
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
