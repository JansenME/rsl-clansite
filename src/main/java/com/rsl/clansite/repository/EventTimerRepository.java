package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.EventTimerEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventTimerRepository extends MongoRepository<EventTimerEntity, String> {
}