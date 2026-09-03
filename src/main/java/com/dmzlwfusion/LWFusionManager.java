package com.dmzlwfusion;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.common.stats.extras.ActionMode;
import com.dragonminez.server.util.FusionLogic;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;

import java.awt.Color;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Player -> Living World companion Metamoran bridge.
 *
 * The player remains a real DMZ fusion leader. We intentionally write the same
 * Status fields, FusionBonus key, appearance snapshot, FUSED effect and timer
 * that DMZ's FusionLogic writes for player-player fusion. The only custom part
 * is representing the NPC partner while the fusion is active.
 */
public final class LWFusionManager {
    private static final String ROOT = "DMZLWFusionBridge";
    private static final String DEBUG_ROOT = "DMZLWFusionDebug";
    private static final String PARTNER_ROOT = "DMZLWFusionPartnerState";
    private static final String TYPE_METAMORU = "METAMORU";
    private static final String DEBUG_PARTNER = "DebugPartner";
    private static final String DEBUG_ENABLED = "DebugEnabled";
    private static final double NATIVE_RANGE = 5.0D;
    private static final Map<UUID, PendingPlayerDance> PLAYER_DANCES = new HashMap<>();

    private LWFusionManager() {}

    /** Called only after DMZ's normal player-player attempt returned false. */
    public static boolean tryMetamoru(ServerPlayer player, StatsData stats) {
        return tryMetamoruInternal(player, stats, false, false);
    }

    /** Profile-selected player/NPC fusion. Uses normal DMZ requirements but targets the inspected fighter directly. */
    public static boolean tryMetamoruWithPartner(ServerPlayer player, StatsData stats, LivingEntity partner) {
        if (player == null || stats == null || partner == null || isActive(player)) return false;
        if (PLAYER_DANCES.containsKey(player.getUUID())) return true;
        if (stats.getStatus().isFused()) {
            player.displayClientMessage(Component.literal("You are already fused."), true);
            return false;
        }
        if (!stats.getSkills().hasSkill("fusion")) {
            player.displayClientMessage(Component.literal("You have not learned Fusion yet."), true);
            return false;
        }
        // The fighter-profile Fusion button is an explicit server-authoritative
        // request, so it starts the dance directly. Selecting/charging DMZ's
        // Fusion action is only required by the normal action-input route.
        if (stats.getCooldowns().hasCooldown("CombatTimer") || stats.getCooldowns().hasCooldown("FusionCooldown")) {
            player.displayClientMessage(Component.literal("Fusion is not ready yet."), true);
            return false;
        }
        if (player.distanceToSqr(partner) > 12.0D * 12.0D) return false;
        if (!LivingWorldCompat.isLivingWorldFighter(partner)) return false;
        if (!validatePlayerNpcPair(player, stats, partner, false)) return false;
        startPlayerNpcDance(player, stats, partner, false);
        return true;
    }

    /** Debug force now runs the visible native dance first. */
    public static boolean forceMetamoru(ServerPlayer player, StatsData stats) {
        return tryMetamoruInternal(player, stats, true, false);
    }

    /** Debug-only escape hatch for isolating the fusion state from presentation. */
    public static boolean forceMetamoruInstant(ServerPlayer player, StatsData stats) {
        return tryMetamoruInternal(player, stats, true, true);
    }

    private static boolean tryMetamoruInternal(ServerPlayer player, StatsData stats, boolean debugForce, boolean instant) {
        if (player == null || stats == null || isActive(player)) return false;
        if (PLAYER_DANCES.containsKey(player.getUUID())) return true;
        if (stats.getStatus().isFused()) { debug(player, "Rejected: DMZ already reports the player as fused."); return false; }

        if (!debugForce) {
            if (stats.getStatus().getSelectedAction() != ActionMode.FUSION) return false;
            if (stats.getResources().getActionCharge() < 100) return false;
            if (!stats.getSkills().hasSkill("fusion")) return false;
            if (stats.getCooldowns().hasCooldown("CombatTimer") || stats.getCooldowns().hasCooldown("FusionCooldown")) return false;
        }

        UUID partnerId = debugForce ? preferredDebugPartnerId(player) : LivingWorldCompat.companionId(player);
        if (partnerId == null) {
            debug(player, debugForce ? "Rejected: no debug-bound fighter or LW companion." : "Rejected: LW reports no active travelling companion.");
            return false;
        }

        LivingEntity partner = findNearbyPartner(player, partnerId, debugForce ? 16.0D : NATIVE_RANGE);
        if (partner == null) {
            debug(player, "Rejected: partner UUID exists but the fighter is not loaded/nearby.");
            return false;
        }
        if (!validatePlayerNpcPair(player, stats, partner, debugForce)) return false;

        if (instant) return completeMetamoru(player, stats, partner, debugForce);

        startPlayerNpcDance(player, stats, partner, debugForce);
        return true;
    }

