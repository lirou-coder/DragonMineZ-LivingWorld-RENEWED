package com.kunyo.dbzmeditation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Narrow compatibility bridge that keeps DMZ Dynamic Growth receiving gravity-training practice
 * while the meditation carrier has the player mounted under training gravity. It calls DMZ's own DynamicGrowthService;
 * caps, disabled stats and server growth multipliers therefore remain authoritative.
 */
public final class DMZGravityGrowthBridge {
    private static boolean attempted, available, logged;
    private static Capability<?> capability;
    private static Method award;
    private static Class<? extends Enum> growthStatClass;

    private DMZGravityGrowthBridge() {}

    public static void pulse(ServerPlayer player, double trainingGravity, int meditationTicks) {
        if (player == null || trainingGravity <= 0.0001D || !resolve()) return;
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            LazyOptional<?> optional = player.getCapability((Capability) capability);
            Object data = optional.resolve().orElse(null);
            if (data == null) return;
            // Rotate body-focused practice instead of awarding every stat every second.
            String stat = switch (Math.floorMod(meditationTicks / 20, 3)) {
                case 0 -> "RES";
                case 1 -> "VIT";
                default -> "STR";
            };
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object growthStat = Enum.valueOf((Class) growthStatClass, stat);
            double baseXp = Math.min(90.0D, 1.5D + Math.sqrt(Math.max(0.0D, trainingGravity)) * 1.8D);
            award.invoke(null, player, data, growthStat, baseXp, (LivingEntity)null);
        } catch (Throwable throwable) {
            if (!logged) {
                logged = true;
                DBZMeditation.LOGGER.warn("Could not preserve DMZ gravity Dynamic Growth during meditation.", throwable);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean resolve() {
        if (attempted) return available;
        attempted = true;
        try {
            Class<?> capClass = Class.forName("com.dragonminez.common.stats.StatsCapability");
            Field instance = capClass.getField("INSTANCE");
            capability = (Capability<?>) instance.get(null);
            Class<?> dataClass = Class.forName("com.dragonminez.common.stats.StatsData");
            growthStatClass = (Class<? extends Enum>) Class.forName("com.dragonminez.common.stats.extras.DynamicGrowthStat");
            Class<?> service = Class.forName("com.dragonminez.server.dynamicgrowth.DynamicGrowthService");
            award = service.getMethod("award", ServerPlayer.class, dataClass, growthStatClass, double.class, LivingEntity.class);
            available = true;
        } catch (Throwable throwable) {
            available = false;
            if (!logged) {
                logged = true;
                DBZMeditation.LOGGER.warn("DMZ Dynamic Growth gravity compatibility bridge is unavailable.", throwable);
            }
        }
        return available;
    }
}
