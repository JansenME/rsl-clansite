package com.rsl.clansite.backup;

import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.repository.ChampionRepository;
import com.rsl.clansite.repository.ClanmemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupServiceTest {
    @Mock
    private ChampionRepository championRepository;

    @Mock
    private ClanmemberRepository clanmemberRepository;

    @Mock
    private Resource backupFile;

    @InjectMocks
    private BackupService backupService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(backupService, "backupFile", backupFile);
    }

    @Test
    @DisplayName("createBackup - Should gather data from all repositories")
    void createBackup_ShouldGatherData() {
        when(championRepository.findAll()).thenReturn(List.of(new ChampionEntity()));
        when(clanmemberRepository.findAll()).thenReturn(List.of(new ClanmemberEntity()));

        SystemBackupDTO result = backupService.createBackup();

        assertNotNull(result);
        assertEquals(1, result.getChampions().size());
        assertEquals(1, result.getClanmembers().size());
        assertNotNull(result.getTimestamp());
    }

    @Test
    @DisplayName("restoreFromBackup - Happy Path - Should parse JSON and Reload Repositories")
    void restoreFromBackup_ShouldReloadData() throws IOException {
        String jsonContent = """
            {
                "version": "1.0",
                "champions": [{"name": "Kael"}],
                "clanmembers": [{"ingameName": "PlayerOne"}]
            }
        """;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8));

        when(backupFile.exists()).thenReturn(true);
        when(backupFile.getInputStream()).thenReturn(inputStream);

        backupService.restoreFromBackup();

        verify(championRepository).deleteAll();
        verify(championRepository).saveAll(argThat(iterable -> {
            List<ChampionEntity> list = (List<ChampionEntity>) iterable;
            return list.size() == 1 && list.get(0).getName().equals("Kael");
        }));

        verify(clanmemberRepository).deleteAll();
        verify(clanmemberRepository).saveAll(argThat(iterable -> {
            List<ClanmemberEntity> list = (List<ClanmemberEntity>) iterable;
            return list.size() == 1 && list.get(0).getIngameName().equals("PlayerOne");
        }));
    }

    @Test
    @DisplayName("restoreFromBackup - File Missing - Should Throw Exception")
    void restoreFromBackup_WhenFileMissing_ShouldThrow() {
        when(backupFile.exists()).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> backupService.restoreFromBackup());
        assertTrue(ex.getMessage().contains("Backup file 'backup.json' not found"));
    }
}