package com.rsl.clansite.controller;

import com.rsl.clansite.model.VersionInfo;
import com.rsl.clansite.service.AppTokenService;
import com.rsl.clansite.service.RosterSyncService;
import com.rsl.clansite.service.VersionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppTokenController {

    private final AppTokenService appTokenService;
    private final RosterSyncService rosterSyncService;
    private final VersionService versionService;

    @PostMapping("/token/generate")
    public ResponseEntity<?> generateToken(Authentication authentication,
                                           HttpServletRequest request,
                                           HttpSession session) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Not authenticated"
            ));
        }

        if (!(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Invalid authentication type"
            ));
        }

        String discordId = oauth2User.getName();

        String discordName = oauth2User.getAttribute("username");
        if (discordName == null) {
            discordName = oauth2User.getAttribute("login");
        }
        if (discordName == null) {
            discordName = oauth2User.getAttribute("global_name");
        }
        if (discordName == null) {
            discordName = discordId;
        }

        String origin = request.getRequestURL().toString()
                .replace(request.getRequestURI(), "");

        String sessionId = session.getId();

        String token = appTokenService.generateToken(authentication, sessionId);

        VersionInfo version = versionService.getCurrentVersion();

        String launchUrl = String.format(
                "kloepiebot://sync?token=%s&user=%s&origin=%s&minVersion=%s",
                token,
                UriUtils.encodePathSegment(discordName, StandardCharsets.UTF_8),
                origin,
                version.minVersion()
        );

        log.info("Generated app token for user: {} ({})", discordName, discordId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "token", token,
                "origin", origin,
                "launchUrl", launchUrl
        ));
    }

    @PostMapping("/champions/sync")
    public ResponseEntity<?> syncChampions(@RequestBody Map<String, Object> payload,
                                           Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Champions sync attempted without authentication");
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Invalid or expired token"
            ));
        }

        String discordId = authentication.getName();
        log.info("Received champion sync from Discord ID: {}", discordId);

        Object championsObj = payload.get("Champions");
        if (championsObj == null) {
            championsObj = payload.get("champions");
        }

        if (!(championsObj instanceof List)) {
            log.error("Invalid payload structure: Champions not found or not a list");
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid payload structure"
            ));
        }

        List<Map<String, Object>> champions = (List<Map<String, Object>>) championsObj;

        try {
            rosterSyncService.saveJsonPayload(discordId, champions);

            log.info("Saved {} champions for {}", champions.size(), discordId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Champions received. Visit /sync/preview to review changes.",
                    "discordId", discordId,
                    "receivedChampionCount", champions.size(),
                    "previewUrl", "/sync/preview"
            ));
        } catch (Exception e) {
            log.error("Failed to save champions", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to process champions: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/champions/status")
    public ResponseEntity<?> checkSyncStatus(Authentication authentication, HttpSession session) {
        String discordId = authentication.getName();

        File jsonFile = rosterSyncService.getExistingJsonFile(discordId);

        boolean hasData = false;
        if (jsonFile != null && jsonFile.exists()) {
            Long uploadTimestamp = (Long) session.getAttribute("championsUploadedAt");
            if (uploadTimestamp != null) {
                long minutesSinceUpload = (System.currentTimeMillis() - uploadTimestamp) / (1000 * 60);
                hasData = minutesSinceUpload < 5;
            }
        }

        return ResponseEntity.ok(Map.of("hasData", hasData));
    }

    @PostMapping("/champions/uploaded")
    public ResponseEntity<?> markChampionsUploaded(Authentication authentication, HttpSession session) {
        String discordId = authentication.getName();

        session.setAttribute("championsUploadedAt", System.currentTimeMillis());

        return ResponseEntity.ok(Map.of("success", true));
    }
}