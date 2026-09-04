package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterArchetype;
import com.dmzlivingworld.entity.FighterRank;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import software.bernie.geckolib.animatable.GeoItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Living Arsenal: persistent LW fighters own genuine DragonMineZ equipment.
 *
 * Equipment lives in ordinary entity equipment slots. The exact ItemStack is kept
 * when a recurring fighter is serialized, can move between people through loot or
 * inheritance, and carries a tiny owner-chain tag so a weapon can acquire real
 * history without introducing any custom item or registry entry.
 */
public final class FighterArsenalManager {
    private static final String PROFILE_EQUIPMENT = "ArsenalEquipment";
    private static final String PROFILE_INITIALIZED = "ArsenalInitialized";
    private static final String OWNERSHIP = "DMZLivingWorldOwnership";
    private static final String OWNER_LIST = "Owners";

    private FighterArsenalManager() {}

    /**
     * R35: DMZ GeoItems (katana/Z Sword/etc.) are singleton Item objects with a shared
     * GeckoLib animatable cache. GeckoLib distinguishes individual ItemStacks by the
     * per-stack GeckoLibID NBT. An unassigned stack resolves to Long.MAX_VALUE, so two
     * otherwise unrelated copies of the same weapon can alias the same render instance.
     *
     * Every live LW-owned GeoItem therefore receives a real server-allocated stack id.
     * forceFresh is used when an item becomes a different NPC's possession so copied NBT
     * can never preserve an id that is still present on another stack (creative gifts,
     * debug copies, old inheritance data, etc.).
     */
    private static void prepareGeoItemIdentity(AmbientFighterEntity fighter, ItemStack stack, boolean forceFresh) {
        if (fighter == null || stack == null || stack.isEmpty() || !(stack.getItem() instanceof GeoItem)) return;
        if (!(fighter.level() instanceof ServerLevel level)) return;

        CompoundTag tag = stack.getOrCreateTag();
        if (forceFresh) tag.remove(GeoItem.ID_NBT_KEY);
        GeoItem.getOrAssignId(stack, level);
    }

    private static void equipOwnedStack(AmbientFighterEntity fighter, EquipmentSlot slot, ItemStack stack, boolean forceFreshGeoId) {
        if (fighter == null || slot == null) return;
        if (stack != null && !stack.isEmpty()) prepareGeoItemIdentity(fighter, stack, forceFreshGeoId);
        fighter.setItemSlot(slot, stack == null ? ItemStack.EMPTY : stack);
    }

