package com.rsl.clansite.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "champion_fingerprints")
public class ChampionFingerprint {

    @Id
    private String id;

    @Indexed
    private String championId;

    @Indexed(unique = true)
    private Long hash;

    private String addedBy;
    private Long timestamp;

}