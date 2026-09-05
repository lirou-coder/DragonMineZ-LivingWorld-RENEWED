package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterRace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/** Optional visual bridge for Sairens DMZ World's Bio Android cosmetic armor. */
public final class SairensBioAndroidCompat {
    private static final String MOD_ID = "sairens_dmz_world";
    private static final String BACKUP = "LWSairensBioArmorBackup";

    private SairensBioAndroidCompat() {}

    public static void apply(AmbientFighterEntity fighter, String formId) {
        if (!available() || fighter == null || fighter.getRace() != FighterRace.BIO_ANDROID) return;
        if (!fighter.getPersistentData().contains(BACKUP)) {
            var backup = new net.minecraft.nbt.CompoundTag();
            backup.put("Head", fighter.getItemBySlot(EquipmentSlot.HEAD).save(new net.minecraft.nbt.CompoundTag()));
            backup.put("Chest", fighter.getItemBySlot(EquipmentSlot.CHEST).save(new net.minecraft.nbt.CompoundTag()));
            backup.put("Legs", fighter.getItemBySlot(EquipmentSlot.LEGS).save(new net.minecraft.nbt.CompoundTag()));
            backup.put("Feet", fighter.getItemBySlot(EquipmentSlot.FEET).save(new net.minecraft.nbt.CompoundTag()));
            fighter.getPersistentData().put(BACKUP, backup);
        }
        String base = armorBase(formId);
        if (base == null) base = "megamanx";
        fighter.setItemSlot(EquipmentSlot.HEAD, item(base + "_helmet"));
        fighter.setItemSlot(EquipmentSlot.CHEST, item(base + "_chestplate"));
        fighter.setItemSlot(EquipmentSlot.LEGS, item(base + "_legs"));
        fighter.setItemSlot(EquipmentSlot.FEET, item(base + "_boots"));
    }

    public static void clear(AmbientFighterEntity fighter) {
        if (!available() || fighter == null || !fighter.getPersistentData().contains(BACKUP)) return;
        var backup = fighter.getPersistentData().getCompound(BACKUP);
        fighter.setItemSlot(EquipmentSlot.HEAD, ItemStack.of(backup.getCompound("Head")));
        fighter.setItemSlot(EquipmentSlot.CHEST, ItemStack.of(backup.getCompound("Chest")));
        fighter.setItemSlot(EquipmentSlot.LEGS, ItemStack.of(backup.getCompound("Legs")));
        fighter.setItemSlot(EquipmentSlot.FEET, ItemStack.of(backup.getCompound("Feet")));
        fighter.getPersistentData().remove(BACKUP);
    }

    private static boolean available() { return ModList.get().isLoaded(MOD_ID); }

    private static ItemStack item(String id) {
        var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(MOD_ID, id));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static String armorBase(String formId) {
        if (formId == null) return null;
        return switch (formId.toLowerCase(java.util.Locale.ROOT)) {
            case "superarmor", "superarmor1" -> "megamanx";
            case "superarmor2" -> "firstarmor";
            case "superarmor3" -> "zerofirst";
            case "superarmor4" -> "zeroexe";
            case "ultraarmor" -> "megamanexe";
            case "superultraarmor" -> "via";
            case "starforceultraarmor", "legendarystarforceultraarmor" -> "megamanstarforce";
            default -> null;
        };
    }
}
