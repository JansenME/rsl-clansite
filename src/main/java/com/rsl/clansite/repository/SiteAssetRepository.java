package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.SiteAssetEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SiteAssetRepository extends MongoRepository<SiteAssetEntity, String> {
}
