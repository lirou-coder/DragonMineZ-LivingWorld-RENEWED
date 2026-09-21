package com.dmzlivingworld.client.screen;

import com.dmzlivingworld.client.LWLang;
import com.dmzlivingworld.config.LivingWorldClientConfig;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.network.LWNetwork;
import com.dmzlivingworld.network.WorldSettingsPacket;
import com.kunyo.dbzmeditation.MeditationConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Responsive integrated editor for Living World + Meditation + client readability. */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("unused")
public final class WorldSettingsScreen extends Screen implements LivingWorldScreenMarker {
    private static final int[] FIGHTER_CAPS = {0, 8, 12, 20, 30, 40, 64, 96, 128, 192, 256, 384, 512, 768, 1024, 1536, 2048, 3072, 4096};
    private static final int[] HOSTILE_CAPS = {0, 2, 4, 6, 8, 10, 16, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512, 768, 1024, 1536, 2048, 3072, 4096};
    private static final int[] PRESENCE_RADII = {96, 128, 160, 192, 256, 320, 384, 512, 768, 1024, 1536, 2048, 3072, 4096, 6144, 8192};
    private static final int[] ALERT_RADII = {256, 512, 800, 1000, 1400, 1800, 2400, 3200, 4096, 6144, 8192, 12288, 16384, 24576, 32768};
    private static final int[] KI_MODE_ORDER = {0, 3, 1, 2};
    private static final String[] TAB_KEYS = {"world", "npc_behaviour", "advanced_npc", "relationships", "display", "meditation", "meditation_visuals"};
    // Visual order is independent of the established logical tab IDs, preserving save/default/update logic.
    private static final int[] TAB_ORDER = {0, 5, 6, 1, 2, 3, 4};

    private int tab;
    private int scroll;
    private final boolean canEdit;

    // Living World server values
    private int activityPreset, nearbyFighterCap, nearbyHostileCap, livingPresenceTargetBase,
            livingPresenceRadius, factionResidentCap, worldEventAlertRadius, talkBaseGain,
            talkRelationshipCap, talkCooldownMinSeconds, talkCooldownMaxSeconds, npcChaosPercent, npcKiMode, npcStrengthPercent, npcGrowthPercent, npcChatFrequencyPercent, earthGuardianResponsePercent, wantedPressureDecayMinutes;
    private boolean factionEncounters, dynamicEncounters, recurringFighters, automaticPowerSensing,
            worldIncidents, worldEventAlerts, socialTalk, npcSocializing, companionSagaHelp, attackMinecraftMobs,
            worldMenacesEnabled, npcsEnabledOnCreativeMode;
    private int maxRememberedDeadFighters, npcDespawnProtectionRadius, npcCombatLives, racialSkillMinimumEra;
    private double levelMultiplierPerSaga, maxDefenseMitigation, bpVisualMultiplier, npcPowerMultiplier;
    private boolean canMeditationProcSkillProgression, treatRaceBlacklistAsWhitelist, treatDimensionWhitelistAsBlacklist;
    private String npcRaceBlacklist, canUseClothes, dimensionWhitelist, companionDimensionBlacklist;
    private double[] archetypeShares = new double[20];

    // Living World client values
    private double nameScale, dialogueScale, verticalOffset, hudScale, factionQuestHudScale;
    private boolean dispositionIcon, factionLabel, dialogueVisible, speechToChat;
    private int nameplateDistance, factionLabelDistance, dialogueDistance, speechChatRadius;

    // Meditation server values
    private boolean medEnabled, medTpRewards, medDamageInterrupts, medGroup, medNpc,
            medBreakthroughs, medFormMastery, medParticles, medSounds;
    private int medTpScale, medRewardInterval, medFocused, medCentered, medDeep, medTranscendent,
            medCalmMultiplier, medFocusedMultiplier, medCenteredMultiplier, medDeepMultiplier, medTranscendentMultiplier,
            medDamageCooldown, medGroupRadius, medBreakthroughRoll, medBreakthroughPoints, medParticleDensity;
    private double medBreakthroughChance, medDeepMastery, medTransMastery, medLevitation;

    // Meditation client values
    private boolean medHud, medSummary, medNativeAnimation, medFocusSeal, medFirstPersonAura, medStageFx, medNpcFx;
    private int medHudOffset, medAuraIntensity, medSealIntensity, medSealSize;

    private int panelLeft, panelTop, panelWidth, panelHeight, contentTop, footerY, rowHeight;
    private String status = "";
    private long statusUntil;
    private boolean savePending;
    private EditBox numericEditor;
    private int editingRow = -1;
    private int editingTab = -1;
    private int draggingSliderTab = -1;
    private int draggingSliderRow = -1;
    private boolean dirty;

    private WorldSettingsScreen(WorldSettingsPacket packet) {
        super(LWLang.text("screen.world_settings.title"));
        this.canEdit = packet.canEdit();
        loadWorld(packet.world());
        loadMeditation(packet.meditation());
        loadClient(LivingWorldClientConfig.snapshot());
        loadMeditationClient(MeditationConfig.clientSnapshot());
    }

    public static void open(WorldSettingsPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        boolean ack = mc.screen instanceof WorldSettingsScreen current && current.savePending;
        WorldSettingsScreen next = new WorldSettingsScreen(packet);
        if (ack) { next.dirty = false; next.setStatus(LWLang.string("screen.world_settings.saved"), 2600L); }
        mc.setScreen(next);
    }

    @Override
    protected void init() {
        clearWidgets();
        int maxW = Math.max(1, width - 8);
        int maxH = Math.max(1, height - 8);
        panelWidth = Math.min(720, maxW);
        panelHeight = Math.min(440, maxH);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        contentTop = panelTop + (panelHeight < 250 ? 61 : 70);
        footerY = panelTop + panelHeight - 27;
        rowHeight = panelWidth < 430 ? 42 : (panelHeight < 300 ? 27 : 34);
        clampScroll();
    }

    private void loadWorld(LivingWorldConfig.Snapshot v) {
        activityPreset=v.activityPreset(); nearbyFighterCap=v.nearbyFighterCap(); nearbyHostileCap=v.nearbyHostileCap();
        factionEncounters=v.factionEncounters(); dynamicEncounters=v.dynamicEncounters(); recurringFighters=v.recurringFighters();
        livingPresenceTargetBase=v.livingPresenceTargetBase(); livingPresenceRadius=v.livingPresenceRadius(); factionResidentCap=v.factionResidentCap();
        automaticPowerSensing=v.automaticPowerSensing(); worldIncidents=v.worldIncidents(); worldEventAlerts=v.worldEventAlerts();
        worldEventAlertRadius=v.worldEventAlertRadius(); socialTalk=v.socialTalk(); talkBaseGain=v.talkBaseGain();
        talkRelationshipCap=v.talkRelationshipCap(); talkCooldownMinSeconds=v.talkCooldownMinSeconds();
        talkCooldownMaxSeconds=v.talkCooldownMaxSeconds(); npcSocializing=v.npcSocializing();
        npcChaosPercent=v.npcChaosPercent(); companionSagaHelp=v.companionSagaHelp(); npcKiMode=v.npcKiMode(); npcStrengthPercent=v.npcStrengthPercent();
        npcGrowthPercent=v.npcGrowthPercent(); attackMinecraftMobs=v.attackMinecraftMobs();
        worldMenacesEnabled=v.worldMenacesEnabled(); wantedPressureDecayMinutes=v.wantedPressureDecayMinutes();
        npcsEnabledOnCreativeMode=v.npcsEnabledOnCreativeMode();
        npcChatFrequencyPercent=v.npcChatFrequencyPercent(); earthGuardianResponsePercent=v.earthGuardianResponsePercent();
        maxRememberedDeadFighters=v.maxRememberedDeadFighters(); npcDespawnProtectionRadius=v.npcDespawnProtectionRadius(); npcCombatLives=v.npcCombatLives(); racialSkillMinimumEra=v.racialSkillMinimumEra();
        levelMultiplierPerSaga=v.levelMultiplierPerSaga(); maxDefenseMitigation=v.maxDefenseMitigation(); bpVisualMultiplier=v.bpVisualMultiplier(); npcPowerMultiplier=v.npcPowerMultiplier();
        canMeditationProcSkillProgression=v.canMeditationProcSkillProgression();
        npcRaceBlacklist=String.join(",",v.npcRaceBlacklist()); treatRaceBlacklistAsWhitelist=v.treatRaceBlacklistAsWhitelist();
        canUseClothes=String.join(",",v.canUseClothes()); dimensionWhitelist=String.join(",",v.dimensionWhitelist());
        treatDimensionWhitelistAsBlacklist=v.treatDimensionWhitelistAsBlacklist();
        companionDimensionBlacklist=String.join(",",v.companionDimensionBlacklist());
        for(int i=0;i<Math.min(20,v.archetypeShares().size());i++) archetypeShares[i]=v.archetypeShares().get(i);
    }

    private void loadClient(LivingWorldClientConfig.Snapshot v) {
        nameScale=v.nameplateScale(); dialogueScale=v.dialogueScale(); verticalOffset=v.verticalOffset();
        dispositionIcon=v.showDispositionIcon(); factionLabel=v.showFactionLabel(); dialogueVisible=v.showDialogue();
        nameplateDistance=v.nameplateDistance(); factionLabelDistance=v.factionLabelDistance(); dialogueDistance=v.dialogueDistance();
        speechToChat=v.speechToChat(); speechChatRadius=v.speechChatRadius(); hudScale=v.hudScale(); factionQuestHudScale=v.factionQuestHudScale();
    }

