package com.dmzlivingworld.client.screen;

import com.dmzlivingworld.client.FighterPortraitRenderState;
import com.dragonminez.client.systems.kisense.KiSenseScan;
import com.dragonminez.client.systems.kisense.KiSenseState;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.LWEntities;
import com.dmzlivingworld.network.FighterProfilePacket;
import com.dmzlivingworld.network.LWNetwork;
import com.dmzlivingworld.world.FighterRelationshipManager;
import com.dmzlivingworld.world.BattlePowerDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shift+Right-click character panel. The world dossier remains compact; inspected fighters
 * get a richer, personal screen with live model, relationship, equipment, story and combat tabs.
 */
@OnlyIn(Dist.CLIENT)
public final class FighterProfileScreen extends Screen implements LivingWorldScreenMarker {
    private enum Tab { OVERVIEW("Overview"), STORY("Story"), COMBAT("Combat"), SCIENCE("Science"), MESSAGES("Messages");
        private final String label;
        Tab(String label) { this.label = label; }
        String label() { return label; }
    }

    private record VisualLine(FormattedCharSequence text, int color, int gapBefore) {}
    private record GearHitbox(int x, int y, FighterProfilePacket.EquipmentEntry entry) {}

    private FighterProfilePacket profile;
    private final List<VisualLine> visualLines = new ArrayList<>();
    private final List<GearHitbox> gearHitboxes = new ArrayList<>();
    private Tab tab = Tab.OVERVIEW;
    private boolean scheduleView;
    private int scroll;
    private int maxScroll;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int leftCardWidth;
    private AmbientFighterEntity rememberedPortrait;
    private int rememberedRefreshTicks;

    private FighterProfileScreen(FighterProfilePacket profile) {
        super(Component.literal(profile.displayName()));
        this.profile = profile;
        if (profile != null && profile.combatOnly()) this.tab = Tab.COMBAT;
    }

