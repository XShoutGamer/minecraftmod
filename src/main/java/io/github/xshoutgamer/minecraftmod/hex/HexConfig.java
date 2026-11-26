package io.github.xshoutgamer.minecraftmod.hex;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class HexConfig {
    public enum Target { LOOK, STATIONARY, FOLLOW, AURA }

    public static final ModConfigSpec SERVER, CLIENT;
    public static final ModConfigSpec.EnumValue<Target> TARGET;
    public static final ModConfigSpec.IntValue RADIUS, ULTRA_RADIUS, EXPANSION, COLLAPSE, LIFE, COOLDOWN_PERCENT, TELEKINESIS_LIMIT,
            TELEKINESIS_RANGE, EDITS, RECORDS, TEMPORAL_TICKS, RUNE_RADIUS;
    public static final ModConfigSpec.BooleanValue EDIT, RECORD_NAIL, FLIGHT, GAMEPLAY, DEMO_ORBS;
    public static final ModConfigSpec.DoubleValue GRAVITY, RESISTANCE, VOLUME, DENSITY, SMOKE, TENDRILS, LIGHTNING, GLOW, FOG;
    public static final ModConfigSpec.BooleanValue POST_EFFECTS;

    static {
        var server = new ModConfigSpec.Builder();
        TARGET = server.defineEnum("targetMode", Target.LOOK);
        RADIUS = server.defineInRange("radius", 100, 10, 250);
        ULTRA_RADIUS = server.defineInRange("ultraRadius", 150, 10, 250);
        EXPANSION = server.defineInRange("expansionTicks", 100, 20, 400);
        COLLAPSE = server.defineInRange("collapseTicks", 100, 20, 400);
        LIFE = server.defineInRange("durationSeconds", 0, 0, 3600);
        COOLDOWN_PERCENT = server.defineInRange("cooldownPercent", 100, 0, 400);
        TELEKINESIS_LIMIT = server.defineInRange("telekinesisObjects", 3, 1, 12);
        TELEKINESIS_RANGE = server.defineInRange("telekinesisRange", 36, 6, 64);
        EDIT = server.comment("Allow operators or single-player hosts to rewrite ordinary terrain.").define("terrainRewrite", false);
        RECORD_NAIL = server.comment("Record future Celestial Nail excavation for Genesis, within the journal limit.").define("recordNailExcavation", false);
        EDITS = server.defineInRange("terrainEditsPerTick", 24, 1, 128);
        RECORDS = server.defineInRange("restorationRecordsPerDimension", 8192, 64, 32768);
        FLIGHT = server.define("ascensionFlight", true);
        RESISTANCE = server.defineInRange("ascensionDamageResistance", .20, 0, .8);
        GAMEPLAY = server.define("gameplayEffects", true);
        DEMO_ORBS = server.define("harmlessOrbs", false);
        GRAVITY = server.defineInRange("gravityPower", .13, .01, .5);
        RUNE_RADIUS = server.defineInRange("runeRadius", 9, 3, 24);
        TEMPORAL_TICKS = server.defineInRange("temporalTicks", 120, 20, 400);
        VOLUME = server.defineInRange("soundVolume", .85, 0, 2);
        SERVER = server.build();

        var client = new ModConfigSpec.Builder();
        DENSITY = client.defineInRange("particleDensity", 1D, 0, 3);
        SMOKE = client.defineInRange("smokeDensity", 1D, 0, 3);
        TENDRILS = client.defineInRange("tendrilComplexity", 1D, 0, 3);
        LIGHTNING = client.defineInRange("lightningComplexity", 1D, 0, 3);
        GLOW = client.defineInRange("glow", 1D, 0, 2);
        FOG = client.defineInRange("atmosphericSaturation", .7, 0, 1);
        POST_EFFECTS = client.define("screenDistortion", false);
        CLIENT = client.build();
    }

    private HexConfig() {}
}
