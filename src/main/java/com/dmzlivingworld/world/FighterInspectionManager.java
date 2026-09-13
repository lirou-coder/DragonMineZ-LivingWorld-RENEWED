package com.dmzlivingworld.world;

import com.dmzlivingworld.client.LWLang;
import com.dmzlivingworld.config.LivingWorldConfig;

import com.dragonminez.common.util.CuriosUtil;
import com.dragonminez.common.init.entities.sagas.SagaSaibamanEntity;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.LWEntities;
import com.dmzlivingworld.network.FighterProfilePacket;
import com.dmzlivingworld.network.LWNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builds the server-authoritative Shift+Right-click character panel snapshot. */
public final class FighterInspectionManager {
    private FighterInspectionManager() {}

    private static String profileText(String key, String fallback, Object... args) {
        return LWLang.speechKey("profile.content." + key, fallback, args);
    }

    private static String labelText(String category, String fallback) {
        if ("faction_name".equals(category)) return fallback == null ? "" : fallback;
        String slug = fallback == null ? "unknown" : fallback.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return LWLang.speechKey("label." + category + "." + slug, fallback == null ? "" : fallback);
    }

    public static boolean isScouter(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && "dragonminez".equals(id.getNamespace()) && id.getPath().contains("scouter");
    }

    /** True when the player is wearing a DMZ scouter in DMZ's native head_tech slot. */
    public static boolean hasWornScouter(ServerPlayer player) {
        if (player == null) return false;
        try {
            // Use Dragon Mine Z's own Curios bridge. DMZ itself queries equipment through
            // CuriosUtil.getFirstStack(...), including head_tech for Anti-Ki Cloak/scouter logic.
            // This avoids reproducing Curios capability semantics inside Living World.
            return isScouter(CuriosUtil.getFirstStack(player, "head_tech"));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void inspect(ServerPlayer player, AmbientFighterEntity fighter, boolean scouter) {
        inspectInternal(player, fighter, scouter, false, null);
    }

    /** Combat-only live readout for Scientist-owned native Saibamen. */
    public static void inspectScientistSpecimen(ServerPlayer player, SagaSaibamanEntity specimen) {
        if (player == null || specimen == null || !specimen.isAlive()) return;
        CompoundTag pd = specimen.getPersistentData();
        if (!pd.getBoolean("LWScientistPersistentSpecimen") || !pd.hasUUID("LWScientistOwner")) return;
        int intendedBp = Math.max(1, pd.getInt("LWScientistScaledBP"));
        if (specimen.getBattlePower() != intendedBp) specimen.setBattlePower(intendedBp);
        int bp = Math.max(1, specimen.getBattlePower());
        List<String> combat = new ArrayList<>();
        combat.add("## Live Specimen Combat Data");
        combat.add("* Current Power Level: " + bp);
        combat.add(String.format(java.util.Locale.ROOT, "* Health: %.1f / %.1f", specimen.getHealth(), specimen.getMaxHealth()));
        combat.add(String.format(java.util.Locale.ROOT, "* Melee damage: %.2f", specimen.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        combat.add(String.format(java.util.Locale.ROOT, "* Ki blast damage: %.2f", specimen.getKiBlastDamage()));
        combat.add(String.format(java.util.Locale.ROOT, "* Movement speed: %.3f", specimen.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        LWNetwork.sendFighterProfile(player, new FighterProfilePacket(
                specimen.getId(), specimen.getUUID(), "Saibaman Specimen", "", "", "", "Saibaman", "Specimen",
                "", "", "", "", "", 0, "", "", false, 0, "", "", 0, 0,
                0, "", "", "", "", "", "", bp, true, false, true, false, false, false, "",
                new CompoundTag(), List.of(), List.of(), List.of(), combat, List.of(), List.of()));
    }

    /** Opens the World Menace profile from the player's own last confirmed Herobrine sighting. */
    public static void inspectWorldMenace(ServerPlayer player) {
        if (player == null) return;
        CompoundTag profile = WorldMenaceManager.knownProfile(player);
        if (profile.isEmpty()) return;
        AmbientFighterEntity snapshot = LWEntities.AMBIENT_FIGHTER.get().create(player.serverLevel());
        if (snapshot == null) return;
        snapshot.initializeFromMemory(profile);
        snapshot.configureHerobrineAppearance();
        inspectInternal(player, snapshot, false, true, null);
    }

    public static void inspectRedRibbonExperiment(ServerPlayer player) {
        if (player == null) return;
        CompoundTag profile = RedRibbonExperimentManager.knownProfile(player);
        if (profile.isEmpty()) return;
        AmbientFighterEntity snapshot = LWEntities.AMBIENT_FIGHTER.get().create(player.serverLevel());
        if (snapshot == null) return;
        snapshot.initializeFromMemory(profile);
        snapshot.getPersistentData().putBoolean(RedRibbonExperimentManager.TAG, true);
        snapshot.configureRedRibbonExperimentAppearance();
        inspectInternal(player, snapshot, false, true, null);
    }

    /** Opens a read-only profile for a deceased fighter from the world legacy archive. */
    public static void inspectFallen(ServerPlayer player, UUID recordId) {
        if (player == null || recordId == null) return;
        FighterLegacyWorldData.FallenEntry entry = FighterLegacyWorldData.get(player.serverLevel()).byRecordId(recordId);
        if (entry == null || entry.appearance().isEmpty()) return;
        inspectArchivedProfile(player, entry.appearance(), "fallen");
    }

    /** Opens a read-only profile for a Wanted dossier entry from its stored last-known profile. */
    public static void inspectWanted(ServerPlayer player, int slot) {
        if (player == null || slot <= 0) return;
        WantedWorldData.WantedProfile wanted = WantedWorldData.get(player.serverLevel()).bySlot(slot);
        if (wanted == null || wanted.profile == null || wanted.profile.isEmpty()) return;
        inspectArchivedProfile(player, wanted.profile, "wanted");
    }

    private static void inspectArchivedProfile(ServerPlayer player, CompoundTag profile, String archiveKind) {
        AmbientFighterEntity snapshot = LWEntities.AMBIENT_FIGHTER.get().create(player.serverLevel());
        if (snapshot == null) return;
        snapshot.initializeFromMemory(profile.copy());
        CompoundTag record = new CompoundTag();
        record.put("Profile", profile.copy());
        record.putString("PanelArchiveKind", archiveKind == null ? "archive" : archiveKind);
        inspectInternal(player, snapshot, false, true, record);
    }

    /** Opens the People tab's read-only profile using only the last snapshot the player actually learned. */
    public static void inspectRemembered(ServerPlayer player, UUID recordId) {
        if (player == null || recordId == null) return;
        CompoundTag record = FighterMemoryManager.rememberedRecord(player, recordId);
        if (record.isEmpty() || !record.contains("Profile", Tag.TAG_COMPOUND)) return;
        AmbientFighterEntity snapshot = LWEntities.AMBIENT_FIGHTER.get().create(player.serverLevel());
        if (snapshot == null) return;
        snapshot.initializeFromMemory(record.getCompound("Profile"));
        snapshot.bindMemory(player.getUUID(), recordId, Math.max(1, record.getInt("Encounters")),
                record.getInt("Relationship"), record.getBoolean("Rescued"));
        inspectInternal(player, snapshot, false, true, record);
    }

    private static void inspectInternal(ServerPlayer player, AmbientFighterEntity fighter, boolean scouter,
                                        boolean rememberedSnapshot, CompoundTag rememberedRecord) {
        if (player == null || fighter == null) return;
        if (!rememberedSnapshot) {
            WorldPowerScaler.observeNearbyPlayerPressure(fighter, player);
            FighterGoalManager.summary(fighter); // lazily assigns only for live fighters
            if (WorldMenaceManager.isHerobrine(fighter)) WorldMenaceManager.onInspected(player, fighter);
        }

        FighterRelationshipManager.Disposition disposition = rememberedSnapshot
                ? rememberedDisposition(fighter, rememberedRecord)
                : FighterRelationshipManager.disposition(player, fighter);
        FighterRelationshipManager.SocialStyle social = FighterRelationshipManager.socialStyle(fighter);
        boolean menace = WorldMenaceManager.isWorldMenace(fighter);
        boolean relationshipKnown = !menace && fighter.isRememberedFor(player);
        int relationship = relationshipKnown ? fighter.getMemoryRelationship() : 0;
        String relationshipStage = relationshipKnown
                ? FighterRelationshipManager.relationshipStage(relationship) : "Unknown";
        String nextStage = relationshipKnown
                ? FighterRelationshipManager.nextPositiveStage(relationship) : "Familiar";
        int nextThreshold = relationshipKnown
                ? FighterRelationshipManager.nextPositiveThreshold(relationship) : 15;

        String factionName = fighter.getFactionDisplayName().isBlank() ? fighter.getFactionId() : fighter.getFactionDisplayName();
        String factionRole = fighter.getFactionTitle();
        int factionRep = 0;
        String factionRepLabel = "No faction standing";
        WorldFaction faction = null;
        if (!rememberedSnapshot && fighter.isFactionMember() && fighter.level() instanceof ServerLevel level) {
            faction = FactionManager.byId(level, fighter.getFactionId());
            factionRep = FactionManager.getReputation(player, fighter.getFactionId());
            factionRepLabel = FactionManager.reputationLabel(factionRep);
        } else if (rememberedSnapshot && fighter.isFactionMember()) {
            factionRepLabel = "Last known member";
        }

        String activeForm = fighter.isRacialFormActive() ? fighter.getRacialFormName()
                : fighter.isKaiokenActive() ? "Kaioken x" + fighter.getKaiokenLevel()
                : fighter.isAwakened() ? "Awakened" : "Base";
        String goal = FighterGoalManager.localizedSummary(fighter, rememberedSnapshot);
        String goalProgress = rememberedSnapshot ? FighterGoalManager.progressStored(fighter) : FighterGoalManager.progress(fighter);
        String fightingStyle = FighterCombatDirector.signatureLabel(fighter);
        String techniques = FighterTechniqueManager.summary(fighter);
        if (menace) {
            factionName = RedRibbonExperimentManager.isExperiment(fighter) ? "Red Ribbon" : "Unknown";
            factionRole = ""; factionRep = 0; factionRepLabel = "";
            goal = ""; goalProgress = "";
        }

        List<FighterProfilePacket.EquipmentEntry> equipment = new ArrayList<>();
        if (!menace) {
            addEquipment(equipment, "Weapon", fighter.getItemBySlot(EquipmentSlot.MAINHAND));
            addEquipment(equipment, "Off hand", fighter.getItemBySlot(EquipmentSlot.OFFHAND));
            addEquipment(equipment, "Head", fighter.getItemBySlot(EquipmentSlot.HEAD));
            addEquipment(equipment, "Chest", fighter.getItemBySlot(EquipmentSlot.CHEST));
            addEquipment(equipment, "Legs", fighter.getItemBySlot(EquipmentSlot.LEGS));
            addEquipment(equipment, "Feet", fighter.getItemBySlot(EquipmentSlot.FEET));
        }

        boolean requestLocked = !rememberedSnapshot && FactionRequestMissionManager.isRequestActionLocked(fighter);
        boolean supplyReceiver = !rememberedSnapshot && FactionRequestManager.isAssignedSupplyReceiver(player, fighter);
        String supplyRequestLine = supplyReceiver ? FactionRequestManager.supplyProfileLine(player, fighter) : "";

        List<String> overview = buildOverview(player, fighter, disposition, social, relationshipKnown,
                relationship, relationshipStage, nextStage, nextThreshold, factionRep, factionRepLabel,
                goal, goalProgress, fightingStyle, faction, rememberedSnapshot, rememberedRecord);
        List<String> story = buildStory(player, fighter, rememberedSnapshot, rememberedRecord);
        List<String> combat = buildCombat(fighter, scouter || menace, activeForm, techniques, faction, rememberedSnapshot);
        List<String> science = !menace && !rememberedSnapshot && FighterScientistManager.isScientist(fighter)
                ? FighterScientistManager.scienceProfileLines(fighter) : java.util.List.of();
        List<String> messages = WorldMenaceManager.isHerobrine(fighter)
                ? WorldMenaceManager.dossierEvidenceLines(player, fighter)
                : menace ? java.util.List.of() : buildMessages(fighter);

        LWNetwork.sendFighterProfile(player, new FighterProfilePacket(
                rememberedSnapshot ? 0 : fighter.getId(), rememberedSnapshot && rememberedRecord != null && rememberedRecord.hasUUID("RecordId")
                        ? rememberedRecord.getUUID("RecordId") : fighter.getUUID(), fighter.getFighterName(), fighter.getLegacyTitle(),
                factionName, factionRole, menace ? "Unknown" : fighter.getRace().displayName(), menace ? "World Menace" : fighter.getRank().displayName(),
                menace ? "Unknown" : fighter.getArchetype().displayName(), menace ? "Unknown" : fighter.getAlignment().displayName(),
                menace ? "Unreadable" : fighter.getPersonality().displayName(),
                WorldMenaceManager.isWorldMenace(fighter) ? "Non-social" : social.label(),
                WorldMenaceManager.isWorldMenace(fighter) ? "World Menace dossier only" : social.connection(),
                disposition.id(), disposition.label(), rememberedSnapshot && rememberedRecord != null
                        ? rememberedRecord.getString("SeenAttitude") : FighterRelationshipManager.attitudeReason(player, fighter),
                relationshipKnown, relationship, relationshipStage, nextStage, nextThreshold,
                relationshipKnown ? Math.max(1, fighter.getMemoryEncounters()) : 0,
                factionRep, factionRepLabel, goal, goalProgress, fightingStyle, techniques,
                activeForm, FighterVisualPower.ofLong(fighter), scouter || menace, rememberedSnapshot, false,
                !rememberedSnapshot && LivingBondManager.isCompanion(player, fighter), requestLocked, supplyReceiver, supplyRequestLine, profileSnapshotForPanel(fighter, rememberedSnapshot, rememberedRecord),
                equipment, overview, story, combat, science, messages));
    }


    /**
     * Remembered People use their last-seen Profile. World Menace inspection is also a
     * remembered/read-only panel, but it is not stored as an ordinary People record; preserve
     * Herobrine's dedicated menace snapshot instead of sending an empty portrait.
     */
    private static CompoundTag profileSnapshotForPanel(AmbientFighterEntity fighter, boolean rememberedSnapshot, CompoundTag rememberedRecord) {
        if (rememberedSnapshot && rememberedRecord != null && rememberedRecord.contains("Profile", Tag.TAG_COMPOUND)) {
            CompoundTag profile = rememberedRecord.getCompound("Profile").copy();
            if (rememberedRecord.contains("PanelArchiveKind", Tag.TAG_STRING))
                profile.putString("LWPanelArchiveKind", rememberedRecord.getString("PanelArchiveKind"));
            return profile;
        }
        if (fighter == null) return new CompoundTag();

        // R9 always gives the client a detached portrait snapshot, even for a live inspection.
        // Rendering the live world entity in the GUI allowed DMZ Ki Sense's entity-id overlay to
        // leak into the portrait. Strip non-appearance history before transmission: the panel's
        // normal fields already carry the relationship/combat information it is entitled to show.
        CompoundTag profile = fighter.writeMemoryProfile();
        profile.remove("Legacy");
        profile.remove("DialogueHistory");
        profile.remove("RivalName");
        profile.remove("TrainingSessions");
        profile.remove("RacialTrainingProgress");
        if (WorldMenaceManager.isHerobrine(fighter)) profile.putBoolean(WorldMenaceManager.HEROBRINE_TAG, true);
        if (RedRibbonExperimentManager.isExperiment(fighter)) profile.putBoolean(RedRibbonExperimentManager.TAG, true);
        return profile;
    }

    private static List<String> buildMessages(AmbientFighterEntity fighter) {
        List<String> lines = new ArrayList<>();
        lines.add("## " + profileText("messages.heading", "Recent messages"));
        List<String> history = fighter.getDialogueHistory();
        if (history.isEmpty()) {
            lines.add(". " + profileText("messages.none", "No remembered messages yet."));
            return lines;
        }
        lines.add("~ " + profileText("messages.newest_first", "Newest first"));
        for (int i = history.size() - 1; i >= 0; i--) lines.add("* " + history.get(i));
        return lines;
    }

    private static List<String> buildOverview(ServerPlayer player, AmbientFighterEntity fighter,
                                              FighterRelationshipManager.Disposition disposition,
                                              FighterRelationshipManager.SocialStyle social,
                                              boolean relationshipKnown, int relationship,
                                              String relationshipStage, String nextStage, int nextThreshold,
                                              int factionRep, String factionRepLabel,
                                              String goal, String goalProgress,
                                              String fightingStyle, WorldFaction faction, boolean rememberedSnapshot,
                                              CompoundTag rememberedRecord) {
        List<String> lines = new ArrayList<>();
        if (RedRibbonExperimentManager.isExperiment(fighter)) return new ArrayList<>(RedRibbonExperimentManager.overviewLines(player, fighter));
        if (WorldMenaceManager.isHerobrine(fighter)) {
            lines.add("!! " + profileText("overview.herobrine", "HEROBRINE • WORLD MENACE"));
            lines.add("~ " + profileText("overview.hostile_anomaly", "Hostile anomaly. Approach with caution."));
            lines.addAll(WorldMenaceManager.dossierRoutineLines(player, fighter, rememberedSnapshot));
            return lines;
        }

        if (rememberedSnapshot && rememberedRecord != null) {
            long ageTicks = Math.max(0L, player.serverLevel().getGameTime() - rememberedRecord.getLong("LastSeen"));
            String age = ageTicks < 24000L ? profileText("time.today", "today")
                    : profileText("time.days_ago", "%sd ago", Math.max(1L, ageTicks / 24000L));
            lines.add("~ " + profileText("overview.last_seen", "LAST SEEN • %s • %s", age,
                    FighterMemoryManager.rememberedWhereabouts(rememberedRecord)));
            if (rememberedRecord.hasUUID("RecordId") && rememberedRecord.getString("PanelArchiveKind").isBlank()) {
                // Functional metadata consumed by FighterProfileScreen's IT footer button.
                // The client deliberately filters this line from the decluttered profile body.
                lines.add("~ Instant Transmission: " + FighterInstantTransmissionManager.menuStatus(player));
            }
        } else {
            lines.add("## " + profileText("overview.right_now", "Right Now"));
            lines.add("+ " + currentTask(fighter));
            if (FactionRequestManager.isAssignedSupplyReceiver(player, fighter)) {
                lines.add("## " + profileText("overview.active_request", "Active Request"));
                lines.add("+ " + FactionRequestManager.supplyProfileLine(player, fighter));
            }
        }

        lines.add("## " + profileText("overview.person", "Person"));
        lines.add("* " + profileText("overview.personality_archetype_v2", "%s | %s",
                labelText("personality", fighter.getPersonality().displayName()), labelText("archetype", fighter.getArchetype().displayName())));
        lines.add("* " + profileText("overview.mood", "Mood: %s", localizedMood(fighter)));
        if (FighterScientistManager.isScientist(fighter)) lines.add("* " + profileText("overview.scientist_v2", "Scientist | %s/%s active specimens", FighterScientistManager.currentMinions(fighter), FighterScientistManager.maxMinions(fighter)));

        lines.add("## " + profileText("overview.you_and_them", "You & Them"));
        if (relationshipKnown) {
            lines.add("* " + profileText("overview.relationship_v2", "%s | %s | %s", labelText("disposition", disposition.label()),
                    labelText("relationship", relationshipStage), signed(relationship)));
            String lastBondEvent = FighterMemoryManager.lastBondEvent(player, fighter);
            if (!lastBondEvent.isBlank()) lines.add(". " + labelText("bond_event", lastBondEvent));
        } else lines.add("* " + labelText("disposition", disposition.label()));
        if (fighter.isFactionMember()) {
            String factionName = fighter.getFactionDisplayName().isBlank() ? fighter.getFactionId() : fighter.getFactionDisplayName();
            lines.add("* " + profileText("overview.faction", "%s%s", labelText("faction_name", factionName),
                    fighter.getFactionTitle().isBlank() ? "" : " | " + labelText("faction_role", fighter.getFactionTitle())));
            if (!rememberedSnapshot && !factionRepLabel.isBlank()) lines.add(". " + profileText("overview.standing_v2", "Your standing: %s | %s", labelText("faction_standing", factionRepLabel), signed(factionRep)));
        }

        // Keep the decluttered Overview compact. The client exposes this factual activity history
        // only through the nested Schedule view. @schedule lines are never rendered directly.
        if (!rememberedSnapshot) {
            for (String scheduleLine : FighterDailyRoutineManager.scheduleHistoryLines(fighter))
                lines.add("@schedule|" + scheduleLine);
        }

        if (!goal.isBlank()) {
            lines.add("## " + profileText("overview.direction", "Direction"));
            lines.add("+ " + profileText("overview.goal", "%s%s", goal,
                    goalProgress.isBlank() ? "" : " | " + labelText("goal_progress", goalProgress)));
        }

        List<String> connections = FighterNpcSocialManager.profileConnections(fighter);
        if (!connections.isEmpty() || !fighter.getRivalName().isBlank()) {
            lines.add("## " + profileText("overview.connections", "Connections"));
            int shown = 0;
            for (String connection : connections) {
                if (shown++ >= 2) break;
                lines.add("* " + connection);
            }
            if (shown < 2 && !fighter.getRivalName().isBlank()) lines.add("* " + profileText("overview.rival", "Rival: %s", fighter.getRivalName()));
        }
        return lines;
    }

    private static List<String> buildStory(ServerPlayer player, AmbientFighterEntity fighter, boolean rememberedSnapshot, CompoundTag rememberedRecord) {
        if (WorldMenaceManager.isHerobrine(fighter)) return WorldMenaceManager.dossierStoryLines(player, fighter);
        if (RedRibbonExperimentManager.isExperiment(fighter)) return RedRibbonExperimentManager.storyLines(player, fighter);
        List<String> lines = new ArrayList<>();
        if (rememberedSnapshot) lines.add("~ " + profileText("story.historical", "This page is historical: it will not update until you meet this person again."));
        lines.add("## " + profileText("story.life_legacy", "Life & Legacy"));
        lines.addAll(FighterMemoryManager.biographyLines(player, fighter));

        lines.add("## " + profileText("story.battle_record", "Battle Record"));
        int wins = fighter.getLegacyData().getInt("Wins");
        int losses = fighter.getLegacyData().getInt("Losses");
        int deaths = fighter.getLegacyData().getInt("LWCombatLivesUsed");
        lines.add("* " + profileText("story.record", "Record: %s wins / %s losses • lethal defeats %s/%s",
                wins, losses, Math.min(deaths, LivingWorldConfig.npcCombatLives()), LivingWorldConfig.npcCombatLives()));
        if (fighter.getLegacyData().getInt("StrongestWinPower") > 0) {
            lines.add("* " + profileText("story.strongest_victory", "Strongest victory: %s • PL %s",
                    fighter.getLegacyData().getString("StrongestWinName"), fighter.getLegacyData().getInt("StrongestWinPower")));
        }
        if (fighter.getLegacyData().getInt("Fusions") > 0) {
            lines.add("* " + profileText("story.fusions", "Fusions: %s • last partner %s",
                    fighter.getLegacyData().getInt("Fusions"), fighter.getLegacyData().getString("LastFusionPartner")));
        }
        String lastOpponent = fighter.getLegacyData().getString("LastOpponent");
        if (!lastOpponent.isBlank()) {
            lines.add("* " + profileText("story.last_battle", "Last battle: %s vs %s",
                    labelText("battle_result", fighter.getLegacyData().getString("LastResult")), lastOpponent));
        }
        return lines;
    }

    private static List<String> buildCombat(AmbientFighterEntity fighter, boolean scouter,
                                             String activeForm, String techniques, WorldFaction faction, boolean rememberedSnapshot) {
        List<String> lines = new ArrayList<>();
        boolean menace = WorldMenaceManager.isWorldMenace(fighter);
        lines.add("## " + profileText("combat.heading", "Combat"));
        long currentPower = FighterVisualPower.ofLong(fighter);
        long permanentPower = FighterVisualPower.scaleLong(fighter.getPermanentBattlePowerLong());
        lines.add("* " + profileText("combat.current_power", "Current Power Level: %s", BattlePowerDisplay.format(currentPower)));
        if (currentPower != permanentPower)
            lines.add("* " + profileText("combat.base_power", "Base permanent Power Level: %s • temporary form/power state is included above", BattlePowerDisplay.format(permanentPower)));
        lines.add("* " + profileText("combat.stats_v2", "Health %s | Melee %s | Ki %s | Speed %s",
                String.format(java.util.Locale.ROOT, "%.1f", fighter.getMaxHealth()),
                String.format(java.util.Locale.ROOT, "%.1f", fighter.getAttributeValue(Attributes.ATTACK_DAMAGE)),
                String.format(java.util.Locale.ROOT, "%.1f", fighter.getKiBlastDamage()),
                String.format(java.util.Locale.ROOT, "%.3f", fighter.getAttributeValue(Attributes.MOVEMENT_SPEED))));
        lines.add("* " + profileText("combat.defense_v2", "Defense: %s", String.format(java.util.Locale.ROOT, "%.1f", fighter.getDefenseStat())));
        lines.add("* " + profileText("combat.style", "Style: %s", FighterCombatDirector.signatureLabel(fighter)));
        if (RedRibbonExperimentManager.isExperiment(fighter)) {
            lines.add("!! " + profileText("combat.engineered", "Engineered Red Ribbon combatant"));
            lines.add("* " + profileText("combat.calls_support", "Observed calling Red Ribbon support when badly hurt."));
            lines.add("* " + profileText("combat.x7_persistent", "Repeated sightings confirm X-7 remains an active threat after defeat."));
        } else if (WorldMenaceManager.isHerobrine(fighter)) {
            lines.add("!! " + profileText("combat.persistent_anomaly", "Persistent anomaly"));
            lines.add("* " + profileText("combat.stronger_return", "Returns after defeat with a stronger Power Level; its combat stats are recalculated from that power."));
            lines.add("* " + profileText("combat.observer", "Prefers to observe and engage from farther away than ordinary fighters."));
        } else {
            lines.add("* " + profileText("combat.form", "Form: %s • %s %s", activeForm, fighter.getRank().displayName(), fighter.getRace().displayName()));
            if (!scouter && !rememberedSnapshot) lines.add(". " + profileText("combat.scouter_required", "Exact combat readings normally require a DMZ scouter."));
        }
        if (!menace) {
            lines.add("## " + profileText("combat.techniques", "Techniques"));
            lines.add("* " + (techniques.equalsIgnoreCase("none") ? labelText("common", "None") : techniques));
            lines.add("## " + profileText("combat.equipment", "Equipment"));
            for (String line : FighterArsenalManager.detailedEquipment(fighter))
                lines.add("* " + (line.equalsIgnoreCase("none") ? labelText("common", "None") : line));
        }
        return lines;
    }

    private static String formatActivityTime(int ticks) {
        long seconds = Math.max(0L, ticks / 20L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0L) return hours + "h " + minutes + "m";
        if (minutes > 0L) return minutes + "m " + secs + "s";
        return secs + "s";
    }

    private static String currentTask(AmbientFighterEntity fighter) {
        if (fighter == null) return LWLang.speechKey("profile.task.idle", "Idle");
        if (WorldMenaceManager.isHerobrine(fighter)) {
            String menace = WorldMenaceManager.moodSummary(fighter);
            if (menace.startsWith("Watching")) return LWLang.speechKey("profile.task.watching", "Watching from a distance");
            if (menace.startsWith("Hostile Silence")) return fighter.getTarget() != null
                    ? LWLang.speechKey("profile.task.hunting_target", "Hunting %s", fighter.getTarget().getName().getString())
                    : LWLang.speechKey("profile.task.hunting", "Hunting");
            if (menace.startsWith("Restless")) return LWLang.speechKey("profile.task.roaming_unknown", "Roaming somewhere unknown");
            return LWLang.speechKey("profile.task.deliberate", "Whereabouts deliberate");
        }
        if (SparManager.isFighterInSpar(fighter)) return LWLang.speechKey("profile.task.sparring", "Sparring");
        if (fighter.isMeditating()) return LWLang.speechKey("profile.task.meditating", "Meditating • %s this session", formatActivityTime(fighter.getMeditationElapsedTicks()));
        if (fighter.isSocialPowerDisplay()) return LWLang.speechKey("profile.task.comparing_power", "Comparing power");
        String ambient = FighterAmbientActivityManager.currentActivity(fighter);
        if (!ambient.isBlank()) return labelText("activity", ambient);
        if (fighter.getTarget() != null && fighter.getTarget().isAlive())
            return LWLang.speechKey("profile.task.fighting", "Fighting %s", fighter.getTarget().getName().getString());
        if (FactionRequestMissionManager.isAssigned(fighter)) {
            String role = FactionRequestMissionManager.missionRole(fighter);
            if (FactionRequestMissionManager.ROLE_PATROL.equals(role))
                return fighter.isFlying() ? LWLang.speechKey("profile.task.air_patrol", "Patrolling by air") : LWLang.speechKey("profile.task.patrol", "On patrol");
            if (FactionRequestMissionManager.ROLE_PROTECTED.equals(role)) return LWLang.speechKey("profile.task.protected", "Under protection");
            if (FactionRequestMissionManager.ROLE_CAPTIVE.equals(role)) return LWLang.speechKey("profile.task.escorted", "Being escorted");
        }
        String plan = FighterLifeJoinManager.activeLabel(fighter);
        if (!plan.isBlank()) return plan; // locomotion (including flight) must not replace the actual task label
        if (PhysicalContinuityManager.isTransitioning(fighter)) return LWLang.speechKey("profile.task.travelling", "Travelling");
        if (FighterEnvironmentManager.isEscapingWater(fighter)) return LWLang.speechKey("profile.task.swimming_to_shore", "Swimming to shore");
        if (fighter.isIdleFlightTravelling()) return LWLang.speechKey("profile.task.flying_somewhere", "Flying somewhere");
        if (fighter.isSocialPlayerApproach()) return LWLang.speechKey("profile.task.coming_to_talk", "Coming over to talk");
        String npcSocial = FighterNpcSocialManager.currentActivityLabel(fighter);
        if (!npcSocial.isBlank()) return npcSocial;
        if (fighter.isSocialLifeActivity()) return LWLang.speechKey("profile.task.socializing", "Spending time with someone");
        String intent = FighterIntentManager.summary(fighter);
        if (!intent.isBlank()) return FighterIntentManager.localizedSummary(fighter);
        if (!fighter.getNavigation().isDone()) return LWLang.speechKey("profile.task.wandering", "Wandering");
        return LWLang.speechKey("profile.task.relaxing", "Taking it easy");
    }

    private static FighterRelationshipManager.Disposition rememberedDisposition(AmbientFighterEntity fighter, CompoundTag record) {
        if (record != null && record.contains("SeenDisposition", Tag.TAG_ANY_NUMERIC))
            return FighterRelationshipManager.Disposition.byId(record.getInt("SeenDisposition"));
        int relationship = record == null ? fighter.getMemoryRelationship() : record.getInt("Relationship");
        if (relationship <= -35) return FighterRelationshipManager.Disposition.HOSTILE;
        if (relationship <= -15) return FighterRelationshipManager.Disposition.WARY;
        if (relationship >= 85) return FighterRelationshipManager.Disposition.ALLY;
        if (relationship >= 35) return FighterRelationshipManager.Disposition.FRIENDLY;
        return switch (fighter.getAlignment()) {
            case GOOD -> FighterRelationshipManager.Disposition.FRIENDLY;
            case BAD -> FighterRelationshipManager.Disposition.WARY;
            default -> FighterRelationshipManager.Disposition.NEUTRAL;
        };
    }

    private static void addEquipment(List<FighterProfilePacket.EquipmentEntry> out, String slot, ItemStack stack) {
        ItemStack shown = stack == null || FighterArsenalManager.isTemporaryActivityProp(stack) ? ItemStack.EMPTY : stack.copy();
        out.add(new FighterProfilePacket.EquipmentEntry(slot, shown));
    }

    private static String localizedMood(AmbientFighterEntity fighter) {
        ReactiveWorldManager.Mood mood = ReactiveWorldManager.mood(fighter);
        return profileText("mood.summary", "%s - %s", labelText("mood", mood.label()),
                localizedMoodCause(ReactiveWorldManager.moodCause(fighter)));
    }

    private static String localizedMoodCause(String cause) {
        if (cause == null || cause.isBlank()) return labelText("mood_cause", "recent events");
        String[][] dynamic = {
                {" being supportive", "being_supportive"}, {" getting under their skin", "getting_under_their_skin"},
                {" falling in battle", "falling_in_battle"}, {" being defeated", "being_defeated"},
                {"'s sudden power spike", "sudden_power_spike"}, {"'s power spike", "power_spike"}
        };
        for (String[] entry : dynamic) if (cause.endsWith(entry[0]))
            return LWLang.speechKey("label.mood_cause.dynamic." + entry[1], "%s" + entry[0],
                    cause.substring(0, cause.length() - entry[0].length()));
        return labelText("mood_cause", cause);
    }

    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
}
