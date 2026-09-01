package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.config.LivingWorldConfig;

/** Converts the fighter's canonical effective stat budget into its real combat attributes. */
public final class FighterPowerStatScaler {
    public static final String EFFECTIVE_STATS = "LWEffectiveStats";
    private static final String STABLE_MAX_HEALTH = "LWStableMaxHealth";

    private FighterPowerStatScaler() {}

    public static double effectiveStatBudget(AmbientFighterEntity fighter) {
        double stored = fighter.getLegacyData().getDouble(EFFECTIVE_STATS);
        if (Double.isFinite(stored) && stored > 0.0D) return stored;
        double migrated = BattlePowerFormula.effectiveFromBattlePower(fighter.getPermanentBattlePower());
        fighter.getLegacyData().putDouble(EFFECTIVE_STATS, migrated);
        return migrated;
    }

    public static void setEffectiveStatBudget(AmbientFighterEntity fighter, double effective) {
        double safe = Math.max(1.0D, Double.isFinite(effective) ? effective : 1.0D);
        fighter.getLegacyData().putDouble(EFFECTIVE_STATS, safe);
    }

    public static double baseHealth(AmbientFighterEntity fighter, double livedMultiplier) {
        double lived = Math.min(1.22D, 1.0D + (Math.max(1.0D, livedMultiplier) - 1.0D) * 0.55D);
        double calculated = 20.0D + shares(fighter).health * effectiveStatBudget(fighter) * lived;
        double stable = fighter.getLegacyData().getDouble(STABLE_MAX_HEALTH);
        double result = Math.max(calculated, Double.isFinite(stable) ? stable : 0.0D);
        fighter.getLegacyData().putDouble(STABLE_MAX_HEALTH, result);
        return result;
    }

    /** A temporary powered state may raise max HP, but exhaustion must never take it away. */
    public static void preserveCurrentMaxHealth(AmbientFighterEntity fighter) {
        if (fighter == null) return;
        double current = fighter.getMaxHealth();
        if (Double.isFinite(current) && current > fighter.getLegacyData().getDouble(STABLE_MAX_HEALTH))
            fighter.getLegacyData().putDouble(STABLE_MAX_HEALTH, current);
    }

    public static double baseAttack(AmbientFighterEntity fighter, double livedMultiplier) {
        return Math.max(1.0D, shares(fighter).melee * effectiveStatBudget(fighter)
                * Math.max(1.0D, livedMultiplier));
    }

    public static double baseDefense(AmbientFighterEntity fighter) {
        double lived = Math.max(1.0D, FighterBattleGrowthManager.combatMultiplier(fighter));
        return Math.max(0.0D, shares(fighter).defense * effectiveStatBudget(fighter) * lived
                * FighterPassiveSkillManager.healthMultiplier(fighter));
    }

    public static float baseKi(AmbientFighterEntity fighter, float ignoredHistoricalKi) {
        return (float)Math.max(1.0D, shares(fighter).ki * effectiveStatBudget(fighter));
    }

    public static double transformedBattlePower(AmbientFighterEntity fighter, double meleeMultiplier,
                                                 double defenseMultiplier, double vitalityMultiplier,
                                                 double kiMultiplier) {
        Shares s = shares(fighter);
        double post = effectiveStatBudget(fighter) * (s.melee * meleeMultiplier
                + s.defense * defenseMultiplier + s.ki * kiMultiplier + s.health * vitalityMultiplier);
        double ratio = post / Math.max(1.0D, effectiveStatBudget(fighter));
        return battlePowerForStats(fighter, effectiveStatBudget(fighter)) * Math.pow(Math.max(0.0D, ratio), 1.2D);
    }

    public static double currentTotalStats(AmbientFighterEntity fighter) {
        if (fighter == null) return 1.0D;
        return Math.max(1.0D, fighter.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                + fighter.getDefenseStat() + fighter.getKiBlastDamage() + Math.max(0.0D, fighter.getMaxHealth() - 20.0D));
    }

    public static double battlePowerForStats(AmbientFighterEntity fighter, double totalStats) {
        return BattlePowerFormula.battlePower(totalStats);
    }

    /** BP of a freshly distributed budget, before lived/passive/form modifiers are applied. */
    public static double battlePowerForEffectiveBudget(AmbientFighterEntity fighter, double effective) {
        Shares s = shares(fighter);
        double distributed = Math.max(1.0D, effective * (s.melee + s.defense + s.ki + s.health));
        return battlePowerForStats(fighter, distributed);
    }

    /**
     * Changes the canonical budget by the relative BP change. This is deliberately not a direct
     * inverse of displayed BP: NPC archetypes distribute only part of effective, so directly
     * inverting an unchanged displayed BP would repeatedly discard that undistributed part.
     */
    public static double effectiveForPowerChange(AmbientFighterEntity fighter, double oldBattlePower,
                                                  double newBattlePower) {
        double current = effectiveStatBudget(fighter);
        double oldSafe = Math.max(1.0D, oldBattlePower);
        double newSafe = Math.max(1.0D, newBattlePower);
        if (Math.abs(newSafe - oldSafe) < 0.5D) return current;
        double ratio = newSafe / oldSafe;
        return Math.max(1.0D, current * Math.pow(ratio, 1.0D / BattlePowerFormula.exponent()));
    }

    public static double effectiveFromBattlePower(AmbientFighterEntity fighter, double battlePower) {
        return BattlePowerFormula.effectiveFromBattlePower(Math.max(1.0D, battlePower));
    }

    private static Shares shares(AmbientFighterEntity fighter) {
        return switch (fighter.getArchetype()) {
            case BRAWLER -> new Shares(LivingWorldConfig.BRAWLER_MELEE_SHARE.get(), LivingWorldConfig.BRAWLER_DEFENSE_SHARE.get(),
                    LivingWorldConfig.BRAWLER_KI_SHARE.get(), LivingWorldConfig.BRAWLER_HEALTH_SHARE.get());
            case MARTIAL_ARTIST -> new Shares(LivingWorldConfig.MARTIAL_ARTIST_MELEE_SHARE.get(), LivingWorldConfig.MARTIAL_ARTIST_DEFENSE_SHARE.get(),
                    LivingWorldConfig.MARTIAL_ARTIST_KI_SHARE.get(), LivingWorldConfig.MARTIAL_ARTIST_HEALTH_SHARE.get());
            case SPEEDSTER -> new Shares(LivingWorldConfig.SPEED_FIGHTER_MELEE_SHARE.get(), LivingWorldConfig.SPEED_FIGHTER_DEFENSE_SHARE.get(),
                    LivingWorldConfig.SPEED_FIGHTER_KI_SHARE.get(), LivingWorldConfig.SPEED_FIGHTER_HEALTH_SHARE.get());
            case GUARDIAN -> new Shares(LivingWorldConfig.GUARDIAN_MELEE_SHARE.get(), LivingWorldConfig.GUARDIAN_DEFENSE_SHARE.get(),
                    LivingWorldConfig.GUARDIAN_KI_SHARE.get(), LivingWorldConfig.GUARDIAN_HEALTH_SHARE.get());
            case KI_SPECIALIST -> new Shares(LivingWorldConfig.KI_SPECIALIST_MELEE_SHARE.get(), LivingWorldConfig.KI_SPECIALIST_DEFENSE_SHARE.get(),
                    LivingWorldConfig.KI_SPECIALIST_KI_SHARE.get(), LivingWorldConfig.KI_SPECIALIST_HEALTH_SHARE.get());
        };
    }

    private record Shares(double melee, double defense, double ki, double health) {}
}
