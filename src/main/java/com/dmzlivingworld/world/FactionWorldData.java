package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterArchetype;
import com.dmzlivingworld.entity.FighterNames;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dmzlivingworld.entity.FighterRace;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persistent social map shared by Earth and DragonMineZ Namek. */
public final class FactionWorldData extends SavedData {
    // Keep the v1 id so 0.6.11 test worlds migrate instead of silently losing their cast.
    private static final String DATA_NAME = "dmzlivingworld_factions_v1";
    public static final String EARTH_GUARDIANS_ID = "earth_guardian_corps";
    public static final String BLACK_SUN_ID = "black_sun_syndicate";
    private static final String[] NAME_FIRST = {
            "Crimson", "Azure", "Silver", "Golden", "Iron", "Jade", "Scarlet", "Violet",
            "Obsidian", "White", "Black", "Cobalt", "Burning", "Silent", "Rising", "Falling",
            "Northern", "Southern", "Wild", "Radiant", "Storm", "Solar", "Lunar", "Thunder",
            "Ashen", "Blood", "Hollow", "Dread", "Neon", "Grim", "Ivory", "Broken", "Last", "Void"
    };
    private static final String[] NAME_SECOND = {
            "Comets", "Lotus", "Fangs", "Vipers", "Stars", "Hawks", "Jackals", "Cranes",
            "Dragons", "Meteors", "Blades", "Fists", "Wolves", "Suns", "Moons", "Sparks",
            "Guard", "Circle", "School", "Crew", "Clan", "Hands", "Wings", "Seekers",
            "Covenant", "Saints", "Disciples", "Syndicate", "Brotherhood", "Crows", "Order", "Knives"
    };
    private static final String[] NAMEK_FIRST = {
            "Emerald", "Verdant", "Ajissa", "Sacred", "Azure", "Root", "Sky", "Still", "Ancient", "Green"
    };
    private static final String[] NAMEK_SECOND = {
            "Circle", "Wardens", "Keepers", "Sages", "Hands", "Spears", "Watch", "Roots", "Monks", "Guard"
    };

    private final List<WorldFaction> factions;
    private final CompoundTag factionStates;
    private long lastOrganizationTick;
    private long lastSocietyDay;
    private long lastFactionFormationTick;

    private FactionWorldData(List<WorldFaction> factions, CompoundTag factionStates, long lastOrganizationTick, long lastSocietyDay, long lastFactionFormationTick) {
        this.factions = factions;
        this.factionStates = factionStates;
        this.lastOrganizationTick = lastOrganizationTick;
        this.lastSocietyDay = lastSocietyDay;
        this.lastFactionFormationTick = lastFactionFormationTick;
    }

