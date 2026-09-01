package com.dmzlivingworld.network;

import com.dmzlivingworld.world.FighterInstantTransmissionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** C2S request to use the player's real DMZ Instant Transmission skill on a familiar remembered fighter. */
public record RememberedFighterTeleportPacket(UUID recordId) {
    public RememberedFighterTeleportPacket { recordId = recordId == null ? new UUID(0L, 0L) : recordId; }
    public static void encode(RememberedFighterTeleportPacket msg, FriendlyByteBuf buf) { buf.writeUUID(msg.recordId); }
    public static RememberedFighterTeleportPacket decode(FriendlyByteBuf buf) { return new RememberedFighterTeleportPacket(buf.readUUID()); }
    public static void handle(RememberedFighterTeleportPacket msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer sender = ctx.getSender();
        if (sender != null) ctx.enqueueWork(() -> FighterInstantTransmissionManager.travel(sender, msg.recordId));
        ctx.setPacketHandled(true);
    }
}
