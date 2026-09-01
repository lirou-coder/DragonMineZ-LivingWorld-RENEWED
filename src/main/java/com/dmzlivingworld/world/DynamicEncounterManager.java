package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dmzlivingworld.entity.FighterRank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;

import java.util.ArrayList;
import java.util.List;

/**
 * Small encounter compositions. These are deliberately scenes, not structures or quests:
 * a few compatible entities are placed near one another and given an initial motive.
 */
public final class DynamicEncounterManager {
    private DynamicEncounterManager() {}

    public static boolean trySpawnNaturalEncounter(ServerPlayer player, int capacity, int remainingBadSlots) {
        RandomSource random = player.getRandom();
        List<EncounterType> eligible = new ArrayList<>();

        if (capacity >= 2) eligible.add(EncounterType.DUEL);
        if (capacity >= 2 && remainingBadSlots >= 1) {
            eligible.add(EncounterType.CLASH);
            eligible.add(EncounterType.MUGGING);
        }
        if (capacity >= 3 && remainingBadSlots >= 1) eligible.add(EncounterType.RESCUE);
        if (capacity >= 2 && remainingBadSlots >= 2 && random.nextDouble() < 0.20D) eligible.add(EncounterType.AMBUSH);
        if (capacity >= 4 && remainingBadSlots >= 2 && random.nextDouble() < 0.14D) eligible.add(EncounterType.BRAWL);
        if (capacity >= 1 && random.nextDouble() < 0.14D) eligible.add(EncounterType.FRIEZA_SKIRMISH);

        if (eligible.isEmpty()) return false;
        EncounterType chosen = eligible.get(random.nextInt(eligible.size()));
        return spawnEncounter(player, chosen, false) > 0;
    }

    public static int spawnEncounter(ServerPlayer player, EncounterType type, boolean debug) {
        BlockPos anchor = AmbientFighterSpawner.findEncounterAnchor(player, debug);
        if (anchor == null) return 0;

        int spawned = switch (type) {
            case DUEL -> spawnDuel(player, anchor);
            case CLASH -> spawnClash(player, anchor);
            case MUGGING -> spawnMugging(player, anchor);
            case RESCUE -> spawnRescue(player, anchor);
            case AMBUSH -> spawnAmbush(player, anchor);
            case BRAWL -> spawnBrawl(player, anchor);
            case FRIEZA_SKIRMISH -> spawnFriezaSkirmish(player, anchor);
        };
        if (spawned > 0 && player.level() instanceof ServerLevel level) {
            String label = switch (type) {
                case CLASH -> "LOCAL CLASH";
                case MUGGING -> "MUGGING IN PROGRESS";
                case RESCUE -> "RESCUE IN PROGRESS";
                case BRAWL -> "MULTI-FIGHTER BRAWL";
                case FRIEZA_SKIRMISH -> "FRIEZA SKIRMISH";
                default -> "";
            };
            if (!label.isBlank()) WorldEventNotifier.announce(level, anchor, label, "A Living World scene is unfolding");
        }
        return spawned;
    }

    private static int spawnDuel(ServerPlayer player, BlockPos anchor) {
        FighterRank rank = player.getRandom().nextDouble() < 0.72D ? FighterRank.TRAINED : FighterRank.ROOKIE;
        AmbientFighterEntity first = spawn(player, anchor, FighterAlignment.NEUTRAL, rank, scenePersonality(player, FighterAlignment.NEUTRAL, FighterPersonality.PROUD, 0.58F));
        AmbientFighterEntity second = spawn(player, anchor, FighterAlignment.NEUTRAL, rank, scenePersonality(player, FighterAlignment.NEUTRAL, FighterPersonality.CALM, 0.58F));
        if (!complete(first, second)) return cleanup(first, second);

        first.startDuel(second);
        second.startDuel(first);
        return 2;
    }

