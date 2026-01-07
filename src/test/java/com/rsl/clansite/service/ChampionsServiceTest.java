package com.rsl.clansite.service;

import com.rsl.clansite.exceptions.ChampionSaveException;
import com.rsl.clansite.model.Aura;
import com.rsl.clansite.model.BaseStats;
import com.rsl.clansite.model.Champion;
import com.rsl.clansite.model.dto.ChampionEntryDTO;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.enums.AuraLocation;
import com.rsl.clansite.model.enums.AuraStat;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.repository.ChampionRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChampionsServiceTest {
    @Mock
    private ChampionRepository championRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private ChampionsService championsService;

    @Test
    @DisplayName("getAllChampions should return sorted list of champions")
    void getAllChampions_ShouldReturnSortedList() {
        ChampionEntity c1 = new ChampionEntity();
        c1.setId(ObjectId.get());
        c1.setName("Z-Champ");

        ChampionEntity c2 = new ChampionEntity();
        c2.setId(ObjectId.get());
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

        assertThrows(ChampionSaveException.class, () -> championsService.saveNewChampion(dto, authentication));
    }

    @Test
    @DisplayName("saveNewChampion (Happy Path) should save ONLY to DB (No CSV)")
    void saveNewChampion_ShouldSaveToDb() throws ChampionSaveException {
        ChampionEntryDTO dto = new ChampionEntryDTO(false);
        dto.setName("TestChamp");
        dto.setHp(100);

        championsService.saveNewChampion(dto, authentication);

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

        ChampionSaveException ex = assertThrows(ChampionSaveException.class, () -> championsService.saveNewChampion(dto, authentication));
        assertEquals("Failed to save champion: DB Connection Failed", ex.getMessage());
    }

    @Test
    @DisplayName("saveNewChampion should map Aura correctly when it exists")
    void saveNewChampion_ShouldMapAura_WhenAuraExists() throws ChampionSaveException {
        ChampionEntryDTO dto = new ChampionEntryDTO(true);
        dto.setName("AuraChamp");
        dto.setAuraExists(true);
        dto.setAmount(33);
        championsService.saveNewChampion(dto, authentication);

        ArgumentCaptor<ChampionEntity> entityCaptor = ArgumentCaptor.forClass(ChampionEntity.class);
        verify(championRepository).save(entityCaptor.capture());

        ChampionEntity savedEntity = entityCaptor.getValue();

        Aura savedAura = savedEntity.getAura();
        assertNotNull(savedAura, "Aura should not be null!");
        assertEquals(33, savedAura.getAmount());
        assertTrue(savedAura.isPercentage());
    }

    @Test
    @DisplayName("getChampionById - Valid ID and Found - Should return Entity")
    void getChampionById_Valid_ShouldReturnEntity() {
        String id = "507f1f77bcf86cd799439011";
        ChampionEntity entity = new ChampionEntity();
        entity.setId(new org.bson.types.ObjectId(id));
        entity.setName("Kael");

        when(championRepository.findById(new org.bson.types.ObjectId(id))).thenReturn(java.util.Optional.of(entity));

        ChampionEntity result = championsService.getChampionById(id);

        assertNotNull(result);
        assertEquals("Kael", result.getName());
    }

    @Test
    @DisplayName("getChampionById - Invalid ID Format - Should throw IllegalArgumentException")
    void getChampionById_InvalidFormat_ShouldThrow() {
        String invalidId = "invalid-id";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                championsService.getChampionById(invalidId)
        );

        assertTrue(ex.getMessage().contains("Invalid Champion ID"));
    }

    @Test
    @DisplayName("getChampionById - Valid ID but Not Found - Should throw RuntimeException")
    void getChampionById_NotFound_ShouldThrow() {
        String id = "507f1f77bcf86cd799439011";
        when(championRepository.findById(any())).thenReturn(java.util.Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                championsService.getChampionById(id)
        );

        assertTrue(ex.getMessage().contains("Champion not found"));
    }

    @Test
    @DisplayName("Mapping Check - Should correctly map Entity ID to Domain ID")
    void mapping_ShouldMapIdCorrectly() {
        String hexId = "507f1f77bcf86cd799439011";
        ChampionEntity entity = new ChampionEntity();
        entity.setId(new org.bson.types.ObjectId(hexId));
        entity.setName("TestMap");

        when(championRepository.findAll()).thenReturn(List.of(entity));

        List<Champion> result = championsService.getAllChampions();

        assertEquals(1, result.size());
        assertEquals(hexId, result.get(0).getId());
        assertEquals("TestMap", result.get(0).getName());
    }

    @Test
    @DisplayName("getChampionForEdit - Should return fully populated DTO")
    void getChampionForEdit_ShouldReturnDTO() {
        String id = "507f1f77bcf86cd799439011";
        ChampionEntity entity = new ChampionEntity();
        entity.setId(new org.bson.types.ObjectId(id));
        entity.setName("Kael");
        entity.setRarity(Rarity.RARE);
        entity.setBaseStats(new com.rsl.clansite.model.BaseStats(1000, 100, 100, 90, 15, 50, 0, 0));
        entity.setImagename("kael.png");

        when(championRepository.findById(any())).thenReturn(Optional.of(entity));

        ChampionEntryDTO result = championsService.getChampionForEdit(id);

        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals("Kael", result.getName());
        assertEquals("kael.png", result.getCurrentImageName());
        assertEquals(1000, result.getHp());
    }

    @Test
    @DisplayName("updateChampion - Valid Update - Should Save Entity with new Filename")
    void updateChampion_Valid_ShouldSave() throws ChampionSaveException {
        String id = "507f1f77bcf86cd799439011";
        ChampionEntity existingEntity = new ChampionEntity();
        existingEntity.setId(new org.bson.types.ObjectId(id));
        existingEntity.setName("OldName");
        existingEntity.setImagename("oldname.png");

        ChampionEntryDTO dto = new ChampionEntryDTO();
        dto.setName("NewName");
        dto.setRarity(Rarity.LEGENDARY);

        when(championRepository.findById(any())).thenReturn(Optional.of(existingEntity));
        when(championRepository.findByName("NewName")).thenReturn(Optional.empty());

        championsService.updateChampion(id, dto, authentication);

        verify(championRepository).save(argThat(savedEntity ->
                savedEntity.getName().equals("NewName") &&
                        savedEntity.getImagename().equals("newname.png")
        ));
    }

    @Test
    @DisplayName("updateChampion - Name Taken by Other - Should Throw Exception")
    void updateChampion_NameTaken_ShouldThrow() {
        String id1 = "507f1f77bcf86cd799439011";
        String id2 = "607f1f77bcf86cd799439022";

        ChampionEntity targetEntity = new ChampionEntity();
        targetEntity.setId(new org.bson.types.ObjectId(id1));

        ChampionEntity conflictEntity = new ChampionEntity();
        conflictEntity.setId(new org.bson.types.ObjectId(id2));
        conflictEntity.setName("TakenName");

        ChampionEntryDTO dto = new ChampionEntryDTO();
        dto.setName("TakenName");

        when(championRepository.findById(any())).thenReturn(Optional.of(targetEntity));
        when(championRepository.findByName("TakenName")).thenReturn(Optional.of(conflictEntity));

        assertThrows(ChampionSaveException.class, () ->
                championsService.updateChampion(id1, dto, authentication)
        );
    }

    @Test
    @DisplayName("saveNewChampion - Duplicate Name - Should Throw Exception")
    void saveNewChampion_DuplicateName_ShouldThrow() {
        ChampionEntryDTO dto = new ChampionEntryDTO(false);
        dto.setName("Duplicate");

        when(championRepository.findByName("Duplicate")).thenReturn(Optional.of(new ChampionEntity()));

        assertThrows(ChampionSaveException.class, () -> championsService.saveNewChampion(dto, authentication));
    }

    @Test
    @DisplayName("getChampionForEdit - Champion has Aura - Should Map Aura Details")
    void getChampionForEdit_WithAura_ShouldMapCorrectly() {
        String id = "507f1f77bcf86cd799439011";
        ChampionEntity entity = new ChampionEntity();
        entity.setId(new ObjectId(id));
        entity.setName("AuraMaster");
        entity.setBaseStats(new BaseStats());

        entity.setAura(new Aura(true, 19, AuraStat.ALLY_SPD, AuraLocation.ALL_BATTLES));

        when(championRepository.findById(any())).thenReturn(Optional.of(entity));

        ChampionEntryDTO result = championsService.getChampionForEdit(id);

        assertTrue(result.isAuraExists());
        assertTrue(result.isPercentageAura());
        assertEquals(19, result.getAmount());
        assertEquals(AuraStat.ALLY_SPD, result.getStat());
        assertEquals(AuraLocation.ALL_BATTLES, result.getLocation());
    }

    @Test
    @DisplayName("updateChampion - DTO has Aura - Should Save Entity with Aura")
    void updateChampion_WithAura_ShouldSaveAura() throws ChampionSaveException {
        String id = "507f1f77bcf86cd799439011";
        ChampionEntity existing = new ChampionEntity();
        existing.setId(new ObjectId(id));
        existing.setName("OldName");

        ChampionEntryDTO dto = new ChampionEntryDTO();
        dto.setName("NewName");

        dto.setAuraExists(true);
        dto.setPercentageAura(false);
        dto.setAmount(50);
        dto.setStat(AuraStat.ALLY_HP);
        dto.setLocation(AuraLocation.DUNGEON);

        when(championRepository.findById(any())).thenReturn(Optional.of(existing));
        when(championRepository.findByName("NewName")).thenReturn(Optional.empty());

        championsService.updateChampion(id, dto, authentication);

        verify(championRepository).save(argThat(saved ->
                saved.getAura() != null &&
                        saved.getAura().getAmount() == 50 &&
                        !saved.getAura().isPercentage() &&
                        saved.getAura().getStat() == AuraStat.ALLY_HP
        ));
    }

    @Test
    @DisplayName("updateChampion - Should Generate and Log Diff of Changes")
    void updateChampion_ShouldLogDiff() throws ChampionSaveException {
        String id = "507f1f77bcf86cd799439011";

        ChampionEntity existing = new ChampionEntity();
        existing.setId(new org.bson.types.ObjectId(id));
        existing.setName("OldName");
        existing.setRarity(Rarity.RARE);
        existing.setBaseStats(new BaseStats(100, 100, 100, 100, 15, 50, 0, 0));

        ChampionEntryDTO dto = new ChampionEntryDTO();
        dto.setName("NewName");
        dto.setRarity(Rarity.RARE);
        dto.setHp(200);
        dto.setType(com.rsl.clansite.model.enums.Type.ATTACK);
        dto.setAffinity(com.rsl.clansite.model.enums.Affinity.MAGIC);
        dto.setFaction(com.rsl.clansite.model.enums.Faction.BANNER_LORDS);
        dto.setAttack(100);
        dto.setDefense(100);
        dto.setSpeed(100);

        when(championRepository.findById(any())).thenReturn(Optional.of(existing));
        when(championRepository.findByName("NewName")).thenReturn(Optional.empty());

        championsService.updateChampion(id, dto, authentication);

        ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);

        verify(auditLogService).logAction(
                any(),
                eq(com.rsl.clansite.model.enums.AuditAction.CHAMPION_UPDATE),
                eq("NewName"),
                detailsCaptor.capture()
        );

        String loggedDetails = detailsCaptor.getValue();

        assertTrue(loggedDetails.contains("Name: OldName->NewName"));
        assertTrue(loggedDetails.contains("HP: 100->200"));
        assertFalse(loggedDetails.contains("Rarity"));
    }

    @Test
    @DisplayName("deleteChampion - Should Delete from Repo and Log Action")
    void deleteChampion_ShouldDeleteAndLog() {
        String id = "507f1f77bcf86cd799439011";
        ChampionEntity entity = new ChampionEntity();
        entity.setId(new ObjectId(id));
        entity.setName("To Be Deleted");

        when(championRepository.findById(new ObjectId(id))).thenReturn(Optional.of(entity));

        championsService.deleteChampion(id, authentication);

        verify(championRepository).delete(entity);

        verify(auditLogService).logAction(
                eq(authentication),
                eq(com.rsl.clansite.model.enums.AuditAction.CHAMPION_DELETE),
                eq("To Be Deleted"),
                contains("Deleted champion manually")
        );
    }
}