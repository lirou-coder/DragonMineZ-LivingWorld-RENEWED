package com.dmzlivingworld.world;

public enum FactionRelation {
    SAME("Same faction", 4),
    ALLY("Allies", 3),
    FRIENDLY("Friendly", 2),
    NEUTRAL("Neutral", 0),
    RIVAL("Rivals", -2),
    ENEMY("Enemies", -4);

    private final String displayName;
    private final int weight;

    FactionRelation(String displayName, int weight) {
        this.displayName = displayName;
        this.weight = weight;
    }

    public String displayName() { return displayName; }
    public int weight() { return weight; }
    public boolean allied() { return this == SAME || this == ALLY; }
    public boolean hostile() { return this == ENEMY; }
    public boolean rivalry() { return this == RIVAL || this == ENEMY; }
}
