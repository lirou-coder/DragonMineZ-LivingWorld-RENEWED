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

/** Persistent registry for antagonist organizations and their actual recurring core members. */
public final class AntagonistWorldData extends SavedData {
    private static final String DATA_NAME = "dmzlivingworld_antagonists_v1";
    private final CompoundTag factions = new CompoundTag();

    public static AntagonistWorldData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(AntagonistWorldData::load, AntagonistWorldData::new, DATA_NAME);
    }

    public boolean isAntagonistFaction(String factionId) {
        return factionId != null && factions.contains(factionId, Tag.TAG_COMPOUND)
                && factions.getCompound(factionId).getBoolean("Recognized");
    }

    public long recognizedAt(String factionId) {
        return isAntagonistFaction(factionId) ? factions.getCompound(factionId).getLong("RecognizedAt") : 0L;
    }

    public void recognize(WorldFaction faction, long gameTime, String leaderName, int victories, String reason) {
        if (faction == null || isAntagonistFaction(faction.id())) return;
        CompoundTag state = state(faction.id());
        state.putBoolean("Recognized", true);
        state.putLong("RecognizedAt", gameTime);
        state.putString("LeaderName", leaderName == null ? "" : leaderName);
        state.putInt("VictoriesAtRecognition", Math.max(0, victories));
        state.putString("Reason", reason == null ? "" : reason);
        state.put("Core", new ListTag());
        factions.put(faction.id(), state);
        setDirty();
    }

    public String reason(String factionId) {
        return isAntagonistFaction(factionId) ? factions.getCompound(factionId).getString("Reason") : "";
    }

    public int coreCount(String factionId) {
        return core(factionId, false).size();
    }

    public List<CoreMember> coreMembers(String factionId) {
        List<CoreMember> out = new ArrayList<>();
        ListTag list = core(factionId, false);
        for (int i = 0; i < list.size(); i++) out.add(CoreMember.load(list.getCompound(i)));
        return out;
    }

    public boolean registerCore(WorldFaction faction, AmbientFighterEntity fighter, long gameTime) {
        if (faction == null || fighter == null || !isAntagonistFaction(faction.id())) return false;
        CompoundTag factionState = state(faction.id());
        ListTag list = factionState.getList("Core", Tag.TAG_COMPOUND);
        UUID coreId = ensureCoreId(fighter);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            if (row.hasUUID("CoreId") && row.getUUID("CoreId").equals(coreId)) {
                updateRow(row, fighter, gameTime);
                list.set(i, row);
                factionState.put("Core", list);
                factions.put(faction.id(), factionState);
                setDirty();
                return false;
            }
        }
        if (activeCoreCount(list) >= 7) return false;
        CompoundTag row = new CompoundTag();
        row.putUUID("CoreId", coreId);
        row.putLong("JoinedAt", gameTime);
        updateRow(row, fighter, gameTime);
        list.add(row);
        factionState.put("Core", list);
        factions.put(faction.id(), factionState);
        setDirty();
        return true;
    }

    public void updateCore(AmbientFighterEntity fighter, long gameTime) {
        if (fighter == null || !fighter.isFactionMember()) return;
        String factionId = fighter.getFactionId();
        if (!isAntagonistFaction(factionId) || !fighter.getLegacyData().hasUUID("AntagonistCoreId")) return;
        UUID coreId = fighter.getLegacyData().getUUID("AntagonistCoreId");
        CompoundTag factionState = state(factionId);
        ListTag list = factionState.getList("Core", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            if (row.hasUUID("CoreId") && row.getUUID("CoreId").equals(coreId)) {
                updateRow(row, fighter, gameTime);
                list.set(i, row);
                factionState.put("Core", list);
                factions.put(factionId, factionState);
                setDirty();
                return;
            }
        }
    }

    public void markCoreFallen(AmbientFighterEntity fighter, long gameTime) {
        if (fighter == null || !fighter.isFactionMember() || !fighter.getLegacyData().hasUUID("AntagonistCoreId")) return;
        String factionId = fighter.getFactionId();
        if (!isAntagonistFaction(factionId)) return;
        UUID coreId = fighter.getLegacyData().getUUID("AntagonistCoreId");
        CompoundTag factionState = state(factionId);
        ListTag list = factionState.getList("Core", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            if (!row.hasUUID("CoreId") || !row.getUUID("CoreId").equals(coreId)) continue;
            row.putBoolean("Fallen", true);
            row.putLong("FallenAt", gameTime);
            row.put("Profile", fighter.writeMemoryProfile());
            list.set(i, row);
            factionState.put("Core", list);
            factions.put(factionId, factionState);
            setDirty();
            return;
        }
    }

    private static UUID ensureCoreId(AmbientFighterEntity fighter) {
        CompoundTag legacy = fighter.getLegacyData();
        if (!legacy.hasUUID("AntagonistCoreId")) legacy.putUUID("AntagonistCoreId", UUID.randomUUID());
        return legacy.getUUID("AntagonistCoreId");
    }

    private static void updateRow(CompoundTag row, AmbientFighterEntity fighter, long gameTime) {
        row.putString("Name", fighter.getFighterName());
        row.putBoolean("Fallen", false);
        row.putLong("LastSeen", gameTime);
        row.put("Profile", fighter.writeMemoryProfile());
        row.putInt("Role", fighter.getFactionRole().id());
        row.putInt("Rank", fighter.getRank().id());
        row.putInt("Wins", Math.max(0, fighter.getLegacyData().getInt("Wins")));
        row.putInt("Kills", Math.max(0, fighter.getLegacyData().getInt("Kills")));
    }

    private static int activeCoreCount(ListTag list) {
        int count = 0;
        for (int i = 0; i < list.size(); i++) if (!list.getCompound(i).getBoolean("Fallen")) count++;
        return count;
    }

    private CompoundTag state(String factionId) {
        return factions.contains(factionId, Tag.TAG_COMPOUND) ? factions.getCompound(factionId).copy() : new CompoundTag();
    }

    private ListTag core(String factionId, boolean copy) {
        if (!isAntagonistFaction(factionId)) return new ListTag();
        ListTag list = factions.getCompound(factionId).getList("Core", Tag.TAG_COMPOUND);
        return copy ? list.copy() : list;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("Factions", factions.copy());
        return tag;
    }

    private static AntagonistWorldData load(CompoundTag tag) {
        AntagonistWorldData data = new AntagonistWorldData();
        if (tag.contains("Factions", Tag.TAG_COMPOUND)) {
            CompoundTag loaded = tag.getCompound("Factions");
            for (String key : loaded.getAllKeys()) {
                if (loaded.contains(key, Tag.TAG_COMPOUND)) data.factions.put(key, loaded.getCompound(key).copy());
            }
        }
        return data;
    }

    public record CoreMember(UUID coreId, String name, boolean fallen, long joinedAt, long fallenAt,
                             int roleId, int rankId, int wins, int kills, CompoundTag profile) {
        static CoreMember load(CompoundTag row) {
            UUID id = row.hasUUID("CoreId") ? row.getUUID("CoreId") : UUID.randomUUID();
            return new CoreMember(id, row.getString("Name"), row.getBoolean("Fallen"), row.getLong("JoinedAt"),
                    row.getLong("FallenAt"), row.getInt("Role"), row.getInt("Rank"), row.getInt("Wins"), row.getInt("Kills"),
                    row.contains("Profile", Tag.TAG_COMPOUND) ? row.getCompound("Profile").copy() : new CompoundTag());
        }
    }
}
