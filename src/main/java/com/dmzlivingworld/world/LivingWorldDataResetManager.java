package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** One deliberately destructive, server-authoritative reset used by World Settings. */
public final class LivingWorldDataResetManager {
    private LivingWorldDataResetManager() { }

    public static void reset(MinecraftServer server) {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof AmbientFighterEntity fighter) fighter.discard();
            }
        }
        FighterAfterlifeManager.resetData(server);
        FighterLegacyWorldData.get(server.overworld()).resetData();
        WorldEraData.get(server.overworld()).resetData();
        FactionWorldData.get(server.overworld()).resetData(server.overworld());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            FighterMemoryManager.resetRememberedPeople(player);
            // All LW player memory, companion and faction state is contained in Forge persistent data.
            var persistent = player.getPersistentData();
            persistent.remove("LivingWorld");
            persistent.remove("LivingWorldFighters");
            persistent.remove("LWCompanion");
        }
    }
}
