package com.rsl.clansite.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "event_timers")
public class EventTimerEntity {

    @Id
    private String id;

    private String eventName;

    private Instant targetTime;

    private Instant lastScraped;
}