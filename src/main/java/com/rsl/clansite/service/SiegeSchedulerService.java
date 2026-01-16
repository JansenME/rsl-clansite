package com.rsl.clansite.service;

import com.rsl.clansite.model.entity.SiegeEntity;
import com.rsl.clansite.model.enums.ClanGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Slf4j
@Service
public class SiegeSchedulerService {

    private final SiegeService siegeService;

    public SiegeSchedulerService(SiegeService siegeService) {
        this.siegeService = siegeService;
    }

    /**
     * Runs every Tuesday at 10:00 UTC (Raid Reset Time).
     * Checks if the current cycle is over (14 days) and starts a new one if needed.
     */
    @Scheduled(cron = "0 0 10 * * TUE")
    public void checkAndStartNewSiegeCycle() {
        log.info("Running Scheduled Siege Cycle Check...");
        checkAndRotate(ClanGroup.T1);
        checkAndRotate(ClanGroup.T2);
    }

    private void checkAndRotate(ClanGroup clanGroup) {
        Optional<SiegeEntity> activeSiegeOpt = siegeService.getActiveSiege(clanGroup);

        if (activeSiegeOpt.isEmpty()) {
            log.info("[{}] No active siege found. Starting fresh cycle.", clanGroup);
            siegeService.createNextSiege(clanGroup);
            return;
        }

        SiegeEntity activeSiege = activeSiegeOpt.get();
        long daysSinceStart = ChronoUnit.DAYS.between(activeSiege.getStartDate(), LocalDateTime.now());

        // Siege cycle is 14 days. If it's been running for >= 13 days, it's time for a new one.
        // (Using 13 to be safe against slight clock drifts vs cron execution)
        if (daysSinceStart >= 13) {
            log.info("[{}] Current siege started {} days ago. Rotating to new cycle.", clanGroup, daysSinceStart);
            siegeService.createNextSiege(clanGroup);
        } else {
            log.info("[{}] Current siege is only {} days old. No rotation needed.", clanGroup, daysSinceStart);
        }
    }
}