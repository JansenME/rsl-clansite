package com.rsl.clansite.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
public class OwnedChampion {

    private String id = UUID.randomUUID().toString();
    private String championId;
    private int level;
    private int rank;

    public OwnedChampion(String championId, int level, int rank) {
        this.championId = championId;
        this.level = level;
        this.rank = rank;
    }
}