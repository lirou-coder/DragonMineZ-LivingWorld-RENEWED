package com.kunyo.dbzmeditation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection-only bridge for meditation features that intentionally operate on
 * DragonMine Z's native systems instead of inventing parallel resources.
 *
 * Supported DMZ 2.1.x paths:
 * - StatsData#getResources / getMaxEnergy / getMaxStamina
 * - Resources#getCurrentEnergy / getCurrentStamina / addEnergy / addStamina
 * - StatsData#getCharacter
 * - Character active form/stack-form getters
 * - Character#gainMastery(group, form, amount)
 * - FormMasteries#getMastery(group, form)
 *
 * The bridge stays optional at runtime so the addon remains compilable without
 * bundling DragonMine Z classes into the source project.
 */
public final class DMZTrainingBridge {
    private static boolean attempted;
    private static boolean available;
    private static boolean failureLogged;

    private static Capability<?> capability;
    private static Method getResources;
    private static Method getCharacter;
    private static Method getMaxEnergy;
    private static Method getMaxStamina;
    private static Method getStats;
    private static Method getTotalStats;
    private static Method getSingleStatCost;
    private static Method getTrainingConfig;
    private static Method computeTpsPerLevel;
    private static Method getSettings;
    private static Method getTpsLimitPerGame;

    private static Method getCurrentEnergy;
    private static Method getTrainingPoints;
    private static Method getCurrentStamina;
    private static Method addEnergy;
    private static Method addStamina;

    private static Method hasActiveForm;
    private static Method getActiveFormGroup;
    private static Method getActiveForm;
    private static Method getActiveFormData;
    private static Method getFormMasteries;

    private static Method hasActiveStackForm;
    private static Method getActiveStackFormGroup;
    private static Method getActiveStackForm;
    private static Method getActiveStackFormData;
    private static Method getStackFormMasteries;

    private static Method gainMastery;
    private static Method getMastery;
    private static Method getMaxMastery;

    private static Constructor<?> statsSyncCtor;
    private static Method sendToTrackingAndSelf;

    private DMZTrainingBridge() {}

    public record ResourceRecovery(
        float energyRecovered,
        float staminaRecovered,
        float energyPercent,
        float staminaPercent
    ) {}

    public record FormProgress(
        String group,
        String form,
        boolean stack,
        double mastery,
        double maxMastery,
        double gained
    ) {
        public static FormProgress none() {
            return new FormProgress("", "", false, 0.0D, 0.0D, 0.0D);
        }

        public boolean active() {
            return form != null && !form.isEmpty();
        }
    }

    public static ResourceRecovery recoverResources(
        ServerPlayer player,
        double fractionOfMaximum
    ) {
        if (player == null || fractionOfMaximum <= 0.0D || !resolve()) {
            return new ResourceRecovery(0.0F, 0.0F, 0.0F, 0.0F);
        }

        try {
            Object data = getData(player);
            if (data == null) {
                return new ResourceRecovery(0.0F, 0.0F, 0.0F, 0.0F);
            }

            Object resources = getResources.invoke(data);
            if (resources == null) {
                return new ResourceRecovery(0.0F, 0.0F, 0.0F, 0.0F);
            }

            float maxEnergy = asFloat(getMaxEnergy.invoke(data));
            float maxStamina = asFloat(getMaxStamina.invoke(data));
            float beforeEnergy = asFloat(getCurrentEnergy.invoke(resources));
            float beforeStamina = asFloat(getCurrentStamina.invoke(resources));

            float energyAdd = (float)Math.max(0.0D, maxEnergy * fractionOfMaximum);
            float staminaAdd = (float)Math.max(0.0D, maxStamina * fractionOfMaximum);

            if (energyAdd > 0.0F && beforeEnergy < maxEnergy) {
                addEnergy.invoke(resources, energyAdd);
            }
            if (staminaAdd > 0.0F && beforeStamina < maxStamina) {
                addStamina.invoke(resources, staminaAdd);
            }

            float afterEnergy = asFloat(getCurrentEnergy.invoke(resources));
            float afterStamina = asFloat(getCurrentStamina.invoke(resources));

            return new ResourceRecovery(
                Math.max(0.0F, afterEnergy - beforeEnergy),
                Math.max(0.0F, afterStamina - beforeStamina),
                percent(afterEnergy, maxEnergy),
                percent(afterStamina, maxStamina)
            );
        } catch (Throwable throwable) {
            logFailureOnce("Could not apply DragonMine Z meditation resource recovery.", throwable);
            return new ResourceRecovery(0.0F, 0.0F, 0.0F, 0.0F);
        }
    }

