package com.dmzlivingworld.world;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.server.level.ServerPlayer;

/** Temporarily removes Friendly Fist only while a completed Majin absorption finalizes its target. */
public final class MajinAbsorptionFriendlyFistBypass {
    private static final String RESTORE = "DMZLWRestoreFriendlyFistAfterMajinAbsorption";
    private MajinAbsorptionFriendlyFistBypass() {}

    public static void begin(ServerPlayer player) {
        if (player == null || player.getPersistentData().getBoolean(RESTORE)) return;
        StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            if (data.getStatus().isFriendlyFistEnabled()) {
                player.getPersistentData().putBoolean(RESTORE, true);
                data.getStatus().setFriendlyFistEnabled(false);
            }
        });
    }

    public static void end(ServerPlayer player) {
        if (player == null || !player.getPersistentData().getBoolean(RESTORE)) return;
        player.getPersistentData().remove(RESTORE);
        StatsProvider.get(StatsCapability.INSTANCE, player)
                .ifPresent(data -> data.getStatus().setFriendlyFistEnabled(true));
    }
}
