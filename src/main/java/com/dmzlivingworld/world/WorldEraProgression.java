package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.Saga;
import com.dragonminez.common.stats.StatsCapability;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Advances Living World's population era from real DMZ/Expanded quest activity. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldEraProgression {
    private WorldEraProgression() {}

    @SubscribeEvent
    public static void onQuestCompleted(DMZEvent.QuestCompletedEvent event) {
        if (event.getPlayer() == null || !(event.getPlayer().level() instanceof ServerLevel level)) return;
        Saga saga = event.getSaga();
        Quest quest = event.getQuest();
        if (saga == null || quest == null) return;

        WorldEraData data = WorldEraData.get(level);
        WorldEra implied = WorldEra.impliedBySaga(saga.getId());
        if (implied != null) data.advanceTo(implied, saga.getName());

        if (!saga.getQuests().isEmpty()) {
            Quest last = saga.getQuests().get(saga.getQuests().size() - 1);
            if (last.getId() == quest.getId()) data.recordSagaCompletion(saga.getName());
        }
    }
    /** Reconstruct era when 1.1 is added to an existing save. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) return;
        player.getCapability(StatsCapability.INSTANCE).ifPresent(stats -> {
            PlayerQuestData quests = stats.getPlayerQuestData();
            WorldEra recovered = recoveredEra(quests);
            if (recovered != null) WorldEraData.get(level).advanceTo(recovered, "existing saga progress");
        });
    }

    private static WorldEra recoveredEra(PlayerQuestData quests) {
        if (quests == null) return null;
        if (completedAny(quests, "super_02_universe6_v2", "super_03_goku_black_v2",
                "super_04_tournament_power_v2", "super_05_dbs_broly_v2")) return WorldEra.SUPER;
        if (completedAny(quests, "super_01_bog_rof_v2")) return WorldEra.GOD;
        if (completedAny(quests, "buu_saga")) return WorldEra.BUU;
        if (completedAny(quests, "android_saga", "future_saga")) return WorldEra.ANDROID_CELL;
        if (completedAny(quests, "frieza_saga")) return WorldEra.NAMEK_FRIEZA;
        if (completedAny(quests, "saiyan_saga")) return WorldEra.SAIYAN;
        return WorldEra.EARLY_EARTH;
    }

    private static boolean completedAny(PlayerQuestData quests, String... sagaIds) {
        for (String sagaId : sagaIds) {
            // Completing any quest in an era-defining saga means the world has entered it.
            for (int id = 1; id <= 64; id++) {
                if (quests.isQuestCompleted(PlayerQuestData.sagaQuestKey(sagaId, id))) return true;
            }
        }
        return false;
    }

}
