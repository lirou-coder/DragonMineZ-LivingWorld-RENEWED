package com.dmzlwfusion.mixin;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.FighterVisualPower;
import com.dragonminez.client.gui.hud.ScouterHUD;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ScouterHUD.class, remap = false)
public abstract class ScouterVisualBattlePowerMixin {
    @Inject(method = "getEntityBP", at = @At("HEAD"), cancellable = true)
    private static void livingWorldVisualPower(LivingEntity entity, CallbackInfoReturnable<Double> cir) {
        if (entity instanceof AmbientFighterEntity fighter) cir.setReturnValue((double)FighterVisualPower.of(fighter));
    }
}
