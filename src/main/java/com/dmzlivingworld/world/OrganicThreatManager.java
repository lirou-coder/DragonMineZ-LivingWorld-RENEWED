package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterRank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * Recognizes danger that already emerged from Living World history. This never grants
 * stats, changes rank, or scales a fighter to the player. It only interprets facts that
 * already exist: alignment/faction, battle record, leadership and power versus the
 * current world era.
 */
public final class OrganicThreatManager {
    private static final int MAJOR_THREAT_SCORE = 5;

    private OrganicThreatManager() {}

    public static void tick(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || fighter.tickCount % 200 != Math.floorMod(fighter.getUUID().hashCode(), 200)) return;
        int score = score(fighter);
        CompoundTag legacy = fighter.getLegacyData();
        if (!hasProvenThreatHistory(fighter, legacy) && legacy.getBoolean("ThreatRecognized")) {
            // Older builds could award this title from alignment/rank/raw BP alone. Remove only
            // those false positives; fighters with an actual violent/wanted/rival record keep it.
            legacy.remove("ThreatRecognized");
        }
        if (score > legacy.getInt("ThreatPeakScore")) legacy.putInt("ThreatPeakScore", score);
        if (score >= MAJOR_THREAT_SCORE && !legacy.getBoolean("ThreatRecognized")) {
            legacy.putBoolean("ThreatRecognized", true);
            fighter.recordLegacyEvent("Became recognized as a major world threat");
        }
    }

    public static int score(AmbientFighterEntity fighter) {
        if (fighter == null || !(fighter.level() instanceof ServerLevel level)) return 0;
        CompoundTag legacy = fighter.getLegacyData();
        int kills = Math.max(0, legacy.getInt("Kills"));
        boolean hostileIdentity = fighter.getAlignment() == FighterAlignment.BAD || kills >= 4;
        if (fighter.isFactionMember()) {
            WorldFaction faction = FactionManager.byId(level, fighter.getFactionId());
            hostileIdentity |= faction != null && faction.alignment() == FighterAlignment.BAD;
        }
        if (!hostileIdentity) return 0;

        int score = 1;
        if (fighter.getRank() == FighterRank.VETERAN) score++;
        if (kills >= 1) score++;
        if (kills >= 3) score++;
        if (kills >= 7) score++;
        int wins = Math.max(0, legacy.getInt("Wins"));
        if (wins >= 6) score++;
        if (wins >= 12) score++;
        if (fighter.isFactionLeader()) score++;

        double anchor = Math.max(1.0D, WorldPowerScaler.resolveWorldAnchor(level, fighter.blockPosition()));
        double relative = fighter.getBattlePower() / anchor;
        if (relative >= 2.0D) score++;
        if (relative >= 4.0D) score++;
        if (relative >= 7.0D) score++;

        // Being strong, BAD, a veteran, or a leader is not by itself a world-threatening deed.
        // Major-threat recognition now requires evidence that this fighter has actually behaved
        // like a threat in the simulated world.
        if (!hasProvenThreatHistory(fighter, legacy)) score = Math.min(score, 2);
        return score;
    }

    private static boolean hasProvenThreatHistory(AmbientFighterEntity fighter, CompoundTag legacy) {
        int kills = Math.max(0, legacy.getInt("Kills"));
        int wins = Math.max(0, legacy.getInt("Wins"));
        return kills >= 1
                || wins >= 4
                || (fighter.isWanted() && fighter.getWantedLevel() >= 3)
                || (fighter.getPlayerRivalBattles() >= 3 && fighter.getMemoryRelationship() <= -50);
    }

    public static boolean isMajorThreat(AmbientFighterEntity fighter) {
        return score(fighter) >= MAJOR_THREAT_SCORE;
    }

    public static String statusLabel(AmbientFighterEntity fighter) {
        int score = score(fighter);
        if (score >= 7) return "Severe world threat";
        if (score >= MAJOR_THREAT_SCORE) return "Major world threat";
        if (score >= 3) return "Rising threat";
        return "";
    }

    public static boolean wasRecognized(AmbientFighterEntity fighter) {
        return fighter != null && fighter.getLegacyData().getBoolean("ThreatRecognized");
    }
}
