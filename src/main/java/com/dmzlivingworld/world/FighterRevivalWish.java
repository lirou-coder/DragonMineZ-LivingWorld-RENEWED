package com.dmzlivingworld.world;

import com.dragonminez.common.wish.Wish;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** A server-authoritative DMZ wish. The UUID is validated again when granted. */
public final class FighterRevivalWish extends Wish {
    private String recordId;
    private String mode;

    public FighterRevivalWish(String name, String description, UUID recordId, String mode) {
        super(name, description, "dmzlivingworld_revive_fighter");
        this.recordId = recordId == null ? "" : recordId.toString();
        this.mode = mode == null ? "ONE" : mode;
    }

    @Override public void grant(ServerPlayer player) {
        UUID id = null;
        try { if (recordId != null && !recordId.isBlank()) id = UUID.fromString(recordId); }
        catch (IllegalArgumentException ignored) {}
        FighterAfterlifeManager.revive(player, id, mode);
    }

    @Override public String toJson() { return ""; }
}
