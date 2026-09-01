package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Shared mission-role layer for faction requests.
 *
 * Requests never manufacture actors here: a mission can only reserve an already-manifested,
 * persistent member from the faction ResidentRoster. The selected UUID stays fixed for the
 * operation. Yield, withdrawal and death therefore have meaning instead of triggering refills.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FactionRequestMissionManager {
    public static final String ROSTER_PREFIX = "MissionRoster.";
    private static final String ASSIGNED = "LWFactionRequestAssigned";
    private static final String OWNER = "LWFactionRequestOwner";
    private static final String REQUEST = "LWFactionRequestId";
    private static final String TYPE = "LWFactionRequestType";
    private static final String SIDE = "LWFactionRequestSide";
    private static final String ROLE = "LWFactionRequestRole";
    private static final String YIELDED = "LWFactionRequestYielded";
    private static final String RETREATED = "LWFactionRequestRetreated";
    private static final String LAST_CHAT = "LWFactionRequestLastChat";
    private static final String NEXT_TARGET_SCAN = "LWFactionRequestNextTargetScan";
    private static final String NAV_LAST_CHECK = "LWRequestNavLastCheck";
    private static final String NAV_LAST_DISTANCE = "LWRequestNavLastDistance";
    private static final String NAV_STUCK_TICKS = "LWRequestNavStuckTicks";
    private static final String NAV_AUTO_FLIGHT = "LWRequestNavAutoFlight";
    private static final String NAV_DETOUR_UNTIL = "LWRequestNavDetourUntil";

    // Runtime-only indexes. They never define identity; UUIDs in request/player/entity NBT remain authoritative.
    // These caches remove two catastrophic hot paths from R28: synchronous 3x3 chunk waking and giant
    // per-NPC world scans every tick.
    private static final Map<UUID, Long> NEXT_RESIDENT_WAKE = new HashMap<>();
    private static final Map<UUID, Set<UUID>> REQUEST_PARTICIPANTS = new HashMap<>();

    public static final String SIDE_ALLY = "ALLY";
    public static final String SIDE_ENEMY = "ENEMY";
    public static final String SIDE_NEUTRAL = "NEUTRAL";

    public static final String ROLE_COMBAT = "COMBAT";
    public static final String ROLE_PATROL = "PATROL";
    public static final String ROLE_RECEIVER = "RECEIVER";
    public static final String ROLE_TRAINEE = "TRAINEE";
    public static final String ROLE_CONTACT = "CONTACT";
    public static final String ROLE_OBSERVER = "OBSERVER";
    public static final String ROLE_OPERATIVE = "OPERATIVE";
    public static final String ROLE_PROTECTED = "PROTECTED";
    public static final String ROLE_CAPTURE_TARGET = "CAPTURE_TARGET";
    public static final String ROLE_CAPTIVE = "CAPTIVE";

    private FactionRequestMissionManager() {}

    public static UUID requestId(CompoundTag req) {
        if (req == null) return new UUID(0L, 0L);
        if (!req.hasUUID("RequestId")) req.putUUID("RequestId", UUID.randomUUID());
        return req.getUUID("RequestId");
    }

    public static boolean isAssigned(AmbientFighterEntity fighter) {
        return fighter != null && fighter.getPersistentData().getBoolean(ASSIGNED);
    }

    /**
     * Every active request assignment owns the participant's ordinary life/social intent until the
     * request releases that exact persistent fighter. Supply receivers still continue physically
     * existing and moving normally; exclusivity only prevents unrelated activities/interactions from
     * stealing them away from the accepted request. Deliver Supplies remains explicitly allowed.
     */
    public static boolean isExclusiveFieldAssignment(AmbientFighterEntity fighter) {
        return isAssigned(fighter);
    }

    /** Player-facing profile/action lock for any exact fighter currently committed to a live request. */
    public static boolean isRequestActionLocked(AmbientFighterEntity fighter) {
        return isAssigned(fighter);
    }

    public static boolean isYielded(AmbientFighterEntity fighter) {
        return fighter != null && fighter.getPersistentData().getBoolean(YIELDED);
    }

    public static boolean isRetreated(AmbientFighterEntity fighter) {
        return fighter != null && fighter.getPersistentData().getBoolean(RETREATED);
    }

    public static String missionRole(AmbientFighterEntity fighter) {
        return fighter == null ? "" : fighter.getPersistentData().getString(ROLE);
    }

    public static String missionSide(AmbientFighterEntity fighter) {
        return fighter == null ? "" : fighter.getPersistentData().getString(SIDE);
    }

    /** True only when this exact persistent fighter belongs to this exact request instance. */
    public static boolean belongsToRequest(AmbientFighterEntity fighter, CompoundTag req) {
        if (fighter == null || req == null || !isAssigned(fighter) || !fighter.getPersistentData().hasUUID(REQUEST)) return false;
        return requestId(req).equals(fighter.getPersistentData().getUUID(REQUEST));
    }

    /** Mission ownership is authoritative: unrelated ambient/faction AI cannot overwrite request combat roles. */
    public static boolean allowsMissionTarget(AmbientFighterEntity fighter, LivingEntity target) {
        if (fighter == null || target == null || !isAssigned(fighter)) return true;
        CompoundTag pd = fighter.getPersistentData();
        if (isYielded(fighter) || isRetreated(fighter)) return false;
        String role = pd.getString(ROLE);
        if (ROLE_RECEIVER.equals(role) || ROLE_CONTACT.equals(role) || ROLE_OPERATIVE.equals(role) || ROLE_CAPTIVE.equals(role)) return false;
        if (ROLE_TRAINEE.equals(role)) return fighter.isSanctionedMatchParticipant() && fighter.isSanctionedOpponent(target);
        if (ROLE_OBSERVER.equals(role) && !pd.getBoolean("LWRequestAlerted")) return false;
        if (!pd.hasUUID(REQUEST)) return false;
        UUID requestId = pd.getUUID(REQUEST);
        String ownSide = pd.getString(SIDE);
        if (target instanceof AmbientFighterEntity other) {
            CompoundTag od = other.getPersistentData();
            if (!isAssigned(other) || !od.hasUUID(REQUEST) || !requestId.equals(od.getUUID(REQUEST))) return false;
            return (SIDE_ALLY.equals(ownSide) && SIDE_ENEMY.equals(od.getString(SIDE)))
                    || (SIDE_ENEMY.equals(ownSide) && SIDE_ALLY.equals(od.getString(SIDE)));
        }
        if (target instanceof ServerPlayer player && SIDE_ENEMY.equals(ownSide) && pd.hasUUID(OWNER))
            return player.getUUID().equals(pd.getUUID(OWNER));
        return false;
    }

    /** Commit one already-loaded real resident to an existing mission roster without spawning, teleporting, or replacing anybody.
     * Patrol uses this for genuine route contacts: the resident has to physically exist near the patrol first. */
    public static boolean commitLoadedResident(ServerPlayer player, CompoundTag req, AmbientFighterEntity fighter,
                                               String rosterName, String side, String role) {
        if (player == null || req == null || fighter == null || rosterName == null || !fighter.isAlive()) return false;
        CompoundTag pd = fighter.getPersistentData();
        UUID request = requestId(req);
        if (pd.getBoolean(ASSIGNED) && (!pd.hasUUID(REQUEST) || !request.equals(pd.getUUID(REQUEST)))) return false;
        String key = rosterKey(rosterName);
        ListTag list = req.getList(key, Tag.TAG_STRING);
        String id = fighter.getUUID().toString();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.getString(i))) { found = true; break; }
        }
        if (!found) { list.add(StringTag.valueOf(id)); req.put(key, list); }
        assign(player, req, fighter, side, role);
        return true;
    }

    public static boolean rosterContains(CompoundTag req, String rosterName, UUID fighterId) {
        if (req == null || rosterName == null || fighterId == null) return false;
        ListTag list = req.getList(rosterKey(rosterName), Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            UUID id = parseUuid(list.getString(i));
            if (fighterId.equals(id)) return true;
        }
        return false;
    }

    public static void recordOutcome(CompoundTag req, UUID fighterId, boolean yielded) {
        if (req == null || fighterId == null) return;
        String key = yielded ? "MissionYielded" : "MissionRetreated";
        ListTag list = req.getList(key, Tag.TAG_STRING);
        String id = fighterId.toString();
        for (int i = 0; i < list.size(); i++) if (id.equals(list.getString(i))) return;
        list.add(StringTag.valueOf(id)); req.put(key, list);
    }

    public static boolean outcomeRecorded(CompoundTag req, UUID fighterId) {
        if (req == null || fighterId == null) return false;
        String id = fighterId.toString();
        for (String key : new String[]{"MissionYielded", "MissionRetreated"}) {
            ListTag list = req.getList(key, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) if (id.equals(list.getString(i))) return true;
        }
        return false;
    }

    public static int yieldedCount(CompoundTag req) { return req == null ? 0 : req.getList("MissionYielded", Tag.TAG_STRING).size(); }
    public static int retreatedCount(CompoundTag req) { return req == null ? 0 : req.getList("MissionRetreated", Tag.TAG_STRING).size(); }

    /** Releases one mission roster without ending the whole request (used between serial stages). */
    public static void releaseRoster(ServerLevel level, CompoundTag req, String rosterName) {
        if (level == null || req == null || rosterName == null) return;
        UUID expected = req.hasUUID("RequestId") ? req.getUUID("RequestId") : null;
        ListTag list = req.getList(rosterKey(rosterName), Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            UUID id = parseUuid(list.getString(i));
            if (id != null && level.getEntity(id) instanceof AmbientFighterEntity fighter) release(fighter, expected);
        }
        req.remove(rosterKey(rosterName));
        req.remove(rosterKey(rosterName) + ".Locked");
    }

    public static boolean isRealResident(ServerLevel level, WorldFaction faction, AmbientFighterEntity fighter) {
        if (level == null || faction == null || fighter == null || !fighter.isAlive() || !fighter.isRegionalPresence()) return false;
        if (!fighter.isFactionMember() || !faction.id().equals(fighter.getFactionId())) return false;
        return FactionWorldData.get(level).residents(faction).stream()
                .anyMatch(r -> !r.fallen() && !r.departed() && r.entityId().equals(fighter.getUUID()));
    }

    public static List<AmbientFighterEntity> loadedAvailableResidents(ServerLevel level, WorldFaction faction,
                                                                       Predicate<AmbientFighterEntity> filter) {
        if (level == null || faction == null) return List.of();
        List<AmbientFighterEntity> out = new ArrayList<>();
        for (FactionWorldData.ResidentRecord record : FactionWorldData.get(level).residents(faction)) {
            if (record.fallen() || record.departed()) continue;
            if (!(level.getEntity(record.entityId()) instanceof AmbientFighterEntity fighter) || !fighter.isAlive()) continue;
            if (!isRealResident(level, faction, fighter)) continue;
            if (isAssigned(fighter)) continue;
            if (filter != null && !filter.test(fighter)) continue;
            out.add(fighter);
        }
        out.sort(Comparator.comparingInt((AmbientFighterEntity f) -> f.getFactionRole().id()).reversed()
                .thenComparingInt(f -> f.getRank().id()).reversed());
        return out;
    }


    /** Persistent roster record for an exact real faction member, loaded or unloaded. */
    public static FactionWorldData.ResidentRecord residentRecord(ServerLevel level, WorldFaction faction, UUID id) {
        if (level == null || faction == null || id == null) return null;
        for (FactionWorldData.ResidentRecord record : FactionWorldData.get(level).residents(faction)) {
            if (id.equals(record.entityId())) return record;
        }
        return null;
    }

    /**
     * Tries to load the chunk containing an already-existing resident and recover that exact entity.
     * This never creates a fighter. It only wakes a persistent resident whose UUID is already in the faction roster.
     */
    public static AmbientFighterEntity materializeExistingResident(ServerLevel level, WorldFaction faction, UUID id) {
        if (level == null || faction == null || id == null) return null;
        if (level.getEntity(id) instanceof AmbientFighterEntity loaded && loaded.isAlive()
                && isRealResident(level, faction, loaded)) return loaded;
        FactionWorldData.ResidentRecord record = residentRecord(level, faction, id);
        if (record == null || record.fallen() || record.departed()) return null;

        int cx = record.x() >> 4, cz = record.z() >> 4;
        // First inspect already-loaded chunks only. This path is cheap and is safe to call from mission simulation.
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (level.getChunkSource().getChunkNow(cx + dx, cz + dz) == null) continue;
            if (level.getEntity(id) instanceof AmbientFighterEntity fighter && fighter.isAlive()
                    && isRealResident(level, faction, fighter)) return fighter;
        }

        // R29: never synchronously wake a 3x3 chunk grid every second (or, previously, from the HUD four times/sec).
        // At most one saved resident chunk is synchronously woken every five seconds for this UUID. If the roster
        // record was one chunk stale, the neighbour checks above pick it up once normal player travel loads it.
        long now = level.getServer().overworld().getGameTime();
        long next = NEXT_RESIDENT_WAKE.getOrDefault(id, Long.MIN_VALUE);
        if (now < next) return null;
        NEXT_RESIDENT_WAKE.put(id, now + 100L);
        level.getChunk(cx, cz);
        if (level.getEntity(id) instanceof AmbientFighterEntity fighter && fighter.isAlive()
                && isRealResident(level, faction, fighter)) return fighter;
        return null;
    }

    /** Cheap loaded-only lookup. HUD/render snapshots must use this and never wake chunks. */
    public static AmbientFighterEntity loadedResident(ServerLevel level, WorldFaction faction, UUID id) {
        if (level == null || faction == null || id == null) return null;
        if (level.getEntity(id) instanceof AmbientFighterEntity fighter && fighter.isAlive()
                && isRealResident(level, faction, fighter)) return fighter;
        return null;
    }

    /** Last recorded position of a real resident, useful for precise mission navigation while its chunk is unloaded. */
    public static BlockPos residentLastPos(ServerLevel level, WorldFaction faction, UUID id) {
        FactionWorldData.ResidentRecord record = residentRecord(level, faction, id);
        return record == null ? null : new BlockPos(record.x(), record.y(), record.z());
    }

    /** Name from the persistent faction roster even if the entity is not currently loaded. */
    public static String residentName(ServerLevel level, WorldFaction faction, UUID id) {
        FactionWorldData.ResidentRecord record = residentRecord(level, faction, id);
        return record == null ? "" : record.name();
    }

    public static AmbientFighterEntity reserveOne(ServerPlayer player, ServerLevel level, CompoundTag req,
                                                   WorldFaction faction, String rosterName, BlockPos anchor,
                                                   String side, String role, Predicate<AmbientFighterEntity> filter) {
        List<AmbientFighterEntity> roster = ensureRoster(player, level, req, faction, rosterName, 1, anchor, side, role, filter);
        return roster.isEmpty() ? null : roster.get(0);
    }

    public static List<AmbientFighterEntity> ensureRoster(ServerPlayer player, ServerLevel level, CompoundTag req,
                                                           WorldFaction faction, String rosterName, int desired,
                                                           BlockPos anchor, String side, String role,
                                                           Predicate<AmbientFighterEntity> filter) {
        if (player == null || level == null || req == null || faction == null || rosterName == null || desired <= 0) return List.of();
        String key = rosterKey(rosterName);
        UUID requestId = requestId(req);
        ListTag list = req.getList(key, Tag.TAG_STRING);
        List<AmbientFighterEntity> loaded = new ArrayList<>();
        Set<UUID> chosen = new HashSet<>();
        boolean locked = req.getBoolean(key + ".Locked");
        boolean nearAnchor = anchor == null || player.distanceToSqr(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D) <= 190.0D * 190.0D;

        // Recover exact members already committed. A missing physical entity does NOT cause an immediate refill.
        for (int i = list.size() - 1; i >= 0; i--) {
            UUID id = parseUuid(list.getString(i));
            if (id == null) { list.remove(i); continue; }
            FactionWorldData.ResidentRecord record = residentRecord(level, faction, id);
            if (record == null || record.fallen() || record.departed()) {
                // Before lock, a dead/stale reservation was never truly committed and can be dropped. After lock,
                // the UUID remains in the fixed roster so neutralizedCount records the real outcome.
                if (!locked) list.remove(i);
                continue;
            }
            chosen.add(id);
            AmbientFighterEntity fighter = loadedResident(level, faction, id);
            if (fighter == null && nearAnchor) fighter = materializeExistingResident(level, faction, id);
            if (fighter == null) continue;
            if (filter != null && !filter.test(fighter) && !locked) {
                list.remove(i); chosen.remove(id); continue;
            }
            assign(player, req, fighter, side, role);
            moveIntoOperation(player, level, fighter, anchor);
            loaded.add(fighter);
        }

        if (!locked && list.size() < desired) {
            // Prefer already-loaded real residents. This is both smoother and vastly cheaper than force-loading a
            // whole faction population merely because a quest was accepted.
            for (AmbientFighterEntity candidate : loadedAvailableResidents(level, faction, filter)) {
                if (list.size() >= desired) break;
                if (chosen.contains(candidate.getUUID())) continue;
                CompoundTag pd = candidate.getPersistentData();
                if (pd.getBoolean(ASSIGNED) && (!pd.hasUUID(REQUEST) || !requestId.equals(pd.getUUID(REQUEST)))) continue;
                list.add(StringTag.valueOf(candidate.getUUID().toString()));
                chosen.add(candidate.getUUID());
                assign(player, req, candidate, side, role);
                moveIntoOperation(player, level, candidate, anchor);
                loaded.add(candidate);
            }

            // If nobody suitable is loaded and the player has actually reached the mission area, try to wake ONE
            // exact persistent resident per simulation tick. Do not pre-commit a pile of unloaded UUIDs: that was
            // what made R28 rosters hostage to missing actors and created 45-second withdrawals.
            if (list.size() < desired && nearAnchor) {
                List<FactionWorldData.ResidentRecord> records = new ArrayList<>(FactionWorldData.get(level).residents(faction));
                records.removeIf(r -> r.fallen() || r.departed() || chosen.contains(r.entityId()));
                records.sort(Comparator
                        .comparingInt((FactionWorldData.ResidentRecord r) -> r.role().id()).reversed()
                        .thenComparingInt(r -> r.rank().id()).reversed());
                Set<UUID> reservedElsewhere = reservedByOtherMissionRosters(req, key);
                for (FactionWorldData.ResidentRecord record : records) {
                    if (reservedElsewhere.contains(record.entityId()) || !recordSuitableForRoster(req, rosterName, record)) continue;
                    AmbientFighterEntity candidate = materializeExistingResident(level, faction, record.entityId());
                    // Only a physically recovered real resident becomes part of the operation roster.
                    if (candidate != null && candidate.isAlive() && !isAssigned(candidate)
                            && (filter == null || filter.test(candidate))) {
                        list.add(StringTag.valueOf(candidate.getUUID().toString()));
                        chosen.add(candidate.getUUID());
                        assign(player, req, candidate, side, role);
                        moveIntoOperation(player, level, candidate, anchor);
                        loaded.add(candidate);
                    }
                    break; // at most one synchronous resident-recovery attempt per roster/simulation tick
                }
            }
            req.put(key, list);
        }
        return List.copyOf(loaded);
    }


    private static Set<UUID> reservedByOtherMissionRosters(CompoundTag req, String currentKey) {
        Set<UUID> out = new HashSet<>();
        if (req == null) return out;
        for (String k : req.getAllKeys()) {
            if (!k.startsWith(ROSTER_PREFIX) || k.equals(currentKey) || k.endsWith(".Locked")) continue;
            ListTag list = req.getList(k, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                UUID id = parseUuid(list.getString(i)); if (id != null) out.add(id);
            }
        }
        return out;
    }

    private static boolean recordSuitableForRoster(CompoundTag req, String rosterName, FactionWorldData.ResidentRecord record) {
        if (record == null || record.fallen() || record.departed()) return false;
        if ("SupplyReceiver".equals(rosterName) || "IntelContact".equals(rosterName) || "IntelMissionGiver".equals(rosterName)) return true;
        if ("HuntTarget".equals(rosterName)) return false; // named/rank-specific contract uses the loaded entity predicate.
        if (record.nonCombatant()) return false;
        if ("CaptureTarget".equals(rosterName) && req != null && "ELITE_CAPTURE".equals(req.getString("Type")))
            return record.role().id() >= FactionRole.ENFORCER.id();
        return true;
    }

    public static void lockRoster(CompoundTag req, String rosterName) {
        if (req != null) req.putBoolean(rosterKey(rosterName) + ".Locked", true);
    }

    public static int rosterSize(CompoundTag req, String rosterName) {
        return req == null ? 0 : req.getList(rosterKey(rosterName), Tag.TAG_STRING).size();
    }

    public static boolean rosterLocked(CompoundTag req, String rosterName) {
        return req != null && rosterName != null && req.getBoolean(rosterKey(rosterName) + ".Locked");
    }

    /** Before a mission starts, lock only the real residents that actually assembled. No absent UUID is treated as a casualty. */
    public static int trimUnlockedRosterToLoaded(ServerLevel level, CompoundTag req, String rosterName) {
        if (level == null || req == null || rosterName == null || rosterLocked(req, rosterName)) return rosterSize(req, rosterName);
        String key = rosterKey(rosterName);
        ListTag old = req.getList(key, Tag.TAG_STRING);
        ListTag kept = new ListTag();
        for (int i = 0; i < old.size(); i++) {
            UUID id = parseUuid(old.getString(i));
            if (id != null && level.getEntity(id) instanceof AmbientFighterEntity fighter && fighter.isAlive())
                kept.add(StringTag.valueOf(id.toString()));
        }
        req.put(key, kept);
        return kept.size();
    }

    public static List<AmbientFighterEntity> loadedRoster(ServerLevel level, CompoundTag req, String rosterName) {
        if (level == null || req == null) return List.of();
        ListTag list = req.getList(rosterKey(rosterName), Tag.TAG_STRING);
        List<AmbientFighterEntity> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            UUID id = parseUuid(list.getString(i));
            if (id != null && level.getEntity(id) instanceof AmbientFighterEntity fighter && fighter.isAlive()) out.add(fighter);
        }
        return List.copyOf(out);
    }

    /** Loaded members who are still able and willing to perform the mission role. */
    public static List<AmbientFighterEntity> loadedActiveRoster(ServerLevel level, CompoundTag req, String rosterName) {
        return loadedRoster(level, req, rosterName).stream()
                .filter(f -> !isYielded(f) && !isRetreated(f) && !f.isDefeated() && !f.isCaptive())
                .toList();
    }

    public static UUID rosterId(CompoundTag req, String rosterName, int index) {
        if (req == null || rosterName == null || index < 0) return null;
        ListTag list = req.getList(rosterKey(rosterName), Tag.TAG_STRING);
        return index >= list.size() ? null : parseUuid(list.getString(index));
    }

    public static boolean residentFallenOrDeparted(ServerLevel level, WorldFaction faction, UUID id) {
        if (level == null || faction == null || id == null) return false;
        return FactionWorldData.get(level).residents(faction).stream()
                .anyMatch(r -> r.entityId().equals(id) && (r.fallen() || r.departed()));
    }

    public static int fallenOrDepartedCount(ServerLevel level, CompoundTag req, WorldFaction faction, String rosterName) {
        if (level == null || req == null || faction == null) return 0;
        int count = 0;
        ListTag list = req.getList(rosterKey(rosterName), Tag.TAG_STRING);
        List<FactionWorldData.ResidentRecord> records = FactionWorldData.get(level).residents(faction);
        for (int i = 0; i < list.size(); i++) {
            UUID id = parseUuid(list.getString(i));
            if (id != null && records.stream().anyMatch(r -> r.entityId().equals(id) && (r.fallen() || r.departed()))) count++;
        }
        return count;
    }

    public static AmbientFighterEntity firstLoaded(ServerLevel level, CompoundTag req, String rosterName) {
        List<AmbientFighterEntity> fighters = loadedRoster(level, req, rosterName);
        return fighters.isEmpty() ? null : fighters.get(0);
    }

    public static boolean rosterNeutralized(ServerLevel level, CompoundTag req, WorldFaction faction, String rosterName) {
        int size = rosterSize(req, rosterName);
        return size > 0 && neutralizedCount(level, req, faction, rosterName) >= size;
    }

    public static int neutralizedCount(ServerLevel level, CompoundTag req, WorldFaction faction, String rosterName) {
        if (level == null || req == null) return 0;
        int neutralized = 0;
        ListTag list = req.getList(rosterKey(rosterName), Tag.TAG_STRING);
        List<FactionWorldData.ResidentRecord> records = faction == null ? List.of() : FactionWorldData.get(level).residents(faction);
        for (int i = 0; i < list.size(); i++) {
            UUID id = parseUuid(list.getString(i));
            if (id == null) { neutralized++; continue; }
            if (outcomeRecorded(req, id)) { neutralized++; continue; }
            if (level.getEntity(id) instanceof AmbientFighterEntity fighter) {
                if (!fighter.isAlive() || isYielded(fighter) || isRetreated(fighter) || fighter.isDefeated() || fighter.isCaptive()) neutralized++;
                continue;
            }
            boolean noLongerAvailable = records.stream().anyMatch(r -> r.entityId().equals(id) && (r.fallen() || r.departed()));
            if (noLongerAvailable) neutralized++;
        }
        return neutralized;
    }

    /** Marks every surviving member of one fixed roster as having withdrawn without deleting them. */
    public static void withdrawRoster(ServerLevel level, CompoundTag req, String rosterName) {
        if (level == null || req == null || rosterName == null) return;
        for (AmbientFighterEntity fighter : loadedRoster(level, req, rosterName)) {
            if (isYielded(fighter) || fighter.isDefeated() || fighter.isCaptive()) continue;
            fighter.getPersistentData().putBoolean(RETREATED, true); recordOutcome(req, fighter.getUUID(), false);
            fighter.setTarget(null); fighter.getNavigation().stop();
            fighter.setStoryRole(AmbientFighterEntity.STORY_NONE);
        }
    }

    /** Breaks morale once roughly two thirds of a fixed force is neutralized. No refill occurs. */
    public static boolean applyMoraleBreak(ServerLevel level, CompoundTag req, WorldFaction faction, String rosterName) {
        int size = rosterSize(req, rosterName);
        if (size <= 1) return false;
        int down = neutralizedCount(level, req, faction, rosterName);
        if (down * 3 < size * 2) return false;
        boolean changed = false;
        for (AmbientFighterEntity fighter : loadedRoster(level, req, rosterName)) {
            if (isYielded(fighter) || isRetreated(fighter) || fighter.isDefeated() || fighter.isCaptive()) continue;
            fighter.getPersistentData().putBoolean(RETREATED, true); recordOutcome(req, fighter.getUUID(), false);
            fighter.setTarget(null);
            fighter.getNavigation().stop();
            fighter.setStoryRole(AmbientFighterEntity.STORY_NONE);
            fighter.speak(FactionRequestDialogue.yield(req.getString("Type"), fighter.getUUID().getLeastSignificantBits()), 70);
            changed = true;
        }
        return changed;
    }

    public static void assign(ServerPlayer player, CompoundTag req, AmbientFighterEntity fighter, String side, String role) {
        if (player == null || req == null || fighter == null) return;
        CompoundTag pd = fighter.getPersistentData();
        UUID currentRequest = requestId(req);
        boolean sameAssignment = pd.getBoolean(ASSIGNED) && pd.hasUUID(REQUEST) && currentRequest.equals(pd.getUUID(REQUEST));
        pd.putBoolean(ASSIGNED, true);
        pd.putUUID(OWNER, player.getUUID());
        pd.putUUID(REQUEST, currentRequest);
        pd.putString(TYPE, req.getString("Type"));
        pd.putString(SIDE, side == null ? SIDE_NEUTRAL : side);
        pd.putString(ROLE, role == null ? ROLE_COMBAT : role);
        if (!sameAssignment) {
            pd.remove(RETREATED); pd.putBoolean(YIELDED, false); pd.remove("LWRequestAlerted"); pd.remove(LAST_CHAT);
        }
        fighter.setPersistenceRequired();
        if (isYielded(fighter)) fighter.setStoryRole(AmbientFighterEntity.STORY_CAPTIVE);
        else if (isRetreated(fighter)) fighter.setStoryRole(AmbientFighterEntity.STORY_NONE);
        else if (SIDE_ENEMY.equals(side)) fighter.setStoryRole(AmbientFighterEntity.STORY_ENEMY);
        else if (ROLE_CAPTIVE.equals(role)) fighter.setStoryRole(AmbientFighterEntity.STORY_CAPTIVE);
        else fighter.setStoryRole(AmbientFighterEntity.STORY_ALLY);
        suppressLife(fighter);
        REQUEST_PARTICIPANTS.computeIfAbsent(currentRequest, k -> new HashSet<>()).add(fighter.getUUID());
    }

    public static void releaseRequestParticipants(ServerPlayer player, CompoundTag req) {
        if (player == null || req == null || !(player.level() instanceof ServerLevel level)) return;
        UUID requestId = req.hasUUID("RequestId") ? req.getUUID("RequestId") : null;
        for (String key : req.getAllKeys()) {
            if (!key.startsWith(ROSTER_PREFIX)) continue;
            ListTag list = req.getList(key, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                UUID id = parseUuid(list.getString(i));
                if (id != null && level.getEntity(id) instanceof AmbientFighterEntity fighter) release(fighter, requestId);
            }
        }
        // Unloaded participants self-release when they next tick, but the runtime target index belongs only to the
        // live request. Drop it immediately so completed/abandoned operations cannot accumulate stale UUID sets.
        if (requestId != null) REQUEST_PARTICIPANTS.remove(requestId);
    }

    public static void release(AmbientFighterEntity fighter, UUID expectedRequest) {
        if (fighter == null) return;
        CompoundTag pd = fighter.getPersistentData();
        if (!pd.getBoolean(ASSIGNED)) return;
        UUID actualRequest = pd.hasUUID(REQUEST) ? pd.getUUID(REQUEST) : null;
        if (expectedRequest != null && actualRequest != null && !expectedRequest.equals(actualRequest)) return;
        if (actualRequest != null) {
            Set<UUID> ids = REQUEST_PARTICIPANTS.get(actualRequest);
            if (ids != null) {
                ids.remove(fighter.getUUID());
                if (ids.isEmpty()) REQUEST_PARTICIPANTS.remove(actualRequest);
            }
        }
        pd.remove(ASSIGNED); pd.remove(OWNER); pd.remove(REQUEST); pd.remove(TYPE); pd.remove(SIDE); pd.remove(ROLE);
        pd.remove(YIELDED); pd.remove(RETREATED); pd.remove(LAST_CHAT); pd.remove("LWRequestAlerted"); pd.remove(NEXT_TARGET_SCAN);
        pd.remove(NAV_LAST_CHECK); pd.remove(NAV_LAST_DISTANCE); pd.remove(NAV_STUCK_TICKS);
        pd.remove(NAV_AUTO_FLIGHT); pd.remove(NAV_DETOUR_UNTIL);
        fighter.finishFactionRequestAssignment();
        FighterAmbientActivityManager.cancelFor(fighter);
        FighterNpcSocialManager.cancelFor(fighter);
        FighterDailyRoutineManager.invalidatePlan(fighter);
        FighterAmbientActivityManager.nudgeSoon(fighter);
    }


    /** Called from the fighter tick. Returns true when the fighter is mission-assigned. */
    public static boolean enforceMissionState(AmbientFighterEntity fighter) {
        if (!isAssigned(fighter) || !(fighter.level() instanceof ServerLevel level)) return false;
        CompoundTag pd = fighter.getPersistentData();
        ServerPlayer owner = !pd.hasUUID(OWNER) ? null : level.getServer().getPlayerList().getPlayer(pd.getUUID(OWNER));
        UUID assignedRequest = pd.hasUUID(REQUEST) ? pd.getUUID(REQUEST) : null;
        if (owner == null || assignedRequest == null || !FactionRequestManager.isActiveRequestId(owner, assignedRequest)) {
            release(fighter, assignedRequest);
            return false;
        }
        REQUEST_PARTICIPANTS.computeIfAbsent(assignedRequest, k -> new HashSet<>()).add(fighter.getUUID());
        suppressLife(fighter);
        if (isYielded(fighter) || isRetreated(fighter) || ROLE_CAPTIVE.equals(pd.getString(ROLE))) {
            fighter.setTarget(null);
            fighter.getNavigation().stop();
            return true;
        }
        String role = pd.getString(ROLE);
        if (ROLE_RECEIVER.equals(role) || ROLE_CONTACT.equals(role)) {
            // Request code owns their rendezvous path. Do not let ordinary life/combat acquire targets, but do not
            // cancel the mission navigation that is bringing the real receiver/contact to the marked handoff.
            fighter.setTarget(null);
        } else if (ROLE_TRAINEE.equals(role)) {
            // The request owns ordinary life, not the sanctioned spar itself.
            if (!fighter.isSanctionedMatchParticipant()) {
                fighter.setTarget(null);
                fighter.getNavigation().stop();
            }
        } else if (ROLE_OPERATIVE.equals(role)) {
            // Extraction code owns movement. Keep the operative out of autonomous fights without
            // stopping the navigation path that is physically taking the same real person home.
            fighter.setTarget(null);
        } else if (ROLE_PROTECTED.equals(role)) {
            // A protected officer is still a fighter: they can defend themselves and the formation.
            driveCombat(fighter, level, pd);
        } else if (ROLE_OBSERVER.equals(role)) {
            if (pd.getBoolean("LWRequestAlerted")) driveCombat(fighter, level, pd);
            else {
                // Stealth-scene code owns the observer's post/rotation path. Do not let ambient combat target the
                // player before detection, but preserve the mission navigation that places this real resident.
                fighter.setTarget(null);
            }
        } else if (ROLE_COMBAT.equals(role) || ROLE_PATROL.equals(role) || ROLE_CAPTURE_TARGET.equals(role)) {
            driveCombat(fighter, level, pd);
        }
        maybeMissionChatter(fighter, level, pd);
        return true;
    }

    private static void suppressLife(AmbientFighterEntity fighter) {
        if (fighter == null) return;
        if (FighterAmbientActivityManager.isActive(fighter)) FighterAmbientActivityManager.cancelFor(fighter);
        FighterNpcSocialManager.cancelFor(fighter);
        if (fighter.isMeditating()) fighter.stopMeditation(false);
    }

    private static void driveCombat(AmbientFighterEntity fighter, ServerLevel level, CompoundTag pd) {
        if (!pd.hasUUID(REQUEST)) return;
        UUID request = pd.getUUID(REQUEST);
        String ownSide = pd.getString(SIDE);
        if (!(SIDE_ALLY.equals(ownSide) || SIDE_ENEMY.equals(ownSide))) return;

        LivingEntity current = fighter.getTarget();
        if (current != null && current.isAlive() && allowsMissionTarget(fighter, current)) return;
        if (current != null) fighter.setTarget(null);

        long now = level.getServer().overworld().getGameTime();
        long next = pd.getLong(NEXT_TARGET_SCAN);
        if (next > now) return;
        // Stagger exact-roster target acquisition to roughly twice per second. R28 scanned a huge world AABB for
        // every mission fighter every tick, which could stall the entire integrated server and make vanilla mobs lag.
        pd.putLong(NEXT_TARGET_SCAN, now + 8L + Math.floorMod(fighter.getUUID().hashCode(), 7));

        String opposite = SIDE_ALLY.equals(ownSide) ? SIDE_ENEMY : SIDE_ALLY;
        AmbientFighterEntity npcTarget = null;
        double best = Double.MAX_VALUE;
        Set<UUID> indexed = REQUEST_PARTICIPANTS.get(request);
        if (indexed != null) {
            for (UUID id : List.copyOf(indexed)) {
                if (id.equals(fighter.getUUID())) continue;
                if (!(level.getEntity(id) instanceof AmbientFighterEntity other) || !other.isAlive()) continue;
                CompoundTag od = other.getPersistentData();
                if (!isAssigned(other) || isYielded(other) || isRetreated(other) || !od.hasUUID(REQUEST)
                        || !request.equals(od.getUUID(REQUEST)) || !opposite.equals(od.getString(SIDE))) continue;
                double d = fighter.distanceToSqr(other);
                if (d < best) { best = d; npcTarget = other; }
            }
        }

        LivingEntity target = npcTarget;
        if (SIDE_ENEMY.equals(ownSide) && pd.hasUUID(OWNER)) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(pd.getUUID(OWNER));
            if (owner != null && !owner.isCreative() && !owner.isSpectator() && fighter.distanceToSqr(owner) <= 96.0D * 96.0D) {
                if (npcTarget == null || ((fighter.getUUID().getLeastSignificantBits() & 1L) == 0L)) target = owner;
            }
        }
        if (target != null && target.isAlive()) fighter.setTarget(target);
    }


    private static void maybeMissionChatter(AmbientFighterEntity fighter, ServerLevel level, CompoundTag pd) {
        long now = level.getServer().overworld().getGameTime();
        long last = pd.getLong(LAST_CHAT);
        String type = pd.getString(TYPE);
        String role = pd.getString(ROLE);
        boolean patrolTravel = "PATROL".equals(type) && ROLE_PATROL.equals(role)
                && (fighter.getTarget() == null || !fighter.getTarget().isAlive());
        long interval = patrolTravel
                ? 1200L + Math.floorMod(fighter.getUUID().hashCode(), 900) // sparse, mission-only route chatter
                : 900L + Math.floorMod(fighter.getUUID().hashCode(), 700);
        if (last > 0L && now - last < interval) return;
        if (patrolTravel) {
            pd.putLong(LAST_CHAT, now);
            fighter.speak(FactionMissionFlavor.patrolTravel(now ^ fighter.getUUID().getLeastSignificantBits()), 60);
            return;
        }
        if (fighter.getTarget() == null || !fighter.getTarget().isAlive()) return;
        pd.putLong(LAST_CHAT, now);
        fighter.speak(FactionRequestDialogue.pressure(type, now ^ fighter.getUUID().getLeastSignificantBits()), 60);
    }

    /**
     * Shared request travel primitive. Current requests use this instead of raw vanilla navigation,
     * and future field requests should use it too. It detects lack of forward progress and water
     * directly in the route. Flight-capable fighters temporarily take a real DMZ aerial detour;
     * grounded fighters choose a dry progress waypoint rather than vibrating on the same wet block.
     */
    public static void navigateMissionActor(AmbientFighterEntity fighter, Vec3 destination,
                                            double groundSpeed, long now) {
        if (fighter == null || destination == null || !(fighter.level() instanceof ServerLevel level)
                || !fighter.isAlive()) return;
        // A finished ambush can leave DMZ's target reference pointing at an entity that is already
        // dead. Request movement used to treat any non-null reference as live combat and would then
        // refuse to navigate forever, while the profile correctly showed no active fight. Clear only
        // that stale combat reference; a genuinely live target still owns the fighter as before.
        LivingEntity combatTarget = fighter.getTarget();
        if (combatTarget != null) {
            if (combatTarget.isAlive()) return;
            fighter.setTarget(null);
        }
        CompoundTag pd = fighter.getPersistentData();
        double distance = fighter.position().distanceTo(destination);
        boolean waterHazard = fighter.isInWaterOrBubble() || missionWaterAhead(level, fighter.position(), destination);
        boolean solidHazard = missionSolidAhead(level, fighter, destination);

        long lastCheck = pd.getLong(NAV_LAST_CHECK);
        if (lastCheck <= 0L || now - lastCheck >= 20L) {
            double previous = pd.contains(NAV_LAST_DISTANCE, Tag.TAG_DOUBLE) ? pd.getDouble(NAV_LAST_DISTANCE) : distance + 2.0D;
            int stuck = pd.getInt(NAV_STUCK_TICKS);
            // Less than ~0.7 blocks of progress per second while a meaningful distance away is a stall.
            if (distance > 5.0D && previous - distance < 0.70D) stuck = Math.min(160, stuck + 20);
            else stuck = Math.max(0, stuck - 40);
            pd.putInt(NAV_STUCK_TICKS, stuck);
            pd.putDouble(NAV_LAST_DISTANCE, distance);
            pd.putLong(NAV_LAST_CHECK, now);
        }

        int stuckTicks = pd.getInt(NAV_STUCK_TICKS);
        boolean canFly = fighter.hasFlightUnlocked() && !fighter.isNonCombatant();
        boolean autoFlight = pd.getBoolean(NAV_AUTO_FLIGHT);
        if (canFly && (autoFlight || waterHazard || solidHazard || stuckTicks >= 40)) {
            pd.putBoolean(NAV_AUTO_FLIGHT, true);
            fighter.getNavigation().stop();
            fighter.setCanFly(true);
            fighter.setFlying(true);
            fighter.setNoGravity(true);
            boolean arriving = distance <= 4.2D;
            fighter.setFlyingFast(!arriving && distance > 26.0D);
            Vec3 airTarget = arriving ? destination.add(0.0D, 0.20D, 0.0D)
                    : destination.add(0.0D, Math.min(4.0D, 2.4D + distance * 0.025D), 0.0D);
            fighter.steerAmbientFlightToward(airTarget, arriving ? 0.30D : 0.62D);
            if ((fighter.onGround() || distance <= 1.25D) && !missionWaterAt(level, fighter.blockPosition())) {
                pd.putBoolean(NAV_AUTO_FLIGHT, false);
                pd.putInt(NAV_STUCK_TICKS, 0);
                fighter.setFlyingFast(false);
                fighter.setFlying(false);
                fighter.setNoGravity(false);
                fighter.setDeltaMovement(fighter.getDeltaMovement().scale(0.25D));
            }
            return;
        }

        // A non-flier that has stalled at water gets a dry lateral waypoint. Reuse it briefly so
        // vanilla pathfinding has time to commit instead of selecting a new direction every tick.
        if (!canFly && (waterHazard || stuckTicks >= 40) && now >= pd.getLong(NAV_DETOUR_UNTIL)) {
            BlockPos detour = dryProgressDetour(level, fighter, destination);
            if (detour != null) {
                pd.putLong(NAV_DETOUR_UNTIL, now + 80L);
                fighter.getNavigation().moveTo(detour.getX() + 0.5D, detour.getY(), detour.getZ() + 0.5D,
                        Math.max(0.72D, groundSpeed));
                return;
            }
        }
        if (now % 20L == Math.floorMod(fighter.getId(), 20) || fighter.getNavigation().isDone())
            fighter.getNavigation().moveTo(destination.x, destination.y, destination.z, groundSpeed);
    }

    public static void navigateMissionActor(AmbientFighterEntity fighter, BlockPos destination,
                                            double groundSpeed, long now) {
        if (destination != null) navigateMissionActor(fighter,
                new Vec3(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D), groundSpeed, now);
    }

    private static boolean missionWaterAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        return level.getFluidState(pos).is(FluidTags.WATER) || level.getFluidState(pos.below()).is(FluidTags.WATER);
    }

    private static boolean missionWaterAhead(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 flat = new Vec3(to.x - from.x, 0.0D, to.z - from.z);
        if (flat.lengthSqr() < 1.0D) return false;
        Vec3 dir = flat.normalize();
        int y = BlockPos.containing(from).getY();
        // Look far enough ahead that a normal walking stride reaches flight transition before
        // the shoreline, rather than after several seconds of pathfinder retries at the edge.
        for (double d = 1.5D; d <= Math.min(18.0D, flat.length()); d += 1.5D) {
            BlockPos sample = BlockPos.containing(from.x + dir.x * d, y, from.z + dir.z * d);
            if (missionWaterAt(level, sample)) return true;
        }
        return false;
    }

    private static boolean missionSolidAhead(ServerLevel level, AmbientFighterEntity fighter, Vec3 destination) {
        if (level == null || fighter == null || destination == null) return false;
        Vec3 from = fighter.position().add(0.0D, Math.max(0.65D, fighter.getBbHeight() * 0.52D), 0.0D);
        Vec3 flat = new Vec3(destination.x - from.x, 0.0D, destination.z - from.z);
        if (flat.lengthSqr() < 1.0D) return false;
        Vec3 probe = from.add(flat.normalize().scale(Math.min(5.5D, Math.sqrt(flat.lengthSqr()))));
        HitResult hit = level.clip(new ClipContext(from, probe, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, fighter));
        return hit.getType() != HitResult.Type.MISS;
    }

    private static BlockPos dryProgressDetour(ServerLevel level, AmbientFighterEntity fighter, Vec3 destination) {
        double current = fighter.position().distanceToSqr(destination);
        BlockPos best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < 18; i++) {
            BlockPos candidate = AmbientFighterSpawner.findSafeGroundAround(level, fighter.blockPosition(),
                    fighter.getRandom(), 5, 15, 24);
            if (candidate == null || missionWaterAt(level, candidate) || !level.getFluidState(candidate.above()).isEmpty()) continue;
            Vec3 c = Vec3.atBottomCenterOf(candidate);
            // Prefer actual progress, but allow a modest sideways move to get around a shoreline.
            double after = c.distanceToSqr(destination);
            if (after > current + 196.0D) continue;
            double score = (current - after) - fighter.position().distanceToSqr(c) * 0.08D;
            if (score > bestScore) { bestScore = score; best = candidate; }
        }
        return best;
    }

    private static void moveIntoOperation(ServerPlayer player, ServerLevel level, AmbientFighterEntity fighter, BlockPos anchor) {
        if (player == null || fighter == null || anchor == null) return;
        double dx = fighter.getX() - (anchor.getX() + 0.5D), dz = fighter.getZ() - (anchor.getZ() + 0.5D);
        if (dx * dx + dz * dz <= 90.0D * 90.0D) return;
        // Intelligence contacts are meetings, not combat roster teleports. If a real handler has to
        // be recovered from far away, stage them outside the player's forward view and let them
        // physically walk the last stretch into the rendezvous. This also covers old persistent
        // residents, not only newly materialized R41 handlers.
        if (ROLE_CONTACT.equals(missionRole(fighter))) {
            BlockPos arrival = null; double bestScore = -Double.MAX_VALUE;
            Vec3 look = player.getLookAngle();
            Vec3 flatLook = new Vec3(look.x, 0.0D, look.z);
            if (flatLook.lengthSqr() > 0.0001D) flatLook = flatLook.normalize();
            for (int i = 0; i < 24; i++) {
                BlockPos candidate = AmbientFighterSpawner.findSafeGroundAround(level, anchor, player.getRandom(), 46, 82, 112);
                if (candidate == null) continue;
                Vec3 to = Vec3.atCenterOf(candidate).subtract(player.position());
                double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
                if (horizontal < 42.0D) continue;
                Vec3 flat = new Vec3(to.x, 0.0D, to.z);
                double facing = flat.lengthSqr() < 0.0001D || flatLook.lengthSqr() < 0.0001D ? 0.0D : flatLook.dot(flat.normalize());
                // Contacts should enter from outside the player's current forward view. This is
                // stricter than the old distance-only staging and prevents visible pop-in.
                if (facing > 0.05D) continue;
                double score = horizontal + Math.max(0.0D, -facing) * 18.0D;
                if (score > bestScore) { bestScore = score; arrival = candidate; }
            }
            if (arrival != null) {
                fighter.teleportTo(arrival.getX() + 0.5D, arrival.getY(), arrival.getZ() + 0.5D);
                navigateMissionActor(fighter, anchor, 1.10D, level.getGameTime());
                FactionWorldData.get(level).recordResident(FactionWorldData.get(level).byId(fighter.getFactionId()), fighter);
                return;
            }
            // Do not fall through to the generic near-anchor teleport if no hidden arrival could
            // be found. Keeping the real resident at their witnessed location is preferable to a
            // visible quest pop-in; the request tick can try to route/recover them again later.
            navigateMissionActor(fighter, anchor, 1.10D, level.getGameTime());
            return;
        }
        // Never pop somebody out of the player's immediate view if their real location is already being witnessed.
        // They still have to assemble for the operation, though, so visibly loaded residents travel there naturally
        // instead of making the request look empty/stuck.
        if (player.distanceToSqr(fighter) <= 72.0D * 72.0D && player.hasLineOfSight(fighter)) {
            navigateMissionActor(fighter, anchor, 1.12D, level.getGameTime());
            return;
        }
        BlockPos safe = AmbientFighterSpawner.findSafeGroundAround(level, anchor, player.getRandom(), 8, 24, 40);
        if (safe == null || !level.getFluidState(safe).isEmpty() || !level.getFluidState(safe.above()).isEmpty()
                || level.getBlockState(safe.below()).getCollisionShape(level, safe.below()).isEmpty()) safe = anchor;
        fighter.teleportTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D);
        fighter.getNavigation().stop();
        FactionWorldData.get(level).recordResident(FactionWorldData.get(level).byId(fighter.getFactionId()), fighter);
    }

    private static String rosterKey(String name) {
        return name.startsWith(ROSTER_PREFIX) ? name : ROSTER_PREFIX + name;
    }

    private static UUID parseUuid(String text) {
        try { return text == null || text.isBlank() ? null : UUID.fromString(text); }
        catch (IllegalArgumentException ex) { return null; }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMissionHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof AmbientFighterEntity fighter) || !isAssigned(fighter)
                || isYielded(fighter) || isRetreated(fighter) || event.getAmount() <= 0.0F) return;
        CompoundTag pd = fighter.getPersistentData();
        String role = pd.getString(ROLE);
        if (!(ROLE_COMBAT.equals(role) || ROLE_PATROL.equals(role) || ROLE_OBSERVER.equals(role) || ROLE_PROTECTED.equals(role))) return;
        float projected = fighter.getHealth() - event.getAmount();
        float threshold = Math.max(1.0F, fighter.getMaxHealth() * Math.max(0.12F, fighter.getPersonality().retreatHealthRatio() + 0.04F));
        if (projected > threshold) return;
        double chance = switch (fighter.getFactionRole()) {
            case RECRUIT -> 0.78D; case MEMBER -> 0.66D; case ENFORCER -> 0.52D; case LIEUTENANT -> 0.40D; case LEADER -> 0.24D;
        };
        if (fighter.getRandom().nextDouble() > chance) return;
        event.setCanceled(true);
        pd.putBoolean(YIELDED, true);
        if (pd.hasUUID(OWNER) && pd.hasUUID(REQUEST)) {
            ServerPlayer owner = ((ServerLevel) fighter.level()).getServer().getPlayerList().getPlayer(pd.getUUID(OWNER));
            if (owner != null) FactionRequestManager.recordMissionOutcome(owner, pd.getUUID(REQUEST), fighter.getUUID(), true);
        }
        fighter.setHealth(Math.max(1.0F, fighter.getMaxHealth() * 0.20F));
        fighter.setTarget(null);
        fighter.getNavigation().stop();
        fighter.setStoryRole(AmbientFighterEntity.STORY_CAPTIVE);
        fighter.speak(FactionRequestDialogue.yield(pd.getString(TYPE), fighter.getUUID().getMostSignificantBits()), 76);
    }
}
