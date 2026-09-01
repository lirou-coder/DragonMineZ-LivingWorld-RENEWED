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

/** One persistent world-level history shared by all dimensions. */
public final class WorldEraData extends SavedData {
    private static final String DATA_NAME = "dmzlivingworld_world_era";

    private WorldEra era = WorldEra.EARLY_EARTH;
    private final List<String> milestones = new ArrayList<>();

    public static WorldEraData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(WorldEraData::load, WorldEraData::new, DATA_NAME);
    }

    public WorldEra era() { return era; }
    public List<String> milestones() { return Collections.unmodifiableList(milestones); }

    public boolean advanceTo(WorldEra target, String reason) {
        if (target == null || target.id() <= era.id()) return false;
        WorldEra previous = era;
        era = target;
        addMilestone("World era advanced: " + previous.displayName() + " -> " + target.displayName()
                + (reason == null || reason.isBlank() ? "" : " (" + reason + ")"));
        setDirty();
        return true;
    }

    public void recordSagaCompletion(String sagaName) {
        if (sagaName == null || sagaName.isBlank()) return;
        String entry = "Saga completed: " + sagaName;
        if (milestones.contains(entry)) return;
        addMilestone(entry);
        setDirty();
    }

    private void addMilestone(String entry) {
        if (entry == null || entry.isBlank()) return;
        if (milestones.size() >= 64) milestones.remove(0);
        milestones.add(entry);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Era", era.id());
        ListTag list = new ListTag();
        for (String milestone : milestones) list.add(StringTag.valueOf(milestone));
        tag.put("Milestones", list);
        return tag;
    }

    public static WorldEraData load(CompoundTag tag) {
        WorldEraData data = new WorldEraData();
        data.era = WorldEra.byId(tag.getInt("Era"));
        if (tag.contains("Milestones", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Milestones", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) data.milestones.add(list.getString(i));
        }
        return data;
    }
}
