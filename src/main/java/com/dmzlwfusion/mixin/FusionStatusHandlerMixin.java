package com.dmzlwfusion.mixin;

import com.dmzlwfusion.LWFusionManager;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.server.events.players.statuseffect.FusionStatusHandler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FusionStatusHandler.class, remap = false)
public abstract class FusionStatusHandlerMixin {
    /**
     * DMZ's native status tick resolves fusion partners exclusively through the
     * server player list. For an LW companion that would end the fusion on the
     * first tick. Bridge sessions keep DMZ's Status fields but own this one tick
     * path until separation.
     */
    @Inject(method = "fusionTickHandling", at = @At("HEAD"), cancellable = true)
    private static void dmzlwfusion$handleNpcPartner(ServerPlayer player, StatsData stats, CallbackInfo ci) {
        if (LWFusionManager.handleFusionTick(player, stats)) ci.cancel();
    }
}
