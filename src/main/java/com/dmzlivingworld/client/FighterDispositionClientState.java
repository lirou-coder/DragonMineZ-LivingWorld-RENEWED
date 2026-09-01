package com.dmzlivingworld.client;

import com.dmzlivingworld.network.FighterDispositionSnapshotPacket;
import com.dmzlivingworld.world.FighterRelationshipManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client-only cache of server-authoritative, player-specific fighter disposition. */
public final class FighterDispositionClientState {
    public record View(FighterRelationshipManager.Disposition disposition, int relationship) {
        public boolean hasRelationship() { return relationship >= -100 && relationship <= 100; }
    }

    private static final Map<Integer, View> BY_ENTITY = new HashMap<>();

    private FighterDispositionClientState() {}

    public static void replace(List<FighterDispositionSnapshotPacket.Entry> entries) {
        BY_ENTITY.clear();
        if (entries == null) return;
        for (FighterDispositionSnapshotPacket.Entry entry : entries) {
            BY_ENTITY.put(entry.entityId(), new View(
                    FighterRelationshipManager.Disposition.byId(entry.dispositionId()), entry.relationship()));
        }
    }

    public static View get(int entityId) { return BY_ENTITY.get(entityId); }
    public static void clear() { BY_ENTITY.clear(); }
}
