package com.dmzlivingworld.client;

/**
 * Tiny client render guard used while the character panel asks Minecraft to render the real
 * fighter entity as a portrait. The normal world renderer must not draw floating LW labels,
 * speech or deferred aura effects into that GUI portrait.
 */
public final class FighterPortraitRenderState {
    private static boolean active;

    private FighterPortraitRenderState() {}

    public static void begin() { active = true; }
    public static void end() { active = false; }
    public static boolean isActive() { return active; }
}
