package com.rsl.clansite.model.enums;

import lombok.Getter;

@Getter
public enum QuickLink {
    ADD_CLANMEMBER("Add Clanmember", "/clanmembers/add", "ROLE_ADMIN"),
    ADD_CHAMPION("Add Champion", "/champions/new", "ROLE_OWNER"),
    AUDIT_LOG("Audit Log", "/audit-log", "ROLE_ADMIN"),
    LOGIN_HISTORY("Login History", "/clanmembers/admin/login-history", "ROLE_ADMIN"),
    DATA_HEALTH("Discord Data Health", "/clanmembers/admin/data-health", "ROLE_ADMIN");

    private final String label;
    private final String url;
    private final String requiredRole;

    QuickLink(String label, String url, String requiredRole) {
        this.label = label;
        this.url = url;
        this.requiredRole = requiredRole;
    }
}
