package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.HubConfigEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HubConfigRepository extends MongoRepository<HubConfigEntity, String> {

    List<HubConfigEntity> findByGuildId(String guildId);

    Optional<HubConfigEntity> findByGuildIdAndChannelId(String guildId, String channelId);
}