package com.rsl.clansite.service;

import com.rsl.clansite.model.entity.FactionTargetEntity;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.repository.FactionTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TargetServiceTest {
    @Mock
    private FactionTargetRepository factionTargetRepository;

    @InjectMocks
    private TargetService targetService;

    @BeforeEach
    void setUp() {
        FactionTargetEntity bannerLords = new FactionTargetEntity(Faction.BANNER_LORDS);
        bannerLords.getRarityTargets().put(Rarity.LEGENDARY, 5);
        bannerLords.getRarityTargets().put(Rarity.EPIC, 10);
        bannerLords.getRarityTargets().put(Rarity.RARE, 20);

        FactionTargetEntity highElves = new FactionTargetEntity(Faction.HIGH_ELVES);
        highElves.getRarityTargets().put(Rarity.LEGENDARY, 2);

        when(factionTargetRepository.findByFaction(Faction.BANNER_LORDS))
                .thenReturn(Optional.of(bannerLords));

        when(factionTargetRepository.findByFaction(Faction.HIGH_ELVES))
                .thenReturn(Optional.of(highElves));

        when(factionTargetRepository.findByFaction(Faction.BARBARIANS))
                .thenReturn(Optional.empty());
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