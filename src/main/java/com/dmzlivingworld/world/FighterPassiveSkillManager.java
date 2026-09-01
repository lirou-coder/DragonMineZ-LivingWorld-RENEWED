package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterArchetype;
import com.dmzlivingworld.entity.FighterPersonality;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * Quiet learned passives for Living World fighters.
 *
 * Study is theory: it discovers/understands one discipline. Real Training or Ki Control then
 * supplies practice. Nothing unlocks merely because a menu was opened or time passed off-screen.
 */
public final class FighterPassiveSkillManager {
    private static final String ROOT = "LWPassiveSkillsV1";
    private static final String PRACTICE = "LWPassivePracticeV1";
    private static final String STUDY_TARGET = "LWPassiveStudyTarget";
    private static final String STUDY_PROGRESS = "LWPassiveStudyProgress";

    public enum Skill {
        POTENTIAL_UNLOCK("Potential Unlock", "earned growth", Practice.BOTH, 3),
        KI_BOOST("Ki Boost", "Ki output", Practice.KI, 3),
        KI_INFUSION("Ki Infusion", "melee impact", Practice.TRAINING, 3),
        SPRINT("Sprint", "movement speed", Practice.TRAINING, 3),
        ENDURANCE("Endurance", "durability", Practice.TRAINING, 4),
        KI_EFFICIENCY("Ki Efficiency", "Ki-control development", Practice.KI, 4);

        private final String label;
        private final String effect;
        private final Practice practice;
        private final int sessions;
        Skill(String label, String effect, Practice practice, int sessions) {
            this.label = label; this.effect = effect; this.practice = practice; this.sessions = sessions;
        }
        public String label() { return label; }
        public String effect() { return effect; }
    }
    private enum Practice { TRAINING, KI, BOTH }

    private FighterPassiveSkillManager() {}

    private static CompoundTag states(AmbientFighterEntity fighter) {
        CompoundTag legacy = fighter.getLegacyData();
        if (!legacy.contains(ROOT, net.minecraft.nbt.Tag.TAG_COMPOUND)) legacy.put(ROOT, new CompoundTag());
        return legacy.getCompound(ROOT);
    }
    private static CompoundTag practice(AmbientFighterEntity fighter) {
        CompoundTag legacy = fighter.getLegacyData();
        if (!legacy.contains(PRACTICE, net.minecraft.nbt.Tag.TAG_COMPOUND)) legacy.put(PRACTICE, new CompoundTag());
        return legacy.getCompound(PRACTICE);
    }

    /** 0 unknown, 1 understood/theory learned, 2 unlocked. */
    public static int state(AmbientFighterEntity fighter, Skill skill) {
        if (fighter == null || skill == null || WorldMenaceManager.isWorldMenace(fighter)) return 0;
        return Math.max(0, Math.min(2, states(fighter).getInt(skill.name())));
    }
    public static boolean unlocked(AmbientFighterEntity fighter, Skill skill) { return state(fighter, skill) >= 2; }

    public static void onStudyCompleted(AmbientFighterEntity fighter, int effortTicks) {
        if (fighter == null || effortTicks < 100 || WorldMenaceManager.isWorldMenace(fighter)) return;
        CompoundTag legacy = fighter.getLegacyData();
        Skill target = parse(legacy.getString(STUDY_TARGET));
        if (target == null || state(fighter, target) != 0) {
            target = chooseNextStudy(fighter);
            if (target == null) return;
            legacy.putString(STUDY_TARGET, target.name());
            legacy.putInt(STUDY_PROGRESS, 0);
        }
        int progress = Math.min(9, legacy.getInt(STUDY_PROGRESS) + 1);
        int needed = studySessionsNeeded(fighter, target);
        if (progress >= needed) {
            CompoundTag states = states(fighter);
            states.putInt(target.name(), 1);
            fighter.getLegacyData().put(ROOT, states);
            legacy.remove(STUDY_TARGET);
            legacy.remove(STUDY_PROGRESS);
            fighter.recordLegacyEvent("Understood the theory behind " + target.label());
            FighterBattleAdaptationManager.onTheoryUnderstood(fighter, target);
            if (fighter.getSpeech().isEmpty()) fighter.speak("I think I understand " + target.label() + ". Now I need to make it work in practice.", 84);
        } else {
            legacy.putInt(STUDY_PROGRESS, progress);
        }
    }

