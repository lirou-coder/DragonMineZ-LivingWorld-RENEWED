package com.dmzlivingworld.network;

import com.dmzlivingworld.client.screen.FactionDossierScreen;
import com.dmzlivingworld.client.screen.LivingWorldGuideScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.UUID;

/** Read-only world/faction snapshot with an optional server-validated fighter action target. */
public record FactionDossierPacket(String page, int slot, String title, String subtitle, List<String> lines, String actionTarget, List<Portrait> portraits) {
    public record Portrait(UUID recordId, CompoundTag appearance) {
        public Portrait {
            appearance = appearance == null ? new CompoundTag() : appearance.copy();
        }
    }
    public FactionDossierPacket {
        page = page == null ? "world" : page;
        slot = Math.max(0, slot);
        title = title == null ? "Living World" : title;
        subtitle = subtitle == null ? "" : subtitle;
        lines = lines == null ? List.of() : List.copyOf(lines);
        actionTarget = actionTarget == null ? "" : actionTarget;
        portraits = portraits == null ? List.of() : List.copyOf(portraits);
    }

    public FactionDossierPacket(String page, int slot, String title, String subtitle, List<String> lines, String actionTarget) {
        this(page, slot, title, subtitle, lines, actionTarget, List.of());
    }

    public FactionDossierPacket(String page, int slot, String title, String subtitle, List<String> lines) {
        this(page, slot, title, subtitle, lines, "", List.of());
    }

    /** Compatibility constructor for existing debug/profile callers. */
    public FactionDossierPacket(String title, String subtitle, List<String> lines) {
        this("world", 0, title, subtitle, lines, "", List.of());
    }

    public static void encode(FactionDossierPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.page, 32);
        buf.writeVarInt(msg.slot);
        buf.writeUtf(msg.title, 256);
        buf.writeUtf(msg.subtitle, 512);
        int size = Math.min(180, msg.lines.size());
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) buf.writeUtf(msg.lines.get(i), 1024);
        buf.writeUtf(msg.actionTarget, 64);
        int portraits = Math.min(32, msg.portraits.size());
        buf.writeVarInt(portraits);
        for (int i = 0; i < portraits; i++) {
            Portrait portrait = msg.portraits.get(i);
            buf.writeUUID(portrait.recordId());
            buf.writeNbt(portrait.appearance());
        }
    }

    public static FactionDossierPacket decode(FriendlyByteBuf buf) {
        String page = buf.readUtf(32);
        int slot = buf.readVarInt();
        String title = buf.readUtf(256);
        String subtitle = buf.readUtf(512);
        int size = Math.min(180, Math.max(0, buf.readVarInt()));
        List<String> lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) lines.add(buf.readUtf(1024));
        String actionTarget = buf.readUtf(64);
        int portraitCount = Math.min(32, Math.max(0, buf.readVarInt()));
        List<Portrait> portraits = new ArrayList<>(portraitCount);
        for (int i = 0; i < portraitCount; i++) {
            UUID recordId = buf.readUUID();
            CompoundTag appearance = buf.readNbt();
            portraits.add(new Portrait(recordId, appearance == null ? new CompoundTag() : appearance));
        }
        return new FactionDossierPacket(page, slot, title, subtitle, lines, actionTarget, portraits);
    }

    public static void handle(FactionDossierPacket msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    if ("guide".equals(msg.page())) LivingWorldGuideScreen.open();
                    else FactionDossierScreen.open(msg);
                }));
        ctx.setPacketHandled(true);
    }
}
