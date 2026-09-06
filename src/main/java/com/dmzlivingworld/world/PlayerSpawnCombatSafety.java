package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Prevents unsolicited Living World combat in the five-chunk radius around a player's spawnpoint. */
public final class PlayerSpawnCombatSafety {
    private static final double RADIUS = 5.0D * 16.0D;
    private static final double RADIUS_SQ = RADIUS * RADIUS;

    private PlayerSpawnCombatSafety() {}

    /** Starting a Spar with the target is the only way to bypass this protection; self-defence never does. */
    public static boolean blocksTarget(AmbientFighterEntity fighter, ServerPlayer player) {
        if (fighter == null || player == null) return false;
        if (fighter.isSanctionedMatchParticipant() && fighter.isSanctionedOpponent(player)) return false;
        return isInsideProtectedArea(player);
    }

    /** Raw location check for encounters which must withdraw even after the player strikes first. */
    public static boolean isInsideProtectedArea(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return false;
        ResourceKey<Level> spawnDimension = player.getRespawnDimension();
        BlockPos spawn = player.getRespawnPosition();
        if (spawn == null) {
            ServerLevel overworld = level.getServer().overworld();
            spawnDimension = overworld.dimension();
            spawn = overworld.getSharedSpawnPos();
        }
        if (!level.dimension().equals(spawnDimension)) return false;
        double dx = player.getX() - (spawn.getX() + 0.5D);
        double dz = player.getZ() - (spawn.getZ() + 0.5D);
        return dx * dx + dz * dz <= RADIUS_SQ;
    }
}
