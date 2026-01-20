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
import java.time.temporal.ChronoUnit;
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

    /**
     * The core State Machine. Checks the current time against the Siege's anchor date
     * and advances the status or creates a new cycle if needed.
     */
    @Transactional
    public void checkAndAdvanceState(ClanGroup clanGroup) {
        Optional<SiegeEntity> siegeOpt = getActiveSiege(clanGroup);

        // Bootstrap: If no siege exists, create one starting NOW (snapped to hour?)
        // Or if we assume we are starting fresh on a Thursday.
        if (siegeOpt.isEmpty()) {
            log.info("[{}] No active siege found during check. Bootstrapping new cycle.", clanGroup);
            createNextSiege(clanGroup, LocalDateTime.now());
            return;
        }

        SiegeEntity siege = siegeOpt.get();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = siege.getStartDate();

        long hoursSinceStart = ChronoUnit.HOURS.between(startDate, now);
        long daysSinceStart = ChronoUnit.DAYS.between(startDate, now);

        // --- PHASE 1: PREP -> MATCHMAKING ---
        // Happens on Day 12 (Tuesday) at 10:00 UTC (approx 288 hours)
        if (siege.getStatus() == SiegeStatus.PREP) {
            // 12 days = 288 hours.
            if (hoursSinceStart >= 288) {
                log.info("[{}] Switching PREP -> MATCHMAKING (Hours: {})", clanGroup, hoursSinceStart);
                siege.setStatus(SiegeStatus.MATCHMAKING);
                siege.setLastModified(now);
                siegeRepository.save(siege);
            }
        }

        // --- PHASE 2: MATCHMAKING -> BATTLE ---
        // Happens on Day 12 (Tuesday) at 14:00 UTC (approx 292 hours)
        else if (siege.getStatus() == SiegeStatus.MATCHMAKING) {
            // 12 days + 4 hours = 292 hours.
            if (hoursSinceStart >= 292) {
                log.info("[{}] Switching MATCHMAKING -> BATTLE (Hours: {})", clanGroup, hoursSinceStart);
                siege.setStatus(SiegeStatus.BATTLE);
                siege.setLastModified(now);
                siegeRepository.save(siege);
            }
        }

        // --- PHASE 3: BATTLE -> FINISH & ROTATE ---
        // Happens on Day 14 (Thursday) at 10:00 UTC (336 hours)
        // We check if we passed the 14-day mark.
        else if (siege.getStatus() == SiegeStatus.BATTLE) {
            if (daysSinceStart >= 14) {
                log.info("[{}] Siege Cycle Complete ({} days). Rotating...", clanGroup, daysSinceStart);

                // 1. Finish current
                finishActiveSiege(clanGroup);

                // 2. Start next one exactly 14 days after the previous one started
                // This prevents time drift over months.
                LocalDateTime nextCycleStart = startDate.plusDays(14);
                createNextSiege(clanGroup, nextCycleStart);
            }
        }
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
    public SiegeEntity createNextSiege(ClanGroup clanGroup, LocalDateTime startDate) {
        // Double-check to ensure no active siege exists before creating
        // (Though the state machine usually handles the finish first)
        finishActiveSiege(clanGroup);

        log.info("Creating new PREP siege for {} starting at {}", clanGroup, startDate);

        SiegeEntity newSiege = new SiegeEntity(clanGroup, startDate);

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