    public static void onPracticeCompleted(AmbientFighterEntity fighter, FighterAmbientActivityManager.Type type, int effortTicks) {
        if (fighter == null || type == null || effortTicks < 120 || WorldMenaceManager.isWorldMenace(fighter)) return;
        boolean ki = type == FighterAmbientActivityManager.Type.KI_TRAINING;
        boolean training = type == FighterAmbientActivityManager.Type.TRAINING;
        if (!ki && !training) return;
        CompoundTag practice = practice(fighter);
        for (Skill skill : Skill.values()) {
            if (state(fighter, skill) != 1) continue;
            if (!matches(skill.practice, ki)) continue;
            int count = Math.min(99, practice.getInt(skill.name()) + 1);
            practice.putInt(skill.name(), count);
            if (count >= skill.sessions) unlock(fighter, skill);
        }
        fighter.getLegacyData().put(PRACTICE, practice);
    }

    private static boolean matches(Practice practice, boolean ki) {
        return practice == Practice.BOTH || (practice == Practice.KI && ki) || (practice == Practice.TRAINING && !ki);
    }

    private static void unlock(AmbientFighterEntity fighter, Skill skill) {
        CompoundTag states = states(fighter);
        states.putInt(skill.name(), 2);
        fighter.getLegacyData().put(ROOT, states);
        CompoundTag p = practice(fighter); p.remove(skill.name()); fighter.getLegacyData().put(PRACTICE, p);
        fighter.recordLegacyEvent("Unlocked passive skill: " + skill.label());
        if (fighter.getSpeech().isEmpty()) fighter.speak(skill.label() + " finally feels natural.", 78);
        // Passive physical benefits are composed into the normal BP-backed profile; no parallel stat layer.
        fighter.refreshCombatStatsFromPower();
    }

    private static int studySessionsNeeded(AmbientFighterEntity fighter, Skill skill) {
        int base = 2;
        if (fighter.getPersonality() == FighterPersonality.CAUTIOUS || fighter.getPersonality() == FighterPersonality.CALM) base--;
        if (skill == Skill.POTENTIAL_UNLOCK || skill == Skill.KI_EFFICIENCY) base++;
        return Math.max(1, Math.min(3, base));
    }

    private static Skill chooseNextStudy(AmbientFighterEntity fighter) {
        Skill battleLesson = FighterBattleAdaptationManager.preferredStudy(fighter);
        if (battleLesson != null && state(fighter, battleLesson) == 0) return battleLesson;
        List<Skill> unknown = new ArrayList<>();
        for (Skill skill : preferredOrder(fighter)) if (state(fighter, skill) == 0 && !unknown.contains(skill)) unknown.add(skill);
        return unknown.isEmpty() ? null : unknown.get(Math.floorMod(fighter.getUUID().hashCode() + fighter.getTrainingSessions(), unknown.size()));
    }

    private static List<Skill> preferredOrder(AmbientFighterEntity fighter) {
        List<Skill> order = new ArrayList<>();
        FighterArchetype a = fighter.getArchetype();
        switch (a) {
            case KI_SPECIALIST -> { order.add(Skill.KI_BOOST); order.add(Skill.KI_EFFICIENCY); order.add(Skill.POTENTIAL_UNLOCK); }
            case SPEEDSTER -> { order.add(Skill.SPRINT); order.add(Skill.KI_INFUSION); order.add(Skill.POTENTIAL_UNLOCK); }
            case BRAWLER -> { order.add(Skill.KI_INFUSION); order.add(Skill.ENDURANCE); order.add(Skill.POTENTIAL_UNLOCK); }
            case GUARDIAN -> { order.add(Skill.ENDURANCE); order.add(Skill.KI_INFUSION); order.add(Skill.KI_EFFICIENCY); }
            case MARTIAL_ARTIST -> { order.add(Skill.POTENTIAL_UNLOCK); order.add(Skill.KI_INFUSION); order.add(Skill.SPRINT); }
        }
        for (Skill s : Skill.values()) if (!order.contains(s)) order.add(s);
        return order;
    }

