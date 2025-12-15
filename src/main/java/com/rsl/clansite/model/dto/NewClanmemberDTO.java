package com.rsl.clansite.model.dto;

import com.rsl.clansite.model.enums.ClanRank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NewClanmemberDTO {
    private String discordId;

    private String discordName;
    private String playerNickname;

    private String ingameName;
    private ClanRank clanRank;
}
