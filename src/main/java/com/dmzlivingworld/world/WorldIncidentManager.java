package com.dmzlivingworld.world;

import com.dmzlivingworld.compat.MeditationCompat;
import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dmzlivingworld.entity.FighterRank;
import com.mojang.brigadier.Command;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Slow, history-driven incidents that reuse fighters already living around the player.
 * Unlike DynamicEncounterManager, this class does not generate disposable actors for a scene.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldIncidentManager {
    private static final double SEARCH_RADIUS = 144.0D;
    private static long lastTick = Long.MIN_VALUE;
    private static ActiveIncident active;

    private record ActiveIncident(String type, UUID first, UUID second, String firstName, String secondName, long startedAt) {}

    private WorldIncidentManager() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        long now = server.overworld().getGameTime();
        if (lastTick == now) return;
        lastTick = now;
        if (now % 40L != 0L) return;

        if (active != null) {
            tickActive(server, now);
            return;
        }
        if (!LivingWorldConfig.worldIncidents() || SparManager.isActive()) return;

        WorldIncidentData data = WorldIncidentData.get(server.overworld());
        data.ensureSchedule(now);
        if (now < data.nextIncidentAt()) return;

        ServerPlayer host = firstEligiblePlayer(server);
        if (host == null || !tryStart(host, data, false)) {
            data.scheduleNext(now, 0L); // no suitable cast right now; try again later rather than spawning filler
        } else {
            data.scheduleNext(now, server.overworld().getRandom().nextInt(60_001));
        }
    }

    private static ServerPlayer firstEligiblePlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator() && LivingWorldDimensions.isSupported(player.serverLevel())) return player;
        }
        return null;
    }

    private static boolean tryStart(ServerPlayer host, WorldIncidentData data, boolean forced) {
        List<AmbientFighterEntity> nearby = host.serverLevel().getEntitiesOfClass(AmbientFighterEntity.class,
                host.getBoundingBox().inflate(SEARCH_RADIUS, 72.0D, SEARCH_RADIUS), WorldIncidentManager::eligible);
        if (nearby.size() < 2) return false;

        // Priority one: if the simulation already produced an explicit rivalry, let it matter.
        for (AmbientFighterEntity a : nearby) {
            for (AmbientFighterEntity b : nearby) {
                if (a == b) continue;
                if (a.getRivalName().equals(b.getFighterName()) || b.getRivalName().equals(a.getFighterName())) {
                    if (startDuel(host, data, a, b, "Rival showdown")) return true;
                }
            }
        }

        // Priority two: a fighter who has organically become a real world threat can draw
        // opposition from an existing capable local. Threat recognition itself grants no power.
        for (AmbientFighterEntity threat : nearby) {
            if (!OrganicThreatManager.isMajorThreat(threat)) continue;
            AmbientFighterEntity defender = nearby.stream()
                    .filter(f -> f != threat && f.getAlignment() != FighterAlignment.BAD && f.getRank() != FighterRank.ROOKIE)
                    .filter(f -> !f.isFactionMember() || !threat.isFactionMember() || !f.getFactionId().equals(threat.getFactionId()))
                    .filter(f -> f.getBattlePower() >= Math.max(1, (int)Math.round(threat.getBattlePower() * 0.28D)))
                    .filter(f -> f.getPersonality() != FighterPersonality.CAUTIOUS)
                    .max(Comparator.comparingInt(AmbientFighterEntity::getBattlePower)).orElse(null);
            if (defender != null && startDuel(host, data, defender, threat, "Threat confrontation")) return true;
        }


        // Final fallback: a believable challenge between existing fighters with comparable power.
        List<AmbientFighterEntity> shuffled = new ArrayList<>(nearby);
        java.util.Collections.shuffle(shuffled, new java.util.Random(host.getRandom().nextLong()));
        for (AmbientFighterEntity a : shuffled) {
            for (AmbientFighterEntity b : shuffled) {
                if (a == b || a.getAlignment() == FighterAlignment.BAD && b.getAlignment() == FighterAlignment.GOOD) continue;
                double ratio = a.getBattlePower() / (double)Math.max(1, b.getBattlePower());
                if (ratio < 0.55D || ratio > 1.82D) continue;
                if (!a.getFactionId().isBlank() && !b.getFactionId().isBlank() && FactionManager.areEnemies(a, b)) continue;
                if (a.getPersonality() == FighterPersonality.CAUTIOUS && b.getPersonality() == FighterPersonality.CAUTIOUS) continue;
                if (startDuel(host, data, a, b, "Open challenge")) return true;
            }
        }
        return false;
    }

    private static boolean startDuel(ServerPlayer host, WorldIncidentData data, AmbientFighterEntity a,
                                     AmbientFighterEntity b, String type) {
        if (!eligible(a) || !eligible(b)) return false;
        a.setRivalName(b.getFighterName());
        b.setRivalName(a.getFighterName());
        a.recordLegacyEvent(type + " with " + b.getFighterName());
        b.recordLegacyEvent(type + " with " + a.getFighterName());
        a.startDuel(b);
        b.startDuel(a);
        active = new ActiveIncident(type, a.getUUID(), b.getUUID(), a.getFighterName(), b.getFighterName(),
                host.getServer().overworld().getGameTime());
        String line = type + ": " + a.getFighterName() + " vs " + b.getFighterName();
        data.record(line);
        announce(host, a.blockPosition(), line);
        return true;
    }

    private static void tickActive(MinecraftServer server, long now) {
        ActiveIncident incident = active;
        if (incident == null) return;
        AmbientFighterEntity a = find(server, incident.first);
        AmbientFighterEntity b = find(server, incident.second);
        if (a == null || b == null) {
            if (now - incident.startedAt > 2_400L) active = null;
            return;
        }

        AmbientFighterEntity winner = null;
        AmbientFighterEntity loser = null;
        if (a.isDefeated()) { winner = b; loser = a; }
        else if (b.isDefeated()) { winner = a; loser = b; }
        else if (now - incident.startedAt > 3_600L) {
            // A stalled incident ends without inventing a winner.
            a.clearDuelOpponent(); b.clearDuelOpponent();
            active = null;
            return;
        }
        if (winner == null) return;

        WorldIncidentData data = WorldIncidentData.get(server.overworld());
        String line = incident.type + " resolved: " + winner.getFighterName() + " defeated " + loser.getFighterName();
        data.record(line);
        winner.recordLegacyEvent("Won " + incident.type.toLowerCase() + " against " + loser.getFighterName());
        FighterGoalManager.focusOnRival(loser, winner.getFighterName());
        loser.recordLegacyEvent("Set a rematch with " + winner.getFighterName() + " as a personal goal");
        active = null;
    }

    private static AmbientFighterEntity find(MinecraftServer server, UUID id) {
        if (id == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            var entity = level.getEntity(id);
            if (entity instanceof AmbientFighterEntity fighter) return fighter;
        }
        return null;
    }

    private static boolean eligible(AmbientFighterEntity fighter) {
        return fighter != null && fighter.isAlive() && !fighter.isDefeated() && !fighter.isRecovering()
                && !fighter.isCaptive() && !fighter.isNonCombatant() && !fighter.isMeditating()
                && !fighter.isSanctionedMatchParticipant() && fighter.getTarget() == null && !fighter.isFactionLeader();
    }

    private static void announce(ServerPlayer host, net.minecraft.core.BlockPos pos, String line) {
        WorldEventNotifier.announce(host.serverLevel(), pos, "WORLD INCIDENT", line);
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("incidents")
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("history").executes(ctx -> history(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("start").requires(s -> s.hasPermission(2))
                        .executes(ctx -> forceStart(ctx.getSource().getPlayerOrException()))));
    }

    private static int status(ServerPlayer player) {
        WorldIncidentData data = WorldIncidentData.get(player.serverLevel());
        long now = player.getServer().overworld().getGameTime();
        String state = active == null ? "idle" : active.type + ": " + active.firstName + " / " + active.secondName;
        player.displayClientMessage(Component.literal("[Living World] Incidents " + state + " • next window in ~"
                + Math.max(1L, Math.max(0L, data.nextIncidentAt() - now) / 24_000L) + " MC day(s)."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int history(ServerPlayer player) {
        player.displayClientMessage(Component.literal("[Living World] Recent world incidents").withStyle(ChatFormatting.GOLD), false);
        for (String line : WorldIncidentData.get(player.serverLevel()).recent(8))
            player.displayClientMessage(Component.literal("• " + line).withStyle(ChatFormatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int forceStart(ServerPlayer player) {
        if (active != null || SparManager.isActive()) return 0;
        WorldIncidentData data = WorldIncidentData.get(player.serverLevel());
        return tryStart(player, data, true) ? Command.SINGLE_SUCCESS : 0;
    }

    public static boolean isActive() { return active != null; }

    public static void clearRuntime() {
        active = null;
        lastTick = Long.MIN_VALUE;
    }
}
