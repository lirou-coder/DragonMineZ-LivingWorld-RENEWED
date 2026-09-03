package com.dmzlivingworld.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Persistent numeric world era and the completed saga currently supplying its power anchor. */
public final class WorldEraData extends SavedData {
    private static final String DATA_NAME = "dmzlivingworld_world_era";
    private int eraNumber;
    private String anchorSagaId = "";
    private double anchorReference;
    private long lastAdvanceTick = Long.MIN_VALUE;
    private final List<String> milestones = new ArrayList<>();

    public static WorldEraData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(WorldEraData::load, WorldEraData::new, DATA_NAME);
    }

    public int eraNumber() { return eraNumber; }
    public String anchorSagaId() { return anchorSagaId; }
    public long lastAdvanceTick() { return lastAdvanceTick; }
    public String displayName() { return "Era " + eraNumber; }
    public List<String> milestones() { return Collections.unmodifiableList(milestones); }

    public boolean advanceTo(int targetNumber, String sagaId, String sagaName, double reference, long gameTime) {
        boolean sameCompletionMoment = gameTime == lastAdvanceTick;
        if ((!sameCompletionMoment && targetNumber <= eraNumber) || sagaId == null || sagaId.isBlank()) return false;
        if (sameCompletionMoment && reference <= anchorReference) {
            if (targetNumber > eraNumber) { eraNumber = targetNumber; setDirty(); }
            return false;
        }
        int previous = eraNumber;
        eraNumber = Math.max(eraNumber, targetNumber);
        anchorSagaId = sagaId;
        anchorReference = Math.max(0.0D, reference);
        lastAdvanceTick = gameTime;
        addMilestone("World era advanced: " + previous + " -> " + targetNumber
                + (sagaName == null || sagaName.isBlank() ? "" : " (" + sagaName + ")"));
        setDirty();
        return true;
    }

    /** Rebuild after DMZ mutates/reset quest state; unlike advancement this may legitimately regress. */
    public void redefine(int number, String sagaId, double reference) {
        int safeNumber = Math.max(0, number);
        String safeSaga = sagaId == null ? "" : sagaId;
        if (eraNumber == safeNumber && anchorSagaId.equals(safeSaga)
                && Double.compare(anchorReference, Math.max(0.0D, reference)) == 0) return;
        eraNumber = safeNumber;
        anchorSagaId = safeSaga;
        anchorReference = Math.max(0.0D, reference);
        setDirty();
    }

    private void addMilestone(String entry) {
        if (entry == null || entry.isBlank()) return;
        if (milestones.size() >= 64) milestones.remove(0);
        milestones.add(entry);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("EraNumber", eraNumber);
        tag.putString("AnchorSaga", anchorSagaId);
        tag.putDouble("AnchorReference", anchorReference);
        tag.putLong("LastAdvanceTick", lastAdvanceTick);
        ListTag list = new ListTag();
        for (String milestone : milestones) list.add(StringTag.valueOf(milestone));
        tag.put("Milestones", list);
        return tag;
    }

    public static WorldEraData load(CompoundTag tag) {
        WorldEraData data = new WorldEraData();
        // Legacy named-era data is intentionally re-evaluated from real player completion on login.
        data.eraNumber = tag.contains("EraNumber", Tag.TAG_INT) ? tag.getInt("EraNumber") : 0;
        data.anchorSagaId = tag.getString("AnchorSaga");
        data.anchorReference = tag.getDouble("AnchorReference");
        data.lastAdvanceTick = tag.contains("LastAdvanceTick", Tag.TAG_LONG) ? tag.getLong("LastAdvanceTick") : Long.MIN_VALUE;
        if (data.anchorSagaId.isBlank()) data.eraNumber = 0;
        if (tag.contains("Milestones", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Milestones", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) data.milestones.add(list.getString(i));
        }
        return data;
    }
}
