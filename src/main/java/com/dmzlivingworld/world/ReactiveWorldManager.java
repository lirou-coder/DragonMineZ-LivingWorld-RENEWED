package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterPersonality;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Small persistent social state used by the Reactive World layer.
 * Temperament is stable; mood and recent social impressions change with circumstances.
 */
public final class ReactiveWorldManager {
    public static final String ROOT = "LWReactiveWorld";
    private static final String TEMPERAMENT = "Temperament";
    private static final String MOOD = "Mood";
    private static final String MOOD_CAUSE = "MoodCause";
    private static final String MOOD_UNTIL = "MoodUntil";
    private static final String IMPRESSIONS = "Impressions";
    private static final String LAST_TOPIC = "LastTopic";
    private static final String LAST_TOPIC_AT = "LastTopicAt";
    private static final String LAST_EVENT_TYPE = "LastEventType";
    private static final String LAST_EVENT_SUBJECT = "LastEventSubject";
    private static final String LAST_EVENT_DETAIL = "LastEventDetail";
    private static final String LAST_EVENT_AT = "LastEventAt";
    private static final String MOOD_STRENGTH = "MoodStrength";
    private static final String MOOD_STARTED_AT = "MoodStartedAt";
    private static final String MOOD_LAST_DECAY = "MoodLastDecay";
    private static final String DEBUG_CYCLE_ACTIVE = "LWDebugMoodCycleActive";
    private static final String DEBUG_CYCLE_INDEX = "LWDebugMoodCycleIndex";
    private static final String DEBUG_CYCLE_NEXT = "LWDebugMoodCycleNext";
    private static final int DEBUG_CYCLE_STEP_TICKS = 115; // 7 moods ~= 40.25 seconds
    private static final int MAX_IMPRESSIONS = 16;

