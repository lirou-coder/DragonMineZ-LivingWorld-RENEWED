package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Deliberate player/NPC sparring started from the fighter inspection screen.
 * Ordinary fighters use the established Living World sanctioned-spar contract; X-7 keeps its
 * newer dedicated controlled-bout path as an additive special case.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SparManager {
    private static final long MAX_SPAR_TICKS = 3_600L;
    private static final long SPAR_COOLDOWN_TICKS = 20L * 60L * 5L;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, PendingResume> PENDING_RESUMES = new HashMap<>();
    private static final String RECONNECT_FIGHTER = "LWSparReconnectFighter";
    private static final String RECONNECT_NAME = "LWSparReconnectName";
    private static final String RECONNECT_ELAPSED = "LWSparReconnectElapsed";
    private static final String RECONNECT_DIMENSION = "LWSparReconnectDimension";
    private static final String RECONNECT_X = "LWSparReconnectX";
    private static final String RECONNECT_Y = "LWSparReconnectY";
    private static final String RECONNECT_Z = "LWSparReconnectZ";
    private static long lastTick = Long.MIN_VALUE;
    private static final java.util.Set<UUID> SENZU_WARNED = new java.util.HashSet<>();
    private static final long MAX_X7_SPAR_TICKS = 20L * 60L * 5L;
    private static final Map<UUID, X7SparSession> X7_SPARS = new HashMap<>();

    private record X7SparSession(UUID fighterId, long startedAt) {}
    private record Session(UUID playerId, UUID fighterId, long startedAt) {}
    private record PendingResume(UUID fighterId, long readyAt, long elapsed, String dimension, BlockPos lastPos, String fighterName) {}
    private SparManager() {}

    public static boolean request(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !fighter.isAlive()) return false;
        if (FactionRequestMissionManager.isRequestActionLocked(fighter)) {
            message(player, fighter.getFighterName() + " is busy with an active faction request.", ChatFormatting.GRAY);
            return false;
        }
        if (WorldMenaceManager.isWorldMenace(fighter)) {
            message(player, "Herobrine does not spar.", ChatFormatting.DARK_RED);
            return false;
        }
        if (WorldIncidentManager.isActive()) {
            message(player, "The world is already hosting another organized fight.", ChatFormatting.GRAY);
            return false;
        }
        if (isPlayerInSpar(player) || fighter.isSanctionedMatchParticipant()) {
            message(player, "A spar is already in progress.", ChatFormatting.GRAY);
            return false;
        }
        if (fighter.isCaptive() || fighter.isDefeated() || fighter.isRecovering() || fighter.isNonCombatant()
                || fighter.isMeditating() || fighter.getTarget() != null) {
            message(player, fighter.getFighterName() + " isn't available to spar right now.", ChatFormatting.GRAY);
            return false;
        }
        if (fighter.getAlignment() == com.dmzlivingworld.entity.FighterAlignment.BAD
                && fighter.isRememberedFor(player) && fighter.getMemoryRelationship() <= -30) {
            message(player, fighter.getFighterName() + " isn't interested in a friendly spar.", ChatFormatting.RED);
            return false;
        }
        int relationship = fighter.isRememberedFor(player) ? fighter.getMemoryRelationship() : 0;
        String moodRefusal = ReactiveInteractionManager.sparRefusal(player, fighter, relationship);
        if (moodRefusal != null) {
            fighter.speak(moodRefusal, 86);
            message(player, fighter.getFighterName() + " doesn't want to spar right now.", ChatFormatting.GRAY);
            return false;
        }

        long now = player.getServer().overworld().getGameTime();
        String cooldownKey = "LWSparCooldown_" + player.getUUID();
        long readyAt = fighter.getLegacyData().getLong(cooldownKey);
        if (readyAt > now) {
            long seconds = Math.max(1L, (readyAt - now + 19L) / 20L);
            message(player, fighter.getFighterName() + " needs a little longer to recover (" + seconds + "s).", ChatFormatting.GRAY);
            return false;
        }
        if (fighter.getHealth() < fighter.getMaxHealth() * 0.30F) {
            message(player, fighter.getFighterName() + " is too hurt to spar safely right now.", ChatFormatting.GRAY);
            return false;
        }
        if (player.getHealth() < player.getMaxHealth() * 0.30F) {
            message(player, "You're too hurt to start a safe spar right now.", ChatFormatting.GRAY);
            return false;
        }
        SESSIONS.put(player.getUUID(), new Session(player.getUUID(), fighter.getUUID(), now));
        FighterBattleGrowthManager.clearProgressiveAdvance(fighter, FighterBattleGrowthManager.Source.SPAR);
        SENZU_WARNED.remove(player.getUUID());
        fighter.beginSanctionedMatch(player);
        rememberReconnectState(player, fighter, 0L);
        fighter.recordLegacyEvent("Accepted a spar with " + player.getGameProfile().getName());
        fighter.speak("All right. Let's spar.", 58);
        message(player, "Spar started with " + fighter.getFighterName() + ".", ChatFormatting.GREEN);
        SanctionedMatchGuard.noteSparStart(player, fighter);
        if (SanctionedMatchGuard.isTraceEnabled(player)) {
            player.displayClientMessage(Component.literal("[LW SparTrace] Auto-recording this spar and the 30s cleanup window in latest.log.")
                    .withStyle(ChatFormatting.AQUA), false);
        }
        return true;
    }

    /** Dedicated dossier action for X-7. This remains separate from ordinary social spar eligibility. */
    public static boolean requestX7(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !RedRibbonExperimentManager.isExperiment(fighter)) return false;
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            player.displayClientMessage(Component.literal("[Living World] X-7 will not spar in your current state."), false);
            return false;
        }
        if (player.distanceToSqr(fighter) > 12.0D * 12.0D) return false;
        if (!fighter.isAlive() || fighter.isDefeated() || fighter.isCaptive() || fighter.isSanctionedMatchParticipant()) {
            player.displayClientMessage(Component.literal("[Living World] X-7 is not available for a controlled bout right now."), false);
            return false;
        }
        if (isPlayerInSpar(player)) {
            player.displayClientMessage(Component.literal("[Living World] You are already in a sanctioned bout."), false);
            return false;
        }
        for (X7SparSession session : X7_SPARS.values()) {
            if (session.fighterId().equals(fighter.getUUID())) {
                player.displayClientMessage(Component.literal("[Living World] X-7 is already testing another opponent."), false);
                return false;
            }
        }

        long now = player.getServer().overworld().getGameTime();
        fighter.prepareDebugSpar(player);
        fighter.beginSanctionedMatch(player);
        fighter.setAggressive(true);
        fighter.setTarget(player);
        X7_SPARS.put(player.getUUID(), new X7SparSession(fighter.getUUID(), now));
        SanctionedMatchGuard.noteSparStart(player, fighter);
        fighter.speak("Very well. Show me what you can do.", 80);
        player.displayClientMessage(Component.literal("[Living World] X-7 SPAR • non-lethal • either side concedes at roughly 30% health."), false);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (lastTick == now) return;
        lastTick = now;

        tickPendingResumes(server, now);
        tickX7(server, now);

        for (Session session : java.util.List.copyOf(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            AmbientFighterEntity fighter = find(server, session.fighterId);
            if (player != null && fighter != null && fighter.maintainSanctionedMatch(player)) {
                SanctionedMatchGuard.noteSparTargetRepair(player, fighter);
                long elapsed = Math.max(0L, now - session.startedAt);
                if (elapsed >= 40L && elapsed % 40L == 0L) {
                    FighterBattleGrowthManager.onSparPulse(fighter, (int)Math.min(Integer.MAX_VALUE, elapsed));
                    // Keep the reconnect snapshot current enough to survive an abrupt client/server stop.
                    rememberReconnectState(player, fighter, elapsed);
                }
            }
        }
        if (now % 5L != 0L) return;

        for (Session session : java.util.List.copyOf(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            AmbientFighterEntity fighter = find(server, session.fighterId);
            if (player == null || fighter == null) {
                end(server, session, player, fighter, false, false, "Spar ended");
                continue;
            }
            SanctionedMatchGuard.noteSparTick(player, fighter);
            if (!fighter.isAlive() || fighter.getHealth() <= 0.0F) {
                // Last-resort recovery for DMZ damage paths that reached vanilla death state.
                fighter.restoreSanctionedLivingState(false);
                fighter.concedeSanctionedMatch();
                end(server, session, player, fighter, true, true, player.getGameProfile().getName() + " won the spar");
                continue;
            }
            // A spar is a controlled test, not a near-death fight. The first participant to
            // reach roughly 30% remaining health concedes; the damage guard also prevents a large
            // single hit from skipping past this floor.
            boolean fighterLow = fighter.getHealth() <= fighter.getMaxHealth() * 0.30F;
            boolean playerLow = player.getHealth() <= player.getMaxHealth() * 0.30F;
            if (fighterLow || playerLow) {
                if (fighterLow && playerLow) {
                    end(server, session, player, fighter, false, false, "Spar ended at the safety limit");
                } else if (fighterLow) {
                    fighter.concedeSanctionedMatch();
                    end(server, session, player, fighter, true, true, player.getGameProfile().getName() + " won the spar");
                } else {
                    end(server, session, player, fighter, false, true, fighter.getFighterName() + " won the spar");
                }
                continue;
            }
            if (fighter.isDefeated()) {
                end(server, session, player, fighter, true, true, player.getGameProfile().getName() + " won the spar");
                continue;
            }
            if (now - session.startedAt >= MAX_SPAR_TICKS || player.distanceToSqr(fighter) > 160.0D * 160.0D) {
                end(server, session, player, fighter, false, false, "Spar ended without a winner");
            }
        }
    }

    public static boolean isPlayerInSpar(ServerPlayer player) {
        return player != null && (SESSIONS.containsKey(player.getUUID()) || X7_SPARS.containsKey(player.getUUID()));
    }

    public static boolean isSanctionedPlayerOpponent(ServerPlayer player, Entity attacker) {
        if (player == null || attacker == null) return false;
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.fighterId.equals(attacker.getUUID())) return true;
        X7SparSession x7 = X7_SPARS.get(player.getUUID());
        return x7 != null && x7.fighterId().equals(attacker.getUUID());
    }

    public static boolean isFighterInSpar(AmbientFighterEntity fighter) {
        if (fighter == null) return false;
        UUID id = fighter.getUUID();
        return sessionForFighter(id) != null || X7_SPARS.values().stream().anyMatch(s -> s.fighterId().equals(id));
    }

    /** Finishes a bout using Forge's final, post-mitigation damage result. */
    public static void finishFromFinalDamage(ServerPlayer player, AmbientFighterEntity fighter, boolean playerWon) {
        if (player == null || fighter == null) return;
        X7SparSession x7 = X7_SPARS.get(player.getUUID());
        if (x7 != null && x7.fighterId().equals(fighter.getUUID())) {
            finishX7(player.getServer(), player.getUUID(), playerWon,
                    playerWon ? "X-7 lowers his guard. \"Enough. You proved the point.\""
                            : fighter.getFighterName() + " won the spar.", true);
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.fighterId.equals(fighter.getUUID())) return;
        if (playerWon) fighter.concedeSanctionedMatch();
        end(player.getServer(), session, player, fighter, playerWon, true,
                (playerWon ? player.getGameProfile().getName() : fighter.getFighterName()) + " won the spar");
    }

    public static void concedePlayer(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null) return;
        X7SparSession x7 = X7_SPARS.get(player.getUUID());
        if (x7 != null && (fighter == null || x7.fighterId().equals(fighter.getUUID()))) {
            finishX7(player.getServer(), player.getUUID(), false, "You concede. X-7 immediately disengages.", true);
            return;
        }
        if (fighter == null) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.fighterId.equals(fighter.getUUID())) return;
        end(player.getServer(), session, player, fighter, false, true, fighter.getFighterName() + " won the spar");
    }

    public static void concedePlayer(ServerPlayer player) {
        if (player == null) return;
        if (X7_SPARS.containsKey(player.getUUID())) {
            finishX7(player.getServer(), player.getUUID(), false, "You concede. X-7 immediately disengages.", true);
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        AmbientFighterEntity fighter = find(player.getServer(), session.fighterId);
        if (fighter == null) return;
        concedePlayer(player, fighter);
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SESSIONS.containsKey(player.getUUID())) return;
        var id = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (id == null || !"dragonminez".equals(id.getNamespace()) || !id.getPath().toLowerCase(java.util.Locale.ROOT).contains("senzu")) return;
        if (!SENZU_WARNED.add(player.getUUID())) return;
        Session session = SESSIONS.get(player.getUUID());
        AmbientFighterEntity fighter = session == null ? null : find(player.getServer(), session.fighterId);
        if (fighter != null && fighter.getSpeech().isEmpty()) {
            String line = switch (fighter.getPersonality()) {
                case PROUD -> "A Senzu? During a spar? Seriously?";
                case HEROIC -> "Hey—save the Senzu for after the spar.";
                case CALM -> "Using a Senzu rather defeats the point of a spar.";
                case CAUTIOUS -> "You're healing now? Then this isn't much of a test.";
                case AGGRESSIVE -> "Oh, come on! No beans in the middle of this!";
            };
            fighter.speak(line, 82);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (X7_SPARS.containsKey(player.getUUID())) {
            finishX7(player.getServer(), player.getUUID(), false, null, false);
            return;
        }
        SENZU_WARNED.remove(player.getUUID());
        PENDING_RESUMES.remove(player.getUUID());
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) return;
        AmbientFighterEntity fighter = find(player.getServer(), session.fighterId);
        player.clearFire();
        long now = player.getServer().overworld().getGameTime();
        long elapsed = Math.max(0L, Math.min(MAX_SPAR_TICKS - 1L, now - session.startedAt));
        if (fighter != null) {
            fighter.clearFire();
            fighter.setPersistenceRequired();
            rememberReconnectState(player, fighter, elapsed);
            // End only the live combat ownership while the player is gone. The SPAR progressive
            // advance is deliberately retained and reconciled if this same session resumes.
            fighter.endSanctionedMatch();
        } else {
            player.getPersistentData().putLong(RECONNECT_ELAPSED, elapsed);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var pd = player.getPersistentData();
        if (!pd.hasUUID(RECONNECT_FIGHTER)) return;
        UUID fighterId = pd.getUUID(RECONNECT_FIGHTER);
        long now = player.getServer().overworld().getGameTime();
        String dimension = pd.getString(RECONNECT_DIMENSION);
        BlockPos lastPos = new BlockPos(pd.getInt(RECONNECT_X), pd.getInt(RECONNECT_Y), pd.getInt(RECONNECT_Z));
        String name = pd.getString(RECONNECT_NAME);
        long elapsed = Math.max(0L, Math.min(MAX_SPAR_TICKS - 1L, pd.getLong(RECONNECT_ELAPSED)));
        PENDING_RESUMES.put(player.getUUID(), new PendingResume(fighterId, now + 100L, elapsed, dimension, lastPos, name));
        message(player, "Your spar" + (name.isBlank() ? "" : " with " + name) + " will resume in 5 seconds.", ChatFormatting.YELLOW);
    }

    private static void tickX7(MinecraftServer server, long now) {
        if (X7_SPARS.isEmpty()) return;
        for (var entry : java.util.List.copyOf(X7_SPARS.entrySet())) {
            UUID playerId = entry.getKey();
            X7SparSession session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            AmbientFighterEntity fighter = find(server, session.fighterId());
            if (player == null || fighter == null || !player.isAlive() || !fighter.isAlive()) {
                finishX7(server, playerId, false, null, false);
                continue;
            }
            if (player.level() != fighter.level() || player.distanceToSqr(fighter) > 160.0D * 160.0D
                    || now - session.startedAt() > MAX_X7_SPAR_TICKS) {
                finishX7(server, playerId, false, "The controlled bout ends as the fighters separate.", false);
                continue;
            }
            if (fighter.isDefeated()) {
                finishX7(server, playerId, true, "X-7 lowers his guard. \"Enough. You proved the point.\"", true);
                continue;
            }
            fighter.maintainSanctionedMatch(player);
            SanctionedMatchGuard.noteSparTick(player, fighter);
        }
    }

    private static void finishX7(MinecraftServer server, UUID playerId, boolean playerWon, String text, boolean decisive) {
        X7SparSession session = X7_SPARS.remove(playerId);
        if (session == null || server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        AmbientFighterEntity fighter = find(server, session.fighterId());
        if (player != null) player.clearFire();
        if (fighter != null) {
            fighter.clearFire();
            fighter.endSanctionedMatch();
            fighter.restoreSanctionedLivingState(true);
            if (player != null) SanctionedMatchGuard.beginPostSparPeace(fighter, player);
        }
        if (player != null && text != null && !text.isBlank())
            player.displayClientMessage(Component.literal("[Living World] " + text), false);
        if (player != null && decisive) DMZSkillProgressionCompat.onFighterDefeated(player);
    }

    private static void end(MinecraftServer server, Session session, ServerPlayer player, AmbientFighterEntity fighter,
                            boolean playerWon, boolean decisive, String summary) {
        if (session == null) return;
        SESSIONS.remove(session.playerId);
        PENDING_RESUMES.remove(session.playerId);
        if (player != null) clearReconnectState(player);
        // Fire is a vanilla lingering damage state, not a potion effect. A sanctioned spar
        // ending must not leave either participant burning and allow the next fire tick to
        // turn a non-lethal result into a real death. Keep cleanup narrow: do not strip DMZ
        // forms, buffs, or unrelated effects.
        if (player != null) player.clearFire();
        if (fighter != null) {
            fighter.clearFire();
            fighter.endSanctionedMatch();
            if (player != null) {
                SanctionedMatchGuard.beginPostSparPeace(fighter, player);
                fighter.getLegacyData().putLong("LWSparCooldown_" + player.getUUID(), server.overworld().getGameTime() + SPAR_COOLDOWN_TICKS);
                if (decisive) {
                    int power = (int)Math.min(Integer.MAX_VALUE - 1L, Math.round(PlayerWorldManager.playerBattlePower(player)));
                    fighter.recordLegacyBattle(player.getGameProfile().getName(), power, !playerWon, false, true);
                    FighterBattleAdaptationManager.notePlayerOutcome(fighter, player, !playerWon, false);
                }
                int sparEffort = decisive ? 620 : 320;
                fighter.applyTrainingGrowth(sparEffort, false);
                FighterBattleGrowthManager.onSpar(fighter, sparEffort, decisive);
                FighterLifeNeedsManager.onSparCompleted(fighter, sparEffort);
                String reactionLine = ReactiveInteractionManager.sparOutcome(fighter, player, playerWon, decisive);
                int relationshipGain = decisive ? 2 : 1;
                if (playerWon && ReactiveWorldManager.mood(fighter) == ReactiveWorldManager.Mood.IRRITATED) {
                    int before = fighter.isRememberedFor(player) ? fighter.getMemoryRelationship() : 0;
                    boolean soreLoser = fighter.getPersonality() == com.dmzlivingworld.entity.FighterPersonality.PROUD
                            || fighter.getPersonality() == com.dmzlivingworld.entity.FighterPersonality.AGGRESSIVE
                            || ReactiveWorldManager.temperament(fighter) == ReactiveWorldManager.Temperament.BULLY;
                    relationshipGain = soreLoser && before < 35 ? -1 : 0;
                }
                FighterMemoryManager.strengthenRelationship(player, fighter, relationshipGain, FighterRelationshipManager.BondEvent.SPAR, summary);
                FighterPromotionManager.evaluate(fighter);
                // Outcome reaction is the point of the scene: replace any stale mid-spar bubble so
                // a sore loser/winner/draw reaction is always actually visible to the player.
                fighter.speak(reactionLine, 96);
                FactionRequestManager.onSparCompleted(player, fighter, decisive);
                FighterAftermathManager.beginPlayerSpar(fighter, player, playerWon, decisive);
                if (decisive) DMZSkillProgressionCompat.onFighterDefeated(player);
            }
        }
        if (player != null) {
            SENZU_WARNED.remove(player.getUUID());
            message(player, summary + ".", ChatFormatting.GOLD);
        }
    }

    private static Session sessionForFighter(UUID fighterId) {
        if (fighterId == null) return null;
        for (Session session : SESSIONS.values()) if (fighterId.equals(session.fighterId)) return session;
        return null;
    }

    private static AmbientFighterEntity find(MinecraftServer server, UUID id) {
        if (server == null || id == null) return null;
        for (var level : server.getAllLevels()) {
            var entity = level.getEntity(id);
            if (entity instanceof AmbientFighterEntity fighter) return fighter;
        }
        return null;
    }

    private static void tickPendingResumes(MinecraftServer server, long now) {
        for (Map.Entry<UUID, PendingResume> entry : java.util.List.copyOf(PENDING_RESUMES.entrySet())) {
            if (now < entry.getValue().readyAt) continue;
            UUID playerId = entry.getKey();
            PendingResume pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) { PENDING_RESUMES.remove(playerId); continue; }
            AmbientFighterEntity fighter = find(server, pending.fighterId);
            if (fighter == null && pending.lastPos != null) {
                // One bounded recovery load of the exact last saved chunk. This is not a ticket and
                // cannot clone the fighter: UUID lookup is repeated after the chunk is loaded.
                for (var level : server.getAllLevels()) {
                    if (!pending.dimension.isBlank() && !level.dimension().location().toString().equals(pending.dimension)) continue;
                    level.getChunkAt(pending.lastPos);
                    fighter = find(server, pending.fighterId);
                    if (fighter != null) break;
                }
            }
            if (fighter == null || !fighter.isAlive() || fighter.isDefeated() || fighter.isCaptive()
                    || fighter.isRecovering() || fighter.isNonCombatant() || fighter.isSanctionedMatchParticipant()
                    || sessionForFighter(pending.fighterId) != null || SESSIONS.containsKey(playerId)) {
                if (fighter != null) FighterBattleGrowthManager.clearProgressiveAdvance(fighter, FighterBattleGrowthManager.Source.SPAR);
                clearReconnectState(player);
                PENDING_RESUMES.remove(playerId);
                message(player, "The interrupted spar could not be safely resumed.", ChatFormatting.GRAY);
                continue;
            }
            fighter.getNavigation().stop();
            fighter.setTarget(null);
            fighter.beginSanctionedMatch(player);
            long startedAt = now - pending.elapsed;
            SESSIONS.put(playerId, new Session(playerId, fighter.getUUID(), startedAt));
            PENDING_RESUMES.remove(playerId);
            clearReconnectState(player);
            SENZU_WARNED.remove(playerId);
            SanctionedMatchGuard.noteSparStart(player, fighter);
            message(player, "Spar resumed with " + fighter.getFighterName() + ".", ChatFormatting.GREEN);
        }
    }

    private static void rememberReconnectState(ServerPlayer player, AmbientFighterEntity fighter, long elapsed) {
        if (player == null || fighter == null) return;
        var pd = player.getPersistentData();
        pd.putUUID(RECONNECT_FIGHTER, fighter.getUUID());
        pd.putString(RECONNECT_NAME, fighter.getFighterName());
        pd.putLong(RECONNECT_ELAPSED, Math.max(0L, Math.min(MAX_SPAR_TICKS - 1L, elapsed)));
        pd.putString(RECONNECT_DIMENSION, fighter.level().dimension().location().toString());
        pd.putInt(RECONNECT_X, fighter.blockPosition().getX());
        pd.putInt(RECONNECT_Y, fighter.blockPosition().getY());
        pd.putInt(RECONNECT_Z, fighter.blockPosition().getZ());
    }

    private static void clearReconnectState(ServerPlayer player) {
        if (player == null) return;
        var pd = player.getPersistentData();
        pd.remove(RECONNECT_FIGHTER);
        pd.remove(RECONNECT_NAME);
        pd.remove(RECONNECT_ELAPSED);
        pd.remove(RECONNECT_DIMENSION);
        pd.remove(RECONNECT_X);
        pd.remove(RECONNECT_Y);
        pd.remove(RECONNECT_Z);
    }

    private static void message(ServerPlayer player, String text, ChatFormatting color) {
        if (player != null) player.displayClientMessage(Component.literal("[Living World] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(color)), false);
    }

    public static void clearRuntime(UUID playerId) {
        if (playerId == null) return;
        SENZU_WARNED.remove(playerId);
        SESSIONS.remove(playerId);
        PENDING_RESUMES.remove(playerId);
        X7_SPARS.remove(playerId);
        SanctionedMatchGuard.clearRuntime(playerId);
    }
    public static boolean isActive() { return !SESSIONS.isEmpty() || !X7_SPARS.isEmpty(); }
    public static void clearRuntime() {
        SESSIONS.clear(); PENDING_RESUMES.clear(); SENZU_WARNED.clear(); X7_SPARS.clear(); lastTick = Long.MIN_VALUE;
    }
    public static int runtimeEntries() { return SESSIONS.size() + X7_SPARS.size(); }
}
