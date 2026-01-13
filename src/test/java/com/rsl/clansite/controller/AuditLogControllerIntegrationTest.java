package com.rsl.clansite.controller;

import com.rsl.clansite.model.entity.AuditLogEntity;
import com.rsl.clansite.model.enums.AuditAction;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
        String adminId = "admin-view";

        // FIX: Mock authorities for Filter
        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(get("/audit-log")
                        .with(oauth2User("ROLE_ADMIN", adminId)))
                .andExpect(status().isOk())
                .andExpect(view().name("audit-log"))
                .andExpect(model().attributeExists("logs"))
                .andExpect(content().string(not(containsString("<th>Delete</th>"))));

        verify(auditLogService).searchLogs(any(), any(), any(), any(), any());
        verify(commonsService).fillModel(any(), any());
    }

    @Test
    @DisplayName("GET /audit-log - OWNER should access logs AND see Delete Column")
    void viewAuditLog_AsOwner_ShouldSucceed_WithDelete() throws Exception {
        String ownerId = "owner-view";

        AuditLogEntity log = new AuditLogEntity(ObjectId.get(), LocalDateTime.now(), "user", "User", AuditAction.MEMBER_ADD, "Target", "Details");
        when(auditLogService.searchLogs(any(), any(), any(), any(), any())).thenReturn(List.of(log));

        // FIX: Mock authorities for Filter
        when(clanmemberService.getFreshAuthorities(eq(ownerId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_OWNER"))));

        mockMvc.perform(get("/audit-log")
                        .with(oauth2User("ROLE_OWNER", ownerId)))
                .andExpect(status().isOk())
                .andExpect(view().name("audit-log"))
                .andExpect(model().attributeExists("logs"))
                .andExpect(content().string(containsString("<th style=\"text-align: center;\">Delete</th>")))
                .andExpect(content().string(containsString("/delete")));
    }

    @Test
    @DisplayName("GET /audit-log - COORDINATOR should be denied (Redirect to Error)")
    void viewAuditLog_AsCoordinator_ShouldFail() throws Exception {
        String coordId = "coord-fail";

        // FIX: User exists -> 403
        when(clanmemberService.getFreshAuthorities(eq(coordId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_COORDINATOR"))));

        mockMvc.perform(get("/audit-log")
                        .with(oauth2User("ROLE_COORDINATOR", coordId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("GET /audit-log - MEMBER should be denied (Redirect to Error)")
    void viewAuditLog_AsMember_ShouldFail() throws Exception {
        String memberId = "member-fail";

        // FIX: User exists -> 403
        when(clanmemberService.getFreshAuthorities(eq(memberId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        mockMvc.perform(get("/audit-log")
                        .with(oauth2User("ROLE_MEMBER", memberId)))
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
        String ownerId = "owner-del";
        String logId = "123456789012345678901234";

        when(clanmemberService.getFreshAuthorities(eq(ownerId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_OWNER"))));

        mockMvc.perform(post("/audit-log/" + logId + "/delete")
                        .with(oauth2User("ROLE_OWNER", ownerId))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/audit-log"));

        verify(auditLogService).deleteLogEntry(logId);
    }

    @Test
    @DisplayName("POST /delete - ADMIN should be denied (403 Forbidden)")
    void deleteLog_AsAdmin_ShouldFail() throws Exception {
        String adminId = "admin-del-fail";
        String logId = "123456789012345678901234";

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(post("/audit-log/" + logId + "/delete")
                        .with(oauth2User("ROLE_ADMIN", adminId))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("POST /delete - MEMBER should be denied (403 Forbidden)")
    void deleteLog_AsMember_ShouldFail() throws Exception {
        String memberId = "member-del-fail";
        String logId = "123456789012345678901234";

        when(clanmemberService.getFreshAuthorities(eq(memberId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        mockMvc.perform(post("/audit-log/" + logId + "/delete")
                        .with(oauth2User("ROLE_MEMBER", memberId))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("GET /audit-log?params - Should map URL parameters to Service method")
    void viewAuditLog_WithSearchParameters_ShouldPassToService() throws Exception {
        String adminId = "admin-search";

        String startDateStr = "2025-12-01";
        String endDateStr = "2025-12-31";
        String actor = "Martijn";
        String target = "BadGuy";
        String action = "MEMBER_DELETE";

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(get("/audit-log")
                        .with(oauth2User("ROLE_ADMIN", adminId))
                        .param("startDate", startDateStr)
                        .param("endDate", endDateStr)
                        .param("actor", actor)
                        .param("action", action)
                        .param("target", target))
                .andExpect(status().isOk());

        verify(auditLogService).searchLogs(
                eq(java.time.LocalDate.parse(startDateStr)),
                eq(java.time.LocalDate.parse(endDateStr)),
                eq(actor),
                eq(AuditAction.MEMBER_DELETE),
                eq(target)
        );
    }

    @Test
    @DisplayName("GET /audit-log - Should show Warning if results exceed 100")
    void viewAuditLog_WithManyResults_ShouldShowLimitWarning() throws Exception {
        String adminId = "admin-limit";

        List<AuditLogEntity> largeList = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            largeList.add(new AuditLogEntity(ObjectId.get(), LocalDateTime.now(), "user", "User", AuditAction.MEMBER_ADD, "Target", "Details"));
        }

        when(auditLogService.searchLogs(any(), any(), any(), any(), any())).thenReturn(largeList);

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(get("/audit-log")
                        .with(oauth2User("ROLE_ADMIN", adminId)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("limitWarning", notNullValue()))
                .andExpect(content().string(containsString("Showing the first 100 results only")));
    }

    @Test
    @DisplayName("GET /audit-log - Should not show Warning if results are less than 100")
    void viewAuditLog_WithFewResults_ShouldNotShowLimitWarning() throws Exception {
        String adminId = "admin-no-limit";

        List<AuditLogEntity> largeList = new ArrayList<>();
        for (int i = 0; i < 99; i++) {
            largeList.add(new AuditLogEntity(ObjectId.get(), LocalDateTime.now(), "user", "User", AuditAction.MEMBER_ADD, "Target", "Details"));
        }

        when(auditLogService.searchLogs(any(), any(), any(), any(), any())).thenReturn(largeList);

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(get("/audit-log")
                        .with(oauth2User("ROLE_ADMIN", adminId)))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("limitWarning"));
    }

    @Test
    @DisplayName("Audit Log View - Should Truncate Long Details and Show Tooltip")
    void viewAuditLog_LongDetails_ShouldTruncate() throws Exception {
        String adminId = "admin-trunc";

        String longDetails = "This is a very long message that exceeds the limit of 35";

        String expectedTruncated = "This is a very long message that...";

        AuditLogEntity logEntry = new AuditLogEntity(
                null,
                LocalDateTime.now(),
                "123",
                "AdminUser",
                AuditAction.CHAMPION_UPDATE,
                "Kael",
                longDetails
        );

        when(auditLogService.searchLogs(any(), any(), any(), any(), any())).thenReturn(List.of(logEntry));

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(get("/audit-log")
                        .with(oauth2User("ROLE_ADMIN", adminId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("title=\"" + longDetails + "\"")))
                .andExpect(content().string(containsString(">" + expectedTruncated)));
    }

    @Test
    @DisplayName("Audit Log View - Should Display Short Details Normally")
    void viewAuditLog_ShortDetails_ShouldNotTruncate() throws Exception {
        String adminId = "admin-short";

        String shortDetails = "Short message";

        AuditLogEntity logEntry = new AuditLogEntity(
                null,
                LocalDateTime.now(),
                "123",
                "AdminUser",
                AuditAction.CHAMPION_UPDATE,
                "Kael",
                shortDetails
        );

        when(auditLogService.searchLogs(any(), any(), any(), any(), any())).thenReturn(List.of(logEntry));

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(get("/audit-log")
                        .with(oauth2User("ROLE_ADMIN", adminId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">" + shortDetails + "<")));
    }
}