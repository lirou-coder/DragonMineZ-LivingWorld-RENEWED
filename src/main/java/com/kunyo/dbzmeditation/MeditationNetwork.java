package com.kunyo.dbzmeditation;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Small integrated meditation presentation channel owned by Living World. */
public final class MeditationNetwork {
    private static final String PROTOCOL = "8";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DBZMeditation.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int nextId;
    private static boolean registered;

    private MeditationNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(nextId++, MeditationStatePacket.class,
                MeditationStatePacket::encode, MeditationStatePacket::decode, MeditationStatePacket::handle);
        CHANNEL.registerMessage(nextId++, MeditationSummaryPacket.class,
                MeditationSummaryPacket::encode, MeditationSummaryPacket::decode, MeditationSummaryPacket::handle);
    }

    public static void sendState(ServerPlayer player,
                                 boolean active,
                                 int ticks,
                                 int sessionTp,
                                 int groupCount,
                                 String sessionStatGains,
                                 int stage,
                                 int multiplier,
                                 float stageProgress,
                                 boolean fastTesting,
                                 boolean debugView,
                                 float energyPercent,
                                 float staminaPercent,
                                 String activeForm,
                                 double formMastery,
                                 double formMasteryMax,
                                 double sessionMasteryGain) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MeditationStatePacket(active, ticks, sessionTp, groupCount, sessionStatGains,
                        stage, multiplier, stageProgress, fastTesting, debugView,
                        energyPercent, staminaPercent, activeForm, formMastery,
                        formMasteryMax, sessionMasteryGain));
    }

    public static void sendSummary(ServerPlayer player,
                                   int durationTicks,
                                   int totalTp,
                                   String stageName,
                                   String statGains,
                                   String masteryForm,
                                   double masteryGain,
                                   boolean newRecord,
                                   boolean interrupted) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MeditationSummaryPacket(durationTicks, totalTp, stageName, statGains,
                        masteryForm, masteryGain, newRecord, interrupted));
    }

    public record MeditationStatePacket(boolean active,
                                        int ticks,
                                        int sessionTp,
                                        int groupCount,
                                        String sessionStatGains,
                                        int stage,
                                        int multiplier,
                                        float stageProgress,
                                        boolean fastTesting,
                                        boolean debugView,
                                        float energyPercent,
                                        float staminaPercent,
                                        String activeForm,
                                        double formMastery,
                                        double formMasteryMax,
                                        double sessionMasteryGain) {
        public static void encode(MeditationStatePacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.active);
            buf.writeVarInt(msg.ticks);
            buf.writeVarInt(msg.sessionTp);
            buf.writeVarInt(msg.groupCount);
            buf.writeUtf(msg.sessionStatGains, 96);
            buf.writeVarInt(msg.stage);
            buf.writeVarInt(msg.multiplier);
            buf.writeFloat(msg.stageProgress);
            buf.writeBoolean(msg.fastTesting);
            buf.writeBoolean(msg.debugView);
            buf.writeFloat(msg.energyPercent);
            buf.writeFloat(msg.staminaPercent);
            buf.writeUtf(msg.activeForm, 96);
            buf.writeDouble(msg.formMastery);
            buf.writeDouble(msg.formMasteryMax);
            buf.writeDouble(msg.sessionMasteryGain);
        }

        public static MeditationStatePacket decode(FriendlyByteBuf buf) {
            return new MeditationStatePacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readUtf(96), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                    buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readUtf(96),
                    buf.readDouble(), buf.readDouble(), buf.readDouble());
        }

        public static void handle(MeditationStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientMeditation.handleStatePacket(msg)));
            ctx.setPacketHandled(true);
        }
    }

    public record MeditationSummaryPacket(int durationTicks,
                                          int totalTp,
                                          String stageName,
                                          String statGains,
                                          String masteryForm,
                                          double masteryGain,
                                          boolean newRecord,
                                          boolean interrupted) {
        public static void encode(MeditationSummaryPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.durationTicks);
            buf.writeVarInt(msg.totalTp);
            buf.writeUtf(msg.stageName, 48);
            buf.writeUtf(msg.statGains, 96);
            buf.writeUtf(msg.masteryForm, 96);
            buf.writeDouble(msg.masteryGain);
            buf.writeBoolean(msg.newRecord);
            buf.writeBoolean(msg.interrupted);
        }

        public static MeditationSummaryPacket decode(FriendlyByteBuf buf) {
            return new MeditationSummaryPacket(buf.readVarInt(), buf.readVarInt(), buf.readUtf(48),
                    buf.readUtf(96), buf.readUtf(96), buf.readDouble(), buf.readBoolean(), buf.readBoolean());
        }

        public static void handle(MeditationSummaryPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientMeditation.handleSummaryPacket(msg)));
            ctx.setPacketHandled(true);
        }
    }
}
