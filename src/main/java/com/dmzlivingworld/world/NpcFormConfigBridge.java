package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.FighterRace;
import com.dmzlivingworld.entity.RacialFormProfile;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Reads the player's live superform configuration for NPC racial transformations. */
public final class NpcFormConfigBridge {
    private NpcFormConfigBridge() {}

    public static Form form(FighterRace race, int skillLevel) {
        if (race == null || skillLevel <= 0) return null;
        return candidates(race).stream()
                .filter(candidate -> candidate.skillLevel() == skillLevel)
                .max(Comparator.comparingDouble(Form::averageMultiplier))
                .orElse(null);
    }

    public static int maxSkillLevel(FighterRace race) {
        return candidates(race).stream().mapToInt(Form::skillLevel).max().orElse(0);
    }

    public static int nextUnlockLevel(FighterRace race, int current) {
        return candidates(race).stream().mapToInt(Form::skillLevel)
                .filter(level -> level > current).min().orElse(current);
    }

    public static Form kaioken(int level) {
        if (level <= 0) return null;
        FormConfig group = ConfigManager.getStackFormGroup("kaioken");
        if (group == null || group.getForms() == null) return null;
        String number = Integer.toString(level);
        return group.getForms().entrySet().stream()
                .filter(e -> e.getKey().replace("x", "").replace("times", "").contains(number))
                .findFirst().map(e -> from(e.getKey(), e.getValue(), level)).orElse(null);
    }

    public static RacialFormProfile profile(FighterRace race, int skillLevel) {
        Form form = form(race, skillLevel);
        if (form == null) return null;
        return new RacialFormProfile(race, form.skillLevel(), form.id(), form.name(), form.melee(), form.speed(),
                form.attackSpeed(), form.scale(), form.auraColor(), form.lightning(), form.hairType(), form.hairColor(),
                form.eyeColor(), form.modelKey());
    }

    private static List<Form> candidates(FighterRace race) {
        List<Form> result = new ArrayList<>();
        if (race != null && race.isSairensRace() && !SairensRaceCompat.isLoaded()) return result;
        for (var entry : ConfigManager.getAllFormsForRace(race.dmzId()).entrySet()) {
            FormConfig group = entry.getValue();
            if (group == null || !isSuperform(group.getFormType())) continue;
            if (race == FighterRace.SAIYAN && isOozaru(group.getGroupName(), entry.getKey())) continue;
            for (var form : group.getForms().entrySet()) {
                FormConfig.FormData data = form.getValue();
                if (data == null || data.getUnlockOnSkillLevel() == null || data.getUnlockOnSkillLevel() <= 0) continue;
                result.add(from(form.getKey(), data, data.getUnlockOnSkillLevel()));
            }
        }
        return result;
    }

    private static boolean isSuperform(String type) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT).replace("_", "");
        return normalized.equals("superform") || normalized.equals("superforms");
    }

    private static boolean isOozaru(String group, String form) {
        String value = ((group == null ? "" : group) + " " + (form == null ? "" : form)).toLowerCase(Locale.ROOT);
        return value.contains("oozaru");
    }

    private static Form from(String id, FormConfig.FormData f, int skillLevel) {
        Float[] scale = f.getModelScaling();
        float modelScale = scale != null && scale.length > 0 && scale[0] != null ? scale[0] : 1.0F;
        return new Form(id, f.getName(), skillLevel, positive(f.getStrMultiplier()), positive(f.getDefMultiplier()),
                positive(f.getVitMultiplier()), positive(f.getPwrMultiplier()), positive(f.getSpeedMultiplier()),
                positive(f.getAttackSpeed()), modelScale, clean(f.getCustomModel()), clean(f.getHairType()),
                clean(f.getHairColor()), clean(f.getEye1Color()), clean(f.getEye2Color()), clean(f.getAuraType()),
                parseColor(f.getAuraColor()), Boolean.TRUE.equals(f.getHasLightnings()), clean(f.getLightningColor()),
                clean(f.getBodyColor1()), clean(f.getBodyColor2()), clean(f.getBodyColor3()));
    }

    private static double positive(Double value) { return value != null && value > 0.0D ? value : 1.0D; }
    private static String clean(String value) { return value == null ? "" : value; }
    private static int parseColor(String value) {
        try { return Integer.parseInt(clean(value).replace("#", ""), 16) & 0xFFFFFF; }
        catch (NumberFormatException ignored) { return 0xFFFFFF; }
    }

    public record Form(String id, String name, int skillLevel, double melee, double defense, double vitality,
                       double ki, double speed, double attackSpeed, float scale, String modelKey, String hairType,
                       String hairColor, String eyeColor, String eye2Color, String auraType, int auraColor,
                       boolean lightning, String lightningColor, String bodyColor1, String bodyColor2, String bodyColor3) {
        public double averageMultiplier() { return (melee + defense + vitality + ki + speed + attackSpeed) / 6.0D; }
    }
}
