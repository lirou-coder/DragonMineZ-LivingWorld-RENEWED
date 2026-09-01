package com.dmzlivingworld.world;

import com.dmzlivingworld.config.LivingWorldConfig;
import com.dragonminez.common.config.CombatConfig;
import com.dragonminez.common.config.ConfigManager;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;

/** DMZ-style flat and adaptive defense with Living World's global mitigation ceiling. */
public final class NpcDefenseCalculator {
    private NpcDefenseCalculator() {}

    public static float mitigate(float incoming, double defense, double defensePenetration) {
        if (incoming <= 0.0F || defense <= 0.0D) return incoming;
        defense *= 1.0D - clamp01(defensePenetration);
        if (defense <= 0.0D) return incoming;

        CombatConfig dmz = ConfigManager.getCombatConfig();
        double flatAbsorbed = Math.min(defense, incoming * dmz.getFlatMitigationMaxAbsorbFraction());
        double result = Math.max(0.0D, incoming - flatAbsorbed);

        Adaptive adaptive = adaptive(dmz);
        if (adaptive.enabled) result *= 1.0D - adaptive.curve(incoming / defense);

        // Adaptive defense is restored, but the combined flat+adaptive result still obeys
        // Living World's explicit world setting. At 0.7 at least 30% always passes.
        double minimumDamage = incoming * (1.0D - LivingWorldConfig.maxDefenseMitigation());
        return (float)Math.max(minimumDamage, result);
    }

    private static Adaptive adaptive(CombatConfig dmz) {
        if (ModList.get().isLoaded("dmzrevamp")) {
            try {
                Object c = Class.forName("com.dmzrevamp.config.AdaptiveDefenseMoreConfigured")
                        .getMethod("get").invoke(null);
                return new Adaptive(bool(c, "enable"), number(c, "adaptativeMitigationParityRatio"),
                        number(c, "adaptativeMitigationParityValue"), number(c, "adaptativeMitigationZeroRatio"),
                        number(c, "adaptativeDefenseMitigationCap"), number(c, "adaptiveDefenseCapRatio"));
            } catch (ReflectiveOperationException ignored) {}
        }
        return new Adaptive(dmz.getEnableAdaptativeDefenseMitigation(), dmz.getAdaptativeMitigationParityRatio(),
                dmz.getAdaptativeMitigationParityValue(), dmz.getAdaptativeMitigationZeroRatio(),
                dmz.getAdaptativeDefenseMitigationCap(), 0.0D);
    }

    private static boolean bool(Object owner, String name) throws ReflectiveOperationException {
        return owner.getClass().getField(name).getBoolean(owner);
    }

    private static double number(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getField(name);
        return ((Number)field.get(owner)).doubleValue();
    }

    private static double clamp01(double value) { return Math.max(0.0D, Math.min(1.0D, value)); }

    private record Adaptive(boolean enabled, double parityRatio, double parityValue,
                            double zeroRatio, double cap, double capRatio) {
        double curve(double ratio) {
            if (!Double.isFinite(ratio) || ratio <= 0.0D) return 0.0D;
            if (capRatio > 0.0D) {
                double capPoint = 1.0D / Math.max(0.0001D, capRatio);
                if (ratio <= capPoint) return clamp01(cap);
                if (ratio < parityRatio) {
                    double progress = (parityRatio - ratio) / Math.max(.0001D, parityRatio - capPoint);
                    return clamp01(Math.min(cap, parityValue + (cap - parityValue) * progress));
                }
            }
            if (ratio < zeroRatio) {
                double value = parityValue * (zeroRatio - ratio) / Math.max(.0001D, zeroRatio - parityRatio);
                return clamp01(Math.min(cap, Math.max(0.0D, value)));
            }
            return 0.0D;
        }
    }
}
