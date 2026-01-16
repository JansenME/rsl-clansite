package com.rsl.clansite.model.dto;

import com.rsl.clansite.model.enums.Rarity;
import java.util.Map;

public record DataHealthDTO(
        boolean isHealthy,
        int totalMissing,
        Map<Rarity, Integer> missingPerRarity
) {}