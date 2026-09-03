package com.dmzlwfusion.mixin;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.LivingBondManager;
import com.dragonminez.common.combat.logic.player.TargetHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes a travelling fighter use DMZ's friendly-target path for buffs, heals and attacks. */
@Mixin(value = TargetHelper.class, remap = false)
public abstract class TargetHelperCompanionMixin {
    @Inject(method = "getRelation", at = @At("HEAD"), cancellable = true)
    private static void dmzlivingworld$companionRelation(Player attacker, Entity target,
                                                          CallbackInfoReturnable<TargetHelper.Relation> cir) {
        if (target instanceof AmbientFighterEntity fighter && LivingBondManager.isCompanionAlly(attacker, fighter))
            cir.setReturnValue(TargetHelper.Relation.FRIENDLY);
    }
}