    /**
     * Trains one native mastery track only: stack form first, otherwise the
     * normal active form. Character#gainMastery handles DMZ's shared-mastery
     * relationships itself.
     */
    public static FormProgress gainActiveFormMastery(
        ServerPlayer player,
        double amount
    ) {
        if (player == null || amount <= 0.0D || !resolve()) {
            return getFormProgress(player);
        }

        try {
            Object data = getData(player);
            if (data == null) return FormProgress.none();
            Object character = getCharacter.invoke(data);
            if (character == null) return FormProgress.none();

            FormContext ctx = activeFormContext(character);
            if (ctx == null) return FormProgress.none();

            double before = masteryValue(ctx.masteries(), ctx.group(), ctx.form());
            gainMastery.invoke(character, ctx.group(), ctx.form(), amount);
            double after = masteryValue(ctx.masteries(), ctx.group(), ctx.form());
            double max = maxMastery(ctx.formData());

            return new FormProgress(
                ctx.group(),
                ctx.form(),
                ctx.stack(),
                after,
                max,
                Math.max(0.0D, after - before)
            );
        } catch (Throwable throwable) {
            logFailureOnce("Could not grant DragonMine Z meditation form mastery.", throwable);
            return FormProgress.none();
        }
    }

    public static FormProgress getFormProgress(ServerPlayer player) {
        if (player == null || !resolve()) {
            return FormProgress.none();
        }

        try {
            Object data = getData(player);
            if (data == null) return FormProgress.none();
            Object character = getCharacter.invoke(data);
            if (character == null) return FormProgress.none();

            FormContext ctx = activeFormContext(character);
            if (ctx == null) return FormProgress.none();

            return new FormProgress(
                ctx.group(),
                ctx.form(),
                ctx.stack(),
                masteryValue(ctx.masteries(), ctx.group(), ctx.form()),
                maxMastery(ctx.formData()),
                0.0D
            );
        } catch (Throwable throwable) {
            logFailureOnce("Could not read DragonMine Z active-form meditation feedback.", throwable);
            return FormProgress.none();
        }
    }

    /** Current unspent/native DragonMine Z Training Points, for diagnostics only. */
    public static double getTrainingPoints(ServerPlayer player) {
        if (player == null || !resolve() || getTrainingPoints == null) {
            return 0.0D;
        }

        try {
            Object data = getData(player);
            if (data == null) return 0.0D;

            Object resources = getResources.invoke(data);
            if (resources == null) return 0.0D;

            Object raw = getTrainingPoints.invoke(resources);
            return raw instanceof Number number
                ? Math.max(0.0D, number.doubleValue())
                : 0.0D;
        } catch (Throwable throwable) {
            DBZMeditation.LOGGER.debug(
                "Could not read DragonMine Z training points.",
                throwable
            );
            return 0.0D;
        }
    }

