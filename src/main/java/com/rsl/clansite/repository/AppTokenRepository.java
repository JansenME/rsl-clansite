package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.AppToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppTokenRepository extends MongoRepository<AppToken, String> {

    Optional<AppToken> findByToken(String token);

    void deleteByDiscordId(String discordId);

    boolean existsByToken(String token);
}