package com.rsl.clansite.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Team {

    private String id = UUID.randomUUID().toString();
    private String teamName;

    private String leaderChampionId;

    private String champion2Id;
    private String champion3Id;
    private String champion4Id;

    private ObjectId siegeConditionId;
}