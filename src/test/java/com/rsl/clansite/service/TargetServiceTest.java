package com.rsl.clansite.service;

import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetServiceTest {
    private TargetService targetService;

    @BeforeEach
    void setUp() {
        targetService = new TargetService();

        // 1. Create Mock Data
        // Structure: Map<FactionName, Map<RarityString, Count>>
        Map<String, Map<String, Integer>> mockTargets = new HashMap<>();

        // Data for Banner Lords
        Map<String, Integer> bannerLordsCounts = new HashMap<>();
        bannerLordsCounts.put("legendary", 5);
        bannerLordsCounts.put("epic", 10);
        bannerLordsCounts.put("rare", 20);
        mockTargets.put(Faction.BANNER_LORDS.getName(), bannerLordsCounts);

        // Data for High Elves (Empty/Partial)
        Map<String, Integer> highElvesCounts = new HashMap<>();
        highElvesCounts.put("legendary", 2);
        mockTargets.put(Faction.HIGH_ELVES.getName(), highElvesCounts);

        // 2. Inject Mock Data into the private 'targets' field
        ReflectionTestUtils.setField(targetService, "targets", mockTargets);
    }

    @Test
    @DisplayName("getTargetsForFaction - Existing Faction - Should return correct map")
    void getTargetsForFaction_Existing() {
        Map<String, Integer> result = targetService.getTargetsForFaction(Faction.BANNER_LORDS);

        assertEquals(3, result.size());
        assertEquals(5, result.get("legendary"));
        assertEquals(10, result.get("epic"));
    }

    @Test
    @DisplayName("getTargetsForFaction - Unknown Faction - Should return empty map")
    void getTargetsForFaction_Unknown() {
        // Barbarians was not added to our mock data
        Map<String, Integer> result = targetService.getTargetsForFaction(Faction.BARBARIANS);

        assertTrue(result.isEmpty(), "Should return empty map for unknown faction");
    }

    @Test
    @DisplayName("getTargetCount - Valid Faction & Rarity - Should return exact count")
    void getTargetCount_Valid() {
        int count = targetService.getTargetCount(Faction.BANNER_LORDS, Rarity.LEGENDARY);
        assertEquals(5, count);
    }

    @Test
    @DisplayName("getTargetCount - Unknown Rarity in Faction - Should return 0")
    void getTargetCount_UnknownRarity() {
        // Banner Lords has no 'common' in our mock data
        int count = targetService.getTargetCount(Faction.BANNER_LORDS, Rarity.COMMON);
        assertEquals(0, count);
    }

    @Test
    @DisplayName("getTargetCount - Unknown Faction - Should return 0")
    void getTargetCount_UnknownFaction() {
        int count = targetService.getTargetCount(Faction.BARBARIANS, Rarity.LEGENDARY);
        assertEquals(0, count);
    }

    @Test
    @DisplayName("getMyTotalForFaction - Should sum all rarities")
    void getMyTotalForFaction_ShouldSum() {
        // Banner Lords: 5 (Leg) + 10 (Epic) + 20 (Rare) = 35
        int total = targetService.getMyTotalForFaction(Faction.BANNER_LORDS);
        assertEquals(35, total);
    }

    @Test
    @DisplayName("getMyTotalForFaction - Unknown Faction - Should return 0")
    void getMyTotalForFaction_Unknown() {
        int total = targetService.getMyTotalForFaction(Faction.BARBARIANS);
        assertEquals(0, total);
    }
}