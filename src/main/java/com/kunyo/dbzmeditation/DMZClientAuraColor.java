package com.kunyo.dbzmeditation;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-only source of truth for the color DragonMine Z is rendering now.
 *
 * This intentionally uses reflection so the addon can compile in a normal
 * Forge MDK without putting DragonMine Z on javac's compile classpath.
 */
public final class DMZClientAuraColor {
    private static volatile String lastSource =
        "fallback:common-bridge";

    private static boolean attempted;
    private static boolean available;

    private static Capability<?> capability;
    private static Method getAuraLayers;

    /*
     * Meditation can request aura color dozens of times in one client tick
     * (motes, wisps, glyphs and HUD). AuraRenderer#getAuraLayers builds
     * collections, so resolve it once per entity per game tick and reuse the
     * color for the rest of that tick.
     */
    private static final Map<Integer, float[]> TICK_COLOR_CACHE =
        new HashMap<>();

    private static long cachedGameTime =
        Long.MIN_VALUE;

    private static Object cachedLevelIdentity;

    private DMZClientAuraColor() {}

    /** Release references to the previous client world as soon as it unloads. */
    public static void clearClientState() {
        TICK_COLOR_CACHE.clear();
        cachedGameTime = Long.MIN_VALUE;
        cachedLevelIdentity = null;
    }

    public static float[] getRgb(
        Player player,
        float partialTick
    ) {
        if (player == null) {
            return new float[] {
                1.0F,
                1.0F,
                1.0F
            };
        }

        long gameTime =
            player.level().getGameTime();

        Object levelIdentity =
            player.level();

        if (cachedGameTime != gameTime
            || cachedLevelIdentity
                != levelIdentity) {

            TICK_COLOR_CACHE.clear();
            cachedGameTime = gameTime;
            cachedLevelIdentity =
                levelIdentity;
        }

        float[] cached =
            TICK_COLOR_CACHE.get(
                player.getId()
            );

        if (cached != null) {
            return cached.clone();
        }

        float[] resolved =
            resolveLiveAura(
                player,
                partialTick
            );

        TICK_COLOR_CACHE.put(
            player.getId(),
            resolved.clone()
        );

        return resolved;
    }

    /**
     * Source of truth order:
     *
     * 1) Dragon Mine Z's own AuraRenderer composition, in EVERY form including
     *    base/untransformed.
     * 2) Character's raw player-selected AuraColor appearance value.
     * 3) white only if DMZ's aura data genuinely cannot be read.
     *
     * Previous releases incorrectly skipped AuraRenderer while untransformed.
     * That is why transformation colors could track perfectly while the base
     * meditation aura still appeared as an unrelated gold/default color.
     */
    private static float[] resolveLiveAura(
        Player player,
        float partialTick
    ) {
        try {
            if (!resolve()) {
                return fallbackBase(player);
            }

            Object stats =
                getStats(player);

            if (stats == null) {
                return fallbackBase(player);
            }

            Object rawLayers =
                getAuraLayers.invoke(
                    null,
                    player,
                    stats,
                    partialTick
                );

            if (!(rawLayers
                    instanceof List<?> layers)
                || layers.isEmpty()) {

                return fallbackBase(player);
            }

            float[] resolved = null;
            int topLayer = -1;

            /*
             * Mirror DMZ's actual ordered aura-layer composition. This also
             * naturally continues to handle transformed/stack-form colors.
             */
            for (Object layer : layers) {
                if (layer == null) {
                    continue;
                }

                float[] color =
                    normalizeColor(
                        readMember(
                            layer,
                            "color"
                        )
                    );

                if (color == null) {
                    continue;
                }

                float alpha =
                    clamp(
                        asFloat(
                            readMember(
                                layer,
                                "alpha"
                            ),
                            1.0F
                        )
                    );

                if (resolved == null) {
                    resolved =
                        color.clone();
                } else {
                    resolved[0] +=
                        (color[0]
                            - resolved[0])
                            * alpha;

                    resolved[1] +=
                        (color[1]
                            - resolved[1])
                            * alpha;

                    resolved[2] +=
                        (color[2]
                            - resolved[2])
                            * alpha;
                }

                topLayer =
                    asInt(
                        readMember(
                            layer,
                            "layerId"
                        ),
                        topLayer
                    );
            }

            if (resolved != null) {
                lastSource =
                    DMZKiColorBridge
                        .hasActiveTransformation(
                            player
                        )
                        ? "dmz:AuraRenderer transformed layer="
                            + topLayer
                        : "dmz:AuraRenderer base layer="
                            + topLayer;

                return resolved;
            }
        } catch (Throwable throwable) {
            DBZMeditation.LOGGER.debug(
                "DragonMine Z AuraRenderer color resolver was unavailable; using the player's raw base aura color.",
                throwable
            );
        }

        return fallbackBase(player);
    }


