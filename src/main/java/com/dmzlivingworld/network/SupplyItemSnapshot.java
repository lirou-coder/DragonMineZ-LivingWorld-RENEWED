package com.dmzlivingworld.network;

import net.minecraft.network.FriendlyByteBuf;

/** Lightweight exact supply line used by client HUDs for local inventory counting. */
public record SupplyItemSnapshot(String itemId, String name, int need, int progress) {
    public SupplyItemSnapshot {
        itemId = itemId == null ? "" : itemId;
        name = name == null ? "" : name;
        need = Math.max(0, need);
        progress = Math.max(0, Math.min(need, progress));
    }

    public static void encode(SupplyItemSnapshot value, FriendlyByteBuf buf) {
        buf.writeUtf(value.itemId, 128);
        buf.writeUtf(value.name, 256);
        buf.writeVarInt(value.need);
        buf.writeVarInt(value.progress);
    }

    public static SupplyItemSnapshot decode(FriendlyByteBuf buf) {
        return new SupplyItemSnapshot(buf.readUtf(128), buf.readUtf(256), buf.readVarInt(), buf.readVarInt());
    }
}
