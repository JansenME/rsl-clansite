package com.rsl.clansite.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ProfileControllerIntegrationTest extends BaseControllerTest {
    private static final String QUICK_LINKS_HEADER = "Quick Links";
    private static final String LINK_ADD_CLANMEMBER = "href=\"/clanmembers/add\"";
    private static final String LINK_ADD_CHAMPION = "href=\"/champions/new\"";
    private static final String LINK_AUDIT_LOG = "href=\"/audit-log\"";

    @Test
    @DisplayName("GET /profile - OWNER should see Quick Links and ALL buttons")
    void viewProfile_AsOwner_ShouldSeeAll() throws Exception {
        mockMvc.perform(get("/profile")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andExpect(content().string(containsString(QUICK_LINKS_HEADER)))
                .andExpect(content().string(containsString(LINK_ADD_CLANMEMBER)))
                .andExpect(content().string(containsString(LINK_AUDIT_LOG)))
                .andExpect(content().string(containsString(LINK_ADD_CHAMPION)));
    }

    @Test
    @DisplayName("GET /profile - ADMIN should see Quick Links, Clanmember/Audit buttons, but NOT Champion button")
    void viewProfile_AsAdmin_ShouldSeeAdminLinksOnly() throws Exception {
        mockMvc.perform(get("/profile")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(QUICK_LINKS_HEADER)))
                .andExpect(content().string(containsString(LINK_ADD_CLANMEMBER)))
                .andExpect(content().string(containsString(LINK_AUDIT_LOG)))
                .andExpect(content().string(not(containsString(LINK_ADD_CHAMPION))));
    }

    @Test
    @DisplayName("GET /profile - COORDINATOR should see Quick Links header, but NO buttons")
    void viewProfile_AsCoordinator_ShouldSeeContainerButNoLinks() throws Exception {
        mockMvc.perform(get("/profile")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_COORDINATOR"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(QUICK_LINKS_HEADER)))
                .andExpect(content().string(not(containsString(LINK_ADD_CLANMEMBER))))
                .andExpect(content().string(not(containsString(LINK_AUDIT_LOG))))
                .andExpect(content().string(not(containsString(LINK_ADD_CHAMPION))));
    }

    @Test
    @DisplayName("GET /profile - MEMBER should NOT see Quick Links container at all")
    void viewProfile_AsMember_ShouldNotSeeQuickLinks() throws Exception {
        mockMvc.perform(get("/profile")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(QUICK_LINKS_HEADER))));

        verify(commonsService).fillModel(any(), any());
    }

    @Test
    @DisplayName("GET /profile - Guest should be redirected to Login (302)")
    void viewProfile_Guest_ShouldRedirect() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection());
    }
}