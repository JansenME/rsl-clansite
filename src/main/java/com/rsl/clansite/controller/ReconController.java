package com.rsl.clansite.controller;

import com.rsl.clansite.model.dto.ChampionOptionDTO;
import com.rsl.clansite.model.dto.FingerprintSubmissionDTO;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.entity.ChampionFingerprint;
import com.rsl.clansite.repository.ChampionFingerprintRepository;
import com.rsl.clansite.repository.ChampionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/recon")
@RequiredArgsConstructor
public class ReconController {

    private final ChampionRepository championRepository;
    private final ChampionFingerprintRepository fingerprintRepository;

    // 1. SEARCH: Used by C# Dropdown to find the Champion ID
    @GetMapping("/champions")
    public List<ChampionOptionDTO> searchChampions(@RequestParam String query) {
        List<ChampionEntity> matches = championRepository.findByNameContainingIgnoreCase(query);

        return matches.stream()
                .map(c -> new ChampionOptionDTO(c.getId().toHexString(), c.getName()))
                .limit(10)
                .collect(Collectors.toList());
    }

    // 2. LIBRARY SYNC: Used by C# Bot on Startup to download the "Brain"
    @GetMapping("/library")
    public List<Map<String, Object>> getLibrary() {
        // Fetch all fingerprints
        List<ChampionFingerprint> fingerprints = fingerprintRepository.findAll();

        // Fetch all Champions
        List<ChampionEntity> allChampions = championRepository.findAll();

        // Create a lookup map: ID -> Name
        Map<String, String> nameMap = allChampions.stream()
                .collect(Collectors.toMap(
                        c -> c.getId().toHexString(),
                        ChampionEntity::getName
                ));

        List<Map<String, Object>> result = new ArrayList<>();

        for (ChampionFingerprint fp : fingerprints) {
            String name = nameMap.get(fp.getChampionId());

            // Only add if we actually found the champion name (Data Integrity)
            if (name != null) {
                result.add(Map.of(
                        "championName", name,
                        "hash", fp.getHash()
                ));
            }
        }
        return result;
    }

    // 3. TRAIN: Used by C# Bot to upload new learning
    @PostMapping("/train")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'COORDINATOR')")
    public ChampionFingerprint submitFingerprint(@RequestBody FingerprintSubmissionDTO dto,
                                                 Authentication authentication) {

        if (fingerprintRepository.findByHash(dto.getHash()).isPresent()) {
            return null;
        }

        ChampionFingerprint fp = new ChampionFingerprint();
        fp.setChampionId(dto.getChampionId());
        fp.setHash(dto.getHash());
        fp.setTimestamp(System.currentTimeMillis());

        if (authentication != null) {
            fp.setAddedBy(authentication.getName());
        }

        log.info("New Fingerprint added for Champion {} by {}", dto.getChampionId(), fp.getAddedBy());

        return fingerprintRepository.save(fp);
    }
}