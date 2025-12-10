package com.rsl.clansite.model;

import com.rsl.clansite.model.enums.Location;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Aura {
    private boolean flatstat;
    private boolean percentage;
    private int amount;
    private String aura;
    private Location location;
}
