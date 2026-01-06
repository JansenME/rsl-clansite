package com.rsl.clansite.model.dto;

import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.model.enums.ClanRank;
import com.rsl.clansite.validation.OnCreate;
import com.rsl.clansite.validation.UniqueIngameName;
import com.rsl.clansite.validation.ValidClanAssignment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@ValidClanAssignment
public class NewClanmemberDTO implements Serializable {
    @Pattern(regexp = "^\\d*$", message = "Discord ID must contain only numbers.")
    private String discordId;

    private String discordName;
    private String playerNickname;

    private ClanGroup clanGroup;

    @UniqueIngameName(groups = OnCreate.class)
    private String ingameName;

    private ClanRank clanRank;

    private String avatarHash;
    private List<String> discordRoles;
}
