package com.dmzlivingworld.world;

public enum EncounterType {
    DUEL("duel"),
    CLASH("clash"),
    MUGGING("mugging"),
    RESCUE("rescue"),
    AMBUSH("ambush"),
    BRAWL("brawl"),
    FRIEZA_SKIRMISH("frieza");

    private final String commandName;

    EncounterType(String commandName) {
        this.commandName = commandName;
    }

    public String commandName() { return commandName; }
}
