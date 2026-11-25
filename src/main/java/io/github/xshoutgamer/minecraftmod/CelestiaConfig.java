package io.github.xshoutgamer.minecraftmod;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CelestiaConfig {
    public enum Quality { LOW, MEDIUM, HIGH, CINEMATIC, ULTRA_CELESTIAL }
    public static final ModConfigSpec.EnumValue<Quality> QUALITY;
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.DoubleValue DAMAGE, VOLUME;
    public static final ModConfigSpec.IntValue LENGTH, WIDTH, ALTITUDE, CHARGE, DESCENT, RADIUS, DEPTH, CRACK_LENGTH, COOLDOWN, PERSIST, TERRAIN_BUDGET;
    public static final ModConfigSpec.BooleanValue TERRAIN, LIGHT, VISUAL_ONLY;
    public static final ModConfigSpec.DoubleValue PARTICLES, GLOW, SHAKE, FLASH, MOTION, EXPOSURE, DISTORTION, ATMOSPHERE;
    public static final ModConfigSpec.BooleanValue REDUCED, INVERT, REDUCED_FLASH, REDUCED_MOTION;
    public static final ModConfigSpec.DoubleValue LIGHTNING, RIBBONS, INVERT_INTENSITY, CINEMATIC_SOUNDS;
    public static final ModConfigSpec.IntValue LIGHTNING_DETAIL, PARTICLE_VARIETY, INVERT_TICKS, RESIDUAL_TICKS;
    public static final ModConfigSpec.IntValue VISUAL_RANGE, BLAST_RADIUS, SEAL_DIAMETER, BEAM_HEIGHT;

    public static final ModConfigSpec.BooleanValue ALTERNATE_BOLTS, SECONDARY_MOTION;
    public static final ModConfigSpec.DoubleValue BOLT_INTERVAL, BOLT_MOVEMENT, BOLT_GROWTH, BOLT_GLOW;
    public static final ModConfigSpec.IntValue BOLT_STATES, BOLT_RESIDUAL, INVERT_RECOVERY;

    static {
        var server = new ModConfigSpec.Builder();
        LENGTH = server.defineInRange("nailLength", 60, 20, 80);
        WIDTH = server.defineInRange("nailWidth", 8, 4, 12);
        ALTITUDE = server.defineInRange("castingAltitude", 200, 32, 240);
        CHARGE = server.comment("Ticks spent charging after materialization and levitation.").defineInRange("chargeDurationTicks", 120, 20, 200);
        DESCENT = server.defineInRange("descentDurationTicks", 7, 4, 24);
        RADIUS = server.defineInRange("excavationRadius", 15, 8, 28);
        DEPTH = server.defineInRange("excavationDepth", 50, 12, 64);
        CRACK_LENGTH = server.defineInRange("fractureRange", 145, 20, 155);
        COOLDOWN = server.defineInRange("cooldownSeconds", 20, 1, 600);
        DAMAGE = server.defineInRange("impactDamage", 28D, 0D, 100D);
        TERRAIN = server.define("terrainDestruction", true);
        LIGHT = server.comment("Place a temporary vanilla light block near the impact if permitted.").define("environmentLighting", true);
        PERSIST = server.comment("Seconds embedded before solidification. -1 keeps the animated entity.").defineInRange("solidificationSeconds", 48, -1, 3600);
        VISUAL_ONLY = server.comment("Suppress damage, carving, lighting blocks and permanent placement.").define("visualOnly", false);
        TERRAIN_BUDGET = server.defineInRange("terrainEditsPerTick", 230, 80, 600);
        CINEMATIC_SOUNDS = server.defineInRange("cinematicSoundIntensity", 1D, 0D, 1D);
        VOLUME = server.defineInRange("soundVolume", 1D, 0D, 1.5D);
        SERVER_SPEC = server.build();

        var client = new ModConfigSpec.Builder();
        QUALITY = client.defineEnum("visualQuality", Quality.HIGH);
        PARTICLES = client.defineInRange("particleDensity", 1D, 0D, 2D);
        GLOW = client.defineInRange("glowIntensity", 1D, 0D, 1.5D);
        SHAKE = client.comment("Set to zero to disable camera movement.").defineInRange("cameraShakeIntensity", 0.55D, 0D, 1D);
        FLASH = client.comment("Set to zero to disable impact flashes.").defineInRange("flashIntensity", 0.46D, 0D, 1D);
        MOTION = client.comment("Intensity of screen-space motion streaks. Zero disables them.").defineInRange("motionStreakIntensity", 0.42D, 0D, 1D);
        ATMOSPHERE = client.defineInRange("atmosphericIntensity", 0.78D, 0D, 1D);
        EXPOSURE = client.defineInRange("exposureIntensity", 0.58D, 0D, 1D);
        DISTORTION = client.defineInRange("distortionIntensity", 0.58D, 0D, 1D);
        VISUAL_RANGE = client.defineInRange("cinematicRenderDistance", 512, 96, 700);
        BLAST_RADIUS = client.defineInRange("visualBlastRadius", 320, 60, 350);
        SEAL_DIAMETER = client.defineInRange("skySealDiameter", 180, 40, 200);
        BEAM_HEIGHT = client.defineInRange("beamHeight", 360, 80, 400);
        ALTERNATE_BOLTS = client.define("alternateCrimsonGeometry", true);
        BOLT_INTERVAL = client.defineInRange("lightningGeometryIntervalSeconds", .5D, .15D, 2D);
        BOLT_STATES = client.defineInRange("lightningGeometryStates", 2, 2, 4);
        SECONDARY_MOTION = client.define("animateSecondaryBranches", true);
        BOLT_MOVEMENT = client.defineInRange("lightningMovementIntensity", 1D, 0D, 1.5D);
        BOLT_GROWTH = client.defineInRange("lightningGrowthSpeed", 1D, .4D, 2D);
        BOLT_GLOW = client.defineInRange("lightningGlowIntensity", 1D, 0D, 1.5D);
        BOLT_RESIDUAL = client.defineInRange("residualLightningTicks", 86, 12, 240);
        INVERT_RECOVERY = client.defineInRange("colorInversionRecoveryTicks", 3, 1, 8);
        LIGHTNING = client.defineInRange("lightningDensity", 1D, 0D, 1.5D);
        LIGHTNING_DETAIL = client.defineInRange("lightningComplexity", 3, 1, 3);
        RIBBONS = client.defineInRange("energyRibbonDensity", 1D, 0D, 1.5D);
        PARTICLE_VARIETY = client.defineInRange("particleVariety", 4, 1, 4);
        RESIDUAL_TICKS = client.defineInRange("residualEffectTicks", 480, 40, 1100);
        INVERT = client.comment("Brief impact negative. Has no effect with reducedFlash enabled.").define("impactColorInversion", true);
        INVERT_INTENSITY = client.defineInRange("colorInversionIntensity", 0.84D, 0D, 1D);
        INVERT_TICKS = client.defineInRange("colorInversionTicks", 5, 2, 9);
        REDUCED_FLASH = client.define("reducedFlash", false);
        REDUCED_MOTION = client.define("reducedMotion", false);
        REDUCED = client.define("reducedEffects", false);
        CLIENT_SPEC = client.build();
    }

    private CelestiaConfig() {}
}
