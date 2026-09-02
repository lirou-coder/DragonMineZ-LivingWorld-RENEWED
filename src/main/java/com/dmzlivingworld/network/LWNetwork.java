package com.dmzlivingworld.network;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.kunyo.dbzmeditation.MeditationConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Tiny S2C channel used only for presentation snapshots. Simulation remains server authoritative. */
public final class LWNetwork {
    private static final String PROTOCOL = "38.4";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LivingWorldMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int nextId;
    private static boolean registered;

    private LWNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(nextId++, FactionDossierPacket.class,
                FactionDossierPacket::encode,
                FactionDossierPacket::decode,
                FactionDossierPacket::handle);
        CHANNEL.registerMessage(nextId++, LivingWorldMenuRequestPacket.class,
                LivingWorldMenuRequestPacket::encode,
                LivingWorldMenuRequestPacket::decode,
                LivingWorldMenuRequestPacket::handle);
        CHANNEL.registerMessage(nextId++, FactionRequestActionPacket.class,
                FactionRequestActionPacket::encode,
                FactionRequestActionPacket::decode,
                FactionRequestActionPacket::handle);
        CHANNEL.registerMessage(nextId++, FactionRequestScreenPacket.class,
                FactionRequestScreenPacket::encode,
                FactionRequestScreenPacket::decode,
                FactionRequestScreenPacket::handle);
        CHANNEL.registerMessage(nextId++, FactionTravelScreenPacket.class,
                FactionTravelScreenPacket::encode,
                FactionTravelScreenPacket::decode,
                FactionTravelScreenPacket::handle);
        CHANNEL.registerMessage(nextId++, FactionRequestTrackerPacket.class,
                FactionRequestTrackerPacket::encode,
                FactionRequestTrackerPacket::decode,
                FactionRequestTrackerPacket::handle);
        CHANNEL.registerMessage(nextId++, FactionRequestCompletePacket.class,
                FactionRequestCompletePacket::encode,
                FactionRequestCompletePacket::decode,
                FactionRequestCompletePacket::handle);
        CHANNEL.registerMessage(nextId++, FighterActionPacket.class,
                FighterActionPacket::encode,
                FighterActionPacket::decode,
                FighterActionPacket::handle);
        CHANNEL.registerMessage(nextId++, WorldSettingsPacket.class,
                WorldSettingsPacket::encode,
                WorldSettingsPacket::decode,
                WorldSettingsPacket::handle);
        CHANNEL.registerMessage(nextId++, WorldSettingsUpdatePacket.class,
                WorldSettingsUpdatePacket::encode,
                WorldSettingsUpdatePacket::decode,
                WorldSettingsUpdatePacket::handle);
        CHANNEL.registerMessage(nextId++, FighterProfilePacket.class,
                FighterProfilePacket::encode,
                FighterProfilePacket::decode,
                FighterProfilePacket::handle);
        CHANNEL.registerMessage(nextId++, RememberedFighterRequestPacket.class,
                RememberedFighterRequestPacket::encode,
                RememberedFighterRequestPacket::decode,
                RememberedFighterRequestPacket::handle);
        CHANNEL.registerMessage(nextId++, LiveFighterProfileRequestPacket.class,
                LiveFighterProfileRequestPacket::encode,
                LiveFighterProfileRequestPacket::decode,
                LiveFighterProfileRequestPacket::handle);
        CHANNEL.registerMessage(nextId++, RememberedFighterTeleportPacket.class,
                RememberedFighterTeleportPacket::encode,
                RememberedFighterTeleportPacket::decode,
                RememberedFighterTeleportPacket::handle);
        CHANNEL.registerMessage(nextId++, PeopleMemoryActionPacket.class,
                PeopleMemoryActionPacket::encode,
                PeopleMemoryActionPacket::decode,
                PeopleMemoryActionPacket::handle);
        CHANNEL.registerMessage(nextId++, FighterDispositionSnapshotPacket.class,
                FighterDispositionSnapshotPacket::encode,
                FighterDispositionSnapshotPacket::decode,
                FighterDispositionSnapshotPacket::handle);
        CHANNEL.registerMessage(nextId++, FighterInteractPacket.class,
                FighterInteractPacket::encode,
                FighterInteractPacket::decode,
                FighterInteractPacket::handle);
    }

    public static void sendFactionDossier(ServerPlayer player, FactionDossierPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendFactionRequestScreen(ServerPlayer player, FactionRequestScreenPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendFactionTravelScreen(ServerPlayer player, FactionTravelScreenPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendFactionRequestTracker(ServerPlayer player, FactionRequestTrackerPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendFactionRequestComplete(ServerPlayer player, FactionRequestCompletePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void requestMenu(String page, int slot) {
        CHANNEL.sendToServer(new LivingWorldMenuRequestPacket(page, slot));
    }

    public static void requestFactionAction(String action, int slot) {
        CHANNEL.sendToServer(new FactionRequestActionPacket(action, slot));
    }

    public static void requestFighterAction(String action, java.util.UUID fighterId) {
        if (fighterId != null) CHANNEL.sendToServer(new FighterActionPacket(action, fighterId));
    }

    public static void interactWithFighter(int entityId, net.minecraft.world.InteractionHand hand) {
        CHANNEL.sendToServer(new FighterInteractPacket(entityId, hand));
    }

    public static void requestRememberedFighter(java.util.UUID recordId) {
        if (recordId != null) CHANNEL.sendToServer(new RememberedFighterRequestPacket(recordId));
    }

    public static void requestLiveFighter(java.util.UUID fighterId) {
        if (fighterId != null) CHANNEL.sendToServer(new LiveFighterProfileRequestPacket(fighterId));
    }

    public static void instantTransmitToRemembered(java.util.UUID recordId) {
        if (recordId != null) CHANNEL.sendToServer(new RememberedFighterTeleportPacket(recordId));
    }

    public static void peopleMemoryAction(String action, java.util.UUID recordId) {
        CHANNEL.sendToServer(new PeopleMemoryActionPacket(action, recordId));
    }

    public static void sendFighterProfile(ServerPlayer player, FighterProfilePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendDispositionSnapshot(ServerPlayer player, FighterDispositionSnapshotPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendWorldSettings(ServerPlayer player, boolean canEdit) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                WorldSettingsPacket.current(canEdit));
    }

    public static void updateWorldSettings(LivingWorldConfig.Snapshot world,
                                           MeditationConfig.ServerSnapshot meditation) {
        CHANNEL.sendToServer(new WorldSettingsUpdatePacket(world, meditation));
    }
}
