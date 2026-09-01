package com.dmzlwfusion.mixin;

import com.dmzlivingworld.world.MajinAbsorptionFriendlyFistBypass;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Revamp kills absorbed targets with playerAttack damage, which DMZ Friendly Fist otherwise makes non-lethal. */
@Pseudo
@Mixin(targets = "com.dmzrevamp.racial.impl.MajinRevampRacialSkill", remap = false)
public abstract class MajinRevampFriendlyFistMixin {
    @Inject(method = "killAbsorbedTarget", at = @At("HEAD"), remap = false)
    private static void dmzlivingworld$begin(ServerPlayer player, LivingEntity target, CallbackInfo ci) {
        MajinAbsorptionFriendlyFistBypass.begin(player);
    }

    @Inject(method = "killAbsorbedTarget", at = @At("RETURN"), remap = false)
    private static void dmzlivingworld$end(ServerPlayer player, LivingEntity target, CallbackInfo ci) {
        MajinAbsorptionFriendlyFistBypass.end(player);
    }
}
