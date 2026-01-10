package com.rsl.clansite.model.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "visitor_logs")
@Data
@NoArgsConstructor
public class VisitorLogEntity {
    @Id
    private ObjectId id;

    private String discordId;
    private String username;
    private String avatarHash;
    private LocalDateTime lastLogin;
    private int visitCount;

    public VisitorLogEntity(String discordId, String username, String avatarHash) {
        this.discordId = discordId;
        this.username = username;
        this.avatarHash = avatarHash;
        this.lastLogin = LocalDateTime.now();
        this.visitCount = 1;
    }

    public void updateLogin() {
        this.lastLogin = LocalDateTime.now();
        this.visitCount++;
    }
}
