package com.rsl.clansite.model.entity;

import com.rsl.clansite.model.enums.ClanGroup;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "clanmembers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClanmemberEntity {
    @Id
    private ObjectId id;

    private ClanGroup clanGroup;
    private String discordName;

    @Indexed
    private String discordId;

    private String avatarHash;
    private List<String> discordRoles;
    private String playerNickname;

    @Indexed(unique = true)
    private String ingameName;

    private String clanRank;
    private List<String> rosterChampionIds = new ArrayList<>();

    private LocalDateTime lastLogin;

    private LocalDateTime rosterLastUpdated;
    private String rosterUpdatedBy;
}
