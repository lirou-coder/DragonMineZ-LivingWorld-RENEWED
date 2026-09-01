package com.dmzlivingworld.world;

import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterRank;
import com.dragonminez.common.stats.StatsCapability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Small, persistent combat growth earned from lived training and real NPC battles. */
public final class FighterBattleGrowthManager {
    private static final String GROWTH = "LWCombatGrowth";
    private static final String BATTLES = "LWCombatGrowthBattles";
    private static final String EARNED_BP_REMAINDER = "LWEarnedBpRemainder";
    private static final String LEGACY_MEDITATION_REMAINDER = "LWMeditationBpRemainder";
    private static final String REMAINDER_MIGRATED = "LWEarnedBpRemainderMigrated";
    private static final String DEFERRED_BP_EXACT = "LWDeferredBpExact";
    private static final String DEFERRED_BP_CAP = "LWDeferredBpCap";
    private static final String DEFERRED_BP_TICKS = "LWDeferredBpTicks";
    private static final int DEFERRED_SETTLE_TICKS = 600; // ~30 seconds
    private static final int DEFERRED_STEP_TICKS = 20;    // one visible installment per second
    private static final String FAST_DEFERRED_BP_EXACT = "LWFastDeferredBpExact";
    private static final String FAST_DEFERRED_BP_CAP = "LWFastDeferredBpCap";
    private static final String FAST_DEFERRED_BP_TICKS = "LWFastDeferredBpTicks";
    private static final int FAST_DEFERRED_SETTLE_TICKS = 100; // ~5 seconds for training/meditation
    private static final int FAST_DEFERRED_STEP_TICKS = 5;     // four visible installments per second

    /** Sources that can visibly pre-pay part of their established end-of-session BP reward. */
    public enum Source { TRAINING, JOGGING, SPAR, BATTLE }
    private record Advance(double exactBp, double startBp) {}

    private FighterBattleGrowthManager() {}

    public static void onTraining(AmbientFighterEntity fighter, int effortTicks, boolean meditation) {
        if (fighter == null || effortTicks < 100) return;
        double effort = Math.min(1.5D, Math.max(0.12D, effortTicks / 1200.0D));
        addGrowth(fighter, (meditation ? 0.0012D : 0.0020D) * effort);
    }

