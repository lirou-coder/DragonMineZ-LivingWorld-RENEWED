package com.dmzlwfusion.mixin;

import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Monster overrides Mob.shouldDespawnInPeaceful(), so the generic Mob persistence mixin cannot
 * intercept native DMZ Saibamen/Red Ribbon soldiers. Keep only explicitly LW-owned hostiles alive.
 */
@Mixin(value = Monster.class, remap = false)
public abstract class LivingWorldOwnedHostilePersistenceMixin {
    @Inject(method = {"shouldDespawnInPeaceful()Z", "m_8028_()Z"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void dmzlivingworld$keepOwnedHostilesInPeaceful(CallbackInfoReturnable<Boolean> cir) {
        Monster self = (Monster)(Object)this;
        if (self.level().isClientSide) return;
        var data = self.getPersistentData();
        if (!data.getBoolean("LWScientistPersistentSpecimen") && !data.getBoolean("LWX7Reinforcement")) return;
        self.setPersistenceRequired();
        cir.setReturnValue(false);
    }
}
