package com.rsl.clansite.model;

import com.rsl.clansite.model.enums.FilterType;
import lombok.Data;

@Data
public class ChampionFilter {
    private boolean value;
    private String name;
    private FilterType filterType;
    private String field;

    public ChampionFilter(final String name, final FilterType filterType) {
        this.value = true;
        this.name = name;
        this.filterType = filterType;
        this.field = name.toLowerCase().replaceAll(" ", "_");
    }
}
