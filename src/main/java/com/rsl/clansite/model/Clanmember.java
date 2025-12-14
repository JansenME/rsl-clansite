package com.rsl.clansite.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Clanmember {
    private String discordName;
    private String discordId;
    private String avatarHash;
    private String playerName;
    private String ingameName;
    private String clanRank;
    private List<Champion> champions;
}
