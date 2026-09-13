package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.client.LWLang;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.SyncWishesS2C;
import com.dragonminez.common.wish.DragonWishRegistry;
import com.dragonminez.common.wish.Wish;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/** Adds live afterlife records to DMZ's own synced wish registry. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterWishIntegration {
    private static volatile boolean refreshPending;
    private FighterWishIntegration() {}

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        requestRefresh();
    }

    // Run after DMZ rebuilds its datapack registry. Running at HIGHEST copied the
    // registry while it was temporarily empty and then replaced every vanilla wish.
    // Defer the merge until the next server tick: OnDatapackSyncEvent is also where
    // DMZ sends its own packet, so mutating and sending from inside that callback
    // makes the visible result depend on listener ordering during /dmzreload.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void datapackSync(OnDatapackSyncEvent event) {
        requestRefresh();
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && refreshPending
                && !DragonWishRegistry.getServerWishes().isEmpty()) refresh(event.getServer());
    }

    public static void refresh(MinecraftServer server) {
        if (server == null) return;
        if (DragonWishRegistry.getServerWishes().isEmpty()) { refreshPending = true; return; }
        Map<String, List<Wish>> updated = new LinkedHashMap<>();
        DragonWishRegistry.getServerWishes().forEach((dragon, wishes) -> {
            List<Wish> clean = new ArrayList<>();
            for (Wish wish : wishes) if (!(wish instanceof FighterRevivalWish) && !(wish instanceof ClearWantedWish)) clean.add(wish);
            if (supportsLivingWorldWishes(dragon)) append(clean, server, isPorunga(dragon));
            updated.put(dragon, List.copyOf(clean));
        });
        DragonWishRegistry.setServerWishes(updated);
        refreshPending = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            NetworkHandler.sendToPlayer(new SyncWishesS2C(updated), player);
    }

    /** Marks the registry for a safe server-thread merge after DMZ has finished reloading it. */
    public static void requestRefresh() {
        refreshPending = true;
    }

    private static boolean supportsLivingWorldWishes(String dragonId) {
        if (dragonId == null || dragonId.isBlank()) return false;
        String path = dragonId.toLowerCase(Locale.ROOT);
        int namespaceSeparator = path.lastIndexOf(':');
        if (namespaceSeparator >= 0) path = path.substring(namespaceSeparator + 1);
        return "shenron".equals(path) || "shenlong".equals(path) || "porunga".equals(path);
    }

    private static boolean isPorunga(String dragonId) {
        if (dragonId == null) return false;
        String path = dragonId.toLowerCase(Locale.ROOT);
        int namespaceSeparator = path.lastIndexOf(':');
        if (namespaceSeparator >= 0) path = path.substring(namespaceSeparator + 1);
        return "porunga".equals(path);
    }

    private static void append(List<Wish> wishes, MinecraftServer server, boolean porunga) {
        wishes.add(new ClearWantedWish());
        List<FighterAfterlifeManager.DeadFighter> dead = FighterAfterlifeManager.deadFighters(server.overworld());
        for (FighterAfterlifeManager.DeadFighter fighter : dead) {
            wishes.add(new FighterRevivalWish(LWLang.speechKey("wish.revive_fighter", "Revive a Fighter — %s", fighter.name()),
                    "Bring " + fighter.name() + " back to the living world.", fighter.id(), "ONE"));
        }
        // Porunga can grant several wishes, but Living World only exposes its
        // individual fighter revivals there. Collective revivals are Shenron-only.
        if (porunga) return;
        if (dead.stream().anyMatch(f -> f.alignment() == com.dmzlivingworld.entity.FighterAlignment.GOOD))
            wishes.add(new FighterRevivalWish(LWLang.speechKey("wish.revive_all_good", "Revive All Good Fighters"),
                    "Revive every fallen friendly Living World fighter.", null, "ALL_GOOD"));
        if (dead.stream().anyMatch(FighterAfterlifeManager.DeadFighter::wanted))
            wishes.add(new FighterRevivalWish(LWLang.speechKey("wish.revive_all_wanted", "Revive All Wanted NPCs"),
                    "Revive every fallen Wanted Living World fighter.", null, "ALL_WANTED"));
        if (dead.stream().anyMatch(f -> f.alignment() != com.dmzlivingworld.entity.FighterAlignment.GOOD && !f.wanted()))
            wishes.add(new FighterRevivalWish(LWLang.speechKey("wish.revive_all_neutral", "Revive All Neutral NPCs"),
                    "Revive every fallen fighter who belongs to neither Otherworld nor Hell.", null, "ALL_NEUTRAL"));
        if (!dead.isEmpty()) wishes.add(new FighterRevivalWish(LWLang.speechKey("wish.revive_all", "Revive All NPCs"),
                "Revive every fallen Living World fighter.", null, "ALL"));
    }
}