    private void loadMeditation(MeditationConfig.ServerSnapshot v) {
        medEnabled=v.enabled(); medTpRewards=v.tpRewardsEnabled(); medTpScale=v.tpRewardScalePercent();
        medRewardInterval=v.rewardIntervalSeconds(); medFocused=v.focusedSeconds(); medCentered=v.centeredSeconds();
        medDeep=v.deepSeconds(); medTranscendent=v.transcendentSeconds();
        medCalmMultiplier=v.calmMultiplier(); medFocusedMultiplier=v.focusedMultiplier();
        medCenteredMultiplier=v.centeredMultiplier(); medDeepMultiplier=v.deepMultiplier();
        medTranscendentMultiplier=v.transcendentMultiplier(); medDamageInterrupts=v.damageInterrupts();
        medDamageCooldown=v.damageCooldownSeconds(); medGroup=v.groupMeditation(); medGroupRadius=v.groupMeditationRadius();
        medNpc=v.livingWorldNpcMeditation(); medBreakthroughs=v.statBreakthroughEnabled();
        medBreakthroughChance=v.statBreakthroughChancePercent(); medBreakthroughRoll=v.statBreakthroughRollSeconds();
        medBreakthroughPoints=v.statBreakthroughPoints(); medFormMastery=v.formMasteryEnabled(); medDeepMastery=v.deepFormMasteryPerMinute();
        medTransMastery=v.transcendentFormMasteryPerMinute(); medLevitation=v.levitationHeight();
        medParticles=v.particles(); medParticleDensity=v.particleDensityPercent(); medSounds=v.milestoneSounds();
    }

    private void loadMeditationClient(MeditationConfig.ClientSnapshot v) {
        medHud=v.focusHud(); medSummary=v.sessionSummary();
        medNativeAnimation=v.nativeAnimation(); medHudOffset=v.hudTopOffset(); medAuraIntensity=v.auraIntensityPercent();
        medFocusSeal=v.focusSealEnabled(); medSealIntensity=v.focusSealIntensityPercent(); medSealSize=v.focusSealRadiusPercent();
        medFirstPersonAura=v.firstPersonAura(); medStageFx=v.stageTransitionEffects(); medNpcFx=v.npcMeditationEffects();
    }

    private LivingWorldConfig.Snapshot worldSnapshot() {
        return new LivingWorldConfig.Snapshot(activityPreset, nearbyFighterCap, nearbyHostileCap,
                factionEncounters, dynamicEncounters, recurringFighters, livingPresenceTargetBase, livingPresenceRadius,
                factionResidentCap, automaticPowerSensing, worldIncidents, worldEventAlerts, worldEventAlertRadius,
                socialTalk, talkBaseGain, talkRelationshipCap, talkCooldownMinSeconds, talkCooldownMaxSeconds,
                npcSocializing, npcChaosPercent, companionSagaHelp, npcKiMode, npcStrengthPercent, npcGrowthPercent, attackMinecraftMobs,
                npcChatFrequencyPercent, earthGuardianResponsePercent, maxRememberedDeadFighters, npcDespawnProtectionRadius,
                levelMultiplierPerSaga, maxDefenseMitigation, bpVisualMultiplier, npcPowerMultiplier, canMeditationProcSkillProgression,
                csv(npcRaceBlacklist), treatRaceBlacklistAsWhitelist, csv(canUseClothes), csv(dimensionWhitelist),
                treatDimensionWhitelistAsBlacklist, csv(companionDimensionBlacklist), java.util.Arrays.stream(archetypeShares).boxed().toList(), worldMenacesEnabled, wantedPressureDecayMinutes, npcCombatLives, racialSkillMinimumEra, npcsEnabledOnCreativeMode);
    }

    private LivingWorldClientConfig.Snapshot clientSnapshot() {
        return new LivingWorldClientConfig.Snapshot(nameScale, dialogueScale, verticalOffset, dispositionIcon,
                factionLabel, dialogueVisible, nameplateDistance, factionLabelDistance, dialogueDistance, speechToChat, speechChatRadius, hudScale, factionQuestHudScale);
    }

    private MeditationConfig.ServerSnapshot meditationSnapshot() {
        return new MeditationConfig.ServerSnapshot(medEnabled, medTpRewards, medTpScale, medRewardInterval,
                medFocused, medCentered, medDeep, medTranscendent, medCalmMultiplier, medFocusedMultiplier,
                medCenteredMultiplier, medDeepMultiplier, medTranscendentMultiplier, medDamageInterrupts, medDamageCooldown,
                medGroup, medGroupRadius, medNpc, medBreakthroughs, medBreakthroughChance, medBreakthroughRoll,
                medBreakthroughPoints, medFormMastery, medDeepMastery, medTransMastery, medLevitation,
                medParticles, medParticleDensity, medSounds);
    }

    private MeditationConfig.ClientSnapshot meditationClientSnapshot() {
        return new MeditationConfig.ClientSnapshot(medHud, medSummary, 5, medNativeAnimation,
                medHudOffset, medAuraIntensity, medFocusSeal, medSealIntensity, medSealSize, medFirstPersonAura, medStageFx, medNpcFx);
    }

    private int rowCount() {
        return switch (tab) { case 0 -> 12; case 1 -> 6; case 2 -> 13; case 3 -> 15; case 4 -> 10; case 6 -> 35; default -> 11; };
    }

