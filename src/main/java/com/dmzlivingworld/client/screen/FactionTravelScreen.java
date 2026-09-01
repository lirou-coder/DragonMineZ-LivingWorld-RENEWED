package com.dmzlivingworld.client.screen;

import com.dmzlivingworld.network.FactionTravelScreenPacket;
import com.dmzlivingworld.network.LWNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Proper LW GUI for selecting a trusted faction contact as an Instant Transmission destination. */
@OnlyIn(Dist.CLIENT)
public final class FactionTravelScreen extends Screen implements LivingWorldScreenMarker {
    private final FactionTravelScreenPacket data;
    private int left, top, panelW, panelH, selected, scroll;
    private static final int ROW_H = 38;

    private FactionTravelScreen(FactionTravelScreenPacket data) {
        super(Component.literal("Quest Navigation")); this.data = data; this.selected = data.contacts().isEmpty() ? -1 : 0;
    }

    public static void open(FactionTravelScreenPacket packet) { Minecraft.getInstance().setScreen(new FactionTravelScreen(packet)); }

    @Override protected void init() {
        clearWidgets(); panelW = Math.min(560, Math.max(330, width - 20)); panelH = Math.min(390, Math.max(285, height - 20));
        left = (width - panelW) / 2; top = (height - panelH) / 2;
        selected = Math.min(selected, data.contacts().size() - 1); scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int listTop() { return top + 84; }
    private int listBottom() { return top + panelH - 62; }
    private int visibleRows() { return Math.max(1, (listBottom() - listTop()) / ROW_H); }
    private int maxScroll() { return Math.max(0, data.contacts().size() - visibleRows()); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics); LivingWorldGuiStyle.drawPanel(graphics, left, top, panelW, panelH);
        LivingWorldGuiStyle.drawFitted(graphics, font, "GET TO QUEST AREA — " + data.factionName(), left + 18, top + 14, panelW - 72, LivingWorldGuiStyle.GOLD);
        LivingWorldGuiStyle.drawButton(graphics, font, left + panelW - 40, top + 8, 28, 20, "×", mouseX, mouseY, true, false, false);
        LivingWorldGuiStyle.drawHeaderDivider(graphics, left + 8, left + panelW - 8, top + 40);
        LivingWorldGuiStyle.drawFitted(graphics, font, "Instant Transmission shortcut: " + data.instantTransmissionStatus(), left + 18, top + 51, panelW - 36,
                "READY".equals(data.instantTransmissionStatus()) ? LivingWorldGuiStyle.GREEN : LivingWorldGuiStyle.MUTED);
        LivingWorldGuiStyle.drawFitted(graphics, font, "Follow the live quest compass normally, or jump to a trusted contact (REL 60+) in the objective faction.", left + 18, top + 66, panelW - 36, LivingWorldGuiStyle.MUTED);

        int y = listTop(); int end = Math.min(data.contacts().size(), scroll + visibleRows());
        if (data.contacts().isEmpty()) {
            LivingWorldGuiStyle.drawInsetPanel(graphics, left + 18, y, panelW - 36, 54);
            LivingWorldGuiStyle.drawFitted(graphics, font, "No trusted contact near this objective yet.", left + 30, y + 12, panelW - 60, LivingWorldGuiStyle.TEXT);
            LivingWorldGuiStyle.drawFitted(graphics, font, "Use the live compass, or build a real relationship with one of their members to unlock this shortcut.", left + 30, y + 29, panelW - 60, LivingWorldGuiStyle.MUTED);
        } else for (int i = scroll; i < end; i++) {
            var c = data.contacts().get(i); boolean sel = i == selected;
            LivingWorldGuiStyle.drawInsetPanel(graphics, left + 18, y, panelW - 46, ROW_H - 4, sel ? LivingWorldGuiStyle.GOLD_DARK : LivingWorldGuiStyle.CARD_BORDER);
            LivingWorldGuiStyle.drawFitted(graphics, font, c.name(), left + 30, y + 7, panelW - 230, sel ? LivingWorldGuiStyle.GOLD : LivingWorldGuiStyle.TEXT);
            String meta = c.role() + " • " + c.rank() + (c.activity().isBlank() ? "" : " • " + c.activity());
            LivingWorldGuiStyle.drawFitted(graphics, font, meta, left + 30, y + 21, panelW - 230, LivingWorldGuiStyle.MUTED);
            LivingWorldGuiStyle.drawChip(graphics, font, "REL " + c.relationship(), left + panelW - 157, y + 7, 92, 20, LivingWorldGuiStyle.GREEN);
            y += ROW_H;
        }
        LivingWorldGuiStyle.drawScrollBar(graphics, left + panelW - 23, listTop(), listBottom(), scroll * ROW_H, maxScroll() * ROW_H);

        boolean canTravel = selected >= 0 && selected < data.contacts().size() && "READY".equals(data.instantTransmissionStatus());
        LivingWorldGuiStyle.drawButton(graphics, font, left + 18, top + panelH - 43, 166, 24, "Transmit to Contact", mouseX, mouseY, canTravel, false, true);
        LivingWorldGuiStyle.drawButton(graphics, font, left + panelW - 168, top + panelH - 43, 150, 24, "Back to Quest", mouseX, mouseY, true, false, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (LivingWorldGuiStyle.isInside(mouseX, mouseY, left + panelW - 40, top + 8, 28, 20)) { onClose(); return true; }
        int y = listTop(); int end = Math.min(data.contacts().size(), scroll + visibleRows());
        for (int i = scroll; i < end; i++, y += ROW_H) if (LivingWorldGuiStyle.isInside(mouseX, mouseY, left + 18, y, panelW - 46, ROW_H - 4)) { selected = i; return true; }
        if (LivingWorldGuiStyle.isInside(mouseX, mouseY, left + 18, top + panelH - 43, 166, 24)
                && selected >= 0 && selected < data.contacts().size() && "READY".equals(data.instantTransmissionStatus())) {
            LWNetwork.instantTransmitToRemembered(data.contacts().get(selected).recordId()); onClose(); return true;
        }
        if (LivingWorldGuiStyle.isInside(mouseX, mouseY, left + panelW - 168, top + panelH - 43, 150, 24)) {
            LWNetwork.requestMenu("faction_requests", data.factionSlot()); return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) scroll = Math.max(0, scroll - 1); else if (delta < 0) scroll = Math.min(maxScroll(), scroll + 1); return true;
    }
    @Override public boolean isPauseScreen() { return false; }
}
