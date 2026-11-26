package io.github.xshoutgamer.minecraftmod.hex;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class HexConfig {
    public enum HandPreset { MINIMAL, BALANCED, CINEMATIC, CINEMATIC_OVERDRIVE }
    public enum Volume { SPHERE, CYLINDER, TERRAIN, CUBE }
    public static final ModConfigSpec.EnumValue<Volume> VOLUME_SHAPE;
    public static final ModConfigSpec.BooleanValue CORRUPTION_HOSTILE, CORRUPTION_FALLOFF;
    public static final ModConfigSpec.DoubleValue CORRUPTION_DAMAGE;
    public static final ModConfigSpec.IntValue CORRUPTION_TICKS;
    public static final ModConfigSpec.IntValue EXPAND_FIRST, EXPAND_SECOND, EXPAND_MAX, REWRITE_CHARGE;
    public enum Rewrite { SCARLET, CRYSTALLINE, CELESTIAL, GOLDEN, OBSIDIAN, NATURAL, CUSTOM }
    public static final ModConfigSpec.EnumValue<Rewrite> REWRITE;
    public static final ModConfigSpec.ConfigValue<String> REWRITE_BLOCK;
    private static final ModConfigSpec.IntValue VOLUME_RADIUS, LEGACY_REWRITE_RADIUS;
    public static final ModConfigSpec.IntValue REWRITE_DEPTH, REWRITE_SECONDS;
    public enum Target { LOOK, STATIONARY, FOLLOW, AURA }

    public static final ModConfigSpec SERVER, CLIENT;
    public static final java.util.List<ModConfigSpec.EnumValue<HexSpell>> SHORTCUTS;
    public static final ModConfigSpec.EnumValue<HandPreset> HAND_PRESET;
    public static final ModConfigSpec.DoubleValue HAND_X, HAND_Y, HAND_SPACING, HAND_SCALE, HAND_PARTICLES, HAND_SMOKE,
            HAND_GLOW, HAND_FADE, HAND_EXCLUSION, HAND_LENGTH, WORLD_HAND_SCALE;
    public static final ModConfigSpec.BooleanValue HAND_MASK, HAND_ANIMATION, HAND_PERIPHERAL;
    public static final ModConfigSpec.EnumValue<Target> TARGET;
    public static final ModConfigSpec.IntValue RADIUS, ULTRA_RADIUS, EXPANSION, COLLAPSE, LIFE, COOLDOWN_PERCENT, TELEKINESIS_LIMIT,
            TELEKINESIS_RANGE, EDITS, TEMPORAL_TICKS, RUNE_RADIUS;
    private static final ModConfigSpec.IntValue RECORD_LIMIT, LEGACY_RECORD_LIMIT;
    public static final ModConfigSpec.IntValue DISINTEGRATION_RANGE, DISINTEGRATION_AREA;
    public static final ModConfigSpec.DoubleValue DISINTEGRATION_DAMAGE;
    public static final ModConfigSpec.BooleanValue EDIT, OWNER_REWRITE, RECORD_NAIL, FLIGHT, GAMEPLAY, DEMO_ORBS;
    public static final ModConfigSpec.DoubleValue GRAVITY, RESISTANCE, VOLUME, DENSITY, SMOKE, TENDRILS, LIGHTNING, GLOW, FOG;
    public static final ModConfigSpec.BooleanValue POST_EFFECTS, ORB_PLAYERS, ORB_SELF, ORB_TERRAIN, TEMPORAL_FREEZE, ECHO_ATTACK, ECHO_GUARD, DEBUG;
    public static final ModConfigSpec.IntValue ORB_CHARGE, ORB_RADIUS, ORB_MAX_RADIUS, ORB_ENHANCED_RADIUS, RUNE_LIFE,
            TEMPORAL_RADIUS, GRAVITY_RADIUS, FIELD_LIMIT, ECHO_LIFE, ECHO_RANGE, GENESIS_RADIUS;
    public static final ModConfigSpec.DoubleValue ORB_DAMAGE, ORB_PUSH, TEMPORAL_SLOW, RUNE_PROTECTION;

    public static final ModConfigSpec.IntValue ECLIPSE_RADIUS, ECLIPSE_TICKS, VOID_RADIUS, VOID_TICKS, VOID_CORE,
            VOID_CHARGE, SCRIPTURE_TICKS, SCRIPTURE_COOLDOWN, SCRIPTURE_RADIUS;
    public static final ModConfigSpec.DoubleValue ECLIPSE_DAMAGE, ECLIPSE_RESISTANCE, VOID_DAMAGE,
            CORRUPTION_INNER, CORRUPTION_MIDDLE, CORRUPTION_EDGE, CORRUPTION_PERIODIC;
    public enum MirrorProjectiles { REFLECT, DISSOLVE }
    public static final ModConfigSpec.EnumValue<MirrorProjectiles> MIRROR_PROJECTILES;

    static {
        var server = new ModConfigSpec.Builder();
        TARGET = server.defineEnum("targetMode", Target.LOOK);
        RADIUS = server.defineInRange("radius", 100, 10, 250);
        ULTRA_RADIUS = server.defineInRange("ultraRadius", 150, 10, 250);
        EXPAND_FIRST = server.defineInRange("scarletWorldFirstRadius", 150, 10, 400);
        EXPAND_SECOND = server.defineInRange("scarletWorldSecondRadius", 200, 10, 400);
        EXPAND_MAX = server.defineInRange("scarletWorldMaximumRadius", 300, 10, 400);
        EXPANSION = server.defineInRange("expansionTicks", 100, 20, 400);
        COLLAPSE = server.defineInRange("collapseTicks", 100, 20, 400);
        LIFE = server.defineInRange("durationSeconds", 0, 0, 3600);
        COOLDOWN_PERCENT = server.defineInRange("cooldownPercent", 100, 0, 400);
        TELEKINESIS_LIMIT = server.defineInRange("telekinesisObjects", 3, 1, 12);
        TELEKINESIS_RANGE = server.defineInRange("telekinesisRange", 36, 6, 64);
        EDIT = server.comment("Allow operators on a dedicated server to rewrite ordinary terrain.").define("terrainRewrite", false);
        OWNER_REWRITE = server.comment("Allow the local world owner to use Reality Rewrite, even with cheats off.")
                .define("singlePlayerTerrainRewrite", true);
        REWRITE = server.defineEnum("rewritePreset", Rewrite.SCARLET);
        REWRITE_BLOCK = server.define("rewriteCustomBlock", "minecraft:red_terracotta");
        VOLUME_SHAPE = server.defineEnum("rewriteVolume", Volume.SPHERE);
        REWRITE_CHARGE = server.defineInRange("rewriteChargeTicks", 120, 20, 200);
        VOLUME_RADIUS = server.comment("Maximum charged volume radius. Zero uses 60 or a customized legacy radius.")
                .defineInRange("rewriteVolumeMaxRadius", 0, 0, 60);
        LEGACY_REWRITE_RADIUS = server.defineInRange("rewriteMaxRadius", 13, 2, 40);
        REWRITE_DEPTH = server.defineInRange("rewriteDepth", 1, 1, 8);
        REWRITE_SECONDS = server.comment("Zero leaves changes until Genesis restores them.").defineInRange("rewriteTemporarySeconds", 0, 0, 3600);
        RECORD_NAIL = server.comment("Record future Celestial Nail excavation for Genesis, within the journal limit.").define("recordNailExcavation", false);
        EDITS = server.defineInRange("terrainEditsPerTick", 24, 1, 128);
        RECORD_LIMIT = server.comment("Undo records per dimension. Zero uses 262144 or a customized legacy limit.")
                .defineInRange("terrainJournalLimit", 0, 0, 300000);
        LEGACY_RECORD_LIMIT = server.comment("Legacy journal limit. Set terrainJournalLimit to override it.")
                .defineInRange("restorationRecordsPerDimension", 32768, 64, 300000);
        FLIGHT = server.define("ascensionFlight", true);
        RESISTANCE = server.defineInRange("ascensionDamageResistance", .20, 0, .8);
        GAMEPLAY = server.define("gameplayEffects", true);
        DEMO_ORBS = server.define("harmlessOrbs", false);
        GRAVITY = server.defineInRange("gravityPower", .13, .01, .5);
        RUNE_RADIUS = server.defineInRange("runeRadius", 9, 3, 24);
        TEMPORAL_TICKS = server.defineInRange("temporalTicks", 120, 20, 400);
        VOLUME = server.defineInRange("soundVolume", .85, 0, 2);
        ORB_CHARGE = server.defineInRange("orbMaxChargeTicks", 45, 10, 200);
        ORB_RADIUS = server.defineInRange("orbRadius", 8, 1, 24);
        ORB_MAX_RADIUS = server.defineInRange("orbChargedRadius", 15, 1, 32);
        ORB_ENHANCED_RADIUS = server.defineInRange("orbEnhancedRadius", 25, 1, 48);
        ORB_DAMAGE = server.defineInRange("orbDamage", 16D, 0, 100);
        ORB_PUSH = server.defineInRange("orbKnockback", 1.25, 0, 5);
        ORB_PLAYERS = server.define("orbFriendlyFire", false);
        ORB_TERRAIN = server.comment("Let operator orbs excavate bounded patches of natural terrain.").define("orbTerrain", false);
        ORB_SELF = server.define("orbSelfDamage", false);
        RUNE_LIFE = server.defineInRange("runeDurationSeconds", 180, 10, 3600);
        RUNE_PROTECTION = server.defineInRange("protectionRuneDamageReduction", .65, 0, 1);
        TEMPORAL_RADIUS = server.defineInRange("temporalRadius", 16, 3, 40);
        TEMPORAL_SLOW = server.defineInRange("temporalSpeedMultiplier", .2, .05, .95);
        TEMPORAL_FREEZE = server.define("temporalFreeze", false);
        GRAVITY_RADIUS = server.defineInRange("gravityRadius", 14, 3, 40);
        FIELD_LIMIT = server.defineInRange("fieldEntityLimit", 50, 1, 128);
        ECHO_LIFE = server.defineInRange("echoDurationTicks", 240, 40, 2400);
        ECHO_ATTACK = server.define("echoAttacks", true);
        ECHO_GUARD = server.define("echoDeflectsProjectiles", true);
        ECHO_RANGE = server.defineInRange("echoRange", 16, 4, 48);
        GENESIS_RADIUS = server.defineInRange("genesisRadius", 38, 4, 150);
        DEBUG = server.define("abilityDebug", false);
        DISINTEGRATION_RANGE = server.defineInRange("disintegrationRange", 32, 6, 64);
        DISINTEGRATION_AREA = server.comment("Radius for crouch-cast disintegration. Set to zero for single-block only.")
                .defineInRange("disintegrationAreaRadius", 2, 0, 3);
        DISINTEGRATION_DAMAGE = server.defineInRange("disintegrationDamage", 22D, 0, 100);
        CORRUPTION_HOSTILE = server.define("corruptionHostileOnly", true);
        CORRUPTION_FALLOFF = server.define("corruptionDamageFalloff", true);
        CORRUPTION_DAMAGE = server.defineInRange("corruptionDamage", 5D, 0, 50);
        CORRUPTION_TICKS = server.defineInRange("corruptionEffectTicks", 100, 0, 600);
        MIRROR_PROJECTILES = server.defineEnum("mirrorProjectiles", MirrorProjectiles.REFLECT);
        ECLIPSE_RADIUS = server.defineInRange("eclipseRadius", 150, 20, 240);
        ECLIPSE_TICKS = server.defineInRange("eclipseDurationTicks", 900, 80, 2400);
        ECLIPSE_DAMAGE = server.defineInRange("eclipsePulseDamage", 2D, 0, 20);
        ECLIPSE_RESISTANCE = server.defineInRange("eclipseResistance", .35, 0, .85);
        VOID_RADIUS = server.defineInRange("singularityRadius", 35, 8, 60);
        VOID_TICKS = server.defineInRange("singularityDurationTicks", 240, 40, 900);
        VOID_CORE = server.defineInRange("singularityCoreRadius", 6, 2, 12);
        VOID_CHARGE = server.defineInRange("singularityChargeTicks", 80, 20, 160);
        VOID_DAMAGE = server.defineInRange("singularityCoreDamage", 8D, 0, 40);
        SCRIPTURE_TICKS = server.defineInRange("scriptureDurationTicks", 1200, 100, 2400);
        SCRIPTURE_COOLDOWN = server.defineInRange("scriptureCooldownTicks", 1800, 100, 7200);
        SCRIPTURE_RADIUS = server.defineInRange("scriptureDominionRadius", 24, 4, 48);
        CORRUPTION_INNER = server.defineInRange("corruptionInnerCoverage", 1D, 0, 1);
        CORRUPTION_MIDDLE = server.defineInRange("corruptionMiddleCoverage", .91, 0, 1);
        CORRUPTION_EDGE = server.defineInRange("corruptionEdgeCoverage", .66, 0, 1);
        CORRUPTION_PERIODIC = server.defineInRange("corruptionPeriodicDamage", 1D, 0, 20);
        SERVER = server.build();

        var client = new ModConfigSpec.Builder();
        DENSITY = client.defineInRange("particleDensity", 1D, 0, 3);
        SMOKE = client.defineInRange("smokeDensity", 1D, 0, 3);
        TENDRILS = client.defineInRange("tendrilComplexity", 1D, 0, 3);
        LIGHTNING = client.defineInRange("lightningComplexity", 1D, 0, 3);
        GLOW = client.defineInRange("glow", 1D, 0, 2);
        FOG = client.defineInRange("atmosphericSaturation", .7, 0, 1);
        POST_EFFECTS = client.define("screenDistortion", false);
        client.push("shortcuts");
        SHORTCUTS = java.util.List.of(client.defineEnum("slot1", HexSpell.ORB),
                client.defineEnum("slot2", HexSpell.BARRIER), client.defineEnum("slot3", HexSpell.TELEKINESIS));
        client.pop();
        client.push("firstPersonMagic");
        HAND_PRESET = client.defineEnum("preset", HandPreset.CINEMATIC);
        HAND_X = client.defineInRange("horizontalPosition", .69, .55, 1.02);
        HAND_Y = client.defineInRange("verticalPosition", .68, .54, 1.02);
        HAND_SPACING = client.defineInRange("handSpacing", 0D, -.18, .18);
        HAND_SCALE = client.defineInRange("scale", 1D, .4, 1.55);
        HAND_PARTICLES = client.defineInRange("particleDensity", 1D, 0, 2);
        HAND_SMOKE = client.defineInRange("smokeOpacity", .72, 0, 1);
        HAND_GLOW = client.defineInRange("glowIntensity", 1D, 0, 2);
        HAND_FADE = client.defineInRange("cameraFade", 1D, 0, 1);
        HAND_MASK = client.define("protectCrosshair", true);
        HAND_EXCLUSION = client.defineInRange("centralExclusion", .48, .25, .70);
        HAND_LENGTH = client.defineInRange("tendrilLength", 1D, .35, 1.55);
        HAND_ANIMATION = client.define("castingAnimation", true);
        HAND_PERIPHERAL = client.define("peripheralSparks", true);
        client.pop();
        WORLD_HAND_SCALE = client.defineInRange("thirdPersonHandScale", 1D, .5, 2);
        CLIENT = client.build();
    }

    public static int rewriteRadius() {
        int configured = VOLUME_RADIUS.get();
        if (configured > 0) return Math.max(6, configured);
        int legacy = LEGACY_REWRITE_RADIUS.get();
        return legacy == 13 ? 60 : Math.max(6, legacy);
    }

    static int restorationRecordLimit() {
        int configured = RECORD_LIMIT.get();
        if (configured > 0) return Math.max(64, configured);
        int legacy = LEGACY_RECORD_LIMIT.get();
        return legacy == 8192 || legacy == 32768 ? 262144 : legacy;
    }

    private HexConfig() {}
}
