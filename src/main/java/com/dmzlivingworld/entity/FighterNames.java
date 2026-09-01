package com.dmzlivingworld.entity;

import net.minecraft.util.RandomSource;

/** Original short names with small race-flavoured pools; no canon character copies. */
public final class FighterNames {
    private static final String[] HUMAN_MALE = {
            "Renso", "Daiki", "Kado", "Toma", "Riku", "Boro", "Jin", "Kenta",
            "Sato", "Ramon", "Teo", "Miro", "Kiro", "Naro", "Daro", "Renji",
            "Taro", "Kobi", "Rado", "Seki", "Niko", "Haru", "Keno", "Beni",
            "Viktor", "Petar", "Ivo", "Kaloyan", "Marto", "Aren", "Jaro", "Todor",
            "Yori", "Kasen", "Deni", "Bram", "Oren", "Sami", "Vasko", "Ilian",
            "Akira", "Haruto", "Ren", "Sota", "Kaito", "Takumi", "Yuto", "Kenji", "Shun", "Daichi",
            "Hiro", "Ryo", "Naoki", "Itsuki", "Masato", "Ryota", "Hayato", "Kota",
            "Amir", "Zayd", "Karim", "Samir", "Tariq", "Nabil", "Faris", "Rami", "Omar", "Adil",
            "Hadi", "Malik", "Zain", "Yasin", "Khalil", "Jamal", "Bilal", "Hasan", "Mateo", "Luka", "Deyan", "Stoyan", "Boris", "Nikolai", "Emil", "Rafael", "Marco", "Leon", "Arin", "Jalen", "Kei", "Isamu", "Takeshi", "Jun", "Koji", "Minato", "Rayan", "Idris", "Elias", "Marek", "Timo", "Soren"};
    private static final String[] HUMAN_FEMALE = {
            "Nami", "Mika", "Rina", "Ami", "Yuna", "Sena", "Lina", "Mara",
            "Kira", "Tali", "Nera", "Sora", "Mina", "Rumi", "Kari", "Aira",
            "Nori", "Meli", "Hana", "Reni", "Tera", "Yori", "Kumi", "Sari",
            "Mila", "Elena", "Vera", "Raya", "Dara", "Teya", "Iva", "Neli",
            "Kaya", "Zara", "Lora", "Mira", "Asha", "Yara", "Sumi", "Tina",
            "Aiko", "Emi", "Yui", "Sakura", "Rin", "Mei", "Akari", "Nanami", "Hikari", "Kaori",
            "Ayame", "Nozomi", "Rei", "Mio", "Asuka", "Chihiro", "Keiko", "Miu",
            "Layla", "Noor", "Yasmin", "Salma", "Farah", "Amina", "Leila", "Mariam", "Dalia", "Rania",
            "Huda", "Nadia", "Samira", "Amal", "Reem", "Safiya", "Zahra", "Inaya", "Sofia", "Nina", "Teodora", "Kalina", "Boryana", "Anika", "Elira", "Selin", "Maya", "Alina", "Iris", "Leona", "Yuki", "Misaki", "Riko", "Nao", "Kanna", "Sayuri", "Aya", "Imani", "Noura", "Esra", "Lamia", "Soraya"};
    private static final String[] SAIYAN_MALE = {"Cabro", "Ruga", "Taroq", "Vesso", "Kress", "Zukal", "Bruss", "Korat", "Tubar", "Paret", "Celon", "Onio", "Rutab", "Chard", "Leeko", "Bross", "Peppar", "Kalette", "Turren", "Radish", "Parsn", "Kohl", "Celeri", "Beanor", "Turnip", "Sprout", "Fennel", "Kressa", "Bokar", "Yamto", "Ginger", "Tater", "Chikor", "Kabal", "Endiv", "Ruccor", "Leekon", "Beetor", "Karela", "Daikon", "Artich", "Kohlra", "Cresson", "Tatsoi", "Chicor", "Pumpko", "Squaro", "Gourdo", "Pepino", "Cassav", "Radic", "Arugon", "Okron", "Cucur", "Shallo", "Chivon"};
    private static final String[] SAIYAN_FEMALE = {"Cassa", "Roka", "Tressa", "Tomae", "Salla", "Leeka", "Peppa", "Yama", "Mizu", "Chira", "Rucola", "Cressa", "Parya", "Lentia", "Okra", "Sorrela", "Kalea", "Endiva", "Bokki", "Nappael", "Turna", "Celera", "Rutta", "Fenna", "Beanie", "Charda", "Raddi", "Ginga", "Yamra", "Kohla", "Leekael", "Karela", "Beeta", "Daika", "Parsa", "Articha", "Kohlri", "Cressia", "Tatsoya", "Chicora", "Pumpka", "Squara", "Gourda", "Pepina", "Cassava", "Radica", "Aruga", "Okrena", "Cucura", "Shalla", "Chiva"};
    private static final String[] NAMEKIAN = {"Neru", "Kargo", "Voro", "Caden", "Pira", "Tuno", "Garo", "Rema", "Loru", "Sargo", "Nali", "Temba", "Moru", "Dendei", "Koru", "Talem", "Vekru", "Palo", "Doru", "Senna", "Fluto", "Tromu", "Basso", "Cymba", "Ocaro", "Reedo", "Tabor", "Bellon", "Hornel", "Picol", "Druma", "Lyren", "Violo", "Cello", "Gonga", "Mando", "Tubel", "Fifel", "Chord", "Rondo", "Sonora", "Clarin", "Timpan", "Marim", "Conga", "Zither", "Sitaro", "Lutem", "Banjor", "Oboen", "Harpa", "Citren", "Tambor", "Rebec", "Dulci", "Vibra"};
    private static final String[] MAJIN_MALE = {"Mubo", "Bemi", "Poro", "Jubu", "Maji", "Bono", "Kubu", "Dumi", "Pabu", "Moro", "Boppo", "Gumi", "Zubu", "Fumi", "Pepo", "Balu", "Bubu", "Mallow", "Toffi", "Nouga", "Jelli", "Mochi", "Fuddo", "Taffi", "Candi", "Gello", "Puffo", "Truff", "Caram", "Syrup", "Bonbo", "Gummi", "Pralin", "Waffo", "Sorbe", "Custar", "Marzi", "Brittle", "Crembo", "Churro", "Gelato", "Mering", "Cocoa", "Bonbon", "Truffle", "Puddo", "Mousse", "Biscor"};
    private static final String[] MAJIN_FEMALE = {"Bibi", "Mimi", "Juna", "Pomi", "Lulu", "Bena", "Maja", "Riri", "Pina", "Sumi", "Fifi", "Guma", "Bela", "Pepa", "Nunu", "Momo", "Mallowa", "Toffia", "Nouga", "Jelli", "Mochia", "Taffia", "Candia", "Puffa", "Truffa", "Carama", "Syrupa", "Bonni", "Gumina", "Prali", "Fudda", "Bubella", "Pralina", "Waffa", "Sorbea", "Custara", "Marzia", "Brittla", "Crema", "Churra", "Gelata", "Meringa", "Cocoa", "Bonbona", "Truffla", "Pudda", "Moussa", "Bisca"};
    private static final String[] FROST = {"Cryon", "Vyre", "Sleet", "Arctis", "Nival", "Glace", "Kryos", "Vanta", "Rime", "Zeric", "Hailen", "Boreal", "Frozel", "Cirrus", "Tundra", "Iskar", "Mistral", "Shiver", "Brumal", "Gelid", "Hoarf", "Polar", "Nix", "Frazil", "Raska", "Nevus", "Glacien", "Hiber", "Rauke", "Chillan", "Perma", "Auster", "Snowel", "Icera", "Frysta", "Siroc", "Crysal", "Rimek", "Albedo", "Vortis", "Nivor", "Frosten", "Glacis", "Boreon", "Skaal", "Hailor", "Wintra", "Permaf", "Cirren", "Siver", "Aisval", "Gelon"};
    private static final String[] BIO = {"Cera", "Nex", "Viro", "Kell", "Syn", "Orin", "Mera", "Zeta", "Crix", "Iona", "Axon", "Vexa", "Nodal", "Kera", "Soma", "Tess", "Coda", "Myn", "Helix", "Cytor", "Nerva", "Kinet", "Mitos", "Vecto", "Lumen", "Cortex", "Membra", "Amino", "Chrom", "Ribo", "Nuclea", "Myelo", "Telo", "Sporo", "Genix", "Vesra", "Genome", "Cytix", "Axion", "Virel", "Nucleon", "Synap", "Protea", "Ciliar", "Metrin", "Karyon", "Splic", "Ribon", "Enzyma", "Vectora", "Chromel", "Mitera"};

