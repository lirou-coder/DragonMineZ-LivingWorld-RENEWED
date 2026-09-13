package com.dmzlivingworld.world;

import com.dmzlivingworld.client.LWLang;
import com.dragonminez.common.wish.Wish;
import net.minecraft.server.level.ServerPlayer;

/** Dragon Ball wish that clears only the requesting player's Living World Wanted record. */
public final class ClearWantedWish extends Wish {
    public ClearWantedWish() {
        super(LWLang.speechKey("wish.clear_wanted", "Clear my Wanted Level"),
                "Clear your Living World Wanted record completely.", "dmzlivingworld_clear_wanted");
    }

    @Override
    public void grant(ServerPlayer player) {
        WantedManager.clearPlayerWanted(player);
    }

    @Override
    public String toJson() {
        return "";
    }
}
