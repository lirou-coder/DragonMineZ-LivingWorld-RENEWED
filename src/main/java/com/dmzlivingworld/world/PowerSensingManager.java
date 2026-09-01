package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A subtle Dragon Ball-style sense for noteworthy nearby powers and active battles. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PowerSensingManager {
    private static final double RANGE = 640.0D;
    private static final ConcurrentHashMap<UUID, Long> NEXT_PULSE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, UUID> LAST_SIGNAL = new ConcurrentHashMap<>();

    private PowerSensingManager() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !LivingWorldConfig.automaticPowerSensing()) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) continue;
            if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) continue;
            long now = level.getGameTime();
            if (now < NEXT_PULSE.getOrDefault(player.getUUID(), 0L)) continue;
            NEXT_PULSE.put(player.getUUID(), now + 120L);
            sense(player, false);
        }
    }

    public static void clearRuntime(UUID playerId) {
        if (playerId == null) return;
        NEXT_PULSE.remove(playerId);
        LAST_SIGNAL.remove(playerId);
    }

    public static void clearRuntime() { NEXT_PULSE.clear(); LAST_SIGNAL.clear(); }
    public static int runtimeEntries() { return NEXT_PULSE.size() + LAST_SIGNAL.size(); }

    public static String senseNow(ServerPlayer player) {
        Sensed sensed = findBest(player);
        if (sensed == null) return "You don't sense any noteworthy power nearby.";
        return describe(player, sensed);
    }

    private static void sense(ServerPlayer player, boolean forced) {
        Sensed sensed = findBest(player);
        if (sensed == null) return;
        UUID previous = LAST_SIGNAL.get(player.getUUID());
        if (!forced && previous != null && previous.equals(sensed.fighter.getUUID()) && player.tickCount % 360 != 0) return;
        LAST_SIGNAL.put(player.getUUID(), sensed.fighter.getUUID());
        player.displayClientMessage(Component.literal("POWER SENSE • ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(describe(player, sensed)).withStyle(ChatFormatting.WHITE)), true);
    }

    private static Sensed findBest(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        double anchor = WorldPowerScaler.resolveWorldAnchor(level, player.blockPosition());
        List<AmbientFighterEntity> fighters = level.getEntitiesOfClass(
                AmbientFighterEntity.class,
                player.getBoundingBox().inflate(RANGE, 256.0D, RANGE),
                f -> f.isAlive() && !f.isDefeated() && f.distanceToSqr(player) > 48.0D * 48.0D);

        return fighters.stream()
                .map(f -> {
                    double ratio = f.getBattlePower() / Math.max(1.0D, anchor);
                    boolean fighting = f.getTarget() != null && f.getTarget().isAlive();
                    double score = ratio + (fighting ? 0.95D : 0.0D) + (f.isRememberedFor(player) ? 0.35D : 0.0D);
                    return new Sensed(f, ratio, fighting, score);
                })
                .filter(s -> s.ratio >= 1.55D || (s.fighting && s.ratio >= 0.55D))
                .max(Comparator.comparingDouble(s -> s.score))
                .orElse(null);
    }

    private static String describe(ServerPlayer player, Sensed sensed) {
        AmbientFighterEntity fighter = sensed.fighter;
        int distance = Mth.floor(Math.sqrt(fighter.distanceToSqr(player)));
        if (WorldMenaceManager.isHerobrine(fighter))
            return "an unreadable presence • " + compass(player, fighter.getX(), fighter.getZ()) + " • " + distance + " blocks";
        String magnitude;
        if (fighter.isRememberedFor(player)) magnitude = "a familiar power";
        else if (sensed.ratio >= 2.65D) magnitude = "an overwhelming power";
        else if (sensed.ratio >= 1.80D) magnitude = "a massive power";
        else if (sensed.fighting) magnitude = "powers clashing";
        else magnitude = "a strong power";
        return magnitude + " • " + compass(player, fighter.getX(), fighter.getZ()) + " • " + distance + " blocks";
    }

    private static String compass(ServerPlayer player, double x, double z) {
        double dx = x - player.getX();
        double dz = z - player.getZ();
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        if (yaw < 0.0D) yaw += 360.0D;
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        return names[Math.floorMod((int)Math.round(yaw / 45.0D), 8)];
    }

    private record Sensed(AmbientFighterEntity fighter, double ratio, boolean fighting, double score) {}
}
