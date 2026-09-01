package com.dmzlwfusion;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.extras.ActionMode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = com.dmzlivingworld.LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LWFusionEvents {
    private LWFusionEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        LWFusionManager.tickPendingDances(server);
        NpcFusionManager.tick(server);
    }

    /**
     * Immersive NPC<->NPC setup. With DMZ's Fusion action selected and an empty
     * hand, interact with one LW fighter and then a second compatible fighter.
     * Debug commands are not involved in this path.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof LivingEntity target) || !LivingWorldCompat.isLivingWorldFighter(target)) return;
        if (!player.getItemInHand(event.getHand()).isEmpty()) return;

        StatsData stats = player.getCapability(StatsCapability.INSTANCE).orElse(null);
        if (stats == null || stats.getStatus().getSelectedAction() != ActionMode.FUSION || !stats.getSkills().hasSkill("fusion")) return;
        if (stats.getStatus().isFused() || LWFusionManager.isDancePending(player)) return;

        if (NpcFusionManager.selectNaturalParticipant(player, target)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // Abnormal shutdown recovery is intentionally conservative: resume as
        // the original characters instead of guessing where a dance was.
        if (event.getEntity() instanceof ServerPlayer player) endEverythingOwnedBy(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) endEverythingOwnedBy(player);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity living = event.getEntity();
        if (NpcFusionManager.isTemporaryFused(living)) {
            // Separation replaces death for the temporary fusion body; prevent a
            // transient third fighter from producing death drops/side effects.
            NpcFusionManager.onTemporaryDeath(living);
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) endEverythingOwnedBy(player);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;

        NpcFusionManager.onEntityJoin(living);

        if (!LivingWorldCompat.isLivingWorldFighter(living) || !LWFusionManager.hasPartnerBackup(living)) return;
        // Crash recovery for an original NPC saved invisible/no-AI during either
        // player<->NPC or NPC<->NPC fusion. Keep it hidden only when its matching
        // live session can be proved to exist.
        if (!LWFusionManager.partnerBelongsToLiveSession(living, event.getLevel().getServer())) {
            LWFusionManager.restoreOrphanPartner(living);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            // Clean shutdown must unwind pending dances too, not only already-active fusions.
            // This makes stopping the server a safe pre-removal point for the bridge.
            endEverythingOwnedBy(player);
        }
        LWFusionManager.cancelAllPendingDances(event.getServer());
        NpcFusionManager.endAll(event.getServer());
    }

    private static void endEverythingOwnedBy(ServerPlayer player) {
        LWFusionManager.cancelPlayerDance(player);
        NpcFusionManager.clearNaturalSelection(player);
        NpcFusionManager.cancelForInitiator(player);
        endPlayerFusion(player);
    }

    private static void endPlayerFusion(ServerPlayer player) {
        if (!LWFusionManager.isActive(player)) return;
        StatsData stats = player.getCapability(StatsCapability.INSTANCE).orElse(null);
        if (stats != null) LWFusionManager.forceEnd(player, stats);
    }
}
