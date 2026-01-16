package com.rsl.clansite.model.enums;

public enum SiegeStatus {
    PREP("Preparation Phase"),
    MATCHMAKING("Matchmaking Phase"),
    BATTLE("Battle Phase"),
    FINISHED("Finished");

    private final String displayName;

    SiegeStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}