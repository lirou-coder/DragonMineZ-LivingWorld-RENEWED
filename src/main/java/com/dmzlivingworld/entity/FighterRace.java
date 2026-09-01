package com.dmzlivingworld.entity;

import com.dmzlivingworld.config.LivingWorldConfig;
import net.minecraft.util.RandomSource;
import java.util.ArrayList;
import java.util.List;

/** DragonMineZ player races used by procedural roaming fighters. */
public enum FighterRace {
    HUMAN(0, "Human", "human", true),
    SAIYAN(1, "Saiyan", "saiyan", true),
    NAMEKIAN(2, "Namekian", "namekian", false),
    MAJIN(3, "Majin", "majin", true),
    FROST_DEMON(4, "Frost Demon", "frostdemon", false),
    BIO_ANDROID(5, "Bio-Android", "bioandroid", false);

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
    public boolean usesHair() { return this == HUMAN || this == SAIYAN; }

    public static FighterRace byId(int id) {
        for (FighterRace race : values()) if (race.id == id) return race;
        return HUMAN;
    }

    /** Earth stays human-heavy while non-human fighters remain common enough to notice. */
    public static FighterRace roll(RandomSource random) {
        List<FighterRace> allowed = new ArrayList<>();
        List<String> configured = LivingWorldConfig.npcRaceBlacklist();
        boolean whitelist = LivingWorldConfig.treatRaceBlacklistAsWhitelist();
        for (FighterRace race : values()) {
            boolean listed = configured.contains(race.dmzId.toLowerCase(java.util.Locale.ROOT));
            if (whitelist == listed) allowed.add(race);
        }
        if (allowed.isEmpty()) return HUMAN;
        // Preserve the established weights when no filter is active; filtered lists are
        // intentionally uniform so a surviving uncommon race is not nearly impossible.
        if (allowed.size() != values().length) return allowed.get(random.nextInt(allowed.size()));
        int value = random.nextInt(100);
        if (value < 43) return HUMAN;
        if (value < 64) return SAIYAN;
        if (value < 78) return NAMEKIAN;
        if (value < 87) return MAJIN;
        if (value < 94) return FROST_DEMON;
        return BIO_ANDROID;
    }
}
