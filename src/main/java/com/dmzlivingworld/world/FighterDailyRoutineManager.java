package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dmzlivingworld.entity.FighterArchetype;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;

/**
 * Persistent fine-grained daily intent for ordinary Living World life.
 *
 * The plan is deliberately not a second AI system. It chooses what this person intends to spend
 * each part of the day doing, then hands execution to the existing meditation, practice-spar and
 * ambient-activity systems. Combat, danger, companions, authored scenes and goals can still
 * interrupt it. Plans are stable for the whole Minecraft day and use only small deterministic
 * variation after personality/goals/hobbies/relevance have done the real decision-making.
 */
public final class FighterDailyRoutineManager {
    private static final int SCHEMA = 12;
    private static final int SLOT_TICKS = 1_500;
    private static final String K_SCHEMA = "LWDailyRoutineSchema";
    private static final String K_DAY = "LWDailyRoutineDay";
    private static final String K_PLAN = "LWDailyRoutinePlan";
    private static final String K_REASONS = "LWDailyRoutineReasons";
    private static final String K_INTERLUDES = "LWDailyRoutineInterludes";
    private static final String K_INTERLUDE_REASONS = "LWDailyRoutineInterludeReasons";
    private static final String K_FOCUS = "LWDailyRoutineFocus";
    private static final String K_PREVIOUS = "LWDailyRoutinePreviousPlan";
    private static final String K_LAST_STARTED = "LWDailyRoutineLastStarted";
    private static final String K_LAST_STARTED_DAY = "LWDailyRoutineLastStartedDay";
    private static final String K_LAST_STARTED_SEGMENT = "LWDailyRoutineLastStartedSegment";
    private static final String K_LAST_INTERLUDE_DAY = "LWDailyRoutineLastInterludeDay";
    private static final String K_LAST_INTERLUDE_SEGMENT = "LWDailyRoutineLastInterludeSegment";
    private static final String K_ACTIVITY_JOURNAL = "LWDailyActivityJournal";
    private static final int ACTIVITY_JOURNAL_DAYS = 7;
    private static final int ACTIVITY_JOURNAL_MAX = 72;

    public enum Segment {
        DAWN("Dawn"), EARLY_MORNING("Early morning"), MORNING("Morning"), LATE_MORNING("Late morning"),
        NOON("Noon"), EARLY_AFTERNOON("Early afternoon"), MID_AFTERNOON("Mid afternoon"),
        LATE_AFTERNOON("Late afternoon"), DUSK("Dusk"), EARLY_EVENING("Early evening"),
        EVENING("Evening"), LATE_EVENING("Late evening"), NIGHT("Night"),
        LATE_NIGHT("Late night"), DEEP_NIGHT("Deep night"), PRE_DAWN("Pre-dawn");
        private final String label;
        Segment(String label) { this.label = label; }
        public String label() { return label; }
        public boolean darkEnoughForStars() {
            return ordinal() >= DUSK.ordinal() && ordinal() <= PRE_DAWN.ordinal();
        }
        public boolean isMealAnchor() {
            return this == EARLY_MORNING || this == NOON || this == DUSK;
        }
        public boolean isDeepRestTime() {
            return this == NIGHT || this == LATE_NIGHT || this == DEEP_NIGHT || this == PRE_DAWN;
        }
        public boolean isDaylight() {
            return ordinal() >= DAWN.ordinal() && ordinal() <= LATE_AFTERNOON.ordinal();
        }
    }

    public enum Activity {
        TRAINING("Training"), STRENGTH_TRAINING("Training"), KI_TRAINING("Ki training"),
        MEDITATION("Meditation"), SPARRING("Sparring"), FISHING("Fishing"),
        REST("Rest"), SITTING("Sitting / recovering"), NAP("Taking a nap"), JOGGING("Jogging"), WALKING("Taking a walk"),
        SCOUTING("Scouting"), STUDYING("Studying / reviewing notes"), SCIENTIST_RESEARCH("Improving Saibaman formula"), EATING("Eating"),
        STARGAZING("Stargazing"), FLIGHT("Flying"), DANCING("Dancing"), FLOWER("Gardening / flowers"),
        TREE("Taking an apple break"), FOOD_GATHERING("Gathering food"), SOCIALIZING("Socializing"), HANGING_OUT("Hanging out"),
        WALK_TOGETHER("Walking with someone"), MEETING_UP("Meeting up with someone");

        private final String label;
        Activity(String label) { this.label = label; }
        public String label() { return label; }
        static Activity byName(String value) {
            if (value == null || value.isBlank()) return REST;
            try {
                Activity parsed = Activity.valueOf(value);
                // R19 removes Strength as a live activity. Keep the enum token only so old
                // persisted R18 plans load safely, then migrate them into ordinary Training.
                return parsed == STRENGTH_TRAINING ? TRAINING : parsed;
            } catch (IllegalArgumentException ignored) { return REST; }
        }
    }

    // Local aliases keep the planner scoring code readable while still binding every shorthand
    // to the Activity enum. R8 initially omitted these bindings, which made javac interpret
    // TRAINING/FISHING/etc. as undefined variables outside enum-switch labels.
    private static final Activity TRAINING = Activity.TRAINING;
    private static final Activity STRENGTH_TRAINING = Activity.STRENGTH_TRAINING;
    private static final Activity KI_TRAINING = Activity.KI_TRAINING;
    private static final Activity MEDITATION = Activity.MEDITATION;
    private static final Activity SPARRING = Activity.SPARRING;
    private static final Activity FISHING = Activity.FISHING;
    private static final Activity REST = Activity.REST;
    private static final Activity SITTING = Activity.SITTING;
    private static final Activity NAP = Activity.NAP;
    private static final Activity JOGGING = Activity.JOGGING;
    private static final Activity WALKING = Activity.WALKING;
    private static final Activity SCOUTING = Activity.SCOUTING;
    private static final Activity STUDYING = Activity.STUDYING;
    private static final Activity SCIENTIST_RESEARCH = Activity.SCIENTIST_RESEARCH;
    private static final Activity EATING = Activity.EATING;
    private static final Activity STARGAZING = Activity.STARGAZING;
    private static final Activity FLIGHT = Activity.FLIGHT;
    private static final Activity DANCING = Activity.DANCING;
    private static final Activity FLOWER = Activity.FLOWER;
    private static final Activity TREE = Activity.TREE;
    private static final Activity FOOD_GATHERING = Activity.FOOD_GATHERING;
    private static final Activity SOCIALIZING = Activity.SOCIALIZING;
    private static final Activity HANGING_OUT = Activity.HANGING_OUT;
    private static final Activity WALK_TOGETHER = Activity.WALK_TOGETHER;
    private static final Activity MEETING_UP = Activity.MEETING_UP;

    private FighterDailyRoutineManager() {}

    public static long currentDay(ServerLevel level) {
        if (level == null || level.getServer() == null) return 0L;
        return Math.floorDiv(level.getServer().overworld().getDayTime(), 24_000L);
    }

    public static Segment currentSegment(ServerLevel level) {
        long t = level == null || level.getServer() == null ? 0L
                : Math.floorMod(level.getServer().overworld().getDayTime(), 24_000L);
        int slot = Math.max(0, Math.min(Segment.values().length - 1, (int)(t / SLOT_TICKS)));
        return Segment.values()[slot];
    }

