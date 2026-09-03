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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/** Dynamic, completion-only world progression over every configured non-Movies saga. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldEraProgression {
    private WorldEraProgression() {}

    @SubscribeEvent
    public static void onQuestCompleted(DMZEvent.QuestCompletedEvent event) {
        if (event.getPlayer() == null || !(event.getPlayer().level() instanceof ServerLevel level)) return;
        Saga saga = event.getSaga();
        Quest quest = event.getQuest();
        if (saga == null || quest == null || isMovies(saga) || saga.getQuests().isEmpty()) return;

        // Starting a saga, or completing any intermediate quest, never changes world progression.
        Quest last = saga.getQuests().get(saga.getQuests().size() - 1);
        if (last.getId() != quest.getId()) return;
        int number = configuredEraNumber(saga.getId());
        if (number > 0) WorldEraData.get(level).advanceTo(number, saga.getId(), saga.getName());
    }

    /** Recovers the furthest fully completed configured saga, including custom sagas. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) return;
        player.getCapability(StatsCapability.INSTANCE).ifPresent(stats ->
                recoverFromPlayer(WorldEraData.get(level), stats.getPlayerQuestData()));
    }

    private static void recoverFromPlayer(WorldEraData data, PlayerQuestData quests) {
        if (quests == null) return;
        int number = 0;
        for (Saga saga : QuestRegistry.getAllSagas().values()) {
            if (isMovies(saga)) continue;
            number++;
            if (isSagaCompleted(quests, saga)) data.advanceTo(number, saga.getId(), saga.getName());
        }
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
