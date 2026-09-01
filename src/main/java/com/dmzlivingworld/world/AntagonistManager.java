package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dmzlivingworld.entity.FighterRank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Organic antagonist recognition. No antagonist is generated, buffed or scaled here.
 * Existing hostile people/organizations earn the role through their real record.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AntagonistManager {
    private AntagonistManager() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.getServer().overworld().getGameTime();

        if (now % 1200L == 0L) evaluateOrganizations(event.getServer().overworld(), now);
        if (now % 100L != 0L) return;

        Set<UUID> seen = new HashSet<>();
        for (net.minecraft.server.level.ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) continue;
            for (AmbientFighterEntity fighter : level.getEntitiesOfClass(AmbientFighterEntity.class,
                    player.getBoundingBox().inflate(512.0D))) {
                if (seen.add(fighter.getUUID())) evaluateFighter(fighter, now);
            }
        }
    }

    private static void evaluateOrganizations(ServerLevel overworld, long now) {
        FactionWorldData factions = FactionWorldData.get(overworld);
        AntagonistWorldData antagonists = AntagonistWorldData.get(overworld);
        for (WorldFaction faction : factions.activeFactions()) {
            if (faction.alignment() != FighterAlignment.BAD || antagonists.isAntagonistFaction(faction.id())) continue;
            int victories = factions.victories(faction);
            int fighters = factions.fighterPopulation(faction);
            float momentum = factions.momentum(faction);
            boolean earned = (victories >= 3 && fighters >= 6 && momentum >= 0.98F)
                    || (victories >= 5 && fighters >= 4)
                    || (victories >= 2 && fighters >= 8 && momentum >= 1.08F);
            if (!earned) continue;
            String leader = factions.currentLeaderName(faction);
            String reason = victories + " recorded faction victories • momentum x"
                    + String.format(java.util.Locale.ROOT, "%.2f", momentum);
            antagonists.recognize(faction, now, leader, victories, reason);
            factions.addHistory(faction, now, "Rose into a major antagonist organization after " + victories + " recorded victories.");
        }
    }

    public static void evaluateFighter(AmbientFighterEntity fighter, long now) {
        if (fighter == null || fighter.level().isClientSide || !(fighter.level() instanceof ServerLevel level)
                || !fighter.isAlive() || fighter.isNonCombatant() || fighter.isCaptive()) return;

        AntagonistWorldData world = AntagonistWorldData.get(level);
        WorldFaction faction = fighter.isFactionMember() ? FactionManager.byId(level, fighter.getFactionId()) : null;
        if (faction != null && faction.alignment() == FighterAlignment.BAD && fighter.isFactionLeader()
                && !world.isAntagonistFaction(faction.id()) && qualifiesBoss(fighter)) {
            FactionWorldData fd = FactionWorldData.get(level);
            int victories = fd.victories(faction);
            world.recognize(faction, now, fighter.getFighterName(), victories,
                    "leader " + fighter.getFighterName() + " became a major world threat");
            fd.addHistory(faction, now, "Became an antagonist organization under " + fighter.getFighterName() + ".");
        }
        if (faction != null && world.isAntagonistFaction(faction.id())) {
            if (fighter.isFactionLeader()) {
                recognizeIndividual(fighter, "BOSS", bossEpithet(level, faction, fighter), now,
                        "Became the leader of antagonist organization " + faction.name());
            } else if (qualifiesForCore(fighter)) {
                boolean added = world.registerCore(faction, fighter, now);
                if (added) {
                    fighter.getLegacyData().putBoolean("AntagonistCore", true);
                    fighter.recordLegacyEvent("Became a core member of antagonist organization " + faction.name());
                }
                fighter.setPersistenceRequired();
                world.updateCore(fighter, now);
                FighterMemoryManager.refreshLoadedProfile(fighter);
            }
            return;
        }

        if (qualifiesSolo(fighter)) {
            recognizeIndividual(fighter, "SOLO", soloEpithet(fighter), now,
                    "Became recognized as a recurring antagonist");
        }
    }

    private static boolean qualifiesBoss(AmbientFighterEntity fighter) {
        CompoundTag l = fighter.getLegacyData();
        return fighter.getAlignment() == FighterAlignment.BAD && OrganicThreatManager.score(fighter) >= 5
                && l.getInt("Wins") >= 3 && l.getInt("Kills") >= 1;
    }

    private static boolean qualifiesSolo(AmbientFighterEntity fighter) {
        if (fighter.getAlignment() != FighterAlignment.BAD || fighter.isFactionMember()) return false;
        CompoundTag l = fighter.getLegacyData();
        int wins = Math.max(0, l.getInt("Wins"));
        int kills = Math.max(0, l.getInt("Kills"));
        boolean seriousHistory = fighter.isWanted() && fighter.getWantedLevel() >= 3
                || (fighter.getMemoryRelationship() <= -70 && fighter.getPlayerRivalBattles() >= 3)
                || OrganicThreatManager.score(fighter) >= 6;
        return wins >= 4 && kills >= 1 && seriousHistory;
    }

    private static boolean qualifiesForCore(AmbientFighterEntity fighter) {
        if (fighter.getRank() != FighterRank.VETERAN
                || fighter.getFactionRole().ordinal() < FactionRole.ENFORCER.ordinal()) return false;
        CompoundTag l = fighter.getLegacyData();
        return l.getInt("Wins") >= 3 || l.getInt("Kills") >= 1 || OrganicThreatManager.score(fighter) >= 5;
    }

    private static void recognizeIndividual(AmbientFighterEntity fighter, String role, String epithet, long now, String event) {
        CompoundTag l = fighter.getLegacyData();
        boolean first = !l.getBoolean("AntagonistRecognized") || !role.equals(l.getString("AntagonistRole"));
        l.putBoolean("AntagonistRecognized", true);
        l.putString("AntagonistRole", role);
        l.putString("AntagonistEpithet", epithet);
        if (!l.contains("AntagonistSince")) l.putLong("AntagonistSince", now);
        fighter.setPersistenceRequired();
        if (first) fighter.recordLegacyEvent(event);
        FighterMemoryManager.refreshLoadedProfile(fighter);
    }

    private static String bossEpithet(ServerLevel level, WorldFaction faction, AmbientFighterEntity fighter) {
        int victories = FactionWorldData.get(level).victories(faction);
        if (victories >= 8) return "Warlord";
        if (fighter.getLegacyData().getInt("Kills") >= 7) return "Reaper";
        return "Dread Leader";
    }

    private static String soloEpithet(AmbientFighterEntity fighter) {
        CompoundTag l = fighter.getLegacyData();
        if (l.getInt("UnlawfulCivilianKills") >= 2) return "Butcher";
        if (l.getInt("Kills") >= 7) return "Reaper";
        if (fighter.isWanted() && fighter.getWantedLevel() >= 4) return "Outlaw";
        if (fighter.getPlayerRivalWins() >= 3) return "Hunter";
        if (l.getInt("StrongestWinPower") > Math.max(1L, (long) fighter.getBattlePower()) * 2L) return "Breaker";
        if (fighter.getPersonality() == FighterPersonality.AGGRESSIVE) return "Marauder";
        return "Renegade";
    }

    public static boolean isAntagonist(AmbientFighterEntity fighter) {
        return fighter != null && fighter.getLegacyData().getBoolean("AntagonistRecognized");
    }

    public static String role(AmbientFighterEntity fighter) {
        return isAntagonist(fighter) ? fighter.getLegacyData().getString("AntagonistRole") : "";
    }

    public static String epithet(AmbientFighterEntity fighter) {
        return isAntagonist(fighter) ? fighter.getLegacyData().getString("AntagonistEpithet") : "";
    }

    public static boolean isAntagonistFaction(ServerLevel level, WorldFaction faction) {
        return level != null && faction != null && AntagonistWorldData.get(level).isAntagonistFaction(faction.id());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AmbientFighterEntity fighter) || !(fighter.level() instanceof ServerLevel level)) return;
        if (fighter.getLegacyData().getBoolean("AntagonistCore")) {
            AntagonistWorldData.get(level).markCoreFallen(fighter, level.getServer().overworld().getGameTime());
            WorldFaction faction = fighter.isFactionMember() ? FactionManager.byId(level, fighter.getFactionId()) : null;
            if (faction != null) FactionWorldData.get(level).addHistory(faction, level.getServer().overworld().getGameTime(),
                    "Core member " + fighter.getFighterName() + " was killed.");
        }
    }
}
