package com.kunyo.dbzmeditation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** One-way cleanup for transient tags left by pre-integration Meditation releases. */
public final class MeditationLegacyMigration {
    private static final String[] RETIRED_TRANSIENT_KEYS = {
            "dbzm_image_training", "dbzm_image_shadow_uuid", "dbzm_image_battle_ticks",
            "dbzm_image_pending_loss", "dbzm_image_shadow_defeated", "dbzm_image_shadow_missing_ticks",
            "dbzm_image_shadow_armed", "dbzm_image_shadow_last_end", "dbzm_image_return_delay",
            "dbzm_image_post_return_grace", "dbzm_image_arena_slot", "dbzm_image_return_dim",
            "dbzm_image_return_x", "dbzm_image_return_y", "dbzm_image_return_z", "dbzm_image_return_yaw",
            "dbzm_image_return_pitch", "dbzm_image_return_health", "dbzm_image_return_absorption",
            "dbzm_image_return_food", "dbzm_image_return_saturation", "dbzm_image_return_air",
            "dbzm_image_return_fire", "dbzm_image_return_flying", "dbzm_image_return_mayfly",
            "dbzm_image_return_selected", "dbzm_image_return_inventory", "dbzm_image_return_dmz",
            "dbzm_image_start_stage", "dbzm_image_score", "dbzm_image_streak", "dbzm_image_window",
            "dbzm_image_timer", "dbzm_image_prompt", "dbzm_image_best_streak", "dbzm_image_success_carry",
            "dbzm_post_image_recenter"
    };

    private MeditationLegacyMigration() {}

    public static void scrubRetiredSessionState(ServerPlayer player) {
        if (player == null) return;
        CompoundTag data = player.getPersistentData();
        for (String key : RETIRED_TRANSIENT_KEYS) data.remove(key);
    }
}
