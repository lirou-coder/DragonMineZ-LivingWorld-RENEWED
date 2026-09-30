package com.dmzlwfusion.mixin;

import com.dragonminez.server.util.GravityLogic;
import com.kunyo.dbzmeditation.DBZMeditation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps native DMZ gravity queries active while Meditation uses its visual seat. */
@Mixin(value = GravityLogic.class, remap = false)
public abstract class MeditationGravityLogicMixin {
    @Inject(method = "getMachineGravity", at = @At("RETURN"), cancellable = true, require = 0)
    private static void dmzlivingworld$preserveMachineGravity(Player player,
                                                               CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(DBZMeditation.preserveMountedGravity(player, "machine", cir.getReturnValueD()));
    }

    @Inject(method = "getGravityMultiplier", at = @At("RETURN"), cancellable = true, require = 0)
    private static void dmzlivingworld$preserveGravity(Player player,
                                                        CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(DBZMeditation.preserveMountedGravity(player, "general", cir.getReturnValueD()));
    }

    @Inject(method = "getTrainingGravityMultiplier", at = @At("RETURN"), cancellable = true, require = 0)
    private static void dmzlivingworld$preserveTrainingGravity(Player player,
                                                                CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(DBZMeditation.preserveMountedGravity(player, "training", cir.getReturnValueD()));
    }
}
