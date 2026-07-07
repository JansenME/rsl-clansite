package com.rsl.clansite.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "raiduser")
public class RaidUser {

    @Id
    private ObjectId id;

    @Indexed(unique = true)
    private Long raidId;

    private String playerName;
}
