package com.rsl.clansite.model;

import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Champion {
    private String name;
    private Rarity rarity;
    private Type type;
    private Affinity affinity;
    private Faction faction;
    private BaseStats baseStats;
    private Aura aura;
    private Double arenaScore;
    private String imagename;
}
