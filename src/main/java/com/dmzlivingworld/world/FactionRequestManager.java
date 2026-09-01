package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.network.FactionRequestTrackerPacket;
import com.dmzlivingworld.network.SupplyItemSnapshot;
import com.dmzlivingworld.network.FactionRequestCompletePacket;
import com.dmzlivingworld.network.LWNetwork;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Need-driven faction requests. R37 keeps the four physical supply jobs and reintroduces Patrol
 * as the first offensive/field request family, using only real persistent faction residents. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FactionRequestManager {
    private static final String ROOT = "DMZLivingWorldFactionRequest";
    private static final String OFFERS = "Offers";
    // Boards are re-evaluated on staggered real-time intervals, but an interval never manufactures a quest.
    // Availability is driven by current faction needs; a quiet/healthy faction can legitimately have no work.
    private static final long BOARD_RECHECK_MIN_MS = 35L * 60L * 1000L;
    private static final long BOARD_RECHECK_MAX_MS = 95L * 60L * 1000L;
    private static final long OFFER_LIFETIME_MIN_MS = 45L * 60L * 1000L;
    private static final long OFFER_LIFETIME_MAX_MS = 120L * 60L * 1000L;
    private static long lastWarCaptureDay = -1L;

    private FactionRequestManager() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ServerLevel overworld = event.getServer().overworld();
        long now = overworld.getGameTime();
        long day = now / 24000L;
        if (day != lastWarCaptureDay) {
            lastWarCaptureDay = day;
            simulateRareCaptures(overworld, now, day);
            resolveOldPrisoners(overworld, now);
        }
        // Navigation is intentionally more responsive than request simulation. The objective snapshot is refreshed
        // four times per second while the actual quest logic keeps its established once-per-second cadence.
        if (now % 5L == 0L) {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) continue;
                CompoundTag active = request(player);
                if (!active.getString("Type").isBlank() && !isSupportedRequestType(active.getString("Type"))) {
                    clearRequest(player);
                    continue;
                }
                if (!active.getString("Type").isBlank()) {
                    tickMovementOnly(player, level, now, active);
                    updateQuestTracker(player, level, now, active);
                }
            }
        }
        if (now % 20L != 0L) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) continue;
            tickPlayer(player, level, now);
        }
    }


    /** A supply hand-in is an explicit interaction with the assigned real faction receiver. */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // Forge may emit the interaction for both hands. Consume a shipment exactly once per physical click.
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof AmbientFighterEntity fighter)) return;
        if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) return;
        // Shift+Right-click belongs to the native LW profile. The profile exposes a styled Deliver Supplies
        // button for the assigned receiver; ordinary right-click remains a quick hand-in shortcut.
        if (player.isShiftKeyDown()) return;
        CompoundTag req = request(player);
        long now = level.getServer().overworld().getGameTime();
        if (isSupplyRequest(req)) {
            if (!req.hasUUID("SupplyReceiverEntity") || !req.getUUID("SupplyReceiverEntity").equals(fighter.getUUID())) return;
            WorldFaction supplier = FactionWorldData.get(level).byId(req.getString("Source"));
            if (supplier == null) return;
            deliverSupplies(player, level, supplier, req, now);
            event.setCanceled(true);
            return;
        }

    }

    private static void simulateRareCaptures(ServerLevel overworld, long now, long day) {
        FactionWorldData data = FactionWorldData.get(overworld);
        data.tickOrganizations(overworld);
        // World-seeded daily roll: rare enough that captures remain notable.
        var random = net.minecraft.util.RandomSource.create(FactionWorldData.mix(overworld.getSeed() ^ day * 0xB5297A4D1B56C4E9L));
        if (PrisonerWorldData.get(overworld).active().size() >= 5 || random.nextFloat() > 0.075F) return;
        List<WorldFaction> atWar = data.activeFactions().stream().filter(f -> !data.warEnemies(f, now).isEmpty()).toList();
        if (atWar.isEmpty()) return;
        WorldFaction victim = atWar.get(random.nextInt(atWar.size()));
        List<WorldFaction> enemies = data.warEnemies(victim, now);
        if (enemies.isEmpty()) return;
        WorldFaction captor = enemies.get(random.nextInt(enemies.size()));
        List<AmbientFighterEntity> candidates = FactionRequestMissionManager.loadedAvailableResidents(overworld, victim,
                f -> !f.isNonCombatant() && !f.isCaptive() && !f.isDefeated());
        if (candidates.isEmpty()) return; // no real loaded person means no capture event today
        AmbientFighterEntity captive = candidates.get(random.nextInt(candidates.size()));
        PrisonerWorldData.Prisoner prisoner = PrisonerWorldData.get(overworld).captureExisting(overworld, captive, captor, now);
        if (prisoner == null) return;
        BlockPos prisonSite = findPracticalFactionLand(overworld, captor, null);
        if (prisonSite != null && overworld.getEntitiesOfClass(ServerPlayer.class, captive.getBoundingBox().inflate(96.0D)).isEmpty()) {
            captive.teleportTo(prisonSite.getX() + 0.5D, prisonSite.getY(), prisonSite.getZ() + 0.5D);
        }
    }

    private static void resolveOldPrisoners(ServerLevel overworld, long now) {
        PrisonerWorldData prisoners = PrisonerWorldData.get(overworld);
        for (PrisonerWorldData.Prisoner p : prisoners.active()) {
            long age = now - p.capturedAt;
            long deadline = 120000L + Math.floorMod(p.id.hashCode(), 72001); // ~5-8 days
            if (age < deadline) continue;

            ServerLevel realmLevel = LivingWorldDimensions.levelFor(overworld.getServer(), p.realm);
            AmbientFighterEntity physical = realmLevel == null || p.entityId == null ? null : entity(realmLevel, p.entityId);
            if (physical != null) {
                // Do not resolve somebody out from under a player who is actively at the prison scene.
                boolean witnessed = !realmLevel.getEntitiesOfClass(ServerPlayer.class, physical.getBoundingBox().inflate(96.0D)).isEmpty();
                if (witnessed) continue;
            }
            prisoners.resolveExpired(overworld, p, now);
            if (physical != null) {
                physical.setCaptive(false); physical.setTarget(null); physical.getNavigation().stop();
                if ("DEFECTED".equals(p.status)) {
                    WorldFaction newFaction = FactionWorldData.get(overworld).byId(p.captorFactionId);
                    if (newFaction != null) physical.assignFaction(newFaction, p.role, null, false, true);
                    physical.setStoryRole(AmbientFighterEntity.STORY_NONE);
                } else if ("DEAD".equals(p.status)) {
                    physical.kill();
                } else {
                    physical.setStoryRole(AmbientFighterEntity.STORY_NONE);
                }
            }
        }
    }

    /**
     * Movement-only refresh for already-committed request actors. This deliberately cannot advance
     * checkpoints, holds, rewards, ambushes or any other objective state; those remain in tickActive()
     * at the established once-per-second cadence.
     */
    private static void tickMovementOnly(ServerPlayer player, ServerLevel level, long now, CompoundTag req) {
        if (player == null || level == null || req == null || !"PATROL".equals(req.getString("Type"))) return;
        if (req.getBoolean("PatrolContactActive") || (req.getBoolean("PatrolAmbushTriggered") && !req.getBoolean("PatrolAmbushResolved"))) return;
        List<AmbientFighterEntity> patrol = FactionRequestMissionManager.loadedActiveRoster(level, req, "Patrol");
        if (patrol.isEmpty()) return;
        WorldFaction faction = FactionWorldData.get(level).byId(req.getString("Source"));
        AmbientFighterEntity leader = resolvePatrolLeader(player, level, req, faction, patrol);
        if (leader == null || !leader.isAlive() || (leader.getTarget() != null && leader.getTarget().isAlive())) return;

        if (!req.getBoolean("Started")) {
            BlockPos rendezvous = patrolRendezvous(req);
            if (rendezvous != null && leader.distanceToSqr(rendezvous.getX() + 0.5D, rendezvous.getY(), rendezvous.getZ() + 0.5D) > 5.0D * 5.0D)
                FactionRequestMissionManager.navigateMissionActor(leader, rendezvous, 1.10D, now);
            return;
        }
        if (!req.contains("PatrolRoute", Tag.TAG_LIST)) return;
        ListTag route = req.getList("PatrolRoute", Tag.TAG_COMPOUND);
        if (route.isEmpty()) return;
        int leg = Math.max(0, Math.min(route.size() - 1, req.getInt("PatrolLeg")));
        CompoundTag point = route.getCompound(leg);
        BlockPos waypoint = new BlockPos(point.getInt("X"), point.getInt("Y"), point.getInt("Z"));
        boolean fighting = patrol.stream().anyMatch(f -> f.getTarget() != null && f.getTarget().isAlive());
        if (fighting) return;
        boolean flightLeg = req.contains("PatrolLegFlight") ? req.getBoolean("PatrolLegFlight")
                : patrolFlightLeg(player, req, patrol, leader, waypoint);
        if (flightLeg) movePatrolByAir(req, patrol, leader, waypoint);
        else movePatrolOnFoot(patrol, leader, waypoint, now);
    }

    private static void tickPlayer(ServerPlayer player, ServerLevel level, long now) {
        CompoundTag req = request(player);
        if (!req.getString("Type").isBlank()) {
            tickActive(player, level, now, req);
            return;
        }
        CompoundTag root = root(player);
        long next = root.getLong("NextOffer");
        if (next == 0L) {
            root.putLong("NextOffer", now + 72000L + player.getRandom().nextInt(72001)); // 3-6 days before first chance
            save(player, root); return;
        }
        if (now < next) return;
        root.putLong("NextOffer", now + 120000L + player.getRandom().nextInt(120001)); // then ~5-10 days
        save(player, root);
        createRareOffer(player, level, now);
    }

    private static void createRareOffer(ServerPlayer player, ServerLevel level, long now) {
        // Global rare postings remain retired. Per-faction boards are the only request source.
    }

    private static void tickActive(ServerPlayer player, ServerLevel level, long now, CompoundTag req) {
        String type = req.getString("Type");
        // Removed mission families are terminal on upgrade. R42 supports only the stable
        // supply requests and Patrol.
        if (!isSupportedRequestType(type)) {
            notify(player, "This legacy faction request type is not enabled in the current build. It was cleared without a cooldown.");
            clearRequest(player);
            return;
        }

        FactionRequestMissionManager.requestId(req);
        migrateLegacyRequestActors(player, level, req);
        migrateR281RequestState(req);
        FactionWorldData data = FactionWorldData.get(level);
        WorldFaction supplier = data.byId(req.getString("Source"));
        // Accepted supply work is stable. Board offers may expire/reassess, but an accepted shipment does
        // not silently die while the player is gathering the exact requested goods.
        if (supplier == null) {
            clearRequest(player);
            return;
        }
        if (isSupplyType(type)) ensureExactSupplyBasket(req, type, player.getRandom());
        if (supplier.realm() != LivingWorldDimensions.realm(level)) return;

        if (isSupplyType(type)) tickSupplyReceiver(player, level, now, req, supplier);
        else if ("PATROL".equals(type)) tickPatrol(player, level, now, req, supplier);
    }

    private static void migrateR281RequestState(CompoundTag req) {
        if (req == null || req.getBoolean("R28_1StateMigration")) return;
        String type = req.getString("Type");
        if (("CAPTURE".equals(type) || "ELITE_CAPTURE".equals(type)) && !req.getBoolean("CaptureSecured")
                && req.getBoolean("Started") && !req.getBoolean("CaptureEncountered")) {
            req.putBoolean("Started", false);
        }
        req.putBoolean("DebugImmediate", false);
        for (String key : new java.util.ArrayList<>(req.getAllKeys())) {
            if (key.startsWith("MandatoryRosterWait.") || key.startsWith("MandatoryRosterWaitTicks.") || key.startsWith("MandatoryRosterWaitSince.")
                    || key.startsWith("RealMemberWait.") || key.startsWith("RealMemberWaitTicks.") || key.startsWith("RealMemberWaitSince.")
                    || "RealRosterWaitTicks".equals(key) || "RealRosterWaitSince".equals(key) || "RealRosterWaitNotified".equals(key)) req.remove(key);
        }
        req.putBoolean("R28_1StateMigration", true);
    }

    /**
     * One-time R26.x migration: retire only non-regional request stand-ins tied to the active request.
     * Normal persistent regional residents are never deleted. The request then rebinds to real roster UUIDs.
     */
    private static void migrateLegacyRequestActors(ServerPlayer player, ServerLevel level, CompoundTag req) {
        if (player == null || level == null || req == null || req.getBoolean("R27ActorMigration")) return;
        String source = req.getString("Source"), target = req.getString("Target");
        java.util.Set<UUID> legacyParties = new java.util.HashSet<>();
        for (String key : new String[]{"GuardParty","PatrolParty","PatrolAmbushParty","ReconParty","ExtractionPursuitParty","IntelGuardParty","SabotageGuardParty"})
            if (req.hasUUID(key)) legacyParties.add(req.getUUID(key));
        UUID legacyTarget = req.hasUUID("TargetEntity") ? req.getUUID("TargetEntity") : null;
        UUID legacyContact = req.hasUUID("IntelContact") ? req.getUUID("IntelContact") : null;
        int retired = 0;
        for (var entity : level.getAllEntities()) {
            if (!(entity instanceof AmbientFighterEntity fighter) || fighter.isRegionalPresence() || !fighter.isFactionMember()) continue;
            boolean requestStory = fighter.getStoryRole() >= AmbientFighterEntity.STORY_ALLY && fighter.getStoryRole() <= AmbientFighterEntity.STORY_CAPTIVE;
            boolean factionMatch = fighter.getFactionId().equals(source) || fighter.getFactionId().equals(target);
            boolean direct = fighter.getUUID().equals(legacyTarget) || fighter.getUUID().equals(legacyContact);
            boolean party = fighter.getPartyId() != null && legacyParties.contains(fighter.getPartyId());
            boolean nearbyLegacyScene = requestStory && factionMatch && fighter.distanceToSqr(player) <= 240.0D * 240.0D;
            if (!(direct || party || nearbyLegacyScene)) continue;
            if ("RESCUE".equals(req.getString("Type"))) {
                PrisonerWorldData.Prisoner old = PrisonerWorldData.get(level).byId(req.getString("Prisoner"));
                if (old != null && old.active() && fighter.getUUID().equals(old.entityId)) PrisonerWorldData.get(level).markRescued(level, old, level.getGameTime());
            }
            fighter.discard(); retired++;
        }
        for (String key : new String[]{"GuardParty","PatrolParty","PatrolAmbushParty","ReconParty","ExtractionPursuitParty","IntelGuardParty","SabotageGuardParty",
                "TargetEntity","PatrolLeader","IntelContact"}) req.remove(key);
        req.remove("IntelContactName"); req.remove("IntelContactX"); req.remove("IntelContactZ");
        req.remove("ReconX"); req.remove("ReconY"); req.remove("ReconZ");
        req.remove("PatrolX"); req.remove("PatrolY"); req.remove("PatrolZ"); req.remove("PatrolRoute"); req.remove("PatrolLeg");
        if ("MERCENARY_INTEL".equals(req.getString("Type")) && Math.max(1, req.getInt("SeriesStage")) == 1) {
            req.remove("ObjectiveX"); req.remove("ObjectiveY"); req.remove("ObjectiveZ"); req.putBoolean("IntelDetected", false); req.putInt("IntelSuspicion", 0);
        }
        req.putBoolean("Started", false); req.putBoolean("R27ActorMigration", true); resetPresence(req);
        saveRequest(player, req);
        if (retired > 0) notify(player, "Living World retired " + retired + " old request-only stand-in" + (retired == 1 ? "" : "s")
                + ". This request will now use an available faction member already in the world.");
    }

    private static void tickRescue(ServerPlayer player, ServerLevel level, long now, CompoundTag req,
                                   WorldFaction ally, WorldFaction captor) {
        PrisonerWorldData store = PrisonerWorldData.get(level);
        PrisonerWorldData.Prisoner p = store.byId(req.getString("Prisoner"));
        if (p == null || !p.active()) { clearRequest(player); return; }
        WorldFaction victim = FactionWorldData.get(level).byId(p.victimFactionId);
        if (victim == null) { clearRequest(player); return; }

        if (p.entityId != null && FactionRequestMissionManager.residentFallenOrDeparted(level, victim, p.entityId)) {
            store.markDead(level, p, now, "the captive did not survive until extraction");
            FactionManager.adjustReputation(player, ally, -2);
            notify(player, "Rescue failed: " + p.name + " is no longer alive/available. No substitute prisoner will be created.");
            setCooldown(player, ally, now + 24000L); clearRequest(player); return;
        }

        AmbientFighterEntity captive = p.entityId == null ? null : FactionRequestMissionManager.loadedResident(level, victim, p.entityId);
        if (captive == null && p.entityId != null) {
            BlockPos last = FactionRequestMissionManager.residentLastPos(level, victim, p.entityId);
            if (last != null && distanceSqTo(last, player) <= 180.0D * 180.0D)
                captive = FactionRequestMissionManager.materializeExistingResident(level, victim, p.entityId);
        }
        if (captive != null && !isRealFactionMember(level, victim, captive)) {
            if (!req.getBoolean("LegacySyntheticPrisonerNotified")) {
                req.putBoolean("LegacySyntheticPrisonerNotified", true); saveRequest(player, req);
                notify(player, "This older rescue referenced a disposable stand-in. Living World will not replace it with another fake person; the request has been retired without a cooldown.");
            }
            clearRequest(player); return;
        }
        if (captive == null) {
            if (!req.getBoolean("CaptiveWaitNotified")) {
                req.putBoolean("CaptiveWaitNotified", true); saveRequest(player, req);
                notify(player, "The real captive is currently unloaded. The mission is waiting for that exact UUID rather than spawning a duplicate.");
            }
            return;
        }
        req.remove("CaptiveWaitNotified");

        BlockPos home = ensureFactionLandSite(player, level, req, ally, "SourceSite");
        if (home == null) return;

        if (req.getBoolean("RescueFreed")) {
            captive.setCaptive(false);
            captive.setStoryRole(AmbientFighterEntity.STORY_ALLY);
            FactionRequestMissionManager.assign(player, req, captive, FactionRequestMissionManager.SIDE_NEUTRAL,
                    FactionRequestMissionManager.ROLE_OPERATIVE);
            if (!captive.isAlive() || captive.isDefeated()) {
                store.markDead(level, p, now, "killed during the rescue escape");
                FactionManager.adjustReputation(player, ally, -3);
                notify(player, "Rescue failed: " + captive.getFighterName() + " died during the escape. There is no replacement captive.");
                setCooldown(player, ally, now + 30000L); clearRequest(player); return;
            }
            double dp = player.distanceTo(captive);
            if (dp > 4.0D && dp < 110.0D && (now % 20L == 0L || captive.getNavigation().isDone()))
                captive.getNavigation().moveTo(player, 1.08D);
            boolean playerHome = distanceSqTo(home, player) <= 70.0D * 70.0D;
            double hx = captive.getX() - (home.getX() + 0.5D), hz = captive.getZ() - (home.getZ() + 0.5D);
            boolean captiveHome = hx * hx + hz * hz <= 70.0D * 70.0D;
            if (!playerHome || !captiveHome || dp > 24.0D) { saveRequest(player, req); return; }

            store.markRescued(level, p, now);
            int reward = rewardRep(req, 22);
            FactionManager.adjustReputation(player, ally, reward);
            FactionManager.propagatePlayerFactionConsequence(player, ally, reward);
            FactionWorldData.get(level).adjustMomentum(ally, 0.025F);
            setCooldown(player, ally, now + 48000L + player.getRandom().nextInt(48001));
            FighterMemoryManager.rememberRescue(player, captive);
            captive.speak(FactionRequestDialogue.success("RESCUE", now ^ captive.getUUID().getMostSignificantBits()), 82);
            finishSuccess(player, req, reward,
                    captive.getFighterName() + " made it home alive and the exact rescued resident returned to faction custody.",
                    "The rescued resident returns to " + ally.name() + " manpower and the faction gains momentum."); return;
        }

        captive.setCaptive(true);
        FactionRequestMissionManager.assign(player, req, captive, FactionRequestMissionManager.SIDE_NEUTRAL,
                FactionRequestMissionManager.ROLE_CAPTIVE);
        BlockPos anchor = captive.blockPosition();
        List<AmbientFighterEntity> guards = FactionRequestMissionManager.ensureRoster(player, level, req, captor,
                "RescueGuards", 4, anchor, FactionRequestMissionManager.SIDE_ENEMY,
                FactionRequestMissionManager.ROLE_COMBAT, f -> !f.isNonCombatant());
        if (!mandatoryRosterReady(player, level, req, captor, "RescueGuards", captive.blockPosition())) return;
        FactionRequestMissionManager.lockRoster(req, "RescueGuards");
        if (!req.getBoolean("Started")) {
            req.putBoolean("Started", true); req.putBoolean("DebugImmediate", false); saveRequest(player, req);
            captive.speak(FactionRequestDialogue.start("RESCUE", now ^ captive.getUUID().getLeastSignificantBits()), 76);
            AmbientFighterEntity voice = guards.isEmpty() ? null : guards.get(0);
            if (voice != null) voice.speak(FactionRequestDialogue.pressure("RESCUE", now), 68);
            notify(player, "The captive and guards are all real residents. Break the fixed guard roster, then physically escort " + captive.getFighterName() + " home.");
            return;
        }
        FactionRequestMissionManager.applyMoraleBreak(level, req, captor, "RescueGuards");
        if (!FactionRequestMissionManager.rosterNeutralized(level, req, captor, "RescueGuards")) { saveRequest(player, req); return; }

        captive.setCaptive(false);
        captive.setStoryRole(AmbientFighterEntity.STORY_ALLY);
        FactionRequestMissionManager.assign(player, req, captive, FactionRequestMissionManager.SIDE_NEUTRAL,
                FactionRequestMissionManager.ROLE_OPERATIVE);
        req.putBoolean("RescueFreed", true); resetPresence(req);
        captive.speak(FactionRequestDialogue.pressure("RESCUE", now ^ 0x4A71L), 76);
        objectiveToast(player, "Captive freed — escort " + captive.getFighterName() + " back to " + ally.name() + ".");
        notify(player, "The guards are broken. The mission is not over: get the same freed resident back to the land-safe faction handoff.");
        saveRequest(player, req);
    }

    private static BlockPos patrolRendezvous(CompoundTag req) {
        if (req == null || !req.contains("PatrolMeetX")) return null;
        return new BlockPos(req.getInt("PatrolMeetX"), req.getInt("PatrolMeetY"), req.getInt("PatrolMeetZ"));
    }

    /** Commit a named real leader immediately on acceptance so Patrol can never begin as an empty marker. */
    private static boolean preparePatrolAcceptance(ServerPlayer player, ServerLevel level, CompoundTag req, WorldFaction faction) {
        if (player == null || level == null || req == null || faction == null) return false;
        BlockPos meet = AmbientFighterSpawner.findSafeGroundAround(level, player.blockPosition(), player.getRandom(), 18, 38, 40);
        if (meet == null || !isPracticalQuestLand(level, meet)) {
            meet = AmbientFighterSpawner.findSafeGroundAround(level, player.blockPosition(), player.getRandom(), 8, 24, 32);
        }
        if (meet == null || !isPracticalQuestLand(level, meet)) return false;
        req.putInt("PatrolMeetX", meet.getX()); req.putInt("PatrolMeetY", meet.getY()); req.putInt("PatrolMeetZ", meet.getZ());
        req.putDouble("PatrolX", meet.getX() + 0.5D); req.putInt("PatrolY", meet.getY()); req.putDouble("PatrolZ", meet.getZ() + 0.5D);

        int patrolTeamSize = "RECOVERY".equals(req.getString("Type")) ? 3 : 4;
        // R41: Patrol is supposed to feel like a real route, not four short hops that a flying
        // player clears in a minute. Keep the established real roster size, but give it more and
        // longer route legs. Recovery remains a little shorter/slower by design.
        int routePoints = "RECOVERY".equals(req.getString("Type")) ? 4 : 5;
        ListTag route = new ListTag();
        BlockPos cursor = meet;
        for (int i = 0; i < routePoints; i++) {
            BlockPos next = AmbientFighterSpawner.findSafeGroundAround(level, cursor, player.getRandom(), 72, 128, 108);
            if (next == null || !isPracticalQuestLand(level, next))
                next = AmbientFighterSpawner.findSafeGroundAround(level, meet, player.getRandom(), 58, 116, 108);
            if (next == null || !isPracticalQuestLand(level, next)) return false;
            CompoundTag point = new CompoundTag(); point.putInt("X", next.getX()); point.putInt("Y", next.getY()); point.putInt("Z", next.getZ());
            route.add(point); cursor = next;
        }
        req.put("PatrolRoute", route); req.putInt("PatrolLeg", 0);
        req.putInt("PatrolCheckpointHold", 0);
        req.putInt("PatrolCheckpointNeed", "RECOVERY".equals(req.getString("Type")) ? 120 : 180);

        // ensureRoster wakes at most one unloaded resident per call; acceptance is a bounded one-time
        // operation, so make a few attempts now instead of asking the player to stare at an empty marker.
        for (int attempt = 0; attempt < patrolTeamSize + 1 && FactionRequestMissionManager.rosterSize(req, "Patrol") < patrolTeamSize; attempt++) {
            FactionRequestMissionManager.ensureRoster(player, level, req, faction, "Patrol", patrolTeamSize, meet,
                    FactionRequestMissionManager.SIDE_ALLY, FactionRequestMissionManager.ROLE_PATROL,
                    f -> !f.isNonCombatant() && !f.isCaptive() && !f.isDefeated());
        }
        List<AmbientFighterEntity> assembled = FactionRequestMissionManager.loadedActiveRoster(level, req, "Patrol");
        if (assembled.isEmpty()) return false;
        AmbientFighterEntity leader = assembled.get(0);
        req.putUUID("PatrolLeader", leader.getUUID());
        req.putString("PatrolLeaderName", leader.getFighterName());
        return true;
    }

    private static void tickPatrol(ServerPlayer player, ServerLevel level, long now, CompoundTag req, WorldFaction faction) {
        boolean recovery = "RECOVERY".equals(req.getString("Type"));
        FactionWorldData data = FactionWorldData.get(level);
        BlockPos sourceSite = ensureFactionLandSite(player, level, req, faction, "SourceSite");
        if (sourceSite == null) return;

        if (!req.contains("PatrolRoute", Tag.TAG_LIST) || patrolRendezvous(req) == null) {
            if (!preparePatrolAcceptance(player, level, req, faction)) {
                notify(player, "Patrol cannot assemble: no available patrol leader could reach a safe rendezvous. The request has been released without a cooldown.");
                clearRequest(player);
                return;
            }
            saveRequest(player, req);
        }

        ListTag route = req.getList("PatrolRoute", Tag.TAG_COMPOUND); if (route.isEmpty()) return;
        int leg = Math.max(0, Math.min(route.size() - 1, req.getInt("PatrolLeg")));
        CompoundTag point = route.getCompound(leg); BlockPos waypoint = new BlockPos(point.getInt("X"), point.getInt("Y"), point.getInt("Z"));
        BlockPos rendezvous = patrolRendezvous(req);
        BlockPos rosterAnchor = req.getBoolean("Started") ? waypoint : rendezvous;
        // The faction commits the real patrol when the request is accepted, so the player can follow an exact named
        // leader immediately instead of reaching an empty waypoint and waiting for somebody to exist.
        List<AmbientFighterEntity> loaded = FactionRequestMissionManager.ensureRoster(player, level, req, faction,
                "Patrol", recovery ? 3 : 4, rosterAnchor, FactionRequestMissionManager.SIDE_ALLY,
                FactionRequestMissionManager.ROLE_PATROL,
                f -> !f.isNonCombatant() && !f.isCaptive() && !f.isDefeated());
        if (!mandatoryRosterReady(player, level, req, faction, "Patrol", rosterAnchor)) return;
        FactionRequestMissionManager.lockRoster(req, "Patrol");
        List<AmbientFighterEntity> patrol = FactionRequestMissionManager.loadedActiveRoster(level, req, "Patrol");
        if (patrol.isEmpty()) {
            if (FactionRequestMissionManager.rosterNeutralized(level, req, faction, "Patrol")) {
                FactionManager.adjustReputation(player, faction, recovery ? -1 : -2);
                data.adjustMomentum(faction, -0.012F);
                data.addHistory(faction, now, requestTitle(req) + " failed when its fixed patrol roster was lost, yielded or withdrew. No replacements were committed.");
                notify(player, "Patrol failed: the committed patrol can no longer continue. No replacement patrol will take their place.");
                setCooldown(player, faction, now + 26000L); clearRequest(player);
            }
            return;
        }
        AmbientFighterEntity leader = resolvePatrolLeader(player, level, req, faction, patrol);
        // A temporarily unloaded committed leader is not silently replaced by whichever roster
        // member happens to be first in the currently loaded subset. Wait for that exact identity;
        // only a conclusively neutralized leader receives a visible field handoff.
        if (leader == null) { saveRequest(player, req); return; }

        if (!req.getBoolean("Started")) {
            double leaderToPoint = leader.distanceToSqr(rendezvous.getX() + 0.5D, rendezvous.getY(), rendezvous.getZ() + 0.5D);
            if (leaderToPoint > 18.0D * 18.0D)
                FactionRequestMissionManager.navigateMissionActor(leader, rendezvous, 1.10D, now);
            // The tracker marks the committed leader, so meeting that leader is the complete start condition.
            // Do not also gate the mission on an unshown rendezvous radius: the leader can be displaced by real
            // terrain/pathing while the player is correctly standing at the marked NPC.
            if (player.distanceToSqr(leader) > 14.0D * 14.0D) {
                saveRequest(player, req); return;
            }
            req.putBoolean("Started", true);
            WorldFaction threat = patrolThreat(level, faction);
            if (threat != null && player.getRandom().nextFloat() < (recovery ? 0.34F : 0.58F)) {
                req.putString("PatrolAmbushFaction", threat.id());
                req.putInt("PatrolAmbushLeg", Math.min(route.size() - 1, 1 + player.getRandom().nextInt(Math.max(1, route.size() - 1))));
            }
            leader.speak(FactionMissionFlavor.patrolJoin(recovery, now ^ leader.getUUID().getLeastSignificantBits()), 82);
            notify(player, "Patrol started with " + FactionRequestMissionManager.rosterSize(req, "Patrol") + " " + faction.name() + " fighters. Follow the marked leader; each route checkpoint advances only when you and that same patrol arrive together.");
            saveRequest(player, req); return;
        }

        // A genuine route contact is discovered from already-loaded rival residents. It is not spawned,
        // teleported in, or selected from an unloaded roster merely to make the quest exciting.
        if (tickNaturalPatrolContact(player, level, now, req, faction, patrol, leader)) return;

        // A pre-committed ambush may be waiting at a later checkpoint; keep travelling until somebody is
        // actually in combat range instead of freezing the patrol the instant that distant roster is reserved.
        boolean fighting = patrol.stream().anyMatch(f -> f.getTarget() != null && f.getTarget().isAlive());
        boolean flightLeg = false;
        if (!fighting) {
            flightLeg = patrolFlightLeg(player, req, patrol, leader, waypoint);
            if (flightLeg) movePatrolByAir(req, patrol, leader, waypoint);
            else movePatrolOnFoot(patrol, leader, waypoint, now);
        }

        if (!req.getBoolean("PatrolAmbushTriggered") && !req.getString("PatrolAmbushFaction").isBlank()
                && !req.getBoolean("PatrolContactActive") && leg >= req.getInt("PatrolAmbushLeg")) {
            WorldFaction threat = data.byId(req.getString("PatrolAmbushFaction"));
            if (threat != null) {
                List<AmbientFighterEntity> ambush = FactionRequestMissionManager.ensureRoster(player, level, req, threat,
                        "PatrolAmbush", recovery ? 2 : 4, waypoint, FactionRequestMissionManager.SIDE_ENEMY,
                        FactionRequestMissionManager.ROLE_COMBAT, f -> !f.isNonCombatant());
                if (FactionRequestMissionManager.rosterSize(req, "PatrolAmbush") > 0) {
                    FactionRequestMissionManager.lockRoster(req, "PatrolAmbush"); req.putBoolean("PatrolAmbushTriggered", true); req.putBoolean("PatrolAmbushResolved", false);
                    leader.speak(FactionMissionFlavor.patrolAmbush(now), 78);
                    notify(player, threat.name() + " committed an ambush team. Defend the patrol; no reinforcements will appear.");
                    saveRequest(player, req); return;
                }
            }
        }

        if (req.getBoolean("PatrolAmbushTriggered") && !req.getBoolean("PatrolAmbushResolved")) {
            WorldFaction threat = data.byId(req.getString("PatrolAmbushFaction"));
            if (threat != null) FactionRequestMissionManager.applyMoraleBreak(level, req, threat, "PatrolAmbush");
            FactionRequestMissionManager.applyMoraleBreak(level, req, faction, "Patrol");
            boolean enemyBroken = threat == null || FactionRequestMissionManager.rosterNeutralized(level, req, threat, "PatrolAmbush");
            boolean patrolBroken = FactionRequestMissionManager.rosterNeutralized(level, req, faction, "Patrol");
            if (patrolBroken && !enemyBroken) {
                FactionManager.adjustReputation(player, faction, recovery ? -1 : -3); data.adjustMomentum(faction, -0.018F);
                data.addHistory(faction, now, requestTitle(req) + " ended when the real patrol broke under an ambush.");
                notify(player, "Patrol failed: your faction's committed roster broke first. The mission will not refill the team.");
                setCooldown(player, faction, now + 32000L); clearRequest(player); return;
            }
            if (!enemyBroken) { saveRequest(player, req); return; }
            if (patrolBroken) {
                notify(player, "The ambush was stopped, but the patrol itself can no longer continue. The operation ends without replacement fighters.");
                data.addHistory(faction, now, requestTitle(req) + " survived an ambush tactically but lost its ability to continue the route.");
                setCooldown(player, faction, now + 28000L); clearRequest(player); return;
            }
            req.putBoolean("PatrolAmbushResolved", true);
            if (threat != null) { data.adjustMomentum(faction, recovery ? 0.008F : 0.012F); data.adjustMomentum(threat, -0.010F); data.adjustRelation(faction, threat, -2, now, null); data.addHistory(faction, now, "A real " + threat.name() + " ambush roster was broken during " + requestTitle(req) + "."); }
            leader.speak(FactionMissionFlavor.patrolResume(now ^ 0x5A17L), 76);
            notify(player, "The ambush team has yielded, withdrawn or fallen. The surviving patrol resumes its route.");
        }

        boolean leaderAtWaypoint = leader.distanceToSqr(waypoint.getX() + 0.5D, waypoint.getY(), waypoint.getZ() + 0.5D) <= 18.0D * 18.0D;
        boolean playerWithPatrol = player.distanceToSqr(leader) <= 38.0D * 38.0D;
        if (!leaderAtWaypoint || !playerWithPatrol) {
            req.putInt("PatrolCheckpointHold", Math.max(0, req.getInt("PatrolCheckpointHold") - 10));
            saveRequest(player, req); return;
        }
        // Arriving is not the whole checkpoint. The patrol briefly stops, observes the area and
        // regroups before the next leg. This keeps fast flight from collapsing an entire patrol
        // into a chain of instant radius triggers.
        int holdNeed = Math.max(100, req.getInt("PatrolCheckpointNeed"));
        int hold = Math.min(holdNeed, req.getInt("PatrolCheckpointHold") + 20);
        req.putInt("PatrolCheckpointHold", hold);
        if (hold < holdNeed) {
            settlePatrolFlight(patrol);
            if (hold == 20) leader.speak(FactionMissionFlavor.patrolTravel(now ^ (leg * 131L + 0x41L)), 56);
            saveRequest(player, req); return;
        }
        req.putInt("PatrolCheckpointHold", 0);
        if (leg + 1 < route.size()) {
            settlePatrolFlight(patrol);
            req.remove("PatrolLegFlight"); req.putBoolean("PatrolAirborne", false);
            req.putInt("PatrolLeg", leg + 1); CompoundTag next = route.getCompound(leg + 1);
            req.putDouble("PatrolX", next.getInt("X") + 0.5D); req.putInt("PatrolY", next.getInt("Y")); req.putDouble("PatrolZ", next.getInt("Z") + 0.5D);
            leader.speak(FactionRequestDialogue.start(req.getString("Type"), now ^ (leg * 7919L)), 64);
            objectiveToast(player, "Patrol checkpoint " + (leg + 1) + " / " + route.size() + " complete — move with the surviving patrol."); saveRequest(player, req); return;
        }
        settlePatrolFlight(patrol);
        req.putBoolean("PatrolAirborne", false);
        int reward = rewardRep(req, recovery ? 11 : 8); FactionManager.adjustReputation(player, faction, reward); FactionManager.propagatePlayerFactionConsequence(player, faction, reward);
        data.addHistory(faction, now, recovery ? "A fixed resident recovery patrol completed its route with outside support." : "A fixed resident patrol completed its full route with outside support.");
        setCooldown(player, faction, now + 30000L + player.getRandom().nextInt(30001)); leader.speak(FactionMissionFlavor.patrolFinish(req.getBoolean("PatrolAmbushTriggered"), now), 84);
        finishSuccess(player, req, reward,
                faction.name() + " patrol completed every route checkpoint with the surviving real patrol roster.",
                req.getBoolean("PatrolAmbushTriggered") ? "The ambush outcome and patrol survival are recorded in faction history; surviving members return to normal life."
                        : "The full patrol route is recorded in faction history; surviving members return to normal life.");
    }

    /** Returns true when this patrol tick is consumed by a natural contact or a contact-caused failure. */
    private static boolean tickNaturalPatrolContact(ServerPlayer player, ServerLevel level, long now, CompoundTag req,
                                                    WorldFaction faction, List<AmbientFighterEntity> patrol,
                                                    AmbientFighterEntity leader) {
        FactionWorldData data = FactionWorldData.get(level);

        if (!req.getBoolean("PatrolContactSeen") && player.distanceToSqr(leader) <= 48.0D * 48.0D) {
            List<AmbientFighterEntity> nearby = level.getEntitiesOfClass(AmbientFighterEntity.class,
                    leader.getBoundingBox().inflate(30.0D, 14.0D, 30.0D), candidate -> {
                        if (candidate == leader || !candidate.isAlive() || !candidate.isFactionMember()
                                || candidate.isNonCombatant() || candidate.isDefeated() || candidate.isCaptive()
                                || faction.id().equals(candidate.getFactionId()) || FactionRequestMissionManager.isAssigned(candidate)) return false;
                        WorldFaction other = data.byId(candidate.getFactionId());
                        return other != null && other.realm() == faction.realm()
                                && FactionRequestMissionManager.isRealResident(level, other, candidate)
                                && FactionManager.relation(level, faction, other).rivalry();
                    });
            nearby.sort(java.util.Comparator.comparingDouble(leader::distanceToSqr));
            if (!nearby.isEmpty()) {
                AmbientFighterEntity first = nearby.get(0);
                WorldFaction contactFaction = data.byId(first.getFactionId());
                int committed = 0;
                if (contactFaction != null) {
                    for (AmbientFighterEntity candidate : nearby) {
                        if (committed >= 3 || !contactFaction.id().equals(candidate.getFactionId())) continue;
                        if (FactionRequestMissionManager.commitLoadedResident(player, req, candidate, "PatrolContact",
                                FactionRequestMissionManager.SIDE_ENEMY, FactionRequestMissionManager.ROLE_COMBAT)) committed++;
                    }
                }
                if (committed > 0) {
                    FactionRequestMissionManager.lockRoster(req, "PatrolContact");
                    req.putBoolean("PatrolContactSeen", true);
                    req.putBoolean("PatrolContactActive", true);
                    req.putBoolean("PatrolContactResolved", false);
                    req.putString("PatrolContactFaction", contactFaction.id());
                    leader.speak(FactionRequestDialogue.pressure("PATROL", now ^ first.getUUID().getLeastSignificantBits()), 72);
                    notify(player, "Contact! " + contactFaction.name() + " fighters were encountered on the route. "
                            + committed + " rival fighter" + (committed == 1 ? " is" : "s are")
                            + " engaging your patrol. No reinforcements will appear.");
                    objectiveToast(player, "PATROL CONTACT — defend the patrol and stay with its surviving members");
                    saveRequest(player, req);
                }
            }
        }

        if (!req.getBoolean("PatrolContactActive") || req.getBoolean("PatrolContactResolved")) return false;
        WorldFaction enemyFaction = data.byId(req.getString("PatrolContactFaction"));
        if (enemyFaction != null) FactionRequestMissionManager.applyMoraleBreak(level, req, enemyFaction, "PatrolContact");
        FactionRequestMissionManager.applyMoraleBreak(level, req, faction, "Patrol");
        boolean enemyBroken = enemyFaction == null || FactionRequestMissionManager.rosterNeutralized(level, req, enemyFaction, "PatrolContact");
        boolean patrolBroken = FactionRequestMissionManager.rosterNeutralized(level, req, faction, "Patrol");
        if (patrolBroken && !enemyBroken) {
            FactionManager.adjustReputation(player, faction, -3);
            data.adjustMomentum(faction, -0.014F);
            data.addHistory(faction, now, requestTitle(req) + " failed after a genuine route contact broke the committed patrol.");
            notify(player, "Patrol failed: the patrol broke during route contact. No replacement members will appear.");
            setCooldown(player, faction, now + 32000L);
            clearRequest(player);
            return true;
        }
        if (!enemyBroken) { saveRequest(player, req); return true; }
        if (patrolBroken) {
            data.addHistory(faction, now, requestTitle(req) + " survived a route contact but had no viable patrol left to continue.");
            notify(player, "The rival contact is broken, but your patrol cannot continue. The operation ends without replacement fighters.");
            setCooldown(player, faction, now + 28000L);
            clearRequest(player);
            return true;
        }
        req.putBoolean("PatrolContactResolved", true);
        req.putBoolean("PatrolContactActive", false);
        if (enemyFaction != null) {
            data.adjustMomentum(faction, 0.006F);
            data.addHistory(faction, now, "A real " + enemyFaction.name() + " route contact was broken during " + requestTitle(req) + ".");
        }
        leader.speak(FactionMissionFlavor.patrolResume(now ^ 0x31A7L), 72);
        notify(player, "Route contact resolved. The surviving patrol reforms and continues; the rival residents are not replaced.");
        saveRequest(player, req);
        return false;
    }

    private static boolean patrolFlightLeg(ServerPlayer player, CompoundTag req, List<AmbientFighterEntity> patrol,
                                           AmbientFighterEntity leader, BlockPos waypoint) {
        if (!req.contains("PatrolLegFlight")) {
            boolean playerFlying = com.dragonminez.common.stats.StatsProvider.get(com.dragonminez.common.stats.StatsCapability.INSTANCE, player)
                    .map(data -> {
                        var fly = data.getSkills().getSkill("fly");
                        return fly != null && fly.isActive();
                    }).orElse(false);
            boolean capable = !patrol.isEmpty() && patrol.stream().allMatch(f -> f.hasFlightUnlocked() && !f.isInWaterOrBubble());
            boolean worthwhile = leader.distanceToSqr(waypoint.getX() + 0.5D, waypoint.getY(), waypoint.getZ() + 0.5D) > 28.0D * 28.0D;
            boolean fly = playerFlying && capable && worthwhile;
            req.putBoolean("PatrolLegFlight", fly);
            req.putBoolean("PatrolAirborne", fly);
            if (fly) notify(player, "The patrol is flight-capable and you started this leg airborne. They are taking an aerial route; stay near the leader until the next checkpoint.");
        }
        return req.getBoolean("PatrolLegFlight");
    }

    private static void movePatrolByAir(CompoundTag req, List<AmbientFighterEntity> patrol, AmbientFighterEntity leader, BlockPos waypoint) {
        Vec3 destination = new Vec3(waypoint.getX() + 0.5D, waypoint.getY() + 3.5D, waypoint.getZ() + 0.5D);
        leader.getNavigation().stop();
        leader.setCanFly(true); leader.setFlying(true); leader.setNoGravity(true);
        leader.setFlyingFast(leader.position().distanceToSqr(destination) > 34.0D * 34.0D);
        leader.steerAmbientFlightToward(destination, 0.72D);
        for (int i = 1; i < patrol.size(); i++) {
            AmbientFighterEntity member = patrol.get(i);
            member.getNavigation().stop();
            member.setCanFly(true); member.setFlying(true); member.setNoGravity(true);
            member.setFlyingFast(member.distanceToSqr(leader) > 20.0D * 20.0D);
            double side = (i % 2 == 0 ? 1.0D : -1.0D) * (1.8D + (i / 2) * 1.2D);
            Vec3 formation = new Vec3(leader.getX() + side, leader.getY() - 0.3D, leader.getZ() - 2.2D - i * 0.5D);
            member.steerAmbientFlightToward(formation, 0.76D);
        }
        req.putBoolean("PatrolAirborne", true);
    }

    private static void movePatrolOnFoot(List<AmbientFighterEntity> patrol, AmbientFighterEntity leader, BlockPos waypoint, long now) {
        if (leader.distanceToSqr(waypoint.getX() + 0.5D, waypoint.getY(), waypoint.getZ() + 0.5D) > 5.0D * 5.0D)
            FactionRequestMissionManager.navigateMissionActor(leader, waypoint, 1.05D, now);
        for (int i = 1; i < patrol.size(); i++) {
            AmbientFighterEntity member = patrol.get(i);
            // Keep feeding the mission flight controller while a follower is already airborne or
            // over water. The old 9-block formation shortcut could stop steering an auto-flying
            // member as soon as it briefly caught up, leaving it hovering over the same water block.
            if (member.distanceToSqr(leader) > 9.0D * 9.0D || member.isFlying() || member.isInWaterOrBubble())
                FactionRequestMissionManager.navigateMissionActor(member, leader.position(), 1.08D, now);
        }
    }

    /**
     * Keeps the committed Patrol marker/leader tied to one real resident. Loaded-list order is not
     * identity: after combat/chunk changes a different roster member can become index zero. We only
     * transfer leadership when the saved leader is conclusively unable to continue, and that handoff
     * is visible to the player instead of silently changing the protected/marked NPC.
     */
    private static AmbientFighterEntity resolvePatrolLeader(ServerPlayer player, ServerLevel level, CompoundTag req,
                                                             WorldFaction faction, List<AmbientFighterEntity> patrol) {
        if (req == null || level == null || patrol == null || patrol.isEmpty()) return null;
        if (!req.hasUUID("PatrolLeader")) {
            AmbientFighterEntity first = patrol.get(0);
            req.putUUID("PatrolLeader", first.getUUID());
            req.putString("PatrolLeaderName", first.getFighterName());
            return first;
        }

        UUID committedId = req.getUUID("PatrolLeader");
        for (AmbientFighterEntity member : patrol) {
            if (committedId.equals(member.getUUID())) return member;
        }

        boolean neutralized = false;
        if (level.getEntity(committedId) instanceof AmbientFighterEntity original) {
            neutralized = !original.isAlive() || original.isDefeated() || original.isCaptive()
                    || FactionRequestMissionManager.isYielded(original) || FactionRequestMissionManager.isRetreated(original);
        } else if (faction != null) {
            neutralized = FactionRequestMissionManager.residentFallenOrDeparted(level, faction, committedId);
        }
        if (!neutralized) return null; // merely unloaded: preserve the exact committed identity

        AmbientFighterEntity replacement = patrol.get(0);
        String oldName = req.getString("PatrolLeaderName");
        req.putUUID("PatrolLeader", replacement.getUUID());
        req.putString("PatrolLeaderName", replacement.getFighterName());
        if (player != null && !replacement.getFighterName().equals(oldName)) {
            notify(player, (oldName.isBlank() ? "The patrol leader" : oldName)
                    + " can no longer lead. " + replacement.getFighterName() + " is taking point.");
        }
        return replacement;
    }

    private static void settlePatrolFlight(List<AmbientFighterEntity> patrol) {
        for (AmbientFighterEntity fighter : patrol) {
            fighter.setFlyingFast(false);
            fighter.setFlying(false);
            fighter.setNoGravity(false);
        }
    }

    private static WorldFaction patrolThreat(ServerLevel level, WorldFaction faction) {
        FactionWorldData data = FactionWorldData.get(level);
        return data.activeFactions().stream()
                .filter(other -> other != null && !other.id().equals(faction.id()) && other.realm() == faction.realm())
                .filter(other -> FactionManager.relation(level, faction, other).rivalry())
                .max(java.util.Comparator.comparingInt(data::fighterPopulation))
                .orElse(null);
    }

    private static void clearSupplyReceiverIdentity(CompoundTag req) {
        if (req == null) return;
        req.remove("SupplyReceiverEntity"); req.remove("SupplyReceiverName"); req.remove("SupplyReceiverRole");
        req.remove("SupplyReceiverX"); req.remove("SupplyReceiverY"); req.remove("SupplyReceiverZ");
        req.remove("SourceSiteFor"); req.remove("SourceSiteX"); req.remove("SourceSiteY"); req.remove("SourceSiteZ");
        req.remove("MissionStartSpoken");
    }

    private static FactionWorldData.ResidentRecord supplyReceiverRecord(CompoundTag req, WorldFaction faction, ServerLevel level) {
        if (req == null || faction == null || level == null || !req.hasUUID("SupplyReceiverEntity")) return null;
        FactionWorldData.ResidentRecord record = FactionRequestMissionManager.residentRecord(level, faction, req.getUUID("SupplyReceiverEntity"));
        return record == null || record.fallen() || record.departed() ? null : record;
    }

    /** Bind a named persistent receiver without loading or creating any entity/chunk. Safe for request-board generation. */
    private static boolean bindSupplyReceiverIdentity(CompoundTag holder, WorldFaction faction, ServerLevel level) {
        if (holder == null || faction == null || level == null) return false;
        FactionWorldData.ResidentRecord existing = supplyReceiverRecord(holder, faction, level);
        if (existing != null) {
            AmbientFighterEntity loadedExisting = FactionRequestMissionManager.loadedResident(level, faction, existing.entityId());
            if (loadedExisting == null || (loadedExisting.isAlive() && !loadedExisting.isCaptive() && !loadedExisting.isDefeated())) {
                holder.putString("SupplyReceiverName", loadedExisting != null ? loadedExisting.getFighterName() : existing.name());
                holder.putInt("SupplyReceiverRole", existing.role().id());
                BlockPos pos = loadedExisting != null ? loadedExisting.blockPosition() : new BlockPos(existing.x(), existing.y(), existing.z());
                holder.putInt("SupplyReceiverX", pos.getX()); holder.putInt("SupplyReceiverY", pos.getY()); holder.putInt("SupplyReceiverZ", pos.getZ());
                return true;
            }
            // The exact resident loaded in an unusable state (captured/defeated/dead). Pick somebody else now.
            clearSupplyReceiverIdentity(holder);
        }

        java.util.ArrayList<FactionWorldData.ResidentRecord> candidates = new java.util.ArrayList<>(FactionWorldData.get(level).residents(faction));
        candidates.removeIf(r -> r == null || r.fallen() || r.departed());
        candidates.sort(java.util.Comparator
                .comparingInt((FactionWorldData.ResidentRecord r) -> {
                    AmbientFighterEntity loaded = FactionRequestMissionManager.loadedResident(level, faction, r.entityId());
                    return loaded != null && !loaded.isCaptive() && !loaded.isDefeated() ? 0 : 1;
                })
                .thenComparing((FactionWorldData.ResidentRecord r) -> r.nonCombatant() ? 0 : 1)
                .thenComparing(java.util.Comparator.comparingLong(FactionWorldData.ResidentRecord::lastSeen).reversed()));
        if (candidates.isEmpty()) return false;

        FactionWorldData.ResidentRecord chosen = candidates.get(0);
        AmbientFighterEntity loaded = FactionRequestMissionManager.loadedResident(level, faction, chosen.entityId());
        if (loaded != null && (loaded.isCaptive() || loaded.isDefeated())) {
            chosen = candidates.stream().filter(r -> {
                AmbientFighterEntity f = FactionRequestMissionManager.loadedResident(level, faction, r.entityId());
                return f == null || (!f.isCaptive() && !f.isDefeated());
            }).findFirst().orElse(null);
            if (chosen == null) return false;
            loaded = FactionRequestMissionManager.loadedResident(level, faction, chosen.entityId());
        }

        holder.putUUID("SupplyReceiverEntity", chosen.entityId());
        holder.putString("SupplyReceiverName", loaded != null ? loaded.getFighterName() : chosen.name());
        holder.putInt("SupplyReceiverRole", chosen.role().id());
        BlockPos pos = loaded != null ? loaded.blockPosition() : new BlockPos(chosen.x(), chosen.y(), chosen.z());
        holder.putInt("SupplyReceiverX", pos.getX()); holder.putInt("SupplyReceiverY", pos.getY()); holder.putInt("SupplyReceiverZ", pos.getZ());
        return true;
    }

    /**
     * Commit one exact persistent resident as the hand-in target. Supply receivers deliberately do NOT enter the
     * old mission-roster actor system: they keep their normal Living World AI/routine and the compass follows them.
     * Any R29/R30 SupplyReceiver roster is released here as an upgrade migration.
     */
    private static boolean commitSupplyReceiverBinding(ServerPlayer player, ServerLevel level, CompoundTag req, WorldFaction faction) {
        if (!bindSupplyReceiverIdentity(req, faction, level)) return false;
        if (FactionRequestMissionManager.rosterSize(req, "SupplyReceiver") > 0) {
            FactionRequestMissionManager.releaseRoster(level, req, "SupplyReceiver");
        }
        req.remove(FactionRequestMissionManager.ROSTER_PREFIX + "SupplyReceiver");
        req.remove(FactionRequestMissionManager.ROSTER_PREFIX + "SupplyReceiver.Locked");
        req.putString("SourceSiteFor", faction.id());
        req.putDouble("SourceSiteX", req.getInt("SupplyReceiverX") + 0.5D);
        req.putInt("SourceSiteY", req.getInt("SupplyReceiverY"));
        req.putDouble("SourceSiteZ", req.getInt("SupplyReceiverZ") + 0.5D);
        if (player != null) saveRequest(player, req);
        return true;
    }

    private static String supplyReceiverLabel(CompoundTag req, WorldFaction faction) {
        String name = req == null ? "" : req.getString("SupplyReceiverName");
        if (name.isBlank()) return faction == null ? "the marked faction receiver" : "the marked " + faction.name() + " receiver";
        return name + (faction == null ? "" : " of " + faction.name());
    }

    /** Supply work is handed to one exact persistent faction resident; the request never creates or suppresses a clerk. */
    private static void tickSupplyReceiver(ServerPlayer player, ServerLevel level, long now, CompoundTag req, WorldFaction faction) {
        UUID previous = req.hasUUID("SupplyReceiverEntity") ? req.getUUID("SupplyReceiverEntity") : null;
        FactionWorldData.ResidentRecord previousRecord = previous == null ? null
                : FactionRequestMissionManager.residentRecord(level, faction, previous);
        AmbientFighterEntity previousLoaded = previous == null ? null : FactionRequestMissionManager.loadedResident(level, faction, previous);
        boolean previousUnavailable = previous != null && (previousRecord == null || previousRecord.fallen() || previousRecord.departed()
                || (previousLoaded != null && (!previousLoaded.isAlive() || previousLoaded.isCaptive() || previousLoaded.isDefeated())));

        if (previousUnavailable) {
            String oldName = req.getString("SupplyReceiverName");
            clearSupplyReceiverIdentity(req);
            if (!commitSupplyReceiverBinding(player, level, req, faction)) {
                notify(player, "Supply request cancelled without a cooldown: " + faction.name()
                        + " no longer has an available faction member to receive the shipment.");
                clearRequest(player);
                return;
            }
            notify(player, (oldName.isBlank() ? "The original receiver" : oldName)
                    + " is no longer available. " + faction.name() + " reassigned the shipment to "
                    + req.getString("SupplyReceiverName") + ". The compass now tracks the new receiver.");
        } else if (!commitSupplyReceiverBinding(player, level, req, faction)) {
            notify(player, "Supply request cancelled without a cooldown: no available " + faction.name()
                    + " resident is available to receive the shipment.");
            clearRequest(player);
            return;
        }

        UUID receiverId = req.getUUID("SupplyReceiverEntity");
        AmbientFighterEntity receiver = FactionRequestMissionManager.loadedResident(level, faction, receiverId);
        BlockPos last = FactionRequestMissionManager.residentLastPos(level, faction, receiverId);
        if (receiver == null && last != null && distanceSqTo(last, player) <= 190.0D * 190.0D) {
            // Only recover the already-existing UUID when the player actually approaches its recorded area.
            receiver = FactionRequestMissionManager.materializeExistingResident(level, faction, receiverId);
        }

        if (receiver != null && (!receiver.isAlive() || receiver.isCaptive() || receiver.isDefeated())) {
            String oldName = req.getString("SupplyReceiverName");
            clearSupplyReceiverIdentity(req);
            if (!commitSupplyReceiverBinding(player, level, req, faction)) {
                notify(player, "Supply request cancelled without a cooldown: " + faction.name()
                        + " has nobody else available to receive the shipment.");
                clearRequest(player);
                return;
            }
            notify(player, oldName + " can no longer receive the shipment. New receiver: "
                    + req.getString("SupplyReceiverName") + ". The compass has updated.");
            return;
        }

        if (receiver != null) {
            req.putString("SupplyReceiverName", receiver.getFighterName());
            req.putInt("SupplyReceiverX", receiver.blockPosition().getX());
            req.putInt("SupplyReceiverY", receiver.blockPosition().getY());
            req.putInt("SupplyReceiverZ", receiver.blockPosition().getZ());
            req.putDouble("SourceSiteX", receiver.getX()); req.putInt("SourceSiteY", receiver.blockPosition().getY()); req.putDouble("SourceSiteZ", receiver.getZ());
        } else if (last != null) {
            req.putInt("SupplyReceiverX", last.getX()); req.putInt("SupplyReceiverY", last.getY()); req.putInt("SupplyReceiverZ", last.getZ());
            req.putDouble("SourceSiteX", last.getX() + 0.5D); req.putInt("SourceSiteY", last.getY()); req.putDouble("SourceSiteZ", last.getZ() + 0.5D);
        }

        req.putBoolean("Started", true);
        if (!req.getBoolean("MissionStartSpoken")) {
            req.putBoolean("MissionStartSpoken", true);
            notify(player, req.getString("SupplyReceiverName") + " is the receiver for " + faction.name()
                    + ". Gather the listed goods, follow the live compass, and RIGHT-CLICK "
                    + req.getString("SupplyReceiverName") + " to hand them over.");
        }
        saveRequest(player, req);
    }

    private static void tickTrainTarget(ServerPlayer player, ServerLevel level, long now, CompoundTag req, WorldFaction faction) {
        String type = req.getString("Type");
        boolean officerAssignment = "TRAIN_OFFICER".equals(type);
        BlockPos factionSite = ensureFactionLandSite(player, level, req, faction, "SourceSite");
        if (factionSite == null) return;
        int requested = "TRAINING".equals(type) ? 1 : Math.max(1, requiredCount(req, type));
        int availableRealTrainees = (int)Math.max(1L, activeResidentCount(FactionWorldData.get(level), faction, true));
        int required = Math.min(requested, availableRealTrainees);
        if (required != requested) req.putInt("Required", required);
        if (FactionRequestMissionManager.rosterSize(req, "TrainingTeam") > 0
                && FactionRequestMissionManager.fallenOrDepartedCount(level, req, faction, "TrainingTeam") > 0) {
            int lost = FactionRequestMissionManager.fallenOrDepartedCount(level, req, faction, "TrainingTeam");
            FactionManager.adjustReputation(player, faction, -2);
            FactionWorldData.get(level).addHistory(faction, now, requestTitle(req) + " ended early after " + lost
                    + " assigned trainee" + (lost == 1 ? " became" : "s became") + " unavailable. The roster was not refilled.");
            notify(player, "Training assignment failed: an assigned real trainee died or left before completing the drill. The fixed roster will not be replaced.");
            setCooldown(player, faction, now + 24000L); clearRequest(player); return;
        }
        java.util.function.Predicate<AmbientFighterEntity> preferred = officerAssignment
                ? f -> !f.isNonCombatant() && f.getFactionRole().id() >= FactionRole.ENFORCER.id()
                : f -> !f.isNonCombatant() && f.getFactionRole().id() <= FactionRole.MEMBER.id();
        List<AmbientFighterEntity> team = FactionRequestMissionManager.ensureRoster(player, level, req, faction,
                "TrainingTeam", required, factionSite, FactionRequestMissionManager.SIDE_ALLY,
                FactionRequestMissionManager.ROLE_TRAINEE, preferred);
        if (FactionRequestMissionManager.rosterSize(req, "TrainingTeam") < required) {
            // Rank preference is flavor, identity is not. Fill any missing slots only with other real resident combatants.
            team = FactionRequestMissionManager.ensureRoster(player, level, req, faction,
                    "TrainingTeam", required, factionSite, FactionRequestMissionManager.SIDE_ALLY,
                    FactionRequestMissionManager.ROLE_TRAINEE, f -> !f.isNonCombatant());
        }
        if (team.isEmpty() && distanceSqTo(factionSite, player) > 180.0D * 180.0D) return;
        if (!mandatoryRosterReady(player, level, req, faction, "TrainingTeam", factionSite)) return;
        FactionRequestMissionManager.lockRoster(req, "TrainingTeam");
        int assembled = FactionRequestMissionManager.rosterSize(req, "TrainingTeam");
        if (assembled <= 0) return;
        req.putInt("Required", assembled);
        req.remove("TrainingRosterWaitNotified");
        ListTag completed = req.getList("TrainingCompleted", Tag.TAG_STRING);
        AmbientFighterEntity trainee = null;
        for (AmbientFighterEntity candidate : FactionRequestMissionManager.loadedRoster(level, req, "TrainingTeam")) {
            boolean done = false;
            for (int i = 0; i < completed.size(); i++) if (candidate.getUUID().toString().equals(completed.getString(i))) { done = true; break; }
            if (!done && candidate.isAlive() && !candidate.isDefeated()) { trainee = candidate; break; }
        }
        if (trainee == null) {
            if (completed.size() >= FactionRequestMissionManager.rosterSize(req, "TrainingTeam")) {
                int reward = rewardRep(req, officerAssignment ? 12 : "TRAIN_RECRUIT".equals(type) ? 8 : 5);
                FactionManager.adjustReputation(player, faction, reward); FactionManager.propagatePlayerFactionConsequence(player, faction, reward);
                FactionWorldData.get(level).adjustMomentum(faction, officerAssignment ? 0.012F : 0.007F);
                FactionWorldData.get(level).addHistory(faction, now, "A fixed roster of " + completed.size()
                        + " real faction members completed " + requestTitle(req) + " with outside instruction and improved readiness.");
                AmbientFighterEntity speaker = FactionRequestMissionManager.firstLoaded(level, req, "TrainingTeam");
                if (speaker != null) speaker.speak(FactionRequestDialogue.success(type, now), 78);
                setCooldown(player, faction, now + (officerAssignment ? 42000L : 36000L));
                finishSuccess(player, req, reward,
                        faction.name() + " completed the training assignment with the same real trainees who began it.",
                        "The trained residents gain readiness/growth and the faction records the completed drill.");
            }
            return;
        }
        req.putUUID("TargetEntity", trainee.getUUID()); req.putBoolean("Started", true); req.putBoolean("DebugImmediate", false);
        if (!req.getBoolean("MissionStartSpoken")) {
            trainee.speak(FactionRequestDialogue.start(type, now ^ trainee.getUUID().getLeastSignificantBits()), 76);
            req.putBoolean("MissionStartSpoken", true);
            notify(player, faction.name() + " assigned a fixed roster of " + FactionRequestMissionManager.rosterSize(req, "TrainingTeam")
                    + " real members. Each person spars once; nobody is replaced if hurt or killed.");
        }
        saveRequest(player, req);
    }

    private static void tickRecon(ServerPlayer player, ServerLevel level, long now, CompoundTag req, WorldFaction faction) {
        FactionWorldData data = FactionWorldData.get(level); WorldFaction target = data.byId(req.getString("Target"));
        if (target == null || data.isExtinct(target)) { clearRequest(player); return; }
        BlockPos targetSite = ensureFactionLandSite(player, level, req, target, "TargetSite"); if (targetSite == null) return;
        if (!req.contains("ReconX") || !req.contains("ReconZ")) {
            BlockPos observation = AmbientFighterSpawner.findSafeGroundAround(level, targetSite, player.getRandom(), 12, 46, 72);
            if (observation == null || !isPracticalQuestLand(level, observation)) observation = targetSite;
            req.putDouble("ReconX", observation.getX() + 0.5D); req.putInt("ReconY", observation.getY()); req.putDouble("ReconZ", observation.getZ() + 0.5D);
            req.putInt("ReconSuspicion", 0); req.putInt("ReconCalm", 100); resetPresence(req); saveRequest(player, req);
            objectiveToast(player, "Recon point assigned — observe the marked faction members without being identified."); return;
        }
        BlockPos observation = new BlockPos((int)Math.floor(req.getDouble("ReconX")), req.getInt("ReconY"), (int)Math.floor(req.getDouble("ReconZ")));
        if (FactionRequestMissionManager.rosterSize(req, "ReconObservers") == 0 && distanceSqTo(observation, player) > 140.0D * 140.0D) return;
        FactionRequestMissionManager.ensureRoster(player, level, req, target, "ReconObservers", 4, observation,
                FactionRequestMissionManager.SIDE_ENEMY, FactionRequestMissionManager.ROLE_OBSERVER, f -> !f.isNonCombatant());
        if (!mandatoryRosterReady(player, level, req, target, "ReconObservers", observation)) return;
        FactionRequestMissionManager.lockRoster(req, "ReconObservers");
        List<AmbientFighterEntity> guards = FactionRequestMissionManager.loadedActiveRoster(level, req, "ReconObservers");
        boolean securityBroken = FactionRequestMissionManager.rosterNeutralized(level, req, target, "ReconObservers");
        if (guards.isEmpty() && !securityBroken) return; // same real observers are merely unloaded
        if (securityBroken && !req.getBoolean("ReconSecurityBrokenNotified")) {
            req.putBoolean("ReconSecurityBrokenNotified", true);
            notify(player, "The fixed observation roster has been neutralized. Recon can continue, but the violent outcome reduces its value; nobody replaces them.");
        }
        boolean visible = !securityBroken && guards.stream().anyMatch(g -> canCovertGuardSee(level, g, player,
                g.getFactionRole().id() >= FactionRole.ENFORCER.id() ? 48.0D : 40.0D));
        int suspicion = Math.max(0, req.getInt("ReconSuspicion"));
        suspicion = visible ? Math.min(100, suspicion + (player.isShiftKeyDown() ? 26 : 40)) : Math.max(0, suspicion - 45); req.putInt("ReconSuspicion", suspicion);
        if (suspicion >= 100 && !guards.isEmpty()) {
            if (!req.getBoolean("ReconDetected")) { notify(player, "The real patrol identified you. Break line of sight before continuing."); guards.get(0).speak(FactionRequestDialogue.pressure("RECON", now), 64); }
            req.putBoolean("ReconDetected", true); req.putInt("ReconCalm", 0);
            for (AmbientFighterEntity guard : guards) { guard.getPersistentData().putBoolean("LWRequestAlerted", true); guard.setStoryRole(AmbientFighterEntity.STORY_ENEMY); guard.setTarget(player); PeacekeeperManager.markNpcAggressor(player, guard); }
        } else if (req.getBoolean("ReconDetected")) {
            if (!visible) req.putInt("ReconCalm", req.getInt("ReconCalm") + 20); else req.putInt("ReconCalm", 0);
            if (req.getInt("ReconCalm") >= 120 || securityBroken) { req.putBoolean("ReconDetected", false); req.putInt("ReconSuspicion", 0); clearQuestAggression(guards, player); notify(player, "You shook the patrol. Resume observation."); }
        }
        double dx=player.getX()-req.getDouble("ReconX"), dz=player.getZ()-req.getDouble("ReconZ");
        if (dx*dx+dz*dz<=24.0D*24.0D && !req.getBoolean("ReconDetected")) req.putInt("Presence", req.getInt("Presence")+20);
        if (req.getInt("Presence") < requiredPresence(req, 1800)) { saveRequest(player, req); return; }
        int reward=Math.max(1,rewardRep(req,8)-req.getInt("ReconCasualties")); FactionManager.adjustReputation(player,faction,reward); FactionManager.propagatePlayerFactionConsequence(player,faction,reward);
        data.adjustMomentum(faction, req.getInt("ReconCasualties") == 0 ? 0.010F : 0.004F);
        data.addHistory(faction,now,"Reconnaissance observed a fixed roster of " + FactionRequestMissionManager.rosterSize(req,"ReconObservers") + " real " + target.name() + " residents; " + req.getInt("ReconCasualties") + " became casualties.");
        AmbientFighterEntity voice = FactionRequestMissionManager.firstLoaded(level, req, "ReconObservers"); if (voice != null) voice.speak(FactionRequestDialogue.success("RECON",now),70);
        setCooldown(player,faction,now+30000L+player.getRandom().nextInt(24001));
        finishSuccess(player, req, reward,
                faction.name() + " received the reconnaissance report after observing " + FactionRequestMissionManager.rosterSize(req,"ReconObservers") + " real residents.",
                req.getInt("ReconCasualties") == 0 ? "Clean reconnaissance improved faction momentum without casualties."
                        : req.getInt("ReconCasualties") + " real observer casualties are recorded; the reduced payout reflects the compromised mission.");
    }

    private static void tickProtectOfficer(ServerPlayer player, ServerLevel level, long now, CompoundTag req, WorldFaction faction) {
        FactionWorldData data = FactionWorldData.get(level); WorldFaction enemy = data.byId(req.getString("Target"));
        if (enemy == null) { clearRequest(player); return; }
        BlockPos sourceSite = ensureFactionLandSite(player, level, req, faction, "SourceSite"); if (sourceSite == null) return;
        UUID assigned = FactionRequestMissionManager.rosterId(req, "ProtectedOfficer", 0);
        if (assigned != null && FactionRequestMissionManager.residentFallenOrDeparted(level, faction, assigned)) {
            FactionManager.adjustReputation(player, faction, -3); data.adjustMomentum(faction, -0.018F);
            data.addHistory(faction, now, "Protection detail failed because its assigned real officer was killed or left before completion.");
            notify(player, "Protection failed: the assigned officer is no longer available. The mission will not substitute another person."); setCooldown(player, faction, now + 24000L); clearRequest(player); return;
        }
        AmbientFighterEntity officer = reserveRealMember(player, level, req, faction, "ProtectedOfficer", sourceSite,
                FactionRequestMissionManager.SIDE_ALLY, FactionRequestMissionManager.ROLE_PROTECTED,
                f -> !f.isNonCombatant() && f.getFactionRole().id() >= FactionRole.ENFORCER.id());
        if (officer == null && assigned == null) officer = reserveRealMember(player, level, req, faction, "ProtectedOfficer", sourceSite,
                FactionRequestMissionManager.SIDE_ALLY, FactionRequestMissionManager.ROLE_PROTECTED, f -> !f.isNonCombatant());
        if (officer == null) return;
        req.putUUID("TargetEntity", officer.getUUID()); req.putString("TargetName", officer.getFighterName());
        // Mark the exact officer immediately, but do not start the attack off-screen. The hostile roster only commits
        // when the player reaches the 120-block protection zone.
        if (!req.getBoolean("Started") && distanceSqTo(sourceSite, player) > 120.0D * 120.0D) { saveRequest(player, req); return; }
        FactionRequestMissionManager.ensureRoster(player, level, req, enemy, "ProtectAttackers", 4, sourceSite,
                FactionRequestMissionManager.SIDE_ENEMY, FactionRequestMissionManager.ROLE_COMBAT, f -> !f.isNonCombatant());
        if (!mandatoryRosterReady(player, level, req, enemy, "ProtectAttackers", sourceSite)) return;
        FactionRequestMissionManager.lockRoster(req,"ProtectedOfficer"); FactionRequestMissionManager.lockRoster(req,"ProtectAttackers");
        if (!req.getBoolean("Started")) { req.putBoolean("Started",true); officer.speak(FactionRequestDialogue.start("PROTECT",now),76); notify(player,"Protect officer " + officer.getFighterName() + ". The enemy force is fixed; break it before the officer is killed, defeated or forced to withdraw."); saveRequest(player,req); return; }
        if (!officer.isAlive() || officer.isDefeated() || officer.isCaptive() || FactionRequestMissionManager.isYielded(officer) || FactionRequestMissionManager.isRetreated(officer)) {
            FactionManager.adjustReputation(player,faction,-3); data.adjustMomentum(faction,-0.018F);
            data.addHistory(faction,now,"A protection detail failed when officer " + officer.getFighterName() + " was neutralized.");
            notify(player,"Protection failed: " + officer.getFighterName() + " was neutralized before the attackers broke."); setCooldown(player,faction,now+24000L); clearRequest(player); return;
        }
        FactionRequestMissionManager.applyMoraleBreak(level,req,enemy,"ProtectAttackers");
        if (!FactionRequestMissionManager.rosterNeutralized(level,req,enemy,"ProtectAttackers")) { saveRequest(player,req); return; }
        int reward=rewardRep(req,12); FactionManager.adjustReputation(player,faction,reward); FactionManager.propagatePlayerFactionConsequence(player,faction,reward);
        data.adjustMomentum(faction, 0.018F); data.adjustMomentum(enemy, -0.012F); data.addHistory(faction,now,"Real officer " + officer.getFighterName() + " survived after the committed attack roster broke.");
        officer.speak(FactionRequestDialogue.success("PROTECT",now),80); setCooldown(player,faction,now+42000L);
        finishSuccess(player, req, reward,
                "Officer " + officer.getFighterName() + " survived and the committed attack roster was broken.",
                "The officer returns to normal duty; allied momentum rises and enemy momentum falls.");
    }

    private static void tickCaptureTarget(ServerPlayer player, ServerLevel level, long now, CompoundTag req, WorldFaction faction) {
        FactionWorldData data = FactionWorldData.get(level); WorldFaction enemy = data.byId(req.getString("Target"));
        if (enemy == null) { clearRequest(player); return; }
        BlockPos targetSite = ensureFactionLandSite(player, level, req, enemy, "TargetSite"); BlockPos home = ensureFactionLandSite(player, level, req, faction, "SourceSite");
        if (targetSite == null || home == null) return;

        if (req.getBoolean("CaptureSecured")) {
            PrisonerWorldData.Prisoner prisoner = PrisonerWorldData.get(level).byId(req.getString("CapturePrisoner"));
            if (prisoner == null || !prisoner.active() || prisoner.entityId == null) { notify(player,"Capture failed: the prisoner record is no longer active."); clearRequest(player); return; }
            if (FactionRequestMissionManager.residentFallenOrDeparted(level, enemy, prisoner.entityId)) {
                PrisonerWorldData.get(level).markDead(level, prisoner, now, "killed during capture transport");
                FactionManager.adjustReputation(player, faction, -3); notify(player,"Capture failed: the prisoner died during transport. No substitute will appear."); setCooldown(player,faction,now+30000L); clearRequest(player); return;
            }
            AmbientFighterEntity target = entity(level, prisoner.entityId); if (target == null) return;
            target.setCaptive(false); target.setStoryRole(AmbientFighterEntity.STORY_CAPTIVE);
            FactionRequestMissionManager.assign(player, req, target, FactionRequestMissionManager.SIDE_NEUTRAL, FactionRequestMissionManager.ROLE_OPERATIVE);
            target.setStoryRole(AmbientFighterEntity.STORY_CAPTIVE);
            double dp = player.distanceTo(target); if (dp > 4.0D && dp < 110.0D && (now % 20L == 0L || target.getNavigation().isDone())) target.getNavigation().moveTo(player,1.06D);
            boolean playerHome = distanceSqTo(home, player) <= 65.0D*65.0D; double hx=target.getX()-(home.getX()+0.5D), hz=target.getZ()-(home.getZ()+0.5D); boolean targetHome=hx*hx+hz*hz<=65.0D*65.0D;
            if (!playerHome || !targetHome || dp > 22.0D) { saveRequest(player,req); return; }
            target.setCaptive(true); target.setStoryRole(AmbientFighterEntity.STORY_CAPTIVE);
            int reward=rewardRep(req,"ELITE_CAPTURE".equals(req.getString("Type"))?18:14); FactionManager.adjustReputation(player,faction,reward); FactionManager.propagatePlayerFactionConsequence(player,faction,reward);
            data.adjustMomentum(faction,0.012F); data.adjustMomentum(enemy,-0.014F); data.addHistory(faction,now,"The same real prisoner " + target.getFighterName() + " was escorted alive to faction custody.");
            setCooldown(player,faction,now+48000L+player.getRandom().nextInt(30001));
            finishSuccess(player, req, reward,
                    target.getFighterName()+" was subdued and escorted alive to the faction handoff as the same persistent prisoner.",
                    "The prisoner remains physically tied to future rescue/release outcomes; allied momentum rises and enemy momentum falls."); return;
        }

        boolean elite="ELITE_CAPTURE".equals(req.getString("Type")); UUID assigned=FactionRequestMissionManager.rosterId(req,"CaptureTarget",0);
        if (assigned != null && FactionRequestMissionManager.residentFallenOrDeparted(level,enemy,assigned)) { notify(player,"Capture failed: the marked target died or left before being subdued. The request will not choose a replacement."); setCooldown(player,faction,now+30000L); clearRequest(player); return; }
        AmbientFighterEntity target = reserveRealMember(player,level,req,enemy,"CaptureTarget",targetSite,
                FactionRequestMissionManager.SIDE_ENEMY,FactionRequestMissionManager.ROLE_CAPTURE_TARGET,
                elite ? f -> !f.isNonCombatant() && f.getFactionRole().id()>=FactionRole.ENFORCER.id() : f -> !f.isNonCombatant());
        if(target==null)return; req.putUUID("TargetEntity",target.getUUID()); req.putString("TargetName",target.getFighterName());
        FactionRequestMissionManager.ensureRoster(player,level,req,enemy,"CaptureEscorts",elite?3:1,targetSite,
                FactionRequestMissionManager.SIDE_ENEMY,FactionRequestMissionManager.ROLE_COMBAT,f->!f.isNonCombatant()&&!f.getUUID().equals(target.getUUID()));
        // Escorts are the faction's real available security, not a synthetic quota. If fewer residents exist,
        // capture remains playable with the smaller fixed escort roster instead of timing out waiting for nobody.
        FactionRequestMissionManager.lockRoster(req,"CaptureTarget"); FactionRequestMissionManager.lockRoster(req,"CaptureEscorts");

        // Assignment is not the same thing as reaching the target. R28 incorrectly jumped the HUD to step 2/3
        // as soon as the UUID existed. Keep step 1 active until the player actually reaches the marked person.
        if (!req.getBoolean("Started")) {
            double approachX = player.getX() - target.getX(), approachZ = player.getZ() - target.getZ();
            if (approachX * approachX + approachZ * approachZ > 18.0D * 18.0D) { saveRequest(player, req); return; }
            req.putBoolean("CaptureEncountered", true); req.putBoolean("Started", true);
            target.speak(FactionRequestDialogue.start(req.getString("Type"),now),76);
            notify(player,"Target reached: " + target.getFighterName() + ". Step 2/3: use Friendly Fist to subdue them without killing them.");
            objectiveToast(player, "Capture step 2/3 — Friendly Fist " + target.getFighterName() + ".");
            saveRequest(player,req); return;
        }
    }

    private static void tickMercenaryHunt(ServerPlayer player, ServerLevel level, long now, CompoundTag req, WorldFaction employer) {
        FactionWorldData data=FactionWorldData.get(level); WorldFaction targetFaction=data.byId(req.getString("Target"));
        if(targetFaction==null||data.isExtinct(targetFaction)){clearRequest(player);return;}
        BlockPos site=ensureFactionLandSite(player,level,req,targetFaction,"TargetSite"); if(site==null)return;
        UUID locked = FactionRequestMissionManager.rosterId(req,"HuntTarget",0);
        if (locked != null && FactionRequestMissionManager.residentFallenOrDeparted(level,targetFaction,locked)) {
            notify(player,"Mercenary contract voided: the marked target died or left through another event. The contract will not retarget someone else."); setCooldown(player,employer,now+18000L); clearRequest(player); return;
        }
        FactionRole wanted=FactionRole.byId(req.getInt("TargetRole")); String wantedName=req.getString("TargetName");
        java.util.function.Predicate<AmbientFighterEntity> match=f->!f.isNonCombatant() && (wanted==FactionRole.LEADER ? f.isFactionLeader() || (!wantedName.isBlank()&&wantedName.equals(f.getFighterName())) : f.getFactionRole()==wanted);
        AmbientFighterEntity target=reserveRealMember(player,level,req,targetFaction,"HuntTarget",site,FactionRequestMissionManager.SIDE_ENEMY,FactionRequestMissionManager.ROLE_COMBAT,match);
        if(target==null && wanted!=FactionRole.LEADER && locked==null) target=reserveRealMember(player,level,req,targetFaction,"HuntTarget",site,FactionRequestMissionManager.SIDE_ENEMY,FactionRequestMissionManager.ROLE_COMBAT,f->!f.isNonCombatant());
        if(target==null)return;
        req.putUUID("TargetEntity",target.getUUID()); req.putString("TargetName",target.getFighterName()); req.putDouble("TargetX",target.getX()); req.putDouble("TargetZ",target.getZ()); req.putLong("TargetLastSeen",now);
        final UUID huntTargetId = target.getUUID();
        int escorts=Math.max(0,Math.min(3,req.getInt("EscortNeed"))); FactionRequestMissionManager.ensureRoster(player,level,req,targetFaction,"HuntEscorts",escorts,site,FactionRequestMissionManager.SIDE_ENEMY,FactionRequestMissionManager.ROLE_COMBAT,f->!f.isNonCombatant()&&!f.getUUID().equals(huntTargetId));
        FactionRequestMissionManager.lockRoster(req,"HuntTarget"); FactionRequestMissionManager.lockRoster(req,"HuntEscorts");
        if(!req.getBoolean("Started")){req.putBoolean("Started",true);target.speak(FactionRequestDialogue.start("MERCENARY_HUNT",now),76);notify(player,"Named contract confirmed on " + target.getFighterName() + ". No duplicate target can replace them.");saveRequest(player,req);return;}
        if(FactionRequestMissionManager.isYielded(target)||FactionRequestMissionManager.isRetreated(target)||target.isDefeated()){
            int reward=Math.max(1,rewardRep(req,16)-3); FactionManager.adjustReputation(player,employer,reward); FactionManager.propagatePlayerFactionConsequence(player,employer,reward);
            data.addHistory(employer,now,"Mercenary target " + target.getFighterName() + " was forced to yield and abandon the field rather than killed."); target.speak(FactionRequestDialogue.yield("MERCENARY_HUNT",now),72);
            setCooldown(player,employer,now+48000L);
            finishSuccess(player, req, reward,
                    "The named target " + target.getFighterName() + " surrendered and abandoned the field.",
                    "The real target survives; the employer pays a reduced reward and records the non-lethal contract resolution.");
        } else saveRequest(player,req);
    }

    private static void tickMercenaryExtraction(ServerPlayer player, ServerLevel level, long now, CompoundTag req, WorldFaction employer) {
        FactionWorldData data=FactionWorldData.get(level); WorldFaction hostile=data.byId(req.getString("Target")); if(hostile==null||data.isExtinct(hostile)){clearRequest(player);return;}
        BlockPos hostileSite=ensureFactionLandSite(player,level,req,hostile,"TargetSite"); BlockPos homeSite=ensureFactionLandSite(player,level,req,employer,"SourceSite"); if(hostileSite==null||homeSite==null)return;
        UUID operativeId=FactionRequestMissionManager.rosterId(req,"ExtractionOperative",0);
        if(operativeId!=null&&FactionRequestMissionManager.residentFallenOrDeparted(level,employer,operativeId)){
            FactionManager.adjustReputation(player,employer,-2);data.adjustMomentum(employer,-0.010F);data.addHistory(employer,now,"Extraction failed because the assigned real operative was killed or departed before reaching home.");
            notify(player,"Extraction failed: the operative is no longer available. The mission will not substitute someone else.");setCooldown(player,employer,now+30000L);clearRequest(player);return;
        }
        AmbientFighterEntity agent=reserveRealMember(player,level,req,employer,"ExtractionOperative",hostileSite,FactionRequestMissionManager.SIDE_ALLY,FactionRequestMissionManager.ROLE_OPERATIVE,f->!f.isNonCombatant());
        if(agent==null)return;
        if(agent.isCaptive()||agent.isDefeated()||FactionRequestMissionManager.isYielded(agent)||FactionRequestMissionManager.isRetreated(agent)){
            FactionManager.adjustReputation(player,employer,-2);notify(player,"Extraction failed: "+agent.getFighterName()+" was recaptured or neutralized before reaching home.");setCooldown(player,employer,now+30000L);clearRequest(player);return;
        }
        req.putUUID("TargetEntity",agent.getUUID());req.putString("TargetName",agent.getFighterName());req.putDouble("AgentX",agent.getX());req.putDouble("AgentZ",agent.getZ());
        // The exact operative is known immediately, but hostile guards do not activate off-screen before the player
        // reaches the pickup sector. The compass can therefore track the person without the mission playing itself.
        if(!req.getBoolean("Started")&&distanceSqTo(hostileSite,player)>140.0D*140.0D){saveRequest(player,req);return;}
        FactionRequestMissionManager.ensureRoster(player,level,req,hostile,"ExtractionGuards",Math.max(2,Math.min(4,req.getInt("EscortNeed"))),hostileSite,FactionRequestMissionManager.SIDE_ENEMY,FactionRequestMissionManager.ROLE_COMBAT,f->!f.isNonCombatant());
        if(!mandatoryRosterReady(player, level, req, hostile, "ExtractionGuards", hostileSite))return;
        FactionRequestMissionManager.lockRoster(req,"ExtractionOperative");FactionRequestMissionManager.lockRoster(req,"ExtractionGuards");
        if(!req.getBoolean("Started")){req.putBoolean("Started",true);resetPresence(req);agent.speak(FactionRequestDialogue.start("MERCENARY_EXTRACTION",now),82);notify(player,"Extraction contact: "+agent.getFighterName()+". Get them home safely; the guards and pursuers are committed teams and will not be replaced.");saveRequest(player,req);return;}
        if(!agent.isAlive()){FactionManager.adjustReputation(player,employer,-2);notify(player,"Extraction failed: "+agent.getFighterName()+" was killed.");setCooldown(player,employer,now+30000L);clearRequest(player);return;}
        double da=player.distanceTo(agent);if(da>4.0D&&da<110.0D&&(now%20L==0L||agent.getNavigation().isDone()))agent.getNavigation().moveTo(player,1.10D);
        if(req.getBoolean("ExtractionPursuitPlanned")&&!req.getBoolean("ExtractionPursuitTriggered")&&distanceSqTo(hostileSite,player)>=260.0D*260.0D&&distanceSqTo(homeSite,player)>=220.0D*220.0D&&da<=36.0D){
            FactionRequestMissionManager.ensureRoster(player,level,req,hostile,"ExtractionPursuit",3,player.blockPosition(),FactionRequestMissionManager.SIDE_ENEMY,FactionRequestMissionManager.ROLE_COMBAT,f->!f.isNonCombatant());
            if(FactionRequestMissionManager.rosterSize(req,"ExtractionPursuit")>0){FactionRequestMissionManager.lockRoster(req,"ExtractionPursuit");req.putBoolean("ExtractionPursuitTriggered",true);req.putInt("ExtractionPursuitCalm",0);agent.speak(FactionRequestDialogue.pressure("MERCENARY_EXTRACTION",now),72);notify(player,hostile.name()+" committed a pursuit team. Break contact or break their morale; no reinforcements are coming.");}
        }
        if(req.getBoolean("ExtractionPursuitTriggered")&&!req.getBoolean("ExtractionPursuitResolved")){
            FactionRequestMissionManager.applyMoraleBreak(level,req,hostile,"ExtractionPursuit");boolean broken=FactionRequestMissionManager.rosterNeutralized(level,req,hostile,"ExtractionPursuit");
            boolean closePursuer=FactionRequestMissionManager.loadedActiveRoster(level,req,"ExtractionPursuit").stream().anyMatch(f->f.distanceToSqr(player)<=160.0D*160.0D);
            if(!closePursuer)req.putInt("ExtractionPursuitCalm",req.getInt("ExtractionPursuitCalm")+20);else req.putInt("ExtractionPursuitCalm",0);
            if(broken||req.getInt("ExtractionPursuitCalm")>=200){if(!broken)FactionRequestMissionManager.withdrawRoster(level,req,"ExtractionPursuit");req.putBoolean("ExtractionPursuitResolved",true);agent.speak(FactionRequestDialogue.success("MERCENARY_EXTRACTION",now^0x72A1L),68);objectiveToast(player,broken?"Pursuit force broken — continue the extraction.":"Pursuit shaken — continue the extraction.");}
        }
        boolean playerHome=distanceSqTo(homeSite,player)<=150.0D*150.0D;double ax=agent.getX()-(homeSite.getX()+0.5D),az=agent.getZ()-(homeSite.getZ()+0.5D);boolean agentHome=ax*ax+az*az<=150.0D*150.0D;
        if(playerHome&&agentHome&&da<=28.0D)req.putInt("Presence",req.getInt("Presence")+20);else req.putInt("Presence",Math.max(0,req.getInt("Presence")-10));
        if(req.getInt("Presence")<Math.min(40,Math.max(20,req.getInt("ReturnNeed")))){saveRequest(player,req);return;}
        int reward=rewardRep(req,16);FactionManager.adjustReputation(player,employer,reward);FactionManager.propagatePlayerFactionConsequence(player,employer,reward);data.adjustMomentum(employer,0.014F);data.adjustMomentum(hostile,-0.006F);data.addHistory(employer,now,"Real operative "+agent.getFighterName()+" was extracted alive from "+hostile.name()+" territory.");agent.speak(FactionRequestDialogue.success("MERCENARY_EXTRACTION",now),82);setCooldown(player,employer,now+48000L+player.getRandom().nextInt(24001));
        finishSuccess(player, req, reward,
                "Operative " + agent.getFighterName() + " was extracted alive and returned to the employer faction.",
                "Employer momentum increases, hostile momentum falls slightly, and the same operative returns to normal faction life.");
    }

    /**
     * Source-side intelligence rendezvous. It is deliberately separate from the hostile listening
     * site; old R39/R40 requests could effectively brief the player on top of the place they were
     * supposed to infiltrate. Persist it so reloads never reroll the meeting.
     */
    private static BlockPos intelMeetingPoint(ServerPlayer player, ServerLevel level, CompoundTag req, WorldFaction employer) {
        if (player == null || level == null || req == null || employer == null) return null;
        if (req.contains("IntelMeetX") && req.contains("IntelMeetY") && req.contains("IntelMeetZ"))
            return new BlockPos(req.getInt("IntelMeetX"), req.getInt("IntelMeetY"), req.getInt("IntelMeetZ"));
        BlockPos source = ensureFactionLandSite(player, level, req, employer, "SourceSite");
        WorldFaction target = FactionWorldData.get(level).byId(req.getString("Target"));
        BlockPos hostile = target == null ? null : ensureFactionLandSite(player, level, req, target, "TargetSite");
        if (source == null) return null;
        BlockPos best = null; double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < 14; i++) {
            BlockPos candidate = AmbientFighterSpawner.findSafeGroundAround(level, source, player.getRandom(), 12, 52, 72);
            if (candidate == null || !isPracticalQuestLand(level, candidate)) continue;
            double hostileDist = hostile == null ? 999.0D : Math.sqrt(candidate.distSqr(hostile));
            if (hostile != null && hostileDist < 96.0D) continue;
            double sourceDist = Math.sqrt(candidate.distSqr(source));
            double score = hostileDist - sourceDist * 0.20D;
            if (score > bestScore) { bestScore = score; best = candidate; }
        }
        // If two faction sites happen to be unusually close, deliberately step farther away from
        // the hostile site rather than falling back to that same infiltration area.
        if (best == null) {
            for (int i = 0; i < 12; i++) {
                BlockPos candidate = AmbientFighterSpawner.findSafeGroundAround(level, source, player.getRandom(), 58, 128, 112);
                if (candidate == null || !isPracticalQuestLand(level, candidate)) continue;
                double hostileDist = hostile == null ? 999.0D : Math.sqrt(candidate.distSqr(hostile));
                if (best == null || hostileDist > bestScore) { best = candidate; bestScore = hostileDist; }
            }
        }
        if (best == null) best = source;
        req.putInt("IntelMeetX", best.getX()); req.putInt("IntelMeetY", best.getY()); req.putInt("IntelMeetZ", best.getZ());
        return best;
    }

    /** Pick an arrival point away from the player so a newly materialized real resident approaches naturally. */
    private static BlockPos intelHandlerArrivalPoint(ServerPlayer player, ServerLevel level, BlockPos meet) {
        if (player == null || level == null || meet == null) return meet;
        Vec3 look = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() > 0.0001D) horizontalLook = horizontalLook.normalize();
        BlockPos best = null; double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < 32; i++) {
            BlockPos candidate = AmbientFighterSpawner.findSafeGroundAround(level, meet, player.getRandom(), 48, 82, 112);
            if (candidate == null || !isPracticalQuestLand(level, candidate)) continue;
            Vec3 to = Vec3.atCenterOf(candidate).subtract(player.position());
            double dist = Math.sqrt(to.x * to.x + to.z * to.z);
            if (dist < 46.0D) continue;
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            double facing = flat.lengthSqr() < 0.0001D || horizontalLook.lengthSqr() < 0.0001D ? 0.0D : horizontalLook.dot(flat.normalize());
            // A newly materialized handler must begin outside the current forward view, not merely
            // be penalized for being visible. Side/back candidates still score by distance.
            if (facing > 0.05D) continue;
            double score = dist + Math.max(0.0D, -facing) * 18.0D;
            if (score > bestScore) { bestScore = score; best = candidate; }
        }
        if (best != null) return best;
        // Never degrade into spawning directly on the rendezvous in front of the player. If the
        // persisted meeting itself already happens to be safely behind them, it is an acceptable
        // natural arrival point; otherwise fail this materialization attempt and let the request
        // retry/rebind instead of visibly popping an NPC into existence.
        Vec3 toMeet = Vec3.atCenterOf(meet).subtract(player.position());
        Vec3 flatMeet = new Vec3(toMeet.x, 0.0D, toMeet.z);
        double meetDist = Math.sqrt(flatMeet.lengthSqr());
        double meetFacing = flatMeet.lengthSqr() < 0.0001D || horizontalLook.lengthSqr() < 0.0001D
                ? 0.0D : horizontalLook.dot(flatMeet.normalize());
        return meetDist >= 46.0D && meetFacing <= 0.05D ? meet : null;
    }

    private static boolean prepareIntelAcceptance(ServerPlayer player, ServerLevel level, CompoundTag req,
                                                  WorldFaction employer, WorldFaction target) {
        if (player == null || level == null || req == null || employer == null || target == null
                || employer.id().equals(target.id())) return false;
        BlockPos sourceSite = ensureFactionLandSite(player, level, req, employer, "SourceSite");
        BlockPos meeting = intelMeetingPoint(player, level, req, employer);
        if (sourceSite == null || meeting == null) return false;
        req.putDouble("IntelMissionGiverX", meeting.getX() + 0.5D);
        req.putDouble("IntelMissionGiverZ", meeting.getZ() + 0.5D);
        AmbientFighterEntity giver = null;
        // reserveRealMember intentionally withdraws requests for factions with zero persistent
        // residents. Intelligence has a stronger contract: create one normal regional resident
        // first instead of briefly clearing the just-accepted request/tracker.
        if (activeResidentCount(FactionWorldData.get(level), employer, false) > 0L) {
            giver = reserveRealMember(player, level, req, employer, "IntelMissionGiver", meeting,
                    FactionRequestMissionManager.SIDE_ALLY, FactionRequestMissionManager.ROLE_CONTACT,
                    f -> !f.isCaptive() && !f.isDefeated());
        }
        // R40 hard contract: an Intelligence request cannot become active in a nameless "go to the
        // site and hope somebody appears" state. If the faction currently exists only through its
        // abstract population / stale unloaded roster, materialize ONE normal persistent regional
        // member at the real faction site. That person remains part of the faction after the request.
        if (giver == null) giver = materializeIntelMissionGiver(player, level, req, employer, meeting);
        if (giver == null) {
            notify(player, "Intelligence request unavailable: " + employer.name()
                    + " could not field a real mission handler at its faction site.");
            return false;
        }
        FactionRequestMissionManager.lockRoster(req, "IntelMissionGiver");
        req.putUUID("IntelMissionGiver", giver.getUUID());
        req.putString("IntelMissionGiverName", giver.getFighterName());
        req.putDouble("IntelMissionGiverX", giver.getX());
        req.putDouble("IntelMissionGiverZ", giver.getZ());
        req.putBoolean("IntelBriefed", false);
        req.putInt("SeriesStage", 1);
        req.putInt("IntelPoint", 1);
        req.putBoolean("Started", false);
        giver.setTarget(null);
        giver.setStoryRole(AmbientFighterEntity.STORY_ALLY);
        saveRequest(player, req);
        return true;
    }

    private static AmbientFighterEntity ensureIntelMissionGiver(ServerPlayer player, ServerLevel level, CompoundTag req,
                                                                 WorldFaction employer) {
        if (player == null || level == null || req == null || employer == null) return null;
        UUID expected = req.hasUUID("IntelMissionGiver") ? req.getUUID("IntelMissionGiver")
                : FactionRequestMissionManager.rosterId(req, "IntelMissionGiver", 0);
        if (expected != null && FactionRequestMissionManager.residentFallenOrDeparted(level, employer, expected)) {
            FactionRequestMissionManager.releaseRoster(level, req, "IntelMissionGiver");
            req.remove("IntelMissionGiver");
            req.remove("IntelMissionGiverName");
            req.remove("IntelGiverReassignedNotified");
        }
        BlockPos sourceSite = ensureFactionLandSite(player, level, req, employer, "SourceSite");
        BlockPos meeting = intelMeetingPoint(player, level, req, employer);
        if (sourceSite == null || meeting == null) return null;
        AmbientFighterEntity giver = null;
        if (activeResidentCount(FactionWorldData.get(level), employer, false) > 0L) {
            giver = reserveRealMember(player, level, req, employer, "IntelMissionGiver", meeting,
                    FactionRequestMissionManager.SIDE_ALLY, FactionRequestMissionManager.ROLE_CONTACT,
                    f -> !f.isCaptive() && !f.isDefeated());
        }
        // Self-heal R39 saves that already accepted Intelligence without a bound physical handler.
        if (giver == null) giver = materializeIntelMissionGiver(player, level, req, employer, meeting);
        if (giver == null) return null;
        FactionRequestMissionManager.lockRoster(req, "IntelMissionGiver");
        if (expected != null && !expected.equals(giver.getUUID()) && !req.getBoolean("IntelGiverReassignedNotified")) {
            req.putBoolean("IntelGiverReassignedNotified", true);
            notify(player, "Your original intelligence handler became unavailable. " + giver.getFighterName()
                    + " is taking over the same briefing/report role.");
        }
        req.putUUID("IntelMissionGiver", giver.getUUID());
        req.putString("IntelMissionGiverName", giver.getFighterName());
        req.putDouble("IntelMissionGiverX", giver.getX());
        req.putDouble("IntelMissionGiverZ", giver.getZ());
        giver.setTarget(null);
        giver.setStoryRole(AmbientFighterEntity.STORY_ALLY);
        return giver;
    }


    private static AmbientFighterEntity materializeIntelMissionGiver(ServerPlayer player, ServerLevel level, CompoundTag req,
                                                                       WorldFaction employer, BlockPos meeting) {
        if (player == null || level == null || req == null || employer == null || meeting == null) return null;
        // One deliberate chunk wake at acceptance is preferable to an accepted mission with no actor.
        // spawnMember(...regional=true) is the same persistent faction-population path used when a
        // player visits a faction region; this is not a disposable quest entity.
        BlockPos arrival = intelHandlerArrivalPoint(player, level, meeting);
        if (arrival == null) return null;
        level.getChunk(arrival.getX() >> 4, arrival.getZ() >> 4);
        AmbientFighterEntity manifested = FactionEncounterManager.spawnMember(player, employer, arrival,
                UUID.randomUUID(), false, FactionRole.MEMBER, null, true);
        if (manifested == null) return null;
        manifested.setPersistenceRequired();
        manifested.getPersistentData().putBoolean("LWIntelHandlerMaterialized", true);
        manifested.getPersistentData().putBoolean("LWIntelHandlerApproaching", !arrival.equals(meeting));
        FactionWorldData.get(level).recordResident(employer, manifested);
        // Run through the ordinary reservation path so assignment tags, roster ownership and
        // cleanup semantics are identical to a naturally-loaded handler.
        AmbientFighterEntity reserved = reserveRealMember(player, level, req, employer, "IntelMissionGiver", meeting,
                FactionRequestMissionManager.SIDE_ALLY, FactionRequestMissionManager.ROLE_CONTACT,
                f -> !f.isCaptive() && !f.isDefeated());
        if (reserved != null) return reserved;

        // Defensive fallback: the freshly materialized resident should always be reservable, but if
        // another mission-index edge case prevents that lookup, bind this exact actor explicitly
        // rather than ever returning a visible-but-unassigned handler.
        ListTag roster = new ListTag();
        roster.add(StringTag.valueOf(manifested.getUUID().toString()));
        req.put(FactionRequestMissionManager.ROSTER_PREFIX + "IntelMissionGiver", roster);
        FactionRequestMissionManager.assign(player, req, manifested,
                FactionRequestMissionManager.SIDE_ALLY, FactionRequestMissionManager.ROLE_CONTACT);
        return manifested;
    }

    private static void beginIntelBriefing(ServerPlayer player, ServerLevel level, long now, CompoundTag req,
                                           WorldFaction employer, WorldFaction target, AmbientFighterEntity giver) {
        if (req.getBoolean("IntelBriefed")) return;
        req.putBoolean("IntelBriefed", true);
        req.putBoolean("Started", true);
        req.putInt("SeriesStage", 1);
        req.putInt("IntelPoint", Math.max(1, req.contains("IntelPoint") ? req.getInt("IntelPoint") : 1));
        req.remove("ObjectiveX"); req.remove("ObjectiveY"); req.remove("ObjectiveZ");
        req.remove("IntelScenePreparedPoint"); req.remove("IntelLastSceneChatter");
        req.putBoolean("IntelDetected", false); req.putInt("IntelSuspicion", 0); req.putInt("IntelCalm", 100);
        resetPresence(req);
        ensureFactionLandSite(player, level, req, target, "TargetSite");
        giver.speak(FactionRequestDialogue.briefing("MERCENARY_INTEL", now ^ giver.getUUID().getLeastSignificantBits()), 108);
        notify(player, giver.getFighterName() + " briefed you on " + target.name()
                + ". Reach each marked listening position, stay hidden while the local members talk, and return to this same person with the report.");
        objectiveToast(player, "INTELLIGENCE GAIN • briefing complete • infiltrate " + target.name() + " without being seen");
        saveRequest(player, req);
    }

    private static void tickMercenaryIntel(ServerPlayer player, ServerLevel level, long now, CompoundTag req, WorldFaction employer) {
        FactionWorldData data = FactionWorldData.get(level);
        WorldFaction target = data.byId(req.getString("Target"));
        if (target == null || data.isExtinct(target)) { clearRequest(player); return; }

        AmbientFighterEntity giver = ensureIntelMissionGiver(player, level, req, employer);
        if (giver == null) { saveRequest(player, req); return; }
        if (!req.getBoolean("IntelBriefed")) {
            BlockPos meeting = intelMeetingPoint(player, level, req, employer);
            if (meeting != null) {
                double md = giver.distanceToSqr(meeting.getX() + 0.5D, meeting.getY(), meeting.getZ() + 0.5D);
                if (md > 6.0D * 6.0D && giver.getTarget() == null && (now % 20L == 0L || giver.getNavigation().isDone())) {
                    giver.setLocomotionMode(DBSagasEntity.LocomotionMode.RUN);
                    giver.getNavigation().moveTo(meeting.getX() + 0.5D, meeting.getY(), meeting.getZ() + 0.5D, 1.10D);
                } else if (md <= 6.0D * 6.0D) {
                    giver.getPersistentData().remove("LWIntelHandlerApproaching");
                    giver.setLocomotionMode(DBSagasEntity.LocomotionMode.WALK);
                }
            }
            req.putDouble("IntelMissionGiverX", giver.getX()); req.putDouble("IntelMissionGiverZ", giver.getZ());
            if (player.distanceToSqr(giver) <= 18.0D * 18.0D && !req.getBoolean("IntelBriefReminder")) {
                req.putBoolean("IntelBriefReminder", true);
                giver.speak("You're here for the intelligence job. Talk to me before you move on their base.", 82);
                objectiveToast(player, "BRIEFING • right-click " + giver.getFighterName());
            }
            saveRequest(player, req);
            return;
        }

        int stage = Math.max(1, req.getInt("SeriesStage"));
        if (stage == 1) {
            int point = Math.max(1, req.contains("IntelPoint") ? req.getInt("IntelPoint") : 1);
            int pointNeed = Math.max(3, req.getInt("IntelPointsNeed"));
            FactionMissionFlavor.IntelScenario scenario = FactionMissionFlavor.intelScenario(req.getInt("IntelScenarioSeed"), point);
            BlockPos targetSite = ensureFactionLandSite(player, level, req, target, "TargetSite");
            if (targetSite == null) return;

            // The exact observation point exists before the player travels there. The compass never sends the player
            // to a vague faction centre and then silently decides where the real mission is.
            if (!req.contains("ObjectiveX") || !req.contains("ObjectiveZ")) {
                BlockPos observation = findIntelObservationPoint(level, targetSite, req, player.getRandom(), player);
                req.putDouble("ObjectiveX", observation.getX() + 0.5D);
                req.putInt("ObjectiveY", observation.getY());
                req.putDouble("ObjectiveZ", observation.getZ() + 0.5D);
                rememberIntelObservationPoint(req, observation);
                req.putInt("IntelPoint", point);
                req.putString("IntelScenarioName", scenario.name());
                req.remove("IntelScenePreparedPoint");
                resetPresence(req);
                saveRequest(player, req);
                objectiveToast(player, "Observation " + point + " / " + pointNeed + " marked — get within 10 blocks. Crouching matters on exposed ground; the timer is shown on the HUD.");
                return;
            }

            BlockPos observation = new BlockPos((int)Math.floor(req.getDouble("ObjectiveX")), req.getInt("ObjectiveY"),
                    (int)Math.floor(req.getDouble("ObjectiveZ")));

            // Only pull the real security roster into mission duty once the contractor is reasonably close. The roster
            // is fixed on first assignment and the same surviving UUIDs rotate through every observation scene.
            if (FactionRequestMissionManager.rosterSize(req, "IntelObservers") == 0 && distanceSqTo(observation, player) > 140.0D * 140.0D) return;
            if (FactionRequestMissionManager.rosterSize(req, "IntelObservers") == 0) {
                FactionRequestMissionManager.ensureRoster(player, level, req, target, "IntelObservers", 5, targetSite,
                        FactionRequestMissionManager.SIDE_ENEMY, FactionRequestMissionManager.ROLE_OBSERVER, f -> !f.isNonCombatant());
                if (!mandatoryRosterReady(player, level, req, target, "IntelObservers", targetSite)) return;
                FactionRequestMissionManager.lockRoster(req, "IntelObservers");
            } else {
                // Locked rosters still wake their exact persistent residents if their chunks unloaded.
                FactionRequestMissionManager.ensureRoster(player, level, req, target, "IntelObservers",
                        FactionRequestMissionManager.rosterSize(req, "IntelObservers"), targetSite,
                        FactionRequestMissionManager.SIDE_ENEMY, FactionRequestMissionManager.ROLE_OBSERVER, f -> !f.isNonCombatant());
            }

            List<AmbientFighterEntity> observers = FactionRequestMissionManager.loadedActiveRoster(level, req, "IntelObservers");
            boolean securityBroken = FactionRequestMissionManager.rosterNeutralized(level, req, target, "IntelObservers");
            if (observers.isEmpty() && !securityBroken) { saveRequest(player, req); return; }

            // Re-stage the SAME surviving residents for each new intelligence scenario. R27 only did this for point one,
            // which made later points appear empty despite a valid locked roster.
            if (req.getInt("IntelScenePreparedPoint") != point) {
                int idx = 0;
                for (AmbientFighterEntity guard : observers) {
                    BlockPos gp = AmbientFighterSpawner.findSafeGroundAround(level, targetSite, player.getRandom(),
                            5 + idx, 10 + idx, 32);
                    if (gp != null) {
                        if (player.distanceToSqr(guard) > 72.0D * 72.0D || !player.hasLineOfSight(guard)) {
                            guard.teleportTo(gp.getX() + 0.5D, gp.getY(), gp.getZ() + 0.5D);
                            guard.getNavigation().stop();
                        } else {
                            guard.getNavigation().moveTo(gp.getX() + 0.5D, gp.getY(), gp.getZ() + 0.5D, 1.08D);
                        }
                    }
                    guard.setTarget(null);
                    guard.getPersistentData().putBoolean("LWRequestAlerted", false);
                    guard.setStoryRole(AmbientFighterEntity.STORY_NONE);
                    idx++;
                }
                req.putInt("IntelScenePreparedPoint", point);
                req.putBoolean("IntelDetected", false);
                req.putInt("IntelSuspicion", 0);
                req.putInt("IntelCalm", 100);
                req.remove("IntelSecurityBrokenNotified");
                resetPresence(req);
                if (!observers.isEmpty()) observers.get(0).speak(FactionMissionFlavor.pick(scenario.arrival(), now + point * 17L), 72);
                int original = FactionRequestMissionManager.rosterSize(req, "IntelObservers");
                String observerNames = observers.stream().map(AmbientFighterEntity::getFighterName)
                        .collect(java.util.stream.Collectors.joining(", "));
                notify(player, scenario.name() + ": " + scenario.brief() + " Stay within 10 blocks for "
                        + secondsRemaining(req.getInt("Presence"), requiredPresence(req, 600)) + "s of clean observation. "
                        + "Security team (" + observers.size() + "/" + original + " active): " + observerNames
                        + ". This is the committed security team; casualties are not replaced.");
                objectiveToast(player, "Observation " + point + " / " + pointNeed + " • 10-block listening zone • crouch/use cover if exposed");
                saveRequest(player, req);
                return;
            }

            if (securityBroken && !req.getBoolean("IntelSecurityBrokenNotified")) {
                req.putBoolean("IntelSecurityBrokenNotified", true);
                req.putBoolean("IntelCompromised", true);
                notify(player, "All assigned observers are neutralized. Observation can continue, but the operation is fully compromised and no replacement NPC will appear.");
            }

            double ox = req.getDouble("ObjectiveX"), oz = req.getDouble("ObjectiveZ");
            double pdx = player.getX() - ox, pdz = player.getZ() - oz;
            boolean atPoint = pdx * pdx + pdz * pdz <= 10.0D * 10.0D;
            boolean visible = false;
            AmbientFighterEntity spotter = null;
            double casualtySecurity = Math.min(12.0D, req.getInt("IntelCasualties") * 2.5D);
            if (!securityBroken) {
                for (AmbientFighterEntity guard : observers) {
                    double sight = (guard.getFactionRole().id() >= FactionRole.ENFORCER.id() ? 48.0D : 40.0D) + casualtySecurity;
                    if (canCovertGuardSee(level, guard, player, sight)) { visible = true; spotter = guard; break; }
                }
            }

            // Intelligence Gain uses real sight, not a delayed suspicion grace bar. One actual LOS detection
            // compromises the current listening attempt and immediately turns the assigned guards hostile.
            req.putInt("IntelSuspicion", visible ? 100 : 0);
            if (visible && !observers.isEmpty()) {
                if (!req.getBoolean("IntelDetected")) {
                    req.putBoolean("IntelDetected", true);
                    req.putInt("IntelCalm", 0);
                    req.putInt("Presence", -20);
                    req.putBoolean("IntelCompromised", true);
                    if (spotter != null) spotter.speak(FactionMissionFlavor.pick(scenario.detected(), now), 78);
                    notify(player, "SPOTTED during " + scenario.name() + ". That listening attempt was broken. Escape line of sight for 6 seconds, then return and listen again.");
                    objectiveToast(player, "SPOTTED • progress broken • lose line of sight for 6s");
                }
                for (AmbientFighterEntity guard : observers) {
                    guard.getPersistentData().putBoolean("LWRequestAlerted", true);
                    guard.setStoryRole(AmbientFighterEntity.STORY_ENEMY);
                    guard.setTarget(player);
                    PeacekeeperManager.markNpcAggressor(player, guard);
                }
            } else if (req.getBoolean("IntelDetected")) {
                if (!visible) req.putInt("IntelCalm", req.getInt("IntelCalm") + 20); else req.putInt("IntelCalm", 0);
                if (req.getInt("IntelCalm") >= 120 || securityBroken) {
                    req.putBoolean("IntelDetected", false); req.putInt("IntelSuspicion", 0);
                    clearQuestAggression(observers, player);
                    AmbientFighterEntity voice = observers.isEmpty() ? null : observers.get(0);
                    if (voice != null) voice.speak(FactionMissionFlavor.pick(scenario.searching(), now), 66);
                    notify(player, "Pursuit lost. Return to the observation marker; your clean observation timer can resume.");
                }
            }

            if (atPoint && !req.getBoolean("IntelDetected")) {
                req.putInt("Presence", req.getInt("Presence") + 20);
                // Let the player actually hear the scene they are observing. These are contextual guard lines from the
                // current scenario, not generic mission spam, and the pool/salt prevents one sentence looping.
                long lastSceneChat = req.getLong("IntelLastSceneChatter");
                long sceneInterval = 160L + Math.floorMod(req.getInt("IntelScenarioSeed") * 31L + point * 47L, 120L);
                if (!observers.isEmpty() && (lastSceneChat <= 0L || now - lastSceneChat >= sceneInterval)) {
                    AmbientFighterEntity speaker = observers.get(Math.floorMod((int)(now + point), observers.size()));
                    speaker.speak(FactionMissionFlavor.pick(scenario.arrival(), now ^ speaker.getUUID().getLeastSignificantBits()), 66);
                    req.putLong("IntelLastSceneChatter", now);
                }
            } else if (!atPoint) req.putInt("Presence", Math.max(-20, req.getInt("Presence") - 6));

            if (req.getInt("Presence") < requiredPresence(req, 600)) { saveRequest(player, req); return; }

            clearQuestAggression(observers, player);
            if (!observers.isEmpty()) observers.get(0).speak(FactionMissionFlavor.pick(scenario.observed(), now), 70);
            point++;
            req.putInt("IntelPoint", point);
            req.remove("ObjectiveX"); req.remove("ObjectiveY"); req.remove("ObjectiveZ");
            req.remove("IntelScenePreparedPoint"); req.remove("IntelLastSceneChatter");
            req.putBoolean("IntelDetected", false); req.putInt("IntelSuspicion", 0); req.putInt("IntelCalm", 100);
            resetPresence(req);

            if (point > pointNeed) {
                // Observation duty is over. Surviving enemy residents immediately return to normal faction life.
                FactionRequestMissionManager.releaseRoster(level, req, "IntelObservers");
                req.putInt("SeriesStage", 2);
                resetPresence(req);
                objectiveToast(player, "INTEL COMPLETE • return to " + giver.getFighterName() + " and report");
                notify(player, "Collection complete. Return to the same mission giver, " + giver.getFighterName()
                        + ", and right-click them to deliver the intelligence report. There is no automatic proximity handoff.");
            } else {
                FactionMissionFlavor.IntelScenario next = FactionMissionFlavor.intelScenario(req.getInt("IntelScenarioSeed"), point);
                objectiveToast(player, "NEXT: observation " + point + " / " + pointNeed + " • " + next.name());
                notify(player, "Relocate to the newly marked " + next.name() + " point. The same surviving security residents rotate there; anyone killed stays permanently absent.");
            }
            saveRequest(player, req);
            return;
        }

        // Report stage stays with the same real person who gave the briefing. No second liaison and no
        // proximity auto-completion: the player physically returns and right-clicks the handler.
        giver.setTarget(null);
        giver.setStoryRole(AmbientFighterEntity.STORY_ALLY);
        req.putDouble("IntelMissionGiverX", giver.getX());
        req.putDouble("IntelMissionGiverZ", giver.getZ());
        if (!req.getBoolean("IntelReturnSpoken") && player.distanceToSqr(giver) <= 24.0D * 24.0D) {
            giver.speak("You made it back. Give me the report when you're ready.", 82);
            req.putBoolean("IntelReturnSpoken", true);
            objectiveToast(player, "REPORT • right-click " + giver.getFighterName());
        }
        saveRequest(player, req);
    }

    private static void completeIntelHandoff(ServerPlayer player, ServerLevel level, long now, CompoundTag req,
                                             WorldFaction employer, WorldFaction target, AmbientFighterEntity contact) {
        if (player == null || level == null || req == null || employer == null || target == null) return;
        int casualties = Math.max(0, req.getInt("IntelCasualties"));
        int reward = Math.max(1, rewardRep(req, 12) - Math.min(6, casualties * 2));
        FactionManager.adjustReputation(player, employer, reward);
        FactionManager.propagatePlayerFactionConsequence(player, employer, reward);
        FactionWorldData.get(level).adjustMomentum(employer, casualties == 0 ? 0.014F : 0.006F);
        FactionWorldData.get(level).addHistory(employer, now, "An outside contractor returned with a multi-point field intelligence package on " + target.name()
                + (casualties > 0 ? ", but the operation caused " + casualties + " enemy casualties." : " without exposing the collection team."));
        setCooldown(player, employer, now + 36000L + player.getRandom().nextInt(24001));
        if (contact != null) {
            if (contact.getTarget() == player) contact.setTarget(null);
            contact.getNavigation().stop();
            FactionMissionFlavor.IntelScenario lastScenario = FactionMissionFlavor.intelScenario(req.getInt("IntelScenarioSeed"), Math.max(1, req.getInt("IntelPointsNeed")));
            contact.speak(FactionMissionFlavor.pick(casualties > 0 ? lastScenario.reportBloody() : lastScenario.reportClean(), now + player.getUUID().hashCode()), 84);
            contact.setStoryRole(AmbientFighterEntity.STORY_NONE);
        }
        finishSuccess(player, req, reward,
                employer.name() + " accepted the completed field-intelligence report on " + target.name() + ".",
                casualties > 0 ? casualties + " real casualties were recorded; the compromised operation reduced the payout and hardened enemy security."
                        : "The collection remained clean; employer momentum improves and the full intelligence value is recorded.");
    }

    private static void tickMercenarySabotage(ServerPlayer player, ServerLevel level, long now, CompoundTag req, WorldFaction employer) {
        FactionWorldData data = FactionWorldData.get(level);
        WorldFaction target = data.byId(req.getString("Target"));
        if (target == null || data.isExtinct(target)) { clearRequest(player); return; }
        BlockPos site = ensureFactionLandSite(player, level, req, target, "TargetSite");
        if (site == null) return;
        double d2 = distanceSqTo(site, player);
        if (req.getBoolean("SabotageDone")) {
            if (d2 >= 300.0D * 300.0D) req.putInt("Presence", req.getInt("Presence") + 20);
            else req.putInt("Presence", Math.max(0, req.getInt("Presence") - 10));
            if (req.getInt("Presence") < Math.max(120, req.getInt("ReturnNeed"))) { saveRequest(player, req); return; }
            int disrupted = req.getInt("SabotageDisrupted");
            int reward = Math.max(1, rewardRep(req, 13) - Math.min(4, req.getInt("SabotageCasualties")));
            FactionManager.adjustReputation(player, employer, reward); FactionManager.propagatePlayerFactionConsequence(player, employer, reward);
            data.adjustMomentum(employer, 0.012F); data.adjustMomentum(target, -0.018F);
            setCooldown(player, employer, now + 42000L + player.getRandom().nextInt(24001));
            speakMissionSuccess(level, req, "SabotageSecurity");
            finishSuccess(player, req, reward,
                    "All disruption points were completed and you escaped the supply route.",
                    target.name() + " lost " + disrupted + " real supplies. Surviving guards remain the same persistent faction members; casualties are permanent."); return;
        }
        if (!req.contains("SabotageRoute", Tag.TAG_LIST)) {
            ListTag route = new ListTag(); BlockPos cursor = site;
            for (int i = 0; i < 3; i++) {
                BlockPos node = AmbientFighterSpawner.findSafeGroundAround(level, cursor, player.getRandom(), 26, 64, 56);
                if (node == null || !isPracticalQuestLand(level, node)) node = site;
                CompoundTag t = new CompoundTag(); t.putInt("X", node.getX()); t.putInt("Y", node.getY()); t.putInt("Z", node.getZ()); route.add(t); cursor = node;
            }
            req.put("SabotageRoute", route); req.putInt("SabotageNode", 0); req.putInt("SabotageWork", 0);
            saveRequest(player, req);
        }
        if (!req.getBoolean("Started") && d2 > 140.0D * 140.0D) return;
        ListTag route = req.getList("SabotageRoute", Tag.TAG_COMPOUND);
        if (route.isEmpty()) return;
        int nodeIndex = Math.max(0, Math.min(route.size() - 1, req.getInt("SabotageNode")));
        CompoundTag nt = route.getCompound(nodeIndex);
        BlockPos node = new BlockPos(nt.getInt("X"), nt.getInt("Y"), nt.getInt("Z"));
        String securityRole = req.getBoolean("SabotageAlarm") ? FactionRequestMissionManager.ROLE_COMBAT : FactionRequestMissionManager.ROLE_OBSERVER;
        FactionRequestMissionManager.ensureRoster(player, level, req, target,
                "SabotageSecurity", Math.max(3, Math.min(5, req.getInt("EscortNeed"))), node,
                FactionRequestMissionManager.SIDE_ENEMY, securityRole, f -> !f.isNonCombatant());
        if (!mandatoryRosterReady(player, level, req, target, "SabotageSecurity", node)) return;
        FactionRequestMissionManager.lockRoster(req, "SabotageSecurity");
        List<AmbientFighterEntity> security = FactionRequestMissionManager.loadedActiveRoster(level, req, "SabotageSecurity");
        boolean securityAlreadyBroken = FactionRequestMissionManager.rosterNeutralized(level, req, target, "SabotageSecurity");
        if (security.isEmpty() && !securityAlreadyBroken) return;
        if (!req.getBoolean("Started")) {
            req.putBoolean("Started", true); req.putBoolean("DebugImmediate", false); resetPresence(req);
            if (!security.isEmpty()) security.get(0).speak(FactionRequestDialogue.start("MERCENARY_SABOTAGE", now), 72);
            objectiveToast(player, "Interdiction 1 / " + route.size() + " — reach the first disruption point.");
            notify(player, "Supply-route security is a fixed roster of real " + target.name() + " members. Disrupt all " + route.size()
                    + " route points, then escape; nobody will reinforce or replace the guards.");
            saveRequest(player, req); return;
        }

        if (!req.getBoolean("SabotageAlarm")) {
            AmbientFighterEntity spot = security.stream().filter(f -> !FactionRequestMissionManager.isYielded(f) && !FactionRequestMissionManager.isRetreated(f))
                    .filter(f -> canCovertGuardSee(level, f, player, 44.0D)).findFirst().orElse(null);
            if (spot != null) {
                req.putBoolean("SabotageAlarm", true);
                for (AmbientFighterEntity g : security) if (!FactionRequestMissionManager.isYielded(g)) {
                    g.getPersistentData().putBoolean("LWRequestAlerted", true);
                    FactionRequestMissionManager.assign(player, req, g, FactionRequestMissionManager.SIDE_ENEMY, FactionRequestMissionManager.ROLE_COMBAT);
                    g.setTarget(player); PeacekeeperManager.markNpcAggressor(player, g);
                }
                spot.speak(FactionRequestDialogue.pressure("MERCENARY_SABOTAGE", now), 70);
                notify(player, "Alarm raised. The same route guards are engaging; there is no reinforcement wave to wait out.");
            }
        }
        FactionRequestMissionManager.applyMoraleBreak(level, req, target, "SabotageSecurity");
        double ndx = player.getX() - (node.getX() + 0.5D), ndz = player.getZ() - (node.getZ() + 0.5D);
        boolean atNode = ndx * ndx + ndz * ndz <= 12.0D * 12.0D;
        if (atNode) {
            boolean securityBroken = FactionRequestMissionManager.rosterNeutralized(level, req, target, "SabotageSecurity");
            req.putInt("SabotageWork", req.getInt("SabotageWork") + (securityBroken ? 40 : 20));
        } else req.putInt("SabotageWork", Math.max(0, req.getInt("SabotageWork") - 5));
        int workNeed = 200; // ~10 seconds of hands-on disruption at each distinct point
        if (req.getInt("SabotageWork") < workNeed) { saveRequest(player, req); return; }

        int completedNode = nodeIndex + 1;
        if (completedNode < route.size()) {
            req.putInt("SabotageNode", completedNode); req.putInt("SabotageWork", 0);
            CompoundTag next = route.getCompound(completedNode);
            objectiveToast(player, "Interdiction " + completedNode + " / " + route.size() + " complete — relocate to the next route point.");
            AmbientFighterEntity voice = security.stream().filter(AmbientFighterEntity::isAlive).findFirst().orElse(null);
            if (voice != null) voice.speak(FactionRequestDialogue.pressure("MERCENARY_SABOTAGE", now ^ completedNode * 6151L), 62);
            saveRequest(player, req); return;
        }

        int requested = Math.max(6, req.getInt("SupplyHit")); int disrupted = Math.min(data.supplies(target), requested);
        if (disrupted > 0) data.addSupplies(target, -disrupted);
        int recovered = disrupted <= 0 ? 0 : Math.max(2, disrupted / 2); if (recovered > 0) data.addSupplies(employer, recovered);
        data.adjustRelation(employer, target, -5, now, null); data.adjustMomentum(target, -0.025F);
        data.addHistory(employer, now, "A three-point supply interdiction disrupted " + target.name() + " for " + disrupted
                + " supplies using a fixed real security roster; " + req.getInt("SabotageCasualties") + " guards were killed.");
        req.putBoolean("SabotageDone", true); req.putInt("SabotageDisrupted", disrupted); resetPresence(req);
        objectiveToast(player, "Supply route disabled — escape 300 blocks clear.");
        notify(player, "All route points are disrupted. Escape 300 blocks clear; surviving security remains on duty.");
        saveRequest(player, req);
    }

    private static void tickFrontline(ServerPlayer player, ServerLevel level, long now, CompoundTag req, WorldFaction faction) {
        WorldFaction enemy=FactionWorldData.get(level).byId(req.getString("Target"));if(enemy==null){clearRequest(player);return;}BlockPos site=ensureFactionLandSite(player,level,req,enemy,"TargetSite");if(site==null||(!req.getBoolean("Started")&&distanceSqTo(site,player)>120.0D*120.0D))return;
        if(!req.getBoolean("Started")){if(!ensureCombatOperation(player,level,req,faction,enemy,site,5,6))return;req.putBoolean("Started",true);saveRequest(player,req);notify(player,"Frontline force committed: one fixed roster on each side. Break enemy morale; there are no waves and no replacements.");return;}
        FactionRequestMissionManager.applyMoraleBreak(level,req,enemy,"Enemies");FactionRequestMissionManager.applyMoraleBreak(level,req,faction,"Allies");
        boolean enemyBroken=FactionRequestMissionManager.rosterNeutralized(level,req,enemy,"Enemies"),allyBroken=FactionRequestMissionManager.rosterNeutralized(level,req,faction,"Allies");
        if(allyBroken&&!enemyBroken){FactionManager.adjustReputation(player,faction,-2);notify(player,"Frontline failed: the committed allied roster broke first.");setCooldown(player,faction,now+36000L);clearRequest(player);return;}
        if(!enemyBroken){saveRequest(player,req);return;}int reward=rewardRep(req,18);FactionManager.adjustReputation(player,faction,reward);FactionManager.propagatePlayerFactionConsequence(player,faction,reward);int supplyLoss=Math.min(4,FactionWorldData.get(level).supplies(enemy));FactionWorldData.get(level).adjustMomentum(faction,0.035F);FactionWorldData.get(level).adjustMomentum(enemy,-0.045F);FactionWorldData.get(level).addSupplies(enemy,-supplyLoss);FactionWorldData.get(level).addHistory(faction,now,"A single fixed frontline force broke "+enemy.name()+" without artificial reinforcement waves.");speakMissionSuccess(level,req,"Allies");setCooldown(player,faction,now+54000L+player.getRandom().nextInt(24001));
        finishSuccess(player, req, reward,
                "The fixed frontline force broke " + enemy.name() + " without reinforcement waves.",
                "Survivors return to normal life; casualties remain real. " + enemy.name() + " lost " + supplyLoss + " supplies and significant momentum.");
    }

    private static void tickWarReadiness(ServerPlayer player, ServerLevel level, long now, CompoundTag req, WorldFaction faction) {
        int stage = Math.max(1, req.getInt("SeriesStage"));
        if (stage <= 1) { tickSupplyReceiver(player, level, now, req, faction); return; }

        FactionWorldData data = FactionWorldData.get(level);
        WorldFaction enemy = data.byId(req.getString("Target"));
        if (enemy == null) { clearRequest(player); return; }
        BlockPos site = ensureFactionLandSite(player, level, req, enemy, "TargetSite");
        if (site == null) return;

        if (stage == 2) {
            // Create the real observation marker up front. The player is never told only "enemy territory" while
            // the server secretly expects a different point.
            if (!req.contains("ReadinessReconX") || !req.contains("ReadinessReconZ")) {
                BlockPos observation = AmbientFighterSpawner.findSafeGroundAround(level, site, player.getRandom(), 28, 70, 72);
                if (observation == null || !isPracticalQuestLand(level, observation)) observation = site;
                req.putDouble("ReadinessReconX", observation.getX() + 0.5D);
                req.putInt("ReadinessReconY", observation.getY());
                req.putDouble("ReadinessReconZ", observation.getZ() + 0.5D);
                resetPresence(req);
                req.putInt("ReadinessCalm", 0);
                objectiveToast(player, "READINESS RECON • exact observation point marked • get within 24 blocks");
                saveRequest(player, req);
                return;
            }

            BlockPos observation = new BlockPos((int)Math.floor(req.getDouble("ReadinessReconX")), req.getInt("ReadinessReconY"),
                    (int)Math.floor(req.getDouble("ReadinessReconZ")));
            if (FactionRequestMissionManager.rosterSize(req, "ReadinessObservers") == 0
                    && distanceSqTo(observation, player) > 140.0D * 140.0D) return;

            FactionRequestMissionManager.ensureRoster(player, level, req, enemy, "ReadinessObservers", 3, observation,
                    FactionRequestMissionManager.SIDE_ENEMY, FactionRequestMissionManager.ROLE_OBSERVER, f -> !f.isNonCombatant());
            if (!mandatoryRosterReady(player, level, req, enemy, "ReadinessObservers", observation)) return;
            FactionRequestMissionManager.lockRoster(req, "ReadinessObservers");
            List<AmbientFighterEntity> scouts = FactionRequestMissionManager.loadedActiveRoster(level, req, "ReadinessObservers");
            boolean scoutsBroken = FactionRequestMissionManager.rosterNeutralized(level, req, enemy, "ReadinessObservers");
            if (scouts.isEmpty() && !scoutsBroken) return;

            if (!req.getBoolean("ReadinessScenePrepared")) {
                int idx = 0;
                for (AmbientFighterEntity scout : scouts) {
                    BlockPos gp = AmbientFighterSpawner.findSafeGroundAround(level, observation, player.getRandom(), 18 + idx * 3, 42 + idx * 3, 46);
                    if (gp != null) {
                        if (player.distanceToSqr(scout) > 72.0D * 72.0D || !player.hasLineOfSight(scout))
                            scout.teleportTo(gp.getX() + 0.5D, gp.getY(), gp.getZ() + 0.5D);
                        else scout.getNavigation().moveTo(gp.getX() + 0.5D, gp.getY(), gp.getZ() + 0.5D, 1.06D);
                    }
                    scout.setTarget(null); scout.getPersistentData().putBoolean("LWRequestAlerted", false);
                    idx++;
                }
                req.putBoolean("ReadinessScenePrepared", true);
                if (!scouts.isEmpty()) scouts.get(0).speak(FactionRequestDialogue.start("WAR_READINESS", now), 72);
                notify(player, "Observe the marked deployment point from within 24 blocks. Clean observation time remaining is shown on the HUD; if scouts spot you, the timer pauses until you lose them for 5 seconds.");
                saveRequest(player, req);
                return;
            }

            if (scoutsBroken && !req.getBoolean("ReadinessObserversBroken")) {
                req.putBoolean("ReadinessObserversBroken", true);
                req.putInt("SeriesRepPenalty", Math.max(req.getInt("SeriesRepPenalty"), 3));
                notify(player, "The real deployment scout roster has been neutralized. Recon can continue, but no replacements appear and the violent approach reduces the final reputation reward.");
            }

            boolean atPoint = player.distanceToSqr(observation.getX() + 0.5D, observation.getY(), observation.getZ() + 0.5D) <= 24.0D * 24.0D;
            boolean seen = !scoutsBroken && scouts.stream().anyMatch(g -> canCovertGuardSee(level, g, player, 42.0D));
            if (atPoint && !seen && !req.getBoolean("ReadinessSpotted")) req.putInt("Presence", req.getInt("Presence") + 20);
            else if (!atPoint) req.putInt("Presence", Math.max(-20, req.getInt("Presence") - 6));

            if (seen && !req.getBoolean("ReadinessSpotted")) {
                req.putBoolean("ReadinessSpotted", true); req.putInt("ReadinessCalm", 0);
                for (AmbientFighterEntity scout : scouts) {
                    scout.getPersistentData().putBoolean("LWRequestAlerted", true); scout.setTarget(player); PeacekeeperManager.markNpcAggressor(player, scout);
                }
                if (!scouts.isEmpty()) scouts.get(0).speak(FactionRequestDialogue.pressure("WAR_READINESS", now), 76);
                notify(player, "SPOTTED by the deployment scouts. Observation is paused. Break line of sight for 5 seconds; the HUD shows the clear timer.");
                objectiveToast(player, "SPOTTED • break line of sight • 5s clear required");
            } else if (!seen && req.getBoolean("ReadinessSpotted")) {
                req.putInt("ReadinessCalm", req.getInt("ReadinessCalm") + 20);
                if (req.getInt("ReadinessCalm") >= 100 || scoutsBroken) {
                    req.putBoolean("ReadinessSpotted", false); req.putInt("ReadinessCalm", 0);
                    clearQuestAggression(scouts, player);
                    notify(player, "Deployment scouts lost you. Return within 24 blocks of the marked point to resume observation.");
                }
            } else if (seen) req.putInt("ReadinessCalm", 0);

            if (req.getInt("Presence") >= requiredPresence(req, 600)) {
                clearQuestAggression(scouts, player);
                FactionRequestMissionManager.releaseRoster(level, req, "ReadinessObservers");
                req.putInt("SeriesStage", 3); req.putBoolean("Started", false); resetPresence(req);
                req.remove("MissionStartSpoken"); req.remove("ReadinessScenePrepared");
                req.remove("ReadinessReconX"); req.remove("ReadinessReconY"); req.remove("ReadinessReconZ");
                objectiveToast(player, "READINESS RECON COMPLETE • go to the marked mobilization staging point");
                notify(player, "Recon complete. The final stage commits actual residents on both sides. Reach the exact staging marker; then the compass will track the nearest active enemy automatically.");
            }
            saveRequest(player, req);
            return;
        }

        if (!req.getBoolean("Started") && distanceSqTo(site, player) > 120.0D * 120.0D) return;
        if (!req.getBoolean("Started")) {
            if (!ensureCombatOperation(player, level, req, faction, enemy, site, 4, 5 + Math.min(2, req.getInt("ReadinessCasualties")))) return;
            req.putBoolean("Started", true); saveRequest(player, req);
            notify(player, "Mobilization rosters are assembled. You do NOT need to search the region: the compass now tracks the nearest active enemy. Break their fixed force before your own roster breaks.");
            return;
        }
        FactionRequestMissionManager.applyMoraleBreak(level, req, enemy, "Enemies");
        FactionRequestMissionManager.applyMoraleBreak(level, req, faction, "Allies");
        boolean enemyBroken = FactionRequestMissionManager.rosterNeutralized(level, req, enemy, "Enemies");
        boolean allyBroken = FactionRequestMissionManager.rosterNeutralized(level, req, faction, "Allies");
        if (allyBroken && !enemyBroken) {
            FactionManager.adjustReputation(player, faction, -3); data.adjustMomentum(faction, -0.020F);
            data.addHistory(faction, now, "War readiness failed when the committed mobilization roster broke first.");
            notify(player, "War Readiness failed: the allied fixed roster broke first. No replacement force will appear.");
            setCooldown(player, faction, now + 36000L); clearRequest(player); return;
        }
        if (!enemyBroken) { saveRequest(player, req); return; }
        speakMissionSuccess(level, req, "Allies");
        completeSeries(player, level, now, req, faction, "War readiness ended with a real fixed-roster mobilization instead of spawned waves.");
    }

    private static void tickRecoveryLine(ServerPlayer player, ServerLevel level, long now, CompoundTag req, WorldFaction faction) {
        int stage=Math.max(1,req.getInt("SeriesStage"));if(stage<=1){tickSupplyReceiver(player,level,now,req,faction);return;}BlockPos site=ensureFactionLandSite(player,level,req,faction,"SourceSite");if(site==null||distanceSqTo(site,player)>120.0D*120.0D)return;
        FactionRequestMissionManager.ensureRoster(player,level,req,faction,"RecoveryTeam",4,site,FactionRequestMissionManager.SIDE_ALLY,FactionRequestMissionManager.ROLE_PATROL,f->!f.isNonCombatant());if(!mandatoryRosterReady(player, level, req, faction, "RecoveryTeam", site))return;FactionRequestMissionManager.lockRoster(req,"RecoveryTeam");
        List<AmbientFighterEntity> team=FactionRequestMissionManager.loadedActiveRoster(level,req,"RecoveryTeam");if(team.isEmpty()){if(FactionRequestMissionManager.rosterNeutralized(level,req,faction,"RecoveryTeam")){FactionManager.adjustReputation(player,faction,-2);FactionWorldData.get(level).adjustMomentum(faction,-0.015F);FactionWorldData.get(level).addHistory(faction,now,"Faction Recovery failed when its fixed resident team could no longer continue.");notify(player,"Faction Recovery failed: the real recovery team was neutralized or withdrew. No replacements will be assigned.");setCooldown(player,faction,now+30000L);clearRequest(player);}return;}
        AmbientFighterEntity leader=team.get(0);if(!req.getBoolean("Started")){req.putBoolean("Started",true);req.putInt("RecoveryCheckpoints",0);leader.speak(FactionRequestDialogue.start("RECOVERY_LINE",now),76);notify(player,"Recovery team assembled from real residents. Escort this same surviving team through three regroup checkpoints.");saveRequest(player,req);return;}
        if(!req.contains("RecoveryX")){BlockPos next=AmbientFighterSpawner.findSafeGroundAround(level,leader.blockPosition(),player.getRandom(),45,85,64);if(next==null||!isPracticalQuestLand(level,next))next=site;req.putDouble("RecoveryX",next.getX()+0.5D);req.putInt("RecoveryY",next.getY());req.putDouble("RecoveryZ",next.getZ()+0.5D);saveRequest(player,req);return;}
        double x=req.getDouble("RecoveryX"),z=req.getDouble("RecoveryZ");if(leader.distanceToSqr(x,req.getInt("RecoveryY"),z)>8.0D*8.0D&&(now%20L==0L||leader.getNavigation().isDone()))leader.getNavigation().moveTo(x,req.getInt("RecoveryY"),z,1.0D);for(int i=1;i<team.size();i++)if(team.get(i).distanceToSqr(leader)>10.0D*10.0D)team.get(i).getNavigation().moveTo(leader,1.05D);
        if(leader.distanceToSqr(x,req.getInt("RecoveryY"),z)<=10.0D*10.0D&&player.distanceToSqr(leader)<=36.0D*36.0D){int done=req.getInt("RecoveryCheckpoints")+1;req.putInt("RecoveryCheckpoints",done);req.remove("RecoveryX");req.remove("RecoveryY");req.remove("RecoveryZ");objectiveToast(player,"Recovery checkpoint "+done+" / 3 secured.");if(done>=3){leader.speak(FactionRequestDialogue.success("RECOVERY_LINE",now),78);completeSeries(player,level,now,req,faction,"A fixed real resident recovery team regrouped successfully after three escorted checkpoints.");return;}}saveRequest(player,req);
    }

    private static void completeSeries(ServerPlayer player, ServerLevel level, long now, CompoundTag req, WorldFaction faction, String history) {
        int reward = Math.max(1, rewardRep(req, 14) - Math.max(0, req.getInt("SeriesRepPenalty"))); int supply = Math.max(0, req.getInt("SupplyReward") - req.getInt("SeriesSupplyGranted"));
        FactionManager.adjustReputation(player, faction, reward); FactionManager.propagatePlayerFactionConsequence(player, faction, reward);
        if (supply > 0) FactionWorldData.get(level).addSupplies(faction, supply);
        FactionWorldData.get(level).addHistory(faction, now, history); setCooldown(player, faction, now + 54000L + player.getRandom().nextInt(30001));
        finishSuccess(player, req, reward, supply,
                requestTitle(req) + " completed every required stage in order.",
                history);
    }

    private static int rewardRep(CompoundTag req, int fallback) {
        int stored = req == null ? 0 : req.getInt("RepReward");
        int base = stored > 0 ? stored : fallback;
        if (base <= 0) return 0;
        // R26: faction work matters without becoming the easiest reputation farm in the mod.
        return Math.max(1, (int)Math.round(base * 0.60D));
    }

    public record RequestView(int factionSlot, String factionName, String standing, int reputation,
                              boolean activeForFaction, boolean activeElsewhere, boolean hasRequest,
                              String type, String title, String description, String difficulty,
                              String reward, String progress, long refreshSeconds,
                              boolean canAccept, boolean canAbandon, boolean canDeliver, String note,
                              List<SupplyItemSnapshot> supplyItems) { }

    public record TravelView(int factionSlot, String factionName, String instantTransmissionStatus,
                             List<FighterMemoryManager.KnownFactionPerson> contacts) { }

    public record ActiveQuestView(boolean active, int factionSlot, String factionName, String targetFactionName,
                                  String title, String description, String difficulty, String reward,
                                  String progress, String note) { }

    public static ActiveQuestView activeQuestView(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level))
            return new ActiveQuestView(false, 0, "", "", "No active faction request", "", "", "", "", "");
        CompoundTag req = request(player);
        if (!req.getString("Type").isBlank() && !isSupportedRequestType(req.getString("Type"))) {
            clearRequest(player);
            req = request(player);
        }
        if (req.getString("Type").isBlank())
            return new ActiveQuestView(false, 0, "", "", "No active faction request",
                    "Accept a request from any known faction and it will be listed here.", "", "", "",
                    "You can always return to Factions → Active Quest instead of remembering which faction issued it.");
        FactionWorldData data = FactionWorldData.get(level);
        WorldFaction source = data.byId(req.getString("Source"));
        WorldFaction target = data.byId(req.getString("Target"));
        if (source == null) return new ActiveQuestView(false, 0, "", "", "Old request unavailable",
                "The issuing faction no longer exists in the current world state.", "", "", "", "");
        if (isSupplyRequest(req) && !commitSupplyReceiverBinding(player, level, req, source)) {
            clearRequest(player);
            return new ActiveQuestView(false, 0, "", "", "Supply request cancelled",
                    "The faction no longer has an available member who can receive the shipment. No cooldown was applied.", "", "", "", "");
        }
        String receiverNote = isSupplyRequest(req)
                ? "GIVE TO: " + supplyReceiverLabel(req, source) + " • Shift+Right-click them for the LW profile and use Deliver Supplies, or ordinary right-click for a quick hand-in. The live compass tracks them. • " + supplyHistoryNote(player, source)
                : ("PATROL".equals(req.getString("Type"))
                    ? "FIELD REQUEST: Meet " + (req.getString("PatrolLeaderName").isBlank() ? "the marked patrol leader" : req.getString("PatrolLeaderName"))
                        + ", then travel with that same roster. The tracker follows the leader/checkpoint and switches to any active combat contact."
                    : ("MERCENARY_INTEL".equals(req.getString("Type"))
                        ? (!req.getBoolean("IntelBriefed")
                            ? "INTELLIGENCE: Meet " + (req.getString("IntelMissionGiverName").isBlank() ? "the marked mission giver" : req.getString("IntelMissionGiverName")) + " in person and right-click them for the briefing."
                            : (Math.max(1, req.getInt("SeriesStage")) == 1
                                ? "INTELLIGENCE: Complete every marked listening position unseen. A guard with real line of sight attacks immediately and breaks that point's current progress."
                                : "INTELLIGENCE: Return to the same mission giver and right-click them to deliver the report."))
                        : seriesNote(req, "Accepted request • issued by " + source.name() + ".")));
        return new ActiveQuestView(true, source.slot(), source.name(), target == null ? "" : target.name(),
                requestTitle(req), requestDescription(req, source, level), requestDifficulty(req), requestReward(req),
                requestProgress(player, req), withNeedReason(req, receiverNote));
    }

    public static TravelView travelView(ServerPlayer player, WorldFaction faction) {
        if (player == null || faction == null) return new TravelView(0, "Faction", "Unavailable", List.of());
        WorldFaction destination = faction;
        if (player.level() instanceof ServerLevel level) {
            CompoundTag active = request(player);
            WorldFaction source = FactionWorldData.get(level).byId(active.getString("Source"));
            WorldFaction target = FactionWorldData.get(level).byId(active.getString("Target"));
            if (source != null && source.id().equals(faction.id()) && target != null) {
                String type = active.getString("Type");
                int stage = Math.max(1, active.getInt("SeriesStage"));
                boolean objectiveAtTarget = "ASSAULT".equals(type) || "RETALIATION".equals(type) || "RECON".equals(type)
                        || "CAPTURE".equals(type) || "ELITE_CAPTURE".equals(type) || "FRONTLINE".equals(type)
                        || "MERCENARY_HUNT".equals(type) || "MERCENARY_SABOTAGE".equals(type)
                        || ("MERCENARY_EXTRACTION".equals(type) && !active.getBoolean("Started"))
                        || ("MERCENARY_INTEL".equals(type) && active.getBoolean("IntelBriefed") && stage == 1)
                        || ("WAR_READINESS".equals(type) && stage >= 2);
                if (objectiveAtTarget) destination = target;
            }
        }
        WorldFaction contactFaction = destination;
        List<FighterMemoryManager.KnownFactionPerson> contacts = FighterMemoryManager.knownFactionPeople(player, contactFaction.id()).stream()
                .filter(p -> p.relationship() >= 60)
                .sorted(java.util.Comparator.comparingInt(FighterMemoryManager.KnownFactionPerson::relationship).reversed()
                        .thenComparing(FighterMemoryManager.KnownFactionPerson::name))
                .toList();
        return new TravelView(faction.slot(), destination.name(), FighterInstantTransmissionManager.menuStatus(player), contacts);
    }

    public static RequestView requestView(ServerPlayer player, WorldFaction faction) {
        if (player == null || faction == null || !(player.level() instanceof ServerLevel level))
            return new RequestView(0, "Faction", "Unknown", 0, false, false, false, "", "No request", "", "", "", "", 0L, false, false, false, "", List.of());
        long now = level.getServer().overworld().getGameTime();
        int rep = FactionManager.getReputation(player, faction);
        CompoundTag active = request(player);
        if (!active.getString("Type").isBlank() && !isSupportedRequestType(active.getString("Type"))) {
            clearRequest(player);
            active = request(player);
        }
        if (!active.getString("Type").isBlank()) {
            WorldFaction source = FactionWorldData.get(level).byId(active.getString("Source"));
            if (source != null && isSupplyRequest(active) && !commitSupplyReceiverBinding(player, level, active, source)) {
                clearRequest(player);
                return requestView(player, faction); // one clean re-entry with no active request and no cooldown
            }
            boolean same = source != null && source.id().equals(faction.id());
            String type = active.getString("Type");
            return new RequestView(faction.slot(), faction.name(), FactionManager.reputationLabel(rep), rep,
                    same, !same, same, same ? type : "", same ? requestTitle(active) : "Request unavailable",
                    same ? requestDescription(active, faction, level) : "You already accepted work for " + (source == null ? "another faction" : source.name()) + ".",
                    same ? requestDifficulty(active) : "", same ? requestReward(active) : "",
                    same ? requestProgress(player, active) : "Finish or abandon your active request first.", 0L,
                    false, same, same && isSupplyRequest(active), same && isSupplyRequest(active)
                            ? withNeedReason(active, "GIVE TO: " + supplyReceiverLabel(active, faction) + " • Shift+Right-click for their LW profile → Deliver Supplies; ordinary right-click also works. The live compass tracks them. • " + supplyHistoryNote(player, faction))
                            : (same ? ("PATROL".equals(type)
                                    ? withNeedReason(active, "FIELD REQUEST: Meet " + (active.getString("PatrolLeaderName").isBlank() ? "the marked patrol leader" : active.getString("PatrolLeaderName"))
                                            + ", stay with the patrol, and complete the route together. Hostile contact is not guaranteed.")
                                    : seriesNote(active, "Accepted request"))
                                    : "One active faction request is allowed at a time."),
                    same && isSupplyRequest(active) ? supplyItemSnapshots(active) : List.of());
        }
        long ready = cooldown(player, faction);
        if (ready > now) {
            long seconds = Math.max(1L, (ready - now + 19L) / 20L);
            return new RequestView(faction.slot(), faction.name(), FactionManager.reputationLabel(rep), rep,
                    false, false, false, "", "No request available", "This faction is not ready to offer more work yet.",
                    "", "", "Cooldown • " + formatSeconds(seconds), seconds, false, false, false,
                    "Completed/abandoned work is deliberately paced to prevent reputation grinding.", List.of());
        }
        CompoundTag offer = ensureOffer(player, faction, level, now);
        String type = offer.getString("Type");
        long refreshMs = offer.getLong("RefreshAtMs");
        long remaining = Math.max(0L, (refreshMs - System.currentTimeMillis() + 999L) / 1000L);
        if (type.isBlank()) return new RequestView(faction.slot(), faction.name(), FactionManager.reputationLabel(rep), rep,
                false, false, false, "", "No request available", "This faction does not currently need outside help, or has chosen to handle its present pressure internally.",
                "", "", "Board reassesses in " + formatSeconds(remaining), remaining, false, false, false,
                "Requests are need-driven, not guaranteed. Different factions reassess their boards on different schedules.", List.of());
        return new RequestView(faction.slot(), faction.name(), FactionManager.reputationLabel(rep), rep,
                false, false, true, type, requestTitle(offer), requestDescription(offer, faction, level), requestDifficulty(offer),
                requestReward(offer), offerBoardProgress(player, offer, remaining), remaining, true, false, isSupplyRequest(offer),
                withNeedReason(offer, isSupplyRequest(offer)
                        ? seriesNote(offer, rep <= FactionManager.HOSTILE_REP ? "This is reconciliation work: material reparations can rebuild standing without another fight."
                                : "This request exists because of the faction's current circumstances.") + " • " + supplyHistoryNote(player, faction)
                        : "FIELD REQUEST: A patrol team will assemble when accepted. Travel may stay quiet, encounter rival fighters along the route, or escalate into an ambush if current faction pressure supports one."),
                isSupplyRequest(offer) ? supplyItemSnapshots(offer) : List.of());
    }

    public static List<String> guiLines(ServerPlayer player, WorldFaction faction) {
        RequestView v = requestView(player, faction);
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        lines.add("## Standing"); lines.add("* " + v.standing() + " • " + (v.reputation() >= 0 ? "+" : "") + v.reputation());
        lines.add("## " + v.title()); if (!v.description().isBlank()) lines.add("* " + v.description());
        if (!v.difficulty().isBlank()) lines.add("~ Difficulty: " + v.difficulty());
        if (!v.reward().isBlank()) lines.add("+ Reward: " + v.reward());
        if (!v.progress().isBlank()) lines.add(". " + v.progress());
        if (v.canAccept()) lines.add("@request:accept_" + v.type().toLowerCase(java.util.Locale.ROOT) + "|Accept request");
        if (v.canAbandon()) lines.add("@request:abandon|Abandon request");
        if (!v.note().isBlank()) lines.add("~ " + v.note());
        return lines;
    }

    public static void handleGuiAction(ServerPlayer player, int factionSlot, String action) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;
        WorldFaction faction = FactionManager.bySlot(level, factionSlot);
        if (faction == null || !PlayerWorldManager.knowsFaction(player, faction)) return;
        long now = level.getServer().overworld().getGameTime();
        if ("deliver".equals(action)) {
            CompoundTag active = request(player);
            if (faction.id().equals(active.getString("Source")) && isSupplyRequest(active))
                deliverSupplies(player, level, faction, active, now);
            return;
        }
        if ("abandon".equals(action)) {
            CompoundTag active = request(player);
            if (faction.id().equals(active.getString("Source"))) {
                clearRequest(player);
                clearOffer(player, faction);
                setCooldown(player, faction, now + 72000L); // cannot accept/abandon to reroll instantly
                notify(player, "You stepped away from " + faction.name() + "'s request. New work will take time to appear.");
            }
            return;
        }
        if (action == null || !("accept".equals(action) || action.startsWith("accept_"))
                || !request(player).getString("Type").isBlank() || cooldown(player, faction) > now) return;
        CompoundTag offer = ensureOffer(player, faction, level, now);
        String wanted = "accept".equals(action) ? offer.getString("Type")
                : action.substring("accept_".length()).toUpperCase(java.util.Locale.ROOT);
        if (offer.getString("Type").isBlank() || wanted.isBlank() || !wanted.equals(offer.getString("Type"))) return;
        if (!isSupportedRequestType(wanted)) { clearOffer(player, faction); return; }
        FactionWorldData data = FactionWorldData.get(level);
        WorldFaction target = data.byId(offer.getString("Target"));
        if (target == null) target = faction;
        String prisoner = offer.getString("Prisoner");
        if ("RESCUE".equals(wanted)) {
            PrisonerWorldData.Prisoner p = PrisonerWorldData.get(level).byId(prisoner);
            if (p == null || !p.active()) { clearOffer(player, faction); return; }
            WorldFaction captor = data.byId(p.captorFactionId); if (captor == null) { clearOffer(player, faction); return; }
            target = captor;
        }
        if (needsOtherFaction(wanted) && (target == null || target.id().equals(faction.id()))) { clearOffer(player, faction); return; }
        if (isSupplyType(wanted) && !bindSupplyReceiverIdentity(offer, faction, level)) {
            clearOffer(player, faction);
            notify(player, "That supply request expired because no real faction receiver is currently available.");
            return;
        }
        setRequest(player, wanted, faction, target == null ? faction : target, prisoner, Long.MAX_VALUE);
        CompoundTag active = request(player); copyOfferParameters(offer, active); active.putBoolean("GuiRequest", true); active.putInt("Progress", 0); resetPresence(active);
        if (isSupplyType(wanted) && !commitSupplyReceiverBinding(player, level, active, faction)) {
            clearRequest(player); clearOffer(player, faction);
            notify(player, "Request not accepted: no available faction member could receive the shipment. You were not given a cooldown.");
            return;
        }
        if (target != null && !target.id().equals(faction.id())) ensureFactionLandSite(player, level, active, target, "TargetSite");
        if ("PATROL".equals(wanted) && !preparePatrolAcceptance(player, level, active, faction)) {
            clearRequest(player); clearOffer(player, faction);
            notify(player, "Request not accepted: no available patrol leader could reach a safe rendezvous. You were not given a cooldown.");
            return;
        }
        clearOffer(player, faction);
        if (isSupplyType(wanted)) {
            notify(player, "Accepted: " + requestTitle(wanted) + " for " + faction.name() + ". Deliver " + exactSupplySummary(active, false)
                    + " to " + active.getString("SupplyReceiverName") + "; Shift+Right-click them and choose Deliver Supplies. The live compass tracks the receiver.");
        } else if ("PATROL".equals(wanted)) {
            notify(player, "Accepted: Patrol Support for " + faction.name() + ". Meet " + active.getString("PatrolLeaderName")
                    + " at the marked rendezvous. The live compass stays locked to that patrol leader until the patrol starts.");
        }
    }

    private static List<String> offerTypes(ServerPlayer player, WorldFaction faction, ServerLevel level, long now) {
        int rep = FactionManager.getReputation(player, faction);
        java.util.ArrayList<String> candidates = new java.util.ArrayList<>();
        FactionWorldData data = FactionWorldData.get(level);
        int supplies = data.supplies(faction);
        float momentum = data.momentum(faction);
        boolean war = !data.warEnemies(faction, now).isEmpty();

        // R42 request contract: stable supply work plus Patrol. Other legacy mission families
        // remain disabled until they are deliberately rebuilt one by one.
        if (rep <= FactionManager.HOSTILE_REP) {
            if (supplies < 58 || momentum < 0.94F) candidates.add("REPARATIONS");
        } else {
            if (supplies < (war ? 64 : 46)) candidates.add("PROVISIONS");
            if (supplies < (war ? 52 : 34)) candidates.add("MATERIALS");
            if (war && rep >= FactionManager.FRIENDLY_REP && supplies < 72) candidates.add("WAR_STOCKPILE");
            // Patrol is field work, so the faction must trust the player at least somewhat and
            // must have a real reason to put extra bodies on the road.
            if (rep >= 15 && (war || momentum < 0.97F || supplies < 38)) candidates.add("PATROL");
        }

        candidates.removeIf(type -> !isSupportedRequestType(type)
                || !requestHasRealActors(type, faction, faction, data));
        return new java.util.ArrayList<>(new java.util.LinkedHashSet<>(candidates));
    }

    private static long activeResidentCount(FactionWorldData data, WorldFaction faction, boolean combatantOnly) {
        if (data == null || faction == null) return 0L;
        return data.residents(faction).stream().filter(r -> !r.fallen() && !r.departed())
                .filter(r -> !combatantOnly || !r.nonCombatant()).count();
    }

    /**
     * A request that is forbidden from inventing actors must never be offered unless its persistent cast actually
     * exists. This turns impossible "go there and wait for somebody" boards into no offer at all.
     */
    private static boolean requestHasRealActors(String type, WorldFaction source, WorldFaction target, FactionWorldData data) {
        long sourceAny = activeResidentCount(data, source, false);
        long sourceCombat = activeResidentCount(data, source, true);
        long targetAny = activeResidentCount(data, target, false);
        long targetCombat = activeResidentCount(data, target, true);
        return switch (type) {
            case "REPARATIONS", "PROVISIONS", "MATERIALS", "WAR_STOCKPILE" -> sourceAny >= 1;
            case "PATROL" -> sourceCombat >= 4;
            case "TRAINING", "TRAIN_RECRUIT", "TRAIN_OFFICER", "RECOVERY", "RECOVERY_LINE" -> sourceCombat >= 1;
            case "RESCUE" -> sourceAny >= 1; // exact real prisoner eligibility is checked separately by offerTypes.
            case "RECON", "MERCENARY_HUNT", "MERCENARY_SABOTAGE", "CAPTURE" -> targetCombat >= 1;
            case "ELITE_CAPTURE" -> data.residents(target).stream().anyMatch(r -> !r.fallen() && !r.departed()
                    && !r.nonCombatant() && r.role().id() >= FactionRole.ENFORCER.id());
            case "MERCENARY_INTEL" -> sourceAny >= 1 && targetCombat >= 1;
            case "MERCENARY_EXTRACTION", "PROTECT", "ASSAULT", "DEFEND", "RETALIATION", "FRONTLINE", "WAR_READINESS" -> sourceCombat >= 1 && targetCombat >= 1;
            default -> true;
        };
    }

    private static boolean needsOtherFaction(String type) {
        return "ASSAULT".equals(type) || "DEFEND".equals(type) || "RETALIATION".equals(type) || "RECON".equals(type)
                || "CAPTURE".equals(type) || "ELITE_CAPTURE".equals(type) || "PROTECT".equals(type) || "FRONTLINE".equals(type)
                || "WAR_READINESS".equals(type) || "MERCENARY_HUNT".equals(type)
                || "MERCENARY_EXTRACTION".equals(type) || "MERCENARY_INTEL".equals(type) || "MERCENARY_SABOTAGE".equals(type);
    }
    private static WorldFaction resolveRequestTarget(ServerLevel level, WorldFaction faction, long now) {
        FactionWorldData data = FactionWorldData.get(level);
        List<WorldFaction> war = data.warEnemies(faction, now).stream().filter(o -> !data.isExtinct(o)).toList();
        if (!war.isEmpty()) return war.get(Math.floorMod((int)(now / 24000L + faction.slot()), war.size()));
        return data.factions(faction.realm()).stream().filter(o -> !o.id().equals(faction.id()) && !data.isExtinct(o)
                && FactionManager.relation(level, faction, o).rivalry()).findFirst().orElse(null);
    }

    private static String requestTitle(String type) { return switch(type) {
        case "REPARATIONS" -> "Reparations Shipment";
        case "MERCENARY_HUNT" -> "Mercenary Contract"; case "MERCENARY_EXTRACTION" -> "Operative Extraction";
        case "MERCENARY_INTEL" -> "Intelligence Gain"; case "MERCENARY_SABOTAGE" -> "Supply Interdiction";
        case "PROVISIONS" -> "Provision Run"; case "MATERIALS" -> "Material Stockpile"; case "WAR_STOCKPILE" -> "Prepare for War";
        case "WAR_READINESS" -> "War Readiness"; case "RECOVERY_LINE" -> "Faction Recovery";
        case "TRAINING" -> "Training Assistance"; case "TRAIN_RECRUIT" -> "Recruit Drill";
        case "TRAIN_OFFICER" -> "Officer Combat Drill"; case "PATROL" -> "Patrol Support"; case "RECOVERY" -> "Recovery Deployment";
        case "RESCUE" -> "Rescue Captive"; case "DEFEND" -> "Defensive Call"; case "ASSAULT" -> "War Strike";
        case "RETALIATION" -> "Retaliation"; case "FRONTLINE" -> "Hold the Frontline"; case "RECON" -> "Deep Recon";
        case "PROTECT" -> "Protect an Officer"; case "CAPTURE" -> "Capture Target"; case "ELITE_CAPTURE" -> "Capture an Enemy Lieutenant";
        default -> "Faction Request"; }; }

    private static String requestTitle(CompoundTag req) {
        String type = req == null ? "" : req.getString("Type");
        if ("WAR_READINESS".equals(type)) return "War Readiness — " + Math.max(1, req.getInt("SeriesStage")) + "/3";
        if ("RECOVERY_LINE".equals(type)) return "Faction Recovery — " + Math.max(1, req.getInt("SeriesStage")) + "/2";
        if ("MERCENARY_INTEL".equals(type)) {
            if (!req.getBoolean("IntelBriefed")) return "Intelligence Gain — Briefing";
            return Math.max(1, req.getInt("SeriesStage")) == 1 ? "Intelligence Gain — Infiltration" : "Intelligence Gain — Report";
        }
        if ("MERCENARY_HUNT".equals(type) && FactionRole.byId(req.getInt("TargetRole")) == FactionRole.LEADER) return "Mercenary Contract — Leadership Target";
        return requestTitle(type);
    }

    private static String requestDescription(String type, WorldFaction faction, ServerLevel level) {
        CompoundTag temp = new CompoundTag(); temp.putString("Type", type); initializeRequirements(temp, type, RandomSource.create());
        return requestDescription(temp, faction, level);
    }

    private static String requestDescription(CompoundTag req, WorldFaction faction, ServerLevel level) {
        String type = req.getString("Type"); int required = requiredCount(req, type);
        int food = req.getInt("FoodNeed"), ore = req.getInt("OreNeed"), escorts = Math.max(0, req.getInt("EscortNeed"));
        int observationSeconds = Math.max(1, requiredPresence(req, 600) / 20);
        if ("WAR_READINESS".equals(type)) {
            int stage = Math.max(1, req.getInt("SeriesStage"));
            if (stage == 1) return "Stage 1/3: deliver " + food + " food and " + ore + " useful materials to an actual " + faction.name() + " receiver. The stockpile changes their real supplies.";
            if (stage == 2) return "Stage 2/3: observe a fixed group of real enemy residents without being identified. Cover and line of sight matter; if spotted, lose them before observation resumes.";
            return "Stage 3/3: join a single fixed mobilization roster of real faction members. Break the committed enemy force by defeat, surrender, capture or morale withdrawal; nobody respawns as a quest replacement.";
        }
        if ("RECOVERY_LINE".equals(type)) {
            int stage = Math.max(1, req.getInt("SeriesStage"));
            if (stage == 1) return "Stage 1/2: stabilize the faction through a physical handoff to a real member: " + food + " food" + (ore > 0 ? " and " + ore + " materials" : "") + ".";
            return "Stage 2/2: escort the same fixed team of real residents through three regroup checkpoints. Their survival and the completed route affect faction recovery.";
        }
        return switch(type) {
            case "MERCENARY_HUNT" -> mercenaryHuntDescription(req, faction, level);
            case "MERCENARY_EXTRACTION" -> mercenaryExtractionDescription(req, faction, level);
            case "MERCENARY_INTEL" -> mercenaryIntelDescription(req, faction, level);
            case "MERCENARY_SABOTAGE" -> mercenarySabotageDescription(req, faction, level);
            case "REPARATIONS" -> supplyDescription(req, faction, level, "Material restitution", "They are accepting a concrete shipment instead of another escalation.");
            case "PROVISIONS" -> supplyDescription(req, faction, level, "Provision shortage", "Their stores are low enough to request a specific food shipment.");
            case "MATERIALS" -> supplyDescription(req, faction, level, "Material shortage", "Their reserve needs specific construction/training material.");
            case "WAR_STOCKPILE" -> supplyDescription(req, faction, level, "Wartime stockpile", "Active conflict has created a specific stockpile order.");
            case "TRAINING" -> "Meet one actual " + faction.name() + " fighter and complete a sanctioned spar. That exact fighter receives the normal Living World training growth and then returns to their life.";
            case "TRAIN_RECRUIT" -> "Train a fixed roster of " + required + " real resident fighter" + (required == 1 ? "" : "s") + ". Each person spars once; injured or killed trainees are never replaced just to satisfy the request.";
            case "TRAIN_OFFICER" -> "Run serious sanctioned drills with a fixed roster of " + required + " experienced real faction member" + (required == 1 ? "" : "s") + ". Their existing ranks, stats and identities are preserved.";
            case "PATROL" -> "Meet the marked " + faction.name() + " patrol and travel checkpoint-to-checkpoint with their leader. They normally travel on foot, but flight-capable members can cross water and obstacles by air. Rival fighters may be encountered along the route, and pressure can trigger an ambush. There are no reinforcement waves; surrender, retreat and casualties matter.";
            case "RECOVERY" -> "Escort a weakened fixed resident patrol through a shorter recovery route. The goal is to bring the same people through the route, not remain inside a timer radius.";
            case "RESCUE" -> "Recover the exact persistent faction member currently held captive. Their guards are real members of the captor faction; break that fixed guard force and free the same UUID—no prisoner or guard doubles exist.";
            case "DEFEND" -> "Defend faction territory with one committed roster of real allies against one fixed real attacking force. The operation ends when the attack is genuinely broken, and its outcome changes faction momentum/supplies.";
            case "ASSAULT" -> "Take part in a real wartime strike using fixed resident rosters. Break the opposing force through defeat, surrender or withdrawal; the attack damages real enemy momentum and supplies instead of advancing an arbitrary timer.";
            case "RETALIATION" -> "Join a fixed counterstrike after faction losses. Neutralize the committed opposing force; casualties are real, survivors can yield or withdraw, and the result changes faction pressure and relations.";
            case "FRONTLINE" -> "Fight one decisive frontline engagement between fixed real faction rosters. There are no artificial waves: whichever committed force breaks first determines the outcome and faction momentum changes accordingly.";
            case "RECON" -> "Observe a fixed group of actual rival residents for about " + observationSeconds + " seconds of clean intelligence. They must truly see and identify you to raise pursuit; cover, facing and crouching matter, and killed observers are permanent casualties.";
            case "PROTECT" -> "Protect one actual faction officer from a fixed real enemy attack roster. The mission ends when that force breaks or the real officer dies; their survival improves faction momentum.";
            case "CAPTURE" -> "Subdue one specific real hostile faction member with Friendly Fist. The same UUID becomes a physical prisoner and is removed from available manpower until rescued, released or otherwise resolved.";
            case "ELITE_CAPTURE" -> "Take one specific high-ranking real enemy member alive with Friendly Fist while up to " + escorts + " real escort" + (escorts == 1 ? "" : "s") + " defend them. Killing the marked target fails the request.";
            default -> "Help with a current faction need using the actual people and simulated state involved; request actors are never manufactured stand-ins.";
        };
    }

    private static String requestDifficulty(String type) { return switch(type) {
        case "TRAINING", "PROVISIONS" -> "Standard";
        case "PATROL", "TRAIN_RECRUIT", "RECON", "MATERIALS", "REPARATIONS" -> "Hard";
        case "DEFEND", "ASSAULT", "RECOVERY", "RETALIATION", "PROTECT", "CAPTURE", "RESCUE", "WAR_STOCKPILE", "RECOVERY_LINE", "MERCENARY_EXTRACTION", "MERCENARY_INTEL", "MERCENARY_SABOTAGE" -> "Very Hard";
        case "FRONTLINE", "ELITE_CAPTURE", "TRAIN_OFFICER", "WAR_READINESS" -> "Elite";
        case "MERCENARY_HUNT" -> "Very Hard"; default -> "Standard"; }; }

    private static String requestDifficulty(CompoundTag req) {
        if (req != null && "MERCENARY_HUNT".equals(req.getString("Type"))) {
            FactionRole role = FactionRole.byId(req.getInt("TargetRole"));
            return role == FactionRole.LEADER || role == FactionRole.LIEUTENANT ? "Elite" : "Very Hard";
        }
        return requestDifficulty(req == null ? "" : req.getString("Type"));
    }

    private static String requestReward(String type) { return switch(type) {
        case "TRAINING" -> "+5 reputation"; case "PROVISIONS" -> "+8 reputation + faction supplies";
        case "PATROL", "TRAIN_RECRUIT", "RECON" -> "+8 reputation";
        case "MERCENARY_INTEL" -> "+12 reputation"; case "MERCENARY_EXTRACTION" -> "+16 reputation"; case "MERCENARY_HUNT" -> "+18 reputation";
        case "MERCENARY_SABOTAGE" -> "+13 reputation + disrupts rival supplies";
        case "MATERIALS", "REPARATIONS" -> "+10 reputation + faction supplies";
        case "RECOVERY" -> "+11 reputation"; case "DEFEND", "ASSAULT", "PROTECT", "TRAIN_OFFICER" -> "+12 reputation";
        case "RETALIATION", "CAPTURE" -> "+14 reputation"; case "WAR_STOCKPILE" -> "+16 reputation + major supplies"; case "RESCUE" -> "+22 reputation";
        case "FRONTLINE", "ELITE_CAPTURE" -> "+18 reputation"; case "WAR_READINESS" -> "+20 reputation + major supplies"; case "RECOVERY_LINE" -> "+14 reputation + supplies";
        default -> "Faction reputation"; }; }

    private static String requestReward(CompoundTag req) {
        if (req == null) return "Faction reputation";
        int storedRep = req.getInt("RepReward"); if (storedRep <= 0) return requestReward(req.getString("Type"));
        int rep = rewardRep(req, storedRep);
        if ("MERCENARY_SABOTAGE".equals(req.getString("Type"))) return "+" + rep + " reputation + disrupts rival supplies";
        int supplies = req.getInt("SupplyReward");
        return "+" + rep + " reputation" + (supplies > 0 ? " + " + supplies + " faction supplies" : "");
    }

    private static String seriesNote(CompoundTag req, String fallback) {
        if (req == null) return fallback;
        String type = req.getString("Type");
        if ("WAR_READINESS".equals(type)) return "Serial request • all 3 stages must be completed in order. Most faction requests remain one-off.";
        if ("RECOVERY_LINE".equals(type)) return "Serial request • stabilize supplies, then remain with the faction while it regroups.";
        if ("MERCENARY_INTEL".equals(type)) return "Three-part operation • meet the mission giver for the briefing, complete every listening position unseen, then return to the same person and report.";
        if ("MERCENARY_HUNT".equals(type)) return "Targeted contract • the named person is the objective. Killing them has normal real consequences with their faction; a rare contract can name the faction leader.";
        if ("MERCENARY_EXTRACTION".equals(type)) return "Extraction contract • the operative must survive the trip home. Fighting pursuers is optional; getting the person out is the objective.";
        if ("MERCENARY_SABOTAGE".equals(type)) return "Interdiction contract • stay in hostile territory long enough to disrupt real faction supplies. The patrol creates pressure, but no kill is required.";
        return fallback;
    }


    private static String withNeedReason(CompoundTag req, String note) {
        if (req == null) return note == null ? "" : note;
        String reason = req.getString("NeedReason");
        if (reason.isBlank()) return note == null ? "" : note;
        String base = note == null ? "" : note.trim();
        return (base.isBlank() ? "" : base + " • ") + "Why now: " + reason;
    }

    private static String requestProgress(CompoundTag req) {
        String type = req.getString("Type");
        if ("WAR_READINESS".equals(type)) {
            int stage = Math.max(1, req.getInt("SeriesStage"));
            if (stage == 1) return supplyProgress(req, "Stage 1/3 • real receiver handoff");
            if (stage == 2) return "Stage 2/3 • real deployment scouts • " + (req.getBoolean("ReadinessSpotted") ? "SPOTTED — break contact" : "observe without identification");
            return "Stage 3/3 • fixed mobilization roster engaged • no reinforcement waves";
        }
        if ("RECOVERY_LINE".equals(type)) {
            int stage = Math.max(1, req.getInt("SeriesStage"));
            if (stage == 1) return supplyProgress(req, "Stage 1/2 • real receiver handoff");
            return "Stage 2/2 • recovery checkpoints " + Math.min(3, req.getInt("RecoveryCheckpoints")) + " / 3 • fixed resident team";
        }
        if ("MERCENARY_HUNT".equals(type)) {
            String name = req.getString("TargetName"); FactionRole role = FactionRole.byId(req.getInt("TargetRole"));
            return req.getBoolean("Started") ? "Real target confirmed • " + (name.isBlank() ? role.name().toLowerCase(java.util.Locale.ROOT) : name) + " • no substitute target"
                    : "Contract target is being bound to one exact persistent resident; the compass will track that person, not a search area.";
        }
        if ("MERCENARY_SABOTAGE".equals(type)) {
            if (!req.getBoolean("Started")) return "Exact land-safe supply corridor marked • security roster commits automatically on approach.";
            if (req.getBoolean("SabotageDone")) return "All 3 route points disrupted • escape 300 blocks clear";
            int node = Math.min(3, Math.max(1, req.getInt("SabotageNode") + 1));
            return "Route disruption " + node + " / 3 • " + (req.getBoolean("SabotageAlarm") ? "ALARMED — fixed security engaged" : "covert")
                    + " • work " + Math.min(100, req.getInt("SabotageWork") * 100 / 200) + "%";
        }
        if ("MERCENARY_EXTRACTION".equals(type)) {
            if (!req.getBoolean("Started")) return "Exact embedded operative is marked by name • reach that person, not a search area.";
            if (req.getBoolean("ExtractionPursuitTriggered") && !req.getBoolean("ExtractionPursuitResolved")) return "Escort the same operative • pursuit team active";
            return "Escort " + (req.getString("TargetName").isBlank() ? "the same operative" : req.getString("TargetName")) + " home alive";
        }
        if ("MERCENARY_INTEL".equals(type)) {
            if (!req.getBoolean("IntelBriefed")) {
                String giver = req.getString("IntelMissionGiverName");
                return "Briefing pending • meet " + (giver.isBlank() ? "the marked mission giver" : giver) + " and right-click them";
            }
            int stage = Math.max(1, req.getInt("SeriesStage"));
            if (stage == 1) {
                int total = Math.max(3, req.getInt("IntelPointsNeed")); int point = Math.min(Math.max(1, req.contains("IntelPoint") ? req.getInt("IntelPoint") : 1), total);
                FactionMissionFlavor.IntelScenario sc = FactionMissionFlavor.intelScenario(req.getInt("IntelScenarioSeed"), point);
                String stealth = req.getBoolean("IntelDetected") ? "SPOTTED — break LOS" : "unseen";
                return "Observation " + point + "/" + total + " • " + sc.name() + " • " + stealth + (req.getInt("IntelCasualties") > 0 ? " • casualties " + req.getInt("IntelCasualties") : "");
            }
            String giver = req.getString("IntelMissionGiverName");
            return "Report ready • return to " + (giver.isBlank() ? "the same mission giver" : giver) + " and right-click them";
        }
        if (isSupplyRequest(req)) {
            ensureExactSupplyBasket(req, type, null);
            String base = supplyProgress(req, "Shipment");
            String receiver = req.getString("SupplyReceiverName");
            return receiver.isBlank() ? base + " • receiver unavailable — request will rebind automatically" : base + " • GIVE TO: " + receiver;
        }
        if ("TRAIN_RECRUIT".equals(type) || "TRAIN_OFFICER".equals(type) || "TRAINING".equals(type)) {
            int done = req.getList("TrainingCompleted", Tag.TAG_STRING).size();
            int total = Math.max(1, FactionRequestMissionManager.rosterSize(req, "TrainingTeam") > 0 ? FactionRequestMissionManager.rosterSize(req, "TrainingTeam") : requiredCount(req, type));
            return "Real trainees • " + done + " / " + total + " completed" + (req.hasUUID("TargetEntity") ? " • current spar assigned" : " • selecting next member");
        }
        if ("PATROL".equals(type) || "RECOVERY".equals(type)) {
            int total = req.contains("PatrolRoute", Tag.TAG_LIST) ? Math.max(1, req.getList("PatrolRoute", Tag.TAG_COMPOUND).size()) : ("RECOVERY".equals(type) ? 4 : 5);
            int leg = Math.min(total, Math.max(1, req.getInt("PatrolLeg") + 1));
            if (!req.getBoolean("Started")) return "Meet the marked patrol leader • route " + total + " legs • patrol starts when you are within 14 blocks";
            if (req.getBoolean("PatrolContactActive") && !req.getBoolean("PatrolContactResolved"))
                return "Route " + leg + " / " + total + " • ROUTE CONTACT — defend the patrol against encountered rivals";
            if (req.getBoolean("PatrolAmbushTriggered") && !req.getBoolean("PatrolAmbushResolved")) return "Route " + leg + " / " + total + " • AMBUSH — break the fixed rival force";
            return "Route leg " + leg + " / " + total + " • " + (req.getBoolean("PatrolAirborne") ? "AERIAL — fly with" : "stay with") + " the same patrol";
        }
        if ("RECON".equals(type)) {
            if (!req.contains("ReconX")) return "A safe observation point is being selected; follow the marker when it appears.";
            return "Observe faction members • " + (req.getBoolean("ReconDetected") ? "SPOTTED — break line of sight" : "remain unidentified")
                    + (req.getInt("ReconCasualties") > 0 ? " • casualties " + req.getInt("ReconCasualties") : "");
        }
        if ("PROTECT".equals(type)) return req.getBoolean("Started") ? "Assigned officer under protection • fixed attackers remaining are tracked directly" : "Assigned officer is marked by name • meet them";
        if ("FRONTLINE".equals(type)) return req.getBoolean("Started") ? "Frontline teams engaged • nearest active enemy is compass-tracked" : "Enter the marked 120-block staging zone • both committed teams assemble automatically";
        if ("DEFEND".equals(type)) return req.getBoolean("Started") ? "Fixed attacking force engaged • nearest active attacker is compass-tracked" : "Enter the marked 120-block defense zone • defenders and attackers assemble automatically";
        if ("ASSAULT".equals(type)) return req.getBoolean("Started") ? "War Strike active • nearest active enemy is compass-tracked • no timer/no refills" : "Enter the marked 120-block strike staging zone • the committed teams assemble automatically; follow the marker";
        if ("RETALIATION".equals(type)) return req.getBoolean("Started") ? "Counterstrike active • nearest active enemy is compass-tracked" : "Enter the marked 120-block counterstrike staging zone • the committed fighters assemble automatically";
        if ("CAPTURE".equals(type) || "ELITE_CAPTURE".equals(type)) {
            if (req.getBoolean("CaptureSecured")) return "Step 3/3 • same prisoner secured • escort them to the land-safe faction handoff";
            if (req.getBoolean("Started")) return "Step 2/3 • marked target reached • Friendly Fist to subdue • fixed escorts only";
            return req.hasUUID("TargetEntity") || FactionRequestMissionManager.rosterId(req, "CaptureTarget", 0) != null
                    ? "Step 1/3 • marked resident reserved • reach within 18 blocks; do not attack yet"
                    : "Step 1/3 • selecting the named capture target; follow the marker when it appears";
        }
        if ("RESCUE".equals(type)) {
            if (req.getBoolean("RescueFreed")) return "Same captive freed • escort the rescued fighter home alive";
            return req.getBoolean("Started") ? "Exact captive located • break the fixed real guard roster" : "Travel to the same persistent captive";
        }
        return req.getBoolean("Started") ? "Real mission participants assigned • complete the concrete objective" : "Travel to the land-safe mission area";
    }

    /** Player-aware progress keeps server-authoritative delivery state only; inventory counts are rendered locally. */
    private static String requestProgress(ServerPlayer player, CompoundTag req) {
        return requestProgress(req);
    }

    private static String offerBoardProgress(ServerPlayer player, CompoundTag offer, long remainingSeconds) {
        if (offer == null) return "";
        if (isSupplyRequest(offer)) {
            String base = "EXACT ORDER: " + exactSupplySummary(offer, false)
                    + " • GIVE TO: " + offer.getString("SupplyReceiverName")
                    + " • offer remains posted for " + formatSeconds(remainingSeconds);
            return base;
        }
        return requestProgress(player, offer) + " • offer remains posted for " + formatSeconds(remainingSeconds);
    }

    private static String supplyProgress(CompoundTag req, String prefix) {
        ensureExactSupplyBasket(req, req.getString("Type"), null);
        ListTag items = req.getList("SupplyItems", Tag.TAG_COMPOUND);
        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            CompoundTag line = items.getCompound(i);
            parts.add(line.getString("Name") + " " + Math.min(line.getInt("Progress"), line.getInt("Need")) + " / " + line.getInt("Need"));
        }
        return prefix + (parts.isEmpty() ? " • no item order" : " • " + String.join(" • ", parts));
    }

    private static List<SupplyItemSnapshot> supplyItemSnapshots(CompoundTag req) {
        if (req == null || !isSupplyRequest(req)) return List.of();
        ensureExactSupplyBasket(req, req.getString("Type"), null);
        ListTag items = req.getList("SupplyItems", Tag.TAG_COMPOUND);
        ArrayList<SupplyItemSnapshot> out = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            CompoundTag line = items.getCompound(i);
            out.add(new SupplyItemSnapshot(line.getString("Id"), line.getString("Name"),
                    line.getInt("Need"), line.getInt("Progress")));
        }
        return List.copyOf(out);
    }

    private static String exactSupplyInventorySummary(ServerPlayer player, CompoundTag req) {
        if (player == null || req == null) return "";
        ensureExactSupplyBasket(req, req.getString("Type"), null);
        ListTag items = req.getList("SupplyItems", Tag.TAG_COMPOUND);
        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            CompoundTag line = items.getCompound(i);
            Item wanted = supplyItem(line);
            if (wanted == null || wanted == Items.AIR) continue;
            parts.add(line.getString("Name") + " ×" + inventoryCount(player, wanted));
        }
        return String.join(" • ", parts);
    }

    private static int inventoryCount(ServerPlayer player, Item wanted) {
        if (player == null || wanted == null || wanted == Items.AIR) return 0;
        int total = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(wanted)) total += stack.getCount();
        }
        return total;
    }

    private static long requestLifetime(String type) {
        if ("WAR_READINESS".equals(type) || "RECOVERY_LINE".equals(type) || "MERCENARY_EXTRACTION".equals(type) || "MERCENARY_INTEL".equals(type)) return 180000L;
        if ("FRONTLINE".equals(type) || "RESCUE".equals(type) || "MERCENARY_HUNT".equals(type) || "MERCENARY_SABOTAGE".equals(type)) return 108000L;
        return 90000L;
    }

    /** True only when the player's current faction contract explicitly authorizes lethal force against this actor. */
    /** Prevent ordinary faction hostility from bypassing Intelligence Gain's real LOS detection before identification. */
    public static boolean suppressAutomaticCovertAggression(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !fighter.isFactionMember()) return false;
        CompoundTag req = request(player);
        if (!"MERCENARY_INTEL".equals(req.getString("Type"))) return false;
        if (!fighter.getFactionId().equals(req.getString("Target"))
                || !FactionRequestMissionManager.belongsToRequest(fighter, req)
                || !FactionRequestMissionManager.ROLE_OBSERVER.equals(FactionRequestMissionManager.missionRole(fighter))) return false;
        boolean alerted = fighter.getPersistentData().getBoolean("LWRequestAlerted");
        return !req.getBoolean("IntelDetected") && !alerted;
    }

    public static boolean isAuthorizedPlayerKill(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null) return false;
        CompoundTag req = request(player);
        if (!"PATROL".equals(req.getString("Type"))) return false;
        return FactionRequestMissionManager.rosterContains(req, "PatrolAmbush", fighter.getUUID())
                || FactionRequestMissionManager.rosterContains(req, "PatrolContact", fighter.getUUID());
    }

    public static boolean isCaptureTarget(ServerPlayer player, AmbientFighterEntity fighter) {
        return false;
    }

    public static void onMercyDowned(ServerPlayer player, AmbientFighterEntity fighter) {
        // Capture requests were removed in R30. Friendly Fist itself remains a DMZ/LW combat mechanic.
    }

    public static void onSparCompleted(ServerPlayer player, AmbientFighterEntity fighter, boolean decisive) {
        // Training/spar faction requests were removed in R30. Kept as a compatibility callback.
    }

    public static void onFactionMemberKilled(ServerPlayer player, AmbientFighterEntity victim) {
        if (player == null || victim == null || !victim.isFactionMember() || !(victim.level() instanceof ServerLevel level)) return;
        CompoundTag req = request(player);
        String activeType = req.getString("Type");
        if ("PATROL".equals(activeType)) {
            if (FactionRequestMissionManager.rosterContains(req, "Patrol", victim.getUUID())) {
                WorldFaction source = FactionWorldData.get(level).byId(req.getString("Source"));
                if (source != null) {
                    FactionWorldData.get(level).addHistory(source, level.getServer().overworld().getGameTime(),
                            requestTitle(req) + " was betrayed when the outside helper killed patrol member " + victim.getFighterName() + ".");
                    notify(player, "Patrol failed: you killed " + victim.getFighterName() + ", one of the real residents you were assigned to support.");
                    setCooldown(player, source, level.getServer().overworld().getGameTime() + 36000L);
                }
                clearRequest(player);
            }
            return;
        }
        if ("MERCENARY_INTEL".equals(activeType) && victim.getFactionId().equals(req.getString("Target"))
                && FactionRequestMissionManager.rosterContains(req, "IntelObservers", victim.getUUID())) {
            int casualties = Math.min(99, Math.max(0, req.getInt("IntelCasualties")) + 1);
            req.putInt("IntelCasualties", casualties); req.putBoolean("IntelCompromised", true);
            req.putBoolean("IntelDetected", true); req.putInt("IntelSuspicion", 100); req.putInt("IntelCalm", 0);
            saveRequest(player, req);
            AmbientFighterEntity witness = FactionRequestMissionManager.loadedRoster(level, req, "IntelObservers").stream()
                    .filter(f -> f != victim && f.isAlive()).findFirst().orElse(null);
            if (witness != null) witness.speak(FactionMissionFlavor.intelCasualty(req.getString("IntelScenarioName"),
                    level.getGameTime() ^ victim.getUUID().getLeastSignificantBits()), 78);
            WorldFaction casualtyFaction = FactionWorldData.get(level).byId(req.getString("Target"));
            String casualtyFactionName = casualtyFaction == null ? req.getString("Target") : casualtyFaction.name();
            notify(player, victim.getFighterName() + " is dead. They were a real " + casualtyFactionName
                    + " member and will NOT return or be replaced. The intelligence operation is now compromised.");
            objectiveToast(player, "CASUALTY: " + victim.getFighterName() + " is permanently gone • no replacement");
            return;
        }
        // All other retired combat-request callbacks below remain unreachable in R39; only the deliberately rebuilt
        // Intelligence Gain handler is restored above the R30 supply-only compatibility gate.
        if (!isSupplyType(activeType)) return;
        if ("RECON".equals(activeType) && victim.getFactionId().equals(req.getString("Target"))
                && FactionRequestMissionManager.rosterContains(req, "ReconObservers", victim.getUUID())) {
            req.putInt("ReconCasualties", Math.min(99, req.getInt("ReconCasualties") + 1));
            req.putBoolean("ReconDetected", true); req.putInt("ReconSuspicion", 100); req.putInt("ReconCalm", 0);
            saveRequest(player, req);
            notify(player, "Recon turned violent. That resident is an actual faction casualty; the patrol will not be replenished and the reputation value is reduced.");
            return;
        }
        if ("WAR_READINESS".equals(activeType) && Math.max(1, req.getInt("SeriesStage")) == 2
                && victim.getFactionId().equals(req.getString("Target"))
                && FactionRequestMissionManager.rosterContains(req, "ReadinessObservers", victim.getUUID())) {
            int casualties = Math.min(8, Math.max(0, req.getInt("ReadinessCasualties")) + 1);
            req.putInt("ReadinessCasualties", casualties);
            req.putInt("SeriesRepPenalty", Math.max(req.getInt("SeriesRepPenalty"), Math.min(5, casualties + 1)));
            req.putBoolean("ReadinessSpotted", true);
            saveRequest(player, req);
            notify(player, "Readiness recon casualty: " + victim.getFighterName() + " was a real deployment scout. The final mobilization will be more alert and the reputation payout is reduced; no replacement scout appears.");
            return;
        }
        if ("MERCENARY_SABOTAGE".equals(activeType) && victim.getFactionId().equals(req.getString("Target"))
                && FactionRequestMissionManager.rosterContains(req, "SabotageSecurity", victim.getUUID())) {
            req.putInt("SabotageCasualties", Math.min(99, req.getInt("SabotageCasualties") + 1));
            req.putBoolean("SabotageAlarm", true); saveRequest(player, req);
            notify(player, "A real route guard was killed. Security is now fully alerted; nobody will spawn to replace them.");
            return;
        }
        if ("MERCENARY_HUNT".equals(activeType) && req.hasUUID("TargetEntity")
                && victim.getUUID().equals(req.getUUID("TargetEntity")) && victim.getFactionId().equals(req.getString("Target"))) {
            FactionWorldData data = FactionWorldData.get(level);
            WorldFaction source = data.byId(req.getString("Source"));
            WorldFaction targetFaction = data.byId(req.getString("Target"));
            if (source != null && targetFaction != null) {
                int reward = rewardRep(req, victim.isFactionLeader() ? 24 : victim.getFactionRole() == FactionRole.LIEUTENANT ? 20 : 16);
                FactionManager.adjustReputation(player, source, reward);
                FactionManager.propagatePlayerFactionConsequence(player, source, reward);
                data.recordVictory(source, victim.getFactionRole());
                int relationDamage = victim.isFactionLeader() ? -14 : victim.getFactionRole() == FactionRole.LIEUTENANT ? -8 : -4;
                data.adjustRelation(source, targetFaction, relationDamage, level.getGameTime(), null);
                data.addHistory(source, level.getGameTime(), "An outside mercenary completed a contract on " + victim.getFighterName()
                        + " of " + targetFaction.name() + ".");
                setCooldown(player, source, level.getGameTime() + 54000L + player.getRandom().nextInt(30001));
                finishSuccess(player, req, reward,
                        "The named contract target " + victim.getFighterName() + " was eliminated.",
                        "The death is permanent, " + targetFaction.name() + " records the casualty, and relations changed by " + relationDamage + ".");
                return;
            }
            clearRequest(player);
            return;
        }
        if (("CAPTURE".equals(req.getString("Type")) || "ELITE_CAPTURE".equals(req.getString("Type"))) && req.hasUUID("TargetEntity") && victim.getUUID().equals(req.getUUID("TargetEntity"))) {
            WorldFaction source = FactionWorldData.get(level).byId(req.getString("Source"));
            if (source != null) {
                setCooldown(player, source, level.getGameTime() + 36000L);
                notify(player, "Capture request failed: the target was killed instead of subdued.");
            }
            clearRequest(player); return;
        }
        if (req.getString("Type").isBlank() || !victim.getFactionId().equals(req.getString("Source"))) return;
        WorldFaction faction = FactionManager.byId(level, victim.getFactionId());
        if (faction == null) return;
        FactionManager.adjustReputation(player, faction, -6);
        FactionManager.propagatePlayerFactionConsequence(player, faction, -6);
        setCooldown(player, faction, level.getGameTime() + 60000L);
        notify(player, "Request failed: killing one of " + faction.name() + "'s members was treated as betrayal.");
        clearRequest(player);
    }

    private static long cooldown(ServerPlayer player, WorldFaction faction) { return root(player).getLong("Cooldown." + faction.id()); }
    private static void setCooldown(ServerPlayer player, WorldFaction faction, long when) { CompoundTag r = root(player); r.putLong("Cooldown." + faction.id(), when); save(player, r); }

    public static String summary(ServerPlayer player) {
        CompoundTag req = request(player);
        if (!req.getString("Type").isBlank() && !isSupportedRequestType(req.getString("Type"))) { clearRequest(player); req = request(player); }
        if (req.getString("Type").isBlank()) return "No allied faction is currently asking anything of you.";
        FactionWorldData data = FactionWorldData.get(player.serverLevel());
        WorldFaction a=data.byId(req.getString("Source")), b=data.byId(req.getString("Target"));
        if (a==null||b==null) return "An old request is no longer relevant.";
        String type=req.getString("Type");
        if (isSupplyRequest(req)) return requestTitle(req) + " • #" + a.slot() + " " + a.name() + " • " + requestProgress(player, req);
        if ("PATROL".equals(type)) return requestTitle(req) + " • #" + a.slot() + " " + a.name() + " • " + requestProgress(player, req);
        if (type.equals("RESCUE")) {
            PrisonerWorldData.Prisoner p=PrisonerWorldData.get(player.serverLevel()).byId(req.getString("Prisoner"));
            return "RESCUE • " + (p==null?"captured fighter":p.name) + " • #" + a.slot() + " " + a.name() + " → held by #" + b.slot() + " " + b.name();
        }
        return type + " • #" + a.slot() + " " + a.name() + " vs #" + b.slot() + " " + b.name();
    }

    public static int force(ServerPlayer player, String type) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        String normalized = type == null ? "" : type.toUpperCase(java.util.Locale.ROOT);
        if (!isSupportedRequestType(normalized)) {
            notify(player, "That request type is not enabled yet. Debug forcing currently supports provisions, materials, war stockpiles, reparations and patrol.");
            return 0;
        }
        clearRequest(player);
        FactionWorldData data = FactionWorldData.get(level);
        long now = level.getServer().overworld().getGameTime();

        // Debug forcing is a test harness, not normal offer generation. Deliberately choose a pair that already has
        // the real persistent cast required by the requested mission so /lw request attack|defend|... always creates
        // a playable test when such a pair exists. Prefer factions whose residents are already loaded.
        List<WorldFaction> realm = data.activeFactions().stream()
                .filter(f -> f.realm() == LivingWorldDimensions.realm(level) && !data.isExtinct(f)).toList();
        WorldFaction ally = null, enemy = null; int bestScore = Integer.MIN_VALUE;
        boolean debugNeedsOther = needsOtherFaction(normalized) || "RESCUE".equals(normalized);
        if (debugNeedsOther) {
            for (WorldFaction source : realm) for (WorldFaction target : realm) {
                if (source.id().equals(target.id()) || !requestHasRealActors(normalized, source, target, data)) continue;
                if ("ELITE_CAPTURE".equals(normalized) && data.residents(target).stream().noneMatch(r -> !r.fallen() && !r.departed()
                        && !r.nonCombatant() && r.role().id() >= FactionRole.ENFORCER.id())) continue;
                int loadedSource = FactionRequestMissionManager.loadedAvailableResidents(level, source, f -> !f.isNonCombatant()).size();
                int loadedTarget = FactionRequestMissionManager.loadedAvailableResidents(level, target, f -> !f.isNonCombatant()).size();
                int score = loadedSource * 120 + loadedTarget * 120
                        + (int)(activeResidentCount(data, source, true) + activeResidentCount(data, target, true)) * 8
                        + Math.max(-20, Math.min(20, FactionManager.getReputation(player, source) / 5));
                if (score > bestScore) { bestScore = score; ally = source; enemy = target; }
            }
        } else {
            for (WorldFaction source : realm) {
                if (!requestHasRealActors(normalized, source, source, data)) continue;
                int loaded = FactionRequestMissionManager.loadedAvailableResidents(level, source, f -> !f.isNonCombatant()).size();
                int score = loaded * 120 + (int)activeResidentCount(data, source, true) * 8
                        + Math.max(-20, Math.min(20, FactionManager.getReputation(player, source) / 5));
                if (score > bestScore) { bestScore = score; ally = source; }
            }
        }
        if (ally == null) {
            notify(player, "Debug force failed: no faction in this realm currently has the real persistent cast required for " + requestTitle(normalized) + ".");
            return 0;
        }

        if ("RESCUE".equals(normalized)) {
            if (enemy == null) return 0;
            List<AmbientFighterEntity> realAllies = FactionRequestMissionManager.loadedAvailableResidents(level, ally,
                    f -> !f.isNonCombatant() && !f.isCaptive() && !f.isDefeated());
            if (realAllies.isEmpty()) { notify(player, "Debug rescue needs a real loaded " + ally.name() + " member; no synthetic prisoner will be created."); return 0; }
            AmbientFighterEntity realCaptive = realAllies.get(0);
            PrisonerWorldData.Prisoner p = PrisonerWorldData.get(level).captureExisting(level, realCaptive, enemy, now);
            if (p == null) return 0;
            setRequest(player,"RESCUE",ally,enemy,p.id,now+72000L);
        } else if ("TRAIN_RECRUIT".equals(normalized) || "TRAIN_OFFICER".equals(normalized) || "RECOVERY".equals(normalized)
                || "PATROL".equals(normalized) || "TRAINING".equals(normalized) || "RECOVERY_LINE".equals(normalized) || isSupplyType(normalized)) {
            setRequest(player, normalized, ally, ally, "", now + 72000L);
        } else {
            if (enemy == null) { notify(player, "Debug force failed: no second faction with a suitable real roster is available."); return 0; }
            setRequest(player, normalized, ally, enemy, "", now + 72000L);
        }
        CompoundTag forced = request(player);
        initializeTargetedContract(forced, normalized, player, ally, enemy == null ? ally : enemy, data);
        if (isSupplyRequest(forced) && !commitSupplyReceiverBinding(player, level, forced, ally)) {
            clearRequest(player);
            notify(player, "Debug force failed: " + ally.name() + " has no available faction member to receive the shipment.");
            return 0;
        }
        forced.putBoolean("DebugForcedTest", true);
        forced.putBoolean("DebugImmediate", false);
        resetPresence(forced); saveRequest(player, forced);
        notify(player, "FORCED TEST REQUEST: " + requestTitle(forced) + " • " + ally.name()
                + (enemy == null || enemy.id().equals(ally.id()) ? "" : " vs " + enemy.name())
                + ". Starts at Step 1 and uses normal mission logic; no live-offer/need check is applied.");
        return 1;
    }


    public static int forceRefreshOffer(ServerPlayer player, int factionSlot) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction faction = FactionManager.bySlot(level, factionSlot);
        if (faction == null || !PlayerWorldManager.knowsFaction(player, faction)) return 0;
        clearOffer(player, faction);
        notify(player, "Debug: cleared " + faction.name() + "'s stored offer. Reopen Requests to generate a fresh one.");
        return 1;
    }

    private static boolean isSupplyType(String type) {
        return "PROVISIONS".equals(type) || "MATERIALS".equals(type)
                || "WAR_STOCKPILE".equals(type) || "REPARATIONS".equals(type);
    }

    private static boolean isSupportedRequestType(String type) {
        return isSupplyType(type) || "PATROL".equals(type);
    }

    private static boolean isSupplyRequest(CompoundTag req) {
        return req != null && isSupplyType(req.getString("Type"));
    }

    /** True only for the exact live fighter assigned to receive this player's active shipment. */
    public static boolean isAssignedSupplyReceiver(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null) return false;
        CompoundTag req = request(player);
        return isSupplyRequest(req) && req.hasUUID("SupplyReceiverEntity")
                && req.getUUID("SupplyReceiverEntity").equals(fighter.getUUID());
    }

    /** Native-profile text for the receiver; intentionally server-authoritative. */
    public static String supplyProfileLine(ServerPlayer player, AmbientFighterEntity fighter) {
        if (!isAssignedSupplyReceiver(player, fighter)) return "";
        CompoundTag req = request(player);
        ensureExactSupplyBasket(req, req.getString("Type"), null);
        return requestTitle(req) + " • " + exactSupplySummary(req, true);
    }

    /** Profile button / packet action hand-in. Re-validates UUID, distance and faction before touching inventory. */
    public static boolean deliverToReceiver(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !(player.level() instanceof ServerLevel level)) return false;
        CompoundTag req = request(player);
        if (!isAssignedSupplyReceiver(player, fighter)) return false;
        WorldFaction faction = FactionWorldData.get(level).byId(req.getString("Source"));
        if (faction == null) return false;
        deliverSupplies(player, level, faction, req, level.getServer().overworld().getGameTime());
        return true;
    }

    private static final Item[] SUPPLY_FOODS = new Item[]{
            Items.BREAD, Items.COOKED_BEEF, Items.COOKED_CHICKEN, Items.COOKED_PORKCHOP,
            Items.BAKED_POTATO, Items.CARROT, Items.APPLE, Items.COOKED_MUTTON, Items.COOKED_RABBIT
    };
    private static final Item[] SUPPLY_MATERIALS = new Item[]{
            Items.COAL, Items.CHARCOAL, Items.RAW_IRON, Items.RAW_COPPER, Items.RAW_GOLD,
            Items.IRON_INGOT, Items.COPPER_INGOT, Items.GOLD_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI, Items.QUARTZ
    };

    private static void initializeExactSupplyBasket(CompoundTag req, String type, RandomSource random) {
        if (req == null || !isSupplyType(type)) return;
        RandomSource r = random == null ? RandomSource.create(deterministicSupplySeed(req, type)) : random;
        ListTag list = new ListTag();
        java.util.HashSet<Item> used = new java.util.HashSet<>();
        if ("PROVISIONS".equals(type)) {
            addSupplyLine(list, pickSupplyItem(SUPPLY_FOODS, used, r), between(r, 10, 24), "food");
            if (r.nextFloat() < 0.55F) addSupplyLine(list, pickSupplyItem(SUPPLY_FOODS, used, r), between(r, 6, 16), "food");
        } else if ("MATERIALS".equals(type)) {
            addSupplyLine(list, pickSupplyItem(SUPPLY_MATERIALS, used, r), between(r, 10, 24), "material");
            if (r.nextFloat() < 0.62F) addSupplyLine(list, pickSupplyItem(SUPPLY_MATERIALS, used, r), between(r, 6, 18), "material");
        } else if ("WAR_STOCKPILE".equals(type)) {
            addSupplyLine(list, pickSupplyItem(SUPPLY_FOODS, used, r), between(r, 18, 34), "food");
            addSupplyLine(list, pickSupplyItem(SUPPLY_MATERIALS, used, r), between(r, 14, 30), "material");
            if (r.nextFloat() < 0.58F) addSupplyLine(list, pickSupplyItem(SUPPLY_MATERIALS, used, r), between(r, 8, 20), "material");
        } else { // REPARATIONS
            addSupplyLine(list, pickSupplyItem(SUPPLY_FOODS, used, r), between(r, 8, 20), "food");
            addSupplyLine(list, pickSupplyItem(SUPPLY_MATERIALS, used, r), between(r, 6, 16), "material");
        }
        req.put("SupplyItems", list);
    }

    private static void ensureExactSupplyBasket(CompoundTag req, String type, RandomSource random) {
        if (req == null || !isSupplyType(type)) return;
        if (req.contains("SupplyItems", Tag.TAG_LIST) && !req.getList("SupplyItems", Tag.TAG_COMPOUND).isEmpty()) return;
        // R31 migration: turn old category counts into a deterministic exact order, then preserve as much
        // already-delivered category progress as possible across the new item lines.
        int oldFood = Math.max(0, req.getInt("FoodProgress"));
        int oldOre = Math.max(0, req.getInt("OreProgress"));
        initializeExactSupplyBasket(req, type, random);
        ListTag list = req.getList("SupplyItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag line = list.getCompound(i);
            int available = "food".equals(line.getString("Category")) ? oldFood : oldOre;
            if (available <= 0) continue;
            int credit = Math.min(available, line.getInt("Need"));
            line.putInt("Progress", credit);
            if ("food".equals(line.getString("Category"))) oldFood -= credit; else oldOre -= credit;
        }
        req.put("SupplyItems", list);
    }

    private static long deterministicSupplySeed(CompoundTag req, String type) {
        long seed = type == null ? 0L : type.hashCode() * 0x9E3779B97F4A7C15L;
        if (req != null && req.hasUUID("RequestId")) {
            UUID id = req.getUUID("RequestId"); seed ^= id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17);
        } else if (req != null) seed ^= req.getString("Source").hashCode() * 0xC2B2AE3D27D4EB4FL;
        return seed;
    }

    private static Item pickSupplyItem(Item[] pool, java.util.Set<Item> used, RandomSource r) {
        for (int tries = 0; tries < pool.length * 2; tries++) {
            Item item = pool[r.nextInt(pool.length)];
            if (used.add(item)) return item;
        }
        Item item = pool[0]; used.add(item); return item;
    }

    private static void addSupplyLine(ListTag list, Item item, int need, String category) {
        if (item == null || need <= 0) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        CompoundTag line = new CompoundTag();
        line.putString("Id", id.toString());
        line.putString("Name", new ItemStack(item).getHoverName().getString());
        line.putString("Category", category == null ? "" : category);
        line.putInt("Need", need); line.putInt("Progress", 0);
        list.add(line);
    }

    private static Item supplyItem(CompoundTag line) {
        if (line == null || line.getString("Id").isBlank()) return Items.AIR;
        try { return BuiltInRegistries.ITEM.get(new ResourceLocation(line.getString("Id"))); }
        catch (Exception ignored) { return Items.AIR; }
    }

    private static int consumeExactSupplyItems(ServerPlayer player, CompoundTag req) {
        ListTag list = req.getList("SupplyItems", Tag.TAG_COMPOUND); int total = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag line = list.getCompound(i); Item wanted = supplyItem(line);
            int remaining = Math.max(0, line.getInt("Need") - line.getInt("Progress"));
            if (wanted == Items.AIR || remaining <= 0) continue;
            int taken = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (taken >= remaining) break;
                if (stack.isEmpty() || !stack.is(wanted)) continue;
                int use = Math.min(stack.getCount(), remaining - taken); stack.shrink(use); taken += use;
            }
            if (taken > 0) { line.putInt("Progress", line.getInt("Progress") + taken); total += taken; }
        }
        req.put("SupplyItems", list); return total;
    }

    private static boolean exactSupplyComplete(CompoundTag req) {
        ListTag list = req.getList("SupplyItems", Tag.TAG_COMPOUND);
        if (list.isEmpty()) return false;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag line = list.getCompound(i);
            if (line.getInt("Progress") < line.getInt("Need")) return false;
        }
        return true;
    }

    private static String exactSupplySummary(CompoundTag req, boolean remainingOnly) {
        ensureExactSupplyBasket(req, req == null ? "" : req.getString("Type"), null);
        if (req == null) return "the requested shipment";
        ListTag list = req.getList("SupplyItems", Tag.TAG_COMPOUND); java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag line = list.getCompound(i); int need = line.getInt("Need"), progress = Math.min(need, line.getInt("Progress"));
            int amount = remainingOnly ? Math.max(0, need - progress) : need;
            if (remainingOnly && amount <= 0) continue;
            parts.add(amount + "x " + line.getString("Name"));
        }
        return parts.isEmpty() ? "nothing remaining" : String.join(", ", parts);
    }

    private static String supplyDescription(CompoundTag req, WorldFaction faction, ServerLevel level, String state, String context) {
        ensureExactSupplyBasket(req, req.getString("Type"), null);
        if (req.getString("UrgencyTier").isBlank() && faction != null && level != null)
            applySupplyDemandContext(req, req.getString("Type"), faction, level, level.getServer().overworld().getGameTime());
        String urgency = req.getString("UrgencyTier"); if (urgency.isBlank()) urgency = "Needed";
        String demand = req.getString("DemandState"); if (demand.isBlank()) demand = state;
        return demand + " • Urgency: " + urgency + ". " + context + " ORDER: " + exactSupplySummary(req, false)
                + ". GIVE TO " + supplyReceiverLabel(req, faction)
                + ". Shift+Right-click the receiver and choose Deliver Supplies; ordinary right-click is also a quick hand-in. Partial deliveries are kept.";
    }

    private static void applySupplyDemandContext(CompoundTag req, String type, WorldFaction faction, ServerLevel level, long now) {
        if (req == null || !isSupplyType(type) || faction == null || level == null) return;
        int score = requestUrgency(type, faction, level, now);
        req.putInt("UrgencyScore", score); req.putString("UrgencyTier", urgencyTier(score));
        String state = switch (type) {
            case "WAR_STOCKPILE" -> "Preparing for active conflict";
            case "REPARATIONS" -> "Recovery through restitution";
            case "PROVISIONS" -> faction.ethos() == FactionEthos.RAIDERS || faction.ethos() == FactionEthos.MERCENARIES ? "Field ration shortage" : "Provision shortage";
            case "MATERIALS" -> faction.ethos() == FactionEthos.MARTIAL_SCHOOL || faction.ethos() == FactionEthos.CHALLENGERS ? "Training material shortage" : "Material reserve shortage";
            default -> "Supply need";
        };
        req.putString("DemandState", state);
    }

    private static String urgencyTier(int score) {
        if (score >= 82) return "CRITICAL";
        if (score >= 68) return "URGENT";
        if (score >= 52) return "NEEDED";
        return "ROUTINE";
    }

    private static String partialDeliveryLine(CompoundTag req) {
        int done = 0, total = 0; ListTag list = req.getList("SupplyItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) { CompoundTag line = list.getCompound(i); total += line.getInt("Need"); done += Math.min(line.getInt("Need"), line.getInt("Progress")); }
        double ratio = total <= 0 ? 0.0D : done / (double)total;
        String receiver = req.getString("SupplyReceiverName");
        if (ratio >= 0.75D) return receiver + " accepts the shipment: almost there";
        if (ratio >= 0.35D) return receiver + " accepts the shipment: that helps";
        return receiver + " accepts the first part of the shipment";
    }

    private static String supplyHistoryNote(ServerPlayer player, WorldFaction faction) {
        if (player == null || faction == null) return "";
        int previous = root(player).getInt("SupplyHelp." + faction.id());
        int rep = FactionManager.getReputation(player, faction);
        String tone = rep >= 60 ? "They trust you with a concrete order."
                : rep < 0 ? "They are keeping this strictly transactional."
                : "They are asking for practical outside help.";
        return tone + (previous > 0 ? " They remember " + previous + " previous completed shipment" + (previous == 1 ? "" : "s") + " from you." : "");
    }

    private static void recordSupplyMemory(ServerPlayer player, WorldFaction faction) {
        CompoundTag r = root(player); String key = "SupplyHelp." + faction.id();
        r.putInt(key, Math.min(999, r.getInt(key) + 1)); save(player, r);
    }

    private static void deliverSupplies(ServerPlayer player, ServerLevel level, WorldFaction faction, CompoundTag req, long now) {
        if (player == null || faction == null || req == null || !isSupplyRequest(req)) return;
        if (!commitSupplyReceiverBinding(player, level, req, faction)) {
            notify(player, "This shipment cannot be completed because " + faction.name() + " has no member available to receive it. The request was cleared without a cooldown.");
            clearRequest(player);
            return;
        }
        if (!atFactionDeliveryPoint(player, level, faction, req)) {
            String receiverName = req.getString("SupplyReceiverName");
            UUID receiverId = req.hasUUID("SupplyReceiverEntity") ? req.getUUID("SupplyReceiverEntity") : null;
            AmbientFighterEntity receiver = receiverId == null ? null : FactionRequestMissionManager.loadedResident(level, faction, receiverId);
            BlockPos targetPos = receiver != null ? receiver.blockPosition()
                    : (receiverId == null ? null : FactionRequestMissionManager.residentLastPos(level, faction, receiverId));
            if (targetPos == null && req.contains("SupplyReceiverX"))
                targetPos = new BlockPos(req.getInt("SupplyReceiverX"), req.getInt("SupplyReceiverY"), req.getInt("SupplyReceiverZ"));
            if (targetPos == null) {
                notify(player, "Receiver: " + receiverName + ". Their exact position is temporarily unavailable; the request will update as soon as that resident is known again.");
                return;
            }
            double dx = targetPos.getX() + 0.5D - player.getX(), dz = targetPos.getZ() + 0.5D - player.getZ();
            int blocks = (int)Math.round(Math.sqrt(dx * dx + dz * dz));
            notify(player, "GIVE TO " + receiverName + " • follow the live compass • "
                    + FactionManager.direction(player.getX(), player.getZ(), targetPos.getX(), targetPos.getZ()) + " • " + blocks
                    + " blocks • right-click " + receiverName + " when you reach them.");
            return;
        }
        ensureExactSupplyBasket(req, req.getString("Type"), player.getRandom());
        int delivered = consumeExactSupplyItems(player, req);
        player.getInventory().setChanged();
        saveRequest(player, req);
        if (delivered <= 0) {
            notify(player, "You are not carrying the exact requested items still needed: " + exactSupplySummary(req, true) + ".");
            return;
        }
        if (!exactSupplyComplete(req)) {
            notify(player, partialDeliveryLine(req) + " • Remaining: " + exactSupplySummary(req, true) + ".");
            return;
        }

        String type = req.getString("Type");
        FactionWorldData data = FactionWorldData.get(level);
        int supplyGain = Math.max(1, req.getInt("SupplyReward"));
        UUID receiverId = req.hasUUID("SupplyReceiverEntity") ? req.getUUID("SupplyReceiverEntity") : null;
        AmbientFighterEntity receiver = receiverId == null ? null : FactionRequestMissionManager.loadedResident(level, faction, receiverId);
        if (receiver != null) receiver.speak(FactionRequestDialogue.success(type, now ^ receiver.getUUID().getLeastSignificantBits()), 72);
        int rep = rewardRep(req, 8);
        FactionManager.adjustReputation(player, faction, rep);
        FactionManager.propagatePlayerFactionConsequence(player, faction, rep);
        data.addSupplies(faction, supplyGain);
        data.addHistory(faction, now, switch (type) {
            case "PROVISIONS" -> "An outsider delivered a substantial food shipment.";
            case "MATERIALS" -> "An outsider delivered mined materials for the faction's stores.";
            case "WAR_STOCKPILE" -> "A trusted outsider helped stock the faction before a major conflict.";
            case "REPARATIONS" -> "A hostile outsider made material reparations instead of escalating the feud.";
            default -> "An outsider delivered supplies.";
        });
        recordSupplyMemory(player, faction);
        setCooldown(player, faction, now + 36000L + player.getRandom().nextInt(24001));
        finishSuccess(player, req, rep, supplyGain,
                faction.name() + " accepted the full shipment through " + req.getString("SupplyReceiverName") + ".",
                "The faction's persistent supply reserve increased by " + supplyGain + ".");
    }

    private static boolean atFactionDeliveryPoint(ServerPlayer player, ServerLevel level, WorldFaction faction) {
        return atFactionDeliveryPoint(player, level, faction, request(player));
    }

    private static boolean atFactionDeliveryPoint(ServerPlayer player, ServerLevel level, WorldFaction faction, CompoundTag req) {
        if (faction.realm() != LivingWorldDimensions.realm(level) || req == null || !req.hasUUID("SupplyReceiverEntity")) return false;
        AmbientFighterEntity receiver = FactionRequestMissionManager.loadedResident(level, faction, req.getUUID("SupplyReceiverEntity"));
        return receiver != null && receiver.isAlive() && !receiver.isCaptive() && !receiver.isDefeated()
                && receiver.distanceToSqr(player) <= 18.0D * 18.0D;
    }

    private static int consumeFood(ServerPlayer player, int needed) {
        int taken = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (taken >= needed) break;
            if (stack.isEmpty() || stack.getFoodProperties(player) == null) continue;
            int use = Math.min(stack.getCount(), needed - taken);
            stack.shrink(use); taken += use;
        }
        return taken;
    }

    private static int consumeMaterials(ServerPlayer player, int needed) {
        int taken = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (taken >= needed) break;
            if (stack.isEmpty() || !isUsefulMaterial(stack)) continue;
            int use = Math.min(stack.getCount(), needed - taken);
            stack.shrink(use); taken += use;
        }
        return taken;
    }

    private static boolean isUsefulMaterial(ItemStack stack) {
        return stack.is(Items.COAL) || stack.is(Items.CHARCOAL)
                || stack.is(Items.RAW_IRON) || stack.is(Items.RAW_COPPER) || stack.is(Items.RAW_GOLD)
                || stack.is(Items.IRON_INGOT) || stack.is(Items.COPPER_INGOT) || stack.is(Items.GOLD_INGOT)
                || stack.is(Items.REDSTONE) || stack.is(Items.LAPIS_LAZULI) || stack.is(Items.QUARTZ);
    }

    /** Constant accepted-request HUD. R28 exposes the exact server-side objective instead of a vague area hint. */
    private static void updateQuestTracker(ServerPlayer player, ServerLevel level, long now, CompoundTag req) {
        if (now % 20L != 0L || req == null || req.getString("Type").isBlank()) return;
        ObjectiveSnapshot objective = objectiveSnapshot(player, level, req);
        if (objective == null) return;
        MissionStep step = objective.step();
        double dx = objective.targetX() - player.getX(), dz = objective.targetZ() - player.getZ();
        int blocks = (int)Math.round(Math.sqrt(dx * dx + dz * dz));
        String direction = blocks <= objective.arrivalRadius() ? "READY"
                : FactionManager.direction(player.getX(), player.getZ(), (int)Math.round(objective.targetX()), (int)Math.round(objective.targetZ()));
        if (step.total() > 0 && req.getInt("HudStepIndex") != step.index()) {
            req.putInt("HudStepIndex", step.index());
            saveRequest(player, req);
            objectiveToast(player, "Step " + step.index() + " / " + step.total() + " — " + step.label());
        }
        LWNetwork.sendFactionRequestTracker(player, new FactionRequestTrackerPacket(true, requestTitle(req),
                objective.status(), direction, blocks, objective.targetX(), objective.targetZ(),
                step.index(), step.total(), step.label(), step.next(), objective.arrivalRadius(),
                objective.actionPrompt(), objective.secondsRemaining(), isSupplyRequest(req) ? supplyItemSnapshots(req) : List.of()));
    }

    private record ObjectiveSnapshot(MissionStep step, String status, double targetX, double targetZ,
                                     int arrivalRadius, String actionPrompt, int secondsRemaining) {
        ObjectiveSnapshot {
            if (step == null) step = new MissionStep(0, 0, "", "");
            status = status == null ? "" : status;
            arrivalRadius = Math.max(1, Math.min(320, arrivalRadius));
            actionPrompt = actionPrompt == null ? "" : actionPrompt;
            secondsRemaining = Math.max(-1, secondsRemaining);
        }
    }

    private static int secondsRemaining(int currentTicks, int requiredTicks) {
        int left = Math.max(0, requiredTicks - Math.max(0, currentTicks));
        return (left + 19) / 20;
    }

    private static int rosterRemaining(ServerLevel level, CompoundTag req, WorldFaction faction, String rosterName) {
        int total = FactionRequestMissionManager.rosterSize(req, rosterName);
        if (total <= 0) return 0;
        return Math.max(0, total - FactionRequestMissionManager.neutralizedCount(level, req, faction, rosterName));
    }

    private static AmbientFighterEntity nearestActiveRoster(ServerLevel level, CompoundTag req, String rosterName, ServerPlayer player) {
        return FactionRequestMissionManager.loadedActiveRoster(level, req, rosterName).stream()
                .min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }

    private static BlockPos rosterLastPosition(ServerLevel level, CompoundTag req, WorldFaction faction, String rosterName) {
        int count = FactionRequestMissionManager.rosterSize(req, rosterName);
        for (int i = 0; i < count; i++) {
            UUID id = FactionRequestMissionManager.rosterId(req, rosterName, i);
            if (id == null || FactionRequestMissionManager.outcomeRecorded(req, id)) continue;
            BlockPos pos = FactionRequestMissionManager.residentLastPos(level, faction, id);
            if (pos != null) return pos;
        }
        return null;
    }

    private static ObjectiveSnapshot objectiveSnapshot(ServerPlayer player, ServerLevel level, CompoundTag req) {
        FactionWorldData data = FactionWorldData.get(level);
        WorldFaction source = data.byId(req.getString("Source"));
        if (source == null) return null;
        WorldFaction target = data.byId(req.getString("Target"));
        String type = req.getString("Type");
        MissionStep step = missionStep(req);
        String status = requestProgress(player, req);

        // Supply HUD/navigation is always about one exact real resident. It never creates a generic handoff point
        // and never wakes a chunk just to paint the compass.
        if (isSupplyRequest(req)) {
            boolean gathered = req.getInt("FoodProgress") >= req.getInt("FoodNeed") && req.getInt("OreProgress") >= req.getInt("OreNeed");
            UUID receiverId = req.hasUUID("SupplyReceiverEntity") ? req.getUUID("SupplyReceiverEntity")
                    : FactionRequestMissionManager.rosterId(req, "SupplyReceiver", 0);
            AmbientFighterEntity receiver = receiverId == null ? null : FactionRequestMissionManager.loadedResident(level, source, receiverId);
            String receiverName = req.getString("SupplyReceiverName");
            if (receiver != null) receiverName = receiver.getFighterName();
            if (receiverName.isBlank()) receiverName = "the assigned faction receiver";

            BlockPos receiverPos = receiver != null ? receiver.blockPosition()
                    : (receiverId == null ? null : FactionRequestMissionManager.residentLastPos(level, source, receiverId));
            if (receiverPos == null && req.contains("SupplyReceiverX"))
                receiverPos = new BlockPos(req.getInt("SupplyReceiverX"), req.getInt("SupplyReceiverY"), req.getInt("SupplyReceiverZ"));

            double tx = receiverPos == null ? player.getX() : receiverPos.getX() + 0.5D;
            double tz = receiverPos == null ? player.getZ() : receiverPos.getZ() + 0.5D;
            int radius = receiver != null ? 4 : 10;
            String shipmentStatus = supplyProgress(req, gathered ? "Supplies ready" : "Shipment");
            status = shipmentStatus + " • GIVE TO: " + receiverName;
            String action;
            if (!gathered) {
                action = "Gather the listed items. Receiver: " + receiverName + ". The live compass already marks where you will hand them in.";
            } else if (receiver != null) {
                action = "Right-click " + receiverName + " to hand over the shipment.";
            } else if (receiverPos != null) {
                action = "Go to " + receiverName + "'s marked last position. They should appear as you approach; then right-click them.";
            } else {
                action = "Receiver: " + receiverName + ". Their position is temporarily unavailable; keep the request active and check the tracker again shortly.";
            }
            return new ObjectiveSnapshot(step, status, tx, tz, radius, action, -1);
        }

        BlockPos sourceSite = cachedFactionLandSite(req, source, "SourceSite");
        if (sourceSite == null) sourceSite = ensureFactionLandSite(player, level, req, source, "SourceSite");
        double tx = sourceSite == null ? player.getX() : sourceSite.getX() + 0.5D;
        double tz = sourceSite == null ? player.getZ() : sourceSite.getZ() + 0.5D;
        int radius = 6;
        String action = step.label();
        int seconds = -1;

        BlockPos targetSite = target == null ? null : cachedFactionLandSite(req, target, "TargetSite");
        if (target != null && targetSite == null) targetSite = ensureFactionLandSite(player, level, req, target, "TargetSite");

        if ("TRAINING".equals(type) || "TRAIN_RECRUIT".equals(type) || "TRAIN_OFFICER".equals(type)) {
            UUID id = req.hasUUID("TargetEntity") ? req.getUUID("TargetEntity") : FactionRequestMissionManager.rosterId(req, "TrainingTeam", 0);
            AmbientFighterEntity trainee = id == null ? null : FactionRequestMissionManager.loadedResident(level, source, id);
            if (trainee != null) {
                tx = trainee.getX(); tz = trainee.getZ(); radius = 6;
                action = "Meet " + trainee.getFighterName() + " and start the sanctioned spar.";
                status = requestProgress(req) + " • target: " + trainee.getFighterName();
            } else {
                radius = 120; action = "Approach the training area; the exact real trainee will be marked automatically.";
            }
            return new ObjectiveSnapshot(step, status, tx, tz, radius, action, -1);
        }

        if ("PATROL".equals(type) || "RECOVERY".equals(type)) {
            ListTag route = req.getList("PatrolRoute", Tag.TAG_COMPOUND);
            BlockPos meet = patrolRendezvous(req);
            if (!req.getBoolean("Started") && meet != null) {
                tx = meet.getX() + 0.5D; tz = meet.getZ() + 0.5D; radius = 10;
            } else if (!route.isEmpty()) {
                int leg = Math.max(0, Math.min(route.size() - 1, req.getInt("PatrolLeg")));
                CompoundTag p = route.getCompound(leg);
                tx = p.getInt("X") + 0.5D; tz = p.getInt("Z") + 0.5D; radius = 10;
            } else {
                radius = 120; action = "Approach the marked rendezvous; the route is created automatically.";
            }
            UUID leaderId = req.hasUUID("PatrolLeader") ? req.getUUID("PatrolLeader")
                    : FactionRequestMissionManager.rosterId(req, "Patrol", 0);
            AmbientFighterEntity leader = leaderId == null ? null : FactionRequestMissionManager.loadedResident(level, source, leaderId);
            if (req.getBoolean("PatrolContactActive") && !req.getBoolean("PatrolContactResolved")) {
                WorldFaction contactFaction = data.byId(req.getString("PatrolContactFaction"));
                AmbientFighterEntity enemy = nearestActiveRoster(level, req, "PatrolContact", player);
                if (enemy != null) { tx = enemy.getX(); tz = enemy.getZ(); radius = 12; action = "Route contact: help the patrol against " + enemy.getFighterName() + "."; }
                int er = contactFaction == null ? 0 : rosterRemaining(level, req, contactFaction, "PatrolContact");
                int et = FactionRequestMissionManager.rosterSize(req, "PatrolContact");
                int ar = rosterRemaining(level, req, source, "Patrol");
                int at = FactionRequestMissionManager.rosterSize(req, "Patrol");
                status = "Natural route contact • rivals active " + er + "/" + et + " • patrol active " + ar + "/" + at;
            } else if (req.getBoolean("PatrolAmbushTriggered") && !req.getBoolean("PatrolAmbushResolved")) {
                WorldFaction threat = data.byId(req.getString("PatrolAmbushFaction"));
                AmbientFighterEntity enemy = nearestActiveRoster(level, req, "PatrolAmbush", player);
                if (enemy != null) { tx = enemy.getX(); tz = enemy.getZ(); radius = 12; action = "Defend the patrol: engage " + enemy.getFighterName() + "."; }
                int er = threat == null ? 0 : rosterRemaining(level, req, threat, "PatrolAmbush");
                int et = FactionRequestMissionManager.rosterSize(req, "PatrolAmbush");
                int ar = rosterRemaining(level, req, source, "Patrol");
                int at = FactionRequestMissionManager.rosterSize(req, "Patrol");
                status = "Ambush in progress • rivals active " + er + "/" + et + " • patrol active " + ar + "/" + at;
            } else if (leader != null) {
                double ld = Math.sqrt(player.distanceToSqr(leader));
                if (!req.getBoolean("Started") || ld > 34.0D) {
                    tx = leader.getX(); tz = leader.getZ(); radius = 10;
                    action = !req.getBoolean("Started") ? "Meet patrol leader " + leader.getFighterName() + "; patrol starts automatically."
                            : "Regroup with " + leader.getFighterName() + " and stay within 38 blocks.";
                } else if (req.getInt("PatrolCheckpointHold") > 0) {
                    int need = Math.max(100, req.getInt("PatrolCheckpointNeed"));
                    action = "Secure this checkpoint with " + leader.getFighterName() + " for "
                            + secondsRemaining(req.getInt("PatrolCheckpointHold"), need) + "s before moving on.";
                } else action = req.getBoolean("PatrolAirborne")
                        ? "Fly with " + leader.getFighterName() + " and stay within 38 blocks until the marked checkpoint."
                        : "Stay within 38 blocks of " + leader.getFighterName() + " and reach the marked checkpoint together.";
                status = requestProgress(req) + " • leader: " + leader.getFighterName();
            } else if (leaderId != null) {
                String leaderName = req.getString("PatrolLeaderName");
                if (leaderName.isBlank()) leaderName = FactionRequestMissionManager.residentName(level, source, leaderId);
                action = "Stay at the marked patrol rendezvous. " + (leaderName.isBlank() ? "The patrol" : leaderName)
                        + " is assembling here; do not search the surrounding territory.";
                status = "Patrol assembly paused until a patrol member reaches the rendezvous • no hidden withdrawal timer";
            }
            return new ObjectiveSnapshot(step, status, tx, tz, radius, action, -1);
        }

        if ("RECON".equals(type)) {
            if (req.contains("ReconX")) { tx = req.getDouble("ReconX"); tz = req.getDouble("ReconZ"); radius = 24; }
            else if (targetSite != null) { tx = targetSite.getX() + 0.5D; tz = targetSite.getZ() + 0.5D; radius = 140; }
            if (req.getBoolean("ReconDetected")) {
                seconds = secondsRemaining(req.getInt("ReconCalm"), 120);
                action = "Break line of sight. Stay hidden for " + seconds + "s to shake the patrol.";
            } else {
                seconds = secondsRemaining(req.getInt("Presence"), requiredPresence(req, 1800));
                action = "Stay within 24 blocks and remain unseen for " + seconds + "s.";
            }
            status = requestProgress(req) + " • suspicion " + Math.max(0, req.getInt("ReconSuspicion")) + "%";
            return new ObjectiveSnapshot(step, status, tx, tz, radius, action, seconds);
        }

        if ("MERCENARY_INTEL".equals(type)) {
            int stage = Math.max(1, req.getInt("SeriesStage"));
            UUID giverId = req.hasUUID("IntelMissionGiver") ? req.getUUID("IntelMissionGiver")
                    : FactionRequestMissionManager.rosterId(req, "IntelMissionGiver", 0);
            AmbientFighterEntity giver = giverId == null ? null : FactionRequestMissionManager.loadedResident(level, source, giverId);
            if (!req.getBoolean("IntelBriefed")) {
                if (giver != null) { tx = giver.getX(); tz = giver.getZ(); radius = 4; }
                else if (req.contains("IntelMissionGiverX")) { tx = req.getDouble("IntelMissionGiverX"); tz = req.getDouble("IntelMissionGiverZ"); radius = 8; }
                String name = req.getString("IntelMissionGiverName");
                action = "Meet " + (name.isBlank() ? "the marked mission giver" : name) + " and right-click them for the briefing.";
                status = requestProgress(req);
                return new ObjectiveSnapshot(step, status, tx, tz, radius, action, -1);
            } else if (stage == 1) {
                if (req.contains("ObjectiveX")) { tx = req.getDouble("ObjectiveX"); tz = req.getDouble("ObjectiveZ"); radius = 10; }
                else if (targetSite != null) { tx = targetSite.getX() + 0.5D; tz = targetSite.getZ() + 0.5D; radius = 140; }
                if (req.getBoolean("IntelDetected")) {
                    seconds = secondsRemaining(req.getInt("IntelCalm"), 120);
                    action = "SPOTTED: break real line of sight for " + seconds + "s, then return to this listening position.";
                } else {
                    seconds = secondsRemaining(req.getInt("Presence"), requiredPresence(req, 600));
                    action = "Stay within 10 blocks and listen unseen for " + seconds + "s. On exposed ground, crouch and use distance; a real guard seeing you breaks the current attempt.";
                }
                status = requestProgress(req);
            } else {
                if (giver != null) { tx = giver.getX(); tz = giver.getZ(); radius = 4; }
                else if (req.contains("IntelMissionGiverX")) { tx = req.getDouble("IntelMissionGiverX"); tz = req.getDouble("IntelMissionGiverZ"); radius = 8; }
                String name = req.getString("IntelMissionGiverName");
                action = "Return to " + (name.isBlank() ? "the same mission giver" : name) + " and right-click them to deliver the report.";
                status = requestProgress(req) + (req.getInt("IntelCasualties") > 0
                        ? " • compromised by " + req.getInt("IntelCasualties") + " casualty/casualties" : " • clean collection");
                seconds = -1;
            }
            return new ObjectiveSnapshot(step, status, tx, tz, radius, action, seconds);
        }

        if ("MERCENARY_SABOTAGE".equals(type)) {
            if (!req.getBoolean("SabotageDone") && req.contains("SabotageRoute", Tag.TAG_LIST)) {
                ListTag route = req.getList("SabotageRoute", Tag.TAG_COMPOUND);
                if (!route.isEmpty()) {
                    int node = Math.max(0, Math.min(route.size() - 1, req.getInt("SabotageNode")));
                    CompoundTag point = route.getCompound(node); tx = point.getInt("X") + 0.5D; tz = point.getInt("Z") + 0.5D; radius = 12;
                    seconds = secondsRemaining(req.getInt("SabotageWork"), 200);
                    action = "Stay within 12 blocks and disrupt this route point for " + seconds + "s.";
                }
            } else if (req.getBoolean("SabotageDone") && targetSite != null) {
                double cx = targetSite.getX() + 0.5D, cz = targetSite.getZ() + 0.5D;
                double awayX = player.getX() - cx, awayZ = player.getZ() - cz;
                double mag = Math.max(1.0D, Math.sqrt(awayX * awayX + awayZ * awayZ));
                tx = cx + awayX / mag * 330.0D; tz = cz + awayZ / mag * 330.0D; radius = 30;
                seconds = secondsRemaining(req.getInt("Presence"), Math.max(120, req.getInt("ReturnNeed")));
                action = "Get at least 300 blocks clear, then remain clear for " + seconds + "s.";
            } else if (targetSite != null) {
                tx = targetSite.getX() + 0.5D; tz = targetSite.getZ() + 0.5D; radius = 140;
                action = "Approach the guarded supply corridor; exact disruption points will appear automatically.";
            }
            return new ObjectiveSnapshot(step, status, tx, tz, radius, action, seconds);
        }

        if ("MERCENARY_HUNT".equals(type)) {
            UUID id = req.hasUUID("TargetEntity") ? req.getUUID("TargetEntity")
                    : FactionRequestMissionManager.rosterId(req, "HuntTarget", 0);
            AmbientFighterEntity hunted = id == null || target == null ? null : FactionRequestMissionManager.loadedResident(level, target, id);
            if (hunted != null) {
                tx = hunted.getX(); tz = hunted.getZ(); radius = 12;
                action = "Neutralize " + hunted.getFighterName() + ". They are the marked contract target.";
                status = "Target: " + hunted.getFighterName() + " • " + target.name() + " • no substitute";
            } else if (target != null && id != null) {
                BlockPos last = FactionRequestMissionManager.residentLastPos(level, target, id);
                if (last != null) { tx = last.getX() + 0.5D; tz = last.getZ() + 0.5D; radius = 24; action = "Go to " + FactionRequestMissionManager.residentName(level, target, id) + "'s last confirmed position."; }
            } else if (targetSite != null) {
                tx = targetSite.getX() + 0.5D; tz = targetSite.getZ() + 0.5D; radius = 140;
                action = "Approach the target faction sector; the contracted resident will be marked automatically.";
            }
            return new ObjectiveSnapshot(step, status, tx, tz, radius, action, -1);
        }

        if ("MERCENARY_EXTRACTION".equals(type)) {
            UUID id = req.hasUUID("TargetEntity") ? req.getUUID("TargetEntity")
                    : FactionRequestMissionManager.rosterId(req, "ExtractionOperative", 0);
            AmbientFighterEntity agent = id == null ? null : FactionRequestMissionManager.loadedResident(level, source, id);
            if (!req.getBoolean("Started")) {
                if (agent != null) { tx = agent.getX(); tz = agent.getZ(); radius = 8; action = "Reach operative " + agent.getFighterName() + "; extraction starts automatically."; }
                else if (targetSite != null) { tx = targetSite.getX() + 0.5D; tz = targetSite.getZ() + 0.5D; radius = 140; action = "Approach the pickup sector; the operative will be marked automatically."; }
            } else if (req.getBoolean("ExtractionPursuitTriggered") && !req.getBoolean("ExtractionPursuitResolved") && target != null) {
                AmbientFighterEntity pursuer = nearestActiveRoster(level, req, "ExtractionPursuit", player);
                boolean closePursuer = FactionRequestMissionManager.loadedActiveRoster(level, req, "ExtractionPursuit").stream()
                        .anyMatch(f -> f.distanceToSqr(player) <= 160.0D * 160.0D);
                if (pursuer != null) { tx = pursuer.getX(); tz = pursuer.getZ(); radius = 14; }
                if (closePursuer) {
                    seconds = 10;
                    action = "A pursuer is within 160 blocks. Create distance; the 10-second escape counter starts only when every pursuer is farther away.";
                } else {
                    seconds = secondsRemaining(req.getInt("ExtractionPursuitCalm"), 200);
                    action = "Keep every pursuer 160+ blocks away for " + seconds + "s, or break their fixed roster.";
                }
                status = "Pursuit active • pursuers " + rosterRemaining(level, req, target, "ExtractionPursuit") + "/" + FactionRequestMissionManager.rosterSize(req, "ExtractionPursuit")
                        + (agent != null ? " • operative " + (int)Math.round(Math.sqrt(player.distanceToSqr(agent))) + " blocks from you" : "");
            } else {
                if (sourceSite != null) { tx = sourceSite.getX() + 0.5D; tz = sourceSite.getZ() + 0.5D; radius = 150; }
                seconds = secondsRemaining(req.getInt("Presence"), Math.min(40, Math.max(20, req.getInt("ReturnNeed"))));
                action = "Bring the same operative into the marked home zone. Handoff completes in " + seconds + "s once both of you are there.";
            }
            return new ObjectiveSnapshot(step, status, tx, tz, radius, action, seconds);
        }

        if ("CAPTURE".equals(type) || "ELITE_CAPTURE".equals(type)) {
            if (req.getBoolean("CaptureSecured")) {
                if (sourceSite != null) { tx = sourceSite.getX() + 0.5D; tz = sourceSite.getZ() + 0.5D; radius = 65; }
                UUID id = req.hasUUID("TargetEntity") ? req.getUUID("TargetEntity") : FactionRequestMissionManager.rosterId(req, "CaptureTarget", 0);
                AmbientFighterEntity captive = id == null || target == null ? null : FactionRequestMissionManager.loadedResident(level, target, id);
                action = "Escort " + (captive == null ? "the same prisoner" : captive.getFighterName()) + " into the marked faction handoff alive.";
                if (captive != null) status = "Prisoner: " + captive.getFighterName() + " • " + (int)Math.round(Math.sqrt(player.distanceToSqr(captive))) + " blocks from you";
            } else {
                UUID id = req.hasUUID("TargetEntity") ? req.getUUID("TargetEntity") : FactionRequestMissionManager.rosterId(req, "CaptureTarget", 0);
                AmbientFighterEntity captureTarget = id == null || target == null ? null : FactionRequestMissionManager.loadedResident(level, target, id);
                if (captureTarget != null) {
                    tx = captureTarget.getX(); tz = captureTarget.getZ();
                    double captureDx = captureTarget.getX() - player.getX(), captureDz = captureTarget.getZ() - player.getZ();
                    int exactDistance = (int)Math.round(Math.sqrt(captureDx * captureDx + captureDz * captureDz));
                    if (!req.getBoolean("Started")) {
                        radius = 18;
                        action = "Approach " + captureTarget.getFighterName() + " within 18 blocks. Friendly Fist becomes the objective after you reach them.";
                        status = "Exact target: " + captureTarget.getFighterName() + " • distance " + exactDistance + " blocks • confrontation range 18";
                    } else {
                        radius = 12;
                        action = "Use Friendly Fist on " + captureTarget.getFighterName() + ". Do not kill the target.";
                        status = "Capture target: " + captureTarget.getFighterName() + " • distance " + exactDistance + " blocks • Friendly Fist range 12";
                    }
                } else if (id != null && target != null) {
                    BlockPos last = FactionRequestMissionManager.residentLastPos(level, target, id);
                    if (last != null) {
                        tx = last.getX() + 0.5D; tz = last.getZ() + 0.5D; radius = 48;
                        action = "Go to " + FactionRequestMissionManager.residentName(level, target, id) + "'s last confirmed position so that exact persistent resident can load.";
                        status = "Recovering exact capture target • no substitute resident will be chosen";
                    }
                } else if (targetSite != null) { tx = targetSite.getX() + 0.5D; tz = targetSite.getZ() + 0.5D; radius = 140; action = "Approach the marked target sector while the exact persistent resident is being resolved."; }
            }
            return new ObjectiveSnapshot(step, status, tx, tz, radius, action, -1);
        }

        if ("RESCUE".equals(type)) {
            PrisonerWorldData.Prisoner prisoner = PrisonerWorldData.get(level).byId(req.getString("Prisoner"));
            WorldFaction prisonerFaction = prisoner == null ? null : data.byId(prisoner.victimFactionId);
            AmbientFighterEntity captive = prisoner == null || prisoner.entityId == null || prisonerFaction == null ? null
                    : FactionRequestMissionManager.loadedResident(level, prisonerFaction, prisoner.entityId);
            if (req.getBoolean("RescueFreed")) {
                if (sourceSite != null) { tx = sourceSite.getX() + 0.5D; tz = sourceSite.getZ() + 0.5D; radius = 65; }
                action = "Escort " + (captive == null ? "the freed resident" : captive.getFighterName()) + " home alive.";
                if (captive != null) status = "Freed resident: " + captive.getFighterName() + " • " + (int)Math.round(Math.sqrt(player.distanceToSqr(captive))) + " blocks from you";
            } else if (captive != null) {
                tx = captive.getX(); tz = captive.getZ(); radius = 12;
                action = req.getBoolean("Started") ? "Break the fixed guard roster and free " + captive.getFighterName() + "."
                        : "Reach captive " + captive.getFighterName() + "; guards are the real captor members.";
            } else if (targetSite != null) { tx = targetSite.getX() + 0.5D; tz = targetSite.getZ() + 0.5D; radius = 140; action = "Approach the prison sector; the exact captive will be marked."; }
            return new ObjectiveSnapshot(step, status, tx, tz, radius, action, -1);
        }

        if ("PROTECT".equals(type)) {
            UUID oid = req.hasUUID("TargetEntity") ? req.getUUID("TargetEntity") : FactionRequestMissionManager.rosterId(req, "ProtectedOfficer", 0);
            AmbientFighterEntity officer = oid == null ? null : FactionRequestMissionManager.loadedResident(level, source, oid);
            if (!req.getBoolean("Started")) {
                if (officer != null) { tx = officer.getX(); tz = officer.getZ(); radius = 10; action = "Meet officer " + officer.getFighterName() + "; the attack starts automatically."; }
                else { radius = 120; action = "Approach the protection point; the exact officer will be marked automatically."; }
            } else if (target != null) {
                AmbientFighterEntity attacker = nearestActiveRoster(level, req, "ProtectAttackers", player);
                if (attacker != null) { tx = attacker.getX(); tz = attacker.getZ(); radius = 14; action = "Protect " + (officer == null ? "the officer" : officer.getFighterName()) + " and engage " + attacker.getFighterName() + "."; }
                status = "Attackers active " + rosterRemaining(level, req, target, "ProtectAttackers") + "/" + FactionRequestMissionManager.rosterSize(req, "ProtectAttackers")
                        + (officer == null ? "" : " • officer HP " + Math.max(0, Math.round(officer.getHealth())) + "/" + Math.round(officer.getMaxHealth()));
            }
            return new ObjectiveSnapshot(step, status, tx, tz, radius, action, -1);
        }

        if ("WAR_READINESS".equals(type)) {
            int stage = Math.max(1, req.getInt("SeriesStage"));
            if (stage == 1) return new ObjectiveSnapshot(step, status, tx, tz, 120, "Complete the supply handoff to the assigned receiver.", -1);
            if (stage == 2) {
                if (req.contains("ReadinessReconX")) { tx = req.getDouble("ReadinessReconX"); tz = req.getDouble("ReadinessReconZ"); radius = 24; }
                else if (targetSite != null) { tx = targetSite.getX() + 0.5D; tz = targetSite.getZ() + 0.5D; radius = 140; }
                if (req.getBoolean("ReadinessSpotted")) {
                    seconds = secondsRemaining(req.getInt("ReadinessCalm"), 100);
                    action = "Break line of sight from the deployment scouts for " + seconds + "s.";
                } else {
                    seconds = secondsRemaining(req.getInt("Presence"), requiredPresence(req, 600));
                    action = "Stay within 24 blocks and observe deployment for " + seconds + "s without being identified.";
                }
                return new ObjectiveSnapshot(step, status, tx, tz, radius, action, seconds);
            }
            // Stage 3 intentionally falls through to fixed-force combat handling below.
        }

        if ("RECOVERY_LINE".equals(type)) {
            if (Math.max(1, req.getInt("SeriesStage")) <= 1)
                return new ObjectiveSnapshot(step, status, tx, tz, 120, "Complete the supply handoff to the assigned receiver.", -1);
            if (req.contains("RecoveryX")) { tx = req.getDouble("RecoveryX"); tz = req.getDouble("RecoveryZ"); radius = 10; }
            UUID lid = FactionRequestMissionManager.rosterId(req, "RecoveryTeam", 0);
            AmbientFighterEntity leader = lid == null ? null : FactionRequestMissionManager.loadedResident(level, source, lid);
            action = "Escort the same recovery team to checkpoint " + (Math.min(3, req.getInt("RecoveryCheckpoints") + 1)) + "/3"
                    + (leader == null ? "." : " and stay within 36 blocks of " + leader.getFighterName() + ".");
            return new ObjectiveSnapshot(step, status, tx, tz, radius, action, -1);
        }

        boolean fixedCombat = "ASSAULT".equals(type) || "RETALIATION".equals(type) || "DEFEND".equals(type)
                || "FRONTLINE".equals(type) || ("WAR_READINESS".equals(type) && Math.max(1, req.getInt("SeriesStage")) >= 3);
        if (fixedCombat && target != null) {
            BlockPos staging = "DEFEND".equals(type) ? sourceSite : targetSite;
            if (!req.getBoolean("Started")) {
                if (staging != null) { tx = staging.getX() + 0.5D; tz = staging.getZ() + 0.5D; }
                radius = 120;
                action = "Enter the 120-block staging zone. The real fixed rosters assemble automatically—do not search the wilderness for them.";
                int alliesPresent = FactionRequestMissionManager.loadedRoster(level, req, "Allies").size();
                int enemiesPresent = FactionRequestMissionManager.loadedRoster(level, req, "Enemies").size();
                status = "Staging • real participants present: allies " + alliesPresent + " • enemies " + enemiesPresent
                        + " • operation starts automatically once both sides are physically assembled";
            } else {
                AmbientFighterEntity enemy = nearestActiveRoster(level, req, "Enemies", player);
                if (enemy != null) {
                    tx = enemy.getX(); tz = enemy.getZ(); radius = 14;
                    action = "Engage " + enemy.getFighterName() + " — nearest active member of the fixed enemy roster.";
                } else {
                    BlockPos last = rosterLastPosition(level, req, target, "Enemies");
                    if (last != null) { tx = last.getX() + 0.5D; tz = last.getZ() + 0.5D; radius = 24; action = "Move to the last confirmed position of the remaining enemy roster."; }
                }
                int er = rosterRemaining(level, req, target, "Enemies"), et = FactionRequestMissionManager.rosterSize(req, "Enemies");
                int ar = rosterRemaining(level, req, source, "Allies"), at = FactionRequestMissionManager.rosterSize(req, "Allies");
                status = "Enemy force active " + er + "/" + et + " • allied force active " + ar + "/" + at + " • no timer/no refills";
            }
            return new ObjectiveSnapshot(step, status, tx, tz, radius, action, -1);
        }

        return new ObjectiveSnapshot(step, status, tx, tz, radius, action, seconds);
    }

    private record MissionStep(int index, int total, String label, String next) {
        MissionStep { index = Math.max(0, index); total = Math.max(0, total); label = label == null ? "" : label; next = next == null ? "" : next; }
    }

    private static MissionStep missionStep(CompoundTag req) {
        if (req == null) return new MissionStep(0, 0, "", "");
        String type = req.getString("Type");
        if (type.isBlank()) return new MissionStep(0, 0, "", "");

        if ("MERCENARY_INTEL".equals(type)) {
            int points = Math.max(3, req.getInt("IntelPointsNeed"));
            int total = points + 2; // in-person briefing + listening points + in-person report
            if (!req.getBoolean("IntelBriefed")) {
                String giver = req.getString("IntelMissionGiverName");
                return new MissionStep(1, total,
                        "Meet " + (giver.isBlank() ? "the marked mission giver" : giver) + " for the in-person briefing",
                        "Right-click them; infiltration does not begin before the briefing");
            }
            int stage = Math.max(1, req.getInt("SeriesStage"));
            if (stage == 1) {
                int point = Math.min(points, Math.max(1, req.contains("IntelPoint") ? req.getInt("IntelPoint") : 1));
                FactionMissionFlavor.IntelScenario sc = FactionMissionFlavor.intelScenario(req.getInt("IntelScenarioSeed"), point);
                return new MissionStep(point + 1, total,
                        "Listening " + point + "/" + points + ": " + sc.name(),
                        point < points ? "Then relocate to listening position " + (point + 1) + "/" + points : "Then return to the same mission giver");
            }
            String giver = req.getString("IntelMissionGiverName");
            return new MissionStep(total, total,
                    "Return to " + (giver.isBlank() ? "the mission giver" : giver) + " and right-click to report",
                    "Final handoff — no hidden timer or automatic completion");
        }

        if ("PATROL".equals(type) || "RECOVERY".equals(type)) {
            int legs = req.contains("PatrolRoute", Tag.TAG_LIST)
                    ? Math.max(1, req.getList("PatrolRoute", Tag.TAG_COMPOUND).size()) : ("RECOVERY".equals(type) ? 4 : 5);
            if (!req.contains("PatrolRoute", Tag.TAG_LIST))
                return new MissionStep(1, legs + 1, "Go to the marked patrol rendezvous", "The patrol route appears automatically");
            if (!req.getBoolean("Started")) {
                String leader = req.getString("PatrolLeaderName");
                return new MissionStep(1, legs + 1, leader.isBlank() ? "Meet the marked patrol leader" : "Meet " + leader + ", your patrol leader",
                        "The patrol starts automatically when you regroup");
            }
            int leg = Math.max(0, Math.min(legs - 1, req.getInt("PatrolLeg")));
            if (req.getBoolean("PatrolContactActive") && !req.getBoolean("PatrolContactResolved"))
                return new MissionStep(2 + leg, legs + 1, "Defend the patrol from the rival fighters encountered on the route", "Then reform with the same surviving patrol");
            if (req.getBoolean("PatrolAmbushTriggered") && !req.getBoolean("PatrolAmbushResolved"))
                return new MissionStep(2 + leg, legs + 1, "Defend the patrol: break the marked fixed ambush roster", "Then reform with the same surviving patrol");
            String movement = req.getBoolean("PatrolAirborne") ? "Fly with the patrol" : "Follow the patrol";
            return new MissionStep(2 + leg, legs + 1,
                    movement + " to checkpoint " + (leg + 1) + "/" + legs + " and stay within 38 blocks of the leader",
                    leg + 1 < legs ? "Then continue with the same patrol to checkpoint " + (leg + 2) + "/" + legs : "Finish the route with the surviving patrol");
        }

        if ("RECON".equals(type)) {
            if (!req.contains("ReconX"))
                return new MissionStep(1, 2, "Go to the marked reconnaissance sector", "An exact observation point appears automatically");
            return new MissionStep(2, 2,
                    req.getBoolean("ReconDetected") ? "Lose the marked patrol and break line of sight" : "Stay within 24 blocks of the exact observation point and remain unseen",
                    "Finish the visible observation timer");
        }

        if ("MERCENARY_EXTRACTION".equals(type)) {
            if (!req.getBoolean("Started"))
                return new MissionStep(1, 3, "Reach the marked real operative", "Pickup begins automatically when you get close");
            if (req.getBoolean("ExtractionPursuitTriggered") && !req.getBoolean("ExtractionPursuitResolved"))
                return new MissionStep(2, 3, "Protect the operative from the marked fixed pursuit roster", "Break them or shake the pursuit, then continue home");
            return new MissionStep(3, 3, "Escort the same operative into the marked employer home zone", "Handoff completes in at most 2 visible seconds");
        }

        if ("MERCENARY_SABOTAGE".equals(type)) {
            if (!req.getBoolean("Started"))
                return new MissionStep(1, 5, "Go to the marked guarded supply corridor", "Three exact disruption points will appear");
            if (!req.getBoolean("SabotageDone")) {
                int node = Math.min(3, Math.max(1, req.getInt("SabotageNode") + 1));
                return new MissionStep(1 + node, 5, "Disrupt route point " + node + "/3 from within 12 blocks",
                        node < 3 ? "Then move to disruption point " + (node + 1) + "/3" : "Then escape the target faction area");
            }
            return new MissionStep(5, 5, "Get 300+ blocks clear of the target faction and remain clear", "Visible escape timer completes the contract");
        }

        if ("MERCENARY_HUNT".equals(type))
            return req.getBoolean("Started")
                    ? new MissionStep(2, 2, "Neutralize the marked named resident — they are the contract target", "No substitute target can appear")
                    : new MissionStep(1, 2, "Reach the marked contract target", "Then confirm and engage that same resident");

        if ("WAR_READINESS".equals(type)) {
            int stage = Math.max(1, req.getInt("SeriesStage"));
            return switch (stage) {
                case 1 -> new MissionStep(1, 3, "Deliver the requested stockpile to the marked receiver", "Then perform field reconnaissance");
                case 2 -> new MissionStep(2, 3, req.getBoolean("ReadinessSpotted") ? "Break line of sight from the deployment scouts" : "Observe the marked scout point from within 24 blocks",
                        "Then join the fixed mobilization battle");
                default -> new MissionStep(3, 3, "Break the marked enemy mobilization team", "No timer and no reinforcement waves");
            };
        }

        if ("RECOVERY_LINE".equals(type)) {
            int stage = Math.max(1, req.getInt("SeriesStage"));
            return stage <= 1
                    ? new MissionStep(1, 2, "Deliver recovery supplies to the marked receiver", "Then escort the fixed recovery team")
                    : new MissionStep(2, 2, "Escort the same recovery team through all 3 marked checkpoints", "Stay within 36 blocks of the leader");
        }

        if (isSupplyRequest(req)) {
            boolean complete = req.getInt("FoodProgress") >= req.getInt("FoodNeed") && req.getInt("OreProgress") >= req.getInt("OreNeed");
            String receiver = req.getString("SupplyReceiverName");
            if (receiver.isBlank()) receiver = "the named faction receiver";
            return complete
                    ? new MissionStep(2, 2, "Go to " + receiver + " and right-click them to deliver the supplies", "The handoff is immediate")
                    : new MissionStep(1, 2, "Gather the listed food/materials (partial hand-ins are allowed)", "Give what you have to " + receiver + " by right-clicking them");
        }

        if ("TRAIN_RECRUIT".equals(type) || "TRAIN_OFFICER".equals(type) || "TRAINING".equals(type)) {
            int done = req.getList("TrainingCompleted", Tag.TAG_STRING).size();
            int total = Math.max(1, FactionRequestMissionManager.rosterSize(req, "TrainingTeam") > 0
                    ? FactionRequestMissionManager.rosterSize(req, "TrainingTeam") : requiredCount(req, type));
            return new MissionStep(Math.min(total, done + 1), total,
                    "Meet and spar the marked real trainee " + Math.min(total, done + 1) + "/" + total,
                    done + 1 < total ? "Then the next exact trainee will be marked" : "Finish the final sanctioned spar");
        }

        if ("CAPTURE".equals(type) || "ELITE_CAPTURE".equals(type)) {
            if (req.getBoolean("CaptureSecured"))
                return new MissionStep(3, 3, "Escort the same subdued prisoner into the marked faction handoff", "The exact prisoner must arrive alive");
            if (req.getBoolean("Started") || req.getBoolean("CaptureEncountered"))
                return new MissionStep(2, 3, "Use Friendly Fist on the marked real target — do not kill them", "Then escort that same prisoner home");
            return new MissionStep(1, 3, "Reach within 18 blocks of the exact marked capture target", "Then Friendly Fist that same resident alive");
        }

        if ("RESCUE".equals(type)) {
            if (req.getBoolean("RescueFreed"))
                return new MissionStep(3, 3, "Escort the same freed resident into the marked home handoff", "The exact rescued person must arrive alive");
            if (req.getBoolean("Started"))
                return new MissionStep(2, 3, "Break the fixed real guard roster around the marked captive", "Then escort the captive home");
            return new MissionStep(1, 3, "Reach the exact marked persistent captive", "Then break their fixed guard roster");
        }

        if ("PROTECT".equals(type))
            return req.getBoolean("Started")
                    ? new MissionStep(2, 2, "Protect the named officer and break the marked fixed attacking roster", "The officer must remain active and alive")
                    : new MissionStep(1, 2, "Meet the exact marked officer", "The attack begins automatically");

        if ("FRONTLINE".equals(type))
            return req.getBoolean("Started")
                    ? new MissionStep(2, 2, "Break the marked fixed frontline enemy roster", "No waves, no timer, no refills")
                    : new MissionStep(1, 2, "Enter the marked 120-block frontline staging zone", "Your allies assemble automatically");

        if ("DEFEND".equals(type))
            return req.getBoolean("Started")
                    ? new MissionStep(2, 2, "Break the marked fixed attacking roster before your defenders break", "No timer and no replacements")
                    : new MissionStep(1, 2, "Enter the marked 120-block defense staging zone", "Real defenders and attackers assemble automatically");

        if ("ASSAULT".equals(type))
            return req.getBoolean("Started")
                    ? new MissionStep(2, 2, "War Strike: break the marked fixed enemy roster", "Follow the compass to the nearest active enemy — no timer/no refills")
                    : new MissionStep(1, 2, "Enter the marked 120-block strike staging zone", "The real strike and defense rosters assemble automatically");

        if ("RETALIATION".equals(type))
            return req.getBoolean("Started")
                    ? new MissionStep(2, 2, "Counterstrike: break the marked fixed enemy roster", "Follow the compass to the nearest active enemy")
                    : new MissionStep(1, 2, "Enter the marked 120-block retaliation staging zone", "Real combatants assemble automatically");

        return new MissionStep(req.getBoolean("Started") ? 2 : 1, 2,
                req.getBoolean("Started") ? "Complete the marked concrete mission objective" : "Go to the exact marked mission point",
                req.getBoolean("Started") ? "Follow the current objective text — no hidden requirement" : "The mission advances automatically when its shown condition is met");
    }

    private static void applyOperationSimulationOutcome(FactionWorldData data, WorldFaction ally, WorldFaction enemy,
                                                        String type, int enemyDown, int enemyTotal, long now) {
        if (data == null || ally == null || enemy == null || type == null) return;
        float decisiveness = enemyTotal <= 0 ? 0.5F : Math.max(0.25F, Math.min(1.0F, enemyDown / (float) enemyTotal));
        switch (type) {
            case "DEFEND" -> {
                data.adjustMomentum(ally, 0.024F * decisiveness); data.adjustMomentum(enemy, -0.020F * decisiveness);
                int loss = Math.min(data.supplies(enemy), Math.max(1, Math.round(2.0F * decisiveness))); if (loss > 0) data.addSupplies(enemy, -loss);
            }
            case "ASSAULT" -> {
                data.adjustMomentum(ally, 0.026F * decisiveness); data.adjustMomentum(enemy, -0.040F * decisiveness);
                int loss = Math.min(data.supplies(enemy), Math.max(2, Math.round(5.0F * decisiveness))); if (loss > 0) data.addSupplies(enemy, -loss);
                data.adjustRelation(ally, enemy, -4, now, null);
            }
            case "RETALIATION" -> {
                data.adjustMomentum(ally, 0.020F * decisiveness); data.adjustMomentum(enemy, -0.030F * decisiveness);
                data.adjustRelation(ally, enemy, -6, now, null);
            }
            default -> { }
        }
    }

    /**
     * Shared deadlock guard for request rosters that are mandatory. A request may wait briefly for an existing
     * resident/chunk to load, but it may never leave the player on an impossible objective indefinitely.
     */
    private static boolean mandatoryRosterReady(ServerPlayer player, ServerLevel level, CompoundTag req,
                                                WorldFaction faction, String rosterName) {
        return mandatoryRosterReady(player, level, req, faction, rosterName, null);
    }

    /**
     * R29 assembly contract: loading is never a mission-failure timer. Before lock, physically assembled real
     * residents become the roster; unavailable reservations are trimmed. After lock, unload/death/yield belongs to
     * mission outcome logic and can never trigger a recovery withdrawal.
     */
    private static boolean mandatoryRosterReady(ServerPlayer player, ServerLevel level, CompoundTag req,
                                                WorldFaction faction, String rosterName, BlockPos activationPoint) {
        if (FactionRequestMissionManager.rosterLocked(req, rosterName)) return FactionRequestMissionManager.rosterSize(req, rosterName) > 0;
        if (activeResidentCount(FactionWorldData.get(level), faction, true) == 0L) {
            notify(player, "Request unavailable: " + faction.name() + " has no real persistent combatant for this role. No fake replacement will be created.");
            clearRequest(player);
            return false;
        }
        if (activationPoint != null && distanceSqTo(activationPoint, player) > 180.0D * 180.0D) {
            req.remove("RosterAssemblySince." + rosterName);
            saveRequest(player, req);
            return false;
        }

        int loaded = FactionRequestMissionManager.loadedRoster(level, req, rosterName).size();
        long now = level.getServer().overworld().getGameTime();
        String sinceKey = "RosterAssemblySince." + rosterName;
        if (loaded <= 0) {
            req.remove(sinceKey);
            if (!req.getBoolean("RosterAssemblyNotified." + rosterName)) {
                req.putBoolean("RosterAssemblyNotified." + rosterName, true);
                notify(player, "The real " + faction.name() + " participants are assembling at the marked objective. Stay here; the quest will not withdraw on a hidden loading timer.");
            }
            saveRequest(player, req);
            return false;
        }

        if (!req.contains(sinceKey, Tag.TAG_LONG)) {
            req.putLong(sinceKey, now);
            saveRequest(player, req);
            return false;
        }
        // One second of stable presence lets additional already-real residents join without ever holding the player
        // hostage for a full desired quota. Then the actually-present cast becomes the fixed no-refill roster.
        if (now - req.getLong(sinceKey) < 20L) { saveRequest(player, req); return false; }
        int finalCount = FactionRequestMissionManager.trimUnlockedRosterToLoaded(level, req, rosterName);
        if (finalCount <= 0) { req.remove(sinceKey); saveRequest(player, req); return false; }
        req.remove(sinceKey); req.remove("RosterAssemblyNotified." + rosterName);
        return true;
    }


    private static boolean ensureCombatOperation(ServerPlayer player, ServerLevel level, CompoundTag req,
                                                 WorldFaction ally, WorldFaction enemy, BlockPos anchor,
                                                 int allyDesired, int enemyDesired) {
        FactionRequestMissionManager.ensureRoster(player, level, req, ally,
                "Allies", Math.max(1, allyDesired), anchor, FactionRequestMissionManager.SIDE_ALLY,
                FactionRequestMissionManager.ROLE_COMBAT, f -> !f.isNonCombatant());
        FactionRequestMissionManager.ensureRoster(player, level, req, enemy,
                "Enemies", Math.max(1, enemyDesired), anchor, FactionRequestMissionManager.SIDE_ENEMY,
                FactionRequestMissionManager.ROLE_COMBAT, f -> !f.isNonCombatant());

        if (activeResidentCount(FactionWorldData.get(level), ally, true) == 0L
                || activeResidentCount(FactionWorldData.get(level), enemy, true) == 0L) {
            notify(player, "Operation unavailable: one side has no real persistent combatant. No stand-in force will be created.");
            clearRequest(player);
            return false;
        }

        int allyLoaded = FactionRequestMissionManager.loadedRoster(level, req, "Allies").size();
        int enemyLoaded = FactionRequestMissionManager.loadedRoster(level, req, "Enemies").size();
        if (allyLoaded <= 0 || enemyLoaded <= 0) {
            if (!req.getBoolean("CombatAssemblyNotified")) {
                req.putBoolean("CombatAssemblyNotified", true);
                notify(player, "Real allied and enemy participants are assembling at staging. Stay in the marked zone; there is no 45-second withdrawal timer and no need to search for them.");
            }
            req.remove("CombatAssemblyReadySince"); saveRequest(player, req); return false;
        }

        long now = level.getServer().overworld().getGameTime();
        if (!req.contains("CombatAssemblyReadySince", Tag.TAG_LONG)) {
            req.putLong("CombatAssemblyReadySince", now); saveRequest(player, req); return false;
        }
        if (now - req.getLong("CombatAssemblyReadySince") < 20L) { saveRequest(player, req); return false; }

        int alliesFinal = FactionRequestMissionManager.trimUnlockedRosterToLoaded(level, req, "Allies");
        int enemiesFinal = FactionRequestMissionManager.trimUnlockedRosterToLoaded(level, req, "Enemies");
        if (alliesFinal <= 0 || enemiesFinal <= 0) { req.remove("CombatAssemblyReadySince"); saveRequest(player, req); return false; }
        FactionRequestMissionManager.lockRoster(req, "Allies");
        FactionRequestMissionManager.lockRoster(req, "Enemies");
        req.remove("CombatAssemblyNotified"); req.remove("CombatAssemblyReadySince");
        if (!req.getBoolean("MissionStartSpoken")) {
            AmbientFighterEntity speaker = FactionRequestMissionManager.firstLoaded(level, req, "Allies");
            if (speaker != null) speaker.speak(FactionRequestDialogue.start(req.getString("Type"), level.getGameTime() ^ speaker.getUUID().getLeastSignificantBits()), 82);
            req.putBoolean("MissionStartSpoken", true);
        }
        return true;
    }


    private static boolean combatOperationResolved(ServerLevel level, CompoundTag req, WorldFaction enemy) {
        FactionRequestMissionManager.applyMoraleBreak(level, req, enemy, "Enemies");
        return FactionRequestMissionManager.rosterNeutralized(level, req, enemy, "Enemies");
    }

    private static void speakMissionSuccess(ServerLevel level, CompoundTag req, String rosterName) {
        AmbientFighterEntity speaker = FactionRequestMissionManager.firstLoaded(level, req, rosterName);
        if (speaker != null) speaker.speak(FactionRequestDialogue.success(req.getString("Type"),
                level.getGameTime() ^ speaker.getUUID().getMostSignificantBits()), 84);
    }

    private static AmbientFighterEntity reserveRealMember(ServerPlayer player, ServerLevel level, CompoundTag req,
                                                           WorldFaction faction, String rosterName, BlockPos anchor,
                                                           String side, String role,
                                                           java.util.function.Predicate<AmbientFighterEntity> filter) {
        AmbientFighterEntity fighter = FactionRequestMissionManager.reserveOne(player, level, req, faction, rosterName,
                anchor, side, role, filter);
        if (fighter == null && activeResidentCount(FactionWorldData.get(level), faction, false) == 0L) {
            notify(player, "Request withdrawn: " + faction.name() + " has no real persistent resident available for this role. No fake quest NPC will be created.");
            clearRequest(player);
            return null;
        }
        if (fighter == null) {
            boolean nearRecoveryPoint = anchor == null || distanceSqTo(anchor, player) <= 180.0D * 180.0D;
            UUID reservedId = FactionRequestMissionManager.rosterId(req, rosterName, 0);
            BlockPos lastKnown = reservedId == null ? null : FactionRequestMissionManager.residentLastPos(level, faction, reservedId);
            if (!nearRecoveryPoint && lastKnown != null) nearRecoveryPoint = distanceSqTo(lastKnown, player) <= 96.0D * 96.0D;
            if (nearRecoveryPoint && !req.getBoolean("RealMemberWait." + rosterName)) {
                req.putBoolean("RealMemberWait." + rosterName, true);
                notify(player, "Recovering the real faction member at the marked area. The request is paused—not failing—and will never spawn a duplicate.");
            }
            saveRequest(player, req);
        } else {
            req.remove("RealMemberWait." + rosterName); req.remove("RealMemberWaitSince." + rosterName);
        }
        return fighter;
    }

    private static boolean isRealFactionMember(ServerLevel level, WorldFaction faction, AmbientFighterEntity fighter) {
        return FactionRequestMissionManager.isRealResident(level, faction, fighter);
    }

    private static void markStorySides(ServerLevel level, ServerPlayer player, WorldFaction ally, WorldFaction enemy) {
        for (AmbientFighterEntity fighter : level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(96.0D), f -> f.isAlive() && f.isFactionMember() && !f.isRegionalPresence())) {
            if (ally.id().equals(fighter.getFactionId())) fighter.setStoryRole(AmbientFighterEntity.STORY_ALLY);
            else if (enemy.id().equals(fighter.getFactionId())) fighter.setStoryRole(AmbientFighterEntity.STORY_ENEMY);
        }
    }

    private static void setRequest(ServerPlayer player,String type,WorldFaction source,WorldFaction target,String prisoner,long expires){
        CompoundTag root=root(player), req=new CompoundTag(); req.putString("Type",type); req.putString("Source",source.id()); req.putString("Target",target.id());
        req.putString("Prisoner",prisoner==null?"":prisoner); req.putLong("Expires",expires); FactionRequestMissionManager.requestId(req); initializeRequirements(req, type, player.getRandom()); root.put("Active",req); save(player,root);
        PlayerWorldManager.discoverFaction(player,source); PlayerWorldManager.discoverFaction(player,target);
    }

    private static void initializeRequirements(CompoundTag req, String type) {
        initializeRequirements(req, type, RandomSource.create());
    }

    private static void initializeRequirements(CompoundTag req, String type, RandomSource random) {
        if (req == null) return;
        RandomSource r = random == null ? RandomSource.create() : random;
        req.remove("Required"); req.remove("FoodNeed"); req.remove("OreNeed"); req.remove("PresenceNeed"); req.remove("ReturnNeed");
        req.remove("WaveNeed"); req.remove("EscortNeed"); req.remove("RepReward"); req.remove("SupplyReward");
        if ("TRAIN_RECRUIT".equals(type)) req.putInt("Required", between(r, 1, 3));
        else if ("TRAIN_OFFICER".equals(type)) req.putInt("Required", between(r, 2, 4));
        if ("PROVISIONS".equals(type)) req.putInt("FoodNeed", between(r, 18, 52));
        else if ("MATERIALS".equals(type)) req.putInt("OreNeed", between(r, 12, 36));
        else if ("WAR_STOCKPILE".equals(type)) { req.putInt("FoodNeed", between(r, 32, 64)); req.putInt("OreNeed", between(r, 20, 48)); }
        else if ("REPARATIONS".equals(type)) { req.putInt("FoodNeed", between(r, 16, 40)); req.putInt("OreNeed", between(r, 8, 24)); }
        if ("PATROL".equals(type)) req.putInt("PresenceNeed", between(r, 2400, 4800));
        else if ("RECOVERY".equals(type)) req.putInt("PresenceNeed", between(r, 2600, 5000));
        else if ("RECON".equals(type)) req.putInt("PresenceNeed", between(r, 1800, 3200));
        else if ("MERCENARY_INTEL".equals(type)) {
            req.putInt("PresenceNeed", between(r, 500, 850));
            req.putInt("ReturnNeed", between(r, 180, 320));
            req.putInt("IntelPointsNeed", between(r, 3, 4));
            req.putInt("IntelScenarioSeed", r.nextInt(1000000));
            req.putInt("SeriesStage", 1); req.putInt("SeriesLength", 3);
        }
        else if ("MERCENARY_EXTRACTION".equals(type)) { req.putInt("ReturnNeed", between(r, 200, 400)); req.putInt("EscortNeed", between(r, 2, 5)); req.putBoolean("ExtractionPursuitPlanned", r.nextFloat() < 0.45F); }
        else if ("PROTECT".equals(type)) req.putInt("PresenceNeed", between(r, 2200, 4200));
        else if ("RETALIATION".equals(type)) req.putInt("PresenceNeed", between(r, 2000, 3800));
        else if ("DEFEND".equals(type) || "ASSAULT".equals(type)) req.putInt("PresenceNeed", between(r, 1800, 3600));
        else if ("FRONTLINE".equals(type)) { req.putInt("PresenceNeed", between(r, 1200, 2400)); }
        else if ("ELITE_CAPTURE".equals(type)) req.putInt("EscortNeed", between(r, 2, 5));
        else if ("MERCENARY_HUNT".equals(type)) req.putInt("EscortNeed", between(r, 0, 3));
        else if ("MERCENARY_SABOTAGE".equals(type)) { req.putInt("PresenceNeed", between(r, 1600, 3000)); req.putInt("ReturnNeed", between(r, 160, 300)); req.putInt("EscortNeed", between(r, 3, 5)); req.putInt("SupplyHit", between(r, 8, 18)); }
        if (isSupplyType(type)) initializeExactSupplyBasket(req, type, r);
        if ("WAR_READINESS".equals(type)) {
            req.putInt("SeriesStage", 1); req.putInt("SeriesLength", 3);
            req.putInt("FoodNeed", between(r, 28, 58)); req.putInt("OreNeed", between(r, 14, 36)); req.putInt("PresenceNeed", between(r, 1400, 2600));
        } else if ("RECOVERY_LINE".equals(type)) {
            req.putInt("SeriesStage", 1); req.putInt("SeriesLength", 2);
            req.putInt("FoodNeed", between(r, 18, 38)); req.putInt("OreNeed", r.nextBoolean() ? 0 : between(r, 8, 20)); req.putInt("PresenceNeed", between(r, 2200, 4200));
        }
        int rep = switch (type) {
            case "PROVISIONS" -> between(r, 5, 9); case "MATERIALS" -> between(r, 7, 12); case "REPARATIONS" -> between(r, 8, 14);
            case "WAR_STOCKPILE" -> between(r, 13, 18); case "PATROL", "TRAIN_RECRUIT", "RECON" -> between(r, 6, 10);
            case "MERCENARY_INTEL" -> between(r, 10, 14); case "MERCENARY_EXTRACTION" -> between(r, 14, 18); case "MERCENARY_HUNT" -> between(r, 15, 20);
            case "MERCENARY_SABOTAGE" -> between(r, 11, 15);
            case "TRAIN_OFFICER", "DEFEND", "ASSAULT", "PROTECT" -> between(r, 10, 14); case "RECOVERY" -> between(r, 9, 13);
            case "RETALIATION", "CAPTURE" -> between(r, 12, 16); case "RESCUE" -> between(r, 18, 25);
            case "FRONTLINE", "ELITE_CAPTURE" -> between(r, 16, 21); case "WAR_READINESS" -> between(r, 18, 24); case "RECOVERY_LINE" -> between(r, 12, 17);
            default -> between(r, 5, 9);
        };
        int supply = switch (type) {
            case "PROVISIONS" -> between(r, 10, 18); case "MATERIALS" -> between(r, 8, 15); case "REPARATIONS" -> between(r, 9, 16);
            case "WAR_STOCKPILE" -> between(r, 24, 36); case "WAR_READINESS" -> between(r, 28, 42); case "RECOVERY_LINE" -> between(r, 16, 26); default -> 0;
        };
        req.putInt("RepReward", rep); if (supply > 0) req.putInt("SupplyReward", supply);
    }

    private static int between(RandomSource r, int min, int max) { return min + r.nextInt(Math.max(1, max - min + 1)); }

    private static void copyOfferParameters(CompoundTag offer, CompoundTag active) {
        for (String key : new String[]{"Required","FoodNeed","OreNeed","PresenceNeed","ReturnNeed","EscortNeed","SupplyHit","RepReward","SupplyReward","SeriesStage","SeriesLength","TargetRole","TargetName","NeedReason","UrgencyScore","UrgencyTier","DemandState","SupplyItems","IntelPointsNeed","IntelScenarioSeed","ExtractionPursuitPlanned","SupplyReceiverEntity","SupplyReceiverName","SupplyReceiverRole","SupplyReceiverX","SupplyReceiverY","SupplyReceiverZ"})
            if (offer.contains(key)) active.put(key, offer.get(key).copy());
    }

    /** One-tick display grace so every field timer visibly begins at 0 seconds. */
    private static void resetPresence(CompoundTag req) { if (req != null) req.putInt("Presence", -20); }

    private static int requiredPresence(CompoundTag req, int fallback) { int v = req.getInt("PresenceNeed"); return v > 0 ? v : fallback; }
    private static int requiredCount(CompoundTag req, String type) { int v=req.getInt("Required"); if(v>0)return v; return "TRAIN_RECRUIT".equals(type)||"TRAIN_OFFICER".equals(type)?2:1; }

    private static CompoundTag ensureOffer(ServerPlayer player, WorldFaction faction, ServerLevel level, long now) {
        CompoundTag root = root(player); CompoundTag offers = root.getCompound(OFFERS); CompoundTag current = offers.getCompound(faction.id());
        long wallNow = System.currentTimeMillis();
        long refreshAt = current.getLong("RefreshAtMs");
        if (refreshAt > wallNow && (current.getString("Type").isBlank() || offerStillValid(current, faction, level, now))) return current;
        CompoundTag generated = generateOffer(player, faction, level, now, wallNow);
        offers.put(faction.id(), generated); root.put(OFFERS, offers); save(player, root); return generated;
    }

    private static CompoundTag generateOffer(ServerPlayer player, WorldFaction faction, ServerLevel level, long now, long wallNow) {
        FactionWorldData data = FactionWorldData.get(level);
        CompoundTag offer = new CompoundTag(); List<String> candidates = offerTypes(player, faction, level, now);
        long nextCheck = wallNow + boardRecheckDelayMs(faction);
        if (candidates.isEmpty()) {
            offer.putLong("RefreshAtMs", nextCheck);
            offer.putString("NeedReason", "No current faction pressure requires outside help.");
            return offer;
        }

        // Even when a legitimate need exists, factions do not automatically outsource it. Severity controls how
        // likely the board is to post external work, which keeps quest availability sparse and prevents easy REP loops.
        int urgency = candidates.stream().mapToInt(t -> requestUrgency(t, faction, level, now)).max().orElse(0);
        int postingChance = Math.max(22, Math.min(94, urgency));
        boolean urgentConcreteEvent = candidates.contains("RESCUE");
        if (!urgentConcreteEvent && player.getRandom().nextInt(100) >= postingChance) {
            offer.putLong("RefreshAtMs", nextCheck);
            offer.putString("NeedReason", "The faction has pressure to handle, but has not chosen to hire outside help.");
            return offer;
        }

        String type;
        if (FactionManager.getReputation(player, faction) >= 70) {
            List<String> elite = candidates.stream().filter(t -> "Elite".equals(requestDifficulty(t))).toList();
            type = !elite.isEmpty() && player.getRandom().nextFloat() < 0.60F
                    ? elite.get(player.getRandom().nextInt(elite.size()))
                    : weightedNeedPick(candidates, faction, level, now, player.getRandom());
        } else type = weightedNeedPick(candidates, faction, level, now, player.getRandom());

        WorldFaction target = faction; String prisoner = "";
        if ("RESCUE".equals(type)) {
            PrisonerWorldData.Prisoner p = PrisonerWorldData.get(level).active().stream().filter(v -> v.entityId != null && faction.id().equals(v.victimFactionId)).findFirst().orElse(null);
            if (p != null) { prisoner = p.id; WorldFaction captor = FactionWorldData.get(level).byId(p.captorFactionId); if (captor != null) target = captor; }
        } else if (needsOtherFaction(type)) { WorldFaction resolved = resolveRequestTarget(level, faction, now); if (resolved != null) target = resolved; }
        offer.putString("Type", type); offer.putString("Target", target.id()); offer.putString("Prisoner", prisoner);
        initializeRequirements(offer, type, player.getRandom());
        applySupplyDemandContext(offer, type, faction, level, now);
        initializeTargetedContract(offer, type, player, faction, target, data);
        if (isSupplyType(type) && !bindSupplyReceiverIdentity(offer, faction, level)) {
            CompoundTag unavailable = new CompoundTag();
            unavailable.putLong("RefreshAtMs", nextCheck);
            unavailable.putString("NeedReason", "The faction needs supplies, but no member is currently available to receive a shipment.");
            return unavailable;
        }
        offer.putString("NeedReason", requestNeedReason(type, faction, target, data, now));
        offer.putLong("CreatedAtMs", wallNow); offer.putLong("RefreshAtMs", wallNow + offerLifetimeMs(faction)); return offer;
    }

    private static boolean offerStillValid(CompoundTag offer, WorldFaction faction, ServerLevel level, long now) {
        String type = offer.getString("Type");
        if (!isSupportedRequestType(type)) return false;
        FactionWorldData data = FactionWorldData.get(level);
        if (isSupplyType(type) && supplyReceiverRecord(offer, faction, level) == null) return false;
        if ("RESCUE".equals(type)) { PrisonerWorldData.Prisoner p = PrisonerWorldData.get(level).byId(offer.getString("Prisoner")); return p != null && p.active() && p.entityId != null && faction.id().equals(p.victimFactionId); }
        WorldFaction target = data.byId(offer.getString("Target"));
        if (needsOtherFaction(type) && (target == null || target.id().equals(faction.id()) || data.isExtinct(target))) return false;
        if ("MERCENARY_HUNT".equals(type) && target != null && FactionRole.byId(offer.getInt("TargetRole")) == FactionRole.LEADER && data.isLeaderKilled(target)) return false;
        return requestNeedStillExists(type, faction, target, data, level, now);
    }

    private static void pinOffer(ServerPlayer player, WorldFaction faction, String type, WorldFaction target, String prisoner) {
        if (!isSupportedRequestType(type)) return;
        CompoundTag root=root(player), offers=root.getCompound(OFFERS), current=offers.getCompound(faction.id()); long wallNow=System.currentTimeMillis();
        if (!current.getString("Type").isBlank() && wallNow < current.getLong("RefreshAtMs")) return;
        CompoundTag offer=new CompoundTag(); offer.putString("Type",type); offer.putString("Target",target==null?faction.id():target.id()); offer.putString("Prisoner",prisoner==null?"":prisoner);
        initializeRequirements(offer, type, player.getRandom());
        if (player.level() instanceof ServerLevel level) {
            applySupplyDemandContext(offer, type, faction, level, level.getServer().overworld().getGameTime());
            FactionWorldData data = FactionWorldData.get(level);
            initializeTargetedContract(offer, type, player, faction, target == null ? faction : target, data);
            if (isSupplyType(type) && !bindSupplyReceiverIdentity(offer, faction, level)) return;
            offer.putString("NeedReason", requestNeedReason(type, faction, target == null ? faction : target, data, level.getServer().overworld().getGameTime()));
        }
        offer.putLong("CreatedAtMs",wallNow); offer.putLong("RefreshAtMs",wallNow+offerLifetimeMs(faction)); offers.put(faction.id(),offer); root.put(OFFERS,offers); save(player,root);
    }

    private static long boardRecheckDelayMs(WorldFaction faction) {
        int spanMinutes = (int)((BOARD_RECHECK_MAX_MS - BOARD_RECHECK_MIN_MS) / 60000L) + 1;
        int minutes = (int)(BOARD_RECHECK_MIN_MS / 60000L) + Math.floorMod(faction.slot() * 17, spanMinutes);
        return minutes * 60000L;
    }

    private static long offerLifetimeMs(WorldFaction faction) {
        int spanMinutes = (int)((OFFER_LIFETIME_MAX_MS - OFFER_LIFETIME_MIN_MS) / 60000L) + 1;
        int minutes = (int)(OFFER_LIFETIME_MIN_MS / 60000L) + Math.floorMod(faction.slot() * 23, spanMinutes);
        return minutes * 60000L;
    }

    // Restored from the R24 known-good request-board contract. R25 still calls this whenever an
    // accepted, abandoned, invalid, or debug-refreshed offer must be removed from persistent state.
    private static void clearOffer(ServerPlayer player, WorldFaction faction) {
        CompoundTag root = root(player), offers = root.getCompound(OFFERS);
        offers.remove(faction.id());
        root.put(OFFERS, offers);
        save(player, root);
    }

    private static String weightedNeedPick(List<String> candidates, WorldFaction faction, ServerLevel level, long now, RandomSource random) {
        int total = 0;
        for (String type : candidates) total += Math.max(1, requestUrgency(type, faction, level, now));
        int roll = random.nextInt(Math.max(1, total));
        for (String type : candidates) {
            roll -= Math.max(1, requestUrgency(type, faction, level, now));
            if (roll < 0) return type;
        }
        return candidates.get(candidates.size() - 1);
    }

    private static int requestUrgency(String type, WorldFaction faction, ServerLevel level, long now) {
        FactionWorldData data = FactionWorldData.get(level);
        int supplies = data.supplies(faction); float momentum = data.momentum(faction);
        WorldFaction target = resolveRequestTarget(level, faction, now);
        float targetMomentum = target == null ? momentum : data.momentum(target);
        int targetSupplies = target == null ? supplies : data.supplies(target);
        boolean war = !data.warEnemies(faction, now).isEmpty();
        return switch (type) {
            case "RESCUE" -> 100;
            case "REPARATIONS" -> Math.min(82, 48 + Math.max(0, 58 - supplies));
            case "PROVISIONS" -> Math.min(88, 44 + Math.max(0, 54 - supplies));
            case "MATERIALS" -> Math.min(90, 48 + Math.max(0, 46 - supplies));
            case "WAR_STOCKPILE", "WAR_READINESS" -> Math.min(92, 62 + Math.max(0, 68 - supplies));
            case "RECOVERY", "RECOVERY_LINE" -> Math.min(92, 58 + Math.round(Math.max(0.0F, 0.94F - momentum) * 180.0F));
            case "DEFEND", "RETALIATION", "FRONTLINE" -> Math.min(92, 62 + Math.round(Math.max(0.0F, 1.04F - momentum) * 120.0F));
            case "ASSAULT" -> war ? 58 : 25;
            case "RECON", "MERCENARY_INTEL", "CAPTURE", "ELITE_CAPTURE" -> Math.min(88, 48 + Math.round(Math.max(0.0F, targetMomentum - momentum) * 120.0F));
            case "MERCENARY_HUNT", "MERCENARY_EXTRACTION" -> Math.min(90, 54 + Math.round(Math.max(0.0F, targetMomentum - momentum) * 135.0F));
            case "MERCENARY_SABOTAGE" -> Math.min(90, 48 + Math.max(0, targetSupplies - supplies));
            case "PROTECT" -> 64;
            case "TRAIN_RECRUIT", "TRAIN_OFFICER" -> 52;
            case "PATROL" -> war ? 60 : 42;
            default -> 45;
        };
    }

    private static boolean requestNeedStillExists(String type, WorldFaction faction, WorldFaction target, FactionWorldData data, ServerLevel level, long now) {
        int supplies = data.supplies(faction); float momentum = data.momentum(faction);
        boolean war = !data.warEnemies(faction, now).isEmpty();
        float targetMomentum = target == null ? momentum : data.momentum(target);
        int targetSupplies = target == null ? supplies : data.supplies(target);
        return switch (type) {
            case "REPARATIONS" -> supplies < 64 || momentum < 0.97F;
            case "PROVISIONS" -> supplies < (war ? 70 : 52);
            case "MATERIALS" -> supplies < (war ? 58 : 40);
            case "WAR_STOCKPILE" -> war && supplies < 78;
            case "WAR_READINESS" -> war && (supplies < 74 || momentum < 1.04F);
            case "RECOVERY" -> momentum < 0.94F || supplies < 32;
            case "RECOVERY_LINE" -> momentum < 0.94F || supplies < 42;
            case "DEFEND", "RETALIATION", "FRONTLINE", "PROTECT" -> war;
            case "ASSAULT" -> war && momentum > 1.02F;
            case "RECON" -> war && target != null;
            case "MERCENARY_INTEL" -> target != null && (war || targetMomentum > momentum + 0.01F || targetSupplies > supplies + 8);
            case "MERCENARY_HUNT" -> target != null && (war || targetMomentum > momentum + 0.05F);
            case "MERCENARY_SABOTAGE" -> target != null && targetSupplies >= 45 && targetSupplies >= supplies + 4;
            case "CAPTURE", "ELITE_CAPTURE" -> target != null && targetMomentum >= momentum;
            case "MERCENARY_EXTRACTION" -> war && target != null;
            case "TRAIN_RECRUIT" -> data.fighterPopulation(faction) < Math.max(3, (int)Math.ceil(data.population(faction) * 0.54D));
            case "TRAIN_OFFICER" -> momentum < 0.96F;
            case "PATROL" -> war || momentum < 0.97F || supplies < 38;
            default -> true;
        };
    }

    private static String requestNeedReason(String type, WorldFaction faction, WorldFaction target, FactionWorldData data, long now) {
        int supplies = data.supplies(faction); float momentum = data.momentum(faction);
        String rival = target == null || target.id().equals(faction.id()) ? "an opposing faction" : target.name();
        return switch (type) {
            case "RESCUE" -> "One of their actual members is currently being held prisoner.";
            case "REPARATIONS" -> "Their current shortages/recovery make material restitution useful enough to accept.";
            case "PROVISIONS" -> "Their food/supply reserve is low (" + supplies + ").";
            case "MATERIALS" -> "Their material reserve is strained (" + supplies + ").";
            case "WAR_STOCKPILE", "WAR_READINESS" -> "They are at war and their current readiness/supplies need outside support.";
            case "RECOVERY", "RECOVERY_LINE" -> "Their faction momentum has fallen to " + String.format(java.util.Locale.ROOT, "%.2f", momentum) + ".";
            case "DEFEND", "RETALIATION", "FRONTLINE", "PROTECT" -> "An active war is putting immediate pressure on their forces.";
            case "ASSAULT" -> "They currently have enough momentum and supplies to press an active war.";
            case "RECON", "MERCENARY_INTEL" -> rival + " currently represents a strategic information threat.";
            case "MERCENARY_HUNT" -> rival + " has become strong enough that they want a specific person removed.";
            case "MERCENARY_SABOTAGE" -> rival + " currently has a supply advantage worth disrupting.";
            case "CAPTURE", "ELITE_CAPTURE" -> rival + " is strong enough that taking a live prisoner has strategic value.";
            case "MERCENARY_EXTRACTION" -> "An active conflict makes leaving an embedded operative in " + rival + " territory too risky.";
            case "TRAIN_RECRUIT" -> "Their active fighter core has fallen below the faction's manpower target.";
            case "TRAIN_OFFICER" -> "Their weakened combat readiness calls for higher-level officer drills.";
            case "PATROL" -> "Current war/recovery pressure requires additional presence around their territory.";
            default -> "A current faction need has created this request.";
        };
    }

    private static void initializeTargetedContract(CompoundTag req, String type, ServerPlayer player, WorldFaction employer, WorldFaction target, FactionWorldData data) {
        if (req == null || player == null || employer == null || target == null || data == null) return;
        if ("MERCENARY_HUNT".equals(type)) {
            int roll = player.getRandom().nextInt(100);
            FactionRole role = roll < 26 ? FactionRole.MEMBER : roll < 61 ? FactionRole.ENFORCER : FactionRole.LIEUTENANT;
            boolean leaderEligible = FactionManager.getReputation(player, employer) >= 70
                    && !data.isLeaderKilled(target) && !data.isLeaderSpawned(target);
            if (leaderEligible && roll >= 92) role = FactionRole.LEADER;
            req.putInt("TargetRole", role.id());
            if (role == FactionRole.LEADER) {
                req.putString("TargetName", data.currentLeaderName(target));
                req.putInt("RepReward", between(player.getRandom(), 22, 28));
                req.putInt("EscortNeed", Math.max(3, req.getInt("EscortNeed")));
            } else if (role == FactionRole.LIEUTENANT) req.putInt("RepReward", between(player.getRandom(), 18, 23));
            else if (role == FactionRole.ENFORCER) req.putInt("RepReward", between(player.getRandom(), 16, 20));
        }
    }

    private static String mercenaryHuntDescription(CompoundTag req, WorldFaction employer, ServerLevel level) {
        WorldFaction target = FactionWorldData.get(level).byId(req.getString("Target"));
        FactionRole role = FactionRole.byId(req.getInt("TargetRole"));
        String targetName = req.getString("TargetName");
        String who = role == FactionRole.LEADER && !targetName.isBlank() ? targetName + ", leader of " + (target == null ? "the opposing faction" : target.name())
                : "a marked " + (target == null ? role.name().toLowerCase(java.util.Locale.ROOT) : target.roleTitle(role)) + " of " + (target == null ? "an opposing faction" : target.name());
        return "Work as a hired fighter for " + employer.name() + ": hunt down and eliminate " + who
                + ". The target is a real faction person; normal reputation, casualty, leadership and faction-relation consequences still apply.";
    }

    private static String mercenaryExtractionDescription(CompoundTag req, WorldFaction employer, ServerLevel level) {
        WorldFaction target = FactionWorldData.get(level).byId(req.getString("Target"));
        return "Enter " + (target == null ? "opposing territory" : target.name() + " territory") + ", locate an embedded " + employer.name()
                + " operative, and physically escort that same person home alive. Enemy guards contest the pickup and some extractions trigger a pursuit on the way out; killing them is optional, but abandoning the operative is not.";
    }

    private static String mercenaryIntelDescription(CompoundTag req, WorldFaction employer, ServerLevel level) {
        WorldFaction target = FactionWorldData.get(level).byId(req.getString("Target"));
        int seconds = Math.max(1, requiredPresence(req, 600) / 20);
        int points = Math.max(3, req.getInt("IntelPointsNeed"));
        return "Meet a real " + employer.name() + " mission giver first for an in-person briefing, then infiltrate "
                + (target == null ? "opposing territory" : target.name() + " territory") + ". Complete " + points
                + " changing listening scenarios—guard rotations, supply audits, courier relays, officer briefings, training rotations or perimeter signals—about "
                + seconds + " seconds each. Stay in the marked position while mission-specific dialogue plays. A real guard with line of sight spots you immediately and attacks; escape sight for six seconds before retrying that listening point. Killing observers is possible but carries normal faction consequences and reduces the payout. Return to the same mission giver and right-click them to report.";
    }

    private static String mercenarySabotageDescription(CompoundTag req, WorldFaction employer, ServerLevel level) {
        WorldFaction target = FactionWorldData.get(level).byId(req.getString("Target"));
        int supplyHit = Math.max(6, req.getInt("SupplyHit"));
        return "Take an interdiction contract for " + employer.name() + ": penetrate "
                + (target == null ? "opposing territory" : target.name() + " territory") + ", disrupt three separate points along a guarded supply route, and cost the rival up to "
                + supplyHit + " real faction supplies. The security roster is made of actual persistent members and never refills; stay covert, force a withdrawal, or fight through them, then escape at least 300 blocks clear.";
    }


    /**
     * Intelligence stages are meant to make the player physically relocate around the rival base, not stand in one
     * lucky blind spot for the whole contract. Prefer practical land at least 34 blocks from every earlier listening
     * marker. If terrain is unusually constrained, choose the safest/farthest viable candidate rather than failing the
     * accepted mission; the persisted history still prevents arbitrary rerolls when the chunk reloads.
     */
    private static BlockPos findIntelObservationPoint(ServerLevel level, BlockPos targetSite, CompoundTag req, RandomSource random, ServerPlayer player) {
        if (level == null || targetSite == null || req == null) return targetSite;
        RandomSource r = random == null ? RandomSource.create() : random;
        ListTag history = req.getList("IntelObservationHistory", Tag.TAG_COMPOUND);
        BlockPos best = null; double bestScore = -Double.MAX_VALUE;
        // Natural cover is preferred, but open terrain remains playable by pushing the listening
        // marker to the distant perimeter. Guards now remain around their base instead of being
        // teleported around the player's hiding marker.
        for (int attempt = 0; attempt < 28; attempt++) {
            BlockPos candidate = AmbientFighterSpawner.findSafeGroundAround(level, targetSite, r, 46, 72, 112);
            if (candidate == null || !isPracticalQuestLand(level, candidate)) continue;
            double minimum = Double.POSITIVE_INFINITY;
            for (int i = 0; i < history.size(); i++) {
                CompoundTag prior = history.getCompound(i);
                double dx = candidate.getX() - prior.getInt("X"), dz = candidate.getZ() - prior.getInt("Z");
                minimum = Math.min(minimum, dx * dx + dz * dz);
            }
            if (!history.isEmpty() && minimum < 34.0D * 34.0D) continue;
            double baseDistance = Math.sqrt(candidate.distSqr(targetSite));
            Vec3 from = Vec3.atCenterOf(targetSite).add(0.0D, 1.6D, 0.0D);
            Vec3 to = Vec3.atCenterOf(candidate).add(0.0D, 1.0D, 0.0D);
            HitResult terrain = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            boolean screened = terrain.getType() != HitResult.Type.MISS;
            double score = (screened ? 80.0D : 0.0D) + baseDistance;
            if (!screened && baseDistance < 58.0D) score -= 90.0D; // open ground needs distance
            if (score > bestScore) { bestScore = score; best = candidate; }
            if (screened && baseDistance >= 46.0D) return candidate;
        }
        if (best != null) return best;
        BlockPos distant = AmbientFighterSpawner.findSafeGroundAround(level, targetSite, r, 58, 84, 128);
        return distant != null && isPracticalQuestLand(level, distant) ? distant : targetSite;
    }

    private static void rememberIntelObservationPoint(CompoundTag req, BlockPos point) {
        if (req == null || point == null) return;
        ListTag history = req.getList("IntelObservationHistory", Tag.TAG_COMPOUND);
        CompoundTag stored = new CompoundTag();
        stored.putInt("X", point.getX());
        stored.putInt("Y", point.getY());
        stored.putInt("Z", point.getZ());
        history.add(stored);
        req.put("IntelObservationHistory", history);
    }

    private static boolean canCovertGuardSee(ServerLevel level, AmbientFighterEntity guard, ServerPlayer player, double baseSight) {
        if (level == null || guard == null || player == null || !guard.isAlive()) return false;
        double sight = Math.max(6.0D, baseSight * (player.isShiftKeyDown() ? 0.78D : 1.0D));
        double d2 = guard.distanceToSqr(player);
        if (d2 > sight * sight) return false;
        Vec3 from = guard.getEyePosition();
        Vec3 to = player.getEyePosition();
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() <= 0.0001D) return true;
        if (!guard.hasLineOfSight(player)) return false;
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, guard));
        if (hit.getType() != HitResult.Type.MISS) return false;
        if (d2 <= 7.0D * 7.0D) return true; // very close movement is hard to hide even outside the main view cone
        Vec3 look = guard.getLookAngle().normalize();
        Vec3 toward = delta.normalize();
        return look.dot(toward) >= 0.24D; // roughly a 152-degree forward field of view
    }


    /**
     * Every faction request uses a practical dry-land anchor rather than raw simulated roam coordinates.
     * SourceSite is the employer/home anchor; TargetSite is the opposing-faction/field anchor.
     * Existing active requests migrate lazily the first time they tick or render.
     */
    private static BlockPos cachedFactionLandSite(CompoundTag req, WorldFaction faction, String prefix) {
        if (req == null || faction == null || prefix == null || prefix.isBlank()) return null;
        String key = prefix + "For", xKey = prefix + "X", yKey = prefix + "Y", zKey = prefix + "Z";
        if (!faction.id().equals(req.getString(key)) || !req.contains(xKey) || !req.contains(yKey) || !req.contains(zKey)) return null;
        return new BlockPos((int)Math.floor(req.getDouble(xKey)), req.getInt(yKey), (int)Math.floor(req.getDouble(zKey)));
    }

    private static BlockPos ensureFactionLandSite(ServerPlayer player, ServerLevel level, CompoundTag req,
                                                  WorldFaction faction, String prefix) {
        if (player == null || level == null || req == null || faction == null || prefix == null || prefix.isBlank()) return null;
        String key = prefix + "For";
        String xKey = prefix + "X", yKey = prefix + "Y", zKey = prefix + "Z";
        if (faction.id().equals(req.getString(key)) && req.contains(xKey) && req.contains(yKey) && req.contains(zKey)) {
            // The anchor was validated when it was created. Trust the persisted point instead of re-probing terrain
            // every simulation tick; repeated height/ground checks around an unloaded site can themselves load chunks.
            return new BlockPos((int)Math.floor(req.getDouble(xKey)), req.getInt(yKey), (int)Math.floor(req.getDouble(zKey)));
        }

        BlockPos site = findPracticalFactionLand(level, faction, player);
        if (site == null) return null;
        req.putString(key, faction.id());
        req.putDouble(xKey, site.getX() + 0.5D);
        req.putInt(yKey, site.getY());
        req.putDouble(zKey, site.getZ() + 0.5D);
        saveRequest(player, req);
        return site;
    }

    private static BlockPos findPracticalFactionLand(ServerLevel level, WorldFaction faction, ServerPlayer player) {
        if (level == null || faction == null) return null;

        // Prefer an already-physical roster resident on genuine usable land. Never scan a giant world AABB or
        // synchronously probe/generate distant chunks merely because a request needs a marker.
        AmbientFighterEntity loaded = FactionRequestMissionManager.loadedAvailableResidents(level, faction,
                f -> isPracticalQuestLand(level, f.blockPosition())).stream()
                .min(java.util.Comparator.comparingDouble(f -> {
                    double dx = f.getX() - faction.roamX(), dz = f.getZ() - faction.roamZ();
                    return dx * dx + dz * dz;
                })).orElse(null);
        if (loaded != null) return loaded.blockPosition();

        // Next inspect only chunks that are already loaded around the simulated faction centre. getChunkNow is a
        // hard performance boundary: if the terrain is not already resident in memory, request setup does not wake it.
        int[] radii = {0, 32, 64, 96, 128};
        int phase = Math.floorMod(faction.id().hashCode(), 8);
        for (int radius : radii) {
            int samples = radius == 0 ? 1 : 8;
            for (int i = 0; i < samples; i++) {
                double angle = radius == 0 ? 0.0D : (Math.PI * 2.0D * i / samples) + phase * (Math.PI / 16.0D);
                int x = faction.roamX() + (int)Math.round(Math.cos(angle) * radius);
                int z = faction.roamZ() + (int)Math.round(Math.sin(angle) * radius);
                if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos candidate = new BlockPos(x, y, z);
                if (isPracticalQuestLand(level, candidate)) return candidate;
            }
        }

        // Last resort: pick practical land from the player's already-loaded neighbourhood. This intentionally trades
        // perfect simulated geography for a playable, lag-free mission instead of loading distant ocean/terrain.
        if (player != null) {
            BlockPos here = player.blockPosition();
            if (isPracticalQuestLand(level, here)) return here;
            for (int radius : new int[]{16, 32, 48, 72, 96}) {
                for (int i = 0; i < 12; i++) {
                    double angle = Math.PI * 2.0D * i / 12.0D;
                    int x = here.getX() + (int)Math.round(Math.cos(angle) * radius);
                    int z = here.getZ() + (int)Math.round(Math.sin(angle) * radius);
                    if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
                    int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (isPracticalQuestLand(level, candidate)) return candidate;
                }
            }
        }
        return null;
    }

    private static boolean isPracticalQuestLand(ServerLevel level, BlockPos center) {
        if (level == null || center == null || !AmbientFighterSpawner.isUsableGround(level, center)) return false;
        // Require surrounding walkable columns so NPC meetings cannot land on tiny ocean specks.
        int usable = 1;
        int[][] offsets = {{4,0},{-4,0},{0,4},{0,-4},{4,4},{4,-4},{-4,4},{-4,-4}};
        for (int[] off : offsets) {
            int x = center.getX() + off[0], z = center.getZ() + off[1];
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos sample = new BlockPos(x, y, z);
            if (Math.abs(sample.getY() - center.getY()) <= 6 && AmbientFighterSpawner.isUsableGround(level, sample)) usable++;
        }
        return usable >= 5;
    }

    private static double distanceSqTo(BlockPos pos, net.minecraft.world.entity.Entity entity) {
        if (pos == null || entity == null) return Double.POSITIVE_INFINITY;
        double dx = entity.getX() - (pos.getX() + 0.5D), dz = entity.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }

    private static BlockPos findFactionReportSite(ServerLevel level, WorldFaction faction, ServerPlayer player) {
        return findPracticalFactionLand(level, faction, player);
    }

    private static void objectiveToast(ServerPlayer player, String text) {
        if (player == null || text == null || text.isBlank()) return;
        player.displayClientMessage(Component.literal("◆ " + text).withStyle(ChatFormatting.AQUA), true);
    }

    private static void clearQuestAggression(java.util.List<AmbientFighterEntity> fighters, ServerPlayer player) {
        if (fighters == null || fighters.isEmpty()) return;
        for (AmbientFighterEntity fighter : fighters) {
            if (fighter == null || !fighter.isAlive()) continue;
            if (fighter.getTarget() == player) fighter.setTarget(null);
            fighter.getPersistentData().remove("LWRequestAlerted");
            if (fighter.getStoryRole() == AmbientFighterEntity.STORY_ENEMY) fighter.setStoryRole(AmbientFighterEntity.STORY_NONE);
            fighter.getNavigation().stop();
        }
    }

    private static String formatSeconds(long seconds) { long s=Math.max(0L,seconds); long m=s/60L; long r=s%60L; return m>0 ? m+"m "+r+"s" : r+"s"; }
    public static boolean isActiveRequestId(ServerPlayer player, UUID requestId) {
        if (player == null || requestId == null) return false;
        CompoundTag active = request(player);
        return isSupportedRequestType(active.getString("Type")) && active.hasUUID("RequestId") && requestId.equals(active.getUUID("RequestId"));
    }

    public static void recordMissionOutcome(ServerPlayer player, UUID requestId, UUID fighterId, boolean yielded) {
        if (player == null || requestId == null || fighterId == null) return;
        CompoundTag active = request(player);
        if (!isSupportedRequestType(active.getString("Type")) || !active.hasUUID("RequestId") || !requestId.equals(active.getUUID("RequestId"))) return;
        FactionRequestMissionManager.recordOutcome(active, fighterId, yielded);
        saveRequest(player, active);
    }

    private static CompoundTag request(ServerPlayer p){return root(p).getCompound("Active");}
    private static void saveRequest(ServerPlayer p,CompoundTag req){CompoundTag root=root(p);root.put("Active",req);save(p,root);}

    private static void finishSuccess(ServerPlayer player, CompoundTag req, int reputation, int supplies,
                                      String summary, String worldImpact) {
        if (player == null || req == null) return;
        WorldFaction source = player.level() instanceof ServerLevel level ? FactionWorldData.get(level).byId(req.getString("Source")) : null;
        StringBuilder reward = new StringBuilder();
        if (reputation > 0) reward.append("+").append(reputation).append(" faction reputation");
        if (supplies > 0) {
            if (reward.length() > 0) reward.append(" • ");
            reward.append("+").append(supplies).append(" faction supplies");
        }
        if (reward.length() == 0) reward.append("No direct material payout");
        LWNetwork.sendFactionRequestComplete(player, new FactionRequestCompletePacket(
                requestTitle(req), source == null ? "" : source.name(), summary, reward.toString(), worldImpact));
        clearRequest(player);
    }

    private static void finishSuccess(ServerPlayer player, CompoundTag req, int reputation,
                                      String summary, String worldImpact) {
        finishSuccess(player, req, reputation, 0, summary, worldImpact);
    }

    private static void clearRequest(ServerPlayer p){
        CompoundTag active = request(p);
        FactionRequestMissionManager.releaseRequestParticipants(p, active);
        if (p.level() instanceof ServerLevel level) {
            for (AmbientFighterEntity fighter : level.getEntitiesOfClass(AmbientFighterEntity.class, p.getBoundingBox().inflate(96.0D),
                    f -> !FactionRequestMissionManager.isAssigned(f) && f.getStoryRole() >= AmbientFighterEntity.STORY_ALLY && f.getStoryRole() <= AmbientFighterEntity.STORY_CAPTIVE))
                if (!fighter.isCaptive()) fighter.setStoryRole(AmbientFighterEntity.STORY_NONE);
        }
        CompoundTag root=root(p);root.remove("Active");save(p,root);
        LWNetwork.sendFactionRequestTracker(p, FactionRequestTrackerPacket.clear());
    }
    private static CompoundTag root(ServerPlayer p){CompoundTag d=p.getPersistentData();if(!d.contains(ROOT,net.minecraft.nbt.Tag.TAG_COMPOUND))d.put(ROOT,new CompoundTag());return d.getCompound(ROOT);}
    private static void save(ServerPlayer p,CompoundTag root){p.getPersistentData().put(ROOT,root);}
    private static AmbientFighterEntity entity(ServerLevel l,UUID id){var e=l.getEntity(id);return e instanceof AmbientFighterEntity f?f:null;}
    private static void notify(ServerPlayer p,String text){p.displayClientMessage(Component.literal("[Living World] ").withStyle(ChatFormatting.GOLD).append(Component.literal(text).withStyle(ChatFormatting.WHITE)),false);}

    @SubscribeEvent public static void onClone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer o&&e.getEntity() instanceof ServerPlayer c&&o.getPersistentData().contains(ROOT,net.minecraft.nbt.Tag.TAG_COMPOUND))c.getPersistentData().put(ROOT,o.getPersistentData().getCompound(ROOT).copy());}
}
