package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.nbt.CompoundTag;

/** Small persistent slice of ordinary life. It affects flavor/social dialogue, never combat stats. */
public enum FighterHobby {
    COOKING("Cooking", "trying new recipes"),
    STARGAZING("Stargazing", "watching the night sky"),
    FISHING("Fishing", "finding quiet fishing spots"),
    MUSIC("Music", "collecting songs and rhythms"),
    MECHANICS("Mechanics", "taking machines apart and putting them back together"),
    MAPMAKING("Mapmaking", "mapping places worth remembering"),
    GARDENING("Gardening", "keeping stubborn plants alive"),
    TEA("Tea", "experimenting with tea"),
    ROCK_COLLECTING("Rock collecting", "collecting unusual stones"),
    CLOUD_WATCHING("Cloud watching", "doing absolutely nothing under a good sky"),
    MARTIAL_NOTES("Training notes", "writing down little training discoveries"),
    CARD_GAMES("Card games", "getting far too competitive over card games"),
    CAMPING("Camping", "finding peaceful places to camp"),
    FASHION("Clothes", "putting together outfits that actually look right"),
    BULGARIAN_FOLKLORE("Bulgarian folklore", "reading about Bulgarian folklore and mountain legends");

    private static final String KEY = "HobbyId";
    private final String label;
    private final String activity;

    FighterHobby(String label, String activity) {
        this.label = label;
        this.activity = activity;
    }

    public String label() { return label; }
    public String activity() { return activity; }

    public static FighterHobby of(AmbientFighterEntity fighter) {
        if (fighter == null) return TRAINING_FALLBACK;
        CompoundTag legacy = fighter.getLegacyData();
        if (legacy.contains(KEY)) return byId(legacy.getInt(KEY));
        java.util.UUID identity = fighter.getMemoryRecordId() != null ? fighter.getMemoryRecordId() : fighter.getUUID();
        long mixed = identity.getMostSignificantBits() ^ Long.rotateLeft(identity.getLeastSignificantBits(), 23);
        int id;
        // A rare, stable Bulgaria easter egg rather than every second fighter shouting it.
        if (Math.floorMod(mixed, 97L) == 0L) id = BULGARIAN_FOLKLORE.ordinal();
        else id = Math.floorMod((int)(mixed ^ (mixed >>> 32)), values().length - 1);
        if (!fighter.level().isClientSide) legacy.putInt(KEY, id);
        return byId(id);
    }

    public static FighterHobby byId(int id) {
        FighterHobby[] all = values();
        return all[Math.floorMod(id, all.length)];
    }

    private static final FighterHobby TRAINING_FALLBACK = MARTIAL_NOTES;
}
