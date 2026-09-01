package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterArchetype;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dmzlivingworld.entity.FighterRace;
import com.dmzlivingworld.entity.FighterRank;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** Creates a normal LW fighter designed to exercise the real Player/NPC Fusion menu path. */
public final class FighterFusionDebugPartnerManager {
    private FighterFusionDebugPartnerManager() {}

    public static AmbientFighterEntity spawnPartner(ServerPlayer player) {
        if (player == null) return null;
        StatsData stats = player.getCapability(StatsCapability.INSTANCE).orElse(null);
        if (stats == null || stats.getCharacter() == null || stats.getStats() == null) return null;
        FighterRace race = raceFor(stats.getCharacter().getRace());
        if (race == null) return null;

        AmbientFighterEntity fighter = AmbientFighterSpawner.spawnNearPlayer(player, FighterAlignment.GOOD, FighterRank.TRAINED, true);
        if (fighter == null) return null;
        fighter.initializeAs(FighterAlignment.GOOD, FighterRank.TRAINED, FighterPersonality.CALM,
                race, FighterArchetype.MARTIAL_ARTIST);

        int playerTotal = Math.max(6, stats.getStats().getTotalStats());
        // Inverse of LWFusionProfile: total ~= effective*1.2 and BP=1200*(effective/100)^1.2.
        double targetTotal = playerTotal * (0.97D + player.getRandom().nextDouble() * 0.06D);
        double effective = targetTotal / 1.2D;
        long bp = Math.round(1200.0D * Math.pow(Math.max(1.0D, effective) / 100.0D, 1.2D));
        fighter.setBattlePowerAndRefresh((int)Math.max(1L, Math.min(Integer.MAX_VALUE - 1L, bp)));
        FighterMemoryManager.strengthenRelationship(player, fighter, 45,
                FighterRelationshipManager.BondEvent.GENERIC, "Debug fusion partner introduced");
        ReactiveWorldManager.react(fighter, ReactiveWorldManager.Mood.CONTENT, "ready to test fusion", 1200);
        FighterMemoryManager.refreshLoadedProfile(fighter);
        return fighter;
    }

    private static FighterRace raceFor(String raw) {
        String race = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        for (FighterRace value : FighterRace.values()) {
            String id = value.dmzId().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
            if (id.equals(race)) return value;
        }
        return null;
    }
}
