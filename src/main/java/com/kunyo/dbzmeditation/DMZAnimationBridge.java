package com.kunyo.dbzmeditation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Optional integration with DragonMine Z's own player animation system.
 *
 * No DragonMine Z classes are referenced at compile time. Reflection is intentional:
 * if DMZ changes its animation interface later, this addon simply stops requesting
 * the native animation and the existing meditation seat remains as a visual fallback.
 */
public final class DMZAnimationBridge {
    private static final String INTERFACE_NAME =
        "com.dragonminez.client.animation.IPlayerAnimatable";

    private static final String PLAY_METHOD =
        "dragonminez$playKiAnimation";

    private static final String STOP_METHOD =
        "dragonminez$stopKiAnimation";

    private static final String MEDITATION_ANIMATION =
        "base.meditation";

    private static Class<?> animatableInterface;
    private static Method playMethod;
    private static Method stopMethod;

    private static boolean resolutionAttempted = false;
    private static boolean available = false;
    private static boolean failureLogged = false;

    private static final Set<UUID> ACTIVE = new HashSet<>();

    private DMZAnimationBridge() {}

    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }

        if (!MeditationConfig.CLIENT.nativeDmzMeditationAnimation.get()) {
            stopAllKnown(mc);
            return;
        }

        if (!resolve()) {
            return;
        }

        Set<UUID> meditatingNow = new HashSet<>();

        for (AbstractClientPlayer player : mc.level.players()) {
            boolean meditating =
                DBZMeditation.isMeditationSeat(player.getVehicle());

            if (!meditating) {
                continue;
            }

            meditatingNow.add(player.getUUID());

            if (!ACTIVE.contains(player.getUUID())) {
                startNativeMeditation(player);
            }
        }

        Set<UUID> ending = new HashSet<>(ACTIVE);
        ending.removeAll(meditatingNow);

        for (UUID uuid : ending) {
            var rawPlayer =
                mc.level.getPlayerByUUID(uuid);

            if (rawPlayer instanceof AbstractClientPlayer player) {
                stopNativeMeditation(player);
            } else {
                ACTIVE.remove(uuid);
            }
        }
    }

    public static boolean isNativeAnimationAvailable() {
        return resolve();
    }

    /** Drop world-scoped client state immediately on disconnect/world unload. */
    public static void clearClientState() {
        ACTIVE.clear();
    }

    private static boolean resolve() {
        if (resolutionAttempted) {
            return available;
        }

        resolutionAttempted = true;

        try {
            animatableInterface =
                Class.forName(INTERFACE_NAME);

            playMethod = animatableInterface.getMethod(
                PLAY_METHOD,
                String.class,
                boolean.class
            );

            stopMethod = animatableInterface.getMethod(
                STOP_METHOD
            );

            available = true;
        } catch (Throwable throwable) {
            available = false;
            logFailureOnce(
                "DragonMine Z native meditation animation API was not found. "
                    + "Using the safe seated fallback.",
                throwable
            );
        }

        return available;
    }

    private static void startNativeMeditation(
        AbstractClientPlayer player
    ) {
        if (!animatableInterface.isInstance(player)) {
            return;
        }

        try {
            /*
             * hold=true makes DMZ's main animation controller retain the
             * base.meditation loop until we explicitly stop it.
             */
            playMethod.invoke(
                player,
                MEDITATION_ANIMATION,
                true
            );

            ACTIVE.add(player.getUUID());
        } catch (Throwable throwable) {
            logFailureOnce(
                "Could not start DragonMine Z's native meditation animation. "
                    + "Using the seated fallback.",
                throwable
            );
        }
    }

    private static void stopNativeMeditation(
        AbstractClientPlayer player
    ) {
        if (!animatableInterface.isInstance(player)) {
            ACTIVE.remove(player.getUUID());
            return;
        }

        try {
            stopMethod.invoke(player);
        } catch (Throwable throwable) {
            logFailureOnce(
                "Could not stop DragonMine Z's native meditation animation cleanly.",
                throwable
            );
        } finally {
            ACTIVE.remove(player.getUUID());
        }
    }

    private static void stopAllKnown(Minecraft mc) {
        if (!available || mc.level == null) {
            ACTIVE.clear();
            return;
        }

        Set<UUID> copy = new HashSet<>(ACTIVE);

        for (UUID uuid : copy) {
            var rawPlayer =
                mc.level.getPlayerByUUID(uuid);

            if (rawPlayer instanceof AbstractClientPlayer player) {
                stopNativeMeditation(player);
            } else {
                ACTIVE.remove(uuid);
            }
        }
    }

    private static void logFailureOnce(
        String message,
        Throwable throwable
    ) {
        if (failureLogged) {
            return;
        }

        failureLogged = true;
        DBZMeditation.LOGGER.warn(message, throwable);
    }
}
