package com.dmzlivingworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Persistent provenance used by Ki Security's "Player Blocks" mode.
 *
 * 1.8.5 stores positions in chunk buckets instead of one world-sized set. Lookups stay O(1),
 * saves remain compact, and future cleanup can touch one loaded chunk without scanning every
 * build in the dimension. Older v1 flat Position arrays migrate automatically on load.
 */
public final class PlayerPlacedBlockData extends SavedData {
    private static final String DATA_NAME = "dmzlivingworld_player_placed_blocks_v1";
    private final Map<Long, Set<Long>> byChunk = new HashMap<>();

    public static PlayerPlacedBlockData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PlayerPlacedBlockData::load,
                PlayerPlacedBlockData::new, DATA_NAME);
    }

    public void remember(BlockPos pos) {
        if (pos == null) return;
        long chunk = chunkKey(pos);
        if (byChunk.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(pos.asLong())) setDirty();
    }

    public void forget(BlockPos pos) {
        if (pos == null) return;
        long chunk = chunkKey(pos);
        Set<Long> positions = byChunk.get(chunk);
        if (positions == null || !positions.remove(pos.asLong())) return;
        if (positions.isEmpty()) byChunk.remove(chunk);
        setDirty();
    }

    public boolean isPlayerPlaced(BlockPos pos) {
        if (pos == null) return false;
        Set<Long> positions = byChunk.get(chunkKey(pos));
        return positions != null && positions.contains(pos.asLong());
    }

    /** Lazily clears stale air markers encountered by Ki checks. */
    public boolean isPlayerPlaced(ServerLevel level, BlockPos pos) {
        if (!isPlayerPlaced(pos)) return false;
        if (level != null && level.getBlockState(pos).isAir()) {
            forget(pos);
            return false;
        }
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag chunks = new ListTag();
        for (Map.Entry<Long, Set<Long>> entry : byChunk.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag row = new CompoundTag();
            row.putLong("Chunk", entry.getKey());
            long[] packed = new long[entry.getValue().size()];
            int index = 0;
            for (long value : entry.getValue()) packed[index++] = value;
            row.putLongArray("Positions", packed);
            chunks.add(row);
        }
        tag.put("Chunks", chunks);
        return tag;
    }

    private static PlayerPlacedBlockData load(CompoundTag tag) {
        PlayerPlacedBlockData data = new PlayerPlacedBlockData();
        if (tag.contains("Chunks", Tag.TAG_LIST)) {
            ListTag chunks = tag.getList("Chunks", Tag.TAG_COMPOUND);
            for (int i = 0; i < chunks.size(); i++) {
                CompoundTag row = chunks.getCompound(i);
                long chunk = row.getLong("Chunk");
                Set<Long> positions = data.byChunk.computeIfAbsent(chunk, ignored -> new HashSet<>());
                for (long value : row.getLongArray("Positions")) positions.add(value);
                if (positions.isEmpty()) data.byChunk.remove(chunk);
            }
            return data;
        }

        // Seamless migration from 1.8.3/1.8.4's flat saved array.
        for (long value : tag.getLongArray("Positions")) {
            BlockPos pos = BlockPos.of(value);
            data.byChunk.computeIfAbsent(chunkKey(pos), ignored -> new HashSet<>()).add(value);
        }
        return data;
    }

    private static long chunkKey(BlockPos pos) {
        return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
