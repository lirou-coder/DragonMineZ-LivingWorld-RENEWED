package com.dmzlivingworld.world;

import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterRank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.quest.Saga;
import com.dragonminez.common.quest.objectives.KillObjective;
import java.util.List;

/**
 * Procedural power starts from the world's actual era. A nearby, active player can add a
 * bounded local pressure signal to new generation and future earned growth, never a direct
 * copy of that player's BP. Existing fighters are never forcibly rescaled by an era change.
 */
public final class WorldPowerScaler {
    private static final double LOCAL_PLAYER_RADIUS = 128.0D;
    private static final double LOCAL_PLAYER_RADIUS_SQR = LOCAL_PLAYER_RADIUS * LOCAL_PLAYER_RADIUS;
    private static final double PLAYER_PRESSURE_CAP_MULTIPLIER = 28.0D;
    private static final String OBSERVED_PLAYER_PRESSURE = "LWObservedPlayerPressure";
    private static final String OBSERVED_PLAYER_PRESSURE_AT = "LWObservedPlayerPressureAt";
    private static final long OBSERVED_PLAYER_PRESSURE_MEMORY_TICKS = 12_000L;

    // Medium relevance profile. These are soft growth-rate anchors, never stat targets.
    // Ratios are fighter permanent BP / unsuppressed player progression BP.
    private static final double RELEVANCE_CRITICAL_BEHIND = 0.08D;
    private static final double RELEVANCE_DEEP_BEHIND = 0.15D;
    private static final double RELEVANCE_BEHIND = 0.35D;
    private static final double RELEVANCE_NEAR_BEHIND = 0.50D;
    private static final double RELEVANCE_NORMAL_FLOOR = 0.55D;
    private static final double RELEVANCE_NORMAL_CEILING = 1.35D;
    private static final double RELEVANCE_AHEAD = 1.60D;
    private static final double RELEVANCE_FAR_AHEAD = 2.00D;
    private static final double RELEVANCE_VERY_FAR_AHEAD = 2.50D;
    private WorldPowerScaler() {}

    public static int rollBattlePower(ServerLevel level, BlockPos pos, FighterRank rank, RandomSource random) {
        return (int)Math.min(Integer.MAX_VALUE - 1L, Math.round(BattlePowerFormula.battlePower(
                rollEffectiveStats(level, rank, random))));
    }

    /** Canonical totalStats budget. Every era is anchored to configured QUEST enemy stats. */
    public static double rollEffectiveStats(ServerLevel level, FighterRank rank, RandomSource random) {
        double reference = sagaKillReference(level) * LivingWorldConfig.npcStrengthScale();
        return Math.max(1.0D, reference * rollReferenceFactor(rank, random));
    }

    /**
     * Before any completion, use the first QUEST kill reachable from the first root saga. After
     * that, use the final configured QUEST kill in the furthest completed saga recorded by world.
     */
    private static double sagaKillReference(ServerLevel level) {
        WorldEraData data = WorldEraData.get(level);
        boolean first = data.eraNumber() == 0 || data.anchorSagaId().isBlank();
        if (first) return initialAvailableReference();
        Saga saga = QuestRegistry.getSaga(data.anchorSagaId());
        if (saga != null) {
            int questIndex = saga.getQuests().size() - 1;
            int questEnd = -1;
            int questStep = -1;
            for (; questIndex != questEnd; questIndex += questStep) {
                Quest quest = saga.getQuests().get(questIndex);
                int objectiveIndex = quest.getObjectives().size() - 1;
                int objectiveEnd = -1;
                int objectiveStep = -1;
                for (; objectiveIndex != objectiveEnd; objectiveIndex += objectiveStep) {
                    if (!(quest.getObjectives().get(objectiveIndex) instanceof KillObjective kill)
                            || kill.getSpawnMode() != KillObjective.SpawnMode.QUEST) continue;
                    double hp = Math.max(0.0D, kill.getHealth());
                    double melee = Math.max(0.0D, kill.getMeleeDamage());
                    double ki = Math.max(0.0D, kill.getKiDamage());
                    double reference = (hp * 2.0D + melee + ki) / 2.0D;
                    if (Double.isFinite(reference) && reference > 0.0D) return reference;
                }
            }
        }
        // Invalid or temporarily absent custom files must not crash generation.
        return 450.0D;
    }

