package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterArchetype;
import com.dmzlivingworld.entity.FighterNames;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dmzlivingworld.entity.FighterRace;
import com.dmzlivingworld.entity.FighterRank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistent faction captives used by rare war/rescue stories. */
public final class PrisonerWorldData extends SavedData {
    private static final String DATA_NAME = "dmzlivingworld_prisoners_v1";
    private final List<Prisoner> prisoners;

    private PrisonerWorldData(List<Prisoner> prisoners) { this.prisoners = prisoners; }

    public static PrisonerWorldData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(PrisonerWorldData::load,
                () -> new PrisonerWorldData(new ArrayList<>()), DATA_NAME);
    }

    private static PrisonerWorldData load(CompoundTag tag) {
        List<Prisoner> out = new ArrayList<>();
        ListTag list = tag.getList("Prisoners", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) out.add(Prisoner.load(list.getCompound(i)));
        return new PrisonerWorldData(out);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Prisoner p : prisoners) list.add(p.save());
        tag.put("Prisoners", list);
        return tag;
    }

    public List<Prisoner> all() { return List.copyOf(prisoners); }
    public List<Prisoner> active() { return prisoners.stream().filter(Prisoner::active).toList(); }
    public Prisoner byId(String id) { return prisoners.stream().filter(p -> p.id.equals(id)).findFirst().orElse(null); }

    /**
     * Legacy API kept for compatibility, but R27 will no longer synthesize a prisoner identity.
     * It can only select one already-manifested persistent resident from the victim faction.
     */
    public Prisoner capture(ServerLevel level, WorldFaction victim, WorldFaction captor, long now) {
        if (level == null || victim == null || captor == null || victim.realm() != captor.realm() || active().size() >= 6) return null;
        List<AmbientFighterEntity> candidates = FactionRequestMissionManager.loadedAvailableResidents(level, victim,
                f -> !f.isNonCombatant() && !f.isCaptive() && !f.isDefeated());
        if (candidates.isEmpty()) return null;
        RandomSource random = RandomSource.create(FactionWorldData.mix(level.getServer().overworld().getSeed()
                ^ now ^ victim.seed() ^ Long.rotateLeft(captor.seed(), 17)));
        return captureExisting(level, candidates.get(random.nextInt(candidates.size())), captor, now);
    }

    /** Converts the exact real LW resident into a prisoner without deleting/replacing their entity identity. */
    public Prisoner captureExisting(ServerLevel level, AmbientFighterEntity fighter, WorldFaction captor, long now) {
        if (level == null || fighter == null || captor == null || !fighter.isFactionMember() || active().size() >= 6) return null;
        WorldFaction victim = FactionWorldData.get(level).byId(fighter.getFactionId());
        if (victim == null || victim.realm() != captor.realm() || victim.id().equals(captor.id())) return null;
        Prisoner already = prisoners.stream().filter(Prisoner::active).filter(p -> fighter.getUUID().equals(p.entityId)).findFirst().orElse(null);
        if (already != null) return already;
        // Population represents available manpower. Capture removes availability when the simulated faction can spare
        // the slot; the flag prevents a later death/rescue from decrementing/incrementing that same person twice.
        boolean populationRemoved = FactionWorldData.get(level).removePopulation(victim, fighter.isNonCombatant(), 1, now, fighter.getFighterName());
        Prisoner prisoner = new Prisoner("prisoner_" + UUID.randomUUID(), fighter.getFighterName(), victim.id(), captor.id(), victim.realm(),
                fighter.getRace(), fighter.isFemale(), fighter.getPersonality(), fighter.getArchetype(), fighter.getRank(),
                fighter.getFactionRole(), fighter.getPermanentBattlePower(), now);
        prisoner.entityId = fighter.getUUID();
        prisoner.populationRemoved = populationRemoved;
        fighter.setCaptive(true);
        fighter.setStoryRole(AmbientFighterEntity.STORY_CAPTIVE);
        fighter.setPersistenceRequired();
        fighter.setTarget(null);
        fighter.getNavigation().stop();
        prisoners.add(prisoner);
        FactionWorldData.get(level).addHistory(victim, now, fighter.getFighterName() + " was captured by " + captor.name() + ".");
        FactionWorldData.get(level).addHistory(captor, now, fighter.getFighterName() + " of " + victim.name() + " was taken prisoner alive.");
        setDirty();
        return prisoner;
    }

    public void markRescued(ServerLevel level, Prisoner prisoner, long now) {
        if (prisoner == null || !prisoner.active()) return;
        prisoner.status = "RESCUED";
        WorldFaction victim = FactionWorldData.get(level).byId(prisoner.victimFactionId);
        if (victim != null && prisoner.populationRemoved) {
            FactionWorldData.get(level).addPopulation(victim, false, 1, now, prisoner.name + " returned after being rescued.");
            prisoner.populationRemoved = false;
        }
        if (prisoner.entityId != null && level.getEntity(prisoner.entityId) instanceof AmbientFighterEntity physical) {
            physical.setCaptive(false);
            physical.setStoryRole(AmbientFighterEntity.STORY_NONE);
            physical.setTarget(null);
        }
        setDirty();
    }

    public void resolveExpired(ServerLevel level, Prisoner prisoner, long now) {
        if (prisoner == null || !prisoner.active()) return;
        WorldFaction victim = FactionWorldData.get(level).byId(prisoner.victimFactionId);
        WorldFaction captor = FactionWorldData.get(level).byId(prisoner.captorFactionId);
        RandomSource random = RandomSource.create(FactionWorldData.mix(prisoner.id.hashCode() ^ now));
        int roll = random.nextInt(100);
        if (roll < 42) {
            prisoner.status = "ESCAPED";
            if (victim != null && prisoner.populationRemoved) {
                FactionWorldData.get(level).addPopulation(victim, false, 1, now, prisoner.name + " escaped captivity and returned.");
                prisoner.populationRemoved = false;
            }
        } else if (roll < 65) {
            prisoner.status = "RELEASED";
            if (victim != null && prisoner.populationRemoved) {
                FactionWorldData.get(level).addPopulation(victim, false, 1, now, prisoner.name + " was released from captivity.");
                prisoner.populationRemoved = false;
            }
        } else if (roll < 82 && captor != null) {
            prisoner.status = "DEFECTED";
            if (prisoner.populationRemoved) {
                FactionWorldData.get(level).addPopulation(captor, false, 1, now, prisoner.name + " defected while in captivity.");
                prisoner.populationRemoved = false;
            } else if (victim != null) {
                FactionWorldData.get(level).transferPopulation(victim, captor, false, now, prisoner.name);
            }
            if (victim != null) FactionWorldData.get(level).addHistory(victim, now, prisoner.name + " defected to " + captor.name() + " while captive.");
        } else {
            prisoner.status = "DEAD";
            if (victim != null) FactionWorldData.get(level).addHistory(victim, now, prisoner.name + " died in enemy captivity.");
        }
        setDirty();
    }


    public void markDead(ServerLevel level, Prisoner prisoner, long now, String reason) {
        if (prisoner == null || !prisoner.active()) return;
        prisoner.status = "DEAD";
        WorldFaction victim = level == null ? null : FactionWorldData.get(level).byId(prisoner.victimFactionId);
        if (victim != null) FactionWorldData.get(level).addHistory(victim, now,
                prisoner.name + " died while captive" + (reason == null || reason.isBlank() ? "." : " (" + reason + ")."));
        setDirty();
    }

    public static final class Prisoner {
        public final String id;
        public final String name;
        public final String victimFactionId;
        public final String captorFactionId;
        public final FactionRealm realm;
        public final FighterRace race;
        public final boolean female;
        public final FighterPersonality personality;
        public final FighterArchetype archetype;
        public final FighterRank rank;
        public final FactionRole role;
        public final int battlePower;
        public final long capturedAt;
        public String status = "CAPTIVE";
        public UUID entityId;
        /** True when capture already removed this person's active manpower slot from the victim faction. */
        public boolean populationRemoved;

        Prisoner(String id, String name, String victimFactionId, String captorFactionId, FactionRealm realm,
                 FighterRace race, boolean female, FighterPersonality personality, FighterArchetype archetype,
                 FighterRank rank, FactionRole role, int battlePower, long capturedAt) {
            this.id=id; this.name=name; this.victimFactionId=victimFactionId; this.captorFactionId=captorFactionId;
            this.realm=realm; this.race=race; this.female=female; this.personality=personality; this.archetype=archetype;
            this.rank=rank; this.role=role; this.battlePower=battlePower; this.capturedAt=capturedAt;
        }
        public boolean active() { return "CAPTIVE".equals(status); }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Id",id); t.putString("Name",name); t.putString("Victim",victimFactionId); t.putString("Captor",captorFactionId);
            t.putInt("Realm",realm.id()); t.putInt("Race",race.id()); t.putBoolean("Female",female); t.putInt("Personality",personality.id());
            t.putInt("Archetype",archetype.id()); t.putInt("Rank",rank.id()); t.putInt("Role",role.id()); t.putInt("Power",battlePower);
            t.putLong("CapturedAt",capturedAt); t.putString("Status",status); if (entityId != null) t.putUUID("Entity",entityId);
            t.putBoolean("PopulationRemoved", populationRemoved); return t;
        }
        static Prisoner load(CompoundTag t) {
            Prisoner p = new Prisoner(t.getString("Id"),t.getString("Name"),t.getString("Victim"),t.getString("Captor"),
                    FactionRealm.byId(t.getInt("Realm")), FighterRace.byId(t.getInt("Race")),t.getBoolean("Female"),
                    FighterPersonality.byId(t.getInt("Personality")),FighterArchetype.byId(t.getInt("Archetype")),
                    FighterRank.byId(t.getInt("Rank")),FactionRole.byId(t.getInt("Role")),t.getInt("Power"),t.getLong("CapturedAt"));
            p.status=t.getString("Status"); if (p.status.isBlank()) p.status="CAPTIVE"; if (t.hasUUID("Entity")) p.entityId=t.getUUID("Entity");
            p.populationRemoved = t.contains("PopulationRemoved") ? t.getBoolean("PopulationRemoved") : true; return p;
        }
    }
}
