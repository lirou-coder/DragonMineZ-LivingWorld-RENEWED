package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.FighterRace;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;

import java.util.Comparator;

/** Reads the same live race-form files used for players. */
public final class NpcFormConfigBridge {
    private NpcFormConfigBridge() {}

    public static Form form(FighterRace race, int skillLevel) {
        if (skillLevel <= 0) return null;
        FormConfig group = ConfigManager.getFormGroup(race.dmzId(), group(race));
        if (group == null || group.getForms() == null) return null;
        return group.getForms().entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().getUnlockOnSkillLevel() != null
                        && e.getValue().getUnlockOnSkillLevel() <= skillLevel)
                .max(Comparator.comparingInt(e -> e.getValue().getUnlockOnSkillLevel()))
                .map(e -> from(e.getKey(), e.getValue())).orElse(null);
    }

    public static int maxSkillLevel(FighterRace race) {
        FormConfig group = ConfigManager.getFormGroup(race.dmzId(), group(race));
        if (group == null || group.getForms() == null) return 0;
        return group.getForms().values().stream().filter(f -> f != null && f.getUnlockOnSkillLevel() != null)
                .mapToInt(FormConfig.FormData::getUnlockOnSkillLevel).max().orElse(0);
    }

    public static int nextUnlockLevel(FighterRace race, int current) {
        FormConfig group = ConfigManager.getFormGroup(race.dmzId(), group(race));
        if (group == null || group.getForms() == null) return current;
        return group.getForms().values().stream().filter(f -> f != null && f.getUnlockOnSkillLevel() != null
                        && f.getUnlockOnSkillLevel() > current).mapToInt(FormConfig.FormData::getUnlockOnSkillLevel)
                .min().orElse(current);
    }

    public static Form kaioken(int level) {
        if (level <= 0) return null;
        FormConfig group = ConfigManager.getStackFormGroup("kaioken");
        if (group == null || group.getForms() == null) return null;
        String number = Integer.toString(level);
        return group.getForms().entrySet().stream()
                .filter(e -> e.getKey().replace("x", "").replace("times", "").contains(number))
                .findFirst().map(e -> from(e.getKey(), e.getValue())).orElse(null);
    }

    private static String group(FighterRace race) {
        return switch (race) {
            case HUMAN, NAMEKIAN -> "superforms";
            case SAIYAN -> "supersaiyan";
            case MAJIN -> "pureforms";
            case FROST_DEMON -> "evolutionforms";
            case BIO_ANDROID -> "bioevolution";
        };
    }

    private static Form from(String id, FormConfig.FormData f) {
        Float[] scale = f.getModelScaling();
        float modelScale = scale != null && scale.length > 0 && scale[0] != null ? scale[0] : 1.0F;
        return new Form(id, f.getName(), f.getUnlockOnSkillLevel(), positive(f.getStrMultiplier()),
                positive(f.getDefMultiplier()), positive(f.getVitMultiplier()), positive(f.getPwrMultiplier()),
                positive(f.getSpeedMultiplier()), positive(f.getAttackSpeed()), modelScale,
                clean(f.getHairColor()), clean(f.getEye1Color()), parseColor(f.getAuraColor()),
                Boolean.TRUE.equals(f.getHasLightnings()));
    }

    private static double positive(Double value) { return value != null && value > 0.0D ? value : 1.0D; }
    private static String clean(String value) { return value == null ? "" : value; }
    private static int parseColor(String value) {
        try { return Integer.parseInt(clean(value).replace("#", ""), 16) & 0xFFFFFF; }
        catch (NumberFormatException ignored) { return 0xFFFFFF; }
    }

    public record Form(String id, String name, int skillLevel, double melee, double defense, double vitality,
                       double ki, double speed, double attackSpeed, float scale, String hairColor,
                       String eyeColor, int auraColor, boolean lightning) {}
}
