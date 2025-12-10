package com.rsl.clansite.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Slf4j
@Service
public class CommonsService {
    @Value("${app.version:unknown}")
    String version;

    public void fillModel(Model model) {
        model.addAttribute("versionNumber", version);
        model.addAttribute("currentYear", new SimpleDateFormat("yyyy").format(new Date()));
        model.addAttribute("applicationDate", getApplicationDate());
    }

    private String getApplicationDate() {
        URL resource = getClass().getResource(getClass().getSimpleName() + ".class");

        if (resource == null) {
            log.error("Failed to find class file for class");
            return "";
        }
        try {
            if (resource.getProtocol().equals("file")) {
                return formatDate(new File(resource.toURI()).lastModified());
            } else if (resource.getProtocol().equals("jar") || resource.getProtocol().equals("war")) {
                String path = resource.getPath();
                return formatDate(new File(path.substring(5, path.indexOf("!"))).lastModified());
            }
        } catch (URISyntaxException e) {
            log.error(e.getMessage());
            return "";
        }

        log.error("Something not right, no date was found and returned");
        return "";
    }

    private String formatDate(final long millis) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.of("Europe/Paris"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d-M-yyyy");

        return localDateTime.format(formatter);
    }
}
