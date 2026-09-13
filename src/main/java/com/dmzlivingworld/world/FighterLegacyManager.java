package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.client.LWLang;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Records only events that actually happened in-world. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterLegacyManager {
    private static final String FUSION_BACKUP = "DMZLWFusionPartnerState";
    private FighterLegacyManager() {}

    public static void recordConcession(AmbientFighterEntity victor, AmbientFighterEntity defeated) {
        if (victor == null || defeated == null || victor.level().isClientSide) return;
        victor.recordLegacyBattle(defeated.getFighterName(), defeated.getBattlePower(), true, false, false);
        defeated.recordLegacyBattle(victor.getFighterName(), victor.getBattlePower(), false, false, false);
        FighterGoalManager.onBattleVictory(victor, defeated);
        FighterBattleAdaptationManager.noteOutcome(defeated, victor, false, false);
        FighterPromotionManager.evaluate(victor);
        // Technique adaptation can still emerge from surviving rival fights; the retired
        // master/student prototype is not involved.
        if (defeated.getRivalName().equals(victor.getFighterName()) && defeated.getRandom().nextFloat() < 0.18F) {
            FighterTechniqueManager.tryLearnFrom(defeated, victor, "rival observation");
        }
    }

    // Resolve the life budget before the other death listeners inspect permanent-death state.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (event.getEntity() instanceof AmbientFighterEntity victim && victim.level() instanceof ServerLevel level) {
            // World menaces use their own persistence/respawn/history systems. They are never
            // ordinary People records and must never leak into the Passed Away dossier.
            if (WorldMenaceManager.isWorldMenace(victim)) return;
            if (victim.getPersistentData().contains("DMZLWNpcFusionTemp", Tag.TAG_COMPOUND)
                    && victim.getPersistentData().getCompound("DMZLWNpcFusionTemp").getBoolean("Active")) return;
            String killerName = attacker == null ? "" : attacker.getName().getString();
            int killerPower = 0;
            boolean killerIsPlayer = false;
            if (attacker instanceof AmbientFighterEntity other) {
                killerPower = other.getBattlePower();
                other.recordLegacyBattle(victim.getFighterName(), victim.getBattlePower(), true, true, false);
                FighterGoalManager.onBattleVictory(other, victim);
                FighterPromotionManager.evaluate(other);
            } else if (attacker instanceof ServerPlayer player) {
                killerPower = (int)Math.min(Integer.MAX_VALUE - 1L, Math.round(PlayerWorldManager.playerBattlePower(player)));
                killerIsPlayer = true;
                FighterMemoryManager.strengthenRelationship(player, victim, -10, "Killed this fighter");
            }
            victim.recordLegacyBattle(killerName.isBlank() ? "Unknown" : killerName, killerPower, false, true, killerIsPlayer);
            // Every ordinary fighter has three physical lives.  The counter lives in the
            // identity/profile data, so a scheduled materialization remains the same person
            // and cannot reset their lives merely by unloading or respawning.
            int maximumLives = LivingWorldConfig.npcCombatLives();
            boolean forcedPermanent = isForcedPermanentDeath(victim, attacker);
            // Authored permanent-death scenes bypass the ordinary life budget entirely.
            int defeats = forcedPermanent ? maximumLives
                    : Math.min(maximumLives, Math.max(0, victim.getLegacyData().getInt("LWCombatLivesUsed")) + 1);
            victim.getLegacyData().putInt("LWCombatLivesUsed", defeats);
            if (!isPermanentDeath(victim, attacker)) {
                FighterAftermathManager.beginLethalScene(victim, attacker);
                FighterAfterlifeManager.scheduleRecovery(victim);
                notifyTemporaryDefeat(victim);
                return;
            }
            if (attacker instanceof ServerPlayer player) {
                FighterMemoryManager.forgetPermanentlyKilled(player, victim);
            }
            notifyPermanentDeath(victim);
            FighterArsenalManager.inheritFromFallen(victim);
            FighterLegacyWorldData legacyWorld = FighterLegacyWorldData.get(level);
            java.util.UUID deadRecordId = victim.getMemoryRecordId() != null ? victim.getMemoryRecordId() : victim.getUUID();
            legacyWorld.markDeadRecord(deadRecordId);
            if (!deadRecordId.equals(victim.getUUID())) legacyWorld.markDeadRecord(victim.getUUID());
            victim.getPersistentData().putBoolean("LWDeathArchived", true);
            discardLiveCopies(level.getServer(), deadRecordId, victim);
            FighterNpcSocialManager.cancelFor(victim);
            FighterAmbientActivityManager.cancelFor(victim);
            victim.setAmbientPose(0);
            victim.setFlyingFast(false);
            victim.setFlying(false);
            legacyWorld.archive(victim, killerName, level.getServer().overworld().getGameTime());
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player && attacker instanceof AmbientFighterEntity fighter) {
            int playerPower = (int)Math.min(Integer.MAX_VALUE - 1L, Math.round(PlayerWorldManager.playerBattlePower(player)));
            fighter.recordLegacyBattle(player.getGameProfile().getName(), playerPower, true, false, true);
        }
    }

    public static boolean isPermanentDeath(AmbientFighterEntity victim, Entity attacker) {
        // Keep forced outcomes identifiable even to listeners that query the event independently.
        return victim != null && (isForcedPermanentDeath(victim, attacker)
                || victim.getLegacyData().getInt("LWCombatLivesUsed") >= LivingWorldConfig.npcCombatLives());
    }

    private static boolean isForcedPermanentDeath(AmbientFighterEntity victim, Entity attacker) {
        if (victim == null) return false;
        if (victim.isWanted()) return true;
        if (attacker instanceof AmbientFighterEntity killer && WorldMenaceManager.isWorldMenace(killer)) return true;
        return attacker instanceof ServerPlayer player
                && FactionRequestManager.causesPermanentDeathNotice(player, victim);
    }

    private static void notifyTemporaryDefeat(AmbientFighterEntity victim) {
        if (!(victim.level() instanceof ServerLevel level)) return;
        Component message = LWLang.text("message.fighter.temporary_defeat", victim.getFighterName(),
                Math.max(0, LivingWorldConfig.npcCombatLives() - victim.getLegacyData().getInt("LWCombatLivesUsed"))).copy().withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(victim) <= 20.0D * 20.0D)
                player.displayClientMessage(message, false);
        }
    }

    private static void notifyPermanentDeath(AmbientFighterEntity victim) {
        if (!(victim.level() instanceof ServerLevel level)) return;
        Component message = LWLang.text("message.fighter.death_permanent", victim.getFighterName())
                .copy().withStyle(ChatFormatting.DARK_GRAY);
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(victim) <= 20.0D * 20.0D) {
                player.displayClientMessage(message, false);
            }
        }
    }

    private static void discardLiveCopies(MinecraftServer server, java.util.UUID recordId, AmbientFighterEntity original) {
        if (server == null || recordId == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof AmbientFighterEntity fighter) || fighter == original || fighter.isDeadSoul()) continue;
                java.util.UUID otherId = fighter.getMemoryRecordId() != null ? fighter.getMemoryRecordId() : fighter.getUUID();
                if (recordId.equals(otherId)) fighter.discard();
            }
        }
    }

    /**
     * Integrated fusion observation. The fusion system writes a crash-safe
     * partner-state tag; legacy tracking observes the transition once and records
     * the real participant.
     */
    public static void tickFusionObservation(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || fighter.tickCount % 20 != 0) return;
        CompoundTag persistent = fighter.getPersistentData();
        boolean active = persistent.contains(FUSION_BACKUP, Tag.TAG_COMPOUND)
                && persistent.getCompound(FUSION_BACKUP).getBoolean("Active");
        CompoundTag legacy = fighter.getLegacyData();
        boolean seen = legacy.getBoolean("FusionSessionSeen");
        if (!active) {
            if (seen) legacy.putBoolean("FusionSessionSeen", false);
            return;
        }
        if (seen) return;

        CompoundTag backup = persistent.getCompound(FUSION_BACKUP);
        String partner = "unknown partner";
        if (backup.hasUUID("Host") && fighter.level() instanceof ServerLevel level) {
            ServerPlayer host = level.getServer().getPlayerList().getPlayer(backup.getUUID("Host"));
            if (host != null) partner = host.getGameProfile().getName();
        } else if (backup.hasUUID("PairOther") && fighter.level() instanceof ServerLevel level) {
            Entity other = level.getEntity(backup.getUUID("PairOther"));
            if (other != null) partner = other.getName().getString();
        }
        fighter.recordFusion(partner);
        legacy.putBoolean("FusionSessionSeen", true);
    }

    public static boolean isNotable(AmbientFighterEntity fighter) {
        if (fighter == null) return false;
        CompoundTag l = fighter.getLegacyData();
        return fighter.isRemembered() || fighter.isFactionLeader() || l.getInt("Fights") >= 3
                || fighter.getPlayerRivalBattles() > 0 || l.getInt("Fusions") > 0
                || l.getBoolean("ThreatRecognized")
                || !FighterArsenalManager.summary(fighter).equals("none");
    }
}
