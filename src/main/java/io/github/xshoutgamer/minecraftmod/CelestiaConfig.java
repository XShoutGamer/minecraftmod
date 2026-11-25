package io.github.xshoutgamer.minecraftmod;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CelestiaConfig {
    public enum Quality { LOW, MEDIUM, HIGH, CINEMATIC }
    public static final ModConfigSpec.EnumValue<Quality> QUALITY;
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.DoubleValue HEIGHT, SPEED, DAMAGE, VOLUME;
    public static final ModConfigSpec.IntValue ALTITUDE, CHARGE, RADIUS, DEPTH, CRACK_LENGTH, COOLDOWN, PERSIST;
    public static final ModConfigSpec.BooleanValue TERRAIN, LIGHT;
    public static final ModConfigSpec.DoubleValue PARTICLES, GLOW, SHAKE, FLASH, MOTION;
    public static final ModConfigSpec.BooleanValue REDUCED;

    static {
        var server = new ModConfigSpec.Builder();
        HEIGHT = server.comment("Height of the monument in blocks.").defineInRange("nailHeight", 40D, 12D, 56D);
        ALTITUDE = server.defineInRange("summoningAltitude", 112, 32, 160);
        CHARGE = server.comment("Ticks spent charging after materialization and levitation.").defineInRange("chargeTicks", 60, 20, 160);
        SPEED = server.comment("Maximum falling speed in blocks per tick.").defineInRange("descentSpeed", 22D, 2D, 32D);
        RADIUS = server.defineInRange("craterRadius", 40, 5, 52);
        DEPTH = server.defineInRange("craterDepth", 15, 1, 20);
        CRACK_LENGTH = server.defineInRange("crackLength", 92, 8, 112);
        COOLDOWN = server.defineInRange("cooldownSeconds", 20, 1, 600);
        DAMAGE = server.defineInRange("impactDamage", 28D, 0D, 100D);
        TERRAIN = server.define("terrainDestruction", true);
        LIGHT = server.comment("Place a temporary vanilla light block near the impact if permitted.").define("environmentLighting", true);
        PERSIST = server.comment("Seconds embedded before solidification. -1 keeps the animated entity.").defineInRange("persistenceSeconds", 40, -1, 3600);
        VOLUME = server.defineInRange("soundVolume", 1D, 0D, 1.5D);
        SERVER_SPEC = server.build();

        var client = new ModConfigSpec.Builder();
        QUALITY = client.defineEnum("visualQuality", Quality.HIGH);
        PARTICLES = client.defineInRange("particleDensity", 1D, 0D, 2D);
        GLOW = client.defineInRange("glowIntensity", 1D, 0D, 1.5D);
        SHAKE = client.comment("Set to zero to disable camera movement.").defineInRange("cameraShakeIntensity", 0.55D, 0D, 1D);
        FLASH = client.comment("Set to zero to disable impact flashes.").defineInRange("flashIntensity", 0.46D, 0D, 1D);
        MOTION = client.comment("Intensity of screen-space motion streaks. Zero disables them.").defineInRange("motionStreakIntensity", 0.42D, 0D, 1D);
        REDUCED = client.define("reducedEffects", false);
        CLIENT_SPEC = client.build();
    }

    private CelestiaConfig() {}
}
