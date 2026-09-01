package com.dmzlivingworld.world;

import com.dmzlivingworld.compat.MeditationCompat;
import com.dmzlivingworld.config.LivingWorldConfig;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterArchetype;
import com.dmzlivingworld.entity.FighterRank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Procedural organization scenes shared by Earth and Namek. */
public final class FactionEncounterManager {
    private FactionEncounterManager() {}

    public static boolean trySpawnNatural(ServerPlayer player, int capacity) {
        if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level) || capacity < 2) return false;
        WorldFaction faction = FactionManager.pickFactionFor(player);
        if (faction == null || faction.realm() != LivingWorldDimensions.realm(level)
                || FactionWorldData.get(level).isExtinct(faction)) return false;
        RandomSource random = player.getRandom();
        FactionWorldData data = FactionWorldData.get(level);
        data.tickOrganizations(level);
        if (FactionActivityRegistry.isBusy(level, faction)) return false;

        long worldTime = level.getServer().overworld().getGameTime();
        List<WorldFaction> warEnemies = data.warEnemies(faction, worldTime);
        if (capacity >= 6 && !warEnemies.isEmpty() && random.nextFloat() < 0.42F) {
            WorldFaction enemy = warEnemies.get(random.nextInt(warEnemies.size()));
            if (spawnFactionClash(player, faction, enemy, false) > 0) return true;
        }

        double regionDistance = Math.sqrt(distanceSq(player.getX(), player.getZ(), faction.roamX(), faction.roamZ()));
        float leaderPresenceChance = AntagonistManager.isAntagonistFaction(level, faction) ? 0.10F : 0.055F;
        if (capacity >= 5 && regionDistance < Math.max(1200.0D, faction.roamRadius() * 1.6D)
                && !data.isLeaderSpawned(faction) && !data.isLeaderKilled(faction)
                && random.nextFloat() < leaderPresenceChance) {
            return spawnLeaderEntourage(player, faction, false) > 0;
        }

        float roll = random.nextFloat();
        if (capacity >= 4 && data.supplies(faction) < 34 && roll < 0.18F
                && spawnForagingParty(player, faction, false) > 0) return true;
        if (capacity >= 6 && roll < 0.30F) {
            WorldFaction other = FactionManager.pickRelatedFaction(level, faction, random, true);
            if (other != null && spawnFactionClash(player, faction, other, false) > 0) return true;
        }
        if (capacity >= 3 && roll < 0.50F && spawnTrainingParty(player, faction, false) > 0) return true;
        int size = Math.min(capacity, 4 + random.nextInt(Math.min(4, Math.max(1, capacity - 3))));
        if (AntagonistManager.isAntagonistFaction(level, faction) && size < capacity) size++;
        return spawnPatrol(player, faction, size, false) > 0;
    }

    /**
     * Makes a generated roaming region correspond to an actual local cell when a
     * player visits it. This fixes 0.6.11's misleading "locate an empty coordinate" behavior.
     */
    public static boolean ensureRegionalPresence(ServerPlayer player, WorldFaction faction, boolean debug) {
        return ensureRegionalPresence(player, faction, debug, Integer.MAX_VALUE);
    }

    /** Natural calls pass their remaining local entity budget; debug calls may bypass it. */
    public static boolean ensureRegionalPresence(ServerPlayer player, WorldFaction faction, boolean debug, int maxAdditional) {
        if (!(player.level() instanceof ServerLevel level) || faction == null || maxAdditional <= 0) return false;
        if (faction.realm() != LivingWorldDimensions.realm(level) || FactionWorldData.get(level).isExtinct(faction)) return false;
        double distance = Math.sqrt(distanceSq(player.getX(), player.getZ(), faction.roamX(), faction.roamZ()));
        if (!debug && distance > faction.roamRadius()) return false;

        List<AmbientFighterEntity> loaded = level.getEntitiesOfClass(
                AmbientFighterEntity.class, player.getBoundingBox().inflate(220.0D),
                f -> f.isAlive() && f.isFactionMember() && faction.id().equals(f.getFactionId()));
        FactionWorldData data = FactionWorldData.get(level);
        int adults = Math.max(0, data.fighterPopulation(faction) + data.civilianPopulation(faction));
        int desiredTotal = Math.min(adults, desiredRegionalCellSize(data, faction));
        if (desiredTotal <= 0 || loaded.size() >= desiredTotal) return false;

        long now = level.getServer().overworld().getGameTime();
        if (!debug && now < data.nextPresenceTime(faction)) return false;
        int desired = Math.min(maxAdditional, Math.max(1, desiredTotal - loaded.size()));
        int spawned = spawnRegionalCell(player, faction, desired, debug);
        if (spawned > 0) {
            data.markPresenceSpawned(faction, now, player.blockPosition());
            return true;
        }
        return false;
    }

    public static int spawnRegionalCell(ServerPlayer player, WorldFaction faction, int requestedSize, boolean debug) {
        if (faction == null || !(player.level() instanceof ServerLevel level)
                || faction.realm() != LivingWorldDimensions.realm(level)) return 0;
        BlockPos anchor = AmbientFighterSpawner.findEncounterAnchor(player, debug);
        if (anchor == null) return 0;
        FactionWorldData data = FactionWorldData.get(level);
        int adults = Math.max(1, data.fighterPopulation(faction) + data.civilianPopulation(faction));
        int size = Math.max(1, Math.min(Math.min(15, requestedSize), adults));
        int visibleFighterTarget = Math.min(data.fighterPopulation(faction), Math.max(1, Math.round(size * 0.68F)));
        UUID party = UUID.randomUUID();
        List<AmbientFighterEntity> members = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            boolean civilian = i >= visibleFighterTarget && data.civilianPopulation(faction) > 0;
            FactionRole role;
            if (i == 0) role = size >= 6 ? FactionRole.LIEUTENANT
                    : size >= 3 ? FactionRole.ENFORCER : FactionRole.MEMBER;
            else if (i <= 2 && size >= 5) role = FactionRole.ENFORCER;
            else role = player.getRandom().nextFloat() < 0.32F ? FactionRole.RECRUIT : FactionRole.MEMBER;
            AmbientFighterEntity fighter = spawnMember(player, faction, anchor, party, i == 0, role,
                    civilian ? FighterRank.ROOKIE : null, true);
            if (fighter == null) { cleanup(members); return 0; }
            if (civilian) {
                fighter.setNonCombatant(true);
                data.recordResident(faction, fighter); // refresh after civilian flag is applied
            }
            members.add(fighter);
        }
        reactToPlayer(player, faction, members);
        if (!members.isEmpty() && members.get(0).getSpeech().isEmpty()) {
            members.get(0).speak(homeLine(faction), 62);
        }
        return members.size();
    }

    public static int spawnPatrol(ServerPlayer player, WorldFaction faction, int requestedSize, boolean debug) {
        if (faction == null || !(player.level() instanceof ServerLevel level)
                || faction.realm() != LivingWorldDimensions.realm(level)) return 0;
        if (FactionActivityRegistry.isBusy(level, faction) || hasTransientFactionSceneNearby(player, faction, 260.0D)) return 0;
        BlockPos anchor = AmbientFighterSpawner.findEncounterAnchor(player, debug);
        if (anchor == null) return 0;
        int size = Math.max(3, Math.min(7, requestedSize));
        UUID party = UUID.randomUUID();
        List<AmbientFighterEntity> members = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            FactionRole role = FactionManager.rollMemberRole(faction, player.getRandom(), i == 0);
            AmbientFighterEntity fighter = spawnMember(player, faction, anchor, party, i == 0, role, null, false);
            if (fighter == null) { cleanup(members); return 0; }
            members.add(fighter);
        }
        reactToPlayer(player, faction, members);
        if (!members.isEmpty() && members.get(0).getSpeech().isEmpty() && members.get(0).getRandom().nextFloat() < 0.50F) {
            members.get(0).speak(patrolLine(faction), 54);
        }
        if (!members.isEmpty()) FactionActivityRegistry.acquire(level, faction, debug ? 900L : 1400L);
        return members.size();
    }

    public static int spawnForagingParty(ServerPlayer player, WorldFaction faction, boolean debug) {
        if (faction == null || !(player.level() instanceof ServerLevel level)
                || faction.realm() != LivingWorldDimensions.realm(level) || FactionWorldData.get(level).isExtinct(faction)) return 0;
        if (FactionActivityRegistry.isBusy(level, faction) || hasTransientFactionSceneNearby(player, faction, 260.0D)) return 0;
        BlockPos anchor = AmbientFighterSpawner.findEncounterAnchor(player, debug);
        if (anchor == null) return 0;
        int size = 3 + player.getRandom().nextInt(3);
        UUID party = UUID.randomUUID();
        List<AmbientFighterEntity> members = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            FactionRole role = i == 0 ? FactionRole.ENFORCER : (i == size - 1 && player.getRandom().nextFloat() < 0.45F ? FactionRole.RECRUIT : FactionRole.MEMBER);
            AmbientFighterEntity fighter = spawnMember(player, faction, anchor, party, i == 0, role, null, false);
            if (fighter == null) { cleanup(members); return 0; }
            members.add(fighter);
        }
        members.get(0).speak(FactionWorldData.get(level).supplies(faction) < 20 ? "We need food. Spread out." : "Let's bring something back.", 58);
        FactionActivityRegistry.acquire(level, faction, debug ? 900L : 1600L);
        return members.size();
    }

    public static int spawnTrainingParty(ServerPlayer player, WorldFaction faction, boolean debug) {
        if (faction == null || !(player.level() instanceof ServerLevel level)
                || faction.realm() != LivingWorldDimensions.realm(level)) return 0;
        if (FactionActivityRegistry.isBusy(level, faction) || hasTransientFactionSceneNearby(player, faction, 260.0D)) return 0;
        BlockPos anchor = AmbientFighterSpawner.findEncounterAnchor(player, debug);
        if (anchor == null) return 0;
        UUID party = UUID.randomUUID();
        float meditationChance = switch (faction.structure()) {
            case CULT, ORDER -> 0.72F;
            case SCHOOL, CLAN -> 0.46F;
            case GUARD -> 0.26F;
            default -> 0.13F;
        };
        if (faction.realm() == FactionRealm.NAMEK) meditationChance += 0.16F;
        if (MeditationCompat.isNpcMeditationEnabled() && player.getRandom().nextFloat() < meditationChance) {
            int count = 2 + player.getRandom().nextInt(3);
            List<AmbientFighterEntity> group = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                FactionRole role = i == 0 ? FactionRole.ENFORCER : (i == 1 ? FactionRole.RECRUIT : FactionRole.MEMBER);
                AmbientFighterEntity member = spawnMember(player, faction, anchor, party, i == 0, role,
                        i == 0 ? FighterRank.TRAINED : null, false);
                if (member == null) { cleanup(group); return 0; }
                group.add(member);
            }
            int duration = debug ? 700 : AmbientFighterEntity.naturalMeditationDuration(player.getRandom(), 1400);
            for (AmbientFighterEntity member : group) member.beginMeditation(duration + member.getRandom().nextInt(241), true);
            group.get(0).speak(faction.structure() == FactionStructure.CULT ? "Still your thoughts. Listen inward."
                    : "Settle in. We train the mind too.", 76);
            FactionActivityRegistry.acquire(level, faction, debug ? 1000L : Math.max(3000L, duration + 300L));
            return group.size();
        }

        AmbientFighterEntity instructor = spawnMember(player, faction, anchor, party, true,
                FactionRole.ENFORCER, FighterRank.TRAINED, false);
        AmbientFighterEntity trainee = spawnMember(player, faction, anchor, party, false,
                FactionRole.RECRUIT, FighterRank.ROOKIE, false);
        AmbientFighterEntity observer = player.getRandom().nextFloat() < 0.60F
                ? spawnMember(player, faction, anchor, party, false, FactionRole.MEMBER, null, false) : null;
        if (instructor == null || trainee == null) return cleanup(instructor, trainee, observer);
        instructor.startDuel(trainee); trainee.startDuel(instructor);
        instructor.speak("One clean round. Ready?", 62);
        trainee.speak("Ready.", 52);
        FactionActivityRegistry.acquire(level, faction, debug ? 1100L : 1900L);
        return observer == null ? 2 : 3;
    }

    public static int spawnFactionClash(ServerPlayer player, WorldFaction firstFaction,
                                        WorldFaction secondFaction, boolean debug) {
        if (firstFaction == null || secondFaction == null || !(player.level() instanceof ServerLevel level)) return 0;
        FactionRealm realm = LivingWorldDimensions.realm(level);
        if (firstFaction.realm() != realm || secondFaction.realm() != realm) return 0;
        if (FactionActivityRegistry.pairBusy(level, firstFaction, secondFaction)
                || FactionActivityRegistry.isBusy(level, firstFaction)
                || FactionActivityRegistry.isBusy(level, secondFaction)
                || hasTransientFactionSceneNearby(player, firstFaction, 300.0D)
                || hasTransientFactionSceneNearby(player, secondFaction, 300.0D)) return 0;
        BlockPos anchor = AmbientFighterSpawner.findEncounterAnchor(player, debug);
        if (anchor == null) return 0;
        UUID firstParty = UUID.randomUUID();
        UUID secondParty = UUID.randomUUID();
        List<AmbientFighterEntity> a = spawnClashSide(player, firstFaction, anchor, firstParty);
        List<AmbientFighterEntity> b = spawnClashSide(player, secondFaction, anchor, secondParty);
        if (a.size() != 3 || b.size() != 3) { cleanup(a); cleanup(b); return 0; }

        FactionWorldData worldData = FactionWorldData.get(level);
        boolean war = worldData.isAtWar(firstFaction, secondFaction, level.getServer().overworld().getGameTime());
        FactionRelation relation = FactionManager.relation(level, firstFaction, secondFaction);
        for (int i = 0; i < 3; i++) {
            if (war || relation == FactionRelation.ENEMY) {
                a.get(i).setTarget(b.get(i)); b.get(i).setTarget(a.get(i));
            } else {
                a.get(i).startDuel(b.get(i)); b.get(i).startDuel(a.get(i));
            }
        }
        a.get(0).speak(war ? "War party! Spread out!" : relation == FactionRelation.ENEMY ? "Spread out!" : "Let's settle this properly.", 58);
        b.get(0).speak(war ? "Don't let them through!" : relation == FactionRelation.ENEMY ? "Take them!" : "Finally.", 54);
        FactionActivityRegistry.acquirePair(level, firstFaction, secondFaction, debug ? 1800L : 3000L);
        // Rare third-party intervention is layered on top of the already-working clash,
        // never substituted for its choreography.
        PeacekeeperManager.maybeInterveneInClash(player, firstFaction, secondFaction, a, b, debug);
        String event = war ? "WAR SKIRMISH" : relation == FactionRelation.ENEMY ? "FACTION CLASH" : "RIVAL CLASH";
        WorldEventNotifier.announce(level, anchor, event, firstFaction.name() + " ↔ " + secondFaction.name());
        return 6;
    }

    private static List<AmbientFighterEntity> spawnClashSide(ServerPlayer player, WorldFaction faction,
                                                              BlockPos anchor, UUID party) {
        List<AmbientFighterEntity> out = new ArrayList<>();
        FactionRole[] roles = {FactionRole.ENFORCER, FactionRole.MEMBER, FactionRole.MEMBER};
        for (int i = 0; i < roles.length; i++) {
            AmbientFighterEntity fighter = spawnMember(player, faction, anchor, party, i == 0, roles[i],
                    i == 0 ? FighterRank.TRAINED : null, false);
            if (fighter != null) out.add(fighter);
        }
        return out;
    }

    public static int spawnLeaderEntourage(ServerPlayer player, WorldFaction faction, boolean debug) {
        if (faction == null || !(player.level() instanceof ServerLevel level)
                || faction.realm() != LivingWorldDimensions.realm(level)) return 0;
        FactionWorldData data = FactionWorldData.get(level);
        data.tickOrganizations(level);
        if (data.isExtinct(faction) || data.isLeaderKilled(faction) || data.isLeaderSpawned(faction)
                || FactionActivityRegistry.isBusy(level, faction)) return 0;
        BlockPos anchor = AmbientFighterSpawner.findEncounterAnchor(player, debug);
        if (anchor == null) return 0;
        UUID party = UUID.randomUUID();

        BlockPos leaderPos = AmbientFighterSpawner.findSafeGroundAround(level, anchor, player.getRandom(), 2, 5, 12);
        if (leaderPos == null) leaderPos = anchor;
        AmbientFighterEntity leader = AmbientFighterSpawner.spawnAt(
                level, leaderPos, faction.alignment(), FighterRank.VETERAN,
                data.currentLeaderPersonality(faction), data.currentLeaderRace(faction),
                data.currentLeaderArchetype(faction), player.getRandom());
        if (leader == null) return 0;
        leader.assignFaction(faction, FactionRole.LEADER, party, true, true);
        diversifyPartyOutfit(leader, faction, party);
        applyFactionPower(leader, faction, FactionRole.LEADER, 1.08F);

        List<AmbientFighterEntity> entourage = new ArrayList<>();
        entourage.add(leader);
        FactionRole[] roles = {FactionRole.LIEUTENANT, FactionRole.LIEUTENANT, FactionRole.ENFORCER, FactionRole.ENFORCER, FactionRole.MEMBER};
        for (FactionRole role : roles) {
            AmbientFighterEntity member = spawnMember(player, faction, anchor, party, false, role, null, true);
            if (member == null) { cleanup(entourage); return 0; }
            entourage.add(member);
        }
        data.markLeaderSpawned(faction, leader.blockPosition());
        FactionActivityRegistry.acquire(level, faction, debug ? 1000L : 1800L);
        leader.speak(AntagonistManager.isAntagonistFaction(level, faction)
                ? faction.name() + ". No loose ends." : faction.name() + ". Keep moving.", 72);
        reactToPlayer(player, faction, entourage);
        return entourage.size();
    }

    public static AmbientFighterEntity spawnMember(ServerPlayer player, WorldFaction faction, BlockPos anchor,
                                                    UUID party, boolean captain, FighterRank forcedRank) {
        FactionRole role = FactionManager.rollMemberRole(faction, player.getRandom(), captain);
        return spawnMember(player, faction, anchor, party, captain, role, forcedRank, false);
    }

    public static AmbientFighterEntity spawnMember(ServerPlayer player, WorldFaction faction, BlockPos anchor,
                                                    UUID party, boolean captain, FactionRole role,
                                                    FighterRank forcedRank, boolean regional) {
        if (!(player.level() instanceof ServerLevel level) || faction == null
                || faction.realm() != LivingWorldDimensions.realm(level) || FactionWorldData.get(level).isExtinct(faction)) return null;
        int minSpread = regional ? 6 : 4;
        int maxSpread = regional ? 20 : 15;
        BlockPos pos = AmbientFighterSpawner.findSafeGroundAround(level, anchor, player.getRandom(), minSpread, maxSpread, 24);
        if (pos == null) pos = anchor;
        RandomSource random = player.getRandom();
        FighterRank rank = forcedRank == null ? FactionManager.rankForRole(faction, role, random) : forcedRank;
        var race = FactionWorldData.rollFactionRace(random, faction.realm());
        var personality = faction.ethos().rollPersonality(random, faction.alignment());
        FighterArchetype style = faction.ethos().rollArchetype(random);
        AmbientFighterEntity fighter = AmbientFighterSpawner.spawnAt(
                level, pos, faction.alignment(), rank, personality, race, style, random);
        if (fighter == null) return null;
        fighter.assignFaction(faction, role, party, captain, regional);
        if (regional) FactionWorldData.get(level).recordResident(faction, fighter);
        diversifyPartyOutfit(fighter, faction, party);
        applyFactionPower(fighter, faction, role, 1.0F);
        return fighter;
    }

    private static void diversifyPartyOutfit(AmbientFighterEntity fighter, WorldFaction faction, UUID party) {
        if (fighter == null || party == null) return;
        List<AmbientFighterEntity> peers = fighter.level().getEntitiesOfClass(AmbientFighterEntity.class,
                fighter.getBoundingBox().inflate(48.0D), other -> other != fighter && other.isAlive()
                        && other.getPartyId() != null && party.equals(other.getPartyId()));
        if (peers.isEmpty()) return;
        for (int attempt = 1; attempt <= 10; attempt++) {
            int outfit = fighter.getOutfit();
            boolean duplicate = peers.stream().anyMatch(other -> other.getRace() == fighter.getRace() && other.getOutfit() == outfit);
            if (!duplicate) return;
            fighter.varyFactionUniform(faction, attempt);
        }
    }

    private static void applyFactionPower(AmbientFighterEntity fighter, WorldFaction faction,
                                          FactionRole role, float extra) {
        if (!(fighter.level() instanceof ServerLevel level)) return;
        double multiplier = FactionManager.effectiveFactionPower(level, faction) * role.powerMultiplier() * extra;
        long scaled = Math.round(Math.max(1, fighter.getBattlePower()) * multiplier);
        fighter.setBattlePowerAndRefresh((int)Math.min(Integer.MAX_VALUE - 1L, Math.max(1L, scaled)));
    }

    private static void reactToPlayer(ServerPlayer player, WorldFaction faction, List<AmbientFighterEntity> members) {
        if (members.isEmpty()) return;
        PlayerWorldManager.discoverFaction(player, faction);
        int rep = FactionManager.getReputation(player, faction);
        if (rep <= FactionManager.HOSTILE_REP) {
            for (AmbientFighterEntity member : members) {
                PeacekeeperManager.markNpcAggressor(player, member);
                member.setTarget(player);
            }
            members.get(0).speak(AntagonistManager.isAntagonistFaction(player.serverLevel(), faction)
                    ? "There you are. Don't let them leave." : "There. That's the one.", 70);
        } else if (rep >= FactionManager.FRIENDLY_REP) {
            members.get(0).speak("Good to see you out here.", 58);
        }
    }

    private static String homeLine(WorldFaction faction) {
        return switch (faction.structure()) {
            case GANG, SYNDICATE -> "You're in our stretch now.";
            case CULT -> "The strong always find their way here.";
            case SCHOOL -> "This is where we train.";
            case ORDER, CLAN -> "Our people are nearby.";
            case GUARD -> "We keep watch around here.";
            case CREW -> "Our crew runs this route.";
        };
    }

    private static String patrolLine(WorldFaction faction) {
        return switch (faction.ethos()) {
            case MARTIAL_SCHOOL -> "Stay sharp. Training never stops.";
            case WANDERING_GUARD, NAMEK_WARDENS -> "Keep an eye on the road.";
            case KI_ORDER, ASCETIC_ORDER, ROOT_CIRCLE -> "I can feel several powers nearby.";
            case CHALLENGERS -> "Maybe we'll find someone strong today.";
            case MERCENARIES -> "Keep moving. We have work to do.";
            case STREET_GANG, CRIME_FAMILY, SYNDICATE -> "This stretch is ours.";
            case RAIDERS -> "Look alive. Easy targets travel these roads.";
            case SEEKERS -> "There's a strong signature somewhere nearby.";
            case POWER_CULT -> "Power reveals who deserves to stand.";
        };
    }

    /**
     * Reload-resilient duplicate guard. Activity locks are intentionally ephemeral, but
     * transient patrol/training/war entities can survive a save/reload while their lock
     * table is rebuilt. Refuse to stack another scene on top of an already-loaded one.
     * Regional residents are excluded: they are the faction's local population, not a scene.
     */
    private static boolean hasTransientFactionSceneNearby(ServerPlayer player, WorldFaction faction, double radius) {
        if (player == null || faction == null || !(player.level() instanceof ServerLevel level)) return false;
        return !level.getEntitiesOfClass(AmbientFighterEntity.class, player.getBoundingBox().inflate(radius),
                fighter -> fighter.isAlive() && fighter.isFactionMember()
                        && faction.id().equals(fighter.getFactionId())
                        && !fighter.isRegionalPresence()
                        && !fighter.isFactionLeader()).isEmpty();
    }

    public static int desiredRegionalCellSize(FactionWorldData data, WorldFaction faction) {
        int pop = Math.max(0, data.population(faction));
        int base = pop < 12 ? 5 : pop < 22 ? 7 : pop < 38 ? 9 : pop < 56 ? 11 : 13;
        if (faction.structure() == FactionStructure.SYNDICATE || faction.structure() == FactionStructure.GUARD) base++;
        if (faction.structure() == FactionStructure.CULT && pop < 30) base = Math.max(4, base - 1);
        return Math.max(4, Math.min(LivingWorldConfig.factionResidentCap(), base));
    }

    private static int cleanup(AmbientFighterEntity... fighters) {
        for (AmbientFighterEntity fighter : fighters) if (fighter != null && fighter.isAlive()) fighter.discard();
        return 0;
    }
    private static int cleanup(List<AmbientFighterEntity> fighters) {
        for (AmbientFighterEntity fighter : fighters) if (fighter != null && fighter.isAlive()) fighter.discard();
        return 0;
    }
    private static double distanceSq(double x, double z, double tx, double tz) {
        double dx = tx - x; double dz = tz - z; return dx * dx + dz * dz;
    }
}
