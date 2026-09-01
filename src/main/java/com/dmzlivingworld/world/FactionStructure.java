package com.dmzlivingworld.world;

/**
 * Social architecture layered over the generic role ladder. It is intentionally
 * presentation/behavioral depth, not an economy or territory-conquest system.
 */
public enum FactionStructure {
    SCHOOL(0, "School"),
    CREW(1, "Crew"),
    GANG(2, "Gang"),
    ORDER(3, "Order"),
    CULT(4, "Cult"),
    SYNDICATE(5, "Syndicate"),
    CLAN(6, "Clan"),
    GUARD(7, "Guard");

    private final int id;
    private final String displayName;

    FactionStructure(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public int id() { return id; }
    public String displayName() { return displayName; }

    public String title(FactionRole role) {
        return switch (this) {
            case SCHOOL -> switch (role) {
                case RECRUIT -> "Student"; case MEMBER -> "Disciple"; case ENFORCER -> "Senior Disciple";
                case LIEUTENANT -> "Instructor"; case LEADER -> "Grandmaster";
            };
            case CREW -> switch (role) {
                case RECRUIT -> "Rookie"; case MEMBER -> "Crew"; case ENFORCER -> "Heavy";
                case LIEUTENANT -> "First Mate"; case LEADER -> "Captain";
            };
            case GANG -> switch (role) {
                case RECRUIT -> "Runner"; case MEMBER -> "Member"; case ENFORCER -> "Enforcer";
                case LIEUTENANT -> "Lieutenant"; case LEADER -> "Boss";
            };
            case ORDER -> switch (role) {
                case RECRUIT -> "Initiate"; case MEMBER -> "Adept"; case ENFORCER -> "Keeper";
                case LIEUTENANT -> "High Adept"; case LEADER -> "Master";
            };
            case CULT -> switch (role) {
                case RECRUIT -> "Initiate"; case MEMBER -> "Disciple"; case ENFORCER -> "Zealot";
                case LIEUTENANT -> "Hand"; case LEADER -> "Prophet";
            };
            case SYNDICATE -> switch (role) {
                case RECRUIT -> "Associate"; case MEMBER -> "Soldier"; case ENFORCER -> "Enforcer";
                case LIEUTENANT -> "Underboss"; case LEADER -> "Boss";
            };
            case CLAN -> switch (role) {
                case RECRUIT -> "Youngblood"; case MEMBER -> "Kin"; case ENFORCER -> "Warrior";
                case LIEUTENANT -> "Elder"; case LEADER -> "Clan Head";
            };
            case GUARD -> switch (role) {
                case RECRUIT -> "Cadet"; case MEMBER -> "Guard"; case ENFORCER -> "Sentinel";
                case LIEUTENANT -> "Commander"; case LEADER -> "Warden";
            };
        };
    }

    public static FactionStructure byId(int id) {
        FactionStructure[] values = values();
        return values[Math.floorMod(id, values.length)];
    }

    public static FactionStructure forEthos(FactionEthos ethos, FactionRealm realm) {
        if (realm == FactionRealm.NAMEK) {
            return switch (ethos) {
                case MARTIAL_SCHOOL -> SCHOOL;
                case WANDERING_GUARD, NAMEK_WARDENS -> GUARD;
                case ROOT_CIRCLE -> CLAN;
                case POWER_CULT -> CULT;
                default -> ORDER;
            };
        }
        return switch (ethos) {
            case MARTIAL_SCHOOL -> SCHOOL;
            case WANDERING_GUARD -> GUARD;
            case MERCENARIES, CHALLENGERS, SEEKERS -> CREW;
            case STREET_GANG, RAIDERS -> GANG;
            case CRIME_FAMILY, SYNDICATE -> SYNDICATE;
            case POWER_CULT -> CULT;
            case ROOT_CIRCLE -> CLAN;
            default -> ORDER;
        };
    }
}
