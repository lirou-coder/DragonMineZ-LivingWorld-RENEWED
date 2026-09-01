package com.dmzlivingworld.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/** Persistent singleton lifecycle for the Red Ribbon Prodigy/Experiment world menace. */
public final class RedRibbonExperimentData extends SavedData {
    private static final String DATA_NAME = "dmzlivingworld_rr_experiment_v1";
    private boolean initialized;
    private boolean active;
    private UUID entityId;
    private CompoundTag profile = new CompoundTag();
    private long returnAt;
    private int defeats;
    private double x, y, z;

    public static RedRibbonExperimentData get(ServerLevel anyLevel) {
        return anyLevel.getServer().overworld().getDataStorage().computeIfAbsent(
                RedRibbonExperimentData::load, RedRibbonExperimentData::new, DATA_NAME);
    }
    public boolean initialized(){return initialized;} public boolean active(){return active;} public UUID entityId(){return entityId;}
    public CompoundTag profile(){return profile.copy();} public long returnAt(){return returnAt;} public int defeats(){return defeats;}
    public double x(){return x;} public double y(){return y;} public double z(){return z;}
    public void scheduleFirst(long when){ if(initialized)return; initialized=true; active=false; returnAt=Math.max(1L,when); setDirty(); }
    public void markActive(UUID id, CompoundTag p,double x,double y,double z){ initialized=true;active=true;entityId=id;profile=p==null?new CompoundTag():p.copy();this.x=x;this.y=y;this.z=z;returnAt=0L;setDirty(); }
    public void updateSnapshot(CompoundTag p,double x,double y,double z){ if(p!=null)profile=p.copy();this.x=x;this.y=y;this.z=z;setDirty(); }
    public void markDefeated(CompoundTag p,long when,double x,double y,double z){ initialized=true;active=false;entityId=null;defeats++;profile=p==null?new CompoundTag():p.copy();returnAt=Math.max(1L,when);this.x=x;this.y=y;this.z=z;setDirty(); }
    @Override public CompoundTag save(CompoundTag t){ t.putBoolean("Initialized",initialized);t.putBoolean("Active",active);if(entityId!=null)t.putUUID("EntityId",entityId);t.put("Profile",profile.copy());t.putLong("ReturnAt",returnAt);t.putInt("Defeats",defeats);t.putDouble("X",x);t.putDouble("Y",y);t.putDouble("Z",z);return t; }
    public static RedRibbonExperimentData load(CompoundTag t){ RedRibbonExperimentData d=new RedRibbonExperimentData();d.initialized=t.getBoolean("Initialized");d.active=t.getBoolean("Active");d.entityId=t.hasUUID("EntityId")?t.getUUID("EntityId"):null;if(t.contains("Profile",Tag.TAG_COMPOUND))d.profile=t.getCompound("Profile").copy();d.returnAt=Math.max(0L,t.getLong("ReturnAt"));d.defeats=Math.max(0,t.getInt("Defeats"));d.x=t.getDouble("X");d.y=t.getDouble("Y");d.z=t.getDouble("Z");return d; }
}
