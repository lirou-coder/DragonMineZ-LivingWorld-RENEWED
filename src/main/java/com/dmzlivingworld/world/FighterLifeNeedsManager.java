package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterPersonality;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.util.EnumMap;
import java.util.UUID;

/**
 * Quiet cause-and-effect layer behind ordinary Living World life.
 * Values are deliberately not exposed as Sims-style bars; they exist so a fighter's next choices
 * are consequences of what they actually did, how hard they pushed, and who they spent time with.
 */
public final class FighterLifeNeedsManager {
    private static final int SCHEMA = 1;
    private static final String K_SCHEMA = "LWLifeNeedsSchema";
    private static final String K_LAST = "LWLifeNeedsLastTick";
    private static final String K_HUNGER = "LWLifeHunger";       // 0 fed -> 100 hungry
    private static final String K_FATIGUE = "LWLifeFatigue";     // 0 rested -> 100 exhausted
    private static final String K_KI_STRAIN = "LWLifeKiStrain";  // 0 fresh -> 100 strained
    private static final String K_SOCIAL = "LWLifeSocialNeed";   // 0 connected -> 100 isolated
    private static final String K_RESTLESS = "LWLifeRestless";   // 0 content -> 100 bored/restless
    private static final String K_LAST_DAY = "LWLifeIntentDay";
    private static final String K_INTENT = "LWLifeIntent";
    private static final String K_POST_SPAR_AWAKE_UNTIL = "LWPostSparAwakeUntil";

    public enum Intent {
        RECOVER("Recover and take the day lightly"),
        REFUEL("Get fed and restore energy"),
        SERIOUS_TRAINING("Push development seriously"),
        KI_DEVELOPMENT("Work on Ki control and focus"),
        SOCIAL_DAY("Spend time with people who matter"),
        EXPLORATION("Get out, move around and see something"),
        RESEARCH("Work on Saibaman research"),
        BALANCED("Keep a balanced ordinary day");
        private final String label;
        Intent(String label) { this.label = label; }
        public String label() { return label; }
    }

    private FighterLifeNeedsManager() {}

    public static void tick(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || !(fighter.level() instanceof ServerLevel level)) return;
        if (WorldMenaceManager.isWorldMenace(fighter)) {
            clearCivilianNeeds(fighter.getLegacyData());
            return;
        }
        CompoundTag d = fighter.getLegacyData();
        ensureDefaults(fighter, d, level.getGameTime());
        long now = level.getGameTime();
        long last = d.getLong(K_LAST);
        long elapsed = Math.max(0L, Math.min(2400L, now - last));
        if (elapsed < 100L) return;
        d.putLong(K_LAST, now);

        double hunger = value(d, K_HUNGER) + elapsed * 0.0031D;
        double fatigue = value(d, K_FATIGUE) + elapsed * 0.00165D;
        double social = value(d, K_SOCIAL) + elapsed * 0.00110D;
        double restless = value(d, K_RESTLESS) + elapsed * 0.00092D;
        double ki = Math.max(0.0D, value(d, K_KI_STRAIN) - elapsed * 0.00042D);

        // Combat and actual movement make the next life choice matter instead of resetting to RNG.
        if (fighter.getTarget() != null) { fatigue += elapsed * 0.0022D; hunger += elapsed * 0.0008D; ki += elapsed * 0.0012D; restless -= elapsed * 0.0018D; }
        if (fighter.isMeditating()) { fatigue -= elapsed * 0.0010D; ki -= elapsed * 0.0038D; restless -= elapsed * 0.0010D; }
        if (fighter.isSocialLifeActivity()) { social -= elapsed * 0.0032D; restless -= elapsed * 0.0015D; }

