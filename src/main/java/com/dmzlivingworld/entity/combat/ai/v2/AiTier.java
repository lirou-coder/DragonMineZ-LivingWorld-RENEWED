package com.dmzlivingworld.entity.combat.ai.v2;

public enum AiTier {
    NOVICE(1),
    SMART(2),
    ELITE(3),
    EXPERT(4);

    private final int id;

    AiTier(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    public static AiTier fromId(int id) {
        for (AiTier tier : values()) {
            if (tier.id == id) return tier;
        }
        return id > EXPERT.id ? EXPERT : NOVICE;
    }

    public boolean atLeast(AiTier other) {
        return this.ordinal() >= other.ordinal();
    }

    public AiProfile profile() {
        return AiProfile.of(this);
    }
}


