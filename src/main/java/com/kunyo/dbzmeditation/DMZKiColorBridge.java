package com.kunyo.dbzmeditation;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import java.lang.reflect.Method;

/**
 * Reads DragonMine Z's effective character/form aura identity without making
 * DragonMine Z a compile-time dependency.
 *
 * 3.2 intentionally stops doing broad reflection guesses. DMZ 2.1 exposes the
 * exact data we need on Character/FormData:
 *   Character#getActiveStackFormData
 *   Character#getActiveFormData
 *   FormData#hasAuraColorOverride
 *   FormData#getRgbAuraColor
 *   FormData#getOutlineShader
 *
 * Aura-effect precedence mirrors DMZ AuraRenderer:
 *   active stack-form aura -> active form aura -> base character aura.
 *
 * Outline color remains a separate concern. It is NEVER allowed to replace
 * meditation ki/aura color.
 */
public final class DMZKiColorBridge {
    private static final float[] WHITE =
        new float[] {1.0F, 1.0F, 1.0F};

    private static boolean resolutionAttempted = false;
    private static boolean available = false;
    private static boolean failureLogged = false;

    private static volatile String lastSource = "fallback:white";
    private static volatile String lastOutlineSource = "fallback:aura";
    private static volatile String lastEffectSource = "fallback:white";

    private static Capability<?> statsCapability;
    private static Method getCharacterMethod;
    private static Method getBaseAuraMethod;
    private static Method getBaseAuraHexMethod;
    private static Method getActiveFormDataMethod;
    private static Method getActiveStackFormDataMethod;

    private static Method hasAuraColorOverrideMethod;
    private static Method getFormAuraColorMethod;
    private static Method getFormAuraRgbMethod;
    private static Method getOutlineShaderMethod;
    private static Method outlineEnabledMethod;
    private static Method outlinePrimaryColorMethod;
    private static Method outlineThicknessMethod;

    private DMZKiColorBridge() {}

    /**
     * Mirrors DragonMine Z AuraRenderer's CURRENT aura-color precedence.
     *
     * DMZ itself does NOT use the form outline color as the aura color.
     * It starts from the character aura, then replaces the normal aura with
     * the active form aura when FormData#getAuraColor() is non-empty, and
     * renders an active stack form as an additional aura layer.
     *
     * Meditation uses the uppermost active aura identity:
     * active stack-form aura -> active form aura -> character aura.
     */
    public static float[] getAuraRgb(Player player) {
        Context ctx = context(player);
        if (ctx == null) {
            lastSource = "fallback:white";
            return WHITE.clone();
        }

        try {
            /*
             * Prefer Character#getAuraColor() -- the raw appearance value the
             * player actually selected. getRgbAuraColor() is a cached derived
             * array in DMZ and can be stale in some client-sync situations.
             */
            float[] normal =
                getRawCharacterAuraRgb(
                    ctx.character()
                );

            String normalSource =
                normal != null
                    ? "character:raw-base-aura"
                    : "fallback:white";

            Object form =
                safeInvoke(
                    ctx.character(),
                    getActiveFormDataMethod
                );

            float[] formColor =
                getFormAuraIfPresent(form);

            if (formColor != null) {
                normal = formColor;
                normalSource = "form:aura";
            }

            Object stack =
                safeInvoke(
                    ctx.character(),
                    getActiveStackFormDataMethod
                );

            float[] stackColor =
                getFormAuraIfPresent(stack);

            if (stackColor != null) {
                lastSource = "stack-form:aura";
                return stackColor;
            }

            if (normal != null) {
                lastSource = normalSource;
                return normal;
            }
        } catch (Throwable throwable) {
            logFailureOnce(
                "Could not resolve DragonMine Z current aura color. Meditation will use white.",
                throwable
            );
        }

        lastSource = "fallback:white";
        return WHITE.clone();
    }

    /**
     * Effect color is now ALWAYS the actual DMZ aura color.
     *
     * 3.2/3.3 incorrectly allowed an outline-shader primary color to become
     * the meditation particle color. That is what could produce orange/gold
     * effects even when the player's actual ki/aura was another color.
     */
    public static float[] getEffectRgb(Player player) {
        float[] rgb = getAuraRgb(player);
        lastEffectSource = lastSource;
        return rgb;
    }

    /**
     * Raw character-selected/base ki color.  This is deliberately separate
     * from the live transformed AuraRenderer color so untransformed
     * meditation never inherits an unrelated renderer layer/fallback tint.
     */
    public static float[] getBaseAuraRgb(Player player) {
        Context ctx =
            context(player);

        if (ctx == null) {
            lastSource = "fallback:white";
            return WHITE.clone();
        }

        float[] rgb =
            getRawCharacterAuraRgb(
                ctx.character()
            );

        if (rgb != null) {
            lastSource =
                "character:raw-base-aura";
            return rgb;
        }

        lastSource =
            "fallback:white";
        return WHITE.clone();
    }

