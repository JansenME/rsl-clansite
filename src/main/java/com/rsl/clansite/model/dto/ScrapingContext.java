package com.rsl.clansite.model.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ScrapingContext {
    private final ChampionTarget target;
    private ScrapedChampion scrapedData;
    private String error;
}
