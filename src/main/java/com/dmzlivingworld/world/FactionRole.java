package com.dmzlivingworld.world;

/** Generic organizational rank; factions render it through their own structure titles. */
public enum FactionRole {
    RECRUIT(0, 0.82F),
    MEMBER(1, 1.00F),
    ENFORCER(2, 1.12F),
    LIEUTENANT(3, 1.28F),
    LEADER(4, 1.58F);

    private final int id;
    private final float powerMultiplier;

    FactionRole(int id, float powerMultiplier) {
        this.id = id;
        this.powerMultiplier = powerMultiplier;
    }

    public int id() { return id; }
    public float powerMultiplier() { return powerMultiplier; }

    public static FactionRole byId(int id) {
        FactionRole[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, id))];
    }
}
