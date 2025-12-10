package com.rsl.clansite.model.enums;

import lombok.Getter;

@Getter
public enum FilterType {
    RARITY("Rarity"),
    TYPE("Type"),
    AFFINITY("Affinity"),
    FACTION("Faction"),
    ALLIANCE("Alliance");

    private final String name;

    FilterType(final String name) {
        this.name = name;
    }

    public static FilterType getFilterTypeByName(final String name) {
        return switch (name) {
            case "Rarity" -> FilterType.RARITY;
            case "Type" -> FilterType.TYPE;
            case "Affinity" -> FilterType.AFFINITY;
            case "Faction" -> FilterType.FACTION;
            case "Alliance" -> FilterType.ALLIANCE;
            default -> null;
        };
    }
}
