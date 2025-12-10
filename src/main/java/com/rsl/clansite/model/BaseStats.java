package com.rsl.clansite.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BaseStats {
    private int hp;
    private int attack;
    private int defense;
    private int speed;
    private int criticalRate;
    private int criticalDamage;
    private int resistance;
    private int accuracy;
}
