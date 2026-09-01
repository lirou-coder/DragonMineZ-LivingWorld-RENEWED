package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterRank;
import com.dmzlivingworld.entity.RacialFormProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Concrete fighter ambitions. Goals are selected from things this individual can
 * actually accomplish with existing LW/DMZ mechanics; they do not fabricate story
 * progress or silently modify stats to satisfy themselves.
 */
public final class FighterGoalManager {
    private static final String TYPE = "GoalType";
    private static final String TARGET = "GoalTarget";
    private static final String BASE_TRAINING = "GoalTrainingBase";
    private static final String BASE_RACIAL = "GoalRacialBase";
    private static final String BASE_TECHNIQUES = "GoalTechniqueBase";
    private static final String BASE_WINS = "GoalWinsBase";
    private static final String BASE_FUSIONS = "GoalFusionsBase";
    private static final String TARGET_COUNT = "GoalTargetCount";
    private static final String TARGET_POWER = "GoalTargetPower";
    private static final String COOLDOWN = "GoalCooldownUntil";
    private static final String COMPLETED = "GoalsCompleted";
    private static final String LAST_TYPE = "LastGoalType";
    private static final String LAST_RESULT = "LastGoalResult";
    private static final String SCHEMA_KEY = "GoalSchema";
    private static final int SCHEMA = 5;

    private FighterGoalManager() {}

    private record Option(String type, int weight, String target) {}

    public static void tick(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || fighter.isDefeated() || fighter.isCaptive()
                || fighter.getPersistentData().contains("DMZLWNpcFusionTemp")) return;
        if (FactionRequestMissionManager.isExclusiveFieldAssignment(fighter)) return;
        if (!fighter.isRemembered() && !fighter.isFactionMember()) return;
        if (fighter.tickCount % 200 != Math.floorMod(fighter.getUUID().hashCode(), 200)) return;

        CompoundTag l = fighter.getLegacyData();
        migrateGoalSchema(l);
        long now = fighter.level().getGameTime();
        if (l.getString(TYPE).isBlank()) {
            if (now < l.getLong(COOLDOWN)) return;
            assignGoal(fighter);
        }
        evaluate(fighter);

