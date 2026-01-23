package com.rsl.clansite.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rsl.clansite.model.BaseStats;
import com.rsl.clansite.model.dto.ScrapedChampion;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.enums.AuraLocation;
import com.rsl.clansite.model.enums.AuraStat;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import com.rsl.clansite.repository.ChampionRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class HellHadesScraperServiceTest {

    @Mock
    private ChampionRepository championRepository;
    @Mock
    private CommonsService commonsService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private Authentication authentication;

    private HellHadesScraperService scraperService;

    @BeforeEach
    void setUp() {
        // FIX: Use the Testable subclass that handles the "Alaric" JSON logic correctly
        scraperService = new TestableHellHadesScraperService(championRepository, commonsService, auditLogService);
    }

    @Test
    void testImportChampions_CompleteFlow() {
        // 1. Setup the "Scraped" Data
        ScrapedChampion scrapedData = new ScrapedChampion();
        scrapedData.setName("Test Champion");
        scrapedData.setUrl("http://fake.url");
        scrapedData.setImageUrl("http://fake.img");

        BaseStats stats = new BaseStats();
        stats.setHp(18825);
        stats.setAttack(760);
        stats.setDefense(1564);
        stats.setSpeed(106);
        stats.setCriticalDamage(63);
        scrapedData.setBaseStats(stats);

        // 2. Create the Context
        HellHadesScraperService.ScrapingContext context =
                new HellHadesScraperService.ScrapingContext("Test Champion", "http://fake.url", "http://fake.img", "520");
        context.setScrapedData(scrapedData);

        // 3. Mock dependencies
        when(championRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(commonsService.generateImageFilename(anyString())).thenReturn("test-champion.png");

        // 4. Execute
        scraperService.importChampions(Collections.singletonList(context), Faction.BANNER_LORDS, authentication);

        // 5. Verify
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChampionEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(championRepository).saveAll(captor.capture());

        ChampionEntity result = captor.getValue().get(0);
        assertEquals("Test Champion", result.getName());
        assertEquals("test-champion.png", result.getImagename());
        assertEquals(18825, result.getBaseStats().getHp());
    }

    @Test
    void testScrape_UncommonChampion_WithNoise_ShouldBeUncommon() throws Exception {
        // This test defines its own specific service behavior, so we overwrite the one from setUp()
        scraperService = new HellHadesScraperService(championRepository, commonsService, auditLogService) {
            @Override
            protected Document fetchDocument(String url) {
                String html = "<html><head><title>Archer - Fury of the Fallen</title>" +
                        "<meta name='description' content='Archer is an Uncommon HP Champion from Banner Lords.'/>" +
                        "</head><body>" +
                        "<h1>Archer</h1>" +
                        "<div class='content'>This champion is rarely used in the Arena.</div>" +
                        "</body></html>";
                return Jsoup.parse(html);
            }

            @Override
            protected <T> T fetchJson(String url, TypeReference<T> typeReference) {
                return null; // Return null to force HTML fallback logic
            }
        };

        // Use Reflection to access private method
        java.lang.reflect.Method method = HellHadesScraperService.class.getDeclaredMethod("scrapeSingleChampion", String.class);
        method.setAccessible(true);

        ScrapedChampion result = (ScrapedChampion) method.invoke(scraperService, "http://fake.url/archer");

        assertEquals("Archer", result.getName());
        assertEquals(Rarity.UNCOMMON, result.getRarity());
    }

    @Test
    @DisplayName("scanForChampions - New Champion - Should Construct Image URL from JSON ID")
    void scanForChampions_NewChampion() {
        when(championRepository.findByNameIgnoreCase("Alaric")).thenReturn(Optional.empty());

        List<HellHadesScraperService.ScrapingContext> results = scraperService.scanForChampions(Faction.BANNER_LORDS, false);

        assertEquals(2, results.size());
        HellHadesScraperService.ScrapingContext ctx = results.get(0);

        assertEquals("Alaric", ctx.getName());
        assertEquals("https://hellhades.com/wp-content/plugins/rsl-assets/assets/champbyIds/9999.png", ctx.getImageUrl());
    }

    @Test
    @DisplayName("scanForChampions - Existing Champion (No Force) - Should Skip")
    void scanForChampions_Existing_NoForce() {
        when(championRepository.findByNameIgnoreCase("Alaric")).thenReturn(Optional.of(new ChampionEntity()));
        when(championRepository.findByNameIgnoreCase("Acelin")).thenReturn(Optional.of(new ChampionEntity()));

        List<HellHadesScraperService.ScrapingContext> results = scraperService.scanForChampions(Faction.BANNER_LORDS, false);

        assertTrue(results.isEmpty(), "Should skip existing champion when forceRefresh is false");
    }

    @Test
    @DisplayName("scanForChampions - Existing Champion (Force Refresh) - Should Include")
    void scanForChampions_Existing_Force() {
        List<HellHadesScraperService.ScrapingContext> results = scraperService.scanForChampions(Faction.BANNER_LORDS, true);

        assertEquals(2, results.size());
        assertEquals("Alaric", results.get(0).getName());
    }

    @Test
    @DisplayName("Deep Integration: Scrape Single Champion with Full API Data")
    void testScrape_FullApiData() throws Exception {
        // Access private method to bypass discovery and test the parsing logic directly
        Method method = HellHadesScraperService.class.getDeclaredMethod("scrapeSingleChampion", String.class);
        method.setAccessible(true);

        // This URL triggers the specific JSON mocks in TestableHellHadesScraperService
        // ID 45734 -> Ratings -> HeroID 8686 -> Aura & Stats
        ScrapedChampion result = (ScrapedChampion) method.invoke(scraperService, "https://hellhades.com/champions/acelin-the-stalwart/");

        // 1. Verify Basic Info (From HTML)
        assertEquals("Acelin The Stalwart", result.getName());

        // 2. Verify Ratings API (9.0 rating / 2 = 4.5)
        assertEquals(4.5, result.getArenaScore());

        // 3. Verify Aura API (19% Speed in All Battles)
        assertNotNull(result.getAura());
        assertEquals(19, result.getAura().getAmount());
        assertEquals(AuraStat.ALLY_SPD, result.getAura().getStat());
        assertEquals(AuraLocation.ALL_BATTLES, result.getAura().getLocation());

        // 4. Verify Stats API & Math
        // Base Defense from JSON is "142"
        // Math: 142 * 11.014 = 1563.988 -> Round to 1564
        assertNotNull(result.getBaseStats());
        assertEquals(1564, result.getBaseStats().getDefense());

        // Verify Type (Role) from API
        // JSON says "role": "Defense"
        assertEquals(Type.DEFENSE, result.getType());

        // Verify Rarity from API (should overwrite HTML fallback)
        // JSON says "rarity": "Legendary"
        assertEquals(Rarity.LEGENDARY, result.getRarity());
    }

    @Test
    @DisplayName("getOnlineCounts - Should Tally Rarities Correctly")
    void testGetOnlineCounts() {
        // Banner Lords URL triggers the mock JSON list
        Map<Rarity, Integer> counts = scraperService.getOnlineCounts(Faction.BANNER_LORDS);

        // Mock returns 1 Legendary (Alaric - let's pretend he's Leggo for this test) and 1 Epic
        assertEquals(1, counts.get(Rarity.EPIC));
        assertEquals(1, counts.get(Rarity.LEGENDARY));
        assertEquals(0, counts.get(Rarity.RARE));
    }

    // --- TEST SUBCLASS TO OVERRIDE NETWORK CALLS ---
    static class TestableHellHadesScraperService extends HellHadesScraperService {

        public TestableHellHadesScraperService(ChampionRepository repo, CommonsService commons, AuditLogService auditLogService) {
            super(repo, commons, auditLogService);
        }

        @Override
        protected Document fetchDocument(String url) throws IOException {
            // FIX 1: Dynamic HTML based on the URL requested
            if (url.contains("acelin")) {
                String html = "<html><head><title>Acelin - Fury of the Fallen</title>" +
                        "<meta name='description' content='Acelin fallback description'/>" +
                        // This link ID (45734) triggers the Ratings JSON below
                        "<link rel='shortlink' href='https://hellhades.com/?p=45734' />" +
                        "</head><body><h1>Acelin The Stalwart</h1></body></html>";
                return Jsoup.parse(html);
            }

            // Default to Alaric for other tests
            return Jsoup.parse("<html><head><title>Alaric</title></head><body><h1>Alaric</h1></body></html>");
        }

        @Override
        @SuppressWarnings("unchecked")
        protected <T> T fetchJson(String url, TypeReference<T> typeReference) throws IOException {
            // FIX 2: Return Java Objects directly instead of parsing JSON Strings
            // This avoids Jackson visibility issues with the inner classes

            // 1. Ratings API (Acelin)
            if (url.contains("ratings/45734")) {
                HellHadesScraperService.HellHadesRatingJson r = new HellHadesScraperService.HellHadesRatingJson();
                r.setId("854");
                r.setHeroid("8686");
                r.setArena_rating("9");
                return (T) Collections.singletonList(r);
            }

            // 2. Aura API (Acelin)
            if (url.contains("auras/8686")) {
                HellHadesScraperService.HellHadesAuraJson a = new HellHadesScraperService.HellHadesAuraJson();
                a.setStrength("19");
                a.setLocation("All Battles");
                a.setType("Speed");
                return (T) Collections.singletonList(a);
            }

            // 3. Stats/Forms API (Acelin)
            if (url.contains("forms/8680")) { // 8680 is the base form ID
                HellHadesScraperService.HellHadesStatJson s = new HellHadesScraperService.HellHadesStatJson();
                s.setHeroid("8686"); // Max Ascension ID
                s.setRole("Defense");
                s.setRarity("Legendary");
                s.setHealth("114");
                s.setAttack("69");
                s.setDefense("142");
                s.setSpeed("106");
                s.setCritrate("0.15");
                s.setCritdamage("0.63");
                s.setResistance("30");
                s.setAccuracy("0");
                return (T) Collections.singletonList(s);
            }

            // 4. Faction Discovery / Online Counts
            if (url.contains("banner-lords")) {
                HellHadesScraperService.HellHadesChampionJson epic = new HellHadesScraperService.HellHadesChampionJson();
                epic.setId("9999");
                epic.setName("Alaric");
                epic.setRarity("Epic");

                HellHadesScraperService.HellHadesChampionJson leg = new HellHadesScraperService.HellHadesChampionJson();
                leg.setId("8888");
                leg.setName("Acelin");
                leg.setRarity("Legendary");

                // Return a list of 2 items
                List<HellHadesScraperService.HellHadesChampionJson> list = new java.util.ArrayList<>();
                list.add(epic);
                list.add(leg);
                return (T) list;
            }

            return (T) Collections.emptyList();
        }
    }
}