    /**
     * Character#auraColor is DMZ's serialized/customized appearance source of
     * truth. Read its raw hex first; only use getRgbAuraColor() if the raw
     * getter is unavailable in a compatible DMZ build.
     */
    private static float[] getRawCharacterAuraRgb(
        Object character
    ) {
        if (character == null) {
            return null;
        }

        Object rawHex =
            safeInvoke(
                character,
                getBaseAuraHexMethod
            );

        if (rawHex instanceof String hex
            && !hex.isBlank()) {

            float[] parsed =
                hexToRgb(hex);

            if (parsed != null) {
                return parsed;
            }
        }

        return normalizeColor(
            safeInvoke(
                character,
                getBaseAuraMethod
            )
        );
    }


    /** True while DMZ reports a normal or stack transformation as active. */
    public static boolean hasActiveTransformation(Player player) {
        Context ctx = context(player);
        if (ctx == null) {
            return false;
        }

        return safeInvoke(
                ctx.character(),
                getActiveStackFormDataMethod
            ) != null
            || safeInvoke(
                ctx.character(),
                getActiveFormDataMethod
            ) != null;
    }

    /**
     * If the currently maintained form already owns a DMZ transformation
     * outline, meditation must not add a second mask/outline on top of it.
     */
    public static boolean hasActiveFormOutline(Player player) {
        Context ctx = context(player);
        if (ctx == null) {
            return false;
        }

        for (Object form : new Object[] {
            safeInvoke(
                ctx.character(),
                getActiveStackFormDataMethod
            ),
            safeInvoke(
                ctx.character(),
                getActiveFormDataMethod
            )
        }) {
            if (form == null) {
                continue;
            }

            Object shader =
                safeInvoke(
                    form,
                    getOutlineShaderMethod
                );

            if (shader != null
                && asBoolean(
                    safeInvoke(
                        shader,
                        outlineEnabledMethod
                    )
                )) {

                return true;
            }
        }

        return false;
    }

    public static float[] getOutlineRgb(Player player) {
        Context ctx = context(player);
        if (ctx != null) {
            try {
                Object stack = safeInvoke(ctx.character(), getActiveStackFormDataMethod);
                float[] stackOutline = getFormOutlineColor(stack);
                if (stackOutline != null) {
                    lastOutlineSource = "stack-form:outline";
                    return stackOutline;
                }

                Object form = safeInvoke(ctx.character(), getActiveFormDataMethod);
                float[] formOutline = getFormOutlineColor(form);
                if (formOutline != null) {
                    lastOutlineSource = "form:outline";
                    return formOutline;
                }
            } catch (Throwable ignored) {
            }
        }

        lastOutlineSource = "aura-color";
        return getAuraRgb(player);
    }

    public static float getOutlineScale(Player player, boolean transcendent) {
        double authoredThickness = getAuthoredOutlineThickness(player);

        // DMZ authored values are around shader-pixel thickness, not model scale.
        // Translate that into a conservative expanded-shell size.
        float extra =
            (float)Math.max(
                0.026D,
                Math.min(0.060D, 0.018D + authoredThickness * 0.010D)
            );

        if (transcendent) {
            extra += 0.007F;
        }

        return 1.0F + extra;
    }

    public static int getAuraRgbInt(Player player) {
        return rgbInt(getAuraRgb(player));
    }

    public static int getEffectRgbInt(Player player) {
        return rgbInt(getEffectRgb(player));
    }

    public static int getOutlineRgbInt(Player player) {
        return rgbInt(getOutlineRgb(player));
    }

    public static int getReadableAuraRgbInt(Player player) {
        float[] rgb = getEffectRgb(player);
        float max = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
        if (max < 0.55F) {
            float lift = 0.55F - max;
            rgb[0] = clamp(rgb[0] + lift);
            rgb[1] = clamp(rgb[1] + lift);
            rgb[2] = clamp(rgb[2] + lift);
        }
        return rgbInt(rgb);
    }

    public static String getLastSource() {
        return lastSource;
    }

    public static String getLastOutlineSource() {
        return lastOutlineSource;
    }

    public static String getLastEffectSource() {
        return lastEffectSource;
    }

    private static double getAuthoredOutlineThickness(Player player) {
        Context ctx = context(player);
        if (ctx == null) {
            return 1.5D;
        }

        for (Object form : new Object[] {
            safeInvoke(ctx.character(), getActiveStackFormDataMethod),
            safeInvoke(ctx.character(), getActiveFormDataMethod)
        }) {
            if (form == null) {
                continue;
            }

            Object shader = safeInvoke(form, getOutlineShaderMethod);
            if (shader == null || !asBoolean(safeInvoke(shader, outlineEnabledMethod))) {
                continue;
            }

            Object thickness = safeInvoke(shader, outlineThicknessMethod);
            if (thickness instanceof Number number) {
                return Math.max(0.0D, number.doubleValue());
            }
        }

        return 1.5D;
    }

