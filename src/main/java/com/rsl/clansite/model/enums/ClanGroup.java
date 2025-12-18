package com.rsl.clansite.model.enums;

public enum ClanGroup {
    T1("T1"),
    T2("T2");

    private final String name;

    ClanGroup(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
