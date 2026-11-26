package io.github.xshoutgamer.minecraftmod.hex;

public enum HexSpell {
    TELEKINESIS("Absolute Telekinesis", "Hold to lift a creature or loose item along your aim.", 24, true),
    REWRITE("Reality Rewrite", "Transform a patch of terrain. Hold to grow the patch.", 60, true),
    ORB("Chaos Energy Orb", "Charge a turbulent orb and release it along your aim.", 24, true),
    BARRIER("Chaos Barrier", "Hold to catch incoming projectiles in a protective dome.", 24, true),
    ASCENSION("Scarlet Ascension", "Toggle flight and knockback resistance.", 14, false),
    FRACTURE("Reality Fracture", "Open a rift and travel to a safe destination.", 36, false),
    RUNES("Scarlet Runes", "Place a rune. Crouch while casting to cycle its function.", 14, false),
    TEMPORAL("Temporal Authority", "Temporarily slow mobs, projectiles, and loose objects.", 100, false),
    GRAVITY("Chaos Gravity Field", "Suspend nearby creatures and debris in a rising spiral.", 100, false),
    ECHO("Astral Echo", "Conjure a crimson silhouette that mirrors your movement.", 30, false),
    GENESIS("Genesis", "Rebuild terrain from your saved Reality Rewrite records.", 60, false),
    SCARLET_WORLD("Scarlet World", "Intensify the domain and extend spell reach.", 300, false);

    public final String title;
    public final String description;
    public final int cooldown;
    public final boolean sustained;

    HexSpell(String title, String description, int cooldown, boolean sustained) {
        this.title = title;
        this.description = description;
        this.cooldown = cooldown;
        this.sustained = sustained;
    }
}
