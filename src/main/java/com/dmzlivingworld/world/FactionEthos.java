package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterArchetype;
import com.dmzlivingworld.entity.FighterPersonality;
import net.minecraft.util.RandomSource;

/** High-level identity for a procedurally generated world faction. */
public enum FactionEthos {
    MARTIAL_SCHOOL("Martial school", FighterAlignment.GOOD),
    WANDERING_GUARD("Wandering protectors", FighterAlignment.GOOD),
    KI_ORDER("Ki order", FighterAlignment.NEUTRAL),
    CHALLENGERS("Proud challengers", FighterAlignment.NEUTRAL),
    MERCENARIES("Mercenary crew", FighterAlignment.NEUTRAL),
    STREET_GANG("Street gang", FighterAlignment.BAD),
    RAIDERS("Raiders", FighterAlignment.BAD),
    SEEKERS("Power seekers", FighterAlignment.NEUTRAL),
    CRIME_FAMILY("Crime family", FighterAlignment.BAD),
    SYNDICATE("Underworld syndicate", FighterAlignment.BAD),
    POWER_CULT("Power cult", FighterAlignment.BAD),
    ASCETIC_ORDER("Ascetic order", FighterAlignment.NEUTRAL),
    ROOT_CIRCLE("Root circle", FighterAlignment.GOOD),
    NAMEK_WARDENS("Namek wardens", FighterAlignment.GOOD);

    private final String displayName;
    private final FighterAlignment preferredAlignment;

    FactionEthos(String displayName, FighterAlignment preferredAlignment) {
        this.displayName = displayName;
        this.preferredAlignment = preferredAlignment;
    }

    public String displayName() { return displayName; }
    public FighterAlignment preferredAlignment() { return preferredAlignment; }

    public boolean namekNative() {
        return this == ROOT_CIRCLE || this == NAMEK_WARDENS;
    }

    public FighterPersonality rollPersonality(RandomSource random, FighterAlignment alignment) {
        int roll = random.nextInt(100);
        return switch (this) {
            case MARTIAL_SCHOOL -> roll < 45 ? FighterPersonality.CALM : roll < 78 ? FighterPersonality.PROUD : FighterPersonality.HEROIC;
            case WANDERING_GUARD, NAMEK_WARDENS -> roll < 66 ? FighterPersonality.HEROIC : FighterPersonality.CALM;
            case KI_ORDER, ASCETIC_ORDER, ROOT_CIRCLE -> roll < 62 ? FighterPersonality.CALM : roll < 82 ? FighterPersonality.PROUD : FighterPersonality.CAUTIOUS;
            case CHALLENGERS -> roll < 68 ? FighterPersonality.PROUD : roll < 88 ? FighterPersonality.AGGRESSIVE : FighterPersonality.CALM;
            case MERCENARIES -> roll < 42 ? FighterPersonality.CALM : roll < 73 ? FighterPersonality.CAUTIOUS : FighterPersonality.AGGRESSIVE;
            case STREET_GANG, CRIME_FAMILY, SYNDICATE -> roll < 58 ? FighterPersonality.AGGRESSIVE : roll < 84 ? FighterPersonality.PROUD : FighterPersonality.CAUTIOUS;
            case RAIDERS -> roll < 72 ? FighterPersonality.AGGRESSIVE : FighterPersonality.CAUTIOUS;
            case SEEKERS -> roll < 54 ? FighterPersonality.PROUD : roll < 79 ? FighterPersonality.CALM : FighterPersonality.AGGRESSIVE;
            case POWER_CULT -> roll < 52 ? FighterPersonality.PROUD : roll < 86 ? FighterPersonality.AGGRESSIVE : FighterPersonality.CALM;
        };
    }

    public FighterArchetype rollArchetype(RandomSource random) {
        int roll = random.nextInt(100);
        return switch (this) {
            case MARTIAL_SCHOOL -> roll < 44 ? FighterArchetype.MARTIAL_ARTIST : roll < 68 ? FighterArchetype.BRAWLER : roll < 83 ? FighterArchetype.SPEEDSTER : FighterArchetype.GUARDIAN;
            case WANDERING_GUARD, NAMEK_WARDENS -> roll < 35 ? FighterArchetype.GUARDIAN : roll < 63 ? FighterArchetype.MARTIAL_ARTIST : roll < 82 ? FighterArchetype.BRAWLER : FighterArchetype.KI_SPECIALIST;
            case KI_ORDER, ASCETIC_ORDER, ROOT_CIRCLE -> roll < 56 ? FighterArchetype.KI_SPECIALIST : roll < 77 ? FighterArchetype.GUARDIAN : roll < 91 ? FighterArchetype.MARTIAL_ARTIST : FighterArchetype.SPEEDSTER;
            case CHALLENGERS -> roll < 34 ? FighterArchetype.BRAWLER : roll < 62 ? FighterArchetype.MARTIAL_ARTIST : roll < 82 ? FighterArchetype.SPEEDSTER : FighterArchetype.KI_SPECIALIST;
            case MERCENARIES, SYNDICATE -> roll < 31 ? FighterArchetype.BRAWLER : roll < 56 ? FighterArchetype.KI_SPECIALIST : roll < 78 ? FighterArchetype.SPEEDSTER : FighterArchetype.GUARDIAN;
            case STREET_GANG, CRIME_FAMILY -> roll < 49 ? FighterArchetype.BRAWLER : roll < 72 ? FighterArchetype.SPEEDSTER : roll < 88 ? FighterArchetype.MARTIAL_ARTIST : FighterArchetype.KI_SPECIALIST;
            case RAIDERS -> roll < 41 ? FighterArchetype.BRAWLER : roll < 67 ? FighterArchetype.KI_SPECIALIST : roll < 86 ? FighterArchetype.SPEEDSTER : FighterArchetype.GUARDIAN;
            case SEEKERS -> roll < 34 ? FighterArchetype.KI_SPECIALIST : roll < 60 ? FighterArchetype.MARTIAL_ARTIST : roll < 80 ? FighterArchetype.SPEEDSTER : FighterArchetype.BRAWLER;
            case POWER_CULT -> roll < 50 ? FighterArchetype.KI_SPECIALIST : roll < 72 ? FighterArchetype.BRAWLER : roll < 88 ? FighterArchetype.GUARDIAN : FighterArchetype.SPEEDSTER;
        };
    }

    public static FactionEthos byId(int id) {
        FactionEthos[] values = values();
        return values[Math.floorMod(id, values.length)];
    }
}
