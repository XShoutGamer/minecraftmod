package io.github.xshoutgamer.minecraftmod.hex;

import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class HexDomains {
    private static final Map<MinecraftServer, Map<UUID, Domain>> WORLDS = new HashMap<>();
    private static final Map<MinecraftServer, java.util.Set<UUID>> TEMPORAL = new HashMap<>();

    static final class Domain {
        final ResourceKey<Level> dimension;
        final HexConfig.Target mode;
        float radius;
        float expansionFrom;
        long expansionStart;
        int expansionStage;
        final HexAbilities abilities = new HexAbilities();
        Vec3 center;
        long phaseStart, deadline, established, nextInput;
        int phase = HexNetwork.AWAKENING, collapseProgress = 1000;

        Domain(ServerPlayer player, boolean ultra, long now) {
            dimension = player.level().dimension();
            mode = HexConfig.TARGET.get();
            center = mode == HexConfig.Target.LOOK ? HexAbilities.aim(player, 64) : player.position();
            // Long-range targeting still starts at the caster if the ray misses.
            if (mode == HexConfig.Target.LOOK && center.distanceTo(player.position()) >= 64) center = player.position();
            radius = mode == HexConfig.Target.AURA ? 9 : ultra ? HexConfig.ULTRA_RADIUS.get() : HexConfig.RADIUS.get();
            state(HexNetwork.AWAKENING, now, 32);
        }

        void state(int next, long now, int duration) {
            phase = next;
            phaseStart = now;
            deadline = now + duration;
        }

        boolean empowered() { return phase == HexNetwork.ACTIVE || phase == HexNetwork.ENHANCED; }
        boolean gameplay() { return mode != HexConfig.Target.AURA && HexConfig.GAMEPLAY.get(); }
        double scale() { return phase == HexNetwork.ENHANCED ? 1.42 : 1; }

        float reach(long now) {
            if (phase != HexNetwork.ENHANCED || expansionFrom >= radius) return radius;
            float t = Math.clamp((now - expansionStart) / (float) HexConfig.EXPANSION.get(), 0, 1);
            return expansionFrom + (radius - expansionFrom) * t * t * (3 - 2 * t);
        }

        boolean expand(long now) {
            if (mode == HexConfig.Target.AURA) return false;
            int cap = Math.max((int) radius, HexConfig.EXPAND_MAX.get());
            for (; expansionStage < 3; expansionStage++) {
                int next = Math.min(cap, switch (expansionStage) {
                    case 0 -> HexConfig.EXPAND_FIRST.get();
                    case 1 -> HexConfig.EXPAND_SECOND.get();
                    default -> HexConfig.EXPAND_MAX.get();
                });
                if (next <= radius) continue;
                expansionFrom = reach(now);
                expansionStart = now;
                radius = next;
                expansionStage++;
                state(HexNetwork.ENHANCED, now, Math.max(360, HexConfig.EXPANSION.get()));
                return true;
            }
            return false;
        }

        void sync(ServerPlayer player) {
            scene(player, phase, phase == HexNetwork.COLLAPSING ? collapseProgress : 0, center, player.position(), phaseStart, Math.toIntExact(deadline - phaseStart), reach(player.level().getGameTime()));
        }
    }

    private HexDomains() {}

    static void input(ServerPlayer player, HexNetwork.Input packet) {
        if (!player.isAlive() || player.isSpectator()) return;
        var domains = WORLDS.computeIfAbsent(player.getServer(), server -> new HashMap<>());
        var now = player.level().getGameTime();
        var domain = domains.get(player.getUUID());
        if (domain != null && domain.dimension != player.level().dimension()) return;
        if (packet.action() == HexNetwork.TOGGLE) {
            if (domain == null) {
                if (domains.size() >= 16) return;
                var created = new Domain(player, packet.ultra(), now);
                domains.put(player.getUUID(), created);
                created.nextInput = now + 8;
                created.sync(player);
                sound(player, player.position(), MinecraftMod.HEX_AWAKEN.get(), .8F, 1);
            } else if (now >= domain.nextInput && domain.phase != HexNetwork.COLLAPSING) {
                collapse(domain, player, now);
            }
        } else if (domain != null && domain.empowered()) {
            if (packet.action() == HexNetwork.RELEASE) {
                domain.abilities.release(domain, player, now);
            } else if (packet.action() == HexNetwork.PRESS && now >= domain.nextInput
                    && packet.spell() >= 0 && packet.spell() < HexSpell.values().length) {
                domain.nextInput = now + 4;
                domain.abilities.press(domain, player, HexSpell.values()[packet.spell()], now);
            }
        }
    }

    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        TEMPORAL.computeIfAbsent(server, key -> new java.util.HashSet<>()).clear();
        if (server.getTickCount() % 20 == 0) {
            for (var level : server.getAllLevels()) HexTerrain.get(level).tickPermanence(level);
            for (var player : server.getPlayerList().getPlayers()) {
                if (HexTerrain.canRestore(player)) HexTerrain.get(player.serverLevel()).restoreExpired(player);
            }
        }
        var domains = WORLDS.get(server);
        if (domains == null) return;
        Iterator<Map.Entry<UUID, Domain>> iterator = domains.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var player = server.getPlayerList().getPlayer(entry.getKey());
            var domain = entry.getValue();
            if (player == null || !player.isAlive() || player.isSpectator() || player.level().dimension() != domain.dimension) {
                var level = server.getLevel(domain.dimension);
                if (level != null) broadcast(level, entry.getKey(), HexNetwork.COLLAPSING, 1000, domain.center,
                        domain.center, level.getGameTime(), 35, domain.radius);
                if (player != null) domain.abilities.clear(player);
                iterator.remove();
                continue;
            }
            var now = player.level().getGameTime();
            if (domain.mode == HexConfig.Target.FOLLOW || domain.mode == HexConfig.Target.AURA) domain.center = player.position();
            if (domain.phase == HexNetwork.COLLAPSING && now >= domain.deadline) {
                iterator.remove();
                continue;
            }
            if (domain.phase == HexNetwork.AWAKENING && now >= domain.deadline) {
                domain.state(HexNetwork.EXPANDING, now, HexConfig.EXPANSION.get());
                domain.sync(player);
                sound(player, domain.center, MinecraftMod.HEX_EXPAND.get(), 1.1F, 1);
            } else if (domain.phase == HexNetwork.EXPANDING && now >= domain.deadline) {
                domain.state(HexNetwork.ACTIVE, now, 0);
                domain.established = now;
                domain.sync(player);
            } else if (domain.phase == HexNetwork.ENHANCED && now >= domain.deadline) {
                domain.state(HexNetwork.ACTIVE, now, 0);
                domain.sync(player);
            }
            if (domain.empowered()) {
                int life = HexConfig.LIFE.get();
                if (life > 0 && now - domain.established >= life * 20L) collapse(domain, player, now);
                else domain.abilities.tick(domain, player, now);
                if (now % 140 == 0) sound(player, domain.center, MinecraftMod.HEX_HUM.get(), .42F, 1);
            }
            if (now % (domain.phase == HexNetwork.ENHANCED ? 2 : 10) == 0) domain.sync(player);
        }
    }

    private static void collapse(Domain domain, ServerPlayer player, long now) {
        domain.radius = domain.reach(now);
        float progress = domain.phase == HexNetwork.AWAKENING ? 0
                : domain.phase == HexNetwork.EXPANDING
                ? Math.clamp((now - domain.phaseStart) / (float) (domain.deadline - domain.phaseStart), 0, 1) : 1;
        domain.state(HexNetwork.COLLAPSING, now, HexConfig.COLLAPSE.get());
        domain.abilities.clear(player);
        domain.collapseProgress = Math.round(progress * 1000);
        domain.sync(player);
        sound(player, domain.center, MinecraftMod.HEX_COLLAPSE.get(), .8F, 1);
    }

    static boolean silenced(ServerPlayer player) { return hasRune(player, true); }

    private static boolean hasRune(ServerPlayer player, boolean silence) {
        var active = WORLDS.get(player.getServer());
        if (active == null) return false;
        var players = player.getServer().getPlayerList();
        for (var entry : active.entrySet()) {
            var domain = entry.getValue();
            var owner = players.getPlayer(entry.getKey());
            if (owner != null && domain.empowered() && domain.gameplay()
                    && domain.abilities.runeApplies(owner, player, silence)) return true;
        }
        return false;
    }

    public static void adjustDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var domains = WORLDS.get(player.getServer());
        var domain = domains == null ? null : domains.get(player.getUUID());
        float multiplier = 1;
        if (domain != null && domain.gameplay() && domain.abilities.ascended) multiplier -= HexConfig.RESISTANCE.get().floatValue();
        if (hasRune(player, false)) {
            multiplier *= 1 - HexConfig.RUNE_PROTECTION.get().floatValue();
            scene(player, HexNetwork.BARRIER_FX, 2, player.position(), player.position().add(0, 1, 0),
                    player.level().getGameTime(), 14, 2.25F);
        }
        if (multiplier < 1) event.setNewDamage(event.getNewDamage() * multiplier);
    }

    public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) HexAbilities.resetFlight(player);
    }

    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) HexAbilities.resetFlight(player);
    }

    static void slow(ServerPlayer caster, net.minecraft.world.entity.Entity entity) {
        TEMPORAL.computeIfAbsent(caster.getServer(), key -> new java.util.HashSet<>()).add(entity.getUUID());
    }

    public static void temporalTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        var entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        var affected = TEMPORAL.get(level.getServer());
        if (affected == null || !affected.contains(entity.getUUID())) return;
        int period = Math.max(2, (int) Math.round(1 / HexConfig.TEMPORAL_SLOW.get()));
        if (HexConfig.TEMPORAL_FREEZE.get() || Math.floorMod(level.getServer().getTickCount() + entity.getId(), period) != 0) {
            event.setCanceled(true);
        }
    }

    public static void stop(ServerStoppedEvent event) {
        WORLDS.remove(event.getServer());
        TEMPORAL.remove(event.getServer());
    }

    static void scene(ServerPlayer player, int kind, int variant, Vec3 origin, Vec3 target, long now, int duration, float radius) {
        broadcast(player.serverLevel(), player.getUUID(), kind, variant, origin, target, now, duration, radius);
    }

    private static void broadcast(ServerLevel level, UUID owner, int kind, int variant, Vec3 origin, Vec3 target,
                                  long now, int duration, float radius) {
        var scene = new HexNetwork.Scene(level.dimension().location(), owner, origin, target, now, duration, kind, variant, radius);
        double distance = Math.max(190, radius + 150);
        for (var viewer : level.players()) {
            if (viewer.getUUID().equals(owner) || viewer.distanceToSqr(origin) < distance * distance
                    || viewer.distanceToSqr(target) < distance * distance) scene.send(viewer);
        }
    }

    static void sound(ServerPlayer player, Vec3 at, net.minecraft.sounds.SoundEvent event, float volume, float pitch) {
        player.level().playSound(null, at.x, at.y, at.z, event, SoundSource.PLAYERS, volume * HexConfig.VOLUME.get().floatValue(), pitch);
    }
}
