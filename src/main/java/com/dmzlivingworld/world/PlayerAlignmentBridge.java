package com.dmzlivingworld.world;

import com.dragonminez.common.stats.StatsCapability;
import net.minecraft.server.level.ServerPlayer;

/** Single server-side source of truth for DMZ's 0..100 moral alignment. */
public final class PlayerAlignmentBridge {
    private PlayerAlignmentBridge() {}

    public static int alignment(ServerPlayer player) {
        if (player == null) return 100;
        return player.getCapability(StatsCapability.INSTANCE)
                .map(stats -> Math.max(0, Math.min(100, stats.getResources().getAlignment())))
                .orElse(100);
    }

    public static boolean good(ServerPlayer player) { return alignment(player) >= 67; }
    public static boolean middling(ServerPlayer player) { int value = alignment(player); return value >= 33 && value <= 66; }
    public static boolean evil(ServerPlayer player) { return alignment(player) <= 32; }
}
