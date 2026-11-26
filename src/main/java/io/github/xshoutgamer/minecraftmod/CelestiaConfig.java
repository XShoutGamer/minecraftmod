package io.github.xshoutgamer.minecraftmod;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CelestiaConfig {
    public enum Quality { LOW, MEDIUM, HIGH, CINEMATIC, ULTRA_CELESTIAL, ULTRA_SCARLET, CINEMATIC_OVERDRIVE }
    public static final ModConfigSpec.EnumValue<Quality> QUALITY;
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.DoubleValue DAMAGE, KNOCKBACK, VOLUME;
    public static final int DEFAULT_CRATER_RADIUS = 22;
    private static final int LEGACY_CRATER_RADIUS = 15;
    private static final ModConfigSpec.IntValue RADIUS, LEGACY_RADIUS;
    public static final ModConfigSpec.IntValue LENGTH, WIDTH, ALTITUDE, CHARGE, DESCENT, DEPTH, CRACK_LENGTH, COOLDOWN, PERSIST;
    public static final ModConfigSpec.IntValue CRATER_BUDGET, FRACTURE_BUDGET;
    public static final ModConfigSpec.BooleanValue TERRAIN, LIGHT, VISUAL_ONLY;
    public static final ModConfigSpec.DoubleValue PARTICLES, GLOW, SHAKE, FLASH, MOTION, EXPOSURE, DISTORTION, ATMOSPHERE;
    public static final ModConfigSpec.BooleanValue REDUCED, MONOCHROME, REDUCED_FLASH, REDUCED_MOTION;
    public static final ModConfigSpec.DoubleValue LIGHTNING, RIBBONS, MONOCHROME_STRENGTH, CINEMATIC_SOUNDS;
    public static final ModConfigSpec.IntValue LIGHTNING_DETAIL, PARTICLE_VARIETY, MONOCHROME_TICKS, RESIDUAL_TICKS;
    public static final ModConfigSpec.IntValue VISUAL_RANGE, BLAST_RADIUS, SEAL_DIAMETER, BEAM_HEIGHT;

    public static final ModConfigSpec.BooleanValue ALTERNATE_BOLTS, SECONDARY_MOTION;
    public static final ModConfigSpec.DoubleValue BOLT_INTERVAL, BOLT_MOVEMENT, BOLT_GROWTH, BOLT_GLOW;
    public static final ModConfigSpec.IntValue BOLT_STATES, BOLT_RESIDUAL, MONOCHROME_RECOVERY;

    static {
        var server = new ModConfigSpec.Builder();
        LENGTH = server.defineInRange("nailLength", 60, 20, 80);
        WIDTH = server.defineInRange("nailWidth", 8, 4, 12);
        ALTITUDE = server.defineInRange("castingAltitude", 200, 32, 240);
        CHARGE = server.comment("Ticks spent charging after materialization and levitation.").defineInRange("chargeDurationTicks", 120, 20, 200);
        DESCENT = server.defineInRange("descentDurationTicks", 7, 4, 24);
        RADIUS = server.comment("Impact crater radius in blocks (8–40). Zero uses the default (22) or a customized legacy excavationRadius.")
                .defineInRange("craterRadius", 0, 0, 40);
        LEGACY_RADIUS = server.comment("Legacy crater setting, superseded by a nonzero craterRadius.")
                .defineInRange("excavationRadius", LEGACY_CRATER_RADIUS, 8, 28);
        DEPTH = server.defineInRange("excavationDepth", 50, 12, 64);
        CRACK_LENGTH = server.defineInRange("fractureRange", 145, 20, 155);
        COOLDOWN = server.defineInRange("cooldownSeconds", 20, 1, 600);
        DAMAGE = server.comment("Maximum shockwave damage at the impact center. Falls to zero at 100 blocks.")
                .defineInRange("impactDamage", 28D, 0D, 100D);
        KNOCKBACK = server.comment("Maximum shockwave knockback at the impact center. Set to zero to disable the push.")
                .defineInRange("impactKnockback", 2D, 0D, 5D);
        TERRAIN = server.define("terrainDestruction", true);
        LIGHT = server.comment("Place a temporary vanilla light block near the impact if permitted.").define("environmentLighting", true);
        PERSIST = server.comment("Seconds embedded before solidification. -1 keeps the animated entity.").defineInRange("solidificationSeconds", 48, -1, 3600);
        VISUAL_ONLY = server.comment("Suppress damage, carving, lighting blocks and permanent placement.").define("visualOnly", false);
        CRATER_BUDGET = server.comment("Block work budget per Nail per tick for the impact crater.")
                .defineInRange("craterEditsPerTick", 3200, 80, 4800);
        FRACTURE_BUDGET = server.comment("Block work budget per Nail per tick for the spreading fractures.")
                .defineInRange("terrainEditsPerTick", 230, 80, 600);
        CINEMATIC_SOUNDS = server.defineInRange("cinematicSoundIntensity", 1D, 0D, 1D);
        VOLUME = server.defineInRange("soundVolume", 1D, 0D, 1.5D);
        SERVER_SPEC = server.build();

        var client = new ModConfigSpec.Builder();
        QUALITY = client.defineEnum("visualQuality", Quality.CINEMATIC_OVERDRIVE);
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
        MONOCHROME_RECOVERY = client.defineInRange("impactMonochromeRecoveryTicks", 5, 1, 12);
        LIGHTNING = client.defineInRange("lightningDensity", 1D, 0D, 1.5D);
        LIGHTNING_DETAIL = client.defineInRange("lightningComplexity", 3, 1, 3);
        RIBBONS = client.defineInRange("energyRibbonDensity", 1D, 0D, 1.5D);
        PARTICLE_VARIETY = client.defineInRange("particleVariety", 4, 1, 4);
        RESIDUAL_TICKS = client.defineInRange("residualEffectTicks", 480, 40, 1100);
        MONOCHROME = client.comment("Turn the world grayscale at impact while leaving crimson lightning in color.").define("impactMonochrome", true);
        MONOCHROME_STRENGTH = client.defineInRange("impactMonochromeStrength", 1D, 0D, 1D);
        MONOCHROME_TICKS = client.defineInRange("impactMonochromeTicks", 12, 3, 20);
        REDUCED_FLASH = client.define("reducedFlash", false);
        REDUCED_MOTION = client.define("reducedMotion", false);
        REDUCED = client.define("reducedEffects", false);
        CLIENT_SPEC = client.build();
    }

    public static int craterRadius() {
        int configured = RADIUS.get();
        if (configured != 0) return Math.max(8, configured);
        int legacy = LEGACY_RADIUS.get();
        return legacy == LEGACY_CRATER_RADIUS ? DEFAULT_CRATER_RADIUS : legacy;
    }

    private CelestiaConfig() {}
}
