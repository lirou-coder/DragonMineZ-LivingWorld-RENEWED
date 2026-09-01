package com.dmzlivingworld.network;

import com.dmzlivingworld.client.screen.FactionTravelScreen;
import com.dmzlivingworld.world.FactionRequestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** S2C snapshot for the faction contact travel picker. */
public record FactionTravelScreenPacket(int factionSlot, String factionName, String instantTransmissionStatus,
                                        List<Contact> contacts) {
    public record Contact(UUID recordId, String name, String role, String rank, int relationship, String activity) {
        public Contact {
            name = safe(name); role = safe(role); rank = safe(rank); activity = safe(activity);
        }
    }

    public FactionTravelScreenPacket {
        factionSlot = Math.max(0, factionSlot);
        factionName = safe(factionName);
        instantTransmissionStatus = safe(instantTransmissionStatus);
        contacts = contacts == null ? List.of() : List.copyOf(contacts);
    }

    public static FactionTravelScreenPacket from(FactionRequestManager.TravelView view) {
        List<Contact> rows = view.contacts().stream().map(c -> new Contact(c.recordId(), c.name(),
                pretty(c.role().name()), c.rank().displayName(), c.relationship(), c.activity())).toList();
        return new FactionTravelScreenPacket(view.factionSlot(), view.factionName(), view.instantTransmissionStatus(), rows);
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String pretty(String s) {
        if (s == null || s.isBlank()) return "Member";
        String lower = s.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return java.lang.Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public static void encode(FactionTravelScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.factionSlot); buf.writeUtf(msg.factionName, 256); buf.writeUtf(msg.instantTransmissionStatus, 160);
        buf.writeVarInt(msg.contacts.size());
        for (Contact c : msg.contacts) {
            buf.writeUUID(c.recordId()); buf.writeUtf(c.name(), 256); buf.writeUtf(c.role(), 96); buf.writeUtf(c.rank(), 96);
            buf.writeInt(c.relationship()); buf.writeUtf(c.activity(), 160);
        }
    }

    public static FactionTravelScreenPacket decode(FriendlyByteBuf buf) {
        int slot = buf.readVarInt(); String name = buf.readUtf(256); String status = buf.readUtf(160);
        int count = Math.max(0, Math.min(64, buf.readVarInt())); List<Contact> contacts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) contacts.add(new Contact(buf.readUUID(), buf.readUtf(256), buf.readUtf(96), buf.readUtf(96), buf.readInt(), buf.readUtf(160)));
        return new FactionTravelScreenPacket(slot, name, status, contacts);
    }

    public static void handle(FactionTravelScreenPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FactionTravelScreen.open(msg)));
        ctx.setPacketHandled(true);
    }
}
