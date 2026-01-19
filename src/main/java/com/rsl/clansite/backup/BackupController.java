package com.rsl.clansite.backup;

import com.rsl.clansite.service.CommonsService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/backups")
@PreAuthorize("hasRole('OWNER')")
public class BackupController {

    private final BackupService backupService;
    private final CommonsService commonsService;

    public BackupController(BackupService backupService, CommonsService commonsService) {
        this.backupService = backupService;
        this.commonsService = commonsService;
    }

    public record BackupFileDTO(String filename, long sizeBytes, LocalDateTime lastModified) {}

    @GetMapping
    public String listBackups(Model model, Authentication authentication, HttpSession session) {
        // Standard UI Setup (Header, Footer, User Info)
        commonsService.fillModel(model, authentication, session);

        List<BackupFileDTO> backups = backupService.listBackups().stream()
                .map(file -> new BackupFileDTO(
                        file.getName(),
                        file.length(),
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault())
                ))
                .collect(Collectors.toList());

        model.addAttribute("backups", backups);

        return "admin/backups"; // Maps to templates/admin/backups.html
    }

    @PostMapping("/trigger")
    public String triggerManualBackup() {
        try {
            backupService.createBackup();
            return "redirect:/admin/backups";
        } catch (Exception e) {
            // For now, redirecting back. In a full implementation, we might add a flash attribute for errors.
            return "redirect:/admin/backups?error=true";
        }
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadBackup(@PathVariable String filename) {
        try {
            File file = backupService.getBackupFile(filename);
            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                    .contentLength(file.length())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (FileNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
}