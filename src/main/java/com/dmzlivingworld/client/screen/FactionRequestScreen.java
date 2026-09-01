package com.dmzlivingworld.client.screen;

import com.dmzlivingworld.network.FactionRequestScreenPacket;
import com.dmzlivingworld.network.LWNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** Dedicated faction request board with scrollable text and an in-place trusted-contact shortcut. */
@OnlyIn(Dist.CLIENT)
public final class FactionRequestScreen extends Screen implements LivingWorldScreenMarker {
    private FactionRequestScreenPacket data;
    private int left, top, widthPanel, heightPanel;
    private long receivedAtMs;
    private boolean refreshRequested;
    private int bodyScroll;
    private int bodyMaxScroll;
    private int selectedContact;
    private boolean contactsOpen;
    private String cachedLiveProgress = "";
    private long cachedLiveProgressTick = Long.MIN_VALUE;

    private FactionRequestScreen(FactionRequestScreenPacket data) {
        super(Component.literal("Faction Request"));
        this.data = data;
        this.receivedAtMs = System.currentTimeMillis();
        this.selectedContact = data.contacts().isEmpty() ? -1 : 0;
        refreshLiveProgress(true);
    }

    public static void open(FactionRequestScreenPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FactionRequestScreen current && current.data.factionSlot() == packet.factionSlot()) {
            java.util.UUID selectedId = current.selectedContact >= 0 && current.selectedContact < current.data.contacts().size()
                    ? current.data.contacts().get(current.selectedContact).recordId() : null;
            current.data = packet; current.receivedAtMs = System.currentTimeMillis(); current.refreshRequested = false;
            current.selectedContact = selectedId == null ? (packet.contacts().isEmpty() ? -1 : 0) : indexOf(packet, selectedId);
            current.contactsOpen = current.contactsOpen && !packet.contacts().isEmpty(); current.refreshLiveProgress(true); current.init();
            return;
        }
        mc.setScreen(new FactionRequestScreen(packet));
    }

    private static int indexOf(FactionRequestScreenPacket packet, java.util.UUID id) {
        for (int i = 0; i < packet.contacts().size(); i++) if (packet.contacts().get(i).recordId().equals(id)) return i;
        return packet.contacts().isEmpty() ? -1 : 0;
    }

    @Override
    protected void init() {
        clearWidgets();
        widthPanel = Math.min(610, Math.max(330, width - 16));
        heightPanel = Math.min(440, Math.max(330, height - 16));
        left = (width - widthPanel) / 2;
        top = (height - heightPanel) / 2;
        selectedContact = Math.min(selectedContact, data.contacts().size() - 1);
    }

    private long remainingSeconds() {
        if (data.activeForFaction() || data.activeElsewhere() || !data.hasRequest()) return Math.max(0L, data.refreshSeconds());
        long elapsed = Math.max(0L, (System.currentTimeMillis() - receivedAtMs) / 1000L);
        return Math.max(0L, data.refreshSeconds() - elapsed);
    }

    private String refreshLabel() {
        long s = remainingSeconds(), m = s / 60L, r = s % 60L;
        return m > 0 ? "Offer refreshes in " + m + "m " + r + "s" : "Offer refreshes in " + r + "s";
    }

    private void refreshLiveProgress(boolean force) {
        Minecraft mc = Minecraft.getInstance();
        long tick = mc.level == null ? 0L : mc.level.getGameTime();
        if (!force && tick - cachedLiveProgressTick < 5L) return;
        cachedLiveProgress = SupplyInventoryClient.withLiveCounts(data.progress(), data.supplyItems());
        cachedLiveProgressTick = tick;
    }

    @Override
    public void tick() {
        super.tick();
        refreshLiveProgress(false);
        if (!data.activeForFaction() && !data.activeElsewhere() && data.hasRequest() && remainingSeconds() <= 0L && !refreshRequested) {
            refreshRequested = true;
            LWNetwork.requestMenu("faction_requests", data.factionSlot());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        LivingWorldGuiStyle.drawPanel(graphics, left, top, widthPanel, heightPanel);
        graphics.drawString(font, data.factionName() + " — Requests", left + 48, top + 14, LivingWorldGuiStyle.GOLD, false);
        LivingWorldGuiStyle.drawButton(graphics, font, left + 12, top + 8, 28, 20, "‹", mouseX, mouseY, true, false, false);
        LivingWorldGuiStyle.drawButton(graphics, font, left + widthPanel - 40, top + 8, 28, 20, "×", mouseX, mouseY, true, false, false);
        String standing = data.standing() + "  " + (data.reputation() >= 0 ? "+" : "") + data.reputation();
        LivingWorldGuiStyle.drawChip(graphics, font, standing, left + widthPanel - 178, top + 8, 130, 20,
                data.reputation() <= -40 ? LivingWorldGuiStyle.RED : data.reputation() >= 35 ? LivingWorldGuiStyle.GREEN : LivingWorldGuiStyle.BLUE);
        LivingWorldGuiStyle.drawHeaderDivider(graphics, left + 8, left + widthPanel - 8, top + 40);

        int cardX = left + 18, cardY = top + 55, cardW = widthPanel - 36, cardH = heightPanel - 166;
        int accent = "Elite".equals(data.difficulty()) ? LivingWorldGuiStyle.RED : "Very Hard".equals(data.difficulty()) ? LivingWorldGuiStyle.ORANGE : LivingWorldGuiStyle.BLUE;
        LivingWorldGuiStyle.drawInsetPanel(graphics, cardX, cardY, cardW, cardH, accent);

        int titleRight = data.difficulty().isBlank() ? cardX + cardW - 14 : cardX + cardW - 120;
        List<FormattedCharSequence> titleLines = font.split(Component.literal(data.title()), Math.max(80, titleRight - (cardX + 14)));
        int ty = cardY + 11;
        for (int i = 0; i < Math.min(2, titleLines.size()); i++) { graphics.drawString(font, titleLines.get(i), cardX + 14, ty, LivingWorldGuiStyle.GOLD, false); ty += 11; }
        if (!data.difficulty().isBlank()) LivingWorldGuiStyle.drawChip(graphics, font, data.difficulty(), cardX + cardW - 104, cardY + 8, 90, 20, accent);

        int bodyTop = cardY + (titleLines.size() > 1 ? 40 : 32);
        int bodyBottom = cardY + cardH - 22;
        graphics.enableScissor(cardX + 8, bodyTop, cardX + cardW - 8, bodyBottom);
        int contentY = bodyTop - bodyScroll;
        contentY = drawWrapped(graphics, data.description(), cardX + 14, contentY, cardW - 28, LivingWorldGuiStyle.TEXT, 11);
        contentY += 7;
        if (!data.reward().isBlank()) { contentY = drawWrapped(graphics, "Reward • " + data.reward(), cardX + 14, contentY, cardW - 28, LivingWorldGuiStyle.GREEN, 11); contentY += 5; }
        if (!cachedLiveProgress.isBlank()) { contentY = drawWrapped(graphics, cachedLiveProgress, cardX + 14, contentY, cardW - 28, LivingWorldGuiStyle.BLUE, 11); contentY += 5; }
        if (!data.note().isBlank()) { contentY = drawWrapped(graphics, data.note(), cardX + 14, contentY, cardW - 28, LivingWorldGuiStyle.MUTED, 11); contentY += 4; }
        if (!data.activeForFaction() && !data.activeElsewhere() && data.hasRequest())
            contentY = drawWrapped(graphics, refreshLabel(), cardX + 14, contentY, cardW - 28, LivingWorldGuiStyle.MUTED, 11);
        graphics.disableScissor();
        int contentHeight = contentY + bodyScroll - bodyTop;
        bodyMaxScroll = Math.max(0, contentHeight - Math.max(10, bodyBottom - bodyTop));
        bodyScroll = Math.max(0, Math.min(bodyScroll, bodyMaxScroll));
        LivingWorldGuiStyle.drawScrollBar(graphics, cardX + cardW - 9, bodyTop, bodyBottom, bodyScroll, bodyMaxScroll);

        int navY = top + heightPanel - 102;
        String navInfo = data.activeForFaction()
                ? "Live compass is primary navigation" + (data.travelFactionName().isBlank() ? "" : " • contact region: " + data.travelFactionName())
                : "Accept the request to unlock its live compass and trusted-contact shortcut.";
        drawWrappedLimited(graphics, navInfo, left + 20, navY - 5, widthPanel - 40,
                data.activeForFaction() ? LivingWorldGuiStyle.BLUE : LivingWorldGuiStyle.MUTED, 2);

        int buttonY1 = top + heightPanel - 76;
        int buttonY2 = top + heightPanel - 44;
        boolean supply = data.canDeliver();
        if (data.canAccept())
            LivingWorldGuiStyle.drawButton(graphics, font, left + 18, buttonY1, 150, 24, "Accept Request", mouseX, mouseY, true, false, true);
        else if (data.activeForFaction() && supply)
            LivingWorldGuiStyle.drawButton(graphics, font, left + 18, buttonY1, 150, 24, "Deliver Items", mouseX, mouseY, true, false, true);
        else LivingWorldGuiStyle.drawButton(graphics, font, left + 18, buttonY1, 150, 24, data.activeElsewhere() ? "Another request active" : "Request active", mouseX, mouseY, false, false, false);
        if (data.canAbandon()) LivingWorldGuiStyle.drawDangerButton(graphics, font, left + widthPanel - 168, buttonY1, 150, 24, "Abandon Request", mouseX, mouseY, true);

        boolean hasContact = data.activeForFaction() && selectedContact >= 0 && selectedContact < data.contacts().size();
        String contactLabel = hasContact ? "Contact ▾  " + data.contacts().get(selectedContact).name() : "No trusted contact";
        LivingWorldGuiStyle.drawButton(graphics, font, left + 18, buttonY2, 220, 24, contactLabel, mouseX, mouseY, hasContact, contactsOpen, false);
        boolean canTravel = hasContact && "READY".equals(data.instantTransmissionStatus());
        LivingWorldGuiStyle.drawButton(graphics, font, left + 246, buttonY2, 118, 24, canTravel ? "Transmit" : data.instantTransmissionStatus(), mouseX, mouseY, canTravel, false, true);
        LivingWorldGuiStyle.drawButton(graphics, font, left + widthPanel - 168, buttonY2, 150, 24, "Back to Faction", mouseX, mouseY, true, false, false);

        if (contactsOpen && hasContact) drawContactDropdown(graphics, mouseX, mouseY, buttonY2);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawContactDropdown(GuiGraphics graphics, int mouseX, int mouseY, int buttonY) {
        int count = Math.min(5, data.contacts().size());
        int start = Math.max(0, Math.min(selectedContact - 2, data.contacts().size() - count));
        int y = buttonY - count * 27 - 4;
        for (int i = start; i < start + count; i++, y += 27) {
            var c = data.contacts().get(i); boolean selected = i == selectedContact;
            LivingWorldGuiStyle.drawButton(graphics, font, left + 18, y, 300, 24,
                    c.name() + " • REL " + c.relationship() + " • " + c.rank(), mouseX, mouseY, true, selected, false);
        }
    }

    private int drawWrapped(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color, int lineHeight) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text == null ? "" : text), Math.max(24, maxWidth));
        for (FormattedCharSequence line : lines) { graphics.drawString(font, line, x, y, color, false); y += lineHeight; }
        return y;
    }

    private void drawWrappedLimited(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text == null ? "" : text), Math.max(24, maxWidth));
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) graphics.drawString(font, lines.get(i), x, y + i * 11, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int buttonY1 = top + heightPanel - 76, buttonY2 = top + heightPanel - 44;
        if (LivingWorldGuiStyle.isInside(mouseX, mouseY, left + 12, top + 8, 28, 20)
                || LivingWorldGuiStyle.isInside(mouseX, mouseY, left + widthPanel - 168, buttonY2, 150, 24)) {
            LWNetwork.requestMenu("faction", data.factionSlot()); return true;
        }
        if (LivingWorldGuiStyle.isInside(mouseX, mouseY, left + widthPanel - 40, top + 8, 28, 20)) { onClose(); return true; }
        if (LivingWorldGuiStyle.isInside(mouseX, mouseY, left + 18, buttonY1, 150, 24)) {
            if (data.canAccept() && !data.type().isBlank()) { LWNetwork.requestFactionAction("accept", data.factionSlot()); return true; }
            if (data.activeForFaction() && data.canDeliver()) { LWNetwork.requestFactionAction("deliver", data.factionSlot()); return true; }
        }
        if (data.canAbandon() && LivingWorldGuiStyle.isInside(mouseX, mouseY, left + widthPanel - 168, buttonY1, 150, 24)) {
            LWNetwork.requestFactionAction("abandon", data.factionSlot()); return true;
        }

        boolean hasContact = data.activeForFaction() && selectedContact >= 0 && selectedContact < data.contacts().size();
        if (hasContact && LivingWorldGuiStyle.isInside(mouseX, mouseY, left + 18, buttonY2, 220, 24)) { contactsOpen = !contactsOpen; return true; }
        if (contactsOpen && hasContact) {
            int count = Math.min(5, data.contacts().size()); int start = Math.max(0, Math.min(selectedContact - 2, data.contacts().size() - count)); int y = buttonY2 - count * 27 - 4;
            for (int i = start; i < start + count; i++, y += 27) {
                if (LivingWorldGuiStyle.isInside(mouseX, mouseY, left + 18, y, 300, 24)) { selectedContact = i; contactsOpen = false; return true; }
            }
        }
        if (hasContact && "READY".equals(data.instantTransmissionStatus())
                && LivingWorldGuiStyle.isInside(mouseX, mouseY, left + 246, buttonY2, 118, 24)) {
            LWNetwork.instantTransmitToRemembered(data.contacts().get(selectedContact).recordId()); return true;
        }
        contactsOpen = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int cardY = top + 55, cardH = heightPanel - 166;
        if (LivingWorldGuiStyle.isInside(mouseX, mouseY, left + 18, cardY, widthPanel - 36, cardH)) {
            if (delta > 0) bodyScroll = Math.max(0, bodyScroll - 22); else if (delta < 0) bodyScroll = Math.min(bodyMaxScroll, bodyScroll + 22);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override public boolean isPauseScreen() { return false; }
}
