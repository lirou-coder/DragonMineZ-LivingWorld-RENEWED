package com.kunyo.dbzmeditation;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

/**
 * Dragon Mine Z Minigames-screen integration intentionally disabled.
 *
 * Meditation remains available through Living World's dedicated Meditation page,
 * keybind and commands.  Keeping the native DMZ Minigames screen untouched avoids
 * row insertion/hover-colour leakage when Dragon Mine Z changes its private menu
 * geometry or another addon also extends that screen.
 */
@Mod.EventBusSubscriber(
        modid = DBZMeditation.OWNER_MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class DMZMinigamesIntegration {
    private DMZMinigamesIntegration() {}
}
