package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.RaidUser;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RaidUserRepository extends MongoRepository<RaidUser, ObjectId> {
    List<RaidUser> findByRaidIdIn(List<Long> raidIds);
}
