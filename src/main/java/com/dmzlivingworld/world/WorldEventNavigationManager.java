package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.client.LWLang;
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
                    track(player, new BlockPos(IntegerArgumentType.getInteger(ctx,"x"), IntegerArgumentType.getInteger(ctx,"y"), IntegerArgumentType.getInteger(ctx,"z")), LWLang.speechKey("message.navigation.world_event", "World Event"));
                    return 1;
                }))))
                .then(net.minecraft.commands.Commands.literal("last").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (TARGETS.remove(player.getUUID()) != null) {
                        player.displayClientMessage(Component.translatable("dmzlivingworld.message.navigation.stopped").withStyle(ChatFormatting.GRAY), false);
                        return 1;
                    }
                    Target latest = LATEST.get(player.getUUID());
                    if (latest == null) {
                        player.displayClientMessage(Component.translatable("dmzlivingworld.message.navigation.none").withStyle(ChatFormatting.GRAY), false);
                        return 0;
                    }
                    TARGETS.put(player.getUUID(), latest);
                    player.displayClientMessage(Component.translatable("dmzlivingworld.message.navigation.latest", LWLang.speechEmbedded(latest.label)).withStyle(ChatFormatting.GOLD), false);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("clear").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    TARGETS.remove(player.getUUID());
                    player.displayClientMessage(Component.translatable("dmzlivingworld.message.navigation.cleared").withStyle(ChatFormatting.GRAY), false);
                    return 1;
                })));
    }

    public static void rememberLatest(ServerPlayer player, BlockPos pos, String label) {
        if (player == null || pos == null) return;
        LATEST.put(player.getUUID(), new Target(player.level().dimension(), pos.immutable(), label == null || label.isBlank() ? LWLang.speechKey("message.navigation.world_event", "World Event") : label));
    }

    public static void track(ServerPlayer player, BlockPos pos, String label) {
        if (player == null || pos == null) return;
        Target active = TARGETS.get(player.getUUID());
        if (active != null) {
            TARGETS.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("dmzlivingworld.message.navigation.stopped").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        TARGETS.put(player.getUUID(), new Target(player.level().dimension(), pos.immutable(), label == null || label.isBlank() ? LWLang.speechKey("message.navigation.world_event", "World Event") : label));
        player.displayClientMessage(Component.translatable("dmzlivingworld.message.navigation.tracking",
                label == null ? Component.translatable("dmzlivingworld.message.navigation.world_event") : LWLang.speechEmbedded(label)).withStyle(ChatFormatting.GOLD), false);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().overworld().getGameTime() % 20L != 0L) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Target target = TARGETS.get(player.getUUID());
            if (target == null) continue;
            if (!player.level().dimension().equals(target.dimension)) {
                player.displayClientMessage(Component.translatable("dmzlivingworld.message.navigation.other_dimension", LWLang.speechEmbedded(target.label)).withStyle(ChatFormatting.GRAY), true);
                continue;
            }
            double dx = target.pos.getX() + 0.5D - player.getX(), dz = target.pos.getZ() + 0.5D - player.getZ();
            double distance = Math.sqrt(dx*dx + dz*dz);
            if (distance <= 12.0D) {
                player.displayClientMessage(Component.translatable("dmzlivingworld.message.navigation.reached", LWLang.speechEmbedded(target.label)).withStyle(ChatFormatting.GREEN), true);
                TARGETS.remove(player.getUUID());
                continue;
            }
            Component dir = direction(dx, dz);
            player.displayClientMessage(Component.translatable("dmzlivingworld.message.navigation.distance", LWLang.speechEmbedded(target.label), Math.round(distance), dir).withStyle(ChatFormatting.GOLD), true);
        }
    }

    private static Component direction(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        if (angle < 0) angle += 360.0D;
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        String value = names[(int)Math.floor((angle + 22.5D) / 45.0D) & 7];
        return Component.translatable("dmzlivingworld.direction." + value.toLowerCase(java.util.Locale.ROOT));
    }

    public static void clearRuntime() { TARGETS.clear(); LATEST.clear(); }

    public static void clearRuntime(UUID playerId) {
        if (playerId == null) return;
        TARGETS.remove(playerId);
        LATEST.remove(playerId);
    }
}
