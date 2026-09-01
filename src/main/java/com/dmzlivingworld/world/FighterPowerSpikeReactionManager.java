package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Physical/social awareness of nearby Ki charging and transformations, for players and LW fighters. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterPowerSpikeReactionManager {
    private static final Map<UUID, Double> LAST_PLAYER_BP = new HashMap<>();
    private static final Map<UUID, Boolean> LAST_PLAYER_CHARGE = new HashMap<>();
    private static final Map<UUID, Double> LAST_PLAYER_RELEASE = new HashMap<>();
    private static final Map<UUID, Long> PLAYER_POWER_DOWN_UNTIL = new HashMap<>();
    private static final Map<UUID, Boolean> LAST_NPC_CHARGE = new HashMap<>();
    private static final Map<UUID, Boolean> LAST_NPC_TRANSFORM = new HashMap<>();
    private static final Map<UUID, Long> SOURCE_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> LAST_NPC_SEEN = new HashMap<>();
    private static final long NPC_STALE_TICKS = 1_200L;

    private FighterPowerSpikeReactionManager() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 5L != 0L) return;
        Set<UUID> onlinePlayers = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            onlinePlayers.add(player.getUUID());
            if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) continue;
            tickPlayer(level, player, now);
        }
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) continue;
            for (AmbientFighterEntity fighter : level.getEntitiesOfClass(AmbientFighterEntity.class,
                    player.getBoundingBox().inflate(80.0D), AmbientFighterEntity::isAlive)) {
                if (seen.add(fighter.getUUID())) tickNpc(level, fighter, now);
            }
        }
        // Sources outside any active player's awareness need no edge/cooldown memory. A short
        // grace avoids repeat reactions while a player moves around the same loaded NPC.
        LAST_PLAYER_BP.keySet().retainAll(onlinePlayers);
        LAST_PLAYER_CHARGE.keySet().retainAll(onlinePlayers);
        LAST_PLAYER_RELEASE.keySet().retainAll(onlinePlayers);
        PLAYER_POWER_DOWN_UNTIL.keySet().retainAll(onlinePlayers);
        LAST_NPC_CHARGE.keySet().removeIf(id -> staleNpc(id, now));
        LAST_NPC_TRANSFORM.keySet().removeIf(id -> staleNpc(id, now));
        LAST_NPC_SEEN.entrySet().removeIf(entry -> now - entry.getValue() > NPC_STALE_TICKS);
        SOURCE_COOLDOWN.keySet().removeIf(id -> !onlinePlayers.contains(id) && staleNpc(id, now));
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer player, long now) {
        StatsData stats = player.getCapability(StatsCapability.INSTANCE).orElse(null);
        if (stats == null) return;
        double current = Math.max(1.0D, stats.getBattlePowerExact());
        Double previous = LAST_PLAYER_BP.put(player.getUUID(), current);
        double release = Math.max(0.0D, stats.getResources().getPowerRelease());
        Double previousRelease = LAST_PLAYER_RELEASE.put(player.getUUID(), release);
        boolean charging = stats.getStatus() != null && stats.getStatus().isChargingKi();
        Boolean priorCharge = LAST_PLAYER_CHARGE.put(player.getUUID(), charging);

        // Suppression/power-down is a different event from powering up. Some DMZ input states can
        // briefly overlap the charging flag while Power Release is being lowered, so remember a
        // short downward-transition grace period and categorically refuse to call it a Ki surge.
        if (previousRelease != null && release + 0.25D < previousRelease) {
            PLAYER_POWER_DOWN_UNTIL.put(player.getUUID(), now + 100L);
            return;
        }
        if (now < PLAYER_POWER_DOWN_UNTIL.getOrDefault(player.getUUID(), 0L)) return;
        if (now < SOURCE_COOLDOWN.getOrDefault(player.getUUID(), 0L)) return;

        // A charge is now an episode-level *chance* to be noticed, not a guaranteed social event.
        // Rising-edge gating still means holding charge gets only one opportunity.
        if (charging && priorCharge != null && !priorCharge) {
            if (player.getRandom().nextFloat() < 0.34F) react(level, player, current, false, false, now);
            return;
        }
        if (previous == null) return;
        double ratio = current / Math.max(1.0D, previous);
        if (ratio >= 1.10D && current - previous >= Math.max(75.0D, previous * 0.06D)
                && player.getRandom().nextFloat() < 0.24F) {
            react(level, player, current, false, false, now);
        }
    }

    private static void tickNpc(ServerLevel level, AmbientFighterEntity source, long now) {
        LAST_NPC_SEEN.put(source.getUUID(), now);
        boolean charging = source.isCharge();
        boolean transforming = source.isTransforming();
        boolean wasCharging = LAST_NPC_CHARGE.put(source.getUUID(), charging) == Boolean.TRUE;
        boolean wasTransforming = LAST_NPC_TRANSFORM.put(source.getUUID(), transforming) == Boolean.TRUE;
        if (now < SOURCE_COOLDOWN.getOrDefault(source.getUUID(), 0L)) return;
        if (transforming && !wasTransforming) react(level, source, source.getBattlePower(), true, true, now);
        else if (charging && !wasCharging) react(level, source, source.getBattlePower(), true, false, now);
    }

    private static void react(ServerLevel level, LivingEntity source, double sourcePower, boolean npcSource, boolean transform, long now) {
        react(level, source, sourcePower, npcSource, transform, now, false);
    }

    /** Debug/testing path keeps R7's guarantee that the explicit command really forces observers. */
    private static void react(ServerLevel level, LivingEntity source, double sourcePower, boolean npcSource, boolean transform, long now, boolean forced) {
        int acted = 0;
        for (AmbientFighterEntity observer : level.getEntitiesOfClass(AmbientFighterEntity.class,
                source.getBoundingBox().inflate(34.0D, 18.0D, 34.0D), f -> eligibleObserver(source, f))) {
            double distance = Math.sqrt(observer.distanceToSqr(source));
            float chance;
            if (!npcSource && !transform) {
                // Ordinary player charging should usually pass without commentary. Near observers
                // are a little more likely to notice, but even point-blank reactions remain rare.
                chance = (float)Math.max(0.045D, 0.18D - distance / 260.0D);
                if (observer.getPersonality() == FighterPersonality.CAUTIOUS) chance += 0.035F;
            } else {
                // Preserve the established stronger visibility of actual transformations and NPC
                // spectacle; the reported spam problem was specifically ordinary player charging.
                chance = (float)Math.max(0.24D, 0.78D - distance / 68.0D);
                if (transform) chance += 0.12F;
                if (observer.getPersonality() == FighterPersonality.CAUTIOUS) chance += 0.10F;
            }
            if (!forced && observer.getRandom().nextFloat() > Math.min(0.92F, chance)) continue;

            double ratio = sourcePower / Math.max(1.0D, observer.getBattlePower());
            observer.getLookControl().setLookAt(source, 42.0F, 36.0F);
            String subject = npcSource && source instanceof AmbientFighterEntity f ? f.getFighterName() : "you";
            String possessive = npcSource ? subject + "'s" : "your";
            if (ratio >= 2.0D && (observer.getPersonality() == FighterPersonality.CAUTIOUS
                    || ReactiveWorldManager.temperament(observer) == ReactiveWorldManager.Temperament.ALOOF)) {
                ReactiveWorldManager.reactStrong(observer, ReactiveWorldManager.Mood.WARY,
                        "feeling " + subject + "'s sudden power spike", 1050);
                moveAway(observer, source, level);
                observer.speak(pick(observer,
                        transform ? "That transformation changed the pressure around the whole area." : "That's a serious amount of Ki. I'm giving that some room.",
                        transform ? "I felt " + possessive + " power change instantly. I'm keeping my distance." : "I felt that immediately. I'm not standing right on top of it.",
                        transform ? "Whatever form that is, it just made " + subject + " much harder to ignore." : "That power just jumped hard. Keep some distance.",
                        transform ? "Yeah... I'm not pretending I didn't feel that transformation." : "That Ki is climbing fast."), 82);
            } else if (observer.getPersonality() == FighterPersonality.PROUD || observer.getPersonality() == FighterPersonality.AGGRESSIVE) {
                ReactiveWorldManager.react(observer, ReactiveWorldManager.Mood.FOCUSED,
                        "answering " + subject + "'s power spike", 850);
                if (observer.getRandom().nextFloat() < 0.58F) observer.flareAura(34);
                observer.speak(pick(observer,
                        transform ? "There it is. Now show me what that form can actually do." : "Heh. So you're powering up too.",
                        transform ? possessive.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + possessive.substring(1) + " power just changed. Good." : "I felt that. Don't expect me not to answer it.",
                        "Now that's enough power to get my attention.",
                        transform ? "So that's " + subject + " after transforming... interesting." : "Keep raising it. I want to see where it stops."), 80);
            } else {
                ReactiveWorldManager.react(observer, ratio > 1.35D ? ReactiveWorldManager.Mood.WARY : ReactiveWorldManager.Mood.FOCUSED,
                        "noticing " + subject + "'s sudden power spike", 720);
                observer.speak(pick(observer,
                        transform ? (npcSource ? subject + " feels completely different after that transformation." : "Your energy changed completely.") : "I can feel that Ki rising from here.",
                        transform ? "That form has a completely different pressure." : "That's a noticeable jump in power.",
                        transform ? "The shape isn't the only thing that changed. " + possessive + " Ki did too." : (ratio > 1.5D ? "Okay... that's stronger than I expected." : "I noticed that. You're putting out more power now."),
                        transform ? (ratio > 1.5D ? "That's a serious transformation. The difference is obvious." : "Interesting. That transformation changed the feel of the Ki more than the amount.") : "That's enough of a rise to notice."), 80);
            }
            ReactiveWorldManager.rememberEvent(observer, transform ? "POWER_TRANSFORM" : "POWER_CHARGE", subject,
                    transform ? "reacted to a nearby transformation" : "reacted to a nearby Ki surge");
            FighterMemoryManager.refreshLoadedProfile(observer);
            acted++;
            if (acted >= 3) break;
        }
        if (acted > 0) SOURCE_COOLDOWN.put(source.getUUID(), now + (transform ? 320L : (npcSource ? 220L : 600L)));
    }

    private static boolean eligibleObserver(LivingEntity source, AmbientFighterEntity observer) {
        return observer != null && observer != source && observer.isAlive() && !observer.isCaptive() && !observer.isDefeated()
                && !observer.isMeditating() && !observer.isTransforming() && !observer.isSanctionedMatchParticipant()
                && !WorldMenaceManager.isHerobrine(observer) && observer.getSpeech().isEmpty();
    }

    private static boolean staleNpc(UUID id, long now) {
        Long seen = LAST_NPC_SEEN.get(id);
        return seen == null || now - seen > NPC_STALE_TICKS;
    }

    private static void moveAway(AmbientFighterEntity fighter, LivingEntity source, ServerLevel level) {
        Vec3 away = fighter.position().subtract(source.position());
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 0.01D) away = new Vec3(1.0D, 0.0D, 0.0D);
        BlockPos rough = BlockPos.containing(fighter.position().add(away.normalize().scale(8.0D + fighter.getRandom().nextDouble() * 7.0D)));
        BlockPos safe = AmbientFighterSpawner.findSafeGroundAround(level, rough, fighter.getRandom(), 1, 8, 18);
        if (safe != null) fighter.getNavigation().moveTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D, 1.08D);
    }

    public static int debugPlayer(ServerPlayer player, boolean transform) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        double bp = PlayerWorldManager.playerBattlePower(player);
        SOURCE_COOLDOWN.remove(player.getUUID());
        react(level, player, bp, false, transform, level.getServer().overworld().getGameTime(), true);
        player.displayClientMessage(Component.literal("[Living World] Forced nearby NPC reactions to your " + (transform ? "transformation" : "Ki charge") + "."), false);
        return 1;
    }

    public static int debugNpc(ServerPlayer player, boolean transform) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        AmbientFighterEntity source = level.getEntitiesOfClass(AmbientFighterEntity.class,
                        player.getBoundingBox().inflate(42.0D), f -> f.isAlive() && !com.dmzlwfusion.NpcFusionManager.isHiddenFusionPartner(f) && !WorldMenaceManager.isHerobrine(f))
                .stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (source == null) return 0;
        SOURCE_COOLDOWN.remove(source.getUUID());
        if (transform) source.flareAura(48); else source.setKiCharge(true);
        react(level, source, source.getBattlePower(), true, transform, level.getServer().overworld().getGameTime(), true);
        if (!transform) source.getPersistentData().putLong("LWDebugStopChargeAt", level.getGameTime() + 55L);
        player.displayClientMessage(Component.literal("[Living World] Forced nearby NPC reactions to " + source.getFighterName() + "'s " + (transform ? "transformation-style power spike" : "Ki charge") + "."), false);
        return 1;
    }

    private static String pick(AmbientFighterEntity fighter, String... lines) {
        return lines[fighter.getRandom().nextInt(lines.length)];
    }

    public static void clearRuntime() {
        LAST_PLAYER_BP.clear();
        LAST_PLAYER_CHARGE.clear();
        LAST_PLAYER_RELEASE.clear();
        PLAYER_POWER_DOWN_UNTIL.clear();
        LAST_NPC_CHARGE.clear();
        LAST_NPC_TRANSFORM.clear();
        SOURCE_COOLDOWN.clear();
        LAST_NPC_SEEN.clear();
    }

    public static void clearRuntime(UUID sourceId) {
        if (sourceId == null) return;
        LAST_PLAYER_BP.remove(sourceId);
        LAST_PLAYER_CHARGE.remove(sourceId);
        LAST_PLAYER_RELEASE.remove(sourceId);
        PLAYER_POWER_DOWN_UNTIL.remove(sourceId);
        LAST_NPC_CHARGE.remove(sourceId);
        LAST_NPC_TRANSFORM.remove(sourceId);
        SOURCE_COOLDOWN.remove(sourceId);
        LAST_NPC_SEEN.remove(sourceId);
    }
}
