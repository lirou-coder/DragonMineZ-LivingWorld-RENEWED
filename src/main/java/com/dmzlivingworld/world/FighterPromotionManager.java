package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterRank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * Rank is now a consequence of the fighter's own life rather than a permanent
 * spawn label.  Promotion never reads player power and never rescales the NPC.
 */
public final class FighterPromotionManager {
    private static final String ORIGIN_POWER = "OriginPower";
    private static final String BIRTH_ERA = "BirthEra";

    private FighterPromotionManager() {}

    public static void tick(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide) return;
        ensureOrigin(fighter);
        if (fighter.tickCount % 200 != Math.floorMod(fighter.getUUID().hashCode(), 200)) return;
        evaluate(fighter);
    }

    public static void ensureOrigin(AmbientFighterEntity fighter) {
        CompoundTag legacy = fighter.getLegacyData();
        if (!legacy.contains(ORIGIN_POWER)) legacy.putInt(ORIGIN_POWER, fighter.getPermanentBattlePower());
        if (!legacy.contains(BIRTH_ERA) && fighter.level() instanceof ServerLevel level) {
        legacy.putString(BIRTH_ERA, WorldEraData.get(level).displayName());
        }
    }

    public static boolean evaluate(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || !fighter.isAlive()) return false;
        if (fighter.isDefeated() || fighter.isCaptive() || fighter.isMeditating()
                || fighter.isTransforming() || fighter.isRacialFormActive() || fighter.isKaiokenActive()
                || FighterSpecialItemManager.hasActiveMightFruit(fighter) || fighter.getTarget() != null) return false;
        ensureOrigin(fighter);

        CompoundTag legacy = fighter.getLegacyData();
        int origin = Math.max(1, legacy.getInt(ORIGIN_POWER));
        double growth = fighter.getPermanentBattlePower() / (double) origin;
        int sessions = fighter.getTrainingSessions();
        int fights = legacy.getInt("Fights");
        int wins = legacy.getInt("Wins");

        if (fighter.getRank() == FighterRank.ROOKIE) {
            int score = sessions * 2 + fights * 3 + wins * 4
                    + (fighter.hasFlightUnlocked() ? 6 : 0)
                    + fighter.getRacialSkillLevel() * 4;
            if (sessions >= 6 && fights >= 3 && wins >= 1 && growth >= 1.28D && score >= 38) {
                return fighter.promoteTo(FighterRank.TRAINED);
            }
        } else if (fighter.getRank() == FighterRank.TRAINED) {
            int score = sessions * 2 + fights * 4 + wins * 5
                    + (fighter.hasFlightUnlocked() ? 10 : 0)
                    + fighter.getRacialSkillLevel() * 6;
            if (sessions >= 18 && fights >= 8 && wins >= 4 && growth >= 1.65D
                    && fighter.hasFlightUnlocked() && score >= 105) {
                return fighter.promoteTo(FighterRank.VETERAN);
            }
        }
        return false;
    }
}
