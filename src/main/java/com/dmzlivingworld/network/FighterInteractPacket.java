package com.dmzlivingworld.network;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client-selected Fighter Interact modifier; every action is validated again by the server. */
public record FighterInteractPacket(int entityId, InteractionHand hand) {
    public static void encode(FighterInteractPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId);
        buffer.writeEnum(message.hand);
    }

    public static FighterInteractPacket decode(FriendlyByteBuf buffer) {
        return new FighterInteractPacket(buffer.readVarInt(), buffer.readEnum(InteractionHand.class));
    }

    public static void handle(FighterInteractPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> {
            if (message.hand != InteractionHand.MAIN_HAND) return;
            if (!(player.serverLevel().getEntity(message.entityId) instanceof AmbientFighterEntity fighter)) return;
            if (!fighter.isAlive() || player.distanceToSqr(fighter) > 12.0D * 12.0D) return;
            fighter.performFighterInteraction(player, message.hand);
        });
        context.setPacketHandled(true);
    }
}
