package com.dmzlivingworld.world;

import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Optional bridge to the active BP formula. No hard dependency on dmzrevamp is introduced. */
public final class BattlePowerFormula {
    private static final double DMZ_REFERENCE = 1200.0D;
    private static final double DMZ_DIVISOR = 100.0D;
    private static final double DMZ_EXPONENT = 1.2D;
    private static volatile Method revampCalculator;
    private static volatile boolean calculatorLookupComplete;
    private static volatile ConfigAccess revampConfig;
    private static volatile boolean configLookupComplete;

    private BattlePowerFormula() {}

    public static double battlePower(double effective) {
        double safe = Math.max(1.0D, effective);
        if (ModList.get().isLoaded("dmzrevamp")) {
            try {
                Method method = calculatorMethod();
                if (method != null) return ((Number)method.invoke(null, safe)).doubleValue();
            } catch (ReflectiveOperationException ignored) {
                // A different Overhaul build may not expose the bridge; use its public config below.
            }
        }
        Parameters p = parameters();
        return p.reference * Math.pow(safe / p.divisor, p.exponent);
    }

    public static double effectiveFromBattlePower(double battlePower) {
        Parameters p = parameters();
        return p.divisor * Math.pow(Math.max(1.0D, battlePower) / p.reference, 1.0D / p.exponent);
    }

    /** Exponent from the formula currently authoritative for BP, including dmzrevamp. */
    public static double exponent() {
        return parameters().exponent;
    }

    private static Parameters parameters() {
        if (ModList.get().isLoaded("dmzrevamp")) {
            try {
                ConfigAccess access = configAccess();
                if (access != null) {
                    Object config = access.getter.invoke(null);
                    return new Parameters(read(config, access.reference, DMZ_REFERENCE),
                            read(config, access.divisor, DMZ_DIVISOR), read(config, access.exponent, DMZ_EXPONENT));
                }
            } catch (ReflectiveOperationException ignored) {}
        }
        return new Parameters(DMZ_REFERENCE, DMZ_DIVISOR, DMZ_EXPONENT);
    }

    private static double read(Object owner, Field field, double fallback) throws ReflectiveOperationException {
        double value = ((Number)field.get(owner)).doubleValue();
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }

    private static Method calculatorMethod() {
        if (!calculatorLookupComplete) {
            synchronized (BattlePowerFormula.class) {
                if (!calculatorLookupComplete) {
                    try {
                        revampCalculator = Class.forName("com.dmzrevamp.revamp.battlepower.CustomBattlePowerCalculator")
                                .getMethod("calculateMobBattlePower", double.class);
                    } catch (ReflectiveOperationException ignored) {
                        revampCalculator = null;
                    }
                    calculatorLookupComplete = true;
                }
            }
        }
        return revampCalculator;
    }

    private static ConfigAccess configAccess() {
        if (!configLookupComplete) {
            synchronized (BattlePowerFormula.class) {
                if (!configLookupComplete) {
                    try {
                        Class<?> type = Class.forName("com.dmzrevamp.config.CustomBattlePowerConfig");
                        revampConfig = new ConfigAccess(type.getMethod("get"), type.getField("referenceMultiplier"),
                                type.getField("totalStatsDivisor"), type.getField("exponent"));
                    } catch (ReflectiveOperationException ignored) {
                        revampConfig = null;
                    }
                    configLookupComplete = true;
                }
            }
        }
        return revampConfig;
    }

    private record Parameters(double reference, double divisor, double exponent) {}
    private record ConfigAccess(Method getter, Field reference, Field divisor, Field exponent) {}
}
