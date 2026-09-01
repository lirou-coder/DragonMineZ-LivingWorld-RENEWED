package com.dmzlivingworld.command;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.network.FactionDossierPacket;
import com.dmzlivingworld.network.LWNetwork;
import com.dmzlivingworld.network.WorldSettingsUpdatePacket;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterArchetype;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dmzlivingworld.entity.FighterRace;
import com.dmzlivingworld.entity.FighterRank;
import com.dmzlivingworld.world.AmbientFighterSpawner;
import com.dmzlivingworld.world.AntagonistWorldData;
import com.dmzlivingworld.world.DynamicEncounterManager;
import com.dmzlivingworld.world.EncounterType;
import com.dmzlivingworld.world.FighterBattleGrowthManager;
import com.dmzlivingworld.world.FighterMemoryManager;
import com.dmzlivingworld.world.FighterRelationshipManager;
import com.dmzlivingworld.world.FighterArsenalManager;
import com.dmzlivingworld.world.FighterLegacyWorldData;
import com.dmzlivingworld.world.FighterTechniqueManager;
import com.dmzlivingworld.world.FighterGoalManager;
import com.dmzlivingworld.world.FighterAmbientActivityManager;
import com.dmzlivingworld.world.FighterDailyRoutineManager;
import com.dmzlivingworld.world.FighterNpcSocialManager;
import com.dmzlivingworld.world.FighterLifeJoinManager;
import com.dmzlivingworld.world.FighterInspectionManager;
import com.dmzlivingworld.world.FighterInstantTransmissionManager;
import com.dmzlivingworld.world.FighterPowerCompareManager;
import com.dmzlivingworld.world.FighterPowerSpikeReactionManager;
import com.dmzlivingworld.world.FighterPracticeSparManager;
import com.dmzlivingworld.world.FighterAppearanceEvolutionManager;
import com.dmzlivingworld.world.FighterAnimalMimicManager;
import com.dmzlivingworld.world.FighterFusionDebugPartnerManager;
import com.dmzlivingworld.world.FighterSpecialItemManager;
import com.dmzlivingworld.world.FighterScientistManager;
import com.dmzlivingworld.world.FighterPotentialManager;
import com.dmzlivingworld.world.LivingWorldLifecycle;
import com.dmzlivingworld.world.PowerSensingManager;
import com.dmzlivingworld.world.WorldPowerScaler;
import com.dmzlivingworld.world.WorldEraData;
import com.dmzlivingworld.world.WorldIncidentData;
import com.dmzlivingworld.world.PlayerWorldManager;
import com.dmzlivingworld.world.LivingBondManager;
import com.dmzlivingworld.world.FactionRequestManager;
import com.dmzlivingworld.world.PrisonerWorldData;
import com.dmzlivingworld.compat.MeditationCompat;
import com.dmzlivingworld.world.FactionEncounterManager;
import com.dmzlivingworld.world.FactionRealm;
import com.dmzlivingworld.world.LivingWorldDimensions;
import com.dmzlivingworld.world.FactionRole;
import com.dmzlivingworld.world.FactionManager;
import com.dmzlivingworld.world.FactionWorldData;
import com.dmzlivingworld.world.FactionActivityRegistry;
import com.dmzlivingworld.world.PeacekeeperManager;
import com.dmzlivingworld.world.WorldFaction;
import com.dmzlivingworld.world.WantedManager;
import com.dmzlivingworld.world.WantedWorldData;
import com.dmzlivingworld.world.WorldMenaceData;
import com.dmzlivingworld.world.WorldMenaceManager;
import com.dmzlivingworld.world.RedRibbonExperimentManager;
import com.dmzlivingworld.world.RedRibbonExperimentData;
import com.dmzlivingworld.world.NpcPlayerDamageManager;
import com.dmzlivingworld.world.ReactiveWorldManager;
import com.dmzlivingworld.world.FactionHornManager;
import com.dmzlivingworld.world.FighterDebugSpectateManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LivingWorldCommands {
    private LivingWorldCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("lw")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("guide").executes(ctx -> openGuide(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("menace")
                                .executes(ctx -> openWorldMenace(ctx.getSource().getPlayerOrException()))
                                .then(Commands.literal("tp").executes(ctx -> WorldMenaceManager.debugTeleport(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("spawn").executes(ctx -> WorldMenaceManager.debugSpawn(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("experiment")
                                        .then(Commands.literal("status").executes(ctx -> debugExperimentStatus(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.literal("spawn").executes(ctx -> RedRibbonExperimentManager.debugSpawn(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.literal("tp").executes(ctx -> RedRibbonExperimentManager.debugTeleport(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.literal("slaughter").executes(ctx -> RedRibbonExperimentManager.debugSlaughter(ctx.getSource().getPlayerOrException())))))
                        .then(Commands.literal("fighter")
                                .then(Commands.literal("spawn")
                                        .then(Commands.literal("good").executes(ctx -> spawn(ctx.getSource().getPlayerOrException(), FighterAlignment.GOOD, null)))
                                        .then(Commands.literal("neutral").executes(ctx -> spawn(ctx.getSource().getPlayerOrException(), FighterAlignment.NEUTRAL, null)))
                                        .then(Commands.literal("bad").executes(ctx -> spawn(ctx.getSource().getPlayerOrException(), FighterAlignment.BAD, null)))
                                        .then(Commands.literal("rookie")
                                                .then(Commands.literal("good").executes(ctx -> spawn(ctx.getSource().getPlayerOrException(), FighterAlignment.GOOD, FighterRank.ROOKIE)))
                                                .then(Commands.literal("neutral").executes(ctx -> spawn(ctx.getSource().getPlayerOrException(), FighterAlignment.NEUTRAL, FighterRank.ROOKIE)))
                                                .then(Commands.literal("bad").executes(ctx -> spawn(ctx.getSource().getPlayerOrException(), FighterAlignment.BAD, FighterRank.ROOKIE))))
                                        .then(Commands.literal("trained")
                                                .then(Commands.literal("good").executes(ctx -> spawn(ctx.getSource().getPlayerOrException(), FighterAlignment.GOOD, FighterRank.TRAINED)))
                                                .then(Commands.literal("neutral").executes(ctx -> spawn(ctx.getSource().getPlayerOrException(), FighterAlignment.NEUTRAL, FighterRank.TRAINED)))
                                                .then(Commands.literal("bad").executes(ctx -> spawn(ctx.getSource().getPlayerOrException(), FighterAlignment.BAD, FighterRank.TRAINED))))
                                        .then(Commands.literal("veteran")
                                                .then(Commands.literal("good").executes(ctx -> spawn(ctx.getSource().getPlayerOrException(), FighterAlignment.GOOD, FighterRank.VETERAN)))
                                                .then(Commands.literal("neutral").executes(ctx -> spawn(ctx.getSource().getPlayerOrException(), FighterAlignment.NEUTRAL, FighterRank.VETERAN)))
                                                .then(Commands.literal("bad").executes(ctx -> spawn(ctx.getSource().getPlayerOrException(), FighterAlignment.BAD, FighterRank.VETERAN))))
                                        .then(Commands.literal("race")
                                                .then(Commands.literal("human").executes(ctx -> spawnCustom(ctx.getSource().getPlayerOrException(), FighterRace.HUMAN, null)))
                                                .then(Commands.literal("saiyan").executes(ctx -> spawnCustom(ctx.getSource().getPlayerOrException(), FighterRace.SAIYAN, null)))
                                                .then(Commands.literal("namekian").executes(ctx -> spawnCustom(ctx.getSource().getPlayerOrException(), FighterRace.NAMEKIAN, null)))
                                                .then(Commands.literal("majin").executes(ctx -> spawnCustom(ctx.getSource().getPlayerOrException(), FighterRace.MAJIN, null)))
                                                .then(Commands.literal("frostdemon").executes(ctx -> spawnCustom(ctx.getSource().getPlayerOrException(), FighterRace.FROST_DEMON, null)))
                                                .then(Commands.literal("bioandroid").executes(ctx -> spawnCustom(ctx.getSource().getPlayerOrException(), FighterRace.BIO_ANDROID, null))))
                                        .then(Commands.literal("style")
                                                .then(Commands.literal("brawler").executes(ctx -> spawnCustom(ctx.getSource().getPlayerOrException(), null, FighterArchetype.BRAWLER)))
                                                .then(Commands.literal("martial").executes(ctx -> spawnCustom(ctx.getSource().getPlayerOrException(), null, FighterArchetype.MARTIAL_ARTIST)))
                                                .then(Commands.literal("ki").executes(ctx -> spawnCustom(ctx.getSource().getPlayerOrException(), null, FighterArchetype.KI_SPECIALIST)))
                                                .then(Commands.literal("speed").executes(ctx -> spawnCustom(ctx.getSource().getPlayerOrException(), null, FighterArchetype.SPEEDSTER)))
                                                .then(Commands.literal("guardian").executes(ctx -> spawnCustom(ctx.getSource().getPlayerOrException(), null, FighterArchetype.GUARDIAN)))))
                                .then(Commands.literal("pulse").executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    FighterAlignment alignment = FighterAlignment.roll(player.getRandom());
                                    return spawn(player, alignment, null);
                                }))
                                .then(Commands.literal("meditate").executes(ctx -> debugMeditateNearest(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("meditationgroup").executes(ctx -> debugMeditationGroup(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("meditatewithme").executes(ctx -> bondInviteMeditation(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("goalong").executes(ctx -> debugGoAlong(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("activity")
                                        .then(Commands.literal("all").executes(ctx -> debugActivitiesAll(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.literal("sitting").executes(ctx -> debugSit(ctx.getSource().getPlayerOrException(), 0)))
                                        .then(Commands.literal("crosslegged").executes(ctx -> debugSit(ctx.getSource().getPlayerOrException(), 1)))
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                                        java.util.List.of("fishing", "rest", "nap", "sitting", "crosslegged", "jogging", "walking", "training", "ki", "studying", "research", "food", "hunting", "flower", "tree", "stargazing", "eating", "scouting", "flight", "dancing", "social", "hangout", "walktogether", "meeting"), builder))
                                                .executes(ctx -> debugActivity(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "type")))))
                                .then(Commands.literal("accessory")
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                                        java.util.List.of("red", "blue", "green", "purple", "turtle", "workout", "piccolo", "none"), builder))
                                                .executes(ctx -> debugAccessory(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "type")))))
                                .then(Commands.literal("scientist")
                                        .executes(ctx -> debugScientist(ctx.getSource().getPlayerOrException(), false))
                                        .then(Commands.literal("summon").executes(ctx -> debugScientist(ctx.getSource().getPlayerOrException(), true)))
                                        .then(Commands.literal("diagnose").executes(ctx -> debugScientistDiagnose(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.literal("specimens")
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                                                        .executes(ctx -> debugScientistSpecimens(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "count"))))))
                                .then(Commands.literal("powercompare").executes(ctx -> debugPowerCompare(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("growthstatus").executes(ctx -> debugGrowthStatus(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("meleestatus").executes(ctx -> debugMeleeStatus(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("meleehit").executes(ctx -> debugMeleeHit(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("dayplan").executes(ctx -> debugDayPlan(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("powerspike")
                                        .then(Commands.literal("charge").executes(ctx -> debugPowerSpike(ctx.getSource().getPlayerOrException(), false, false)))
                                        .then(Commands.literal("transform").executes(ctx -> debugPowerSpike(ctx.getSource().getPlayerOrException(), false, true)))
                                        .then(Commands.literal("npccharge").executes(ctx -> debugPowerSpike(ctx.getSource().getPlayerOrException(), true, false)))
                                        .then(Commands.literal("npctransform").executes(ctx -> debugPowerSpike(ctx.getSource().getPlayerOrException(), true, true))))
                                .then(Commands.literal("appearance").executes(ctx -> debugAppearance(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("practice").executes(ctx -> debugPractice(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("reunion").executes(ctx -> debugReunion(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("fusionpartner").executes(ctx -> debugFusionPartner(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("animalmimic").executes(ctx -> debugAnimalMimic(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("companionhelp").executes(ctx -> LivingBondManager.debugCompanionHelp(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("companion").executes(ctx -> debugSpawnCompanion(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("flight").executes(ctx -> debugFlightNearest(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("form")
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 8))
                                                .executes(ctx -> debugFormNearest(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "level")))))
                                .then(Commands.literal("transform").executes(ctx -> debugTransformNearest(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("kaioken").executes(ctx -> debugKaiokenNearest(ctx.getSource().getPlayerOrException(), 2))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(2, 10))
                                                .executes(ctx -> debugKaiokenNearest(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "level")))))
                                .then(Commands.literal("dance")
                                        .executes(ctx -> debugDance(ctx.getSource().getPlayerOrException(), "random"))
                                        .then(Commands.literal("groove").executes(ctx -> debugDance(ctx.getSource().getPlayerOrException(), "groove")))
                                        .then(Commands.literal("disco").executes(ctx -> debugDance(ctx.getSource().getPlayerOrException(), "disco")))
                                        .then(Commands.literal("all").executes(ctx -> debugDanceAll(ctx.getSource().getPlayerOrException()))))
                                .then(Commands.literal("idle")
                                        .then(Commands.literal("stretch").executes(ctx -> debugIdle(ctx.getSource().getPlayerOrException(), 0)))
                                        .then(Commands.literal("side").executes(ctx -> debugIdle(ctx.getSource().getPlayerOrException(), 1)))
                                        .then(Commands.literal("all").executes(ctx -> debugIdleAll(ctx.getSource().getPlayerOrException()))))
                                .then(Commands.literal("spectate")
                                        .executes(ctx -> debugSpectateNearest(ctx.getSource().getPlayerOrException()))
                                        .then(Commands.literal("stop").executes(ctx -> FighterDebugSpectateManager.stop(ctx.getSource().getPlayerOrException()))))
                                .then(Commands.literal("horn").executes(ctx -> debugHornNearest(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("mood")
                                        .then(Commands.literal("all").executes(ctx -> debugMoodAll(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.literal("cycle").executes(ctx -> debugMoodCycle(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                                        java.util.List.of("upbeat", "content", "focused", "wary", "irritated", "somber", "weary"), builder))
                                                .executes(ctx -> debugMoodNearest(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "type")))))
                                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("police").executes(ctx -> debugPeacekeepers(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("encounter")
                                .then(Commands.literal("duel").executes(ctx -> encounter(ctx.getSource().getPlayerOrException(), EncounterType.DUEL)))
                                .then(Commands.literal("clash").executes(ctx -> encounter(ctx.getSource().getPlayerOrException(), EncounterType.CLASH)))
                                .then(Commands.literal("mugging").executes(ctx -> encounter(ctx.getSource().getPlayerOrException(), EncounterType.MUGGING)))
                                .then(Commands.literal("rescue").executes(ctx -> encounter(ctx.getSource().getPlayerOrException(), EncounterType.RESCUE)))
                                .then(Commands.literal("ambush").executes(ctx -> encounter(ctx.getSource().getPlayerOrException(), EncounterType.AMBUSH)))
                                .then(Commands.literal("brawl").executes(ctx -> encounter(ctx.getSource().getPlayerOrException(), EncounterType.BRAWL)))
                                .then(Commands.literal("frieza").executes(ctx -> encounter(ctx.getSource().getPlayerOrException(), EncounterType.FRIEZA_SKIRMISH))))
                        .then(Commands.literal("memory")
                                .then(Commands.literal("status").executes(ctx -> memoryStatus(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("mark").executes(ctx -> memoryMark(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("summon").executes(ctx -> memorySummon(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("clear").executes(ctx -> memoryClear(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("equipment")
                                .then(Commands.literal("status").executes(ctx -> arsenalStatus(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("equiptest").executes(ctx -> arsenalEquipTest(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("give").executes(ctx -> arsenalGive(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("pickup").executes(ctx -> arsenalPickup(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("inherit").executes(ctx -> arsenalInherit(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("clear").executes(ctx -> arsenalClear(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("technique")
                                .then(Commands.literal("learn").executes(ctx -> techniqueLearn(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("status").executes(ctx -> techniqueStatus(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("goal")
                                .then(Commands.literal("status").executes(ctx -> goalStatus(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("history")
                                .then(Commands.literal("inspect").executes(ctx -> legacyInspect(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("fallen").executes(ctx -> legacyFallen(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("rivalry")
                                .then(Commands.literal("force").executes(ctx -> rivalryForce(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("maintenance")
                                .then(Commands.literal("runtime").executes(ctx -> maintenanceRuntime(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("faction")
                                .executes(ctx -> factionList(ctx.getSource().getPlayerOrException()))
                                .then(Commands.literal("list").executes(ctx -> factionList(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("status").executes(ctx -> factionStatus(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("inspect")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                                .executes(ctx -> factionInspect(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot")))))
                                .then(Commands.literal("locate")
                                        .executes(ctx -> factionLocate(ctx.getSource().getPlayerOrException(), 0))
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                                .executes(ctx -> factionLocate(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot")))))
                                .then(Commands.literal("tp")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                                .executes(ctx -> factionTeleport(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot")))))
                                .then(Commands.literal("presence")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                                .executes(ctx -> factionPresence(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot")))))
                                .then(Commands.literal("patrol")
                                        .executes(ctx -> factionPatrol(ctx.getSource().getPlayerOrException(), 0))
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                                .executes(ctx -> factionPatrol(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot")))))
                                .then(Commands.literal("clash")
                                        .then(Commands.argument("first", IntegerArgumentType.integer(1, 512))
                                                .then(Commands.argument("second", IntegerArgumentType.integer(1, 512))
                                                        .executes(ctx -> factionClash(ctx.getSource().getPlayerOrException(),
                                                                IntegerArgumentType.getInteger(ctx, "first"), IntegerArgumentType.getInteger(ctx, "second"))))))
                                .then(Commands.literal("war")
                                        .then(Commands.argument("first", IntegerArgumentType.integer(1, 512))
                                                .then(Commands.argument("second", IntegerArgumentType.integer(1, 512))
                                                        .executes(ctx -> factionWar(ctx.getSource().getPlayerOrException(),
                                                                IntegerArgumentType.getInteger(ctx, "first"), IntegerArgumentType.getInteger(ctx, "second"), false)))))
                                .then(Commands.literal("peace")
                                        .then(Commands.argument("first", IntegerArgumentType.integer(1, 512))
                                                .then(Commands.argument("second", IntegerArgumentType.integer(1, 512))
                                                        .executes(ctx -> factionWar(ctx.getSource().getPlayerOrException(),
                                                                IntegerArgumentType.getInteger(ctx, "first"), IntegerArgumentType.getInteger(ctx, "second"), true)))))
                                .then(Commands.literal("leader")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                                .executes(ctx -> factionLeader(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot")))))
                                .then(Commands.literal("history")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                                .executes(ctx -> factionHistory(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot")))))
                                .then(Commands.literal("forage")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                                .executes(ctx -> factionForage(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot")))))
                                .then(Commands.literal("defect")
                                        .then(Commands.argument("from", IntegerArgumentType.integer(1, 512))
                                                .then(Commands.argument("to", IntegerArgumentType.integer(1, 512))
                                                        .executes(ctx -> factionDefect(ctx.getSource().getPlayerOrException(),
                                                                IntegerArgumentType.getInteger(ctx, "from"), IntegerArgumentType.getInteger(ctx, "to"))))))
                                .then(Commands.literal("simday").executes(ctx -> factionSimDay(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("collapse")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                                .executes(ctx -> factionCollapse(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot")))))
                                .then(Commands.literal("newcult")
                                        .then(Commands.literal("earth").executes(ctx -> factionNewCult(ctx.getSource().getPlayerOrException(), FactionRealm.EARTH)))
                                        .then(Commands.literal("namek").executes(ctx -> factionNewCult(ctx.getSource().getPlayerOrException(), FactionRealm.NAMEK))))
                                .then(Commands.literal("setrep")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                                .then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
                                                        .executes(ctx -> factionSetRep(ctx.getSource().getPlayerOrException(),
                                                                IntegerArgumentType.getInteger(ctx, "slot"), IntegerArgumentType.getInteger(ctx, "value")))))))
                        .then(Commands.literal("wanted")
                                .executes(ctx -> wantedList(ctx.getSource().getPlayerOrException()))
                                .then(Commands.literal("list").executes(ctx -> wantedList(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("locate")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                                .executes(ctx -> wantedLocate(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot"), false))))
                                .then(Commands.literal("tp")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                                .executes(ctx -> wantedLocate(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot"), true))))
                                .then(Commands.literal("spawn")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                                .executes(ctx -> wantedSpawn(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot")))))
                                .then(Commands.literal("track")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                                .executes(ctx -> wantedTrack(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot")))))
                                .then(Commands.literal("untrack").executes(ctx -> wantedUntrack(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("playerstatus").executes(ctx -> wantedPlayerStatus(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("playeradd")
                                        .then(Commands.argument("pressure", IntegerArgumentType.integer(1, 100))
                                                .executes(ctx -> wantedPlayerAdd(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "pressure")))))
                                .then(Commands.literal("playerclear").executes(ctx -> wantedPlayerClear(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("request")
                                .then(Commands.literal("status").executes(ctx -> requestStatus(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("provisions").executes(ctx -> FactionRequestManager.force(ctx.getSource().getPlayerOrException(), "PROVISIONS")))
                                .then(Commands.literal("materials").executes(ctx -> FactionRequestManager.force(ctx.getSource().getPlayerOrException(), "MATERIALS")))
                                .then(Commands.literal("patrol").executes(ctx -> FactionRequestManager.force(ctx.getSource().getPlayerOrException(), "PATROL")))
                                .then(Commands.literal("warstockpile").executes(ctx -> FactionRequestManager.force(ctx.getSource().getPlayerOrException(), "WAR_STOCKPILE")))
                                .then(Commands.literal("reparations").executes(ctx -> FactionRequestManager.force(ctx.getSource().getPlayerOrException(), "REPARATIONS")))
                                .then(Commands.literal("refresh")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                                .executes(ctx -> FactionRequestManager.forceRefreshOffer(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot"))))))
                        .then(Commands.literal("bond")
                                .then(Commands.literal("status").executes(ctx -> bondStatus(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("meditate").executes(ctx -> bondInviteMeditation(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("companion").executes(ctx -> bondInviteCompanion(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("clear").executes(ctx -> bondClearCompanion(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("world")
                                .then(Commands.literal("earth").executes(ctx -> worldTeleport(ctx.getSource().getPlayerOrException(), FactionRealm.EARTH)))
                                .then(Commands.literal("namek").executes(ctx -> worldTeleport(ctx.getSource().getPlayerOrException(), FactionRealm.NAMEK))))
                        .then(Commands.literal("sense").executes(ctx -> sense(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("fight")
                                .then(Commands.literal("clash").executes(ctx -> fightStyles(ctx.getSource().getPlayerOrException(), "ki", "ki")))
                                .then(Commands.argument("first", StringArgumentType.word())
                                        .then(Commands.argument("second", StringArgumentType.word())
                                                .executes(ctx -> fightStyles(
                                                        ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "first"),
                                                        StringArgumentType.getString(ctx, "second"))))))
        );

        // Player-facing Living World interface.
        event.getDispatcher().register(
                Commands.literal("livingworld")
                        .executes(ctx -> openWorldOverview(ctx.getSource().getPlayerOrException()))
                        .then(Commands.literal("factions")
                                .executes(ctx -> openPlayerFactionList(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("faction")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                        .executes(ctx -> openPlayerFaction(ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "slot")))))
                        .then(Commands.literal("history")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 512))
                                        .executes(ctx -> factionHistory(ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "slot")))))
                        .then(Commands.literal("people")
                                .executes(ctx -> openPeople(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("companion")
                                .executes(ctx -> openTravel(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("wanted")
                                .executes(ctx -> wantedList(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("antagonists")
                                .executes(ctx -> openAntagonists(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("menace")
                                .executes(ctx -> openWorldMenace(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("guide")
                                .executes(ctx -> openGuide(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("settings")
                                .executes(ctx -> openSettings(ctx.getSource().getPlayerOrException())))
        );
    }

    private static int debugExperimentStatus(ServerPlayer player) {
        if (player == null) return 0;
        player.displayClientMessage(Component.literal("[LW] Red Ribbon Experiment: " + RedRibbonExperimentManager.status(player)).withStyle(ChatFormatting.DARK_RED), false);
        return Command.SINGLE_SUCCESS;
    }

    public static int openWorldMenace(ServerPlayer player) {
        if (player == null) return 0;
        java.util.List<String> lines = new java.util.ArrayList<>();
        java.util.List<FactionDossierPacket.Portrait> portraits = new java.util.ArrayList<>();
        lines.add("## World Menaces");

        boolean herobrineKnown = WorldMenaceManager.hasSpotted(player);
        boolean experimentKnown = RedRibbonExperimentManager.hasSpotted(player);
        if (!herobrineKnown && !experimentKnown) {
            lines.add(". No unique world menace has been identified yet.");
            lines.add("~ Persistent threats become dossier entries only after you actually encounter them in-world.");
            LWNetwork.sendFactionDossier(player, new FactionDossierPacket("menace", 0, "Living World — World Menaces",
                    "No confirmed sighting", lines, "", portraits));
            return Command.SINGLE_SUCCESS;
        }

        if (herobrineKnown) {
            WorldMenaceData data = WorldMenaceData.get(player.serverLevel());
            java.util.UUID menaceId = WorldMenaceManager.dossierRecordId();
            lines.add("@person:" + menaceId + "|!! Herobrine  •  WORLD MENACE  •  " + WorldMenaceManager.status(player));
            lines.add("* Confirmed sightings: " + WorldMenaceManager.sightingCount(player));
            net.minecraft.nbt.CompoundTag spotted = WorldMenaceManager.knownProfile(player);
            if (!spotted.isEmpty()) portraits.add(new FactionDossierPacket.Portrait(menaceId, spotted));
        }

        if (experimentKnown) {
            java.util.UUID id = RedRibbonExperimentManager.dossierRecordId();
            lines.add("@person:" + id + "|!! Red Ribbon Experiment X-7  •  WORLD MENACE  •  " + RedRibbonExperimentManager.status(player));
            lines.add("* Confirmed sightings: " + RedRibbonExperimentManager.sightings(player));
            net.minecraft.nbt.CompoundTag spotted = RedRibbonExperimentManager.knownProfile(player);
            if (!spotted.isEmpty()) portraits.add(new FactionDossierPacket.Portrait(id, spotted));
        }

        LWNetwork.sendFactionDossier(player, new FactionDossierPacket("menace", 0, "Living World — World Menaces",
                "Confirmed persistent threats", lines, "", portraits));
        return Command.SINGLE_SUCCESS;
    }

    public static int openWorldMenaceProfile(ServerPlayer player) { return openWorldMenaceProfile(player, 0); }

    public static int openWorldMenaceProfile(ServerPlayer player, int slot) {
        if (player == null) return 0;
        if (slot == 1) {
            if (!RedRibbonExperimentManager.hasSpotted(player)) return openWorldMenace(player);
            FighterInspectionManager.inspectRedRibbonExperiment(player);
        } else {
            if (!WorldMenaceManager.hasSpotted(player)) return openWorldMenace(player);
            FighterInspectionManager.inspectWorldMenace(player);
        }
        return Command.SINGLE_SUCCESS;
    }

    public static int worldMenaceTeleportFromMenu(ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) return 0;
        return WorldMenaceManager.debugTeleport(player) > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    public static int worldMenaceSpawnFromMenu(ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) return 0;
        int result = WorldMenaceManager.debugSpawn(player);
        if (result > 0) openWorldMenace(player);
        return result > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    public static int openGuide(ServerPlayer player) {
        LWNetwork.sendFactionDossier(player, new FactionDossierPacket("guide", 0, "Living World — Guide",
                "Player guide", java.util.List.of()));
        return Command.SINGLE_SUCCESS;
    }

    public static int openSettings(ServerPlayer player) {
        if (player == null) return 0;
        LWNetwork.sendWorldSettings(player, WorldSettingsUpdatePacket.canEdit(player));
        return Command.SINGLE_SUCCESS;
    }

    public static int openMeditationInfo(ServerPlayer player) {
        if (player == null) return 0;
        List<String> lines = new java.util.ArrayList<>();
        lines.add("## Meditation");
        lines.add("* Meditation TP follows your current Dragon Mine Z training progression instead of using a flat reward.");
        lines.add("* Calm starts with a small TP pulse; deeper stages gradually improve the reward while keeping meditation below active training.");
        lines.add("* Deep/Transcendent meditation can slowly train the active Dragon Mine Z form when enabled.");
        lines.add("* A Meditative Breakthrough increases one base stat by the percentage set in World Settings.");
        lines.add("* Deeper stages strengthen the aura/Focus Seal beneath the character and improve configured rewards.");
        lines.add("## Living World");
        lines.add("* Shift + Right-click a fighter you know → Meditate to train beside them.");
        lines.add("* You can invite several nearby friendly fighters into the same meditation session; adding another partner does not restart your meditation.");
        int partners = LivingBondManager.meditationPartnerCount(player);
        lines.add("~ Meditation circle: " + partners + "/4 nearby fighters");
        lines.add("@meditation:invite|Invite nearby friend");
        lines.add(". Staying in shared meditation can gradually strengthen your relationship with each fighter.");
        LWNetwork.sendFactionDossier(player, new FactionDossierPacket("meditation", 0, "Living World • Meditation",
                "Press M to start or stop meditation", lines));
        return Command.SINGLE_SUCCESS;
    }

    public static int openWorldOverview(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        FactionWorldData data = FactionWorldData.get(level);
        data.tickOrganizations(level);
        if (data.earthGuardians() != null) PlayerWorldManager.discoverFaction(player, data.earthGuardians());
        if (data.blackSun() != null) PlayerWorldManager.discoverFaction(player, data.blackSun());

        List<String> lines = new java.util.ArrayList<>();
        FactionRequestManager.ActiveQuestView quest = FactionRequestManager.activeQuestView(player);
        lines.add("## Active Quest");
        if (quest.active()) {
            lines.add("+ " + quest.title() + " • " + quest.factionName());
            if (!quest.progress().isBlank()) lines.add("* " + quest.progress());
            if (!quest.note().isBlank()) lines.add(". " + quest.note());
        } else lines.add(". No active faction request.");

        lines.add("## World");
        lines.add("* " + LivingWorldDimensions.realm(level).displayName() + " • " + WorldEraData.get(level).era().displayName());

        // R40 restores the useful pre-declutter world-power snapshot without restoring the old
        // prose-heavy presentation. These two institutions are public knowledge and remain clickable.
        lines.add("## Major World Powers");
        WorldFaction guardians = data.earthGuardians(), blackSun = data.blackSun();
        if (guardians != null) lines.add("+ #" + guardians.slot() + " " + guardians.name() + " • Peacekeepers • "
                + data.fighterPopulation(guardians) + " fighters • momentum x"
                + String.format(java.util.Locale.ROOT, "%.2f", data.momentum(guardians)));
        if (blackSun != null) lines.add("!! #" + blackSun.slot() + " " + blackSun.name() + " • Hostile power • "
                + data.fighterPopulation(blackSun) + " fighters • momentum x"
                + String.format(java.util.Locale.ROOT, "%.2f", data.momentum(blackSun)));

        long now = level.getServer().overworld().getGameTime();
        java.util.Set<String> wars = new java.util.LinkedHashSet<>();
        for (WorldFaction faction : data.activeFactions()) {
            if (faction.realm() != LivingWorldDimensions.realm(level)) continue;
            for (WorldFaction enemy : data.warEnemies(faction, now)) {
                if (enemy == null) continue;
                String key = faction.slot() < enemy.slot() ? faction.id() + ":" + enemy.id() : enemy.id() + ":" + faction.id();
                if (wars.stream().noneMatch(line -> line.startsWith(key + "|")))
                    wars.add(key + "|" + faction.name() + " ↔ " + enemy.name());
            }
        }
        if (wars.isEmpty()) lines.add(". No known faction war on this world.");
        else {
            int shown = 0;
            for (String war : wars) {
                int split = war.indexOf('|');
                lines.add("!! " + (split >= 0 ? war.substring(split + 1) : war));
                if (++shown >= 3) break;
            }
        }

        java.util.List<String> recentIncidents = WorldIncidentData.get(level).recent(1);
        List<String> rumors = PlayerWorldManager.rumors(player);
        if (!recentIncidents.isEmpty() || !rumors.isEmpty()) {
            lines.add("## Recent");
            if (!recentIncidents.isEmpty()) lines.add("* " + recentIncidents.get(0));
            if (!rumors.isEmpty()) lines.add("~ " + rumors.get(0));
        }

        LWNetwork.sendFactionDossier(player, new FactionDossierPacket("world", 0, "DragonMine Z: Living World",
                "Overview", lines));
        return Command.SINGLE_SUCCESS;
    }

    public static int openPlayerFactionList(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        FactionWorldData data = FactionWorldData.get(level); data.tickOrganizations(level);
        if (data.earthGuardians() != null) PlayerWorldManager.discoverFaction(player, data.earthGuardians());
        if (data.blackSun() != null) PlayerWorldManager.discoverFaction(player, data.blackSun());
        List<String> lines = new java.util.ArrayList<>();
        int known = 0, unknown = 0;
        for (FactionRealm realm : new FactionRealm[]{FactionRealm.EARTH, FactionRealm.NAMEK}) {
            lines.add("## " + realm.displayName());
            boolean any = false;
            for (WorldFaction faction : data.activeFactions()) {
                if (faction.realm() != realm) continue;
                if (!PlayerWorldManager.knowsFaction(player, faction)) { unknown++; continue; }
                any = true; known++;
                int rep = FactionManager.getReputation(player, faction);
                String state = data.publicState(faction, level.getServer().overworld().getGameTime());
                lines.add((state.equals("AT WAR") ? "!! " : "* ") + "#" + faction.slot() + "  " + faction.name()
                        + " — " + faction.structure().displayName() + " • " + state + " • " + FactionManager.reputationLabel(rep));
            }
            if (!any) lines.add(". No organization from this world has reached your knowledge yet.");
        }
        LWNetwork.sendFactionDossier(player, new FactionDossierPacket("factions", 0, "Living World — Factions",
                known + " known organizations • " + unknown + " still unknown", lines));
        return Command.SINGLE_SUCCESS;
    }

    public static int openPlayerActiveFactionQuest(ServerPlayer player) {
        FactionRequestManager.ActiveQuestView view = FactionRequestManager.activeQuestView(player);
        List<String> lines = new java.util.ArrayList<>();
        if (!view.active()) {
            lines.add("## Active Quest");
            lines.add(". " + view.description());
            if (!view.note().isBlank()) lines.add("~ " + view.note());
        } else {
            lines.add("## Issuing faction");
            lines.add("+ #" + view.factionSlot() + "  " + view.factionName());
            if (!view.targetFactionName().isBlank() && !view.targetFactionName().equals(view.factionName()))
                lines.add("!! Opposing faction • " + view.targetFactionName());
            lines.add("## " + view.title());
            if (!view.description().isBlank()) lines.add("* " + view.description());
            if (!view.difficulty().isBlank()) lines.add("~ Difficulty: " + view.difficulty());
            if (!view.reward().isBlank()) lines.add("+ Reward: " + view.reward());
            if (!view.progress().isBlank()) lines.add("~ " + view.progress());
            if (!view.note().isBlank()) lines.add(". " + view.note());
            lines.add("@activequest:open|Open Request Board");
            lines.add("@activequest:travel|Travel / Contacts");
        }
        LWNetwork.sendFactionDossier(player, new FactionDossierPacket("faction_active", view.active() ? view.factionSlot() : 0,
                "Living World — Active Quest", view.active() ? "Issued by " + view.factionName() : "No accepted faction request", lines));
        return Command.SINGLE_SUCCESS;
    }

    public static int openPlayerFaction(ServerPlayer player, int slot) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction faction = FactionManager.bySlot(level, slot);
        if (faction == null) return missingFaction(player, slot);
        if (!PlayerWorldManager.knowsFaction(player, faction)) {
            LWNetwork.sendFactionDossier(player, new FactionDossierPacket("faction", slot, "Unknown Organization",
                    "You have not learned enough about faction #" + slot, List.of(
                    ". Find their members, enter their rally region, or learn about them through another faction.",
                    ". Learn more by meeting members and hearing about them in the world.")));
            return Command.SINGLE_SUCCESS;
        }
        return factionInspect(player, slot);
    }

    public static int openPlayerFactionRequests(ServerPlayer player, int slot) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction faction = FactionManager.bySlot(level, slot);
        if (faction == null) return missingFaction(player, slot);
        if (!PlayerWorldManager.knowsFaction(player, faction)) return openPlayerFaction(player, slot);
        FactionWorldData.get(level).tickOrganizations(level);
        FactionRequestManager.RequestView view = FactionRequestManager.requestView(player, faction);
        FactionRequestManager.TravelView travel = FactionRequestManager.travelView(player, faction);
        LWNetwork.sendFactionRequestScreen(player, com.dmzlivingworld.network.FactionRequestScreenPacket.from(view, travel));
        return Command.SINGLE_SUCCESS;
    }

    public static int openPlayerFactionTravel(ServerPlayer player, int slot) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction faction = FactionManager.bySlot(level, slot);
        if (faction == null) return missingFaction(player, slot);
        if (!PlayerWorldManager.knowsFaction(player, faction)) return openPlayerFaction(player, slot);
        FactionRequestManager.TravelView view = FactionRequestManager.travelView(player, faction);
        LWNetwork.sendFactionTravelScreen(player, com.dmzlivingworld.network.FactionTravelScreenPacket.from(view));
        return Command.SINGLE_SUCCESS;
    }

    public static int openPlayerFactionRoster(ServerPlayer player, int slot) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction faction = FactionManager.bySlot(level, slot);
        if (faction == null) return missingFaction(player, slot);
        if (!PlayerWorldManager.knowsFaction(player, faction)) return openPlayerFaction(player, slot);

        FactionWorldData data = FactionWorldData.get(level);
        data.tickOrganizations(level);
        List<String> lines = new java.util.ArrayList<>();
        java.util.List<FactionDossierPacket.Portrait> portraits = new java.util.ArrayList<>();
        java.util.Set<String> shownNames = new java.util.HashSet<>();
        java.util.List<FighterMemoryManager.KnownFactionPerson> known =
                new java.util.ArrayList<>(FighterMemoryManager.knownFactionPeople(player, faction.id()));
        known.sort(java.util.Comparator.comparingInt((FighterMemoryManager.KnownFactionPerson person) -> person.role().id()).reversed()
                .thenComparing(FighterMemoryManager.KnownFactionPerson::name));

        // Faction people use the same remembered portrait cards as People/Wanted. The appearance
        // payload is SeenProfile, so a person's dossier avatar is the last appearance the player
        // actually witnessed rather than silently updating to their current unloaded simulation state.
        lines.add("## Leadership");
        String leaderName = data.currentLeaderName(faction);
        String leaderStatus = data.isLeaderKilled(faction) ? "fallen • succession pending"
                : data.isLeaderSpawned(faction) ? "currently present" : "currently away";
        FighterMemoryManager.KnownFactionPerson knownLeader = known.stream()
                .filter(person -> leaderName.equals(person.name())).findFirst().orElse(null);
        String leaderText = (data.isLeaderKilled(faction) ? "!! " : "+ ") + faction.roleTitle(FactionRole.LEADER)
                + " • " + leaderName + " — " + leaderStatus;
        if (knownLeader != null) {
            lines.add("@person:" + knownLeader.recordId() + "|" + leaderText);
            portraits.add(new FactionDossierPacket.Portrait(knownLeader.recordId(), knownLeader.appearance().copy()));
        } else lines.add(leaderText);
        shownNames.add(leaderName);

        lines.add("## Members you know");
        int knownOthers = 0;
        for (FighterMemoryManager.KnownFactionPerson person : known) {
            if (!shownNames.add(person.name())) continue;
            knownOthers++;
            String activity = person.activity() == null || person.activity().isBlank() ? "last seen away" : person.activity();
            lines.add("@person:" + person.recordId() + "|* " + faction.roleTitle(person.role()) + " • " + person.name()
                    + " — " + person.rank().displayName() + " • "
                    + FighterRelationshipManager.relationshipStage(person.relationship()) + " • " + activity);
            portraits.add(new FactionDossierPacket.Portrait(person.recordId(), person.appearance().copy()));
        }
        if (knownOthers == 0) lines.add(". You have not personally identified any other members yet.");

        int knownNamed = knownOthers + 1;
        int estimate = Math.max(knownNamed, data.population(faction));
        lines.add("## Membership");
        lines.add("* " + knownNamed + " named member" + (knownNamed == 1 ? "" : "s")
                + " known to you • about " + estimate + " members in the faction");
        if (estimate > knownNamed) lines.add(". You have not identified everyone in this faction yet.");
        else lines.add(". You currently know the named members represented here.");

        LWNetwork.sendFactionDossier(player, new FactionDossierPacket("faction_roster", slot,
                "#" + slot + "  " + faction.name(), faction.realm().displayName() + " • Members", lines, "", portraits));
        return Command.SINGLE_SUCCESS;
    }

    public static int openPeople(ServerPlayer player) {
        List<String> lines = new java.util.ArrayList<>();
        String companion = LivingBondManager.companionName(player);
        if (!companion.isBlank()) {
            lines.add("## Current companion");
            lines.add("+ " + companion + " is travelling with you");
            lines.add("@travel:recall|Regroup " + companion);
            lines.add("@travel:end|End travel with " + companion);
        }
        // Instant Transmission status belongs to the selected remembered fighter, not the
        // directory itself. People remains an index; open a person to see live lock/cooldown state.
        lines.add("@sort:people|Sort: " + FighterMemoryManager.peopleSortLabel(player));
        lines.add("## Remembered people");
        lines.addAll(FighterMemoryManager.peopleLines(player));
        if (FighterMemoryManager.count(player) > 0) lines.add("@clear:known|Forget all remembered people");
        java.util.List<FighterLegacyWorldData.FallenEntry> fallenEntries = java.util.List.of();
        if (player.level() instanceof ServerLevel level) {
            fallenEntries = FighterLegacyWorldData.get(level).recentEntriesSince(5, FighterMemoryManager.fallenViewSince(player));
            if (!fallenEntries.isEmpty()) {
                lines.add("## Recently fallen");
                for (int fallenIndex = 0; fallenIndex < fallenEntries.size(); fallenIndex++) {
                    FighterLegacyWorldData.FallenEntry fallen = fallenEntries.get(fallenIndex);
                    if (fallen.recordId() != null && !fallen.appearance().isEmpty())
                        lines.add("@fallen:" + fallen.recordId() + "|#" + (fallenIndex + 1) + " " + fallen.line());
                    else lines.add(". " + fallen.line());
                }
                lines.add("@clear:fallen|Clear fallen history from this view");
            }
        }
        lines.add(". Strong bonds come from shared history: rescues, fights, travel, training, equipment and meditation.");
        java.util.List<FactionDossierPacket.Portrait> portraits = new java.util.ArrayList<>();
        FighterMemoryManager.peoplePortraitSnapshots(player).forEach((id, appearance) ->
                portraits.add(new FactionDossierPacket.Portrait(id, appearance)));
        for (FighterLegacyWorldData.FallenEntry fallen : fallenEntries)
            if (fallen.recordId() != null && !fallen.appearance().isEmpty())
                portraits.add(new FactionDossierPacket.Portrait(fallen.recordId(), fallen.appearance()));
        LWNetwork.sendFactionDossier(player, new FactionDossierPacket("people", 0, "Living World — People",
                "Click a person to view what you last remember about them", lines, "", portraits));
        return Command.SINGLE_SUCCESS;
    }

    public static int openTravel(ServerPlayer player) {
        List<String> lines = new java.util.ArrayList<>();
        String companion = LivingBondManager.companionName(player);
        lines.add("## Current companion");
        if (companion.isBlank() || LivingBondManager.companionId(player) == null) {
            lines.add(". Nobody is travelling with you right now.");
            lines.add(". A fighter who trusts you can sometimes agree to come along from their profile.");
        } else {
            lines.add("+ " + companion + " is travelling with you");
            lines.add(". If they fall behind, change dimensions, or their chunk unloads, Living World now attempts to regroup them automatically.");
            lines.add("@travel:recall|Regroup companion");
            lines.add("@travel:end|End travel");
        }
        LWNetwork.sendFactionDossier(player, new FactionDossierPacket("travel", 0, "Living World — Companion",
                "Manage the fighter currently travelling with you", lines));
        return Command.SINGLE_SUCCESS;
    }

    public static int openWanted(ServerPlayer player) { return wantedList(player); }

    public static int openWantedProfile(ServerPlayer player, int slot) {
        if (player == null || slot <= 0) return 0;
        FighterInspectionManager.inspectWanted(player, slot);
        return Command.SINGLE_SUCCESS;
    }

    /** Fallen rows use a temporary integer slot in the client request, resolved against current archive order. */
    public static int openFallenProfile(ServerPlayer player, int slot) {
        if (player == null || slot <= 0) return 0;
        java.util.List<FighterLegacyWorldData.FallenEntry> entries = FighterLegacyWorldData.get(player.serverLevel()).recentEntriesSince(64, 0L);
        if (slot > entries.size()) return 0;
        FighterLegacyWorldData.FallenEntry entry = entries.get(slot - 1);
        if (entry.recordId() == null) return 0;
        FighterInspectionManager.inspectFallen(player, entry.recordId());
        return Command.SINGLE_SUCCESS;
    }

    /** Player-facing record of recurring major opposition the player has actually learned about. */
    public static int openAntagonists(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        FactionWorldData data = FactionWorldData.get(level);
        data.tickOrganizations(level);
        AntagonistWorldData antagonists = AntagonistWorldData.get(level);
        List<String> lines = new java.util.ArrayList<>();
        int knownOrganizations = 0;
        int hiddenOrganizations = 0;

        lines.add("## Organizations");
        for (WorldFaction faction : data.activeFactions()) {
            if (!antagonists.isAntagonistFaction(faction.id())) continue;
            if (!PlayerWorldManager.knowsFaction(player, faction)) { hiddenOrganizations++; continue; }
            knownOrganizations++;
            int rep = FactionManager.getReputation(player, faction);
            String state = data.publicState(faction, level.getServer().overworld().getGameTime());
            lines.add("!! #" + faction.slot() + "  " + faction.name() + " — " + state
                    + " • " + FactionManager.reputationLabel(rep));
            int knownPeople = FighterMemoryManager.knownFactionPeople(player, faction.id()).size();
            if (knownPeople == 0) lines.add(". You know the organization, but have not personally learned much about its members yet.");
            else lines.add(". " + knownPeople + " member" + (knownPeople == 1 ? "" : "s") + " personally known to you.");
        }
        if (knownOrganizations == 0) lines.add(". You have not identified any major hostile organization yet.");

        lines.add("## People");
        List<String> people = FighterMemoryManager.antagonistLines(player);
        if (people.isEmpty()) lines.add(". No recurring individual antagonist has become part of your remembered history yet.");
        else lines.addAll(people);

        if (hiddenOrganizations > 0) lines.add(". Some hostile organizations remain unidentified.");
        java.util.List<FactionDossierPacket.Portrait> portraits = new java.util.ArrayList<>();
        FighterMemoryManager.peoplePortraitSnapshots(player).forEach((id, appearance) ->
                portraits.add(new FactionDossierPacket.Portrait(id, appearance)));
        LWNetwork.sendFactionDossier(player, new FactionDossierPacket("antagonists", 0, "Living World — Antagonists",
                "Recurring opposition known to you", lines, "", portraits));
        return Command.SINGLE_SUCCESS;
    }

    private static int arsenalStatus(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) { player.displayClientMessage(Component.literal("[Living World] No fighter nearby."), false); return 0; }
        player.displayClientMessage(Component.literal("[Living World] " + fighter.getFighterName() + " gear: "
                + FighterArsenalManager.summary(fighter) + " • GeoItemID: "
                + FighterArsenalManager.geoItemIdentitySummary(fighter)).withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int arsenalEquipTest(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) { player.displayClientMessage(Component.literal("[Living World] No fighter nearby."), false); return 0; }
        FighterArsenalManager.debugEquipTest(fighter);
        player.displayClientMessage(Component.literal("[Living World] Equipped real DMZ fighter armor + blaster on " + fighter.getFighterName()
                + ". Gear: " + FighterArsenalManager.summary(fighter)).withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int arsenalGive(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) { player.displayClientMessage(Component.literal("[Living World] No fighter nearby."), false); return 0; }
        boolean ok = FighterArsenalManager.tryGift(player, fighter, player.getMainHandItem(), true);
        player.displayClientMessage(Component.literal(ok ? "[Living World] Debug-gifted the held DMZ equipment to " + fighter.getFighterName()
                : "[Living World] Hold a supported DMZ weapon or armor item first.").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return ok ? Command.SINGLE_SUCCESS : 0;
    }

    private static int arsenalClear(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) { player.displayClientMessage(Component.literal("[Living World] No fighter nearby."), false); return 0; }
        FighterArsenalManager.clearEquipment(fighter);
        player.displayClientMessage(Component.literal("[Living World] Cleared " + fighter.getFighterName() + " equipment for testing.").withStyle(ChatFormatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int legacyInspect(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) { player.displayClientMessage(Component.literal("[Living World] No fighter nearby."), false); return 0; }
        String title = fighter.getLegacyTitle();
        player.displayClientMessage(Component.literal("[Living World] " + fighter.getFighterName()
                + (title.isBlank() ? "" : " • " + title) + " • " + fighter.getLegacySummary()
                + " • Gear: " + FighterArsenalManager.summary(fighter)).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        net.minecraft.nbt.ListTag events = fighter.getLegacyData().getList("Events", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = Math.max(0, events.size() - 5); i < events.size(); i++) {
            player.displayClientMessage(Component.literal("  • " + events.getString(i)).withStyle(ChatFormatting.GRAY), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int legacyFallen(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        java.util.List<String> lines = FighterLegacyWorldData.get(level).recentLines(10);
        player.displayClientMessage(Component.literal("[Living World] Archived notable fallen: " + FighterLegacyWorldData.get(level).count())
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        if (lines.isEmpty()) player.displayClientMessage(Component.literal("  none yet").withStyle(ChatFormatting.GRAY), false);
        else for (String line : lines) player.displayClientMessage(Component.literal("  " + line).withStyle(ChatFormatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int maintenanceRuntime(ServerPlayer player) {
        player.displayClientMessage(Component.literal("[Living World] " + LivingWorldLifecycle.runtimeSummary())
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int arsenalPickup(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) { player.displayClientMessage(Component.literal("[Living World] No fighter nearby."), false); return 0; }
        boolean ok = FighterArsenalManager.tryPickupNearby(fighter);
        player.displayClientMessage(Component.literal("[Living World] " + (ok ? "Picked up nearby usable equipment. Gear: " + FighterArsenalManager.summary(fighter) : "No usable equipment pickup found.")), false);
        return ok ? Command.SINGLE_SUCCESS : 0;
    }

    private static int arsenalInherit(ServerPlayer player) {
        List<AmbientFighterEntity> fighters = player.serverLevel().getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(48.0D), f -> f.isAlive() && !com.dmzlwfusion.NpcFusionManager.isHiddenFusionPartner(f))
                .stream().sorted(java.util.Comparator.comparingDouble(player::distanceToSqr)).limit(2).toList();
        if (fighters.size() < 2) {
            player.displayClientMessage(Component.literal("[Living World] Need two fighters nearby. Closest = heir, second = donor."), false);
            return 0;
        }
        AmbientFighterEntity heir = fighters.get(0);
        AmbientFighterEntity donor = fighters.get(1);
        boolean ok = FighterArsenalManager.debugTransferInheritance(donor, heir);
        player.displayClientMessage(Component.literal(ok
                ? "[Living World] Moved one real item from " + donor.getFighterName() + " to heir " + heir.getFighterName() + "."
                : "[Living World] No inheritable upgrade found on the donor."), false);
        return ok ? Command.SINGLE_SUCCESS : 0;
    }

    private static int techniqueStatus(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) { player.displayClientMessage(Component.literal("[Living World] No fighter nearby."), false); return 0; }
        player.displayClientMessage(Component.literal("[Living World] " + fighter.getFighterName() + " learned: " + FighterTechniqueManager.summary(fighter)), false);
        player.displayClientMessage(Component.literal("[Living World] Lineage: " + FighterTechniqueManager.lineageSummary(fighter)).withStyle(ChatFormatting.DARK_AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int techniqueLearn(ServerPlayer player) {
        List<AmbientFighterEntity> fighters = player.serverLevel().getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(48.0D), f -> f.isAlive() && !com.dmzlwfusion.NpcFusionManager.isHiddenFusionPartner(f))
                .stream().sorted(java.util.Comparator.comparingDouble(player::distanceToSqr)).limit(2).toList();
        if (fighters.size() < 2) {
            player.displayClientMessage(Component.literal("[Living World] Need two fighters nearby. Closest = learner, second = source."), false);
            return 0;
        }
        AmbientFighterEntity learner = fighters.get(0);
        AmbientFighterEntity source = fighters.get(1);
        boolean ok = FighterTechniqueManager.tryLearnFrom(learner, source, "debug test");
        player.displayClientMessage(Component.literal(ok ? "[Living World] " + learner.getFighterName() + " learned from " + source.getFighterName()
                : "[Living World] No new compatible technique could be learned."), false);
        return ok ? Command.SINGLE_SUCCESS : 0;
    }

    private static int goalStatus(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) { player.displayClientMessage(Component.literal("[Living World] No fighter nearby."), false); return 0; }
        player.displayClientMessage(Component.literal("[Living World] " + fighter.getFighterName() + " goal: " + FighterGoalManager.summary(fighter)).withStyle(ChatFormatting.YELLOW), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int rivalryForce(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) { player.displayClientMessage(Component.literal("[Living World] No fighter nearby."), false); return 0; }
        boolean ok = FighterMemoryManager.forceRivalryEncounter(player, fighter);
        player.displayClientMessage(Component.literal(ok ? "[Living World] Recorded one rivalry encounter for " + fighter.getFighterName()
                + " • battles " + fighter.getPlayerRivalBattles() + " • wins " + fighter.getPlayerRivalWins()
                : "[Living World] Could not record rivalry. Use a living hostile fighter for rivalry testing.")
                .withStyle(ok ? ChatFormatting.RED : ChatFormatting.GRAY), false);
        return ok ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugMeditationGroup(ServerPlayer player) {
        int ok = FighterNpcSocialManager.forceMeditationCircle(player);
        player.displayClientMessage(Component.literal(ok > 0
                ? "[Living World] Started a real nearby NPC meditation circle."
                : "[Living World] Need at least two available nearby fighters and NPC Meditation enabled."), false);
        return ok > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugMeditateNearest(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) { player.displayClientMessage(Component.literal("[Living World] No fighter nearby."), false); return 0; }
        boolean ok = fighter.beginMeditation(900, true);
        player.displayClientMessage(Component.literal("[Living World] " + (ok ? "Meditation started for " : "Could not start meditation for ") + fighter.getFighterName()), false);
        return ok ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugFlightNearest(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) { player.displayClientMessage(Component.literal("[Living World] No fighter nearby."), false); return 0; }
        fighter.debugUnlockFlight();
        player.displayClientMessage(Component.literal("[Living World] " + fighter.getFighterName() + " has learned DMZ flight."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int debugFormNearest(ServerPlayer player, int level) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) { player.displayClientMessage(Component.literal("[Living World] No fighter nearby."), false); return 0; }
        boolean ok = fighter.debugSetRacialSkill(level);
        String learned = fighter.getRacialSkillLevel() > 0 ? "skill level " + fighter.getRacialSkillLevel() : "base form only";
        player.displayClientMessage(Component.literal("[Living World] " + (ok ? "Set " : "Could not set ") + fighter.getFighterName()
                + " racial progression: " + learned + "."), false);
        return ok ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugTransformNearest(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) { player.displayClientMessage(Component.literal("[Living World] No fighter nearby."), false); return 0; }
        boolean ok = fighter.debugTransformRacial();
        player.displayClientMessage(Component.literal("[Living World] " + (ok ? fighter.getFighterName() + " transformed into " + fighter.getRacialFormName()
                : "Could not transform " + fighter.getFighterName() + "; give them a racial skill level first.")), false);
        return ok ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugAccessory(ServerPlayer player, String type) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) {
            player.displayClientMessage(Component.literal("[Living World] No fighter nearby."), false);
            return 0;
        }
        String applied = FighterSpecialItemManager.forceAccessory(fighter, type);
        if (applied.isBlank()) {
            player.displayClientMessage(Component.literal("[Living World] Unknown accessory. Try red, blue, green, purple, turtle, workout, piccolo, or none."), false);
            return 0;
        }
        player.displayClientMessage(Component.literal("[Living World] " + fighter.getFighterName() + " accessory: " + applied + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int debugScientist(ServerPlayer player, boolean summon) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null || WorldMenaceManager.isHerobrine(fighter)) {
            player.displayClientMessage(Component.literal("[Living World] No eligible fighter nearby.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        FighterScientistManager.forceScientist(fighter);
        if (summon) {
            net.minecraft.world.entity.Mob minion = FighterScientistManager.forceSummon(fighter);
            if (minion == null) {
                player.displayClientMessage(Component.literal("[Living World] Scientist role applied, but Saibaman deployment failed at a specific stage: "
                        + FighterScientistManager.lastSpawnError(fighter)).withStyle(ChatFormatting.RED), false);
                return 0;
            }
            player.displayClientMessage(Component.literal("[Living World] " + fighter.getFighterName()
                    + " deployed a scaled " + minion.getName().getString() + ".").withStyle(ChatFormatting.AQUA), false);
        } else {
            player.displayClientMessage(Component.literal("[Living World] " + fighter.getFighterName()
                    + " is now a Scientist fighter with a research scouter.").withStyle(ChatFormatting.AQUA), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int debugScientistSpecimens(ServerPlayer player, int count) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null || WorldMenaceManager.isWorldMenace(fighter)) {
            player.displayClientMessage(Component.literal("[Living World] No eligible fighter nearby.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        int added = FighterScientistManager.debugAddSpecimens(fighter, count);
        player.displayClientMessage(Component.literal("[Living World] Added " + added + " viable specimen(s) to "
                + fighter.getFighterName() + " • " + FighterScientistManager.diagnosticStatus(fighter)).withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int debugScientistDiagnose(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) {
            player.displayClientMessage(Component.literal("[Living World] No fighter nearby.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        player.displayClientMessage(Component.literal("[Living World] " + fighter.getFighterName() + " • "
                + FighterScientistManager.diagnosticStatus(fighter)).withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int debugPeacekeepers(ServerPlayer player) {
        int count = PeacekeeperManager.debugSpawn(player);
        String names = player.serverLevel().getEntitiesOfClass(AmbientFighterEntity.class, player.getBoundingBox().inflate(120.0D),
                        f -> f.isAlive() && f.getStoryRole() == AmbientFighterEntity.STORY_PEACEKEEPER)
                .stream().sorted(java.util.Comparator.comparingDouble(player::distanceToSqr)).limit(Math.max(0, count))
                .map(AmbientFighterEntity::getFighterName).collect(java.util.stream.Collectors.joining(", "));
        player.displayClientMessage(Component.literal(count > 0
                ? "[Living World] Spawned " + count + " Earth Guardian Corps peacekeepers • NPCs: " + names + "."
                : "[Living World] Could not spawn peacekeepers here (Earth only, and no duplicate response nearby).")
                .withStyle(count > 0 ? ChatFormatting.AQUA : ChatFormatting.RED), false);
        return count > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugKaiokenNearest(ServerPlayer player, int level) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) { player.displayClientMessage(Component.literal("[Living World] No fighter nearby."), false); return 0; }
        int use = level >= 10 ? 10 : level >= 4 ? 4 : level >= 3 ? 3 : 2;
        boolean ok = fighter.startKaioken(use);
        player.displayClientMessage(Component.literal("[Living World] " + (ok ? "Kaioken x" + use + " started for " : "Could not start Kaioken for ") + fighter.getFighterName()), false);
        return ok ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugHornNearest(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) {
            player.displayClientMessage(Component.literal("[Living World] No fighter nearby for horn testing.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        boolean ok = FactionHornManager.debugBlow(fighter);
        player.displayClientMessage(Component.literal(ok
                ? "[Living World] Forced a real Goat Horn rally on " + fighter.getFighterName() + "."
                : "[Living World] Could not start the horn test.")
                .withStyle(ok ? ChatFormatting.GOLD : ChatFormatting.RED), false);
        return ok ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugMoodNearest(ServerPlayer player, String rawMood) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) {
            player.displayClientMessage(Component.literal("[Living World] No fighter nearby for mood testing.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        boolean ok = ReactiveWorldManager.debugSetMood(fighter, rawMood);
        player.displayClientMessage(Component.literal(ok
                ? "[Living World] " + fighter.getFighterName() + " mood -> " + ReactiveWorldManager.profileSummary(fighter) + "."
                : "[Living World] Unknown mood. Try upbeat, content, focused, wary, irritated, somber, or weary.")
                .withStyle(ok ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.RED), false);
        return ok ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugMoodCycle(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) {
            player.displayClientMessage(Component.literal("[Living World] No fighter nearby for the mood cycle.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        boolean ok = ReactiveWorldManager.startDebugMoodCycle(fighter);
        player.displayClientMessage(Component.literal(ok
                ? "[Living World] " + fighter.getFighterName() + " will cycle through all 7 moods in about 40 seconds."
                : "[Living World] Could not start the mood cycle on that fighter.")
                .withStyle(ok ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.RED), false);
        return ok ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugMoodAll(ServerPlayer player) {
        java.util.List<AmbientFighterEntity> fighters = new java.util.ArrayList<>(
                player.serverLevel().getEntitiesOfClass(AmbientFighterEntity.class,
                        player.getBoundingBox().inflate(48.0D), f -> f.isAlive() && !WorldMenaceManager.isHerobrine(f))
                        .stream().sorted(java.util.Comparator.comparingDouble(player::distanceToSqr)).limit(7).toList());
        int attempts = 0;
        while (fighters.size() < 7 && attempts++ < 14) {
            AmbientFighterEntity spawned = AmbientFighterSpawner.spawnNearPlayer(
                    player, FighterAlignment.NEUTRAL, FighterRank.TRAINED, true);
            if (spawned != null && !fighters.contains(spawned)) fighters.add(spawned);
        }
        ReactiveWorldManager.Mood[] moods = ReactiveWorldManager.Mood.values();
        int count = Math.min(moods.length, fighters.size());
        for (int i = 0; i < count; i++) ReactiveWorldManager.debugSetMood(fighters.get(i), moods[i].name());
        String names = fighters.stream().limit(count).map(AmbientFighterEntity::getFighterName).collect(java.util.stream.Collectors.joining(", "));
        player.displayClientMessage(Component.literal("[Living World] Mood showcase applied to " + count + "/7 fighters • NPCs: " + names + ". "
                + "No canned activity is forced now; watch their movement/social choices or use spectate.")
                .withStyle(count == 7 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.YELLOW), false);
        return count > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static AmbientFighterEntity nearestFighter(ServerPlayer player, double radius) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        return level.getEntitiesOfClass(AmbientFighterEntity.class, player.getBoundingBox().inflate(radius), f -> f.isAlive() && !com.dmzlwfusion.NpcFusionManager.isHiddenFusionPartner(f))
                .stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }

    private static int debugGoAlong(ServerPlayer player) {
        int result = FighterLifeJoinManager.forceDebug(player);
        if (result <= 0) player.displayClientMessage(Component.literal("[Living World] Could not start a Go Along test here.").withStyle(ChatFormatting.RED), false);
        return result > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugDance(ServerPlayer player, String variant) {
        int result = FighterAmbientActivityManager.forceDanceVariant(player, variant);
        String names = FighterAmbientActivityManager.debugSubjects(player);
        player.displayClientMessage(Component.literal(result > 0
                ? "[Living World] Forced dance variant: " + variant + " • NPC: " + names + "."
                : "[Living World] Could not start a dance test here.")
                .withStyle(result > 0 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.RED), false);
        return result > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugDanceAll(ServerPlayer player) {
        int result = FighterAmbientActivityManager.forceDanceShowcase(player);
        String names = FighterAmbientActivityManager.debugSubjects(player);
        player.displayClientMessage(Component.literal("[Living World] Started " + result + "/2 dance variants: groove and disco • NPCs: " + names + ".")
                .withStyle(result == 2 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.YELLOW), false);
        return result > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugIdle(ServerPlayer player, int variant) {
        int result = FighterAmbientActivityManager.forceIdleVariant(player, variant);
        String names = FighterAmbientActivityManager.debugSubjects(player);
        player.displayClientMessage(Component.literal(result > 0
                ? "[Living World] Forced idle animation: " + (variant == 0 ? "stretch" : "side") + " • NPC: " + names + "."
                : "[Living World] Could not start an idle-animation test here.")
                .withStyle(result > 0 ? ChatFormatting.AQUA : ChatFormatting.RED), false);
        return result > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugIdleAll(ServerPlayer player) {
        int result = FighterAmbientActivityManager.forceIdleShowcase(player);
        String names = FighterAmbientActivityManager.debugSubjects(player);
        player.displayClientMessage(Component.literal("[Living World] Started " + result + "/2 idle animations: stretch and side • NPCs: " + names + ".")
                .withStyle(result == 2 ? ChatFormatting.AQUA : ChatFormatting.YELLOW), false);
        return result > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugSpawnCompanion(ServerPlayer player) {
        int result = LivingBondManager.forceSpawnCompanion(player);
        String companionName = "";
        if (result > 0) {
            UUID companionId = LivingBondManager.companionId(player);
            AmbientFighterEntity companion = companionId == null ? null : player.serverLevel().getEntitiesOfClass(
                    AmbientFighterEntity.class, player.getBoundingBox().inflate(96.0D), f -> companionId.equals(f.getUUID())).stream().findFirst().orElse(null);
            if (companion != null) companionName = companion.getFighterName();
        }
        final String shownName = companionName.isBlank() ? "registered companion" : companionName;
        player.displayClientMessage(Component.literal(result > 0
                ? "[Living World] Spawned and registered persistent travelling companion • NPC: " + shownName + "."
                : "[Living World] Could not create a travelling companion here.")
                .withStyle(result > 0 ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return result > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugSpectateNearest(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) {
            player.displayClientMessage(Component.literal("[Living World] No living fighter nearby to spectate.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        return FighterDebugSpectateManager.start(player, fighter) > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugSit(ServerPlayer player, int variant) {
        int result = FighterAmbientActivityManager.forceSitVariant(player, variant);
        String label = variant == 1 ? "cross-legged sitting" : "relaxed sitting";
        String names = FighterAmbientActivityManager.debugSubjects(player);
        player.displayClientMessage(Component.literal(result > 0
                ? "[Living World] Forced " + label + " pose • NPC: " + names + "."
                : "[Living World] Could not start a sitting test on an eligible nearby fighter.")
                .withStyle(result > 0 ? ChatFormatting.AQUA : ChatFormatting.RED), false);
        return result > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugActivity(ServerPlayer player, String type) {
        String normalized = type == null ? "" : type.toLowerCase(java.util.Locale.ROOT);
        int result = switch (normalized) {
            case "social", "socializing", "hangout", "hangingout", "walktogether", "walk_together", "socialwalk",
                    "meeting", "meet", "meetup", "meetingup", "meeting_up"
                    -> FighterNpcSocialManager.forceNearest(player, normalized);
            default -> FighterAmbientActivityManager.forceNearest(player, type);
        };
        String fail = switch (normalized) {
            case "fishing" -> "[Living World] Could not start fishing here. The NPC needs reachable real water.";
            case "food", "hunting", "hunt", "foraging" -> "[Living World] Could not start food gathering here. The NPC needs daylight and reachable living prey.";
            case "flower" -> "[Living World] Could not start flower inspection here. The NPC needs a nearby small flower and safe standing spot.";
            case "tree", "apple" -> "[Living World] Could not start the tree activity here. The NPC needs a nearby tree and safe standing spot.";
            case "stargazing", "stargaze" -> "[Living World] Could not start stargazing here. The NPC needs a safe open-sky spot.";
            case "flight", "fly" -> "[Living World] Could not start leisure flight here.";
            case "social", "socializing" -> "[Living World] Could not start socializing. Two compatible idle NPCs need to be nearby.";
            case "hangout", "hangingout" -> "[Living World] Could not start a hangout. Two compatible idle NPCs need to be nearby.";
            case "walktogether", "walk_together", "socialwalk" -> "[Living World] Could not start a walk together. Two compatible idle NPCs need to be nearby.";
            case "meeting", "meet", "meetup", "meetingup", "meeting_up" -> "[Living World] Could not start a meeting. Two compatible loaded NPCs need to be available within the meeting search range.";
            default -> "[Living World] Could not start that activity on an eligible nearby fighter.";
        };
        String names = FighterAmbientActivityManager.debugSubjects(player);
        String shownActivity = switch (normalized) {
            case "social", "socializing" -> "Socializing";
            case "hangout", "hangingout" -> "Hanging out";
            case "walktogether", "walk_together", "socialwalk" -> "Walking with someone";
            case "meeting", "meet", "meetup", "meetingup", "meeting_up" -> "Meeting up with someone";
            default -> FighterAmbientActivityManager.debugActivityLabel(type);
        };
        player.displayClientMessage(Component.literal(result > 0
                ? "[Living World] Forced nearest NPC activity: " + shownActivity + " • NPC: " + names + "."
                : fail)
                .withStyle(result > 0 ? ChatFormatting.AQUA : ChatFormatting.RED), false);
        return result > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugActivitiesAll(ServerPlayer player) {
        int result = FighterAmbientActivityManager.forceShowcase(player);
        String names = FighterAmbientActivityManager.debugSubjects(player);
        player.displayClientMessage(Component.literal("[Living World] Started " + result + "/" + FighterAmbientActivityManager.Type.values().length + " ambient activity tests nearby • NPCs: " + names + ". "
                + "Fishing still needs real water and stargazing needs an open safe spot. Social/hangout/walk-together/meeting are tested separately through /lw fighter activity <type>.")
                .withStyle(result > 0 ? ChatFormatting.AQUA : ChatFormatting.RED), false);
        return result > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugPowerCompare(ServerPlayer player) {
        int result = FighterPowerCompareManager.forceDebug(player);
        String names = FighterPowerCompareManager.debugSubjects(player);
        player.displayClientMessage(Component.literal(result > 0
                ? "[Living World] Forced a friendly power comparison • NPCs: " + names + "."
                : "[Living World] Could not start a power comparison here.")
                .withStyle(result > 0 ? ChatFormatting.AQUA : ChatFormatting.RED), false);
        return result > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugGrowthStatus(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null || !(player.level() instanceof ServerLevel level)) {
            player.displayClientMessage(Component.literal("[Living World] No fighter nearby.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        // Prime the same local observation used by organic activity/growth so this diagnostic
        // describes what the nearest fighter would actually use right now.
        WorldPowerScaler.observeNearbyPlayerPressure(fighter, player);
        double sensedPlayerPower = Math.max(1.0D, PlayerWorldManager.playerBattlePower(player));
        double progressionPlayerPower = Math.max(1.0D, PlayerWorldManager.playerProgressionBattlePower(player));
        double npcPower = Math.max(1.0D, fighter.getPermanentBattlePower());
        double multiplier = WorldPowerScaler.earnedGrowthMultiplier(level, fighter);
        double ceiling = WorldPowerScaler.earnedProgressionCeiling(level, fighter.blockPosition(), fighter.getRank(), fighter);
        int pressure = WorldPowerScaler.trainingPressure(fighter);
        double ratio = npcPower / progressionPlayerPower;
        double deferred = FighterBattleGrowthManager.deferredBattlePower(fighter);
        double potential = FighterPotentialManager.potential(fighter);
        player.displayClientMessage(Component.literal(String.format(java.util.Locale.ROOT,
                "[Living World] %s • permanent BP %.0f • deferred BP settling %.1f • player progression BP %.0f • sensed BP %.0f • NPC/player %.2fx • ordinary growth %.2fx • potential %s %.2fx • training pressure %+d • earned ceiling %.0f",
                fighter.getFighterName(), npcPower, deferred, progressionPlayerPower, sensedPlayerPower, ratio, multiplier,
                FighterPotentialManager.label(potential), potential, pressure, ceiling))
                .withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }


    private static int debugMeleeHit(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) {
            player.displayClientMessage(Component.literal("[Living World] No fighter nearby.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        boolean bypassedPostSpar = fighter.isPostSparOpponent(player);
        int peaceTicks = fighter.getPostSparPeaceTicks();
        float before = player.getHealth();
        boolean accepted = fighter.debugForceMeleeHit(player);
        float immediate = Math.max(0.0F, before - player.getHealth());
        String peaceNote = bypassedPostSpar
                ? String.format(java.util.Locale.ROOT, " • debug-only bypassed %dt (%.1fs) of post-spar peace; protection restored afterward",
                peaceTicks, peaceTicks / 20.0D) : "";
        player.displayClientMessage(Component.literal(String.format(java.util.Locale.ROOT,
                "[LW Melee] forced one native direct melee hit from %s • accepted %s • immediate HP delta %.2f%s • use /lw fighter meleestatus for pipeline details",
                fighter.getFighterName(), accepted, immediate, peaceNote)).withStyle(ChatFormatting.AQUA), false);
        return accepted ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugMeleeStatus(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) {
            player.displayClientMessage(Component.literal("[Living World] No fighter nearby.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        player.displayClientMessage(Component.literal("[LW Melee] " + NpcPlayerDamageManager.debugStatus(player, fighter))
                .withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int debugDayPlan(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        if (fighter == null) {
            player.displayClientMessage(Component.literal("[Living World] No fighter nearby.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (player.level() instanceof ServerLevel) WorldPowerScaler.observeNearbyPlayerPressure(fighter, player);
        for (String line : FighterDailyRoutineManager.debugLines(fighter)) {
            player.displayClientMessage(Component.literal("[LW DayPlan] " + line).withStyle(ChatFormatting.AQUA), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int debugPowerSpike(ServerPlayer player, boolean npcSource, boolean transform) {
        int result = npcSource ? FighterPowerSpikeReactionManager.debugNpc(player, transform)
                : FighterPowerSpikeReactionManager.debugPlayer(player, transform);
        return result > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugAppearance(ServerPlayer player) {
        return FighterAppearanceEvolutionManager.debugNearest(player) > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugPractice(ServerPlayer player) {
        return FighterPracticeSparManager.debugStart(player) > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugReunion(ServerPlayer player) {
        AmbientFighterEntity fighter = nearestFighter(player, 48.0D);
        boolean ok = fighter != null && FighterMemoryManager.debugPrepareReunion(player, fighter);
        if (!ok) {
            fighter = AmbientFighterSpawner.spawnNearPlayer(player, FighterAlignment.GOOD, FighterRank.TRAINED, true);
            ok = fighter != null && FighterMemoryManager.debugPrepareReunion(player, fighter);
        }
        if (ok) ReactiveWorldManager.debugSetMood(fighter, "content");
        player.displayClientMessage(Component.literal(ok
                ? "[Living World] Reunion prepared with " + fighter.getFighterName() + ". Use Talk normally now."
                : "[Living World] Could not prepare a reunion test here.")
                .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return ok ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugFusionPartner(ServerPlayer player) {
        AmbientFighterEntity fighter = FighterFusionDebugPartnerManager.spawnPartner(player);
        player.displayClientMessage(Component.literal(fighter != null
                ? "[Living World] Spawned fusion test partner " + fighter.getFighterName() + " near your race/stat level. Open their normal NPC menu and choose Fusion."
                : "[Living World] Could not create a compatible fusion partner (check that your DMZ character/race is initialized).")
                .withStyle(fighter != null ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.RED), false);
        return fighter != null ? Command.SINGLE_SUCCESS : 0;
    }

    private static int debugAnimalMimic(ServerPlayer player) {
        int result = FighterAnimalMimicManager.debug(player);
        String name = FighterAnimalMimicManager.debugSubject(player);
        player.displayClientMessage(Component.literal(result > 0
                ? "[Living World] Animal cue played • NPC: " + name + "; watch/listen for the delayed imitation."
                : "[Living World] Could not stage an animal-mimic test here.")
                .withStyle(result > 0 ? ChatFormatting.AQUA : ChatFormatting.RED), false);
        return result > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int requestStatus(ServerPlayer player) {
        player.displayClientMessage(Component.literal("[Living World] " + FactionRequestManager.summary(player)).withStyle(ChatFormatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int bondStatus(ServerPlayer player) {
        player.displayClientMessage(Component.literal("[Living World] " + LivingBondManager.status(player)).withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int bondInviteMeditation(ServerPlayer player) {
        int result = LivingBondManager.forceMeditationInvite(player);
        player.displayClientMessage(Component.literal(result > 0
                ? "[Living World] Forced shared meditation started with a nearby fighter (a neutral test fighter is created if needed)."
                : "[Living World] Could not start forced shared meditation. Check that integrated Meditation is enabled.")
                .withStyle(result > 0 ? ChatFormatting.AQUA : ChatFormatting.RED), false);
        return result > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int bondInviteCompanion(ServerPlayer player) {
        int result = LivingBondManager.forceCompanionInvite(player);
        player.displayClientMessage(Component.literal(result > 0
                ? "[Living World] Debug travel invitation created. Sneak + empty-hand right-click the inviting fighter to accept."
                : "[Living World] Could not create a travel invitation; keep a fighter nearby and make sure you do not already have a companion.")
                .withStyle(result > 0 ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return result > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int bondClearCompanion(ServerPlayer player) {
        LivingBondManager.clearCompanion(player);
        player.displayClientMessage(Component.literal("[Living World] You are no longer travelling with that companion.").withStyle(ChatFormatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int spawn(ServerPlayer player, FighterAlignment alignment, FighterRank forcedRank) {
        FighterRank rank = forcedRank == null ? FighterRank.roll(player.getRandom()) : forcedRank;
        AmbientFighterEntity fighter = AmbientFighterSpawner.spawnNearPlayer(player, alignment, rank, true);
        if (fighter == null) {
            player.displayClientMessage(Component.literal("[Living World] Could not find safe ground nearby.")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        player.displayClientMessage(
                Component.literal("[Living World] Spawned ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(fighter.getFighterName()).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" — "))
                        .append(Component.literal(alignment.displayName()).withStyle(alignment.color()))
                        .append(Component.literal(" / " + fighter.getRace().displayName()
                                + (fighter.getRace().gendered() ? " / " + fighter.genderLabel() : "")
                                + " / " + rank.displayName()
                                + " / " + fighter.getArchetype().displayName() + " / " + fighter.getPersonality().displayName()
                                + " / PL " + fighter.getBattlePower()
                                + " / Height " + Math.round(fighter.getDisplayScale() * 100.0F) + "%").withStyle(ChatFormatting.GRAY)),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int spawnCustom(ServerPlayer player, FighterRace forcedRace, FighterArchetype forcedStyle) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        BlockPos pos = AmbientFighterSpawner.findSafeGroundAround(level, player.blockPosition(), player.getRandom(), 7, 13, 18);
        if (pos == null) return 0;
        FighterRace race = forcedRace == null ? AmbientFighterSpawner.rollRaceForLevel(level, player.getRandom()) : forcedRace;
        FighterArchetype style = forcedStyle == null ? FighterArchetype.roll(player.getRandom(), FighterRank.TRAINED) : forcedStyle;
        AmbientFighterEntity fighter = AmbientFighterSpawner.spawnAt(level, pos, FighterAlignment.NEUTRAL,
                FighterRank.TRAINED, FighterPersonality.CALM, race, style, player.getRandom());
        if (fighter == null) return 0;
        player.displayClientMessage(Component.literal("[Living World] Spawned test fighter: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(fighter.getFighterName()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" • " + race.displayName()
                        + (race.gendered() ? " / " + fighter.genderLabel() : "")
                        + " • " + style.displayName()
                        + " • PL " + fighter.getBattlePower()
                        + " • Height " + Math.round(fighter.getDisplayScale() * 100.0F) + "%").withStyle(ChatFormatting.GRAY)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int memoryStatus(ServerPlayer player) {
        player.displayClientMessage(Component.literal("[Living World] " + FighterMemoryManager.status(player))
                .withStyle(ChatFormatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int memoryMark(ServerPlayer player) {
        List<AmbientFighterEntity> nearby = player.level().getEntitiesOfClass(
                AmbientFighterEntity.class, player.getBoundingBox().inflate(24.0D), f -> f.isAlive() && !com.dmzlwfusion.NpcFusionManager.isHiddenFusionPartner(f));
        AmbientFighterEntity fighter = nearby.stream()
                .min(java.util.Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
        if (fighter == null || !FighterMemoryManager.rememberForDebug(player, fighter)) {
            player.displayClientMessage(Component.literal("[Living World] No roaming fighter close enough to remember.")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        player.displayClientMessage(Component.literal("[Living World] Remembering " + fighter.getFighterName() + " for recurrence testing.")
                .withStyle(ChatFormatting.GOLD), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int memorySummon(ServerPlayer player) {
        if (!FighterMemoryManager.summonKnown(player)) {
            player.displayClientMessage(Component.literal("[Living World] No eligible remembered fighter could be spawned.")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int memoryClear(ServerPlayer player) {
        FighterMemoryManager.clear(player);
        player.displayClientMessage(Component.literal("[Living World] Cleared remembered fighter records.")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int factionList(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        FactionWorldData data = FactionWorldData.get(level);
        data.tickOrganizations(level);
        long now = level.getServer().overworld().getGameTime();
        List<WorldFaction> all = data.factions();
        long active = all.stream().filter(f -> !data.isExtinct(f)).count();
        long extinct = all.size() - active;
        long earth = all.stream().filter(f -> f.realm() == FactionRealm.EARTH && !data.isExtinct(f)).count();
        long namek = all.stream().filter(f -> f.realm() == FactionRealm.NAMEK && !data.isExtinct(f)).count();
        List<String> lines = new java.util.ArrayList<>();
        lines.add(". These totals describe the wider faction; you will meet only the people who are currently nearby.");
        for (FactionRealm realm : new FactionRealm[]{FactionRealm.EARTH, FactionRealm.NAMEK}) {
            lines.add("## " + realm.displayName());
            for (WorldFaction faction : all) {
                if (faction.realm() != realm || data.isExtinct(faction)) continue;
                int rep = FactionManager.getReputation(player, faction);
                String state = data.publicState(faction, now);
                boolean antagonist = AntagonistWorldData.get(level).isAntagonistFaction(faction.id());
                String prefix = antagonist || state.equals("AT WAR") ? "!! " : state.equals("ASCENDANT") || state.equals("RISING") ? "+ " : "* ";
                lines.add(prefix + "#" + faction.slot() + "  " + faction.name() + "  —  " + faction.structure().displayName()
                        + (antagonist ? "  •  ANTAGONIST" : "")
                        + "  •  Pop " + data.population(faction) + "  •  " + state
                        + "  •  Rep " + rep + " " + FactionManager.reputationLabel(rep));
            }
        }
        if (extinct > 0) lines.add(". " + extinct + " extinct organization" + (extinct == 1 ? " remains" : "s remain") + " in world history.");
        LWNetwork.sendFactionDossier(player, new FactionDossierPacket("factions", 0, "Living World — Factions",
                active + " active organizations • Earth " + earth + " • Namek " + namek, lines));
        return Command.SINGLE_SUCCESS;
    }

    private static int factionStatus(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        FactionWorldData data = FactionWorldData.get(level);
        data.tickOrganizations(level);
        List<WorldFaction> factions = data.factions();
        long earth = factions.stream().filter(f -> f.realm() == FactionRealm.EARTH).count();
        long namek = factions.size() - earth;
        long active = factions.stream().filter(f -> !data.isExtinct(f)).count();
        long extinct = factions.size() - active;
        long wanted = WantedWorldData.get(level).profiles().stream().filter(p -> !p.eliminated).count();
        List<AmbientFighterEntity> loaded = level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(512.0D), AmbientFighterEntity::isFactionMember);
        long leaders = loaded.stream().filter(AmbientFighterEntity::isFactionLeader).count();
        long regional = loaded.stream().filter(AmbientFighterEntity::isRegionalPresence).count();
        player.displayClientMessage(Component.literal("[Living World] Realm " + LivingWorldDimensions.realm(level).displayName()
                + " • organizations " + active + " active / " + extinct + " extinct (Earth records " + earth + "/Namek records " + namek + ")"
                + " • wanted " + wanted + " • affiliated within 512b " + loaded.size() + " • regional residents " + regional
                + " • leaders " + leaders + " • independents enabled").withStyle(ChatFormatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int factionInspect(ServerPlayer player, int slot) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction faction = FactionManager.bySlot(level, slot);
        if (faction == null) return missingFaction(player, slot);
        FactionWorldData data = FactionWorldData.get(level);
        data.tickOrganizations(level);
        long now = level.getServer().overworld().getGameTime();
        int rep = FactionManager.getReputation(player, faction);
        List<String> lines = new java.util.ArrayList<>();
        String state = data.publicState(faction, now);
        // Hidden presentation metadata: the dossier renders the same center-zero relationship bar
        // used by individual fighter profiles, while the existing standing text stays accessible.
        lines.add("@factionbond|" + rep + "|" + FactionManager.reputationLabel(rep));
        lines.add((state.equals("AT WAR") ? "!! " : "~ ") + "STATE  " + state);
        AntagonistWorldData antagonistData = AntagonistWorldData.get(level);
        if (antagonistData.isAntagonistFaction(faction.id())) {
            lines.add("!! ANTAGONIST ORGANIZATION • " + antagonistData.reason(faction.id()));
        }
        if (data.earthGuardians() != null && data.earthGuardians().id().equals(faction.id()))
            lines.add("+ Public peacekeepers and crisis responders.");
        if (data.blackSun() != null && data.blackSun().id().equals(faction.id()))
            lines.add("!! Hostile underworld organization.");
        lines.add("* " + faction.realm().displayName() + " • " + faction.structure().displayName() + " • "
                + faction.ethos().displayName() + " • " + faction.alignment().displayName());
        if (FighterArsenalManager.isSwordFaction(faction)) lines.add("+ Weapon tradition: swordsmen • combat members carry DMZ swords.");
        lines.add("* Your standing: " + rep + " — " + FactionManager.reputationLabel(rep));
        lines.add("## Society");
        lines.add("* Population: " + data.population(faction) + "  (fighters " + data.fighterPopulation(faction)
                + ", civilians " + data.civilianPopulation(faction) + ", youth " + data.youthPopulation(faction) + ")");
        lines.add("* Supplies: " + data.supplies(faction) + "/120 — " + data.supplyLabel(faction));
        lines.add("## Leadership");
        String leaderState;
        if (data.isLeaderKilled(faction)) {
            long remain = Math.max(0L, data.successionAt(faction) - now);
            leaderState = "FALLEN • succession in " + String.format(java.util.Locale.ROOT, "%.1f", remain / 24000.0D) + " days";
        } else leaderState = data.isLeaderSpawned(faction) ? "active in world" : "not currently nearby";
        lines.add("* " + faction.roleTitle(FactionRole.LEADER) + " " + data.currentLeaderName(faction) + " — " + leaderState);
        lines.add(". Rank ladder: " + faction.roleTitle(FactionRole.RECRUIT) + " → " + faction.roleTitle(FactionRole.MEMBER)
                + " → " + faction.roleTitle(FactionRole.ENFORCER) + " → " + faction.roleTitle(FactionRole.LIEUTENANT)
                + " → " + faction.roleTitle(FactionRole.LEADER));
        if (antagonistData.isAntagonistFaction(faction.id())) {
            lines.add("## Antagonist core");
            java.util.List<AntagonistWorldData.CoreMember> core = antagonistData.coreMembers(faction.id());
            if (core.isEmpty()) lines.add(". No recurring core members have emerged yet.");
            else for (AntagonistWorldData.CoreMember member : core) {
                lines.add((member.fallen() ? "x " : "!! ") + member.name() + (member.fallen() ? " — fallen" : " — recurring core"));
            }
        }
        lines.add("## Territory & activity");
        lines.add("* Natural rally territory: X " + faction.roamX() + " Z " + faction.roamZ() + " • radius ~" + faction.roamRadius());
        List<WorldFaction> wars = data.warEnemies(faction, now);
        if (!wars.isEmpty()) lines.add("!! WAR: " + wars.stream().map(WorldFaction::name).collect(java.util.stream.Collectors.joining(", ")));
        List<PrisonerWorldData.Prisoner> missing = PrisonerWorldData.get(level).active().stream().filter(p -> p.victimFactionId.equals(faction.id())).toList();
        List<PrisonerWorldData.Prisoner> held = PrisonerWorldData.get(level).active().stream().filter(p -> p.captorFactionId.equals(faction.id())).toList();
        if (!missing.isEmpty() || !held.isEmpty()) {
            lines.add("## Captivity");
            for (PrisonerWorldData.Prisoner prisoner : missing) lines.add("!! Missing: " + prisoner.name + " — held by "
                    + java.util.Optional.ofNullable(data.byId(prisoner.captorFactionId)).map(WorldFaction::name).orElse("an enemy faction"));
            if (!held.isEmpty()) lines.add("* Prisoners currently held: " + held.size());
        }
        lines.add("## Notable relations");
        int shown = 0;
        for (WorldFaction other : data.activeFactions()) {
            if (other.id().equals(faction.id()) || other.realm() != faction.realm()) continue;
            var relation = FactionManager.relation(level.getServer().overworld(), faction, other);
            if (relation == com.dmzlivingworld.world.FactionRelation.NEUTRAL) continue;
            lines.add((relation.hostile() ? "!! " : relation.allied() ? "+ " : "* ") + other.name() + " — " + relation.displayName());
            if (++shown >= 6) break;
        }
        if (shown == 0) lines.add(". No notable current relationships.");
        lines.add("## Recent history");
        List<String> history = data.history(faction);
        if (history.isEmpty()) lines.add(". No major recorded events yet.");
        else for (int i = Math.max(0, history.size() - 7); i < history.size(); i++) lines.add("* " + history.get(i));

        LWNetwork.sendFactionDossier(player, new FactionDossierPacket("faction", slot, "#" + slot + "  " + faction.name(),
                faction.realm().displayName() + " • " + faction.structure().displayName() + " • " + state, lines));
        return Command.SINGLE_SUCCESS;
    }

    private static int factionLocate(ServerPlayer player, int requestedSlot) {
        if (!(player.level() instanceof ServerLevel current)) return 0;
        WorldFaction faction;
        if (requestedSlot > 0) faction = FactionManager.bySlot(current, requestedSlot);
        else faction = FactionManager.factionsForRealm(current).stream()
                .min(java.util.Comparator.comparingDouble(f -> {
                    double dx = f.roamX() - player.getX(); double dz = f.roamZ() - player.getZ(); return dx * dx + dz * dz;
                })).orElse(null);
        if (faction == null) return missingFaction(player, requestedSlot);
        ServerLevel targetLevel = LivingWorldDimensions.levelFor(player.getServer(), faction.realm());
        if (targetLevel == null) {
            player.displayClientMessage(Component.literal("[Living World] " + faction.realm().displayName() + " is not currently available.")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }

        double radius = Math.max(900.0D, faction.roamRadius() + 320.0D);
        AABB search = new AABB(faction.roamX() - radius, targetLevel.getMinBuildHeight(), faction.roamZ() - radius,
                faction.roamX() + radius, targetLevel.getMaxBuildHeight(), faction.roamZ() + radius);
        List<AmbientFighterEntity> loaded = targetLevel.getEntitiesOfClass(AmbientFighterEntity.class, search,
                f -> f.isAlive() && f.isFactionMember() && faction.id().equals(f.getFactionId()));
        BlockPos target;
        String what;
        if (!loaded.isEmpty()) {
            AmbientFighterEntity best = loaded.stream().sorted(java.util.Comparator
                    .comparing((AmbientFighterEntity f) -> !f.isFactionLeader())
                    .thenComparing(f -> !f.isRegionalPresence())
                    .thenComparingDouble(f -> {
                        double dx = f.getX() - faction.roamX(); double dz = f.getZ() - faction.roamZ(); return dx * dx + dz * dz;
                    })).findFirst().orElse(loaded.get(0));
            target = best.blockPosition();
            what = best.isFactionLeader() ? "live " + faction.roleTitle(FactionRole.LEADER)
                    : best.isRegionalPresence() ? "live regional group" : "live patrol";
        } else {
            FactionWorldData data = FactionWorldData.get(targetLevel);
            BlockPos presencePos = data.presenceLastPos(faction);
            BlockPos leaderPos = !data.isLeaderKilled(faction) ? data.leaderLastPos(faction) : null;
            if (presencePos != null) {
                target = presencePos;
                what = "resident cell's last known position";
            } else if (leaderPos != null) {
                target = leaderPos;
                what = "leader's last known position";
            } else {
                target = new BlockPos(faction.roamX(), targetLevel.getSeaLevel(), faction.roamZ());
                what = "dormant regional presence";
            }
        }

        String location;
        if (LivingWorldDimensions.realm(current) == faction.realm()) {
            int distance = (int)Math.round(Math.hypot(target.getX() - player.getX(), target.getZ() - player.getZ()));
            String dir = FactionManager.direction(player.getX(), player.getZ(), target.getX(), target.getZ());
            location = dir + " • " + distance + " blocks";
        } else {
            location = "offworld — use /lw faction tp " + faction.slot();
        }
        player.displayClientMessage(Component.literal("[Living World] " + faction.name() + " • " + faction.realm().displayName()
                + " • " + what + " • " + location + " • X " + target.getX() + " Z " + target.getZ())
                .withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int factionTeleport(ServerPlayer player, int slot) {
        if (!(player.level() instanceof ServerLevel current)) return 0;
        WorldFaction faction = FactionManager.bySlot(current, slot);
        if (faction == null) return missingFaction(player, slot);
        ServerLevel targetLevel = LivingWorldDimensions.levelFor(player.getServer(), faction.realm());
        if (targetLevel == null) {
            player.displayClientMessage(Component.literal("[Living World] " + faction.realm().displayName() + " is unavailable.")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        FactionWorldData data = FactionWorldData.get(targetLevel);
        BlockPos knownPresence = data.presenceLastPos(faction);
        int tx = knownPresence != null ? knownPresence.getX() : faction.roamX();
        int tz = knownPresence != null ? knownPresence.getZ() : faction.roamZ();
        if (!teleportToPoint(player, targetLevel, tx, tz)) return 0;
        boolean manifested = FactionEncounterManager.ensureRegionalPresence(player, faction, true);
        player.displayClientMessage(Component.literal("[Living World] Teleported to " + faction.name() + " territory on "
                + faction.realm().displayName() + (manifested ? " • a nearby group has arrived" : " • members are already nearby"))
                .withStyle(ChatFormatting.GOLD), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int factionPresence(ServerPlayer player, int slot) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction faction = FactionManager.bySlot(level, slot);
        if (faction == null) return missingFaction(player, slot);
        if (faction.realm() != LivingWorldDimensions.realm(level)) {
            player.displayClientMessage(Component.literal("[Living World] " + faction.name() + " lives on "
                    + faction.realm().displayName() + ". Use /lw faction tp " + slot + ".")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        boolean spawned = FactionEncounterManager.ensureRegionalPresence(player, faction, true);
        player.displayClientMessage(Component.literal("[Living World] " + faction.name()
                + (spawned ? " regional presence spawned." : " already has a loaded presence nearby."))
                .withStyle(ChatFormatting.GOLD), false);
        return Command.SINGLE_SUCCESS;
    }

    private static boolean teleportToPoint(ServerPlayer player, ServerLevel targetLevel, int x, int z) {
        // Load only the immediate debug destination neighborhood; normal faction systems
        // never force distant chunks just because a region exists in SavedData.
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            targetLevel.getChunkAt(new BlockPos(x + dx * 16, targetLevel.getSeaLevel(), z + dz * 16));
        }
        int y = targetLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos rough = new BlockPos(x, y, z);
        BlockPos safe = AmbientFighterSpawner.isUsableGround(targetLevel, rough) ? rough
                : AmbientFighterSpawner.findSafeGroundAround(targetLevel, rough, player.getRandom(), 0, 72, 96);
        if (safe == null) {
            player.displayClientMessage(Component.literal("[Living World] Could not resolve safe ground at that destination.")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        player.teleportTo(targetLevel, safe.getX() + 0.5D, safe.getY() + 0.15D, safe.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        return true;
    }

    private static int worldTeleport(ServerPlayer player, FactionRealm realm) {
        ServerLevel target = LivingWorldDimensions.levelFor(player.getServer(), realm);
        if (target == null) {
            player.displayClientMessage(Component.literal("[Living World] " + realm.displayName() + " is unavailable.")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        int x = realm == FactionRealm.EARTH ? target.getSharedSpawnPos().getX() : 0;
        int z = realm == FactionRealm.EARTH ? target.getSharedSpawnPos().getZ() : 0;
        if (!teleportToPoint(player, target, x, z)) return 0;
        player.displayClientMessage(Component.literal("[Living World] Teleported to " + realm.displayName() + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int factionPatrol(ServerPlayer player, int requestedSlot) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction faction = requestedSlot > 0 ? FactionManager.bySlot(level, requestedSlot) : FactionManager.pickFactionFor(player);
        if (faction == null) return missingFaction(player, requestedSlot);
        if (faction.realm() != LivingWorldDimensions.realm(level)) {
            player.displayClientMessage(Component.literal("[Living World] " + faction.name() + " is on "
                    + faction.realm().displayName() + ". Use /lw faction tp " + faction.slot() + ".")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        int count = FactionEncounterManager.spawnPatrol(player, faction, 4, true);
        if (count <= 0) {
            player.displayClientMessage(Component.literal("[Living World] " + faction.name()
                    + " already has an active scene nearby/recently. No duplicate patrol created.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        player.displayClientMessage(Component.literal("[Living World] Spawned " + faction.name() + " patrol (" + count + ").")
                .withStyle(ChatFormatting.GOLD), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int factionClash(ServerPlayer player, int firstSlot, int secondSlot) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction first = FactionManager.bySlot(level, firstSlot);
        WorldFaction second = FactionManager.bySlot(level, secondSlot);
        if (first == null || second == null || first.id().equals(second.id())) {
            player.displayClientMessage(Component.literal("[Living World] Choose two different valid faction slots.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (first.realm() != second.realm() || first.realm() != LivingWorldDimensions.realm(level)) {
            player.displayClientMessage(Component.literal("[Living World] Both factions must inhabit your current realm for a clash test.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        int count = FactionEncounterManager.spawnFactionClash(player, first, second, true);
        if (count <= 0) {
            player.displayClientMessage(Component.literal("[Living World] One of these factions is already busy, or this clash is already active. No duplicate scene created.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        player.displayClientMessage(Component.literal("[Living World] " + first.name() + " vs " + second.name()
                + " • " + FactionManager.relation(level, first, second).displayName())
                .withStyle(ChatFormatting.GOLD), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int factionWar(ServerPlayer player, int firstSlot, int secondSlot, boolean peace) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction first = FactionManager.bySlot(level, firstSlot), second = FactionManager.bySlot(level, secondSlot);
        if (first == null || second == null || first.id().equals(second.id()) || first.realm() != second.realm()) {
            player.displayClientMessage(Component.literal("[Living World] Choose two different factions on the same world.")
                    .withStyle(ChatFormatting.RED), false); return 0;
        }
        FactionWorldData data = FactionWorldData.get(level);
        long now = level.getServer().overworld().getGameTime();
        if (peace) {
            data.endWar(first, second, now, "A ceasefire");
            player.displayClientMessage(Component.literal("[Living World] Ceasefire: " + first.name() + " / " + second.name())
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            data.startWar(first, second, now, 5, "Tensions erupted");
            player.displayClientMessage(Component.literal("[Living World] WAR: " + first.name() + " vs " + second.name()
                    + " • natural skirmishes can now occur.").withStyle(ChatFormatting.DARK_RED), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int factionLeader(ServerPlayer player, int slot) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction faction = FactionManager.bySlot(level, slot);
        if (faction == null) return missingFaction(player, slot);
        if (faction.realm() != LivingWorldDimensions.realm(level)) {
            player.displayClientMessage(Component.literal("[Living World] " + faction.name() + " is on "
                    + faction.realm().displayName() + ". Use /lw faction tp " + slot + ".")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        FactionWorldData data = FactionWorldData.get(level);
        data.tickOrganizations(level);
        if (data.isLeaderKilled(faction)) {
            long remaining = Math.max(0L, data.successionAt(faction) - level.getServer().overworld().getGameTime());
            player.displayClientMessage(Component.literal("[Living World] " + data.currentLeaderName(faction)
                    + " is fallen. Succession expected in "
                    + String.format(java.util.Locale.ROOT, "%.1f", remaining / 24000.0D) + " Minecraft days.")
                    .withStyle(ChatFormatting.DARK_RED), false);
            return 0;
        }
        if (data.isLeaderSpawned(faction)) {
            BlockPos known = data.leaderLastPos(faction);
            if (known != null && teleportToPoint(player, level, known.getX(), known.getZ())) {
                player.displayClientMessage(Component.literal("[Living World] Leader already exists; teleported to "
                        + data.currentLeaderName(faction) + "'s last known position instead of duplicating them.")
                        .withStyle(ChatFormatting.YELLOW), false);
            } else {
                player.displayClientMessage(Component.literal("[Living World] Leader already exists; no duplicate created.")
                        .withStyle(ChatFormatting.YELLOW), false);
            }
            return Command.SINGLE_SUCCESS;
        }
        int count = FactionEncounterManager.spawnLeaderEntourage(player, faction, true);
        if (count <= 0) {
            player.displayClientMessage(Component.literal("[Living World] " + faction.name()
                    + " is currently occupied by another activity; no duplicate leadership scene created.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        player.displayClientMessage(Component.literal("[Living World] " + data.currentLeaderName(faction) + " arrived with " + faction.name() + ".")
                .withStyle(ChatFormatting.GOLD), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int factionHistory(ServerPlayer player, int slot) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction faction = FactionManager.bySlot(level, slot);
        if (faction == null) return missingFaction(player, slot);
        List<String> history = FactionWorldData.get(level).history(faction);
        List<String> lines = new java.util.ArrayList<>();
        lines.add(". Only consequential events are recorded here; everyday life is not listed individually.");
        lines.add("## Major events");
        if (history.isEmpty()) lines.add(". No major recorded events yet.");
        else for (String line : history) lines.add("* " + line);
        LWNetwork.sendFactionDossier(player, new FactionDossierPacket("faction", slot, faction.name() + " — History",
                faction.realm().displayName() + " • " + faction.structure().displayName(), lines));
        return Command.SINGLE_SUCCESS;
    }

    private static int factionForage(ServerPlayer player, int slot) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction faction = FactionManager.bySlot(level, slot);
        if (faction == null) return missingFaction(player, slot);
        if (faction.realm() != LivingWorldDimensions.realm(level)) {
            player.displayClientMessage(Component.literal("[Living World] Use /lw faction tp " + slot + " first.").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        int count = FactionEncounterManager.spawnForagingParty(player, faction, true);
        if (count <= 0) {
            player.displayClientMessage(Component.literal("[Living World] " + faction.name()
                    + " already has an active scene; no duplicate foraging party created.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        player.displayClientMessage(Component.literal("[Living World] Spawned " + faction.name() + " foraging party (" + count + ").")
                .withStyle(ChatFormatting.GOLD), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int factionDefect(ServerPlayer player, int fromSlot, int toSlot) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction from = FactionManager.bySlot(level, fromSlot), to = FactionManager.bySlot(level, toSlot);
        if (from == null || to == null || from.id().equals(to.id()) || from.realm() != to.realm()
                || from.realm() != LivingWorldDimensions.realm(level)) {
            player.displayClientMessage(Component.literal("[Living World] Choose two different factions on your current world.")
                    .withStyle(ChatFormatting.RED), false); return 0;
        }
        AmbientFighterEntity member = level.getEntitiesOfClass(AmbientFighterEntity.class, player.getBoundingBox().inflate(64.0D),
                f -> f.isAlive() && f.isFactionMember() && from.id().equals(f.getFactionId()) && !f.isFactionLeader())
                .stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (member == null) {
            player.displayClientMessage(Component.literal("[Living World] No non-leader member of " + from.name() + " is loaded nearby.")
                    .withStyle(ChatFormatting.RED), false); return 0;
        }
        FactionWorldData data = FactionWorldData.get(level);
        data.transferPopulation(from, to, member.isNonCombatant(), level.getServer().overworld().getGameTime(), member.getFighterName());
        FactionRole role = member.isNonCombatant() ? FactionRole.RECRUIT
                : member.getFactionRole() == FactionRole.LIEUTENANT ? FactionRole.ENFORCER : member.getFactionRole();
        member.assignFaction(to, role, null, false, member.isRegionalPresence());
        member.speak("I answer to " + to.name() + " now.", 70);
        player.displayClientMessage(Component.literal("[Living World] Forced defection: " + member.getFighterName()
                + " • " + from.name() + " → " + to.name()).withStyle(ChatFormatting.GOLD), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int wantedList(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WantedWorldData data = WantedWorldData.get(level); data.tick(level);
        List<String> lines = new java.util.ArrayList<>();
        java.util.List<FactionDossierPacket.Portrait> portraits = new java.util.ArrayList<>();
        if (WantedManager.isPlayerWanted(player)) {
            lines.add("!! YOU ARE WANTED • " + "★".repeat(WantedManager.playerWantedSeverity(player)) + " • "
                    + WantedManager.playerWantedCrime(player) + " • guard-aligned factions may pursue you");
        } else {
            int unlawful = WantedManager.playerUnlawfulKills(player);
            if (unlawful > 0) lines.add(". Recent unlawful kills: " + unlawful);
        }
        for (FactionRealm realm : new FactionRealm[]{FactionRealm.EARTH, FactionRealm.NAMEK}) {
            lines.add("## " + realm.displayName());
            boolean any = false;
            for (WantedWorldData.WantedProfile p : data.profiles()) {
                if (p.realm != realm) continue;
                any = true;
                java.util.UUID cardId = java.util.UUID.nameUUIDFromBytes(("dmzlivingworld:wanted:" + p.id)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String status = p.eliminated ? "ELIMINATED" : p.spawned ? "last known in world" : "whereabouts unknown";
                lines.add("@person:" + cardId + "|" + (p.eliminated ? ". " : "!! ") + "#" + p.slot + "  " + p.name
                        + "  •  " + "★".repeat(Math.max(1, p.severity)) + "  •  " + p.crime + "  •  " + status);
                if (p.profile != null && !p.profile.isEmpty())
                    portraits.add(new FactionDossierPacket.Portrait(cardId, p.profile));
            }
            if (!any) lines.add(". No known wanted fighters here.");
        }
        LWNetwork.sendFactionDossier(player, new FactionDossierPacket("wanted", 0, "Living World — Wanted",
                "Known wanted fighters", lines, "", portraits));
        return Command.SINGLE_SUCCESS;
    }

    private static int wantedPlayerStatus(ServerPlayer player) {
        int pressure = WantedManager.playerUnlawfulKills(player);
        int severity = WantedManager.playerWantedSeverity(player);
        String crime = WantedManager.playerWantedCrime(player);
        player.displayClientMessage(Component.literal("[Living World] Player wanted debug • pressure " + pressure
                + " • severity " + severity + (crime.isBlank() ? "" : " • " + crime))
                .withStyle(severity > 0 ? ChatFormatting.RED : ChatFormatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int wantedPlayerAdd(ServerPlayer player, int pressure) {
        WantedManager.debugAddPlayerWantedPressure(player, pressure);
        return wantedPlayerStatus(player);
    }

    private static int wantedPlayerClear(ServerPlayer player) {
        WantedManager.clearPlayerWanted(player);
        player.displayClientMessage(Component.literal("[Living World] Player wanted record cleared for testing.")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int wantedLocate(ServerPlayer player, int slot, boolean teleport) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WantedWorldData data = WantedWorldData.get(level);
        WantedWorldData.WantedProfile p = data.bySlot(slot);
        if (p == null) { player.displayClientMessage(Component.literal("[Living World] No wanted record #" + slot + ".").withStyle(ChatFormatting.RED), false); return 0; }
        if (p.eliminated) { player.displayClientMessage(Component.literal("[Living World] " + p.name + " is already eliminated.").withStyle(ChatFormatting.DARK_GRAY), false); return Command.SINGLE_SUCCESS; }
        ServerLevel target = LivingWorldDimensions.levelFor(player.getServer(), p.realm);
        if (target == null) return 0;
        int x = p.spawned && p.lastX != 0 ? p.lastX : p.anchorX;
        int z = p.spawned && p.lastZ != 0 ? p.lastZ : p.anchorZ;
        if (teleport) {
            if (!teleportToPoint(player, target, x, z)) return 0;
            if (!p.spawned) WantedManager.spawn(player, p, true);
        }
        String direction = player.level() == target ? FactionManager.direction(player.getX(), player.getZ(), x, z) : "offworld";
        player.displayClientMessage(Component.literal("[Living World] WANTED #" + slot + " " + p.name + " • " + p.realm.displayName()
                + " • " + p.crime + " • " + direction + " • X " + x + " Z " + z).withStyle(ChatFormatting.RED), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int wantedSpawn(ServerPlayer player, int slot) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WantedWorldData.WantedProfile p = WantedWorldData.get(level).bySlot(slot);
        if (p == null || p.eliminated) return 0;
        if (p.realm != LivingWorldDimensions.realm(level)) {
            player.displayClientMessage(Component.literal("[Living World] " + p.name + " is on " + p.realm.displayName()
                    + ". Use /lw wanted tp " + slot + ".").withStyle(ChatFormatting.YELLOW), false); return 0;
        }
        if (p.spawned) {
            player.displayClientMessage(Component.literal("[Living World] " + p.name + " already exists. Use /lw wanted tp " + slot + ".")
                    .withStyle(ChatFormatting.YELLOW), false); return Command.SINGLE_SUCCESS;
        }
        AmbientFighterEntity rogue = WantedManager.spawn(player, p, true);
        return rogue == null ? 0 : Command.SINGLE_SUCCESS;
    }

    private static int wantedTrack(ServerPlayer player, int slot) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WantedWorldData.WantedProfile profile = WantedWorldData.get(level).bySlot(slot);
        if (profile == null || profile.eliminated) {
            player.displayClientMessage(Component.literal("[Living World] That wanted record is unavailable or already eliminated.")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        WantedManager.track(player, profile);
        player.displayClientMessage(Component.literal("[Living World] Tracking WANTED #" + slot + " " + profile.name
                + ". The action bar gives a rough trail; /lw wanted locate " + slot + " shows the last known coordinates.")
                .withStyle(ChatFormatting.RED), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int wantedUntrack(ServerPlayer player) {
        WantedManager.clearTrack(player);
        player.displayClientMessage(Component.literal("[Living World] Wanted tracking cleared.").withStyle(ChatFormatting.DARK_GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int factionSimDay(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        FactionWorldData.get(level).debugSimulateDay(level);
        player.displayClientMessage(Component.literal("[Living World] Simulated one organization day. Check /lw faction history <slot>.")
                .withStyle(ChatFormatting.GOLD), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int factionCollapse(ServerPlayer player, int slot) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        FactionWorldData data = FactionWorldData.get(level); WorldFaction faction = data.bySlot(slot);
        if (faction == null) return missingFaction(player, slot);
        data.debugCollapse(faction, level.getServer().overworld().getGameTime());
        if (FactionWorldData.isPermanentFaction(faction)) {
            player.displayClientMessage(Component.literal("[Living World] " + faction.name()
                    + " is a permanent institution and rebuilt instead of going extinct.")
                    .withStyle(ChatFormatting.GOLD), false);
        } else {
            player.displayClientMessage(Component.literal("[Living World] Forced extinction of " + faction.name() + ".")
                    .withStyle(ChatFormatting.DARK_RED), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int factionNewCult(ServerPlayer player, FactionRealm realm) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction faction = FactionWorldData.get(level).debugFoundFaction(level, realm, true);
        if (faction == null) return 0;
        player.displayClientMessage(Component.literal("[Living World] New cult founded: #" + faction.slot() + " " + faction.name()
                + " • " + realm.displayName()).withStyle(ChatFormatting.DARK_PURPLE), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int factionSetRep(ServerPlayer player, int slot, int desired) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        WorldFaction faction = FactionManager.bySlot(level, slot);
        if (faction == null) return missingFaction(player, slot);
        int current = FactionManager.getReputation(player, faction);
        int value = FactionManager.adjustReputation(player, faction, desired - current);
        player.displayClientMessage(Component.literal("[Living World] " + faction.name() + " reputation = " + value
                + " (" + FactionManager.reputationLabel(value) + ")").withStyle(ChatFormatting.GOLD), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int missingFaction(ServerPlayer player, int slot) {
        player.displayClientMessage(Component.literal("[Living World] No generated faction in slot " + slot + ". Use /lw faction list.")
                .withStyle(ChatFormatting.RED), false);
        return 0;
    }

    private static int sense(ServerPlayer player) {
        player.displayClientMessage(Component.literal("[Living World] " + PowerSensingManager.senseNow(player))
                .withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int fightStyles(ServerPlayer player, String firstName, String secondName) {
        FighterArchetype firstStyle = parseStyle(firstName);
        FighterArchetype secondStyle = parseStyle(secondName);
        if (firstStyle == null || secondStyle == null) {
            player.displayClientMessage(Component.literal("[Living World] Styles: brawler, martial, ki, speed, guardian")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (!(player.level() instanceof ServerLevel level)) return 0;
        BlockPos anchor = AmbientFighterSpawner.findEncounterAnchor(player, true);
        if (anchor == null) return 0;
        BlockPos p1 = AmbientFighterSpawner.findSafeGroundAround(level, anchor, player.getRandom(), 2, 4, 12);
        // Give veteran test fights enough opening distance for power-ups, beams and
        // DMZ's native beam-clash detector to matter before they collapse into melee.
        BlockPos p2 = AmbientFighterSpawner.findSafeGroundAround(level, anchor, player.getRandom(), 12, 17, 18);
        if (p1 == null || p2 == null) return 0;
        FighterRank rank = FighterRank.VETERAN;
        AmbientFighterEntity first = AmbientFighterSpawner.spawnAt(level, p1, FighterAlignment.NEUTRAL, rank,
                FighterPersonality.PROUD, AmbientFighterSpawner.rollRaceForLevel(level, player.getRandom()), firstStyle, player.getRandom());
        AmbientFighterEntity second = AmbientFighterSpawner.spawnAt(level, p2, FighterAlignment.NEUTRAL, rank,
                FighterPersonality.CALM, AmbientFighterSpawner.rollRaceForLevel(level, player.getRandom()), secondStyle, player.getRandom());
        if (first == null || second == null) {
            if (first != null) first.discard();
            if (second != null) second.discard();
            return 0;
        }
        first.startDuel(second);
        second.startDuel(first);
        player.displayClientMessage(Component.literal("[Living World] Maximum Spectacle test: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(first.getFighterName() + " [" + first.getRace().displayName()
                        + (first.getRace().gendered() ? "/" + first.genderLabel() : "")
                        + ", PL " + first.getBattlePower() + "] vs "
                        + second.getFighterName() + " [" + second.getRace().displayName()
                        + (second.getRace().gendered() ? "/" + second.genderLabel() : "")
                        + ", PL " + second.getBattlePower() + "]").withStyle(ChatFormatting.WHITE)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static FighterArchetype parseStyle(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "brawler" -> FighterArchetype.BRAWLER;
            case "martial", "martial_artist" -> FighterArchetype.MARTIAL_ARTIST;
            case "ki", "ki_specialist" -> FighterArchetype.KI_SPECIALIST;
            case "speed", "speedster" -> FighterArchetype.SPEEDSTER;
            case "guardian" -> FighterArchetype.GUARDIAN;
            default -> null;
        };
    }

    private static int encounter(ServerPlayer player, EncounterType type) {
        int count = DynamicEncounterManager.spawnEncounter(player, type, true);
        if (count <= 0) {
            player.displayClientMessage(Component.literal("[Living World] Could not find enough safe ground for that encounter.")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }

        player.displayClientMessage(
                Component.literal("[Living World] Encounter spawned: ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(type.commandName()).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" (" + count + " fighter" + (count == 1 ? "" : "s") + ")")
                                .withStyle(ChatFormatting.GRAY)),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int status(ServerPlayer player) {
        List<AmbientFighterEntity> fighters = player.level().getEntitiesOfClass(
                AmbientFighterEntity.class,
                player.getBoundingBox().inflate(160.0D)
        );
        long good = fighters.stream().filter(f -> f.getAlignment() == FighterAlignment.GOOD).count();
        long neutral = fighters.stream().filter(f -> f.getAlignment() == FighterAlignment.NEUTRAL).count();
        long bad = fighters.stream().filter(f -> f.getAlignment() == FighterAlignment.BAD).count();
        long defeated = fighters.stream().filter(AmbientFighterEntity::isDefeated).count();
        long awakened = fighters.stream().filter(AmbientFighterEntity::isAwakened).count();
        long affiliated = fighters.stream().filter(AmbientFighterEntity::isFactionMember).count();
        long female = fighters.stream().filter(f -> f.getRace().gendered() && f.isFemale()).count();
        long male = fighters.stream().filter(f -> f.getRace().gendered() && !f.isFemale()).count();
        double anchor = player.level() instanceof ServerLevel server
                ? WorldPowerScaler.resolveWorldAnchor(server, player.blockPosition()) : 0.0D;
        player.displayClientMessage(Component.literal("[Living World] Fighters within 160 blocks: " + fighters.size()
                + " (good " + good + ", neutral " + neutral + ", bad " + bad + ", defeated " + defeated
                + ", awakened " + awakened + ", affiliated " + affiliated + ", gendered F/M " + female + "/" + male + ")"
                + " • world era " + (player.level() instanceof ServerLevel server ? WorldEraData.get(server).era().displayName() : "unknown")
                + " • era power anchor " + Math.round(anchor)
                + " • remembered " + FighterMemoryManager.count(player))
                .withStyle(ChatFormatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }
}
