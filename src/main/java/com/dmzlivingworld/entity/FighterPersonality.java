package com.dmzlivingworld.entity;

import net.minecraft.util.RandomSource;

/**
 * Small behavioral biases layered over DMZ's combat AI.
 * These never replace the native chase/melee/flight/ki implementation.
 */
public enum FighterPersonality {
    HEROIC(0, "Heroic", 0.08F, 8.0D),
    CALM(1, "Calm", 0.20F, 4.5D),
    PROUD(2, "Proud", 0.16F, 6.0D),
    AGGRESSIVE(3, "Aggressive", 0.10F, 5.5D),
    CAUTIOUS(4, "Cautious", 0.38F, 2.6D);

    private final int id;
    private final String displayName;
    private final float retreatHealthRatio;
    private final double overwhelmingPowerRatio;

    FighterPersonality(int id, String displayName, float retreatHealthRatio, double overwhelmingPowerRatio) {
        this.id = id;
        this.displayName = displayName;
        this.retreatHealthRatio = retreatHealthRatio;
        this.overwhelmingPowerRatio = overwhelmingPowerRatio;
    }

    public int id() { return id; }
    public String displayName() { return displayName; }
    public float retreatHealthRatio() { return retreatHealthRatio; }
    public double overwhelmingPowerRatio() { return overwhelmingPowerRatio; }

    public static FighterPersonality byId(int id) {
        for (FighterPersonality personality : values()) if (personality.id == id) return personality;
        return CALM;
    }

    public static FighterPersonality roll(RandomSource random, FighterAlignment alignment) {
        int value = random.nextInt(100);
        return switch (alignment) {
            case GOOD -> value < 48 ? HEROIC : value < 78 ? CALM : PROUD;
            case NEUTRAL -> value < 48 ? CALM : value < 78 ? PROUD : CAUTIOUS;
            case BAD -> value < 52 ? AGGRESSIVE : value < 76 ? PROUD : CAUTIOUS;
        };
    }
}
