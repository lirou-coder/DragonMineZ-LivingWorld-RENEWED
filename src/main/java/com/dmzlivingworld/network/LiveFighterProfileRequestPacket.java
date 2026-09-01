package com.dmzlivingworld.network;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.FighterInspectionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** C2S refresh for an already-open live fighter dossier (meditation time, scientist count, etc.). */
public record LiveFighterProfileRequestPacket(UUID fighterId) {
    public LiveFighterProfileRequestPacket {
        fighterId = fighterId == null ? new UUID(0L, 0L) : fighterId;
    }

    public static void encode(LiveFighterProfileRequestPacket msg, FriendlyByteBuf buf) { buf.writeUUID(msg.fighterId); }
    public static LiveFighterProfileRequestPacket decode(FriendlyByteBuf buf) { return new LiveFighterProfileRequestPacket(buf.readUUID()); }

    public static void handle(LiveFighterProfileRequestPacket msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer sender = ctx.getSender();
        if (sender != null) ctx.enqueueWork(() -> {
            if (!(sender.serverLevel().getEntity(msg.fighterId) instanceof AmbientFighterEntity fighter)) return;
            if (!fighter.isAlive() || sender.distanceToSqr(fighter) > 24.0D * 24.0D) return;
            FighterInspectionManager.inspect(sender, fighter, FighterInspectionManager.hasWornScouter(sender));
        });
        ctx.setPacketHandled(true);
    }
}
