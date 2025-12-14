package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.ClanmemberEntity;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClanmemberRepository extends MongoRepository<ClanmemberEntity, ObjectId> {

    Optional<ClanmemberEntity> findByPlayerName(String playerName);
    Optional<ClanmemberEntity> findByDiscordId(String discordId);
}
