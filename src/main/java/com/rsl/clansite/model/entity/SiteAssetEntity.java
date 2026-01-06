package com.rsl.clansite.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "site_assets")
public class SiteAssetEntity {
    @Id
    private String id;

    private byte[] data;
    private String hash;
    private String contentType;
}
