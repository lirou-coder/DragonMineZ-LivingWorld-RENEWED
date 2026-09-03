package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Prevents unsolicited Living World combat in the four-chunk area around a player's spawn. */
public final class PlayerSpawnCombatSafety {
    private static final String ATTACKER = "LWSpawnSafetyPlayerAttacker";
    private static final String ATTACK_UNTIL = "LWSpawnSafetyPlayerAttackUntil";
    private static final double RADIUS = 4.0D * 16.0D;

    private PlayerSpawnCombatSafety() {}

    public static void notePlayerAttack(AmbientFighterEntity fighter, ServerPlayer player) {
        if (fighter == null || player == null) return;
        fighter.getPersistentData().putUUID(ATTACKER, player.getUUID());
        fighter.getPersistentData().putLong(ATTACK_UNTIL,
                player.serverLevel().getServer().overworld().getGameTime() + 1200L);
    }

    public static boolean blocksTarget(AmbientFighterEntity fighter, ServerPlayer player) {
        if (fighter == null || player == null || !(fighter.level() instanceof ServerLevel level)) return false;
        if (fighter.isSanctionedMatchParticipant() && fighter.isSanctionedOpponent(player)) return false;
        long now = level.getServer().overworld().getGameTime();
        if (fighter.getPersistentData().hasUUID(ATTACKER)
                && fighter.getPersistentData().getUUID(ATTACKER).equals(player.getUUID())
                && fighter.getPersistentData().getLong(ATTACK_UNTIL) >= now) return false;

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
        return Math.abs(dx) <= RADIUS && Math.abs(dz) <= RADIUS;
    }
}
