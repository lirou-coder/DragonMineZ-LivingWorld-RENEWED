package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Rare native-DMZ items that ordinary Living World fighters can visibly carry or use. */
public final class FighterSpecialItemManager {
    public static final int ACCESSORY_NONE = 0;
    public static final int SCOUTER_RED = 1;
    public static final int SCOUTER_BLUE = 2;
    public static final int SCOUTER_GREEN = 3;
    public static final int SCOUTER_PURPLE = 4;
    public static final int WEIGHT_TURTLE = 5;
    public static final int WEIGHT_WORKOUT = 6;
    public static final int WEIGHT_PICCOLO = 7;
    private static final String FRUIT = "LWMightFruitCarrier";
    private static final String FRUIT_USED = "LWMightFruitUsed";
    private static final String FRUIT_BASE_BP = "LWMightFruitBaseBP";
    private static final String FRUIT_MULTIPLIER = "LWMightFruitMultiplier";
    private static final String FRUIT_END = "LWMightFruitEnd";
    private static final String FRUIT_PROP_END = "LWMightFruitPropEnd";
    private static final String FRUIT_PROP_BACKUP = "LWMightFruitPropBackup";
    private static final String COMBAT_WEIGHT_STOWED = "LWCombatWeightStowed";
    private static final String COMBAT_WEIGHT_DECIDED = "LWCombatWeightDecided";
    private static final String COMBAT_WEIGHT_CLEAR_SINCE = "LWCombatWeightClearSince";
    private FighterSpecialItemManager() {}

    public static void initialize(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide) return;
        migrateRc4HeldAccessory(fighter);
        syncAccessoryFromLegacy(fighter);
        restoreLiveFruitEffect(fighter);
        // Existing non-scientist saves are migrated too. FighterScientistManager runs immediately
        // after this initializer and reapplies its research scouter to genuine Scientists.
        int existingAccessory = fighter.getCosmeticAccessoryId();
        if (existingAccessory >= SCOUTER_RED && existingAccessory <= SCOUTER_PURPLE
                && !fighter.getLegacyData().getBoolean("LWScientist")) {
            setAccessory(fighter, ACCESSORY_NONE, "");
        }

        boolean originalRollDone = fighter.getLegacyData().getBoolean("SpecialItemsRolled");
        boolean visualRollDone = fighter.getLegacyData().getBoolean("AccessoryVisualRollV2");
        if (!originalRollDone) {
            fighter.getLegacyData().putBoolean("SpecialItemsRolled", true);
            fighter.getLegacyData().putBoolean("AccessoryVisualRollV2", true);
            if (fighter.getCosmeticAccessoryId() == ACCESSORY_NONE) rollAccessory(fighter);
            if (fighter.getRank().id() >= 2 && fighter.getRandom().nextFloat() < 0.012F) {
                fighter.getLegacyData().putBoolean(FRUIT, true);
            }
            return;
        }

