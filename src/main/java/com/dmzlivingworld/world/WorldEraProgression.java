package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.quest.Saga;
import com.dragonminez.common.stats.StatsCapability;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Dynamic, completion-only world progression over every configured non-Movies saga. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldEraProgression {
    private static final Map<UUID, Integer> QUEST_STATE_SIGNATURES = new HashMap<>();
    private WorldEraProgression() {}

    @SubscribeEvent
    public static void onQuestCompleted(DMZEvent.QuestCompletedEvent event) {
        if (event.getPlayer() == null || !(event.getPlayer().level() instanceof ServerLevel level)) return;
        Saga saga = event.getSaga();
        Quest quest = event.getQuest();
        if (saga == null || quest == null || isMovies(saga) || saga.getQuests().isEmpty()) return;

        // Starting a saga, or completing any intermediate quest, never changes world progression.
        Quest last = saga.getQuests().get(saga.getQuests().size() - 1);
        if (!java.util.Objects.equals(last.getId(), quest.getId())) return;
        redefineFromCurrentPlayers(level.getServer().getPlayerList().getPlayers(), WorldEraData.get(level),
            saga, event.getPlayer());
    }

    /** Recovers the furthest fully completed configured saga, including custom sagas. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) return;
        redefineFromCurrentPlayers(level.getServer().getPlayerList().getPlayers(), WorldEraData.get(level));
    }

    /**
     * DMZ commands, Shenlong and addons all ultimately mutate PlayerQuestData, but not every path
     * emits the same lifecycle event. A one-second signature audit catches the authoritative data
     * change itself and rebuilds the world reference, including legitimate regression after reset.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.getServer().overworld().getGameTime();
        if (now % 20L != 0L) return;
        boolean changed = false;
        for (var player : event.getServer().getPlayerList().getPlayers()) {
            PlayerQuestData quests = player.getCapability(StatsCapability.INSTANCE)
                    .map(stats -> stats.getPlayerQuestData()).orElse(null);
            int signature = quests == null ? 0 : quests.getCompletedQuestIds().hashCode();
            Integer previous = QUEST_STATE_SIGNATURES.put(player.getUUID(), signature);
            if (previous == null || previous != signature) changed = true;
        }
        QUEST_STATE_SIGNATURES.keySet().removeIf(id -> event.getServer().getPlayerList().getPlayer(id) == null);
        if (!changed || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        WorldEraData data = WorldEraData.get(event.getServer().overworld());
        // Completion events already resolved same-tick ties by enemy strength.
        if (data.lastAdvanceTick() == now) return;
        redefineFromCurrentPlayers(event.getServer().getPlayerList().getPlayers(), data);
    }

    private static void redefineFromCurrentPlayers(java.util.List<net.minecraft.server.level.ServerPlayer> players,
                                                   WorldEraData data) {
        redefineFromCurrentPlayers(players, data, null, null);
    }

    private static void redefineFromCurrentPlayers(java.util.List<net.minecraft.server.level.ServerPlayer> players,
                                                   WorldEraData data, Saga completingSaga,
                                                   net.minecraft.server.level.ServerPlayer completingPlayer) {
        double bestReference = -1.0D;
        int bestDepth = 0;
        Saga bestSaga = null;
        for (Saga saga : QuestRegistry.getAllSagas().values()) {
            if (isMovies(saga)) continue;
            boolean completed = saga == completingSaga && completingPlayer != null
                    || players.stream().anyMatch(player -> player.getCapability(StatsCapability.INSTANCE)
                    .map(stats -> isSagaCompleted(stats.getPlayerQuestData(), saga)).orElse(false));
            if (!completed) continue;

            double reference = WorldPowerScaler.lastKillReference(saga);
            if (!Double.isFinite(reference) || reference <= 0.0D) continue;
            int depth = sagaDepth(saga);
            if (reference > bestReference || (Double.compare(reference, bestReference) == 0 && depth > bestDepth)) {
                bestReference = reference;
                bestDepth = depth;
                bestSaga = saga;
            }
        }
        data.redefine(bestDepth, bestSaga == null ? "" : bestSaga.getId(), bestReference);
    }

    private static int sagaDepth(Saga saga) {
        int depth = 1;
        Set<String> visited = new HashSet<>();
        Saga current = saga;
        while (current != null && current.getRequirements() != null) {
            String previousId = current.getRequirements().previousSagaId();
            if (previousId == null || previousId.isBlank() || !visited.add(previousId)) break;
            Saga previous = QuestRegistry.getSaga(previousId);
            if (previous == null || isMovies(previous)) break;
            depth++;
            current = previous;
        }
        return depth;
    }

    private static boolean isSagaCompleted(PlayerQuestData quests, Saga saga) {
        if (saga == null || saga.getQuests().isEmpty()) return false;
        Quest last = saga.getQuests().get(saga.getQuests().size() - 1);
        return quests.isQuestCompleted(PlayerQuestData.sagaQuestKey(saga.getId(), last.getId()));
    }

    /** One-based position among configured non-Movies sagas; zero is the pre-completion era. */
    public static int configuredEraNumber(String sagaId) {
        int number = 0;
        for (Map.Entry<String, Saga> entry : QuestRegistry.getAllSagas().entrySet()) {
            Saga saga = entry.getValue();
            if (isMovies(saga)) continue;
            number++;
            if (entry.getKey().equals(sagaId) || saga.getId().equals(sagaId)) return number;
        }
        return -1;
    }

    public static Saga initialSaga() {
        for (Saga saga : QuestRegistry.getAllSagas().values()) {
            if (isMovies(saga)) continue;
            Saga.SagaRequirements requirements = saga.getRequirements();
            if (requirements == null || requirements.previousSagaId() == null
                    || requirements.previousSagaId().isBlank()) return saga;
        }
        return null;
    }

    public static boolean isMovies(Saga saga) {
        if (saga == null) return false;
        String id = saga.getId() == null ? "" : saga.getId().trim();
        String name = saga.getName() == null ? "" : saga.getName().trim();
        return "movies".equalsIgnoreCase(id) || "movies_saga".equalsIgnoreCase(id)
                || "movies".equalsIgnoreCase(name) || "dmz.saga.movies_saga".equalsIgnoreCase(name);
    }
}
