package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ResourceSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Cooldowns;
import com.dragonminez.common.util.ITTeleportHelper;
import com.dragonminez.server.events.players.combat.DashHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Lets a player use Dragon Mine Z's learned Instant Transmission to reach a familiar remembered fighter. */
public final class FighterInstantTransmissionManager {
    private static final int MENU_SKILL_LEVEL = 5;
    private static final int CROSS_DIMENSION_SKILL_LEVEL = 10;
    private static final int KI_FAMILIAR_RELATIONSHIP = 5;
    // Server-thread guard: one remembered identity may only be materialized once at a time.
    private static final Set<String> MATERIALIZING = new HashSet<>();

    private FighterInstantTransmissionManager() {}

    /** Live remembered-profile status. Remembered People profiles refresh this while open. */
    public static String menuStatus(ServerPlayer player) {
        if (player == null) return "Unavailable";
        StatsData data = player.getCapability(StatsCapability.INSTANCE).orElse(null);
        if (data == null) return "Unavailable";
        int skillLevel = data.getSkills().getSkillLevel("instant_transmission");
        if (skillLevel < MENU_SKILL_LEVEL) return "Locked • requires stronger Instant Transmission";
        if (!player.isCreative() && !player.isSpectator() && data.getCooldowns().hasCooldown(Cooldowns.TELEPORT_CD))
            return "Cooldown active • recovering";
        return "READY";
    }


