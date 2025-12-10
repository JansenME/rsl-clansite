package com.rsl.clansite.model;

import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.Alliance;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.FilterType;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Data
public class CompleteChampionsFilter {
    private List<ChampionFilter> rarities = new ArrayList<>();
    private List<ChampionFilter> types = new ArrayList<>();
    private List<ChampionFilter> affinities = new ArrayList<>();
    private List<ChampionFilter> factions = new ArrayList<>();
    private List<ChampionFilter> alliances = new ArrayList<>();

    public CompleteChampionsFilter() {
        addRarityToFilter();
        addTypeToFilter();
        addAffinityToFilter();
        addFactionToFilter();
        addAllianceToFilter();
    }

    private void addRarityToFilter() {
        Stream.of(Rarity.values())
                .forEach(value -> rarities.add(new ChampionFilter(value.getName(), FilterType.RARITY)));
    }

    private void addTypeToFilter() {
        Stream.of(Type.values())
                .forEach(value -> types.add(new ChampionFilter(value.getName(), FilterType.TYPE)));
    }

    private void addAffinityToFilter() {
        Stream.of(Affinity.values())
                .forEach(value -> affinities.add(new ChampionFilter(value.getName(), FilterType.AFFINITY)));
    }

    private void addFactionToFilter() {
        Stream.of(Faction.values())
                .forEach(value -> factions.add(new ChampionFilter(value.getName(), FilterType.FACTION)));
    }

    private void addAllianceToFilter() {
        Stream.of(Alliance.values())
                .forEach(value -> alliances.add(new ChampionFilter(value.getName(), FilterType.ALLIANCE)));
    }
}
