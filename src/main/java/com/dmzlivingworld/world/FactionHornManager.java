package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/** Gives each living faction leader a real Goat Horn and sparse battle-rally behavior. */
public final class FactionHornManager {
    public static final String ROLE_HORN_TAG = "LWFactionRoleHorn";
    private static final String BACKUP = "LWFactionHornBackup";
    private static final String COOLDOWN = "LWFactionHornCooldown";
    private static final String LAST_TARGET = "LWFactionHornLastTarget";
    private static final String USE_END = "LWFactionHornUseEnd";
    private static final String DEBUG_UNTIL = "LWFactionHornDebugUntil";

    private FactionHornManager() {}

    public static void tick(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide) return;
        long now = fighter.level().getGameTime();
        boolean debugBearer = fighter.getPersistentData().getLong(DEBUG_UNTIL) > now;
        if ((!fighter.isFactionMember() || !fighter.isFactionLeader()) && !debugBearer) {
            restoreIfFormerBearer(fighter);
            fighter.getPersistentData().remove(DEBUG_UNTIL);
            return;
        }
        long useEnd = fighter.getPersistentData().getLong(USE_END);
        if (useEnd > 0L) {
            if (now >= useEnd) {
                fighter.stopUsingItem();
                fighter.getPersistentData().remove(USE_END);
                if (fighter.isHornRallyPose()) fighter.setAmbientPose(0);
            } else return;
        }
        // Might Tree Fruit temporarily owns the off-hand. Let that native special-item scene finish.
        if (fighter.getLegacyData().getLong("LWMightFruitPropEnd") > now) return;
        ensureHorn(fighter);
        if (!isRoleHorn(fighter.getOffhandItem())) return;

        if (fighter.tickCount % 10 != Math.floorMod(fighter.getUUID().hashCode(), 10)) return;
        if (now < fighter.getPersistentData().getLong(COOLDOWN)) return;
        if (fighter.getTarget() == null) {
            fighter.getPersistentData().remove(LAST_TARGET);
            return;
        }

