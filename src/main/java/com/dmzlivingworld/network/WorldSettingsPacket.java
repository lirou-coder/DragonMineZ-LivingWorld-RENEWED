package com.dmzlivingworld.network;

import com.dmzlivingworld.client.screen.WorldSettingsScreen;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.kunyo.dbzmeditation.MeditationConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-authoritative snapshot used by the integrated Living World Settings screen. */
public record WorldSettingsPacket(LivingWorldConfig.Snapshot world,
                                  MeditationConfig.ServerSnapshot meditation,
                                  boolean canEdit) {
    public static WorldSettingsPacket current(boolean canEdit) {
        return new WorldSettingsPacket(LivingWorldConfig.snapshot(), MeditationConfig.serverSnapshot(), canEdit);
    }

    public static void encode(WorldSettingsPacket msg, FriendlyByteBuf buf) {
        writeWorld(buf, msg.world);
        writeMeditation(buf, msg.meditation);
        buf.writeBoolean(msg.canEdit);
    }

    public static WorldSettingsPacket decode(FriendlyByteBuf buf) {
        return new WorldSettingsPacket(readWorld(buf), readMeditation(buf), buf.readBoolean());
    }

    public static void handle(WorldSettingsPacket msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WorldSettingsScreen.open(msg)));
        ctx.setPacketHandled(true);
    }

    static void writeWorld(FriendlyByteBuf b, LivingWorldConfig.Snapshot v) {
        b.writeVarInt(v.activityPreset()); b.writeVarInt(v.nearbyFighterCap()); b.writeVarInt(v.nearbyHostileCap());
        b.writeBoolean(v.factionEncounters()); b.writeBoolean(v.dynamicEncounters()); b.writeBoolean(v.recurringFighters());
        b.writeVarInt(v.livingPresenceTargetBase()); b.writeVarInt(v.livingPresenceRadius()); b.writeVarInt(v.factionResidentCap());
        b.writeBoolean(v.automaticPowerSensing()); b.writeBoolean(v.worldIncidents()); b.writeBoolean(v.worldEventAlerts());
        b.writeVarInt(v.worldEventAlertRadius()); b.writeBoolean(v.socialTalk()); b.writeVarInt(v.talkBaseGain());
        b.writeVarInt(v.talkRelationshipCap()); b.writeVarInt(v.talkCooldownMinSeconds()); b.writeVarInt(v.talkCooldownMaxSeconds());
        b.writeBoolean(v.npcSocializing()); b.writeVarInt(v.npcChaosPercent()); b.writeBoolean(v.companionSagaHelp()); b.writeVarInt(v.npcKiMode()); b.writeVarInt(v.npcStrengthPercent()); b.writeVarInt(v.npcGrowthPercent()); b.writeBoolean(v.attackMinecraftMobs()); b.writeVarInt(v.npcChatFrequencyPercent()); b.writeVarInt(v.earthGuardianResponsePercent());
        b.writeVarInt(v.maxRememberedDeadFighters()); b.writeVarInt(v.npcDespawnProtectionRadius());
        b.writeDouble(v.levelMultiplierPerSaga()); b.writeDouble(v.maxDefenseMitigation()); b.writeDouble(v.bpVisualMultiplier());
        b.writeBoolean(v.canMeditationProcSkillProgression()); writeStrings(b, v.npcRaceBlacklist());
        b.writeBoolean(v.treatRaceBlacklistAsWhitelist()); writeStrings(b, v.canUseClothes()); writeStrings(b, v.dimensionWhitelist());
        b.writeBoolean(v.treatDimensionWhitelistAsBlacklist()); writeStrings(b, v.companionDimensionBlacklist()); b.writeVarInt(v.archetypeShares().size());
        for (double share : v.archetypeShares()) b.writeDouble(share);
    }

    static LivingWorldConfig.Snapshot readWorld(FriendlyByteBuf b) {
        return new LivingWorldConfig.Snapshot(b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readBoolean(), b.readVarInt(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readBoolean(), readStrings(b), b.readBoolean(), readStrings(b), readStrings(b), b.readBoolean(), readStrings(b), readDoubles(b));
    }

    private static void writeStrings(FriendlyByteBuf b, java.util.List<String> values) {
        b.writeVarInt(values.size()); for (String value : values) b.writeUtf(value, 256);
    }
    private static java.util.List<String> readStrings(FriendlyByteBuf b) {
        int size = Math.min(256, b.readVarInt()); java.util.List<String> result = new java.util.ArrayList<>(size);
        for (int i=0;i<size;i++) result.add(b.readUtf(256)); return result;
    }
    private static java.util.List<Double> readDoubles(FriendlyByteBuf b) {
        int size = Math.min(64, b.readVarInt()); java.util.List<Double> result = new java.util.ArrayList<>(size);
        for (int i=0;i<size;i++) result.add(b.readDouble()); return result;
    }

    static void writeMeditation(FriendlyByteBuf b, MeditationConfig.ServerSnapshot v) {
        b.writeBoolean(v.enabled()); b.writeBoolean(v.tpRewardsEnabled()); b.writeVarInt(v.tpRewardScalePercent());
        b.writeVarInt(v.rewardIntervalSeconds()); b.writeVarInt(v.focusedSeconds()); b.writeVarInt(v.centeredSeconds());
        b.writeVarInt(v.deepSeconds()); b.writeVarInt(v.transcendentSeconds());
        b.writeVarInt(v.calmMultiplier()); b.writeVarInt(v.focusedMultiplier()); b.writeVarInt(v.centeredMultiplier());
        b.writeVarInt(v.deepMultiplier()); b.writeVarInt(v.transcendentMultiplier());
        b.writeBoolean(v.damageInterrupts()); b.writeVarInt(v.damageCooldownSeconds());
        b.writeBoolean(v.groupMeditation()); b.writeVarInt(v.groupMeditationRadius());
        b.writeBoolean(v.livingWorldNpcMeditation()); b.writeBoolean(v.statBreakthroughEnabled());
        b.writeDouble(v.statBreakthroughChancePercent()); b.writeVarInt(v.statBreakthroughRollSeconds());
        b.writeVarInt(v.statBreakthroughPoints()); b.writeBoolean(v.formMasteryEnabled());
        b.writeDouble(v.deepFormMasteryPerMinute()); b.writeDouble(v.transcendentFormMasteryPerMinute());
        b.writeDouble(v.levitationHeight()); b.writeBoolean(v.particles());
        b.writeVarInt(v.particleDensityPercent()); b.writeBoolean(v.milestoneSounds());
    }

    static MeditationConfig.ServerSnapshot readMeditation(FriendlyByteBuf b) {
        return new MeditationConfig.ServerSnapshot(b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readBoolean(), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readBoolean(),
                b.readBoolean(), b.readDouble(), b.readVarInt(), b.readVarInt(), b.readBoolean(),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readBoolean(), b.readVarInt(), b.readBoolean());
    }
}
