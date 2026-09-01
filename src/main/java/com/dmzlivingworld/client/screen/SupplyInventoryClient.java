package com.dmzlivingworld.client.screen;

import com.dmzlivingworld.network.SupplyItemSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side inventory read for request presentation.
 *
 * R38.4 rule: this helper is never called from a render callback. Callers update a prepared string
 * on the client tick. Item IDs are also resolved only when the requested basket changes, and an
 * unchanged inventory reuses the previous summary rather than rebuilding it.
 */
final class SupplyInventoryClient {
    private static List<SupplyItemSnapshot> preparedLines = List.of();
    private static List<Item> preparedItems = List.of();
    private static int[] lastCounts = new int[0];
    private static String lastSummary = "";

    private SupplyInventoryClient() { }

    static String withLiveCounts(String base, List<SupplyItemSnapshot> lines) {
        String safeBase = base == null ? "" : base;
        String held = liveSummary(lines);
        return held.isBlank() ? safeBase : safeBase + " • YOU HAVE: " + held;
    }

    static String liveSummary(List<SupplyItemSnapshot> lines) {
        if (lines == null || lines.isEmpty()) return "";
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "";

        if (!lines.equals(preparedLines)) prepare(lines);

        var inv = mc.player.getInventory();
        int[] counts = new int[preparedItems.size()];
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty()) continue;
            Item held = stack.getItem();
            for (int i = 0; i < preparedItems.size(); i++) {
                Item wanted = preparedItems.get(i);
                if (wanted != Items.AIR && held == wanted) counts[i] += stack.getCount();
            }
        }
        if (java.util.Arrays.equals(counts, lastCounts)) return lastSummary;

        StringBuilder out = new StringBuilder(Math.max(32, preparedLines.size() * 18));
        for (int i = 0; i < preparedLines.size(); i++) {
            SupplyItemSnapshot line = preparedLines.get(i);
            if (preparedItems.get(i) == Items.AIR) continue;
            if (out.length() > 0) out.append(" • ");
            out.append(line.name()).append(" ×").append(counts[i]);
        }
        lastCounts = counts;
        lastSummary = out.toString();
        return lastSummary;
    }

    private static void prepare(List<SupplyItemSnapshot> lines) {
        preparedLines = List.copyOf(lines);
        ArrayList<Item> items = new ArrayList<>(preparedLines.size());
        for (SupplyItemSnapshot line : preparedLines) items.add(resolve(line.itemId()));
        preparedItems = List.copyOf(items);
        lastCounts = new int[0];
        lastSummary = "";
    }

    private static Item resolve(String id) {
        if (id == null || id.isBlank()) return Items.AIR;
        try {
            ResourceLocation key = new ResourceLocation(id);
            return BuiltInRegistries.ITEM.get(key);
        } catch (RuntimeException ignored) {
            return Items.AIR;
        }
    }
}
