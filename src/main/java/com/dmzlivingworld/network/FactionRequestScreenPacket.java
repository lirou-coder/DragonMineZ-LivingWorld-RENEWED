package com.dmzlivingworld.network;

import com.dmzlivingworld.client.screen.FactionRequestScreen;
import com.dmzlivingworld.world.FactionRequestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Structured S2C snapshot for the dedicated faction request board, including its in-place contact shortcut. */
public record FactionRequestScreenPacket(int factionSlot, String factionName, String standing, int reputation,
                                         boolean activeForFaction, boolean activeElsewhere, boolean hasRequest,
                                         String type, String title, String description, String difficulty,
                                         String reward, String progress, long refreshSeconds,
                                         boolean canAccept, boolean canAbandon, boolean canDeliver, String note,
                                         String travelFactionName, String instantTransmissionStatus,
                                         List<SupplyItemSnapshot> supplyItems,
                                         List<FactionTravelScreenPacket.Contact> contacts) {
    public FactionRequestScreenPacket {
        factionSlot = Math.max(0, factionSlot);
        factionName = safe(factionName); standing = safe(standing); type = safe(type); title = safe(title);
        description = safe(description); difficulty = safe(difficulty); reward = safe(reward); progress = safe(progress); note = safe(note);
        travelFactionName = safe(travelFactionName); instantTransmissionStatus = safe(instantTransmissionStatus);
        supplyItems = supplyItems == null ? List.of() : List.copyOf(supplyItems);
        contacts = contacts == null ? List.of() : List.copyOf(contacts);
        refreshSeconds = Math.max(0L, refreshSeconds);
    }

    /** R26 source-compatibility constructor for callers that do not supply the in-place travel/contact section. */
    public FactionRequestScreenPacket(int factionSlot, String factionName, String standing, int reputation,
                                      boolean activeForFaction, boolean activeElsewhere, boolean hasRequest,
                                      String type, String title, String description, String difficulty,
                                      String reward, String progress, long refreshSeconds,
                                      boolean canAccept, boolean canAbandon, boolean canDeliver, String note) {
        this(factionSlot, factionName, standing, reputation, activeForFaction, activeElsewhere, hasRequest,
                type, title, description, difficulty, reward, progress, refreshSeconds,
                canAccept, canAbandon, canDeliver, note, "", "Unavailable", List.of(), List.of());
    }

    public static FactionRequestScreenPacket from(FactionRequestManager.RequestView view, FactionRequestManager.TravelView travel) {
        List<FactionTravelScreenPacket.Contact> rows = travel == null ? List.of() : travel.contacts().stream()
                .map(c -> new FactionTravelScreenPacket.Contact(c.recordId(), c.name(), pretty(c.role().name()), c.rank().displayName(), c.relationship(), c.activity()))
                .toList();
        return new FactionRequestScreenPacket(view.factionSlot(), view.factionName(), view.standing(), view.reputation(),
                view.activeForFaction(), view.activeElsewhere(), view.hasRequest(), view.type(), view.title(), view.description(),
                view.difficulty(), view.reward(), view.progress(), view.refreshSeconds(), view.canAccept(), view.canAbandon(), view.canDeliver(), view.note(),
                travel == null ? "" : travel.factionName(), travel == null ? "Unavailable" : travel.instantTransmissionStatus(), view.supplyItems(), rows);
    }

    /** Compatibility helper for any older call site that does not need travel contacts. */
    public static FactionRequestScreenPacket from(FactionRequestManager.RequestView view) { return from(view, null); }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String pretty(String s) {
        if (s == null || s.isBlank()) return "Member";
        String lower = s.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return java.lang.Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public static void encode(FactionRequestScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.factionSlot);
        buf.writeUtf(msg.factionName, 256); buf.writeUtf(msg.standing, 96); buf.writeInt(msg.reputation);
        buf.writeBoolean(msg.activeForFaction); buf.writeBoolean(msg.activeElsewhere); buf.writeBoolean(msg.hasRequest);
        buf.writeUtf(msg.type, 48); buf.writeUtf(msg.title, 256); buf.writeUtf(msg.description, 1536);
        buf.writeUtf(msg.difficulty, 64); buf.writeUtf(msg.reward, 192); buf.writeUtf(msg.progress, 768);
        buf.writeVarLong(msg.refreshSeconds); buf.writeBoolean(msg.canAccept); buf.writeBoolean(msg.canAbandon); buf.writeBoolean(msg.canDeliver); buf.writeUtf(msg.note, 768);
        buf.writeUtf(msg.travelFactionName, 256); buf.writeUtf(msg.instantTransmissionStatus, 160);
        buf.writeVarInt(Math.min(8, msg.supplyItems.size()));
        for (int i = 0; i < Math.min(8, msg.supplyItems.size()); i++) SupplyItemSnapshot.encode(msg.supplyItems.get(i), buf);
        buf.writeVarInt(msg.contacts.size());
        for (FactionTravelScreenPacket.Contact c : msg.contacts) {
            buf.writeUUID(c.recordId()); buf.writeUtf(c.name(), 256); buf.writeUtf(c.role(), 96); buf.writeUtf(c.rank(), 96);
            buf.writeInt(c.relationship()); buf.writeUtf(c.activity(), 160);
        }
    }

    public static FactionRequestScreenPacket decode(FriendlyByteBuf buf) {
        int slot = buf.readVarInt(); String factionName = buf.readUtf(256); String standing = buf.readUtf(96); int rep = buf.readInt();
        boolean active = buf.readBoolean(), elsewhere = buf.readBoolean(), has = buf.readBoolean();
        String type = buf.readUtf(48), title = buf.readUtf(256), description = buf.readUtf(1536), difficulty = buf.readUtf(64), reward = buf.readUtf(192), progress = buf.readUtf(768);
        long refresh = buf.readVarLong(); boolean accept = buf.readBoolean(), abandon = buf.readBoolean(), deliver = buf.readBoolean(); String note = buf.readUtf(768);
        String travelName = buf.readUtf(256), status = buf.readUtf(160);
        int supplyCount = Math.max(0, Math.min(8, buf.readVarInt())); List<SupplyItemSnapshot> supplyItems = new ArrayList<>(supplyCount);
        for (int i = 0; i < supplyCount; i++) supplyItems.add(SupplyItemSnapshot.decode(buf));
        int count = Math.max(0, Math.min(64, buf.readVarInt())); List<FactionTravelScreenPacket.Contact> contacts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) contacts.add(new FactionTravelScreenPacket.Contact(buf.readUUID(), buf.readUtf(256), buf.readUtf(96), buf.readUtf(96), buf.readInt(), buf.readUtf(160)));
        return new FactionRequestScreenPacket(slot, factionName, standing, rep, active, elsewhere, has, type, title, description, difficulty, reward, progress,
                refresh, accept, abandon, deliver, note, travelName, status, supplyItems, contacts);
    }

    public static void handle(FactionRequestScreenPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FactionRequestScreen.open(msg)));
        ctx.setPacketHandled(true);
    }
}
