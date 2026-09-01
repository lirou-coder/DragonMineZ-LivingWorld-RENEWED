package com.dmzlivingworld.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.util.RandomSource;

public enum FighterAlignment {
    GOOD(0, "Good", ChatFormatting.GREEN),
    NEUTRAL(1, "Neutral", ChatFormatting.YELLOW),
    BAD(2, "Bad", ChatFormatting.RED);

    private final int id;
    private final String displayName;
    private final ChatFormatting color;

    FighterAlignment(int id, String displayName, ChatFormatting color) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
    }

    public int id() { return id; }
    public String displayName() { return displayName; }
    public ChatFormatting color() { return color; }

    public static FighterAlignment byId(int id) {
        for (FighterAlignment alignment : values()) if (alignment.id == id) return alignment;
        return NEUTRAL;
    }

    /** 35% good, 40% neutral, 25% bad. */
    public static FighterAlignment roll(RandomSource random) {
        int value = random.nextInt(100);
        if (value < 35) return GOOD;
        if (value < 75) return NEUTRAL;
        return BAD;
    }
}
