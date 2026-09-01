package com.dmzlivingworld.network;

import com.dmzlivingworld.command.LivingWorldCommands;
import com.dmzlivingworld.world.FactionRequestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.function.Supplier;

/** C2S action for the dedicated simulation-backed faction Request Board. */
public record FactionRequestActionPacket(String action, int factionSlot) {
    public FactionRequestActionPacket {
        action = action == null ? "" : action.toLowerCase(Locale.ROOT);
        factionSlot = Math.max(0, factionSlot);
    }

    public static void encode(FactionRequestActionPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action, 64);
        buf.writeVarInt(msg.factionSlot);
    }

    public static FactionRequestActionPacket decode(FriendlyByteBuf buf) {
        return new FactionRequestActionPacket(buf.readUtf(64), buf.readVarInt());
    }

    public static void handle(FactionRequestActionPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ServerPlayer player = ctx.getSender();
        if (player != null) ctx.enqueueWork(() -> {
            FactionRequestManager.handleGuiAction(player, msg.factionSlot, msg.action);
            LivingWorldCommands.openPlayerFactionRequests(player, msg.factionSlot);
        });
        ctx.setPacketHandled(true);
    }
}
