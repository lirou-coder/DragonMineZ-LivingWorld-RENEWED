package com.dmzlivingworld.world;

import java.util.Locale;

/**
 * Persistent world-progression bands for procedural Living World fighters.
 *
 * These are intentionally NOT player-power tiers.  They describe what level of
 * fighter is plausible in the world after genuine saga progression has occurred.
 */
public enum WorldEra {
    EARLY_EARTH(0, "Early Earth", 650.0D),
    SAIYAN(1, "Saiyan Era", 2_400.0D),
    NAMEK_FRIEZA(2, "Namek / Frieza Era", 9_000.0D),
    ANDROID_CELL(3, "Android / Cell Era", 28_000.0D),
    BUU(4, "Majin Buu Era", 72_000.0D),
    GOD(5, "God Ki Era", 175_000.0D),
    SUPER(6, "Super Era", 380_000.0D);

    private final int id;
    private final String displayName;
    private final double powerAnchor;

    WorldEra(int id, String displayName, double powerAnchor) {
        this.id = id;
        this.displayName = displayName;
        this.powerAnchor = powerAnchor;
    }

    public int id() { return id; }
    public String displayName() { return displayName; }
    public double powerAnchor() { return powerAnchor; }

    public static WorldEra byId(int id) {
        for (WorldEra era : values()) if (era.id == id) return era;
        return EARLY_EARTH;
    }

    /**
     * Map genuine DMZ / Expanded saga IDs to the minimum world era they imply.
     * Unknown/movie/side sagas deliberately do not advance the world.
     */
    public static WorldEra impliedBySaga(String rawId) {
        if (rawId == null || rawId.isBlank()) return null;
        String id = rawId.toLowerCase(Locale.ROOT);
        return switch (id) {
            case "saiyan_saga" -> SAIYAN;
            case "frieza_saga" -> NAMEK_FRIEZA;
            case "android_saga", "future_saga" -> ANDROID_CELL;
            case "buu_saga" -> BUU;
            case "super_01_bog_rof_v2" -> GOD;
            case "super_02_universe6_v2", "super_03_goku_black_v2",
                 "super_04_tournament_power_v2", "super_05_dbs_broly_v2" -> SUPER;
            default -> null;
        };
    }
}
