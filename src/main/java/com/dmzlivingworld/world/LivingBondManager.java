package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.compat.MeditationCompat;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterRank;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.server.events.QuestEvents;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Sparse personal bonds. Nothing here turns a fighter into a tame pet: invites
 * are voluntary, one companion can travel with a player, and the companion keeps
 * self-preservation/faction loyalties while supporting genuine threats.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LivingBondManager {
    private static final String ROOT = "DMZLivingWorldBonds";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Invite> INVITES = new HashMap<>();
    private static final Map<UUID, Map<UUID, MeditationBondSession>> MEDITATION_BONDS = new HashMap<>();
    private static final Map<UUID, AutoMeditationState> AUTO_MEDITATION = new HashMap<>();
    private static final Map<UUID, DefenseThreat> DEFENSE_THREATS = new HashMap<>();
    private static final Map<UUID, Deque<Vec3>> TRAVEL_TRAILS = new HashMap<>();
    private static final int MAX_MEDITATION_PARTNERS = 4;
    private static final int MAX_COMPANIONS = 5;
    private static final long FRIENDLY_FIRE_RESET_TICKS = 2400L;
    private enum InviteType { TRAVEL }
    private record Invite(UUID npc, InviteType type, long expires) {}
    private record DefenseThreat(UUID target, long expires) {}
    private static final class MeditationBondSession {
        final UUID npc;
        final long createdAt;
        long activeSince = -1L;
        boolean firstMilestone;
        boolean secondMilestone;
        MeditationBondSession(UUID npc, long createdAt) { this.npc = npc; this.createdAt = createdAt; }
    }

    private static final class AutoMeditationState {
        long nextFriendlyCheck;
        int spontaneousJoins;
        AutoMeditationState(long startedAt) {
            this.nextFriendlyCheck = startedAt + 80L;
        }
    }

    private LivingBondManager() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) continue;
            long now = level.getServer().overworld().getGameTime();
            tickCompanion(player, level, now);
            tickAdditionalCompanions(player, level, now);
            tickMeditationBonds(player, level, now);
            // Offers and discovery are human-scale decisions; only active companion/meditation
            // state needs per-tick control.
            if (now % 20L == Math.floorMod(player.getUUID().hashCode(), 20)) {
                tickInvite(player, level, now);
                tickAutomaticMeditation(player, level, now);
            }
        }
        long now = event.getServer().overworld().getGameTime();
        if (now % 20L == 0L) {
            INVITES.entrySet().removeIf(e -> e.getValue().expires < now);
            DEFENSE_THREATS.entrySet().removeIf(e -> e.getValue().expires < now);
        }
    }

    /**
     * Damage events are the authoritative companion-defense trigger.
     * Polling Player#getLastHurtByMob remains as a fallback, but some modded attackers and
     * attack pipelines can update/clear that state in ways that make a travelling companion
     * miss the fight. Latch the real attacker briefly so the companion has time to acquire,
     * approach and engage it through DMZ's native combat brain.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = false)
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0.0F) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        LivingEntity attacker = responsibleAttacker(event.getSource());
        if (attacker == null || attacker == player || !attacker.isAlive()) return;
        // A voluntary spar temporarily supersedes every Come Along friendly-fire/ally rule.
        // This must precede the companion-hit cancellation below so the companion can damage
        // its owner through DMZ's normal sanctioned-match pipeline.
        if (SparManager.isSanctionedPlayerOpponent(player, attacker)) return;
        if (attacker instanceof AmbientFighterEntity member && isCompanion(player, member)) {
            if (attacker instanceof AmbientFighterEntity companion && companion.getTarget() != null) {
                event.setCanceled(true);
            }
            return;
        }
        latchDefenseThreat(player, attacker, level.getServer().overworld().getGameTime(), 240L);
    }

    private static LivingEntity responsibleAttacker(net.minecraft.world.damagesource.DamageSource source) {
        if (source == null) return null;
        LivingEntity living = livingOwner(source.getEntity());
        return living != null ? living : livingOwner(source.getDirectEntity());
    }

    private static LivingEntity livingOwner(Entity entity) {
        if (entity instanceof LivingEntity living) return living;
        if (entity instanceof Projectile projectile && projectile.getOwner() instanceof LivingEntity owner) return owner;
        return null;
    }

    public static void clearRuntime(UUID playerId) {
        if (playerId != null) {
            INVITES.remove(playerId);
            MEDITATION_BONDS.remove(playerId);
            AUTO_MEDITATION.remove(playerId);
            DEFENSE_THREATS.remove(playerId);
        }
    }
    public static void clearRuntime() { INVITES.clear(); MEDITATION_BONDS.clear(); AUTO_MEDITATION.clear(); DEFENSE_THREATS.clear(); }
    public static int runtimeEntries() {
        int bonds = MEDITATION_BONDS.values().stream().mapToInt(Map::size).sum();
        return INVITES.size() + bonds + AUTO_MEDITATION.size() + DEFENSE_THREATS.size();
    }

    private static void tickInvite(ServerPlayer player, ServerLevel level, long now) {
        Invite active = INVITES.get(player.getUUID());
        if (active != null) {
            AmbientFighterEntity npc = entity(level, active.npc);
            if (npc == null || !npc.isAlive()) { INVITES.remove(player.getUUID()); return; }
            if (player.distanceToSqr(npc) > 7.0D * 7.0D && player.distanceToSqr(npc) < 36.0D * 36.0D && !npc.isMeditating()) {
                npc.getNavigation().moveTo(player, 1.05D);
            }
            return;
        }

        CompoundTag root = root(player);
        if (now >= root.getLong("NextCompanionOffer") && companionId(player) == null) {
            root.putLong("NextCompanionOffer", now + 72000L + player.getRandom().nextInt(72001));
            AmbientFighterEntity friend = level.getEntitiesOfClass(AmbientFighterEntity.class,
                    player.getBoundingBox().inflate(30.0D), f -> f.isAlive() && !f.isNonCombatant() && f.isRememberedFor(player)
                            && f.getMemoryRelationship() >= 45 && !f.isCaptive() && !f.isDefeated())
                    .stream().findAny().orElse(null);
            if (friend != null) {
                INVITES.put(player.getUUID(), new Invite(friend.getUUID(), InviteType.TRAVEL, now + 800L));
                friend.speak("You're heading out again? Want some company?", 110);
                friend.getNavigation().moveTo(player, 1.05D);
            }
            save(player, root);
        }
    }

    /** Called before normal sneak-inspection. */
    public static boolean tryHandleInteraction(ServerPlayer player, AmbientFighterEntity npc) {
        Invite invite = INVITES.get(player.getUUID());
        if (invite == null || !invite.npc.equals(npc.getUUID())) return false;
        INVITES.remove(player.getUUID());
        if (invite.type == InviteType.TRAVEL) {
            setCompanion(player, npc);
            npc.speak("I'll come with you. But I'm making my own calls.", 86);
            FighterMemoryManager.strengthenRelationship(player, npc, 3, FighterRelationshipManager.BondEvent.TRAVEL, "Travelled together");
            return true;
        }
        return false;
    }

    /** Server-authoritative profile action for intentionally meditating with a known nearby fighter. */
    public static void requestSharedMeditation(ServerPlayer player, AmbientFighterEntity npc) {
        if (player == null || npc == null || !(player.level() instanceof ServerLevel level)) return;
        if (!MeditationCompat.isAvailable()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Living World] Meditation is unavailable right now."), false);
            return;
        }
        if (!MeditationCompat.isNpcMeditationEnabled()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Living World] NPC meditation is disabled in World Settings."), false);
            return;
        }
        if (!npc.isAlive() || npc.isCaptive() || npc.isDefeated() || npc.isTransforming()
                || npc.isKaiokenActive() || npc.getTarget() != null
                || player.distanceToSqr(npc) > 12.0D * 12.0D) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Living World] This fighter cannot meditate with you right now."), false);
            return;
        }
        if (!canShareMeditation(player, npc)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Living World] This fighter is not comfortable meditating together yet."), false);
            return;
        }
        if (MeditationCompat.isPlayerMeditating(player) && npc.isMeditatingWith(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Living World] You are already meditating together."), false);
            return;
        }
        if (activeMeditationPartnerCount(player, level) >= MAX_MEDITATION_PARTNERS) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Living World] Your meditation circle is already full."), false);
            return;
        }
        if (!MeditationCompat.startPlayerMeditation(player)) return;
        if (!npc.beginSharedMeditation(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Living World] The fighter could not settle into meditation."), false);
            return;
        }
        INVITES.remove(player.getUUID());
        trackMeditationBond(player, npc, level.getServer().overworld().getGameTime());
        if (npc.getSpeech().isEmpty()) npc.speak("Let's focus.", 52);
    }

    /**
     * Shared meditation builds friendship through time actually spent together. Multiple nearby
     * fighters may share one player session; every partner keeps an independent, bounded bond timer.
     */
    private static void tickMeditationBonds(ServerPlayer player, ServerLevel level, long now) {
        Map<UUID, MeditationBondSession> sessions = MEDITATION_BONDS.get(player.getUUID());
        if (sessions == null || sessions.isEmpty()) return;
        if (!MeditationCompat.isPlayerMeditating(player)) {
            MEDITATION_BONDS.remove(player.getUUID());
            return;
        }

        for (MeditationBondSession session : new ArrayList<>(sessions.values())) {
            AmbientFighterEntity npc = entity(level, session.npc);
            if (npc == null || !npc.isAlive()) {
                sessions.remove(session.npc);
                continue;
            }
            if (!npc.isMeditatingWith(player)) {
                if (player.distanceToSqr(npc) > 16.0D * 16.0D || now - session.createdAt > 240L)
                    sessions.remove(session.npc);
                continue;
            }
            if (session.activeSince < 0L) session.activeSince = now;
            long elapsed = now - session.activeSince;
            if (!session.firstMilestone && elapsed >= 20L * 20L) {
                session.firstMilestone = true;
                FighterMemoryManager.strengthenRelationship(player, npc, 1,
                        FighterRelationshipManager.BondEvent.MEDITATION, "Meditated together");
                if (npc.getSpeech().isEmpty() && npc.getRandom().nextFloat() < 0.35F) npc.speak("Your focus is steady.", 54);
            }
            if (!session.secondMilestone && elapsed >= 20L * 90L) {
                session.secondMilestone = true;
                FighterMemoryManager.strengthenRelationship(player, npc, 1,
                        FighterRelationshipManager.BondEvent.MEDITATION, "Shared a long meditation");
                if (npc.getSpeech().isEmpty() && npc.getRandom().nextFloat() < 0.55F) npc.speak("That was good. I feel clearer.", 64);
            }
        }
        if (sessions.isEmpty()) MEDITATION_BONDS.remove(player.getUUID());
    }

    private static void trackMeditationBond(ServerPlayer player, AmbientFighterEntity npc, long now) {
        if (player == null || npc == null) return;
        Map<UUID, MeditationBondSession> sessions = MEDITATION_BONDS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        if (!sessions.containsKey(npc.getUUID()) && sessions.size() >= MAX_MEDITATION_PARTNERS) return;
        sessions.computeIfAbsent(npc.getUUID(), ignored -> new MeditationBondSession(npc.getUUID(), now));
    }

    private static int activeMeditationPartnerCount(ServerPlayer player, ServerLevel level) {
        if (player == null || level == null) return 0;
        return level.getEntitiesOfClass(AmbientFighterEntity.class, player.getBoundingBox().inflate(14.0D),
                fighter -> fighter.isAlive() && fighter.isMeditatingWith(player)).size();
    }

    public static int meditationPartnerCount(ServerPlayer player) {
        return player != null && player.level() instanceof ServerLevel level
                ? activeMeditationPartnerCount(player, level) : 0;
    }

    /** GUI action for deliberately growing the player's current meditation circle one fighter at a time. */
    public static boolean inviteNearestMeditationFriend(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level) || !MeditationCompat.isAvailable()) return false;
        if (!MeditationCompat.isNpcMeditationEnabled()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Living World] NPC meditation is disabled in World Settings."), false);
            return false;
        }
        if (!MeditationCompat.isPlayerMeditating(player) && !MeditationCompat.startPlayerMeditation(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Living World] Start meditation first, then invite someone into the circle."), false);
            return false;
        }
        if (activeMeditationPartnerCount(player, level) >= MAX_MEDITATION_PARTNERS) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Living World] Your meditation circle is already full."), false);
            return false;
        }
        UUID companion = companionId(player);
        List<AmbientFighterEntity> candidates = level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(12.0D), fighter -> fighter.isAlive() && !fighter.isMeditatingWith(player)
                        && canAutoJoinMeditation(player, fighter)
                        && ((companion != null && companion.equals(fighter.getUUID()))
                        || isFriendlyMeditationCandidate(player, fighter)));
        if (candidates.isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Living World] No nearby friend is free to join you right now."), false);
            return false;
        }
        candidates.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
        AmbientFighterEntity npc = candidates.get(0);
        if (!npc.beginSharedMeditation(player)) return false;
        long now = level.getServer().overworld().getGameTime();
        trackMeditationBond(player, npc, now);
        if (npc.getSpeech().isEmpty()) npc.speak("I'll join you.", 58);
        return true;
    }

    /**
     * Makes shared meditation a natural social scene. A travelling companion joins as soon as it
     * can reach the player; one other close friendly fighter may occasionally settle in as well.
     * This never restarts the player's Meditation session and never multiplies Meditation rewards.
     */
    private static void tickAutomaticMeditation(ServerPlayer player, ServerLevel level, long now) {
        if (!MeditationCompat.isNpcMeditationEnabled()) {
            AUTO_MEDITATION.remove(player.getUUID());
            return;
        }
        if (!MeditationCompat.isPlayerMeditating(player)) {
            AUTO_MEDITATION.remove(player.getUUID());
            return;
        }
        AutoMeditationState state = AUTO_MEDITATION.computeIfAbsent(player.getUUID(), ignored -> new AutoMeditationState(now));
        int activePartners = activeMeditationPartnerCount(player, level);
        if (activePartners >= MAX_MEDITATION_PARTNERS) return;

        UUID companionId = companionId(player);
        AmbientFighterEntity companion = findLoadedCompanion(player, companionId);
        if (companion != null && companion.level() == player.level() && companion.isAlive()
                && player.distanceToSqr(companion) <= 32.0D * 32.0D && !companion.isMeditatingWith(player)
                && canAutoJoinMeditation(player, companion)) {
            if (companion.beginSharedMeditation(player)) {
                trackMeditationBond(player, companion, now);
                activePartners++;
                if (companion.getSpeech().isEmpty()) companion.speak("I'll join you.", 58);
            }
        }

        if (activePartners >= MAX_MEDITATION_PARTNERS || state.spontaneousJoins >= 1 || now < state.nextFriendlyCheck) return;
        state.nextFriendlyCheck = now + 360L + player.getRandom().nextInt(601);

        List<AmbientFighterEntity> candidates = level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(11.0D), fighter -> fighter.isAlive() && !fighter.isMeditatingWith(player)
                        && (companionId == null || !companionId.equals(fighter.getUUID()))
                        && canAutoJoinMeditation(player, fighter)
                        && isFriendlyMeditationCandidate(player, fighter));
        if (candidates.isEmpty()) return;
        candidates.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
        AmbientFighterEntity friend = candidates.get(0);
        int relationship = FighterRelationshipManager.relationshipOrUnknown(player, friend);
        float chance = relationship >= 85 ? 0.52F : relationship >= 60 ? 0.38F : relationship >= 35 ? 0.24F : 0.10F;
        if (player.getRandom().nextFloat() > chance) return;
        if (friend.beginSharedMeditation(player)) {
            state.spontaneousJoins++;
            trackMeditationBond(player, friend, now);
            if (friend.getSpeech().isEmpty()) friend.speak("Mind if I join you?", 62);
        }
    }

    private static boolean canAutoJoinMeditation(ServerPlayer player, AmbientFighterEntity npc) {
        if (player == null || npc == null || !npc.isAlive() || npc.isCaptive() || npc.isDefeated()
                || npc.isTransforming() || npc.isKaiokenActive() || npc.getTarget() != null
                || npc.isSanctionedMatchParticipant()) return false;
        return canShareMeditation(player, npc);
    }

    private static boolean isFriendlyMeditationCandidate(ServerPlayer player, AmbientFighterEntity npc) {
        int relationship = FighterRelationshipManager.relationshipOrUnknown(player, npc);
        if (relationship >= 35 && relationship <= 100) return true;
        return npc.isFactionMember()
                && FactionManager.getReputation(player, npc.getFactionId()) >= FactionManager.FRIENDLY_REP;
    }

    private static boolean canShareMeditation(ServerPlayer player, AmbientFighterEntity npc) {
        UUID companion = companionId(player);
        if (companion != null && companion.equals(npc.getUUID())) return true;
        if (npc.isRememberedFor(player) && npc.getMemoryRelationship() >= 0) return true;
        return npc.isFactionMember()
                && FactionManager.getReputation(player, npc.getFactionId()) >= FactionManager.FRIENDLY_REP;
    }

    private static void tickCompanion(ServerPlayer player, ServerLevel level, long now) {
        UUID id = companionId(player);
        if (id == null) return;
        CompoundTag root = root(player);
        AmbientFighterEntity companion = findLoadedCompanion(player, id);

        if (companion == null) {
            long missingSince = root.getLong("CompanionMissingSince");
            if (missingSince <= 0L) {
                root.putLong("CompanionMissingSince", now);
                save(player, root);
                return;
            }
            // A travelling companion is allowed to cross unloaded terrain, but should not be
            // permanently lost because Minecraft stopped ticking its chunk. After a short grace
            // period, deliberately recover the same saved fighter and regroup near the player.
            if (now - missingSince >= 100L) recoverCompanion(player, true);
            return;
        }
        boolean hadMissingFlag = root.contains("CompanionMissingSince");
        root.remove("CompanionMissingSince");
        // Keep a durable travel snapshot without rewriting the player's full persistent NBT every tick.
        if (now % 20L == Math.floorMod(companion.getUUID().hashCode(), 20)) {
            rememberCompanionState(player, root, companion);
        } else if (hadMissingFlag) {
            save(player, root);
        }

        if (!companion.isAlive()) { removeCompanion(player, companion); return; }
        if (!companion.level().dimension().equals(player.level().dimension())) {
            if (!companionDimensionBlocked(level)) {
                CompoundTag profile = companion.writeMemoryProfile();
                companion.discard();
                AmbientFighterEntity moved = AmbientFighterSpawner.spawnCompanionByPlayer(player, profile);
                if (moved != null) {
                    List<UUID> ids = companionIds(player);
                    if (!ids.isEmpty()) ids.set(0, moved.getUUID());
                    writeCompanionIds(root, ids);
                    moved.getPersistentData().putUUID("LWCompanionOwner", player.getUUID());
                    rememberCompanionState(player, root, moved);
                }
            }
            return;
        }

        long joined = root.getLong("CompanionJoined");
        if (joined > 0 && now - joined > 96000L) {
            companion.speak("I should head back for a while. We'll meet again.", 82);
            removeCompanion(player, companion);
            return;
        }
        if (companion.isFactionMember()) {
            WorldFaction faction = FactionManager.byId(level, companion.getFactionId());
            if (faction != null && !FactionWorldData.get(level).warEnemies(faction, now).isEmpty()
                    && now - joined > 24000L && Math.floorMod(now + companion.getUUID().hashCode(), 24000L) == 0L
                    && companion.getRandom().nextFloat() < 0.35F) {
                companion.speak("My people are at war. I need to go back.", 82);
                removeCompanion(player, companion);
                return;
            }
        }

        if (companion.isSanctionedMatchParticipant() && companion.isSanctionedOpponent(player)) {
            DEFENSE_THREATS.remove(player.getUUID());
            return;
        }

        double companionDistance = player.distanceToSqr(companion);
        recordTravelTrail(player);
        // Navigation cannot bridge unloaded chunks reliably. A companion who falls far enough
        // behind gets a clean catch-up instead of becoming a stale "travelling with you" flag.
        double regroupDistance = companion.hasLineOfSight(player) ? 192.0D : 40.0D;
        if (companionDistance > regroupDistance * regroupDistance) {
            recoverCompanion(player, false);
            return;
        }

        // Travelling owns the companion's idle locomotion. Do not let an ambient hobby or an
        // unrelated social scene quietly steal them away from the player mid-trip.
        FighterAmbientActivityManager.cancel(companion);
        companion.setSocialLifeActivity(false);

        if (companion.getHealth() < companion.getMaxHealth() * 0.18F) {
            companion.setTarget(null);
            followPlayer(player, companion, companionDistance, true);
            maybeCompanionChatter(player, companion, root, now);
            return;
        }

        LivingEntity threat = latchedDefenseThreat(player, level, now);
        if (!validCompanionThreat(player, companion, threat, 96.0D)) threat = player.getLastHurtByMob();
        if (!validCompanionThreat(player, companion, threat, 96.0D)) {
            LivingEntity attacked = player.getLastHurtMob();
            threat = validCompanionThreat(player, companion, attacked, 64.0D) ? attacked : null;
        }
        if (threat != null && !loyaltyConflict(companion, threat) && sagaHelpAllowed(threat)) {
            if (companion.isMeditating() || companion.isPreparingMeditation()) companion.stopMeditation(false);
            FighterAmbientActivityManager.cancel(companion);
            companion.setSocialLifeActivity(false);
            if (companion.getTarget() != threat) {
                companion.getNavigation().stop();
                companion.setTarget(threat);
            }
            boolean followTargetInFlight = companion.hasFlightUnlocked() && isActuallyFlying(threat);
            companion.setCanFly(followTargetInFlight);
            if (!followTargetInFlight) {
                companion.setFlying(false);
                companion.setFlyingFast(false);
                companion.setNoGravity(false);
            }
        } else if (companion.getTarget() == null || !sagaHelpAllowed(companion.getTarget())) {
            if (companion.getTarget() != null && !sagaHelpAllowed(companion.getTarget())) companion.setTarget(null);
            followPlayer(player, companion, companionDistance, false);
        } else {
            // The original trigger may have expired while native combat still owns a valid target.
            // Keep the same grounded/airborne rule for the rest of that engagement.
            LivingEntity currentTarget = companion.getTarget();
            boolean followTargetInFlight = companion.hasFlightUnlocked() && isActuallyFlying(currentTarget);
            companion.setCanFly(followTargetInFlight);
            if (!followTargetInFlight) {
                companion.setFlying(false);
                companion.setFlyingFast(false);
                companion.setNoGravity(false);
            }
        }
        maybeCompanionChatter(player, companion, root, now);
    }

    private static void tickAdditionalCompanions(ServerPlayer player, ServerLevel level, long now) {
        List<UUID> ids = companionIds(player);
        if (ids.size() <= 1) return;
        for (int index = 1; index < ids.size(); index++) {
            AmbientFighterEntity companion = findLoadedCompanion(player, ids.get(index));
            if (companion == null || !companion.isAlive()) continue;
            if (!companion.level().dimension().equals(level.dimension())) {
                if (companionDimensionBlocked(level)) continue;
                CompoundTag profile = companion.writeMemoryProfile();
                companion.discard();
                AmbientFighterEntity moved = AmbientFighterSpawner.spawnCompanionByPlayer(player, profile);
                if (moved == null) continue;
                moved.getPersistentData().putUUID("LWCompanionOwner", player.getUUID());
                ids.set(index, moved.getUUID());
                writeCompanionIds(root(player), ids);
                companion = moved;
            }
            FighterAmbientActivityManager.cancel(companion);
            companion.setSocialLifeActivity(false);
            LivingEntity threat = latchedDefenseThreat(player, level, now);
            if (!validCompanionThreat(player, companion, threat, 96.0D)) threat = player.getLastHurtByMob();
            if (validCompanionThreat(player, companion, threat, 96.0D)
                    && !loyaltyConflict(companion, threat) && sagaHelpAllowed(threat)) {
                if (companion.getTarget() != threat) companion.setTarget(threat);
            } else if (companion.getTarget() == null) {
                followPlayer(player, companion, player.distanceToSqr(companion), false);
            }
        }
    }

    private static boolean companionDimensionBlocked(ServerLevel level) {
        return level != null && LivingWorldConfig.companionDimensionBlacklist().contains(
                level.dimension().location().toString().toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean validCompanionThreat(ServerPlayer player, AmbientFighterEntity companion, LivingEntity threat, double maxDistance) {
        if (player == null || companion == null || threat == null || !threat.isAlive()) return false;
        if (threat == player || threat == companion) return false;
        if (!threat.level().dimension().equals(player.level().dimension())) return false;
        if (player.distanceToSqr(threat) > maxDistance * maxDistance) return false;
        return !SparManager.isSanctionedPlayerOpponent(player, threat);
    }

    private static void followPlayer(ServerPlayer player, AmbientFighterEntity companion, double distanceSq, boolean cautious) {
        if (player == null || companion == null || companion.isMeditating() || companion.isPreparingMeditation()) return;
        CompoundTag travelData = companion.getPersistentData();
        double vertical = player.getY() - companion.getY();
        boolean playerAirborne = isActuallyFlying(player);

        // If the owner gets five blocks above an earthbound companion, capture that exact point.
        // The rescue flight deliberately does not chase the owner's later positions: it reaches
        // the captured ledge first, then returns to ordinary pet-style following.
        boolean rescueFlight = travelData.getBoolean("LWCompanionRescueFlight");
        if (!rescueFlight && vertical >= 5.0D && companion.hasFlightUnlocked() && !companion.isNonCombatant()) {
            rescueFlight = true;
            travelData.putBoolean("LWCompanionRescueFlight", true);
            travelData.putDouble("LWCompanionRescueX", player.getX());
            travelData.putDouble("LWCompanionRescueY", player.getY() + 0.5D);
            travelData.putDouble("LWCompanionRescueZ", player.getZ());
        }
        Vec3 flightTarget = rescueFlight
                ? new Vec3(travelData.getDouble("LWCompanionRescueX"), travelData.getDouble("LWCompanionRescueY"),
                        travelData.getDouble("LWCompanionRescueZ"))
                : player.position().add(0.0D, 1.0D, 0.0D);
        double flightDistanceSq = companion.position().distanceToSqr(flightTarget);
        if (rescueFlight && flightDistanceSq <= 2.25D) {
            rescueFlight = false;
            travelData.remove("LWCompanionRescueFlight");
        }

        // Keep an already airborne companion under direct flight control while descending. This
        // mirrors DBSagasEntity instead of handing a flying entity to ground navigation, which was
        // the source of the stationary-in-air and unable-to-descend failures.
        boolean controlledDescent = companion.isFlying() && !companion.onGround() && distanceSq > 7.0D * 7.0D;
        boolean shouldFly = companion.hasFlightUnlocked() && !companion.isNonCombatant()
                && (rescueFlight || playerAirborne || controlledDescent);
        companion.setCanFly(shouldFly);

        if (shouldFly) {
            companion.getNavigation().stop();
            companion.setFlying(true);
            companion.setNoGravity(true);
            companion.setSprinting(false);
            double stopRadiusSq = rescueFlight ? 2.25D : 7.0D * 7.0D;
            if (flightDistanceSq <= stopRadiusSq) {
                companion.setFlyingFast(false);
                companion.setDeltaMovement(companion.getDeltaMovement().scale(0.25D));
                companion.getLookControl().setLookAt(player, 28.0F, 24.0F);
                return;
            }

            // Exact DBSagasEntity flight rule: normalized three-dimensional movement, native fly
            // speed, fast-flight beyond 15 blocks, and a small downward drag that permits descent.
            double distance = Math.sqrt(flightDistanceSq);
            companion.setFlyingFast(distance > 15.0D);
            double speed = Math.max(0.18D, companion.getFlySpeed()) * (companion.isFlyingFast() ? 2.0D : 1.0D);
            Vec3 toward = flightTarget.subtract(companion.position());
            if (toward.lengthSqr() >= 1.0D) {
                double gravityDrag = toward.y < -0.5D ? -0.05D : -0.03D;
                companion.setDeltaMovement(toward.normalize().scale(speed).add(0.0D, gravityDrag, 0.0D));
            }
            companion.getLookControl().setLookAt(flightTarget.x, flightTarget.y, flightTarget.z, 35.0F, 30.0F);
            return;
        }

        if (companion.isFlying() || companion.isNoGravity()) {
            companion.setFlying(false);
            companion.setFlyingFast(false);
            companion.setNoGravity(false);
        }

        // Ground following deliberately follows vanilla FollowOwnerGoal's shape: leave the pet
        // alone in its comfort radius and periodically ask navigation for a fresh path directly to
        // the owner. No synthetic surface point, waypoint or forced jump competes with navigation.
        if (distanceSq <= 6.0D * 6.0D) {
            if (!travelData.getBoolean("LWCompanionComfortZone")) {
                companion.getNavigation().stop();
                travelData.putBoolean("LWCompanionComfortZone", true);
            }
            companion.setSprinting(false);
            companion.setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);
            return;
        }
        travelData.remove("LWCompanionComfortZone");
        boolean far = distanceSq > 20.0D * 20.0D;
        companion.setSprinting(far);
        companion.setLocomotionMode(far ? DBSagasEntity.LocomotionMode.RUN : DBSagasEntity.LocomotionMode.WALK);
        if (companion.getNavigation().isDone() || companion.tickCount % 10 == 0)
            companion.getNavigation().moveTo(player, cautious ? 1.05D : far ? 1.42D : 1.16D);
    }

    private static boolean isActuallyFlying(LivingEntity entity) {
        if (entity instanceof AmbientFighterEntity fighter) return fighter.isFlying();
        if (entity instanceof DBSagasEntity saga) return saga.isFlying();
        if (entity instanceof ServerPlayer player) {
            if (player.getAbilities().flying || player.isFallFlying()) return true;
            return StatsProvider.get(StatsCapability.INSTANCE, player)
                    .map(data -> data.getSkills().isSkillActive("fly"))
                    .orElse(false);
        }
        return false;
    }

    private static boolean travelBlocked(ServerLevel level, BlockPos pos) {
        return level != null && pos != null && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static void recordTravelTrail(ServerPlayer player) {
        Deque<Vec3> trail = TRAVEL_TRAILS.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
        Vec3 point = player.position().add(0.0D, 0.8D, 0.0D);
        Vec3 last = trail.peekLast();
        if (last == null || last.distanceToSqr(point) >= 2.25D) trail.addLast(point);
        while (trail.size() > 36) trail.removeFirst();
    }

    /**
     * Follow the player's recent route through doors, cave bends and gaps. The companion skips
     * breadcrumbs only while the farther point is directly visible, so open terrain remains fast
     * without restoring the old wall-cutting behaviour.
     */
    private static Vec3 flightTrailTarget(ServerLevel level, AmbientFighterEntity companion,
                                          ServerPlayer player, Vec3 directTarget) {
        Deque<Vec3> trail = TRAVEL_TRAILS.get(player.getUUID());
        if (trail == null || trail.isEmpty()) return directTarget;
        while (trail.size() > 1 && companion.position().distanceToSqr(trail.peekFirst()) < 3.0D * 3.0D) trail.removeFirst();
        Vec3 selected = trail.peekFirst();
        Vec3 eye = companion.position().add(0.0D, companion.getBbHeight() * 0.55D, 0.0D);
        for (Vec3 candidate : trail) {
            HitResult hit = level.clip(new ClipContext(eye, candidate, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, companion));
            if (hit.getType() != HitResult.Type.MISS) break;
            selected = candidate;
        }
        if (selected == null || selected.distanceToSqr(directTarget) < 4.0D) return directTarget;
        return selected;
    }

    /** Measures real progress toward the player's destination; oscillating on one block increases stall. */
    private static int travelStallTicks(AmbientFighterEntity companion, Vec3 destination) {
        CompoundTag data = companion.getPersistentData();
        long now = companion.level().getGameTime();
        double lastGoalX = data.getDouble("LWTravelGoalX"), lastGoalY = data.getDouble("LWTravelGoalY"), lastGoalZ = data.getDouble("LWTravelGoalZ");
        double goalShift = new Vec3(lastGoalX, lastGoalY, lastGoalZ).distanceToSqr(destination);
        if (!data.contains("LWTravelProgressAt") || goalShift > 9.0D * 9.0D) {
            data.putDouble("LWTravelGoalX", destination.x); data.putDouble("LWTravelGoalY", destination.y); data.putDouble("LWTravelGoalZ", destination.z);
            data.putDouble("LWTravelBestDistance", companion.position().distanceToSqr(destination));
            data.putLong("LWTravelProgressAt", now); data.putInt("LWTravelStall", 0);
            return 0;
        }
        data.putDouble("LWTravelGoalX", destination.x); data.putDouble("LWTravelGoalY", destination.y); data.putDouble("LWTravelGoalZ", destination.z);
        int stall = data.getInt("LWTravelStall");
        if (now - data.getLong("LWTravelProgressAt") >= 10L) {
            double dist = companion.position().distanceToSqr(destination);
            double best = data.getDouble("LWTravelBestDistance");
            if (dist + 1.0D < best) { data.putDouble("LWTravelBestDistance", dist); stall = Math.max(0, stall - 10); }
            else stall = Math.min(200, stall + 10);
            data.putInt("LWTravelStall", stall); data.putLong("LWTravelProgressAt", now);
        }
        return stall;
    }

    private static void maybeCompanionChatter(ServerPlayer player, AmbientFighterEntity companion, CompoundTag root, long now) {
        if (player == null || companion == null || root == null || MeditationCompat.isPlayerMeditating(player)
                || companion.isMeditating() || companion.isPreparingMeditation()
                || companion.getTarget() != null || !companion.getSpeech().isEmpty()) return;
        long next = root.getLong("NextCompanionChatter");
        if (next <= 0L) {
            root.putLong("NextCompanionChatter", now + 1400L + player.getRandom().nextInt(2201));
            save(player, root);
            return;
        }
        if (now < next) return;
        root.putLong("NextCompanionChatter", now + 2200L + player.getRandom().nextInt(3601));
        save(player, root);
        if (player.getRandom().nextFloat() > 0.72F) return;

        String line;
        LivingEntity threat = player.getLastHurtByMob();
        if (threat != null && threat.isAlive() && player.distanceToSqr(threat) < 48.0D * 48.0D) {
            line = companion.getPersonality() == com.dmzlivingworld.entity.FighterPersonality.CAUTIOUS
                    ? "Stay sharp. That fight might not be over." : "That got interesting fast.";
        } else if (player.getHealth() < player.getMaxHealth() * 0.40F) {
            line = "You're hurt. Don't push yourself too hard.";
        } else if (player.isSprinting() || player.getDeltaMovement().horizontalDistanceSqr() > 0.10D) {
            line = switch (companion.getPersonality()) {
                case PROUD -> "Keep moving. I'm not falling behind.";
                case AGGRESSIVE -> "Finally, some pace.";
                case CAUTIOUS -> "Slow down a little. I'd rather see where we're going.";
                default -> "You've got somewhere in mind, or are we just moving?";
            };
        } else if (player.isUsingItem()) {
            line = "Good time for a quick break.";
        } else {
            line = switch (companion.getPersonality()) {
                case HEROIC -> "Quiet for once. I don't mind it.";
                case CALM -> "This is nice. Just travelling without a crisis.";
                case CAUTIOUS -> "Nothing strange nearby so far.";
                case PROUD -> "Don't get too comfortable. We still have ground to cover.";
                case AGGRESSIVE -> "If nothing happens soon, I'm picking the next route.";
            };
        }
        companion.speak(line, 92);
    }

    /** GUI-facing companion recovery. */
    public static boolean regroupCompanion(ServerPlayer player) {
        return recoverCompanion(player, true) != null;
    }

    private static AmbientFighterEntity recoverCompanion(ServerPlayer player, boolean forceLoadLastChunk) {
        if (player == null || companionId(player) == null || !(player.level() instanceof ServerLevel targetLevel)) return null;
        // Companions belong to Living World's supported realms. Never recreate/regroup one in Afterlife
        // or another unrelated dimension just because the player arrived there through Instant Transmission.
        if (!LivingWorldDimensions.isSupported(targetLevel)) return null;
        CompoundTag root = root(player);
        UUID id = companionId(player);
        UUID recordId = root.hasUUID("CompanionRecord") ? root.getUUID("CompanionRecord") : null;
        AmbientFighterEntity companion = findLoadedCompanion(player, id);
        if (recordId != null && FighterLegacyWorldData.get(targetLevel).isDeadRecord(recordId)) {
            if (companion != null) companion.discard();
            clearCompanion(player);
            return null;
        }

        // We persist the last physical location specifically so a temporarily unloaded entity can
        // be recovered by UUID before we ever consider recreating it, preventing duplicate companions.
        if (companion == null && forceLoadLastChunk && root.contains("CompanionLastX") && root.contains("CompanionLastZ")) {
            String wantedDimension = root.getString("CompanionDimension");
            BlockPos last = new BlockPos(root.getInt("CompanionLastX"), root.getInt("CompanionLastY"), root.getInt("CompanionLastZ"));
            for (ServerLevel candidate : player.getServer().getAllLevels()) {
                if (!wantedDimension.isBlank() && !candidate.dimension().location().toString().equals(wantedDimension)) continue;
                candidate.getChunkAt(last);
                companion = entity(candidate, id);
                if (companion != null) {
                    LOGGER.info("[LW CompanionRecovery] found exact travelling companion uuid={} after loading saved chunk {} in {} for player={}",
                            id, last, candidate.dimension().location(), player.getGameProfile().getName());
                    break;
                }
            }
        }

        BlockPos regroup = player.getY() <= 0.0D
                ? findUndergroundRegroup(targetLevel, player.blockPosition())
                : AmbientFighterSpawner.findSafeGroundAround(targetLevel, player.blockPosition(), player.getRandom(), 5, 10, 48);

        if (companion != null && companion.level().dimension().equals(targetLevel.dimension())) {
            if (regroup == null) {
                // Never "regroup" a ground companion onto a wet/unsafe player block. Keep the
                // existing body and let travel retry when a dry destination becomes available.
                companion.getNavigation().stop();
                companion.setTarget(null);
                return companion;
            }
            companion.getNavigation().stop();
            companion.setTarget(null);
            companion.moveTo(regroup.getX() + 0.5D, regroup.getY(), regroup.getZ() + 0.5D,
                    companion.getYRot(), companion.getXRot());
            companion.setDeltaMovement(0.0D, 0.0D, 0.0D);
            root.putLong("LastCompanionRegroup", player.getServer().overworld().getGameTime());
            rememberCompanionState(player, root, companion);
            LOGGER.info("[LW CompanionRecovery] regrouped existing travelling companion uuid={} name={} near player={} without recreation",
                    companion.getUUID(), companion.getFighterName(), player.getGameProfile().getName());
            companion.speak("There you are. I caught up.", 58);
            return companion;
        }

        CompoundTag profile = companion != null ? companion.writeMemoryProfile()
                : root.contains("CompanionProfile", net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? root.getCompound("CompanionProfile").copy() : new CompoundTag();
        UUID previousUuid = companion != null ? companion.getUUID() : id;
        if (companion != null) {
            LOGGER.info("[LW CompanionRecovery] travelling companion uuid={} is loaded outside the player's current dimension; preserving profile before safe recreation",
                    companion.getUUID());
            companion.discard();
        }

        AmbientFighterEntity recreated = null;
        if (recordId != null) {
            CompoundTag record = FighterMemoryManager.internalSignalRecord(player, recordId);
            if (profile.isEmpty() && record.contains("Profile", net.minecraft.nbt.Tag.TAG_COMPOUND)) profile = record.getCompound("Profile").copy();
            if (!profile.isEmpty()) recreated = AmbientFighterSpawner.spawnRememberedAt(player, profile, recordId,
                    Math.max(1, record.getInt("Encounters")), record.getInt("Relationship"), record.getBoolean("Rescued"), null, true);
        }
        if (recreated == null && !profile.isEmpty()) recreated = AmbientFighterSpawner.spawnProfileNearPlayer(player, profile);
        if (recreated == null) {
            LOGGER.warn("[LW CompanionRecovery] failed to recover travelling companion uuid={} record={} for player={}",
                    previousUuid, recordId, player.getGameProfile().getName());
            return null;
        }

        LOGGER.warn("[LW CompanionRecovery] recreated missing travelling companion logical identity oldUuid={} newUuid={} record={} name={} player={}",
                previousUuid, recreated.getUUID(), recordId, recreated.getFighterName(), player.getGameProfile().getName());
        root.putUUID("Companion", recreated.getUUID());
        root.putLong("LastCompanionRegroup", player.getServer().overworld().getGameTime());
        root.remove("CompanionMissingSince");
        rememberCompanionState(player, root, recreated);
        recreated.speak("There you are. I caught up.", 58);
        return recreated;
    }

    private static AmbientFighterEntity findLoadedCompanion(ServerPlayer player, UUID id) {
        if (player == null || id == null || player.getServer() == null) return null;
        for (ServerLevel candidate : player.getServer().getAllLevels()) {
            AmbientFighterEntity found = entity(candidate, id);
            if (found != null) return found;
        }
        return null;
    }

    private static BlockPos findUndergroundRegroup(ServerLevel level, BlockPos center) {
        if (level == null || center == null) return null;
        for (int radius = 1; radius <= 4; radius++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        BlockPos feet = center.offset(dx, dy, dz);
                        if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
                            return feet;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void rememberCompanionState(ServerPlayer player, CompoundTag root, AmbientFighterEntity npc) {
        if (player == null || root == null || npc == null) return;
        npc.getPersistentData().putUUID("LWCompanionOwner", player.getUUID());
        root.putString("CompanionName", npc.getFighterName());
        root.put("CompanionProfile", npc.writeMemoryProfile().copy());
        if (npc.getMemoryRecordId() != null) root.putUUID("CompanionRecord", npc.getMemoryRecordId());
        root.putString("CompanionDimension", npc.level().dimension().location().toString());
        root.putInt("CompanionLastX", npc.blockPosition().getX());
        root.putInt("CompanionLastY", npc.blockPosition().getY());
        root.putInt("CompanionLastZ", npc.blockPosition().getZ());
        save(player, root);
    }

    private static void latchDefenseThreat(ServerPlayer player, LivingEntity attacker, long now, long duration) {
        if (player == null || attacker == null || attacker == player || !attacker.isAlive()) return;
        if (attacker instanceof AmbientFighterEntity member && isCompanion(player, member)) return;
        DEFENSE_THREATS.put(player.getUUID(), new DefenseThreat(attacker.getUUID(), now + Math.max(40L, duration)));

        for (UUID id : companionIds(player)) {
            AmbientFighterEntity companion = findLoadedCompanion(player, id);
            if (companion == null || !companion.isAlive() || companion.level() != player.level()
                    || companion.getHealth() < companion.getMaxHealth() * 0.18F) continue;
            if (loyaltyConflict(companion, attacker) || !sagaHelpAllowed(attacker)) continue;
            if (companion.isMeditating()) companion.stopMeditation(false);
            FighterAmbientActivityManager.cancel(companion);
            companion.setSocialLifeActivity(false);
            companion.setTarget(attacker);
        }
    }

    private static LivingEntity latchedDefenseThreat(ServerPlayer player, ServerLevel level, long now) {
        DefenseThreat defense = DEFENSE_THREATS.get(player.getUUID());
        if (defense == null) return null;
        if (defense.expires() < now) {
            DEFENSE_THREATS.remove(player.getUUID());
            return null;
        }

        net.minecraft.world.entity.Entity entity = level.getEntity(defense.target());
        if (!(entity instanceof LivingEntity living) || !living.isAlive()
                || player.distanceToSqr(living) > 96.0D * 96.0D) {
            DEFENSE_THREATS.remove(player.getUUID());
            return null;
        }
        return living;
    }

    /**
     * Deterministic reproduction for companion protection against Dragon Mine Z's actual
     * Red Ribbon soldier entity. Requires an active travelling companion.
     */
    public static int debugCompanionHelp(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        AmbientFighterEntity companion = findLoadedCompanion(player, companionId(player));
        if (companion == null || !companion.isAlive()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Living World] No loaded travelling companion. Use the Companion system first."), false);
            return 0;
        }

        var type = ForgeRegistries.ENTITY_TYPES.getValue(
                new ResourceLocation("dragonminez", "red_ribbon_soldier"));
        if (type == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Living World] Dragon Mine Z red_ribbon_soldier entity was not found."), false);
            return 0;
        }

        net.minecraft.world.entity.Entity created = type.create(level);
        if (!(created instanceof LivingEntity attacker)) {
            if (created != null) created.discard();
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Living World] Red Ribbon soldier could not be created as a living attacker."), false);
            return 0;
        }

        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 0.01D) flat = new Vec3(1.0D, 0.0D, 0.0D);
        flat = flat.normalize().scale(6.0D);
        attacker.moveTo(player.getX() + flat.x, player.getY(), player.getZ() + flat.z,
                player.getYRot() + 180.0F, 0.0F);
        if (attacker instanceof Mob mob) mob.setTarget(player);
        if (!level.addFreshEntity(attacker)) {
            attacker.discard();
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Living World] Could not add the Red Ribbon soldier to the world."), false);
            return 0;
        }

        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "[Living World] Spawned a real DMZ Red Ribbon soldier targeting you. Let it land one hit; your companion should intervene immediately."), false);
        return 1;
    }

    private static boolean sagaHelpAllowed(LivingEntity target) {
        return LivingWorldConfig.companionSagaHelp()
                || !(target instanceof DBSagasEntity)
                || target instanceof AmbientFighterEntity;
    }

    /** Player-facing Come Along request. Friendship helps, but fighters can still be busy or decline. */
    public static boolean requestCompanion(ServerPlayer player, AmbientFighterEntity npc) {
        if (player == null || npc == null || !(player.level() instanceof ServerLevel level)) return false;
        if (isCompanion(player, npc)) {
            removeCompanion(player, npc);
            npc.speak("I'll head out on my own again.", 58);
            return true;
        }
        if (companionIds(player).size() >= MAX_COMPANIONS) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "You can't have more than 5 companions!").withStyle(net.minecraft.ChatFormatting.RED), false);
            return false;
        }
        if (!npc.isAlive() || npc.isCaptive() || npc.isDefeated() || npc.isRecovering() || npc.isMeditating()
                || npc.isTransforming() || npc.isKaiokenActive() || npc.getTarget() != null
                || npc.isSocialLifeActivity() || npc.isSocialPlayerApproach() || npc.isSocialPowerDisplay()
                || npc.isSanctionedMatchParticipant()) {
            npc.speak("Not right now. I've got something else going on.", 76);
            return false;
        }
        int relationship = npc.isRememberedFor(player) ? npc.getMemoryRelationship() : Integer.MIN_VALUE;
        boolean factionFriend = npc.isFactionMember() && FactionManager.getReputation(player, npc.getFactionId()) >= FactionManager.FRIENDLY_REP;
        if (relationship < 15 && !factionFriend) {
            npc.speak("We don't know each other well enough for that yet.", 82);
            return false;
        }
        // Familiar fighters are willing sometimes; established friends generally say yes unless busy.
        if (relationship >= 15 && relationship < 35) {
            float accept = switch (npc.getPersonality()) {
                case HEROIC -> 0.78F;
                case CALM -> 0.68F;
                case CAUTIOUS -> 0.48F;
                case PROUD -> 0.42F;
                case AGGRESSIVE -> 0.58F;
            };
            if (npc.getRandom().nextFloat() > accept) {
                npc.speak(switch (npc.getPersonality()) {
                    case CAUTIOUS -> "Maybe another time. I have my own route today.";
                    case PROUD -> "Not today. Keep up with me a little longer first.";
                    case AGGRESSIVE -> "Not this time. I've got my own thing to do.";
                    default -> "Not today. Maybe another time.";
                }, 84);
                return false;
            }
        }
        setCompanion(player, npc);
        npc.setSocialLifeActivity(false);
        npc.speak(switch (npc.getPersonality()) {
            case HEROIC -> "Sure. I'll watch your back.";
            case CALM -> "All right. Let's go together for a while.";
            case CAUTIOUS -> "Okay. But let's not get careless.";
            case PROUD -> "Fine. Just don't expect me to follow blindly.";
            case AGGRESSIVE -> "Yeah. If trouble finds us, even better.";
        }, 90);
        FighterMemoryManager.strengthenRelationship(player, npc, 1,
                FighterRelationshipManager.BondEvent.TRAVEL, "Agreed to travel together");
        INVITES.remove(player.getUUID());
        return true;
    }

    private static boolean loyaltyConflict(AmbientFighterEntity companion, LivingEntity target) {
        if (!(target instanceof AmbientFighterEntity other)) return false;
        if (companion.isFactionMember() && other.isFactionMember() && FactionManager.areAllies(companion, other)) return true;
        // Travelling with the player does not erase the fighter's own close NPC relationships.
        return FighterNpcSocialManager.bond(companion, other) >= 6;
    }

    private static void setCompanion(ServerPlayer player, AmbientFighterEntity npc) {
        CompoundTag root = root(player);
        boolean firstCompanion = companionIds(player).isEmpty();
        List<UUID> roster = companionIds(player);
        if (!roster.contains(npc.getUUID())) roster.add(npc.getUUID());
        writeCompanionIds(root, roster);
        if (firstCompanion) {
            root.putUUID("Companion", npc.getUUID());
            root.putString("CompanionName", npc.getFighterName());
            root.putLong("CompanionJoined", player.serverLevel().getServer().overworld().getGameTime());
        }
        root.remove("CompanionMissingSince");
        root.remove("CompanionFriendlyFireStrikes");
        root.remove("CompanionLastFriendlyFire");
        root.remove("CompanionFriendlyFireBrokenUntil");
        root.putLong("NextCompanionChatter", player.serverLevel().getServer().overworld().getGameTime() + 1200L + player.getRandom().nextInt(1801));
        npc.getPersistentData().putUUID("LWCompanionOwner", player.getUUID());
        Deque<Vec3> trail = new ArrayDeque<>();
        trail.add(player.position().add(0.0D, 0.8D, 0.0D));
        TRAVEL_TRAILS.put(player.getUUID(), trail);
        if (firstCompanion) rememberCompanionState(player, root, npc); else save(player, root);
        npc.setPersistenceRequired();
    }

    public static UUID companionId(ServerPlayer player) {
        List<UUID> ids = companionIds(player);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public static List<UUID> companionIds(ServerPlayer player) {
        if (player == null) return new ArrayList<>();
        CompoundTag root = root(player);
        List<UUID> result = new ArrayList<>();
        if (root.contains("CompanionIds", Tag.TAG_LIST)) {
            ListTag list = root.getList("CompanionIds", Tag.TAG_STRING);
            for (int i = 0; i < list.size() && result.size() < MAX_COMPANIONS; i++) {
                try { UUID id = UUID.fromString(list.getString(i)); if (!result.contains(id)) result.add(id); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        if (root.hasUUID("Companion") && !result.contains(root.getUUID("Companion"))) result.add(0, root.getUUID("Companion"));
        if (!result.isEmpty()) writeCompanionIds(root, result);
        return result;
    }

    private static void writeCompanionIds(CompoundTag root, List<UUID> ids) {
        ListTag list = new ListTag();
        ids.stream().limit(MAX_COMPANIONS).forEach(id -> list.add(StringTag.valueOf(id.toString())));
        root.put("CompanionIds", list);
        if (ids.isEmpty()) root.remove("Companion"); else root.putUUID("Companion", ids.get(0));
    }

    public static boolean isCompanion(ServerPlayer player, AmbientFighterEntity npc) {
        return player != null && npc != null && companionIds(player).contains(npc.getUUID());
    }

    public static void removeCompanion(ServerPlayer player, AmbientFighterEntity npc) {
        if (player == null || npc == null) return;
        CompoundTag root = root(player);
        List<UUID> ids = companionIds(player);
        if (!ids.remove(npc.getUUID())) return;
        writeCompanionIds(root, ids);
        npc.getPersistentData().remove("LWCompanionOwner");
        npc.getNavigation().stop(); npc.setTarget(null); npc.setFlying(false); npc.setFlyingFast(false); npc.setNoGravity(false);
        if (ids.isEmpty()) {
            clearCompanion(player);
        } else {
            AmbientFighterEntity promoted = findLoadedCompanion(player, ids.get(0));
            root.putLong("CompanionJoined", player.serverLevel().getServer().overworld().getGameTime());
            root.remove("CompanionMissingSince");
            if (promoted != null) {
                root.putString("CompanionName", promoted.getFighterName());
                rememberCompanionState(player, root, promoted);
            } else save(player, root);
        }
    }

    public static String companionName(ServerPlayer player) { return root(player).getString("CompanionName"); }

    public static boolean isTravellingCompanion(AmbientFighterEntity fighter) {
        if (fighter == null || !(fighter.level() instanceof ServerLevel level)) return false;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (isCompanion(player, fighter)) return true;
        }
        return false;
    }

    /** DMZ-facing ownership/team check used by relation, healing and buff targeting. */
    public static boolean isCompanionAlly(Player player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !(fighter.level() instanceof ServerLevel level)) return false;
        if (fighter.isSanctionedMatchParticipant() && fighter.isSanctionedOpponent(player)) return false;
        UUID ownerId = fighter.getPersistentData().hasUUID("LWCompanionOwner")
                ? fighter.getPersistentData().getUUID("LWCompanionOwner") : null;
        if (ownerId == null) return false;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null || !isCompanion(owner, fighter)) return false;
        return player.getUUID().equals(ownerId) || PartyManager.areInSameParty(owner, player);
    }

    /** True only while this player's current travelling companion has an active combat target. */
    public static boolean isCombatProtectedCompanion(ServerPlayer player, AmbientFighterEntity npc) {
        if (player == null || npc == null || npc.getTarget() == null) return false;
        if (npc.isSanctionedMatchParticipant() && npc.isSanctionedOpponent(player)) return false;
        return isCompanion(player, npc);
    }

    /**
     * Protects against accidental friendly fire while travelling. The first three hits inside a
     * short window are treated as mistakes: no damage, retaliation, faction penalty or friendship
     * fallout is applied. A fourth hit is allowed through as deliberate aggression.
     */
    public static boolean protectCompanionFromFriendlyFire(ServerPlayer player, AmbientFighterEntity npc) {
        if (player == null || npc == null) return false;
        if (npc.isSanctionedMatchParticipant() && npc.isSanctionedOpponent(player)) return false;
        if (!isCompanion(player, npc)) return false;

        CompoundTag root = root(player);
        long now = player.serverLevel().getServer().overworld().getGameTime();
        long brokenUntil = root.getLong("CompanionFriendlyFireBrokenUntil");
        if (brokenUntil > now) return false;
        if (brokenUntil > 0L) root.remove("CompanionFriendlyFireBrokenUntil");
        long last = root.getLong("CompanionLastFriendlyFire");
        int strikes = now - last > FRIENDLY_FIRE_RESET_TICKS ? 0 : root.getInt("CompanionFriendlyFireStrikes");
        strikes++;
        root.putLong("CompanionLastFriendlyFire", now);

        if (strikes <= 3) {
            root.putInt("CompanionFriendlyFireStrikes", strikes);
            save(player, root);
            npc.setTarget(null);
            npc.speak(switch (strikes) {
                case 1 -> "Hey! Watch it.";
                case 2 -> "Seriously. Be careful.";
                default -> "That's three. Hit me again and I'm defending myself.";
            }, 92);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[Living World] Companion warning " + strikes + "/3 — accidental hit ignored."), false);
            return true;
        }

        root.remove("CompanionFriendlyFireStrikes");
        root.remove("CompanionLastFriendlyFire");
        root.putLong("CompanionFriendlyFireBrokenUntil", now + FRIENDLY_FIRE_RESET_TICKS);
        save(player, root);
        if (npc.getSpeech().isEmpty()) npc.speak("Enough. I'm not taking another one.", 92);
        return false;
    }

    /** Debug hook for validating shared Meditation. Creates a neutral test fighter if none is available. */
    public static int forceMeditationInvite(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !MeditationCompat.isAvailable()) return 0;
        AmbientFighterEntity npc = level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(48.0D), f -> f.isAlive() && !f.isCaptive() && !f.isDefeated()
                        && !f.isNonCombatant() && !f.isTransforming() && !f.isKaiokenActive()
                        && f.getTarget() == null && !f.isMeditating())
                .stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (npc == null) {
            npc = AmbientFighterSpawner.spawnNearPlayer(player, FighterAlignment.NEUTRAL, FighterRank.TRAINED, true);
            if (npc == null) return 0;
        }
        if (!MeditationCompat.startPlayerMeditation(player)) return 0;
        if (!npc.beginSharedMeditation(player)) return 0;
        trackMeditationBond(player, npc, level.getServer().overworld().getGameTime());
        npc.speak("Let's focus for a while.", 82);
        return 1;
    }

    /** Debug/QA hook: creates a proper persistent travelling companion immediately. */
    public static int forceSpawnCompanion(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        UUID existing = companionId(player);
        if (existing != null) {
            AmbientFighterEntity loaded = findLoadedCompanion(player, existing);
            if (loaded != null && loaded.isAlive()) return 1;
            AmbientFighterEntity recovered = recoverCompanion(player, true);
            return recovered != null ? 1 : 0;
        }
        AmbientFighterEntity npc = AmbientFighterSpawner.spawnNearPlayer(player, FighterAlignment.GOOD, FighterRank.TRAINED, true);
        if (npc == null) return 0;
        npc.setFlightUnlockedForDebug(true);
        setCompanion(player, npc);
        npc.setSocialLifeActivity(false);
        npc.speak("Let's move. I'll travel with you.", 90);
        return 1;
    }

    /** Debug hook: invites the nearest suitable fighter without bypassing the companion rules after acceptance. */
    public static int forceCompanionInvite(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || companionId(player) != null) return 0;
        AmbientFighterEntity npc = level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(48.0D), f -> f.isAlive() && !f.isCaptive() && !f.isDefeated()
                        && !f.isNonCombatant() && f.getTarget() == null)
                .stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (npc == null) return 0;
        long now = level.getServer().overworld().getGameTime();
        INVITES.put(player.getUUID(), new Invite(npc.getUUID(), InviteType.TRAVEL, now + 1200L));
        npc.speak("You're heading out again? Want some company?", 110);
        npc.getNavigation().moveTo(player, 1.05D);
        return 1;
    }

    public static String status(ServerPlayer player) {
        Invite invite = INVITES.get(player.getUUID());
        String invitation = invite == null ? "none" : invite.type.name().toLowerCase(java.util.Locale.ROOT);
        String companion = companionName(player);
        return "companion=" + (companion.isBlank() ? "none" : companion)
                + " • pending invite=" + invitation
                + " • Meditation=" + (MeditationCompat.isAvailable() ? "enabled" : "disabled");
    }

    public static void clearCompanion(ServerPlayer player) {
        TRAVEL_TRAILS.remove(player.getUUID());
        CompoundTag root = root(player);
        for (UUID id : companionIds(player)) {
            AmbientFighterEntity member = findLoadedCompanion(player, id);
            if (member != null) member.getPersistentData().remove("LWCompanionOwner");
        }
        UUID currentId = root.hasUUID("Companion") ? root.getUUID("Companion") : null;
        AmbientFighterEntity current = findLoadedCompanion(player, currentId);
        if (current != null) {
            current.getNavigation().stop();
            current.setTarget(null);
            current.setSprinting(false);
            current.setFlying(false);
            current.setFlyingFast(false);
            current.setNoGravity(false);
            current.getPersistentData().remove("LWCompanionOwner");
        }
        root.remove("Companion"); root.remove("CompanionIds"); root.remove("CompanionName"); root.remove("CompanionJoined");
        root.remove("CompanionRecord"); root.remove("CompanionProfile"); root.remove("CompanionDimension");
        root.remove("CompanionLastX"); root.remove("CompanionLastY"); root.remove("CompanionLastZ");
        root.remove("CompanionMissingSince"); root.remove("LastCompanionRegroup");
        root.remove("CompanionFriendlyFireStrikes"); root.remove("CompanionLastFriendlyFire");
        root.remove("CompanionFriendlyFireBrokenUntil"); root.remove("NextCompanionChatter");
        if (current != null) {
            String[] travelKeys = {"LWTravelGoalX","LWTravelGoalY","LWTravelGoalZ","LWTravelBestDistance","LWTravelProgressAt","LWTravelStall",
                    "LWTravelWaypointX","LWTravelWaypointY","LWTravelWaypointZ","LWTravelWaypointUntil"};
            for (String key : travelKeys) current.getPersistentData().remove(key);
        }
        save(player, root);
    }

    private static AmbientFighterEntity entity(ServerLevel level, UUID id) {
        if (level == null || id == null) return null;
        var entity = level.getEntity(id);
        return entity instanceof AmbientFighterEntity fighter ? fighter : null;
    }

    private static CompoundTag root(ServerPlayer player) {
        CompoundTag p = player.getPersistentData();
        if (!p.contains(ROOT, net.minecraft.nbt.Tag.TAG_COMPOUND)) p.put(ROOT, new CompoundTag());
        return p.getCompound(ROOT);
    }
    private static void save(ServerPlayer player, CompoundTag root) { player.getPersistentData().put(ROOT, root); }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        // DMZ's native kill event requires a ServerPlayer damage source. Forward a companion's
        // finishing blow to its owner once, so saga/custom quest objectives use the same public
        // credit pipeline as a direct player kill.
        LivingEntity killer = responsibleAttacker(event.getSource());
        if (killer instanceof AmbientFighterEntity companion
                && companion.getPersistentData().hasUUID("LWCompanionOwner")) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(
                    companion.getPersistentData().getUUID("LWCompanionOwner"));
            if (owner != null && isCompanion(owner, companion))
                QuestEvents.creditQuestKill(owner, event.getEntity());
        }

        if (!(event.getEntity() instanceof AmbientFighterEntity npc)) return;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (isCompanion(player, npc)) removeCompanion(player, npc);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer old) || !(event.getEntity() instanceof ServerPlayer copy)) return;
        if (old.getPersistentData().contains(ROOT, net.minecraft.nbt.Tag.TAG_COMPOUND))
            copy.getPersistentData().put(ROOT, old.getPersistentData().getCompound(ROOT).copy());
    }
}
