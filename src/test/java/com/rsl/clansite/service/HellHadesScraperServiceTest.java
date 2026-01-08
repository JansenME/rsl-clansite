package com.rsl.clansite.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rsl.clansite.model.dto.ChampionEntryDTO;
import com.rsl.clansite.model.enums.AuraLocation;
import com.rsl.clansite.model.enums.AuraStat;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import com.rsl.clansite.repository.ChampionRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HellHadesScraperServiceTest {
    @Mock
    private ChampionsService championsService;

    @Mock
    private ChampionRepository championRepository;

    @Mock
    private Authentication authentication;

    private HellHadesScraperService scraperService;

    @BeforeEach
    void setUp() {
        // We create a "Spy" version of the service to override the network methods
        scraperService = new HellHadesScraperService(championsService, championRepository) {
            @Override
            protected Document fetchDocument(String url) throws IOException {
                // Return FAKE HTML simulating Acelin's page
                String html = "<html><head><title>Acelin the Stalwart - Raid Shadow Legends</title>" +
                        "<meta name='description' content='Acelin the Stalwart is a Legendary Defense Champion from Banner Lords faction.'/>" +
                        "<link rel='shortlink' href='https://hellhades.com/?p=45734' />" + // <--- Key Post ID
                        "</head><body><h1>Acelin The Stalwart</h1>" +
                        "<div class='content'>Some content...</div></body></html>";
                return Jsoup.parse(html);
            }

            @Override
            protected <T> T fetchJson(String url, TypeReference<T> typeReference) throws IOException {
                // Return FAKE JSON based on the URL called
                if (url.contains("ratings/45734")) {
                    // Simulating Ratings Response (Score 9, Hero ID 8686)
                    String json = "[{\"id\":\"854\",\"heroid\":\"8686\",\"arena_rating\":\"9\"}]";
                    return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, typeReference);
                }
                if (url.contains("auras/8686")) {
                    // Simulating Aura Response (19 Speed)
                    String json = "[{\"strength\":\"19\",\"location\":\"All Battles\",\"type\":\"Speed\"}]";
                    return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, typeReference);
                }
                return (T) Collections.emptyList();
            }
        };
    }

    @Test
    void testScrapeSingleChampion_Success() throws Exception {
        // GIVEN
        HellHadesScraperService.ScrapeContext context = new HellHadesScraperService.ScrapeContext("http://fake.url/acelin", "http://fake.url/img.png");
        Faction faction = Faction.BANNER_LORDS;

        // Ensure duplicate check passes (returns empty)
        when(championRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());

        // WHEN
        // We access the private method via the public 'importChampions' loop, or effectively test the logic via single scrape if visible.
        // Since scrapeSingleChampion is private, we will trigger it via the public importChampions method with a list of 1.
        scraperService.importChampions(Collections.singletonList(context), faction, authentication);

        // THEN
        ArgumentCaptor<ChampionEntryDTO> dtoCaptor = ArgumentCaptor.forClass(ChampionEntryDTO.class);

        // Verify saveNewChampion was called exactly once
        verify(championsService, times(1)).saveNewChampion(dtoCaptor.capture(), eq(authentication), anyString());

        ChampionEntryDTO result = dtoCaptor.getValue();

        // 1. Check Basic Info
        assertEquals("Acelin The Stalwart", result.getName());
        assertEquals(Faction.BANNER_LORDS, result.getFaction());
        assertEquals("acelin-the-stalwart.png", result.getImagename());

        // 2. Check Metadata Parsing (from Fake HTML)
        assertEquals(Rarity.LEGENDARY, result.getRarity());
        assertEquals(Type.DEFENSE, result.getType());

        // 3. Check API Logic (from Fake JSONs)
        // Rating: "9" -> 4.5
        assertEquals(4.5, result.getArenaScore());

        // Aura: 19 Speed All Battles
        assertTrue(result.isAuraExists());
        assertEquals(19, result.getAmount());
        assertEquals(AuraStat.ALLY_SPD, result.getStat());
        assertEquals(AuraLocation.ALL_BATTLES, result.getLocation());
        assertTrue(result.isPercentageAura()); // Speed is percentage
    }
}