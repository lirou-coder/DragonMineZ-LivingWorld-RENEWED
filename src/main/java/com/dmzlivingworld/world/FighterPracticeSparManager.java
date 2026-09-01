package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Real, non-lethal NPC/NPC practice using the fighters' existing DMZ combat AI.
 * This is progression, not pantomime: both participants feed the normal LW training-growth path.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterPracticeSparManager {
    private static final Map<UUID, Session> BY_FIGHTER = new HashMap<>();

    private record Session(UUID a, UUID b, ResourceKey<Level> dimension, long started, long expires) {}

    private FighterPracticeSparManager() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 5L == 0L) tickSessions(server, now);
        if (now % 500L == 0L && LivingWorldConfig.npcChaosPercent() > 0) tryNatural(server, now);
    }

    private static void tickSessions(MinecraftServer server, long now) {
        java.util.HashSet<Session> unique = new java.util.HashSet<>(BY_FIGHTER.values());
        for (Session session : unique) {
            ServerLevel level = server.getLevel(session.dimension());
            AmbientFighterEntity a = level != null && level.getEntity(session.a()) instanceof AmbientFighterEntity f ? f : null;
            AmbientFighterEntity b = level != null && level.getEntity(session.b()) instanceof AmbientFighterEntity f ? f : null;
            if (a == null || b == null || !a.isAlive() || !b.isAlive() || a.distanceToSqr(b) > 96.0D * 96.0D) {
                finish(a, b, session, null, now, false);
                continue;
            }
            long elapsed = Math.max(0L, now - session.started());
            if (elapsed >= 40L && elapsed % 40L < 5L) {
                FighterBattleGrowthManager.onSparPulse(a, (int)Math.min(Integer.MAX_VALUE, elapsed));
                FighterBattleGrowthManager.onSparPulse(b, (int)Math.min(Integer.MAX_VALUE, elapsed));
            }
            boolean aLow = a.getHealth() <= a.getMaxHealth() * 0.30F;
            boolean bLow = b.getHealth() <= b.getMaxHealth() * 0.30F;
            if (aLow || bLow) {
                if (aLow && bLow) finish(a, b, session, null, now, false);
                else finish(a, b, session, aLow ? b : a, now, true);
                continue;
            }
            if (a.isDefeated()) { finish(a, b, session, b, now, true); continue; }
            if (b.isDefeated()) { finish(a, b, session, a, now, true); continue; }
            if (now >= session.expires()) finish(a, b, session, null, now, false);
        }
    }

    private static void tryNatural(MinecraftServer server, long now) {
        double chance = Math.min(0.12D, 0.012D * (LivingWorldConfig.npcChaosPercent() / 100.0D));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) continue;
            if (player.getRandom().nextDouble() > chance) continue;
            List<AmbientFighterEntity> candidates = new ArrayList<>(level.getEntitiesOfClass(AmbientFighterEntity.class,
                    player.getBoundingBox().inflate(48.0D), FighterPracticeSparManager::eligible));
            if (candidates.size() < 2) continue;
            candidates.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
            for (int i = 0; i < candidates.size(); i++) {
                AmbientFighterEntity a = candidates.get(i);
                for (int j = i + 1; j < candidates.size(); j++) {
                    AmbientFighterEntity b = candidates.get(j);
                    if (a.distanceToSqr(b) > 22.0D * 22.0D || !sociallySafePair(a, b)) continue;
                    if (start(a, b, now)) return;
                }
            }
        }
    }

    private static boolean eligible(AmbientFighterEntity fighter) {
        return fighter != null && fighter.isAlive() && !fighter.isNonCombatant() && !fighter.isCaptive()
                && !fighter.isDefeated() && !fighter.isRecovering() && !fighter.isMeditating()
                && !fighter.isPreparingMeditation() && !fighter.isTransforming() && !fighter.isKaiokenActive()
                && !fighter.isSanctionedMatchParticipant() && fighter.getTarget() == null
                && !fighter.isSocialLifeActivity() && !LivingBondManager.isTravellingCompanion(fighter)
                && !WorldMenaceManager.isWorldMenace(fighter) && !BY_FIGHTER.containsKey(fighter.getUUID());
    }

    private static boolean sociallySafePair(AmbientFighterEntity a, AmbientFighterEntity b) {
        if (a == null || b == null || a == b) return false;
        // Actual enemies belong in normal/faction combat, not in a training session.
        if ((a.getAlignment() == com.dmzlivingworld.entity.FighterAlignment.BAD) != (b.getAlignment() == com.dmzlivingworld.entity.FighterAlignment.BAD)) return false;
        if (a.isFactionMember() && b.isFactionMember() && !a.getFactionId().equals(b.getFactionId())) return false;
        double ratio = Math.max(a.getBattlePower(), b.getBattlePower()) / (double)Math.max(1, Math.min(a.getBattlePower(), b.getBattlePower()));
        return ratio <= 2.25D;
    }

    public static boolean start(AmbientFighterEntity a, AmbientFighterEntity b, long now) {
        if (!eligible(a) || !eligible(b) || !sociallySafePair(a, b) || a.level() != b.level()) return false;
        FighterAmbientActivityManager.cancel(a);
        FighterAmbientActivityManager.cancel(b);
        FighterBattleGrowthManager.clearProgressiveAdvance(a, FighterBattleGrowthManager.Source.SPAR);
        FighterBattleGrowthManager.clearProgressiveAdvance(b, FighterBattleGrowthManager.Source.SPAR);
        Session session = new Session(a.getUUID(), b.getUUID(), a.level().dimension(), now, now + 1200L + a.getRandom().nextInt(801));
        BY_FIGHTER.put(a.getUUID(), session);
        BY_FIGHTER.put(b.getUUID(), session);
        a.beginSanctionedMatch(b);
        b.beginSanctionedMatch(a);
        a.speak(pick(a, "Want to run a few rounds?", "Let's train for real.", "Spar with me. I need the practice.", "No holding back on the technique—just stop before it gets stupid."), 78);
        b.speak(pick(b, "You're on.", "Good. I needed a real opponent.", "All right. Let's see what needs work.", "Fine by me. Keep it clean."), 78);
        ReactiveWorldManager.rememberEvent(a, "PRACTICE_START", b.getFighterName(), "started a real practice spar");
        ReactiveWorldManager.rememberEvent(b, "PRACTICE_START", a.getFighterName(), "started a real practice spar");
        return true;
    }

    private static void finish(AmbientFighterEntity a, AmbientFighterEntity b, Session session,
                               AmbientFighterEntity winner, long now, boolean decisive) {
        BY_FIGHTER.remove(session.a());
        BY_FIGHTER.remove(session.b());
        long elapsedLong = Math.max(0L, now - session.started());
        int effort = (int)Math.min(Integer.MAX_VALUE, elapsedLong);
        if (a != null) { a.clearFire(); a.endSanctionedMatch(); }
        if (b != null) { b.clearFire(); b.endSanctionedMatch(); }
        if (a == null || b == null) return;
        a.setHealth(Math.max(a.getHealth(), a.getMaxHealth() * 0.42F));
        b.setHealth(Math.max(b.getHealth(), b.getMaxHealth() * 0.42F));
        if (effort >= 180) {
            a.applyTrainingGrowth(effort, false);
            b.applyTrainingGrowth(effort, false);
            FighterBattleGrowthManager.onSpar(a, effort, decisive);
            FighterBattleGrowthManager.onSpar(b, effort, decisive);
        }
        FighterLifeNeedsManager.onSparCompleted(a, effort);
        FighterLifeNeedsManager.onSparCompleted(b, effort);
        boolean aWon = decisive && winner == a;
        boolean bWon = decisive && winner == b;
        // Only a decisive practice result belongs in win/loss battle history. A time-limit draw
        // is still a real training session, but must never manufacture two losses.
        if (decisive) {
            a.recordLegacyBattle(b.getFighterName(), b.getBattlePower(), aWon, false, false);
            b.recordLegacyBattle(a.getFighterName(), a.getBattlePower(), bWon, false, false);
            AmbientFighterEntity loser = winner == a ? b : a;
            AmbientFighterEntity victor = winner == a ? a : b;
            FighterBattleAdaptationManager.noteOutcome(loser, victor, false, false);
        } else {
            a.recordLegacyEvent("Completed a long practice spar with " + b.getFighterName());
            b.recordLegacyEvent("Completed a long practice spar with " + a.getFighterName());
        }
        if (decisive) {
            AmbientFighterEntity loser = winner == a ? b : a;
            ReactiveWorldManager.react(winner, ReactiveWorldManager.Mood.FOCUSED, "finishing a useful practice spar", 760);
            ReactiveWorldManager.react(loser, ReactiveWorldManager.Mood.FOCUSED, "learning from a practice loss", 900);
            winner.speak(pick(winner, "Good round. That gave me something to work with.", "Nice. Again another time.", "That was useful. I felt a few openings I need to remember."), 84);
            loser.speak(pick(loser, "Yeah... I see what I did wrong.", "Good. I needed to find that weakness.", "Next time I'll read that sooner."), 84);
        } else {
            ReactiveWorldManager.react(a, ReactiveWorldManager.Mood.FOCUSED, "cooling down after a long practice session", 620);
            ReactiveWorldManager.react(b, ReactiveWorldManager.Mood.FOCUSED, "cooling down after a long practice session", 620);
        }
        FighterAftermathManager.beginPractice(a, b, winner, decisive);
        FighterMemoryManager.refreshLoadedProfile(a);
        FighterMemoryManager.refreshLoadedProfile(b);
    }


    /** Planned-day entry point: find one compatible nearby practice partner for this fighter. */
    public static boolean tryPlanned(AmbientFighterEntity fighter, long now) {
        if (!eligible(fighter) || !(fighter.level() instanceof ServerLevel level)) return false;
        List<AmbientFighterEntity> nearby = new ArrayList<>(level.getEntitiesOfClass(AmbientFighterEntity.class,
                fighter.getBoundingBox().inflate(22.0D), other -> other != fighter && eligible(other) && sociallySafePair(fighter, other)));
        if (nearby.isEmpty()) return false;
        // Prefer a partner whose own routine also wants practice, then the closest compatible person.
        nearby.sort(java.util.Comparator.<AmbientFighterEntity>comparingInt(other ->
                        FighterDailyRoutineManager.wantsSparring(other) ? 0 : 1)
                .thenComparingDouble(fighter::distanceToSqr));
        return start(fighter, nearby.get(0), now);
    }

    public static int debugStart(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        List<AmbientFighterEntity> nearby = new ArrayList<>(level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(48.0D), FighterPracticeSparManager::eligible));
        nearby.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
        while (nearby.size() < 2) {
            AmbientFighterEntity spawned = AmbientFighterSpawner.spawnNearPlayer(player, com.dmzlivingworld.entity.FighterAlignment.NEUTRAL,
                    com.dmzlivingworld.entity.FighterRank.TRAINED, true);
            if (spawned == null) break;
            FighterArsenalManager.initializeNaturalLoadout(spawned);
            nearby.add(spawned);
        }
        for (int i = 0; i < nearby.size(); i++) for (int j = i + 1; j < nearby.size(); j++) {
            AmbientFighterEntity a = nearby.get(i), b = nearby.get(j);
            if (sociallySafePair(a, b) && start(a, b, level.getServer().overworld().getGameTime())) {
                player.displayClientMessage(Component.literal("[Living World] Started a real NPC practice spar: " + a.getFighterName() + " vs " + b.getFighterName() + "."), false);
                return 1;
            }
        }
        player.displayClientMessage(Component.literal("[Living World] Could not find/create a compatible practice pair."), false);
        return 0;
    }

    public static boolean isPracticing(AmbientFighterEntity fighter) {
        return fighter != null && BY_FIGHTER.containsKey(fighter.getUUID());
    }

    public static int runtimeEntries() { return new java.util.HashSet<>(BY_FIGHTER.values()).size(); }
    public static void clearRuntime() { BY_FIGHTER.clear(); }

    private static String pick(AmbientFighterEntity fighter, String... lines) {
        return lines[fighter.getRandom().nextInt(lines.length)];
    }
}
