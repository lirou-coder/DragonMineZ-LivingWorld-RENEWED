package com.dmzlwfusion.mixin;

import com.dmzlivingworld.client.LWLang;
import com.dragonminez.client.gui.WishesScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lets Living World wish titles carry client-localized text and dynamic NPC names. */
@Mixin(WishesScreen.class)
public abstract class WishesScreenMixin {
    @Redirect(
            method = "renderWishesList",
            at = @At(value = "INVOKE", target = "Lcom/dragonminez/client/gui/WishesScreen;tr(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;")
    )
    private MutableComponent dmzlivingworld$translateWishName(WishesScreen screen, String key, Object[] arguments) {
        return LWLang.isSpeechKey(key) ? LWLang.speech(key).copy() : Component.translatable(key, arguments);
    }
}
