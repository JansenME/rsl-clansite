package com.rsl.clansite.model.enums;

public enum AuraLocation {
    ARENA("Arena"),
    DUNGEONS("Dungeons"),
    FACTION_WARS("Faction Wars"),
    DOOM_TOWER("Doom Tower"),
    ALL_BATTLES("All Battles"),;

    private final String name;

    AuraLocation(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static AuraLocation getAuraLocationByName(final String name) {
        for(AuraLocation auraLocation : AuraLocation.values()) {
            if(auraLocation.name.equalsIgnoreCase(name)) {
                return auraLocation;
            }
        }

        return null;
    }
}
