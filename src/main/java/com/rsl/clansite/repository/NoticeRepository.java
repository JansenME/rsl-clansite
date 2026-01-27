package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.NoticeEntity;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoticeRepository extends MongoRepository<NoticeEntity, ObjectId> {
    Optional<NoticeEntity> findFirstByActiveTrueOrderByCreatedAtDesc();

    List<NoticeEntity> findAllByActiveTrueOrderByCreatedAtDesc();
}