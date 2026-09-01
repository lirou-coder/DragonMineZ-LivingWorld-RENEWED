package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterArchetype;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dmzlivingworld.entity.FighterRace;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 1.5 wanted registry. Every row originates from an actual Living World fighter and
 * an actual recorded hostile act. The old v1 generated-criminal dataset is deliberately
 * not migrated: invented crimes are discarded rather than preserved as fake history.
 */
public final class WantedWorldData extends SavedData {
    private static final String DATA_NAME = "dmzlivingworld_wanted_v2";

    private final List<WantedProfile> profiles = new ArrayList<>();

    public static WantedWorldData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(WantedWorldData::load, WantedWorldData::new, DATA_NAME);
    }

    public List<WantedProfile> profiles() { return List.copyOf(profiles); }
    public List<WantedProfile> active(FactionRealm realm) {
        return profiles.stream().filter(p -> p.realm == realm && !p.eliminated).toList();
    }
    public WantedProfile bySlot(int slot) {
        for (WantedProfile p : profiles) if (p.slot == slot) return p;
        return null;
    }
    public WantedProfile byId(String id) {
        if (id == null || id.isBlank()) return null;
        for (WantedProfile p : profiles) if (id.equals(p.id)) return p;
        return null;
    }

    /** Kept for old command call sites; 1.5 has no daily wanted generation. */
    public void tick(ServerLevel ignored) {}

    /** Removes old rows that predate the rule that World Menaces are never ordinary wanted criminals. */
    public int purgeWorldMenaces() {
        int before = profiles.size();
        profiles.removeIf(p -> isWorldMenaceProfile(p.profile, p.name));
        int removed = before - profiles.size();
        if (removed > 0) setDirty();
        return removed;
    }

    private static boolean isWorldMenaceProfile(CompoundTag profile, String name) {
        if (profile != null && (profile.getBoolean(WorldMenaceManager.HEROBRINE_TAG)
                || profile.getBoolean(RedRibbonExperimentManager.TAG))) return true;
        return "Herobrine".equals(name) || "Red Ribbon Experiment X-7".equals(name);
    }

    public WantedProfile registerOrUpdate(AmbientFighterEntity fighter, int severity, String crime) {
        if (fighter == null || !(fighter.level() instanceof ServerLevel level)) return null;
        String id = fighter.getWantedId();
        if (id == null || id.isBlank()) return null;
        WantedProfile p = byId(id);
        UUID social = socialIdentity(fighter.writeMemoryProfile());
        if (p == null && social != null) {
            for (WantedProfile row : profiles) {
                if (social.equals(socialIdentity(row.profile))) {
                    p = row;
                    // Repair R26 saves where the same person acquired a second wanted id after being remembered.
                    fighter.markWanted(row.id, severity, crime);
                    break;
                }
            }
        }
        if (p == null) {
            int slot = profiles.stream().mapToInt(row -> row.slot).max().orElse(0) + 1;
            p = new WantedProfile(slot, id);
            profiles.add(p);
        }
        p.name = fighter.getFighterName();
        p.realm = LivingWorldDimensions.realm(level);
        p.race = fighter.getRace();
        p.female = fighter.isFemale();
        p.personality = fighter.getPersonality();
        p.archetype = fighter.getArchetype();
        p.severity = Math.max(p.severity, Math.max(1, Math.min(5, severity)));
        p.crime = limit(crime, 120);
        p.anchorX = fighter.blockPosition().getX();
        p.anchorZ = fighter.blockPosition().getZ();
        p.lastX = fighter.blockPosition().getX();
        p.lastY = fighter.blockPosition().getY();
        p.lastZ = fighter.blockPosition().getZ();
        p.spawned = true;
        p.eliminated = false;
        p.profile = fighter.writeMemoryProfile();
        setDirty();
        return p;
    }

    public void updateFromFighter(WantedProfile p, AmbientFighterEntity fighter) {
        if (p == null || fighter == null) return;
        BlockPos pos = fighter.blockPosition();
        p.lastX = pos.getX(); p.lastY = pos.getY(); p.lastZ = pos.getZ();
        p.spawned = true;
        p.profile = fighter.writeMemoryProfile();
        p.severity = Math.max(p.severity, fighter.getWantedLevel());
        if (!fighter.getWantedCrime().isBlank()) p.crime = limit(fighter.getWantedCrime(), 120);
        setDirty();
    }

    public void markSpawned(WantedProfile p, BlockPos pos) {
        if (p == null || pos == null) return;
        p.spawned = true;
        p.lastX = pos.getX(); p.lastY = pos.getY(); p.lastZ = pos.getZ();
        setDirty();
    }

    public void updatePosition(WantedProfile p, BlockPos pos) {
        if (p == null || pos == null) return;
        p.lastX = pos.getX(); p.lastY = pos.getY(); p.lastZ = pos.getZ();
        setDirty();
    }

    public void markEliminated(WantedProfile p, long gameTime) {
        if (p == null) return;
        p.eliminated = true;
        p.spawned = false;
        p.eliminatedAt = gameTime;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (WantedProfile profile : profiles) list.add(profile.save());
        tag.put("Wanted", list);
        return tag;
    }

    private static WantedWorldData load(CompoundTag tag) {
        WantedWorldData data = new WantedWorldData();
        ListTag saved = tag.getList("Wanted", Tag.TAG_COMPOUND);
        Map<UUID, WantedProfile> byIdentity = new HashMap<>();
        for (int i = 0; i < saved.size(); i++) {
            WantedProfile p = WantedProfile.load(saved.getCompound(i));
            if (p.id == null || p.id.isBlank()) continue;
            UUID social = socialIdentity(p.profile);
            if (social == null) { data.profiles.add(p); continue; }
            WantedProfile existing = byIdentity.get(social);
            if (existing == null) {
                byIdentity.put(social, p); data.profiles.add(p);
            } else {
                mergeDuplicate(existing, p);
            }
        }
        return data;
    }


    private static UUID socialIdentity(CompoundTag profile) {
        if (profile == null || !profile.contains("Legacy", Tag.TAG_COMPOUND)) return null;
        CompoundTag legacy = profile.getCompound("Legacy");
        return legacy.hasUUID("NpcSocialIdentity") ? legacy.getUUID("NpcSocialIdentity") : null;
    }

    private static int crimeScore(CompoundTag profile) {
        if (profile == null || !profile.contains("Legacy", Tag.TAG_COMPOUND)) return 0;
        CompoundTag l = profile.getCompound("Legacy");
        return Math.max(0, l.getInt("UnlawfulCivilianKills")) * 4
                + Math.max(0, l.getInt("UnlawfulAllyKills")) * 3
                + Math.max(0, l.getInt("UnlawfulPlayerKills")) * 2
                + Math.max(0, l.getInt("UnlawfulNeutralKills"));
    }

    private static void mergeDuplicate(WantedProfile keep, WantedProfile duplicate) {
        // R26 could write two rows for one person when their memory identity became available after the first crime.
        // Keep one stable slot and retain whichever snapshot contains the richer factual crime history.
        WantedProfile newer = crimeScore(duplicate.profile) > crimeScore(keep.profile) ? duplicate : keep;
        keep.name = newer.name; keep.realm = newer.realm; keep.race = newer.race; keep.female = newer.female;
        keep.personality = newer.personality; keep.archetype = newer.archetype;
        keep.severity = Math.max(keep.severity, duplicate.severity);
        keep.crime = newer.crime; keep.anchorX = newer.anchorX; keep.anchorZ = newer.anchorZ;
        keep.lastX = newer.lastX; keep.lastY = newer.lastY; keep.lastZ = newer.lastZ;
        keep.spawned = keep.spawned || duplicate.spawned;
        keep.eliminated = keep.eliminated && duplicate.eliminated;
        keep.eliminatedAt = Math.max(keep.eliminatedAt, duplicate.eliminatedAt);
        keep.profile = newer.profile.copy();
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    public static final class WantedProfile {
        public final int slot;
        public final String id;
        public String name = "Unknown Fighter";
        public FactionRealm realm = FactionRealm.EARTH;
        public FighterRace race = FighterRace.HUMAN;
        public boolean female;
        public FighterPersonality personality = FighterPersonality.CAUTIOUS;
        public FighterArchetype archetype = FighterArchetype.MARTIAL_ARTIST;
        public int severity = 1;
        public String crime = "recorded hostile acts";
        public int anchorX;
        public int anchorZ;
        public boolean spawned;
        public boolean eliminated;
        public long eliminatedAt;
        public int lastX;
        public int lastY;
        public int lastZ;
        public CompoundTag profile = new CompoundTag();

        WantedProfile(int slot, String id) {
            this.slot = slot;
            this.id = id == null ? "" : id;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("Slot", slot); t.putString("Id", id); t.putString("Name", name); t.putInt("Realm", realm.id());
            t.putInt("Race", race.id()); t.putBoolean("Female", female); t.putInt("Personality", personality.id());
            t.putInt("Archetype", archetype.id()); t.putInt("Severity", severity); t.putString("Crime", crime);
            t.putInt("AnchorX", anchorX); t.putInt("AnchorZ", anchorZ); t.putBoolean("Spawned", spawned);
            t.putBoolean("Eliminated", eliminated); t.putLong("EliminatedAt", eliminatedAt);
            t.putInt("LastX", lastX); t.putInt("LastY", lastY); t.putInt("LastZ", lastZ);
            t.put("Profile", profile.copy());
            return t;
        }

        static WantedProfile load(CompoundTag t) {
            WantedProfile p = new WantedProfile(t.getInt("Slot"), t.getString("Id"));
            p.name = limit(t.getString("Name"), 64);
            p.realm = FactionRealm.byId(t.getInt("Realm"));
            p.race = FighterRace.byId(t.getInt("Race"));
            p.female = t.getBoolean("Female");
            p.personality = FighterPersonality.byId(t.getInt("Personality"));
            p.archetype = FighterArchetype.byId(t.getInt("Archetype"));
            p.severity = Math.max(1, Math.min(5, t.getInt("Severity")));
            p.crime = limit(t.getString("Crime"), 120);
            p.anchorX = t.getInt("AnchorX"); p.anchorZ = t.getInt("AnchorZ");
            p.spawned = t.getBoolean("Spawned"); p.eliminated = t.getBoolean("Eliminated"); p.eliminatedAt = t.getLong("EliminatedAt");
            p.lastX = t.getInt("LastX"); p.lastY = t.getInt("LastY"); p.lastZ = t.getInt("LastZ");
            p.profile = t.contains("Profile", Tag.TAG_COMPOUND) ? t.getCompound("Profile").copy() : new CompoundTag();
            return p;
        }
    }
}
