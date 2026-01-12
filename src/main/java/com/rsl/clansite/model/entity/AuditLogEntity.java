package com.rsl.clansite.model.entity;

import com.rsl.clansite.model.enums.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity {
    @Id
    private ObjectId id;

    @Indexed(direction = IndexDirection.DESCENDING)
    private LocalDateTime timestamp;

    @Indexed
    private String actorDiscordId;

    private String actorDiscordName;
    private AuditAction action;
    private String target;
    private String details;
}