    private FighterNames() {}

    public static String roll(RandomSource random, FighterRace race, boolean female) {
        String[] pool = switch (race) {
            case HUMAN -> female ? HUMAN_FEMALE : HUMAN_MALE;
            case SAIYAN -> female ? SAIYAN_FEMALE : SAIYAN_MALE;
            case NAMEKIAN -> NAMEKIAN;
            case MAJIN -> female ? MAJIN_FEMALE : MAJIN_MALE;
            case FROST_DEMON -> FROST;
            case BIO_ANDROID -> BIO;
        };
        return pool[random.nextInt(pool.length)];
    }

    /** Prefer a name not already visible in the local population without forcing global uniqueness. */
    public static String rollUnique(AmbientFighterEntity fighter, RandomSource random, FighterRace race, boolean female) {
        String candidate = roll(random, race, female);
        if (fighter == null || fighter.level().isClientSide) return candidate;
        for (int tries = 0; tries < 14; tries++) {
            final String test = candidate;
            boolean used = !fighter.level().getEntitiesOfClass(AmbientFighterEntity.class, fighter.getBoundingBox().inflate(128.0D),
                    other -> other != fighter && test.equalsIgnoreCase(other.getFighterName())).isEmpty();
            if (!used) return candidate;
            candidate = roll(random, race, female);
        }
        return candidate;
    }

    public static String roll(RandomSource random, boolean female) {
        return roll(random, FighterRace.HUMAN, female);
    }
}
