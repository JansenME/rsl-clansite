package com.rsl.clansite.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

@Configuration
public class RoleConfig {

    @Bean
    public RoleHierarchy roleHierarchy() {
        String hierarchy = """
                ROLE_OWNER > ROLE_ADMIN
                ROLE_ADMIN > ROLE_COORDINATOR
                ROLE_COORDINATOR > ROLE_MEMBER
                ROLE_MEMBER > ROLE_USER
                ROLE_USER > ROLE_GUEST
                """;

        return RoleHierarchyImpl.fromHierarchy(hierarchy);
    }
}