package com.rsl.clansite.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rsl.clansite.repository.ChampionRepository;
import com.rsl.clansite.repository.ClanmemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Service
@Slf4j
public class BackupService {
    @Value("classpath:backup.json")
    private Resource backupFile;

    private final ChampionRepository championRepository;
    private final ClanmemberRepository clanmemberRepository;
    private final ObjectMapper objectMapper;

    public BackupService(ChampionRepository championRepository, ClanmemberRepository clanmemberRepository) {
        this.championRepository = championRepository;
        this.clanmemberRepository = clanmemberRepository;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public SystemBackupDTO createBackup() {
        return new SystemBackupDTO(
                championRepository.findAll(),
                clanmemberRepository.findAll()
        );
    }

    public byte[] exportBackupToJsonBytes() {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(createBackup());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize backup", e);
        }
    }

    @Transactional
    public void restoreFromBackup() {
        try {
            if (!backupFile.exists()) {
                throw new RuntimeException("Backup file 'backup.json' not found in resources!");
            }

            SystemBackupDTO backup = objectMapper.readValue(backupFile.getInputStream(), SystemBackupDTO.class);

            log.info("Restoring System Backup (Timestamp: {})", backup.getTimestamp());

            if (backup.getChampions() != null) {
                championRepository.deleteAll();
                championRepository.saveAll(backup.getChampions());
                log.info("Restored {} champions.", backup.getChampions().size());
            }

            if (backup.getClanmembers() != null) {
                clanmemberRepository.deleteAll();
                clanmemberRepository.saveAll(backup.getClanmembers());
                log.info("Restored {} clanmembers.", backup.getClanmembers().size());
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read backup file", e);
        }
    }
}