    public static void travel(ServerPlayer player, UUID recordId) {
        if (player == null || recordId == null) return;
        StatsData data = player.getCapability(StatsCapability.INSTANCE).orElse(null);
        if (data == null) return;
        int skillLevel = data.getSkills().getSkillLevel("instant_transmission");
        if (skillLevel < MENU_SKILL_LEVEL) {
            message(player, "You need a stronger Instant Transmission skill before you can lock onto remembered Ki this way.");
            return;
        }
        // IT is an explicit attempt to sense a real remembered person. Advance their coarse
        // off-screen life first so the lock uses the current simulated whereabouts, not stale last-seen data.
        FighterMemoryManager.tickPersistentLives(player, player.getServer().overworld().getGameTime());
        CompoundTag remembered = FighterMemoryManager.rememberedRecord(player, recordId);
        if (remembered.isEmpty()) {
            message(player, "You do not remember that Ki signature clearly enough yet.");
            return;
        }
        if (remembered.contains("Profile", Tag.TAG_COMPOUND)) {
            CompoundTag profile = remembered.getCompound("Profile");
            if (profile.getBoolean(WorldMenaceManager.HEROBRINE_TAG) || profile.getBoolean(RedRibbonExperimentManager.TAG)) {
                message(player, "That presence cannot be selected as a personal Instant Transmission destination.");
                return;
            }
        }
        if (FighterLegacyWorldData.get(player.serverLevel()).isDeadRecord(recordId)) {
            message(player, "No Ki signal. You cannot sense them anymore.");
            return;
        }

        CompoundTag signalRecord = FighterMemoryManager.internalSignalRecord(player, recordId);
        AmbientFighterEntity target = findLoadedIdentity(player, recordId, signalRecord);
        if (!knowsKiSignature(remembered)) {
            if (target != null && target.isAlive()) {
                message(player, "You can sense them, but you have not learned their Ki well enough yet. Spend more time together first.");
            } else {
                message(player, "You remember them, but you have not learned their Ki signature well enough yet.");
            }
            return;
        }

        boolean targetLoaded = target != null && target.isAlive() && !target.isCaptive();
        String recordedDimension = signalRecord.getString("LifeDimension");
        boolean sameDimension = targetLoaded
                ? target.level().dimension().equals(player.level().dimension())
                : recordedDimension.isBlank() || recordedDimension.equals(player.level().dimension().location().toString());
        if (!sameDimension && skillLevel < CROSS_DIMENSION_SKILL_LEVEL) {
            message(player, "Your Instant Transmission is not strong enough to reach their Ki across dimensions.");
            return;
        }

        boolean bypassCosts = player.isCreative() || player.isSpectator();
        if (!bypassCosts && data.getCooldowns().hasCooldown(Cooldowns.TELEPORT_CD)) {
            message(player, "Instant Transmission is still recovering.");
            return;
        }

        double distance = 0.0D;
        if (sameDimension) {
            if (targetLoaded) distance = player.position().distanceTo(target.position());
            else if (signalRecord.contains("LifeX") && signalRecord.contains("LifeZ")) {
                double dx = signalRecord.getInt("LifeX") + 0.5D - player.getX();
                double dy = (signalRecord.contains("LifeY") ? signalRecord.getInt("LifeY") : player.getY()) - player.getY();
                double dz = signalRecord.getInt("LifeZ") + 0.5D - player.getZ();
                distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            } else {
                message(player, "You remember their Ki, but you cannot sense that signature in the world right now.");
                return;
            }

        } else if (!targetLoaded) {
            // Cross-dimension travel is supported when the fighter is physically present in that
            // dimension. Living World never force-loads another dimension just to create a target.
            message(player, "You remember their Ki, but you cannot get a stable lock across dimensions right now.");
            return;
        }

        int estimatedKiCost = (DashHandler.getFlyDashKiCost() * 5) + ITTeleportHelper.extraKiCostForDistance(distance);
        if (!bypassCosts && data.getResources().getCurrentEnergy() < estimatedKiCost) {
            message(player, "You do not have enough Ki for Instant Transmission.");
            return;
        }

        // A remembered fighter can be abstract/off-screen when the player deliberately locks on.
        // Materialize that SAME recorded identity at its simulated life location for the IT action.
        if (!targetLoaded) {
            target = tryMaterializeNearbySignal(player, recordId, signalRecord, skillLevel);
            targetLoaded = target != null && target.isAlive() && !target.isCaptive();
            if (!targetLoaded) {
                message(player, "You remember their Ki, but you cannot sense that signature in the world right now.");
                return;
            }
            sameDimension = target.level().dimension().equals(player.level().dimension());
            distance = sameDimension ? player.position().distanceTo(target.position()) : 0.0D;
        }

        int kiCost = (DashHandler.getFlyDashKiCost() * 5) + ITTeleportHelper.extraKiCostForDistance(distance);
        if (!bypassCosts) {
            if (data.getResources().getCurrentEnergy() < kiCost) {
                message(player, "You do not have enough Ki for Instant Transmission.");
                return;
            }
            data.getResources().removeEnergy(kiCost);
            NetworkHandler.sendToTrackingEntityAndSelf(new ResourceSyncS2C(player), player);
            ITTeleportHelper.applyTeleportCooldown(player, data);
        }

        ServerLevel targetLevel = (ServerLevel) target.level();
        BlockPos center = target.blockPosition();
        BlockPos safePos = ITTeleportHelper.findSafeTeleportPos(targetLevel, center);
        if (player.isVehicle()) player.stopRiding();
        double dX = center.getX() - safePos.getX();
        double dZ = center.getZ() - safePos.getZ();
        float yaw = (float)(Math.atan2(dZ, dX) * (180D / Math.PI)) - 90.0F;
        player.teleportTo(targetLevel, safePos.getX() + 0.5D, safePos.getY(), safePos.getZ() + 0.5D, yaw, player.getXRot());
        player.playNotifySound(MainSounds.TP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static boolean knowsKiSignature(net.minecraft.nbt.CompoundTag remembered) {
        if (remembered == null || remembered.isEmpty()) return false;
        return remembered.getInt("Encounters") >= 1
                || remembered.getInt("Relationship") >= KI_FAMILIAR_RELATIONSHIP
                || remembered.getInt("BattlesVsPlayer") > 0
                || remembered.getBoolean("Rescued");
    }

    private static AmbientFighterEntity tryMaterializeNearbySignal(ServerPlayer player, UUID recordId,
                                                                   CompoundTag record, int skillLevel) {
        if (record == null || record.isEmpty() || !record.contains("Profile")
                || !record.contains("LifeX") || !record.contains("LifeZ")) return null;
        String dimension = record.getString("LifeDimension");
        // Cross-dimension IT still requires the target to be physically present; this explicit
        // materialization path currently targets the player's active supported dimension.
        if (!dimension.isBlank() && !dimension.equals(player.level().dimension().location().toString())) return null;
        BlockPos life = new BlockPos(record.getInt("LifeX"),
                record.contains("LifeY") ? record.getInt("LifeY") : player.blockPosition().getY(),
                record.getInt("LifeZ"));

        // The destination chunk may contain the real persistent fighter. Loading it can deserialize
        // that entity immediately, so ALWAYS re-check the remembered identity before materializing.
        // Without this second lookup, IT could spawn a memory copy beside the just-loaded original.
        ServerLevel level = player.serverLevel();
        level.getChunkAt(life);
        AmbientFighterEntity restored = findLoadedIdentity(player, recordId, record);
        if (restored != null) return restored;

        // A persistent entity can cross a chunk edge between the last whereabouts write and the
        // chunk becoming unloaded. For this explicit IT action only, probe the eight adjacent
        // chunks as a bounded recovery ring, then re-check exact/stable identity. No chunk ticket
        // is retained and ordinary ambient simulation still never force-loads remote areas.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            level.getChunkAt(life.offset(dx * 16, 0, dz * 16));
        }
        restored = findLoadedIdentity(player, recordId, record);
        if (restored != null) return restored;

        String guard = player.getUUID() + ":" + recordId;
        if (!MATERIALIZING.add(guard)) return findLoadedIdentity(player, recordId, record);
        try {
            // One final identity lookup happens under the materialization guard. This catches a
            // fighter restored by the same server tick before a second IT request can make a copy.
            restored = findLoadedIdentity(player, recordId, record);
            if (restored != null) return restored;
            AmbientFighterEntity spawned = AmbientFighterSpawner.spawnRememberedSignalAt(player, record.getCompound("Profile"), recordId,
                    Math.max(1, record.getInt("Encounters")), record.getInt("Relationship"),
                    record.getBoolean("Rescued"), life);
            if (spawned != null) FighterMemoryManager.refreshLoadedProfile(spawned);
            return spawned;
        } finally {
            MATERIALIZING.remove(guard);
        }
    }

    /**
     * Resolve one remembered person through every stable identity we persist. Old saves may have
     * a stale/missing memory binding even though the original entity is alive; IT must rebind that
     * exact actor instead of spawning a second copy. If an older IT bug already left two loaded
     * instances with the same logical identity, the explicit lock-on repairs that impossible state
     * by retaining the best canonical match and discarding only the duplicate identity instances.
     */
    private static AmbientFighterEntity findLoadedIdentity(ServerPlayer player, UUID recordId, CompoundTag record) {
        if (player == null || player.getServer() == null || recordId == null) return null;
        UUID entityId = record != null && record.hasUUID("LifeEntityUUID") ? record.getUUID("LifeEntityUUID") : null;
        UUID socialId = rememberedSocialIdentity(record);
        String lifeDimension = record == null ? "" : record.getString("LifeDimension");
        double lifeX = record != null && record.contains("LifeX") ? record.getInt("LifeX") + 0.5D : 0.0D;
        double lifeY = record != null && record.contains("LifeY") ? record.getInt("LifeY") : 0.0D;
        double lifeZ = record != null && record.contains("LifeZ") ? record.getInt("LifeZ") + 0.5D : 0.0D;

        List<AmbientFighterEntity> matches = new ArrayList<>();
        AmbientFighterEntity canonical = null;
        int bestRank = Integer.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof AmbientFighterEntity fighter) || !fighter.isAlive()) continue;
                boolean exact = entityId != null && entityId.equals(fighter.getUUID());
                boolean recordMatch = recordId.equals(fighter.getMemoryRecordId());
                CompoundTag legacy = fighter.getLegacyData();
                boolean socialMatch = socialId != null && legacy.hasUUID("NpcSocialIdentity")
                        && socialId.equals(legacy.getUUID("NpcSocialIdentity"));
                if (!exact && !recordMatch && !socialMatch) continue;
                matches.add(fighter);
                int rank = exact ? 0 : recordMatch ? 1 : 2;
                double d = (!lifeDimension.isBlank() && lifeDimension.equals(level.dimension().location().toString()))
                        ? fighter.distanceToSqr(lifeX, lifeY, lifeZ) : Double.MAX_VALUE / 4.0D;
                if (canonical == null || rank < bestRank || (rank == bestRank && d < bestDistance)) {
                    canonical = fighter;
                    bestRank = rank;
                    bestDistance = d;
                }
            }
        }
        if (canonical == null) return null;
        rebindRecoveredIdentity(player, canonical, recordId, record);
        for (AmbientFighterEntity duplicate : matches) {
            if (duplicate != canonical) duplicate.discard();
        }
        return canonical;
    }

    private static UUID rememberedSocialIdentity(CompoundTag record) {
        if (record == null || !record.contains("Profile", Tag.TAG_COMPOUND)) return null;
        CompoundTag profile = record.getCompound("Profile");
        if (!profile.contains("Legacy", Tag.TAG_COMPOUND)) return null;
        CompoundTag legacy = profile.getCompound("Legacy");
        return legacy.hasUUID("NpcSocialIdentity") ? legacy.getUUID("NpcSocialIdentity") : null;
    }

    private static void rebindRecoveredIdentity(ServerPlayer player, AmbientFighterEntity fighter, UUID recordId, CompoundTag record) {
        if (fighter == null || recordId == null || record == null) return;
        fighter.bindMemory(player.getUUID(), recordId, Math.max(1, record.getInt("Encounters")),
                record.getInt("Relationship"), record.getBoolean("Rescued"));
        FighterMemoryManager.refreshLoadedProfile(fighter);
    }

    private static void message(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal("[Living World] " + text), false);
    }
}