    public static FactionWorldData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                tag -> load(tag, overworld),
                () -> create(overworld),
                DATA_NAME
        );
    }

    private static FactionWorldData create(ServerLevel level) {
        long worldSeed = level.getSeed();
        int count = targetFactionCount(worldSeed);
        List<WorldFaction> factions = ensureAnchorFactions(level, generateFactions(level, List.of(), count));
        FactionWorldData data = new FactionWorldData(factions, new CompoundTag(), 0L, -1L, level.getGameTime());
        data.setDirty();
        return data;
    }

    private static FactionWorldData load(CompoundTag tag, ServerLevel overworld) {
        List<WorldFaction> factions = new ArrayList<>();
        ListTag list = tag.getList("Factions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) factions.add(WorldFaction.load(list.getCompound(i)));
        CompoundTag states = tag.contains("LeaderStates", Tag.TAG_COMPOUND)
                ? tag.getCompound("LeaderStates").copy() : new CompoundTag();
        long lastTick = tag.contains("LastOrganizationTick") ? tag.getLong("LastOrganizationTick") : 0L;
        long lastSocietyDay = tag.contains("LastSocietyDay") ? tag.getLong("LastSocietyDay") : -1L;
        long lastFormation = tag.contains("LastFactionFormationTick") ? tag.getLong("LastFactionFormationTick") : 0L;

        // Preserve established organizations, then deterministically expand older worlds into
        // the denser emergent-society map introduced before this presentation pass. Existing identities never reroll.
        int target = targetFactionCount(overworld.getSeed());
        if (!factions.isEmpty()) target = Math.max(target, Math.min(36, factions.size() + 10));
        if (factions.size() < target) factions = generateFactions(overworld, factions, target);
        factions = ensureAnchorFactions(overworld, factions);
        FactionWorldData data = new FactionWorldData(factions, states, lastTick, lastSocietyDay, lastFormation);
        if (list.size() != factions.size()) data.setDirty();
        return data;
    }

    private static int targetFactionCount(long worldSeed) {
        RandomSource random = RandomSource.create(mix(worldSeed ^ 0x4465657046616374L));
        return 28 + random.nextInt(9); // 28-36 organizations total; most remain abstract until visited.
    }

    private static List<WorldFaction> generateFactions(ServerLevel level, List<WorldFaction> existing, int target) {
        long worldSeed = level.getSeed();
        BlockPos earthSpawn = level.getSharedSpawnPos();
        List<WorldFaction> out = new ArrayList<>(existing);
        Set<String> usedNames = new HashSet<>();
        for (WorldFaction faction : existing) usedNames.add(faction.name());
        long existingNamek = existing.stream().filter(f -> f.realm() == FactionRealm.NAMEK).count();
        int desiredNamek = Math.max(10, Math.round(target * 0.40F));

        for (int i = existing.size(); i < target; i++) {
            int slot = i + 1;
            long factionSeed = mix(worldSeed + 0x9E3779B97F4A7C15L * slot);
            RandomSource fr = RandomSource.create(factionSeed);

            int remaining = target - i;
            int neededNamek = Math.max(0, desiredNamek - (int)existingNamek);
            FactionRealm realm;
            if (neededNamek >= remaining) realm = FactionRealm.NAMEK;
            else realm = fr.nextFloat() < 0.40F ? FactionRealm.NAMEK : FactionRealm.EARTH;
            if (realm == FactionRealm.NAMEK) existingNamek++;

            FactionEthos ethos = rollEthos(fr, realm);
            FighterAlignment alignment = rollAlignment(fr, ethos);
            String name = uniqueName(fr, usedNames, realm);
            FactionStructure structure = FactionStructure.forEthos(ethos, realm);
            int uniform = fr.nextInt(16);
            float powerBias = 0.68F + fr.nextFloat() * 0.88F;

            double angle = fr.nextDouble() * Math.PI * 2.0D;
            int radius = realm == FactionRealm.EARTH ? 750 + fr.nextInt(6001) : 650 + fr.nextInt(4801);
            int baseX = realm == FactionRealm.EARTH ? earthSpawn.getX() : 0;
            int baseZ = realm == FactionRealm.EARTH ? earthSpawn.getZ() : 0;
            int roamX = baseX + (int)Math.round(Math.cos(angle) * radius);
            int roamZ = baseZ + (int)Math.round(Math.sin(angle) * radius);
            int roamRadius = 520 + fr.nextInt(581); // 520-1100 block social region.

            FighterRace leaderRace = rollFactionRace(fr, realm);
            boolean leaderFemale = leaderRace.gendered() && fr.nextFloat() < 0.46F;
            String leaderName = FighterNames.roll(fr, leaderRace, leaderFemale);
            FighterPersonality leaderPersonality = ethos.rollPersonality(fr, alignment);
            FighterArchetype leaderArchetype = ethos.rollArchetype(fr);

            out.add(new WorldFaction(
                    slot, "faction_" + slot, name, ethos, alignment, realm, structure,
                    uniform, powerBias, factionSeed, roamX, roamZ, roamRadius,
                    leaderName, leaderRace, leaderFemale, leaderPersonality, leaderArchetype
            ));
        }
        return out;
    }

    private static List<WorldFaction> ensureAnchorFactions(ServerLevel level, List<WorldFaction> input) {
        List<WorldFaction> out = new ArrayList<>(input);
        boolean hasGuard = out.stream().anyMatch(f -> EARTH_GUARDIANS_ID.equals(f.id()));
        boolean hasEvil = out.stream().anyMatch(f -> BLACK_SUN_ID.equals(f.id()));
        int slot = out.stream().mapToInt(WorldFaction::slot).max().orElse(0) + 1;
        BlockPos spawn = level.getSharedSpawnPos();
        if (!hasGuard) {
            long seed = mix(level.getSeed() ^ 0x4541525448475541L);
            RandomSource r = RandomSource.create(seed);
            FighterRace race = FighterRace.HUMAN;
            boolean female = r.nextFloat() < 0.46F;
            out.add(new WorldFaction(slot++, EARTH_GUARDIANS_ID, "Earth Guardian Corps",
                    FactionEthos.WANDERING_GUARD, FighterAlignment.GOOD, FactionRealm.EARTH, FactionStructure.GUARD,
                    10, 2.10F, seed, spawn.getX() + 1800, spawn.getZ() - 1200, 1250,
                    FighterNames.roll(r, race, female), race, female, FighterPersonality.HEROIC, FighterArchetype.GUARDIAN));
        }
        if (!hasEvil) {
            long seed = mix(level.getSeed() ^ 0x424C41434B53554EL);
            RandomSource r = RandomSource.create(seed);
            FighterRace race = r.nextBoolean() ? FighterRace.HUMAN : FighterRace.SAIYAN;
            boolean female = race.gendered() && r.nextFloat() < 0.46F;
            out.add(new WorldFaction(slot, BLACK_SUN_ID, "Black Sun Syndicate",
                    FactionEthos.SYNDICATE, FighterAlignment.BAD, FactionRealm.EARTH, FactionStructure.SYNDICATE,
                    13, 2.08F, seed, spawn.getX() - 2200, spawn.getZ() + 1500, 1300,
                    FighterNames.roll(r, race, female), race, female, FighterPersonality.AGGRESSIVE, FighterArchetype.KI_SPECIALIST));
        }
        return out;
    }

    public static boolean isPermanentFaction(WorldFaction faction) {
        return faction != null && (EARTH_GUARDIANS_ID.equals(faction.id()) || BLACK_SUN_ID.equals(faction.id()));
    }

    public WorldFaction earthGuardians() { return byId(EARTH_GUARDIANS_ID); }
    public WorldFaction blackSun() { return byId(BLACK_SUN_ID); }

    private static FactionEthos rollEthos(RandomSource random, FactionRealm realm) {
        if (realm == FactionRealm.NAMEK) {
            int r = random.nextInt(100);
            if (r < 24) return FactionEthos.ROOT_CIRCLE;
            if (r < 46) return FactionEthos.NAMEK_WARDENS;
            if (r < 62) return FactionEthos.ASCETIC_ORDER;
            if (r < 75) return FactionEthos.KI_ORDER;
            if (r < 86) return FactionEthos.MARTIAL_SCHOOL;
            if (r < 94) return FactionEthos.SEEKERS;
            return random.nextBoolean() ? FactionEthos.RAIDERS : FactionEthos.POWER_CULT;
        }
        FactionEthos[] earth = {
                FactionEthos.MARTIAL_SCHOOL, FactionEthos.WANDERING_GUARD, FactionEthos.KI_ORDER,
                FactionEthos.CHALLENGERS, FactionEthos.MERCENARIES, FactionEthos.STREET_GANG,
                FactionEthos.RAIDERS, FactionEthos.SEEKERS, FactionEthos.CRIME_FAMILY,
                FactionEthos.SYNDICATE, FactionEthos.POWER_CULT, FactionEthos.ASCETIC_ORDER
        };
        return earth[random.nextInt(earth.length)];
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (WorldFaction faction : factions) list.add(faction.save());
        tag.put("Factions", list);
        // Compatibility name retained, but this now stores all organization state, not just leaders.
        tag.put("LeaderStates", factionStates.copy());
        tag.putLong("LastOrganizationTick", lastOrganizationTick);
        tag.putLong("LastSocietyDay", lastSocietyDay);
        tag.putLong("LastFactionFormationTick", lastFactionFormationTick);
        return tag;
    }

    public List<WorldFaction> factions() { return List.copyOf(factions); }

    public List<WorldFaction> factions(FactionRealm realm) {
        return factions.stream().filter(f -> f.realm() == realm && !isExtinct(f)).toList();
    }

    public List<WorldFaction> activeFactions() {
        return factions.stream().filter(f -> !isExtinct(f)).toList();
    }

    public boolean isExtinct(WorldFaction faction) { return state(faction).getBoolean("Extinct"); }

    public int population(WorldFaction faction) {
        CompoundTag s = state(faction);
        return s.contains("Population") ? s.getInt("Population") : initialPopulation(faction);
    }

    public int fighterPopulation(WorldFaction faction) {
        CompoundTag s = state(faction);
        if (s.contains("Fighters")) return s.getInt("Fighters");
        return Math.max(2, Math.min(population(faction) - 2, Math.round(population(faction) * fighterShare(faction))));
    }

    public int civilianPopulation(WorldFaction faction) {
        CompoundTag s = state(faction);
        return s.contains("Civilians") ? s.getInt("Civilians") : Math.max(2, population(faction) - fighterPopulation(faction));
    }

    public int youthPopulation(WorldFaction faction) { return state(faction).getInt("Youth"); }

    /** A named person who has actually manifested as a persistent regional resident. */
    public record ResidentRecord(UUID entityId, String name, FactionRole role, com.dmzlivingworld.entity.FighterRank rank,
                                 FighterRace race, boolean nonCombatant, boolean fallen, boolean departed,
                                 long lastSeen, int x, int y, int z) {}

    public List<ResidentRecord> residents(WorldFaction faction) {
        if (faction == null) return List.of();
        ListTag list = state(faction).getList("ResidentRoster", Tag.TAG_COMPOUND);
        List<ResidentRecord> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            UUID id;
            try { id = row.hasUUID("EntityId") ? row.getUUID("EntityId") : UUID.nameUUIDFromBytes((faction.id()+":"+row.getString("Name")).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            catch (RuntimeException ex) { continue; }
            out.add(new ResidentRecord(id, row.getString("Name"), FactionRole.byId(row.getInt("Role")),
                    com.dmzlivingworld.entity.FighterRank.byId(row.getInt("Rank")), FighterRace.byId(row.getInt("Race")),
                    row.getBoolean("NonCombatant"), row.getBoolean("Fallen"), row.getBoolean("Departed"),
                    row.getLong("LastSeen"), row.getInt("X"), row.getInt("Y"), row.getInt("Z")));
        }
        return List.copyOf(out);
    }

    public void recordResident(WorldFaction faction, AmbientFighterEntity fighter) {
        if (faction == null || fighter == null || !fighter.isAlive() || !fighter.isRegionalPresence()
                || !(fighter.level() instanceof ServerLevel residentLevel)) return;
        CompoundTag s = ensureSocietyState(faction);
        ListTag list = s.getList("ResidentRoster", Tag.TAG_COMPOUND);
        int index = residentIndex(list, fighter.getUUID(), fighter.getFighterName());
        CompoundTag row = index >= 0 ? list.getCompound(index) : new CompoundTag();
        row.putUUID("EntityId", fighter.getUUID());
        row.putString("Name", fighter.getFighterName());
        row.putInt("Role", fighter.getFactionRole().id());
        row.putInt("Rank", fighter.getRank().id());
        row.putInt("Race", fighter.getRace().id());
        row.putBoolean("NonCombatant", fighter.isNonCombatant());
        row.putBoolean("Fallen", false);
        row.putBoolean("Departed", false);
        row.putLong("LastSeen", residentLevel.getServer().overworld().getGameTime());
        row.putInt("X", fighter.blockPosition().getX()); row.putInt("Y", fighter.blockPosition().getY()); row.putInt("Z", fighter.blockPosition().getZ());
        if (index >= 0) list.set(index, row); else list.add(row);
        pruneResidents(list);
        s.put("ResidentRoster", list); factionStates.put(faction.id(), s); setDirty();
    }

    public void markResidentFallen(WorldFaction faction, AmbientFighterEntity fighter, long now) {
        updateResidentState(faction, fighter, now, true, false);
    }

    public void markResidentDeparted(WorldFaction faction, AmbientFighterEntity fighter, long now) {
        updateResidentState(faction, fighter, now, false, true);
    }

    private void updateResidentState(WorldFaction faction, AmbientFighterEntity fighter, long now, boolean fallen, boolean departed) {
        if (faction == null || fighter == null) return;
        CompoundTag s = ensureSocietyState(faction);
        ListTag list = s.getList("ResidentRoster", Tag.TAG_COMPOUND);
        int index = residentIndex(list, fighter.getUUID(), fighter.getFighterName());
        if (index < 0 && !fighter.isRegionalPresence()) return;
        CompoundTag row = index >= 0 ? list.getCompound(index) : new CompoundTag();
        row.putUUID("EntityId", fighter.getUUID()); row.putString("Name", fighter.getFighterName());
        row.putInt("Role", fighter.getFactionRole().id()); row.putInt("Rank", fighter.getRank().id()); row.putInt("Race", fighter.getRace().id());
        row.putBoolean("NonCombatant", fighter.isNonCombatant()); row.putBoolean("Fallen", fallen); row.putBoolean("Departed", departed);
        row.putLong("LastSeen", now); row.putInt("X", fighter.blockPosition().getX()); row.putInt("Y", fighter.blockPosition().getY()); row.putInt("Z", fighter.blockPosition().getZ());
        if (index >= 0) list.set(index, row); else list.add(row);
        pruneResidents(list);
        s.put("ResidentRoster", list); factionStates.put(faction.id(), s); setDirty();
    }

    private static int residentIndex(ListTag list, UUID id, String name) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            if (row.hasUUID("EntityId") && row.getUUID("EntityId").equals(id)) return i;
            if (!name.isBlank() && name.equals(row.getString("Name"))) return i;
        }
        return -1;
    }

    private static void pruneResidents(ListTag list) {
        while (list.size() > 32) {
            int remove = -1; long oldest = Long.MAX_VALUE;
            for (int i = 0; i < list.size(); i++) {
                CompoundTag row = list.getCompound(i);
                if (!(row.getBoolean("Fallen") || row.getBoolean("Departed"))) continue;
                if (row.getLong("LastSeen") < oldest) { oldest = row.getLong("LastSeen"); remove = i; }
            }
            if (remove < 0) {
                for (int i = 0; i < list.size(); i++) {
                    long seen = list.getCompound(i).getLong("LastSeen");
                    if (seen < oldest) { oldest = seen; remove = i; }
                }
            }
            if (remove < 0) break;
            list.remove(remove);
        }
    }

    public int supplies(WorldFaction faction) {
        CompoundTag s = state(faction);
        return s.contains("Supplies") ? s.getInt("Supplies") : 58 + Math.floorMod((int)(faction.seed() >>> 21), 36);
    }

    public String supplyLabel(WorldFaction faction) {
        int value = supplies(faction);
        if (value < 18) return "Starving";
        if (value < 35) return "Low";
        if (value < 68) return "Stable";
        if (value < 90) return "Good";
        return "Abundant";
    }

    public List<String> history(WorldFaction faction) {
        ListTag list = state(faction).getList("History", Tag.TAG_STRING);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String entry = list.getString(i).replace(" (debug acceleration)", "");
            if (isHistoryWorthy(entry)) out.add(entry);
        }
        return out;
    }

    public void addHistory(WorldFaction faction, long gameTime, String text) {
        if (faction == null || text == null) return;
        String cleaned = cleanHistoryText(text.trim());
        if (cleaned.isBlank() || !isHistoryWorthy(cleaned)) return;
        CompoundTag s = ensureSocietyState(faction);
        ListTag list = s.getList("History", Tag.TAG_STRING);
        String entry = "Day " + Math.max(0L, gameTime / 24000L) + " — " + cleaned;
        if (!list.isEmpty() && list.getString(list.size() - 1).equals(entry)) return;
        list.add(StringTag.valueOf(entry));
        while (list.size() > 10) list.remove(0);
        s.put("History", list);
        factionStates.put(faction.id(), s);
        setDirty();
    }

    private static String cleanHistoryText(String text) {
        return text.replace(" (debug acceleration)", "")
                .replace(" [relations +2]", "")
                .replace(" [relations -2]", "")
                .replace(" [relations -3]", "")
                .replace(" [relations -1]", "");
    }

    /** The simulation can be busy without making the profile read like an accounting log. */
    private static boolean isHistoryWorthy(String text) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("foraging party returned")) return false;
        if (lower.contains("child was born")) return false;
        if (lower.contains("came of age")) return false;
        if (lower.contains("new resident joined")) return false;
        if (lower.contains("wandering fighter joined")) return false;
        if (lower.contains("completed combat training")) return false;
        if (lower.contains("joined from the unaffiliated population")) return false;
        if (lower.contains("non-lethal clash") && !lower.contains("war")) return false;
        if (lower.contains("off-screen clash")) return false;
        if (lower.startsWith("a clash with ") && lower.contains("hard victory")) return false;
        if (lower.startsWith("the clash with ") && lower.contains("cost one")) return false;
        return true;
    }

    public void addSupplies(WorldFaction faction, int delta) {
        CompoundTag s = ensureSocietyState(faction);
        s.putInt("Supplies", Math.max(0, Math.min(120, s.getInt("Supplies") + delta)));
        factionStates.put(faction.id(), s);
        setDirty();
    }

    public void addPopulation(WorldFaction faction, boolean civilian, int amount, long gameTime, String reason) {
        if (faction == null || amount <= 0) return;
        CompoundTag s = ensureSocietyState(faction);
        s.putInt("Population", s.getInt("Population") + amount);
        String key = civilian ? "Civilians" : "Fighters";
        s.putInt(key, s.getInt(key) + amount);
        factionStates.put(faction.id(), s);
        if (reason != null && !reason.isBlank()) addHistory(faction, gameTime, reason);
        setDirty();
    }

    public boolean removePopulation(WorldFaction faction, boolean civilian, int amount, long gameTime, String reason) {
        if (faction == null || amount <= 0) return false;
        CompoundTag s = ensureSocietyState(faction);
        String key = civilian ? "Civilians" : "Fighters";
        int current = s.getInt(key);
        int removable = Math.min(amount, Math.max(0, current - (civilian ? 1 : 2)));
        if (removable <= 0) return false;
        s.putInt(key, current - removable);
        s.putInt("Population", Math.max(0, s.getInt("Population") - removable));
        factionStates.put(faction.id(), s);
        if (reason != null && !reason.isBlank()) addHistory(faction, gameTime, reason);
        setDirty();
        return true;
    }

    public void transferPopulation(WorldFaction from, WorldFaction to, boolean civilian, long gameTime, String personName) {
        if (from == null || to == null || from.id().equals(to.id())) return;
        CompoundTag a = ensureSocietyState(from);
        CompoundTag b = ensureSocietyState(to);
        String key = civilian ? "Civilians" : "Fighters";
        if (a.getInt(key) <= 0) return;
        a.putInt(key, a.getInt(key) - 1); a.putInt("Population", Math.max(0, a.getInt("Population") - 1));
        b.putInt(key, b.getInt(key) + 1); b.putInt("Population", b.getInt("Population") + 1);
        factionStates.put(from.id(), a); factionStates.put(to.id(), b);
        addHistory(from, gameTime, personName + " defected to " + to.name() + ".");
        addHistory(to, gameTime, personName + " joined after leaving " + from.name() + ".");
        adjustRelation(from, to, -2, gameTime, null);
        setDirty();
    }

    public WorldFaction bySlot(int slot) {
        for (WorldFaction faction : factions) if (faction.slot() == slot) return faction;
        return null;
    }

    public WorldFaction byId(String id) {
        if (id == null || id.isBlank()) return null;
        for (WorldFaction faction : factions) if (faction.id().equals(id)) return faction;
        return null;
    }

    /** Persistent history offset layered over each pair's world-seeded baseline relationship. */
    public int relationShift(WorldFaction a, WorldFaction b) {
        if (a == null || b == null || a.id().equals(b.id())) return 0;
        CompoundTag relations = factionStates.contains("__Relations", Tag.TAG_COMPOUND)
                ? factionStates.getCompound("__Relations") : new CompoundTag();
        return relations.getInt(relationKey(a, b));
    }

    public void adjustRelation(WorldFaction a, WorldFaction b, int delta, long gameTime, String reason) {
        if (a == null || b == null || a.id().equals(b.id()) || delta == 0) return;
        CompoundTag relations = factionStates.contains("__Relations", Tag.TAG_COMPOUND)
                ? factionStates.getCompound("__Relations").copy() : new CompoundTag();
        String key = relationKey(a, b);
        int before = relations.getInt(key);
        int after = Math.max(-70, Math.min(70, before + delta));
        if (after == before) return;
        relations.putInt(key, after);
        factionStates.put("__Relations", relations);
        if (reason != null && !reason.isBlank()) {
            addHistory(a, gameTime, reason + " [relations " + (delta > 0 ? "+" : "") + delta + "]");
            addHistory(b, gameTime, reason + " [relations " + (delta > 0 ? "+" : "") + delta + "]");
        }
        setDirty();
    }

    private static String relationKey(WorldFaction a, WorldFaction b) {
        return a.slot() < b.slot() ? a.id() + "__" + b.id() : b.id() + "__" + a.id();
    }

    private CompoundTag wars() {
        return factionStates.contains("__Wars", Tag.TAG_COMPOUND)
                ? factionStates.getCompound("__Wars").copy() : new CompoundTag();
    }

    public long warEndTime(WorldFaction a, WorldFaction b) {
        if (a == null || b == null || a.id().equals(b.id())) return 0L;
        return wars().getLong(relationKey(a, b));
    }

    public boolean isAtWar(WorldFaction a, WorldFaction b, long gameTime) {
        long end = warEndTime(a, b);
        return end > gameTime;
    }

    public List<WorldFaction> warEnemies(WorldFaction faction, long gameTime) {
        if (faction == null) return List.of();
        return factions.stream().filter(other -> !other.id().equals(faction.id())
                        && other.realm() == faction.realm() && !isExtinct(other)
                        && isAtWar(faction, other, gameTime))
                .toList();
    }

    public void startWar(WorldFaction a, WorldFaction b, long gameTime, int days, String reason) {
        if (a == null || b == null || a.id().equals(b.id()) || a.realm() != b.realm()
                || isExtinct(a) || isExtinct(b)) return;
        CompoundTag wars = wars();
        String key = relationKey(a, b);
        long end = gameTime + Math.max(2, Math.min(12, days)) * 24000L;
        if (wars.getLong(key) > gameTime) return;
        wars.putLong(key, end);
        factionStates.put("__Wars", wars);
        String why = reason == null || reason.isBlank() ? "Open war broke out with "
                : reason.endsWith("war with") ? reason + " " : reason + " into war with ";
        addHistory(a, gameTime, why + b.name() + ".");
        addHistory(b, gameTime, why + a.name() + ".");
        adjustRelation(a, b, -12, gameTime, null);
        setDirty();
    }

    public void endWar(WorldFaction a, WorldFaction b, long gameTime, String reason) {
        if (a == null || b == null) return;
        CompoundTag wars = wars();
        String key = relationKey(a, b);
        if (!wars.contains(key)) return;
        wars.remove(key);
        factionStates.put("__Wars", wars);
        String ending = reason == null || reason.isBlank() ? "A ceasefire ended the war with " : reason + " ended the war with ";
        addHistory(a, gameTime, ending + b.name() + ".");
        addHistory(b, gameTime, ending + a.name() + ".");
        setDirty();
    }

    public String publicState(WorldFaction faction, long gameTime) {
        if (isExtinct(faction)) return "EXTINCT";
        if (!warEnemies(faction, gameTime).isEmpty()) return "AT WAR";
        if (isLeaderKilled(faction)) return "LEADERLESS";
        if (supplies(faction) < 12) return "STARVING";
        if (supplies(faction) < 28) return "SHORT ON SUPPLIES";
        float m = momentum(faction);
        if (m < 0.76F) return "WEAKENED";
        if (m > 1.24F) return "ASCENDANT";
        if (m > 1.10F) return "RISING";
        if (faction.structure() == FactionStructure.SCHOOL || faction.structure() == FactionStructure.ORDER) return "TRAINING";
        return "STABLE";
    }

    private void coolRelationHistory() {
        if (!factionStates.contains("__Relations", Tag.TAG_COMPOUND)) return;
        CompoundTag relations = factionStates.getCompound("__Relations").copy();
        for (String key : new HashSet<>(relations.getAllKeys())) {
            int value = relations.getInt(key);
            if (value > 0) relations.putInt(key, value - 1);
            else if (value < 0) relations.putInt(key, value + 1);
        }
        factionStates.put("__Relations", relations);
    }

    public float momentum(WorldFaction faction) {
        CompoundTag state = state(faction);
        return state.contains("Momentum") ? state.getFloat("Momentum") : 1.0F;
    }

    public int casualties(WorldFaction faction) { return state(faction).getInt("Casualties"); }
    public int victories(WorldFaction faction) { return state(faction).getInt("Victories"); }
    public int succession(WorldFaction faction) { return state(faction).getInt("Succession"); }

    public void adjustMomentum(WorldFaction faction, float delta) {
        CompoundTag state = state(faction);
        float next = Math.max(0.62F, Math.min(1.48F, (state.contains("Momentum") ? state.getFloat("Momentum") : 1.0F) + delta));
        state.putFloat("Momentum", next);
        factionStates.put(faction.id(), state);
        setDirty();
    }

    public void recordCasualty(WorldFaction faction, FactionRole role) { recordCasualty(faction, role, false); }

    public void recordCasualty(WorldFaction faction, FactionRole role, boolean civilian) {
        CompoundTag state = ensureSocietyState(faction);
        state.putInt("Casualties", state.getInt("Casualties") + 1);
        state.putInt("Population", Math.max(0, state.getInt("Population") - 1));
        String key = civilian ? "Civilians" : "Fighters";
        state.putInt(key, Math.max(0, state.getInt(key) - 1));
        factionStates.put(faction.id(), state);
        float loss = civilian ? -0.003F : switch (role) {
            case RECRUIT -> -0.004F; case MEMBER -> -0.006F; case ENFORCER -> -0.012F;
            case LIEUTENANT -> -0.028F; case LEADER -> -0.16F;
        };
        adjustMomentum(faction, loss);
    }

    /** Records a real death for someone whose manpower slot was already removed by captivity. */
    public void recordCasualtyAlreadyAbsent(WorldFaction faction, FactionRole role, boolean civilian) {
        if (faction == null) return;
        CompoundTag state = ensureSocietyState(faction);
        state.putInt("Casualties", state.getInt("Casualties") + 1);
        factionStates.put(faction.id(), state);
        float loss = civilian ? -0.003F : switch (role) {
            case RECRUIT -> -0.004F; case MEMBER -> -0.006F; case ENFORCER -> -0.012F;
            case LIEUTENANT -> -0.028F; case LEADER -> -0.16F;
        };
        adjustMomentum(faction, loss);
    }

    public void recordVictory(WorldFaction faction, FactionRole defeatedRole) {
        CompoundTag state = state(faction);
        state.putInt("Victories", state.getInt("Victories") + 1);
        factionStates.put(faction.id(), state);
        float gain = switch (defeatedRole) {
            case RECRUIT -> 0.002F; case MEMBER -> 0.004F; case ENFORCER -> 0.007F;
            case LIEUTENANT -> 0.014F; case LEADER -> 0.045F;
        };
        adjustMomentum(faction, gain);
    }

    public long nextPresenceTime(WorldFaction faction) {
        return state(faction).getLong("NextPresence");
    }

    public void markPresenceSpawned(WorldFaction faction, long gameTime) {
        markPresenceSpawned(faction, gameTime, null);
    }

    public void markPresenceSpawned(WorldFaction faction, long gameTime, BlockPos pos) {
        CompoundTag state = state(faction);
        state.putLong("NextPresence", gameTime + 1800L + Math.floorMod(faction.seed(), 1800L));
        if (pos != null) {
            state.putInt("PresenceX", pos.getX());
            state.putInt("PresenceY", pos.getY());
            state.putInt("PresenceZ", pos.getZ());
        }
        factionStates.put(faction.id(), state);
        setDirty();
    }

    public void updatePresencePos(WorldFaction faction, BlockPos pos) {
        CompoundTag state = state(faction);
        state.putInt("PresenceX", pos.getX());
        state.putInt("PresenceY", pos.getY());
        state.putInt("PresenceZ", pos.getZ());
        factionStates.put(faction.id(), state);
        setDirty();
    }

    public BlockPos presenceLastPos(WorldFaction faction) {
        CompoundTag state = state(faction);
        return state.contains("PresenceX")
                ? new BlockPos(state.getInt("PresenceX"), state.getInt("PresenceY"), state.getInt("PresenceZ")) : null;
    }

    public boolean isLeaderSpawned(WorldFaction faction) { return state(faction).getBoolean("Spawned"); }
    public boolean isLeaderKilled(WorldFaction faction) { return state(faction).getBoolean("Killed"); }
    public long successionAt(WorldFaction faction) { return state(faction).getLong("SuccessionAt"); }

    public String currentLeaderName(WorldFaction faction) {
        CompoundTag state = state(faction);
        return state.contains("CurrentLeaderName") ? state.getString("CurrentLeaderName") : faction.leaderName();
    }

    public boolean matchesCurrentLeader(WorldFaction faction, AmbientFighterEntity fighter) {
        if (faction == null || fighter == null) return false;
        CompoundTag state = state(faction);
        if (state.hasUUID("CurrentLeaderUUID")) return state.getUUID("CurrentLeaderUUID").equals(fighter.getUUID());
        return currentLeaderName(faction).equals(fighter.getFighterName());
    }

    public FighterRace currentLeaderRace(WorldFaction faction) {
        CompoundTag state = state(faction);
        return state.contains("CurrentLeaderRace") ? FighterRace.byId(state.getInt("CurrentLeaderRace")) : faction.leaderRace();
    }

    public boolean currentLeaderFemale(WorldFaction faction) {
        CompoundTag state = state(faction);
        return state.contains("CurrentLeaderFemale") ? state.getBoolean("CurrentLeaderFemale") : faction.leaderFemale();
    }

    public FighterPersonality currentLeaderPersonality(WorldFaction faction) {
        CompoundTag state = state(faction);
        return state.contains("CurrentLeaderPersonality")
                ? FighterPersonality.byId(state.getInt("CurrentLeaderPersonality")) : faction.leaderPersonality();
    }

    public FighterArchetype currentLeaderArchetype(WorldFaction faction) {
        CompoundTag state = state(faction);
        return state.contains("CurrentLeaderArchetype")
                ? FighterArchetype.byId(state.getInt("CurrentLeaderArchetype")) : faction.leaderArchetype();
    }

    public BlockPos leaderLastPos(WorldFaction faction) {
        CompoundTag state = state(faction);
        if (!state.getBoolean("Spawned") || !state.contains("X")) return null;
        return new BlockPos(state.getInt("X"), state.getInt("Y"), state.getInt("Z"));
    }

    public void markLeaderSpawned(WorldFaction faction, BlockPos pos) {
        CompoundTag state = state(faction);
        state.putBoolean("Spawned", true);
        state.putBoolean("Killed", false);
        putPos(state, pos);
        factionStates.put(faction.id(), state);
        setDirty();
    }

    public void updateLeaderPos(WorldFaction faction, BlockPos pos, long gameTime) {
        CompoundTag state = state(faction);
        if (!state.getBoolean("Spawned") || state.getBoolean("Killed")) return;
        putPos(state, pos);
        state.putLong("LastSeen", gameTime);
        factionStates.put(faction.id(), state);
        setDirty();
    }

    public void nominateSuccessor(WorldFaction faction, AmbientFighterEntity candidate, long gameTime) {
        if (candidate == null || faction == null) return;
        CompoundTag state = ensureSocietyState(faction);
        candidate.setPersistenceRequired();
        state.putString("CandidateName", candidate.getFighterName());
        state.putUUID("CandidateUUID", candidate.getUUID());
        state.putInt("CandidateX", candidate.blockPosition().getX());
        state.putInt("CandidateY", candidate.blockPosition().getY());
        state.putInt("CandidateZ", candidate.blockPosition().getZ());
        state.putInt("CandidateRace", candidate.getRace().id());
        state.putBoolean("CandidateFemale", candidate.isFemale());
        state.putInt("CandidatePersonality", candidate.getPersonality().id());
        state.putInt("CandidateArchetype", candidate.getArchetype().id());
        factionStates.put(faction.id(), state);
        addHistory(faction, gameTime, candidate.getFighterName() + " became the leading succession candidate.");
        setDirty();
    }

    public void clearSuccessionCandidateIf(WorldFaction faction, String name, long gameTime) {
        CompoundTag state = state(faction);
        if (state.contains("CandidateName") && state.getString("CandidateName").equals(name)) {
            state.remove("CandidateName"); state.remove("CandidateUUID");
            state.remove("CandidateX"); state.remove("CandidateY"); state.remove("CandidateZ");
            state.remove("CandidateRace"); state.remove("CandidateFemale");
            state.remove("CandidatePersonality"); state.remove("CandidateArchetype");
            factionStates.put(faction.id(), state);
            addHistory(faction, gameTime, "The leading succession candidate was lost.");
            setDirty();
        }
    }

    public void markLeaderKilled(WorldFaction faction, BlockPos pos, long gameTime) {
        CompoundTag state = state(faction);
        state.putBoolean("Spawned", true);
        state.putBoolean("Killed", true);
        putPos(state, pos);
        RandomSource random = RandomSource.create(mix(faction.seed() ^ gameTime ^ (long)state.getInt("Succession") * 31L));
        int days = 4 + random.nextInt(5); // 4-8 Minecraft days without a leader.
        state.putLong("SuccessionAt", gameTime + days * 24000L);
        factionStates.put(faction.id(), state);
        setDirty();
    }

    /** Debug-only recovery when testing leader spawning. */
    public void resetLeader(WorldFaction faction) {
        CompoundTag state = state(faction);
        state.putBoolean("Spawned", false);
        state.putBoolean("Killed", false);
        state.remove("SuccessionAt");
        factionStates.put(faction.id(), state);
        setDirty();
    }

    /** Organization simulation: food, births, recruitment, promotions, defections, extinction and succession. */
    public void tickOrganizations(ServerLevel anyLevel) {
        long gameTime = anyLevel.getServer().overworld().getGameTime();
        if (gameTime - lastOrganizationTick < 1200L) return;
        lastOrganizationTick = gameTime;

        for (WorldFaction faction : new ArrayList<>(factions)) {
            CompoundTag state = ensureSocietyState(faction);
            if (state.getBoolean("Extinct") && isPermanentFaction(faction)) {
                state.putBoolean("Extinct", false);
                state.putInt("Population", Math.max(36, state.getInt("Population")));
                state.putInt("Fighters", Math.max(26, state.getInt("Fighters")));
                state.putInt("Civilians", Math.max(10, state.getInt("Civilians")));
                state.putInt("Supplies", Math.max(45, state.getInt("Supplies")));
                state.putFloat("Momentum", Math.max(0.96F, state.getFloat("Momentum")));
            }
            if (state.getBoolean("Extinct")) { factionStates.put(faction.id(), state); continue; }
            float momentum = state.contains("Momentum") ? state.getFloat("Momentum") : 1.0F;
            float target = state.getBoolean("Killed") ? 0.82F : 1.0F;
            if (momentum < target) momentum = Math.min(target, momentum + 0.006F);
            else if (momentum > target) momentum = Math.max(target, momentum - 0.003F);
            state.putFloat("Momentum", momentum);

            if (state.getBoolean("Killed") && state.getLong("SuccessionAt") > 0L
                    && gameTime >= state.getLong("SuccessionAt")) appointSuccessor(faction, state);
            factionStates.put(faction.id(), state);
        }

        long day = Math.max(0L, gameTime / 24000L);
        if (day != lastSocietyDay) {
            lastSocietyDay = day;
            simulateDay(anyLevel.getServer().overworld(), gameTime, day);
        }
        setDirty();
    }

    private void simulateDay(ServerLevel overworld, long gameTime, long day) {
        RandomSource global = RandomSource.create(mix(overworld.getSeed() ^ (day * 0x9E3779B97F4A7C15L)));
        for (WorldFaction faction : new ArrayList<>(factions)) {
            CompoundTag s = ensureSocietyState(faction);
            if (s.getBoolean("Extinct")) continue;
            RandomSource r = RandomSource.create(mix(faction.seed() ^ day * 0xC2B2AE3D27D4EB4FL));
            int pop = s.getInt("Population");
            int fighters = s.getInt("Fighters");
            int civilians = s.getInt("Civilians");
            int youth = s.getInt("Youth");
            int supplies = s.getInt("Supplies");
            List<String> events = new ArrayList<>();

            // Daily consumption is abstract; foraging scenes and hunting restore this value.
            int consume = Math.max(1, pop / 12);
            supplies = Math.max(0, supplies - consume);
            if (r.nextFloat() < 0.38F + Math.max(0, 45 - supplies) / 100.0F) {
                int found = 4 + r.nextInt(9);
                supplies = Math.min(120, supplies + found);
                if (supplies < 35 || found >= 10) events.add("A foraging party returned with supplies.");
            }
            if (supplies < 15) {
                s.putFloat("Momentum", Math.max(0.62F, s.getFloat("Momentum") - (supplies == 0 ? 0.018F : 0.008F)));
            }
            if (supplies == 0 && pop > 4 && r.nextFloat() < 0.16F) {
                if (civilians > 2) { civilians--; pop--; events.add("A resident left after the supplies ran dry."); }
                else if (fighters > 2) { fighters--; pop--; events.add("A fighter deserted during the shortage."); }
            }

            int capacity = populationCapacity(faction);
            if (supplies >= 42 && pop < capacity && r.nextFloat() < 0.30F) {
                youth++; pop++;
                events.add("A child was born into the community.");
            }
            if (youth > 0 && r.nextFloat() < 0.24F) {
                youth--; civilians++;
                events.add("A young resident came of age.");
            }
            if (supplies >= 28 && pop < capacity && r.nextFloat() < (0.16F + Math.max(0F, s.getFloat("Momentum") - 0.85F) * 0.18F)) {
                pop++;
                boolean recruitFighter = r.nextFloat() < 0.58F;
                if (recruitFighter) fighters++; else civilians++;
                events.add(recruitFighter ? "A wandering fighter joined the ranks." : "A new resident joined the group.");
            }
            if (civilians > 2 && r.nextFloat() < trainingChance(faction)) {
                civilians--; fighters++;
                events.add("A resident completed combat training and joined the fighters.");
            }

            // Rare abstract betrayal/defection. Physical named members can also defect separately.
            if (pop > 7 && r.nextFloat() < (s.getFloat("Momentum") < 0.82F ? 0.10F : 0.025F)) {
                WorldFaction to = pickDefectionTarget(faction, r);
                if (to != null) {
                    String deserter = FighterNames.roll(r, rollFactionRace(r, faction.realm()), false);
                    boolean civilian = civilians > 2 && r.nextFloat() < 0.35F;
                    // Save current state first; transferPopulation reads canonical state again.
                    s.putInt("Population", pop); s.putInt("Fighters", fighters); s.putInt("Civilians", civilians);
                    s.putInt("Youth", youth); s.putInt("Supplies", supplies); factionStates.put(faction.id(), s);
                    transferPopulation(faction, to, civilian, gameTime, deserter);
                    s = ensureSocietyState(faction);
                    pop=s.getInt("Population"); fighters=s.getInt("Fighters"); civilians=s.getInt("Civilians");
                }
            }

            // Quiet diplomacy can soften a world-seeded rivalry over long periods, while
            // violence/defections push the persistent relation history in the other direction.
            if (s.getFloat("Momentum") >= 0.92F && supplies >= 35 && r.nextFloat() < 0.035F) {
                List<WorldFaction> diplomatic = factions.stream()
                        .filter(other -> !other.id().equals(faction.id()) && other.realm() == faction.realm() && !isExtinct(other))
                        .filter(other -> faction.alignment() == other.alignment() || faction.alignment() == FighterAlignment.NEUTRAL
                                || other.alignment() == FighterAlignment.NEUTRAL).toList();
                if (!diplomatic.isEmpty()) {
                    WorldFaction other = diplomatic.get(r.nextInt(diplomatic.size()));
                    adjustRelation(faction, other, 2, gameTime, null);
                }
            }

            if (isPermanentFaction(faction)) {
                // These are global institutions, not immortal individual people. Losses still
                // matter, leaders can die and wars can hurt them, but the organization recruits
                // enough replacements that it cannot disappear from the setting.
                fighters = Math.max(18, fighters);
                civilians = Math.max(8, civilians);
                pop = Math.max(fighters + civilians + youth, 30);
                supplies = Math.max(24, supplies);
                s.putFloat("Momentum", Math.max(0.82F, s.getFloat("Momentum")));
            }
            s.putInt("Population", pop); s.putInt("Fighters", fighters); s.putInt("Civilians", civilians);
            s.putInt("Youth", youth); s.putInt("Supplies", supplies);
            factionStates.put(faction.id(), s);
            for (String event : events) addHistory(faction, gameTime, event);

            if (!isPermanentFaction(faction) && (pop <= 2 || (pop <= 5 && supplies == 0 && s.getFloat("Momentum") <= 0.68F && r.nextFloat() < 0.30F))) {
                s.putBoolean("Extinct", true);
                s.putLong("ExtinctAt", gameTime);
                factionStates.put(faction.id(), s);
                addHistory(faction, gameTime, "The organization collapsed. Its remaining members scattered.");
            }
        }

        simulateWars(overworld, global, gameTime);
        simulateOffscreenConflicts(overworld, global, gameTime);

        // Historical grudges/alliances matter, but very slowly cool without fresh incidents.
        coolRelationHistory();

        int target = targetFactionCount(overworld.getSeed());
        int active = (int)factions.stream().filter(f -> !isExtinct(f)).count();
        long quietTicks = lastFactionFormationTick <= 0L ? gameTime : Math.max(0L, gameTime - lastFactionFormationTick);
        long quietDays = quietTicks / 24000L;
        // R40: after the seeded society reaches its baseline, organic founding used to be a flat
        // 1.8% daily roll. That can produce multi-week silent stretches. Pressure now grows with
        // each quiet day and is bounded by a five-day guarantee, while the 44-faction hard ceiling
        // and the existing split/founder simulation remain unchanged.
        float foundingPressure = Math.min(0.68F, 0.08F + Math.max(0L, quietDays - 1L) * 0.12F);
        boolean replacement = active < target;
        boolean organicFounding = active < 44 && quietDays >= 1L
                && (quietDays >= 5L || global.nextFloat() < foundingPressure);
        if (replacement || organicFounding) {
            boolean cult = global.nextFloat() < (replacement ? 0.42F : 0.58F);
            FactionRealm realm = global.nextFloat() < 0.40F ? FactionRealm.NAMEK : FactionRealm.EARTH;
            WorldFaction born = foundFaction(overworld, realm, cult, gameTime);
            if (born != null) {
                lastFactionFormationTick = gameTime;
                WorldFaction parent = null;
                if (!replacement) {
                    List<WorldFaction> possibleParents = factions.stream()
                            .filter(f -> !f.id().equals(born.id()) && f.realm() == realm && !isExtinct(f) && population(f) >= 14).toList();
                    if (!possibleParents.isEmpty()) parent = possibleParents.get(global.nextInt(possibleParents.size()));
                }
                if (parent != null) {
                    createSplinterPopulation(parent, born, 6 + global.nextInt(7), gameTime);
                    addHistory(born, gameTime, "The organization split away from " + parent.name() + ".");
                    addHistory(parent, gameTime, "A splinter group broke away and formed " + born.name() + ".");
                    adjustRelation(parent, born, -18, gameTime, null);
                } else {
                    addHistory(born, gameTime, cult ? "A new cult emerged among drifters and defectors."
                            : "A new organization was founded by drifters and defectors.");
                }
            }
        }
    }

    private void simulateWars(ServerLevel overworld, RandomSource global, long gameTime) {
        CompoundTag warTag = wars();
        // End expired wars cleanly and preserve the event in both histories.
        List<WorldFaction> snapshot = new ArrayList<>(factions);
        for (int i = 0; i < snapshot.size(); i++) {
            WorldFaction a = snapshot.get(i);
            if (isExtinct(a)) continue;
            for (int j = i + 1; j < snapshot.size(); j++) {
                WorldFaction b = snapshot.get(j);
                if (a.realm() != b.realm() || isExtinct(b)) continue;
                String key = relationKey(a, b);
                long end = warTag.getLong(key);
                if (end > 0L && end <= gameTime) {
                    warTag.remove(key);
                    addHistory(a, gameTime, "A ceasefire ended the war with " + b.name() + ".");
                    addHistory(b, gameTime, "A ceasefire ended the war with " + a.name() + ".");
                }
            }
        }
        factionStates.put("__Wars", warTag);

        int activeWars = 0;
        for (int i = 0; i < snapshot.size(); i++) for (int j = i + 1; j < snapshot.size(); j++)
            if (isAtWar(snapshot.get(i), snapshot.get(j), gameTime)) activeWars++;

        // Enemy relations can escalate into a multi-day war, but cap concurrent wars so the
        // whole world does not permanently become one giant combat event.
        if (activeWars < 6) {
            List<WorldFaction[]> candidates = new ArrayList<>();
            for (int i = 0; i < snapshot.size(); i++) {
                WorldFaction a = snapshot.get(i);
                if (isExtinct(a)) continue;
                for (int j = i + 1; j < snapshot.size(); j++) {
                    WorldFaction b = snapshot.get(j);
                    if (a.realm() != b.realm() || isExtinct(b) || isAtWar(a, b, gameTime)) continue;
                    if (FactionManager.relation(overworld, a, b) == FactionRelation.ENEMY) candidates.add(new WorldFaction[]{a,b});
                }
            }
            if (!candidates.isEmpty() && global.nextFloat() < 0.12F) {
                WorldFaction[] pair = candidates.get(global.nextInt(candidates.size()));
                startWar(pair[0], pair[1], gameTime, 3 + global.nextInt(6), "Escalating violence erupted");
            }
        }

        // Wars create meaningful off-screen pressure, not a daily wall of minor logs.
        Set<String> processed = new HashSet<>();
        for (WorldFaction a : snapshot) {
            for (WorldFaction b : warEnemies(a, gameTime)) {
                String key = relationKey(a, b);
                if (!processed.add(key) || global.nextFloat() >= 0.52F) continue;
                double ap = Math.max(1, fighterPopulation(a)) * effectiveWarPower(a);
                double bp = Math.max(1, fighterPopulation(b)) * effectiveWarPower(b);
                WorldFaction winner = global.nextDouble() < ap / Math.max(1.0D, ap + bp) ? a : b;
                WorldFaction loser = winner == a ? b : a;
                int losses = fighterPopulation(loser) > 8 && global.nextFloat() < 0.28F ? 2 : 1;
                for (int n = 0; n < losses && fighterPopulation(loser) > 1; n++) recordCasualty(loser, FactionRole.MEMBER, false);
                recordVictory(winner, FactionRole.ENFORCER);
                int raid = Math.min(supplies(loser), 4 + global.nextInt(9));
                if (raid > 0 && (winner.structure() == FactionStructure.GANG || winner.structure() == FactionStructure.SYNDICATE
                        || winner.ethos() == FactionEthos.RAIDERS)) {
                    addSupplies(loser, -raid); addSupplies(winner, raid);
                }
                addHistory(winner, gameTime, "War skirmish against " + loser.name() + " ended in a decisive victory.");
                addHistory(loser, gameTime, "The war with " + winner.name() + " cost " + losses + " fighter" + (losses == 1 ? "" : "s") + ".");
                adjustRelation(winner, loser, -2, gameTime, null);
            }
        }
    }

    private double effectiveWarPower(WorldFaction faction) {
        return faction.powerBias() * momentum(faction) * (0.75D + Math.min(1.25D, supplies(faction) / 80.0D));
    }

    private void simulateOffscreenConflicts(ServerLevel overworld, RandomSource global, long gameTime) {
        Set<String> resolvedPairs = new HashSet<>();
        for (WorldFaction faction : new ArrayList<>(factions)) {
            if (isExtinct(faction) || fighterPopulation(faction) <= 0 || global.nextFloat() >= 0.055F) continue;
            List<WorldFaction> conflicts = factions.stream()
                    .filter(other -> !other.id().equals(faction.id()) && !isExtinct(other)
                            && other.realm() == faction.realm() && fighterPopulation(other) > 0)
                    .filter(other -> FactionManager.relation(overworld, faction, other).rivalry()).toList();
            if (conflicts.isEmpty()) continue;
            WorldFaction other = conflicts.get(global.nextInt(conflicts.size()));
            String pair = relationKey(faction, other);
            if (!resolvedPairs.add(pair)) continue;
            FactionRelation relation = FactionManager.relation(overworld, faction, other);

            double aPower = Math.max(1.0D, fighterPopulation(faction)) * faction.powerBias() * momentum(faction);
            double bPower = Math.max(1.0D, fighterPopulation(other)) * other.powerBias() * momentum(other);
            double aChance = aPower / Math.max(1.0D, aPower + bPower);
            WorldFaction winner = global.nextDouble() < aChance ? faction : other;
            WorldFaction loser = winner == faction ? other : faction;

            recordVictory(winner, FactionRole.MEMBER);
            if (relation == FactionRelation.ENEMY && global.nextFloat() < 0.68F && fighterPopulation(loser) > 0) {
                recordCasualty(loser, FactionRole.MEMBER, false);
                addHistory(loser, gameTime, "A fighter was lost in an off-screen clash with " + winner.name() + ".");
                addHistory(winner, gameTime, "A clash with " + loser.name() + " ended in a hard victory.");
                adjustRelation(winner, loser, -3, gameTime, null);
                if (global.nextFloat() < 0.22F && fighterPopulation(winner) > 2) {
                    recordCasualty(winner, FactionRole.MEMBER, false);
                    addHistory(winner, gameTime, "The clash with " + loser.name() + " also cost one of their fighters.");
                }
                if (winner.structure() == FactionStructure.GANG || winner.structure() == FactionStructure.SYNDICATE
                        || winner.ethos() == FactionEthos.RAIDERS) {
                    int stolen = Math.min(supplies(loser), 3 + global.nextInt(7));
                    if (stolen > 0) { addSupplies(loser, -stolen); addSupplies(winner, stolen); }
                }
            } else {
                addHistory(winner, gameTime, "A non-lethal clash with " + loser.name() + " ended in victory.");
                adjustRelation(winner, loser, -1, gameTime, null);
            }
        }
    }

    private void createSplinterPopulation(WorldFaction parent, WorldFaction child, int requested, long gameTime) {
        CompoundTag from = ensureSocietyState(parent);
        CompoundTag to = ensureSocietyState(child);
        int availableAdults = Math.max(0, from.getInt("Fighters") + from.getInt("Civilians") - 4);
        int moved = Math.max(3, Math.min(requested, availableAdults));
        if (moved <= 0) return;
        int movedFighters = Math.min(from.getInt("Fighters") - 2, Math.max(2, Math.round(moved * 0.68F)));
        movedFighters = Math.max(0, movedFighters);
        int movedCivilians = Math.min(from.getInt("Civilians") - 2, Math.max(0, moved - movedFighters));
        movedCivilians = Math.max(0, movedCivilians);
        moved = movedFighters + movedCivilians;
        if (moved <= 0) return;

        from.putInt("Fighters", Math.max(0, from.getInt("Fighters") - movedFighters));
        from.putInt("Civilians", Math.max(0, from.getInt("Civilians") - movedCivilians));
        from.putInt("Population", Math.max(from.getInt("Youth"), from.getInt("Population") - moved));
        int stolenSupplies = Math.min(from.getInt("Supplies"), 8 + moved * 2);
        from.putInt("Supplies", Math.max(0, from.getInt("Supplies") - stolenSupplies));

        to.putInt("Population", moved);
        to.putInt("Fighters", movedFighters);
        to.putInt("Civilians", movedCivilians);
        to.putInt("Youth", 0);
        to.putInt("Supplies", Math.max(12, Math.min(70, stolenSupplies)));
        to.putFloat("Momentum", 0.84F + Math.min(0.16F, moved * 0.012F));
        factionStates.put(parent.id(), from);
        factionStates.put(child.id(), to);
        adjustMomentum(parent, -0.035F);
        setDirty();
    }

    private float trainingChance(WorldFaction faction) {
        return switch (faction.structure()) {
            case SCHOOL -> 0.22F; case CULT, ORDER -> 0.15F; case CLAN, GUARD -> 0.13F;
            case GANG, SYNDICATE -> 0.09F; case CREW -> 0.11F;
        };
    }

    private WorldFaction pickDefectionTarget(WorldFaction from, RandomSource random) {
        List<WorldFaction> candidates = factions.stream().filter(f -> !f.id().equals(from.id()))
                .filter(f -> f.realm() == from.realm() && !isExtinct(f)).toList();
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    private static int initialPopulation(WorldFaction faction) {
        if (EARTH_GUARDIANS_ID.equals(faction.id())) return 92;
        if (BLACK_SUN_ID.equals(faction.id())) return 88;
        RandomSource r = RandomSource.create(mix(faction.seed() ^ 0x504F50554C415449L));
        return switch (faction.structure()) {
            case CULT -> 10 + r.nextInt(23);       // 10-32
            case CREW -> 12 + r.nextInt(25);       // 12-36
            case ORDER -> 16 + r.nextInt(31);      // 16-46
            case CLAN -> 18 + r.nextInt(34);       // 18-51
            case SCHOOL -> 20 + r.nextInt(37);     // 20-56
            case GANG -> 22 + r.nextInt(39);       // 22-60
            case GUARD -> 26 + r.nextInt(43);      // 26-68
            case SYNDICATE -> 28 + r.nextInt(45);  // 28-72
        };
    }

    private static int populationCapacity(WorldFaction faction) {
        if (isPermanentFaction(faction)) return 150;
        int base = initialPopulation(faction);
        int extra = switch (faction.structure()) {
            case CULT, CREW -> 18;
            case ORDER, CLAN -> 26;
            case SCHOOL, GANG -> 34;
            case GUARD, SYNDICATE -> 42;
        };
        return base + extra;
    }

    private static float fighterShare(WorldFaction faction) {
        return switch (faction.structure()) {
            case CULT -> 0.72F; case GUARD -> 0.70F; case SCHOOL -> 0.66F; case GANG -> 0.64F;
            case CREW -> 0.62F; case SYNDICATE -> 0.60F; case CLAN -> 0.58F; case ORDER -> 0.56F;
        };
    }

    private CompoundTag ensureSocietyState(WorldFaction faction) {
        CompoundTag s = state(faction);
        if (!s.contains("Population")) {
            int pop = initialPopulation(faction);
            int fighters = Math.max(2, Math.min(pop - 2, Math.round(pop * fighterShare(faction))));
            s.putInt("Population", pop);
            s.putInt("Fighters", fighters);
            s.putInt("Civilians", Math.max(2, pop - fighters));
            s.putInt("Youth", 0);
            s.putInt("Supplies", isPermanentFaction(faction) ? 105 : 58 + Math.floorMod((int)(faction.seed() >>> 21), 36));
            s.putFloat("Momentum", s.contains("Momentum") ? s.getFloat("Momentum") : (isPermanentFaction(faction) ? 1.24F : 1.0F));
        }
        return s;
    }

    private WorldFaction foundFaction(ServerLevel level, FactionRealm realm, boolean forceCult, long gameTime) {
        int slot = factions.stream().mapToInt(WorldFaction::slot).max().orElse(0) + 1;
        long seed = mix(level.getSeed() ^ gameTime ^ (slot * 0xD6E8FEB86659FD93L));
        RandomSource r = RandomSource.create(seed);
        Set<String> used = new HashSet<>(); for (WorldFaction f : factions) used.add(f.name());
        FactionEthos ethos = forceCult ? FactionEthos.POWER_CULT : rollEthos(r, realm);
        FactionStructure structure = forceCult ? FactionStructure.CULT : FactionStructure.forEthos(ethos, realm);
        FighterAlignment alignment = rollAlignment(r, ethos);
        String name = uniqueName(r, used, realm);
        int uniform = r.nextInt(16);
        float powerBias = 0.68F + r.nextFloat() * 0.88F;
        BlockPos spawn = level.getSharedSpawnPos();
        double angle = r.nextDouble() * Math.PI * 2.0D;
        int radius = realm == FactionRealm.EARTH ? 900 + r.nextInt(6501) : 700 + r.nextInt(5201);
        int bx = realm == FactionRealm.EARTH ? spawn.getX() : 0, bz = realm == FactionRealm.EARTH ? spawn.getZ() : 0;
        FighterRace race = rollFactionRace(r, realm); boolean female = race.gendered() && r.nextFloat() < 0.46F;
        WorldFaction f = new WorldFaction(slot, "faction_" + slot, name, ethos, alignment, realm, structure,
                uniform, powerBias, seed, bx + (int)Math.round(Math.cos(angle)*radius), bz + (int)Math.round(Math.sin(angle)*radius),
                520 + r.nextInt(581), FighterNames.roll(r, race, female), race, female,
                ethos.rollPersonality(r, alignment), ethos.rollArchetype(r));
        factions.add(f); factionStates.put(f.id(), ensureSocietyState(f)); setDirty(); return f;
    }

    public WorldFaction debugFoundFaction(ServerLevel level, FactionRealm realm, boolean cult) {
        WorldFaction created = foundFaction(level.getServer().overworld(), realm, cult,
                level.getServer().overworld().getGameTime() + factions.size() * 31L);
        if (created != null) addHistory(created, level.getServer().overworld().getGameTime(),
                cult ? "The cult was founded (debug acceleration)." : "The organization was founded (debug acceleration).");
        return created;
    }

    public void debugCollapse(WorldFaction faction, long gameTime) {
        if (isPermanentFaction(faction)) {
            addHistory(faction, gameTime, "A catastrophic attack tested the organization, but the institution survived and rebuilt.");
            return;
        }
        CompoundTag s = ensureSocietyState(faction);
        s.putInt("Population", 0); s.putInt("Fighters", 0); s.putInt("Civilians", 0); s.putInt("Youth", 0);
        s.putBoolean("Extinct", true); s.putLong("ExtinctAt", gameTime);
        factionStates.put(faction.id(), s);
        addHistory(faction, gameTime, "The organization collapsed (debug acceleration).");
        setDirty();
    }

    public void debugSimulateDay(ServerLevel level) {
        long fakeDay = Math.max(lastSocietyDay + 1L, level.getServer().overworld().getGameTime() / 24000L + 1L);
        lastSocietyDay = fakeDay;
        simulateDay(level.getServer().overworld(), fakeDay * 24000L, fakeDay);
        setDirty();
    }

    private void appointSuccessor(WorldFaction faction, CompoundTag state) {
        int succession = state.getInt("Succession") + 1;
        RandomSource random = RandomSource.create(mix(faction.seed() ^ (0x51CC35510L * succession)));
        FighterRace race;
        boolean female;
        boolean existingCandidate = state.contains("CandidateName");
        if (existingCandidate) {
            race = FighterRace.byId(state.getInt("CandidateRace"));
            female = state.getBoolean("CandidateFemale");
            state.putString("CurrentLeaderName", state.getString("CandidateName"));
            if (state.hasUUID("CandidateUUID")) state.putUUID("CurrentLeaderUUID", state.getUUID("CandidateUUID"));
            state.putInt("CurrentLeaderRace", race.id());
            state.putBoolean("CurrentLeaderFemale", female);
            state.putInt("CurrentLeaderPersonality", state.getInt("CandidatePersonality"));
            state.putInt("CurrentLeaderArchetype", state.getInt("CandidateArchetype"));
            if (state.contains("CandidateX")) {
                state.putInt("X", state.getInt("CandidateX"));
                state.putInt("Y", state.getInt("CandidateY"));
                state.putInt("Z", state.getInt("CandidateZ"));
            }
        } else {
            state.remove("CurrentLeaderUUID");
            race = rollFactionRace(random, faction.realm());
            female = race.gendered() && random.nextFloat() < 0.46F;
            state.putString("CurrentLeaderName", FighterNames.roll(random, race, female));
            state.putInt("CurrentLeaderRace", race.id());
            state.putBoolean("CurrentLeaderFemale", female);
            state.putInt("CurrentLeaderPersonality", faction.ethos().rollPersonality(random, faction.alignment()).id());
            state.putInt("CurrentLeaderArchetype", faction.ethos().rollArchetype(random).id());
        }
        state.putInt("Succession", succession);
        // If an existing persistent person won succession, reserve the leader slot for that
        // entity instead of allowing /lw faction leader to create a duplicate profile.
        state.putBoolean("Spawned", existingCandidate);
        state.putBoolean("Killed", false);
        state.remove("SuccessionAt");
        state.remove("CandidateName"); state.remove("CandidateUUID");
        state.remove("CandidateX"); state.remove("CandidateY"); state.remove("CandidateZ");
        state.remove("CandidateRace"); state.remove("CandidateFemale");
        state.remove("CandidatePersonality"); state.remove("CandidateArchetype");
        if (!existingCandidate) { state.remove("X"); state.remove("Y"); state.remove("Z"); }
        state.putFloat("Momentum", Math.max(0.86F, state.contains("Momentum") ? state.getFloat("Momentum") : 0.86F));
    }

    private CompoundTag state(WorldFaction faction) {
        return factionStates.contains(faction.id(), Tag.TAG_COMPOUND)
                ? factionStates.getCompound(faction.id()).copy() : new CompoundTag();
    }

    private static void putPos(CompoundTag tag, BlockPos pos) {
        tag.putInt("X", pos.getX()); tag.putInt("Y", pos.getY()); tag.putInt("Z", pos.getZ());
    }

    private static String uniqueName(RandomSource random, Set<String> used, FactionRealm realm) {
        String[] first = realm == FactionRealm.NAMEK ? NAMEK_FIRST : NAME_FIRST;
        String[] second = realm == FactionRealm.NAMEK ? NAMEK_SECOND : NAME_SECOND;
        for (int attempt = 0; attempt < 96; attempt++) {
            String name = first[random.nextInt(first.length)] + " " + second[random.nextInt(second.length)];
            if (used.add(name)) return name;
        }
        String fallback = (realm == FactionRealm.NAMEK ? "Namek Circle " : "Wayfarers ") + (used.size() + 1);
        used.add(fallback);
        return fallback;
    }

    private static FighterAlignment rollAlignment(RandomSource random, FactionEthos ethos) {
        int roll = random.nextInt(100);
        FighterAlignment preferred = ethos.preferredAlignment();
        if (roll < 72) return preferred;
        if (roll < 90) return FighterAlignment.NEUTRAL;
        if (preferred == FighterAlignment.GOOD) return FighterAlignment.BAD;
        if (preferred == FighterAlignment.BAD) return FighterAlignment.GOOD;
        return random.nextBoolean() ? FighterAlignment.GOOD : FighterAlignment.BAD;
    }

    public static FighterRace rollFactionRace(RandomSource random) {
        return rollFactionRace(random, FactionRealm.EARTH);
    }

    public static FighterRace rollFactionRace(RandomSource random, FactionRealm realm) {
        int roll = random.nextInt(100);
        if (realm == FactionRealm.NAMEK) {
            if (roll < 66) return FighterRace.NAMEKIAN;
            if (roll < 77) return FighterRace.HUMAN;
            if (roll < 87) return FighterRace.SAIYAN;
            if (roll < 92) return FighterRace.MAJIN;
            if (roll < 96) return FighterRace.FROST_DEMON;
            return FighterRace.BIO_ANDROID;
        }
        if (roll < 38) return FighterRace.HUMAN;
        if (roll < 62) return FighterRace.SAIYAN;
        if (roll < 76) return FighterRace.NAMEKIAN;
        if (roll < 88) return FighterRace.MAJIN;
        if (roll < 95) return FighterRace.FROST_DEMON;
        return FighterRace.BIO_ANDROID;
    }

    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
