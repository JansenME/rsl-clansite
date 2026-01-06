package com.rsl.clansite.model.dto;

import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.AuraLocation;
import com.rsl.clansite.model.enums.AuraStat;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import com.rsl.clansite.validation.UniqueChampionName;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class ChampionEntryDTO implements Serializable {
    @NotBlank(message = "Champion Name is required.")
    @UniqueChampionName
    private String name;

    @NotNull(message = "Rarity is required.")
    private Rarity rarity;

    @NotNull(message = "Type is required.")
    private Type type;

    @NotNull(message = "Affinity is required.")
    private Affinity affinity;

    @NotNull(message = "Faction is required.")
    private Faction faction;

    private Double arenaScore;

    @Min(value = 0, message = "HP must be 0 or greater.")
    private int hp;

    @Min(value = 0, message = "Attack must be 0 or greater.")
    private int attack;

    @Min(value = 0, message = "Defense must be 0 or greater.")
    private int defense;

    @Min(value = 0, message = "Speed must be 0 or greater.")
    private int speed;

    @Min(value = 0, message = "Critical Rate must be 0 or greater.")
    private int criticalRate;

    @Min(value = 0, message = "Critical Damage must be 0 or greater.")
    private int criticalDamage;

    @Min(value = 0, message = "Resistance must be 0 or greater.")
    private int resistance;

    @Min(value = 0, message = "Accuracy must be 0 or greater.")
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
