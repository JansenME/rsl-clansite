package com.rsl.clansite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsl.clansite.model.VersionInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

@Service
public class VersionService {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.version-file-location}")
    private String versionJsonPath;

    public VersionInfo getCurrentVersion() {
        File versionFile = Paths.get(versionJsonPath).toFile();

        if (versionFile.exists()) {
            try {
                return objectMapper.readValue(versionFile, VersionInfo.class);
            } catch (IOException e) {
                return new VersionInfo("1.0", "/api/download/installer", "Initial version");
            }
        }

        // Fallback to default if file doesn't exist
        return new VersionInfo("1.0", "/api/download/installer", "Initial version");
    }
}
