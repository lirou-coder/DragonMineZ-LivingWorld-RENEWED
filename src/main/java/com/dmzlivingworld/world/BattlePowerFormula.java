package com.dmzlivingworld.world;

import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Optional bridge to the active BP formula. No hard dependency on dmzrevamp is introduced. */
public final class BattlePowerFormula {
    private static final double DMZ_REFERENCE = 1200.0D;
    private static final double DMZ_DIVISOR = 100.0D;
    private static final double DMZ_EXPONENT = 1.2D;

    private BattlePowerFormula() {}

    public static double battlePower(double effective) {
        double safe = Math.max(1.0D, effective);
        if (ModList.get().isLoaded("dmzrevamp")) {
            try {
                Class<?> calculator = Class.forName("com.dmzrevamp.revamp.battlepower.CustomBattlePowerCalculator");
                Method method = calculator.getMethod("calculateMobBattlePower", double.class);
                return ((Number)method.invoke(null, safe)).doubleValue();
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
                Object config = Class.forName("com.dmzrevamp.config.CustomBattlePowerConfig")
                        .getMethod("get").invoke(null);
                return new Parameters(read(config, "referenceMultiplier", DMZ_REFERENCE),
                        read(config, "totalStatsDivisor", DMZ_DIVISOR), read(config, "exponent", DMZ_EXPONENT));
            } catch (ReflectiveOperationException ignored) {}
        }
        return new Parameters(DMZ_REFERENCE, DMZ_DIVISOR, DMZ_EXPONENT);
    }

    private static double read(Object owner, String name, double fallback) throws ReflectiveOperationException {
        Field field = owner.getClass().getField(name);
        double value = ((Number)field.get(owner)).doubleValue();
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }

    private record Parameters(double reference, double divisor, double exponent) {}
}
