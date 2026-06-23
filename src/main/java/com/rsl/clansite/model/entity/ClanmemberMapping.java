package com.rsl.clansite.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "clan_member_mapping")
@Data
public class ClanmemberMapping {
    @Id
    private Long plariumId;

    @Field("player_name")
    private String playerName;

    @Field("has_used_app")
    private boolean hasUsedApp;
}
