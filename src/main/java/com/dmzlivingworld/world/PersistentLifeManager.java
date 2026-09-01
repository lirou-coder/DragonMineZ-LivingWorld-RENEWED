package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.config.LivingWorldConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Coarse off-screen life simulation for remembered Living World fighters. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PersistentLifeManager {
    private static long lastTick = Long.MIN_VALUE;
    private PersistentLifeManager() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.getServer().overworld().getGameTime();
        if (lastTick == now || now % 1200L != 0L) return;
        lastTick = now;
        if (!LivingWorldConfig.recurringFighters()) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            FighterMemoryManager.tickPersistentLives(player, now);
        }
    }

    public static void clearRuntime() { lastTick = Long.MIN_VALUE; }
}
