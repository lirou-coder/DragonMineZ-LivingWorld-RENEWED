package com.dmzlivingworld.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/** Slow world-level incident schedule/history. Incidents themselves only use people who already exist. */
public final class WorldIncidentData extends SavedData {
    private static final String DATA_NAME = "dmzlivingworld_incidents_v1";
    private static final int MAX_HISTORY = 32;
    private long nextIncidentAt;
    private final List<String> history = new ArrayList<>();

    public static WorldIncidentData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(WorldIncidentData::load, WorldIncidentData::new, DATA_NAME);
    }

    public long nextIncidentAt() { return nextIncidentAt; }

    public void ensureSchedule(long now) {
        if (nextIncidentAt > 0L) return;
        nextIncidentAt = now + 36_000L;
        setDirty();
    }

    public void scheduleNext(long now, long jitter) {
        nextIncidentAt = now + 36_000L + Math.max(0L, Math.min(60_000L, jitter)); // ~1.5-4 MC days
        setDirty();
    }

    public void record(String line) {
        if (line == null || line.isBlank()) return;
        history.add(line.length() > 180 ? line.substring(0, 180) : line);
        while (history.size() > MAX_HISTORY) history.remove(0);
        setDirty();
    }

    public List<String> recent(int limit) {
        List<String> out = new ArrayList<>();
        int start = Math.max(0, history.size() - Math.max(1, limit));
        for (int i = history.size() - 1; i >= start; i--) out.add(history.get(i));
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("NextIncidentAt", nextIncidentAt);
        ListTag list = new ListTag();
        for (String line : history) {
            CompoundTag row = new CompoundTag();
            row.putString("Line", line);
            list.add(row);
        }
        tag.put("History", list);
        return tag;
    }

    public static WorldIncidentData load(CompoundTag tag) {
        WorldIncidentData data = new WorldIncidentData();
        data.nextIncidentAt = Math.max(0L, tag.getLong("NextIncidentAt"));
        ListTag list = tag.getList("History", Tag.TAG_COMPOUND);
        int start = Math.max(0, list.size() - MAX_HISTORY);
        for (int i = start; i < list.size(); i++) data.history.add(list.getCompound(i).getString("Line"));
        return data;
    }
}
