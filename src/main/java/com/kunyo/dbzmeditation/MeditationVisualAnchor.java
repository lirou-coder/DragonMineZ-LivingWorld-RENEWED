package com.kunyo.dbzmeditation;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** One model-aware origin for every client meditation world-space visual. */
public final class MeditationVisualAnchor {
    private static final double NORMAL_SEATED_TORSO_HEIGHT = 1.54D;

    private static boolean attempted;
    private static boolean available;
    private static Capability<?> capability;
    private static Method getCharacter;
    private static Method getResolvedModelScaling;
    private static Method getRenderLogicKey;

    private MeditationVisualAnchor() {}

    public static Anchor resolve(Player player, float partialTick) {
        double x = Mth.lerp(partialTick, player.xOld, player.getX());
        double y = Mth.lerp(partialTick, player.yOld, player.getY());
        double z = Mth.lerp(partialTick, player.zOld, player.getZ());

        float scaleX = 1.0F;
        float scaleY = 1.0F;
        float scaleZ = 1.0F;

        try {
            Object stats = getStats(player);
            if (stats != null && resolve()) {
                Object character = getCharacter.invoke(stats);

                if (character != null) {
                    Object rawScaling = getResolvedModelScaling.invoke(character);

                    if (rawScaling instanceof Object[] scaling
                        && scaling.length >= 3) {

                        scaleX = safeScale(scaling[0]);
                        scaleY = safeScale(scaling[1]);
                        scaleZ = safeScale(scaling[2]);
                    }

                    Object rawLogicKey = getRenderLogicKey.invoke(character);
                    String logicKey =
                        rawLogicKey != null ? rawLogicKey.toString() : "";

                    if (logicKey.startsWith("oozaru")) {
                        scaleX = Math.max(0.1F, scaleX - 2.8F);
                        scaleY = Math.max(0.1F, scaleY - 2.8F);
                        scaleZ = Math.max(0.1F, scaleZ - 2.8F);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Normal player scaling remains a safe visual fallback.
        }

        float horizontal =
            Math.max(0.65F, (scaleX + scaleZ) * 0.5F);

        Vec3 torso = new Vec3(
            x,
            y + NORMAL_SEATED_TORSO_HEIGHT * scaleY,
            z
        );

        return new Anchor(
            torso,
            horizontal,
            Math.max(0.65F, scaleY)
        );
    }

    private static Object getStats(Player player) {
        if (player == null || !resolve()) {
            return null;
        }

        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            LazyOptional<?> optional =
                player.getCapability((Capability) capability);

            return optional.resolve().orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean resolve() {
        if (attempted) {
            return available;
        }

        attempted = true;

        try {
            Class<?> capClass =
                Class.forName(
                    "com.dragonminez.common.stats.StatsCapability"
                );

            Field instance =
                capClass.getField("INSTANCE");

            Object rawCapability =
                instance.get(null);

            if (!(rawCapability instanceof Capability<?> c)) {
                throw new IllegalStateException(
                    "StatsCapability.INSTANCE is not a Forge Capability"
                );
            }

            capability = c;

            Class<?> statsDataClass =
                Class.forName(
                    "com.dragonminez.common.stats.StatsData"
                );

            getCharacter =
                statsDataClass.getMethod("getCharacter");

            Class<?> characterClass =
                getCharacter.getReturnType();

            getResolvedModelScaling =
                characterClass.getMethod(
                    "getResolvedModelScaling"
                );

            getRenderLogicKey =
                characterClass.getMethod(
                    "getRenderLogicKey"
                );

            available = true;
        } catch (Throwable ignored) {
            available = false;
        }

        return available;
    }

    private static float safeScale(Object value) {
        if (!(value instanceof Number number)) {
            return 1.0F;
        }

        float result = number.floatValue();

        if (!Float.isFinite(result)) {
            return 1.0F;
        }

        return Mth.clamp(result, 0.1F, 8.0F);
    }

    public record Anchor(
        Vec3 torso,
        float horizontalScale,
        float verticalScale
    ) {}
}
