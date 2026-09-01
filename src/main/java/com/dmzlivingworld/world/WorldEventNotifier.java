package com.dmzlivingworld.world;

import com.dmzlivingworld.config.LivingWorldConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Concise, coordinate-bearing alerts for rare scenes the player may want to go watch. */
public final class WorldEventNotifier {
    private WorldEventNotifier() {}

    public static void announce(ServerLevel level, BlockPos pos, String event, String detail) {
        if (level == null || pos == null || event == null || event.isBlank() || !LivingWorldConfig.worldEventAlerts()) return;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.serverLevel() != level || player.isSpectator()) continue;
            double dx = player.getX() - (pos.getX() + 0.5D);
            double dz = player.getZ() - (pos.getZ() + 0.5D);
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > LivingWorldConfig.worldEventAlertRadius()) continue;
            WorldEventNavigationManager.rememberLatest(player, pos, event);
            String coords = "X " + pos.getX() + "  Y " + pos.getY() + "  Z " + pos.getZ();
            player.displayClientMessage(Component.literal("LIVING WORLD • " + event).withStyle(ChatFormatting.GOLD), true);
            Component track = Component.literal("  [Track]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lwtrack " + pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Show distance and direction"))));
            player.displayClientMessage(Component.literal("[Living World] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(event + " • ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal((detail == null || detail.isBlank() ? "" : detail + " • ")
                            + coords + " • ~" + Math.max(0, Math.round(distance)) + " blocks away")
                            .withStyle(ChatFormatting.GRAY)).append(track), false);
        }
    }
}