    private static boolean validatePlayerNpcPair(ServerPlayer player, StatsData stats, LivingEntity partner, boolean debugForce) {
        String partnerName = LivingWorldCompat.fighterName(partner);
        if (hasPartnerBackup(partner)) {
            player.displayClientMessage(Component.literal(partnerName + " is already committed to another fusion sequence."), true);
            return false;
        }
        if (LivingWorldCompat.unavailableForFusion(partner)) {
            player.displayClientMessage(Component.translatable("message.dmzlwfusion.partner_busy", partnerName), true);
            debug(player, "Rejected: partner is defeated/captive/non-combatant/meditating/or actively targeting something.");
            return false;
        }
        if (LivingWorldCompat.hasActiveForm(partner)) {
            player.displayClientMessage(Component.translatable("message.dmzlwfusion.partner_form", partnerName), true);
            debug(player, "Rejected: partner has an active racial form or Kaioken.");
            return false;
        }
        if (stats.getStatus().isAndroidUpgraded()) {
            player.displayClientMessage(Component.translatable("message.dragonminez.fusion.android_cannot_fuse"), true);
            debug(player, "Rejected: player is Android-upgraded; matching native DMZ restriction.");
            return false;
        }

        String playerRace = safe(stats.getCharacter().getRace()).toLowerCase(Locale.ROOT);
        String npcRace = safe(LivingWorldCompat.raceId(partner)).toLowerCase(Locale.ROOT);
        if (playerRace.isBlank() || npcRace.isBlank()
                || (!playerRace.equals(npcRace) && !DmzRevampFusionCompat.allowsDifferentRaceMetamoru())) {
            player.displayClientMessage(Component.translatable("message.dragonminez.fusion.different_race"), true);
            debug(player, "Rejected: race mismatch player=" + playerRace + ", npc=" + npcRace + ".");
            return false;
        }

        LWFusionProfile profile = LWFusionProfile.from(partner);
        int playerTotal = Math.max(1, stats.getStats().getTotalStats());
        int partnerTotal = Math.max(1, profile.totalStats());
        double threshold = ConfigManager.getServerConfig().getGameplay().getMetamoruFusionThreshold();
        if (!debugForce && threshold > 0.0D) {
            double difference = Math.abs(playerTotal - partnerTotal) / (double) Math.max(playerTotal, partnerTotal);
            if (difference > threshold && !powerSyncEligible(stats, partner)) {
                player.displayClientMessage(Component.literal(
                        "Fusion partners must match their stats or bring current power levels within 35% so the stronger fighter can power down."), true);
                debug(player, "Rejected: native Metamoran stat gap exceeded and current BP is not synchronizable. playerStats="
                        + playerTotal + ", npcEquivalent=" + partnerTotal + ", playerBP="
                        + Math.max(1.0D, stats.getBattlePowerExact()) + ", npcBP=" + Math.max(1, LivingWorldCompat.battlePower(partner)));
                return false;
            }
            if (difference > threshold) {
                debug(player, "Accepted via current-power synchronization; persistent stats/BP remain untouched.");
            }
        }
        return true;
    }

    /** Secondary Dragon Ball-style dance eligibility. The stronger side may lower current output
     * to a reasonably-close partner for the dance without writing any permanent stats or BP. */
    private static boolean powerSyncEligible(StatsData stats, LivingEntity partner) {
        if (stats == null || partner == null) return false;
        double playerBp = Math.max(1.0D, stats.getBattlePowerExact());
        double partnerBp = Math.max(1.0D, LivingWorldCompat.battlePower(partner));
        double gap = Math.abs(playerBp - partnerBp) / Math.max(playerBp, partnerBp);
        return gap <= 0.35D;
    }

