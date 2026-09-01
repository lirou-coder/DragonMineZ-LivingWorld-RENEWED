package com.dmzlwfusion;

import com.dmzlwfusion.network.FusionAnimationNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;
import software.bernie.geckolib.network.GeckoLibNetwork;
import software.bernie.geckolib.network.packet.EntityAnimTriggerPacket;
import software.bernie.geckolib.network.packet.StopTriggeredEntityAnimPacket;

/**
 * Small GeckoLib transport shim for DMZ's own fusion-dance animation names.
 * No animation data is invented here; the bridge only asks the already-rendered
 * player/LW models to play base.fusion_dance_left/right.
 */
public final class FusionAnimations {
    public static final String CONTROLLER = "lwfusion_controller";
    public static final String LEFT = "left";
    public static final String RIGHT = "right";
    public static final int DANCE_TICKS = 180; // 5.25s native clip at DMZ's 0.583 speed ~= 9.0s.

    private FusionAnimations() {}

    public static void trigger(Entity entity, boolean left) {
        if (entity == null || entity.level().isClientSide()) return;
        if (entity instanceof ServerPlayer) {
            FusionAnimationNetwork.startPlayerDance(entity, left);
            return;
        }
        GeckoLibNetwork.send(
                new EntityAnimTriggerPacket<>(entity.getId(), CONTROLLER, left ? LEFT : RIGHT),
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity)
        );
    }

    public static void stop(Entity entity) {
        if (entity == null || entity.level().isClientSide()) return;
        if (entity instanceof ServerPlayer) {
            FusionAnimationNetwork.stopPlayerDance(entity);
            return;
        }
        GeckoLibNetwork.send(
                new StopTriggeredEntityAnimPacket(entity.getId(), CONTROLLER, LEFT),
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity)
        );
        GeckoLibNetwork.send(
                new StopTriggeredEntityAnimPacket(entity.getId(), CONTROLLER, RIGHT),
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity)
        );
    }
}