    /**
     * DMZ native training games scale from the current single-stat cost derived
     * from total allocated stats, not from the spendable TP balance.
     */
    public static int getProgressionSingleStatCost(ServerPlayer player) {
        if (player == null
            || !resolve()
            || getStats == null
            || getTotalStats == null
            || getSingleStatCost == null) {
            return 1;
        }

        try {
            Object data = getData(player);
            if (data == null) return 1;
            Object stats = getStats.invoke(data);
            if (stats == null) return 1;
            Object totalRaw = getTotalStats.invoke(stats);
            Object coerced = coerceNumber(totalRaw, getSingleStatCost.getParameterTypes()[0]);
            Object costRaw =
                getSingleStatCost.invoke(
                    data,
                    coerced
                );

            if (!(costRaw instanceof Number number)) {
                return 1;
            }

            long rawCost =
                number.longValue();

            return (int)Math.max(
                1L,
                Math.min(
                    Integer.MAX_VALUE,
                    rawCost
                )
            );
        } catch (Throwable throwable) {
            DBZMeditation.LOGGER.debug(
                "Could not read DragonMine Z progression cost for training scaling.",
                throwable
            );
            return 1;
        }
    }

    /** Same progression curve used by DMZ TrainingRewardC2S. */
    public static double getNativeMinigameTpPerLevel(ServerPlayer player) {
        int cost = getProgressionSingleStatCost(player);

        if (resolve() && getTrainingConfig != null && computeTpsPerLevel != null) {
            try {
                Object config = getTrainingConfig.invoke(null);
                if (config != null) {
                    Object raw = computeTpsPerLevel.invoke(config, cost, null);
                    if (raw instanceof Number number) {
                        double value =
                            number.doubleValue();

                        if (Double.isFinite(value)
                            && value > 0.0D) {

                            return Math.min(
                                1000000000.0D,
                                Math.max(
                                    1.0D,
                                    value
                                )
                            );
                        }
                    }
                }
            } catch (Throwable throwable) {
                DBZMeditation.LOGGER.debug(
                    "Could not read Dragon Mine Z's active training reward curve; using the compatible 2.1 default.",
                    throwable
                );
            }
        }

        double fallback =
            3.4D
                * Math.pow(
                    Math.max(
                        1.0D,
                        cost
                    ),
                    0.6D
                );

        if (!Double.isFinite(fallback)) {
            return 1.0D;
        }

        return Math.min(
            1000000000.0D,
            Math.max(
                1.0D,
                fallback
            )
        );
    }

    /** Native per-game TP cap; falls back to DMZ 2.1's 50,000. */
    public static int getNativeMinigameRewardCap(ServerPlayer player) {
        if (resolve()
            && getTrainingConfig != null
            && getSettings != null
            && getTpsLimitPerGame != null) {
            try {
                Object config = getTrainingConfig.invoke(null);
                if (config != null) {
                    Object settings = getSettings.invoke(config, "rhythm");
                    if (settings != null) {
                        Object raw = getTpsLimitPerGame.invoke(settings);
                        if (raw instanceof Number number) {
                            float rawCap =
                                number.floatValue();

                            if (Float.isFinite(rawCap)
                                && rawCap > 0.0F) {

                                return Math.min(
                                    100000,
                                    Math.max(
                                        1,
                                        Math.round(rawCap)
                                    )
                                );
                            }
                        }
                    }
                }
            } catch (Throwable throwable) {
                DBZMeditation.LOGGER.debug(
                    "Could not read DragonMine Z's native training reward cap.",
                    throwable
                );
            }
        }
        return 50000;
    }

    /** Passive meditation uses a restrained fraction of the active training reward curve. */
    public static int getMeditationTpUnit(
        ServerPlayer player
    ) {
        double nativePerLevel =
            getNativeMinigameTpPerLevel(
                player
            );

        int cap =
            getNativeMinigameRewardCap(
                player
            );

        int cycleCap =
            Math.max(
                1,
                Math.min(
                    7500,
                    cap / 8
                )
            );

        double raw =
            nativePerLevel * 0.030D;

        if (!Double.isFinite(raw)) {
            return 1;
        }

        return Math.max(
            1,
            Math.min(
                cycleCap,
                (int)Math.round(
                    Math.min(
                        cycleCap,
                        raw
                    )
                )
            )
        );
    }

