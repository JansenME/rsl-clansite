package com.rsl.clansite.model.entity;

import com.rsl.clansite.model.Champion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "clanmembers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClanmemberEntity {
    @Id
    private ObjectId id;

    private String discordName;
    private String discordId;
    private String avatarHash;
    private String playerName;
    private String ingameName;
    private String clanRank;
    private List<Champion> champions;
}
