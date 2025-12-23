package com.rsl.clansite.controller;

import com.rsl.clansite.exceptions.ChampionSaveException;
import com.rsl.clansite.model.dto.ChampionEntryDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ChampionsControllerIntegrationTest extends BaseControllerTest {
    @Test
    @DisplayName("GET /new - OWNER should access the form (200 OK)")
    void newChampionForm_AsOwner_ShouldSucceed() throws Exception {
        mockMvc.perform(get("/champions/new")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isOk())
                .andExpect(view().name("champion-entry"))
                .andExpect(model().attributeExists("newChampion"));
    }

    @Test
    @DisplayName("GET /new - ADMIN should be denied (403 Forbidden)")
    void newChampionForm_AsAdmin_ShouldFail() throws Exception {
        mockMvc.perform(get("/champions/new")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("GET /new - COORDINATOR should be denied (403 Forbidden)")
    void newChampionForm_AsCoordinator_ShouldFail() throws Exception {
        mockMvc.perform(get("/champions/new")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_COORDINATOR"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("GET /new - MEMBER should be denied (403 Forbidden)")
    void newChampionForm_AsMember_ShouldFail() throws Exception {
        mockMvc.perform(get("/champions/new")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("GET /new - GUEST (Unauthenticated) should be redirected to Login (302)")
    void newChampionForm_AsGuest_ShouldRedirectToLogin() throws Exception {
        mockMvc.perform(get("/champions/new"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("POST /save - OWNER should save and redirect to list (302 Found)")
    void saveChampion_AsOwner_ShouldSaveAndRedirect() throws Exception {
        mockMvc.perform(post("/champions/save")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER")))
                        .with(csrf())
                        .param("name", "TestChamp")
                        .param("hp", "100")
                        .param("percentageAura", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/champions"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(championsService).saveNewChampion(any(ChampionEntryDTO.class));
    }

    @Test
    @DisplayName("POST /save - ADMIN should be denied (403 Forbidden)")
    void saveChampion_AsAdmin_ShouldFail() throws Exception {
        mockMvc.perform(post("/champions/save")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf())
                        .param("name", "TestChamp")
                        .param("percentageAura", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("POST /save - COORDINATOR should be denied (403 Forbidden)")
    void saveChampion_AsCoordinator_ShouldFail() throws Exception {
        mockMvc.perform(post("/champions/save")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_COORDINATOR")))
                        .with(csrf())
                        .param("name", "TestChamp")
                        .param("percentageAura", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("POST /save - MEMBER should be denied (403 Forbidden)")
    void saveChampion_AsMember_ShouldFail() throws Exception {
        mockMvc.perform(post("/champions/save")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                        .with(csrf())
                        .param("name", "TestChamp")
                        .param("percentageAura", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("POST /save - GUEST should be redirected (302)")
    void saveChampion_AsGuest_ShouldRedirect() throws Exception {
        mockMvc.perform(post("/champions/save")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("POST /save - Service Exception should redirect back to form with error")
    void saveChampion_ServiceError_ShouldRedirectBack() throws Exception {
        doThrow(new ChampionSaveException("Name empty")).when(championsService).saveNewChampion(any());

        mockMvc.perform(post("/champions/save")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER")))
                        .with(csrf())
                        .param("name", "")
                        .param("percentageAura", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/champions/new"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("GET /saveChampsFromCsv - OWNER should succeed")
    void saveCsv_AsOwner_ShouldSucceed() throws Exception {
        mockMvc.perform(get("/champions/saveChampsFromCsv")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /saveChampsFromCsv - ADMIN should be denied")
    void saveCsv_AsAdmin_ShouldFail() throws Exception {
        mockMvc.perform(get("/champions/saveChampsFromCsv")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }
}