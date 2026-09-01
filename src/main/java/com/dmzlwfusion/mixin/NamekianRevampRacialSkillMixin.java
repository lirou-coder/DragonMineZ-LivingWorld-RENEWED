package com.dmzlwfusion.mixin;

import com.dmzlivingworld.world.NamekAssimilationCompat;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.dmzrevamp.racial.impl.NamekianRevampRacialSkill", remap = false)
public abstract class NamekianRevampRacialSkillMixin {
    @Inject(method = "assimilate", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dmzlivingworld$namekFighter(ServerPlayer player, StatsData data, CallbackInfo ci) {
        if (NamekAssimilationCompat.tryRevamp(player, data)) ci.cancel();
    }
}
