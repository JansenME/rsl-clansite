package com.rsl.clansite.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "hub_config")
@CompoundIndex(name = "guild_channel_idx", def = "{'guildId': 1, 'channelId': 1}")
public class HubConfigEntity {

    @Id
    private String id;

    private String guildId;
    private String channelId;
    private String messageId;
}