package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.model.enums.MemberStatus;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClanmemberRepository extends MongoRepository<ClanmemberEntity, ObjectId> {
    List<ClanmemberEntity> findAllByDiscordId(String discordId);
    long countByDiscordId(String discordId);
    boolean existsByIngameName(String ingameName);
    List<ClanmemberEntity> findAllByDiscordIdIsNotNull();
    Optional<ClanmemberEntity> findByIngameName(String ingameName);

    List<ClanmemberEntity> findByClanGroupAndStatus(ClanGroup group, MemberStatus memberStatus);
}
