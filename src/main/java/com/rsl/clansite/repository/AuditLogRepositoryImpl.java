package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.AuditLogEntity;
import com.rsl.clansite.model.enums.AuditAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class AuditLogRepositoryImpl implements AuditLogRepositoryCustom {
    private final MongoTemplate mongoTemplate;

    @Autowired
    public AuditLogRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<AuditLogEntity> searchAuditLogs(LocalDateTime startDate,
                                                LocalDateTime endDate,
                                                String actorName,
                                                AuditAction action,
                                                String target) {
        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();

        if (startDate != null && endDate != null) {
            criteria.add(Criteria.where("timestamp").gte(startDate).lte(endDate));
        } else if (startDate != null) {
            criteria.add(Criteria.where("timestamp").gte(startDate));
        } else if (endDate != null) {
            criteria.add(Criteria.where("timestamp").lte(endDate));
        }

        if (actorName != null && !actorName.isBlank()) {
            criteria.add(Criteria.where("actorDiscordName").regex(actorName, "i"));
        }

        if (action != null) {
            criteria.add(Criteria.where("action").is(action));
        }

        if (target != null && !target.isBlank()) {
            criteria.add(Criteria.where("target").regex(target, "i"));
        }

        if (!criteria.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        }

        query.with(Sort.by(Sort.Direction.DESC, "timestamp"));

        query.limit(101);

        return mongoTemplate.find(query, AuditLogEntity.class);
    }
}
