package com.dmzlivingworld.entity;

/**
 * DragonMineZ 2.1.3 main-path racial forms used by Living World fighters.
 * Values mirror the shipped previousConfigs race form files. Legendary/alternate
 * trees and Saiyan Oozaru are deliberately excluded from procedural progression.
 */
public record RacialFormProfile(
        FighterRace race,
        int skillLevel,
        String id,
        String displayName,
        double powerMultiplier,
        double speedMultiplier,
        double attackSpeedMultiplier,
        float scaleMultiplier,
        int auraColor,
        boolean lightning,
        String hairType,
        String hairColor,
        String eyeColor,
        String modelKey
) {
    private static final RacialFormProfile[] FORMS = {
            // Human superforms
            f(FighterRace.HUMAN,1,"buffed","Buffed",1.60,1.0,1.0,1.16f,0xFFFFFF,false,"base","","","buffed"),
            f(FighterRace.HUMAN,2,"fullpower","Full Power",2.10,1.0,1.0,1.00f,0xFFFFFF,false,"ssj","","",""),
            f(FighterRace.HUMAN,3,"overdrive","Overdrive",2.85,1.0,1.0,1.10f,0xFFFD99,true,"ssj2","","",""),
            f(FighterRace.HUMAN,4,"solaris","Solaris",3.60,1.0,1.0,1.00f,0xFFFFFF,false,"ssj2","","",""),

            // Saiyan normal super-form path. Skill level 7 has no new form; SSJ4 unlocks at 8.
            f(FighterRace.SAIYAN,1,"supersaiyan","Super Saiyan",1.50,1.0,1.0,0.9375f,0xFFD700,false,"ssj","#FFEDB3","#00FFFF",""),
            f(FighterRace.SAIYAN,2,"supersaiyangrade2","Super Saiyan Grade 2",1.75,0.9,1.0,1.00f,0xFFD700,false,"ssj","#FFEDB3","#00FFFF","buffed"),
            f(FighterRace.SAIYAN,3,"supersaiyangrade3","Super Saiyan Grade 3",2.75,0.7,0.75,1.20f,0xFFD700,false,"ssj","#FFEDB3","#00FFFF","buffed"),
            f(FighterRace.SAIYAN,4,"supersaiyanmastered","Mastered Super Saiyan",1.75,1.0,1.0,0.9375f,0xFFD700,false,"ssj","#FFE89E","#00FFFF",""),
            f(FighterRace.SAIYAN,5,"supersaiyan2","Super Saiyan 2",2.25,1.0,1.0,0.9375f,0xFFD700,true,"ssj2","#FFE89E","#00FFFF",""),
            f(FighterRace.SAIYAN,6,"supersaiyan3","Super Saiyan 3",3.00,1.0,1.0,0.9375f,0xFFD700,true,"ssj3","#FFE89E","#00FFFF",""),
            // DMZ's SSJ4 uses a player-specific custom model/race layer. We preserve its exact
            // unlock level/stat/aura identity but do not synthesize a tail or fake that model.
            f(FighterRace.SAIYAN,8,"supersaiyan4","Super Saiyan 4",3.75,1.0,1.0,1.20f,0xFFD633,true,"base","#83073F","#83073F","ssj4d"),

            // Namekian superforms
            f(FighterRace.NAMEKIAN,1,"giant","Giant",2.00,1.0,0.25,3.60f,0xFFFFFF,false,"base","","",""),
            f(FighterRace.NAMEKIAN,2,"fullpower","Full Power",2.85,1.0,1.0,1.00f,0xFFFFFF,false,"base","","",""),
            f(FighterRace.NAMEKIAN,3,"supernamekian","Super Namekian",3.75,1.0,1.0,1.05f,0x7FFF00,true,"base","","","namekian_buffed"),

            // Majin pureforms
            f(FighterRace.MAJIN,1,"kid","Kid",1.75,1.0,1.0,0.70f,0xFFFFFF,false,"base","","","majin_kid"),
            f(FighterRace.MAJIN,2,"evil","Evil",2.25,1.0,1.0,0.90f,0xFFFFFF,false,"base","#917979","#F52746","majin_evil"),
            f(FighterRace.MAJIN,3,"super","Super",3.00,1.0,1.0,1.00f,0xFFFFFF,false,"base","","","majin_super"),
            f(FighterRace.MAJIN,4,"ultra","Ultra",3.75,1.0,1.0,1.27f,0xFFFFFF,true,"base","","","majin_ultra"),

            // Frost Demon evolutionforms
            f(FighterRace.FROST_DEMON,1,"second","Second Form",1.65,1.0,1.0,1.30f,0xFFFFFF,false,"base","","","frostdemon_second"),
            f(FighterRace.FROST_DEMON,2,"third","Third Form",2.10,1.0,1.0,1.40f,0xFFFFFF,false,"base","","","frostdemon_third"),
            f(FighterRace.FROST_DEMON,3,"final","Final Form",2.60,1.0,1.0,1.00f,0xFFFFFF,false,"base","","",""),
            f(FighterRace.FROST_DEMON,4,"fullpower","Full Power",3.15,1.0,0.75,1.27f,0xFFFFFF,false,"base","","","frostdemon_fp"),
            f(FighterRace.FROST_DEMON,5,"fifth","Fifth Form",3.90,1.0,1.0,1.37f,0xFFFFFF,true,"base","","#D91E1E","frostdemon_fifth"),

            // Bio-Android evolution
            f(FighterRace.BIO_ANDROID,1,"semiperfect","Semi-Perfect",1.75,1.0,1.0,1.30f,0xFFFFFF,false,"base","","#0095FF","bioandroid_semi"),
            f(FighterRace.BIO_ANDROID,2,"perfect","Perfect",2.40,1.0,1.0,1.10f,0xFFFFFF,false,"base","","#F6A6FF","bioandroid_perfect"),
            f(FighterRace.BIO_ANDROID,3,"superperfect","Super Perfect",3.05,1.0,1.0,1.10f,0xFFFF69,true,"base","","#F6A6FF","bioandroid_perfect"),
            f(FighterRace.BIO_ANDROID,4,"ultraperfect","Ultra Perfect",3.90,0.6,0.55,1.30f,0xFFFF69,true,"base","","#F6A6FF","bioandroid_ultra")
    };

    private static RacialFormProfile f(FighterRace race, int level, String id, String name,
                                       double power, double speed, double attackSpeed, float scale, int aura, boolean lightning,
                                       String hairType, String hairColor, String eyeColor, String modelKey) {
        return new RacialFormProfile(race, level, id, name, power, speed, attackSpeed, scale, aura, lightning,
                hairType, hairColor, eyeColor, modelKey);
    }

    public static RacialFormProfile forSkill(FighterRace race, int skillLevel) {
        RacialFormProfile best = null;
        for (RacialFormProfile form : FORMS) {
            if (form.race == race && form.skillLevel <= skillLevel && (best == null || form.skillLevel > best.skillLevel)) best = form;
        }
        return best;
    }

    public static int nextUnlockLevel(FighterRace race, int current) {
        int next = Integer.MAX_VALUE;
        for (RacialFormProfile form : FORMS) if (form.race == race && form.skillLevel > current) next = Math.min(next, form.skillLevel);
        return next == Integer.MAX_VALUE ? current : next;
    }

    public static int maxSkillLevel(FighterRace race) {
        int max = 0;
        for (RacialFormProfile form : FORMS) if (form.race == race) max = Math.max(max, form.skillLevel);
        return max;
    }
}
