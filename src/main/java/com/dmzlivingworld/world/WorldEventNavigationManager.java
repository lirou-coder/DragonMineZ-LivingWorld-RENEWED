package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Lightweight action-bar guidance to a Living World alert. No teleporting and no forced waypoints. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldEventNavigationManager {
    private static final Map<UUID, Target> TARGETS = new HashMap<>();
    private static final Map<UUID, Target> LATEST = new HashMap<>();
    private record Target(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos pos, String label) {}
    private WorldEventNavigationManager() {}

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("lwtrack")
                .then(net.minecraft.commands.Commands.argument("x", IntegerArgumentType.integer())
                .then(net.minecraft.commands.Commands.argument("y", IntegerArgumentType.integer())
                .then(net.minecraft.commands.Commands.argument("z", IntegerArgumentType.integer())
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    track(player, new BlockPos(IntegerArgumentType.getInteger(ctx,"x"), IntegerArgumentType.getInteger(ctx,"y"), IntegerArgumentType.getInteger(ctx,"z")), "World event");
                    return 1;
                }))))
                .then(net.minecraft.commands.Commands.literal("last").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (TARGETS.remove(player.getUUID()) != null) {
                        player.displayClientMessage(Component.literal("[Living World] Event tracking stopped.").withStyle(ChatFormatting.GRAY), false);
                        return 1;
                    }
                    Target latest = LATEST.get(player.getUUID());
                    if (latest == null) {
                        player.displayClientMessage(Component.literal("[Living World] No recent announced event to track.").withStyle(ChatFormatting.GRAY), false);
                        return 0;
                    }
                    TARGETS.put(player.getUUID(), latest);
                    player.displayClientMessage(Component.literal("[Living World] Tracking latest event: " + latest.label + ".").withStyle(ChatFormatting.GOLD), false);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("clear").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    TARGETS.remove(player.getUUID());
                    player.displayClientMessage(Component.literal("[Living World] Navigation cleared.").withStyle(ChatFormatting.GRAY), false);
                    return 1;
                })));
    }

    public static void rememberLatest(ServerPlayer player, BlockPos pos, String label) {
        if (player == null || pos == null) return;
        LATEST.put(player.getUUID(), new Target(player.level().dimension(), pos.immutable(), label == null || label.isBlank() ? "World event" : label));
    }

    public static void track(ServerPlayer player, BlockPos pos, String label) {
        if (player == null || pos == null) return;
        Target active = TARGETS.get(player.getUUID());
        if (active != null) {
            TARGETS.remove(player.getUUID());
            player.displayClientMessage(Component.literal("[Living World] Event tracking stopped.").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        TARGETS.put(player.getUUID(), new Target(player.level().dimension(), pos.immutable(), label == null || label.isBlank() ? "World event" : label));
        player.displayClientMessage(Component.literal("[Living World] Tracking " + (label == null ? "world event" : label) + ".").withStyle(ChatFormatting.GOLD), false);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().overworld().getGameTime() % 20L != 0L) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Target target = TARGETS.get(player.getUUID());
            if (target == null) continue;
            if (!player.level().dimension().equals(target.dimension)) {
                player.displayClientMessage(Component.literal(target.label + " • another dimension").withStyle(ChatFormatting.GRAY), true);
                continue;
            }
            double dx = target.pos.getX() + 0.5D - player.getX(), dz = target.pos.getZ() + 0.5D - player.getZ();
            double distance = Math.sqrt(dx*dx + dz*dz);
            if (distance <= 12.0D) {
                player.displayClientMessage(Component.literal("You reached the " + target.label.toLowerCase(java.util.Locale.ROOT) + " area.").withStyle(ChatFormatting.GREEN), true);
                TARGETS.remove(player.getUUID());
                continue;
            }
            String dir = direction(dx, dz);
            player.displayClientMessage(Component.literal(target.label + " • " + Math.round(distance) + " blocks • " + dir).withStyle(ChatFormatting.GOLD), true);
        }
    }

    private static String direction(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        if (angle < 0) angle += 360.0D;
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        return names[(int)Math.floor((angle + 22.5D) / 45.0D) & 7];
    }

    public static void clearRuntime() { TARGETS.clear(); LATEST.clear(); }

    public static void clearRuntime(UUID playerId) {
        if (playerId == null) return;
        TARGETS.remove(playerId);
        LATEST.remove(playerId);
    }
}