        put(d, K_HUNGER, hunger); put(d, K_FATIGUE, fatigue); put(d, K_SOCIAL, social);
        put(d, K_RESTLESS, restless); put(d, K_KI_STRAIN, ki);
    }

    public static void onActivityCompleted(AmbientFighterEntity fighter, FighterAmbientActivityManager.Type type, int elapsedTicks) {
        if (fighter == null || type == null || WorldMenaceManager.isWorldMenace(fighter)) return;
        CompoundTag d = fighter.getLegacyData();
        ensureDefaults(fighter, d, fighter.level().getGameTime());
        double scale = Math.max(0.55D, Math.min(1.65D, elapsedTicks / 700.0D));
        switch (type) {
            case EATING, TREE -> add(d, K_HUNGER, -52.0D * scale);
            case FOOD_GATHERING -> { add(d, K_HUNGER, -18.0D); add(d, K_FATIGUE, 7.0D); add(d, K_RESTLESS, -12.0D); }
            case NAP -> { add(d, K_FATIGUE, -43.0D * scale); add(d, K_KI_STRAIN, -10.0D); }
            case REST, SITTING -> { add(d, K_FATIGUE, -22.0D * scale); add(d, K_KI_STRAIN, -8.0D); add(d, K_RESTLESS, -5.0D); }
            case TRAINING, STRENGTH_TRAINING -> { add(d, K_FATIGUE, 31.0D * scale); add(d, K_HUNGER, 17.0D * scale); add(d, K_RESTLESS, -25.0D); }
            case KI_TRAINING -> { add(d, K_FATIGUE, 18.0D * scale); add(d, K_KI_STRAIN, 29.0D * scale); add(d, K_HUNGER, 11.0D * scale); add(d, K_RESTLESS, -24.0D); }
            case JOGGING, RELAXED_FLIGHT -> { add(d, K_FATIGUE, 20.0D * scale); add(d, K_HUNGER, 10.0D); add(d, K_RESTLESS, -22.0D); }
            case WALKING, FISHING, SCOUTING, FLOWER, STARGAZING, DANCING -> add(d, K_RESTLESS, -24.0D * scale);
            case STUDYING, SCIENTIST_RESEARCH -> { add(d, K_RESTLESS, -16.0D); add(d, K_FATIGUE, 4.0D); }
        }
    }

    public static void onMeditationCompleted(AmbientFighterEntity fighter, int elapsedTicks) {
        if (fighter == null || WorldMenaceManager.isWorldMenace(fighter)) return;
        CompoundTag d = fighter.getLegacyData(); ensureDefaults(fighter, d, fighter.level().getGameTime());
        double scale = Math.max(0.7D, Math.min(2.3D, elapsedTicks / 1200.0D));
        add(d, K_KI_STRAIN, -38.0D * scale); add(d, K_FATIGUE, -14.0D * scale); add(d, K_RESTLESS, -18.0D);
    }

    public static void onSocialCompleted(AmbientFighterEntity fighter, boolean meaningfulMeet, boolean walk) {
        if (fighter == null || WorldMenaceManager.isWorldMenace(fighter)) return;
        CompoundTag d = fighter.getLegacyData(); ensureDefaults(fighter, d, fighter.level().getGameTime());
        add(d, K_SOCIAL, meaningfulMeet ? -46.0D : -30.0D); add(d, K_RESTLESS, walk ? -20.0D : -13.0D);
    }

    /** A real spar can make a fighter tired without making the next routine block almost always sleep. */
    public static void onSparCompleted(AmbientFighterEntity fighter, int effortTicks) {
        if (fighter == null || WorldMenaceManager.isWorldMenace(fighter)) return;
        CompoundTag d = fighter.getLegacyData();
        ensureDefaults(fighter, d, fighter.level().getGameTime());
        double scale = Math.max(0.45D, Math.min(1.35D, Math.max(100, effortTicks) / 700.0D));
        add(d, K_FATIGUE, 12.0D * scale);
        add(d, K_HUNGER, 5.0D * scale);
        // Cool down awake first: rest/sitting/eating/meditation remain valid, but no immediate nap.
        d.putLong(K_POST_SPAR_AWAKE_UNTIL, fighter.level().getGameTime() + 2400L + fighter.getRandom().nextInt(1201));
    }

    public static boolean postSparAwake(AmbientFighterEntity fighter) {
        return fighter != null && fighter.getLegacyData().getLong(K_POST_SPAR_AWAKE_UNTIL) > fighter.level().getGameTime();
    }

    public static Intent intent(AmbientFighterEntity fighter) {
        if (fighter == null || !(fighter.level() instanceof ServerLevel level)) return Intent.BALANCED;
        if (WorldMenaceManager.isWorldMenace(fighter)) return Intent.SERIOUS_TRAINING;
        CompoundTag d = fighter.getLegacyData(); ensureDefaults(fighter, d, level.getGameTime());
        long day = FighterDailyRoutineManager.currentDay(level);
        if (d.getLong(K_LAST_DAY) != day || !d.contains(K_INTENT, Tag.TAG_STRING)) {
            Intent selected = chooseIntent(fighter, d);
            d.putLong(K_LAST_DAY, day); d.putString(K_INTENT, selected.name());
        }
        try { return Intent.valueOf(d.getString(K_INTENT)); } catch (IllegalArgumentException ignored) { return Intent.BALANCED; }
    }

    public static String intentLabel(AmbientFighterEntity fighter) { return intent(fighter).label(); }

    public static void applyPlanBias(AmbientFighterEntity fighter, EnumMap<FighterDailyRoutineManager.Activity, Integer> s) {
        if (fighter == null || s == null || WorldMenaceManager.isWorldMenace(fighter)) return;
        CompoundTag d = fighter.getLegacyData(); ensureDefaults(fighter, d, fighter.level().getGameTime());
        double hunger = value(d, K_HUNGER), fatigue = value(d, K_FATIGUE), ki = value(d, K_KI_STRAIN), social = value(d, K_SOCIAL), restless = value(d, K_RESTLESS);
        using(s, FighterDailyRoutineManager.Activity.EATING, hunger > 74 ? 46 : hunger > 52 ? 22 : hunger < 25 ? -30 : 0);
        using(s, FighterDailyRoutineManager.Activity.REST, fatigue > 76 ? 48 : fatigue > 55 ? 24 : fatigue < 24 ? -9 : 0);
        using(s, FighterDailyRoutineManager.Activity.NAP, postSparAwake(fighter) ? -1000 : (fatigue > 70 ? 34 : fatigue > 52 ? 14 : -2));
        using(s, FighterDailyRoutineManager.Activity.MEDITATION, ki > 68 ? 24 : ki > 48 ? 11 : ki < 22 ? -12 : 0);
        using(s, FighterDailyRoutineManager.Activity.SOCIALIZING, social > 66 ? 28 : social > 48 ? 12 : social < 20 ? -10 : 0);
        using(s, FighterDailyRoutineManager.Activity.MEETING_UP, social > 72 ? 31 : social > 52 ? 13 : -2);
        using(s, FighterDailyRoutineManager.Activity.WALK_TOGETHER, social > 62 ? 17 : 0);
        if (restless > 62) {
            using(s, FighterDailyRoutineManager.Activity.WALKING, 18); using(s, FighterDailyRoutineManager.Activity.SCOUTING, 15);
            using(s, FighterDailyRoutineManager.Activity.FISHING, 10); using(s, FighterDailyRoutineManager.Activity.FLOWER, 8);
        }
        if (fatigue > 68) {
            using(s, FighterDailyRoutineManager.Activity.TRAINING, -42); using(s, FighterDailyRoutineManager.Activity.STRENGTH_TRAINING, -1000);
            using(s, FighterDailyRoutineManager.Activity.KI_TRAINING, -36); using(s, FighterDailyRoutineManager.Activity.SPARRING, -52);
        }
        switch (intent(fighter)) {
            case RECOVER -> { using(s, FighterDailyRoutineManager.Activity.REST, 28); using(s, FighterDailyRoutineManager.Activity.NAP, 22); using(s, FighterDailyRoutineManager.Activity.TRAINING, -24); }
            case REFUEL -> { using(s, FighterDailyRoutineManager.Activity.EATING, 28); using(s, FighterDailyRoutineManager.Activity.FOOD_GATHERING, 16); }
            case SERIOUS_TRAINING -> { using(s, FighterDailyRoutineManager.Activity.TRAINING, 24); using(s, FighterDailyRoutineManager.Activity.TRAINING, 20); using(s, FighterDailyRoutineManager.Activity.SPARRING, 13); }
            case KI_DEVELOPMENT -> { using(s, FighterDailyRoutineManager.Activity.KI_TRAINING, 29); using(s, FighterDailyRoutineManager.Activity.MEDITATION, 13); }
            case SOCIAL_DAY -> { using(s, FighterDailyRoutineManager.Activity.MEETING_UP, 26); using(s, FighterDailyRoutineManager.Activity.SOCIALIZING, 20); using(s, FighterDailyRoutineManager.Activity.WALK_TOGETHER, 16); }
            case EXPLORATION -> { using(s, FighterDailyRoutineManager.Activity.WALKING, 22); using(s, FighterDailyRoutineManager.Activity.SCOUTING, 20); using(s, FighterDailyRoutineManager.Activity.FLIGHT, 10); }
            case RESEARCH -> using(s, FighterDailyRoutineManager.Activity.SCIENTIST_RESEARCH, 48);
            case BALANCED -> { }
        }
    }

    /** Live plans are intentions, not prison bars: urgent consequences may replace the next ordinary block. */
    public static FighterDailyRoutineManager.Activity adaptiveChoice(AmbientFighterEntity fighter, FighterDailyRoutineManager.Activity planned) {
        if (fighter == null || planned == null || WorldMenaceManager.isWorldMenace(fighter)) return planned;
        CompoundTag d = fighter.getLegacyData(); ensureDefaults(fighter, d, fighter.level().getGameTime());
        // Deliberate deep meditation, a real meal, sparring and a hunt already in the plan remain commitments.
        if (planned == FighterDailyRoutineManager.Activity.MEDITATION || planned == FighterDailyRoutineManager.Activity.EATING
                || planned == FighterDailyRoutineManager.Activity.SPARRING || planned == FighterDailyRoutineManager.Activity.FOOD_GATHERING) return planned;
        double hunger=value(d,K_HUNGER), fatigue=value(d,K_FATIGUE), ki=value(d,K_KI_STRAIN), social=value(d,K_SOCIAL);
        if (planned == FighterDailyRoutineManager.Activity.NAP && postSparAwake(fighter)) return FighterDailyRoutineManager.Activity.REST;
        if (fatigue >= 88.0D) return !postSparAwake(fighter) && fighter.getRandom().nextFloat() < 0.26F
                ? FighterDailyRoutineManager.Activity.NAP : FighterDailyRoutineManager.Activity.REST;
        if (hunger >= 86.0D) return FighterDailyRoutineManager.Activity.EATING;
        if (ki >= 88.0D && !FighterScientistManager.isScientist(fighter)) return FighterDailyRoutineManager.Activity.MEDITATION;
        if (social >= 90.0D && FighterNpcSocialManager.meaningfulBondCount(fighter) > 0) return FighterDailyRoutineManager.Activity.MEETING_UP;
        return planned;
    }

    /** Carry cause/effect forward while a remembered fighter is abstract/off-screen. */
    public static void simulateRememberedDay(CompoundTag profile, UUID identity) {
        if (profile == null || !profile.contains("Legacy", Tag.TAG_COMPOUND)) return;
        CompoundTag d = profile.getCompound("Legacy").copy();
        if (profile.getBoolean(WorldMenaceManager.HEROBRINE_TAG) || profile.getBoolean(RedRibbonExperimentManager.TAG)) {
            clearCivilianNeeds(d);
            profile.put("Legacy", d);
            return;
        }
        if (!d.contains(K_SCHEMA, Tag.TAG_ANY_NUMERIC)) {
            long seed = identity == null ? 0x51A7L : identity.getMostSignificantBits() ^ Long.rotateLeft(identity.getLeastSignificantBits(),21);
            d.putInt(K_SCHEMA,SCHEMA); d.putLong(K_LAST,0L);
            put(d,K_HUNGER,28+Math.floorMod(seed,24)); put(d,K_FATIGUE,18+Math.floorMod(seed>>>7,22));
            put(d,K_KI_STRAIN,12+Math.floorMod(seed>>>13,20)); put(d,K_SOCIAL,22+Math.floorMod(seed>>>19,26)); put(d,K_RESTLESS,20+Math.floorMod(seed>>>27,28));
        }
        // One day passes regardless of schedule. Then each real plan block pushes the state in its causal direction.
        add(d,K_HUNGER,24); add(d,K_FATIGUE,12); add(d,K_SOCIAL,10); add(d,K_RESTLESS,8); add(d,K_KI_STRAIN,-5);
        net.minecraft.nbt.ListTag plan=d.getList("LWDailyRoutinePlan",Tag.TAG_STRING);
        for(int i=0;i<plan.size();i++){
            String a=plan.getString(i);
            switch(a){
                case "EATING","TREE" -> add(d,K_HUNGER,-28);
                case "NAP" -> {add(d,K_FATIGUE,-24);add(d,K_KI_STRAIN,-5);}
                case "REST","SITTING" -> {add(d,K_FATIGUE,-13);add(d,K_KI_STRAIN,-4);}
                case "TRAINING","STRENGTH_TRAINING","SPARRING" -> {add(d,K_FATIGUE,14);add(d,K_HUNGER,8);add(d,K_RESTLESS,-10);}
                case "KI_TRAINING" -> {add(d,K_FATIGUE,9);add(d,K_HUNGER,6);add(d,K_KI_STRAIN,14);add(d,K_RESTLESS,-10);}
                case "MEDITATION" -> {add(d,K_KI_STRAIN,-20);add(d,K_FATIGUE,-7);add(d,K_RESTLESS,-8);}
                case "SOCIALIZING","HANGING_OUT","WALK_TOGETHER","MEETING_UP" -> {add(d,K_SOCIAL,-22);add(d,K_RESTLESS,-8);}
                case "WALKING","SCOUTING","FISHING","FLOWER","STARGAZING","DANCING" -> add(d,K_RESTLESS,-9);
                default -> {}
            }
        }
        d.remove(K_LAST_DAY); d.remove(K_INTENT); // tomorrow is chosen from what today actually caused
        profile.put("Legacy",d);
    }

    public static java.util.List<String> profileLines(AmbientFighterEntity fighter) {
        if (fighter == null || WorldMenaceManager.isWorldMenace(fighter)) return java.util.List.of();
        CompoundTag d = fighter.getLegacyData(); ensureDefaults(fighter, d, fighter.level().getGameTime());
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("## What is driving today");
        out.add("* " + intentLabel(fighter));
        String state = dominantState(d);
        if (!state.isBlank()) out.add(". Current pressure: " + state);
        out.add("~ Activities change these pressures; tomorrow's routine remembers today's consequences.");
        return out;
    }

    private static Intent chooseIntent(AmbientFighterEntity f, CompoundTag d) {
        if (FighterScientistManager.isScientist(f) && f.getRandom().nextFloat() < 0.52F) return Intent.RESEARCH;
        double hunger=value(d,K_HUNGER), fatigue=value(d,K_FATIGUE), ki=value(d,K_KI_STRAIN), social=value(d,K_SOCIAL), rest=value(d,K_RESTLESS);
        if (fatigue >= 68 || f.getHealth() < f.getMaxHealth()*0.58F) return Intent.RECOVER;
        if (hunger >= 72) return Intent.REFUEL;
        if (social >= 70 && FighterNpcSocialManager.meaningfulBondCount(f) > 0) return Intent.SOCIAL_DAY;
        if (ki >= 60 || f.getArchetype() == com.dmzlivingworld.entity.FighterArchetype.KI_SPECIALIST) return Intent.KI_DEVELOPMENT;
        int pressure = WorldPowerScaler.trainingPressure(f);
        if (pressure >= 1 || f.getPersonality() == FighterPersonality.PROUD || f.getPersonality() == FighterPersonality.AGGRESSIVE) return Intent.SERIOUS_TRAINING;
        if (rest >= 58) return Intent.EXPLORATION;
        return Intent.BALANCED;
    }

    private static String dominantState(CompoundTag d) {
        double h=value(d,K_HUNGER), f=value(d,K_FATIGUE), k=value(d,K_KI_STRAIN), s=value(d,K_SOCIAL), r=value(d,K_RESTLESS);
        double max=Math.max(h,Math.max(f,Math.max(k,Math.max(s,r))));
        if (max < 48) return "comfortable / no urgent need";
        if (max==h) return h>72?"hungry":"getting hungry";
        if (max==f) return f>72?"very tired":"moderately tired";
        if (max==k) return k>72?"Ki-strained":"needs Ki recovery";
        if (max==s) return s>72?"has gone too long without company":"wants social contact";
        return r>72?"restless / bored":"wants a change of pace";
    }

    private static void ensureDefaults(AmbientFighterEntity fighter, CompoundTag d, long now) {
        if (d.getInt(K_SCHEMA) == SCHEMA && d.contains(K_LAST, Tag.TAG_ANY_NUMERIC)) return;
        long seed = fighter.getUUID().getMostSignificantBits() ^ Long.rotateLeft(fighter.getUUID().getLeastSignificantBits(), 21);
        d.putInt(K_SCHEMA, SCHEMA); d.putLong(K_LAST, now);
        put(d,K_HUNGER,28 + Math.floorMod(seed,24)); put(d,K_FATIGUE,18 + Math.floorMod(seed>>>7,22));
        put(d,K_KI_STRAIN,12 + Math.floorMod(seed>>>13,20)); put(d,K_SOCIAL,22 + Math.floorMod(seed>>>19,26)); put(d,K_RESTLESS,20 + Math.floorMod(seed>>>27,28));
    }
    private static void clearCivilianNeeds(CompoundTag d) {
        if (d == null) return;
        d.remove(K_SCHEMA); d.remove(K_LAST); d.remove(K_HUNGER); d.remove(K_FATIGUE);
        d.remove(K_KI_STRAIN); d.remove(K_SOCIAL); d.remove(K_RESTLESS); d.remove(K_LAST_DAY); d.remove(K_INTENT);
        d.remove(K_POST_SPAR_AWAKE_UNTIL);
    }
    private static double value(CompoundTag d,String k){ return clamp(d.getDouble(k)); }
    private static void add(CompoundTag d,String k,double v){ put(d,k,value(d,k)+v); }
    private static void put(CompoundTag d,String k,double v){ d.putDouble(k,clamp(v)); }
    private static double clamp(double v){ return Math.max(0.0D,Math.min(100.0D,v)); }
    private static void using(EnumMap<FighterDailyRoutineManager.Activity,Integer>s,FighterDailyRoutineManager.Activity a,int n){ if(n!=0)s.put(a,s.getOrDefault(a,0)+n); }
}
