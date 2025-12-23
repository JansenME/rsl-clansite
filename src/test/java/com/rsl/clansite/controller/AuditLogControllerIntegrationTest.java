package com.rsl.clansite.controller;

import com.rsl.clansite.security.CustomAuthenticationFailureHandler;
import com.rsl.clansite.security.CustomOAuth2UserService;
import com.rsl.clansite.security.SecurityConfig;
import com.rsl.clansite.service.AuditLogService;
import com.rsl.clansite.service.CommonsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AuditLogController.class)
@Import(SecurityConfig.class)
class AuditLogControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private CommonsService commonsService;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private CustomAuthenticationFailureHandler customAuthenticationFailureHandler;

    @TestConfiguration
    static class TestConfig {
        @Bean
        MongoOperations mongoOperations() {
            MongoOperations mongoOps = mock(MongoOperations.class);
            IndexOperations indexOps = mock(IndexOperations.class);
            when(mongoOps.indexOps(anyString())).thenReturn(indexOps);
            return mongoOps;
        }
    }

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
        // FIXED: Owner > Admin, so this must SUCCEED, not fail.
        mockMvc.perform(get("/audit-log")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isOk())
                .andExpect(view().name("audit-log"))
                .andExpect(model().attributeExists("logs"));
    }

    @Test
    @DisplayName("GET /audit-log - COORDINATOR should be denied (Redirect to Error)")
    void viewAuditLog_AsCoordinator_ShouldFail() throws Exception {
        // Coordinator < Admin, so this must FAIL.
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