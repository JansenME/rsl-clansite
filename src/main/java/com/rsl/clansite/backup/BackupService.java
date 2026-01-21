package com.rsl.clansite.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rsl.clansite.repository.ChampionRepository;
import com.rsl.clansite.repository.ClanmemberRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

@Service
@Slf4j
public class BackupService {

    private final MongoTemplate mongoTemplate;
    private final String backupLocation;
    private final boolean backupEnabled;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    public BackupService(MongoTemplate mongoTemplate,
                         @Value("${app.backup.location}") String backupLocation,
                         @Value("${app.backup.enabled:false}") boolean backupEnabled) {
        this.mongoTemplate = mongoTemplate;
        this.backupLocation = backupLocation;
        this.backupEnabled = backupEnabled;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void init() {
        if (backupEnabled) {
            try {
                Path path = Paths.get(backupLocation);
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                    log.info("Backup directory created at: {}", backupLocation);
                } else {
                    log.info("Backup directory already exists at: {}", backupLocation);
                }

                if (!Files.isWritable(path)) {
                    log.error("WARNING: Backup directory exists but is NOT writable by Tomcat: {}", backupLocation);
                }
            } catch (IOException e) {
                log.error("Could not initialize backup directory: {}", e.getMessage());
            }
        }
    }

    /**
     * Automatic Daily Backup at 3:00 AM Server Time.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void runScheduledBackup() {
        if (!backupEnabled) return;

        log.info("Starting Scheduled Daily Backup...");
        try {
            String filename = createBackup();
            log.info("Scheduled Backup finished successfully: {}", filename);
        } catch (Exception e) {
            log.error("Scheduled Backup failed execution", e);
        }
    }

    /**
     * Triggered manually or by schedule.
     * 1. Dumps DB to JSON.
     * 2. GZIPs the file.
     * 3. Runs retention cleanup.
     */
    public String createBackup() {
        if (!backupEnabled) {
            return "Backup is disabled in configuration.";
        }

        String filename = "backup-" + LocalDateTime.now().format(FILE_DATE_FORMATTER) + ".json.gz";
        Path filePath = Paths.get(backupLocation, filename);

        log.info("Starting database backup: {}", filename);

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile());
             GZIPOutputStream gzipOs = new GZIPOutputStream(fos);
             OutputStreamWriter writer = new OutputStreamWriter(gzipOs, StandardCharsets.UTF_8)) {

            Map<String, List<Object>> fullDump = new HashMap<>();
            Set<String> collectionNames = mongoTemplate.getCollectionNames();

            for (String collectionName : collectionNames) {
                List<Object> allDocs = mongoTemplate.findAll(Object.class, collectionName);
                fullDump.put(collectionName, allDocs);
            }

            objectMapper.writeValue(writer, fullDump);

            log.info("Backup completed successfully. Size: {} bytes", Files.size(filePath));

            performRetentionCleanup();

            return filename;

        } catch (IOException e) {
            log.error("Backup failed", e);
            throw new RuntimeException("Backup failed: " + e.getMessage());
        }
    }

    public List<File> listBackups() {
        try {
            return Files.list(Paths.get(backupLocation))
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(f -> f.getName().endsWith(".json.gz"))
                    .sorted((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified())) // Newest first
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list backups", e);
            return List.of();
        }
    }

    public File getBackupFile(String filename) {
        Path path = Paths.get(backupLocation, filename).normalize();
        // Security check to prevent path traversal (e.g. ../../etc/passwd)
        if (!path.startsWith(Paths.get(backupLocation))) {
            throw new IllegalArgumentException("Invalid filename");
        }
        File file = path.toFile();
        if (!file.exists()) {
            throw new IllegalArgumentException("File not found");
        }
        return file;
    }

    /**
     * Smart Retention Logic:
     * - Current Month: Keep ALL files.
     * - Past Months: Keep ONLY the latest file of that month.
     */
    public void performRetentionCleanup() {
        List<File> allBackups = listBackups();
        if (allBackups.isEmpty()) return;

        // Group files by YearMonth
        Map<String, List<File>> backupsByMonth = new HashMap<>();

        for (File file : allBackups) {
            Optional<LocalDateTime> dateOpt = parseDateFromFilename(file.getName());
            if (dateOpt.isPresent()) {
                LocalDateTime date = dateOpt.get();
                String key = date.getYear() + "-" + date.getMonthValue(); // e.g., "2025-12"
                backupsByMonth.computeIfAbsent(key, k -> new ArrayList<>()).add(file);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        String currentMonthKey = now.getYear() + "-" + now.getMonthValue();

        int deletedCount = 0;

        for (Map.Entry<String, List<File>> entry : backupsByMonth.entrySet()) {
            String monthKey = entry.getKey();
            List<File> filesInMonth = entry.getValue();

            // If it's the current month, keep everything
            if (monthKey.equals(currentMonthKey)) {
                continue;
            }

            // If it's a past month, keep only the one with the latest timestamp
            if (filesInMonth.size() > 1) {
                // Sort descending (newest first)
                filesInMonth.sort((f1, f2) -> f2.getName().compareTo(f1.getName()));

                // Keep index 0 (latest), delete the rest
                for (int i = 1; i < filesInMonth.size(); i++) {
                    File toDelete = filesInMonth.get(i);
                    try {
                        if (toDelete.delete()) {
                            deletedCount++;
                            log.info("Retention: Deleted old backup {}", toDelete.getName());
                        }
                    } catch (Exception e) {
                        log.warn("Retention: Failed to delete {}", toDelete.getName());
                    }
                }
            }
        }

        if (deletedCount > 0) {
            log.info("Retention Cleanup: Removed {} old backup files.", deletedCount);
        }
    }

    private Optional<LocalDateTime> parseDateFromFilename(String filename) {
        try {
            // filename: backup-2026-01-20-153000.json.gz
            String datePart = filename.replace("backup-", "").replace(".json.gz", "");
            return Optional.of(LocalDateTime.parse(datePart, FILE_DATE_FORMATTER));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}