package com.rsl.clansite.model.enums;

import lombok.Getter;

@Getter
public enum SiegeStructureType {
    STRONGHOLD("Stronghold", 1200, 1300, 12, 16, 18, 22, 25, 16),
    SHRINE("Mana Shrine", 450, 550, 6, 7, 6, 6, 6, 6),
    MAGIC_TOWER("Magic Tower", 275, 275, 2, 3, 2, 2, 2, 2),
    DEFENSE_TOWER("Defense Tower", 150, 150, 2, 3, 4, 2, 2, 2),
    POST("Post", 50, 50, 1, 1, 1, 1, 1, 1);

    private final String displayName;
    private final int defaultDefensePoints;
    private final int defaultAttackPoints;

    // Hardcoded slot counts per level
    private final int slotsLevel1;
    private final int slotsLevel2;
    private final int slotsLevel3;
    private final int slotsLevel4;
    private final int slotsLevel5;
    private final int slotsLevel6;

    SiegeStructureType(String displayName,
                       int defaultDefensePoints,
                       int defaultAttackPoints,
                       int slotsLevel1,
                       int slotsLevel2,
                       int slotsLevel3,
                       int slotsLevel4,
                       int slotsLevel5,
                       int slotsLevel6) {
        this.displayName = displayName;
        this.defaultDefensePoints = defaultDefensePoints;
        this.defaultAttackPoints = defaultAttackPoints;
        this.slotsLevel1 = slotsLevel1;
        this.slotsLevel2 = slotsLevel2;
        this.slotsLevel3 = slotsLevel3;
        this.slotsLevel4 = slotsLevel4;
        this.slotsLevel5 = slotsLevel5;
        this.slotsLevel6 = slotsLevel6;
    }

    public int getSlotsForLevel(int level) {
        return switch (level) {
            case 1 -> slotsLevel1;
            case 2 -> slotsLevel2;
            case 3 -> slotsLevel3;
            case 4 -> slotsLevel4;
            case 5 -> slotsLevel5;
            case 6 -> slotsLevel6;
            default -> slotsLevel1;
        };
    }

    public int getMaxLevel() {
        return this == POST ? 1 : 6;
    }
}