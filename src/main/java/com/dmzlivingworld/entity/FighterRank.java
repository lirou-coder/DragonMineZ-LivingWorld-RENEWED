package com.dmzlivingworld.entity;

import net.minecraft.util.RandomSource;

/** Ordinary fighters are intentionally much more common than high-end veterans. */
public enum FighterRank {
    ROOKIE(0, "Rookie", 115.0D, 0.255D, 5.2D, 250, 1400, 1, false),
    TRAINED(1, "Trained", 240.0D, 0.285D, 7.6D, 1400, 5200, 2, true),
    VETERAN(2, "Veteran", 460.0D, 0.315D, 11.2D, 5200, 12500, 3, true);

    private final int id;
    private final String displayName;
    private final double maxHealth;
    private final double speed;
    private final double attackDamage;
    private final int minPower;
    private final int maxPower;
    private final int aiTier;
    private final boolean canFly;

    FighterRank(int id, String displayName, double maxHealth, double speed, double attackDamage,
                int minPower, int maxPower, int aiTier, boolean canFly) {
        this.id = id;
        this.displayName = displayName;
        this.maxHealth = maxHealth;
        this.speed = speed;
        this.attackDamage = attackDamage;
        this.minPower = minPower;
        this.maxPower = maxPower;
        this.aiTier = aiTier;
        this.canFly = canFly;
    }

    public int id() { return id; }
    public String displayName() { return displayName; }
    public double maxHealth() { return maxHealth; }
    public double speed() { return speed; }
    public double attackDamage() { return attackDamage; }
    public int aiTier() { return aiTier; }
    public boolean canFly() { return canFly; }

    public int rollBattlePower(RandomSource random) {
        return minPower + random.nextInt(maxPower - minPower + 1);
    }

    public static FighterRank byId(int id) {
        for (FighterRank rank : values()) if (rank.id == id) return rank;
        return ROOKIE;
    }

    /** 64% rookie, 29% trained, 7% veteran. */
    public static FighterRank roll(RandomSource random) {
        int value = random.nextInt(100);
        if (value < 64) return ROOKIE;
        if (value < 93) return TRAINED;
        return VETERAN;
    }
}
