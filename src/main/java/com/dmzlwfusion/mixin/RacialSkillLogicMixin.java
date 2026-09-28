package com.dmzlwfusion.mixin;

import com.dmzlivingworld.world.NamekAssimilationCompat;
import com.dragonminez.common.racial.RacialContext;
import com.dragonminez.common.racial.impl.NamekAssimilation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DMZ 2.2 moved racial actions from RacialSkillLogic into per-race ability classes. */
@Mixin(NamekAssimilation.class)
public abstract class RacialSkillLogicMixin {
    @Inject(method = "onActivate", at = @At("HEAD"), cancellable = true)
    private void dmzlivingworld$namekFighter(RacialContext context, CallbackInfoReturnable<Boolean> cir) {
        if (NamekAssimilationCompat.tryDmz(context.player())) cir.setReturnValue(true);
    }
}