        // One-time RC4 -> RC5 migration. RC4's accessory chance was tiny and the items were
        // only held in the off-hand, so existing worlds get one fair cosmetic roll without
        // changing identity, stats, or save structure.
        if (!visualRollDone) {
            fighter.getLegacyData().putBoolean("AccessoryVisualRollV2", true);
            if (fighter.getCosmeticAccessoryId() == ACCESSORY_NONE) rollAccessory(fighter);
        }
    }

    /** A remembered fighter can re-enter the world before a valid fruit window ends. */
    private static void restoreLiveFruitEffect(AmbientFighterEntity fighter) {
        long remaining = fighter.getLegacyData().getLong(FRUIT_END) - fighter.level().getGameTime();
        if (remaining <= 0L || fighter.hasEffect(MainEffects.MIGHTFRUIT.get())) return;
        fighter.addEffect(new MobEffectInstance(MainEffects.MIGHTFRUIT.get(),
                (int)Math.min(Integer.MAX_VALUE, remaining), 0, false, false, true));
    }

    private static void rollAccessory(AmbientFighterEntity fighter) {
        // R19: scouters are a readable Scientist specialization, not generic fashion.
        // Preserve R18's ~10% ordinary training-weight frequency without rolling scouters.
        float roll = fighter.getRandom().nextFloat();
        if (roll < 0.10F) {
            setAccessory(fighter, switch (fighter.getRandom().nextInt(3)) {
                case 0 -> WEIGHT_TURTLE;
                case 1 -> WEIGHT_WORKOUT;
                default -> WEIGHT_PICCOLO;
            }, "Training weights");
        }
    }

    private static void setAccessory(AmbientFighterEntity fighter, int id, String label) {
        fighter.setCosmeticAccessoryId(id);
        fighter.getLegacyData().putInt("CosmeticAccessoryId", id);
        fighter.getLegacyData().putString("CosmeticAccessory", label == null ? "" : label);
    }

    private static void syncAccessoryFromLegacy(AmbientFighterEntity fighter) {
        int id = fighter.getLegacyData().getInt("CosmeticAccessoryId");
        if (id > 0 && fighter.getCosmeticAccessoryId() != id) fighter.setCosmeticAccessoryId(id);
    }

    /** RC5 stored cosmetic scouters/weights in the off-hand, so they rendered as held items instead of worn gear. */
    private static void migrateRc4HeldAccessory(AmbientFighterEntity fighter) {
        if (fighter.getLegacyData().getInt("CosmeticAccessoryId") > 0) return;
        String oldLabel = fighter.getLegacyData().getString("CosmeticAccessory");
        if (oldLabel.isBlank()) return;
        ItemStack held = fighter.getOffhandItem();
        int id = ACCESSORY_NONE;
        if (held.is(MainItems.RED_SCOUTER.get())) id = SCOUTER_RED;
        else if (held.is(MainItems.BLUE_SCOUTER.get())) id = SCOUTER_BLUE;
        else if (held.is(MainItems.GREEN_SCOUTER.get())) id = SCOUTER_GREEN;
        else if (held.is(MainItems.PURPLE_SCOUTER.get())) id = SCOUTER_PURPLE;
        else if (held.is(MainItems.WEIGHT_TURTLE_SHELL.get())) id = WEIGHT_TURTLE;
        else if (held.is(MainItems.WORKOUT_WEIGHTS.get())) id = WEIGHT_WORKOUT;
        else if (held.is(MainItems.WEIGHT_PICCOLO_CAPE.get())) id = WEIGHT_PICCOLO;
        if (id <= 0) return;
        setAccessory(fighter, id, oldLabel);
        fighter.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    /** Debug helper: force a visible accessory onto one fighter without applying Curios/weight effects. */
    public static String forceAccessory(AmbientFighterEntity fighter, String requested) {
        if (fighter == null) return "";
        String key = requested == null ? "" : requested.trim().toLowerCase(java.util.Locale.ROOT);
        int id = switch (key) {
            case "red", "red_scouter", "scouter_red" -> SCOUTER_RED;
            case "blue", "blue_scouter", "scouter_blue" -> SCOUTER_BLUE;
            case "green", "green_scouter", "scouter_green" -> SCOUTER_GREEN;
            case "purple", "purple_scouter", "scouter_purple" -> SCOUTER_PURPLE;
            case "turtle", "turtle_shell" -> WEIGHT_TURTLE;
            case "workout", "bands" -> WEIGHT_WORKOUT;
            case "piccolo", "cape" -> WEIGHT_PICCOLO;
            case "none", "clear" -> ACCESSORY_NONE;
            default -> -1;
        };
        if (id < 0) return "";
        if (id >= SCOUTER_RED && id <= SCOUTER_PURPLE && !FighterScientistManager.isScientist(fighter)) return "";
        setAccessory(fighter, id, id == ACCESSORY_NONE ? "" : (id <= SCOUTER_PURPLE ? "Scouter" : "Training weights"));
        return switch (id) {
            case SCOUTER_RED -> "red scouter";
            case SCOUTER_BLUE -> "blue scouter";
            case SCOUTER_GREEN -> "green scouter";
            case SCOUTER_PURPLE -> "purple scouter";
            case WEIGHT_TURTLE -> "turtle shell";
            case WEIGHT_WORKOUT -> "workout weights";
            case WEIGHT_PICCOLO -> "Piccolo weights";
            default -> "none";
        };
    }

    public static void tick(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide) return;
        long now = fighter.level().getGameTime();
        tickCombatWeightVisual(fighter, now);
        restoreFruitPropIfReady(fighter, now);
        if (fighter.getLegacyData().getLong(FRUIT_END) > 0L && now >= fighter.getLegacyData().getLong(FRUIT_END)) {
            fighter.getLegacyData().remove(FRUIT_END);
            fighter.getLegacyData().remove(FRUIT_BASE_BP);
            fighter.getLegacyData().remove(FRUIT_MULTIPLIER);
            fighter.removeEffect(MainEffects.MIGHTFRUIT.get());
            // The displayed power is projected from canonical permanent BP, so a combat/training
            // gain earned during the fruit cannot be overwritten by this one-minute layer ending.
            fighter.refreshTemporaryPowerProjection();
        }
        if (!fighter.getLegacyData().getBoolean(FRUIT) || fighter.getLegacyData().getBoolean(FRUIT_USED)) return;
        if (fighter.getTarget() == null || fighter.getHealth() > fighter.getMaxHealth() * 0.70F || fighter.isDefeated() || fighter.isCaptive()) return;
        if (fighter.tickCount % 20 != Math.floorMod(fighter.getUUID().hashCode(), 20)) return;

        fighter.getLegacyData().putBoolean(FRUIT_USED, true);
        int base = fighter.getPermanentBattlePower();
        double multiplier = configuredMightFruitMultiplier();
        fighter.getLegacyData().putInt(FRUIT_BASE_BP, base);
        fighter.getLegacyData().putDouble(FRUIT_MULTIPLIER, multiplier);
        fighter.getLegacyData().putLong(FRUIT_END, now + 20L * 60L);
        fighter.refreshTemporaryPowerProjection();
        fighter.addEffect(new MobEffectInstance(MainEffects.MIGHTFRUIT.get(), 20 * 60, 0, false, false, true));

        // Briefly show the genuine DMZ fruit in hand while it is eaten, then restore any
        // cosmetic scouter/weights that were already being carried.
        ItemStack previous = fighter.getOffhandItem().copy();
        CompoundTag backup = new CompoundTag();
        previous.save(backup);
        fighter.getLegacyData().put(FRUIT_PROP_BACKUP, backup);
        fighter.getLegacyData().putLong(FRUIT_PROP_END, now + 30L);
        fighter.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(MainItems.MIGHT_TREE_FRUIT.get()));
        fighter.swing(net.minecraft.world.InteractionHand.OFF_HAND, true);
        fighter.speak(fighter.getRandom().nextBoolean() ? "This should give me an edge." : "Let's see what this fruit can do.", 65);
        fighter.flareAura(70);
    }

    /** True only while the native fruit's temporary layer is still active. */
    public static boolean hasActiveMightFruit(AmbientFighterEntity fighter) {
        return fighter != null && fighter.level() != null
                && fighter.getLegacyData().getLong(FRUIT_END) > fighter.level().getGameTime();
    }

    /** Stored explicitly in R6 so the layer composes safely with forms and survives reload. */
    public static double mightFruitMultiplier(AmbientFighterEntity fighter) {
        if (!hasActiveMightFruit(fighter)) return 1.0D;
        double stored = fighter.getLegacyData().getDouble(FRUIT_MULTIPLIER);
        if (stored >= 1.05D && stored <= 3.0D) return stored;
        // R5 saved only a pre-fruit snapshot. Migrate a live old save to the same configured
        // native strength instead of deriving a multiplier from a possibly transformed display BP.
        double migrated = configuredMightFruitMultiplier();
        if (!fighter.level().isClientSide) fighter.getLegacyData().putDouble(FRUIT_MULTIPLIER, migrated);
        return migrated;
    }

    private static double configuredMightFruitMultiplier() {
        double configured = ConfigManager.getServerConfig().getGameplay().getMightFruitPower();
        // DMZ stores the player's configured fruit strength separately from the visible status effect.
        // Mirror that strength on the NPC's native combat values for the same one-minute duration.
        double multiplier = configured > 1.0D ? configured : 1.0D + Math.max(0.0D, configured);
        return Math.max(1.05D, Math.min(3.0D, multiplier));
    }
    /** Visual-only immersion: some fighters shed cosmetic training weights for a real fight,
     * then put the same weights back on after combat. Legacy accessory identity is untouched. */
    private static void tickCombatWeightVisual(AmbientFighterEntity fighter, long now) {
        CompoundTag data = fighter.getPersistentData();
        boolean inCombat = fighter.getTarget() != null && fighter.getTarget().isAlive()
                && !fighter.isDefeated() && !fighter.isCaptive();
        int visible = fighter.getCosmeticAccessoryId();
        boolean wearingWeights = visible >= WEIGHT_TURTLE && visible <= WEIGHT_PICCOLO;

        if (inCombat) {
            data.remove(COMBAT_WEIGHT_CLEAR_SINCE);
            if (!data.getBoolean(COMBAT_WEIGHT_DECIDED)) {
                data.putBoolean(COMBAT_WEIGHT_DECIDED, true);
                if (wearingWeights && fighter.getRandom().nextFloat() < 0.55F) {
                    data.putInt(COMBAT_WEIGHT_STOWED, visible);
                    fighter.setCosmeticAccessoryId(ACCESSORY_NONE);
                }
            }
            return;
        }

        if (!data.getBoolean(COMBAT_WEIGHT_DECIDED)) return;
        long clearSince = data.getLong(COMBAT_WEIGHT_CLEAR_SINCE);
        if (clearSince <= 0L) {
            data.putLong(COMBAT_WEIGHT_CLEAR_SINCE, now);
            return;
        }
        if (now - clearSince < 40L) return;
        int stowed = data.getInt(COMBAT_WEIGHT_STOWED);
        if (stowed >= WEIGHT_TURTLE && stowed <= WEIGHT_PICCOLO) {
            fighter.setCosmeticAccessoryId(stowed);
        }
        data.remove(COMBAT_WEIGHT_STOWED);
        data.remove(COMBAT_WEIGHT_DECIDED);
        data.remove(COMBAT_WEIGHT_CLEAR_SINCE);
    }

    private static void restoreFruitPropIfReady(AmbientFighterEntity fighter, long now) {
        long end = fighter.getLegacyData().getLong(FRUIT_PROP_END);
        if (end <= 0L || now < end) return;
        if (fighter.getOffhandItem().is(MainItems.MIGHT_TREE_FRUIT.get())) {
            ItemStack previous = fighter.getLegacyData().contains(FRUIT_PROP_BACKUP)
                    ? ItemStack.of(fighter.getLegacyData().getCompound(FRUIT_PROP_BACKUP)) : ItemStack.EMPTY;
            fighter.setItemSlot(EquipmentSlot.OFFHAND, previous);
        }
        fighter.getLegacyData().remove(FRUIT_PROP_END);
        fighter.getLegacyData().remove(FRUIT_PROP_BACKUP);
    }

}
