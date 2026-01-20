package com.rsl.clansite.service;

import com.rsl.clansite.model.enums.ClanGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SiegeSchedulerService {

    private final SiegeService siegeService;

    public SiegeSchedulerService(SiegeService siegeService) {
        this.siegeService = siegeService;
    }

    /**
     * Runs every hour to check if a Siege phase transition is needed.
     * We run hourly to ensure that even if a specific time is missed due to downtime,
     * the state machine will catch up within the next hour.
     *
     * Transitions handled by the Service:
     * - Thursday 10:00 UTC: Finish Old -> Start New (PREP)
     * - Tuesday 10:00 UTC: PREP -> MATCHMAKING
     * - Tuesday 14:00 UTC: MATCHMAKING -> BATTLE
     */
    @Scheduled(cron = "0 0 * * * *")
    public void runSiegeStateCheck() {
        log.debug("Running Scheduled Siege State Check...");
        siegeService.checkAndAdvanceState(ClanGroup.T1);
        siegeService.checkAndAdvanceState(ClanGroup.T2);
    }
}