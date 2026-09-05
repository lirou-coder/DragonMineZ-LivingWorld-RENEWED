package com.dmzlwfusion.mixin;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Excludes Living World fighters from vanilla's nearby-monster sleep check. */
@Mixin(value = Monster.class, remap = false)
public abstract class PlayerRestMonsterMixin {
    @Inject(method = "m_6935_(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void dmzlivingworld$ignoreAmbientFighter(Player player, CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof AmbientFighterEntity) cir.setReturnValue(false);
    }
}