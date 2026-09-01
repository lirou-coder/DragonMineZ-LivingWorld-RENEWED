package com.dmzlwfusion.mixin;

import com.dmzlivingworld.world.NamekAssimilationCompat;
import com.dmzlivingworld.world.MajinAbsorptionFriendlyFistBypass;
import com.dragonminez.server.util.RacialSkillLogic;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RacialSkillLogic.class)
public abstract class RacialSkillLogicMixin {
    @Inject(method = "attemptRacialAction", at = @At("HEAD"), cancellable = true)
    private static void dmzlivingworld$namekFighter(ServerPlayer player, CallbackInfo ci) {
        if (NamekAssimilationCompat.tryDmz(player)) ci.cancel();
    }

    @Inject(method = "handleNamekianAssimilation", at = @At("HEAD"), cancellable = true)
    private static void dmzlivingworld$namekExactTarget(ServerPlayer player, StatsData data,
                                                        LivingEntity target, CallbackInfo ci) {
        if (NamekAssimilationCompat.tryDmzTarget(player, data, target)) ci.cancel();
    }

    @Inject(method = "handleMajinAbsorption", at = @At("HEAD"))
    private static void dmzlivingworld$beginMajinFriendlyFistBypass(ServerPlayer player, StatsData data,
                                                                   LivingEntity target, CallbackInfo ci) {
        MajinAbsorptionFriendlyFistBypass.begin(player);
    }

    @Inject(method = "handleMajinAbsorption", at = @At("RETURN"))
    private static void dmzlivingworld$endMajinFriendlyFistBypass(ServerPlayer player, StatsData data,
                                                                 LivingEntity target, CallbackInfo ci) {
        MajinAbsorptionFriendlyFistBypass.end(player);
    }
}
