package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

/** Persistent natural-development ceiling/aptitude for one Living World fighter. */
public final class FighterPotentialManager {
    private static final String KEY = "LWPotentialV1";
    private FighterPotentialManager() {}

    public static double potential(AmbientFighterEntity fighter) {
        if (fighter == null || WorldMenaceManager.isHerobrine(fighter)) return 1.0D;
        CompoundTag legacy = fighter.getLegacyData();
        if (!legacy.contains(KEY, Tag.TAG_ANY_NUMERIC)) {
            UUID identity = legacy.hasUUID("NpcSocialIdentity") ? legacy.getUUID("NpcSocialIdentity") : fighter.getUUID();
            legacy.putDouble(KEY, roll(identity));
        }
        return clamp(legacy.getDouble(KEY), 0.62D, 1.68D);
    }

    public static double potentialFromProfile(CompoundTag profile, UUID identity) {
        if (profile == null) return 1.0D;
        CompoundTag legacy = profile.contains("Legacy", Tag.TAG_COMPOUND) ? profile.getCompound("Legacy").copy() : new CompoundTag();
        if (!legacy.contains(KEY, Tag.TAG_ANY_NUMERIC)) {
            UUID id = identity != null ? identity : UUID.nameUUIDFromBytes(profile.getString("Name").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            legacy.putDouble(KEY, roll(id));
            profile.put("Legacy", legacy);
        }
        return clamp(legacy.getDouble(KEY), 0.62D, 1.68D);
    }

    public static String label(AmbientFighterEntity fighter) { return label(potential(fighter)); }
    public static String label(double p) {
        if (p < 0.82D) return "Low";
        if (p < 1.12D) return "Standard";
        if (p < 1.42D) return "High";
        return "Exceptional";
    }

    /** Modest universal aptitude effect; most of Potential is expressed through relevance/ceiling. */
    public static double baseGrowthRate(double p) {
        return clamp(0.78D + 0.22D * p, 0.90D, 1.15D);
    }

    /** Higher potential catches up more aggressively; low potential receives less of the catch-up boost. */
    public static double behindCurve(double baseMultiplier, double p) {
        if (baseMultiplier <= 1.0D) return baseMultiplier;
        double factor = clamp(0.45D + 0.55D * p, 0.80D, 1.38D);
        return 1.0D + (baseMultiplier - 1.0D) * factor;
    }

    /** Higher potential suffers a softer ahead-of-player penalty without ever converting it into free acceleration. */
    public static double aheadCurve(double baseMultiplier, double p) {
        if (baseMultiplier >= 1.0D) return baseMultiplier;
        double penaltyScale = clamp(1.45D - 0.45D * p, 0.55D, 1.15D);
        return 1.0D - (1.0D - baseMultiplier) * penaltyScale;
    }

    /** Player-linked earned ceiling modifier. Low potential is lower; exceptional potential is materially higher. */
    public static double ceilingFactor(double p) {
        return clamp(0.55D + 0.45D * p, 0.82D, 1.31D);
    }

    private static double roll(UUID id) {
        long z = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 29) ^ 0x6A09E667F3BCC909L;
        z = mix(z);
        double u = ((z >>> 11) & ((1L << 53) - 1)) / (double)(1L << 53);
        long z2 = mix(z ^ 0x9E3779B97F4A7C15L);
        double v = ((z2 >>> 11) & ((1L << 53) - 1)) / (double)(1L << 53);
        if (u < 0.08D) return 0.64D + v * 0.17D;          // rare low ceiling
        if (u < 0.65D) return 0.84D + v * 0.27D;          // standard majority
        if (u < 0.93D) return 1.12D + v * 0.29D;          // high
        return 1.43D + v * 0.23D;                         // exceptional minority
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
