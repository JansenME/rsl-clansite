package com.rsl.clansite.model.enums;

import lombok.Getter;

@Getter
public enum QuickLink {
    EDIT_ROSTER("Edit My Roster", "/champions?editingMemberId=", "ROLE_MEMBER"),
    SYNC_ROSTER("Sync My Roster", "/sync", "ROLE_MEMBER"),
    ADD_SIEGE_TEAM("Add Siege Team", "/teams/builder", "ROLE_MEMBER"),

    SIEGE_CONDITIONS("Siege Conditions", "/admin/siege-conditions", "ROLE_COORDINATOR"),

    ADD_CLANMEMBER("Add Clanmember", "/clanmembers/add", "ROLE_ADMIN"),
    LOGIN_HISTORY("Login History", "/clanmembers/admin/login-history", "ROLE_ADMIN"),
    NOTICES("Website Notices", "/admin/notices", "ROLE_ADMIN"),
    DATA_HEALTH("Discord Data Health", "/clanmembers/admin/data-health", "ROLE_ADMIN"),
    AUDIT_LOG("Audit Log", "/audit-log", "ROLE_ADMIN"),

    ADD_CHAMPION("Add Champion", "/champions/new", "ROLE_OWNER"),
    MANAGE_BACKUPS("Manage Backups", "/admin/backups", "ROLE_OWNER"),
    OPEN_KLOEPIEBOT("Open Kloepiebot", "/profile/connect-app", "ROLE_OWNER");

    private final String label;
    private final String url;
    private final String requiredRole;

    QuickLink(String label, String url, String requiredRole) {
        this.label = label;
        this.url = url;
        this.requiredRole = requiredRole;
    }

    public int getGroupOrder() {
        return switch (this.requiredRole) {
            case "ROLE_MEMBER" -> 1;
            case "ROLE_COORDINATOR" -> 2;
            case "ROLE_ADMIN" -> 3;
            case "ROLE_OWNER" -> 4;
            default -> 99;
        };
    }
}