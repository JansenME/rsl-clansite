package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.enums.Faction;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChampionRepository extends MongoRepository<ChampionEntity, ObjectId> {
    Optional<ChampionEntity> findByNameIgnoreCase(String name);
}