    /** The first quest containing a QUEST-spawn kill in every configured non-Movies saga may seed an untouched world. */
    private static double initialAvailableReference() {
        double weakest = Double.POSITIVE_INFINITY;
        for (Saga saga : QuestRegistry.getAllSagas().values()) {
            if (WorldEraProgression.isMovies(saga) || saga.getQuests().isEmpty()) continue;
            // A custom saga may open with dialogue/travel objectives (DBClassic commonly does).
            // Stop at its first quest which actually spawns a kill target; never compare later fights.
            for (Quest quest : saga.getQuests()) {
                boolean foundKillQuest = false;
                for (var objective : quest.getObjectives()) {
                    if (!(objective instanceof KillObjective kill)
                            || kill.getSpawnMode() != KillObjective.SpawnMode.QUEST) continue;
                    foundKillQuest = true;
                    double reference = killReference(kill);
                    if (reference > 0.0D) weakest = Math.min(weakest, reference);
                }
                if (foundKillQuest) break;
            }
        }
        return Double.isFinite(weakest) ? weakest : 450.0D;
    }

    public static double lastKillReference(Saga saga) {
        if (saga == null) return -1.0D;
        for (int q = saga.getQuests().size() - 1; q >= 0; q--) {
            List<?> objectives = saga.getQuests().get(q).getObjectives();
            for (int o = objectives.size() - 1; o >= 0; o--) {
                if (objectives.get(o) instanceof KillObjective kill
                        && kill.getSpawnMode() == KillObjective.SpawnMode.QUEST) return killReference(kill);
            }
        }
        return -1.0D;
    }

    private static double killReference(KillObjective kill) {
        double hp = Math.max(0.0D, kill.getHealth());
        double melee = Math.max(0.0D, kill.getMeleeDamage());
        double ki = Math.max(0.0D, kill.getKiDamage());
        double reference = (hp * 2.0D + melee + ki) / 2.0D;
        return Double.isFinite(reference) ? reference : -1.0D;
    }

    /** World/faction maintenance retains the original era-only baseline and never reacts to a passerby. */
    public static int rollWorldBattlePower(ServerLevel level, BlockPos pos, FighterRank rank, RandomSource random) {
        return rollBattlePowerFromAnchor(resolveWorldAnchor(level, pos), rank, random, true);
    }

    private static int rollBattlePowerFromAnchor(double anchor, FighterRank rank, RandomSource random,
                                                  boolean veteranMaximumAnchor) {

        double minFactor;
        double maxFactor;
        switch (rank) {
            case ROOKIE -> {
                minFactor = 0.18D;
                maxFactor = 0.65D;
            }
            case TRAINED -> {
                minFactor = 0.55D;
                maxFactor = 1.40D;
            }
            case VETERAN -> {
                minFactor = 1.10D;
                maxFactor = 2.70D;
            }
            default -> {
                minFactor = 0.25D;
                maxFactor = 1.0D;
            }
        }

        // Triangular distribution: extreme fighters exist, but the population does
        // not constantly spawn at the edge of its era's plausible range.
        double t = (random.nextDouble() + random.nextDouble()) * 0.5D;
        double factor = Mth.lerp(t, minFactor, maxFactor);

        // Saga references explicitly describe the strongest ordinary Veteran. Preserve the old
        // population proportions by normalizing the historical 0.18..2.70 bands to that maximum.
        if (veteranMaximumAnchor) factor /= 2.70D;

        // Rare natural monsters remain possible independently of the player.
        if (rank == FighterRank.VETERAN && random.nextFloat() < 0.045F) {
            factor *= 1.40D + random.nextDouble() * 0.35D;
        }

        long result = Math.round(Math.max(90.0D, anchor * factor));
        return (int)Math.min(Integer.MAX_VALUE - 1L, result);
    }

