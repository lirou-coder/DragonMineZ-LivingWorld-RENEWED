package com.dmzlivingworld.client;

import com.dmzlivingworld.LivingWorldMod;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/** Central key factory for client-visible Living World text. */
public final class LWLang {
    /**
     * The entity data watcher can only synchronize a String.  Prefixing a key lets the
     * server send a dialogue identifier instead of an already-English sentence, so the
     * receiving client resolves it in its own language.  Ordinary strings are retained
     * for compatibility with datapacks and user-created dialogue.
     */
    private static final String SPEECH_KEY_PREFIX = "\\u0001lw:";
    private static final String SPEECH_CONTROL_PREFIX = String.valueOf((char) 1) + "lw:";
    private static final String SPEECH_FALLBACK_SEPARATOR = "\\u0002";
    private static final String SPEECH_ARGUMENT_SEPARATOR = "\\u0003";
    private static final String ENCODED_ARGUMENT_PREFIX = "b64:";
    private LWLang() {}

    public static Component text(String key, Object... arguments) {
        return Component.translatable(LivingWorldMod.MOD_ID + "." + key, arguments);
    }

    public static String string(String key, Object... arguments) {
        return text(key, arguments).getString();
    }

    /** Localizes server-provided enum/display labels while retaining mod/datapack fallbacks. */
    public static String label(String category, String fallback) {
        if (fallback == null || fallback.isBlank()) return fallback == null ? "" : fallback;
        String slug = fallback.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return Component.translatableWithFallback(LivingWorldMod.MOD_ID + ".label." + category + "." + slug, fallback).getString();
    }

    /** Encodes generated faction names word-by-word, while NPC personal names remain untouched. */
    public static String factionNameKey(String name) {
        if (name == null || name.isBlank()) return "";
        String[] words = name.trim().split("\\s+");
        Object[] translated = new Object[words.length];
        for (int i = 0; i < words.length; i++) translated[i] = speechKey("label.faction_word." + slug(words[i]), words[i]);
        String fallback = String.join(" ", words);
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < words.length; i++) pattern.append(i == 0 ? "%s" : " %s");
        return speechKey("label.faction_name.parts_" + words.length, pattern.toString(), translated);
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    public static String speechKey(String key) {
        return SPEECH_KEY_PREFIX + key;
    }

    public static String speechKey(String key, String fallback) {
        return speechKey(key) + SPEECH_FALLBACK_SEPARATOR + (fallback == null ? "" : fallback);
    }

    public static String speechKey(String key, String fallback, Object... arguments) {
        StringBuilder encoded = new StringBuilder(speechKey(key, fallback));
        for (Object argument : arguments) {
            String raw = argument == null ? "" : String.valueOf(argument);
            encoded.append(SPEECH_ARGUMENT_SEPARATOR).append(ENCODED_ARGUMENT_PREFIX)
                    .append(Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        }
        return encoded.toString();
    }

    public static boolean isSpeechKey(String value) {
        return value != null && (value.startsWith(SPEECH_KEY_PREFIX) || value.startsWith(SPEECH_CONTROL_PREFIX));
    }

    public static Component speech(String value) {
        if (isSpeechKey(value)) {
            String prefix = value.startsWith(SPEECH_CONTROL_PREFIX) ? SPEECH_CONTROL_PREFIX : SPEECH_KEY_PREFIX;
            String encoded = value.substring(prefix.length());
            int split = encoded.indexOf(SPEECH_FALLBACK_SEPARATOR);
            if (split >= 0) {
                String tail = encoded.substring(split + SPEECH_FALLBACK_SEPARATOR.length());
                String[] pieces = tail.split(java.util.regex.Pattern.quote(SPEECH_ARGUMENT_SEPARATOR), -1);
                String fallback = pieces.length == 0 ? "" : pieces[0];
                Object[] arguments = new Object[Math.max(0, pieces.length - 1)];
                for (int i = 1; i < pieces.length; i++) {
                    String argument = decodeArgument(pieces[i]);
                    arguments[i - 1] = containsSpeechKey(argument) ? speechEmbedded(argument) : argument;
                }
                String key = encoded.substring(0, split);
                if (key.startsWith("message.incident.type.") && arguments.length >= 3) {
                    String suffix = key.substring("message.incident.type.".length());
                    return Component.translatableWithFallback(LivingWorldMod.MOD_ID + ".message.incident.v2.type." + suffix,
                            "%s vs %s", arguments[1], arguments[2]);
                }
                if (key.startsWith("message.incident.resolved.") && arguments.length >= 3) {
                    String suffix = key.substring("message.incident.resolved.".length());
                    return Component.translatableWithFallback(LivingWorldMod.MOD_ID + ".message.incident.v2.resolved." + suffix,
                            "%s defeated %s", arguments[1], arguments[2]);
                }
                return Component.translatableWithFallback(LivingWorldMod.MOD_ID + "." + key, fallback, arguments);
            }
            return text(encoded);
        }
        return Component.literal(value == null ? "" : value);
    }

    /** Resolves a localized payload even when a UI protocol marker precedes it. */
    public static Component speechEmbedded(String value) {
        if (value == null || value.isEmpty()) return Component.empty();
        int marker = value.indexOf(SPEECH_KEY_PREFIX);
        String markerPrefix = SPEECH_KEY_PREFIX;
        int controlMarker = value.indexOf(SPEECH_CONTROL_PREFIX);
        if (marker < 0 || (controlMarker >= 0 && controlMarker < marker)) {
            marker = controlMarker;
            markerPrefix = SPEECH_CONTROL_PREFIX;
        }
        if (marker < 0) return Component.literal(value);
        if (marker == 0) return speech(value);
        return Component.literal(value.substring(0, marker)).append(speech(markerPrefix + value.substring(marker + markerPrefix.length())));
    }

    private static boolean containsSpeechKey(String value) {
        return value != null && (value.contains(SPEECH_KEY_PREFIX) || value.contains(SPEECH_CONTROL_PREFIX));
    }

    private static String decodeArgument(String value) {
        if (value == null || !value.startsWith(ENCODED_ARGUMENT_PREFIX)) return value == null ? "" : value;
        try {
            byte[] decoded = Base64.getDecoder().decode(value.substring(ENCODED_ARGUMENT_PREFIX.length()));
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }
}
