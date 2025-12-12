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
        for(FilterType filterType : FilterType.values()) {
            if(filterType.name.equalsIgnoreCase(name)) {
                return filterType;
            }
        }

        return null;
    }
}
