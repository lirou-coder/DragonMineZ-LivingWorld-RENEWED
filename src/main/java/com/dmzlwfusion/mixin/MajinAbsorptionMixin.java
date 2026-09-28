package com.dmzlwfusion.mixin;

import com.dmzlivingworld.world.MajinAbsorptionFriendlyFistBypass;
import com.dragonminez.common.racial.RacialContext;
import com.dragonminez.common.racial.impl.MajinAbsorption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Friendly Fist from preserving a target after DMZ 2.2 Majin absorption. */
@Mixin(MajinAbsorption.class)
public abstract class MajinAbsorptionMixin {
    @Inject(method = "onActivate", at = @At("HEAD"))
    private void dmzlivingworld$beginFriendlyFistBypass(RacialContext context,
                                                        CallbackInfoReturnable<Boolean> cir) {
        MajinAbsorptionFriendlyFistBypass.begin(context.player());
    }

    @Inject(method = "onActivate", at = @At("RETURN"))
    private void dmzlivingworld$endFriendlyFistBypass(RacialContext context,
                                                      CallbackInfoReturnable<Boolean> cir) {
        MajinAbsorptionFriendlyFistBypass.end(context.player());
    }
}
