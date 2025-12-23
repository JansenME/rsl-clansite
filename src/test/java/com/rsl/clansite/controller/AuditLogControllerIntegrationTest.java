package com.rsl.clansite.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class AuditLogControllerIntegrationTest extends BaseControllerTest {
    @Test
    @DisplayName("GET /audit-log - ADMIN should access log (200 OK)")
    void viewAuditLog_AsAdmin_ShouldSucceed() throws Exception {
        mockMvc.perform(get("/audit-log")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("audit-log"))
                .andExpect(model().attributeExists("logs"));

        verify(auditLogService).getAllLogs();
        verify(commonsService).fillModel(any(), any());
    }

    @Test
    @DisplayName("GET /audit-log - OWNER should access log (200 OK) - Inherits ADMIN rights")
    void viewAuditLog_AsOwner_ShouldSucceed() throws Exception {
        mockMvc.perform(get("/audit-log")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isOk())
                .andExpect(view().name("audit-log"))
                .andExpect(model().attributeExists("logs"));
    }

    @Test
    @DisplayName("GET /audit-log - COORDINATOR should be denied (Redirect to Error)")
    void viewAuditLog_AsCoordinator_ShouldFail() throws Exception {
        mockMvc.perform(get("/audit-log")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_COORDINATOR"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("GET /audit-log - MEMBER should be denied (Redirect to Error)")
    void viewAuditLog_AsMember_ShouldFail() throws Exception {
        mockMvc.perform(get("/audit-log")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("GET /audit-log - GUEST should be redirected to Login (302)")
    void viewAuditLog_AsGuest_ShouldRedirect() throws Exception {
        mockMvc.perform(get("/audit-log"))
                .andExpect(status().is3xxRedirection());
    }
}