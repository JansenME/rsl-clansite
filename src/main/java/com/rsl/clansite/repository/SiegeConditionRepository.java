package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.SiegeConditionEntity;
import com.rsl.clansite.model.enums.ConditionCategory;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SiegeConditionRepository extends MongoRepository<SiegeConditionEntity, ObjectId> {
    Optional<SiegeConditionEntity> findByCategoryAndConditionKey(ConditionCategory category, String conditionKey);
    List<SiegeConditionEntity> findAllByCategory(ConditionCategory category);
}