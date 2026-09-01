package com.dmzlivingworld.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-lifetime encounter locks. They prevent repeated debug/natural calls from
 * manufacturing duplicate patrols/clashes while an activity is already underway.
 * Persistent entities themselves remain the source of truth after a reload.
 */
public final class FactionActivityRegistry {
    private static final Map<MinecraftServer, Map<String, Long>> LOCKS = new WeakHashMap<>();

    private FactionActivityRegistry() {}

    public static boolean isBusy(ServerLevel level, WorldFaction faction) {
        if (level == null || faction == null) return false;
        return remaining(level, factionKey(level, faction)) > 0L;
    }

    public static long busyTicks(ServerLevel level, WorldFaction faction) {
        if (level == null || faction == null) return 0L;
        return remaining(level, factionKey(level, faction));
    }

    public static boolean acquire(ServerLevel level, WorldFaction faction, long durationTicks) {
        if (level == null || faction == null) return false;
        return acquireKey(level, factionKey(level, faction), durationTicks);
    }

    public static boolean acquirePair(ServerLevel level, WorldFaction a, WorldFaction b, long durationTicks) {
        if (level == null || a == null || b == null || a.id().equals(b.id())) return false;
        String pair = pairKey(level, a, b);
        if (remaining(level, pair) > 0L || isBusy(level, a) || isBusy(level, b)) return false;
        long end = now(level) + Math.max(100L, durationTicks);
        Map<String, Long> map = map(level);
        map.put(pair, end);
        map.put(factionKey(level, a), end);
        map.put(factionKey(level, b), end);
        return true;
    }

    public static boolean pairBusy(ServerLevel level, WorldFaction a, WorldFaction b) {
        if (level == null || a == null || b == null) return false;
        return remaining(level, pairKey(level, a, b)) > 0L;
    }

    private static boolean acquireKey(ServerLevel level, String key, long durationTicks) {
        if (remaining(level, key) > 0L) return false;
        map(level).put(key, now(level) + Math.max(100L, durationTicks));
        return true;
    }

    private static long remaining(ServerLevel level, String key) {
        Map<String, Long> map = map(level);
        long now = now(level);
        long end = map.getOrDefault(key, 0L);
        if (end <= now) {
            map.remove(key);
            return 0L;
        }
        return end - now;
    }

    private static Map<String, Long> map(ServerLevel level) {
        return LOCKS.computeIfAbsent(level.getServer(), ignored -> new HashMap<>());
    }

    public static void clearRuntime() { LOCKS.clear(); }
    public static int runtimeEntries() {
        int total = 0;
        for (Map<String, Long> map : LOCKS.values()) total += map.size();
        return total;
    }

    private static long now(ServerLevel level) { return level.getServer().overworld().getGameTime(); }

    private static String factionKey(ServerLevel level, WorldFaction faction) {
        return LivingWorldDimensions.realm(level).id() + ":faction:" + faction.id();
    }

    private static String pairKey(ServerLevel level, WorldFaction a, WorldFaction b) {
        String left = a.id().compareTo(b.id()) <= 0 ? a.id() : b.id();
        String right = a.id().compareTo(b.id()) <= 0 ? b.id() : a.id();
        return LivingWorldDimensions.realm(level).id() + ":pair:" + left + ":" + right;
    }
}
