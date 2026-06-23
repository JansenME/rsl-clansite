package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.ClanmemberMapping;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClanmemberMappingRepository extends MongoRepository<ClanmemberMapping, Long> {
}
