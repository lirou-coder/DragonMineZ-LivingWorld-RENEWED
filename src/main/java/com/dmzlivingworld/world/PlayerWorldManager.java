package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dmzlivingworld.network.FighterDispositionSnapshotPacket;
import com.dmzlivingworld.network.LWNetwork;
import com.dragonminez.common.stats.StatsCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Player-facing memory: discovered factions and information learned in-world. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerWorldManager {
    private static final String ROOT = "DMZLivingWorldPlayerWorld";
    private static final String DISCOVERED = "DiscoveredFactions";
    private static final String RUMORS = "Rumors";
    /** Highest unsuppressed/form-inclusive player BP Living World has actually observed. */
    private static final String PEAK_PROGRESSION_BP = "PeakProgressionBattlePower";

    private PlayerWorldManager() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) return;
        long now = level.getServer().overworld().getGameTime();
        PeacekeeperManager.tickPlayer(player);
        // Record the player's real progression high-water mark independently of any NPC. This
        // lets a short-lived transformation (for example Super Saiyan) remain meaningful to future
        // catch-up pressure without remotely touching or revealing any fighter's current profile.
        if (Math.floorMod((int)(now % 20L), 20) == Math.floorMod(player.getUUID().hashCode(), 20))
            recordProgressionPeak(player);

        // One compact player-specific snapshot per second keeps the world nametag intent icon
        // accurate without abusing synchronized entity data (which would be wrong in multiplayer).
        int dispositionOffset = Math.floorMod(player.getUUID().hashCode(), 20);
        if (Math.floorMod((int)(now % 20L), 20) == dispositionOffset) {
            java.util.List<FighterDispositionSnapshotPacket.Entry> entries = level.getEntitiesOfClass(
                            AmbientFighterEntity.class, player.getBoundingBox().inflate(64.0D),
                            f -> f.isAlive() && !f.isInvisible())
                    .stream().limit(96)
                    .map(f -> new FighterDispositionSnapshotPacket.Entry(f.getId(),
                            FighterRelationshipManager.disposition(player, f).id(),
                            FighterRelationshipManager.relationshipOrUnknown(player, f)))
                    .toList();
            LWNetwork.sendDispositionSnapshot(player, new FighterDispositionSnapshotPacket(entries));
        }
        int offset = Math.floorMod(player.getUUID().hashCode(), 100);
        if (Math.floorMod((int)(now % 100L), 100) == offset) {
            for (AmbientFighterEntity fighter : level.getEntitiesOfClass(AmbientFighterEntity.class,
                    player.getBoundingBox().inflate(96.0D), f -> f.isAlive() && f.isFactionMember())) {
                WorldFaction faction = FactionManager.byId(level, fighter.getFactionId());
                if (faction != null) discoverFaction(player, faction);
            }
        }
        // Rumors are intentionally slow: at most one meaningful whisper every ~2 MC days.
        CompoundTag root = root(player);
        long next = root.getLong("NextRumor");
        if (now >= next && Math.floorMod(now + player.getUUID().hashCode(), 1200L) == 0L) {
            root.putLong("NextRumor", now + 36000L + player.getRandom().nextInt(24001));
            tryLearnRumor(player, level);
            save(player, root);
        }
    }

    public static void discoverFaction(ServerPlayer player, WorldFaction faction) {
        if (player == null || faction == null) return;
        CompoundTag root = root(player);
        ListTag list = root.getList(DISCOVERED, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) if (faction.id().equals(list.getString(i))) return;
        list.add(StringTag.valueOf(faction.id()));
        root.put(DISCOVERED, list);
        save(player, root);
    }

    public static boolean knowsFaction(ServerPlayer player, WorldFaction faction) {
        if (player == null || faction == null) return false;
        ListTag list = root(player).getList(DISCOVERED, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) if (faction.id().equals(list.getString(i))) return true;
        return false;
    }

    public static double playerBattlePower(ServerPlayer player) {
        if (player == null) return 1.0D;
        final double[] out = {1.0D};
        player.getCapability(StatsCapability.INSTANCE).ifPresent(stats -> out[0] = Math.max(1.0D, stats.getBattlePowerExact()));
        return out[0];
    }

    /**
     * Stable progression benchmark used by Living World's long-term NPC relevance math.
     * Dragon Mine Z multiplies displayed/current BP by Power Release, so using the sensed
     * value directly made a deliberately suppressed player look artificially weak to training
     * and progression systems. Restore the equivalent 100%-release BP without mutating DMZ
     * state. Forms/bonuses remain part of the benchmark; only deliberate release suppression
     * is removed.
     */
    public static double playerProgressionBattlePower(ServerPlayer player) {
        if (player == null) return 1.0D;
        recordProgressionPeak(player);
        double peak = root(player).getDouble(PEAK_PROGRESSION_BP);
        return Double.isFinite(peak) && peak > 0.0D ? Math.max(1.0D, peak) : playerBattlePower(player);
    }

    /** Current 100%-release equivalent BP, including real active forms/bonuses but not suppression. */
    private static double currentUnsuppressedProgressionBattlePower(ServerPlayer player) {
        if (player == null) return 1.0D;
        final double[] out = {1.0D};
        player.getCapability(StatsCapability.INSTANCE).ifPresent(stats -> {
            double sensed = Math.max(0.0D, stats.getBattlePowerExact());
            double release = Math.max(0.0D, stats.getResources().getPowerRelease());
            double restored = release > 0.001D ? sensed * (100.0D / release) : sensed;
            out[0] = Double.isFinite(restored) && restored > 0.0D ? restored : Math.max(1.0D, sensed);
        });
        return Math.max(1.0D, out[0]);
    }

    /** Monotonic, persistent high-water mark. It never lowers when the player de-transforms. */
    public static void recordProgressionPeak(ServerPlayer player) {
        if (player == null) return;
        double current = currentUnsuppressedProgressionBattlePower(player);
        CompoundTag root = root(player);
        // Migrate R18's direct transient benchmark if it happens to be higher on an upgraded save.
        double legacyDirect = player.getPersistentData().getDouble("LWProgressionBattlePower");
        double previous = Math.max(root.getDouble(PEAK_PROGRESSION_BP), legacyDirect);
        double peak = Math.max(previous, current);
        if (Double.isFinite(peak) && peak > 0.0D) {
            root.putDouble(PEAK_PROGRESSION_BP, peak);
            save(player, root);
        }
        player.getPersistentData().remove("LWProgressionBattlePower");
    }

    public static double highestRecordedProgressionBattlePower(ServerPlayer player) {
        return playerProgressionBattlePower(player);
    }

    /**
     * Reactions are derived from facts that actually happened: raw power, remembered
     * personal encounters, faction standing, and wanted-threat history. There is no global
     * Fame/Heroic/Infamy meter.
     */
    public static boolean shouldFearPlayer(AmbientFighterEntity fighter, ServerPlayer player) {
        if (fighter == null || player == null) return false;
        if (fighter.getPersonality() == FighterPersonality.PROUD || fighter.getPersonality() == FighterPersonality.AGGRESSIVE) return false;

        double ratio = playerBattlePower(player) / Math.max(1.0D, fighter.getBattlePower());
        if (ratio < 2.8D) return false;

        boolean personalHistory = fighter.isRememberedFor(player)
                && (fighter.getMemoryEncounters() >= 2 || fighter.getLegacyData().getInt("PlayerLosses") > 0
                || fighter.getMemoryRelationship() <= -35);
        boolean factionHistory = fighter.isFactionMember()
                && FactionManager.getReputation(player, fighter.getFactionId()) <= -70;

        // An absurd power gap is directly observable even without a reputation system.
        boolean overwhelmingPresence = ratio >= 6.0D;
        int wantedEliminations = wantedEliminations(player);
        if (!overwhelmingPresence && wantedEliminations <= 0 && !personalHistory && !factionHistory) return false;

        int chance = 44;
        if (overwhelmingPresence) chance += 26;
        chance += Math.min(12, wantedEliminations * 3);
        if (personalHistory) chance += 12;
        if (factionHistory) chance += 10;
        long day = player.serverLevel().getServer().overworld().getGameTime() / 24000L;
        long stable = FactionWorldData.mix(fighter.getUUID().getMostSignificantBits()
                ^ player.getUUID().getLeastSignificantBits() ^ day);
        return Math.floorMod(stable, 100L) < Math.min(92, chance);
    }


    public static void recordWantedElimination(ServerPlayer player) {
        if (player == null) return;
        CompoundTag root = root(player);
        root.putInt("WantedEliminations", Math.max(0, root.getInt("WantedEliminations")) + 1);
        save(player, root);
    }

    public static int wantedEliminations(ServerPlayer player) {
        return player == null ? 0 : Math.max(0, root(player).getInt("WantedEliminations"));
    }

    public static List<String> rumors(ServerPlayer player) {
        ListTag list = root(player).getList(RUMORS, Tag.TAG_STRING);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
        return out;
    }

    private static void tryLearnRumor(ServerPlayer player, ServerLevel level) {
        // Rumors only travel through a friendly local presence.
        AmbientFighterEntity source = level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(72.0D), f -> f.isAlive() && f.isFactionMember()
                        && FactionManager.getReputation(player, f.getFactionId()) >= FactionManager.FRIENDLY_REP)
                .stream().findAny().orElse(null);
        if (source == null) return;
        FactionWorldData data = FactionWorldData.get(level);
        long now = level.getServer().overworld().getGameTime();
        List<WorldFaction> candidates = data.activeFactions().stream().filter(f -> f.realm() == LivingWorldDimensions.realm(level)).toList();
        if (candidates.isEmpty()) return;
        WorldFaction faction = candidates.get(player.getRandom().nextInt(candidates.size()));
        discoverFaction(player, faction);
        String rumor;
        List<WorldFaction> wars = data.warEnemies(faction, now);
        if (!wars.isEmpty()) rumor = "Word is spreading: " + faction.name() + " is at war with " + wars.get(0).name() + ".";
        else {
            List<String> history = data.history(faction);
            rumor = history.isEmpty() ? "People have been talking about " + faction.name() + " on " + faction.realm().displayName() + "."
                    : "Rumor: " + history.get(history.size() - 1);
        }
        addRumor(player, rumor);
        if (source.getSpeech().isEmpty()) source.speak("I heard something you might want to know.", 64);
    }

    private static void addRumor(ServerPlayer player, String rumor) {
        CompoundTag root = root(player);
        ListTag list = root.getList(RUMORS, Tag.TAG_STRING);
        if (!list.isEmpty() && list.getString(list.size() - 1).equals(rumor)) return;
        list.add(StringTag.valueOf(rumor));
        while (list.size() > 8) list.remove(0);
        root.put(RUMORS, list);
        save(player, root);
    }

    private static CompoundTag root(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) persistent.put(ROOT, new CompoundTag());
        CompoundTag root = persistent.getCompound(ROOT);
        // 1.5 migration: the old abstract RP meters no longer exist. Purge stale values
        // so an upgraded save cannot accidentally revive them through an older UI path.
        root.remove("Heroic");
        root.remove("Infamy");
        root.remove("Fame");
        if (!root.contains(DISCOVERED, Tag.TAG_LIST)) root.put(DISCOVERED, new ListTag());
        if (!root.contains(RUMORS, Tag.TAG_LIST)) root.put(RUMORS, new ListTag());
        return root;
    }

    private static void save(ServerPlayer player, CompoundTag root) { player.getPersistentData().put(ROOT, root); }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer old) || !(event.getEntity() instanceof ServerPlayer copy)) return;
        if (old.getPersistentData().contains(ROOT, Tag.TAG_COMPOUND))
            copy.getPersistentData().put(ROOT, old.getPersistentData().getCompound(ROOT).copy());
    }
}
