package com.rsl.clansite.model.enums;

public enum Affinity {
    FORCE("Force"),
    MAGIC("Magic"),
    SPIRIT("Spirit"),
    VOID("Void");

    private final String name;

    Affinity(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static Affinity getAffinityByName(final String name) {
        for(Affinity affinity : Affinity.values()) {
            if(affinity.name.equalsIgnoreCase(name)) {
                return affinity;
            }
        }

        return null;
    }
}
