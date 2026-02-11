package com.rsl.clansite.model;

import com.rsl.clansite.model.enums.SiegeStructureType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SiegeStructure {

    // ... (Fields remain the same) ...
    private String id = UUID.randomUUID().toString();
    private String name;
    private SiegeStructureType type;
    private int level = 1;
    private int defensePoints;
    private int attackPoints;
    private boolean isCleared;
    private List<String> conditionKeys = new ArrayList<>();
    private List<SiegeSlot> slots = new ArrayList<>();

    // ... (Constructor & updateLevel remain the same) ...

    public SiegeStructure(String name, SiegeStructureType type) {
        this.name = name;
        this.type = type;
        this.defensePoints = type.getDefaultDefensePoints();
        this.attackPoints = type.getDefaultAttackPoints();
        this.slots = new ArrayList<>();
        this.conditionKeys = new ArrayList<>();

        int initialSlots = type.getSlotsForLevel(1);
        for(int i = 0; i < initialSlots; i++) {
            this.slots.add(new SiegeSlot(i + 1));
        }
    }

    public void updateLevel(int newLevel) {
        if (newLevel < 1 || newLevel > type.getMaxLevel()) {
            return;
        }
        this.level = newLevel;
        int targetSize = this.type.getSlotsForLevel(newLevel);
        while (this.slots.size() < targetSize) {
            this.slots.add(new SiegeSlot(this.slots.size() + 1));
        }
        while (this.slots.size() > targetSize) {
            this.slots.remove(this.slots.size() - 1);
        }
    }

    /**
     * Custom setter to enforce business logic:
     * 1. Only POST type can have conditions.
     * 2. Filter out nulls and empty strings.
     * 3. Maximum of 3 conditions allowed.
     */
    public void setConditionKeys(List<String> keys) {
        if (this.type != SiegeStructureType.POST) {
            this.conditionKeys = new ArrayList<>();
            return;
        }

        if (keys == null) {
            this.conditionKeys = new ArrayList<>();
            return;
        }

        // Filter out empty strings and nulls
        List<String> validKeys = keys.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());

        // Enforce Max 3
        if (validKeys.size() > 3) {
            this.conditionKeys = new ArrayList<>(validKeys.subList(0, 3));
        } else {
            this.conditionKeys = new ArrayList<>(validKeys);
        }
    }

    // ... (getGroups and SiegeSlot class remain the same) ...
    public List<List<SiegeSlot>> getGroups() {
        List<List<SiegeSlot>> groups = new ArrayList<>();
        int groupSize = 3;
        for (int i = 0; i < slots.size(); i += groupSize) {
            groups.add(slots.subList(i, Math.min(i + groupSize, slots.size())));
        }
        return groups;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SiegeSlot {
        private int slotNumber;
        private String memberId;
        private String playerName;
        private String leaderChampionId;
        private List<String> supportChampionIds = new ArrayList<>();

        public SiegeSlot(int slotNumber) {
            this.slotNumber = slotNumber;
            this.memberId = null;
            this.playerName = null;
            this.leaderChampionId = null;
            this.supportChampionIds = new ArrayList<>();
        }
    }
}