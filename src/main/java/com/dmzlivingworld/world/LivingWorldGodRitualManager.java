package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterRace;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.common.util.GodRitualHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bridge between Living World companions and DMZ's player God Ritual.
 * DMZ's native service deliberately accepts players only; this manager keeps
 * the same server-authoritative ritual phases while using existing companion
 * entities as the donors (no duplicate or temporary NPCs are created).
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LivingWorldGodRitualManager {
    private static final Map<UUID, Ritual> ACTIVE = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final int RITUAL_TICKS = 160;
    private LivingWorldGodRitualManager() {}

    private record Ritual(UUID player, List<UUID> donors, List<UUID> playerDonors, int ticks) {}
    private record Pending(UUID player, List<UUID> npcDonors, List<UUID> accepted, long expires) {}

    public static boolean start(ServerPlayer player, AmbientFighterEntity requested) {
        if (player == null || requested == null || !LivingBondManager.isCompanion(player, requested)) return false;
        if (ACTIVE.containsKey(player.getUUID())) return false;
        GeneralServerConfig.GodRitualConfig cfg = GodRitualHelper.config();
        StatsData data = stats(player);
        if (cfg == null || !cfg.getEnabled() || data == null || !GodRitualHelper.isSaiyan(data)) return false;
        if (!GodRitualHelper.isInAnyForm(data, cfg.getRecipientForms())) return false;
        int required = Math.max(1, cfg.getRequiredDonors());
        List<AmbientFighterEntity> helpers = new ArrayList<>();
        for (UUID id : LivingBondManager.companionIds(player)) {
            var entity = player.serverLevel().getEntity(id);
            if (!(entity instanceof AmbientFighterEntity npc) || !npc.isAlive()) continue;
            if (npc.getRace() != FighterRace.SAIYAN || npc.getRacialSkillLevel() < 1) continue;
            if (npc.distanceToSqr(player) > cfg.getRitualRadius() * cfg.getRitualRadius()) continue;
            if (Math.abs(npc.getY() - player.getY()) > cfg.getRitualVerticalRange()) continue;
            helpers.add(npc);
            if (helpers.size() >= required) break;
        }
        if (helpers.size() < required) {
            List<ServerPlayer> candidates = player.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                    player.getBoundingBox().inflate(20.0D), p -> p != player && p.isAlive() && eligiblePlayerHelper(p));
            if (candidates.isEmpty()) return false;
            PENDING.put(player.getUUID(), new Pending(player.getUUID(), helpers.stream().map(AmbientFighterEntity::getUUID).toList(), new ArrayList<>(), player.level().getGameTime() + 600L));
            for (ServerPlayer candidate : candidates) {
                candidate.sendSystemMessage(Component.translatable("dmzlivingworld.message.god_ritual.request",
                                player.getGameProfile().getName())
                        .append(Component.literal(" "))
                        .append(Component.translatable("dmzlivingworld.message.god_ritual.yes").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN)
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                        "/livingworld godritual accept " + player.getUUID()))))
                        .append(Component.literal(" "))
                        .append(Component.translatable("dmzlivingworld.message.god_ritual.no").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.RED)
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                        "/livingworld godritual decline " + player.getUUID())))));
            }
            return true;
        }
        return startWithDonors(player, helpers, List.of());
    }

    public static boolean accept(ServerPlayer helper, UUID ownerId) {
        Pending pending = PENDING.get(ownerId);
        if (pending == null || helper == null || !eligiblePlayerHelper(helper)) return false;
        ServerPlayer owner = helper.server.getPlayerList().getPlayer(ownerId);
        if (owner == null || helper.distanceToSqr(owner) > 20.0D * 20.0D) return false;
        List<UUID> accepted = new ArrayList<>(pending.accepted());
        if (!accepted.contains(helper.getUUID())) accepted.add(helper.getUUID());
        GeneralServerConfig.GodRitualConfig cfg = GodRitualHelper.config();
        int required = cfg == null ? 5 : Math.max(1, cfg.getRequiredDonors());
        int total = pending.npcDonors().size() + accepted.size();
        if (total >= required) {
            List<AmbientFighterEntity> npcs = new ArrayList<>();
            for (UUID id : pending.npcDonors()) if (owner.serverLevel().getEntity(id) instanceof AmbientFighterEntity n) npcs.add(n);
            PENDING.remove(ownerId);
            return startWithDonors(owner, npcs, accepted);
        }
        PENDING.put(ownerId, new Pending(ownerId, pending.npcDonors(), accepted, pending.expires()));
        return true;
    }

    public static boolean decline(ServerPlayer helper, UUID ownerId) {
        Pending pending = PENDING.get(ownerId);
        if (pending == null || helper == null) return false;
        ServerPlayer owner = helper.server.getPlayerList().getPlayer(ownerId);
        return owner != null && helper.distanceToSqr(owner) <= 20.0D * 20.0D;
    }

    private static boolean eligiblePlayerHelper(ServerPlayer player) {
        StatsData data = stats(player);
        GeneralServerConfig.GodRitualConfig cfg = GodRitualHelper.config();
        return data != null && cfg != null && GodRitualHelper.isSaiyan(data)
                && GodRitualHelper.isInAnyForm(data, cfg.getDonorForms());
    }

    private static boolean startWithDonors(ServerPlayer player, List<AmbientFighterEntity> helpers, List<UUID> playerDonors) {
        StatsData data = stats(player);
        if (data == null) return false;
        List<UUID> ids = new ArrayList<>();
        for (AmbientFighterEntity npc : helpers) {
            npc.setTarget(null);
            npc.getNavigation().stop();
            npc.getPersistentData().putBoolean("LWGodRitualDonor", true);
            npc.debugTransformRacial();
            ids.add(npc.getUUID());
        }
        ACTIVE.put(player.getUUID(), new Ritual(player.getUUID(), ids, new ArrayList<>(playerDonors), 0));
        data.getStatus().setGodRitualRole(Status.GOD_RITUAL_RECIPIENT);
        data.getStatus().setGodRitualAnchor(player.getUUID());
        data.getResources().setActionCharge(0);
        for (UUID id : playerDonors) {
            ServerPlayer donor = player.server.getPlayerList().getPlayer(id);
            StatsData donorData = donor == null ? null : stats(donor);
            if (donorData != null) {
                donorData.getStatus().setGodRitualRole(Status.GOD_RITUAL_DONOR);
                donorData.getStatus().setGodRitualAnchor(player.getUUID());
                sync(donor);
            }
        }
        sync(player);
        player.displayClientMessage(Component.translatable("message.dragonminez.godritual.begin"), true);
        return true;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.getServer().overworld().getGameTime();
        PENDING.entrySet().removeIf(entry -> {
            if (entry.getValue().expires() > now) return false;
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (owner != null) owner.displayClientMessage(Component.translatable("dmzlivingworld.message.god_ritual.no_helpers"), false);
            return true;
        });
        if (ACTIVE.isEmpty()) return;
        for (Ritual ritual : new ArrayList<>(ACTIVE.values())) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(ritual.player());
            if (player == null || !player.isAlive()) { cancel(event, ritual); continue; }
            List<UUID> valid = new ArrayList<>();
            for (UUID id : ritual.donors()) {
                var entity = player.serverLevel().getEntity(id);
                if (entity instanceof AmbientFighterEntity npc && npc.isAlive()) {
                    npc.setTarget(null); npc.getNavigation().stop(); npc.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); valid.add(id);
                }
            }
            for (UUID id : ritual.playerDonors()) {
                ServerPlayer donor = event.getServer().getPlayerList().getPlayer(id);
                if (donor == null || !donor.isAlive() || donor.distanceToSqr(player) > 20.0D * 20.0D) { cancel(event, ritual); continue; }
            }
            if (valid.size() != ritual.donors().size()) { cancel(event, ritual); continue; }
            int next = ritual.ticks() + 1;
            if (next < RITUAL_TICKS) {
                StatsData data = stats(player);
                if (data != null) data.getResources().setActionCharge(Math.min(99, next * 100 / RITUAL_TICKS));
                sync(player);
                ACTIVE.put(player.getUUID(), new Ritual(player.getUUID(), valid, ritual.playerDonors(), next));
                continue;
            }
            complete(player, ritual, valid);
            ACTIVE.remove(player.getUUID());
        }
    }

    private static void complete(ServerPlayer player, Ritual ritual, List<UUID> donors) {
        StatsData data = stats(player);
        GeneralServerConfig.GodRitualConfig cfg = GodRitualHelper.config();
        var form = GodRitualHelper.formData();
        if (data != null && cfg != null && form != null) {
            data.getCharacter().setActiveForm(cfg.getGroupName(), form.getName());
            data.getStatus().setGodRitualTicks(cfg.getDurationTicks());
            data.getStatus().setGodRitualGlowStart(player.level().getGameTime());
            data.getStatus().setGodRitualRole(Status.GOD_RITUAL_NONE);
            data.getStatus().setGodRitualAnchor(null);
            data.getResources().setActionCharge(0);
            player.refreshDimensions();
            player.displayClientMessage(Component.translatable("message.dragonminez.godritual.transformed"), true);
            sync(player);
        }
        for (UUID id : donors) {
            var entity = player.serverLevel().getEntity(id);
            if (entity instanceof AmbientFighterEntity npc) {
                npc.getPersistentData().remove("LWGodRitualDonor");
                if (npc.isRacialFormActive()) npc.stopRacialForm();
            }
        }
        for (UUID id : ritual.playerDonors()) {
            ServerPlayer donor = player.server.getPlayerList().getPlayer(id);
            if (donor != null) { StatsData donorData = stats(donor); if (donorData != null) { donorData.getStatus().setGodRitualRole(Status.GOD_RITUAL_NONE); donorData.getStatus().setGodRitualAnchor(null); sync(donor); } }
        }
    }

    private static void cancel(TickEvent.ServerTickEvent event, Ritual ritual) {
        ServerPlayer player = event.getServer().getPlayerList().getPlayer(ritual.player());
        if (player != null) for (UUID id : ritual.donors()) {
            var entity = player.serverLevel().getEntity(id);
            if (entity instanceof AmbientFighterEntity npc) {
                npc.getPersistentData().remove("LWGodRitualDonor");
                if (npc.isRacialFormActive()) npc.stopRacialForm();
            }
        }
        ACTIVE.remove(ritual.player());
    }

    private static void sync(ServerPlayer player) {
        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
    }

    private static StatsData stats(Player player) {
        return StatsProvider.get(StatsCapability.INSTANCE, player).resolve().orElse(null);
    }
}
