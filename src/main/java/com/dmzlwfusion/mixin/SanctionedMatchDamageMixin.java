package com.dmzlwfusion.mixin;

import com.dmzlivingworld.world.SanctionedMatchGuard;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies the spar floor after LivingEntity has finished all vanilla/modded damage mitigation. */
@Mixin(value = LivingEntity.class)
public abstract class SanctionedMatchDamageMixin {
    @Redirect(
            method = "m_6475_(Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_21153_(F)V"),
            remap = false
    )
    private void dmzlivingworld$applyFinalSparDamage(LivingEntity target, float requestedHealth,
                                                      DamageSource source, float ignoredDamageAmount) {
        float finalDamage = target.getHealth() - requestedHealth;
        if (SanctionedMatchGuard.handleFinalSparDamage(target, source, finalDamage)) return;
        target.setHealth(requestedHealth);
    }
}
