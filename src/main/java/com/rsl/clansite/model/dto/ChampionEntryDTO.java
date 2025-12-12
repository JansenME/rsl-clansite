package com.rsl.clansite.model.dto;

import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.AuraLocation;
import com.rsl.clansite.model.enums.AuraStat;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import lombok.Data;

@Data
public class ChampionEntryDTO {
    private String name;
    private Rarity rarity;
    private Type type;
    private Affinity affinity;
    private Faction faction;
    private Double arenaScore;

    private int hp;
    private int attack;
    private int defense;
    private int speed;
    private int criticalRate;
    private int criticalDamage;
    private int resistance;
    private int accuracy;

    private boolean auraExists;
    private boolean percentageAura;
    private int amount;
    private AuraStat stat;
    private AuraLocation location;

    public ChampionEntryDTO(final boolean percentageAura) {
        this.percentageAura = percentageAura;
    }
}
