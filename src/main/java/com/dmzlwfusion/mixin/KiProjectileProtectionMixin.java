package com.dmzlwfusion.mixin;

import com.dmzlivingworld.world.KiSafetyManager;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Selective Living World build protection inside DMZ's native Ki block-destruction path. */
@Mixin(value = AbstractKiProjectile.class, remap = false)
public abstract class KiProjectileProtectionMixin {
    @Inject(method = "canKiDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void dmzlivingworld$protectTrackedPlayerBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        AbstractKiProjectile projectile = (AbstractKiProjectile)(Object)this;
        if (KiSafetyManager.shouldProtectBlock(projectile, pos)) cir.setReturnValue(false);
    }

    @Inject(method = "destroyKiBlock", at = @At("RETURN"))
    private void dmzlivingworld$forgetDirectDestroyedBlock(BlockPos pos, boolean drop,
                                                            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            KiSafetyManager.onDirectKiBlockDestroyed((AbstractKiProjectile)(Object)this, pos);
        }
    }

    @Inject(method = "setKiBlockToAir", at = @At("RETURN"))
    private void dmzlivingworld$forgetDirectAirBlock(BlockPos pos, int flags,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            KiSafetyManager.onDirectKiBlockDestroyed((AbstractKiProjectile)(Object)this, pos);
        }
    }
}
