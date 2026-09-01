package com.dmzlwfusion.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Client-only accessor used to isolate LW-held DMZ GeoItem model state during nested item renders. */
@Mixin(value = GeoItemRenderer.class, remap = false)
public interface GeoItemRendererAccessor {
    @Accessor("model")
    GeoModel<?> dmzlivingworld$getModel();

    @Mutable
    @Accessor("model")
    void dmzlivingworld$setModel(GeoModel<?> model);
}