    /** Save-upgrade/runtime migration for already-equipped R34-and-earlier GeoItems. */
    public static void refreshEquippedGeoItemIdentities(AmbientFighterEntity fighter, boolean forceFresh) {
        if (fighter == null || fighter.level().isClientSide) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = fighter.getItemBySlot(slot);
            if (!stack.isEmpty()) prepareGeoItemIdentity(fighter, stack, forceFresh);
        }
    }

    public static void initializeNaturalLoadout(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || fighter.isArsenalInitialized()) return;
        if (WorldMenaceManager.isHerobrine(fighter) || fighter.isNonCombatant() || isTemporaryFusionBody(fighter)) {
            fighter.setArsenalInitialized(true);
            return;
        }
        fighter.setArsenalInitialized(true);

        float armorChance = switch (fighter.getRank()) {
            case ROOKIE -> 0.09F;
            case TRAINED -> 0.27F;
            case VETERAN -> 0.49F;
        };
        if (fighter.isFactionMember()) armorChance += 0.10F;
        if (!RedRibbonExperimentManager.isExperiment(fighter) && fighter.getRandom().nextFloat() < armorChance) {
            equipFighterArmor(fighter, fighter.getRank() == FighterRank.VETERAN && fighter.getRandom().nextFloat() < 0.64F);
        }

        if (!fighter.getMainHandItem().isEmpty()) {
            refreshEquippedGeoItemIdentities(fighter, true);
            ensureCurrentOwnership(fighter);
            FighterMemoryManager.refreshLoadedProfile(fighter);
            return;
        }

        WorldFaction faction = fighter.isFactionMember() && fighter.level() instanceof ServerLevel level
                ? FactionManager.byId(level, fighter.getFactionId()) : null;

        // A small subset of suitable organizations develops a real sword tradition.
        // This is deterministic from the faction seed, so existing worlds gain the
        // specialization without changing the faction save schema.
        if (isSwordFaction(faction)) {
            ItemStack sword = rollFactionSword(fighter);
            markOwnership(sword, fighter.getFighterName(), "faction sword tradition");
            equipOwnedStack(fighter, EquipmentSlot.MAINHAND, sword, true);
            FighterMemoryManager.refreshLoadedProfile(fighter);
            return;
        }

        float weaponChance = switch (fighter.getRank()) {
            case ROOKIE -> 0.11F;
            case TRAINED -> 0.28F;
            case VETERAN -> 0.44F;
        };
        if (fighter.isFactionMember()) weaponChance += 0.10F;
        if (fighter.getRandom().nextFloat() >= weaponChance) {
            FighterMemoryManager.refreshLoadedProfile(fighter);
            return;
        }

        ItemStack weapon = rollOrdinaryWeapon(fighter);
        if (!weapon.isEmpty()) {
            markOwnership(weapon, fighter.getFighterName(), "first known owner");
            equipOwnedStack(fighter, EquipmentSlot.MAINHAND, weapon, true);
        }
        FighterMemoryManager.refreshLoadedProfile(fighter);
    }

    /** True for a deterministic minority of Earth martial/mercenary organizations. */
    public static boolean isSwordFaction(WorldFaction faction) {
        if (faction == null || faction.realm() != FactionRealm.EARTH) return false;
        boolean suitable = switch (faction.ethos()) {
            case MARTIAL_SCHOOL, CHALLENGERS, MERCENARIES, WANDERING_GUARD, STREET_GANG -> true;
            default -> false;
        };
        if (!suitable) return false;
        long mixed = FactionWorldData.mix(faction.seed() ^ 0x53574F52444D454EL);
        return Math.floorMod(mixed, 100L) < 28L;
    }

    public static String factionArsenalIdentity(WorldFaction faction) {
        return isSwordFaction(faction) ? "Swordsmen" : "Mixed weapons";
    }

    private static ItemStack rollOrdinaryWeapon(AmbientFighterEntity fighter) {
        FighterArchetype style = fighter.getArchetype();
        float roll = fighter.getRandom().nextFloat();
        if (style == FighterArchetype.KI_SPECIALIST) {
            return roll < 0.78F ? new ItemStack(MainItems.BLASTER_CANNON.get()) : rollSwordForFighter(fighter, false);
        }
        if (style == FighterArchetype.SPEEDSTER) {
            return roll < 0.84F ? rollSwordForFighter(fighter, false) : new ItemStack(MainItems.POWER_POLE.get());
        }
        if (style == FighterArchetype.MARTIAL_ARTIST) {
            return roll < 0.74F ? rollSwordForFighter(fighter, false) : new ItemStack(MainItems.POWER_POLE.get());
        }
        if (style == FighterArchetype.GUARDIAN) {
            return roll < 0.52F ? rollSwordForFighter(fighter, false) : new ItemStack(MainItems.POWER_POLE.get());
        }
        if (fighter.getAlignment() == FighterAlignment.BAD && roll < 0.52F) {
            return new ItemStack(MainItems.BLASTER_CANNON.get());
        }
        return roll < 0.50F ? rollSwordForFighter(fighter, false) : ItemStack.EMPTY;
    }

    /** Actual DMZ katana item, only renamed from its character-specific registry display name. */
    private static ItemStack commonKatana() {
        ItemStack stack = new ItemStack(MainItems.KATANA_YAJIROBE.get());
        stack.setHoverName(Component.literal("Katana"));
        return stack;
    }

    /**
     * DMZ 2.1.3 ships four genuine swords. Katana is the normal world weapon;
     * Brave/Z/Dimensional swords are rare veteran relics rather than routine RNG gear.
     * All remain ordinary DMZ ItemStacks and therefore can also move through loot,
     * gifts and inheritance without Living World registering replacement items.
     */
    private static ItemStack rollFactionSword(AmbientFighterEntity fighter) {
        return rollSwordForFighter(fighter, true);
    }

    private static ItemStack rollSwordForFighter(AmbientFighterEntity fighter, boolean swordTradition) {
        if (fighter == null) return commonKatana();
        float roll = fighter.getRandom().nextFloat();
        if (fighter.getRank() == FighterRank.ROOKIE) return commonKatana();
        if (fighter.getRank() == FighterRank.TRAINED) {
            if (swordTradition && roll < 0.018F) return new ItemStack(MainItems.BRAVE_SWORD.get());
            return commonKatana();
        }
        float relicScale = swordTradition ? 1.0F : 0.42F;
        if (roll < 0.010F * relicScale) return new ItemStack(MainItems.DIMENSIONAL_SWORD.get());
        if (roll < 0.038F * relicScale) return new ItemStack(MainItems.Z_SWORD.get());
        if (roll < 0.092F * relicScale) return new ItemStack(MainItems.BRAVE_SWORD.get());
        return commonKatana();
    }

    public static boolean isSword(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof SwordItem
                && stack.getItem() != MainItems.POWER_POLE.get();
    }

    public static boolean hasPreferredWeapon(AmbientFighterEntity fighter) {
        if (fighter == null) return false;
        ItemStack held = fighter.getMainHandItem();
        if (held.isEmpty()) return false;
        Item item = held.getItem();
        return switch (fighter.getArchetype()) {
            case SPEEDSTER -> isSword(held);
            case MARTIAL_ARTIST -> isSword(held) || item == MainItems.POWER_POLE.get();
            case KI_SPECIALIST -> item == MainItems.BLASTER_CANNON.get() || item == MainItems.MERUS_LASER.get();
            case GUARDIAN -> isSword(held) || item == MainItems.POWER_POLE.get();
            case BRAWLER -> true;
        };
    }


    /** Exact mechanical test used by ACQUIRE_EQUIPMENT goals. */
    public static boolean satisfiesEquipmentGoal(AmbientFighterEntity fighter, String target) {
        if (fighter == null) return false;
        String need = target == null ? "" : target.trim().toLowerCase(java.util.Locale.ROOT);
        ItemStack held = fighter.getMainHandItem();
        if (isTemporaryActivityProp(held)) held = ItemStack.EMPTY;
        Item heldItem = held.isEmpty() ? null : held.getItem();
        return switch (need) {
            case "a sword", "sword" -> isSword(held);
            case "a martial weapon", "martial weapon" -> isSword(held) || heldItem == MainItems.POWER_POLE.get();
            case "a ranged weapon", "ranged weapon" -> heldItem == MainItems.BLASTER_CANNON.get() || heldItem == MainItems.MERUS_LASER.get();
            case "a weapon", "weapon" -> !held.isEmpty();
            case "useful equipment", "equipment", "" -> hasAnyEquipment(fighter);
            default -> hasPreferredWeapon(fighter);
        };
    }

    private static boolean hasAnyEquipment(AmbientFighterEntity fighter) {
        if (fighter == null) return false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = fighter.getItemBySlot(slot);
            if (!stack.isEmpty() && !isNonPersistentActivityProp(stack)) return true;
        }
        return false;
    }

    private static void equipFighterArmor(AmbientFighterEntity fighter, boolean full) {
        putArmor(fighter, ArmorItem.Type.CHESTPLATE);
        if (full || fighter.getRandom().nextBoolean()) putArmor(fighter, ArmorItem.Type.LEGGINGS);
        if (full || fighter.getRandom().nextFloat() < 0.55F) putArmor(fighter, ArmorItem.Type.BOOTS);
        if (full && fighter.getRandom().nextFloat() < 0.30F) putArmor(fighter, ArmorItem.Type.HELMET);
    }

    private static void putArmor(AmbientFighterEntity fighter, ArmorItem.Type type) {
        var entry = MainItems.FIGHTER_ARMOR.get(type);
        if (entry == null) return;
        ItemStack stack = new ItemStack(entry.get());
        markOwnership(stack, fighter.getFighterName(), "first known owner");
        equipOwnedStack(fighter, slot(type), stack, true);
    }

    /** Uses DMZ's real Blaster Cannon projectile path for NPC owners. */
    public static void tickCombatEquipment(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || fighter.isDefeated() || fighter.isCaptive()
                || fighter.isMeditating() || fighter.isNonCombatant()) return;
        if (fighter.getArsenalWeaponCooldown() > 0) return;
        if (fighter.getMainHandItem().getItem() != MainItems.BLASTER_CANNON.get()) return;
        var target = fighter.getTarget();
        if (target == null || !target.isAlive() || fighter.distanceToSqr(target) > 36.0D * 36.0D
                || fighter.distanceToSqr(target) < 7.0D * 7.0D || !fighter.hasLineOfSight(target)) return;

        fighter.getLookControl().setLookAt(target, 40.0F, 40.0F);
        KiBlastEntity blast = new KiBlastEntity(fighter.level(), fighter);
        blast.setupKiBlast(fighter, 10.0F, 1.0F, 16735868, 9700356, 1.0F, 5);
        fighter.level().addFreshEntity(blast);
        fighter.setArsenalWeaponCooldown(70 + fighter.getRandom().nextInt(41));
    }

    public static boolean tryGift(ServerPlayer player, AmbientFighterEntity fighter, ItemStack held, boolean bypassTrust) {
        if (player == null || fighter == null || held == null || held.isEmpty() || !fighter.isAlive()) return false;
        EquipmentSlot slot = supportedSlot(held);
        if (slot == null || (!bypassTrust && !canReceiveGift(player, fighter))) return false;

        // A visible fishing/eating/scouting prop is not real equipment. End the idle activity
        // before any genuine inventory mutation so the prop can never be gifted, dropped or displaced.
        FighterAmbientActivityManager.cancel(fighter);
        ItemStack previous = fighter.getItemBySlot(slot).copy();
        ItemStack given = held.copy();
        given.setCount(1);
        markOwnership(given, fighter.getFighterName(), "gift from " + player.getGameProfile().getName());
        equipOwnedStack(fighter, slot, given, true);
        fighter.setPersistenceRequired();
        fighter.setArsenalInitialized(true);

        if (!player.getAbilities().instabuild) held.shrink(1);
        if (!previous.isEmpty() && !player.getInventory().add(previous)) player.drop(previous, false);

        fighter.recordLegacyEvent("Received " + given.getHoverName().getString() + " from " + player.getGameProfile().getName());
        FighterGoalManager.onEquipmentAcquired(fighter, given.getHoverName().getString());
        if (!bypassTrust) {
            FighterMemoryManager.strengthenRelationship(player, fighter, 2, FighterRelationshipManager.BondEvent.GIFT,
                    "Received " + given.getHoverName().getString() + " as a gift");
        }
        FighterMemoryManager.refreshLoadedProfile(fighter);
        return true;
    }

    public static void debugEquipTest(AmbientFighterEntity fighter) {
        if (fighter == null) return;
        FighterAmbientActivityManager.cancel(fighter);
        ItemStack weapon = fighter.getRandom().nextBoolean() ? commonKatana() : new ItemStack(MainItems.BLASTER_CANNON.get());
        markOwnership(weapon, fighter.getFighterName(), "debug loadout");
        equipOwnedStack(fighter, EquipmentSlot.MAINHAND, weapon, true);
        equipFighterArmor(fighter, true);
        fighter.setArsenalInitialized(true);
        FighterMemoryManager.refreshLoadedProfile(fighter);
    }

    public static void clearEquipment(AmbientFighterEntity fighter) {
        if (fighter == null) return;
        FighterAmbientActivityManager.cancel(fighter);
        for (EquipmentSlot slot : EquipmentSlot.values()) fighter.setItemSlot(slot, ItemStack.EMPTY);
        fighter.setArsenalInitialized(true);
        FighterMemoryManager.refreshLoadedProfile(fighter);
    }

    /**
     * Real loot cycle. An idle fighter notices a worthwhile dropped item, walks to
     * it, and only equips the exact stack when close enough. This fixes the old
     * "instant invisible vacuum" behavior and allows weapon upgrades instead of
     * refusing every pickup whenever MAINHAND was occupied.
     */
    /** Finds a real dropped upgrade without moving or equipping it. Used by contextual Go Along activities. */
    public static ItemEntity findUsefulDroppedItem(AmbientFighterEntity fighter, double radius) {
        if (fighter == null || fighter.level().isClientSide || FighterAmbientActivityManager.isActive(fighter)
                || !(fighter.level() instanceof ServerLevel level)) return null;
        return level.getEntitiesOfClass(ItemEntity.class, fighter.getBoundingBox().inflate(Math.max(2.0D, radius)),
                        e -> e.isAlive() && !e.getItem().isEmpty() && e.tickCount > 5)
                .stream().filter(e -> {
                    EquipmentSlot slot = supportedSlot(e.getItem());
                    return slot != null && isUpgrade(fighter, fighter.getItemBySlot(slot), e.getItem(), slot);
                })
                .min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
    }

    public static boolean tryPickupNearby(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || fighter.isDefeated() || fighter.isCaptive()
                || fighter.isMeditating() || fighter.isNonCombatant() || FighterAmbientActivityManager.isActive(fighter)
                || FactionRequestMissionManager.isExclusiveFieldAssignment(fighter)
                || !(fighter.level() instanceof ServerLevel level)) return false;
        if (fighter.getTarget() != null && fighter.getTarget().isAlive()) return false;

        List<ItemEntity> candidates = new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class,
                fighter.getBoundingBox().inflate(9.0D), e -> e.isAlive() && !e.getItem().isEmpty() && e.tickCount > 5));
        candidates.removeIf(e -> {
            EquipmentSlot slot = supportedSlot(e.getItem());
            return slot == null || !isUpgrade(fighter, fighter.getItemBySlot(slot), e.getItem(), slot);
        });
        if (candidates.isEmpty()) return false;
        candidates.sort(Comparator.comparingDouble(e -> fighter.distanceToSqr(e)));
        ItemEntity itemEntity = candidates.get(0);

        if (fighter.distanceToSqr(itemEntity) > 2.35D * 2.35D) {
            fighter.getLookControl().setLookAt(itemEntity, 35.0F, 35.0F);
            fighter.getNavigation().moveTo(itemEntity, 1.16D);
            return false;
        }

        ItemStack ground = itemEntity.getItem();
        EquipmentSlot slot = supportedSlot(ground);
        if (slot == null) return false;
        ItemStack taken = ground.copy();
        taken.setCount(1);
        boolean deliberateGift = itemEntity.getPersistentData().hasUUID(FighterGiftPickupManager.GIVER);
        String giverName = "";
        if (deliberateGift) {
            var giver = level.getServer().getPlayerList().getPlayer(itemEntity.getPersistentData().getUUID(FighterGiftPickupManager.GIVER));
            if (giver != null) giverName = giver.getGameProfile().getName();
        }
        markOwnership(taken, fighter.getFighterName(), deliberateGift ? "gift" : "recovered from the battlefield");

        ItemStack old = fighter.getItemBySlot(slot).copy();
        equipOwnedStack(fighter, slot, taken, true);
        fighter.setArsenalInitialized(true);
        if (!old.isEmpty()) fighter.spawnAtLocation(old);
        FighterGiftPickupManager.thankForRecoveredGift(fighter, itemEntity);

        ground.shrink(1);
        if (ground.isEmpty()) itemEntity.discard();
        else itemEntity.setItem(ground.copy());

        fighter.recordLegacyEvent(deliberateGift
                ? "Accepted " + taken.getHoverName().getString() + " as a gift" + (giverName.isBlank() ? "" : " from " + giverName)
                : "Recovered " + taken.getHoverName().getString() + " from the battlefield");
        FighterGoalManager.onEquipmentAcquired(fighter, taken.getHoverName().getString());
        FighterMemoryManager.refreshLoadedProfile(fighter);
        return true;
    }

    /**
     * Succession moves up to two useful real items (signature weapon first, then
     * one armor piece) to the best nearby fighter/ally/rival. Nothing is copied.
     */
    public static AmbientFighterEntity inheritFromFallen(AmbientFighterEntity fallen) {
        if (fallen == null || fallen.level().isClientSide || !(fallen.level() instanceof ServerLevel level)) return null;
        AmbientFighterEntity heir = level.getEntitiesOfClass(AmbientFighterEntity.class, fallen.getBoundingBox().inflate(16.0D),
                        other -> other != fallen && other.isAlive() && !other.isDefeated() && !other.isCaptive())
                .stream().sorted((a, b) -> Integer.compare(inheritancePriority(b, fallen), inheritancePriority(a, fallen)))
                .filter(other -> inheritancePriority(other, fallen) > 0)
                .findFirst().orElse(null);
        if (heir == null) return null;
        FighterAmbientActivityManager.cancel(heir);

        EquipmentSlot[] priority = {EquipmentSlot.MAINHAND, EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int moved = 0;
        for (EquipmentSlot slot : priority) {
            if (moved >= 2) break;
            ItemStack legacy = fallen.getItemBySlot(slot);
            if (legacy.isEmpty()) continue;
            if (slot != EquipmentSlot.MAINHAND && !isUpgrade(heir, heir.getItemBySlot(slot), legacy, slot)) continue;
            if (slot == EquipmentSlot.MAINHAND && !isUpgrade(heir, heir.getItemBySlot(slot), legacy, slot)
                    && !heir.getItemBySlot(slot).isEmpty()) continue;

            ItemStack movedStack = legacy.copy();
            movedStack.setCount(1);
            markOwnership(movedStack, heir.getFighterName(), "inherited from " + fallen.getFighterName());
            ItemStack displaced = heir.getItemBySlot(slot).copy();
            equipOwnedStack(heir, slot, movedStack, true);
            fallen.setItemSlot(slot, ItemStack.EMPTY);
            if (!displaced.isEmpty()) heir.spawnAtLocation(displaced);
            heir.setArsenalInitialized(true);
            heir.recordLegacyEvent("Inherited " + movedStack.getHoverName().getString() + " from " + fallen.getFighterName());
            FighterGoalManager.onEquipmentAcquired(heir, movedStack.getHoverName().getString());
            moved++;
        }
        if (moved <= 0) return null;
        FighterMemoryManager.refreshLoadedProfile(heir);
        return heir;
    }

    public static boolean debugTransferInheritance(AmbientFighterEntity donor, AmbientFighterEntity heir) {
        if (donor == null || heir == null || donor == heir || donor.level().isClientSide) return false;
        FighterAmbientActivityManager.cancel(donor);
        FighterAmbientActivityManager.cancel(heir);
        EquipmentSlot[] priority = {EquipmentSlot.MAINHAND, EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : priority) {
            ItemStack legacy = donor.getItemBySlot(slot);
            if (legacy.isEmpty() || !isUpgrade(heir, heir.getItemBySlot(slot), legacy, slot)) continue;
            ItemStack moved = legacy.copy();
            moved.setCount(1);
            markOwnership(moved, heir.getFighterName(), "inherited from " + donor.getFighterName());
            equipOwnedStack(heir, slot, moved, true);
            donor.setItemSlot(slot, ItemStack.EMPTY);
            heir.setArsenalInitialized(true);
            heir.recordLegacyEvent("Inherited " + moved.getHoverName().getString() + " from " + donor.getFighterName());
            FighterGoalManager.onEquipmentAcquired(heir, moved.getHoverName().getString());
            FighterMemoryManager.refreshLoadedProfile(donor);
            FighterMemoryManager.refreshLoadedProfile(heir);
            return true;
        }
        return false;
    }

    private static int inheritancePriority(AmbientFighterEntity candidate, AmbientFighterEntity fallen) {
        if (candidate.isFactionMember() && fallen.isFactionMember() && candidate.getFactionId().equals(fallen.getFactionId())) return 75;
        if (candidate.getRivalName().equals(fallen.getFighterName())) return 58;
        if (candidate.getAlignment() == fallen.getAlignment()) return 24;
        return 0;
    }

    private static boolean isUpgrade(AmbientFighterEntity fighter, ItemStack current, ItemStack candidate, EquipmentSlot slot) {
        if (candidate == null || candidate.isEmpty()) return false;
        if (current == null || current.isEmpty()) return true;
        if (slot.getType() == EquipmentSlot.Type.ARMOR
                && current.getItem() instanceof ArmorItem oldArmor && candidate.getItem() instanceof ArmorItem newArmor) {
            return newArmor.getDefense() > oldArmor.getDefense();
        }
        if (slot == EquipmentSlot.MAINHAND) {
            return weaponScore(fighter, candidate) >= weaponScore(fighter, current) + 5;
        }
        return false;
    }

    private static int weaponScore(AmbientFighterEntity fighter, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        Item item = stack.getItem();
        int score;
        if (item == MainItems.Z_SWORD.get() || item == MainItems.BRAVE_SWORD.get() || item == MainItems.DIMENSIONAL_SWORD.get()) score = 95;
        else if (item instanceof SwordItem && item != MainItems.POWER_POLE.get()) score = 52;
        else if (item == MainItems.MERUS_LASER.get()) score = 52;
        else if (item == MainItems.BLASTER_CANNON.get()) score = 47;
        else if (item == MainItems.POWER_POLE.get()) score = 44;
        else score = 10;

        if (fighter != null) {
            switch (fighter.getArchetype()) {
                case SPEEDSTER -> { if (isSword(stack)) score += 26; }
                case MARTIAL_ARTIST -> { if (isSword(stack)) score += 22; if (item == MainItems.POWER_POLE.get()) score += 14; }
                case GUARDIAN -> { if (isSword(stack)) score += 15; if (item == MainItems.POWER_POLE.get()) score += 18; }
                case KI_SPECIALIST -> { if (item == MainItems.BLASTER_CANNON.get() || item == MainItems.MERUS_LASER.get()) score += 30; }
                case BRAWLER -> { if (item == MainItems.POWER_POLE.get()) score += 8; }
            }
        }
        return score;
    }

    public static boolean canReceiveGift(ServerPlayer player, AmbientFighterEntity fighter) {
        if (fighter.getTarget() == player || fighter.isCaptive() || fighter.isDefeated()) return false;
        if (fighter.isRememberedFor(player) && fighter.getMemoryRelationship() >= 20) return true;
        if (LivingBondManager.isCompanion(player, fighter)) return true;
        return fighter.isFactionMember() && FactionManager.getReputation(player, fighter.getFactionId()) >= FactionManager.FRIENDLY_REP;
    }

    public static EquipmentSlot supportedSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Item item = stack.getItem();
        if (item instanceof ArmorItem armor) return slot(armor.getType());
        if (item instanceof SwordItem || item == MainItems.BLASTER_CANNON.get() || item == MainItems.MERUS_LASER.get()
                || item == MainItems.POWER_POLE.get()) return EquipmentSlot.MAINHAND;
        return null;
    }

    private static EquipmentSlot slot(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> EquipmentSlot.HEAD;
            case CHESTPLATE -> EquipmentSlot.CHEST;
            case LEGGINGS -> EquipmentSlot.LEGS;
            case BOOTS -> EquipmentSlot.FEET;
        };
    }

    public static String summaryProfile(CompoundTag profile) {
        if (profile == null || !profile.contains(PROFILE_EQUIPMENT, Tag.TAG_LIST)) return "none";
        List<String> parts = new ArrayList<>();
        ListTag list = profile.getList(PROFILE_EQUIPMENT, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains("Stack", Tag.TAG_COMPOUND)) continue;
            ItemStack stack = ItemStack.of(entry.getCompound("Stack"));
            add(parts, stack);
        }
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    /** Debug-facing render identity for the currently equipped main-hand GeoItem. */
    public static String geoItemIdentitySummary(AmbientFighterEntity fighter) {
        if (fighter == null) return "none";
        ItemStack stack = fighter.getMainHandItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof GeoItem)) return "not a GeoItem";
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(GeoItem.ID_NBT_KEY, Tag.TAG_ANY_NUMERIC)) return "UNASSIGNED";
        return Long.toString(tag.getLong(GeoItem.ID_NBT_KEY));
    }

    public static String summary(AmbientFighterEntity fighter) {
        if (fighter == null) return "none";
        List<String> parts = new ArrayList<>();
        add(parts, fighter.getItemBySlot(EquipmentSlot.MAINHAND));
        add(parts, fighter.getItemBySlot(EquipmentSlot.HEAD));
        add(parts, fighter.getItemBySlot(EquipmentSlot.CHEST));
        add(parts, fighter.getItemBySlot(EquipmentSlot.LEGS));
        add(parts, fighter.getItemBySlot(EquipmentSlot.FEET));
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    public static List<String> detailedEquipment(AmbientFighterEntity fighter) {
        if (fighter == null) return List.of("none");
        List<String> out = new ArrayList<>();
        detail(out, "Weapon", fighter.getItemBySlot(EquipmentSlot.MAINHAND));
        detail(out, "Head", fighter.getItemBySlot(EquipmentSlot.HEAD));
        detail(out, "Chest", fighter.getItemBySlot(EquipmentSlot.CHEST));
        detail(out, "Legs", fighter.getItemBySlot(EquipmentSlot.LEGS));
        detail(out, "Feet", fighter.getItemBySlot(EquipmentSlot.FEET));
        return out.isEmpty() ? List.of("none") : out;
    }

    private static void detail(List<String> out, String slot, ItemStack stack) {
        if (stack == null || stack.isEmpty() || isNonPersistentActivityProp(stack)) return;
        String owners = ownershipSummary(stack);
        out.add(slot + ": " + stack.getHoverName().getString() + (owners.isBlank() ? "" : " • owners: " + owners));
    }

    private static void add(List<String> out, ItemStack stack) {
        if (stack != null && !stack.isEmpty() && !isNonPersistentActivityProp(stack)) out.add(stack.getHoverName().getString());
    }

    private static void ensureCurrentOwnership(AmbientFighterEntity fighter) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = fighter.getItemBySlot(slot);
            if (!stack.isEmpty() && supportedSlot(stack) != null && ownershipSummary(stack).isBlank()) {
                markOwnership(stack, fighter.getFighterName(), "first known owner");
            }
        }
    }

    public static void markOwnership(ItemStack stack, String owner, String reason) {
        if (stack == null || stack.isEmpty() || owner == null || owner.isBlank()) return;
        CompoundTag root = stack.getOrCreateTag();
        CompoundTag history = root.contains(OWNERSHIP, Tag.TAG_COMPOUND) ? root.getCompound(OWNERSHIP) : new CompoundTag();
        ListTag owners = history.getList(OWNER_LIST, Tag.TAG_STRING);
        if (owners.isEmpty() || !owner.equals(owners.getString(owners.size() - 1))) owners.add(StringTag.valueOf(owner));
        while (owners.size() > 6) owners.remove(0);
        history.put(OWNER_LIST, owners);
        history.putString("CurrentOwner", owner);
        if (reason != null && !reason.isBlank()) history.putString("LastTransfer", reason.length() > 96 ? reason.substring(0, 96) : reason);
        root.put(OWNERSHIP, history);
        stack.setTag(root);
    }

    /** Activity props are visual/session state, never persistent arsenal equipment. */
    public static boolean isTemporaryActivityProp(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasTag()
                && stack.getTag() != null && stack.getTag().getBoolean("LWTemporaryActivityProp");
    }

    private static boolean isNonPersistentActivityProp(ItemStack stack) {
        return isTemporaryActivityProp(stack) || FactionHornManager.isRoleHorn(stack) || (stack != null && !stack.isEmpty()
                && (stack.is(Items.FISHING_ROD) || stack.is(Items.BREAD) || stack.is(Items.SPYGLASS)));
    }

    public static String ownershipSummary(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) return "";
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(OWNERSHIP, Tag.TAG_COMPOUND)) return "";
        ListTag owners = root.getCompound(OWNERSHIP).getList(OWNER_LIST, Tag.TAG_STRING);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < owners.size(); i++) if (!owners.getString(i).isBlank()) names.add(owners.getString(i));
        return String.join(" → ", names);
    }

    public static void writeProfile(AmbientFighterEntity fighter, CompoundTag profile) {
        profile.putBoolean(PROFILE_INITIALIZED, fighter.isArsenalInitialized());
        ListTag list = new ListTag();
        writeSlot(list, EquipmentSlot.MAINHAND, fighter.getItemBySlot(EquipmentSlot.MAINHAND));
        writeSlot(list, EquipmentSlot.OFFHAND, fighter.getItemBySlot(EquipmentSlot.OFFHAND));
        writeSlot(list, EquipmentSlot.HEAD, fighter.getItemBySlot(EquipmentSlot.HEAD));
        writeSlot(list, EquipmentSlot.CHEST, fighter.getItemBySlot(EquipmentSlot.CHEST));
        writeSlot(list, EquipmentSlot.LEGS, fighter.getItemBySlot(EquipmentSlot.LEGS));
        writeSlot(list, EquipmentSlot.FEET, fighter.getItemBySlot(EquipmentSlot.FEET));
        profile.put(PROFILE_EQUIPMENT, list);
    }

    private static void writeSlot(ListTag list, EquipmentSlot slot, ItemStack stack) {
        if (stack == null || stack.isEmpty() || isNonPersistentActivityProp(stack)) return;
        CompoundTag entry = new CompoundTag();
        entry.putString("Slot", slot.getName());
        entry.put("Stack", stack.save(new CompoundTag()));
        list.add(entry);
    }

    public static void readProfile(AmbientFighterEntity fighter, CompoundTag profile) {
        fighter.setArsenalInitialized(profile.contains(PROFILE_INITIALIZED) && profile.getBoolean(PROFILE_INITIALIZED));
        if (!profile.contains(PROFILE_EQUIPMENT, Tag.TAG_LIST)) return;
        ListTag list = profile.getList(PROFILE_EQUIPMENT, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            EquipmentSlot slot = byName(entry.getString("Slot"));
            if (slot == null || !entry.contains("Stack", Tag.TAG_COMPOUND)) continue;
            ItemStack stack = ItemStack.of(entry.getCompound("Stack"));
            // RC5 could snapshot a temporary fishing/eating/scouting prop mid-activity.
            // These were never legitimate arsenal items, so discard that old transient state on load.
            if (!stack.isEmpty() && !isNonPersistentActivityProp(stack)) {
                stack.setCount(1);
                equipOwnedStack(fighter, slot, stack, true);
            }
        }
        refreshEquippedGeoItemIdentities(fighter, false);
        ensureCurrentOwnership(fighter);
    }

    private static EquipmentSlot byName(String name) {
        for (EquipmentSlot slot : EquipmentSlot.values()) if (slot.getName().equals(name)) return slot;
        return null;
    }

    private static boolean isTemporaryFusionBody(AmbientFighterEntity fighter) {
        CompoundTag data = fighter.getPersistentData();
        return data.contains("DMZLWNpcFusionTemp", Tag.TAG_COMPOUND)
                && data.getCompound("DMZLWNpcFusionTemp").getBoolean("Active");
    }
}