    public static void ensurePlan(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || !(fighter.level() instanceof ServerLevel level)
                || WorldMenaceManager.isHerobrine(fighter)) return;
        CompoundTag data = fighter.getLegacyData();
        long day = currentDay(level);
        if (data.getInt(K_SCHEMA) == SCHEMA && data.getLong(K_DAY) == day
                && data.getList(K_PLAN, Tag.TAG_STRING).size() == Segment.values().length
                && data.getList(K_INTERLUDES, Tag.TAG_STRING).size() == Segment.values().length) return;
        generateLoadedPlan(fighter, level, day, data);
    }

    public static Activity currentActivity(AmbientFighterEntity fighter) {
        if (fighter == null || !(fighter.level() instanceof ServerLevel level)) return Activity.REST;
        ensurePlan(fighter);
        Activity planned = activityAt(fighter.getLegacyData(), currentSegment(level).ordinal());
        return FighterLifeNeedsManager.adaptiveChoice(fighter, planned);
    }

    public static String currentReason(AmbientFighterEntity fighter) {
        if (fighter == null || !(fighter.level() instanceof ServerLevel level)) return "";
        ensurePlan(fighter);
        int slot = currentSegment(level).ordinal();
        Activity planned = activityAt(fighter.getLegacyData(), slot);
        Activity live = FighterLifeNeedsManager.adaptiveChoice(fighter, planned);
        if (live != planned) return "adapting the day to a current need instead of blindly following the plan";
        return reasonAt(fighter.getLegacyData(), slot);
    }

    /** Optional short beat that happens between/around the segment's real primary activity. */
    public static Activity currentInterlude(AmbientFighterEntity fighter) {
        if (fighter == null || !(fighter.level() instanceof ServerLevel level)) return null;
        ensurePlan(fighter);
        return interludeAt(fighter.getLegacyData(), currentSegment(level).ordinal());
    }

    public static boolean mayStartPlannedInterlude(AmbientFighterEntity fighter) {
        if (FactionRequestMissionManager.isAssigned(fighter)) return false;
        if (fighter == null || !(fighter.level() instanceof ServerLevel level)) return false;
        ensurePlan(fighter);
        if (currentInterlude(fighter) == null) return false;
        CompoundTag data = fighter.getLegacyData();
        long day = currentDay(level);
        int segment = currentSegment(level).ordinal();
        return !data.contains(K_LAST_INTERLUDE_DAY, Tag.TAG_ANY_NUMERIC)
                || !data.contains(K_LAST_INTERLUDE_SEGMENT, Tag.TAG_ANY_NUMERIC)
                || data.getLong(K_LAST_INTERLUDE_DAY) != day || data.getInt(K_LAST_INTERLUDE_SEGMENT) != segment;
    }

    /** Consumes the short beat whether it physically started or had to be skipped. */
    public static void notePlannedInterludeAttempted(AmbientFighterEntity fighter) {
        if (fighter == null || !(fighter.level() instanceof ServerLevel level)) return;
        CompoundTag data = fighter.getLegacyData();
        data.putLong(K_LAST_INTERLUDE_DAY, currentDay(level));
        data.putInt(K_LAST_INTERLUDE_SEGMENT, currentSegment(level).ordinal());
    }

    public static boolean wantsMeditation(AmbientFighterEntity fighter) { return currentActivity(fighter) == Activity.MEDITATION; }

    /**
     * Minimum real meditation length implied by consecutive planned meditation blocks. Long
     * meditation runs are deliberately represented in the day plan, so an NPC can reach the
     * deeper duration bands instead of every plan block behaving like an isolated short session.
     */
    public static int plannedMeditationMinimumTicks(AmbientFighterEntity fighter) {
        if (fighter == null || !(fighter.level() instanceof ServerLevel level)) return 0;
        ensurePlan(fighter);
        CompoundTag data = fighter.getLegacyData();
        int start = currentSegment(level).ordinal();
        if (activityAt(data, start) != MEDITATION) return 0;
        int run = 0;
        for (int i = start; i < Segment.values().length && activityAt(data, i) == MEDITATION; i++) run++;
        return Math.max(SLOT_TICKS, run * SLOT_TICKS);
    }
    public static boolean wantsSparring(AmbientFighterEntity fighter) { return currentActivity(fighter) == Activity.SPARRING; }

    /** At most one plan-owned start per segment; ordinary fallback life can still happen afterwards. */
    public static boolean mayStartPlannedActivity(AmbientFighterEntity fighter) {
        if (FactionRequestMissionManager.isAssigned(fighter)) return false;
        if (fighter == null || !(fighter.level() instanceof ServerLevel level)) return false;
        ensurePlan(fighter);
        CompoundTag data = fighter.getLegacyData();
        long day = currentDay(level);
        int segment = currentSegment(level).ordinal();
        return !data.contains(K_LAST_STARTED_DAY, Tag.TAG_ANY_NUMERIC)
                || !data.contains(K_LAST_STARTED_SEGMENT, Tag.TAG_ANY_NUMERIC)
                || data.getLong(K_LAST_STARTED_DAY) != day || data.getInt(K_LAST_STARTED_SEGMENT) != segment;
    }

    public static void notePlannedActivityStarted(AmbientFighterEntity fighter, Activity activity) {
        if (fighter == null || !(fighter.level() instanceof ServerLevel level)) return;
        CompoundTag data = fighter.getLegacyData();
        data.putLong(K_LAST_STARTED_DAY, currentDay(level));
        data.putInt(K_LAST_STARTED_SEGMENT, currentSegment(level).ordinal());
        data.putString(K_LAST_STARTED, activity == null ? "" : activity.name());
        // Claiming a planned slot is not the same as physically beginning it. Ambient activities
        // journal themselves only after they settle/start for real; non-ambient callers explicitly
        // record once their own start API succeeds. This prevents zero-tick "started/finished" history.
    }

    /** Records an activity that actually started, not merely something present in the generated plan. */
    public static void recordActivityStart(AmbientFighterEntity fighter, String label) {
        if (fighter == null || label == null || label.isBlank() || !(fighter.level() instanceof ServerLevel level)) return;
        String displayLabel = canonicalJournalActivity(label);
        CompoundTag data = fighter.getLegacyData();
        long day = currentDay(level);
        int tick = (int)Math.floorMod(level.getServer().overworld().getDayTime(), 24_000L);
        ListTag journal = data.getList(K_ACTIVITY_JOURNAL, Tag.TAG_COMPOUND);
        if (!journal.isEmpty()) {
            CompoundTag last = journal.getCompound(journal.size() - 1);
            if (last.getLong("Day") == day && displayLabel.equals(canonicalJournalActivity(last.getString("Activity")))
                    && Math.abs(tick - last.getInt("Tick")) <= 120) return;
        }
        CompoundTag row = new CompoundTag();
        row.putLong("Day", day); row.putInt("Tick", tick); row.putString("Activity", displayLabel);
        journal.add(row);
        while (!journal.isEmpty() && (journal.size() > ACTIVITY_JOURNAL_MAX
                || journal.getCompound(0).getLong("Day") < day - (ACTIVITY_JOURNAL_DAYS - 1L))) journal.remove(0);
        data.put(K_ACTIVITY_JOURNAL, journal);
    }

    /** Compact nested-history payload. The main Overview intentionally keeps these lines hidden. */
    public static List<String> scheduleHistoryLines(AmbientFighterEntity fighter) {
        List<String> out = new ArrayList<>();
        if (fighter == null || !(fighter.level() instanceof ServerLevel level) || WorldMenaceManager.isWorldMenace(fighter)) return out;
        ensurePlan(fighter);
        long today = currentDay(level);
        String live = FighterAmbientActivityManager.currentActivity(fighter);
        if (fighter.isMeditating() || fighter.isPreparingMeditation()) live = "Meditation";
        if (live.isBlank()) live = currentActivity(fighter).label() + " (planned/current routine)";
        out.add("## Now");
        out.add("+ " + live);

        // The nested Schedule view is the authoritative place for the full generated day plan.
        // Always show every remaining slot, even when the activity journal is sparse. This fixes
        // profiles that appeared to know only their next slot while others showed further plans.
        CompoundTag data = fighter.getLegacyData();
        Segment current = currentSegment(level);
        out.add("## Today's plan");
        Segment[] segments = Segment.values();
        for (int start = 0; start < segments.length; ) {
            int end = start;
            String planText = planTextAt(data, start);
            while (end + 1 < segments.length && planText.equals(planTextAt(data, end + 1))) end++;
            boolean containsCurrent = current.ordinal() >= start && current.ordinal() <= end;
            String marker = containsCurrent ? "→ " : end < current.ordinal() ? "· " : "• ";
            String period = start == end ? segments[start].label() : segments[start].label() + "–" + segments[end].label();
            out.add(marker + period + " — " + planText);
            start = end + 1;
        }

        ListTag journal = data.getList(K_ACTIVITY_JOURNAL, Tag.TAG_COMPOUND);
        appendJournalDay(out, journal, today, "Actually started today", false);
        appendJournalDay(out, journal, today - 1L, "Previous day", false);

        out.add("## Earlier");
        int shown = 0;
        Set<String> earlierShown = new HashSet<>();
        for (int i = journal.size() - 1; i >= 0 && shown < 16; i--) {
            CompoundTag row = journal.getCompound(i);
            long day = row.getLong("Day");
            if (day >= today - 1L || day < today - (ACTIVITY_JOURNAL_DAYS - 1L)) continue;
            Segment segment = segmentForTick(row.getInt("Tick"));
            String activity = canonicalJournalActivity(row.getString("Activity"));
            if (!earlierShown.add(day + "\u0000" + segment.ordinal() + "\u0000" + activity)) continue;
            out.add("* " + (today - day) + "d ago • " + segment.label() + " — " + activity);
            shown++;
        }
        if (shown == 0) out.add(". No older activity starts recorded yet.");
        out.add("~ Actual starts are retained for the last " + ACTIVITY_JOURNAL_DAYS + " Minecraft days; generated plans are not counted as completed activity.");
        return out;
    }

    private static String planTextAt(CompoundTag data, int ordinal) {
        Activity activity = activityAt(data, ordinal);
        Activity beat = interludeAt(data, ordinal);
        return beat == null ? activity.label() : beat.label() + " → " + activity.label();
    }

    private static void appendJournalDay(List<String> out, ListTag journal, long wantedDay, String title, boolean reverse) {
        out.add("## " + title);
        int before = out.size();
        Set<String> shown = new HashSet<>();
        for (int i = 0; i < journal.size(); i++) {
            CompoundTag row = journal.getCompound(i);
            if (row.getLong("Day") != wantedDay) continue;
            Segment segment = segmentForTick(row.getInt("Tick"));
            String activity = canonicalJournalActivity(row.getString("Activity"));
            if (!shown.add(segment.ordinal() + "\u0000" + activity)) continue;
            out.add("* " + segment.label() + " — " + activity);
        }
        if (out.size() == before) out.add(". No recorded activity starts.");
    }

    /**
     * Normalizes historical display synonyms without rewriting old saves. Older accepted builds
     * recorded both verb and gerund forms for the same routine, which made one period look doubled.
     */
    private static String canonicalJournalActivity(String label) {
        if (label == null) return "";
        String trimmed = label.trim();
        return switch (trimmed.toLowerCase(java.util.Locale.ROOT)) {
            case "eat", "eating" -> "Eating";
            case "rest", "resting" -> "Resting";
            default -> trimmed;
        };
    }

    private static Segment segmentForTick(int tick) {
        int slot = Math.max(0, Math.min(Segment.values().length - 1, Math.floorMod(tick, 24_000) / SLOT_TICKS));
        return Segment.values()[slot];
    }

    public static FighterAmbientActivityManager.Type ambientType(Activity activity, AmbientFighterEntity fighter) {
        if (activity == null) return null;
        return switch (activity) {
            case TRAINING -> FighterAmbientActivityManager.Type.TRAINING;
            case STRENGTH_TRAINING -> FighterAmbientActivityManager.Type.TRAINING;
            case KI_TRAINING -> FighterAmbientActivityManager.Type.KI_TRAINING;
            case FISHING -> FighterAmbientActivityManager.Type.FISHING;
            case REST -> FighterAmbientActivityManager.Type.REST;
            case SITTING -> FighterAmbientActivityManager.Type.SITTING;
            case NAP -> FighterAmbientActivityManager.Type.NAP;
            case JOGGING -> FighterAmbientActivityManager.Type.JOGGING;
            case WALKING -> FighterAmbientActivityManager.Type.WALKING;
            case SCOUTING -> FighterAmbientActivityManager.Type.SCOUTING;
            case STUDYING -> FighterAmbientActivityManager.Type.STUDYING;
            case SCIENTIST_RESEARCH -> FighterAmbientActivityManager.Type.SCIENTIST_RESEARCH;
            case EATING -> FighterAmbientActivityManager.Type.EATING;
            case STARGAZING -> FighterAmbientActivityManager.Type.STARGAZING;
            case FLIGHT -> fighter != null && fighter.hasFlightUnlocked() ? FighterAmbientActivityManager.Type.RELAXED_FLIGHT : FighterAmbientActivityManager.Type.JOGGING;
            case DANCING -> FighterAmbientActivityManager.Type.DANCING;
            case FLOWER -> FighterAmbientActivityManager.Type.FLOWER;
            case TREE -> FighterAmbientActivityManager.Type.TREE;
            case FOOD_GATHERING -> FighterAmbientActivityManager.Type.FOOD_GATHERING;
            case MEDITATION, SPARRING, SOCIALIZING, HANGING_OUT, WALK_TOGETHER, MEETING_UP -> null;
        };
    }

    public static List<String> profileLines(AmbientFighterEntity fighter) {
        List<String> out = new ArrayList<>();
        if (fighter == null || !(fighter.level() instanceof ServerLevel level) || WorldMenaceManager.isHerobrine(fighter)) return out;
        ensurePlan(fighter);
        CompoundTag data = fighter.getLegacyData();
        Segment current = currentSegment(level);
        out.add("## Daily Routine");
        Activity interlude = currentInterlude(fighter);
        out.add("+ Planned now: " + current.label() + " • " + currentActivity(fighter).label());
        if (interlude != null) out.add(". Short beat: " + interlude.label() + " • then back to the main activity");
        String reason = currentReason(fighter);
        if (!reason.isBlank()) out.add(". Why: " + reason);
        String focus = data.getString(K_FOCUS);
        if (!focus.isBlank()) out.add("* Today's focus: " + focus);
        out.addAll(FighterLifeNeedsManager.profileLines(fighter));
        String recent = previousPattern(data);
        if (!recent.isBlank()) out.add(". Recent pattern: " + recent);
        if (data.contains(K_LAST_STARTED_DAY, Tag.TAG_ANY_NUMERIC) && !data.getString(K_LAST_STARTED).isBlank()) {
            int lastSegment = Math.floorMod(data.getInt(K_LAST_STARTED_SEGMENT), Segment.values().length);
            out.add(". Last followed routine: " + Segment.values()[lastSegment].label() + " — "
                    + Activity.byName(data.getString(K_LAST_STARTED)).label());
        }
        for (Segment segment : Segment.values()) {
            Activity activity = activityAt(data, segment.ordinal());
            Activity beat = interludeAt(data, segment.ordinal());
            String planText = beat == null ? activity.label() : beat.label() + " → " + activity.label();
            out.add((segment == current ? "→ " : "• ") + segment.label() + " — " + planText);
        }
        return out;
    }

    public static List<String> debugLines(AmbientFighterEntity fighter) {
        List<String> out = new ArrayList<>();
        if (fighter == null || !(fighter.level() instanceof ServerLevel level)) return out;
        if (WorldMenaceManager.isHerobrine(fighter)) {
            out.add("Herobrine • World Menace • ordinary daily planner intentionally bypassed");
            out.add("Observed state: " + WorldMenaceManager.moodSummary(fighter));
            return out;
        }
        ensurePlan(fighter);
        CompoundTag data = fighter.getLegacyData();
        out.add(fighter.getFighterName() + " • day " + currentDay(level) + " • " + currentSegment(level).label()
                + " • focus: " + data.getString(K_FOCUS));
        for (Segment segment : Segment.values()) {
            Activity beat = interludeAt(data, segment.ordinal());
            out.add(segment.label() + ": " + (beat == null ? "" : beat.label() + " → ")
                    + activityAt(data, segment.ordinal()).label() + " • " + reasonAt(data, segment.ordinal()));
        }
        return out;
    }

    /**
     * Off-screen life keeps the existing one-meaningful-development-action-per-day safeguard.
     * It nevertheless generates/stores the same sixteen-slot routine and chooses a stable meaningful
     * slot from it instead of performing a separate unrelated RNG roll.
     */
    public static String rememberedDevelopmentActivity(ServerPlayer player, CompoundTag profile, CompoundTag record, long tick) {
        if (player == null || profile == null) return "Travelling";
        CompoundTag legacy = profile.contains("Legacy", Tag.TAG_COMPOUND) ? profile.getCompound("Legacy").copy() : new CompoundTag();
        long day = Math.floorDiv(tick, 24_000L);
        ensureRememberedPlan(player, profile, record, legacy, day);
        profile.put("Legacy", legacy);
        UUID identity = profileIdentity(profile, record);
        long slotSeed = identity.getMostSignificantBits() ^ Long.rotateLeft(identity.getLeastSignificantBits(), 23)
                ^ day * 0x9E3779B97F4A7C15L;
        int slot = Math.floorMod((int)stableHash(slotSeed), Segment.values().length);
        Activity chosen = activityAt(legacy, slot);
        // A planned spar/meditation is still real training effort in off-screen simulation, but it
        // does not create a fabricated duel result. Actual rival rematches remain handled separately.
        return switch (chosen) {
            case TRAINING, STRENGTH_TRAINING, KI_TRAINING, SPARRING, MEDITATION -> "Training";
            case JOGGING -> "Jogging";
            case WALKING -> "Walking";
            case SCOUTING -> "Scouting";
            case STUDYING, SCIENTIST_RESEARCH -> "Studying";
            case FISHING -> "Fishing";
            case STARGAZING -> "Stargazing";
            case EATING, TREE -> "Eating";
            case FOOD_GATHERING -> "Walking"; // no off-screen fake BP/reward
            case FLIGHT -> "Travelling";
            case DANCING, SOCIALIZING, HANGING_OUT, WALK_TOGETHER, MEETING_UP -> "Socializing";
            case FLOWER -> "Gardening";
            case REST, SITTING, NAP -> "Resting";
        };
    }

    /** Number of explicit development blocks in the off-screen day plan, bounded for catch-up safety. */
    public static int rememberedTrainingBlocks(ServerPlayer player, CompoundTag profile, CompoundTag record, long tick) {
        if (player == null || profile == null) return 0;
        CompoundTag legacy = profile.contains("Legacy", Tag.TAG_COMPOUND) ? profile.getCompound("Legacy").copy() : new CompoundTag();
        long day = Math.floorDiv(tick, 24_000L);
        ensureRememberedPlan(player, profile, record, legacy, day);
        profile.put("Legacy", legacy);
        int blocks = 0;
        ListTag plan = legacy.getList(K_PLAN, Tag.TAG_STRING);
        for (int i = 0; i < plan.size(); i++) {
            Activity a = Activity.byName(plan.getString(i));
            if (a == TRAINING || a == STRENGTH_TRAINING || a == KI_TRAINING || a == SPARRING || a == MEDITATION) blocks++;
        }
        // Aggregate the real routine without allowing one unusually obsessive generated day to
        // bypass the established medium-scale catch-up budget.
        return Math.min(6, blocks);
    }

    /**
     * Deterministically turns a minority of planned meditation starts into multi-block sessions.
     * Meal anchors are preserved so long meditation creates a believable commitment rather than
     * blindly erasing every basic-need beat in the day.
     */
    private static void extendMeditationRuns(ListTag plan, ListTag reasons, UUID id, long day) {
        if (plan == null || plan.size() != Segment.values().length) return;
        for (int i = 0; i < plan.size(); i++) {
            if (Activity.byName(plan.getString(i)) != MEDITATION) continue;
            long seed = stableHash(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17)
                    ^ day * 0xD1B54A32D192ED03L ^ i * 0x9E3779B97F4A7C15L);
            int roll = Math.floorMod((int)seed, 1000);
            int wanted = roll < 12 ? 8 + Math.floorMod((int)(seed >>> 32), 5)      // rare 10-15 minute planned commitment
                    : roll < 72 ? 4 + Math.floorMod((int)(seed >>> 32), 4)       // several blocks
                    : roll < 220 ? 2 + Math.floorMod((int)(seed >>> 32), 2)       // common longer session
                    : 1;
            if (wanted <= 1) continue;
            int made = 1;
            for (int j = i + 1; j < plan.size() && made < wanted; j++) {
                Segment seg = Segment.values()[j];
                Activity existing = Activity.byName(plan.getString(j));
                if (seg.isMealAnchor() && existing == EATING) break;
                if (existing == SPARRING || existing == FOOD_GATHERING) break;
                plan.set(j, StringTag.valueOf(MEDITATION.name()));
                if (reasons != null && j < reasons.size()) reasons.set(j, StringTag.valueOf("continuing a deliberate deep meditation"));
                made++;
            }
            i += made - 1;
        }
    }

    private static void generateLoadedPlan(AmbientFighterEntity fighter, ServerLevel level, long day, CompoundTag data) {
        preservePreviousPlan(data);
        if (RedRibbonExperimentManager.isExperiment(fighter)) {
            generateExperimentPlan(fighter, day, data);
            return;
        }
        ListTag plan = new ListTag();
        ListTag reasons = new ListTag();
        ListTag interludes = new ListTag();
        ListTag interludeReasons = new ListTag();
        Activity previous = null;
        EnumMap<Activity, Integer> dayCounts = new EnumMap<>(Activity.class);
        String focus = focusFor(fighter);
        for (Segment segment : Segment.values()) {
            EnumMap<Activity, Integer> scores = baseScores(segment);
            applyPersonality(scores, fighter.getPersonality());
            applyArchetype(scores, fighter.getArchetype());
            applyHobby(scores, FighterHobby.of(fighter));
            applyGoal(scores, FighterGoalManager.currentType(fighter));
            applyRelevance(scores, WorldPowerScaler.trainingPressure(fighter), fighter.getPersonality());
            applyMood(scores, fighter);
            applyRelationshipLife(scores, FighterNpcSocialManager.meaningfulBondCount(fighter), segment);
            applyTimeConstraints(scores, segment);
            applySequenceLogic(scores, previous, dayCounts, segment);
            penalizePreviousDay(scores, data, segment);
            applyPreviousDayContinuity(scores, data, segment, FighterHobby.of(fighter));
            applyWithinDayVariety(scores, dayCounts, FighterGoalManager.currentType(fighter),
                    WorldPowerScaler.trainingPressure(fighter));
            if (dayCounts.getOrDefault(FOOD_GATHERING, 0) >= 1) scores.put(FOOD_GATHERING, -10_000);
            applyRecoverySanity(scores, dayCounts, segment);
            applyR15RhythmBias(scores, dayCounts, segment, previous, FighterScientistManager.isScientist(fighter));
            FighterLifeNeedsManager.applyPlanBias(fighter, scores);
            FighterPassiveSkillManager.addStudyBias(fighter, scores);
            applyFactionContext(scores, fighter, level);
            applyFactionRoleBias(scores, fighter);
            if (previous != null && !allowsRepeatedFocus(fighter, previous)) add(scores, previous, -12);
            suppressShortPrimaryActivities(scores);
            addDeterministicNoise(scores, fighter.getUUID(), day, segment.ordinal());
            Activity picked = highest(scores, fighter.isNonCombatant(), fighter.hasFlightUnlocked());
            Activity beat = chooseInterlude(fighter.getPersonality(), FighterHobby.of(fighter),
                    FighterGoalManager.currentType(fighter), WorldPowerScaler.trainingPressure(fighter),
                    segment, previous, fighter.getUUID(), day);
            interludes.add(StringTag.valueOf(beat == null ? "" : beat.name()));
            interludeReasons.add(StringTag.valueOf(beat == null ? "" : interludeReason(beat, segment, previous)));
            plan.add(StringTag.valueOf(picked.name()));
            reasons.add(StringTag.valueOf(reasonFor(fighter, picked)));
            dayCounts.put(picked, dayCounts.getOrDefault(picked, 0) + 1);
            previous = picked;
        }
        extendMeditationRuns(plan, reasons, fighter.getUUID(), day);
        normalizeDailyRhythm(plan, reasons);
        ensureScientistResearchBlocks(plan, reasons, FighterScientistManager.isScientist(fighter), fighter.getUUID(), day,
                FighterLifeNeedsManager.intent(fighter) == FighterLifeNeedsManager.Intent.RESEARCH ? 2 : 1);
        normalizeMeals(interludes, interludeReasons, fighter.getUUID(), day);
        data.putInt(K_SCHEMA, SCHEMA);
        data.putLong(K_DAY, day);
        data.put(K_PLAN, plan);
        data.put(K_REASONS, reasons);
        data.put(K_INTERLUDES, interludes);
        data.put(K_INTERLUDE_REASONS, interludeReasons);
        data.putString(K_FOCUS, focus);
    }


    /** X-7 is an android weapon, not a civilian. Its "day" is conditioning between hunts. */
    private static void generateExperimentPlan(AmbientFighterEntity fighter, long day, CompoundTag data) {
        ListTag plan = new ListTag();
        ListTag reasons = new ListTag();
        ListTag interludes = new ListTag();
        ListTag interludeReasons = new ListTag();
        Activity previous = null;
        for (Segment segment : Segment.values()) {
            long seed = stableHash(fighter.getUUID().getMostSignificantBits()
                    ^ Long.rotateLeft(fighter.getUUID().getLeastSignificantBits(), 19)
                    ^ day * 0xD1B54A32D192ED03L ^ segment.ordinal() * 0x9E3779B97F4A7C15L);
            int roll = Math.floorMod((int)seed, 100);
            Activity picked = roll < 66 ? TRAINING : KI_TRAINING;
            if (picked == previous) picked = picked == TRAINING ? KI_TRAINING : TRAINING;
            plan.add(StringTag.valueOf(picked.name()));
            reasons.add(StringTag.valueOf(switch (picked) {
                case TRAINING -> "relentless combat conditioning";
                case STRENGTH_TRAINING -> "legacy training record";
                case KI_TRAINING -> "refining Ki control for combat";
                default -> "combat conditioning";
            }));
            interludes.add(StringTag.valueOf(""));
            interludeReasons.add(StringTag.valueOf(""));
            previous = picked;
        }
        data.putInt(K_SCHEMA, SCHEMA);
        data.putLong(K_DAY, day);
        data.put(K_PLAN, plan);
        data.put(K_REASONS, reasons);
        data.put(K_INTERLUDES, interludes);
        data.put(K_INTERLUDE_REASONS, interludeReasons);
        data.putString(K_FOCUS, "Combat conditioning and target acquisition");
    }

    private static void ensureRememberedPlan(ServerPlayer player, CompoundTag profile, CompoundTag record,
                                             CompoundTag legacy, long day) {
        if (legacy.getInt(K_SCHEMA) == SCHEMA && legacy.getLong(K_DAY) == day
                && legacy.getList(K_PLAN, Tag.TAG_STRING).size() == Segment.values().length
                && legacy.getList(K_INTERLUDES, Tag.TAG_STRING).size() == Segment.values().length) return;
        preservePreviousPlan(legacy);
        ListTag plan = new ListTag();
        ListTag reasons = new ListTag();
        ListTag interludes = new ListTag();
        ListTag interludeReasons = new ListTag();
        FighterPersonality personality = FighterPersonality.byId(profile.getInt("Personality"));
        UUID id = profileIdentity(profile, record);
        FighterHobby hobby = rememberedHobby(legacy, id);
        String goal = legacy.getString("GoalType");
        double own = Math.max(1.0D, profile.contains("PermanentBattlePower") ? profile.getInt("PermanentBattlePower") : profile.getInt("BattlePower"));
        double playerPower = WorldPowerScaler.activePlayerPowerPressure(player.serverLevel(), player.blockPosition(), player);
        int pressure = profilePressure(playerPower, own, record == null ? 0 : record.getInt("BattlesVsPlayer"), record == null ? 0 : record.getInt("Relationship"));
        Activity previous = null;
        EnumMap<Activity, Integer> dayCounts = new EnumMap<>(Activity.class);
        for (Segment segment : Segment.values()) {
            EnumMap<Activity, Integer> scores = baseScores(segment);
            applyPersonality(scores, personality);
            applyArchetype(scores, FighterArchetype.byId(profile.getInt("Archetype")));
            applyHobby(scores, hobby);
            applyGoal(scores, goal);
            applyRelevance(scores, pressure, personality);
            applyRelationshipLife(scores, FighterNpcSocialManager.meaningfulBondCount(legacy), segment);
            applyTimeConstraints(scores, segment);
            applySequenceLogic(scores, previous, dayCounts, segment);
            penalizePreviousDay(scores, legacy, segment);
            applyPreviousDayContinuity(scores, legacy, segment, hobby);
            applyWithinDayVariety(scores, dayCounts, goal, pressure);
            if (dayCounts.getOrDefault(FOOD_GATHERING, 0) >= 1) scores.put(FOOD_GATHERING, -10_000);
            applyRecoverySanity(scores, dayCounts, segment);
            applyR15RhythmBias(scores, dayCounts, segment, previous, FighterScientistManager.isScientist(legacy));
            if (profile.contains("FactionRole") && profile.contains("FactionId") && !profile.getString("FactionId").isBlank())
                applyFactionRoleBias(scores, FactionRole.byId(profile.getInt("FactionRole")), false);
            if (previous != null && !allowsRepeatedFocus(goal, pressure, previous)) add(scores, previous, -12);
            suppressShortPrimaryActivities(scores);
            addDeterministicNoise(scores, id, day, segment.ordinal());
            Activity picked = highest(scores, false, profile.getBoolean("FlightUnlocked"));
            Activity beat = chooseInterlude(personality, hobby, goal, pressure, segment, previous, id, day);
            interludes.add(StringTag.valueOf(beat == null ? "" : beat.name()));
            interludeReasons.add(StringTag.valueOf(beat == null ? "" : interludeReason(beat, segment, previous)));
            plan.add(StringTag.valueOf(picked.name()));
            reasons.add(StringTag.valueOf(reasonFor(personality, hobby, goal, pressure, picked)));
            dayCounts.put(picked, dayCounts.getOrDefault(picked, 0) + 1);
            previous = picked;
        }
        extendMeditationRuns(plan, reasons, id, day);
        normalizeDailyRhythm(plan, reasons);
        ensureScientistResearchBlocks(plan, reasons, FighterScientistManager.isScientist(legacy), id, day, 1);
        normalizeMeals(interludes, interludeReasons, id, day);
        legacy.putInt(K_SCHEMA, SCHEMA);
        legacy.putLong(K_DAY, day);
        legacy.put(K_PLAN, plan);
        legacy.put(K_REASONS, reasons);
        legacy.put(K_INTERLUDES, interludes);
        legacy.put(K_INTERLUDE_REASONS, interludeReasons);
        legacy.putString(K_FOCUS, focusFor(personality, hobby, goal, pressure));
    }

    private static void ensureScientistResearchBlocks(ListTag plan, ListTag reasons, boolean scientist, UUID id, long day, int wanted) {
        if (!scientist || plan == null || plan.size() != Segment.values().length) return;
        int existing = 0;
        for (int i = 0; i < plan.size(); i++) if (Activity.byName(plan.getString(i)) == SCIENTIST_RESEARCH) existing++;
        if (existing >= wanted) return;
        Segment[] candidates = {Segment.MORNING, Segment.LATE_MORNING, Segment.MID_AFTERNOON, Segment.LATE_AFTERNOON};
        int offset = Math.floorMod((int)(id.getLeastSignificantBits() ^ day), candidates.length);
        for (int n = 0; n < candidates.length && existing < wanted; n++) {
            int idx = candidates[(offset + n) % candidates.length].ordinal();
            Activity old = Activity.byName(plan.getString(idx));
            if (old == EATING || old == MEDITATION || old == SPARRING || old == FOOD_GATHERING) continue;
            plan.set(idx, StringTag.valueOf(SCIENTIST_RESEARCH.name()));
            if (reasons != null && idx < reasons.size()) reasons.set(idx, StringTag.valueOf("scientist role: maintaining the Saibaman research program"));
            existing++;
        }
    }

    private static EnumMap<Activity, Integer> baseScores(Segment segment) {
        EnumMap<Activity, Integer> s = new EnumMap<>(Activity.class);
        for (Activity a : Activity.values()) s.put(a, 10);

        // Broad human rhythm first; personality/goals/hobbies then bend it without being able to
        // produce nonsense such as stars at noon or six consecutive hard workouts without recovery.
        if (segment.isDaylight()) {
            add(s, WALKING, 8); add(s, SCOUTING, 7); add(s, SOCIALIZING, 5); add(s, STUDYING, 5); add(s, FOOD_GATHERING, 2);
        }
        if (segment.isDeepRestTime()) {
            add(s, REST, 30); add(s, SITTING, 20); add(s, MEDITATION, 16);
            add(s, TRAINING, -18); add(s, STRENGTH_TRAINING, -20); add(s, KI_TRAINING, -18); add(s, SPARRING, -22); add(s, JOGGING, -14); add(s, DANCING, -18);
        }
        switch (segment) {
            case DAWN -> {
                add(s, REST, 24); add(s, SITTING, 14); add(s, MEDITATION, 20); add(s, WALKING, 8);
                add(s, SOCIALIZING, -10); add(s, HANGING_OUT, -10); add(s, DANCING, -18);
            }
            case EARLY_MORNING -> {
                add(s, EATING, 42); add(s, WALKING, 13); add(s, MEDITATION, 12); add(s, JOGGING, 10);
                add(s, FISHING, 9); add(s, STUDYING, 5);
            }
            case MORNING -> {
                add(s, TRAINING, 15); add(s, STRENGTH_TRAINING, 13); add(s, KI_TRAINING, 12); add(s, JOGGING, 13); add(s, WALKING, 11); add(s, FISHING, 10); add(s, FOOD_GATHERING, 5);
                add(s, STUDYING, 9); add(s, SOCIALIZING, 5);
            }
            case LATE_MORNING -> {
                add(s, TRAINING, 18); add(s, STRENGTH_TRAINING, 16); add(s, KI_TRAINING, 15); add(s, SPARRING, 14); add(s, SCOUTING, 12); add(s, STUDYING, 10); add(s, FOOD_GATHERING, 6);
                add(s, WALKING, 8); add(s, SOCIALIZING, 8); add(s, WALK_TOGETHER, 5); add(s, MEETING_UP, 7); add(s, FLOWER, 5);
            }
            case NOON -> {
                add(s, EATING, 46); add(s, NAP, 18); add(s, SITTING, 3); add(s, SOCIALIZING, 15); add(s, HANGING_OUT, 12); add(s, WALK_TOGETHER, 8); add(s, MEETING_UP, 10);
                add(s, STUDYING, 8); add(s, TRAINING, 5); add(s, SPARRING, 3);
            }
            case EARLY_AFTERNOON -> {
                add(s, NAP, 14); add(s, TRAINING, 18); add(s, STRENGTH_TRAINING, 15); add(s, KI_TRAINING, 15); add(s, SPARRING, 17); add(s, SCOUTING, 12); add(s, FLIGHT, 10);
                add(s, WALKING, 8); add(s, SOCIALIZING, 8); add(s, STUDYING, 7);
            }
            case MID_AFTERNOON -> {
                add(s, TRAINING, 14); add(s, STRENGTH_TRAINING, 13); add(s, KI_TRAINING, 12); add(s, SPARRING, 13); add(s, WALKING, 12); add(s, FISHING, 9);
                add(s, FLOWER, 8); add(s, STUDYING, 9); add(s, HANGING_OUT, 8);
            }
            case LATE_AFTERNOON -> {
                add(s, TRAINING, 10); add(s, STRENGTH_TRAINING, 8); add(s, KI_TRAINING, 9); add(s, WALKING, 13); add(s, FISHING, 12); add(s, FLOWER, 10);
                add(s, HANGING_OUT, 13); add(s, SOCIALIZING, 11); add(s, WALK_TOGETHER, 12); add(s, MEETING_UP, 14); add(s, SITTING, 1);
            }
            case DUSK -> {
                add(s, EATING, 42); add(s, REST, 12); add(s, SITTING, 15); add(s, FISHING, 10);
                add(s, SOCIALIZING, 17); add(s, HANGING_OUT, 18); add(s, WALK_TOGETHER, 14); add(s, MEETING_UP, 14); add(s, WALKING, 9); add(s, STARGAZING, 8);
            }
            case EARLY_EVENING -> {
                add(s, SOCIALIZING, 20); add(s, HANGING_OUT, 21); add(s, WALK_TOGETHER, 18); add(s, MEETING_UP, 17); add(s, WALKING, 11); add(s, REST, 11);
                add(s, SITTING, 12); add(s, DANCING, 10); add(s, STARGAZING, 11); add(s, STUDYING, 6);
            }
            case EVENING -> {
                add(s, SOCIALIZING, 18); add(s, HANGING_OUT, 20); add(s, WALK_TOGETHER, 13); add(s, MEETING_UP, 12); add(s, REST, 14); add(s, SITTING, 14);
                add(s, DANCING, 11); add(s, STARGAZING, 15); add(s, WALKING, 7);
            }
            case LATE_EVENING -> {
                add(s, NAP, 10); add(s, STARGAZING, 22); add(s, HANGING_OUT, 15); add(s, SOCIALIZING, 11); add(s, REST, 19);
                add(s, SITTING, 16); add(s, MEDITATION, 13); add(s, STUDYING, 8); add(s, DANCING, 4);
            }
            case NIGHT -> {
                add(s, REST, 38); add(s, NAP, 8); add(s, SITTING, 2); add(s, STARGAZING, 27); add(s, MEDITATION, 20);
                add(s, HANGING_OUT, 3); add(s, STUDYING, 5);
            }
            case LATE_NIGHT -> {
                add(s, REST, 44); add(s, NAP, 5); add(s, SITTING, 2); add(s, STARGAZING, 20); add(s, MEDITATION, 17);
                add(s, EATING, 3); add(s, STUDYING, 4);
            }
            case DEEP_NIGHT -> {
                add(s, REST, 50); add(s, SITTING, 1); add(s, MEDITATION, 15); add(s, STARGAZING, 12);
                add(s, SOCIALIZING, -16); add(s, HANGING_OUT, -14);
            }
            case PRE_DAWN -> {
                add(s, REST, 54); add(s, SITTING, 1); add(s, MEDITATION, 16); add(s, STARGAZING, 7);
                add(s, SOCIALIZING, -20); add(s, HANGING_OUT, -18); add(s, DANCING, -24);
            }
        }
        return s;
    }

    private static void applyPersonality(EnumMap<Activity, Integer> s, FighterPersonality p) {
        switch (p) {
            case HEROIC -> {
                add(s, TRAINING, 14); add(s, STRENGTH_TRAINING, 12); add(s, KI_TRAINING, 7); add(s, SPARRING, 16); add(s, SCOUTING, 12); add(s, JOGGING, 8);
                add(s, SOCIALIZING, 9); add(s, HANGING_OUT, 5); add(s, WALK_TOGETHER, 7); add(s, WALKING, 6); add(s, FOOD_GATHERING, 8);
            }
            case CALM -> {
                add(s, MEDITATION, 18); add(s, KI_TRAINING, 13); add(s, REST, 15); add(s, NAP, 8); add(s, SITTING, 2); add(s, FISHING, 14);
                add(s, STARGAZING, 14); add(s, HANGING_OUT, 12); add(s, WALK_TOGETHER, 11); add(s, WALKING, 10); add(s, STUDYING, 8); add(s, SOCIALIZING, 5); add(s, SPARRING, -5);
            }
            case PROUD -> {
                add(s, TRAINING, 22); add(s, STRENGTH_TRAINING, 18); add(s, KI_TRAINING, 14); add(s, SPARRING, 20); add(s, FLIGHT, 12); add(s, STUDYING, 4); add(s, SOCIALIZING, 2);
                add(s, HANGING_OUT, -4); add(s, DANCING, -4);
            }
            case AGGRESSIVE -> {
                add(s, SPARRING, 28); add(s, TRAINING, 22); add(s, STRENGTH_TRAINING, 20); add(s, KI_TRAINING, 8); add(s, JOGGING, 13); add(s, SCOUTING, 10); add(s, FOOD_GATHERING, 10);
                add(s, REST, -8); add(s, HANGING_OUT, -7); add(s, WALKING, 2); add(s, SOCIALIZING, -3);
            }
            case CAUTIOUS -> {
                add(s, SCOUTING, 24); add(s, REST, 14); add(s, NAP, 7); add(s, SITTING, 2); add(s, MEDITATION, 13); add(s, KI_TRAINING, 8);
                add(s, FISHING, 10); add(s, HANGING_OUT, 7); add(s, WALKING, 11); add(s, STUDYING, 8); add(s, SPARRING, -8);
            }
        }
    }

    private static void applyArchetype(EnumMap<Activity, Integer> s, FighterArchetype archetype) {
        if (archetype == null) return;
        // Training style should express combat identity, not just personality. These are weights,
        // not locks: every fighter can still cross-train when goals/relevance/time call for it.
        switch (archetype) {
            case KI_SPECIALIST -> { add(s, KI_TRAINING, 34); add(s, MEDITATION, 10); add(s, STRENGTH_TRAINING, -8); add(s, TRAINING, 4); }
            case BRAWLER -> { add(s, STRENGTH_TRAINING, 30); add(s, TRAINING, 16); add(s, KI_TRAINING, -10); add(s, SPARRING, 8); }
            case MARTIAL_ARTIST -> { add(s, TRAINING, 25); add(s, SPARRING, 14); add(s, STRENGTH_TRAINING, 8); add(s, KI_TRAINING, 5); }
            case SPEEDSTER -> { add(s, JOGGING, 25); add(s, TRAINING, 12); add(s, KI_TRAINING, 5); add(s, STRENGTH_TRAINING, -5); }
            case GUARDIAN -> { add(s, STRENGTH_TRAINING, 16); add(s, TRAINING, 13); add(s, MEDITATION, 8); add(s, KI_TRAINING, 5); }
        }
    }

    private static void applyHobby(EnumMap<Activity, Integer> s, FighterHobby h) {
        switch (h) {
            case FISHING -> add(s, FISHING, 42);
            case STARGAZING -> add(s, STARGAZING, 42);
            case COOKING, TEA -> { add(s, EATING, 30); add(s, HANGING_OUT, 10); add(s, REST, 8); if (h == FighterHobby.COOKING) add(s, FOOD_GATHERING, 26); }
            case CAMPING, CLOUD_WATCHING -> { add(s, REST, 20); add(s, SITTING, 20); add(s, STARGAZING, 12); add(s, WALKING, 10); add(s, WALK_TOGETHER, 9); if (h == FighterHobby.CAMPING) add(s, FOOD_GATHERING, 24); }
            case MAPMAKING, MECHANICS, ROCK_COLLECTING -> { add(s, SCOUTING, 23); add(s, STUDYING, 28); add(s, WALKING, 8); add(s, HANGING_OUT, 4); }
            case GARDENING -> { add(s, FLOWER, 35); add(s, TREE, 15); }
            case MUSIC -> { add(s, DANCING, 22); add(s, HANGING_OUT, 24); add(s, SOCIALIZING, 14); add(s, WALK_TOGETHER, 5); }
            case CARD_GAMES -> { add(s, HANGING_OUT, 32); add(s, SOCIALIZING, 18); add(s, SITTING, 10); add(s, WALK_TOGETHER, 3); }
            case MARTIAL_NOTES -> { add(s, TRAINING, 28); add(s, STUDYING, 34); add(s, MEDITATION, 10); add(s, SITTING, 6); }
            case FASHION, BULGARIAN_FOLKLORE -> { add(s, SOCIALIZING, 20); add(s, HANGING_OUT, 18); add(s, REST, 8); }
        }
    }

    private static void applyGoal(EnumMap<Activity, Integer> s, String goal) {
        if (goal == null) return;
        switch (goal) {
            case "TRAIN" -> { add(s, TRAINING, 58); add(s, STRENGTH_TRAINING, 48); add(s, KI_TRAINING, 42); }
            case "ADVANCE_RACIAL" -> { add(s, TRAINING, 52); add(s, STRENGTH_TRAINING, 32); add(s, KI_TRAINING, 48); add(s, MEDITATION, 28); }
            case "LEARN_FLIGHT" -> { add(s, FLIGHT, 50); add(s, TRAINING, 34); add(s, JOGGING, 18); }
            case "LEARN_TECHNIQUE" -> { add(s, TRAINING, 38); add(s, KI_TRAINING, 44); add(s, SPARRING, 28); add(s, STUDYING, 28); add(s, MEDITATION, 16); }
            case "DEFEAT_RIVAL", "WIN_FIGHTS", "DEFEAT_STRONGER" -> { add(s, SPARRING, 58); add(s, TRAINING, 38); add(s, SCOUTING, 28); }
            case "ACQUIRE_EQUIPMENT" -> { add(s, SCOUTING, 48); add(s, WALKING, 20); add(s, JOGGING, 12); }
            case "FUSION" -> { add(s, SCOUTING, 25); add(s, REST, 12); }
            default -> { }
        }
    }

    private static void applyRelevance(EnumMap<Activity, Integer> s, int pressure, FighterPersonality p) {
        if (pressure > 0) {
            add(s, TRAINING, 16 * pressure);
            add(s, STRENGTH_TRAINING, 12 * pressure);
            add(s, KI_TRAINING, 12 * pressure);
            add(s, SPARRING, 14 * pressure);
            add(s, JOGGING, 7 * pressure);
            // Fighters who are not naturally gym-minded still respond in-character instead of
            // losing their personality to a generic catch-up script.
            if (p == FighterPersonality.CALM) add(s, MEDITATION, 15 * pressure);
            if (p == FighterPersonality.CAUTIOUS) add(s, SCOUTING, 12 * pressure);
            if (p == FighterPersonality.HEROIC) add(s, SPARRING, 8 * pressure);
            // Even a fighter chasing a huge gap still needs food/recovery between hard blocks.
            if (pressure >= 2) { add(s, EATING, 5 * pressure); add(s, REST, 4 * pressure); add(s, NAP, 3 * pressure); }
        } else if (pressure < 0) {
            add(s, TRAINING, -20);
            add(s, SPARRING, -16);
            add(s, REST, 10);
            add(s, SITTING, 10);
            add(s, HANGING_OUT, 8);
            add(s, SOCIALIZING, 6);
            add(s, WALK_TOGETHER, 7);
        }
    }

    private static void applyRelationshipLife(EnumMap<Activity, Integer> s, int meaningfulBonds, Segment segment) {
        if (meaningfulBonds <= 0) {
            // Strangers can still meet people, but an established social circle should be much more
            // visible in somebody's routine than pure opportunistic small talk.
            add(s, HANGING_OUT, -5); add(s, MEETING_UP, -8);
            add(s, WALK_TOGETHER, -6);
            return;
        }
        int capped = Math.min(3, meaningfulBonds);
        add(s, SOCIALIZING, 4 * capped);
        add(s, HANGING_OUT, 5 * capped);
        add(s, WALK_TOGETHER, 5 * capped);
        add(s, MEETING_UP, 6 * capped);
        if (segment == Segment.NOON || segment == Segment.DUSK || segment == Segment.EARLY_EVENING || segment == Segment.EVENING) {
            add(s, SOCIALIZING, 5 * capped);
            add(s, HANGING_OUT, 7 * capped);
            add(s, WALK_TOGETHER, 6 * capped);
        }
    }

    private static void applyMood(EnumMap<Activity, Integer> s, AmbientFighterEntity fighter) {
        switch (ReactiveWorldManager.mood(fighter)) {
            case WEARY -> { add(s, REST, 30); add(s, SITTING, 26); add(s, EATING, 15); add(s, TRAINING, -18); add(s, SPARRING, -22); }
            case SOMBER -> { add(s, REST, 18); add(s, SITTING, 22); add(s, STARGAZING, 17); add(s, HANGING_OUT, 5); add(s, DANCING, -25); }
            case WARY -> { add(s, SCOUTING, 25); add(s, HANGING_OUT, -5); add(s, DANCING, -18); }
            case IRRITATED -> { add(s, SCOUTING, 18); add(s, SPARRING, 8); add(s, SOCIALIZING, -6); add(s, DANCING, -20); }
            case FOCUSED -> { add(s, TRAINING, 15); add(s, STUDYING, 10); add(s, SCOUTING, 12); add(s, SOCIALIZING, -3); add(s, DANCING, -12); }
            case UPBEAT -> { add(s, DANCING, 18); add(s, SOCIALIZING, 15); add(s, HANGING_OUT, 16); add(s, JOGGING, 8); }
            case CONTENT -> { add(s, REST, 6); add(s, SITTING, 8); add(s, FISHING, 6); add(s, WALKING, 9); add(s, HANGING_OUT, 7); }
        }
    }

    /** Short/novelty actions can enrich a day but must never consume an entire routine block. */
    private static void suppressShortPrimaryActivities(EnumMap<Activity, Integer> scores) {
        for (Activity a : new Activity[]{EATING, TREE, FLOWER, DANCING, SCOUTING, STUDYING, SITTING}) scores.put(a, -10_000);
    }

    private static Activity chooseInterlude(FighterPersonality personality, FighterHobby hobby, String goal, int pressure,
                                            Segment segment, Activity previous, UUID id, long day) {
        int roll = deterministicPercent(id, day, segment.ordinal(), 0x51A7);
        if (segment == Segment.EARLY_MORNING && roll < 68) return EATING;
        if (segment == Segment.NOON && roll < 76) return EATING;
        if (segment == Segment.DUSK && roll < 86) return EATING;
        boolean daylight = segment.isDaylight();
        boolean evening = segment.ordinal() >= Segment.DUSK.ordinal() && segment.ordinal() <= Segment.LATE_EVENING.ordinal();
        boolean strenuous = previous == TRAINING || previous == STRENGTH_TRAINING || previous == KI_TRAINING
                || previous == SPARRING || previous == JOGGING || previous == FLIGHT;
        if (strenuous && roll < 10 && segment != Segment.MORNING && segment != Segment.LATE_MORNING
                && segment != Segment.EARLY_AFTERNOON && segment != Segment.EARLY_EVENING) return EATING;
        if (hobby == FighterHobby.MARTIAL_NOTES || "LEARN_TECHNIQUE".equals(goal)) {
            if (roll < 18 && !segment.isDeepRestTime()) return STUDYING;
        } else if ((personality == FighterPersonality.CALM || personality == FighterPersonality.CAUTIOUS) && roll < 3) return STUDYING;
        if (hobby == FighterHobby.MAPMAKING || "ACQUIRE_EQUIPMENT".equals(goal) || personality == FighterPersonality.CAUTIOUS) {
            if (roll >= 18 && roll < 29 && daylight) return SCOUTING;
        } else if (roll == 31 && daylight) return SCOUTING;
        if (hobby == FighterHobby.GARDENING && daylight && roll >= 34 && roll < 48) return FLOWER;
        if (daylight && roll == 52) return TREE;
        if (hobby == FighterHobby.MUSIC && evening && roll >= 58 && roll < 70) return DANCING;
        if (evening && roll == 73) return DANCING;
        return null;
    }

    private static int deterministicPercent(UUID id, long day, int slot, long salt) {
        long base = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17)
                ^ day * 0x9E3779B97F4A7C15L ^ slot * 0x632BE59BD9B4E019L ^ salt;
        return Math.floorMod((int)stableHash(base), 100);
    }

    private static String interludeReason(Activity beat, Segment segment, Activity previous) {
        if (beat == EATING) return previous == TRAINING || previous == STRENGTH_TRAINING || previous == KI_TRAINING
                || previous == SPARRING || previous == JOGGING || previous == FLIGHT
                ? "quick recovery food after exertion" : "short meal between activities";
        if (beat == STUDYING) return "brief technique/notes review";
        if (beat == SCOUTING) return "brief check of the surroundings";
        if (beat == FLOWER) return "brief hobby stop";
        if (beat == TREE) return "quick snack stop";
        if (beat == DANCING) return "short music/mood moment";
        return "short life moment";
    }

    private static Activity highest(EnumMap<Activity, Integer> s, boolean nonCombatant, boolean canFly) {
        Activity best = REST;
        int score = Integer.MIN_VALUE;
        for (Activity a : Activity.values()) {
            if (a == STRENGTH_TRAINING) continue; // retired in R19; legacy token only
            int value = s.getOrDefault(a, 0);
            if (nonCombatant && (a == TRAINING || a == STRENGTH_TRAINING || a == KI_TRAINING || a == SPARRING || a == FLIGHT || a == FOOD_GATHERING)) value -= 1000;
            if (!canFly && a == FLIGHT) value -= 1000;
            if (value > score) { score = value; best = a; }
        }
        return best;
    }

    private static void addDeterministicNoise(EnumMap<Activity, Integer> scores, UUID id, long day, int slot) {
        long base = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17) ^ day * 0x9E3779B97F4A7C15L ^ slot * 0x632BE59BD9B4E019L;
        for (Activity a : Activity.values()) {
            long h = stableHash(base ^ a.ordinal() * 0x94D049BB133111EBL);
            add(scores, a, Math.floorMod((int)h, 11) - 5); // only ±5 points of variety
        }
    }

    private static void penalizePreviousDay(EnumMap<Activity, Integer> scores, CompoundTag data, Segment segment) {
        ListTag previous = data.getList(K_PREVIOUS, Tag.TAG_STRING);
        if (previous.size() != Segment.values().length) return;
        Activity sameSlot = Activity.byName(previous.getString(segment.ordinal()));
        add(scores, sameSlot, -8);
        for (Activity a : Activity.values()) {
            int count = 0;
            for (int i = 0; i < previous.size(); i++) if (a.name().equals(previous.getString(i))) count++;
            if (count >= 3) add(scores, a, -7);
        }
    }

    private static void applyPreviousDayContinuity(EnumMap<Activity, Integer> scores, CompoundTag data,
                                                   Segment segment, FighterHobby hobby) {
        ListTag previous = data.getList(K_PREVIOUS, Tag.TAG_STRING);
        if (previous.size() != Segment.values().length) return;
        int hard = 0, recovery = 0, social = 0;
        boolean hobbySeen = false;
        for (int i = 0; i < previous.size(); i++) {
            Activity a = Activity.byName(previous.getString(i));
            if (a == TRAINING || a == STRENGTH_TRAINING || a == KI_TRAINING || a == SPARRING || a == JOGGING || a == FLIGHT) hard++;
            if (a == REST || a == SITTING || a == NAP || a == EATING || a == MEDITATION) recovery++;
            if (a == SOCIALIZING || a == HANGING_OUT || a == WALK_TOGETHER || a == MEETING_UP || a == DANCING) social++;
            if (hobbySupports(hobby, a)) hobbySeen = true;
        }
        if (hard >= 6 && segment.ordinal() <= Segment.MORNING.ordinal()) {
            add(scores, REST, 28); add(scores, EATING, 24); add(scores, SITTING, 20); add(scores, TRAINING, -18);
        }
        if (recovery >= 8 && segment.ordinal() >= Segment.MORNING.ordinal() && segment.ordinal() <= Segment.MID_AFTERNOON.ordinal()) {
            add(scores, WALKING, 14); add(scores, SCOUTING, 10); add(scores, TRAINING, 9);
        }
        if (social == 0 && segment.ordinal() >= Segment.DUSK.ordinal() && segment.ordinal() <= Segment.LATE_EVENING.ordinal()) {
            add(scores, SOCIALIZING, 14); add(scores, HANGING_OUT, 12); add(scores, WALK_TOGETHER, 12); add(scores, MEETING_UP, 14);
        }
        if (!hobbySeen && segment.ordinal() >= Segment.MID_AFTERNOON.ordinal() && segment.ordinal() <= Segment.EVENING.ordinal()) {
            for (Activity a : Activity.values()) if (hobbySupports(hobby, a)) add(scores, a, 15);
        }
    }


    /** Force regeneration of today's plan after a persistent role/life-state change. */
    public static void invalidatePlan(AmbientFighterEntity fighter) {
        if (fighter == null) return;
        CompoundTag d = fighter.getLegacyData();
        d.remove(K_SCHEMA);
        d.remove(K_PLAN);
        d.remove(K_REASONS);
    }

    private static void preservePreviousPlan(CompoundTag data) {
        ListTag current = data.getList(K_PLAN, Tag.TAG_STRING);
        if (current.size() == Segment.values().length) data.put(K_PREVIOUS, current.copy());
    }

    private static boolean allowsRepeatedFocus(AmbientFighterEntity fighter, Activity previous) {
        return allowsRepeatedFocus(FighterGoalManager.currentType(fighter), WorldPowerScaler.trainingPressure(fighter), previous);
    }

    private static boolean allowsRepeatedFocus(String goal, int pressure, Activity previous) {
        return (previous == TRAINING || previous == STRENGTH_TRAINING || previous == KI_TRAINING)
                && (pressure >= 2 || "TRAIN".equals(goal) || "ADVANCE_RACIAL".equals(goal))
                || previous == SPARRING && (pressure >= 3 || "DEFEAT_RIVAL".equals(goal) || "WIN_FIGHTS".equals(goal));
    }

    /**
     * A hobby is a preference, not a five-slot prison. Repetition gets progressively less
     * attractive inside one day, except when an explicit training/rival focus genuinely calls
     * for repeated work. This keeps recognizable habits while still producing a believable day.
     */
    private static void applyWithinDayVariety(EnumMap<Activity, Integer> scores,
                                              EnumMap<Activity, Integer> counts,
                                              String goal, int pressure) {
        for (Activity activity : Activity.values()) {
            int count = counts.getOrDefault(activity, 0);
            if (count <= 0) continue;
            int penalty = 18 * count;
            if (allowsRepeatedFocus(goal, pressure, activity)) penalty = Math.max(4, penalty / 3);
            add(scores, activity, -penalty);
        }
    }

    /** R15 schedule sanity: one coherent day, not sixteen independent attractive actions. */
    private static void applyR15RhythmBias(EnumMap<Activity, Integer> scores, EnumMap<Activity, Integer> counts,
                                           Segment segment, Activity previous, boolean scientist) {
        // Meditation is rarer as a daily choice, but extendMeditationRuns can still turn the one
        // chosen session into a deep multi-block commitment.
        add(scores, MEDITATION, -18);
        if (counts.getOrDefault(MEDITATION, 0) > 0 && previous != MEDITATION) scores.put(MEDITATION, -10_000);
        add(scores, SITTING, -24);

        boolean daytimeResearch = segment.ordinal() >= Segment.MORNING.ordinal() && segment.ordinal() <= Segment.LATE_AFTERNOON.ordinal();
        if (scientist && daytimeResearch) add(scores, SCIENTIST_RESEARCH, 34);
        else scores.put(SCIENTIST_RESEARCH, -10_000);
        if (counts.getOrDefault(SCIENTIST_RESEARCH, 0) >= 2) scores.put(SCIENTIST_RESEARCH, -10_000);

        int hard = counts.getOrDefault(TRAINING, 0) + counts.getOrDefault(STRENGTH_TRAINING, 0)
                + counts.getOrDefault(KI_TRAINING, 0) + counts.getOrDefault(SPARRING, 0)
                + counts.getOrDefault(JOGGING, 0) + counts.getOrDefault(FLIGHT, 0);
        if (hard >= 4) {
            for (Activity a : new Activity[]{TRAINING, STRENGTH_TRAINING, KI_TRAINING, SPARRING, JOGGING, FLIGHT}) add(scores, a, -38);
            add(scores, REST, 26); add(scores, NAP, 20); add(scores, WALKING, 14);
        }
        if (previous != null && isHard(previous)) {
            add(scores, REST, 16); add(scores, WALKING, 12); add(scores, NAP, 10);
            for (Activity a : new Activity[]{TRAINING, STRENGTH_TRAINING, KI_TRAINING, SPARRING}) add(scores, a, -16);
        }
        if (segment == Segment.DEEP_NIGHT || segment == Segment.PRE_DAWN) {
            for (Activity a : Activity.values()) scores.put(a, -10_000);
            scores.put(REST, 78); scores.put(NAP, 54); scores.put(STARGAZING, segment.darkEnoughForStars() ? 24 : -10_000);
            if (counts.getOrDefault(MEDITATION, 0) == 0) scores.put(MEDITATION, 12);
        } else if (segment == Segment.NIGHT || segment == Segment.LATE_NIGHT) {
            add(scores, REST, 34); add(scores, NAP, 18);
            for (Activity a : new Activity[]{TRAINING, STRENGTH_TRAINING, KI_TRAINING, SPARRING, JOGGING, FLIGHT, MEETING_UP}) add(scores, a, -30);
        }
    }

    private static boolean isHard(Activity a) {
        return a == TRAINING || a == STRENGTH_TRAINING || a == KI_TRAINING || a == SPARRING || a == JOGGING || a == FLIGHT;
    }

    /**
     * Final day-level rhythm pass. Scoring chooses personality-specific intentions; this pass makes
     * the result read like one person's day rather than sixteen unrelated attractive actions.
     * Deliberate multi-block meditation is preserved as the exception because long sessions are
     * meant to feel like a real commitment.
     */
    private static void normalizeDailyRhythm(ListTag plan, ListTag reasons) {
        if (plan == null || plan.size() != Segment.values().length) return;
        int hardRun = 0;
        int sameRun = 0;
        Activity previous = null;
        for (int i = 0; i < plan.size(); i++) {
            Segment segment = Segment.values()[i];
            Activity current = Activity.byName(plan.getString(i));

            // Sleep/recovery owns the deepest part of the night unless this exact slot is part of
            // a deliberately extended meditation run. No midnight sparring/jogging roulette.
            if ((segment == Segment.DEEP_NIGHT || segment == Segment.PRE_DAWN) && current != MEDITATION) {
                current = REST;
                plan.set(i, StringTag.valueOf(REST.name()));
                if (reasons != null && i < reasons.size()) reasons.set(i, StringTag.valueOf("deep-night recovery"));
            } else if ((segment == Segment.NIGHT || segment == Segment.LATE_NIGHT) && isHard(current)) {
                current = REST;
                plan.set(i, StringTag.valueOf(REST.name()));
                if (reasons != null && i < reasons.size()) reasons.set(i, StringTag.valueOf("winding down after the day"));
            }

            if (current == previous && current != MEDITATION) sameRun++; else sameRun = 1;
            if (isHard(current)) hardRun++; else hardRun = 0;

            // Two serious blocks in a row is enough for ordinary life. A third becomes recovery
            // unless meditation itself intentionally spans several blocks.
            if (hardRun >= 3) {
                current = (segment == Segment.NOON || segment == Segment.EARLY_AFTERNOON) ? NAP : REST;
                plan.set(i, StringTag.valueOf(current.name()));
                if (reasons != null && i < reasons.size()) reasons.set(i, StringTag.valueOf("recovering after sustained exertion"));
                hardRun = 0; sameRun = 1;
            } else if (sameRun >= 3 && current != MEDITATION) {
                current = isHard(current) ? REST : WALKING;
                if (segment.isDeepRestTime()) current = REST;
                plan.set(i, StringTag.valueOf(current.name()));
                if (reasons != null && i < reasons.size()) reasons.set(i, StringTag.valueOf("changing pace instead of repeating the same block"));
                sameRun = 1; hardRun = isHard(current) ? 1 : 0;
            }
            previous = current;
        }
    }

    /** Remove implausible adjacent meals and guarantee that a day does not become constant eating. */
    private static void normalizeMeals(ListTag beats, ListTag reasons, UUID id, long day) {
        if (beats == null || beats.size() != Segment.values().length) return;
        int meals = 0;
        int lastMeal = -99;
        for (int i = 0; i < beats.size(); i++) {
            if (!EATING.name().equals(beats.getString(i))) continue;
            if (i - lastMeal <= 2 || meals >= 3) {
                beats.set(i, StringTag.valueOf(""));
                if (reasons != null && i < reasons.size()) reasons.set(i, StringTag.valueOf(""));
                continue;
            }
            meals++; lastMeal = i;
        }
        // Most fighters should still eat twice on a normal day. Prefer noon/dusk without forcing
        // three rigid meals every single day.
        if (meals < 2) {
            int[] anchors = {Segment.NOON.ordinal(), Segment.DUSK.ordinal(), Segment.EARLY_MORNING.ordinal()};
            for (int slot : anchors) {
                if (meals >= 2) break;
                boolean near = false;
                for (int j = Math.max(0, slot - 2); j <= Math.min(beats.size()-1, slot + 2); j++)
                    if (EATING.name().equals(beats.getString(j))) { near = true; break; }
                if (near) continue;
                beats.set(slot, StringTag.valueOf(EATING.name()));
                if (reasons != null && slot < reasons.size()) reasons.set(slot, StringTag.valueOf("normal daily meal"));
                meals++;
            }
        }
    }

    /** Hard plausibility gates. The score model can be expressive, but it cannot schedule stars at noon. */
    private static void applyTimeConstraints(EnumMap<Activity, Integer> scores, Segment segment) {
        if (!segment.darkEnoughForStars()) scores.put(STARGAZING, -10_000);
        if (!segment.isDaylight()) { add(scores, FLOWER, -45); scores.put(FOOD_GATHERING, -10_000); }
        if (segment == Segment.DEEP_NIGHT || segment == Segment.PRE_DAWN) {
            add(scores, SOCIALIZING, -42);
            add(scores, HANGING_OUT, -35);
            add(scores, WALK_TOGETHER, -34);
            add(scores, MEETING_UP, -40);
            add(scores, MEETING_UP, -40);
            add(scores, DANCING, -48);
            add(scores, WALKING, -18);
            add(scores, STUDYING, -14);
        }
        if (segment.isDeepRestTime()) {
            add(scores, SCOUTING, -12);
            add(scores, FLIGHT, -18);
        }
    }

    /**
     * Makes a day read like a sequence rather than sixteen independent dice rolls. Hard exertion
     * creates recovery/food pressure, meals free the next slot for work or people, and short social
     * blocks naturally break up training/hobby stretches.
     */
    private static void applySequenceLogic(EnumMap<Activity, Integer> scores, Activity previous,
                                           EnumMap<Activity, Integer> counts, Segment segment) {
        if (previous == null) return;
        boolean strenuous = previous == TRAINING || previous == STRENGTH_TRAINING || previous == KI_TRAINING
                || previous == SPARRING || previous == JOGGING || previous == FLIGHT;
        if (strenuous) {
            add(scores, EATING, 22);
            add(scores, REST, 15);
            add(scores, NAP, 15);
            add(scores, SITTING, 2);
            add(scores, MEDITATION, 10);
            add(scores, WALKING, 8);
            add(scores, STUDYING, 6);
            add(scores, TRAINING, -14);
            add(scores, SPARRING, -16);
            add(scores, JOGGING, -10);
        } else if (previous == EATING || previous == TREE) {
            add(scores, TRAINING, 8);
            add(scores, SCOUTING, 6);
            add(scores, SOCIALIZING, 7);
            add(scores, HANGING_OUT, 6);
            add(scores, WALKING, 7);
            add(scores, STUDYING, 6);
            add(scores, EATING, -30);
            add(scores, TREE, -24);
        } else if (previous == REST || previous == SITTING || previous == NAP) {
            add(scores, JOGGING, 7);
            add(scores, SCOUTING, 7);
            add(scores, SOCIALIZING, 8);
            add(scores, WALKING, 10);
            add(scores, STUDYING, 7);
            add(scores, TRAINING, 5);
            add(scores, REST, -10);
            add(scores, SITTING, -10);
        } else if (previous == SOCIALIZING || previous == HANGING_OUT || previous == WALK_TOGETHER || previous == MEETING_UP || previous == DANCING) {
            add(scores, TRAINING, 5);
            add(scores, SCOUTING, 5);
            add(scores, REST, 5);
            add(scores, SOCIALIZING, -15);
            add(scores, HANGING_OUT, -12);
            add(scores, WALK_TOGETHER, -14);
            add(scores, MEETING_UP, -16);
            add(scores, DANCING, -18);
        } else if (previous == STUDYING) {
            add(scores, WALKING, 10);
            add(scores, SOCIALIZING, 7);
            add(scores, TRAINING, 5);
            add(scores, STUDYING, -20);
        } else if (previous == WALKING) {
            add(scores, SOCIALIZING, 7);
            add(scores, HANGING_OUT, 5);
            add(scores, WALK_TOGETHER, 7);
            add(scores, MEETING_UP, 8);
            add(scores, STUDYING, 5);
            add(scores, WALKING, -14);
        }

        int hard = counts.getOrDefault(TRAINING, 0) + counts.getOrDefault(STRENGTH_TRAINING, 0)
                + counts.getOrDefault(KI_TRAINING, 0) + counts.getOrDefault(SPARRING, 0)
                + counts.getOrDefault(JOGGING, 0) + counts.getOrDefault(FLIGHT, 0);
        if (hard >= 2) { add(scores, EATING, 10); add(scores, NAP, 10); add(scores, REST, 8); add(scores, SITTING, 1); }
        if (hard >= 4) { add(scores, TRAINING, -18); add(scores, SPARRING, -22); add(scores, REST, 18); add(scores, NAP, 15); add(scores, SITTING, 2); }

        // Meals and other quick life moments are now interludes, not 75-second primary blocks.
        // Recovery remains a real primary activity after exertion; food happens between those beats.
    }

    /** Even obsessive fighters need believable recovery, especially once darkness sets in. */
    private static void applyRecoverySanity(EnumMap<Activity, Integer> scores,
                                            EnumMap<Activity, Integer> counts,
                                            Segment segment) {
        int improvement = counts.getOrDefault(TRAINING, 0) + counts.getOrDefault(STRENGTH_TRAINING, 0)
                + counts.getOrDefault(KI_TRAINING, 0) + counts.getOrDefault(SPARRING, 0)
                + counts.getOrDefault(JOGGING, 0) + counts.getOrDefault(FLIGHT, 0);
        boolean deepNight = segment.isDeepRestTime();
        if (improvement >= 3) {
            add(scores, REST, 14);
            add(scores, NAP, 12);
            add(scores, SITTING, 2);
            add(scores, EATING, 10);
            add(scores, TRAINING, -10);
            add(scores, SPARRING, -12);
        }
        if (!deepNight || improvement < 2) return;
        add(scores, TRAINING, -65);
        add(scores, SPARRING, -70);
        add(scores, JOGGING, -30);
        add(scores, FLIGHT, -24);
        add(scores, REST, 32);
        add(scores, NAP, 16);
        add(scores, SITTING, 1);
        add(scores, MEDITATION, 22);
    }

    private static void applyFactionContext(EnumMap<Activity, Integer> scores, AmbientFighterEntity fighter, ServerLevel level) {
        if (fighter == null || level == null || !fighter.isFactionMember()) return;
        WorldFaction faction = FactionManager.byId(level, fighter.getFactionId());
        if (faction == null) return;
        FactionWorldData data = FactionWorldData.get(level);
        long now = level.getServer().overworld().getGameTime();
        boolean war = !data.warEnemies(faction, now).isEmpty();
        if (war) {
            add(scores, TRAINING, 10); add(scores, KI_TRAINING, 6); add(scores, STRENGTH_TRAINING, 5);
            add(scores, SCOUTING, 11); add(scores, SPARRING, 6);
            add(scores, DANCING, -5); add(scores, FLOWER, -4);
            if (fighter.getPersonality() == FighterPersonality.AGGRESSIVE || fighter.getPersonality() == FighterPersonality.PROUD) {
                add(scores, TRAINING, 5); add(scores, SPARRING, 4);
            } else if (fighter.getPersonality() == FighterPersonality.CAUTIOUS) add(scores, SCOUTING, 7);
            else if (fighter.getPersonality() == FighterPersonality.CALM) add(scores, MEDITATION, 5);
        }
        if (data.isLeaderKilled(faction)) {
            if (fighter.getPersonality() == FighterPersonality.AGGRESSIVE || fighter.getPersonality() == FighterPersonality.PROUD) {
                add(scores, TRAINING, 7); add(scores, SCOUTING, 3);
            } else if (fighter.getPersonality() == FighterPersonality.CAUTIOUS) {
                add(scores, SCOUTING, 8); add(scores, REST, 2);
            } else {
                add(scores, MEDITATION, 6); add(scores, REST, 4);
            }
        }
        if (data.supplies(faction) < 34) {
            add(scores, FOOD_GATHERING, 14); add(scores, SCOUTING, 4); add(scores, EATING, 3);
            add(scores, DANCING, -4);
        }
    }


    /** Organizational rank changes how a faction member spends an ordinary day without replacing personality. */
    private static void applyFactionRoleBias(EnumMap<Activity, Integer> scores, AmbientFighterEntity fighter) {
        if (fighter == null || !fighter.isFactionMember()) return;
        applyFactionRoleBias(scores, fighter.getFactionRole(), fighter.isNonCombatant());
    }

    private static void applyFactionRoleBias(EnumMap<Activity, Integer> scores, FactionRole role, boolean nonCombatant) {
        if (role == null || nonCombatant) return;
        switch (role) {
            case RECRUIT -> {
                add(scores, TRAINING, 18); add(scores, KI_TRAINING, 10); add(scores, SPARRING, 13);
                add(scores, MEETING_UP, 5); add(scores, SCOUTING, -4); add(scores, DANCING, -3);
            }
            case MEMBER -> {
                add(scores, TRAINING, 6); add(scores, SPARRING, 5); add(scores, MEETING_UP, 4);
            }
            case ENFORCER -> {
                add(scores, SCOUTING, 18); add(scores, TRAINING, 9); add(scores, SPARRING, 8);
                add(scores, WALKING, 5); add(scores, MEETING_UP, 6); add(scores, DANCING, -5);
            }
            case LIEUTENANT -> {
                add(scores, SCOUTING, 16); add(scores, MEETING_UP, 14); add(scores, SOCIALIZING, 8);
                add(scores, TRAINING, 8); add(scores, SPARRING, 6); add(scores, WALK_TOGETHER, 8);
                add(scores, FISHING, -5); add(scores, FLOWER, -5); add(scores, DANCING, -7);
            }
            case LEADER -> {
                add(scores, MEETING_UP, 20); add(scores, SOCIALIZING, 12); add(scores, SCOUTING, 12);
                add(scores, STUDYING, 8); add(scores, TRAINING, 6); add(scores, WALK_TOGETHER, 10);
                add(scores, FISHING, -9); add(scores, FLOWER, -8); add(scores, DANCING, -10);
            }
        }
    }

    private static String focusFor(AmbientFighterEntity fighter) {
        String goal = FighterGoalManager.currentType(fighter);
        int pressure = WorldPowerScaler.trainingPressure(fighter);
        if (fighter != null && fighter.isFactionMember() && fighter.level() instanceof ServerLevel level) {
            WorldFaction faction = FactionManager.byId(level, fighter.getFactionId());
            if (faction != null) {
                FactionWorldData data = FactionWorldData.get(level);
                long now = level.getServer().overworld().getGameTime();
                if (!data.warEnemies(faction, now).isEmpty()) return "Faction at war • " + FighterLifeNeedsManager.intentLabel(fighter);
                if (data.isLeaderKilled(faction)) return "Faction leadership disrupted • " + FighterLifeNeedsManager.intentLabel(fighter);
                if (data.supplies(faction) < 34) return "Faction supplies running low • " + FighterLifeNeedsManager.intentLabel(fighter);
                if (fighter.getFactionRole() == FactionRole.LEADER) return "Leading " + faction.name() + " • " + FighterLifeNeedsManager.intentLabel(fighter);
                if (fighter.getFactionRole() == FactionRole.LIEUTENANT) return faction.roleTitle(FactionRole.LIEUTENANT) + " duties • " + FighterLifeNeedsManager.intentLabel(fighter);
                if (fighter.getFactionRole() == FactionRole.ENFORCER) return faction.roleTitle(FactionRole.ENFORCER) + " duties • " + FighterLifeNeedsManager.intentLabel(fighter);
                if (fighter.getFactionRole() == FactionRole.RECRUIT) return "Earning a place in " + faction.name() + " • " + FighterLifeNeedsManager.intentLabel(fighter);
            }
        }
        if (goal != null && !goal.isBlank()) return "Personal goal • " + readableGoal(goal) + " • " + FighterLifeNeedsManager.intentLabel(fighter);
        if (pressure >= 3) return "Closing a major strength gap • " + FighterLifeNeedsManager.intentLabel(fighter);
        return FighterLifeNeedsManager.intentLabel(fighter);
    }

    private static String focusFor(FighterPersonality personality, FighterHobby hobby, String goal, int pressure) {
        if (goal != null && !goal.isBlank()) return "Personal goal • " + readableGoal(goal);
        if (pressure >= 3) return "Closing a major strength gap in their own way";
        if (pressure >= 1) return "Keeping pace with stronger fighters nearby";
        if (pressure < 0) return "Coasting, recovering and living outside training";
        return personality.displayName() + " routine • " + hobby.label().toLowerCase(Locale.ROOT);
    }

    private static String reasonFor(AmbientFighterEntity fighter, Activity picked) {
        if (picked == SCIENTIST_RESEARCH && FighterScientistManager.isScientist(fighter)) return "scientist work: improving Saibaman cultivation formula";
        return reasonFor(fighter.getPersonality(), FighterHobby.of(fighter), FighterGoalManager.currentType(fighter), WorldPowerScaler.trainingPressure(fighter), picked);
    }

    private static String reasonFor(FighterPersonality personality, FighterHobby hobby, String goal, int pressure, Activity picked) {
        if (picked == SCIENTIST_RESEARCH) return "scientist work: improving Saibaman cultivation formula";
        if (goal != null && !goal.isBlank() && goalSupports(goal, picked)) return "personal goal: " + readableGoal(goal);
        if (pressure > 0 && (picked == TRAINING || picked == STRENGTH_TRAINING || picked == KI_TRAINING
                || picked == SPARRING || picked == MEDITATION || picked == JOGGING || picked == SCOUTING || picked == STUDYING))
            return pressure >= 3 ? "far behind the current strength benchmark" : "wants to keep pace with stronger fighters";
        if (hobbySupports(hobby, picked)) return "likes " + hobby.label().toLowerCase(Locale.ROOT);
        return personality.displayName().toLowerCase(Locale.ROOT) + " temperament and time of day";
    }

    private static boolean goalSupports(String goal, Activity a) {
        return switch (goal) {
            case "TRAIN", "ADVANCE_RACIAL", "LEARN_FLIGHT", "LEARN_TECHNIQUE" -> a == TRAINING || a == STRENGTH_TRAINING || a == KI_TRAINING || a == MEDITATION || a == FLIGHT || a == JOGGING || a == STUDYING;
            case "DEFEAT_RIVAL", "WIN_FIGHTS", "DEFEAT_STRONGER" -> a == SPARRING || a == TRAINING || a == STRENGTH_TRAINING || a == KI_TRAINING || a == SCOUTING;
            case "ACQUIRE_EQUIPMENT" -> a == SCOUTING || a == JOGGING || a == WALKING;
            default -> false;
        };
    }

    private static boolean hobbySupports(FighterHobby h, Activity a) {
        return switch (h) {
            case FISHING -> a == FISHING;
            case STARGAZING -> a == STARGAZING;
            case COOKING, TEA -> a == EATING || a == HANGING_OUT || a == REST || (h == FighterHobby.COOKING && a == FOOD_GATHERING);
            case CAMPING, CLOUD_WATCHING -> a == REST || a == SITTING || a == STARGAZING || a == WALKING || (h == FighterHobby.CAMPING && a == FOOD_GATHERING);
            case MAPMAKING, MECHANICS, ROCK_COLLECTING -> a == SCOUTING || a == STUDYING || a == WALKING;
            case GARDENING -> a == FLOWER || a == TREE;
            case MUSIC -> a == DANCING || a == HANGING_OUT || a == SOCIALIZING || a == WALK_TOGETHER || a == MEETING_UP;
            case CARD_GAMES -> a == HANGING_OUT || a == SOCIALIZING || a == MEETING_UP || a == SITTING;
            case MARTIAL_NOTES -> a == TRAINING || a == STRENGTH_TRAINING || a == KI_TRAINING || a == MEDITATION || a == SITTING || a == STUDYING;
            case FASHION, BULGARIAN_FOLKLORE -> a == SOCIALIZING || a == HANGING_OUT || a == WALK_TOGETHER || a == MEETING_UP || a == REST;
        };
    }

    private static int profilePressure(double playerPower, double own, int rivalry, int relationship) {
        if (playerPower <= 0.0D || own <= 0.0D) return 0;
        double ratio = playerPower / own;
        if (ratio > 6.666D) return 3;
        if (ratio >= 2.40D) return 2;
        if (ratio > 1.20D) return 1;
        double rivalExtension = rivalry >= 3 && relationship <= -25 ? 1.35D : rivalry >= 1 && relationship <= -15 ? 1.18D : 1.0D;
        return own / playerPower / rivalExtension > 1.60D ? -1 : 0;
    }

    private static String readableGoal(String goal) {
        return switch (goal) {
            case "TRAIN" -> "serious training";
            case "ADVANCE_RACIAL" -> "racial advancement";
            case "LEARN_FLIGHT" -> "learning flight";
            case "LEARN_TECHNIQUE" -> "learning a technique";
            case "DEFEAT_RIVAL" -> "defeating a rival";
            case "WIN_FIGHTS" -> "winning meaningful fights";
            case "DEFEAT_STRONGER" -> "beating a stronger fighter";
            case "ACQUIRE_EQUIPMENT" -> "finding useful equipment";
            case "FUSION" -> "finding a fusion opportunity";
            default -> goal.toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private static Activity activityAt(CompoundTag data, int index) {
        ListTag plan = data.getList(K_PLAN, Tag.TAG_STRING);
        return index >= 0 && index < plan.size() ? Activity.byName(plan.getString(index)) : Activity.REST;
    }

    private static Activity interludeAt(CompoundTag data, int index) {
        ListTag beats = data.getList(K_INTERLUDES, Tag.TAG_STRING);
        if (index < 0 || index >= beats.size()) return null;
        String value = beats.getString(index);
        if (value == null || value.isBlank()) return null;
        try { return Activity.valueOf(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    private static String reasonAt(CompoundTag data, int index) {
        ListTag reasons = data.getList(K_REASONS, Tag.TAG_STRING);
        return index >= 0 && index < reasons.size() ? reasons.getString(index) : "";
    }

    private static String previousPattern(CompoundTag data) {
        ListTag previous = data.getList(K_PREVIOUS, Tag.TAG_STRING);
        if (previous.size() != Segment.values().length) return "";
        int improvement = 0, leisure = 0, recovery = 0;
        for (int i = 0; i < previous.size(); i++) {
            switch (Activity.byName(previous.getString(i))) {
                case TRAINING, STRENGTH_TRAINING, KI_TRAINING, MEDITATION, SPARRING -> improvement++;
                case JOGGING, FLIGHT, STUDYING, SCIENTIST_RESEARCH, FISHING, WALKING, SCOUTING, STARGAZING, DANCING, FLOWER, FOOD_GATHERING, SOCIALIZING, HANGING_OUT, WALK_TOGETHER, MEETING_UP -> leisure++;
                case REST, SITTING, NAP, EATING, TREE -> recovery++;
            }
        }
        if (improvement >= 3) return "training-heavy yesterday";
        if (leisure >= 3) return "hobby / exploration-heavy yesterday";
        if (recovery >= 3) return "recovery-heavy yesterday";
        return "balanced yesterday";
    }

    /** Mirrors FighterHobby.of for an unloaded record so old saves do not all default to Cooking. */
    private static FighterHobby rememberedHobby(CompoundTag legacy, UUID identity) {
        if (legacy.contains("HobbyId", Tag.TAG_ANY_NUMERIC)) return FighterHobby.byId(legacy.getInt("HobbyId"));
        long mixed = identity.getMostSignificantBits() ^ Long.rotateLeft(identity.getLeastSignificantBits(), 23);
        int id;
        if (Math.floorMod(mixed, 97L) == 0L) id = FighterHobby.BULGARIAN_FOLKLORE.ordinal();
        else id = Math.floorMod((int)(mixed ^ (mixed >>> 32)), FighterHobby.values().length - 1);
        legacy.putInt("HobbyId", id);
        return FighterHobby.byId(id);
    }

    private static UUID profileIdentity(CompoundTag profile, CompoundTag record) {
        if (record != null && record.hasUUID("RecordId")) return record.getUUID("RecordId");
        String name = profile == null ? "fighter" : profile.getString("Name");
        return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static long stableHash(UUID id) { return stableHash(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 23)); }
    private static long stableHash(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static void add(EnumMap<Activity, Integer> scores, Activity activity, int amount) {
        scores.put(activity, scores.getOrDefault(activity, 0) + amount);
    }
}