    public static void open(FighterProfilePacket profile) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof FighterProfileScreen current
                && current.profile.rememberedSnapshot() == profile.rememberedSnapshot()
                && current.profile.fighterId().equals(profile.fighterId())) {
            current.applyProfileRefresh(profile);
            return;
        }
        minecraft.setScreen(new FighterProfileScreen(profile));
    }

    private void applyProfileRefresh(FighterProfilePacket refreshed) {
        this.rememberedRefreshTicks = 0;
        if (refreshed.equals(this.profile)) return;
        this.profile = refreshed;
        if (refreshed.combatOnly()) this.tab = Tab.COMBAT;
        int oldScroll = scroll;
        rebuildLines();
        scroll = Math.max(0, Math.min(oldScroll, maxScroll));
    }

    @Override
    public void tick() {
        super.tick();
        // Remembered People need a small live server refresh so Instant Transmission readiness
        // (skill/cooldown state) cannot become permanently stale while the profile is open.
        // Live fighter profiles remain snapshots to keep the newer GUI-performance safeguard.
        if (!archivedReadOnly() && !isWorldMenace() && profile.rememberedSnapshot()
                && ++rememberedRefreshTicks >= 20) {
            rememberedRefreshTicks = 0;
            LWNetwork.requestRememberedFighter(profile.fighterId());
        }
    }

    @Override
    protected void init() {
        clearWidgets();
        panelWidth = Math.min(620, Math.max(1, width - 8));
        panelHeight = Math.min(390, Math.max(1, height - 8));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        leftCardWidth = panelWidth >= 500 ? 166 : 0;
        rememberedPortrait = null;
        if (minecraft != null && minecraft.level != null
                && profile.appearanceSnapshot() != null && !profile.appearanceSnapshot().isEmpty()) {
            try {
                rememberedPortrait = LWEntities.AMBIENT_FIGHTER.get().create(minecraft.level);
                if (rememberedPortrait != null) {
                    rememberedPortrait.initializePortraitFromMemory(profile.appearanceSnapshot());
                    if ("fallen".equals(archiveKind())) rememberedPortrait.configureArchivedPortrait();
                    rememberedPortrait.setYRot(180.0F);
                    rememberedPortrait.setYHeadRot(180.0F);
                }
            } catch (RuntimeException ignored) {
                rememberedPortrait = null;
            }
        }
        rebuildLines();
    }

    private int headerBottom() { return panelTop + 45; }
    private int footerY() { return panelTop + panelHeight - 27; }
    private boolean hasLivePowerRead() {
        if (profile.combatOnly() || isHerobrine()) return profile.combatOnly();
        if (profile.rememberedSnapshot()) return true;
        if (profile.scouter()) return true;
        return profile.entityId() > 0 && KiSenseState.isCombat() && KiSenseScan.getCombatEntities().contains(profile.entityId());
    }

    private String archiveKind() {
        return profile.appearanceSnapshot() == null ? "" : profile.appearanceSnapshot().getString("LWPanelArchiveKind");
    }

    private boolean archivedReadOnly() { return !archiveKind().isBlank(); }

    private boolean twoRowLiveFooter() { return !profile.rememberedSnapshot() && !isWorldMenace() && !profile.combatOnly() && !profile.requestLocked() && !profile.supplyReceiver() && panelWidth < 500; }
    private boolean isHerobrine() { return "Herobrine".equals(profile.displayName()); }
    private boolean isX7() { return profile.displayName().contains("X-7"); }
    private boolean isWorldMenace() { return "World Menace".equals(profile.rank()) || isHerobrine() || isX7(); }
    private List<Tab> visibleTabs() {
        if (profile.combatOnly()) return List.of();
        List<Tab> out = new ArrayList<>(List.of(Tab.OVERVIEW, Tab.STORY, Tab.COMBAT));
        if (!profile.scienceLines().isEmpty()) out.add(Tab.SCIENCE);
        if (!profile.messageLines().isEmpty()) out.add(Tab.MESSAGES);
        return out;
    }
    private int liveFooterTop() { return twoRowLiveFooter() ? panelTop + panelHeight - 47 : footerY(); }
    private int bodyTop() { return panelTop + 91; }
    private int bodyBottom() { return panelTop + panelHeight - (twoRowLiveFooter() ? 56 : 36); }
    private boolean canClearMessages() { return tab == Tab.MESSAGES && !profile.rememberedSnapshot() && !archivedReadOnly() && !isWorldMenace(); }
    private boolean hasScheduleData() { return profile.overviewLines().stream().anyMatch(line -> line != null && line.startsWith("@schedule|")); }
    private boolean canOpenSchedule() { return tab == Tab.OVERVIEW && !profile.rememberedSnapshot() && !archivedReadOnly() && !isWorldMenace() && !profile.combatOnly() && hasScheduleData(); }
    private boolean hasBodyUtilityRow() { return canClearMessages() || canOpenSchedule(); }
    private boolean canGoFullPower() { return !profile.rememberedSnapshot() && !isWorldMenace() && profile.relationshipKnown() && profile.relationship() >= 35; }
    private String fullPowerHint() {
        if (canGoFullPower()) return "Ask them to reveal their real learned full-power state.";
        return "Unavailable until this fighter trusts you enough.";
    }
    private String instantTransmissionStatus() {
        for (String line : profile.overviewLines()) {
            if (line == null) continue;
            String marker = "Instant Transmission: ";
            int at = line.indexOf(marker);
            if (at >= 0) return line.substring(at + marker.length()).trim();
        }
        return "Unavailable";
    }
    private boolean instantTransmissionReady() { return "READY".equals(instantTransmissionStatus()); }
    private int bodyContentTop() { return bodyTop() + (hasBodyUtilityRow() ? 22 : 0); }
    private int contentLeft() { return panelLeft + 14 + leftCardWidth; }
    private int contentRight() { return panelLeft + panelWidth - 13; }
    private int contentWidth() { return Math.max(24, contentRight() - contentLeft()); }
    private int backX() { return panelLeft + 12; }
    private void goBack() {
        if (profile.combatOnly()) onClose();
        else if (isWorldMenace()) LWNetwork.requestMenu("menace", 0);
        else if ("wanted".equals(archiveKind())) LWNetwork.requestMenu("wanted", 0);
        else if (profile.rememberedSnapshot() || "fallen".equals(archiveKind())) LWNetwork.requestMenu("people", 0);
        else LWNetwork.requestMenu("world", 0);
    }

    private void rebuildLines() {
        visualLines.clear();
        List<String> source;
        if (scheduleView && canOpenSchedule()) {
            source = profile.overviewLines().stream()
                    .filter(line -> line != null && line.startsWith("@schedule|"))
                    .map(line -> line.substring("@schedule|".length())).toList();
        } else {
            source = switch (tab) {
                case STORY -> profile.storyLines();
                case COMBAT -> profile.combatLines();
                case SCIENCE -> profile.scienceLines();
                case MESSAGES -> profile.messageLines();
                default -> profile.overviewLines();
            };
        }
        int wrap = Math.max(24, contentWidth() - 16);
        for (String raw : source) {
            if (raw == null) continue;
            if (raw.startsWith("* Mood: ")) continue;
            if (raw.startsWith("@schedule|")) continue;
            if (profile.rememberedSnapshot() && raw.contains("Instant Transmission: ")) continue;
            String line = raw;
            int color = LivingWorldGuiStyle.TEXT;
            int gap = 0;
            if (line.startsWith("## ")) { line = line.substring(3); color = LivingWorldGuiStyle.GOLD; gap = 5; }
            else if (line.startsWith("!! ")) { line = line.substring(3); color = 0xFFFF7777; gap = 2; }
            else if (line.startsWith("+ ")) { line = line.substring(2); color = LivingWorldGuiStyle.GREEN; }
            else if (line.startsWith("~ ")) { line = line.substring(2); color = 0xFF8ED6FF; }
            else if (line.startsWith("* ")) { line = "• " + line.substring(2); color = 0xFFC7C7C7; }
            else if (line.startsWith(". ")) { line = line.substring(2); color = 0xFF8D8D8D; }
            List<FormattedCharSequence> wrapped = font.split(Component.literal(line), wrap);
            if (wrapped.isEmpty()) wrapped = List.of(Component.empty().getVisualOrderText());
            boolean first = true;
            for (FormattedCharSequence seq : wrapped) {
                visualLines.add(new VisualLine(seq, color, first ? gap : 0));
                first = false;
            }
        }
        int content = 0;
        for (VisualLine line : visualLines) content += 11 + line.gapBefore;
        maxScroll = Math.max(0, content - Math.max(1, bodyBottom() - bodyContentTop()));
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        LivingWorldGuiStyle.drawPanel(graphics, panelLeft, panelTop, panelWidth, panelHeight);
        graphics.fill(panelLeft + 7, headerBottom(), panelLeft + panelWidth - 7, headerBottom() + 1, LivingWorldGuiStyle.DIVIDER);

        drawHeader(graphics, mouseX, mouseY);
        drawTabs(graphics, mouseX, mouseY);
        drawRelationship(graphics, mouseX, mouseY);

        gearHitboxes.clear();
        if (leftCardWidth > 0) drawCharacterCard(graphics, mouseX, mouseY);
        drawTabBody(graphics);
        drawFooterActions(graphics, mouseX, mouseY);

        LivingWorldGuiStyle.drawButton(graphics, font, backX(), panelTop + 8, 30, 20, "‹",
                mouseX, mouseY, true, false, false);
        if (panelWidth >= 360) LivingWorldGuiStyle.drawButton(graphics, font, panelLeft + panelWidth - 70, panelTop + 8, 25, 20, "?",
                mouseX, mouseY, true, false, false);
        LivingWorldGuiStyle.drawButton(graphics, font, panelLeft + panelWidth - 40, panelTop + 8, 25, 20, "×",
                mouseX, mouseY, true, false, false);

        drawGearTooltip(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = panelLeft + 49;
        boolean compact = panelWidth < 360;
        FighterRelationshipManager.Disposition disposition = FighterRelationshipManager.Disposition.byId(profile.dispositionId());
        String chip = profile.combatOnly() ? "COMBAT READOUT" : isHerobrine() ? "! ANOMALY" : isX7() ? "! X-7" : isWorldMenace() ? "! WORLD MENACE" : disposition.worldBadge() + " " + profile.dispositionLabel();
        int chipWidth = compact ? 0 : Math.min(86, Math.max(54, font.width(chip) + 12));
        int chipX = compact ? panelLeft + panelWidth - 43 : panelLeft + panelWidth - 76 - chipWidth;
        int right = compact ? panelLeft + panelWidth - 45 : Math.max(x + 40, chipX - 7);

        String titleText = profile.legacyTitle().isBlank() ? profile.displayName() : profile.legacyTitle() + " " + profile.displayName();
        LivingWorldGuiStyle.drawFitted(graphics, font, titleText, x, panelTop + 10, Math.max(24, right - x), 0xFFFFE29A);

        if (panelHeight >= 190) {
            String sub;
            if (profile.combatOnly()) sub = "Scientist specimen • live combat attributes";
            else if (isHerobrine()) sub = "WORLD MENACE • encounter dossier";
            else if (isX7()) sub = "WORLD MENACE • Red Ribbon engineered subject";
            else if (isWorldMenace()) sub = "WORLD MENACE • hostile subject";
            else {
                sub = profile.rememberedSnapshot() ? "Last remembered • " : "";
                sub += profile.faction().isBlank() ? "Independent fighter" : profile.faction();
                if (!profile.factionRole().isBlank()) sub += " • " + profile.factionRole();
                sub += " • " + profile.race() + " • " + profile.rank();
            }
            LivingWorldGuiStyle.drawFitted(graphics, font, sub, x, panelTop + 26, Math.max(24, right - x), LivingWorldGuiStyle.MUTED);
        }
        if (!compact) {
            int chipColor = isWorldMenace() ? 0xFFFF5555 : disposition.color();
            graphics.fill(chipX, panelTop + 8, chipX + chipWidth, panelTop + 28, chipColor);
            graphics.fill(chipX + 1, panelTop + 9, chipX + chipWidth - 1, panelTop + 27, isWorldMenace() ? 0xFF160B0B : LivingWorldGuiStyle.PANEL_INNER);
            LivingWorldGuiStyle.drawCentered(graphics, font, chip, chipX, panelTop + 8, chipWidth, 20, chipColor);
        }
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = contentLeft();
        int y = panelTop + 50;
        int gap = 4;
        List<Tab> tabs = visibleTabs();
        if (tabs.isEmpty()) return; // combat-only specimen profiles open directly on Combat; no tab strip.
        int w = Math.max(24, (contentWidth() - gap * (tabs.size() - 1)) / tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            Tab shown = tabs.get(i);
            String label = shown.label();
            if (isHerobrine() || isX7()) label = switch (shown) {
                case OVERVIEW -> isX7() ? "Dossier" : "Evidence";
                case STORY -> isX7() ? "Record" : "Encounters";
                case COMBAT -> "Threat";
                case SCIENCE -> "Science";
                case MESSAGES -> isX7() ? "Reports" : "Signs";
            };
            LivingWorldGuiStyle.drawButton(graphics, font, x + i * (w + gap), y, w, 18, label,
                    mouseX, mouseY, true, tab == shown, false);
        }
    }

    private void drawRelationship(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = contentLeft();
        int y = panelTop + 72;
        int w = contentWidth();
        if (profile.combatOnly()) {
            graphics.fill(x, y, x + w, y + 14, LivingWorldGuiStyle.CONTROL_BORDER);
            graphics.fill(x + 1, y + 1, x + w - 1, y + 13, LivingWorldGuiStyle.CARD);
            LivingWorldGuiStyle.drawCentered(graphics, font, "LIVE ATTRIBUTE READOUT", x, y, w, 14, LivingWorldGuiStyle.BLUE);
            return;
        }
        if (isWorldMenace()) {
            graphics.fill(x, y, x + w, y + 14, 0xFF6E1C1C);
            graphics.fill(x + 1, y + 1, x + w - 1, y + 13, 0xFF160B0B);
            LivingWorldGuiStyle.drawCentered(graphics, font, "NO PERSONAL BOND • OBSERVATION ONLY", x, y, w, 14, 0xFFFF7777);
            return;
        }
        if (!profile.relationshipKnown()) {
            graphics.fill(x, y, x + w, y + 14, LivingWorldGuiStyle.CONTROL_BORDER);
            graphics.fill(x + 1, y + 1, x + w - 1, y + 13, LivingWorldGuiStyle.CARD);
            String label = "No personal bond yet";
            LivingWorldGuiStyle.drawFitted(graphics, font, label, x + 5, y + 3, w - 10, LivingWorldGuiStyle.MUTED);
            return;
        }

        int rel = Math.max(-100, Math.min(100, profile.relationship()));
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
        LivingWorldGuiStyle.drawCentered(graphics, font, profile.relationshipStage(), x, y, w, 14, 0xFFFFFFFF);
    }

    private void drawCharacterCard(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = panelLeft + 12;
        int top = panelTop + 51;
        int right = left + leftCardWidth - 10;
        int bottom = bodyBottom();
        graphics.fill(left, top, right, bottom, LivingWorldGuiStyle.CARD_BORDER);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, LivingWorldGuiStyle.CARD);

        int cardHeight = bottom - top;
        boolean showModel = cardHeight >= 235;
        int detailY = showModel ? top + 178 : top + 10;

        // Prefer the detached appearance clone for both live and remembered profiles. It has no
        // world entity id, so DMZ Ki Sense cannot treat the GUI portrait as another sensed target.
        Entity entity = minecraft != null && minecraft.level != null ? minecraft.level.getEntity(profile.entityId()) : null;
        LivingEntity portrait = rememberedPortrait != null ? rememberedPortrait
                : entity instanceof LivingEntity living && living.isAlive() ? living : null;
        if (showModel && portrait != null) {
            try {
                int centerX = left + (right - left) / 2;
                int baseY = top + 139;
                float mouseDX = centerX - mouseX;
                float mouseDY = (baseY - 52) - mouseY;
                // Clip the 3D portrait to its own card viewport. Tall/animated models may rotate
                // beyond their nominal bounds; they must never paint over tabs, header or text.
                graphics.enableScissor(left + 2, top + 2, right - 2, top + 144);
                FighterPortraitRenderState.begin();
                try {
                    InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, centerX, baseY, 44, mouseDX, mouseDY, portrait);
                } finally {
                    FighterPortraitRenderState.end();
                    graphics.disableScissor();
                }
                if ("fallen".equals(archiveKind())) {
                    graphics.fill(left + 7, top + 7, right - 7, top + 143, 0x99545A60);
                    LivingWorldGuiStyle.drawChip(graphics, font, "PASSED AWAY", left + 8, top + 8, 82, 14, LivingWorldGuiStyle.NEUTRAL);
                } else if (profile.rememberedSnapshot()) {
                    LivingWorldGuiStyle.drawChip(graphics, font, "LAST SEEN", left + 8, top + 8, 62, 14, LivingWorldGuiStyle.BLUE);
                }
                drawHeightMarker(graphics, portrait, right, top, baseY);
            } catch (RuntimeException ignored) {
                // The rest of the profile remains usable even if another renderer rejects GUI rendering.
            }
        } else if (showModel) {
            LivingWorldGuiStyle.drawCentered(graphics, font, profile.rememberedSnapshot() ? "Portrait unavailable" : "Fighter",
                    left + 8, top + 50, right - left - 16, 18, LivingWorldGuiStyle.MUTED);
        }

        if (showModel) {
            String formattedPower = BattlePowerDisplay.format(profile.battlePower());
            String powerLabel = profile.combatOnly() ? "Live Power Level: " + formattedPower
                    : isWorldMenace() ? "Power Level: " + formattedPower
                    : profile.rememberedSnapshot() ? "Last known PL: " + formattedPower
                    : hasLivePowerRead() ? "Live Power Level: " + formattedPower : "Live PL: scouter / Ki Sense required";
            LivingWorldGuiStyle.drawCentered(graphics, font, powerLabel, left + 7, top + 145, right - left - 14, 12,
                    hasLivePowerRead() ? 0xFF8ED6FF : LivingWorldGuiStyle.MUTED);
        }

        // Menaces/specimens have purpose-built dossier data on the right. Do not fill the left
        // card with generic NPC placeholders such as Unknown nature or an empty Equipment section.
        if (profile.combatOnly()) {
            LivingWorldGuiStyle.drawCentered(graphics, font, "Scientist specimen", left + 7, showModel ? top + 160 : top + 22,
                    right - left - 14, 14, LivingWorldGuiStyle.BLUE);
            return;
        }
        if (isWorldMenace()) {
            LivingWorldGuiStyle.drawCentered(graphics, font, isX7() ? "Red Ribbon engineered combatant" : "World Menace",
                    left + 7, showModel ? top + 160 : top + 22, right - left - 14, 14, 0xFFFF7777);
            return;
        }

        String mood = moodLabel();
        if (!mood.isBlank()) {
            // Keep the portrait card readable: the cause belongs in dialogue/context, while the
            // card gets the complete mood name with no ellipsis. All seven labels fit comfortably.
            if (showModel) drawMoodLine(graphics, mood, left + 7, top + 158, right - left - 14, true);
            else drawMoodLine(graphics, mood, left + 7, detailY, right - left - 14, false);
        }

        int detailCursor = detailY + (!showModel && !mood.isBlank() ? 12 : 0);
        int gearY = detailCursor + 5;
        graphics.drawString(font, "Equipment", left + 7, gearY, LivingWorldGuiStyle.GOLD, false);
        int cell = 23;
        int startX = left + 7;
        int startY = gearY + 12;
        int index = 0;
        for (FighterProfilePacket.EquipmentEntry entry : profile.equipment()) {
            int col = index % 3;
            int row = index / 3;
            int x = startX + col * cell;
            int y = startY + row * cell;
            graphics.fill(x, y, x + 20, y + 20, LivingWorldGuiStyle.CONTROL_BORDER);
            graphics.fill(x + 1, y + 1, x + 19, y + 19, LivingWorldGuiStyle.CONTROL);
            if (!entry.stack().isEmpty()) {
                graphics.renderItem(entry.stack(), x + 2, y + 2);
                graphics.renderItemDecorations(font, entry.stack(), x + 2, y + 2);
                gearHitboxes.add(new GearHitbox(x, y, entry));
            } else {
                String mark = slotMark(entry.slot());
                LivingWorldGuiStyle.drawCentered(graphics, font, mark, x, y, 20, 20, 0xFF606A74);
            }
            index++;
            if (index >= 6) break;
        }
    }

    /** Wary's eye icon is intentionally a touch larger without scaling the mood text itself. */
    private void drawMoodLine(GuiGraphics graphics, String mood, int x, int y, int width, boolean centered) {
        final int color = 0xFF8ED6FF;
        String eyes = "👀";
        if (mood == null || !mood.startsWith(eyes)) {
            if (centered) LivingWorldGuiStyle.drawCentered(graphics, font, "Mood: " + mood, x, y, width, 14, color);
            else graphics.drawString(font, "Mood: " + mood, x, y, color, false);
            return;
        }
        String prefix = "Mood: ";
        String rest = mood.substring(eyes.length()).trim();
        float iconScale = 1.14F;
        int iconWidth = Math.max(1, font.width(eyes));
        int total = font.width(prefix) + (int)Math.ceil(iconWidth * iconScale) + 2 + font.width(rest);
        int start = centered ? x + Math.max(0, (width - total) / 2) : x;
        graphics.drawString(font, prefix, start, y, color, false);
        int iconX = start + font.width(prefix);
        graphics.pose().pushPose();
        graphics.pose().translate(iconX, y - 1.0F, 0.0F);
        graphics.pose().scale(iconScale, iconScale, 1.0F);
        graphics.drawString(font, eyes, 0, 0, color, false);
        graphics.pose().popPose();
        int textX = iconX + (int)Math.ceil(iconWidth * iconScale) + 2;
        graphics.drawString(font, rest, textX, y, color, false);
    }

    private String moodLabel() {
        for (String raw : profile.overviewLines()) {
            if (raw == null || !raw.startsWith("* Mood: ")) continue;
            String value = raw.substring(8).trim();
            int separator = value.indexOf(" — ");
            return separator >= 0 ? value.substring(0, separator).trim() : value;
        }
        return "";
    }

    /** Small portrait-side ruler; one Minecraft block is presented as one metre for readability. */
    private void drawHeightMarker(GuiGraphics graphics, LivingEntity portrait, int cardRight, int cardTop, int baseY) {
        double height = portrait.getBbHeight();
        if (portrait instanceof AmbientFighterEntity fighter) height *= fighter.getDisplayScale();
        height = Math.max(0.50D, Math.min(4.50D, height));

        int bottom = baseY - 4;
        int top = Math.max(cardTop + 35, bottom - 82);
        int x = cardRight - 10;
        int color = 0xFF8ED6FF;
        graphics.fill(x, top, x + 1, bottom, color);
        graphics.fill(x - 3, top, x + 2, top + 1, color);
        graphics.fill(x - 3, bottom - 1, x + 2, bottom, color);

        String label = String.format(Locale.ROOT, "%.2f m", height);
        int labelWidth = Math.min(48, Math.max(30, font.width(label) + 6));
        int labelX = Math.max(cardRight - labelWidth - 13, cardRight - 64);
        int labelY = Math.max(cardTop + 23, top - 12);
        graphics.fill(labelX, labelY, labelX + labelWidth, labelY + 11, 0xCC111820);
        LivingWorldGuiStyle.drawCentered(graphics, font, label, labelX, labelY, labelWidth, 11, color);
    }

    private int drawCardWrapped(GuiGraphics graphics, String text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text == null ? "" : text), Math.max(18, width));
        int count = Math.min(Math.max(1, maxLines), Math.max(1, lines.size()));
        if (lines.isEmpty()) lines = List.of(Component.empty().getVisualOrderText());
        for (int i = 0; i < count; i++) {
            graphics.drawString(font, lines.get(i), x, y + i * 10, color, false);
        }
        return y + count * 10;
    }

    private static String slotMark(String slot) {
        if (slot == null || slot.isBlank()) return "-";
        return switch (slot) {
            case "Weapon" -> "W";
            case "Off hand" -> "O";
            case "Head" -> "H";
            case "Chest" -> "C";
            case "Legs" -> "L";
            case "Feet" -> "F";
            default -> slot.substring(0, 1).toUpperCase();
        };
    }

    private void drawTabBody(GuiGraphics graphics) {
        int clipLeft = contentLeft();
        int clipTop = bodyContentTop();
        int clipRight = contentRight();
        int clipBottom = bodyBottom();
        if (canClearMessages()) {
            LivingWorldGuiStyle.drawButton(graphics, font, clipRight - 92, bodyTop() + 1, 92, 18, "Clear recent",
                    -10_000, -10_000, true, false, false);
        } else if (canOpenSchedule()) {
            LivingWorldGuiStyle.drawButton(graphics, font, clipRight - 92, bodyTop() + 1, 92, 18,
                    scheduleView ? "‹ Overview" : "Schedule ›", -10_000, -10_000, true, false, false);
        }
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        int y = clipTop - scroll;
        for (VisualLine line : visualLines) {
            y += line.gapBefore;
            if (y > clipTop - 14 && y < clipBottom + 4) graphics.drawString(font, line.text, clipLeft + 3, y, line.color, false);
            y += 11;
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackHeight = clipBottom - clipTop;
            int thumb = Math.max(18, trackHeight * trackHeight / Math.max(trackHeight + maxScroll, 1));
            int thumbY = clipTop + (trackHeight - thumb) * scroll / Math.max(maxScroll, 1);
            graphics.fill(clipRight - 2, clipTop, clipRight, clipBottom, 0x443E4650);
            graphics.fill(clipRight - 2, thumbY, clipRight, thumbY + thumb, LivingWorldGuiStyle.BLUE);
        }
    }

    private void drawFooterActions(GuiGraphics graphics, int mouseX, int mouseY) {
        if (profile.combatOnly()) {
            LivingWorldGuiStyle.drawButton(graphics, font, panelLeft + 12, footerY(), panelWidth - 24, 18,
                    "Refresh Combat Stats", mouseX, mouseY, true, false, false);
            return;
        }
        if (isWorldMenace()) {
            int x = panelLeft + 12;
            int w = panelWidth - 24;
            graphics.fill(x, footerY(), x + w, footerY() + 18, 0xFF241B1B);
            graphics.fill(x + 1, footerY() + 1, x + w - 1, footerY() + 17, 0xFF110E0E);
            LivingWorldGuiStyle.drawCentered(graphics, font, "WORLD MENACE • NO SOCIAL OPTIONS", x, footerY(), w, 18, 0xFFFF7777);
            return;
        }
        if (archivedReadOnly()) {
            int x = panelLeft + 12;
            int w = panelWidth - 24;
            String label = "fallen".equals(archiveKind()) ? "PASSED AWAY • ARCHIVED PROFILE" : "WANTED DOSSIER • LAST KNOWN PROFILE";
            LivingWorldGuiStyle.drawCentered(graphics, font, label, x, footerY(), w, 18, LivingWorldGuiStyle.MUTED);
            return;
        }
        if (profile.rememberedSnapshot()) {
            int gap = 5;
            int total = Math.min(panelWidth - 24, 420);
            int w = Math.max(62, (total - gap * 2) / 3);
            int x = panelLeft + (panelWidth - (w * 3 + gap * 2)) / 2;
            String itStatus = instantTransmissionStatus();
            String itLabel = "READY".equals(itStatus) ? "Instant Transmission"
                    : itStatus.startsWith("Cooldown active") ? "IT • " + itStatus.substring("Cooldown active • ".length())
                    : "Instant Transmission";
            LivingWorldGuiStyle.drawButton(graphics, font, x, footerY(), w, 18, itLabel,
                    mouseX, mouseY, instantTransmissionReady(), false, false);
            LivingWorldGuiStyle.drawDangerButton(graphics, font, x + w + gap, footerY(), w, 18, "Forget",
                    mouseX, mouseY, true);
            LivingWorldGuiStyle.drawButton(graphics, font, x + (w + gap) * 2, footerY(), w, 18, "Back to People",
                    mouseX, mouseY, true, false, false);
            return;
        }
        if (profile.requestLocked() && !profile.supplyReceiver()) {
            int x = panelLeft + 12;
            int w = panelWidth - 24;
            LivingWorldGuiStyle.drawCentered(graphics, font, "ON FACTION DUTY • SOCIAL ACTIONS UNAVAILABLE",
                    x, footerY(), w, 18, LivingWorldGuiStyle.MUTED);
            return;
        }
        String[] actions = profile.supplyReceiver()
                ? new String[]{"Deliver Supplies"}
                : new String[]{"Talk", "Spar", "Go Along", "Come Along", "Fusion", "Meditate", "Go Full Power"};
        int gap = 5;
        int cols = twoRowLiveFooter() ? (actions.length > 6 ? 4 : 3) : actions.length;
        int rows = (actions.length + cols - 1) / cols;
        int startX = panelLeft + 12;
        int available = panelWidth - 24 - gap * (cols - 1);
        int actionWidth = Math.max(24, available / cols);
        int top = liveFooterTop();
        for (int i = 0; i < actions.length; i++) {
            int row = i / cols, col = i % cols;
            int x = startX + col * (actionWidth + gap);
            int y = top + row * 20;
            boolean fullPower = "Go Full Power".equals(actions[i]);
            boolean enabled = !fullPower || canGoFullPower();
            boolean primary = "Deliver Supplies".equals(actions[i]);
            LivingWorldGuiStyle.drawButton(graphics, font, x, y, actionWidth, 18, actions[i],
                    mouseX, mouseY, enabled, false, primary);
            if (primary && LivingWorldGuiStyle.isInside(mouseX, mouseY, x, y, actionWidth, 18) && !profile.supplyRequestLine().isBlank()) {
                graphics.renderTooltip(font, Component.literal(profile.supplyRequestLine()), mouseX, mouseY);
            } else if (fullPower && LivingWorldGuiStyle.isInside(mouseX, mouseY, x, y, actionWidth, 18)) {
                graphics.renderTooltip(font, Component.literal(fullPowerHint()), mouseX, mouseY);
            }
        }
    }

    private void drawGearTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (GearHitbox hitbox : gearHitboxes) {
            if (LivingWorldGuiStyle.isInside(mouseX, mouseY, hitbox.x, hitbox.y, 20, 20)) {
                ItemStack stack = hitbox.entry.stack();
                graphics.renderTooltip(font, stack, mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (LivingWorldGuiStyle.isInside(mouseX, mouseY, backX(), panelTop + 8, 30, 20)) {
                goBack();
                return true;
            }
            if (panelWidth >= 360 && LivingWorldGuiStyle.isInside(mouseX, mouseY, panelLeft + panelWidth - 70, panelTop + 8, 25, 20)) {
                LivingWorldGuideScreen.open();
                return true;
            }
            if (LivingWorldGuiStyle.isInside(mouseX, mouseY, panelLeft + panelWidth - 40, panelTop + 8, 25, 20)) {
                onClose();
                return true;
            }

            int x = contentLeft();
            int y = panelTop + 50;
            int gap = 4;
            List<Tab> tabs = visibleTabs();
            int w = tabs.isEmpty() ? 0 : Math.max(24, (contentWidth() - gap * (tabs.size() - 1)) / tabs.size());
            for (int i = 0; i < tabs.size(); i++) {
                if (LivingWorldGuiStyle.isInside(mouseX, mouseY, x + i * (w + gap), y, w, 18)) {
                    tab = tabs.get(i);
                    scheduleView = false;
                    scroll = 0;
                    rebuildLines();
                    return true;
                }
            }

            if (canClearMessages() && LivingWorldGuiStyle.isInside(mouseX, mouseY, contentRight() - 92, bodyTop() + 1, 92, 18)) {
                LWNetwork.requestFighterAction("clearmessages", profile.fighterId());
                onClose();
                return true;
            }
            if (canOpenSchedule() && LivingWorldGuiStyle.isInside(mouseX, mouseY, contentRight() - 92, bodyTop() + 1, 92, 18)) {
                scheduleView = !scheduleView;
                scroll = 0;
                rebuildLines();
                return true;
            }

            if (profile.combatOnly()) {
                if (LivingWorldGuiStyle.isInside(mouseX, mouseY, panelLeft + 12, footerY(), panelWidth - 24, 18)) {
                    LWNetwork.requestLiveFighter(profile.fighterId());
                }
                return true;
            }
            if (isWorldMenace() || archivedReadOnly()) return true;
            if (profile.rememberedSnapshot()) {
                int gapFooter = 5;
                int total = Math.min(panelWidth - 24, 420);
                int bw = Math.max(62, (total - gapFooter * 2) / 3);
                int bx = panelLeft + (panelWidth - (bw * 3 + gapFooter * 2)) / 2;
                if (LivingWorldGuiStyle.isInside(mouseX, mouseY, bx, footerY(), bw, 18)) {
                    if (instantTransmissionReady()) {
                        LWNetwork.instantTransmitToRemembered(profile.fighterId());
                        onClose();
                    }
                    return true;
                }
                if (LivingWorldGuiStyle.isInside(mouseX, mouseY, bx + bw + gapFooter, footerY(), bw, 18)) {
                    minecraft.setScreen(new ConfirmScreen(ok -> {
                        if (ok) LWNetwork.peopleMemoryAction("one", profile.fighterId());
                        else minecraft.setScreen(this);
                    }, Component.literal("Forget this person?"), Component.literal("Their remembered profile will be removed from your People list.")));
                    return true;
                }
                if (LivingWorldGuiStyle.isInside(mouseX, mouseY, bx + (bw + gapFooter) * 2, footerY(), bw, 18)) {
                    LWNetwork.requestMenu("people", 0);
                    return true;
                }
            } else {
                if (profile.requestLocked() && !profile.supplyReceiver()) return true;
                String[] actions = profile.supplyReceiver()
                        ? new String[]{"deliver"}
                        : new String[]{"talk", "spar", "join", "companion", "fusion", "meditate", "fullpower"};
                int actionGap = 5;
                int cols = twoRowLiveFooter() ? (actions.length > 6 ? 4 : 3) : actions.length;
                int startX = panelLeft + 12;
                int available = panelWidth - 24 - actionGap * (cols - 1);
                int actionWidth = Math.max(24, available / cols);
                int top = liveFooterTop();
                for (int i = 0; i < actions.length; i++) {
                    int row = i / cols, col = i % cols;
                    int ax = startX + col * (actionWidth + actionGap);
                    int ay = top + row * 20;
                    if (LivingWorldGuiStyle.isInside(mouseX, mouseY, ax, ay, actionWidth, 18)) {
                        if ("fullpower".equals(actions[i]) && !canGoFullPower()) return true;
                        LWNetwork.requestFighterAction(actions[i], profile.fighterId());
                        onClose();
                        return true;
                    }
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

    @Override
    public boolean isPauseScreen() { return false; }
}
