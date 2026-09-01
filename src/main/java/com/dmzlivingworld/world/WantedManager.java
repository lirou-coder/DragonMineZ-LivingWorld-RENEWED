package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.LWEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * 1.5 factual wanted system. It never invents criminals, crimes or bonus power.
 * A Living World fighter enters the registry only after an unlawful act that really
 * occurred in-world, and a respawn uses that same fighter's saved profile.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WantedManager {
    private static final String TRACK_KEY = "DMZLivingWorldWantedTrack";
    private static final String PLAYER_WANTED = "DMZLivingWorldPlayerWanted";
    private static final String PLAYER_KILLS = "UnlawfulKills";
    private static final String PLAYER_CIVILIANS = "UnlawfulCivilianKills";
    private static final String PLAYER_ALLIES = "UnlawfulAllyKills";
    private static final String PLAYER_NEUTRALS = "UnlawfulNeutralKills";
    private static final String PLAYER_LEVEL = "WantedLevel";
    private static final String PLAYER_CRIME = "Crime";
    private static final int PLAYER_WANTED_THRESHOLD = 6;
    private WantedManager() {}

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer original) || !(event.getEntity() instanceof ServerPlayer clone)) return;
        if (original.getPersistentData().contains(PLAYER_WANTED, net.minecraft.nbt.Tag.TAG_COMPOUND))
            clone.getPersistentData().put(PLAYER_WANTED, original.getPersistentData().getCompound(PLAYER_WANTED).copy());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long overworldTime = event.getServer().overworld().getGameTime();
        if (overworldTime % 200L == 0L) WantedWorldData.get(event.getServer().overworld()).purgeWorldMenaces();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) continue;
            if (player.isCreative() || player.isSpectator()) continue;
            long time = level.getGameTime();
            if (Math.floorMod(time + player.getUUID().hashCode(), 80) == 0) {
                tickTracker(player, WantedWorldData.get(level));
            }
        }
    }


    public static boolean isPlayerWanted(ServerPlayer player) {
        return player != null && player.getPersistentData().getCompound(PLAYER_WANTED).getInt(PLAYER_LEVEL) > 0;
    }

    public static int playerWantedSeverity(ServerPlayer player) {
        return player == null ? 0 : Math.max(0, Math.min(5, player.getPersistentData().getCompound(PLAYER_WANTED).getInt(PLAYER_LEVEL)));
    }

    public static int playerUnlawfulKills(ServerPlayer player) {
        return player == null ? 0 : Math.max(0, player.getPersistentData().getCompound(PLAYER_WANTED).getInt(PLAYER_KILLS));
    }

    public static String playerWantedCrime(ServerPlayer player) {
        if (player == null) return "";
        return player.getPersistentData().getCompound(PLAYER_WANTED).getString(PLAYER_CRIME);
    }

    public static boolean shouldFactionPursuePlayer(WorldFaction faction, ServerPlayer player) {
        if (faction == null || player == null || !isPlayerWanted(player)) return false;
        return faction.alignment() == FighterAlignment.GOOD
                || faction.structure() == FactionStructure.GUARD
                || faction.structure() == FactionStructure.ORDER
                || faction.ethos() == FactionEthos.WANDERING_GUARD
                || faction.ethos() == FactionEthos.NAMEK_WARDENS;
    }

    private static void recordPlayerCrime(ServerPlayer player, AmbientFighterEntity victim, boolean ally) {
        CompoundTag root = player.getPersistentData().getCompound(PLAYER_WANTED);
        int weight = victim.isNonCombatant() ? 2 : ally ? 2 : 1;
        root.putInt(PLAYER_KILLS, Math.min(Integer.MAX_VALUE, Math.max(0, root.getInt(PLAYER_KILLS)) + weight));
        if (victim.isNonCombatant()) root.putInt(PLAYER_CIVILIANS, root.getInt(PLAYER_CIVILIANS) + 1);
        else if (ally) root.putInt(PLAYER_ALLIES, root.getInt(PLAYER_ALLIES) + 1);
        else root.putInt(PLAYER_NEUTRALS, root.getInt(PLAYER_NEUTRALS) + 1);

        int acts = root.getInt(PLAYER_KILLS);
        int severity = severityForPressure(acts);
        int old = root.getInt(PLAYER_LEVEL);
        root.putInt(PLAYER_LEVEL, severity);
        String crime = root.getInt(PLAYER_CIVILIANS) > 0
                ? "repeated unlawful killings, including " + root.getInt(PLAYER_CIVILIANS) + " non-combatant" + (root.getInt(PLAYER_CIVILIANS) == 1 ? "" : "s")
                : root.getInt(PLAYER_ALLIES) > 0
                ? "repeated killings of allies and non-hostile fighters"
                : "repeated killings of non-hostile fighters";
        root.putString(PLAYER_CRIME, crime);
        root.putLong("LastCrime", player.getServer().overworld().getGameTime());
        player.getPersistentData().put(PLAYER_WANTED, root);
        if (severity > old && severity > 0) {
            player.displayClientMessage(Component.literal("[Living World] WANTED • threat " + "★".repeat(severity)
                    + " • " + crime + ". Guard-aligned factions may now pursue you."), false);
        } else if (severity == 0 && acts > 0) {
            player.displayClientMessage(Component.literal("[Living World] Unlawful kill recorded • " + acts + " / " + PLAYER_WANTED_THRESHOLD + " wanted pressure."), true);
        }
    }

    private static int severityForPressure(int pressure) {
        return pressure >= 45 ? 5 : pressure >= 30 ? 4 : pressure >= 20 ? 3 : pressure >= 12 ? 2 : pressure >= PLAYER_WANTED_THRESHOLD ? 1 : 0;
    }

    public static void debugAddPlayerWantedPressure(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) return;
        CompoundTag root = player.getPersistentData().getCompound(PLAYER_WANTED);
        int pressure = Math.min(Integer.MAX_VALUE, Math.max(0, root.getInt(PLAYER_KILLS)) + amount);
        root.putInt(PLAYER_KILLS, pressure);
        root.putInt(PLAYER_LEVEL, severityForPressure(pressure));
        if (root.getString(PLAYER_CRIME).isBlank()) root.putString(PLAYER_CRIME, "repeated unlawful killings of non-hostile fighters");
        root.putLong("LastCrime", player.getServer().overworld().getGameTime());
        player.getPersistentData().put(PLAYER_WANTED, root);
    }

    public static void clearPlayerWanted(ServerPlayer player) {
        if (player != null) player.getPersistentData().remove(PLAYER_WANTED);
    }

    private static boolean isLawfulSelfDefense(ServerPlayer player, AmbientFighterEntity victim) {
        if (player == null || victim == null || !(victim.level() instanceof ServerLevel level)) return false;
        long now = level.getServer().overworld().getGameTime();
        return PeacekeeperManager.isNpcAggressorFor(victim, player, now);
    }

    /** Evaluate a loaded fighter from factual counters only. */
    public static void evaluate(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || !(fighter.level() instanceof ServerLevel level)) return;
        if (WorldMenaceManager.isWorldMenace(fighter)) {
            if (fighter.isWanted()) fighter.markWanted("", 0, "");
            WantedWorldData.get(level).purgeWorldMenaces();
            return;
        }
        CompoundTag legacy = fighter.getLegacyData();
        int civilian = Math.max(0, legacy.getInt("UnlawfulCivilianKills"));
        int betrayal = Math.max(0, legacy.getInt("UnlawfulAllyKills"));
        int player = Math.max(0, legacy.getInt("UnlawfulPlayerKills"));
        int neutral = Math.max(0, legacy.getInt("UnlawfulNeutralKills"));
        int acts = civilian + betrayal + player + neutral;
        if (acts <= 0 && !fighter.isWanted()) return;

        int severity = Math.min(5, 1 + civilian * 2 + betrayal * 2 + player + neutral / 2);
        String crime = crimeSummary(civilian, betrayal, player, neutral);
        String id = stableWantedId(fighter);
        fighter.markWanted(id, severity, crime);
        WantedWorldData.get(level).registerOrUpdate(fighter, severity, crime);
        if (!legacy.getBoolean("WantedRecorded")) {
            legacy.putBoolean("WantedRecorded", true);
            fighter.recordLegacyEvent("Became wanted after " + crime);
        }
    }

    public static void update(AmbientFighterEntity fighter) {
        if (fighter == null || WorldMenaceManager.isWorldMenace(fighter) || !fighter.isWanted()
                || !(fighter.level() instanceof ServerLevel level)) return;
        WantedWorldData data = WantedWorldData.get(level);
        WantedWorldData.WantedProfile profile = data.byId(fighter.getWantedId());
        if (profile != null && !profile.eliminated) data.updateFromFighter(profile, fighter);
    }

    public static boolean track(ServerPlayer player, WantedWorldData.WantedProfile profile) {
        if (player == null || profile == null || profile.eliminated) return false;
        player.getPersistentData().putInt(TRACK_KEY, profile.slot);
        tickTracker(player, WantedWorldData.get(player.serverLevel()));
        return true;
    }

    public static void clearTrack(ServerPlayer player) {
        if (player != null) player.getPersistentData().remove(TRACK_KEY);
    }

    public static int trackedSlot(ServerPlayer player) {
        return player == null ? 0 : player.getPersistentData().getInt(TRACK_KEY);
    }

    private static void tickTracker(ServerPlayer player, WantedWorldData data) {
        int slot = trackedSlot(player);
        if (slot <= 0) return;
        WantedWorldData.WantedProfile profile = data.bySlot(slot);
        if (profile == null || profile.eliminated) {
            clearTrack(player);
            if (profile != null) player.displayClientMessage(Component.literal("WANTED TRACK • " + profile.name + " • eliminated"), true);
            return;
        }
        if (!(player.level() instanceof ServerLevel current) || LivingWorldDimensions.realm(current) != profile.realm) {
            player.displayClientMessage(Component.literal("WANTED TRACK • " + profile.name + " • " + profile.realm.displayName()
                    + " • threat " + "★".repeat(profile.severity)), true);
            return;
        }
        int x = profile.lastX != 0 ? profile.lastX : profile.anchorX;
        int z = profile.lastZ != 0 ? profile.lastZ : profile.anchorZ;
        int distance = (int)Math.round(Math.hypot(x - player.getX(), z - player.getZ()));
        String direction = FactionManager.direction(player.getX(), player.getZ(), x, z);
        player.displayClientMessage(Component.literal("WANTED TRACK • " + profile.name + " • " + direction + " • "
                + distance + "b • last known • " + "★".repeat(profile.severity)), true);
    }

    /**
     * Debug/manual materialization of the SAME factual criminal. No rerolling and no
     * wanted power multiplier: the saved profile is the source of truth.
     */
    public static AmbientFighterEntity spawn(ServerPlayer player, WantedWorldData.WantedProfile profile, boolean debug) {
        if (!(player.level() instanceof ServerLevel level) || profile == null || profile.eliminated) return null;
        if (profile.realm != LivingWorldDimensions.realm(level)) return null;
        BlockPos center = debug ? player.blockPosition()
                : new BlockPos(profile.lastX != 0 ? profile.lastX : profile.anchorX,
                player.blockPosition().getY(), profile.lastZ != 0 ? profile.lastZ : profile.anchorZ);
        BlockPos pos = AmbientFighterSpawner.findSafeGroundAround(level, center, player.getRandom(), debug ? 8 : 0,
                debug ? 16 : 28, 36);
        if (pos == null) return null;
        AmbientFighterEntity fighter = LWEntities.AMBIENT_FIGHTER.get().create(level);
        if (fighter == null) return null;
        fighter.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getRandom().nextFloat() * 360.0F, 0.0F);
        fighter.initializeFromMemory(profile.profile.copy());
        fighter.markWanted(profile.id, profile.severity, profile.crime);
        fighter.setPersistenceRequired();
        if (!level.noCollision(fighter)) return null;
        level.addFreshEntity(fighter);
        WantedWorldData.get(level).markSpawned(profile, fighter.blockPosition());
        return fighter;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Entity attacker = event.getSource().getEntity();

        // Close an existing factual wanted record regardless of who finally defeated them.
        if (event.getEntity() instanceof AmbientFighterEntity wanted && wanted.isWanted()
                && wanted.level() instanceof ServerLevel wantedLevel) {
            WantedWorldData data = WantedWorldData.get(wantedLevel);
            WantedWorldData.WantedProfile profile = data.byId(wanted.getWantedId());
            if (profile != null && !profile.eliminated) {
                data.markEliminated(profile, wantedLevel.getServer().overworld().getGameTime());
                if (attacker instanceof ServerPlayer player) {
                    if (trackedSlot(player) == profile.slot) clearTrack(player);
                    player.displayClientMessage(Component.literal("[Living World] WANTED eliminated: " + profile.name
                            + " — " + profile.crime + "."), false);
                    PlayerWorldManager.recordWantedElimination(player);
                    List<WorldFaction> factions = FactionManager.factionsForRealm(wantedLevel);
                    for (WorldFaction faction : factions) {
                        if (faction.alignment() == FighterAlignment.GOOD
                                && (faction.structure() == FactionStructure.GUARD
                                || faction.ethos() == FactionEthos.WANDERING_GUARD
                                || faction.ethos() == FactionEthos.NAMEK_WARDENS)) {
                            FactionManager.adjustReputation(player, faction, 2 + profile.severity);
                        }
                    }
                }
            }
        }

        // Players now enter the same factual crime loop after repeated unsanctioned Living World kills.
        // Contracts, sanctioned matches, wanted targets and actual self-defense remain lawful.
        if (event.getEntity() instanceof AmbientFighterEntity victim && attacker instanceof ServerPlayer playerKiller) {
            if (!playerKiller.isCreative() && !playerKiller.isSpectator()
                    && !victim.isWanted()
                    && !victim.isSanctionedMatchParticipant()
                    && !victim.isDuelOpponent(playerKiller)
                    && !FactionRequestManager.isAuthorizedPlayerKill(playerKiller, victim)
                    && !isLawfulSelfDefense(playerKiller, victim)) {
                boolean ally = false;
                if (victim.isFactionMember() && victim.level() instanceof ServerLevel level) {
                    WorldFaction vf = FactionManager.byId(level, victim.getFactionId());
                    ally = vf != null && FactionManager.getReputation(playerKiller, vf) >= FactionManager.FRIENDLY_REP;
                }
                boolean nonHostile = victim.isNonCombatant() || ally || victim.getAlignment() != FighterAlignment.BAD
                        || victim.getTarget() != playerKiller;
                if (nonHostile) recordPlayerCrime(playerKiller, victim, ally);
            }
        }

        // An actual LW fighter killed another LW person outside sanctioned combat.
        if (event.getEntity() instanceof AmbientFighterEntity victim
                && attacker instanceof AmbientFighterEntity killer
                && killer.level() instanceof ServerLevel level) {
            if (WorldMenaceManager.isWorldMenace(killer)) return;
            if (killer.isSanctionedMatchParticipant() || victim.isSanctionedMatchParticipant()
                    || killer.isDuelOpponent(victim) || victim.isDuelOpponent(killer)) return;
            boolean enemyWar = false;
            if (killer.isFactionMember() && victim.isFactionMember()) {
                WorldFaction kf = FactionManager.byId(level, killer.getFactionId());
                WorldFaction vf = FactionManager.byId(level, victim.getFactionId());
                if (kf != null && vf != null) {
                    enemyWar = FactionWorldData.get(level).isAtWar(kf, vf, level.getServer().overworld().getGameTime());
                }
            }
            boolean ally = killer.isFactionMember() && victim.isFactionMember()
                    && (killer.getFactionId().equals(victim.getFactionId()) || FactionManager.areAllies(killer, victim));
            if (enemyWar) return; // legitimate faction-war casualty, not a crime.

            CompoundTag legacy = killer.getLegacyData();
            if (victim.isNonCombatant()) {
                increment(legacy, "UnlawfulCivilianKills");
                killer.recordLegacyEvent("Killed non-combatant " + victim.getFighterName());
            } else if (ally) {
                increment(legacy, "UnlawfulAllyKills");
                killer.recordLegacyEvent("Killed ally " + victim.getFighterName());
            } else if (victim.getAlignment() != FighterAlignment.BAD
                    && killer.getAlignment() == FighterAlignment.BAD) {
                increment(legacy, "UnlawfulNeutralKills");
                killer.recordLegacyEvent("Killed " + victim.getFighterName() + " outside a faction war");
            } else {
                return;
            }
            evaluate(killer);
            return;
        }

        // Killing the player is only treated as criminal behavior for an already hostile/bad
        // unsanctioned fighter; losing a fight the player started does not manufacture a crime.
        if (event.getEntity() instanceof ServerPlayer player && attacker instanceof AmbientFighterEntity killer) {
            if (WorldMenaceManager.isWorldMenace(killer)) return;
            if (killer.isSanctionedMatchParticipant() || killer.isDuelOpponent(player) || killer.getAlignment() != FighterAlignment.BAD) return;
            increment(killer.getLegacyData(), "UnlawfulPlayerKills");
            killer.recordLegacyEvent("Killed " + player.getGameProfile().getName() + " outside sanctioned combat");
            evaluate(killer);
            return;
        }


    }

    private static String stableWantedId(AmbientFighterEntity fighter) {
        // Once an entity has entered the registry its wanted id is part of that person's identity.
        // Do not recompute it later when a memory record is created, or the same criminal can appear twice.
        if (fighter.isWanted() && fighter.getWantedId() != null && !fighter.getWantedId().isBlank())
            return fighter.getWantedId();
        CompoundTag legacy = fighter.getLegacyData();
        if (!legacy.hasUUID("NpcSocialIdentity")) legacy.putUUID("NpcSocialIdentity", fighter.getUUID());
        return "fighter_" + legacy.getUUID("NpcSocialIdentity");
    }

    private static void increment(CompoundTag tag, String key) {
        int value = Math.max(0, tag.getInt(key));
        if (value < Integer.MAX_VALUE) tag.putInt(key, value + 1);
    }

    private static String crimeSummary(int civilian, int betrayal, int player, int neutral) {
        if (civilian > 0) return "killing " + civilian + " non-combatant" + (civilian == 1 ? "" : "s");
        if (betrayal > 0) return "killing " + betrayal + " " + (betrayal == 1 ? "ally" : "allies");
        if (player > 0) return "killing the player " + player + " time" + (player == 1 ? "" : "s");
        return "killing " + Math.max(1, neutral) + " non-hostile fighter" + (neutral == 1 ? "" : "s");
    }
}
