package com.rsl.clansite.service;

import com.rsl.clansite.exceptions.ChampionSaveException;
import com.rsl.clansite.model.Aura;
import com.rsl.clansite.model.Champion;
import com.rsl.clansite.model.dto.ChampionEntryDTO;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.repository.ChampionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChampionsServiceTest {
    @Mock
    private ChampionRepository championRepository;

    @Mock
    private Resource championsBackupFile;

    @InjectMocks
    private ChampionsService championsService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(championsService, "championsBackupFile", championsBackupFile);
    }

    @Test
    @DisplayName("getAllChampions should return sorted list of champions")
    void getAllChampions_ShouldReturnSortedList() {
        ChampionEntity c1 = new ChampionEntity();
        c1.setName("Z-Champ");

        ChampionEntity c2 = new ChampionEntity();
        c2.setName("A-Champ");

        when(championRepository.findAll()).thenReturn(List.of(c1, c2));

        List<Champion> result = championsService.getAllChampions();

        assertEquals(2, result.size());
        assertEquals("A-Champ", result.get(0).getName());
        assertEquals("Z-Champ", result.get(1).getName());
    }

    @Test
    @DisplayName("saveNewChampion should throw exception if name is empty")
    void saveNewChampion_ShouldThrow_WhenNameEmpty() {
        ChampionEntryDTO dto = new ChampionEntryDTO(true);
        dto.setName("");

        assertThrows(ChampionSaveException.class, () -> championsService.saveNewChampion(dto));
    }

    @Test
    @DisplayName("saveNewChampion (Happy Path) should save ONLY to DB (No CSV)")
    void saveNewChampion_ShouldSaveToDb() throws ChampionSaveException {
        ChampionEntryDTO dto = new ChampionEntryDTO(false);
        dto.setName("TestChamp");
        dto.setHp(100);

        championsService.saveNewChampion(dto);

        ArgumentCaptor<ChampionEntity> entityCaptor = ArgumentCaptor.forClass(ChampionEntity.class);
        verify(championRepository).save(entityCaptor.capture());

        ChampionEntity savedEntity = entityCaptor.getValue();
        assertEquals("TestChamp", savedEntity.getName());
        assertEquals(100, savedEntity.getBaseStats().getHp());
    }

    @Test
    @DisplayName("saveNewChampion should wrap DB exceptions in ChampionSaveException")
    void saveNewChampion_ShouldWrapExceptions() {
        ChampionEntryDTO dto = new ChampionEntryDTO(false);
        dto.setName("ErrorChamp");

        doThrow(new RuntimeException("DB Connection Failed")).when(championRepository).save(any());

        ChampionSaveException ex = assertThrows(ChampionSaveException.class, () -> championsService.saveNewChampion(dto));
        assertEquals("Failed to save champion: DB Connection Failed", ex.getMessage());
    }

    @Test
    @DisplayName("restoreChampionsFromBackup - Happy Path - Should parse JSON and Reload DB")
    void restoreChampionsFromBackup_ShouldReloadData() throws IOException {
        String jsonContent = "[{\"name\":\"ImportedChamp\", \"rarity\":\"LEGENDARY\"}]";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8));

        when(championsBackupFile.exists()).thenReturn(true);
        when(championsBackupFile.getInputStream()).thenReturn(inputStream);

        List<ChampionEntity> result = championsService.restoreChampionsFromBackup();

        assertEquals(1, result.size());
        assertEquals("ImportedChamp", result.get(0).getName());

        verify(championRepository).deleteAll();
        verify(championRepository).saveAll(any());
    }

    @Test
    @DisplayName("restoreChampionsFromBackup - File Missing - Should Throw Exception")
    void restoreChampionsFromBackup_WhenFileMissing_ShouldThrow() {
        when(championsBackupFile.exists()).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> championsService.restoreChampionsFromBackup());
        assertTrue(ex.getMessage().contains("Backup file champions.json not found"));
    }

    @Test
    @DisplayName("saveNewChampion should map Aura correctly when it exists")
    void saveNewChampion_ShouldMapAura_WhenAuraExists() throws ChampionSaveException {
        ChampionEntryDTO dto = new ChampionEntryDTO(true);
        dto.setName("AuraChamp");
        dto.setAuraExists(true);
        dto.setAmount(33);
        championsService.saveNewChampion(dto);

        ArgumentCaptor<ChampionEntity> entityCaptor = ArgumentCaptor.forClass(ChampionEntity.class);
        verify(championRepository).save(entityCaptor.capture());

        ChampionEntity savedEntity = entityCaptor.getValue();

        Aura savedAura = savedEntity.getAura();
        assertNotNull(savedAura, "Aura should not be null!");
        assertEquals(33, savedAura.getAmount());
        assertTrue(savedAura.isPercentage());
    }
}