package com.dmzlwfusion.mixin;

import com.dmzlwfusion.FusionAnimations;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/** Adds a trigger-only fusion controller to DMZ saga entities. */
@Mixin(value = DBSagasEntity.class, remap = false)
public abstract class SagaFusionAnimationMixin {
    private static final RawAnimation LWFUSION_LEFT = RawAnimation.begin().thenPlay("base.fusion_dance_left");
    private static final RawAnimation LWFUSION_RIGHT = RawAnimation.begin().thenPlay("base.fusion_dance_right");

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "registerControllers", at = @At("RETURN"))
    private void dmzlwfusion$registerFusionDanceController(AnimatableManager.ControllerRegistrar controllers, CallbackInfo ci) {
        AnimationController controller = new AnimationController(
                (software.bernie.geckolib.core.animatable.GeoAnimatable) (Object) this,
                FusionAnimations.CONTROLLER,
                0,
                state -> PlayState.STOP
        )
                .triggerableAnim(FusionAnimations.LEFT, LWFUSION_LEFT)
                .triggerableAnim(FusionAnimations.RIGHT, LWFUSION_RIGHT)
                .setAnimationSpeed(0.583D);
        controllers.add(controller);
    }
}