    /**
     * Tiny visible BP trickle while meditation is actively happening. Fractional gains are kept
     * as a remainder so low-BP fighters still progress eventually instead of rounding to zero.
     * The completed-session spike in AmbientFighterEntity remains deliberately much larger.
     */
    public static void onMeditationPulse(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || !fighter.isMeditating()) return;
        double catchup = 1.0D;
        double ceiling = fighter.getPermanentBattlePower();
        if (fighter.level() instanceof net.minecraft.server.level.ServerLevel level) {
            catchup = WorldPowerScaler.earnedGrowthMultiplier(level, fighter);
            ceiling = WorldPowerScaler.earnedProgressionCeiling(level, fighter.blockPosition(), fighter.getRank(), fighter);
        }
        applyFractionalEarnedGrowth(fighter, 0.00004D * catchup * LivingWorldConfig.npcGrowthScale(), ceiling, false);
    }

    /**
     * R9 live-training advance. This is not bonus progression: applyTrainingGrowth() consumes the
     * stored exact advance from the session's old completion budget. Roughly a quarter of a normal
     * session therefore becomes visible in small steps while the fighter is actually working.
     */
    public static void onTrainingPulse(AmbientFighterEntity fighter, int pulseTicks, boolean meditation) {
        if (fighter == null || fighter.level().isClientSide || meditation || pulseTicks <= 0) return;
        double diminishing = 1.0D / (1.0D + (fighter.getTrainingSessions() + 1) * 0.035D);
        double raw = 0.00032D * Math.max(0.25D, Math.min(1.5D, pulseTicks / 200.0D)) * diminishing;
        raw *= RedRibbonExperimentManager.trainingEfficiency(fighter);
        if (fighter.getRank() == FighterRank.ROOKIE) raw *= 1.18D;
        applyRawAdvance(fighter, Source.TRAINING, raw);
    }

    /** Small jogging advance, reconciled against onJogging() at session end. */
    public static void onJoggingPulse(AmbientFighterEntity fighter, int pulseTicks) {
        if (fighter == null || fighter.level().isClientSide || pulseTicks <= 0) return;
        double raw = 0.00007D * Math.max(0.25D, Math.min(1.5D, pulseTicks / 200.0D));
        applyRawAdvance(fighter, Source.JOGGING, raw);
    }

    /**
     * Small sanctioned/practice spar advance, reconciled against onSpar() at session end. The
     * argument is elapsed session time so the cumulative live amount is capped below even the
     * old short/draw completion budget.
     */
    public static void onSparPulse(AmbientFighterEntity fighter, int elapsedTicks) {
        if (fighter == null || fighter.level().isClientSide || elapsedTicks < 40) return;
        // Same established accrual rate as R12.2 (0.00016 per 200 ticks), presented in five
        // smaller 40-tick steps so a short real spar can visibly move BP more than once.
        int steps = Math.max(1, elapsedTicks / 40);
        double rawTarget = Math.min(0.00075D, steps * 0.000032D);
        applyRawAdvanceToTarget(fighter, Source.SPAR, rawTarget);
    }

    /** Very small conditioning reward for a completed real jogging session. */
    public static void onJogging(AmbientFighterEntity fighter, int effortTicks) {
        if (fighter == null || effortTicks < 160) return;
        double effort = Math.min(1.25D, Math.max(0.20D, effortTicks / 1200.0D));
        // Jogging should be visible progression, but remain the lightest real BP activity.
        // Approximate progression order is Battle > Spar > Training > Meditation > Jogging.
        addGrowth(fighter, 0.00115D * effort);
        growBattlePowerWithAdvance(fighter, 0.00105D * effort, Source.JOGGING);
    }

    /** Extra progression reserved for sanctioned/practice spars so they sit above solo training but below real battles. */
    public static void onSpar(AmbientFighterEntity fighter, int effortTicks, boolean decisive) {
        if (fighter == null || effortTicks < 100) return;
        double effort = Math.max(0.30D, Math.min(1.25D, effortTicks / 900.0D));
        double fraction = (decisive ? 0.0045D : 0.0030D) * effort;
        addGrowth(fighter, fraction * 0.70D);
        growBattlePowerWithAdvance(fighter, fraction, Source.SPAR);
    }

    public static void onConcession(AmbientFighterEntity winner, AmbientFighterEntity loser) {
        if (winner == null || loser == null || winner == loser) return;
        double wp = Math.max(1.0D, winner.getBattlePower());
        double lp = Math.max(1.0D, loser.getBattlePower());
        double challengeWinner = Math.max(0.55D, Math.min(1.75D, lp / wp));
        double challengeLoser = Math.max(0.45D, Math.min(1.35D, wp / lp));
        addGrowth(winner, 0.0045D * challengeWinner);
        addGrowth(loser, 0.0022D * challengeLoser);
        winner.getLegacyData().putInt(BATTLES, winner.getLegacyData().getInt(BATTLES) + 1);
        loser.getLegacyData().putInt(BATTLES, loser.getLegacyData().getInt(BATTLES) + 1);
        // A concluded real battle is the strongest ordinary progression source.
        growBattlePowerWithAdvance(winner, 0.0105D * challengeWinner, Source.BATTLE);
        growBattlePowerWithAdvance(loser, 0.0042D * challengeLoser, Source.BATTLE);
        resetCombatTracker(winner);
        resetCombatTracker(loser);
    }

    /**
     * Gives surviving fighters a small amount of lived growth after genuine combat.
     * A fight must last long enough to matter, and growth is challenge-weighted rather than
     * rubber-banded to the player. Spars use their own training path and are excluded here.
     */
    public static void tickCombat(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide) return;
        tickDeferredGrowth(fighter);
        var data = fighter.getPersistentData();
        if (fighter.isSanctionedMatchParticipant() || fighter.isDefeated() || fighter.isCaptive()) {
            resetCombatTracker(fighter);
            return;
        }
        LivingEntity target = fighter.getTarget();
        boolean fighting = target != null && target.isAlive() && fighter.canAttack(target);
        if (fighting) {
            boolean wasActive = data.getBoolean("LWGrowthCombatActive");
            data.putBoolean("LWGrowthCombatActive", true);
            int ticks = Math.min(7200, data.getInt("LWGrowthCombatTicks") + 1);
            data.putInt("LWGrowthCombatTicks", ticks);
            data.putInt("LWGrowthCombatIdle", 0);
            double opponent = opponentPower(target);
            if (opponent > 0.0D) data.putDouble("LWGrowthOpponentPower", Math.max(data.getDouble("LWGrowthOpponentPower"), opponent));
            if (!wasActive) clearProgressiveAdvance(fighter, Source.BATTLE);
            if (ticks >= 40 && ticks % 40 == 0) onBattlePulse(fighter, data.getDouble("LWGrowthOpponentPower"), ticks);
            return;
        }
        if (!data.getBoolean("LWGrowthCombatActive")) return;
        int idle = data.getInt("LWGrowthCombatIdle") + 1;
        data.putInt("LWGrowthCombatIdle", idle);
        if (idle < 80) return;

        int ticks = data.getInt("LWGrowthCombatTicks");
        double opponent = data.getDouble("LWGrowthOpponentPower");
        if (ticks < 100) { resetCombatTracker(fighter); clearProgressiveAdvance(fighter, Source.BATTLE); return; }
        double own = Math.max(1.0D, fighter.getBattlePower());
        double challenge = opponent > 0.0D ? Math.max(0.45D, Math.min(1.8D, opponent / own)) : 0.8D;
        double duration = Math.max(0.25D, Math.min(1.5D, ticks / 1200.0D));
        addGrowth(fighter, 0.0018D * duration * challenge);
        growBattlePowerWithAdvance(fighter, 0.0062D * duration * challenge, Source.BATTLE);
        fighter.getLegacyData().putInt(BATTLES, fighter.getLegacyData().getInt(BATTLES) + 1);
        resetCombatTracker(fighter);
    }

    /**
     * Pays only a conservative fraction of the smallest plausible eventual battle reward. This
     * makes BP visibly rise during a fight without letting a fighter earn more merely because the
     * fight later ends as a loss/concession instead of the normal combat-idle resolution path.
     */
    private static void onBattlePulse(AmbientFighterEntity fighter, double opponentPower, int combatTicks) {
        if (fighter == null || fighter.level().isClientSide || combatTicks < 40) return;
        double own = Math.max(1.0D, fighter.getBattlePower());
        double opponent = Math.max(1.0D, opponentPower);
        double challengeNormal = Math.max(0.45D, Math.min(1.8D, opponent / own));
        double loserChallenge = Math.max(0.45D, Math.min(1.35D, opponent / own));
        // The cap is still based on the smallest plausible eventual R12.2 reward. Only the
        // presentation is subdivided: the safe pre-payment fills gradually over ~30 seconds.
        double minimumNormalBudget = 0.0062D * 0.25D * challengeNormal;
        double concessionLoserBudget = 0.0042D * loserChallenge;
        double safeCap = Math.min(minimumNormalBudget, concessionLoserBudget) * 0.28D;
        double progress = Math.max(0.0D, Math.min(1.0D, combatTicks / 600.0D));
        applyRawAdvanceToTarget(fighter, Source.BATTLE, safeCap * progress);
    }

    private static String advanceExactKey(Source source) { return "LWProgressAdvanceExact_" + source.name(); }
    private static String advanceStartKey(Source source) { return "LWProgressAdvanceStart_" + source.name(); }

    private static void ensureAdvanceStart(AmbientFighterEntity fighter, Source source) {
        var data = fighter.getPersistentData();
        String start = advanceStartKey(source);
        if (!data.contains(start)) data.putDouble(start, Math.max(1.0D, fighter.getPermanentBattlePower()));
    }

    private static double adjustedFraction(AmbientFighterEntity fighter, double rawFraction) {
        double adjusted = Math.max(0.0D, rawFraction) * LivingWorldConfig.npcGrowthScale();
        if (fighter.level() instanceof net.minecraft.server.level.ServerLevel level)
            adjusted *= WorldPowerScaler.earnedGrowthMultiplier(level, fighter);
        return adjusted;
    }

    private static double earnedCeiling(AmbientFighterEntity fighter) {
        if (fighter.level() instanceof net.minecraft.server.level.ServerLevel level)
            return WorldPowerScaler.earnedProgressionCeiling(level, fighter.blockPosition(), fighter.getRank(), fighter);
        return fighter.getPermanentBattlePower();
    }

    private static void applyRawAdvance(AmbientFighterEntity fighter, Source source, double rawFraction) {
        if (fighter == null || fighter.level().isClientSide || rawFraction <= 0.0D) return;
        ensureAdvanceStart(fighter, source);
        double adjusted = adjustedFraction(fighter, rawFraction);
        applyAdjustedAdvanceExact(fighter, source, adjusted * Math.max(1.0D, fighter.getPermanentBattlePower()));
    }

    /** Cumulative target form used by combat so the live amount can never outrun its safe budget. */
    private static void applyRawAdvanceToTarget(AmbientFighterEntity fighter, Source source, double rawTargetFraction) {
        if (fighter == null || fighter.level().isClientSide || rawTargetFraction <= 0.0D) return;
        ensureAdvanceStart(fighter, source);
        var data = fighter.getPersistentData();
        double startBp = Math.max(1.0D, data.getDouble(advanceStartKey(source)));
        double targetExact = adjustedFraction(fighter, rawTargetFraction) * startBp;
        double already = Math.max(0.0D, data.getDouble(advanceExactKey(source)));
        applyAdjustedAdvanceExact(fighter, source, Math.max(0.0D, targetExact - already));
    }

    private static void applyAdjustedAdvanceExact(AmbientFighterEntity fighter, Source source, double requestedExact) {
        if (fighter == null || requestedExact <= 0.0D) return;
        int base = fighter.getPermanentBattlePower();
        double ceiling = Math.max(base, earnedCeiling(fighter));
        double room = Math.max(0.0D, ceiling - base);
        double exact = Math.min(requestedExact, room);
        if (exact <= 0.0D) return;
        applyFractionalEarnedGrowth(fighter, exact / Math.max(1.0D, base), ceiling, false);
        var data = fighter.getPersistentData();
        data.putDouble(advanceExactKey(source), Math.max(0.0D, data.getDouble(advanceExactKey(source))) + exact);
    }

    /** Used by AmbientFighterEntity's established training completion formula. */
    public static double remainingAdjustedFraction(AmbientFighterEntity fighter, Source source, double adjustedTotalFraction) {
        if (fighter == null || adjustedTotalFraction <= 0.0D) { clearProgressiveAdvance(fighter, source); return 0.0D; }
        Advance advance = consumeAdvance(fighter, source);
        double current = Math.max(1.0D, fighter.getPermanentBattlePower());
        double start = advance.startBp() > 0.0D ? advance.startBp() : current;
        double intendedExact = Math.max(0.0D, adjustedTotalFraction) * start;
        return Math.max(0.0D, intendedExact - advance.exactBp()) / current;
    }

    private static void growBattlePowerWithAdvance(AmbientFighterEntity fighter, double rawFraction, Source source) {
        if (fighter == null) return;
        double adjusted = adjustedFraction(fighter, rawFraction);
        double remaining = remainingAdjustedFraction(fighter, source, adjusted);
        // R13 smooths substantial training/spar/battle completions. Jogging keeps its established
        // immediate tiny finish because turning the lightest reward into a 30-second tail would
        // make it feel less responsive without solving the lumping problem this pass targets.
        if (source == Source.JOGGING)
            applyFractionalEarnedGrowth(fighter, remaining, earnedCeiling(fighter), true);
        else queueAdjustedFraction(fighter, remaining, earnedCeiling(fighter));
    }

    /**
     * Queues an already-adjusted, already-reconciled completion remainder. This changes only when
     * legitimate earned BP is presented; it does not increase the established progression budget.
     */
    public static void queueAdjustedFraction(AmbientFighterEntity fighter, double adjustedFraction, double ceiling) {
        if (fighter == null || fighter.level().isClientSide || adjustedFraction <= 0.0D) return;
        queueDeferred(fighter, adjustedFraction, ceiling,
                DEFERRED_BP_EXACT, DEFERRED_BP_CAP, DEFERRED_BP_TICKS, DEFERRED_SETTLE_TICKS);
    }

    /** Training/meditation completion queue: same earned budget, but settles over about five seconds. */
    public static void queueFastAdjustedFraction(AmbientFighterEntity fighter, double adjustedFraction, double ceiling) {
        if (fighter == null || fighter.level().isClientSide || adjustedFraction <= 0.0D) return;
        queueDeferred(fighter, adjustedFraction, ceiling,
                FAST_DEFERRED_BP_EXACT, FAST_DEFERRED_BP_CAP, FAST_DEFERRED_BP_TICKS, FAST_DEFERRED_SETTLE_TICKS);
    }

    private static void queueDeferred(AmbientFighterEntity fighter, double adjustedFraction, double ceiling,
                                      String exactKey, String capKey, String ticksKey, int settleTicks) {
        var data = fighter.getLegacyData();
        double current = Math.max(1.0D, fighter.getPermanentBattlePower());
        double queued = Math.max(0.0D, data.getDouble(exactKey));
        double totalQueued = Math.max(0.0D, data.getDouble(DEFERRED_BP_EXACT))
                + Math.max(0.0D, data.getDouble(FAST_DEFERRED_BP_EXACT));
        double cap = Math.max(current, ceiling);
        // Fast training/meditation settlement and the established slow combat settlement can
        // coexist. They must share the same earned ceiling rather than each reserving the full
        // remaining room independently, otherwise overlapping queues could overpay legitimate BP.
        double room = Math.max(0.0D, cap - current - totalQueued);
        double exact = Math.min(room, adjustedFraction * current);
        if (exact <= 0.0D) return;
        data.putDouble(exactKey, queued + exact);
        double committedCap = current + totalQueued + exact;
        data.putDouble(capKey, Math.max(data.getDouble(capKey), committedCap));
        // If the other cadence was queued first, raise its committed cap to the same shared total.
        // Otherwise paying the new queue first could push permanent BP above the old queue's local
        // cap and make legitimate older installments disappear while being subtracted.
        if (data.getDouble(DEFERRED_BP_EXACT) > 0.000001D)
            data.putDouble(DEFERRED_BP_CAP, Math.max(data.getDouble(DEFERRED_BP_CAP), committedCap));
        if (data.getDouble(FAST_DEFERRED_BP_EXACT) > 0.000001D)
            data.putDouble(FAST_DEFERRED_BP_CAP, Math.max(data.getDouble(FAST_DEFERRED_BP_CAP), committedCap));
        data.putInt(ticksKey, Math.max(data.getInt(ticksKey), settleTicks));
    }

    /**
     * Pays the first fast training/meditation installment immediately after a completed session.
     * This keeps the established five-second smoothing budget, but removes the dead-looking gap
     * between the finish event and the next UUID-aligned deferred tick.
     */
    public static void settleFastDeferredGrowthNow(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide) return;
        var data = fighter.getLegacyData();
        tickDeferredQueueNow(fighter, data, FAST_DEFERRED_BP_EXACT, FAST_DEFERRED_BP_CAP,
                FAST_DEFERRED_BP_TICKS, FAST_DEFERRED_STEP_TICKS);
    }

    private static void tickDeferredQueueNow(AmbientFighterEntity fighter, net.minecraft.nbt.CompoundTag data,
                                             String exactKey, String capKey, String ticksKey, int stepTicks) {
        double remaining = Math.max(0.0D, data.getDouble(exactKey));
        if (remaining <= 0.000001D) { clearDeferred(data, exactKey, capKey, ticksKey); return; }
        int ticks = Math.max(1, data.getInt(ticksKey));
        int steps = Math.max(1, (ticks + stepTicks - 1) / stepTicks);
        double installment = Math.min(remaining, remaining / steps);
        double cap = Math.max(fighter.getPermanentBattlePower(), data.getDouble(capKey));
        applyFractionalEarnedGrowth(fighter, installment / Math.max(1.0D, fighter.getPermanentBattlePower()), cap, true);
        remaining = Math.max(0.0D, remaining - installment);
        if (remaining <= 0.000001D) clearDeferred(data, exactKey, capKey, ticksKey);
        else {
            data.putDouble(exactKey, remaining);
            data.putInt(ticksKey, Math.max(1, ticks - stepTicks));
        }
    }

    /** Called from the fighter's existing growth tick. Queue state is entity-persistent across saves. */
    public static void tickDeferredGrowth(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide) return;
        var data = fighter.getLegacyData();
        tickDeferredQueue(fighter, data, FAST_DEFERRED_BP_EXACT, FAST_DEFERRED_BP_CAP,
                FAST_DEFERRED_BP_TICKS, FAST_DEFERRED_STEP_TICKS);
        tickDeferredQueue(fighter, data, DEFERRED_BP_EXACT, DEFERRED_BP_CAP,
                DEFERRED_BP_TICKS, DEFERRED_STEP_TICKS);
    }

    private static void tickDeferredQueue(AmbientFighterEntity fighter, net.minecraft.nbt.CompoundTag data,
                                          String exactKey, String capKey, String ticksKey, int stepTicks) {
        double remaining = Math.max(0.0D, data.getDouble(exactKey));
        if (remaining <= 0.000001D) { clearDeferred(data, exactKey, capKey, ticksKey); return; }
        int ticks = Math.max(1, data.getInt(ticksKey));
        int nextTicks = Math.max(0, ticks - 1);
        if (fighter.tickCount % stepTicks != Math.floorMod(fighter.getUUID().hashCode(), stepTicks)) {
            data.putInt(ticksKey, Math.max(1, nextTicks));
            return;
        }
        int steps = Math.max(1, (ticks + stepTicks - 1) / stepTicks);
        double installment = Math.min(remaining, remaining / steps);
        double cap = Math.max(fighter.getPermanentBattlePower(), data.getDouble(capKey));
        applyFractionalEarnedGrowth(fighter, installment / Math.max(1.0D, fighter.getPermanentBattlePower()), cap, true);
        remaining = Math.max(0.0D, remaining - installment);
        if (remaining <= 0.000001D) clearDeferred(data, exactKey, capKey, ticksKey);
        else {
            data.putDouble(exactKey, remaining);
            data.putInt(ticksKey, Math.max(1, nextTicks));
        }
    }

    public static double deferredBattlePower(AmbientFighterEntity fighter) {
        if (fighter == null) return 0.0D;
        var data = fighter.getLegacyData();
        return Math.max(0.0D, data.getDouble(DEFERRED_BP_EXACT))
                + Math.max(0.0D, data.getDouble(FAST_DEFERRED_BP_EXACT));
    }

    private static void clearDeferred(net.minecraft.nbt.CompoundTag data, String exactKey, String capKey, String ticksKey) {
        data.remove(exactKey);
        data.remove(capKey);
        data.remove(ticksKey);
    }

    private static Advance consumeAdvance(AmbientFighterEntity fighter, Source source) {
        if (fighter == null) return new Advance(0.0D, 0.0D);
        var data = fighter.getPersistentData();
        double exact = Math.max(0.0D, data.getDouble(advanceExactKey(source)));
        double start = Math.max(0.0D, data.getDouble(advanceStartKey(source)));
        data.remove(advanceExactKey(source));
        data.remove(advanceStartKey(source));
        return new Advance(exact, start);
    }

    public static void clearProgressiveAdvance(AmbientFighterEntity fighter, Source source) {
        if (fighter == null || source == null) return;
        fighter.getPersistentData().remove(advanceExactKey(source));
        fighter.getPersistentData().remove(advanceStartKey(source));
    }

    private static double opponentPower(LivingEntity target) {
        if (target instanceof AmbientFighterEntity fighter) return Math.max(1.0D, fighter.getBattlePower());
        if (target instanceof ServerPlayer player) {
            final double[] out = {0.0D};
            player.getCapability(StatsCapability.INSTANCE).ifPresent(stats -> out[0] = Math.max(1.0D, stats.getBattlePowerExact()));
            return out[0];
        }
        return Math.max(1.0D, target.getMaxHealth() * 20.0D);
    }

    private static void resetCombatTracker(AmbientFighterEntity fighter) {
        if (fighter == null) return;
        var data = fighter.getPersistentData();
        data.remove("LWGrowthCombatActive");
        data.remove("LWGrowthCombatTicks");
        data.remove("LWGrowthCombatIdle");
        data.remove("LWGrowthOpponentPower");
    }

    private static void addGrowth(AmbientFighterEntity fighter, double amount) {
        double current = fighter.getLegacyData().getDouble(GROWTH);
        double scaled = Math.max(0.0D, amount) * LivingWorldConfig.npcGrowthScale();
        // Conditioning is part of actual combat capability too, so it follows the same soft
        // relevance pressure as earned BP. The existing 35% lifetime cap remains unchanged.
        if (fighter.level() instanceof net.minecraft.server.level.ServerLevel level)
            scaled *= WorldPowerScaler.earnedGrowthMultiplier(level, fighter);
        fighter.getLegacyData().putDouble(GROWTH, Math.min(0.35D, current + scaled));
    }

    /**
     * Apply an earned percentage against canonical base BP. Fractional gains are persisted so
     * valid low-power jogging/training/spar gains cannot be lost to integer rounding.
     */
    public static int applyFractionalEarnedGrowth(AmbientFighterEntity fighter, double fraction,
                                                   double ceiling, boolean refreshOnNoWholeGain) {
        if (fighter == null || fighter.level().isClientSide) return fighter == null ? 0 : fighter.getPermanentBattlePower();
        int base = fighter.getPermanentBattlePower();
        long cap = Math.round(Math.max(base, ceiling));
        var data = fighter.getLegacyData();
        if (base >= cap) {
            // A cap must not turn deferred fractions into a later burst when the ceiling moves.
            data.putDouble(EARNED_BP_REMAINDER, 0.0D);
            data.remove(LEGACY_MEDITATION_REMAINDER);
            data.putBoolean(REMAINDER_MIGRATED, true);
            return base;
        }
        double remainder = earnedRemainder(fighter);
        double exactDelta = Math.max(0.0D, fraction) * Math.max(1, base) + remainder;
        long whole = (long)Math.floor(exactDelta);
        double nextRemainder = Math.max(0.0D, exactDelta - whole);
        if (whole <= 0L) {
            data.putDouble(EARNED_BP_REMAINDER, nextRemainder);
            if (refreshOnNoWholeGain) fighter.setEarnedBattlePowerAndRefresh(base);
            return base;
        }
        long grown = Math.min(cap, (long)base + whole);
        if (grown < (long)base + whole) nextRemainder = 0.0D;
        data.putDouble(EARNED_BP_REMAINDER, nextRemainder);
        fighter.setEarnedBattlePowerAndRefresh((int)Math.min(Integer.MAX_VALUE - 1L, Math.max(1L, grown)));
        return fighter.getPermanentBattlePower();
    }

    private static double earnedRemainder(AmbientFighterEntity fighter) {
        var data = fighter.getLegacyData();
        double remainder = Math.max(0.0D, data.getDouble(EARNED_BP_REMAINDER));
        if (!data.getBoolean(REMAINDER_MIGRATED)) {
            remainder += Math.max(0.0D, data.getDouble(LEGACY_MEDITATION_REMAINDER));
            data.remove(LEGACY_MEDITATION_REMAINDER);
            data.putBoolean(REMAINDER_MIGRATED, true);
        }
        return Math.min(0.999999D, remainder);
    }

    private static void growBattlePower(AmbientFighterEntity fighter, double fraction) {
        double adjusted = Math.max(0.0D, fraction) * LivingWorldConfig.npcGrowthScale();
        double ceiling = fighter.getPermanentBattlePower();
        if (fighter.level() instanceof net.minecraft.server.level.ServerLevel level) {
            adjusted *= WorldPowerScaler.earnedGrowthMultiplier(level, fighter);
            ceiling = WorldPowerScaler.earnedProgressionCeiling(level, fighter.blockPosition(), fighter.getRank(), fighter);
        }
        applyFractionalEarnedGrowth(fighter, adjusted, ceiling, true);
    }

    public static double combatMultiplier(AmbientFighterEntity fighter) {
        if (fighter == null) return 1.0D;
        double lived = Math.max(0.0D, Math.min(0.35D, fighter.getLegacyData().getDouble(GROWTH)));
        // Titles describe a history that should be faintly visible in actual capability too.
        double title = fighter.getLegacyTitle().isBlank() ? 0.0D : 0.055D;
        return 1.0D + lived + title;
    }
}
