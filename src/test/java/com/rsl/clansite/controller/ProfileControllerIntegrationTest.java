package com.rsl.clansite.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ProfileControllerIntegrationTest extends BaseControllerTest {
    @Test
    @DisplayName("GET /profile - Authenticated user should access profile (200 OK)")
    void viewProfile_Authorized_ShouldSucceed() throws Exception {
        mockMvc.perform(get("/profile")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"));

        verify(commonsService).fillModel(any(), any());
    }

    @Test
    @DisplayName("GET /profile - Guest should be redirected to Login (302)")
    void viewProfile_Guest_ShouldRedirect() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection());
    }
}