    private boolean serverTab() { return tab == 0 || tab == 1 || tab == 3 || tab == 5 || tab == 6; }
    private boolean rowEditable() { return !serverTab() || canEdit; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        LivingWorldGuiStyle.drawPanel(g, panelLeft, panelTop, panelWidth, panelHeight);
        LivingWorldGuiStyle.drawFitted(g, font, LWLang.string("screen.world_settings.header"), panelLeft + 11, panelTop + 9,
                Math.max(70, panelWidth - 110), LivingWorldGuiStyle.GOLD);
        LivingWorldGuiStyle.drawFitted(g, font, LWLang.string("screen.world_settings.subtitle"), panelLeft + 11, panelTop + 24,
                Math.max(70, panelWidth - 110), LivingWorldGuiStyle.MUTED);
        LivingWorldGuiStyle.drawButton(g, font, panelLeft + panelWidth - 35, panelTop + 8, 24, 20, "×",
                mouseX, mouseY, true, false, false);
        drawTabs(g, mouseX, mouseY);

        int bodyLeft = panelLeft + 8, bodyRight = panelLeft + panelWidth - 8;
        LivingWorldGuiStyle.drawInsetPanel(g, bodyLeft, contentTop - 3, bodyRight - bodyLeft, settingsBodyBottom() - contentTop + 3);
        for (int r = 0; r < rowCount(); r++) {
            int y = contentTop + r * rowHeight - scroll;
            if (y < contentTop || y + rowHeight - 3 > settingsBodyBottom()) continue;
            drawRow(g, r, y, mouseX, mouseY);
        }

        String access = tabContextLine();
        if (serverTab() && !canEdit) access = LWLang.string("screen.world_settings.view_only", access);
        int contextY = footerY - 25;
        LivingWorldGuiStyle.drawFitted(g, font, access, panelLeft + 11, contextY,
                Math.max(40, panelWidth - 22), LivingWorldGuiStyle.MUTED);
        int statusY = footerY - 13;
        boolean transientStatus = !status.isBlank() && Util.getMillis() <= statusUntil;
        String line = transientStatus ? status : (dirty ? LWLang.string("screen.world_settings.unsaved") : "");
        if (!line.isBlank()) LivingWorldGuiStyle.drawFitted(g, font, line, panelLeft + 11, statusY,
                Math.max(40, panelWidth - 22), transientStatus ? LivingWorldGuiStyle.GREEN : LivingWorldGuiStyle.GOLD);

        int compact = panelWidth < 300 ? 1 : 0;
        int gap = panelWidth < 210 ? 3 : 5;
        int defaultsW, bw, defaultsX = panelLeft + 10, saveX, backX;
        int normalNeed = (compact == 1 ? 56 : 72) + (compact == 1 ? 52 : 68) * 2 + gap * 2;
        if (panelWidth - 20 < normalNeed) {
            int each = Math.max(24, (panelWidth - 20 - gap * 2) / 3);
            defaultsW = bw = each;
            saveX = defaultsX + each + gap;
            backX = saveX + each + gap;
        } else {
            bw = compact == 1 ? 52 : 68;
            defaultsW = compact == 1 ? 56 : 72;
            backX = panelLeft + panelWidth - 10 - bw;
            saveX = backX - gap - bw;
        }
        LivingWorldGuiStyle.drawButton(g, font, defaultsX, footerY, defaultsW, 20, compact == 1 ? LWLang.string("button.reset") : LWLang.string("button.defaults"),
                mouseX, mouseY, rowEditable(), false, false);
        if (tab == 0 && canEdit)
            LivingWorldGuiStyle.drawButton(g, font, defaultsX + defaultsW + gap, footerY, 130, 20,
                    LWLang.string("button.reset_npc_data"), mouseX, mouseY, true, false, false);
        LivingWorldGuiStyle.drawButton(g, font, saveX, footerY, bw, 20, LWLang.string("button.save"), mouseX, mouseY,
                !serverTab() || canEdit, false, true);
        LivingWorldGuiStyle.drawButton(g, font, backX, footerY, bw, 20, LWLang.string("button.back"), mouseX, mouseY, true, false, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        int x = panelLeft + 8;
        int y = panelTop + 43;
        int gap = 3;
        int total = panelWidth - 16 - gap * (TAB_KEYS.length - 1);
        int w = Math.max(18, total / TAB_KEYS.length);
        for (int i=0;i<TAB_KEYS.length;i++) {
            int tx = x + i * (w + gap);
            int logicalTab = TAB_ORDER[i];
            LivingWorldGuiStyle.drawButton(g, font, tx, y, w, 20, LWLang.string("screen.world_settings.tab." + TAB_KEYS[i]), mouseX, mouseY, true, logicalTab == tab, false);
        }
    }

    private void drawRow(GuiGraphics g, int row, int y, int mouseX, int mouseY) {
        int left = panelLeft + 12, right = panelLeft + panelWidth - 12, h = rowHeight - 3;
        int stripe = tab == 3 || tab == 4 ? LivingWorldGuiStyle.BLUE : tab == 2 ? LivingWorldGuiStyle.GREEN : LivingWorldGuiStyle.GOLD;
        g.fill(left, y, right, y + h, 0xFF405D75);
        g.fill(left + 1, y + 1, right - 1, y + h - 1, (row & 1) == 0 ? 0xEE101923 : 0xEE14202C);
        g.fill(left + 1, y + 1, left + 4, y + h - 1, stripe);

        int controlW = Math.min(200, Math.max(108, (right-left) / 3));
        int controlX = right - controlW - 5;
        boolean narrow = panelWidth < 430;
        int textX = left + 8;
        if (narrow) {
            LivingWorldGuiStyle.drawFitted(g, font, rowName(row), textX, y + 4, right - textX - 8, LivingWorldGuiStyle.TEXT);
            drawControl(g, row, controlX, y + h - 21, controlW, 18, mouseX, mouseY);
        } else {
            LivingWorldGuiStyle.drawFitted(g, font, rowName(row), textX, y + 4,
                    Math.max(30, controlX - textX - 8), LivingWorldGuiStyle.TEXT);
            if (h >= 30) LivingWorldGuiStyle.drawFitted(g, font, rowHelp(row), textX, y + 16,
                    Math.max(30, controlX - textX - 8), LivingWorldGuiStyle.MUTED);
            drawControl(g, row, controlX, y + Math.max(2,(h-20)/2), controlW, 20, mouseX, mouseY);
        }
    }

    private void drawControl(GuiGraphics g, int row, int x, int y, int w, int h, int mouseX, int mouseY) {
        int arrow = Math.min(26, Math.max(20, w/5));
        int gutter = 4;
        int valueX = x + arrow + gutter;
        int valueW = Math.max(28, w - arrow*2 - gutter*2);
        boolean editable = rowEditable();
        LivingWorldGuiStyle.drawArrowButton(g, font, x, y, arrow, h, "<", mouseX, mouseY, editable && canDecrease(row));
        g.fill(valueX, y, valueX + valueW, y + h, LivingWorldGuiStyle.CONTROL_BORDER);
        g.fill(valueX+1, y+1, valueX+valueW-1, y+h-1, LivingWorldGuiStyle.CONTROL);
        if (editingRow != row || numericEditor == null) {
            String value = rowValue(row);
            String fitted = LivingWorldGuiStyle.fitText(font, value, Math.max(10, valueW - 6));
            g.drawString(font, fitted, valueX + Math.max(2,(valueW-font.width(fitted))/2), y + Math.max(1, (h - font.lineHeight) / 2), valueColor(row), false);
            if (isTypeableRow(row) && !isTextRow(tab, row)) {
                // Every numeric option uses the same hybrid control: arrows for small steps,
                // click the value to type exactly, or drag the thin track for quick adjustment.
                int trackLeft = valueX + 5;
                int trackRight = valueX + valueW - 5;
                int trackY = y + h - 5;
                g.fill(trackLeft, trackY, trackRight, trackY + 2, 0xFF405D75);
                double fraction = numericSliderFraction(row);
                int knobX = trackLeft + (int)Math.round((trackRight - trackLeft - 1) * fraction);
                g.fill(Math.max(trackLeft, knobX - 2), trackY - 2, Math.min(trackRight, knobX + 3), trackY + 4, LivingWorldGuiStyle.GOLD);
            }
        }
        int rx = valueX + valueW + gutter;
        LivingWorldGuiStyle.drawArrowButton(g, font, rx, y, arrow, h, ">", mouseX, mouseY, editable && canIncrease(row));
    }
    private int valueColor(int row) {
        String v = rowValue(row);
        if (LWLang.string("value.on").equals(v)) return LivingWorldGuiStyle.GREEN;
        if (LWLang.string("value.off").equals(v)) return LivingWorldGuiStyle.NEUTRAL;
        return LivingWorldGuiStyle.TEXT;
    }

    private String rowName(int r) {
        if (tab == 6) return advancedName(r);
        String group = switch (tab) { case 0 -> "world"; case 1 -> "relationships"; case 2 -> "display"; case 3 -> "meditation"; case 4 -> "meditation_visuals"; default -> "npc_behaviour"; };
        return LWLang.string("screen.world_settings.row." + group + "." + r);
    }

    private String rowHelp(int r) {
        if (tab == 0) return worldRowHelp(r);
        if (tab == 5) return npcBehaviorRowHelp(r);
        if (tab == 6) return LWLang.string(r >= 14
                ? "screen.world_settings.help.advanced.share"
                : "screen.world_settings.help.advanced." + r);
        String group = switch (tab) { case 1 -> "relationships"; case 2 -> "display"; case 3 -> "meditation"; default -> "meditation_visuals"; };
        int keyRow = tab == 3 && r >= 4 && r <= 8 ? 4 : r;
        return LWLang.string("screen.world_settings.help." + group + "." + keyRow);
    }

    private String worldRowHelp(int r) {
        return switch (r) {
            case 0 -> LWLang.string("screen.world_settings.help.world.0", nearbyFighterCap, performanceCost(nearbyFighterCap));
            case 1 -> LWLang.string("screen.world_settings.help.world.1", nearbyHostileCap);
            case 2 -> LWLang.string(recurringFighters ? "screen.world_settings.help.world.2.on" : "screen.world_settings.help.world.2.off");
            case 3 -> LWLang.string("screen.world_settings.help.world.3", livingPresenceTargetBase);
            case 4 -> LWLang.string("screen.world_settings.help.world.4", livingPresenceRadius);
            case 5 -> LWLang.string("screen.world_settings.help.world.5", factionResidentCap, performanceCost(factionResidentCap >= 20 ? 64 : 30));
            case 6 -> LWLang.string(factionEncounters ? "screen.world_settings.help.world.6.on" : "screen.world_settings.help.world.6.off");
            case 7 -> LWLang.string(dynamicEncounters ? "screen.world_settings.help.world.7.on" : "screen.world_settings.help.world.7.off");
            case 8 -> LWLang.string(worldIncidents ? "screen.world_settings.help.world.8.on" : "screen.world_settings.help.world.8.off");
            case 9 -> LWLang.string(worldEventAlerts ? "screen.world_settings.help.world.9.on" : "screen.world_settings.help.world.9.off");
            case 10 -> LWLang.string("screen.world_settings.help.world.10", worldEventAlertRadius);
            default -> LWLang.string(earthGuardianResponsePercent == 100 ? "screen.world_settings.help.world.11.normal" : "screen.world_settings.help.world.11.scaled", earthGuardianResponsePercent);
        };
    }

    private String performanceCost(int value) {
        String level = value >= 512 ? "extreme" : value >= 128 ? "very_high" : value >= 64 ? "high" : value >= 30 ? "moderate" : "low";
        return LWLang.string("screen.world_settings.performance." + level);
    }

    private String npcBehaviorRowHelp(int r) {
        return switch (r) {
            case 0 -> {
                String state = npcChaosPercent <= 0 ? "off" : npcChaosPercent < 50 ? "quiet" : npcChaosPercent < 100 ? "reduced" : npcChaosPercent == 100 ? "normal" : npcChaosPercent <= 200 ? "increased" : "chaotic";
                yield LWLang.string("screen.world_settings.help.npc_behaviour.activity." + state, npcChaosPercent);
            }
            case 1 -> LWLang.string("screen.world_settings.help.npc_behaviour.sensing." + (automaticPowerSensing ? "on" : "off"));
            case 2 -> LWLang.string("screen.world_settings.help.npc_behaviour.companion." + (companionSagaHelp ? "on" : "off"));
            case 3 -> LWLang.string("screen.world_settings.help.npc_behaviour.ki." + npcKiMode);
            case 4 -> LWLang.string(npcStrengthPercent == 100 ? "screen.world_settings.help.npc_behaviour.strength.normal" : "screen.world_settings.help.npc_behaviour.strength.scaled", npcStrengthPercent);
            case 5 -> LWLang.string("screen.world_settings.help.npc_behaviour.growth." + (npcGrowthPercent == 0 ? "off" : npcGrowthPercent == 100 ? "normal" : "scaled"), npcGrowthPercent);
            case 6 -> LWLang.string(npcChatFrequencyPercent == 100 ? "screen.world_settings.help.npc_behaviour.chat.normal" : "screen.world_settings.help.npc_behaviour.chat.scaled", npcChatFrequencyPercent);
            case 7 -> LWLang.string("screen.world_settings.help.npc_behaviour.minecraft_mobs." + (attackMinecraftMobs ? "on" : "off"));
            case 8 -> LWLang.string("screen.world_settings.help.npc_behaviour.menaces." + (worldMenacesEnabled ? "on" : "off"));
            case 9 -> LWLang.string(wantedPressureDecayMinutes <= 0 ? "screen.world_settings.help.npc_behaviour.wanted.off" : "screen.world_settings.help.npc_behaviour.wanted.on");
            default -> LWLang.string("screen.world_settings.help.npc_behaviour.creative." + (npcsEnabledOnCreativeMode ? "on" : "off"));
        };
    }

    private String rowValue(int r) {
        return switch (tab) {
            case 0 -> switch (r) { case 0 -> ""+nearbyFighterCap; case 1 -> ""+nearbyHostileCap;
                case 2 -> onOff(recurringFighters); case 3 -> ""+livingPresenceTargetBase; case 4 -> blocks(livingPresenceRadius);
                case 5 -> ""+factionResidentCap; case 6 -> onOff(factionEncounters); case 7 -> onOff(dynamicEncounters);
                case 8 -> onOff(worldIncidents); case 9 -> onOff(worldEventAlerts); case 10 -> blocks(worldEventAlertRadius);
                default -> earthGuardianResponsePercent+"%"; };
            case 1 -> switch (r) { case 0 -> onOff(socialTalk); case 1 -> "+"+talkBaseGain; case 2 -> ""+talkRelationshipCap;
                case 3 -> seconds(talkCooldownMinSeconds); case 4 -> seconds(talkCooldownMaxSeconds); default -> onOff(npcSocializing); };
            case 2 -> switch (r) { case 0 -> pct(nameScale); case 1 -> pct(dialogueScale); case 2 -> String.format(java.util.Locale.ROOT,"%.2f",verticalOffset);
                case 3 -> onOff(dispositionIcon); case 4 -> onOff(factionLabel); case 5 -> onOff(dialogueVisible);
                case 6 -> blocks(nameplateDistance); case 7 -> blocks(factionLabelDistance); case 8 -> blocks(dialogueDistance);
                case 9 -> chatSpeechMode(); case 10 -> blocks(speechChatRadius);
                case 11 -> pct(hudScale); default -> pct(factionQuestHudScale); };
            case 3 -> switch (r) { case 0 -> onOff(medEnabled); case 1 -> onOff(medTpRewards); case 2 -> medTpScale+"%";
                case 3 -> seconds(medRewardInterval); case 4 -> "x"+medCalmMultiplier; case 5 -> "x"+medFocusedMultiplier;
                case 6 -> "x"+medCenteredMultiplier; case 7 -> "x"+medDeepMultiplier; case 8 -> "x"+medTranscendentMultiplier;
                case 9 -> onOff(medNpc); case 10 -> onOff(medDamageInterrupts); case 11 -> onOff(medBreakthroughs);
                case 12 -> formatNumber(medBreakthroughChance,2)+"%"; case 13 -> medBreakthroughPoints+"%";
                default -> onOff(medFormMastery); };
            case 4 -> switch (r) { case 0 -> onOff(medHud); case 1 -> onOff(medSummary);
                case 2 -> onOff(medNativeAnimation); case 3 -> LWLang.string("value.pixels", medHudOffset); case 4 -> medAuraIntensity+"%";
                case 5 -> onOff(medFocusSeal); case 6 -> medSealIntensity+"%"; case 7 -> medSealSize+"%";
                case 8 -> onOff(medNpcFx); default -> onOff(medStageFx); };
            case 6 -> advancedValue(r);
            default -> switch (r) { case 0 -> npcChaosPercent+"%"; case 1 -> onOff(automaticPowerSensing);
                case 2 -> onOff(companionSagaHelp); case 3 -> kiModeLabel(); case 4 -> npcStrengthPercent+"%";
                case 5 -> npcGrowthPercent+"%"; case 6 -> npcChatFrequencyPercent+"%"; case 7 -> onOff(attackMinecraftMobs); case 8 -> onOff(worldMenacesEnabled);
                case 9 -> LWLang.string("value.minutes", wantedPressureDecayMinutes); default -> onOff(npcsEnabledOnCreativeMode); };
        };
    }

    private boolean canDecrease(int r) { return rowEditable() && canStep(r,-1); }
    private boolean canIncrease(int r) { return rowEditable() && canStep(r,1); }
    private boolean canStep(int r, int d) {
        if (tab == 2 && r == 9) {
            int mode = speechModeIndex();
            return d < 0 ? mode > 0 : mode < 2;
        }
        // Booleans always have one useful direction; numeric bounds are clamped by changeValue.
        String v = rowValue(r);
        if (LWLang.string("value.on").equals(v)) return d < 0;
        if (LWLang.string("value.off").equals(v)) return d > 0;
        return true;
    }

    private void changeValue(int r, int d) {
        if (!rowEditable() || d == 0) return;
        String before = rowValue(r);
        int stepSign = Integer.signum(d);
        switch (tab) {
            case 0 -> {
                switch (r) {
                    case 0 -> nearbyFighterCap=step(FIGHTER_CAPS,nearbyFighterCap,stepSign);
                    case 1 -> nearbyHostileCap=clamp(step(HOSTILE_CAPS,nearbyHostileCap,stepSign),0,nearbyFighterCap);
                    case 2 -> recurringFighters=stepSign>0;
                    case 3 -> livingPresenceTargetBase=clamp(livingPresenceTargetBase+stepSign,0,128);
                    case 4 -> livingPresenceRadius=step(PRESENCE_RADII,livingPresenceRadius,stepSign);
                    case 5 -> factionResidentCap=clamp(factionResidentCap+stepSign,4,512);
                    case 6 -> factionEncounters=stepSign>0;
                    case 7 -> dynamicEncounters=stepSign>0;
                    case 8 -> worldIncidents=stepSign>0;
                    case 9 -> worldEventAlerts=stepSign>0;
                    case 10 -> worldEventAlertRadius=step(ALERT_RADII,worldEventAlertRadius,stepSign);
                    case 11 -> earthGuardianResponsePercent=clamp(earthGuardianResponsePercent+stepSign*5,0,500);
                    default -> {}
                }
                nearbyHostileCap=Math.min(nearbyHostileCap,nearbyFighterCap);
            }
            case 1 -> { switch (r) {
                case 0 -> socialTalk=stepSign>0;
                case 1 -> talkBaseGain=clamp(talkBaseGain+stepSign,0,50);
                case 2 -> talkRelationshipCap=clamp(talkRelationshipCap+stepSign*5,0,100);
                case 3 -> { talkCooldownMinSeconds=clamp(talkCooldownMinSeconds+stepSign*15,10,86400); talkCooldownMaxSeconds=Math.max(talkCooldownMaxSeconds,talkCooldownMinSeconds); }
                case 4 -> talkCooldownMaxSeconds=clamp(talkCooldownMaxSeconds+stepSign*15,Math.max(10,talkCooldownMinSeconds),86400);
                case 5 -> npcSocializing=stepSign>0;
                default -> {}
            } }
            case 2 -> { switch (r) { case 0 -> nameScale=clamp(nameScale+stepSign*0.05,0.65,4.0); case 1 -> dialogueScale=clamp(dialogueScale+stepSign*0.05,0.65,4.0);
                    case 2 -> verticalOffset=clamp(verticalOffset+stepSign*0.05,-0.25,8.0); case 3 -> dispositionIcon=stepSign>0; case 4 -> factionLabel=stepSign>0;
                    case 5 -> dialogueVisible=stepSign>0; case 6 -> nameplateDistance=clamp(nameplateDistance+stepSign*16,8,4096);
                    case 7 -> factionLabelDistance=clamp(factionLabelDistance+stepSign*16,6,4096); case 8 -> dialogueDistance=clamp(dialogueDistance+stepSign*16,8,4096);
                    case 9 -> setSpeechMode(speechModeIndex()+stepSign); case 10 -> speechChatRadius=clamp(speechChatRadius+stepSign*2,8,70);
                    case 11 -> hudScale=clamp(hudScale+stepSign*.05D,.5D,3D); case 12 -> factionQuestHudScale=clamp(factionQuestHudScale+stepSign*.05D,.5D,3D); default -> {} } }
            case 3 -> { switch (r) { case 0 -> medEnabled=stepSign>0; case 1 -> medTpRewards=stepSign>0; case 2 -> medTpScale=clamp(medTpScale+stepSign*10,0,5000);
                    case 3 -> medRewardInterval=clamp(medRewardInterval+stepSign,1,120); case 4 -> medCalmMultiplier=clamp(medCalmMultiplier+stepSign,1,100);
                    case 5 -> medFocusedMultiplier=clamp(medFocusedMultiplier+stepSign,1,100); case 6 -> medCenteredMultiplier=clamp(medCenteredMultiplier+stepSign,1,100);
                    case 7 -> medDeepMultiplier=clamp(medDeepMultiplier+stepSign,1,100); case 8 -> medTranscendentMultiplier=clamp(medTranscendentMultiplier+stepSign,1,100);
                    case 9 -> medNpc=stepSign>0; case 10 -> medDamageInterrupts=stepSign>0; case 11 -> medBreakthroughs=stepSign>0;
                    case 12 -> medBreakthroughChance=clamp(medBreakthroughChance+stepSign*0.1,0.0,100.0); case 13 -> medBreakthroughPoints=clamp(medBreakthroughPoints+stepSign,1,100);
                    case 14 -> medFormMastery=stepSign>0; default -> {} } }
            case 4 -> { switch (r) { case 0 -> medHud=stepSign>0; case 1 -> medSummary=stepSign>0; case 2 -> medNativeAnimation=stepSign>0;
                    case 3 -> medHudOffset=clamp(medHudOffset+stepSign*4,0,240); case 4 -> medAuraIntensity=clamp(medAuraIntensity+stepSign*10,0,250);
                    case 5 -> medFocusSeal=stepSign>0; case 6 -> medSealIntensity=clamp(medSealIntensity+stepSign*10,25,200); case 7 -> medSealSize=clamp(medSealSize+stepSign*10,60,160);
                    case 8 -> medNpcFx=stepSign>0; case 9 -> medStageFx=stepSign>0; default -> {} } }
            case 5 -> { switch (r) {
                case 0 -> npcChaosPercent=clamp(npcChaosPercent+stepSign*10,0,1000);
                case 1 -> automaticPowerSensing=stepSign>0;
                case 2 -> companionSagaHelp=stepSign>0;
                case 3 -> npcKiMode=KI_MODE_ORDER[clamp(kiModeIndex()+stepSign,0,KI_MODE_ORDER.length-1)];
                case 4 -> npcStrengthPercent=clamp(npcStrengthPercent+stepSign*5,25,1000);
                case 5 -> npcGrowthPercent=clamp(npcGrowthPercent+stepSign*10,0,1000);
                case 6 -> npcChatFrequencyPercent=clamp(npcChatFrequencyPercent+stepSign*5,0,500);
                case 7 -> attackMinecraftMobs=stepSign>0;
                case 8 -> worldMenacesEnabled=stepSign>0;
                case 9 -> wantedPressureDecayMinutes=clamp(wantedPressureDecayMinutes+stepSign*5,0,10080);
                case 10 -> npcsEnabledOnCreativeMode=stepSign>0;
                default -> {}
            } }
            case 6 -> changeAdvanced(r, stepSign);
        }
        if (!before.equals(rowValue(r))) markDirty();
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (numericEditor != null && inside(mouseX, mouseY, numericEditor.getX(), numericEditor.getY(), numericEditor.getWidth(), numericEditor.getHeight())) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        commitNumericEditor();
        if (inside(mouseX,mouseY,panelLeft+panelWidth-35,panelTop+8,24,20)) { back(); return true; }
        int tx=panelLeft+8, ty=panelTop+43, gap=3, total=panelWidth-16-gap*(TAB_KEYS.length-1), tw=Math.max(18,total/TAB_KEYS.length);
        for (int i=0;i<TAB_KEYS.length;i++) if (inside(mouseX,mouseY,tx+i*(tw+gap),ty,tw,20)) { tab=TAB_ORDER[i]; scroll=0; clampScroll(); return true; }
        int left=panelLeft+12,right=panelLeft+panelWidth-12;
        int controlW=Math.min(200,Math.max(108,(right-left)/3)), arrow=Math.min(26,Math.max(20,controlW/5)), gutter=4;
        int controlX=right-controlW-5, valueX=controlX+arrow+gutter, valueW=Math.max(28,controlW-arrow*2-gutter*2), rx=valueX+valueW+gutter;
        for (int r=0;r<rowCount();r++) {
            int y=contentTop+r*rowHeight-scroll;
            if (y<contentTop || y+rowHeight-3>settingsBodyBottom()) continue;
            int h=rowHeight-3, cy=panelWidth<430?y+h-21:y+Math.max(2,(h-20)/2), ch=panelWidth<430?18:20;
            if (inside(mouseX,mouseY,valueX,cy,valueW,ch) && isTypeableRow(r) && rowEditable()) {
                if (!isTextRow(tab, r) && mouseY >= cy + ch - 7) {
                    draggingSliderTab = tab;
                    draggingSliderRow = r;
                    setNumericFromSlider(r, mouseX, valueX, valueW);
                } else {
                    beginNumericEditor(r, valueX, cy, valueW, ch);
                }
                return true;
            }
            if (inside(mouseX,mouseY,controlX,cy,arrow,ch)) { changeValue(r,-1); return true; }
            if (inside(mouseX,mouseY,rx,cy,arrow,ch)) { changeValue(r,1); return true; }
        }
        int compact=panelWidth<300?1:0, gapB=panelWidth<210?3:5;
        int defaultsW,bw,defaultsX=panelLeft+10,saveX,backX;
        int normalNeed=(compact==1?56:72)+(compact==1?52:68)*2+gapB*2;
        if(panelWidth-20<normalNeed){int each=Math.max(24,(panelWidth-20-gapB*2)/3);defaultsW=bw=each;saveX=defaultsX+each+gapB;backX=saveX+each+gapB;}
        else{bw=compact==1?52:68;defaultsW=compact==1?56:72;backX=panelLeft+panelWidth-10-bw;saveX=backX-gapB-bw;}
        if (tab == 0 && canEdit && inside(mouseX, mouseY, defaultsX + defaultsW + gapB, footerY, 130, 20)) {
            Minecraft.getInstance().setScreen(new ResetNpcDataConfirmScreen(this)); return true;
        }
        if (inside(mouseX,mouseY,defaultsX,footerY,defaultsW,20)) { resetCurrent(); return true; }
        if (inside(mouseX,mouseY,saveX,footerY,bw,20)) { save(); return true; }
        if (inside(mouseX,mouseY,backX,footerY,bw,20)) { back(); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingSliderRow >= 0 && draggingSliderTab == tab && rowEditable()) {
            int left=panelLeft+12,right=panelLeft+panelWidth-12;
            int controlW=Math.min(200,Math.max(108,(right-left)/3)), arrow=Math.min(26,Math.max(20,controlW/5)), gutter=4;
            int controlX=right-controlW-5, valueX=controlX+arrow+gutter, valueW=Math.max(28,controlW-arrow*2-gutter*2);
            setNumericFromSlider(draggingSliderRow, mouseX, valueX, valueW);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingSliderRow >= 0) {
            draggingSliderRow = -1;
            draggingSliderTab = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private double numericSliderFraction(int row) {
        double min = numericMin(row), max = numericMax(row), value = numericValue(row);
        if (max <= min) return 0.0D;
        return clamp((value - min) / (max - min), 0.0D, 1.0D);
    }

    private double numericValue(int row) {
        return switch (tab) {
            case 0 -> switch (row) { case 0 -> nearbyFighterCap; case 1 -> nearbyHostileCap; case 3 -> livingPresenceTargetBase;
                case 4 -> livingPresenceRadius; case 5 -> factionResidentCap; case 10 -> worldEventAlertRadius; default -> earthGuardianResponsePercent; };
            case 1 -> switch (row) { case 1 -> talkBaseGain; case 2 -> talkRelationshipCap; case 3 -> talkCooldownMinSeconds; default -> talkCooldownMaxSeconds; };
            case 2 -> switch (row) { case 0 -> nameScale * 100.0D; case 1 -> dialogueScale * 100.0D; case 2 -> verticalOffset;
                case 6 -> nameplateDistance; case 7 -> factionLabelDistance; case 8 -> dialogueDistance; case 10 -> speechChatRadius; case 11 -> hudScale * 100D; default -> factionQuestHudScale * 100D; };
            case 3 -> switch (row) { case 2 -> medTpScale; case 3 -> medRewardInterval; case 4 -> medCalmMultiplier;
                case 5 -> medFocusedMultiplier; case 6 -> medCenteredMultiplier; case 7 -> medDeepMultiplier; case 8 -> medTranscendentMultiplier;
                case 12 -> medBreakthroughChance; default -> medBreakthroughPoints; };
            case 4 -> switch (row) { case 3 -> medHudOffset; case 4 -> medAuraIntensity; case 6 -> medSealIntensity; default -> medSealSize; };
            case 6 -> row == 5 ? npcPowerMultiplier * 100.0D : advancedNumericValue(row);
            default -> switch (row) { case 0 -> npcChaosPercent; case 4 -> npcStrengthPercent; case 5 -> npcGrowthPercent; case 9 -> wantedPressureDecayMinutes; default -> npcChatFrequencyPercent; };
        };
    }

    private double numericMin(int row) {
        return switch (tab) {
            case 0 -> switch (row) { case 0,1,3 -> 0.0D; case 4 -> 96.0D; case 5 -> 4.0D; case 10 -> 128.0D; default -> 0.0D; };
            case 1 -> switch (row) { case 1,2 -> 0.0D; case 3,4 -> 10.0D; default -> 0.0D; };
            case 2 -> switch (row) { case 0,1,11,12 -> 50D; case 2 -> -0.25D; case 6,8,10 -> 8.0D; case 7 -> 6.0D; default -> 0.0D; };
            case 3 -> switch (row) { case 2 -> 0.0D; case 3,4,5,6,7,8,13 -> 1.0D; case 12 -> 0.0D; default -> 0.0D; };
            case 4 -> switch (row) { case 3,4 -> 0.0D; case 6 -> 25.0D; case 7 -> 60.0D; default -> 0.0D; };
            case 6 -> row == 1 ? 96D : row == 2 ? .1D : 0D;
            default -> switch (row) { case 0,5 -> 0.0D; case 4 -> 25.0D; default -> 0.0D; };
        };
    }

    private double numericMax(int row) {
        return switch (tab) {
            case 0 -> switch (row) { case 0 -> 4096.0D; case 1 -> Math.max(0, nearbyFighterCap); case 3 -> 128.0D;
                case 4 -> 8192.0D; case 5 -> 512.0D; case 10 -> 32768.0D; default -> 500.0D; };
            case 1 -> switch (row) { case 1 -> 50.0D; case 2 -> 100.0D; case 3,4 -> 86400.0D; default -> 500.0D; };
            case 2 -> switch (row) { case 0,1 -> 400.0D; case 2 -> 8.0D; case 10 -> 70.0D; case 11,12 -> 300D; default -> 4096.0D; };
            case 3 -> switch (row) { case 2 -> 5000.0D; case 3 -> 120.0D; case 4,5,6,7,8 -> 100.0D;
                case 12 -> 100.0D; default -> 100.0D; };
            case 4 -> switch (row) { case 3 -> 240.0D; case 4 -> 250.0D; case 6 -> 200.0D; default -> 160.0D; };
            case 6 -> row == 0 || row == 1 ? 4096D : row == 2 ? 1000D : row == 3 ? .99D : row == 4 ? 1_000_000D : row == 5 ? 1000D : row == 13 ? 100D : row == 14 ? 10000D : 10D;
            default -> switch (row) { case 0,4,5 -> 1000.0D; case 6 -> 500.0D; case 9 -> 10080.0D; default -> 1000.0D; };
        };
    }

    private void setNumericFromSlider(int row, double mouseX, int valueX, int valueW) {
        int trackLeft = valueX + 5;
        int trackRight = valueX + valueW - 5;
        double fraction = clamp((mouseX - trackLeft) / Math.max(1.0D, trackRight - trackLeft), 0.0D, 1.0D);
        double min = numericMin(row), max = numericMax(row);
        double raw = min + (max - min) * fraction;
        switch (tab) {
            case 0 -> { switch (row) {
                case 0 -> { nearbyFighterCap = clamp((int)Math.round(raw),0,4096); nearbyHostileCap=Math.min(nearbyHostileCap,nearbyFighterCap); }
                case 1 -> nearbyHostileCap=clamp((int)Math.round(raw),0,nearbyFighterCap);
                case 3 -> livingPresenceTargetBase=clamp((int)Math.round(raw),0,128);
                case 4 -> livingPresenceRadius=clamp((int)Math.round(raw/16.0D)*16,96,8192);
                case 5 -> factionResidentCap=clamp((int)Math.round(raw),4,512);
                case 10 -> worldEventAlertRadius=clamp((int)Math.round(raw/16.0D)*16,128,32768);
                case 11 -> earthGuardianResponsePercent=clamp((int)Math.round(raw/5.0D)*5,0,500);
                default -> {}
            } }
            case 1 -> { switch (row) {
                case 1 -> talkBaseGain=clamp((int)Math.round(raw),0,50);
                case 2 -> talkRelationshipCap=clamp((int)Math.round(raw),0,100);
                case 3 -> { talkCooldownMinSeconds=clamp((int)Math.round(raw/15.0D)*15,10,86400); talkCooldownMaxSeconds=Math.max(talkCooldownMaxSeconds,talkCooldownMinSeconds); }
                case 4 -> talkCooldownMaxSeconds=clamp((int)Math.round(raw/15.0D)*15,Math.max(10,talkCooldownMinSeconds),86400);
                default -> {}
            } }
            case 2 -> { switch (row) {
                case 0 -> nameScale=clamp(Math.round(raw/5.0D)*5.0D/100.0D,0.65D,4.0D);
                case 1 -> dialogueScale=clamp(Math.round(raw/5.0D)*5.0D/100.0D,0.65D,4.0D);
                case 2 -> verticalOffset=clamp(Math.round(raw*20.0D)/20.0D,-0.25D,8.0D);
                case 6 -> nameplateDistance=clamp((int)Math.round(raw/16.0D)*16,8,4096);
                case 7 -> factionLabelDistance=clamp((int)Math.round(raw/16.0D)*16,6,4096);
                case 8 -> dialogueDistance=clamp((int)Math.round(raw/16.0D)*16,8,4096);
                case 10 -> speechChatRadius=clamp((int)Math.round(raw/2.0D)*2,8,70);
                case 11 -> hudScale=clamp(Math.round(raw/5D)*5D/100D,.5D,3D);
                case 12 -> factionQuestHudScale=clamp(Math.round(raw/5D)*5D/100D,.5D,3D);
                default -> {}
            } }
            case 3 -> { switch (row) {
                case 2 -> medTpScale=clamp((int)Math.round(raw/10.0D)*10,0,5000);
                case 3 -> medRewardInterval=clamp((int)Math.round(raw),1,120);
                case 4 -> medCalmMultiplier=clamp((int)Math.round(raw),1,100);
                case 5 -> medFocusedMultiplier=clamp((int)Math.round(raw),1,100);
                case 6 -> medCenteredMultiplier=clamp((int)Math.round(raw),1,100);
                case 7 -> medDeepMultiplier=clamp((int)Math.round(raw),1,100);
                case 8 -> medTranscendentMultiplier=clamp((int)Math.round(raw),1,100);
                case 12 -> medBreakthroughChance=clamp(Math.round(raw*10.0D)/10.0D,0.0D,100.0D);
                case 13 -> medBreakthroughPoints=clamp((int)Math.round(raw),1,100);
                default -> {}
            } }
            case 4 -> { switch (row) {
                case 3 -> medHudOffset=clamp((int)Math.round(raw/4.0D)*4,0,240);
                case 4 -> medAuraIntensity=clamp((int)Math.round(raw/10.0D)*10,0,250);
                case 6 -> medSealIntensity=clamp((int)Math.round(raw/5.0D)*5,25,200);
                case 7 -> medSealSize=clamp((int)Math.round(raw/5.0D)*5,60,160);
                default -> {}
            } }
            case 5 -> { switch (row) {
                case 0 -> npcChaosPercent=clamp((int)Math.round(raw/5.0D)*5,0,1000);
                case 4 -> npcStrengthPercent=clamp((int)Math.round(raw/5.0D)*5,25,1000);
                case 5 -> npcGrowthPercent=clamp((int)Math.round(raw/5.0D)*5,0,1000);
                case 6 -> npcChatFrequencyPercent=clamp((int)Math.round(raw/5.0D)*5,0,500);
                case 9 -> wantedPressureDecayMinutes=clamp((int)Math.round(raw/5.0D)*5,0,10080);
                default -> {}
            } }
            case 6 -> setAdvancedNumeric(row, row == 5 ? raw / 100.0D : raw);
        }
        markDirty();
    }

    private int speechModeIndex() {
        if (!speechToChat) return 0;
        return dialogueVisible ? 2 : 1;
    }

    private String chatSpeechMode() {
        return switch (speechModeIndex()) {
            case 1 -> LWLang.string("value.speech.chat_only");
            case 2 -> LWLang.string("value.speech.chat_overhead");
            default -> LWLang.string("value.off");
        };
    }

    private void setSpeechMode(int mode) {
        mode = clamp(mode, 0, 2);
        if (mode == 0) {
            speechToChat = false;
        } else if (mode == 1) {
            speechToChat = true;
            dialogueVisible = false;
        } else {
            speechToChat = true;
            dialogueVisible = true;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= contentTop && mouseY <= settingsBodyBottom()) {
            commitNumericEditor();
            scroll -= (int)Math.signum(delta) * rowHeight;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void clampScroll() {
        int available = Math.max(rowHeight, settingsBodyBottom() - contentTop);
        int visibleRows = Math.max(1, available / rowHeight);
        int max = Math.max(0, (rowCount() - visibleRows) * rowHeight);
        scroll = clamp(scroll, 0, max);
        // Keep the viewport aligned to complete rows so the final scroll step never hides
        // the top option and leaves a misleading blank strip.
        scroll = (scroll / rowHeight) * rowHeight;
    }

    private void resetCurrent() {
        commitNumericEditor();
        if (!rowEditable()) return;
        switch (tab) {
            case 0 -> resetWorldTabDefaults();
            case 1 -> resetRelationshipTabDefaults();
            case 2 -> loadClient(LivingWorldClientConfig.defaults());
            case 3 -> resetMeditationTabDefaults();
            case 4 -> resetMeditationVisualTabDefaults();
            case 5 -> resetNpcBehaviorTabDefaults();
            case 6 -> resetAdvancedDefaults();
            default -> { return; }
        }
        dirty = true;
        setStatus(LWLang.string("screen.world_settings.defaults_loaded"), 2400L);
    }

    private void resetWorldTabDefaults() {
        LivingWorldConfig.Snapshot d = LivingWorldConfig.defaults();
        nearbyFighterCap = d.nearbyFighterCap();
        nearbyHostileCap = d.nearbyHostileCap();
        recurringFighters = d.recurringFighters();
        livingPresenceTargetBase = d.livingPresenceTargetBase();
        livingPresenceRadius = d.livingPresenceRadius();
        factionResidentCap = d.factionResidentCap();
        factionEncounters = d.factionEncounters();
        dynamicEncounters = d.dynamicEncounters();
        worldIncidents = d.worldIncidents();
        worldEventAlerts = d.worldEventAlerts();
        worldEventAlertRadius = d.worldEventAlertRadius();
        earthGuardianResponsePercent = d.earthGuardianResponsePercent();
    }

    private void resetRelationshipTabDefaults() {
        LivingWorldConfig.Snapshot d = LivingWorldConfig.defaults();
        socialTalk = d.socialTalk();
        talkBaseGain = d.talkBaseGain();
        talkRelationshipCap = d.talkRelationshipCap();
        talkCooldownMinSeconds = d.talkCooldownMinSeconds();
        talkCooldownMaxSeconds = d.talkCooldownMaxSeconds();
        npcSocializing = d.npcSocializing();
    }

    private void resetNpcBehaviorTabDefaults() {
        LivingWorldConfig.Snapshot d = LivingWorldConfig.defaults();
        npcChaosPercent = d.npcChaosPercent();
        automaticPowerSensing = d.automaticPowerSensing();
        companionSagaHelp = d.companionSagaHelp();
        npcKiMode = d.npcKiMode();
        npcStrengthPercent = d.npcStrengthPercent();
        npcGrowthPercent = d.npcGrowthPercent();
        npcChatFrequencyPercent = d.npcChatFrequencyPercent();
        attackMinecraftMobs = d.attackMinecraftMobs();
        worldMenacesEnabled = d.worldMenacesEnabled(); wantedPressureDecayMinutes=d.wantedPressureDecayMinutes();
        npcsEnabledOnCreativeMode = d.npcsEnabledOnCreativeMode();
    }

    private void resetAdvancedDefaults() {
        LivingWorldConfig.Snapshot d=LivingWorldConfig.defaults();
        maxRememberedDeadFighters=d.maxRememberedDeadFighters(); npcDespawnProtectionRadius=d.npcDespawnProtectionRadius(); npcCombatLives=d.npcCombatLives();
        levelMultiplierPerSaga=d.levelMultiplierPerSaga(); maxDefenseMitigation=d.maxDefenseMitigation(); bpVisualMultiplier=d.bpVisualMultiplier();
        canMeditationProcSkillProgression=d.canMeditationProcSkillProgression(); npcRaceBlacklist=String.join(",",d.npcRaceBlacklist());
        treatRaceBlacklistAsWhitelist=d.treatRaceBlacklistAsWhitelist(); canUseClothes=String.join(",",d.canUseClothes());
        dimensionWhitelist=String.join(",",d.dimensionWhitelist()); treatDimensionWhitelistAsBlacklist=d.treatDimensionWhitelistAsBlacklist();
        companionDimensionBlacklist=String.join(",",d.companionDimensionBlacklist());
        for(int i=0;i<20;i++) archetypeShares[i]=d.archetypeShares().get(i);
    }

    private void resetMeditationTabDefaults() {
        MeditationConfig.ServerSnapshot d = MeditationConfig.serverDefaults();
        medEnabled = d.enabled();
        medTpRewards = d.tpRewardsEnabled();
        medTpScale = d.tpRewardScalePercent();
        medRewardInterval = d.rewardIntervalSeconds();
        medCalmMultiplier = d.calmMultiplier();
        medFocusedMultiplier = d.focusedMultiplier();
        medCenteredMultiplier = d.centeredMultiplier();
        medDeepMultiplier = d.deepMultiplier();
        medTranscendentMultiplier = d.transcendentMultiplier();
        medNpc = d.livingWorldNpcMeditation();
        medDamageInterrupts = d.damageInterrupts();
        medBreakthroughs = d.statBreakthroughEnabled();
        medBreakthroughChance = d.statBreakthroughChancePercent();
        medBreakthroughPoints = d.statBreakthroughPoints();
        medFormMastery = d.formMasteryEnabled();
    }

    private void resetMeditationVisualTabDefaults() {
        MeditationConfig.ClientSnapshot d = MeditationConfig.clientDefaults();
        medHud = d.focusHud();
        medSummary = d.sessionSummary();
        medNativeAnimation = d.nativeAnimation();
        medHudOffset = d.hudTopOffset();
        medAuraIntensity = d.auraIntensityPercent();
        medFocusSeal = d.focusSealEnabled();
        medSealIntensity = d.focusSealIntensityPercent();
        medSealSize = d.focusSealRadiusPercent();
        medNpcFx = d.npcMeditationEffects();
        medStageFx = d.stageTransitionEffects();
    }

    private void save() {
        commitNumericEditor();
        LivingWorldClientConfig.apply(clientSnapshot());
        MeditationConfig.applyClient(meditationClientSnapshot());
        if (canEdit) {
            savePending=true;
            LWNetwork.updateWorldSettings(worldSnapshot(), meditationSnapshot());
            setStatus(LWLang.string("screen.world_settings.saving_all"),2200L);
        } else {
            dirty = false;
            setStatus(LWLang.string("screen.world_settings.client_saved"),2800L);
        }
    }

    private boolean isTypeableRow(int row) {
        return switch (tab) {
            case 0 -> row == 0 || row == 1 || row == 3 || row == 4 || row == 5 || row == 10 || row == 11;
            case 1 -> row >= 1 && row <= 4;
            case 2 -> row == 0 || row == 1 || row == 2 || row == 6 || row == 7 || row == 8 || row == 10 || row == 11 || row == 12;
            case 3 -> row == 2 || row == 3 || (row >= 4 && row <= 8) || row == 12 || row == 13;
            case 4 -> row == 3 || row == 4 || row == 6 || row == 7;
            case 5 -> row == 0 || row == 4 || row == 5 || row == 6 || row == 9;
            // Advanced NPC: rows 6, 8 and 11 are boolean toggles.  Row 5 is
            // the NPC power multiplier and must remain a numeric slider.
            case 6 -> row != 6 && row != 8 && row != 11;
            default -> false;
        };
    }

    private String rawEditableValue(int row) {
        return switch (tab) {
            case 0 -> switch (row) {
                case 0 -> Integer.toString(nearbyFighterCap);
                case 1 -> Integer.toString(nearbyHostileCap);
                case 3 -> Integer.toString(livingPresenceTargetBase);
                case 4 -> Integer.toString(livingPresenceRadius);
                case 5 -> Integer.toString(factionResidentCap);
                case 10 -> Integer.toString(worldEventAlertRadius);
                case 11 -> Integer.toString(earthGuardianResponsePercent);
                default -> "";
            };
            case 1 -> switch (row) {
                case 1 -> Integer.toString(talkBaseGain);
                case 2 -> Integer.toString(talkRelationshipCap);
                case 3 -> Integer.toString(talkCooldownMinSeconds);
                case 4 -> Integer.toString(talkCooldownMaxSeconds);
                default -> "";
            };
            case 2 -> switch (row) {
                case 0 -> formatNumber(nameScale * 100.0D, 2);
                case 1 -> formatNumber(dialogueScale * 100.0D, 2);
                case 2 -> formatNumber(verticalOffset, 2);
                case 6 -> Integer.toString(nameplateDistance);
                case 7 -> Integer.toString(factionLabelDistance);
                case 8 -> Integer.toString(dialogueDistance);
                case 10 -> Integer.toString(speechChatRadius);
                case 11 -> formatNumber(hudScale * 100D, 2);
                case 12 -> formatNumber(factionQuestHudScale * 100D, 2);
                default -> "";
            };
            case 3 -> switch (row) {
                case 2 -> Integer.toString(medTpScale);
                case 3 -> Integer.toString(medRewardInterval);
                case 4 -> Integer.toString(medCalmMultiplier);
                case 5 -> Integer.toString(medFocusedMultiplier);
                case 6 -> Integer.toString(medCenteredMultiplier);
                case 7 -> Integer.toString(medDeepMultiplier);
                case 8 -> Integer.toString(medTranscendentMultiplier);
                case 12 -> formatNumber(medBreakthroughChance, 2);
                case 13 -> Integer.toString(medBreakthroughPoints);
                default -> "";
            };
            case 4 -> switch (row) {
                case 3 -> Integer.toString(medHudOffset);
                case 4 -> Integer.toString(medAuraIntensity);
                case 6 -> Integer.toString(medSealIntensity);
                case 7 -> Integer.toString(medSealSize);
                default -> "";
            };
            case 5 -> switch (row) {
                case 0 -> Integer.toString(npcChaosPercent);
                case 4 -> Integer.toString(npcStrengthPercent);
                case 5 -> Integer.toString(npcGrowthPercent);
                case 6 -> Integer.toString(npcChatFrequencyPercent);
                case 9 -> Integer.toString(wantedPressureDecayMinutes);
                default -> "";
            };
            case 6 -> advancedRawValue(row);
            default -> "";
        };
    }

    private static String formatNumber(double value, int decimals) {
        String format = decimals <= 0 ? "%.0f" : "%." + decimals + "f";
        String text = String.format(java.util.Locale.ROOT, format, value);
        if (text.indexOf('.') >= 0) {
            while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
            if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static boolean isDecimalNumericRow(int tab, int row) {
        return (tab == 2 && (row == 0 || row == 1 || row == 2)) || (tab == 3 && row == 12) || (tab == 6 && row >= 2);
    }

    private void beginNumericEditor(int row, int x, int y, int w, int h) {
        commitNumericEditor();
        clearWidgets();
        editingRow = row;
        editingTab = tab;
        numericEditor = new EditBox(font, x + 1, y + 1, Math.max(24, w - 2), Math.max(16, h - 2), Component.literal(rowName(row)));
        numericEditor.setMaxLength(isTextRow(tab, row) ? 512 : 14);
        final boolean decimal = isDecimalNumericRow(tab, row);
        final boolean negative = tab == 2 && row == 2;
        numericEditor.setFilter(value -> {
            if (isTextRow(tab, row)) return true;
            if (value.isEmpty()) return true;
            if (negative) return value.matches("-?[0-9]*\\.?[0-9]*");
            if (decimal) return value.matches("[0-9]*\\.?[0-9]*");
            return value.matches("[0-9]*");
        });
        numericEditor.setValue(rawEditableValue(row));
        numericEditor.setFocused(true);
        addRenderableWidget(numericEditor);
        setFocused(numericEditor);
    }

    private void commitNumericEditor() {
        if (numericEditor == null || editingRow < 0) return;
        String raw = numericEditor.getValue().trim();
        int row = editingRow;
        int sourceTab = editingTab;
        numericEditor = null;
        editingRow = -1;
        editingTab = -1;
        clearWidgets();
        if (raw.isEmpty() || "-".equals(raw) || ".".equals(raw) || "-.".equals(raw)) return;
        try {
            switch (sourceTab) {
                case 0 -> commitWorldNumber(row, raw);
                case 1 -> commitRelationshipNumber(row, raw);
                case 2 -> commitDisplayNumber(row, raw);
                case 3 -> commitMeditationNumber(row, raw);
                case 4 -> commitMeditationVisualNumber(row, raw);
                case 5 -> commitNpcBehaviorNumber(row, raw);
                case 6 -> commitAdvanced(row, raw);
                default -> { return; }
            }
            markDirty();
        } catch (NumberFormatException ignored) {
            setStatus(LWLang.string("screen.world_settings.invalid_number"), 1800L);
        }
    }

    private void commitWorldNumber(int row, String raw) {
        int value = Integer.parseInt(raw);
        switch (row) {
            case 0 -> { nearbyFighterCap = clamp(value, 0, 4096); nearbyHostileCap = Math.min(nearbyHostileCap, nearbyFighterCap); }
            case 1 -> nearbyHostileCap = clamp(value, 0, nearbyFighterCap);
            case 3 -> livingPresenceTargetBase = clamp(value, 0, 128);
            case 4 -> livingPresenceRadius = clamp(value, 96, 8192);
            case 5 -> factionResidentCap = clamp(value, 4, 512);
            case 10 -> worldEventAlertRadius = clamp(value, 128, 32768);
            case 11 -> earthGuardianResponsePercent = clamp(value, 0, 500);
            default -> throw new NumberFormatException("not numeric");
        }
    }

    private void commitRelationshipNumber(int row, String raw) {
        int value = Integer.parseInt(raw);
        switch (row) {
            case 1 -> talkBaseGain = clamp(value, 0, 50);
            case 2 -> talkRelationshipCap = clamp(value, 0, 100);
            case 3 -> {
                talkCooldownMinSeconds = clamp(value, 10, 86400);
                talkCooldownMaxSeconds = Math.max(talkCooldownMaxSeconds, talkCooldownMinSeconds);
            }
            case 4 -> talkCooldownMaxSeconds = clamp(value, Math.max(10, talkCooldownMinSeconds), 86400);
            default -> throw new NumberFormatException("not numeric");
        }
    }

    private void commitNpcBehaviorNumber(int row, String raw) {
        int value = Integer.parseInt(raw);
        switch (row) {
            case 0 -> npcChaosPercent = clamp(value, 0, 1000);
            case 4 -> npcStrengthPercent = clamp(value, 25, 1000);
            case 5 -> npcGrowthPercent = clamp(value, 0, 1000);
            case 6 -> npcChatFrequencyPercent = clamp(value, 0, 500);
            case 9 -> wantedPressureDecayMinutes = clamp(value, 0, 10080);
            default -> throw new NumberFormatException("not numeric");
        }
    }

    private void commitDisplayNumber(int row, String raw) {
        double value = Double.parseDouble(raw);
        switch (row) {
            case 0 -> nameScale = clamp(value / 100.0D, 0.65D, 4.0D);
            case 1 -> dialogueScale = clamp(value / 100.0D, 0.65D, 4.0D);
            case 2 -> verticalOffset = clamp(value, -0.25D, 8.0D);
            case 6 -> nameplateDistance = clamp(Integer.parseInt(raw), 8, 4096);
            case 7 -> factionLabelDistance = clamp(Integer.parseInt(raw), 6, 4096);
            case 8 -> dialogueDistance = clamp(Integer.parseInt(raw), 8, 4096);
            case 10 -> speechChatRadius = clamp(Integer.parseInt(raw), 8, 70);
            case 11 -> hudScale = clamp(value / 100D, .5D, 3D);
            case 12 -> factionQuestHudScale = clamp(value / 100D, .5D, 3D);
            default -> throw new NumberFormatException("not numeric");
        }
    }

    private void commitMeditationNumber(int row, String raw) {
        switch (row) {
            case 2 -> medTpScale = clamp(Integer.parseInt(raw), 0, 5000);
            case 3 -> medRewardInterval = clamp(Integer.parseInt(raw), 1, 120);
            case 4 -> medCalmMultiplier = clamp(Integer.parseInt(raw), 1, 100);
            case 5 -> medFocusedMultiplier = clamp(Integer.parseInt(raw), 1, 100);
            case 6 -> medCenteredMultiplier = clamp(Integer.parseInt(raw), 1, 100);
            case 7 -> medDeepMultiplier = clamp(Integer.parseInt(raw), 1, 100);
            case 8 -> medTranscendentMultiplier = clamp(Integer.parseInt(raw), 1, 100);
            case 12 -> medBreakthroughChance = clamp(Double.parseDouble(raw), 0.0D, 100.0D);
            case 13 -> medBreakthroughPoints = clamp(Integer.parseInt(raw), 1, 100);
            default -> throw new NumberFormatException("not numeric");
        }
    }

    private void commitMeditationVisualNumber(int row, String raw) {
        int value = Integer.parseInt(raw);
        switch (row) {
            case 3 -> medHudOffset = clamp(value, 0, 240);
            case 4 -> medAuraIntensity = clamp(value, 0, 250);
            case 6 -> medSealIntensity = clamp(value, 25, 200);
            case 7 -> medSealSize = clamp(value, 60, 160);
            default -> throw new NumberFormatException("not numeric");
        }
    }

    private static boolean isTextRow(int selectedTab,int row){return selectedTab==6&&(row==7||row==9||row==10||row==12);}
    private static java.util.List<String> csv(String text){
        if(text==null||text.isBlank())return java.util.List.of();
        return java.util.Arrays.stream(text.split(",")).map(String::trim).filter(s->!s.isBlank()).toList();
    }
    private String advancedName(int r){
        if(r<15)return LWLang.string("screen.world_settings.row.advanced." + r);
        int i=r-15;
        return LWLang.string("screen.world_settings.row.advanced.share",
                LWLang.string("archetype." + (i / 4)), LWLang.string("stat." + (i % 4)));
    }
    private String advancedValue(int r){
        if(r>=15)return pct(archetypeShares[r-15]);
        return switch(r){case 0->Integer.toString(maxRememberedDeadFighters);case 1->blocks(npcDespawnProtectionRadius);
            case 2->"x"+formatNumber(levelMultiplierPerSaga,2);case 3->pct(maxDefenseMitigation);case 4->"x"+formatNumber(bpVisualMultiplier,2);
            case 5->pct(npcPowerMultiplier);case 6->onOff(canMeditationProcSkillProgression);case 7->npcRaceBlacklist.isBlank()?LWLang.string("value.empty"):npcRaceBlacklist;
            case 8->onOff(treatRaceBlacklistAsWhitelist);case 9->canUseClothes;case 10->dimensionWhitelist;
            case 11->onOff(treatDimensionWhitelistAsBlacklist);case 12->companionDimensionBlacklist;case 13->Integer.toString(npcCombatLives);default->Integer.toString(racialSkillMinimumEra);};
    }
    private String advancedRawValue(int r){if(isTextRow(6,r))return r==7?npcRaceBlacklist:r==9?canUseClothes:r==10?dimensionWhitelist:companionDimensionBlacklist;
        return formatNumber(r == 5 ? npcPowerMultiplier * 100.0D : advancedNumericValue(r),4);}
    private double advancedNumericValue(int r){if(r>=15)return archetypeShares[r-15];return switch(r){case 0->maxRememberedDeadFighters;
        case 1->npcDespawnProtectionRadius;case 2->levelMultiplierPerSaga;case 3->maxDefenseMitigation;case 4->bpVisualMultiplier;case 5->npcPowerMultiplier;case 13->npcCombatLives;case 14->racialSkillMinimumEra;default->0D;};}
    private void changeAdvanced(int r,int d){if(r==6)canMeditationProcSkillProgression=d>0;else if(r==8)treatRaceBlacklistAsWhitelist=d>0;
        else if(r==11)treatDimensionWhitelistAsBlacklist=d>0;else if(!isTextRow(6,r))setAdvancedNumeric(r,advancedNumericValue(r)+d*(r>=15?.01D:r==3?.01D:r==2?.1D:r==5?.01D:1D));}
    private void setAdvancedNumeric(int r,double v){if(r>=15)archetypeShares[r-15]=clamp(v,0D,10D);else switch(r){case 0->maxRememberedDeadFighters=clamp((int)Math.round(v),0,4096);
        case 1->npcDespawnProtectionRadius=clamp((int)Math.round(v),96,4096);case 2->levelMultiplierPerSaga=clamp(v,.1D,1000D);
        case 3->maxDefenseMitigation=clamp(v,0D,.99D);case 4->bpVisualMultiplier=clamp(v,0D,1_000_000D);case 5->npcPowerMultiplier=clamp(v,0D,1_000_000D);case 13->npcCombatLives=clamp((int)Math.round(v),1,100);case 14->racialSkillMinimumEra=clamp((int)Math.round(v),0,10000);default->{}}}
    private void commitAdvanced(int r,String raw){if(r==7)npcRaceBlacklist=raw;else if(r==9)canUseClothes=raw;else if(r==10)dimensionWhitelist=raw;else if(r==12)companionDimensionBlacklist=raw;
        else setAdvancedNumeric(r,r == 5 ? Double.parseDouble(raw) / 100.0D : Double.parseDouble(raw));}

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (numericEditor != null) {
            if (keyCode == 257 || keyCode == 335) { commitNumericEditor(); return true; }
            if (keyCode == 256) { numericEditor = null; editingRow = -1; editingTab = -1; clearWidgets(); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void back() {
        commitNumericEditor();
        if (FactionRequestTrackerOverlay.hasActiveRequest()) {
            FactionRequestTrackerOverlay.resumeAfterWorldMenu();
            Minecraft.getInstance().setScreen(null);
            return;
        }
        LWNetwork.requestMenu("world",0);
    }
    private void setStatus(String s,long ms){status=s;statusUntil=Util.getMillis()+ms;}
    private void markDirty(){ dirty=true; status=""; statusUntil=0L; }
    private int settingsBodyBottom(){ return footerY - 30; }
    private String tabContextLine(){
        String key = switch(tab){case 0->"world";case 1->"relationships";case 2->"display";case 3->"meditation";case 4->"meditation_visuals";case 6->"advanced_npc";default->"npc_behaviour";};
        return LWLang.string("screen.world_settings.context." + key);
    }
    private String activityLabel(){return LWLang.string("value.activity." + switch(activityPreset){case 0->"off";case 1->"low";case 3->"high";default->"normal";});}
    private String kiModeLabel(){return LWLang.string("value.ki_mode." + switch(npcKiMode){case 0->"normal";case 3->"player_blocks";case 2->"off";default->"player_world";});}
    private int kiModeIndex(){for(int i=0;i<KI_MODE_ORDER.length;i++)if(KI_MODE_ORDER[i]==npcKiMode)return i;return 2;}
    private static String onOff(boolean v){return LWLang.string(v ? "value.on" : "value.off");}
    private static String blocks(int value){return LWLang.string("value.blocks", value);}
    private static String seconds(int value){return LWLang.string("value.seconds_short", value);}
    private static String pct(double v){return formatNumber(v*100.0D,2)+"%";}
    private static int step(int[] values,int current,int dir){int best=current;if(dir>0){for(int v:values)if(v>current){best=v;break;}}else{for(int v:values)if(v<current)best=v;}return best;}
    private static int clamp(int v,int a,int b){return Math.max(a,Math.min(b,v));}
    private static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
    private static boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
}
