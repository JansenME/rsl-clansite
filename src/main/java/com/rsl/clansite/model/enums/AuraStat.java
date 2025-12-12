package com.rsl.clansite.model.enums;

public enum AuraStat {
    ALLY_HP("Ally HP"),
    ALLY_SPD("Ally SPD");

    private final String name;

    AuraStat(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static AuraStat getAuraStatByName(final String name) {
        for(AuraStat auraStat : AuraStat.values()) {
            if(auraStat.name.equalsIgnoreCase(name)) {
                return auraStat;
            }
        }

        return null;
    }
}
