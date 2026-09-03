package com.dmzlivingworld.world;

import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;

/** Keeps cosmetic BP inflation completely separate from every authoritative power calculation. */
public final class FighterVisualPower {
    private FighterVisualPower() {}

    public static int of(AmbientFighterEntity fighter) {
        return fighter == null ? 1 : scale(fighter.getBattlePower());
    }

    public static int scale(int realBattlePower) {
        double visual = Math.max(1, realBattlePower) * LivingWorldConfig.bpVisualMultiplier();
        if (!Double.isFinite(visual) || visual >= Integer.MAX_VALUE - 1.0D) return Integer.MAX_VALUE - 1;
        return Math.max(1, (int)Math.round(visual));
    }
}
