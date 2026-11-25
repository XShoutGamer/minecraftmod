package io.github.xshoutgamer.minecraftmod.nail;

/** Cross sections from the point to the crown, shared by the mesh and the block layout. */
public final class NailProfile {
    private static final double[] LEVELS = {0, .19, .29, .32, .35, .38, .44, .64, .70, .74, .80, .84, .88, .94, .98, 1};
    private static final double[] WIDTHS = {.03, .32, .47, .76, 1, .89, .56, .56, .69, .81, .64, .95, .87, .55, .28, .02};

    private NailProfile() {}

    public static int sections() { return LEVELS.length; }
    public static double level(int section) { return LEVELS[section]; }
    public static double width(int section) { return WIDTHS[section]; }

    public static double widthAt(double elevation) {
        for (int i = 1; i < LEVELS.length; i++) {
            if (elevation <= LEVELS[i]) {
                double t = Math.max(0, (elevation - LEVELS[i - 1]) / (LEVELS[i] - LEVELS[i - 1]));
                return WIDTHS[i - 1] + (WIDTHS[i] - WIDTHS[i - 1]) * t;
            }
        }
        return WIDTHS[WIDTHS.length - 1];
    }

    public static double descent(double from, double to, double progress) {
        double t = Math.clamp(progress, 0, 1);
        return from + (to - from) * t * t * (1.17 - 0.17 * t);
    }
}
