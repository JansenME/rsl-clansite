package com.rsl.clansite.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsl.clansite.model.dto.ChampionEntryDTO;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HellHadesScraperServiceTest {@Mock
private ChampionsService championsService;

    @Mock
    private ChampionRepository championRepository;

    @Mock
    private Authentication authentication;

    private HellHadesScraperService scraperService;

    @BeforeEach
    void setUp() {
        scraperService = new HellHadesScraperService(championRepository) {
            @Override
            protected Document fetchDocument(String url) {
                String html = "<html><head><title>Acelin - Raid Shadow Legends</title>" +
                        "<meta name='description' content='Acelin is a Legendary Defense Champion.'/>" +
                        "<link rel='shortlink' href='https://hellhades.com/?p=45734' />" +
                        "</head><body><h1>Acelin The Stalwart</h1></body></html>";
                return Jsoup.parse(html);
            }

            @Override
            protected <T> T fetchJson(String url, TypeReference<T> typeReference) throws IOException {
                ObjectMapper mapper = new ObjectMapper();

                if (url.contains("ratings/45734")) {
                    String json = "[{\"id\":\"854\",\"heroid\":\"8686\",\"arena_rating\":\"9\"}]";
                    return mapper.readValue(json, typeReference);
                }

                if (url.contains("auras/8686")) {
                    String json = "[{\"strength\":\"19\",\"location\":\"All Battles\",\"type\":\"Speed\"}]";
                    return mapper.readValue(json, typeReference);
                }

                if (url.contains("forms/8680")) {
                    String json = "[" +
                            "{\"heroid\":\"8680\",\"health\":\"100\",\"attack\":\"50\",\"defense\":\"100\",\"speed\":\"90\",\"critrate\":\"0.10\",\"critdamage\":\"0.50\",\"resistance\":\"0\",\"accuracy\":\"0\"}," + // base form
                            "{\"heroid\":\"8686\",\"health\":\"114\",\"attack\":\"69\",\"defense\":\"142\",\"speed\":\"106\",\"critrate\":\"0.15\",\"critdamage\":\"0.63\",\"resistance\":\"30\",\"accuracy\":\"0\"}" + // Max form
                            "]";
                    return mapper.readValue(json, typeReference);
                }

                return (T) Collections.emptyList();
            }
        };
    }

    /*@Test
    void testScrapeSingleChampion_CompleteFlow() throws Exception {
        HellHadesScraperService.ScrapeContext context = new HellHadesScraperService.ScrapeContext("http://fake.url", "http://fake.img");

        when(championRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());

        scraperService.importChampions(Collections.singletonList(context), Faction.BANNER_LORDS, authentication);

        ArgumentCaptor<ChampionEntryDTO> captor = ArgumentCaptor.forClass(ChampionEntryDTO.class);
        verify(championsService).saveNewChampion(captor.capture(), eq(authentication), anyString());

        ChampionEntryDTO result = captor.getValue();

        assertEquals(18825, result.getHp());
        assertEquals(760, result.getAttack());
        assertEquals(1564, result.getDefense());
        assertEquals(106, result.getSpeed());
        assertEquals(63, result.getCriticalDamage());
    }

    @Test
    void testScrape_UncommonChampion_WithNoise_ShouldBeUncommon() throws Exception {
        HellHadesScraperService.ScrapeContext context = new HellHadesScraperService.ScrapeContext("http://fake.url/archer", "http://fake.url/img.png");

        scraperService = new HellHadesScraperService(championsService, championRepository) {
            @Override
            protected Document fetchDocument(String url) {
                String html = "<html><head><title>Archer - Raid Shadow Legends</title>" +
                        "<meta name='description' content='Archer is an Uncommon HP Champion from Banner Lords.'/>" +
                        "</head><body>" +
                        "<h1>Archer</h1>" +
                        "<div class='content'>This champion is rarely used in the Arena.</div>" +
                        "</body></html>";
                return Jsoup.parse(html);
            }

            @Override
            protected <T> T fetchJson(String url, TypeReference<T> typeReference) {
                return (T) Collections.emptyList();
            }
        };

        when(championRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());

        scraperService.importChampions(Collections.singletonList(context), Faction.BANNER_LORDS, authentication);

        ArgumentCaptor<ChampionEntryDTO> captor = ArgumentCaptor.forClass(ChampionEntryDTO.class);
        verify(championsService).saveNewChampion(captor.capture(), eq(authentication), anyString());

        ChampionEntryDTO result = captor.getValue();

        assertEquals("Archer", result.getName());
        assertEquals(Rarity.UNCOMMON, result.getRarity());
    }*/
}