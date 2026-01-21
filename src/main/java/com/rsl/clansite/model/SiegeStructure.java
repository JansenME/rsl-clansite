package com.rsl.clansite.model;

import com.rsl.clansite.model.enums.SiegeStructureType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SiegeStructure {

    private String id = UUID.randomUUID().toString();
    private String name;
    private SiegeStructureType type;
    private int level = 1;

    private int defensePoints;
    private int attackPoints;

    private boolean isCleared;

    private List<SiegeSlot> slots = new ArrayList<>();

    public SiegeStructure(String name, SiegeStructureType type) {
        this.name = name;
        this.type = type;
        this.defensePoints = type.getDefaultDefensePoints();
        this.attackPoints = type.getDefaultAttackPoints();
        this.slots = new ArrayList<>();

        for(int i = 0; i < type.getDefaultSlotsLevel1(); i++) {
            this.slots.add(new SiegeSlot(i + 1));
        }
    }

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