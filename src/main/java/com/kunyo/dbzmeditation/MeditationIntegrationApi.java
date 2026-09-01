package com.kunyo.dbzmeditation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Small, stable compatibility surface for optional addons such as Living World.
 *
 * This API deliberately exposes session/shared-visual interoperability only. It
 * does not expose direct TP, stat, resource-recovery or form-mastery grants, so
 * another addon cannot accidentally bypass Meditation's own balance rules.
 */
public final class MeditationIntegrationApi {
    private static final String EXTERNAL_PARTNERS = "dbzm_external_meditation_partners";
    private static final String EXTERNAL_PARTNERS_TICK = "dbzm_external_meditation_tick";
    private static final long EXTERNAL_FRESH_TICKS = 60L;

    private MeditationIntegrationApi() {}

    public static boolean isMeditating(ServerPlayer player) {
        return player != null && DBZMeditation.isMeditating(player);
    }

    /** Starts the normal Meditation session. All native cooldown/seat checks remain authoritative. */
    public static boolean tryStartMeditation(ServerPlayer player) {
        if (player == null) return false;
        if (DBZMeditation.isMeditating(player)) return true;
        DBZMeditation.startMeditationForIntegration(player);
        return DBZMeditation.isMeditating(player);
    }

    public static int groupMeditationRadius() {
        if (!MeditationConfig.SERVER.groupMeditation.get()) return 0;
        return MeditationConfig.SERVER.groupMeditationRadius.get();
    }

    /** Supplies nearby non-player partners for synchronized shared-meditation visuals only. */
    public static void updateExternalMeditationPartners(ServerPlayer player, int partners) {
        if (player == null) return;
        CompoundTag data = player.getPersistentData();
        int safe = Math.max(0, Math.min(8, partners));
        if (safe == 0 || !DBZMeditation.isMeditating(player) || !MeditationConfig.SERVER.groupMeditation.get()) {
            data.remove(EXTERNAL_PARTNERS);
            data.remove(EXTERNAL_PARTNERS_TICK);
            return;
        }
        data.putInt(EXTERNAL_PARTNERS, safe);
        data.putLong(EXTERNAL_PARTNERS_TICK, player.serverLevel().getGameTime());
    }

    public static void clearExternalMeditationPartners(ServerPlayer player) {
        if (player == null) return;
        player.getPersistentData().remove(EXTERNAL_PARTNERS);
        player.getPersistentData().remove(EXTERNAL_PARTNERS_TICK);
    }

    static int getFreshExternalMeditationPartners(ServerPlayer player) {
        if (player == null || !DBZMeditation.isMeditating(player)) return 0;
        CompoundTag data = player.getPersistentData();
        long stamp = data.getLong(EXTERNAL_PARTNERS_TICK);
        long now = player.serverLevel().getGameTime();
        if (stamp <= 0L || now - stamp > EXTERNAL_FRESH_TICKS) {
            if (data.contains(EXTERNAL_PARTNERS) || data.contains(EXTERNAL_PARTNERS_TICK)) {
                data.remove(EXTERNAL_PARTNERS);
                data.remove(EXTERNAL_PARTNERS_TICK);
            }
            return 0;
        }
        return Math.max(0, Math.min(8, data.getInt(EXTERNAL_PARTNERS)));
    }

    /** Uses Meditation's own particle registrations for a non-player meditation partner. */
    public static void spawnExternalMeditationStartVisual(ServerLevel level, LivingEntity entity) {
        if (level == null || entity == null || !entity.isAlive()) return;
        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        level.sendParticles(DBZMeditation.KI_BURST.get(), x, y + entity.getBbHeight() * 0.48D, z,
            5, 0.24D, 0.18D, 0.24D, 0.012D);
        level.sendParticles(DBZMeditation.KI_MOTE.get(), x, y + 0.34D, z,
            10, 0.50D, 0.12D, 0.50D, 0.006D);
    }

    /** Lightweight shared visual language; no rewards or state are granted here. */
    public static void spawnExternalMeditationVisual(ServerLevel level, LivingEntity entity, boolean shared) {
        if (level == null || entity == null || !entity.isAlive()) return;
        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        level.sendParticles(DBZMeditation.KI_MOTE.get(), x, y + entity.getBbHeight() * 0.55D, z,
            shared ? 3 : 2, 0.34D, 0.42D, 0.34D, 0.012D);
        if (entity.tickCount % 72 < 20) {
            level.sendParticles(DBZMeditation.GROUND_RUNE.get(), x, y + 0.04D, z,
                1, 0.02D, 0.01D, 0.02D, 0.0D);
        }
        if (entity.tickCount % 108 < 20) {
            level.sendParticles(DBZMeditation.MEDITATION_GLYPH.get(), x, y + entity.getBbHeight() * 0.72D, z,
                1, 0.10D, 0.12D, 0.10D, 0.0D);
        }
        if (shared) {
            // KI_ABSORB's data channels encode a target vector and therefore cannot be emitted
            // correctly by this target-less compatibility hook. Use a small shared pulse here;
            // player/NPC reciprocal streams are client-owned where both endpoints are known.
            level.sendParticles(DBZMeditation.KI_BURST.get(), x, y + entity.getBbHeight() * 0.62D, z,
                2, 0.18D, 0.18D, 0.18D, 0.008D);
        }
    }
}
