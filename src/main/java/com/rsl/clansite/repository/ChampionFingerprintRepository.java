package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.ChampionFingerprint;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ChampionFingerprintRepository extends MongoRepository<ChampionFingerprint, String> {
    Optional<ChampionFingerprint> findByHash(Long hash);
}
