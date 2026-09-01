package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Clears process-local state between players/worlds; persistent state stays in NBT/SavedData. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LivingWorldLifecycle {
    private LivingWorldLifecycle() {}

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PowerSensingManager.clearRuntime(player.getUUID());
        LivingBondManager.clearRuntime(player.getUUID());
        RescueMissionManager.clearRuntime(player);
        SanctionedMatchGuard.clearRuntime(player.getUUID());
        FighterPowerSpikeReactionManager.clearRuntime(player.getUUID());
        FighterTransformationReactionManager.clearRuntime(player.getUUID());
        WorldEventNavigationManager.clearRuntime(player.getUUID());
        // SparManager owns its PlayerLoggedOutEvent cleanup because it must also release the
        // fighter's sanctioned-match state. Do not remove its session here before that handler
        // can perform the paired NPC cleanup.
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) { clearProcessState(); }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        // Finish visible ambient activities before clearing process-local maps so temporary
        // props/poses cannot be serialized as permanent fighter state during shutdown.
        FighterAmbientActivityManager.clearRuntime(event.getServer());
        FighterPlayerSocialManager.clearRuntime(event.getServer());
        FighterPowerCompareManager.clearRuntime(event.getServer());
        clearProcessState();
    }

    public static String runtimeSummary() {
        return "transient entries — sensing " + PowerSensingManager.runtimeEntries()
                + " • bonds " + LivingBondManager.runtimeEntries()
                + " • rescue " + RescueMissionManager.runtimeEntries()
                + " • combat " + FighterCombatDirector.runtimeEntries()
                + " • faction locks " + FactionActivityRegistry.runtimeEntries()
                + " • player social " + FighterPlayerSocialManager.runtimeEntries()
                + " • life outings " + FighterLifeJoinManager.runtimeEntries()
                + " • ambient activities " + FighterAmbientActivityManager.runtimeEntries()
                + " • power comparisons " + FighterPowerCompareManager.runtimeEntries();
    }

    private static void clearProcessState() {
        PowerSensingManager.clearRuntime();
        LivingBondManager.clearRuntime();
        RescueMissionManager.clearRuntime();
        FighterCombatDirector.clearRuntime();
        FactionActivityRegistry.clearRuntime();
        WorldIncidentManager.clearRuntime();
        SparManager.clearRuntime();
        SanctionedMatchGuard.clearRuntime();
        FighterPlayerSocialManager.clearRuntime();
        FighterNpcSocialManager.clearRuntime();
        FighterLifeJoinManager.clearRuntime();
        FighterAmbientActivityManager.clearRuntime();
        FighterPowerCompareManager.clearRuntime();
        FighterPracticeSparManager.clearRuntime();
        FighterPowerSpikeReactionManager.clearRuntime();
        FighterAnimalMimicManager.clearRuntime();
        FighterTransformationReactionManager.clearRuntime();
        DialogueLocalityManager.clearRuntime();
        FighterDebugSpectateManager.clearRuntime();
        WorldMenaceManager.clearRuntime();
        PersistentLifeManager.clearRuntime();
        PhysicalContinuityManager.clearRuntime();
        WorldEventNavigationManager.clearRuntime();
    }
}
