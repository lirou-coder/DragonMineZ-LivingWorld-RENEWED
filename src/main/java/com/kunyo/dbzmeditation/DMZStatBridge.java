package com.kunyo.dbzmeditation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflection-only bridge for rare permanent Dragon Mine Z base-stat rewards.
 *
 * DMZ 2.1 StatsData exposes:
 * - getStats()
 * - getMaxAllowedIncreaseForStat(stat, amount)
 *
 * Stats exposes direct integer getters/setters for the six trainable stats.
 * We mutate the BASE stat itself, never a percentage/multiplicative bonus,
 * respect DMZ's configured caps, then send DMZ's normal StatsSyncS2C packet.
 */
public final class DMZStatBridge {
    private static final String[] STATS = {
        "STR", "SKP", "RES", "VIT", "PWR", "ENE"
    };

    private static boolean attempted;
    private static boolean available;
    private static boolean failureLogged;

    private static Capability<?> capability;
    private static Method getStats;
    private static Method getMaxAllowedIncrease;

    private static Method getStrength;
    private static Method setStrength;
    private static Method getStrikePower;
    private static Method setStrikePower;
    private static Method getResistance;
    private static Method setResistance;
    private static Method getVitality;
    private static Method setVitality;
    private static Method getKiPower;
    private static Method setKiPower;
    private static Method getEnergy;
    private static Method setEnergy;

    private static Constructor<?> statsSyncCtor;
    private static Method sendToTrackingAndSelf;

    private DMZStatBridge() {}

    public record StatGain(String stat, int amount) {}

    /** Compatibility helper for old callers. */
    public static String tryGrantRandomBaseStat(ServerPlayer player) {
        StatGain gain = tryGrantRandomBaseStat(player, 1);
        return gain == null ? null : gain.stat();
    }

