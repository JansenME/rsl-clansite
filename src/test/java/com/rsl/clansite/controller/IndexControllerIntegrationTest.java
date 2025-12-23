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

class IndexControllerIntegrationTest extends BaseControllerTest {
    @Test
    @DisplayName("GET / - GUEST should access homepage (200 OK)")
    void index_AsGuest_ShouldSucceed() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));

        verify(commonsService).fillModel(any(), any());
    }

    @Test
    @DisplayName("GET /index - MEMBER should access homepage (200 OK)")
    void index_AsMember_ShouldSucceed() throws Exception {
        mockMvc.perform(get("/index")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    @DisplayName("GET /login - GUEST should see login page (200 OK)")
    void login_AsGuest_ShouldSucceed() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    @DisplayName("GET /login?error=xyz - GUEST should see error message")
    void login_WithError_ShouldShowMessage() throws Exception {
        mockMvc.perform(get("/login").param("error", "Bad credentials"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attribute("loginError", "Bad credentials"));
    }

    @Test
    @DisplayName("GET /login - AUTHENTICATED user should be redirected to Profile (302)")
    void login_AsAuthenticatedUser_ShouldRedirect() throws Exception {
        mockMvc.perform(get("/login")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));
    }
}