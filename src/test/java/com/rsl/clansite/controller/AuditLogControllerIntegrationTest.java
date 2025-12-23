package com.rsl.clansite.controller;

import com.rsl.clansite.model.entity.AuditLogEntity;
import com.rsl.clansite.model.enums.AuditAction;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class AuditLogControllerIntegrationTest extends BaseControllerTest {
    @Test
    @DisplayName("GET /audit-log - ADMIN should access logs but NOT see Delete Column")
    void viewAuditLog_AsAdmin_ShouldSucceed_NoDelete() throws Exception {
        mockMvc.perform(get("/audit-log")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("audit-log"))
                .andExpect(model().attributeExists("logs"))
                .andExpect(content().string(not(containsString("<th>Delete</th>"))));

        verify(auditLogService).getAllLogs();
        verify(commonsService).fillModel(any(), any());
    }

    @Test
    @DisplayName("GET /audit-log - OWNER should access logs AND see Delete Column")
    void viewAuditLog_AsOwner_ShouldSucceed_WithDelete() throws Exception {
        AuditLogEntity log = new AuditLogEntity(ObjectId.get(), LocalDateTime.now(), "user", "User", AuditAction.MEMBER_ADD, "Target", "Details");
        when(auditLogService.getAllLogs()).thenReturn(List.of(log));

        mockMvc.perform(get("/audit-log")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isOk())
                .andExpect(view().name("audit-log"))
                .andExpect(model().attributeExists("logs"))
                .andExpect(content().string(containsString("<th style=\"text-align: center;\">Delete</th>")))
                .andExpect(content().string(containsString("/delete")));
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

    @Test
    @DisplayName("POST /delete - OWNER should delete log entry (302 Redirect)")
    void deleteLog_AsOwner_ShouldSucceed() throws Exception {
        String logId = "123456789012345678901234";

        mockMvc.perform(post("/audit-log/" + logId + "/delete")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER")))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/audit-log"));

        verify(auditLogService).deleteLogEntry(logId);
    }

    @Test
    @DisplayName("POST /delete - ADMIN should be denied (403 Forbidden)")
    void deleteLog_AsAdmin_ShouldFail() throws Exception {
        String logId = "123456789012345678901234";

        mockMvc.perform(post("/audit-log/" + logId + "/delete")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("POST /delete - MEMBER should be denied (403 Forbidden)")
    void deleteLog_AsMember_ShouldFail() throws Exception {
        String logId = "123456789012345678901234";

        mockMvc.perform(post("/audit-log/" + logId + "/delete")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }
}