    /** Grants up to {@code requested} points to one random trainable base stat while respecting DMZ's cap. */
    public static StatGain tryGrantRandomBaseStat(ServerPlayer player, int requested) {
        if (player == null || requested <= 0 || !resolve()) return null;

        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            LazyOptional<?> optional = player.getCapability((Capability) capability);
            Object data = optional.resolve().orElse(null);
            if (data == null) return null;

            Object stats = getStats.invoke(data);
            if (stats == null) return null;

            List<String> order = new ArrayList<>(List.of(STATS));
            for (int i = order.size() - 1; i > 0; i--) {
                int j = player.getRandom().nextInt(i + 1);
                String temp = order.get(i);
                order.set(i, order.get(j));
                order.set(j, temp);
            }

            for (String stat : order) {
                Object allowedRaw = getMaxAllowedIncrease.invoke(data, stat, requested);
                int allowed = allowedRaw instanceof Number n ? n.intValue() : 0;
                int amount = Math.max(0, Math.min(requested, allowed));
                if (amount < 1) continue;

                increment(stats, stat, amount);
                sync(player);
                return new StatGain(stat, amount);
            }
        } catch (Throwable throwable) {
            logFailureOnce("Could not grant Dragon Mine Z meditation stat breakthrough.", throwable);
        }
        return null;
    }


    /** Percentage-based permanent base-stat breakthrough. */
    public static StatGain tryGrantPercentRandomBaseStat(ServerPlayer player, int gainPercent) {
        if (player == null || gainPercent <= 0 || !resolve()) return null;
        int percent = Math.max(1, Math.min(100, gainPercent));
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            LazyOptional<?> optional = player.getCapability((Capability) capability);
            Object data = optional.resolve().orElse(null);
            if (data == null) return null;
            Object stats = getStats.invoke(data);
            if (stats == null) return null;

            List<String> order = new ArrayList<>(List.of(STATS));
            for (int i = order.size() - 1; i > 0; i--) {
                int j = player.getRandom().nextInt(i + 1);
                String temp = order.get(i); order.set(i, order.get(j)); order.set(j, temp);
            }
            for (String stat : order) {
                int current = currentValue(stats, stat);
                int requested = Math.max(1, (int)Math.round(Math.max(1, current) * (percent / 100.0D)));
                Object allowedRaw = getMaxAllowedIncrease.invoke(data, stat, requested);
                int allowed = allowedRaw instanceof Number n ? n.intValue() : 0;
                int amount = Math.max(0, Math.min(requested, allowed));
                if (amount < 1) continue;
                increment(stats, stat, amount);
                sync(player);
                return new StatGain(stat, amount);
            }
        } catch (Throwable throwable) {
            logFailureOnce("Could not grant Dragon Mine Z meditation stat breakthrough.", throwable);
        }
        return null;
    }

    /**
     * Retained for older internal callers; the configured value now represents a percentage.
     */
    public static StatGain tryGrantScaledRandomBaseStat(ServerPlayer player, int configuredValue) {
        return tryGrantPercentRandomBaseStat(player, configuredValue);
    }

    private static int currentValue(Object stats, String stat) throws Exception {
        return switch (stat) {
            case "STR" -> ((Number)getStrength.invoke(stats)).intValue();
            case "SKP" -> ((Number)getStrikePower.invoke(stats)).intValue();
            case "RES" -> ((Number)getResistance.invoke(stats)).intValue();
            case "VIT" -> ((Number)getVitality.invoke(stats)).intValue();
            case "PWR" -> ((Number)getKiPower.invoke(stats)).intValue();
            case "ENE" -> ((Number)getEnergy.invoke(stats)).intValue();
            default -> 0;
        };
    }

    private static void increment(Object stats, String stat, int amount) throws Exception {
        switch (stat) {
            case "STR" -> setStrength.invoke(stats, ((Number)getStrength.invoke(stats)).intValue() + amount);
            case "SKP" -> setStrikePower.invoke(stats, ((Number)getStrikePower.invoke(stats)).intValue() + amount);
            case "RES" -> setResistance.invoke(stats, ((Number)getResistance.invoke(stats)).intValue() + amount);
            case "VIT" -> setVitality.invoke(stats, ((Number)getVitality.invoke(stats)).intValue() + amount);
            case "PWR" -> setKiPower.invoke(stats, ((Number)getKiPower.invoke(stats)).intValue() + amount);
            case "ENE" -> setEnergy.invoke(stats, ((Number)getEnergy.invoke(stats)).intValue() + amount);
            default -> throw new IllegalArgumentException("Unknown DMZ stat: " + stat);
        }
    }

    private static void sync(ServerPlayer player) {
        try {
            Object packet = statsSyncCtor.newInstance(player);
            sendToTrackingAndSelf.invoke(null, packet, player);
        } catch (Throwable throwable) {
            logFailureOnce("Stat was changed but DMZ stat sync could not be sent immediately.", throwable);
        }
    }

    private static boolean resolve() {
        if (attempted) {
            return available;
        }
        attempted = true;

        try {
            Class<?> capClass = Class.forName("com.dragonminez.common.stats.StatsCapability");
            Field instance = capClass.getField("INSTANCE");
            Object cap = instance.get(null);
            if (!(cap instanceof Capability<?> c)) {
                throw new IllegalStateException("StatsCapability.INSTANCE is not a Forge Capability");
            }
            capability = c;

            Class<?> dataClass = Class.forName("com.dragonminez.common.stats.StatsData");
            getStats = dataClass.getMethod("getStats");
            getMaxAllowedIncrease = dataClass.getMethod("getMaxAllowedIncreaseForStat", String.class, int.class);

            Class<?> statsClass = getStats.getReturnType();
            getStrength = statsClass.getMethod("getStrength");
            setStrength = statsClass.getMethod("setStrength", int.class);
            getStrikePower = statsClass.getMethod("getStrikePower");
            setStrikePower = statsClass.getMethod("setStrikePower", int.class);
            getResistance = statsClass.getMethod("getResistance");
            setResistance = statsClass.getMethod("setResistance", int.class);
            getVitality = statsClass.getMethod("getVitality");
            setVitality = statsClass.getMethod("setVitality", int.class);
            getKiPower = statsClass.getMethod("getKiPower");
            setKiPower = statsClass.getMethod("setKiPower", int.class);
            getEnergy = statsClass.getMethod("getEnergy");
            setEnergy = statsClass.getMethod("setEnergy", int.class);

            Class<?> syncClass = Class.forName("com.dragonminez.common.network.S2C.StatsSyncS2C");
            for (Constructor<?> ctor : syncClass.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 1 && params[0].isAssignableFrom(ServerPlayer.class)) {
                    statsSyncCtor = ctor;
                    break;
                }
            }
            if (statsSyncCtor == null) {
                // The current source uses StatsSyncS2C(ServerPlayer), but keep a
                // second permissive search for a Player-supertype constructor.
                for (Constructor<?> ctor : syncClass.getConstructors()) {
                    if (ctor.getParameterCount() == 1) {
                        statsSyncCtor = ctor;
                        break;
                    }
                }
            }
            if (statsSyncCtor == null) {
                throw new NoSuchMethodException("StatsSyncS2C one-argument constructor");
            }

            Class<?> network = Class.forName("com.dragonminez.common.network.NetworkHandler");
            for (Method method : network.getMethods()) {
                if (method.getName().equals("sendToTrackingEntityAndSelf")
                    && method.getParameterCount() == 2) {
                    sendToTrackingAndSelf = method;
                    break;
                }
            }
            if (sendToTrackingAndSelf == null) {
                throw new NoSuchMethodException("NetworkHandler.sendToTrackingEntityAndSelf");
            }

            available = true;
        } catch (Throwable throwable) {
            available = false;
            logFailureOnce("DragonMine Z stat API was not found; rare meditation stat rewards are disabled.", throwable);
        }

        return available;
    }

    private static void logFailureOnce(String message, Throwable throwable) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        DBZMeditation.LOGGER.warn(message, throwable);
    }
}
