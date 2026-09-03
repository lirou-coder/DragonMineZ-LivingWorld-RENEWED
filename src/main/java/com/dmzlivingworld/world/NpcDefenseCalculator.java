package com.dmzlivingworld.world;

import com.dmzlivingworld.config.LivingWorldConfig;
import com.dragonminez.common.config.CombatConfig;
import com.dragonminez.common.config.ConfigManager;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** DMZ-style flat and adaptive defense with Living World's global mitigation ceiling. */
public final class NpcDefenseCalculator {
    private static volatile AdaptiveAccess revampAdaptive;
    private static volatile boolean adaptiveLookupComplete;

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
                AdaptiveAccess access = adaptiveAccess();
                if (access != null) {
                    Object c = access.getter.invoke(null);
                    return new Adaptive(access.enabled.getBoolean(c), number(c, access.parityRatio),
                            number(c, access.parityValue), number(c, access.zeroRatio),
                            number(c, access.cap), number(c, access.capRatio));
                }
            } catch (ReflectiveOperationException ignored) {}
        }
        return new Adaptive(dmz.getEnableAdaptativeDefenseMitigation(), dmz.getAdaptativeMitigationParityRatio(),
                dmz.getAdaptativeMitigationParityValue(), dmz.getAdaptativeMitigationZeroRatio(),
                dmz.getAdaptativeDefenseMitigationCap(), 0.0D);
    }

    private static double number(Object owner, Field field) throws ReflectiveOperationException {
        return ((Number)field.get(owner)).doubleValue();
    }

    private static AdaptiveAccess adaptiveAccess() {
        if (!adaptiveLookupComplete) {
            synchronized (NpcDefenseCalculator.class) {
                if (!adaptiveLookupComplete) {
                    try {
                        Class<?> type = Class.forName("com.dmzrevamp.config.AdaptiveDefenseMoreConfigured");
                        revampAdaptive = new AdaptiveAccess(type.getMethod("get"), type.getField("enable"),
                                type.getField("adaptativeMitigationParityRatio"),
                                type.getField("adaptativeMitigationParityValue"),
                                type.getField("adaptativeMitigationZeroRatio"),
                                type.getField("adaptativeDefenseMitigationCap"),
                                type.getField("adaptiveDefenseCapRatio"));
                    } catch (ReflectiveOperationException ignored) {
                        revampAdaptive = null;
                    }
                    adaptiveLookupComplete = true;
                }
            }
        }
        return revampAdaptive;
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

    private record AdaptiveAccess(Method getter, Field enabled, Field parityRatio, Field parityValue,
                                  Field zeroRatio, Field cap, Field capRatio) {}
}
