package com.rsl.clansite.model.enums;

public enum ClanRank {
    LEADER("Leader"),
    DEPUTY("Deputy"),
    LIEUTENANT("Lieutenant"),
    SOLDIER("Soldier");

    private final String name;

    ClanRank(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
