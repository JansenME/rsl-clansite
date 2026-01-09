package com.rsl.clansite.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChampionTarget {
    private String name;
    private String url;
    private String rosterImageUrl;
}
