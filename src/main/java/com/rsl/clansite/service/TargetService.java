package com.rsl.clansite.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsl.clansite.model.entity.FactionTargetEntity;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.repository.FactionTargetRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TargetService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FactionTargetRepository factionTargetRepository;

    public TargetService(FactionTargetRepository factionTargetRepository) {
        this.factionTargetRepository = factionTargetRepository;
    }

    @PostConstruct
    @Transactional
    public void init() {
        // 1. Load JSON Baseline
        Map<String, Map<String, Integer>> jsonTargets = new HashMap<>();
        try (InputStream is = getClass().getResourceAsStream("/champion_targets.json")) {
            if (is != null) {
                jsonTargets = objectMapper.readValue(is, new TypeReference<>() {});
                log.info("Loaded baseline targets from JSON for {} factions.", jsonTargets.size());
            } else {
                log.warn("champion_targets.json not found!");
            }
        } catch (IOException e) {
            log.error("Failed to load champion_targets.json", e);
        }

        // 2. Sync Logic: Iterate Factions -> Rarities
        for (Faction faction : Faction.values()) {
            syncFaction(faction, jsonTargets.getOrDefault(faction.getName(), Collections.emptyMap()));
        }
    }

    private void syncFaction(Faction faction, Map<String, Integer> jsonRarityMap) {
        FactionTargetEntity entity = factionTargetRepository.findByFaction(faction)
                .orElse(new FactionTargetEntity(faction));

        boolean changed = false;

        for (Rarity rarity : Rarity.values()) {
            // REMOVED: Skipping Common/Uncommon. Now we track EVERYTHING.

            // JSON Key is usually lowercase (e.g. "legendary")
            int jsonCount = jsonRarityMap.getOrDefault(rarity.name().toLowerCase(), 0);
            int dbCount = entity.getRarityTargets().getOrDefault(rarity, 0);

            // LOGIC: Highest Number Wins
            if (jsonCount > dbCount) {
                entity.getRarityTargets().put(rarity, jsonCount);
                changed = true;
                log.info("Sync [{} - {}]: Updated DB ({}) to match higher JSON ({})", faction, rarity, dbCount, jsonCount);
            } else if (dbCount == 0 && jsonCount > 0) {
                // Initialize if DB is empty
                entity.getRarityTargets().put(rarity, jsonCount);
                changed = true;
            }
        }

        if (changed) {
            factionTargetRepository.save(entity);
        }
    }

    // --- Runtime Methods (Read from DB) ---

    public int getTargetCount(Faction faction, Rarity rarity) {
        return factionTargetRepository.findByFaction(faction)
                .map(entity -> entity.getRarityTargets().getOrDefault(rarity, 0))
                .orElse(0);
    }

    public int getMyTotalForFaction(Faction faction) {
        return factionTargetRepository.findByFaction(faction)
                .map(entity -> entity.getRarityTargets().values().stream().mapToInt(Integer::intValue).sum())
                .orElse(0);
    }

    public int getGlobalTargetForRarity(Rarity rarity) {
        List<FactionTargetEntity> allFactions = factionTargetRepository.findAll();
        return allFactions.stream()
                .mapToInt(e -> e.getRarityTargets().getOrDefault(rarity, 0))
                .sum();
    }

    public int getTotalChampionCount() {
        return factionTargetRepository.findAll().stream()
                .flatMap(e -> e.getRarityTargets().values().stream())
                .mapToInt(Integer::intValue)
                .sum();
    }

    @Transactional
    public void updateTarget(Faction faction, Rarity rarity, int newCount) {
        FactionTargetEntity entity = factionTargetRepository.findByFaction(faction)
                .orElse(new FactionTargetEntity(faction));

        entity.getRarityTargets().put(rarity, newCount);
        factionTargetRepository.save(entity);
        log.info("Manually updated target for [{} - {}] to {}", faction.getName(), rarity, newCount);
    }
}