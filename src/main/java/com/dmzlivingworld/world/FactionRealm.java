package com.dmzlivingworld.world;

/** World layer a generated organization primarily inhabits. */
public enum FactionRealm {
    EARTH(0, "Earth"),
    NAMEK(1, "Namek");

    private final int id;
    private final String displayName;

    FactionRealm(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public int id() { return id; }
    public String displayName() { return displayName; }

    public static FactionRealm byId(int id) {
        return id == NAMEK.id ? NAMEK : EARTH;
    }
}