    private static int spawnClash(ServerPlayer player, BlockPos anchor) {
        AmbientFighterEntity good = spawn(player, anchor, FighterAlignment.GOOD, FighterRank.TRAINED, scenePersonality(player, FighterAlignment.GOOD, FighterPersonality.HEROIC, 0.58F));
        AmbientFighterEntity bad = spawn(player, anchor, FighterAlignment.BAD, FighterRank.TRAINED, scenePersonality(player, FighterAlignment.BAD, FighterPersonality.AGGRESSIVE, 0.58F));
        if (!complete(good, bad)) return cleanup(good, bad);

        good.setTarget(bad);
        bad.setTarget(good);
        return 2;
    }

    private static int spawnMugging(ServerPlayer player, BlockPos anchor) {
        AmbientFighterEntity bad = spawn(player, anchor, FighterAlignment.BAD, FighterRank.TRAINED, scenePersonality(player, FighterAlignment.BAD, FighterPersonality.AGGRESSIVE, 0.58F));
        if (bad == null) return 0;

        // Some Earth muggings confront the player directly. Good standing with the Earth Guardian
        // Corps can turn this into a living-world rescue response rather than a scripted quest.
        if (player.level() instanceof ServerLevel earth && LivingWorldDimensions.realm(earth) == FactionRealm.EARTH
                && !player.isCreative() && !player.isSpectator() && player.getRandom().nextFloat() < 0.34F) {
            PeacekeeperManager.markNpcAggressor(player, bad);
            bad.setTarget(player);
            PeacekeeperManager.maybeAidMuggedPlayer(player, bad);
            return 1;
        }

        // If this scene happens near an existing village, use an existing civilian rather
        // than creating permanent villagers. Otherwise another Earth fighter is the victim.
        if (player.level() instanceof ServerLevel level) {
            List<Villager> villagers = level.getEntitiesOfClass(
                    Villager.class,
                    bad.getBoundingBox().inflate(22.0D, 10.0D, 22.0D),
                    Villager::isAlive
            );
            if (!villagers.isEmpty()) {
                bad.setTarget(villagers.get(0));
                return 1;
            }
        }

        AmbientFighterEntity victim = spawn(player, anchor, FighterAlignment.NEUTRAL, FighterRank.ROOKIE, scenePersonality(player, FighterAlignment.NEUTRAL, FighterPersonality.CAUTIOUS, 0.62F));
        if (victim == null) return cleanup(bad);
        bad.setTarget(victim);
        return 2;
    }

    private static int spawnRescue(ServerPlayer player, BlockPos anchor) {
        AmbientFighterEntity victim = spawn(player, anchor, FighterAlignment.NEUTRAL, FighterRank.ROOKIE, scenePersonality(player, FighterAlignment.NEUTRAL, FighterPersonality.CAUTIOUS, 0.62F));
        AmbientFighterEntity bad = spawn(player, anchor, FighterAlignment.BAD, FighterRank.TRAINED, scenePersonality(player, FighterAlignment.BAD, FighterPersonality.AGGRESSIVE, 0.58F));
        AmbientFighterEntity good = spawn(player, anchor, FighterAlignment.GOOD, FighterRank.TRAINED, scenePersonality(player, FighterAlignment.GOOD, FighterPersonality.HEROIC, 0.58F));
        if (!complete(victim, bad, good)) return cleanup(victim, bad, good);

        bad.setTarget(victim);
        good.setTarget(bad);
        return 3;
    }

    private static int spawnAmbush(ServerPlayer player, BlockPos anchor) {
        AmbientFighterEntity first = spawn(player, anchor, FighterAlignment.BAD, FighterRank.ROOKIE, scenePersonality(player, FighterAlignment.BAD, FighterPersonality.AGGRESSIVE, 0.58F));
        FighterRank secondRank = player.getRandom().nextDouble() < 0.72D ? FighterRank.ROOKIE : FighterRank.TRAINED;
        AmbientFighterEntity second = spawn(player, anchor, FighterAlignment.BAD, secondRank, scenePersonality(player, FighterAlignment.BAD, FighterPersonality.AGGRESSIVE, 0.58F));
        if (!complete(first, second)) return cleanup(first, second);

        PeacekeeperManager.markNpcAggressor(player, first);
        PeacekeeperManager.markNpcAggressor(player, second);
        first.setTarget(player);
        second.setTarget(player);
        // One response roll is enough; the responsibility scan lets any responding guards split
        // their targets across both members of this exact ambush.
        PeacekeeperManager.maybeAidMuggedPlayer(player, first);
        return 2;
    }