    /**
     * World age is only a slow secondary pressure and uses monotonic game time, not the daylight clock. Saga progression is the real
     * accelerator; this can never look at player BP.
     */
    public static double resolveWorldAnchor(ServerLevel level, BlockPos ignored) {
        double days = level.getServer().overworld().getGameTime() / 24000.0D;
        double ageFactor = 1.0D + Math.min(0.35D, Math.max(0.0D, days) / 900.0D);
        double difficulty = LivingWorldConfig.npcStrengthScale();
        double effectiveReference = sagaKillReference(level) * ageFactor * difficulty;
        return Math.max(90.0D, BattlePowerFormula.battlePower(Math.max(1.0D, effectiveReference)));
    }

    private static double rollReferenceFactor(FighterRank rank, RandomSource random) {
        double min = switch (rank) {
            case ROOKIE -> 0.18D / 2.70D;
            case TRAINED -> 0.55D / 2.70D;
            case VETERAN -> 1.10D / 2.70D;
        };
        double max = switch (rank) {
            case ROOKIE -> 0.65D / 2.70D;
            case TRAINED -> 1.40D / 2.70D;
            case VETERAN -> 1.0D;
        };
        double t = (random.nextDouble() + random.nextDouble()) * 0.5D;
        double factor = Mth.lerp(t, min, max);
        if (rank == FighterRank.VETERAN && random.nextFloat() < 0.045F)
            factor *= 1.40D + random.nextDouble() * 0.35D;
        return factor;
    }

    /**
     * A local player can noticeably influence fresh ambient population without becoming a
     * rubber-band target. The player only raises a world-era anchor, the influence is blended,
     * and rank/random distribution remains exactly intact.
     */
    public static double contextualSpawnAnchor(ServerLevel level, BlockPos pos) {
        double world = resolveWorldAnchor(level, pos);
        double player = nearbyActivePlayerPressure(level, pos);
        if (player <= world) return world;
        double bounded = Math.min(player, world * 10.0D);
        return world + (bounded - world) * 0.42D;
    }

    /** Called by existing nearby-world loops so activities can respond without extra entity scans. */
    public static void observeNearbyPlayerPressure(AmbientFighterEntity fighter, ServerPlayer player) {
        if (fighter == null || player == null || !(fighter.level() instanceof ServerLevel level)
                || player.level() != level || !eligiblePressurePlayer(player)) return;
        if (fighter.distanceToSqr(player) > LOCAL_PLAYER_RADIUS_SQR) return;
        rememberObservedPressure(fighter, capPlayerPressure(level, fighter.blockPosition(), PlayerWorldManager.playerProgressionBattlePower(player)));
    }

    /** A smoothed, same-dimension local pressure for one loaded fighter's future earned growth. */
    public static double activePlayerPowerPressure(ServerLevel level, AmbientFighterEntity fighter) {
        if (level == null || fighter == null) return 0.0D;
        double raw = nearbyActivePlayerPressure(level, fighter.blockPosition());
        return raw <= 0.0D ? 0.0D : rememberObservedPressure(fighter, raw);
    }

    /** Same cap used by remembered/off-screen training tied to one real player. */
    public static double activePlayerPowerPressure(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (level == null || player == null || player.level() != level || !eligiblePressurePlayer(player)) return 0.0D;
        return capPlayerPressure(level, pos, PlayerWorldManager.playerProgressionBattlePower(player));
    }

    /**
     * Activity-selection pressure for the medium relevance profile. Positive values add extra
     * voluntary training weight while badly behind; -1 suppresses only generic ambient training
     * while far ahead. Explicit TRAIN/racial/flight goals remain authoritative.
     */
    public static int trainingPressure(AmbientFighterEntity fighter) {
        if (fighter == null || WorldMenaceManager.isHerobrine(fighter)) return 0;
        var data = fighter.getLegacyData();
        double observed = data.getDouble(OBSERVED_PLAYER_PRESSURE);
        long seenAt = data.getLong(OBSERVED_PLAYER_PRESSURE_AT);
        long now = fighter.level().getGameTime();
        // Player pressure is a short-lived impression, not permanent mind control. It only
        // biases voluntary activity selection while the player is still a recent local fact.
        if (!Double.isFinite(observed) || observed <= 0.0D || seenAt <= 0L
                || now - seenAt > OBSERVED_PLAYER_PRESSURE_MEMORY_TICKS) {
            data.remove(OBSERVED_PLAYER_PRESSURE);
            data.remove(OBSERVED_PLAYER_PRESSURE_AT);
            return 0;
        }
        double own = Math.max(1.0D, fighter.getPermanentBattlePower());
        double behindRatio = observed / own;
        if (behindRatio > 1.0D / RELEVANCE_DEEP_BEHIND) return 3;
        // Preserve R6's existing 2.4x and 1.2x observed-power behavior while adding a stronger
        // deep-gap tier. This keeps previously working training pressure fully intact.
        if (behindRatio >= 2.40D) return 2;
        if (behindRatio > 1.20D) return 1;

        double effectiveAheadRatio = (own / observed) / rivalAheadBandExtension(
                fighter.getPlayerRivalBattles(), fighter.getMemoryRelationship());
        return effectiveAheadRatio > RELEVANCE_AHEAD ? -1 : 0;
    }

