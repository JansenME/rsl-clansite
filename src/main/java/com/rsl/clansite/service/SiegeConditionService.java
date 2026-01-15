package com.rsl.clansite.service;

import com.rsl.clansite.model.entity.SiegeConditionEntity;
import com.rsl.clansite.model.enums.ConditionCategory;
import com.rsl.clansite.repository.SiegeConditionRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class SiegeConditionService {
    private final SiegeConditionRepository siegeConditionRepository;

    public SiegeConditionService(SiegeConditionRepository siegeConditionRepository) {
        this.siegeConditionRepository = siegeConditionRepository;
    }

    @PostConstruct
    public void init() {
        syncConditions();
    }

    @Transactional
    public void syncConditions() {
        log.info("Starting Siege Condition synchronization...");
        int newConditions = 0;

        for (ConditionCategory category : ConditionCategory.values()) {
            Class<? extends Enum<?>> enumClass = category.getEnumClass();

            Object[] constants = enumClass.getEnumConstants();

            for (Object constant : constants) {
                String key = ((Enum<?>) constant).name();

                Optional<SiegeConditionEntity> existing = siegeConditionRepository.findByCategoryAndConditionKey(category, key);

                if (existing.isEmpty()) {
                    SiegeConditionEntity newEntity = new SiegeConditionEntity(category, key);
                    siegeConditionRepository.save(newEntity);
                    newConditions++;
                }
            }
        }

        if (newConditions > 0) {
            log.info("Siege Condition Sync: Discovered and added {} new conditions to the database.", newConditions);
        } else {
            log.info("Siege Condition Sync: Database is up to date.");
        }
    }

    public List<SiegeConditionEntity> findAllConditions() {
        List<SiegeConditionEntity> conditions = siegeConditionRepository.findAll();

        conditions.sort(Comparator.comparing((SiegeConditionEntity c) -> c.getCategory().name())
                .thenComparing(SiegeConditionEntity::getConditionKey));

        return conditions;
    }

    @Transactional
    public void toggleConditionStatus(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            throw new IllegalArgumentException("Invalid ID provided");
        }

        siegeConditionRepository.findById(new ObjectId(id)).ifPresent(condition -> {
            boolean newState = !condition.isActive();
            condition.setActive(newState);
            siegeConditionRepository.save(condition);
            log.info("Siege Condition '{}' ({}) toggled to: {}", condition.getConditionKey(), condition.getCategory(), newState);
        });
    }

    public SiegeConditionEntity getConditionById(ObjectId id) {
        if (id == null) {
            return null;
        }
        return siegeConditionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Siege Condition not found with ID: " + id));
    }
}