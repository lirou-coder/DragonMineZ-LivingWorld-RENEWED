package com.dmzlivingworld.client;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.network.LWNetwork;
import com.dmzlivingworld.config.LivingWorldClientConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.WorldMenaceManager;
import com.dragonminez.client.systems.kisense.KiSenseScan;
import com.dragonminez.common.init.entities.sagas.SagaSaibamanEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

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
import net.minecraftforge.client.event.InputEvent;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Runtime client input; separate from mod-bus registrations. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private static Object lastLevel;
    private static final Map<UUID, String> LAST_MIRRORED_SPEECH = new HashMap<>();
    private static final Map<Integer, Integer> LAST_SAIBAMAN_BP = new HashMap<>();
    // Client tick is single-threaded. Reusing these sets avoids two short-lived hash tables
    // (and their backing arrays) on every polling pass.
    private static final Set<UUID> NEARBY_SPEECH_IDS = new HashSet<>();
    private static final Set<Integer> NEARBY_SAIBAMAN_IDS = new HashSet<>();

    private ClientForgeEvents() {}

    /** Keep first-person hands/items from rendering over Living World overlay screens. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (Minecraft.getInstance().screen instanceof LivingWorldScreenMarker) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof AmbientFighterEntity fighter)) return;
        // An unbound modifier intentionally means that ordinary right-click is sufficient.
        if (!ClientModEvents.FIGHTER_INTERACT.isUnbound() && !ClientModEvents.FIGHTER_INTERACT.isDown()) return;
        LWNetwork.interactWithFighter(fighter.getId(), event.getHand());
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            FighterDispositionClientState.clear();
            LAST_MIRRORED_SPEECH.clear();
            LAST_SAIBAMAN_BP.clear();
            NEARBY_SPEECH_IDS.clear();
            NEARBY_SAIBAMAN_IDS.clear();
            FactionRequestTrackerOverlay.clear();
            lastLevel = null;
            return;
        }
        if (minecraft.level != lastLevel) {
            FighterDispositionClientState.clear();
            LAST_MIRRORED_SPEECH.clear();
            LAST_SAIBAMAN_BP.clear();
            NEARBY_SPEECH_IDS.clear();
            NEARBY_SAIBAMAN_IDS.clear();
            FactionRequestTrackerOverlay.clear();
            lastLevel = minecraft.level;
        }
        while (ClientModEvents.OPEN_LIVING_WORLD.consumeClick()) {
            FactionRequestTrackerOverlay.suspendForWorldMenu();
            LWNetwork.requestMenu("world", 0);
        }
        while (ClientModEvents.TRACK_LAST_WORLD_EVENT.consumeClick()) {
            if (minecraft.player.connection != null) minecraft.player.connection.sendCommand("lwtrack last");
        }
        while (ClientModEvents.TOGGLE_FACTION_QUEST.consumeClick()) {
            FactionRequestTrackerOverlay.toggleVisibility();
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
        Set<Integer> nearby = NEARBY_SAIBAMAN_IDS;
        nearby.clear();
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
        // Synced speech lasts many ticks; a two-tick poll is visually immediate while halving
        // client entity-section queries in populated areas.
        if ((minecraft.player.tickCount & 1) != 0) return;
        int radius = LivingWorldClientConfig.speechChatRadius();
        Set<UUID> nearby = NEARBY_SPEECH_IDS;
        nearby.clear();
        for (AmbientFighterEntity fighter : minecraft.level.getEntitiesOfClass(AmbientFighterEntity.class,
                minecraft.player.getBoundingBox().inflate(radius), entity -> entity.isAlive())) {
            if (!isInsideSpeechChatRadius(minecraft, fighter, radius)) continue;
            UUID id = fighter.getUUID();
            nearby.add(id);
            String speech = fighter.getSpeech();
            if (speech == null || speech.isBlank()) {
                LAST_MIRRORED_SPEECH.remove(id);
                continue;
            }
            String previous = LAST_MIRRORED_SPEECH.put(id, speech);
            if (!speech.equals(previous)) {
                ChatFormatting nameColor = WorldMenaceManager.isHerobrine(fighter)
                        ? ChatFormatting.RED : ChatFormatting.AQUA;
                minecraft.gui.getChat().addMessage(Component.literal(fighter.getFighterName()).withStyle(nameColor)
                        .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
                        .append(LWLang.speech(speech).copy().withStyle(ChatFormatting.WHITE)));
            }
        }
        LAST_MIRRORED_SPEECH.keySet().removeIf(id -> !nearby.contains(id));
    }

    /**
     * Client-only spherical range check. Keep all three axes explicit so neither entity-section
     * lookup nor a large vertical separation can turn the configured chat radius into an X/Z-only
     * cylinder.
     */
    private static boolean isInsideSpeechChatRadius(Minecraft minecraft,
                                                    AmbientFighterEntity fighter, int radius) {
        if (minecraft.player == null || fighter == null || fighter.level() != minecraft.player.level()) return false;
        double dx = fighter.getX() - minecraft.player.getX();
        double dy = fighter.getY() - minecraft.player.getY();
        double dz = fighter.getZ() - minecraft.player.getZ();
        double radiusSquared = (double) radius * radius;
        return dx * dx + dy * dy + dz * dz <= radiusSquared;
    }
}
