package com.rsl.clansite.service;

import com.rsl.clansite.model.SiegeStructure;
import com.rsl.clansite.model.entity.SiegeEntity;
import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.model.enums.SiegeStatus;
import com.rsl.clansite.model.enums.SiegeStructureType;
import com.rsl.clansite.repository.SiegeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class SiegeService {

    private final SiegeRepository siegeRepository;

    public SiegeService(SiegeRepository siegeRepository) {
        this.siegeRepository = siegeRepository;
    }

    public Optional<SiegeEntity> getActiveSiege(ClanGroup clanGroup) {
        return siegeRepository.findFirstByClanGroupAndStatusNot(clanGroup, SiegeStatus.FINISHED);
    }

    @Transactional
    public void finishActiveSiege(ClanGroup clanGroup) {
        getActiveSiege(clanGroup).ifPresent(siege -> {
            log.info("Finishing active siege for {} (ID: {})", clanGroup, siege.getId());
            siege.setStatus(SiegeStatus.FINISHED);
            siege.setLastModified(LocalDateTime.now());
            siegeRepository.save(siege);
        });
    }

    @Transactional
    public SiegeEntity createNextSiege(ClanGroup clanGroup) {
        // Double-check to ensure no active siege exists before creating
        finishActiveSiege(clanGroup);

        log.info("Creating new PREP siege for {}", clanGroup);

        SiegeEntity newSiege = new SiegeEntity(clanGroup);

        // Populate Hardcoded Map Layouts
        newSiege.setDefensiveStructures(generateDefaultMap());
        newSiege.setTargetStructures(generateDefaultMap());

        return siegeRepository.save(newSiege);
    }

    // --- Helper: Generates the specific Raid Siege Map Layout ---
    private List<SiegeStructure> generateDefaultMap() {
        List<SiegeStructure> map = new ArrayList<>();

        // 1. The Stronghold (1) - No Number
        map.add(new SiegeStructure("Stronghold", SiegeStructureType.STRONGHOLD));

        // 2. Mana Shrines (2)
        for (int i = 1; i <= 2; i++) {
            map.add(new SiegeStructure("Mana Shrine " + i, SiegeStructureType.SHRINE));
        }

        // 3. Magic Towers (4)
        for (int i = 1; i <= 4; i++) {
            map.add(new SiegeStructure("Magic Tower " + i, SiegeStructureType.MAGIC_TOWER));
        }

        // 4. Defense Towers (5)
        for (int i = 1; i <= 5; i++) {
            map.add(new SiegeStructure("Defense Tower " + i, SiegeStructureType.DEFENSE_TOWER));
        }

        // 5. Posts (18)
        for (int i = 1; i <= 18; i++) {
            map.add(new SiegeStructure("Post " + i, SiegeStructureType.POST));
        }

        return map;
    }
}