        String type = l.getString(TYPE);
        if ("DEFEAT_RIVAL".equals(type) && fighter.getTarget() == null && !fighter.getRivalName().isBlank()) {
            AmbientFighterEntity rival = findNamedNearby(fighter, fighter.getRivalName(), 26.0D);
            if (rival != null && rival.getTarget() == null && !rival.isDefeated() && !rival.isCaptive()
                    && fighter.getRandom().nextFloat() < 0.08F) {
                fighter.setTarget(rival);
                rival.setTarget(fighter);
            }
        } else if ("ACQUIRE_EQUIPMENT".equals(type) && fighter.getTarget() == null) {
            FighterArsenalManager.tryPickupNearby(fighter);
        } else if (("TRAIN".equals(type) || "ADVANCE_RACIAL".equals(type) || "LEARN_FLIGHT".equals(type))
                && fighter.getTarget() == null && !fighter.isMeditating() && fighter.getRandom().nextFloat() < 0.025F) {
            fighter.beginMeditation(AmbientFighterEntity.naturalMeditationDuration(fighter.getRandom(), 700));
        }
    }

    public static String summary(AmbientFighterEntity fighter) {
        if (fighter == null) return "none";
        ensureAssignedForInspection(fighter);
        return summaryStored(fighter);
    }

    /** Read-only version used by remembered snapshots; never invents a new goal while inspecting history. */
    public static String summaryStored(AmbientFighterEntity fighter) {
        if (fighter == null) return "none";
        CompoundTag l = fighter.getLegacyData();
        String type = l.getString(TYPE);
        String target = l.getString(TARGET);
        return switch (type) {
            case "DEFEAT_RIVAL" -> target.isBlank() ? "Defeat a rival" : "Defeat " + target;
            case "LEARN_TECHNIQUE" -> "Learn a new technique";
            case "ACQUIRE_EQUIPMENT" -> target.isBlank() ? "Acquire useful equipment" : "Acquire " + target;
            case "ADVANCE_RACIAL" -> "Advance racial training";
            case "LEARN_FLIGHT" -> "Learn to fly";
            case "WIN_FIGHTS" -> "Win " + Math.max(1, l.getInt(TARGET_COUNT)) + " meaningful fights";
            case "DEFEAT_STRONGER" -> "Defeat a fighter above PL " + Math.max(1, l.getInt(TARGET_POWER));
            case "FUSION" -> "Perform another fusion";
            case "TRAIN" -> "Complete " + Math.max(1, l.getInt(TARGET_COUNT)) + " serious training sessions";
            default -> "none";
        };
    }

    /** Current stored goal type for systems that want to react to this fighter's actual life.
     * This never assigns a new goal and is therefore safe for contextual interactions. */
    public static String currentType(AmbientFighterEntity fighter) {
        return fighter == null ? "" : fighter.getLegacyData().getString(TYPE);
    }

    /** Current stored goal target, if that goal names a specific person/item. */
    public static String currentTarget(AmbientFighterEntity fighter) {
        return fighter == null ? "" : fighter.getLegacyData().getString(TARGET);
    }

    public static String progress(AmbientFighterEntity fighter) {
        if (fighter == null) return "";
        ensureAssignedForInspection(fighter);
        return progressStored(fighter);
    }

    /** Read-only remembered-snapshot progress; like summaryStored, it never assigns anything. */
    public static String progressStored(AmbientFighterEntity fighter) {
        if (fighter == null) return "";
        CompoundTag l = fighter.getLegacyData();
        return switch (l.getString(TYPE)) {
            case "TRAIN" -> progressCount(fighter.getTrainingSessions() - l.getInt(BASE_TRAINING), l.getInt(TARGET_COUNT));
            case "LEARN_TECHNIQUE" -> progressCount(FighterTechniqueManager.learnedCount(fighter) - l.getInt(BASE_TECHNIQUES), 1);
            case "ACQUIRE_EQUIPMENT" -> FighterArsenalManager.satisfiesEquipmentGoal(fighter, l.getString(TARGET)) ? "1/1" : "0/1";
            case "ADVANCE_RACIAL" -> Math.max(0, fighter.getRacialSkillLevel() - l.getInt(BASE_RACIAL)) + "/1";
            case "LEARN_FLIGHT" -> fighter.hasFlightUnlocked() ? "1/1" : "0/1";
            case "WIN_FIGHTS" -> progressCount(fighter.getLegacyData().getInt("Wins") - l.getInt(BASE_WINS), l.getInt(TARGET_COUNT));
            case "DEFEAT_STRONGER", "DEFEAT_RIVAL" -> "0/1";
            case "FUSION" -> progressCount(fighter.getLegacyData().getInt("Fusions") - l.getInt(BASE_FUSIONS), 1);
            default -> "";
        };
    }

    private static String progressCount(int current, int target) {
        int max = Math.max(1, target);
        return Math.max(0, Math.min(max, current)) + "/" + max;
    }

    public static String lastCompleted(AmbientFighterEntity fighter) {
        if (fighter == null) return "none";
        String result = fighter.getLegacyData().getString(LAST_RESULT);
        return result.isBlank() ? "none" : result;
    }

    public static int completedCount(AmbientFighterEntity fighter) {
        return fighter == null ? 0 : Math.max(0, fighter.getLegacyData().getInt(COMPLETED));
    }

    public static void onTraining(AmbientFighterEntity fighter) { evaluate(fighter); }
    public static void onTechniqueLearned(AmbientFighterEntity fighter, String technique) { evaluate(fighter); }
    public static void onEquipmentAcquired(AmbientFighterEntity fighter, String item) { evaluate(fighter); }
    public static void onRacialAdvanced(AmbientFighterEntity fighter) { evaluate(fighter); }
    public static void onFusion(AmbientFighterEntity fighter) { evaluate(fighter); }

    public static void onBattleVictory(AmbientFighterEntity fighter, AmbientFighterEntity defeated) {
        if (fighter == null || defeated == null) return;
        CompoundTag l = fighter.getLegacyData();
        String type = l.getString(TYPE);
        if ("DEFEAT_RIVAL".equals(type) && defeated.getFighterName().equals(l.getString(TARGET))) {
            complete(fighter, "Defeated rival " + defeated.getFighterName());
        } else if ("DEFEAT_STRONGER".equals(type) && defeated.getBattlePower() >= Math.max(1, l.getInt(TARGET_POWER))) {
            complete(fighter, "Defeated stronger fighter " + defeated.getFighterName());
        } else {
            evaluate(fighter);
        }
    }

    private static void ensureAssignedForInspection(AmbientFighterEntity fighter) {
        if (fighter.level().isClientSide) return;
        CompoundTag l = fighter.getLegacyData();
        migrateGoalSchema(l);
        if (l.getString(TYPE).isBlank() && fighter.level().getGameTime() >= l.getLong(COOLDOWN)) assignGoal(fighter);
    }

    private static void assignGoal(AmbientFighterEntity fighter) {
        CompoundTag l = fighter.getLegacyData();
        List<Option> options = new ArrayList<>();

        if (!fighter.getRivalName().isBlank()) options.add(new Option("DEFEAT_RIVAL", 24, fighter.getRivalName()));
        if (FighterTechniqueManager.learnedCount(fighter) < 4) {
            options.add(new Option("LEARN_TECHNIQUE", 9, ""));
        }
        boolean lacksWeapon = !FighterArsenalManager.hasPreferredWeapon(fighter);
        if (lacksWeapon) {
            options.add(new Option("ACQUIRE_EQUIPMENT", 20, preferredEquipmentGoal(fighter)));
        }
        if (!fighter.hasFlightUnlocked() && fighter.getRank() != FighterRank.ROOKIE) options.add(new Option("LEARN_FLIGHT", 13, ""));
        if (fighter.getRank() != FighterRank.ROOKIE
                && fighter.getRacialSkillLevel() < RacialFormProfile.maxSkillLevel(fighter.getRace())) {
            options.add(new Option("ADVANCE_RACIAL", 13, ""));
        }
        options.add(new Option("WIN_FIGHTS", fighter.getRank() == FighterRank.VETERAN ? 18 : 13, ""));
        if (fighter.getRank() != FighterRank.ROOKIE) options.add(new Option("DEFEAT_STRONGER", 12, ""));
        if (l.getInt("Fusions") > 0) options.add(new Option("FUSION", 6, ""));
        options.add(new Option("TRAIN", 3, ""));

        String last = l.getString(LAST_TYPE);
        if (options.size() > 1) options.removeIf(o -> o.type.equals(last));
        int total = options.stream().mapToInt(option -> weightFor(fighter, option)).sum();
        int roll = fighter.getRandom().nextInt(Math.max(1, total));
        Option selected = options.get(options.size() - 1);
        for (Option option : options) {
            roll -= weightFor(fighter, option);
            if (roll < 0) { selected = option; break; }
        }

        l.putInt(SCHEMA_KEY, SCHEMA);
        l.putString(TYPE, selected.type);
        l.putString(TARGET, selected.target == null ? "" : selected.target);
        l.putInt(BASE_TRAINING, fighter.getTrainingSessions());
        l.putInt(BASE_RACIAL, fighter.getRacialSkillLevel());
        l.putInt(BASE_TECHNIQUES, FighterTechniqueManager.learnedCount(fighter));
        l.putInt(BASE_WINS, l.getInt("Wins"));
        l.putInt(BASE_FUSIONS, l.getInt("Fusions"));
        l.putInt(TARGET_COUNT, switch (selected.type) {
            case "TRAIN" -> 2 + fighter.getRandom().nextInt(3);
            case "WIN_FIGHTS" -> 2 + fighter.getRandom().nextInt(3);
            default -> 1;
        });
        if ("DEFEAT_STRONGER".equals(selected.type)) {
            l.putInt(TARGET_POWER, (int)Math.min(Integer.MAX_VALUE - 1L,
                    Math.max(fighter.getBattlePower() + 1L, Math.round(fighter.getBattlePower() * (1.28D + fighter.getRandom().nextDouble() * 0.30D)))));
        } else l.remove(TARGET_POWER);
        FighterMemoryManager.refreshLoadedProfile(fighter);
    }


    /**
     * Individuality comes from state, personality and fighting style rather than a
     * separate roleplay trait system. The option must already be mechanically valid;
     * this only decides which valid ambition matters most to this person right now.
     */
    private static int weightFor(AmbientFighterEntity fighter, Option option) {
        int weight = option.weight;
        switch (option.type) {
            case "DEFEAT_RIVAL" -> {
                weight += switch (fighter.getPersonality()) {
                    case PROUD -> 12; case AGGRESSIVE -> 9; case HEROIC -> 5; case CALM -> 1; case CAUTIOUS -> -5;
                };
            }
            case "LEARN_TECHNIQUE" -> {
                weight += switch (fighter.getPersonality()) {
                    case CALM -> 9; case HEROIC -> 5; case PROUD -> 2; case CAUTIOUS -> 3; case AGGRESSIVE -> -2;
                };
                weight += switch (fighter.getArchetype()) {
                    case MARTIAL_ARTIST -> 7; case KI_SPECIALIST -> 6; case SPEEDSTER -> 2; case GUARDIAN -> 1; case BRAWLER -> -3;
                };
            }
            case "ACQUIRE_EQUIPMENT" -> {
                weight += switch (fighter.getPersonality()) {
                    case CAUTIOUS -> 8; case CALM -> 3; case HEROIC -> 1; case PROUD -> 0; case AGGRESSIVE -> 2;
                };
                weight += switch (fighter.getArchetype()) {
                    case SPEEDSTER, MARTIAL_ARTIST -> 6; case KI_SPECIALIST -> 5; case GUARDIAN -> 4; case BRAWLER -> 0;
                };
            }
            case "LEARN_FLIGHT" -> {
                weight += fighter.getArchetype() == com.dmzlivingworld.entity.FighterArchetype.SPEEDSTER ? 9 : 0;
                if (fighter.getPersonality() == com.dmzlivingworld.entity.FighterPersonality.CAUTIOUS) weight += 4;
            }
            case "ADVANCE_RACIAL" -> {
                if (fighter.getPersonality() == com.dmzlivingworld.entity.FighterPersonality.PROUD) weight += 8;
                if (fighter.getPersonality() == com.dmzlivingworld.entity.FighterPersonality.HEROIC) weight += 4;
            }
            case "WIN_FIGHTS" -> {
                if (fighter.getPersonality() == com.dmzlivingworld.entity.FighterPersonality.AGGRESSIVE) weight += 9;
                if (fighter.getArchetype() == com.dmzlivingworld.entity.FighterArchetype.BRAWLER) weight += 6;
            }
            case "DEFEAT_STRONGER" -> {
                weight += switch (fighter.getPersonality()) {
                    case PROUD -> 12; case AGGRESSIVE -> 7; case HEROIC -> 4; case CALM -> 0; case CAUTIOUS -> -7;
                };
            }
            case "FUSION" -> {
                if (fighter.getPersonality() == com.dmzlivingworld.entity.FighterPersonality.HEROIC) weight += 3;
            }
            case "TRAIN" -> {
                if (fighter.getPersonality() == com.dmzlivingworld.entity.FighterPersonality.CALM) weight += 4;
                if (fighter.getRank() == FighterRank.ROOKIE) weight += 4;
            }
        }

        int wins = Math.max(0, fighter.getLegacyData().getInt("Wins"));
        int losses = Math.max(0, fighter.getLegacyData().getInt("Losses"));
        if (losses >= wins + 2) {
            if ("LEARN_TECHNIQUE".equals(option.type) || "ACQUIRE_EQUIPMENT".equals(option.type) || "TRAIN".equals(option.type)) weight += 4;
            if ("DEFEAT_STRONGER".equals(option.type)) weight -= 4;
        } else if (wins >= losses + 3) {
            if ("DEFEAT_STRONGER".equals(option.type) || "WIN_FIGHTS".equals(option.type)) weight += 3;
        }
        return Math.max(1, weight);
    }

    /** A consequential fight can redirect the loser's existing ambition toward a real rival. */
    public static void focusOnRival(AmbientFighterEntity fighter, String rivalName) {
        if (fighter == null || fighter.level().isClientSide || rivalName == null || rivalName.isBlank()) return;
        CompoundTag l = fighter.getLegacyData();
        migrateGoalSchema(l);
        fighter.setRivalName(rivalName);
        l.putString(TYPE, "DEFEAT_RIVAL");
        l.putString(TARGET, rivalName);
        l.putInt(BASE_TRAINING, fighter.getTrainingSessions());
        l.putInt(BASE_RACIAL, fighter.getRacialSkillLevel());
        l.putInt(BASE_TECHNIQUES, FighterTechniqueManager.learnedCount(fighter));
        l.putInt(BASE_WINS, l.getInt("Wins"));
        l.putInt(BASE_FUSIONS, l.getInt("Fusions"));
        l.putInt(TARGET_COUNT, 1);
        l.remove(TARGET_POWER);
        l.putLong(COOLDOWN, 0L);
        FighterMemoryManager.refreshLoadedProfile(fighter);
    }

    private static String preferredEquipmentGoal(AmbientFighterEntity fighter) {
        return switch (fighter.getArchetype()) {
            case SPEEDSTER -> "a sword";
            case MARTIAL_ARTIST -> fighter.getRandom().nextBoolean() ? "a sword" : "a martial weapon";
            case KI_SPECIALIST -> "a ranged weapon";
            case GUARDIAN -> "a weapon";
            case BRAWLER -> "a weapon";
        };
    }

    private static void evaluate(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide) return;
        CompoundTag l = fighter.getLegacyData();
        migrateGoalSchema(l);
        String type = l.getString(TYPE);
        if (type.isBlank()) return;
        boolean done = switch (type) {
            case "TRAIN" -> fighter.getTrainingSessions() >= l.getInt(BASE_TRAINING) + Math.max(1, l.getInt(TARGET_COUNT));
            case "LEARN_TECHNIQUE" -> FighterTechniqueManager.learnedCount(fighter) > l.getInt(BASE_TECHNIQUES);
            case "ACQUIRE_EQUIPMENT" -> FighterArsenalManager.satisfiesEquipmentGoal(fighter, l.getString(TARGET));
            case "ADVANCE_RACIAL" -> fighter.getRacialSkillLevel() > l.getInt(BASE_RACIAL);
            case "LEARN_FLIGHT" -> fighter.hasFlightUnlocked();
            case "WIN_FIGHTS" -> l.getInt("Wins") >= l.getInt(BASE_WINS) + Math.max(1, l.getInt(TARGET_COUNT));
            case "FUSION" -> l.getInt("Fusions") > l.getInt(BASE_FUSIONS);
            default -> false;
        };
        if (done) complete(fighter, summary(fighter));
    }

    private static void complete(AmbientFighterEntity fighter, String result) {
        CompoundTag l = fighter.getLegacyData();
        String old = l.getString(TYPE);
        if (old.isBlank()) return;
        String resolved = result == null || result.isBlank() ? old : result;
        fighter.recordLegacyEvent("Completed goal: " + resolved);
        l.putInt(COMPLETED, Math.min(Integer.MAX_VALUE, Math.max(0, l.getInt(COMPLETED)) + 1));
        l.putString(LAST_TYPE, old);
        l.putString(LAST_RESULT, resolved.length() > 120 ? resolved.substring(0, 120) : resolved);
        l.remove(TYPE);
        l.remove(TARGET);
        l.remove(TARGET_POWER);
        l.putLong(COOLDOWN, fighter.level().getGameTime() + 700L + fighter.getRandom().nextInt(901));
        FighterMemoryManager.refreshLoadedProfile(fighter);
    }

    /** Older goals lacked personality/history weighting or could false-complete equipment goals. Reset them once. */
    private static void migrateGoalSchema(CompoundTag l) {
        if (l.getInt(SCHEMA_KEY) == SCHEMA) return;
        l.remove(TYPE);
        l.remove(TARGET);
        l.remove(BASE_TRAINING);
        l.remove(BASE_RACIAL);
        l.remove(BASE_TECHNIQUES);
        l.remove(BASE_WINS);
        l.remove(BASE_FUSIONS);
        l.remove(TARGET_COUNT);
        l.remove(TARGET_POWER);
        l.remove(COOLDOWN);
        l.putInt(SCHEMA_KEY, SCHEMA);
    }

    private static AmbientFighterEntity findNamedNearby(AmbientFighterEntity fighter, String name, double range) {
        if (!(fighter.level() instanceof ServerLevel level) || name == null || name.isBlank()) return null;
        return level.getEntitiesOfClass(AmbientFighterEntity.class, fighter.getBoundingBox().inflate(range),
                        other -> other != fighter && other.isAlive() && name.equals(other.getFighterName()))
                .stream().findFirst().orElse(null);
    }
}
