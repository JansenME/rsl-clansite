package com.rsl.clansite.model;

import com.rsl.clansite.model.enums.AuraLocation;
import com.rsl.clansite.model.enums.AuraStat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Aura {
    private boolean isPercentage;
    private int amount;
    private AuraStat stat;
    private AuraLocation location;

    public String toCsvString() {
        return String.format("%s,%d,%s,%s",
                this.isPercentage,
                this.amount,
                this.stat.getName(),
                this.location.getName()
        );
    }
}