    private static float[] getFormAuraIfPresent(Object form) {
        if (form == null) {
            return null;
        }

        Object rawHex =
            safeInvoke(
                form,
                getFormAuraColorMethod
            );

        if (!(rawHex instanceof String hex)
            || hex.isBlank()) {
            return null;
        }

        float[] rgb =
            normalizeColor(
                safeInvoke(
                    form,
                    getFormAuraRgbMethod
                )
            );

        return rgb != null
            ? rgb
            : hexToRgb(hex);
    }

    private static float[] getFormOutlineColor(Object form) {
        if (form == null) {
            return null;
        }

        Object shader = safeInvoke(form, getOutlineShaderMethod);
        if (shader == null || !asBoolean(safeInvoke(shader, outlineEnabledMethod))) {
            return null;
        }

        Object raw = safeInvoke(shader, outlinePrimaryColorMethod);
        if (raw instanceof String hex) {
            return hexToRgb(hex);
        }

        return normalizeColor(raw);
    }

    private static Context context(Player player) {
        if (player == null || !resolve()) {
            return null;
        }

        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            LazyOptional<?> optional =
                player.getCapability((Capability) statsCapability);

            Object stats = optional.resolve().orElse(null);
            if (stats == null) {
                return null;
            }

            Object character = safeInvoke(stats, getCharacterMethod);
            return character != null ? new Context(stats, character) : null;
        } catch (Throwable throwable) {
            logFailureOnce("Could not access DragonMine Z stats capability.", throwable);
            return null;
        }
    }

    private static boolean resolve() {
        if (resolutionAttempted) {
            return available;
        }

        resolutionAttempted = true;

        try {
            Class<?> statsCapabilityClass =
                Class.forName("com.dragonminez.common.stats.StatsCapability");
            Object cap = statsCapabilityClass.getField("INSTANCE").get(null);
            if (!(cap instanceof Capability<?> capability)) {
                throw new IllegalStateException("StatsCapability.INSTANCE is not a Forge Capability");
            }
            statsCapability = capability;

            Class<?> statsDataClass =
                Class.forName("com.dragonminez.common.stats.StatsData");
            getCharacterMethod = statsDataClass.getMethod("getCharacter");

            Class<?> characterClass =
                Class.forName("com.dragonminez.common.stats.character.Character");

            getBaseAuraMethod =
                characterClass.getMethod(
                    "getRgbAuraColor"
                );

            try {
                getBaseAuraHexMethod =
                    characterClass.getMethod(
                        "getAuraColor"
                    );
            } catch (Throwable ignored) {
                getBaseAuraHexMethod = null;
            }

            getActiveFormDataMethod =
                characterClass.getMethod(
                    "getActiveFormData"
                );
            getActiveStackFormDataMethod = characterClass.getMethod("getActiveStackFormData");

            Class<?> formDataClass =
                Class.forName("com.dragonminez.common.config.FormConfig$FormData");
            hasAuraColorOverrideMethod = formDataClass.getMethod("hasAuraColorOverride");
            getFormAuraColorMethod = formDataClass.getMethod("getAuraColor");
            getFormAuraRgbMethod = formDataClass.getMethod("getRgbAuraColor");
            getOutlineShaderMethod = formDataClass.getMethod("getOutlineShader");

            Class<?> outlineClass =
                Class.forName("com.dragonminez.common.config.FormConfig$FormData$OutlineShaderConfig");
            outlineEnabledMethod = outlineClass.getMethod("isEnabled");
            outlinePrimaryColorMethod = outlineClass.getMethod("getPrimaryColor");
            outlineThicknessMethod = outlineClass.getMethod("getOutlineThickness");

            available = true;
        } catch (Throwable throwable) {
            available = false;
            logFailureOnce(
                "DragonMine Z form/aura API was not found. Meditation will fall back to white.",
                throwable
            );
        }

        return available;
    }

    private static Object safeInvoke(Object target, Method method) {
        if (target == null || method == null) {
            return null;
        }
        try {
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static float[] normalizeColor(Object raw) {
        if (raw instanceof float[] rgb && rgb.length >= 3) {
            return new float[] { clamp(rgb[0]), clamp(rgb[1]), clamp(rgb[2]) };
        }
        if (raw instanceof String hex) {
            return hexToRgb(hex);
        }
        return null;
    }

    private static float[] hexToRgb(String value) {
        if (value == null) {
            return null;
        }

        String hex = value.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return null;
        }

        try {
            int packed = Integer.parseInt(hex, 16);
            return new float[] {
                ((packed >> 16) & 0xFF) / 255.0F,
                ((packed >> 8) & 0xFF) / 255.0F,
                (packed & 0xFF) / 255.0F
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int rgbInt(float[] rgb) {
        int r = Math.round(clamp(rgb[0]) * 255.0F);
        int g = Math.round(clamp(rgb[1]) * 255.0F);
        int b = Math.round(clamp(rgb[2]) * 255.0F);
        return (r << 16) | (g << 8) | b;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static void logFailureOnce(String message, Throwable throwable) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        DBZMeditation.LOGGER.warn(message, throwable);
    }

    private record Context(Object stats, Object character) {}
}
