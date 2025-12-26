package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.AuditLogEntity;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLogEntity, ObjectId>, AuditLogRepositoryCustom {
    List<AuditLogEntity> findAllByOrderByTimestampDesc();
}
