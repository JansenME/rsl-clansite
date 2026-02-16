package com.rsl.clansite.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rsl.clansite.model.entity.EventTimerEntity;
import com.rsl.clansite.repository.EventTimerRepository;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
public class TimerScraperService {

    private final EventTimerRepository eventTimerRepository;
    private static final String HH_API_URL = "https://hellhades.com/wp-json/hh-api/v3/event-timers";

    public TimerScraperService(EventTimerRepository eventTimerRepository) {
        this.eventTimerRepository = eventTimerRepository;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HellHadesTimerDto {
        @JsonProperty("event_name")
        private String eventName;
        @JsonProperty("event_start")
        private String eventStart;
        @JsonProperty("event_duration")
        private String eventDuration;
        @JsonProperty("is_recurring")
        private String isRecurring;
        @JsonProperty("event_interval")
        private String eventInterval;
    }

    @PostConstruct
    @Scheduled(fixedDelay = 1800000) // Run every 30 minutes
    public void fetchTimers() {
        try {
            log.info("Fetching HellHades API for event timers...");
            RestTemplate restTemplate = new RestTemplate();
            HellHadesTimerDto[] timers = restTemplate.getForObject(HH_API_URL, HellHadesTimerDto[].class);

            if (timers == null || timers.length == 0) {
                log.warn("No timers found from HellHades API.");
                return;
            }

            Instant now = Instant.now();

            for (HellHadesTimerDto dto : timers) {
                if (dto.getEventStart() == null || dto.getEventName() == null) {
                    continue;
                }

                Instant baseStart = Instant.parse(dto.getEventStart());
                long durationHours = dto.getEventDuration() != null ? Long.parseLong(dto.getEventDuration()) : 0;
                long intervalHours = dto.getEventInterval() != null ? Long.parseLong(dto.getEventInterval()) : 0;
                boolean isRecurring = "1".equals(dto.getIsRecurring());

                Instant currentStart = baseStart;

                // Project 2025 dates into the current cycle
                if (isRecurring && intervalHours > 0 && now.isAfter(baseStart)) {
                    Duration diff = Duration.between(baseStart, now);
                    long cyclesPassed = diff.toHours() / intervalHours;
                    currentStart = baseStart.plus(cyclesPassed * intervalHours, ChronoUnit.HOURS);

                    // If the current cycle's event has already finished, point to the next cycle
                    if (now.isAfter(currentStart.plus(durationHours, ChronoUnit.HOURS))) {
                        currentStart = currentStart.plus(intervalHours, ChronoUnit.HOURS);
                    }
                }

                Instant currentEnd = currentStart.plus(durationHours, ChronoUnit.HOURS);
                boolean isActive = now.isAfter(currentStart) && now.isBefore(currentEnd);

                Instant targetTime = isActive ? currentEnd : currentStart;
                String suffix = isActive ? " Ends In" : " Starts In";
                String finalName = dto.getEventName() + suffix;

                EventTimerEntity entity = EventTimerEntity.builder()
                        .id(dto.getEventName()) // Use the base name as ID so we update the same record
                        .eventName(finalName)
                        .targetTime(targetTime)
                        .lastScraped(now)
                        .build();

                eventTimerRepository.save(entity);
                log.debug("Saved timer: {} -> Target: {}", finalName, targetTime);
            }
            log.info("Successfully fetched and calculated event timers from API.");
        } catch (Exception e) {
            log.error("An error occurred while fetching HellHades timers: {}", e.getMessage());
        }
    }
}