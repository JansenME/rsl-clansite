package com.rsl.clansite.model.enums;

import lombok.Getter;

@Getter
public enum MemberStatus {
    ACTIVE("Active"),
    INACTIVE("Inactive");

    private final String label;

    MemberStatus(String label) {
        this.label = label;
    }
}