    private static void startPlayerNpcDance(ServerPlayer player, StatsData stats, LivingEntity partner, boolean debugForce) {
        boolean playerLeft = player.getUUID().compareTo(partner.getUUID()) <= 0;
        float yaw = player.getYRot();
        storePartnerBackup(player, partner);
        preparePartnerForDance(partner);
        positionPlayerNpcDance(player, partner, yaw, playerLeft);
        FusionAnimations.trigger(player, playerLeft);
        FusionAnimations.trigger(partner, !playerLeft);
        stats.getResources().setActionCharge(0);
        sync(player);
        PLAYER_DANCES.put(player.getUUID(), new PendingPlayerDance(
                player.getUUID(), partner.getUUID(), FusionAnimations.DANCE_TICKS, yaw, playerLeft, debugForce,
                player.getX(), player.getY(), player.getZ()));
        debug(player, "Native fusion dance started with " + LivingWorldCompat.fighterName(partner) + " for " + FusionAnimations.DANCE_TICKS + " ticks.");
    }

    private static boolean completeMetamoru(ServerPlayer player, StatsData stats, LivingEntity partner, boolean debugForce) {
        if (partner == null || !partner.isAlive() || partner.level() != player.level()) return false;
        String partnerName = LivingWorldCompat.fighterName(partner);
        LWFusionProfile profile = LWFusionProfile.from(partner);
        int playerTotal = Math.max(1, stats.getStats().getTotalStats());
        int partnerTotal = Math.max(1, profile.totalStats());
        DMZEvent.FusionEvent event = new DMZEvent.FusionEvent(player, partner, DMZEvent.FusionEvent.FusionType.METAMORU);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            debug(player, "Rejected at dance completion: another mod cancelled DMZ FusionEvent.");
            restoreOrphanPartner(partner);
            return false;
        }
        beginMetamoru(player, stats, partner, partnerName, profile, playerTotal, partnerTotal, debugForce);
        return true;
    }

    private static void beginMetamoru(ServerPlayer player, StatsData stats, LivingEntity partner,
                                      String partnerName, LWFusionProfile profile,
                                      int playerTotal, int partnerTotal, boolean debugForce) {
        Status status = stats.getStatus();
        Character character = stats.getCharacter();

        CompoundTag originalAppearance = new CompoundTag();
        character.saveAppearance(originalAppearance);
        status.setOriginalAppearance(originalAppearance);

        String fusionName = buildFusionName(player.getGameProfile().getName(), partnerName, TYPE_METAMORU);
        status.setFused(true);
        status.setFusionLeader(true);
        status.setFusionPartnerUUID(partner.getUUID());
        status.setFusionType(TYPE_METAMORU);
        status.setFusionName(fusionName);

        int configuredDuration = ConfigManager.getServerConfig().getGameplay().getFusionDurationSeconds() * 20;
        int skillLevel = Math.max(debugForce ? 1 : 0, stats.getSkills().getSkillLevel("fusion"));
        int maxSkill = Math.max(1, stats.getSkills().getMaxSkillLevel("fusion"));
        int perLevel = configuredDuration / maxSkill;
        // DMZ uses: (configured duration / max Fusion level) * average partner
        // Fusion level. An LW companion has no fake hidden skill track, so a
        // travelling companion is treated as synchronized to the player's
        // learned dance level. Equal levels make the native average == player level.
        int duration = Math.max(1, perLevel * skillLevel);
        status.setFusionTimer(duration);

        mixAppearance(character, partner);
        if (!DmzRevampFusionCompat.applyIfEnabled(stats, profile, playerTotal)) {
            applyNativeFusionBonus(stats, profile, playerTotal, partnerTotal);
        }

        CompoundTag session = new CompoundTag();
        session.putBoolean("Active", true);
        session.putUUID("Partner", partner.getUUID());
        session.putString("PartnerName", partnerName);
        session.putString("FusionName", fusionName);
        session.putString("Type", TYPE_METAMORU);
        session.putBoolean("PartnerInvisible", partner.isInvisible());
        session.putBoolean("PartnerInvulnerable", partner.isInvulnerable());
        session.putBoolean("PartnerSilent", partner.isSilent());
        if (partner instanceof Mob mob) session.putBoolean("PartnerNoAI", mob.isNoAi());
        player.getPersistentData().put(ROOT, session);

        hidePartner(player, partner);
        stats.getResources().setActionCharge(0);
        player.addEffect(new MobEffectInstance(MainEffects.FUSED.get(), duration, 0, false, false));
        player.refreshDisplayName();
        player.refreshTabListName();
        sync(player);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                MainSounds.FUSION.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.dragonminez.fusion.success", Component.literal(partnerName)), true);
        debug(player, "Fusion started with " + partnerName + " [" + partner.getUUID() + "] for " + duration + " ticks" + (debugForce ? " (debug force)" : " (native input)") + ".");
    }

    public static LivingEntity findCompanionPartner(ServerPlayer player) {
        UUID id = LivingWorldCompat.companionId(player);
        return id == null ? null : findNearbyPartner(player, id, NATIVE_RANGE);
    }

    public static LivingEntity findPreferredDebugPartner(ServerPlayer player) {
        UUID id = preferredDebugPartnerId(player);
        return id == null ? null : findNearbyPartner(player, id, 16.0D);
    }

    public static LivingEntity nearestLivingWorldFighter(ServerPlayer player, double radius) {
        if (player == null) return null;
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                        e -> e.isAlive() && LivingWorldCompat.isLivingWorldFighter(e) && !NpcFusionManager.isHiddenFusionPartner(e))
                .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }

    public static boolean bindNearestDebugPartner(ServerPlayer player) {
        LivingEntity fighter = nearestLivingWorldFighter(player, 16.0D);
        if (fighter == null) return false;
        CompoundTag root = bridgeRoot(player);
        root.putUUID(DEBUG_PARTNER, fighter.getUUID());
        saveBridgeRoot(player, root);
        debug(player, "Bound debug partner: " + LivingWorldCompat.fighterName(fighter) + " [" + fighter.getUUID() + "].");
        return true;
    }

    public static void clearDebugPartner(ServerPlayer player) {
        CompoundTag root = bridgeRoot(player);
        root.remove(DEBUG_PARTNER);
        saveBridgeRoot(player, root);
    }

    public static UUID debugPartnerId(ServerPlayer player) {
        CompoundTag root = bridgeRoot(player);
        return root.hasUUID(DEBUG_PARTNER) ? root.getUUID(DEBUG_PARTNER) : null;
    }

    public static void setDebugEnabled(ServerPlayer player, boolean enabled) {
        CompoundTag root = bridgeRoot(player);
        root.putBoolean(DEBUG_ENABLED, enabled);
        saveBridgeRoot(player, root);
    }

    public static boolean debugEnabled(ServerPlayer player) {
        return bridgeRoot(player).getBoolean(DEBUG_ENABLED);
    }

    public static LivingEntity activePartner(ServerPlayer player) {
        if (!isActive(player)) return null;
        CompoundTag active = session(player);
        if (!active.hasUUID("Partner")) return null;
        return findPartner(player.getServer(), active.getUUID("Partner"));
    }

    public static int restoreNearbyOrphans(ServerPlayer player, double radius) {
        if (player == null) return 0;
        int restored = 0;
        for (LivingEntity fighter : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius), LivingWorldCompat::isLivingWorldFighter)) {
            if (hasPartnerBackup(fighter) && !partnerBelongsToLiveSession(fighter, player.getServer())) {
                restoreOrphanPartner(fighter);
                restored++;
            }
        }
        return restored;
    }

    private static UUID preferredDebugPartnerId(ServerPlayer player) {
        UUID debug = debugPartnerId(player);
        return debug != null ? debug : LivingWorldCompat.companionId(player);
    }

    private static LivingEntity findNearbyPartner(ServerPlayer player, UUID id, double range) {
        if (player == null || id == null) return null;
        return player.level().getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(range),
                        e -> e.isAlive() && id.equals(e.getUUID()) && LivingWorldCompat.isLivingWorldFighter(e))
                .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }

    public static void tickPendingDances(MinecraftServer server) {
        if (server == null || PLAYER_DANCES.isEmpty()) return;
        for (UUID playerId : new java.util.HashSet<>(PLAYER_DANCES.keySet())) {
            PendingPlayerDance dance = PLAYER_DANCES.get(playerId);
            if (dance == null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            LivingEntity partner = findPartner(server, dance.partner);
            if (player == null || partner == null || !partner.isAlive() || partner.level() != player.level()) {
                cancelPlayerDance(server, playerId, true);
                continue;
            }
            StatsData stats = player.getCapability(com.dragonminez.common.stats.StatsCapability.INSTANCE).orElse(null);
            if (stats == null || stats.getStatus().isFused()) {
                cancelPlayerDance(server, playerId, true);
                continue;
            }
            // LW owns this dance, so pin the player to the exact starting point until the
            // animation completes. This prevents walking around while visually fusing without
            // touching Dragon Mine Z's native fusion implementation.
            player.teleportTo(dance.originX, dance.originY, dance.originZ);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            player.setYRot(dance.yaw);
            player.setYHeadRot(dance.yaw);
            player.setXRot(0.0F);
            preparePartnerForDance(partner);
            positionPlayerNpcDance(player, partner, dance.yaw, dance.playerLeft);
            dance.ticksLeft--;
            if (dance.ticksLeft <= 0) {
                PLAYER_DANCES.remove(playerId);
                FusionAnimations.stop(player);
                FusionAnimations.stop(partner);
                if (!completeMetamoru(player, stats, partner, dance.debugForce)) {
                    restoreOrphanPartner(partner);
                }
            }
        }
    }

    public static boolean isDancePending(ServerPlayer player) {
        return player != null && PLAYER_DANCES.containsKey(player.getUUID());
    }

    public static int danceTicksRemaining(ServerPlayer player) {
        PendingPlayerDance dance = player == null ? null : PLAYER_DANCES.get(player.getUUID());
        return dance == null ? 0 : dance.ticksLeft;
    }

    public static void cancelPlayerDance(ServerPlayer player) {
        if (player == null) return;
        cancelPlayerDance(player.getServer(), player.getUUID(), true);
    }

    /** Deterministic shutdown cleanup for any pending dance, including a stale offline entry. */
    public static void cancelAllPendingDances(MinecraftServer server) {
        if (server == null || PLAYER_DANCES.isEmpty()) return;
        for (UUID playerId : new java.util.HashSet<>(PLAYER_DANCES.keySet())) {
            cancelPlayerDance(server, playerId, true);
        }
        PLAYER_DANCES.clear();
    }

    static void cancelPlayerDance(MinecraftServer server, UUID playerId, boolean restore) {
        PendingPlayerDance dance = PLAYER_DANCES.remove(playerId);
        if (dance == null || server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        LivingEntity partner = findPartner(server, dance.partner);
        if (player != null) FusionAnimations.stop(player);
        if (partner != null) {
            FusionAnimations.stop(partner);
            if (restore) restoreOrphanPartner(partner);
        }
    }

    private static void preparePartnerForDance(LivingEntity partner) {
        partner.setDeltaMovement(Vec3.ZERO);
        if (partner instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.setNoAi(true);
        }
    }

    private static void positionPlayerNpcDance(ServerPlayer player, LivingEntity partner, float yaw, boolean playerLeft) {
        double radians = Math.toRadians(yaw);
        double sideX = -Math.cos(radians);
        double sideZ = -Math.sin(radians);
        // Native player animation decides LEFT/RIGHT from the partner's side.
        // Place the NPC so our chosen side exactly matches that same sign rule.
        double sign = playerLeft ? -1.0D : 1.0D;
        partner.teleportTo(player.getX() + sideX * 2.30D * sign, player.getY(), player.getZ() + sideZ * 2.30D * sign);
        partner.setYRot(yaw);
        partner.setYHeadRot(yaw);
        partner.setXRot(0.0F);
        partner.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * Replaces DMZ's player-only FusionStatusHandler only for bridge sessions.
     * @return true when the native status handler must be cancelled for this tick.
     */
    public static boolean handleFusionTick(ServerPlayer player, StatsData stats) {
        if (!isActive(player)) return false;
        CompoundTag session = session(player);
        UUID partnerId = session.hasUUID("Partner") ? session.getUUID("Partner") : null;

        Status status = stats.getStatus();
        if (!status.isFused() || partnerId == null || !partnerId.equals(status.getFusionPartnerUUID())) {
            // Another DMZ mechanic/plugin already ended or replaced the fusion state.
            restorePartner(player, session, false);
            clearSession(player);
            return true;
        }

        LivingEntity partner = findPartner(player.getServer(), partnerId);
        if (partner == null || !partner.isAlive()) {
            player.displayClientMessage(Component.translatable("message.dmzlwfusion.partner_missing"), true);
            finish(player, stats, session, false);
            return true;
        }
        if (partner.level() != player.level()) {
            player.displayClientMessage(Component.translatable("message.dmzlwfusion.dimension_end"), true);
            finish(player, stats, session, false);
            return true;
        }

        keepPartnerAttached(player, partner);

        int timer = status.getFusionTimer();
        if (timer <= 1) {
            finish(player, stats, session, false);
            return true;
        }
        status.setFusionTimer(timer - 1);
        return true;
    }

    public static boolean isActive(ServerPlayer player) {
        return player != null && player.getPersistentData().contains(ROOT)
                && player.getPersistentData().getCompound(ROOT).getBoolean("Active");
    }

    /** Used for logout/death/server-stop safety. */
    public static void forceEnd(ServerPlayer player, StatsData stats) {
        if (player == null) return;
        if (isDancePending(player)) cancelPlayerDance(player);
        if (!isActive(player) || stats == null) return;
        finish(player, stats, session(player), true);
    }

    private static void finish(ServerPlayer player, StatsData stats, CompoundTag session, boolean forced) {
        // Let DMZ perform its own leader cleanup: removes FusionBonus, restores the
        // saved appearance, applies the native Metamoran cooldown, clears status,
        // refreshes names and syncs StatsData. The NPC UUID simply resolves to no
        // ServerPlayer, which is safe for the leader branch.
        if (stats.getStatus().isFused() || stats.getStatus().getFusionPartnerUUID() != null) {
            FusionLogic.endFusion(player, stats, forced);
        }
        DmzRevampFusionCompat.clear(stats);
        restorePartner(player, session, true);
        clearSession(player);
    }

    private static void hidePartner(ServerPlayer player, LivingEntity partner) {
        storePartnerBackup(player, partner);
        LivingWorldCompat.suppressAura(partner);
        partner.stopRiding();
        partner.setInvisible(true);
        // LW 0.6.x renders its custom identity label independently of vanilla
        // invisibility. Blank only the LW fighter name while hidden and restore
        // the exact original from PARTNER_ROOT on separation. Newer LW builds
        // also suppress labels for invisible fighters, so this is a safe
        // backwards-compatible second layer.
        if (LivingWorldCompat.isLivingWorldFighter(partner)) LivingWorldCompat.setFighterName(partner, "");
        partner.setInvulnerable(true);
        partner.setSilent(true);
        partner.setDeltaMovement(Vec3.ZERO);
        partner.getPersistentData().putUUID("DMZLWFusionHost", player.getUUID());
        if (partner instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.setNoAi(true);
        }
        partner.teleportTo(player.getX(), player.getY(), player.getZ());
        partner.startRiding(player, true);
    }

    private static void keepPartnerAttached(ServerPlayer player, LivingEntity partner) {
        LivingWorldCompat.suppressAura(partner);
        partner.setInvisible(true);
        if (LivingWorldCompat.isLivingWorldFighter(partner)) LivingWorldCompat.setFighterName(partner, "");
        partner.setInvulnerable(true);
        partner.setSilent(true);
        partner.setDeltaMovement(Vec3.ZERO);
        if (partner instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.setNoAi(true);
        }
        if (partner.getVehicle() != player) {
            partner.stopRiding();
            partner.teleportTo(player.getX(), player.getY(), player.getZ());
            partner.startRiding(player, true);
        }
    }

    private static void restorePartner(ServerPlayer player, CompoundTag session, boolean placeNearPlayer) {
        if (session == null || !session.hasUUID("Partner")) return;
        LivingEntity partner = findPartner(player.getServer(), session.getUUID("Partner"));
        if (partner == null) return;

        CompoundTag backup = partner.getPersistentData().contains(PARTNER_ROOT)
                ? partner.getPersistentData().getCompound(PARTNER_ROOT) : session;
        restorePartnerState(partner, backup);

        if (placeNearPlayer && partner.level() == player.level()) {
            Vec3 look = player.getLookAngle();
            double sideX = -look.z * 1.35D;
            double sideZ = look.x * 1.35D;
            partner.teleportTo(player.getX() + sideX, player.getY(), player.getZ() + sideZ);
            partner.setDeltaMovement(Vec3.ZERO);
        }
    }

    /**
     * Keep a second copy of the few flags we alter on the NPC itself. This is
     * deliberate crash safety: if the server is killed while a fusion is live,
     * the exact persistent fighter can repair itself the next time its chunk is
     * loaded even when the leader session is no longer online.
     */
    private static void storePartnerBackup(ServerPlayer player, LivingEntity partner) {
        // Preserve the pre-dance state. hidePartner() is called again after the
        // dance and must not overwrite the original flags with dance-time NoAI.
        if (hasPartnerBackup(partner)) return;
        CompoundTag backup = new CompoundTag();
        backup.putBoolean("Active", true);
        backup.putUUID("Host", player.getUUID());
        backup.putBoolean("PartnerInvisible", partner.isInvisible());
        backup.putBoolean("PartnerInvulnerable", partner.isInvulnerable());
        backup.putBoolean("PartnerSilent", partner.isSilent());
        if (LivingWorldCompat.isLivingWorldFighter(partner)) {
            backup.putString("PartnerFighterName", LivingWorldCompat.fighterName(partner));
        }
        if (partner instanceof Mob mob) backup.putBoolean("PartnerNoAI", mob.isNoAi());
        partner.getPersistentData().put(PARTNER_ROOT, backup);
    }

    private static void restorePartnerState(LivingEntity partner, CompoundTag backup) {
        if (partner == null || backup == null) return;
        partner.stopRiding();
        partner.setInvisible(backup.getBoolean("PartnerInvisible"));
        partner.setInvulnerable(backup.getBoolean("PartnerInvulnerable"));
        partner.setSilent(backup.getBoolean("PartnerSilent"));
        if (backup.contains("PartnerFighterName") && LivingWorldCompat.isLivingWorldFighter(partner)) {
            LivingWorldCompat.setFighterName(partner, backup.getString("PartnerFighterName"));
        }
        partner.getPersistentData().remove("DMZLWFusionHost");
        partner.getPersistentData().remove(PARTNER_ROOT);
        if (partner instanceof Mob mob) {
            mob.setNoAi(backup.getBoolean("PartnerNoAI"));
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        // Hidden fusion partners can enter the fusion while flying. Riding the fusion keeps
        // their old no-gravity/flight flags alive, so merely restoring NoAI leaves them hanging
        // motionless until a later watchdog happens to reclaim locomotion. Hand control back to
        // the fighter immediately; learned flight remains available, but no flight mode owns it.
        if (partner instanceof AmbientFighterEntity fighter && !fighter.isCaptive()) {
            fighter.setFlying(false);
            fighter.setFlyingFast(false);
            fighter.setNoGravity(false);
            fighter.setCanFly(fighter.hasFlightUnlocked() && !fighter.isNonCombatant());
            fighter.setAmbientFlightActivity(false);
            fighter.setSocialLifeActivity(false);
            fighter.getPersistentData().remove("LWIdleFlightTravel");
            fighter.getPersistentData().remove("LWTravelFlightHolding");
            fighter.getPersistentData().remove("LWCompanionComfortZone");
        }
        partner.setDeltaMovement(Vec3.ZERO);
    }

    static boolean hasPartnerBackup(LivingEntity partner) {
        return partner != null && partner.getPersistentData().contains(PARTNER_ROOT)
                && partner.getPersistentData().getCompound(PARTNER_ROOT).getBoolean("Active");
    }

    static boolean partnerBelongsToLiveSession(LivingEntity partner, MinecraftServer server) {
        if (!hasPartnerBackup(partner) || server == null) return false;

        // NPC<->NPC fusion shares the same crash-safe partner backup envelope.
        if (NpcFusionManager.partnerBelongsToLiveNpcSession(partner, server)) return true;

        CompoundTag backup = partner.getPersistentData().getCompound(PARTNER_ROOT);
        if (!backup.hasUUID("Host")) return false;
        UUID hostId = backup.getUUID("Host");
        ServerPlayer host = server.getPlayerList().getPlayer(hostId);
        if (host == null) return false;

        PendingPlayerDance dance = PLAYER_DANCES.get(hostId);
        if (dance != null && partner.getUUID().equals(dance.partner)) return true;

        if (!isActive(host)) return false;
        CompoundTag hostSession = session(host);
        return hostSession.hasUUID("Partner") && partner.getUUID().equals(hostSession.getUUID("Partner"));
    }

    static void restoreOrphanPartner(LivingEntity partner) {
        if (!hasPartnerBackup(partner)) return;
        restorePartnerState(partner, partner.getPersistentData().getCompound(PARTNER_ROOT));
    }

    static LivingEntity findPartner(MinecraftServer server, UUID id) {
        LivingEntity living = findAnyLiving(server, id);
        return living != null && LivingWorldCompat.isLivingWorldFighter(living) ? living : null;
    }

    static LivingEntity findAnyLiving(MinecraftServer server, UUID id) {
        if (server == null || id == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            var entity = level.getEntity(id);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }

    private static void applyNativeFusionBonus(StatsData stats, LWFusionProfile profile,
                                               int playerTotal, int partnerTotal) {
        double ratio = Math.min(playerTotal, partnerTotal) / (double) Math.max(playerTotal, partnerTotal);
        double multiplier = 1.25D + ratio * (2.0D - 1.25D);
        String[] boosted = ConfigManager.getServerConfig().getGameplay().getFusionBoosts();
        if (boosted == null) return;
        for (String stat : boosted) {
            if (stat == null || stat.isBlank()) continue;
            String normalized = stat.toUpperCase(Locale.ROOT);
            int partnerValue = profile.stat("DEF".equals(normalized) ? "RES" : normalized);
            stats.getBonusStats().addBonusSplit(stat, "FusionBonus", "+", partnerValue * multiplier, true);
        }
    }

    /** Exact name ordering/split used by DMZ 2.1.3 FusionLogic. */
    static String buildFusionName(String firstName, String secondName, String type) {
        if (firstName == null || firstName.isEmpty()) return secondName;
        if (secondName == null || secondName.isEmpty()) return firstName;

        int order = java.lang.Character.toLowerCase(firstName.charAt(0)) - java.lang.Character.toLowerCase(secondName.charAt(0));
        if (order == 0) order = firstName.compareToIgnoreCase(secondName);
        String first = order <= 0 ? firstName : secondName;
        String second = order <= 0 ? secondName : firstName;
        String prefixSource;
        String suffixSource;
        if ("POTHALA".equals(type)) {
            prefixSource = second;
            suffixSource = first;
        } else {
            prefixSource = first;
            suffixSource = second;
        }
        String prefix = prefixSource.substring(0, (prefixSource.length() + 1) / 2);
        String suffix = suffixSource.substring((suffixSource.length() + 1) / 2);
        return prefix + suffix;
    }

    private static void mixAppearance(Character character, LivingEntity partner) {
        character.setBodyColor(mixHex(character.getBodyColor(), LivingWorldCompat.bodyColor(partner)));
        character.setHairColor(mixHex(character.getHairColor(), LivingWorldCompat.hairColor(partner)));
        character.setAuraColor(mixHex(character.getAuraColor(), LivingWorldCompat.auraColor(partner)));
        character.setEye2Color(stripHash(LivingWorldCompat.eye1Color(partner)));
    }

    /** Exact RGB average used by DMZ's own FusionLogic.mixHex(). */
    static String mixHex(String first, String second) {
        try {
            String a = stripHash(first);
            String b = stripHash(second);
            Color c1 = new Color(Integer.parseInt(a, 16));
            Color c2 = new Color(Integer.parseInt(b, 16));
            return String.format(Locale.ROOT, "%02x%02x%02x",
                    (c1.getRed() + c2.getRed()) / 2,
                    (c1.getGreen() + c2.getGreen()) / 2,
                    (c1.getBlue() + c2.getBlue()) / 2);
        } catch (RuntimeException ignored) {
            return stripHash(first);
        }
    }

    private static CompoundTag bridgeRoot(ServerPlayer player) {
        return player.getPersistentData().getCompound(DEBUG_ROOT);
    }

    private static void saveBridgeRoot(ServerPlayer player, CompoundTag root) {
        player.getPersistentData().put(DEBUG_ROOT, root);
    }

    private static void debug(ServerPlayer player, String message) {
        if (player != null && debugEnabled(player)) {
            player.displayClientMessage(Component.literal("[LW Fusion Debug] " + message), false);
        }
    }

    private static CompoundTag session(ServerPlayer player) {
        return player.getPersistentData().getCompound(ROOT);
    }

    private static void clearSession(ServerPlayer player) {
        player.getPersistentData().remove(ROOT);
    }

    private static void sync(ServerPlayer player) {
        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
    }

    private static String stripHash(String value) {
        String safe = safe(value);
        return safe.startsWith("#") ? safe.substring(1) : safe;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
    private static final class PendingPlayerDance {
        final UUID player;
        final UUID partner;
        int ticksLeft;
        final float yaw;
        final boolean playerLeft;
        final boolean debugForce;
        final double originX;
        final double originY;
        final double originZ;

        PendingPlayerDance(UUID player, UUID partner, int ticksLeft, float yaw, boolean playerLeft, boolean debugForce,
                           double originX, double originY, double originZ) {
            this.player = player;
            this.partner = partner;
            this.ticksLeft = ticksLeft;
            this.yaw = yaw;
            this.playerLeft = playerLeft;
            this.debugForce = debugForce;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
        }
    }

}
