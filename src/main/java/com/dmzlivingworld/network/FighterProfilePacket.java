package com.dmzlivingworld.network;

import com.dmzlivingworld.client.screen.FighterProfileScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Structured, server-authoritative snapshot for the Shift+Right-click character panel. */
public record FighterProfilePacket(
        int entityId,
        UUID fighterId,
        String displayName,
        String legacyTitle,
        String faction,
        String factionRole,
        String race,
        String rank,
        String archetype,
        String alignment,
        String personality,
        String socialStyle,
        String connectsThrough,
        int dispositionId,
        String dispositionLabel,
        String attitudeReason,
        boolean relationshipKnown,
        int relationship,
        String relationshipStage,
        String nextRelationshipStage,
        int nextRelationshipThreshold,
        int encounters,
        int factionReputation,
        String factionReputationLabel,
        String currentGoal,
        String goalProgress,
        String fightingStyle,
        String techniques,
        String activeForm,
        long battlePower,
        boolean scouter,
        boolean rememberedSnapshot,
        boolean combatOnly,
        boolean travellingCompanion,
        boolean requestLocked,
        boolean supplyReceiver,
        String supplyRequestLine,
        CompoundTag appearanceSnapshot,
        List<EquipmentEntry> equipment,
        List<String> overviewLines,
        List<String> storyLines,
        List<String> combatLines,
        List<String> scienceLines,
        List<String> messageLines) {

    public record EquipmentEntry(String slot, ItemStack stack) {
        public EquipmentEntry {
            slot = slot == null ? "" : slot;
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
        }
    }

    public FighterProfilePacket {
        fighterId = fighterId == null ? new UUID(0L, 0L) : fighterId;
        displayName = safe(displayName);
        legacyTitle = safe(legacyTitle);
        faction = safe(faction);
        factionRole = safe(factionRole);
        race = safe(race);
        rank = safe(rank);
        archetype = safe(archetype);
        alignment = safe(alignment);
        personality = safe(personality);
        socialStyle = safe(socialStyle);
        connectsThrough = safe(connectsThrough);
        dispositionLabel = safe(dispositionLabel);
        attitudeReason = safe(attitudeReason);
        relationshipStage = safe(relationshipStage);
        nextRelationshipStage = safe(nextRelationshipStage);
        factionReputationLabel = safe(factionReputationLabel);
        currentGoal = safe(currentGoal);
        goalProgress = safe(goalProgress);
        fightingStyle = safe(fightingStyle);
        techniques = safe(techniques);
        activeForm = safe(activeForm);
        supplyRequestLine = safe(supplyRequestLine);
        appearanceSnapshot = appearanceSnapshot == null ? new CompoundTag() : appearanceSnapshot.copy();
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        overviewLines = overviewLines == null ? List.of() : List.copyOf(overviewLines);
        storyLines = storyLines == null ? List.of() : List.copyOf(storyLines);
        combatLines = combatLines == null ? List.of() : List.copyOf(combatLines);
        scienceLines = scienceLines == null ? List.of() : List.copyOf(scienceLines);
        messageLines = messageLines == null ? List.of() : List.copyOf(messageLines);
    }

    private static String safe(String value) { return value == null ? "" : value; }

    public static void encode(FighterProfilePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(Math.max(0, msg.entityId));
        buf.writeUUID(msg.fighterId);
        write(buf, msg.displayName, 256);
        write(buf, msg.legacyTitle, 128);
        write(buf, msg.faction, 192);
        write(buf, msg.factionRole, 128);
        write(buf, msg.race, 96);
        write(buf, msg.rank, 96);
        write(buf, msg.archetype, 96);
        write(buf, msg.alignment, 64);
        write(buf, msg.personality, 96);
        write(buf, msg.socialStyle, 96);
        write(buf, msg.connectsThrough, 256);
        buf.writeByte(msg.dispositionId);
        write(buf, msg.dispositionLabel, 96);
        write(buf, msg.attitudeReason, 384);
        buf.writeBoolean(msg.relationshipKnown);
        buf.writeByte(Math.max(-100, Math.min(100, msg.relationship)));
        write(buf, msg.relationshipStage, 96);
        write(buf, msg.nextRelationshipStage, 96);
        buf.writeByte(Math.max(-100, Math.min(100, msg.nextRelationshipThreshold)));
        buf.writeVarInt(Math.max(0, msg.encounters));
        buf.writeByte(Math.max(-100, Math.min(100, msg.factionReputation)));
        write(buf, msg.factionReputationLabel, 96);
        write(buf, msg.currentGoal, 384);
        write(buf, msg.goalProgress, 192);
        write(buf, msg.fightingStyle, 192);
        write(buf, msg.techniques, 768);
        write(buf, msg.activeForm, 192);
        buf.writeVarLong(Math.max(1L, msg.battlePower));
        buf.writeBoolean(msg.scouter);
        buf.writeBoolean(msg.rememberedSnapshot);
        buf.writeBoolean(msg.combatOnly);
        buf.writeBoolean(msg.travellingCompanion);
        buf.writeBoolean(msg.requestLocked);
        buf.writeBoolean(msg.supplyReceiver);
        write(buf, msg.supplyRequestLine, 768);
        buf.writeNbt(msg.appearanceSnapshot);

        int gearSize = Math.min(8, msg.equipment.size());
        buf.writeVarInt(gearSize);
        for (int i = 0; i < gearSize; i++) {
            EquipmentEntry entry = msg.equipment.get(i);
            write(buf, entry.slot, 64);
            buf.writeItem(entry.stack);
        }

        writeLines(buf, msg.overviewLines);
        writeLines(buf, msg.storyLines);
        writeLines(buf, msg.combatLines);
        writeLines(buf, msg.scienceLines);
        writeLines(buf, msg.messageLines);
    }

    public static FighterProfilePacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        UUID fighterId = buf.readUUID();
        String displayName = buf.readUtf(256);
        String legacyTitle = buf.readUtf(128);
        String faction = buf.readUtf(192);
        String factionRole = buf.readUtf(128);
        String race = buf.readUtf(96);
        String rank = buf.readUtf(96);
        String archetype = buf.readUtf(96);
        String alignment = buf.readUtf(64);
        String personality = buf.readUtf(96);
        String socialStyle = buf.readUtf(96);
        String connectsThrough = buf.readUtf(256);
        int dispositionId = buf.readByte();
        String dispositionLabel = buf.readUtf(96);
        String attitudeReason = buf.readUtf(384);
        boolean relationshipKnown = buf.readBoolean();
        int relationship = buf.readByte();
        String relationshipStage = buf.readUtf(96);
        String nextRelationshipStage = buf.readUtf(96);
        int nextRelationshipThreshold = buf.readByte();
        int encounters = buf.readVarInt();
        int factionReputation = buf.readByte();
        String factionReputationLabel = buf.readUtf(96);
        String currentGoal = buf.readUtf(384);
        String goalProgress = buf.readUtf(192);
        String fightingStyle = buf.readUtf(192);
        String techniques = buf.readUtf(768);
        String activeForm = buf.readUtf(192);
        long battlePower = buf.readVarLong();
        boolean scouter = buf.readBoolean();
        boolean rememberedSnapshot = buf.readBoolean();
        boolean combatOnly = buf.readBoolean();
        boolean travellingCompanion = buf.readBoolean();
        boolean requestLocked = buf.readBoolean();
        boolean supplyReceiver = buf.readBoolean();
        String supplyRequestLine = buf.readUtf(768);
        CompoundTag appearanceSnapshot = buf.readNbt();
        if (appearanceSnapshot == null) appearanceSnapshot = new CompoundTag();

        int gearSize = Math.min(8, Math.max(0, buf.readVarInt()));
        List<EquipmentEntry> equipment = new ArrayList<>(gearSize);
        for (int i = 0; i < gearSize; i++) equipment.add(new EquipmentEntry(buf.readUtf(64), buf.readItem()));

        return new FighterProfilePacket(entityId, fighterId, displayName, legacyTitle, faction, factionRole,
                race, rank, archetype, alignment, personality, socialStyle, connectsThrough,
                dispositionId, dispositionLabel, attitudeReason, relationshipKnown, relationship,
                relationshipStage, nextRelationshipStage, nextRelationshipThreshold, encounters,
                factionReputation, factionReputationLabel, currentGoal, goalProgress, fightingStyle,
                techniques, activeForm, battlePower, scouter, rememberedSnapshot, combatOnly, travellingCompanion, requestLocked, supplyReceiver, supplyRequestLine, appearanceSnapshot, equipment,
                readLines(buf), readLines(buf), readLines(buf), readLines(buf), readLines(buf));
    }

    private static void write(FriendlyByteBuf buf, String value, int max) {
        String safe = value == null ? "" : value;
        if (safe.length() > max) safe = safe.substring(0, max);
        buf.writeUtf(safe, max);
    }

    private static void writeLines(FriendlyByteBuf buf, List<String> lines) {
        int size = Math.min(120, lines == null ? 0 : lines.size());
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) write(buf, lines.get(i), 1024);
    }

    private static List<String> readLines(FriendlyByteBuf buf) {
        int size = Math.min(120, Math.max(0, buf.readVarInt()));
        List<String> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) out.add(buf.readUtf(1024));
        return out;
    }

    public static void handle(FighterProfilePacket msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> FighterProfileScreen.open(msg)));
        ctx.setPacketHandled(true);
    }
}
