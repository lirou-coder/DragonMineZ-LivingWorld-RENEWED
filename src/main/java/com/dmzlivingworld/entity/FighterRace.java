package com.dmzlivingworld.entity;

import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.world.SairensRaceCompat;
import net.minecraft.util.RandomSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** DragonMineZ player races used by procedural roaming fighters. */
public enum FighterRace {
    HUMAN(0, "Human", "human", true),
    SAIYAN(1, "Saiyan", "saiyan", true),
    NAMEKIAN(2, "Namekian", "namekian", false),
    MAJIN(3, "Majin", "majin", true),
    FROST_DEMON(4, "Frost Demon", "frostdemon", false),
    BIO_ANDROID(5, "Bio-Android", "bioandroid", false),
    ZAARAKIN(6, "Zaarakin", "zaarakins", true),
    ANTORANIAN(7, "Antoranian", "antoranian", true);

    private final int id;
    private final String displayName;
    private final String dmzId;
    private final boolean gendered;

    FighterRace(int id, String displayName, String dmzId, boolean gendered) {
        this.id = id;
        this.displayName = displayName;
        this.dmzId = dmzId;
        this.gendered = gendered;
    }

    public int id() { return id; }
    public String displayName() { return displayName; }
    public String dmzId() { return dmzId; }
    public boolean gendered() { return gendered; }
            public boolean usesHair() { return this == HUMAN || this == SAIYAN || this == MAJIN
                || (this == BIO_ANDROID && SairensRaceCompat.isBioAndroidHumanModel()) || isSairensRace(); }

        public boolean isSairensRace() { return this == ZAARAKIN || this == ANTORANIAN; }

    public static FighterRace byId(int id) {
        for (FighterRace race : values()) if (race.id == id) return race;
        return HUMAN;
    }

    /** Earth stays human-heavy while non-human fighters remain common enough to notice. */
    public static FighterRace roll(RandomSource random) {
        List<FighterRace> allowed = new ArrayList<>();
        for (FighterRace race : values()) {
            if (isAllowedForNaturalSpawn(race)) allowed.add(race);
        }
        if (allowed.isEmpty()) return HUMAN;
        // Preserve the established weights when no filter is active; filtered lists are
        // intentionally uniform so a surviving uncommon race is not nearly impossible.
        int naturalRaceCount = SairensRaceCompat.isLoaded() ? values().length : 6;
        if (allowed.size() != naturalRaceCount) return allowed.get(random.nextInt(allowed.size()));
        int value = random.nextInt(100);
        if (value < 43) return HUMAN;
        if (value < 64) return SAIYAN;
        if (value < 78) return NAMEKIAN;
        if (value < 87) return MAJIN;
        if (value < 94) return FROST_DEMON;
        if (value < 97) return BIO_ANDROID;
        return SairensRaceCompat.isLoaded() ? (random.nextBoolean() ? ZAARAKIN : ANTORANIAN) : BIO_ANDROID;
    }

    public static boolean isAllowedForNaturalSpawn(FighterRace race) {
        if (race == null || (race.isSairensRace() && !SairensRaceCompat.isLoaded())) return false;
        List<String> configured = LivingWorldConfig.npcRaceBlacklist().stream()
                .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank()).toList();
        boolean listed = configured.contains(race.dmzId.toLowerCase(Locale.ROOT))
                || configured.contains(race.name().toLowerCase(Locale.ROOT))
                || configured.contains(race.displayName.toLowerCase(Locale.ROOT));
        return LivingWorldConfig.treatRaceBlacklistAsWhitelist() ? listed : !listed;
    }

    /** Weighted selection shared by realm/faction pools while honoring the race filter. */
    public static FighterRace rollWeighted(RandomSource random, Object... raceWeightPairs) {
        int total = 0;
        for (int i = 0; i + 1 < raceWeightPairs.length; i += 2) {
            FighterRace race = (FighterRace) raceWeightPairs[i];
            int weight = (Integer) raceWeightPairs[i + 1];
            if (weight > 0 && isAllowedForNaturalSpawn(race)) total += weight;
        }
        if (total <= 0) return HUMAN;
        int roll = random.nextInt(total);
        for (int i = 0; i + 1 < raceWeightPairs.length; i += 2) {
            FighterRace race = (FighterRace) raceWeightPairs[i];
            int weight = (Integer) raceWeightPairs[i + 1];
            if (weight <= 0 || !isAllowedForNaturalSpawn(race)) continue;
            if (roll < weight) return race;
            roll -= weight;
        }
        return HUMAN;
    }
}
