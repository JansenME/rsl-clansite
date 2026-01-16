package com.rsl.clansite.model.enums;

import lombok.Getter;

@Getter
public enum SiegeStructureType {
    STRONGHOLD("Stronghold", 12, 1200, 1300),
    SHRINE("Mana Shrine", 6, 450, 550),
    MAGIC_TOWER("Magic Tower", 2, 275, 275),
    DEFENSE_TOWER("Defense Tower", 2, 150, 150),
    POST("Post", 1, 50, 50);

    private final String displayName;
    private final int defaultSlotsLevel1;
    private final int defaultDefensePoints;
    private final int defaultAttackPoints;

    SiegeStructureType(String displayName, int defaultSlotsLevel1, int defaultDefensePoints, int defaultAttackPoints) {
        this.displayName = displayName;
        this.defaultSlotsLevel1 = defaultSlotsLevel1;
        this.defaultDefensePoints = defaultDefensePoints;
        this.defaultAttackPoints = defaultAttackPoints;
    }
}