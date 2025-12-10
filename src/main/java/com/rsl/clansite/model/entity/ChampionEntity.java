package com.rsl.clansite.model.entity;

import com.rsl.clansite.model.Aura;
import com.rsl.clansite.model.BaseStats;
import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
public class ChampionEntity {
    @Id
    private ObjectId id;

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
