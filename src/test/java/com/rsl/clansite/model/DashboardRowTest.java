package com.rsl.clansite.model;

import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
class DashboardRowTest {
    @Test
    @DisplayName("isComplete - When DB matches Target - Returns True")
    void isComplete_ReturnsTrue_WhenMatch() {
        DashboardRow row = new DashboardRow(
                Faction.BANNER_LORDS,
                Collections.emptyMap(),
                Map.of(Rarity.LEGENDARY, 10),
                Collections.emptyMap(),
                10
        );

        assertTrue(row.isComplete());
    }

    @Test
    @DisplayName("isUpdateAvailable - When Online > Database - Returns True")
    void isUpdateAvailable_ReturnsTrue_WhenOnlineHigher() {
        DashboardRow row = new DashboardRow(
                Faction.BANNER_LORDS,
                Collections.emptyMap(),
                Map.of(Rarity.LEGENDARY, 5),
                Map.of(Rarity.LEGENDARY, 6),
                10
        );

        assertTrue(row.isUpdateAvailable());
    }

    @Test
    @DisplayName("Red Cross Scenario - Incomplete AND Online <= Database")
    void testRedCrossScenario() {
        DashboardRow row = new DashboardRow(
                Faction.BANNER_LORDS,
                Collections.emptyMap(),
                Map.of(Rarity.LEGENDARY, 5),
                Map.of(Rarity.LEGENDARY, 5),
                10
        );

        assertFalse(row.isComplete(), "Should be incomplete");
        assertFalse(row.isUpdateAvailable(), "No update should be available");
    }
}