    private static int spawnBrawl(ServerPlayer player, BlockPos anchor) {
        // 0.6.10: brawl is a readable 2v2 skirmish rather than three bodies collapsing
        // onto one target. Native target acquisition can still reshuffle the fight later.
        AmbientFighterEntity goodOne = spawn(player, anchor, FighterAlignment.GOOD, FighterRank.TRAINED, scenePersonality(player, FighterAlignment.GOOD, FighterPersonality.HEROIC, 0.58F));
        AmbientFighterEntity goodTwo = spawn(player, anchor, FighterAlignment.GOOD,
                player.getRandom().nextBoolean() ? FighterRank.ROOKIE : FighterRank.TRAINED, scenePersonality(player, FighterAlignment.GOOD, FighterPersonality.CALM, 0.52F));
        AmbientFighterEntity badOne = spawn(player, anchor, FighterAlignment.BAD, FighterRank.TRAINED, scenePersonality(player, FighterAlignment.BAD, FighterPersonality.AGGRESSIVE, 0.58F));
        AmbientFighterEntity badTwo = spawn(player, anchor, FighterAlignment.BAD,
                player.getRandom().nextBoolean() ? FighterRank.ROOKIE : FighterRank.TRAINED, scenePersonality(player, FighterAlignment.BAD, FighterPersonality.PROUD, 0.52F));
        if (!complete(goodOne, goodTwo, badOne, badTwo)) return cleanup(goodOne, goodTwo, badOne, badTwo);

        goodOne.setTarget(badOne);
        badOne.setTarget(goodOne);
        goodTwo.setTarget(badTwo);
        badTwo.setTarget(goodTwo);
        return 4;
    }

    private static int spawnFriezaSkirmish(ServerPlayer player, BlockPos anchor) {
        AmbientFighterEntity fighter = spawn(player, anchor, FighterAlignment.GOOD, FighterRank.TRAINED, scenePersonality(player, FighterAlignment.GOOD, FighterPersonality.HEROIC, 0.58F));
        if (fighter == null || !(player.level() instanceof ServerLevel level)) return cleanup(fighter);
        BlockPos soldierPos = AmbientFighterSpawner.findSafeGroundAround(level, anchor, player.getRandom(), 5, 10, 16);
        if (soldierPos == null) return cleanup(fighter);
        String type = player.getRandom().nextBoolean() ? "saga_friezasoldier01" : "saga_friezasoldier02";
        Mob soldier = FriezaNpcUtil.spawnSoldier(level, soldierPos, fighter, player.getRandom(), type,
                "lw_frieza_skirmish");
        if (soldier == null) return cleanup(fighter);
        fighter.setTarget(soldier);
        return 2;
    }


    /** Scene roles bias personality without cloning the same personality every time. */
    private static FighterPersonality scenePersonality(ServerPlayer player, FighterAlignment alignment,
                                                       FighterPersonality preferred, float preferredChance) {
        RandomSource random = player.getRandom();
        if (random.nextFloat() < preferredChance) return preferred;
        FighterPersonality rolled = FighterPersonality.roll(random, alignment);
        // Avoid the fallback always landing back on the scripted preference.
        if (rolled == preferred && random.nextBoolean()) rolled = FighterPersonality.roll(random, alignment);
        return rolled;
    }

    private static AmbientFighterEntity spawn(ServerPlayer player, BlockPos anchor, FighterAlignment alignment,
                                               FighterRank rank, FighterPersonality personality) {
        return AmbientFighterSpawner.spawnAroundAnchor(player, anchor, alignment, rank, personality, 3, 8);
    }

    private static boolean complete(AmbientFighterEntity... fighters) {
        for (AmbientFighterEntity fighter : fighters) if (fighter == null) return false;
        return true;
    }

    /** Returns zero so partial failed scenes never count as successfully spawned. */
    private static int cleanup(AmbientFighterEntity... fighters) {
        for (AmbientFighterEntity fighter : fighters) {
            if (fighter != null && fighter.isAlive()) fighter.discard();
        }
        return 0;
    }
}
