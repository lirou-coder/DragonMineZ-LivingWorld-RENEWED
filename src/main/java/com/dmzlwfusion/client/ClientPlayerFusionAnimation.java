package com.dmzlwfusion.client;

import com.dmzlwfusion.network.PlayerDanceS2C;
import com.dragonminez.client.animation.IPlayerAnimatable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;

/**
 * Uses DragonMineZ 2.1.3's existing player animation controller rather than
 * pretending vanilla AbstractClientPlayer owns a GeckoLib controller itself.
 * DMZ's PlayerGeoAnimatableMixin supplies IPlayerAnimatable + GeoAnimatable at
 * runtime and its ki_controller can play any animation from the player library.
 */
public final class ClientPlayerFusionAnimation {
    private static final String LEFT = "base.fusion_dance_left";
    private static final String RIGHT = "base.fusion_dance_right";
    private static final String DMZ_KI_CONTROLLER = "ki_controller";
    private static final double NATIVE_FUSION_SPEED = 0.583D;

    private ClientPlayerFusionAnimation() {}

    public static void handle(int entityId, byte action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Entity entity = minecraft.level.getEntity(entityId);
        if (!(entity instanceof AbstractClientPlayer player)) return;
        if (!(player instanceof IPlayerAnimatable animatable)) return;

        if (action == PlayerDanceS2C.STOP) {
            animatable.dragonminez$stopKiAnimation();
            setControllerSpeed(player, 1.0D, true);
            return;
        }

        String animation = action == PlayerDanceS2C.LEFT ? LEFT : RIGHT;
        // DMZ's boolean is the controller's hold gate; false is the path used
        // for a continuously-playing named animation until stopKiAnimation().
        animatable.dragonminez$playKiAnimation(animation, false);
        setControllerSpeed(player, NATIVE_FUSION_SPEED, false);
    }

    private static void setControllerSpeed(AbstractClientPlayer player, double speed, boolean stop) {
        if (!(player instanceof GeoAnimatable geo)) return;

        AnimatableManager<?> manager = geo.getAnimatableInstanceCache().getManagerForId(player.getId());
        AnimationController<?> controller = manager.getAnimationControllers().get(DMZ_KI_CONTROLLER);
        if (controller == null) return;

        controller.setAnimationSpeed(speed);
        if (stop) {
            controller.stop();
            controller.forceAnimationReset();
        }
    }
}
