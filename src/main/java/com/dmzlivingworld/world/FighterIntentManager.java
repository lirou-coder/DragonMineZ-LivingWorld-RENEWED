package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterPersonality;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.UUID;

/**
 * Short-lived, saved reasons for an NPC's next ordinary decision.  Goals answer "what matters
 * to this character over days"; an intent answers "what are they trying to do right now?".
 * The manager deliberately never owns combat or pathfinding.  It guides the existing activity
 * and conversation systems so native DMZ combat remains authoritative.
 */
public final class FighterIntentManager {
    private static final String TYPE = "LWIntent";
    private static final String REASON = "LWIntentReason";
    private static final String SUBJECT = "LWIntentSubject";
    private static final String UNTIL = "LWIntentUntil";
    private static final String SET_AT = "LWIntentSetAt";
    private static final String NEXT_CONCERN = "LWNextIntentConcern";
    private static final String SCHEMA = "LWIntentSchema";
    private static final int CURRENT_SCHEMA = 1;
    private static final int CHECK_INTERVAL = 100;

    public enum Intent {
        NONE("none"),
        RECOVER("recover"),
        TRAIN("train"),
        SCOUT("watch the area"),
        CHECK_ALLY("check on someone"),
        SOCIALIZE("spend time with someone"),
        REFLECT("take a quiet moment"),
        ROAM("look for something to do");

        private final String label;
        Intent(String label) { this.label = label; }
        public String label() { return label; }
    }

    private FighterIntentManager() {}

    public static void tick(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || !(fighter.level() instanceof ServerLevel level)) return;
        if (FactionRequestMissionManager.isExclusiveFieldAssignment(fighter)) {
            clear(fighter);
            return;
        }
        if (fighter.tickCount % CHECK_INTERVAL != Math.floorMod(fighter.getUUID().hashCode(), CHECK_INTERVAL)) return;
        if (!fighter.isAlive() || fighter.isCaptive() || fighter.isDefeated()) return;

        CompoundTag data = fighter.getPersistentData();
        if (data.getInt(SCHEMA) != CURRENT_SCHEMA) {
            data.remove(TYPE); data.remove(REASON); data.remove(SUBJECT); data.remove(UNTIL); data.remove(SET_AT);
            data.putInt(SCHEMA, CURRENT_SCHEMA);
        }
        long now = level.getGameTime();
        Intent current = current(fighter);
        if (current != Intent.NONE && now < data.getLong(UNTIL)) {
            // Physical safety and an injured friend are allowed to supersede a weaker plan.
            if (current != Intent.RECOVER && fighter.getHealth() < fighter.getMaxHealth() * 0.52F) {
                set(fighter, Intent.RECOVER, "needs time to recover", null, 900L);
            }
            return;
        }

        if (fighter.getTarget() != null || fighter.isMeditating() || fighter.isTransforming()
                || fighter.isKaiokenActive() || fighter.isSanctionedMatchParticipant()) return;

        if (fighter.getHealth() < fighter.getMaxHealth() * 0.62F) {
            set(fighter, Intent.RECOVER, "is nursing recent injuries", null, 900L);
            return;
        }

        AmbientFighterEntity hurtFriend = now >= data.getLong(NEXT_CONCERN) ? nearestHurtFriend(fighter, level) : null;
        if (hurtFriend != null) {
            set(fighter, Intent.CHECK_ALLY, "noticed " + hurtFriend.getFighterName() + " was hurt", hurtFriend, 720L);
            return;
        }

        String event = ReactiveWorldManager.recentEventType(fighter, 1800L);
        if ("ALLY_DIED".equals(event) || "ENEMY_DIED".equals(event)) {
            set(fighter, Intent.REFLECT, "is processing what happened nearby", null, 760L);
            return;
        }
        if ("MOB_SEEN".equals(event) || "POWER_TRANSFORM".equals(event)
                || "POWER_CHARGE".equals(event) || "HORN_RALLY".equals(event)) {
            set(fighter, Intent.SCOUT, "is keeping an eye on the area", null, 620L);
            return;
        }

        AmbientFighterEntity companion = FighterNpcSocialManager.closestMeaningfulBond(fighter, 20.0D);
        if (companion != null && wantsCompany(fighter)) {
            set(fighter, Intent.SOCIALIZE, "wants to catch up with " + companion.getFighterName(), companion, 620L);
            return;
        }

