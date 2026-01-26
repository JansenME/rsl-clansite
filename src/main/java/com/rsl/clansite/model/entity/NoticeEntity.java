package com.rsl.clansite.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "site_notices")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoticeEntity {
    @Id
    private ObjectId id;

    private String title;

    private String content;

    private boolean active;

    private LocalDateTime createdAt;
    private String createdBy;
}