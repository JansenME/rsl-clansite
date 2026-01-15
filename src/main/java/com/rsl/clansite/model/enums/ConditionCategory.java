package com.rsl.clansite.model.enums;

import lombok.Getter;

@Getter
public enum ConditionCategory {
    RARITY("Rarity", Rarity.class),
    TYPE("Type", Type.class),
    AFFINITY("Affinity", Affinity.class),
    FACTION("Faction", Faction.class),
    ALLIANCE("Alliance", Alliance.class);

    private final String displayName;
    private final Class<? extends Enum<?>> enumClass;

    ConditionCategory(String displayName, Class<? extends Enum<?>> enumClass) {
        this.displayName = displayName;
        this.enumClass = enumClass;
    }
}