package com.rsl.clansite.backup;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backup")
@PreAuthorize("hasRole('OWNER')")
public class BackupController {
    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping("/export-backup")
    public ResponseEntity<byte[]> exportSystemBackup() {
        byte[] jsonBytes = backupService.exportBackupToJsonBytes();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=backup.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBytes);
    }

    @GetMapping("/restore-backup")
    public ResponseEntity<String> restoreSystemBackup() {
        try {
            backupService.restoreFromBackup();
            return ResponseEntity.ok("System restored successfully from backup.json");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Restore failed: " + e.getMessage());
        }
    }
}
