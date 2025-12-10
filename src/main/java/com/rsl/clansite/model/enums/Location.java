package com.rsl.clansite.model.enums;

public enum Location {
    ALL_BATTLES("All Battles"),
    ARENA("Arena"),
    FACTION_WARS("Faction Wars");

    private final String name;

    Location(final String name) {
        this.name = name;
    }

    public static Location getLocationByName(final String name) {
        return switch (name) {
            case "All Battles" -> Location.ALL_BATTLES;
            case "Arena" -> Location.ARENA;
            case "Faction Wars" -> Location.FACTION_WARS;
            default -> null;
        };
    }
}
