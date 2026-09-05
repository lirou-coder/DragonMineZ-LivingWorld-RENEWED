package com.dmzlivingworld.world;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceCharacterConfig;
import net.minecraftforge.fml.ModList;

/** Optional Sairens race/config bridge kept free of hard Sairens class references. */
public final class SairensRaceCompat {
    private static final String MOD_ID = "sairens_dmz_world";

    private SairensRaceCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isBioAndroidHumanModel() {
        return isCustomModel("bioandroid", "human") || isCustomModel("bioandroid", "androidbase");
    }

    public static boolean isCustomModel(String raceId, String model) {
        if (!isLoaded() || raceId == null || model == null) return false;
        try {
            RaceCharacterConfig config = ConfigManager.getRaceCharacter(raceId);
            return config != null && model.equalsIgnoreCase(config.getCustomModel());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean isCustomRace(String raceId) {
        return isLoaded() && ("antoranian".equalsIgnoreCase(raceId) || "zaarakins".equalsIgnoreCase(raceId)
                || "zaarakin".equalsIgnoreCase(raceId));
    }
}
