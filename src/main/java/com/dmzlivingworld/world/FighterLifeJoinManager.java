package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterRank;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Go Along" does not generate a quest. It lets the player accompany something already meaningful
 * to this fighter right now: a rival visit, useful equipment, active trouble, or a real NPC
 * friend. If the fighter has nothing meaningful happening in the loaded world, there is simply no
 * outing available.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterLifeJoinManager {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final long MAX_TICKS = 20L * 120L;

    private enum Kind { RIVAL, EQUIPMENT, THREAT, FRIEND, FACTION }
    private record Opportunity(Kind kind, UUID targetId, String label) {}
    private static final class Session {
        final UUID playerId;
        final UUID fighterId;
        final UUID targetId;
        final Kind kind;
        final long started;
        boolean arrived;
        boolean engaged;
        long phaseStarted;
        Session(UUID playerId, UUID fighterId, UUID targetId, Kind kind, long started) {
            this.playerId = playerId;
            this.fighterId = fighterId;
            this.targetId = targetId;
            this.kind = kind;
            this.started = started;
        }
    }

    private FighterLifeJoinManager() {}

    public static boolean request(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !fighter.isAlive()) return false;
        if (activeSession(player) != null) {
            message(player, "You're already accompanying someone.", ChatFormatting.GRAY);
            return false;
        }
        if (!available(player, fighter)) {
            message(player, fighter.getFighterName() + " can't head out with you right now.", ChatFormatting.GRAY);
            return false;
        }
        Opportunity opportunity = findOpportunity(fighter);
        if (opportunity == null) {
            fighter.speak(nothingToDo(fighter), 74);
            message(player, "Nothing in " + fighter.getFighterName() + "'s life needs your help right now.", ChatFormatting.GRAY);
            return false;
        }

        Entity target = player.serverLevel().getEntity(opportunity.targetId());
        if (target == null || !target.isAlive()) return false;
        fighter.setSocialLifeActivity(true);
        moveTowardOpportunity(fighter, target, true);
        fighter.speak(opening(fighter, player, opportunity, target), 120);
        SESSIONS.put(player.getUUID(), new Session(player.getUUID(), fighter.getUUID(), target.getUUID(),
                opportunity.kind(), player.serverLevel().getGameTime()));
        message(player, fighter.getFighterName() + " agreed to go with you. " + opportunity.label(), ChatFormatting.GREEN);
        message(player, "Stay close until the situation is finished.", ChatFormatting.GRAY);
        return true;
    }

    /**
     * Static runtime state can outlive a world transition inside the same client/server process.
     * Only a session whose player and fighter are still physically participating may block a new
     * Go Along request; every orphaned/expired entry is released silently here.
     */
    private static Session activeSession(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return null;
        AmbientFighterEntity oldFighter = findFighter(player.getServer(), session.fighterId);
        long now = player.getServer().overworld().getGameTime();
        boolean valid = oldFighter != null && oldFighter.isAlive()
                && oldFighter.level() == player.level()
                && player.distanceToSqr(oldFighter) <= 32.0D * 32.0D
                && now - session.started <= MAX_TICKS;
        if (valid) return session;
        SESSIONS.remove(player.getUUID());
        releaseQuietTarget(session, oldFighter);
        if (oldFighter != null) {
            oldFighter.setSocialLifeActivity(false);
            if (oldFighter.getTarget() == null) oldFighter.getNavigation().stop();
        }
        return null;
    }

    public static String activeLabel(AmbientFighterEntity fighter) {
        if (fighter == null) return "";
        for (Session s : SESSIONS.values()) {
            if (s.fighterId.equals(fighter.getUUID())) {
                return switch (s.kind) {
                    case RIVAL -> "Facing a rival";
                    case EQUIPMENT -> "Recovering equipment";
                    case THREAT -> "Responding to trouble";
                    case FRIEND -> "Visiting a friend";
                    case FACTION -> "Checking in with their faction";
                };
            }
        }
        return "";
    }

    /** Same live fact without interaction gating; used when an NPC is already walking over to speak. */
    public static String currentLifeHint(AmbientFighterEntity fighter) {
        Opportunity o = findOpportunity(fighter);
        return o == null ? "" : o.label();
    }

    /** Short live hint for the profile. It describes only opportunities that physically exist now. */
    public static String opportunityLabel(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !available(player, fighter)) return "";
        Opportunity o = findOpportunity(fighter);
        return o == null ? "Nothing they want company for right now" : o.label();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        for (Session session : List.copyOf(SESSIONS.values())) tick(server, session, now);
    }

    private static void tick(MinecraftServer server, Session session, long now) {
        ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
        AmbientFighterEntity fighter = findFighter(server, session.fighterId);
        Entity target = fighter == null ? null : ((ServerLevel) fighter.level()).getEntity(session.targetId);

        if (player == null || fighter == null || !fighter.isAlive()) {
            cancel(session, player, fighter, "That outing can no longer continue.");
            return;
        }
        if (now - session.started > MAX_TICKS || player.level() != fighter.level()
                || player.distanceToSqr(fighter) > 32.0D * 32.0D) {
            cancel(session, player, fighter, "You got separated, so they carried on without you.");
            return;
        }

        // Once a rival/threat confrontation has begun, the result itself is the activity. The
        // player may watch, help with a genuine threat, or simply stay with their friend through it.
        if (session.engaged && (session.kind == Kind.RIVAL || session.kind == Kind.THREAT)) {
            AmbientFighterEntity other = target instanceof AmbientFighterEntity f ? f : null;
            boolean resolved = other == null || !other.isAlive() || other.isDefeated() || fighter.isDefeated();
            if (resolved) finishMeaningful(session, player, fighter,
                    session.kind == Kind.RIVAL ? "Went with them to face a rival" : "Stood with them through nearby trouble");
            return;
        }

        if (target == null || !target.isAlive() || target.level() != fighter.level()) {
            cancel(session, player, fighter, "The situation changed before you got there.");
            return;
        }
        if (fighter.isDefeated() || fighter.isCaptive() || fighter.isMeditating() || fighter.hurtTime > 0) {
            cancel(session, player, fighter, "Something interrupted the outing.");
            return;
        }

        if (!session.arrived) {
            fighter.setTarget(null);
            fighter.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double arrive = session.kind == Kind.EQUIPMENT ? 3.0D : 5.0D;
            if (fighter.distanceToSqr(target) > arrive * arrive) {
                moveTowardOpportunity(fighter, target, fighter.getNavigation().isDone() || now % 20L == 0L);
                return;
            }
            if (player.distanceToSqr(fighter) > 9.0D * 9.0D) return;
            session.arrived = true;
            session.phaseStarted = now;
            beginAtDestination(session, player, fighter, target);
            return;
        }

        // Quiet visits are allowed to breathe for a few seconds instead of resolving the instant
        // the destination enters a radius.
        if ((session.kind == Kind.FRIEND || session.kind == Kind.FACTION) && now - session.phaseStarted >= 110L) {
            AmbientFighterEntity friend = target instanceof AmbientFighterEntity f ? f : null;
            if (friend == null) { cancel(session, player, fighter, "The visit was interrupted."); return; }
            if (now - session.phaseStarted >= 50L && friend.getSpeech().isEmpty()) friend.speak(
                    session.kind == Kind.FACTION ? "Good. I wanted to catch up on what's happening here." : "It's good to catch up properly.", 62);
            if (session.kind == Kind.FACTION) fighter.getLegacyData().putLong("NextFactionJoin", fighter.level().getGameTime() + 16000L);
            else fighter.getLegacyData().putLong("NextFriendJoin", fighter.level().getGameTime() + 12000L);
            finishMeaningful(session, player, fighter, session.kind == Kind.FACTION
                    ? "Went with them to check in with their faction" : "Went with them to visit a friend");
        }
    }

    private static void beginAtDestination(Session session, ServerPlayer player, AmbientFighterEntity fighter, Entity target) {
        fighter.getNavigation().stop();
        switch (session.kind) {
            case RIVAL -> {
                AmbientFighterEntity rival = target instanceof AmbientFighterEntity f ? f : null;
                if (rival == null || rival.isDefeated() || rival.isCaptive() || rival.getTarget() != null) {
                    cancel(session, player, fighter, "The rivalry could not be settled right now."); return;
                }
                fighter.speak("There you are. This is between us.", 72);
                rival.speak("Took you long enough.", 72);
                fighter.setSocialLifeActivity(false);
                fighter.startDuel(rival);
                rival.startDuel(fighter);
                session.engaged = true;
            }
            case EQUIPMENT -> {
                boolean picked = FighterArsenalManager.tryPickupNearby(fighter);
                if (!picked) { cancel(session, player, fighter, "Someone moved the equipment before you reached it."); return; }
                fighter.speak("Good eye. I can actually use this.", 72);
                finishMeaningful(session, player, fighter, "Helped them recover useful equipment");
            }
            case THREAT -> {
                AmbientFighterEntity threat = target instanceof AmbientFighterEntity f ? f : null;
                if (threat == null || !threat.isAlive() || threat.isDefeated()) {
                    cancel(session, player, fighter, "The trouble was already over when you arrived."); return;
                }
                fighter.speak("That's the problem. I'm stepping in.", 82);
                fighter.setSocialLifeActivity(false);
                fighter.setTarget(threat);
                session.engaged = true;
            }
            case FRIEND, FACTION -> {
                AmbientFighterEntity friend = target instanceof AmbientFighterEntity f ? f : null;
                if (friend == null) { cancel(session, player, fighter, "The visit was interrupted."); return; }
                friend.setSocialLifeActivity(true);
                friend.getNavigation().stop();
                fighter.getLookControl().setLookAt(friend, 35.0F, 35.0F);
                friend.getLookControl().setLookAt(fighter, 35.0F, 35.0F);
                if (session.kind == Kind.FACTION) {
                    fighter.speak("I wanted to check in before I head off again.", 78);
                    friend.speak("Good timing. There are a couple things worth knowing.", 82);
                } else {
                    fighter.speak("There you are. I wanted to check in.", 78);
                    friend.speak("Good to see you. And you brought company.", 82);
                }
            }
        }
    }

    private static void finishMeaningful(Session session, ServerPlayer player, AmbientFighterEntity fighter, String outcome) {
        SESSIONS.remove(session.playerId);
        releaseQuietTarget(session, fighter);
        if (fighter != null) {
            fighter.setSocialLifeActivity(false);
            if (fighter.getTarget() == null) fighter.getNavigation().stop();
        }
        if (player == null || fighter == null) return;
        FighterMemoryManager.strengthenRelationship(player, fighter, 2,
                FighterRelationshipManager.BondEvent.TRAVEL, outcome);
        fighter.recordLegacyEvent(outcome + " with " + player.getGameProfile().getName());
        FighterMemoryManager.refreshLoadedProfile(fighter);
        message(player, "You stayed with " + fighter.getFighterName() + " and saw it through.", ChatFormatting.GOLD);
    }

    private static Opportunity findOpportunity(AmbientFighterEntity fighter) {
        if (!(fighter.level() instanceof ServerLevel level)) return null;
        String goal = FighterGoalManager.currentType(fighter);
        if ("DEFEAT_RIVAL".equals(goal) && !fighter.getRivalName().isBlank()) {
            AmbientFighterEntity rival = findNamed(level, fighter, fighter.getRivalName(), 80.0D);
            if (rival != null && rival.getTarget() == null && fighter.distanceToSqr(rival) > 7.0D * 7.0D)
                return new Opportunity(Kind.RIVAL, rival.getUUID(), "Go face rival " + rival.getFighterName() + ".");
        }
        if ("ACQUIRE_EQUIPMENT".equals(goal)) {
            ItemEntity item = FighterArsenalManager.findUsefulDroppedItem(fighter, 64.0D);
            if (item != null && fighter.distanceToSqr(item) > 3.0D * 3.0D)
                return new Opportunity(Kind.EQUIPMENT, item.getUUID(), "Recover a useful piece of equipment.");
        }

        if (fighter.getAlignment() != FighterAlignment.BAD) {
            AmbientFighterEntity threat = level.getEntitiesOfClass(AmbientFighterEntity.class,
                            fighter.getBoundingBox().inflate(64.0D), other -> other != fighter && other.isAlive()
                                    && !other.isDefeated() && other.getAlignment() == FighterAlignment.BAD
                                    && other.getTarget() != null && other.getTarget().isAlive())
                    .stream().min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
            if (threat != null && fighter.distanceToSqr(threat) > 8.0D * 8.0D)
                return new Opportunity(Kind.THREAT, threat.getUUID(), "Respond to trouble involving " + threat.getFighterName() + ".");
        }

        AmbientFighterEntity friend = fighter.level().getGameTime() >= fighter.getLegacyData().getLong("NextFriendJoin")
                ? FighterNpcSocialManager.closestMeaningfulBond(fighter, 72.0D) : null;
        if (friend != null && fighter.distanceToSqr(friend) > 10.0D * 10.0D)
            return new Opportunity(Kind.FRIEND, friend.getUUID(), "Go check in with " + friend.getFighterName() + ".");

        if (fighter.isFactionMember() && fighter.level().getGameTime() >= fighter.getLegacyData().getLong("NextFactionJoin")) {
            AmbientFighterEntity member = level.getEntitiesOfClass(AmbientFighterEntity.class, fighter.getBoundingBox().inflate(72.0D),
                            other -> other != fighter && other.isAlive() && !other.isDefeated() && !other.isCaptive()
                                    && other.isFactionMember() && fighter.getFactionId().equals(other.getFactionId())
                                    && other.getTarget() == null && fighter.distanceToSqr(other) > 10.0D * 10.0D)
                    .stream().max(Comparator.comparingInt(other -> other.getFactionRole().id())).orElse(null);
            if (member != null) return new Opportunity(Kind.FACTION, member.getUUID(), "Check in with " + member.getFighterName() + " from " + fighter.getFactionDisplayName() + ".");
        }
        return null;
    }

    /** Forces a real Go Along opportunity for testing. Normal gameplay never uses this fallback. */
    public static int forceDebug(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level) || SESSIONS.containsKey(player.getUUID())) return 0;
        AmbientFighterEntity fighter = level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(24.0D), f -> f.isAlive() && !f.isCaptive() && !f.isDefeated()
                        && !f.isSocialLifeActivity() && f.getTarget() == null)
                .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (fighter == null) fighter = AmbientFighterSpawner.spawnNearPlayer(player, FighterAlignment.NEUTRAL, FighterRank.TRAINED, true);
        if (fighter == null) return 0;
        Opportunity opportunity = findOpportunity(fighter);
        if (opportunity == null) {
            AmbientFighterEntity friend = AmbientFighterSpawner.spawnNearPlayer(player, FighterAlignment.GOOD, FighterRank.TRAINED, true);
            if (friend == null || friend == fighter) return 0;
            opportunity = new Opportunity(Kind.FRIEND, friend.getUUID(), "Go check in with " + friend.getFighterName() + ".");
        }
        Entity target = level.getEntity(opportunity.targetId());
        if (target == null || !target.isAlive()) return 0;
        fighter.setSocialLifeActivity(true);
        moveTowardOpportunity(fighter, target, true);
        fighter.speak(opening(fighter, player, opportunity, target), 120);
        SESSIONS.put(player.getUUID(), new Session(player.getUUID(), fighter.getUUID(), target.getUUID(),
                opportunity.kind(), level.getServer().overworld().getGameTime()));
        message(player, "Go Along test started: " + opportunity.label(), ChatFormatting.AQUA);
        return 1;
    }

    private static boolean available(ServerPlayer player, AmbientFighterEntity fighter) {
        if (fighter == null || player == null || fighter.level() != player.level() || player.distanceToSqr(fighter) > 12.0D * 12.0D)
            return false;
        if (fighter.isCaptive() || fighter.isDefeated() || fighter.isRecovering() || fighter.isMeditating()
                || fighter.isTransforming() || fighter.isKaiokenActive() || fighter.getTarget() != null
                || fighter.isSocialPlayerApproach() || fighter.isSocialLifeActivity() || fighter.isSocialPowerDisplay()
                || fighter.isSanctionedMatchParticipant()) return false;
        return FighterRelationshipManager.disposition(player, fighter) != FighterRelationshipManager.Disposition.HOSTILE;
    }

    private static AmbientFighterEntity findNamed(ServerLevel level, AmbientFighterEntity origin, String name, double radius) {
        if (name == null || name.isBlank()) return null;
        return level.getEntitiesOfClass(AmbientFighterEntity.class, origin.getBoundingBox().inflate(radius),
                        other -> other != origin && other.isAlive() && !other.isDefeated() && !other.isCaptive()
                                && name.equals(other.getFighterName()))
                .stream().min(Comparator.comparingDouble(origin::distanceToSqr)).orElse(null);
    }

    private static AmbientFighterEntity findFighter(MinecraftServer server, UUID id) {
        if (server == null || id == null) return null;
        for (ServerLevel level : server.getAllLevels()) if (level.getEntity(id) instanceof AmbientFighterEntity fighter) return fighter;
        return null;
    }

    private static String opening(AmbientFighterEntity fighter, ServerPlayer player, Opportunity opportunity, Entity target) {
        String objective = switch (opportunity.kind()) {
            case RIVAL -> "I found " + ((AmbientFighterEntity) target).getFighterName() + ". I'm settling this now.";
            case EQUIPMENT -> "There's something nearby I can actually use. I'm going for it.";
            case THREAT -> "There's trouble nearby. I'm not ignoring it.";
            case FRIEND -> "I haven't checked in with " + ((AmbientFighterEntity) target).getFighterName() + " in a while. Come on.";
            case FACTION -> "I'm checking in with " + ((AmbientFighterEntity) target).getFighterName() + " before I move on. Come if you want.";
        };
        int relationship = fighter.isRememberedFor(player) ? fighter.getMemoryRelationship() : 0;
        String address = relationship >= 35 ? "I trust you, so come with me. "
                : relationship >= 15 ? "You can come if you keep up. " : "Stay out of my way. ";
        String mood = switch (ReactiveWorldManager.mood(fighter)) {
            case UPBEAT -> "This should be a good change of pace. ";
            case IRRITATED -> "I don't want to waste time. ";
            case SOMBER -> "I need to do this, even if I'm not feeling talkative. ";
            case WARY -> "Keep your eyes open. ";
            case WEARY -> "I'm tired, but this still needs doing. ";
            case FOCUSED -> "I've made up my mind. ";
            case CONTENT -> "Now is as good a time as any. ";
        };
        String personality = switch (fighter.getPersonality()) {
            case HEROIC -> "Let's handle it properly. ";
            case CALM -> "No need to rush blindly. ";
            case CAUTIOUS -> "We'll take the safe route. ";
            case PROUD -> "Don't slow me down. ";
            case AGGRESSIVE -> "If it turns into a fight, even better. ";
        };
        return address + mood + personality + objective;
    }

    private static void moveTowardOpportunity(AmbientFighterEntity fighter, Entity target, boolean refreshGroundPath) {
        if (fighter == null || target == null) return;
        boolean targetFlying = target instanceof DBSagasEntity sagaTarget && sagaTarget.isFlying();
        if (targetFlying && fighter.hasFlightUnlocked() && !fighter.isNonCombatant()) {
            fighter.getNavigation().stop();
            fighter.setFlying(true);
            fighter.setNoGravity(true);
            double distanceSq = fighter.distanceToSqr(target);
            fighter.setFlyingFast(distanceSq > 16.0D * 16.0D);
            fighter.steerAmbientFlightToward(target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D),
                    distanceSq > 16.0D * 16.0D ? 0.58D : 0.42D);
            fighter.getLookControl().setLookAt(target, 35.0F, 30.0F);
            return;
        }
        if (fighter.isFlying() || fighter.isNoGravity()) {
            fighter.setFlying(false);
            fighter.setFlyingFast(false);
            fighter.setNoGravity(false);
        }
        if (refreshGroundPath) fighter.getNavigation().moveTo(target, 1.08D);
    }

    private static String nothingToDo(AmbientFighterEntity fighter) {
        return switch (fighter.getPersonality()) {
            case PROUD -> "I've got nothing that needs company right now.";
            case AGGRESSIVE -> "Nothing worth dragging you into right now.";
            case CALM -> "Nothing pressing. Maybe another time.";
            case CAUTIOUS -> "Not right now. There's nothing I need help with.";
            case HEROIC -> "Nothing urgent on my end. Enjoy the quiet while it lasts.";
        };
    }

    private static void releaseQuietTarget(Session session, AmbientFighterEntity fighter) {
        if (session == null || fighter == null || (session.kind != Kind.FRIEND && session.kind != Kind.FACTION)) return;
        if (!(fighter.level() instanceof ServerLevel level)) return;
        Entity target = level.getEntity(session.targetId);
        if (target instanceof AmbientFighterEntity other) other.setSocialLifeActivity(false);
    }

    private static void cancel(Session session, ServerPlayer player, AmbientFighterEntity fighter, String reason) {
        SESSIONS.remove(session.playerId);
        releaseQuietTarget(session, fighter);
        if (fighter != null) {
            fighter.setSocialLifeActivity(false);
            fighter.getNavigation().stop();
        }
        if (player != null) message(player, reason, ChatFormatting.GRAY);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) return;
        AmbientFighterEntity fighter = findFighter(player.getServer(), session.fighterId);
        if (fighter != null) {
            releaseQuietTarget(session, fighter);
            fighter.setSocialLifeActivity(false);
        }
    }

    private static void message(ServerPlayer player, String text, ChatFormatting color) {
        if (player != null) player.displayClientMessage(Component.literal("[Living World] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(color)), false);
    }

    public static int runtimeEntries() { return SESSIONS.size(); }
    public static void clearRuntime() { SESSIONS.clear(); }
}
