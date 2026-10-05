package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterPersonality;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Tracks players who interrupt an otherwise lethal encounter and rewards the rescued fighter. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterRescueManager {
    private static final String THREAT = "LWRescueThreat";
    private static final String LAST_DAMAGE = "LWRescueLastDamage";
    private static final String SAVIOR = "LWRescueSavior";
    private static final String ARMED = "LWRescueArmed";
    private static final long QUIET_TICKS = 200L;
    private static final long COOLDOWN_TICKS = 200L;

    private FighterRescueManager() {}

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entity source = event.getSource().getEntity();
        if (!(source instanceof LivingEntity attacker) || !attacker.isAlive()) return;
        LivingEntity victim = event.getEntity();

        // A player striking the aggressor arms every nearby endangered fighter for that player.
        if (attacker instanceof ServerPlayer savior && !(victim instanceof ServerPlayer same && same == savior))
            armNearby(victim, savior);

        if (!(victim instanceof AmbientFighterEntity fighter) || !isLethalThreat(attacker, fighter)) return;
        long now = fighter.level().getGameTime();
        if (fighter.getPersistentData().getLong(LAST_DAMAGE) > 0L)
            fighter.getPersistentData().putLong(LAST_DAMAGE, now);
        else {
            fighter.getPersistentData().putUUID(THREAT, attacker.getUUID());
            fighter.getPersistentData().putLong(LAST_DAMAGE, now);
            fighter.getPersistentData().putBoolean(ARMED, false);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;
        UUID deadId = dead.getUUID();
        for (AmbientFighterEntity fighter : level.getEntitiesOfClass(AmbientFighterEntity.class,
                dead.getBoundingBox().inflate(64.0D), f -> f.isAlive())) {
            if (fighter.getPersistentData().hasUUID(THREAT)
                    && deadId.equals(fighter.getPersistentData().getUUID(THREAT))) {
                fighter.getPersistentData().putLong(LAST_DAMAGE, level.getGameTime());
                fighter.getPersistentData().putBoolean(ARMED, true);
            }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                for (AmbientFighterEntity fighter : level.getEntitiesOfClass(AmbientFighterEntity.class,
                        player.getBoundingBox().inflate(96.0D), f -> f.isAlive())) finishIfQuiet(fighter, level);
            }
        }
    }

    private static void armNearby(LivingEntity aggressor, ServerPlayer savior) {
        if (!(aggressor.level() instanceof ServerLevel level)) return;
        for (AmbientFighterEntity fighter : level.getEntitiesOfClass(AmbientFighterEntity.class,
                aggressor.getBoundingBox().inflate(64.0D), f -> f.isAlive())) {
            var tag = fighter.getPersistentData();
            if (!tag.hasUUID(THREAT) || !aggressor.getUUID().equals(tag.getUUID(THREAT))) continue;
            if (aggressor instanceof ServerPlayer && aggressor.getUUID().equals(savior.getUUID())) continue;
            tag.putUUID(SAVIOR, savior.getUUID());
            tag.putBoolean(ARMED, true);
            tag.putLong(LAST_DAMAGE, level.getGameTime());
        }
    }

    private static void finishIfQuiet(AmbientFighterEntity fighter, ServerLevel level) {
        var tag = fighter.getPersistentData();
        if (!tag.hasUUID(THREAT) || !tag.getBoolean(ARMED)) return;
        long now = level.getGameTime();
        if (now - tag.getLong(LAST_DAMAGE) < QUIET_TICKS) return;
        // Killing one attacker is not enough if another hostile is still actively targeting
        // the same fighter.  Keep the ten-second quiet window running until all threats stop.
        if (hasActiveThreat(fighter, level)) {
            tag.putLong(LAST_DAMAGE, now);
            return;
        }
        if (!tag.hasUUID(SAVIOR) || now < tag.getLong("LWRescueCooldownUntil")) {
            clear(fighter);
            return;
        }
        ServerPlayer savior = level.getServer().getPlayerList().getPlayer(tag.getUUID(SAVIOR));
        if (savior == null) { clear(fighter); return; }
        double ratio = Math.max(0.0D, Math.min(1.0D, fighter.getHealth() / Math.max(1.0D, fighter.getMaxHealth())));
        int points = Math.max(1, Math.min(20, (int)Math.ceil((1.0D - ratio) * 20.0D)));
        FighterMemoryManager.strengthenRelationship(savior, fighter, points,
                FighterRelationshipManager.BondEvent.RESCUE, "Saved this fighter from a deadly encounter");
        fighter.getPersistentData().putLong("LWRescueCooldownUntil", now + COOLDOWN_TICKS);
        fighter.speakKey("dialogue.rescue." + personality(fighter) + "." + alignment(fighter), 72);
        savior.displayClientMessage(Component.translatable("dmzlivingworld.message.fighter_rescued", fighter.getFighterName())
                .withStyle(ChatFormatting.GREEN), false);
        clear(fighter);
    }

    private static boolean hasActiveThreat(AmbientFighterEntity fighter, ServerLevel level) {
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                fighter.getBoundingBox().inflate(64.0D), e -> e != fighter && e.isAlive())) {
            if (other instanceof AmbientFighterEntity otherFighter
                    && otherFighter.getAlignment() != FighterAlignment.BAD) continue;
            if (other instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == fighter)
                return true;
        }
        return false;
    }

    private static boolean isLethalThreat(LivingEntity attacker, AmbientFighterEntity victim) {
        if (attacker instanceof AmbientFighterEntity fighter)
            return fighter.getAlignment() == FighterAlignment.BAD && fighter.getTarget() == victim;
        if (attacker instanceof ServerPlayer player)
            return !FriendlyFistCompat.friendlyFistEnabled(player);
        return attacker instanceof Enemy;
    }

    private static String personality(AmbientFighterEntity f) {
        return f.getPersonality().displayName().toLowerCase(java.util.Locale.ROOT);
    }

    private static String alignment(AmbientFighterEntity f) {
        return f.getAlignment().displayName().toLowerCase(java.util.Locale.ROOT);
    }

    private static void clear(AmbientFighterEntity fighter) {
        var tag = fighter.getPersistentData();
        tag.remove(THREAT); tag.remove(LAST_DAMAGE); tag.remove(SAVIOR); tag.remove(ARMED);
    }
}
