package com.rsl.clansite.model.dto;

import com.rsl.clansite.model.Aura;
import com.rsl.clansite.model.BaseStats;
import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import lombok.Data;

@Data
public class ScrapedChampion {
    private String name;
    private String url;
    private String imageUrl;

    private Rarity rarity;
    private Type type;
    private Affinity affinity;

    private BaseStats baseStats;
    private Aura aura;
    private Double arenaScore;
}
