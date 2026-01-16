package com.rsl.clansite.model.entity;

import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

@Document(collection = "faction_targets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FactionTargetEntity {

    @Id
    private ObjectId id;

    @Indexed(unique = true)
    private Faction faction;

    private Map<Rarity, Integer> rarityTargets = new HashMap<>();

    public FactionTargetEntity(Faction faction) {
        this.faction = faction;
        this.rarityTargets = new HashMap<>();
    }
}