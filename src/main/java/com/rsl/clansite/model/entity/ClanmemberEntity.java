package com.rsl.clansite.model.entity;

import com.rsl.clansite.model.Team;
import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.model.enums.ClanRank;
import com.rsl.clansite.model.enums.MemberStatus;
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

    private ClanRank clanRank;

    private int maxDefenseScrolls = 2;

    private List<String> rosterChampionIds = new ArrayList<>();

    private List<Team> knownTeams = new ArrayList<>();

    private MemberStatus status = MemberStatus.ACTIVE;
    private LocalDateTime statusChangedDate;

    private LocalDateTime lastLogin;
    private String lastLocation;

    private String lastSeenNoticeId;

    private LocalDateTime rosterLastUpdated;
    private String rosterUpdatedBy;
}