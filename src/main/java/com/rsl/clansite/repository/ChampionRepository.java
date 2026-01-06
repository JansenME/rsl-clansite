package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.ChampionEntity;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChampionRepository extends MongoRepository<ChampionEntity, ObjectId> {
    boolean existsByName(String name);
}
