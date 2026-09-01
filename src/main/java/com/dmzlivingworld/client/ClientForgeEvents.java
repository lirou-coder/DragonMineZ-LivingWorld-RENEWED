package com.dmzlivingworld.client;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.network.LWNetwork;
import com.dmzlivingworld.config.LivingWorldClientConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dragonminez.client.systems.kisense.KiSenseScan;
import com.dragonminez.common.init.entities.sagas.SagaSaibamanEntity;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.dmzlivingworld.client.screen.LivingWorldScreenMarker;
import com.dmzlivingworld.client.screen.FactionRequestTrackerOverlay;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Runtime client input; separate from mod-bus registrations. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private static Object lastLevel;
    private static final Map<UUID, String> LAST_MIRRORED_SPEECH = new HashMap<>();
    private static final Map<Integer, Integer> LAST_SAIBAMAN_BP = new HashMap<>();

    private ClientForgeEvents() {}

    /** Keep first-person hands/items from rendering over Living World overlay screens. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (Minecraft.getInstance().screen instanceof LivingWorldScreenMarker) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            FighterDispositionClientState.clear();
            LAST_MIRRORED_SPEECH.clear();
            LAST_SAIBAMAN_BP.clear();
            FactionRequestTrackerOverlay.clear();
            lastLevel = null;
            return;
        }
        if (minecraft.level != lastLevel) {
            FighterDispositionClientState.clear();
            LAST_MIRRORED_SPEECH.clear();
            LAST_SAIBAMAN_BP.clear();
            FactionRequestTrackerOverlay.clear();
            lastLevel = minecraft.level;
        }
        while (ClientModEvents.OPEN_LIVING_WORLD.consumeClick()) {
            LWNetwork.requestMenu("world", 0);
        }
        while (ClientModEvents.TRACK_LAST_WORLD_EVENT.consumeClick()) {
            if (minecraft.player.connection != null) minecraft.player.connection.sendCommand("lwtrack last");
        }
        mirrorNearbySpeechToChat(minecraft);
        refreshKiSenseWhenSaibamanPowerChanges(minecraft);
    }

    /**
     * DMZ Ki Sense intentionally caches entity Power Levels for about five seconds. Scientist
     * specimens receive their generated BP after native saga construction, so refresh the cache
     * as soon as the synced Saibaman BP arrives instead of showing the old constructor value.
     */
    private static void refreshKiSenseWhenSaibamanPowerChanges(Minecraft minecraft) {
        if (minecraft.player.tickCount % 5 != 0) return;
        Set<Integer> nearby = new HashSet<>();
        boolean changed = false;
        for (SagaSaibamanEntity saibaman : minecraft.level.getEntitiesOfClass(SagaSaibamanEntity.class,
                minecraft.player.getBoundingBox().inflate(192.0D), entity -> entity.isAlive())) {
            int id = saibaman.getId();
            int bp = Math.max(1, saibaman.getBattlePower());
            nearby.add(id);
            Integer previous = LAST_SAIBAMAN_BP.put(id, bp);
            if (previous == null || previous.intValue() != bp) changed = true;
        }
        LAST_SAIBAMAN_BP.keySet().removeIf(id -> !nearby.contains(id));
        if (changed) KiSenseScan.forceRescan();
    }

    /** Optional client-side mirror. Floating speech stays intact; this only adds a chat copy. */
    private static void mirrorNearbySpeechToChat(Minecraft minecraft) {
        if (!LivingWorldClientConfig.speechToChat()) {
            LAST_MIRRORED_SPEECH.clear();
            return;
        }
        int radius = LivingWorldClientConfig.speechChatRadius();
        Set<UUID> nearby = new HashSet<>();
        for (AmbientFighterEntity fighter : minecraft.level.getEntitiesOfClass(AmbientFighterEntity.class,
                minecraft.player.getBoundingBox().inflate(radius), entity -> entity.isAlive())) {
            if (minecraft.player.distanceToSqr(fighter) > (double)radius * radius) continue;
            UUID id = fighter.getUUID();
            nearby.add(id);
            String speech = fighter.getSpeech();
            if (speech == null || speech.isBlank()) {
                LAST_MIRRORED_SPEECH.remove(id);
                continue;
            }
            String previous = LAST_MIRRORED_SPEECH.put(id, speech);
            if (!speech.equals(previous)) {
                minecraft.gui.getChat().addMessage(Component.literal("[Living World] " + fighter.getFighterName() + ": " + speech));
            }
        }
        LAST_MIRRORED_SPEECH.keySet().removeIf(id -> !nearby.contains(id));
    }
}
