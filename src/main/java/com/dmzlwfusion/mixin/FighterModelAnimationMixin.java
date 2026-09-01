package com.dmzlwfusion.mixin;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Living World 0.x uses DMZ's saga_base animation file for generated fighters.
 * The bridge's resource is the same saga_base library plus DMZ's four native
 * fusion clips, so every existing LW animation remains available unchanged.
 */
@Pseudo
@Mixin(targets = "com.dmzlivingworld.client.FighterModel", remap = false)
public abstract class FighterModelAnimationMixin {
    private static final ResourceLocation LWFUSION_ANIMS =
            new ResourceLocation(com.dmzlivingworld.LivingWorldMod.MOD_ID, "animations/entity/lw_fusion.animation.json");

    @Inject(
            method = "getAnimationResource(Lcom/dmzlivingworld/entity/AmbientFighterEntity;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void dmzlwfusion$useFusionCapableAnimationLibrary(CallbackInfoReturnable<ResourceLocation> cir) {
        cir.setReturnValue(LWFUSION_ANIMS);
    }
}
