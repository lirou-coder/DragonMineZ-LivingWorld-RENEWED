package com.dmzlwfusion.mixin;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.FighterVisualPower;
import com.dragonminez.client.systems.kisense.KiSenseScan;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KiSenseScan.class, remap = false)
public abstract class KiSenseVisualBattlePowerMixin {
    @Inject(method = "getEntityBP", at = @At("HEAD"), cancellable = true)
    private static void livingWorldVisualPower(LivingEntity entity, CallbackInfoReturnable<Float> cir) {
        if (!(entity instanceof AmbientFighterEntity fighter)) return;
        // Overhaul already replaces KiSenseScan#getEntityBP for every non-player
        // LivingEntity and performs the same double -> bounded-float path used by
        // players. Do not race/override its mixin when it is installed.
        if (ModList.get().isLoaded("dmzrevamp")) return;
        // Keep the value outside the legacy int-backed IBattlePower path. Ki Sense
        // accepts a Float, so calculate in double and convert only at the API boundary.
        double battlePower = FighterVisualPower.ofDouble(fighter);
        if (!Double.isFinite(battlePower) || battlePower <= 0.0D) {
            cir.setReturnValue(0.0F);
            return;
        }
        cir.setReturnValue(battlePower >= Float.MAX_VALUE ? Float.MAX_VALUE : (float)battlePower);
    }
}
