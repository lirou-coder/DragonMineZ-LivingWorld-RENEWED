package com.dmzlivingworld.network;

import com.dmzlivingworld.config.LivingWorldConfig;
import com.kunyo.dbzmeditation.MeditationConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S settings update. Client-only readability/visual settings are saved locally by the screen. */
public record WorldSettingsUpdatePacket(LivingWorldConfig.Snapshot world,
                                        MeditationConfig.ServerSnapshot meditation) {
    public static void encode(WorldSettingsUpdatePacket msg, FriendlyByteBuf buf) {
        WorldSettingsPacket.writeWorld(buf, msg.world);
        WorldSettingsPacket.writeMeditation(buf, msg.meditation);
    }
    public static WorldSettingsUpdatePacket decode(FriendlyByteBuf buf) {
        return new WorldSettingsUpdatePacket(WorldSettingsPacket.readWorld(buf), WorldSettingsPacket.readMeditation(buf));
    }
    public static void handle(WorldSettingsUpdatePacket msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer sender = ctx.getSender();
        if (sender != null) ctx.enqueueWork(() -> {
            if (!canEdit(sender)) return;
            LivingWorldConfig.apply(msg.world());
            MeditationConfig.applyServer(msg.meditation());
            LWNetwork.sendWorldSettings(sender, true);
        });
        ctx.setPacketHandled(true);
    }
    public static boolean canEdit(ServerPlayer player) {
        if (player == null || player.getServer() == null) return false;
        return player.createCommandSourceStack().hasPermission(2)
                || player.getServer().isSingleplayerOwner(player.getGameProfile());
    }
}
