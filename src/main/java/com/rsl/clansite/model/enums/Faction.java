package com.rsl.clansite.model.enums;

import lombok.Getter;

@Getter
public enum Faction {
    BANNER_LORDS("Banner Lords", Alliance.TELERIAN_LEAGUE),
    HIGH_ELVES("High Elves", Alliance.TELERIAN_LEAGUE),
    THE_SACRED_ORDER("The Sacred Order", Alliance.TELERIAN_LEAGUE),
    BARBARIANS("Barbarians", Alliance.TELERIAN_LEAGUE),
    OGRYN_TRIBES("Ogryn Tribes", Alliance.GAELLEN_PACT),
    LIZARDMEN("Lizardmen", Alliance.GAELLEN_PACT),
    SKINWALKERS("Skinwalkers", Alliance.GAELLEN_PACT),
    ORCS("Orcs", Alliance.GAELLEN_PACT),
    DEMONSPAWN("Demonspawn", Alliance.THE_CORRUPTED),
    UNDEAD_HORDES("Undead Hordes", Alliance.THE_CORRUPTED),
    DARK_ELVES("Dark Elves", Alliance.THE_CORRUPTED),
    KNIGHTS_REVENANT("Knights Revenant", Alliance.THE_CORRUPTED),
    DWARVES("Dwarves", Alliance.NYRESAN_UNION),
    SHADOWKIN("Shadowkin", Alliance.NYRESAN_UNION),
    SYLVAN_WATCHERS("Sylvan Watchers", Alliance.NYRESAN_UNION);

    private final String name;
    private final Alliance alliance;

    Faction(final String name, final Alliance alliance) {
        this.name = name;
        this.alliance = alliance;
    }

    public static Faction getFactionByName(final String name) {
        for(Faction faction : Faction.values()) {
            if(faction.name.equalsIgnoreCase(name)) {
                return faction;
            }
        }

        return null;
    }
}
