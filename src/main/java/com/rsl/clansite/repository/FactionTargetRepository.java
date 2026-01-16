package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.FactionTargetEntity;
import com.rsl.clansite.model.enums.Faction;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FactionTargetRepository extends MongoRepository<FactionTargetEntity, ObjectId> {
    Optional<FactionTargetEntity> findByFaction(Faction faction);
}