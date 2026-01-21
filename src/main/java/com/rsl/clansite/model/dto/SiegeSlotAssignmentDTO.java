package com.rsl.clansite.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SiegeSlotAssignmentDTO {
    private String siegeId;
    private String structureId;
    private int slotNumber;

    private String memberId;

    private String leaderChampionId;
    private List<String> supportChampionIds;
}