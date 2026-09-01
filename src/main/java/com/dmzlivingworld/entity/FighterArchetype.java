package com.dmzlivingworld.entity;

import net.minecraft.util.RandomSource;

/** Combat identity layered over rank/personality. */
public enum FighterArchetype {
    BRAWLER(0, "Brawler"),
    MARTIAL_ARTIST(1, "Martial Artist"),
    KI_SPECIALIST(2, "Ki Specialist"),
    SPEEDSTER(3, "Speed Fighter"),
    GUARDIAN(4, "Guardian");

    private final int id;
    private final String displayName;

    FighterArchetype(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public int id() { return id; }
    public String displayName() { return displayName; }

    public static FighterArchetype byId(int id) {
        for (FighterArchetype type : values()) if (type.id == id) return type;
        return MARTIAL_ARTIST;
    }

    public static FighterArchetype roll(RandomSource random, FighterRank rank) {
        int value = random.nextInt(100);
        if (rank == FighterRank.ROOKIE) {
            if (value < 45) return BRAWLER;
            if (value < 82) return MARTIAL_ARTIST;
            if (value < 91) return SPEEDSTER;
            if (value < 97) return GUARDIAN;
            return KI_SPECIALIST;
        }
        if (rank == FighterRank.TRAINED) {
            if (value < 24) return BRAWLER;
            if (value < 50) return MARTIAL_ARTIST;
            if (value < 69) return KI_SPECIALIST;
            if (value < 85) return SPEEDSTER;
            return GUARDIAN;
        }
        if (value < 18) return BRAWLER;
        if (value < 40) return MARTIAL_ARTIST;
        if (value < 63) return KI_SPECIALIST;
        if (value < 81) return SPEEDSTER;
        return GUARDIAN;
    }
}
