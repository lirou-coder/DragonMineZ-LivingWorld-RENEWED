package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Non-lethal arbitration for deliberate player/NPC spars.
 * The normal path intercepts finishing damage before vanilla/DMZ can enter a death state;
 * LivingDeathEvent exists only as a last-resort repair for unusual damage implementations.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SanctionedMatchGuard {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int POST_SPAR_PEACE_TICKS = 200;
    // Diagnostic only. Protection ends at 10s; the watch keeps observing the same pair afterward
    // so an intermittent late re-aggro is visible instead of disappearing when PEACE_END logs.
    private static final int POST_PEACE_WATCH_TICKS = 600;
    private static final Map<UUID, PeaceSession> PEACE = new HashMap<>();
    private static final Map<UUID, Long> POST_SPAR_INVULNERABLE = new HashMap<>();
    private static final Map<UUID, WatchSession> WATCH = new HashMap<>();
    private static final Set<UUID> TRACE_PLAYERS = new HashSet<>();
    private record PeaceSession(UUID playerId, long startedAt, long expiresAt) {}
    private record WatchSession(UUID playerId, long expiresAt) {}

    private SanctionedMatchGuard() {}

    public static void beginPostSparPeace(AmbientFighterEntity fighter, ServerPlayer player) {
        if (fighter == null || player == null || fighter.level().isClientSide) return;
        long now = player.getServer().overworld().getGameTime();
        fighter.beginPostSparPeace(player, POST_SPAR_PEACE_TICKS);
        POST_SPAR_INVULNERABLE.put(fighter.getUUID(), now + POST_SPAR_PEACE_TICKS);
        POST_SPAR_INVULNERABLE.put(player.getUUID(), now + POST_SPAR_PEACE_TICKS);
        WATCH.remove(fighter.getUUID());
        PEACE.put(fighter.getUUID(), new PeaceSession(player.getUUID(), now, now + POST_SPAR_PEACE_TICKS));
        trace(player, fighter, "PEACE_START", now);
    }

    public static boolean toggleTrace(ServerPlayer player) {
        if (player == null) return false;
        UUID id = player.getUUID();
        if (!TRACE_PLAYERS.add(id)) { TRACE_PLAYERS.remove(id); return false; }
        LOGGER.info("[LW SparTrace] ENABLED player={} uuid={}", player.getGameProfile().getName(), id);
        return true;
    }

    public static boolean isTraceEnabled(ServerPlayer player) {
        return player != null && TRACE_PLAYERS.contains(player.getUUID());
    }

    public static void noteSparStart(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null) return;
        // HOTFIX4 diagnostic behavior: every sanctioned spar traces automatically.
        // This avoids losing the reproduction because the tester forgot the toggle command.
        boolean newlyEnabled = TRACE_PLAYERS.add(player.getUUID());
        long now = player.getServer().overworld().getGameTime();
        if (newlyEnabled) {
            LOGGER.info("[LW SparTrace] AUTO_ENABLED player={} uuid={}",
                    player.getGameProfile().getName(), player.getUUID());
        }
        trace(player, fighter, "SPAR_START", now);
    }

    public static void noteSparTick(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !isTraceEnabled(player)) return;
        trace(player, fighter, "SPAR_TICK", player.getServer().overworld().getGameTime());
    }

    public static void noteSparTargetRepair(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !isTraceEnabled(player)) return;
        trace(player, fighter, "SPAR_TARGET_REPAIRED", player.getServer().overworld().getGameTime());
    }

    public static void notePostSparCleanup(AmbientFighterEntity fighter, String reason) {
        if (fighter == null) return;
        PeaceSession session = PEACE.get(fighter.getUUID());
        if (session == null) return;
        ServerPlayer player = fighter.getServer() == null ? null : fighter.getServer().getPlayerList().getPlayer(session.playerId());
        if (player != null) trace(player, fighter, reason, player.getServer().overworld().getGameTime());
    }

    public static void clearPostSparPeace(AmbientFighterEntity fighter) {
        if (fighter != null) {
            PEACE.remove(fighter.getUUID());
            WATCH.remove(fighter.getUUID());
        }
    }

    public static boolean isPostSparInvulnerable(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide || !entity.isAlive()) return false;
        Long until = POST_SPAR_INVULNERABLE.get(entity.getUUID());
        if (until == null) return false;
        if (until > entity.level().getGameTime()) return true;
        POST_SPAR_INVULNERABLE.remove(entity.getUUID());
        return false;
    }

    public static void clearRuntime(UUID playerId) {
        if (playerId == null) return;
        TRACE_PLAYERS.remove(playerId);
        PEACE.entrySet().removeIf(e -> e.getValue().playerId().equals(playerId));
        WATCH.entrySet().removeIf(e -> e.getValue().playerId().equals(playerId));
    }

    public static void clearRuntime() { PEACE.clear(); WATCH.clear(); TRACE_PLAYERS.clear(); POST_SPAR_INVULNERABLE.clear(); }

    public static String state(ServerPlayer player) {
        if (player == null) return "no player";
        AmbientFighterEntity nearest = player.serverLevel().getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(48.0D), f -> f.isAlive() && !com.dmzlwfusion.NpcFusionManager.isHiddenFusionPartner(f)).stream()
                .min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (nearest == null) return "no Living World fighter within 48 blocks";
        return snapshot(player, nearest, player.getServer().overworld().getGameTime());
    }

    private static void trace(ServerPlayer player, AmbientFighterEntity fighter, String event, long now) {
        if (!isTraceEnabled(player)) return;
        LOGGER.info("[LW SparTrace] {} {}", event, snapshot(player, fighter, now));
    }

    private static String snapshot(ServerPlayer player, AmbientFighterEntity fighter, long now) {
        if (fighter == null) return "fighter=null";
        LivingEntity target = fighter.getTarget();
        LivingEntity hurtBy = fighter.getLastHurtByMob();
        LivingEntity hurt = fighter.getLastHurtMob();
        var velocity = fighter.getDeltaMovement();
        return "t=" + now + " player=" + player.getGameProfile().getName()
                + " fighter=" + fighter.getFighterName() + "(" + fighter.getUUID() + ")"
                + " target=" + entityLabel(target)
                + " lastHurtBy=" + entityLabel(hurtBy)
                + " lastHurt=" + entityLabel(hurt)
                + " sanctioned=" + fighter.isSanctionedMatchParticipant()
                + " friendlyFist=" + friendlyFistEnabled(player)
                + " peaceTicks=" + fighter.getPostSparPeaceTicks()
                + " graceTicks=" + fighter.getPostSparIncomingGraceTicks()
                + " navDone=" + fighter.getNavigation().isDone()
                + " attacking=" + fighter.isAttacking()
                + " aggressive=" + fighter.isAggressive()
                + " comboing=" + fighter.isComboing()
                + " comboId=" + fighter.getComboId()
                + " casting=" + fighter.isCasting()
                + " zanzoken=" + fighter.isZanzoken()
                + " evading=" + fighter.isEvading()
                + " charging=" + fighter.isCharge()
                + " flying=" + fighter.isFlying()
                + " locomotion=" + fighter.getLocomotionMode()
                + " vel=" + String.format(java.util.Locale.ROOT, "(%.3f,%.3f,%.3f)",
                        velocity.x, velocity.y, velocity.z)
                + " dist=" + String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(player.distanceToSqr(fighter)));
    }


    private static boolean friendlyFistEnabled(ServerPlayer player) {
        if (player == null) return false;
        return StatsProvider.get(StatsCapability.INSTANCE, player)
                .map(data -> data.getStatus().isFriendlyFistEnabled())
                .orElse(false);
    }

    private static String entityLabel(Entity entity) {
        if (entity == null) return "null";
        return entity.getType().toString() + "(" + entity.getUUID() + ")";
    }

    private static LivingEntity responsibleAttacker(net.minecraft.world.damagesource.DamageSource source) {
        if (source == null) return null;
        Entity causing = source.getEntity();
        if (causing instanceof LivingEntity living) return living;
        if (causing instanceof Projectile projectile && projectile.getOwner() instanceof LivingEntity owner) return owner;
        Entity direct = source.getDirectEntity();
        if (direct instanceof AbstractKiProjectile ki && ki.getOwner() instanceof LivingEntity owner) return owner;
        if (direct instanceof LivingEntity living) return living;
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof LivingEntity owner) return owner;
        return null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getAmount() <= 0.0F) return;
        Entity attacker = event.getSource().getEntity();
        LivingEntity responsible = responsibleAttacker(event.getSource());

        Long protectedUntil = POST_SPAR_INVULNERABLE.get(event.getEntity().getUUID());
        if (protectedUntil != null) {
            long now = event.getEntity().level().getGameTime();
            if (event.getEntity().isAlive() && protectedUntil > now) {
                event.setCanceled(true);
                return;
            }
            POST_SPAR_INVULNERABLE.remove(event.getEntity().getUUID());
        }

        if (event.getEntity() instanceof AmbientFighterEntity peaceFighter && peaceFighter.isPostSparIncomingGrace(responsible)) {
            event.setCanceled(true);
            notePostSparCleanup(peaceFighter, "FORMER_OPPONENT_GRACE_DAMAGE_CANCELED");
            return;
        }
        if (event.getEntity() instanceof ServerPlayer peacePlayer && responsible instanceof AmbientFighterEntity peaceAttacker
                && peaceAttacker.isPostSparOpponent(peacePlayer)) {
            event.setCanceled(true);
            trace(peacePlayer, peaceAttacker, "SUPPRESSED_POST_SPAR_DAMAGE", peacePlayer.getServer().overworld().getGameTime());
            return;
        }

        if (event.getEntity() instanceof AmbientFighterEntity fighter && fighter.isSanctionedMatchParticipant()) {
            // Non-finishing third-party/stray damage is never allowed into a deliberate spar.
            if (!fighter.isSanctionedOpponent(attacker)) event.setCanceled(true);
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            boolean spar = SparManager.isPlayerInSpar(player);
            if (!spar) return;
            if (!SparManager.isSanctionedPlayerOpponent(player, responsible)) event.setCanceled(true);
        }
    }

    /** Receives the exact damage after actuallyHurt has completed all mitigation. */
    public static boolean handleFinalSparDamage(LivingEntity target, DamageSource source, float finalDamage) {
        if (target == null || target.level().isClientSide || finalDamage <= 0.0F) return false;
        float safeDamage = Float.isFinite(finalDamage) ? Math.max(0.0F, finalDamage) : Float.POSITIVE_INFINITY;
        LivingEntity responsible = responsibleAttacker(source);
        if (target instanceof AmbientFighterEntity fighter && fighter.isSanctionedMatchParticipant()) {
            if (!(responsible instanceof ServerPlayer player) || !fighter.isSanctionedOpponent(player)) return false;
            float floor = Math.max(1.0F, fighter.getMaxHealth() * 0.30F);
            if (fighter.getHealth() - safeDamage <= floor) {
                fighter.setHealth(floor);
                fighter.restoreSanctionedLivingState(false);
                SparManager.finishFromFinalDamage(player, fighter, true);
                return true;
            }
            return false;
        }
        if (target instanceof ServerPlayer player && SparManager.isPlayerInSpar(player)
                && responsible instanceof AmbientFighterEntity fighter
                && SparManager.isSanctionedPlayerOpponent(player, fighter)) {
            float floor = Math.max(1.0F, player.getMaxHealth() * 0.30F);
            if (player.getHealth() - safeDamage <= floor) {
                player.setHealth(floor);
                player.setPose(Pose.STANDING);
                SparManager.finishFromFinalDamage(player, fighter, false);
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || (PEACE.isEmpty() && WATCH.isEmpty() && POST_SPAR_INVULNERABLE.isEmpty())) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        POST_SPAR_INVULNERABLE.entrySet().removeIf(entry -> entry.getValue() < now);

        for (var entry : java.util.List.copyOf(PEACE.entrySet())) {
            PeaceSession session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId());
            AmbientFighterEntity fighter = find(server, entry.getKey());
            if (now >= session.expiresAt() || fighter == null || fighter.getPostSparPeaceTicks() <= 0) {
                if (player != null && fighter != null) {
                    trace(player, fighter, "PEACE_END", now);
                    WATCH.put(entry.getKey(), new WatchSession(player.getUUID(), now + POST_PEACE_WATCH_TICKS));
                    trace(player, fighter, "POST_PEACE_WATCH_START", now);
                }
                PEACE.remove(entry.getKey());
                continue;
            }
            if (player != null && isTraceEnabled(player) && now % 20L == 0L) trace(player, fighter, "PEACE_TICK", now);
        }

        // Watch-only: no target suppression, no navigation changes, no damage cancellation. This
        // exists solely to capture the intermittent state transition reported after PEACE_END.
        for (var entry : java.util.List.copyOf(WATCH.entrySet())) {
            WatchSession session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId());
            AmbientFighterEntity fighter = find(server, entry.getKey());
            if (now >= session.expiresAt() || player == null || fighter == null) {
                if (player != null && fighter != null) trace(player, fighter, "POST_PEACE_WATCH_END", now);
                WATCH.remove(entry.getKey());
                continue;
            }
            if (isTraceEnabled(player) && now % 20L == 0L) {
                String eventName = fighter.getTarget() == player
                        || fighter.getLastHurtByMob() == player
                        || fighter.getLastHurtMob() == player
                        ? "POST_PEACE_REAGGRO_STATE"
                        : "POST_PEACE_TICK";
                trace(player, fighter, eventName, now);
            }
        }
    }

    private static AmbientFighterEntity find(MinecraftServer server, UUID id) {
        if (server == null || id == null) return null;
        for (var level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof AmbientFighterEntity fighter) return fighter;
        }
        return null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            POST_SPAR_INVULNERABLE.remove(event.getEntity().getUUID());
        }
        Entity attacker = event.getSource().getEntity();
        if (event.getEntity() instanceof AmbientFighterEntity fighter && fighter.isSanctionedMatchParticipant()) {
            event.setCanceled(true);
            float floor = Math.max(1.0F, fighter.getMaxHealth() * 0.30F);
            fighter.setHealth(floor);
            fighter.restoreSanctionedLivingState(false);
            if (fighter.hasSanctionedOpponent()) fighter.concedeSanctionedMatch();
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            boolean spar = SparManager.isPlayerInSpar(player);
            if (!spar) return;
            event.setCanceled(true);
            player.setHealth(Math.max(1.0F, player.getMaxHealth() * 0.30F));
            player.setPose(Pose.STANDING);
            SparManager.concedePlayer(player);
        }
    }
}
