package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterRace;

import java.util.Locale;

/** Single authoritative identity gate for DMZ 2.2 attacks, including restored save data. */
public final class FighterCombatTechniquePolicy {
    private FighterCombatTechniquePolicy() {}

    public static boolean canUse(AmbientFighterEntity fighter, String techniqueId) {
        if (fighter == null || techniqueId == null || techniqueId.isBlank()) return false;
        String id = techniqueId.toLowerCase(Locale.ROOT);
        return switch (id) {
            case "dimensional_slash", "dimensional_sword_attack", "dimensional_punch",
                 "dimensional_warp", "dimensional_teleport" -> WorldMenaceManager.isHerobrine(fighter);
            case "gum_punch", "rage_scream" -> fighter.getRace() == FighterRace.MAJIN;
            default -> true;
        };
    }
}