    public enum Temperament {
        SUPPORTIVE("Supportive"), WARM("Warm"), TEASING("Teasing"), BLUNT("Blunt"),
        ALOOF("Aloof"), BULLY("Bully");
        private final String label;
        Temperament(String label) { this.label = label; }
        public String label() { return label; }
        static Temperament byName(String raw) {
            try { return valueOf(raw == null ? "" : raw.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { return WARM; }
        }
    }

    public enum Mood {
        UPBEAT("Upbeat", "😊"), CONTENT("Content", "🙂"), FOCUSED("Focused", "🎯"), WARY("Wary", "👀"),
        IRRITATED("Irritated", "😠"), SOMBER("Somber", "😔"), WEARY("Weary", "😴");
        private final String label;
        private final String icon;
        Mood(String label, String icon) { this.label = label; this.icon = icon; }
        public String label() { return label; }
        public String icon() { return icon; }
        public String displayLabel() { return icon + " " + label; }
        static Mood byName(String raw) {
            try { return valueOf(raw == null ? "" : raw.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { return CONTENT; }
        }
    }

    private ReactiveWorldManager() {}

    private static CompoundTag root(AmbientFighterEntity fighter) {
        CompoundTag persistent = fighter.getPersistentData();
        CompoundTag root = persistent.contains(ROOT, Tag.TAG_COMPOUND) ? persistent.getCompound(ROOT) : new CompoundTag();
        if (!root.contains(TEMPERAMENT, Tag.TAG_STRING)) root.putString(TEMPERAMENT, rollTemperament(fighter).name());
        if (!root.contains(MOOD, Tag.TAG_STRING)) {
            root.putString(MOOD, Mood.CONTENT.name());
            root.putString(MOOD_CAUSE, "a quiet stretch");
            root.putInt(MOOD_STRENGTH, 20);
        }
        persistent.put(ROOT, root);
        return root;
    }

    public static void restore(AmbientFighterEntity fighter, CompoundTag profile) {
        if (fighter == null || profile == null || !profile.contains(ROOT, Tag.TAG_COMPOUND)) return;
        fighter.getPersistentData().put(ROOT, profile.getCompound(ROOT).copy());
        Mood restored = mood(fighter);
        fighter.setReactiveMoodVisual(restored.ordinal(), moodStrength(fighter));
    }

    public static void writeProfile(AmbientFighterEntity fighter, CompoundTag profile) {
        if (fighter == null || profile == null) return;
        profile.put(ROOT, root(fighter).copy());
    }

    public static Temperament temperament(AmbientFighterEntity fighter) {
        return Temperament.byName(root(fighter).getString(TEMPERAMENT));
    }

    public static Mood mood(AmbientFighterEntity fighter) {
        return Mood.byName(root(fighter).getString(MOOD));
    }

    public static String moodCause(AmbientFighterEntity fighter) {
        String cause = root(fighter).getString(MOOD_CAUSE);
        return cause.isBlank() ? "recent events" : cause;
    }

    public static String profileSummary(AmbientFighterEntity fighter) {
        if (WorldMenaceManager.isHerobrine(fighter)) return WorldMenaceManager.moodSummary(fighter);
        return mood(fighter).displayLabel() + " — " + moodCause(fighter);
    }

    public static int moodStrength(AmbientFighterEntity fighter) {
        return Math.max(0, Math.min(100, root(fighter).getInt(MOOD_STRENGTH)));
    }


    /** How strongly this mood seeks casual NPC/player conversation. 1 = baseline. */
    public static float socialDrive(AmbientFighterEntity fighter) {
        if (WorldMenaceManager.isHerobrine(fighter)) return 0.03F;
        float target = switch (mood(fighter)) {
            case UPBEAT -> 1.65F;
            case CONTENT -> 1.00F;
            case FOCUSED -> 0.48F;
            case WARY -> 0.55F;
            case IRRITATED -> 0.20F;
            case SOMBER -> 0.16F;
            case WEARY -> 0.28F;
        };
        float blend = moodStrength(fighter) / 100.0F;
        return 1.0F + (target - 1.0F) * blend;
    }

    /** Ground movement pace outside combat; deliberately subtle except for strong weary/somber moods. */
    public static double movementPace(AmbientFighterEntity fighter) {
        if (WorldMenaceManager.isHerobrine(fighter)) return 0.96D;
        double target = switch (mood(fighter)) {
            case UPBEAT -> 1.08D;
            case CONTENT -> 1.00D;
            case FOCUSED -> 1.10D;
            case WARY -> 1.06D;
            case IRRITATED -> 1.15D;
            case SOMBER -> 0.82D;
            case WEARY -> 0.60D;
        };
        double blend = moodStrength(fighter) / 100.0D;
        return 1.0D + (target - 1.0D) * blend;
    }

    /**
     * Mood can influence whether a fighter *accepts* a useful flight route, but it is never the
     * reason for flying by itself. RC4 let irritated/wary moods multiply flight too aggressively,
     * which made emotion read as "pick a direction and keep flying" instead of believable behavior.
     */
    public static float flightDrive(AmbientFighterEntity fighter) {
        if (WorldMenaceManager.isHerobrine(fighter)) return 0.15F;
        float target = switch (mood(fighter)) {
            case UPBEAT -> 1.10F;
            case CONTENT -> 1.00F;
            case FOCUSED -> 1.05F;
            case WARY -> 1.10F;
            case IRRITATED -> 0.82F;
            case SOMBER -> 0.48F;
            case WEARY -> 0.28F;
        };
        float blend = moodStrength(fighter) / 100.0F;
        return 1.0F + (target - 1.0F) * blend;
    }

    /** Leisure flight is an activity, not an emotion. Strong low moods usually avoid it. */
    public static boolean allowsLeisureFlight(AmbientFighterEntity fighter) {
        if (fighter == null || !fighter.hasFlightUnlocked() || WorldMenaceManager.isHerobrine(fighter)) return false;
        if (moodStrength(fighter) < 58) return true;
        return switch (mood(fighter)) {
            case UPBEAT, CONTENT -> true;
            case FOCUSED, WARY -> moodStrength(fighter) < 82;
            case IRRITATED, SOMBER, WEARY -> false;
        };
    }

    /** Extra idle pause between wander decisions. Values >1 make the fighter linger longer. */
    public static float idlePauseMultiplier(AmbientFighterEntity fighter) {
        float target = switch (mood(fighter)) {
            case UPBEAT -> 0.82F;
            case CONTENT -> 1.00F;
            case FOCUSED -> 0.86F;
            case WARY -> 0.78F;
            case IRRITATED -> 0.72F;
            case SOMBER -> 1.45F;
            case WEARY -> 1.75F;
        };
        float blend = moodStrength(fighter) / 100.0F;
        return 1.0F + (target - 1.0F) * blend;
    }

    /**
     * Strong moods change who a fighter is willing to casually approach. Close bonds can break
     * through low/social moods, while wary fighters avoid people who have treated them badly.
     */
    public static boolean allowsCasualSocial(AmbientFighterEntity fighter, AmbientFighterEntity other, int bond) {
        if (fighter == null || other == null) return false;
        if (WorldMenaceManager.isHerobrine(fighter) || WorldMenaceManager.isHerobrine(other)) return false;
        if (moodStrength(fighter) < 55) return true;
        return switch (mood(fighter)) {
            case UPBEAT, CONTENT -> true;
            case FOCUSED -> bond >= 4;
            case WARY -> bond >= 4 || (impression(fighter, other) >= 0 && temperament(other) != Temperament.BULLY);
            case IRRITATED -> bond >= 8 || impression(fighter, other) >= 4;
            case SOMBER -> bond >= 6 || temperament(other) == Temperament.SUPPORTIVE || temperament(other) == Temperament.WARM;
            case WEARY -> bond >= 4 || temperament(other) == Temperament.SUPPORTIVE || temperament(other) == Temperament.WARM;
        };
    }

    /** Mood-aware activity duration multiplier. */
    public static float activityDurationMultiplier(AmbientFighterEntity fighter, FighterAmbientActivityManager.Type type) {
        Mood m = mood(fighter);
        return switch (type) {
            case REST -> m == Mood.WEARY ? 1.65F : m == Mood.SOMBER ? 1.35F : m == Mood.IRRITATED ? 0.70F : 1.0F;
            case DANCING -> m == Mood.UPBEAT ? 1.45F : m == Mood.CONTENT ? 1.10F : 0.75F;
            case SCOUTING -> (m == Mood.WARY || m == Mood.FOCUSED || m == Mood.IRRITATED) ? 1.35F : 1.0F;
            case RELAXED_FLIGHT -> m == Mood.UPBEAT ? 1.15F : (m == Mood.SOMBER || m == Mood.WEARY || m == Mood.IRRITATED) ? 0.65F : 1.0F;
            case STARGAZING -> m == Mood.SOMBER ? 1.35F : m == Mood.CONTENT ? 1.15F : 1.0F;
            default -> 1.0F;
        };
    }

    /** Completing an activity feeds back into mood instead of mood being a one-way cosmetic label. */
    public static void activityCompleted(AmbientFighterEntity fighter, FighterAmbientActivityManager.Type type) {
        if (fighter == null || fighter.level().isClientSide || fighter.getTarget() != null) return;
        switch (type) {
            case REST, NAP -> {
                if (mood(fighter) == Mood.WEARY || mood(fighter) == Mood.SOMBER || mood(fighter) == Mood.IRRITATED)
                    react(fighter, Mood.CONTENT, "taking time to recover", 700);
            }
            case TRAINING, STRENGTH_TRAINING, KI_TRAINING -> {
                if (mood(fighter) != Mood.WEARY && mood(fighter) != Mood.SOMBER)
                    react(fighter, Mood.FOCUSED, "putting in focused training", 560);
            }
            case DANCING -> react(fighter, Mood.UPBEAT, "letting loose for a while", 850);
            case SCOUTING -> react(fighter, Mood.FOCUSED, "checking the area", 650);
            case RELAXED_FLIGHT -> {
                if (mood(fighter) != Mood.IRRITATED) react(fighter, Mood.CONTENT, "clearing their head in the air", 650);
            }
            case STARGAZING -> react(fighter, Mood.CONTENT, "a quiet look at the sky", 750);
            case WALKING -> {
                if (mood(fighter) == Mood.WEARY || mood(fighter) == Mood.IRRITATED || mood(fighter) == Mood.SOMBER)
                    react(fighter, Mood.CONTENT, "taking an unhurried walk", 620);
            }
            case STUDYING -> {
                if (mood(fighter) != Mood.WEARY && mood(fighter) != Mood.SOMBER)
                    react(fighter, Mood.FOCUSED, "reviewing what they have learned", 620);
            }
            case FISHING, EATING -> {
                if (mood(fighter) == Mood.WEARY || mood(fighter) == Mood.IRRITATED) react(fighter, Mood.CONTENT, "taking a proper break", 600);
            }
        }
    }

    public static void rememberEvent(AmbientFighterEntity fighter, String type, String subject, String detail) {
        if (fighter == null || fighter.level().isClientSide) return;
        CompoundTag root = root(fighter);
        root.putString(LAST_EVENT_TYPE, type == null ? "" : truncate(type, 32));
        root.putString(LAST_EVENT_SUBJECT, subject == null ? "" : truncate(subject, 64));
        root.putString(LAST_EVENT_DETAIL, detail == null ? "" : truncate(detail, 120));
        root.putLong(LAST_EVENT_AT, fighter.level().getGameTime());
        fighter.getPersistentData().put(ROOT, root);
        FighterIntentManager.noteWorldEvent(fighter, type, subject, detail);
    }

    public static String recentEventType(AmbientFighterEntity fighter, long maxAge) {
        CompoundTag root = root(fighter);
        return fighter.level().getGameTime() - root.getLong(LAST_EVENT_AT) <= maxAge ? root.getString(LAST_EVENT_TYPE) : "";
    }

    public static String recentEventSubject(AmbientFighterEntity fighter, long maxAge) {
        return recentEventType(fighter, maxAge).isBlank() ? "" : root(fighter).getString(LAST_EVENT_SUBJECT);
    }

    public static String recentEventDetail(AmbientFighterEntity fighter, long maxAge) {
        return recentEventType(fighter, maxAge).isBlank() ? "" : root(fighter).getString(LAST_EVENT_DETAIL);
    }

    public static boolean debugSetMood(AmbientFighterEntity fighter, String raw) {
        if (fighter == null || raw == null) return false;
        Mood selected;
        try { selected = Mood.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return false; }
        if (WorldMenaceManager.isHerobrine(fighter)) return false;
        fighter.getPersistentData().remove(DEBUG_CYCLE_ACTIVE);
        fighter.getPersistentData().remove(DEBUG_CYCLE_INDEX);
        fighter.getPersistentData().remove(DEBUG_CYCLE_NEXT);
        FighterAmbientActivityManager.cancel(fighter);
        ReactiveMoodBehaviorManager.reset(fighter);
        setMood(fighter, selected, "debug mood test", 2400, 100);
        if (fighter.getSpeech().isEmpty()) fighter.speak(debugMoodLine(selected), 90);
        // Do not fake the mood by forcing one canned activity. Let the mood's own micro-behaviour,
        // social gates and activity filter demonstrate themselves during the debug window.
        fighter.getPersistentData().putLong("LWNextIdleWander", fighter.level().getGameTime() + 12L);
        FighterAmbientActivityManager.nudgeSoon(fighter);
        return true;
    }

    public static boolean startDebugMoodCycle(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || WorldMenaceManager.isHerobrine(fighter)) return false;
        FighterAmbientActivityManager.cancel(fighter);
        ReactiveMoodBehaviorManager.reset(fighter);
        CompoundTag data = fighter.getPersistentData();
        data.putBoolean(DEBUG_CYCLE_ACTIVE, true);
        data.putInt(DEBUG_CYCLE_INDEX, 0);
        data.putLong(DEBUG_CYCLE_NEXT, fighter.level().getGameTime());
        return true;
    }

    private static boolean tickDebugMoodCycle(AmbientFighterEntity fighter, long now) {
        CompoundTag data = fighter.getPersistentData();
        if (!data.getBoolean(DEBUG_CYCLE_ACTIVE)) return false;
        if (now < data.getLong(DEBUG_CYCLE_NEXT)) return true;
        int index = data.getInt(DEBUG_CYCLE_INDEX);
        Mood[] moods = Mood.values();
        if (index >= moods.length) {
            data.remove(DEBUG_CYCLE_ACTIVE);
            data.remove(DEBUG_CYCLE_INDEX);
            data.remove(DEBUG_CYCLE_NEXT);
            setMood(fighter, Mood.CONTENT, "finishing the debug mood cycle", 300, 35);
            if (fighter.getSpeech().isEmpty()) fighter.speak("Mood cycle complete.", 55);
            return true;
        }
        Mood next = moods[index];
        FighterAmbientActivityManager.cancel(fighter);
        ReactiveMoodBehaviorManager.reset(fighter);
        setMood(fighter, next, "debug mood cycle", DEBUG_CYCLE_STEP_TICKS + 30, 100);
        fighter.speak(debugMoodLine(next), 72);
        fighter.getPersistentData().putLong("LWNextIdleWander", now + 8L);
        FighterAmbientActivityManager.nudgeSoon(fighter);
        data.putInt(DEBUG_CYCLE_INDEX, index + 1);
        data.putLong(DEBUG_CYCLE_NEXT, now + DEBUG_CYCLE_STEP_TICKS);
        return true;
    }

    private static String debugMoodLine(Mood mood) {
        return switch (mood) {
            case UPBEAT -> "I'm in a good mood. Let's make the most of it.";
            case CONTENT -> "I'm fine right here. Nothing pressing for once.";
            case FOCUSED -> "I'm focused. Don't distract me unless it matters.";
            case WARY -> "Something feels off. I'm watching everything around me.";
            case IRRITATED -> "I'm irritated. Give me some space.";
            case SOMBER -> "I don't really want company right now.";
            case WEARY -> "I'm worn out. I need to slow down and recover.";
        };
    }

    public static void tick(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || !(fighter.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (tickDebugMoodCycle(fighter, now)) return;
        if (WorldMenaceManager.isHerobrine(fighter)) {
            // Keep a synced visual channel for the renderer, but his displayed "mood" comes from
            // the separate World Menace presence state rather than the ordinary seven emotions.
            fighter.setReactiveMoodVisual(Mood.WARY.ordinal(), 100);
            return;
        }
        CompoundTag root = root(fighter);
        if (fighter.tickCount % 100 != Math.floorMod(fighter.getUUID().hashCode(), 100)) return;

        Mood desired;
        String cause;
        if (fighter.getTarget() != null) {
            desired = fighter.getHealth() < fighter.getMaxHealth() * 0.35F ? Mood.WARY : Mood.FOCUSED;
            cause = "the fight in front of them";
        } else if (fighter.getHealth() < fighter.getMaxHealth() * 0.30F) {
            desired = Mood.WEARY; cause = "their injuries";
        } else if (root.getLong(MOOD_UNTIL) > now) {
            decayActiveMood(fighter, root, now);
            return; // a recent social/world event still has priority, but its intensity naturally fades
        } else if (level.isThundering()) {
            desired = temperament(fighter) == Temperament.ALOOF ? Mood.CONTENT : Mood.WARY;
            cause = "the storm";
        } else if (fighter.isFactionMember()) {
            WorldFaction faction = FactionManager.byId(level, fighter.getFactionId());
            if (faction != null && FactionWorldData.get(level).supplies(faction) < 24) {
                desired = Mood.WARY; cause = "their faction running short on supplies";
            } else {
                desired = baselineMood(fighter); cause = "a quiet stretch";
            }
        } else {
            desired = baselineMood(fighter); cause = "a quiet stretch";
        }
        int strength = switch (desired) {
            case IRRITATED, SOMBER, WEARY -> 62;
            case WARY, FOCUSED -> 54;
            case UPBEAT -> 40;
            case CONTENT -> 24;
        };
        setMood(fighter, desired, cause, 0, strength);
    }

    public static void react(AmbientFighterEntity fighter, Mood mood, String cause, int durationTicks) {
        if (fighter == null || fighter.level().isClientSide || WorldMenaceManager.isHerobrine(fighter)) return;
        int strength = switch (mood) {
            case IRRITATED, SOMBER -> 82;
            case WEARY -> 74;
            case WARY, FOCUSED -> 68;
            case UPBEAT -> 58;
            case CONTENT -> 30;
        };
        setMood(fighter, mood, cause, Math.max(0, durationTicks), strength);
    }

    public static void reactStrong(AmbientFighterEntity fighter, Mood mood, String cause, int durationTicks) {
        if (fighter == null || fighter.level().isClientSide || WorldMenaceManager.isHerobrine(fighter)) return;
        setMood(fighter, mood, cause, Math.max(0, durationTicks), 100);
    }

    public static void supportiveExchange(AmbientFighterEntity speaker, AmbientFighterEntity listener) {
        if (speaker == null || listener == null) return;
        react(listener, Mood.UPBEAT, speaker.getFighterName() + " being supportive", 900);
        adjustImpression(listener, speaker, 1, "support");
    }

    public static void hostileExchange(AmbientFighterEntity speaker, AmbientFighterEntity listener) {
        if (speaker == null || listener == null) return;
        Mood reaction = listener.getPersonality() == FighterPersonality.PROUD || listener.getPersonality() == FighterPersonality.AGGRESSIVE
                ? Mood.IRRITATED : Mood.SOMBER;
        react(listener, reaction, speaker.getFighterName() + " getting under their skin", 1100);
        adjustImpression(listener, speaker, -1, "insult");
    }

    public static int impression(AmbientFighterEntity owner, AmbientFighterEntity other) {
        if (owner == null || other == null) return 0;
        CompoundTag impressions = impressionRoot(root(owner));
        return Math.max(-8, Math.min(8, impressions.getInt(other.getUUID().toString())));
    }

    public static boolean topicRecentlyUsed(AmbientFighterEntity fighter, String topic, long cooldown) {
        CompoundTag root = root(fighter);
        if (!topic.equals(root.getString(LAST_TOPIC))) return false;
        return fighter.level().getGameTime() - root.getLong(LAST_TOPIC_AT) < cooldown;
    }

    public static void rememberTopic(AmbientFighterEntity fighter, String topic) {
        if (fighter == null || topic == null) return;
        CompoundTag root = root(fighter);
        root.putString(LAST_TOPIC, topic);
        root.putLong(LAST_TOPIC_AT, fighter.level().getGameTime());
        fighter.getPersistentData().put(ROOT, root);
    }

    /**
     * Mood now acts as a real behavioral filter, not just a small weight multiplier. Strong moods
     * remove activities that would contradict the state, while personality/hobbies still provide
     * variation inside the believable choices that remain.
     */
    public static void addActivityPreferences(AmbientFighterEntity fighter, List<FighterAmbientActivityManager.Type> bag,
                                              boolean freeHand, boolean canFish, boolean canStargaze, boolean canFly) {
        if (fighter == null || bag == null) return;
        if (WorldMenaceManager.isHerobrine(fighter)) { bag.clear(); return; }
        Mood current = mood(fighter);
        int strength = moodStrength(fighter);
        boolean strong = strength >= 60;

        if (strong) {
            switch (current) {
                case WEARY -> bag.removeIf(type -> type == FighterAmbientActivityManager.Type.DANCING
                        || type == FighterAmbientActivityManager.Type.SCOUTING
                        || type == FighterAmbientActivityManager.Type.RELAXED_FLIGHT);
                case SOMBER -> bag.removeIf(type -> type == FighterAmbientActivityManager.Type.DANCING
                        || type == FighterAmbientActivityManager.Type.SCOUTING
                        || type == FighterAmbientActivityManager.Type.RELAXED_FLIGHT);
                case WARY -> bag.removeIf(type -> type == FighterAmbientActivityManager.Type.DANCING
                        || type == FighterAmbientActivityManager.Type.STARGAZING);
                case IRRITATED -> bag.removeIf(type -> type == FighterAmbientActivityManager.Type.DANCING
                        || type == FighterAmbientActivityManager.Type.STARGAZING
                        || type == FighterAmbientActivityManager.Type.RELAXED_FLIGHT
                        || type == FighterAmbientActivityManager.Type.FISHING);
                case FOCUSED -> bag.removeIf(type -> type == FighterAmbientActivityManager.Type.DANCING
                        || type == FighterAmbientActivityManager.Type.STARGAZING);
                case UPBEAT, CONTENT -> { }
            }
        }

        switch (current) {
            case WEARY -> {
                addMany(bag, FighterAmbientActivityManager.Type.REST, 8);
                if (freeHand && bag.contains(FighterAmbientActivityManager.Type.EATING)) addMany(bag, FighterAmbientActivityManager.Type.EATING, 4);
                if (canStargaze) bag.add(FighterAmbientActivityManager.Type.STARGAZING);
            }
            case SOMBER -> {
                addMany(bag, FighterAmbientActivityManager.Type.REST, 6);
                if (canStargaze) addMany(bag, FighterAmbientActivityManager.Type.STARGAZING, 5);
                if (canFish) addMany(bag, FighterAmbientActivityManager.Type.FISHING, 2);
            }
            case WARY -> {
                if (freeHand && bag.contains(FighterAmbientActivityManager.Type.SCOUTING)) addMany(bag, FighterAmbientActivityManager.Type.SCOUTING, 8);
                addMany(bag, FighterAmbientActivityManager.Type.REST, 2);
            }
            case IRRITATED -> {
                if (freeHand && bag.contains(FighterAmbientActivityManager.Type.SCOUTING)) addMany(bag, FighterAmbientActivityManager.Type.SCOUTING, 7);
                addMany(bag, FighterAmbientActivityManager.Type.REST, 3);
            }
            case FOCUSED -> {
                if (freeHand && bag.contains(FighterAmbientActivityManager.Type.SCOUTING)) addMany(bag, FighterAmbientActivityManager.Type.SCOUTING, 8);
                if (freeHand && bag.contains(FighterAmbientActivityManager.Type.EATING)) bag.add(FighterAmbientActivityManager.Type.EATING);
            }
            case UPBEAT -> {
                if (bag.contains(FighterAmbientActivityManager.Type.DANCING)) addMany(bag, FighterAmbientActivityManager.Type.DANCING, 8);
                if (freeHand && bag.contains(FighterAmbientActivityManager.Type.EATING)) addMany(bag, FighterAmbientActivityManager.Type.EATING, 3);
                if (canFly && allowsLeisureFlight(fighter)) bag.add(FighterAmbientActivityManager.Type.RELAXED_FLIGHT);
            }
            case CONTENT -> {
                addMany(bag, FighterAmbientActivityManager.Type.REST, 3);
                if (canFish) addMany(bag, FighterAmbientActivityManager.Type.FISHING, 2);
                if (canStargaze) bag.add(FighterAmbientActivityManager.Type.STARGAZING);
            }
        }
        if (temperament(fighter) == Temperament.TEASING && current == Mood.UPBEAT && bag.contains(FighterAmbientActivityManager.Type.DANCING)) addMany(bag, FighterAmbientActivityManager.Type.DANCING, 2);
        switch (fighter.getPersonality()) {
            case CALM -> { addMany(bag, FighterAmbientActivityManager.Type.REST, 2); if (canFish && current != Mood.IRRITATED) addMany(bag, FighterAmbientActivityManager.Type.FISHING, 2); }
            case PROUD, AGGRESSIVE -> { if (canFly && allowsLeisureFlight(fighter) && !strong) bag.add(FighterAmbientActivityManager.Type.RELAXED_FLIGHT); }
            case CAUTIOUS -> { if (freeHand && bag.contains(FighterAmbientActivityManager.Type.SCOUTING)) addMany(bag, FighterAmbientActivityManager.Type.SCOUTING, 2); }
            case HEROIC -> { if (current == Mood.UPBEAT && bag.contains(FighterAmbientActivityManager.Type.DANCING)) bag.add(FighterAmbientActivityManager.Type.DANCING); }
            default -> { }
        }
    }


    /**
     * A player choosing Talk should interact with the fighter's emotional state, not bypass it.
     * Familiar company can soften a strong low mood without magically curing injuries or grief.
     */
    public static void onPlayerTalk(AmbientFighterEntity fighter, int relationship) {
        if (fighter == null || fighter.level().isClientSide || WorldMenaceManager.isHerobrine(fighter) || moodStrength(fighter) < 55) return;
        Mood current = mood(fighter);
        int strength = moodStrength(fighter);
        if (relationship >= 60) {
            switch (current) {
                case IRRITATED -> setMood(fighter, Mood.IRRITATED, "having a familiar person hear them out", 650, Math.max(42, strength - 24));
                case SOMBER -> setMood(fighter, Mood.SOMBER, "having company they trust", 750, Math.max(40, strength - 22));
                case WARY -> setMood(fighter, Mood.WARY, "being reassured by someone familiar", 600, Math.max(38, strength - 18));
                case WEARY -> setMood(fighter, Mood.WEARY, moodCause(fighter), 650, Math.max(45, strength - 12));
                case FOCUSED, UPBEAT, CONTENT -> { }
            }
        } else if (relationship < 15 && current == Mood.IRRITATED) {
            setMood(fighter, Mood.IRRITATED, moodCause(fighter), 700, Math.min(100, strength + 6));
        }
    }

    private static void decayActiveMood(AmbientFighterEntity fighter, CompoundTag root, long now) {
        long last = root.getLong(MOOD_LAST_DECAY);
        if (last <= 0L) { root.putLong(MOOD_LAST_DECAY, now); fighter.getPersistentData().put(ROOT, root); return; }
        long intervals = (now - last) / 100L;
        if (intervals <= 0L) return;
        Mood current = Mood.byName(root.getString(MOOD));
        int floor = switch (current) {
            case UPBEAT, CONTENT -> 30;
            case FOCUSED, WARY -> 38;
            case IRRITATED, SOMBER -> 42;
            case WEARY -> 48;
        };
        int amount = (int)Math.min(12L, intervals * (current == Mood.IRRITATED || current == Mood.SOMBER ? 2L : 1L));
        int next = Math.max(floor, root.getInt(MOOD_STRENGTH) - amount);
        root.putInt(MOOD_STRENGTH, next);
        root.putLong(MOOD_LAST_DECAY, last + intervals * 100L);
        fighter.getPersistentData().put(ROOT, root);
        fighter.setReactiveMoodVisual(current.ordinal(), next);
    }

    private static void addMany(List<FighterAmbientActivityManager.Type> bag, FighterAmbientActivityManager.Type type, int count) {
        for (int i = 0; i < count; i++) bag.add(type);
    }

    private static Mood baselineMood(AmbientFighterEntity fighter) {
        return switch (temperament(fighter)) {
            case SUPPORTIVE, WARM, TEASING -> Mood.UPBEAT;
            case BLUNT, ALOOF, BULLY -> Mood.CONTENT;
        };
    }

    private static void setMood(AmbientFighterEntity fighter, Mood mood, String cause, int durationTicks) {
        setMood(fighter, mood, cause, durationTicks, 50);
    }

    private static void setMood(AmbientFighterEntity fighter, Mood mood, String cause, int durationTicks, int strength) {
        CompoundTag root = root(fighter);
        Mood previous = Mood.byName(root.getString(MOOD));
        int previousStrength = root.getInt(MOOD_STRENGTH);
        int clampedStrength = Math.max(0, Math.min(100, strength));
        root.putString(MOOD, mood.name());
        root.putString(MOOD_CAUSE, cause == null || cause.isBlank() ? "recent events" : truncate(cause, 90));
        long now = fighter.level().getGameTime();
        root.putLong(MOOD_UNTIL, durationTicks <= 0 ? 0L : now + durationTicks);
        root.putInt(MOOD_STRENGTH, clampedStrength);
        if (previous != mood || clampedStrength > previousStrength) root.putLong(MOOD_STARTED_AT, now);
        root.putLong(MOOD_LAST_DECAY, now);
        fighter.getPersistentData().put(ROOT, root);
        fighter.setReactiveMoodVisual(mood.ordinal(), clampedStrength);
        if (previous != mood && clampedStrength >= 65 && fighter.getTarget() == null) {
            FighterAmbientActivityManager.nudgeSoon(fighter);
        }
        if (previous != mood && clampedStrength >= 65 && previousStrength < clampedStrength
                && !FactionRequestMissionManager.isExclusiveFieldAssignment(fighter)
                && fighter.getTarget() == null && fighter.getSpeech().isEmpty() && fighter.getRandom().nextFloat() < 0.18F) {
            String line = switch (mood) {
                case UPBEAT -> "I'm feeling good. Might as well enjoy it.";
                case CONTENT -> "That's better. I can breathe again.";
                case FOCUSED -> "All right. Focus.";
                case WARY -> "Something feels off. I'm keeping watch.";
                case IRRITATED -> "I need some space before I lose my patience.";
                case SOMBER -> "I'm not really feeling talkative.";
                case WEARY -> "I'm running on fumes. I need to slow down.";
            };
            fighter.speak(line, 78);
        }
    }

    private static Temperament rollTemperament(AmbientFighterEntity fighter) {
        int roll = Math.floorMod(fighter.getUUID().hashCode() * 31 + fighter.getPersonality().id() * 17 + fighter.getAlignment().id() * 11, 100);
        FighterPersonality p = fighter.getPersonality();
        FighterAlignment a = fighter.getAlignment();
        if (a == FighterAlignment.BAD && roll < 30) return Temperament.BULLY;
        if (p == FighterPersonality.HEROIC && roll < 58) return roll < 30 ? Temperament.SUPPORTIVE : Temperament.WARM;
        if (p == FighterPersonality.CALM && roll < 55) return roll < 28 ? Temperament.WARM : Temperament.ALOOF;
        if (p == FighterPersonality.PROUD) return roll < 35 ? Temperament.BLUNT : roll < 62 ? Temperament.TEASING : Temperament.ALOOF;
        if (p == FighterPersonality.AGGRESSIVE) return roll < 38 ? Temperament.BLUNT : roll < 64 ? Temperament.BULLY : Temperament.TEASING;
        if (p == FighterPersonality.CAUTIOUS && roll < 52) return roll < 28 ? Temperament.SUPPORTIVE : Temperament.ALOOF;
        return Temperament.values()[Math.floorMod(roll, Temperament.values().length)];
    }

    private static CompoundTag impressionRoot(CompoundTag root) {
        if (!root.contains(IMPRESSIONS, Tag.TAG_COMPOUND)) root.put(IMPRESSIONS, new CompoundTag());
        return root.getCompound(IMPRESSIONS);
    }

    private static void adjustImpression(AmbientFighterEntity owner, AmbientFighterEntity other, int delta, String reason) {
        CompoundTag root = root(owner);
        CompoundTag impressions = impressionRoot(root);
        String key = other.getUUID().toString();
        impressions.putInt(key, Math.max(-8, Math.min(8, impressions.getInt(key) + delta)));
        if (impressions.getAllKeys().size() > MAX_IMPRESSIONS) {
            List<String> keys = new ArrayList<>(impressions.getAllKeys());
            keys.remove(key);
            if (!keys.isEmpty()) impressions.remove(keys.get(0));
        }
        root.put(IMPRESSIONS, impressions);
        root.putString("LastSocialCause", truncate(reason + " from " + other.getFighterName(), 90));
        owner.getPersistentData().put(ROOT, root);
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
