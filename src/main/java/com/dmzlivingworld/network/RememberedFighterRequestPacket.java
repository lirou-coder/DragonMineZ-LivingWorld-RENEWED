package com.dmzlivingworld.network;

import com.dmzlivingworld.world.FighterInspectionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** C2S request for the read-only, last-remembered People profile. */
public record RememberedFighterRequestPacket(UUID recordId) {
    public RememberedFighterRequestPacket {
        recordId = recordId == null ? new UUID(0L, 0L) : recordId;
    }

    public static void encode(RememberedFighterRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.recordId);
    }

    public static RememberedFighterRequestPacket decode(FriendlyByteBuf buf) {
        return new RememberedFighterRequestPacket(buf.readUUID());
    }

    public static void handle(RememberedFighterRequestPacket msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer sender = ctx.getSender();
        if (sender != null) ctx.enqueueWork(() -> FighterInspectionManager.inspectRemembered(sender, msg.recordId));
        ctx.setPacketHandled(true);
    }
}
