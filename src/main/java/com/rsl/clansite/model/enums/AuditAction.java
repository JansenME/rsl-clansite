package com.rsl.clansite.model.enums;

public enum AuditAction {
    // Clanmember Actions
    MEMBER_ADD("Added Clanmember"),
    MEMBER_UPDATE("Updated Clanmember"),
    MEMBER_REACTIVATE("Re-activate Clanmember"),
    MEMBER_DELETE("Deleted Clanmember"),

    // Champion Actions (Individual)
    CHAMPION_ADD("Added Champion"),
    CHAMPION_UPDATE("Updated Champion"),
    CHAMPION_DELETE("Deleted Champion"),

    // Global Data / Admin Tools
    CHAMPION_SCRAPE("HellHades Data Scrape"),
    TARGET_CONFIG_UPDATE("Target Configuration Updated"),

    // Siege Actions
    SIEGE_SLOT_UPDATE("Siege Slot Assignment"),
    SIEGE_CONDITION_TOGGLE("Siege Condition Changed"),
    SIEGE_SYSTEM_EVENT("Siege System Event"),

    // System Actions
    SYSTEM_BACKUP("System Backup");

    private final String description;

    AuditAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}