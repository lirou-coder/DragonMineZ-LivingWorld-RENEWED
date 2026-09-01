package com.dmzlwfusion.mixin;

import com.dmzlivingworld.world.FighterRevivalWish;
import com.dragonminez.common.wish.Wish;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(com.dragonminez.common.util.adapters.WishTypeAdapter.class)
public abstract class WishTypeAdapterMixin {
    @Inject(method = "classForType", at = @At("HEAD"), cancellable = true)
    private static void dmzlivingworld$wishType(String type, CallbackInfoReturnable<Class<? extends Wish>> cir) {
        if ("dmzlivingworld_revive_fighter".equals(type)) cir.setReturnValue(FighterRevivalWish.class);
    }
}
