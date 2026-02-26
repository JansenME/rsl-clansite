package com.rsl.clansite.model.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@Document(collection = "app_tokens")
public class AppToken {

    @Id
    private String id;

    private String token;

    private String discordId;

    private List<String> roles;

    private String sessionId;

    private Instant createdAt;
}