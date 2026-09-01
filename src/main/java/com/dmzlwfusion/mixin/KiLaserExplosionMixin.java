package com.dmzlwfusion.mixin;

import com.dmzlivingworld.world.KiSafetyManager;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Identifies DMZ laser explosions while their synchronous Forge Detonate event is being built. */
@Mixin(value = KiLaserEntity.class, remap = false)
public abstract class KiLaserExplosionMixin {
    @Inject(method = "explodeAndDie", at = @At("HEAD"))
    private void dmzlivingworld$beginSelectiveExplosion(Vec3 hit, CallbackInfo ci) {
        KiSafetyManager.beginLaserExplosion((AbstractKiProjectile)(Object)this);
    }

    @Inject(method = "explodeAndDie", at = @At("RETURN"))
    private void dmzlivingworld$endSelectiveExplosion(Vec3 hit, CallbackInfo ci) {
        KiSafetyManager.endLaserExplosion((AbstractKiProjectile)(Object)this);
    }
}