            public static float[] getResourcePercents(ServerPlayer player) {
        if (player == null || !resolve()) {
            return new float[] {0.0F, 0.0F};
        }

        try {
            Object data = getData(player);
            if (data == null) return new float[] {0.0F, 0.0F};
            Object resources = getResources.invoke(data);
            if (resources == null) return new float[] {0.0F, 0.0F};

            float maxEnergy = asFloat(getMaxEnergy.invoke(data));
            float maxStamina = asFloat(getMaxStamina.invoke(data));
            return new float[] {
                percent(asFloat(getCurrentEnergy.invoke(resources)), maxEnergy),
                percent(asFloat(getCurrentStamina.invoke(resources)), maxStamina)
            };
        } catch (Throwable throwable) {
            logFailureOnce("Could not read DragonMine Z meditation resource feedback.", throwable);
            return new float[] {0.0F, 0.0F};
        }
    }

    public static void sync(ServerPlayer player) {
        if (player == null || !resolve()) return;
        try {
            Object packet = statsSyncCtor.newInstance(player);
            sendToTrackingAndSelf.invoke(null, packet, player);
        } catch (Throwable throwable) {
            logFailureOnce("Meditation changed DMZ data but its sync packet could not be sent immediately.", throwable);
        }
    }

    private record FormContext(
        String group,
        String form,
        boolean stack,
        Object masteries,
        Object formData
    ) {}

    private static FormContext activeFormContext(Object character) throws Exception {
        boolean stack = asBoolean(hasActiveStackForm.invoke(character));
        if (stack) {
            String group = asString(getActiveStackFormGroup.invoke(character));
            String form = asString(getActiveStackForm.invoke(character));
            if (!group.isEmpty() && !form.isEmpty()) {
                return new FormContext(
                    group,
                    form,
                    true,
                    getStackFormMasteries.invoke(character),
                    getActiveStackFormData.invoke(character)
                );
            }
        }

        boolean normal = asBoolean(hasActiveForm.invoke(character));
        if (normal) {
            String group = asString(getActiveFormGroup.invoke(character));
            String form = asString(getActiveForm.invoke(character));
            if (!group.isEmpty() && !form.isEmpty()) {
                return new FormContext(
                    group,
                    form,
                    false,
                    getFormMasteries.invoke(character),
                    getActiveFormData.invoke(character)
                );
            }
        }

        return null;
    }

    private static double masteryValue(Object masteries, String group, String form) throws Exception {
        if (masteries == null) return 0.0D;
        Object raw = getMastery.invoke(masteries, group, form);
        return raw instanceof Number n ? Math.max(0.0D, n.doubleValue()) : 0.0D;
    }

    private static double maxMastery(Object formData) throws Exception {
        if (formData == null) return 100.0D;
        Object raw = getMaxMastery.invoke(formData);
        return raw instanceof Number n ? Math.max(0.0D, n.doubleValue()) : 100.0D;
    }

