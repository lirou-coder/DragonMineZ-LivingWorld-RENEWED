package com.dmzlwfusion.mixin;

import com.dmzlwfusion.LWFusionManager;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.server.events.players.actionmode.FusionModeHandler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FusionModeHandler.class, remap = false)
public abstract class FusionModeHandlerMixin {
    /**
     * Preserve DMZ player-player fusion completely. Only when DMZ returns false
     * do we give the currently active LW companion a chance to satisfy the same
     * native Fusion action.
     */
    @Inject(method = "attemptFusion", at = @At("RETURN"), cancellable = true)
    private static void dmzlwfusion$tryLivingWorldPartner(ServerPlayer player, StatsData stats,
                                                           CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        if (LWFusionManager.tryMetamoru(player, stats)) cir.setReturnValue(true);
    }
}
