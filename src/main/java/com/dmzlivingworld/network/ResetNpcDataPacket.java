package com.dmzlivingworld.network;

import com.dmzlivingworld.world.LivingWorldDataResetManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Empty C2S packet. Reset is restricted to the same authority level as World Settings edits. */
public record ResetNpcDataPacket() {
    public static void encode(ResetNpcDataPacket msg, FriendlyByteBuf buf) { }
    public static ResetNpcDataPacket decode(FriendlyByteBuf buf) { return new ResetNpcDataPacket(); }
    public static void handle(ResetNpcDataPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ServerPlayer sender = ctx.getSender();
        if (sender != null) ctx.enqueueWork(() -> {
            if (WorldSettingsUpdatePacket.canEdit(sender)) LivingWorldDataResetManager.reset(sender.getServer());
        });
        ctx.setPacketHandled(true);
    }
}
