package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Compact world archive for notable fallen Living World fighters.
 * This is SavedData only: removing Living World leaves an inert data file and
 * does not strand any registry objects in the save.
 */
public final class FighterLegacyWorldData extends SavedData {
    private static final String DATA_NAME = "dmzlivingworld_fighter_legacy_v1";
    private static final int MAX_FALLEN = 64;
    private static final int MAX_DEAD_RECORD_IDS = 2048;

    private final List<CompoundTag> fallen;
    private final List<java.util.UUID> deadRecordIds;

    private FighterLegacyWorldData(List<CompoundTag> fallen, List<java.util.UUID> deadRecordIds) {
        this.fallen = fallen;
        this.deadRecordIds = deadRecordIds;
    }

    public static FighterLegacyWorldData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                FighterLegacyWorldData::load,
                () -> new FighterLegacyWorldData(new ArrayList<>(), new ArrayList<>()),
                DATA_NAME);
    }

    private static FighterLegacyWorldData load(CompoundTag tag) {
        List<CompoundTag> out = new ArrayList<>();
        ListTag list = tag.getList("Fallen", Tag.TAG_COMPOUND);
        int start = Math.max(0, list.size() - MAX_FALLEN);
        for (int i = start; i < list.size(); i++) out.add(sanitizeRecord(list.getCompound(i)));
        List<java.util.UUID> dead = new ArrayList<>();
        ListTag deadList = tag.getList("DeadRecords", Tag.TAG_COMPOUND);
        int deadStart = Math.max(0, deadList.size() - MAX_DEAD_RECORD_IDS);
        for (int i = deadStart; i < deadList.size(); i++) {
            CompoundTag entry = deadList.getCompound(i);
            if (entry.hasUUID("Id")) {
                java.util.UUID id = entry.getUUID("Id");
                if (!dead.contains(id)) dead.add(id);
            }
        }
        return new FighterLegacyWorldData(out, dead);
    }

    private static CompoundTag sanitizeRecord(CompoundTag raw) {
        CompoundTag r = new CompoundTag();
        r.putString("Name", limit(raw.getString("Name"), 64));
        r.putString("Race", limit(raw.getString("Race"), 32));
        r.putInt("BattlePower", Math.max(0, raw.getInt("BattlePower")));
        r.putString("Title", limit(raw.getString("Title"), 48));
        r.putString("Summary", limit(raw.getString("Summary"), 192));
        r.putString("Equipment", limit(raw.getString("Equipment"), 192));
        r.putString("Killer", limit(raw.getString("Killer"), 64));
        r.putLong("FallenAt", Math.max(0L, raw.getLong("FallenAt")));
        if (raw.hasUUID("RecordId")) r.putUUID("RecordId", raw.getUUID("RecordId"));
        if (raw.contains("Profile", Tag.TAG_COMPOUND)) r.put("Profile", raw.getCompound("Profile").copy());
        return r;
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (CompoundTag record : fallen) list.add(record.copy());
        tag.put("Fallen", list);
        ListTag dead = new ListTag();
        for (java.util.UUID id : deadRecordIds) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            dead.add(entry);
        }
        tag.put("DeadRecords", dead);
        return tag;
    }

    public void markDeadRecord(java.util.UUID recordId) {
        if (recordId == null || deadRecordIds.contains(recordId)) return;
        deadRecordIds.add(recordId);
        while (deadRecordIds.size() > MAX_DEAD_RECORD_IDS) deadRecordIds.remove(0);
        setDirty();
    }

    public boolean isDeadRecord(java.util.UUID recordId) {
        return recordId != null && deadRecordIds.contains(recordId);
    }

    /** Removes the death tombstone/archive when a Dragon Ball wish restores this person. */
    public void reviveRecord(java.util.UUID recordId) {
        if (recordId == null) return;
        boolean changed = deadRecordIds.removeIf(recordId::equals);
        changed |= fallen.removeIf(record -> record.hasUUID("RecordId") && recordId.equals(record.getUUID("RecordId")));
        if (changed) setDirty();
    }

    public void archive(AmbientFighterEntity fighter, String killerName, long gameTime) {
        if (fighter == null || WorldMenaceManager.isWorldMenace(fighter) || !FighterLegacyManager.isNotable(fighter)) return;
        CompoundTag record = new CompoundTag();
        record.putString("Name", fighter.getFighterName());
        record.putString("Race", fighter.getRace().displayName());
        record.putInt("BattlePower", fighter.getBattlePower());
        record.putString("Title", fighter.getLegacyTitle());
        record.putString("Summary", fighter.getLegacySummary());
        record.putString("Equipment", FighterArsenalManager.summary(fighter));
        record.putString("Killer", killerName == null ? "" : killerName);
        record.putLong("FallenAt", gameTime);
        UUID recordId = fighter.getMemoryRecordId() != null ? fighter.getMemoryRecordId() : fighter.getUUID();
        record.putUUID("RecordId", recordId);
        record.put("Profile", fighter.writeMemoryProfile().copy());
        fallen.add(record);
        while (fallen.size() > MAX_FALLEN) fallen.remove(0);
        setDirty();
    }

    public List<String> recentLines(int limit) { return recentLinesSince(limit, 0L); }

    public List<String> recentLinesSince(int limit, long since) {
        List<String> out = new ArrayList<>();
        for (FallenEntry entry : recentEntriesSince(limit, since)) out.add(entry.line());
        return out;
    }

    /** Structured fallen-person view used by the People GUI. Old saves without portraits remain readable. */
    public List<FallenEntry> recentEntriesSince(int limit, long since) {
        List<FallenEntry> out = new ArrayList<>();
        for (int i = fallen.size() - 1; i >= 0 && out.size() < Math.max(1, limit); i--) {
            CompoundTag r = fallen.get(i);
            if (isWorldMenaceRecord(r) || r.getLong("FallenAt") <= since) continue;
            String title = r.getString("Title");
            String line = (title.isBlank() ? "" : title + " ") + r.getString("Name")
                    + " — " + r.getString("Race") + " • Last known PL " + r.getInt("BattlePower");
            String killer = r.getString("Killer");
            if (!killer.isBlank()) line += " • fell to " + killer;
            UUID recordId = r.hasUUID("RecordId") ? r.getUUID("RecordId") : null;
            CompoundTag profile = r.contains("Profile", Tag.TAG_COMPOUND) ? r.getCompound("Profile").copy() : new CompoundTag();
            out.add(new FallenEntry(recordId, line, profile));
        }
        return out;
    }

    /** Lookup used by the clickable Recently Fallen dossier. The archive remains read-only. */
    public FallenEntry byRecordId(UUID wanted) {
        if (wanted == null) return null;
        for (int i = fallen.size() - 1; i >= 0; i--) {
            CompoundTag r = fallen.get(i);
            if (isWorldMenaceRecord(r)) continue;
            if (!r.hasUUID("RecordId") || !wanted.equals(r.getUUID("RecordId"))) continue;
            String title = r.getString("Title");
            String line = (title.isBlank() ? "" : title + " ") + r.getString("Name")
                    + " — " + r.getString("Race") + " • Last known PL " + r.getInt("BattlePower");
            String killer = r.getString("Killer");
            if (!killer.isBlank()) line += " • fell to " + killer;
            CompoundTag profile = r.contains("Profile", Tag.TAG_COMPOUND) ? r.getCompound("Profile").copy() : new CompoundTag();
            return new FallenEntry(wanted, line, profile);
        }
        return null;
    }

    private static boolean isWorldMenaceRecord(CompoundTag record) {
        if (record == null) return false;
        String name = record.getString("Name");
        if ("Herobrine".equalsIgnoreCase(name) || "X-7".equalsIgnoreCase(name)) return true;
        // Historical builds archive the experiment under its full canonical name. Accept the
        // suffix too so existing saves immediately stop exposing old X-7 entries in Passed Away.
        return name != null && name.toUpperCase(java.util.Locale.ROOT).endsWith(" X-7");
    }

    public record FallenEntry(UUID recordId, String line, CompoundTag appearance) {}

    public int count() {
        int visible = 0;
        for (CompoundTag record : fallen) if (!isWorldMenaceRecord(record)) visible++;
        return visible;
    }
}
