package com.dmzlwfusion.mixin;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.FighterVisualPower;
import com.dragonminez.client.systems.kisense.KiSenseScan;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KiSenseScan.class, remap = false)
public abstract class KiSenseVisualBattlePowerMixin {
    @Inject(method = "getEntityBP", at = @At("HEAD"), cancellable = true)
    private static void livingWorldVisualPower(LivingEntity entity, CallbackInfoReturnable<Float> cir) {
        if (!(entity instanceof AmbientFighterEntity fighter)) return;
        // Living World owns the canonical reference budget for its fighters. Overhaul's generic
        // mob path derives BP from physical attributes and therefore cannot know that archetype
        // shares are distribution-only. This value already uses Overhaul's configured formula
        // when present, keeping Ki Sense, scouters and the fighter menu identical.
        double battlePower = FighterVisualPower.ofDouble(fighter);
        if (!Double.isFinite(battlePower) || battlePower <= 0.0D) {
            cir.setReturnValue(0.0F);
            return;
        }
        cir.setReturnValue(battlePower >= Float.MAX_VALUE ? Float.MAX_VALUE : (float)battlePower);
    }
}
