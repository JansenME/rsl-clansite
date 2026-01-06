package com.rsl.clansite.controller;

import com.rsl.clansite.exceptions.ChampionSaveException;
import com.rsl.clansite.model.dto.ChampionEntryDTO;
import com.rsl.clansite.repository.ChampionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ChampionsControllerIntegrationTest extends BaseControllerTest {
    @MockitoBean
    private ChampionRepository championRepository;

    @Test
    @DisplayName("GET /champions - OWNER should see 'Add new Champion' button")
    void viewChampions_AsOwner_ShouldShowAddButton() throws Exception {
        mockMvc.perform(get("/champions")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isOk())
                .andExpect(view().name("champions"))
                .andExpect(content().string(containsString("href=\"/champions/new\"")));
    }

    @Test
    @DisplayName("GET /champions - ADMIN should NOT see 'Add new Champion' button")
    void viewChampions_AsAdmin_ShouldNotShowAddButton() throws Exception {
        mockMvc.perform(get("/champions")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("champions"))
                .andExpect(content().string(not(containsString("href=\"/champions/new\""))));
    }

    @Test
    @DisplayName("GET /champions - COORDINATOR should NOT see 'Add new Champion' button")
    void viewChampions_AsCoordinator_ShouldNotShowAddButton() throws Exception {
        mockMvc.perform(get("/champions")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_COORDINATOR"))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/champions/new\""))));
    }

    @Test
    @DisplayName("GET /champions - MEMBER should NOT see 'Add new Champion' button")
    void viewChampions_AsMember_ShouldNotShowAddButton() throws Exception {
        mockMvc.perform(get("/champions")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/champions/new\""))));
    }

    @Test
    @DisplayName("GET /champions - GUEST should access list but NOT see Add Button")
    void viewChampions_AsGuest_ShouldNotShowAddButton() throws Exception {
        mockMvc.perform(get("/champions"))
                .andExpect(status().isOk())
                .andExpect(view().name("champions"))
                .andExpect(content().string(not(containsString("href=\"/champions/new\""))));
    }

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
                        .param("rarity", "LEGENDARY")
                        .param("type", "ATTACK")
                        .param("affinity", "MAGIC")
                        .param("faction", "BANNER_LORDS")
                        .param("hp", "100")
                        .param("attack", "100")
                        .param("defense", "100")
                        .param("speed", "100")
                        .param("criticalRate", "15")
                        .param("criticalDamage", "50")
                        .param("resistance", "0")
                        .param("accuracy", "0")
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
    @DisplayName("POST /save - Service Exception should return form with error (No Redirect)")
    void saveChampion_ServiceError_ShouldReturnForm() throws Exception {
        doThrow(new ChampionSaveException("Database error")).when(championsService).saveNewChampion(any());

        mockMvc.perform(post("/champions/save")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER")))
                        .with(csrf())
                        .param("name", "Valid Name")
                        .param("rarity", "LEGENDARY")
                        .param("type", "ATTACK")
                        .param("affinity", "MAGIC")
                        .param("faction", "BANNER_LORDS")
                        .param("hp", "100")
                        .param("attack", "100")
                        .param("defense", "100")
                        .param("speed", "100")
                        .param("criticalRate", "15")
                        .param("criticalDamage", "50")
                        .param("resistance", "0")
                        .param("accuracy", "0")
                        .param("percentageAura", "false"))
                .andExpect(status().isOk())
                .andExpect(view().name("champion-entry"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(model().attribute("errorMessage", "Database error"));
    }

    @Test
    @DisplayName("GET /restore-backup - OWNER should succeed")
    void saveCsv_AsOwner_ShouldSucceed() throws Exception {
        mockMvc.perform(get("/champions/restore-backup")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /restore-backup - ADMIN should be denied")
    void saveCsv_AsAdmin_ShouldFail() throws Exception {
        mockMvc.perform(get("/champions/restore-backup")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("GET /restore-backup - COORDINATOR should be denied (403)")
    void saveCsv_AsCoordinator_ShouldFail() throws Exception {
        mockMvc.perform(get("/champions/restore-backup")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_COORDINATOR"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("GET /restore-backup - MEMBER should be denied (403)")
    void saveCsv_AsMember_ShouldFail() throws Exception {
        mockMvc.perform(get("/champions/restore-backup")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("GET /restore-backup - GUEST should be redirected to Login (302)")
    void saveCsv_AsGuest_ShouldRedirect() throws Exception {
        mockMvc.perform(get("/champions/restore-backup"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("POST /save - Should show errors for Duplicate Name, Missing Enum, and Negative Stat")
    void saveChampion_ValidationFailures_ShouldShowErrors() throws Exception {
        when(championRepository.existsByName("Existing Champion")).thenReturn(true);

        mockMvc.perform(post("/champions/save")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER")))
                        .with(csrf())
                        .param("name", "Existing Champion")
                        .param("hp", "-100")
                        .param("type", "ATTACK")
                        .param("affinity", "MAGIC")
                        .param("faction", "BANNER_LORDS"))
                .andExpect(status().isOk())
                .andExpect(view().name("champion-entry"))
                .andExpect(model().attributeHasFieldErrors("newChampion", "name"))
                .andExpect(model().attributeHasFieldErrors("newChampion", "hp"))
                .andExpect(model().attributeHasFieldErrors("newChampion", "rarity"));
    }
}