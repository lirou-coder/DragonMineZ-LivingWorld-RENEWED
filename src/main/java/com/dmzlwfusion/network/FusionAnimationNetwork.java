package com.dmzlwfusion.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class FusionAnimationNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(com.dmzlivingworld.LivingWorldMod.MOD_ID, "fusion_animation"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int nextId;
    private static boolean registered;

    private FusionAnimationNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(nextId++, PlayerDanceS2C.class,
                PlayerDanceS2C::encode,
                PlayerDanceS2C::decode,
                PlayerDanceS2C::handle);
    }

    public static void startPlayerDance(Entity player, boolean left) {
        if (player == null || player.level().isClientSide()) return;
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                PlayerDanceS2C.start(player.getId(), left));
    }

    public static void stopPlayerDance(Entity player) {
        if (player == null || player.level().isClientSide()) return;
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                PlayerDanceS2C.stop(player.getId()));
    }
}
