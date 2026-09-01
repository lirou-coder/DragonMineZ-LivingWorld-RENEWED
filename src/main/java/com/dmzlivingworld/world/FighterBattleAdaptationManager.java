package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterArchetype;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;

/**
 * Small bridge from real battle experience into the existing Study -> practice -> passive loop.
 * It never grants a skill directly: repeated problems only bias what the fighter decides to study.
 */
public final class FighterBattleAdaptationManager {
    private static final String ROOT = "LWBattleLessonsV1";
    private static final String LAST = "LWBattleLessonLast";

    private FighterBattleAdaptationManager() {}

    private static CompoundTag lessons(AmbientFighterEntity fighter) {
        CompoundTag legacy = fighter.getLegacyData();
        if (!legacy.contains(ROOT, net.minecraft.nbt.Tag.TAG_COMPOUND)) legacy.put(ROOT, new CompoundTag());
        return legacy.getCompound(ROOT);
    }

    public static void noteOutcome(AmbientFighterEntity learner, AmbientFighterEntity opponent, boolean won, boolean lethal) {
        if (learner == null || opponent == null || won || WorldMenaceManager.isWorldMenace(learner)) return;
        CompoundTag d = lessons(learner);
        double ratio = opponent.getBattlePower() / (double)Math.max(1, learner.getBattlePower());
        if (ratio >= 1.22D) add(d, FighterPassiveSkillManager.Skill.POTENTIAL_UNLOCK, ratio >= 1.75D ? 3 : 2);
        if (lethal) add(d, FighterPassiveSkillManager.Skill.ENDURANCE, 2);

        FighterArchetype archetype = opponent.getArchetype();
        switch (archetype) {
            case SPEEDSTER -> add(d, FighterPassiveSkillManager.Skill.SPRINT, 3);
            case KI_SPECIALIST -> {
                add(d, FighterPassiveSkillManager.Skill.KI_EFFICIENCY, 2);
                add(d, FighterPassiveSkillManager.Skill.KI_BOOST, 1);
            }
            case BRAWLER -> {
                add(d, FighterPassiveSkillManager.Skill.ENDURANCE, 2);
                add(d, FighterPassiveSkillManager.Skill.KI_INFUSION, 1);
            }
            case GUARDIAN -> {
                add(d, FighterPassiveSkillManager.Skill.KI_BOOST, 2);
                add(d, FighterPassiveSkillManager.Skill.KI_INFUSION, 1);
            }
            case MARTIAL_ARTIST -> {
                add(d, FighterPassiveSkillManager.Skill.KI_INFUSION, 2);
                add(d, FighterPassiveSkillManager.Skill.SPRINT, 1);
            }
        }
        if (FighterPassiveSkillManager.unlocked(opponent, FighterPassiveSkillManager.Skill.SPRINT))
            add(d, FighterPassiveSkillManager.Skill.SPRINT, 1);
        if (FighterPassiveSkillManager.unlocked(opponent, FighterPassiveSkillManager.Skill.KI_BOOST))
            add(d, FighterPassiveSkillManager.Skill.KI_EFFICIENCY, 1);
        learner.getLegacyData().put(ROOT, d);
        rememberTopLesson(learner);
    }

    public static void notePlayerOutcome(AmbientFighterEntity learner, ServerPlayer opponent, boolean won, boolean lethal) {
        if (learner == null || opponent == null || won || WorldMenaceManager.isWorldMenace(learner)) return;
        CompoundTag d = lessons(learner);
        double ratio = PlayerWorldManager.playerBattlePower(opponent) / Math.max(1.0D, learner.getBattlePower());
        if (ratio >= 1.18D) add(d, FighterPassiveSkillManager.Skill.POTENTIAL_UNLOCK, ratio >= 1.75D ? 3 : 2);
        // Against an unknown player style, do not invent a fake diagnosis. A hard loss teaches
        // survivability first; repeated Study still chooses the fighter's normal archetype choices
        // if this pressure never becomes dominant.
        add(d, FighterPassiveSkillManager.Skill.ENDURANCE, lethal ? 2 : 1);
        learner.getLegacyData().put(ROOT, d);
        rememberTopLesson(learner);
    }

    /** Returns an unknown skill only after battle evidence is strong enough to be meaningful. */
    public static FighterPassiveSkillManager.Skill preferredStudy(AmbientFighterEntity fighter) {
        if (fighter == null || WorldMenaceManager.isWorldMenace(fighter)) return null;
        CompoundTag d = lessons(fighter);
        return List.of(FighterPassiveSkillManager.Skill.values()).stream()
                .filter(s -> FighterPassiveSkillManager.state(fighter, s) == 0)
                .filter(s -> d.getInt(s.name()) >= 3)
                .max(Comparator.<FighterPassiveSkillManager.Skill>comparingInt(s -> d.getInt(s.name()))
                        .thenComparingInt(s -> s.ordinal()))
                .orElse(null);
    }

    public static void onTheoryUnderstood(AmbientFighterEntity fighter, FighterPassiveSkillManager.Skill skill) {
        if (fighter == null || skill == null) return;
        CompoundTag d = lessons(fighter);
        int left = Math.max(0, d.getInt(skill.name()) - 3);
        if (left == 0) d.remove(skill.name()); else d.putInt(skill.name(), left);
        fighter.getLegacyData().put(ROOT, d);
        rememberTopLesson(fighter);
    }

    public static String summary(AmbientFighterEntity fighter) {
        FighterPassiveSkillManager.Skill skill = preferredStudy(fighter);
        if (skill == null) return "";
        return "Battle lesson • repeated fights are pushing Study toward " + skill.label();
    }

    private static void rememberTopLesson(AmbientFighterEntity fighter) {
        FighterPassiveSkillManager.Skill skill = preferredStudy(fighter);
        if (skill == null) fighter.getLegacyData().remove(LAST);
        else fighter.getLegacyData().putString(LAST, skill.name());
    }

    private static void add(CompoundTag d, FighterPassiveSkillManager.Skill skill, int amount) {
        d.putInt(skill.name(), Math.min(12, Math.max(0, d.getInt(skill.name()) + amount)));
    }
}