    private static double nearbyActivePlayerPressure(ServerLevel level, BlockPos pos) {
        if (level == null || level.getServer() == null || pos == null) return 0.0D;
        double strongest = 0.0D;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level || !eligiblePressurePlayer(player)) continue;
            double dx = player.getX() - (pos.getX() + 0.5D);
            double dy = player.getY() - (pos.getY() + 0.5D);
            double dz = player.getZ() - (pos.getZ() + 0.5D);
            if (dx * dx + dy * dy + dz * dz > LOCAL_PLAYER_RADIUS_SQR) continue;
            strongest = Math.max(strongest, PlayerWorldManager.playerProgressionBattlePower(player));
        }
        return capPlayerPressure(level, pos, strongest);
    }

    private static boolean eligiblePressurePlayer(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    private static double capPlayerPressure(ServerLevel level, BlockPos pos, double playerPower) {
        if (!Double.isFinite(playerPower) || playerPower <= 0.0D) return 0.0D;
        return Math.max(0.0D, Math.min(playerPower,
                resolveWorldAnchor(level, pos == null ? BlockPos.ZERO : pos) * PLAYER_PRESSURE_CAP_MULTIPLIER));
    }

    private static double rememberObservedPressure(AmbientFighterEntity fighter, double raw) {
        if (fighter == null || !Double.isFinite(raw) || raw <= 0.0D) return 0.0D;
        var data = fighter.getLegacyData();
        double stored = data.getDouble(OBSERVED_PLAYER_PRESSURE);
        double previous = Double.isFinite(stored) ? Math.max(0.0D, stored) : 0.0D;
        long now = fighter.level().getGameTime();
        long previousAt = data.getLong(OBSERVED_PLAYER_PRESSURE_AT);
        // An activity scan can encounter more than one eligible player in one tick. Keep the
        // strongest local reading independent of player-list order, then smooth only between
        // distinct observation moments.
        if (previous > 0.0D && previousAt == now) {
            double observed = Math.max(previous, raw);
            data.putDouble(OBSERVED_PLAYER_PRESSURE, observed);
            return observed;
        }
        double blend = previous <= 0.0D ? 1.0D : (now - previousAt > 12_000L ? 0.55D : 0.32D);
        double observed = previous <= 0.0D ? raw : previous + (raw - previous) * blend;
        data.putDouble(OBSERVED_PLAYER_PRESSURE, Math.max(1.0D, observed));
        data.putLong(OBSERVED_PLAYER_PRESSURE_AT, now);
        return observed;
    }
    /**
     * Compatibility/global diagnostic pressure. Organic loaded growth uses the local overload
     * above; this method remains for external callers and ignores creative/spectator accounts.
     */
    public static double activePlayerPowerPressure(ServerLevel level) {
        if (level == null || level.getServer() == null) return 0.0D;
        double strongest = 0.0D;
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            if (!eligiblePressurePlayer(player)) continue;
            strongest = Math.max(strongest, PlayerWorldManager.playerProgressionBattlePower(player));
        }
        return capPlayerPressure(level, BlockPos.ZERO, strongest);
    }

    public static double earnedProgressionCeiling(ServerLevel level, BlockPos pos, FighterRank rank, com.dmzlivingworld.entity.AmbientFighterEntity fighter) {
        double player = activePlayerPowerPressure(level, fighter);
        int rivalry = fighter == null ? 0 : fighter.getPlayerRivalBattles();
        int relationship = fighter == null ? 0 : fighter.getMemoryRelationship();
        double ordinary = earnedProgressionCeiling(level, pos, rank, player, rivalry, relationship, FighterPotentialManager.potential(fighter));
        return RedRibbonExperimentManager.progressionCeiling(level, fighter, ordinary);
    }

    /** Shared loaded/off-screen ceiling calculation so remembered fighters retain rivalry allowances. */
    public static double earnedProgressionCeiling(ServerLevel level, BlockPos pos, FighterRank rank,
                                                   double playerPower, int rivalry, int relationship) {
        return earnedProgressionCeiling(level, pos, rank, playerPower, rivalry, relationship, 1.0D);
    }

    public static double earnedProgressionCeiling(ServerLevel level, BlockPos pos, FighterRank rank,
                                                   double playerPower, int rivalry, int relationship, double potential) {
        double anchor = resolveWorldAnchor(level, pos);
        double worldCap = anchor * switch (rank) {
            case ROOKIE -> 1.55D;
            case TRAINED -> 2.55D;
            case VETERAN -> 3.75D;
        };
        // R16.2: the earned ceiling must agree with the post-parity growth curve. The old
        // rank caps could hard-stop legitimate spar/meditation/training BP near parity even
        // while earnedGrowthMultiplier() still intentionally granted a positive advantage.
        // These remain earned ceilings only: no fighter is rescaled or handed player BP.
        double playerFactor = switch (rank) {
            case ROOKIE -> 2.60D;
            case TRAINED -> 3.00D;
            case VETERAN -> 3.40D;
        };
        if (rivalry > 0) playerFactor += Math.min(0.65D, 0.14D + rivalry * 0.070D);
        if (relationship <= -70 && rivalry >= 3) playerFactor += 0.20D;
        playerFactor *= FighterPotentialManager.ceilingFactor(potential);
        double earnedCap = playerPower > 0.0D ? playerPower * playerFactor : 0.0D;
        return Math.max(worldCap, earnedCap);
    }

    /**
     * Medium soft-relevance multiplier for ordinary earned BP. It never rewrites or reduces BP:
     * fighters far behind progress faster; fighters far ahead keep everything they earned but
     * coast until the player/world catches up. Rival history widens the ahead band.
     */
    public static double earnedGrowthMultiplier(ServerLevel level, com.dmzlivingworld.entity.AmbientFighterEntity fighter) {
        if (level == null || fighter == null || WorldMenaceManager.isHerobrine(fighter)) return 1.0D;
        if (RedRibbonExperimentManager.isExperiment(fighter)) return RedRibbonExperimentManager.growthMultiplier(level, fighter);
        double player = activePlayerPowerPressure(level, fighter);
        double relevance = earnedGrowthMultiplier(player, fighter.getPermanentBattlePower(),
                fighter.getPlayerRivalBattles(), fighter.getMemoryRelationship(), FighterPotentialManager.potential(fighter));
        return relevance * FighterPassiveSkillManager.growthMultiplier(fighter);
    }

    /** Shared pure calculation used by loaded and remembered/off-screen progression. */
    public static double earnedGrowthMultiplier(double playerPower, double ownPower, int rivalry, int relationship) {
        return earnedGrowthMultiplier(playerPower, ownPower, rivalry, relationship, 1.0D);
    }

    public static double earnedGrowthMultiplier(double playerPower, double ownPower, int rivalry, int relationship, double potential) {
        if (!Double.isFinite(playerPower) || playerPower <= 0.0D
                || !Double.isFinite(ownPower) || ownPower <= 0.0D) return FighterPotentialManager.baseGrowthRate(potential);

        double rawRatio = ownPower / playerPower;
        double mult;
        if (rawRatio <= RELEVANCE_CRITICAL_BEHIND) {
            // R40: critically obsolete fighters need a real chance to re-enter the world instead of
            // spending dozens of sessions permanently irrelevant. This still multiplies only EARNED
            // growth (training, fights, goals); it never copies or grants the player's stats.
            mult = 14.00D;
        } else if (rawRatio <= RELEVANCE_DEEP_BEHIND) {
            mult = lerpRatio(rawRatio, RELEVANCE_CRITICAL_BEHIND, RELEVANCE_DEEP_BEHIND, 14.00D, 10.50D);
        } else if (rawRatio < RELEVANCE_BEHIND) {
            mult = lerpRatio(rawRatio, RELEVANCE_DEEP_BEHIND, RELEVANCE_BEHIND, 10.50D, 5.50D);
        } else if (rawRatio < 0.60D) {
            mult = lerpRatio(rawRatio, RELEVANCE_BEHIND, 0.60D, 5.50D, 2.80D);
        } else if (rawRatio < 1.0D) {
            mult = lerpRatio(rawRatio, 0.60D, 1.0D, 2.80D, 1.45D);
        } else {
            // Reaching parity is no longer a hard brake. An ordinary fighter keeps a modest
            // earned-development advantage for a while after catching the player, then coasts
            // progressively as the gap becomes genuinely large. Rival history widens that band.
            double effectiveAheadRatio = rawRatio / rivalAheadBandExtension(rivalry, relationship);
            if (effectiveAheadRatio <= RELEVANCE_NORMAL_CEILING) {
                mult = lerpRatio(effectiveAheadRatio, 1.0D, RELEVANCE_NORMAL_CEILING, 1.45D, 1.20D);
            } else if (effectiveAheadRatio < RELEVANCE_AHEAD) {
                mult = lerpRatio(effectiveAheadRatio, RELEVANCE_NORMAL_CEILING, RELEVANCE_AHEAD, 1.20D, 0.85D);
            } else if (effectiveAheadRatio < RELEVANCE_FAR_AHEAD) {
                mult = lerpRatio(effectiveAheadRatio, RELEVANCE_AHEAD, RELEVANCE_FAR_AHEAD, 0.85D, 0.45D);
            } else if (effectiveAheadRatio < RELEVANCE_VERY_FAR_AHEAD) {
                mult = lerpRatio(effectiveAheadRatio, RELEVANCE_FAR_AHEAD, RELEVANCE_VERY_FAR_AHEAD, 0.45D, 0.18D);
            } else {
                mult = 0.15D;
            }
        }

        // Preserve R6's rival-behind bonus on top of the stronger medium catch-up curve.
        if (playerPower > ownPower && rivalry > 0) mult += Math.min(0.30D, rivalry * 0.05D);
        mult = ownPower < playerPower
                ? FighterPotentialManager.behindCurve(mult, potential)
                : FighterPotentialManager.aheadCurve(mult, potential);
        mult *= FighterPotentialManager.baseGrowthRate(potential);
        return Math.max(0.10D, Math.min(15.00D, mult));
    }

    /** Backward-compatible name retained for existing integrations/callers. */
    public static double earnedCatchupMultiplier(ServerLevel level, com.dmzlivingworld.entity.AmbientFighterEntity fighter) {
        return earnedGrowthMultiplier(level, fighter);
    }

    /** Off-screen voluntary-training chance, leaving explicit personal goals untouched. */
    public static float developmentTrainingChance(double playerPower, double ownPower, int rivalry, int relationship) {
        return developmentTrainingChance(playerPower, ownPower, rivalry, relationship, 1.0D);
    }

    public static float developmentTrainingChance(double playerPower, double ownPower, int rivalry, int relationship, double potential) {
        double mult = earnedGrowthMultiplier(playerPower, ownPower, rivalry, relationship, potential);
        double chance = mult >= 1.0D
                ? 0.20D + Math.min(0.62D, (mult - 1.0D) * 0.18D)
                : 0.20D * mult;
        return (float)Math.max(0.02D, Math.min(0.82D, chance));
    }

    private static double rivalAheadBandExtension(int rivalry, int relationship) {
        if (rivalry <= 0) return 1.0D;
        double extension = 1.0D + Math.min(0.35D, 0.08D + rivalry * 0.045D);
        if (relationship <= -70 && rivalry >= 3) extension += 0.12D;
        return Math.min(1.47D, extension);
    }

    private static double lerpRatio(double value, double min, double max, double from, double to) {
        if (max <= min) return to;
        double t = Math.max(0.0D, Math.min(1.0D, (value - min) / (max - min)));
        return from + (to - from) * t;
    }

}
