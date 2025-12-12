package com.rsl.clansite.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseStats {
    private int hp;
    private int attack;
    private int defense;
    private int speed;
    private int criticalRate;
    private int criticalDamage;
    private int resistance;
    private int accuracy;

    public String toCsvString() {
        return String.format("%d,%d,%d,%d,%d,%d,%d,%d",
                this.hp,
                this.attack,
                this.defense,
                this.speed,
                this.criticalRate,
                this.criticalDamage,
                this.resistance,
                this.accuracy
        );
    }
}
