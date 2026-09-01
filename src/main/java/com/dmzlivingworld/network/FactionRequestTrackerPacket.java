package com.dmzlivingworld.network;

import com.dmzlivingworld.client.screen.FactionRequestTrackerOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C snapshot for the persistent accepted faction-request HUD.
 * R28 carries the exact interaction radius/prompt/timer used by the server objective so the HUD
 * cannot say "HERE" while the backend still expects the player to move or wait.
 */
public record FactionRequestTrackerPacket(boolean active, String title, String progress, String direction, int distance,
                                          double targetX, double targetZ, int stepIndex, int stepTotal,
                                          String stepLabel, String nextStep, int arrivalRadius,
                                          String actionPrompt, int secondsRemaining, List<SupplyItemSnapshot> supplyItems) {
    public FactionRequestTrackerPacket {
        title = safe(title); progress = safe(progress); direction = safe(direction); distance = Math.max(0, distance);
        if (!Double.isFinite(targetX)) targetX = 0.0D;
        if (!Double.isFinite(targetZ)) targetZ = 0.0D;
        stepTotal = Math.max(0, Math.min(12, stepTotal));
        stepIndex = stepTotal <= 0 ? 0 : Math.max(1, Math.min(stepTotal, stepIndex));
        stepLabel = safe(stepLabel); nextStep = safe(nextStep);
        arrivalRadius = Math.max(1, Math.min(320, arrivalRadius));
        actionPrompt = safe(actionPrompt);
        secondsRemaining = Math.max(-1, Math.min(3600, secondsRemaining));
        supplyItems = supplyItems == null ? List.of() : List.copyOf(supplyItems);
    }

    /** R27 compatibility constructor retained for old call sites while R28 migrates the tracker. */
    public FactionRequestTrackerPacket(boolean active, String title, String progress, String direction, int distance,
                                       double targetX, double targetZ, int stepIndex, int stepTotal,
                                       String stepLabel, String nextStep) {
        this(active, title, progress, direction, distance, targetX, targetZ,
                stepIndex, stepTotal, stepLabel, nextStep, 4, "", -1, List.of());
    }

    /** R26 source-compatibility constructor. */
    public FactionRequestTrackerPacket(boolean active, String title, String progress, String direction, int distance,
                                       double targetX, double targetZ) {
        this(active, title, progress, direction, distance, targetX, targetZ,
                0, 0, "", "", 4, "", -1, List.of());
    }

    private static String safe(String s) { return s == null ? "" : s; }

    public static FactionRequestTrackerPacket clear() {
        return new FactionRequestTrackerPacket(false, "", "", "", 0, 0.0D, 0.0D,
                0, 0, "", "", 4, "", -1, List.of());
    }

    public static void encode(FactionRequestTrackerPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active); buf.writeUtf(msg.title, 256); buf.writeUtf(msg.progress, 768);
        buf.writeUtf(msg.direction, 64); buf.writeVarInt(msg.distance);
        buf.writeDouble(msg.targetX); buf.writeDouble(msg.targetZ);
        buf.writeVarInt(msg.stepIndex); buf.writeVarInt(msg.stepTotal);
        buf.writeUtf(msg.stepLabel, 384); buf.writeUtf(msg.nextStep, 384);
        buf.writeVarInt(msg.arrivalRadius); buf.writeUtf(msg.actionPrompt, 384); buf.writeVarInt(msg.secondsRemaining);
        buf.writeVarInt(Math.min(8, msg.supplyItems.size()));
        for (int i = 0; i < Math.min(8, msg.supplyItems.size()); i++) SupplyItemSnapshot.encode(msg.supplyItems.get(i), buf);
    }

    public static FactionRequestTrackerPacket decode(FriendlyByteBuf buf) {
        boolean active = buf.readBoolean(); String title = buf.readUtf(256); String progress = buf.readUtf(768);
        String direction = buf.readUtf(64); int distance = buf.readVarInt(); double targetX = buf.readDouble(), targetZ = buf.readDouble();
        int stepIndex = buf.readVarInt(), stepTotal = buf.readVarInt(); String stepLabel = buf.readUtf(384), nextStep = buf.readUtf(384);
        int radius = buf.readVarInt(); String prompt = buf.readUtf(384); int seconds = buf.readVarInt();
        int supplyCount = Math.max(0, Math.min(8, buf.readVarInt())); List<SupplyItemSnapshot> supplyItems = new ArrayList<>(supplyCount);
        for (int i = 0; i < supplyCount; i++) supplyItems.add(SupplyItemSnapshot.decode(buf));
        return new FactionRequestTrackerPacket(active, title, progress, direction, distance, targetX, targetZ,
                stepIndex, stepTotal, stepLabel, nextStep, radius, prompt, seconds, supplyItems);
    }

    public static void handle(FactionRequestTrackerPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FactionRequestTrackerOverlay.update(msg)));
        ctx.setPacketHandled(true);
    }
}
