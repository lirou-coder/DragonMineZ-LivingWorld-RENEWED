package com.dmzlwfusion.mixin;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.NamekAssimilationCompat;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Extends Revamp's concrete NamekWarrior/NamekTrader/Piccolo check with LW race identity. */
@Pseudo
@Mixin(targets = "com.dmzrevamp.racial.impl.NamekianRevampRacialSkill", remap = false)
public abstract class NamekianRevampTargetMixin {
    @Inject(method = "isValidTarget", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dmzlivingworld$namekTarget(LivingEntity target, @Coerce Object config,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof AmbientFighterEntity fighter && NamekAssimilationCompat.isNamekian(fighter))
            cir.setReturnValue(true);
    }
}
