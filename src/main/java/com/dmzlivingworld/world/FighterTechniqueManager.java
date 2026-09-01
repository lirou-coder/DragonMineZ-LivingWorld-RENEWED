package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Persistent technique lineage for Living World fighters.
 *
 * LW does not invent a second Ki system. Learned techniques are exact native
 * DBSagasEntity KiSkillType actions copied from another fighter's live DMZ skill
 * pool, then reapplied to the learner whenever its native combat profile is rebuilt.
 */
public final class FighterTechniqueManager {
    private static final String KEY = "LearnedTechniques";
    private static final int MAX_LEARNED = 4;

    private FighterTechniqueManager() {}

    public static void applyLearnedTechniques(AmbientFighterEntity fighter) {
        if (fighter == null) return;
        ListTag list = cleanList(fighter.getLegacyData());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            DBSagasEntity.KiSkillType type = DBSagasEntity.KiSkillType.fromId(entry.getInt("Id"));
            if (type == null || hasSkill(fighter, type.getId())) continue;
            int cooldown = clamp(entry.getInt("Cooldown"), 70, 900);
            float size = clampFloat(entry.getFloat("Size"), 0.35F, 2.5F);
            int main = entry.contains("Main", Tag.TAG_ANY_NUMERIC) ? entry.getInt("Main") : 0x67D7FF;
            int border = entry.contains("Border", Tag.TAG_ANY_NUMERIC) ? entry.getInt("Border") : 0xE7FAFF;
            int outline = entry.contains("Outline", Tag.TAG_ANY_NUMERIC) ? entry.getInt("Outline") : 0x2F72FF;
            fighter.addKiSkill(type, cooldown, size, main, border, outline);
        }
    }

    /** Learn one real technique that another fighter currently knows. This is generic observation/copying, not mentorship. */
    public static boolean tryLearnFrom(AmbientFighterEntity learner, AmbientFighterEntity source, String reason) {
        if (learner == null || source == null || learner == source || learner.level().isClientSide) return false;
        ListTag known = cleanList(learner.getLegacyData());
        if (known.size() >= MAX_LEARNED) return false;

        List<DBSagasEntity.KiSkill> candidates = new ArrayList<>();
        for (DBSagasEntity.KiSkill skill : source.getSkillPool()) {
            if (skill == null || DBSagasEntity.KiSkillType.fromId(skill.id) == null || hasSkill(learner, skill.id)) continue;
            candidates.add(skill);
        }
        if (candidates.isEmpty()) return false;

        // Named/major techniques are more memorable, but generic techniques remain valid.
        candidates.sort(Comparator.comparingInt(FighterTechniqueManager::learningPriority).reversed());
        int pool = Math.min(3, candidates.size());
        DBSagasEntity.KiSkill chosen = candidates.get(learner.getRandom().nextInt(pool));
        DBSagasEntity.KiSkillType type = DBSagasEntity.KiSkillType.fromId(chosen.id);
        if (type == null) return false;

        CompoundTag entry = new CompoundTag();
        entry.putInt("Id", chosen.id);
        entry.putInt("Cooldown", clamp(chosen.cooldownMax, 70, 900));
        entry.putFloat("Size", clampFloat(chosen.size, 0.35F, 2.5F));
        entry.putInt("Main", chosen.colorMain);
        entry.putInt("Border", chosen.colorBorder);
        entry.putInt("Outline", chosen.colorOutline);
        entry.putString("Teacher", trim(source.getFighterName(), 48));
        entry.putString("Lineage", trim(lineageFor(source, chosen.id), 140));
        known.add(entry);
        learner.getLegacyData().put(KEY, known);

        learner.addKiSkill(type, entry.getInt("Cooldown"), entry.getFloat("Size"),
                entry.getInt("Main"), entry.getInt("Border"), entry.getInt("Outline"));
        learner.recordLegacyEvent("Learned " + label(type) + " from " + source.getFighterName()
                + (reason == null || reason.isBlank() ? "" : " (" + reason + ")"));
        FighterGoalManager.onTechniqueLearned(learner, label(type));
        FighterMemoryManager.refreshLoadedProfile(learner);
        return true;
    }

    public static int learnedCount(AmbientFighterEntity fighter) {
        return fighter == null ? 0 : cleanList(fighter.getLegacyData()).size();
    }

    public static String summary(AmbientFighterEntity fighter) {
        if (fighter == null) return "none";
        ListTag list = cleanList(fighter.getLegacyData());
        if (list.isEmpty()) return "none";
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            DBSagasEntity.KiSkillType type = DBSagasEntity.KiSkillType.fromId(entry.getInt("Id"));
            if (type == null) continue;
            String teacher = entry.getString("Teacher");
            out.add(label(type) + (teacher.isBlank() ? "" : " ← " + teacher));
        }
        return out.isEmpty() ? "none" : String.join(", ", out);
    }

    public static String lineageSummary(AmbientFighterEntity fighter) {
        if (fighter == null) return "none";
        ListTag list = cleanList(fighter.getLegacyData());
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            DBSagasEntity.KiSkillType type = DBSagasEntity.KiSkillType.fromId(entry.getInt("Id"));
            if (type == null) continue;
            String lineage = entry.getString("Lineage");
            if (!lineage.isBlank()) out.add(label(type) + ": " + lineage);
        }
        return out.isEmpty() ? "none" : String.join(" • ", out);
    }

    private static ListTag cleanList(CompoundTag legacy) {
        ListTag source = legacy.contains(KEY, Tag.TAG_LIST) ? legacy.getList(KEY, Tag.TAG_COMPOUND) : new ListTag();
        ListTag clean = new ListTag();
        for (int i = Math.max(0, source.size() - MAX_LEARNED); i < source.size(); i++) {
            CompoundTag entry = source.getCompound(i).copy();
            DBSagasEntity.KiSkillType type = DBSagasEntity.KiSkillType.fromId(entry.getInt("Id"));
            if (type == null || containsId(clean, type.getId())) continue;
            entry.putInt("Cooldown", clamp(entry.getInt("Cooldown"), 70, 900));
            entry.putFloat("Size", clampFloat(entry.getFloat("Size"), 0.35F, 2.5F));
            if (entry.contains("Teacher", Tag.TAG_STRING)) entry.putString("Teacher", trim(entry.getString("Teacher"), 48));
            if (entry.contains("Lineage", Tag.TAG_STRING)) entry.putString("Lineage", trim(entry.getString("Lineage"), 140));
            clean.add(entry);
        }
        legacy.put(KEY, clean);
        return clean;
    }

    private static boolean containsId(ListTag list, int id) {
        for (int i = 0; i < list.size(); i++) if (list.getCompound(i).getInt("Id") == id) return true;
        return false;
    }

    private static boolean hasSkill(AmbientFighterEntity fighter, int id) {
        for (DBSagasEntity.KiSkill skill : fighter.getSkillPool()) if (skill != null && skill.id == id) return true;
        return false;
    }

    private static int learningPriority(DBSagasEntity.KiSkill skill) {
        DBSagasEntity.KiSkillType type = DBSagasEntity.KiSkillType.fromId(skill.id);
        if (type == null) return 0;
        return switch (type) {
            case KAMEHAMEHA, GALICK_GUN, MAKANKOSAPPO, KIENZAN, DEATH_BALL, MASENKO,
                    BIG_BANG, FINAL_FLASH, DOUBLE_SUNDAY -> 100;
            case GENERIC_KI_WAVE, KI_EXPLOSION, KI_BARRIER, KI_AIR_VOLLEY, TRIPLE_LASER -> 70;
            case KI_VOLLEY, KI_LASER -> 45;
            default -> 25;
        };
    }

    private static String lineageFor(AmbientFighterEntity source, int id) {
        ListTag list = cleanList(source.getLegacyData());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.getInt("Id") != id) continue;
            String existing = entry.getString("Lineage");
            return existing.isBlank() ? source.getFighterName() : existing + " → " + source.getFighterName();
        }
        return source.getFighterName();
    }

    public static String label(DBSagasEntity.KiSkillType type) {
        if (type == null) return "Ki technique";
        return switch (type) {
            case KI_SMALL -> "Ki shots";
            case KI_VOLLEY -> "Ki volley";
            case GENERIC_KI_WAVE -> "Ki wave";
            case KI_LASER -> "Ki laser";
            case KI_EXPLOSION -> "Ki burst";
            case KI_AIR_VOLLEY -> "Aerial volley";
            case TRIPLE_LASER -> "Triple laser";
            case KI_BARRIER -> "Ki barrier";
            case KAMEHAMEHA -> "Kamehameha";
            case GALICK_GUN -> "Galick Gun";
            case MASENKO -> "Masenko";
            case FINAL_FLASH -> "Final Flash";
            case DOUBLE_SUNDAY -> "Double Sunday";
            case MAKANKOSAPPO -> "Special Beam Cannon";
            case KIENZAN -> "Destructo Disc";
            case DEATH_BALL -> "Death Ball";
            case BIG_BANG -> "Big Bang Attack";
            default -> "Ki technique";
        };
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static float clampFloat(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
