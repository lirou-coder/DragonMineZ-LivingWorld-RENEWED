package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.compat.MeditationCompat;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterDialogue;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dmzlivingworld.entity.FighterRace;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Context-aware, paced NPC-to-NPC conversations for the Reactive World layer. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterNpcSocialManager {
    private static final String SOCIAL_ID = "NpcSocialIdentity";
    private static final String BONDS = "NpcSocialBonds";
    private static final String NAMES = "NpcSocialNames";
    private static final String NEXT_SOCIAL = "LWNextNpcSocial";
    private static final String RACE_KINSHIP_SEEN = "LWRaceKinshipSeen";
    private static final int CHECK_INTERVAL = 200;
    private static final double SOCIAL_RADIUS = 10.0D;
    private static final Map<UUID, Conversation> CONVERSATIONS = new HashMap<>();

    private enum Topic { RECOVERY, RECENT_EVENT, RACE_KINSHIP, ACTIVITY, TRAINING, HOBBY, FACTION, WEATHER, NIGHT, ENCOURAGEMENT, TENSION, WORLD, JOKE }
    private record Beat(UUID speaker, String text, Tone tone) {}
    private enum Tone { NEUTRAL, WARM, TEASING, HOSTILE }

    private static final class Conversation {
        final UUID a, b;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final long started;
        final Topic topic;
        List<Beat> beats = List.of();
        int beat;
        long nextBeatAt;
        long settledAt;
        final boolean hangout;
        final boolean walking;
        final boolean meeting;
        boolean hangoutSitDecided;
        boolean hangoutSit;
        Vec3 walkTarget;
        long walkUntil;
        long walkStartedAt;
        long regroupSince;
        long nextWalkLineAt;
        boolean midWalkLineDone;
        int walkWaypoints;
        Conversation(AmbientFighterEntity a, AmbientFighterEntity b, long now, Topic topic) {
            this(a, b, now, topic, false, false, false);
        }
        Conversation(AmbientFighterEntity a, AmbientFighterEntity b, long now, Topic topic, boolean hangout) {
            this(a, b, now, topic, hangout, false, false);
        }
        Conversation(AmbientFighterEntity a, AmbientFighterEntity b, long now, Topic topic, boolean hangout, boolean walking) {
            this(a, b, now, topic, hangout, walking, false);
        }
        Conversation(AmbientFighterEntity a, AmbientFighterEntity b, long now, Topic topic, boolean hangout, boolean walking, boolean meeting) {
            this.a = a.getUUID(); this.b = b.getUUID(); this.dimension = a.level().dimension();
            this.started = now; this.topic = topic; this.hangout = hangout; this.walking = walking; this.meeting = meeting;
        }
    }

    private FighterNpcSocialManager() {}

    public static void tick(AmbientFighterEntity fighter) {
        if (FactionRequestMissionManager.isAssigned(fighter)) { cancelFor(fighter); return; }
        if (fighter == null || fighter.level().isClientSide || !LivingWorldConfig.npcSocializing()) return;
        double chatScale = LivingWorldConfig.npcChatFrequencyScale();
        if (chatScale <= 0.0D) return;
        if (CONVERSATIONS.containsKey(fighter.getUUID())) return;
        boolean prompted = FighterIntentManager.current(fighter) == FighterIntentManager.Intent.CHECK_ALLY
                || FighterIntentManager.current(fighter) == FighterIntentManager.Intent.SOCIALIZE;
        int checkInterval = prompted ? 40 : Math.max(40, (int)Math.round(CHECK_INTERVAL / Math.max(0.10D, chatScale)));
        if (fighter.tickCount % checkInterval != Math.floorMod(fighter.getUUID().hashCode(), checkInterval)) return;
        if (!(fighter.level() instanceof ServerLevel level)
                || level.players().stream().noneMatch(player -> player.distanceToSqr(fighter) <= 56.0D * 56.0D)) return;
        if (!available(fighter)) return;
        long now = level.getGameTime();
        if (!prompted && now < fighter.getPersistentData().getLong(NEXT_SOCIAL)) return;
        float socialDrive = ReactiveWorldManager.socialDrive(fighter);
        if (!prompted && socialDrive < 1.0F && fighter.getRandom().nextFloat() > socialDrive) {
            fighter.getPersistentData().putLong(NEXT_SOCIAL, now + socialDelay(260L + fighter.getRandom().nextInt(381)));
            return;
        }

        // A short-term intention may name someone this fighter actively wants to check on.
        // Honour it when possible, then fall back to the normal nearest-compatible social scan.
        AmbientFighterEntity preferred = FighterIntentManager.preferredSocialTarget(fighter, SOCIAL_RADIUS);
        AmbientFighterEntity other = preferred != null && !CONVERSATIONS.containsKey(preferred.getUUID())
                && available(preferred) && now >= preferred.getPersistentData().getLong(NEXT_SOCIAL)
                && compatible(fighter, preferred) ? preferred : level.getEntitiesOfClass(AmbientFighterEntity.class,
                        fighter.getBoundingBox().inflate(SOCIAL_RADIUS), candidate -> candidate != fighter
                                && !CONVERSATIONS.containsKey(candidate.getUUID()) && available(candidate)
                                && now >= candidate.getPersistentData().getLong(NEXT_SOCIAL)
                                && compatible(fighter, candidate))
                .stream().min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (other == null) {
            fighter.getPersistentData().putLong(NEXT_SOCIAL, now + socialDelay(300L + fighter.getRandom().nextInt(401)));
            return;
        }
        if (!prompted && socialId(fighter).compareTo(socialId(other)) > 0) return;

        float pairDrive = Math.max(0.25F, (ReactiveWorldManager.socialDrive(fighter) + ReactiveWorldManager.socialDrive(other)) * 0.5F);
        long next = now + socialDelay(Math.max(420L, Math.round((1000L + fighter.getRandom().nextInt(1401)) / pairDrive)));
        fighter.getPersistentData().putLong(NEXT_SOCIAL, next);
        other.getPersistentData().putLong(NEXT_SOCIAL, next + other.getRandom().nextInt(121));
        Topic topic = chooseTopic(fighter, other, level);
        Conversation conversation = new Conversation(fighter, other, now, topic);
        CONVERSATIONS.put(fighter.getUUID(), conversation);
        CONVERSATIONS.put(other.getUUID(), conversation);
        fighter.setSocialLifeActivity(true);
        other.setSocialLifeActivity(true);
    }

    private static long socialDelay(long establishedTicks) {
        double scale = LivingWorldConfig.npcChatFrequencyScale();
        if (scale <= 0.0D) return 12000L;
        // 100% preserves the established cadence exactly. Higher/lower values only change
        // how often a new autonomous conversation may begin; turn pacing inside a conversation
        // is intentionally unchanged so dialogue still sounds natural.
        return Math.max(80L, Math.round(establishedTicks / scale));
    }

    /**
     * Daily-routine entry point. It reuses the established relationship/conversation machinery
     * rather than inventing a fake background social activity. Friends/meaningful bonds are
     * preferred, with a compatible nearby fighter as fallback.
     */
    public static boolean tryPlanned(AmbientFighterEntity fighter, boolean hangingOut) {
        if (FactionRequestMissionManager.isAssigned(fighter)) return false;
        if (fighter == null || fighter.level().isClientSide || !LivingWorldConfig.npcSocializing()
                || !(fighter.level() instanceof ServerLevel level) || !available(fighter)
                || CONVERSATIONS.containsKey(fighter.getUUID())) return false;
        long now = level.getGameTime();

        // Do not drag somebody out of training/fishing/etc. just because this fighter wants company.
        // Prefer people whose own current routine also has room for social time, then existing bonds.
        AmbientFighterEntity other = level.getEntitiesOfClass(AmbientFighterEntity.class,
                        fighter.getBoundingBox().inflate(14.0D), candidate -> candidate != fighter
                                && !CONVERSATIONS.containsKey(candidate.getUUID()) && available(candidate)
                                && !FighterAmbientActivityManager.isActive(candidate)
                                && compatible(fighter, candidate))
                .stream().min(Comparator
                        .<AmbientFighterEntity>comparingInt(candidate -> {
                            FighterDailyRoutineManager.Activity plan = FighterDailyRoutineManager.currentActivity(candidate);
                            return plan == FighterDailyRoutineManager.Activity.SOCIALIZING
                                    || plan == FighterDailyRoutineManager.Activity.HANGING_OUT
                                    || plan == FighterDailyRoutineManager.Activity.WALK_TOGETHER
                                    || plan == FighterDailyRoutineManager.Activity.MEETING_UP ? 0 : 1;
                        })
                        .thenComparingInt(candidate -> -bond(fighter, candidate))
                        .thenComparingDouble(fighter::distanceToSqr)).orElse(null);
        if (other == null) return false;

        Topic topic;
        if (hangingOut) {
            int bond = bond(fighter, other);
            if (bond >= 6 && fighter.getRandom().nextFloat() < 0.45F) topic = Topic.HOBBY;
            else if (fighter.getRandom().nextFloat() < 0.35F) topic = Topic.JOKE;
            else topic = chooseTopic(fighter, other, level);
        } else topic = chooseTopic(fighter, other, level);

        long next = now + socialDelay(hangingOut ? 900L : 1100L);
        fighter.getPersistentData().putLong(NEXT_SOCIAL, next);
        other.getPersistentData().putLong(NEXT_SOCIAL, next + 40L);
        Conversation conversation = new Conversation(fighter, other, now, topic, hangingOut);
        CONVERSATIONS.put(fighter.getUUID(), conversation);
        CONVERSATIONS.put(other.getUUID(), conversation);
        fighter.setSocialLifeActivity(true);
        other.setSocialLifeActivity(true);
        if (hangingOut) {
            ReactiveWorldManager.rememberEvent(fighter, "HANGOUT", other.getFighterName(), "spent some quiet time together");
            ReactiveWorldManager.rememberEvent(other, "HANGOUT", fighter.getFighterName(), "spent some quiet time together");
        }
        return true;
    }

    /**
     * Planned short walk with another fighter. This is deliberately social-life behavior rather
     * than companion following: the pair talks, walks one short local leg, then separates and
     * returns to autonomous life.
     */
    public static boolean tryPlannedWalk(AmbientFighterEntity fighter) {
        if (FactionRequestMissionManager.isAssigned(fighter)) return false;
        if (fighter == null || fighter.level().isClientSide || !LivingWorldConfig.npcSocializing()
                || !(fighter.level() instanceof ServerLevel level) || !available(fighter)
                || CONVERSATIONS.containsKey(fighter.getUUID())) return false;
        long now = level.getGameTime();
        AmbientFighterEntity other = level.getEntitiesOfClass(AmbientFighterEntity.class,
                        fighter.getBoundingBox().inflate(16.0D), candidate -> candidate != fighter
                                && !CONVERSATIONS.containsKey(candidate.getUUID()) && available(candidate)
                                && !FighterAmbientActivityManager.isActive(candidate)
                                && compatible(fighter, candidate))
                .stream().min(Comparator
                        .<AmbientFighterEntity>comparingInt(candidate -> {
                            FighterDailyRoutineManager.Activity plan = FighterDailyRoutineManager.currentActivity(candidate);
                            return plan == FighterDailyRoutineManager.Activity.WALK_TOGETHER
                                    || plan == FighterDailyRoutineManager.Activity.HANGING_OUT
                                    || plan == FighterDailyRoutineManager.Activity.SOCIALIZING
                                    || plan == FighterDailyRoutineManager.Activity.MEETING_UP ? 0 : 1;
                        })
                        .thenComparingInt(candidate -> -bond(fighter, candidate))
                        .thenComparingDouble(fighter::distanceToSqr)).orElse(null);
        if (other == null) return false;

        Topic topic = bond(fighter, other) >= 5 && fighter.getRandom().nextFloat() < 0.40F
                ? Topic.HOBBY : chooseTopic(fighter, other, level);
        long next = now + socialDelay(1050L);
        fighter.getPersistentData().putLong(NEXT_SOCIAL, next);
        other.getPersistentData().putLong(NEXT_SOCIAL, next + 50L);
        Conversation conversation = new Conversation(fighter, other, now, topic, false, true);
        CONVERSATIONS.put(fighter.getUUID(), conversation);
        CONVERSATIONS.put(other.getUUID(), conversation);
        fighter.setSocialLifeActivity(true);
        other.setSocialLifeActivity(true);
        ReactiveWorldManager.rememberEvent(fighter, "WALK_TOGETHER", other.getFighterName(), "took a short walk together");
        ReactiveWorldManager.rememberEvent(other, "WALK_TOGETHER", fighter.getFighterName(), "took a short walk together");
        return true;
    }

    /**
     * Deliberate long-range social visit. Both fighters remain real loaded entities and physically
     * travel toward one another; no teleport, clone, chunk-force or abstract completion is used.
     * Existing bonds are strongly preferred so a scheduled meet-up usually has a believable reason.
     */
    public static boolean tryPlannedMeetUp(AmbientFighterEntity fighter) {
        if (FactionRequestMissionManager.isAssigned(fighter)) return false;
        if (fighter == null || fighter.level().isClientSide || !LivingWorldConfig.npcSocializing()
                || !(fighter.level() instanceof ServerLevel level) || !available(fighter)
                || CONVERSATIONS.containsKey(fighter.getUUID())) return false;
        long now = level.getGameTime();
        AmbientFighterEntity other = level.getEntitiesOfClass(AmbientFighterEntity.class,
                        fighter.getBoundingBox().inflate(72.0D, 40.0D, 72.0D), candidate -> candidate != fighter
                                && !CONVERSATIONS.containsKey(candidate.getUUID()) && available(candidate)
                                && !FighterAmbientActivityManager.isActive(candidate)
                                && compatible(fighter, candidate))
                .stream().min(Comparator
                        .<AmbientFighterEntity>comparingInt(candidate -> {
                            int existingBond = bond(fighter, candidate);
                            FighterDailyRoutineManager.Activity plan = FighterDailyRoutineManager.currentActivity(candidate);
                            if (existingBond >= 6) return 0;
                            if (plan == FighterDailyRoutineManager.Activity.MEETING_UP) return 1;
                            if (existingBond >= 4) return 2;
                            return 3;
                        })
                        .thenComparingInt(candidate -> -bond(fighter, candidate))
                        .thenComparingDouble(fighter::distanceToSqr)).orElse(null);
        if (other == null) return false;

        Topic topic = bond(fighter, other) >= 5 && fighter.getRandom().nextFloat() < 0.40F
                ? Topic.HOBBY : chooseTopic(fighter, other, level);
        long next = now + socialDelay(1300L);
        fighter.getPersistentData().putLong(NEXT_SOCIAL, next);
        other.getPersistentData().putLong(NEXT_SOCIAL, next + 60L);
        Conversation conversation = new Conversation(fighter, other, now, topic, false, false, true);
        CONVERSATIONS.put(fighter.getUUID(), conversation);
        CONVERSATIONS.put(other.getUUID(), conversation);
        fighter.setSocialLifeActivity(true);
        other.setSocialLifeActivity(true);
        ReactiveWorldManager.rememberEvent(fighter, "MEETING_UP", other.getFighterName(), "went to meet them in person");
        ReactiveWorldManager.rememberEvent(other, "MEETING_UP", fighter.getFighterName(), "met up in person");
        return true;
    }

    /** Debug/QA entry point for a real bounded NPC meditation circle. */
    public static int forceMeditationCircle(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level) || !MeditationCompat.isNpcMeditationEnabled()) return 0;
        List<AmbientFighterEntity> nearby = new ArrayList<>(level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(18.0D), fighter -> available(fighter)
                        && !WorldMenaceManager.isWorldMenace(fighter)
                        && !LivingBondManager.isTravellingCompanion(fighter)));
        nearby.sort(Comparator.comparingDouble(player::distanceToSqr));
        if (nearby.size() < 2) return 0;
        AmbientFighterEntity a = nearby.get(0), b = nearby.get(1);
        int sessionTicks = AmbientFighterEntity.naturalMeditationDuration(a.getRandom(), 3600);
        if (!a.beginSharedMeditation(b, sessionTicks)) return 0;
        startMeditationCircle(a, b, sessionTicks);
        a.recordLegacyEvent("Joined a group meditation");
        b.recordLegacyEvent("Joined a group meditation");
        return 1;
    }

    /** Debug/QA entry point for the now-physical social activities. */
    public static int forceNearest(ServerPlayer player, String requested) {
        if (player == null || !(player.level() instanceof ServerLevel level) || !LivingWorldConfig.npcSocializing()) return 0;
        String mode = requested == null ? "social" : requested.trim().toLowerCase(Locale.ROOT);
        FighterAmbientActivityManager.clearDebugSubjectsForQA(player);
        List<AmbientFighterEntity> candidates = new ArrayList<>(level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(72.0D, 40.0D, 72.0D), FighterNpcSocialManager::debugSocialUsable));
        candidates.sort(Comparator.comparingDouble(player::distanceToSqr));
        for (AmbientFighterEntity fighter : candidates) {
            AmbientFighterEntity partner = candidates.stream().filter(other -> other != fighter && debugSocialUsable(other)
                            && compatible(fighter, other) && socialId(fighter).compareTo(socialId(other)) != 0)
                    .min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
            if (partner == null) continue;
            prepareDebugSocial(fighter); prepareDebugSocial(partner);
            if (!available(fighter) || !available(partner)) continue;
            long now = level.getGameTime();
            Topic topic = chooseTopic(fighter, partner, level);
            boolean hangout = mode.equals("hangout") || mode.equals("hangingout") || mode.equals("hanging_out");
            boolean walking = mode.equals("walktogether") || mode.equals("walk_together") || mode.equals("socialwalk");
            boolean meeting = mode.equals("meeting") || mode.equals("meet") || mode.equals("meetup") || mode.equals("meetingup") || mode.equals("meeting_up");
            Conversation conversation = new Conversation(fighter, partner, now, topic, hangout, walking, meeting);
            CONVERSATIONS.put(fighter.getUUID(), conversation); CONVERSATIONS.put(partner.getUUID(), conversation);
            fighter.setSocialLifeActivity(true); partner.setSocialLifeActivity(true);
            FighterAmbientActivityManager.recordDebugSubjectForQA(player, fighter);
            FighterAmbientActivityManager.recordDebugSubjectForQA(player, partner);
            return 1;
        }
        return 0;
    }

    private static boolean debugSocialUsable(AmbientFighterEntity fighter) {
        return fighter != null && !WorldMenaceManager.isWorldMenace(fighter) && fighter.isAlive() && !fighter.isCaptive()
                && !fighter.isDefeated() && !fighter.isRecovering() && !fighter.isTransforming() && !fighter.isKaiokenActive()
                && fighter.getTarget() == null && !fighter.isSanctionedMatchParticipant() && !LivingBondManager.isTravellingCompanion(fighter);
    }

    private static void prepareDebugSocial(AmbientFighterEntity fighter) {
        if (fighter == null) return;
        FighterAmbientActivityManager.cancel(fighter);
        cancelForDebug(fighter);
        if (fighter.isMeditating() || fighter.isPreparingMeditation()) fighter.stopMeditation(false);
        fighter.getNavigation().stop(); fighter.setSocialLifeActivity(false); fighter.setAmbientPose(0);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CONVERSATIONS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 5L != 0L) return;
        for (Conversation c : new HashSet<>(CONVERSATIONS.values())) tickConversation(server, c, now);
    }

    private static void tickConversation(MinecraftServer server, Conversation c, long now) {
        ServerLevel level = server.getLevel(c.dimension);
        AmbientFighterEntity a = level != null && level.getEntity(c.a) instanceof AmbientFighterEntity f ? f : null;
        AmbientFighterEntity b = level != null && level.getEntity(c.b) instanceof AmbientFighterEntity f ? f : null;
        // A live social scene owns standing presentation. If any stale/shared meditation state
        // reappears while the conversation is active, clear it here before availability/animation
        // checks so Meeting/Talk/Hangout can never flash a meditation pose between beats.
        if (a != null && (a.isMeditating() || a.isPreparingMeditation())) a.stopMeditation(false);
        if (b != null && (b.isMeditating() || b.isPreparingMeditation())) b.stopMeditation(false);
        if (a != null) a.setPose(net.minecraft.world.entity.Pose.STANDING);
        if (b != null) b.setPose(net.minecraft.world.entity.Pose.STANDING);
        if (a == null || b == null || !availableDuring(a) || !availableDuring(b)
                || a.getUUID().equals(b.getUUID()) || socialId(a).equals(socialId(b))) { finish(a, b, c); return; }

        a.getLookControl().setLookAt(b, 35.0F, 35.0F);
        b.getLookControl().setLookAt(a, 35.0F, 35.0F);
        double d = a.distanceToSqr(b);
        if (c.settledAt == 0L) {
            if (d > 3.35D * 3.35D) {
                long approachLimit = c.meeting ? 800L : 200L;
                if (now - c.started > approachLimit) { finish(a, b, c); return; }
                if (c.meeting) {
                    moveForMeeting(a, b, d, now);
                    moveForMeeting(b, a, d, now);
                } else {
                    if (a.getNavigation().isDone() || now % 20L == 0L) a.getNavigation().moveTo(b, 0.86D * ReactiveWorldManager.movementPace(a));
                    if (b.getNavigation().isDone() || now % 20L == 0L) b.getNavigation().moveTo(a, 0.86D * ReactiveWorldManager.movementPace(b));
                }
                return;
            }
            stopMeetingTravel(a); stopMeetingTravel(b);
            a.getNavigation().stop(); b.getNavigation().stop();
            a.setLocomotionMode(com.dragonminez.common.init.entities.sagas.DBSagasEntity.LocomotionMode.IDLE);
            b.setLocomotionMode(com.dragonminez.common.init.entities.sagas.DBSagasEntity.LocomotionMode.IDLE);
            a.setPose(net.minecraft.world.entity.Pose.STANDING);
            b.setPose(net.minecraft.world.entity.Pose.STANDING);
            c.settledAt = now;
            // R11: a conversation is visually real from the moment the pair settles. Both begin
            // attentive, then speaker/listener gestures swap with actual dialogue beats below.
            a.setAmbientPose(18);
            b.setAmbientPose(18);
            c.beats = c.walking ? buildWalkOpening(a, b, level)
                    : c.meeting ? buildMeetingConversation(a, b, c.topic, level)
                    : buildConversation(a, b, c.topic, level);
            c.nextBeatAt = now + 12L + a.getRandom().nextInt(18);
            String rememberedTopic = c.walking ? "WALK_TOGETHER" : c.meeting ? "MEETING_UP" : c.topic.name();
            ReactiveWorldManager.rememberTopic(a, rememberedTopic);
            ReactiveWorldManager.rememberTopic(b, rememberedTopic);
            return;
        }

        if (!c.walking || c.beat < c.beats.size()) {
            a.getNavigation().stop(); b.getNavigation().stop();
        }
        // Walking pairs get a real regroup window once the walk has begun. Ordinary
        // conversations retain their established close-range termination behavior.
        if ((!c.walking && d > 5.0D * 5.0D)
                || (c.walking && c.beat < c.beats.size() && d > 8.0D * 8.0D)) {
            finish(a, b, c); return;
        }
        if (c.beat < c.beats.size()) {
            if (now < c.nextBeatAt) return;
            Beat beat = c.beats.get(c.beat++);
            AmbientFighterEntity speaker = beat.speaker.equals(a.getUUID()) ? a : b;
            AmbientFighterEntity listener = speaker == a ? b : a;
            // Gesture state is tied to the actual spoken turn rather than being a free-floating
            // emote. This keeps social animations meaningful and automatically reverses roles.
            speaker.setAmbientPose(17);
            listener.setAmbientPose(18);
            speaker.speak(beat.text, 82);
            applyTone(speaker, listener, beat.tone);
            c.nextBeatAt = now + 52L + speaker.getRandom().nextInt(42); // 2.6-4.7 seconds between turns
            return;
        }

        // A planned walk remains temporary social-life behavior rather than companion AI, but
        // R13 gives it enough physical time and path tolerance to read as an actual shared walk.
        if (c.walking) {
            if (a.isSocialGesturePose() || a.isGroundSitting()) a.setAmbientPose(0);
            if (b.isSocialGesturePose() || b.isGroundSitting()) b.setAmbientPose(0);
            if (now < c.nextBeatAt + 25L) return;

            if (c.walkStartedAt == 0L) {
                c.walkStartedAt = now;
                c.walkUntil = now + 500L + a.getRandom().nextInt(201); // roughly 25-35 seconds after the opening
                if (a.getRandom().nextFloat() < 0.34F)
                    c.nextWalkLineAt = now + 180L + a.getRandom().nextInt(221);
                else c.nextWalkLineAt = Long.MAX_VALUE;
            }

            if (now >= c.walkUntil) {
                if (bond(a, b) < 12 && a.getRandom().nextFloat() < 0.50F) strengthenBond(a, b, 1);
                FighterIntentManager.resolveSocialIntent(a, b);
                finish(a, b, c);
                return;
            }

            if (c.walkTarget == null) {
                BlockPos target = AmbientFighterSpawner.findSafeGroundAround(level, a.blockPosition(), a.getRandom(), 7, 18, 28);
                if (target == null) target = AmbientFighterSpawner.findSafeGroundAround(level, b.blockPosition(), b.getRandom(), 6, 16, 28);
                if (target == null) {
                    // A later leg failing should not erase an otherwise successful walk. Let the pair
                    // stay together briefly, then try again instead of ending on the first awkward hill.
                    a.getNavigation().stop(); b.getNavigation().stop();
                    if (now % 30L == 0L) c.walkTarget = null;
                    return;
                }
                c.walkTarget = Vec3.atBottomCenterOf(target);
                c.walkWaypoints++;
            }

            double pairDistance = a.distanceToSqr(b);
            if (pairDistance > 8.0D * 8.0D) {
                if (c.regroupSince == 0L) c.regroupSince = now;
                AmbientFighterEntity ahead = a.position().distanceToSqr(c.walkTarget) <= b.position().distanceToSqr(c.walkTarget) ? a : b;
                AmbientFighterEntity trailing = ahead == a ? b : a;
                ahead.getNavigation().stop();
                ahead.setSprinting(false);
                trailing.setSprinting(false);
                trailing.getNavigation().moveTo(ahead, 0.78D * ReactiveWorldManager.movementPace(trailing));
                ahead.getLookControl().setLookAt(trailing, 24.0F, 20.0F);
                trailing.getLookControl().setLookAt(ahead, 24.0F, 20.0F);
                // Terrain/pathing separation gets about five seconds to recover before the social beat
                // is considered genuinely broken.
                if (now - c.regroupSince > 100L) { finish(a, b, c); }
                return;
            }
            if (c.regroupSince != 0L && pairDistance <= 5.5D * 5.5D) c.regroupSince = 0L;

            if (!c.midWalkLineDone && now >= c.nextWalkLineAt && pairDistance <= 6.0D * 6.0D) {
                AmbientFighterEntity speaker = a.getRandom().nextBoolean() ? a : b;
                String line = switch (a.getRandom().nextInt(4)) {
                    case 0 -> "Nice change of pace.";
                    case 1 -> "Good to get moving for a bit.";
                    case 2 -> "It's quieter out here.";
                    default -> "This beats standing around.";
                };
                speaker.speak(line, 58);
                c.midWalkLineDone = true;
            }

            boolean reached = a.position().distanceToSqr(c.walkTarget) < 2.5D * 2.5D
                    && b.position().distanceToSqr(c.walkTarget) < 2.5D * 2.5D;
            if (reached) {
                // Reaching an unusually close first waypoint no longer terminates the whole activity.
                // Pick another local leg and keep the same overall 25-35 second walk budget.
                c.walkTarget = null;
                a.getNavigation().stop(); b.getNavigation().stop();
                return;
            }

            a.setSprinting(false); b.setSprinting(false);
            a.getNavigation().moveTo(c.walkTarget.x, c.walkTarget.y, c.walkTarget.z, 0.72D * ReactiveWorldManager.movementPace(a));
            b.getNavigation().moveTo(c.walkTarget.x, c.walkTarget.y, c.walkTarget.z, 0.70D * ReactiveWorldManager.movementPace(b));
            a.getLookControl().setLookAt(c.walkTarget.x, c.walkTarget.y + 1.0D, c.walkTarget.z, 14.0F, 12.0F);
            b.getLookControl().setLookAt(c.walkTarget.x, c.walkTarget.y + 1.0D, c.walkTarget.z, 14.0F, 12.0F);
            return;
        }

        // A planned "hang out" block now becomes an actual quiet shared rest after the spoken
        // exchange. The pair sits together for the linger window; ordinary conversations remain
        // standing and preserve their established pacing.
        if (c.hangout) {
            a.getNavigation().stop(); b.getNavigation().stop();
            a.setDeltaMovement(0.0D, 0.0D, 0.0D); b.setDeltaMovement(0.0D, 0.0D, 0.0D);
            if (!c.hangoutSitDecided) {
                c.hangoutSitDecided = true;
                // R14: hanging out no longer means "both NPCs sit every time". Sitting is an
                // occasional presentation of the existing social activity, not its default state.
                // Social scenes stay visibly social/standing. Cross-legged ground sitting was too
                // easy to confuse with meditation and could leak between conversation states.
                c.hangoutSit = false;
            }
            if (c.hangoutSit) {
                a.setAmbientPose(7 + Math.floorMod(a.getUUID().hashCode(), 2));
                b.setAmbientPose(7 + Math.floorMod(b.getUUID().hashCode(), 2));
            } else {
                if (a.isGroundSitting()) a.setAmbientPose(0);
                if (b.isGroundSitting()) b.setAmbientPose(0);
            }
        }
        if (now < c.nextBeatAt + (c.hangout ? 180L : 35L)) return;
        int currentBond = bond(a, b);
        boolean meditate = !c.meeting && !c.walking && !c.hangout && c.topic == Topic.TRAINING
                && shouldMeditateTogether(a, b, currentBond);
        if (c.topic == Topic.RACE_KINSHIP) {
            if (markRaceKinship(a, b)) {
                strengthenBond(a, b, 1);
                ReactiveWorldManager.rememberEvent(a, "SAME_RACE", b.getFighterName(), "recognized another " + a.getRace().displayName());
                ReactiveWorldManager.rememberEvent(b, "SAME_RACE", a.getFighterName(), "recognized another " + b.getRace().displayName());
            }
        } else if (c.topic != Topic.TENSION && a.getRandom().nextFloat() < 0.62F) {
            strengthenBond(a, b, 1);
        }
        FighterIntentManager.resolveSocialIntent(a, b);
        finish(a, b, c);
        if (meditate && available(a) && available(b)) {
            a.speak(FighterDialogue.npcMeditationInvite(a.getRandom(), a.getPersonality()), 86);
            // Shared meditation begins immediately, but the reply is not spoken in the same tick.
            int sessionTicks = AmbientFighterEntity.naturalMeditationDuration(a.getRandom(), 900);
            if (a.beginSharedMeditation(b, sessionTicks)) {
                b.getPersistentData().putLong("LWPendingMeditationReply", b.level().getGameTime() + 55L + b.getRandom().nextInt(35));
                startMeditationCircle(a, b, sessionTicks);
                if (currentBond == 4 || currentBond == 8) {
                    a.recordLegacyEvent("Meditated with " + b.getFighterName());
                    b.recordLegacyEvent("Meditated with " + a.getFighterName());
                }
            }
        }
    }

    /**
     * Expands an established NPC/NPC meditation pair into a bounded 3-4 fighter circle when
     * compatible nearby fighters are physically present. Nobody is teleported or force-loaded.
     */
    private static void startMeditationCircle(AmbientFighterEntity a, AmbientFighterEntity b, int sessionTicks) {
        if (!(a.level() instanceof ServerLevel level) || b.level() != level) return;
        Vec3 center = a.position().add(b.position()).scale(0.5D);
        a.setMeditationCircleCenter(center);
        b.setMeditationCircleCenter(center);

        List<AmbientFighterEntity> candidates = level.getEntitiesOfClass(AmbientFighterEntity.class,
                a.getBoundingBox().inflate(9.5D), other -> other != a && other != b && available(other)
                        && !WorldMenaceManager.isWorldMenace(other)
                        && !LivingBondManager.isTravellingCompanion(other));
        candidates.sort(Comparator.comparingDouble(other -> other.distanceToSqr(center)));
        int joined = 0;
        for (AmbientFighterEntity other : candidates) {
            if (joined >= 2) break;
            int bondA = bond(a, other), bondB = bond(b, other);
            boolean naturalFit = meditationFriendly(other.getPersonality()) || bondA >= 4 || bondB >= 4;
            float chance = naturalFit ? 0.62F : 0.20F;
            if (other.getRandom().nextFloat() >= chance) continue;
            if (other.beginMeditationCircle(center, sessionTicks)) {
                joined++;
                other.getPersistentData().putLong("LWPendingMeditationReply", other.level().getGameTime() + 75L + other.getRandom().nextInt(55));
                if (bondA >= 4 || bondB >= 4) other.recordLegacyEvent("Joined a group meditation");
            }
        }
    }

    private static void moveForMeeting(AmbientFighterEntity mover, AmbientFighterEntity other, double pairDistanceSq, long now) {
        double distance = Math.sqrt(Math.max(0.0D, pairDistanceSq));
        boolean fly = distance > 24.0D && mover.hasFlightUnlocked() && !mover.isInWaterOrBubble();
        if (fly) {
            mover.getNavigation().stop();
            mover.setSprinting(false);
            mover.setAmbientFlightActivity(true);
            mover.setFlying(true);
            mover.setFlyingFast(distance > 46.0D);
            Vec3 destination = other.position().add(0.0D, 0.8D, 0.0D);
            mover.steerAmbientFlightToward(destination, distance > 46.0D ? 0.50D : 0.36D);
            mover.getLookControl().setLookAt(destination.x, destination.y, destination.z, 24.0F, 20.0F);
            return;
        }
        if (mover.isAmbientFlightActivity()) {
            mover.setFlyingFast(false);
            mover.setFlying(false);
            mover.setNoGravity(false);
            mover.setAmbientFlightActivity(false);
        }
        boolean run = distance > 10.0D;
        mover.setSprinting(run);
        mover.setLocomotionMode(run
                ? com.dragonminez.common.init.entities.sagas.DBSagasEntity.LocomotionMode.RUN
                : com.dragonminez.common.init.entities.sagas.DBSagasEntity.LocomotionMode.WALK);
        if (mover.getNavigation().isDone() || now % 15L == Math.floorMod(mover.getId(), 15))
            mover.getNavigation().moveTo(other, (run ? 1.18D : 0.84D) * ReactiveWorldManager.movementPace(mover));
    }

    private static void stopMeetingTravel(AmbientFighterEntity fighter) {
        if (fighter == null) return;
        fighter.setSprinting(false);
        fighter.setFlyingFast(false);
        if (fighter.isAmbientFlightActivity()) {
            fighter.setFlying(false);
            fighter.setNoGravity(false);
            fighter.setAmbientFlightActivity(false);
        }
        if (fighter.getTarget() == null && !fighter.isFlying())
            fighter.setLocomotionMode(com.dragonminez.common.init.entities.sagas.DBSagasEntity.LocomotionMode.IDLE);
    }

    private static List<Beat> buildMeetingConversation(AmbientFighterEntity a, AmbientFighterEntity b, Topic topic, ServerLevel level) {
        List<Beat> beats = new ArrayList<>();
        beats.add(new Beat(a.getUUID(), pick(a,
                "There you are.", "Good, I caught up with you.", "Glad I found you.", "Hey. I wanted to see you."), Tone.WARM));
        beats.add(new Beat(b.getUUID(), replyByTemperament(b,
                "Good to see you too.", "Yeah. What's up?", "You came all this way for me?"), Tone.NEUTRAL));
        List<Beat> context = buildConversation(a, b, topic, level);
        for (int i = 0; i < Math.min(2, context.size()); i++) beats.add(context.get(i));
        return List.copyOf(beats);
    }

    /** Called from the fighter tick so a meditation reply can happen naturally after the invitation. */
    public static void tickPendingReply(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide) return;
        long due = fighter.getPersistentData().getLong("LWPendingMeditationReply");
        if (due <= 0L || fighter.level().getGameTime() < due) return;
        fighter.getPersistentData().remove("LWPendingMeditationReply");
        if (fighter.isMeditating() && fighter.getSpeech().isEmpty())
            fighter.speak(FighterDialogue.npcMeditationReply(fighter.getRandom(), fighter.getPersonality()), 86);
    }

    private static Topic chooseTopic(AmbientFighterEntity a, AmbientFighterEntity b, ServerLevel level) {
        String aa = FighterAmbientActivityManager.recentActivity(a);
        String ba = FighterAmbientActivityManager.recentActivity(b);
        if (!aa.isBlank() && aa.equalsIgnoreCase(ba) && a.getRandom().nextFloat() < 0.72F) return Topic.ACTIVITY;
        if ((FighterScientistManager.isScientist(a) || FighterScientistManager.isScientist(b)) && a.getRandom().nextFloat() < 0.52F) return Topic.ACTIVITY;
        if (FighterIntentManager.isConcernedAbout(a, b) || FighterIntentManager.isConcernedAbout(b, a)) return Topic.RECOVERY;
        if (a.getHealth() < a.getMaxHealth() * 0.55F || b.getHealth() < b.getMaxHealth() * 0.55F) return Topic.RECOVERY;
        if ((!ReactiveWorldManager.recentEventType(a, 2600L).isBlank()
                || !ReactiveWorldManager.recentEventType(b, 2600L).isBlank())
                && a.getRandom().nextFloat() < 0.72F) return Topic.RECENT_EVENT;
        int currentBond = bond(a, b);
        ReactiveWorldManager.Mood am = ReactiveWorldManager.mood(a), bm = ReactiveWorldManager.mood(b);
        if (currentBond >= 4 && (am == ReactiveWorldManager.Mood.WEARY || bm == ReactiveWorldManager.Mood.WEARY
                || am == ReactiveWorldManager.Mood.SOMBER || bm == ReactiveWorldManager.Mood.SOMBER)) return Topic.ENCOURAGEMENT;
        if ((am == ReactiveWorldManager.Mood.IRRITATED || bm == ReactiveWorldManager.Mood.IRRITATED)
                && (ReactiveWorldManager.impression(a, b) < 0 || ReactiveWorldManager.impression(b, a) < 0
                || ReactiveWorldManager.temperament(a) == ReactiveWorldManager.Temperament.BULLY
                || ReactiveWorldManager.temperament(b) == ReactiveWorldManager.Temperament.BULLY)) return Topic.TENSION;
        if (a.getRace() == b.getRace() && !ReactiveWorldManager.topicRecentlyUsed(a, Topic.RACE_KINSHIP.name(), 5200L)) {
            float raceChance = switch (a.getRace()) {
                case HUMAN -> 0.08F;
                case SAIYAN -> 0.28F;
                case NAMEKIAN -> 0.26F;
                case MAJIN -> 0.22F;
                case FROST_DEMON -> 0.25F;
                case BIO_ANDROID -> 0.24F;
            };
            if (!hasRaceKinshipSeen(a, b)) raceChance += 0.18F;
            if (a.getRandom().nextFloat() < raceChance) return Topic.RACE_KINSHIP;
        }
        if ((am == ReactiveWorldManager.Mood.FOCUSED || bm == ReactiveWorldManager.Mood.FOCUSED) && a.getRandom().nextFloat() < 0.58F) return Topic.TRAINING;
        if ((am == ReactiveWorldManager.Mood.WARY || bm == ReactiveWorldManager.Mood.WARY) && a.getRandom().nextFloat() < 0.52F)
            return a.isFactionMember() && b.isFactionMember() && a.getFactionId().equals(b.getFactionId()) ? Topic.FACTION : Topic.WORLD;
        if ((am == ReactiveWorldManager.Mood.UPBEAT || bm == ReactiveWorldManager.Mood.UPBEAT) && a.getRandom().nextFloat() < 0.46F)
            return a.getRandom().nextBoolean() ? Topic.JOKE : Topic.HOBBY;

        if ((!aa.isBlank() || !ba.isBlank()) && a.getRandom().nextFloat() < 0.45F) return Topic.ACTIVITY;
        if (ReactiveWorldManager.temperament(a) == ReactiveWorldManager.Temperament.BULLY
                && a.getBattlePower() > b.getBattlePower() * 1.15D && a.getRandom().nextFloat() < 0.45F) return Topic.TENSION;
        if (bond(a, b) >= 6 && (ReactiveWorldManager.mood(a) == ReactiveWorldManager.Mood.WEARY
                || ReactiveWorldManager.mood(b) == ReactiveWorldManager.Mood.WEARY)) return Topic.ENCOURAGEMENT;
        if (a.isFactionMember() && b.isFactionMember() && a.getFactionId().equals(b.getFactionId()) && a.getRandom().nextFloat() < 0.34F) return Topic.FACTION;
        if (level.isRaining() && a.getRandom().nextFloat() < 0.18F && !ReactiveWorldManager.topicRecentlyUsed(a, Topic.WEATHER.name(), 4200L)) return Topic.WEATHER;
        long time = Math.floorMod(level.getDayTime(), 24000L);
        if (time >= 13000L && time <= 23000L && a.getRandom().nextFloat() < 0.12F && !ReactiveWorldManager.topicRecentlyUsed(a, Topic.NIGHT.name(), 5000L)) return Topic.NIGHT;
        if (FighterHobby.of(a) == FighterHobby.of(b) || a.getRandom().nextFloat() < 0.22F) return Topic.HOBBY;
        int roll = a.getRandom().nextInt(100);
        if (roll < 32) return Topic.TRAINING;
        if (roll < 60) return Topic.WORLD;
        if (roll < 78) return Topic.HOBBY;
        return Topic.JOKE;
    }

    private static List<Beat> buildWalkOpening(AmbientFighterEntity a, AmbientFighterEntity b, ServerLevel level) {
        int variant = Math.floorMod(a.getUUID().hashCode() ^ b.getUUID().hashCode() ^ (int)level.getGameTime(), 3);
        String invite = switch (variant) {
            case 0 -> "Want to walk for a bit?";
            case 1 -> "Come on, let's take a walk.";
            default -> "Feel like stretching your legs?";
        };
        String reply;
        if (bond(a, b) >= 6) reply = variant == 1 ? "Yeah. Let's go." : "Sure. Lead the way.";
        else reply = variant == 2 ? "Alright. A short walk sounds good." : "Sure. Why not?";
        return List.of(new Beat(a.getUUID(), invite, Tone.WARM), new Beat(b.getUUID(), reply, Tone.WARM));
    }

    private static List<Beat> buildConversation(AmbientFighterEntity a, AmbientFighterEntity b, Topic topic, ServerLevel level) {
        List<Beat> beats = new ArrayList<>();
        int v = Math.floorMod(a.getUUID().hashCode() ^ b.getUUID().hashCode() ^ (int)level.getGameTime(), 8);
        ReactiveWorldManager.Temperament ta = ReactiveWorldManager.temperament(a), tb = ReactiveWorldManager.temperament(b);
        switch (topic) {
            case RECOVERY -> {
                AmbientFighterEntity hurt = a.getHealth() / a.getMaxHealth() <= b.getHealth() / b.getMaxHealth() ? a : b;
                AmbientFighterEntity other = hurt == a ? b : a;
                ReactiveWorldManager.Temperament otherTemperament = ReactiveWorldManager.temperament(other);
                boolean bully = otherTemperament == ReactiveWorldManager.Temperament.BULLY;
                beats.add(new Beat(other.getUUID(), bully ? "You're hurt. Still think you can keep up?" : "You're hurt. How bad is it?", bully ? Tone.HOSTILE : Tone.WARM));
                beats.add(new Beat(hurt.getUUID(), replyByTemperament(hurt, "I'll manage, but I need a minute.", "It hurts. I can still move.", "I know I'm hurt. Worry about the fight."), Tone.NEUTRAL));
                beats.add(new Beat(other.getUUID(), bully ? "Then stop looking like you're about to fall over." : "Then slow down until you can move properly.", bully ? Tone.HOSTILE : Tone.WARM));
            }
            case RECENT_EVENT -> {
                AmbientFighterEntity witness = !ReactiveWorldManager.recentEventType(a, 2600L).isBlank() ? a : b;
                AmbientFighterEntity listener = witness == a ? b : a;
                String type = ReactiveWorldManager.recentEventType(witness, 2600L);
                String subject = ReactiveWorldManager.recentEventSubject(witness, 2600L);
                if ("ALLY_DIED".equals(type)) {
                    beats.add(new Beat(witness.getUUID(), "I saw " + subject + " go down back there.", Tone.NEUTRAL));
                    beats.add(new Beat(listener.getUUID(), replyByTemperament(listener,
                            "I know. We make sure it wasn't for nothing.",
                            "Yeah. We keep moving, but we remember it.",
                            "Then we stop the next one from happening."), Tone.WARM));
                    beats.add(new Beat(witness.getUUID(), "Right. Nobody else falls if we can help it.", Tone.WARM));
                } else if ("ENEMY_DIED".equals(type)) {
                    beats.add(new Beat(witness.getUUID(), subject + " went down in that fight.", Tone.NEUTRAL));
                    beats.add(new Beat(listener.getUUID(), listener.getAlignment() == FighterAlignment.BAD
                            ? "Good. One less problem in our way."
                            : "Then that fight is over. No reason to keep hitting a body.", Tone.NEUTRAL));
                    beats.add(new Beat(witness.getUUID(), "Agreed. We watch for whoever is still standing.", Tone.NEUTRAL));
                } else if ("MOB_SEEN".equals(type)) {
                    String lower = subject.toLowerCase(Locale.ROOT);
                    beats.add(new Beat(witness.getUUID(), "There was a " + lower + " nearby earlier.", Tone.NEUTRAL));
                    if (lower.contains("red ribbon") || lower.contains("robot") || lower.contains("bandit")) {
                        beats.add(new Beat(listener.getUUID(), "I saw it too. We should keep it in sight until we're clear of the area.", Tone.NEUTRAL));
                        beats.add(new Beat(witness.getUUID(), "Exactly. No reason to let it walk up behind us.", Tone.NEUTRAL));
                    } else if (lower.contains("dinosaur")) {
                        beats.add(new Beat(listener.getUUID(), "Hard to miss. Let's not corner it unless we want another fight.", Tone.NEUTRAL));
                        beats.add(new Beat(witness.getUUID(), "Agreed. Give it room and keep moving.", Tone.NEUTRAL));
                    } else {
                        beats.add(new Beat(listener.getUUID(), "Yeah, I noticed it. Nice reminder that not everything nearby wants a fight.", Tone.WARM));
                        beats.add(new Beat(witness.getUUID(), "For once, I'll take that.", Tone.NEUTRAL));
                    }
                } else if ("WORLD_CONDITION".equals(type)) {
                    beats.add(new Beat(witness.getUUID(), "I was just thinking about " + subject + ".", Tone.NEUTRAL));
                    beats.add(new Beat(listener.getUUID(), level.isRaining() ? "Same. It changes how far you can see and hear." : "Yeah. It changes the whole feel of this place.", Tone.NEUTRAL));
                    beats.add(new Beat(witness.getUUID(), "Worth keeping in mind if trouble starts.", Tone.NEUTRAL));
                } else if ("TRAINING_GROWTH".equals(type)) {
                    beats.add(new Beat(witness.getUUID(), "Something finally clicked in my last training session.", Tone.WARM));
                    beats.add(new Beat(listener.getUUID(), "I thought your Ki felt steadier. You're not imagining it.", Tone.WARM));
                    beats.add(new Beat(witness.getUUID(), "Good. Then I know which part of the routine to keep.", Tone.NEUTRAL));
                } else {
                    String detail = ReactiveWorldManager.recentEventDetail(witness, 2600L);
                    beats.add(new Beat(witness.getUUID(), detail.isBlank() ? "Something happened nearby earlier." : detail + ".", Tone.NEUTRAL));
                    beats.add(new Beat(listener.getUUID(), "I noticed. I'm keeping it in mind.", Tone.NEUTRAL));
                }
            }
            case RACE_KINSHIP -> {
                FighterRace race = a.getRace();
                switch (race) {
                    case SAIYAN -> {
                        beats.add(new Beat(a.getUUID(), "You're a Saiyan too. I could tell from the way you carry your power.", Tone.NEUTRAL));
                        beats.add(new Beat(b.getUUID(), b.getPersonality() == FighterPersonality.PROUD
                                ? "Of course you could. Saiyan pride isn't exactly subtle."
                                : "Yeah. It's strange how familiar another Saiyan's presence feels.", Tone.NEUTRAL));
                        beats.add(new Beat(a.getUUID(), a.getPersonality() == FighterPersonality.AGGRESSIVE
                                ? "Good. Then I know who to ask when I need a real training partner."
                                : "We should train sometime. No point wasting that kind of common ground.", Tone.WARM));
                    }
                    case NAMEKIAN -> {
                        beats.add(new Beat(a.getUUID(), "Another Namekian. I don't run into that very often out here.", Tone.WARM));
                        beats.add(new Beat(b.getUUID(), "Same. There's something reassuring about hearing that from one of our own.", Tone.WARM));
                        beats.add(new Beat(a.getUUID(), "Then let's remember each other. The world is wide enough already.", Tone.WARM));
                    }
                    case MAJIN -> {
                        beats.add(new Beat(a.getUUID(), "Huh. Another Majin. That explains why your energy felt familiar.", Tone.TEASING));
                        beats.add(new Beat(b.getUUID(), ReactiveWorldManager.temperament(b) == ReactiveWorldManager.Temperament.TEASING
                                ? "Familiar? I was going to say better." : "I noticed yours too. Hard to mistake it once you know it.", Tone.TEASING));
                        beats.add(new Beat(a.getUUID(), "Either way, we're not exactly common. That's worth something.", Tone.WARM));
                    }
                    case FROST_DEMON -> {
                        beats.add(new Beat(a.getUUID(), "You're one of my kind. I wondered why your energy felt so... familiar.", Tone.NEUTRAL));
                        beats.add(new Beat(b.getUUID(), b.getPersonality() == FighterPersonality.PROUD
                                ? "Then you should know better than to underestimate me."
                                : "I noticed the same thing. There aren't many of us around here.", Tone.NEUTRAL));
                        beats.add(new Beat(a.getUUID(), "Rare company, then. I'll remember you.", Tone.WARM));
                    }
                    case BIO_ANDROID -> {
                        beats.add(new Beat(a.getUUID(), "Your energy pattern is like mine. Another Bio-Android.", Tone.NEUTRAL));
                        beats.add(new Beat(b.getUUID(), "I noticed. Similar origin doesn't mean identical purpose, though.", Tone.NEUTRAL));
                        beats.add(new Beat(a.getUUID(), "No. But it gives us something real to compare.", Tone.WARM));
                    }
                    case HUMAN -> {
                        beats.add(new Beat(a.getUUID(), "Funny. With everyone around here, it's nice meeting another ordinary Human fighter.", Tone.WARM));
                        beats.add(new Beat(b.getUUID(), b.getPersonality() == FighterPersonality.PROUD
                                ? "Ordinary is doing a lot of work in that sentence."
                                : "I know what you mean. Makes the place feel a little less strange.", Tone.WARM));
                        beats.add(new Beat(a.getUUID(), "Fair. Human doesn't have to mean ordinary.", Tone.WARM));
                    }
                }
            }
            case ACTIVITY -> {
                String aa = FighterAmbientActivityManager.recentActivity(a);
                String ba = FighterAmbientActivityManager.recentActivity(b);
                if (FighterScientistManager.isScientist(a) || FighterScientistManager.isScientist(b)) {
                    AmbientFighterEntity scientist = FighterScientistManager.isScientist(a) ? a : b;
                    AmbientFighterEntity listener = scientist == a ? b : a;
                    beats.add(new Beat(listener.getUUID(), "Still working on those Saibaman cultivation notes?", Tone.NEUTRAL));
                    beats.add(new Beat(scientist.getUUID(), pick(scientist,
                            "Yeah. The last batch gave me enough data to change the formula.",
                            "I am. Power is easy to raise; keeping the specimens controllable is harder.",
                            "Almost. I want the next batch to scale cleanly without becoming unstable.",
                            "I found one bad ratio. Now I'm checking whether fixing it changes their temperament."), Tone.NEUTRAL));
                    beats.add(new Beat(listener.getUUID(), bond(a, b) >= 6
                            ? "Just make sure your research subjects know which side they're on."
                            : "I'll give your lab work some distance, then.", Tone.WARM));
                } else {
                    String activity = !aa.isBlank() && aa.equalsIgnoreCase(ba) ? aa : !aa.isBlank() ? aa : ba;
                    AmbientFighterEntity actor = !aa.isBlank() ? a : b;
                    AmbientFighterEntity observer = actor == a ? b : a;
                    if (activity.isBlank()) activity = "taking it easy";
                    beats.add(new Beat(observer.getUUID(), "I saw you " + activityPhrase(activity) + " earlier.", Tone.NEUTRAL));
                    beats.add(new Beat(actor.getUUID(), replyByTemperament(actor,
                            "Yeah. It fit what I needed at the time.",
                            "It helped. I was trying not to force the rest of the day.",
                            "You keeping a schedule on me now?"),
                            ReactiveWorldManager.temperament(actor) == ReactiveWorldManager.Temperament.TEASING ? Tone.TEASING : Tone.NEUTRAL));
                    beats.add(new Beat(observer.getUUID(), "No. I just noticed. Better than doing the same thing all day.", Tone.WARM));
                }
            }
            case TRAINING -> {
                String[] q = {"Been keeping up with your training?", "Your movement looks sharper lately.", "Still working on that weak side?", "You feel stronger than last time.",
                        "You changed something in your stance, didn't you?", "Your Ki feels steadier than it did before.",
                        "How's the new routine treating you?", "You've been training hard. You actually giving yourself time to recover?"};
                beats.add(new Beat(a.getUUID(), q[v], Tone.NEUTRAL));
                beats.add(new Beat(b.getUUID(), replyByTemperament(b, "Every day I can.", "Enough to know I've got more work to do.", "Keep watching and you'll find out."), tb == ReactiveWorldManager.Temperament.TEASING ? Tone.TEASING : Tone.NEUTRAL));
                beats.add(new Beat(a.getUUID(), ta == ReactiveWorldManager.Temperament.BULLY ? "Good. You need it." : "It shows. Keep at it.", ta == ReactiveWorldManager.Temperament.BULLY ? Tone.HOSTILE : Tone.WARM));
                if (bond(a,b) >= 6) beats.add(new Beat(b.getUUID(), "Maybe we train together next time.", Tone.WARM));
            }
            case HOBBY -> {
                FighterHobby ah = FighterHobby.of(a), bh = FighterHobby.of(b);
                beats.add(new Beat(a.getUUID(), "I've been " + ah.activity() + " lately.", Tone.NEUTRAL));
                beats.add(new Beat(b.getUUID(), ah == bh ? "Same here. It's good to have something normal." : "Better than my habit of " + bh.activity() + ".", Tone.WARM));
                beats.add(new Beat(a.getUUID(), ta == ReactiveWorldManager.Temperament.TEASING ? "Normal might be asking too much from us." : "Keeps the world from becoming only fights.", ta == ReactiveWorldManager.Temperament.TEASING ? Tone.TEASING : Tone.NEUTRAL));
            }
            case FACTION -> {
                beats.add(new Beat(a.getUUID(), "How are things holding together with the others?", Tone.NEUTRAL));
                beats.add(new Beat(b.getUUID(), ReactiveWorldManager.mood(b) == ReactiveWorldManager.Mood.WARY ? "Tense. Everyone can feel it." : "Steady enough. I'll take that as a win.", Tone.NEUTRAL));
                beats.add(new Beat(a.getUUID(), ta == ReactiveWorldManager.Temperament.SUPPORTIVE ? "Then we keep looking out for each other." : "Good. Let's keep it that way.", Tone.WARM));
            }
            case WEATHER -> {
                beats.add(new Beat(a.getUUID(), "This weather's getting old.", Tone.NEUTRAL));
                beats.add(new Beat(b.getUUID(), tb == ReactiveWorldManager.Temperament.ALOOF ? "It's just rain." : "At least it keeps the dust down.", Tone.NEUTRAL));
                beats.add(new Beat(a.getUUID(), ta == ReactiveWorldManager.Temperament.TEASING ? "Tell me that when lightning finds your hair." : "I'd still rather train under a clear sky.", Tone.TEASING));
            }
            case NIGHT -> {
                beats.add(new Beat(a.getUUID(), "Quiet night.", Tone.NEUTRAL));
                beats.add(new Beat(b.getUUID(), tb == ReactiveWorldManager.Temperament.ALOOF ? "That's why I like it." : "Quiet usually means someone is about to ruin it.", Tone.NEUTRAL));
                beats.add(new Beat(a.getUUID(), "Then let's enjoy the quiet while it lasts.", Tone.WARM));
            }
            case ENCOURAGEMENT -> {
                beats.add(new Beat(a.getUUID(), "You've been carrying a lot lately.", Tone.WARM));
                beats.add(new Beat(b.getUUID(), replyByTemperament(b, "I'll be alright.", "Yeah... I know.", "Since when did you get sentimental?"), Tone.NEUTRAL));
                beats.add(new Beat(a.getUUID(), "You don't have to carry all of it alone.", Tone.WARM));
                beats.add(new Beat(b.getUUID(), "...Thanks.", Tone.WARM));
            }
            case TENSION -> {
                beats.add(new Beat(a.getUUID(), "Try not to slow everyone down again.", Tone.HOSTILE));
                beats.add(new Beat(b.getUUID(), b.getPersonality() == FighterPersonality.PROUD || b.getPersonality() == FighterPersonality.AGGRESSIVE ? "Say that again when you're ready to back it up." : "You could try being useful instead of loud.", Tone.HOSTILE));
                beats.add(new Beat(a.getUUID(), ta == ReactiveWorldManager.Temperament.BULLY ? "There's the spirit." : "Relax. I'm messing with you.", ta == ReactiveWorldManager.Temperament.BULLY ? Tone.TEASING : Tone.NEUTRAL));
            }
            case WORLD -> {
                int nearbyFighters = level.getEntitiesOfClass(AmbientFighterEntity.class,
                        a.getBoundingBox().inflate(24.0D), f -> f.isAlive() && f != a && f != b).size();
                long localTime = Math.floorMod(level.getDayTime(), 24000L);
                if (nearbyFighters >= 3) {
                    beats.add(new Beat(a.getUUID(), "There are a lot of fighters close by right now.", Tone.NEUTRAL));
                    beats.add(new Beat(b.getUUID(), "I noticed. I'm trying to keep track of who belongs with who.", Tone.NEUTRAL));
                    beats.add(new Beat(a.getUUID(), "Same. Better than mistaking somebody's friend for an enemy.", Tone.NEUTRAL));
                } else if (localTime >= 13000L && localTime <= 23000L) {
                    beats.add(new Beat(a.getUUID(), "It's late. The area finally quieted down.", Tone.NEUTRAL));
                    beats.add(new Beat(b.getUUID(), "Good. I could use a few minutes without someone starting a fight.", Tone.NEUTRAL));
                    beats.add(new Beat(a.getUUID(), "Then let's not be the ones who ruin it.", Tone.WARM));
                } else {
                    beats.add(new Beat(a.getUUID(), "Nothing hostile nearby at the moment.", Tone.NEUTRAL));
                    beats.add(new Beat(b.getUUID(), "Good. Gives us time to recover before the next problem finds us.", Tone.NEUTRAL));
                    beats.add(new Beat(a.getUUID(), "I'll take a quiet stretch when I can get one.", Tone.NEUTRAL));
                }
            }
            case JOKE -> {
                String[] q = {"You ever wonder who repairs the ground after our fights?", "I tried counting how many times I've been launched through a hill.", "Do you think yelling makes techniques stronger?", "I saw a chicken survive a Ki blast yesterday.",
                        "I think that tree has seen more fights than either of us.", "Be honest. Have you ever pretended not to hear someone challenge you?",
                        "If another person tells me to 'just sense their Ki,' I'm charging them for lessons.", "I nearly tripped over my own landing earlier. Nobody saw it, right?"};
                String[] r = {"I assumed the ground accepted its fate.", "That's not a statistic I'd brag about.", "Obviously. Science is settled.", "Finally, someone here with proper defense.",
                        "Then the tree is the veteran here.", "Only when I was hoping they'd challenge somebody else.",
                        "Make it double if they ask twice.", "I saw nothing. Your dignity is safe."};
                beats.add(new Beat(a.getUUID(), q[v], Tone.TEASING));
                beats.add(new Beat(b.getUUID(), r[v], Tone.TEASING));
                beats.add(new Beat(a.getUUID(), switch (v) {
                    case 2 -> "Knew it."; case 5 -> "See? I'm not the only one."; case 7 -> "Good. Let's keep it that way.";
                    default -> "Fair point.";
                }, Tone.TEASING));
            }
        }
        return List.copyOf(beats);
    }

    private static void applyTone(AmbientFighterEntity speaker, AmbientFighterEntity listener, Tone tone) {
        if (tone == Tone.WARM) ReactiveWorldManager.supportiveExchange(speaker, listener);
        else if (tone == Tone.HOSTILE) ReactiveWorldManager.hostileExchange(speaker, listener);
    }

    private static String pick(AmbientFighterEntity fighter, String... lines) {
        return lines[fighter.getRandom().nextInt(lines.length)];
    }

    private static String replyByTemperament(AmbientFighterEntity fighter, String warm, String neutral, String sharp) {
        return switch (ReactiveWorldManager.temperament(fighter)) {
            case SUPPORTIVE, WARM -> warm;
            case BLUNT, BULLY, TEASING -> sharp;
            case ALOOF -> neutral;
        };
    }

    private static String activityPhrase(String activity) {
        if (activity == null || activity.isBlank()) return "taking a break";
        String lower = activity.toLowerCase(Locale.ROOT);
        if (lower.contains("fish")) return "fishing";
        if (lower.contains("star")) return "watching the sky";
        if (lower.contains("fly")) return "flying around";
        if (lower.contains("train")) return "training";
        if (lower.contains("jog")) return "out on a run";
        if (lower.contains("walk")) return "taking a walk";
        if (lower.contains("study") || lower.contains("notes")) return "reviewing some notes";
        if (lower.contains("dance")) return "dancing";
        if (lower.contains("flower")) return "looking at that flower";
        if (lower.contains("apple") || lower.contains("tree")) return "getting an apple from that tree";
        if (lower.contains("rest")) return "resting";
        if (lower.contains("scout") || lower.contains("looking")) return "checking the area";
        if (lower.contains("eat")) return "grabbing something to eat";
        return "taking a break";
    }

    /** Immediate lifecycle cleanup for a fighter that died/was archived. */
    public static void cancelFor(AmbientFighterEntity fighter) {
        if (fighter == null) return;
        Conversation c = CONVERSATIONS.get(fighter.getUUID());
        if (c == null) return;
        ServerLevel level = fighter.level() instanceof ServerLevel sl ? sl : null;
        AmbientFighterEntity a = resolveConversationFighter(level, c.a, fighter);
        AmbientFighterEntity b = resolveConversationFighter(level, c.b, fighter);
        finish(a, b, c);
    }

    private static AmbientFighterEntity resolveConversationFighter(ServerLevel level, UUID id, AmbientFighterEntity known) {
        if (known != null && known.getUUID().equals(id)) return known;
        if (level == null || id == null) return null;
        var entity = level.getEntity(id);
        return entity instanceof AmbientFighterEntity fighter ? fighter : null;
    }

    private static void finish(AmbientFighterEntity a, AmbientFighterEntity b, Conversation c) {
        CONVERSATIONS.remove(c.a); CONVERSATIONS.remove(c.b);
        if (c != null && c.settledAt > 0L) {
            FighterLifeNeedsManager.onSocialCompleted(a, c.meeting || c.hangout, c.walking);
            FighterLifeNeedsManager.onSocialCompleted(b, c.meeting || c.hangout, c.walking);
        }
        if (a != null) {
            a.getNavigation().stop();
            // Social manager owns these temporary talk/hangout poses; never let a final gesture
            // freeze into subsequent combat, meditation or ordinary life.
            if (a.isSocialGesturePose() || a.isGroundSitting()) a.setAmbientPose(0);
            stopMeetingTravel(a);
            a.setSocialLifeActivity(false);
        }
        if (b != null) {
            b.getNavigation().stop();
            if (b.isSocialGesturePose() || b.isGroundSitting()) b.setAmbientPose(0);
            stopMeetingTravel(b);
            b.setSocialLifeActivity(false);
        }
    }

    /** Debug forcing owns the nearest fighter; end an ordinary conversation cleanly first. */
    public static void cancelForDebug(AmbientFighterEntity fighter) {
        if (fighter == null) return;
        Conversation c = CONVERSATIONS.get(fighter.getUUID());
        if (c == null || !(fighter.level() instanceof ServerLevel level)) return;
        AmbientFighterEntity a = level.getEntity(c.a) instanceof AmbientFighterEntity f ? f : null;
        AmbientFighterEntity b = level.getEntity(c.b) instanceof AmbientFighterEntity f ? f : null;
        finish(a, b, c);
    }

    private static boolean available(AmbientFighterEntity fighter) {
        return !WorldMenaceManager.isWorldMenace(fighter) && fighter.isAlive() && !fighter.isCaptive() && !fighter.isDefeated() && !fighter.isMeditating() && !fighter.isPreparingMeditation()
                && !fighter.isTransforming() && !fighter.isKaiokenActive() && fighter.getTarget() == null
                && !fighter.isSocialLifeActivity() && !fighter.isSocialPlayerApproach() && !fighter.isSocialPowerDisplay()
                && !fighter.isSanctionedMatchParticipant() && !LivingBondManager.isTravellingCompanion(fighter);
    }
    private static boolean availableDuring(AmbientFighterEntity fighter) {
        long lastDamage = fighter.getPersistentData().getLong("LWLastDamageTime");
        boolean recentlyHurt = lastDamage > 0L && fighter.level().getGameTime() - lastDamage <= 45L;
        return !WorldMenaceManager.isWorldMenace(fighter) && fighter.isAlive() && !fighter.isCaptive() && !fighter.isDefeated() && !fighter.isMeditating() && !fighter.isPreparingMeditation()
                && !recentlyHurt && !fighter.isTransforming() && !fighter.isKaiokenActive() && fighter.getTarget() == null
                && !fighter.isSocialPlayerApproach() && !fighter.isSocialPowerDisplay() && !fighter.isSanctionedMatchParticipant()
                && !LivingBondManager.isTravellingCompanion(fighter);
    }

    private static boolean compatible(AmbientFighterEntity a, AmbientFighterEntity b) {
        if (a == null || b == null || a == b || a.getUUID().equals(b.getUUID()) || socialId(a).equals(socialId(b))) return false;
        int currentBond = bond(a, b);
        if (!ReactiveWorldManager.allowsCasualSocial(a, b, currentBond)
                || !ReactiveWorldManager.allowsCasualSocial(b, a, bond(b, a))) return false;
        if (b.getFighterName().equals(a.getRivalName()) || a.getFighterName().equals(b.getRivalName())) return false;
        if (FactionManager.areEnemies(a, b)) return false;
        if (a.isFactionMember() && b.isFactionMember()) {
            if (a.getFactionId().equals(b.getFactionId()) || FactionManager.areAllies(a, b)) return true;
            if (FactionManager.areRivals(a, b)) return false;
        }
        if (a.getAlignment() == FighterAlignment.BAD || b.getAlignment() == FighterAlignment.BAD)
            return a.getAlignment() == b.getAlignment() && a.isFactionMember() && b.isFactionMember();
        return true;
    }

    private static boolean shouldMeditateTogether(AmbientFighterEntity a, AmbientFighterEntity b, int bond) {
        if (bond < 4 || !MeditationCompat.isNpcMeditationEnabled()) return false;
        if (a.getRandom().nextFloat() >= 0.055F) return false;
        return meditationFriendly(a.getPersonality()) || meditationFriendly(b.getPersonality()) || bond >= 8;
    }
    private static boolean meditationFriendly(FighterPersonality personality) {
        return personality == FighterPersonality.CALM || personality == FighterPersonality.CAUTIOUS || personality == FighterPersonality.HEROIC;
    }

    private static UUID socialId(AmbientFighterEntity fighter) {
        CompoundTag legacy = fighter.getLegacyData();
        if (!legacy.hasUUID(SOCIAL_ID)) legacy.putUUID(SOCIAL_ID, fighter.getUUID());
        return legacy.getUUID(SOCIAL_ID);
    }
    public static int bond(AmbientFighterEntity a, AmbientFighterEntity b) {
        if (a == null || b == null) return 0;
        return Math.max(0, Math.min(12, bondRoot(a).getInt(socialId(b).toString())));
    }

    /** Number of remembered NPC relationships strong enough to shape ordinary daily life. */
    public static int meaningfulBondCount(AmbientFighterEntity fighter) {
        return fighter == null ? 0 : meaningfulBondCount(fighter.getLegacyData());
    }

    /** Same query for remembered/off-screen profiles; keeps the daily planner on one social model. */
    public static int meaningfulBondCount(CompoundTag legacy) {
        if (legacy == null || !legacy.contains(BONDS, Tag.TAG_COMPOUND)) return 0;
        CompoundTag root = legacy.getCompound(BONDS);
        int count = 0;
        for (String key : root.getAllKeys()) if (root.getInt(key) >= 4) count++;
        return count;
    }
    private static void strengthenBond(AmbientFighterEntity a, AmbientFighterEntity b, int amount) {
        setBond(a, b, bond(a, b) + amount); setBond(b, a, bond(b, a) + amount);
    }
    private static void setBond(AmbientFighterEntity owner, AmbientFighterEntity other, int value) {
        CompoundTag root = bondRoot(owner), names = nameRoot(owner);
        String key = socialId(other).toString();
        root.putInt(key, Math.max(0, Math.min(12, value))); names.putString(key, other.getFighterName());
        if (root.getAllKeys().size() > 24) {
            String weakest = null; int weakestValue = Integer.MAX_VALUE;
            for (String candidate : root.getAllKeys()) {
                if (candidate.equals(key)) continue;
                int v = root.getInt(candidate); if (v < weakestValue) { weakestValue = v; weakest = candidate; }
            }
            if (weakest != null) { root.remove(weakest); names.remove(weakest); }
        }
        owner.getLegacyData().put(BONDS, root); owner.getLegacyData().put(NAMES, names);
    }

    public static AmbientFighterEntity closestMeaningfulBond(AmbientFighterEntity fighter, double radius) {
        if (fighter == null || !(fighter.level() instanceof ServerLevel level)) return null;
        return level.getEntitiesOfClass(AmbientFighterEntity.class, fighter.getBoundingBox().inflate(Math.max(4.0D, radius)),
                        other -> other != fighter && other.isAlive() && !other.isDefeated() && !other.isCaptive() && bond(fighter, other) >= 6)
                .stream().max(java.util.Comparator.comparingInt(other -> bond(fighter, other))).orElse(null);
    }

    /** Human-readable live activity for the inspection panel. */
    public static String currentActivityLabel(AmbientFighterEntity fighter) {
        if (fighter == null) return "";
        Conversation conversation = CONVERSATIONS.get(fighter.getUUID());
        if (conversation == null) return "";
        UUID otherId = fighter.getUUID().equals(conversation.a) ? conversation.b : conversation.a;
        String prefix = conversation.walking ? "Walking" : conversation.meeting ? "Meeting up" : conversation.hangout ? "Hanging out" : "Talking";
        if (fighter.level() instanceof ServerLevel level
                && level.getEntity(otherId) instanceof AmbientFighterEntity other)
            return prefix + " with " + other.getFighterName();
        if (conversation.walking) return "Walking with someone";
        if (conversation.meeting) return "Meeting up with someone";
        return conversation.hangout ? "Hanging out" : "Socializing";
    }

    public static List<String> profileConnections(AmbientFighterEntity fighter) {
        if (fighter == null) return List.of();
        CompoundTag bonds = bondRoot(fighter), names = nameRoot(fighter);
        List<String> keys = new ArrayList<>(bonds.getAllKeys());
        keys.removeIf(key -> bonds.getInt(key) < 4 || names.getString(key).isBlank());
        keys.sort((a, b) -> Integer.compare(bonds.getInt(b), bonds.getInt(a)));
        List<String> out = new ArrayList<>();
        for (String key : keys) {
            int value = bonds.getInt(key); String label = value >= 9 ? "Close bond" : value >= 6 ? "Friend" : "Familiar";
            out.add(label + ": " + names.getString(key)); if (out.size() >= 3) break;
        }
        return List.copyOf(out);
    }

    private static boolean hasRaceKinshipSeen(AmbientFighterEntity a, AmbientFighterEntity b) {
        if (a == null || b == null) return false;
        return raceKinshipRoot(a).getBoolean(socialId(b).toString());
    }

    /** Marks this specific same-race recognition once so it can create a small lasting bond without farming. */
    private static boolean markRaceKinship(AmbientFighterEntity a, AmbientFighterEntity b) {
        if (a == null || b == null || a.getRace() != b.getRace()) return false;
        String aKey = socialId(b).toString();
        String bKey = socialId(a).toString();
        CompoundTag ar = raceKinshipRoot(a), br = raceKinshipRoot(b);
        boolean first = !ar.getBoolean(aKey) || !br.getBoolean(bKey);
        ar.putBoolean(aKey, true); br.putBoolean(bKey, true);
        a.getLegacyData().put(RACE_KINSHIP_SEEN, ar);
        b.getLegacyData().put(RACE_KINSHIP_SEEN, br);
        return first;
    }

    private static CompoundTag raceKinshipRoot(AmbientFighterEntity fighter) {
        CompoundTag legacy = fighter.getLegacyData();
        if (!legacy.contains(RACE_KINSHIP_SEEN, Tag.TAG_COMPOUND)) legacy.put(RACE_KINSHIP_SEEN, new CompoundTag());
        return legacy.getCompound(RACE_KINSHIP_SEEN);
    }

    private static CompoundTag bondRoot(AmbientFighterEntity fighter) {
        CompoundTag legacy = fighter.getLegacyData();
        if (!legacy.contains(BONDS, Tag.TAG_COMPOUND)) legacy.put(BONDS, new CompoundTag());
        return legacy.getCompound(BONDS);
    }
    private static CompoundTag nameRoot(AmbientFighterEntity fighter) {
        CompoundTag legacy = fighter.getLegacyData();
        if (!legacy.contains(NAMES, Tag.TAG_COMPOUND)) legacy.put(NAMES, new CompoundTag());
        return legacy.getCompound(NAMES);
    }

    public static void clearRuntime() { CONVERSATIONS.clear(); }
    public static int runtimeEntries() { return new HashSet<>(CONVERSATIONS.values()).size(); }
}
