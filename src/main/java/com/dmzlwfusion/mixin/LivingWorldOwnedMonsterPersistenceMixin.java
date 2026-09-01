package com.dmzlwfusion.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps only Living World-owned native minions functional through vanilla Peaceful/distance rules. */
@Mixin(value = Mob.class, remap = false)
public abstract class LivingWorldOwnedMonsterPersistenceMixin {
    private boolean dmzlivingworld$isOwnedMinion(Mob self) {
        var data = self.getPersistentData();
        return data.getBoolean("LWScientistPersistentSpecimen") || data.getBoolean("LWX7Reinforcement");
    }

    @Inject(method = {"shouldDespawnInPeaceful()Z", "m_8028_()Z"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void dmzlivingworld$keepOwnedMinionsInPeaceful(CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob)(Object)this;
        if (self.level().isClientSide || !dmzlivingworld$isOwnedMinion(self)) return;
        self.setPersistenceRequired();
        cir.setReturnValue(false);
    }

}