        UUID targetId = fighter.getTarget().getUUID();
        String previous = fighter.getPersistentData().getString(LAST_TARGET);
        boolean battleOpening = !targetId.toString().equals(previous);
        fighter.getPersistentData().putString(LAST_TARGET, targetId.toString());
        boolean desperate = fighter.getHealth() <= fighter.getMaxHealth() * 0.34F;
        float chance = desperate ? 0.62F : battleOpening ? 0.42F : 0.035F;
        if (fighter.getRandom().nextFloat() >= chance) return;
        blow(fighter, now, desperate);
    }

    private static void ensureHorn(AmbientFighterEntity fighter) {
        ItemStack offhand = fighter.getOffhandItem();
        if (isRoleHorn(offhand)) return;
        if (!fighter.getPersistentData().contains(BACKUP, Tag.TAG_COMPOUND)) {
            CompoundTag saved = new CompoundTag();
            offhand.copy().save(saved);
            fighter.getPersistentData().put(BACKUP, saved);
        }
        ItemStack horn = new ItemStack(Items.GOAT_HORN);
        horn.getOrCreateTag().putBoolean(ROLE_HORN_TAG, true);
        fighter.setItemSlot(EquipmentSlot.OFFHAND, horn);
    }

    private static void blow(AmbientFighterEntity leader, long now, boolean desperate) {
        FighterAmbientActivityManager.cancel(leader);
        leader.swing(InteractionHand.OFF_HAND, true);
        leader.startUsingItem(InteractionHand.OFF_HAND);
        leader.setAmbientPose(6);
        leader.getPersistentData().putLong(USE_END, now + 30L);
        SoundEvent horn = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("minecraft", "item.goat_horn.sound.0"));
        if (horn != null) leader.level().playSound(null, leader.blockPosition(), horn, SoundSource.NEUTRAL, 1.7F, 0.94F + leader.getRandom().nextFloat() * 0.10F);
        leader.flareAura(75);
        leader.speak(desperate ? rallyLine(leader, true) : rallyLine(leader, false), 72);
        ReactiveWorldManager.react(leader, ReactiveWorldManager.Mood.FOCUSED, "rallying their faction", 700);
        ReactiveWorldManager.rememberEvent(leader, "HORN_RALLY", leader.getFighterName(), "sounded the faction horn to rally nearby allies");

        if (leader.level() instanceof net.minecraft.server.level.ServerLevel level) {
            int rallyShouts = 0;
            for (AmbientFighterEntity ally : level.getEntitiesOfClass(AmbientFighterEntity.class,
                    leader.getBoundingBox().inflate(28.0D), other -> other != leader && other.isAlive()
                            && other.isFactionMember() && leader.getFactionId().equals(other.getFactionId()))) {
                ally.flareAura(35 + ally.getRandom().nextInt(31));
                ReactiveWorldManager.react(ally, ReactiveWorldManager.Mood.FOCUSED,
                        leader.getFighterName() + " sounding the faction horn", 700);
                ReactiveWorldManager.rememberEvent(ally, "HORN_RALLY", leader.getFighterName(),
                        "heard the faction horn and rallied with the group");
                if (rallyShouts < 4 && ally.getSpeech().isEmpty() && ally.getRandom().nextFloat() < 0.62F) {
                    ally.speak(rallyResponse(ally), 54 + ally.getRandom().nextInt(25));
                    rallyShouts++;
                }
                if (ally.getTarget() == null && leader.getTarget() != null && ally.distanceToSqr(leader.getTarget()) <= 32.0D * 32.0D) {
                    ally.setTarget(leader.getTarget());
                }
            }
        }
        leader.getPersistentData().putLong(COOLDOWN, now + 900L + leader.getRandom().nextInt(701));
    }


    private static String rallyResponse(AmbientFighterEntity fighter) {
        int roll = fighter.getRandom().nextInt(6);
        if (fighter.getAlignment() == com.dmzlivingworld.entity.FighterAlignment.BAD) {
            return switch (roll) {
                case 0 -> "RAAAH!";
                case 1 -> "CRUSH THEM!";
                case 2 -> "CHARGE!";
                case 3 -> "NO MERCY!";
                default -> "WITH YOU!";
            };
        }
        return switch (roll) {
            case 0 -> "CHARGE!";
            case 1 -> "RAAAH!";
            case 2 -> "WITH YOU!";
            case 3 -> "LET'S MOVE!";
            case 4 -> "TOGETHER!";
            default -> "LET'S GO!";
        };
    }

    private static String rallyLine(AmbientFighterEntity fighter, boolean desperate) {
        if (desperate) return switch (fighter.getAlignment()) {
            case GOOD -> "Together! Nobody falls here!";
            case BAD -> "Stand your ground! Crush them!";
            default -> "Hold the line! We're not done!";
        };
        return switch (fighter.getAlignment()) {
            case GOOD -> "With me!";
            case BAD -> "Show them who we are!";
            default -> "Move together!";
        };
    }

    /** Debug: put a real role horn on the nearest chosen fighter and blow it immediately. */
    public static boolean debugBlow(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || !fighter.isAlive() || WorldMenaceManager.isHerobrine(fighter)) return false;
        long now = fighter.level().getGameTime();
        fighter.getPersistentData().putLong(DEBUG_UNTIL, now + 240L);
        ensureHorn(fighter);
        if (!isRoleHorn(fighter.getOffhandItem())) return false;
        blow(fighter, now, fighter.getHealth() <= fighter.getMaxHealth() * 0.34F);
        return true;
    }

    public static boolean isRoleHorn(ItemStack stack) {
        return stack != null && stack.is(Items.GOAT_HORN) && stack.hasTag() && stack.getTag().getBoolean(ROLE_HORN_TAG);
    }

    private static void restoreIfFormerBearer(AmbientFighterEntity fighter) {
        if (fighter == null) return;
        if (fighter.isHornRallyPose()) fighter.setAmbientPose(0);
        if (!fighter.getPersistentData().contains(BACKUP, Tag.TAG_COMPOUND)) return;
        if (isRoleHorn(fighter.getOffhandItem())) {
            ItemStack previous = ItemStack.of(fighter.getPersistentData().getCompound(BACKUP));
            fighter.setItemSlot(EquipmentSlot.OFFHAND, previous);
        }
        fighter.getPersistentData().remove(BACKUP);
        fighter.getPersistentData().remove(COOLDOWN);
        fighter.getPersistentData().remove(LAST_TARGET);
        fighter.getPersistentData().remove(USE_END);
        fighter.getPersistentData().remove(DEBUG_UNTIL);
    }
}
