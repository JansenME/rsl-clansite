package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.SiegeEntity;
import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.model.enums.SiegeStatus;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SiegeRepository extends MongoRepository<SiegeEntity, ObjectId> {

    Optional<SiegeEntity> findFirstByClanGroupAndStatusNot(ClanGroup clanGroup, SiegeStatus status);

    List<SiegeEntity> findByClanGroupAndStatusOrderByStartDateDesc(ClanGroup clanGroup, SiegeStatus status);
}