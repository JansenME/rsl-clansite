package com.rsl.clansite.model.enums;

public enum AuraStat {
    ALLY_HP("Ally HP"),
    ALLY_SPD("Ally SPD"),
    ALLY_ACC("Ally ACC"),
    ALLY_ATK("Ally ATK"),
    ALLY_RES("Ally RES"),
    ALLY_DEF("Ally DEF"),
    ALLY_CRATE("Ally C.RATE");

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
