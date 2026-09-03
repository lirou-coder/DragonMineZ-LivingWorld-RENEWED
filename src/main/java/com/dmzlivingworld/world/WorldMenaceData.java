package com.dmzlivingworld.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/** Persistent singleton state for special recurring world menaces. */
public final class WorldMenaceData extends SavedData {
    private static final String DATA_NAME = "dmzlivingworld_world_menace_v1";
    private boolean initialized;
    private boolean active;
    private UUID entityId;
    private CompoundTag profile = new CompoundTag();
    private long returnAt;
    private int deaths;
    private double x, y, z;

    public static WorldMenaceData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(WorldMenaceData::load, WorldMenaceData::new, DATA_NAME);
    }

    public boolean initialized() { return initialized; }
    public boolean active() { return active; }
    public UUID entityId() { return entityId; }
    public CompoundTag profile() { return profile.copy(); }
    public long returnAt() { return returnAt; }
    public int deaths() { return deaths; }
    public double x() { return x; } public double y() { return y; } public double z() { return z; }

    public void scheduleFirst(long when) {
        if (initialized) return;
        initialized = true; active = false; returnAt = Math.max(1L, when); setDirty();
    }

    public void markActive(UUID entityId, CompoundTag profile, double x, double y, double z) {
        initialized = true; active = true; this.entityId = entityId;
        this.profile = profile == null ? new CompoundTag() : profile.copy();
        this.x = x; this.y = y; this.z = z; returnAt = 0L; setDirty();
    }

    public void updateSnapshot(CompoundTag profile, double x, double y, double z) {
        if (profile != null) this.profile = profile.copy();
        this.x = x; this.y = y; this.z = z; setDirty();
    }

    public void markDead(CompoundTag profile, long returnAt, double x, double y, double z) {
        initialized = true; active = false; entityId = null; deaths++;
        this.profile = profile == null ? new CompoundTag() : profile.copy();
        this.returnAt = Math.max(1L, returnAt); this.x=x; this.y=y; this.z=z; setDirty();
    }

    /** Removes a sighting without counting it as a defeat/return. */
    public void markAbsent(CompoundTag profile, long returnAt, double x, double y, double z) {
        initialized = true; active = false; entityId = null;
        this.profile = profile == null ? new CompoundTag() : profile.copy();
        this.returnAt = Math.max(1L, returnAt); this.x=x; this.y=y; this.z=z; setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Initialized", initialized);
        tag.putBoolean("Active", active);
        if (entityId != null) tag.putUUID("EntityId", entityId);
        tag.put("Profile", profile.copy());
        tag.putLong("ReturnAt", returnAt);
        tag.putInt("Deaths", deaths);
        tag.putDouble("X", x); tag.putDouble("Y", y); tag.putDouble("Z", z);
        return tag;
    }

    public static WorldMenaceData load(CompoundTag tag) {
        WorldMenaceData data = new WorldMenaceData();
        data.initialized = tag.getBoolean("Initialized");
        data.active = tag.getBoolean("Active");
        data.entityId = tag.hasUUID("EntityId") ? tag.getUUID("EntityId") : null;
        if (tag.contains("Profile", Tag.TAG_COMPOUND)) data.profile = tag.getCompound("Profile").copy();
        data.returnAt = Math.max(0L, tag.getLong("ReturnAt"));
        data.deaths = Math.max(0, tag.getInt("Deaths"));
        data.x=tag.getDouble("X"); data.y=tag.getDouble("Y"); data.z=tag.getDouble("Z");
        return data;
    }
}
