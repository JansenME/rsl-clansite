package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.UserRefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRefreshTokenRepository extends MongoRepository<UserRefreshToken, String> {
    Optional<UserRefreshToken> findByToken(String token);

    Optional<UserRefreshToken> findByDiscordId(String discordId);

    void deleteByDiscordId(String discordId);
}
