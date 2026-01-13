package com.rsl.clansite.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class TargetService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, Map<String, Integer>> targets = new HashMap<>();

    @PostConstruct
    public void init() {
        try (InputStream is = getClass().getResourceAsStream("/champion_targets.json")) {
            if (is == null) {
                log.warn("champion_targets.json not found!");
                return;
            }
            targets = objectMapper.readValue(is, new TypeReference<>() {});
            log.info("Loaded Champion Targets for {} factions.", targets.size());
        } catch (IOException e) {
            log.error("Failed to load champion_targets.json", e);
        }
    }

    public Map<String, Integer> getTargetsForFaction(Faction faction) {
        return targets.getOrDefault(faction.getName(), Collections.emptyMap());
    }

    public int getTargetCount(Faction faction, Rarity rarity) {
        Map<String, Integer> factionTargets = targets.getOrDefault(faction.getName(), Collections.emptyMap());
        return factionTargets.getOrDefault(rarity.name().toLowerCase(), 0);
    }

    public int getMyTotalForFaction(Faction faction) {
        Map<String, Integer> factionTargets = targets.getOrDefault(faction.getName(), Collections.emptyMap());
        return factionTargets.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getTotalChampionCount() {
        return targets.values().stream()
                .flatMap(rarityMap -> rarityMap.values().stream())
                .mapToInt(Integer::intValue)
                .sum();
    }
}
