package com.rsl.clansite.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Slf4j
@Service
public class SecurityService {

    private final RoleHierarchy roleHierarchy;

    public SecurityService(RoleHierarchy roleHierarchy) {
        this.roleHierarchy = roleHierarchy;
    }

    public boolean isOwner(Authentication authentication) {
        return hasRole(authentication, "ROLE_OWNER");
    }

    public boolean isAdmin(Authentication authentication) {
        return hasRole(authentication, "ROLE_ADMIN");
    }

    public boolean isCoordinator(Authentication authentication) {
        return hasRole(authentication, "ROLE_COORDINATOR");
    }

    public Collection<? extends GrantedAuthority> getReachableAuthorities(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return java.util.Collections.emptyList();
        }
        return roleHierarchy.getReachableGrantedAuthorities(authentication.getAuthorities());
    }

    public boolean hasRole(Authentication authentication, String roleName) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Collection<? extends GrantedAuthority> reachableAuthorities =
                roleHierarchy.getReachableGrantedAuthorities(authentication.getAuthorities());

        return reachableAuthorities.contains(new SimpleGrantedAuthority(roleName));
    }
}