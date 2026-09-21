package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.WorldMenaceFighterEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Initializes direct command-created menace entities instead of leaving generic hostile Fighters. */
public final class WorldMenaceSummonManager {
    private WorldMenaceSummonManager() {}

    public static void initializeCommandSpawn(WorldMenaceFighterEntity fighter, ServerLevel level) {
        if (fighter == null || WorldMenaceManager.isWorldMenace(fighter)) return;

        boolean herobrineLoaded = false;
        boolean experimentLoaded = false;
        for (ServerLevel current : level.getServer().getAllLevels()) {
            for (Entity entity : current.getAllEntities()) {
                if (!(entity instanceof AmbientFighterEntity other) || other == fighter) continue;
                herobrineLoaded |= WorldMenaceManager.isHerobrine(other);
                experimentLoaded |= RedRibbonExperimentManager.isExperiment(other);
                if (herobrineLoaded && experimentLoaded) break;
            }
            if (herobrineLoaded && experimentLoaded) break;
        }

        if (!herobrineLoaded) {
            WorldMenaceManager.initializeCommandSpawn(fighter, level);
        } else if (!experimentLoaded) {
            RedRibbonExperimentManager.initializeCommandSpawn(fighter, level);
        } else {
            // Both unique menaces already exist. Never allow this internal entity type to
            // survive as a nameless/common Fighter or create a third menace duplicate.
            fighter.discard();
        }
    }
}
