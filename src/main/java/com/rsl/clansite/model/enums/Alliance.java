package com.rsl.clansite.model.enums;

public enum Alliance {
    TELERIAN_LEAGUE("Telerian League"),
    GAELLEN_PACT("Gaellen Pact"),
    THE_CORRUPTED("The Corrupted"),
    NYRESAN_UNION("Nyresan Union");

    private final String name;

    Alliance(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static Alliance getAllianceByName(final String name) {
        return switch (name) {
            case "Telerian League" -> Alliance.TELERIAN_LEAGUE;
            case "Gaellen Pact" -> Alliance.GAELLEN_PACT;
            case "The Corrupted" -> Alliance.THE_CORRUPTED;
            case "Nyresan Union" -> Alliance.NYRESAN_UNION;
            default -> null;
        };
    }
}
