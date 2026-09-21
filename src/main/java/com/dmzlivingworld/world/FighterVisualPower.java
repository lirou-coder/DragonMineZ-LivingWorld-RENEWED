package com.dmzlivingworld.world;

import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;

/** Keeps cosmetic BP inflation completely separate from every authoritative power calculation. */
public final class FighterVisualPower {
    private FighterVisualPower() {}

    public static int of(AmbientFighterEntity fighter) {
        return (int)Math.min(Integer.MAX_VALUE - 1L, ofLong(fighter));
    }

    public static long ofLong(AmbientFighterEntity fighter) {
        return fighter == null ? 1L : scaleLong(fighter.getPermanentBattlePowerLong());
    }

    /** Exact Ki Sense/scouter value before the API's final float conversion. */
    public static double ofDouble(AmbientFighterEntity fighter) {
        if (fighter == null) return 1.0D;
        double visual = Math.max(1L, fighter.getPermanentBattlePowerLong())
                * LivingWorldConfig.bpVisualMultiplier();
        if (!Double.isFinite(visual)) return Double.MAX_VALUE;
        return Math.max(1.0D, visual);
    }

    public static int scale(int realBattlePower) {
        return (int)Math.min(Integer.MAX_VALUE - 1L, scaleLong(realBattlePower));
    }

    public static long scaleLong(long realBattlePower) {
        double visual = Math.max(1, realBattlePower) * LivingWorldConfig.bpVisualMultiplier();
        if (!Double.isFinite(visual) || visual >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.max(1L, Math.round(visual));
    }
}
