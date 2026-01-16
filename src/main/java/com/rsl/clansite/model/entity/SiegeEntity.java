package com.rsl.clansite.model.entity;

import com.rsl.clansite.model.SiegeStructure;
import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.model.enums.SiegeStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "sieges")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SiegeEntity {

    @Id
    private ObjectId id;

    @Indexed
    private ClanGroup clanGroup;

    @Indexed
    private SiegeStatus status;

    private LocalDateTime startDate;
    private LocalDateTime lastModified;

    private String opponentClanName = "TBD";

    private List<SiegeStructure> defensiveStructures = new ArrayList<>();

    private List<SiegeStructure> targetStructures = new ArrayList<>();

    private Map<String, SiegeMemberData> memberStats = new HashMap<>();

    public SiegeEntity(ClanGroup clanGroup) {
        this.clanGroup = clanGroup;
        this.status = SiegeStatus.PREP;
        this.startDate = LocalDateTime.now();
        this.lastModified = LocalDateTime.now();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SiegeMemberData {
        private int maxDefenseScrolls = 0;
        private int maxAttackScrolls = 0;

        private int usedDefenseScrolls = 0;
        private int usedAttackScrolls = 0;
    }
}