    public static int getReadableRgbInt(
        Player player,
        float partialTick
    ) {
        float[] rgb =
            getRgb(player, partialTick);

        float max =
            Math.max(
                rgb[0],
                Math.max(rgb[1], rgb[2])
            );

        if (max < 0.55F) {
            float lift = 0.55F - max;
            rgb[0] = clamp(rgb[0] + lift);
            rgb[1] = clamp(rgb[1] + lift);
            rgb[2] = clamp(rgb[2] + lift);
        }

        int r =
            Math.round(rgb[0] * 255.0F);
        int g =
            Math.round(rgb[1] * 255.0F);
        int b =
            Math.round(rgb[2] * 255.0F);

        return (r << 16) | (g << 8) | b;
    }

    public static String getLastSource() {
        return lastSource;
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

            Object rawCapability =
                capClass
                    .getField("INSTANCE")
                    .get(null);

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

            Class<?> auraRendererClass =
                Class.forName(
                    "com.dragonminez.client.render.effects.AuraRenderer"
                );

            getAuraLayers =
                auraRendererClass.getDeclaredMethod(
                    "getAuraLayers",
                    Player.class,
                    statsDataClass,
                    float.class
                );

            getAuraLayers.setAccessible(true);
            available = true;
        } catch (Throwable throwable) {
            available = false;

            DBZMeditation.LOGGER.debug(
                "Could not bind DragonMine Z AuraRenderer reflection bridge.",
                throwable
            );
        }

        return available;
    }

    private static Object getStats(Player player) {
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            LazyOptional<?> optional =
                player.getCapability((Capability) capability);

            return optional.resolve().orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object readMember(
        Object source,
        String name
    ) {
        Class<?> type =
            source.getClass();

        try {
            Field field =
                type.getField(name);

            return field.get(source);
        } catch (Throwable ignored) {}

        try {
            Field field =
                type.getDeclaredField(name);

            field.setAccessible(true);
            return field.get(source);
        } catch (Throwable ignored) {}

        try {
            Method method =
                type.getMethod(name);

            return method.invoke(source);
        } catch (Throwable ignored) {}

        try {
            Method method =
                type.getDeclaredMethod(name);

            method.setAccessible(true);
            return method.invoke(source);
        } catch (Throwable ignored) {}

        return null;
    }

    private static float[] normalizeColor(Object value) {
        if (value instanceof float[] rgb
            && rgb.length >= 3) {

            return new float[] {
                clamp(rgb[0]),
                clamp(rgb[1]),
                clamp(rgb[2])
            };
        }

        if (value instanceof double[] rgb
            && rgb.length >= 3) {

            return new float[] {
                clamp((float) rgb[0]),
                clamp((float) rgb[1]),
                clamp((float) rgb[2])
            };
        }

        if (value instanceof int[] rgb
            && rgb.length >= 3) {

            float divisor =
                rgb[0] > 1 || rgb[1] > 1 || rgb[2] > 1
                    ? 255.0F
                    : 1.0F;

            return new float[] {
                clamp(rgb[0] / divisor),
                clamp(rgb[1] / divisor),
                clamp(rgb[2] / divisor)
            };
        }

        return null;
    }

    private static float[] fallbackBase(
        Player player
    ) {
        float[] rgb =
            DMZKiColorBridge
                .getBaseAuraRgb(
                    player
                );

        lastSource =
            "fallback:raw-character-aura/"
                + DMZKiColorBridge
                    .getLastSource();

        return rgb;
    }

    private static float asFloat(
        Object value,
        float fallback
    ) {
        return value instanceof Number n
            ? n.floatValue()
            : fallback;
    }

    private static int asInt(
        Object value,
        int fallback
    ) {
        return value instanceof Number n
            ? n.intValue()
            : fallback;
    }

    private static float clamp(float value) {
        return Math.max(
            0.0F,
            Math.min(1.0F, value)
        );
    }
}
