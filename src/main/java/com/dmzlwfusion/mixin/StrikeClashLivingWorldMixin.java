package com.dmzlwfusion.mixin;

import com.dmzlivingworld.world.StrikeClashCompat;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends DMZ's player-side clash discovery to Living World fighters. */
@Mixin(targets = "com.dragonminez.common.combat.clash.StrikeClash", remap = false)
public abstract class StrikeClashLivingWorldMixin {
    @Inject(method = "tryStart", at = @At("HEAD"), cancellable = true, remap = false)
    private static void livingWorld$tryStart(LivingEntity self, CallbackInfoReturnable<Boolean> cir) {
        if (StrikeClashCompat.tryStart(self)) cir.setReturnValue(true);
    }
}
