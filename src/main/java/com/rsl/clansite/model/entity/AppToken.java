package com.rsl.clansite.model.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "app_tokens")
public class AppToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String token;

    @Indexed
    private String discordId;

    private String globalName;

    private List<String> roles;

    private long createdDate;

    public AppToken(String token, String discordId, List<String> roles, String globalName) {
        this.token = token;
        this.discordId = discordId;
        this.roles = roles;
        this.globalName = globalName;
        this.createdDate = System.currentTimeMillis();
    }
}