        String goal = FighterGoalManager.currentType(fighter);
        if ("TRAIN".equals(goal) || "ADVANCE_RACIAL".equals(goal) || "LEARN_FLIGHT".equals(goal)
                || "LEARN_TECHNIQUE".equals(goal)) {
            set(fighter, Intent.TRAIN, "is working toward a personal goal", null, 820L);
        } else if ("ACQUIRE_EQUIPMENT".equals(goal) || "DEFEAT_RIVAL".equals(goal)
                || "DEFEAT_STRONGER".equals(goal)) {
            set(fighter, Intent.SCOUT, "is looking for an opening", null, 640L);
        } else if (isQuietNight(level)) {
            set(fighter, Intent.REFLECT, "is taking advantage of the quiet", null, 620L);
        } else {
            set(fighter, Intent.ROAM, "is deciding what to do next", null, 420L);
        }
    }

    /** An event changes the owner's next choices, rather than becoming a forgotten text log. */
    public static void noteWorldEvent(AmbientFighterEntity fighter, String type, String subject, String detail) {
        if (fighter == null || fighter.level().isClientSide || type == null || type.isBlank()) return;
        if (FactionRequestMissionManager.isExclusiveFieldAssignment(fighter)) return;
        if (fighter.getTarget() != null || fighter.isMeditating() || fighter.isSanctionedMatchParticipant()) return;
        switch (type) {
            case "ALLY_DIED", "ENEMY_DIED" -> setIfWeaker(fighter, Intent.REFLECT,
                    "is thinking about " + (subject == null || subject.isBlank() ? "the recent fight" : subject), null, 760L);
            case "MOB_SEEN", "POWER_TRANSFORM", "POWER_CHARGE", "HORN_RALLY" -> setIfWeaker(fighter, Intent.SCOUT,
                    "wants to understand what changed nearby", null, 600L);
            case "TRAINING_GROWTH" -> setIfWeaker(fighter, Intent.TRAIN,
                    "wants to build on a breakthrough", null, 720L);
            default -> { }
        }
    }

    /** Biases ordinary-life activities toward a current reason without forcing a scripted scene. */
    public static void addActivityPreferences(AmbientFighterEntity fighter, List<FighterAmbientActivityManager.Type> bag) {
        if (fighter == null || bag == null || bag.isEmpty()) return;
        switch (current(fighter)) {
            case RECOVER -> {
                addManyIfPresent(bag, FighterAmbientActivityManager.Type.REST, 5);
                addManyIfPresent(bag, FighterAmbientActivityManager.Type.EATING, 3);
                addManyIfPresent(bag, FighterAmbientActivityManager.Type.FISHING, 1);
            }
            case TRAIN -> addManyIfPresent(bag, FighterAmbientActivityManager.Type.TRAINING, 5);
            case SCOUT -> {
                addManyIfPresent(bag, FighterAmbientActivityManager.Type.SCOUTING, 5);
                addManyIfPresent(bag, FighterAmbientActivityManager.Type.JOGGING, 2);
            }
            case REFLECT -> {
                addManyIfPresent(bag, FighterAmbientActivityManager.Type.STARGAZING, 4);
                addManyIfPresent(bag, FighterAmbientActivityManager.Type.REST, 3);
            }
            case ROAM -> addManyIfPresent(bag, FighterAmbientActivityManager.Type.JOGGING, 2);
            default -> { }
        }
    }

    public static Intent current(AmbientFighterEntity fighter) {
        if (fighter == null) return Intent.NONE;
        CompoundTag data = fighter.getPersistentData();
        long now = fighter.level().getGameTime();
        if (data.getLong(UNTIL) <= now) return Intent.NONE;
        try { return Intent.valueOf(data.getString(TYPE)); }
        catch (IllegalArgumentException ignored) { return Intent.NONE; }
    }

    public static String summary(AmbientFighterEntity fighter) {
        Intent intent = current(fighter);
        if (intent == Intent.NONE || fighter == null) return "";
        String reason = fighter.getPersistentData().getString(REASON);
        return reason.isBlank() ? intent.label() : intent.label() + " • " + reason;
    }

    public static AmbientFighterEntity preferredSocialTarget(AmbientFighterEntity fighter, double radius) {
        if (fighter == null || !(fighter.level() instanceof ServerLevel level)) return null;
        Intent intent = current(fighter);
        if (intent != Intent.CHECK_ALLY && intent != Intent.SOCIALIZE) return null;
        CompoundTag data = fighter.getPersistentData();
        if (!data.hasUUID(SUBJECT)) return null;
        UUID id = data.getUUID(SUBJECT);
        if (!(level.getEntity(id) instanceof AmbientFighterEntity other) || other == fighter || !other.isAlive()) return null;
        return fighter.distanceToSqr(other) <= radius * radius ? other : null;
    }

    public static boolean isConcernedAbout(AmbientFighterEntity fighter, AmbientFighterEntity other) {
        if (fighter == null || other == null || current(fighter) != Intent.CHECK_ALLY) return false;
        CompoundTag data = fighter.getPersistentData();
        return data.hasUUID(SUBJECT) && other.getUUID().equals(data.getUUID(SUBJECT));
    }

    /** A successful check-in is a completed small intention, not a permanent looping behaviour. */
    public static void resolveSocialIntent(AmbientFighterEntity a, AmbientFighterEntity b) {
        resolveIfAbout(a, b);
        resolveIfAbout(b, a);
    }

    private static void resolveIfAbout(AmbientFighterEntity owner, AmbientFighterEntity other) {
        if (owner == null || other == null) return;
        Intent intent = current(owner);
        if ((intent == Intent.CHECK_ALLY || intent == Intent.SOCIALIZE) && isSubject(owner, other)) {
            if (intent == Intent.CHECK_ALLY) owner.getPersistentData().putLong(NEXT_CONCERN, owner.level().getGameTime() + 1200L);
            clear(owner);
        }
    }

    private static AmbientFighterEntity nearestHurtFriend(AmbientFighterEntity fighter, ServerLevel level) {
        return level.getEntitiesOfClass(AmbientFighterEntity.class, fighter.getBoundingBox().inflate(18.0D), other -> {
                    if (other == fighter || !other.isAlive() || other.isDefeated() || other.isCaptive()) return false;
                    if (other.getHealth() >= other.getMaxHealth() * 0.58F) return false;
                    if (fighter.isFactionMember() && other.isFactionMember() && fighter.getFactionId().equals(other.getFactionId())) return true;
                    return FighterNpcSocialManager.bond(fighter, other) >= 5;
                }).stream().min(java.util.Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
    }

    private static boolean wantsCompany(AmbientFighterEntity fighter) {
        return fighter.getPersonality() != FighterPersonality.AGGRESSIVE
                && ReactiveWorldManager.mood(fighter) != ReactiveWorldManager.Mood.IRRITATED
                && ReactiveWorldManager.mood(fighter) != ReactiveWorldManager.Mood.WARY;
    }

    private static boolean isQuietNight(ServerLevel level) {
        long day = Math.floorMod(level.getDayTime(), 24000L);
        return day >= 13000L && day <= 22800L && !level.isRaining();
    }

    private static void setIfWeaker(AmbientFighterEntity fighter, Intent intent, String reason,
                                    AmbientFighterEntity subject, long duration) {
        Intent active = current(fighter);
        if (active == Intent.RECOVER || active == Intent.CHECK_ALLY) return;
        set(fighter, intent, reason, subject, duration);
    }

    private static void set(AmbientFighterEntity fighter, Intent intent, String reason,
                            AmbientFighterEntity subject, long duration) {
        if (fighter == null || fighter.level().isClientSide) return;
        CompoundTag data = fighter.getPersistentData();
        data.putInt(SCHEMA, CURRENT_SCHEMA);
        data.putString(TYPE, intent.name());
        data.putString(REASON, truncate(reason, 96));
        if (subject != null) data.putUUID(SUBJECT, subject.getUUID()); else data.remove(SUBJECT);
        long now = fighter.level().getGameTime();
        data.putLong(SET_AT, now);
        data.putLong(UNTIL, now + Math.max(80L, duration));
    }

    private static boolean isSubject(AmbientFighterEntity owner, AmbientFighterEntity other) {
        CompoundTag data = owner.getPersistentData();
        return data.hasUUID(SUBJECT) && other.getUUID().equals(data.getUUID(SUBJECT));
    }

    private static void clear(AmbientFighterEntity fighter) {
        CompoundTag data = fighter.getPersistentData();
        data.remove(TYPE); data.remove(REASON); data.remove(SUBJECT); data.remove(UNTIL); data.remove(SET_AT);
    }

    private static void addManyIfPresent(List<FighterAmbientActivityManager.Type> bag,
                                         FighterAmbientActivityManager.Type type, int count) {
        if (!bag.contains(type)) return;
        for (int i = 0; i < count; i++) bag.add(type);
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
