package com.dmzlivingworld.client.screen;

import com.dmzlivingworld.network.FactionDossierPacket;
import com.dmzlivingworld.client.FighterPortraitRenderState;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.LWEntities;
import com.dmzlivingworld.network.LWNetwork;
import com.dmzlivingworld.world.RedRibbonExperimentManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/** Main Living World interface. Uses the same card language as the fighter character panel. */
@OnlyIn(Dist.CLIENT)
public final class FactionDossierScreen extends Screen implements LivingWorldScreenMarker {
    private final String page;
    private final int slot;
    private String subtitle;
    private String actionTarget;
    private List<String> rawLines;
    private List<FactionDossierPacket.Portrait> portraitSnapshots;
    private final Map<UUID, AmbientFighterEntity> portraitEntities = new HashMap<>();
    private final List<VisualLine> visualLines = new ArrayList<>();
    private final List<MemberBlock> memberBlocks = new ArrayList<>();
    private int scroll;
    private int maxScroll;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int navWidth;
    private int peopleRefreshTicks;

    private record VisualLine(FormattedCharSequence text, String rawText, int color, int gapBefore, int factionSlot, UUID personRecordId, String clearAction, boolean heading, boolean fallen) {}
    private record MemberBlock(List<FormattedCharSequence> lines, String rawText, int color, int gapBefore, boolean heading) {}

    private FactionDossierScreen(FactionDossierPacket packet) {
        super(Component.literal(packet.title()));
        this.page = packet.page();
        this.slot = packet.slot();
        this.subtitle = packet.subtitle();
        this.actionTarget = packet.actionTarget();
        this.rawLines = packet.lines();
        this.portraitSnapshots = packet.portraits();
    }

