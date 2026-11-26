package io.github.xshoutgamer.minecraftmod.hex;

import io.github.xshoutgamer.minecraftmod.CelestialBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class DarkholdFields {
    private DarkholdFields() {}

    static List<Mob> hostiles(ServerPlayer caster, Vec3 center, double radius) {
        var result = new ArrayList<>(caster.level().getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(radius),
                mob -> mob instanceof Enemy && PersonalMagic.allowed(caster, mob) && mob.distanceToSqr(center) < radius * radius));
        result.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(center)));
        return result.subList(0, Math.min(32, result.size()));
    }

    static int singularity(ServerPlayer caster, Vec3 center, float radius, float charge, long age) {
        if (age < 0) return 0;
        double core = HexConfig.VOID_CORE.get() * (.8 + charge * .2);
        var candidates = caster.level().getEntities(caster, new AABB(center, center).inflate(radius),
                entity -> (entity instanceof Mob || entity instanceof ItemEntity || entity instanceof Projectile projectile
                        && projectile.getOwner() != caster) && PersonalMagic.allowed(caster, entity)
                        && entity.distanceToSqr(center) < radius * radius);
        candidates.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)));
        int damaged = 0;
        for (Entity entity : candidates.subList(0, Math.min(candidates.size(), 64))) {
            var offset = center.subtract(entity.getBoundingBox().getCenter());
            double distance = offset.length();
            var tangent = new Vec3(-offset.z, 0, offset.x).normalize();
            var motion = offset.normalize().scale(.20 + .42 * (1 - distance / radius)).add(tangent.scale(.17));
            entity.setDeltaMovement(entity.getDeltaMovement().scale(.42).add(motion));
            entity.hasImpulse = true;
            double damage = HexConfig.VOID_DAMAGE.get();
            if (age % 10 == 0 && entity instanceof Mob mob && mob instanceof Enemy && distance < core && damage > 0
                    && mob.hurt(caster.damageSources().indirectMagic(caster, caster),
                            (float) (damage * (.55 + charge * .45) * (1 - .75 * distance / core)))) damaged++;
        }
        return damaged;
    }

    static int pulse(ServerPlayer caster, Vec3 center, double radius, float damage, boolean suppress, long now) {
        int affected = 0;
        for (var mob : hostiles(caster, center, radius)) {
            if (damage > 0) mob.hurt(caster.damageSources().indirectMagic(caster, caster), damage);
            if (suppress) mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, 1, true, false));
            if (affected++ < 8) {
                var at = mob.position();
                HexDomains.scene(caster, PersonalMagic.SHADOW_FX, 0, at, at, now, 26, (float) mob.getBbHeight());
            }
        }
        return affected;
    }

    static void mirror(ServerPlayer caster, Vec3 center, double radius, long now) {
        for (var projectile : caster.level().getEntitiesOfClass(Projectile.class, new AABB(center, center).inflate(radius),
                target -> target.getOwner() != caster && (target.getOwner() == null || !caster.isAlliedTo(target.getOwner()))
                        && PersonalMagic.allowed(caster, target))) {
            var outward = projectile.position().subtract(center);
            var motion = projectile.getDeltaMovement();
            if (outward.lengthSqr() > radius * radius || motion.dot(outward) >= -.03) continue;
            if (HexConfig.MIRROR_PROJECTILES.get() == HexConfig.MirrorProjectiles.DISSOLVE) projectile.discard();
            else {
                var normal = outward.normalize();
                projectile.setDeltaMovement(motion.subtract(normal.scale(2 * motion.dot(normal))).scale(.82));
                projectile.hasImpulse = true;
            }
            var at = projectile.position();
            HexDomains.scene(caster, PersonalMagic.SHADOW_FX, 2, at, at, now, 16, 1.8F);
        }
    }

    static int platform(ServerPlayer caster, BlockPos center, int radius, int ticks) {
        var level = caster.serverLevel();
        if (!HexTerrain.canRewrite(caster)) return 0;
        var block = CelestialBlocks.ARCANE_PLATFORM.get();
        int placed = 0;
        for (var pos : HexTerrain.patch(center, radius)) {
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.mayInteract(caster, pos) || !level.isEmptyBlock(pos)
                    || !level.isUnobstructed(block.defaultBlockState(), pos, net.minecraft.world.phys.shapes.CollisionContext.empty())) continue;
            var snapshot = net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, pos);
            if (!level.setBlockAndUpdate(pos, block.defaultBlockState())) continue;
            if (net.neoforged.neoforge.event.EventHooks.onBlockPlace(caster, snapshot, net.minecraft.core.Direction.UP)) {
                snapshot.restore();
            } else {
                level.scheduleTick(pos, block, Math.max(1, ticks));
                placed++;
            }
        }
        return placed;
    }
}
