package com.dmzlivingworld.world;

import com.dragonminez.common.init.block.entity.GravityDeviceBlockEntity;
import com.dragonminez.server.util.GravityLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Keeps Living World encounter materialization out of Dragon Mine Z gravity rooms, whether the machine is currently running or not.
 * Gravity chambers are deliberate training/private interiors, not valid ambient encounter ground.
 */
public final class GravityChamberSafety {
    private static final int CHUNK_SCAN_RADIUS = 2; // DMZ rooms are capped at 25 blocks per axis.

    private GravityChamberSafety() {}

    public static boolean isPlayerInsideActiveChamber(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return false;
        try {
            if (GravityLogic.getMachineGravity(player) > 1.0001D) return true;
        } catch (Throwable ignored) {
            // Fall through to the room-bounds check for compatible DMZ builds.
        }
        return isInsideActiveChamber(level, player.blockPosition());
    }

    public static boolean isInsideActiveChamber(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        int centerChunkX = pos.getX() >> 4;
        int centerChunkZ = pos.getZ() >> 4;
        for (int cx = centerChunkX - CHUNK_SCAN_RADIUS; cx <= centerChunkX + CHUNK_SCAN_RADIUS; cx++) {
            for (int cz = centerChunkZ - CHUNK_SCAN_RADIUS; cz <= centerChunkZ + CHUNK_SCAN_RADIUS; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof GravityDeviceBlockEntity chamber)) continue;
                    if (!chamber.isRoomValid()) continue;
                    BlockPos min = chamber.getRoomMin();
                    BlockPos max = chamber.getRoomMax();
                    if (min == null || max == null) continue;
                    if (pos.getX() >= min.getX() && pos.getX() <= max.getX()
                            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ()) return true;
                }
            }
        }
        return false;
    }
}
