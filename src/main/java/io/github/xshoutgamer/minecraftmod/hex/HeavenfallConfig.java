package io.github.xshoutgamer.minecraftmod.hex;

import net.neoforged.neoforge.common.ModConfigSpec;

final class HeavenfallConfig {
    record Scale(float beam, int crater, int depth, int corruption, float aperture, int range, int ticks) {}

    private record Settings(ModConfigSpec.IntValue beam, ModConfigSpec.IntValue crater, ModConfigSpec.IntValue depth,
                            ModConfigSpec.IntValue corruption, ModConfigSpec.IntValue aperture, ModConfigSpec.IntValue range,
                            ModConfigSpec.IntValue seconds) {
        Settings(ModConfigSpec.Builder spec, String name, int beam, int crater, int depth,
                 int corruption, int aperture, int range, int seconds) {
            this(spec.push(name).defineInRange("beamDiameter", beam, 2, 44),
                    spec.defineInRange("craterDiameter", crater, 4, 80),
                    spec.defineInRange("craterDepth", depth, 1, 64),
                    spec.defineInRange("corruptionRadius", corruption, 0, 100),
                    spec.defineInRange("openingDiameter", aperture, 12, 280),
                    spec.defineInRange("viewRange", range, 80, 700),
                    spec.defineInRange("beamSeconds", seconds, 1, 12));
            spec.pop();
        }
    }

    final ModConfigSpec.BooleanValue enabled, terrain;
    final ModConfigSpec.IntValue charge, reach, altitude, aftermath, creativeCooldown, survivalCooldown;
    final ModConfigSpec.IntValue ownerLimit, dimensionLimit, terrainBudget, pulseInterval, corruptionTicks;
    final ModConfigSpec.DoubleValue damage, knockback;
    private final Settings standard, charged;

    HeavenfallConfig(ModConfigSpec.Builder spec) {
        spec.push("heavenfall");
        enabled = spec.define("enabled", true);
        terrain = spec.define("terrainEdits", true);
        charge = spec.defineInRange("chargeTicks", 50, 10, 120);
        reach = spec.defineInRange("targetRange", 160, 8, 320);
        altitude = spec.defineInRange("openingAltitude", 180, 32, 320);
        aftermath = spec.defineInRange("aftermathSeconds", 90, 0, 180);
        creativeCooldown = spec.defineInRange("creativeCooldownTicks", 40, 0, 600);
        survivalCooldown = spec.defineInRange("survivalCooldownTicks", 240, 10, 1200);
        ownerLimit = spec.defineInRange("eventsPerPlayer", 4, 1, 8);
        dimensionLimit = spec.defineInRange("eventsPerDimension", 12, 1, 24);
        terrainBudget = spec.defineInRange("terrainEditsPerTick", 896, 32, 2048);
        pulseInterval = spec.defineInRange("damageIntervalTicks", 16, 5, 60);
        corruptionTicks = spec.defineInRange("mobCorruptionTicks", 100, 0, 600);
        damage = spec.defineInRange("beamDamage", 14D, 0, 100);
        knockback = spec.defineInRange("beamKnockback", 1.2, 0, 5);
        standard = new Settings(spec, "standard", 12, 30, 20, 45, 100, 300, 3);
        charged = new Settings(spec, "charged", 25, 60, 40, 80, 200, 500, 6);
        spec.pop();
    }

    Scale scale(float charge) {
        return new Scale(mix(standard.beam, charged.beam, charge) / 2F,
                Math.max(2, mix(standard.crater, charged.crater, charge) / 2),
                mix(standard.depth, charged.depth, charge), mix(standard.corruption, charged.corruption, charge),
                mix(standard.aperture, charged.aperture, charge) / 2F,
                mix(standard.range, charged.range, charge), mix(standard.seconds, charged.seconds, charge) * 20);
    }

    private static int mix(ModConfigSpec.IntValue low, ModConfigSpec.IntValue high, float charge) {
        return Math.round(low.get() + (high.get() - low.get()) * charge);
    }
}
