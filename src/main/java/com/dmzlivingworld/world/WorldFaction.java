package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterArchetype;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dmzlivingworld.entity.FighterRace;
import net.minecraft.nbt.CompoundTag;

/** Immutable seed identity; mutable organizational state lives in FactionWorldData. */
public final class WorldFaction {
    private final int slot;
    private final String id;
    private final String name;
    private final FactionEthos ethos;
    private final FighterAlignment alignment;
    private final FactionRealm realm;
    private final FactionStructure structure;
    private final int uniformTheme;
    private final float powerBias;
    private final long seed;
    private final int roamX;
    private final int roamZ;
    private final int roamRadius;
    private final String leaderName;
    private final FighterRace leaderRace;
    private final boolean leaderFemale;
    private final FighterPersonality leaderPersonality;
    private final FighterArchetype leaderArchetype;

    public WorldFaction(int slot, String id, String name, FactionEthos ethos, FighterAlignment alignment,
                        FactionRealm realm, FactionStructure structure,
                        int uniformTheme, float powerBias, long seed, int roamX, int roamZ, int roamRadius,
                        String leaderName, FighterRace leaderRace, boolean leaderFemale,
                        FighterPersonality leaderPersonality, FighterArchetype leaderArchetype) {
        this.slot = slot;
        this.id = id;
        this.name = name;
        this.ethos = ethos;
        this.alignment = alignment;
        this.realm = realm;
        this.structure = structure;
        this.uniformTheme = uniformTheme;
        this.powerBias = powerBias;
        this.seed = seed;
        this.roamX = roamX;
        this.roamZ = roamZ;
        this.roamRadius = roamRadius;
        this.leaderName = leaderName;
        this.leaderRace = leaderRace;
        this.leaderFemale = leaderFemale;
        this.leaderPersonality = leaderPersonality;
        this.leaderArchetype = leaderArchetype;
    }

    public int slot() { return slot; }
    public String id() { return id; }
    public String name() { return name; }
    public FactionEthos ethos() { return ethos; }
    public FighterAlignment alignment() { return alignment; }
    public FactionRealm realm() { return realm; }
    public FactionStructure structure() { return structure; }
    public int uniformTheme() { return uniformTheme; }
    public float powerBias() { return powerBias; }
    public long seed() { return seed; }
    public int roamX() { return roamX; }
    public int roamZ() { return roamZ; }
    public int roamRadius() { return roamRadius; }
    public String leaderName() { return leaderName; }
    public FighterRace leaderRace() { return leaderRace; }
    public boolean leaderFemale() { return leaderFemale; }
    public FighterPersonality leaderPersonality() { return leaderPersonality; }
    public FighterArchetype leaderArchetype() { return leaderArchetype; }

    public String roleTitle(FactionRole role) { return structure.title(role); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Slot", slot);
        tag.putString("Id", id);
        tag.putString("Name", name);
        tag.putInt("Ethos", ethos.ordinal());
        tag.putInt("Alignment", alignment.id());
        tag.putInt("Realm", realm.id());
        tag.putInt("Structure", structure.id());
        tag.putInt("Uniform", uniformTheme);
        tag.putFloat("PowerBias", powerBias);
        tag.putLong("Seed", seed);
        tag.putInt("RoamX", roamX);
        tag.putInt("RoamZ", roamZ);
        tag.putInt("RoamRadius", roamRadius);
        tag.putString("LeaderName", leaderName);
        tag.putInt("LeaderRace", leaderRace.id());
        tag.putBoolean("LeaderFemale", leaderFemale);
        tag.putInt("LeaderPersonality", leaderPersonality.id());
        tag.putInt("LeaderArchetype", leaderArchetype.id());
        return tag;
    }

    public static WorldFaction load(CompoundTag tag) {
        FactionEthos ethos = FactionEthos.byId(tag.getInt("Ethos"));
        FactionRealm realm = tag.contains("Realm") ? FactionRealm.byId(tag.getInt("Realm")) : FactionRealm.EARTH;
        FactionStructure structure = tag.contains("Structure")
                ? FactionStructure.byId(tag.getInt("Structure")) : FactionStructure.forEthos(ethos, realm);
        return new WorldFaction(
                tag.getInt("Slot"), tag.getString("Id"), tag.getString("Name"),
                ethos, FighterAlignment.byId(tag.getInt("Alignment")), realm, structure,
                tag.getInt("Uniform"), tag.getFloat("PowerBias"), tag.getLong("Seed"),
                tag.getInt("RoamX"), tag.getInt("RoamZ"), tag.contains("RoamRadius") ? tag.getInt("RoamRadius") : 750,
                tag.getString("LeaderName"), FighterRace.byId(tag.getInt("LeaderRace")), tag.getBoolean("LeaderFemale"),
                FighterPersonality.byId(tag.getInt("LeaderPersonality")), FighterArchetype.byId(tag.getInt("LeaderArchetype"))
        );
    }
}
