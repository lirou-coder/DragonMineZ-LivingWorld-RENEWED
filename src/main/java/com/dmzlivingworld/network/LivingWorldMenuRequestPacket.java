package com.dmzlivingworld.network;

import com.dmzlivingworld.command.LivingWorldCommands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.function.Supplier;

/** C2S read-only navigation request for the Living World profile. */
public record LivingWorldMenuRequestPacket(String page, int slot) {
    public LivingWorldMenuRequestPacket {
        page = page == null ? "world" : page.toLowerCase(Locale.ROOT);
        slot = Math.max(0, slot);
    }

    public static void encode(LivingWorldMenuRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.page, 32);
        buf.writeVarInt(msg.slot);
    }

    public static LivingWorldMenuRequestPacket decode(FriendlyByteBuf buf) {
        return new LivingWorldMenuRequestPacket(buf.readUtf(32), buf.readVarInt());
    }

    public static void handle(LivingWorldMenuRequestPacket msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer sender = ctx.getSender();
        if (sender != null) {
            ctx.enqueueWork(() -> {
                switch (msg.page) {
                    case "factions" -> LivingWorldCommands.openPlayerFactionList(sender);
                    case "faction_active" -> LivingWorldCommands.openPlayerActiveFactionQuest(sender);
                    case "faction" -> LivingWorldCommands.openPlayerFaction(sender, msg.slot);
                    case "faction_roster" -> LivingWorldCommands.openPlayerFactionRoster(sender, msg.slot);
                    case "faction_requests" -> LivingWorldCommands.openPlayerFactionRequests(sender, msg.slot);
                    case "faction_travel" -> LivingWorldCommands.openPlayerFactionTravel(sender, msg.slot);
                    case "people" -> LivingWorldCommands.openPeople(sender);
                    case "travel" -> LivingWorldCommands.openTravel(sender);
                    case "wanted" -> LivingWorldCommands.openWanted(sender);
                    case "wanted_profile" -> LivingWorldCommands.openWantedProfile(sender, msg.slot);
                    case "fallen_profile" -> LivingWorldCommands.openFallenProfile(sender, msg.slot);
                    case "antagonists" -> LivingWorldCommands.openAntagonists(sender);
                    case "menace" -> LivingWorldCommands.openWorldMenace(sender);
                    case "menace_profile" -> LivingWorldCommands.openWorldMenaceProfile(sender, msg.slot);
                    case "menace_tp" -> LivingWorldCommands.worldMenaceTeleportFromMenu(sender);
                    case "menace_spawn" -> LivingWorldCommands.worldMenaceSpawnFromMenu(sender);
                    case "meditation" -> LivingWorldCommands.openMeditationInfo(sender);
                    case "guide" -> LivingWorldCommands.openGuide(sender);
                    case "settings" -> LivingWorldCommands.openSettings(sender);
                    default -> LivingWorldCommands.openWorldOverview(sender);
                }
            });
        }
        ctx.setPacketHandled(true);
    }
}
