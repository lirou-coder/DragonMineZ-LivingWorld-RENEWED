package com.dmzlivingworld.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-only readability controls. Safe to change without affecting world simulation. */
public final class LivingWorldClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue NAMEPLATE_SCALE;
    public static final ForgeConfigSpec.DoubleValue DIALOGUE_SCALE;
    public static final ForgeConfigSpec.DoubleValue NAMEPLATE_VERTICAL_OFFSET;
    public static final ForgeConfigSpec.BooleanValue SHOW_DISPOSITION_ICON;
    public static final ForgeConfigSpec.BooleanValue SHOW_FACTION_LABEL;
    public static final ForgeConfigSpec.BooleanValue SHOW_DIALOGUE;
    public static final ForgeConfigSpec.IntValue NAMEPLATE_DISTANCE;
    public static final ForgeConfigSpec.IntValue FACTION_LABEL_DISTANCE;
    public static final ForgeConfigSpec.IntValue DIALOGUE_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue SPEECH_TO_CHAT;
    public static final ForgeConfigSpec.IntValue SPEECH_CHAT_RADIUS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment(
                "Dragon Mine Z: Living World — client readability settings.",
                "These affect only this player's presentation and can also be changed from L -> Settings -> Display.")
                .push("readability");

        NAMEPLATE_SCALE = builder.comment("Scale of Living World fighter names/faction labels. 1.0 = base size.")
                .defineInRange("nameplateScale", 1.18D, 0.65D, 4.0D);
        DIALOGUE_SCALE = builder.comment("Scale of short dialogue drawn above Living World fighters.")
                .defineInRange("dialogueScale", 1.12D, 0.65D, 4.0D);
        NAMEPLATE_VERTICAL_OFFSET = builder.comment("Extra blocks above the fighter before drawing the nameplate. Useful with KISense/other bars.")
                .defineInRange("nameplateVerticalOffset", 0.34D, -0.25D, 8.0D);
        SHOW_DISPOSITION_ICON = builder.comment("Show the player-specific Trusted/Friendly/Neutral/Wary/Hostile badge beside names.")
                .define("showDispositionIcon", true);
        SHOW_FACTION_LABEL = builder.comment("Show faction text beneath a Living World fighter's name.")
                .define("showFactionLabel", true);
        SHOW_DIALOGUE = builder.comment("Show short in-world Living World dialogue above fighters.")
                .define("showDialogue", true);
        NAMEPLATE_DISTANCE = builder.comment("Maximum distance in blocks for fighter names. Wanted fighters and faction leaders retain at least 64 blocks of visibility.")
                .defineInRange("nameplateDistance", 48, 8, 4096);
        FACTION_LABEL_DISTANCE = builder.comment("Maximum distance in blocks for the faction label.")
                .defineInRange("factionLabelDistance", 30, 6, 4096);
        DIALOGUE_DISTANCE = builder.comment("Maximum distance in blocks for floating dialogue.")
                .defineInRange("dialogueDistance", 42, 8, 4096);
        SPEECH_TO_CHAT = builder.comment("Also mirror nearby Living World NPC speech into normal chat. Floating dialogue remains available independently.")
                .define("speechToChat", false);
        SPEECH_CHAT_RADIUS = builder.comment("Maximum distance in blocks for NPC speech mirrored into chat.")
                .defineInRange("speechChatRadius", 42, 8, 4096);

        builder.pop();
        SPEC = builder.build();
    }

    private LivingWorldClientConfig() {}

    public static float nameplateScale() { return NAMEPLATE_SCALE.get().floatValue(); }
    public static float dialogueScale() { return DIALOGUE_SCALE.get().floatValue(); }
    public static double verticalOffset() { return NAMEPLATE_VERTICAL_OFFSET.get(); }
    public static boolean showDispositionIcon() { return SHOW_DISPOSITION_ICON.get(); }
    public static boolean showFactionLabel() { return SHOW_FACTION_LABEL.get(); }
    public static boolean showDialogue() { return SHOW_DIALOGUE.get(); }
    public static int nameplateDistance() { return NAMEPLATE_DISTANCE.get(); }
    public static int factionLabelDistance() { return FACTION_LABEL_DISTANCE.get(); }
    public static int dialogueDistance() { return DIALOGUE_DISTANCE.get(); }
    public static boolean speechToChat() { return SPEECH_TO_CHAT.get(); }
    public static int speechChatRadius() { return Math.min(70, SPEECH_CHAT_RADIUS.get()); }

    public static Snapshot snapshot() {
        return new Snapshot(NAMEPLATE_SCALE.get(), DIALOGUE_SCALE.get(), NAMEPLATE_VERTICAL_OFFSET.get(),
                SHOW_DISPOSITION_ICON.get(), SHOW_FACTION_LABEL.get(), SHOW_DIALOGUE.get(),
                NAMEPLATE_DISTANCE.get(), FACTION_LABEL_DISTANCE.get(), DIALOGUE_DISTANCE.get(), SPEECH_TO_CHAT.get(), speechChatRadius());
    }

    public static void apply(Snapshot v) {
        if (v == null) return;
        NAMEPLATE_SCALE.set(clamp(v.nameplateScale(), 0.65D, 4.0D));
        DIALOGUE_SCALE.set(clamp(v.dialogueScale(), 0.65D, 4.0D));
        NAMEPLATE_VERTICAL_OFFSET.set(clamp(v.verticalOffset(), -0.25D, 8.0D));
        SHOW_DISPOSITION_ICON.set(v.showDispositionIcon());
        SHOW_FACTION_LABEL.set(v.showFactionLabel());
        SHOW_DIALOGUE.set(v.showDialogue());
        NAMEPLATE_DISTANCE.set(Math.max(8, Math.min(4096, v.nameplateDistance())));
        FACTION_LABEL_DISTANCE.set(Math.max(6, Math.min(4096, v.factionLabelDistance())));
        DIALOGUE_DISTANCE.set(Math.max(8, Math.min(4096, v.dialogueDistance())));
        SPEECH_TO_CHAT.set(v.speechToChat());
        SPEECH_CHAT_RADIUS.set(Math.max(8, Math.min(70, v.speechChatRadius())));
        NAMEPLATE_SCALE.save();
    }

    public static Snapshot defaults() { return new Snapshot(1.18D, 1.12D, 0.34D, true, true, true, 48, 30, 42, false, 42); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    public record Snapshot(double nameplateScale, double dialogueScale, double verticalOffset,
                           boolean showDispositionIcon, boolean showFactionLabel, boolean showDialogue,
                           int nameplateDistance, int factionLabelDistance, int dialogueDistance,
                           boolean speechToChat, int speechChatRadius) {}
}
