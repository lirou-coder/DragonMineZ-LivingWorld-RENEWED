package com.dmzlivingworld.world;

import net.minecraft.server.level.ServerPlayer;

/**
 * Removed in 1.5. Generated distress missions invented a captive/guards after the
 * event was selected. Real faction prisoner rescues remain in FactionRequestManager.
 * This no-op shell exists only so old runtime/lifecycle references remain save-safe.
 */
public final class RescueMissionManager {
    private RescueMissionManager() {}
    public static boolean hasMission(ServerPlayer player) { return false; }
    public static boolean startRescue(ServerPlayer player, boolean debug) { return false; }
    public static boolean cancel(ServerPlayer player) { return false; }
    public static String status(ServerPlayer player) { return "Generated distress missions were removed in Living World 1.5."; }
    public static void clearRuntime(ServerPlayer player) {}
    public static void clearRuntime() {}
    public static int runtimeEntries() { return 0; }
}
