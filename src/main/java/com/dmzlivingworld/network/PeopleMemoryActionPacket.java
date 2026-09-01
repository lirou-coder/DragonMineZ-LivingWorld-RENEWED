package com.dmzlivingworld.network;

import com.dmzlivingworld.world.FighterMemoryManager;
import com.dmzlivingworld.world.LivingBondManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Explicit, confirmed client request to forget one or more entries from the player's own People journal. */
public final class PeopleMemoryActionPacket {
    private final String action;
    private final UUID recordId;

    public PeopleMemoryActionPacket(String action, UUID recordId) {
        this.action = action == null ? "" : action;
        this.recordId = recordId;
    }
    public static void encode(PeopleMemoryActionPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action, 24);
        buf.writeBoolean(msg.recordId != null);
        if (msg.recordId != null) buf.writeUUID(msg.recordId);
    }
    public static PeopleMemoryActionPacket decode(FriendlyByteBuf buf) {
        String action = buf.readUtf(24);
        UUID id = buf.readBoolean() ? buf.readUUID() : null;
        return new PeopleMemoryActionPacket(action, id);
    }
    public static void handle(PeopleMemoryActionPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ServerPlayer player = ctx.getSender();
        if (player != null) ctx.enqueueWork(() -> {
            switch (msg.action) {
                case "one" -> { if (msg.recordId != null) FighterMemoryManager.forget(player, msg.recordId); }
                case "all" -> FighterMemoryManager.clear(player);
                case "fallen" -> FighterMemoryManager.clearFallenView(player);
                case "companion_recall" -> LivingBondManager.regroupCompanion(player);
                case "companion_end" -> LivingBondManager.clearCompanion(player);
                case "meditation_invite" -> LivingBondManager.inviteNearestMeditationFriend(player);
                case "people_sort" -> FighterMemoryManager.cyclePeopleSort(player);
                default -> { }
            }
            if (msg.action.startsWith("companion_")) com.dmzlivingworld.command.LivingWorldCommands.openTravel(player);
            else if ("meditation_invite".equals(msg.action)) com.dmzlivingworld.command.LivingWorldCommands.openMeditationInfo(player);
            else com.dmzlivingworld.command.LivingWorldCommands.openPeople(player);
        });
        ctx.setPacketHandled(true);
    }
}
