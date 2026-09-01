package com.dmzlivingworld.network;

import com.dmzlivingworld.client.FighterDispositionClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** One compact per-player snapshot for nearby fighter nametag intent indicators. */
public record FighterDispositionSnapshotPacket(List<Entry> entries) {
    public record Entry(int entityId, int dispositionId, int relationship) {}

    public FighterDispositionSnapshotPacket {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static void encode(FighterDispositionSnapshotPacket msg, FriendlyByteBuf buf) {
        int size = Math.min(96, msg.entries.size());
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            Entry entry = msg.entries.get(i);
            buf.writeVarInt(Math.max(0, entry.entityId));
            buf.writeByte(entry.dispositionId);
            buf.writeByte(Math.max(-101, Math.min(101, entry.relationship)));
        }
    }

    public static FighterDispositionSnapshotPacket decode(FriendlyByteBuf buf) {
        int size = Math.min(96, Math.max(0, buf.readVarInt()));
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readVarInt(), buf.readByte(), buf.readByte()));
        }
        return new FighterDispositionSnapshotPacket(entries);
    }

    public static void handle(FighterDispositionSnapshotPacket msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> FighterDispositionClientState.replace(msg.entries)));
        ctx.setPacketHandled(true);
    }
}