    private static Object getData(ServerPlayer player) {
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            LazyOptional<?> optional = player.getCapability((Capability) capability);
            return optional.resolve().orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean resolve() {
        if (attempted) return available;
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
            getResources = dataClass.getMethod("getResources");
            getCharacter = dataClass.getMethod("getCharacter");
            getMaxEnergy = dataClass.getMethod("getMaxEnergy");
            getMaxStamina = dataClass.getMethod("getMaxStamina");

            try {
                getStats = dataClass.getMethod("getStats");
                Class<?> statsClass = getStats.getReturnType();
                getTotalStats = statsClass.getMethod("getTotalStats");
                for (Method method : dataClass.getMethods()) {
                    if (method.getName().equals("getSingleStatCost")
                        && method.getParameterCount() == 1) {
                        getSingleStatCost = method;
                        break;
                    }
                }
            } catch (Throwable ignored) {
                getStats = null;
                getTotalStats = null;
                getSingleStatCost = null;
            }

            try {
                Class<?> configManagerClass =
                    Class.forName("com.dragonminez.common.config.ConfigManager");
                getTrainingConfig = configManagerClass.getMethod("getTrainingConfig");
                Class<?> trainingConfigClass = getTrainingConfig.getReturnType();
                for (Method method : trainingConfigClass.getMethods()) {
                    if (method.getName().equals("computeTpsPerLevel")
                        && method.getParameterCount() == 2) {
                        computeTpsPerLevel = method;
                        break;
                    }
                }
                getSettings = trainingConfigClass.getMethod("getSettings", String.class);
                getTpsLimitPerGame = getSettings.getReturnType().getMethod("getTpsLimitPerGame");
            } catch (Throwable ignored) {
                getTrainingConfig = null;
                computeTpsPerLevel = null;
                getSettings = null;
                getTpsLimitPerGame = null;
            }


            Class<?> resourcesClass = getResources.getReturnType();
            getCurrentEnergy = resourcesClass.getMethod("getCurrentEnergy");
            getCurrentStamina = resourcesClass.getMethod("getCurrentStamina");

            try {
                getTrainingPoints =
                    resourcesClass.getMethod("getTrainingPoints");
            } catch (Throwable ignored) {
                getTrainingPoints = null;
            }

            addEnergy = resourcesClass.getMethod("addEnergy", float.class);
            addStamina = resourcesClass.getMethod("addStamina", float.class);

            Class<?> characterClass = getCharacter.getReturnType();
            hasActiveForm = characterClass.getMethod("hasActiveForm");
            getActiveFormGroup = characterClass.getMethod("getActiveFormGroup");
            getActiveForm = characterClass.getMethod("getActiveForm");
            getActiveFormData = characterClass.getMethod("getActiveFormData");
            getFormMasteries = characterClass.getMethod("getFormMasteries");

            hasActiveStackForm = characterClass.getMethod("hasActiveStackForm");
            getActiveStackFormGroup = characterClass.getMethod("getActiveStackFormGroup");
            getActiveStackForm = characterClass.getMethod("getActiveStackForm");
            getActiveStackFormData = characterClass.getMethod("getActiveStackFormData");
            getStackFormMasteries = characterClass.getMethod("getStackFormMasteries");
            gainMastery = characterClass.getMethod("gainMastery", String.class, String.class, double.class);

            Class<?> masteriesClass = getFormMasteries.getReturnType();
            getMastery = masteriesClass.getMethod("getMastery", String.class, String.class);

            Class<?> formDataClass = getActiveFormData.getReturnType();
            getMaxMastery = formDataClass.getMethod("getMaxMastery");

            Class<?> syncClass = Class.forName("com.dragonminez.common.network.S2C.StatsSyncS2C");
            for (Constructor<?> ctor : syncClass.getConstructors()) {
                if (ctor.getParameterCount() == 1) {
                    statsSyncCtor = ctor;
                    break;
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
            logFailureOnce(
                "DragonMine Z resource/form APIs were not found; meditation recovery and form mastery are disabled.",
                throwable
            );
        }

        return available;
    }

    private static Object coerceNumber(Object value, Class<?> target) {
        Number number = value instanceof Number n ? n : 0;
        if (target == int.class || target == Integer.class) return number.intValue();
        if (target == long.class || target == Long.class) return number.longValue();
        if (target == float.class || target == Float.class) return number.floatValue();
        if (target == double.class || target == Double.class) return number.doubleValue();
        if (target == short.class || target == Short.class) return number.shortValue();
        if (target == byte.class || target == Byte.class) return number.byteValue();
        return value;
    }

    private static float percent(float current, float max) {
        if (max <= 0.0F) return 0.0F;
        return Math.max(0.0F, Math.min(100.0F, current * 100.0F / max));
    }

    private static float asFloat(Object value) {
        return value instanceof Number n ? n.floatValue() : 0.0F;
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean b && b;
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : "";
    }

    private static void logFailureOnce(String message, Throwable throwable) {
        if (failureLogged) return;
        failureLogged = true;
        DBZMeditation.LOGGER.warn(message, throwable);
    }
}
