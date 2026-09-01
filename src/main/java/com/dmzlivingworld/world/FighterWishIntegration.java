package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.SyncWishesS2C;
import com.dragonminez.common.wish.DragonWishRegistry;
import com.dragonminez.common.wish.Wish;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/** Adds live afterlife records to DMZ's own synced wish registry. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterWishIntegration {
    private FighterWishIntegration() {}

    @SubscribeEvent public static void serverStarted(ServerStartedEvent event) { refresh(event.getServer()); }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void datapackSync(OnDatapackSyncEvent event) { refresh(event.getPlayerList().getServer()); }

    public static void refresh(MinecraftServer server) {
        if (server == null) return;
        Map<String, List<Wish>> updated = new LinkedHashMap<>();
        DragonWishRegistry.getServerWishes().forEach((dragon, wishes) -> {
            List<Wish> clean = new ArrayList<>();
            for (Wish wish : wishes) if (!(wish instanceof FighterRevivalWish)) clean.add(wish);
            if ("shenron".equalsIgnoreCase(dragon) || "porunga".equalsIgnoreCase(dragon)) append(clean, server);
            updated.put(dragon, List.copyOf(clean));
        });
        DragonWishRegistry.setServerWishes(updated);
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            NetworkHandler.sendToPlayer(new SyncWishesS2C(updated), player);
    }

    private static void append(List<Wish> wishes, MinecraftServer server) {
        List<FighterAfterlifeManager.DeadFighter> dead = FighterAfterlifeManager.deadFighters(server.overworld());
        for (FighterAfterlifeManager.DeadFighter fighter : dead) {
            wishes.add(new FighterRevivalWish("Revive a Fighter — " + fighter.name(),
                    "Bring " + fighter.name() + " back to the living world.", fighter.id(), "ONE"));
        }
        if (dead.stream().anyMatch(f -> f.alignment() != com.dmzlivingworld.entity.FighterAlignment.BAD))
            wishes.add(new FighterRevivalWish("Revive all good Fighters",
                    "Revive every fallen good and neutral Living World fighter.", null, "ALL_GOOD"));
        if (dead.stream().anyMatch(f -> f.alignment() == com.dmzlivingworld.entity.FighterAlignment.BAD))
            wishes.add(new FighterRevivalWish("Revive all evil Fighters",
                    "Revive every fallen evil Living World fighter.", null, "ALL_EVIL"));
    }
}
