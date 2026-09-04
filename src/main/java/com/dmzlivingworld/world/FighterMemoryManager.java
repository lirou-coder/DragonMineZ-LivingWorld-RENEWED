package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterRank;
import com.dmzlivingworld.entity.RacialFormProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Lightweight per-player world memory. Only a handful of meaningful fighters are
 * retained as records; ordinary ambient population remains disposable/despawnable.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterMemoryManager {
    private static final String ROOT_KEY = "DMZLivingWorldMemory";
    private static final String RIVALS_KEY = "KnownFighters";
    private static final String PEOPLE_SORT_KEY = "LWPeopleSort";
    private static final int MAX_RECORDS = 16;
    private static final int MEMORY_DATA_VERSION = 6;
    private static final long LIFE_DAY = 24_000L;
    private static final int MAX_CATCHUP_DAYS = 21;

    private FighterMemoryManager() {}

    public static void rememberEscape(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !fighter.isAlive() || fighter.isFactionLeader()) return;
        if (!fighter.isRemembered()) {
            float chance = switch (fighter.getRank()) {
                case ROOKIE -> 0.18F;
                case TRAINED -> 0.46F;
                case VETERAN -> 0.82F;
            };
            if (fighter.getRandom().nextFloat() > chance) return;
        }
        int relationshipDelta = fighter.getAlignment() == FighterAlignment.BAD ? -28 : 12;
        relationshipDelta = FighterRelationshipManager.adjustedDelta(fighter, relationshipDelta, FighterRelationshipManager.BondEvent.ENCOUNTER);
        CompoundTag record = upsert(player, fighter, relationshipDelta, false, "Escaped your fight", false);
        if (record != null && fighter.getAlignment() == FighterAlignment.BAD) noteRivalryEncounter(player, fighter, record, false);
        if (fighter.getSpeech().isEmpty()) {
            fighter.speak(fighter.getAlignment() == FighterAlignment.BAD ? "I'll remember you." : "We'll meet again.", 62);
        }
    }

    public static void rememberRescue(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !fighter.isAlive()) return;
        FactionManager.onMemberRescued(player, fighter);
        if (fighter.isFactionLeader()) return;
        upsert(player, fighter, FighterRelationshipManager.adjustedDelta(fighter, 70, FighterRelationshipManager.BondEvent.RESCUE),
                true, "Rescued from Frieza", true);
    }

    public static void rememberPlayerDefeat(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !fighter.isAlive() || fighter.isFactionLeader()) return;
        if (!fighter.isRemembered() && fighter.getRank() == FighterRank.ROOKIE && fighter.getRandom().nextFloat() > 0.45F) return;
        int relationshipDelta = fighter.getAlignment() == FighterAlignment.BAD ? -35 : 18;
        relationshipDelta = FighterRelationshipManager.adjustedDelta(fighter, relationshipDelta, FighterRelationshipManager.BondEvent.DEFEAT);
        CompoundTag record = upsert(player, fighter, relationshipDelta,
                false, "Defeated you", true);
        if (record != null) {
            noteRivalryEncounter(player, fighter, record, true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (event.getEntity() instanceof ServerPlayer player && attacker instanceof AmbientFighterEntity fighter) {
            rememberPlayerDefeat(player, fighter);
            return;
        }
        if (event.getEntity() instanceof AmbientFighterEntity fighter && attacker instanceof ServerPlayer player) {
            forgetIfKilled(player, fighter);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer original) || !(event.getEntity() instanceof ServerPlayer copy)) return;
        CompoundTag old = original.getPersistentData();
        if (old.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            copy.getPersistentData().put(ROOT_KEY, old.getCompound(ROOT_KEY).copy());
        }
    }

    /** Called by natural population before creating a fresh stranger. */
    public static boolean trySpawnRecurring(ServerPlayer player, boolean force) {
        if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) return false;
        discardLoadedDuplicateRecords(player.getServer());
        CompoundTag root = getRoot(player);
        ListTag list = root.getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        if (list.isEmpty()) return false;

        long now = level.getGameTime();
        int start = player.getRandom().nextInt(list.size());
        for (int offset = 0; offset < list.size(); offset++) {
            int index = (start + offset) % list.size();
            CompoundTag record = list.getCompound(index);
            if (!force && now < record.getLong("NextEligible")) continue;
            if (!record.hasUUID("RecordId") || !record.contains("Profile", Tag.TAG_COMPOUND)) continue;
            UUID recordId = record.getUUID("RecordId");
            // Never allow a stale recurring record to recreate a fighter already archived dead.
            if (FighterLegacyWorldData.get(level).isDeadRecord(recordId)) continue;
            if (isRecordAlreadyLoaded(player, recordId)) continue;

            CompoundTag profile = record.getCompound("Profile").copy();
            // Affiliated recurring characters remain tied to their organization's home
            // realm. Independent fighters still need to physically be in the player's realm.
            if (profile.contains("FactionId")) {
                WorldFaction faction = FactionManager.byId(level, profile.getString("FactionId"));
                if (faction != null && faction.realm() != LivingWorldDimensions.realm(level)) continue;
            }
            if (!force && !isActuallyNearby(player, record)) continue;
            BlockPos preferred = record.contains("LifeX") && record.contains("LifeZ")
                    ? new BlockPos(record.getInt("LifeX"), record.getInt("LifeY"), record.getInt("LifeZ"))
                    : player.blockPosition();
            AmbientFighterEntity fighter = AmbientFighterSpawner.spawnRememberedAt(
                    player, profile, recordId, record.getInt("Encounters") + 1,
                    record.getInt("Relationship"), record.getBoolean("Rescued"), preferred, force);
            if (fighter == null) continue;
            syncRivalryState(player, fighter, record);
            PhysicalContinuityManager.beginArrival(player, fighter, record, preferred, force);

            // Eligibility is reserved now, but the encounter itself is not counted as "seen"
            // until the physical approach actually completes near the player.
            record.putLong("NextEligible", now + 12000L + player.getRandom().nextInt(18001));
            record.put("Profile", fighter.writeMemoryProfile());
            if (force) {
                captureSeenProfile(record, player, fighter);
                record.putLong("LastSeen", now);
                recordWhereabouts(record, fighter, now, "Encountering you");
            } else {
                // Internal continuity moves to the physical approach body immediately, but the
                // player's directory is not updated until the fighter actually arrives nearby.
                record.putInt("LifeX", fighter.blockPosition().getX());
                record.putInt("LifeY", fighter.blockPosition().getY());
                record.putInt("LifeZ", fighter.blockPosition().getZ());
                record.putString("LifeDimension", fighter.level().dimension().location().toString());
                record.putInt("LifeRealm", LivingWorldDimensions.realm(level).id());
                record.putLong("LastLifeTick", now);
                record.putString("LifeActivity", "Arriving nearby");
            }
            list.set(index, record);
            root.put(RIVALS_KEY, list);
            saveRoot(player, root);

            if (force) {
                String line;
                if (record.getBoolean("Rescued")) line = "I remember you. You saved me.";
                else if (record.getInt("Relationship") <= -20) line = "You again. Good.";
                else line = "We meet again.";
                fighter.speak(line, 78);
                if (record.getInt("Relationship") <= -25 && fighter.getAlignment() == FighterAlignment.BAD) {
                    PeacekeeperManager.markNpcAggressor(player, fighter);
                    fighter.setTarget(player);
                }
            }
            return true;
        }
        return false;
    }


    public static void strengthenRelationship(ServerPlayer player, AmbientFighterEntity fighter, int delta, String outcome) {
        strengthenRelationship(player, fighter, delta, FighterRelationshipManager.BondEvent.GENERIC, outcome);
    }

    public static void strengthenRelationship(ServerPlayer player, AmbientFighterEntity fighter, int delta,
                                              FighterRelationshipManager.BondEvent event, String outcome) {
        if (player == null || fighter == null || !fighter.isAlive() || fighter.isFactionLeader()) return;
        FighterRelationshipManager.BondEvent resolved = event == null ? FighterRelationshipManager.BondEvent.GENERIC : event;
        int adjusted = FighterRelationshipManager.adjustedDelta(fighter, delta, resolved);
        if (adjusted > 0 && fighter.getAlignment() == FighterAlignment.NEUTRAL && PlayerAlignmentBridge.evil(player)) {
            adjusted = Math.max(1, (int)Math.floor(adjusted * 0.5D));
        }
        upsert(player, fighter, adjusted, fighter.wasRescuedByMemoryOwner(),
                outcome == null ? "Spent time together" : outcome, true);
    }

    public static String peopleSort(ServerPlayer player) {
        if (player == null) return "recent";
        String value = player.getPersistentData().getString(PEOPLE_SORT_KEY);
        return switch (value) {
            case "strongest", "weakest", "friendship", "recent", "lastseen" -> value;
            default -> "recent";
        };
    }

    public static String peopleSortLabel(ServerPlayer player) {
        return switch (peopleSort(player)) {
            case "strongest" -> "Strongest";
            case "weakest" -> "Weakest";
            case "friendship" -> "Friendship";
            case "lastseen" -> "Last Seen";
            default -> "Recently met";
        };
    }

    public static void cyclePeopleSort(ServerPlayer player) {
        if (player == null) return;
        String next = switch (peopleSort(player)) {
            case "recent" -> "lastseen";
            case "lastseen" -> "strongest";
            case "strongest" -> "weakest";
            case "weakest" -> "friendship";
            default -> "recent";
        };
        player.getPersistentData().putString(PEOPLE_SORT_KEY, next);
    }

    public static java.util.List<String> peopleLines(ServerPlayer player) {
        java.util.List<String> out = new java.util.ArrayList<>();
        ListTag list = getRoot(player).getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        java.util.List<CompoundTag> records = new java.util.ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag record = list.getCompound(i);
            if (!hiddenFromPeople(seenProfile(record))) records.add(record);
        }
        // Always produce a total order. Older saves can lack FirstSeen/power snapshots and many
        // fighters can share the same BP/relationship; without tie-breakers those tail entries
        // appeared unsorted even though the primary comparator had technically tied them.
        java.util.Comparator<CompoundTag> recentTie = java.util.Comparator
                .comparingLong(FighterMemoryManager::effectiveFirstSeen).reversed()
                .thenComparing(java.util.Comparator.comparingLong((CompoundTag r) -> r.getLong("LastSeen")).reversed())
                .thenComparing(FighterMemoryManager::recordName, String.CASE_INSENSITIVE_ORDER);
        java.util.Comparator<CompoundTag> comparator = switch (peopleSort(player)) {
            case "lastseen" -> java.util.Comparator.comparingLong((CompoundTag r) -> r.getLong("LastSeen")).reversed()
                    .thenComparing(java.util.Comparator.comparingLong(FighterMemoryManager::effectiveFirstSeen).reversed())
                    .thenComparing(FighterMemoryManager::recordName, String.CASE_INSENSITIVE_ORDER);
            case "strongest" -> java.util.Comparator.comparingInt(FighterMemoryManager::recordPower).reversed()
                    .thenComparing(java.util.Comparator.comparingInt((CompoundTag r) -> r.getInt("Relationship")).reversed())
                    .thenComparing(recentTie);
            case "weakest" -> java.util.Comparator.comparingInt(FighterMemoryManager::recordPower)
                    .thenComparing(java.util.Comparator.comparingInt((CompoundTag r) -> r.getInt("Relationship")).reversed())
                    .thenComparing(recentTie);
            case "friendship" -> java.util.Comparator.comparingInt((CompoundTag r) -> r.getInt("Relationship")).reversed()
                    .thenComparing(java.util.Comparator.comparingLong((CompoundTag r) -> r.getLong("LastSeen")).reversed())
                    .thenComparing(FighterMemoryManager::recordName, String.CASE_INSENSITIVE_ORDER);
            default -> recentTie;
        };
        records.sort(comparator);
        long now = player.getServer().overworld().getGameTime();
        for (CompoundTag record : records) {
            CompoundTag profile = seenProfile(record);
            int rel = record.getInt("Relationship");
            String bond = FighterRelationshipManager.relationshipStage(rel);
            String faction = profile.getString("FactionName");
            String affiliation = faction.isBlank() ? "Independent" : faction;
            String title = profile.getString("LegacyTitle");
            String inspectMarker = record.hasUUID("RecordId") ? "@person:" + record.getUUID("RecordId") + "|" : "";
            long unseenDays = Math.max(0L, now - record.getLong("LastSeen")) / LIFE_DAY;
            String activity = compactActivity(record.getString("SeenActivity"));
            String lastSeen = unseenDays <= 0L ? "today" : unseenDays == 1L ? "1d ago" : unseenDays + "d ago";
            String prefix = rel >= 40 ? "+ " : rel < 0 ? "!! " : "* ";
            out.add(inspectMarker + prefix + (title.isBlank() ? "" : title + " ") + profile.getString("Name")
                    + " — " + bond + " • " + affiliation + (activity.isBlank() ? "" : " • " + activity) + " • " + lastSeen);
        }
        if (out.isEmpty()) out.add(". You have not formed any lasting bonds yet. People appear here after enough shared history to remember each other.");
        return out;
    }

    private static int recordPower(CompoundTag record) {
        CompoundTag profile = seenProfile(record);
        return Math.max(profile.getInt("PermanentBattlePower"), profile.getInt("BattlePower"));
    }

    private static long effectiveFirstSeen(CompoundTag record) {
        long first = record == null ? 0L : record.getLong("FirstSeen");
        if (first > 0L) return first;
        return record == null ? 0L : record.getLong("LastSeen");
    }

    private static String recordName(CompoundTag record) {
        if (record == null) return "";
        CompoundTag profile = seenProfile(record);
        return profile.getString("Name");
    }

    /** Last-seen appearance snapshots keyed by People record id, safe for the client journal. */
    public static java.util.Map<UUID, CompoundTag> peoplePortraitSnapshots(ServerPlayer player) {
        java.util.Map<UUID, CompoundTag> out = new java.util.HashMap<>();
        if (player == null) return out;
        ListTag list = getRoot(player).getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag record = list.getCompound(i);
            if (!record.hasUUID("RecordId")) continue;
            CompoundTag profile = seenProfile(record);
            if (hiddenFromPeople(profile)) continue;
            if (!profile.isEmpty()) out.put(record.getUUID("RecordId"), profile.copy());
        }
        return out;
    }

    private static String compactActivity(String activity) {
        if (activity == null || activity.isBlank()) return "";
        String clean = activity.trim();
        // The People page is an index, not the full biography. Keep live/last-seen activity
        // recognizable at a glance and leave the full wording to the remembered profile.
        if (clean.length() <= 22) return clean;
        String lower = clean.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("fishing")) return "Fishing";
        if (lower.contains("stargaz")) return "Stargazing";
        if (lower.contains("meditat")) return "Meditating";
        if (lower.contains("flying")) return "Flying";
        if (lower.contains("scout") || lower.contains("looking around")) return "Scouting";
        if (lower.contains("rest")) return "Resting";
        if (lower.contains("break") || lower.contains("eating")) return "Taking a break";
        if (lower.contains("meeting up") || lower.contains("meet up")) return "Meeting someone";
        if (lower.contains("mentor")) return "Training";
        if (lower.contains("rival")) return "Rival business";
        if (lower.contains("faction")) return "Faction business";
        return clean.substring(0, 19).trim() + "…";
    }

    /** Antagonist people the player has personally learned about; never exposes off-screen hidden records. */
    public static java.util.List<String> antagonistLines(ServerPlayer player) {
        java.util.List<String> out = new java.util.ArrayList<>();
        ListTag list = getRoot(player).getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        long now = player.getServer().overworld().getGameTime();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag record = list.getCompound(i);
            CompoundTag profile = seenProfile(record);
            CompoundTag legacy = profile.contains("Legacy", Tag.TAG_COMPOUND) ? profile.getCompound("Legacy") : new CompoundTag();
            if (!legacy.getBoolean("AntagonistRecognized") && !legacy.getBoolean("AntagonistCore")) continue;
            String marker = record.hasUUID("RecordId") ? "@person:" + record.getUUID("RecordId") + "|" : "";
            String epithet = legacy.getString("AntagonistEpithet");
            String name = profile.getString("Name");
            int rel = record.getInt("Relationship");
            long unseenDays = Math.max(0L, now - record.getLong("LastSeen")) / LIFE_DAY;
            out.add(marker + "!! " + (epithet.isBlank() ? "" : epithet + " ") + name
                    + " — " + FighterRelationshipManager.relationshipStage(rel)
                    + " • last seen " + (unseenDays <= 0L ? "recently" : unseenDays + " day(s) ago")
                    + " • remembered appearance");
        }
        return out;
    }

    /** Read-only copy used by the People tab's last-remembered profile. */
    public static CompoundTag rememberedRecord(ServerPlayer player, UUID recordId) {
        if (player == null || recordId == null) return new CompoundTag();
        ListTag list = getRoot(player).getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, recordId);
        if (index < 0) return new CompoundTag();
        CompoundTag copy = list.getCompound(index).copy();
        // The People journal must never expose off-screen simulation state. Replace the internal
        // life profile in this read-only copy with what the player actually saw last.
        copy.put("Profile", seenProfile(copy));
        return copy;
    }

    /** Server-side current record for systems that need the fighter's physical Ki/life state. Never send this to the client UI. */
    public static CompoundTag internalSignalRecord(ServerPlayer player, UUID recordId) {
        if (player == null || recordId == null) return new CompoundTag();
        ListTag list = getRoot(player).getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, recordId);
        return index < 0 ? new CompoundTag() : list.getCompound(index).copy();
    }

    public static String rememberedWhereabouts(CompoundTag record) {
        return record == null || record.isEmpty() ? "unknown" : approximateKnownWhereabouts(record);
    }

    /** Latest real player/fighter relationship event, if this player actually remembers the fighter. */
    public static String lastBondEvent(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !fighter.isRememberedFor(player) || fighter.getMemoryRecordId() == null) return "";
        ListTag list = getRoot(player).getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, fighter.getMemoryRecordId());
        if (index < 0) return "";
        return list.getCompound(index).getString("LastOutcome");
    }

    /** Long-save biography assembled only from events the fighter actually lived through. */
    public static java.util.List<String> biographyLines(ServerPlayer player, AmbientFighterEntity fighter) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (fighter == null) return out;
        CompoundTag legacy = fighter.getLegacyData();

        if (player != null && fighter.isRememberedFor(player) && fighter.getMemoryRecordId() != null) {
            ListTag known = getRoot(player).getList(RIVALS_KEY, Tag.TAG_COMPOUND);
            int index = findRecordIndex(known, fighter.getMemoryRecordId());
            if (index >= 0) {
                CompoundTag record = known.getCompound(index);
                long now = player.getServer().overworld().getGameTime();
                long first = record.getLong("FirstSeen");
                long knownDays = first <= 0L ? 0L : Math.max(0L, now - first) / LIFE_DAY;
                out.add("* Known since day " + dayNumber(first) + " • " + knownDays + " day(s) of shared history • met "
                        + Math.max(1, record.getInt("Encounters")) + "x");
            }
        }

        out.add("* Training sessions: " + fighter.getTrainingSessions() + " • completed goals: " + FighterGoalManager.completedCount(fighter));
        int given = legacy.getInt("InterventionsGiven");
        int received = legacy.getInt("InterventionsReceived");
        if (given > 0 || received > 0) out.add("* Bond interventions: gave " + given + " • received " + received);
        String threat = OrganicThreatManager.statusLabel(fighter);
        if (!threat.isBlank()) out.add("!! " + threat + " • recognition comes from existing record/power, not bonus scaling");

        ListTag timeline = legacy.getList("Timeline", Tag.TAG_COMPOUND);
        java.util.Set<String> timed = new java.util.HashSet<>();
        if (!timeline.isEmpty()) {
            int start = Math.max(0, timeline.size() - 12);
            for (int i = start; i < timeline.size(); i++) {
                CompoundTag row = timeline.getCompound(i);
                String text = row.getString("Text");
                if (text.isBlank()) continue;
                timed.add(text);
                out.add(". Day " + dayNumber(row.getLong("Tick")) + " — " + text);
            }
        }

        // Keep a few pre-1.4 events visible even though old saves did not timestamp them.
        ListTag events = legacy.getList("Events", Tag.TAG_STRING);
        int earlierAdded = 0;
        for (int i = events.size() - 1; i >= 0 && earlierAdded < 4; i--) {
            String text = events.getString(i);
            if (text.isBlank() || timed.contains(text)) continue;
            out.add(". Earlier — " + text);
            earlierAdded++;
        }
        return out;
    }

    private static long dayNumber(long tick) {
        return Math.max(1L, Math.max(0L, tick) / LIFE_DAY + 1L);
    }

    public static boolean rememberForDebug(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !fighter.isAlive() || fighter.isFactionLeader()) return false;
        return upsert(player, fighter, 5, false, "Marked for testing", true) != null;
    }

    public static String status(ServerPlayer player) {
        ListTag list = getRoot(player).getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        if (list.isEmpty()) return "No recurring fighters are remembered yet.";
        StringBuilder out = new StringBuilder("Known fighters: ").append(list.size()).append('/').append(MAX_RECORDS).append(" — ");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag record = list.getCompound(i);
            CompoundTag profile = record.getCompound("Profile");
            if (i > 0) out.append(" | ");
            out.append(profile.getString("Name"))
                    .append(" (PL ").append(profile.getInt("BattlePower"))
                    .append(", met ").append(Math.max(1, record.getInt("Encounters"))).append('x');
            if (record.getBoolean("Rescued")) out.append(", rescued");
            if (record.getInt("BattlesVsPlayer") > 0) out.append(", rivalry ").append(record.getInt("BattlesVsPlayer")).append(" fights");
            out.append(')');
        }
        return out.toString();
    }

    /** Debug only: records one real rivalry encounter without granting staged upgrades. */
    public static boolean forceRivalryEncounter(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !fighter.isAlive() || fighter.getAlignment() != FighterAlignment.BAD) return false;
        CompoundTag record = upsert(player, fighter, -25, false, "Debug rivalry encounter", true);
        if (record == null) return false;
        noteRivalryEncounter(player, fighter, record, false);
        return true;
    }

    public record SocialContactResult(boolean accepted, boolean coolingDown, int gained, int relationship, long remainingTicks) {}

    /** Last meaningful Talk contact, kept separate from passive LastSeen updates so reunions remain real. */
    public record ReunionInfo(boolean qualifies, long ticksAway, double oldPlayerPower) {}

    public static ReunionInfo reunionInfo(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !fighter.isRememberedFor(player) || fighter.getMemoryRecordId() == null)
            return new ReunionInfo(false, 0L, 0.0D);
        ListTag list = getRoot(player).getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, fighter.getMemoryRecordId());
        if (index < 0) return new ReunionInfo(false, 0L, 0.0D);
        CompoundTag record = list.getCompound(index);
        long last = record.getLong("LastSocialContact");
        if (last <= 0L) return new ReunionInfo(false, 0L, record.getDouble("LastPlayerPowerAtSocial"));
        long now = player.serverLevel().getServer().overworld().getGameTime();
        long away = Math.max(0L, now - last);
        return new ReunionInfo(away >= 48_000L, away, record.getDouble("LastPlayerPowerAtSocial"));
    }

    /** Prepares a deterministic long-absence + power-growth reunion without bypassing normal Talk. */
    public static boolean debugPrepareReunion(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !fighter.isAlive()) return false;
        if (!fighter.isRememberedFor(player)) strengthenRelationship(player, fighter, 35, "Debug reunion setup");
        ListTag list = getRoot(player).getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, fighter.getMemoryRecordId());
        if (index < 0) return false;
        CompoundTag record = list.getCompound(index);
        long now = player.serverLevel().getServer().overworld().getGameTime();
        record.putLong("LastSocialContact", Math.max(1L, now - 96_000L));
        record.putDouble("LastPlayerPowerAtSocial", Math.max(1.0D, PlayerWorldManager.playerBattlePower(player) * 0.55D));
        record.putLong("NextSocialContact", 0L);
        saveRecord(player, record);
        return true;
    }

    /**
     * A low-pressure conversation can introduce a stranger and slowly build familiarity, but
     * conversation alone caps at the configured social limit. From there, sparring, gifts, protection, travelling and
     * other shared experiences have to carry the relationship further. The cooldown is stored
     * in the same persistent person record, so re-instantiating a remembered fighter cannot
     * reset it.
     */
    /** Remaining Talk cooldown for the exact remembered person. Passive LastSeen never affects it. */
    public static long socialContactCooldownRemaining(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !fighter.isAlive() || fighter.getMemoryRecordId() == null) return 0L;
        ListTag list = getRoot(player).getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, fighter.getMemoryRecordId());
        if (index < 0) return 0L;
        CompoundTag existing = list.getCompound(index);
        long now = player.serverLevel().getServer().overworld().getGameTime();
        return Math.max(0L, existing.getLong("NextSocialContact") - now);
    }

    public static SocialContactResult trySocialContact(ServerPlayer player, AmbientFighterEntity fighter,
                                                       int baseDelta, long cooldownTicks, String outcome) {
        if (player == null || fighter == null || !fighter.isAlive())
            return new SocialContactResult(false, false, 0, 0, 0L);
        CompoundTag root = getRoot(player);
        ListTag list = root.getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, fighter.getMemoryRecordId());
        CompoundTag existing = index >= 0 ? list.getCompound(index) : null;
        int current = existing == null ? 0 : existing.getInt("Relationship");
        long now = player.serverLevel().getServer().overworld().getGameTime();
        if (existing != null && now < existing.getLong("NextSocialContact")) {
            return new SocialContactResult(true, true, 0, current, existing.getLong("NextSocialContact") - now);
        }
        // Enemies do not become friends because the player repeatedly presses Talk.
        if (current <= -35) return new SocialContactResult(false, false, 0, current, 0L);

        int adjusted = FighterRelationshipManager.adjustedDelta(fighter, Math.max(0, baseDelta),
                FighterRelationshipManager.BondEvent.CONVERSATION);
        if (adjusted > 0 && fighter.getAlignment() == FighterAlignment.NEUTRAL && PlayerAlignmentBridge.evil(player)) {
            adjusted = Math.max(1, (int)Math.floor(adjusted * 0.5D));
        }
        int target = Math.min(LivingWorldConfig.talkRelationshipCap(), current + adjusted);
        int actual = Math.max(0, target - current);
        CompoundTag record = upsert(player, fighter, actual, fighter.wasRescuedByMemoryOwner(),
                outcome == null || outcome.isBlank() ? "Talked together" : outcome, true);
        if (record == null) return new SocialContactResult(false, false, 0, current, 0L);
        record.putLong("NextSocialContact", now + Math.max(20L, cooldownTicks));
        record.putLong("LastSocialContact", now);
        record.putDouble("LastPlayerPowerAtSocial", PlayerWorldManager.playerBattlePower(player));
        saveRecord(player, record);
        return new SocialContactResult(true, false, actual, record.getInt("Relationship"), 0L);
    }

    public record KnownFactionPerson(UUID recordId, String name, FactionRole role, FighterRank rank, int relationship,
                                     String activity, long lastSeen, int battlePower, CompoundTag appearance) {}

    /** Player-known recurring people belonging to a faction, used by the faction Members page. */
    public static List<KnownFactionPerson> knownFactionPeople(ServerPlayer player, String factionId) {
        if (player == null || factionId == null || factionId.isBlank()) return List.of();
        ListTag list = getRoot(player).getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        java.util.ArrayList<KnownFactionPerson> out = new java.util.ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag record = list.getCompound(i);
            CompoundTag profile = seenProfile(record);
            if (!factionId.equals(profile.getString("FactionId"))) continue;
            if (!record.hasUUID("RecordId")) continue;
            out.add(new KnownFactionPerson(record.getUUID("RecordId"), profile.getString("Name"),
                    FactionRole.byId(profile.getInt("FactionRole")),
                    FighterRank.byId(profile.getInt("Rank")), record.getInt("Relationship"),
                    record.getString("SeenActivity"), record.getLong("LastSeen"), profile.getInt("BattlePower"), profile.copy()));
        }
        return List.copyOf(out);
    }

    public static int count(ServerPlayer player) {
        if (player == null) return 0;
        ListTag list = getRoot(player).getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int visible = 0;
        for (int i = 0; i < list.size(); i++) {
            if (!hiddenFromPeople(seenProfile(list.getCompound(i)))) visible++;
        }
        return visible;
    }

    /** World Menace remains remembered internally, but it is not an ordinary person in the People directory. */
    private static boolean hiddenFromPeople(CompoundTag profile) {
        return profile != null && !profile.isEmpty()
                && (profile.getBoolean(WorldMenaceManager.HEROBRINE_TAG)
                || profile.getBoolean(RedRibbonExperimentManager.TAG)
                || "Herobrine".equalsIgnoreCase(profile.getString("Name"))
                || "Red Ribbon Experiment X-7".equalsIgnoreCase(profile.getString("Name")));
    }

    public static boolean summonKnown(ServerPlayer player) {
        return trySpawnRecurring(player, true);
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        CompoundTag root = getRoot(player);
        root.put(RIVALS_KEY, new ListTag());
        saveRoot(player, root);
        detachLoaded(player, null);
    }

    public static boolean forget(ServerPlayer player, UUID recordId) {
        if (player == null || recordId == null) return false;
        CompoundTag root = getRoot(player);
        ListTag list = root.getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, recordId);
        if (index < 0) return false;
        list.remove(index);
        root.put(RIVALS_KEY, list);
        saveRoot(player, root);
        detachLoaded(player, recordId);
        return true;
    }

    private static void detachLoaded(ServerPlayer player, UUID recordId) {
        if (player == null || player.getServer() == null) return;
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (AmbientFighterEntity fighter : level.getEntitiesOfClass(AmbientFighterEntity.class,
                    new net.minecraft.world.phys.AABB(-3.0E7D, level.getMinBuildHeight(), -3.0E7D, 3.0E7D, level.getMaxBuildHeight(), 3.0E7D),
                    f -> player.getUUID().equals(f.getMemoryOwnerId()) && (recordId == null || recordId.equals(f.getMemoryRecordId())))) {
                fighter.detachMemory(player.getUUID(), recordId);
            }
        }
    }

    public static void clearFallenView(ServerPlayer player) {
        if (player != null) player.getPersistentData().putLong("LWFallenClearedAt", player.getServer().overworld().getGameTime());
    }

    public static long fallenViewSince(ServerPlayer player) {
        return player == null ? 0L : player.getPersistentData().getLong("LWFallenClearedAt");
    }

    private static CompoundTag upsert(ServerPlayer player, AmbientFighterEntity fighter, int relationshipDelta,
                                      boolean rescued, String outcome, boolean forceCreate) {
        // Herobrine is not an ordinary remembered person and never forms a personal bond.
        // WorldMenaceManager owns per-player sightings/inspection history separately.
        if (player == null || fighter == null || WorldMenaceManager.isWorldMenace(fighter)) return null;
        CompoundTag root = getRoot(player);
        ListTag list = root.getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, fighter.getMemoryRecordId());

        if (index < 0 && !forceCreate && list.size() >= MAX_RECORDS) return null;
        if (index < 0 && list.size() >= MAX_RECORDS) {
            index = oldestRecordIndex(list);
            list.remove(index);
            index = -1;
        }

        CompoundTag record = index >= 0 ? list.getCompound(index) : new CompoundTag();
        // If the owner was offline when a wish revived this fighter, the entity still carries
        // the exact old bond and can seed its missing player-side directory entry on reunion.
        if (index < 0 && fighter.isRememberedFor(player)) {
            record.putInt("Relationship", fighter.getMemoryRelationship());
            record.putInt("Encounters", Math.max(1, fighter.getMemoryEncounters()));
            record.putBoolean("Rescued", fighter.wasRescuedByMemoryOwner());
        }
        UUID recordId = record.hasUUID("RecordId") ? record.getUUID("RecordId") : UUID.randomUUID();
        int encounters = Math.max(1, record.getInt("Encounters"));
        if (index < 0) encounters = 1;

        record.putUUID("RecordId", recordId);
        record.putInt("Relationship", clamp(record.getInt("Relationship") + relationshipDelta, -100, 100));
        record.putBoolean("Rescued", rescued || record.getBoolean("Rescued"));
        record.putInt("Encounters", encounters);
        record.putString("LastOutcome", outcome);
        long now = player.serverLevel().getGameTime();
        if (!record.contains("FirstSeen", Tag.TAG_ANY_NUMERIC)) record.putLong("FirstSeen", now);
        record.putLong("LastSeen", now);
        record.putLong("NextEligible", now + 12000L + player.getRandom().nextInt(18001));
        // Bind before capturing disposition so the snapshot reflects the relationship change that
        // just happened (rescue, spar, gift, conversation, etc.), not a pre-bond first impression.
        fighter.bindMemory(player.getUUID(), recordId, encounters, record.getInt("Relationship"), record.getBoolean("Rescued"));
        captureSeenProfile(record, player, fighter);
        recordWhereabouts(record, fighter, now, "Active nearby");

        if (index >= 0) list.set(index, record); else list.add(record);
        root.put(RIVALS_KEY, list);
        saveRoot(player, root);
        return record;
    }

    public static void refreshLoadedProfile(AmbientFighterEntity fighter) {
        if (fighter == null || WorldMenaceManager.isWorldMenace(fighter) || !fighter.isRemembered() || fighter.getMemoryOwnerId() == null
                || !(fighter.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(fighter.getMemoryOwnerId());
        if (owner == null) return;
        CompoundTag root = getRoot(owner);
        ListTag list = root.getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, fighter.getMemoryRecordId());
        if (index < 0) return;
        CompoundTag record = list.getCompound(index);
        CompoundTag currentProfile = fighter.writeMemoryProfile();
        record.put("Profile", currentProfile.copy());
        long now = level.getServer().overworld().getGameTime();
        double distance = owner.distanceToSqr(fighter);
        // "Seen" means physically nearby, not merely loaded/sensed at long range. Ki Sense may
        // reveal a live signal, but it must never silently replace the People panel's last-seen BP.
        boolean observable = owner.level() == fighter.level() && distance <= 32.0D * 32.0D;
        if (PhysicalContinuityManager.isTransitioning(fighter) || !observable) {
            String ambient = FighterAmbientActivityManager.currentActivity(fighter);
            recordInternalWhereabouts(record, fighter, now, ambient.isBlank() ? activityForProfile(currentProfile, record) : ambient);
        } else {
            captureSeenProfile(record, owner, fighter, currentProfile);
            String ambient = FighterAmbientActivityManager.currentActivity(fighter);
            recordWhereabouts(record, fighter, now, ambient.isBlank() ? activityForProfile(currentProfile, record) : ambient);
        }
        list.set(index, record);
        root.put(RIVALS_KEY, list);
        saveRoot(owner, root);
    }

    private static void saveRecord(ServerPlayer player, CompoundTag updated) {
        if (!updated.hasUUID("RecordId")) return;
        CompoundTag root = getRoot(player);
        ListTag list = root.getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, updated.getUUID("RecordId"));
        if (index >= 0) list.set(index, updated);
        root.put(RIVALS_KEY, list);
        saveRoot(player, root);
    }

    /** Reinstates the exact player-side directory entry carried by a wished-back fighter. */
    public static void restoreRevivedBond(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.getMemoryOwnerId() == null || fighter.getMemoryRecordId() == null
                || !(fighter.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(fighter.getMemoryOwnerId());
        if (owner == null) return;
        CompoundTag root = getRoot(owner);
        ListTag list = root.getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, fighter.getMemoryRecordId());
        CompoundTag record = index >= 0 ? list.getCompound(index) : new CompoundTag();
        record.putUUID("RecordId", fighter.getMemoryRecordId());
        record.putInt("Relationship", fighter.getMemoryRelationship());
        record.putInt("Encounters", Math.max(1, fighter.getMemoryEncounters()));
        record.putBoolean("Rescued", fighter.wasRescuedByMemoryOwner());
        record.putString("LastOutcome", "Returned to life by a wish");
        long now = level.getServer().overworld().getGameTime();
        if (!record.contains("FirstSeen", Tag.TAG_ANY_NUMERIC)) record.putLong("FirstSeen", now);
        record.putLong("LastSeen", now);
        record.putLong("NextEligible", now + 12000L);
        captureSeenProfile(record, owner, fighter);
        recordWhereabouts(record, fighter, now, "Returned to life");
        if (index >= 0) list.set(index, record);
        else {
            if (list.size() >= MAX_RECORDS) list.remove(oldestRecordIndex(list));
            list.add(record);
        }
        root.put(RIVALS_KEY, list);
        saveRoot(owner, root);
    }

    private static void forgetIfKilled(ServerPlayer player, AmbientFighterEntity fighter) {
        UUID recordId = fighter.getMemoryRecordId();
        if (recordId == null) return;
        CompoundTag root = getRoot(player);
        ListTag list = root.getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, recordId);
        if (index < 0) return;
        String name = list.getCompound(index).getCompound("Profile").getString("Name");
        list.remove(index);
        root.put(RIVALS_KEY, list);
        saveRoot(player, root);
        player.displayClientMessage(Component.literal(name + " is dead. It may only come back with a wish...")
                .withStyle(ChatFormatting.DARK_GRAY), false);
    }

    private static boolean isRecordAlreadyLoaded(ServerPlayer player, UUID recordId) {
        if (player == null || recordId == null || player.getServer() == null) return false;
        // Identity, not proximity, decides whether a remembered person is physically loaded.
        // A fighter thousands of blocks away must not be simulated as an abstract duplicate.
        for (ServerLevel loadedLevel : player.getServer().getAllLevels()) {
            for (var entity : loadedLevel.getAllEntities()) {
                if (entity instanceof AmbientFighterEntity fighter && fighter.isAlive()
                        && recordId.equals(fighter.getMemoryRecordId())) return true;
            }
        }
        return false;
    }

    private static void discardLoadedDuplicateRecords(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (ServerLevel loadedLevel : server.getAllLevels()) {
            for (var entity : loadedLevel.getAllEntities()) {
                if (!(entity instanceof AmbientFighterEntity fighter) || !fighter.isAlive()) continue;
                UUID recordId = fighter.getMemoryRecordId();
                if (recordId == null) continue;
                if (!seen.add(recordId)) fighter.discard();
            }
        }
    }

    /**
     * Advances remembered fighters while they are not physically loaded. The simulation is
     * deliberately coarse (one meaningful decision per MC day): no fake per-tick AI and no
     * free stat assignment from the player. Power only changes when the recorded activity is training;
     * the medium relevance profile can only change how productive/frequent that earned training is.
     */
    public static void tickPersistentLives(ServerPlayer player, long now) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;
        CompoundTag root = getRoot(player);
        ListTag list = root.getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        boolean changed = false;

        // Keep every genuinely loaded remembered person authoritative over their abstract record,
        // even when their loaded chunk is thousands of blocks from the player. Collect loaded
        // identities in the same pass so persistent-life simulation does not rescan every loaded
        // entity once for every remembered record.
        Set<UUID> loadedRecordIds = new HashSet<>();
        for (ServerLevel loadedLevel : player.getServer().getAllLevels()) {
            for (var entity : loadedLevel.getAllEntities()) {
                if (entity instanceof AmbientFighterEntity fighter && fighter.isAlive()) {
                    UUID recordId = fighter.getMemoryRecordId();
                    if (recordId != null) loadedRecordIds.add(recordId);
                    if (fighter.isRememberedFor(player)) refreshLoadedProfile(fighter);
                }
            }
        }

        for (int i = 0; i < list.size(); i++) {
            CompoundTag record = list.getCompound(i);
            if (!record.hasUUID("RecordId") || !record.contains("Profile", Tag.TAG_COMPOUND)) continue;
            if (loadedRecordIds.contains(record.getUUID("RecordId"))) continue;

            long last = record.getLong("LastLifeTick");
            if (last <= 0L) {
                record.putLong("LastLifeTick", now);
                list.set(i, record);
                changed = true;
                continue;
            }
            int days = (int)Math.min(MAX_CATCHUP_DAYS, Math.max(0L, (now - last) / LIFE_DAY));
            if (days <= 0) continue;
            for (int day = 0; day < days; day++) simulateLifeDay(player, list, i, record, last + LIFE_DAY * (day + 1L));
            record.putLong("LastLifeTick", last + LIFE_DAY * days);
            list.set(i, record);
            changed = true;
        }

        if (changed) {
            root.put(RIVALS_KEY, list);
            saveRoot(player, root);
        }
    }

    private static void simulateLifeDay(ServerPlayer player, ListTag list, int index, CompoundTag record, long tick) {
        CompoundTag profile = record.getCompound("Profile").copy();
        CompoundTag legacy = profile.contains("Legacy", Tag.TAG_COMPOUND) ? profile.getCompound("Legacy").copy() : new CompoundTag();
        profile.put("Legacy", legacy);

        String goal = legacy.getString("GoalType");
        String baselineActivity = activityForProfile(profile, record);
        String routineActivity = FighterDailyRoutineManager.rememberedDevelopmentActivity(player, profile, record, tick);
        // Concrete travel/search missions keep their established causal movement. The daily plan
        // can still replace a day with real training, but it never makes a rival/equipment mission
        // disappear into an unrelated leisure roll.
        String plannedActivity = switch (goal) {
            case "DEFEAT_RIVAL", "ACQUIRE_EQUIPMENT", "FUSION" -> "Training".equals(routineActivity) ? routineActivity : baselineActivity;
            case "WIN_FIGHTS", "DEFEAT_STRONGER" -> "Training".equals(routineActivity) ? routineActivity : baselineActivity;
            default -> routineActivity;
        };
        String activity = chooseDevelopmentActivity(player, profile, record, plannedActivity);
        String destination = "";
        CompoundTag target = null;
        if ("DEFEAT_RIVAL".equals(goal) && !profile.getString("RivalName").isBlank()) {
            target = recordByName(list, profile.getString("RivalName"), index);
            destination = target == null ? profile.getString("RivalName") : "Rival " + profile.getString("RivalName");
        }

        // Strong personal bonds and established nemeses can intentionally travel toward the player.
        boolean bondedToPlayer = record.getInt("Relationship") >= 55;
        boolean hostileRival = record.getInt("Relationship") <= -35 && record.getInt("BattlesVsPlayer") >= 2;
        if (target == null && (bondedToPlayer || hostileRival)
                && player.getRandom().nextFloat() < 0.18F) {
            record.putInt("LifeTargetX", player.blockPosition().getX());
            record.putInt("LifeTargetZ", player.blockPosition().getZ());
            record.putString("LifeTargetDimension", player.level().dimension().location().toString());
            destination = bondedToPlayer ? "Meeting you" : "Seeking you";
            activity = "Travelling";
        } else if (target != null && sameLifeDimension(record, target)) {
            record.putInt("LifeTargetX", target.getInt("LifeX"));
            record.putInt("LifeTargetZ", target.getInt("LifeZ"));
            record.putString("LifeTargetDimension", target.getString("LifeDimension"));
        } else if (profile.contains("FactionId", Tag.TAG_STRING) && !profile.getString("FactionId").isBlank()) {
            WorldFaction faction = FactionManager.byId(player.serverLevel(), profile.getString("FactionId"));
            if (faction != null && faction.realm() == FactionRealm.byId(record.getInt("LifeRealm"))) {
                record.putInt("LifeTargetX", faction.roamX());
                record.putInt("LifeTargetZ", faction.roamZ());
                destination = faction.name();
            }
        }

        moveOneDay(player, record, profile);
        record.putString("LifeActivity", activity);
        record.putString("LifeDestination", destination);

        // Training is causal. Aggregate the explicit training/meditation blocks that the same
        // remembered daily plan actually contains instead of describing a heavy training day but
        // paying only one arbitrary simulated session. This remains bounded to six effort blocks.
        int routineTrainingBlocks = FighterDailyRoutineManager.rememberedTrainingBlocks(player, profile, record, tick);
        if (routineTrainingBlocks > 0 || isTrainingActivity(goal, activity)) {
            int effortBlocks = Math.max(1, routineTrainingBlocks);
            applyProfileTraining(player, profile, record, tick,
                    effortBlocks >= 3 ? "Completed a heavy off-screen training day" : "Completed off-screen training",
                    effortBlocks);
        }

        // A known rival can produce an actual off-screen rematch only after the two simulated
        // lives have physically converged. Cooldowns prevent the pair from resolving twice.
        if ("DEFEAT_RIVAL".equals(goal) && target != null && sameLifeDimension(record, target)
                && lifeDistanceSqr(record, target) <= 128.0D * 128.0D
                && tick >= record.getLong("NextOffscreenDuel") && tick >= target.getLong("NextOffscreenDuel")) {
            resolveOffscreenRivalry(player, record, target, profile, tick);
        }

        // The remembered schedule changes tomorrow's needs too; off-screen life is not a reset button.
        FighterLifeNeedsManager.simulateRememberedDay(profile, record.hasUUID("RecordId") ? record.getUUID("RecordId") : null);

        record.put("Profile", profile);
    }

    private static boolean isTrainingActivity(String goal, String activity) {
        return "TRAIN".equals(goal) || "ADVANCE_RACIAL".equals(goal) || "LEARN_FLIGHT".equals(goal)
                || "LEARN_TECHNIQUE".equals(goal) || "Training".equals(activity)
                || "Racial training".equals(activity) || "Flight training".equals(activity);
    }

    private static void applyProfileTraining(ServerPlayer player, CompoundTag profile, CompoundTag record, long tick, String event) {
        applyProfileTraining(player, profile, record, tick, event, 1);
    }

    private static void applyProfileTraining(ServerPlayer player, CompoundTag profile, CompoundTag record, long tick, String event, int effortBlocks) {
        int blocks = Math.max(1, Math.min(6, effortBlocks));
        int priorSessions = Math.max(0, profile.getInt("TrainingSessions"));
        int sessions = priorSessions + blocks;
        profile.putInt("TrainingSessions", sessions);
        CompoundTag legacy = profile.contains("Legacy", Tag.TAG_COMPOUND) ? profile.getCompound("Legacy").copy() : new CompoundTag();
        int oldPower = profile.contains("PermanentBattlePower") ? Math.max(1, profile.getInt("PermanentBattlePower"))
                : Math.max(1, legacy.contains("LWPermanentBattlePower") ? legacy.getInt("LWPermanentBattlePower") : profile.getInt("BattlePower"));
        double playerPower = WorldPowerScaler.activePlayerPowerPressure(player.serverLevel(), player.blockPosition(), player);
        int rivalry = Math.max(0, record.getInt("BattlesVsPlayer"));
        int relationship = record.getInt("Relationship");
        // The same medium soft-relevance curve used by loaded fighters applies here. Power is
        // still earned only by a simulated training day; no BP is copied from or removed for the player.
        UUID identity = record != null && record.hasUUID("RecordId") ? record.getUUID("RecordId") : null;
        double potential = FighterPotentialManager.potentialFromProfile(profile, identity);
        // potentialFromProfile may seed the Legacy compound on an old record; re-read it so the
        // later profile.put cannot accidentally overwrite that newly persisted trait.
        legacy = profile.contains("Legacy", Tag.TAG_COMPOUND) ? profile.getCompound("Legacy").copy() : legacy;
        double relevance = WorldPowerScaler.earnedGrowthMultiplier(playerPower, oldPower, rivalry, relationship, potential);
        double diminishing = 1.0D / (1.0D + priorSessions * 0.018D);
        // Each explicit planned effort block contributes a bounded amount. Three+ blocks now
        // produce a visible multi-block training day while preserving diminishing returns/ceiling.
        // R41 simulation parity: an unloaded fighter only receives this payment for explicit
        // development blocks in their persisted daily plan. Loaded fighters can complete several
        // real sessions while a distant profile gets one daily simulation settlement, so the old
        // per-block budget left long-range residents noticeably behind. This factor closes that
        // simulation-frequency gap without inventing training or copying player power.
        double powerRatio = Math.max(1.0D, playerPower / (double)Math.max(1L, oldPower));
        double gapSteps = Math.max(0.0D, Math.log(powerRatio) / Math.log(4.0D));
        double offscreenParity = Math.min(6.0D, 1.85D + gapSteps * 1.05D);
        double gain = 0.0062D * blocks * relevance * diminishing * offscreenParity
                * (0.90D + player.getRandom().nextDouble() * 0.20D) * LivingWorldConfig.npcGrowthScale();
        FighterRank rank = FighterRank.byId(profile.getInt("Rank"));
        double earnedCeiling = WorldPowerScaler.earnedProgressionCeiling(player.serverLevel(), player.blockPosition(),
                rank, playerPower, rivalry, relationship, potential);
        long grown = gain <= 0.0D ? oldPower : Math.max(oldPower + 1L, Math.round(oldPower * (1.0D + gain)));
        int nextPower = (int)Math.min(Integer.MAX_VALUE - 1L, Math.min(grown, Math.max(oldPower, Math.round(earnedCeiling))));
        profile.putInt("BattlePower", nextPower);
        profile.putInt("PermanentBattlePower", nextPower);
        legacy.putInt("LWPermanentBattlePower", nextPower);
        if (nextPower > oldPower) legacy.putInt("LWEarnedBattlePowerFloor", Math.max(nextPower, legacy.getInt("LWEarnedBattlePowerFloor")));
        // Off-screen training must develop the same underlying conditioning and racial tree as
        // loaded training; otherwise a remembered person can pursue racial training forever.
        double growth = Math.max(0.0D, legacy.getDouble("LWCombatGrowth"));
        legacy.putDouble("LWCombatGrowth", Math.min(0.35D, growth
                + 0.00135D * blocks * relevance * offscreenParity * LivingWorldConfig.npcGrowthScale()));
        profile.put("Legacy", legacy);
        int racialSkill = Math.max(0, profile.getInt("RacialSkillLevel"));
        if (rank != FighterRank.ROOKIE && racialSkill < RacialFormProfile.maxSkillLevel(com.dmzlivingworld.entity.FighterRace.byId(profile.getInt("Race")))) {
            int racialEffort = Math.max(1, (int)Math.floor(7.0D * blocks * LivingWorldConfig.npcGrowthScale()));
            int progress = Math.max(0, profile.getInt("RacialTrainingProgress")) + racialEffort;
            var race = com.dmzlivingworld.entity.FighterRace.byId(profile.getInt("Race"));
            int next = RacialFormProfile.nextUnlockLevel(race, racialSkill);
            int recordHash = record.hasUUID("RecordId") ? record.getUUID("RecordId").hashCode() : profile.getString("Name").hashCode();
            int threshold = 95 + next * 42 + Math.floorMod(recordHash, 45);
            if (next > racialSkill && progress >= threshold) {
                progress -= threshold;
                profile.putInt("RacialSkillLevel", next);
                RacialFormProfile unlocked = RacialFormProfile.forSkill(race, next);
                if (unlocked != null) {
                    event = "Unlocked " + unlocked.displayName() + " through off-screen training";
                    appendProfileEvent(profile, event, tick);
                }
            }
            profile.putInt("RacialTrainingProgress", Math.max(0, progress));
        }

        if (!profile.getBoolean("FlightUnlocked")) {
            int recordHash = record.hasUUID("RecordId") ? record.getUUID("RecordId").hashCode() : profile.getString("Name").hashCode();
            int threshold = 3 + Math.floorMod(recordHash, 5);
            if (sessions >= threshold) {
                profile.putBoolean("FlightUnlocked", true);
                appendProfileEvent(profile, "Learned flight through training", tick);
                event = "Learned flight while training";
            }
        }
        record.putString("LastLifeEvent", event);
        record.putLong("LastLifeEventTick", tick);
        if (sessions == 5 || sessions == 15 || sessions == 30) appendProfileEvent(profile, "Completed " + sessions + " training sessions", tick);
    }

    private static void resolveOffscreenRivalry(ServerPlayer player, CompoundTag self, CompoundTag other,
                                                 CompoundTag selfProfile, long tick) {
        CompoundTag otherProfile = other.getCompound("Profile").copy();
        CompoundTag selfLegacy = selfProfile.getCompound("Legacy");
        CompoundTag otherLegacy = otherProfile.contains("Legacy", Tag.TAG_COMPOUND) ? otherProfile.getCompound("Legacy").copy() : new CompoundTag();

        int a = Math.max(1, selfProfile.getInt("BattlePower"));
        int b = Math.max(1, otherProfile.getInt("BattlePower"));
        double chance = Math.max(0.20D, Math.min(0.80D, a / (double)(a + b)));
        boolean selfWon = player.getRandom().nextDouble() < chance;

        noteProfileBattle(selfLegacy, selfWon);
        noteProfileBattle(otherLegacy, !selfWon);
        otherProfile.put("Legacy", otherLegacy);

        String aName = selfProfile.getString("Name");
        String bName = otherProfile.getString("Name");
        String winner = selfWon ? aName : bName;
        String loser = selfWon ? bName : aName;
        String event = winner + " defeated " + loser + " in an off-screen rival rematch";
        appendProfileEvent(selfProfile, event, tick);
        appendProfileEvent(otherProfile, event, tick);
        self.putString("LastLifeEvent", event);
        other.putString("LastLifeEvent", event);
        self.putLong("LastLifeEventTick", tick);
        other.putLong("LastLifeEventTick", tick);
        self.putLong("NextOffscreenDuel", tick + 72_000L);
        other.putLong("NextOffscreenDuel", tick + 72_000L);

        // Both participants learned from a real fight; the loser gets a little more stimulus.
        applyProfileTraining(player, selfProfile, self, tick, selfWon ? "Won a rival rematch" : "Learned from a rival defeat");
        applyProfileTraining(player, otherProfile, other, tick, selfWon ? "Learned from a rival defeat" : "Won a rival rematch");
        other.put("Profile", otherProfile);
    }

    private static void noteProfileBattle(CompoundTag legacy, boolean won) {
        legacy.putInt("Fights", legacy.getInt("Fights") + 1);
        if (won) legacy.putInt("Wins", legacy.getInt("Wins") + 1);
        else legacy.putInt("Losses", legacy.getInt("Losses") + 1);
    }

    private static void appendProfileEvent(CompoundTag profile, String text, long tick) {
        if (text == null || text.isBlank()) return;
        String safeText = text.length() > 160 ? text.substring(0, 160) : text;
        CompoundTag legacy = profile.contains("Legacy", Tag.TAG_COMPOUND) ? profile.getCompound("Legacy").copy() : new CompoundTag();
        ListTag events = legacy.getList("Events", Tag.TAG_STRING);
        if (events.isEmpty() || !events.getString(events.size() - 1).equals(safeText)) events.add(net.minecraft.nbt.StringTag.valueOf(safeText));
        while (events.size() > 40) events.remove(0);
        legacy.put("Events", events);

        ListTag timeline = legacy.getList("Timeline", Tag.TAG_COMPOUND);
        CompoundTag row = new CompoundTag();
        row.putLong("Tick", Math.max(0L, tick));
        row.putString("Text", safeText);
        timeline.add(row);
        while (timeline.size() > 48) timeline.remove(0);
        legacy.put("Timeline", timeline);
        profile.put("Legacy", legacy);
    }

    private static void moveOneDay(ServerPlayer player, CompoundTag record, CompoundTag profile) {
        if (!record.contains("LifeX")) {
            record.putInt("LifeX", player.blockPosition().getX());
            record.putInt("LifeY", player.blockPosition().getY());
            record.putInt("LifeZ", player.blockPosition().getZ());
            record.putString("LifeDimension", player.level().dimension().location().toString());
            record.putInt("LifeRealm", LivingWorldDimensions.realm(player.serverLevel()).id());
        }
        String targetDim = record.getString("LifeTargetDimension");
        boolean hasTarget = record.contains("LifeTargetX") && record.contains("LifeTargetZ")
                && (targetDim.isBlank() || targetDim.equals(record.getString("LifeDimension")));
        double step = profile.getBoolean("FlightUnlocked") ? 620.0D : 310.0D;
        int x = record.getInt("LifeX");
        int z = record.getInt("LifeZ");

        if (hasTarget) {
            double dx = record.getInt("LifeTargetX") - x;
            double dz = record.getInt("LifeTargetZ") - z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len <= step || len < 1.0D) {
                x = record.getInt("LifeTargetX");
                z = record.getInt("LifeTargetZ");
            } else {
                x += (int)Math.round(dx / len * step);
                z += (int)Math.round(dz / len * step);
            }
        } else if (player.getRandom().nextFloat() < 0.55F) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
            double roam = 90.0D + player.getRandom().nextDouble() * (profile.getBoolean("FlightUnlocked") ? 360.0D : 180.0D);
            x += (int)Math.round(Math.cos(angle) * roam);
            z += (int)Math.round(Math.sin(angle) * roam);
        }
        record.putInt("LifeX", x);
        record.putInt("LifeZ", z);
    }

    private static String chooseDevelopmentActivity(ServerPlayer player, CompoundTag profile, CompoundTag record, String planned) {
        if (player == null || profile == null) return planned;
        CompoundTag legacy = profile.contains("Legacy", Tag.TAG_COMPOUND) ? profile.getCompound("Legacy") : new CompoundTag();
        String goal = legacy.getString("GoalType");
        if (isTrainingActivity(goal, planned)) return planned;
        // Concrete personal missions remain authoritative instead of being overwritten by catch-up.
        if (!goal.isBlank() && !"WIN_FIGHTS".equals(goal) && !"DEFEAT_STRONGER".equals(goal)) return planned;
        double own = Math.max(1.0D, profile.contains("PermanentBattlePower")
                ? profile.getInt("PermanentBattlePower") : profile.getInt("BattlePower"));
        double playerPower = WorldPowerScaler.activePlayerPowerPressure(player.serverLevel(), player.blockPosition(), player);
        UUID identity = record != null && record.hasUUID("RecordId") ? record.getUUID("RecordId") : null;
        double potential = FighterPotentialManager.potentialFromProfile(profile, identity);
        float chance = WorldPowerScaler.developmentTrainingChance(playerPower, own,
                Math.max(0, record.getInt("BattlesVsPlayer")), record.getInt("Relationship"), potential);
        if ("WIN_FIGHTS".equals(goal) || "DEFEAT_STRONGER".equals(goal)) chance *= 0.65F;
        return player.getRandom().nextFloat() < chance ? "Training" : planned;
    }

    private static String activityForProfile(CompoundTag profile, CompoundTag record) {
        CompoundTag legacy = profile.contains("Legacy", Tag.TAG_COMPOUND) ? profile.getCompound("Legacy") : new CompoundTag();
        return switch (legacy.getString("GoalType")) {
            case "DEFEAT_RIVAL" -> "Seeking rival";
            case "LEARN_TECHNIQUE" -> "Technique practice";
            case "ACQUIRE_EQUIPMENT" -> "Searching for equipment";
            case "ADVANCE_RACIAL" -> "Racial training";
            case "LEARN_FLIGHT" -> "Flight training";
            case "WIN_FIGHTS", "DEFEAT_STRONGER" -> "Looking for opponents";
            case "FUSION" -> "Seeking fusion partner";
            case "TRAIN" -> "Training";
            default -> profile.contains("FactionId", Tag.TAG_STRING) && !profile.getString("FactionId").isBlank()
                    ? "Faction duty" : "Travelling";
        };
    }

    private static CompoundTag recordByName(ListTag list, String name, int skipIndex) {
        if (name == null || name.isBlank()) return null;
        for (int i = 0; i < list.size(); i++) {
            if (i == skipIndex) continue;
            CompoundTag row = list.getCompound(i);
            if (name.equals(row.getCompound("Profile").getString("Name"))) return row;
        }
        return null;
    }

    private static boolean sameLifeDimension(CompoundTag a, CompoundTag b) {
        String ad = a.getString("LifeDimension");
        String bd = b.getString("LifeDimension");
        return !ad.isBlank() && ad.equals(bd);
    }

    private static double lifeDistanceSqr(CompoundTag a, CompoundTag b) {
        double dx = a.getInt("LifeX") - b.getInt("LifeX");
        double dz = a.getInt("LifeZ") - b.getInt("LifeZ");
        return dx * dx + dz * dz;
    }

    private static boolean isActuallyNearby(ServerPlayer player, CompoundTag record) {
        if (!record.contains("LifeX") || !record.contains("LifeZ")) return true; // migrate old 1.2 records safely
        String dim = record.getString("LifeDimension");
        if (!dim.isBlank() && !dim.equals(player.level().dimension().location().toString())) return false;
        double dx = record.getInt("LifeX") - player.getX();
        double dz = record.getInt("LifeZ") - player.getZ();
        return dx * dx + dz * dz <= (double)LivingWorldConfig.livingPresenceRadius() * LivingWorldConfig.livingPresenceRadius();
    }

    /** Called only when the physical approach has actually reached the player-visible area. */
    public static void notePhysicalArrival(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || fighter.getMemoryRecordId() == null) return;
        CompoundTag root = getRoot(player);
        ListTag list = root.getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, fighter.getMemoryRecordId());
        if (index < 0) return;
        CompoundTag record = list.getCompound(index);
        long now = player.getServer().overworld().getGameTime();
        record.putInt("Encounters", Math.max(record.getInt("Encounters") + 1, fighter.getMemoryEncounters()));
        record.putLong("LastSeen", now);
        captureSeenProfile(record, player, fighter);
        String ambient = FighterAmbientActivityManager.currentActivity(fighter);
        recordWhereabouts(record, fighter, now, ambient.isBlank() ? activityForProfile(record.getCompound("Profile"), record) : ambient);
        list.set(index, record);
        root.put(RIVALS_KEY, list);
        saveRoot(player, root);
    }

    public static BlockPos continuityTarget(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || fighter.getMemoryRecordId() == null) return null;
        CompoundTag root = getRoot(player);
        ListTag list = root.getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, fighter.getMemoryRecordId());
        if (index < 0) return null;
        CompoundTag record = list.getCompound(index);
        if (record.contains("LifeTargetX") && record.contains("LifeTargetZ")) {
            int y = record.contains("LifeY") ? record.getInt("LifeY") : fighter.blockPosition().getY();
            return new BlockPos(record.getInt("LifeTargetX"), y, record.getInt("LifeTargetZ"));
        }
        if (record.contains("LifeX") && record.contains("LifeZ")) {
            int y = record.contains("LifeY") ? record.getInt("LifeY") : fighter.blockPosition().getY();
            return new BlockPos(record.getInt("LifeX"), y, record.getInt("LifeZ"));
        }
        return null;
    }

    public static String continuityActivity(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || fighter.getMemoryRecordId() == null) return "";
        CompoundTag root = getRoot(player);
        ListTag list = root.getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, fighter.getMemoryRecordId());
        if (index < 0) return "";
        return list.getCompound(index).getString("LifeActivity");
    }

    public static void notePhysicalDeparture(ServerPlayer player, AmbientFighterEntity fighter, BlockPos destination) {
        if (player == null || fighter == null || fighter.getMemoryRecordId() == null) return;
        CompoundTag root = getRoot(player);
        ListTag list = root.getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        int index = findRecordIndex(list, fighter.getMemoryRecordId());
        if (index < 0) return;
        CompoundTag record = list.getCompound(index);
        long now = player.getServer().overworld().getGameTime();
        record.put("Profile", fighter.writeMemoryProfile());
        // The body is already outside observation range here. Update only the simulation state;
        // do not leak its departure destination into the player's last-known whereabouts.
        recordInternalWhereabouts(record, fighter, now, "Travelling");
        if (destination != null) {
            record.putInt("LifeTargetX", destination.getX());
            record.putInt("LifeTargetZ", destination.getZ());
            record.putString("LifeTargetDimension", fighter.level().dimension().location().toString());
        }
        record.putLong("LastLifeTick", now);
        list.set(index, record);
        root.put(RIVALS_KEY, list);
        saveRoot(player, root);
    }

    /** Internal simulation uses Profile; player-facing People/Member pages use SeenProfile. */
    private static CompoundTag seenProfile(CompoundTag record) {
        if (record == null) return new CompoundTag();
        if (record.contains("SeenProfile", Tag.TAG_COMPOUND)) return record.getCompound("SeenProfile").copy();
        return record.contains("Profile", Tag.TAG_COMPOUND) ? record.getCompound("Profile").copy() : new CompoundTag();
    }

    private static void captureSeenProfile(CompoundTag record, ServerPlayer player, AmbientFighterEntity fighter) {
        captureSeenProfile(record, player, fighter, fighter.writeMemoryProfile());
    }

    private static void captureSeenProfile(CompoundTag record, ServerPlayer player, AmbientFighterEntity fighter, CompoundTag profile) {
        if (record == null || fighter == null || profile == null) return;
        record.put("Profile", profile.copy());
        record.put("SeenProfile", profile.copy());
        if (player != null) {
            FighterRelationshipManager.Disposition disposition = FighterRelationshipManager.disposition(player, fighter);
            record.putInt("SeenDisposition", disposition.id());
            record.putString("SeenAttitude", FighterRelationshipManager.attitudeReason(player, fighter));
        }
    }

    private static void recordInternalWhereabouts(CompoundTag record, AmbientFighterEntity fighter, long now, String activity) {
        int x = fighter.blockPosition().getX();
        int y = fighter.blockPosition().getY();
        int z = fighter.blockPosition().getZ();
        String dimension = fighter.level().dimension().location().toString();
        int realm = fighter.level() instanceof ServerLevel level ? LivingWorldDimensions.realm(level).id() : record.getInt("LifeRealm");
        record.putInt("LifeX", x);
        record.putInt("LifeY", y);
        record.putInt("LifeZ", z);
        record.putUUID("LifeEntityUUID", fighter.getUUID());
        record.putString("LifeDimension", dimension);
        record.putInt("LifeRealm", realm);
        record.putLong("LastLifeTick", Math.max(record.getLong("LastLifeTick"), now));
        if (activity != null && !activity.isBlank()) record.putString("LifeActivity", activity);
    }

    private static void recordWhereabouts(CompoundTag record, AmbientFighterEntity fighter, long now, String activity) {
        int x = fighter.blockPosition().getX();
        int y = fighter.blockPosition().getY();
        int z = fighter.blockPosition().getZ();
        String dimension = fighter.level().dimension().location().toString();
        int realm = fighter.level() instanceof ServerLevel level ? LivingWorldDimensions.realm(level).id() : record.getInt("LifeRealm");

        // Internal life state drives simulation. Seen* is intentionally separate so the
        // directory never becomes an omniscient off-screen tracker.
        record.putInt("LifeX", x);
        record.putInt("LifeY", y);
        record.putInt("LifeZ", z);
        record.putUUID("LifeEntityUUID", fighter.getUUID());
        record.putString("LifeDimension", dimension);
        record.putInt("LifeRealm", realm);
        record.putInt("SeenX", x);
        record.putInt("SeenY", y);
        record.putInt("SeenZ", z);
        record.putString("SeenDimension", dimension);
        record.putInt("SeenRealm", realm);
        record.putLong("LastLifeTick", Math.max(record.getLong("LastLifeTick"), now));
        if (activity != null && !activity.isBlank()) {
            record.putString("LifeActivity", activity);
            record.putString("SeenActivity", activity);
        }
        long eventTick = record.getLong("LastLifeEventTick");
        if (eventTick > record.getLong("LastKnownLifeEventTick") && !record.getString("LastLifeEvent").isBlank()) {
            record.putString("KnownLifeEvent", record.getString("LastLifeEvent"));
            record.putLong("LastKnownLifeEventTick", eventTick);
        }
    }

    private static String approximateKnownWhereabouts(CompoundTag record) {
        if (!record.contains("SeenX") || !record.contains("SeenZ")) return approximateWhereabouts(record);
        String realm = FactionRealm.byId(record.contains("SeenRealm") ? record.getInt("SeenRealm") : record.getInt("LifeRealm")).displayName();
        int x = record.getInt("SeenX");
        int z = record.getInt("SeenZ");
        double dist = Math.sqrt((double)x * x + (double)z * z);
        if (dist < 420.0D) return "central " + realm;
        double angle = Math.atan2(-z, x);
        String[] dirs = {"east", "north-east", "north", "north-west", "west", "south-west", "south", "south-east"};
        int idx = Math.floorMod((int)Math.round(angle / (Math.PI / 4.0D)), 8);
        String band = dist > 3500.0D ? "far " : "";
        return band + dirs[idx] + " " + realm;
    }

    private static String approximateWhereabouts(CompoundTag record) {
        if (!record.contains("LifeX") || !record.contains("LifeZ")) return "unknown";
        String realm = FactionRealm.byId(record.getInt("LifeRealm")).displayName();
        int x = record.getInt("LifeX");
        int z = record.getInt("LifeZ");
        double dist = Math.sqrt((double)x * x + (double)z * z);
        if (dist < 420.0D) return "central " + realm;
        double angle = Math.atan2(-z, x);
        String[] dirs = {"east", "north-east", "north", "north-west", "west", "south-west", "south", "south-east"};
        int idx = Math.floorMod((int)Math.round(angle / (Math.PI / 4.0D)), 8);
        String band = dist > 3500.0D ? "far " : "";
        return band + dirs[idx] + " " + realm;
    }

    private static void noteRivalryEncounter(ServerPlayer player, AmbientFighterEntity fighter, CompoundTag record, boolean fighterWon) {
        if (player == null || fighter == null || record == null || fighter.getAlignment() != FighterAlignment.BAD) return;
        int battles = Math.max(0, record.getInt("BattlesVsPlayer")) + 1;
        int wins = Math.max(0, record.getInt("WinsVsPlayer")) + (fighterWon ? 1 : 0);
        record.putInt("BattlesVsPlayer", battles);
        record.putInt("WinsVsPlayer", wins);
        fighter.setPlayerRivalState(player.getUUID(), player.getGameProfile().getName(), battles, wins);
        captureSeenProfile(record, player, fighter);
        saveRecord(player, record);
    }

    private static void syncRivalryState(ServerPlayer player, AmbientFighterEntity fighter, CompoundTag record) {
        if (player == null || fighter == null || record == null) return;
        int battles = Math.max(0, record.getInt("BattlesVsPlayer"));
        if (battles <= 0) return;
        fighter.setPlayerRivalState(player.getUUID(), player.getGameProfile().getName(), battles,
                Math.max(0, record.getInt("WinsVsPlayer")));
    }

    private static int findRecordIndex(ListTag list, UUID recordId) {
        if (recordId == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag record = list.getCompound(i);
            if (record.hasUUID("RecordId") && recordId.equals(record.getUUID("RecordId"))) return i;
        }
        return -1;
    }

    private static int oldestRecordIndex(ListTag list) {
        int index = 0;
        long oldest = Long.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            long seen = list.getCompound(i).getLong("LastSeen");
            if (seen < oldest) {
                oldest = seen;
                index = i;
            }
        }
        return index;
    }

    private static CompoundTag getRoot(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(ROOT_KEY, Tag.TAG_COMPOUND)) persistent.put(ROOT_KEY, new CompoundTag());
        CompoundTag root = persistent.getCompound(ROOT_KEY);
        sanitizeRoot(root);
        purgeDeadRecords(player, root);
        return root;
    }

    private static void purgeDeadRecords(ServerPlayer player, CompoundTag root) {
        if (!(player.level() instanceof ServerLevel level)) return;
        FighterLegacyWorldData legacy = FighterLegacyWorldData.get(level);
        ListTag list = root.getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            CompoundTag record = list.getCompound(i);
            if (record.hasUUID("RecordId") && legacy.isDeadRecord(record.getUUID("RecordId"))) {
                list.remove(i);
                changed = true;
            }
        }
        if (changed) {
            root.put(RIVALS_KEY, list);
            player.getPersistentData().put(ROOT_KEY, root);
        }
    }

    private static void sanitizeRoot(CompoundTag root) {
        if (!root.contains(RIVALS_KEY, Tag.TAG_LIST)) root.put(RIVALS_KEY, new ListTag());
        ListTag source = root.getList(RIVALS_KEY, Tag.TAG_COMPOUND);
        ListTag clean = new ListTag();
        int start = Math.max(0, source.size() - MAX_RECORDS);
        for (int i = start; i < source.size(); i++) {
            CompoundTag r = source.getCompound(i).copy();
            // R14 migration: old builds could persist Herobrine as an ordinary person/bond.
            // His encounter evidence now belongs exclusively to WorldMenaceManager, so discard
            // that obsolete social record rather than merely hiding it in the People UI.
            CompoundTag existingProfile = seenProfile(r);
            if (hiddenFromPeople(existingProfile)) continue;
            if (!r.hasUUID("RecordId")) r.putUUID("RecordId", UUID.randomUUID());
            r.putInt("Relationship", clamp(r.getInt("Relationship"), -100, 100));
            r.putInt("Encounters", Math.max(1, r.getInt("Encounters")));
            r.putInt("BattlesVsPlayer", Math.max(0, r.getInt("BattlesVsPlayer")));
            r.putInt("WinsVsPlayer", Math.max(0, r.getInt("WinsVsPlayer")));
            r.remove("NemesisStage");
            sanitizeRememberedProfile(r);
            if (!r.contains("SeenProfile", Tag.TAG_COMPOUND) && r.contains("Profile", Tag.TAG_COMPOUND))
                r.put("SeenProfile", r.getCompound("Profile").copy());
            r.putLong("LastSeen", Math.max(0L, r.getLong("LastSeen")));
            if (!r.contains("FirstSeen", Tag.TAG_ANY_NUMERIC)) r.putLong("FirstSeen", r.getLong("LastSeen"));
            r.putLong("FirstSeen", Math.max(0L, r.getLong("FirstSeen")));
            r.putLong("NextEligible", Math.max(0L, r.getLong("NextEligible")));
            r.putLong("LastLifeTick", Math.max(0L, r.getLong("LastLifeTick")));
            r.putLong("LastLifeEventTick", Math.max(0L, r.getLong("LastLifeEventTick")));
            r.putLong("LastKnownLifeEventTick", Math.max(0L, r.getLong("LastKnownLifeEventTick")));
            r.putLong("NextOffscreenDuel", Math.max(0L, r.getLong("NextOffscreenDuel")));
            if (!r.contains("LifeRealm")) r.putInt("LifeRealm", FactionRealm.EARTH.id());
            if (r.getString("LifeActivity").length() > 80) r.putString("LifeActivity", r.getString("LifeActivity").substring(0, 80));
            if (r.getString("LifeDestination").length() > 96) r.putString("LifeDestination", r.getString("LifeDestination").substring(0, 96));
            if (r.getString("SeenActivity").length() > 80) r.putString("SeenActivity", r.getString("SeenActivity").substring(0, 80));
            if (r.getString("LastLifeEvent").length() > 160) r.putString("LastLifeEvent", r.getString("LastLifeEvent").substring(0, 160));
            if (r.getString("KnownLifeEvent").length() > 160) r.putString("KnownLifeEvent", r.getString("KnownLifeEvent").substring(0, 160));
            if (r.contains("LastOutcome", Tag.TAG_STRING) && r.getString("LastOutcome").length() > 160)
                r.putString("LastOutcome", r.getString("LastOutcome").substring(0, 160));
            clean.add(r);
        }
        root.put(RIVALS_KEY, clean);
        root.putInt("DataVersion", MEMORY_DATA_VERSION);
    }

    /** Migrates retired rivalry-stage fields even while a remembered fighter is off-screen. */
    private static void sanitizeRememberedProfile(CompoundTag record) {
        if (record == null || !record.contains("Profile", Tag.TAG_COMPOUND)) return;
        CompoundTag profile = record.getCompound("Profile").copy();
        CompoundTag legacy = profile.contains("Legacy", Tag.TAG_COMPOUND)
                ? profile.getCompound("Legacy").copy() : new CompoundTag();

        int battles = Math.max(record.getInt("BattlesVsPlayer"),
                Math.max(legacy.getInt("PlayerRivalBattles"), legacy.getInt("NemesisBattles")));
        int wins = Math.max(record.getInt("WinsVsPlayer"),
                Math.max(legacy.getInt("PlayerRivalWins"), legacy.getInt("NemesisWins")));
        if (battles > 0) legacy.putInt("PlayerRivalBattles", battles);
        if (wins > 0) legacy.putInt("PlayerRivalWins", wins);
        if (!legacy.contains("PlayerRivalName") && legacy.contains("NemesisName"))
            legacy.putString("PlayerRivalName", legacy.getString("NemesisName"));
        if (!legacy.contains("PlayerRival") && legacy.hasUUID("NemesisPlayer"))
            legacy.putUUID("PlayerRival", legacy.getUUID("NemesisPlayer"));
        legacy.remove("NemesisStage");
        legacy.remove("NemesisBattles");
        legacy.remove("NemesisWins");
        legacy.remove("NemesisName");
        legacy.remove("NemesisPlayer");
        // R42 retires the unfinished master/student system completely. Strip stale relationship
        // identity/locks from remembered profiles too so old saves cannot resurrect it in UI or simulation.
        legacy.remove("MentorStudentLocked");
        legacy.remove("NativeMentor");
        profile.remove("MentorName");
        profile.remove("LWMentorName");
        profile.put("Legacy", legacy);

        boolean organicArchrival = record.getInt("Relationship") <= -70 && battles >= 3;
        if (legacy.getBoolean("AntagonistRecognized") && !legacy.getString("AntagonistEpithet").isBlank())
            profile.putString("LegacyTitle", legacy.getString("AntagonistEpithet"));
        else if (organicArchrival)
            profile.putString("LegacyTitle", "Archrival");
        else if ("Archrival".equals(profile.getString("LegacyTitle")))
            profile.putString("LegacyTitle", "");
        record.put("Profile", profile);
        if (record.contains("SeenProfile", Tag.TAG_COMPOUND)) {
            CompoundTag seen = record.getCompound("SeenProfile").copy();
            CompoundTag seenLegacy = seen.contains("Legacy", Tag.TAG_COMPOUND) ? seen.getCompound("Legacy").copy() : new CompoundTag();
            if (battles > 0) seenLegacy.putInt("PlayerRivalBattles", battles);
            if (wins > 0) seenLegacy.putInt("PlayerRivalWins", wins);
            if (!seenLegacy.contains("PlayerRivalName") && seenLegacy.contains("NemesisName"))
                seenLegacy.putString("PlayerRivalName", seenLegacy.getString("NemesisName"));
            if (!seenLegacy.contains("PlayerRival") && seenLegacy.hasUUID("NemesisPlayer"))
                seenLegacy.putUUID("PlayerRival", seenLegacy.getUUID("NemesisPlayer"));
            seenLegacy.remove("NemesisStage"); seenLegacy.remove("NemesisBattles"); seenLegacy.remove("NemesisWins");
            seenLegacy.remove("NemesisName"); seenLegacy.remove("NemesisPlayer");
            seenLegacy.remove("MentorStudentLocked"); seenLegacy.remove("NativeMentor");
            seen.remove("MentorName"); seen.remove("LWMentorName");
            seen.put("Legacy", seenLegacy);
            if (seenLegacy.getBoolean("AntagonistRecognized") && !seenLegacy.getString("AntagonistEpithet").isBlank())
                seen.putString("LegacyTitle", seenLegacy.getString("AntagonistEpithet"));
            else if (organicArchrival) seen.putString("LegacyTitle", "Archrival");
            else if ("Archrival".equals(seen.getString("LegacyTitle"))) seen.putString("LegacyTitle", "");
            record.put("SeenProfile", seen);
        }
    }

    private static void saveRoot(ServerPlayer player, CompoundTag root) {
        player.getPersistentData().put(ROOT_KEY, root);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
