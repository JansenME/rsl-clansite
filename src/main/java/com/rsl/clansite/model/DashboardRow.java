package com.rsl.clansite.model;

import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class DashboardRow {
    private Faction faction;
    private Map<Rarity, Integer> targets;
    private Map<Rarity, Integer> database;
    private Map<Rarity, Integer> online;
    private int myTotal;

    public int getDiff(Rarity r) {
        return targets.getOrDefault(r, 0) - database.getOrDefault(r, 0);
    }

    private int getTotalDatabase() {
        return database.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean isComplete() {
        return myTotal == getTotalDatabase();
    }

    public boolean isUpdateAvailable() {
        int totalOnline = online.values().stream().mapToInt(Integer::intValue).sum();
        return totalOnline > getTotalDatabase();
    }
}
