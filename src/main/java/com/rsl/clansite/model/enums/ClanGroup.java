package com.rsl.clansite.model.enums;

import lombok.Getter;

@Getter
public enum ClanGroup {
    T1("T1", "Fury of the Fallen"),
    T2("T2", "Raid Elite Newbz Own");

    private final String name;
    private final String displayName;

    ClanGroup(final String name, final String displayName) {
        this.name = name;
        this.displayName = displayName;
    }
}
