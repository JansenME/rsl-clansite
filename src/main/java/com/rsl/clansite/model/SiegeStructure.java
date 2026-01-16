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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SiegeSlot {
        private int slotNumber;
        private String memberId;
        private String playerName;

        public SiegeSlot(int slotNumber) {
            this.slotNumber = slotNumber;
            this.memberId = null;
            this.playerName = null;
        }
    }
}