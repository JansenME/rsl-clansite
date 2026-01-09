package com.rsl.clansite.controller;

import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.enums.Alliance;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.service.HellHadesScraperService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ScraperControllerIntegrationTest extends BaseControllerTest {

    @Test
    @DisplayName("Dashboard - Should prepare AllianceGroups correctly")
    void showDashboard_ShouldPrepareRows() throws Exception {
        ChampionEntity existingChamp = new ChampionEntity();
        existingChamp.setFaction(Faction.BANNER_LORDS);
        existingChamp.setRarity(Rarity.LEGENDARY);
        when(championRepository.findAll()).thenReturn(List.of(existingChamp));

        when(targetService.getMyTotalForFaction(Faction.BANNER_LORDS)).thenReturn(2);
        when(targetService.getTargetCount(Faction.BANNER_LORDS, Rarity.LEGENDARY)).thenReturn(2);

        when(scraperService.getOnlineCounts(Faction.BANNER_LORDS))
                .thenReturn(Map.of(Rarity.LEGENDARY, 2));

        mockMvc.perform(get("/admin/scraper")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isOk())
                .andExpect(view().name("scraper-dashboard"))

                .andExpect(model().attributeExists("allianceGroups"))

                .andExpect(model().attribute("allianceGroups", hasSize(4)))

                .andExpect(model().attribute("allianceGroups", hasItem(
                        allOf(
                                hasProperty("alliance", is(Alliance.TELERIAN_LEAGUE)),
                                hasProperty("rows", hasItem(
                                        allOf(
                                                hasProperty("faction", is(Faction.BANNER_LORDS)),
                                                hasProperty("myTotal", is(2))
                                        )
                                ))
                        )
                )));
    }

    @Test
    @DisplayName("Action - Import Execute - Should call service and redirect")
    void executeScrape_ShouldImportAndRedirect() throws Exception {
        // 1. Fix Class Name and Constructor (Name, URL, Image)
        HellHadesScraperService.ScrapingContext context =
                new HellHadesScraperService.ScrapingContext("Test Champion", "http://test-url.com", "http://test-img.png", "520");

        // 2. Fix Method Name and Arguments (Faction, forceRefresh=false)
        when(scraperService.scanForChampions(Faction.BANNER_LORDS, false))
                .thenReturn(Collections.singletonList(context));

        mockMvc.perform(post("/admin/scraper/faction/Banner-Lords/execute")
                        .with(csrf())
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/scraper")) // Default redirect when no Referer
                .andExpect(flash().attribute("message", containsString("Successfully imported")));

        // 3. Verify the import call uses the correct list type
        verify(scraperService).importChampions(anyList(), eq(Faction.BANNER_LORDS), any());
    }

    @Test
    @DisplayName("Security - Access Denied for Non-Owners")
    void showDashboard_WhenNotOwner_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/admin/scraper")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("Security - Dashboard Access Denied for MEMBER")
    void showDashboard_WhenMember_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/admin/scraper")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("Security - Import Execution Denied for MEMBER")
    void executeScrape_WhenMember_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/admin/scraper/faction/Banner-Lords/execute")
                        .with(csrf())
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));

        verify(scraperService, never()).importChampions(anyList(), any(), any());
    }
}