package com.rsl.clansite.model.enums;

public enum AuditAction {
    MEMBER_ADD("Added Clanmember"),
    MEMBER_DELETE("Deleted Clanmember"),
    MEMBER_UPDATE("Updated Clanmember"),

    CHAMPION_ADD("Added Champion"),
    CHAMPION_UPDATE("Updated Champion");

    private final String description;

    AuditAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