    public static void open(FactionDossierPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FactionDossierScreen current
                && current.page.equals(packet.page()) && current.slot == packet.slot()) {
            current.applyPacket(packet);
            return;
        }
        minecraft.setScreen(new FactionDossierScreen(packet));
    }

    private void applyPacket(FactionDossierPacket packet) {
        boolean linesChanged = !Objects.equals(this.subtitle, packet.subtitle())
                || !Objects.equals(this.actionTarget, packet.actionTarget())
                || !Objects.equals(this.rawLines, packet.lines());
        boolean portraitsChanged = !Objects.equals(this.portraitSnapshots, packet.portraits());
        this.subtitle = packet.subtitle();
        this.actionTarget = packet.actionTarget();
        this.rawLines = packet.lines();
        this.portraitSnapshots = packet.portraits();
        this.peopleRefreshTicks = 0;
        if (minecraft != null && width > 0 && height > 0) {
            if (portraitsChanged) buildPortraitEntities();
            if (linesChanged) rebuildLines();
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Keep background dossier polling disabled while a GUI is open. Direct R37→R38.3
        // comparison showed polling was not the regression (R37 itself used it); this remains a
        // conservative safeguard. Pages refresh on open/switch or an explicit action.
    }

    @Override
    protected void init() {
        clearWidgets();
        panelWidth = Math.min(620, Math.max(1, width - 8));
        panelHeight = Math.min(390, Math.max(1, height - 8));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        navWidth = panelWidth >= 510 && !"fighter".equals(page) ? 136 : 0;
        buildPortraitEntities();
        rebuildLines();
    }

    private List<String> tabs() {
        List<String> tabs = new ArrayList<>(List.of("World", "Factions", "People", "Companion", "Wanted", "Antagonists", "World Menace"));
        tabs.add("Meditation");
        return tabs;
    }

    private int headerBottom() { return panelTop + 45; }
    private int footerY() { return panelTop + panelHeight - 27; }
    private boolean twoRowFighterActions() { return "fighter".equals(page) && panelWidth < 500; }
    private int fighterActionTop() { return twoRowFighterActions() ? panelTop + panelHeight - 47 : footerY(); }
    private int bodyTop() { return panelTop + 52; }
    private boolean factionDetailPage() { return ("faction".equals(page) || "faction_roster".equals(page) || "faction_requests".equals(page)) && slot > 0; }
    private boolean factionsHubPage() { return "factions".equals(page) || "faction_active".equals(page); }
    private String factionBondMarker() {
        if (!"faction".equals(page) || rawLines == null) return null;
        for (String line : rawLines) if (line != null && line.startsWith("@factionbond|")) return line;
        return null;
    }
    private int factionBondReputation() {
        String marker = factionBondMarker();
        if (marker == null) return 0;
        try {
            String[] parts = marker.split("\\|", 3);
            return Math.max(-100, Math.min(100, Integer.parseInt(parts[1])));
        } catch (RuntimeException ignored) { return 0; }
    }
    private String factionBondLabel() {
        String marker = factionBondMarker();
        if (marker == null) return "Unknown";
        String[] parts = marker.split("\\|", 3);
        return parts.length >= 3 && !parts[2].isBlank() ? parts[2] : "Neutral";
    }
    private int contentBodyTop() {
        int top = bodyTop() + ((factionDetailPage() || factionsHubPage()) ? 25 : 0);
        if ("faction".equals(page) && factionBondMarker() != null) top += 20;
        return top;
    }
    private int bodyBottom() { return (twoRowFighterActions() ? panelTop + panelHeight - 52 : footerY() - 5); }
    private int navLeft() { return panelLeft + 12; }
    /** Single source of truth for the visible navigation rail and its click targets. */
    private int navigationTabTop() { return bodyTop() + 29; }
    private int contentLeft() { return navWidth > 0 ? panelLeft + navWidth + 20 : panelLeft + 12; }
    private int contentRight() { return panelLeft + panelWidth - 12; }
    private int contentWidth() { return Math.max(24, contentRight() - contentLeft()); }
    private int helpX() { return panelLeft + panelWidth - 70; }
    private int closeX() { return panelLeft + panelWidth - 40; }
    private boolean hasBackDestination() { return !"world".equals(page); }
    private int backX() { return panelLeft + 12; }
    private void goBack() {
        if ("faction_roster".equals(page)) LWNetwork.requestMenu("faction", slot);
        else if ("faction".equals(page)) LWNetwork.requestMenu("factions", 0);
        else LWNetwork.requestMenu("world", 0);
    }

    // Roster pages now use the same portrait-card renderer as People/Wanted instead of a
    // separate text-only HUD. Keep the old member-block path dormant for save/UI compatibility.
    private boolean membersPage() { return false; }

    private void rebuildLines() {
        visualLines.clear();
        memberBlocks.clear();
        int wrap = Math.max(24, contentWidth() - 22);
        for (String raw : rawLines) {
            if (raw == null || raw.startsWith("@factionbond|")) continue;
            String line = raw;
            UUID personRecordId = extractPersonRecordId(raw);
            boolean fallen = raw.startsWith("@fallen:");
            if (personRecordId == null && fallen) personRecordId = extractMarkedRecordId(raw, "@fallen:");
            String clearAction = extractClearAction(raw);
            if (personRecordId != null || clearAction != null) line = stripPersonMarker(line);
            int factionSlot = extractSlot(line);
            int color = LivingWorldGuiStyle.TEXT;
            int gap = 0;
            boolean heading = false;
            if (clearAction != null) {
                color = ("companion_recall".equals(clearAction) || "people_sort".equals(clearAction))
                        ? LivingWorldGuiStyle.BLUE : LivingWorldGuiStyle.RED;
                gap = 4;
            }
            else if (line.startsWith("## ")) { line = line.substring(3); color = LivingWorldGuiStyle.GOLD; gap = 5; heading = true; }
            else if (line.startsWith("!! ")) { line = line.substring(3); color = LivingWorldGuiStyle.RED; gap = 2; }
            else if (line.startsWith("+ ")) { line = line.substring(2); color = LivingWorldGuiStyle.GREEN; }
            else if (line.startsWith("~ ")) { line = line.substring(2); color = LivingWorldGuiStyle.BLUE; }
            else if (line.startsWith("* ")) { line = "• " + line.substring(2); color = 0xFFC7CDD3; }
            else if (line.startsWith(". ")) { line = line.substring(2); color = LivingWorldGuiStyle.MUTED; }
            List<FormattedCharSequence> wrapped;
            // Portrait-card pages keep one person/threat on one compact visual row. People rows
            // remain clickable; Wanted/Menace reuse the visual language without pretending they are memories.
            if ((portraitCardPage() && personRecordId != null) || ("world".equals(page) && factionSlot > 0)) {
                // Portrait people and the two Major Powers are deliberately one visual row / one hit target.
                String fitted = LivingWorldGuiStyle.fitText(font, line, wrap);
                wrapped = List.of(Component.literal(fitted).getVisualOrderText());
            } else {
                wrapped = font.split(Component.literal(line), wrap);
                if (wrapped.isEmpty()) wrapped = List.of(Component.empty().getVisualOrderText());
            }
            if (membersPage()) {
                memberBlocks.add(new MemberBlock(List.copyOf(wrapped), line, color, gap, heading));
                continue;
            }
            boolean first = true;
            for (FormattedCharSequence seq : wrapped) {
                visualLines.add(new VisualLine(seq, line, fallen ? LivingWorldGuiStyle.MUTED : color, first ? gap : 0, factionSlot, personRecordId, clearAction, heading, fallen));
                first = false;
                heading = false;
            }
        }
        int content = 8;
        if (membersPage()) {
            for (MemberBlock block : memberBlocks) {
                content += block.gapBefore + (block.heading ? 15 : Math.max(20, 8 + block.lines.size() * 10) + 3);
            }
        } else {
            for (VisualLine line : visualLines) content += line.gapBefore + (line.heading ? 15 : (line.clearAction != null ? 22 : visualLineHeight(line)));
        }
        maxScroll = Math.max(0, content - Math.max(1, bodyBottom() - contentBodyTop() - 10));
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    private boolean portraitCardPage() {
        return "people".equals(page) || "wanted".equals(page) || "menace".equals(page)
                || "faction_roster".equals(page) || !portraitSnapshots.isEmpty();
    }

    private int visualLineHeight(VisualLine line) {
        return portraitCardPage() && line.personRecordId != null ? 30 : 11;
    }

    private void buildPortraitEntities() {
        portraitEntities.clear();
        if (!portraitCardPage() || minecraft == null || minecraft.level == null) return;
        for (FactionDossierPacket.Portrait snapshot : portraitSnapshots) {
            try {
                AmbientFighterEntity portrait = LWEntities.AMBIENT_FIGHTER.get().create(minecraft.level);
                if (portrait == null) continue;
                portrait.initializePortraitFromMemory(snapshot.appearance());
                boolean fallen = rawLines.stream().anyMatch(line -> line != null
                        && line.startsWith("@fallen:" + snapshot.recordId()));
                if (fallen) portrait.configureArchivedPortrait();
                portraitEntities.put(snapshot.recordId(), portrait);
            } catch (RuntimeException ignored) { }
        }
    }

    private int tabGap() { return 4; }
    private int tabWidth(List<String> tabs) {
        int available = panelWidth - 24 - tabGap() * (tabs.size() - 1);
        return Math.max(20, available / Math.max(1, tabs.size()));
    }
    private int tabX(int index, List<String> tabs) { return panelLeft + 12 + index * (tabWidth(tabs) + tabGap()); }

    private boolean tabSelected(String tab) {
        return switch (tab) {
            case "World" -> "world".equals(page);
            case "Factions" -> "factions".equals(page) || "faction_active".equals(page) || "faction".equals(page) || "faction_roster".equals(page);
            case "People" -> "people".equals(page);
            case "Companion" -> "travel".equals(page);
            case "Wanted" -> "wanted".equals(page);
            case "Antagonists" -> "antagonists".equals(page);
            case "World Menace" -> "menace".equals(page);
            case "Meditation" -> "meditation".equals(page);
            default -> false;
        };
    }

    private String pageForTab(String tab) {
        return switch (tab) {
            case "Factions" -> "factions";
            case "People" -> "people";
            case "Companion" -> "travel";
            case "Wanted" -> "wanted";
            case "Antagonists" -> "antagonists";
            case "World Menace" -> "menace";
            case "Meditation" -> "meditation";
            default -> "world";
        };
    }

    private String pageChip() {
        return switch (page) {
            case "factions" -> "FACTIONS";
            case "faction_active" -> "ACTIVE QUEST";
            case "faction" -> slot > 0 ? "FACTION #" + slot : "FACTION";
            case "faction_roster" -> slot > 0 ? "MEMBERS #" + slot : "MEMBERS";
            case "people" -> "PEOPLE";
            case "travel" -> "COMPANION";
            case "wanted" -> "WANTED";
            case "antagonists" -> "ANTAGONISTS";
            case "menace" -> "WORLD MENACE";
            case "meditation" -> "MEDITATION";
            case "fighter" -> "FIGHTER";
            default -> "WORLD";
        };
    }

    private int pageChipColor() {
        return switch (page) {
            case "wanted", "antagonists", "menace" -> LivingWorldGuiStyle.RED;
            case "people" -> LivingWorldGuiStyle.GREEN;
            case "travel" -> LivingWorldGuiStyle.BLUE;
            case "meditation" -> LivingWorldGuiStyle.BLUE;
            case "factions", "faction_active", "faction", "faction_roster" -> 0xFFFFE29A;
            default -> LivingWorldGuiStyle.GOLD;
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        LivingWorldGuiStyle.drawPanel(graphics, panelLeft, panelTop, panelWidth, panelHeight);
        LivingWorldGuiStyle.drawHeaderDivider(graphics, panelLeft + 7, panelLeft + panelWidth - 7, headerBottom());

        drawHeader(graphics, mouseX, mouseY);
        if (navWidth > 0) drawNavigationRail(graphics, mouseX, mouseY);
        else if (!"fighter".equals(page)) drawBottomTabs(graphics, mouseX, mouseY);

        if ("fighter".equals(page) && !actionTarget.isBlank()) drawFighterActions(graphics, mouseX, mouseY);
        if (factionsHubPage()) drawFactionsHubSubtabs(graphics, mouseX, mouseY);
        if (factionDetailPage()) drawFactionSubtabs(graphics, mouseX, mouseY);
        if ("faction".equals(page) && factionBondMarker() != null) drawFactionBond(graphics);
        drawBody(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean compact = panelWidth < 390;
        int closeX = closeX();
        int helpX = helpX();
        int rightReserved = compact ? closeX - 7 : helpX - 7;
        int chipWidth = compact ? 0 : Math.min(94, Math.max(62, font.width(pageChip()) + 14));
        int chipX = compact ? rightReserved : helpX - chipWidth - 7;
        int titleRight = compact ? rightReserved : chipX - 8;
        if (compact && navWidth == 0 && "world".equals(page)) titleRight -= 60;
        if (!compact && factionDetailPage()) titleRight -= 61;
        if (!compact && navWidth == 0 && "world".equals(page)) titleRight -= 77;

        int titleLeft = hasBackDestination() ? panelLeft + 49 : panelLeft + 13;
        LivingWorldGuiStyle.drawFitted(graphics, font, title.getString(), titleLeft, panelTop + 10,
                Math.max(24, titleRight - titleLeft), 0xFFFFE29A);
        if (!subtitle.isBlank() && panelHeight >= 190) {
            LivingWorldGuiStyle.drawFitted(graphics, font, subtitle, titleLeft, panelTop + 26,
                    Math.max(24, titleRight - titleLeft), LivingWorldGuiStyle.MUTED);
        }
        if (hasBackDestination()) LivingWorldGuiStyle.drawButton(graphics, font, backX(), panelTop + 8, 30, 20, "‹",
                mouseX, mouseY, true, false, false);
        if (!compact) {
            LivingWorldGuiStyle.drawChip(graphics, font, pageChip(), chipX, panelTop + 8, chipWidth, 20, pageChipColor());
        }

        if (navWidth == 0 && "world".equals(page)) {
            int settingsW = compact ? 54 : 70;
            int settingsX = compact ? closeX - settingsW - 6 : Math.max(panelLeft + 90, chipX - 77);
            if (settingsX > panelLeft + 68) LivingWorldGuiStyle.drawButton(graphics, font, settingsX, panelTop + 8, settingsW, 20,
                    compact ? "Config" : "Settings", mouseX, mouseY, true, false, false);
        }
        if (!compact && factionDetailPage()) {
            int rightArrow = chipX - 32;
            int leftArrow = rightArrow - 29;
            LivingWorldGuiStyle.drawArrowButton(graphics, font, leftArrow, panelTop + 8, 25, 20, "<", mouseX, mouseY, slot > 1);
            LivingWorldGuiStyle.drawArrowButton(graphics, font, rightArrow, panelTop + 8, 25, 20, ">", mouseX, mouseY, true);
        }

        if (!compact) LivingWorldGuiStyle.drawButton(graphics, font, helpX, panelTop + 8, 25, 20, "?",
                mouseX, mouseY, true, false, false);
        LivingWorldGuiStyle.drawButton(graphics, font, closeX, panelTop + 8, 25, 20, "×",
                mouseX, mouseY, true, false, false);
    }

    private void drawNavigationRail(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = navLeft();
        int top = bodyTop();
        int height = bodyBottom() - top;
        LivingWorldGuiStyle.drawInsetPanel(graphics, left, top, navWidth, height);
        LivingWorldGuiStyle.drawFitted(graphics, font, "LIVING WORLD", left + 9, top + 9, navWidth - 18, 0xFFFFE29A);
        List<String> tabs = tabs();
        int y = navigationTabTop();
        int buttonW = navWidth - 14;
        for (int i = 0; i < tabs.size(); i++) {
            String tab = tabs.get(i);
            LivingWorldGuiStyle.drawButton(graphics, font, left + 7, y + i * 25, buttonW, 20, tab,
                    mouseX, mouseY, true, tabSelected(tab), false);
        }

        int settingsY = top + height - 28;
        LivingWorldGuiStyle.drawButton(graphics, font, left + 7, settingsY, buttonW, 20, "World Settings",
                mouseX, mouseY, true, false, false);
    }

    private void drawBottomTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        List<String> tabs = tabs();
        for (int i = 0; i < tabs.size(); i++) {
            String tab = tabs.get(i);
            LivingWorldGuiStyle.drawButton(graphics, font, tabX(i, tabs), footerY(), tabWidth(tabs), 18, tab,
                    mouseX, mouseY, true, tabSelected(tab), false);
        }
    }

    private void drawFighterActions(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean meditation = true;
        String[] actions = meditation ? new String[]{"Talk", "Go Along", "Come Along", "Fusion", "Meditate"} : new String[]{"Talk", "Fusion"};
        int gap = 5;
        int cols = twoRowFighterActions() ? 3 : actions.length;
        int available = panelWidth - 24 - gap * (cols - 1);
        int actionWidth = Math.max(24, available / cols);
        int top = fighterActionTop();
        for (int i = 0; i < actions.length; i++) {
            int row = i / cols, col = i % cols;
            int x = panelLeft + 12 + col * (actionWidth + gap);
            int y = top + row * 20;
            LivingWorldGuiStyle.drawButton(graphics, font, x, y, actionWidth, 18, actions[i],
                    mouseX, mouseY, true, false, false);
        }
    }

    private void drawFactionsHubSubtabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = contentLeft();
        int y = bodyTop();
        int gap = 5;
        int w = Math.min(132, Math.max(74, (contentWidth() - gap) / 2));
        LivingWorldGuiStyle.drawButton(graphics, font, x, y, w, 20, "Organizations", mouseX, mouseY, true, "factions".equals(page), false);
        LivingWorldGuiStyle.drawButton(graphics, font, x + w + gap, y, w, 20, "Active Quest", mouseX, mouseY, true, "faction_active".equals(page), false);
    }

    private void drawFactionSubtabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = contentLeft();
        int y = bodyTop();
        int gap = 5;
        int w = Math.min(92, Math.max(24, (contentWidth() - gap * 2) / 3));
        LivingWorldGuiStyle.drawButton(graphics, font, x, y, w, 20, "Overview", mouseX, mouseY, true, "faction".equals(page), false);
        LivingWorldGuiStyle.drawButton(graphics, font, x + w + gap, y, w, 20, "Members", mouseX, mouseY, true, "faction_roster".equals(page), false);
        LivingWorldGuiStyle.drawButton(graphics, font, x + (w + gap) * 2, y, w, 20, "Requests", mouseX, mouseY, true, "faction_requests".equals(page), false);
    }

    private void drawFactionBond(GuiGraphics graphics) {
        int x = contentLeft();
        int y = bodyTop() + 25;
        int w = contentWidth();
        int rel = factionBondReputation();
        int center = x + w / 2;
        graphics.fill(x, y, x + w, y + 14, 0xFF12161B);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 13, 0xFF252D36);
        graphics.fill(center, y + 1, center + 1, y + 13, 0xFF6C747D);
        if (rel < 0) {
            int px = Math.max(x + 1, center + (w / 2 - 2) * rel / 100);
            graphics.fill(px, y + 2, center, y + 12, 0xFF9C4A4A);
        } else if (rel > 0) {
            int px = Math.min(x + w - 1, center + (w / 2 - 2) * rel / 100);
            graphics.fill(center + 1, y + 2, px, y + 12, 0xFF4F9A63);
        }
        LivingWorldGuiStyle.drawCentered(graphics, font, factionBondLabel() + "  •  " + rel, x, y, w, 14, 0xFFFFFFFF);
    }

    private void drawBody(GuiGraphics graphics, int mouseX, int mouseY) {
        if (membersPage()) {
            drawMembersBody(graphics, mouseX, mouseY);
            return;
        }
        int left = contentLeft();
        int top = contentBodyTop();
        int right = contentRight();
        int bottom = bodyBottom();
        LivingWorldGuiStyle.drawInsetPanel(graphics, left, top, right - left, bottom - top);

        int clipLeft = left + 5;
        int clipTop = top + 5;
        int clipRight = right - 5;
        int clipBottom = bottom - 5;
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        int y = clipTop + 2 - scroll;
        for (VisualLine line : visualLines) {
            y += line.gapBefore;
            if (line.heading) {
                if (y > clipTop - 16 && y < clipBottom + 2) {
                    LivingWorldGuiStyle.drawSectionHeader(graphics, font,
                            line.rawText, clipLeft + 2, y, Math.max(20, clipRight - clipLeft - 7));
                }
                y += 15;
                continue;
            }

            if (line.clearAction != null) {
                int buttonW = Math.min(Math.max(112, font.width(line.rawText) + 22), Math.max(112, clipRight - clipLeft - 16));
                if (y > clipTop - 20 && y < clipBottom + 2) {
                    if ("companion_recall".equals(line.clearAction) || "people_sort".equals(line.clearAction)
                            || "activequest_open".equals(line.clearAction) || "activequest_travel".equals(line.clearAction)
                            || (line.clearAction != null && line.clearAction.startsWith("request_accept_")))
                        LivingWorldGuiStyle.drawButton(graphics, font, clipLeft + 8, y, buttonW, 18, line.rawText, mouseX, mouseY, true, false, true);
                    else LivingWorldGuiStyle.drawDangerButton(graphics, font, clipLeft + 8, y, buttonW, 18,
                            line.rawText, mouseX, mouseY, true);
                }
                y += 22;
                continue;
            }
            int rowH = visualLineHeight(line);
            if (portraitCardPage() && line.personRecordId != null && y >= clipTop - rowH && y <= clipBottom) {
                // Give remembered people their own compact dossier cards instead of letting
                // portraits and text visually run into the next person.
                graphics.fill(clipLeft + 2, y - 1, clipRight - 3, y + rowH - 2, LivingWorldGuiStyle.CARD_BORDER);
                graphics.fill(clipLeft + 3, y, clipRight - 4, y + rowH - 3, line.fallen ? 0xFF24282C : LivingWorldGuiStyle.CARD_ALT);
                graphics.fill(clipLeft + 3, y, clipLeft + 5, y + rowH - 3, line.fallen ? LivingWorldGuiStyle.NEUTRAL : LivingWorldGuiStyle.GREEN);
            }
            if ((line.factionSlot > 0 || line.personRecordId != null) && mouseY >= y - 1 && mouseY <= y + rowH - 1
                    && mouseX >= clipLeft && mouseX <= clipRight && y >= clipTop - rowH && y <= clipBottom) {
                graphics.fill(clipLeft + 5, y, clipRight - 4, y + rowH - 3, LivingWorldGuiStyle.HOVER_ROW);
            }
            if (y > clipTop - rowH && y < clipBottom + 4) {
                int textX = clipLeft + 8;
                int textY = y;
                if (portraitCardPage() && line.personRecordId != null) {
                    AmbientFighterEntity portrait = portraitEntities.get(line.personRecordId);
                    if (portrait != null) {
                        try {
                            FighterPortraitRenderState.begin();
                            try {
                                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, clipLeft + 18, y + 28, 12, 0.0F, 0.0F, portrait);
                            } finally { FighterPortraitRenderState.end(); }
                        } catch (RuntimeException ignored) { }
                        if (line.fallen) {
                            // Deliberately desaturate/mute the historical portrait without changing its stored appearance.
                            graphics.fill(clipLeft + 7, y + 2, clipLeft + 31, y + 29, 0xB05A6066);
                            LivingWorldGuiStyle.drawFitted(graphics, font, "PASSED AWAY", clipLeft + 36, y + 3, Math.max(40, clipRight - clipLeft - 44), LivingWorldGuiStyle.NEUTRAL);
                        } else if ("faction_roster".equals(page)) {
                            // Roster portraits are intentionally historical: show the last appearance this player actually saw.
                            LivingWorldGuiStyle.drawFitted(graphics, font, "LAST SEEN", clipLeft + 36, y + 3, Math.max(40, clipRight - clipLeft - 44), LivingWorldGuiStyle.BLUE);
                        }
                    }
                    textX = clipLeft + 37;
                    textY = (line.fallen || "faction_roster".equals(page)) ? y + 15 : y + 10;
                }
                graphics.drawString(font, line.text, textX, textY, line.color, false);
            }
            y += rowH;
        }
        graphics.disableScissor();
        LivingWorldGuiStyle.drawScrollBar(graphics, right - 4, clipTop, clipBottom, scroll, maxScroll);
    }

    /** Member rows use the same bordered/accented card language as Shift + Right-click fighter profiles. */
    private void drawMembersBody(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = contentLeft();
        int top = contentBodyTop();
        int right = contentRight();
        int bottom = bodyBottom();
        LivingWorldGuiStyle.drawInsetPanel(graphics, left, top, right - left, bottom - top);
        int clipLeft = left + 5, clipTop = top + 5, clipRight = right - 5, clipBottom = bottom - 5;
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        int y = clipTop + 2 - scroll;
        for (MemberBlock block : memberBlocks) {
            y += block.gapBefore;
            if (block.heading) {
                if (y > clipTop - 16 && y < clipBottom + 2) {
                    LivingWorldGuiStyle.drawSectionHeader(graphics, font, block.rawText, clipLeft + 2, y, Math.max(20, clipRight - clipLeft - 7));
                }
                y += 15;
                continue;
            }
            int cardH = Math.max(20, 8 + block.lines.size() * 10);
            if (y + cardH > clipTop && y < clipBottom) {
                int accent = block.color == LivingWorldGuiStyle.RED ? LivingWorldGuiStyle.RED
                        : block.color == LivingWorldGuiStyle.GREEN ? LivingWorldGuiStyle.GREEN
                        : block.color == LivingWorldGuiStyle.BLUE ? LivingWorldGuiStyle.BLUE
                        : block.color == LivingWorldGuiStyle.MUTED ? LivingWorldGuiStyle.NEUTRAL
                        : LivingWorldGuiStyle.GOLD_DARK;
                graphics.fill(clipLeft + 2, y, clipRight - 4, y + cardH, LivingWorldGuiStyle.CARD_BORDER);
                graphics.fill(clipLeft + 3, y + 1, clipRight - 5, y + cardH - 1, LivingWorldGuiStyle.CARD_ALT);
                graphics.fill(clipLeft + 3, y + 1, clipLeft + 7, y + cardH - 1, accent);
                int textY = y + 5;
                for (FormattedCharSequence text : block.lines) {
                    graphics.drawString(font, text, clipLeft + 12, textY, block.color, false);
                    textY += 10;
                }
            }
            y += cardH + 3;
        }
        graphics.disableScissor();
        LivingWorldGuiStyle.drawScrollBar(graphics, right - 4, clipTop, clipBottom, scroll, maxScroll);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (hasBackDestination() && LivingWorldGuiStyle.isInside(mouseX, mouseY, backX(), panelTop + 8, 30, 20)) {
            goBack();
            return true;
        }

        if (navWidth > 0 && !"fighter".equals(page)) {
            List<String> tabs = tabs();
            int y = navigationTabTop();
            int buttonW = navWidth - 14;
            for (int i = 0; i < tabs.size(); i++) {
                if (LivingWorldGuiStyle.isInside(mouseX, mouseY, navLeft() + 7, y + i * 25, buttonW, 20)) {
                    LWNetwork.requestMenu(pageForTab(tabs.get(i)), 0);
                    return true;
                }
            }
            if (LivingWorldGuiStyle.isInside(mouseX, mouseY, navLeft() + 7, bodyBottom() - 28, buttonW, 20)) {
                LWNetwork.requestMenu("settings", 0);
                return true;
            }
        } else if (!"fighter".equals(page)) {
            List<String> tabs = tabs();
            for (int i = 0; i < tabs.size(); i++) {
                if (LivingWorldGuiStyle.isInside(mouseX, mouseY, tabX(i, tabs), footerY(), tabWidth(tabs), 18)) {
                    LWNetwork.requestMenu(pageForTab(tabs.get(i)), 0);
                    return true;
                }
            }
            if ("world".equals(page)) {
                boolean compact = panelWidth < 390;
                int settingsW = compact ? 54 : 70;
                int chipWidth = Math.min(94, Math.max(62, font.width(pageChip()) + 14));
                int chipX = helpX() - chipWidth - 7;
                int settingsX = compact ? closeX() - settingsW - 6 : Math.max(panelLeft + 90, chipX - 77);
                if (settingsX > panelLeft + 68 && LivingWorldGuiStyle.isInside(mouseX, mouseY, settingsX, panelTop + 8, settingsW, 20)) {
                    LWNetwork.requestMenu("settings", 0);
                    return true;
                }
            }
        }

        if (factionsHubPage()) {
            int sx = contentLeft();
            int sy = bodyTop();
            int gap = 5;
            int sw = Math.min(132, Math.max(74, (contentWidth() - gap) / 2));
            if (LivingWorldGuiStyle.isInside(mouseX, mouseY, sx, sy, sw, 20)) {
                if (!"factions".equals(page)) LWNetwork.requestMenu("factions", 0);
                return true;
            }
            if (LivingWorldGuiStyle.isInside(mouseX, mouseY, sx + sw + gap, sy, sw, 20)) {
                if (!"faction_active".equals(page)) LWNetwork.requestMenu("faction_active", 0);
                return true;
            }
        }

        if (factionDetailPage()) {
            int sx = contentLeft();
            int sy = bodyTop();
            int gap = 5;
            int sw = Math.min(92, Math.max(24, (contentWidth() - gap * 2) / 3));
            if (LivingWorldGuiStyle.isInside(mouseX, mouseY, sx, sy, sw, 20)) {
                if (!"faction".equals(page)) LWNetwork.requestMenu("faction", slot);
                return true;
            }
            if (LivingWorldGuiStyle.isInside(mouseX, mouseY, sx + sw + gap, sy, sw, 20)) {
                if (!"faction_roster".equals(page)) LWNetwork.requestMenu("faction_roster", slot);
                return true;
            }
            if (LivingWorldGuiStyle.isInside(mouseX, mouseY, sx + (sw + gap) * 2, sy, sw, 20)) {
                if (!"faction_requests".equals(page)) LWNetwork.requestMenu("faction_requests", slot);
                return true;
            }
        }

        if (factionDetailPage() && panelWidth >= 390) {
            int chipWidth = Math.min(94, Math.max(62, font.width(pageChip()) + 14));
            int chipX = helpX() - chipWidth - 7;
            int rightArrow = chipX - 7 - 25;
            int leftArrow = rightArrow - 29;
            if (slot > 1 && LivingWorldGuiStyle.isInside(mouseX, mouseY, leftArrow, panelTop + 8, 25, 20)) {
                LWNetwork.requestMenu(page, slot - 1);
                return true;
            }
            if (LivingWorldGuiStyle.isInside(mouseX, mouseY, rightArrow, panelTop + 8, 25, 20)) {
                LWNetwork.requestMenu(page, slot + 1);
                return true;
            }
        }

        if ("fighter".equals(page) && !actionTarget.isBlank()) {
            boolean meditation = true;
            String[] actions = meditation ? new String[]{"talk", "join", "companion", "fusion", "meditate"} : new String[]{"talk", "fusion"};
            int gap = 5;
            int cols = twoRowFighterActions() ? 3 : actions.length;
            int available = panelWidth - 24 - gap * (cols - 1);
            int actionWidth = Math.max(24, available / cols);
            int top = fighterActionTop();
            for (int i = 0; i < actions.length; i++) {
                int row = i / cols, col = i % cols;
                int x = panelLeft + 12 + col * (actionWidth + gap);
                int y = top + row * 20;
                if (LivingWorldGuiStyle.isInside(mouseX, mouseY, x, y, actionWidth, 18)) {
                    try {
                        LWNetwork.requestFighterAction(actions[i], java.util.UUID.fromString(actionTarget));
                        onClose();
                    } catch (IllegalArgumentException ignored) { }
                    return true;
                }
            }
        }

        if (panelWidth >= 390 && LivingWorldGuiStyle.isInside(mouseX, mouseY, helpX(), panelTop + 8, 25, 20)) {
            LivingWorldGuideScreen.open();
            return true;
        }
        if (LivingWorldGuiStyle.isInside(mouseX, mouseY, closeX(), panelTop + 8, 25, 20)) {
            onClose();
            return true;
        }

        if ("factions".equals(page)) {
            int clipTop = contentBodyTop() + 5;
            int clipBottom = bodyBottom() - 5;
            if (mouseX >= contentLeft() + 5 && mouseX <= contentRight() - 5 && mouseY >= clipTop && mouseY <= clipBottom) {
                int y = clipTop + 2 - scroll;
                for (VisualLine line : visualLines) {
                    y += line.gapBefore;
                    if (line.heading) { y += 15; continue; }
                    if (line.factionSlot > 0 && mouseY >= y - 1 && mouseY <= y + 10) {
                        LWNetwork.requestMenu("faction", line.factionSlot);
                        return true;
                    }
                    y += 11;
                }
            }
        }

        if ("people".equals(page) || "wanted".equals(page) || "travel".equals(page) || "world".equals(page) || "meditation".equals(page)
                || "faction_active".equals(page) || "faction_roster".equals(page) || "faction_requests".equals(page) || "menace".equals(page) || "antagonists".equals(page)) {
            int clipLeft = contentLeft() + 5;
            int clipTop = contentBodyTop() + 5;
            int clipRight = contentRight() - 5;
            int clipBottom = bodyBottom() - 5;
            if (mouseX >= clipLeft && mouseX <= clipRight && mouseY >= clipTop && mouseY <= clipBottom) {
                int y = clipTop + 2 - scroll;
                for (VisualLine line : visualLines) {
                    y += line.gapBefore;
                    if (line.heading) { y += 15; continue; }
                    if (line.clearAction != null) {
                        int buttonW = Math.min(Math.max(112, font.width(line.rawText) + 22), Math.max(112, clipRight - clipLeft - 16));
                        if (LivingWorldGuiStyle.isInside(mouseX, mouseY, clipLeft + 8, y, buttonW, 18)) {
                            String action = line.clearAction;
                            if ("menace_tp".equals(action)) { LWNetwork.requestMenu("menace_tp", 0); return true; }
                            if ("menace_spawn".equals(action)) { LWNetwork.requestMenu("menace_spawn", 0); return true; }
                            if ("activequest_open".equals(action) && slot > 0) { LWNetwork.requestMenu("faction_requests", slot); return true; }
                            if ("activequest_travel".equals(action) && slot > 0) { LWNetwork.requestMenu("faction_travel", slot); return true; }
                            if (action.startsWith("request_accept_") || "request_abandon".equals(action)) {
                                LWNetwork.requestFactionAction(action.substring("request_".length()), slot);
                                return true;
                            }
                            if ("companion_recall".equals(action) || "meditation_invite".equals(action) || "people_sort".equals(action)) {
                                LWNetwork.peopleMemoryAction(action, null);
                                return true;
                            }
                            String title = "companion_end".equals(action) ? "End this trip?" : "Are you sure?";
                            String what = "fallen".equals(action) ? "Clear fallen history from this view?"
                                    : "companion_end".equals(action) ? "You and your companion will stop travelling together."
                                    : "Forget all remembered people?";
                            minecraft.setScreen(new ConfirmScreen(ok -> {
                                if (ok) LWNetwork.peopleMemoryAction(action, null);
                                else minecraft.setScreen(this);
                            }, Component.literal(title), Component.literal(what)));
                            return true;
                        }
                        y += 22;
                        continue;
                    }
                    int rowH = visualLineHeight(line);
                    if (mouseY >= y - 1 && mouseY <= y + rowH - 1 && line.factionSlot > 0 && "world".equals(page)) {
                        LWNetwork.requestMenu("faction", line.factionSlot);
                        return true;
                    }
                    if (mouseY >= y - 1 && mouseY <= y + rowH - 1 && line.personRecordId != null) {
                        if ("menace".equals(page)) LWNetwork.requestMenu("menace_profile", RedRibbonExperimentManager.dossierRecordId().equals(line.personRecordId) ? 1 : 0);
                        else if ("wanted".equals(page) && line.factionSlot > 0) LWNetwork.requestMenu("wanted_profile", line.factionSlot);
                        else if (line.fallen && line.factionSlot > 0) LWNetwork.requestMenu("fallen_profile", line.factionSlot);
                        else LWNetwork.requestRememberedFighter(line.personRecordId);
                        return true;
                    }
                    y += rowH;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll <= 0) return super.mouseScrolled(mouseX, mouseY, delta);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int)Math.round(delta * 26.0D)));
        return true;
    }

    private static String extractClearAction(String raw) {
        if (raw == null) return null;
        int end = raw.indexOf('|');
        if (raw.startsWith("@clear:") && end > 7) {
            String action = raw.substring(7, end);
            return ("known".equals(action) || "fallen".equals(action)) ? ("known".equals(action) ? "all" : "fallen") : null;
        }
        if (raw.startsWith("@travel:") && end > 8) {
            String action = raw.substring(8, end);
            if ("recall".equals(action)) return "companion_recall";
            if ("end".equals(action)) return "companion_end";
        }
        if (raw.startsWith("@meditation:") && end > 12) {
            String action = raw.substring(12, end);
            if ("invite".equals(action)) return "meditation_invite";
        }
        if (raw.startsWith("@menace:") && end > 8) {
            String action = raw.substring(8, end);
            if ("tp".equals(action)) return "menace_tp";
            if ("spawn".equals(action)) return "menace_spawn";
        }
        if (raw.startsWith("@activequest:") && end > 13) {
            String action = raw.substring(13, end);
            if ("open".equals(action)) return "activequest_open";
            if ("travel".equals(action)) return "activequest_travel";
        }
        if (raw.startsWith("@request:") && end > 9) {
            String action = raw.substring(9, end);
            if (action.startsWith("accept_")) return "request_" + action;
            if ("abandon".equals(action)) return "request_abandon";
        }
        if (raw.startsWith("@sort:") && end > 6 && "people".equals(raw.substring(6, end))) return "people_sort";
        return null;
    }

    private static UUID extractPersonRecordId(String raw) {
        return extractMarkedRecordId(raw, "@person:");
    }

    private static UUID extractMarkedRecordId(String raw, String marker) {
        if (raw == null || marker == null || !raw.startsWith(marker)) return null;
        int end = raw.indexOf('|');
        if (end <= marker.length()) return null;
        try { return UUID.fromString(raw.substring(marker.length(), end)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static String stripPersonMarker(String raw) {
        int end = raw == null ? -1 : raw.indexOf('|');
        return end >= 0 && end + 1 < raw.length() ? raw.substring(end + 1) : raw;
    }

    private static int extractSlot(String raw) {
        if (raw == null) return 0;
        int hash = raw.indexOf('#');
        if (hash < 0) return 0;
        int end = hash + 1;
        while (end < raw.length() && Character.isDigit(raw.charAt(end))) end++;
        if (end == hash + 1) return 0;
        try { return Integer.parseInt(raw.substring(hash + 1, end)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
