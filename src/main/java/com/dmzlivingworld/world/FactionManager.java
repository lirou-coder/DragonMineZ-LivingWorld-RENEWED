package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterRank;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.Animal;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

/** Social rules, hierarchy and lightweight persistent organization simulation. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FactionManager {
    private static final String PLAYER_ROOT = "DMZLivingWorldFactionReputation";
    public static final int HOSTILE_REP = -40;
    public static final int FRIENDLY_REP = 35;

    private FactionManager() {}

    public static List<WorldFaction> factions(ServerLevel level) {
        return FactionWorldData.get(level).activeFactions();
    }

    public static List<WorldFaction> factionsForRealm(ServerLevel level) {
        return FactionWorldData.get(level).factions(LivingWorldDimensions.realm(level));
    }

    public static WorldFaction byId(ServerLevel level, String id) { return FactionWorldData.get(level).byId(id); }
    public static WorldFaction bySlot(ServerLevel level, int slot) { return FactionWorldData.get(level).bySlot(slot); }

    public static FactionRelation relation(ServerLevel level, WorldFaction a, WorldFaction b) {
        if (a == null || b == null) return FactionRelation.NEUTRAL;
        if (a.id().equals(b.id())) return FactionRelation.SAME;
        // The two permanent Earth institutions are ideological opposites by design.
        if ((FactionWorldData.EARTH_GUARDIANS_ID.equals(a.id()) && FactionWorldData.BLACK_SUN_ID.equals(b.id()))
                || (FactionWorldData.BLACK_SUN_ID.equals(a.id()) && FactionWorldData.EARTH_GUARDIANS_ID.equals(b.id())))
            return FactionRelation.ENEMY;
        FactionRelation base = baseRelation(level, a, b);
        int score = relationScore(base) + FactionWorldData.get(level).relationShift(a, b);
        if (score >= 55) return FactionRelation.ALLY;
        if (score >= 20) return FactionRelation.FRIENDLY;
        if (score > -20) return FactionRelation.NEUTRAL;
        if (score > -55) return FactionRelation.RIVAL;
        return FactionRelation.ENEMY;
    }

    private static int relationScore(FactionRelation relation) {
        return switch (relation) {
            case SAME, ALLY -> 70;
            case FRIENDLY -> 35;
            case NEUTRAL -> 0;
            case RIVAL -> -35;
            case ENEMY -> -70;
        };
    }

    private static FactionRelation baseRelation(ServerLevel level, WorldFaction a, WorldFaction b) {
        int low = Math.min(a.slot(), b.slot());
        int high = Math.max(a.slot(), b.slot());
        long mixed = FactionWorldData.mix(level.getServer().overworld().getSeed()
                ^ (0x9E3779B97F4A7C15L * low)
                ^ (0xC2B2AE3D27D4EB4FL * high));
        int roll = (int)Math.floorMod(mixed, 100L);

        if (a.realm() != b.realm()) {
            if (roll < 10) return FactionRelation.ALLY;
            if (roll < 23) return FactionRelation.FRIENDLY;
            if (roll < 64) return FactionRelation.NEUTRAL;
            if (roll < 84) return FactionRelation.RIVAL;
            return FactionRelation.ENEMY;
        }
        if ((a.alignment() == FighterAlignment.GOOD && b.alignment() == FighterAlignment.BAD)
                || (a.alignment() == FighterAlignment.BAD && b.alignment() == FighterAlignment.GOOD)) {
            if (roll < 68) return FactionRelation.ENEMY;
            if (roll < 90) return FactionRelation.RIVAL;
            return FactionRelation.NEUTRAL;
        }
        if (a.alignment() == FighterAlignment.BAD && b.alignment() == FighterAlignment.BAD) {
            if (roll < 30) return FactionRelation.ENEMY;
            if (roll < 64) return FactionRelation.RIVAL;
            if (roll < 74) return FactionRelation.ALLY;
            return FactionRelation.NEUTRAL;
        }
        if (a.alignment() == b.alignment()) {
            if (roll < 24) return FactionRelation.ALLY;
            if (roll < 40) return FactionRelation.FRIENDLY;
            if (roll < 63) return FactionRelation.NEUTRAL;
            if (roll < 91) return FactionRelation.RIVAL;
            return FactionRelation.ENEMY;
        }
        if (roll < 13) return FactionRelation.ALLY;
        if (roll < 29) return FactionRelation.FRIENDLY;
        if (roll < 58) return FactionRelation.NEUTRAL;
        if (roll < 82) return FactionRelation.RIVAL;
        return FactionRelation.ENEMY;
    }

    public static FactionRelation relation(ServerLevel level, AmbientFighterEntity a, AmbientFighterEntity b) {
        if (!a.isFactionMember() || !b.isFactionMember()) return FactionRelation.NEUTRAL;
        return relation(level, byId(level, a.getFactionId()), byId(level, b.getFactionId()));
    }

    public static boolean areAllies(AmbientFighterEntity a, AmbientFighterEntity b) {
        if (a == null || b == null || !a.isFactionMember() || !b.isFactionMember()) return false;
        if (a.getFactionId().equals(b.getFactionId())) return true;
        if (!(a.level() instanceof ServerLevel level)) return false;
        return relation(level, a, b).allied();
    }

    public static boolean areEnemies(AmbientFighterEntity a, AmbientFighterEntity b) {
        if (a == null || b == null || !a.isFactionMember() || !b.isFactionMember()) return false;
        if (a.getFactionId().equals(b.getFactionId())) return false;
        if (!(a.level() instanceof ServerLevel level)) return false;
        return relation(level, a, b).hostile();
    }

    public static boolean areRivals(AmbientFighterEntity a, AmbientFighterEntity b) {
        if (a == null || b == null || !a.isFactionMember() || !b.isFactionMember()) return false;
        if (!(a.level() instanceof ServerLevel level)) return false;
        return relation(level, a, b).rivalry();
    }

    public static int getReputation(ServerPlayer player, WorldFaction faction) {
        CompoundTag root = repRoot(player);
        if (!root.contains(faction.id(), Tag.TAG_INT)) {
            int initial = initialReputation(player, faction);
            root.putInt(faction.id(), initial);
            player.getPersistentData().put(PLAYER_ROOT, root);
            return initial;
        }
        return root.getInt(faction.id());
    }

    public static int getReputation(ServerPlayer player, String factionId) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction faction = byId(level, factionId);
        return faction == null ? 0 : getReputation(player, faction);
    }

    public static int adjustReputation(ServerPlayer player, WorldFaction faction, int delta) {
        if (player == null || faction == null || delta == 0) return getReputation(player, faction);
        CompoundTag root = repRoot(player);
        int value = clamp(getReputation(player, faction) + delta, -100, 100);
        root.putInt(faction.id(), value);
        player.getPersistentData().put(PLAYER_ROOT, root);
        return value;
    }

    /**
     * Small secondary consequence through the faction graph. This deliberately never rewards the
     * player with an enemy faction merely for killing that enemy's rival; only allies/friends of
     * the directly affected organization inherit a weaker version of the same opinion shift.
     */
    public static void propagatePlayerFactionConsequence(ServerPlayer player, WorldFaction source, int directDelta) {
        if (player == null || source == null || Math.abs(directDelta) < 5 || !(player.level() instanceof ServerLevel level)) return;
        for (WorldFaction other : FactionWorldData.get(level).activeFactions()) {
            if (other.id().equals(source.id()) || other.realm() != source.realm()) continue;
            FactionRelation relation = relation(level, source, other);
            int amount = 0;
            if (relation == FactionRelation.ALLY) {
                amount = Math.max(1, Math.min(3, Math.abs(directDelta) / 8));
            } else if (relation == FactionRelation.FRIENDLY && Math.abs(directDelta) >= 8) {
                amount = Math.max(1, Math.min(2, Math.abs(directDelta) / 14));
            }
            if (amount <= 0) continue;
            adjustReputation(player, other, directDelta > 0 ? amount : -amount);
        }
    }

    public static String reputationLabel(int rep) {
        if (rep <= -70) return "Hated";
        if (rep <= HOSTILE_REP) return "Hostile";
        if (rep <= -15) return "Suspicious";
        if (rep < 15) return "Neutral";
        if (rep < FRIENDLY_REP) return "Warm";
        if (rep < 70) return "Friendly";
        return "Trusted";
    }

    public static boolean shouldAttackPlayer(AmbientFighterEntity fighter, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !fighter.isFactionMember()) return false;
        if (!(fighter.level() instanceof ServerLevel level)) return false;
        if (MercyManager.isMercyPeaceActive(fighter, serverPlayer)) return false;
        // Authored quest allies/contacts must never auto-aggro because of broad faction hostility or Wanted state.
        // Direct retaliation still works through HurtByTarget if the player attacks them first.
        if (fighter.getStoryRole() == AmbientFighterEntity.STORY_ALLY
                || fighter.getStoryRole() == AmbientFighterEntity.STORY_CAPTIVE) return false;
        // Covert quest patrols begin unaware even if their faction normally hates the player.
        // Their own LOS/suspicion state machine is authoritative until the player is actually identified.
        if (FactionRequestManager.suppressAutomaticCovertAggression(serverPlayer, fighter)) return false;

        // Personal history is allowed to matter more than broad faction policy for an individual.
        // Explicit story enemies are authored conflicts and do not get silently neutralized here.
        if (fighter.getStoryRole() != AmbientFighterEntity.STORY_ENEMY && fighter.isRememberedFor(serverPlayer)) {
            int relationship = fighter.getMemoryRelationship();
            if (relationship >= 35) return false;
            if (relationship <= -35) {
                if (PlayerWorldManager.shouldFearPlayer(fighter, serverPlayer)) {
                    if (fighter.getSpeech().isEmpty()) fighter.speak("...Not worth dying over.", 48);
                    return false;
                }
                return true;
            }
        }

        WorldFaction faction = byId(level, fighter.getFactionId());
        if (faction == null) return false;
        boolean wantedPursuit = WantedManager.shouldFactionPursuePlayer(faction, serverPlayer);
        if (!wantedPursuit && getReputation(serverPlayer, faction) > HOSTILE_REP) return false;
        if (PlayerWorldManager.shouldFearPlayer(fighter, serverPlayer)) {
            if (fighter.getSpeech().isEmpty()) fighter.speak("...Not worth dying over.", 48);
            return false;
        }
        return true;
    }

    public static void onPlayerHitMember(ServerPlayer player, AmbientFighterEntity member) {
        if (!member.isFactionMember() || !(member.level() instanceof ServerLevel level)) return;
        WorldFaction faction = byId(level, member.getFactionId());
        if (faction == null) return;
        if (MercyManager.shouldSuppressFactionHitPenalty(player, member)) return;
        int penalty = switch (member.getFactionRole()) {
            case RECRUIT -> -2; case MEMBER -> -4; case ENFORCER -> -5; case LIEUTENANT -> -7; case LEADER -> -9;
        };
        adjustReputation(player, faction, penalty);
    }

    public static void onMemberRescued(ServerPlayer player, AmbientFighterEntity member) {
        if (!member.isFactionMember() || !(member.level() instanceof ServerLevel level)) return;
        WorldFaction faction = byId(level, member.getFactionId());
        if (faction != null) {
            int gain = member.getFactionRole().ordinal() >= FactionRole.LIEUTENANT.ordinal() ? 24 : 18;
            adjustReputation(player, faction, gain);
            propagatePlayerFactionConsequence(player, faction, gain);
            PlayerWorldManager.discoverFaction(player, faction);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AmbientFighterEntity fighter) || !fighter.isFactionMember()) return;
        if (!(fighter.level() instanceof ServerLevel level)) return;
        FactionWorldData data = FactionWorldData.get(level);
        WorldFaction faction = byId(level, fighter.getFactionId());
        if (faction == null) return;

        data.markResidentFallen(faction, fighter, level.getServer().overworld().getGameTime());
        PrisonerWorldData.Prisoner activePrisoner = PrisonerWorldData.get(level).active().stream()
                .filter(p -> p.entityId != null && p.entityId.equals(fighter.getUUID())).findFirst().orElse(null);
        if (activePrisoner != null && activePrisoner.populationRemoved)
            data.recordCasualtyAlreadyAbsent(faction, fighter.getFactionRole(), fighter.isNonCombatant());
        else data.recordCasualty(faction, fighter.getFactionRole(), fighter.isNonCombatant());
        if (activePrisoner != null)
            PrisonerWorldData.get(level).markDead(level, activePrisoner, level.getServer().overworld().getGameTime(), "the physical captive died");
        if (fighter.isNonCombatant() || fighter.getFactionRole().ordinal() >= FactionRole.ENFORCER.ordinal()) {
            data.addHistory(faction, level.getServer().overworld().getGameTime(),
                    fighter.getFighterName() + " died (" + (fighter.isNonCombatant() ? "Resident" : faction.roleTitle(fighter.getFactionRole())) + ").");
        }
        long now = level.getServer().overworld().getGameTime();
        if (fighter.isFactionLeader()) {
            AmbientFighterEntity candidate = level.getEntitiesOfClass(AmbientFighterEntity.class,
                    fighter.getBoundingBox().inflate(160.0D), other -> other != fighter && other.isAlive()
                            && other.isFactionMember() && faction.id().equals(other.getFactionId())
                            && other.getFactionRole().ordinal() >= FactionRole.ENFORCER.ordinal())
                    .stream().max(Comparator.comparingInt(other -> other.getFactionRole().id() * 100 + other.getFactionMerit())).orElse(null);
            if (candidate != null) data.nominateSuccessor(faction, candidate, now);
            data.markLeaderKilled(faction, fighter.blockPosition(), now);
        } else {
            data.clearSuccessionCandidateIf(faction, fighter.getFighterName(), now);
        }

        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            int penalty = switch (fighter.getFactionRole()) {
                case RECRUIT -> -10; case MEMBER -> -18; case ENFORCER -> -24; case LIEUTENANT -> -32; case LEADER -> -48;
            };
            adjustReputation(player, faction, penalty);
            propagatePlayerFactionConsequence(player, faction, penalty);
            FactionRequestManager.onFactionMemberKilled(player, fighter);

            // The direct victim faction remains authoritative; R18 only lets its established
            // allies/friends inherit a much smaller secondary opinion shift.
        } else if (event.getSource().getEntity() instanceof AmbientFighterEntity killer && killer.isFactionMember()) {
            WorldFaction killerFaction = byId(level, killer.getFactionId());
            if (killerFaction != null && !killerFaction.id().equals(faction.id())) {
                data.recordVictory(killerFaction, fighter.getFactionRole());
                int relationDamage = switch (fighter.getFactionRole()) {
                    case RECRUIT -> -3; case MEMBER -> -5; case ENFORCER -> -7;
                    case LIEUTENANT -> -11; case LEADER -> -18;
                };
                data.adjustRelation(killerFaction, faction, relationDamage, now, null);
                killer.addFactionMerit(1 + fighter.getFactionRole().id());
                tryPromote(killer, killerFaction);
            }
        }
    }

    @SubscribeEvent
    public static void onAnimalDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Animal)) return;
        if (!(event.getSource().getEntity() instanceof AmbientFighterEntity hunter) || !hunter.isFactionMember()) return;
        if (!(hunter.level() instanceof ServerLevel level)) return;
        WorldFaction faction = byId(level, hunter.getFactionId());
        if (faction == null) return;
        FactionWorldData data = FactionWorldData.get(level);
        data.addSupplies(faction, 4 + hunter.getRandom().nextInt(4));
        if (hunter.getSpeech().isEmpty() && data.supplies(faction) < 35) hunter.speak("This should keep us fed.", 45);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer original) || !(event.getEntity() instanceof ServerPlayer copy)) return;
        CompoundTag old = original.getPersistentData();
        if (old.contains(PLAYER_ROOT, Tag.TAG_COMPOUND)) {
            copy.getPersistentData().put(PLAYER_ROOT, old.getCompound(PLAYER_ROOT).copy());
        }
    }

    /** Pick a group belonging to the player's current realm with a strong local-region bias. */
    public static WorldFaction pickFactionFor(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) return null;
        List<WorldFaction> available = factionsForRealm(level);
        if (available.isEmpty()) return null;
        if (player.getRandom().nextFloat() < 0.82F) {
            List<WorldFaction> local = available.stream()
                    .sorted(Comparator.comparingDouble(f -> horizontalDistanceSq(player.getX(), player.getZ(), f.roamX(), f.roamZ())))
                    .limit(Math.min(3, available.size())).toList();
            float localRoll = player.getRandom().nextFloat();
            int index = localRoll < 0.72F ? 0 : localRoll < 0.93F ? 1 : 2;
            return local.get(Math.min(index, local.size() - 1));
        }
        return available.get(player.getRandom().nextInt(available.size()));
    }

    public static WorldFaction pickRelatedFaction(ServerLevel level, WorldFaction source, RandomSource random, boolean wantConflict) {
        List<WorldFaction> candidates = factions(level).stream()
                .filter(f -> f.realm() == source.realm())
                .filter(f -> !f.id().equals(source.id()))
                .filter(f -> !wantConflict || relation(level, source, f).rivalry()).toList();
        if (candidates.isEmpty() && wantConflict) {
            candidates = factions(level).stream()
                    .filter(f -> f.realm() == source.realm() && !f.id().equals(source.id())).toList();
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    public static FactionRole rollMemberRole(WorldFaction faction, RandomSource random, boolean captain) {
        if (captain) {
            int roll = random.nextInt(100);
            return roll < 24 ? FactionRole.LIEUTENANT : roll < 78 ? FactionRole.ENFORCER : FactionRole.MEMBER;
        }
        int roll = random.nextInt(100);
        if (roll < 20) return FactionRole.RECRUIT;
        if (roll < 70) return FactionRole.MEMBER;
        if (roll < 94) return FactionRole.ENFORCER;
        return FactionRole.LIEUTENANT;
    }

    public static FighterRank rankForRole(WorldFaction faction, FactionRole role, RandomSource random) {
        return switch (role) {
            case RECRUIT -> FighterRank.ROOKIE;
            case MEMBER -> rollMemberRank(faction, random);
            case ENFORCER -> random.nextFloat() < Math.max(0.08F, faction.powerBias() - 0.75F) ? FighterRank.VETERAN : FighterRank.TRAINED;
            case LIEUTENANT, LEADER -> FighterRank.VETERAN;
        };
    }

    public static FighterRank rollMemberRank(WorldFaction faction, RandomSource random) {
        float bias = faction.powerBias();
        int roll = random.nextInt(100);
        int veteran = 6 + Math.round(Math.max(0F, bias - 1F) * 24F);
        int trained = 47 + Math.round((bias - 0.68F) * 22F);
        if (roll < veteran) return FighterRank.VETERAN;
        if (roll < Math.min(92, veteran + trained)) return FighterRank.TRAINED;
        return FighterRank.ROOKIE;
    }

    public static float effectiveFactionPower(ServerLevel level, WorldFaction faction) {
        return faction.powerBias() * FactionWorldData.get(level).momentum(faction);
    }

    public static void tryRecruitWanderer(AmbientFighterEntity wanderer) {
        if (wanderer == null || WorldMenaceManager.isWorldMenace(wanderer) || wanderer.isFactionMember() || wanderer.isWanted() || wanderer.isCaptive()
                || wanderer.isDefeated() || wanderer.getTarget() != null || !(wanderer.level() instanceof ServerLevel level)) return;
        List<AmbientFighterEntity> recruiters = level.getEntitiesOfClass(AmbientFighterEntity.class,
                wanderer.getBoundingBox().inflate(18.0D), other -> other != wanderer && other.isAlive()
                        && other.isFactionMember() && !other.isNonCombatant());
        if (recruiters.isEmpty()) return;
        AmbientFighterEntity recruiter = recruiters.stream().min(Comparator.comparingDouble(wanderer::distanceToSqr)).orElse(null);
        if (recruiter == null) return;
        WorldFaction faction = byId(level, recruiter.getFactionId());
        if (faction == null || FactionWorldData.get(level).isExtinct(faction)) return;
        if (wanderer.getAlignment() != FighterAlignment.NEUTRAL && faction.alignment() != FighterAlignment.NEUTRAL
                && wanderer.getAlignment() != faction.alignment()) return;
        float chance = wanderer.isNonCombatant() ? 0.10F : 0.065F;
        if (wanderer.getPersonality() == com.dmzlivingworld.entity.FighterPersonality.CAUTIOUS) chance *= 0.60F;
        if (wanderer.getRandom().nextFloat() >= chance) return;
        FactionRole role = wanderer.isNonCombatant() ? FactionRole.RECRUIT
                : (wanderer.getRank() == FighterRank.VETERAN ? FactionRole.MEMBER : FactionRole.RECRUIT);
        wanderer.assignFaction(faction, role, recruiter.getPartyId(), false, recruiter.isRegionalPresence());
        if (wanderer.isRegionalPresence()) FactionWorldData.get(level).recordResident(faction, wanderer);
        FactionWorldData.get(level).addPopulation(faction, wanderer.isNonCombatant(), 1,
                level.getServer().overworld().getGameTime(), wanderer.getFighterName() + " joined from the unaffiliated population.");
        wanderer.speak("I'll run with " + faction.name() + ".", 65);
    }

    /** Called from fighter AI at low frequency. */
    public static void tickMember(AmbientFighterEntity fighter) {
        if (!fighter.isFactionMember() || !(fighter.level() instanceof ServerLevel level)) return;
        WorldFaction faction = byId(level, fighter.getFactionId());
        if (faction == null) return;
        if (FactionWorldData.get(level).isExtinct(faction)) {
            fighter.leaveFaction();
            if (fighter.getSpeech().isEmpty()) fighter.speak("It's over. We scattered.", 65);
            return;
        }
        if (fighter.getFactionDisplayName().isBlank() || fighter.getFactionTitle().isBlank()) {
            fighter.setFactionRole(fighter.getFactionRole());
        }
        FactionWorldData org = FactionWorldData.get(level);
        if (!fighter.isFactionLeader() && !org.isLeaderKilled(faction)
                && org.matchesCurrentLeader(faction, fighter)
                && fighter.getFactionRole().ordinal() >= FactionRole.ENFORCER.ordinal()) {
            fighter.setFactionRole(FactionRole.LEADER);
            fighter.flareAura(110);
            fighter.speak("I'll lead us from here.", 75);
            org.markLeaderSpawned(faction, fighter.blockPosition());
            org.addHistory(faction, level.getServer().overworld().getGameTime(),
                    fighter.getFighterName() + " assumed leadership as " + faction.roleTitle(FactionRole.LEADER) + ".");
        }

        if (fighter.isFactionLeader() && fighter.tickCount % 200 == 0) {
            FactionWorldData.get(level).updateLeaderPos(faction, fighter.blockPosition(), level.getServer().overworld().getGameTime());
        }
        if (fighter.isRegionalPresence() && fighter.isPartyCaptain() && fighter.tickCount % 200 == 0) {
            FactionWorldData.get(level).updatePresencePos(faction, fighter.blockPosition());
        }
        if (fighter.isRegionalPresence() && fighter.tickCount % 200 == Math.floorMod(fighter.getUUID().hashCode(), 200)) {
            org.recordResident(faction, fighter);
        }
        // Request assignment temporarily owns behavior. Keep identity/leader/resident persistence above,
        // but do not let ordinary faction support, roaming, food hunting, rogue hunting or defection
        // compete with the mission state machine below this point.
        if (FactionRequestMissionManager.isAssigned(fighter)) return;
        if (fighter.tickCount % 32 == Math.floorMod(fighter.getUUID().hashCode(), 32)) spreadPartyMembers(fighter);
        if (fighter.getTarget() == null && fighter.tickCount % 64 == Math.floorMod(fighter.getUUID().hashCode(), 64)) supportFriendlyPlayer(fighter, faction);
        if (fighter.getTarget() == null && !fighter.isNonCombatant()
                && fighter.tickCount % 320 == Math.floorMod(fighter.getUUID().hashCode(), 320)) huntNearbyRogue(fighter, faction);
        if (fighter.getTarget() == null && fighter.tickCount % 120 == Math.floorMod(fighter.getUUID().hashCode(), 120)) roamWithParty(fighter);

        if (fighter.getTarget() == null && !fighter.isNonCombatant()
                && fighter.tickCount % 360 == Math.floorMod(fighter.getUUID().hashCode(), 360)
                && FactionWorldData.get(level).supplies(faction) < 28) {
            huntForFood(fighter, faction);
        }
        if (!fighter.isFactionLeader() && fighter.tickCount % 6000 == Math.floorMod(fighter.getUUID().hashCode(), 6000)) {
            maybeDefect(fighter, faction);
        }

        // Persistent residents must not fossilize at the PL they had when first discovered.
        // Out of combat, they slowly re-anchor to the world's current DMZ power curve and
        // the organization's own momentum. A real training/battle gain is an individual fact
        // about this person, however, and must never be pulled back down by this maintenance.
        if (fighter.getTarget() == null && fighter.tickCount % 1200 == Math.floorMod(fighter.getUUID().hashCode(), 1200)) {
            long identitySeed = fighter.getUUID().getMostSignificantBits()
                    ^ Long.rotateLeft(fighter.getUUID().getLeastSignificantBits(), 19);
            RandomSource stable = RandomSource.create(identitySeed);
            int base = WorldPowerScaler.rollWorldBattlePower(level, fighter.blockPosition(), fighter.getRank(), stable);
            double desired = base * effectiveFactionPower(level, faction) * fighter.getFactionRole().powerMultiplier();
            if (fighter.isAwakened()) desired *= fighter.getRank() == FighterRank.VETERAN ? 1.72D : 1.42D;
            int current = fighter.getPermanentBattlePower();
            int target = (int)Math.min(Integer.MAX_VALUE - 1L, Math.max(1L, Math.round(desired)));
            int earnedFloor = fighter.getEarnedBattlePowerFloor();
            if (earnedFloor > 0) target = Math.max(target, earnedFloor);
            if (Math.abs(target - current) > Math.max(75, current * 0.08D)) {
                int nudged = (int)Math.max(1L, Math.round(current + (target - current) * 0.14D));
                fighter.setBattlePowerAndRefresh(nudged);
            }
        }
    }

    public static void onConcessionVictory(AmbientFighterEntity winner, AmbientFighterEntity loser) {
        if (winner == null || loser == null || !winner.isFactionMember() || winner.isNonCombatant()) return;
        if (!(winner.level() instanceof ServerLevel level)) return;
        WorldFaction faction = byId(level, winner.getFactionId());
        if (faction == null) return;
        boolean crossFaction = loser.isFactionMember() && !winner.getFactionId().equals(loser.getFactionId());
        int merit = crossFaction ? 2 : 1;
        winner.addFactionMerit(merit);
        winner.applyTrainingGrowth(crossFaction ? 650 : 500, false);
        if (loser.isAlive()) loser.applyTrainingGrowth(crossFaction ? 360 : 280, false);
        FighterGoalManager.onBattleVictory(winner, loser);
        if (loser.isAlive() && loser.getRivalName().equals(winner.getFighterName()) && loser.getRandom().nextFloat() < 0.16F) {
            FighterTechniqueManager.tryLearnFrom(loser, winner, "rival observation");
        }
        if (crossFaction && winner.getRandom().nextFloat() < 0.16F && winner.getRivalName().isBlank() && loser.getRivalName().isBlank()) {
            winner.setRivalName(loser.getFighterName());
            loser.setRivalName(winner.getFighterName());
            if (winner.getSpeech().isEmpty()) winner.speak("We're not finished. Remember my name.", 68);
        }
        if (crossFaction) {
            WorldFaction loserFaction = byId(level, loser.getFactionId());
            if (loserFaction != null) FactionWorldData.get(level).adjustRelation(faction, loserFaction, -1,
                    level.getServer().overworld().getGameTime(), null);
        }
        tryPromote(winner, faction);
    }

    private static void tryPromote(AmbientFighterEntity fighter, WorldFaction faction) {
        if (fighter == null || faction == null || fighter.isFactionLeader() || fighter.isNonCombatant()) return;
        FactionRole role = fighter.getFactionRole();
        int merit = fighter.getFactionMerit();
        FactionRole next = null;
        if (role == FactionRole.RECRUIT && merit >= 3) next = FactionRole.MEMBER;
        else if (role == FactionRole.MEMBER && merit >= 7) next = FactionRole.ENFORCER;
        else if (role == FactionRole.ENFORCER && merit >= 14) next = FactionRole.LIEUTENANT;
        if (next == null) return;
        fighter.setFactionRole(next);
        if (fighter.level() instanceof ServerLevel residentLevel && fighter.isRegionalPresence())
            FactionWorldData.get(residentLevel).recordResident(faction, fighter);
        fighter.flareAura(70);
        fighter.speak("I've earned my place.", 55);
        if (fighter.level() instanceof ServerLevel level) {
            FactionWorldData.get(level).addHistory(faction, level.getServer().overworld().getGameTime(),
                    fighter.getFighterName() + " was promoted to " + faction.roleTitle(next) + ".");
        }
    }

    private static void maybeDefect(AmbientFighterEntity fighter, WorldFaction faction) {
        if (!(fighter.level() instanceof ServerLevel level) || fighter.isFactionLeader() || fighter.getTarget() != null) return;
        FactionWorldData data = FactionWorldData.get(level);
        float momentum = data.momentum(faction);
        float chance = momentum < 0.72F ? 0.13F : momentum < 0.84F ? 0.055F : 0.008F;
        if (fighter.getFactionRole() == FactionRole.LIEUTENANT) chance *= 0.55F;
        if (fighter.getRandom().nextFloat() >= chance) return;
        List<WorldFaction> options = data.factions(faction.realm()).stream()
                .filter(other -> !other.id().equals(faction.id()))
                .filter(other -> !relation(level, faction, other).hostile()).toList();
        if (options.isEmpty()) return;
        WorldFaction target = options.get(fighter.getRandom().nextInt(options.size()));
        boolean civilian = fighter.isNonCombatant();
        long now = level.getServer().overworld().getGameTime();
        data.clearSuccessionCandidateIf(faction, fighter.getFighterName(), now);
        data.markResidentDeparted(faction, fighter, now);
        data.transferPopulation(faction, target, civilian, now, fighter.getFighterName());
        FactionRole newRole = civilian ? FactionRole.RECRUIT
                : fighter.getFactionRole() == FactionRole.LIEUTENANT ? FactionRole.ENFORCER : fighter.getFactionRole();
        fighter.assignFaction(target, newRole, null, false, fighter.isRegionalPresence());
        if (fighter.isRegionalPresence()) data.recordResident(target, fighter);
        fighter.speak("I'm done with " + faction.name() + ".", 75);
    }

    private static void huntNearbyRogue(AmbientFighterEntity fighter, WorldFaction faction) {
        boolean hunterCulture = faction.alignment() == FighterAlignment.GOOD
                || faction.structure() == FactionStructure.GUARD
                || faction.structure() == FactionStructure.ORDER
                || faction.ethos() == FactionEthos.WANDERING_GUARD
                || faction.ethos() == FactionEthos.NAMEK_WARDENS;
        if (!hunterCulture) return;
        List<AmbientFighterEntity> rogues = fighter.level().getEntitiesOfClass(AmbientFighterEntity.class,
                fighter.getBoundingBox().inflate(44.0D), other -> other != fighter && other.isAlive() && other.isWanted());
        AmbientFighterEntity rogue = rogues.stream().min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (rogue != null && fighter.canAttack(rogue)) {
            fighter.setTarget(rogue);
            if (fighter.getSpeech().isEmpty() && fighter.getRandom().nextFloat() < 0.38F)
                fighter.speak("That's the wanted one.", 48);
        }
    }

    private static void huntForFood(AmbientFighterEntity fighter, WorldFaction faction) {
        // Namekians in Dragon Ball do not need meat; their faction's abstract provision/foraging
        // simulation handles water/produce rather than making them slaughter animals for food.
        if (fighter.getRace() == com.dmzlivingworld.entity.FighterRace.NAMEKIAN) return;
        List<Animal> animals = fighter.level().getEntitiesOfClass(Animal.class, fighter.getBoundingBox().inflate(28.0D), Animal::isAlive);
        if (animals.isEmpty()) return;
        Animal prey = animals.stream().min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (prey != null && fighter.canAttack(prey)) {
            fighter.setTarget(prey);
            if (fighter.getSpeech().isEmpty() && fighter.getRandom().nextFloat() < 0.25F) fighter.speak("We need provisions.", 44);
        }
    }

    private static void spreadPartyMembers(AmbientFighterEntity fighter) {
        if (!fighter.hasParty()) return;
        List<AmbientFighterEntity> close = fighter.level().getEntitiesOfClass(
                AmbientFighterEntity.class, fighter.getBoundingBox().inflate(2.35D, 1.5D, 2.35D),
                other -> other != fighter && fighter.sameParty(other) && other.isAlive());
        if (close.isEmpty()) return;
        AmbientFighterEntity other = close.get(0);
        double dx = fighter.getX() - other.getX();
        double dz = fighter.getZ() - other.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05D) {
            dx = fighter.getRandom().nextDouble() - 0.5D; dz = fighter.getRandom().nextDouble() - 0.5D;
            len = Math.max(0.05D, Math.sqrt(dx * dx + dz * dz));
        }
        double strength = fighter.getFactionRole().ordinal() >= FactionRole.ENFORCER.ordinal() ? 0.065D : 0.052D;
        fighter.setDeltaMovement(fighter.getDeltaMovement().add(dx / len * strength, 0.0D, dz / len * strength));
    }

    private static void roamWithParty(AmbientFighterEntity fighter) {
        if (fighter.getTarget() != null || fighter.isDefeated() || fighter.isCaptive()) return;
        if (fighter.hasParty() && !fighter.isPartyCaptain()) {
            List<AmbientFighterEntity> captains = fighter.level().getEntitiesOfClass(
                    AmbientFighterEntity.class, fighter.getBoundingBox().inflate(48.0D),
                    other -> other != fighter && fighter.sameParty(other) && other.isPartyCaptain() && other.isAlive());
            if (!captains.isEmpty()) {
                AmbientFighterEntity captain = captains.get(0);
                if (fighter.distanceToSqr(captain) > 76.0D) fighter.getNavigation().moveTo(captain, 0.86D);
                return;
            }
        }
        BlockPos pos = AmbientFighterSpawner.findSafeGroundAround((ServerLevel)fighter.level(), fighter.blockPosition(),
                fighter.getRandom(), 7, 18, 8);
        if (pos != null) fighter.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.76D);
    }

    private static void supportFriendlyPlayer(AmbientFighterEntity fighter, WorldFaction faction) {
        if (!(fighter.level() instanceof ServerLevel level)) return;
        List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(ServerPlayer.class, fighter.getBoundingBox().inflate(28.0D),
                player -> !player.isSpectator() && !player.isCreative());
        ServerPlayer nearest = nearbyPlayers.stream().min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (nearest == null || getReputation(nearest, faction) < FRIENDLY_REP) return;
        List<AmbientFighterEntity> threats = level.getEntitiesOfClass(AmbientFighterEntity.class,
                fighter.getBoundingBox().inflate(28.0D), other -> other != fighter && other.isAlive()
                        && other.getTarget() == nearest && fighter.canAttack(other));
        if (!threats.isEmpty()) fighter.setTarget(threats.get(0));
    }

    public static String relationSummary(ServerLevel level, WorldFaction faction) {
        StringBuilder out = new StringBuilder();
        for (WorldFaction other : factions(level)) {
            if (other.id().equals(faction.id())) continue;
            FactionRelation relation = relation(level, faction, other);
            if (relation == FactionRelation.NEUTRAL) continue;
            if (out.length() > 0) out.append(", ");
            out.append(other.name()).append("=").append(relation.displayName());
        }
        return out.length() == 0 ? "mostly neutral" : out.toString();
    }

    public static String direction(double fromX, double fromZ, double toX, double toZ) {
        double angle = Math.toDegrees(Math.atan2(toZ - fromZ, toX - fromX));
        angle = (angle + 360.0D) % 360.0D;
        if (angle >= 337.5D || angle < 22.5D) return "E";
        if (angle < 67.5D) return "SE";
        if (angle < 112.5D) return "S";
        if (angle < 157.5D) return "SW";
        if (angle < 202.5D) return "W";
        if (angle < 247.5D) return "NW";
        if (angle < 292.5D) return "N";
        return "NE";
    }

    private static int initialReputation(ServerPlayer player, WorldFaction faction) {
        // The two major Earth institutions have a clear public stance at first contact.
        // Reputation can still move normally through the player's later actions.
        if (FactionWorldData.EARTH_GUARDIANS_ID.equals(faction.id())) return 25;
        if (FactionWorldData.BLACK_SUN_ID.equals(faction.id())) return -45;
        long seed = FactionWorldData.mix(player.serverLevel().getServer().overworld().getSeed()
                ^ player.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 21) ^ faction.seed());
        RandomSource random = RandomSource.create(seed);
        int roll = random.nextInt(100);
        int value;
        if (roll < 14) value = -65 + random.nextInt(26);
        else if (roll < 34) value = -39 + random.nextInt(25);
        else if (roll < 70) value = -14 + random.nextInt(29);
        else if (roll < 91) value = 15 + random.nextInt(25);
        else value = 40 + random.nextInt(26);
        if (faction.alignment() == FighterAlignment.GOOD) value += 5;
        if (faction.alignment() == FighterAlignment.BAD) value -= 6;
        return clamp(value, -75, 75);
    }

    private static CompoundTag repRoot(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(PLAYER_ROOT, Tag.TAG_COMPOUND)) data.put(PLAYER_ROOT, new CompoundTag());
        return data.getCompound(PLAYER_ROOT);
    }

    private static double horizontalDistanceSq(double x, double z, double tx, double tz) {
        double dx = tx - x; double dz = tz - z; return dx * dx + dz * dz;
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
