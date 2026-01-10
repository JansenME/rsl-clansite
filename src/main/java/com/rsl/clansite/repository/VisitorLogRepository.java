package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.VisitorLogEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface VisitorLogRepository extends MongoRepository<VisitorLogEntity, String> {
    Optional<VisitorLogEntity> findByDiscordId(String discordId);
}
