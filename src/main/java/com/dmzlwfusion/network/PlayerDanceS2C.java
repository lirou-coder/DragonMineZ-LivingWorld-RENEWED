package com.dmzlwfusion.network;

import com.dmzlwfusion.client.ClientPlayerFusionAnimation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PlayerDanceS2C(int entityId, byte action) {
    public static final byte STOP = 0;
    public static final byte LEFT = 1;
    public static final byte RIGHT = 2;

    public static PlayerDanceS2C start(int entityId, boolean left) {
        return new PlayerDanceS2C(entityId, left ? LEFT : RIGHT);
    }

    public static PlayerDanceS2C stop(int entityId) {
        return new PlayerDanceS2C(entityId, STOP);
    }

    public static void encode(PlayerDanceS2C message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId);
        buffer.writeByte(message.action);
    }

    public static PlayerDanceS2C decode(FriendlyByteBuf buffer) {
        return new PlayerDanceS2C(buffer.readVarInt(), buffer.readByte());
    }

    public static void handle(PlayerDanceS2C message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPlayerFusionAnimation.handle(message.entityId, message.action)));
        context.setPacketHandled(true);
    }
}
