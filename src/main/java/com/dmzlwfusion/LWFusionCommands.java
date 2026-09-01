package com.dmzlwfusion;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.mojang.brigadier.Command;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = com.dmzlivingworld.LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LWFusionCommands {
    private LWFusionCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("lwfusion")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> status(ctx.getSource().getPlayerOrException()))
                        .then(Commands.literal("status")
                                .executes(ctx -> status(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("scan")
                                .executes(ctx -> scan(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("bind")
                                .executes(ctx -> bind(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("unbind")
                                .executes(ctx -> unbind(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("force")
                                .executes(ctx -> force(ctx.getSource().getPlayerOrException(), false))
                                .then(Commands.literal("instant")
                                        .executes(ctx -> force(ctx.getSource().getPlayerOrException(), true))))
                        .then(Commands.literal("end")
                                .executes(ctx -> end(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("restore")
                                .executes(ctx -> restore(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("on")
                                        .executes(ctx -> debug(ctx.getSource().getPlayerOrException(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> debug(ctx.getSource().getPlayerOrException(), false))))
                        .then(Commands.literal("npc")
                                .executes(ctx -> npcStatus(ctx.getSource().getPlayerOrException()))
                                .then(Commands.literal("scan")
                                        .executes(ctx -> npcScan(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("status")
                                        .executes(ctx -> npcStatus(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("bind")
                                        .then(Commands.literal("first")
                                                .executes(ctx -> npcBind(ctx.getSource().getPlayerOrException(), true)))
                                        .then(Commands.literal("second")
                                                .executes(ctx -> npcBind(ctx.getSource().getPlayerOrException(), false))))
                                .then(Commands.literal("unbind")
                                        .executes(ctx -> npcUnbind(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("force")
                                        .executes(ctx -> npcForce(ctx.getSource().getPlayerOrException(), false))
                                        .then(Commands.literal("instant")
                                                .executes(ctx -> npcForce(ctx.getSource().getPlayerOrException(), true))))
                                .then(Commands.literal("end")
                                        .executes(ctx -> npcEnd(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("restore")
                                        .executes(ctx -> npcRestore(ctx.getSource().getPlayerOrException()))))
        );
    }

    private static int status(ServerPlayer player) {
        StatsData stats = stats(player);
        player.displayClientMessage(Component.literal("[Living World Fusion] Integrated Native Dance + NPC Fusion").withStyle(ChatFormatting.GOLD), false);
        if (stats == null) {
            player.displayClientMessage(Component.literal("DMZ StatsData: MISSING").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        UUID companion = LivingWorldCompat.companionId(player);
        UUID bound = LWFusionManager.debugPartnerId(player);
        LivingEntity companionEntity = LWFusionManager.findCompanionPartner(player);
        LivingEntity preferred = LWFusionManager.findPreferredDebugPartner(player);
        String playerRace = safe(stats.getCharacter().getRace());
        int playerTotal = Math.max(1, stats.getStats().getTotalStats());
        int fusionLevel = Math.max(0, stats.getSkills().getSkillLevel("fusion"));
        int fusionMax = Math.max(1, stats.getSkills().getMaxSkillLevel("fusion"));

        line(player, "Bridge session", LWFusionManager.isActive(player) ? "ACTIVE" : "inactive",
                LWFusionManager.isActive(player) ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        line(player, "Player/NPC dance", LWFusionManager.isDancePending(player)
                        ? "ACTIVE (" + LWFusionManager.danceTicksRemaining(player) + " ticks left)" : "inactive",
                LWFusionManager.isDancePending(player) ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY);
        line(player, "DMZ fused", String.valueOf(stats.getStatus().isFused()), stats.getStatus().isFused() ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        line(player, "Selected action", String.valueOf(stats.getStatus().getSelectedAction()), ChatFormatting.GRAY);
        line(player, "Action charge", String.valueOf(stats.getResources().getActionCharge()), ChatFormatting.GRAY);
        line(player, "Fusion skill", fusionLevel + "/" + fusionMax + " (known=" + stats.getSkills().hasSkill("fusion") + ")", ChatFormatting.GRAY);
        line(player, "Combat cooldown", String.valueOf(stats.getCooldowns().hasCooldown("CombatTimer")), ChatFormatting.GRAY);
        line(player, "Fusion cooldown", String.valueOf(stats.getCooldowns().hasCooldown("FusionCooldown")), ChatFormatting.GRAY);
        line(player, "Player race / total", playerRace + " / " + playerTotal, ChatFormatting.AQUA);
        line(player, "LW companion UUID", companion == null ? "none" : companion.toString(), companion == null ? ChatFormatting.YELLOW : ChatFormatting.AQUA);
        line(player, "Debug-bound UUID", bound == null ? "none" : bound.toString(), bound == null ? ChatFormatting.GRAY : ChatFormatting.LIGHT_PURPLE);
        line(player, "Debug messages", LWFusionManager.debugEnabled(player) ? "ON" : "off", LWFusionManager.debugEnabled(player) ? ChatFormatting.GREEN : ChatFormatting.GRAY);

        if (companion != null && companionEntity == null) {
            player.displayClientMessage(Component.literal(" - LW companion exists but is not a loaded fighter within 5 blocks.").withStyle(ChatFormatting.YELLOW), false);
        }
        if (preferred == null) {
            player.displayClientMessage(Component.literal(" - No usable debug partner is loaded within 16 blocks. Use /lwfusion scan then /lwfusion bind.")
                    .withStyle(ChatFormatting.YELLOW), false);
        } else {
            showPartner(player, stats, preferred, playerTotal, bound != null && bound.equals(preferred.getUUID()) ? "debug-bound" : "LW companion");
        }

        if (LWFusionManager.isActive(player)) {
            LivingEntity active = LWFusionManager.activePartner(player);
            line(player, "Active partner", active == null ? "NOT FOUND" : LivingWorldCompat.fighterName(active) + " " + active.getUUID(),
                    active == null ? ChatFormatting.RED : ChatFormatting.GREEN);
            line(player, "Fusion timer", stats.getStatus().getFusionTimer() + " ticks", ChatFormatting.GREEN);
            line(player, "Fusion name", safe(stats.getStatus().getFusionName()), ChatFormatting.GREEN);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int scan(ServerPlayer player) {
        LivingEntity fighter = LWFusionManager.nearestLivingWorldFighter(player, 16.0D);
        if (fighter == null) {
            player.displayClientMessage(Component.literal("[LW Fusion] No Living World fighter found within 16 blocks.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        double distance = Math.sqrt(player.distanceToSqr(fighter));
        player.displayClientMessage(Component.literal("[LW Fusion] Nearest fighter: " + LivingWorldCompat.fighterName(fighter)
                + " | race=" + LivingWorldCompat.raceId(fighter)
                + " | BP=" + LivingWorldCompat.battlePower(fighter)
                + " | distance=" + String.format(Locale.ROOT, "%.1f", distance)
                + " | UUID=" + fighter.getUUID()).withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int bind(ServerPlayer player) {
        LivingEntity fighter = LWFusionManager.nearestLivingWorldFighter(player, 16.0D);
        if (fighter == null || !LWFusionManager.bindNearestDebugPartner(player)) {
            player.displayClientMessage(Component.literal("[LW Fusion] No Living World fighter found within 16 blocks.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        player.displayClientMessage(Component.literal("[LW Fusion] Debug partner bound: " + LivingWorldCompat.fighterName(fighter)
                + " (does not alter Living World's real companion bond).")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int unbind(ServerPlayer player) {
        LWFusionManager.clearDebugPartner(player);
        player.displayClientMessage(Component.literal("[LW Fusion] Debug partner cleared.").withStyle(ChatFormatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int force(ServerPlayer player, boolean instant) {
        StatsData stats = stats(player);
        if (stats == null) {
            player.displayClientMessage(Component.literal("[LW Fusion] DMZ StatsData is unavailable.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        boolean ok = instant ? LWFusionManager.forceMetamoruInstant(player, stats) : LWFusionManager.forceMetamoru(player, stats);
        if (!ok) {
            player.displayClientMessage(Component.literal("[LW Fusion] Debug force failed. Run /lwfusion status.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        player.displayClientMessage(Component.literal(instant
                ? "[LW Fusion] DEBUG INSTANT fusion accepted."
                : "[LW Fusion] DEBUG FORCE accepted; native dance sequence should now play before fusion.")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int end(ServerPlayer player) {
        boolean pending = LWFusionManager.isDancePending(player);
        boolean active = LWFusionManager.isActive(player);
        StatsData stats = stats(player);
        if (!pending && !active) {
            player.displayClientMessage(Component.literal("[LW Fusion] No player/NPC dance or fusion to end.").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        if (pending) LWFusionManager.cancelPlayerDance(player);
        if (active && stats != null) LWFusionManager.forceEnd(player, stats);
        player.displayClientMessage(Component.literal("[LW Fusion] Player/NPC fusion sequence ended; restoration requested.").withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int restore(ServerPlayer player) {
        StatsData stats = stats(player);
        boolean pending = LWFusionManager.isDancePending(player);
        boolean active = LWFusionManager.isActive(player);
        if (pending) LWFusionManager.cancelPlayerDance(player);
        if (active && stats != null) LWFusionManager.forceEnd(player, stats);
        int restored = LWFusionManager.restoreNearbyOrphans(player, 64.0D);
        player.displayClientMessage(Component.literal("[LW Fusion] Player/NPC recovery: danceCanceled=" + pending
                + ", activeFusionEnded=" + active + ", nearbyOrphansRestored=" + restored + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int npcScan(ServerPlayer player) {
        LivingEntity fighter = LWFusionManager.nearestLivingWorldFighter(player, 12.0D);
        if (fighter == null) {
            player.displayClientMessage(Component.literal("[LW Fusion NPC] No LW fighter within 12 blocks.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        player.displayClientMessage(Component.literal("[LW Fusion NPC] Nearest: " + LivingWorldCompat.fighterName(fighter)
                + " | race=" + LivingWorldCompat.raceId(fighter)
                + " | BP=" + LivingWorldCompat.battlePower(fighter)
                + " | UUID=" + fighter.getUUID()).withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int npcBind(ServerPlayer player, boolean first) {
        LivingEntity nearest = LWFusionManager.nearestLivingWorldFighter(player, 12.0D);
        if (nearest == null || !NpcFusionManager.bindNearest(player, first)) {
            player.displayClientMessage(Component.literal("[LW Fusion NPC] Could not bind the nearest fighter to "
                    + (first ? "FIRST" : "SECOND") + ". Move close to a different fighter.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        player.displayClientMessage(Component.literal("[LW Fusion NPC] " + (first ? "FIRST" : "SECOND")
                + " = " + LivingWorldCompat.fighterName(nearest) + " [" + nearest.getUUID() + "]")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int npcUnbind(ServerPlayer player) {
        NpcFusionManager.clearBindings(player);
        NpcFusionManager.clearNaturalSelection(player);
        player.displayClientMessage(Component.literal("[LW Fusion NPC] Pair bindings cleared.").withStyle(ChatFormatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int npcStatus(ServerPlayer player) {
        player.displayClientMessage(Component.literal("[LW Fusion NPC] " + NpcFusionManager.pairStatus(player)).withStyle(ChatFormatting.GOLD), false);
        line(player, "NPC dance", NpcFusionManager.isPending(player)
                        ? "ACTIVE (" + NpcFusionManager.pendingTicks(player) + " ticks left)" : "inactive",
                NpcFusionManager.isPending(player) ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY);
        line(player, "Strict pair eligible", String.valueOf(NpcFusionManager.strictPairEligible(player)),
                NpcFusionManager.strictPairEligible(player) ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        line(player, "Active temporary NPC fusions", String.valueOf(NpcFusionManager.activeCount()), ChatFormatting.AQUA);
        return Command.SINGLE_SUCCESS;
    }

    private static int npcForce(ServerPlayer player, boolean instant) {
        boolean ok = NpcFusionManager.startBoundFusion(player, instant);
        if (!ok) {
            player.displayClientMessage(Component.literal("[LW Fusion NPC] Force failed. Run /lwfusion npc status.").withStyle(ChatFormatting.RED), false);
            return 0;
        }
        player.displayClientMessage(Component.literal(instant
                ? "[LW Fusion NPC] DEBUG INSTANT accepted."
                : "[LW Fusion NPC] DEBUG FORCE accepted; both fighters should perform the native dance first.")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int npcEnd(ServerPlayer player) {
        boolean pending = NpcFusionManager.isPending(player);
        if (pending) NpcFusionManager.cancelPending(player.getServer(), player.getUUID(), true);
        int ended = NpcFusionManager.endForInitiator(player.getServer(), player.getUUID());
        player.displayClientMessage(Component.literal("[LW Fusion NPC] End requested: danceCanceled=" + pending + ", activeEnded=" + ended + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int npcRestore(ServerPlayer player) {
        NpcFusionManager.cancelPending(player.getServer(), player.getUUID(), true);
        int ended = NpcFusionManager.endForInitiator(player.getServer(), player.getUUID());
        int restored = NpcFusionManager.restoreNearby(player, 64.0D);
        restored += LWFusionManager.restoreNearbyOrphans(player, 64.0D);
        player.displayClientMessage(Component.literal("[LW Fusion NPC] Recovery complete: activeEnded=" + ended
                + ", nearbyRecovered=" + restored + ".").withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int debug(ServerPlayer player, boolean enabled) {
        LWFusionManager.setDebugEnabled(player, enabled);
        player.displayClientMessage(Component.literal("[LW Fusion] Debug messages " + (enabled ? "enabled." : "disabled."))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void showPartner(ServerPlayer player, StatsData stats, LivingEntity partner, int playerTotal, String source) {
        LWFusionProfile profile = LWFusionProfile.from(partner);
        int partnerTotal = Math.max(1, profile.totalStats());
        double diff = Math.abs(playerTotal - partnerTotal) / (double) Math.max(playerTotal, partnerTotal);
        double threshold = ConfigManager.getServerConfig().getGameplay().getMetamoruFusionThreshold();
        boolean sameRace = safe(stats.getCharacter().getRace()).equalsIgnoreCase(LivingWorldCompat.raceId(partner));
        boolean available = !LivingWorldCompat.unavailableForFusion(partner);
        boolean base = !LivingWorldCompat.hasActiveForm(partner);
        boolean strictGap = threshold <= 0.0D || diff <= threshold;

        player.displayClientMessage(Component.literal(" - Partner [" + source + "]: " + LivingWorldCompat.fighterName(partner)
                + " | race=" + LivingWorldCompat.raceId(partner)
                + " | BP=" + LivingWorldCompat.battlePower(partner)
                + " | derivedTotal=" + partnerTotal).withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.literal(" - Strict checks: sameRace=" + sameRace
                + ", available=" + available
                + ", baseForm=" + base
                + ", statGap=" + String.format(Locale.ROOT, "%.3f", diff)
                + " <= " + String.format(Locale.ROOT, "%.3f", threshold)
                + " => " + strictGap)
                .withStyle(sameRace && available && base && strictGap ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
    }

    private static StatsData stats(ServerPlayer player) {
        return player.getCapability(StatsCapability.INSTANCE).orElse(null);
    }

    private static void line(ServerPlayer player, String label, String value, ChatFormatting color) {
        player.displayClientMessage(Component.literal(" - " + label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(color)), false);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
