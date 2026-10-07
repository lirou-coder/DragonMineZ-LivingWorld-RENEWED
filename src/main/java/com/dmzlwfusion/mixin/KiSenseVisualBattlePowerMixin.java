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
        // When Overhaul is installed its Ki Sense integration is authoritative. Do not replace
        // that result with Living World's visual multiplier or cached reference value.
        if (ModList.get().isLoaded("dmzrevamp")) return;
        double battlePower = FighterVisualPower.ofDouble(fighter);
        if (!Double.isFinite(battlePower) || battlePower <= 0.0D) {
            cir.setReturnValue(0.0F);
            return;
        }
        cir.setReturnValue(battlePower >= Float.MAX_VALUE ? Float.MAX_VALUE : (float)battlePower);
    }
}
