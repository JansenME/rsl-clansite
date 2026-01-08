package com.rsl.clansite.model.enums;

import lombok.Getter;

@Getter
public enum Faction {
    BANNER_LORDS("Banner Lords", Alliance.TELERIAN_LEAGUE, "https://hellhades.com/wp-json/hh-api/experimental/raid/index?config%5Bfaction%5D=banner-lords"),
    HIGH_ELVES("High Elves", Alliance.TELERIAN_LEAGUE, "https://hellhades.com/wp-json/hh-api/experimental/raid/index?config%5Bfaction%5D=high-elves"),
    THE_SACRED_ORDER("The Sacred Order", Alliance.TELERIAN_LEAGUE, "https://hellhades.com/wp-json/hh-api/experimental/raid/index?config%5Bfaction%5D=sacred-order"),
    BARBARIANS("Barbarians", Alliance.TELERIAN_LEAGUE, "https://hellhades.com/wp-json/hh-api/experimental/raid/index?config%5Bfaction%5D=barbarians"),
    OGRYN_TRIBES("Ogryn Tribes", Alliance.GAELLEN_PACT, "https://hellhades.com/wp-json/hh-api/experimental/raid/index?config%5Bfaction%5D=ogryn-tribes"),
    LIZARDMEN("Lizardmen", Alliance.GAELLEN_PACT, "https://hellhades.com/wp-json/hh-api/experimental/raid/index?config%5Bfaction%5D=lizardmen"),
    SKINWALKERS("Skinwalkers", Alliance.GAELLEN_PACT, "https://hellhades.com/wp-json/hh-api/experimental/raid/index?config%5Bfaction%5D=skinwalkers"),
    ORCS("Orcs", Alliance.GAELLEN_PACT, "https://hellhades.com/wp-json/hh-api/experimental/raid/index?config%5Bfaction%5D=orcs"),
    DEMONSPAWN("Demonspawn", Alliance.THE_CORRUPTED, "https://hellhades.com/wp-json/hh-api/experimental/raid/index?config%5Bfaction%5D=demonspawn"),
    UNDEAD_HORDES("Undead Hordes", Alliance.THE_CORRUPTED, "https://hellhades.com/wp-json/hh-api/experimental/raid/index?config%5Bfaction%5D=undead-hordes"),
    DARK_ELVES("Dark Elves", Alliance.THE_CORRUPTED, "https://hellhades.com/wp-json/hh-api/experimental/raid/index?config%5Bfaction%5D=dark-elves"),
    KNIGHTS_REVENANT("Knights Revenant", Alliance.THE_CORRUPTED, "https://hellhades.com/wp-json/hh-api/experimental/raid/index?config%5Bfaction%5D=knights-revenant"),
    DWARVES("Dwarves", Alliance.NYRESAN_UNION, "https://hellhades.com/wp-json/hh-api/experimental/raid/index?config%5Bfaction%5D=dwarves"),
    SHADOWKIN("Shadowkin", Alliance.NYRESAN_UNION, "https://hellhades.com/wp-json/hh-api/experimental/raid/index?config%5Bfaction%5D=shadowkin"),
    SYLVAN_WATCHERS("Sylvan Watchers", Alliance.NYRESAN_UNION, "https://hellhades.com/wp-json/hh-api/experimental/raid/index?config%5Bfaction%5D=sylvan-watchers");

    private final String name;
    private final Alliance alliance;
    private final String hellHadesUrl;

    Faction(final String name, final Alliance alliance, final String hellHadesUrl) {
        this.name = name;
        this.alliance = alliance;
        this.hellHadesUrl = hellHadesUrl;
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
