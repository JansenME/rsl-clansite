package com.rsl.clansite.controller;

import com.rsl.clansite.model.VersionInfo;
import com.rsl.clansite.service.VersionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/download")
public class DownloadController {
    @Value("${app.kloepiebot.installer-path}")
    private String installerPath;

    @Value("${app.kloepiebot.installer-filename}")
    private String installerFilename;

    private final VersionService versionService;

    public DownloadController(VersionService versionService) {
        this.versionService = versionService;
    }

    /**
     * Serve the MSI installer
     */
    @GetMapping("/installer")
    public ResponseEntity<Resource> downloadInstaller(HttpServletRequest request) {
        try {
            Path path = Paths.get(installerPath);
            Resource resource = new InputStreamResource(Files.newInputStream(path));

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            long fileSize = Files.size(path);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + installerFilename + "\"");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileSize)
                    .contentType(MediaType.parseMediaType("application/x-msi"))
                    .body(resource);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get version info for the launcher script
     */
    @GetMapping("/version-info")
    public ResponseEntity<VersionInfo> getVersionInfo() {
        VersionInfo info = versionService.getCurrentVersion();
        return ResponseEntity.ok(info);
    }
}