    private static Skill parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return Skill.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public static void addStudyBias(AmbientFighterEntity fighter, EnumMap<FighterDailyRoutineManager.Activity, Integer> scores) {
        if (fighter == null || scores == null || WorldMenaceManager.isWorldMenace(fighter)) return;
        int unknown = 0, understood = 0;
        for (Skill skill : Skill.values()) {
            int state = state(fighter, skill);
            if (state == 0) unknown++; else if (state == 1) understood++;
        }
        if (unknown > 0) scores.put(FighterDailyRoutineManager.Activity.STUDYING,
                scores.getOrDefault(FighterDailyRoutineManager.Activity.STUDYING, 0) + (understood == 0 ? 14 : 7));
        if (understood > 0) {
            scores.put(FighterDailyRoutineManager.Activity.TRAINING,
                    scores.getOrDefault(FighterDailyRoutineManager.Activity.TRAINING, 0) + 8);
            scores.put(FighterDailyRoutineManager.Activity.KI_TRAINING,
                    scores.getOrDefault(FighterDailyRoutineManager.Activity.KI_TRAINING, 0) + 7);
        }
    }

    public static boolean hasUnstudiedSkill(AmbientFighterEntity fighter) {
        if (fighter == null || WorldMenaceManager.isWorldMenace(fighter)) return false;
        for (Skill skill : Skill.values()) if (state(fighter, skill) == 0) return true;
        return false;
    }

    public static double growthMultiplier(AmbientFighterEntity fighter) {
        if (fighter == null || WorldMenaceManager.isWorldMenace(fighter)) return 1.0D;
        double m = 1.0D;
        if (unlocked(fighter, Skill.POTENTIAL_UNLOCK)) m *= 1.10D;
        if (unlocked(fighter, Skill.KI_EFFICIENCY)) m *= 1.04D;
        return m;
    }
    public static double meleeMultiplier(AmbientFighterEntity fighter) {
        return unlocked(fighter, Skill.KI_INFUSION) ? 1.08D : 1.0D;
    }
    public static double kiMultiplier(AmbientFighterEntity fighter) {
        double m = unlocked(fighter, Skill.KI_BOOST) ? 1.10D : 1.0D;
        if (unlocked(fighter, Skill.KI_EFFICIENCY)) m *= 1.04D;
        return m;
    }
    public static double speedMultiplier(AmbientFighterEntity fighter) {
        return unlocked(fighter, Skill.SPRINT) ? 1.08D : 1.0D;
    }
    public static double healthMultiplier(AmbientFighterEntity fighter) {
        return unlocked(fighter, Skill.ENDURANCE) ? 1.08D : 1.0D;
    }

    public static List<String> profileLines(AmbientFighterEntity fighter) {
        if (fighter == null || WorldMenaceManager.isWorldMenace(fighter)) return List.of();
        List<String> out = new ArrayList<>();
        out.add("## Passive Skills");
        CompoundTag p = practice(fighter);
        boolean any = false;
        for (Skill skill : Skill.values()) {
            int state = state(fighter, skill);
            if (state == 2) { out.add("+ " + skill.label() + " • unlocked • " + skill.effect()); any = true; }
            else if (state == 1) { out.add("~ " + skill.label() + " • understood • practice " + p.getInt(skill.name()) + "/" + skill.sessions); any = true; }
        }
        Skill studying = parse(fighter.getLegacyData().getString(STUDY_TARGET));
        if (studying != null && state(fighter, studying) == 0) {
            out.add(". Studying: " + studying.label() + " • theory " + fighter.getLegacyData().getInt(STUDY_PROGRESS) + "/" + studySessionsNeeded(fighter, studying));
            any = true;
        }
        if (!any) out.add(". No passive discipline understood yet. Studying can reveal one to practice.");
        String lesson = FighterBattleAdaptationManager.summary(fighter);
        if (!lesson.isBlank()) out.add("~ " + lesson);
        out.add("~ Theory comes from Study; understood skills unlock only through real Training or Ki Control practice.");
        return out;
    }
}
