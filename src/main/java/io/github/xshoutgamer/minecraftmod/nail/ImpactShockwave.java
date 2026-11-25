package io.github.xshoutgamer.minecraftmod.nail;

import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ImpactShockwave {
    private static final float RANGE = 100;
    static final int DURATION = 20;
    private static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, MinecraftMod.id("celestial_shockwave"));

    private final Set<UUID> reached = new HashSet<>();

    public static float reach(float age) {
        return RANGE * Math.clamp(age / DURATION, 0, 1);
    }

    void spread(ServerLevel level, CelestialNail nail, UUID caster, int age) {
        double damage = CelestiaConfig.DAMAGE.get();
        double push = CelestiaConfig.KNOCKBACK.get();
        if (damage == 0 && push == 0) return;

        var owner = caster == null ? null : level.getServer().getPlayerList().getPlayer(caster);
        var source = level.damageSources().source(DAMAGE_TYPE, nail, owner);
        var center = new Vec3(nail.getX(), nail.target().getY() + 1, nail.getZ());
        double front = reach(age);
        for (var victim : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(front),
                entity -> entity.isAlive() && !entity.isSpectator() && !entity.getUUID().equals(caster))) {
            if (victim instanceof ServerPlayer player
                    && (player.isCreative() || owner == null || !owner.canHarmPlayer(player))) continue;
            var towardCenter = center.subtract(victim.position());
            double distance = towardCenter.length();
            if (distance > front || distance >= RANGE || victim.isInvulnerableTo(source)
                    || !reached.add(victim.getUUID())) continue;

            double strength = 1 - distance / RANGE;
            if (damage > 0) victim.hurt(source, (float) (damage * strength));
            if (push > 0) {
                double x = towardCenter.x, z = towardCenter.z;
                if (x * x + z * z < 1e-8) x = 1;
                var oldMotion = victim.getDeltaMovement();
                victim.knockback(push * strength, x, z);
                if (!oldMotion.equals(victim.getDeltaMovement()) && victim instanceof ServerPlayer) {
                    victim.hurtMarked = true;
                }
            }
        }
    }

    ListTag save() {
        var tags = new ListTag();
        for (UUID uuid : reached) tags.add(NbtUtils.createUUID(uuid));
        return tags;
    }

    static ImpactShockwave load(ListTag tags) {
        var wave = new ImpactShockwave();
        tags.forEach(tag -> wave.reached.add(NbtUtils.loadUUID(tag)));
        return wave;
    }
}
