package com.dmzlivingworld.network;

import com.dmzlivingworld.client.screen.FactionRequestCompleteScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-authoritative success receipt shown once when a faction request genuinely completes. */
public record FactionRequestCompletePacket(String title, String factionName, String summary,
                                           String rewards, String worldImpact) {
    public FactionRequestCompletePacket {
        title = safe(title); factionName = safe(factionName); summary = safe(summary);
        rewards = safe(rewards); worldImpact = safe(worldImpact);
    }

    private static String safe(String value) { return value == null ? "" : value; }

    public static void encode(FactionRequestCompletePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.title, 256); buf.writeUtf(msg.factionName, 256); buf.writeUtf(msg.summary, 1024);
        buf.writeUtf(msg.rewards, 512); buf.writeUtf(msg.worldImpact, 1024);
    }

    public static FactionRequestCompletePacket decode(FriendlyByteBuf buf) {
        return new FactionRequestCompletePacket(buf.readUtf(256), buf.readUtf(256), buf.readUtf(1024),
                buf.readUtf(512), buf.readUtf(1024));
    }

    public static void handle(FactionRequestCompletePacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FactionRequestCompleteScreen.open(msg)));
        ctx.setPacketHandled(true);
    }
}
