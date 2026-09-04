package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.LWEntities;
import com.dmzlivingworld.config.LivingWorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.*;

/** Persistent distinction between dead fighters and people permanently removed by assimilation. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterAfterlifeManager {
    public static final String DEAD_SOUL = "LWDeadSoul";
    private static final ResourceKey<Level> OTHERWORLD = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("dragonminez", "otherworld"));
    private static final ResourceKey<Level> NETHER = Level.NETHER;
    private static volatile Method dmzPlusHellMethod;
    private static volatile boolean dmzPlusHellLookupComplete;

    private FighterAfterlifeManager() {}

    public static void markAssimilated(AmbientFighterEntity fighter) {
        if (!(fighter.level() instanceof ServerLevel level)) return;
        if (WorldMenaceManager.isWorldMenace(fighter)) return;
        Data data = Data.get(level);
        UUID id = recordId(fighter);
        data.dead.remove(id);
        data.assimilated.add(id);
        data.setDirty();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AmbientFighterEntity fighter) || !(fighter.level() instanceof ServerLevel level)) return;
        if (WorldMenaceManager.isWorldMenace(fighter) || fighter.getPersistentData().getBoolean(NamekAssimilationCompat.ASSIMILATED)) return;
        if (fighter.getPersistentData().getBoolean(DEAD_SOUL)) return;
        if (!fighter.getPersistentData().getBoolean("LWDeathArchived")) return;
        Data data = Data.get(level);
        UUID id = recordId(fighter);
        if (data.assimilated.contains(id)) return;
        CompoundTag entry = new CompoundTag();
        entry.putUUID("Id", id);
        entry.putInt("Alignment", fighter.getAlignment().id());
        entry.put("Profile", fighter.writeMemoryProfile());
        data.dead.put(id, entry);
        data.trimDead();
        data.setDirty();
        FighterWishIntegration.refresh(level.getServer());
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        Data data = Data.get(server.overworld());
        for (UUID forgotten : data.trimDead()) discardSoul(server, forgotten);
        boolean hellEnabled = dmzPlusHellEnabled();
        SoulSnapshot souls = scanSouls(server);

        if (server.getTickCount() % 200 == 0) {
            for (CompoundTag entry : List.copyOf(data.dead.values())) {
                FighterAlignment alignment = FighterAlignment.byId(entry.getInt("Alignment"));
                if (alignment == FighterAlignment.BAD) continue;
                if (souls.visibleGood >= 5) continue;
                if (ensureGoodSoul(server, entry, souls)) souls.visibleGood++;
            }
        }

        if (hellEnabled) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tryNaturalHellOpportunity(player, data, souls);
            }
        }
    }

    private static boolean ensureGoodSoul(MinecraftServer server, CompoundTag entry, SoulSnapshot souls) {
        if (isWorldMenaceRecord(entry)) return false;
        FighterAlignment alignment = FighterAlignment.byId(entry.getInt("Alignment"));
        if (alignment == FighterAlignment.BAD) return false;
        ServerLevel destination = server.getLevel(OTHERWORLD);
        if (destination == null) return false;
        BlockPos center = souls.kaiosama;
        if (center == null) return false; // Never violate the requested 20-block Kaiosama boundary.
        UUID id = entry.getUUID("Id");
        if (destination == null || souls.ids(destination).contains(id)) return false;
        AmbientFighterEntity soul = LWEntities.AMBIENT_FIGHTER.get().create(destination);
        if (soul == null) return false;
        soul.initializeFromMemory(entry.getCompound("Profile"));
        soul.setDeadSoul(true);
        soul.getPersistentData().putUUID("LWAfterlifeRecord", entry.getUUID("Id"));
        soul.setPersistenceRequired();
        int dx = soul.getRandom().nextInt(31) - 15;
        int dz = soul.getRandom().nextInt(31) - 15;
        BlockPos at = destination.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                center.offset(dx, 0, dz));
        soul.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, soul.getRandom().nextFloat() * 360F, 0F);
        destination.addFreshEntity(soul);
        souls.ids(destination).add(id);
        return true;
    }

    private static void tryNaturalHellOpportunity(ServerPlayer player, Data data, SoulSnapshot souls) {
        if (player == null || player.isSpectator() || player.isCreative()
                || !(player.level() instanceof ServerLevel level) || !level.dimension().equals(NETHER)) return;

        int checkInterval = LivingWorldConfig.naturalCheckIntervalTicks();
        long gameTime = level.getGameTime();
        int stagger = Math.floorMod(player.getUUID().hashCode(), checkInterval);
        if (Math.floorMod(gameTime + stagger, checkInterval) != 0) return;

        int attempts = LivingWorldConfig.naturalActivityAttempts();
        if (attempts <= 0) return;
        RandomSource random = player.getRandom();
        for (int opportunity = 0; opportunity < attempts; opportunity++) {
            if (random.nextDouble() >= LivingWorldConfig.naturalSpawnRoll()) continue;
            if (trySpawnHellSoulNearPlayer(player, level, data, souls, random)) return;
        }
    }

    private static boolean trySpawnHellSoulNearPlayer(ServerPlayer player, ServerLevel level, Data data,
                                                      SoulSnapshot souls, RandomSource random) {
        int localCap = LivingWorldConfig.nearbyFighterCap();
        int hostileCap = LivingWorldConfig.nearbyHostileCap();
        if (localCap <= 0 || hostileCap <= 0) return false;

        List<AmbientFighterEntity> nearby = level.getEntitiesOfClass(
                AmbientFighterEntity.class,
                player.getBoundingBox().inflate(160.0D, 512.0D, 160.0D)
        );
        if (nearby.size() >= localCap) return false;
        long nearbyHostile = nearby.stream().filter(f -> f.getAlignment() == FighterAlignment.BAD).count();
        if (nearbyHostile >= hostileCap) return false;

        List<CompoundTag> candidates = data.dead.values().stream()
                .filter(entry -> !isWorldMenaceRecord(entry))
                .filter(entry -> FighterAlignment.byId(entry.getInt("Alignment")) == FighterAlignment.BAD)
                .filter(entry -> entry.hasUUID("Id") && !souls.ids(level).contains(entry.getUUID("Id")))
                .toList();
        if (candidates.isEmpty()) return false;

        CompoundTag entry = candidates.get(random.nextInt(candidates.size()));
        BlockPos pos = AmbientFighterSpawner.findSafeGroundAroundSeparated(
                level, player.blockPosition(), random, 38, 72, 28, 16.0D);
        if (pos == null) return false;

        UUID id = entry.getUUID("Id");
        if (!level.getEntitiesOfClass(AmbientFighterEntity.class,
                new AABB(pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1.0D, pos.getY() + 2.0D, pos.getZ() + 1.0D)
                        .inflate(160.0D, 512.0D, 160.0D),
                f -> f.isDeadSoul() && f.getPersistentData().hasUUID("LWAfterlifeRecord")
                        && id.equals(f.getPersistentData().getUUID("LWAfterlifeRecord"))).isEmpty()) return false;

        AmbientFighterEntity soul = LWEntities.AMBIENT_FIGHTER.get().create(level);
        if (soul == null) return false;
        soul.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360F, 0F);
        soul.initializeFromMemory(entry.getCompound("Profile"));
        soul.setDeadSoul(true);
        soul.getPersistentData().putUUID("LWAfterlifeRecord", id);
        soul.setPersistenceRequired();
        if (!level.noCollision(soul)) return false;
        if (!level.addFreshEntity(soul)) return false;
        souls.ids(level).add(id);
        return true;
    }

    private static SoulSnapshot scanSouls(MinecraftServer server) {
        SoulSnapshot snapshot = new SoulSnapshot();
        for (ServerLevel level : server.getAllLevels()) {
            Set<UUID> ids = snapshot.ids(level);
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof AmbientFighterEntity fighter && fighter.isDeadSoul()) {
                    if (WorldMenaceManager.isWorldMenace(fighter)) {
                        fighter.discard();
                        continue;
                    }
                    if (level.dimension().equals(OTHERWORLD)) snapshot.visibleGood++;
                    if (fighter.getPersistentData().hasUUID("LWAfterlifeRecord"))
                        ids.add(fighter.getPersistentData().getUUID("LWAfterlifeRecord"));
                }
                if (snapshot.kaiosama == null && level.dimension().equals(OTHERWORLD)) {
                    ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
                    if (key != null && (key.getPath().contains("kaiosama") || key.getPath().contains("king_kai")))
                        snapshot.kaiosama = entity.blockPosition();
                }
            }
        }
        return snapshot;
    }

    private static boolean dmzPlusHellEnabled() {
        if (!ModList.get().isLoaded("dmzplus")) return false;
        try {
            Method method = dmzPlusHellMethod();
            return method != null && Boolean.TRUE.equals(method.invoke(null));
        } catch (ReflectiveOperationException ignored) { return false; }
    }

    public static boolean canUseNetherHell(AmbientFighterEntity fighter, ResourceKey<Level> dimension) {
        return fighter != null
                && dimension != null
                && dimension.equals(NETHER)
                && fighter.isDeadSoul()
                && fighter.getAlignment() == FighterAlignment.BAD
                && dmzPlusHellEnabled();
    }

    private static Method dmzPlusHellMethod() {
        if (!dmzPlusHellLookupComplete) {
            synchronized (FighterAfterlifeManager.class) {
                if (!dmzPlusHellLookupComplete) {
                    try {
                        dmzPlusHellMethod = Class.forName("com.kiziro.dmzplus.config.DMZPlusConfig")
                                .getMethod("hellEnabled");
                    } catch (ReflectiveOperationException ignored) {
                        dmzPlusHellMethod = null;
                    }
                    dmzPlusHellLookupComplete = true;
                }
            }
        }
        return dmzPlusHellMethod;
    }

    private static UUID recordId(AmbientFighterEntity fighter) {
        return fighter.getMemoryRecordId() == null ? fighter.getUUID() : fighter.getMemoryRecordId();
    }

    public static List<DeadFighter> deadFighters(ServerLevel level) {
        Data data = Data.get(level);
        List<DeadFighter> result = new ArrayList<>();
        for (CompoundTag entry : data.dead.values()) {
            if (isWorldMenaceRecord(entry)) continue;
            CompoundTag profile = entry.getCompound("Profile");
            String name = profile.getString("Name");
            if (name.isBlank()) name = "Unknown Fighter";
            result.add(new DeadFighter(entry.getUUID("Id"), name, FighterAlignment.byId(entry.getInt("Alignment"))));
        }
        result.sort(Comparator.comparing(DeadFighter::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public static void revive(ServerPlayer player, UUID wanted, String mode) {
        if (player == null) return;
        Data data = Data.get(player.serverLevel());
        List<UUID> selected = new ArrayList<>();
        for (Map.Entry<UUID, CompoundTag> entry : data.dead.entrySet()) {
            if (isWorldMenaceRecord(entry.getValue())) continue;
            FighterAlignment alignment = FighterAlignment.byId(entry.getValue().getInt("Alignment"));
            boolean matches = "ALL_GOOD".equals(mode) ? alignment != FighterAlignment.BAD
                    : "ALL_EVIL".equals(mode) ? alignment == FighterAlignment.BAD
                    : wanted != null && wanted.equals(entry.getKey());
            if (matches) selected.add(entry.getKey());
        }
        if (selected.isEmpty()) return;
        for (UUID id : selected) {
            CompoundTag record = data.dead.remove(id);
            FighterLegacyWorldData.get(player.serverLevel()).reviveRecord(id);
            discardSoul(player.getServer(), id);
            if (record != null) spawnRevived(player, record);
        }
        data.setDirty();
        FighterWishIntegration.refresh(player.getServer());
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(selected.size() == 1
                ? "[Living World] The fighter has returned to life."
                : "[Living World] " + selected.size() + " fighters have returned to life."), false);
    }

    private static void spawnRevived(ServerPlayer player, CompoundTag record) {
        ServerLevel level = LivingWorldDimensions.isSupported(player.serverLevel())
                ? player.serverLevel() : player.getServer().overworld();
        AmbientFighterEntity fighter = LWEntities.AMBIENT_FIGHTER.get().create(level);
        if (fighter == null) return;
        fighter.initializeFromMemory(record.getCompound("Profile"));
        fighter.setDeadSoul(false);
        fighter.getPersistentData().remove("LWAfterlifeRecord");
        fighter.setPersistenceRequired();
        BlockPos center = level == player.serverLevel() ? player.blockPosition() : level.getSharedSpawnPos();
        int radius = 4 + Math.floorMod(record.getUUID("Id").hashCode(), 7);
        double angle = Math.floorMod(record.getUUID("Id").hashCode() * 31, 360) * Math.PI / 180D;
        BlockPos horizontal = center.offset((int)Math.round(Math.cos(angle) * radius), 0,
                (int)Math.round(Math.sin(angle) * radius));
        BlockPos at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, horizontal);
        fighter.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, fighter.getRandom().nextFloat() * 360F, 0F);
        if (level.addFreshEntity(fighter)) FighterMemoryManager.restoreRevivedBond(fighter);
    }

    private static void discardSoul(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof AmbientFighterEntity fighter
                        && fighter.getPersistentData().hasUUID("LWAfterlifeRecord")
                        && id.equals(fighter.getPersistentData().getUUID("LWAfterlifeRecord"))) fighter.discard();
            }
        }
    }

    private static boolean isWorldMenaceRecord(CompoundTag entry) {
        return entry != null && WorldMenaceManager.isWorldMenaceProfile(entry.getCompound("Profile"));
    }

    private static final class SoulSnapshot {
        private final Map<ResourceKey<Level>, Set<UUID>> idsByDimension = new HashMap<>();
        private int visibleGood;
        private BlockPos kaiosama;

        private Set<UUID> ids(ServerLevel level) {
            return idsByDimension.computeIfAbsent(level.dimension(), ignored -> new HashSet<>());
        }
    }

    public record DeadFighter(UUID id, String name, FighterAlignment alignment) {}

        public static final class Data extends SavedData {
        private static final String NAME = "dmzlivingworld_afterlife_v1";
        private final Map<UUID, CompoundTag> dead = new LinkedHashMap<>();
        private final Set<UUID> assimilated = new HashSet<>();

        static Data get(ServerLevel level) {
            Data data = level.getServer().overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, NAME);
            if (data.dead.entrySet().removeIf(entry -> isWorldMenaceRecord(entry.getValue()))) data.setDirty();
            return data;
        }
        static Data load(CompoundTag root) {
            Data data = new Data();
            for (Tag raw : root.getList("Dead", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag)raw;
                if (entry.hasUUID("Id") && !isWorldMenaceRecord(entry)) data.dead.put(entry.getUUID("Id"), entry.copy());
            }
            for (Tag raw : root.getList("Assimilated", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag)raw;
                if (entry.hasUUID("Id")) data.assimilated.add(entry.getUUID("Id"));
            }
            data.trimDead();
            return data;
        }
        private List<UUID> trimDead() {
            List<UUID> removed = new ArrayList<>();
            int cap = LivingWorldConfig.maxRememberedDeadFighters();
            while (dead.size() > cap) {
                Iterator<UUID> iterator = dead.keySet().iterator();
                if (!iterator.hasNext()) break;
                UUID id = iterator.next();
                iterator.remove();
                removed.add(id);
            }
            if (!removed.isEmpty()) setDirty();
            return removed;
        }
        @Override public CompoundTag save(CompoundTag root) {
            ListTag deadList = new ListTag(); this.dead.values().forEach(e -> deadList.add(e.copy())); root.put("Dead", deadList);
            ListTag assimilatedList = new ListTag();
            for (UUID id : assimilated) { CompoundTag e = new CompoundTag(); e.putUUID("Id", id); assimilatedList.add(e); }
            root.put("Assimilated", assimilatedList);
            return root;
        }
    }
}
