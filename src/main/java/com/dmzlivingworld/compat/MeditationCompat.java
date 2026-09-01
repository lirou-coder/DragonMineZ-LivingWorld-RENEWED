package com.dmzlivingworld.compat;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.kunyo.dbzmeditation.MeditationConfig;
import com.kunyo.dbzmeditation.MeditationIntegrationApi;
import net.minecraft.server.level.ServerPlayer;

/** Direct bridge to Living World's integrated Meditation subsystem. */
public final class MeditationCompat {
    private MeditationCompat() {}

    public static boolean isAvailable() { return MeditationConfig.SERVER.enabled.get(); }

    /** Ordinary Living World NPC meditation toggle; independent from the player's master switch. */
    public static boolean isNpcMeditationEnabled() { return MeditationConfig.SERVER.livingWorldNpcMeditation.get(); }

    public static boolean isPlayerMeditating(ServerPlayer player) {
        return player != null && MeditationIntegrationApi.isMeditating(player);
    }

    public static boolean startPlayerMeditation(ServerPlayer player) {
        return player != null && isAvailable() && MeditationIntegrationApi.tryStartMeditation(player);
    }

    /** Updates Meditation's visual-only shared-partner count. It never changes rewards. */
    public static void updateExternalMeditationPartners(ServerPlayer player, int partners) {
        if (player == null || !isAvailable() || !MeditationConfig.SERVER.groupMeditation.get()) return;
        MeditationIntegrationApi.updateExternalMeditationPartners(player, Math.max(0, partners));
    }

    public static void spawnNpcMeditationStartVisual(AmbientFighterEntity fighter) {
        // NPC meditation visuals are client-owned in Living World 1.9 so each player can
        // independently disable them from Meditation Visuals. Entity MEDITATING state is synced.
    }

    public static void spawnNpcMeditationVisual(AmbientFighterEntity fighter, boolean shared) {
        // Intentionally client-owned; see ClientMeditation.tickNpcMeditationVisuals.
    }
}
